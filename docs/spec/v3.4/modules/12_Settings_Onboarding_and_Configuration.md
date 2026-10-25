# Settings, onboarding and configuration — v3.4

**Normative module. Requirement prefix `SET-`.** This document defines a **general-purpose, multi-household product**, not a hardcoded installation for one owner. Load Core, Safety and Testing before implementing it; load platform module(s), UX and relevant integration module(s) as needed. Settings are a **first-class destination**, not a developer/debug screen. The initial implementation must work entirely with a D-pad, OK, Back and accessible on-screen keyboard. Do not claim a setting works on a platform without a capability check and physical proof.

## 1. Product principles and configuration boundaries

- **SET-001 — Zero-friction defaults.** A new installation must present a usable, minimal Home with six configurable app positions, curated licensed/offline wallpaper, and an obvious Library/Apps/Settings route. Do not require a cloud account, Synology NAS, Ring, Home Assistant, NordVPN, photo access, or an IP address to use Home.
- **SET-002 — Discover, then offer.** Detect supported apps, DLNA/UPnP media servers, compatible network services and hardware capabilities automatically where permitted. Display **friendly names and purposes**, not technical identifiers. Discovery failures are recoverable and never force manual IP entry in normal user flows. Technical connection details belong only in optional owner diagnostics; no promise of universal discovery without platform validation.
- **SET-003 — Honest capability.** Every control is `available`, `setup_required`, `temporarily_unavailable`, `unsupported_on_device`, or `permission_required`, with plain-language explanation and next action. Unsupported features are hidden from routine menus or shown disabled with reason; never present nonfunctional switches.
- **SET-004 — Safe changes.** Preview changes where practical; save explicitly for high-impact changes; offer Restore Defaults per section, with confirmation and clear scope. Never silently overwrite working integrations, PINs, device assignments or watch history.
- **SET-005 — Local-first privacy.** Profiles, watch history, preferences and discovered device addresses stored locally by default. Opt-in only for telemetry, photo libraries, remote integrations or optional future sync. Avoid storing passwords/tokens in plaintext or exporting them in backup.
- **SET-006 — Platform adapter.** One shared settings schema, UI and behaviour contract; Android TV, Google TV and Tizen adapters expose supported capabilities. Feature flags derive from verified capabilities, not assumptions based on OS label.
- **SET-007 — Navigation.** Open Settings from a dedicated Apps/Settings route (not forced into the six-app dock). Breadcrumbs, visible focus, predictable Back, section search later, and contextual Help. Exit returns to prior focus and never launches another app unexpectedly.

## 2. Information architecture — Settings landing page

A clean two-column 10-foot layout: left-hand category rail, right-hand contextual details, with a single clear selected item. Avoid dense phone-style preference lists. Default categories and order:

| Category | Primary controls | Gate / default |
|---|---|---|
| **Appearance & Home** | Home layout, six-app dock, app order, visual theme, motion, Wallpaper, clock, accessibility | G2 / minimal Home |
| **Library & Media** | Discovered servers, sources, folder permissions, metadata, playback, subtitles, Continue Watching | G3 / auto-discover, no source required |
| **Profiles & Family** | Adults/Kids, switch behaviour, PIN, permitted apps, content access, history | G3+ / Adults default until configured |
| **Apps & Shortcuts** | Installed apps, dock editor, preferred handlers, launch shortcuts, unsupported-app notices | G1+ |
| **Notifications & Doorbell** | Ring event rules, camera view, quiet hours, deduplication, emergency status | G6 / optional, no false monitoring |
| **Smart Home** | Home Assistant connection, supported devices/scenes, heating, alert sources | G6 / disconnected |
| **Live TV & Network Protection** | Live TV source, VPN requirement, protected-route status, leak-test results | G6 / locked until verified |
| **Connections & Devices** | Network status, discovery, NAS reconnect, audio, remote, HDMI/system handoff | Per platform / read-only where OS-owned |
| **Privacy & Data** | Photos permission, history, cache, optional diagnostics, backup/export/reset | Baseline |
| **Updates & About** | App/version, update channel, signatures, support, licences, diagnostics, recovery | G0/G1 baseline |

**SET-008:** Show only relevant integration categories when available or deliberately selected via **Add a service**; retain an accessible Integrations catalogue to discover future possibilities. Users should never see dozens of empty developer toggles.

## 3. First-run onboarding and progressive setup

