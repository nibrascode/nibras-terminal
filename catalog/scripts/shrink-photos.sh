#!/bin/sh
# shrink-photos: make smaller copies of all photos in a folder (longest side 1600 px) in <folder>/small.
# Usage: shrink-photos <folder> [size]
[ -d "${1:-}" ] || { echo "Usage: shrink-photos <folder> [size]"; exit 1; }
command -v ffmpeg >/dev/null 2>&1 || pkg install -y ffmpeg
s=${2:-1600}; mkdir -p "$1/small"; n=0
for f in "$1"/*.jpg "$1"/*.jpeg "$1"/*.JPG "$1"/*.png; do
  [ -f "$f" ] || continue
  ffmpeg -loglevel error -y -i "$f" -vf "scale='if(gt(iw,ih),min($s,iw),-2)':'if(gt(iw,ih),-2,min($s,ih))'" -q:v 3 "$1/small/${f##*/}" && n=$((n + 1))
done
echo "✓ $n photos → $1/small"
