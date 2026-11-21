# Nibras Terminal — İstifadə qaydası (Azərbaycanca)

[← README-yə qayıt](../../README.md) · [Bütün dillər](../../README.md#-guides-in-your-language)

## 1. Nibras Terminal nədir?

Nibras Terminal Android telefonlar üçün tam Linux terminalıdır. Tətbiq açılan kimi birbaşa əsl terminal gəlir, Alpine Linux hazır işləyir, Debian, Ubuntu, Arch, Fedora və Kali isə lazım olanda əlavə edilir. Tətbiqin içində dil serveri dəstəkli kod redaktoru, fayl meneceri, SSH müştərisi, APK yığmaq və dəyişdirmək üçün alətlər, media alətləri və yalnız Nibras-da olan çoxlu əmr var. Ağır alətlər yalnız siz istəyəndə yükləndiyi üçün tətbiqin özü kiçik qalır.

## 2. Quraşdırma və ilk açılış

1. Ən son APK-nı layihənin GitHub səhifəsindən (Actions → *Android package* → artifact) və ya tərtibatçının göndərdiyi linkdən yükləyin.
2. Faylı açın və Android soruşanda "Naməlum tətbiqləri quraşdırmaq" icazəsini verin.
3. İlk açılışda Nibras Terminal dil seçimi ekranını göstərir. Bütün dillər öz adları ilə yazılıb, telefonunuzun dili isə artıq seçilmiş olur. Telefonunuzun dili siyahıda yoxdursa, ingilis dili istifadə olunur.
4. Daxili Alpine Linux mühiti avtomatik hazırlanır və terminal açılır.

Dili istənilən vaxt `lang` əmri ilə və ya Ayarlarda dəyişə bilərsiniz.

## 3. Əsaslar

- Sessiyalar, mühitlər, alətlər və ayarlar olan menyunu açmaq üçün ekranın sol kənarından sürüşdürün və ya əlavə düymələr sırasındakı `☰` düyməsinə toxunun.
- Sessiyanın adını dəyişmək və ya onu bağlamaq üçün üzərinə uzun basın. Mühiti (Alpine, Debian, Ubuntu və ya Android shell) seçmək üçün "Yeni sessiya" düyməsinə uzun basın.
- Əlavə düymələr sırasında `ESC`, `TAB`, `CTRL`, `ALT`, ox düymələri və başqaları var. `CTRL` və `ALT` növbəti düyməyə qədər basılı qalır.
- Səsi azaltma düyməsi `CTRL`, səsi artırma düyməsi isə əlavə düymə rolunu oynayır.
- Bütün Nibras alətlərinin menyusunu açmaq üçün `nibras`, qısa əmr siyahısı üçün isə `help` yazın.

```sh
nibras
help
```

## 4. `install` ilə alət quraşdırmaq

Alətlər APK-nın içində saxlanılmır. Bütün paketləri qısa izahla görmək üçün `install`, birini əlavə etmək üçün isə `install <ad>` yazın. Nibras paketi olmayan proqramlar `pkg install <ad>` ilə quraşdırılır.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Linux mühitləri (distributivlər)

Alpine Linux tətbiqin içindədir. Digər distributivlər istəyə görə yüklənir və hər biri ayrıca mühit kimi işləyir.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Proqramlaşdırma dilləri və `run`

`run` əmri faylı uzantısından tanıyır, lazım olan aləti ilk dəfə özü quraşdırır və faylı işə salır. Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell və başqa dillər dəstəklənir. Qovluq verəndə layihənin növünü (Cargo, Go modulları, npm, Gradle, CMake, Makefile və s.) özü müəyyən edir.

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Kod redaktoru və LSP

Faylı `edit <fayl>` əmri ilə və ya fayl menecerindən açın. Redaktor 25-dən çox dilin sintaksisini rəngləyir, açar sözləri və şablonları tamamlayır, kodu formatlayır, Markdown, HTML və Android layout önizləməsini göstərir, faylı isə ▶ düyməsi ilə işə salır.

Ağıllı tamamlama, xətaların altından xətt çəkilməsi, izahlar və tərifə keçid üçün dil serverini bir dəfə quraşdırın. Server işləyəndə status sətrində 🧠 görünür.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Sözün izahını görmək üçün redaktorda onun üzərinə uzun basın. ⋯ menyusunda "Problemlər", "Kursordakı məlumat" və "Tərifə keç" bəndləri var.

## 8. Fayl meneceri

Fayl menecerini yan menyudan və ya `files` əmri ilə açın. Burada faylları kopyalamaq, köçürmək, adını dəyişmək, silmək, sıxmaq, arxivdən çıxarmaq, paylaşmaq və açmaq olar. `nibras-setup-storage` əmrini işlətdikdən sonra telefon yaddaşına da girişiniz olur.

```sh
files
nibras-setup-storage
```

## 9. SSH

Yan menyudakı SSH ekranı hostlarınızı, açarlarınızı, port yönləndirmələrini və jump hostları saxlayır. Mosh, Telnet və SFTP dəstəklənir. Telefonda SSH serveri də işə salmaq olar.

```sh
ssh user@example.com
nibras-sshd
```

Hostlar `ssh config` faylı kimi ixrac və idxal olunur, ona görə onları bulud hesabı olmadan bir cihazdan digərinə köçürə bilərsiniz.

## 10. APK alətləri və resurs redaktoru

`nibras-apk` Android tətbiqlərini telefonda yığır, açır, dəyişdirir və imzalayır.

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

Vizual resurs redaktoru (`nibras-apk res`) hər dil üçün mətnləri cədvəldə dəyişməyə, rəngləri redaktə etməyə, ikon və şəkilləri qalereyadan əvəz etməyə, tətbiqin adını, paket adını və versiyasını dəyişməyə imkan verir. Sonra bir toxunuşla tətbiq yenidən yığılır, imzalanır və quraşdırılır. Başqalarının lisenziya yoxlamasını və ya qorumasını sındıran funksiyalar qəsdən əlavə edilməyib.

## 11. Video, audio, yükləmələr və sənədlər

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

`download` əmri linkdən video, musiqi və faylları yükləyir. Zəhmət olmasa, yalnız saxlamaq hüququnuz olan məzmunu yükləyin. `media` əmri eyni işlər üçün vizual ekran açır.

## 12. Fərdiləşdirmə və rəngli yazılar

```sh
color bold red "Salam"
color rainbow "Nibras Terminal"
echo "Hazırdır" | color green
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

`theme list`, `prompt list`, `font list` və `color list` bütün seçimləri göstərir. `customize` bütün ayarları bir menyuda açır, `reset-look` isə hər şeyi standart vəziyyətə qaytarır. Ayarlar `~/.nibras/` qovluğunda saxlanılır.

## 13. Dil əmrləri

Terminal bir neçə dildə təbii ifadələri başa düşür, böyük və kiçik hərflər fərq etmir.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Ərəb, fars və urdu dillərində ekran sağdan sola düzülür. Terminalın içində ərəb mətnini birləşmiş hərflərlə göstərmək üçün `rtl` əmrindən istifadə edin, məsələn: `cat note.txt | rtl`.

## 14. Yalnız Nibras-da olan imkanlar

- `ask`, `explain` və `fix` əmrlərlə və xətalarla işləməyə kömək edir.
- `recipes` və `learn` hazır həllər və Linux dərsləri təqdim edir.
- `vault` şəxsi faylları şifrəli saxlayır, `trash` isə silməni təhlükəsiz edir.
- `quran`, `hadith` və xatırlatmalı `prayer` daxildir.
- `llm` telefonda internetsiz süni zəka söhbəti işlədir.
- `webterm` bu terminalı Wi-Fi üzərindən kompüterin brauzerində açır.
- Ekranı bölmək, üzən pəncərə və Linux masaüstü (XFCE) tətbiqin içində işləyir.

## 15. İcazələr

Nibras Terminal icazəni yalnız əmrə lazım olanda istəyir. Kamera, mikrofon, məkan, kontaktlar və SMS əmrləri ilk istifadədə icazə soruşur. Yığdığınız APK fayllarını quraşdırmaq üçün "Naməlum tətbiqləri quraşdırmaq" icazəsi lazımdır. Namaz xatırlatmaları üçün "Zəngli saatlar və xatırlatmalar" icazəsi lazımdır. Bildirişlərə giriş, "Sistem ayarlarını dəyişmək" və simsiz sazlama ilə cütləşdirmə (`nibras-adb pair`) könüllüdür və yalnız onları tələb edən əmrlər tərəfindən istifadə olunur. Root olmadan Windows fayl paylaşımı 445 əvəzinə 4445 portundan istifadə edir, `sshfs` kimi FUSE tələb edən alətlər isə işləmir.

## 16. Problemlərin həlli

- Əmr tapılmırsa, onu yenə də yazın: Nibras lazım olan paketi təklif edəcək.
- Quraşdırma yarımçıq qalarsa, eyni `install` əmrini yenidən işlədin; yükləmə davam etdirilir və yoxlanılır.
- Ayar fayllarında etdiyiniz dəyişiklikləri tətbiq etmək üçün `nibras-reload-settings` yazın.
- Tətbiq gözlənilmədən bağlanarsa, xəta hesabatı `crash.txt` faylında saxlanılır və növbəti açılışda onu göstərib paylaşmaq təklif olunur. GitHub-da problem bildirəndə bu faylı əlavə edin.

## 17. Tez-tez verilən suallar

**Nibras Terminal pulsuzdurmu?** Bəli. O, açıq mənbəlidir.

**Root lazımdırmı?** Xeyr. Hər şey root olmadan işləyir; yuxarıda yazıldığı kimi, yalnız bir neçə sistem səviyyəli iş məhduddur.

**Tətbiq niyə bu qədər kiçikdir?** İçində yalnız Alpine Linux və tərcümələr var. Qalan hər şey lazım olanda `install` ilə yüklənir.

**32-bit telefonlarda işləyirmi?** Bəli. Bir APK həm arm64, həm də arm32 cihazları dəstəkləyir.
