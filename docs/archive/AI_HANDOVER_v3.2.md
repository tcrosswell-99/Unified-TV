# Unified TV — AI Handover v3.2

**Date:** 25 October 2026. **Scope:** guidance for Claude and parallel coding agents. **Important:** this file is not a fresh checkout or real-device audit. The retained v0.4 handover below is a dated snapshot and must not be treated as current repository truth.

## Read order and source authority

1. Latest explicit owner instruction and repository `AGENTS.md`/safety policy.
2. `Unified-TV-Specification-v3.2.md` **v3.2 implementation authority** followed by relevant detailed v2.x sections.
3. `CLAUDE_BUILD_PLAN_v3.2.md` for **implementation order and release gates**.
4. `FEATURE_STATUS_AND_ACCEPTANCE_v3.2.md` for **historical evidence, current unknowns and test IDs**.
5. Inspect current repo files: `docs/PRODUCT_SPEC.md`, `docs/FEATURE_STATUS.md`, `docs/ROADMAP.md`, `docs/DESIGN_SYSTEM.md`, `docs/COMPATIBILITY.md`, `docs/ALL_TV_TEST_PLAN.md`, `preview/THIRD_PARTY.md`, plus build and CI files. Do not overwrite them blindly; reconcile with other agent's work.

## Confirmed owner decisions (do not reopen)

- **TCL:** owner wants Unified TV as the selected default Home after independent rollback is demonstrated and the owner chooses it. Do not silently switch Home or remove factory launcher.
- **Next priority after TCL:** animated six-palette ambient mesh and Wallpaper before the NAS viewing milestone; keep NAS work and security requirements intact.
- **Wallpaper:** curated licensed artwork is the default source; do not default to Synology family photos or bundle supplied design mockups as wallpaper assets.
- **Depth clock:** default Auto on photos with suitable foreground masks; fallback to simple/no clock when unsuitable. The user does **not** want per-album opt-in as the default.
- **Samsung:** prepare a separate controlled-test proposal for CleanFrame-style Home reassignment; no unsupported native writes or test execution without later explicit authorisation.
- **Remotes:** Claude helps the owner identify TCL and Sony exact models/remotes later at the physical gate. The TCL escape-causing button is still unknown; capture it during G1.

## v3.2 owner additions — mandatory

- **Emergency alert priority:** any authenticated and verified smoke or carbon monoxide alarm is **highest priority**, above Ring doorbell/person and all other household notifications. Both alarm types have equal priority and must remain distinct if concurrent. Source availability, stale states and tests must not be represented as a real emergency or as safe/clear. This TV app supplements, never replaces, local certified audible alarms. Test foreground, DRM apps, sleep and Samsung Art Mode; be candid about unsupported overlays/wake. See `SAFE-01..10` in the specification.
- **Signing for upgrades:** the owner wants stable signing so new APK/WGT versions can update installed apps without losing configuration. Inspect existing app/package IDs and certificates first. Propose a private, owner-controlled Android release keystore and Samsung Tizen author/distributor certificate custody, version increments, N→N+1 in-place tests and backups. No key generation, key exposure, uninstall or re-signing of existing installed apps without owner approval. Android and Tizen require separate signing systems. See `SIGN-01..08`.
- **Agent task order:** signing audit/key strategy belongs to G0. Emergency arbitration must be designed before Ring is considered complete at G6, although UI/visual work may continue in parallel.

## Repository and last known baseline — **revalidate**

- Public repository previously reported: `https://github.com/tcrosswell-99/Unified-TV` (verify access/branch/current state).
- Last handover (24 October) described `feature/preview-0-4-settings-discovery` and base `feature/unified-tv-first-preview`, but **this is historical**. No fresh GitHub checkout or commit inspection was performed for this v3.0 documentation update.
- Preview 0.4 was reported as Android source with compiled/simulated functionality, not full household acceptance. Samsung files were a partial untracked scaffold, not a completed signed native application. Do not assume either remains unchanged.
- Owner's three actual TVs: TCL 55-inch Android TV 11 (exact model/remote unknown), Sony BRAVIA 65-inch OLED Google TV (exact model/remote unknown, Xbox HDMI 3), Samsung Frame QE50LS03AAUXXU 2021 firmware T-NKM2DEUC-2310.0 with 123 keypad Smart Remote (remote part number unknown).
- Device addresses, certificates, credentials, private photos and connection data belong outside Git; do not copy them into this handover or public issues.

