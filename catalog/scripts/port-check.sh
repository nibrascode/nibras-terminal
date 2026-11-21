#!/bin/sh
# port-check: which common ports are open on a host (yours or one you own).
# Usage: port-check <host> [port …]
[ -n "${1:-}" ] || { echo "Usage: port-check <host> [ports]"; exit 1; }
h=$1; shift; [ $# -gt 0 ] || set -- 21 22 25 53 80 110 143 443 445 3306 5432 6379 8080 8443
for p in "$@"; do
  if nc -z -w 2 "$h" "$p" 2>/dev/null; then printf '\033[32mopen\033[0m    %s\n' "$p"; else printf 'closed  %s\n' "$p"; fi
done
