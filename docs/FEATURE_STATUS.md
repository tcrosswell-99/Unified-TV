# Unified TV — feature status for Preview 0.5

Date: 24 October 2026. Product specification: v2.2 consolidated draft.

Preview 0.5 changes are marked **0.5** below; they are code-checked and unit-tested only, with the on-TV checks in [PREVIEW_0_5_TESTS.md](PREVIEW_0_5_TESTS.md).

This report separates implemented source code from automated evidence and physical-TV acceptance. Preview 0.4 is a test build, not a complete household or all-TV release. No installation on Samsung is claimed. The latest TCL connection attempt failed, so the 0.4 changes have not been physically verified there.

## Evidence summary

| Evidence | Result and scope |
|---|---|
| Android Preview 0.4 local checks | 37 tests passed, 0 failures, 0 errors, 0 skipped; lintDebug, assembleDebug and assembleDeviceCheck succeeded. Covers policy, networking security and simulated Android journeys; not real TV acceptance. |
| Android Preview 0.3 baseline | Earlier GitHub Actions run 40123046697 passed all three jobs. Canonical preview was installed and limited remote/material/VPN journeys were inspected on the TCL. Existing 0.3 screenshots are retained separately. |
| Current TCL availability | Developer connection did not answer during this 0.4 update. No 0.4 install, playback or revised screenshot claim. |
| NAS media service | Owner restored Synology Media Server. PC received a MediaServer discovery response and verified its ContentDirectory service. No claim of TV discovery, folder selection or playback. |
| Samsung | PC verified reported Developer Mode enabled and reachable developer port. Samsung SDB connection failed; no installed Unified TV Tizen application. Host PC authorisation/restart still needs checking. |
| Sony | No physical testing recorded in this session. |
| GitHub Preview 0.4 | Source, docs and version are published through a separate feature branch. Actions and tagged release results must be read from GitHub; local checks do not imply remote CI passed. |

## Built and available in Android source

