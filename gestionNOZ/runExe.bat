@echo off
setlocal
chcp 65001 >nul
title BUILD EXE — Planning Global Futura
cd /d "%~dp0"

echo ==================================================
echo   BUILD FAT-JAR + EXE (Serveur + Client)
echo ==================================================

set BIN=bin
set OUT=dist
set EXEOUT=output
set TOOLS=tools_build
set POI_CP=app\jar\poi-bin-5.2.3\*;app\jar\poi-bin-5.2.3\lib\*;app\jar\poi-bin-5.2.3\ooxml-lib\*

REM ── Nettoyage ──────────────────────────────────────
if exist %BIN%    rmdir /s /q %BIN%
if exist %OUT%    rmdir /s /q %OUT%
if exist %EXEOUT% rmdir /s /q %EXEOUT%
if exist %TOOLS%  rmdir /s /q %TOOLS%

mkdir %BIN%
mkdir %OUT%
mkdir %EXEOUT%
mkdir %TOOLS%

REM ══════════════════════════════════════════════════
REM  ETAPE 1 — Compilation du projet
REM ══════════════════════════════════════════════════
echo.
echo ===== [1/5] COMPILATION DU PROJET =====
javac -encoding UTF-8 -cp "%POI_CP%" -d %BIN% @compile.list
if errorlevel 1 (
    echo.
    echo ERREUR : La compilation a echoue. Corrigez les erreurs ci-dessus.
    pause
    exit /b 1
)
echo OK

REM ══════════════════════════════════════════════════
REM  ETAPE 2 — Compilation de MergeFatJar
REM ══════════════════════════════════════════════════
echo.
echo ===== [2/5] COMPILATION DE MergeFatJar =====

