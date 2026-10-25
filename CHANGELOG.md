# Changelog

All notable changes to Unified TV are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/): while the project is below 1.0.0, any release may change behaviour.

Unified TV's version follows the Android Preview line (`0.4`, `0.4.1`, …) and covers the whole repository. Preview APKs are published from `android-preview-v*` tags. The inherited apps keep their own Android version numbers, listed under each release. The product specification has its own document version (see [PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md)); feature-by-feature evidence is in [FEATURE_STATUS.md](docs/FEATURE_STATUS.md).

Nothing in a release has been tested on a television unless the entry says so. Planned features stay in the [roadmap](docs/ROADMAP.md) until they have been tested on real hardware.

## [Unreleased]

### Added

- **Samsung Frame web app** (`samsung/`): a native Tizen web app for the Frame (2021) with the same Home light palettes as Android (a test keeps them identical), remote focus, six-category Settings, the locked Live TV sheet, smoke/CO alerts above Ring, the Wallpaper and AVPlay/DLNA playback of NAS folders. 117 Node tests; CI (`samsung.yml`) builds an **unsigned, test-only** `.wgt`, which a retail TV will not install until the owner signs it with their own Samsung certificates. Secrets are kept only in Tizen Key Manager and the app fails closed without it. **Not tested on the Frame.**
  - Limits: the NAS address is typed once under Advanced (web apps cannot discover it; awaiting the owner's acceptance), the Ring card has no live camera, private NAS playback and a Content-Security-Policy are not built, and Samsung TV Plus is found by name because its app id is unconfirmed.
- **Samsung Home-button proposal** ([SAMSUNG_HOME_PROPOSAL.md](docs/SAMSUNG_HOME_PROPOSAL.md)) and the Frame capability table ([samsung-frame-2021.md](docs/devices/samsung-frame-2021.md), every value `unknown`). A proposal only: nothing was run on the TV.

- **Adults and Kids, Library and owner diagnostics** (Android TCL/Sony; owner direction 25 October 2026: Home → Library → Apps → Settings):
  - **Library** can be placed in the dock like an app; Back returns to the same tile.
  - **Profiles:** the TV opens in the last-used profile; switch from the top row (Up, then Left) or Settings. Moving to Adults always needs the PIN.
  - **Kids:** only adult-chosen Kids folders on the NAS, with ratings checked (UK U/PG, US G/PG/TV-Y/TV-Y7/TV-G/TV-PG; unrated only inside a Kids folder), and checked again when a video starts. Only approved apps open from Unified TV by any route; system settings need the PIN. An optional **Kids app guard** (an accessibility service, off by default) sends the TV back to Home when another app is opened some other way. It is a guard, not a lock: Android allows no hard block without a factory reset, and the screen says so.
  - **Continue Watching** is kept separately for Adults and Kids ("on this TV").
  - **"Show person alerts in Kids"** (on by default); doorbell presses and smoke/CO are unchanged.
  - **Owner diagnostics** in Settings → TV & performance (Adults, PIN): NAS, Home Assistant/Ring, VPN/Live TV, version and signing, with no secrets.
  - **NAS discovery** offers a server only when its library browses, names servers only, and shows a plain checklist with Search again when nothing is found.
  - A single catalogue item model so search can be added later. Unit-tested only; nothing tried on a TV.
- **Samsung Frame DLNA discovery probe** (`samsung/poc-dlna`, package `UTVDLNAPOC`): a separate, unsigned test package following Tessel's architecture (our own code; Tessel is MIT and credited). A Tizen web service listens on 127.0.0.1 only, behind a per-launch token, and tries SSDP by multicast, directed broadcast, a bounded sweep of the TV's own /24 and passive NOTIFY listening; it accepts only a MediaServer with a ContentDirectory on a private address and lists its folders. It is not a proxy and does not stream. Privileges: internet and application.launch only. Not run on the Frame; the Unified TV Frame app is unchanged until the probe works there. CI uploads it as `UnifiedTV-DLNA-POC-unsigned-test-only`.
- **My Photos on the Wallpaper, Phase 1** (Android TCL/Sony; design approved 25 October 2026): the adult profile can choose NAS picture folders over DLNA, found without typing, saved encrypted. Pictures are fetched at up to 1920 × 1080 and turned upright; portrait pictures sit whole over a soft blurred fill; the clock goes in the calmest spot. With the NAS off: kept copies if "Keep a copy" is on (off by default, about 200 MB), otherwise Artwork, never a blank screen. Child (and any future Guest) profile always gets Artwork. The screen states that DLNA pictures are visible to any device on the home network. Unit-tested only; not run against a real NAS or TV.
- **Ring rules RING-01..05** (owner, 25 October 2026), Android TCL/Sony: ordinary motion is ignored; a confirmed person shows only a discreet 5-second toast naming the doorbell (one per minute, never during an emergency); a doorbell press opens the live camera as a large picture-in-picture card that leaves playback visible, closes with Back and says "Live view unavailable" if no picture arrives. Smoke and CO still come first. A "person at the door" TEST button joins the alarm tests. Unit-tested only; the Frame follows.

### Changed

- **Register status names** follow the v3.3 developer pack, as the owner chose on 25 October 2026: `test_passed_nonphysical`, `physical_verified` and `unsupported_verified` replace `automated_pass`, `hardware_pass` and `unsupported`. The pack has no failed or deferred status, so a failed TV test stays `implemented_unverified` with the failure written down. The 44 rows whose automated tests pass are now `test_passed_nonphysical`; none is `physical_verified`.

### Fixed

- **Smoke/CO alarms, Android and Frame** (from an independent review of the alarm code against the spec; unit-tested only):
  - An alarm that cleared and re-sounded while the TV was disconnected now brings the full screen back, unacknowledged. A Home Assistant restart resets its times, so an acknowledged alarm still sounding may ask to be acknowledged again: deliberately on the safe side.
  - An alarm missing from Home Assistant's list on reconnect (HA still starting, or an integration failed) is no longer cleared by its absence; it stays, marked "Detector not responding", until its detector reports off.
  - Redrawing the alarm screen keeps focus on the same detector's Acknowledge, so OK can't acknowledge a different alarm.
  - A refused Home Assistant token during an alarm keeps the alarm on screen with "alarm status unknown", instead of closing it.
  - Reminders arriving during an emergency are dropped, as the spec says, instead of appearing after it; a doorbell visit exactly 45 s old now gives the note ("under 45 seconds" shows the card).
  - Android only: detector warnings that change while the alarm screen is up are shown once it goes; a camera asked for while offline opens only if the connection returns within 45 s; no duplicate connections after a quick restart; turning alarms off forgets earlier detectors; the alerts service restarts if the system ends it.

## [0.5.0] - 2026-10-24

Android Preview **0.5** (`0.5-preview`, versionCode 5), "The Viewing Experience". Home now changes its whole scene with what is in focus and your films rise into view on Down. Unified TV can become the TV's real Home if an adult chooses it, and can always hand Home back. The NAS, Home Assistant and Jellyfin are found on the home network and set up without typing an address, and Jellyfin signs in with a phone. Code-checked and unit-tested only. Release notes: [PREVIEW_0_5_RELEASE.md](docs/PREVIEW_0_5_RELEASE.md). **Not yet installed on a TV:** see the [0.5 on-TV checks](docs/PREVIEW_0_5_TESTS.md).

### Added

- **One Home journey** (spec 4.6 and 32):
  - **Each app has its own light** (spec 32): SmartTube crimson, Netflix a dark red glow, Disney+ navy with a cyan bloom behind the dock, BBC magenta, Live TV violet, Music peach and pink. Soft light fields drift slowly (20 to 40 second paths) behind frosted glass. Any other app gets a quiet slate light.
  - **Focus changes the whole scene at once:** a 400 ms crossfade starts the moment focus moves; the latest app always wins, and the name and the tile's glow change with it. The focused tile has a crisp outline and a soft halo in the app's colour.
  - **Light on the TCL:** the light is a few pre-drawn images in their own layer behind Home, moved and blended by the system's compositor; Home itself doesn't redraw for the light at all (v3.3: "compositor-transformed"). The blurred glass is kept per app, so moving along the dock never re-blurs.
  - **Motion settings:** Settings → Appearance → **Keep the Home background still** stops the drift but keeps the crossfade; **Reduce motion** makes changes instant; **Lightweight rendering** uses still frames.
  - **Titles:** moving along your films settles for a moment, then shows that title's own poster, softened and darkened. Never another title's image.
  - **The dock** rests low and quiet, and rises when you move.
  - **Films & TV rises from Home:** press Down and a row of posters from your chosen collections appears, without leaving Home. OK plays a film or opens a series; Up or Back returns.
- **Two apps from one codebase** (owner decision, 25 October 2026):
  - **Unified TV Preview** (`io.unifiedtv.preview`) has no Home entry at all and keeps exactly the identity it had. The copy already on the TV stays installed and is never replaced or uninstalled.
  - **Unified TV** (`io.unifiedtv.launcher`) is the app that can become Home. It is signed with the owner's own durable key from its first install, so every later version updates it in place and keeps its settings. Key custody and backups: [SIGNING.md](docs/SIGNING.md).
  - A **review build** (`io.unifiedtv.launcher.devicecheck`) installs beside both for trying a build on a TV; [OWNER_TESTING.md](docs/OWNER_TESTING.md) has the Windows steps.
- **Specification v3.3** ([SPEC.md](docs/SPEC.md)): a short authoritative specification. The v3.2 contradictions are corrected where they stood in [PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) (change list: [SPEC_CHANGES_v3.3.md](docs/SPEC_CHANGES_v3.3.md)), and the earlier versions are archived in `docs/archive/`. [ACCEPTANCE_REGISTER.csv](docs/ACCEPTANCE_REGISTER.csv) is the only live status record, with one status vocabulary and one test-level vocabulary.
- **Wallpaper** (spec 33; OD-03, OD-04): when Home is left alone (after 5 minutes by default), the TV shows bundled artwork full screen with a clock, and no dock or status icons. Any button brings Home back exactly as it was, without opening anything; volume and power still work.
  - **The clock follows the picture:** it sits where the picture is calmest, with a soft shade on bright skies, and moves between the two calmest places on each picture so it never stays in one spot for hours. After half an hour it dims.
  - **Depth clock:** for a picture with a prepared mask, the large clock sits behind the subject. Without a suitable mask it falls back to the simple clock. No picture has a mask yet, and the TV never works one out itself.
  - **Settings → Appearance → Wallpaper:** Artwork or Off; depth, simple or no clock; start after 2, 5, 10 or 15 minutes or never; change picture every 30 seconds to 10 minutes; Start Wallpaper now.
  - Pictures crossfade, with a very slow zoom that Reduce motion turns off. Nothing is downloaded. This is not Samsung Art Mode.
- **Back leaves Unified TV when it isn't your Home screen** (found on the TCL, 25 October 2026): on the main screen, Back now returns to the TV's own Home, as any TV app does. Once Unified TV is chosen as the Home screen, Back at the main screen stays put with focus where it was (spec 28.3). Previously Back moved focus off the dock and never left.
- **Time of day** (spec 4.8): Home's light stays each app's own, with a faint wash that changes through the day: nothing by morning and day, slightly warmer and dimmer at dusk, deeper at night. Each change blends over 20 minutes, so nothing ever flashes. High contrast turns it off.
- **OLED care on the Sony:** after two minutes without a button press, Home dims gently, and any press brings it back at once. Everything in front of the background moves by two pixels every three minutes, so nothing sits on the same pixels for hours.
- **Matchday card** (spec 7): on a Tottenham matchday (UK date), a small glass card under the clock names the match ("Tottenham v Arsenal") with the UK kick-off time, then a red-dot **Live** during the match. It never shows a score or result, even if the calendar's text contains one, and it disappears 15 minutes after the scheduled end. It is worked out every minute, so Live and hiding never wait for the next calendar check. Up → **Matchday: Live TV** opens Live TV through the same protection check as the dock. No club crests are shown (they aren't licensed for the app).
- **Screen readers on Home** (spec 4.11): with TalkBack or another screen reader on, Home says the focused app and where it is ("Netflix, 2 of 6. Hold OK to edit."), or the focused film in Films & TV. Nothing changes when no screen reader is running.
- **G1 tools for the TCL** ([G1_TCL_HOME_TESTS.md](docs/G1_TCL_HOME_TESTS.md)):
  - **Check your remote's buttons** (Settings → TV & performance, review build only, spec 6.1): every key the app receives with its name, code, scan code and how long it was held; keys Android keeps (Home, voice, app shortcuts) are explained rather than counted as failures; leaving the screen is logged with the key just before it, to help find the button that once escaped to the TV's own Home. A copyable summary for your report.
  - **Measure Home** (Settings → TV & performance → Performance check): 10 seconds of frame timing (median, 95th percentile, worst, frames over 32 ms), button-to-screen time, and memory, shown on screen and saved on the TV.
  - **`preview/tools/home_recovery.py`** for the PC: shows which app answers the Home button, records the TV's own Home, and puts it back after a typed "yes", then checks it worked. It never uninstalls, disables or clears anything. Records go to the git-ignored `home-evidence/` with no addresses.