| Feature | What exists | Qualification / remaining test |
|---|---|---|
| Separate preview APK | `io.unifiedtv.preview`; Leanback/normal launch entry, no HOME intent; non-exported setup screens; separate local `.devicecheck` variant | Existing factory Home remains intact. Release signing and safe upgrades need a durable strategy. |
| Curated dock | SmartTube, Netflix, Disney+, BBC, Live TV, Music defaults; room-specific Xbox/TV Plus model | Current policy restores fixed membership. Latest requirement to allow add/remove through Apps is **not implemented**. |
| Remote movement/reorder | Left/right, OK, hold OK Move/Cancel, Back cancel, saved order/focus | Earlier simulated and limited 0.3 physical evidence; expanded Apps menu remains to come. |
| Context-aware visuals | Original service-colour ambient mesh, cached low-resolution blur, translucent material, fine top edge, lifted focus tile and contact shadow | Brand fallback is not streaming-provider recommendations. Full cinematic motion and visual acceptance remain open. |
| Neutral background | Bundled original coastal landscape | Static offline asset; unrelated scenery is not shown as a provider recommendation. |
| Header | UK 24-hour clock, utilities, downward chevron | The four-state VPN text layout added in 0.4 needs TV contrast/overlap checking. |
| Accessibility | Reduced motion, high contrast, reduced transparency, lightweight options | Full accessibility/TalkBack/distance audit unverified. |
| Two-column Settings, new in 0.4 | Six categories; changing right panel; category preview, Right/OK entry and Left/Back return | Simulated category/focus journey passes. Physical TV navigation and larger font scaling still need checks. |
| Encrypted connections | Local encrypted Vault, backup exclusion, masked/non-saved text fields, Test/Save/Forget, explicit trusted HTTP consent and refusal of redirects. **0.5:** a server found on the network, or already saved, is shown by name and its address fields stay hidden unless asked for; a saved connection opens straight away from Settings | Real service login on a TV is untested. Home Assistant still needs its access token typed once; a phone sign-in for it is not built yet. |
| HA service discovery | Standard local service search, named selection, connection pre-fill; advanced fallback. **0.5:** runs quietly in the background when Unified TV starts; one result is chosen automatically; if mDNS finds nothing, `homeassistant.local` and then a bounded check of port 8123 on the TV's own /24 (verified by Home Assistant's public manifest); searches bind to the home Wi-Fi/Ethernet network where the VPN allows it; overlapping resolves are serialised | Discovery does not save credentials or imply authentication. Not tested on the TCL, with or without NordVPN. |
| DLNA discovery | Automatic first-library search, named MediaServers, selection and encrypted folder roots, timeout/Retry. **0.5:** quiet background look-ahead; one server is chosen automatically; searches bind to the home network under a VPN; if multicast finds nothing, the same search is sent to each address on the TV's /24 | PC verified the NAS service, but the Android discovery path has not been TV-tested. |
| DLNA browsing | Read-only SOAP ContentDirectory, 24-entry requests, same-host local video resources, selected-root viewer. **0.5:** paging follows the server's own NumberReturned/TotalMatches (filtered music or photos no longer end the list early); titles show as poster cards with their own same-server artwork and year, never another title's | The full premium poster reveal, metadata/search/watch-state parity and real NAS playback remain open. |
| DLNA child protection | Unauthenticated DLNA path is refused in Child profile | Restricted Jellyfin account is the separate child route. Does not restrict other Android apps or OS navigation. |
| Embedded DLNA player, new in 0.4 | Native Media3 direct-play route; exact stream origin, no external VLC UI, redirect refusal | No real NAS playback, audio/subtitle/seek/codec matrix yet. DLNA does not report Jellyfin progress. |
| Jellyfin sign-in, new in 0.5 | The server is found on the home network (Jellyfin's own UDP discovery) and the viewer signs in with Quick Connect: a code on the TV approved in the Jellyfin app on their phone. No address, token or user ID typed. Child profile still refuses an administrator account | Needs Quick Connect switched on in the server. Not tested against a real server or on a TV. |
| Jellyfin catalogue | Server-authenticated film/series listing, paging, genres, search, seasons/episodes and title artwork | Needs a compatible configured server/account. NAS DSM UI is not Jellyfin. Child account identity/policy checks exist. |
| Jellyfin playback/progress | Media3 with authenticated data source; reads resume and attempts start/progress/stop updates | End-to-end progress and Continue Watching are not proven; do not label them synchronised until server evidence exists. |
| Five-image gallery, new in 0.4 | Owner-authorised example JPGs in assets; select all five, configurable 15/30/45/60/120-second interval, fit without stretching, pause/next/previous/Back | Simulated/local compilation only for new gallery behaviour. Fade is a simple image reveal, not a full two-image cross-dissolve. Frame native Art Mode integration is absent. |
| Gallery imports | Android document-picker references with persistent read grants, only offered where a picker resolves | Source list depends on installed providers. USB/network gallery support is not independently implemented or verified on TCL. |
| Foreground idle art | Existing launcher-foreground idle path to Gallery | No background OS screensaver replacement, no native Frame Art Mode takeover. |
| VPN handoff | Connect opens installed NordVPN; quiet Connect/Retry/Cancel sheet | No vendor silent-connect API. Prior TCL session connected NordVPN through its own UI. |
| VPN states | Distinct Connected, Disconnected, Checking and Unknown for Android transport observation. **0.5:** a status check started before a network change is discarded; the Live TV sheet says in plain words whether NordVPN covers this app | Connected does not establish NordVPN ownership, Live TV's routing, exit country or drop protection. Live TV remains blocked. |
| Protected Live TV | **0.5: separate from VPN appearance.** Strategy in [VPN_PROTECTION.md](VPN_PROTECTION.md): Android always-on NordVPN with *block connections without VPN* (only Unified TV exempt, for the home network), proven per TV by `preview/tools/vpn_check.py` reading the Live TV app's own sockets during playback and through a NordVPN drop, recorded by an adult in Settings. Live TV opens from the dock only while that proof's setup is unchanged (same app and versions, VPN on, this app's traffic on the VPN); a drop during playback brings the viewer back with a plain message. Voice and matchday routes stay locked | **Not tested on any TV.** No check has been run or recorded, so Live TV remains locked everywhere until one passes on that TV. The lockdown settings change needs owner consent and a reboot. |
| Music handoff, new in 0.4 | Truthful Amazon Music screen; official installed-app launch or store search | Amazon Music installation/login was not completed or verified on household TVs. Custom playlists/search/player are absent. |
| Profiles/PIN | Adult/Child choice, adult edit PIN, separate server-account checks | Not Android kiosk mode; cannot switch Netflix/Amazon accounts or lock system Home. |
| Ring experimental adapter | HA authenticated WebSocket, allowed off-to-on door/person edges, overlay permission and explicit foreground service, HLS camera path, dismiss/timeout. **0.5:** a person detection during a doorbell visit keeps the live stream and extends it instead of restarting it | Real camera, both event types, buffering, protected apps, resume and all-TV behaviour remain untested. No boot autostart. |
| Reminder experimental adapter | Private configured household events, short non-focus-stealing text overlay, lower priority than Ring. **0.5:** a reminder that arrives during Ring is held and shown afterwards if still relevant (spec 4.10, AT-50); a TV clock up to 5 s behind Home Assistant no longer drops every reminder | No verified Alexa source delivery or DRM overlay evidence. |
| Xbox/Frame action adapters | Explicit configured household scripts/actions | No verified console wake, HDMI sequence, Frame app launch, Art Mode return or Echo audio restoration. |
| Matchday | Fixture parsing and local label policy without scores | Needs real authorised fixture data; no verified automated live source. |
| Voice trial | TV speech provider with bounded commands, guarded Live TV, library query/manual camera routes | Actual remote microphone and recognition unverified; no general OpenAI voice/realtime integration. |
| Performance page | Read-only metrics/trial instrumentation | No automatic cleanup, package disabling, 60fps guarantee or measured broad performance claim. |
| App package correction | 0.4 recognises additional SmartTube and BBC packages previously observed on TCL | Correct launch/return must still be retested on the TV. |

## Features still to implement or finish

1. Compact Apps grid and the latest four-action long-OK menu; editable pins with persistence, adult protection and all Live TV paths guarded. Current fixed DockPolicy and tests must change together.
2. True integrated poster reveal, final title/detail transitions, accurate per-title metadata and a complete DLNA/Jellyfin source-selection experience.
3. Finish folder-scope setup on the TV using exactly the three requested collection roots. No household media source is preconfigured by this APK.
4. Prove representative playback: short/long HD and 4K, audio tracks, subtitles, seeking, server loss, unsupported codecs and clean return.
5. Verify progress reporting, resume and genuine Continue Watching on the actual configured account. Design an explicit local DLNA resume strategy if needed; do not claim server sync from it.
6. Complete Home Assistant authorisation and real household entity/script selection privately; improve owner setup beyond long token typing.
7. Prove Ring press and person detection, overlay priority, live first frame and restoration across real TV/app states.
8. Prove VPN app routing and no-leak-on-drop before allowing protected Live TV. Auto-connect and tunnel observation alone are insufficient.
9. Install/sign in to official Amazon Music where supported; investigate authorised metadata/control access before building the custom player and account search/playlists.
10. Complete optional OpenAI voice relay, ephemeral session security, microphone support, UK voice and safe command policy.
11. Complete native Tizen application: full UI entry point, styles/build scripts/tests, compatible secure storage, AVPlay authentication path, certificate setup, package/install and real remote tests. Existing local `samsung/` files are a partial, untracked scaffold and deliberately excluded from this Android publication to avoid colliding with another agent's work.
12. Prove native TV Plus launch/return, Frame Art Mode interruption/restoration, startup behaviour and Echo Studio audio route.
13. Sony hardware validation, Xbox wake/HDMI sequence, OLED safety and performance.
14. Complete time-of-day material refinement, final visual QA, focus latency/frame/memory measurements and a full accessibility audit.
15. Safe signed update/recovery strategy, retained releases, per-TV install instructions and new screenshots from the tested revision.

## Blockers and next evidence

| Gate | Current state | Next evidence |
|---|---|---|
| TCL device access | Port did not answer at latest check | Restore authorised developer connection; never reset or uninstall configured preview to solve signing differences. |
| NAS service | PC discovery and ContentDirectory verified after owner restored service | TV sees named server; select exact folders; play representative media. |
| Home Assistant | Not authenticated/configured on the TV in recorded evidence | Private supported authentication and test authorised API/events. |
| Samsung | Developer Mode on, SDB handshake failed | Verify Host PC authorisation/restart, certificate profile and permitted install. |
| Amazon custom API | No authorised access established | Obtain supported API entitlement or restrict v1 to official app and truthful limited controls. |
| VPN protection | Tunnel previously connected; routing/drop safety unproven | Controlled per-app route/drop test with evidence. |
| Revised screenshots | No 0.4 physical test completed | Capture after installing and validating exact 0.4 APK; publish only sanitised images. |

## Definition of fully working

A passing build means code compiled and selected tests passed. A feature is household-verified only after its acceptance journey passes on the named TV/runtime and build. A cross-TV feature is complete only when Sony, TCL and Frame results are recorded independently or the unsupported capability is explicitly agreed. Requirements and code presence must never be promoted to test evidence by wording alone.
