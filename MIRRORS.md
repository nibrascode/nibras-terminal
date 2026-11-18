# Güzgülər

Böyük fayllar git ağacında saxlanmır. Onlar `payloads-0.1` release asset-ləridir.
Tətbiq həmin URL-ləri `catalog/manifest.json` içindən oxuyur.

| Fayl | Mənbə | Checksum |
| --- | --- | --- |
| alpine-minirootfs-3.24.1-aarch64.tar.gz | dl-cdn.alpinelinux.org | rəsmi `.sha256`, yoxlanıb: `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259` |
| debian-rootfs-arm64.tar.xz | EXALAB/AnLinux-Resources `Rootfs/Debian/arm64` | ayrıca rəsmi cəm yoxdur; güzgü cəmi `b9f1fbc28cad7984d3a9bac86bf86895c388a3ea398261cc3f761145d5c366dc` |
| ubuntu-rootfs-arm64.tar.xz | EXALAB/AnLinux-Resources `Rootfs/Ubuntu/arm64` | ayrıca rəsmi cəm yoxdur; güzgü cəmi `7480ebcf0169caa9de9810e9f6022f8fde4097deeb4f4018c13346f765342a90` |
| cpython-3.12.16+20261110-aarch64-unknown-linux-gnu-install_only_stripped.tar.gz | astral-sh/python-build-standalone `20261110` | rəsmi SHA256SUMS, yoxlanıb |
| node-v26.13.1-linux-arm64.tar.xz | nodejs.org/dist/v26.13.1 | rəsmi SHASUMS256.txt, yoxlanıb |

Debian və Ubuntu arxivləri uydurulmayıb: AnLinux-un dərc etdiyi faylların
güzgüsüdür. Onları istəməyən istifadəçi yükləmir.
