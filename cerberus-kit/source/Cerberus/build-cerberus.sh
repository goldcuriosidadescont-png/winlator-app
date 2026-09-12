#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./gradlew clean assembleRelease
APK=$(find app/build/outputs/apk/release -type f -name '*.apk' | head -n1)
[ -n "$APK" ] || { echo 'No release APK produced' >&2; exit 1; }
echo "$APK"
sha256sum "$APK"
if command -v apksigner >/dev/null 2>&1; then
  apksigner verify --verbose --print-certs "$APK"
fi
