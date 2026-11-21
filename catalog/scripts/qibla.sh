#!/bin/sh
# qibla: direction to the Kaaba from your location (degrees from north).
# Usage: qibla <latitude> <longitude>     (or: qibla  — uses the phone location)
if [ $# -lt 2 ]; then
  loc=$(nibras-location -p network 2>/dev/null) || { echo "Usage: qibla <lat> <lon>"; exit 1; }
  set -- $(printf '%s' "$loc" | sed -n 's/.*"latitude": *\([-0-9.]*\).*/\1/p') $(printf '%s' "$loc" | sed -n 's/.*"longitude": *\([-0-9.]*\).*/\1/p')
fi
awk -v la="$1" -v lo="$2" 'BEGIN{pi=atan2(0,-1); r=pi/180; kl=21.4225*r; kn=39.8262*r; a=la*r; b=lo*r
  q=atan2(sin(kn-b), cos(a)*sin(kl)/cos(kl)-sin(a)*cos(kn-b)); d=(q/r+360)%360
  printf "Qibla: %.1f° from north\n", d}'
