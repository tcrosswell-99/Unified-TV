# START HERE — Unified TV v3.4 Claude package

1. Read `Unified-TV-Specification-v3.4.md` (**short authoritative index**).
2. Every task loads `modules/00_Core_Product_and_Decisions.md`, `modules/03_Safety_Security_Contracts.md`, `modules/10_Testing_Evidence_and_Agent_Workflow.md`.
3. Load only the additional modules in the index's sprint/task matrix. Do not ingest the full historical archive for routine coding.
4. Read `CLAUDE_BUILD_PLAN_v3.4.md` for the **single** gate order and division of agent/owner work.
5. Inspect repository `AGENTS.md`, actual PR #9, manifests, signing and `docs/ACCEPTANCE_REGISTER.csv`. That repository CSV is the only live status record. The bundled template is **not** an update to that file.
6. Preserve the existing preview; new Android launcher gets separate package ID and durable signing. Do not generate keys, select HOME, uninstall apps or run undocumented Samsung native writes without appropriate owner permission.
7. Cloud agent cannot physically test TVs. Provide exact owner-run commands, evidence requests and rollback steps; don't claim hardware success without evidence.
8. The six palette and five wallpaper references are in `references/`. They are design references, not automatically licensed production artwork.
9. `archive/` is read-only historical context; v3.4 modules are normative. Report historical scope gaps instead of treating archived contradictions as active instructions.

## v3.4 module loading rule

For **any Settings, onboarding, profile, service configuration or discovery task**, also load `modules/12_Settings_Onboarding_and_Configuration.md`. Do not load it for unrelated rendering/manifest tasks unless settings state is touched. Keep master index, Core, Safety and Testing as always-read. Do not infer hardware support from GitHub examples; test on the physical devices.

**v3.4.3 utility pill amendment:** The top-right Home utilities must be a compact frosted-glass pill with minimalist text-free white AirPlay status, NordVPN state, AI voice assistant and Settings glyphs. This supersedes older country-flag/ChatGPT text or icon-group guidance. See `modules/05_UX_Motion_Design.md` UX-UTILITY-01–05.


**v3.4.4 binding amendment:** Use `references/approved-home/Unified-TV-Approved-Home-2026-10-25.jpg` as primary Home visual reference. Dock label setting defaults to **Focused only** (not Always); Adults/Kids may independently choose Always or Hide via Settings → Appearance & Home → Dock & Icons. Read UX-ICON-05/06, UX-VISUAL-01/02 and SET-026 before any dock or Settings change. The approved image's always-visible labels and Disney+/BBC wordmarks do **not** override these requirements.

## v3.4.5 onboarding update

New requirements: **Build My TV** wizard for optional app/service selection, beginning with Music Yes/No and supported provider choices. Detect installed apps, offer trusted native store handoff only on verified platforms, recheck after return, and let users assign to the editable dock. SmartTube uses an original minimalist YouTube-inspired play glyph, distinct from official YouTube. See SET-BUILD-01–08 and UX-ICON-07. The product must work with no optional integrations installed.


## v3.4.6 changes (read first)

Default dock is **six**, optionally **eight** under Settings → Appearance & Home → Dock & Icons; `Focused only` labels remain default. Detect music apps before asking: one installed → auto-configure; multiple installed → ask user which is default and whether one/both/all are displayed; none → optional provider setup; unknown → manual selection. No silent installs or store bypass. See v3.4.6 changelog and modules 05/12. No code implementation or real-TV test is claimed.


## v3.4.7 — READ THIS FOR ANY SETTINGS WORK

Load `modules/13_Settings_Liquid_Glass_Overlay_and_Screen_Design.md` **in addition to module 12 and UX**. It is the binding screen-by-screen design and supersedes the earlier two-column/split Settings presentation. Settings is a ~50% **right-side overlay over unmoved Home**, expandable to 70–80%, with original Liquid Glass-inspired materials and remote-first focus. Do not confuse an internal card grid with Home reflow. See `V3.4.7_SETTINGS_GLASS_CHANGELOG.md`.
