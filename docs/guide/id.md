# Nibras Terminal — Panduan Pengguna (Bahasa Indonesia)

[← Kembali ke README](../../README.md) · [Semua bahasa](../../README.md#-guides-in-your-language)

## 1. Apa itu Nibras Terminal?

Nibras Terminal adalah terminal Linux lengkap untuk ponsel Android. Aplikasi ini langsung terbuka di terminal sungguhan, menjalankan Alpine Linux tanpa pengaturan apa pun, dan dapat menambahkan Debian, Ubuntu, Arch, Fedora, serta Kali kapan pun Anda membutuhkannya. Aplikasi ini juga dilengkapi editor kode dengan dukungan server bahasa, pengelola file, klien SSH, alat untuk membuat dan mengubah file APK, alat media, dan banyak perintah yang hanya ada di Nibras. Ukuran aplikasinya tetap kecil karena alat-alat berukuran besar baru diunduh saat Anda memintanya.

## 2. Pemasangan dan peluncuran pertama

1. Unduh APK langsung: https://github.com/nibrascode/nibras-terminal/releases/latest/download/nibras-terminal.apk
Kamera, mikrofon, SMS, kontak, dan lokasi hanya diminta saat perintah itu dijalankan, bukan saat aplikasi dibuka.
Pembaruan otomatis (Obtainium): https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/nibrascode/nibras-terminal
2. Buka file tersebut dan izinkan "Instal aplikasi tidak dikenal" saat Android memintanya.
3. Pada peluncuran pertama, Nibras Terminal menampilkan pemilih bahasa. Setiap bahasa ditulis dengan namanya sendiri, dan bahasa ponsel Anda sudah terpilih. Jika bahasa ponsel Anda tidak tersedia, bahasa Inggris yang digunakan.
4. Lingkungan Alpine Linux bawaan disiapkan secara otomatis, lalu terminal terbuka.

Anda dapat mengganti bahasa kapan saja dengan perintah `lang` atau melalui Setelan.

## 3. Dasar-dasar

- Geser dari tepi kiri layar, atau ketuk `☰` di baris tombol tambahan, untuk membuka menu samping yang berisi sesi, lingkungan, alat, dan setelan.
- Tekan lama sebuah sesi untuk mengganti namanya atau menutupnya. Tekan lama "Sesi baru" untuk memilih lingkungan (Alpine, Debian, Ubuntu, atau shell Android).
- Baris tombol tambahan menyediakan `ESC`, `TAB`, `CTRL`, `ALT`, tombol panah, dan lainnya. `CTRL` dan `ALT` tetap tertekan sampai tombol berikutnya.
- Tombol volume turun berfungsi sebagai `CTRL` dan tombol volume naik berfungsi sebagai pengubah tombol tambahan.
- Ketik `nibras` untuk membuka menu semua alat Nibras, dan ketik `help` untuk melihat daftar singkat perintah.

```sh
nibras
help
```

## 4. Memasang alat dengan `install`

Alat-alat tidak disimpan di dalam APK. Ketik `install` untuk melihat semua paket beserta deskripsi singkatnya, lalu ketik `install <nama>` untuk menambahkan salah satunya. Program yang bukan paket Nibras dipasang dengan `pkg install <nama>`.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Lingkungan Linux (distribusi)

Alpine Linux sudah tersedia di dalam aplikasi. Distribusi lain diunduh sesuai permintaan, dan masing-masing berjalan sebagai lingkungan terpisah.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Bahasa pemrograman dan `run`

Perintah `run` mengenali file dari ekstensinya, memasang alat yang diperlukan pada kali pertama, lalu menjalankan file tersebut. Perintah ini mendukung Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell, dan lainnya. Jika Anda memberinya sebuah folder, perintah ini mendeteksi jenis proyeknya (Cargo, modul Go, npm, Gradle, CMake, Makefile, dan lainnya).

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Editor kode dan LSP

Buka file dengan `edit <file>` atau dari pengelola file. Editor ini mewarnai sintaks lebih dari 25 bahasa, melengkapi kata kunci dan cuplikan kode, merapikan kode, menampilkan pratinjau Markdown, HTML, dan tata letak Android, serta menjalankan file dengan tombol ▶.

Untuk pelengkapan cerdas, garis bawah pada kesalahan, informasi saat kursor diarahkan, dan lompat ke definisi, pasang server bahasa satu kali. Bilah status menampilkan 🧠 saat server sedang berjalan.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Tekan lama sebuah kata di editor untuk melihat dokumentasinya. Menu ⋯ berisi "Masalah", "Info di kursor", dan "Ke definisi".

## 8. Pengelola file

Buka pengelola file dari menu samping atau dengan perintah `files`. Anda dapat menyalin, memindahkan, mengganti nama, menghapus, mengompres, mengekstrak, membagikan, dan membuka file, serta dapat mengakses penyimpanan ponsel setelah menjalankan `nibras-setup-storage`.

```sh
files
nibras-setup-storage
```

## 9. SSH

Layar SSH di menu samping menyimpan host, kunci, penerusan port, dan jump host Anda. Mosh, Telnet, dan SFTP didukung. Anda juga dapat menjalankan server SSH di ponsel.

```sh
ssh user@example.com
nibras-sshd
```

Host diekspor dan diimpor sebagai file `ssh config`, sehingga Anda dapat memindahkannya antarperangkat tanpa akun cloud.

## 10. Alat APK dan editor sumber daya

`nibras-apk` membangun, membongkar, menyunting, dan menandatangani aplikasi Android langsung di ponsel.

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

Editor sumber daya visual (`nibras-apk res`) memungkinkan Anda menyunting teks setiap bahasa dalam tabel, mengubah warna, mengganti ikon dan gambar dari galeri, serta mengubah nama aplikasi, nama paket, dan versi. Setelah itu, satu ketukan akan membangun ulang, menandatangani, dan memasang aplikasi. Fitur yang membobol pemeriksaan lisensi atau perlindungan aplikasi milik orang lain sengaja tidak disertakan.

## 11. Video, audio, unduhan, dan dokumen

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

`download` menyimpan video, musik, dan file dari sebuah tautan. Harap hanya mengunduh konten yang berhak Anda simpan. Perintah `media` membuka layar visual untuk tugas-tugas yang sama.

## 12. Kustomisasi dan teks berwarna

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

`theme list`, `prompt list`, `font list`, dan `color list` menampilkan semua pilihan. `customize` membuka satu menu berisi semua setelan, dan `reset-look` mengembalikan semuanya ke bawaan. Setelan disimpan di `~/.nibras/`.

## 13. Perintah bahasa

Terminal memahami frasa alami dalam beberapa bahasa, dan huruf besar atau kecil tidak berpengaruh.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Layar dalam bahasa Arab, Persia, dan Urdu ditata dari kanan ke kiri. Untuk menampilkan teks Arab dengan huruf bersambung di dalam terminal, gunakan perintah `rtl`, misalnya `cat note.txt | rtl`.

### Perintah dalam bahasa Anda

Anda juga dapat menulis perintah Nibras dalam bahasa Indonesia. Nama bahasa Inggris selalu berfungsi, dan perintah Linux standar seperti `ls`, `git`, dan `cd` tidak diganti namanya.

| Bahasa Indonesia | Bahasa Inggris |
|---|---|
| `bantuan` | `help` |
| `pasang python` | `install python` |
| `hapus` | `remove` |
| `perbarui` | `update` |
| `jalankan hello.py` | `run hello.py` |
| `bahasa` | `lang` |
| `tema dracula` | `theme dracula` |
| `warna red Salam` | `color red Salam` |
| `spanduk` | `banner` |
| `sesuaikan` | `customize` |
| `skrip` | `scripts` |
| `jarakjauh` | `remote` |
| `cadangkan` | `backup` |
| `pulihkan` | `restore` |
| `belajar` | `learn` |
| `perbaiki` | `fix` |
| `tanya` | `ask` |
| `jelaskan` | `explain` |
| `salat` | `prayer` |
| `unduh` | `download` |
| `berkas` | `files` |
| `sunting` | `edit` |
<!-- native-commands -->

## 14. Fitur yang hanya ada di Nibras

- `ask`, `explain`, dan `fix` membantu Anda dengan perintah dan kesalahan.
- `recipes` dan `learn` berisi solusi siap pakai dan pelajaran Linux.
- `vault` menyimpan file pribadi secara terenkripsi, dan `trash` membuat penghapusan menjadi aman.
- `quran`, `hadith`, dan `prayer` (dengan pengingat) sudah tersedia di dalamnya.
- `llm` menjalankan obrolan AI luring di ponsel.
- `webterm` membuka terminal ini di peramban komputer melalui Wi-Fi.
- Layar terpisah, jendela mengambang, dan desktop Linux (XFCE) berjalan di dalam aplikasi.
- `scripts` membuka toko skrip, tempat Anda dapat menjelajah, membaca, memasang, dan menjalankan skrip siap pakai.
- `remote` membuka terminal ini di peramban komputer pada Wi-Fi yang sama. Tautan sekali pakai juga ditampilkan sebagai kode QR.
- `backup drive` menyimpan host dan pengaturan Anda, terenkripsi dengan frasa sandi Anda sendiri, ke Google Drive atau folder mana pun. `restore drive` mengembalikannya.
- `learn` memulai pelajaran interaktif: Anda mengetik perintah sungguhan dan perintah itu diperiksa secara otomatis.
- Widget layar utama menjalankan perintah favorit Anda dengan satu ketukan serta menampilkan status server dan waktu salat.

## 15. Izin

Nibras Terminal hanya meminta izin ketika sebuah perintah membutuhkannya. Perintah kamera, mikrofon, lokasi, kontak, dan SMS meminta izin saat pertama kali digunakan. "Instal aplikasi tidak dikenal" diperlukan untuk memasang file APK yang Anda buat. "Alarm & pengingat" diperlukan untuk pengingat salat. Akses notifikasi, "Ubah setelan sistem", dan penyandingan debugging nirkabel (`nibras-adb pair`) bersifat opsional dan hanya digunakan oleh perintah yang menyebutkannya. Tanpa root, berbagi file Windows menggunakan port 4445 alih-alih 445, dan alat berbasis FUSE seperti `sshfs` tidak tersedia.

## 16. Pemecahan masalah

- Jika sebuah perintah tidak ada, ketik saja: Nibras akan menyarankan paket yang tepat.
- Jika pemasangan terhenti, jalankan lagi perintah `install` yang sama; unduhan akan dilanjutkan dan diperiksa.
- `nibras-reload-settings` menerapkan perubahan yang dibuat pada file setelan.
- Jika aplikasi tertutup secara tiba-tiba, laporan kesalahan disimpan di `crash.txt`, dan pada peluncuran berikutnya aplikasi menawarkan untuk menampilkan dan membagikannya. Harap lampirkan file ini saat melaporkan masalah di GitHub.

## 17. Pertanyaan umum

**Apakah Nibras Terminal gratis?** Ya. Aplikasi ini sumber terbuka.

**Apakah perlu root?** Tidak. Semuanya berjalan tanpa root; hanya beberapa tugas tingkat sistem yang terbatas, seperti dijelaskan di atas.

**Mengapa aplikasinya sangat kecil?** Isinya hanya Alpine Linux dan terjemahan. Semua yang lain diunduh dengan `install` saat Anda membutuhkannya.

**Apakah bisa berjalan di ponsel 32-bit?** Ya. Satu APK mendukung perangkat arm64 dan arm32.
