#!/usr/bin/env bash
# ------------------------------------------------------------------
# Baut build/VanillaPlusTweaks.jar.
#
# Kompiliert wird gegen die Bibliotheken eines echten Paper-26.3-Servers – genau die Versionen,
# mit denen das Plugin spaeter laeuft. Paper legt sie beim ersten Start in "libraries/" ab.
#
# Voraussetzungen: JDK 21 oder neuer (javac, jar), ein einmal gestarteter Paper-26.3-Server
# und PlaceholderAPI im plugins-Ordner dieses Servers.
#
# Aufruf: ./bauen.sh <pfad-zum-paper-server>
# ------------------------------------------------------------------
set -euo pipefail

SERVER="${1:?Aufruf: ./bauen.sh <pfad-zum-paper-server>}"
HIER="$(cd "$(dirname "$0")" && pwd)"
BUILD="$HIER/build"

CP="$(find "$SERVER/libraries" -name '*.jar' | tr '\n' ':')"
CP="$CP$(ls "$SERVER"/plugins/PlaceholderAPI-*.jar | head -n 1)"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/jar"

javac --release 21 -encoding UTF-8 -Xlint:all,-classfile,-processing \
      -cp "$CP" -d "$BUILD/classes" $(find "$HIER/src" -name '*.java')

cp -r "$BUILD/classes/." "$BUILD/jar/"
cp -r "$HIER/res/." "$BUILD/jar/"
jar cf "$BUILD/VanillaPlusTweaks.jar" -C "$BUILD/jar" .

echo "Fertig: $BUILD/VanillaPlusTweaks.jar"
