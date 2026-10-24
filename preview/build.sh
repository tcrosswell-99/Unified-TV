#!/usr/bin/env bash
# Pinned Gradle wrapper, isolated preview. No device operations.
set -euo pipefail
cd "$(dirname "$0")"
bash ../launcher/gradlew -p . --no-daemon testDebugUnitTest lintDebug assembleDebug
mkdir -p out
cp app/build/outputs/apk/debug/app-debug.apk out/UnifiedTV-Preview.apk
BT="${ANDROID_HOME:?Set ANDROID_HOME}/build-tools/35.0.0"
"$BT/apksigner" verify --verbose out/UnifiedTV-Preview.apk
"$BT/aapt2" dump badging out/UnifiedTV-Preview.apk | grep "^package: name='io.unifiedtv.preview'"
"$BT/aapt2" dump xmltree out/UnifiedTV-Preview.apk --file AndroidManifest.xml > out/manifest.txt
# HOME only on the switched-off, owner-opt-in entry (spec 29.3); a fresh install must never take over Home.
python3 tools/home_check.py out/manifest.txt
(cd out && sha256sum UnifiedTV-Preview.apk > SHA256SUMS.txt)
