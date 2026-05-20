@echo off
title CLIENT Planning Global
chcp 65001 >nul
cd /d "%~dp0"

echo =====================================================
echo   CLIENT Planning Global
echo   Le serveur doit etre demarre avant ce client.
echo =====================================================
echo.
set /p IP="Entrez l'adresse IP du serveur (ex: 192.168.1.15) : "
if "%IP%"=="" (
    echo Aucune IP saisie. Utilisation de localhost.
    set IP=localhost
)

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
echo Connexion au serveur %IP%:8080 ...
java -cp "bin;%POI_CP%" app.ControleurClient %IP%
pause