- **SET-010:** On first launch show a 3–5 step optional guided setup: Welcome → remote/focus demonstration → app dock discovery → automatic local media discovery → finish. Skip is always available; no credentials required to reach Home.
- **SET-011:** Detect installed apps and suggest six dock entries; allow empty slots and custom ordering. Do not hardcode SmartTube, BBC, Netflix, Disney+, Music or Live TV for other households. Default dock may be chosen from installed apps; respect user country and app availability.
- **SET-012:** Scan for NAS/DLNA in the background with permission and bounded timeouts; display friendly server names and detected capabilities. Never prompt for IP address. If discovery fails, offer Retry, Check network, or Continue without Library. Optional owner-only technical diagnostics may expose addresses but are not a prerequisite. Tizen discovery mechanism remains a physical-device feasibility gate; investigate embedded Tizen service with UDP/SSDP as a proof of concept before claiming it works.
- **SET-013:** Optional Add a service wizard for Home Assistant, Ring and others; clearly describe what data/access is required, ask permission at point of use, and allow disconnect/delete. No auto-enrolment.
- **SET-014:** Adults/Kids setup is optional; Kids profile is **not** advertised as secure until source-side restrictions and platform app restrictions are proven. Provide clear warnings where installed third-party apps remain accessible through native OS menus.
- **SET-015:** Re-run onboarding per section from Settings; support migration from older settings schema without erasing existing configuration.

## 4. Appearance & Home

- **SET-020:** Minimal Home is default: six-app dock, atmospheric animated gradient, optional clock/status, no automatic hero, Continue Watching or recommendation rows. Library is a separate destination. Users may configure dock position/size within tested safe-area limits; avoid uncontrolled complex layout editor in initial release.
- **SET-021:** Dock editor: reorder, replace, remove, reset, detect missing/uninstalled apps; show readable icons and friendly names; preserve keyboard/remote focus. The six positions are slots, not a requirement to install six apps.
- **SET-022:** Ambient mesh controls: On/Off, intensity `subtle/standard`, animation `normal/reduced/off`, colour mode `follow focused app/neutral`, and optional custom palette only after supported UI exists. Respect OS reduced-motion, power/performance gates; 400 ms immediate focus transition is the default, not user-adjustable to an untested extreme.
- **SET-023:** Wallpaper controls: curated art / approved local photo source / off; shuffle, idle start delay, rotation interval, clock visible/hidden, clock style/position, depth clock `auto/off`, brightness/dimming. Auto depth only on prepared masks; licensed art must be bundled/cached and usable offline. Personal photos are opt-in, with explicit library selection and cache deletion.
- **SET-024:** OLED care: screen dimming, static-content avoidance, burn-in mitigation. Never override native Sony OLED or Samsung Art Mode protections; device-specific limitations shown honestly.
- **SET-025:** Preview changes and revert on timeout for settings that could make text unreadable or navigation unusable. No active photographic wallpaper behind dock by default.

## 5. Library & Media

- **SET-030:** Automatic source discovery with friendly names, last-seen state, source type and media capabilities. Multiple NAS/servers supported; user chooses preferred source or automatic aggregation. No mandatory IP/port entry.
- **SET-031:** Source management: Enable/Disable, Rename locally, Preferred source, Rescan, Forget source, Refresh catalogue, show error reason. Discovered endpoints are untrusted; validate device descriptions, URL schemes/hosts, service types, size/timeouts and prevent localhost/credential abuse. Keep network discovery scoped and rate-limited.
- **SET-032:** Library layout: Movies / TV Shows / Continue Watching / Recently Added / Favourites / Kids-eligible sources. Display only sections backed by actual media and metadata. Home remains minimal.
- **SET-033:** Playback: preferred supported player, resume prompt, autoplay next episode (off by default), subtitle language/size where player supports, audio preference, seek behaviour, artwork cache. Unsupported controls cannot be advertised as universal.
- **SET-034:** Content classification: metadata provider opt-in where it sends titles outside local network; manual title corrections; cached posters and metadata refresh; no invented rating, certification or cast data.
- **SET-035:** Synology's `/video/Kids`, `/video/Movie`, `/video/TV Show` are **owner-specific example source roots**, not global defaults. Offer discovered folders generically for other households. Do not assume folder filtering is secure Kids access; use authenticated/source-enforced controls where possible.
- **SET-036:** Synology Photos separate optional integration; no automatic access to private albums; distinguish Photos API/albums from DLNA folder browsing.

## 6. Profiles & Family

- **SET-040:** Exactly two built-in profile types in initial scope: **Adults** and **Kids**. No per-person profile requirement. On startup normally use last selected profile without interrupting Home; optional profile chooser at launch. Switching from Kids to Adults requires owner PIN when Kids mode enabled.
- **SET-041:** Adults profile manages setup, PIN, integrations, network protection and family restrictions. Kids can change harmless personalisation only if allowed; never alter emergency rules, VPN policy, account credentials or global source permissions.
- **SET-042:** Kids settings: allowed apps, approved media sources/folders, content rating limits only where reliable metadata and enforcement exist, watch history and Continue Watching separated from Adults, optional daily viewing schedule. Hiding app icons or DLNA folders alone **is not security**; communicate native OS escape limitations.
- **SET-043:** PIN: create/change/recover with secure owner recovery process; never store plaintext PIN; rate-limit attempts and avoid an easy Back-button bypass. No remote cloud recovery without verified identity. If restrictions cannot be enforced outside app, label them **Unified TV only**.
- **SET-044:** Guest/individual profiles, per-child accounts and cross-device sync are future ideas, not v3.4 commitments.

