# Safety, security and privacy contracts — v3.4

**MANDATORY for every sprint.** Load this even for UX-only changes. No visual polish, remote behaviour or feature launch may bypass these invariants.

## Emergency precedence and Home Assistant truth

Priority `EMERGENCY_SMOKE_CO` (co-equal smoke and CO) > `RING_DOORBELL` > `RING_PERSON` > `HOUSEHOLD_IMPORTANT` > `INFORMATIONAL`. **No smoke/CO detectors are currently confirmed connected**. Build simulated event fixtures now, not a claim of live household monitoring.

For Home Assistant binary sensors, normal state mapping is `on` (alarm), `off` (clear), `unknown`, `unavailable`; `test`/`fault` only when a particular device integration provides separate trustworthy entities or attributes. `stale` is **app-calculated freshness**, not a universal Home Assistant sensor state. An authenticated event is **source-authenticated**, not physically “verified” by the TV. No automatic all-clear from silence, loss of connection or an acknowledgement. Distinguish offline/unknown from active emergency with different language and appearance. Certified local audible detectors remain primary safety devices.

Concurrent smoke and CO: list each alarm separately with **kind, room when known, time, source status and per-alarm acknowledgement**. Acknowledge only the chosen alarm; maintain a persistent active-alarm indicator until a reliable source clear event. If the app is foreground, emergency interrupts Wallpaper, focus animation, Ring and other in-app UI. During active emergency, **suppress Ring presentation and queue the latest Ring visit**; on clear, show only if still actionable, otherwise discard as expired with event-log trace. Do not auto-cover an emergency with a Ring timeout. Suggested queue policy: retain up to **60 seconds after emergency clears** if the visitor event is still active, configurable later; this is a provisional implementation default, not a proven Ring capability.

Audio: may request audio focus or interrupt in-app playback where platform permits; **cannot promise to pause Netflix, other external apps, native Samsung Art Mode or powered-off TVs**. Emergency event display outside Unified TV is a feasibility gate, not an assumed capability. Do not present TV notification as a guaranteed life-safety channel.

`HOUSEHOLD_IMPORTANT` payload: `event_id`, `kind`, `title`, `summary`, `source`, `occurred_at`, `received_at`, optional `room`, optional `action`, `expiry`, and explicit `not_emergency=true`. Examples: door left open, leak warning from a verified integration, heating fault. Must not masquerade as smoke/CO and must not override emergency.

## VPN and access

**NordVPN Live TV fail-closed:** never unlock merely because a VPN UI says connected. Require verified protected route and leak/drop checks for target device/stream path; on unknown/disconnect/error, lock access and show understandable state. Test fail-closed behaviour on the actual TV and preserve native security controls. Child profiles require server-enforced authorised content boundaries; DLNA folder selection alone is not access control.

## Recovery and platform restrictions

Never disable stock Home, factory packages, Smart Hub, Samsung native Art Mode, HDMI/CEC, volume, accessibility or OS security. No unattended ADB settings, service-menu changes, firmware patches, unsupported Samsung native writes, remote interception or key reassignment. Samsung controlled test is **proposal only** until separate explicit approval with independent recovery. Protect credentials, private photos, local IPs, serials, certificates and signing secrets from source, logs, screenshots and packages.

## Tests

Unit: event mapping, priority, concurrent alarms, per-alarm acknowledgement, persistent indicator, Ring suppression/expiry, stale/offline, source auth, false clear, VPN drop. Integration: Home Assistant mock, auth failure, duplicate/out-of-order events, reconnect. Physical: exact device/app/foreground/other-app/standby capability matrix. **Do not claim physical alert delivery without actual sensor and device evidence.**

## v3.4 event and configuration invariants

Ordinary Ring motion generates **no user-facing event**. Person detection is a lower-priority toast; physical doorbell press is higher-priority visitor camera request, still below smoke/CO. Do not queue generic motion. All user settings must preserve emergency precedence and NordVPN Live TV fail-closed policy; no toggle, profile switch, restore/import, corrupt-state recovery or reset may bypass these runtime contracts. Kids access enforcement must be honest about OS-level escapes.
