#!/bin/sh
# todo: a tiny to-do list in ~/.todo.  Usage: todo · todo add <text> · todo done <n> · todo clear
f=$HOME/.todo; touch "$f"
case "${1:-}" in
  add) shift; echo "$*" >> "$f" ;;
  done) sed -i "${2:?number}d" "$f" ;;
  clear) : > "$f" ;;
esac
nl -w2 -s'. ' "$f"
