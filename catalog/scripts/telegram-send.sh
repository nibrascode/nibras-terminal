#!/bin/sh
# telegram-send: send a message (or a file) to yourself with your own Telegram bot.
# Setup once: put TG_TOKEN=... and TG_CHAT=... in ~/.config/nibras/telegram (create the bot with @BotFather).
# Usage: telegram-send "text"   |   telegram-send -f file.pdf   |   echo text | telegram-send
conf=$HOME/.config/nibras/telegram; [ -r "$conf" ] && . "$conf"
[ -n "${TG_TOKEN:-}" ] && [ -n "${TG_CHAT:-}" ] || { echo "Set TG_TOKEN and TG_CHAT in $conf"; exit 1; }
api=https://api.telegram.org/bot$TG_TOKEN
if [ "${1:-}" = -f ]; then curl -fsS -F chat_id="$TG_CHAT" -F document=@"$2" "$api/sendDocument" >/dev/null && echo "✓ sent $2"; exit; fi
msg=${*:-$(cat)}
curl -fsS --data-urlencode chat_id="$TG_CHAT" --data-urlencode text="$msg" "$api/sendMessage" >/dev/null && echo "✓ sent"
