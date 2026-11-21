#!/bin/sh
# video-to-gif: turn a short video clip into a good-looking GIF.
# Usage: video-to-gif <video> [start-seconds] [length-seconds] [width]
[ -f "${1:-}" ] || { echo "Usage: video-to-gif <video> [start] [length] [width]"; exit 1; }
command -v ffmpeg >/dev/null 2>&1 || pkg install -y ffmpeg
out=${1%.*}.gif; w=${4:-480}
ffmpeg -loglevel error -y -ss "${2:-0}" -t "${3:-5}" -i "$1" -vf "fps=12,scale=$w:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" "$out"
echo "✓ $out ($(du -h "$out" | cut -f1))"
