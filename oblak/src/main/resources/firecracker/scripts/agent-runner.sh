#!/bin/bash

set -u

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

echo "[agent] starting agent-runner"

mkdir -p /proc /sys /dev

mount -t proc proc /proc 2>/dev/null || true
mount -t sysfs sysfs /sys 2>/dev/null || true
mount -t devtmpfs devtmpfs /dev 2>/dev/null || true

shutdown_vm() {
  code="$1"
  echo "[agent] finished with exit_code=$code"
  sync

  if [ -w /proc/sysrq-trigger ]; then
    echo "[agent] attempting guest poweroff via sysrq"
    echo o > /proc/sysrq-trigger
    sleep 2
  fi

  if command -v halt >/dev/null 2>&1; then
    echo "[agent] attempting halt -f"
    halt -f
    sleep 2
  fi

  if command -v poweroff >/dev/null 2>&1; then
    echo "[agent] attempting poweroff -f"
    poweroff -f
    sleep 2
  fi

  if command -v reboot >/dev/null 2>&1; then
    echo "[agent] attempting reboot -f"
    reboot -f
    sleep 2
  fi

  echo "[agent] ERROR: guest shutdown commands failed; exiting init"
  exit "$code"
}

get_cmdline_arg() {
  key="$1"
  default_value="$2"

  value="$(tr ' ' '\n' < /proc/cmdline | sed -n "s/^${key}=//p" | tail -n 1)"

  if [ -n "$value" ]; then
    printf "%s" "$value"
  else
    printf "%s" "$default_value"
  fi
}

PROJECT_DISK="$(get_cmdline_arg PROJECT_DISK /dev/vdb)"
PROJECT_MOUNT="$(get_cmdline_arg PROJECT_MOUNT /mnt/project)"
PROJECT_ROOT="$(get_cmdline_arg PROJECT_ROOT .)"
ROOT_FILE="$(get_cmdline_arg ROOT_FILE main.py)"
MODE="$(get_cmdline_arg MODE execute)"
GUEST_IP="$(get_cmdline_arg GUEST_IP 172.16.0.2)"
GUEST_CIDR_PREFIX="$(get_cmdline_arg GUEST_CIDR_PREFIX 24)"
GUEST_GATEWAY="$(get_cmdline_arg GUEST_GATEWAY 172.16.0.1)"
DNS_SERVER="$(get_cmdline_arg DNS_SERVER 1.1.1.1)"

echo "[agent] cmdline=$(cat /proc/cmdline)"
echo "[agent] MODE=$MODE"
echo "[agent] PROJECT_DISK=$PROJECT_DISK"
echo "[agent] PROJECT_MOUNT=$PROJECT_MOUNT"
echo "[agent] PROJECT_ROOT=$PROJECT_ROOT"
echo "[agent] ROOT_FILE=$ROOT_FILE"

configure_network() {
  if ! command -v ip >/dev/null 2>&1; then
    echo "[agent] ERROR: ip command is required for network setup"
    shutdown_vm 30
  fi

  mkdir -p /run
  touch /run/network-configured

  ip link set dev eth0 up
  ip addr flush dev eth0 || true
  ip addr add "$GUEST_IP/$GUEST_CIDR_PREFIX" dev eth0
  ip route replace default via "$GUEST_GATEWAY" dev eth0
  printf 'nameserver %s\n' "$DNS_SERVER" > /etc/resolv.conf
}

verify_prepare_prerequisites() {
  if ! python3 -m pip --version >/dev/null 2>&1; then
    echo "[agent] pip is missing, attempting bootstrap"
    bootstrap_pip
  fi

  if ! python3 -m pip --version >/dev/null 2>&1; then
    echo "[agent] ERROR: pip is not available in the guest rootfs even after bootstrap"
    shutdown_vm 31
  fi
}

