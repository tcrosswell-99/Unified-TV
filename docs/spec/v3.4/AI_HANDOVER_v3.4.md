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
