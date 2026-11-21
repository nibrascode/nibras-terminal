# Nibras Terminal — Guía de uso (Español)

[← Volver al README](../../README.md) · [Todos los idiomas](../../README.md#-guides-in-your-language)

## 1. ¿Qué es Nibras Terminal?

Nibras Terminal es una terminal Linux completa para teléfonos Android. Se abre directamente en una terminal real, ejecuta Alpine Linux sin configuración previa y puede añadir Debian, Ubuntu, Arch, Fedora y Kali cuando los necesites. También incluye un editor de código con soporte para servidores de lenguaje, un gestor de archivos, un cliente SSH, herramientas para crear y modificar archivos APK, herramientas multimedia y un gran conjunto de comandos que solo tiene Nibras. La aplicación sigue siendo pequeña, porque las herramientas pesadas solo se descargan cuando las pides.

## 2. Instalación y primer inicio

1. Descarga el APK más reciente desde la página del proyecto en GitHub (Actions → *Android package* → artifact) o desde el enlace que te compartió el desarrollador.
2. Abre el archivo y permite «Instalar aplicaciones desconocidas» cuando Android lo solicite.
3. En el primer inicio, Nibras Terminal muestra un selector de idioma. Cada idioma aparece con su propio nombre y el idioma de tu teléfono ya está seleccionado. Si el idioma de tu teléfono no está disponible, se usa el inglés.
4. El entorno integrado de Alpine Linux se prepara automáticamente y se abre la terminal.

Puedes cambiar el idioma en cualquier momento con el comando `lang` o desde Ajustes.

## 3. Conceptos básicos

- Desliza desde el borde izquierdo de la pantalla, o toca `☰` en la fila de teclas adicionales, para abrir el menú lateral con sesiones, entornos, herramientas y ajustes.
- Mantén pulsada una sesión para cambiarle el nombre o cerrarla. Mantén pulsado «Nueva sesión» para elegir el entorno (Alpine, Debian, Ubuntu o el shell de Android).
- La fila de teclas adicionales ofrece `ESC`, `TAB`, `CTRL`, `ALT`, las flechas y más. `CTRL` y `ALT` permanecen pulsadas hasta la siguiente tecla.
- La tecla de bajar volumen funciona como `CTRL` y la de subir volumen funciona como modificador de las teclas adicionales.
- Escribe `nibras` para abrir el menú de todas las herramientas de Nibras y `help` para ver una lista breve de comandos.

```sh
nibras
help
```

## 4. Instalar herramientas con `install`

Las herramientas no se guardan dentro del APK. Escribe `install` para ver todos los paquetes con una breve descripción y `install <nombre>` para añadir uno. Los programas que no son paquetes de Nibras se instalan con `pkg install <nombre>`.

```sh
install
install python
install video
install dev
pkg install htop
```

## 5. Entornos Linux (distribuciones)

Alpine Linux viene incluido en la aplicación. Las demás distribuciones se descargan cuando las pides y cada una funciona como un entorno independiente.

```sh
install debian
install ubuntu
install arch
install fedora
install kali
proot-distro list
```

## 6. Lenguajes de programación y `run`

El comando `run` reconoce un archivo por su extensión, instala la herramienta necesaria la primera vez y ejecuta el archivo. Funciona con Python, JavaScript, TypeScript, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, Lua, Dart, R, Julia, Haskell, Elixir, Zig, Nim, Bash, PowerShell y otros. Si le das una carpeta, detecta el tipo de proyecto (Cargo, módulos de Go, npm, Gradle, CMake, Makefile y otros).

```sh
run hello.py
run main.c
run app.ts
run .
install typescript
install zig
```

## 7. Editor de código y LSP

Abre un archivo con `edit <archivo>` o desde el gestor de archivos. El editor resalta la sintaxis de más de 25 lenguajes, completa palabras clave y fragmentos, da formato al código, muestra una vista previa de Markdown, HTML y diseños de Android, y ejecuta el archivo con el botón ▶.

Para tener autocompletado inteligente, errores subrayados, información al pasar el cursor e ir a la definición, instala una vez un servidor de lenguaje. La barra de estado muestra 🧠 cuando el servidor está en marcha.

```sh
edit main.py
install lsp-python
install lsp-java
install lsp-kotlin
install lsp-all
```

Mantén pulsada una palabra en el editor para ver su documentación. El menú ⋯ contiene «Problemas», «Información en el cursor» e «Ir a la definición».

## 8. Gestor de archivos

Abre el gestor de archivos desde el menú lateral o con el comando `files`. Puedes copiar, mover, renombrar, eliminar, comprimir, extraer, compartir y abrir archivos, y puedes acceder al almacenamiento del teléfono después de ejecutar `nibras-setup-storage`.

```sh
files
nibras-setup-storage
```

## 9. SSH

La pantalla SSH del menú lateral guarda tus servidores, claves, redirecciones de puertos y servidores intermedios (jump hosts). Se admiten Mosh, Telnet y SFTP. También puedes ejecutar un servidor SSH en el teléfono.

```sh
ssh user@example.com
nibras-sshd
```

Los servidores se exportan e importan como un archivo `ssh config`, así que puedes pasarlos de un dispositivo a otro sin una cuenta en la nube.

## 10. Herramientas APK y editor de recursos

`nibras-apk` compila, desarma, edita y firma aplicaciones Android en el propio teléfono.

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

El editor visual de recursos (`nibras-apk res`) te permite editar los textos de cada idioma en una tabla, cambiar colores, reemplazar iconos e imágenes desde la galería y cambiar el nombre de la aplicación, el nombre del paquete y la versión. Después, un solo toque vuelve a compilar, firmar e instalar la aplicación. Las funciones que rompen la verificación de licencias o la protección de aplicaciones ajenas no se incluyen a propósito.

## 11. Vídeo, audio, descargas y documentos

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

`download` guarda vídeos, música y archivos desde un enlace. Descarga solo contenido que tengas derecho a guardar. El comando `media` abre una pantalla visual para las mismas tareas.

## 12. Personalización y texto en color

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

`theme list`, `prompt list`, `font list` y `color list` muestran todas las opciones. `customize` abre un menú con todos los ajustes y `reset-look` lo devuelve todo a los valores predeterminados. Los ajustes se guardan en `~/.nibras/`.

## 13. Comandos de idioma

La terminal entiende frases naturales en varios idiomas, y las mayúsculas no importan.

```sh
lang
lang az
az dili yukle
türkçe yükle
تحميل العربية
install lang-fr
lang auto
```

Las pantallas en árabe, persa y urdu se muestran de derecha a izquierda. Para mostrar texto árabe con las letras unidas dentro de la terminal, usa el comando `rtl`, por ejemplo `cat note.txt | rtl`.

## 14. Funciones que solo tiene Nibras

- `ask`, `explain` y `fix` te ayudan con comandos y errores.
- `recipes` y `learn` contienen soluciones listas para usar y lecciones de Linux.
- `vault` guarda los archivos privados cifrados y `trash` hace que borrar sea seguro.
- `quran`, `hadith` y `prayer` (con recordatorios) vienen integrados.
- `llm` ejecuta un chat de IA sin conexión en el teléfono.
- `webterm` abre esta terminal en el navegador de un ordenador a través de Wi-Fi.
- La pantalla dividida, una ventana flotante y un escritorio Linux (XFCE) funcionan dentro de la aplicación.

## 15. Permisos

Nibras Terminal solo pide un permiso cuando un comando lo necesita. Los comandos de cámara, micrófono, ubicación, contactos y SMS lo piden la primera vez que los usas. «Instalar aplicaciones desconocidas» es necesario para instalar los APK que creas. «Alarmas y recordatorios» es necesario para los recordatorios de oración. El acceso a notificaciones, «Modificar ajustes del sistema» y la vinculación de depuración inalámbrica (`nibras-adb pair`) son opcionales y solo los usan los comandos que los mencionan. Sin root, el uso compartido de archivos de Windows usa el puerto 4445 en lugar del 445, y las herramientas basadas en FUSE, como `sshfs`, no están disponibles.

## 16. Solución de problemas

- Si falta un comando, escríbelo de todos modos: Nibras te sugerirá el paquete correcto.
- Si una instalación se detiene, vuelve a ejecutar el mismo comando `install`; la descarga continúa y se verifica.
- `nibras-reload-settings` aplica los cambios hechos en los archivos de ajustes.
- Si la aplicación se cierra inesperadamente, el informe de error se guarda en `crash.txt` y en el siguiente inicio se ofrece mostrarlo y compartirlo. Adjunta este archivo cuando informes de un problema en GitHub.

## 17. Preguntas frecuentes

**¿Nibras Terminal es gratuito?** Sí. Es de código abierto.

**¿Necesita root?** No. Todo funciona sin root; solo algunas tareas a nivel de sistema están limitadas, como se describe arriba.

**¿Por qué la aplicación es tan pequeña?** Solo incluye Alpine Linux y las traducciones. Todo lo demás se descarga con `install` cuando lo necesitas.

**¿Funciona en teléfonos de 32 bits?** Sí. Un único APK es compatible con dispositivos arm64 y arm32.
