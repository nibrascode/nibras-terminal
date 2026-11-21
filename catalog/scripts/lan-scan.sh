#!/bin/sh
# lan-scan: find devices on your Wi-Fi network (ping sweep of your /24).
me=$(ip -4 addr 2>/dev/null | awk '/inet / && $2 !~ /^127/ {sub("/.*","",$2); print $2; exit}')
[ -n "$me" ] || { echo "No Wi-Fi address found"; exit 1; }
net=${me%.*}; echo "Scanning $net.0/24 …"
for i in $(seq 1 254); do (ping -c1 -W1 "$net.$i" >/dev/null 2>&1 && echo "  ● $net.$i") & done; wait
