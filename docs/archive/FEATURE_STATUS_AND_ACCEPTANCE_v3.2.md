# Unified TV — Feature Status and Acceptance Matrix v3.2

**Date:** 25 October 2026. **Important:** status below is an evidence reconciliation, **not a fresh repository or physical-device audit**. The only supplied implementation-status evidence is the 24 October v0.4 report and continuation handover; v2.5–v3.2 specifications add requirements, not implemented features. Any work by other agents since that snapshot must be inspected before updating status.

## Status vocabulary

- `required_unimplemented_or_unknown`: required but not established in source.
- `reported_in_v0.4_source`: historical claim from v0.4 source status, **not freshly verified**.
- `implemented_unverified`: confirmed in current source after future audit but not tested on named physical TV.
- `blocked_external`: hardware, account, permission or service prerequisite missing.
- `physical_verified`: exact TV/build/firmware evidence exists and passed.
- `unsupported_verified`: proven not possible on a specific device/firmware, with evidence and approved product fallback.

**At v3.2 document creation, no new physical verification is claimed.** Existing v0.4 screenshots/mockups, build claims and PC discovery are not promoted to household passes.

## Reconciled high-priority matrix

| ID | Requirement / capability | Latest defensible status | Acceptance and next proof | Gate |
|---|---|---|---|---|
| G0-01 | Current Git/branch/build/package/signing inventory | unknown since v0.4 | Inspect repo, exact commit, manifests, signatures, tests | G0 |
| SIGN-01..08 | Stable owner-controlled signing and safe in-place APK/WGT upgrades on TCL, Sony and Frame | required_unimplemented_or_unknown; prior debug/CI signing may not be stable | Audit installed signatures/IDs and private key custody; physical N→N+1 update without uninstall or data loss; owner approval before generating keys | G0 + G1/G4/G5 release gates |
| G0-02 | Separate standalone preview and opt-in HOME launcher; owner selects TCL launcher after rollback proof | required_unimplemented_or_unknown; owner decision confirmed | Verify variants and restore original launcher on TCL before selection | G0/G1 |
| HRC-01 | TCL problematic physical button no longer strands user | unverified; owner observed failure | Reproduce button and film pass/fail | G1 |
| HRC-02 | TCL Home from Netflix/Disney+/BBC | unverified | Real TCL default HOME test and stock rollback | G1 |
| HRC-03 | TCL Back/root and repeated Back | unverified | Real TCL focus/Back matrix | G1 |
| HRC-04 | TCL standby/boot/process death/recovery | unverified | Physical TCL repeated tests | G1 |
| OI-01 | Discreet Settings original-interface option | spec-only | On-TV temporary visit vs restore default, cancel and rollback | G1/G5 |
| DOCK-01 | Editable six-app dock and long OK | v0.4 reportedly fixed dock; newer edits not verified | Inspect current code; move/pin persistence on TCL | G2 |
| NAS-01 | DLNA discovery and exact selected roots | reported_in_v0.4_source | TV discovery; selected root containment; auth/child checks | G3 Media |
| NAS-02 | Poster catalogue, search and Continue Watching | partial source claim; end-to-end unverified | Authenticated source, title match and server progress | G3 Media |
| PLAY-01 | Embedded NAS video/audio/subtitle/seek/recovery | reported_in_v0.4_source; hardware unverified | Actual file playback matrix on TCL | G3 Media |
| AM-01 | Six animated ambient meshes and 400ms focus crossfade; **next after TCL Home** | spec-only in v2.8; priority confirmed | Physical visual/frame/focus latency on TCL | G2 Visual |
| WP-01..16 | Wallpaper defaults to curated Artwork; automatic depth clock on suitable photos | spec-only in v2.9; owner defaults confirmed, not implemented | Source selection, mask suitability/fallback, privacy/OLED tests | G2 Visual/G4/G5 |
| SONY-01 | Sony default HOME, remote, Xbox HDMI 3 | unverified | Sony model/remote and physical acceptance | G4 |
| FRAME-01 | Native signed Tizen launcher and Smart Remote | v0.4 partial scaffold; no physical install proven | WGT install and key/return tests on 2021 Frame | G5 |
| FRAME-02 | Native Samsung Art Mode and TV Plus preserved | unverified | Standby/wake, source, Art Mode exact return | G5 |
| FRAME-03 | CleanFrame-style native Home reassignment **controlled-test proposal only** | proposal authorised; actual reassignment unverified and NOT authorised | Separate written compatibility/hazard/rollback/go-no-go proposal; explicit later owner permission before test | G5P |
| VPN-01 | NordVPN provider/routing/drop protection | blocked_external/unverified | No-leak drop test; fail-closed Live TV | G6 |
| SAFE-01..10 | Authenticated smoke and carbon monoxide emergency notifications, **highest priority above Ring**; equal-priority concurrent alarms and honest platform limitations | required_unimplemented_or_unknown; no real sensor/TV evidence claimed | Physical event source, arbitration/preemption, stale/test/unavailable states, DRM, standby, Art Mode, acknowledgement, no false all-clear | G6 (policy required before Ring release) |
| RING-01 | Doorbell and person events, live camera and restoration | v0.4 experimental adapter; unverified | Real event/video/return on each supported TV | G6 |
| PROFILE-01 | Child-safe NAS and profile separation | partial v0.4 source claim; unverified | Server-auth restricted account; no DLNA bypass | G6 |
| MUSIC-01 | Official Amazon Music app handoff | v0.4 source handoff claim; TV unverified | Supported app launch/return; custom APIs gated | G6 |
| VOICE-01 | Voice/reminders/other optional integrations | experimental/blocked | Service permission, privacy and device-specific evidence | G6 |

