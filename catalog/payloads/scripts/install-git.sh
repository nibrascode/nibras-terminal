#!/bin/sh
# Nibras Terminal — Git quraşdırma skripti.
# Bunu kök sistemin İÇİNDƏ işə sal. Özündə git binary daşımır.
set -eu
if [ ! -f /etc/os-release ]; then
  echo "os-release tapılmadı. Əvvəl mühit arxivini aç." >&2
  exit 1
fi
. /etc/os-release
case "${ID:-}" in
  alpine)
    apk add --no-cache git ca-certificates
    ;;
  debian|ubuntu)
    apt-get update
    apt-get install -y git ca-certificates
    ;;
  *)
    echo "Dəstəklənməyən sistem: ${ID:-unknown}" >&2
    exit 1
    ;;
esac
git --version
