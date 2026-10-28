# Specification reconciliation

The current, authoritative specification is [SPEC.md](SPEC.md) (v3.3, 25 October 2026). Its detailed requirements are in [PRODUCT_SPEC.md](PRODUCT_SPEC.md), corrected in place; the v3.3 corrections are listed in [SPEC_CHANGES_v3.3.md](SPEC_CHANGES_v3.3.md). Requirement status lives only in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv). Older specifications are history in [archive/](archive/README.md) and must not be used as instructions.

## v1.9 proposal decisions (still valid, now part of SPEC.md)

| Proposal | Decision |
| --- | --- |
| Adult / Child profiles | Separate local media accounts with server-enforced permissions; adult PIN to return from Child or edit. Changing launcher profile does not switch SmartTube or other app accounts. |
| Embedded ExoPlayer | Media3 with direct server streams and real progress reports. VLC is excluded from the product and playback path; no VLC tile. |
| Continue Watching | Only after server persistence and interrupted-playback tests pass. |
| Dock | Add, Remove and Move, optionally PIN protected. Six choices are defaults, not a ceiling. |
| Native Samsung features | Preserve native Frame Art Mode and Samsung TV Plus; never silently replace them. |
| Split tunnelling | A routing configuration to prove, not a guarantee. Live TV stays blocked until route and drop are proven. |
| NordVPN intents | No invented silent-connect commands. |
| AirPin LITE | Correct name (not "AirPro"); setup only; no operational icon without real status. |
| Hardware settings | Delegate Wi-Fi, Bluetooth, sound, CEC and OLED care to native settings. |
| Voice | Optional two-tier local plus cloud; no zero-latency claim; no cloud key in an APK. |
| Ambient fallbacks | Only brand-aware imagery behind a highlighted app; scenic imagery for neutral states only. |
