#!/bin/sh
# dhikr: a simple tasbih counter. Enter = +1, q = quit. Remembers today's count.
f=$HOME/.config/nibras/dhikr-$(date +%F); mkdir -p "${f%/*}"; n=$(cat "$f" 2>/dev/null || echo 0)
echo "Enter = +1 · q = quit"
while :; do
  printf '\r\033[K\033[1;32m%s\033[0m  (%s)' "$n" "$(( n % 33 == 0 && n > 0 ? 33 : n % 33 ))/33"
  read -r k || break; [ "$k" = q ] && break
  n=$((n + 1)); echo "$n" > "$f"
done; echo
