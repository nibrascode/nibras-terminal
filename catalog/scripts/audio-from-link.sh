#!/bin/sh
# audio-from-link: save the audio of a video link as MP3 into ~/storage/music (or ~/music).
# Usage: audio-from-link <url>      (only for content you are allowed to download)
[ -n "${1:-}" ] || { echo "Usage: audio-from-link <url>"; exit 1; }
command -v yt-dlp >/dev/null 2>&1 || nibras-kit install media
dst=$HOME/storage/music; [ -d "$dst" ] || dst=$HOME/music; mkdir -p "$dst"
yt-dlp -x --audio-format mp3 -o "$dst/%(title)s.%(ext)s" "$1"
