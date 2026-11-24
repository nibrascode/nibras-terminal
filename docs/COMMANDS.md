# Nibras Terminal — where a command runs

The phone app and the web terminal share a name. They do not share an engine. A command from one side is not a command on the other.

Sensitive phone commands ask for a yes every time: SMS, call log, contacts, microphone, camera, torch, placing a call, location, the clipboard and notifications. An Android permission granted earlier is not enough. No answer within 30 seconds means no. SMS and calls show the number. The web terminal cannot run them.

## Phone only

`nibras-info` `nibras-battery-status` `nibras-clipboard-get` `nibras-clipboard-set` `nibras-toast` `nibras-vibrate` `nibras-tts-speak` `nibras-notification` `nibras-notification-list` `nibras-notification-remove` `nibras-wake-lock` `nibras-wake-unlock` `nibras-share` `nibras-wifi-connectioninfo` `nibras-torch` `nibras-brightness` `nibras-volume` `nibras-camera-info` `nibras-camera-photo` `nibras-microphone-record` `nibras-location` `nibras-sensor` `nibras-fingerprint` `nibras-download` `nibras-setup-storage` `nibras-open-url` `nibras-media-player` `nibras-contact-list` `nibras-call-log` `nibras-sms-list` `nibras-sms-send` `nibras-telephony-call`


## Phone, 1.0.0 (store)

`pkg install nibras-show -y && nibras-show`

This is the Nibras Terminal demo command. `pkg install nibras-show` installs a bundled pack with no network and a short colorful progress display, then `nibras-show` plays a 10-15 second color animation (blue-to-green banner, matrix rain, boot lines, and a system info card). Any key or Ctrl-C returns to the prompt early. `nibras show` is the same command after the pack is installed. No extra packages are required.

## Web only

`nibras install alpine` `nibras install debian` `nibras install ubuntu` `nibras install python` `nibras install node` `nibras install php` `nibras install git` `nibras install build` `nibras apk` `nibras aab` `nibras sign` `nibras token` `nibras layihe` `nibras serve` `nibras md` `nibras stop` `nibras disk` `nibras zibil` `nibras qaytar` `pkg`

The web shell also has its own `help` list. If `help` does not list a command, it is not a web command.

## Rule

The public APK is published only when `versionName` changes. A later push with the same version does not overwrite the file.

## Phone, 1.0.4

`nibras-update` `nibras-apk diff` `nibras-allow` `nibras-guest` `nibras-secret` `record` `nibras-job` `nibras-peer` `nibras-qr-run` `nibras-mirror` `nibras-setup` `nibras-import-termux` `nibras-phrase` `nibras-man` `nibras-ports`

`nibras-update` refuses an APK whose certificate is not the release key. `nibras-allow` limits widgets, cron, Tasker and boot. Private phone commands cannot be listed. `nibras-guest on` blocks those commands plus the vault and stored keys. `nibras-phrase` runs a sentence only when every part is a known command. `nibras-qr-run` asks before it runs, and only for a known sentence or an allowed command. `nibras-setup` does not contain SSH keys or passwords. Git asks `nibras-git-ask`; the password is not written in the terminal. A folder can carry `.nibras/keys` for its own buttons. `nibras-man <name>` is offline in 19 languages.

## Phone, 1.0.7

`nibras-apk studio` `nibras-apk activity` `nibras-apk class` `nibras-apk screen` `nibras-apk dep` `nibras-apk sync` `nibras-apk device` `nibras-apk errors`

`studio` opens the project screen: run, build, test, lint, logcat, layout preview, resources, and the source list. Find, usages, TODO and structure open the editor on the matching line. Problems does the same for the last Gradle failure. `nibras-apk find`, `usages`, `todo` and `outline` are the same search from the terminal. `activity Name` adds an activity, a layout and a manifest entry. `class Name` adds a Java or Kotlin file. `screen button|list|form|empty name` writes a layout. `dep group:artifact:version` adds `implementation`. The run device is this phone. There is no emulator inside the phone. The website's first screen is unchanged.

## Phone, 1.0.6

`nibras-apk test` `nibras-apk lint` `nibras-apk analyze` `nibras-apk assets` `nibras-apk perms` `nibras-apk db` `nibras-apk crash`

`test` runs `testDebugUnitTest`. `lint` runs `lintDebug`. `analyze` splits an APK into dex, resources, native code and assets. `assets` turns one picture into launcher icons. `perms` adds or removes a `uses-permission`, then rebuilds only if you pass `--run` or answer yes. `nibras-apk manifest <apk>` still prints XML; `nibras-apk manifest edit` is the permission list. `db` opens a SQLite file you already have. Another app's database opens only when that app is your debug build and wireless debugging is paired (`nibras-adb pair`). `crash` reads the crash buffer the same way. The website's first screen is unchanged.

## Phone, 1.0.5

`use` `here` `jump` `mark` `goto` `title` `layout` `palette` `hist` `again` `snip` `todo` `dotenv` `pretty` `check` `alert` `disk` `large` `clean` `pack` `alike` `open` `pick` `saf` `opacity` `quiet` `health` `crash` `hostfp` `sayit` `clone` `gdiff` `cmsg` `gpush` `apkbuild` `signapk` `installapk` `logmine` `pyfile` `runweb` `panes` `lesson` `moveto` `homebak` `homerestore` `daily` `pingdown` `sendtext` `scan` `shrinkpic` `trimaudio` `voicenote` `themehere` `links` `abbr` `hold` `qs` `incognito` `jobtest`

`nibras-work help` lists them. `test`, `env`, `sync`, `zip` and `fmt` are not short names. `font` stays the look command; `nibras-work font file.ttf` installs a font file. `jump`, `goto`, `dotenv`, `again`, `clone`, `palette` and `incognito` are shell functions. `snip` runs only a saved line that is on the allow list, and only after yes. `sayit` and `scan` run only a known sentence. Guest mode and the allow list still do not block a command typed in the terminal. The website's first screen is unchanged.
