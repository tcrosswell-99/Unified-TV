# Core product and owner decisions — v3.4

**Normative.** North star: **Content first, underlying technology hidden.** Calm, original tvOS-inspired remote-first viewing; not an Apple UI clone. The supported targets are TCL Android TV 11 (exact model/remote TBD), Sony 65-inch BRAVIA OLED Google TV (exact model/remote TBD), and Samsung Frame 2021 QE50LS03AAUXXU, firmware T-NKM2DEUC-2310.0 (verify installed firmware). Each TV needs separate acceptance evidence.

## Binding decisions (eight, not six)

| ID | Decision |
|---|---|
| OD-01 | TCL Unified TV is owner-selected default Home **only after** independent stock-Home recovery proof. |
| OD-02 | After G1 TCL Home reliability, **G2 Animated Home + Wallpaper precedes G3 Media**. Preserve already-built media code. |
| OD-03 | Wallpaper defaults to curated, properly licensed, offline-capable artwork; no personal photos imported by default. |
| OD-04 | Depth clock defaults to Auto **only when an eligible image has a verified mask**; otherwise simple/no clock. |
| OD-05 | Samsung Home reassignment: **prepare a separate controlled-test proposal only**. No undocumented native writes without later specific approval. |
| OD-06 | Claude helps identify TCL/Sony model, firmware and physical remote during owner-assisted testing; do not invent mappings. |
| OD-07 | Authenticated smoke and CO alerts are co-equal highest notification priority, **above Ring**. No detectors currently confirmed in Home Assistant. |
| OD-08 | Durable owner-controlled signing and in-place upgrade continuity are release prerequisites; protect key custody. |

**Additional owner-approved refinements (25 October 2026):** Approve sound interruption where supported without assuming control of other apps. Approve a **separate package-ID launcher**, preserving the existing preview. Start with free, explicitly licensed artwork; prepared masks only. Ring presentation is queued/suppressed during an active emergency. Repository `docs/ACCEPTANCE_REGISTER.csv` is the sole live status record. All previous Q1–Q6 decisions are closed; do not ask again.

## Core product scope

Six-item editable dock; Apps browser; native app handoff; content browse/detail; selected Synology roots `/video/Kids`, `/video/Movie`, `/video/TV Show`; wallpaper; quiet original-interface escape; local settings; Samsung TV Plus and native Art Mode preserved. Home Assistant household features, Ring, Live TV with NordVPN, Amazon Music official app handoff, profiles and optional integrations remain scoped to their gates. Never imply that unverified platform capability is available.

## Interpretation

P0 = essential for its *named gate*, not a request to build all P0 features immediately. P1 = later desired. GATE = requires independent feasibility, owner input, permission or physical proof. A repository PR, emulator result or mockup is not a physical-TV pass. Historical material is archived for traceability, not active instructions.

## Confirmed product refinements — v3.4

| ID | Decision |
|---|---|
| OD-09 | **Minimal Home**: six-app dock, ambient background, optional clock/status; Library and Continue Watching are on a **separate** screen, not Home rows. |
| OD-10 | **Adults and Kids only** as initial profile types; last-used profile persists, Kids→Adults PIN when configured. |
| OD-11 | **Universal search deferred** until core launcher, visuals and NAS reliability are demonstrated; catalogue should remain search-ready. |
| OD-12 | Ring **motion → ignore; person → toast; doorbell button → live camera attempt**. Smoke/CO precedence unchanged. |
| OD-13 | **Zero-manual-IP NAS discovery** for normal users; actual mechanism is platform-specific and must be physically verified. |
| OD-14 | **Settings is a product-quality first-class destination** with onboarding, integration management, accessibility, privacy, recovery and per-platform capability handling; see module 12. |

The original eight decisions remain binding; these six are **additional**, not replacements. Other households must not inherit this owner's app choices, NAS paths, credentials or region as hardcoded defaults.

**v3.4.3 utility pill amendment:** The top-right Home utilities must be a compact frosted-glass pill with minimalist text-free white AirPlay status, NordVPN state, AI voice assistant and Settings glyphs. This supersedes older country-flag/ChatGPT text or icon-group guidance. See `modules/05_UX_Motion_Design.md` UX-UTILITY-01–05.


## v3.4.4 binding visual preference

**OD-15:** Owner-approved active Home image `references/approved-home/Unified-TV-Approved-Home-2026-10-25.jpg` is the primary visual composition reference. **OD-16:** Dock labels default to **Focused only**, independently configurable for Adults and Kids; Always show labels and Hide labels are optional Settings choices. Minimalist dock glyphs remain mandatory. See UX-ICON-05/06 and SET-026.
