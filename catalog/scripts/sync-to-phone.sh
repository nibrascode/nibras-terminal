#!/bin/sh
# sync-to-phone: copy a folder to phone storage (/sdcard/NibrasSync/<name>), only changed files.
# Usage: sync-to-phone <folder>     (needs storage access: run setup-storage once)
set -e
[ -d "${1:-}" ] || { echo "Usage: sync-to-phone <folder>"; exit 1; }
[ -d /sdcard ] || { echo "Phone storage is not visible. Run: setup-storage"; exit 1; }
command -v rsync >/dev/null 2>&1 || pkg install -y rsync
dst=/sdcard/NibrasSync/$(basename "$(cd "$1" && pwd)"); mkdir -p "$dst"
rsync -rt --info=progress2 "$1"/ "$dst"/
echo "✓ $dst"
