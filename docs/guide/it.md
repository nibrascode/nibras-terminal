# Nibras Terminal — Guida per l'utente (Italiano)

[← Torna al README](../../README.md) · [Tutte le lingue](../../README.md#-guides-in-your-language)

## 1. Che cos'è Nibras Terminal?

Nibras Terminal è un terminale Linux completo per smartphone Android. Si apre direttamente su un vero terminale, esegue Alpine Linux senza alcuna configurazione e può aggiungere Debian, Ubuntu, Arch, Fedora e Kali quando ti servono. Include anche un editor di codice con supporto ai server di linguaggio, un file manager, un client SSH, strumenti per creare e modificare file APK, strumenti multimediali e numerosi comandi che esistono solo in Nibras. L'app resta leggera, perché gli strumenti pesanti vengono scaricati solo quando li richiedi.

## 2. Installazione e primo avvio

1. Scarica l'APK più recente dalla pagina del progetto su GitHub (Actions → *Android package* → artifact) oppure dal link che lo sviluppatore ti ha inviato.
2. Apri il file e consenti "Installa app sconosciute" quando Android lo chiede.
3. Al primo avvio, Nibras Terminal mostra la scelta della lingua. Ogni lingua è indicata con il proprio nome e la lingua del telefono è già selezionata. Se la lingua del telefono non è disponibile, viene usato l'inglese.
4. L'ambiente Alpine Linux integrato viene preparato automaticamente e il terminale si apre.

Puoi cambiare lingua in qualsiasi momento con il comando `lang` o dalle Impostazioni.

## 3. Nozioni di base

- Scorri dal bordo sinistro dello schermo, oppure tocca `☰` nella riga dei tasti extra, per aprire il menu laterale con sessioni, ambienti, strumenti e impostazioni.
- Tieni premuta una sessione per rinominarla o chiuderla. Tieni premuto "Nuova sessione" per scegliere l'ambiente (Alpine, Debian, Ubuntu o la shell di Android).
- La riga dei tasti extra offre `ESC`, `TAB`, `CTRL`, `ALT`, le frecce e altro ancora. `CTRL` e `ALT` restano premuti fino al tasto successivo.
- Il tasto volume giù funziona come `CTRL` e il tasto volume su funziona come modificatore dei tasti extra.
- Digita `nibras` per aprire il menu di tutti gli strumenti Nibras e `help` per un breve elenco di comandi.

```sh
nibras
help
```

## 4. Installare strumenti con `install`

Gli strumenti non sono contenuti nell'APK. Digita `install` per vedere tutti i pacchetti con una breve descrizione e `install <nome>` per aggiungerne uno. I programmi che non sono pacchetti Nibras si installano con `pkg install <nome>`.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Ambienti Linux (distribuzioni)

Alpine Linux è incluso nell'app. Le altre distribuzioni vengono scaricate su richiesta e ognuna funziona come un ambiente separato.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Linguaggi di programmazione e `run`

Il comando `run` riconosce un file dall'estensione, installa lo strumento necessario la prima volta ed esegue il file. Funziona con Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell e altri. Se gli indichi una cartella, rileva il tipo di progetto (Cargo, moduli Go, npm, Gradle, CMake, Makefile e altri).

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Editor di codice e LSP

Apri un file con `edit <file>` o dal file manager. L'editor evidenzia la sintassi di oltre 25 linguaggi, completa parole chiave e snippet, formatta il codice, mostra l'anteprima di Markdown, HTML e layout Android ed esegue il file con il pulsante ▶.

Per il completamento intelligente, la sottolineatura degli errori, le informazioni al passaggio e il salto alla definizione, installa una volta un server di linguaggio. La barra di stato mostra 🧠 quando il server è attivo.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Tieni premuta una parola nell'editor per vederne la documentazione. Il menu ⋯ contiene "Problemi", "Info al cursore" e "Vai alla definizione".

## 8. File manager

Apri il file manager dal menu laterale o con il comando `files`. Puoi copiare, spostare, rinominare, eliminare, comprimere, estrarre, condividere e aprire file, e puoi accedere alla memoria del telefono dopo aver eseguito `nibras-setup-storage`.

```sh
files
nibras-setup-storage
```

## 9. SSH

La schermata SSH nel menu laterale memorizza host, chiavi, inoltri di porta e jump host. Sono supportati Mosh, Telnet e SFTP. Puoi anche eseguire un server SSH sul telefono.

```sh
ssh user@example.com
nibras-sshd
```

Gli host si esportano e si importano come file `ssh config`, così puoi spostarli tra dispositivi senza un account cloud.

## 10. Strumenti APK ed editor delle risorse

`nibras-apk` compila, smonta, modifica e firma app Android direttamente sul telefono.

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

L'editor visivo delle risorse (`nibras-apk res`) ti permette di modificare i testi di ogni lingua in una tabella, cambiare i colori, sostituire icone e immagini dalla galleria e modificare il nome dell'app, il nome del pacchetto e la versione. Poi un solo tocco ricompila, firma e installa l'app. Le funzioni che aggirano i controlli di licenza o le protezioni delle app altrui non sono state incluse di proposito.

## 11. Video, audio, download e documenti

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

`download` salva video, musica e file da un link. Scarica solo contenuti che hai il diritto di salvare. Il comando `media` apre una schermata visiva per le stesse operazioni.

## 12. Personalizzazione e testo colorato

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

`theme list`, `prompt list`, `font list` e `color list` mostrano tutte le opzioni. `customize` apre un unico menu con tutte le impostazioni e `reset-look` riporta tutto ai valori predefiniti. Le impostazioni sono salvate in `~/.nibras/`.

## 13. Comandi di lingua

Il terminale capisce frasi naturali in diverse lingue e non fa differenza tra maiuscole e minuscole.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Le schermate in arabo, persiano e urdu sono disposte da destra a sinistra. Per mostrare nel terminale un testo arabo con le lettere unite, usa il comando `rtl`, ad esempio `cat note.txt | rtl`.

### Comandi nella tua lingua

Puoi scrivere i comandi di Nibras anche in italiano. I nomi inglesi funzionano sempre, e i comandi Linux standard come `ls`, `git` e `cd` non vengono rinominati. I comandi funzionano con e senza accenti.

| Italiano | Inglese |
|---|---|
| `aiuto` | `help` |
| `installa python` | `install python` |
| `rimuovi` | `remove` |
| `aggiorna` | `update` |
| `esegui hello.py` | `run hello.py` |
| `lingua` | `lang` |
| `tema dracula` | `theme dracula` |
| `colore red Salam` | `color red Salam` |
| `personalizza` | `customize` |
| `remoto` | `remote` |
| `ripristina` | `restore` |
| `impara` | `learn` |
| `correggi` | `fix` |
| `chiedi` | `ask` |
| `spiega` | `explain` |
| `preghiera` | `prayer` |
| `scarica` | `download` |
| `modifica` | `edit` |
| `corano` | `quran` |
<!-- native-commands -->

## 14. Funzioni esclusive di Nibras

- `ask`, `explain` e `fix` ti aiutano con comandi ed errori.
- `recipes` e `learn` offrono soluzioni pronte e lezioni di Linux.
- `vault` conserva i file privati cifrati e `trash` rende sicura l'eliminazione.
- `quran`, `hadith` e `prayer` (con promemoria) sono integrati.
- `llm` esegue una chat IA offline sul telefono.
- `webterm` apre questo terminale nel browser di un computer tramite Wi-Fi.
- Schermo diviso, finestra mobile e un desktop Linux (XFCE) funzionano all'interno dell'app.
- `scripts` apre il negozio di script, dove puoi sfogliare, leggere, installare ed eseguire script pronti all'uso.
- `remote` apre questo terminale nel browser di un computer collegato alla stessa rete Wi-Fi. Il link monouso viene mostrato anche come codice QR.
- `backup drive` salva host e impostazioni, cifrati con la tua passphrase, su Google Drive o in qualsiasi cartella. `restore drive` li ripristina.
- `learn` avvia lezioni interattive: digiti comandi veri e vengono verificati automaticamente.
- I widget della schermata Home eseguono i tuoi comandi preferiti con un tocco e mostrano lo stato dei server e gli orari della preghiera.

## 15. Autorizzazioni

Nibras Terminal chiede un'autorizzazione solo quando un comando ne ha bisogno. I comandi per fotocamera, microfono, posizione, contatti e SMS la chiedono al primo utilizzo. "Installa app sconosciute" serve per installare gli APK che crei. "Sveglie e promemoria" serve per i promemoria della preghiera. L'accesso alle notifiche, "Modifica impostazioni di sistema" e l'associazione del debug wireless (`nibras-adb pair`) sono facoltativi e vengono usati solo dai comandi che li menzionano. Senza root, la condivisione file di Windows usa la porta 4445 invece della 445 e gli strumenti basati su FUSE come `sshfs` non sono disponibili.

## 16. Risoluzione dei problemi

- Se un comando manca, digitalo comunque: Nibras ti suggerirà il pacchetto giusto.
- Se un'installazione si interrompe, esegui di nuovo lo stesso comando `install`; il download riprende e viene verificato.
- `nibras-reload-settings` applica le modifiche apportate ai file di impostazione.
- Se l'app si chiude inaspettatamente, il rapporto di errore viene salvato in `crash.txt` e all'avvio successivo ti viene proposto di visualizzarlo e condividerlo. Allega questo file quando segnali un problema su GitHub.

## 17. Domande frequenti

**Nibras Terminal è gratuito?** Sì. È open source.

**Serve il root?** No. Tutto funziona senza root; solo alcune operazioni a livello di sistema sono limitate, come descritto sopra.

**Perché l'app è così leggera?** Contiene solo Alpine Linux e le traduzioni. Tutto il resto si scarica con `install` quando serve.

**Funziona sui telefoni a 32 bit?** Sì. Un solo APK supporta sia i dispositivi arm64 sia quelli arm32.
