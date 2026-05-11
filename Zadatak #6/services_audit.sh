RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "         SERVICES REVIEW"
echo "========================================"
echo "Running as: $(whoami)"
echo "Date: $(date)"
echo ""

# 1. Running services
echo -e "${YELLOW}[1] RUNNING SERVICES (ps -ef):${NC}"
echo "Document: identify running services to reduce attack surface"
echo ""
ps -ef | grep -v "\[.*\]" | grep -v "ps -ef" | head -25
echo ""
echo "  Total running processes: $(ps -ef | wc -l)"
echo ""

# Active systemd services
if command -v systemctl >/dev/null 2>&1; then
    echo "  Active systemd services:"
    systemctl list-units --type=service --state=running --no-pager 2>/dev/null | head -20
fi
echo ""

# 2. Listening TCP/UDP ports
echo -e "${YELLOW}[2] LISTENING SERVICES (TCP/UDP):${NC}"
echo "Document: each listening service increases attack surface"
echo ""

if command -v ss >/dev/null 2>&1; then
    echo "  TCP listening sockets:"
    ss -tlnp 2>/dev/null | head -15
    echo ""
    echo "  UDP listening sockets:"
    ss -ulnp 2>/dev/null | head -15
elif command -v lsof >/dev/null 2>&1; then
    lsof -i -n -P 2>/dev/null | grep LISTEN | head -15
fi
echo ""

# 3. SSH configuration
echo -e "${YELLOW}[3] SSH CONFIGURATION (/etc/ssh/sshd_config):${NC}"
echo "Document: check PermitRootLogin, Protocol, AllowTcpForwarding, Port"
echo ""

SSHD_CONFIG="/etc/ssh/sshd_config"
if [ -f "$SSHD_CONFIG" ]; then
    # PermitRootLogin
    ROOT_LOGIN=$(grep -E "^[^#]*PermitRootLogin" $SSHD_CONFIG | awk '{print $2}')
    echo -n "  PermitRootLogin: "
    if [ -z "$ROOT_LOGIN" ]; then
        echo -e "${RED}not set (default is 'yes' - bad)${NC}"
    elif [ "$ROOT_LOGIN" = "no" ] || [ "$ROOT_LOGIN" = "prohibit-password" ]; then
        echo -e "${GREEN}$ROOT_LOGIN (good)${NC}"
    else
        echo -e "${RED}$ROOT_LOGIN (root can log in directly - bad)${NC}"
    fi

    # Protocol
    PROTOCOL=$(grep -E "^[^#]*Protocol" $SSHD_CONFIG | awk '{print $2}')
    echo -n "  Protocol: "
    if [ "$PROTOCOL" = "1" ]; then
        echo -e "${RED}SSHv1 enabled (very weak)${NC}"
    elif [ "$PROTOCOL" = "2" ]; then
        echo -e "${GREEN}SSHv2 only (good)${NC}"
    else
        echo "default (SSHv2 on modern systems)"
    fi

    # Port
    PORT=$(grep -E "^[^#]*Port " $SSHD_CONFIG | awk '{print $2}')
    echo "  Port: ${PORT:-22 (default)}"
    if [ -z "$PORT" ] || [ "$PORT" = "22" ]; then
        echo -e "    ${YELLOW}Default port - target of automated brute-force scans${NC}"
    fi

    # PasswordAuthentication
    PASS_AUTH=$(grep -E "^[^#]*PasswordAuthentication" $SSHD_CONFIG | awk '{print $2}')
    echo -n "  PasswordAuthentication: "
    if [ "$PASS_AUTH" = "no" ]; then
        echo -e "${GREEN}no (SSH keys required - good)${NC}"
    else
        echo -e "${YELLOW}${PASS_AUTH:-yes (default)} - prefer SSH keys${NC}"
    fi

    # AllowTcpForwarding
    TCP_FWD=$(grep -E "^[^#]*AllowTcpForwarding" $SSHD_CONFIG | awk '{print $2}')
    echo -n "  AllowTcpForwarding: "
    if [ "$TCP_FWD" = "no" ]; then
        echo -e "${GREEN}no (good for non-bounce hosts)${NC}"
    else
        echo -e "${YELLOW}${TCP_FWD:-yes (default)}${NC}"
    fi

    # PermitEmptyPasswords
    EMPTY=$(grep -E "^[^#]*PermitEmptyPasswords" $SSHD_CONFIG | awk '{print $2}')
    echo -n "  PermitEmptyPasswords: "
    if [ "$EMPTY" = "no" ]; then
        echo -e "${GREEN}no (good)${NC}"
    elif [ "$EMPTY" = "yes" ]; then
        echo -e "${RED}yes (CRITICAL - anyone can log in without password)${NC}"
    else
        echo "default (no)"
    fi
else
    echo "  $SSHD_CONFIG not found - SSH server may not be installed"
fi
echo ""

# 4. MySQL/MariaDB configuration
echo -e "${YELLOW}[4] MYSQL/MARIADB CONFIGURATION:${NC}"
echo "Document: bind-address should be 127.0.0.1 if used only locally"
echo ""

MYSQL_CONFIGS="/etc/mysql/my.cnf /etc/my.cnf /etc/mysql/mariadb.conf.d/50-server.cnf"
MYSQL_FOUND=0
for cfg in $MYSQL_CONFIGS; do
    if [ -f "$cfg" ]; then
        MYSQL_FOUND=1
        echo "  Configuration file: $cfg"

        BIND=$(grep -E "^[^#]*bind-address" $cfg 2>/dev/null | head -1)
        if [ -n "$BIND" ]; then
            echo "    $BIND"
            if echo "$BIND" | grep -q "127.0.0.1"; then
                echo -e "    ${GREEN}bound to localhost only (good)${NC}"
            elif echo "$BIND" | grep -q "0.0.0.0"; then
                echo -e "    ${RED}listens on all interfaces - database exposed externally${NC}"
            fi
        else
            echo -e "    ${YELLOW}bind-address not explicitly set${NC}"
        fi
    fi
