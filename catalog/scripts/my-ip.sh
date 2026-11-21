#!/bin/sh
# my-ip: your local (Wi-Fi) address and your public internet address with country.
printf 'Local:  '; ip -4 addr 2>/dev/null | awk '/inet / && $2 !~ /^127/ {sub("/.*","",$2); print $2; exit}' || hostname -i
printf 'Public: '; curl -fsS -m 10 https://ipinfo.io/json 2>/dev/null | sed -n 's/.*"ip": *"\([^"]*\)".*/\1/p;s/.*"country": *"\([^"]*\)".*/  country \1/p;s/.*"org": *"\([^"]*\)".*/  \1/p' | tr '\n' ' '; echo
