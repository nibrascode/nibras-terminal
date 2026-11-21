#!/bin/sh
# git-pull-all: update every git project under a folder (default ~/projects).
d=${1:-$HOME/projects}
for g in "$d"/*/.git; do
  [ -d "$g" ] || continue; p=${g%/.git}
  printf '\033[1m%s\033[0m: ' "${p##*/}"; git -C "$p" pull --ff-only -q 2>&1 && echo "✓" || true
done
