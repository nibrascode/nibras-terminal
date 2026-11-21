# Nibras Terminal — Guide d'utilisation (Français)

[← Retour au README](../../README.md) · [Toutes les langues](../../README.md#-guides-in-your-language)

## 1. Qu'est-ce que Nibras Terminal ?

Nibras Terminal est un terminal Linux complet pour les téléphones Android. Il s'ouvre directement sur un vrai terminal, fait tourner Alpine Linux dès l'installation et peut ajouter Debian, Ubuntu, Arch, Fedora et Kali dès que vous en avez besoin. Il comprend aussi un éditeur de code avec prise en charge des serveurs de langage, un gestionnaire de fichiers, un client SSH, des outils pour créer et modifier des fichiers APK, des outils multimédias et un grand nombre de commandes propres à Nibras. L'application reste légère, car les outils volumineux ne sont téléchargés que lorsque vous les demandez.

## 2. Installation et premier lancement

1. Téléchargez le dernier APK depuis la page du projet sur GitHub (Actions → *Android package* → artifact) ou depuis le lien que le développeur vous a envoyé.
2. Ouvrez le fichier et autorisez « Installer des applis inconnues » lorsque Android le demande.
3. Au premier lancement, Nibras Terminal affiche un sélecteur de langue. Chaque langue est indiquée sous son propre nom, et la langue de votre téléphone est déjà sélectionnée. Si la langue de votre téléphone n'est pas disponible, l'anglais est utilisé.
4. L'environnement Alpine Linux intégré est préparé automatiquement, puis le terminal s'ouvre.

Vous pouvez changer de langue à tout moment avec la commande `lang` ou dans les Paramètres.

## 3. Les bases

- Balayez depuis le bord gauche de l'écran, ou touchez `☰` dans la rangée de touches supplémentaires, pour ouvrir le menu latéral avec les sessions, les environnements, les outils et les paramètres.
- Appuyez longuement sur une session pour la renommer ou la fermer. Appuyez longuement sur « Nouvelle session » pour choisir l'environnement (Alpine, Debian, Ubuntu ou le shell Android).
- La rangée de touches supplémentaires propose `ESC`, `TAB`, `CTRL`, `ALT`, les flèches et d'autres touches. `CTRL` et `ALT` restent enfoncées jusqu'à la touche suivante.
- La touche de baisse du volume fait office de `CTRL` et la touche de hausse du volume sert de modificateur pour les touches supplémentaires.
- Tapez `nibras` pour ouvrir le menu de tous les outils Nibras, et `help` pour afficher une courte liste de commandes.

```sh
nibras
help
```

## 4. Installer des outils avec `install`

Les outils ne sont pas stockés dans l'APK. Tapez `install` pour voir tous les packs avec une courte description, puis `install <nom>` pour en ajouter un. Les logiciels qui ne sont pas des packs Nibras s'installent avec `pkg install <nom>`.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Environnements Linux (distributions)

Alpine Linux est inclus dans l'application. Les autres distributions sont téléchargées à la demande, et chacune fonctionne comme un environnement séparé.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Langages de programmation et `run`

La commande `run` reconnaît un fichier à son extension, installe l'outil nécessaire la première fois, puis exécute le fichier. Elle fonctionne avec Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell et d'autres. Si vous lui donnez un dossier, elle détecte le type de projet (Cargo, modules Go, npm, Gradle, CMake, Makefile, etc.).

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Éditeur de code et LSP

Ouvrez un fichier avec `edit <fichier>` ou depuis le gestionnaire de fichiers. L'éditeur colore la syntaxe de plus de 25 langages, complète les mots-clés et les extraits de code, formate le code, prévisualise le Markdown, le HTML et les mises en page Android, et exécute le fichier avec le bouton ▶.

Pour la complétion intelligente, le soulignement des erreurs, les infobulles et l'accès à la définition, installez une fois un serveur de langage. La barre d'état affiche 🧠 lorsque le serveur est actif.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Appuyez longuement sur un mot dans l'éditeur pour voir sa documentation. Le menu ⋯ contient « Problèmes », « Infos au curseur » et « Aller à la définition ».

## 8. Gestionnaire de fichiers

Ouvrez le gestionnaire de fichiers depuis le menu latéral ou avec la commande `files`. Vous pouvez copier, déplacer, renommer, supprimer, compresser, extraire, partager et ouvrir des fichiers, et vous accédez au stockage du téléphone après avoir lancé `nibras-setup-storage`.

```sh
files
nibras-setup-storage
```

## 9. SSH

L'écran SSH du menu latéral enregistre vos hôtes, vos clés, vos redirections de ports et vos hôtes de rebond. Mosh, Telnet et SFTP sont pris en charge. Vous pouvez aussi faire tourner un serveur SSH sur le téléphone.

```sh
ssh user@example.com
nibras-sshd
```

Les hôtes s'exportent et s'importent sous la forme d'un fichier `ssh config`, ce qui permet de les transférer d'un appareil à l'autre sans compte cloud.

## 10. Outils APK et éditeur de ressources

`nibras-apk` compile, décompose, modifie et signe des applications Android directement sur le téléphone.

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

L'éditeur visuel de ressources (`nibras-apk res`) vous permet de modifier les textes de chaque langue dans un tableau, de changer les couleurs, de remplacer les icônes et les images depuis la galerie, et de modifier le nom de l'application, le nom du paquet et la version. Une seule touche recompile, signe et installe ensuite l'application. Les fonctions qui contournent les vérifications de licence ou les protections des applications d'autrui ne sont volontairement pas incluses.

## 11. Vidéo, audio, téléchargements et documents

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

`download` enregistre des vidéos, de la musique et des fichiers à partir d'un lien. Ne téléchargez que des contenus que vous avez le droit d'enregistrer. La commande `media` ouvre un écran visuel pour les mêmes tâches.

## 12. Personnalisation et texte en couleur

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

`theme list`, `prompt list`, `font list` et `color list` affichent toutes les options. `customize` ouvre un menu regroupant tous les réglages, et `reset-look` rétablit les valeurs par défaut. Les réglages sont enregistrés dans `~/.nibras/`.

## 13. Commandes de langue

Le terminal comprend des phrases naturelles dans plusieurs langues, sans tenir compte des majuscules.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Les écrans en arabe, en persan et en ourdou s'affichent de droite à gauche. Pour afficher dans le terminal un texte arabe aux lettres liées, utilisez la commande `rtl`, par exemple `cat note.txt | rtl`.

## 14. Fonctions propres à Nibras

- `ask`, `explain` et `fix` vous aident avec les commandes et les erreurs.
- `recipes` et `learn` proposent des solutions toutes prêtes et des leçons de Linux.
- `vault` conserve les fichiers privés chiffrés, et `trash` rend la suppression sûre.
- `quran`, `hadith` et `prayer` (avec rappels) sont intégrés.
- `llm` fait tourner un assistant IA hors ligne sur le téléphone.
- `webterm` ouvre ce terminal dans le navigateur d'un ordinateur via le Wi-Fi.
- L'écran partagé, une fenêtre flottante et un bureau Linux (XFCE) fonctionnent dans l'application.

## 15. Autorisations

Nibras Terminal ne demande une autorisation que lorsqu'une commande en a besoin. Les commandes liées à l'appareil photo, au micro, à la localisation, aux contacts et aux SMS la demandent lors de leur première utilisation. « Installer des applis inconnues » est nécessaire pour installer les APK que vous créez. « Alarmes et rappels » est nécessaire pour les rappels de prière. L'accès aux notifications, « Modifier les paramètres système » et l'association du débogage sans fil (`nibras-adb pair`) sont facultatifs et ne servent qu'aux commandes qui les mentionnent. Sans root, le partage de fichiers Windows utilise le port 4445 au lieu du 445, et les outils basés sur FUSE comme `sshfs` ne sont pas disponibles.

## 16. Dépannage

- Si une commande est introuvable, tapez-la quand même : Nibras vous propose le bon pack ou paquet.
- Si une installation s'interrompt, relancez la même commande `install` ; le téléchargement reprend et il est vérifié.
- `nibras-reload-settings` applique les modifications apportées aux fichiers de réglages.
- Si l'application se ferme de manière inattendue, le rapport d'erreur est enregistré dans `crash.txt`, et le lancement suivant propose de l'afficher et de le partager. Joignez ce fichier lorsque vous signalez un problème sur GitHub.

## 17. Questions fréquentes

**Nibras Terminal est-il gratuit ?** Oui. Il est open source.

**Faut-il le root ?** Non. Tout fonctionne sans root ; seules quelques tâches au niveau du système sont limitées, comme décrit ci-dessus.

**Pourquoi l'application est-elle si légère ?** Elle ne contient qu'Alpine Linux et les traductions. Tout le reste se télécharge avec `install` quand vous en avez besoin.

**Fonctionne-t-elle sur les téléphones 32 bits ?** Oui. Un seul APK prend en charge les appareils arm64 et arm32.
