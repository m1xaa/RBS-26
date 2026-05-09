RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "     FILE PERMISSIONS AUDIT"
echo "========================================"
echo "Running as: $(whoami)"
echo "Date: $(date)"
echo ""

# 1. SETUID fajlovi
echo -e "${YELLOW}[1] SETUID FILES (runs with owner privileges):${NC}"
echo "These files run with owner's privileges - potential security risk"
echo "Command: find / -perm -4000 -ls 2>/dev/null"
echo ""
find / -perm -4000 -ls 2>/dev/null | head -20
COUNT=$(find / -perm -4000 -type f 2>/dev/null | wc -l)
echo ""
echo "Total setuid files found: $COUNT"
echo "For each file, verify if it's legitimate and permissions are correct"
echo ""

# 2. WORLD-READABLE + WORLD-WRITABLE fajlovi
echo -e "${YELLOW}[2] FILES READABLE AND WRITABLE BY ANY USER:${NC}"
echo "Command: find / -type f -perm -006 2>/dev/null | grep -v '/proc'"
echo "These files can be read and written by any user - security risk"
echo ""
find / -type f -perm -006 2>/dev/null | grep -v '/proc' | head -15
echo ""

# 3. WORLD-WRITABLE fajlovi
echo -e "${YELLOW}[3] FILES WRITABLE BY ANY USER:${NC}"
echo "Command: find / -type f -perm -002 2>/dev/null | grep -v '/proc'"
echo ""
find / -type f -perm -002 2>/dev/null | grep -v '/proc' | head -15
echo ""

# 4. Backup fajlovi - specifically /etc/shadow.backup (page 9)
echo -e "${YELLOW}[4] CHECKING FOR UNSAFE BACKUP FILES:${NC}"
echo "Document mentions: /etc/shadow.backup being world-readable"
if [ -f /etc/shadow.backup ]; then
    PERM=$(ls -l /etc/shadow.backup 2>/dev/null)
    echo -e "${RED}WARNING: /etc/shadow.backup exists: $PERM${NC}"
else
    echo "No /etc/shadow.backup found (good)"
fi
echo ""

# Also check common backup locations (/backup directory)
if [ -d /backup ]; then
    echo "Checking /backup directory permissions:"
    ls -la /backup/ 2>/dev/null | head -10
fi
echo ""

# 5. Mounted partitions - noatime, noexec, nosuid
echo -e "${YELLOW}[5] MOUNTED PARTITIONS REVIEW:${NC}"
echo "Review /etc/fstab for security options:"
echo "  - noatime: prevents update of inode access time (bad for forensics)"
echo "  - noexec/nosuid: recommended for /tmp and /home"
echo ""
grep -v "^#" /etc/fstab 2>/dev/null | while read line; do
    echo "  $line"
    if echo "$line" | grep -q "/tmp" || echo "$line" | grep -q "/home"; then
        if echo "$line" | grep -q "noexec"; then
            echo "has noexec"
        else
            echo "missing noexec"
        fi
        if echo "$line" | grep -q "nosuid"; then
            echo "has nosuid"
        else
            echo "missing nosuid"
        fi
    fi
    if echo "$line" | grep -q "noatime"; then
        echo "has noatime (prevents access time logging)"
    fi
done
echo ""

echo "========================================"
echo "     END OF FILE PERMISSIONS AUDIT"
echo "========================================"