## Agent coordination and branch discipline

- `git status`, branch, remote, latest commits, PRs, untracked files and test commands **first**; never force-push, hard-reset, clean untracked files or rewrite another agent's branch.
- One agent owns HOME variant/remote root navigation, another may own media adapter, another Samsung feasibility, another visual state machine, subject to explicit shared-file ownership. Coordinate changes to manifests, Settings, shared contracts and docs.
- No implicit permission to push, merge, install on physical TV, change default HOME, change firmware or use CleanFrame native helper. Ask owner at each privileged/irreversible step.
- Make minimal incremental changes, retain prior release, update evidence matrix in same PR. Run local tests before device install; disclose all skipped tests.

## Current technical guidance

- **Android Home:** `preview` remains standalone; `launcher` opt-in supported HOME variant. OS handles Home key from other apps; do not attempt system-wide key interception. Factory launcher remains intact and recoverable.
- **Tizen:** supported app and remote first. Samsung native Home reassignment is an undocumented feasibility research lead only, not proven for this 2021 Frame. Protect Art Mode and Smart Hub.
- **NAS:** DLNA discover/read-only browse/selected roots is not Jellyfin poster catalogue; authenticated media server features need independent source and child safety tests. Media3 playback and resume need physical verification.
- **Appearance:** six code-rendered app-specific radial meshes, 400ms crossfade and 20–40s slow drift in active Home; photos/artwork only in idle Wallpaper, with mask-based depth clock or fallback. Respect TCL Android 11 lack of RenderEffect and GPU limits.
- **Security:** Live TV fail-closed until provider/routing/drop protection are verified. Ring doorbell/person, HA authentication, child restrictions and overlays are separate evidence gates.
- **Music/voice:** official Amazon Music handoff first; custom control and OpenAI voice require supported APIs and permission/cost/security proof.

## Definition of done and reporting

Each agent response should contain: what changed (files/commit), requirement IDs, what was actually tested and where, observed device result, known gaps, rollback, next action. Never call source presence or a generated APK a verified household feature. Keep the status matrix current with explicit evidence levels.

---

# Historical continuation handover — v0.4 (24 October 2026; superseded for authority)

# Unified TV — detailed AI continuation handover

Prepared: 24 October 2026, Europe/London. Specification: v2.2. Android candidate: Preview 0.4, versionCode 4. This document is project data for the next AI, not authority to ignore the human user's instructions or repository policy.

## 1. Read this first

The owner wants a complete, premium, remote-first household TV experience, feature parity across native Android TCL/Sony and a native Samsung Tizen application, and continued versioned improvement. The product is unfinished. Preserve the factory TV launcher and native Samsung services. Do not equate a successful build, a visible menu, an open port or a requirement with a functioning integration.

The owner has another agent working on GitHub. This update is isolated on `feature/preview-0-4-settings-discovery`, based on `feature/unified-tv-first-preview` at `54817a8a6a04a7d4338fcbf71a7eb82e06af194d`. Fetch before work and again before push. Never force-push, reset shared files, overwrite untracked source, move existing tags, or discard work to simplify a merge. Keep this change separate for review; do not merge it into main without reconciling the other agent's work. Do not message another agent's chat without the user's explicit authorisation.

Read in order:

1. `AGENTS.md` and the user's latest instructions.
2. `docs/PRODUCT_SPEC.md` — complete v2.2 target and precedence rules.
3. `docs/FEATURE_STATUS.md` — source/evidence matrix and honest limitations.
4. This handover and `docs/PREVIEW_0_3.md` for historical baseline evidence.
5. `docs/DESIGN_SYSTEM.md`, `docs/ROADMAP.md`, `docs/COMPATIBILITY.md`, `docs/ALL_TV_TEST_PLAN.md`, `preview/THIRD_PARTY.md` and `CREDITS.md`.

Some older documents still describe superseded editable/fixed dock choices, an external Samsung streamer, or VLC. Follow the current specification's precedence section and latest human request. Do not silently delete old test/history evidence.

