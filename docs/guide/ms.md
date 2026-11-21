# Nibras Terminal — Panduan Pengguna (Bahasa Melayu)

[← Kembali ke README](../../README.md) · [Semua bahasa](../../README.md#-guides-in-your-language)

## 1. Apakah Nibras Terminal?

Nibras Terminal ialah terminal Linux lengkap untuk telefon Android. Aplikasi ini terus dibuka dalam terminal sebenar, menjalankan Alpine Linux tanpa sebarang tetapan, dan boleh menambah Debian, Ubuntu, Arch, Fedora serta Kali bila-bila masa anda memerlukannya. Ia juga mempunyai editor kod dengan sokongan pelayan bahasa, pengurus fail, klien SSH, alat untuk membina dan mengubah suai fail APK, alat media dan banyak arahan yang hanya terdapat dalam Nibras. Saiz aplikasi kekal kecil kerana alat yang besar hanya dimuat turun apabila anda memintanya.

## 2. Pemasangan dan pelancaran pertama

1. Muat turun APK terkini dari halaman projek di GitHub (Actions → *Android package* → artifact) atau dari pautan yang dikongsi oleh pembangun.
2. Buka fail itu dan benarkan "Pasang apl tidak diketahui" apabila Android memintanya.
3. Pada pelancaran pertama, Nibras Terminal memaparkan pemilih bahasa. Setiap bahasa ditulis dengan namanya sendiri, dan bahasa telefon anda sudah dipilih. Jika bahasa telefon anda tiada, bahasa Inggeris akan digunakan.
4. Persekitaran Alpine Linux terbina dalam disediakan secara automatik, kemudian terminal dibuka.

Anda boleh menukar bahasa pada bila-bila masa dengan arahan `lang` atau melalui Tetapan.

## 3. Asas

- Leret dari tepi kiri skrin, atau ketik `☰` pada baris kekunci tambahan, untuk membuka menu sisi yang mengandungi sesi, persekitaran, alat dan tetapan.
- Tekan lama pada sesi untuk menamakan semula atau menutupnya. Tekan lama "Sesi baharu" untuk memilih persekitaran (Alpine, Debian, Ubuntu atau shell Android).
- Baris kekunci tambahan menyediakan `ESC`, `TAB`, `CTRL`, `ALT`, kekunci anak panah dan banyak lagi. `CTRL` dan `ALT` kekal ditekan sehingga kekunci seterusnya.
- Butang kurangkan kelantangan berfungsi sebagai `CTRL` dan butang kuatkan kelantangan berfungsi sebagai pengubah suai kekunci tambahan.
- Taip `nibras` untuk membuka menu semua alat Nibras, dan taip `help` untuk melihat senarai ringkas arahan.

```sh
nibras
help
```

## 4. Memasang alat dengan `install`

Alat tidak disimpan di dalam APK. Taip `install` untuk melihat semua pek berserta penerangan ringkas, dan taip `install <nama>` untuk menambah salah satu daripadanya. Program yang bukan pek Nibras dipasang dengan `pkg install <nama>`.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Persekitaran Linux (distribusi)

Alpine Linux disertakan dalam aplikasi. Distribusi lain dimuat turun apabila diminta, dan setiap satu berjalan sebagai persekitaran yang berasingan.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Bahasa pengaturcaraan dan `run`

Arahan `run` mengenal pasti fail melalui sambungannya, memasang alat yang diperlukan pada kali pertama, kemudian menjalankan fail tersebut. Ia menyokong Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell dan banyak lagi. Jika anda memberikannya folder, ia akan mengesan jenis projek (Cargo, modul Go, npm, Gradle, CMake, Makefile dan lain-lain).

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Editor kod dan LSP

Buka fail dengan `edit <fail>` atau dari pengurus fail. Editor ini mewarnakan sintaks lebih daripada 25 bahasa, melengkapkan kata kunci dan coretan kod, memformat kod, memaparkan pratonton Markdown, HTML dan susun atur Android, serta menjalankan fail dengan butang ▶.

Untuk pelengkapan pintar, garis bawah pada ralat, maklumat apabila kursor dihalakan dan lompat ke definisi, pasang pelayan bahasa sekali sahaja. Bar status memaparkan 🧠 apabila pelayan sedang berjalan.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Tekan lama pada sesuatu perkataan dalam editor untuk melihat dokumentasinya. Menu ⋯ mengandungi "Masalah", "Maklumat di kursor" dan "Pergi ke definisi".

## 8. Pengurus fail

Buka pengurus fail dari menu sisi atau dengan arahan `files`. Anda boleh menyalin, memindahkan, menamakan semula, memadam, memampatkan, mengekstrak, berkongsi dan membuka fail, dan anda boleh mencapai storan telefon selepas menjalankan `nibras-setup-storage`.

```sh
files
nibras-setup-storage
```

## 9. SSH

Skrin SSH dalam menu sisi menyimpan hos, kunci, pemajuan port dan hos lompatan (jump host) anda. Mosh, Telnet dan SFTP disokong. Anda juga boleh menjalankan pelayan SSH pada telefon.

```sh
ssh user@example.com
nibras-sshd
```

Hos dieksport dan diimport sebagai fail `ssh config`, jadi anda boleh memindahkannya antara peranti tanpa akaun awan.

## 10. Alat APK dan editor sumber

`nibras-apk` membina, membongkar, menyunting dan menandatangani aplikasi Android terus pada telefon.

```sh
nibras-apk wizard
nibras-apk new MyApp com.example.myapp --template kotlin
nibras-apk run MyApp
nibras-apk decompile app.apk
nibras-apk res app.apk
nibras-apk merge app.apks
nibras-apk install-split app.xapk
nibras-apk apps
nibras-apk extract com.example.app
```

Editor sumber visual (`nibras-apk res`) membolehkan anda menyunting teks bagi setiap bahasa dalam jadual, menukar warna, menggantikan ikon dan gambar dari galeri, serta menukar nama aplikasi, nama pakej dan versi. Kemudian satu ketikan akan membina semula, menandatangani dan memasang aplikasi itu. Ciri yang memecahkan semakan lesen atau perlindungan aplikasi orang lain sengaja tidak disertakan.

## 11. Video, audio, muat turun dan dokumen

```sh
video merge a.mp4 b.mp4
video cut in.mp4 00:01:00 00:02:30
video compress in.mp4
audio convert in.wav out.mp3
image resize photo.jpg 50%
pdf merge a.pdf b.pdf
download https://example.com/video
media
```

`download` menyimpan video, muzik dan fail daripada pautan. Sila muat turun kandungan yang anda berhak simpan sahaja. Arahan `media` membuka skrin visual untuk tugas yang sama.

## 12. Penyesuaian dan teks berwarna

```sh
color bold red "Hello"
color rainbow "Nibras Terminal"
echo "Done" | color green
banner Nibras rainbow
theme dracula
theme custom #1e1e2e #cdd6f4
font jetbrains-mono
font size 16
cursor bar blink on
wallpaper ~/storage/dcim/photo.jpg
prompt powerline
keys dev
customize
```

`theme list`, `prompt list`, `font list` dan `color list` memaparkan semua pilihan. `customize` membuka satu menu dengan semua tetapan, dan `reset-look` mengembalikan semuanya kepada lalai. Tetapan disimpan dalam `~/.nibras/`.

## 13. Arahan bahasa

Terminal memahami frasa semula jadi dalam beberapa bahasa, dan huruf besar atau kecil tidak penting.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Skrin dalam bahasa Arab, Parsi dan Urdu disusun dari kanan ke kiri. Untuk memaparkan teks Arab dengan huruf bersambung di dalam terminal, gunakan arahan `rtl`, contohnya `cat note.txt | rtl`.

### Arahan dalam bahasa anda

Anda juga boleh menaip arahan Nibras dalam bahasa Melayu. Nama bahasa Inggeris sentiasa berfungsi, dan arahan Linux standard seperti `ls`, `git` dan `cd` tidak ditukar nama.

| Bahasa Melayu | Bahasa Inggeris |
|---|---|
| `bantuan` | `help` |
| `pasang python` | `install python` |
| `buang` | `remove` |
| `kemaskini` | `update` |
| `jalankan hello.py` | `run hello.py` |
| `bahasa` | `lang` |
| `tema dracula` | `theme dracula` |
| `warna red Salam` | `color red Salam` |
| `sepanduk` | `banner` |
| `gesaan` | `prompt` |
| `sesuaikan` | `customize` |
| `skrip` | `scripts` |
| `jauh` | `remote` |
| `sandaran` | `backup` |
| `pulihkan` | `restore` |
| `belajar` | `learn` |
| `baiki` | `fix` |
| `tanya` | `ask` |
| `terangkan` | `explain` |
| `solat` | `prayer` |
| `muatturun` | `download` |
| `fail` | `files` |
| `sunting` | `edit` |
<!-- native-commands -->

## 14. Ciri yang hanya ada dalam Nibras

- `ask`, `explain` dan `fix` membantu anda dengan arahan dan ralat.
- `recipes` dan `learn` mengandungi penyelesaian sedia ada dan pelajaran Linux.
- `vault` menyimpan fail peribadi secara tersulit, dan `trash` menjadikan pemadaman selamat.
- `quran`, `hadith` dan `prayer` (dengan peringatan) terbina dalam aplikasi.
- `llm` menjalankan sembang AI luar talian pada telefon.
- `webterm` membuka terminal ini dalam pelayar komputer melalui Wi-Fi.
- Skrin pisah, tetingkap terapung dan desktop Linux (XFCE) berfungsi di dalam aplikasi.
- `scripts` membuka kedai skrip, tempat anda boleh melayari, membaca, memasang dan menjalankan skrip sedia ada.
- `remote` membuka terminal ini dalam pelayar komputer pada Wi-Fi yang sama. Pautan sekali guna juga dipaparkan sebagai kod QR.
- `backup drive` menyimpan hos dan tetapan anda, disulitkan dengan frasa laluan anda sendiri, ke Google Drive atau mana-mana folder. `restore drive` memulihkannya.
- `learn` memulakan pelajaran interaktif: anda menaip arahan sebenar dan ia disemak secara automatik.
- Widget skrin utama menjalankan arahan kegemaran anda dengan satu sentuhan serta memaparkan status pelayan dan waktu solat.

## 15. Kebenaran

Nibras Terminal hanya meminta kebenaran apabila sesuatu arahan memerlukannya. Arahan kamera, mikrofon, lokasi, kenalan dan SMS meminta kebenaran pada kali pertama anda menggunakannya. "Pasang apl tidak diketahui" diperlukan untuk memasang fail APK yang anda bina. "Penggera & peringatan" diperlukan untuk peringatan solat. Akses pemberitahuan, "Ubah suai tetapan sistem" dan pemasangan penyahpepijatan wayarles (`nibras-adb pair`) adalah pilihan dan hanya digunakan oleh arahan yang menyebutnya. Tanpa root, perkongsian fail Windows menggunakan port 4445 dan bukan 445, dan alat berasaskan FUSE seperti `sshfs` tidak tersedia.

## 16. Penyelesaian masalah

- Jika sesuatu arahan tiada, taip sahaja: Nibras akan mencadangkan pek yang betul.
- Jika pemasangan terhenti, jalankan semula arahan `install` yang sama; muat turun akan disambung dan disemak.
- `nibras-reload-settings` menggunakan perubahan yang dibuat pada fail tetapan.
- Jika aplikasi tertutup secara tiba-tiba, laporan ralat disimpan dalam `crash.txt`, dan pada pelancaran seterusnya aplikasi menawarkan untuk memaparkan dan berkongsinya. Sila lampirkan fail ini apabila melaporkan masalah di GitHub.

## 17. Soalan lazim

**Adakah Nibras Terminal percuma?** Ya. Ia sumber terbuka.

**Adakah ia memerlukan root?** Tidak. Semuanya berjalan tanpa root; hanya beberapa tugas peringkat sistem yang terhad, seperti yang diterangkan di atas.

**Mengapakah aplikasi ini begitu kecil?** Ia hanya mengandungi Alpine Linux dan terjemahan. Semua yang lain dimuat turun dengan `install` apabila anda memerlukannya.

**Adakah ia berfungsi pada telefon 32-bit?** Ya. Satu APK menyokong peranti arm64 dan arm32.
