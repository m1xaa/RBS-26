#!/usr/bin/env bash
set -euo pipefail

TAP_DEV="${1:-tap0}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script with sudo."
  echo "Example: sudo bash $0"
  exit 1
fi

if command -v ip >/dev/null 2>&1 && ip link show "$TAP_DEV" >/dev/null 2>&1; then
  ip link set "$TAP_DEV" down || true
  ip tuntap del "$TAP_DEV" mode tap || true
fi

if command -v iptables >/dev/null 2>&1; then
  iptables -t nat -D POSTROUTING -s 172.16.0.0/24 -o eth0 -j MASQUERADE 2>/dev/null || true
  iptables -D FORWARD -i eth0 -o "$TAP_DEV" -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || true
  iptables -D FORWARD -i "$TAP_DEV" -o eth0 -j ACCEPT 2>/dev/null || true
fi

echo "Firecracker network torn down for $TAP_DEV."
