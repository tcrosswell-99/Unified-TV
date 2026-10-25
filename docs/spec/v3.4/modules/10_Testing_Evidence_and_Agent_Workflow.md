# Testing, evidence and agent workflow — v3.4

## Single status source

The **repository's** `docs/ACCEPTANCE_REGISTER.csv` is the **only live feature/acceptance register**. The developer ZIP includes a `templates/ACCEPTANCE_REGISTER_TEMPLATE.csv` to bootstrap/compare **only if the repository does not already contain it**. Never overwrite an existing repository register with a stale exported copy. `FEATURE_STATUS_AND_ACCEPTANCE_v3.2.md` is archive-only.

One status vocabulary: `not_started`, `implemented_unverified`, `test_passed_nonphysical`, `physical_verified`, `blocked`, `unsupported_verified`. Evidence level is a **separate column**, not a competing status: `none`, `source_review`, `unit`, `emulator`, `CI`, `PC_network`, `physical_TCL`, `physical_Sony`, `physical_Frame`. PR #9's dock editing, Apps grid and NAS paging are **reported built, physical unverified**; audit merge and code before changing live rows. No source access in this document-creation session: do not mark as verified.

Recommended columns: `requirement_id,feature,gate,device,status,evidence_level,source_commit,build_id,app_variant,firmware,remote,test_steps,expected,observed,evidence_link,blocker,owner_action,reviewed_at`. Preserve existing repository columns; migrate intentionally, don't destroy history.

## Gate order (only order)

G0 repository/signing/safety audit → G1 TCL recovery and owner-selected Home → **G2 animated Home + Wallpaper** → **G3 NAS/media** → G4 Sony → G5 Samsung supported experience; G5P separate proposal, no execution → G6 household integrations. Security invariants apply from G0; they are not postponed until G6.

## Who tests

**Agent:** inspect code, implement, run unit/CI, write precise PC commands, fixtures, risk and rollback instructions, review owner-supplied logs. **Owner:** runs `install_preview.py`, `vpn_check.py`, signing/key inspection tools and physical remote/TV checks from Windows PC; explicitly selects default Home after rollback; reports results. **Agent must not invent TV access.** Real sensor tests wait for sensor availability.

## Per-PR completion contract

List requirement IDs, module(s), exact changed files, branch/commit/PR, baseline status, tests and evidence, privacy/signing impact, supported/unsupported platform claims, owner physical test steps, rollback and status register update. A PR may be `implemented_unverified`; only physical evidence closes device-specific gates. Do not run destructive commands or native Samsung Home experiment without separate permission. Two agents must not concurrently rewrite the same manifest, shared state or acceptance register without ownership coordination.

## Agent context budget / anti-collapse

Read `README-Claude-v3.4.md` + `Unified-TV-Specification-v3.4.md` + **Core** + **Safety** + **Testing** every sprint (short invariant set). Then read only the relevant platform and feature modules named by the sprint matrix. Load historical archive only to trace a particular requirement. At the end of each sprint, run cross-module invariants and report any conflict; do not invent precedence rules. If a module is edited, update index, dependency list and affected tests in the same PR.

## v3.4 Settings and decision acceptance

Add `SET-001`–`SET-085` requirement IDs from module 12 to the existing repository CSV **without overwriting** it. Check first-run no-account usability, remote navigation, Home/Library separation, Adults/Kids PIN and scope limitations, Ring motion/person/ding behaviour, smoke/CO preemption, fail-closed VPN after import/reset, discovery without IP entry, app permission failure, settings persistence, schema migration, backup redaction and per-TV capability gating. A UI mockup or simulated Ring camera is not a physical integration pass.
