#!/usr/bin/env bash
# Larson's Game - run this to play (Linux): ./"Play on Linux.sh"
# It needs Java 21 or newer (OpenJDK 21 from your package manager is fine).
cd "$(dirname "$0")" || exit 1

if [ ! -f larsons-game.jar ]; then
    echo "Unzip Larsons-Game.zip first, then run \"Play on Linux.sh\" inside the Larsons-Game folder." >&2
    exit 1
fi

java_major() {
    "$1" -version 2>&1 | awk -F'"' '/ version "/ { split($2, v, "."); print v[1]; exit }'
}

# A Java 21 or newer: JAVA_HOME's, the one on the PATH, then any in /usr/lib/jvm.
JAVA=""
for candidate in "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java)" /usr/lib/jvm/*/bin/java; do
    [ -n "$candidate" ] && [ -x "$candidate" ] || continue
    major="$(java_major "$candidate")"
    if [ -n "$major" ] && [ "$major" -ge 21 ] 2>/dev/null; then JAVA="$candidate"; break; fi
done

if [ -z "$JAVA" ]; then
    cat >&2 <<'EOF'
Larson's Game needs Java 21 (or newer). Install it, then run this again:
  Ubuntu, Debian, Mint:  sudo apt install openjdk-21-jre
  Fedora:                sudo dnf install java-21-openjdk
  Arch:                  sudo pacman -S jre21-openjdk
  anything else:         https://adoptium.net/temurin/releases/?version=21
EOF
    exit 1
fi

echo "Starting Larson's Game (Java: $JAVA)"
"$JAVA" -jar larsons-game.jar "$@"
status=$?
if [ $status -ne 0 ]; then
    echo >&2
    echo "The game stopped with an error. Please send Larson the file logs/latest.log from $(pwd)/logs" >&2
fi
exit $status
