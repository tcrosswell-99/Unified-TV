> **Repository copy (25 October 2026).** This is the owner's v3.4.4 developer pack text, unchanged except that the link to the approved Home image is shown as a plain path. The pack's reference images (approved Home, six palettes, five Wallpaper references) are **kept outside Git**: they contain third-party marks (Netflix, Disney+, BBC and others) and are reference only, not licensed production assets ([REFERENCES.md](../../design/REFERENCES.md)).

# Unified TV — Specification v3.4 (authoritative index)

**Date:** 25 October 2026 (UK local decision date)  
**Status:** Requirements and Claude implementation guidance, **not evidence that code or physical TV behaviour has passed**.  
**North star:** **Content first, underlying technology hidden.**

## How to use this specification

This short index and the files in `modules/` are the **only normative v3.4 requirements**. Markdown links are a reading/dependency map, **not executable imports**. Agents must read the always-loaded Core, Safety and Testing modules for every task, then only the feature/platform modules listed below. The previous 30+ section document is preserved under `archive/Unified-TV-Specification-v3.2.md` for historical research and traceability; it is **not an active second authority**. Do not copy superseded language back into v3.4. Owner's latest explicit instructions take precedence; when two current modules conflict, **stop and ask** rather than choosing arbitrarily.

### Always load (every sprint)

1. [Core product and eight owner decisions](modules/00_Core_Product_and_Decisions.md)
2. [Safety, security, privacy and non-negotiable constraints](modules/03_Safety_Security_Contracts.md)
3. [Evidence, status vocabulary and agent workflow](modules/10_Testing_Evidence_and_Agent_Workflow.md)

### Load by task

| Sprint/task | Additional modules |
|---|---|
| G0 repository audit, signatures, release | [Shared architecture](modules/01_Architecture_Shared.md), [Android](modules/02_Architecture_Android.md), [Signing](modules/08_Signing_Release_and_Update.md), [Capability gaps](modules/11_Platform_Capability_and_Open_Issues.md) |
| G1 TCL Home, remote and recovery | [Shared](modules/01_Architecture_Shared.md), [Android](modules/02_Architecture_Android.md), [Remote/Home](modules/07_Remote_Home_and_Recovery.md), [Signing](modules/08_Signing_Release_and_Update.md) |
| **G2 Animated Home and Wallpaper** | [Shared](modules/01_Architecture_Shared.md), [Android](modules/02_Architecture_Android.md), [UX/Motion](modules/05_UX_Motion_Design.md), [Capability gaps](modules/11_Platform_Capability_and_Open_Issues.md) |
| **G3 NAS and media** | [Shared](modules/01_Architecture_Shared.md), [Media/NAS](modules/06_Media_NAS_and_Profiles.md), target platform module |
| G4 Sony | [Android](modules/02_Architecture_Android.md), [Remote/Home](modules/07_Remote_Home_and_Recovery.md), relevant feature modules |
| G5 Samsung supported UI | [Tizen](modules/04_Architecture_Tizen.md), [UX/Motion](modules/05_UX_Motion_Design.md), [Signing](modules/08_Signing_Release_and_Update.md) |
| G5P Samsung controlled-test **proposal only** | [Tizen](modules/04_Architecture_Tizen.md), [Remote/Home](modules/07_Remote_Home_and_Recovery.md), [Capability gaps](modules/11_Platform_Capability_and_Open_Issues.md) |
| Settings/onboarding, any gate | [Settings](modules/12_Settings_Onboarding_and_Configuration.md), [UX/Motion](modules/05_UX_Motion_Design.md), target platform module and affected integration/media module |
| G6 Home Assistant, Ring, VPN and household | [Shared](modules/01_Architecture_Shared.md), [Integrations](modules/09_Integrations_and_Notifications.md), target platform module |

## Product-wide settings and new v3.4 decisions

**Settings are a first-class product surface** for multiple households, not a hardcoded personal setup. Read [Settings, onboarding and configuration](modules/12_Settings_Onboarding_and_Configuration.md) for any Settings, onboarding, profile, discovery, privacy or integration-configuration sprint. **Confirmed:** Home remains minimal with a separate Library; exactly two initial profile types (Adults/Kids); universal search deferred until core reliability. **Ring:** motion ignored, person toast, doorbell press attempts live camera; smoke/CO still takes precedence. **NAS:** zero-manual-IP discovery is the product requirement, with real-device feasibility gates.

## Single milestone order

