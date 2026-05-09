#!/usr/bin/env bash

# Network and Firewall Review
# Defensive audit script - does not modify system configuration.

set -u

HOSTNAME_VALUE="$(hostname 2>/dev/null || echo unknown-host)"
TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
OUT_DIR="${1:-/tmp/audit-network-firewall-${HOSTNAME_VALUE}-${TIMESTAMP}}"
REPORT="$OUT_DIR/report.txt"

mkdir -p "$OUT_DIR"

warning_count=0
info_count=0
ok_count=0

line() {
    echo "------------------------------------------------------------" | tee -a "$REPORT"
}

section() {
    echo "" | tee -a "$REPORT"
    line
    echo "$1" | tee -a "$REPORT"
    line
}

info() {
    info_count=$((info_count + 1))
    echo "[INFO] $1" | tee -a "$REPORT"
}

ok() {
    ok_count=$((ok_count + 1))
    echo "[OK] $1" | tee -a "$REPORT"
}

warn() {
    warning_count=$((warning_count + 1))
    echo "[WARNING] $1" | tee -a "$REPORT"
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

capture() {
    local name="$1"
    local cmd="$2"
    local file="$OUT_DIR/${name}.txt"

    echo "[COMMAND] $cmd" > "$file"
    echo "" >> "$file"

    bash -c "$cmd" >> "$file" 2>&1

    info "Sačuvan izlaz komande u: $file"
}

get_cmd_output() {
    local cmd="$1"
    bash -c "$cmd" 2>/dev/null || true
}

is_root() {
    [ "${EUID:-$(id -u)}" -eq 0 ]
}

get_iptables_policy() {
    local chain="$1"

    if command_exists iptables; then
        iptables -S "$chain" 2>/dev/null | awk -v c="$chain" '$1 == "-P" && $2 == c {print $3}'
    fi
}

get_ip6tables_policy() {
    local chain="$1"

    if command_exists ip6tables; then
        ip6tables -S "$chain" 2>/dev/null | awk -v c="$chain" '$1 == "-P" && $2 == c {print $3}'
    fi
}

ipv6_has_global_address() {
    if command_exists ip; then
        ip -6 addr show scope global 2>/dev/null | grep -q "inet6"
    else
        return 1
    fi
}

port_listening_any_address() {
    local port="$1"
    local ss_output="$2"

    echo "$ss_output" | grep -E "LISTEN.*(0\.0\.0\.0|\[::\]|\*):${port}([[:space:]]|$)" >/dev/null 2>&1
}

port_listening_localhost_only() {
    local port="$1"
    local ss_output="$2"

    echo "$ss_output" | grep -E "LISTEN.*(127\.0\.0\.1|\[::1\]):${port}([[:space:]]|$)" >/dev/null 2>&1
}

check_ssh_firewall_rule_too_broad() {
    if ! command_exists iptables; then
        return
    fi

    local rules
    rules="$(iptables -S INPUT 2>/dev/null || true)"

    local ssh_accept_rules
    ssh_accept_rules="$(echo "$rules" | grep -E -- '^-A INPUT .*--dport 22 .* -j ACCEPT|^-A INPUT .*--dport ssh .* -j ACCEPT' || true)"

    if [ -n "$ssh_accept_rules" ]; then
        if echo "$ssh_accept_rules" | grep -vq -- "-s "; then
            warn "SSH port 22 ima ACCEPT pravilo bez očiglednog source IP ograničenja. Preporuka: dozvoliti SSH samo sa trusted IP adresa ili VPN-a."
        else
            ok "SSH firewall pravilo izgleda kao da ima source IP ograničenje."
        fi
    fi
}

check_persistent_firewall() {
    section "PERSISTENT FIREWALL CHECK"

    local found=0

    local files=(
        "/etc/iptables/rules.v4"
        "/etc/iptables/rules.v6"
        "/etc/iptables.up.rules"
        "/etc/network/if-pre-up.d/iptables"
        "/etc/sysconfig/iptables"
        "/etc/sysconfig/ip6tables"
        "/etc/nftables.conf"
        "/etc/ufw/ufw.conf"
    )

    for f in "${files[@]}"; do
        if [ -e "$f" ]; then
            found=1
            info "Pronađen potencijalni persistent firewall fajl: $f"
        fi
    done

    if command_exists systemctl; then
        local services=(
            "nftables"
            "netfilter-persistent"
            "iptables"
            "ip6tables"
            "firewalld"
            "ufw"
        )

        for svc in "${services[@]}"; do
            if systemctl list-unit-files 2>/dev/null | grep -q "^${svc}.service"; then
                local state
                state="$(systemctl is-enabled "$svc" 2>/dev/null || true)"

                if [ "$state" = "enabled" ]; then
                    found=1
                    info "Firewall servis je enabled: $svc"
                else
                    info "Firewall servis postoji, ali nije enabled: $svc ($state)"
                fi
            fi
        done
    fi

    if [ "$found" -eq 0 ]; then
        warn "Nije pronađena očigledna persistent firewall konfiguracija. Pravila možda neće ostati aktivna posle restarta."
    else
        ok "Postoji neka forma persistent firewall konfiguracije."
    fi
}

check_basic_network() {
    section "BASIC NETWORK INFORMATION"

    if command_exists ip; then
        capture "ip_addr" "ip addr"
        capture "ip_addr_brief" "ip -br addr"
        capture "ip_route" "ip route"
        capture "ip_route_all" "ip route show table all"
    elif command_exists ifconfig; then
        capture "ifconfig" "ifconfig -a"
    else
        warn "Nisu pronađene komande ip ili ifconfig."
    fi

    if command_exists route; then
        capture "route_n" "route -n"
    fi

    if [ -f /etc/resolv.conf ]; then
        capture "resolv_conf" "cat /etc/resolv.conf"

        if grep -qE "^[[:space:]]*nameserver[[:space:]]+" /etc/resolv.conf; then
            ok "/etc/resolv.conf sadrži nameserver konfiguraciju."
        else
            warn "/etc/resolv.conf ne sadrži nameserver konfiguraciju."
        fi
    else
        warn "/etc/resolv.conf ne postoji."
    fi

    if [ -f /etc/hosts ]; then
        capture "hosts" "cat /etc/hosts"

        local hosts_perm
        hosts_perm="$(stat -c "%a" /etc/hosts 2>/dev/null || echo unknown)"

        info "/etc/hosts permisije: $hosts_perm"

        if [ "$hosts_perm" != "unknown" ]; then
            local last_digit
            last_digit="${hosts_perm: -1}"

            if [ "$last_digit" -ge 2 ] 2>/dev/null; then
                warn "/etc/hosts je world-writable ili previše otvoren. To može omogućiti lokalno preusmeravanje domena."
            else
                ok "/etc/hosts nije world-writable."
            fi
        fi
    else
        warn "/etc/hosts ne postoji."
    fi

    if [ -f /etc/nsswitch.conf ]; then
        capture "nsswitch_conf" "cat /etc/nsswitch.conf"
        info "Sačuvana je nsswitch konfiguracija, korisno ako sistem koristi LDAP/NIS/AD."
    fi
}

check_open_ports() {
    section "OPEN PORTS AND LISTENING SERVICES"

    local ss_output=""

    if command_exists ss; then
        ss_output="$(ss -tulpen 2>/dev/null || ss -tuln 2>/dev/null || true)"
        echo "$ss_output" > "$OUT_DIR/open_ports_ss.txt"
        info "Sačuvan spisak otvorenih portova: $OUT_DIR/open_ports_ss.txt"
    elif command_exists lsof; then
        capture "open_ports_lsof" "lsof -i -n -P"
        ss_output="$(lsof -i -n -P 2>/dev/null || true)"
    elif command_exists netstat; then
        ss_output="$(netstat -tulpen 2>/dev/null || netstat -tuln 2>/dev/null || true)"
        echo "$ss_output" > "$OUT_DIR/open_ports_netstat.txt"
        info "Sačuvan spisak otvorenih portova: $OUT_DIR/open_ports_netstat.txt"
    else
        warn "Nisu pronađene komande ss, lsof ili netstat."
        return
    fi

    echo "$ss_output" | grep -E "(0\.0\.0\.0|\[::\]|\*):" > "$OUT_DIR/public_listeners.txt" 2>/dev/null || true

    if [ -s "$OUT_DIR/public_listeners.txt" ]; then
        warn "Postoje servisi koji slušaju na svim adresama. Proveri: $OUT_DIR/public_listeners.txt"
    else
        ok "Nisu pronađeni servisi koji očigledno slušaju na svim adresama."
    fi

    if port_listening_any_address "22" "$ss_output"; then
        warn "SSH sluša na svim adresama. Preporuka: ograničiti pristup firewall-om na administratorske IP adrese/VPN."
    elif port_listening_localhost_only "22" "$ss_output"; then
        ok "SSH sluša samo lokalno."
    fi

    if port_listening_any_address "80" "$ss_output"; then
        info "HTTP port 80 sluša javno. To je očekivano ako je server javni web server."
    fi

    if port_listening_any_address "443" "$ss_output"; then
        info "HTTPS port 443 sluša javno. To je očekivano ako je server javni web server."
    fi

    if port_listening_any_address "3306" "$ss_output"; then
        warn "MySQL/MariaDB port 3306 sluša na svim adresama. Ako bazu koristi samo lokalna aplikacija, bind-address treba da bude 127.0.0.1."
    elif port_listening_localhost_only "3306" "$ss_output"; then
        ok "MySQL/MariaDB port 3306 sluša samo lokalno."
    fi

    if port_listening_any_address "5432" "$ss_output"; then
        warn "PostgreSQL port 5432 sluša na svim adresama. Proveriti da li je eksterni pristup stvarno potreban."
    fi

    if port_listening_any_address "6379" "$ss_output"; then
        warn "Redis port 6379 sluša na svim adresama. Redis obično ne treba da bude javno izložen."
    fi

    if port_listening_any_address "27017" "$ss_output"; then
        warn "MongoDB port 27017 sluša na svim adresama. Proveriti da li je eksterni pristup stvarno potreban."
    fi
}

check_ipv4_firewall() {
    section "IPV4 FIREWALL REVIEW"

    if command_exists iptables; then
        capture "iptables_list" "iptables -L -v -n"
        capture "iptables_rules" "iptables -S"
        capture "iptables_save" "iptables-save"

        local input_policy
        local output_policy
        local forward_policy

        input_policy="$(get_iptables_policy INPUT)"
        output_policy="$(get_iptables_policy OUTPUT)"
        forward_policy="$(get_iptables_policy FORWARD)"

        info "IPv4 INPUT policy: ${input_policy:-unknown}"
        info "IPv4 OUTPUT policy: ${output_policy:-unknown}"
        info "IPv4 FORWARD policy: ${forward_policy:-unknown}"

        if [ "$input_policy" = "ACCEPT" ]; then
            warn "IPv4 INPUT policy je ACCEPT. Bolja praksa je DROP i eksplicitno dozvoljavanje samo potrebnih portova."
        elif [ "$input_policy" = "DROP" ]; then
            ok "IPv4 INPUT policy je DROP."
        else
            warn "Nije moguće jasno odrediti IPv4 INPUT policy."
        fi

        if [ "$output_policy" = "ACCEPT" ]; then
            warn "IPv4 OUTPUT policy je ACCEPT. To je često funkcionalno, ali za hardening je bolje ograničiti odlazni saobraćaj."
        elif [ "$output_policy" = "DROP" ]; then
            ok "IPv4 OUTPUT policy je DROP."
        fi

        if [ "$forward_policy" = "ACCEPT" ]; then
            local ip_forward
            ip_forward="$(cat /proc/sys/net/ipv4/ip_forward 2>/dev/null || echo unknown)"

            if [ "$ip_forward" = "1" ]; then
                warn "IPv4 FORWARD policy je ACCEPT i ip_forward je uključen. Server može rutirati saobraćaj."
            else
                info "IPv4 FORWARD policy je ACCEPT, ali ip_forward nije uključen. Manji rizik ako server nije ruter."
            fi
        fi

        local rule_count
        rule_count="$(iptables -S 2>/dev/null | grep -c "^-A " || true)"

        if [ "$rule_count" -eq 0 ]; then
            warn "Nema eksplicitnih IPv4 firewall pravila. Ako su policy vrednosti ACCEPT, server je široko otvoren."
        else
            ok "Pronađena su eksplicitna IPv4 firewall pravila: $rule_count"
        fi

        check_ssh_firewall_rule_too_broad
    else
        warn "iptables nije pronađen. Sistem možda koristi nftables, firewalld ili ufw."
    fi

    if command_exists nft; then
        capture "nft_ruleset" "nft list ruleset"
        info "Sistem ima nftables podršku. Pregledaj nft_ruleset.txt za stvarna pravila ako se koristi nftables."
    fi

    if command_exists ufw; then
        capture "ufw_status" "ufw status verbose"
    fi

    if command_exists firewall-cmd; then
        capture "firewalld_state" "firewall-cmd --state; firewall-cmd --list-all"
    fi
}

check_ipv6_firewall() {
    section "IPV6 FIREWALL REVIEW"

    local ipv6_active=0

    if ipv6_has_global_address; then
        ipv6_active=1
        info "IPv6 globalna adresa je aktivna na sistemu."
    else
        info "Nije pronađena IPv6 globalna adresa."
    fi

    if command_exists ip6tables; then
        capture "ip6tables_list" "ip6tables -L -v -n"
        capture "ip6tables_rules" "ip6tables -S"
        capture "ip6tables_save" "ip6tables-save"

        local input_policy
        local output_policy
        local forward_policy

        input_policy="$(get_ip6tables_policy INPUT)"
        output_policy="$(get_ip6tables_policy OUTPUT)"
        forward_policy="$(get_ip6tables_policy FORWARD)"

        info "IPv6 INPUT policy: ${input_policy:-unknown}"
        info "IPv6 OUTPUT policy: ${output_policy:-unknown}"
        info "IPv6 FORWARD policy: ${forward_policy:-unknown}"

        local rule_count
        rule_count="$(ip6tables -S 2>/dev/null | grep -c "^-A " || true)"

        if [ "$ipv6_active" -eq 1 ]; then
            if [ "$input_policy" = "ACCEPT" ] && [ "$rule_count" -eq 0 ]; then
                warn "IPv6 je aktivan, a ip6tables izgleda otvoreno. IPv6 može zaobići dobro podešen IPv4 firewall."
            elif [ "$input_policy" = "ACCEPT" ]; then
                warn "IPv6 INPUT policy je ACCEPT. Proveriti da li IPv6 pravila odgovaraju IPv4 pravilima."
            elif [ "$input_policy" = "DROP" ]; then
                ok "IPv6 INPUT policy je DROP."
            fi
        else
            if [ "$input_policy" = "ACCEPT" ] && [ "$rule_count" -eq 0 ]; then
                info "IPv6 nema globalnu adresu, ali ip6tables je otvoren. Ako se IPv6 kasnije uključi, ovo može postati problem."
            fi
        fi
    else
        if [ "$ipv6_active" -eq 1 ]; then
            warn "IPv6 je aktivan, ali ip6tables nije pronađen. Proveriti da li nftables/firewalld pokriva IPv6."
        else
            info "ip6tables nije pronađen, ali nije pronađena ni aktivna globalna IPv6 adresa."
        fi
    fi
}

write_summary() {
    section "SUMMARY"

    echo "Audit directory: $OUT_DIR" | tee -a "$REPORT"
    echo "Report file: $REPORT" | tee -a "$REPORT"
    echo "" | tee -a "$REPORT"

    echo "OK checks: $ok_count" | tee -a "$REPORT"
    echo "Info messages: $info_count" | tee -a "$REPORT"
    echo "Warnings: $warning_count" | tee -a "$REPORT"

    echo "" | tee -a "$REPORT"

    if [ "$warning_count" -gt 0 ]; then
        echo "Zaključak: pronađene su stavke koje treba ručno proveriti." | tee -a "$REPORT"
    else
        echo "Zaključak: nema očiglednih upozorenja u network/firewall delu." | tee -a "$REPORT"
    fi
}

main() {
    section "NETWORK AND FIREWALL REVIEW"

    info "Host: $HOSTNAME_VALUE"
    info "Vreme pokretanja: $(date)"
    info "Izlazni direktorijum: $OUT_DIR"

    if is_root; then
        ok "Skripta je pokrenuta kao root/sudo."
    else
        warn "Skripta nije pokrenuta kao root/sudo. Neki rezultati mogu biti nepotpuni."
    fi

    check_basic_network
    check_open_ports
    check_ipv4_firewall
    check_ipv6_firewall
    check_persistent_firewall
    write_summary
}

main