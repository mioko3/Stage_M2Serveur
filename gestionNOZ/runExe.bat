@echo off
setlocal
title BUILD CLIENT + SERVEUR

cd /d "%~dp0"

echo ==========================
echo   BUILD JAR + EXE
echo ==========================

set BIN=bin
set OUT=dist
set EXEOUT=output

if exist %BIN% rmdir /s /q %BIN%
if exist %OUT% rmdir /s /q %OUT%
if exist %EXEOUT% rmdir /s /q %EXEOUT%

mkdir %BIN%
mkdir %OUT%
mkdir %EXEOUT%

echo.
echo ===== CLASSPATH LIBS =====
set POI_CP=app\jar\poi-bin-5.2.3\*;app\jar\poi-bin-5.2.3\lib\*;app\jar\poi-bin-5.2.3\ooxml-lib\*

echo.
echo ===== COMPILATION =====
javac -encoding UTF-8 -cp "%POI_CP%" -d %BIN% @compile.list

if errorlevel 1 (
    echo ERREUR COMPILATION
    pause
    exit /b
)

echo.
echo ===== JAR SERVEUR =====
jar --create --file %OUT%\ServeurHTTP.jar --main-class app.ServeurHTTP -C %BIN% .

echo.
echo ===== JAR CLIENT =====
jar --create --file %OUT%\ControleurClient.jar --main-class app.ControleurClient -C %BIN% .

echo.
echo ===== EXE SERVEUR =====
jpackage ^
--type app-image ^
--name ServeurHTTP ^
--input %OUT% ^
--main-jar ServeurHTTP.jar ^
--main-class app.ServeurHTTP ^
--dest %EXEOUT% ^
--java-options "-Xmx512m" ^
--java-options "-cp app/jar/poi-bin-5.2.3/*;app/jar/poi-bin-5.2.3/lib/*;app/jar/poi-bin-5.2.3/ooxml-lib/*"

echo.
echo ===== EXE CLIENT =====
jpackage ^
--type app-image ^
--name ControleurClient ^
--input %OUT% ^
--main-jar ControleurClient.jar ^
--main-class app.ControleurClient ^
--dest %EXEOUT% ^
--java-options "-Xmx512m" ^
--java-options "-cp app/jar/poi-bin-5.2.3/*;app/jar/poi-bin-5.2.3/lib/*;app/jar/poi-bin-5.2.3/ooxml-lib/*"

echo.
echo ===== FIN =====
echo EXE disponibles dans %EXEOUT%
pause