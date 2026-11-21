# Nibras Terminal — where a command runs

The phone app and the web terminal share a name. They do not share an engine. A command from one side is not a command on the other.

Sensitive phone commands ask for a yes every time: SMS, call log, contacts, microphone, camera, torch, and placing a call. An Android permission granted earlier is not enough.

## Phone only

`nibras-info` `nibras-battery-status` `nibras-clipboard-get` `nibras-clipboard-set` `nibras-toast` `nibras-vibrate` `nibras-tts-speak` `nibras-notification` `nibras-notification-list` `nibras-notification-remove` `nibras-wake-lock` `nibras-wake-unlock` `nibras-share` `nibras-wifi-connectioninfo` `nibras-torch` `nibras-brightness` `nibras-volume` `nibras-camera-info` `nibras-camera-photo` `nibras-microphone-record` `nibras-location` `nibras-sensor` `nibras-fingerprint` `nibras-download` `nibras-setup-storage` `nibras-open-url` `nibras-media-player` `nibras-contact-list` `nibras-call-log` `nibras-sms-list` `nibras-sms-send` `nibras-telephony-call`

## Web only

`nibras install alpine` `nibras install debian` `nibras install ubuntu` `nibras install python` `nibras install node` `nibras install php` `nibras install git` `nibras install build` `nibras apk` `nibras aab` `nibras sign` `nibras token` `nibras layihe` `nibras serve` `nibras md` `nibras stop` `nibras disk` `nibras zibil` `nibras qaytar` `pkg`

The web shell also has its own `help` list. If `help` does not list a command, it is not a web command.

## Rule

The public APK is published only when `versionName` changes. A later push with the same version does not overwrite the file.