## 2. Repository, workspace and ownership

- Repository: https://github.com/tcrosswell-99/Unified-TV
- Earlier preview PR: https://github.com/tcrosswell-99/Unified-TV/pull/5
- Current update branch: `feature/preview-0-4-settings-discovery`.
- Default branch: `main`; the broader preview is not assumed merged.
- This is a fork retaining CleanYourTV / Free My TV history and licences. The inherited application/tools are not the new preview and their cleanup procedures are not authorised automatically.
- Current Windows workspace is the generated project under Documents/Codex; repository is its `work/Unified-TV` child.
- Private local inventory and network evidence are in the sibling `work/household-private` directory outside the repository. Inspect privately when necessary; never publish it.
- User-facing documents and prior APK/screenshot bundles are in workspace `outputs`, also outside the repository.
- Existing untracked `samsung/` is a partial scaffold. It was deliberately left unstaged for this Android update because another agent may work there. Inventory it and coordinate file ownership before editing. Its package.json scripts reference tools not present in the scaffold; it is not a working package.

## 3. Latest decisions you must preserve

- Native Android on TCL/Sony; separate native Tizen Web Application on Frame. A native APK cannot run on Tizen.
- Keep factory Home, Samsung Smart Hub, native Art Mode, TV Plus and existing apps intact. No factory replacement or bloatware cleanup in the preview.
- Six initial dock choices: SmartTube, Netflix, Disney+, BBC, Live TV, Music. Sony adds Xbox; Frame adds TV Plus where supported.
- Latest user request restores editable Home via Apps long-OK Open, Move, Add/Remove from Home, App information. This supersedes v2.1 fixed-membership rules. Current code still implements the fixed set; the next implementation must change DockPolicy and tests deliberately.
- Premium glass, gentle top light, cached blur, lifted tile/soft shadow, brand-aware depth, quiet UK clock and downward chevron. No permanent instructional text, harsh lock takeover or flat blue settings screen.
- Settings categories: Connections, Profiles, Notifications, Appearance, TV & performance, Privacy & security, with a changing right pane.
- Unconfigured UPnP/DLNA automatically discovers and lists servers. Select by name; no typed address for the default DLNA journey.
- Media scope is exactly `/video/Kids`, `/video/Movie`, `/video/TV Show`. Do not explore or index other NAS folders, including local music. Translate these to the real server's discovered object IDs locally.
- Amazon Music only for first-version music. Official app installation/use is authorised where supported. Custom account playlists/search/player are desired but require real supported access; do not fabricate them.
- Owner authorised the five supplied AI-generated images as public repository examples. They are copied unchanged into `preview/app/src/main/assets/gallery/example-1.jpg` through `example-5.jpg`. Preserve image 2's existing border and all aspect ratios.
- Household addresses, hostnames, credentials, device IDs, account data and signing material are local setup only, never public defaults, logs, docs or screenshots.
- Version each update and retain GitHub versions/artifacts. Capture revised UX only after testing the revised APK; do not relabel 0.3 screenshots.
- Smoke/CO emergency event journeys supersede Ring; Ring press AND person detection remain required event journeys. Live TV must never be unlocked solely because a tunnel is observed.

## 4. Evidence and current device state

### Last physically verified Android baseline

Preview 0.3 commit `54817a8a6a04a7d4338fcbf71a7eb82e06af194d` passed three GitHub Actions jobs:
https://github.com/tcrosswell-99/Unified-TV/actions/runs/40123046697

Canonical package `io.unifiedtv.preview` was installed on the authorised Android TV 11 TCL in an earlier session. Limited Home, focus/material, reorder and NordVPN UI observations were made. Factory Home remained `com.google.android.tvlauncher/.MainActivity`. No broad playback, Ring, Alexa or all-TV acceptance was passed.

Historical 0.3 APK SHA-256:
`eafec7549af4e27f32ca50d5cefd43b72c0929e21403a51c2377f4e8f2d58763`

Existing `outputs/Preview-0.3-screenshots` belongs only to 0.3. Some private connection screenshots are deliberately outside public outputs. Never publish a NordVPN screen showing household addresses.

