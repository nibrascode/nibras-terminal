# Nibras Terminal — 사용 설명서 (한국어)

[← README로 돌아가기](../../README.md) · [모든 언어](../../README.md#-guides-in-your-language)

## 1. Nibras Terminal이란?

Nibras Terminal은 Android 휴대폰을 위한 완전한 Linux 터미널입니다. 앱을 실행하면 바로 실제 터미널이 열리고, Alpine Linux가 별도 설정 없이 동작하며, 필요할 때 언제든지 Debian, Ubuntu, Arch, Fedora, Kali를 추가할 수 있습니다. 또한 언어 서버를 지원하는 코드 편집기, 파일 관리자, SSH 클라이언트, APK 파일을 만들고 수정하는 도구, 미디어 도구, 그리고 Nibras에만 있는 많은 명령어를 갖추고 있습니다. 용량이 큰 도구는 요청할 때만 내려받기 때문에 앱 자체는 작게 유지됩니다.

## 2. 설치와 첫 실행

1. GitHub 프로젝트 페이지(Actions → *Android package* → artifact)나 개발자가 공유한 링크에서 최신 APK를 내려받습니다.
2. 파일을 열고 Android가 요청하면 "출처를 알 수 없는 앱 설치"를 허용합니다.
3. 처음 실행하면 Nibras Terminal이 언어 선택 화면을 보여 줍니다. 각 언어는 해당 언어의 고유 이름으로 표시되며, 휴대폰의 언어가 미리 선택되어 있습니다. 휴대폰 언어가 없으면 영어가 사용됩니다.
4. 내장된 Alpine Linux 환경이 자동으로 준비되고 터미널이 열립니다.

언어는 `lang` 명령어나 설정에서 언제든지 바꿀 수 있습니다.

## 3. 기본 사용법

- 화면 왼쪽 가장자리에서 밀거나 추가 키 줄의 `☰`을 누르면 세션, 환경, 도구, 설정이 있는 사이드 메뉴가 열립니다.
- 세션을 길게 누르면 이름을 바꾸거나 닫을 수 있습니다. "새 세션"을 길게 누르면 환경(Alpine, Debian, Ubuntu 또는 Android 셸)을 선택할 수 있습니다.
- 추가 키 줄에는 `ESC`, `TAB`, `CTRL`, `ALT`, 방향키 등이 있습니다. `CTRL`과 `ALT`는 다음 키를 누를 때까지 눌린 상태로 유지됩니다.
- Termux와 마찬가지로 볼륨 낮추기 버튼은 `CTRL`로, 볼륨 높이기 버튼은 추가 키 보조 키로 동작합니다.
- `nibras`를 입력하면 모든 Nibras 도구 메뉴가 열리고, `help`를 입력하면 간단한 명령어 목록이 표시됩니다.

```sh
nibras
help
```

## 4. `install`로 도구 설치하기

도구는 APK 안에 들어 있지 않습니다. `install`을 입력하면 모든 팩과 간단한 설명을 볼 수 있고, `install <이름>`을 입력하면 원하는 팩을 추가할 수 있습니다. Nibras 팩이 아닌 프로그램은 `pkg install <이름>`으로 설치합니다.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Linux 환경(배포판)

Alpine Linux는 앱에 포함되어 있습니다. 다른 배포판은 요청할 때 내려받으며, 각각 별도의 환경으로 실행됩니다.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. 프로그래밍 언어와 `run`

`run` 명령어는 확장자로 파일을 인식하고, 처음 실행할 때 필요한 도구를 자동으로 설치한 뒤 파일을 실행합니다. Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell 등을 지원합니다. 폴더를 지정하면 프로젝트 종류(Cargo, Go 모듈, npm, Gradle, CMake, Makefile 등)를 스스로 감지합니다.

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. 코드 편집기와 LSP

`edit <파일>`로 또는 파일 관리자에서 파일을 엽니다. 편집기는 25개가 넘는 언어의 구문을 강조하고, 키워드와 코드 조각을 자동 완성하며, 코드를 정렬하고, Markdown, HTML, Android 레이아웃을 미리 보여 주고, ▶ 버튼으로 파일을 실행합니다.

똑똑한 자동 완성, 오류 밑줄 표시, 마우스 오버 정보, 정의로 이동 기능을 쓰려면 언어 서버를 한 번 설치하세요. 서버가 실행 중이면 상태 표시줄에 🧠가 나타납니다.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

편집기에서 단어를 길게 누르면 해당 문서를 볼 수 있습니다. ⋯ 메뉴에는 "문제", "커서 위치 정보", "정의로 이동"이 있습니다.

## 8. 파일 관리자

사이드 메뉴나 `files` 명령어로 파일 관리자를 엽니다. 파일을 복사, 이동, 이름 변경, 삭제, 압축, 압축 해제, 공유하고 열 수 있으며, `nibras-setup-storage`를 실행한 뒤에는 휴대폰 저장소에도 접근할 수 있습니다.

```sh
files
nibras-setup-storage
```

## 9. SSH

사이드 메뉴의 SSH 화면에는 호스트, 키, 포트 포워딩, 점프 호스트가 저장됩니다. Mosh, Telnet, SFTP를 지원합니다. 휴대폰에서 SSH 서버를 실행할 수도 있습니다.

```sh
ssh user@example.com
nibras-sshd
```

호스트는 `ssh config` 파일로 내보내고 가져올 수 있으므로 클라우드 계정 없이도 기기 간에 옮길 수 있습니다.

## 10. APK 도구와 리소스 편집기

`nibras-apk`는 휴대폰에서 직접 Android 앱을 빌드하고, 분해하고, 수정하고, 서명합니다.

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

시각적 리소스 편집기(`nibras-apk res`)를 사용하면 언어별 문자열을 표에서 편집하고, 색상을 바꾸고, 갤러리에서 아이콘과 이미지를 교체하고, 앱 이름, 패키지 이름, 버전을 변경할 수 있습니다. 그런 다음 한 번만 누르면 앱이 다시 빌드되고 서명되어 설치됩니다. 다른 사람 앱의 라이선스 확인이나 보호 기능을 무력화하는 기능은 의도적으로 넣지 않았습니다.

## 11. 동영상, 오디오, 다운로드, 문서

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

`download`는 링크에서 동영상, 음악, 파일을 저장합니다. 저장할 권리가 있는 콘텐츠만 내려받으세요. `media` 명령어는 같은 작업을 위한 시각적 화면을 엽니다.

## 12. 꾸미기와 컬러 텍스트

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

`theme list`, `prompt list`, `font list`, `color list`는 모든 옵션을 보여 줍니다. `customize`는 모든 설정이 담긴 메뉴를 열고, `reset-look`은 모든 것을 기본값으로 되돌립니다. 설정은 `~/.nibras/`에 저장되며 Termux의 `termux.properties` 파일과 호환됩니다.

## 13. 언어 명령어

터미널은 여러 언어의 자연스러운 문장을 이해하며, 대소문자는 구분하지 않습니다.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

아랍어, 페르시아어, 우르두어 화면은 오른쪽에서 왼쪽으로 배치됩니다. 터미널 안에서 아랍어 글자를 이어진 형태로 표시하려면 `rtl` 명령어를 사용하세요. 예: `cat note.txt | rtl`

## 14. Nibras에만 있는 기능

- `ask`, `explain`, `fix`가 명령어와 오류 해결을 도와줍니다.
- `recipes`와 `learn`에는 바로 쓸 수 있는 해결책과 Linux 강좌가 들어 있습니다.
- `vault`는 개인 파일을 암호화해 보관하고, `trash`는 삭제를 안전하게 만들어 줍니다.
- `quran`, `hadith`, `prayer`(알림 포함)가 내장되어 있습니다.
- `llm`은 휴대폰에서 오프라인 AI 채팅을 실행합니다.
- `webterm`은 Wi-Fi를 통해 컴퓨터 브라우저에서 이 터미널을 엽니다.
- 화면 분할, 플로팅 창, Linux 데스크톱(XFCE)이 앱 안에서 동작합니다.

## 15. 권한

Nibras Terminal은 명령어에 필요할 때만 권한을 요청합니다. 카메라, 마이크, 위치, 연락처, SMS 명령어는 처음 사용할 때 권한을 요청합니다. 직접 빌드한 APK 파일을 설치하려면 "출처를 알 수 없는 앱 설치" 권한이 필요합니다. 기도 알림에는 "알람 및 리마인더" 권한이 필요합니다. 알림 접근, "시스템 설정 수정", 무선 디버깅 페어링(`nibras-adb pair`)은 선택 사항이며 이를 언급하는 명령어만 사용합니다. root가 없으면 Windows 파일 공유는 445번 대신 4445번 포트를 사용하고, `sshfs` 같은 FUSE 기반 도구는 사용할 수 없습니다.

## 16. 문제 해결

- 명령어가 없더라도 그냥 입력하세요. Nibras가 알맞은 팩을 추천해 줍니다.
- 설치가 중간에 멈추면 같은 `install` 명령어를 다시 실행하세요. 다운로드가 이어서 진행되고 검증됩니다.
- `nibras-reload-settings`는 설정 파일의 변경 사항을 적용합니다.
- 앱이 예기치 않게 종료되면 오류 보고서가 `crash.txt`에 저장되고, 다음 실행 때 이를 보여 주고 공유할 수 있도록 안내합니다. GitHub에 문제를 보고할 때 이 파일을 첨부해 주세요.

## 17. 자주 묻는 질문

**Nibras Terminal은 무료인가요?** 네. 오픈 소스입니다.

**root가 필요한가요?** 아니요. 모든 기능이 root 없이 동작하며, 위에서 설명한 몇 가지 시스템 수준 작업만 제한됩니다.

**앱이 왜 이렇게 작나요?** Alpine Linux와 번역만 들어 있습니다. 나머지는 모두 필요할 때 `install`로 내려받습니다.

**32비트 휴대폰에서도 동작하나요?** 네. 하나의 APK가 arm64와 arm32 기기를 모두 지원합니다.

**Termux 설정을 그대로 쓸 수 있나요?** 네. Nibras는 `~/.termux/termux.properties`, `colors.properties`, `font.ttf`를 읽고 `termux-*` 명령어 이름도 인식합니다.
