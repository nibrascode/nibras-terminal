#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
sha256sum -c catalog/SHA256SUMS.small
echo "kiçik paketlər düzdür"
