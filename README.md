# Unified TV

**Android Preview 0.4:** two-column Settings, automatic DLNA setup/direct-play trial, five gallery examples, four VPN states and official Amazon Music handoff. [Built and remaining features](docs/FEATURE_STATUS.md) · [Complete specification v2.2](docs/PRODUCT_SPEC.md) · [AI handover](docs/AI_HANDOVER.md). Local checks pass; new physical-TV acceptance is outstanding. The latest owner request restores editable Apps, which is not implemented yet. Older introductory roadmap text below is historical.

**Current native plan:** [Complete specification v2.0 draft](docs/PRODUCT_SPEC.md) selects native Android for Sony/TCL and a native Tizen Web Application for Frame, with embedded Media3/AVPlay and no VLC playback. No external Frame streamer is required. Native Tizen remains unfinished. [TCL hardware evidence](docs/TCL_TEST_2026_10_24.md) records the limited Android smoke pass and remaining gates. Earlier external-streamer and VLC milestones below are superseded by this plan.
### Different TVs. One familiar experience.

A family-first, independently maintained smart TV launcher project that puts the programmes, films, music and games you love at the centre. The aim is a calm, cinematic home screen that feels consistent across televisions from different manufacturers, while letting each room have its own apps and devices.

**Content first, underlying technology hidden.**

