# Platform compatibility and hardware truth

**This is a development compatibility matrix, not a list of tested Unified TV releases.** See [Roadmap](ROADMAP.md) for gates and update this page only after recording the model, firmware, build and reproducible result.

| Platform | Current inherited Android code | Planned Unified TV experience | Important limitations |
| --- | --- | --- | --- |
| Sony BRAVIA Google/Android TV | Android launcher and existing CleanYourTV tooling are present | Shared dock, NAS, optional Home Assistant, performance profiling, room-specific Xbox shortcut | Sony firmware/DRM/HDMI and overlay behaviours require model test |
| TCL Google/Android TV | Same Android foundation | Same visuals with lightweight rendering, locally configured connections, optional AirPin LITE research and reversible cleanup | Lower RAM/CPU; stock app removal can break updates/casting; TCL Android TV 11 confirmed from device settings; precise retail model still to identify |
| Samsung Frame native Tizen | **Android APK not supported** | Retain native Art Mode and Samsung TV Plus; use a tested external Android device for shared launcher | No promise of third-party overlays above Tizen apps; Ring/source switching and Art Mode return not proven |
| Samsung Frame via Android HDMI streamer | Android code can run on compatible external hardware | Shared dock, streaming apps and optional overlays | Native Samsung features still need TV control/automation bridge |
| Other Android TV / Google TV | Possible, model dependent | Configurable standard launcher | Do not assume manufacturer services or ADB permissions identical |
| LG webOS / other non-Android TVs | Not supported by inherited APK | Future platform-adapter investigation | Separate OS implementation or external streamer required |

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

Preview 0.2 implementation and remaining gates: [status and limitations](PREVIEW_0_2.md). This does not advance hardware gates to Tested.

Configured Android overlays and HA script adapters are experimental. They do not establish native Tizen overlay support or passing Ring/TV Plus/Art Mode gates. See [all-TV tests](ALL_TV_TEST_PLAN.md).
