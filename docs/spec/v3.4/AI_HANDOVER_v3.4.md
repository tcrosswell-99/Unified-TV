# AI Handover v3.4 — concise agent entrypoint

**Current truth:** documentation-only v3.4; no repository write or real TV test was performed to produce this pack. Prior v3.2 feature matrix was based on older v0.4 evidence and must not be treated as current. User reports PR `tcrosswell-99/Unified-TV#9` has editable dock, Apps grid and NAS paging; **inspect** branch/merge/commit and then update `docs/ACCEPTANCE_REGISTER.csv` with `implemented_unverified` unless physical proof is supplied.

**Read order:** `README-Claude-v3.4.md` → `Unified-TV-Specification-v3.4.md` → always-load Core/Safety/Testing → only relevant modules → `CLAUDE_BUILD_PLAN_v3.4.md`. Existing repository `AGENTS.md` also applies, but conflicts with v3.4 should be reported to owner, not silently resolved.

**Do first:** `git status`, branch, PRs, manifest/package IDs, existing preview signing, build/test commands, repository acceptance register, Android 11 TCL renderer constraints. Do not reset changes. Check existing tests before adding frameworks. Prepare owner-assisted physical command checklist. Create key custody plan; **do not generate or expose private keys without permission**.

**Current task priorities:** correct emergency event UI; split preview and launcher into distinct installable package IDs; reconcile status and modules; G2 Animated Home and Wallpaper. G3 Media follows, preserving work already built. Separate Samsung G5P proposal only.

**Required PR report:** touched requirements/modules, code paths, tests, actual evidence level, physical test commands and expected outputs, owner action, privacy/signing review, rollback, CSV register changes. Do not claim cloud TV access. All eight owner decisions in Core are closed.

## New confirmed decisions

Minimal Home, separate Library; Adults/Kids only; universal search later; Ring motion silent, person toast, doorbell camera attempt; NAS self-discovery without IP entry; comprehensive consumer Settings. Full controls, defaults, failure states, test IDs and deferred capability gates are in module 12. These requirements are **not proof of implementation**.


## v3.4.4 implementation instruction

Owner confirmed the Home screenshot as primary visual reference and **Focused only** as the dock-label default. Audit the existing dock label rendering, then implement enum-backed per-profile setting and accessible Settings preview. Do not replace mandatory minimalist glyphs with text-only tiles. Compare TCL rendered screenshot against bundled approved reference, record diffs and real-TV evidence in the repository acceptance register; do not claim hardware verification without owner-supplied evidence.

## Owner update v3.4.5 — Build My TV

Read SET-BUILD-01–08, UX-ICON-07 and UX-BUILD-01. Implement a skippable entertainment category onboarding with installed-app detection, explicit platform-store install handoff and rescan on return, and six-slot dock selection. Music Yes/No is the initial demonstration. SmartTube must have an original minimalist YouTube-inspired play glyph, visually distinct from official YouTube. The approved Home image remains authoritative except for Focused-only labels and glyph refinements. Do not claim Tizen store integration or app availability without evidence.


## v3.4.6 — Mandatory owner decisions

Read UX-DOCK-08–10 and SET-BUILD-09–11 / SET-027 before changing the dock or onboarding. Six slots default, eight optional. **One installed music app: configure without asking. Multiple: ask user for default and whether one, both or all should be shown.** Never silently select among multiple or install apps. Preserve Focused-only labels and approved Home aesthetic. Audit existing code, implement platform-capability-aware flows, then update the *existing* `docs/ACCEPTANCE_REGISTER.csv` without replacing it. Physical-TV validation is pending owner testing.


## v3.4.7 owner decision — Settings visual system

Settings must slide from right **OVER** stationary full-screen Home (~50% default, ~75% for complex tasks); never push/reflow/shrink Home. Glass material inspired by Apple Liquid Glass: dark translucent frost, fine edge light, subtle depth, high contrast and calm motion. All ten screens and interactions are detailed in `modules/13_Settings_Liquid_Glass_Overlay_and_Screen_Design.md`. Audit current code and adapt incrementally; no physical TV verification is claimed.
