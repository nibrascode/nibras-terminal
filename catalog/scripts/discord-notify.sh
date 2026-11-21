#!/bin/sh
# discord-notify: post a message to a Discord channel through a webhook.
# Setup: DISCORD_WEBHOOK=https://discord.com/api/webhooks/... in ~/.config/nibras/discord
# Usage: discord-notify "Backup finished"
conf=$HOME/.config/nibras/discord; [ -r "$conf" ] && . "$conf"
[ -n "${DISCORD_WEBHOOK:-}" ] || { echo "Set DISCORD_WEBHOOK in $conf"; exit 1; }
msg=${*:-$(cat)}
esc=$(printf '%s' "$msg" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk '{printf "%s\\n", $0}')
curl -fsS -H 'Content-Type: application/json' -d "{\"content\":\"$esc\"}" "$DISCORD_WEBHOOK" && echo "✓ sent"
