# Nibras Terminal — 使用指南（简体中文）

[← 返回 README](../../README.md) · [所有语言](../../README.md#-guides-in-your-language)

## 1. 什么是 Nibras Terminal？

Nibras Terminal 是一款适用于 Android 手机的完整 Linux 终端。应用打开后直接进入真正的终端，内置的 Alpine Linux 无需任何设置即可使用，还可以随时添加 Debian、Ubuntu、Arch、Fedora 和 Kali。它还包含支持语言服务器的代码编辑器、文件管理器、SSH 客户端、构建和修改 APK 文件的工具、多媒体工具，以及大量 Nibras 独有的命令。由于大型工具只会在你需要时下载，应用本身保持小巧。

## 2. 安装与首次启动

1. 直接下载 APK：https://github.com/nibrascode/nibras-terminal/releases/latest/download/nibras-terminal.apk
相机、麦克风、短信、通讯录和位置只在运行该命令时询问，打开应用时不询问。
自动更新（Obtainium）：https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/nibrascode/nibras-terminal
2. 打开文件，并在 Android 提示时允许“安装未知应用”。
3. 首次启动时，Nibras Terminal 会显示语言选择界面。每种语言都以其本身的名称列出，并已预先选中手机的语言。如果没有你手机所用的语言，则使用英语。
4. 内置的 Alpine Linux 环境会自动准备完成，随后打开终端。

你可以随时使用 `lang` 命令或在“设置”中更改语言。

## 3. 基本操作

- 从屏幕左边缘滑动，或点按额外按键栏中的 `☰`，即可打开包含会话、环境、工具和设置的侧边菜单。
- 长按某个会话可以重命名或关闭它。长按“新建会话”可以选择环境（Alpine、Debian、Ubuntu 或 Android shell）。
- 额外按键栏提供 `ESC`、`TAB`、`CTRL`、`ALT`、方向键等按键。`CTRL` 和 `ALT` 会保持按下状态，直到你按下一个键。
- 音量减键相当于 `CTRL`，音量加键用作额外按键的修饰键。
- 输入 `nibras` 打开所有 Nibras 工具的菜单，输入 `help` 查看简短的命令列表。

```sh
nibras
help
```

## 4. 使用 `install` 安装工具

工具并不存放在 APK 中。输入 `install` 可查看所有工具包及其简要说明，输入 `install <名称>` 即可添加其中一个。不属于 Nibras 工具包的软件可通过 `pkg install <名称>` 安装。

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Linux 环境（发行版）

Alpine Linux 已内置在应用中。其他发行版会在你需要时下载，每个发行版都作为独立的环境运行。

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. 编程语言与 `run`

`run` 命令根据文件扩展名识别文件类型，首次运行时会自动安装所需工具，然后运行该文件。它支持 Python、JavaScript、TypeScript、Java、Kotlin、C、C++、Go、Rust、PHP、Ruby、Perl、Lua、Dart、R、Julia、Haskell、Elixir、Zig、Nim、Bash、PowerShell 等语言。如果指定一个文件夹，它会自动识别项目类型（Cargo、Go modules、npm、Gradle、CMake、Makefile 等）。

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. 代码编辑器与 LSP

使用 `edit <文件>` 或通过文件管理器打开文件。编辑器支持 25 种以上语言的语法高亮，可补全关键字和代码片段、格式化代码、预览 Markdown、HTML 和 Android 布局，并可通过 ▶ 按钮运行文件。

要获得智能补全、错误下划线、悬停信息和跳转到定义功能，只需安装一次语言服务器。服务器运行时，状态栏会显示 🧠。

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

在编辑器中长按某个词即可查看其文档。⋯ 菜单中包含“问题”、“光标处信息”和“跳转到定义”。

## 8. 文件管理器

从侧边菜单或使用 `files` 命令打开文件管理器。你可以复制、移动、重命名、删除、压缩、解压、分享和打开文件；运行 `nibras-setup-storage` 后，还可以访问手机存储。

```sh
files
nibras-setup-storage
```

## 9. SSH

侧边菜单中的 SSH 页面会保存你的主机、密钥、端口转发和跳板机。支持 Mosh、Telnet 和 SFTP。你也可以在手机上运行 SSH 服务器。

```sh
ssh user@example.com
nibras-sshd
```

主机以 `ssh config` 文件的形式导出和导入，因此无需云账号即可在设备之间迁移。

## 10. APK 工具与资源编辑器

`nibras-apk` 可直接在手机上构建、拆解、编辑和签名 Android 应用。

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

可视化资源编辑器（`nibras-apk res`）让你在表格中编辑各语言的文本、修改颜色、从图库替换图标和图片，以及更改应用名称、包名和版本号。之后只需轻点一下，即可重新构建、签名并安装应用。我们有意没有加入破解他人应用许可证校验或保护机制的功能。

## 11. 视频、音频、下载与文档

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

`download` 可以通过链接保存视频、音乐和文件。请只下载你有权保存的内容。`media` 命令会打开一个可视化界面来完成同样的任务。

## 12. 个性化与彩色文字

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

`theme list`、`prompt list`、`font list` 和 `color list` 会列出所有选项。`customize` 会打开包含全部设置的菜单，`reset-look` 则将一切恢复为默认值。设置保存在 `~/.nibras/` 中。

## 13. 语言命令

终端能够理解多种语言的自然短语，且不区分大小写。

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

阿拉伯语、波斯语和乌尔都语界面采用从右到左的布局。要在终端中以连写形式显示阿拉伯文字，请使用 `rtl` 命令，例如 `cat note.txt | rtl`。

### 用您的语言输入命令

您也可以用中文输入 Nibras 命令。英文名称始终可用，`ls`、`git`、`cd` 等标准 Linux 命令不会被改名。

| 中文 | 英文 |
|---|---|
| `帮助` | `help` |
| `安装 python` | `install python` |
| `卸载` | `remove` |
| `更新` | `update` |
| `运行 hello.py` | `run hello.py` |
| `语言` | `lang` |
| `主题 dracula` | `theme dracula` |
| `颜色 red Salam` | `color red Salam` |
| `横幅` | `banner` |
| `提示符` | `prompt` |
| `自定义` | `customize` |
| `脚本` | `scripts` |
| `远程` | `remote` |
| `备份` | `backup` |
| `恢复` | `restore` |
| `学习` | `learn` |
| `修复` | `fix` |
| `提问` | `ask` |
| `解释` | `explain` |
| `礼拜` | `prayer` |
| `视频` | `video` |
| `音频` | `audio` |
| `下载` | `download` |
| `媒体` | `media` |
| `文件` | `files` |
| `编辑` | `edit` |
| `菜单` | `menu` |
| `古兰经` | `quran` |
<!-- native-commands -->

## 14. Nibras 独有的功能

- `ask`、`explain` 和 `fix` 帮助你理解命令、解决错误。
- `recipes` 和 `learn` 提供现成的解决方案和 Linux 课程。
- `vault` 以加密方式保存私密文件，`trash` 让删除更安全。
- 内置 `quran`、`hadith` 和 `prayer`（带提醒）。
- `llm` 可在手机上运行离线 AI 聊天。
- `webterm` 可通过 Wi-Fi 在电脑浏览器中打开此终端。
- 分屏、悬浮窗口和 Linux 桌面（XFCE）都可以在应用内运行。
- `scripts` 打开脚本商店，您可以在其中浏览、阅读、安装并运行现成的脚本。
- `remote` 在同一 Wi-Fi 下的电脑浏览器中打开此终端，一次性链接也会以二维码显示。
- `backup drive` 用您自己的密码短语加密主机和设置，并保存到 Google 云端硬盘或任意文件夹。`restore drive` 可将其恢复。
- `learn` 开始互动课程：您输入真实的命令，系统会自动检查。
- 主屏幕小部件可一键运行常用命令，并显示服务器状态和礼拜时间。

## 15. 权限

Nibras Terminal 只会在命令需要时才请求权限。相机、麦克风、位置、通讯录和短信相关命令会在首次使用时请求权限。安装你自己构建的 APK 需要“安装未知应用”权限。礼拜提醒需要“闹钟和提醒”权限。通知访问权限、“修改系统设置”以及无线调试配对（`nibras-adb pair`）均为可选项，仅由提到它们的命令使用。在没有 root 的情况下，Windows 文件共享使用 4445 端口而不是 445 端口，并且无法使用 `sshfs` 等基于 FUSE 的工具。

## 16. 故障排除

- 如果某个命令不存在，照样输入即可：Nibras 会推荐合适的工具包。
- 如果安装中断，请再次运行相同的 `install` 命令；下载会断点续传并进行校验。
- `nibras-reload-settings` 会应用你对设置文件所做的更改。
- 如果应用意外关闭，错误报告会保存在 `crash.txt` 中，下次启动时应用会提示你查看并分享它。在 GitHub 上报告问题时，请附上此文件。

## 17. 常见问题

**Nibras Terminal 免费吗？** 是的，它是开源软件。

**需要 root 吗？** 不需要。所有功能都无需 root 即可运行，只有上文提到的少数系统级任务受到限制。

**为什么应用这么小？** 应用中只包含 Alpine Linux 和翻译文件，其他所有内容都会在你需要时通过 `install` 下载。

**能在 32 位手机上运行吗？** 可以。同一个 APK 同时支持 arm64 和 arm32 设备。
