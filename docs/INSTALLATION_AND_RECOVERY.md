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

Do not change system packages until the factory Home and previous app state can be restored using a separate channel or the TV settings. Keep the original device settings/package-state backup off the public repository. If a device fails to boot, **stop** and use manufacturer-supported recovery guidance rather than applying more untested commands.

Future public releases will include checksums, signed-versus-debug build labelling, compatible firmware lists, a short install process, model-specific rollback instructions and precise status of optional features.
