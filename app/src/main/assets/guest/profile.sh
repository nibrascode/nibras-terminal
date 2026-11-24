# Nibras Terminal shell profile (managed by the app; put your own settings in ~/.profile)
export TERM="${TERM:-xterm-256color}"
export TERMINFO="${TERMINFO:-/usr/share/terminfo}"
case ":$PATH:" in *:/usr/local/bin:*) ;; *) PATH="/usr/local/bin:$PATH" ;; esac
case ":$PATH:" in *:/root/bin:*) ;; *) PATH="/root/bin:$PATH" ;; esac
[ -d /opt/python/bin ] && case ":$PATH:" in *:/opt/python/bin:*) ;; *) PATH="$PATH:/opt/python/bin" ;; esac
[ -d /opt/node/bin ] && case ":$PATH:" in *:/opt/node/bin:*) ;; *) PATH="$PATH:/opt/node/bin" ;; esac
[ -d /opt/nibras-apk/bin ] && case ":$PATH:" in *:/opt/nibras-apk/bin:*) ;; *) PATH="$PATH:/opt/nibras-apk/bin" ;; esac
# Short Nibras commands (video, download, pdf, ask, trash…) and your plugins come LAST on PATH,
# so real system programs always win and build scripts are never affected.
case ":$PATH:" in *:/usr/local/lib/nibras/short:*) ;; *) PATH="$PATH:/usr/local/lib/nibras/short" ;; esac
case ":$PATH:" in *:/root/.nibras/plugins:*) ;; *) PATH="$PATH:/root/.nibras/plugins" ;; esac
export PATH
[ -d /opt/android-sdk ] && export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
if [ -z "${ZSH_VERSION:-}" ]; then
  if [ -n "${BASH_VERSION:-}" ] || [ "$(readlink /bin/sh 2>/dev/null)" = "/bin/busybox" ]; then
    PS1='\[\e[1;32m\]\w\[\e[0m\] \$ '
  else
    PS1='$ '
  fi
fi
# Session restore: remember the working directory of this session.
if [ -n "${NIBRAS_SESSION:-}" ]; then
  _nibras_cwd() { printf '%s\n' "$PWD" > "/tmp/.nibras-cwd-$NIBRAS_SESSION" 2>/dev/null; }
  cd() { command cd "$@" || return; _nibras_cwd; nibras-work jump-add "$PWD" >/dev/null 2>&1 || true; }
  pushd() { command pushd "$@" && _nibras_cwd; }
  popd() { command popd "$@" && _nibras_cwd; }
  _nibras_cwd
fi
# Keep history on disk after every command so suggestions and restore see it.
[ -n "${BASH_VERSION:-}" ] && PROMPT_COMMAND="history -a${PROMPT_COMMAND:+; $PROMPT_COMMAND}"
if [ -n "${BASH_VERSION:-}" ]; then export HISTFILE="${HISTFILE:-$HOME/.bash_history}"; elif [ -z "${ZSH_VERSION:-}" ]; then export HISTFILE="${HISTFILE:-$HOME/.ash_history}"; fi
alias ls='ls --color=auto' 2>/dev/null
alias ll='ls -lh'
alias la='ls -lha'
alias grep='grep --color=auto' 2>/dev/null
# Colors everywhere by default (ls, grep, diff, ip, man pages, gcc); customize: theme · prompt · color · customize
alias egrep='grep -E --color=auto' 2>/dev/null; alias fgrep='grep -F --color=auto' 2>/dev/null
diff --color=auto /dev/null /dev/null >/dev/null 2>&1 && alias diff='diff --color=auto'
ip -color=auto addr >/dev/null 2>&1 && alias ip='ip -color=auto'
export CLICOLOR=1 GCC_COLORS='error=01;31:warning=01;35:note=01;36:caret=01;32:locus=01:quote=01'
export LS_COLORS="${LS_COLORS:-di=1;34:ln=1;36:so=35:pi=33:ex=1;32:bd=1;33:cd=1;33:su=37;41:sg=30;43:tw=30;42:ow=34;42:*.tar=1;31:*.gz=1;31:*.zip=1;31:*.apk=1;31:*.jpg=35:*.png=35:*.mp4=35:*.mp3=36:*.pdf=33:*.py=32:*.sh=32}"
export LESS="${LESS:--R}" LESS_TERMCAP_mb=$(printf '\033[1;31m') LESS_TERMCAP_md=$(printf '\033[1;36m') LESS_TERMCAP_me=$(printf '\033[0m') \
  LESS_TERMCAP_so=$(printf '\033[30;43m') LESS_TERMCAP_se=$(printf '\033[0m') LESS_TERMCAP_us=$(printf '\033[1;32m') LESS_TERMCAP_ue=$(printf '\033[0m')
export MANPAGER="${MANPAGER:-less -R}" GROFF_NO_SGR=1

