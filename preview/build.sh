#!/usr/bin/env bash
# Pinned Gradle wrapper, isolated preview. No device operations.
# Builds both apps (docs/SIGNING.md): the preview (io.unifiedtv.preview, no HOME entry at all) and the launcher
# (io.unifiedtv.launcher, an opt-in HOME entry), plus a launcher review build (.devicecheck) that installs beside both.
set -euo pipefail
cd "$(dirname "$0")"
bash ../launcher/gradlew -p . --no-daemon \
  testPreviewDebugUnitTest testLauncherDebugUnitTest lintPreviewDebug lintLauncherDebug \
  assemblePreviewDebug assembleLauncherDebug assembleLauncherDeviceCheck
mkdir -p out
cp app/build/outputs/apk/preview/debug/app-preview-debug.apk out/UnifiedTV-Preview.apk
cp app/build/outputs/apk/launcher/debug/app-launcher-debug.apk out/UnifiedTV-Launcher.apk
cp app/build/outputs/apk/launcher/deviceCheck/app-launcher-deviceCheck.apk out/UnifiedTV-Launcher-review.apk
BT="${ANDROID_HOME:?Set ANDROID_HOME}/build-tools/35.0.0"
check() { # apk, expected package, manifest dump name, home_check mode
  "$BT/apksigner" verify --verbose "out/$1"
  "$BT/aapt2" dump badging "out/$1" | grep "^package: name='$2'"
  "$BT/aapt2" dump xmltree "out/$1" --file AndroidManifest.xml > "out/$3"
  python3 tools/home_check.py $4 "out/$3"
}
# The preview has no HOME entry at all; the launcher's ships switched off (spec 29.3): a fresh install never takes Home.
check UnifiedTV-Preview.apk io.unifiedtv.preview manifest.txt --no-home
check UnifiedTV-Launcher.apk io.unifiedtv.launcher manifest-launcher.txt ""
check UnifiedTV-Launcher-review.apk io.unifiedtv.launcher.devicecheck manifest-launcher-review.txt ""
(cd out && sha256sum UnifiedTV-Preview.apk UnifiedTV-Launcher.apk UnifiedTV-Launcher-review.apk > SHA256SUMS.txt)
