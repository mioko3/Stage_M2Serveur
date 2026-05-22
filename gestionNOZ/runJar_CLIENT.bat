@echo off
set POI_CP=app\jar\poi-bin-5.2.3\*;app\jar\poi-bin-5.2.3\lib\*;app\jar\poi-bin-5.2.3\ooxml-lib\*
jar cfe Gestion-Lot-NOZ.jar app.ControleurClient -C bin .
java -cp "Gestion-Lot-NOZ.jar;%POI_CP%" app.ControleurClient  
pause