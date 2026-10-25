# Module coverage, history and migration crosswalk

The **current v3.3 index + modules are normative**; archived v3.2 is retained intact for traceability. This crosswalk prevents silently losing historic scope and highlights areas intentionally deferred.

| Historical v3.2 sections | Current v3.3 destination | Notes |
|---|---|---|
| Authority, owner decisions, 0–3 | Core, Shared, Android, Remote | Eight owner decisions; Q1–Q6 closed; variant-specific HOME. |
| 4.1–4.14, 9–10, 32–33 | UX/Motion | Immediate 400 ms focus crossfade; pre-rendered compositor drift; Wallpaper separate from active Home. |
| 5, 11, 13, 27 media portions | Media/NAS, Integrations | G3 after G2; PR #9 claims to audit; music official app. |
| 6, 16, 16.2 | Safety/Security, Integrations | VPN fail-closed; private settings; reminders later. |
| 7, 7.1, 14, 16.1 | Integrations | Tottenham widget, AirPin, voice, optional cleanup retained as deferred. |
| 8, 24.3, OD-07 | Safety/Security, Integrations | Smoke/CO > Ring; real HA states; Ring not absolute top gate. |
| 15, 17–19, 25–27 | Shared, Testing, Build Plan | Single gate order and evidence vocabulary; owner physical testing. |
| 20–23 | Core, Capability gaps, archive | Historic outside-scope/research items are not auto-authorised. |
| 24–25, 28–31 | Tizen, Remote/Home, Signing | Native Samsung limits, owner-approved proposal only. |
| OD-08 / signing sections | Signing | Separate new launcher identity, lost preview key limitation, owner custody. |

**Known historic requirements not yet fully elaborated in v3.3 modules:** detailed Tottenham matchday widget data source, AirPin LITE configuration, voice assistant service contracts, optional cleanup tool controls, Alexa reminder platform feasibility, full Samsung Echo Studio routing matrix, every legacy acceptance test ID, and complete per-screen visual pixel geometry. These are **deferred detailed design items**, not deleted features. Before implementing one, retrieve its archived subsection, propose a current-module update, confirm constraints and tests with owner, and then implement. No archived instruction overrides a current module.

**Corrections made:** no stale v3.0 Q1–Q6 open questions; eight decisions; G2 Visual before G3 Media; no Ring highest priority; no preview HOME; single motion contract; single CSV status register; correct HA state semantics; signing rotation vs missing prior key; owner-run hardware tests; actual bundled reference paths.

## v3.4 additions

New normative `modules/12_Settings_Onboarding_and_Configuration.md` owns Settings IA, first-run onboarding, user-facing configuration, privacy, family settings and acceptance IDs. Related v3.4 amendments in Core (OD-09–OD-14), Safety (Ring priorities and unbypassable policies), Media (separate Library), UX (minimal Home), Integrations (Ring events), Tizen (embedded service discovery feasibility) and Testing (SET acceptance). Existing v3.2 archive remains untouched.


## v3.4.4 traceability

Primary approved Home screenshot: `references/approved-home/Unified-TV-Approved-Home-2026-10-25.jpg` → module 05 UX-VISUAL-01/02. Configurable label modes: module 05 UX-ICON-05/06, module 12 SET-026, Core OD-15/16. This explicitly overrides the screenshot's always-visible labels and any historical unconditional label language.


## v3.4.6 cross-module dependencies

- UX module 05: UX-DOCK-08–10 responsive six/eight icon dock, labels and navigation.
- Settings module 12: SET-027, SET-BUILD-09–11 detect-first and multi-music choice.
- Core/architecture: persist per-profile capacity and service preference without altering device-level installation.
- Testing: extend the existing acceptance register and preserve G0–G6 safety gates.


## v3.4.7 Settings presentation

New normative `modules/13_Settings_Liquid_Glass_Overlay_and_Screen_Design.md` specifies the visual overlay, all ten screen layouts, motion, focus and tests. It supersedes module 12's historical two-column presentation; module 12 remains authoritative for functional settings and onboarding.
