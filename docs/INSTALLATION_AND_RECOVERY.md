# Installation, development builds and recovery

> **Unified TV is not ready for general installation.** This fork currently contains the existing CleanYourTV UI Android code; the future Unified TV premium interface and optional integrations are not a finished product. No Unified TV signed APK has been published.

## Understanding the existing code

- `launcher-ui/` is the inherited richer Android TV launcher developed in CleanYourTV UI.
- `launcher/` is the original smaller CleanYourTV launcher.
- `tools/` and `examples/` contain the original Android TV diagnostic, reversible package-disable and restoration utilities.
- The original instructions are archived in [the parent README](upstream/CLEANYOURTV_UI_README.md) and [CleanYourTV README](../README.cleanyourtv.md).

These source locations are for development and reference. They **do not** imply a verified Unified TV installation process.

## Before attempting any TV modification

1. Record the exact TV brand, model, firmware, OS and current package configuration privately.
2. Use an emulator or disposable test device first. If testing family hardware, capture a working stock Home, remote, sound, HDMI and network baseline.
3. Determine whether the device runs Android TV/Google TV or Samsung Tizen. **ADB package tools do not work on Tizen.**
4. Research the specific device's input switching, DRM, update, recovery and permissions behaviour. A package that seems disposable on one firmware can be critical on another.
5. Confirm you can return to the factory launcher and restore every altered package without our launcher running.
6. Keep cleanup **separate and opt-in**; never combine an app install with silent package disabling.
7. Turn off remote/wireless debugging after temporary diagnostics.

## Supported-platform reality

On Android/Google TV, a test developer can build the inherited source using the requirements in its upstream README, but the developer is responsible for understanding the scripts and permissions before installing. The optional TV cleaning process should prefer reversible disable operations and explicit consent; no permanent removal.

On Samsung Frame/Tizen, the Android APK **cannot** be installed natively. A compatible external Android streaming device is the current common-UI direction, while native Art Mode and Samsung TV Plus stay on Tizen. Automated transitions to the Ring live feed and back require specific testing and are **not** currently an installation promise.

## Failure and rollback

**How the inherited tools record and restore (Android only).** `tools/disable.sh` writes a recovery record before its first change: each listed package's previous state, the component that owned Home and the animation scales. The record is kept on the computer, outside this repository, filed under the TV's address and port and holding the TV's own identity (`ro.serialno`, or `android_id`). `tools/restore.sh` replays it. A record taken on a different TV is refused; if the TV's address has changed, restore still finds the record taken on that same TV. With **no record**, restore stops rather than re-enabling the whole list, unless `RESTORE_WITHOUT_RECORD=1` is set deliberately. Keep the list you applied, and test a restore on a spare device before relying on it.

## Optional clean-up on the TV itself (Android preview and launcher)

**Not tested on a real TV.** Unified TV's Android apps have **Settings > Updates & About > Advanced > Optional clean-up** (also reachable from TV Performance & Cleanup). It does on the TV what `tools/disable.sh` and `tools/restore.sh` do from a computer, with the same rules:

