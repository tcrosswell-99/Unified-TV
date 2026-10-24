# Unified TV first Android preview

**Status: prototype source only until CI build and a real-TV test pass.** This is not a Unified TV release.

This branch introduces a **standalone preview activity** in the inherited CleanYourTV UI APK. It does *not* register itself as Android HOME and contains no ADB, debloating, accessibility interception, camera control, VPN tunnelling or local network discovery. The inherited HOME activity and system restoration scripts are left unchanged.

## What this first iteration demonstrates

- Neutral scenic background with a single restrained glass dock and 24-hour UK clock.
- Minimal top-right status group labelled \`VPN ?\`: no pretend tunnel verification or false country flag.
- Service-related, locally drawn gradient atmosphere after focus settles, rather than wrongly leaving an unrelated beach behind Netflix.
- Initial Sony/TCL app choices: SmartTube, Netflix, Disney+, BBC, Live TV and Music. Sony gets an extra Xbox **placeholder**, but wake/input switching is *not* wired in.
- Remote-first controls: Left/Right focus; Up or Menu opens installed Apps; OK adds/removes a shortcut in Apps; on a Home tile hold OK for Move or Remove; Left/Right moves, OK saves, Back cancels.
- Saved per-device dock order and pinned installed apps through local Android preferences. Removing a shortcut does not uninstall the app.
- Third-party apps launch only when genuinely installed. The Live TV shortcut is **deliberately blocked** pending verified NordVPN fail-closed protection.

## What is deliberately not yet working

Ring video/alerts, Alexa reminders, Home Assistant, Synology NAS artwork and VLC playback, Samsung Tizen/Art Mode, Amazon Music custom controls, GPT voice, dynamic verified VPN country, personal photo settings, Sony Xbox control and actual per-app cover artwork. These remain requirements under the main product specification and must never be advertised as shipping today.

The dock currently uses locally generated material and an abstract coastal fallback. If a redistribution-cleared \`launcher-ui/app/assets/unified-preview-coast.jpg\` exists, the preview uses that as neutral wallpaper; it is not currently bundled in the APK. App-focused backdrops are original local gradients until authorised content imagery is integrated.

## Compiling without touching a television

The GitHub Actions \`unified-preview\` job invokes \`launcher-ui/build.sh\` to compile the Java app and packages an **unverified, test-only** APK as a short-lived workflow artifact. The existing script generates a signing key with a documented fixed password; **never treat its output as a secure production-signed release**. Use a separate secret signing key and distinct application identity before public distribution.

The inherited Android package currently shares \`io.cleanyourtv.ui\` with its parent. Installing it over a version signed by another author might fail or replace local app data. Do not run automated install scripts, set it as HOME, disable packages or enable ADB unless the exact device and rollback plan have been reviewed.

## TV evaluation checklist

1. Confirm the TV model, Android version, free storage, firmware and original Home behaviour locally.
2. Install this as a **manual test application** only after the CI APK compiles and is reviewed. Do not change the stock launcher.
3. Test the D-pad for 100 rapid focus presses, long-press edit modes, Cancel, app discovery and saved layout after returning from playback.
4. Measure memory consumption, CPU/frame stalls and warm-open time on the real TCL. Compare the same interactions on the Sony.
5. Verify that selecting Live TV never launches ReamSmartz without a verified VPN.
6. Uninstall the preview application if it is not suitable; nothing in this prototype disables TV system packages.

The baseline design and next steps are recorded in the [product spec](PRODUCT_SPEC.md), [design system](DESIGN_SYSTEM.md) and [roadmap](ROADMAP.md).
