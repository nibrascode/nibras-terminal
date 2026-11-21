#!/bin/sh
# serve-here: share the current folder on your Wi-Fi as a simple web page (read-only).
# Usage: serve-here [port]
p=${1:-8000}
command -v python3 >/dev/null 2>&1 || pkg install -y python3
ip=$(ip -4 addr 2>/dev/null | awk '/inet / && $2 !~ /^127/ {sub("/.*","",$2); print $2; exit}')
echo "Open http://${ip:-localhost}:$p  (Ctrl+C to stop)"
exec python3 -m http.server "$p"
