# Unified TV Android Preview 0.5

**Hardware-unverified.** This is a test build for the TCL living-room TV (Android TV 11). It has not yet been installed or tested on any television. It is a **connectivity and playback preview**, not the finished viewing milestone: there is no verified metadata source for posters and titles yet (journey P05-06 below).

Version `0.5-preview`, versionCode 5, package `io.unifiedtv.preview`. Full list of changes: [CHANGELOG.md](../CHANGELOG.md), section 0.5.0.

## What 0.5 contains

- **Nothing to type to find your services.** The NAS, Home Assistant and Jellyfin are looked for in the background and chosen automatically when there is only one. A saved connection opens straight away.
- **Jellyfin sign-in with your phone** (Quick Connect). The Child profile still refuses an administrator account.
- **Poster cards** for films and series, each with its own artwork from the server, never another title's.
- **Long folders fixed:** every video is reachable in a folder that mixes videos with music or photos.
- **Live TV protection, separate from the VPN icon** ([VPN_PROTECTION.md](VPN_PROTECTION.md)). Live TV stays locked on every TV until the network check has passed and been recorded on that TV. It has not been run on any TV yet.
- **Doorbell and reminder fixes:** a person detected during a doorbell visit keeps the live picture; a reminder that arrives during the doorbell alert is shown afterwards.
- **Safe install and update** (new): an optional durable signing key and a safe install tool, described below.

Checks run so far: compiling, lint and unit tests only. The on-TV checks are in [PREVIEW_0_5_TESTS.md](PREVIEW_0_5_TESTS.md).

## Journeys and their current status

From the evidence register, [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv). *Built, not yet tested on a TV* is `implemented_unverified`; *not started* is `not_started`. No journey has passed on hardware.

| ID | Journey | Status |
|---|---|---|
| P05-01 | Safe install/update and launch | Built, not yet tested on a TV |
| P05-02 | Premium Home and remote focus | Built, not yet tested on a TV |
| P05-03 | Editable Apps and dock | Implemented, not tested on a TV (Apps grid; add, remove and move on Home, saved per room) |
| P05-04 | Finding the NAS on the network | Built, not yet tested on a TV |
| P05-05 | Showing only the three chosen collections | Built, not yet tested on a TV |
| P05-06 | Browsing, titles and posters | Built, not yet tested on a TV; **incomplete by design** (see below) |
| P05-07 | Playing a film from the NAS | Built, not yet tested on a TV |
| P05-08 | Recovering from network loss, standby and switching apps | Built, not yet tested on a TV |
| P05-09 | Settings and accessibility | Built, not yet tested on a TV |
| P05-10 | Security and release evidence | Built, not yet tested on a TV |

**Why P05-06 stays incomplete.** Titles and posters come only from what the NAS's media server reports about each file. There is no verified metadata adapter that identifies each film, so this build must not be called poster-first. Search, series grouping and Continue Watching are not claimed from the NAS.

## Signing and updates

Android only installs an update signed with the **same key** as the app already on the TV. Earlier previews were signed with whichever debug key built them, and CI machines can have different debug keys.

- **Never uninstall or clear a configured preview to get an update to install.** That deletes its saved connections, folders and settings.
- **Durable key (recommended).** The owner creates one keystore, keeps it and its passwords private and backed up, and never commits it. Builds use it when all four settings are present, as Gradle properties or environment variables: `UNIFIEDTV_PREVIEW_KEYSTORE`, `UNIFIEDTV_PREVIEW_KEYSTORE_PASSWORD`, `UNIFIEDTV_PREVIEW_KEY_ALIAS`, `UNIFIEDTV_PREVIEW_KEY_PASSWORD`. Without them, builds are signed exactly as before.
- **Releases.** The tagged release workflow signs with the durable key when the repository secrets `UNIFIEDTV_PREVIEW_KEYSTORE_BASE64` (the keystore, base64-encoded), `UNIFIEDTV_PREVIEW_KEYSTORE_PASSWORD`, `UNIFIEDTV_PREVIEW_KEY_ALIAS` and `UNIFIEDTV_PREVIEW_KEY_PASSWORD` are all set. Otherwise the release is labelled **test-signed: may not update over a differently signed install**. Each release includes `SIGNING.txt` with the certificate's SHA-256.
- **Moving to the durable key once.** A preview already on the TV with a different key cannot be updated to the durable-key build. Keep it as it is until its settings can be written down and re-entered, then make that change deliberately. The separate `deviceCheck` build installs alongside with its own empty settings.

### Installing with the safe install tool

`preview/tools/install_preview.py` uses the same ADB connection as `tools/tv.py` (set `TV_HOST`). Before installing it:

1. reads the installed preview's version and signing certificate on the TV, and works out the new APK's certificate on your computer;
2. refuses if the certificates differ, or if the APK is older than the installed one (unless `--allow-downgrade`);
3. records which app owns Home, then installs with `pm install -r`, which keeps the app's data;
4. checks that Home is unchanged and stops loudly if it has changed;
5. opens Unified TV and writes a record to `preview-evidence/` (APK hash, versions, certificate prefix, Home unchanged yes/no, result, time; no addresses, serial numbers or account details).

It never uninstalls, clears data or disables anything. Use `--check` first to see what it would do without installing.

```bash
export TV_HOST=<the TV's address>    # never commit it
python3 preview/tools/install_preview.py UnifiedTV-Preview.apk --check
python3 preview/tools/install_preview.py UnifiedTV-Preview.apk
```

## On-TV evidence still needed

Each item needs the exact APK hash, the TV model and firmware, the date and the result, recorded in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv).

1. **P05-01:** an install record from `install_preview.py` on the TCL, updating the existing preview with its settings kept and the original Home unchanged.
2. **P05-02 and P05-09:** screenshots or a short video of Home focus, Back and all six Settings categories from this exact build, checked for private details before sharing.
3. **P05-04 and P05-05:** the NAS found by name with NordVPN off and on; only the three chosen collections reachable; collections still working after a NAS restart.
4. **P05-06:** a long mixed folder browsed to its last video, with each poster belonging to its own title.
5. **P05-07:** one representative film played, with pause, seek, stop and Back; format, codec, audio, subtitles and time to first picture written down.
6. **P05-08:** network loss, standby and wake, switching to another app and back, and cancelling while loading, with no endless spinner or lost focus.
7. **P05-10 and Live TV:** the [network check](VPN_PROTECTION.md) run on the TCL with the owner's consent. Until it passes, Live TV stays locked.
8. **Remote and Home tests** from the register (RC, HRC and OI rows), starting with the TCL Home and Back checks.

Sony and Samsung results are separate and are not implied by anything here.
