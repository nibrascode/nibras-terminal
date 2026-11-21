<p align="center">
  <img src="documentation/nibras-banner.png" alt="Nibras Terminal" width="100%">
</p>

<p align="center">
  <a href="https://nibrascode.github.io/nibras-terminal/"><b>Terminalı aç</b></a>
  &nbsp;·&nbsp; Android 8+ &nbsp;·&nbsp; arm64
</p>

Üç yol, eyni iş:

| Yol | Nə edir |
| --- | --- |
| Əmr | `nibras install alpine`, `python3 main.py`, `git status` |
| Düymə | Eyni əmri seçimdən işlədir |
| Xana | Token və şifrə əmr sətrinə yazılmır |

Tam istifadə qaydası, süni zəka üçün də: [`AGENTS.md`](AGENTS.md)

## Nə içindədir

| Seçim | Yer |
| --- | --- |
| Alpine 3.24.1 | APK-nın içində |
| Debian, Ubuntu, Python, Node | ayrıca yüklənir, 5 MB-dan böyükdür |
| PRoot | APK-da `libproot.so` |
| Skript, şablon, komut kitabçası | APK-nın içində |

Siyahı: [`catalog/manifest.json`](catalog/manifest.json)

Paket: `com.nibras.terminal` · Kotlin, Jetpack Compose · API 26 · arm64-v8a

APK vebdən qurulur: `nibras apk build`, sonra `nibras apk status`, sonra `nibras apk download`.
Release: `nibras sign`. Açar bir dəfə saxlanır, şifrə göstərilmir, `versionCode` hər qurmada artır.
