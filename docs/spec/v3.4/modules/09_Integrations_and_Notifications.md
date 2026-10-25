# Integrations and notification arbitration — v3.4

**G6 feature integration**, but shared event priority policy can be built/tested earlier. **Always load Safety_Security_Contracts.**

Smoke and CO are highest, co-equal; no current connected sensors confirmed. Home Assistant states: `on`, `off`, `unknown`, `unavailable`; optional vendor-specific fault/test entities; app-calculated freshness. Use mocked alerts until real integration discovered. Present distinct concurrent alarms, per-alarm acknowledgement and lasting indicator. Ring doorbell/person is lower priority; suppress/queue during active emergency, then display only still-actionable visitor events. Audio focus only where supported; never claim control of Netflix/Samsung native UI. Other household important alerts have defined non-emergency payload in Safety module.

Home Assistant Green integration requires explicit owner pairing, protected credentials and capability discovery. Ring events/video depend on real service permissions and TV overlay feasibility. Alexa reminders, voice, heating and performance-cleanup remain optional and opt-in. Amazon Music initially launches the official supported app; do not promise custom API playback. Tottenham matchday widget and optional AirPin are later gated features.

NordVPN Live TV is **fail-closed** on unknown/unverified route and drops; real device/network leak tests mandatory. Preserve Smart Hub/Art Mode and native OS behaviours.

## v3.4 Ring event distinctions (normative)

Generic Ring **motion** events are ignored completely. **Person** detection triggers a discreet five-second toast (deduplicated; no camera, audio or playback pause). A physical **doorbell press** attempts to display live camera, dismissible with remote; overlay/PiP preferred when technically supported, otherwise a documented foreground/full-screen fallback. Camera auto-dismiss is not yet owner-decided. Never infer person detection from motion-only events. Emergency smoke/CO supersedes both Ring types, and queued visitor events expire under Safety contract. Module 12 owns user-facing configuration; this module owns event handling.
