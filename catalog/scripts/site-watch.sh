#!/bin/sh
# site-watch: tell me when a web page changes (checks every N minutes, phone notification).
# Usage: site-watch <url> [minutes]
[ -n "${1:-}" ] || { echo "Usage: site-watch <url> [minutes]"; exit 1; }
u=$1; every=${2:-10}; last=
echo "Watching $u every $every min (Ctrl+C to stop)"
while :; do
  h=$(curl -sL -m 30 "$u" | sed 's/[0-9]\{2\}:[0-9]\{2\}//g' | md5sum | cut -c1-32)
  if [ -n "$last" ] && [ "$h" != "$last" ]; then
    echo "$(date +%H:%M) changed!"; nibras-notification -t "Page changed" -c "$u" 2>/dev/null || true
  fi
  last=$h; sleep $((every * 60))
done