- **Doorbell card, restyled** (owner's "Front Door" mockup): a large glass-framed card in the middle of the screen with the camera's own name, a green **Live** label only once real frames are showing ("Connecting…" before), and the date and time ticking in the corner. Back closes it. An alarm still always comes first.
- **Household setup without typing:** the doorbell, person detection, camera, fixtures calendar and room scripts are chosen with the remote from your own Home Assistant, by name. One clear match is filled in and named on screen ("Found your doorbell: Front Door Ding"); otherwise you pick from a short list, with generic motion kept apart from person detection. The connection shows Not configured, Reachable, Authentication required, Authorised or Offline. Typing an entity ID stays as an advanced option. Xbox appears only for the Sony room, and TV Plus and Art Mode only for the Frame.
- **Newer doorbells:** doorbells that Home Assistant reports as event entities (newer Ring, Nest and UniFi integrations) now ring on the TV: each new press time is a press, and coming back from a restart is not.
- **Music: Now Playing** (from the owner's mockup): once notification access is allowed, Music shows what the playing app reports to Android: large album art, title, artist, album, progress and a glass control bar (Previous, Play/Pause, Next, and Shuffle when the app offers it), over the same art softened and darkened. Amazon has no public API, so this reads the TV's own media controls; notifications themselves are never read, kept or sent. Without access, a screen explains how to allow it, including the one ADB line for TVs without the setting. With nothing playing, it offers to open Amazon Music.
- **AirPlay casting state:** while an iPhone is casting through AirPin LITE, the header glyph brightens to full white with a soft halo. This needs AirPin to report its playback to Android and notification access; otherwise the glyph stays at its ready brightness.
- **AirPlay indicator** (spec 7.1, owner design 25 October 2026): when AirPin LITE is really offering AirPlay on the home network, an AirPlay glyph joins the VPN, voice and Settings icons in the header's glass group, solid white at about 70 % with a tiny shadow. If AirPin isn't running, has stopped or the network is down, the glyph is simply not there; there is no grey or error state. "Ready" means an iPhone could see the TV: Unified TV looks for an AirPlay service answering from the TV's own address, checked on opening Home and every two minutes. Settings → Connections shows the same status and opens AirPin LITE for setup; Up on Home offers "AirPlay from iPhone". The voice and Settings icons are now drawn shapes rather than text characters.
- **Wallpaper artwork record** ([ARTWORK.md](docs/design/ARTWORK.md)) and **visual reference audit** ([REFERENCES.md](docs/design/REFERENCES.md)).
- **Unified TV as the TV's Home, only if you choose it** (spec 29):
  - Only the launcher app has a Home entry, and installing it changes nothing: the entry ships switched off. CI fails any build where the preview has a Home entry or the launcher's is switched on (`preview/tools/home_check.py`).
  - **Settings → TV & performance → Make Unified TV the Home screen** switches it on and lets Android itself ask. If Unified TV isn't chosen, it is switched straight off again.
  - As Home, the Home button returns to the main screen; Back at the main screen never leaves it.
- **Open original TV interface** (spec 30): a small adult-only row offers the manufacturer's interface for now, or as the default again after a second confirmation.
- **Apps, and a Home you can change** (P05-03): a grid of every installed app plus Unified TV's shortcuts. Hold OK to open, add to or remove from Home, or see app information. Each room keeps its own dock.
- **Smoke and carbon monoxide alarms on the TV, above the doorbell** (owner decision OD-07, 25 October 2026):
  - Home Assistant smoke and carbon monoxide detectors are used automatically, from their own device class; nothing to type. It can be switched off in household setup. No detectors are assumed: the household currently has none connected.
  - Each alarm is listed separately with its room and the time it started, and is acknowledged on its own. Acknowledging never changes the detector.
  - After acknowledgement a banner stays on screen while any alarm is still active. A new alarm brings the full screen back.
  - A detector that goes offline is shown as a separate warning, never as an alarm and never as "safe". An alarm whose detector stops answering stays an alarm.
  - The doorbell never covers an alarm: a visit during one is shown once every alarm has cleared (or noted, if it was more than 45 seconds ago). Reminders are dropped.
  - The TV asks other apps to step back so the alarm can be heard; whether they pause is up to each app.
  - **Test the alarm screen** in household setup simulates smoke, carbon monoxide, an offline detector and the doorbell, all labelled TEST.
  - It adds to your alarms and never replaces them. Not yet tested on a TV.
- **Watching films from your NAS** (P05-07, P05-08):
  - Pause, resume and skip with the remote; hold Left or Right to skip faster.
  - Choose a film's soundtrack and subtitles with Down or Menu.
  - Films remember where you stopped, on this TV only, and offer to resume.
  - Plain messages with Try again replace endless spinners when the NAS stops answering or a file can't be played. A file the TV can't show fails plainly instead of playing sound over a black screen.
  - Standby and switching apps pause the film and keep its place.
- **Your chosen collections only** (P05-05): the library stays inside them, Back returns focus to where you were, and the NAS is found again if its address changes.
- **Readable titles:** file names such as "The.Film.2019.mkv" show as "The Film 2019"; titles such as "S.W.A.T." are left alone.
- **Updates that keep your settings:** the preview can be signed with your own durable key through four `UNIFIEDTV_PREVIEW_*` settings. Without them it is signed exactly as before.
- **Creating the signing key** (`preview/tools/make_preview_key.py`): one command on your own computer makes the key in a private folder outside the repository, never replaces an existing one, and stores the four GitHub secrets through the GitHub CLI when asked.
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
- **NAS search messages:** the screen now says whether no server was found, a server didn't let the TV see its library, it isn't answering, it refused the TV, or a chosen folder has gone, each with Try again.
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