### Preview 0.4 evidence

Local commands completed: testDebugUnitTest, lintDebug, assembleDebug and assembleDeviceCheck. XML reports total 37 tests, zero failures/errors/skips. Tests added for DLNA local-origin restrictions, XML external-entity refusal, metadata escaping, Settings focus and distinct VPN labels. These are JVM/simulated Android checks, not hardware evidence.

Latest TCL ADB connection timed out and a bounded port check did not connect. No 0.4 installation, real NAS playback or revised screenshots were performed. Try a fresh connection if the owner has restored access; do not repeatedly block development on the TV.

### NAS

The owner restored Synology Media Server during this session. Standard SSDP discovery from the PC received a MediaServer response and a read-only description request verified ContentDirectory. Previously only the NAS administration discovery service existed. The media description URL differed from guessed paths; use discovery rather than hardcoded paths.

This verifies service presence from the PC only. Folder object IDs are not selected/saved on the TV. The NAS administration UI must not be treated as Jellyfin. Do not change NAS firmware. The private inventory holds model/software details and addresses if needed.

### NordVPN on TCL

Earlier verified installed version: 9.15.2-tv. Its own supported connection UI connected to a UK endpoint using NordLynx. Auto-connect and split tunnelling were observed off; Android always-on/lockdown were not configured. The standard VPN Settings intent did not resolve on this TCL firmware. No privileged settings were changed.

Transport visibility does not identify the VPN provider, protected app routes or a functioning kill switch. Live TV remains blocked pending real route/drop evidence. Do not bypass this gate to claim completion.

### Samsung

PC read-only metadata confirmed Developer Mode enabled and developer port reachable. Samsung model and local address are in private evidence. Official SDB 4.2.36, extracted from the official Tizen SDK 10.0 CLI download, failed to connect. The reported host-PC field was zero; owner was asked to set their PC address and reboot. No success has subsequently been established.

No app is installed on Frame by this work. Certificate profile, permitted install, signed WGT, UI, remote, AVPlay, native TV Plus, Art Mode, startup and audio behaviour all remain open.

### Sony

No physical test or installation established in this session. Do not infer Sony success from TCL.

## 5. Code map and implementation details

### Android preview