- **Adults only**, behind the PIN when one is set, **off until chosen**. Opening it and leaving changes nothing and writes nothing, and it never asks again (AT-18).
- **Tested lists only.** The app bundles the TCL BeyondTV4 lists on build RP1A.200622.001 (Android TV 11): `examples/tcl-50c725k` (tested on fingerprint `TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR10`, the owner's TCL), `examples/tcl-55c725` and `examples/tcl-65c725`, generated into `preview/app/src/main/assets/cleanup/tcl-beyondtv4.json` by `preview/tools/cleanup_lists.py` (a test keeps them in step). Any other TV gets read-only guidance and nothing can be changed (AT-32). A TCL BeyondTV4 on the same build but another firmware is offered the lists with every item marked riskier and none recommended.
- **Never offered, whatever a list says:** Android's core packages (the same patterns as `tools/recovery.py`), the stock launcher and every app that answers Home, Unified TV itself, Live TV, the tuner and HDMI inputs (including any app asking for `TV_INPUT_HARDWARE` or opening channels), the firmware updater, the TV's settings, the remote and Bluetooth, network and Wi-Fi owners and the setup wizard, keyboards, accessibility (TalkBack and its speech engine), sound and HDMI-CEC, Google Play and Play services, NordVPN and the Live TV app, any WebView (any package with "webview" in its name), the active screensaver, Google's TV recommendations while a stock launcher is on, and every package the lists' notes say to keep. Apps that only the 55C725 and 65C725 lists name are offered on the owner's firmware as *optional, your choice*, never recommended.
- **How it reaches the packages.** An ordinary app can't turn other apps off, so the page uses the TV's own network debugging (ADB on port 5555, which must already be on) through a small client written for this (`io.unifiedtv.adb.AdbLink`). It connects only to this TV's loopback address, checks that the device and firmware that answer are this TV's, and can send only a fixed list of commands: reading the firmware, ids, package lists, one package's version and permissions, what answers Home, channels and Settings, the active screensaver, and the animation scales; and the only two changes, `pm disable-user --user 0 <package>` and `pm enable <package>`. Never `pm uninstall`, `pm hide`, `pm clear`, root or a reboot. The first time, the TV shows its own "Allow USB debugging?" prompt for the key `unifiedtv@tv`: tick "Always allow from this computer" and choose OK. The key stays in the Android Keystore (or, if this TV's Keystore can't sign that way, encrypted in app-private storage that backups exclude); it is never shown, logged or exported. The link closes when the page closes.
- **Read-only check first** (AT-20): model, firmware, which list applies, and each listed app: installed or not, on or off, version, what it is, its risk, and *recommended* or *optional, your choice*. Every switch starts off.
- **Per-change consent.** A summary lists exactly what will change; only its confirm button changes anything. Before the first change the app writes its recovery record (package states, Home, animation scales, the TV's ids, firmware and time) to its no-backup storage, in `tools/recovery.py`'s own format; if that write fails, nothing changes. A later clean-up only adds packages to the first record, never overwrites it. Apps are then turned off one at a time; what counts as changed is what the TV reports afterwards, and an app that couldn't be turned off leaves the record again. Each change is recorded in the event log by package name. If a firmware update later takes the TV off the tested lists, the page still offers to turn the earlier apps back on and to save the undo files.
- **Verify and roll back.** The states are read again afterwards. Each app can be turned back on, or all of them (*Undo all*); undo only re-enables what the record says was on. *Check the TV still works* lists streaming, the remote and microphone, NordVPN, each HDMI input with sound, the doorbell and standby (AT-21).
- **Restore without Unified TV.** *Save the undo files on your phone* serves the record and the list through the usual phone setup page (home network only, one-time QR code, ten minutes). On a computer: `TV_HOST=<tv ip> python3 preview/tools/cleanup_restore.py unifiedtv-cleanup-record.json` checks the record with `tools/recovery.py`, files it where `tools/restore.sh` looks and prints the `tools/restore.sh` line to run. Without any computer: on the TV, **Settings > Apps > See all apps > Show system apps**, choose the app, **Enable**. The record contains the TV's serial number and Android ID (so restore refuses another TV): keep it private.
- **At the end** the page closes its link and guides you to turn off USB/network debugging (Settings > System > Developer options), then checks port 5555 is closed, as `tools/adb-closed.py` does. Unified TV never keeps ADB open. To withdraw its key: Developer options > Revoke USB debugging authorisations.

Do not change system packages until the factory Home and previous app state can be restored using a separate channel or the TV settings. Keep the original device settings/package-state backup off the public repository. If a device fails to boot, **stop** and use manufacturer-supported recovery guidance rather than applying more untested commands.

Future public releases will include checksums, signed-versus-debug build labelling, compatible firmware lists, a short install process, model-specific rollback instructions and precise status of optional features.
