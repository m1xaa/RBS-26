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

echo -e "${GREEN}"
echo "╔════════════════════════════════════════════════════════╗"
echo "║                    AUDIT COMPLETE                      ║"
echo "╚════════════════════════════════════════════════════════╝"
echo -e "${NC}"
