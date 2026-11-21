# Müəllim tətbiqlər → Nibras Terminal (yoxlama siyahısı)

✅ var · 🟡 qismən · ❌ yoxdur (səbəbi yazılıb)

## Termux (+ API, Boot, Widget, Float, Styling, Tasker)
- ✅ Tam ekran terminal, əlavə düymələr (ESC/CTRL/TAB/oxlar), səs düymələri ilə qısayollar
- ✅ Çox sessiya, sessiya adı, URL seçmə, mətni kopyala/paylaş, zəng titrəməsi
- ✅ Paket meneceri (`pkg`), telefon yaddaşı (`nibras-setup-storage`, `~/storage`)
- ✅ Styling: 12 tema, şrift, `~/.nibras/colors.properties`
- ✅ Boot: `~/.nibras/boot/` · Widget: `~/.shortcuts/` · Float: üzən pəncərə · Tasker plugin
- ✅ RUN_COMMAND intent (icazə + "digər tətbiqlərə icazə" açarı)
- ✅ API: battery, clipboard, toast, notification, vibrate, torch, brightness, volume, tts, share, open, open-url, download, location, sensor, fingerprint, camera-info, camera-photo, microphone-record, media-player, contact-list, call-log, sms-list, sms-send, telephony-call/deviceinfo/cellinfo, wifi-connectioninfo/scaninfo, wallpaper, audio-info, dialog, storage-get, speech-to-text, media-scan, infrared-transmit, job-scheduler, wake-lock
- ✅ termux-x11 əvəzinə: XFCE masaüstü tətbiqin içində (noVNC)
- ✅ `nibras-usb` (= termux-usb): `-l` siyahı, `-r` icazə, `-e <əmr>` cihazın fd-si ilə (libusb `libusb_wrap_sys_device`)
- ✅ `nibras-nfc` (= termux-nfc): `-r short|full` oxu, `-w -t mətn` / `-w -u url` yaz
- ✅ `nibras-keystore` (= termux-keystore): list, generate (RSA/EC, ölçü, barmaq izi ilə), delete, sign, verify — açar telefonun aparat keystore-undan çıxmır

## Termius · JuiceSSH · ConnectBot
- ✅ Saxlanmış hostlar, qruplar, axtarış, sürətli qoşulma, son/ən çox istifadə sıralaması
- ✅ Bir əmri bir neçə hostda işlətmək (multi-exec), yalnız tunel, bağlantı testi, `~/.ssh/config` idxal/ixrac
- ✅ SSH açarı yaratmaq (ed25519/rsa/ecdsa), açarı hosta göndərmək
- ✅ Port yönləndirmə (L/R/D), jump host, keep-alive, agent forwarding
- ✅ Mosh, Telnet, SFTP, hazır əmrlər (snippets), girişdən sonra əmr
- 🟡 Cihazlar arası sinxronizasiya: yoxdur (bulud hesabı tələb edir); hosts faylı `files/hosts.txt`
- ✅ USB serial konsol: `nibras-usb serial [cihaz] [baud]` (CDC-ACM/Arduino, CP210x/ESP32, FTDI, CH340) — root lazım deyil, menyuda 🔌 USB

## UserLAnd · Andronix · AnLinux · Linux Deploy · NetHunter
- ✅ Alpine, Debian, Ubuntu + öz rootfs-unu idxal etmək
- ✅ XFCE masaüstü (tətbiqin içində), SSH server, `snapshot` ilə mühiti saxla/qaytar
- ✅ Root olan telefonda root shell (su) · `install security` (nmap, hydra, sqlmap…)
- ✅ `install arch` (arm64 + armv7), `install fedora` (yalnız arm64: Fedora 37-dən sonra 32-bit ARM buraxmır), `install kali` (arm64 + armhf, rəsmi NetHunter rootfs) — PRoot ilə CI-da yoxlanılır

## AndroidIDE · AIDE · Acode · Pydroid · Cxxdroid
- ✅ Kod redaktoru: sətir nömrələri, avtomatik girinti, mötərizələri bağlama, simvol sırası, geri al/irəli, regex axtarış, 15+ dil rəngləmə, HTML/Markdown önizləmə, JSON format, son fayllar, Run düyməsi (Makefile/CMake/Cargo/go.mod tanıyır)
- ✅ Python, Node, C/C++, Go, Rust, Java/Kotlin (`install python|node|dev`)
- ✅ Telefonda APK yığmaq: `nibras apk new/build` (Gradle + Android SDK)

