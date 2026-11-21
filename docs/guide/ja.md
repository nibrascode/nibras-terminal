# Nibras Terminal — ユーザーガイド（日本語）

[← README に戻る](../../README.md) · [すべての言語](../../README.md#-guides-in-your-language)

## 1. Nibras Terminal とは？

Nibras Terminal は、Android スマートフォン向けの本格的な Linux ターミナルです。起動するとすぐに本物のターミナルが開き、Alpine Linux が設定なしで動作します。必要になれば Debian、Ubuntu、Arch、Fedora、Kali をいつでも追加できます。さらに、言語サーバーに対応したコードエディター、ファイルマネージャー、SSH クライアント、APK ファイルを作成・編集するツール、メディアツール、そして Nibras にしかない多数のコマンドを備えています。大きなツールは必要なときにだけダウンロードされるため、アプリ本体は小さいままです。

## 2. インストールと初回起動

1. GitHub のプロジェクトページ（Actions → *Android package* → artifact）、または開発者から共有されたリンクから最新の APK をダウンロードします。
2. ファイルを開き、Android に求められたら「不明なアプリのインストール」を許可します。
3. 初回起動時に、Nibras Terminal は言語選択画面を表示します。各言語はその言語自身の名前で表示され、スマートフォンの言語があらかじめ選択されています。スマートフォンの言語がない場合は英語が使われます。
4. 内蔵の Alpine Linux 環境が自動的に準備され、ターミナルが開きます。

言語は `lang` コマンドまたは設定画面からいつでも変更できます。

## 3. 基本操作

- 画面の左端からスワイプするか、追加キーの列にある `☰` をタップすると、セッション、環境、ツール、設定を含むサイドメニューが開きます。
- セッションを長押しすると、名前の変更や終了ができます。「新しいセッション」を長押しすると、環境（Alpine、Debian、Ubuntu、Android シェル）を選べます。
- 追加キーの列には `ESC`、`TAB`、`CTRL`、`ALT`、矢印キーなどがあります。`CTRL` と `ALT` は次のキーを押すまで押された状態のままです。
- Termux と同じく、音量ダウンキーは `CTRL` として、音量アップキーは追加キーの修飾キーとして機能します。
- `nibras` と入力するとすべての Nibras ツールのメニューが開き、`help` と入力すると主なコマンドの一覧が表示されます。

```sh
nibras
help
```

## 4. `install` でツールをインストールする

ツールは APK の中には含まれていません。`install` と入力すると、すべてのパックが簡単な説明付きで表示されます。追加するには `install <名前>` と入力します。Nibras のパックではないソフトウェアは `pkg install <名前>` でインストールします。

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Linux 環境（ディストリビューション）

Alpine Linux はアプリに含まれています。ほかのディストリビューションは必要に応じてダウンロードされ、それぞれが独立した環境として動作します。

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. プログラミング言語と `run`

`run` コマンドは拡張子からファイルの種類を判別し、初回は必要なツールを自動でインストールしてからファイルを実行します。Python、JavaScript、TypeScript、Java、Kotlin、C、C++、Go、Rust、PHP、Ruby、Perl、Lua、Dart、R、Julia、Haskell、Elixir、Zig、Nim、Bash、PowerShell などに対応しています。フォルダーを指定すると、プロジェクトの種類（Cargo、Go モジュール、npm、Gradle、CMake、Makefile など）を自動で判別します。

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. コードエディターと LSP

`edit <ファイル>` またはファイルマネージャーからファイルを開きます。エディターは 25 以上の言語の構文を色分けし、キーワードやスニペットを補完し、コードを整形し、Markdown、HTML、Android レイアウトをプレビューし、▶ ボタンでファイルを実行します。

賢い補完、エラーの下線表示、ホバー情報、定義への移動を使うには、言語サーバーを一度インストールしてください。サーバーが動作しているとき、ステータスバーに 🧠 が表示されます。

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

エディターで単語を長押しすると、そのドキュメントが表示されます。⋯ メニューには「問題」「カーソル位置の情報」「定義へ移動」があります。

## 8. ファイルマネージャー

サイドメニューまたは `files` コマンドでファイルマネージャーを開きます。ファイルのコピー、移動、名前の変更、削除、圧縮、展開、共有、オープンができ、`nibras-setup-storage` を実行するとスマートフォンのストレージにもアクセスできます。

```sh
files
nibras-setup-storage
```

## 9. SSH

サイドメニューの SSH 画面には、ホスト、鍵、ポートフォワード、踏み台ホストが保存されます。Mosh、Telnet、SFTP に対応しています。スマートフォン上で SSH サーバーを動かすこともできます。

```sh
ssh user@example.com
nibras-sshd
```

ホストは `ssh config` ファイルとしてエクスポート・インポートできるため、クラウドアカウントなしで端末間を移行できます。

## 10. APK ツールとリソースエディター

`nibras-apk` は、スマートフォン上で Android アプリのビルド、分解、編集、署名を行います。

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

ビジュアルリソースエディター（`nibras-apk res`）では、各言語の文字列を表で編集し、色を変更し、ギャラリーからアイコンや画像を差し替え、アプリ名、パッケージ名、バージョンを変更できます。その後ワンタップで、アプリの再ビルド、署名、インストールが行われます。他人のアプリのライセンス確認や保護を破る機能は、意図的に含めていません。

## 11. 動画、音声、ダウンロード、ドキュメント

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

`download` はリンクから動画、音楽、ファイルを保存します。保存する権利のあるコンテンツだけをダウンロードしてください。`media` コマンドを使うと、同じ作業を画面操作で行えます。

## 12. カスタマイズとカラーテキスト

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

`theme list`、`prompt list`、`font list`、`color list` ですべての選択肢を確認できます。`customize` はすべての設定をまとめたメニューを開き、`reset-look` はすべてを初期状態に戻します。設定は `~/.nibras/` に保存され、Termux の `termux.properties` と互換性があります。

## 13. 言語コマンド

ターミナルは複数の言語の自然なフレーズを理解し、大文字と小文字は区別しません。

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

アラビア語、ペルシア語、ウルドゥー語の画面は右から左に表示されます。ターミナル内でアラビア文字をつながった形で表示するには、`rtl` コマンドを使います。例：`cat note.txt | rtl`

## 14. Nibras だけの機能

- `ask`、`explain`、`fix` がコマンドやエラーについて手助けします。
- `recipes` と `learn` には、すぐに使える解決策と Linux のレッスンが入っています。
- `vault` は個人ファイルを暗号化して保管し、`trash` は削除を安全にします。
- `quran`、`hadith`、`prayer`（リマインダー付き）が内蔵されています。
- `llm` はスマートフォン上でオフラインの AI チャットを実行します。
- `webterm` は Wi-Fi 経由でパソコンのブラウザーにこのターミナルを開きます。
- 画面分割、フローティングウィンドウ、Linux デスクトップ（XFCE）がアプリ内で動作します。

## 15. 権限

Nibras Terminal は、コマンドが必要とするときにだけ権限を求めます。カメラ、マイク、位置情報、連絡先、SMS のコマンドは、初めて使うときに許可を求めます。自分でビルドした APK をインストールするには「不明なアプリのインストール」が必要です。礼拝のリマインダーには「アラームとリマインダー」が必要です。通知へのアクセス、「システム設定の変更」、ワイヤレスデバッグのペア設定（`nibras-adb pair`）は任意で、それらに言及しているコマンドだけが使用します。root がない場合、Windows のファイル共有は 445 番ではなく 4445 番ポートを使い、`sshfs` などの FUSE ベースのツールは使用できません。

## 16. トラブルシューティング

- コマンドが見つからなくても、そのまま入力してください。Nibras が適切なパックを提案します。
- インストールが途中で止まった場合は、同じ `install` コマンドをもう一度実行してください。ダウンロードは途中から再開され、検証されます。
- `nibras-reload-settings` は設定ファイルへの変更を反映します。
- アプリが予期せず終了した場合、エラーレポートが `crash.txt` に保存され、次回の起動時に表示と共有を提案されます。GitHub で問題を報告するときは、このファイルを添付してください。

## 17. よくある質問

**Nibras Terminal は無料ですか？** はい。オープンソースです。

**root は必要ですか？** いいえ。すべて root なしで動作します。上で説明したとおり、制限されるのはシステムレベルの一部の作業だけです。

**なぜアプリがこんなに小さいのですか？** 含まれているのは Alpine Linux と翻訳だけです。それ以外はすべて、必要なときに `install` でダウンロードされます。

**32 ビットのスマートフォンでも動きますか？** はい。1 つの APK で arm64 と arm32 の両方の端末に対応しています。

**Termux の設定を使えますか？** はい。Nibras は `~/.termux/termux.properties`、`colors.properties`、`font.ttf` を読み込み、`termux-*` のコマンド名も認識します。