> **Project status: early development.** This is a public fork of [CleanYourTV UI](https://github.com/matheus-caldeira/cleanyourtv-ui), preserving its existing Android TV code and history. The Unified TV features described below are a **design and development roadmap**, not an installable finished product. There is no verified Unified TV release yet.

## Why Unified TV?

A household can have a Sony in the living room, a TCL elsewhere and a Samsung Frame in another room. Each television has a different home screen, input flow and set of recommendations. Unified TV aims to give the family **the same simple navigation and visual language everywhere**, without pretending every device has the same capabilities.

- **One clean, editable dock:** favourite streaming apps that family members will be able to pin, remove or reorder with the remote. No adverts or endless content rows. **Planned for Unified TV.**
- **Content-aware atmosphere:** focus Netflix, BBC or a film in your own library, and the full-screen artwork changes to match that selection. No unrelated wallpaper behind a highlighted tile.
- **Personal films and television:** browse your own NAS collection through beautiful posters and a simple `View All` library. Embedded Media3 on Android and AVPlay on Tizen are the selected playback engines; codec and household acceptance remain open.
- **Room-specific shortcuts:** an Xbox tile only on the television connected to the console; Samsung TV Plus only where it is installed. The layout stays familiar.
- **Less to learn:** designed for children, visitors and family members who just want to watch something.

The visual direction draws on the *principles* of contemporary tvOS motion and material design, alongside the clean, gallery-like restraint of [CleanFrame by Nathan Clark](https://github.com/nthnclrk/CleanFrame). It is an **original interface**, not an Apple product, replica or affiliated project.

## What we want to build

| Experience | Intended behaviour | Unified TV status |
| --- | --- | --- |
| Premium home screen | One translucent dock, contextual full-screen artwork, immediate remote focus and day/night refinement | **Planned redesign** |
| Favourite apps | SmartTube, Netflix, Disney+, BBC iPlayer, Live TV and Music; configurable per household | **Planned configuration** |
| Personal NAS library | Poster artwork, films, series, search and `View All`; embedded native playback | **Planned integration** |
| Ring doorbell | Optional automatic live camera takeover when pressed **or a person is detected**, even from artwork mode | **Research and real-TV tests required** |
| Alexa reminders | Optional discreet text toast over playback, where a supported event and overlay are available | **Research and testing required** |
| Voice assistant | Optional ChatGPT voice to search local content, open apps and answer questions, with British English voice preference | **Planned; API, microphone and permissions testing required** |
| VPN awareness | Tiny truthful NordVPN connected/off indicator and country flag; fail-closed Live TV access | **Planned; network safety tests required** |
| Music | Album-led, calm Now Playing experience using an existing music subscription if playback access permits | **Research required for Amazon Music** |
| Matchday | Optional discreet Tottenham fixture on matchdays only, without score spoilers | **Planned personalisation** |
| Art mode | User-selected artwork and family photos; native Samsung Frame Art Mode stays visually unmodified | **Research and device-specific testing required** |
| Performance and cleanup | Optional device scan, before/after benchmarks and reversible cleanup | **Existing Android tooling inherited; Unified TV safety flow planned** |

**Nothing in the table implies those integrations are already implemented.** The repository contains the inherited CleanYourTV UI application, original CleanYourTV tools and a separate experimental preview. We will preserve a usable Android baseline while developing and testing changes.

## One design, different hardware

- **Sony and TCL running Android/Google TV:** the intended native launcher platform. TCL and older Sony hardware receive the same design with lighter rendering where necessary.
- **Samsung Frame (Tizen):** a native Android APK **cannot** replace Tizen Home. The current approach is an external Android/Google TV device for the shared launcher, while preserving Samsung's native Art Mode and Samsung TV Plus. Switching automatically between native TV features and an external launcher needs model-specific tests.
- **Other brands:** possible only through supported platform adapters or appropriate external devices. No claim of universal installation or bloatware removal.

A Ring doorbell **must** interrupt wallpaper/art mode on the reference deployment and show live video automatically. This is a release gate, **not** a claim that Samsung already permits third-party overlays. An alternative source/input transition needs to be proven on hardware. See [compatibility](docs/COMPATIBILITY.md).

## The TV your family knows

The initial household reference uses six familiar choices: **SmartTube · Netflix · Disney+ · BBC · Live TV · Music**. A Sony-only Xbox action powers on its console and changes HDMI input. A Samsung-only TV Plus action should open the native service. Pressing **Down** reveals your own films and series, instead of leaving posters permanently covering the home screen.

All these are *configurable examples*, not compulsory apps. The planned **Apps** screen lets viewers pin and rearrange dock shortcuts independently for each TV; it never uninstalls an app. See [app customisation](docs/APP_CUSTOMISATION.md). People without a NAS, Ring, Alexa, Xbox, VPN or AI subscription should still have a lovely, responsive launcher.

## Privacy and performance

Our intent is no advertising, no sale of household data and no mandatory analytics. Personal photos, media paths, Home Assistant URLs and API credentials stay out of Git and public issue reports. Integrations are opt-in and should fail quietly when disconnected.

The optional **TV Performance & Cleanup** journey is separate from installation. On supported Android models, it must inspect the device, explain every proposed change, save original package states and offer independent rollback. **Never automatically debloat a user's television.** Tizen has different limits; native Samsung package removal is not promised.

## First Android TV preview (experimental)

An isolated, non-HOME **Unified TV Preview** APK (package `io.unifiedtv.preview`) is being developed to test the glass dock, neutral artwork, contextual focus and D-pad app personalisation before modifying any TV's existing Home. See [prototype details and test limitations](docs/FIRST_ANDROID_PREVIEW.md). This is not yet a supported Unified TV release or an operational Ring/NAS/VPN integration.

See [preview 0.2 implementation and limitations](docs/PREVIEW_0_2.md), including private Connections, catalogue and embedded playback trials. These are not yet hardware-validated household features.

## Starting today

This is a **development fork**, not a finished installer. For now, see the [original CleanYourTV UI instructions](docs/upstream/CLEANYOURTV_UI_README.md) if you are evaluating the *inherited* Android code. Those instructions and scripts are inherited from their authors and do not install the future Unified TV experience.

Read the [public product specification](docs/PRODUCT_SPEC.md), [design contract](docs/DESIGN_SYSTEM.md), [platform compatibility](docs/COMPATIBILITY.md), [roadmap](docs/ROADMAP.md) and [safe installation/recovery notes](docs/INSTALLATION_AND_RECOVERY.md).

Please **do not apply package changes or use ADB on a household TV without model/firmware-specific research and a verified restore path**. Testing in an emulator or spare device should come first.

## Project and community

Unified TV is being built for a real family first, with the source openly available for anyone who wants to experiment, adapt it to their home or help improve it. Contributions, suggestions and real device test reports are welcome through [GitHub Issues](https://github.com/tcrosswell-99/Unified-TV/issues) and [Discussions](https://github.com/tcrosswell-99/Unified-TV/discussions).

See [Contributing](CONTRIBUTING.md), [Security](SECURITY.md) and [Credits](CREDITS.md). No final Unified TV APK or supported-devices list has been published yet.

## Credit and licence

This repository is a **GitHub fork** of [CleanYourTV UI by Matheus Caldeira](https://github.com/matheus-caldeira/cleanyourtv-ui), built on [CleanYourTV by Gathin](https://github.com/Gathin23/cleanyourtv). The inherited UI also includes code from [Free My TV](https://github.com/simbasjungles/free-my-tv). The original history and licence files remain in the repository, including [MIT licence](LICENSE), [Free My TV licence](launcher-ui/LICENSE.free-my-tv) and [launcher attribution notice](launcher-ui/NOTICE). Please check the path for the Free My TV licence in the underlying source tree when preparing any redistribution.

[TV Detox](https://github.com/Ansub/tv-detox) by Ansub Khan and [CleanFrame](https://github.com/nthnclrk/CleanFrame) by Nathan Clark are credited research and design references. **No code or visual assets from these projects have been imported into Unified TV** at this stage. Any future code integration will preserve the applicable original copyright and licence notices. Details: [CREDITS.md](CREDITS.md).

Unified TV is independent of Apple, Samsung, Sony, TCL, Google, Amazon, NordVPN and the streaming services it launches. Their names and trademarks belong to their owners.

**Different TVs. One familiar experience.**

Inherited documentation: [Unified TV](README.md) · [CleanYourTV English](README.cleanyourtv.md) · [Português](README.pt-BR.md) · [日本語](README.ja.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh-CN.md). These translations describe inherited projects, not the current Unified TV specification.


Contributor implementation rules: [AGENTS.md](AGENTS.md).

Cross-TV evaluation: [implementation and acceptance matrix](docs/ALL_TV_TEST_PLAN.md). Household functions require [private adapter setup](docs/HOUSEHOLD_ADAPTERS.md).
