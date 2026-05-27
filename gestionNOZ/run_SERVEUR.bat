@echo off
title SERVEUR Planning Global
chcp 65001 >nul

cd /d "%~dp0"

echo =====================================================
echo   SERVEUR PLANNING — ne pas fermer cette fenetre !
echo   Pour trouver votre IP : ouvrez un CMD et tapez :
echo      ipconfig
echo   Cherchez "Adresse IPv4" sous votre carte reseau.
echo =====================================================
echo.

if not exist bin mkdir bin
set POI_CP=app\jar\poi-bin-5.2.3\*;app\jar\poi-bin-5.2.3\lib\*;app\jar\poi-bin-5.2.3\ooxml-lib\*

echo.
echo Lancement du serveur...
REM
REM  Note : pas besoin de -Djava.awt.headless=true ici car
REM  on est sous Windows avec un affichage.
REM  Sur Linux/serveur sans ecran, utilisez run_SERVEUR_headless.sh
REM
java -cp "bin;%POI_CP%" app.ServeurHTTP
pause