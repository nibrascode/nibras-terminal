# Nibras Terminal: localized messages for nibras-* commands (sourced). Usage: nmsg <key> [printf args]
NIBRAS_LANG=$(cat /etc/nibras/lang 2>/dev/null || echo en)
# Other languages: /usr/local/share/nibras/i18n/<lang>.tsv (English text or nmsg.<key> → translation).
nibras_i18n() {
  _t=/usr/local/share/nibras/i18n/$NIBRAS_LANG.tsv
  [ -r "$_t" ] && awk -F '\t' -v k="$1" '$1 == k { print $2; exit }' "$_t"
}
nmsg() {
  _k=$1; shift; _f=
  case "$NIBRAS_LANG" in en|az|tr|ru) ;; *) _f=$(nibras_i18n "nmsg.$_k") ;; esac
  [ -n "$_f" ] || case "$NIBRAS_LANG:$_k" in
    az:lang_now) _f='Dil indi: %s' ;; tr:lang_now) _f='Dil şimdi: %s' ;; ru:lang_now) _f='Язык теперь: %s' ;; *:lang_now) _f='Language is now: %s' ;;
    az:installing) _f='%s quraşdırılır…' ;; tr:installing) _f='%s kuruluyor…' ;; ru:installing) _f='Установка %s…' ;; *:installing) _f='Installing %s…' ;;
    az:no_pm) _f='Bu mühitdə paket meneceri tapılmadı' ;; tr:no_pm) _f='Bu ortamda paket yöneticisi bulunamadı' ;; ru:no_pm) _f='В этой среде нет менеджера пакетов' ;; *:no_pm) _f='No package manager found in this environment' ;;
    az:unknown_cmd) _f='naməlum əmr: %s' ;; tr:unknown_cmd) _f='bilinmeyen komut: %s' ;; ru:unknown_cmd) _f='неизвестная команда: %s' ;; *:unknown_cmd) _f='unknown command: %s' ;;
    az:set_password) _f='Əvvəlcə root üçün şifrə təyin edin:' ;; tr:set_password) _f='Önce root için bir parola belirleyin:' ;; ru:set_password) _f='Сначала задайте пароль root:' ;; *:set_password) _f='Set a password for root first:' ;;
    az:sshd_on) _f='SSH server %s portunda işləyir. Başqa cihazdan: ssh -p %s root@%s' ;; tr:sshd_on) _f='SSH sunucusu %s portunda çalışıyor. Başka cihazdan: ssh -p %s root@%s' ;; ru:sshd_on) _f='SSH-сервер работает на порту %s. С другого устройства: ssh -p %s root@%s' ;; *:sshd_on) _f='SSH server listening on port %s. From another device: ssh -p %s root@%s' ;;
    az:sshd_off) _f='SSH server dayandırıldı' ;; tr:sshd_off) _f='SSH sunucusu durduruldu' ;; ru:sshd_off) _f='SSH-сервер остановлен' ;; *:sshd_off) _f='SSH server stopped' ;;
    az:shell_now) _f='Yeni sessiyalar bu shell ilə açılacaq: %s' ;; tr:shell_now) _f='Yeni oturumlar bu kabukla açılacak: %s' ;; ru:shell_now) _f='Новые сессии будут использовать: %s' ;; *:shell_now) _f='New sessions will use %s' ;;
    az:shell_cur) _f='hazırkı shell: %s' ;; tr:shell_cur) _f='şu anki kabuk: %s' ;; ru:shell_cur) _f='текущая оболочка: %s' ;; *:shell_cur) _f='current shell: %s' ;;
    az:desk_running) _f='Masaüstü işləyir. Tətbiqdə açılır; xarici VNC üçün: 127.0.0.1:5901' ;; tr:desk_running) _f='Masaüstü çalışıyor. Uygulamada açılıyor; harici VNC için: 127.0.0.1:5901' ;; ru:desk_running) _f='Рабочий стол запущен. Открывается в приложении; внешний VNC: 127.0.0.1:5901' ;; *:desk_running) _f='Desktop is running. It opens inside the app; external VNC: 127.0.0.1:5901' ;;
    az:desk_stopped) _f='Masaüstü dayandırıldı' ;; tr:desk_stopped) _f='Masaüstü durduruldu' ;; ru:desk_stopped) _f='Рабочий стол остановлен' ;; *:desk_stopped) _f='Desktop stopped' ;;
    az:desk_wait) _f='Masaüstü başladılır…' ;; tr:desk_wait) _f='Masaüstü başlatılıyor…' ;; ru:desk_wait) _f='Запуск рабочего стола…' ;; *:desk_wait) _f='Starting the desktop…' ;;
    az:desk_fail) _f='Masaüstü başlamadı. Jurnal: %s' ;; tr:desk_fail) _f='Masaüstü başlamadı. Günlük: %s' ;; ru:desk_fail) _f='Рабочий стол не запустился. Журнал: %s' ;; *:desk_fail) _f='The desktop did not start. Log: %s' ;;
    az:not_running) _f='işləmir' ;; tr:not_running) _f='çalışmıyor' ;; ru:not_running) _f='не запущен' ;; *:not_running) _f='not running' ;;
    az:running) _f='işləyir' ;; tr:running) _f='çalışıyor' ;; ru:running) _f='запущен' ;; *:running) _f='running' ;;
    az:adb_pair) _f='Ayarlar → Tərtibatçı seçimləri → Simsiz sazlama → "Cüt kodla birləşdir". Port və 6 rəqəmli kodu yazın.' ;; tr:adb_pair) _f='Ayarlar → Geliştirici seçenekleri → Kablosuz hata ayıklama → "Eşleme koduyla eşle". Portu ve 6 haneli kodu yazın.' ;; ru:adb_pair) _f='Настройки → Для разработчиков → Отладка по Wi-Fi → «Подключить по коду». Введите порт и 6-значный код.' ;; *:adb_pair) _f='Settings → Developer options → Wireless debugging → "Pair device with pairing code". Enter the port and the 6-digit code.' ;;
    az:adb_port) _f='Simsiz sazlama ekranındakı "IP ünvanı və port"dakı portu yazın:' ;; tr:adb_port) _f='Kablosuz hata ayıklama ekranındaki "IP adresi ve bağlantı noktası" portunu yazın:' ;; ru:adb_port) _f='Введите порт из «IP-адрес и порт» на экране отладки по Wi-Fi:' ;; *:adb_port) _f='Enter the port shown under "IP address & Port" on the Wireless debugging screen:' ;;
    az:adb_none) _f='Qoşulu cihaz yoxdur. Əvvəl: nibras-adb pair, sonra nibras-adb connect' ;; tr:adb_none) _f='Bağlı cihaz yok. Önce: nibras-adb pair, sonra nibras-adb connect' ;; ru:adb_none) _f='Нет подключённых устройств. Сначала: nibras-adb pair, затем nibras-adb connect' ;; *:adb_none) _f='No device connected. First: nibras-adb pair, then nibras-adb connect' ;;
    az:api_down) _f='Tətbiq körpüsü cavab vermir (Nibras Terminal açıqdır?)' ;; tr:api_down) _f='Uygulama köprüsü yanıt vermiyor (Nibras Terminal açık mı?)' ;; ru:api_down) _f='Мост приложения не отвечает (Nibras Terminal запущен?)' ;; *:api_down) _f='The app bridge did not answer (is Nibras Terminal running?)' ;;
    az:need_tool) _f='%s lazımdır, quraşdırılır…' ;; tr:need_tool) _f='%s gerekli, kuruluyor…' ;; ru:need_tool) _f='Нужен %s, устанавливаю…' ;; *:need_tool) _f='%s is needed, installing…' ;;
    az:need_fail) _f='%s quraşdırıla bilmədi' ;; tr:need_fail) _f='%s kurulamadı' ;; ru:need_fail) _f='Не удалось установить %s' ;; *:need_fail) _f='Could not install %s' ;;
    *) _f="$_k" ;;
  esac
  printf "$_f\n" "$@"
}