done

if [ "$MYSQL_FOUND" -eq 0 ]; then
    echo "  No MySQL/MariaDB configuration found (service not installed)"
fi

# Try to connect to MySQL without password (root)
if command -v mysql >/dev/null 2>&1; then
    if mysql -u root -e "SELECT 1;" >/dev/null 2>&1; then
        echo -e "  ${RED}WARNING: MySQL root has no password set!${NC}"
    fi
fi
echo ""

# 5. Apache configuration
echo -e "${YELLOW}[5] APACHE CONFIGURATION:${NC}"
echo "Document: check user, ServerTokens, ServerSignature, directory listing"
echo ""

APACHE_CONFIGS="/etc/apache2/apache2.conf /etc/httpd/conf/httpd.conf"
APACHE_FOUND=0
for cfg in $APACHE_CONFIGS; do
    if [ -f "$cfg" ]; then
        APACHE_FOUND=1
        echo "  Configuration file: $cfg"

        # User
        APACHE_USER=$(grep -E "^User " $cfg 2>/dev/null | head -1 | awk '{print $2}')
        echo "  User directive: ${APACHE_USER:-not set in this file}"
        if echo "$APACHE_USER" | grep -qi "root"; then
            echo -e "    ${RED}Apache running as root - CRITICAL${NC}"
        fi

        # ServerTokens
        TOKENS=$(grep -rE "^[^#]*ServerTokens" /etc/apache2/ /etc/httpd/ 2>/dev/null | head -1)
        if [ -n "$TOKENS" ]; then
            echo "  $TOKENS"
            if echo "$TOKENS" | grep -qi "Prod"; then
                echo -e "    ${GREEN}ServerTokens Prod (good - minimal info leak)${NC}"
            else
                echo -e "    ${YELLOW}ServerTokens leaks version info - set to Prod${NC}"
            fi
        else
            echo -e "  ${YELLOW}ServerTokens not set - leaks Apache version${NC}"
        fi

        # ServerSignature
        SIG=$(grep -rE "^[^#]*ServerSignature" /etc/apache2/ /etc/httpd/ 2>/dev/null | head -1)
        if [ -n "$SIG" ]; then
            echo "  $SIG"
            if echo "$SIG" | grep -qi "Off"; then
                echo -e "    ${GREEN}ServerSignature Off (good)${NC}"
            else
                echo -e "    ${YELLOW}ServerSignature On - leaks version on error pages${NC}"
            fi
        fi

        # Directory listing (Indexes)
        INDEXES=$(grep -rE "Options.*Indexes" /etc/apache2/sites-enabled/ /etc/httpd/conf.d/ 2>/dev/null | grep -v "\-Indexes" | head -3)
        if [ -n "$INDEXES" ]; then
            echo -e "  ${YELLOW}Directory listing (Indexes) enabled somewhere:${NC}"
            echo "$INDEXES" | while read line; do
                echo "    $line"
            done
            echo "    Recommendation: replace 'Indexes' with '-Indexes' to disable directory listing"
        fi
    fi
done

if [ "$APACHE_FOUND" -eq 0 ]; then
    echo "  No Apache configuration found (service not installed)"
fi
echo ""

# 6. Crontab review
echo -e "${YELLOW}[6] CRONTAB REVIEW:${NC}"
echo "Document: scripts called from cron must not be world-writable"
echo ""

# System crontabs
if [ -f /etc/crontab ]; then
    echo "  /etc/crontab entries:"
    grep -v "^#" /etc/crontab 2>/dev/null | grep -v "^$" | while read line; do
        echo "    $line"
    done
    echo ""
fi

# User crontabs
if [ -d /var/spool/cron/crontabs ]; then
    echo "  User crontabs in /var/spool/cron/crontabs/:"
    ls -la /var/spool/cron/crontabs/ 2>/dev/null | tail -n +2
elif [ -d /var/spool/cron ]; then
    echo "  User crontabs in /var/spool/cron/:"
    ls -la /var/spool/cron/ 2>/dev/null | tail -n +2
fi
echo ""

# Check permissions of scripts referenced in cron
echo "  Checking scripts referenced in cron for unsafe permissions:"
for cronfile in /etc/crontab /etc/cron.d/* /var/spool/cron/crontabs/* /var/spool/cron/*; do
    if [ -f "$cronfile" ] && [ -r "$cronfile" ]; then
        grep -v "^#" "$cronfile" 2>/dev/null | grep -oE "/[a-zA-Z0-9_/.-]+\.sh" | sort -u | while read script; do
            if [ -f "$script" ]; then
                PERM=$(stat -c "%a" "$script" 2>/dev/null)
                OWNER=$(stat -c "%U" "$script" 2>/dev/null)
                # Check if world-writable
                LAST_DIGIT="${PERM: -1}"
                if [ "$LAST_DIGIT" -ge 2 ] 2>/dev/null && [ "$LAST_DIGIT" -ne 4 ] && [ "$LAST_DIGIT" -ne 5 ]; then
                    echo -e "    ${RED}WORLD-WRITABLE: $script (perms: $PERM, owner: $OWNER)${NC}"
                    echo "      An attacker could modify this and gain $OWNER's privileges on next run"
                else
                    echo "    OK: $script (perms: $PERM, owner: $OWNER)"
                fi
            fi
        done
    fi
done
echo ""

echo "========================================"
echo "      END OF SERVICES REVIEW"
echo "========================================"