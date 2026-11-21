#!/bin/sh
# hijri-today: today's Hijri date (from aladhan.com).
command -v jq >/dev/null 2>&1 || pkg install -y jq
curl -fsS -m 15 "https://api.aladhan.com/v1/gToH/$(date +%d-%m-%Y)" | jq -r '.data.hijri | "\(.day) \(.month.en) \(.year) AH  ·  \(.month.ar)  ·  \(.weekday.ar)"'
