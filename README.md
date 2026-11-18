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
| Debian arm64 kök sistemi | release | güzgü SHA-256, mənbə AnLinux |
| Ubuntu arm64 kök sistemi | release | güzgü SHA-256, mənbə AnLinux |
| Python 3.12.16 aarch64 | release | python-build-standalone SHA256SUMS |
| Node.js 26.13.1 linux-arm64 | release | nodejs.org SHASUMS256.txt |
| Git, C/C++, PHP, yoxlama skriptləri | `catalog/payloads/scripts` | repo SHA-256 |
| Python, Node, HTML şablonları | `catalog/payloads/packs` | repo SHA-256 |
| Komut kitabçası | `catalog/payloads/docs` | repo SHA-256 |

Siyahı: [`catalog/manifest.json`](catalog/manifest.json). Tətbiq əvvəl bu faylın
cihazdakı nüsxəsini oxuyur, şəbəkə varsa GitHub-dakı nüsxə ilə yeniləyir.

## Nə telefon APK-sında hələ yoxdur

PRoot binary APK-ya qoyulmayıb. APK arxivi yükləyir və SHA-256 uyğun gəlmirsə faylı silir.
Native Linux prosesi açılmır. Bax: `linux-engine/README.md`.

## İş axını

Debug APK Actions-da yığılır. Köhnə `tools` SDK paketi artıq verilmir, ona görə iş axını
`android-actions/setup-android@v4` ilə `platform-tools`, `platforms;android-35` və
`build-tools;35.0.0` quraşdırır.

Official rootfs iş axını rəsmi `debian:stable-slim` arm64 imicindən kök sistemi çıxarır
və SHA-256 ilə birlikdə artefakt yükləyir. Release-dəki Debian arxivi hələ AnLinux
güzgüsüdür; yeni artefakt yoxlanmadan manifest dəyişmir.

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
