#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
command -v javac >/dev/null 2>&1 || { echo 'Missing JDK 17 (javac).' >&2; exit 1; }
command -v gradle >/dev/null 2>&1 || { echo 'Install Gradle 8.11.1 and add gradle/bin to PATH.' >&2; exit 1; }
if ! gradle --version | rg '^Gradle 8\.11\.1$' >/dev/null 2>&1; then
    gradle --version | grep -q '^Gradle 8\.11\.1$' || { echo 'Use Gradle 8.11.1.' >&2; exit 1; }
fi
test -f app/src/main/assets/orbit.c86 || { echo 'Missing demo. Run python3 tools/build_guest.py on Linux x86_64.' >&2; exit 1; }
gradle --no-daemon :app:assembleDebug :app:lintDebug
echo 'APK: app/build/outputs/apk/debug/app-debug.apk'