**G0 audit/signing/recovery plan → G1 TCL Home and remote → G2 animated Home and Wallpaper → G3 NAS/media → G4 Sony → G5 supported Samsung + G5P proposal (no test execution) → G6 integrations.** Safety policy and signing design begin at G0, regardless of later integration delivery. G2 takes priority over additional G3 expansion; retain code already built.

## What is and is not known

PR `tcrosswell-99/Unified-TV#9` **is reported** to contain editable dock, Apps grid and NAS paging, but no physical TV acceptance is supplied here. Inspect actual PR/merge state and code before updating the repository's **sole live register** `docs/ACCEPTANCE_REGISTER.csv`. The package's status note is a migration guide, not another status tracker. No detectors are confirmed connected to Home Assistant. The existing TCL preview reportedly uses an unavailable throwaway GitHub signing key: inspect installed APK evidence; use a new independently signed launcher package without uninstalling preview.

## Unconditional release rules

- Android preview **no HOME registration**; new launcher **separate package ID**, durable owner-controlled signing, owner-selected only after independently proven stock recovery.
- **Smoke/CO > Ring**. Each concurrent alarm separately acknowledged and persistently indicated while active. Offline ≠ emergency. No TV-as-certified-alarm claim.
- **VPN Live TV fail-closed**; source status or UI text alone is not route verification.
- Samsung native Art Mode/Smart Hub/TV Plus preserved; Home reassignment **proposal only** until separately approved.
- 400 ms **immediate** focus crossfade; 2–5% subtle compositor-transformed pre-rendered light fields over 20–40 seconds; static fallback.
- Curated properly licensed artwork default; depth mask only where prepared and suitable; no TCL real-time segmentation requirement.
- Cloud agent cannot physically test owner's TVs. Owner runs PC/TV procedures and supplies evidence.

## Visual references

Six palette images exist under `references/ambient/01-smarttube.jpg` ... `06-music.jpg`; five Wallpaper reference images under `references/wallpaper/wallpaper-reference-01.png` ... `05.png`. **Reference only, not licensed production assets.** See [UX/Motion](modules/05_UX_Motion_Design.md).

## Archive and source traceability

`archive/` contains original v3.2 documents, unchanged. Earlier functional detail remains searchable there, but v3.4 modules are the current implementation contracts. If a historical feature is absent from current modules, log a **scope reconciliation issue** and obtain owner confirmation before implementing it. Do not silently drop or reintroduce historical scope. The [coverage crosswalk](MODULE_COVERAGE_AND_MIGRATION.md) identifies moved sections and known gaps.

## v3.4.1 owner clarification — direct vertical navigation

**Confirmed:** Home Up moves directly to the configurable utility icons (NordVPN/AirPin examples), without opening a menu. Home Down reveals NAS films / separate Library with direct focus. Minimal dock-centred Home remains unchanged when idle. See NAV-01–NAV-08 in modules 05/07 and SET-090–SET-093 in module 12. These requirements supersede any previous Up-menu or page-transition interpretation.

## v3.4.2 owner clarification — minimalist dock icons

**Confirmed mandatory:** all six app-dock tiles use cohesive minimalist, monochromatic or simplified vector glyphs; no text-only tiles or default heavy full-colour Android/Tizen app squares. Labels remain outside tiles for clarity and accessibility; focus treatment retains lift, luminous outline, halo and app title. See UX-ICON-01–UX-ICON-04 in `modules/05_UX_Motion_Design.md`. This overrides the legacy v3.2 text-lockup fallback, even where archived text says otherwise. The owner's requested exact v3.2 replacements are included in `archive/Unified-TV-Specification-v3.2-minimalist-icons.md` for traceability; the current modules remain authoritative.

**v3.4.3 utility pill amendment:** The top-right Home utilities must be a compact frosted-glass pill with minimalist text-free white AirPlay status, NordVPN state, AI voice assistant and Settings glyphs. This supersedes older country-flag/ChatGPT text or icon-group guidance. See `modules/05_UX_Motion_Design.md` UX-UTILITY-01–05.


## v3.4.4 — Approved Home visual and default dock labels

**Binding owner decision:** The primary Home visual reference is `references/approved-home/Unified-TV-Approved-Home-2026-10-25.jpg`. It supersedes older Home compositions, subject to accessibility, technical and safety contracts. The image shows labels for all six apps, but **the implementation default MUST be Focused only**. Users can choose Always show labels or Hide labels at **Settings → Appearance & Home → Dock & Icons → App label visibility**, stored independently for Adults/Kids. Minimalist icons are mandatory in every mode. Requirements: **UX-ICON-05, UX-ICON-06, UX-VISUAL-01/02, SET-026, OD-15/16**. The historical v3.2 archive is not normative.
