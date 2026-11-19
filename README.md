# Nibras Terminal

Üç yol, eyni əməliyyat:

- Terminal: `nibras install alpine`, `python3 main.py`, `git commit`
- Seçimlər: eyni addımlar düymə ilə
- Xana: token, ad, layihə adı və böyük yükləmə təsdiqi əmr sətrinə yazılmır

Eyni anda bir mühit saxlanılır. Debian və ya Ubuntu seçiləndə həcm və qalan yer göstərilir.
Redaktorda saxlanan fayl terminaldakı `cat` ilə görünür.

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
| Debian stable-slim arm64 | release | rəsmi imicdən ixrac, SHA-256 yoxlanıb |
| Ubuntu arm64 kök sistemi | release | güzgü SHA-256, mənbə AnLinux |
| Python 3.12.16 aarch64 | release | python-build-standalone SHA256SUMS |
| Node.js 26.13.1 linux-arm64 | release | nodejs.org SHASUMS256.txt |
| Git, C/C++, PHP, yoxlama skriptləri | `catalog/payloads/scripts` | repo SHA-256 |
| Python, Node, HTML şablonları | `catalog/payloads/packs` | repo SHA-256 |
| Komut kitabçası | `catalog/payloads/docs` | repo SHA-256 |
| PRoot mühərriki | APK `libproot.so` və release `proot-aarch64` | eyni SHA-256, GPL-3.0 |

Siyahı: [`catalog/manifest.json`](catalog/manifest.json). Tətbiq əvvəl bu faylın
cihazdakı nüsxəsini oxuyur, şəbəkə varsa GitHub-dakı nüsxə ilə yeniləyir.

## Sessiya

PRoot (~788 KB, statik aarch64) APK-nın içindədir. Telefon, yüklənmiş faylı
öz data qovluğundan işlətməyə icazə vermir, ona görə mühərrik `libproot.so`
kimi yığılır. Alpine, Debian və Ubuntu arxivləri APK-da deyil.

Sessiya ekranı açılmış mühitdə bir əmr işlədir (`echo salam`, `uname -a`, `ls /`
və ya öz əmrin). Bu, açıq qalan interaktiv shell deyil. Əmr bu mühitdə telefonda
yoxlanmayıb.

## Nə telefon APK-sında hələ yoxdur

Tam interaktiv terminal, paket meneceri və Ubuntu-nun rəsmi ixracı yoxdur.
Ubuntu hələ AnLinux güzgüsüdür. Rəsmi `ubuntu:24.04` iş axını cəmi yoxlanmadan
kataloqu dəyişmir.

## İş axını

Debug APK Actions-da yığılır. Köhnə `tools` SDK paketi artıq verilmir, ona görə iş axını
`android-actions/setup-android@v4` ilə `platform-tools`, `platforms;android-35` və
`build-tools;35.0.0` quraşdırır.

Official rootfs iş axını rəsmi `debian:stable-slim` və `ubuntu:24.04` arm64
imicindən kök sistemi çıxarır. Kataloqda Debian ixracı dayanır:
`debian-stable-slim-arm64.tar.xz`. Ubuntu kataloqu hələ köhnə güzgüdür.

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
