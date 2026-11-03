# Claude Build Plan v3.4 — one gate order

Read `README-Claude-v3.4.md` and `Unified-TV-Specification-v3.4.md` first. The master index specifies mandatory and task-specific modules. **G0 → G1 → G2 Visual → G3 Media → G4 Sony → G5 Samsung supported + G5P proposal → G6 integrations.** No alternative release-gate order is valid.

| Gate | Deliverable | Agent actions | Owner physical actions | Exit evidence |
|---|---|---|---|---|
| G0 | Audit + key custody + safe rollback plan | Inspect repo/PR #9, manifest variants, installed-signature questions, acceptance register; write signing custody plan; mock safety policy | Run requested redacted signing/PC commands; confirm safe factory launcher recovery steps | Audited IDs, custody plan, risk and command checklist; no new key without approval |
| G1 | TCL default Home after recovery | Split preview/launcher package IDs, test navigation and remote semantics; document installer/rollback | Run `install_preview.py` or repo-specific install script, test remote/stock Home, explicitly choose launcher only after rollback proven | Actual TCL model/remote/build/firmware evidence; safe restore |
| **G2** | **Animated Home + Wallpaper** | Pre-rendered small GPU light fields, immediate 400 ms crossfade, motion settings, curated licensed art, prepared depth masks, simple fallback | Compare on actual TCL, measure responsiveness, idle, reduced motion | AM/WP physical evidence, licensing manifest |
| **G3** | **NAS browse/playback** | Preserve and audit PR #9 code, finish media adapters, source bounds and player | Test selected Synology roots, playback/seek/errors on TCL | Media physical proof, no unauthorised roots |
| G4 | Sony parity | Sony remote/Home/OLED/HDMI adaptation | Identify model/remote and test on Sony | Device-specific evidence |
| G5 | Supported Samsung experience | Signed WGT, Smart Hub/Art Mode/TV Plus preservation | Test signed WGT and physical Frame behaviours | Frame proof |
| G5P | **Separate Samsung Home research proposal** | Research risks/read-only checks/recovery/stop conditions; no native write | Review proposal and separately authorise any future experiment | Proposal only, no implementation claim |
| G6 | Household integrations | Authenticated smoke/CO event coordinator (above Ring), Ring, NordVPN fail-closed, other integrations | Connect/test real sensors only when available; test actual service/device conditions | Physical/integration proof; no certified alarm claims |

**Immediate requested work order:** (1) correct emergency UI: individual alarms/acknowledgements, persistent active indicator, offline distinction and Ring suppression; (2) separate durably signed launcher without uninstalling preview; (3) current docs/register consistency; (4) complete G2 mesh and Wallpaper. G0 signing audit and safety policy may run in parallel with clear file ownership. This work order does **not** imply G6 real detector integration is a prerequisite for G2.

**Status:** repository `docs/ACCEPTANCE_REGISTER.csv` only; `not_started`, `implemented_unverified`, `test_passed_nonphysical`, `physical_verified`, `blocked`, `unsupported_verified`. Evidence level is separate. PR #9 claims are source-reported, not physical pass. Agent must give owner exact Windows PC commands, expected results, capture and rollback. No unattended TV operations.

**Stop conditions:** missing safe rollback, conflicting signatures/data, unapproved key generation, unsupported Samsung writes, leaked secrets, native Art Mode disruption, or competing agents editing shared manifests/state/register. Report blocker rather than forcing progress.

## Settings workstream (cross-gate; no gate reorder)

Implement the Settings shell, schema/migration, local-first storage, safe reset and capability adapter as part of G1/G2 foundation. Add Appearance/Home at G2; Library discovery, metadata and Adults/Kids at G3; platform-specific settings verification at G4/G5; Ring/HA/VPN integration panels at G6. Settings must not block G2 while optional integrations are absent. Read `modules/12_Settings_Onboarding_and_Configuration.md` and register `SET-*` acceptance rows in the repository CSV. Confirmed Home is minimal; Library separate; two profile types; universal search deferred; Ring motion ignored/person toast/doorbell camera; no normal-user manual IP.


## v3.4.4 incremental task

During G2 dock visual work, adopt primary approved screenshot and implement UX-ICON-05/06 + UX-VISUAL-01/02. During Settings foundation, implement SET-026. Tests: default Focused only; switch to Always/Hide; rapid D-pad focus; Adults/Kids independent persistence; restart/migration; accessible focus; edited dock; TCL/Sony/Samsung hardware evidence. Preserve prior gates and safety.

## v3.4.5 implementation sequence — Entertainment Hub Builder

At G1/G2, first audit installed-app discovery, native store launch/deep-link capability and return/resume on each target platform. Implement the optional category wizard and app selection with local state and a capability-filtered catalogue; reuse the existing dock editor. Start with **Music Yes/No → available provider → installed or store handoff → dock choice**, then expand to other categories. Do not delay animated Home G2 for optional provider coverage. Keep no silent installs, no credentials collection, VPN fail-closed, and stock launcher recovery. Test first on TCL preview; collect Sony/Tizen feasibility separately. Log SET-BUILD and UX-ICON-07 acceptance cases in `docs/ACCEPTANCE_REGISTER.csv` only after inspecting the actual repository.


## v3.4.6 — Build sequence

1. Inspect current app discovery and dock preference schemas; document platform detection gaps and migrations.
2. Implement per-profile `dock.capacity` (6 default / 8 optional) and 6/8 responsive dock with accurate Settings preview; no forced app addition.
3. Implement one/multiple/none/unknown installed music decision branches; multiple must prompt for explicit default and one/both/all visibility.
4. Implement safe dock capacity changes, explicit replacement confirmation, Apps-only fallback, no silent install, persistence and accessible D-pad focus.
5. Unit/UI tests, TCL preview comparison, then request Sony/Tizen owner device evidence. Update acceptance register, keeping implementation and physical verification distinct.


## v3.4.7 Settings implementation workstream (respect existing G0–G6 order)

At the Settings gate, read modules 12, 13 and 05. Audit existing Settings before replacing anything. Implement overlay shell + focus restoration and static fallback first, then root navigation, Appearance live preview, Apps/Build My TV and dock editor, remaining ten-category screens behind capability checks, and real-device owner acceptance. **Never reflow Home.** Track GLASS-01–GLASS-12 in repository `docs/ACCEPTANCE_REGISTER.csv` with separate implementation vs physical-test status.
