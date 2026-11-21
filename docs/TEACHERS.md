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

## Termux ilə sətir-sətir uyğunluq (v0.2.120)

✅ var idi · ➕ indi əlavə olundu · ⛔ mümkün deyil / fərqli yolla (səbəbi). Bütün əmrlər `nibras-*` adı ilə; `termux-*` gizli uyğunluq adlarıdır.

### termux-tools
| Termux | Nibras | Status |
|---|---|---|
| termux-setup-storage, `~/storage` | `nibras-setup-storage` | ✅ |
| termux-open (`--send --view --chooser --content-type`) | `nibras-open` | ➕ bütün bayraqlar, qovluq → Fayllar |
| termux-open-url | `nibras-open-url` | ✅ |
| termux-reload-settings | `nibras-reload-settings` | ✅ |
| termux-change-repo | `nibras-change-repo` (apk/apt/pacman/dnf güzgü menyusu + ən sürətlini avtomatik seçir) | ➕ |
| termux-wake-lock / unlock | `nibras-wake-lock` | ✅ |
| termux-fix-shebang | `nibras-fix-shebang` | ➕ |
| termux-backup / restore | `nibras-backup` (kit) + `termux-backup/restore` | ➕ |
| termux-reset | `nibras-reset` → Mühitlər ekranı | ➕ |
| termux-info | `nibras-info` (API, OS, nüvə, güzgülər, disk, Android) | ➕ |
| termux-am / am | `nibras-am` (tətbiqin içində: start/broadcast/startservice, bütün `-e*` növləri, bayraqlar, intent: URI) | ➕ |
| pm, getprop, logcat, cmd, settings, dumpsys, wm, input, svc | Linux mühitində Android sarğıları | ➕ |
| login / motd | `nibras-login`, `/etc/motd` (4 dil) | ✅ |
| chsh | `nibras-shell` (+ `termux-chsh`) | ➕ ad |
| termux-setup-package-manager | `nibras-setup-package-manager` | ➕ |
| termux-x11 | `nibras-x11` → XFCE masaüstü (VNC, tətbiqin içində) | ⛔ X11 APK əvəzinə daxili masaüstü |

### termux.properties (`~/.termux/termux.properties` və ya `~/.nibras/nibras.properties`)
| Açar | Status |
|---|---|
| extra-keys (JSON, popup, macro, display), extra-keys-hidden | ➕ |
| bell-character (vibrate/beep/ignore) | ➕ |
| back-key (escape/back), volume-keys | ➕ |
| fullscreen | ➕ |
| terminal-cursor-style, terminal-cursor-blink-rate | ➕ |
| terminal-margin-horizontal/vertical | ➕ |
| terminal-transcript-rows | ➕ |
| hide-soft-keyboard-on-startup | ➕ |
| terminal-onclick-url-open | ➕ |
| disable-hardware-keyboard-shortcuts, shortcut.create-session/next/previous/rename | ➕ |
| default-working-directory | ➕ |
| allow-external-apps | ➕ (həm də tətbiq ayarı) |
| font.ttf, colors.properties | ✅ (`~/.nibras/font.ttf`, `~/.termux/colors.properties`) |

### Sessiya, intent, fayl
| Termux | Status |
|---|---|
| Çox sessiya, ad dəyişmə, sürüşmə menyusu | ✅ (+ ekranı bölmək, bərpa) |
| RUN_COMMAND (PATH, ARGUMENTS, WORKDIR, STDIN, BACKGROUND, PENDING_INTENT) — `com.termux.*` və `com.nibras.terminal.*` əlavələri | ➕ |
| Qısayollar / Widget (`~/.shortcuts`, `~/.shortcuts/tasks`) | ✅ |
| Boot (`~/.nibras/boot`, `~/.termux/boot`) | ✅ |
| Float | ✅ |
| Tasker plugin | ✅ |
| Styling (tema + şrift) | ✅ |
| "Terminala paylaş" (share-to-terminal), fayl provayderi, SAF Documents | ✅ |

### Termux:API (hər əmr eyni bayraqlar + eyni JSON)
➕ yenidən yazıldı (TermuxApi.kt): battery-status, brightness, call-log, camera-info, camera-photo (səssiz Camera2), clipboard-get/set, contact-list, dialog (confirm/checkbox/counter/date/radio/sheet/spinner/speech/text/time), download, fingerprint, infrared-frequencies/transmit, job-scheduler (Termux bayraqları), location (`-p -r updates`), media-player, media-scan, microphone-record, notification (düymələr, action, media növü, kanallar), notification-channel, notification-list, notification-remove, open, open-url, sensor (axın), share, sms-inbox/list/send, speech-to-text, storage-get, telephony-call/cellinfo/deviceinfo, toast, torch, tts-engines, tts-speak, usb, vibrate, volume, wallpaper, wifi-connectioninfo/scaninfo/enable, keystore, nfc, saf-* (managedir, dirs, ls, stat, read, write, mkdir, create, rm), api-start/stop.
Həmçinin Android shell-də işləyir (`~/.nibras/android-rc`).