## Owner decision register (decisions are not implementation evidence)

| Decision | Owner-selected value | Effect | Verification status |
|---|---|---|---|
| OD-01 | TCL default Unified TV Home **after** rollback test | G1 physical acceptance, supported chooser and original-UI escape | Decision confirmed; no install/selection claimed |
| OD-02 | Animated Home and Wallpaper immediately after G1 | G2 Visual ahead of G3 Media | Decision confirmed; no build claimed |
| OD-03 | Curated Artwork Wallpaper default | First-run settings, licensed offline art | Decision confirmed; not implemented/verified |
| OD-04 | Automatic depth clock on suitable photos | Auto mask eligibility, simple/no-clock fallback | Decision confirmed; not implemented/verified |
| OD-05 | Separate Samsung controlled-test proposal | G5P research/proposal, **no test execution** | Proposal requested; not yet produced by coding agent |
| OD-06 | Claude-assisted TCL/Sony remote identification later | Device discovery at G1/G4; no invented mappings | Remote identifiers and escape button still unknown |

## Evidence record template

| Test ID | Device model | Firmware | Remote | Build / commit | Steps | Expected | Observed | Level | Evidence | Result | Blocker / rollback |
|---|---|---|---|---|---|---|---|---|---|---|---|
| _Example placeholder — not a test result_ | TBD | TBD | TBD | TBD | TBD | TBD | Not run | spec_only | None | UNVERIFIED | Awaiting access |

## Next evidence required

1. Fresh current repository audit and actual TCL/Sony model/remote inventory.
2. Owner preference for TCL default launcher is already confirmed; **prove recovery first**, then guide the owner through supported selection.
3. Identify the TCL button that previously exited Unified TV during physical testing; verify Home/Back and external app return.
4. Actual Synology DLNA root discovery, representative media playback and honest catalogue capability.
5. Android/Tizen visual-performance measurements; Samsung native installation, Smart Remote and Art Mode tests.
6. Separate security acceptance for VPN, **smoke/CO above Ring**, child restrictions and private photos; do not claim certified safety delivery.
7. Verify protected Android/Tizen signing identity and same-signature in-place N→N+1 updates, without uninstall or loss of owner settings.

## Completion rule

A requirement can be marked `physical_verified` only with evidence on the specified television and exact build. Platform parity is a collection of independent TV results, not a single green checkbox. The older v0.4 status is retained below as historical context; where it conflicts with this matrix, this matrix controls.

---

# Historical Preview 0.4 source-status snapshot (24 October 2026; not current acceptance)

# Unified TV — feature status for Preview 0.4

