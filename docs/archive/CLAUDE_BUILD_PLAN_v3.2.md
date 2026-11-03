# Unified TV — Claude Build Plan v3.2

**Date:** 25 October 2026  
**Status:** Implementation plan and acceptance gates, **not a statement of work completed**.  
**Companions:** `Unified-TV-Specification-v3.2.md`, `AI_HANDOVER_v3.2.md`, `FEATURE_STATUS_AND_ACCEPTANCE_v3.2.md`.

## Mission and definition of done

Deliver a calm, content-first, tvOS-inspired **daily-use** experience on the owner's actual TCL Android TV 11, Sony 65-inch BRAVIA OLED Google TV and Samsung Frame 2021 Tizen. Prioritise dependable Home/remote behaviour, **then the owner-selected animated Home and Wallpaper experience**, then the real NAS viewing journey over breadth. An agent cannot mark a feature **verified** unless physical-TV evidence is attached to the exact installed build; a successful build or mockup is not acceptance.

## Claude's required first response, before modifying source

1. Read `AGENTS.md`, current repository policies and this document. `git status`, branch/commit, open PRs, untracked files and installed artefacts must be recorded **without resetting anything**.
2. Compare repository copies of product spec/status/handover to this package; **do not assume v0.4 branch, code layout or device access remain current**. Record source-of-truth discrepancies.
3. Identify build variants, package IDs, **current signing certificates/fingerprints, protected owner-controlled key custody and N→N+1 upgrade plan**, release retention and recovery route before changing launcher intents. Never generate or export a private key without owner approval.
4. Create a short task plan naming touched files, test level, risks, dependencies, rollback and which other agent owns overlapping areas. Ask the owner before changing system Home settings, running unsupported Samsung native code, uploading private material, or publishing releases.
5. Use existing tests and architecture where viable; no duplicate framework/rewrite merely because a new spec mentions an interface.

## Milestone sequence — owner-approved order

| Gate | Owner-facing outcome | Work package | Proof required | Blocking dependency |
|---|---|---|---|---|
| G0 | Safe, reproducible development | Audit source/branches; distinguish `preview` vs owner-selected `launcher` build; confirm independent TCL recovery; **audit existing APK/WGT signatures and propose stable protected release keys and safe in-place upgrade tests**; begin device/remote identification plan; test data hygiene | Commit/branch/build manifest, package intent inspection, independent stock launcher restore instructions | Repository/device access for physical tests |
| G1 TCL | **Unified TV becomes selected default Home after recovery is proven** | Reproduce escape; supported Android HOME chooser; stable root Back; external-app return; discreet Settings escape; standby/boot recovery; remote key logging | Real TCL video Home from Netflix/Disney+/BBC, repeated Back, observed problem button, restart and restore original interface; owner chooses default only after successful recovery demonstration | G0; owner selection through supported UI; physical TCL |
| G2 Visual | **Animated Home and Wallpaper are the next build priority** | Six palette radial meshes, 400 ms cancellable crossfade, subtle drift, glass/focus; curated Artwork as default Wallpaper source; idle/remote return; simple clock; automatic masked depth clock on suitable photos, graceful fallback, OLED and reduced-motion controls | Reference comparisons, focus latency/frame/memory, AM and WP acceptance on real TCL; no first-key loss | G1 navigation stability; licensed artwork; appropriate mask fixture |
| G3 Media | Complete NAS film/TV viewing | Editable six-app dock/Apps grid as needed; DLNA named discovery and exact selected roots; authenticated catalogue adapter where configured; Media3 playback/seek/subtitles/errors | Real NAS browse/play/seek/return, source loss, no unauthorised folder, clear DLNA limitations | G1; NAS reachability/media fixture; G2 takes priority if capacity conflicts |
| G4 Sony | Same reliable daily-use flow | Sony-specific HOME/remote/standby, Xbox HDMI 3/CEC, audio and OLED safeguards; visual performance | Sony model+remote captured with owner; same journey and recovery on Sony | Transferable G1–G3 logic; Sony access |
| G5 Samsung | Native Frame experience without damaging Art Mode | Signed WGT; real Smart Remote and Smart Hub paths; TV Plus; Wallpaper vs Art Mode; AVPlay feasibility | Exact 2021 Frame firmware, Smart Remote tests, Art Mode/audio recovery | Tizen developer access; supported APIs |
| G5P Samsung proposal | **Separate controlled-test proposal; no native write** | Document CleanFrame-style Home reassignment compatibility, read-only checks, hazards, independent recovery, rollback, explicit test sequence and stop conditions | Owner-readable go/no-go proposal; independent restoration method described and checked before any later execution approval | Source/hardware research; owner approval for any actual experiment is separate |
| G6 | Secure household integrations | NordVPN fail-closed Live TV, **smoke/CO emergency alerts above Ring**, Ring doorbell+person, child-safe NAS, music handoff, optional reminders/voice/tuning | SAFE-01..10 emergency-over-Ring arbitration and delivery limits; SIGN-01..08 per-platform signed-update continuity; independent hardware and privacy/security gates per feature | Service auth, permissions, vendor constraints |

