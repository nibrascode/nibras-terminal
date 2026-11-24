# Commands that must change this Android shell. $NB is ~/bin.
jump() { if [ $# -eq 0 ]; then sh "$NB/nibras-work" jump; return; fi; d=$(sh "$NB/nibras-work" jump-path "$1") || return 1; cd "$d"; }
goto() { d=$(sh "$NB/nibras-work" goto-path "${1:?name}") || return 1; cd "$d"; }
dotenv() {
  if [ "${1:-}" = lock ] || [ "${1:-}" = unlock ]; then sh "$NB/nibras-work" dotenv "$1"; return; fi
  [ -f "${1:-.env}" ] || { echo "no ${1:-.env}"; return 1; }
  while IFS= read -r line; do
    case "$line" in [A-Za-z_]*=*) export "$line" ;; esac
  done <<EOF
$(sh "$NB/nibras-work" dotenv-print "${1:-.env}")
EOF
  echo "✓"
}
again() { line=$(sh "$NB/nibras-work" again-line) || return 1; printf '%s\n' "$line"; sh "$NB/nibras-api" work confirm "Run again?" >/dev/null || return 1; eval "$line"; }
clone() { git clone "$@" || return; last=; for a in "$@"; do last=$a; done; if [ -d "$last" ]; then cd "$last"; return; fi; base=$(basename "$last" .git); [ -d "$base" ] && cd "$base"; }
palette() { c=$(sh "$NB/nibras-work" palette-pick) || return 1; "$c"; }
incognito() { export NIBRAS_INCOGNITO=1 HISTFILE=/dev/null; if [ -n "${NIBRAS_SESSION:-}" ]; then : > "$HOME/.nibras-incognito-$NIBRAS_SESSION"; fi; echo "this session is not saved and not written to history"; }

# nibras-show is a bundled pack. The file is copied to $HOME/bin only after
# `pkg install nibras-show`. This function is always defined so the same-line
# `pkg install nibras-show -y && nibras-show` works in the Android shell, where
# exec() of files in app storage is blocked.
nibras-show() {
  if [ -f "$HOME/bin/nibras-show" ]; then
    sh "$HOME/bin/nibras-show" "$@"
  else
    echo "pkg: nibras-show is not installed yet. Run:  pkg install nibras-show -y"
    return 1
  fi
}

