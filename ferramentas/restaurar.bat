@echo off
setlocal enabledelayedexpansion
title Restaurar apps removidos
where adb >nul 2>nul
if errorlevel 1 (
  echo [ERRO] adb nao encontrado no PATH.
  pause
  exit /b 1
)
adb devices
set LISTA=com.android.email com.android.exchange com.android.calendar com.android.deskclock com.android.calculator2 com.android.music com.android.gallery3d com.android.soundrecorder com.android.quicksearchbox com.android.stk com.android.providers.partnerbookmarks com.android.bookmarkprovider com.android.wallpaper.livepicker com.android.musicvis com.android.dreams.basic com.android.dreams.phototable com.android.magicsmoke com.android.noisefield com.android.phasebeam com.android.galaxy4 com.android.holospiral com.android.facelock com.google.android.videos com.google.android.music com.google.android.apps.magazines com.google.android.talk com.google.android.keep
echo Restaurando os apps da lista que foram removidos (os que nao foram removidos sao ignorados)...
for %%P in (%LISTA%) do (
  adb shell cmd package install-existing %%P
)
echo Pronto.
pause