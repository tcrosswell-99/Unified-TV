<!-- Unified TV: public reference specification. No account credentials, local addresses, family photographs or private configuration belong in this repository. -->

> **Status: design requirements, not shipping claims.** This specification describes a reference household and a reusable system. Hardware abilities and third-party integrations require real-device validation. No local network credentials are published here.

# Household TV Experience: Product and Technical Specification

**Version:** 3.3 detailed requirements (companion to [docs/SPEC.md](SPEC.md)) | **Date:** 25 October 2026 | **Status:** the full v3.2 detailed specification, corrected in place for v3.3. [SPEC.md](SPEC.md) is the short authoritative specification and wins any disagreement. Requirement status lives only in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv).

> **North star: Content first, underlying technology hidden.**
>
> The television should make it effortless to find and enjoy programmes, films, music and games. The person watching should not need to understand playback engines, the NAS, Jellyfin, Home Assistant, VPN connections or which operating system is running.

**Design baseline for the fork:** The quiet, full-screen, one-dock visual direction in the household's CleanFrame-style reference image is the approved starting point. A selected item must change the *whole* backdrop to imagery genuinely related to that app or NAS title. The detailed motion, glass, typography, progressive-reveal, accessibility and performance rules in Section 4 are mandatory acceptance criteria. Third-party references guide principles; our application must use original, appropriately licensed assets and its own implementation.

## v3.3 authority and owner decisions — read before the numbered sections

**Effective baseline:** 25 October 2026 (UK time), with eight owner decisions, OD-01 to OD-08. The short authoritative current specification is [SPEC.md](SPEC.md); this file keeps the detailed functional requirements and traceability behind it. Every numbered section below has been corrected in place for v3.3, so no section needs to be read through an override table; corrections are marked *(changed in v3.3)* and listed in [SPEC_CHANGES_v3.3.md](SPEC_CHANGES_v3.3.md). Requirement status lives only in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv); [FEATURE_STATUS.md](FEATURE_STATUS.md) is its readable summary and [AI_HANDOVER.md](AI_HANDOVER.md) the continuation guide. Hardware tests are run by the owner, who reports the evidence back ([OWNER_TESTING.md](OWNER_TESTING.md)). The v3.2 package (specification, build plan, handover and feature status) is kept in [archive/](archive/README.md) as history only and must not be used as instructions.

### Authority, terminology and conflict resolution

1. **Current human owner instructions** override this file. [SPEC.md](SPEC.md) also overrides this file; if the two disagree, follow SPEC.md and correct this file. Within this file each section states the current rule; do not silently drop older features.
2. **Product requirement ≠ source implementation ≠ verified physical result.** Record them separately. A simulated test, GitHub build, screenshot mockup, code present, or a feature in the spec does not establish working hardware behaviour.
3. **P0** means essential to its named release gate, **P1** means desired after the daily-use baseline, and **GATE** means an unresolved feasibility, safety or third-party dependency. Historical P0 labels are target criticality, **not instructions to implement every P0 feature in the next sprint**. The release gate order G0–G6 below (the same list as SPEC.md) is authoritative for sequencing.
4. **Device specificity:** owner's TCL Android TV 11, Sony 65-inch BRAVIA OLED Google TV and Samsung 2021 Frame QE50LS03AAUXXU firmware T-NKM2DEUC-2310.0 are separate acceptance targets. Capture missing TCL/Sony exact model, firmware and remote identifiers; do not guess key mappings. Samsung Tizen is not Android.
5. **Privacy:** keep IPs, credentials, device serials, certificates, photographs and local household configuration out of public source, CI logs, distributable APK/WGT and evidence screenshots. Sanitised fixture data and non-private design references are allowed subject to rights.
6. **No destructive shortcuts:** never disable the stock launcher, factory packages, Smart Hub, Samsung Art Mode, HDMI/CEC, volume, accessibility or security controls. No unattended ADB changes, unsupported native Home writes, service-menu edits or firmware modification. Preserve and prove recovery first.

### Explicit resolution of inherited contradictions

| Topic | Current rule (v3.3) | Where the older text was corrected |
|---|---|---|
| Android HOME vs standalone preview | **Two separately installed apps built from one codebase as Gradle product flavours** *(changed in v3.3)*. `preview` = `io.unifiedtv.preview`: **no HOME entry at all**, identity unchanged, stays installed and is never uninstalled. `launcher` = `io.unifiedtv.launcher`: declares a HOME activity-alias that **ships disabled** and is enabled only when the owner chooses **Make Unified TV the Home screen** in Settings; Android's own Home selection then decides. If the owner does not choose it, the alias stays withdrawn. The **Open original TV interface** escape hatch lives in the launcher. The launcher is signed with the owner's durable key from its first release (OD-08). Both apps must be independently launchable and recoverable. | Sections 0.1, 3.1, 28.1, 29.2, 29.3 and 29.6 now all state this rule. |
| TCL daily-use priority vs Preview 0.5 | **G1:** reproduce the remote escape, prove safe factory Home recovery, and test an owner-selected default launcher on the actual TCL. **G2:** Animated Home and Wallpaper, the owner-selected next work package. **G3:** Preview 0.5's NAS browse/playback journey. These are distinct evidence gates; neither may be called passed from source alone. | Sections 0.3a, 27.1 and the release gate list now give this order. |
| Samsung Home key | A standard Tizen Web App cannot be assumed to own reserved Home. CleanFrame documents an **unsupported, firmware-specific research lead on a different Frame**. The owner's 2021 Frame remains **unverified**. The owner authorises preparation of a separate, risk-reviewed proposal for a controlled test, **not execution**. No native write or Home reassignment may occur without a later, separate explicit authorisation after independent recovery evidence. Failure is a recorded product gap, not a hidden pass. | Older categorical 'never possible' and later implied 'solved for Samsung' are both too strong. |
| Native Art Mode vs Wallpaper | Samsung **native Art Mode must be preserved**; Unified Wallpaper is an optional app-foreground experience and must not pretend to replace system Art Mode. | Earlier generic ambient/Art language. |
| Background imagery | During **active dock navigation**, Section 32 code-rendered dynamic radial mesh and glass take priority. In **idle Wallpaper**, Section 33 curated artwork or authorised photos with optional depth clock take priority. Selected NAS title art belongs in its content browsing/detail context. | Earlier universal photographic Home-background proposals. |
| NAS catalogue | DLNA can discover/browse/play selected roots, but does **not** by itself deliver rich metadata, server-synchronised progress or child-safe access. Use an authenticated catalogue adapter where supported, otherwise label limitations. | Earlier requirements that imply DLNA discovery is complete catalogue delivery. |
| Music | First usable route is the **official supported Amazon Music app**; custom catalogue/search/Now Playing remains gated on authorised APIs. | Historical custom player as immediately buildable. |
| Remote buttons | Test the **actual three physical remotes**; OS-reserved keys and manufacturer shortcuts may remain outside application control. 'Always return' means **tested owner-selectable Home behaviour where supported**, not key interception inside other apps. | Generic remote assumptions. |
| Security | Live TV stays fail-closed until NordVPN routing/drop protection are physically proven; Smoke/CO emergency alerts supersede Ring overlays; child access and alert delivery require their own security and privacy gates. | Any UI status that appears to imply security verification. |

### Owner decisions — confirmed 25 October 2026 (binding for Claude)

| ID | Confirmed choice | Required implementation interpretation |
|---|---|---|
| OD-01 | **TCL: Unified TV becomes default Home once recovery is tested.** | Build and verify the separate launcher app `io.unifiedtv.launcher` (its HOME activity-alias ships disabled), prove an independent return to the factory launcher and the discreet **Open original TV interface** escape, then the owner chooses **Make Unified TV the Home screen** and Android's own Home selection decides. This is **approval of the desired end state**, not permission to alter the TV remotely or without recovery evidence. The preview `io.unifiedtv.preview` stays a separate ordinary app with no HOME entry *(changed in v3.3)*. |
| OD-02 | **After TCL Home, build Animated Home and Wallpaper first.** | Reorder delivery: TCL Home/remote reliability → six animated radial meshes and glass → curated Artwork Wallpaper and clock → automatic depth clock on suitable photos → NAS viewing journey. Keep media architecture research in parallel only if it does not slow or destabilise this sequence. Existing NAS work must not be removed. |
| OD-03 | **Default Wallpaper source: Curated artwork.** | First-run/default appearance is Artwork (when Wallpaper is enabled), not personal Synology photos. The artwork is free and **explicitly licensed for this use**, bundled offline (nothing is downloaded in real time to display it), with a sources/licence/attribution record in [ARTWORK.md](design/ARTWORK.md) *(changed in v3.3)*. Use the configured idle trigger. Never package reference/mockup imagery as licensed production art. Synology/My Photos is a separate later task whose access method (DLNA folders versus the Synology Photos API) must be designed first. |
| OD-04 | **Depth-effect clock: automatic on suitable photos.** | Default depth setting is `Auto`. Foreground occlusion is used only for selected artwork that ships with a **prepared foreground mask** made offline; there is **no real-time segmentation on the TCL** *(changed in v3.3)*. Every other image shows the standard clock (or no clock if chosen). Never fake occlusion. Respect reduced motion, OLED and privacy controls. |
| OD-05 | **Samsung: prepare a separate proposal for a controlled CleanFrame-style test.** | Produce a written compatibility, hazards, read-only checks, independent recovery, test steps, rollback and explicit go/no-go approval proposal for the owner's 2021 Frame. **Do not execute undocumented helper calls, native configuration writes or reassign Home under this instruction.** Continue supported Tizen work separately. |
| OD-06 | **TCL/Sony remote identification: Claude helps later.** | Do not block initial safe source audit or visual work waiting for remote photos/model numbers. At the physical-device gate, guide the owner through TV About screens and remote photos/part-number capture; use observed key logs to establish the exact mapping. Do not invent mappings or mark remote tests passed before identification. |
| OD-07 | **Smoke and carbon monoxide alarms outrank Ring.** | Smoke and CO are equal and highest (`EMERGENCY_SMOKE_CO`), above `RING_DOORBELL_PERSON`, `HOUSEHOLD_IMPORTANT`, `REMINDER_INFO` and `DECORATIVE`. Supplementary display only, never a certified alarm. Details below. |
| OD-08 | **Durable app signing and in-place updates.** | The owner creates a durable key on their own computer; the separate launcher app uses it from its first release; the existing preview stays as it is. Details below. |

**Unanswered diagnostic detail (not a reason to halt safe development):** the exact TCL button that previously escaped to the manufacturer's UI is not yet known. Capture it with the owner during G1 testing and add it as a named regression case; independently test Home and Back in the meantime.

**Authority:** these eight owner decisions, all confirmed on 25 October 2026 (UK time), override prior recommendations to build NAS immediately after TCL, make the depth clock opt-in, rank Ring as the highest alert, or limit Samsung work to research-only with no proposal. The safety requirement for *separate explicit approval before executing a Samsung test* remains unchanged.

### OD-07 — Safety alarm notifications outrank Ring (confirmed 25 October 2026)

**Highest priority event class: a verified smoke or carbon monoxide (CO) alarm from any owner-authorised sensor/integration.** This is higher priority than Ring doorbell presses, Ring person detection, reminders, media playback, VPN status notices and all animations. Treat smoke and CO equally as emergency-level alarms; do not delay either behind the other. Every section of this file now states this order *(changed in v3.3)*. **The app is a supplementary display, never a certified life-safety alarm, emergency dispatch service or replacement for working local audible alarms.** Never promise detection, delivery, TV wake or overlay over DRM/system content without physical proof.

**Priority order (highest first):** `EMERGENCY_SMOKE_CO` (smoke and CO equal) → `RING_DOORBELL_PERSON` → `HOUSEHOLD_IMPORTANT` (detector offline/fault warnings, VPN-protection notices, delivery/arrival) → `REMINDER_INFO` → `DECORATIVE`. A new emergency pre-empts any Ring camera/overlay, Wallpaper, media controls, animations or reminder **where the TV OS permits it**. Simultaneous smoke and CO alarms remain individually identifiable, with room/name and event time. Acknowledging one alarm never dismisses another active alarm.

**Ring during an emergency** *(changed in v3.3)*: Ring never covers an active emergency. A Ring press or person event during any active smoke/CO alarm, acknowledged or not, gets **no camera card**; it is queued, keeping only the latest visit and its time. When every alarm has cleared: if the queued visit is **under 45 seconds old**, the camera card is shown; otherwise a small note reads `Doorbell rang at HH:mm during the alarm`. The emergency display is never interrupted, and never dismissed by a timeout. **Reminders arriving during an emergency are dropped.**

**Event source and state model** *(changed in v3.3)*: the source is Home Assistant `binary_sensor` entities whose `device_class` is `smoke` or `carbon_monoxide`, from an explicitly configured, authenticated Home Assistant (or another approved integration). Never use a `sensor.*` CO entity: that is a ppm measurement, not an alarm. Standard states map as: `on` → **alarm**; `off` → **normal** (never shown as "safe" or "all clear"); `unavailable` or `unknown` → **detector offline**, a warning and not an emergency. `test` and `fault` are used only when a specific integration exposes them (usually as separate entities); none are assumed. **Freshness:** show the event time (Home Assistant `last_changed`, as local HH:mm); a lost Home Assistant connection shows **Alarm status unknown** rather than clearing an alarm. Test events are clearly labelled TEST. Record sensor type, room/name, state, source time and receipt time; never log addresses, tokens or sensitive household details. Reconcile duplicate and repeated events without suppressing a new genuine alarm. Never invent smoke/CO monitoring if no compatible sensor is connected.

**Current household situation:** the owner has **no smoke or CO detectors in Home Assistant** at present. The framework is built and tested with **simulated events**: an in-app **Test the alarm screen** action in adult household settings (labelled TEST) plus unit tests. Real detectors are not assumed. Owner test steps use a Home Assistant template binary sensor with `device_class: smoke` as a stand-in (see [OWNER_TESTING.md](OWNER_TESTING.md)).

**Emergency display** *(changed in v3.3)*: an unmistakable, high-contrast, large-type full-screen emergency state (as supported). Smoke and CO alarms appear as **separate entries**, each showing its type, room/name and event time (local HH:mm), for example `SMOKE ALARM — Kitchen — 14:32`. Each alarm is **acknowledged individually**; acknowledgement never changes, mutes, resets or disarms the sensor. Once every active alarm is acknowledged, a **persistent visible banner** stays while any alarm is still active, including when the viewer returns to the app; a **new alarm brings the full screen back**. Detector offline/fault warnings are shown separately as `HOUSEHOLD_IMPORTANT` notices, never styled as an emergency and never as "safe". There is **no "all clear"** claim. Never rely on colour alone; ambient mesh, depth clock and photos never obscure the message. **Audio:** the app may request transient audio focus with `USAGE_ALARM` where the platform supports it; it must not claim it can pause or mute third-party apps, which decide for themselves. Supplementary only; never a certified alarm.

**TV/platform constraints:** if the Unified TV app is hidden, asleep, in Samsung native Art Mode, or a DRM streaming app is active, OS restrictions may prevent the overlay or wake-up. Use only supported, permissioned platform/household event delivery mechanisms; record the actual limitations per TCL, Sony and 2021 Frame. On a fully powered-off TV, do not assume wake is possible or desirable. Native detector sirens remain the primary warning; a separate, independently tested household alert route is recommended. Emergency presentation must be tested against Ring events and all key viewing/standby states on the three physical TVs before any 'reliable emergency alerts' claim.

**Acceptance tests:** `SAFE-01` smoke event during Home; `SAFE-02` CO event during Wallpaper; `SAFE-03` smoke/CO pre-empts the Ring camera, Ring is queued (latest visit kept) and after every alarm clears is shown only if under 45 s old, otherwise as the `Doorbell rang at HH:mm during the alarm` note; reminders arriving during the emergency are dropped; `SAFE-04` concurrent smoke and CO remain distinct; `SAFE-05` offline (`unavailable`/`unknown`), test or fault never claims a genuine alarm or safety, and a lost Home Assistant connection shows `Alarm status unknown`; `SAFE-06` Netflix/other DRM foreground delivery and honest limitation; `SAFE-07` standby, native Samsung Art Mode and off-TV limitations; `SAFE-08` reconnect/duplicate/out-of-order events; `SAFE-09` per-alarm acknowledgement does not reset the underlying sensor and the banner persists while any alarm is active; `SAFE-10` accessibility, privacy and no false 'all clear'. Record source entity, event evidence, TV/firmware, actual overlay behaviour and limitations. No simulated event counts as an end-to-end detector pass.

### OD-08 — Durable app signing and in-place update continuity (confirmed 25 October 2026)

**A stable, owner-controlled signing identity is a prerequisite for installing the upgradeable daily-use build, not a last-minute release task.** The agent must audit current APK/WGT signatures and package IDs first, then propose a secure, durable signing strategy before publishing or replacing an installed app. Never generate a new random debug signing identity on each CI runner and call that an update strategy. Do not silently uninstall an existing installation or erase settings to work around signature mismatch. Keys are created only by the owner on their own computer with `preview/tools/make_preview_key.py`, never in CI or by an agent, and never committed; custody and backup are documented in [SIGNING.md](SIGNING.md) *(changed in v3.3)*.

- **Android TCL and Sony:** establish a protected release keystore/key alias and a stable application ID **per app** (`io.unifiedtv.preview`, `io.unifiedtv.launcher`), with versionCode monotonically increasing, reproducible versionName/build metadata and a signed release APK. An installed package can be updated in place only when the package name matches and the new APK is signed by the same key, or by a key in a valid rotation lineage. **APK Signature Scheme v3 supports key rotation from Android 9 (API 28), so Android 11 is not the obstacle; rotation requires the *old* private key to sign the new lineage** *(changed in v3.3)*. The existing preview was signed by a throwaway CI debug key that no longer exists, so it can never be moved to a new key. Hence the separate `io.unifiedtv.launcher` package, signed with the durable key from its first release, while the preview stays as it is. Never uninstall the preview or erase its settings to work around a signature mismatch.
- **Samsung Tizen:** create/use the valid Samsung/Tizen **author and distributor certificates** for the target Frame and preserve the correct author signing identity for future same-app updates; validate the target's device/profile constraints with Tizen tooling. Android APK signing keys cannot sign a WGT; Tizen author/distributor certificates are separate. Record the package/application ID and certificate continuity. A development certificate that installs today is not automatically a production distribution credential.
- **Secrets and custody:** keep keystore/certificate private keys, passwords, recovery material and device identifiers **outside Git, docs, screenshots and build logs**; use owner-controlled secure storage and protected CI secrets only after explicit owner approval. Do not print, paste, upload or commit secrets. Document secure offline backup and loss recovery. Never claim a lost signing key can always be recovered; if no compatible update path exists, stop and propose a non-destructive migration.
- **Verification:** build a signed version N, install it on an authorised test target, persist non-secret settings, install signed N+1 over it, confirm settings/launcher selection/permissions remain appropriate, confirm old app's recovery path and verify hashes/signature metadata. Test separately on TCL, Sony and Frame as their packages differ. A passing build without an actual same-identity update test is `implemented_unverified`, not release-ready.

**Acceptance tests:** `SIGN-01` key custody and no secret leakage; `SIGN-02` Android stable ID/version/signature audit; `SIGN-03` TCL same-signature N→N+1 in-place upgrade preserves settings and Home/recovery; `SIGN-04` Sony signed upgrade with settings and Home; `SIGN-05` Samsung author/distributor signed WGT N→N+1 retains settings and Smart Hub/Art Mode; `SIGN-06` mismatch handling does not uninstall/wipe existing build; `SIGN-07` signed artefact manifest/hash/version and independent rollback/recovery instructions; `SIGN-08` signing key backup/rotation feasibility documented without exposing key material. **Agents never generate signing keys: the owner creates them on their own computer with `preview/tools/make_preview_key.py` (see [SIGNING.md](SIGNING.md)).**

**Implementation precedence:** add `EmergencyAlarmEvent` and priority arbitration to the existing notification domain model and acceptance evidence, without duplicating current Ring coordinator classes. Add `SigningIdentity`/release metadata to build/release documentation (never include private key bytes). G0 must complete signing audit and key-custody plan before release packaging; the emergency-priority policy is P0 for any build that enables Ring or advertises household alerts. G1/G2 visual work may proceed without real sensors, but no alert feature may be labelled finished before `SAFE-*` tests. Existing Wallpaper and Ring acceptance tests must include the emergency-preemption check.

### Implementation architecture contracts (normative)

- **Shared domain model** independent of TV framework: `DeviceCapability`, `Profile`, `DockItem`, `NavigationDestination`, `FocusState`, `WallpaperSettings`, `AmbientPalette`, `MediaSource`, `MediaItem`, `PlaybackState`, `VpnEvidence`, `NotificationEvent` and `AcceptanceEvidence`. Schema shape is a *design contract*; agents must map to existing code rather than creating duplicate parallel models without review.
- **Capability registry:** `DeviceCapability` records platform, OS/firmware, remote mapping provenance, HOME ownership status, art-mode availability, native app launchability, voice availability, overlays, HDR/codec support, photo segmentation capability, and test-evidence timestamp. Values must be `supported_verified`, `unsupported_verified`, or `unknown`; never infer support from a platform name.
- **Navigation contract:** deterministic D-pad focus graph; OK/long-OK/Back semantics; Home is routed by OS where applicable. `HomeRoot` never accidentally finishes. External-app return restores safe root or last eligible focus; no stale modal, private profile leakage or surprise launch on wake.
- **Persistent settings contract:** per-device vs per-profile scope explicitly identified, versioned migrations, rollback defaults, encrypted secrets, no hardcoded household addresses. Persist dock order, appearance, idle interval, wallpaper source, clock style, permitted album IDs, profile selection and non-secret navigation state as appropriate.
- **Media adapter contract:** discovery, capability/authentication, browse/search, artwork, stream selection, subtitles/audio, progress and error states are separate operations. Each adapter must advertise unsupported features. DLNA ContentDirectory object IDs are not filesystem paths. Authenticated child access must be enforced by the server.
- **Events:** `RemoteKey`, `FocusChanged`, `ExternalAppLaunched`, `ReturnToHome`, `WallpaperEntered`, `WallpaperExited`, `PlaybackChanged`, `VpnEvidenceChanged`, `EmergencyAlarmEvent`, `RingEvent`, `SystemStandby` and `SystemResume` have explicit owners and cancellation rules. Security-critical events supersede decorative animations. Do not infer that the app can observe OS-reserved events.
- **Visual state machine:** `ACTIVE_HOME` (Section 32 moving mesh), `CONTENT_BROWSE` (item-matched artwork), `WALLPAPER` (Section 33 photos/art/depth clock). Transitions must be cancellable, focus-safe, accessible and memory-bounded. Reduced motion and low-performance fallback must work on Android 11 and older Tizen. Nothing depends on `RenderEffect`, which TCL Android 11 (API 30) lacks: render with GPU-composited, pre-rendered gradient layers (small pre-rendered light sprites moved and scaled), never continuously repainted full-screen gradients *(changed in v3.3)*.
- **Error/recovery contract:** offline Home remains usable; media and VPN failures present truthful retry/fallback; an unavailable NAS never blocks Home; no blanket exception swallowing. Distinguish transient failure, permission denied, unsupported platform and not configured.
- **Cross-platform boundaries:** share design tokens, state semantics, fixture contracts and acceptance cases; do not assume Android UI, background services, package intents or `RenderEffect` exist on Tizen. Keep native adapters isolated.

### Quantified interaction defaults and proof rules

- **Timing rule (one rule for all TVs)** *(changed in v3.3)*: a dock focus change starts the background crossfade **immediately**: **400 ms, ease-in-out**, latest focus wins, retargeting from what is visible and never queueing. **Ambient drift:** 20–40 s paths, centre shift 2–5 % of the screen, scale 1.00–1.06, independent of focus, never restarting on a focus change. **NAS title (content-browse) backdrops** may wait a **135 ms settle** because artwork decodes; the title's tile still answers at once. **Settings:** *Keep the Home background still* stops drift but keeps the 400 ms crossfade; *Reduce motion* makes the crossfade instant and stops drift; *Lightweight rendering* uses still frames and a 200 ms crossfade.
- First D-pad press waking Wallpaper must **restore controls without also activating a tile**. The depth clock must use a genuine foreground mask or honest fallback; never fake the effect over a face.
- Design target: responsive D-pad interaction and stable frame pacing on each physical TV. **60 fps is an aspiration**, not an assumed pass. Capture measured focus latency, frame drops, CPU/GPU/memory and crash logs per device/build before setting enforceable hardware-specific thresholds.
- Hardware tests are **run by the owner** from a Windows PC, because agents cannot reach the TVs; the owner reports the evidence back ([OWNER_TESTING.md](OWNER_TESTING.md)) *(changed in v3.3)*.
- Every release-critical requirement receives an evidence row with exact device, firmware, remote, package/build/commit, test setup, steps, result, recording/log, failure/rollback and reviewer. **No evidence = unverified**.

### Release gates (not promises of current implementation)

- **G0:** repository, branch and source audit; signing audit and key-custody plan (OD-08); safe package separation (preview versus launcher); known recovery and test inventory.
- **G1 TCL Home and remote:** the launcher app as owner-selected default Home where supported, problematic remote button reproduced/fixed or accurately classified, reliable Home/Back/third-party return/standby, discreet original-interface access and independently verified rollback.
- **G2 Animated Home and Wallpaper** *(changed in v3.3)*: six ambient palettes, the 400 ms crossfade and drift rule, calm tvOS-inspired glass, Wallpaper with curated Artwork, clock fallback and the depth clock for prepared-mask artwork, with real-device performance/privacy checks.
- **G3 NAS/media viewing** *(changed in v3.3)*: editable dock, app launch, authorised DLNA discovery and root selection, embedded playback/pause/seek/back, error recovery and clear empty states.
- **G4 Sony:** G1–G3-equivalent evidence, OLED safeguards and Xbox HDMI 3/audio handling.
- **G5 Samsung:** signed Tizen app, actual Smart Remote matrix, native TV Plus/Art Mode preserved, feasible Home return recorded truthfully, original-interface route, playback/performance. **G5P** is the separate CleanFrame-style controlled-test **proposal only**, with no native writes.
- **G6 household integrations:** VPN fail-closed Live TV, smoke/CO emergency alerts, Ring doorbell/person events and restoration, child safety, music, reminders, voice and optional tuning each pass their independent safety/feasibility gates. Do not label the whole household release complete until agreed required gates pass.

---

## 0. Earlier v2.x owner decisions (corrected for v3.3) and document precedence

This is the complete consolidated product requirement, including the detailed earlier visual, functional and acceptance sections below. It describes the target product, not a statement that every feature is built. Status lives only in `ACCEPTANCE_REGISTER.csv`; `FEATURE_STATUS.md` summarises it and `AI_HANDOVER.md` gives continuation instructions. [SPEC.md](SPEC.md) and the v3.3 authority section above take precedence; every numbered section has been corrected in place to agree with them *(changed in v3.3)*. Sections 32 and 33 are the latest visual-state requirements: Section 32 governs the active app dock; Section 33 governs optional full-screen wallpaper and photo-clock mode. The latter is not a replacement for native Samsung Art Mode. Section 27 defines implementation priority and evidence rules; it does not remove earlier functional or security requirements. Historical records are retained for traceability but are not instructions to implement superseded decisions.

### 0.1 Delivery and platform decisions

