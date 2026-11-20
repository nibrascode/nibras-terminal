#!/bin/sh
# C/C++ alətləri. Arxiv deyil — mühitin paket menecerini çağırır.
set -eu
. /etc/os-release
case "${ID:-}" in
  alpine)
    apk add --no-cache build-base
    ;;
  debian|ubuntu)
    apt-get update
    apt-get install -y build-essential
    ;;
  *)
    echo "Dəstəklənməyən sistem: ${ID:-unknown}" >&2
    exit 1
    ;;
esac
gcc --version
