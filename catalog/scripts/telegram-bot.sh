#!/bin/sh
# telegram-bot: a tiny command bot running on your phone. Replies to /start, /time, /uptime, /ip
# and runs ~/.config/nibras/bot/<command>.sh for your own commands. Only answers TG_CHAT.
# Setup: TG_TOKEN and TG_CHAT in ~/.config/nibras/telegram.
conf=$HOME/.config/nibras/telegram; [ -r "$conf" ] && . "$conf"
[ -n "${TG_TOKEN:-}" ] && [ -n "${TG_CHAT:-}" ] || { echo "Set TG_TOKEN and TG_CHAT in $conf"; exit 1; }
command -v jq >/dev/null 2>&1 || pkg install -y jq
api=https://api.telegram.org/bot$TG_TOKEN; off=0; mkdir -p "$HOME/.config/nibras/bot"
say() { curl -fsS --data-urlencode chat_id="$TG_CHAT" --data-urlencode text="$1" "$api/sendMessage" >/dev/null; }
echo "Bot running (Ctrl+C to stop)"
while :; do
  r=$(curl -fsS -m 60 "$api/getUpdates?timeout=50&offset=$off") || { sleep 5; continue; }
  printf '%s' "$r" | jq -r '.result[] | "\(.update_id)\t\(.message.chat.id // "")\t\(.message.text // "")"' | while IFS="$(printf '\t')" read -r id chat text; do
    echo "$((id + 1))" > "$HOME/.config/nibras/bot/.offset"
    [ "$chat" = "$TG_CHAT" ] || continue
    cmd=${text%% *}; cmd=${cmd#/}
    case "$cmd" in
      start) say "Nibras bot ✓ /time /uptime /ip" ;;
      time) say "$(date)" ;;
      uptime) say "$(uptime)" ;;
      ip) say "$(curl -fsS -m 10 https://api.ipify.org)" ;;
      *) f=$HOME/.config/nibras/bot/$(printf '%s' "$cmd" | tr -cd 'a-z0-9_-').sh; [ -f "$f" ] && say "$(sh "$f" 2>&1 | head -c 3500)" || say "?" ;;
    esac
  done
  off=$(cat "$HOME/.config/nibras/bot/.offset" 2>/dev/null || echo 0)
done
