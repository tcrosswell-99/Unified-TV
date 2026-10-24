# Stage 0.5: private configuration and Windows-to-TCL test

**Pilot target: TCL television running Android TV OS 11.** The owner has already verified its IP and Wi-Fi status locally, but those identifiers must not be committed publicly. The installed casting receiver is **AirPin LITE**, not AirPro LITE.

## 1. CI comes before installation

The isolated prototype is in [pull request #5](https://github.com/tcrosswell-99/Unified-TV/pull/5), not merged. Confirm the GitHub Actions job has built the separate `io.unifiedtv.preview` Android APK and inspected its signature, permissions and manifest. **No `android.intent.category.HOME`** must be present. A GitHub source commit alone is not an APK test.

## 2. Connecting from Windows

Install official [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) on the Windows PC. TV is **Android TV 11**, so **do not assume** the Android TV 13+ Wireless debugging pairing-code menus exist.

1. On TV, open Settings > Device Preferences / System > About > Build and enable Developer options only with the owner's consent.
2. Open Developer options. Record whether **Network debugging**, **ADB debugging**, or **Wireless debugging** is actually offered and any port/address displayed.
3. If a manufacturer-supported network debugging method exists, use only its on-screen port and the owner's private TV address. There must be no LAN scan or speculative open-port sweep.
4. If only USB debugging is supported, use a verified USB route. If neither is supported, test the app by a separately approved USB/offline APK installer rather than altering firmware.
5. A Windows PowerShell helper for a user-authorised ADB connection was drafted in the unmerged experimental [pull request #5](https://github.com/tcrosswell-99/Unified-TV/pull/5). **It is not in this repository yet**, so no command here depends on it. Until it is reviewed and merged, use the official Platform Tools `adb` directly, read the model and version first, and authorise any installation explicitly. Its pairing option would apply only where the TV supports that pairing flow.

## 3. First real-TV tests

- Check launch without replacing Google TV Home; press Home to return to normal Google TV.
- Test D-pad selection, clock, genuine app names, remote Hold OK edit, Add/Move/Remove, Back cancel and persistence.
- Compare the app-selected background with the visual design. This prototype uses gradients, not completed cinematic content artwork.
- Install a separate test build only when the code passes CI. Do not disable stock TV packages or start cleanup.
- Record focus p95, RAM, cold/warm launch and long-dock readability on the actual TCL.
- Uninstall only `io.unifiedtv.preview` and turn debugging off at the end.

## 4. Future on-TV connections UI

**Settings > Connections** must have Home Assistant, NAS/Jellyfin, and optional AirPlay sections with Hostname/IP, Port, protocol, Test, Save, Forget and accurate status states. The TV remote and on-screen keyboard must suffice. Store credentials using an Android Keystore-backed solution; no private token/IP in a public APK, GitHub issue or screenshot. Test invalid endpoints and a disconnected LAN before moving past Stage 0.5.

## 5. AirPin LITE experiment

Inspect the real installed app package, licence and supported auto-start settings. Test reboot, idle receiver availability, iPhone discovery, AirPlay and performance. Do not show a confirmed AirPlay glyph unless a supported state check exists. AirPin stays outside the main entertainment dock by default.

**Stage completion:** CI success, isolated package, approved connection/install path, reliable remote usage, clear rollback, private configuration tested, manufacturer Home untouched.