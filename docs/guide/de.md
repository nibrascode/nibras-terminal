# Nibras Terminal — Benutzerhandbuch (Deutsch)

[← Zurück zur README](../../README.md) · [Alle Sprachen](../../README.md#-guides-in-your-language)

## 1. Was ist Nibras Terminal?

Nibras Terminal ist ein vollständiges Linux-Terminal für Android-Smartphones. Die App öffnet sich direkt mit einem echten Terminal, Alpine Linux läuft ohne Einrichtung, und Debian, Ubuntu, Arch, Fedora und Kali lassen sich jederzeit hinzufügen. Außerdem enthält sie einen Code-Editor mit Sprachserver-Unterstützung, einen Dateimanager, einen SSH-Client, Werkzeuge zum Erstellen und Bearbeiten von APK-Dateien, Medienwerkzeuge und viele Befehle, die es nur in Nibras gibt. Die App selbst bleibt klein, weil umfangreiche Werkzeuge erst heruntergeladen werden, wenn Sie sie anfordern.

## 2. Installation und erster Start

1. Laden Sie die neueste APK von der Projektseite auf GitHub (Actions → *Android package* → Artifact) oder über den Link herunter, den Ihnen der Entwickler geschickt hat.
2. Öffnen Sie die Datei und erlauben Sie „Unbekannte Apps installieren“, wenn Android danach fragt.
3. Beim ersten Start zeigt Nibras Terminal eine Sprachauswahl. Jede Sprache steht unter ihrem eigenen Namen, und die Sprache Ihres Telefons ist bereits ausgewählt. Ist die Sprache Ihres Telefons nicht verfügbar, wird Englisch verwendet.
4. Die eingebaute Alpine-Linux-Umgebung wird automatisch vorbereitet, und das Terminal öffnet sich.

Sie können die Sprache jederzeit mit dem Befehl `lang` oder in den Einstellungen ändern.

## 3. Grundlagen

- Wischen Sie vom linken Bildschirmrand oder tippen Sie in der Zusatztastenleiste auf `☰`, um das Seitenmenü mit Sitzungen, Umgebungen, Werkzeugen und Einstellungen zu öffnen.
- Halten Sie eine Sitzung gedrückt, um sie umzubenennen oder zu schließen. Halten Sie „Neue Sitzung“ gedrückt, um die Umgebung zu wählen (Alpine, Debian, Ubuntu oder die Android-Shell).
- Die Zusatztastenleiste bietet `ESC`, `TAB`, `CTRL`, `ALT`, die Pfeiltasten und mehr. `CTRL` und `ALT` bleiben bis zur nächsten Taste gedrückt.
- Die Leiser-Taste wirkt als `CTRL` und die Lauter-Taste als Modifikator für die Zusatztasten.
- Geben Sie `nibras` ein, um das Menü aller Nibras-Werkzeuge zu öffnen, und `help` für eine kurze Befehlsliste.

```sh
nibras
help
```

## 4. Werkzeuge mit `install` installieren

Werkzeuge sind nicht in der APK gespeichert. Geben Sie `install` ein, um alle Pakete mit einer kurzen Beschreibung zu sehen, und `install <Name>`, um eines hinzuzufügen. Programme, die keine Nibras-Pakete sind, werden mit `pkg install <Name>` installiert.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Linux-Umgebungen (Distributionen)

Alpine Linux ist in der App enthalten. Andere Distributionen werden auf Anfrage heruntergeladen, und jede läuft als eigene Umgebung.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Programmiersprachen und `run`

Der Befehl `run` erkennt eine Datei an ihrer Endung, installiert beim ersten Mal das nötige Werkzeug und führt die Datei aus. Er funktioniert mit Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell und weiteren Sprachen. Wenn Sie ihm einen Ordner übergeben, erkennt er den Projekttyp (Cargo, Go-Module, npm, Gradle, CMake, Makefile und andere).

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Code-Editor und LSP

Öffnen Sie eine Datei mit `edit <Datei>` oder aus dem Dateimanager. Der Editor hebt die Syntax von mehr als 25 Sprachen hervor, vervollständigt Schlüsselwörter und Snippets, formatiert Code, zeigt eine Vorschau für Markdown, HTML und Android-Layouts und führt die Datei mit der Schaltfläche ▶ aus.

Für intelligente Vervollständigung, unterstrichene Fehler, Hover-Informationen und „Gehe zu Definition“ installieren Sie einmalig einen Sprachserver. Die Statusleiste zeigt 🧠, wenn der Server läuft.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Halten Sie im Editor ein Wort gedrückt, um seine Dokumentation zu sehen. Das Menü ⋯ enthält „Probleme“, „Info am Cursor“ und „Gehe zu Definition“.

## 8. Dateimanager

Öffnen Sie den Dateimanager über das Seitenmenü oder mit dem Befehl `files`. Sie können Dateien kopieren, verschieben, umbenennen, löschen, komprimieren, entpacken, teilen und öffnen, und nach `nibras-setup-storage` haben Sie auch Zugriff auf den Telefonspeicher.

```sh
files
nibras-setup-storage
```

## 9. SSH

Der SSH-Bildschirm im Seitenmenü speichert Ihre Hosts, Schlüssel, Portweiterleitungen und Jump-Hosts. Mosh, Telnet und SFTP werden unterstützt. Sie können auch einen SSH-Server auf dem Telefon betreiben.

```sh
ssh user@example.com
nibras-sshd
```

Hosts werden als `ssh config`-Datei exportiert und importiert, sodass Sie sie ohne Cloud-Konto zwischen Geräten übertragen können.

## 10. APK-Werkzeuge und Ressourcen-Editor

`nibras-apk` erstellt, zerlegt, bearbeitet und signiert Android-Apps direkt auf dem Telefon.

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

Mit dem visuellen Ressourcen-Editor (`nibras-apk res`) bearbeiten Sie die Texte jeder Sprache in einer Tabelle, ändern Farben, ersetzen Symbole und Bilder aus der Galerie und ändern App-Namen, Paketnamen und Version. Ein Tippen erstellt, signiert und installiert die App anschließend neu. Funktionen, die Lizenzprüfungen oder Schutzmechanismen fremder Apps aushebeln, sind bewusst nicht enthalten.

## 11. Video, Audio, Downloads und Dokumente

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

`download` speichert Videos, Musik und Dateien über einen Link. Bitte laden Sie nur Inhalte herunter, die Sie speichern dürfen. Der Befehl `media` öffnet eine grafische Oberfläche für dieselben Aufgaben.

## 12. Anpassung und farbiger Text

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

`theme list`, `prompt list`, `font list` und `color list` zeigen alle Optionen. `customize` öffnet ein Menü mit allen Einstellungen, und `reset-look` setzt alles auf die Standardwerte zurück. Die Einstellungen werden in `~/.nibras/` gespeichert.

## 13. Sprachbefehle

Das Terminal versteht natürliche Sätze in mehreren Sprachen, und Groß- und Kleinschreibung spielt keine Rolle.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Bildschirme auf Arabisch, Persisch und Urdu werden von rechts nach links aufgebaut. Um arabischen Text im Terminal mit verbundenen Buchstaben anzuzeigen, verwenden Sie den Befehl `rtl`, zum Beispiel `cat note.txt | rtl`.

## 14. Funktionen, die nur Nibras hat

- `ask`, `explain` und `fix` helfen Ihnen bei Befehlen und Fehlern.
- `recipes` und `learn` enthalten fertige Lösungen und Linux-Lektionen.
- `vault` speichert private Dateien verschlüsselt, und `trash` macht das Löschen sicher.
- `quran`, `hadith` und `prayer` (mit Erinnerungen) sind eingebaut.
- `llm` betreibt einen Offline-KI-Chat auf dem Telefon.
- `webterm` öffnet dieses Terminal über WLAN im Browser eines Computers.
- Geteilter Bildschirm, ein schwebendes Fenster und ein Linux-Desktop (XFCE) laufen innerhalb der App.

## 15. Berechtigungen

Nibras Terminal fragt nur dann nach einer Berechtigung, wenn ein Befehl sie benötigt. Befehle für Kamera, Mikrofon, Standort, Kontakte und SMS fragen bei der ersten Verwendung. „Unbekannte Apps installieren“ wird benötigt, um selbst erstellte APK-Dateien zu installieren. „Wecker und Erinnerungen“ wird für Gebetserinnerungen benötigt. Benachrichtigungszugriff, „Systemeinstellungen ändern“ und die Kopplung für kabelloses Debugging (`nibras-adb pair`) sind optional und werden nur von den Befehlen verwendet, die sie erwähnen. Ohne Root nutzt die Windows-Dateifreigabe Port 4445 statt 445, und FUSE-basierte Werkzeuge wie `sshfs` sind nicht verfügbar.

## 16. Fehlerbehebung

- Wenn ein Befehl fehlt, geben Sie ihn trotzdem ein: Nibras schlägt das passende Paket vor.
- Wenn eine Installation abbricht, führen Sie denselben `install`-Befehl erneut aus; der Download wird fortgesetzt und geprüft.
- `nibras-reload-settings` übernimmt Änderungen an den Einstellungsdateien.
- Falls die App unerwartet beendet wird, wird der Fehlerbericht in `crash.txt` gespeichert, und beim nächsten Start wird angeboten, ihn anzuzeigen und zu teilen. Bitte hängen Sie diese Datei an, wenn Sie ein Problem auf GitHub melden.

## 17. Häufige Fragen

**Ist Nibras Terminal kostenlos?** Ja. Es ist Open Source.

**Braucht es Root?** Nein. Alles läuft ohne Root; nur einige Aufgaben auf Systemebene sind eingeschränkt, wie oben beschrieben.

**Warum ist die App so klein?** Sie enthält nur Alpine Linux und die Übersetzungen. Alles andere wird bei Bedarf mit `install` heruntergeladen.

**Läuft sie auf 32-Bit-Telefonen?** Ja. Eine einzige APK unterstützt sowohl arm64- als auch arm32-Geräte.
