RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "   USERS & AUTHENTICATION AUDIT"
echo "========================================"
echo "Running as: $(whoami)"
echo "Date: $(date)"
echo ""

# 1. Reviewing password file - users with UID 0
echo -e "${YELLOW}[1] USERS WITH UID 0 (only root should have this):${NC}"
echo "Document: Only root is supposed to have uid 0"
awk -F: '($3 == 0) {print $1 " (UID 0)"}' /etc/passwd
echo ""

# 2. Reviewing shadow file - password hashing algorithms
echo -e "${YELLOW}[2] PASSWORD HASH ALGORITHMS FROM /etc/shadow:${NC}"
echo "Document: avoid DES and MD5, prefer SHA-512"
echo ""
echo "Hash format identification:"
echo "  \$1\$ -> MD5 (WEAK - avoid)"
echo "  \$2\$ or \$2a\$ -> Blowfish"
echo "  \$5\$ -> SHA-256 (good)"
echo "  \$6\$ -> SHA-512 (best)"
echo "  no \$ sign -> DES (very weak, max 8 chars)"
echo ""

awk -F: '{print $1 ":" $2}' /etc/shadow 2>/dev/null | head -15 | while IFS=: read user hash; do
    if [ -z "$hash" ]; then
        echo -e "  ${RED}$user: NO PASSWORD (EMPTY)${NC}"
    elif [[ "$hash" == '$1$'* ]]; then
        echo -e "  ${RED}$user: MD5 (WEAK)${NC}"
    elif [[ "$hash" == '$2'* ]] || [[ "$hash" == '$2a$'* ]]; then
        echo -e "  ${YELLOW}$user: Blowfish${NC}"
    elif [[ "$hash" == '$5$'* ]]; then
        echo -e "  ${GREEN}$user: SHA-256${NC}"
    elif [[ "$hash" == '$6$'* ]]; then
        echo -e "  ${GREEN}$user: SHA-512 (BEST)${NC}"
    elif [[ "$hash" != '$'* ]] && [ -n "$hash" ]; then
        echo -e "  ${RED}$user: DES or legacy (VERY WEAK)${NC}"
    fi
done
echo ""

# 3. Default algorithm from PAM
echo -e "${YELLOW}[3] DEFAULT ENCRYPTION ALGORITHM (from /etc/pam.d/common-password):${NC}"
if [ -f /etc/pam.d/common-password ]; then
    grep -E "pam_unix\.so" /etc/pam.d/common-password 2>/dev/null | grep -v "^#" | while read line; do
        echo "  $line"
        if echo "$line" | grep -q "sha512"; then
            echo -e "  ${GREEN}✓ SHA-512 is configured (good)${NC}"
        elif echo "$line" | grep -q "md5"; then
            echo -e "  ${RED}✗ MD5 is configured (weak)${NC}"
        fi
    done
fi
echo ""

# 4. Password complexity with libpam-cracklib
echo -e "${YELLOW}[4] PASSWORD COMPLEXITY REQUIREMENTS (libpam-cracklib):${NC}"
if [ -f /etc/pam.d/common-password ]; then
    grep -E "pam_cracklib\.so|pam_unix\.so" /etc/pam.d/common-password | grep -v "^#" | while read line; do
        echo "  $line"
    done
    echo ""
    echo "Parameters to check:"
    echo "  - minlen: minimum password length"
    echo "  - difok: how many chars must be different from old password"
    echo "  - ucredit: number of uppercase letters required"
    echo "  - lcredit: number of lowercase letters required"
    echo "  - dcredit: number of digits required"
    echo "  - ocredit: number of special characters required"
fi
echo ""

# 5. Sudo configuration
echo -e "${YELLOW}[5] SUDO CONFIGURATION (/etc/sudoers):${NC}"
echo "Document: Check for NOPASSWD and dangerous command combinations"
echo ""
grep -v "^#" /etc/sudoers 2>/dev/null | grep -v "^$" | while read line; do
    echo "  $line"
    if echo "$line" | grep -q "NOPASSWD"; then
        echo -e "    ${RED}WARNING: NOPASSWD entry found - no password required!${NC}"
    fi
done
echo ""

# Check sudoers.d as well
if [ -d /etc/sudoers.d ]; then
    echo "Checking /etc/sudoers.d/:"
    grep -r -v "^#" /etc/sudoers.d/ 2>/dev/null | grep -v "^$" | while read line; do
        echo "  $line"
    done
fi
echo ""

# 6. Dangerous sudo commands
echo -e "${YELLOW}[6] DANGEROUS SUDO COMMAND COMBINATIONS:${NC}"
echo "Document: Users with access to /bin/chown and /bin/chmod can get root shell"
echo "Other dangerous commands: vi, less, find, awk, python, etc."
echo ""

for cmd in chown chmod vi vim less more find awk python perl; do
    grep -r "$cmd" /etc/sudoers /etc/sudoers.d/ 2>/dev/null | grep -v "^#" | while read line; do
        echo -e "  ${RED}DANGEROUS: $cmd allowed - $line${NC}"
        echo "    This can lead to privilege escalation to root"
    done
done
echo ""

# 7. Users with shell access
echo -e "${YELLOW}[7] USERS WITH SHELL ACCESS (/bin/bash, /bin/sh, etc.):${NC}"
echo "Document: Restrict shell access to prevent users from running commands"
echo ""
grep -E "/(bash|sh|zsh|dsh|tcsh)$" /etc/passwd | cut -d: -f1,7 | while read line; do
    echo "  $line"
done
echo ""
echo "Users with /bin/false or /usr/sbin/nologin cannot log in (good)"

echo "========================================"
echo "   END OF USERS & AUTHENTICATION AUDIT"
echo "========================================"
