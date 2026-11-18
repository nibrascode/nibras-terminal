#!/bin/sh
# Mühit açıldıqdan sonra yoxlama. Heç nə silmir, heç nə quraşdırmır.
set -eu
echo "== uname =="
uname -a || true
echo "== arch =="
uname -m || true
echo "== os =="
cat /etc/os-release || true
echo "== id =="
id || true
echo "== pwd =="
pwd
echo "== disk =="
df -h || true
echo "== ram =="
free -h || true
