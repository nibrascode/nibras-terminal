#!/bin/sh
# json-pretty: format JSON from a file, a URL or a pipe.
# Usage: json-pretty file.json | json-pretty https://api… | cat x | json-pretty
command -v jq >/dev/null 2>&1 || pkg install -y jq
case "${1:-}" in
  http*) curl -fsSL "$1" | jq . ;;
  "") jq . ;;
  *) jq . "$1" ;;
esac