- Native Android TV application for TCL and Sony; separate native Tizen Web Application for Samsung Frame. Do not make an external streamer a prerequisite for Frame.
- Keep the factory Home application, native Samsung Smart Hub, Samsung TV Plus and native Art Mode intact. Two separately installed Android apps are built from one codebase *(changed in v3.3)*: the preview `io.unifiedtv.preview` has no HOME entry at all and stays installed; the launcher `io.unifiedtv.launcher` has a HOME activity-alias that ships disabled and is enabled only when the owner chooses **Make Unified TV the Home screen**, after which Android's own Home selection decides.
- Tizen startup, cross-app foreground launch, native Art Mode interruption, DRM overlays and Back behaviour are device test gates. Neither developer access nor a SmartThings API name proves these work. A cold boot and standby resume are separate tests.
- Feature parity means the same experience where supported, with truthful platform-specific availability. Do not fabricate SmartTube, VPN or native app support on Tizen.
- Install supported official apps where authorised and available. The owner's later request permits an independently installed VLC as an auxiliary tool, but Unified TV playback must remain embedded Media3/AVPlay. Do not require or invoke VLC as the library player.
- Work in versioned branches/releases; preserve previous releases, test evidence and rollback instructions. Never overwrite another agent's work, force-push, reset shared files, or merge unrelated work merely to publish a preview.

### 0.2 Latest UX decisions

- Keep premium liquid-glass material: feathered cached blur, light top edge, restrained reflection, lifted focus lens and soft contact shadow. Avoid flat opaque panels and harsh outlines except accessibility settings.
- Brand ambience retains depth; it must not become a heavy opaque red/blue overlay. Selected media artwork is correctly matched to its actual item.
- Active navigation uses the Section 32 moving ambient gradient mesh; optional full-screen Wallpaper mode uses curated artwork or owner-selected photographs and the Section 33 depth-layered clock. Do not place a large photographic clock behind the active app dock. Native Samsung Frame Art Mode remains independent and unchanged.
- No permanent directional text under the header. Use a discoverable downward chevron for Films & TV.
- Apps uses compact cards and a remote-friendly grid. Hold OK opens Open, Move, Add/Remove from Home, App information. Curated defaults are retained at first setup; owner edits are allowed by this latest request.
- Settings uses six left-hand categories and one changing right-hand panel: Connections, Profiles, Notifications, Appearance, TV & performance, Privacy & security. Right/OK enters controls; Left returns to categories; Back returns predictably without losing focus.
- Empty library/gallery screens introduce the benefit and offer useful setup. Sources are shown only where supported.
- VPN state has Connected, Disconnected, Checking and Unknown with a clear icon. Connected refers only to detected tunnel connectivity until provider, routing and drop protection have separately passed. No automatic Live TV unlock on a status icon.

### 0.3 Latest library and music scope

- On first library entry with no configured source, automatically discover local UPnP/DLNA MediaServers. The primary path is a named server list and selection; no typed address is required for DLNA.
- Save only explicitly selected folder roots. The reference household scope is exactly `/video/Kids`, `/video/Movie`, `/video/TV Show`; ignore every other folder and local music. These logical collection names are authorised product requirements, not hardcoded server defaults. Discover object IDs locally and store them encrypted; do not assume DLNA object IDs equal filesystem paths.
- The default must not browse all server media once a source is saved. A child profile may not use unauthenticated DLNA to bypass account restrictions. Keep server-verified restricted accounts as the separate child pathway.
- DLNA folder listing and direct play are an initial connectivity implementation. The agreed poster-first catalogue, title detail, series hierarchy, search and Continue Watching remain requirements. DLNA itself does not establish Jellyfin progress synchronisation or reliable metadata.
- A NAS administration page is not a media API. Discover the actual ContentDirectory service. Do not change firmware, index unrelated folders, install a heavyweight server without checking compatibility, or claim that any MKV must play without conversion.
- First-version Music uses Amazon Music only. Install/open its official TV application where available. Local music indexing is out of scope for this version.
- Custom minimalist Now Playing, account playlists and searches remain desired. Implement them only with supported, authorised service access. Normal consumer sign-in is not proof of access to Amazon's closed-beta APIs. Use the official app as the truthful interim destination.

### 0.3a Delivery focus adopted in v2.3

- **Preview 0.5, The Viewing Experience, is gate G3** *(changed in v3.3)*: it follows G1 (TCL Home and remote) and G2 (Animated Home and Wallpaper). Its aim is one complete remote-only journey on the physical TCL: launch Unified TV without disturbing the factory Home; browse a polished Home and editable Apps; discover the authorised NAS; select a title; play, pause, seek and return; recover from a network failure and standby. Capture fresh evidence from the exact installed build. See Section 27 for precise pass criteria.
- Prioritise **working viewing and reliable recovery over additional feature breadth**. The full Ring, VPN, Tizen, music, voice, accessibility, Art Mode and cross-TV requirements remain in scope, with their existing release gates. Do not misrepresent a Preview 0.5 slice as complete household or cross-platform delivery.
- Run a **parallel, bounded Samsung feasibility track** for signed install, remote navigation, AVPlay, app foreground behaviour, standby/cold boot and Art Mode/Ring recovery. Record unsupported platform behaviour early instead of assuming an API can force foreground launch.
- Maintain one shared **capability and evidence register** ([ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv), the only status record) with owner, device/firmware, build/commit, test level, observed result, artefact reference, blocker severity and next action. No simulated test, PC discovery or successful build counts as a physical-TV pass.
- Separate reusable product logic, household-specific adapters/configuration and per-device capabilities. Keep private inventory and signing keys outside the public repository and artefacts.

### 0.4 Images, private setup and acceptance

- The owner supplied five AI-generated JPGs and expressly authorised their inclusion as example gallery images in the repository. Preserve their originals, aspect ratio and existing borders; do not treat these as private family photos. Other private household photos/camera feeds remain excluded.
- Gallery rotates selected images, has timing and remote controls, no permanent UI, reduced-motion behaviour and sensible fitting. Local/USB/document-provider/network sources appear only when supported.
- Household hostnames, addresses, tokens, accounts, device IDs and signing secrets belong only in private local setup and encrypted device storage. Never publish them in Git, APK defaults, Actions logs, screenshots or handovers. Use symbolic placeholders in commands and keep the actual local inventory outside the repository.
- Testing must state the exact build and `test_level`: `none`, `unit`, `simulated`, `ci_build`, `pc_network`, `physical_tcl`, `physical_sony` or `physical_frame` *(changed in v3.3)*. These levels are not interchangeable; only a `physical_*` level with evidence can support `physical_verified`.
- Take revised screenshots only from the tested revised APK and label their version. Existing 0.3 screenshots must never be presented as 0.4 evidence.

## 1. Product scope and non-negotiables

Build a premium, remote-first, Apple TV-inspired home experience for three household TVs. The layout, motion language, media catalogue and primary controls should feel the same everywhere; only deliberate device-specific features differ. Keep the interface fast on all hardware: the TCL has limited resources, the once-premium Sony is now becoming sluggish, and Samsung Frame/Tizen should also benefit from appropriate maintenance.

**P0 means required for release; P1 means agreed feature that can follow the stable foundation; P2 means an optional enhancement.** A technical dependency marked **GATE** must pass on actual hardware before promising the feature is working. An existing product claim is not a substitute for a device test.

### Guiding rules

1. **Content first:** no advertising, sponsored recommendations, crowded menu panels or technical plumbing in the foreground.
2. **One consistent interface:** same 16:9 layout, artwork treatment, animations, navigation and shared preferences on Sony, TCL and Samsung.
3. **Ring is mandatory:** a doorbell press automatically interrupts active wallpaper / ambient / Frame Art Mode and opens the live camera without pressing the remote; confirmed person detection shows only a 5-second toast and ordinary motion is ignored *(changed 25 October 2026, RING-01..05, SPEC.md Section 10.6)*; restore the previous state afterwards. Ring is second in alert priority: a smoke/CO emergency always comes first and Ring never covers it *(changed in v3.3)*. Interrupting native Frame Art Mode needs a verified household coordinator and is unproven.
4. **Live TV is VPN-protected:** ReamSmartz must not send traffic without a verified NordVPN connection. This applies to all ways of launching Live TV, including the Spurs card and voice commands.
5. **Artwork is artwork:** on Samsung Frame Art Mode there is **no clock, VPN badge, widget, app row or permanent UI**. A smoke/CO emergency or a triggered Ring camera, where the platform allows it, is the deliberate exception.
6. **Hide the plumbing:** use embedded Media3/ExoPlayer on Android and Samsung AVPlay on Tizen. VLC is excluded from the application and playback path. Jellyfin / Synology / Home Assistant are not consumer-facing names in normal browsing.
7. **Optional performance cleanup on all TVs:** present one consistent, opt-in **TV Performance & Cleanup** entry for Sony, TCL and Samsung Frame. It must detect the underlying platform and show only operations proven safe and supported on that model/firmware. Android and Tizen have different permissions; a universal interface does **not** imply universal access to system packages. Never compromise DRM, remote, Wi-Fi, HDMI, Bluetooth, sound, Art Mode, updates, casting, accessibility or recovery.
8. **Alexa reminders:** while actively watching video, a due Alexa reminder should appear as a small, temporary toast above the picture, with its reminder text where the source exposes it. It must not pause playback, hijack focus or compete with a Ring event or an emergency. Device/OS overlay restrictions are an explicit release gate.
9. **Preserve Samsung TV Plus:** Samsung Frame keeps its existing native Samsung TV Plus service, with a Frame-only branded tile. This is a separate source from ReamSmartz and is not subject to the ReamSmartz NordVPN gate. The optional Samsung cleanup must protect TV Plus and all required dependencies.
10. **No fabricated capabilities:** distinguish desired behaviour from what each firmware, app and service has been tested to support.

### Reconciliation of the shorter v1.7 architecture proposal

The short v1.7 proposal contains useful technical suggestions, but **this full v2.0 document supersedes it without deleting previously agreed requirements**. Adopt local on-TV Connections and a safe Windows-to-TCL Stage 0.5. Evaluate optional AirPin LITE, split tunnelling, hybrid voice commands and Media3 as engineering alternatives rather than declaring them working.

Keep six shared entertainment tiles as an **editable default**, not an immutable allowlist. Keep Samsung's native Art Mode and Samsung TV Plus through hardware-specific Tizen/Android handoffs, the existing optional reversible cleanup, Alexa reminder toasts and both Ring triggers. Do not claim guaranteed sub-500ms 4K NAS playback, undocumented NordVPN control Intents or a VPN kill switch based only on a connection icon. See [v1.7 architecture review](ARCHITECTURE_REVIEW_V1_7.md).


### Consolidation of the latest native proposal

Version 2.0 incorporates the owner's shorter v1.9 proposal and retains the complete design, editable dock, profiles, accessibility, household features and acceptance history below. The chosen delivery is two native codebases: Android for Sony/TCL and a Tizen Web Application for the Frame. The same visual and remote contracts apply, with honest OS capability differences. Sections 24 and 25 define the revised native platform contract and evidence gates. Those sections supersede any residual historical reference to an external Frame streamer or VLC. No external box is required. The four newly supplied Samsung tests use NT identifiers because AT-58 to AT-61 already identify dock editing journeys.

The proposal's automatic boot launch, arbitrary MKV direct play, SmartThings force-closing Netflix, and cross-app Back return are desired outcomes to investigate, not guaranteed Samsung APIs. Keep Ring interruption mandatory as a release goal; an unproven launch/Art Mode route blocks that capability's release rather than being labelled working.

## 2. Devices and runtime

| ID | Device | Runtime plan | Specific features |
|---|---|---|---|
| TV-SONY | Sony BRAVIA 65-inch OLED, Google TV | Native Android TV launcher | Xbox tile; Xbox on HDMI 3 (eARC connector); OLED-safe ambient mode; **equal-priority performance baseline + optional Android cleanup** |
| TV-TCL | TCL 55-inch, **Android TV 11 confirmed from TV settings** | Same Android launcher, lightweight rendering profile | Equal-priority performance baseline; installed **AirPin LITE** receiver; optional Android cleanup only after independent tests and owner consent |
| TV-FRAME | Samsung The Frame, Tizen | Native Tizen Web Application; Samsung Smart Hub remains the system Home | Native AVPlay; model-gated Art Mode and Ring handoff; retain Samsung TV Plus; Echo Studio audio path subject to tests; supported-settings maintenance only |

**GATE-TV-01:** Record exact model, firmware, OS version, accessible memory and storage privately. TCL Android TV 11 is confirmed; a Google TV-branded interface is not assumed. **GATE-TV-02:** validate the native Tizen Web Application on the Frame. No external streaming device is required by this product plan. Android-only services remain explicit capability gaps until a legitimate native alternative is approved.

The rendering system uses one shared design and per-TV capability configuration, for example `sony`, `tcl`, `frame`. No separate home-screen design forks. Maintain independent volume, wallpaper, brightness and camera-overlay preferences per display. Shared media listings and app ordering should update across the household without exposing configuration tools to viewers. The performance tool should offer the same entry point and language across devices but use *device capability adapters*; future LG webOS, Fire TV and other brands require separate, tested adapters rather than pretending Android tooling runs on every OS.

### 2.1 Local connections and on-TV configuration (P0)

External developers and GitHub Actions cannot assume access to the household's private LAN. The user must be able to configure Home Assistant Green, NAS/Jellyfin and optional room devices entirely with the TV remote.

- **Settings > Connections** includes **Home Assistant**, **Your Films & TV**, and optional **Device Actions / AirPlay**. Each connection has Enable, Hostname/IP, Port, HTTP/HTTPS, Test, Save, Edit and Forget. The OS on-screen keyboard and D-pad must suffice without a Windows PC. Optional device discovery is advisory, never mandatory.
- Allow IPv4, IPv6 and hostnames including local mDNS names. Never hardcode the household's actual TCL, NAS or Home Assistant IP, Wi-Fi network, MAC address, credentials or camera URLs into public code, GitHub documentation or APK assets.
- Show **Not configured**, **Reachable**, **Authentication required**, **Authorised** and **Offline** accurately. Reachability is not proof that Ring triggers, NAS media streaming or Xbox control work.
- Protect credentials using Android Keystore-backed encryption or a protected household service. Never place tokens in plaintext preferences, logs, URL query strings or published screenshots. Prefer authenticated HTTPS; if trusted-LAN HTTP is the only option, require explicit user consent and scope it to the entered host.
- Per-TV credentials are private and separate. Children can still use Home normally while an adult PIN protects editing. Unavailable integrations must not prevent basic app launching or the dock from working.

**GATE-CONN-01:** use the TCL remote to enter valid and invalid HA/Jellyfin endpoints, test/save/forget and verify precise feedback. **GATE-CONN-02:** verify protected token storage, offline Home operation and no leakage to public diagnostics.

## 3. Home screen information architecture (P0)

**Primary 16:9 structure, from top to bottom and progressively revealed:**

1. **Subtle header:** one live 24-hour UK clock (default top-left, per the selected visual reference), with only the small truthful NordVPN state/exit flag, an optional ChatGPT glyph and a quiet Settings/More affordance at top-right. No news ticker, weather bar or second menu.
2. **Single glass app dock:** a centred, low-profile translucent strip in the lower portion of Home. The six shared choices are `SmartTube`, `Netflix`, `Disney+`, `BBC` (iPlayer), `Live TV` (ReamSmartz) and `Music` (Amazon Music experience). Sony alone adds `Xbox`; Frame alone adds `Samsung TV Plus`. **No VLC tile.**
3. **Your Films & TV:** one curated horizontal row of real Synology artwork with **View All**, *revealed by pressing Down* from the dock. It should not crowd the initial resting screen. The row rises naturally while the app dock becomes secondary; Up returns to the dock. The full catalogue is another deliberate action.
4. **Matchday card:** a small Tottenham Hotspur card appears only on the scheduled UK fixture date and only in a place that does not obscure the contextual hero. No fixture means no gap.
5. **Transient interaction layer:** smoke/CO emergencies, Ring live camera, Alexa reminders, VPN launch checks and voice UI arrive only at their respective moments. They do not occupy permanent content slots.

**Background contract:** if an app tile or NAS title is visibly selected, its own appropriate artwork or brand-aware fallback controls the full-screen backdrop. The user's scenic/family wallpaper is only for a genuine neutral/no-focus state or artwork mode, never a misleading background behind a highlighted unrelated item. This corrects the previous generic-landscape mockup mismatch. The detailed state machine, durations, visual tokens and acceptance rules are in **Section 4**.

**Progressive disclosure:** keep only one visual focus and one dominant image at a time. Do not add a persistent second poster shelf, editorial banner, rows of promotions or separate side menus to the default Home. The settings interface and full media catalogue are intentionally separate destinations.

### 3.1 Remote interaction contract (P0)

- Directional pad changes focus predictably; **OK** activates; **Back** returns one navigation level; **Home** behaviour depends on the authorised OS integration and is defined by the **remote ownership and system-key contract in Section 28**. The preview app `io.unifiedtv.preview` never registers as Home; only the separate launcher app `io.unifiedtv.launcher` can become the default Home, after the owner chooses it in Settings *(changed in v3.3)*. Samsung Smart Hub/system-reserved keys cannot be overridden by an ordinary Tizen app.
- Focus must be obvious even without background images: tile scale, elevation/contrast and accessible outline. Do not use colour alone.
- On entering Home, restore the last relevant focus position; when dismissing an overlay, restore the underlying screen and playback state.
- **Hold OK** on a Home tile to access **Move**, **Remove from Home** and optional **App info**; short OK always launches. A parental edit lock may require a PIN. All editing works with the ordinary remote.
- Fast repeated D-pad presses must stay responsive. Cancel or coalesce outdated artwork requests so focus is never blocked by network imagery.
- When content is missing/offline, show a short human explanation and a safe next action, never an exception stack or raw URL.

### 3.2 Samsung-only TV Plus user journey (P0, GATE)

- Home shows **Samsung TV Plus** only on the Frame, using its familiar service identity and the same premium focus animation as other tiles.
- OK transfers viewing from our custom launcher to **native Samsung TV Plus**; retain the native channel guide and account/service setup already on the television.
- If our launcher is running on a separate Android HDMI device, the bridge must command the Samsung native OS to launch TV Plus; HDMI/input and native-app switching is subject to model-specific Home Assistant/Samsung API testing.
- Ring is still mandatory while native Samsung TV Plus plays: it must trigger the automatic live camera takeover and return path. A standard Android overlay cannot render over the native Tizen app.
- Alexa text overlays during native TV Plus playback are not promised without an independently tested Samsung-supported mechanism; this limitation must be visible in compatibility documentation.
- The cleanup module must mark Samsung TV Plus and dependencies as **protected** and exclude them from disable/remove proposals.

### 3.3 Curated defaults and owner-managed Home

The latest owner request restores an Apps screen with Open, Move, Add to Home / Remove from Home, and App information. This supersedes v2.1's fixed-membership decision. Keep the six curated defaults on a fresh installation; permit deliberate owner edits and preserve those edits. Adding/removing a shortcut does not install/uninstall its application. Reordering is remote-only, with OK to save and Back to cancel. Apply the same protected Live TV gate in Home, Apps, voice, search and matchday routes. Child edits require adult authorisation. Platform-only shortcuts must not leak between room profiles. The 0.4 implementation still has the older fixed-membership policy; editable Apps is an outstanding requirement, not a completed feature.

## 4. Premium interface and interaction system (P0: design release gate)

### 4.1 Creative direction and what it is not

**Experience statement:** A personal cinema with the calm of a gallery. **Content first, underlying technology hidden.** The launcher must be a distinctive, original design inspired by the *principles* of the tvOS 26/27 Liquid Glass era, not a pixel-perfect reproduction of Apple's screens or code. Apple introduced the Liquid Glass redesign in tvOS 26; tvOS 27 is the current reference point for its continued refinement. Our primary non-Apple visual reference is the user-supplied image of a quiet coastal backdrop with one translucent dock, a clock and three small top controls. Supporting references are CleanFrame, Vision Stream and the TV Interface motion study. No reference artwork, film still, logo, proprietary typeface, animation file or UI layout is cleared for redistribution merely because it is visible online.

**The visible result must feel:** quiet, cinematic, light in visual weight, instantly responsive, confident at living-room viewing distances, and personal to the household. Fluid motion adds clarity, never delay. The same personality applies on Sony, TCL and the native Frame application. Samsung's *native* Art Mode remains separate and unmodified except for the required Ring takeover.

**Design priorities, in order:**

1. **Relevance:** The image filling the screen must represent the currently highlighted app, film or programme. A generic landscape behind a highlighted Netflix, BBC or NAS tile **fails review**. Scenic or family wallpaper belongs to the neutral or artwork state.
2. **Clarity:** Only one primary selection and one dominant visual story at a time. Users should identify the focused action in under a second from the sofa.
3. **Restraint:** One principal translucent app dock on the resting Home. No permanent walls of posters, sidebars, content adverts, weather bars, duplicate clocks or competing recommendations.
4. **Continuity:** Focus, artwork, metadata and transition elements respond as parts of one scene rather than as isolated animated cards.
5. **Responsiveness:** The remote takes precedence over animation and network imagery, particularly on the ageing Sony and lower-memory TCL.
6. **Context:** A relevant function appears when needed, then withdraws: NAS catalogue when pressing Down, Spurs card on matchday, Ring on an event, Alexa reminder when due, and voice only when invited.
7. **Originality:** Implement our own materials, spacing, silhouettes and timings. Never describe, market or package the launcher as an official Apple interface.

### 4.2 Screen geometry, visual hierarchy and safe space

Design at a **1920 × 1080 logical 16:9 canvas**, then scale proportionally to both 55-inch and 65-inch televisions, checking actual safe areas on each. Treat the outer ~4-5% of width and ~5-6% of height as a no-critical-content margin. Main artwork extends to every screen edge; key text, icons, tiles and alert controls do not. Fit seven app tiles on Sony/Frame without text truncation. Smaller screens retain the same proportions, not a compact web/mobile layout.

**Resting Home composition:**

- **Background:** one beautiful, full-bleed image covering nearly the entire screen, with local contrast treatments only where needed. Do not put a solid full-screen dark panel over it. This image is either the user's neutral wallpaper before a selection, or the *correct contextual hero art for the focused item*. Artwork may remain largely unobscured above the dock.
- **Clock:** one restrained, white, 24-hour UK clock, default **upper-left** to suit the chosen reference image; optional upper-right preference if desired. It is not a card. Never show seconds or permanently animate the digits.
- **Status/utility controls:** a very small, aligned upper-right group: truthful NordVPN state and verified exit-country flag, ChatGPT voice affordance, and a compact Settings/More control. Keep visual separation; no row of seven icons. An optional gallery shortcut belongs in Settings or as a single quiet utility, not as a second navigation bar.
- **App dock:** one wide, low, rounded glass strip spanning the lower third, but occupying only roughly 15-20% of screen height. Six shared service tiles are shown, plus the appropriate Sony Xbox or Samsung TV Plus tile. The strip is the *only significant glass panel* on resting Home.
- **Under-dock hint:** show a subtle, discoverable `Films & TV` / downward-chevron hint on initial use; retire or soften it after the user has successfully visited the NAS row several times. It must not resemble a separate content shelf.
- **Content metadata:** not permanently visible. Introduce only one short title/line when focus rests on an app or NAS title whose artwork is shown. Let the picture carry most of the meaning.
- **Spurs matchday:** an elegant temporary compact card, placed away from the dock and hero subject when a fixture exists; no reserved empty slot on other days.

**Default full screen rule:** no top navigation bar labelled Home/Movies/TV/Music, no fixed side rail, no multi-row recommendation grid and no multiple stacked translucent containers. Complex structure is revealed only when navigating into it.

### 4.3 Glass as a material, not a decoration

Our material should suggest polished frosted glass with gentle optical depth. The interface is **not** a collection of grey rounded rectangles.

- Use a charcoal-to-slate, wallpaper-aware translucent dock, feathered background blur and a very fine light top edge. Slight colour from the actual image should come through the material.
- The focused item becomes a *single lifted lens* inside the dock: a small increase in scale, marginally stronger translucency/highlight, a clean silhouette and a soft contact shadow. Unfocused items recede rather than dimming into illegibility.
- Do not draw an aggressive white outline around every tile. A subtle bright perimeter is allowed only for accessible focus confirmation, high-contrast mode or a selected tile on an unusually complex image.
- Use restrained reflection and soft specular movement when focus changes; no continuously wobbling blur, strong chromatic aberration, oversaturated blue neon or oversized glowing borders.
- For performance, prefer **cached or pre-rendered blurred materials** and opacity/translation/scale transforms. Never require real-time per-pixel refraction to maintain the chosen appearance.
- Material responds to background brightness. Text and focused outlines must remain readable against snow, bright beaches, white family photos, deep shadows and film backdrops.
- Surface boundaries should be consistent: small top utility chips (if needed), one dock, and a few temporary overlays. **Do not put glass behind every logo, poster and sentence.**

### 4.4 Focus model: the app or title transforms the whole environment

A visible highlight always controls the backdrop. **An image unrelated to the highlighted item is a defect, not an acceptable placeholder.**

**Sequence when the remote moves onto a new item:**

1. **0-80 ms:** immediately confirm the new focus through lift, brightness and scale. The input must feel acknowledged even on an offline TV.
2. **Background start** *(changed in v3.3)*: for a **dock (app) focus change**, start the background crossfade **immediately**. For a **NAS title in content browse**, the backdrop may wait a **135 ms settle** while its artwork decodes, which also prevents a blizzard of decodes when someone holds an arrow key; the title's tile still answers at once. Metadata follows the same settle.
3. **400 ms, ease-in-out:** crossfade from the visible background into the next, with a soft local vignette. The latest focus wins: retarget from what is visible, never queue. Ambient drift is separate (Section 32: 20–40 s paths, centre shift 2–5 %, scale 1.00–1.06, independent of focus, never restarting on a focus change). Do not slide entire screens horizontally merely to change one tile.
4. **After the backdrop is established:** fade in a title or one useful metadata line where applicable. Text must not jump around or force the dock to move.
5. **On rapid additional input:** cancel superseded metadata requests and skip or shorten intermediate background animations. The final focused item always wins. Never queue transitions behind remote presses.

An active tile has a single, obvious focus state. Merely changing focus **must not launch an app, play a trailer, connect NordVPN, power on Xbox or start music**. OK is the separate, deliberate activation step. On Back, restore the previous screen and focused object. If background fetching fails, keep navigation working and show the item's own brand-derived fallback, never another item's hero art.

**Physical continuity, inspired by high-quality TV focus systems:** a focused logo/poster appears marginally closer to the viewer while the dock stays planted on the background plane. Use a tiny change in highlight direction and shadow, not theatrical 3D rotation. When **OK** enters our own film details or NAS library, preserve the selected poster's identity and interpolate its position, size and corner treatment into the destination; on **Back**, reverse that spatial relationship to the exact originating poster. For a *third-party application* launch, only animate our launcher up to the OS activity hand-off; never claim to control Netflix, BBC or Disney+'s internal page animation. Navigation easing should favour quick response followed by a calm settle, with consistent timing across all elements in the same action.

**Proposed motion tokens** (initial engineering values, adjusted only after physical device testing):

| Event | Baseline duration | Behaviour |
|---|---:|---|
| Focus feedback begins | within 80 ms | Remote-first visual acknowledgement; existing P0 p95 response target remains <100 ms |
| Tile elevation and return | 150-210 ms | Ease-out, roughly 1.06-1.09 scale; no large spring bounce |
| Metadata reveal | 130-180 ms | Short fade after a stable item is established |
| Contextual background crossfade | 400 ms, ease-in-out | Dock: starts immediately on focus change; NAS title: may wait a 135 ms settle for artwork decode. Latest focus wins. 200 ms with Lightweight rendering; instant with Reduce motion *(changed in v3.3)* |
| Expand dock into NAS shelf | 260-380 ms | One connected spatial transition, focus retained |
| Enter/leave full NAS library | 280-400 ms | Poster/selection continuity; no full-screen white or black flash |
| Enter/leave artwork mode | 650-1,000 ms | Dock fades away and image becomes unobstructed |
| Smoke/CO emergency | Immediately on alarm | Pre-empts everything, including Ring, where the OS permits |
| Ring appearance | ASAP on verified event | Interrupt immediately unless an emergency is active; never wait for decorative animation |
| Alexa reminder toast | 180-240 ms in/out | Does not steal focus or cover subtitles where avoidable |
| Dismiss overlay / return | 200-350 ms | Restore the exact previously focused object and playback state |

