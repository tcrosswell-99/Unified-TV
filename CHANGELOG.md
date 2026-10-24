# Changelog

All notable changes to Unified TV are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/): while the project is below 1.0.0, any release may change behaviour.

Unified TV's version follows the Android Preview line (`0.4`, `0.4.1`, …) and covers the whole repository. Preview APKs are published from `android-preview-v*` tags. The inherited apps keep their own Android version numbers, listed under each release. The product specification has its own document version (see [PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md)); feature-by-feature evidence is in [FEATURE_STATUS.md](docs/FEATURE_STATUS.md).

Nothing in a release has been tested on a television unless the entry says so. Planned features stay in the [roadmap](docs/ROADMAP.md) until they have been tested on real hardware.

## [Unreleased]

## [0.5.0] - 2026-10-24

Android Preview **0.5** (`0.5-preview`, versionCode 5), "The Viewing Experience". Home now changes its whole scene with what is in focus and your films rise into view on Down. Unified TV can become the TV's real Home if an adult chooses it, and can always hand Home back. The NAS, Home Assistant and Jellyfin are found on the home network and set up without typing an address, and Jellyfin signs in with a phone. Code-checked and unit-tested only. Release notes: [PREVIEW_0_5_RELEASE.md](docs/PREVIEW_0_5_RELEASE.md). **Not yet installed on a TV:** see the [0.5 on-TV checks](docs/PREVIEW_0_5_TESTS.md).

### Added

- **One Home journey** (spec 4.4 to 4.6):
  - **The scene follows focus:** moving to an app or a title settles for a moment, then dissolves into that app's own branded scene or that title's own poster, softened and darkened. Never another title's image. Scenes are drawn once and cached small, so the TCL only draws two pictures during a dissolve.
  - **The dock** rests low and quiet, and rises when you move.
  - **Films & TV rises from Home:** press Down and a row of posters from your chosen collections appears, without leaving Home. OK plays a film or opens a series; Up or Back returns.
- **Unified TV as the TV's Home, only if you choose it** (spec 29):
  - Installing changes nothing: the Home entry ships switched off, and CI fails any build where that isn't true (`preview/tools/home_check.py`).
  - **Settings → TV & performance → Make Unified TV the Home screen** switches it on and lets Android itself ask. If Unified TV isn't chosen, it is switched straight off again.
  - As Home, the Home button returns to the main screen; Back at the main screen never leaves it.
- **Open original TV interface** (spec 30): a small adult-only row offers the manufacturer's interface for now, or as the default again after a second confirmation.
- **Apps, and a Home you can change** (P05-03): a grid of every installed app plus Unified TV's shortcuts. Hold OK to open, add to or remove from Home, or see app information. Each room keeps its own dock.
- **Updates that keep your settings:** the preview can be signed with your own durable key through four `UNIFIEDTV_PREVIEW_*` settings. Without them it is signed exactly as before.
- **Safe install tool** (`preview/tools/install_preview.py`): compares the signing certificate and version with the preview on the TV, refuses a mismatch or downgrade, never uninstalls or clears data, and checks the TV's Home is unchanged. Not yet run on a TV.
- **Release labels:** tagged previews say hardware-unverified, publish the certificate SHA-256, and say "test-signed" when no durable key was used.
- **Evidence register** ([ACCEPTANCE_REGISTER.csv](docs/ACCEPTANCE_REGISTER.csv)) for the P05, RC, HRC (TCL), OI and VPN-01 checks, validated by a unit test.
- **Live TV protection, separate from VPN appearance** ([VPN_PROTECTION.md](docs/VPN_PROTECTION.md)):
  - **The protection:** Android's always-on NordVPN with *Block connections without VPN* stops traffic whenever NordVPN drops. Only Unified TV is exempt, so it can still reach the home network.
  - **The check:** `preview/tools/vpn_check.py` proves it on each TV at network level. It reads the Live TV app's own connections during playback and through a NordVPN disconnect, and writes a record with no addresses in it. The settings change asks first, saves a backup and can be rolled back.
  - **Opening Live TV:** an adult records a passed check in Settings. Live TV then opens from the dock only while that setup is unchanged.
  - **If NordVPN drops during playback:** the viewer is brought back with "Live TV paused".
  - **Not yet run on any TV:** Live TV stays locked until a check passes.
- **No addresses to type.**
  - The NAS, Home Assistant and Jellyfin are looked for quietly in the background when Unified TV starts, only for what isn't set up yet.
  - If exactly one is found, it is chosen for you; a list appears only if there are several.
  - A saved connection opens straight away.
  - Typing an address remains an advanced option.
