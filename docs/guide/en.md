# Nibras Terminal — User Guide (English)

[← Back to the README](../../README.md) · [All languages](../../README.md#-guides-in-your-language)

> The phone app and the web terminal are different. Camera, microphone, SMS, contacts and the call log work on the phone only, and each of those commands asks for a yes every time. The web terminal cannot run them. List: [Commands](../COMMANDS.md).


## 1. What is Nibras Terminal?

Nibras Terminal is a full Linux terminal for Android phones. It opens straight into a real terminal, runs Alpine Linux out of the box, and can add Debian, Ubuntu, Arch, Fedora and Kali whenever you need them. It also includes a code editor with language-server support, a file manager, an SSH client, tools for building and modifying APK files, media tools and a large set of commands that only Nibras has. The app itself stays small, because heavy tools are downloaded only when you ask for them.

## 2. Installation and first launch

1. Download the APK directly: https://github.com/nibrascode/nibras-terminal/releases/latest/download/nibras-terminal.apk
Camera, microphone, SMS, contacts and location are asked only when you run that command, not when the app opens.
Automatic updates (Obtainium): https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/nibrascode/nibras-terminal
2. Open the file and allow "Install unknown apps" when Android asks for it.
3. On the first launch, Nibras Terminal shows a language picker. Every language is listed by its own name, and your phone's language is already selected. English is used when your phone's language is not available.
4. The built-in Alpine Linux environment is prepared automatically, and the terminal opens.

You can change the language at any time with the `lang` command or in Settings.

## 3. Basics

- Swipe from the left edge of the screen, or tap `☰` on the extra-keys row, to open the drawer with sessions, environments, tools and settings.
- Long-press a session to rename or close it. Long-press "New session" to choose the environment (Alpine, Debian, Ubuntu or the Android shell).
- The extra-keys row gives you `ESC`, `TAB`, `CTRL`, `ALT`, the arrow keys and more. `CTRL` and `ALT` stay pressed until the next key.
- The volume-down key works as `CTRL`, and the volume-up key works as the extra-keys modifier.
- Type `nibras` to open the menu of all Nibras tools, and type `help` for a short list of commands.

```sh
nibras
help
```

## 4. Installing tools with `install`

Tools are not stored inside the APK. Type `install` to see every pack with a short description, and type `install <name>` to add one. Packages that are not Nibras packs are installed with `pkg install <name>`.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Linux environments (distributions)

Alpine Linux is included in the app. Other distributions are downloaded on request, and each one runs as a separate environment.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Programming languages and `run`

The `run` command recognizes a file by its extension, installs the required tool the first time, and runs the file. It works for Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell and more. When you give it a folder, it detects the project type (Cargo, Go modules, npm, Gradle, CMake, Makefile and others).

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Code editor and LSP

Open a file with `edit <file>` or from the file manager. The editor highlights syntax for more than 25 languages, completes keywords and snippets, formats code, previews Markdown, HTML and Android layouts, and runs the file with the ▶ button.

For smart completion, error underlining, hover information and go-to-definition, install a language server once. The status bar shows 🧠 when the server is running.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Long-press a word in the editor to see its documentation. The ⋯ menu contains "Problems", "Info at cursor" and "Go to definition".

## 8. File manager

Open the file manager from the drawer or with the `files` command. You can copy, move, rename, delete, compress, extract, share and open files, and you can reach the phone storage after you run `nibras-setup-storage`.

```sh
files
nibras-setup-storage
```

## 9. SSH

The SSH screen in the drawer stores your hosts, keys, port forwards and jump hosts. Mosh, Telnet and SFTP are supported. You can also run an SSH server on the phone.

```sh
ssh user@example.com
nibras-sshd
```

Hosts are exported and imported as an `ssh config` file, so you can move them between devices without a cloud account.

## 10. APK tools and the resource editor

`nibras-apk` builds, takes apart, edits and signs Android apps on the phone.

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
nibras-apk test MyApp
nibras-apk lint MyApp
nibras-apk analyze app.apk
nibras-apk assets icon.png MyApp
nibras-apk perms MyApp +CAMERA
nibras-apk db com.example.myapp
nibras-apk crash com.example.myapp
nibras-apk studio MyApp
nibras-apk activity Details MyApp
nibras-apk screen list items MyApp
nibras-apk dep androidx.recyclerview:recyclerview:1.4.0 MyApp
```

The visual resource editor (`nibras-apk res`) lets you edit strings for every language in a table, change colors, replace icons and pictures from the gallery, and change the app name, package name and version. One tap then rebuilds, signs and installs the app. `nibras-apk studio` is the project screen: files, layout preview, run and new activities. Run installs on this phone. There is no emulator. `perms` edits permissions in your project. `db` and `crash` read another app only when it is a debug build you installed and wireless debugging is paired. Features that break other people's license checks or protection are intentionally not included.

## 11. Video, audio, downloads and documents

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

`download` saves videos, music and files from a link. Please download only content that you have the right to save. The `media` command opens a visual screen for the same tasks.

## 12. Customization and colored text

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

`theme list`, `prompt list`, `font list` and `color list` show every option. `customize` opens one menu with all settings, and `reset-look` returns everything to the defaults. Settings are saved in `~/.nibras/`.

## 13. Language commands

The terminal understands natural phrases in several languages, and capital letters do not matter.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Arabic, Persian and Urdu screens are laid out from right to left. To show shaped Arabic text inside the terminal, use the `rtl` command, for example `cat note.txt | rtl`.
<!-- native-commands -->

## 14. Features only Nibras has

- `ask`, `explain` and `fix` help you with commands and errors.
- `recipes` and `learn` contain ready-made solutions and Linux lessons.
- `vault` keeps private files encrypted, and `trash` makes deleting safe.
- `quran`, `hadith` and `prayer` (with reminders) are built in.
- `llm` runs an offline AI chat on the phone.
- `webterm` opens this terminal in a computer's browser over Wi-Fi.
- Split screen, a floating window and a Linux desktop (XFCE) work inside the app.
- `scripts` opens the script store, where you can browse, read, install and run ready-made scripts.
- `remote` opens this terminal in a computer browser on the same Wi-Fi. The one-time link is also shown as a QR code.
- `backup drive` saves your hosts and settings, encrypted with your own passphrase, to Google Drive or any folder. `restore drive` brings them back.
- `learn` starts interactive lessons: you type real commands and they are checked automatically.
- Home-screen widgets run your favorite commands with one tap and show server status and prayer times.

## 15. Permissions

Nibras Terminal asks for a permission only when a command needs it. Camera, microphone, location, contacts and SMS commands ask the first time you use them. "Install unknown apps" is needed to install APK files you build. "Alarms & reminders" is needed for prayer reminders. Notification access, "Modify system settings" and wireless debugging pairing (`nibras-adb pair`) are optional and are used only by the commands that mention them. Without root, Windows file sharing uses port 4445 instead of 445, and FUSE-based tools such as `sshfs` are not available.

## 16. Troubleshooting

- If a command is missing, type it anyway: Nibras suggests the right pack or package.
- If an installation stops, run the same `install` command again; downloads resume and are checked.
- `nibras-reload-settings` applies changes made to the settings files.
- If the app ever closes unexpectedly, the error report is saved in `crash.txt`, and the next launch offers to show and share it. Please attach this file when you report a problem on GitHub.

## 17. Frequently asked questions

**Is Nibras Terminal free?** Yes. It is open source.

**Does it need root?** No. Everything runs without root; only a few system-level tasks are limited, as described above.

**Why is the app so small?** Only Alpine Linux and the translations are included. Everything else is downloaded with `install` when you need it.

**Does it work on 32-bit phones?** Yes. One APK supports both arm64 and arm32 devices.

## 18. Additions in 1.0.4

`nibras-man` explains each one in your language. Short list: `nibras-update` (checks the release signature), `nibras-apk diff`, `nibras-allow`, `nibras-guest`, `nibras-secret`, `record`, a `.nibras/keys` file in a project, `nibras-job`, `nibras-peer`, `nibras-qr-run`, `nibras-mirror`, `nibras-setup`, `nibras-import-termux`, `nibras-phrase`, `nibras-ports`. Git asks the phone for a password and does not print it. The first screen of the website is unchanged.

## 19. Short commands in 1.0.5

Type `use`, `here`, `jump`, `hist`, `check` or `health` instead of a long name. `nibras-work help` prints the full list. `test`, `env`, `sync`, `zip` and `fmt` stay the real system commands. `font` still changes the look; `nibras-work font file.ttf` copies a font file. `jump`, `goto`, `clone`, `dotenv`, `again` and `palette` change the current shell, so they are functions. `incognito` does not restore that session and does not write its history. `snip` runs a saved line only when it is on your allow list and you say yes. `sayit` and `scan` run only a sentence the app already understands. A project button and a risky multiline paste ask before they are sent. The first screen of the website is unchanged.
