#!/usr/bin/env bash
# Standalone test build only. No ADB commands or device modifications.
set -euo pipefail
cd "$(dirname "$0")"
SDK="$(printenv ANDROID_HOME 2>/dev/null || true)"
if [ -z "$SDK" ]; then SDK="$(printenv ANDROID_SDK_ROOT 2>/dev/null || true)"; fi
if [ -z "$SDK" ]; then echo "Set ANDROID_HOME to an Android SDK" >&2; exit 2; fi
AJ="$SDK/platforms/android-34/android.jar"
BT="$SDK/build-tools/34.0.0"
for f in "$AJ" "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$BT/core-lambda-stubs.jar"; do
    if [ ! -e "$f" ]; then echo "Missing SDK tool: $f" >&2; exit 2; fi
done
for program in javac keytool zip base64; do
    command -v "$program" >/dev/null || { echo "Missing: $program" >&2; exit 2; }
done
rm -rf out
mkdir -p out/classes out/gen
"$BT/aapt2" compile --dir app/res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$AJ" --manifest app/AndroidManifest.xml --java out/gen \
  --min-sdk-version 28 --target-sdk-version 33 out/res.zip
javac -nowarn -Xlint:-options -source 8 -target 8 \
  -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -classpath "$AJ" -encoding UTF-8 \
  -d out/classes $(find app/src out/gen -name '*.java')
"$BT/d8" --release --min-api 28 --lib "$AJ" --output out $(find out/classes -name '*.class')
(cd out && zip -qj base.apk classes.dex)
"$BT/zipalign" -f -p 4 out/base.apk out/aligned.apk
# Fresh temporary signing key for each test build, never a public release signing identity.
PASSWORD="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
keytool -genkeypair -keystore out/temporary-test-only.jks -alias preview -keyalg RSA \
  -keysize 2048 -validity 365 -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Unified TV test build" -noprompt >/dev/null 2>&1
"$BT/apksigner" sign --ks out/temporary-test-only.jks --ks-pass "pass:$PASSWORD" \
  --key-pass "pass:$PASSWORD" --out out/UnifiedTV-Preview.apk out/aligned.apk
"$BT/apksigner" verify --verbose out/UnifiedTV-Preview.apk
"$BT/aapt2" dump badging out/UnifiedTV-Preview.apk | grep "^package: name='io.unifiedtv.preview'"
if grep -q 'android.intent.category.HOME' app/AndroidManifest.xml; then
    echo "ERROR: preview must not register itself as Android Home" >&2
    exit 3
fi
echo "Test APK ready: out/UnifiedTV-Preview.apk (no TV changed)"
