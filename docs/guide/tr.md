# Nibras Terminal — Kullanım Kılavuzu (Türkçe)

[← README'ye dön](../../README.md) · [Tüm diller](../../README.md#-guides-in-your-language)

## 1. Nibras Terminal nedir?

Nibras Terminal, Android telefonlar için eksiksiz bir Linux terminalidir. Uygulama doğrudan gerçek bir terminalle açılır, Alpine Linux hazır olarak çalışır ve Debian, Ubuntu, Arch, Fedora ile Kali gerektiğinde eklenebilir. Uygulamada dil sunucusu destekli bir kod editörü, dosya yöneticisi, SSH istemcisi, APK oluşturma ve düzenleme araçları, medya araçları ve yalnızca Nibras'ta bulunan pek çok komut vardır. Ağır araçlar yalnızca siz istediğinizde indirildiği için uygulamanın kendisi küçük kalır.

## 2. Kurulum ve ilk açılış

1. APK'yı doğrudan indirin: https://github.com/nibrascode/nibras-terminal/releases/download/v1.0.0/nibras-terminal-1.0.0.apk
2. Dosyayı açın ve Android sorduğunda "Bilinmeyen uygulamaları yükle" iznini verin.
3. İlk açılışta Nibras Terminal bir dil seçim ekranı gösterir. Tüm diller kendi adlarıyla listelenir ve telefonunuzun dili önceden seçilidir. Telefonunuzun dili mevcut değilse İngilizce kullanılır.
4. Yerleşik Alpine Linux ortamı otomatik olarak hazırlanır ve terminal açılır.

Dili istediğiniz zaman `lang` komutuyla veya Ayarlar'dan değiştirebilirsiniz.

## 3. Temel kullanım

- Oturumlar, ortamlar, araçlar ve ayarların bulunduğu menüyü açmak için ekranın sol kenarından kaydırın veya ek tuş satırındaki `☰` simgesine dokunun.
- Bir oturumu yeniden adlandırmak veya kapatmak için üzerine uzun basın. Ortamı (Alpine, Debian, Ubuntu veya Android kabuğu) seçmek için "Yeni oturum" düğmesine uzun basın.
- Ek tuş satırında `ESC`, `TAB`, `CTRL`, `ALT`, yön tuşları ve daha fazlası bulunur. `CTRL` ve `ALT` bir sonraki tuşa kadar basılı kalır.
- Ses kısma tuşu `CTRL`, ses açma tuşu ise ek tuş görevi görür.
- Tüm Nibras araçlarının menüsünü açmak için `nibras`, kısa bir komut listesi için `help` yazın.

```sh
nibras
help
```

## 4. `install` ile araç kurmak

Araçlar APK'nın içinde tutulmaz. Tüm paketleri kısa açıklamalarıyla görmek için `install`, birini eklemek için `install <ad>` yazın. Nibras paketi olmayan programlar `pkg install <ad>` ile kurulur.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Linux ortamları (dağıtımlar)

Alpine Linux uygulamanın içindedir. Diğer dağıtımlar istek üzerine indirilir ve her biri ayrı bir ortam olarak çalışır.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Programlama dilleri ve `run`

`run` komutu dosyayı uzantısından tanır, gereken aracı ilk seferde kendisi kurar ve dosyayı çalıştırır. Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell ve diğer diller desteklenir. Bir klasör verdiğinizde proje türünü (Cargo, Go modülleri, npm, Gradle, CMake, Makefile ve diğerleri) kendisi algılar.

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Kod editörü ve LSP

Bir dosyayı `edit <dosya>` komutuyla veya dosya yöneticisinden açın. Editör 25'ten fazla dilin sözdizimini renklendirir, anahtar kelimeleri ve kod parçacıklarını tamamlar, kodu biçimlendirir, Markdown, HTML ve Android düzenlerini önizler ve dosyayı ▶ düğmesiyle çalıştırır.

Akıllı tamamlama, hataların altının çizilmesi, açıklamalar ve tanıma gitme için bir dil sunucusunu bir kez kurun. Sunucu çalışırken durum satırında 🧠 görünür.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Bir kelimenin açıklamasını görmek için editörde üzerine uzun basın. ⋯ menüsünde "Sorunlar", "İmleçteki bilgi" ve "Tanıma git" seçenekleri bulunur.

## 8. Dosya yöneticisi

Dosya yöneticisini yan menüden veya `files` komutuyla açın. Dosyaları kopyalayabilir, taşıyabilir, yeniden adlandırabilir, silebilir, sıkıştırabilir, arşivden çıkarabilir, paylaşabilir ve açabilirsiniz. `nibras-setup-storage` komutunu çalıştırdıktan sonra telefon depolamasına da erişebilirsiniz.

```sh
files
nibras-setup-storage
```

## 9. SSH

Yan menüdeki SSH ekranı sunucularınızı, anahtarlarınızı, port yönlendirmelerinizi ve atlama sunucularınızı saklar. Mosh, Telnet ve SFTP desteklenir. Telefonda bir SSH sunucusu da çalıştırabilirsiniz.

```sh
ssh user@example.com
nibras-sshd
```

Sunucular bir `ssh config` dosyası olarak dışa ve içe aktarılır; böylece onları bulut hesabı olmadan cihazlar arasında taşıyabilirsiniz.

## 10. APK araçları ve kaynak düzenleyici

`nibras-apk`, Android uygulamalarını telefonda derler, açar, düzenler ve imzalar.

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

Görsel kaynak düzenleyici (`nibras-apk res`), her dil için metinleri bir tabloda düzenlemenizi, renkleri değiştirmenizi, simgeleri ve görselleri galeriden değiştirmenizi, uygulama adını, paket adını ve sürümü güncellemenizi sağlar. Ardından tek dokunuşla uygulama yeniden derlenir, imzalanır ve kurulur. Başkalarının lisans denetimlerini veya korumalarını kıran özellikler bilerek eklenmemiştir.

## 11. Video, ses, indirmeler ve belgeler

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

`download` komutu bir bağlantıdan video, müzik ve dosya indirir. Lütfen yalnızca kaydetme hakkınız olan içerikleri indirin. `media` komutu aynı işler için görsel bir ekran açar.

## 12. Kişiselleştirme ve renkli yazılar

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

`theme list`, `prompt list`, `font list` ve `color list` tüm seçenekleri gösterir. `customize` tüm ayarları tek bir menüde açar, `reset-look` ise her şeyi varsayılana döndürür. Ayarlar `~/.nibras/` klasöründe saklanır.

## 13. Dil komutları

Terminal birçok dilde doğal ifadeleri anlar ve büyük-küçük harf fark etmez.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Arapça, Farsça ve Urduca ekranlar sağdan sola düzenlenir. Terminalde Arapça metni harfleri birleşik olarak göstermek için `rtl` komutunu kullanın, örneğin: `cat note.txt | rtl`.

### Kendi dilinizde komutlar

Nibras komutlarını Türkçe olarak da yazabilirsiniz. İngilizce adlar her zaman çalışır; `ls`, `git` ve `cd` gibi standart Linux komutlarının adları değişmez. Komutlar Türkçe karakterlerle de karaktersiz de çalışır, örneğin `yardım` ve `yardim`.

| Türkçe | İngilizce |
|---|---|
| `yardım` | `help` |
| `kur python` | `install python` |
| `kaldır` | `remove` |
| `güncelle` | `update` |
| `çalıştır hello.py` | `run hello.py` |
| `tema dracula` | `theme dracula` |
| `renk red Salam` | `color red Salam` |
| `başlık` | `banner` |
| `istem` | `prompt` |
| `özelleştir` | `customize` |
| `betikler` | `scripts` |
| `uzaktan` | `remote` |
| `yedekle` | `backup` |
| `geriyükle` | `restore` |
| `öğren` | `learn` |
| `düzelt` | `fix` |
| `sor` | `ask` |
| `açıkla` | `explain` |
| `namaz` | `prayer` |
| `ses` | `audio` |
| `indir` | `download` |
| `medya` | `media` |
| `dosyalar` | `files` |
| `düzenle` | `edit` |
| `menü` | `menu` |
| `kuran` | `quran` |
<!-- native-commands -->

## 14. Yalnızca Nibras'ta olan özellikler

- `ask`, `explain` ve `fix`, komutlar ve hatalar konusunda size yardımcı olur.
- `recipes` ve `learn` hazır çözümler ve Linux dersleri sunar.
- `vault` özel dosyaları şifreli saklar, `trash` ise silmeyi güvenli hâle getirir.
- `quran`, `hadith` ve hatırlatmalı `prayer` yerleşiktir.
- `llm` telefonda çevrimdışı bir yapay zekâ sohbeti çalıştırır.
- `webterm` bu terminali Wi-Fi üzerinden bir bilgisayarın tarayıcısında açar.
- Bölünmüş ekran, yüzen pencere ve Linux masaüstü (XFCE) uygulamanın içinde çalışır.
- `scripts` betik mağazasını açar. Orada hazır betiklere göz atabilir, kaynağını okuyabilir, kurabilir ve çalıştırabilirsiniz.
- `remote` bu terminali aynı Wi-Fi ağındaki bir bilgisayarın tarayıcısında açar. Tek kullanımlık bağlantı QR kod olarak da gösterilir.
- `backup drive` sunucularınızı ve ayarlarınızı kendi parolanızla şifreleyerek Google Drive'a veya herhangi bir klasöre kaydeder. `restore drive` onları geri yükler.
- `learn` etkileşimli dersleri başlatır: gerçek komutları yazarsınız ve otomatik olarak kontrol edilir.
- Ana ekran widget'ları sık kullandığınız komutları tek dokunuşla çalıştırır, sunucu durumunu ve namaz vakitlerini gösterir.

## 15. İzinler

Nibras Terminal bir izni yalnızca bir komut ona ihtiyaç duyduğunda ister. Kamera, mikrofon, konum, kişiler ve SMS komutları ilk kullanımda izin ister. Oluşturduğunuz APK dosyalarını kurmak için "Bilinmeyen uygulamaları yükle" izni gerekir. Namaz hatırlatmaları için "Alarmlar ve hatırlatıcılar" izni gerekir. Bildirim erişimi, "Sistem ayarlarını değiştirme" ve kablosuz hata ayıklama eşleştirmesi (`nibras-adb pair`) isteğe bağlıdır ve yalnızca bunlardan söz eden komutlar tarafından kullanılır. Root olmadan Windows dosya paylaşımı 445 yerine 4445 numaralı portu kullanır ve `sshfs` gibi FUSE tabanlı araçlar kullanılamaz.

## 16. Sorun giderme

- Bir komut bulunamazsa yine de yazın: Nibras doğru paketi önerecektir.
- Bir kurulum yarıda kalırsa aynı `install` komutunu tekrar çalıştırın; indirme kaldığı yerden devam eder ve doğrulanır.
- Ayar dosyalarında yaptığınız değişiklikleri uygulamak için `nibras-reload-settings` yazın.
- Uygulama beklenmedik şekilde kapanırsa hata raporu `crash.txt` dosyasına kaydedilir ve bir sonraki açılışta onu gösterip paylaşmanız önerilir. GitHub'da sorun bildirirken lütfen bu dosyayı ekleyin.

## 17. Sık sorulan sorular

**Nibras Terminal ücretsiz mi?** Evet. Açık kaynaklıdır.

**Root gerekir mi?** Hayır. Her şey root olmadan çalışır; yukarıda anlatıldığı gibi yalnızca birkaç sistem düzeyindeki iş sınırlıdır.

**Uygulama neden bu kadar küçük?** İçinde yalnızca Alpine Linux ve çeviriler bulunur. Geri kalan her şey ihtiyaç duyduğunuzda `install` ile indirilir.

**32 bit telefonlarda çalışır mı?** Evet. Tek bir APK hem arm64 hem de arm32 cihazları destekler.
