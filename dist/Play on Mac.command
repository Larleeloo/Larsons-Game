#!/bin/bash
# Larson's Game - double-click this file to play (macOS).
# It needs Java 21 or newer: https://adoptium.net/temurin/releases/?version=21
cd "$(dirname "$0")" || exit 1

pause_and_exit() {
    echo
    read -r -n 1 -p "Press any key to close this window." _
    echo
    exit "$1"
}

if [ ! -f larsons-game.jar ]; then
    echo
    echo "  Unzip the game first (double-click Larsons-Game.zip), then double-click"
    echo "  \"Play on Mac\" inside the Larsons-Game folder it makes."
    pause_and_exit 1
fi

# The major version of a java ("21" for 21.0.5, "1" for 1.8), or nothing.
java_major() {
    "$1" -version 2>&1 | awk -F'"' '/ version "/ { split($2, v, "."); print v[1]; exit }'
}

# A Java 21 or newer: on an Apple Silicon Mac (M1, M2 ...) the Apple Silicon
# one first - an Intel Java runs there translated by Rosetta, much slower -
# then any installed one macOS knows of, JAVA_HOME's, Homebrew's, then the one
# on the PATH.
apple_silicon=""
[ "$(sysctl -in hw.optional.arm64 2>/dev/null)" = "1" ] && apple_silicon="yes"
JAVA=""
for candidate in \
    "${apple_silicon:+$(/usr/libexec/java_home -a arm64 -v 21+ 2>/dev/null)/bin/java}" \
    "$(/usr/libexec/java_home -v 21+ 2>/dev/null)/bin/java" \
    "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
    /opt/homebrew/opt/openjdk/bin/java /opt/homebrew/opt/openjdk@21/bin/java \
    /usr/local/opt/openjdk/bin/java /usr/local/opt/openjdk@21/bin/java \
    "$(command -v java)"; do
    [ -n "$candidate" ] && [ -x "$candidate" ] || continue
    # /usr/bin/java is only a stub when no Java is installed: java_home has the answer then
    if [ "$candidate" = /usr/bin/java ] && ! /usr/libexec/java_home >/dev/null 2>&1; then continue; fi
    major="$(java_major "$candidate")"
    if [ -n "$major" ] && [ "$major" -ge 21 ] 2>/dev/null; then JAVA="$candidate"; break; fi
done

if [ -z "$JAVA" ]; then
    arch="x64"; [ "$(uname -m)" = "arm64" ] && arch="aarch64"
    echo
    echo "  Larson's Game needs Java 21 (or newer), and it is not on this Mac yet."
    echo
    echo "  1. A web page opens now: download \"Temurin 21\" for macOS - the .pkg file - and install it."
    echo "  2. Then double-click \"Play on Mac\" again."
    open "https://adoptium.net/temurin/releases/?version=21&os=mac&arch=$arch&package=jre"
    pause_and_exit 1
fi

echo "Starting Larson's Game (Java: $JAVA)"
if [ -n "$apple_silicon" ] && "$JAVA" -XshowSettings:properties -version 2>&1 | grep -q 'os.arch = x86_64'; then
    echo
    echo "  This Java is the Intel version: on this Mac it runs translated by Rosetta, and the game is"
    echo "  much slower. For the best speed, install Temurin 21 for Apple Silicon (aarch64):"
    echo "  https://adoptium.net/temurin/releases/?version=21&os=mac&arch=aarch64&package=jre"
fi
echo "This window shows what the game is doing. Leave it open while you play."
echo "If the game ever freezes, it writes what it was doing to a hang-....txt file in"
echo "  $(pwd)/logs"
echo "after 10 seconds - please send Larson that file."
echo
"$JAVA" -jar larsons-game.jar "$@"
status=$?
if [ $status -ne 0 ]; then
    echo
    echo "  The game stopped with an error."
    echo "  Please send Larson the file logs/latest.log (and any crash-....txt or hang-....txt"
    echo "  file beside it) from the game's folder:"
    echo "  $(pwd)/logs"
    pause_and_exit $status
fi
exit 0
