#!/bin/sh
# backup-folder: archive any folder with a date in its name.
# Usage: backup-folder <folder> [destination-folder]
set -e
[ -d "${1:-}" ] || { echo "Usage: backup-folder <folder> [destination]"; exit 1; }
src=$(cd "$1" && pwd); dst=${2:-$HOME/backups}; mkdir -p "$dst"
f=$dst/$(basename "$src")-$(date +%Y%m%d-%H%M).tar.gz
tar -czf "$f" -C "$(dirname "$src")" "$(basename "$src")"
echo "✓ $f ($(du -h "$f" | cut -f1))"
