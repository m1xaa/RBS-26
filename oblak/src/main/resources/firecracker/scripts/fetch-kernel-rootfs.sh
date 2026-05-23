#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$PROJECT_ROOT"

MANIFEST="src/main/resources/firecracker/manifest/firecracker-images.json"

if [ ! -f "$MANIFEST" ]; then
  echo "ERROR: Manifest not found: $MANIFEST"
  exit 1
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: Missing command: $1"
    exit 1
  fi
}

require_cmd curl
require_cmd jq
require_cmd unsquashfs
require_cmd mkfs.ext4
require_cmd truncate
require_cmd fakeroot
require_cmd mktemp

IMAGES_DIR="$(jq -r '.localPaths.imagesDir' "$MANIFEST")"

KERNEL_FILENAME="$(jq -r '.kernel.filename' "$MANIFEST")"
KERNEL_URL="$(jq -r '.kernel.url' "$MANIFEST")"

ROOTFS_SQUASHFS_FILENAME="$(jq -r '.rootfs.squashfsFilename' "$MANIFEST")"
ROOTFS_SQUASHFS_URL="$(jq -r '.rootfs.squashfsUrl' "$MANIFEST")"
ROOTFS_EXT4_FILENAME="$(jq -r '.rootfs.ext4Filename' "$MANIFEST")"
ROOTFS_EXT4_SIZE="$(jq -r '.rootfs.ext4Size' "$MANIFEST")"

KERNEL_PATH="$IMAGES_DIR/$KERNEL_FILENAME"
ROOTFS_SQUASHFS_PATH="$IMAGES_DIR/$ROOTFS_SQUASHFS_FILENAME"
ROOTFS_EXT4_PATH="$IMAGES_DIR/$ROOTFS_EXT4_FILENAME"

mkdir -p "$IMAGES_DIR"

echo "Using manifest: $MANIFEST"
echo

if [ ! -f "$KERNEL_PATH" ]; then
  echo "Downloading Linux kernel..."
  curl -fL --retry 3 -o "$KERNEL_PATH" "$KERNEL_URL"
else
  echo "Kernel already exists: $KERNEL_PATH"
fi

echo

if [ ! -f "$ROOTFS_SQUASHFS_PATH" ]; then
  echo "Downloading Ubuntu rootfs squashfs..."
  curl -fL --retry 3 -o "$ROOTFS_SQUASHFS_PATH" "$ROOTFS_SQUASHFS_URL"
else
  echo "Rootfs squashfs already exists: $ROOTFS_SQUASHFS_PATH"
fi

echo

if [ -f "$ROOTFS_EXT4_PATH" ]; then
  echo "Rootfs ext4 already exists: $ROOTFS_EXT4_PATH"
  echo
  echo "Done."
  echo "Kernel: $KERNEL_PATH"
  echo "Rootfs: $ROOTFS_EXT4_PATH"
  exit 0
fi

echo "Creating ext4 rootfs..."

TMP_WORK_DIR="$(mktemp -d /tmp/firecracker-rootfs-build.XXXXXX)"
SQUASHFS_ROOT_DIR="$TMP_WORK_DIR/squashfs-root"
TMP_EXT4_PATH="$TMP_WORK_DIR/$ROOTFS_EXT4_FILENAME.tmp"

cleanup() {
  rm -rf "$TMP_WORK_DIR"
}

trap cleanup EXIT

unsquashfs -d "$SQUASHFS_ROOT_DIR" "$ROOTFS_SQUASHFS_PATH"

if [ ! -x "$SQUASHFS_ROOT_DIR/usr/bin/python3" ]; then
  echo "ERROR: python3 does not exist in rootfs."
  echo "This script does not install packages."
  echo "Use a rootfs that already contains python3."
  exit 1
fi

echo "python3 already exists in rootfs."

AGENT_SCRIPT_SOURCE="src/main/resources/firecracker/scripts/agent-runner.sh"
AGENT_SCRIPT_DEST="$SQUASHFS_ROOT_DIR/usr/local/bin/agent-runner"
mkdir -p "$(dirname "$AGENT_SCRIPT_DEST")"
cp "$AGENT_SCRIPT_SOURCE" "$AGENT_SCRIPT_DEST"
chmod +x "$AGENT_SCRIPT_DEST"

echo "Injected agent-runner into rootfs at $AGENT_SCRIPT_DEST"

export SQUASHFS_ROOT_DIR
export TMP_EXT4_PATH
export ROOTFS_EXT4_SIZE

fakeroot -- bash -c '
set -euo pipefail

chown -R root:root "$SQUASHFS_ROOT_DIR"

truncate -s "$ROOTFS_EXT4_SIZE" "$TMP_EXT4_PATH"

mkfs.ext4 \
  -d "$SQUASHFS_ROOT_DIR" \
  -F "$TMP_EXT4_PATH"
'

mv "$TMP_EXT4_PATH" "$ROOTFS_EXT4_PATH"

echo
echo "Done."
echo "Kernel: $KERNEL_PATH"
echo "Rootfs: $ROOTFS_EXT4_PATH"