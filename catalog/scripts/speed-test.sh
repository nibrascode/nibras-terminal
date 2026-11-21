#!/bin/sh
# speed-test: rough download speed and latency using a public test file.
u=${1:-https://speed.cloudflare.com/__down?bytes=25000000}
printf 'Ping:     '; curl -o /dev/null -s -m 10 -w '%{time_connect}s\n' https://1.1.1.1
printf 'Download: '; curl -o /dev/null -s -m 60 -w '%{speed_download}' "$u" | awk '{printf "%.1f Mbit/s\n", $1*8/1000000}'
