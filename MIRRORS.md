# Güzgülər

Böyük fayllar git ağacında saxlanmır. Onlar `payloads-0.1` release asset-ləridir.
Tətbiq həmin URL-ləri `catalog/manifest.json` içindən oxuyur.

| Fayl | Mənbə | Checksum |
| --- | --- | --- |
| alpine-minirootfs-3.24.1-aarch64.tar.gz | dl-cdn.alpinelinux.org | rəsmi `.sha256`, yoxlanıb: `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259` |
| debian-stable-slim-arm64.tar.xz | rəsmi `debian:stable-slim` arm64 (Docker) | ixracın SHA-256: `28263ad4441b0f67d4e88f46db53ee5f99e51ab6cf763ada8dc43f0d2578c880` |
| ubuntu-24.04-arm64.tar.xz | rəsmi `ubuntu:24.04` arm64 (Docker) | ixracın SHA-256: `af1edfbd93c9e05feaa2c6ff0bc3dea047f09e8a5e86b16a2d8befc9fccee1ef` |
| cpython-3.12.16+20261110-aarch64-unknown-linux-gnu-install_only_stripped.tar.gz | astral-sh/python-build-standalone `20261110` | rəsmi SHA256SUMS, yoxlanıb |
| node-v26.13.1-linux-arm64.tar.xz | nodejs.org/dist/v26.13.1 | rəsmi SHASUMS256.txt, yoxlanıb |
| proot-aarch64 | skirsten.github.io portable PRoot, aarch64, statik | faylın öz SHA-256: `c902f35b3bce4013d2e78e3bf360b606523d55ab7b907578938577b243bfca38` |

Debian və Ubuntu arxivləri rəsmi imicdən çıxarılıb. PRoot eyni cəmlə APK-nın `libproot.so`
faylıdır, çünki Android 10+ data qovluğundan əmr işlətməyə qoymur. Kök sistem
APK-da yoxdur. İstəməyən istifadəçi yükləmir.
