@echo off
title SERVEUR Planning Global
chcp 65001 >nul

REM ══════════════════════════════════════════════════════════
REM  run_SERVEUR.bat — CORRECTIF #1 et #4
REM ══════════════════════════════════════════════════════════
REM
REM  CORRECTIF #1 — On force le répertoire de travail
REM  sur le dossier où se trouve ce .bat, PAS sur le dossier
REM  depuis lequel il a été lancé.
REM
REM  AVANT : si on lançait le .bat depuis un autre dossier
REM  (ex: glisser-déposer sur une invite de commandes ouverte
REM  dans C:\), le programme ne trouvait pas app/data/.
REM
REM  APRÈS : "%~dp0" = dossier du .bat lui-même.
REM  "cd /d" change aussi le lecteur (C:, D:, etc.).
REM  Combiné avec CheminApp.java, les chemins sont doublement
REM  sécurisés.
REM ══════════════════════════════════════════════════════════

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
    echo.
    echo ERREUR de compilation. Verifiez compile.list.
    echo Conseil : chaque fichier .java du projet doit y figurer.
    pause
    exit /b 1
)

echo.
echo Lancement du serveur...
REM
REM  Note : pas besoin de -Djava.awt.headless=true ici car
REM  on est sous Windows avec un affichage.
REM  Sur Linux/serveur sans ecran, utilisez run_SERVEUR_headless.sh
REM
java -cp "bin;%POI_CP%" app.ServeurHTTP
pause