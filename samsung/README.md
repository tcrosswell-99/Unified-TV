# Unified TV for Samsung The Frame (2021)

A native Tizen web application that brings the Unified TV Home to the owner's Samsung The Frame, model QE50LS03AAUXXU, firmware T-NKM2DEUC-2310.0. It follows the authoritative [specification](../docs/SPEC.md), sections 6.2, 7, 8, 10 and 11, and mirrors the Android Home as closely as a Tizen web app truthfully can.

> **Status: `implemented_unverified`.** Everything here is in source and passes its automated tests. **None of it has been installed or tested on the Frame.** No signed package exists. Nothing in this folder is evidence that any feature works on the TV. The acceptance register is the only status record.

## What is in it

| Area | What the code does | Where |
| --- | --- | --- |
| Home | Full-screen ambient mesh, 24-hour UK clock (Europe/London), small utility group (`VPN unknown` label, voice glyph, Settings), one glass dock with the app name above it, focused tile lift, outline and halo, a quiet Films & TV chevron | `src/index.html`, `src/css/home.css`, `src/js/app.js` |
| Dock | Netflix, Disney+, BBC, Live TV, Music and Samsung TV Plus by default. Long OK opens Open, Move, Remove from Home and Add an app. Removing never uninstalls. SmartTube is listed as unavailable (Android only), never as a fake tile | `src/js/apps.js`, `src/js/focus.js` |
| Ambient mesh | The same palettes as Android, from `src/palettes.json`. A test fails if they drift from `AmbientMesh.java`. CSS `radial-gradient()` lights on absolutely positioned elements. Two mesh containers crossfade by opacity over 400 ms, starting at once, and the latest focus wins. Drift uses `translate3d`/`scale` keyframes: 20–40 s paths, 2–5 % of the screen, scale 1.00–1.06. Nothing animates `filter` or `backdrop-filter`. The motion settings work as specified; a slow TV falls back to a still mesh | `src/js/mesh.js` |
| Remote | D-pad, OK, Back. Long OK has a fallback in case the TV never sends keyup. Only the player's media keys are registered, only where the TV says it supports them | `src/js/keys.js`, `src/js/nav.js` |
| Launching apps | `tizen.application.launch`, with ids found on the TV by name (`getAppsInfo`). The owner can pick a different app per tile in Settings, TV & performance, Advanced | `src/js/apps.js` |
| Live TV | Always shows the plain locked sheet. There is no Live TV client for the Frame and no proof that NordVPN protects it, so no route unlocks it in this build. Samsung TV Plus is not under this guard | `src/js/livetv.js` |
| Settings | Six categories (Connections, Profiles, Notifications, Appearance, TV & performance, Privacy & security), with one changing right panel. Everything works with the remote. Settings are saved with a versioned schema that never overwrites data from a newer version | `src/js/settings.js` |
| Secrets | The Home Assistant token and the chosen NAS folders go to Tizen Key Manager only. Without Key Manager this fails closed: nothing is written to `localStorage` | `src/js/secrets.js` |
| Alerts | Home Assistant WebSocket client (auth, `state_changed`, `get_states`) and a port of Android's `AlertPolicy`: smoke and CO binary sensors only, separate entries with room and time, individual acknowledgement, a banner while any alarm is active, offline warnings kept separate, "Alarm status unknown" on disconnect, Ring queued during an emergency (45 s rule), reminders dropped during emergencies, and a TEST alarm screen | `src/js/alerts.js`, `src/js/ha.js` |
| Wallpaper | A port of Android's `WallpaperPolicy`: bundled artwork, the clock in the calmest spot, depth clock only with a prepared mask (none exists yet). The first key press only leaves Wallpaper | `src/js/wallpaper.js` |
| Playback | AVPlay wrapper with an explicit lifecycle (play, pause, seek, stop, Back). It suspends when hidden, and refuses stream addresses that contain credentials or tokens | `src/js/player.js` |
| NAS | DLNA ContentDirectory browse over HTTP to an address the owner enters once. Only video items, at most three chosen folders by object ID, and paging by `NumberReturned`/`TotalMatches` | `src/js/dlna.js` |

