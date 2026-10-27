@echo off
setlocal enabledelayedexpansion
title Debloat da TV box
echo ============================================
echo   DEBLOAT DA TV BOX (via ADB, sem root)
echo ============================================
echo Requisitos: adb instalado (platform-tools no PATH), depuracao USB ligada
echo e a TV box conectada. Para conectar por rede: adb connect IP_DA_TV:5555
echo.
where adb >nul 2>nul
if errorlevel 1 (
  echo [ERRO] adb nao encontrado no PATH.
  pause
  exit /b 1
)
adb devices
echo.
set LISTA=com.android.email com.android.exchange com.android.calendar com.android.deskclock com.android.calculator2 com.android.music com.android.gallery3d com.android.soundrecorder com.android.quicksearchbox com.android.stk com.android.providers.partnerbookmarks com.android.bookmarkprovider com.android.wallpaper.livepicker com.android.musicvis com.android.dreams.basic com.android.dreams.phototable com.android.magicsmoke com.android.noisefield com.android.phasebeam com.android.galaxy4 com.android.holospiral com.android.facelock com.google.android.videos com.google.android.music com.google.android.apps.magazines com.google.android.talk com.google.android.keep
set N=0
set ACHADOS=
for %%P in (%LISTA%) do (
  adb shell pm path %%P | findstr /b /c:"package:" >nul
  if not errorlevel 1 (
    set /a N+=1
    set ACHADOS=!ACHADOS! %%P
    echo   encontrado: %%P
  )
)
echo.
if %N%==0 (
  echo Nenhum app da lista existe nesta TV box. Nada a fazer.
  pause
  exit /b 0
)
echo Foram encontrados %N% apps. Eles serao removidos so para o usuario 0
echo (nao apaga o app da particao do sistema; da para desfazer com restaurar.bat).
set /p OK=Remover agora? (S/N): 
if /i not "%OK%"=="S" (
  echo Cancelado.
  pause
  exit /b 0
)
for %%P in (%ACHADOS%) do (
  echo Removendo %%P ...
  adb shell pm uninstall -k --user 0 %%P
)
echo.
echo Pronto. Se algo parar de funcionar, rode restaurar.bat.
pause