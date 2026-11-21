#!/bin/sh
# ssl-expiry: days until a site's TLS certificate expires.
# Usage: ssl-expiry example.com [more domains]
[ $# -gt 0 ] || { echo "Usage: ssl-expiry <domain> [domain…]"; exit 1; }
command -v openssl >/dev/null 2>&1 || pkg install -y openssl
for d in "$@"; do
  d=${d#https://}; d=${d%%/*}
  end=$(echo | openssl s_client -servername "$d" -connect "$d:443" 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)
  [ -n "$end" ] || { echo "✗ $d: no certificate"; continue; }
  e=$(date -d "$end" +%s 2>/dev/null || date -D '%b %d %H:%M:%S %Y GMT' -d "$end" +%s)
  days=$(( (e - $(date +%s)) / 86400 ))
  if [ "$days" -lt 14 ]; then c=31; else c=32; fi
  printf '\033[%sm%4s days\033[0m  %s  (%s)\n' "$c" "$days" "$d" "$end"
done
