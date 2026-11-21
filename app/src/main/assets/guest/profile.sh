# Nibras Terminal shell profile (managed by the app; put your own settings in ~/.profile)
export TERM="${TERM:-xterm-256color}"
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
  cd() { command cd "$@" && _nibras_cwd; }
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

# "install video" / "install apk" for people; everything else still goes to the real install(1).
install() {
  case "${1:-}" in
    ""|video|audio|image|pdf|download|web|git|python|py|node|dev|apk|backup|net|ocr|share|webterm|desktop|llm|quran|prayer|games|learn|record|vault|notes|monitor|security|office)
      nibras-kit install "$@" ;;
    *) command install "$@" ;;
  esac
}
# Friendly help when a command is missing (bash / zsh).
command_not_found_handle() { nibras-kit fix "$1"; return 127; }
command_not_found_handler() { nibras-kit fix "$1"; return 127; }
