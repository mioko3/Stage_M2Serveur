#!/bin/bash
cd "$(dirname "$0")"

echo "=== Planning Global Futura - PAM S07/2026 ==="
echo ""

echo "[1/1] Compilation + lancement..."
POI_CP="app/jar/poi-bin-5.2.3/*:app/jar/poi-bin-5.2.3/lib/*:app/jar/poi-bin-5.2.3/ooxml-lib/*"
mkdir -p bin
javac -encoding UTF-8 -cp "$POI_CP" -d bin @compile.list || exit 1

echo "Lancement..."
java -cp "bin:$POI_CP" app.Controleur
