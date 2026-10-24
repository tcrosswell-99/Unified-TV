# Unified TV roadmap

**Current native plan:** [Complete specification v2.0 draft](PRODUCT_SPEC.md) selects native Android for Sony/TCL and a native Tizen Web Application for Frame, with embedded Media3/AVPlay and no VLC playback. No external Frame streamer is required. Native Tizen remains unfinished. [TCL hardware evidence](TCL_TEST_2026_10_24.md) records the limited Android smoke pass and remaining gates. Earlier external-streamer and VLC milestones below are superseded by this plan.

**Current phase: foundation and technical validation.** Items listed here are not shipping features until reviewed and tested. Milestones follow [Product Spec](PRODUCT_SPEC.md); hardware acceptance must precede release claims.

## 0. Keep the inherited baseline safe

- Preserve upstream CleanYourTV and Free My TV copyright and licence texts.
- Record how the current Android launcher builds and tests. Verify upstream CI, restoration tooling and licence files.
- Keep a dedicated, reviewed upstream sync process; no automatic merging of package deletion/disable rules.
- Capture TV model and firmware details **locally**, without posting network addresses, secrets or identifying screenshots.
- Publish original design tokens and approved artwork sources, not copies of third-party visual assets.

**Completion:** clean Android test build, verified recovery notes, documented starting performance, public docs and a clear difference between inherited and planned features.

## 0.5 Windows-to-TCL pilot and local setup

- CI-compile and verify standalone `io.unifiedtv.preview` APK from GitHub Actions (separate from existing HOME application).
- Use the confirmed TCL Android TV 11 hardware and private owner-supplied address. Inspect actual Developer options before selecting Android 11 network debugging, USB ADB or approved offline sideload; **do not assume newer TV pairing mode**.
- Read model/version first, install only with express approval, test glass dock, app focus and edit controls, then uninstall preview and verify normal Google TV Home.
- Create **Settings > Connections** with on-TV Home Assistant and NAS/Jellyfin host/port, secure credentials, Test/Save/Forget and offline resilience.
- Test installed **AirPin LITE** independently for start-on-boot and AirPlay discovery, with truthful icon state and no background-app promises.
- Measure real input latency, memory and playback before any optional bloatware cleanup.

**Gate:** confirmed successful build, non-HOME manifest, safe install/rollback, tested local configuration and no factory package changes. See [TCL pilot guide](LOCAL_CONNECTIONS_AND_TCL_TESTING.md).

## 1. Technical release gates

- **Ring:** prove doorbell press and person-detection triggers with live video, without remote interaction, over Home and artwork. Test interrupted viewing and exact return on Sony, TCL and Samsung Frame native Art Mode. No claim of success until proven on all target platforms.
- **VPN:** Live TV cannot launch unprotected or leak packets after a tunnel drop. Verify Android VPN controls and local NAS/Home Assistant access.
- **Cross-TV architecture:** shared Android launcher on Sony, TCL and external Samsung HDMI streamer. Prove native Samsung Art Mode/TV Plus transition pathways and return states separately.
- **Safe cleanup:** baseline and restore on each model before any optional package disabling.

**Completion:** real-device evidence, timestamps, version/firmware notes and pass/fail matrix.

## 2. Premium core experience

- Design and build the single glass dock, correctly matched app and NAS context artwork, genuine focus lift, quiet 24-hour clock, light/dark mood and remote movement.
- Include a remote-only installed Apps library, pin/unpin Home shortcuts and hold OK to Move with persistence per TV, sensible dock overflow and optional child edit protection.
- Render the same layout with high/balanced/lightweight graphics profiles. Benchmark TCL and ageing Sony.
- Add opt-in shared/room-specific app ordering, BBC shortcut, Sony Xbox action and Samsung TV Plus bridge.
- Build NAS poster strip and `View All` browse, then prove direct VLC playback. Do not claim continue-watching without actual playback progress tracking.
- Add personal artwork gallery with a completely uncluttered native Frame Art Mode.

**Completion:** reference screen matrix passes on all target displays; no frame drops or misleading imagery during fast focus changes.

## 3. Optional household modules

- Ring and Alexa reminders via trusted Home Assistant events, with clear overlay priority.
- Tottenham matchday widget without scores, hidden on other days.
- NordVPN truthful status and strict Live TV launch restrictions.
- Permission-based TV Performance & Cleanup. Android disables must be reversible; native Tizen changes only where independently supported.
- Spotify-like/Amazon Music album view as permitted by the service's playback API and devices; do not promise full Amazon catalogue integration yet.
- Optional ChatGPT live voice with UK voice, microphone indicator, scoped device actions and server-side credentials.

**Completion:** each module has a disable switch, explicit test matrix, documented privacy and failure state.

## 4. Community preview and subsequent releases

- Test on reference Sony, TCL and Samsung devices. Mark features **Tested**, **Experimental**, **Planned** or **Unsupported** by model.
- Publish reproducible Android builds and unsigned/signed distinction, release notes and tested install/restore instructions.
- Invite opt-in bug reports and design contributions. Do not claim a universal Tizen launcher if one is not shipping.
- Propose upstream improvements separately where useful.

## Priority policy

Safety, playback and responsive navigation come before visual flourishes. Ring interruption and VPN fail-closed behaviour are absolute gates for the household reference implementation. The public project must remain useful with all optional household integrations disabled.

Preview 0.2 implementation and remaining gates: [status and limitations](PREVIEW_0_2.md). This does not advance hardware gates to Tested.
