# Unified TV: first Windows-to-TCL test

> **Status:** first prototype, not a finished launcher or production APK. Verify GitHub Actions build before installing.

## Why a separate APK?

The first prototype is a completely independent Android package, **io.unifiedtv.preview**. Its manifest contains no \`android.intent.category.HOME\` activity or system/cleanup permission. Installing it from Windows using ADB never changes the default Google TV launcher or disables factory apps.

The earlier draft had embedded a preview activity inside the inherited CleanYourTV UI package, which itself declares Android HOME. That approach has been removed from this branch in favour of the safer isolated preview.

## What is available for testing

An illustrated neutral scenery placeholder, 24-hour UK clock, minimalist glass dock, installed-app discovery, gentle service-specific gradient backgrounds on focus, and remote-only app add/move/remove with local persistence. Press Up to open Apps; long-press OK on the dock for the edit menu. Long docks need real-TV readability testing.

**Deliberate placeholders:** the Ring override, NAS/VLC catalogue, ChatGPT voice, Alexa, VPN verification, Sony Xbox control, music playback and Samsung native Tizen integration remain unbuilt. Live TV is blocked rather than risking an unprotected ReamSmartz launch. The icon currently says **VPN ?**, not a verified connection or exit country. Dynamic artwork and a photo gallery follow later.

## Build via GitHub Actions

If the fork has no runs under [Actions](https://github.com/tcrosswell-99/Unified-TV/actions), first enable workflows for this fork in the Actions page. On an enabled run, look for the **Standalone Unified TV Preview APK (test only)** job. The \`preview/build.sh\` process compiles an Android 28+ app using SDK 34, builds a test APK, checks its own signature and rejects a HOME declaration.

Once the workflow **passes**, download its **UnifiedTV-Preview-test-only** artifact, unzip it and locate \`UnifiedTV-Preview.apk\`. Every CI job generates a temporary signature, so installing a later CI build over a previous one might fail: uninstall the preview using the helper first (only preview preferences are lost).

## Connect from Windows to TCL

1. Install [official Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) on the Windows PC and make \`adb.exe\` available on PATH.
2. On the TCL, open **Settings > System > About**, press OK repeatedly on the **Android TV OS build** entry to reveal Developer options, if available. Then enable **Wireless debugging** in Developer options, on Android versions that support it.
3. Choose **Pair device with pairing code**. Read the TV's displayed *pairing* IP:port and code. On the Wireless debugging screen, find the separate *connection* IP:port. These ports are different.
4. Open PowerShell in the Unified TV checkout and run the following, substituting the TV's real addresses:

\`\`\`powershell
# Inspect the TV without installing:
.\scripts\windows\Test-UnifiedTV.ps1 -PairAddress "192.168.1.50:37111" -ConnectAddress "192.168.1.50:41929" -InspectOnly

# Install the ZIP-extracted APK only after the CI build passes:
.\scripts\windows\Test-UnifiedTV.ps1 -ConnectAddress "192.168.1.50:41929" -ApkPath "$HOME\Downloads\UnifiedTV-Preview.apk"

# Remove only the preview if you want to return to the original state:
.\scripts\windows\Test-UnifiedTV.ps1 -ConnectAddress "192.168.1.50:41929" -RemovePreview
\`\`\`

The IP addresses shown above are **examples**, not discovered addresses. If your TCL doesn't have Wireless debugging, the connection procedure is firmware dependent; do not guess a TCP debugging port or enable permanent remote debugging.

## Acceptance test on TCL

- Confirm the detected model and Android version, and take note of normal Google TV Home performance.
- From the TV remote, test 100 rapid Left/Right presses and confirm responsive focus and final correct themed background.
- Press Up and pin an installed application. Long-press OK to Move, press Back to cancel, repeat and press OK to save.
- Remove a Home shortcut. Check the app is still installed and accessible in Apps.
- Return from Netflix or BBC to the preview and confirm the chosen layout remains.
- Check long docks with eight or more installed apps and note if labels shrink or overflow.
- Confirm Live TV shows an unavailable/protected message without launching ReamSmartz.
- Check that standard Google TV Home continues to work and uninstall preview when finished.
- Turn Wireless debugging off on the TV once testing is complete.

No network scan, Home Assistant access, Ring camera control, streaming-service credentials or bloatware modifications are performed by these scripts. All diagnostic commands act only on the explicitly paired TV.
