# Nibras Terminal — istifadə qaydası

Bu fayl mənbədir. Süni zəka əmr uydurmasın. Burada olmayan əmri işlək kimi yazmasın. Şübhə olanda terminalda `help` yazılsın.

## Harada işləyir

- Veb terminal: https://nibrascode.github.io/nibras-terminal/
- Telefon paketi: `com.nibras.terminal`. Android 8 (API 26), yalnız arm64-v8a. Tətbiq açılan kimi terminal ekranı gəlir, Ev səhifəsi yox.

Veb terminal ilə telefon APK-sı eyni ad daşıyır, amma eyni mühərrik deyil. Veb əmrini telefon shell-ində, telefon əmrini vebdə işlək sayma.

## Üç yol

1. Əmri yaz və Enter bas.
2. Seçim düyməsi eyni əmri göndərir. Düymə ayrıca sehr deyil.
3. Token, şifrə, layihə adı və böyük yükləmə təsdiqi xanada soruşulur. Onları terminal çıxışına, commit-ə, loga və bu fayla yazma.

## Dil

Vebdə dörd dil var: Azərbaycan `az`, Türk `tr`, İngilis `en`, Rus `ru`. Dil düyməsinə toxunanda yalnız seçilən dil yüklənir. Düymə, menyu və tətbiqin öz bildirişi seçilmiş dilə keçir. `ls`, `apt` və digər proqramların öz çıxışı tərcümə olunmur.

## Veb terminal

Əvvəl mühit:

```
nibras install alpine
nibras install debian
nibras install ubuntu
```

Eyni anda bir mühit saxlanır. Debian və Ubuntu böyükdür, təsdiq xanası açılır. `--yes` təsdiqi keçir.

Alətlər, mühitdən sonra:

```
nibras install python
nibras install node
nibras install php
nibras install git
nibras install build
```

Sonra `python3`, `node`, `php`, `git`, `gcc` həmin seçimdən asılıdır.

Paket qurmaq, veb terminaldan, GitHub Actions ilə:

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

SDK `35`, `34` və ya `33` ola bilər: `nibras apk build 34`, `nibras sign 33`. `status` `versionCode` göstərir. `download` zip vermir: APK `nibras-terminal-<version>.apk`, AAB `nibras-terminal-<version>.aab`. Release imzası həm APK, həm AAB verir. İlk `nibras sign` açarı bir dəfə yaradır və saxlayır. Sonrakı imzalar eyni açardandır. Şifrə heç yerdə göstərilmir. `versionCode` hər qurmada artır, ona görə yeni paket köhnənin üstünə quraşır.

Digər `nibras` əmrləri: `token`, `layihe`, `serve`, `md`, `stop`, `disk`, `zibil`, `qaytar`. Siyahıdan kənar fel yoxdur.

`pkg` və `apt` eyni siyahıdan işləyir:

```
pkg update
pkg install <ad>
pkg uninstall <ad>
pkg search <ad>
pkg list-all
pkg list-installed
pkg show <ad>
pkg files <ad>
pkg clean
```

Shell əmrləri, vebdə: `help`, `man`, `clear`, `pwd`, `ls`, `cd`, `pushd`, `popd`, `dirs`, `cat`, `mkdir`, `touch`, `rm`, `cp`, `mv`, `echo`, `printf`, `grep`, `head`, `tail`, `wc`, `sort`, `uniq`, `cut`, `sed`, `awk`, `tr`, `find`, `diff`, `patch`, `tar`, `zip`, `unzip`, `chmod`, `ln`, `export`, `history`, `alias`, `curl`, `wget`, `ping`, `ssh`, `sftp`, `git`, `python3`, `node`, `php`, `gcc`, `make`, `crontab`, `cron`, `rsync`, `sqlite3`, `systemctl`, `logger`, `journalctl`, `nano`, `vi`, `edit`. Tam siyahı terminalda `help`dir. Bu siyahıda olmayan əmri mövcud sayma.

Boru, yönləndirmə, `&&`, `||`, `;`, `$()`, dırnaq və `set -e` / `set -x` veb shell-də işləyir.

## Telefon APK-sı

Açılış birbaşa qara terminaldır. İstək sətri `~ $`.

- Alpine APK-nın içindədir və arxa planda açılır. Hazır olanda Linux əmrləri PRoot ilə Alpine-də işləyir.
- Alpine hələ açılmayıbsa əmr itmir: telefonun `/system/bin/sh` cavab verir.
- Debian və Ubuntu APK-da deyil. Yükləmə siyahısındadır, 5 MB-dan böyükdür.
- PRoot `libproot.so` kimi APK-nın içindədir.
- Qovluq və mühit dəyişənləri növbəti əmrə saxlanır. `/root/storage` telefon yaddaşına bağlıdır.
- `clear` ekranı təmizləyir. `Dur` uzun əmri kəsir.
- Aşağı düymələr: `ESC`, `CTRL`, `ALT`, `TAB`, `←`, `→`, `↑`, `PASTE`. Çıxışı seçib köçürmək olur.
- `CTRL` yanırsa növbəti hərf idarə simvoluna çevrilir.

Telefon əmrləri Linux gözləmir. `termux-` yazılsa, eyni əmr `nibras-` kimi işləyir.

```
nibras-info
nibras-battery-status
nibras-clipboard-get
nibras-clipboard-set
nibras-toast
nibras-vibrate
nibras-tts-speak
nibras-notification
nibras-notification-list
nibras-notification-remove
nibras-wake-lock
nibras-wake-unlock
nibras-share
nibras-wifi-connectioninfo
nibras-torch
nibras-brightness
nibras-volume
nibras-camera-info
nibras-microphone-record
nibras-location
nibras-sensor
nibras-fingerprint
nibras-download
nibras-setup-storage
nibras-open-url
nibras-media-player
nibras-contact-list
nibras-call-log
nibras-sms-list
nibras-sms-send
```

Kamera, mikrofon, yer, kontakt, zəng, SMS və bildiriş icazə istəyir. İcazə verilməyibsə əmr icazə gözləyir, uydurma nəticə qaytarılmır.

## Nəyi etmə

- Tokeni, keystore-u və imza şifrəsini çap etmə, repoya və sənədə yazma.
- Açarı hər qurmada yenidən yaratma. `nibras sign` mövcud açarı saxlayır.
- Alpine-i ayrıca yükləmə kimi göstərmə. O, APK-nın içindədir.
- Debian, Ubuntu, Python və Node-u APK-nın içində kimi göstərmə. Onlar 5 MB-dan böyükdür.
- Veb `nibras apk` əmrini telefon shell-inin əmri kimi göstərmə.
- Telefon `nibras-battery-status` əmrini veb terminalın əmri kimi göstərmə.
- Burada olmayan əmr, düymə və ya ekran vəd etmə.
