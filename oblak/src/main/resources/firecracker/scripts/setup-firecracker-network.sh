#!/usr/bin/env bash
set -euo pipefail

TAP_DEV="${1:-tap0}"
HOST_IP="${HOST_IP:-172.16.0.1}"
GUEST_CIDR_PREFIX="${GUEST_CIDR_PREFIX:-24}"
UPLINK_DEV="${UPLINK_DEV:-eth0}"
TAP_USER="${TAP_USER:-${SUDO_USER:-$(id -un)}}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script with sudo."
  echo "Example: sudo bash $0"
  exit 1
fi

if ! command -v ip >/dev/null 2>&1; then
  echo "ERROR: ip command is required."
  exit 1
fi

if [[ ! -c /dev/net/tun ]]; then
  echo "ERROR: /dev/net/tun is missing."
  exit 1
fi

if ip link show "$TAP_DEV" >/dev/null 2>&1; then
  echo "TAP device $TAP_DEV already exists."
else
  ip tuntap add "$TAP_DEV" mode tap user "$TAP_USER"
fi

ip addr replace "${HOST_IP}/${GUEST_CIDR_PREFIX}" dev "$TAP_DEV"
ip link set "$TAP_DEV" up
sysctl -w net.ipv4.ip_forward=1 >/dev/null

if command -v iptables >/dev/null 2>&1; then
  iptables -t nat -C POSTROUTING -s "${HOST_IP%.*}.0/${GUEST_CIDR_PREFIX}" -o "$UPLINK_DEV" -j MASQUERADE 2>/dev/null || \
    iptables -t nat -A POSTROUTING -s "${HOST_IP%.*}.0/${GUEST_CIDR_PREFIX}" -o "$UPLINK_DEV" -j MASQUERADE
  iptables -C FORWARD -i "$UPLINK_DEV" -o "$TAP_DEV" -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || \
    iptables -A FORWARD -i "$UPLINK_DEV" -o "$TAP_DEV" -m state --state RELATED,ESTABLISHED -j ACCEPT
  iptables -C FORWARD -i "$TAP_DEV" -o "$UPLINK_DEV" -j ACCEPT 2>/dev/null || \
    iptables -A FORWARD -i "$TAP_DEV" -o "$UPLINK_DEV" -j ACCEPT
elif command -v nft >/dev/null 2>&1; then
  nft list table ip firecracker >/dev/null 2>&1 || nft add table ip firecracker
  nft list chain ip firecracker postrouting >/dev/null 2>&1 || \
    nft add chain ip firecracker postrouting "{ type nat hook postrouting priority 100 ; }"
  nft list chain ip firecracker forward >/dev/null 2>&1 || \
    nft add chain ip firecracker forward "{ type filter hook forward priority 0 ; }"
  nft list ruleset | grep -F "ip saddr ${HOST_IP%.*}.0/${GUEST_CIDR_PREFIX} oifname \"$UPLINK_DEV\" masquerade" >/dev/null 2>&1 || \
    nft add rule ip firecracker postrouting ip saddr "${HOST_IP%.*}.0/${GUEST_CIDR_PREFIX}" oifname "$UPLINK_DEV" masquerade
  nft list ruleset | grep -F "iifname \"$TAP_DEV\" oifname \"$UPLINK_DEV\" accept" >/dev/null 2>&1 || \
    nft add rule ip firecracker forward iifname "$TAP_DEV" oifname "$UPLINK_DEV" accept
  nft list ruleset | grep -F "iifname \"$UPLINK_DEV\" oifname \"$TAP_DEV\" ct state related,established accept" >/dev/null 2>&1 || \
    nft add rule ip firecracker forward iifname "$UPLINK_DEV" oifname "$TAP_DEV" ct state related,established accept
else
  echo "WARNING: neither iptables nor nft is installed."
  echo "tap0 was created, but guest internet will not work until NAT is configured."
fi

echo
echo "Firecracker network ready."
echo "TAP device: $TAP_DEV"
echo "Host TAP IP: ${HOST_IP}/${GUEST_CIDR_PREFIX}"
echo "Guest gateway should be: $HOST_IP"
echo "Uplink device: $UPLINK_DEV"