Animations are optional to understanding. Provide *Reduce motion* (instant crossfade, no drift), *Keep the Home background still* (no drift, 400 ms crossfade kept), *Lightweight rendering* (still frames, 200 ms crossfade) and reduced-transparency settings. Never gate input behind a motion sequence. Audio focus ticks are **off by default**; selection confirmation may use a barely audible tick if a household preference enables it.

### 4.5 Contextual hero backgrounds: app-by-app contract

Use legally sourced and maintainable visuals. The preferred source is approved official art or app-provided metadata where authorised; absent such a source, use an **original, clearly app-related branded atmosphere**. Do not scrape other apps' current shows or imitate their internal UI. Each app has a self-contained hero-asset configuration with a safe local fallback and cache policy.

| Focused item | Full-screen background behaviour | What appears above the dock |
|---|---|---|
| **SmartTube** | Elegant, minimal video-centric composition based on genuinely available/authorised current content; otherwise a muted, service-relevant original scene. Never label it YouTube or show random film artwork. | `SmartTube`, optionally one concise line of current context if reliable |
| **Netflix** | Cinematic artwork for Netflix only when rights/source permit, otherwise a refined charcoal scene with a restrained red accent, not a random landscape. | `Netflix`; no invented recommendations |
| **Disney+** | Deep midnight-blue or star-lit original atmosphere with very subtle illumination; licensed imagery if available. | `Disney+` |
| **BBC** | A restrained BBC/iPlayer-related editorial atmosphere and approved identity. No unrelated Hollywood film art. | `BBC` with secondary `iPlayer` only when it helps explain the destination |
| **Live TV** | Original live broadcast/sports mood, abstract broadcast light or a relevant authorised TV still. Never promise a specific channel, fixture or broadcaster. | `Live TV`; show VPN blocking state only **after OK**, not a warning dominating idle focus |
| **Music** | Music-specific colour field drawn from permitted album artwork when track data exists; otherwise a clean, quiet music-themed composition. | `Music` and **Now Playing** only when reliable playback state exists |
| **Xbox, Sony only** | Original dark green gaming atmosphere with subtle depth; console status only if Home Assistant is connected. | `Xbox`; OK follows the Sony HDMI 3 wake/switch journey |
| **Samsung TV Plus, Frame only** | Native broadcast-appropriate imagery or tasteful service-colour background, with approved app identity. | `Samsung TV Plus`; OK starts native Tizen handoff through tested bridge |

**No misleading image rule:** if the service has no authorised dynamic imagery, stay with a high-quality *brand-aware fallback*. Do not fill every service with the same mountain wallpaper, randomly selected stock scenery, unlicensed film posters or guessed titles. Preserve the restrained character of the dock while the dominant image changes.

### 4.6 NAS focus is a cinematic reveal, not a separate-looking application

Press **Down** from the dock to reveal **Your Films & TV**. The dock gently retreats upwards or reduces prominence while a single row of posters rises into view as part of the same scene. Focus moves predictably to the best corresponding poster or the last visited position. Press **Up** to return to the dock without resetting the selected app. The full NAS catalogue appears only after choosing **View All**.

**When focusing a film poster:**

1. The selected poster lifts and scales modestly above neighbouring covers.
2. The *same film's* wide landscape backdrop becomes the full screen hero. Do not show an app-branded background while a NAS title has focus.
3. Reveal the title, year and runtime if known; one short synopsis line is enough on Home. Remove unsupported or mismatched metadata.
4. The rest of the posters recede in contrast, without vanishing or shifting unexpectedly.
5. **OK** opens the agreed title detail/play path and starts embedded native playback; users never browse Synology directories or VLC file lists.

**When focusing a series:** use the series' correct landscape backdrop, series title and a truthful next-episode/season label only when the underlying data proves it. Never label something `Continue Watching` without an actual tested watch position. A season or episode selection is explicit within the dedicated catalogue, not an unpredictable focus action.

**Artwork source order:** verified title-specific landscape backdrop > title-matched key art with a readable crop > blurred/tinted expansion of that **same title's** poster > original neutral backdrop **with the selected title/poster still clearly identified**. Never show a different film's imagery, a broken image icon or a raw NAS filename. Cache identities by item ID, not a guessed title alone; fail gracefully if the catalogue is offline.

**View All gallery:** a dedicated poster-led view with Films, Series, recently added and search; no Plex-like wall of controls. Keep the selected film's large background present during entry. Use a small translucent navigation strip only where the depth of the catalogue requires it. Back walks through episode > season > series > catalogue > Home in that order, preserving focus.

### 4.7 Remote journeys and explicit visual states

| State | Visible UI | Background and transition | Key remote action |
|---|---|---|---|
| **Neutral Home / arrival** | Time + tiny utilities; single app dock. No poster wall. | Chosen personal artwork while *no* app is visibly highlighted; as soon as focus is shown, that item's correct context takes over. | D-pad focuses previous app; Down reveals library |
| **App focused** | One raised app, other apps quiet, no fixed side panel. | Full-screen app-related hero, changed by stable focus. | Left/right changes focus; OK launches; Down opens NAS shelf |
| **NAS shelf focused** | Poster row plus discreet dock; View All at the end. | Exactly matched film/series art with restrained metadata. | Left/right poster; OK details/play; Up dock |
| **Full library** | Poster grid plus minimal category/search navigation. | Current category/selected-title art, never an unrelated app hero. | Arrows, OK, Back; preserve position |
| **App/film launching** | Last selected element gently recedes; no technical progress view unless needed. | Selected image remains until playback owns the screen. | Back cancels only where safe |
| **Live TV VPN blocked** | One compact, legible action sheet; no app launched. | Live TV hero remains behind a darkened scrim. | Connect/Retry/Back; prevent traffic leaking |
| **Music Now Playing** | Large album art, track and play/pause/skip; no busy playlists until requested. | Soft tones sampled from correct album art. | Playback controls; Back returns Home |
| **ChatGPT listening** | Small glass listening panel and clear microphone state. | Keep underlying context legible; no takeover or persistent chat page. | Back closes microphone |
| **Smoke/CO emergency** | Separate full-screen entries per alarm (type, room/name, time), individual acknowledgement, then a persistent banner while any alarm is active. | Pre-empts every other state where the OS permits; never dismissed by a timeout. | Acknowledge each alarm; acknowledgement never changes the sensor |
| **Ring interruption** | Actual live feed, status, clear dismiss; second only to a smoke/CO emergency and never shown over one. | Temporarily takes priority over wallpaper/hero/playing app. | No input required; return to prior state on dismissal |
| **Alexa reminder** | One small corner toast with text where available. | Current programme continues underneath. | Auto dismiss; never take focus |
| **Ambient / native Frame Art Mode** | **No permanent controls, clock, dock, status or logos.** | Selected family/scenic/art image only. | Remote reveals Home; Ring automatically interrupts |
| **Settings and TV cleanup** | Calm, text-forward administrative view, separate from entertainment. | Non-distracting dark material; no full-screen hero competition. | Back returns to exact Home focus |

**Focus persistence:** record selected app, last NAS poster/row, gallery page and the prior TV mode. Returning from Ring, music, voice, failed VPN, Settings or another app must not randomly jump to the first app tile. Where Android/Tizen cannot restore the precise activity or input, document a specific tested fallback.

### 4.8 Time-of-day personality without arbitrary colour overlays

The *active launcher*, not the family's original photographs, changes mood naturally. The selected app or NAS title remains the primary colour source, with a subtle daylight-dependent treatment of glass, highlights, shadow depth and text balance:

| Phase | Intended appearance | Principle |
|---|---|---|
| Morning | Crisp and gently luminous, restrained neutral-white edge light. | Calm, not artificially saturated |
| Day | The clearest glass and brightest legitimate artwork presentation. | Keep important text legible over bright photos |
| Dusk | Gradual, slight warming of focus highlights and reduced panel brightness. | No obvious palette switch at a clock boundary |
| Night | Deeper charcoal materials, softer highlights, lower intensity from utility chips. | Dark and comfortable, without crushing detail |

Use Europe/London time, current sunrise/sunset if an authorised local provider is available, and user-selectable fixed times otherwise. Crossfade phase adjustments gradually (roughly 10-20 minutes), never by flashing the entire display. Preserve the artwork's actual colours and metadata identity. Theme changes must not trigger navigation, move focus, change apps or start media.

### 4.9 Gallery/Art Mode is a separate, truly quiet state

After the selected inactivity timeout *while the launcher is foreground*, withdraw metadata first, then fade the dock and header out, leaving the chosen photograph or artwork edge to edge. No bouncing logos, screensaver clock by default, stock animations or content promotion. Personal family photographs must keep their identity, natural tone and sensible crop; do not apply heavy branded gradients or invent face detail. Crossfades between the chosen 10+ images should feel like a gallery, without gimmicky pan/zoom on every image.

On **Samsung native Art Mode**, there is **never a clock, VPN flag, ChatGPT icon, app dock, Alexa toast by default or permanent status**. This remains true regardless of time of day. A Ring doorbell press must automatically interrupt Art Mode into the real camera stream (person detection does not: RING-02) (unless a smoke/CO emergency is active), then return to native Art Mode and artwork. This is an independently tested *hardware orchestration requirement*, not a promise that Tizen supports arbitrary third-party overlays.

On Sony OLED, use restrained brightness, changing artwork and OLED-safe inactivity behaviour; never leave a glowing static dock or clock for hours. The quieter gallery state does not run while another video app is actively playing.

### 4.10 Alerts that belong to the experience rather than hijack it

**Smoke/CO emergency: priority 1 (`EMERGENCY_SMOKE_CO`)** *(changed in v3.3)*. Smoke and CO are equal. The emergency display (OD-07) pre-empts everything where the OS permits, is never interrupted and is never dismissed by a timeout. Supplementary only; never a certified alarm.

**Ring: priority 2 (`RING_DOORBELL_PERSON`), second only to emergencies** *(changed in v3.3)*. Ring takes visual priority over household notices, Alexa toasts, voice sessions and background transitions, but never covers an active emergency: during an alarm it is queued as described in OD-07. On Home or library browsing, a sizeable live camera card appears without moving underlying focus. On video playback, prefer a safe camera window in an unobtrusive area, with a platform-tested full-screen fallback where protected video or the OS blocks a floating overlay. In Sony/TCL wallpaper or Samsung native Art Mode, interrupt the artwork and show live video automatically. A truthful `Connecting` state is allowed before frames arrive; never label a still as Live. When dismissed, restore the app, soundtrack volume, selection and artwork precisely. Ring visuals should be clear and calm, not a loud flashing notification.

**Household notices: priority 3 (`HOUSEHOLD_IMPORTANT`)** *(changed in v3.3)*. Detector offline/fault warnings, VPN-protection notices and delivery/arrival notices. A detector warning is never styled as an emergency and never as "safe".

**Alexa reminder: priority 4 (`REMINDER_INFO`), below Ring and household notices** *(changed in v3.3)*. A reminder arriving during an emergency is dropped. A short, single glass toast at a safe corner, showing the real Alexa reminder text and an optional small Alexa identity cue. No full-screen sheet, forced playback pause or cursor movement. Position adaptively to avoid covering subtitles or playback controls where possible. Group duplicates; let users disable individual reminder categories/TVs. Samsung native viewing has OS limitations; do not silently claim cross-app Tizen overlays work.

**VPN protection on Live TV activation (a `HOUSEHOLD_IMPORTANT` notice).** The passive Home badge is only a tiny shield/flag. If the user *activates* Live TV while the verified VPN is off, show a simple Connect/Retry/Cancel sheet against the current Live TV background. Never launch the unprotected source, and do not mask failed verification with a successful green badge.

**Decorative: priority 5 (`DECORATIVE`).** Ambient drift, Wallpaper transitions and other decoration yield to every alert.

**Voice: user-invoked and opt-in; every alert takes visual priority over a voice session.** The compact ChatGPT glyph expands to a small listening waveform/panel on deliberate activation; colour and movement are understated. Display a brief transcript or answer when useful, close on Back, and automatically remove the panel when finished. No permanent assistant portrait, conversation feed or advert-like suggestion chips on Home.

### 4.11 Typography, icons, affordances and readable distance

Use an original, redistribution-cleared modern sans family with strong legibility at normal sofa distances; do not bundle Apple's proprietary fonts. The design should feel typographically calm: one dominant heading when needed, one secondary line, consistent spacing and generous line height. Avoid long descriptions, all-caps labels, excessive bolding and dense specification tables in entertainment views.

**Starting values on 1920 × 1080 canvas** (subject to sofa-distance testing): 24-hour clock approximately 40-48 logical px; app label 26-30 px; hero title 44-64 px where shown; short metadata 23-28 px; toast body at least 25 px. Keep all essential text on safe backgrounds with strong contrast, target WCAG-like 4.5:1 for ordinary text when practical and never rely on colour alone for focused/disabled/protected states. Respect screen-reader labels, text scaling, increased contrast, reduced transparency and reduced motion.

Each service icon uses a recognisable and legally redistributable brand mark if permission and licensing permit; otherwise a clear text lockup. App tile styles are coordinated but brands retain their identity. Labels are `SmartTube`, `Netflix`, `Disney+`, `BBC`, `Live TV`, `Music`; `Xbox` only on Sony and `Samsung TV Plus` only on Frame. **Do not put VLC, Jellyfin, Synology, Home Assistant or NordVPN setup controls in the app dock.**

### 4.12 Technical rendering contract for the slower TVs

- Use one shared design system with **high**, **balanced** and **lightweight** *rendering profiles*, automatically selected after device profiling or manually overridden. They **must not change information hierarchy, service names, navigation or core visuals**.
- Keep remote focus changes synchronous on the UI thread and artwork loading asynchronous. Cache properly sized hero images; prefetch current and adjacent items and cancel requests on fast input.
- For photographic idle/gallery backdrops, prefer downsampled imagery, baked blur and bounded memory. For active Home navigation, Section 32 supersedes any static-only preference: use lightweight, code-defined radial-gradient ambient meshes with GPU-friendly opacity/transform animation. Do not require expensive full-4K per-frame Gaussian blur, animated video walls or unrestricted fragment shaders. Benchmark the actual hardware.
- Use only one full-screen backdrop crossfade at a time. During fast scrolling, skip superseded transitions and show the final correct art (dock: retargeted immediately; NAS titles: after the 135 ms settle).
- Keep a placeholder ready *per focused item*. Image failures must not revert to the *previous item's* artwork after focus changes; clear or replace the image identity atomically.
- Target a visually smooth 60 fps where the hardware sustains it; retain honest measured input response and legibility when animation detail is reduced on TCL or ageing Sony. Do not hide lag behind long motion.
- Warm returning Home must be fast, even while Jellyfin, Home Assistant, NordVPN or online artwork sources are unavailable. The dock and installed apps appear from local cache; technical integrations catch up quietly.
- Log telemetry for animation/input performance locally and anonymously by default; never include family photos, media paths, doorbell images or personal tokens in public bug reports.

### 4.13 Mandatory design hand-off artefacts

The project is not ready for launcher development from only a single static hero image. Produce original **1920 × 1080 layouts and short motion studies** for all of these states, on the **same component and spacing system**:

1. Neutral Home with personal landscape, clock and six/seven-tile glass dock.
2. Netflix app focused with a properly Netflix-relevant backdrop (licensed material or original brand-aware fallback); repeat for BBC to prove the system generalises.
3. Live TV focused, then VPN disconnected *after OK*, displaying its clean blocked action sheet.
4. NAS film focused, using that exact film's poster + backdrop; include missing-backdrop fallback.
5. NAS series focused, with legitimate next-episode information; then **View All** and series/season/episode path.
6. Frame-specific dock with Samsung TV Plus; Sony-specific dock with Xbox; no platform leakage.
7. Tottenham matchday compact card and the normal day without it.
8. Samsung full-screen native Art Mode with **absolutely no chrome**, then automatic Ring takeover and return.
9. Netflix or Live TV playing with a short Alexa reminder toast; same scene with Ring taking precedence over the toast, and with a smoke/CO emergency taking precedence over Ring.
10. Amazon Music-inspired Now Playing, with actual album-derived backdrop where authorised.
11. ChatGPT icon at rest, active listening panel, short spoken answer and clean dismissal.
12. Day-to-dusk-to-night treatment of the *same* contextual hero, showing the mood shift without changing the subject.
13. Appearance/Accessibility settings showing Reduce Motion, High Contrast, theme preference and render quality.
14. Two performance recordings: rapid focus changes on TCL and Sony, plus matching screenshots to demonstrate visual parity.
15. Apps library and editable dock: add an installed app, open the hold-OK Move menu, reorder and save/cancel, remove and restore a Home shortcut, show a long horizontal dock and distinct Sony/Frame capabilities.

Provide each with a focus map (Up/Down/Left/Right/OK/Back), source of artwork, enter/exit transition, timing measurement, empty/error state, permission or integration dependency, and supported/unsupported TV matrix. Use **copyright-cleared original or properly licensed placeholder imagery** for published mockups; do not copy Apple's system screens or paid design concepts. Reference demonstrations from third parties are inspiration, not assets for the public repository.

### 4.14 Definition of premium: design review pass/fail rules

This is a **P0 release gate** alongside the hardware Ring and VPN gates:

- **Pass:** the selected Netflix tile controls a correctly Netflix-related backdrop. **Fail:** it shows a mountain or an unrelated film simply because the photograph looks nice.
- **Pass:** the highlighted NAS film uses its matching artwork and metadata. **Fail:** it shows artwork from a different title or raw filenames.
- **Pass:** resting Home is predominantly content image plus one glass dock. **Fail:** three busy poster rows, fixed side menus and stacked glass cards dominate the first screen.
- **Pass:** moving from app dock to NAS feels like one continuous scene. **Fail:** an unrelated page crashes in, resets focus or briefly flashes white.
- **Pass:** focus is acknowledged immediately despite background image loading. **Fail:** the remote feels sticky while images load or glass animates.
- **Pass:** 100 quick remote presses finish on the correct tile with the **correct final background**, without rendering stale art. **Fail:** the image lags behind, freezes or belongs to the previous focus.
- **Pass:** glass provides separation without hiding the artwork; labels read comfortably from sofa distance. **Fail:** panels and glows overwhelm films and family photos.
- **Pass:** Sony, TCL and native Frame application look recognisably identical in high/low rendering modes. **Fail:** the TCL looks like a visibly different, inferior launcher.
- **Pass:** Samsung native Art Mode contains only artwork until a Ring trigger. **Fail:** any clock, VPN badge or decorative controls remain onscreen.
- **Pass:** Ring pre-empts transitions and restores the prior scene. **Fail:** a decorative effect delays or conceals the doorbell video.
- **Pass:** a smoke/CO emergency pre-empts Ring and everything else, and Ring is queued during it. **Fail:** a doorbell card, reminder or effect covers, interrupts or times out the emergency.
- **Pass:** voice, toasts, Spurs and VPN restrictions appear only when context requires them. **Fail:** they become permanent dashboard widgets.
- **Pass:** no image, source code, trademark usage, font file or proprietary animation is taken from another product without the necessary rights.

## 5. Personal films and TV from Synology (P0)

The NAS remains the media source. **DLNA/UPnP discovery and ContentDirectory are the verified initial connectivity path, not the finished catalogue.** The premium catalogue requires a supported metadata and identity layer, such as a locally available, authorised Jellyfin-compatible service where installed and verified, or a separately tested catalogue adapter. Do not assume Jellyfin is installed, available or equivalent to Synology Media Server. Match title identities carefully, expose uncertain matches for correction and never assign unrelated artwork. Direct SMB or UPnP discovery alone does not provide the required posters, search, series organisation or server-synchronised Continue Watching. Confirm service availability and capabilities without requiring viewers to manage folders after owner setup.

- Down reveals a curated Your Films & TV row; View All opens Films, Series, Search, Genres and Recently Added. Continue Watching appears only after progress round-trip has passed.
- Android uses embedded Media3/ExoPlayer. Tizen uses embedded AVPlay. VLC is excluded; no external player handoff or VLC fallback is part of this version.
- Prefer direct play. Validate containers, codecs, profiles, bitrate, HDR, audio passthrough, subtitles, network throughput and server capabilities independently. MKV is a container, not a guarantee of playback. Permit an explicitly supported server remux/transcode path when needed; explain unsupported media and never promise instant playback.
- Measure request-to-first-frame separately for cold and warm playback, buffering, seek and resume on representative 1080p and 4K content. Playback controls must support play/pause, seek, audio/subtitle selection and Back without accidental exit.
- Report authenticated session start, progress, pause and stop with correct user, item, media source, position and session identity. Persist through server state and reload after restart; local guesses cannot populate Continue Watching. Handle network loss and duplicate stop events safely.
- Use separate Adult and restricted Child server accounts. Validate identity and permissions; enforce access at the server for browse, search, images and playback. An adult server API key is not a Child account.
- Android requests use scoped credentials in headers and reject credential-bearing redirects. AVPlay does not establish support for arbitrary media authentication headers. Validate a protected LAN media broker or supported scoped cookie path before enabling private Tizen playback. Never put long-lived server tokens in URLs.
- Bound artwork/cache memory and cancel obsolete requests. On server loss, Home and installed app shortcuts continue to work; show honest unavailable feedback and cached artwork only where a cache actually exists.

**GATE-NAS-01:** play film, subtitled film and episode through each embedded player on its target hardware. **GATE-NAS-02:** verify matching artwork, local access and server authorisation with VPN policy active. **GATE-NAS-03:** prove watch-state round-trip including profile switch and restart. **GATE-PLAYER-01:** publish the measured per-device codec/audio/subtitle matrix. **GATE-PLAYER-02:** block unsupported playback without falling back to VLC.

## 6. Live TV and NordVPN protection (P0, security-critical)

- Visible label is **Live TV**; underlying application is **ReamSmartz**. Every launch origin shares one guarded action: tile, Tottenham card, voice, search and resumed sessions.
- Top-right minimalist badge shows VPN **connected/disconnected/connecting/unknown**, with a flag for the **verified exit country**. Do not display green from NordVPN being merely installed or from a remembered country selection.
- If the tunnel is unverified: do not launch Live TV; offer a clear Connect / Retry action. Do not claim NordVPN can be connected silently until a supported integration is demonstrated.
- **Fail closed during playback:** no unprotected ReamSmartz traffic when VPN drops, including after standby or reconnect. The initial launch check alone is insufficient. Investigate platform-supported always-on VPN + block-without-VPN, app routing and/or a segregated VPN network; test network paths and any split tunnelling carefully. It is acceptable to show a blocked state rather than permit traffic.
- Netflix, Disney+, BBC, native Samsung TV Plus, NAS and other permitted services should retain normal availability where technically possible; account for system-wide VPN lockdown trade-offs.
- NordVPN local-network discovery must preserve access to Home Assistant and the media server. A country flag is only shown where geolocation verification is trustworthy.

**GATE-VPN-01:** with VPN off, Live TV traffic fails; **GATE-VPN-02:** drop VPN mid-stream, confirm packets do not escape; **GATE-VPN-03:** Sony, TCL and native Frame application pass independently.

### 6.1 VPN split tunnelling and protection gate

Android VPN transport detection **does not prove that ReamSmartz's packets use the desired tunnel or stop when it fails**. NordVPN Android TV split tunnelling is an optional route configuration to test. Confirm what the installed NordVPN version actually supports; excluding other apps does not by itself protect one app from leaks. Android always-on VPN and Block connections without VPN have different consequences for excluded apps.

Do not promise a silent NordVPN Connect Intent without documented support. Test the actual ReamSmartz package, forced midstream VPN drop, DNS, IPv4/IPv6, wake/restart and access to the NAS/Home Assistant. An on-screen failure scrim is not a firewall. If no real fail-closed mechanism passes tests, keep Live TV blocked in the preview.

**GATE-VPN-04:** confirm no unprotected ReamSmartz packets after tunnel loss. **GATE-VPN-05:** independently prove normal LAN access and unaffected apps under the chosen routing method.

## 7. Tottenham matchday widget (P0)

- Follow Tottenham Hotspur men's first-team fixtures across domestic and European competitions; matchday means the local date in `Europe/London` (BST/GMT automatically).
- On matchday show Tottenham and opposition crests, competition and **UK kick-off time**. Prior to kick-off display the time; during the match use only `Live`, **never a score or result** (avoid spoilers).
- When match is over, hide after a limited grace period, no later than end of UK matchday; when no fixture is scheduled, no card or reserved blank space.
- Press OK -> protected Live TV launch through the **same VPN gate**. Do not imply ReamSmartz is the official broadcaster.
- Fixture provider must cover date/time changes, postponements and multiple competitions; refresh reliably, cache last verified fixture and avoid a false card if data becomes stale.

### 7.1 TCL AirPin LITE receiver integration (P1, optional)

The TCL settings photographs confirm **AirPin LITE** is installed. Earlier external notes called it **AirPro LITE**, which is the wrong product name and must not be repeated. AirPin is a third-party AirPlay/DLNA receiver; its developer documents background receiving after initial launch, but we have **not yet verified automatic startup after reboot on this Android TV 11 TCL**.

Do not make AirPin a permanent app dock tile. Under **Settings > Connections > AirPlay**, detect the actual installed package, offer a guided one-time receiver setup, and show a small AirPlay glyph in the header only when a supported operational state is confirmed. A configured app is not proof of active receiving. Respect Android 11 background activity restrictions and the receiver's own licensing; never bundle its paid code or change its settings without consent.

**GATE-AIRPLAY-01:** verify startup, discovery from an iPhone, casting and graceful return after restart/sleep, plus CPU/RAM use. If automatic startup is unavailable, report a manual setup fallback rather than a false success icon.

## 8. Ring camera override via Home Assistant Green (P0 release gate; second in alert priority)

**Reference deployment:** Home Assistant Green on the same local network as the televisions; configure the address and credentials privately, with Ring configured. Do not assume remote development tools have access to that private URL. Ring cloud access remains necessary even when HA and TV are on the same LAN.

**Trigger A:** actual Ring doorbell button pressed: the live camera (RING-03). **Trigger B:** **person detected**, not generic motion: a discreet 5-second toast only, no camera (RING-02, changed 25 October 2026); ordinary motion is ignored (RING-01). Person detection requires proof of a suitable Ring Smart Alert event or supported ring-mqtt attribute; Ring subscription/device compatibility to be verified. Deduplicate repeated notifications.

**Presentation priority:**

1. **In wallpaper / ambient mode on Sony/TCL:** without user action, interrupt wallpaper and open the **live Ring feed** prominently, then restore exact previous artwork and ambience.
2. **In Samsung's native Art Mode:** without user action, command the TV into a real live-view state through a model-verified native app launch path, then restore **native Art Mode** and prior artwork. The Frame shows **no clock or other permanent UI** in Art Mode.
3. **Whilst browsing Home or a library:** immediate camera overlay over current screen.
4. **Whilst playing another app/music:** try an in-app-independent Android overlay (picture-in-picture); if OS/DRM blocks it, use a documented and tested full-screen interruption fallback. Restore focus and volume afterwards.
5. Ring alert has precedence over household notices, Alexa reminder toasts, ambient timeout, music, ChatGPT responses and standard notifications, **but never over an active smoke/CO emergency**: during an alarm the Ring event gets no camera card, only the latest visit is queued, and after every alarm clears it is shown if under 45 s old or replaced by the note `Doorbell rang at HH:mm during the alarm` *(changed in v3.3)*. Queue an Alexa reminder briefly if Ring interrupts, or discard it after its short relevance window. TV power **off** is separate from ambient: do not wake a fully off TV unless explicitly configured.

On event show a locally cached recent still with `Connecting` only if the still is available and correctly labelled; switch to **Live** once actual moving frames arrive. On stream failure show a clear `Camera unavailable` state; never pass a stale still off as live video. Dismiss after configurable period / Back; preserve previous playback. Volume ducking must restore original level.

Possible Android building blocks to test before writing new code: `vessosa/ha-video-toast-tv` and `nikitacoolagin/hass-camoverlay`. Candidate Frame control: `bonzanni/ha-samsung-tv-frame` or suitable Samsung Frame Art Mode integration. These are third-party dependencies needing security and firmware testing.

