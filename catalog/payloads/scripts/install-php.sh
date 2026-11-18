#!/bin/sh
# PHP CLI. Binary tətbiqin içində deyil.
set -eu
. /etc/os-release
case "${ID:-}" in
  alpine)
    apk add --no-cache php-cli
    ;;
  debian|ubuntu)
    apt-get update
    apt-get install -y php-cli
    ;;
  *)
    echo "Dəstəklənməyən sistem: ${ID:-unknown}" >&2
    exit 1
    ;;
esac
php --version