# "install video" / "install apk" for people; everything else still goes to the real install(1).
install() {
  case "${1:-}" in
    ""|video|audio|image|pdf|download|web|git|python|py|node|dev|apk|backup|net|ocr|share|webterm|desktop|llm|quran|prayer|games|learn|record|vault|notes|monitor|security|office)
      nibras-kit install "$@" ;;
    *) command install "$@" ;;
  esac
}
# Friendly help when a command is missing (bash / zsh).
__nibras_native() { _k=$(nibras-alias --lookup "$1" 2>/dev/null); [ -n "$_k" ] || return 1; shift; nibras-alias --run "$_k" "$@"; }
command_not_found_handle() { __nibras_native "$@" && return 0; nibras-phrase --try "$*" && return 0; nibras-kit guess "$@" 2>/dev/null && return 0; nibras-kit fix "$1"; return 127; }
command_not_found_handler() { __nibras_native "$@" && return 0; nibras-phrase --try "$*" && return 0; nibras-kit guess "$@" 2>/dev/null && return 0; nibras-kit fix "$1"; return 127; }
# Git asks the phone, not the terminal. The password stays in the phone keystore.
export GIT_ASKPASS="${GIT_ASKPASS:-/usr/local/bin/nibras-git-ask}"
# help: Nibras command list plus the command names in your language (overrides the shell builtin).
help() { nibras-help "$@"; }
# bash: after a failed command, a one-line hint that `fix` can explain it (not for Ctrl+C / not found).
if [ -n "${BASH_VERSION:-}" ]; then
  __nibras_rc() { local r=$?; case $r in 0|127|130|141|148) ;; *) printf '\033[2m[exit %s · fix = %s]\033[0m\n' "$r" "${NIBRAS_FIX_HINT:-suggestion}" ;; esac; return $r; }
  case "${PROMPT_COMMAND:-}" in *__nibras_rc*) ;; *) PROMPT_COMMAND="__nibras_rc${PROMPT_COMMAND:+;$PROMPT_COMMAND}" ;; esac
fi
# Your look: prompt preset and aliases written by the prompt / aliases commands.
[ -r "$HOME/.nibras/prompt.sh" ] && . "$HOME/.nibras/prompt.sh"
[ -r "$HOME/.nibras/aliases.sh" ] && . "$HOME/.nibras/aliases.sh"

# Short names that have to change this shell. The rest are commands (use, here, hist, …).
jump() {
  if [ $# -eq 0 ]; then nibras-work jump; return; fi
  d=$(nibras-work jump-path "$1") || return 1
  cd "$d"
}
goto() {
  d=$(nibras-work goto-path "${1:?name}") || return 1
  cd "$d"
}
dotenv() {
  if [ "${1:-}" = lock ] || [ "${1:-}" = unlock ]; then nibras-work dotenv "$1"; return; fi
  [ -f "${1:-.env}" ] || { echo "no ${1:-.env}"; return 1; }
  while IFS= read -r line; do
    case "$line" in
      [A-Za-z_]*=*) export "$line" ;;
    esac
  done <<EOF
$(nibras-work dotenv-print "${1:-.env}")
EOF
  echo "✓"
}
again() {
  line=$(nibras-work again-line) || return 1
  printf '%s\n' "$line"
  nibras-api work confirm "Run again?" >/dev/null || return 1
  eval "$line"
}
clone() {
  git clone "$@" || return
  last=
  for a in "$@"; do last=$a; done
  [ -d "$last" ] && cd "$last" && return
  base=$(basename "$last" .git)
  [ -d "$base" ] && cd "$base"
}
palette() {
  c=$(nibras-work palette-pick) || return 1
  "$c"
}
incognito() {
  export NIBRAS_INCOGNITO=1 HISTFILE=/dev/null
  if [ -n "${NIBRAS_SESSION:-}" ]; then
    if [ "${NIBRAS_ENV:-}" = android ]; then : > "$HOME/.nibras-incognito-$NIBRAS_SESSION"
    else : > "/tmp/.nibras-incognito-$NIBRAS_SESSION"; fi
  fi
  echo "this session is not saved and not written to history"
}
_nibras_note() {
  [ -n "${NIBRAS_INCOGNITO:-}" ] && return 0
  [ -n "${NIBRAS_SESSION:-}" ] && [ -f "/tmp/.nibras-incognito-$NIBRAS_SESSION" ] && return 0
  line=
  if [ -n "${BASH_VERSION:-}" ]; then line=$(history 1 2>/dev/null | sed 's/^ *[0-9][0-9]* *//'); fi
  if [ -f "$HOME/.nibras/title-auto" ]; then printf '\033]0;%s\007' "$(basename "$PWD")"; fi
  [ -n "$line" ] || return 0
  mkdir -p "$HOME/.nibras"
  case "$line" in
    *password*|*passwd*|*secret*|*token=*|*ghp_*|*BEGIN\ OPENSSH*|*BEGIN\ RSA*)
      printf '%s\n' "[hidden]" > "$HOME/.nibras/last-cmd"; return 0 ;;
  esac
  printf '%s\n' "$line" >> "$HOME/.nibras/history"
  printf '%s\n' "$line" > "$HOME/.nibras/last-cmd"
  tail -400 "$HOME/.nibras/history" > "$HOME/.nibras/history.tmp" 2>/dev/null && mv "$HOME/.nibras/history.tmp" "$HOME/.nibras/history"
  return 0
}
if [ -n "${BASH_VERSION:-}" ]; then
  case "${PROMPT_COMMAND:-}" in *__nibras_note*) ;; *) PROMPT_COMMAND="_nibras_note${PROMPT_COMMAND:+;$PROMPT_COMMAND}" ;; esac
fi

