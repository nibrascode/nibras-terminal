#!/bin/sh
# backup-home: dated .tar.gz of your Linux home in ~/backups, keeps the newest 5.
# Usage: backup-home [keep]
set -e
keep=${1:-5}; out=$HOME/backups; mkdir -p "$out"
f=$out/home-$(date +%Y%m%d-%H%M).tar.gz
tar -czf "$f" -C "$HOME" --exclude=./backups --exclude=./.cache --exclude=./storage .
echo "✓ $f ($(du -h "$f" | cut -f1))"
ls -1t "$out"/home-*.tar.gz 2>/dev/null | tail -n +$((keep + 1)) | while read -r old; do rm -f "$old"; echo "removed old: ${old##*/}"; done