bootstrap_pip() {
  if python3 -m ensurepip --upgrade >/dev/null 2>&1; then
    echo "[agent] bootstrapped pip with ensurepip"
    return 0
  fi

  echo "[agent] ensurepip unavailable, downloading get-pip.py"
  if ! python3 - <<'PY'
import pathlib
import urllib.request

target = pathlib.Path("/tmp/get-pip.py")
urllib.request.urlretrieve("https://bootstrap.pypa.io/get-pip.py", target)
print(target)
PY
  then
    echo "[agent] ERROR: failed to download get-pip.py"
    return 1
  fi

  python3 /tmp/get-pip.py --disable-pip-version-check --break-system-packages
}

mkdir -p "$PROJECT_MOUNT"

echo "[agent] mounting project disk..."
mount "$PROJECT_DISK" "$PROJECT_MOUNT"
mount_status="$?"

if [ "$mount_status" -ne 0 ]; then
  echo "[agent] ERROR: failed to mount project disk $PROJECT_DISK at $PROJECT_MOUNT"
  shutdown_vm 20
fi

WORKDIR="$PROJECT_MOUNT/$PROJECT_ROOT"

if [ ! -d "$WORKDIR" ]; then
  echo "[agent] ERROR: project working directory not found: $WORKDIR"
  shutdown_vm 21
fi

cd "$WORKDIR" || shutdown_vm 22

if [ "$MODE" = "prepare" ]; then
  configure_network

  if [ ! -f "requirements.txt" ]; then
    echo "[agent] no requirements.txt found, skipping dependency preparation"
    umount "$PROJECT_MOUNT" 2>/dev/null || true
    shutdown_vm 0
  fi

  verify_prepare_prerequisites

  rm -rf libs

  echo "[agent] checking guest network reachability (best effort)"
  python3 - <<'PY' || echo "[agent] WARNING: network preflight could not confirm PyPI reachability; proceeding to pip install"
import socket
import urllib.request

socket.gethostbyname("pypi.org")
urllib.request.urlopen("https://pypi.org/simple/", timeout=5).read(1)
PY

  echo "[agent] installing dependencies into libs/"
  python3 -m pip install --no-cache-dir --retries 3 --timeout 30 --target "$WORKDIR/libs" -r "$WORKDIR/requirements.txt"
  install_status="$?"

  if [ "$install_status" -ne 0 ]; then
    rm -rf libs
    echo "[agent] ERROR: dependency installation failed"
    umount "$PROJECT_MOUNT" 2>/dev/null || true
    shutdown_vm "$install_status"
  fi

  echo "[agent] dependency preparation complete"
  sync
  umount "$PROJECT_MOUNT" 2>/dev/null || true
  shutdown_vm 0
fi

if [ ! -f "$ROOT_FILE" ]; then
  echo "[agent] ERROR: root file not found: $WORKDIR/$ROOT_FILE"
  echo "[agent] files in workdir:"
  ls -la
  shutdown_vm 23
fi

PROGRAM_STDOUT_FILE="/tmp/program.stdout"
PROGRAM_STDERR_FILE="/tmp/program.stderr"

if [ -d "libs" ]; then
  echo "[agent] detected libs/ directory, setting PYTHONPATH"
  export PYTHONPATH="$WORKDIR/libs"
fi

echo "[agent] running python3 $ROOT_FILE"
timeout 10s python3 "$ROOT_FILE" >"$PROGRAM_STDOUT_FILE" 2>"$PROGRAM_STDERR_FILE"
program_status="$?"

echo "[agent] program_stdout_begin"
cat "$PROGRAM_STDOUT_FILE"
echo
echo "[agent] program_stdout_end"

echo "[agent] program_stderr_begin"
cat "$PROGRAM_STDERR_FILE"
echo
echo "[agent] program_stderr_end"

echo "[agent] program_status=$program_status"

umount "$PROJECT_MOUNT" 2>/dev/null || true

shutdown_vm "$program_status"
