# What is working today?

Unified TV remains an experimental development fork, not a supported household release. Changes are recorded in the [changelog](../CHANGELOG.md). The only live status record is [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv); [FEATURE_STATUS.md](FEATURE_STATUS.md) describes what each feature does.

- **Preview 0.5 "The Viewing Experience"** is built and unit-tested in source, and **not yet tested on any TV**. It is two apps from one codebase: the preview (`io.unifiedtv.preview`, no HOME entry, unchanged identity) and the Unified TV launcher (`io.unifiedtv.launcher`, an opt-in Home entry, signed with the owner's durable key: [SIGNING.md](SIGNING.md)). A launcher review build installs beside both: see [OWNER_TESTING.md](OWNER_TESTING.md).

## Unified TV Android Preview

- **Android Preview 0.4** (`io.unifiedtv.preview`, a separate app with no HOME intent) is published as a [test prerelease](https://github.com/tcrosswell-99/Unified-TV/releases/tag/android-preview-v0.4.0). It has source for the curated glass dock with remote reorder, two-column Settings, private remote-based Connections, profiles and PIN, Home Assistant and DLNA discovery, catalogue browsing, an embedded Media3 playback trial, the gallery, the NordVPN handoff and experimental Home Assistant camera and reminder adapters.
- Its 37 Android tests, lint and APK builds pass. Compilation is not device acceptance: the 0.4 build has **not** been installed on a TV, and real NAS playback, Ring, reminders, verified VPN protection, Amazon Music, OpenAI voice and Samsung remain open. Live TV stays blocked. See [FEATURE_STATUS.md](FEATURE_STATUS.md) and the [all-TV test plan](ALL_TV_TEST_PLAN.md).
- Earlier preview baselines: [0.2](PREVIEW_0_2.md), [0.3](PREVIEW_0_3.md). Adapter configuration and source references: [household adapters](HOUSEHOLD_ADAPTERS.md).

## Inherited CleanYourTV code

- **Present:** the original CleanYourTV launcher, CleanYourTV UI, tools, add-ons and their licences. You can build and use these today under their [original instructions](../README.cleanyourtv.md); they are not the Unified TV design.
- **Recent fixes** (launcher-ui memory and crash paths, add-ons, the Android 10 theme check, recovery records tied to the TV's identity): code-checked and covered by tests where possible, **not yet tested on TCL, Sony or Samsung hardware**. See [Compatibility](COMPATIBILITY.md#recent-fixes-to-inherited-code-verification-status).
- **Continuous integration** runs the Python suite (documentation checks and the cleanup tools against a fake TV), the inherited launcher build with its size gate, the standalone preview APK and the launcher-ui build.

## Documentation

Product specification ([v2.2](PRODUCT_SPEC.md)), design system, [Home view design note](HOME_VIEW.md), [feature ideas](IDEAS.md), credits, roadmap, setup and recovery cautions, and the [AI handover](AI_HANDOVER.md) for continuing work.

## Not yet verified as Unified TV features

Same apps in the same order on every TV, the premium poster-first library, Home view alerts, Ring live view on a press or a detected person, Alexa reminder toasts, ChatGPT voice, NordVPN fail-closed Live TV, Sony Xbox control, the native Samsung Frame (Tizen) app, Samsung TV Plus and Frame Art Mode transitions. Reference Sony, TCL and Frame hardware, local servers, real media codecs and VPN packet behaviour have not been tested by this work, and no television's existing launcher has been changed.
