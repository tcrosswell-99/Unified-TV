# Shared architecture and contracts — v3.4

**Load with:** Core, relevant platform, relevant feature, Safety/Security when integration touches permissions, data or alerts.

## Architectural boundaries

Separate **domain/state**, **platform adapters**, **navigation and focus**, **media source**, **visual rendering**, **household event coordinator**, **settings and migration**, **diagnostics**. Avoid rewriting functioning code just to match a proposed abstraction; first map existing repository interfaces.

- `DeviceCapabilities`: platform, OS/firmware, actual key capabilities, HOME eligibility, Art Mode/TV Plus, media playback, rendering path, notification surface, audio focus, standby, safe recovery. `supported | unsupported | unverified` with evidence, never inferred from a similar model.
- `RemoteEvent`: device, physical key if observable, mapped semantic action, press type, focus origin, monotonic timestamp; reserved keys must not be intercepted by unsupported means.
- `NavigationState`: `ACTIVE_HOME | APPS | CONTENT_BROWSE | DETAIL | PLAYER | WALLPAPER | SETTINGS | ALERT`; explicit Back, Home, app handoff, focus restoration and state transitions. Native Samsung Art Mode is **outside** app state.
- `MediaSourceAdapter`: source identity, authorised roots, browse, metadata/provenance, playback URL, subtitles, seek and error types; DLNA folder browsing is not a premium catalogue or security boundary.
- `HouseholdEvent`: stable event ID, authenticated source, kind, location label if known, source timestamp, receive timestamp, current source state, source health, priority and acknowledgement. Do not fabricate a detector verification result.
- `WallpaperAsset`: ID, source, local cache, licence/attribution, crop, colour characteristics, optional foreground mask and its quality metadata. Missing masks must not crash the renderer.
- `Settings`: schema version, per-device capability gates, opt-in integrations, Wallpaper defaults, reduced motion, OLED controls and protected secrets stored in platform-safe storage (never repository).

## Error, observability and persistence

Treat absent permissions, disconnected NAS, unknown detector state, VPN drop, invalid credentials, unavailable native integrations and failed image loading as explicit UI states. Use idempotent event processing and per-event acknowledgements. Preserve state across supported app restarts/upgrades. Redact addresses, device identifiers, photos, tokens and keys from logs. No default permission escalation. Every adapter must expose test doubles with clear simulated labels.

## Cross-module invariants

**Security and recovery invariants are always loaded:** VPN fail-closed; smoke/CO outrank Ring; preview never registers HOME; no Samsung undocumented writes; native Art Mode and factory recovery preserved; no private material in source; signing keys protected. These apply even when a sprint loads only one feature module. See `03_Safety_Security_Contracts.md`.