## MT Manager · APK Editor · Apktool · jadx
- ✅ Fayl meneceri (kopyala/kəs/yapışdır, ad dəyiş, paylaş, gizli fayllar, yerlər)
- ✅ `nibras apk decompile/recompile/sign/verify/align/manifest/info/java/dex`
- ✅ İki panel (A⇆B), çoxlu seçim, sıralama, axtarış (alt qovluqlar, `*?`), əlfəcinlər, zip/tar/7z, zibil qutusu + bərpa, xüsusiyyətlər, chmod, MD5/SHA

## LADB · Shizuku
- ✅ `nibras-adb pair/connect/shell` (simsiz sazlama) · `nibras-rish` (Shizuku shell)

## Yalnız Nibras-da
Vizual ffmpeg/yt-dlp qurucusu (🎬 Media ekranı, `media`), xəta mətnindən düzəliş təklifi (`fix`), `record gif|video`, `cron menu`, `share smb|webdav|ftp|sftp|dlna`, AlarmManager ilə namaz xatırlatması (tətbiq bağlı olanda da),
`ask`, `explain`, `fix`, `recipes`, `learn` (dərs + test, 4 dil), əmr təklifləri, səslə əmr,
`video`/`download` menyuları, `trash`, `snapshot`, `record`, `webterm`, `share` + QR, `qr`,
`vault`, bufer tarixçəsi, CPU/RAM monitoru, batareya qənaəti, barmaq izi ilə kilid,
Mağaza, `plugin`, `notes`, `llm`, `quran`, `hadith`, `prayer`, `games`, sessiyaların bərpası.

## Keyfiyyət müqayisəsi (hər funksiyada ən yaxşı müəllim ilə)

| Funksiya | Ən yaxşı müəllim | Onda olan | Nibras-da | Nəticə |
|---|---|---|---|---|
| Fayl meneceri | MT Manager / ZArchiver | 2 panel, çoxlu seçim, arxiv, chmod, hash | Hamısı + zibil qutusu və bərpa, mühitlər arası (Alpine/Debian/Android), "terminal burada", birbaşa redaktor/APK/video menyusu | ✅ bərabər və ya üstün |
| Kod redaktoru | Acode / AndroidIDE | sətir nömrəsi, auto-indent, simvol sırası, önizləmə, tablar, avtomatik tamamlama | Hamısı + açar sözlər/fayldakı sözlər/snippetlərlə tamamlama, 🩺 lint (shellcheck, pyflakes, eslint, gcc…); tam LSP `install lsp` ilə (helix/neovim + dil serverləri) | ✅ |
| SSH | Termius | hostlar, açarlar, tunellər, multi-exec, snippets, sinxron | Hamısı + mosh/telnet, `ssh config` idxal/ixrac, fayl ilə köçürmə | 🟡 bulud sinxronizasiyası yoxdur (hesab tələb edir); parol saxlanmır (açar tövsiyə olunur) |
| Terminal | Termux | əlavə düymələr, sessiyalar, temalar, pinch-zoom | Hamısı + ekranı bölmək, əmr təklifləri, köhnə yazılarda axtarış, sessiya bərpası, səslə əmr | ✅ üstün |
| Linux | Andronix / UserLAnd | distro seçimi, masaüstü | Alpine/Debian/Ubuntu + öz rootfs, masaüstü tətbiqin içində, snapshot | ✅ |
| APK alətləri | APK Editor / jadx | decompile, imzala, manifest | `apk` əmrləri + fayl menecerindən bir toxunuş | 🟡 vizual resurs redaktoru yoxdur (terminal + redaktor ilə) |
| Python/C++ | Pydroid / Cxxdroid | Run, pip, çıxış | Run + çıxış kodu və vaxt, layihə faylı tanıma, `py` paket menyusu | ✅ |
| ADB/Shizuku | LADB | pair/connect/shell | Eyni + `rish` | ✅ |
