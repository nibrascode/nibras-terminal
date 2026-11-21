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
- 🟡 termux-usb, termux-nfc, termux-keystore: hələ yoxdur (USB/NFC üçün ayrıca ekran lazımdır)

## Termius · JuiceSSH · ConnectBot
- ✅ Saxlanmış hostlar, qruplar, axtarış, sürətli qoşulma
- ✅ SSH açarı yaratmaq (ed25519/rsa/ecdsa), açarı hosta göndərmək
- ✅ Port yönləndirmə (L/R/D), jump host, keep-alive, agent forwarding
- ✅ Mosh, Telnet, SFTP, hazır əmrlər (snippets), girişdən sonra əmr
- 🟡 Cihazlar arası sinxronizasiya: yoxdur (bulud hesabı tələb edir); hosts faylı `files/hosts.txt`
- ❌ Serial/USB konsol: USB host ekranı yoxdur

## UserLAnd · Andronix · AnLinux · Linux Deploy · NetHunter
- ✅ Alpine, Debian, Ubuntu + öz rootfs-unu idxal etmək
- ✅ XFCE masaüstü (tətbiqin içində), SSH server, `snapshot` ilə mühiti saxla/qaytar
- ✅ Root olan telefonda root shell (su) · `install security` (nmap, hydra, sqlmap…)
- 🟡 Arch/Fedora/Kali: öz rootfs idxalı ilə

## AndroidIDE · AIDE · Acode · Pydroid · Cxxdroid
- ✅ Kod redaktoru: rəngləmə, axtar/əvəz et, sətrə keç, geri al, Run düyməsi
- ✅ Python, Node, C/C++, Go, Rust, Java/Kotlin (`install python|node|dev`)
- ✅ Telefonda APK yığmaq: `nibras apk new/build` (Gradle + Android SDK)

## MT Manager · APK Editor · Apktool · jadx
- ✅ Fayl meneceri (kopyala/kəs/yapışdır, ad dəyiş, paylaş, gizli fayllar, yerlər)
- ✅ `nibras apk decompile/recompile/sign/verify/align/manifest/info/java/dex`
- 🟡 İki panelli fayl meneceri: tək panel

## LADB · Shizuku
- ✅ `nibras-adb pair/connect/shell` (simsiz sazlama) · `nibras-rish` (Shizuku shell)

## Yalnız Nibras-da
`ask`, `explain`, `fix`, `recipes`, `learn` (dərs + test, 4 dil), əmr təklifləri, səslə əmr,
`video`/`download` menyuları, `trash`, `snapshot`, `record`, `webterm`, `share` + QR, `qr`,
`vault`, bufer tarixçəsi, CPU/RAM monitoru, batareya qənaəti, barmaq izi ilə kilid,
Mağaza, `plugin`, `notes`, `llm`, `quran`, `hadith`, `prayer`, `games`, sessiyaların bərpası.