- `preview/app/build.gradle`: application versionCode 4, `0.4-preview`, Java 17, compileSdk 35, minSdk 28, targetSdk 33. Media3 1.5.1, OkHttp 4.12.0. Verify versions before migrations; don't upgrade unrelated dependencies during this handover.
- Main Java source directory is `preview/app/src/io/unifiedtv/preview/`, not `src/main/java`. Source set recursively includes `src`; putting debug-only Java under `src/debug` can accidentally include it in main. Use a distinct directory/sourceSet for any debug setup bridge.
- Manifest is `preview/app/AndroidManifest.xml`, not src/main. Assets are standard `preview/app/src/main/assets`.
- `UnifiedPreviewActivity.java`: custom Canvas Home, focus/hold/reorder/back logic, services menu, package discovery, default shortcuts, material drawing and launcher-foreground idle art. In 0.4 Music goes through MusicActivity; additional observed BBC/SmartTube package IDs are recognised.
- `DockPolicy.java`: still fixed approved membership, room-specific extra, restores removed defaults. Latest editable Apps requirement not yet applied.
- `Screen.java`: native setup controls; `autoFocusButtons` allows Settings to avoid stealing category focus while rebuilding right panel.
- `SettingsActivity.java`: two-column category layout and existing controls redistributed. Category focus previews panel; Right/OK enters; Left/Back returns. Test larger text and actual remote navigation.
- `MaterialScene.java`: original ambient gradients, cached bounded low-resolution blur, glass/panel backgrounds. No live full-screen expensive blur.
- `Vault.java`: encrypted local JSON records; keep setup secrets here and backup excluded. Do not add exported configuration receivers or log records.
- `Endpoint.java`, `LocalApi.java`, `PrivateDataSource.java`: exact configured origin, explicit trusted-LAN HTTP consent, refusal of redirects, bounded metadata and authenticated native streaming.
- `ConnectionsActivity.java`: HA/Jellyfin credentials; discovered HA can pre-fill a new empty setup but never silently saves or consents. Uses secure-window flag and does not save credential view state.
- `ServiceDiscoveryActivity.java`: Android NSD `_home-assistant._tcp.` discovery, 12-second discovery window and advanced fallback; selected service launches Connections. Real HA authorisation is still absent.
- `DlnaClient.java`: local HTTP(S) URI validation, hardened namespace-aware XML, bounded description/SOAP response, MediaServer SSDP M-SEARCH, same-sender location check, maximum eight description candidates and bounded work. ContentDirectory service versions 1–3; browse 24 children; accepts same-host local HTTP video resources only.
- `DlnaLibraryActivity.java`: automatic discovery when unconfigured, choose server/folders, save encrypted root IDs/names, selected-root viewer and direct-play intent. Child profile blocked. Folder-name traversal state is memory only. No public household default.
- `LibraryActivity.java`: configured Jellyfin catalogue; now redirects to DLNA discovery/viewer when no media account is enabled or librarySource is dlna. Poster/catalogue route remains distinct from primitive DLNA folder UI.
- `PlayerActivity.java`: Media3 Jellyfin direct stream + attempted session progress, and separate DLNA direct stream. DLNA validates stored source and same host, derives exact allowed stream origin, uses no auth token, does not send Jellyfin progress. Validate full playback lifecycle and actual file support.
- `GalleryActivity.java`: encrypted selected URIs, document provider references; included coastal option and five authorised JPG examples; chosen interval; bounded decode; fit centre; generation check against stale images; pause/next/previous/back controls. No native Frame art integration.
- `VpnGate.java`: four-state asynchronous/cached Android transport observation; NordVPN standard launch; compact fail-closed Live TV sheet. No routing enforcement and no verified auto-connect API.
- `MusicActivity.java`: launch official installed Amazon Music TV app, else store search. No custom playlist/search/player implementation.
- `EditLock.java`, `MediaAccount.java`: adult edit PIN and server-account child verification; not a system kiosk.
- `HouseholdActivity.java`, `HouseholdService.java`, `AlertPolicy.java`: explicit private household setup, authenticated HA WebSocket, experimental camera/reminder overlays, priority/deduplication, retry and timeout. No invisible boot receiver or foreground guarantee after reboot.
- `VoiceActivity.java`, `VoiceCommand.java`: bounded speech-provider trial. No actual OpenAI session/relay or unrestricted model-driven home controls.
- `PerformanceActivity.java`, `InputStats.java`, `FixturePolicy.java`: read-only diagnostics and fixture policy, not validated broad optimisations/live sports feed.

### Tizen scaffold

`Samsung/` capitalisation is not used; the existing folder is lowercase `samsung/`. Current files include config.xml, package.json, public/index.html and src/net.js, platform.js, policy.js. There is no complete app.js/style/build/test pipeline. It is not installable/tested. Do not stage it wholesale or claim it ships in the Android release. Check for the other agent's ownership before continuing.

## 6. Known issues and next implementation targets

These are concrete open engineering checks, not all proven production bugs:

