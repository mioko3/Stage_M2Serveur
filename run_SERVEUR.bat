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

echo Compilation...
javac -encoding UTF-8 -cp "%POI_CP%" -d bin @compile.list
if errorlevel 1 (
    echo ERREUR de compilation.
    pause
    exit /b 1
)
echo.
echo Lancement du serveur...
java -cp "bin;%POI_CP%" app.ServeurHTTP
pause
