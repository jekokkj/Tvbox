#!/usr/bin/env bash
# Baixa a UVCAndroid 1.0.13 e aplica a correcao de NV12 em vita/uvc/src (feito na compilacao).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
git clone --depth 1 --branch 1.0.13 https://github.com/shiyinghan/UVCAndroid.git "$TMP/uvc"
rm -rf "$AQUI/uvc/src"
cp -r "$TMP/uvc/libuvccamera/src" "$AQUI/uvc/src"
python3 "$AQUI/tools/patch_uvc.py" "$AQUI/uvc/src/main/jni/UVCCamera/UVCPreview.cpp"
ls "$AQUI/uvc/src/main"