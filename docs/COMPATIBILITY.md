# Platform compatibility and hardware truth

**Current Android candidate: Preview 0.4.** Two-column Settings, automatic local media-server discovery, encrypted selected folders, embedded DLNA playback trial, five authorised gallery examples and four VPN states. See [feature status](FEATURE_STATUS.md), [complete v2.2 specification](PRODUCT_SPEC.md) and [AI handover](AI_HANDOVER.md). Local Android checks passed; new TCL/Frame hardware acceptance remains outstanding. The latest Apps request allows owner pin/unpin, but that menu is not implemented in this build. It supersedes older fixed-membership requirement text below.


**Latest owner direction, 24 October 2026:** the dock is now a fixed approved set, with rearrangement only. Add/remove/pin and the arbitrary Apps picker are removed. This supersedes earlier editable-shortlist language. The approved shared six are SmartTube, Netflix, Disney+, BBC, Live TV and Music; Sony adds Xbox and the native Frame plan adds Samsung TV Plus. See [Preview 0.3](PREVIEW_0_3.md) for implementation and remaining VPN gates.

**Current native plan:** [Complete specification v2.0 draft](PRODUCT_SPEC.md) selects native Android for Sony/TCL and a native Tizen Web Application for Frame, with embedded Media3/AVPlay and no VLC playback. No external Frame streamer is required. Native Tizen remains unfinished. [TCL hardware evidence](TCL_TEST_2026_10_24.md) records the limited Android smoke pass and remaining gates. Earlier external-streamer and VLC milestones below are superseded by this plan.

**This is a development compatibility matrix, not a list of tested Unified TV releases.** See [Roadmap](ROADMAP.md) for gates and update this page only after recording the model, firmware, build and reproducible result.

| Platform | Current inherited Android code | Planned Unified TV experience | Important limitations |
| --- | --- | --- | --- |
| Sony BRAVIA Google/Android TV | Android launcher and existing CleanYourTV tooling are present | Shared dock, NAS, optional Home Assistant, performance profiling, room-specific Xbox shortcut | Sony firmware/DRM/HDMI and overlay behaviours require model test |
| TCL Google/Android TV | Same Android foundation | Same visuals with lightweight rendering, locally configured connections, optional AirPin LITE research and reversible cleanup | Lower RAM/CPU; stock app removal can break updates/casting; TCL Android TV 11 confirmed from device settings; precise retail model still to identify |
| Samsung Frame native Tizen | **Android APK not supported** | Native Tizen Web Application selected; retain native Art Mode and Samsung TV Plus; implementation unfinished | No promise of third-party overlays above Tizen apps; Ring/source switching and Art Mode return not proven |
| Samsung Frame via Android HDMI streamer | Android code can run on compatible external hardware | Shared dock, streaming apps and optional overlays | Native Samsung features still need TV control/automation bridge |
| Other Android TV / Google TV | Possible, model dependent | Configurable standard launcher | Do not assume manufacturer services or ADB permissions identical |
| LG webOS / other non-Android TVs | Not supported by inherited APK | Future platform-adapter investigation | Separate OS implementation or external streamer required |

## Recent fixes to inherited code: verification status

These changes are checked by code review, unit tests and CI only. **None has been tested on real TCL, Sony or Samsung hardware yet**, so none is a compatibility claim.

| Change | Verified | Not yet verified |
| --- | --- | --- |
| `launcher-ui/` memory bounding and crash-path fixes | Code review, CI build | Behaviour and memory on TCL and Sony |
| Add-on fixes (for example `addons/frigate-glance`) | Code review, unit tests where the add-on has them | On any real TV |
| Launcher on Android 10 only applies a theme written by the theme tools | Code review, unit tests | On a real Android 10 TV |
| Recovery records tied to the TV's identity; restore stops without a record unless `RESTORE_WITHOUT_RECORD=1` | Python tests against a fake TV | A full disable and restore on real hardware |

## Planned household modules

The [Home view](HOME_VIEW.md) (optional Home Assistant alerts: doorbell, reminders, deliveries, sport, bins) is **Planned** on every platform. On native Samsung Tizen, no overlay or Art Mode interruption is claimed.

## Household-specific capability flags

A room profile describes **capabilities**, not a different visual design. Reference examples:

- `sony`: Xbox on an HDMI input, optional Home Assistant control, OLED-sensitive art handling.
- `tcl`: reduced graphics workload while retaining the same layout and actions.
- `frame`: Samsung TV Plus native access, original Samsung Art Mode, speaker/audio routing as independently verified.

Core apps are user-configurable. No user should need Ring, Alexa, a NAS, NordVPN or ChatGPT for the launcher to start.

## Release status vocabulary

- **Tested:** verified on specified model and firmware with reproducible steps.
- **Experimental:** prototype works in narrow conditions; failure/recovery documented.
- **Planned:** design agreed, integration not built or not proven.
- **Unsupported:** operating system does not permit this function by the current approach.

Always report genuine model/firmware, failure modes, and steps to restore the factory UI. Never publish real household IP addresses, account tokens, camera snapshots, MAC addresses or serial numbers.

Preview 0.2 implementation and remaining gates: [status and limitations](PREVIEW_0_2.md). Only the specific TCL smoke journeys in the dated report have hardware evidence; integration gates remain open.

Configured Android overlays and HA script adapters are experimental. They do not establish native Tizen overlay support or passing Ring/TV Plus/Art Mode gates. See [all-TV tests](ALL_TV_TEST_PLAN.md).