## Honest limits (read these before testing)

- **Home and Smart Hub.** The physical Home key opens Samsung Smart Hub. A Tizen web app can't change that. This is a documented platform gap, not a defect. The app never registers or intercepts Home, volume, mute, power, Source, the voice key, channel keys, the 123 keypad, Exit or Art Mode.
- **Back.** Back unwinds one layer at a time. At Home, Back does nothing: Unified TV never closes itself from an ordinary Back press (PRODUCT_SPEC 30.3). A long Return stays the TV's own exit and is never intercepted. Samsung's own guidance for Back at an app's first screen may differ; NT-05 records what the TV actually does. Settings, TV & performance, Advanced, **Open original TV interface** explains the Home key and offers a deliberate **Close Unified TV**. It has no "make default" option.
- **Art Mode.** Unified TV's Wallpaper is its own screen inside the app. It is **not** Samsung Art Mode. It never calls itself Art Mode and never tries to start, stop or change Art Mode. Nothing here claims that any alert interrupts Art Mode.
- **Alerts only while the app is in front.** Tizen may suspend a hidden app, so the WebSocket is closed on purpose when the app is hidden and reopened (with a full `get_states` read) when it returns. There is no background overlay. Nothing is drawn over other apps, over Art Mode or on a TV that is off. This is a supplementary display, never a certified alarm. Ring cards have no live camera on the Frame yet.
- **No automatic NAS discovery.** A Tizen web app can't send the multicast UDP (SSDP) that finds media servers. The owner enters the NAS's description address once, and the on-screen "Find your NAS" text says why. Nothing pretends to discover anything. This departs from spec 9 ("no typed address for DLNA") because the platform can't do it. It is recorded as a platform gap for the owner to accept or reject.
- **Private playback is not built.** Only plain, unauthenticated DLNA file addresses play. Spec 9 requires a validated LAN media broker or a scoped cookie for Tizen private playback first.
- **Key Manager.** Whether Key Manager works for a developer-installed app on this firmware, and whether it is hardware-backed on this model, is unknown. If it isn't available, secrets are kept only until the app closes, and the screen says so.
- **App ids.** Netflix and Disney+ have widely reported Samsung app ids, kept only as fallbacks. Samsung TV Plus, BBC iPlayer and Amazon Music are found by name on the TV. No native id has been checked on this Frame.
- **Echo Studio.** Nothing here changes audio routing. Audio after Art Mode (AT-12) depends on the TV's own Bluetooth or optical setup.
- **Engine.** Scripts are ES2015, with no frameworks, CDNs or network fonts, because the 2021 Frame's browser engine is old. A test enforces this. Whether the real engine accepts every feature used is unverified.

## Privileges (config.xml)

| Privilege | Why |
| --- | --- |
| `http://tizen.org/privilege/internet` | Home Assistant WebSocket, the NAS description and Browse requests, AVPlay HTTP streams |
| `http://tizen.org/privilege/tv.inputdevice` | `tizen.tvinputdevice.registerKey` for the player's media keys only |
| `http://tizen.org/privilege/application.launch` | `tizen.application.launch` for Netflix, Disney+, BBC, Music and Samsung TV Plus |
| `http://tizen.org/privilege/application.info` | `tizen.application.getAppsInfo`, to find those apps on this TV by name |

`<access origin="*">` is needed because the Home Assistant and NAS addresses are entered on the TV and can't be listed in advance. The package and application ids in `config.xml` (`UTVFRAME00`, `UTVFRAME00.UnifiedTV`) are placeholders. Keep them or replace them **before the first signed install**. Never change them afterwards, or the TV treats an update as a different app (SIGN-05).

## Building and testing (no TV needed)

```text
node --test samsung/test/*.test.js     # Node 18 or later, no npm packages
bash samsung/build.sh                  # runs the tests, then builds the unsigned package
```

`build.sh` needs only Python 3. It writes:

- `samsung/out/UnifiedTV-Frame-unsigned.wgt`: a zip of the app. It is **unsigned and test-only**.
- `samsung/out/SHA256SUMS.txt` and `samsung/out/manifest.txt`.
- `samsung/out/UnifiedTV-Frame/`: the staged folder that the owner signs.

The package's Wallpaper pictures are copied from images already in the repository and recorded in [ARTWORK.md](../docs/design/ARTWORK.md), as listed in `wallpaper.json`. No image is committed under `samsung/`. CI (`.github/workflows/samsung.yml`) runs the same two commands and uploads the unsigned package as a 14-day test artifact.

For layout work, open `samsung/out/UnifiedTV-Frame/index.html` in a desktop browser. It says it is a browser simulation: there is no Tizen there, so nothing launches, no keys are registered and secrets stay in memory only. A browser view is never evidence for the TV.

## Installing on the Frame (owner only, on the owner's computer)

These steps need the owner's own Samsung account and certificates. An agent can't do them, and none of the certificate material may ever be committed, pasted into an issue or uploaded. Replace `<TV-address>`, `<profile>` and `<device>` with your own values. Keep those values in your private notes, not in this repository. Each step is reversible as described. Record results with [OWNER_TESTING.md](../docs/OWNER_TESTING.md).

1. **Tizen Studio.** Install Tizen Studio (or its CLI). In its Package Manager, add the **Samsung TV Extension** and the **Samsung Certificate Extension**.
2. **Certificates.** In Tizen Studio's **Certificate Manager**, create a **Samsung** certificate profile. You sign in with your Samsung account, which creates an author certificate and a distributor certificate that lists the TV's DUID. This is a development certificate for your own TV, not a distribution credential. Back up the author certificate privately, because same-app updates need the same author ([SIGNING.md](../docs/SIGNING.md)). Check the profile name with:

   ```text
   tizen security-profiles list
   ```

3. **Developer Mode on the TV.** Open Apps, type `12345` on the remote's on-screen 123 keypad, turn Developer mode on, enter your computer's address as the Host PC, then restart the TV. Developer Mode can be turned off again the same way.
4. **Build and sign** (from the repository root):

   ```text
   bash samsung/build.sh
   tizen package -t wgt -s <profile> -- samsung/out/UnifiedTV-Frame
   ```

   This writes a signed `.wgt` into `samsung/out/UnifiedTV-Frame/`. Keep its hash with your test notes.
5. **Connect, read-only first.** These commands read only; they change nothing:

   ```text
   sdb connect <TV-address>
   sdb devices
   sdb capability
   ```

6. **Install and open:**

   ```text
   tizen install -n samsung/out/UnifiedTV-Frame/UnifiedTV.wgt -s <device>
   tizen run -p UTVFRAME00.UnifiedTV -s <device>
   ```

   (`sdb install samsung/out/UnifiedTV-Frame/UnifiedTV.wgt` is the lower-level equivalent of the first command.) Use the actual `.wgt` file name that `tizen package` printed.
7. **Rollback.** Remove the app and leave developer access closed:

   ```text
   tizen uninstall -p UTVFRAME00.UnifiedTV -s <device>
   sdb disconnect <TV-address>
   ```

   Then turn Developer Mode off on the TV. Removing Unified TV changes nothing in Smart Hub, Samsung TV Plus or Art Mode, because it never touched them.

Nothing in this folder runs a native helper, writes Samsung settings or reassigns Home. That is a separate, unapproved proposal: [SAMSUNG_HOME_PROPOSAL.md](../docs/SAMSUNG_HOME_PROPOSAL.md).

## What to test first

Use NT-01 to NT-12, RC-09, HRC-07/08, OI-06, WP-12 and AM-01 to AM-12 in the [specification](../docs/SPEC.md). Record the model, firmware, remote, package hash and what you saw for each. The [capability table](../docs/devices/samsung-frame-2021.md) lists every capability as `unknown` until then.

## Licence

Part of Unified TV, under the repository's MIT licence. All code, icons and the generated app icon are original. There are no third-party logos, fonts or libraries.
