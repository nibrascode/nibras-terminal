<p align="center">
  <img src="documentation/nibras-banner.png" alt="Nibras Terminal" width="100%">
</p>

<p align="center">
  <a href="https://nibrascode.github.io/nibras-terminal/"><b>Terminalı aç</b></a>
  &nbsp;·&nbsp; Android 8+ &nbsp;·&nbsp; arm64
</p>

Nibras Terminal telefon üçün Linux terminalıdır. Vebdə brauzerdən açılır, telefonda isə öz APK-sı kimi quraşır. İkisi eyni addır, amma eyni mühərrik deyil: veb əmri telefon shell-ində, telefon əmri vebdə işlək sayılmır.

Üç yol, eyni iş:

| Yol | Nə edir |
| --- | --- |
| Əmr | `nibras install alpine`, `python3 main.py`, `git status` |
| Düymə | Eyni əmri seçimdən işlədir. Düymə ayrıca sehr deyil |
| Xana | Token, şifrə və layihə adı əmr sətrinə yazılmır |

Tam istifadə qaydası, süni zəka üçün də: [`AGENTS.md`](AGENTS.md)

## Veb terminal

Ünvan: [nibrascode.github.io/nibras-terminal](https://nibrascode.github.io/nibras-terminal/)

Əvvəl bir mühit seçilir. Eyni anda yalnız biri saxlanır.

```
nibras install alpine
nibras install debian
nibras install ubuntu
```

Debian və Ubuntu böyükdür, təsdiq xanası açılır. Sonra alət seçilir: `python`, `node`, `php`, `git`, `build`. Seçilməyən alət işləmir. `python3`, `node`, `php`, `git` və `gcc` yalnız öz paketi seçildikdən sonra açılır.

Shell adi Linux əmrlərini qəbul edir: `ls`, `cd`, `cat`, `mkdir`, `rm`, `cp`, `mv`, `grep`, `find`, `tar`, `zip`, `curl`, `ssh`, `make`, `sqlite3`, `systemctl`. Boru, yönləndirmə, `&&`, `$()` və `set -e` işləyir. Tam siyahı terminalda `help`dir. Siyahıda olmayan əmr mövcud sayılmır.

Dörd dil var: Azərbaycan, Türk, İngilis, Rus. Dil düyməsinə toxunanda yalnız seçilən dil yüklənir. Menyular və tətbiqin öz yazısı həmin dilə keçir. Linux proqramının öz çıxışı tərcümə olunmur.

## Telefon tətbiqi

Paket `com.nibras.terminal`. Kotlin və Jetpack Compose. Minimum Android 8, API 26, yalnız arm64-v8a.

Tətbiq açılan kimi qara terminal gəlir. Ev səhifəsi birinci ekran deyil. İstək sətri `~ $`.

Alpine 3.24.1 APK-nın içindədir və arxa planda açılır. Hazır olanda əmrlər PRoot ilə Alpine-də işləyir. Alpine hələ açılmayıbsa əmr itmir: telefonun öz shell-i cavab verir. Qovluq və mühit dəyişənləri növbəti əmrə qalır. `/root/storage` telefon yaddaşına bağlıdır.

`clear` ekranı təmizləyir. `Dur` uzun əmri kəsir. Aşağıda `ESC`, `CTRL`, `ALT`, `TAB`, oxlar və `PASTE` var. Çıxışı barmaqla seçib köçürmək olur. `CTRL` yanırsa növbəti hərf idarə simvoluna çevrilir.

Telefon əmrləri Linux gözləmir. `termux-` yazılsa, eyni əmr `nibras-` kimi qəbul olunur: batareya, lövhə, bildiriş, titrəmə, səs, fənər, işıq, səs səviyyəsi, kamera, mikrofon, yer, sensor, barmaq izi, kontakt, zəng və SMS. Kamera, mikrofon, yer, kontakt və SMS icazə istəyir. İcazə yoxdursa nəticə uydurulmur.

## Nə içindədir

| Seçim | Yer |
| --- | --- |
| Alpine 3.24.1 | APK-nın içində, ayrıca yüklənmir |
| Debian, Ubuntu | 5 MB-dan böyükdür, yükləmə siyahısındadır |
| Python, Node | 5 MB-dan böyükdür, yükləmə siyahısındadır |
| Git, C, PHP, skript və şablon | APK-nın içində |
| PRoot | APK-da `libproot.so` |
| Komut kitabçası | APK-nın içində |

Siyahı [`catalog/manifest.json`](catalog/manifest.json) içindədir. Tətbiq əvvəl cihazdakı nüsxəni oxuyur, şəbəkə varsa GitHub-dakı nüsxə ilə yeniləyir. Checksum uyğun gəlməyən fayl silinir, yarımçıq paket saxlanılmır.

## APK və AAB

Paket veb terminaldan qurulur, telefondan yox.

```
nibras apk build
nibras apk status
nibras apk download
nibras aab build
nibras aab status
nibras aab download
nibras sign
nibras sign status
nibras sign download
```

SDK `35`, `34` və ya `33` seçilir: `nibras apk build 34`, `nibras sign 33`. Java 17-dir. `status` `versionCode` göstərir. `download` zip vermir. Telefon `nibras-terminal-<version>.apk` alır və açanda quraşdırma çıxır. `nibras sign download` həm imzalı APK, həm AAB verir.

İlk `nibras sign` release açarını bir dəfə yaradır və saxlayır. Sonrakı qurmalar eyni açarla imzalanır, ona görə Play yeniləməni qəbul edir. Şifrə terminalda görünmür və endirilən faylın içinə qoyulmur. `versionCode` hər qurmada artır, yeni paket köhnənin üstünə quraşır.

Debug APK və debug AAB Android-in debug açarı ilə imzalanır. Qurucu imzanı yoxlamadan faylı vermir.

## Nəyi yazmaq olmaz

Token, keystore və imza şifrəsi çıxışa, commit-ə və sənədə yazılmır. Açar hər qurmada yenidən yaradılmır. Alpine ayrıca yükləmə deyil. Debian, Ubuntu, Python və Node APK-nın içində deyil. Vebdəki `nibras apk` telefon shell-inin əmri deyil. Telefonun `nibras-battery-status` əmri veb terminalın əmri deyil.
