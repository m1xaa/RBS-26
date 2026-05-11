RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "          SYSTEM REVIEW"
echo "========================================"
echo "Running as: $(whoami)"
echo "Date: $(date)"
echo ""

# 1. Operating system
echo -e "${YELLOW}[1] OPERATING SYSTEM:${NC}"
echo "Document: identify distribution and check if it is still supported"
echo ""

if [ -f /etc/os-release ]; then
    echo "  /etc/os-release:"
    grep -E "^(NAME|VERSION|PRETTY_NAME|ID|VERSION_ID)=" /etc/os-release | while read line; do
        echo "    $line"
    done
fi

if [ -f /etc/debian_version ]; then
    echo "  Debian version: $(cat /etc/debian_version)"
fi

if [ -f /etc/redhat-release ]; then
    echo "  RedHat-based: $(cat /etc/redhat-release)"
fi

if [ -f /etc/fedora-release ]; then
    echo "  Fedora: $(cat /etc/fedora-release)"
fi

if command -v lsb_release >/dev/null 2>&1; then
    echo ""
    echo "  lsb_release -a:"
    lsb_release -a 2>/dev/null | while read line; do
        echo "    $line"
    done
fi
echo ""
echo "Check if this distribution version is still supported."
echo "End-of-life versions will not receive security patches."
echo ""

# 2. Kernel version
echo -e "${YELLOW}[2] KERNEL VERSION:${NC}"
echo "Command: uname -a"
echo "Document: check for known vulnerabilities for this specific version"
echo ""
uname -a
echo ""
echo "  Kernel release: $(uname -r)"
echo "  Architecture:   $(uname -m)"
echo ""

# Uptime - indicator of last kernel upgrade
echo "  Uptime (long uptime = kernel likely not patched recently):"
uptime
echo ""

# 3. Time management
echo -e "${YELLOW}[3] TIME MANAGEMENT:${NC}"
echo "Document: NTP sync is important for logs, SSL certs and authentication"
echo ""

# Timezone
echo "  Timezone:"
if [ -f /etc/timezone ]; then
    echo "    /etc/timezone: $(cat /etc/timezone)"
fi
if command -v timedatectl >/dev/null 2>&1; then
    timedatectl 2>/dev/null | grep -E "Time zone|System clock|NTP" | while read line; do
        echo "    $line"
    done
fi
echo ""
echo "  For sensitive production systems, UTC is recommended"
echo "  (no daylight saving time jumps in logs)."
echo ""

# NTP daemon running?
echo "  NTP daemon status:"
NTP_RUNNING=0
for proc in ntpd chronyd systemd-timesyncd; do
    if pgrep -x "$proc" >/dev/null 2>&1; then
        echo -e "    ${GREEN}$proc is running${NC}"
        NTP_RUNNING=1
    fi
done

if [ "$NTP_RUNNING" -eq 0 ]; then
    echo -e "    ${RED}No NTP daemon found running${NC}"
    echo "    System time may drift - bad for logs and authentication"
fi
echo ""

# NTP peers
if command -v ntpq >/dev/null 2>&1; then
    echo "  NTP peers (ntpq -p -n):"
    ntpq -p -n 2>/dev/null | head -10 | while read line; do
        echo "    $line"
    done
elif command -v chronyc >/dev/null 2>&1; then
    echo "  Chrony sources:"
    chronyc sources 2>/dev/null | head -10 | while read line; do
        echo "    $line"
    done
elif command -v timedatectl >/dev/null 2>&1; then
    echo "  timedatectl timesync-status:"
    timedatectl timesync-status 2>/dev/null | head -10 | while read line; do
        echo "    $line"
    done
fi
echo ""

# 4. Installed packages
echo -e "${YELLOW}[4] INSTALLED PACKAGES:${NC}"
echo "Document: limit packages to strict minimum to reduce attack surface"
echo "  Web server should not have: X, Gnome, KDE, games, compilers, etc."
echo ""

if command -v dpkg >/dev/null 2>&1; then
    TOTAL=$(dpkg -l 2>/dev/null | grep -c "^ii ")
    echo "  Total installed packages (dpkg): $TOTAL"
elif command -v rpm >/dev/null 2>&1; then
    TOTAL=$(rpm -qa 2>/dev/null | wc -l)
    echo "  Total installed packages (rpm): $TOTAL"
