RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

clear

echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════════╗"
echo "║              LINUX SECURITY AUDIT TOOL                 ║"
echo "║                   (Defensive / Hardening)              ║"
echo "╚════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo "Starting audit at: $(date)"
echo "Running as: $(whoami)"
echo "Hostname: $(hostname)"
echo ""

# Provera root privilegija
if [ "$EUID" -ne 0 ]; then
    echo -e "${YELLOW}WARNING: Not running as root. Some checks may be incomplete.${NC}"
    echo "For full audit, run: sudo ./master_audit.sh"
    echo ""
fi

pause() {
    echo ""
    read -p "Press Enter to continue to next module..."
    echo ""
}

echo -e "${BLUE}=== MODULE 1: FILE PERMISSIONS ===${NC}"
./file_permissions_audit.sh
pause

echo -e "${BLUE}=== MODULE 2: USERS & AUTHENTICATION ===${NC}"
./user_audit.sh
pause

echo -e "${BLUE}=== MODULE 3: NETWORK & FIREWALL ===${NC}"
echo "[Placeholder - Član 2 će implementirati]"
echo "Provere: iptables, otvoreni portovi, IPv6, routing"
pause

echo -e "${BLUE}=== MODULE 4: SERVICES ===${NC}"
echo "[Placeholder - Član 2 će implementirati]"
echo "Provere: Apache, MySQL, SSH, cron, systemd servisi"
pause

echo -e "${BLUE}=== MODULE 5: OS & KERNEL ===${NC}"
echo "[Placeholder - Član 3 će implementirati]"
echo "Provere: kernel verzija, uptime, NTP, paketi"
pause

echo -e "${BLUE}=== MODULE 6: LOGGING ===${NC}"
echo "[Placeholder - Član 3 će implementirati]"
echo "Provere: rsyslog konfiguracija, remote logging, log rotacija"
echo ""

echo -e "${GREEN}"
echo "╔════════════════════════════════════════════════════════╗"
echo "║                    AUDIT COMPLETE                      ║"
echo "║   Review the output above for security issues         ║"
echo "║   Save output: ./master_audit.sh > audit_report.txt   ║"
echo "╚════════════════════════════════════════════════════════╝"
echo -e "${NC}"