**GATE-RING-01:** all three TVs, in active wallpaper/Art Mode, automatically show actual Ring video from **both** triggers without remote input. **GATE-RING-02:** dismiss restores correct image and TV mode. **GATE-RING-03:** ring during Netflix/Live TV/music without lost playback. **GATE-RING-04:** correct action after standby, HA restart, Ring delay, TV network loss and duplicate events. A failure of Frame Art Mode switching blocks the intended three-TV release; do not quietly remove this requirement.

## 9. Ambient wallpaper and time-of-day mood (P0)

- The default source is **curated artwork** that is free and explicitly licensed for this use, bundled offline (OD-03) *(changed in v3.3)*. The owner's own photographs (family holidays, scenic photographs) are the later **My Photos** task; when built, support 10-100 local images where feasible, with no assumption that photos must be cloud hosted.
- Home screen transitions to a gallery presentation after configurable inactivity **on the launcher**, never during active video/music unless the relevant app has its own appropriate idle behaviour.
- In gallery mode: full-bleed photographs/artwork, gentle dissolve, discreet or no animations, no permanent controls. Group/weight Family, Travel and Art; rotate without immediate repeats. Simple selection and removal from Settings.
- On **Frame native Art Mode**: **no clock, widgets or permanent UI at all**; photo/art image only. Ring automatic override (and a smoke/CO emergency, where the platform permits) are the only compulsory exceptions; Alexa reminder toasts are **off by default in Art Mode** and would require deliberate opt-in plus proven native platform support.
- On Sony/TCL ambient mode: image only by default; clock optional and off by default to honour gallery appearance. Use appropriate OLED care: dimming, image variation and no permanently static bright badges.
- Time-of-day influences *active interface* lighting: fresher/clearer by day, warmer at dusk, darker/subdued at night. Use local time and solar schedule if available; let users override. Gradual changes, not sudden full-screen flashes.
- Returning from ambient to Home should preserve the last useful focus state and transition smoothly.

## 10. Current time, header and statuses (P0)

- Active Home shows one small, legible **24-hour clock**, default **top-left** to follow the approved calm visual reference (with a user preference for top-right if required); it follows `Europe/London` GMT/BST and never shows seconds. No second clock in the dock or title area.
- Place the truthful NordVPN state and verified country flag, tiny optional ChatGPT control and discreet Settings/More affordance in a **single small top-right utility group**. Each stays the same location while focus and background art change. The hero image, not the status icons, receives attention.
- The current time and utilities vanish in artwork/ambient mode. In **Samsung native Art Mode there is no chrome at all** until Ring automatically interrupts it. Do not force a permanent header over Netflix, BBC, music, embedded playback or Xbox.
- Status chips stay silent during normal browsing. Only a relevant event, such as Live TV activation without a verified VPN, expands into a temporary action sheet.

## 11. BBC, core apps and Sony Xbox (P0)

- Six shared consumer app tiles: **SmartTube, Netflix, Disney+, BBC (iPlayer), Live TV (ReamSmartz) and Music**. A missing app should show install/help only in Settings, not a dead tile.
- `BBC` should clearly identify iPlayer without pretending to be a different provider. Preserve each app's internal UX when launched; this project replaces Home, not Netflix or Disney+.
- Sony only: beautifully branded **Xbox** tile. OK -> send Home Assistant Xbox wake, wait for ready, switch **Sony HDMI 3 (eARC)**, then hand off to Xbox. Clear timeout/error feedback; don't show on TCL or Frame. Validate wake state and HDMI input name against real entities. Xbox power mode may require Sleep, with standby power trade-off.
- Samsung Frame only: branded **Samsung TV Plus** tile. Keep Samsung TV Plus installed and functional as a native Tizen service. Selecting the tile should open Samsung TV Plus, preferably using a verified Samsung/Home Assistant native-app launch command; from the native Tizen application use a discovered and verified application identifier or a tested household controller action. If native app deep linking cannot be reliably automated on the specific Frame firmware, expose a clearly defined tested fallback. Record the previous input/launcher state so Back/Home and Ring recovery are as predictable as Samsung permits. Never show this tile on Sony or TCL.
- **TV Plus and ReamSmartz are distinct services.** The NordVPN fail-closed rule applies to ReamSmartz/`Live TV`, including Spurs and voice pathways, but not automatically to Samsung TV Plus. A separate Samsung TV Plus connection should be tested for normal playback and audio routing.
- Be aware that HDMI-CEC, ARC/eARC and input routing vary. Never change sound output without validating the real HDMI setup.

## 12. Samsung Frame audio: Echo Studio (P0, GATE)

- Whenever leaving native Art Mode for TV/app viewing, the Samsung should output sound to the paired **Echo Studio automatically**. No manual Bluetooth selection on every wake.
- Test Samsung's persistent Bluetooth audio output, power-on/Art Mode transition and competing Bluetooth pairings first. Test Home Assistant-supported Samsung audio routing only if the exact model exposes it.
- Fallback for a compatible **original** Echo Studio with mini optical input: test optical from Frame / One Connect, audio delay and format. The newer Echo Studio may lack this connector; identify exact Echo model before selecting wiring.
- Do not fake a successful audio reconnect. If Bluetooth cannot be made reliable, present the optical / hardware alternative and a temporary manual action.

## 13. Amazon Music experience (P1, agreed design; integration GATE)

- Home tile named **Music**, using the existing **Amazon Music subscription**. Avoid displaying Amazon's technical integration details.
- Intended custom Now Playing screen is Apple Music inspired: large album cover, colour-derived soft background, song, artist, album, progress, prominent Play/Pause, Previous/Next, volume; remote-friendly focus. Discover personal playlists, recent music and favourites only when authorised APIs make that possible.
- **Technical gate:** Amazon Music playback/library Web API is currently closed beta. Do **not** assume a custom catalogue or independent streaming is available. Phase 1 may open the official app; investigate Android MediaSession state, casting and Home Assistant Alexa Media Player/Echo Studio metadata for a richer Now Playing view without claiming guaranteed control.
- Long music listening should use OLED-friendly dimming; smoke/CO emergencies and the Ring overlay still take precedence. Frame native Art Mode remains visually clean when selected.

## 14. OpenAI voice assistant (P1, agreed design; microphone GATE)

- Tiny optional assistant icon in active Home header, no main app tile and no always-listening microphone by default.
- Tap/OK to start voice session, clear listening state, UK English voice with calm British accent, natural interruption and short spoken responses. Close on Back / timeout; never leave an invisible active microphone.
- Answer general questions and perform safe TV tasks: find NAS content, open BBC, protected Live TV, Sony Xbox, show Ring camera, navigate Home. Use contextual per-TV device capabilities and report failures plainly.
- Use the **currently supported OpenAI GPT-Live/Realtime API** appropriate at implementation time; not the consumer ChatGPT voice session or its subscription entitlement. API credentials live on a protected household service (ideally Synology if resource appropriate), not in TV APKs.
- Never expose private Home Assistant tokens to model output. Give the model only allowlisted commands and read-only content search; confirmations for sensitive home actions. Audio retention/privacy and running API costs must be visible in Settings.
- Test microphone input on actual Sony/TCL remote; the standard remote voice button may be reserved by Google, so an on-screen icon is the dependable starting interaction.

### 14.1 Hybrid voice as a measured option

Use Home Assistant Assist or an allowlisted local phrase recogniser for deterministic TV commands **only when it proves faster on the reference hardware**. Home Assistant Green is modest hardware; do not require on-device Whisper for every instruction without measuring latency. Preserve optional ChatGPT Live/API voice with UK English responses for **general questions**, open discussion and content discovery, not just NAS searches. Maintain explicit microphone consent and a single discreet voice affordance.

## 15. Architecture and separation of concerns

Android Sony and TCL use the native Android application with embedded playback and an optional permission-based alert service. Samsung Frame uses the native Tizen Web Application with embedded AVPlay. Both implement the same interaction contract and local configuration model. Home Assistant supplies household events and an external, model-specific Frame launch and Art Mode coordinator. The media server supplies catalogue, artwork and watch state. A protected local media broker is the proposed Tizen streaming authentication boundary, subject to implementation and tests.


- Main Android launcher, camera overlay, reminder-toast receiver and background integration service must have **separate lifecycle**: Ring and Alexa reminders may not depend on Home being foreground. On Samsung native apps, third-party overlays remain unsupported unless a genuine OS-supported mechanism is demonstrated; the native Frame app may display alerts only while visible; an external household coordinator must handle launch and Art Mode transitions.
- Preference/configuration data centrally synced where useful, with last-known-good local copies. A TV that loses HA/network must still open installed apps and show wallpaper.
- Avoid placing heavyweight metadata indexing and AI requests on the TCL. Cache resized JPEG/WebP artwork; bound caches and cancel obsolete fetches.
- Use properly licensed fonts, icons, posters and logos, with attribution and no redistribution of third-party paid assets. No copying Apple or streaming app interface source.

## 16. Performance, resilience and security (P0)

These are **engineering targets to validate**, not measured performance of your current hardware:

| Metric / control | Release target |
|---|---|
| Focus feedback | Under 100 ms at p95, tested across 100 rapid remote events; visible focus always responds before an image arrives |
| Background correctness | The final item after rapid input has correctly matched art or its own fallback, never stale art from a prior focus |
| Animation smoothness | Aim for 60 fps with no persistent dropped-frame clusters on the weakest TCL or ageing Sony; reduce effects rather than input responsiveness |
| Visual parity | Identical information hierarchy and navigation on all TVs; lightweight rendering reduces cost, not quality of layout or content identity |
| Home usable after warm return | Target under 2 seconds on TCL, Sony and native Frame application |
| Artwork fetch | Never block D-pad focus; placeholder immediately, image replaces asynchronously |
| Background footprint | Memory budget established using actual TCL RAM; no continuously running recommendation polling or full-resolution bitmap cache |
| Idle/OLED | Bright UI fades out; varied imagery; screensaver does not leave fixed high-intensity regions for extended periods |
| Offline | Installed apps and wallpaper remain navigable without NAS, external metadata providers or Home Assistant |
| Recovery | Documented tested restoration of stock Home, packages, remote controls and display/sound settings |
| Secrets | No keys/tokens in public repo, logs or TV artwork; least-privilege service credentials; network debugging off after setup |

**Per-TV assessment sequence (Sony, TCL, Frame):** (1) inventory exact model, firmware, platform, permitted diagnostics and installed applications; (2) establish a comparable pre-change baseline for startup, Home responsiveness, app launch and standby/wake, using RAM figures only when actually accessible; (3) verify the launcher and integrations before changing packages or settings; (4) offer the *optional* tune-up only when the owner chooses it; (5) compare user-visible performance before/after on **each** TV. The Sony's original flagship status is not a reason to skip cleanup testing. Never require cleanup to use the launcher. Full product rules follow below.

### 16.1 Optional TV Performance & Cleanup: multi-platform module (P1, user opt-in)

**Goal:** offer genuine responsiveness improvements on **Sony Google TV, TCL Google TV and Samsung The Frame**, with the same simple, optional admin workflow. Treat Sony and TCL equally; test both before and after. The module is a **portable product feature with OS-specific adapters**, not an Android package script indiscriminately used on every brand. It should be independently usable by people who install the public repo. The entertainment launcher must work unchanged if the user skips it.

**Owner-facing entry point and flow:**

1. Keep it outside the entertainment interface: **Settings > TV & performance > Optional cleanup**, guarded by an adult/admin profile and an additional explicit consent step. First install permits **Skip** without repeated prompting. Never infer that an external Android streamer exists on the Frame; if the user independently connects one, treat it as a separate named device, not the Frame runtime.
2. Start with **Read-only Check TV**. Detect brand/model/firmware/OS, supported access level, installed apps (only where APIs permit), free storage (only if accurately available), and measured Home/app navigation speed. Never infer processor speed or free RAM just from model age or count of installed apps.
3. Explain **what is genuinely possible on this TV**: standard settings-only tidying; removal of user-installed apps through official OS flows; reversible Android package disabling on *verified* profiles; and unsupported actions. Where the TV has no safe automation method, show a guided checklist or **No supported changes found**, not a misleading Cleanup button.
4. Preview each candidate with name, exact identifier where known, user impact, dependencies, platform restrictions, test evidence, risk and expected measured benefit if established. No 'remove everything' preset. No 'one-click boost' claims.
5. Require confirmation for every change batch; capture the original state and a per-TV performance baseline. Export a manifest and instructions to a PC/USB where the OS permits it. Do not present factory reset or firmware reflash as equivalent to a simple undo operation.
6. Carry out **only changes the selected platform supports**, in small batches; pause for error, cancellation, unknown model/firmware or failing regression. No remote silent disabling, root, exploitation, unlocking protected boot paths or mandatory developer mode.
7. Re-test content playback and DRM (Netflix, Disney+, BBC), remote/Home/Back, CEC and HDMI sources, tuner where present, Bluetooth and the Samsung Echo Studio, native Samsung Art Mode, NordVPN policy, Ring interruption, Alexa reminder toasts, voice/microphone, casting, NAS media, accessibility, standby and OTA updates. Show before/after timings, and recommend undo where there is no benefit or a regression.
8. Provide **Undo last change / Restore original state** wherever the underlying operation is genuinely reversible. Document any action that only supports a factory reset and **exclude it from the normal cleanup workflow**. No telemetry or cloud upload by default.

**Android TV adapter: Sony + TCL, plus optional external Android streamer**

- The Android launcher by itself has no general permission to disable other packages. Deliver a separate owner-authorised **desktop/ADB setup helper** or manual instructions. Debugging is deliberately enabled for setup, never left active permanently. A TV that declines ADB still receives the read-only advice it supports.
- Prefer reversible per-user disable/enable operations and a recorded rollback script. Keep firmware-specific, verified allowlists; refuse unknown package IDs or mismatched versions. Disable only genuinely optional promotional apps/recommendation services and unused vendor apps after checking their dependencies.
- Protect stock recovery and a usable Home route, DRM/HDCP, Android System WebView when required, Play Services, app stores, input/tuner, Bluetooth/audio, network/remote, accessibility, updates, voice/microphone, Ring and Home Assistant, NordVPN, casting and all household apps. Never uninstall system partitions/APKs or rely on root.
- Profile **Sony and TCL separately**. Restore the original state if either TV becomes less reliable or gains no material improvement. The performance-light launcher profile is a separate feature from package cleanup.

**Samsung Tizen adapter: native Frame TV**

- Offer the **same optional entry point**, but run a *Tizen-specific capability check*. A standard Android APK or ADB script must never be pointed at Samsung's native OS. The Android streaming device, if fitted, is scanned as a **separate target**, not confused with native Samsung software.
- Initial supported scope: guided Samsung settings to uninstall **user-removable** apps, remove unnecessary Home favourites, review allowed recommendations/autoplay settings, inspect storage and app troubleshooting options, and assess firmware-specific non-destructive maintenance. Present only controls proven present on the exact Frame model; never promise Samsung permits removal of every factory-installed app.
- Samsung's official developer documentation says preinstalled apps are not removable using normal app tooling. Community work (`ardazeytin/samsung-tizen-tv-debloat`) reports an SDB uninstall approach on **one 2018 Tizen 4.0 model**, but warns of launcher damage and restoration requiring reset/reflash. This is **experimental research only**, not a default or one-click cleanup action for the Frame. On a modern Frame, require independent hardware proof and an independently validated recovery plan before even considering a separate *expert-only* tool. Such removals must not be called reversible.
- Preserve native Art Mode, **Samsung TV Plus and its channel-guide/service dependencies**, One Connect/HDMI input switching, Bluetooth/Echo Studio pairing and source state, Samsung update/recovery paths and Home Assistant control. If Tizen exposes no safe package control, complete the safe settings review and state the limit honestly.

**Future TV brands and operating systems:** use `capabilities -> scan -> preview -> operation -> verification -> rollback` adapters for additional platforms (for example LG webOS, Fire OS and Roku) only after actual vendor API/firmware research. **'Any TV' means a common entry point and honest platform-specific support, not identical privileged access or guaranteed removal on every model.** An unknown television supports an informational checklist at most. Firmware changes always trigger a new scan and new owner consent.

**Failure and security boundary:** all restoration instructions must work without the custom launcher. Do not disable the original launch/recovery path before a proven alternative exists. No secrets or device tokens in public logs; avoid untrusted local web panels. A setup error or unplugged PC must not stop normal TV viewing.

### 16.2 Alexa reminder toast overlay (P1, agreement; cross-platform GATE)

**User outcome:** whenever a chosen Alexa reminder becomes due **during active viewing**, display a discreet, elegant, non-blocking text toast above the current programme/game/music, even when our launcher is not foregrounded. For example: a small Alexa glyph and `Reminder: Put the bins out` appearing near the top right for around 6-10 seconds. This should feel like a system notification, not an Alexa screen takeover.

**Source of truth:** evaluate Home Assistant's official **Alexa Devices** integration, which exposes **the next scheduled reminder's timestamp and label** for supported devices. It polls Amazon roughly every five minutes by default; reminder additions and modifications are **not guaranteed to arrive instantly**, and Alexa Household account linkage can affect visibility. Do not claim reliable second-accurate reminder delivery until due-time, cancellation and fresh-reminder tests pass on the user's Amazon account. Alternatively evaluate a permitted, better-timed event source if the official integration proves insufficient. The existing Ring integration alone does not imply Alexa Devices is configured.

**Data flow:** Alexa reminder entity (scheduled time + label) -> Home Assistant due-reminder automation/event gateway -> de-duplicated household notification -> each opted-in TV overlay receiver. Guard against duplicate sensors/devices, restarts, stale reminders, time-zone changes and reminders being deleted or modified; only display a message that was confirmed due. No reminder text may be fabricated. The bridge should not need the launcher open. Store minimal reminder data briefly and do not leak household reminders to external services or logs.

**Presentation:** subtle frosted/dark toast and gentle entrance/exit; reminder title and concise message with truncation for long text. Configurable duration, mute/snooze display, per-TV opt-in and privacy option to show only `Alexa reminder` without the personal label. No playback pause, focus theft, volume change or permanent UI; Back dismisses where technically supported. **Ring live camera override takes precedence over reminders, and smoke/CO emergencies take precedence over both; a reminder arriving during an emergency is dropped** *(changed in v3.3)*. Delay/queue a reminder briefly during Ring viewing, with expiry to prevent a stale popup.

**Device behaviour and platform gate:**

- **Sony and TCL running Android/Google TV:** use a TV-wide overlay service such as the Home Assistant `Notifications for Android TV / Fire TV` integration for text, or our own receiver, once the owner grants notification and **Display over other apps** permissions. Test above Netflix, BBC, SmartTube, VLC and Xbox HDMI as applicable: protected video/DRM or HDMI input modes can block third-party overlays even if normal Android apps work. If an HDMI source is not overlay-capable, record it as unsupported rather than promise every source.
- **Samsung Frame native application:** the application can display its own alerts while visible; it cannot draw over other native Tizen apps (including **Samsung TV Plus**) or native Art Mode. Samsung's developer documentation states third-party overlays over other Tizen applications are unsupported. For native Samsung playback, the desired notification requires an independently demonstrated Samsung-supported notification channel; otherwise it is a documented parity gap. Switching HDMI solely for a minor reminder is **not** an acceptable surprise interruption.
- **Samsung native Art Mode:** no Alexa reminder toasts by default, keeping the artwork pristine. Ring remains the mandatory, automatic Art Mode interruption. In fully powered-off state, Alexa reminders do not wake the screen.

**Acceptance gate:** prove one reminder arriving within a measured interval of its Alexa due time, with exact label, on Sony/TCL and inside the visible native Samsung application; record external-app restrictions. Test creation just before due, edits, cancellation, multiple speakers, standby/wake, notifications during DRM playback, and Ring colliding with a reminder. Report the actual latency and any native Tizen gaps before claiming support across every screen. If reliability is insufficient, keep reminders disabled rather than show stale or misleading alerts.


Do not make an untrusted Home Assistant HTTP or MQTT endpoint publicly accessible. Use a protected LAN and least-privilege credentials. Disable ADB / wireless debugging once installation and verification are complete. Review any fork's build scripts before executing them on a family television.

## 17. Acceptance journeys (core release and optional-module gates)

| ID | From | User action / event | Pass condition |
|---|---|---|---|
| AT-01 | Home | Move focus Netflix -> Disney+ -> BBC | Each tile lifts, background changes smoothly, remote remains responsive |
| AT-02 | NAS row | Highlight film, open details/play | Matching poster/backdrop; film opens in the embedded platform player and sound/subtitles work |
| AT-03 | NAS row | View All -> Series -> season -> episode | Native catalogue flow; correct episode plays in the embedded platform player; no folder interface |
| AT-04 | TV Home | Select Live TV with VPN off | App does not send unprotected data; useful blocked/connecting state |
| AT-05 | ReamSmartz playing | Force VPN disconnect | No unprotected stream continues; user sees an explanation |
| AT-06 | Any TV Home | Spurs matchday | Card on matchday only, correct UK kick off, never scores, opens protected Live TV |
| AT-07 | All TVs ambient/Frame Art Mode | Ring button pressed | Live camera shown automatically; restore exact previous state |
| AT-08 | All TVs ambient/Frame Art Mode | Ring **person detected** | A 5 s toast naming the doorbell, no camera and no interruption; ordinary motion shows nothing *(changed 25 October 2026, RING-01/02)* |
| AT-09 | Netflix/music in progress | Ring event | Visible camera with verified OS behaviour, playback/audio restored |
| AT-10 | Frame art | No notification | Only chosen picture; never clock, VPN, sports, audio controls or tiles |
| AT-11 | Sony Home | Select Xbox | Xbox wakes, Sony HDMI 3 selected; other TVs never show Xbox tile |
| AT-12 | Samsung Frame | Exit Art Mode and launch BBC/Netflix | Echo Studio audio active without manual Bluetooth reconnect |
| AT-13 | Sony and TCL independently | 100 fast D-pad focus changes after standby, before and after cleanup | Meets tested latency targets on both; no memory crash, no stuck artwork |
| AT-14 | Active Home | Check header / wait for evening | Real UK time, verified VPN state, gradual time-responsive lighting |
| AT-15 | Music | Open Music, play/pause/skip | Only controls proven supported by chosen Amazon playback path exposed |
| AT-16 | Voice mode | Ask 'Find a film on my NAS' | Appropriate shortlist; speaking/listening indicator clear; Back dismisses |
| AT-17 | Device reboot | Restart then open Home, Ring event | Launcher, permissions, Home Assistant triggers and restoration survive reboot |
| AT-18 | Fresh Android TV installation | Skip optional cleanup | Full launcher works; no packages changed; prompt does not recur unexpectedly |
| AT-19 | Sony, TCL, Frame | Open TV Performance on each display, and skip it on another | Common owner-facing experience; no packages changed unless user opts in; correct OS/target detected |
| AT-20 | Sony and TCL, optional Android cleanup | Preview proposed package changes independently | Exact model/firmware allowlist, rationale, risks, dependencies and dry-run visible; no package changed before confirmation |
| AT-21 | Supported model after one cleanup batch | Run full streaming, remote, VPN, Ring, HDMI/audio and standby suite | All essential functions still work, with measured comparison against baseline |
| AT-22 | Cleanup already applied | Undo last batch, then Restore all | Original per-user package states restored and TV remains usable without custom launcher |
| AT-23 | Samsung Frame native Tizen | Open optional TV Performance, review native options | Model/firmware-specific guided cleanup offered; Android commands absent; factory packages not promised removable |
| AT-24 | Samsung Frame native Tizen | Attempt unsupported SDB/root-based cleanup or firmware changes | Ordinary module refuses it; no factory package removal; Art Mode, audio, recovery and updates preserved |
| AT-25 | Native Frame | Open performance settings | Only supported native Tizen operations are offered; Android package operations are unavailable |
| AT-26 | Android TV, Alexa reminder due | Create a visible, non-sensitive Alexa reminder and watch Netflix/BBC | Correct text toast overlays video for 6-10 seconds without taking focus or pausing; actual latency recorded |
| AT-27 | Alexa reminder scheduled then edited/cancelled | Change or cancel before due | No old message appears; revised label/time shown only if confirmed by Alexa data source |
| AT-28 | Alexa reminders on several devices | Same Alexa reminder appears in multiple sensors | Each opted-in TV sees no more than one toast; privacy and per-TV settings honoured |
| AT-29 | Alexa reminder and Ring doorbell | Trigger both concurrently | Live Ring feed wins; no reminder obscures it, and no stale reminder appears later |
| AT-30 | Native Frame | Alexa reminder due inside and outside our app | Toast works inside our visible app; no third-party overlay claim or forced viewing interruption for a minor reminder |
| AT-31 | Native Samsung Art Mode | Alexa reminder due with defaults | Artwork remains unobstructed; Ring remains the only mandatory interruption |
| AT-32 | Unknown TV platform | Open optional cleanup module | Safe read-only guidance only, no unsupported Android/Tizen commands or misleading promises |
| AT-33 | Samsung Frame Home | Focus and select Samsung TV Plus tile | Correct branding and themed backdrop; existing native Samsung TV Plus opens with working channels and guide; no TV Plus tile on Sony/TCL |
| AT-34 | Samsung Frame | Launch Samsung TV Plus while ReamSmartz VPN policy is enabled | Samsung TV Plus does not mistakenly inherit ReamSmartz VPN gate; Live TV/ReamSmartz remains protected |
| AT-35 | Samsung Frame, TV Plus playing | Ring doorbell pressed or person detected | Automatically displays live camera as the highest-priority event after any active smoke/CO emergency; verifies return to previous channel/app or documents firmware limitation before release |
| AT-36 | Samsung Frame, optional cleanup | Preview all proposed changes and run regression check | Samsung TV Plus app, channel list and required services cannot be disabled; playback, guide, Art Mode, audio and system recovery still work |
| AT-37 | Neutral Home arrival on each TV | Show Home without moving remote | Full image, one low glass dock, one clock, tiny utilities only; no three-row content wall or unrelated promotional hero |
| AT-38 | App dock | Highlight Netflix, BBC, Music, Live TV in turn | Background is unmistakably related to the highlighted service or its own brand-aware fallback; generic unrelated scenery never remains under an active selection |
| AT-39 | NAS shelf | Highlight three distinct film posters and one series | Every backdrop, title and metadata element matches that exact item ID; no stale prior film art, guessed metadata or generic app imagery |
| AT-40 | App focus | Hold Right rapidly across all tiles and release | Visual focus follows input without delay, and the final settled background belongs to the final focused app; no queued, old artwork appears later |
| AT-41 | App dock | Press Down then Up | Single Synology row emerges naturally; dock recedes without disappearing; Up restores dock and last app focus without a white flash |
| AT-42 | NAS shelf | Select View All then navigate Series > season > episode and Back | One consistent poster-led visual language; each Back action restores previous focus and context |
| AT-43 | Sony, TCL and native Frame application | Compare the same focus states | Shared layout and names are visually recognisable on all three; reduced render profile changes motion cost only |
| AT-44 | Home with bright snowy/photo background | Focus tiles and read app labels from living-room seating | Glass/titles maintain accessible readability; no hard-coded dark-on-dark or white-on-white failure |
| AT-45 | App dock with source image unavailable | Disconnect network or remove an optional hero asset, then focus item | Brand-aware asset or correct neutral fallback appears; no unrelated image, missing-image icon or navigation freeze |
| AT-46 | Evening transition | Keep the same app selected across day/dusk/night boundary | Only material warmth/lighting changes gently; context, artwork identity, position and focus stay stable |
| AT-47 | Sony OLED and TCL | Leave launcher inactive for configured timeout | Dock and clock vanish, artwork fills entire screen without permanent bright static controls; input returns to previous focus |
| AT-48 | Samsung native Art Mode | Observe with no Ring event | Family/artwork only: no clock, HUD, VPN, apps, Alexas or music controls, regardless of time of day |
| AT-49 | Any state including Frame artwork | Ring doorbell press | Ring live view interrupts immediately even during transition (unless a smoke/CO emergency is active, when it is queued), then restores exact prior visual/playback state; no animation causes a missed event |
| AT-50 | Netflix playing | Trigger Alexa reminder during playback, then trigger Ring | Reminder remains a small focus-neutral toast; Ring takes priority without stale toast lingering |
| AT-51 | Voice on Home | Activate ChatGPT then press Back | Small temporary listening pane appears without obscuring main content; Back returns to identical Home focus and backdrop |
| AT-52 | Live TV focus with VPN off | Move focus onto tile, then press OK | Focus alone never starts VPN or Live TV; OK opens protected, clearly explained Connect/Retry/Back flow |
| AT-53 | Music screen | Play music then leave controls idle | Genuine album artwork drives the background, compact play/pause/skip is legible; quiet state reduces persistent OLED-bright UI |
| AT-54 | Settings accessibility | Turn on reduced motion, high contrast and reduced transparency | All content and commands remain available, focus unmistakable; movement and glass complexity fall back without a redesign |
| AT-55 | Public project design hand-off | Inspect layouts and short motion studies for all Section 4.13 states | Assets are cleared, input maps and source permissions supplied, source-of-truth design tokens present, and videos/screenshots show the same visual system |
| AT-56 | Saved layout migration (v2.3 supersedes fixed-membership assertion) | Load legacy fixed-set order, valid user-added shortcuts, unavailable packages and corrupt saved state | Preserve intentional valid owner pins and relative order; drop or mark unavailable entries safely; fresh install gets six curated defaults; provide deterministic empty-dock recovery without silently restoring removed defaults |
| AT-57 | Home dock | Hold OK, Move with Left/Right, press OK, restart | Smooth reorder saves and persists without confusing focus |
| AT-58 | Home edit mode | Reorder then Back; repeat with a Ring alert | Back restores old order, interruption does not half-save and Ring wins |
| AT-59 | Home edit menu (v2.3 supersedes fixed-membership assertion) | Hold OK on Home/Apps, Open, Move, Add/Remove from Home and App information; restart and retry as Child | All supported actions work with remote; deliberate edits persist; Back cancels; protected edits require adult authorisation; removing a shortcut never uninstalls its app |
| AT-60 | Sony/TCL/Frame | Use different dock order and Xbox/TV Plus shortcuts | Per-TV capabilities remain correct and Live TV stays VPN protected |
| AT-61 | Six/seven-item dock | Exercise approved room choices, child lock and reduced motion | Readable single dock with persisted deliberate owner edits and platform-appropriate shortcuts |
| AT-62 | TCL Settings > Connections | Enter valid and invalid Home Assistant/Jellyfin settings by remote | Correct authentication/connectivity states, protected local storage and offline fallback |
| AT-63 | TCL Windows pilot | Build separate preview and connect using supported Android 11 debugging or offline sideload | Stock HOME untouched, launch and edit controls work, clean uninstall |
| AT-64 | TCL AirPin LITE | After authorised setup, restart and cast from iOS device | Receiver operational status is real; no misleading glyph; resource use measured |
| AT-65 | TCL/SONY NAS | Play representative 4K/HD file with embedded Media3 and AVPlay on their respective devices | Audio/subtitles work where supported; first frame measured; supported remux/transcode or unavailable outcome documented |
| AT-66 | TCL/SONY Live TV | Force VPN drop during protected ReamSmartz traffic | Target app cannot leak unprotected packets; otherwise remains blocked |
| AT-67 | Family Home | Use six starting shortcuts, add an eighth and rearrange | One uncluttered dock, remote-only management, per-TV persistence |
| AT-68 | Samsung Frame | Return from Ring interruption to native Art Mode and native TV Plus | Existing native services remain accessible; actual model verified |
| AT-69 | Voice | Ask to open BBC and a broad general knowledge question | Local/ChatGPT route operates correctly, UK English voice and visible microphone |
| AT-70 | Public GitHub | Inspect project files and generated test APK | No personal LAN IP, SSID, MAC, real camera pictures or API secrets committed |


