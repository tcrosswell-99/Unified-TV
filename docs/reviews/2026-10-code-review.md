# Code review, October 2026

> **Status:** the findings below were addressed in [0.4.1](../../CHANGELOG.md). See [Status after 0.4.1](#status-after-041) at the end. Line numbers refer to the code as it was at `7a41145`.

Date: 24 October 2026. Read-only review of `claude/modest-davinci-dq3wrc` at `7a41145`. Nothing was committed, pushed or run on a TV.

## Overview

**What it is.** This is an early fork of CleanYourTV / CleanYourTV UI. Unified TV itself is still mostly documentation: the product spec and roadmap describe planned features, not built ones. The code is all inherited.

| Part | Stack | How it runs |
| --- | --- | --- |
| `launcher/` (`com.gathin.tvhome`) | Plain Java Android TV HOME app; Gradle 8.9, AGP 8.5.2, min 26 / target 34, R8 | Sideloaded over ADB. CI enforces a 512 KB APK limit. No INTERNET permission. Two accessibility services remap remote keys. |
| `launcher-ui/` (`io.cleanyourtv.ui`) | Java, built without Gradle by `build.sh` (aapt2 → javac → d8 → apksigner) | Richer home screen backed by TMDB. `Config.java` is generated from `config.properties`, so the TMDB key is compiled into the APK. |
| `addons/` (about 20 apps) | Separate Gradle projects (one Kotlin) | Optional cards: Frigate (MQTT/RTSP), Home Assistant, Pi-hole, a TV guide and so on. |
| `tools/` | Python 3 (`adb-shell`, `cryptography`) plus sh/PowerShell | Disables and restores packages over ADB, with recovery records, plus benchmarking and tracing. |
| `tests/` | 861 Python unittest checks | Mostly documentation consistency, plus behavioural guard tests that run `disable.sh` against a fake TV. |

**Overall.** The inherited code is careful:
- It validates untrusted input.
- Its IPC is locked down: providers check the caller's uid or `WRITE_SECURE_SETTINGS`, and file names are allow-listed.
- No secrets are committed, in the tree or in the history (1,553 commits scanned for key patterns).
- The ADB debloat guards (Home, setup wizard, core packages, TV input) fail closed, and the sh and PowerShell versions match.

The real problems are:
- CI fails on every push because the fork changed the docs.
- Recovery records can collide between TVs.
- Several crash paths take down the home process.
- Some memory use is unbounded on 1 GB TVs.

## Findings

Severity reflects impact on a household TV. There are no Critical findings.

| # | Sev | Location | Issue | Fix |
| --- | --- | --- | --- | --- |
| 1 | High | `.github/workflows/ci.yml:24-27` | The main CI job always fails: 861 tests, 40 failures, 4 errors (reproduced locally). All of them are doc-content checks that still expect the upstream README, AGENTS.md and links: `test_agents_md` alone gives 19, plus the README tools table, translations and theme count. Two links are also broken: `docs/LOCAL_CONNECTIONS_AND_TCL_TESTING.md:17` links to a Windows test script that isn't in the repository, and `docs/upstream/CLEANYOURTV_UI_README.md` was moved without updating its relative links. While the job is red, a real regression in a safety guard looks like the same noise. | Point the README assertions at `README.cleanyourtv.md`, or retire them. Rewrite the AGENTS.md assertions for the fork. Fix the two link sources. Then make the job a required check. |
| 2 | High | `tools/recovery.py:70-75` | Recovery records are keyed on the `TV_HOST` string and the list path only, not on the port or the device. Every TV behind `relay.py` is `127.0.0.1`. Scenario: you debloat TV A, then TV B, through relays with the same list. B's `--save` keeps A's record ("first one wins"), so `restore.sh` on B replays A's package states and Home component. A changed DHCP address, or writing `host:port`, finds no record, and restore then falls back silently to "enable everything" (`:229-244`). This breaks rule 9's guarantee of verified rollback. | Key the record on the device identity (`ro.serialno` or `android_id`) and store that identity in the record. Refuse to save or restore when it doesn't match the connected TV. Require an explicit flag for the no-record fallback. |
| 3 | Medium | `launcher/.../Config.java:366-368`, `:460-463` | The whole theme and behaviour config (`config.json`) is read from `getExternalFilesDir`, with no check on who wrote it. On Android 8–10 (minSdk 26), any app with storage permission can write there. Such an app can remap remote keys through `app_keys` (both accessibility services obey it), hide the "VPN off" badge (`vpn_app`), or redirect Live TV to itself. I didn't confirm the write access on a device. | On API < 30, accept config only through the permission-gated `ThemeProvider`, and have it write to `getFilesDir()`. |
| 4 | Medium | `launcher/.../RemoteKeys.java:252-255`, `:261-264`; `InputKeyService.java:323-326` | `startActivity` calls reached from `onKeyEvent` catch only `ActivityNotFoundException`. `MainActivity.openInput` (around `:2839-2860`) already catches `SecurityException` because it "used to take Home down". Scenario: a remote key mapped to a permission-guarded vendor input or player crashes the launcher process. Android may then leave the accessibility service disabled, so the Home and Input keys stop working. | Catch `ActivityNotFoundException \| SecurityException` everywhere on the key-service paths. |
| 5 | Medium | `launcher-ui/.../ExploreActivity.java:275-278` (with `:308`, `:904`, `:1180`) | `onDestroy` calls `io.shutdownNow()`, but the delayed `backSoon`, `searchSoon` and `tabSoon` callbacks are not removed in `onPause` or `onDestroy`, and each ends in `io.execute`. Scenario: browse or type, then press Back within 350–600 ms. A `RejectedExecutionException` on the main thread kills `:home`, which also hosts Home and Settings. | Remove these callbacks in `onPause` and `onDestroy`, and add an `isFinishing() \|\| io.isShutdown()` guard to `loadBackdrop`, `runFilter` and `showTab`. |
| 6 | Medium | `launcher-ui/.../CustomTiles.java:77`, `:99` | Custom tile logos are downloaded and decoded at full size before `fit()`. There's no `inSampleSize`, no download cap, and no `OutOfMemoryError` catch, and the work runs on HomeView's loader thread. A failed decode leaves no cache marker, so it retries on every resume. Scenario: a very large PNG as `app_tiles.logo` makes Home crash every time it resumes. | Decode the bounds first and sample down to 640 px. Cap the download at about 2 MB, catch OOM, and write a negative-cache marker. |
| 7 | Medium | `launcher-ui/.../TvUi.java:484-533`; `Images.java:37` | The poster grid never recycles views or releases bitmaps. Explore allows 25 feed pages and Grid allows 20, and `restoreImages` re-queues every image in `onStart`. The image pool's queue is unbounded and can't be cancelled. That adds up to 175 MB or more of posters, which leads to LMK kills or OOM on 1 GB TCL and older Sony sets (rule 5). | Use a recycling list or release off-screen rows. Restore only visible images. Drop requests whose views have detached. |
| 8 | Medium | `addons/frigate-glance/.../MqttClient.java:120` | `new byte[header[1]]` trusts the MQTT remaining length, which can be up to 268 MB. Any client that can publish to `frigate/*` can cause an `OutOfMemoryError`, which is not caught. If the message is retained, the `START_STICKY` service crash-loops. | Reject or drain bodies over about 1 MB. |
| 9 | Medium | `addons/frigate-glance/app/src/main/AndroidManifest.xml:17-22`; `README.md:66` | The README documents `http://host:5000`, but the app has no network security config. With targetSdk 34, cleartext HTTP is blocked, so `/api/config` and snapshots always fail. The JVM tests can't catch this. | Add a cleartext config, as ad-blocking and smart-home do, or document HTTPS only. |
| 10 | Medium | `addons/sleep-timer/.../Timer.java:29-32`; `AlarmReceiver.java:16` | `Timer.end()` returns 0 once the end time is more than 5 s past, and the receiver treats 0 as "cancelled". Without the exact-alarm grant (the default on Android 14), the fallback inexact alarm arrives late and is ignored, so the TV never sleeps. | For WARN and SLEEP, read the raw stored end time and act if it has passed and wasn't cancelled. Clear it afterwards. |
| 11 | Medium | `addons/smart-home/.../MainActivity.java:87-90`, `:125` | `postDelayed(this::load, 600)` runs after `onDestroy` has called `net.shutdownNow()`. Scenario: toggle a light, then press Back. The app crashes with `RejectedExecutionException`. `weather/WeatherActivity.java:102` has the same pattern. | Remove callbacks in `onDestroy` and guard against `isFinishing()`. |
| 12 | Medium | `PROMPT.md:17`, `:44` | The top-level, unlabelled upstream prompt begins "I want to debloat my Android TV…". This conflicts with AGENTS rules 9 and README's "do not run inherited prompts". | Move it to `docs/upstream/` or add a prominent "inherited, not for Unified TV" banner. |
| 13 | Medium | `.github/workflows/ci.yml:29-51`; `addons.yml:40` | CI never builds or tests `launcher-ui/`, the app AGENTS.md names as the base. The add-ons job runs only `assembleRelease`, so their unit tests never run. | Add a `launcher-ui` job. Run `testReleaseUnitTest` for changed add-ons. |
| 14 | Low | `launcher-ui/.../DetailActivity.java:546-553`; `SeasonActivity.java:157` | An AlertDialog and `recreate()` are posted after up to 18 s of network I/O without an `isFinishing()`/`isDestroyed()` check. Back pressed mid-request can cause `BadTokenException` and crash `:home`. | Guard the posted runnables. |
| 15 | Low | `addons/{ad-blocking,smart-home,frigate-glance}/.../ConfigProvider.java:23-28`; `frigate-glance/install.sh:56` | Passwords and tokens are staged in `getExternalFilesDir`, which is readable by apps with storage permission on Android 8–10. | Stage them in `getFilesDir()`. |
| 16 | Low | `launcher/.../MainActivity.java:2482-2490` | `updateRace` formats session times with no try/catch. An out-of-range `race_calendar` date (see #3) throws `DateTimeException` on every resume. | Clamp the years in `Config.raceCalendar` and wrap the panel update. |
| 17 | Low | `launcher/.../AppLaunchActivity.java:16-21` (manifest `:157-170`) | The activity is exported and BROWSABLE, so any web page can launch any installed app through `market://launch?id=`. This matches stock launcher behaviour. | Document it, or put it behind config. |
| 18 | Low | `launcher/.../WallpaperPlayer.java:106-129`; `AppAdapter.java:255-341` | The first wallpaper (up to 10 MB, with up to 100 retries) and every tile's banner are decoded on the UI thread. Banners are forced up to xhdpi. This hurts focus latency on slow sets. | Decode in the background and stop after the first failure. |
| 19 | Low | `launcher-ui/.../TmdbLoginActivity.java:82` | `polling = true` is set on the worker thread after `onDestroy` may already have cleared it. That leaves about 300 POSTs over 15 minutes and a leaked Activity. | Set the flag before starting the thread. |
| 20 | Low | `launcher-ui/build.sh:157-160`; `launcher/app/build.gradle:24` | The release signing password is hard-coded and public (`cleanyourtv-ui`), and the launcher's release build uses the debug key. Builds from different machines can't update each other, and there's no protected release identity. | Take the keystore and password from env or Gradle properties, and document keystore backup. |
| 21 | Low | `tools/bench.py:354` | `enabled_accessibility_services` is written unquoted to the device shell, so a `$Inner` class name is expanded away and another app's service drops out of the list. | Single-quote the validated value. |
| 22 | Low | `tools/bench.py:513`, `:578`; `.gitignore` | `bench-results/result.json` contains the TV's LAN IP and build fingerprint and isn't git-ignored (rule 6). | Ignore `bench-results/` or omit `host`. |
| 23 | Low | `.github/workflows/addons.yml:32-34` | `BASE` is all zeros on a new-branch push, or unreachable after a force-push, so `git diff` fails. | Fall back to `HEAD~1`, or build everything. |
| 24 | Low | `.github/workflows/*.yml` | Actions are pinned by tag, including third-party `android-actions/setup-android@v3`. Permissions are correctly `contents: read`. | Pin to commit SHAs and add Dependabot for actions. |
| 25 | Low | `tools/requirements-perf.txt`; `tools/requirements.txt` | Pillow is pinned at 11.3.0, two majors behind. `cryptography>=46.0.7` is an open-ended pin. | Run `pip-audit` and bump. Use a lock file or a bounded range. |
| 26 | Low | `tools/tv.py:84-88` | The ADB private key is written at umask (often 0644), then chmodded. An existing permissive `tools/adbkey/` directory is not tightened. | Set `os.umask(0o077)` around keygen and chmod the directory. |
| 27 | Low | `addons/tv-static/.../BootReceiver.java`; `photo-screensaver/sync.sh:87` | TV Static's foreground service keeps running, and starts at boot, while the effect is off. `sync.sh` changes `screen_off_timeout` without recording it, so `--off` can't restore it (rule 9). | Skip or stop the service when it's disabled. Back up and restore the timeout. |
| 28 | Low | `.github/FUNDING.yml`; `ISSUE_TEMPLATE/bug.yml`, `tv-list.yml`; `addons/README.md:92`; `launcher-ui/config.example.properties:22` | Inherited and fork leftovers: the upstream sponsor link, upstream debloat issue templates alongside the fork's, an unclosed code fence, and a wrong comment that should say `show_crypto=true`. | Tidy them up. |

**Project rules, not code bugs.** The launcher's VPN state is display-only (`MainActivity.java:2569-2594`), so it is not the fail-closed guard rule 8 requires. None of the Unified TV features (Ring, NAS, glass dock and so on) exist in code yet, which matches `docs/STATUS.md`.

## Top 5 fixes

1. **Get CI green and make it required (#1, #13).** Until then, nothing else can be trusted to stay fixed.
2. **Key recovery records to the device (#2).** This is the one place a bug could leave a family's TV without its rollback.
3. **Remove the crash paths that take down `:home` (#4, #5, #14).** Cancel callbacks and guard executors before calling them, and catch `SecurityException` in the key services.
4. **Bound memory on 1 GB TVs (#6, #7, #8).** Sample decodes, cap downloads and MQTT bodies, and recycle the poster grid.
5. **Stop trusting world-writable storage on Android 8–10 (#3, #15).** Move config and secrets staging into internal storage behind the permission-gated providers.

## What I couldn't check

- **Android builds and JVM unit tests.** The session's network policy blocks `dl.google.com`, so there was no Android SDK or AGP. The launcher, launcher-ui and add-on builds, lint, the APK size limit and `launcher/` unit tests were not run.
- **On-device behaviour.** Nothing was tested on hardware: crash timing (#4, #5, #11, #14), the Android 8–10 shared-storage behaviour (#3, #15), alarm lateness (#10) and real memory figures (#7) come from reading the code paths, not from reproducing them.
- **Dependency CVEs.** The advisory feeds (OSV) were unreachable, so the pins weren't checked against advisories.
- **PowerShell.** The guard tests skip without `pwsh`; sh/ps1 parity was checked by reading only.
- **Partial coverage.** `HomeView.java` (about 2,950 lines) drawing paths and the TV Hub, World Clock, Photo Prints and Flock UIs were skimmed rather than read line by line.

## Status after 0.4.1

| # | Status | Note |
| --- | --- | --- |
| 1 | Fixed | Documentation tests reconciled with the fork; broken links fixed. |
| 2 | Fixed | Records are keyed by address and port and store the TV's identity; a record from another TV is refused; the no-record fallback needs `RESTORE_WITHOUT_RECORD=1`. Legacy records without an identity still load, with a warning. |
| 3 | Partly fixed | Android 10 only applies a theme written through the permission-gated provider. Android 8/9 have no `content write`, so the old behaviour remains there. |
| 4 | Fixed | |
| 5 | Fixed | Pending callbacks removed and every background start guarded. |
| 6 | Fixed | Bounds-first sampled decode, 2 MB cap, OOM caught, failure marker. |
| 7 | Fixed | Off-screen grid lines release posters and reload them nearby; queued requests nobody waits for are dropped. Needs a 1 GB TV test. |
| 8 | Fixed | Oversized MQTT packets are skipped, never buffered. |
| 9 | Fixed | |
| 10 | Fixed | |
| 11 | Fixed | Smart Home and Weather. |
| 12 | Fixed | Banner on `PROMPT.md`; restore advice points at the recovery records. |
| 13 | Fixed | New `launcher-ui` CI job; add-on unit tests run in CI. |
| 14 | Fixed | |
| 15 | Fixed | Staged in private storage; Frigate's Android 8/9 fallback deletes the shared copy after import. |
| 16 | Fixed | |
| 17 | Documented | Kept for the Android TV Remote service; explained in the code. |
| 18 | Partly fixed | Wallpaper start-up and config rewrite fixed; banner loading in `AppAdapter.reload` unchanged. |
| 19 | Fixed | |
| 20 | Fixed | Optional own keys for both apps; defaults unchanged so existing installs still update. |
| 21–23 | Fixed | |
| 24 | Partly fixed | Dependabot added for actions and pip; actions are still pinned by tag, not commit SHA. |
| 25 | Fixed | Pillow 13 on Python 3.11+ (11.3 kept for older Python); cryptography capped below 51. |
| 26–28 | Fixed | |

All of this is code-verified only: none of it has been tested on a television yet.
