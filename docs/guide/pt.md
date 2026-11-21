# Nibras Terminal — Guia do usuário (Português)

[← Voltar ao README](../../README.md) · [Todos os idiomas](../../README.md#-guides-in-your-language)

## 1. O que é o Nibras Terminal?

O Nibras Terminal é um terminal Linux completo para celulares Android. Ele abre direto em um terminal de verdade, roda o Alpine Linux sem nenhuma configuração e pode adicionar Debian, Ubuntu, Arch, Fedora e Kali sempre que você precisar. Também inclui um editor de código com suporte a servidores de linguagem, um gerenciador de arquivos, um cliente SSH, ferramentas para criar e modificar arquivos APK, ferramentas de mídia e um grande conjunto de comandos que só o Nibras tem. O aplicativo continua pequeno, porque as ferramentas pesadas só são baixadas quando você pede.

## 2. Instalação e primeira execução

1. Baixe o APK diretamente: https://github.com/nibrascode/nibras-terminal/releases/latest/download/nibras-terminal.apk
Câmera, microfone, SMS, contatos e localização só são pedidos quando esse comando roda, não ao abrir o aplicativo.
Atualizações automáticas (Obtainium): https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/nibrascode/nibras-terminal
2. Abra o arquivo e permita "Instalar apps desconhecidos" quando o Android pedir.
3. Na primeira execução, o Nibras Terminal mostra um seletor de idioma. Cada idioma aparece com o próprio nome, e o idioma do seu celular já vem selecionado. Se o idioma do seu celular não estiver disponível, o inglês é usado.
4. O ambiente Alpine Linux integrado é preparado automaticamente e o terminal se abre.

Você pode mudar o idioma a qualquer momento com o comando `lang` ou nas Configurações.

## 3. Noções básicas

- Deslize a partir da borda esquerda da tela, ou toque em `☰` na fileira de teclas extras, para abrir o menu lateral com sessões, ambientes, ferramentas e configurações.
- Toque e segure uma sessão para renomeá-la ou fechá-la. Toque e segure "Nova sessão" para escolher o ambiente (Alpine, Debian, Ubuntu ou o shell do Android).
- A fileira de teclas extras oferece `ESC`, `TAB`, `CTRL`, `ALT`, as setas e mais. `CTRL` e `ALT` ficam pressionadas até a próxima tecla.
- O botão de diminuir volume funciona como `CTRL` e o de aumentar volume funciona como modificador das teclas extras.
- Digite `nibras` para abrir o menu com todas as ferramentas do Nibras e `help` para ver uma lista curta de comandos.

```sh
nibras
help
```

## 4. Instalando ferramentas com `install`

As ferramentas não ficam guardadas dentro do APK. Digite `install` para ver todos os pacotes com uma breve descrição e `install <nome>` para adicionar um. Programas que não são pacotes do Nibras são instalados com `pkg install <nome>`.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Ambientes Linux (distribuições)

O Alpine Linux já vem no aplicativo. As outras distribuições são baixadas sob demanda, e cada uma funciona como um ambiente separado.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Linguagens de programação e `run`

O comando `run` reconhece um arquivo pela extensão, instala a ferramenta necessária na primeira vez e executa o arquivo. Ele funciona com Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell e outras. Se você indicar uma pasta, ele detecta o tipo de projeto (Cargo, módulos Go, npm, Gradle, CMake, Makefile e outros).

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Editor de código e LSP

Abra um arquivo com `edit <arquivo>` ou pelo gerenciador de arquivos. O editor destaca a sintaxe de mais de 25 linguagens, completa palavras-chave e trechos de código, formata o código, mostra a pré-visualização de Markdown, HTML e layouts Android, e executa o arquivo com o botão ▶.

Para ter autocompletar inteligente, erros sublinhados, informações ao passar o cursor e ir para a definição, instale um servidor de linguagem uma vez. A barra de status mostra 🧠 quando o servidor está em execução.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Toque e segure uma palavra no editor para ver a documentação dela. O menu ⋯ contém "Problemas", "Informações no cursor" e "Ir para a definição".

## 8. Gerenciador de arquivos

Abra o gerenciador de arquivos pelo menu lateral ou com o comando `files`. Você pode copiar, mover, renomear, excluir, compactar, extrair, compartilhar e abrir arquivos, e pode acessar o armazenamento do celular depois de executar `nibras-setup-storage`.

```sh
files
nibras-setup-storage
```

## 9. SSH

A tela SSH do menu lateral guarda seus hosts, chaves, redirecionamentos de porta e hosts intermediários (jump hosts). Mosh, Telnet e SFTP são suportados. Você também pode rodar um servidor SSH no celular.

```sh
ssh user@example.com
nibras-sshd
```

Os hosts são exportados e importados como um arquivo `ssh config`, então você pode levá-los de um aparelho para outro sem conta na nuvem.

## 10. Ferramentas de APK e editor de recursos

O `nibras-apk` compila, desmonta, edita e assina aplicativos Android no próprio celular.

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

O editor visual de recursos (`nibras-apk res`) permite editar os textos de cada idioma em uma tabela, mudar cores, substituir ícones e imagens pela galeria e alterar o nome do aplicativo, o nome do pacote e a versão. Depois, um toque recompila, assina e instala o aplicativo. Recursos que quebram a verificação de licença ou a proteção de aplicativos de outras pessoas não foram incluídos de propósito.

## 11. Vídeo, áudio, downloads e documentos

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

O `download` salva vídeos, músicas e arquivos a partir de um link. Baixe apenas conteúdo que você tem o direito de salvar. O comando `media` abre uma tela visual para as mesmas tarefas.

## 12. Personalização e texto colorido

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

`theme list`, `prompt list`, `font list` e `color list` mostram todas as opções. `customize` abre um menu com todas as configurações, e `reset-look` volta tudo ao padrão. As configurações ficam em `~/.nibras/`.

## 13. Comandos de idioma

O terminal entende frases naturais em vários idiomas, e letras maiúsculas não fazem diferença.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

As telas em árabe, persa e urdu são exibidas da direita para a esquerda. Para mostrar texto árabe com as letras ligadas dentro do terminal, use o comando `rtl`, por exemplo `cat note.txt | rtl`.

### Comandos no seu idioma

Você também pode escrever os comandos do Nibras em português. Os nomes em inglês sempre funcionam, e os comandos padrão do Linux, como `ls`, `git` e `cd`, não são renomeados. Os comandos funcionam com e sem acentos, por exemplo `instalar` e `atualizar`.

| Português | Inglês |
|---|---|
| `ajuda` | `help` |
| `instalar python` | `install python` |
| `remover` | `remove` |
| `atualizar` | `update` |
| `executar hello.py` | `run hello.py` |
| `idioma` | `lang` |
| `tema dracula` | `theme dracula` |
| `cor red Salam` | `color red Salam` |
| `personalizar` | `customize` |
| `remoto` | `remote` |
| `restaurar` | `restore` |
| `aprender` | `learn` |
| `corrigir` | `fix` |
| `perguntar` | `ask` |
| `explicar` | `explain` |
| `oração` | `prayer` |
| `vídeo` | `video` |
| `som` | `audio` |
| `baixar` | `download` |
| `mídia` | `media` |
| `arquivos` | `files` |
| `editar` | `edit` |
| `alcorão` | `quran` |
<!-- native-commands -->

## 14. Recursos que só o Nibras tem

- `ask`, `explain` e `fix` ajudam você com comandos e erros.
- `recipes` e `learn` trazem soluções prontas e aulas de Linux.
- `vault` guarda arquivos privados criptografados, e `trash` torna a exclusão segura.
- `quran`, `hadith` e `prayer` (com lembretes) vêm integrados.
- `llm` roda um chat de IA offline no celular.
- `webterm` abre este terminal no navegador de um computador pelo Wi-Fi.
- Tela dividida, janela flutuante e um desktop Linux (XFCE) funcionam dentro do aplicativo.
- `scripts` abre a loja de scripts, onde você pode explorar, ler, instalar e executar scripts prontos.
- `remote` abre este terminal no navegador de um computador na mesma rede Wi-Fi. O link de uso único também aparece como código QR.
- `backup drive` salva seus hosts e configurações, criptografados com sua própria frase-senha, no Google Drive ou em qualquer pasta. `restore drive` os traz de volta.
- `learn` inicia aulas interativas: você digita comandos reais e eles são verificados automaticamente.
- Os widgets da tela inicial executam seus comandos favoritos com um toque e mostram o status dos servidores e os horários de oração.

## 15. Permissões

O Nibras Terminal só pede uma permissão quando um comando precisa dela. Os comandos de câmera, microfone, localização, contatos e SMS pedem na primeira vez que você os usa. "Instalar apps desconhecidos" é necessário para instalar os APKs que você cria. "Alarmes e lembretes" é necessário para os lembretes de oração. O acesso a notificações, "Modificar configurações do sistema" e o pareamento de depuração sem fio (`nibras-adb pair`) são opcionais e só são usados pelos comandos que os mencionam. Sem root, o compartilhamento de arquivos do Windows usa a porta 4445 em vez da 445, e ferramentas baseadas em FUSE, como o `sshfs`, não estão disponíveis.

## 16. Solução de problemas

- Se um comando não existir, digite-o mesmo assim: o Nibras sugere o pacote certo.
- Se uma instalação parar, execute o mesmo comando `install` de novo; o download continua de onde parou e é verificado.
- `nibras-reload-settings` aplica as mudanças feitas nos arquivos de configuração.
- Se o aplicativo fechar inesperadamente, o relatório de erro é salvo em `crash.txt`, e a próxima execução oferece mostrá-lo e compartilhá-lo. Anexe esse arquivo ao relatar um problema no GitHub.

## 17. Perguntas frequentes

**O Nibras Terminal é gratuito?** Sim. Ele é de código aberto.

**Precisa de root?** Não. Tudo funciona sem root; apenas algumas tarefas de nível de sistema são limitadas, como descrito acima.

**Por que o aplicativo é tão pequeno?** Ele inclui apenas o Alpine Linux e as traduções. Todo o resto é baixado com `install` quando você precisa.

**Funciona em celulares de 32 bits?** Sim. Um único APK suporta aparelhos arm64 e arm32.