### 16.3 Performance claims must be measured

Collect cold/warm start, D-pad latency, focus-to-background timing, dropped frames, RAM and first-frame playback on the real Android TV 11 TCL and Sony. The reported third-party "90% faster" and proposed 500ms 4K start are **not** measured Unified TV results. No bloatware changes form part of the first pilot.

## 18. Delivery stages and decision gates

*These stages are the original v1.x delivery plan, kept for traceability. The current order is the release gates G0–G6 in the authority section and [SPEC.md](SPEC.md) *(changed in v3.3)*.*

**Stage 0: Hardware audit and safe proof of concept.** Collect model/firmware IDs for **Sony, TCL and Samsung Frame**, record individual performance baselines **before any changes**, identify safe official settings and rollback limits by OS, validate Home Assistant Ring **and Alexa reminder** entities, VPN networking and embedded playback. No irreversible changes and no mandatory cleanup.

**Stage 0.5: Safe CI/CD and Windows-to-TCL sideloaded test (P0).** Build a test-signed **standalone** Android preview package, separate from the existing HOME application, in GitHub Actions and verify the APK manifest and signature. Confirm Windows can connect to TCL **Android TV 11** through a *supported, user-enabled* debug route. Google's modern Wireless debugging pairing method is officially specified for **Android TV 13+**, so inspect the TCL Developer options for manufacturer **Network debugging** and its stated port before suggesting any ADB commands. If neither supported network nor USB debugging is available, use an APK USB/manual installation route and test UI without ADB. Run read-only model/build checks before any authorised installation. Verify Home, remote focus, app pin/move, safe return, distinct APK package identity and clean uninstall while stock Google TV Home remains unchanged. Do not scan the LAN, change factory packages or start cleanup. Build **Settings > Connections** and test valid/invalid local endpoints as the next Stage 0.5 deliverable. The actual private IP is never in public GitHub. See [TCL testing plan](LOCAL_CONNECTIONS_AND_TCL_TESTING.md).

**Stage 1: Two critical proofs.** Establish Ring automatic live video over Android wallpaper and **Samsung native Art Mode**, both triggers and return path. Prove VPN fail-closed Live TV. These decide the architecture; repeat Samsung interruption tests while **native Samsung TV Plus is playing**.

**Stage 2: Core launcher and design-system proof on TCL, with Sony benchmark in parallel.** First build the quiet one-dock Home plus the full Section 4 focused-item background behaviour, NAS poster reveal, View All, embedded playback, clock, ambient images and time-of-day mood. Approve Section 4.13 visual hand-off and Section 4.14 design gates before completing other modules. Verify Ring and Alexa text overlay receiver above active playback. Performance profiles and revert paths for **both** Android TVs. A separate **opt-in TV Performance & Cleanup** module follows only after safe, model/firmware-specific tests.

**Stage 3: Household rollout.** Sony with Xbox, native Frame application with native art handoff, **Frame-only Samsung TV Plus tile and verified Tizen launch/return path**, Echo Studio audio fix, and per-device settings. Offer the Samsung **supported-settings** tune-up alongside optional, reversible Sony/TCL Android cleanup; test Alexa toasts inside the visible native Frame application and document native Tizen limitations. Only release after per-TV acceptance matrix passes.

**Stage 4: Delight features.** Music Now Playing if supported by Amazon, ChatGPT Live with a British voice, refinements to content suggestions and advanced personalisation.

## 19. Open factual checks, not undecided design preferences

- Exact TCL, Sony and Frame model identifiers, firmware revisions, hardware constraints, recoverability, supported Samsung cleanup settings, **Samsung TV Plus native app launch controls and switch-back behaviour**, and native Frame launch capabilities.
- Ring model, Ring subscription and **real person detection entity/event** through Ring or ring-mqtt.
- Whether the **Alexa Devices** integration is installed and exposes the correct Alexa reminder timestamp/label for this Amazon account and Echo Studio; actual polling delay, Alexa Household impact and tested reminder due behaviour.
- Whether the Frame's current Home Assistant integration supports reliable Art Mode interruption, source change and exact return after playback.
- Which Echo Studio generation is present and which Frame audio-routing commands / optical ports are available.
- NAS model, storage paths, Jellyfin support, media codecs, subtitles and dependable embedded playback.
- NordVPN actual Android TV version, always-on/lockdown support and effects on permitted apps/local LAN.
- Voice microphone route, GPT-Live developer API version, cost and private service host.
- Legitimate streaming-device compatibility for SmartTube, Netflix, Disney+, BBC iPlayer, ReamSmartz and Amazon Music.

## 20. Explicitly outside initial scope

No mandatory removal of bloatware, no promise that every manufacturer permits factory app removal, no untested/unrecoverable Samsung SDB deletion in the normal cleanup module, no root-only optimisations, no silent package disabling or always-on ADB; no Plex interface; no VLC home tile; no generic YouTube app tile; no persistent football scoreboard; no permanent ambient clock on Samsung Frame; no unrelated trending/recommendation feeds; no full-time poster walls on neutral Home; no generic unrelated wallpaper behind a highlighted app or film; no mandatory real-time full-screen refraction; no automatic trailer previews on focus; no automatically switching on a fully powered-off TV for Ring unless separately authorised; no untested claims of access to Amazon's music catalogue; no dependence on always-on ADB; no unsolicited advertising or mandatory sign-in to a discovery service.

## 21. Reference links and research

- https://github.com/matheus-caldeira/cleanyourtv-ui
- https://github.com/Gathin23/cleanyourtv
- https://github.com/simbasjungles/free-my-tv
- https://github.com/nthnclrk/CleanFrame
- https://github.com/Ansub/tv-detox (optional Android TV cleanup candidate; never automatic)
- https://dribbble.com/shots/5773537-TV-Interface-motion (motion inspiration only)
- https://dribbble.com/shots/21647921-Vision-Stream-Next-Gen-Smart-TV-App-UI (cinematic presentation inspiration only)
- Reference screenshot discussed during product discovery (24 October 2026): calm coastal photograph, one translucent app dock, discreet clock and utility icons; use visual principles only
- https://support.apple.com/en-gb/106336 (tvOS 26 introduction of Liquid Glass)
- https://support.apple.com/en-ie/106336 (tvOS 27 software updates)
- https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos
- https://developer.apple.com/design/human-interface-guidelines/focus-and-selection/
- https://developer.apple.com/design/human-interface-guidelines/materials
- https://developer.apple.com/design/human-interface-guidelines/images
- https://developer.apple.com/design/human-interface-guidelines/layout
- https://gitlab.com/flauncher/flauncher
- https://gitlab.com/piermark/omarchytv
- https://github.com/vessosa/ha-video-toast-tv
- https://github.com/nikitacoolagin/hass-camoverlay
- https://github.com/bonzanni/ha-samsung-tv-frame
- https://github.com/ardazeytin/samsung-tizen-tv-debloat (experimental reference; 2018 device only)
- https://www.samsung.com/uk/support/tv-audio-video/how-do-i-manage-apps-on-my-smart-tv/
- https://developer.samsung.com/smarttv/develop/faq/application-installation.html
- https://developer.samsung.com/smarttv/develop/faq/other-features.html
- https://www.home-assistant.io/integrations/alexa_devices/
- https://www.home-assistant.io/integrations/nfandroidtv/
- https://www.home-assistant.io/integrations/ring/
- https://github.com/tsightler/ring-mqtt
- https://www.home-assistant.io/integrations/xbox
- https://www.home-assistant.io/integrations/braviatv
- https://support.nordvpn.com/hc/en-us/articles/19509682644369-NordVPN-Kill-Switch-how-does-it-work
- https://developer.amazon.com/docs/music/API_playback_overview_v2.html
- https://developers.openai.com/api/docs/guides/audio

*This document is a build specification, not a claim that each integration has already been verified on the household's hardware.*

## 22. Change log

- **v1.8 (24 October 2026):** Consolidated the short v1.7 architecture proposal into the complete requirements without removing v1.5 functions; confirmed TCL Android TV 11 and AirPin LITE, added remote-based private Connections UI, secure local settings, separate Windows-to-TCL Stage 0.5 with Android 11-specific debugging constraints, player comparison, honest VPN routing tests, optional AirPin and hybrid voice, nine new tests (AT-62..70). No household network identifiers belong in public GitHub.


- **v1.5 (24 October 2026):** Added P0 Apps library and remote-only Home dock pin/move/remove, persistent per-TV layout, child edit lock, single-row overflow, a required design state and six acceptance tests (AT-56..61). Refer to `docs/APP_CUSTOMISATION.md`.
- **v1.4 (24 October 2026):** Replaced the brief cinematic-focus paragraph with a **full P0 visual and interaction specification** for an original Liquid Glass-era-inspired, CleanFrame-calm interface. Defined one-dock resting Home, progressive reveal of NAS row, exact-app/film hero background contract and fallback source priority, focus timing and state machine, glass/material tokens, UK time and minimalist statuses, four time-of-day modes, remote journeys, accessibility, reduced-resource rendering on TCL and Sony, Ring/Alexa/VPN/voice presentation, 14 required design hand-off screens, 12 design pass/fail rules and 19 new acceptance tests (AT-37..55). Corrected previous generic-background mockup mismatch. tvOS 26 introduced Liquid Glass; tvOS 27 is the continued version reference. No other functional requirements weakened.
- **v1.3 (24 October 2026):** Required Samsung TV Plus as a Frame-only tile, preserved its native Tizen app and related services during optional TV cleanup, excluded TV Plus from the ReamSmartz VPN check, and added native-app/Ring acceptance tests.

- **v1.2 (24 October 2026):** Expanded optional TV Performance & Cleanup from Android-only to a universal, capability-detected **Sony + TCL + Samsung** feature, with safe Tizen settings guidance, a distinct Android streamer target and future platform adapters. Gave ageing Sony equal performance priority. Added Home Assistant/Alexa reminder text toasts over active playback, strict Ring alert priority, privacy controls, official Alexa Devices polling limitations, Samsung native Tizen overlay limitations, and nine extra acceptance journeys.
- **v1.1 (24 October 2026):** Expanded brief TCL debloat guidance into an optional, administrator-only TV Performance & Cleanup module for the public Android fork. Added dry-run previews, explicit package consent, versioned device rules, separate ADB helper, rollback independent of the launcher, platform exclusions and six acceptance journeys. Core launcher remains fully functional without cleanup.
## 23. Additional proposal reconciliation (v1.9)

See [decision record](SPEC_RECONCILIATION.md). Preserve every earlier feature and release gate. Add Adult/Child media profiles with separate restricted server credentials, adult-PIN protected editing and adult return. Never imply launcher profiles switch streaming-app accounts. Server restrictions must govern catalogue and playback, not just hide posters.

Embedded Media3 direct playback and real position reporting are now a preview implementation track. Native Media3 and AVPlay are the selected engines; VLC is excluded. Codec, subtitle, seek and hardware acceptance remain mandatory. Continue Watching requires proven server round-trip. Native Wi-Fi/Bluetooth/audio/OLED/CEC controls remain authoritative.

- AT-71: a Child user cannot browse or play an Adult-only server item by changing screens, search, or player requests; use its own restricted server token.
- AT-72: PIN protects adult return and editing; external app/OS limitations are clearly explained. Test PIN recovery and repeated failures before release.
- AT-73: report real playback position on pause/stop; reload from the server after restart; suppress Continue Watching until proven.
- AT-74: refuse credential-bearing redirects and non-consented HTTP; accept IPv4/IPv6/hostnames; encrypted data survives same-key updates and is removed by Forget.
- AT-75: native TV settings still control sound, picture, Wi-Fi, Bluetooth and CEC; no cleanup or hardware override occurs automatically.

v1.9 (24 October 2026) adds these requirements and reconciles the new shorter proposal without deleting app personalisation, native Samsung features or VPN/Ring release gates. Preview implementation status is tracked separately in [0.2 notes](PREVIEW_0_2.md).


## 24. Native Samsung architecture and platform boundaries

### 24.1 Native application and lifecycle

Deliver an installable Tizen Web Application package built with supported tooling, signed with valid author and distributor certificates for the target device. Tizen Studio is a development and installation tool, not the application hosting service. Keep certificate private keys, passwords and device identifiers outside the public repository and published build logs. Same-author update continuity is a release requirement. An unsigned WGT or browser demo is a development artefact, not a tested TV installation.

The app runs full screen but cannot replace Samsung Smart Hub. Physical Home opens Samsung Home. Back navigates within our app and returns from details/player to the prior screen; follow Samsung exit behaviour at our root, and do not intercept long Return. Back from another company's app cannot be promised to reopen Unified TV. Provide an ordinary installed-app launch route and a model-tested shortcut where available.

Automatic startup remains model-dependent. Evaluate available Autorun Last App behaviour separately for standby wake, cold power-on and reboot, and record whether developer-installed packages participate. If unsupported, show the actual supported launch route; do not claim autostart passes. No developer tooling, ADB or SDB session may be a permanent runtime dependency.

Tizen can suspend JavaScript when the app is hidden. Save navigation, playback state and current profile before hiding; stop/release playback as required, close obsolete requests, and revalidate credentials, events, network and media position when visible again. A WebSocket inside a hidden app is not a reliable Ring delivery service. Register only supported extra remote keys and keep all screens usable with a basic directional pad, OK and Back.

### 24.2 Service availability and shared appearance

Use identical hierarchy, tile sizing, safe areas, artwork identity, typography, focus behaviour, motion intent and accessible alternatives across the two implementations. Shared design tokens and fixtures should drive screenshot comparison. The lightweight profile reduces rendering work without replacing the product's layout. Native system dialogs may differ; recognise these differences rather than claiming pixel-identical OS screens.

Preserve SmartTube, Netflix, Disney+, BBC, Live TV and Music as the curated shared default intent, plus Xbox only on Sony and Samsung TV Plus only on Frame. Resolve installed app IDs at runtime and allow owner-approved mappings. Android-only apps do not become native Tizen apps through a launcher tile. SmartTube and ReamSmartz remain unavailable on Frame unless a supported client or explicitly approved substitute exists. Do not silently substitute YouTube, promise access to Amazon Music's full catalogue or expose a non-working shortcut as playable. App absence produces a clear message, and ordinary removable/pinnable dock behaviour remains available.

### 24.3 Ring event coordinator and recovery

An external household coordinator in Home Assistant must receive authorised doorbell and person events, identify target rooms, reject stale/duplicate events, capture source and Art Mode state, request a model-supported launch, and wait for acknowledgement that the camera's first frame is visible. Android's overlay service remains a separate implementation. Foreground camera connections alone do not prove background interruption.

Use the Ring integration's currently exposed event model; normalise legacy binary sensors and current event entities through an adapter. Doorbell press and person classification are separate acceptance cases; generic motion must not be relabelled person detection. Live view and a last-recorded clip are separate sources. Label Connecting, Live, unavailable and stale honestly; do not label a recorded clip live. Avoid camera URLs and household images in logs.

SmartThings capabilities vary by device. The consumer SmartThings app's Art Mode controls do not prove a universal cloud command for closing Netflix, launching a developer app or restoring artwork. Evaluate Home Assistant's Samsung integration and a model-specific local Samsung or Art Mode adapter; use SmartThings only for commands actually exposed and tested. Do not force-stop DRM applications through undocumented APIs. App launch acknowledgement and camera first-frame acknowledgement are different steps.

On dismiss or timeout, restore only the captured state that the platform permits. Art Mode should regain its prior artwork without chrome. An interrupted third-party stream may require the viewer to resume manually; preserve navigation/source where supported and report the limitation. Repeated Ring events must not build an infinite queue. A fully off TV stays off unless an explicit wake policy is later enabled. If a launch or restoration command fails, show a truthful status in the household controller and retain a safe remote recovery path. Ring has priority over reminders, voice and transitions, and is itself below smoke/CO emergencies; reminders never force the Frame out of Netflix or Art Mode.

### 24.4 Embedded playback and private authentication

AVPlay owns the supported playback surface. Keep its lifecycle explicit: open, prepare, first frame, playing, paused, buffering, error, stopped and closed. Release the previous player before opening another item. Test aspect ratio, subtitles, audio selection, seek precision, resume, end-of-item and repeated Back on the Frame. The Android Media3 implementation must meet the same viewer outcomes.

The proposed private LAN media broker authenticates the TV using a scoped revocable session, resolves only authorised server items and attaches the correct server credentials upstream. Restrict origins and paths, enforce profile permissions, validate range requests, rewrite or reject unsafe HLS segment/redirect destinations, and expire sessions. AVPlay authentication may use a documented scoped cookie if validated on the target firmware. A generic AVPlay DRM-header option is not evidence of arbitrary non-DRM HTTP header support. Never embed an administrator token in a stream URL, JavaScript bundle or image source. This broker is a design proposal pending implementation and security tests, not an existing working service.

Use platform Key Manager secure storage for native Tizen credentials; fail closed if it is unavailable. Do not fall back to plaintext localStorage. Non-secret UI preferences can use local storage. Browser simulation may keep credentials only in memory and must identify that mode. Encrypt Android credentials using Keystore-backed storage, exclude them from backup/transfer and support Forget. Private connections stay configurable locally by the owner.

### 24.5 VPN and platform controls

Android Live TV remains blocked until verified NordVPN routing and tunnel-drop prevention apply to the actual playback app, including deep links, matchday actions and voice. System VPN indicators alone are insufficient. LAN access to media and Home Assistant must still work. OS-level VPN protection and bypass paths require independent testing.

Tizen does not inherit Android split tunnelling. A compatible native client and network policy are prerequisites. A proposed router or VLAN route must include fail-closed behaviour, correct DNS and IPv6 handling, and proof that other TV services and local integrations still work. Routing the whole TV is not equivalent to routing one app. If app availability or routing cannot be verified, Frame Live TV remains explicitly unavailable. Samsung TV Plus is separate and stays accessible without the ReamSmartz gate.

Sony input selection and Xbox wake are separate operations. Use a supported Xbox or Home Assistant integration for console wake and a model-verified Sony input control for HDMI 3. Preserve eARC/audio behaviour. Correct the receiver name to AirPin LITE on TCL; its installation does not prove boot startup or active AirPlay receiving. Echo Studio output depends on the real connection topology and supported codecs; preserve native audio controls and avoid promising universal Bluetooth, eARC or multiroom parity.

### 24.6 Voice and secondary household features

Keep remote control sufficient for every essential journey. Initial bounded voice commands may use an available local/system recogniser with a keyboard alternative. Never route an unrecognised command to a general home-control service that can bypass Live TV restrictions. Optional OpenAI live voice requires a server-held API key with short-lived client credentials or a server relay, visible microphone state, explicit activation and cancellation; select and test an appropriate UK voice rather than promising an exact accent. No long-lived API key belongs in either app.

Retain Amazon Music-inspired album artwork, the quiet matchday-only Spurs card without scores, room preferences, personal gallery, day/dusk/night mood, accessibility and optional safe performance advice. These features remain requirements even when their service adapters are unfinished. Personal gallery and native Samsung Art Mode are distinct modes; native artwork must contain no permanent interface. OS profiles, external streaming accounts and parental restrictions remain outside a launcher-only PIN's control. Explain these boundaries in owner settings without cluttering viewing screens.

## 25. Acceptance evidence and release plan

### 25.1 Native Samsung acceptance additions

The submitted Samsung AT-58 to AT-61 are mapped to NT-01 to NT-04 below, preserving the existing dock tests with those AT numbers. Each test records app revision, model, OS/firmware, setup, actual observations and evidence. A desktop simulation result never becomes a hardware pass.

| ID | Journey | Pass condition and failure boundary |
|---|---|---|
| NT-01 | Frame startup | Test standby, cold boot and reboot independently. App reaches foreground automatically only where supported; otherwise mark autostart unsupported or blocked and document manual launch. |
| NT-02 | Frame NAS playback | Representative supported files play in AVPlay with audio, subtitles, seek and resume. Record first-frame and buffering; unsupported codecs have a clear outcome. No blanket MKV or no-transcode promise. |
| NT-03 | Ring during native Netflix | Real doorbell and person events separately reach the coordinator; app launch and camera first frame are acknowledged without remote action; restoration and any resume limitation recorded. Failure blocks this release claim. |
| NT-04 | Samsung TV Plus | Discover verified native application, launch it, retain channel guide, recover our app using a documented supported route; preserve native installation and dependencies. |
| NT-05 | Home and Back | Physical Home opens Samsung Smart Hub; Back inside our app returns predictably; long Return retains OS exit behaviour. |
| NT-06 | Hidden lifecycle | Switch apps and resume repeatedly; no claim that suspended JavaScript delivered background events; restore focus and revalidate network and media state. |
| NT-07 | Private playback | Reject wrong-profile items, unsafe redirects, expired sessions and non-consented HTTP; no server tokens in URLs, logs or localStorage. |
| NT-08 | Frame protection | Ring returns active Art Mode to the prior artwork; reminders do not interrupt it; a fully off television stays off. |
| NT-09 | Service absence | Missing Android-only services show honest availability; no fake SmartTube/ReamSmartz native launch or silent replacement. |
| NT-10 | Live TV isolation | No traffic escapes during VPN loss or DNS/IPv6 bypass; per-app versus whole-TV routing is stated; otherwise keep the tile unavailable. |
| NT-11 | Signed install and update | Valid signed WGT installs on authorised target; same-author update retains configuration; rollback preserves stock Smart Hub and TV Plus. |
| NT-12 | Visual and remote parity | Compare shared mockup states, 100 rapid directional inputs, edit/save/cancel, accessibility and artwork identity across native codebases. |

### 25.2 Test levels and current evidence

Record the highest completed `test_level` for each capability: `none`, `unit`, `simulated`, `ci_build`, `pc_network`, `physical_tcl`, `physical_sony` or `physical_frame` *(changed in v3.3)*. A household service end-to-end test is a `physical_*` test with the real service connected. Each capability lists its actual highest completed level and remaining gates. No all-TV-ready label is permitted while one required target or mandatory Ring/VPN path is unproven.

On 24 October 2026, the standalone CI preview was installed on the authorised Android TV 11 TCL with stock Home retained. Its home screen, controls, unconfigured library response, protected Live TV refusal, hold-OK edit menu, move cancellation, saved layout across restart and stock Home recovery were exercised. Hardware testing identified weak Settings focus visibility and focus restoration after reorder; fixes and subsequent results belong in the dated TCL report. A 100-directional-event smoke check is not an input-latency benchmark, and observed process memory is not a proven budget. No configured Jellyfin playback, Ring, VPN tunnel, Alexa, gallery-source or voice service acceptance is implied.

The Android preview has a passing earlier GitHub Actions build and separate current local checks. Native Tizen code is work in progress, with no signed household WGT, AVPlay hardware pass, autostart proof or Frame Ring/Art Mode handoff yet. Sony hardware remains untested in this phase. Requirements are not implementation claims.

### 25.3 Delivery sequence and definition of done

1. Finish the safe Android preview and real TCL remote checks, preserving the factory launcher. Record startup/input/memory baselines and fix regressions. Keep local device addresses and raw diagnostics private.
2. Complete native Tizen shell, local secure settings, installed-app discovery, remote focus, dock editing, catalogue and embedded-player lifecycle with automated and simulation tests. Share design tokens and journey fixtures; never present browser fallback playback as AVPlay proof.
3. Implement and test the protected media route, server-restricted profiles and real watch-state round-trip on both runtimes. Add bounded offline caches and accurate readiness screens.
4. Prove external Frame event coordination, native app launch and Art Mode recovery; prove Android alert overlays separately, including DRM/input limitations. Keep all unsupported gaps explicit.
5. Prove VPN isolation and failure behaviour before enabling protected Live TV. Validate music, fixtures, AirPlay, audio and optional voice according to actual service permissions.
6. Produce reproducible APK and WGT builds with dependency/licence review, secret scanning, stable signing and release artefact hashes. GitHub Actions tests and packages Android; native Tizen packaging and signing need their own supported toolchain and protected owner credentials.
7. Release only against the complete AT and NT matrix with per-TV evidence, remaining limitations, install/update/recovery instructions and owner-controlled configuration. Claim tested support only for models and firmware actually exercised. Other TVs remain potential adapter targets, not blanket compatibility claims.