- **Sign in with your phone (Jellyfin):** the TV shows a code, approved in the Jellyfin app (Quick Connect). No token or user ID to type. The Child profile still refuses an administrator account.
- **Poster cards for your films and series:** each title shows its own artwork and year from the server, never another title's.
- **Home Assistant is found more reliably:** if the normal network search finds nothing, Unified TV tries the standard `homeassistant.local` name, then checks the TV's own home network for a server that identifies itself as Home Assistant.

### Changed

- **Searches use the home network under a VPN:** they go over the home Wi-Fi or Ethernet even while NordVPN is on, where NordVPN allows it.
- **The NAS search tries harder:** if the usual network-wide search gets no reply, the same request is sent to each address on the home network.
- **Plain language on screen:** setup and status screens show server names, not IP addresses, ports or network terms.
- **Every app opens the same way,** so the Live TV protection check applies whether Live TV is opened from Home or Apps.
- **Quick Connect on an unencrypted home server** now asks for an explicit Continue first, instead of assuming it is trusted (spec 27.4).

### Fixed

- **Long folders:** a folder that mixes videos with music or photos no longer stops paging early, so every video is reachable.
- **Doorbell:**
  - A person detected during a doorbell visit keeps the live picture and extends it, instead of restarting it.
  - A reminder that arrives during the doorbell alert is shown afterwards if still relevant, instead of being lost.
- **Reminders:** a TV clock a few seconds behind Home Assistant no longer drops every reminder.
- **Home Assistant search:** it no longer fails on older Android when two services answer at once.
- **VPN status (display only):** a status check started before a network change is discarded, so the header no longer shows a stale state. This changes only what the status shows. It is not VPN protection, and Live TV stays blocked.


## [0.4.1] - 2026-10-24

Android Preview 0.4 plus fixes for everything around it: the inherited CleanYourTV code made safer and sturdier, the project's purpose explained, and green CI to build on. The preview app itself is unchanged from 0.4 (`0.4-preview`, versionCode 4). Every fix comes from the [October 2026 code review](docs/reviews/2026-10-code-review.md). All changes were checked by compiling and unit-testing the code; **none has been tested on a television yet.**

App versions in this release: Android Preview **0.4-preview** (unchanged), inherited launcher **2.8** (versionCode 9), launcher UI **1.1** (versionCode 2). Product specification **v2.2** (unchanged).

### Added

- **One familiar home on every TV.** The README now explains the aim: the same apps, in the same order, on every TV in the house, whatever the brand. A native Android app for Sony and TCL first, a native Tizen app for Samsung Frame next, other TVs later.
- **Home view design note** ([docs/HOME_VIEW.md](docs/HOME_VIEW.md)): optional Home Assistant alerts on the TV, one at a time in the spec's order (doorbell first, then reminders), with deliveries and matchday as proposed glances. A smoke/CO alarm above the doorbell is recorded as a proposal awaiting the owner's decision. This is a planned design, not a working feature yet.
- **Ideas catalogue** ([docs/IDEAS.md](docs/IDEAS.md)): optional features for home and safety, family, sport, content and practical everyday information, each marked as an idea, planned, or partly inherited.
- **Marketing images** ([docs/marketing](docs/marketing/README.md)): concept artwork for the README and social preview, with an editable source and a render script. Each image is labelled as concept artwork, not a screenshot.
- **Maintainer checklist** ([docs/GITHUB_SETUP.md](docs/GITHUB_SETUP.md)) for repository description, topics, social preview and outreach.
- **Optional own signing keys:** the launcher can be signed with your own key through four settings (`UNIFIEDTV_KEYSTORE`, `UNIFIEDTV_KEYSTORE_PASSWORD`, `UNIFIEDTV_KEY_ALIAS`, `UNIFIEDTV_KEY_PASSWORD`), and the launcher UI's keystore password can be set with `KEYSTORE_PASSWORD`. Without them, builds are signed exactly as before. Keep a backup of your keystore: updates need the same key.
- **CI:** a launcher UI build job alongside the preview APK job, unit tests for changed add-ons, and weekly Dependabot updates for CI actions and the Python tool pins.
- This changelog.

### Changed

- **Restore is stricter and safer.**
  - Undo records now remember which TV they came from and are filed by address and port, so two TVs behind relays no longer share one record.
  - Restore refuses a record taken on a different TV, and still finds the right record if a TV's network address changes.
  - Restoring without a record no longer re-enables the whole list automatically. Set `RESTORE_WITHOUT_RECORD=1` only if you really want that.
- **Themes on Android 10:** the launcher only applies a theme written by the theme tools, so other apps can't change button mappings, the VPN badge or the Live TV app. Android 8, 9 and 11+ behave as before.
- **Pushed passwords and tokens** for Frigate Glance, Smart Home and Ad Blocking are staged in the app's private storage instead of shared storage.
- **Example settings** for the launcher UI now match the build defaults (US region, New York, USD).
- **Inherited material is clearly labelled:**
  - `PROMPT.md` now opens with a warning that it is the upstream CleanYourTV prompt and must not be run on a household TV without the recovery-first process.
  - The issue template for TV reports now asks about restore, and stresses that cleanup is optional and needs consent.
  - The upstream sponsor button and a duplicate bug template have been removed.
