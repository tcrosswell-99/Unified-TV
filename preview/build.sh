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
if grep -q 'android.intent.category.HOME' out/manifest.txt; then echo 'Preview must not declare HOME' >&2; exit 3; fi
(cd out && sha256sum UnifiedTV-Preview.apk > SHA256SUMS.txt)
