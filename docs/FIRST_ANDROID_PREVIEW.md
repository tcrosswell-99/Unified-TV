# Windows sideload and recovery: standalone preview

This is an experimental `io.unifiedtv.preview` APK, not a supported launcher replacement. No HOME category is declared. The installed factory launcher stays in place. No package disabling or cleanup is needed.

## Obtain and verify

Download `UnifiedTV-Preview-test-only` from a passing [GitHub Actions CI run](https://github.com/tcrosswell-99/Unified-TV/actions), unzip it, and keep `UnifiedTV-Preview.apk` alongside `SHA256SUMS.txt` and `manifest.txt`. Compare the SHA-256 with `Get-FileHash .\UnifiedTV-Preview.apk -Algorithm SHA256`. The compiled manifest and build job verify the package and absence of HOME registration. Use only this reviewed artifact.

## Android TV 11: do not assume pairing screens

The reference TCL is confirmed Android TV 11. Newer Android TV wireless-pairing screens may not exist. The owner's earlier LAN connection failed; that does not block development. Do not repeat scans or guess debugging ports.

If the TV offers supported Network debugging, use only the connection address and port it exposes, on the same trusted network. If no supported connection is available, copy the APK from Windows to a USB drive and open it using a trusted file manager already on the TV. Allow that specific app to install unknown apps only for this task, approve the standalone preview installation, then revoke that permission. USB/file-manager compatibility needs checking on the actual TCL. Another option is a supported USB debugging path, if the TV exposes one. No factory reset, rooting or launcher-disable workaround is required.

## ADB route, only once an authorised connection works

Install [official Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) on Windows. Replace the variables below with the address actually shown by the TV and the expected checksum from the build artifact. These commands are instructions for the later owner-controlled installation, not commands run by development.

```powershell
$tvEndpoint = Read-Host 'TV debugging IP:port shown by the TV'
$apk = Join-Path $HOME 'Downloads\UnifiedTV-Preview.apk'
$checksum = Read-Host 'Expected SHA-256 from SHA256SUMS.txt'
.\scripts\windows\Test-UnifiedTV.ps1 -ConnectAddress $tvEndpoint -InspectOnly
.\scripts\windows\Test-UnifiedTV.ps1 -ConnectAddress $tvEndpoint -ApkPath $apk -ExpectedSha256 $checksum
# Later remove only this preview:
.\scripts\windows\Test-UnifiedTV.ps1 -ConnectAddress $tvEndpoint -RemovePreview
```

Only supply `-PairAddress` when the device actually offers pairing; Android TV 11 does not guarantee it. With an existing USB connection, use an explicit `-Serial` from `adb devices -l`. The helper refuses ambiguous targets, requires a matching APK checksum and requests an install/remove confirmation. It never sets HOME, disables packages or edits factory apps. Turn debugging off after testing.

Fresh CI builds can have different test-signing keys. A signature mismatch requires uninstalling only the old preview; its connection/profile/gallery data is lost and must be re-entered. Never uninstall factory Home. Rollback is simply exit via the remote Home button and uninstall Unified TV Preview through TV Settings > Apps, or the helper. PIN recovery in this prototype is preview data reset/uninstall; it does not affect factory Home.

## Owner test checklist

- Factory Home still opens with Home; preview launches as an ordinary app.
- Left/Right acknowledge rapid input, the final brand background matches selection, Up offers controls and Down reveals the media shelf.
- Pin, remove, restore, move-save and move-cancel work; more than six shortcuts stay legible; restart retains layout.
- Test valid/invalid HA/media endpoints, IPv6/hostname input, authorisation errors, HTTPS certificates, consented HTTP, Save/Edit/Forget and offline Home.
- Set an adult PIN, configure different restricted media users and prove Child cannot browse/search/play Adult-only items. This is not a lock on Android or external streaming apps.
- Test real films, subtitles, audio, seek, stop/reopen and server progress. Do not mark Continue Watching verified until results round-trip.
- Gallery has no controls or clock, exits on a key without launching an app, and never starts while another app plays.
- Live TV stays blocked. No Ring, VPN, Samsung, voice or cross-app overlay capability is claimed yet.

See [0.2 boundaries](PREVIEW_0_2.md) and [full specification](PRODUCT_SPEC.md). Never publish real household addresses or tokens in bug reports.