| Mümkün olmayan / şərtli | Səbəb |
|---|---|
| wifi-enable Android 10+ | Android yalnız istifadəçiyə icazə verir → Wi-Fi paneli açılır (root/adb ilə `svc wifi`) |
| notification-list (bütün tətbiqlər) | "Bildirişlərə giriş" icazəsi istifadəçi tərəfindən verilməlidir |
| brightness | "Sistem ayarlarını dəyişmək" icazəsi |
| sms/zəng/kontakt | İş vaxtı icazələri (ilk istifadədə soruşulur) |

### Paketlər (hamısı `install <paket>` ilə, APK kiçik qalır)
| Termux | Nibras | Status |
|---|---|---|
| pkg | `pkg` (apk/apt/pacman/dnf üzərində) | ✅ |
| python, node, clang, go, rust | `install python|node|dev|go|rust` | ✅ |
| java (openjdk-17/21) | `install jdk [17|21]` | ➕ |
| php, ruby, perl, lua | `install php|ruby|perl|lua` | ➕ |
| git, ssh, curl, aria2, tmux | ✅ / `install tmux` ➕ | ✅ |
| ffmpeg, yt-dlp, imagemagick | `media`, `video`, `image` | ✅ |
| nmap + qanuni pentest alətləri | `install security`; metasploit `install metasploit` (yalnız Kali) | ✅ / ➕ |
| vim/nano/micro/helix/neovim/emacs | `install editors` | ➕ |
| zsh + oh-my-zsh, fish | `install zsh` (autosuggestions + syntax-highlighting), `install fish` | ➕ |
| proot-distro | `proot-distro list/install/login/remove/backup/restore` | ➕ |
| x11-repo, tur-repo, root-repo | Lazım deyil: tam Debian/Ubuntu/Arch/Fedora/Kali depoları | ⛔ (daha geniş) |
| code-server | `install code-server` (4.96.4) | ➕ |
| jupyter | `install jupyter` | ➕ |
| apktool/jadx/apksigner/aapt2 | `nibras-apk` (telefon üçün statik aapt2) | ✅ |
| adb/fastboot | `install adb`, `nibras-adb pair/connect` | ➕ |

Root tələb edən (yalnız root olan telefonda): SMB 445 portu, FUSE/sshfs bağlama.

## Android Code Studio (AndroidIDE fork) ilə sətir-sətir uyğunluq

| ACS | Nibras | Status |
|---|---|---|
| Layihə şablonları | `nibras-apk new <qovluq> [paket] --template basic|views|kotlin|compose|library` | ➕ |
| Telefonda Gradle yığımı | Gradle 8.11.1 + AGP 8.7.3, `install gradle`; arm64 CI-da yoxlanılır | ➕ |
| JDK 17/21 | `install jdk 17|21` | ➕ |
| SDK, build-tools, platform-tools | `install android-sdk` (cmdline-tools 19 + telefon üçün aapt2/zipalign) | ➕ |
| NDK / CMake | `install ndk` → clang + cmake + ninja | ⛔ rəsmi NDK yalnız x86_64 host üçündür |
| APK yığ / quraşdır / işlət | `nibras-apk build`, `install`, `run` (yığ → quraşdır → aç) | ➕ |
| Logcat | `nibras-apk logcat [paket]` | ➕ tam logcat üçün `nibras-adb` cütləşməsi lazımdır |
| Git inteqrasiyası | `git ui` (lazygit), `tig`, `git status/diff/branch` | ➕ |
| Fayl ağacı | Fayl meneceri (2 panel) | ✅ |
| Terminal | Nibras Terminal | ✅ |
| Redaktor: rəngləmə, çox tab, axtar/əvəz et (regex) | ✅ |
| Java/Kotlin/XML tamamlama | Açar sözlər + Android XML atributları/teqləri + snippetlər | ➕ (yığcam, LSP deyil) |
| Diaqnostika | Layihədə Gradle kompilyasiyası, ayrı faylda javac/kotlinc, xmllint | ➕ |
| Format | Hər dil üçün (google-java-format, ktfmt, xmllint, black, prettier, shfmt, clang-format, gofmt, rustfmt, stylua) | ➕ |
| Layout XML önizləmə | Yığcam önizləmə: əsas widget-lər, @string/@color/@dimen/@drawable; ConstraintLayout/RelativeLayout sütun kimi | ➕ (yığcam, sürüklə-burax dizayner yoxdur) |
| LSP | `install lsp` (helix/neovim + dil serverləri) | 🟡 redaktorun içində deyil |
| Pluginlər, temalar | `plugin`, 12 tema | ✅ |
