# What is working today?

Unified TV is an independent **early development fork**. No new Unified TV release or cross-platform implementation has been published. Changes are recorded in the [changelog](../CHANGELOG.md).

- **Inherited source present:** CleanYourTV UI Android launcher, original CleanYourTV launcher, tools, add-ons and associated licences. You can build and use these today under their [original instructions](../README.cleanyourtv.md); they are not the Unified TV design.
- **Continuous integration:** the Python suite (documentation checks and the cleanup tools run against a fake TV), the launcher build with its size gate, and the launcher-ui build run on every pull request.
- **Recent fixes to inherited code** (launcher-ui memory and crash paths, add-ons, the Android 10 theme check, recovery records tied to the TV's identity): code-checked and covered by tests where possible, **not yet tested on TCL, Sony or Samsung hardware**. See [Compatibility](COMPATIBILITY.md#recent-fixes-to-inherited-code-verification-status).
- **Documentation present:** product and UX specification, design system, [Home view design note](HOME_VIEW.md), [feature ideas](IDEAS.md), credits, roadmap, setup and recovery cautions.
- **Not yet built or verified as Unified TV features:** harmonised app order across every TV, redesigned glass dock, contextual media artwork, NAS/VLC library, Home view alerts, Ring live-view interruption, person detection, Alexa toasts, ChatGPT voice, NordVPN fail-closed playback, Sony Xbox control, Samsung native TV Plus integration and Frame Art Mode transition.
- **Not yet tested:** actual reference television hardware, local Home Assistant, Synology, Samsung audio or package-cleanup improvements.

Do not report these planned features as available in a build. See [Roadmap](ROADMAP.md) and [Compatibility](COMPATIBILITY.md) for how we will validate each one.
