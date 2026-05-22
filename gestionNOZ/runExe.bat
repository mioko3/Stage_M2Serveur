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
set POI_CP=app\jar\poi-bin-5.2.3\*;app\jar\poi-bin-5.2.3\lib\*;app\jar\poi-bin-5.2.3\ooxml-lib\*

if exist %BIN%    rmdir /s /q %BIN%
if exist %OUT%    rmdir /s /q %OUT%
if exist %EXEOUT% rmdir /s /q %EXEOUT%

mkdir %BIN%
mkdir %OUT%
mkdir %EXEOUT%
mkdir %OUT%\libs

echo.
echo ===== COMPILATION =====
javac -encoding UTF-8 -cp "%POI_CP%" -d %BIN% @compile.list
if errorlevel 1 ( echo ERREUR COMPILATION & pause & exit /b )

echo.
echo ===== EXTRACTION DES LIBS DANS BIN =====
REM Extraire tous les JARs POI dans bin pour créer un fat-jar
pushd %BIN%
for %%f in (..\app\jar\poi-bin-5.2.3\*.jar) do jar xf "%%f"
for %%f in (..\app\jar\poi-bin-5.2.3\lib\*.jar) do jar xf "%%f"
for %%f in (..\app\jar\poi-bin-5.2.3\ooxml-lib\*.jar) do jar xf "%%f"
popd

REM Supprimer les manifestes parasites des JARs extraits
if exist %BIN%\META-INF rmdir /s /q %BIN%\META-INF

echo.
echo ===== FAT-JAR SERVEUR =====
jar --create --file %OUT%\ServeurHTTP.jar --main-class app.ServeurHTTP -C %BIN% .

echo.
echo ===== FAT-JAR CLIENT =====
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
  --java-options "-Xmx512m"

echo.
echo ===== EXE CLIENT =====
jpackage ^
  --type app-image ^
  --name ControleurClient ^
  --input %OUT% ^
  --main-jar ControleurClient.jar ^
  --main-class app.ControleurClient ^
  --dest %EXEOUT% ^
  --java-options "-Xmx512m"

echo.
echo ===== FIN =====
echo EXE disponibles dans %EXEOUT%
pause