## 7. Notifications, Ring and household safety

- **SET-050:** Ring rules (latest owner decision): **motion detected → no notification, sound or camera**; **person detected → small toast** (default 5 seconds, no playback interruption, deduplicated); **doorbell button pressed → attempt to open live camera** with remote dismiss and previous-focus restoration. Never treat generic motion as person detection. Distinguish person-event availability from ordinary motion-only Ring integrations.
- **SET-051:** Doorbell camera layout preference `large overlay where supported / full-screen fallback`; camera automatic-close timeout is **not yet owner-decided**. Provide configurable options `30s/60s/manual` after feasibility testing; propose 60s default only as a product recommendation, not a confirmed owner decision. No claim that PiP overlays can appear above Netflix or native Tizen UI; test foreground/background capability per TV.
- **SET-052:** Quiet hours suppress non-emergency person toasts (optionally doorbell audio); never suppress genuine smoke/CO alerts. Allow per-device volume preference only when OS supports it; do not promise third-party app audio control.
- **SET-053:** Smoke/CO: co-equal highest priority, independently acknowledged, persistent active indicators, source-authenticated but not TV-verified, offline/unknown distinct from emergency. Do not provide an Off toggle for emergency priority; allow test mode and source status. No detectors confirmed connected; show **Not configured — no live monitoring** until paired. Do not describe TV as a certified alarm.
- **SET-054:** Priority order `EMERGENCY_SMOKE_CO > RING_DOORBELL > RING_PERSON > HOUSEHOLD_IMPORTANT > INFORMATIONAL`; during active emergency suppress/queue Ring events under Safety contract; expire stale queued visitors. Separate notification preview/test fixtures from real alerts with obvious **TEST** labels.
- **SET-055:** Event log: opt-in or local-only minimal history with retention limit, redacted camera thumbnails, clear/delete controls. Never save camera footage by default.

## 8. Smart Home, integrations and Live TV

- **SET-060:** Service catalogue: Home Assistant, Ring, supported music app handoff, optional Synology Photos, and future integrations. Show availability and permission requirements, Test Connection, Reconnect, Disconnect, Forget and credential expiry. Never expose token values in UI/logs.
- **SET-061:** Home Assistant: local URL discovery where possible, secure pairing/auth, room/device selection, optional lighting scenes, heating boost with visible expiry, alert sensor mapping and source freshness. An unavailable sensor cannot be shown as clear.
- **SET-062:** Ring: account/integration setup and capability detection for `motion`, `person`, `ding`, `live_stream`; do not infer person detection from motion. Display unavailable capabilities and supported fallback without misleading the user.
- **SET-063:** Live TV and VPN: select provider/source, connect supported VPN integration, show `locked / verifying / verified / disconnected / error`, and a plain-language reason. **Fail closed** unless the actual protected route is verified; unknown, disconnect and failed leak checks always lock. Never offer a user toggle to bypass the fail-closed contract for protected sources.
- **SET-064:** App handoff: Amazon Music and other providers launch official apps where supported; no implied custom playback or access to their watch history.

## 9. Connections, devices and accessibility

- **SET-070:** Show friendly connectivity summary: Internet, local network, media sources, optional integrations and last successful check. Distinguish offline Internet from local NAS availability. Self-heal/retry in background with backoff, no continuous subnet scanning.
- **SET-071:** Remote: focus speed where tested, long-press shortcuts where supported, key mapping help, Back/Home expectations, input-device identification. OS-reserved keys remain native. Do not override Samsung Home/Art Mode or Android stock Home recovery.
- **SET-072:** Audio/output: read-only display of available output and supported actions; volume/HDMI-CEC and third-party playback controls only where APIs permit. No false promise of changing native system settings.
- **SET-073:** Accessibility: larger text, high-contrast focus, reduced motion, screen-reader-friendly labels, timeout extensions, no colour-only status, safe focus, adequate 10-foot spacing. Emergency alerts remain perceivable in all themes.
- **SET-074:** Language and region: use device locale initially; plan localisation-ready labels, 12/24-hour clock, dates, parental rating scheme and streaming app availability by country. No UK-only hardcoding.

## 10. Privacy, updates, diagnostics and support

