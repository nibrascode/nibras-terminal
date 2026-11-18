# Linux mühərriki

0.1 buraxılışında PRoot və ya başqa native mühərrik tətbiqin içinə qoyulmayıb.
Səbəb: mühərrik ayrıca native kitabxanalar tələb edir və APK-nı böyütmədən,
yoxlanmamış binary paylamadan mühit açmaq olmaz.

`app/src/main/java/com/nibras/terminal/engine/Managers.kt` sənədindəki
interfeyslər hazırdır. `startEnvironment` hazırda aydın xəta qaytarır:
arxiv yüklənə və SHA-256 ilə yoxlana bilər, amma sessiya açılmır.

Növbəti mərhələ: arm64 PRoot-u ayrıca yükləmə seçimi kimi, rəsmi checksum ilə
əlavə etmək. Tətbiqin içinə gömmək lazım deyil.