Date: 24 October 2026. Product specification: v2.2 consolidated draft.

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
| Encrypted connections | Local encrypted Vault, backup exclusion, masked/non-saved text fields, Test/Save/Forget, explicit trusted HTTP consent and refusal of redirects | Real service login/configuration is incomplete. Token entry remains for HA/Jellyfin; not a fully automatic account-pairing experience. |
| HA service discovery, new in 0.4 | Standard local service search, named selection, connection pre-fill; advanced fallback | Discovery does not save credentials or imply authentication. Actual TCL discovery/authentication not tested. |
| DLNA discovery, new in 0.4 | Automatic first-library search, named MediaServers, selection and encrypted folder roots, timeout/Retry | PC verified the NAS service, but the Android discovery path has not been TV-tested. VPN LAN rules/multicast may affect it. |
| DLNA browsing, new in 0.4 | Read-only SOAP ContentDirectory, 24-entry requests, same-host local video resources, selected-root viewer | Initial folder/button UI, not the final poster-first library. Full pagination and empty/filtered-page behaviour require stronger fixtures. Metadata/search/watch-state parity are not supplied by DLNA. |
| DLNA child protection | Unauthenticated DLNA path is refused in Child profile | Restricted Jellyfin account is the separate child route. Does not restrict other Android apps or OS navigation. |
| Embedded DLNA player, new in 0.4 | Native Media3 direct-play route; exact stream origin, no external VLC UI, redirect refusal | No real NAS playback, audio/subtitle/seek/codec matrix yet. DLNA does not report Jellyfin progress. |
| Jellyfin catalogue | Server-authenticated film/series listing, paging, genres, search, seasons/episodes and title artwork | Needs a compatible configured server/account. NAS DSM UI is not Jellyfin. Child account identity/policy checks exist. |
| Jellyfin playback/progress | Media3 with authenticated data source; reads resume and attempts start/progress/stop updates | End-to-end progress and Continue Watching are not proven; do not label them synchronised until server evidence exists. |
| Five-image gallery, new in 0.4 | Owner-authorised example JPGs in assets; select all five, configurable 15/30/45/60/120-second interval, fit without stretching, pause/next/previous/Back | Simulated/local compilation only for new gallery behaviour. Fade is a simple image reveal, not a full two-image cross-dissolve. Frame native Art Mode integration is absent. |
| Gallery imports | Android document-picker references with persistent read grants, only offered where a picker resolves | Source list depends on installed providers. USB/network gallery support is not independently implemented or verified on TCL. |
| Foreground idle art | Existing launcher-foreground idle path to Gallery | No background OS screensaver replacement, no native Frame Art Mode takeover. |
| VPN handoff | Connect opens installed NordVPN; quiet Connect/Retry/Cancel sheet | No vendor silent-connect API. Prior TCL session connected NordVPN through its own UI. |
| VPN states, new in 0.4 | Distinct Connected, Disconnected, Checking and Unknown for Android transport observation | Connected does not establish NordVPN ownership, per-app routing, exit country or drop protection. Live TV remains blocked. |
| Protected Live TV | Explicit launch gate across current launcher and local voice routes | No protected playback enabled until no-leak/drop tests pass. This is an intentional release gate, not a broken button to bypass. |
| Music handoff, new in 0.4 | Truthful Amazon Music screen; official installed-app launch or store search | Amazon Music installation/login was not completed or verified on household TVs. Custom playlists/search/player are absent. |
| Profiles/PIN | Adult/Child choice, adult edit PIN, separate server-account checks | Not Android kiosk mode; cannot switch Netflix/Amazon accounts or lock system Home. |
| Ring experimental adapter | HA authenticated WebSocket, allowed off-to-on door/person edges, overlay permission and explicit foreground service, HLS camera path, dismiss/timeout | Real camera, both event types, buffering, protected apps, resume and all-TV behaviour remain untested. No boot autostart. |
| Reminder experimental adapter | Private configured household events, short non-focus-stealing text overlay, lower priority than Ring | No verified Alexa source delivery or DRM overlay evidence. |
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

## v3.2 evidence additions

`SAFE-01..10` and `SIGN-01..08` are **required, not verified**. Do not record physical pass for a simulated Home Assistant alarm, a test event, a successful APK compilation or an unsigned WGT. Emergency alerts are supplementary; the primary audible smoke/CO detectors must work independently of TV power/network. Record alert source/type/freshness, priority against Ring, app foreground/background, TV power/Art state, whether an alert was actually visible/audible, and any OS limitation. Record signed build package ID, versionCode/version, public certificate fingerprint (not private material), target device and settings persistence for in-place update tests.
