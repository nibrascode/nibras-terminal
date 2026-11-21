#!/bin/sh
# site-check: HTTP status and response time for one or more sites.
# Usage: site-check https://example.com [more urls]   (or put urls in ~/.config/nibras/sites)
[ $# -gt 0 ] || set -- $(grep -v '^#' "$HOME/.config/nibras/sites" 2>/dev/null)
[ $# -gt 0 ] || { echo "Usage: site-check <url> [url…]"; exit 1; }
bad=0
for u in "$@"; do
  case "$u" in http*) ;; *) u=https://$u ;; esac
  r=$(curl -o /dev/null -sL -m 15 -w '%{http_code} %{time_total}' "$u") || r="000 0"
  code=${r% *}; t=${r#* }
  if [ "$code" -ge 200 ] 2>/dev/null && [ "$code" -lt 400 ]; then printf '\033[32m✓\033[0m %s  %s  %ss\n' "$code" "$u" "$t"
  else printf '\033[31m✗\033[0m %s  %s\n' "$code" "$u"; bad=$((bad + 1)); fi
done
exit $bad