- **SET-080:** Privacy dashboard: what is stored locally, connected services, optional data sharing, photo/cache usage, watched history per profile; clear history, thumbnails and cached art separately. No telemetry by default; if added, opt-in, documented and revocable.
- **SET-081:** Backup/export/import: user preferences, dock and safe profile settings; **exclude tokens, credentials, signing keys, raw camera images, private photos and PIN hashes** from ordinary export. Sensitive account re-pairing required after restore. Schema versioned and reversible where feasible.
- **SET-082:** Updates: installed version, platform/build, channel (`stable` default, `preview` opt-in), check for updates where supported, release notes, update/recovery instructions. Never suggest preview can upgrade to differently signed launcher. No automatic changes to Home ownership or Samsung native system behaviour.
- **SET-083:** Owner diagnostics behind advanced section: capability matrix, last discovery run, non-sensitive endpoint health, app/OS version, permissions, log export with redaction, smoke/CO simulation (prominently TEST), VPN verification evidence and rollback guidance. No secret values, household identifiers or personal IPs in shared reports by default.
- **SET-084:** About/Help: open-source licences and artwork attribution, privacy notice, supported platforms, troubleshooting, report issue, contact/support link if actually provided by project, and reset options. No invented support service.
- **SET-085:** Reset hierarchy: Reset appearance, Reset Library metadata, Disconnect service, Reset profile, Factory-reset Unified TV app. Destructive resets require clear consequences and explicit confirmation; do not factory-reset the television or modify native apps.

## 11. Data model, portability and failure handling

Use a schema-versioned configuration model, e.g.:

```json
{
  "schema_version": 4,
  "home": {"layout": "minimal", "dock_app_ids": [], "motion": "normal"},
  "wallpaper": {"source": "curated", "depth_clock": "auto"},
  "profiles": {"active": "adults", "kids_enabled": false},
  "library": {"discovery_enabled": true, "selected_source_ids": []},
  "notifications": {"ring_motion": "ignore", "ring_person": "toast", "ring_ding": "camera"},
  "integrations": {"home_assistant": "disconnected", "ring": "disconnected"},
  "live_tv": {"vpn_policy": "fail_closed"},
  "privacy": {"telemetry_opt_in": false}
}
```

**Illustrative schema only:** IDs are opaque local references; credentials belong in platform secure storage; status is derived from integration health, not persisted as a trusted authentication result. Kids access restrictions, emergency priority and VPN verification are enforced by runtime policy, not merely writable JSON preferences. Document migrations, corrupt-state fallback, atomic writes, safe rollback and default values. No app crash or reset may disable emergency priority policy or unlock protected Live TV.

## 12. Acceptance and implementation sequencing

| Requirement group | Minimum acceptance evidence | Earliest gate |
|---|---|---|
| SET-001–015 | Clean install, no-login Home, remote navigation, skip/restart setup, zero-IP media discovery attempt | G1/G3 |
| SET-020–025 | Actual TCL focus/contrast/performance, wallpaper and motion/reduced-motion | G2 |
| SET-030–036 | Synology discovery and playable catalogue; multiple-source and no-source tests; Frame separate evidence | G3/G5 |
| SET-040–044 | Kids PIN/Back escape tests, source-enforced restrictions, profile-specific history | G3+ |
| SET-050–055 | Ring event fixtures: motion silent/person toast/ding camera, concurrent emergency suppression | G6 policy tests earlier |
| SET-060–064 | Auth, revoke/reconnect, HA source state, VPN route drop locks Live TV | G6 |
| SET-070–074 | Remote/OS settings boundaries, reduced motion, language and offline behaviour | G1+ |
| SET-080–085 | Backup/redaction/reset, signing/version continuity, corrupted settings recovery | G0/G1+ |

**Cross-device test matrix:** TCL Android 11, Sony Google TV and 2021 Samsung Frame must each be tested for installation, first-run, Settings navigation, saved preferences after reboot, app discovery, NAS discovery, platform-disabled controls, profile access, recovery, and external-app return. A settings UI screenshot or unit test is not a physical pass. Add requirement rows to the repository's **only live** `docs/ACCEPTANCE_REGISTER.csv` without replacing existing rows.

## 13. Explicitly deferred / unresolved

- Universal search **after core reliability**, not a G2/G3 dependency. Keep catalogue/index design search-ready without shipping a fake global search.
- Third-party streaming watch history and cross-service Continue Watching require supported provider integrations; NAS-local resume only initially.
- Samsung background service / SSDP auto-discovery is a **proof-of-concept**, not verified 2021 Frame support. See Tizen module.
- Ring live camera overlay over other applications is **not proven**; foreground-only fallback must be honestly communicated.
- Ring camera auto-dismiss timeout not decided; expose design option but do not hardcode as confirmed.
- Kids restrictions outside Unified TV may not be enforceable on stock Tizen/Android without system support. Describe actual boundary.
- Cross-device account sync, individual profiles, cloud login, voice assistant and universal search are later, separately approved features.