fi
echo ""

# Check for unnecessary packages on a server
echo "  Checking for packages that usually should NOT be on a server:"
UNNECESSARY="xserver-xorg gnome-shell kde-plasma-desktop xfce4 gcc make gdb telnet rsh-client nis"
for pkg in $UNNECESSARY; do
    if command -v dpkg >/dev/null 2>&1; then
        if dpkg -l "$pkg" 2>/dev/null | grep -q "^ii"; then
            echo -e "    ${RED}Found: $pkg${NC} (consider removing if not needed)"
        fi
    elif command -v rpm >/dev/null 2>&1; then
        if rpm -q "$pkg" >/dev/null 2>&1; then
            echo -e "    ${RED}Found: $pkg${NC} (consider removing if not needed)"
        fi
    fi
done
echo ""

# Pending security updates
echo "  Pending updates:"
if command -v apt >/dev/null 2>&1; then
    UPDATES=$(apt list --upgradable 2>/dev/null | grep -v "^Listing" | wc -l)
    SECURITY=$(apt list --upgradable 2>/dev/null | grep -i security | wc -l)
    echo "    Total upgradable: $UPDATES"
    echo "    Security updates: $SECURITY"
    if [ "$SECURITY" -gt 0 ]; then
        echo -e "    ${RED}WARNING: there are pending security updates${NC}"
    fi
elif command -v yum >/dev/null 2>&1; then
    yum check-update --security 2>/dev/null | tail -20
elif command -v dnf >/dev/null 2>&1; then
    dnf updateinfo list security 2>/dev/null | tail -20
fi
echo ""

# 5. Logging configuration
echo -e "${YELLOW}[5] LOGGING:${NC}"
echo "Document: logs should ideally be sent to a remote system as backup"
echo ""

# Which logging daemon is used
echo "  Logging daemon:"
for proc in rsyslogd syslog-ng systemd-journald; do
    if pgrep -x "$proc" >/dev/null 2>&1; then
        echo -e "    ${GREEN}$proc is running${NC}"
    fi
done
echo ""

# rsyslog config review
if [ -f /etc/rsyslog.conf ]; then
    echo "  rsyslog file/dir permission defaults (/etc/rsyslog.conf):"
    grep -E "^\\\$File(Owner|Group|CreateMode)|^\\\$DirCreateMode|^\\\$Umask" /etc/rsyslog.conf 2>/dev/null | while read line; do
        echo "    $line"
    done
    echo ""

    # Remote logging
    echo "  Remote logging configuration:"
    REMOTE=$(grep -E "^[^#]*@" /etc/rsyslog.conf /etc/rsyslog.d/*.conf 2>/dev/null | grep -v "^$")
    if [ -n "$REMOTE" ]; then
        echo -e "    ${GREEN}Remote logging is configured:${NC}"
        echo "$REMOTE" | while read line; do
            echo "    $line"
        done
    else
        echo -e "    ${YELLOW}No remote logging configured${NC}"
        echo "    Recommendation: forward logs with @servername (UDP) or @@servername (TCP)"
    fi
    echo ""

    # Is rsyslog accepting incoming logs?
    echo "  Incoming syslog reception:"
    INCOMING=$(grep -E "^[^#]*\\\$(ModLoad imudp|ModLoad imtcp|UDPServerRun|InputTCPServerRun)" /etc/rsyslog.conf /etc/rsyslog.d/*.conf 2>/dev/null)
    if [ -n "$INCOMING" ]; then
        echo -e "    ${YELLOW}This system accepts logs from network:${NC}"
        echo "$INCOMING" | while read line; do
            echo "      $line"
        done
        echo "    Verify this is intended (this should be a log server)."
    else
        echo "    System does not accept network syslog (good for non-log-servers)."
    fi
fi
echo ""

# Logrotate
echo "  Log rotation:"
if [ -f /etc/logrotate.conf ]; then
    echo -e "    ${GREEN}/etc/logrotate.conf exists${NC}"
    grep -E "^(weekly|daily|monthly|rotate|compress)" /etc/logrotate.conf 2>/dev/null | head -5 | while read line; do
        echo "      $line"
    done
else
    echo -e "    ${YELLOW}/etc/logrotate.conf not found${NC}"
fi
echo ""

echo "========================================"
echo "       END OF SYSTEM REVIEW"
echo "========================================"