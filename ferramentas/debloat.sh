#!/data/data/com.termux/files/usr/bin/sh
# Debloat da TV box via ADB (sem root). Uso: sh debloat.sh [IP_DA_TV]
# Requer: adb (no Termux: pkg install android-tools) e depuracao USB ligada na TV box.
LISTA="com.android.email com.android.exchange com.android.calendar com.android.deskclock com.android.calculator2 com.android.music com.android.gallery3d com.android.soundrecorder com.android.quicksearchbox com.android.stk com.android.providers.partnerbookmarks com.android.bookmarkprovider com.android.wallpaper.livepicker com.android.musicvis com.android.dreams.basic com.android.dreams.phototable com.android.magicsmoke com.android.noisefield com.android.phasebeam com.android.galaxy4 com.android.holospiral com.android.facelock com.google.android.videos com.google.android.music com.google.android.apps.magazines com.google.android.talk com.google.android.keep"
[ -n "$1" ] && adb connect "$1:5555"
adb devices
ACHADOS=""
for p in $LISTA; do
  if adb shell pm path "$p" | grep -q "^package:"; then
    echo "  encontrado: $p"
    ACHADOS="$ACHADOS $p"
  fi
done
if [ -z "$ACHADOS" ]; then echo "Nenhum app da lista existe nesta TV box."; exit 0; fi
printf "Remover agora (so para o usuario 0, reversivel)? (s/N): "
read r
[ "$r" = "s" ] || [ "$r" = "S" ] || { echo Cancelado.; exit 0; }
for p in $ACHADOS; do
  echo "Removendo $p ..."
  adb shell pm uninstall -k --user 0 "$p"
done
echo "Pronto. Para desfazer: adb shell cmd package install-existing <pacote>"