- **Python tools:** Pillow 13.0.0 on Python 3.11 or later (11.3.0 stays for older Python), and cryptography capped below 51.

### Fixed

- **Launcher**
  - Remote buttons mapped to a protected app or input no longer crash the launcher or switch off its accessibility helpers.
  - A race calendar with an impossible date no longer crashes Home; such sessions are skipped.
  - Home starts faster on low-memory TVs when a wallpaper is broken, and no longer rewrites its saved theme on every return to Home.
- **Launcher UI**
  - Leaving Explore just after moving focus, switching tab or typing a search no longer crashes the home screen.
  - Long Explore and grid lists now release posters well off-screen and reload them as you scroll back, keeping memory bounded on 1 GB televisions.
  - Custom tile logos are size-checked and downsampled, and broken ones are no longer downloaded again on every return to Home.
  - No more crashes when a list picker, message or season refresh arrives after its page has closed.
  - Leaving TMDB sign-in now stops its background check.
  - Home tile pictures are freed when Android asks for memory.
- **Add-ons**
  - Frigate Glance: oversized MQTT messages are skipped instead of crashing the service, and a restart can no longer leave an old connection running.
  - Frigate Glance: `http://host:5000` Frigate addresses now work on the home network; https on port 8971 is still recommended.
  - Sleep Timer: the TV now still goes to sleep when Android delivers the timer's alarm late, as it does without the exact-alarm permission.
  - Smart Home and Weather: closing the screen while a request is in progress no longer crashes the app, and Weather no longer refreshes in the background.
  - TV Static: switching the effect off now stops its background service, and it no longer starts at boot.
  - Photo Screensaver: `sync --off` now also restores the screen-off timeout that `IDLE_MINUTES` changed.
  - Fixed the add-ons README's broken code block, and added the Windows `gradlew.bat` for Flock.
- **Tools**
  - The ADB key is private from the moment it is created, and its folder is kept private.
  - Benchmarks no longer change other apps' accessibility settings by mistake, and their results no longer store the TV's network address. The results folder is kept out of Git.
- **CI and documentation**
  - CI is green again: documentation checks were updated for the fork, and broken links were fixed.
  - The add-ons workflow no longer fails on a new branch or after a force-push.

### Known limitations

- Nothing in this release has been tested on a television. The crash fixes, memory limits, Android 10 theme check and sleep timer changes each need a real-device check (see [compatibility](docs/COMPATIBILITY.md)), as does everything in Preview 0.4.
- Android 8 and 9 still trust a theme file in shared storage, because they have no `content write` route.
- CI actions are pinned by version tag, not commit SHA.

## [0.4.0] - 2026-10-24

[Android Preview 0.4](https://github.com/tcrosswell-99/Unified-TV/releases/tag/android-preview-v0.4.0), a test prerelease of the separate `io.unifiedtv.preview` app. Full notes: [PREVIEW_0_4_RELEASE.md](docs/PREVIEW_0_4_RELEASE.md). Product specification v2.2.

### Added

- Two-column Settings: Connections, Profiles, Notifications, Appearance, TV & performance, and Privacy & security.
- Automatic UPnP/DLNA server discovery by name, encrypted selected-folder configuration, and a Media3 direct-play trial.
- Home Assistant discovery on the home network to pre-fill a new connection.
- A gallery with five owner-authorised example images, interval and remote controls.
- Four VPN states (Connected, Disconnected, Checking, Unknown). Live TV stays blocked until VPN protection is proven.
- Music opens the official Amazon Music app, or its store page.

### Verification

37 Android tests, lint and the APK builds passed. Not installed on a TV; editable Apps, real NAS playback, Ring, reminders, VPN protection and the Samsung app remain open. See [FEATURE_STATUS.md](docs/FEATURE_STATUS.md).

## Earlier previews

Untagged in this changelog: [Preview 0.3](docs/PREVIEW_0_3.md) (limited TCL checks), [Preview 0.2](docs/PREVIEW_0_2.md) and the [first Android preview](docs/FIRST_ANDROID_PREVIEW.md).

[Unreleased]: https://github.com/tcrosswell-99/Unified-TV/compare/android-preview-v0.5.0...HEAD
[0.5.0]: https://github.com/tcrosswell-99/Unified-TV/compare/android-preview-v0.4.1...android-preview-v0.5.0
[0.4.1]: https://github.com/tcrosswell-99/Unified-TV/compare/android-preview-v0.4.0...android-preview-v0.4.1
[0.4.0]: https://github.com/tcrosswell-99/Unified-TV/releases/tag/android-preview-v0.4.0