**Owner decisions already answered:** TCL default Home = yes after rollback proof; next feature = Animated Home and Wallpaper; Wallpaper source = curated Artwork; depth clock = automatic when suitable; Samsung = proposal only; remote identification = Claude guides owner later. **Do not ask these six questions again.** The TCL escape button remains to be identified at G1 physical testing.

**Parallelism:** one agent may implement G2 catalogue and another G5P Samsung proposal **only if** file ownership and shared interfaces are recorded. G1 TCL owns launcher/navigation contract. Do not let parallel agents rewrite the same Settings, domain models, manifest or acceptance files without coordination.

## Work-package contract — mandatory for every agent

Each task issue/PR must include: requirement IDs and affected TVs; existing code paths; exact implementation boundaries; dependencies; failure/rollback; unit/integration tests; manual steps; known unsupported cases; private-data audit; screenshots or logs from exact build; status matrix updates. If a device cannot be accessed, use `implemented_unverified`, not `passed`. No fake service responses presented as live evidence. Any emergency alarm event must pre-empt Ring where supported; the app is supplementary and must never be presented as a certified detector/alarm. Document the alert path when another app or Art Mode is active.

## Technical integration boundaries

- **Android:** separate preview and HOME-launcher identities or manifest variants, verified against existing signing/update paths; Android TV 11-safe rendering; Media3 embedded player; system HOME routing rather than interception inside Netflix.
- **Tizen:** native web UI and supported Tizen APIs, AVPlay where authorised; no assumption of Android intents, background overlays or HOME permission. CleanFrame's undocumented native helper is research, not an approved install step.
- **Shared contracts:** capability registry, remote/focus event schema, media adapter interface, persistence migrations, security event priority (**smoke/CO above Ring**) and visual-state machine described in v3.2 specification.
- **Appearance:** `ACTIVE_HOME` ambient radial mesh, `CONTENT_BROWSE` matched media art, `WALLPAPER` default curated artwork/authorised photos; automatic depth mask only for suitable photos with safe simple-clock/no-clock fallback. Original assets, not a literal Apple UI clone.
- **Safety:** never disable factory Home, Smart Hub, Art Mode, HDMI/CEC, volume or native security; VPN status alone never unlocks Live TV.

## Minimum acceptance evidence format

`test_id | requirement | TV exact model | firmware | physical remote | app variant/version/signature | commit | steps | expected | actual | test level | evidence path | result | blocker | rollback | reviewer/date`

Evidence levels: `spec_only`, `source_inspected`, `unit_pass`, `emulator_pass`, `local_build_pass`, `PC_network_observed`, `physical_TCL_pass`, `physical_Sony_pass`, `physical_Frame_pass`. Only the appropriate physical level can close a household hardware requirement.

## Agent stopping rules

Stop and request owner direction if the default launcher cannot be restored independently; the wrong signing key would prevent a safe in-place update or threaten existing app data; Samsung experiment requires undocumented native writes; a service requires exposing credentials; a change could disrupt native Art Mode/audio; or two agents have conflicting changes. Preserve uncommitted work and provide a concise blocked report rather than using destructive commands.

## Owner decisions and remaining device discovery

The six decisions are **resolved** in `Unified-TV-Specification-v3.2.md` OD-01–OD-06. Do not present them as open questions or substitute the earlier agent recommendations. **No further decision is needed to begin G0 and safe G2 preparation.**

At G1 hardware validation, help the owner identify the TCL's exact model/firmware/remote, the physical button that previously escaped to stock Home, and the safe restore path. At G4, guide identification of the Sony model/firmware/remote. A photo or About-screen reading is optional until physical mapping is necessary. Samsung G5P requires a **separate proposal** and **later explicit permission to execute**; producing the proposal does not authorise a native change.

**Safe defaults:** no automatic TV launcher switch before rollback proof and owner action; no Samsung native Home modification; no private photo import without selection; keep VPN Live TV fail-closed and Samsung Art Mode intact.


## v3.2 release gates — owner instruction

- **G0 signing audit is mandatory now:** inspect current signatures, application IDs, version codes and Tizen certificates; produce a protected owner-controlled key custody/backup proposal. **Do not create or reveal keys without owner approval.** A stable signing identity must exist before the first owner daily-use signed release. Prove signed N→N+1 in-place update on each platform before release acceptance; never uninstall existing configured apps to bypass a mismatch.
- **G6 alert precedence is mandatory:** verified smoke and CO alarms are equal top priority, **above Ring** doorbell/person, reminders and UI effects. Implement emergency priority policy as part of the notification architecture even if actual sensor integration awaits owner device details. Do not claim real emergency delivery until SAFE-01..10 physical/service tests pass; do not replace local audible detectors.
- **Parallel work:** G0 signing plan and emergency alert event schema/policy may be developed alongside G1/G2 if file ownership is coordinated. Neither is permission to change live TV settings, generate secrets or modify Samsung native Home.