### 25.4 Official technical sources for these revisions

Research reviewed 24 October 2026. Vendor documentation defines API and lifecycle boundaries; household behaviour still requires tests. The media broker and external Ring coordinator are proposed engineering designs inferred from those boundaries.

- Samsung multitasking https://developer.samsung.com/smarttv/develop/guides/fundamentals/multitasking.html
- Samsung termination and Return https://developer.samsung.com/smarttv/develop/guides/fundamentals/terminating-applications.html
- Samsung remote input https://developer.samsung.com/smarttv/develop/guides/user-interaction/remote-control.html
- Tizen Application API https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/application-api.html
- Samsung AVPlay https://developer.samsung.com/smarttv/develop/api-references/samsung-product-api-references/avplay-api.html
- Tizen Key Manager https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/keymanager-api.html
- Samsung web engines https://developer.samsung.com/smarttv/develop/specifications/web-engine-specifications.html
- Samsung signing https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html
- Samsung consumer Art Mode https://www.samsung.com/us/support/answer/ANS10005239/
- Home Assistant Samsung https://www.home-assistant.io/integrations/samsungtv
- SmartThings capabilities https://developer.smartthings.com/docs/devices/capabilities/
- Home Assistant Xbox https://www.home-assistant.io/integrations/xbox/
- Home Assistant Ring https://www.home-assistant.io/integrations/ring/
- Media3 formats https://developer.android.com/media/media3/exoplayer/supported-formats
- OpenAI voice security https://developers.openai.com/api/docs/guides/voice-webrtc

Version 2.0 preserves the detailed earlier design and acceptance requirements while replacing the external Frame runtime and VLC playback decisions. Unimplemented features remain requirements and do not become working capabilities through this document.


v2.1 historically recorded the fixed-dock decision, material rendering pass and VPN handoff/status work. Native Tizen and earlier household integration gates remain unfinished.


## 26. v2.2 continuation acceptance additions

