@echo off
title CLIENT Planning Global
chcp 65001 >nul
cd /d "%~dp0"

echo =====================================================
echo   CLIENT Planning Global
echo   Le serveur doit etre demarre avant ce client.
echo =====================================================
echo.

if not exist bin mkdir bin
set POI_CP=app\jar\poi-bin-5.2.3\*;app\jar\poi-bin-5.2.3\lib\*;app\jar\poi-bin-5.2.3\ooxml-lib\*

echo.
java -cp "bin;%POI_CP%" app.ControleurClient
pause