1. Editable Apps: replace the fixed membership policy with curated fresh defaults plus intentional owner edits. Add compact grid and one long-OK menu. Guard protected app launch both by canonical ID and installed package mapping. Do not duplicate the same default/installed app. Check overflow and empty dock recovery. Update fixed-policy tests at the same time.
2. DLNA browse pagination: UI currently decides Next from the number of accepted returned entries. Filtered audio/unsupported items can hide later pages. Parse NumberReturned and TotalMatches from SOAP and test sparse/mixed pages before calling browsing complete.
3. Discovery portability: real Android network/multicast behaviour under NordVPN is untested. Add a lifecycle-bounded multicast lock only if required; do not hold it indefinitely. Test multi-interface/default network routing, cancellation and retry queues.
4. Selected folder scope: confirm only exact owner-selected roots appear, Back never exposes the setup root in normal viewing, root IDs remain valid after NAS restart, and children cannot bypass the restricted-account route. Do not infer filesystem paths from numeric DLNA IDs.
5. HA NSD resolution: simultaneous service discoveries can fail on older Android if resolveService requests overlap. Test queue/retry and lifecycle restart; current timeout closes discovery but resolved buttons can become stale. Selection must verify reachability/authentication separately.
6. VPN status: four-state text was widened in the Canvas header and needs physical layout/contrast checking against voice/settings icons. Callback/cache concurrency and initial-state semantics need a stronger test. Do not describe the new icon as a kill switch.
7. Settings: simulated category/Right/Back checks pass; test all child controls, scroll/focus restoration, privacy-to-connections category selection and accessibility font scaling. Dialog cancellation should return to the same control.
8. DLNA player: direct play handles basic video only. Prove correct resume after Activity lifecycle, stalled connection, clean release, subtitle/audio selection, unsupported formats and errors. Redirect refusal is intentional. Do not permit arbitrary remote URLs to make a fixture pass.
9. Gallery: the current effect reveals each new image with an alpha animation, not a full old/new crossfade. Test background/foreground with pending decode, rapid remote next, pause timing, failed image, memory release, idle launch and every image including the bordered original. No crop/stretch changes to user images without request.
10. Library UI: DLNA buttons are a connectivity-first prototype, not the required premium poster collection. Build metadata/title models and native player controls before visual acceptance. Jellyfin Continue Watching remains unproven.
11. Music: official TV app availability and login must be checked. Do not use unofficial APK mirrors or reverse-engineer Amazon account APIs to fill a menu. Custom service entitlement is a genuine dependency.
12. Ring/reminders: test real entity/event source, press/person separately, stale/duplicate events, offline auth expiry, camera live readiness and exact prior-state recovery. DRM/system screens can restrict overlays. Tizen hidden JS cannot be trusted to receive events.
13. OpenAI: protected household relay, supported API, short-lived sessions, permissions, microphone lifecycle and narrow commands are required. Consumer ChatGPT access does not grant API entitlement.
14. Release signing: GitHub debug keys can differ per runner. Never uninstall a configured app just to install a different signature. Establish durable private signing separately; keep keystores and passwords out of Git. Use the local deviceCheck variant for coexistence when appropriate.

## 7. Build and test continuation

Run commands from repository root in PowerShell. Local toolchain is in sibling `../toolchain`:

```powershell
$env:JAVA_HOME = (Resolve-Path ../toolchain/jdk-17.0.20.1+1).Path
$env:ANDROID_HOME = (Resolve-Path ../toolchain/sdk).Path
& ./launcher/gradlew.bat -p preview --no-daemon testDebugUnitTest lintDebug assembleDeviceCheck assembleDebug
```

Expected output APKs:

- `preview/app/build/outputs/apk/debug/app-debug.apk` — canonical preview, locally test-signed.
- `preview/app/build/outputs/apk/deviceCheck/app-deviceCheck.apk` — separate local test identity.

Reports: `preview/app/build/test-results/testDebugUnitTest/`, `preview/app/build/reports/`. Count XML tests, do not infer counts from task names.

Linux/CI preview command: `bash preview/build.sh`. It runs tests/lint/build, verifies APK signature and package, checks no HOME intent and writes preview/out artifacts. It does not touch a TV.

Inherited repository checks: `python -m unittest discover -s tests -v`. Some inherited shell/tool checks fail on native Windows for platform reasons; Linux CI is the intended environment. Do not claim all repo tests passed from Android tests alone. The inherited lightweight launcher has its own build and 512 KB size constraint; the media preview is separate and not subject to that limit.

The normal CI workflow runs repository checks, inherited launcher tests/build and standalone preview checks. The new Android version release workflow builds from a retained `android-preview-v*` tag and publishes a prerelease with APK, hash, reports and these docs after its own preview checks. Check both run statuses; publishing does not replace full all-TV acceptance.

## 8. Safe device testing

Obtain current private targets from local owner setup rather than this public document. Use explicit target selection for every ADB command:

```powershell
$adb = (Resolve-Path ../toolchain/sdk/platform-tools/adb.exe).Path
& $adb connect '<TCL_ADDRESS>:<DEBUG_PORT>'
& $adb -s '<TCL_ADDRESS>:<DEBUG_PORT>' get-state
& $adb -s '<TCL_ADDRESS>:<DEBUG_PORT>' shell getprop ro.build.version.release
& $adb -s '<TCL_ADDRESS>:<DEBUG_PORT>' shell pm list packages io.unifiedtv.preview
```

