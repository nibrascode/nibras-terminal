#!/bin/sh
# ayah-of-day: one verse of the Quran per day, Arabic with a translation.
# Usage: ayah-of-day [edition]   e.g. en.sahih, az.mammadaliyev, tr.diyanet, ru.kuliev
command -v jq >/dev/null 2>&1 || pkg install -y jq
n=$(( $(date +%j | sed 's/^0*//') * 17 % 6236 + 1 )); ed=${1:-en.sahih}
curl -fsS -m 15 "https://api.alquran.cloud/v1/ayah/$n/editions/quran-uthmani,$ed" |
  jq -r '.data | "\(.[0].surah.name) \(.[0].surah.englishName) \(.[0].numberInSurah)\n\n\(.[0].text)\n\n\(.[1].text)"'
