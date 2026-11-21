# Nibras Terminal shell profile (managed by the app; put your own settings in ~/.profile)
export TERM="${TERM:-xterm-256color}"
case ":$PATH:" in *:/usr/local/bin:*) ;; *) PATH="/usr/local/bin:$PATH" ;; esac
case ":$PATH:" in *:/root/bin:*) ;; *) PATH="/root/bin:$PATH" ;; esac
[ -d /opt/python/bin ] && case ":$PATH:" in *:/opt/python/bin:*) ;; *) PATH="$PATH:/opt/python/bin" ;; esac
[ -d /opt/node/bin ] && case ":$PATH:" in *:/opt/node/bin:*) ;; *) PATH="$PATH:/opt/node/bin" ;; esac
export PATH
if [ -z "${ZSH_VERSION:-}" ]; then
  if [ -n "${BASH_VERSION:-}" ] || [ "$(readlink /bin/sh 2>/dev/null)" = "/bin/busybox" ]; then
    PS1='\[\e[1;32m\]\w\[\e[0m\] \$ '
  else
    PS1='$ '
  fi
fi
alias ls='ls --color=auto' 2>/dev/null
alias ll='ls -lh'
alias la='ls -lha'
alias grep='grep --color=auto' 2>/dev/null
