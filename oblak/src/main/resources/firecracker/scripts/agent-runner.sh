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

echo "[agent] cmdline=$(cat /proc/cmdline)"
echo "[agent] PROJECT_DISK=$PROJECT_DISK"
echo "[agent] PROJECT_MOUNT=$PROJECT_MOUNT"
echo "[agent] PROJECT_ROOT=$PROJECT_ROOT"
echo "[agent] ROOT_FILE=$ROOT_FILE"

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

if [ ! -f "$ROOT_FILE" ]; then
  echo "[agent] ERROR: root file not found: $WORKDIR/$ROOT_FILE"
  echo "[agent] files in workdir:"
  ls -la
  shutdown_vm 23
fi

PROGRAM_STDOUT_FILE="/tmp/program.stdout"
PROGRAM_STDERR_FILE="/tmp/program.stderr"

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