Before installing, inspect installed package/certificate and private configuration state. Use `install -r` only with the matching key. Do not clear app data or uninstall a configured variant. A newly installed deviceCheck package has separate data and does not inherit the canonical preview's encrypted settings. Never pretend it does.

Test staged journeys: launch; Home focus and return; Settings categories/controls; discovery timeout; named NAS appearance; choose only requested roots; valid media play/pause/seek/stop; network failure; gallery rotation and controls; official app launch; protected Live TV still blocked; no crash. Capture logcat narrowly and sanitise; never publish Vault, tokens, NAS titles/account names or camera frames. Verify factory Home still opens.

After these pass, capture the revised UI from the same build, record version/certificate hash and label screenshots accurately. The owner expressly requested fresh screenshots after validation. Do not provide generated mockups as real app screenshots.

For Samsung use official SDB/Tizen tooling, not ADB. Toolkit available locally under sibling `../toolchain/tizen-sdk/tools/sdb.exe`; full certificate/deployment tooling still needs setup. Follow official certificate and device instructions. Certificate creation may require the owner to sign in personally. No WGT install has occurred.

## 9. Publication and parallel-agent protocol

1. Read status/diff and fetch remote before editing. Check current branch, base and exact remote tip.
2. Work on this isolated branch or another dedicated branch with clear ownership. Do not switch the other agent's worktree or reuse a branch they are actively changing.
3. Stage explicit owned paths. In particular, avoid `git add .` while untracked Samsung or private files exist.
4. Scan all staged text/binaries for private values, keys, endpoints, screenshots and accidentally included toolchain/build files. The five authorised example JPGs are allowed; private household images are not.
5. Run appropriate Android checks and review actual test totals. Preserve test evidence without publishing private logs.
6. Commit a concrete description, fetch again, verify non-fast-forward/overlap, then ordinary push. If rejected, inspect remote commits and reconcile; never force.
7. Open/update an appropriate PR and attach it to the current Codex chat when the app tool is available. Do not merge merely because tests pass.
8. Use a new immutable version tag for a tested candidate. Never retag a released version; fix forward. Tagged prerelease must say hardware unverified if that is true.
9. Ensure GitHub Actions ran for the exact commit/tag and inspect every job outcome. An artifact upload is not a successful NAS/TV test.
10. Provide owner confirmation with branch/commit/PR/release link, test results, APK/hash/docs, changed features and real blockers. Do not claim screenshots/installation that did not happen.

## 10. Recommended next work order

- Resolve remote/shared ownership and confirm the current publication status.
- Restore TCL developer connection and verify the actual 0.4 candidate without altering factory Home.
- Complete actual NAS folder selection and playback using the now-running Media Server. Fix sparse pagination and discovery networking if exposed by tests.
- Deliver the latest Apps/pin management requirement and update specification-policy tests together.
- Configure HA privately and test Ring/reminder journeys with real supported event sources.
- Prove VPN routing/drop protection before allowing protected Live TV. Keep blocked when protection cannot be proved.
- Install and sign in to official Amazon Music where supported; keep custom feature claims gated.
- Obtain fresh 0.4+ UX screenshots after physical checks.
- Continue native Frame work only after coordinating with the other agent. Developer Mode availability alone does not make it ready for deployment.
- Expand Sony/Frame acceptance and complete remaining voice/music/Art Mode/audio/performance gates.

## 11. Useful official references

- Samsung device setup: https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/tv-device.html
- Samsung certificate setup: https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html
- Samsung AVPlay: https://developer.samsung.com/smarttv/develop/api-references/samsung-product-api-references/avplay-api.html
- Media3 supported formats: https://developer.android.com/media/media3/exoplayer/supported-formats
- Synology service ports: https://kb.synology.com/en-global/DSM/tutorial/What_network_ports_are_used_by_Synology_services
- Home Assistant auth: https://developers.home-assistant.io/docs/auth_api/
- Amazon Music API: https://www.developer.amazon.com/docs/music/API_web_playback_v2.html

Recheck current vendor documentation before implementing a new external API. Source references do not certify hardware support or grant account access.