| ID | Requirement | Pass evidence |
|---|---|---|
| UX-01 | Two-column Settings | Remote navigates every category and control; focus and Back behaviour checked on TCL and simulated Android. |
| UX-02 | Editable curated Home | Fresh six defaults, Open/Move/pin/unpin/info menu, persistence, cancellation and protected-app checks pass; child authorisation cannot be bypassed. |
| LIB-01 | Automatic discovery | No saved source leads directly to search; actual NAS appears by name without typed address; timeout/Retry remain usable. |
| LIB-02 | Exact library scope | The three locally chosen roots are the only viewer collections; wrong/other server roots are never automatically exposed. |
| LIB-03 | Native DLNA direct play | Representative files play, pause, seek, stop and return correctly on each supported runtime; unsupported codecs fail clearly. |
| LIB-04 | Progress truth | Jellyfin start/progress/stop reaches the server and resume is verified; DLNA-only playback is never labelled server-synchronised. |
| GAL-01 | Five example images | All five original images load, rotate, fit and respond to pause/next/previous/Back without private data appearing. |
| VPN-01 | Four states and Live TV protection | Distinct Connected, Disconnected, Checking, Unknown; no state alone unlocks Live TV. Live TV unlocks only after NordVPN provider, routing and drop protection are proven on that TV (GATE-VPN-01..05) *(changed in v3.3: merges the register's use of this ID)*. |
| MUS-01 | Official Music route | Correct installed Amazon Music app opens; missing-app route uses the supported store; account search/playlists work in the official app after sign-in. |
| MUS-02 | Custom Music gate | Authorised API/media control access is proven before claiming custom playlist/search/Now Playing support. |
| REL-01 | Version retention | GitHub version/tag, APK, hash, notes, limitations and test results retained; no overwrite of previous release or another agent's code. |
| REL-02 | Updated UX evidence | Fresh screenshots belong to the exact physically tested APK; no substitution of old screenshots. |

Version 2.2 incorporates the latest owner directions for editable Apps, two-column Settings, automatic DLNA, exact media scope, Amazon Music, authorised sample art, privacy and retained GitHub releases. Platform limitations remain explicit and implementation status is kept separately. The v2.3 implementation focus and supersessions follow in Section 27.


## 27. v2.3 delivery focus, architecture clarification and quality gates

**Status and scope.** This section is an approved delivery-plan update to the complete v2.2 requirements, not a claim that Preview 0.5 or any hardware acceptance has passed. Preserve earlier feature requirements unless this section explicitly supersedes their wording. A narrow preview can ship with honestly labelled unavailable features; it must not be called the full household release.

### 27.1 Milestone strategy and sequencing

| Track | Priority | Deliverable | Dependency and release rule |
|---|---|---|---|
| A. TCL viewing vertical slice | **Gate G3 (Preview 0.5), after G1 TCL Home and G2 Animated Home and Wallpaper** *(changed in v3.3)* | Stock-Home-safe install; premium Home; editable Apps; NAS discovery and exact folder selection; title browsing; embedded playback; return/recovery; physical screenshots | Actual TCL access, compatible representative file and verified build/signing. Do not block source development indefinitely on failed ADB; do not claim physical pass without it. |
| B. Samsung feasibility | **Parallel, time-boxed** | Official SDB handshake, author/distributor certificate, signed WGT install, basic D-pad/Back, AVPlay smoke test, app lifecycle and foreground/Art Mode feasibility notes | No working Tizen app or automatic interruption can be assumed from Developer Mode or open port. Owner performs any required certificate sign-in. |
| C. Household safety integrations | **Parallel investigation, gated delivery** | Home Assistant pairing and real Ring press/person tests; VPN route/drop verification and LAN coexistence | Ring and Live TV release gates remain mandatory for the full product. Keep unsupported paths unavailable and honestly labelled. |
| D. Cross-TV parity | **After proven TCL slice and Samsung feasibility** | Sony installation, Xbox/HDMI/eARC, Samsung native TV Plus/Art Mode, visual and remote parity | Each runtime requires independent hardware evidence. |
| E. Secondary experiences | **After stable core, without deleting scope** | Official Amazon Music, gallery refinement, voice, matchday, Alexa reminders, optional performance advice/cleanup | Authorised integrations, safety and hardware support must be proven; no fake API or privileged action. |

The first completed milestone must be a **viewing journey**, not merely a collection of screens or a passing CI job. Each subsequent milestone must retain previous passing journeys as regression tests. Do not merge two agents' branches just to satisfy a date or version number.

### 27.2 Preview 0.5: The Viewing Experience acceptance contract

Preview 0.5 is an Android/TCL-first candidate. Its label must say **hardware-unverified** until the following journeys have been observed on the exact APK, device and firmware. Sony compatibility is a separate result, not implied by TCL.

| ID | Required journey | Pass condition and evidence |
|---|---|---|
| P05-01 | Safe install/update and launch | Inspect package identity/signature and existing data before install; matching-key update retains configuration; factory Home remains default and accessible; no forced uninstall/reset. Record APK hash and install result. |
| P05-02 | Premium Home and remote focus | Six fresh curated defaults, correct platform availability, accurate brand/selected-title artwork or honest neutral fallback, legible glass/focus, predictable D-pad/OK/Back and no focus trap. Capture video or exact-build screenshots and input observations. |
| P05-03 | Editable Apps and dock | Compact grid, long-OK Open/Move/Add or Remove from Home/App information, owner edits survive restart, cancellation does not save, no duplicate package mappings, missing app state handled, child editing protected, Live TV gate applies to every route. |
| P05-04 | Local media discovery | Unconfigured Films & TV discovers Synology MediaServer by name on the TCL, supports Retry, distinguishes no server from failed authorisation, and does not require an address for DLNA. Document actual network/VPN condition. |
| P05-05 | Collection restriction | Owner selects only `/video/Kids`, `/video/Movie`, `/video/TV Show` by discovered server hierarchy/object IDs; saved viewer exposes no other collection; child DLNA bypass refused; folder identity/recovery after NAS restart checked. |
| P05-06 | Browse and title identity | Browse real selected content without sparse-pagination loss; show accurate title and poster where a verified metadata adapter exists; otherwise use a coherent, honestly labelled fallback without mismatched imagery. A folder-button prototype alone is not the premium-catalogue design pass. |
| P05-07 | Embedded film playback | At least one representative real NAS video opens within Media3; pause, seek, stop and Back work; record format, codec, audio, subtitle availability and first-frame time. Unsupported formats fail with a useful message, not a false success. |
| P05-08 | Failure and lifecycle recovery | Disconnect/reconnect NAS or Wi-Fi, switch away and back, standby/wake, and cancel during loading; no indefinite spinner, lost focus, unexpected stock-Home replacement or leaked player resources. Show Retry and prior-context recovery. |
| P05-09 | Settings and accessibility smoke | All six categories and right-hand controls reachable by remote, focus restoration works, enlarged text and reduced motion do not hide essential controls, secrets remain protected. |
| P05-10 | Honest security and release evidence | Protected Live TV remains blocked without measured routing/drop evidence; no credentials or private device data in logs/screenshots; versioned APK/hash, test report, exact-build screenshots and limitations retained. |

**Conditional catalogue gate:** A truly poster-first experience requires a proven metadata source. Do not invent artwork or claim full search, series hierarchy or Continue Watching from bare DLNA. If that source is unavailable for Preview 0.5, record P05-06 as incomplete and label the build a *connectivity/playback preview*, not the polished viewing milestone. Continue Watching appears only after authenticated progress round-trip is verified; local DLNA resume, if implemented, must be labelled local to this device.

**Performance evidence:** Measure rather than promise: cold/warm launch, remote focus response, title-to-first-frame, seek recovery, buffering, memory and long-session stability on the TCL. Record the measurement method, conditions and result. No unsupported universal FPS, sub-second start or 4K claim.

### 27.3 Media service and catalogue boundaries

```text
Owner-approved collection roots (local-only configuration)
        |
        +--> UPnP/SSDP: discover MediaServer and ContentDirectory
        |        |
        |        +--> DLNA object IDs, bounded browse, video resources
        |                 |
        |                 +--> Android Media3 / Tizen AVPlay (format-tested)
        |
        +--> Optional authenticated catalogue adapter, only when available
                 |
                 +--> verified item identity, metadata, artwork, search,
                      seasons, account restrictions and server progress
```

- Discovery does not equal catalogue quality. DLNA item IDs, filesystem paths, Jellyfin IDs and playback URLs are distinct; never assume one can be transformed into another without a verified mapping.
- The adapter must identify titles deterministically where possible; ambiguous titles require a safe fallback or owner correction. Cache provenance, expiry and negative lookups; do not publish household media names or artwork captures.
- Fix sparse DLNA pagination using SOAP `NumberReturned` and `TotalMatches` independently of accepted-video count; handle filtered audio/unsupported entries, bounded retries and cancellation. Test mixed pages, multiple interfaces and multicast behaviour under VPN.
- Direct play remains preferred; format support and subtitles are hardware-specific. A compatible server remux/transcode route may be supported only when explicitly configured and proven.
- Restrict server browsing to owner-selected roots in the viewer and player, not just on Home. For Child use an authenticated restricted account where available; refuse unauthenticated DLNA as the Child pathway.
- NAS administrative discovery and a MediaServer response do not prove catalogue, auth, folder scope or video playback on any television.

### 27.4 Home, setup and reliability design rules

- Home remains a **quiet, deliberate selection of favourites**. Apps is the complete remote-friendly installed-app browser; installing/uninstalling is distinct from pinning/unpinning. Keep the one-dock visual intent, handle overflow without tiny tiles, and offer an obvious recovery route if every pin is removed.
- A focused item changes the atmosphere only using appropriately licensed and genuinely related imagery. Prefer the existing brand/neutral fallback over unrelated artwork. Cache and prefetch within strict memory limits; cancellation prevents stale focus backgrounds appearing after rapid remote navigation.
- Initial setup should be guided: discover by friendly name, explain why access is needed, verify a connection before saving and expose addresses/tokens only in Advanced. Never silently grant trusted HTTP consent, persist discovery results as credentials or create unsupported pairing APIs.
- Treat launch, loading, ready, empty, disconnected, unauthorised, unsupported, retrying and resumed states explicitly. Avoid infinite spinners; preserve previous navigation focus and user actions through lifecycle transitions.
- Do not interfere with native television controls, protected streaming-app screens, stock launchers or native Art Mode. Gallery idle art and Frame Art Mode remain separate.

### 27.5 Samsung early feasibility and platform truth

Before designing feature-complete Tizen parity, verify in this order on the actual Frame: developer host authorisation and SDB handshake; valid private certificates; signed WGT install/update; app startup and basic remote focus/Back; foreground AVPlay with representative media; standby/cold boot/reboot; switching to/from native Netflix/TV Plus; native Art Mode preservation; supported Ring event foreground launch and restoration. Record negative results explicitly. A hidden Tizen Web Application cannot be assumed to receive Ring events; any external Home Assistant coordinator requires a supported launch and first-frame acknowledgement path. Failure of a mandatory Ring/Art Mode journey is a **full-product release blocker**, not permission to fake the feature or alter native Samsung services.

### 27.6 Evidence register and multi-agent change control

Maintain one machine-readable acceptance register (CSV/JSON/Markdown table is acceptable) with these columns: `requirement_id`, `feature`, `owner_agent`, `branch_commit`, `build_hash`, `device_model`, `firmware`, `test_level`, `observed_result`, `evidence_path`, `status`, `severity`, `next_action`. Allowed status values: `not_started`, `implemented_unverified`, `test_passed_nonphysical`, `physical_verified`, `blocked`, `unsupported_verified` (v3.3 developer pack names, adopted 25 October 2026). Allowed `test_level` values: `none`, `unit`, `simulated`, `ci_build`, `pc_network`, `physical_tcl`, `physical_sony`, `physical_frame` *(changed in v3.3)*. The register is `ACCEPTANCE_REGISTER.csv`, the only place status is recorded. Only `physical_verified`, backed by evidence at a `physical_*` level on a named model, may support a hardware claim; source code and simulation do not imply cross-TV completion.

Assign one active agent per owned path/feature. Keep Android, Tizen, shared design tokens, service adapters and release automation in explicit branch ownership. Fetch before editing and pushing, stage only owned paths, scan for private data, avoid force-push and do not merge another agent's unreviewed work. Keep durable private release signing, same-author updates, rollback and immutable tags. The concise current decision index is [SPEC.md](SPEC.md); superseded proposal documents are kept unchanged in [archive/](archive/README.md) (done in v3.3); **never delete historical evidence**.

### 27.7 Release language and retained scope

- **Preview 0.5** means the bounded TCL viewing candidate described above. Passing automated checks is useful but insufficient. A poster-catalogue shortfall must be named, not concealed.
- **Household Android release** additionally requires Ring press/person interruption and recovery, verified Live TV isolation or explicit unavailable capability, authorised profile restrictions, sustained performance and TCL/Sony device-specific tests.
- **Full cross-TV release** additionally requires Samsung native WGT/AVPlay, TV Plus and Art Mode preservation, documented platform-specific launch/Back, Ring/Art Mode acceptance, and all applicable NT/AT journeys. Unsupported features must be agreed and disclosed rather than simulated.
- Preserve every earlier agreed feature and security restriction, including music, voice, gallery, matchday, Alexa, Sony Xbox, optional safe cleanup and accessibility. Lower scheduling priority does not revoke requirements.

### 27.8 v2.3 decision and change log

- Reprioritised the next build around one end-to-end TCL viewing journey while retaining parallel Samsung feasibility and household safety work.
- Corrected AT-56 and AT-59 to honour editable Home rather than the superseded fixed dock; clarified optional cleanup location under the current six-category Settings structure.
- Clarified that the restored Synology MediaServer is not proof of Jellyfin or a finished poster catalogue, and introduced a conditional metadata/catalogue acceptance gate.
- Added P05-01 to P05-10 for physical hardware verification, failure recovery, honest screenshots, signing and privacy.
- Added shared architecture boundaries, one evidence register, explicit multi-agent ownership and graduated preview/household/cross-TV release language.
- No source code, device configuration, GitHub branch, application signing material or existing release has been changed by this specification revision.


## 28. v2.4 remote-control ownership, seamless Home and system-button compatibility (P0)

### 28.1 Product outcome and honest platform boundary

**Owner requirement:** Unified TV must feel like the television's primary experience. Ordinary remote use must not unexpectedly expose the old TV launcher or strand the viewer in stock menus. A coherent experience includes Home, Back, OK, directional navigation, long presses, playback, Guide, Source/Input, Settings, voice, volume, channel, power, accessibility and device-specific buttons.

This is an **experience goal subject to OS ownership** rather than permission to hijack keys. Never promise interception of reserved keys, tamper with firmware, disable stock recovery, capture protected remote events, replace manufacturer Art Mode, or conceal unavoidable native hand-offs. If a button is reserved by the OS, document the resulting native destination and the shortest tested path back to Unified TV. Third-party streaming apps remain native apps and may have their own Back/Home behaviour.

**Modes are distinct and must be labelled clearly:**

1. **Android preview app `io.unifiedtv.preview` (always):** an ordinary app with no HOME entry, which stays installed alongside the launcher *(changed in v3.3)*. The physical Home button legitimately opens the original Android/Google TV Home. This is an expected, clearly disclosed limitation, **not a failed implementation**. Do not claim the old interface is gone in this mode.
2. **Android launcher app `io.unifiedtv.launcher` (owner opt-in, Sony and TCL independently):** its HOME activity-alias ships disabled; when the owner chooses **Make Unified TV the Home screen** in Settings it is enabled and Android's own Home selection decides *(changed in v3.3)*. Pressing Home from Unified TV or a third-party app should return to Unified TV, subject to manufacturer-reserved behaviour. Offer a guided, reversible setup and a tested stock-Home restoration path; never silently change the default or disable stock Home.
3. **Samsung Frame native Tizen application:** normal Web Application APIs do **not** confer Smart Hub/Home key ownership. Home, Source, voice, channel and other reserved keys may open Samsung native UI. Do not claim Unified TV replaces Smart Hub. Test which key events reach the app, the native hand-off, resume behaviour, and whether a documented supported launch mechanism can bring the user back. Preserve Frame Art Mode and Samsung TV Plus.

**Success criterion:** On an Android television in confirmed default-Home mode, a normal Home press returns to Unified TV rather than the stock launcher. On Samsung, any unavoidable Smart Hub hand-off is a **documented platform gap** with a tested return path, not a hidden defect or a fabricated workaround. Full parity cannot be signed off unless the owner explicitly accepts these differences.

### 28.2 Remote button behaviour matrix

| Button / gesture | Inside Unified TV | In third-party/native app | Platform rule / exception |
|---|---|---|---|
| D-pad Up/Down/Left/Right | Move focus predictably, never trap it; use directional semantics for Home, shelves and grids | App owns navigation | Key repeat and long holds must not lag or move focus after release |
| OK / Select | Activate focused item once; long OK opens the relevant context menu | App owns selection | Avoid duplicate click and long-press triggering both actions |
| Back / Return | Close overlay -> exit detail -> return to originating tile -> return to Unified Home; show a safe exit confirmation only when appropriate | Native app controls its own Back; when control returns, restore Unified TV state | At Unified Home, Back must **not unexpectedly open stock Home**; use an explicit exit or OS-mandated outcome and test it |
| Home | In Android default-Home mode return to Unified Home and restore meaningful focus; safe preview opens stock Home | Android default Home should bring user to Unified TV when supported | Tizen Home/Smart Hub reserved; cannot guarantee interception |
| Guide / Live TV | Open Unified TV's own guide or protected Live TV entry **only if the key is delivered and supported** | May open native tuner/guide | Never bypass NordVPN Live TV policy; native reserved Guide must be disclosed |
| Source / Input | Open a supported, intentional source picker or native input selector | Often OS-owned | Preserve HDMI/CEC and Sony Xbox input access; no unsupported key remapping |
| Settings / Menu | Open Unified Settings for app-owned controls; system settings only via deliberate explicit action | May be OS-owned | Do not obstruct Wi-Fi, accessibility, audio, recovery or updates |
| Play/Pause, FF/Rewind, Next/Previous | Control active Unified media player, with seek semantics and no duplicate events | App owns media keys | Preserve Bluetooth/CEC media transport and music behaviour |
| Volume, Mute | Leave to TV/OS/receiver hardware path; optional visual feedback only | Native volume remains functional | Never intercept or break eARC, Echo Studio or Bluetooth audio |
| Channel +/- and number keys | Use only if supported and meaningfully mapped to a live-TV/guide context | Native tuner may own them | No silent switch into restricted Live TV or legacy UI where preventable |
| Voice / microphone | Offer in-app voice entry where supported; show fallback | System assistant often owns hardware key | Do not claim capture of a reserved Google/Samsung voice key |
| Power, standby, wake | Persist focus/player state and recover safely on next launch | OS owns power | No power-key interception, forced wake or battery/standby regression |
| Colour keys, Info, dedicated app buttons | Optional mappings only when delivered, discoverable and not reserved | Manufacturer/streaming shortcuts may own them | No dependence on these buttons for core journeys |
| Samsung Art Mode / Frame-specific keys | Preserve native Art Mode and supported transitions | Samsung owns them | Do not simulate or override Art Mode |

### 28.3 Navigation, lifecycle and escape rules

- Define a **single navigation state machine** shared in concept across Android and Tizen: `UnifiedHome -> Apps / Library / Gallery / Settings -> Detail -> Player`, with overlays on top. Back unwinds one layer; Home (where app-owned) returns to UnifiedHome; preserve originating tile and scroll position.
- Distinguish **in-app Back** from **OS Home**, **exit app**, **input switching**, and **third-party app launch**. Never rely on an accidental OS fallback as the intended route. Provide a visible *Return to Unified TV* instruction or supported action where appropriate.
- When launching Netflix, Disney+, BBC, Samsung TV Plus or HDMI, save origin/focus and playback state. On returning to the Unified app, restore the relevant screen without flash, duplicate launch, stale backdrop or reset to the first tile. Do not assert that an ordinary app can override a third-party app's Back or Home key.
- A failed app launch, OS dialog, permission prompt, loss of network, standby/wake or remote disconnect must not trap the viewer. The standard remote must remain sufficient to recover; accessibility remotes and CEC controllers need regression checks.
- **Android default-Home onboarding:** explain the change and how to reverse it; check actual launcher eligibility; ask the owner to select Unified TV through supported OS UI; confirm selection; verify Home from within Unified TV, from Netflix, after standby and after reboot. If not supported or rejected, retain safe preview mode.
- **Stock recovery:** provide an in-app **Restore original TV Home** instruction/action where supported, plus an independent OS Settings route documented per tested model. Do not remove, disable or rename the manufacturer launcher. A broken/uninstalled Unified TV must not prevent normal TV use.
- **Samsung feasibility:** record key codes actually received by the WGT, keys consumed by Tizen, Smart Hub transitions, re-entry, app termination/resume, remote variations and Art Mode. Do not use unverified service-menu tricks, accessibility hijacks or privileged APIs to achieve key ownership.

### 28.4 Required hardware acceptance and evidence

| ID | Test | Pass / required evidence |
|---|---|---|
| RC-01 | TCL safe-preview Home press | Stock Home opens as disclosed; Unified TV can be relaunched; no settings changed silently |
| RC-02 | TCL opt-in default-Home selection | After owner authorisation, Home from Unified, Netflix and another app returns to Unified TV; record model, firmware, launcher selection and video |
| RC-03 | Sony opt-in default-Home selection | Repeat RC-02 on Sony independently; no TCL-only assumption |
| RC-04 | Back stack, overlays and edit mode | Back dismisses overlay, cancels unsaved edit, returns detail/player to origin and stops at a stable Unified Home; no surprise legacy UI while app owns navigation |
| RC-05 | Button coverage on each physical remote | Record each matrix button: `received`, `OS-reserved`, `third-party-owned`, `unsupported` or `not-present`; capture actual destination, not guessed mappings |
| RC-06 | Streaming-app round trip | Launch Netflix/Disney+/BBC, use Home/Back as supported, return to Unified TV with correct focus and no stale hero image |
| RC-07 | Power and recovery | Cold boot, standby/wake, app crash/uninstall and network outage preserve usable remote, sound, stock recovery and reliable re-entry |
| RC-08 | Input, guide and Live TV | HDMI/CEC, native tuner/Guide and Samsung TV Plus work without circumventing VPN restrictions or exposing prohibited viewing routes |
| RC-09 | Samsung Frame reserved-key feasibility | On actual Frame WGT, capture Home/Back/Guide/Source/voice/Art Mode outcomes and verified return path; mark non-overridable keys as platform limits |
| RC-10 | Restore original Home | On TCL and Sony after opting in, restore manufacturer Home without PC, root, ADB or a working Unified TV app; record full steps and pass result |
| RC-11 | Remote responsiveness and accessibility | Fast D-pad repeat, long OK, held Back, CEC and accessible input produce one predictable action without lost focus or repeated activations |

**Release gating:** RC-01, RC-04, RC-05, RC-06, RC-07 and RC-11 apply to Preview 0.5 where testable; RC-02 and RC-03 apply to the launcher app `io.unifiedtv.launcher` and are required **before calling it a full launcher replacement**; they do not apply to the preview app. RC-09 is mandatory before any Samsung seamless-interface claim. RC-10 is mandatory before recommending opt-in default-Home mode. No platform's test result can substitute for another's.

### 28.5 v2.4 change log

- Added explicit owner requirement to minimise unintended exposure to the old TV interface when using ordinary remote controls.
- Defined opt-in Android default-Home mode separately from the intentionally safe Preview 0.5 and the Tizen Smart Hub limitation.
- Added per-button behaviour matrix, navigation/return lifecycle, manufacturer recovery and 11 hardware acceptance tests.
- Preserved native volume, power, voice, HDMI/CEC, protected Live TV, Samsung TV Plus and Frame Art Mode.
- No application code, launcher defaults, device settings or repository branches were changed by this specification revision.

## 29. v2.5 household-first remote ownership and always-return contract (P0, owner release blocker)

### 29.1 Non-negotiable household outcome

The three owner's actual televisions and their supplied physical remotes are the release targets, not representative device categories. The product must be usable every day by household members without knowing that a separate stock launcher exists. The expected Android behaviour is **Home always returns to Unified TV**, whether pressed from the Unified app, Netflix, BBC iPlayer, Disney+, a media player or after waking the TV. **Back never accidentally finishes the Unified Home activity**. The remote must remain able to operate volume, mute, power, inputs, audio and emergency system recovery.

This is an **acceptance requirement**, not a claim about the current APK. No device may be labelled 'always returns' without actual hardware evidence after boot, standby, third-party app round trips and repeated remote key use. A Samsung reserved-key limit must be surfaced as a blocker to full seamless parity, not redefined as a pass.

**Owner preference overrides generic repository defaults:** household-specific tested mappings, profiles and instructions have first priority. Public GitHub users can provide their own adapter and button map. Do not hold up the owner's three-device mapping for hypothetical remotes, and do not hardcode private network identifiers or personal credentials in public source.

### 29.2 Three actual television targets

| Household target | Confirmed facts | Required remote ownership and return behaviour | Information not yet evidenced / must be captured on TV |
|---|---|---|---|
| **TCL living-room TV** | TCL 55-inch; Android TV **11**; Unified TV preview installed and owner has **already pressed a remote button and been dumped into stock UI**, unable to find the app | **First priority:** install the separate launcher app `io.unifiedtv.launcher` (HOME activity-alias shipped disabled); when the owner chooses **Make Unified TV the Home screen**, enable it and let Android's own Home selection decide *(changed in v3.3)*, then test physical Home from Unified TV, Netflix, Disney+, BBC and standby. Back at Unified Home stays there. Provide a labelled 'Open Unified TV' recovery entry in Apps and an owner-tested stock launcher restore path | Exact TCL model, firmware, remote part number and photographed key layout; which specific button caused the observed escape; whether firmware exposes and persists default Home selection |
| **Sony BRAVIA OLED** | Sony BRAVIA **65-inch OLED**, Google TV; Xbox on **HDMI 3** with eARC consideration | Same Android HOME default selection **independently verified on Sony**. Home returns to Unified TV from every tested streaming app; Source/Input intentionally opens native inputs, Xbox action selects the verified HDMI 3 route without breaking eARC. Back and long-Back remain predictable. Protect OLED idle behaviour | Exact Sony model/firmware and physical remote model/key layout; launcher chooser availability; manufacturer shortcut and TV/Guide key behaviour |
| **Samsung The Frame** | **QE50LS03AAUXXU**, 50-inch, Tizen **2310.0**; Samsung Smart Remote with **123 on-screen number keypad** rather than physical number keys; preserve Art Mode and Samsung TV Plus | **Native Tizen Web App** only. Test Back/Return inside Unified TV; physical Home/Smart Hub, Source, 123 keypad, volume, channel, microphone, power and Art Mode transitions. Native Home/Smart Hub is system-owned: **do not promise that Home can be redirected to Unified TV**. Implement and video-verify the fastest supported app relaunch from Smart Hub; restore previous Unified focus on re-entry when possible. If this still exposes Smart Hub, record as **owner experience blocker / platform limitation** | Exact Smart Remote model and key arrangement, actual WGT installation, Smart Hub pinning/launch options, supported resume behaviour and all reserved-key outcomes |

Do not invent missing Sony/TCL remote button labels or assert remote model numbers without a photograph, manufacturer part number or device-level key log. Record the exact button the owner used to escape on TCL as a reproducible regression test. The known Samsung '123' button must be tested as the on-screen keypad, **not** as a physical digit keypad.

### 29.3 Android implementation contract: true default Home, not a fullscreen app

1. Build **two separately installed apps from one codebase as Gradle product flavours** *(changed in v3.3)*: `preview` (`io.unifiedtv.preview`) has **no HOME entry at all**, keeps its identity, stays installed and is never uninstalled; `launcher` (`io.unifiedtv.launcher`) declares a HOME activity-alias handling `ACTION_MAIN` plus `CATEGORY_HOME` and `CATEGORY_DEFAULT` that **ships disabled**, is enabled only when the owner chooses **Make Unified TV the Home screen** in Settings, and is withdrawn again if the owner does not choose it or reverses the choice. The launcher is signed with the owner's durable key from its first release. Confirm intent resolution on each television rather than assuming a normal `LEANBACK_LAUNCHER` entry is enough.
2. Present an on-TV owner-controlled onboarding flow explaining that pressing Home will now open Unified TV. Launch the supported Home selection/settings UI where available; **never silently assign the default, require root, disable the factory launcher or run persistent ADB hacks**. If OEM firmware forbids default selection, record a hard platform blocker and investigate supported alternatives separately.
3. Ensure Home renders an idempotent root screen and Back at root is consumed/handled without `finish()`, `finishAffinity()` or an unintentional fallback to stock Home. Overlays and child pages unwind in deterministic order. A long Back or repeated rapid Back must not escape unexpectedly.
4. Persist pinned apps, selection and library state. Recover from process death, cold boot and standby without exposing setup or a blank screen. The launcher must work offline and must not depend on the NAS, VPN, HA or a network request to render Home.
5. Launch external apps through verified platform intents. On returning via physical Home, restore Unified TV promptly and never show a second 'legacy Home' step. Do not claim to intercept Home while a third-party app has focus; **the OS routes it to the chosen default Home**.
6. Retain an independent emergency route to factory Home through supported OS Settings, even if Unified TV fails to start. Verify it on each device. Never disable or uninstall system packages or interfere with volume, Wi-Fi, input selection, accessibility or firmware updates.
7. Use hardware key logging only in owner-authorised debug builds to identify app-delivered events. Document OS-reserved keys and dedicated app buttons, which may bypass the launcher entirely. Do not use accessibility-service key hijacking or unsupported remapping to pretend they can be controlled.

### 29.4 Samsung Frame contract: supported limits are not negotiable

The 2021 Frame/Tizen 2310.0 cannot be treated as an Android default launcher. An ordinary Tizen Web Application is not entitled to intercept or replace the system Home/Smart Hub key. See Section 31 for a newly documented, unsupported native Home reassignment experiment on a DIFFERENT Samsung Frame; this is a feasibility lead, not verified compatibility for this household. Test native application lifecycle and supported Samsung launcher entry points after every Home, Back, Source, channel, standby and Art Mode journey. Never disable Smart Hub, modify firmware, enter the service menu or damage Art Mode to conceal the old interface. If the owner's requirement is literally that **every remote button must keep Unified TV as the visible system interface**, classify Samsung as **not achievable through the ordinary native Web App architecture alone**. A guarded, unsupported native reassignment experiment may be considered under Section 31 only after device-specific feasibility and recovery gates; success is not guaranteed. A convenient app shortcut is mitigation, not parity.

### 29.5 Mandatory owner-device remote test matrix

On each actual remote, record its photo/model where possible, button legend, short/long press, starting context, actual destination, whether app receives a key event, return route, repeatability, and evidence video. Include every physical button and the Samsung 123 overlay. Minimum journeys:

| ID | Television | Test | Required result |
|---|---|---|---|
| HRC-01 | TCL | Owner's **previously problematic button** from Unified Home, Apps, Settings and player | No surprise legacy launcher when an app-controlled key is used; reserved key recorded explicitly; exact reproduction fixed |
| HRC-02 | TCL | Home from Unified, Netflix, Disney+, BBC, another app | Unified Home appears on first Home press with no manual Apps hunt **after default Home has been selected** |
| HRC-03 | TCL | Repeated Back/long Back from every Unified screen | Never exits the Unified root unexpectedly |
| HRC-04 | TCL | Power cycle, standby, network loss, app crash | Remote recovers to a usable Unified Home or an independently usable stock fallback; no lockout |
| HRC-05 | Sony | Repeat HRC-02/03/04 on Sony's own remote and firmware | Same independently proven Home ownership and Back behaviour |
| HRC-06 | Sony | Input/Source and Xbox on HDMI 3, volume/eARC | Xbox route works; native input and sound controls remain usable; Home returns to Unified where OS allows |
| HRC-07 | Samsung Frame | Home/Smart Hub from WGT and Netflix; app re-entry | Exact native destination and fastest supported return are filmed; **not** recorded as seamless replacement if Smart Hub appears |
| HRC-08 | Samsung Frame | 123 keypad, Return, Source, channel, power, Art Mode | Correct device-specific behaviour and preservation of native Art Mode/TV Plus |
| HRC-09 | All three | Every physical button including long presses and dedicated app shortcuts | One device-specific signed-off key map; any unavoidable system hand-off labelled and owner-approved |
| HRC-10 | Android TVs | Restore factory Home after default selection, including if Unified crashes | Fully tested supported OS recovery with physical remote, no PC or ADB required |
| HRC-11 | All three | 20 successive round trips across apps and standby/wake | No stranded state, lost focus or unexpected return to stock interface beyond documented reserved-key limits |

**Release gate:** TCL HRC-01 to HRC-04 and HRC-10 are **P0 before the next owner daily-use build**. Sony HRC-05/06/10 are **P0 before Sony daily-use release**. Samsung HRC-07/08 and a written owner decision on Smart Hub limitations are **P0 before Samsung is called a seamless household deployment**. Mark `physical_verified` only after observed tests on exact TV, firmware, APK/WGT hash and physical remote. No emulator-only or automated-only sign-off.

### 29.6 Immediate recovery and development sequencing

- **Today, TCL:** from stock Home find Unified TV in Apps/See all apps or Settings > Apps and open it. Record the current package ID and launcher intent. This is recovery only, **not a permanent fix**.
- **Next build:** implement and verify HOME role eligibility in the launcher app `io.unifiedtv.launcher`; provide owner-directed default Home setup and physical Home/Back tests before any additional visual polish or integrations.
- **Then Sony:** repeat independently with its own remote, default Home and HDMI 3/eARC checks.
- **Samsung in parallel:** obtain a signed WGT running on Frame; test real remote reserved keys and Smart Hub re-entry. Surface impossibility rather than inventing interception.
- Keep the original factory Home installed and a documented reversible fallback on Android throughout.

### 29.7 v2.5 change log

- Elevated three household physical remotes above generic repository/device support.
- Added a concrete TCL regression for the owner's real incident of accidentally leaving Unified TV.
- Required default Android HOME selection and remote-based recovery for TCL and Sony before daily-use release.
- Made Samsung Frame QE50LS03AAUXXU / Tizen 2310.0 / 123-keypad behaviour explicit without claiming impossible Smart Hub interception.
- Added eleven device-specific hardware tests and clear release blockers; no source code or TV settings changed by this document update.


## 30. v2.6 discreet original-interface escape hatch (owner decision, P0)

### 30.1 Intent and discoverability

Unified TV remains the normal day-to-day interface on the owner's three televisions wherever the OS permits. Add a deliberately unobtrusive **Settings > TV & performance > Advanced > Open original TV interface** action. On Android it lives in the launcher app `io.unifiedtv.launcher`; the preview app never holds the Home role *(changed in v3.3)*. This must appear as a small secondary text row at the bottom of the advanced settings panel, not a large tile, hero button, Home shortcut, prominent Settings category or always-visible remote hint. The wording must be understandable without developer knowledge; do not call it 'stock launcher', 'intent', 'Tizen' or 'HOME role' in the viewing UI. Make the action remote-focusable, legible, and screen-reader accessible despite its visually low prominence. It is intentionally discoverable in Settings but not easy to trigger accidentally.

Selecting the row opens a confirmation sheet with the two meanings distinguished clearly:

- **Open original interface for now** (primary option): launch the manufacturer's interface through a supported mechanism **without clearing Unified TV's default Home selection**. Explain that pressing Home should return to Unified TV on Android when Unified is the chosen default; on Samsung, Home is owned by Smart Hub and may stay there. If the platform does not support temporarily opening the factory Home independently, disable this option with a plain-language explanation; do not pretend it works or silently change defaults.
- **Make original interface the default** (advanced, Android only): explicitly guide the owner to the system-supported default Home app selection or reset flow. Show a second clear confirmation explaining that future Home presses will go to the original interface until the owner selects Unified TV again. Never change the default silently, remove the manufacturer's launcher, or use root/ADB to force the choice.
- **Cancel**: return to the same Settings row with focus preserved and no state changes.

If household profile permissions are active, only the owner/adult profile can invoke the advanced option or change the default Home. The PIN is an in-app guard against accidental use, not a claim that it can restrict OS Home, dedicated app keys or manufacturer menus.

### 30.2 Platform-specific implementation

| Owner TV | Temporary original-interface action | Restore original as default | Return and limitations |
|---|---|---|---|
| TCL 55-inch Android TV 11 | Discover and verify the actual factory Home component and an OS-supported launch route on this firmware. Do not hardcode a generic package or assume a direct Home activity launch is permitted. If blocked, state unavailable and offer the explicit system Home-selection route instead. | Use the native default-app/Home chooser or supported Settings flow with owner confirmation; never disable stock Home. | With Unified selected as default, physical Home should return to Unified; test from stock interface and other apps. Reboot and crash recovery must remain safe. |
| Sony BRAVIA 65-inch Google TV | Independently resolve and test Sony/Google TV factory Home launch behaviour on the actual Sony. Do not assume TCL component names or Settings paths. | Native supported Home selection/settings with owner confirmation and rollback. | Home should return to Unified only while selected as default; preserve native inputs, audio and HDMI 3 Xbox behaviour. |
| Samsung Frame QE50LS03AAUXXU, Tizen 2310.0 | Use a supported native route to Smart Hub/TV UI if available; otherwise explain that the physical Home key opens Smart Hub. Never intercept reserved Home or disable Art Mode. | Not applicable through ordinary Tizen Web App APIs. Section 31 records an unsupported native Home reassignment possibility for a different model/firmware; do not display a misleading supported 'set default' choice. | The normal supported route remains Smart Hub; if the separately authorised Section 31 experiment proves compatible, record the actual reassigned Home destination and tested native Smart Hub escape. Never claim compatibility from another model. |

The public repository may offer additional platform adapters, but the owner's three TVs and remotes take priority. No private device identifiers or network details belong in public source.

### 30.3 Interaction and resilience rules

1. Do not expose 'Exit Unified TV' as an ordinary root Back action. Back at Unified Home remains on Home; the Settings escape hatch is the deliberate exit route.
2. The first choice must be a temporary **visit**, not a destructive configuration change. Never imply a temporary visit is possible if testing proves it is not.
3. Do not close or disable Unified TV's launcher component when temporarily opening another interface. Preserve the last screen and selection for return.
4. A deliberate switch of default Home must be reversible with the physical remote even if Unified TV fails to start. Provide a short on-TV recovery explanation before proceeding.
5. Any OS Settings or chooser screens are native UI, not visually restyled copies; show the actual tested device-specific route.
6. In child mode, keep the escape hatch absent or adult-locked according to profile policy, while acknowledging that OS-reserved keys remain outside the application's control.
7. Maintain a developer-visible diagnostic state recording which manufacturer interface route was actually verified on each TV; do not put debug data in the viewing UI.

### 30.4 Acceptance tests

| ID | Device | Test | Pass condition |
|---|---|---|---|
| OI-01 | All three | Navigate Settings using physical remote | Small 'Open original TV interface' row is reachable at bottom of Advanced; not visible on Home or prominent in Settings. |
| OI-02 | All three | Open action and cancel | Clear choices, correct focus and no launcher/default change after Cancel. |
| OI-03 | TCL | Choose temporary visit | Opens actual TCL interface only if supported; Unified remains default Home; Home returns to Unified on first press. Otherwise option is honestly unavailable. |
| OI-04 | Sony | Choose temporary visit | Independently verified Sony behaviour, with no false TCL assumptions. |
| OI-05 | TCL and Sony | Choose permanent original Home | Explicit confirmation and supported OS selection; factory Home becomes default only after owner action; restore Unified using remote-only steps. |
| OI-06 | Samsung Frame | Choose original interface | Opens Smart Hub by supported means or clearly instructs physical Home; no 'make default' option; Art Mode and TV Plus remain intact. |
| OI-07 | All three | Child profile and rapid Back/OK presses | No accidental permanent default change; in-app adult gate enforced where configured; OS limits stated honestly. |
| OI-08 | TCL and Sony | Power cycle, crash and restore | Both launcher choices remain recoverable without PC, ADB, root or disabled stock packages. |

**Release gate:** OI-01/02/03/05/07/08 are P0 for TCL owner daily-use deployment; repeat OI-01/02/04/05/07/08 before Sony daily-use deployment. OI-06 is required for Frame. Record actual model, firmware, physical remote, build hash and video of each verified route. No simulated-only sign-off.

### 30.5 v2.6 change log

- Added a deliberately small, secondary escape hatch in Advanced Settings to reach the original manufacturer interface.
- Distinguished temporary viewing of the original UI from permanently changing the Android default Home.
- Added exact household platform contracts and eight acceptance tests; retained Samsung's reserved-key limitations.
- Preserved the owner's goal that Unified TV is the normal experience, and maintained manufacturer Home recovery for safety.
- Specification-only update: no APK/WGT, remote mapping or TV configuration has been changed.


## 31. v2.7 Samsung Frame native Home research: CleanFrame evidence and owner-specific feasibility (P0 research gate)

### 31.1 Why this section exists

The owner found **CleanFrame** (https://github.com/nthnclrk/CleanFrame), an MIT-licensed personal launcher for Samsung The Frame, and its companion video (https://www.youtube.com/watch?v=sCfbcoQgeWo). Its author documents an **actual physical Home-button return from Netflix to CleanFrame** on one Samsung Frame. This materially changes the feasibility assessment: a Samsung Home reassignment has been demonstrated on specific hardware through **unsupported native mechanisms**, even though ordinary Tizen Web Apps cannot claim the Home button. It **does not** establish that the same technique works on the owner's older Samsung Frame. The YouTube video's comments and AI-tool attribution have **not been independently verified**; rely on the repository's source and recorded device tests for engineering decisions.

Primary sources (reviewed 24 October 2026):

- Project and caveats: https://github.com/nthnclrk/CleanFrame
- Reference TV setup, signing, test and recovery stages: https://github.com/nthnclrk/CleanFrame/blob/main/docs/agent-tv-setup-guide.md
- Native helper contract and caveats: https://github.com/nthnclrk/CleanFrame/blob/main/scripts/native-home/README.md
- Device evidence: https://github.com/nthnclrk/CleanFrame/blob/main/docs/tv-status.md
- Native helper source: https://github.com/nthnclrk/CleanFrame/tree/main/scripts/native-home

### 31.2 Precisely what is established, and what is not

| Item | CleanFrame reference (reported by author) | Owner's Samsung Frame / implication |
|---|---|---|
| Hardware | Samsung 55-inch Frame **QA55LS03DAWXXY / LS03D** (2024 generation) | **QE50LS03AAUXXU / LS03A** (2021 generation): different hardware |
| Software | Firmware **T-PTMDUABC-0090-2130.0**; developer capability **Tizen 9.0, ARMv7, 32-bit** | Firmware **T-NKM2DEUC-2310.0** as recorded by owner; **actual Tizen API/runtime and CPU architecture must be read from this TV**, not inferred from version number |
| Signed app | Installed and opened; Netflix and YouTube launch reported | Unified TV signed WGT install not yet verified on owner's Frame |
| Home | Physical Home from Netflix returned to CleanFrame on reference TV | **Unverified**; ordinary Tizen Web App key handling is insufficient |
| Reversal | Author reports native query, apply, restore and reapply on reference firmware | No known independently tested restore on owner's Frame |
| Startup | Normal power-on after another app remained tentative | Must be tested independently: power-on, standby and cold restart differ |
| Art Mode and native Smart Hub escape | Corrected native escape and Art Mode still required physical confirmation in reference guide | Preserve native Art Mode as a release blocker; cannot assume parity |

Do **not** convert author-reported results into owner-device `physical_verified`. A repo source file, successful build or simulator test is not physical evidence.

### 31.3 Engineering explanation (research, not a supported public API)

CleanFrame's optional Home control is **separate from its JavaScript launcher and from ordinary remote key event handlers**. Its documentation describes a one-shot temporary **.NET native helper** executed through a developer SDB connection using an **undocumented package-name command-execution workaround** as Samsung's SDK user **UID 901**. The helper is not a persistent service, does not request root and does not change security policy in the reference setup. It queries and, only when authorised, attempts to change native Home routing and associated `vconf` settings, then restores original values on request. The reference source uses guarded query/apply/restore behaviour and firmware/app-identity checks. This is **not an official Samsung SDK launcher replacement feature** and can break with firmware changes or fail to recover.

Engineering agent: inspect `scripts/native-home/README.md`, `Control.cs`, the setup guide and recorded status **before** proposing any owner-device implementation. Do not blindly copy firmware guards, helper binaries, app IDs, package identifiers, signing credentials, native settings values or commands from CleanFrame. Do not treat the video or an AI model's success claim as a device capability test. Do not bypass firmware checks to force the reference helper to run.

### 31.4 Mandatory staged approach for this owner's Frame

**Stage S1 — Read-only inventory and developer access (authorised normal development):** Record model, complete firmware, TV IP and PC IP privately, real Tizen runtime/CPU architecture, Samsung SDK/SDB versions, installed apps and native capabilities. Use Samsung's official developer-mode workflow and SDB port **26101**. Confirm a real capability response, not just an open port. Do not alter Home/startup. The owner's previously recorded TV and PC addresses are kept only in private local notes and must be rechecked rather than assumed current *(changed in v3.3)*: the addresses were removed from the public specification. Never publish network identifiers or DUIDs.

**Stage S2 — Signed Unified TV application:** Generate this project's own package and application IDs; use owner's Samsung developer account, author certificate, distributor certificate including TV DUID and official signed `.wgt` packaging. Install, launch and test navigation without any Home modification. Record signing identity and a safe update path. Do not reuse CleanFrame's author identity or signed binary.

**Stage S3 — Read-only native feasibility:** Review whether this exact firmware exposes equivalent native operations, permissions and runtime dependencies. Determine whether the original Home/startup configuration can be read without writes and whether a *separate, independently usable recovery path* exists if the developer connection or helper fails. Document unknowns and differences from the LS03D reference. Even query/inspect methods may stage files on the TV; obtain permission for that operation. Do not run an experimental command transport simply to see what happens.

**Stage S4 — Recovery baseline and risk review:** Save exact original values and their *presence/absence* state in private local recovery records, along with firmware, build hash, application ID and verified recovery tooling. Keep an offline copy outside the public repo. Prepare and validate restoration as far as safely possible **before** considering any reassignment. Establish a fallback independent of the same undocumented mechanism; if that cannot be demonstrated, **do not apply the Home reassignment**. Warn that a backup alone does not guarantee recovery, and that firmware updates may break both modification and rollback. No root, service-menu changes, firmware downgrade, factory reset or security-policy modifications are authorised by this specification.

**Stage S5 — Explicit owner go/no-go:** Present a short device-specific feasibility report: what is verified, what remains unknown, what could break (Home, startup, Smart Hub, Art Mode), expected reversibility, and a recovery plan. A separate explicit owner approval is required before any native Home/startup write. No AI agent may interpret approval to edit this specification as approval to modify the television. Under OD-05 only the written proposal (gate G5P, covering S1–S5) is authorised; S6 and S7 need a later, separate explicit approval.

**Stage S6 — Guarded experiment only if S1–S5 pass:** In a supervised maintenance window, use a device-specific implementation with strict firmware, user, installed-app, original-value and target-ID guards. Apply once, read back state, check physical Home, then restore exact original state and verify the physical Samsung Home destination. Reapply only with renewed owner approval after successful rollback. Stop on ambiguous output; never loop or force repeated writes. Never uninstall or change the application identity while native Home routing still targets it: restore original routing first.

**Stage S7 — Household physical verification:** Test actual Samsung Smart Remote (including its **123** numeric overlay), Home from Netflix/YouTube/TV Plus/Unified, Back, Source, channel controls, Settings escape to Smart Hub, Art Mode, short/long Power, standby/wake, cold restart, app crash and firmware-change recovery. Video each pass and log exact firmware and build. If any core behaviour fails or cannot be independently recovered, leave the original Samsung Home configuration in place and mark native reassignment **unsupported on this owner device**.

### 31.5 Relationship to the small 'Open original TV interface' option (Section 30)

- The small **Settings > TV & performance > Advanced > Open original TV interface** entry remains mandatory.
- **Before** native reassignment, use a supported Smart Hub route or explain the physical Home button's native destination; do not pretend a temporary visit is implemented if not possible.
- **If** reassignment is independently proven on this Frame, this option must provide a tested deliberate escape to **Samsung Smart Hub**, and the physical Home button must return to Unified TV afterwards. The developer must verify the exact escape target rather than assume CleanFrame's reference route works.
- Restoring the original Home assignment permanently is a separate advanced, explicitly confirmed operation and must be possible through the verified recovery plan. Do not describe an unsupported native reassignment as an official Samsung 'default launcher' setting.
- Preserve Samsung Art Mode and the ability to operate the TV normally even if Unified TV is unavailable. No remote trap or unsafe lockout is acceptable.

### 31.6 Additional Samsung acceptance tests (new; not yet passed)

| ID | Gate | Pass condition |
|---|---|---|
| CF-01 | S1 | Verified actual owner firmware, runtime, architecture, SDB capability output and Smart Remote key inventory |
| CF-02 | S2 | Signed Unified TV WGT installed and opened using owner's app identity and certificates |
| CF-03 | S3 | Documented read-only feasibility and native API/permission differences against CleanFrame's LS03D reference |
| CF-04 | S4 | Original values, including absent settings, preserved; recovery independently demonstrable before any write |
| CF-05 | S5 | Owner separately authorises a specific, reviewed experiment after a clear risk report |
| CF-06 | S6 | If attempted, one guarded apply, readback, exact restore and observed original Home all succeed before any reapply |
| CF-07 | S7 | Home from Netflix and at least one other external app reliably returns to Unified TV on owner's Frame |
| CF-08 | S7 | Explicit Settings escape opens native Smart Hub and Home returns to Unified TV without changing the saved routing |
| CF-09 | S7 | Native Art Mode, standby/wake, power-on and cold restart work on physical Frame; each recorded separately |
| CF-10 | S7 | Crash, update and uninstall/recovery procedure prevents Home targeting a missing app; firmware changes trigger revalidation |

**Status as of v2.7:** All CF tests **not yet verified on owner's Frame**. CleanFrame demonstrates a **promising unsupported technique on another Samsung model**; it does not close this household's Samsung release gate. If an independently safe recovery path cannot be established, **do not attempt reassignment**. For the TCL and Sony, continue with the separately specified supported Android default-HOME approach; do not import Samsung's native helper.

### 31.7 v2.7 change log

- Added verified CleanFrame repository references and the author's documented physical Home-from-Netflix result.
- Corrected the previous blanket implication that native Samsung Home reassignment is categorically impossible, while retaining that ordinary Tizen Web Apps cannot do it through supported APIs.
- Distinguished the developer's 2024 LS03D / Tizen 9 reference from the owner's 2021 LS03A / firmware 2310.0.
- Defined strict discovery, recovery, owner-consent, physical remote and Art Mode release gates.
- Connected any future native reassignment to the existing discreet original-interface escape hatch.
- Specification-only change: no TV configuration, repository code, certificates or binaries modified.


---

## 32. v2.8 — Dynamic ambient mesh: animated radial gradients, optical-depth glass and app-focused light

**Status:** Product/visual requirement approved for development; six palette mockups (SmartTube, Netflix, Disney+, BBC, Live TV, Music), supplied in chat on 25 October 2026 as rough UX ideas and kept outside Git because they contain third-party logos, define the intended *look* (see [REFERENCES.md](design/REFERENCES.md)), not evidence that it has been implemented or meets performance targets. **Applies to all three owner TVs** (TCL Android TV 11, Sony BRAVIA Google TV, Samsung Frame 2021 native Tizen). Preserve Sections 27–31, especially remote/Home ownership and the discreet original-interface escape. This section supersedes any earlier suggestion that the active Home dock should use only static photographic or baked-gradient backdrops; idle Art Mode and photographic galleries remain separate.

### 32.1 Desired experience and motion model

When the focused item changes across the six-app Home dock, the full-screen background becomes a **dynamic ambient mesh**: overlapping, soft-edged radial light fields in the focused app's palette over a deep dark base. It should resemble coloured LED bias lighting shining through a frosted-glass wall, **not** a flat brand-colour fill, hard-edged blob, image slideshow, or full-screen photograph. Preserve legibility of the time, VPN state, microphone and Settings controls, app title, focus indicator, labels, bottom hints and any alerts.

**Two different motions are required:**

1. **Focus-to-focus colour transition:** Start when D-pad focus changes. Crossfade between the outgoing and incoming complete mesh states over **400 ms** (target), with an ease-in-out curve; no flash, blank frame, abrupt palette jump or lagging title. Title, selected tile halo and background must correspond to the **same current focus**. The selected app does **not** launch merely because focus changed; OK remains the launch action.
2. **Subtle ambient drift while focused:** The radial-light centres may slowly wander, breathe or change scale within restrained bounds so the mesh feels alive. Use a long, non-repeating-feeling cycle, initially **20–40 seconds** per motion path; keep amplitudes small (e.g. centre shifts roughly 2–5% of viewport size and/or scale 1.00–1.06), with no perceptible bobbing, sudden resets or distracting movement behind text. Drift is independent of focus and never restarts on a focus change. These values are starting design targets, adjustable after real-TV observation. Movement must remain low contrast and slow enough to feel atmospheric rather than animated wallpaper.

**Fast navigation:** When the user taps across multiple icons quickly, always prioritise the *latest* focus. Cancel or retarget superseded transitions; do not queue six 400 ms animations or allow the background to display an earlier app after the selection has moved on. Focus and key handling must remain responsive during all animation. No ambient motion should trigger an app launch, alter remote focus or interfere with the back/Home contract.

### 32.2 Six canonical palette/focus states

Each palette uses at least two soft radial light fields and a dark vignette/base. Positions are **approximate art direction**, not rigid pixel coordinates; tune on the real TVs while keeping the supplied references recognisable. Use muted falloff at the corners and behind the top header. No single saturated opaque fill should cover the screen.

| Focus | Palette | Radial-light arrangement and target mood | Visual reference |
|---|---|---|---|
| **SmartTube** | Crimson red, dark charcoal | Strong soft red glow from **bottom-left**, diffuse secondary red nearer the centre-left; fades into deep grey/near-black upper and outer edges. Crisp white header; cinematic red light behind glass. | Chat mockup, not in Git (pending owner references) |
| **Netflix** | Blood red, true black | Low-key dark red **central** glow with a heavy cinematic vignette and near-black extreme corners; deliberately the darkest mesh. | Chat mockup, not in Git (pending owner references) |
| **Disney+** | Navy, indigo, cyan | Broad luminous blue/indigo field; restrained cyan bloom **behind/below the dock** illuminating upward, dark navy perimeter. Night-sky feel without stars or visible texture. | Chat mockup, not in Git (pending owner references) |
| **BBC** | Rich magenta, deep pink | Diffuse pink/magenta wash, saturated yet dark enough for white text; aggressive *visual softness* and no visible gradient banding. Do not treat every BBC brand/service as requiring the same colour outside this dock. | Chat mockup, not in Git (pending owner references) |
| **Live TV** | Deep violet, indigo | Restrained purple/violet pools, dark edges and sufficient calm negative space for VPN/connection sheets and locked-state messages. | Chat mockup, not in Git (pending owner references) |
| **Music** | Peach, coral, vibrant pink | Warmest and most energetic **multi-point** mesh; peach and coral drift into luminous pink while dark outer edges remain. Suggest flowing light, not flashing or pulsing to a beat by default. | Chat mockup, not in Git (pending owner references) |

**Visual reference rule:** these six mockups are *rough UX ideas only*; the named `references/01-smarttube.jpg`… files were never committed *(changed in v3.3)*. The shipped active dock backgrounds must be produced procedurally at runtime, never by switching between images. Keep each app's palette in a declarative theme configuration, separate from navigation and from the icon asset; user-customised dock order must not break palette assignment. For arbitrary user-added apps, provide a neutral, accessible fallback palette, optionally a curated app palette if known. Never infer arbitrary saturated colours from an icon without contrast checks.

### 32.3 Layer composition, glass and focus treatment

Draw in this order, back to front:

1. Opaque deep charcoal/navy/black base (theme dependent), never bright full-screen flat colour.
2. Two to four **radial light layers** with large feathered falloff and subtle animated positions, each a small pre-rendered light sprite moved and scaled as a GPU-composited layer, never a continuously repainted full-screen gradient *(changed in v3.3)*; dark vignette layer to protect corners and header contrast.
3. Optional *very subtle* dithering/noise where supported to reduce 8-bit colour banding; avoid a visible grain overlay or excessive texture.
4. Frosted-glass dock surface: translucent neutral tint, fine light top rim, soft edge and natural shadow; background hue should appear to bleed through it without compromising icon contrast.
5. App tiles and labels. Selected tile gains a slight lift/scale, crisp luminous outline, soft coloured halo and fully readable label; unfocused tiles remain legible, not washed out or blanked. The app title above the dock updates with focus.
6. Header controls, footer hints and all alerts remain sharp, with no blur applied to text, icons or focus outlines.

The supplied mockups are not pixel-perfect implementation templates: maintain existing six-app dock semantics, editable slots, safe-area margins, remote-focus visibility, high-contrast typography and responsive layouts. The glass surface must not cause a large continuous screen-space blur cost merely to achieve the visual effect.

### 32.4 Platform-specific implementation requirements (do not assume feature parity)

**Samsung Frame QE50LS03AAUXXU — native Tizen Web App:** Implement the mesh as layered **CSS `radial-gradient()` backgrounds** on absolutely positioned, clipped elements. For the 400 ms palette change, preferably use two mesh containers and animate **opacity** between them; CSS does not universally interpolate gradient stops or gradient position smoothly on older Tizen browsers. Ambient drift should primarily animate **`transform: translate3d(...) scale(...)`** on a small number of composited layers rather than continuously rewriting full-screen gradient strings. Avoid large continuously animated CSS `filter: blur(...)` / `backdrop-filter` effects unless real-device testing proves they are safe. Soft gradient stops can create the apparent shader blur without expensive true pixel blur. Check the actual Tizen browser engine, GPU memory, Art Mode behaviour and 2021 Frame remote response.

**TCL Android TV 11:** Pre-render each radial light once (for example with `RadialGradient`) into a small bitmap sprite and move, scale and fade those sprites as GPU-composited layers; never repaint full-screen gradients every frame *(changed in v3.3)*. **Do not make `RenderEffect` a dependency:** Android `RenderEffect` was introduced in **API 31 (Android 12)**, while the owner's TCL is **Android TV 11 / API 30** unless real-device checks establish otherwise. Prefer gradient falloff that looks blurred without an additional full-screen Gaussian blur. Double-buffer/composite the two palettes and animate alpha over 400 ms. Any actual blur must use an API-appropriate, tested fallback.

**Sony BRAVIA Google TV 65-inch OLED:** Use the same Android gradient/theme model. Where the actual Sony OS is **API 31+**, `RenderEffect` may be evaluated selectively for small bounded surfaces (e.g. glass treatment) after confirming API level and GPU performance, but the design never depends on it; otherwise use the TCL-compatible falloff/compositing path. Avoid excessive peak brightness and prolonged static luminous shapes on OLED; retain a dark overall image and drifting/idle-dim behaviour.

**Shared design logic:** One canonical palette/state mapping and 400 ms transition contract across all three TVs, with platform-specific renderers. If any renderer cannot hold a smooth transition at full resolution, reduce layer count, render gradients at a suitable internal resolution, or use precomputed *small* procedural textures while retaining dynamic movement; do not silently replace the whole experience with six heavyweight full-screen JPEGs. Treat CSS/Android APIs as implementation candidates until tested on each actual device.

### 32.5 Performance, accessibility and interruption rules

- **Target:** Smooth focus input and perceived **60 fps** animation where the panel/device can sustain it; verify with frame-time traces and human observation. Do not promise guaranteed 60 fps merely because the backdrop is code-generated. Aim for a 16.7 ms frame budget at 60 Hz, without blocking key processing. Record dropped frames and memory on each physical television.
- **Rendering budget:** Limit active full-screen gradient layers and composited surfaces; avoid continuous 4K blur passes, huge off-screen buffers, texture churn and decoding 4K imagery per D-pad press. Reuse gradients and render targets where possible. Make sure old mesh states are released/reused after crossfade.
- **Contrast:** Time, app title, labels, status controls and focus ring must remain readable in all six states and while transitioning. Check 4K and 1080p outputs, bright/dark rooms, colour-vision differences and TV-specific picture modes. Status warnings and VPN-gated Live TV messages must never disappear into similarly coloured light.
- **Motion accessibility** *(changed in v3.3)*: **Settings → Appearance** provides **Keep the Home background still** (stops drift, keeps the 400 ms crossfade), **Reduce motion** (instant crossfade, no drift) and **Lightweight rendering** (still frames, 200 ms crossfade). Honour supported system reduced-motion preferences when detectable. No strobe, fast oscillation, sharp pulsing or sound-reactive flashing by default.
- **Power/idle:** Pause animation when Unified TV is backgrounded, an external app is active, the TV enters standby or Samsung Art Mode takes over, or the interface is in an idle photograph/gallery mode. Do not animate behind protected video or over native overlays. Resume gracefully with the correct focused palette and no flash. Keep idle/Art Mode photography as its own mode, not a permanent backdrop under the active dock.
- **Failure path:** If a shader/CSS effect fails, falls below a device-specific frame-rate threshold or exhausts memory, fall back to a stable **static procedural mesh in the correct palette**, never a white screen, broken dock or unusable remote navigation. Log the downgrade for diagnostics, without surfacing technical jargon to the household.

### 32.6 Developer state contract (illustrative, not prescribed source code)

- `focusedDockAppId` drives `ambientPaletteId`, `displayedAppTitle`, selected-tile outline and halo as one atomic UI state.
- On focus change, compute target palette immediately, retain current composite as the outgoing state, and crossfade to the newest target in 400 ms; rapid repeat D-pad input must retarget from the *currently visible* state.
- Slow ambient drift is a secondary animation on the current mesh and must not restart sharply every time focus changes. Drift must not alter focus state or launch actions.
- Dock editing/reordering must preserve stable app IDs and associated palettes. A removed or unavailable app must not leave a stale title or glow.
- Route changes, Settings, alerts, overlays, app launches, Back/Home, and return from third-party apps must preserve the expected focus and apply the matching mesh without showing the wrong brand colour.

### 32.7 Physical acceptance tests — ambient mesh (all unverified until tested)

| ID | Test on each of TCL / Sony / Samsung | Pass condition |
|---|---|---|
| AM-01 | Step across all six dock icons one by one | Each palette and light position is recognisably the intended one (comparison with the chat mockups is pending owner references and does not block implementation); title/halo/focus always match |
| AM-02 | Change focus with one D-pad press | Full background crossfades in ~400 ms with no flash, hard cut or text blur |
| AM-03 | Hold/rapidly tap D-pad across dock | Latest selected app wins; no queued transitions, stale palette, dropped focus or accidental launch |
| AM-04 | Leave a single tile focused for 60 seconds | Soft radial centres drift gently without obvious loop, jitter, pulsing or distracting text movement |
| AM-05 | Test Keep the Home background still, Reduce motion and Lightweight rendering | Drift stops; the crossfade is 400 ms, instant or 200 ms respectively; remote and contrast remain fully functional |
| AM-06 | Observe all six states on real hardware | Clock, status, focus ring, labels and footer remain legible; no visible banding or blown-out edges |
| AM-07 | Profile memory, frame pacing and key latency | Smooth navigation at the device's tested refresh rate; no unacceptable stutter, memory growth or crash |
| AM-08 | Open app, return Home, enter Settings and open Live TV locked overlay | Palette, focus and status sheets remain correct; no background leaking over external apps |
| AM-09 | Standby/wake; Samsung Art Mode; gallery/idle transition | Animation pauses appropriately and resumes correctly; native Art Mode and gallery photography are preserved |
| AM-10 | Disable/force rendering fallback | Correct static palette displays with fully usable Home and remote, never a black/white failure screen |
| AM-11 | Edit/reorder six dock apps; add an unknown app | Brand palette follows stable app identity, not slot position; unknown app gets neutral accessible fallback |
| AM-12 | Compare actual TCL, Sony and Samsung screens with the owner's six mockups (pending owner references) | Product owner confirms visual character, glass depth and subtle motion; per-device deviations recorded |

**Release gate:** AM-01 through AM-03, AM-06 through AM-09 and AM-11 are required before marking the animated Home dock complete on any specific TV. The motion and fallback checks are also required before enabling the feature as the default daily-use experience. No screenshot, browser simulation or code review substitutes for actual device testing. The original Home/remote reliability requirements remain higher priority than visual polish.

### 32.8 References and v2.8 change log

The six mockups were supplied in chat as rough UX ideas. The files named `references/01-smarttube.jpg` to `references/06-music.jpg` were **never committed**, and the mockups stay outside Git because they contain third-party logos *(changed in v3.3)*. See [REFERENCES.md](design/REFERENCES.md). This file remains complete and readable on its own.

- Added runtime-generated radial-gradient ambient meshes and **400 ms D-pad focus crossfades**.
- Added slow, restrained drifting/breathing light centres rather than a static background.
- Recorded six app-specific palettes, placement and optical-depth requirements from the supplied mockups.
- Defined glass dock layering, focus glow, accessible motion controls, performance/fallback requirements and 12 device acceptance tests.
- Corrected Android API compatibility: `RenderEffect` cannot be assumed on the owner's Android 11 TCL.
- Reconciled older Section 8 performance guidance with dynamic gradients; photographs remain reserved for idle Art Mode/gallery and Synology content artwork.
- **Specification and reference packaging only**: no application code, TV settings, remote mappings or Home routing modified.


---

## 33. v2.9 — Apple tvOS-inspired calm visual language and optional depth-clock Wallpaper mode

**Status:** Approved design direction for development, **not** evidence of an implemented feature. **Applies to the household TCL Android TV 11, Sony BRAVIA Google TV and 2021 Samsung Frame native Tizen application**, subject to the device gates below. This section integrates the owner's five photo-clock and liquid-glass references (never committed; see [REFERENCES.md](design/REFERENCES.md)) with the existing Unified TV design; it does not reproduce Apple software or assume Apple proprietary APIs/assets. The intended result is recognisably Unified TV: cinematic, elegant, calm, remote-first, with gentle layered motion and high-quality typography.

### 33.1 Experience architecture — three deliberately distinct visual states

| State | Intended appearance | Entry and exit | Relationship to earlier requirements |
| --- | --- | --- | --- |
| **Active Home / app dock** | Section 32 brand-aware, slowly drifting radial-gradient mesh; luminous focus, translucent dock and restrained glass | Immediate on Home or any D-pad navigation; app focus controls colour | **Section 32 remains authoritative.** No large clock or photographic wallpaper competing with active controls. |
| **Unified TV Wallpaper mode** | Full-screen **Artwork** or **My Photos** with optional oversized translucent **depth clock** partially obscured by the foreground subject; no permanent dock, VPN badge or technical chrome | Enter via idle timeout or explicit Wallpaper command; any supported navigation key reveals Home | New optional visual experience described here. Distinct from native Samsung Art Mode. |
| **Samsung native Art Mode / system standby** | Manufacturer-controlled art and power behaviour | Samsung's supported Art Mode and Power controls; native transitions preserved | **Never replaced or overridden.** The existing no-clock rule applies to native Art Mode, not to Unified TV's optional Wallpaper mode. |

**Design principle:** The photograph/artwork should be the hero. The clock is a carefully composed graphic element *inside* the scene, not a bright text overlay pasted on top. Use depth layering only where the image can support it convincingly. The active Home dock must still open quickly, even while images are loading.

### 33.2 Appearance settings and discoverability

Add **Settings → Appearance → Wallpaper** as a first-class, remote-operable section within the existing six-category Settings layout. It must contain:

1. **Wallpaper source:** `Artwork` (curated/licensed collection), `My Photos` (owner-selected NAS gallery/albums; a later task whose access method, DLNA folders versus the Synology Photos API, must be designed first), or `Off` (existing ambient Home behaviour only). A future `Mixed` option may alternate sources, but must never silently mix private photos into public/guest profiles.
2. **Clock style:** `Depth clock` (preferred when compatible), `Simple clock`, `No clock`. An accessible **High-contrast clock** variant is also available. If a suitable subject mask is unavailable, automatically use Simple clock, or No clock when chosen; never present fake occlusion.
3. **Photo selection:** Choose album/folder, preview photographs, include/exclude individual items, set shuffle or sequential order and choose crop/focal position. The existing gallery's authorised folders are respected; do not index unrelated NAS directories.
4. **Timing:** Idle start choices `Off`, `2`, `5`, `10`, `15` minutes (suggest `5 minutes` as initial default, adjustable per TV/profile); slideshow interval choices `30 seconds`, `1`, `5`, `10` minutes. Allow `Start Wallpaper now` from Appearance and optionally a discreet remote action from Home.
5. **Visual controls:** Clock 12/24-hour format, optional date, clock placement (`Automatic` / limited safe manual offsets), depth strength, gentle photo motion on/off, dimming/brightness, transition style and a **Preview** action showing real photographs at TV resolution.
6. **Privacy/profile:** `Show my photos on this TV` is opt-in per TV and profile; private albums must not be shown in guest/child mode unless explicitly approved. On a shared family TV, show a clear local privacy confirmation before enabling a personal album.

No mandatory login, cloud photo upload, facial recognition or Apple ecosystem account. If the NAS is offline, use a previously authorised local cache only if the owner allowed caching, or revert to curated artwork/ambient mesh; never display a broken-image or empty screen.

### 33.3 Depth clock — true foreground occlusion

The attached reference imagery demonstrates **large, elegant clock numerals behind a photographed person**, so the subject's head, shoulders, body or another suitable foreground object visually passes *in front* of parts of the digits. Implement the actual depth ordering:

1. **Base photograph/artwork:** Full-bleed image, scaled and cropped with focal-point-aware composition, preserving important faces and avoiding TV overscan/safe-edge cropping.
2. **Clock layer:** Large, refined, translucent or subtly glass-like numerals, correctly displaying the current local time and updating at minute boundaries. Typography and spacing should feel premium and soft, not like a giant opaque banner. Use the household's preferred 24-hour format by default unless changed in Settings.
3. **Foreground cutout layer:** A clean alpha mask/segmented foreground rendered above the clock, so the clock is hidden **only where the subject overlaps it**. Maintain convincing hair edges, shoulders, clothing, fingers and other visible boundaries. Soft feathering is allowed but avoid glowing cutout halos or opaque rectangles.
4. **Optional atmospheric light:** Very subtle colour/tonal treatment sampled from the photo, with enough contrast for the numerals; do not apply Section 32's app-brand gradient over the photo.
5. **Subtle motion:** Gentle parallax, micro-scale/translation and/or extremely slow lighting drift may add dimensionality; avoid fake 3D depth, subject warping, visible crop edges, zoom jumps, distracting loops or motion that makes clock numerals difficult to read.

**Mask strategy** *(changed in v3.3)*: the depth clock uses **prepared foreground masks for selected curated artwork only**, reviewed and shipped alongside the licensed image. There is **no real-time segmentation on the TCL**. Any other image shows the standard clock. For a future My Photos feature, masks may only be prepared off the TV (on an authorised local computer or server) and stored as separate cached derivatives, after that feature's own design and tests. No image may be uploaded to a third-party AI/cloud service without a separate, informed owner opt-in. Never misrepresent a rough CSS gradient mask as accurate human foreground segmentation.

**Graceful fallback:** If there is no subject, the mask is low quality, the clock would obscure a face, or contrast cannot be achieved, use a smaller simple clock placed in available negative space or no clock, according to preference. The app should determine suitability from actual image/mask geometry, not merely whether a file has a `.png` extension. Allow manual clock-position override for difficult photos.

### 33.4 Curated artwork versus owner photographs

- **Artwork:** Curated landscape, nature, abstract, cinematic or original commissioned imagery suitable for 16:9 4K TVs, with carefully selected focal points and optional depth masks. Ship only artwork that is free and **explicitly licensed for this use**, bundled offline (no real-time download to display it), with its source, licence and attribution recorded in [ARTWORK.md](design/ARTWORK.md) *(changed in v3.3)*; Apple marketing images supplied as *design references* must not be bundled as application wallpapers without permission.
- **My Photos (later task; access method to be designed first):** Use owner-authorised albums and photo directories, including compatible Synology/NAS paths; preserve EXIF orientation, colour profiles where supported and sensible HDR/SDR conversion. Avoid stretching portrait photos: use tasteful letterbox/blurred-edge composition or a user-selected crop. Do not automatically scrape all NAS photos.
- **Selection and ordering:** Respect per-profile album scope, favourite photos, exclude lists, duplicates, randomisation and image orientation. Optional date/location captions must be off by default and must never reveal private metadata unexpectedly.
- **Transitions:** Gentle 500–1200 ms fades between approved images; do not decode full-resolution images synchronously on the UI thread. A photo may be displayed without depth effect while its mask is being prepared, without flashing when the mask becomes available.

### 33.5 Remote, Home and interaction rules

- The Wallpaper mode is **non-interactive until the user acts**. First D-pad/OK/Back/Home input should immediately dismiss the wallpaper and reveal the correct Unified TV Home or previous navigation state, with visible focus; **do not swallow the first press** or require multiple presses. The first navigation press reveals Home without accidentally launching a dock app. Subsequent presses navigate normally.
- The remote Home button must use the device-specific Section 28/29/31 contract. On TCL/Sony, selecting Unified TV as the default launcher is a separate, owner-controlled installation choice. Samsung reserved-button/native Home behaviour remains subject to the Section 31 device-specific feasibility gates.
- Native volume, mute, power, input/source, voice, HDMI/CEC, accessibility and Samsung Art Mode functions remain available. An active Ring doorbell/person alert follows the existing notification policy; restore the exact prior wallpaper/Home/native state where supported.
- The discreet **Settings → TV & performance → Advanced → Open original TV interface** escape remains unchanged and available after exiting Wallpaper mode.
- Wallpaper must never hide a critical setup error, lock a user into a screen, intercept native power functions or automatically take over another app's playback.

### 33.6 Platform rendering and performance

**TCL Android TV 11:** Render photo + clock + precomputed foreground mask as a small number of hardware-accelerated composited layers (for example Canvas/OpenGL or compatible views), with masks generated/cached outside the critical render path. **Do not depend on Android 12 `RenderEffect`**, which is unavailable on this device. Avoid expensive real-time 4K blur and neural segmentation on the UI thread.

**Sony BRAVIA Google TV:** Use the same Android compositing model with device capability checks; preserve default Home, HDMI 3 Xbox switching and normal remote/system behaviour. Do not assume the Sony's GPU or API level until verified.

**Samsung Frame QE50LS03AAUXXU (2021):** Use native Tizen Web App layered DOM/canvas/WebGL where actual browser/GPU tests support it; prefer a precomputed transparent PNG/WebP mask or cutout and opacity transforms. Test CSS blend/mask support rather than assuming recent browser APIs, avoid per-frame full-screen `filter: blur()`, and ensure the wallpaper app does not impersonate or replace **Samsung native Art Mode**. If the TV transitions into native Art Mode, the native feature owns the display.

**Performance gates:** First D-pad/Home response remains immediate; no frame hitch from photo decode, segmentation, shader compilation or NAS fetch. Preload next image at an appropriate decoded size, bound image/mask caches by actual device memory, and release GPU textures when hidden. Target smooth transitions on each physical TV; **60 fps is a target, not a claim**, and is subordinate to responsiveness and reliability. Provide a low-motion/low-power mode and disable parallax for reduced-motion preferences.

**OLED/image retention:** Especially on the Sony OLED, avoid a permanently fixed high-contrast clock: allow gentle position variation, adaptive dimming, configurable wallpaper timeout and display/power saving. Prefer no clock in extended idle/overnight sessions and respect TV screen-saver/standby controls. No unsupported claim of eliminating burn-in risk.

### 33.7 Visual integration with the existing design system

The desired **Apple tvOS-like feel** means elegant focus animation, clean hierarchy, spatial depth, calm transitions, polished glass materials and simple remote navigation—not a pixel-for-pixel copy of Apple UI, proprietary iconography or protected artwork. Keep the existing six-app dock, app discovery, family profiles, NAS media, Live TV/VPN protection and accessibility rules. Use one coherent typography, spacing, corner-radius, shadow, focus and motion system across active Home, Wallpaper, Settings and gallery. In Wallpaper mode, the clock should appear to belong *inside* the photo, while in active Home the brand-aware ambient mesh remains the hero.

**Owner visual references (described as bundled separately, but never committed; see [REFERENCES.md](design/REFERENCES.md)):** `wallpaper-reference-01.png` (music/glass controls), `wallpaper-reference-02.png` (liquid-glass home controls), `wallpaper-reference-03.png`, `wallpaper-reference-04.png`, `wallpaper-reference-05.png` (large depth clock with foreground person). These are visual requirements/reference examples only, not licensed production assets. The precise clock positions and photographic subjects are examples, not fixed templates. Visual comparisons against them are pending owner references and do not block implementation.

### 33.8 Acceptance tests (WP-01–WP-16)

| ID | Test on real household hardware | Required result |
| --- | --- | --- |
| WP-01 | Settings → Appearance → Wallpaper on TCL/Sony/Frame | Artwork / My Photos / Off and clock options are discoverable, fully D-pad operable and saved per TV/profile. |
| WP-02 | Enable Artwork, wait for configured idle interval | Full-screen artwork appears; dock/header/VPN controls disappear; selected source and timer are respected. |
| WP-03 | Choose My Photos and a specific Synology album (later task) | Only authorised images appear, with correct orientation and no unrelated NAS folders. |
| WP-04 | Use curated artwork with a prepared foreground mask | Oversized clock visibly sits behind subject silhouette, including hair/shoulders, with clean edges. |
| WP-05 | Use a photo with no usable mask/face obstruction | Graceful Simple clock or No clock; no fake depth or overlapping face. |
| WP-06 | Observe minute rollover, 12/24-hour setting and optional date | Time is accurate; minute update has no full-screen flash or stale numerals. |
| WP-07 | Press first D-pad/OK/Back/Home from Wallpaper | Unified TV controls return promptly, focused and usable; first input never accidentally launches an app. |
| WP-08 | Open Netflix/Disney+/BBC then return Home | Correct launcher behaviour per device; Wallpaper never overlays third-party playback. |
| WP-09 | Move rapidly across six app icons after leaving Wallpaper | Section 32 400 ms ambient mesh returns, smooth and independent of photo caching. |
| WP-10 | Unplug/disconnect NAS during My Photos mode | Safe fallback to approved cached images or Artwork; no blank UI, privacy leak or crash. |
| WP-11 | Switch adult/child/guest profiles | Private photo albums are hidden unless explicitly authorised for that profile. |
| WP-12 | Test Samsung native Art Mode, Power, wake and Smart Hub | Native Art Mode remains intact; optional Wallpaper mode is not misrepresented as Art Mode. |
| WP-13 | Test TCL Android 11, Sony and 2021 Tizen GPU/CPU/memory | No main-thread photo decode or segmentation stalls; bounded cache and responsive remote. |
| WP-14 | Enable reduced motion, high contrast and OLED protection | Clock remains readable; motion and bright static elements reduce appropriately. |
| WP-15 | Trigger authorised Ring event while Wallpaper is active | Existing alert policy works and restores appropriate previous state without losing remote control. |
| WP-16 | Restore original TV interface via discreet Settings option | Wallpaper does not obstruct escape; manufacturer UI can be reached per platform rules. |

**Delivery priority (OD-02/OD-04):** implement Wallpaper source selection, curated artwork and the simple clock first; then the **depth clock** for curated artwork with prepared masks; My Photos album selection and safe NAS/cache handling is a later, separately designed task *(changed in v3.3)*. Do not block the TCL Home-button fix, Samsung Home feasibility/recovery work or core media playback on advanced photo masking. The full feature is not accepted until WP-01–WP-16 are tested on the relevant physical televisions with screenshots/video and failures clearly recorded.

**Change log — v2.9:** Added a tvOS-inspired design language and optional Wallpaper mode with curated Artwork or owner photos, foreground-occluded depth clock, masks/segmentation and fallback, settings, privacy, accessibility, OLED/performance, physical remote acceptance, and explicit preservation of Samsung native Art Mode. The v2.8 animated app-focused radial mesh remains the active Home background.


**Change log — v3.2:** Confirmed smoke and carbon monoxide sensor alerts as the highest-priority notification class, above Ring, with explicit life-safety limitations and SAFE-01..10 tests; required durable Android/Tizen signing identities, in-place upgrade evidence, protected key custody and SIGN-01..08 tests. Prior v3.1 owner decisions remain unchanged.

**Change log — v3.3 (25 October 2026):** Corrected the specification in place and added the short authoritative [SPEC.md](SPEC.md). Eight owner decisions (OD-01..OD-08). One release gate order (G0 audit, G1 TCL Home, G2 Animated Home and Wallpaper, G3 NAS viewing, G4 Sony, G5/G5P Samsung, G6 household). Smoke/CO above Ring everywhere, with the queued-doorbell rule, per-alarm display and acknowledgement, and the Home Assistant `binary_sensor` state model. Two Android apps from one codebase (preview without HOME; launcher with a disabled HOME alias). One animation timing rule. One status and test-level vocabulary. Corrected Android key-rotation explanation. Visual references marked as kept outside Git. Curated, explicitly licensed offline artwork and prepared masks only. Removed the open-question log and private network addresses. Full list: [SPEC_CHANGES_v3.3.md](SPEC_CHANGES_v3.3.md).
