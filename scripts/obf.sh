#!/bin/bash
# Obfuscates a built REDUXion jar with ProGuard.
# Usage: scripts/obf.sh <mc-version> <intermediary-minecraft-jar>
#   <mc-version>                 1.21.1 or 1.21.11
#   <intermediary-minecraft-jar> fabric-loom "minecraft-merged-intermediary" jar
#                                (under ~/.gradle/caches/fabric-loom)
# Env:
#   PROGUARD_DIR  folder with ProGuard jars (default: /tmp/pg)
# Expects mc-<ver>/build/deps.txt (runtime classpath list) produced by the build.
set -e
V=$1; INT=$2
if [ -z "$V" ] || [ -z "$INT" ]; then
  echo "Usage: $0 <mc-version> <intermediary-jar>" >&2; exit 1
fi
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PG=${PROGUARD_DIR:-/tmp/pg}
IN=$ROOT/mc-$V/build/libs/reduxion-mc$V-1.0.0.jar
OUT=$ROOT/mc-$V/build/libs/reduxion-mc$V-1.0.0-obf.jar
LIBS="$JAVA_HOME/jmods/java.base.jmod:$JAVA_HOME/jmods/java.desktop.jmod"
LIBS="$LIBS:$INT"
while read -r j; do case "$j" in *minecraft-merged*|*minecraft-named*) ;; *) LIBS="$LIBS:$j";; esac; done < "$ROOT/mc-$V/build/deps.txt"
CP=$(ls "$PG"/*.jar | tr '\n' ':')
java -cp "$CP" proguard.ProGuard @"$ROOT/proguard/reduxion.pro" \
  -injars "$IN" -outjars "$OUT" \
  $(echo $LIBS | tr ':' '\n' | sed 's/^/-libraryjars /' | tr '\n' ' ') \
  -printmapping "$ROOT/mc-$V/build/libs/mapping-$V.txt" 2>&1 | grep -iv "^Note\|^Warning: .*references" | tail -15
echo "OUT size: $(stat -c%s "$OUT" 2>/dev/null) bytes"
