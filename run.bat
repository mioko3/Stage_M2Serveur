@echo off
title Planning Global Futura - PAM
chcp 65001 >nul

cd /d "%~dp0"

echo "==========================================="
echo    "Planning Global Futura - PAM S07/2026"
echo "==========================================="
echo.

echo  Etape 1 : Compilation + lancement
echo [1/1] Compilation et lancement...
if not exist bin mkdir bin
set POI_CP=app\jar\poi-bin-5.2.3\*;app\jar\poi-bin-5.2.3\lib\*;app\jar\poi-bin-5.2.3\ooxml-lib\*
javac -encoding UTF-8 -cp "%POI_CP%" -d bin @compile.list
if errorlevel 1 (
    echo Erreur de compilation.
    pause
    exit /b 1
)
echo Lancement...
java -cp "bin;%POI_CP%" app.Controleur
pause
