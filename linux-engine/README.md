# Linux mühərriki

PRoot statik aarch64 binary-dir (GPL-3.0, portable Termux yığımı). APK-da
`libproot.so` kimi durur, çünki Android 10 və sonrası tətbiqin data qovluğundan
`exec` etməyə qoymur. Eyni baytlar release-də `proot-aarch64` adlanır.

Sessiya ekranı açılmış kök sistemin içində bir `sh -c` işlədir. Kök sistem
APK-da yoxdur: istifadəçi Alpine, Debian və ya Ubuntu arxivini özü yükləyib açır.

`Managers.kt` köhnə interfeysdir və sessiya açmır. İşlək yol
`TerminalViewModel.run`.

