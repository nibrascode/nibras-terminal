# Nibras Terminal

Yüngül Android qabığı. İçində Linux, Python və Node **yoxdur**.

İstifadəçi nəyi seçsə, yalnız onu yükləyir. Yüklənəcək hər şey bu repoda və
[`payloads-0.1`](https://github.com/nibrascode/nibras-terminal/releases/tag/payloads-0.1)
release-indədir. APK-nın içinə heç bir kök sistem qoyulmayıb.

Paket: `com.nibras.terminal`  
Dil: Kotlin, Jetpack Compose  
Minimum: Android 8 (API 26), ABI arm64-v8a

## Nə yüklənə bilər

| Seçim | Harada | Checksum |
| --- | --- | --- |
| Alpine 3.24.1 minirootfs | release | Alpine-in rəsmi SHA-256 |
| Debian arm64 kök sistemi | release | güzgü SHA-256, mənbə AnLinux |
| Ubuntu arm64 kök sistemi | release | güzgü SHA-256, mənbə AnLinux |
| Python 3.12.16 aarch64 | release | python-build-standalone SHA256SUMS |
| Node.js 26.13.1 linux-arm64 | release | nodejs.org SHASUMS256.txt |
| Git, C/C++, PHP, yoxlama skriptləri | `catalog/payloads/scripts` | repo SHA-256 |
| Python, Node, HTML şablonları | `catalog/payloads/packs` | repo SHA-256 |
| Komut kitabçası | `catalog/payloads/docs` | repo SHA-256 |

Siyahı: [`catalog/manifest.json`](catalog/manifest.json). Tətbiq əvvəl bu faylın
cihazdakı nüsxəsini oxuyur, şəbəkə varsa GitHub-dakı nüsxə ilə yeniləyir.

## Nə bu versiyada yoxdur

PRoot mühərriki APK-ya qoyulmayıb və ayrıca binary də paylanmayıb. Arxiv
yüklənir. SHA-256 uyğun gəlmirsə fayl silinir. İstəsən arxiv açılır. Shell
sessiyası açılmır. Bax: `linux-engine/README.md`.

GitHub tokeni kodda yoxdur. Yükləmə yalnız `github.com` və
`githubusercontent.com` ünvanlarından gedir.

## Qurmaq

Android Studio-da bu qovluğu aç. Sonra:

```
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Release imzası bu repoda yoxdur. Keystore-u git-ə əlavə etmə.

## Yoxlama

```
sh scripts/verify-small-payloads.sh
```

Böyük arxivlər git tarixçəsində deyil. Onların cəmi
`catalog/SHA256SUMS.release` içindədir.