REM Ecrire MergeFatJar.java dans tools_build
(
echo import java.io.*;
echo import java.nio.file.*;
echo import java.util.*;
echo import java.util.jar.*;
echo import java.util.zip.*;
echo.
echo public class MergeFatJar {
echo     public static void main^(String[] args^) throws Exception {
echo         String output    = args[0];
echo         String mainClass = args[1];
echo         Map^<String, StringBuilder^> services = new LinkedHashMap^<^>^(^);
echo         Map^<String, byte[]^>        entries  = new LinkedHashMap^<^>^(^);
echo         for ^(int i = 2; i ^< args.length; i++^) {
echo             File f = new File^(args[i]^);
echo             if ^(!f.exists^(^)^) { System.out.println^("SKIP : " + f^); continue; }
echo             if ^(f.isDirectory^(^)^) {
echo                 Files.walk^(f.toPath^(^)^).filter^(Files::isRegularFile^).forEach^(p -^> {
echo                     String rel = f.toPath^(^).relativize^(p^).toString^(^).replace^('\\', '/'^);
echo                     if ^(rel.startsWith^("META-INF/"^)^) return;
echo                     try { entries.putIfAbsent^(rel, Files.readAllBytes^(p^)^); }
echo                     catch ^(IOException e^) { throw new UncheckedIOException^(e^); }
echo                 }^);
echo             } else {
echo                 try ^(JarInputStream jis = new JarInputStream^(new FileInputStream^(f^)^)^) {
echo                     JarEntry entry;
echo                     while ^(^(entry = jis.getNextJarEntry^(^)^) != null^) {
echo                         String name = entry.getName^(^);
echo                         if ^(entry.isDirectory^(^)^)               continue;
echo                         if ^(name.startsWith^("META-INF/MANIFEST"^)^) continue;
echo                         if ^(name.equals^("META-INF/"^)^)           continue;
echo                         if ^(name.startsWith^("META-INF/maven"^)^)   continue;
echo                         if ^(name.startsWith^("META-INF/LICENSE"^)^)  continue;
echo                         if ^(name.startsWith^("META-INF/license"^)^)  continue;
echo                         if ^(name.startsWith^("META-INF/NOTICE"^)^)   continue;
echo                         if ^(name.startsWith^("META-INF/notice"^)^)   continue;
echo                         byte[] data = jis.readAllBytes^(^);
echo                         if ^(name.startsWith^("META-INF/services/"^)^) {
echo                             services.computeIfAbsent^(name, k -^> new StringBuilder^(^)^)
echo                                     .append^(new String^(data^)^).append^('\n'^);
echo                         } else {
echo                             entries.putIfAbsent^(name, data^);
echo                         }
echo                     }
echo                 }
echo             }
echo         }
echo         Manifest manifest = new Manifest^(^);
echo         manifest.getMainAttributes^(^).put^(Attributes.Name.MANIFEST_VERSION, "1.0"^);
echo         manifest.getMainAttributes^(^).put^(Attributes.Name.MAIN_CLASS, mainClass^);
echo         try ^(JarOutputStream jos = new JarOutputStream^(new FileOutputStream^(output^), manifest^)^) {
echo             for ^(Map.Entry^<String, byte[]^> e : entries.entrySet^(^)^) {
echo                 try {
echo                     jos.putNextEntry^(new ZipEntry^(e.getKey^(^)^)^);
echo                     jos.write^(e.getValue^(^)^);
echo                     jos.closeEntry^(^);
echo                 } catch ^(ZipException ze^) {}
echo             }
echo             for ^(Map.Entry^<String, StringBuilder^> e : services.entrySet^(^)^) {
echo                 String[] lines = e.getValue^(^).toString^(^).split^("\\r?\\n"^);
echo                 Set^<String^> seen = new LinkedHashSet^<^>^(^);
echo                 for ^(String l : lines^) { String t = l.trim^(^); if ^(!t.isEmpty^(^) ^&^& !t.startsWith^("#"^)^) seen.add^(t^); }
echo                 if ^(seen.isEmpty^(^)^) continue;
echo                 try {
echo                     jos.putNextEntry^(new ZipEntry^(e.getKey^(^)^)^);
echo                     jos.write^(String.join^("\n", seen^).getBytes^(^)^);
echo                     jos.closeEntry^(^);
echo                 } catch ^(ZipException ze^) {}
echo             }
echo         }
echo         System.out.println^("Fat-jar cree : " + output^);
echo     }
echo }
) > %TOOLS%\MergeFatJar.java

javac -d %TOOLS% %TOOLS%\MergeFatJar.java
if errorlevel 1 (
    echo ERREUR : Compilation de MergeFatJar echouee.
    pause
    exit /b 1
)
echo OK

REM ══════════════════════════════════════════════════
REM  ETAPE 3 — Creation des Fat-JARs
REM  Les JARs POI sont listes un par un pour eviter
REM  les problemes de wildcard sous Windows.
REM  Si un JAR est absent, il est simplement ignore.
REM ══════════════════════════════════════════════════
echo.
echo ===== [3/5] FAT-JAR SERVEUR =====
java -cp %TOOLS% MergeFatJar %OUT%\ServeurHTTP.jar app.ServeurHTTP ^
  app\jar\poi-bin-5.2.3\poi-5.2.3.jar ^
  app\jar\poi-bin-5.2.3\poi-ooxml-5.2.3.jar ^
  app\jar\poi-bin-5.2.3\poi-ooxml-full-5.2.3.jar ^
  app\jar\poi-bin-5.2.3\poi-ooxml-lite-5.2.3.jar ^
  app\jar\poi-bin-5.2.3\lib\commons-codec-1.15.jar ^
  app\jar\poi-bin-5.2.3\lib\commons-collections4-4.4.jar ^
  app\jar\poi-bin-5.2.3\lib\commons-io-2.11.0.jar ^
  app\jar\poi-bin-5.2.3\lib\commons-math3-3.6.1.jar ^
  app\jar\poi-bin-5.2.3\lib\log4j-api-2.18.0.jar ^
  app\jar\poi-bin-5.2.3\lib\SparseBitSet-1.2.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\commons-compress-1.21.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\commons-logging-1.2.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\curvesapi-1.07.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\log4j-api-2.18.0.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\xmlbeans-5.1.1.jar ^
  %BIN%
