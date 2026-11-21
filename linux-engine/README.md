# Linux mühərriki

PRoot Termux-un `proot 5.1.107` yığımıdır (GPL-2.0). `libtalloc` LGPL-3.0, `libandroid-shmem` isə BSD lisenziyasındadır. Binary-lər Termux-un `aarch64` və `arm` deb paketlərindən götürülüb, `patchelf` ilə dəyişdirilib: NEEDED `libtalloc.so`, soname `libtalloc.so`, rpath yoxdur. Onlar `app/src/main/jniLibs/<abi>/` qovluğunda `lib*.so` adı ilə durur, çünki Android 10 və sonrası yalnız `nativeLibraryDir` qovluğundan `exec` etməyə icazə verir (`useLegacyPackaging = true`).

| Fayl | arm64-v8a | armeabi-v7a |
| --- | --- | --- |
| libproot.so | var | var |
| libproot-loader.so | var | var |
| libproot-loader32.so | var (32-bit qonaqlar üçün) | yoxdur |
| libtalloc.so, libandroid-shmem.so | var | var |

Sessiya `Rootfs.launch()` ilə başlayır:
`libproot.so --kill-on-exit --link2symlink --sysvipc -L -0 -r <rootfs> -w /root -b /dev -b /proc ...`, ardınca `/usr/bin/env -i ... /usr/local/bin/nibras-login`. Env dəyişənləri: `PROOT_LOADER`, `PROOT_LOADER_32`, `PROOT_TMP_DIR`, `LD_LIBRARY_PATH=nativeLibraryDir`.

Testlər:
- `app/src/test`: rootfs açan kodun JVM testi. Simvolik keçidləri və köklə məhdudlaşmanı yoxlayır.
- `.github/workflows/proot-test.yml`: `ubuntu-24.04-arm` runner-də, `termux/termux-docker` (bionic) içində hər iki ABI üçün PRoot və Alpine işlədir.
