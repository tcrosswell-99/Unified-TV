# All-TV functional test plan

**Latest owner direction, 24 October 2026:** the dock is now a fixed approved set, with rearrangement only. Add/remove/pin and the arbitrary Apps picker are removed. This supersedes earlier editable-shortlist language. The approved shared six are SmartTube, Netflix, Disney+, BBC, Live TV and Music; Sony adds Xbox and the native Frame plan adds Samsung TV Plus. See [Preview 0.3](PREVIEW_0_3.md) for implementation and remaining VPN gates.

**Current native plan:** [Complete specification v2.0 draft](PRODUCT_SPEC.md) selects native Android for Sony/TCL and a native Tizen Web Application for Frame, with embedded Media3/AVPlay and no VLC playback. No external Frame streamer is required. Native Tizen remains unfinished. [TCL hardware evidence](TCL_TEST_2026_10_24.md) records the limited Android smoke pass and remaining gates. Earlier external-streamer and VLC milestones below are superseded by this plan.

This is a test plan and implementation inventory, **not a claim of full test readiness or passing hardware tests**. Record pass/fail independently for Sony Android, TCL Android and native Frame Tizen. Compare the corresponding APK and WGT revisions; no external device is required. The legacy matrix is retained below for traceability and is superseded where it describes an external Frame runtime. Use the NT journeys in the complete specification for native Frame acceptance.

| Test | Sony Android | TCL Android 11 | Frame Android streamer | Native Frame |
| --- | --- | --- | --- | --- |
| Separate install/uninstall and factory Home recovery | Not tested | Not tested | Not tested | APK unsupported |
| Dock, app launches, pin/remove/move-save/cancel, overflow | Not tested | Not tested | Not tested | Android input only |
| 100 remote presses, correct final atmosphere, memory/jank | Not tested | Not tested | Not tested | Not applicable |
| HA/media test/save/edit/forget, TLS/HTTP consent, offline Home | Not tested | Not tested | Not tested | Configured via Android |
| Keystore round-trip, same-key upgrade, no credential diagnostics | Not tested | Not tested | Not tested | Not applicable |
| Separate Child account, catalogue and direct-play denial, PIN | Not tested | Not tested | Not tested | External apps outside boundary |
| Film, series/season/episode, search, posters and exact hero | Not tested | Not tested | Not tested | Android input only |
| 1080p/4K MKV, HEVC/HDR, audio, subtitles, seek, actual progress | Not tested | Not tested | Not tested | Android input only |
| Gallery, idle entry, exit key, no entry during other-app playback | Not tested | Not tested | Not tested | Native Art Mode separate |
| Doorbell AND person triggers, duplicate suppression, failure state | Adapter to test | Adapter to test | Adapter to test | Bridge prerequisites outstanding |
| Camera over video, pause/focus/volume/return and standby | Adapter to test | Adapter to test | Adapter to test | Model-specific handoff to prove |
| Alexa real due text, latency, no focus steal, Ring precedence | Bridge to configure | Bridge to configure | Bridge to configure | No native overlay claim |
| Xbox wake + exact HDMI action | Owner HA script to supply | Hidden | Hidden | Not applicable |
| Native TV Plus and Art Mode prior-state return | Hidden | Hidden | Owner HA scripts to supply | Native mode tests required |
| Fixtures, UK dates/DST, postponement, no score spoilers | Calendar to configure | Calendar to configure | Calendar to configure | Android input only |
| AirPin setup, receiving, reboot/sleep behaviour | If installed | Manual setup adapter | If installed | Not claimed |
| Local voice/remote microphone and guarded commands | Adapter to test | Adapter to test | Adapter to test | Not claimed |
| OpenAI realtime media discovery | Relay not implemented | Relay not implemented | Relay not implemented | Not claimed |
| VPN ownership/exit, DNS/IPv4/IPv6 drop/wake leaks and LAN | Live TV blocked | Live TV blocked | Live TV blocked | TV Plus separate |
| Amazon custom album Now Playing | API access unproven | API access unproven | API access unproven | Not claimed |
| Model-safe optional cleanup and independent rollback | Not integrated | Not integrated | Distinct streamer scope | Native Tizen scope separate |

Before testing, record exact model, firmware, OS, free storage/RAM, remote type, network isolation and original Home behaviour privately. Identify the Frame streamer and its input. Configure restricted media users, actual Ring entities/live stream, reminder source, fixture calendar and owner-reviewed room scripts. Never publish addresses, tokens, family art, camera snapshots or serial numbers in public issues.

For each result record APK hash, device/firmware, start state, steps, observed result, timing, recovery outcome and evidence location. A pass on one TV never promotes the others. Missing hardware connectivity blocks deployment, not development.

Release gates remain open for full visual handoff/motion polish, offline catalogue caching, physical-TV performance, reliable watch-state round-trip, boot/sleep alert persistence, native Samsung transitions, Amazon access, realtime voice relay, VPN enforcement and cleanup. Do not describe this preview as a complete household release while any P0 gate is open.

Catalogue browsing now pages 24 items at a time and includes Genres, limiting poster memory while retaining access to the full server catalogue. General assistant requests remain disabled pending a scoped discovery relay; no unconstrained conversation agent can launch Live TV indirectly.