if errorlevel 1 ( echo ERREUR Fat-JAR Serveur & pause & exit /b 1 )


echo.
echo ===== [3/5] FAT-JAR CLIENT =====
java -cp %TOOLS% MergeFatJar %OUT%\ControleurClient.jar app.ControleurClient ^
  app\jar\poi-bin-5.2.3\poi-5.2.3.jar ^
  app\jar\poi-bin-5.2.3\poi-ooxml-5.2.3.jar ^
  app\jar\poi-bin-5.2.3\poi-ooxml-full-5.2.3.jar ^
  app\jar\poi-bin-5.2.3\poi-ooxml-lite-5.2.3.jar ^
  app\jar\poi-bin-5.2.3\lib\commons-codec-1.15.jar ^
  app\jar\poi-bin-5.2.3\lib\commons-collections4-4.4.jar ^
  app\jar\poi-bin-5.2.3\lib\commons-io-2.11.0.jar ^
  app\jar\poi-bin-5.2.3\lib\commons-math3-3.6.1.jar ^
  app\jar\poi-bin-5.2.3\lib\log4j-api-2.18.0.jar ^
  app\jar\poi-bin-5.2.3\lib\SparseBitSet-1.2.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\commons-compress-1.21.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\commons-logging-1.2.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\curvesapi-1.07.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\log4j-api-2.18.0.jar ^
  app\jar\poi-bin-5.2.3\ooxml-lib\xmlbeans-5.1.1.jar ^
  %BIN%
if errorlevel 1 ( echo ERREUR Fat-JAR Client & pause & exit /b 1 )


REM ══════════════════════════════════════════════════
REM  ETAPE 4 — jpackage
REM ══════════════════════════════════════════
echo.
echo ===== [4/5] EXE SERVEUR =====
jpackage ^
  --type app-image ^
  --name ServeurHTTP ^
  --input %OUT% ^
  --main-jar ServeurHTTP.jar ^
  --main-class app.ServeurHTTP ^
  --dest %EXEOUT% ^
  --java-options "-Xmx512m"
if errorlevel 1 ( echo ERREUR jpackage Serveur & pause & exit /b 1 )


echo.
echo ===== [5/5] EXE CLIENT =====
jpackage ^
  --type app-image ^
  --name ControleurClient ^
  --input %OUT% ^
  --main-jar ControleurClient.jar ^
  --main-class app.ControleurClient ^
  --dest %EXEOUT% ^
  --java-options "-Xmx512m"
if errorlevel 1 ( echo ERREUR jpackage Client & pause & exit /b 1 )



REM ══════════════════════════════════════════════════
REM  ETAPE 5 — Copie des donnees a cote des EXE
REM  Les chemins relatifs (app/data/...) doivent exister
REM  dans le dossier de l'exe pour que le programme
REM  retrouve les JSON et les fichiers de reference.
REM ══════════════════════════════════════════════════
echo.
echo ===== [5/5] COPIE DES DONNEES =====
xcopy /E /I /Y app\data %EXEOUT%\ServeurHTTP\app\data >nul
xcopy /E /I /Y app\data %EXEOUT%\ControleurClient\app\data >nul
echo OK

REM ── Nettoyage des temporaires ──────────────────────
if exist %TOOLS% rmdir /s /q %TOOLS%

echo.
echo ==================================================
echo   BUILD TERMINE
echo ==================================================
echo.
echo   Serveur : %EXEOUT%\ServeurHTTP\ServeurHTTP.exe
echo.
echo   IMPORTANT : lancez les .exe depuis leur dossier,
echo   pas en double-cliquant depuis l'explorateur,
echo   sinon les chemins relatifs (app/data) seront faux.
echo   Pour lancer correctement : clic droit sur le .exe
echo   puis "Ouvrir" depuis son dossier.
echo.
pause