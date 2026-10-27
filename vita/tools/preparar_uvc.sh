#!/usr/bin/env bash
# Baixa a UVCAndroid 1.0.13, aplica as correcoes para o PS Vita em vita/uvc/src e roda o teste
# automatico da remontagem bulk e da conversao NV12 (se o teste falhar, a compilacao para aqui).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
git clone --depth 1 --branch 1.0.13 https://github.com/shiyinghan/UVCAndroid.git "$TMP/uvc"
rm -rf "$AQUI/uvc/src"
cp -r "$TMP/uvc/libuvccamera/src" "$AQUI/uvc/src"
python3 "$AQUI/tools/patch_uvc.py" "$AQUI/uvc/src/main" "$AQUI/tools/tvbox_uvc.h"

J="$AQUI/uvc/src/main/jni"
echo "== teste da correcao (remontagem bulk + NV12) =="
g++ -O2 -std=c++14 -w -I"$J/libyuv/include" -I"$AQUI/tools" \
    "$AQUI/tools/teste_tvbox_uvc.cc" "$J"/libyuv/source/*.cc -o "$TMP/teste" -lpthread
"$TMP/teste"