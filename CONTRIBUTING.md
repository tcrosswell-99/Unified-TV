# Contributing to Unified TV

Thank you for helping make different televisions feel like one familiar, calm entertainment experience.

**Status:** this project is in early development. It is an independent fork; changes to Unified TV should be proposed here, while fixes to the original CleanYourTV should generally also be shared with its maintainers.

## Before contributing

Read [README.md](README.md), [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md), [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md), [docs/ROADMAP.md](docs/ROADMAP.md) and [CREDITS.md](CREDITS.md).

Using a coding agent (Claude Code, Codex, Gemini CLI or similar)? It should follow [AGENTS.md](AGENTS.md), which `CLAUDE.md` and `GEMINI.md` import. AGENTS.md also lists the checks CI runs and the inherited contributor workflows.

The non-negotiable product rule is: **Content first, underlying technology hidden.** Children, guests and family members must get a simple TV, not an administration dashboard.

## Useful contributions

- **Design:** original, copyright-cleared 16:9 screens and motion studies with app/content-matched artwork.
- **Android TV:** remote focus latency, image caching, low-memory handling, accessibility and correct app handoff.
- **TV support:** verified model, firmware, commands and restore steps, including Samsung/Tizen and streamer limitations.
- **Media:** NAS cataloguing and correct film/episode playback; never put paths, shares or credentials into examples.
- **Integrations:** genuinely optional Ring, Alexa, music, VPN and voice controls with meaningful offline and error states.
- **TV cleanup:** device-specific, consent-based, reversible improvements with before/after evidence, not untested package removals.

## Pull request checklist

1. Create a small focused branch rather than changing several unrelated systems.
2. Describe the behaviour, platform, devices tested, firmware and a reproducible outcome.
3. Attach screenshots/video **only when you own or have permission to share them**. Remove account names, private photos, notifications and network/serial identifiers.
4. Describe the old behaviour, new behaviour, failure handling, return path and reduced-motion result.
5. Run the checks relevant to your change. If you cannot run hardware tests, explicitly say **Not tested**.
6. Provide original source licence and attribution details for any third-party contribution.
7. Never auto-enable ADB, persist admin debugging, collect personal data, expose local credentials, or automatically disable system apps.
8. For a cleanup change, demonstrate a model-specific **restore procedure that works without Unified TV launching**.
9. For Ring, VPN, Samsung Art Mode, speaker control or overlay changes, provide real-device proof before claiming they work.

**Useful labels** once Issues are enabled: `bug`, `enhancement`, `needs-hardware-test`, `android-tv`, `samsung-tizen`, `ux`, `documentation`.

## Testing and releases

The current source is still largely upstream CleanYourTV UI. Check existing build instructions under `launcher-ui/` and original launcher's `launcher/` separately. Before a pull request, run the Python suite as CI does: `python3 -m unittest discover -s tests -v` (the build, lint and APK size checks are listed in [AGENTS.md](AGENTS.md#checks-ci-runs)). Many documentation tests read the inherited [README.cleanyourtv.md](README.cleanyourtv.md), so update that page, not README.md, when you change an inherited tool, TV list or theme. The future Unified TV release must carry its own verified build, installation, rollback and package-signing instructions. Do **not** distribute unofficial APKs as a completed Unified TV release.

No personal Home Assistant URLs, Ring snapshots, NAS share details, API keys, music account data, family photographs or private GitHub tokens should ever be committed. Use sample configuration and secrets outside the repository.

## Community conduct

Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Report security issues privately as described in [SECURITY.md](SECURITY.md).

This repository is not the upstream support channel. Original CleanYourTV guidance remains in [README.cleanyourtv.md](README.cleanyourtv.md); Matheus Caldeira's inherited README is archived at [docs/upstream/CLEANYOURTV_UI_README.md](docs/upstream/CLEANYOURTV_UI_README.md).

Contributor implementation rules: [AGENTS.md](AGENTS.md).
