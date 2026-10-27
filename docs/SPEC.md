<!-- Unified TV: public specification. No account credentials, local addresses, family photographs or private configuration belong in this repository. -->

# Unified TV specification

**Version:** 3.3, with the owner's later decisions | **Date:** 25 October 2026 (UK time) | **Status:** working specification. It states requirements, **not** features already built or verified.

> **v3.4.7 is the current specification.** The owner supplied the v3.4.7 developer pack on 25 October 2026: [docs/spec/v3.4/](spec/v3.4/README.md) (index, modules 00–13, changelogs to v3.4.7, handover, build plan). v3.4.5–v3.4.7 add Build My TV, the 6/8-icon dock and the Liquid Glass Settings overlay (module 13). Its modules are the normative requirements; where they and this document differ, **the v3.4.4 modules win**, and the owner's later explicit instructions (recorded below, for example the fixtures, alarm-switch, Live TV and PIN-reset answers) win over both. This document remains the consolidated reference for decisions and detail the modules don't restate. The pack's reference images are kept outside Git (third-party marks).

> **North star: Content first, underlying technology hidden.**
>
> The television should make it effortless to find and enjoy programmes, films, music and games. The person watching should not need to understand playback engines, the NAS, Jellyfin, Home Assistant, VPN connections or which operating system is running.

## 1. How to use this document

1. **Authority.** The owner's latest instruction comes first, then this file, then [PRODUCT_SPEC.md](PRODUCT_SPEC.md) (the v3.3 detailed requirements), then the other docs. If PRODUCT_SPEC.md disagrees with this file, this file wins and PRODUCT_SPEC.md must be corrected. Each section below names the PRODUCT_SPEC.md sections that hold the detail.
2. **Status lives only in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv).** Nothing in this file, PRODUCT_SPEC.md, a commit message or a screenshot is a status record. [FEATURE_STATUS.md](FEATURE_STATUS.md) is a readable summary of the register; [AI_HANDOVER.md](AI_HANDOVER.md) is the continuation guide for agents.
3. **History.** Superseded documents, including the owner's v3.2 package and the v2.2 specification, are in [archive/](archive/README.md). They are history and must never be used as instructions. The v3.3 corrections are listed in [SPEC_CHANGES_v3.3.md](SPEC_CHANGES_v3.3.md).
4. **Requirement ≠ code ≠ verified result.** Record them separately. Code present, a passing build, a simulation or a mockup never establishes working hardware behaviour.
5. **Priority labels.** P0 = essential to its named release gate. P1 = desired after the daily-use baseline. P2 = optional enhancement. GATE = an unresolved feasibility, safety or third-party dependency that must pass on hardware before the feature is promised. P0 is target criticality, not an instruction to build everything now; sequencing follows the release gates (Section 4).
6. **Honest limits.** Never claim Ring interrupts native Samsung Art Mode, that Amazon Music custom control or GPT voice streaming works, that Live TV is VPN-protected, or that an alarm reaches the viewer, without real-device evidence.

## 2. The product

A calm, premium, remote-first home experience inspired by the principles of the tvOS Liquid Glass era (not a copy of Apple's software, fonts or assets) for the owner's three televisions. Layout, motion language, media catalogue and primary controls feel the same on every TV; only deliberate device features differ, and every platform difference is stated truthfully.

| ID | Television | Runtime | Device-specific features |
|---|---|---|---|
| TV-TCL | TCL 55-inch, Android TV 11 (API 30); exact model, firmware and remote to be captured with the owner | Native Android: preview app and launcher app | Lightweight rendering; AirPin LITE receiver (optional); first daily-use target |
| TV-SONY | Sony BRAVIA 65-inch OLED, Google TV; exact model, firmware and remote to be captured | Native Android: same apps | Xbox tile, Xbox on HDMI 3 (eARC); OLED care |
| TV-FRAME | Samsung The Frame 2021, QE50LS03AAUXXU, firmware T-NKM2DEUC-2310.0, Smart Remote with on-screen 123 keypad | Native Tizen Web Application (WGT) | Samsung TV Plus tile; native Art Mode and Smart Hub preserved; Echo Studio audio |

**Non-negotiables**

1. **Content first:** no advertising, sponsored rows, poster walls or technical plumbing in the foreground. Resting Home is one full-screen background, one glass dock, one clock and a tiny utility group.
2. **Contextual artwork:** a highlighted app, film or programme always controls the background with genuinely related imagery or its own brand-aware fallback. An unrelated image behind a highlighted item is a defect, never "content aware".
3. **Focus latency and recovery outrank visual effects.** Reduced graphics keep full UX parity on the TCL and the ageing Sony.
4. **Platform separation is deliberate:** native Android on TCL/Sony, native Tizen on the Frame. No external streamer is required.
5. **Preserve native features:** factory Home stays installed and recoverable; Samsung Smart Hub, TV Plus and native Art Mode are never replaced, disabled or overridden. Unified Wallpaper is not Art Mode.
6. **Embedded playback only:** Media3 on Android, AVPlay on Tizen. **VLC is excluded** from the product and playback path.
7. **Alert priority:** smoke/CO emergencies first, then Ring, then household notices, reminders, decoration (Section 10).
8. **Live TV is VPN-guarded:** it opens only while NordVPN is connected, and Unified TV covers it if NordVPN drops (OD-26). That is not a kill switch and is never called one; a status icon alone is never a kill switch. Only Android's own always-on block, proven at network level on that TV, stops traffic ([VPN_PROTECTION.md](VPN_PROTECTION.md)).
9. **All household integrations are optional.** No IP addresses, tokens, personal paths, Ring/child/family images or household preferences in public source.
10. **No blanket cleanup.** Any optional TV tidying reads firmware and package state, needs explicit consent per change, records a backup and verifies rollback. Disable, never uninstall.
11. **No fabricated capabilities:** desired behaviour and tested behaviour are always kept apart.

**Product structure** *(owner, 25 October 2026)*: **Home → Library → Apps → Settings**, with **Adults/Kids** as a lightweight profile layer and household notifications (Section 10) working across all of it.

- **Library** is reachable from a dock item the household can place, move or remove like an app tile, and by normal remote navigation (the Down shelf stays). Opening or closing it keeps Home's focus and Unified TV's own playback position wherever technically possible.
- **Profiles:** the TV opens straight into the **last-used profile**; it never asks on every start. Switching is quick from Home and Settings. Moving **into Adults always needs the PIN**; moving into Kids does not (Kids can only be enabled once a PIN exists).
- **Kids:** NAS content is restricted by **adult-approved Kids folders and by rating metadata** in the data and playback path, not by hiding posters. Apps are restricted by a central launch check inside Unified TV plus, where Android allows, an optional **Kids app guard** that returns to Home when a non-approved app comes to the front. Hiding an icon alone is never called parental control. Native controls (Google TV Kids profiles on the Sony; Android TV restricted profiles where offered) are recommended where they exist.
- **Continue Watching:** separate Adults and Kids histories for **Unified TV's own player**, kept on the TV and labelled as such. Third-party watch progress only through supported APIs; none is integrated (another app's Android TV Watch Next rows are not readable by a third-party launcher).
- **Ring in Kids:** the person toast (RING-02) can be switched off for Kids; doorbell presses and smoke/CO keep their priority in every profile.
- **Universal search (later):** the catalogue uses one item model (title, year, kind, source, stable id, folder/root, rating, poster reference) so a search index can be added without rebuilding it. No streaming-service integration is built now.
- **Home navigation** *(owner, 25 October 2026, answering v3.4 SET-020)*: the default Home stays minimal; utilities and NAS films appear only when you move towards them.
  - **Up:** focus moves straight onto the utility icons (NordVPN, AirPin or another configured utility). Left/Right moves between them; OK launches the focused utility. No intermediate menu.
  - **Down:** the NAS film section slides into view, with focus going straight to the first useful film or category.
  - **Left/Right:** move within the focused row without changing section.
  - **Back:** returns to the previous level or the dock, never resetting the launcher.
  - No loading screen or page transition between these sections: the background stays continuous while content glides into place.
- **Household settings, not hardcoding** *(owner, 25 October 2026, answering SET-011/SET-074)*: dock defaults come from the apps installed on the TV; the football-team card, time zone and rating scheme are settings. The owner's TVs keep today's choices (the six apps, Tottenham, UK time, UK/US ratings) as their settings.
- **Doorbell camera auto-close** *(owner, 25 October 2026, answering SET-051)*: **30 s by default**, with 60 s and manual (Back only) selectable.
- **Team fixtures** *(owner, 25 October 2026)*: the household picks its team from **all UK football teams** (England, Scotland, Wales, Northern Ireland), shown by display name with a **minimal badge the app draws from the team's colours and short name** (club crests are trademarks and are not bundled or downloaded). Fixtures come from **TheSportsDB** by default (OD-22): no account, key or phone step; the household picks a league, then its team, or types a few letters (searched on the TV). The team list is read league by league and kept for a week; because TheSportsDB's free key lists only about 10 teams a league, the Premier League, Championship and League One come complete from a bundled list (marked with its season) and every league is completed from one round of its fixtures. Fixtures come from the team's own next match, its league's next two rounds and, failing those, the season's schedule; they are read at most every six hours and every 15 minutes within three hours of a kick-off. Only league and team numbers, league names and the chosen club's name are sent; a name typed in the picker is sent only when an adult asks TheSportsDB for it. Settings says "No fixtures found yet for <club>" when nothing was found. **API-Football** is optional "more detail" with the household's own key added on a phone (OD-17): when the key is saved and the team has an API-Football number, API-Football is asked first (daily, hourly on matchday, every 15 minutes near kick-off, within the free plan's 100 requests a day) and TheSportsDB answers whenever it refuses or can't be reached. A moved date or kick-off is shown ("Kick-off moved to 17:30"). Home Assistant calendars stay an alternative source. The model is sport-agnostic so other sports can be added later. Scores are never shown.
- **Smoke/CO switch** *(owner exception to v3.4 SET-053, 25 October 2026)*: the "Show smoke and carbon monoxide alarms" switch stays, but turning it off needs the adult PIN and leaves a permanent **"Alarms off on this TV"** notice on Home and in Settings; "Stop alerts" actions route through the same PIN. With no detector paired, Settings says **"Not configured — no live monitoring"**.
- **Live TV and NordVPN** *(owner, 27 October 2026, OD-26; replaces SET-063's "automatic verification only" of 25 October)*: Live TV opens when NordVPN is connected: Android reports a VPN and it is NordVPN's (NordVPN's own notification says connected, with notification access; or NordVPN is the only app on the TV that can run a VPN). Otherwise it stays locked on every route, with a plain reason. An adult's recorded attestation still never unlocks it. If NordVPN drops while Live TV is on, Unified TV covers Live TV with a full-screen notice. This is not a kill switch, and Settings says so.
- **Forgotten PIN** *(owner, 25 October 2026, SET-043)*: reset only from the owner's computer over ADB (clears the PIN and turns Kids off); nothing on the TV itself bypasses it.
- **Owner diagnostics:** a hidden screen in Settings (Adults, PIN), never on Home, checking NAS discovery and catalogue, Ring/Home Assistant connectivity, Live TV's VPN rule and the facts behind it (OD-26), version and signing, and listing hardware tests.

Detail: PRODUCT_SPEC.md Sections 1–3, 15.

## 3. Owner decisions

OD-01 to OD-08 were confirmed by the owner on **25 October 2026 (UK time)**. They are binding; do not reopen them.

| ID | Decision | What it means |
|---|---|---|
| OD-01 | **TCL: Unified TV becomes the default Home once recovery is tested.** | Build the separate launcher app (Section 6.1), prove an independent return to the factory launcher and the **Open original TV interface** escape, then the owner chooses **Make Unified TV the Home screen** and Android's own Home selection decides. Approval of the end state, not permission to change the TV remotely or before recovery is proven. |
| OD-02 | **After TCL Home, build Animated Home and Wallpaper first.** | Gate G2 (visual) comes before G3 (NAS viewing). Existing NAS work is kept, not removed. |
| OD-03 | **Default Wallpaper source: curated artwork.** | Free artwork explicitly licensed for this use, bundled offline (no real-time download to display it), with a sources/licence/attribution record in [ARTWORK.md](design/ARTWORK.md). Owner photos (My Photos) are a later task. |
| OD-04 | **Depth clock: automatic on suitable images.** | Default `Auto`. Uses prepared foreground masks for selected artwork only; no real-time segmentation on the TCL; otherwise the standard clock. Never fake occlusion. |
| OD-05 | **Samsung: prepare a separate proposal for a controlled CleanFrame-style test.** | Gate G5P: a written proposal only (compatibility, hazards, read-only checks, independent recovery, steps, rollback, go/no-go). No undocumented helper calls, native writes or Home reassignment without a later, separate explicit approval. |
| OD-06 | **TCL/Sony remote identification: Claude helps later.** | Do not block work waiting for remote models. At the physical gate, guide the owner through About screens, remote photos and key logs; never invent key mappings. |
| OD-07 | **Smoke and carbon monoxide alarms outrank Ring.** | Smoke and CO are equal and highest. Ring never covers an active emergency. The TV is a supplementary display, never a certified alarm. Section 10. |
| OD-08 | **Durable signing for in-place updates.** | The owner creates a durable key on their own computer; the separate launcher app uses it from its first release; the existing preview stays as it is. Section 12. |
| OD-17 | **Every service is set up on the phone, by QR code.** (Owner, 25 October 2026.) | Any service that needs an account, a sign-in or a key (Home Assistant, API-Football, TMDB, and every later one) is set up from a QR code on the TV that opens a temporary page on the household's phone. The phone page links to the service's sign-up with plain steps, takes the sign-in or key, and the TV tests and saves it; nothing long is typed with the remote. The QR screen and its status live in the Settings half-page glass panel; typing on the TV stays only as an Advanced fallback. |
| OD-18 | **Film and TV artwork comes from TMDB; the NAS stays Synology Media Server.** (Owner, 25 October 2026.) | Posters, backgrounds and descriptions are matched by title from TMDB with the household's own free key (entered by OD-17), as the inherited CleanYourTV UI did. Only the cleaned title and year are sent. No Jellyfin or other NAS install is required. |
| OD-19 | **Settings and setup screens always use the sidebar over Home. Never break this.** (Owner, 25 October 2026.) | Every settings or setup screen (NAS and library folders, Home Assistant, football, posters, QR phone setup, household, apps and dock, profiles and Kids, and every future one) opens as the module 13 glass sidebar over a still Home: half width, three quarters for wide tasks. A full-screen settings or setup page is a defect. Browsing and watching content may be full screen. [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md). |

| OD-20 | **Dock tiles show white brand marks.** (Owner, 25 October 2026.) | YouTube (also on SmartTube's tile, labelled YouTube), Netflix, Disney+ and BBC iPlayer show simple white marks drawn in code, used only to identify the app each tile opens; Disney+ uses the owner-supplied Disney 2024 wordmark SVG as path data; no image files are bundled; trademark note in preview/THIRD_PARTY.md. Live TV is a TV set; Music a bold note pair. This replaces UX-ICON-01/03's "original symbols only" for these four services. |
| OD-21 | **The VPN shield shows the connected country.** (Owner, 25 October 2026.) | A small round flag beside the shield while NordVPN reports connected, read from NordVPN's own notification (notification access, in memory only). This replaces UX-UTILITY-01's "no flags". The flag is information only; it never unlocks Live TV. |
| OD-22 | **Football fixtures come from TheSportsDB by default, with no account.** (Owner, 25 October 2026.) | The household picks a UK team and fixtures appear; no sign-up, key or phone step. API-Football stays optional ("more detail") with the household's own key via the phone (OD-17). This replaces the earlier API-Football-by-default choice, after its dashboard hid the key behind a VPN/proxy warning on the owner's phone. |
| OD-23 | **Opt-in "Open Unified TV when the TV turns on".** (Owner, 25 October 2026.) | For TVs that keep their own Home button (the TCL may): when the TV starts or wakes from standby, Unified TV brings itself to the front. Off by default, adult PIN, launcher app only. It uses Android's "Display over other apps" permission solely to come to the front; it never intercepts or reassigns keys, never watches for the TV's own Home, and the Home button still opens the TV's own Home (module 03). |
| OD-24 | **Back at Home never leaves Unified TV (launcher app).** (Owner, 27 October 2026.) | In the launcher app, Back at the main screen closes any open layer, then moves focus to the first dock tile; a held Back counts as one press. This applies whether or not Unified TV is the chosen Home, so a TV that keeps its own Home (the TCL, with OD-23) no longer drops to the old Home on Back. A one-time hint says the TV's own Home button still opens the TV's Home. The separate review preview still closes on Back at Home (it can never be Home, module 07). |
| OD-25 | **Optional on-TV clean-up of preloaded apps.** (Owner, 27 October 2026.) | Settings > Updates & About > Advanced > Optional clean-up (also from TV Performance & Cleanup), adults only, off until opened. Only on TVs whose firmware matches a tested list (the TCL BeyondTV4/6 family first); anything else gets read-only guidance. It connects only to this TV's own debugging service (127.0.0.1) for the session, after the TV's own "Allow debugging?" prompt; reads first, changes nothing until each app is chosen and confirmed, writes a recovery record before the first change, turns apps off (never uninstalls), re-checks, and offers Undo all and a PC restore record via the phone. The TV's own Home, Live TV and inputs, updater, network, Play, keyboards, accessibility, VPN and Unified TV are never offered. Afterwards it guides the owner to turn debugging off and checks it is closed. This narrows §13's "never disable factory packages" to: never without a tested list and per-app consent. |
| OD-26 | **Live TV opens when NordVPN is connected.** (Owner, 27 October 2026.) | After build run 96 on the TCL never opened Live TV (no automatic route check is possible for an Android app), the owner chose this over an Android always-on/lockdown check or staying locked. Every route (dock, Apps, voice, the Live TV sheet) uses one rule: Android reports a VPN, NordVPN is installed, and either NordVPN's own notification says connected (notification access) or NordVPN is the only app that can run a VPN. Another VPN app and no notification access: "Can't tell which VPN is on", locked. While Live TV that Unified TV opened is on, a drop brings Unified TV forward with a full-screen notice (Reconnect, Back to Home) using "Display over other apps", or a high-priority notification without it. Never called a kill switch: Android doesn't block Live TV's connection when NordVPN drops. Kids rules unchanged. This replaces SET-063's "automatic verification only". |
| OD-27 | **Control the real Amazon Music app from inside Unified TV.** (Owner, 27 October 2026.) | Official Android media APIs only: Amazon Music's own media session (MediaController transport controls and playFromSearch, seen through the owner-granted notification access), Android's standard MEDIA_PLAY_FROM_SEARCH request addressed only to Amazon Music, and its MediaBrowserService if it agrees to share. No unofficial Amazon API, no scraping, no DRM workaround (the owner was shown those and declined them). Amazon Music keeps playing in its own app in the background; Unified TV is the remote and the screen. Now Playing (content, full screen) shows the track's own artwork as the background and maps OK/Play-Pause, Left/Right (held: ±10 s) and the buttons the app itself declares (shuffle, repeat, its own like/thumbs); Back returns to Home and playback continues. Play by name from the Music page (glass sidebar) and voice ("play …"): the live session first, then the request to Amazon Music, then Amazon Music's own search, said plainly. "Your Amazon Music" library browsing is best effort and hidden when Amazon Music refuses (recorded in Owner diagnostics). **Play on: This TV / each Echo / Everywhere** (same day): through the household's Home Assistant and the community Alexa Media Player integration the owner installs (HACS) and signs in to inside Home Assistant; Unified TV never handles Amazon credentials. One media_player.play_media call per request (AMAZON_MUSIC to one Echo or an "Everywhere" group entity; otherwise one spoken-style "play … everywhere" custom command to one Echo), never a separate play to each Echo. Remembered per profile; Echo targets are Adults-only unless an adult turns them on for Kids; without Home Assistant or Alexa Media Player, one plain line explains. Not tested on a TV, with Amazon Music, or with Alexa Media Player. |
| OD-28 | **Take over the TV's own Home button, with a way back in Settings.** (Owner, 27 October 2026: "Take over the TV's own Home button, with a way back in Settings; otherwise everything stays in Unified TV, and Back never drops you out of Unified TV.") | Launcher app only, adults (PIN), Settings > Appearance & Home > **Take over the Home button**. Over the same session-only link to the TV's own debugging as OD-25 (same "Allow debugging?" prompt and key, closed when done, debugging-off check at the end): Unified TV's Home entry is switched on and confirmed as a Home candidate; Android's own route is tried first (`cmd package set-home-activity` naming only Unified TV's own Home entry). Only if the TV keeps its own Home in front (the owner's TCL: `com.google.android.tvlauncher` at priority 2), and only on a TV a tested list covers, for the package that is the TV's stock Home now, and only after a plain explanation and the owner's confirm: the recovery record (OD-25's own) is written first, then that one package is turned off (`pm disable-user`, never uninstalled), Unified TV set as Home again and Home read again. If another app then answers Home it is reported, never turned off. Finally "Press the Home button on your remote now": Home confirms it came to the front within 20 seconds, otherwise the way back is offered. **Return to the original TV interface** (Appearance & Home, Updates & About > Advanced, and Home's safe screen) turns that package back on, restores the previous Home and withdraws Unified TV's Home entry; without debugging it shows the TV-only route and `adb shell pm enable <package>`. While the TV's own Home is off, Updates & About warns before uninstalling, and two crashes within a minute of Home starting open a safe screen instead of a loop. The general clean-up still never offers a Home app. Still never: accessibility key interception, reading or reassigning keys, root or uninstalling. This replaces §6.1's (and module 03's) "never disable the factory launcher" for this one flow, on tested-list TVs, consented, recorded and reversible. |

*OD-17 to OD-19 were first written as OD-09 to OD-11; they were renumbered on 25 October 2026 because the v3.4.7 developer pack already uses OD-01 to OD-16 for other decisions ([module 00](spec/v3.4/modules/00_Core_Product_and_Decisions.md)).*
**Still unknown (not a reason to stop safe work):** the exact TCL button that previously escaped to the manufacturer interface. Capture it with the owner during G1 and add it as a named regression case (HRC-01).

## 4. Release gates

There is **one gate order**, used in every document:

| Gate | Outcome | Includes | Key tests |
|---|---|---|---|
| **G0** | Safe, reproducible development | Repository, branch and source audit; signing audit and key-custody plan (OD-08); preview/launcher package separation; known recovery; test inventory | SIGN-01, SIGN-02, SIGN-07, SIGN-08, P05-01 |
| **G1** | TCL Home and remote reliability | Launcher app as owner-selected default Home; problem button reproduced and fixed or classified; Home/Back/third-party return; standby and boot; discreet original-interface escape; independently verified rollback | RC-01/02/04–07/10/11, HRC-01–04, HRC-10, OI-01/02/03/05/07/08, SIGN-03 |
| **G2** | Animated Home and Wallpaper | Six ambient palettes, timing rule, glass and focus; Wallpaper with curated artwork, simple clock, depth clock for prepared-mask artwork; reduced-motion and OLED controls | AM-01–12, WP-01/02/04–09/12–16 |
| **G3** | NAS/media viewing | Editable dock and Apps; DLNA discovery and exact roots; title browse; Media3 playback, pause, seek, Back; failure recovery; honest empty states | P05-02–P05-10, LIB-01–04, AT-02/03, AT-56–61, GATE-NAS-01–03 |
| **G4** | Sony | G1–G3-equivalent evidence on the Sony with its own remote; Xbox HDMI 3, eARC; OLED safeguards | RC-03, HRC-05/06/10, OI-04, SIGN-04, AT-11 |
| **G5** | Samsung Frame | Signed WGT; Smart Remote matrix; Smart Hub return recorded truthfully; TV Plus and Art Mode preserved; AVPlay playback; original-interface route | NT-01–12, RC-09, HRC-07/08, OI-06, SIGN-05, CF-01/02 |
| **G5P** | Samsung proposal only | CleanFrame-style Home reassignment proposal; **no native writes** | CF-03–05 (documents only) |
| **G6** | Household integrations | VPN-guarded Live TV (OD-26), smoke/CO alerts, Ring doorbell/person, child safety, music, reminders, voice, optional tuning | VPN-01, GATE-VPN-01–05, SAFE-01–10, RING-01–05, GATE-RING-01–04, AT-04–09, AT-26–31, AT-71–73 |

**Gate rules**

- A gate passes only when its required tests reach `physical_verified` on the named TV with evidence. Source, CI and simulations never pass a gate.
- G2 takes priority over G3 if capacity conflicts. Parallel work (for example the G5P proposal, the signing plan or the alert schema) is allowed only with recorded file ownership and must not destabilise G1/G2.
- The emergency-priority policy is P0 for any build that enables Ring or advertises household alerts. No alert feature is "finished" before SAFE-01–10.
- Do not label the household release complete until every agreed required gate has passed. A platform's result never substitutes for another's.

Detail: PRODUCT_SPEC.md authority section, Sections 25, 27–29.

## 5. Status, evidence and testing

**Status vocabulary** (the register's `status` column, and the only status words to use anywhere):

| Status | Meaning |
|---|---|
| `not_started` | Required; no work in source. |
| `implemented_unverified` | In source; not proven on the named TV. Also the status of a requirement that **failed** on hardware: the failure, build and TV go in `observed_result` and `test_level` records the physical attempt. |
| `test_passed_nonphysical` | Unit, simulated or CI checks pass; not hardware evidence. |
| `physical_verified` | Passed on the named TV, firmware and exact build, with evidence. |
| `blocked` | A hardware, account, permission, service or owner-decision prerequisite is missing. |
| `unsupported_verified` | Proven impossible on that device/firmware, with evidence and an agreed fallback. |

These are the v3.3 developer pack's names, adopted by the owner on 25 October 2026. The pack has no separate "failed" or "deferred" status: a hardware failure stays `implemented_unverified` with the failure recorded, and deliberately deferred work is `not_started` with the reason in `next_action`.

**Test level vocabulary** (the register's `test_level` column; the evidence-level words of v3.4 module 10, adopted 25 October 2026): `none`, `source_review`, `unit`, `emulator`, `CI`, `PC_network`, `physical_TCL`, `physical_Sony`, `physical_Frame`. **Only a `physical_*` level with evidence can make `physical_verified`.** Earlier values were migrated: `simulated` (Robolectric journeys) → `emulator`, `ci_build` → `CI`, `pc_network` → `PC_network`, `physical_tcl/sony/frame` → `physical_TCL/Sony/Frame`.

**Evidence row:** the register columns `requirement_id, feature, owner_agent, branch_commit, build_hash, device_model, firmware, test_level, observed_result, evidence_path, status, severity, next_action`. A hardware row also names the physical remote and the app variant/version/signature. No evidence means not verified.

**Physical testing is run by the owner.** Agents cannot reach the televisions. The owner runs test steps from a Windows PC and on the TV, and reports the evidence back (steps and reporting in [OWNER_TESTING.md](OWNER_TESTING.md)). Nothing becomes `physical_verified` without that evidence. Screenshots must come from the exact tested build and be labelled with its version.

## 6. Platform contracts

### 6.1 Android (TCL and Sony): two apps from one codebase

Two separately installed apps are built as Gradle product flavours:

| Flavour | Package | Home role | Signing | Lifecycle |
|---|---|---|---|---|
| `preview` | `io.unifiedtv.preview` | **No HOME entry at all.** Physical Home opens the factory Home; this is disclosed, not a defect. | Unchanged (its original key; see Section 12) | Identity unchanged; stays installed; never uninstalled |
| `launcher` | `io.unifiedtv.launcher` | Declares a HOME activity-alias (`ACTION_MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT`) that **ships disabled**. Enabled only when the owner chooses **Make Unified TV the Home screen** (Android's own Home selection then decides) or **Take over the Home button** (OD-28) in Settings. Withdrawn if not chosen or if the choice is reversed, but never while OD-28 has the TV's own Home turned off (only **Return to the original TV interface** withdraws it then, after turning that Home back on). | Owner's durable key from its first release | Updated in place with the same key |

- The **Open original TV interface** escape hatch lives in the launcher: Settings > Updates & About > Advanced (Adults, PIN), a small secondary row; the Home-button choice is under Appearance & Home. Choices: *Open original interface for now* (temporary visit, keeps the default; disabled with an explanation if unsupported), *Make original interface the default* (guides to the system Home selection; second confirmation; while OD-28 has the TV's own Home off it opens **Return to the original TV interface** instead) and *Cancel*. **Return to the original TV interface** (OD-28) is a full-size row beside it and in Appearance & Home > Home button. Adult-only where profiles are active.
- Never silently assign the default Home, rename the factory launcher, use root, persistent ADB or accessibility key hijacking. The factory launcher is turned off only by **Take over the Home button** (OD-28): launcher app, adults, a TV a tested list covers, the package that is the TV's stock Home, after a plain explanation and the owner's confirm, recovery record first, disabled never uninstalled, with **Return to the original TV interface** in Settings (Appearance & Home, and Updates & About > Advanced) and on Home's safe screen. An independent route to the factory Home through OS Settings must work even if Unified TV fails to start (RC-10, HRC-10).
- Back at the Unified Home root never calls `finish()` and never falls through to the stock Home. Overlays and child pages unwind in a fixed order.
- Home works offline: it never waits for the NAS, VPN, Home Assistant or the network.
- External apps launch through verified intents. The app never claims to intercept Home inside another app; the OS routes Home to the chosen default.
- Hardware key logging only in owner-authorised debug builds.

Detail: PRODUCT_SPEC.md Sections 28–30.

### 6.2 Samsung Frame (Tizen)

- A native Tizen Web Application, signed with Samsung author and distributor certificates. It cannot replace Smart Hub: the physical Home key opens Samsung Home, and that is a **documented platform gap** with a tested, filmed return path, not a hidden defect.
- Back works inside the app; follow Samsung's exit behaviour at the root; never intercept long Return. Register only supported extra keys; every screen works with D-pad, OK and Back. The 123 button is the on-screen keypad, not physical digits.
- Hidden apps may be suspended: save state before hiding, revalidate on resume. A WebSocket in a hidden app is not a reliable alert channel; Ring on the Frame needs an external household coordinator with launch and first-frame acknowledgement, which is unproven.
- AVPlay owns playback; the app may not draw over other Tizen apps or native Art Mode. Credentials use Tizen Key Manager and fail closed; never plaintext `localStorage`.
- Android-only apps (SmartTube, ReamSmartz) are shown as unavailable unless a supported native client exists; never a fake tile.
- **CleanFrame research is proposal-only.** CleanFrame documents an unsupported, firmware-specific native Home reassignment on a different Frame (2024 LS03D, Tizen 9). The owner's 2021 Frame is unverified. Staged approach: S1 read-only inventory, S2 signed Unified TV WGT, S3 read-only native feasibility, S4 recovery baseline and risk review, S5 owner go/no-go, S6 guarded experiment, S7 household verification. OD-05 authorises only the written G5P proposal (S1–S5 documentation); S6 and S7 need a later, separate explicit approval. Never copy CleanFrame binaries, identities or settings values.

Detail: PRODUCT_SPEC.md Sections 24, 29.4, 30.2, 31.

### 6.3 Shared contracts

- **Domain model** (map to existing code; no parallel duplicates): `DeviceCapability`, `Profile`, `DockItem`, `NavigationDestination`, `FocusState`, `WallpaperSettings`, `AmbientPalette`, `MediaSource`, `MediaItem`, `PlaybackState`, `VpnEvidence`, `NotificationEvent`, `AcceptanceEvidence`.
- **Capability registry:** platform, OS/firmware, remote-mapping provenance, Home ownership, Art Mode, app launchability, voice, overlays, codecs, segmentation, evidence time. Values `supported_verified`, `unsupported_verified` or `unknown`; never inferred from a platform name.
- **Events** with owners and cancellation rules: `RemoteKey`, `FocusChanged`, `ExternalAppLaunched`, `ReturnToHome`, `WallpaperEntered`, `WallpaperExited`, `PlaybackChanged`, `VpnEvidenceChanged`, `EmergencyAlarmEvent`, `RingEvent`, `ReminderEvent`, `SystemStandby`, `SystemResume`. Security events supersede decoration.
- **Visual state machine:** `ACTIVE_HOME` (ambient mesh), `CONTENT_BROWSE` (item-matched artwork), `WALLPAPER` (artwork and clock). Transitions are cancellable, focus-safe and memory-bounded.
- **Navigation:** `UnifiedHome → Apps / Library / Gallery / Settings → Detail → Player`, overlays on top; Back unwinds one layer; returning restores the origin tile and scroll position without a flash or stale backdrop.
- **Settings persistence:** per-device versus per-profile scope explicit, versioned migrations, encrypted secrets, no hardcoded household addresses.
- **Errors:** distinguish transient failure, permission denied, unsupported and not configured. An unavailable NAS never blocks Home. No blanket exception swallowing.

### 6.4 Remote buttons

Test the actual three physical remotes; record each button as `received`, `OS-reserved`, `third-party-owned`, `unsupported` or `not-present` (RC-05). Volume, mute, power, input/source, HDMI-CEC, accessibility and the system voice key stay with the OS. Media keys control only Unified TV's own player. Guide/channel keys never bypass the Live TV VPN guard. "Always return" means tested owner-selected Home behaviour where supported, not key interception inside other apps.

## 7. Visual system

### 7.1 Three visual states

| State | Look | Notes |
|---|---|---|
| Active Home (`ACTIVE_HOME`) | Code-rendered ambient mesh in the focused app's palette, one frosted-glass dock, small clock and utilities | No photographs or large clock behind the active dock |
| Content browse (`CONTENT_BROWSE`) | The focused NAS title's own backdrop and metadata | Never an app-brand background while a title has focus |
| Wallpaper (`WALLPAPER`) | Full-screen curated artwork with optional depth clock; no dock, VPN badge or chrome | Not native Samsung Art Mode, which the TV owns |

### 7.2 Layout

1920 × 1080 logical canvas, scaled; keep ~4–5 % width and ~5–6 % height clear of critical content. One 24-hour UK clock (Europe/London, no seconds), top-left by default. Small top-right utility group: truthful VPN state and verified exit flag, optional voice glyph, Settings. One low glass dock (about 15–20 % of screen height) with SmartTube, Netflix, Disney+, BBC, Live TV, Music as editable defaults; Xbox only on Sony, Samsung TV Plus only on Frame; no VLC tile; no setup controls in the dock. A subtle `Films & TV` chevron below the dock, retired after use. Spurs matchday card only on matchday. Starting type sizes: clock 40–48 px, app label 26–30 px, hero title 44–64 px, metadata 23–28 px, toast ≥ 25 px; target 4.5:1 contrast; never colour alone. Original, redistribution-cleared fonts and icons.

### 7.3 Ambient mesh palettes

Overlapping soft radial light fields over a dark base, like LED bias light through frosted glass; never a flat fill, hard blob or slideshow.

| Focus | Palette and arrangement |
|---|---|
| SmartTube | Crimson red glow from bottom-left, secondary red centre-left, deep charcoal edges |
| Netflix | Low-key blood-red central glow, heavy vignette, the darkest mesh |
| Disney+ | Navy/indigo field, restrained cyan bloom below the dock |
| BBC | Diffuse magenta/deep-pink wash, dark enough for white text, no banding |
| Live TV | Restrained violet/indigo pools, calm space for VPN sheets |
| Music | Warmest multi-point peach, coral and pink |
| Unknown app | Neutral accessible fallback palette |

Palettes follow the app's stable identity, not its dock slot.

### 7.4 Timing rule (one rule, all TVs)

| Event | Rule |
|---|---|
| Focus acknowledgement | Within 80 ms (p95 under 100 ms): lift, brightness, scale 1.06–1.09 over 150–210 ms |
| Dock focus change → background | Crossfade **starts immediately**, **400 ms, ease-in-out**. Latest focus wins: retarget from what is visible, never queue |
| Ambient drift | 20–40 s paths; each light travels up to about 20 % of the screen width and back (*changed 25 October 2026 by the owner: the 2–5 % shift was invisible on the TCL; the colour blobs must visibly move around the screen*), at most about 90 px/s at 1080p; scale 1.00–1.06; independent of focus; **never restarts on a focus change**; stops when Home is covered, the screen is off, or after 10 minutes without a key |
| NAS title backdrop (content browse) | May wait a **135 ms settle** while artwork decodes; the title's tile still answers at once |
| Metadata reveal | 130–180 ms fade after the item is established |
| Dock to NAS shelf | 260–380 ms, focus retained; full library 280–400 ms with poster continuity |
| Enter/leave Wallpaper | 650–1,000 ms; Wallpaper image changes 500–1,200 ms |
| Overlay dismiss/return | 200–350 ms, exact previous focus restored |
| Smoke/CO emergency, Ring | Immediately; never waits for a decorative animation |

Focus changes never launch an app, play a trailer, connect the VPN, wake the Xbox or start music. OK is the only activation.

### 7.5 Rendering rule

GPU-composited, **pre-rendered** gradient layers: each radial light is a small pre-rendered sprite that is moved, scaled and faded; full-screen gradients are **never continuously repainted**. Two mesh layers crossfade by alpha (Android) or opacity (Tizen CSS, with drift by `transform`). No continuous full-screen blur, `backdrop-filter` or per-frame 4K decode. **Nothing depends on `RenderEffect`**, which TCL Android 11 lacks; on the Sony it may be evaluated for small surfaces only after API and GPU checks. On failure or a frame-rate shortfall, fall back to a static mesh in the correct palette, never a blank screen. Pause animation when backgrounded, in standby, in Wallpaper or under Art Mode. 60 fps is an aspiration measured on each TV, not a claim.

### 7.6 Motion and rendering settings

| Setting | Drift | Crossfade |
|---|---|---|
| Default | On | 400 ms |
| **Keep the Home background still** | Off | 400 ms |
| **Reduce motion** | Off | Instant |
| **Lightweight rendering** | Still frames | 200 ms |

Also high contrast and reduced transparency. Honour system reduced-motion where detectable. Settings change cost, never hierarchy, names or navigation.

### 7.7 Contextual artwork, glass and mood

- Background source order for a NAS title: its verified landscape backdrop > title-matched key art > blurred expansion of the **same** title's poster > neutral backdrop with the title still identified. Cache by item ID; never another item's art, a broken-image icon or a filename.
- Apps without authorised imagery use their own brand-aware palette; never generic scenery, scraped shows or unlicensed posters.
- Glass: one lifted lens for the focused tile, feathered cached blur, fine light top edge, soft contact shadow; no white outlines on every tile, no neon, no glass behind every element.
- Time of day (morning, day, dusk, night) gently changes glass warmth and highlight over 10–20 minutes, never the subject or focus.
- 100 rapid presses must end on the correct tile with the correct background and no stale art.

### 7.8 Visual references

The six palette mockups (SmartTube, Netflix, Disney+, BBC, Live TV, Music), a Ring "Front Door" card mockup and an Amazon Music now-playing mockup were supplied in chat on **25 October 2026** as rough UX ideas. They are **kept outside Git** because they contain third-party logos and album art. The named `references/01-smarttube.jpg`… and `wallpaper-reference-01..05.png` files were **never committed**. Visual comparisons (AM-01, AM-12 and the WP references) are **pending owner references** and do not block implementation. Audit: [REFERENCES.md](design/REFERENCES.md).

Detail: PRODUCT_SPEC.md Sections 4, 32, 33.7.

## 8. Wallpaper

- **Settings → Appearance → Wallpaper:** source `Artwork` (default, OD-03) or `Off`; `My Photos` is a later task. Clock `Depth clock`, `Simple clock`, `No clock`, plus a high-contrast variant. Idle start `Off`, 2, 5 (default), 10, 15 minutes; image interval 30 s, 1, 5, 10 minutes; *Start Wallpaper now*; 12/24-hour, optional date, placement, dimming.
- **Artwork:** free and explicitly licensed for this use, bundled offline, no real-time download to display; source, licence and attribution recorded in [ARTWORK.md](design/ARTWORK.md). Mockups and reference images are never shipped as wallpaper.
- **Depth clock (OD-04):** large refined numerals between the image and a **prepared foreground mask**, so the subject passes in front of the digits. Masks exist for selected artwork only and are prepared offline; **no real-time segmentation on the TCL**. Any other image gets the standard clock. Never fake occlusion or cover a face.
- **My Photos (later):** a separate task whose access method (DLNA folders versus the Synology Photos API) is designed first. Per-TV and per-profile opt-in; private albums never in guest or child mode; no cloud upload or face recognition.
- **Remote:** the first press of D-pad, OK, Back or Home dismisses Wallpaper and shows Home with focus, **without also activating a tile** and without swallowing the press.
- **OLED care:** gentle clock position variation, dimming, prefer no clock overnight; no burn-in claims.
- Wallpaper never overlays another app's playback and never pretends to be native Art Mode.

Detail: PRODUCT_SPEC.md Sections 9, 33.

## 9. Media scope (NAS)

- **Discovery:** with no source configured, the library discovers UPnP/DLNA MediaServers automatically and lists them by name; no typed address for DLNA.
- **Scope:** the household chooses folders on the media server (by the server's object IDs, stored encrypted), marks any as **For Kids**, and can unselect or remove them at any time; nothing on the NAS is changed. A folder chosen twice through a parent shows its titles once. (The owner's own folders, such as Kids, Movie and TV Show, are examples, never hardcoded defaults.) DLNA object IDs are not filesystem paths. No other folder or local music is indexed or shown.
- **Honest DLNA limits:** DLNA provides discovery, browse and direct play. It does **not** provide reliable metadata, posters, search, series structure or server-synchronised progress. *(Changed 25 October 2026 by the owner, OD-18.)* Unified TV builds those on the TV: a saved list of the chosen folders' titles, local search, series and seasons from file and folder names, and posters, backgrounds and summaries matched by title from **TMDB** with the household's own key (set up on the phone, OD-17). Only the cleaned title and year are sent; matching is cautious, so no picture is better than a wrong one. Without a key, tiles are title cards; never invented artwork. This satisfies SET-034's opt-in: nothing is sent until the household adds its key.
- **Pagination:** use SOAP `NumberReturned` and `TotalMatches`, not the count of accepted videos.
- **Playback:** embedded Media3 (Android) and AVPlay (Tizen). VLC excluded; no external player hand-off. Direct play preferred; MKV is a container, not a guarantee; unsupported media fails with a useful message. Measure first frame, seek and buffering per device.
- **Profiles** *(changed 25 October 2026 by the owner; this replaces "a Child profile never uses unauthenticated DLNA")*: Kids may use the DLNA library, restricted to adult-approved **Kids folders** (by object ID, stored encrypted) and by rating metadata where the server supplies it: only UK U/PG and US G/PG/TV-Y/TV-Y7/TV-G/TV-PG; an unrated item only inside a Kids folder. The check runs when browsing, on the Home shelf, in Continue Watching and again at playback. **Honest limit:** DLNA has no accounts, so this restricts Unified TV only; any other DLNA app on the home network can still see every shared folder. Server-enforced restricted accounts (for example a Jellyfin-compatible service) remain the stronger option where available. A launcher PIN does not switch streaming-app accounts or lock the OS.
- **Private authentication:** scoped credentials in headers on Android, credential-bearing redirects refused; never long-lived tokens in URLs. Tizen private playback needs a validated LAN media broker or scoped cookie first.
- **Continue Watching** *(changed 25 October 2026)*: for Unified TV's own player, separate Adults and Kids histories kept on the TV and labelled "on this TV". Server-synchronised progress appears only after an authenticated round-trip is proven on the server.
- An unavailable NAS never blocks Home or installed apps.

Detail: PRODUCT_SPEC.md Sections 0.3, 5, 24.4, 27.2–27.3.

## 10. Alert model

### 10.1 Classes and order

| Order | Class | Contents |
|---|---|---|
| 1 | `EMERGENCY_SMOKE_CO` | Smoke and carbon monoxide alarms, **equal and highest** |
| 2 | `RING_DOORBELL_PERSON` | Ring doorbell press (live camera) and confirmed Ring person detection (a discreet toast only); ordinary motion is ignored (RING-01..05) |
| 3 | `HOUSEHOLD_IMPORTANT` | Detector offline/fault warnings, VPN-protection notices, delivery/arrival |
| 4 | `REMINDER_INFO` | Alexa reminders and other informational toasts |
| 5 | `DECORATIVE` | Ambient drift, Wallpaper transitions, other decoration |

A higher class pre-empts a lower one where the TV OS permits. Ring takes visual priority over household notices, reminders, voice sessions and background transitions.

### 10.2 Interaction rules

- **Ring never covers an active emergency.** A Ring press during any active smoke/CO alarm, acknowledged or not, gets **no camera card**. It is queued, keeping only the **latest** visit and its time, without delaying the emergency display (RING-04). A person event during an emergency shows nothing: its toast would cover the emergency, and a person toast is never queued.
- When **every** alarm has cleared: if the queued visit is **under 45 seconds old**, the camera card is shown; otherwise a small note reads **"Doorbell rang at HH:mm during the alarm"**.
- The emergency display is **never interrupted** by anything and **never dismissed by a timeout**.
- **Reminders arriving during an emergency are dropped.** During a Ring card, a reminder is briefly queued with an expiry, never shown stale.
- Repeated Ring events never build an unbounded queue. A fully off TV is not woken unless a wake policy is later explicitly enabled.

### 10.3 Emergency display

- Full screen, high contrast, large type. Smoke and CO appear as **separate entries**, each with its type, room/name and event time (HA `last_changed`, shown as local HH:mm), for example `SMOKE ALARM — Kitchen — 14:32`.
- **Individual acknowledgement** per alarm. Acknowledgement **never changes, mutes or resets the sensor**.
- After all active alarms are acknowledged, a **persistent visible banner** stays while any alarm is still active, including after returning to the app. **A new alarm brings the full screen back.**
- Detector offline/fault warnings are shown separately as `HOUSEHOLD_IMPORTANT` notices: **never styled as an emergency and never as "safe"**.
- **No "all clear" claim**, ever. When alarms return to `off` the screen simply leaves.
- **Audio:** the app may request transient audio focus with `USAGE_ALARM` where the platform supports it. It must not claim it can pause or mute third-party apps; those apps decide.
- Never colour alone; mesh, clock and photos never obscure the message.
- **Supplementary only.** Never a certified alarm, dispatch service or substitute for working local audible detectors. Never promise detection, delivery, TV wake or overlay over DRM or system screens without physical proof.

### 10.4 Home Assistant alarm state model

Source: Home Assistant `binary_sensor` entities whose `device_class` is `smoke` or `carbon_monoxide`. **Never** a `sensor.*` CO entity, which is a ppm measurement.

| HA state | Meaning in Unified TV |
|---|---|
| `on` | **Alarm** (emergency) |
| `off` | **Normal**: not shown as "safe" or "all clear" |
| `unavailable` / `unknown` | **Detector offline**: a `HOUSEHOLD_IMPORTANT` warning, not an emergency |
| `test`, `fault` | Only when a specific integration exposes them (usually as separate entities); none are assumed |
| HA connection lost | **"Alarm status unknown"**: an active alarm is never cleared by a lost connection |

Show the event time. Reconcile duplicates and reconnect snapshots without suppressing a new genuine alarm.

**Current household:** the owner has **no smoke or CO detectors in Home Assistant**. The framework is built and tested with **simulated events**: an in-app **Test the alarm screen** action in adult household settings, labelled **TEST**, plus unit tests. Real detectors are not assumed. Owner test steps use a Home Assistant template binary sensor with `device_class: smoke` as a stand-in ([OWNER_TESTING.md](OWNER_TESTING.md)). No simulated event counts as an end-to-end detector pass.

### 10.5 Notification payloads

```text
EmergencyAlarmEvent {
  kind: smoke | carbon_monoxide
  entityId                     // HA entity id
  name                         // HA friendly_name, at most 60 characters
  state: alarm | normal | offline | test | fault
  sourceTime                   // HA last_changed
  receivedTime
  simulated: bool              // true for "Test the alarm screen"; shown as TEST
}
RingEvent      { kind: press | person, entityId, sourceTime, receivedTime, camera }
ReminderEvent  { text (at most 240 characters), room?, sourceTime }
DetectorWarning = EmergencyAlarmEvent with state offline | fault
```

No addresses, tokens or camera URLs in any payload or log.

### 10.6 Ring presentation

Owner rules of 25 October 2026 (RING-01..05; they replace the earlier rule that person detection also opens the camera):

| Rule | Event | Behaviour |
|---|---|---|
| **RING-01** | Ordinary motion | Ignored: no notification, sound, camera or interruption. A sensor that only reports motion never counts as a person. |
| **RING-02** | Confirmed person detection | A **discreet toast** naming the doorbell (for example "Person at Front Door") for **5 seconds**. No camera, no playback interruption, no focus or sound. Repeated person events from one sensor within a minute give one toast. Never shown during an emergency. "Confirmed" means the integration itself says person (a Ring Smart Alert / event type of person or human, ring-mqtt's `personDetected`, or a dedicated person sensor). |
| **RING-03** | Doorbell button press | The live camera opens automatically as a **large picture-in-picture card** (about half the screen width, lower right), so current playback stays visible and, where the platform allows, keeps playing; the camera is muted and takes no audio focus. Back dismisses it and focus returns to the previous interface; it also closes after the household's auto-close time (30 s by default; 60 s or manual selectable, SET-051). `Connecting` until real frames arrive; never label a still as live. If no picture arrives within 12 s, or no camera is set up, the card says **"Live view unavailable"** and when the doorbell was pressed. The viewer never has to open the Ring app. |
| **RING-04** | Any Ring event during a smoke/CO emergency | Smoke and CO always come first. A press is kept for later (Section 10.2); a person toast is not shown. |
| **RING-05** | Platforms | The same intended behaviour on TCL, Sony and the Frame, subject to demonstrated platform capability. Limits (overlays over DRM apps, background execution, live-stream access, third-party apps, Frame Art Mode) are documented in Section 10.8 and tested, never assumed. |

In Sony/TCL Wallpaper a press shows the live card and the Wallpaper resumes after it. **Samsung native Art Mode interruption needs a verified household coordinator (launch and first-frame acknowledgement) and is unproven.**

### 10.7 Reminders

Alexa reminder toasts (6–10 s, small corner glass, focus-neutral, no pause) on Sony/TCL with overlay permission; inside the visible Frame app only. No toasts in native Art Mode by default. Source: Home Assistant Alexa Devices (polls about every five minutes; no second-accurate claim). Never fabricate text; privacy option to show only "Alexa reminder"; per-TV opt-in.

### 10.8 Platform limits

If the app is hidden, the TV is asleep, Samsung Art Mode or a DRM app is in front, the OS may block the overlay or wake. Record the actual behaviour per TV (SAFE-06, SAFE-07). A Tizen app cannot draw over other Tizen apps.

Detail: PRODUCT_SPEC.md OD-07 block, Sections 4.10, 8, 16.2, 24.3.

## 11. Household integrations

- **Live TV and NordVPN (P0, security-critical):** the label is Live TV; the app is ReamSmartz. Every route (tile, Spurs card, voice, search, resume) uses one guard. VPN badge states Connected, Disconnected, Checking, Unknown; "Connected" means only that a tunnel is observed. Live TV opens only while NordVPN is connected (OD-26: a VPN is up and it is NordVPN's), and is covered by a full-screen notice if NordVPN drops while it is on; otherwise it stays locked with a plain reason. No silent connect intent is assumed. A scrim (the cover) is not a firewall and never called a kill switch: claiming that no Live TV traffic escapes still needs Android's always-on block proven on that TV, including after standby, DNS and IPv6. LAN access to the NAS and Home Assistant must keep working. Samsung TV Plus is a separate service and is not under the ReamSmartz guard; Frame Live TV stays unavailable unless a native client and verified routing exist.
- **Matchday:** Tottenham men's first-team fixtures; card only on the UK matchday; kick-off time before the match, `Live` during it, **never scores**; hides after a grace period; OK goes through the Live TV guard.
- **Sony Xbox:** wake through a supported integration, wait for ready, switch to HDMI 3 (eARC) through a model-verified input control; clear timeout feedback.
- **Samsung TV Plus:** Frame-only tile opening the native app through a verified launch path; native guide and dependencies protected.
- **Echo Studio (Frame):** reliable audio after leaving Art Mode, by tested Bluetooth persistence or optical fallback; never fake a reconnect.
- **AirPin LITE (TCL, P1, optional):** correct name AirPin LITE (not "AirPro"); guided setup in Settings > Connections; glyph only for a confirmed operating state.
- **Music (P1):** the official Amazon Music app, controlled from inside Unified TV with Android's official media APIs only (OD-27): Now Playing with full control, play by name (Music page and voice), "Your Amazon Music" browsing when Amazon Music allows it, and Play on This TV, an Echo or Everywhere through Home Assistant's Alexa Media Player. Amazon's own web API (closed beta) is not used. A now-playing mockup exists as a rough reference outside Git.
- **Voice (P1):** optional glyph, explicit activation, visible microphone state, Back closes. GPT realtime/API keys live on a protected household service with short-lived client credentials; never in an APK or WGT. Allowlisted commands only; never a route around the Live TV guard. Optional local recogniser only if measured faster.
- **Connections:** Settings > Connections for Home Assistant, Films & TV and AirPlay, fully usable by remote: Enable, host/IP, port, HTTP/HTTPS, Test, Save, Edit, Forget; states Not configured, Reachable, Authentication required, Authorised, Offline. Trusted-LAN HTTP only with explicit consent for that host.
- **Settings layout:** six categories (Connections, Profiles, Notifications, Appearance, TV & performance, Privacy & security) with one changing right panel.
- **Optional TV Performance & Cleanup (P1):** Settings > Updates & About > Advanced > Optional clean-up (OD-25; also from TV Performance & Cleanup), adult-only, read-only check first, per-change consent, disable never uninstall, recorded backup and verified rollback, restore without the launcher, per-firmware allowlists, never Samsung SDB deletion in the normal module. Never automatic, never blanket. See [INSTALLATION_AND_RECOVERY.md](INSTALLATION_AND_RECOVERY.md).

Detail: PRODUCT_SPEC.md Sections 2.1, 6, 7, 11–14, 16.1, 24.5–24.6.

## 12. Signing

- **Android key rotation, correctly stated:** APK Signature Scheme v3 supports key rotation from **Android 9 (API 28)**, so Android 11 is not the obstacle. Rotation requires the **old private key** to sign the new lineage.
- The existing preview `io.unifiedtv.preview` was signed by a **throwaway CI debug key that no longer exists**, so it can never be updated to a new key. Hence the separate launcher package `io.unifiedtv.launcher`, signed with a **durable key from day one**; the preview stays as it is and is never uninstalled or wiped to work around a mismatch.
- Keys are created by the owner **on their own computer** with `preview/tools/make_preview_key.py`. Never in CI, never by an agent, never committed, printed or uploaded. Custody, backup and loss recovery: [SIGNING.md](SIGNING.md). A lost key cannot be assumed recoverable.
- versionCode increases monotonically; record package, version and the public certificate fingerprint (never private material) per release; keep hashes and rollback instructions.
- **Tizen** author and distributor certificates are separate from Android keys; preserve the author identity for same-app updates. A development certificate is not automatically a distribution credential.
- A build is not release-ready until a signed N→N+1 in-place update is proven on that TV (SIGN-03/04/05).

Detail: PRODUCT_SPEC.md OD-08 block, Section 24.1.

## 13. Security and privacy

- Never commit or ship IP addresses, hostnames, tokens, credentials, serial numbers, device IDs, ADB keys, keystores, certificates, family photos or camera frames, in source, APK/WGT defaults, CI logs, screenshots or handovers. Use symbolic placeholders.
- Android secrets in Keystore-backed encrypted storage, excluded from backup, removable by Forget. Tizen secrets in Key Manager, failing closed.
- No tokens in URLs, logs or query strings. Refuse credential-bearing redirects. Prefer authenticated HTTPS; trusted-LAN HTTP only with explicit consent.
- Never expose Home Assistant or MQTT publicly; least-privilege credentials. Disable ADB/network debugging after setup; confirm closed.
- No telemetry upload by default; local performance logs exclude photos, media paths, doorbell images and tokens.
- Never disable the stock launcher (except through OD-28's consented, recorded and reversible Home takeover on a tested-list TV), Smart Hub, Art Mode, HDMI-CEC, volume, accessibility or security controls. Factory preloads are turned off only through the optional clean-up (OD-25): tested firmware list, per-app consent, recovery record, undo. No root, uninstall, firmware changes or service-menu edits.
- Public mockups use cleared or original imagery; third-party logos and album art stay outside Git.

Detail: PRODUCT_SPEC.md authority section, Sections 2.1, 16, 24.4.

## 14. Performance targets (to be measured, not claimed)

| Metric | Target |
|---|---|
| Focus feedback | p95 under 100 ms over 100 rapid presses; focus always responds before images |
| Background correctness | Final item after rapid input shows its own art or fallback; never stale |
| Animation | 60 fps where sustainable; reduce effects, never responsiveness |
| Warm return to Home | Under 2 s on each TV |
| Offline | Installed apps and Wallpaper usable without NAS, HA or metadata providers |
| Memory | Budget from measured TCL RAM; bounded caches; obsolete requests cancelled |
| Idle/OLED | Bright UI fades; no fixed bright regions for long periods |

Third-party claims (for example "90 % faster", 500 ms 4K start) are not Unified TV results.

Detail: PRODUCT_SPEC.md Sections 4.12, 16, 16.3, 32.5, 33.6.

## 15. Acceptance tests

Each ID has its full pass condition in PRODUCT_SPEC.md; its status is in the register. One line each.

### 15.1 Core journeys (AT, PRODUCT_SPEC.md Sections 17 and 23)

| ID | Pass condition |
|---|---|
| AT-01 | Netflix → Disney+ → BBC: each tile lifts, background changes smoothly, remote stays responsive |
| AT-02 | NAS film: matching poster/backdrop; plays in the embedded player with sound and subtitles |
| AT-03 | View All → Series → season → episode plays in the embedded player; no folder interface |
| AT-04 | Live TV with VPN off sends no unprotected data; useful blocked state |
| AT-05 | VPN drop during ReamSmartz: no unprotected stream continues; explanation shown |
| AT-06 | Spurs card on matchday only, correct UK kick-off, never scores, opens guarded Live TV |
| AT-07 | Ring press in Wallpaper/Art Mode shows live camera automatically and restores the prior state |
| AT-08 | Ring person detection shows a 5 s toast naming the doorbell, no camera; ordinary motion shows nothing (RING-01/02) |
| AT-09 | Ring during Netflix/music: camera visible per verified OS behaviour; playback and audio restored |
| AT-10 | Frame Art Mode with no event: picture only; no clock, VPN, sports, controls or tiles |
| AT-11 | Sony Xbox: console wakes, HDMI 3 selected; no Xbox tile elsewhere |
| AT-12 | Frame leaving Art Mode for BBC/Netflix: Echo Studio audio without manual reconnect |
| AT-13 | Sony and TCL: 100 fast D-pad changes after standby meet latency targets, no crash or stuck art |
| AT-14 | Header shows real UK time, verified VPN state and gradual evening lighting |
| AT-15 | Music exposes only controls proven by the chosen Amazon path |
| AT-16 | Voice "Find a film on my NAS": shortlist, clear listening state, Back dismisses |
| AT-17 | After reboot: launcher, permissions, HA triggers and restoration survive |
| AT-18 | Skipping optional cleanup: launcher fully works, no packages changed, no repeated prompt |
| AT-19 | TV Performance on each TV: common entry, correct OS detected, nothing changed without opt-in |
| AT-20 | Android cleanup preview: model/firmware allowlist, rationale, risks, dry run; no change before confirmation |
| AT-21 | After one cleanup batch: streaming, remote, VPN, Ring, HDMI/audio and standby all still work |
| AT-22 | Undo last batch then Restore all: original package states back; TV usable without the launcher |
| AT-23 | Frame TV Performance: Tizen-specific guidance; no Android commands; factory apps not promised removable |
| AT-24 | Frame: unsupported SDB/root cleanup refused; Art Mode, audio, recovery and updates preserved |
| AT-25 | Frame performance settings offer only supported native operations |
| AT-26 | Android Alexa reminder toast over Netflix/BBC for 6–10 s without focus or pause; latency recorded |
| AT-27 | Edited or cancelled reminder: no old message; only confirmed revisions shown |
| AT-28 | Same reminder on several sensors: at most one toast per opted-in TV |
| AT-29 | Reminder and Ring together: Ring wins; no stale reminder later |
| AT-30 | Frame reminder: toast inside the visible app; no third-party overlay claim |
| AT-31 | Frame Art Mode reminder with defaults: artwork unobstructed |
| AT-32 | Unknown TV platform: read-only cleanup guidance only |
| AT-33 | Frame TV Plus tile: correct branding; native TV Plus opens with channels and guide; no tile on Sony/TCL |
| AT-34 | TV Plus does not inherit the ReamSmartz VPN guard; Live TV stays guarded |
| AT-35 | Ring during TV Plus: live camera as the highest event after any active emergency; return recorded |
| AT-36 | Frame cleanup cannot disable TV Plus or its dependencies; regression passes |
| AT-37 | Neutral Home: full image, one low dock, one clock, tiny utilities; no content wall |
| AT-38 | Netflix, BBC, Music, Live TV focus: background clearly related or own brand fallback |
| AT-39 | Three films and a series: every backdrop and metadata matches its item ID; no stale art |
| AT-40 | Hold Right across all tiles: focus keeps up; final background belongs to the final app |
| AT-41 | Down then Up: one NAS row emerges, dock recedes, Up restores dock focus, no white flash |
| AT-42 | View All → Series → season → episode and Back: consistent visuals; Back restores focus |
| AT-43 | Same focus states on all three TVs: shared layout and names; reduced profile only cuts cost |
| AT-44 | Bright snowy background: labels readable from the sofa |
| AT-45 | Missing hero asset or network: brand or neutral fallback; no unrelated image or freeze |
| AT-46 | Day/dusk/night boundary: only warmth changes; artwork, position and focus stable |
| AT-47 | Sony/TCL idle: dock and clock vanish; no static bright controls; input restores focus |
| AT-48 | Frame Art Mode: artwork only at any time of day |
| AT-49 | A Ring press in any state shows the camera immediately (queued if an emergency is active) and restores exactly |
| AT-50 | Reminder during Netflix then Ring: reminder stays small; Ring wins; no lingering toast |
| AT-51 | Voice then Back: small pane; identical Home focus and backdrop afterwards |
| AT-52 | Live TV focus never starts VPN or Live TV; OK opens the guarded Connect/Retry/Back flow |
| AT-53 | Music: real album art drives background; idle state reduces bright UI |
| AT-54 | Reduce motion, high contrast, reduced transparency: everything available; focus unmistakable |
| AT-55 | Design hand-off: cleared assets, focus maps, tokens and motion studies for all Section 4.13 states |
| AT-56 | Saved layout migration keeps valid owner edits; fresh install gets six defaults; empty dock recoverable |
| AT-57 | Hold OK, Move, OK, restart: reorder persists |
| AT-58 | Reorder then Back restores order; a Ring alert does not half-save |
| AT-59 | Open/Move/Add/Remove/App info by remote; edits persist; Child edits need adult; removing never uninstalls |
| AT-60 | Per-TV dock order and Xbox/TV Plus shortcuts correct; Live TV guarded |
| AT-61 | Six/seven-item dock readable with child lock and reduced motion |
| AT-62 | TCL Connections by remote: correct states, protected storage, offline fallback |
| AT-63 | TCL Windows pilot: separate preview installs; stock Home untouched; clean uninstall of a test build |
| AT-64 | AirPin LITE after setup and restart: real receiver status; resources measured |
| AT-65 | 4K/HD NAS file in Media3 and AVPlay: audio/subtitles where supported; first frame measured |
| AT-66 | VPN drop during ReamSmartz on TCL/Sony: no leak, otherwise stays blocked |
| AT-67 | Six defaults, add an eighth, rearrange: one uncluttered dock, per-TV persistence |
| AT-68 | Frame: return from Ring to native Art Mode and TV Plus |
| AT-69 | Voice opens BBC and answers a general question in UK English with visible microphone |
| AT-70 | Public repo and APK contain no LAN IP, SSID, MAC, camera images or secrets |
| AT-71 | Child cannot browse or play Adult-only items by any route; own restricted token |
| AT-72 | PIN guards adult return and editing; recovery and repeated failures tested |
| AT-73 | Real position reported on pause/stop and reloaded from the server; Continue Watching hidden until proven |
| AT-74 | Credential redirects and non-consented HTTP refused; IPv4/IPv6/hostnames accepted; Forget removes data |
| AT-75 | Native TV settings still control sound, picture, Wi-Fi, Bluetooth and CEC |

### 15.2 Samsung native (NT, PRODUCT_SPEC.md Section 25.1)

| ID | Pass condition |
|---|---|
| NT-01 | Standby, cold boot and reboot tested separately; autostart only where supported, else documented |
| NT-02 | AVPlay plays representative files with audio, subtitles, seek, resume; first frame recorded |
| NT-03 | Ring press and person during native Netflix reach the coordinator; launch and first frame acknowledged |
| NT-04 | Native TV Plus discovered and launched; guide kept; documented return to our app |
| NT-05 | Home opens Smart Hub; Back inside the app predictable; long Return keeps OS exit |
| NT-06 | Repeated hide/resume restores focus and revalidates network and media; no hidden-JS event claims |
| NT-07 | Wrong-profile items, unsafe redirects, expired sessions and plain HTTP refused; no tokens stored or logged |
| NT-08 | Ring returns Art Mode to the prior artwork; reminders do not interrupt it; an off TV stays off |
| NT-09 | Android-only services shown honestly unavailable; no fake launch |
| NT-10 | No traffic escapes on VPN loss or DNS/IPv6 bypass; else the tile stays unavailable |
| NT-11 | Signed WGT installs; same-author update keeps settings; rollback keeps Smart Hub and TV Plus |
| NT-12 | Visual and remote parity across codebases, including 100 rapid inputs and edit/save/cancel |

### 15.3 Continuation checks (PRODUCT_SPEC.md Section 26)

| ID | Pass condition |
|---|---|
| UX-01 | Two-column Settings fully navigable by remote, with correct focus and Back |
| UX-02 | Editable Home: six defaults, menu actions, persistence, cancel, protected-app and child checks |
| LIB-01 | No source → automatic discovery; NAS appears by name; timeout and Retry usable |
| LIB-02 | Only the three chosen roots are viewer collections |
| LIB-03 | DLNA direct play, pause, seek, stop and return on each runtime; unsupported codecs fail clearly |
| LIB-04 | Jellyfin progress reaches the server; DLNA-only playback never labelled synchronised |
| GAL-01 | Five authorised example images load, rotate, fit and respond to remote controls |
| VPN-01 | Four distinct VPN states; no state alone unlocks Live TV; unlock only by OD-26's rule (a VPN is up and it is NordVPN's); a no-leak claim still needs route and drop proven on that TV |
| MUS-01 | Official Amazon Music opens, or the store route if missing |
| MUS-02 | Authorised API access proven before any custom Music claim |
| REL-01 | Each release keeps tag, APK, hash, notes, limitations and results; nothing overwritten |
| REL-02 | Screenshots come from the exact physically tested build |

### 15.4 Preview 0.5 viewing journey (P05, gate G3; PRODUCT_SPEC.md Section 27.2)

| ID | Pass condition |
|---|---|
| P05-01 | Signature, package and data checked before install; same-key update keeps settings; factory Home intact |
| P05-02 | Six defaults, correct artwork or fallback, legible glass/focus, predictable D-pad/OK/Back |
| P05-03 | Apps grid and long-OK menu; edits persist; cancel does not save; child protected; Live TV guarded everywhere |
| P05-04 | NAS discovered by name on the TCL; Retry; no server distinguished from failed authorisation |
| P05-05 | Only the chosen roots by object ID; no other collection; Kids confined to Kids folders and allowed ratings; survives NAS restart |
| P05-06 | Real content browsed without pagination loss; accurate titles or honest fallback |
| P05-07 | A real NAS video plays in Media3 with pause, seek, stop, Back; format and first frame recorded |
| P05-08 | NAS/Wi-Fi loss, app switch, standby and cancel recover without spinners or lost focus |
| P05-09 | All six Settings categories reachable; large text and reduced motion keep controls usable |
| P05-10 | Live TV locked unless NordVPN is connected (OD-26); no private data in logs; versioned APK, hash and report kept |

### 15.5 Remote and Home ownership (RC, HRC; PRODUCT_SPEC.md Sections 28.4, 29.5)

| ID | Pass condition |
|---|---|
| RC-01 | TCL preview app: Home opens stock Home as disclosed; Unified relaunches; nothing changed silently |
| RC-02 | TCL launcher app as chosen default: Home from Unified, Netflix and another app returns to Unified |
| RC-03 | RC-02 repeated independently on the Sony |
| RC-04 | Back dismisses overlays, cancels unsaved edits, returns to origin and stops at Unified Home |
| RC-05 | Every button on each remote recorded as received, OS-reserved, third-party-owned, unsupported or not-present |
| RC-06 | Netflix/Disney+/BBC round trip returns with correct focus and no stale hero |
| RC-07 | Cold boot, standby, crash/uninstall and network loss keep remote, sound, stock recovery and re-entry |
| RC-08 | HDMI-CEC, tuner/Guide and TV Plus work without bypassing the VPN guard |
| RC-09 | Frame WGT: Home/Back/Guide/Source/voice/Art Mode outcomes and return path captured |
| RC-10 | TCL and Sony: restore manufacturer Home without PC, root, ADB or a working Unified app |
| RC-11 | Fast repeat, long OK, held Back, CEC: one predictable action each |
| HRC-01 | TCL: the previously problematic button no longer strands the viewer; exact case fixed or classified |
| HRC-02 | TCL: Home from Unified, Netflix, Disney+, BBC returns to Unified on the first press after default selection |
| HRC-03 | TCL: repeated and long Back never exits the Unified root unexpectedly |
| HRC-04 | TCL: power cycle, standby, network loss and crash recover to Unified Home or a usable stock fallback |
| HRC-05 | Sony: HRC-02/03/04 on its own remote and firmware |
| HRC-06 | Sony: Source and Xbox on HDMI 3 with eARC work; Home returns where the OS allows |
| HRC-07 | Frame: Home/Smart Hub destination and fastest supported return filmed; not called seamless |
| HRC-08 | Frame: 123 keypad, Return, Source, channel, power and Art Mode behave correctly |
| HRC-09 | All three: one signed-off key map per TV, including long presses and app shortcuts |
| HRC-10 | Android TVs: factory Home restored after default selection, even if Unified crashes; no PC or ADB |
| HRC-11 | All three: 20 round trips across apps and standby with no stranded state or lost focus |

### 15.6 Original-interface escape (OI; PRODUCT_SPEC.md Section 30.4)

| ID | Pass condition |
|---|---|
| OI-01 | Small "Open original TV interface" row at the bottom of Advanced; not on Home |
| OI-02 | Open and Cancel: clear choices, focus kept, nothing changed |
| OI-03 | TCL temporary visit opens the TCL interface only if supported; Unified stays default; Home returns |
| OI-04 | Sony temporary visit verified independently |
| OI-05 | TCL/Sony permanent original Home only after confirmation and system selection; Unified restorable by remote |
| OI-06 | Frame opens Smart Hub by supported means or instructs Home; no "make default" option |
| OI-07 | Child profile and rapid presses cause no accidental default change |
| OI-08 | Power cycle, crash and restore: both choices recoverable without PC, ADB, root or disabled packages |

### 15.7 Samsung CleanFrame research (CF; G5/G5P; PRODUCT_SPEC.md Section 31.6)

| ID | Pass condition |
|---|---|
| CF-01 | S1: owner firmware, runtime, architecture, SDB capability and Smart Remote inventory verified |
| CF-02 | S2: signed Unified TV WGT installed with the owner's identity and certificates |
| CF-03 | S3: read-only feasibility and differences from the LS03D reference documented |
| CF-04 | S4: original values (including absent ones) preserved; independent recovery demonstrable before any write |
| CF-05 | S5: the owner separately authorises a specific reviewed experiment |
| CF-06 | S6 (only if approved): one guarded apply, readback, exact restore, original Home observed |
| CF-07 | S7: Home from Netflix and another app returns to Unified on the owner's Frame |
| CF-08 | S7: Settings escape opens Smart Hub; Home returns to Unified without changing saved routing |
| CF-09 | S7: Art Mode, standby/wake, power-on and cold restart each work |
| CF-10 | S7: crash, update and uninstall never leave Home pointing at a missing app |

### 15.8 Ambient mesh (AM, gate G2; PRODUCT_SPEC.md Section 32.7)

| ID | Pass condition |
|---|---|
| AM-01 | Each of six palettes recognisable; title, halo and focus always match (mockup comparison pending owner references) |
| AM-02 | One press: 400 ms crossfade starting immediately; no flash, cut or text blur |
| AM-03 | Rapid presses: latest app wins; no queue, stale palette or accidental launch |
| AM-04 | 60 s on one tile: gentle drift within the rule; no loop, jitter or pulsing |
| AM-05 | Keep still / Reduce motion / Lightweight: drift stops; crossfade 400 ms / instant / 200 ms |
| AM-06 | All six states legible on hardware; no banding |
| AM-07 | Frame pacing, memory and key latency acceptable; no growth or crash |
| AM-08 | App launch, return, Settings and Live TV sheet keep correct palette and focus |
| AM-09 | Standby, Art Mode and Wallpaper pause and resume animation correctly |
| AM-10 | Forced fallback shows the static palette with full navigation |
| AM-11 | Reordered dock and unknown app: palette follows identity; neutral fallback |
| AM-12 | TCL, Sony, Frame compared with the owner's mockups (pending owner references) |

AM-01–03, AM-06–09 and AM-11 are required before the animated Home is complete on a TV.

### 15.9 Wallpaper (WP, gate G2; PRODUCT_SPEC.md Section 33.8)

| ID | Pass condition |
|---|---|
| WP-01 | Wallpaper settings discoverable, D-pad operable, saved per TV/profile |
| WP-02 | Artwork appears after the idle interval; dock, header and VPN vanish |
| WP-03 | My Photos album shows only authorised images (later task) |
| WP-04 | Prepared-mask artwork: clock sits behind the subject with clean edges |
| WP-05 | Image without a mask: standard or no clock; no fake depth or covered face |
| WP-06 | Minute rollover, 12/24-hour and date accurate; no flash |
| WP-07 | First D-pad/OK/Back/Home shows Home with focus; never launches an app |
| WP-08 | Netflix/Disney+/BBC and back: Wallpaper never overlays playback |
| WP-09 | After Wallpaper, rapid dock movement shows the 400 ms mesh, independent of image caching |
| WP-10 | NAS loss in My Photos falls back to cached or Artwork safely (later task) |
| WP-11 | Profile switch hides private albums unless authorised (later task) |
| WP-12 | Frame native Art Mode intact; Wallpaper not presented as Art Mode |
| WP-13 | No main-thread decode or segmentation stalls on TCL, Sony, Frame |
| WP-14 | Reduced motion, high contrast and OLED protection keep the clock readable |
| WP-15 | Ring (and a simulated emergency) during Wallpaper follow the alert model and restore state |
| WP-16 | Original-interface escape reachable after Wallpaper |

### 15.10 Smoke and CO (SAFE, gate G6; PRODUCT_SPEC.md OD-07)

| ID | Pass condition |
|---|---|
| SAFE-01 | Smoke alarm during Home: full-screen entry with type, room, HH:mm |
| SAFE-02 | CO alarm during Wallpaper: same, pre-empting Wallpaper |
| SAFE-03 | Alarm pre-empts the Ring camera; Ring queued (latest kept); after all clear, card if under 45 s, else the HH:mm note; reminders dropped |
| SAFE-04 | Concurrent smoke and CO stay separate entries; acknowledging one never dismisses the other |
| SAFE-05 | Offline, unknown, test or fault never claim an alarm or safety; lost HA shows "Alarm status unknown" |
| SAFE-06 | During Netflix/DRM: actual delivery and honest limitation recorded |
| SAFE-07 | Standby, Samsung Art Mode and TV-off limitations recorded |
| SAFE-08 | Reconnect, duplicate and out-of-order events reconciled without hiding a new alarm |
| SAFE-09 | Per-alarm acknowledgement never resets the sensor; banner stays while any alarm is active; a new alarm restores the full screen |
| SAFE-10 | Accessible (not colour alone), no private data logged, no "all clear" |

A simulated or TEST event never counts as an end-to-end detector pass.

### 15.11 Signing (SIGN, gates G0/G1/G4/G5; PRODUCT_SPEC.md OD-08)

| ID | Pass condition |
|---|---|
| SIGN-01 | Key custody documented; no secret in Git, logs, docs or screenshots |
| SIGN-02 | Android package IDs, versions and certificate fingerprints audited |
| SIGN-03 | TCL: signed N→N+1 in-place update keeps settings, Home choice and recovery |
| SIGN-04 | Sony: same as SIGN-03 |
| SIGN-05 | Frame: author/distributor-signed WGT N→N+1 keeps settings, Smart Hub and Art Mode |
| SIGN-06 | A signature mismatch never uninstalls or wipes the existing app |
| SIGN-07 | Signed artefact manifest with hash and version; independent rollback instructions |
| SIGN-08 | Key backup and rotation feasibility documented without exposing key material |

### 15.11a Ring event rules (RING, gate G6; Section 10.6)

| ID | Test |
|---|---|
| RING-01 | Walk past the doorbell without a person alert (or trigger motion only): nothing appears or sounds on any TV |
| RING-02 | A confirmed person alert: a toast naming the doorbell for about 5 s, no camera, playback continues; a second alert within a minute gives no second toast; no toast during an alarm test |
| RING-03 | Press the doorbell during Home, during playback and in Wallpaper: the live picture-in-picture card appears without input, playback stays visible, Back closes it and focus returns where it was; with the camera unreachable, "Live view unavailable" appears |
| RING-04 | Press the doorbell during an alarm TEST: no camera over the alarm; after ending the test, the card or the "Doorbell rang at HH:mm" note |
| RING-05 | RING-01..04 on the TCL, the Sony and the Frame, each limitation recorded with the model and firmware |

### 15.11b Product structure, profiles and Kids (owner, 25 October 2026; Section 2)

| ID | Test |
|---|---|
| LIB-DOCK-01 | A Library dock item can be added, moved and removed; OK opens the library; Back returns to the same tile with the scene unchanged |
| PROFILE-01 | After a restart the TV opens in the last-used profile without asking |
| PROFILE-02 | Switching to Adults asks for the PIN every time (lock-out after repeated failures); switching to Kids needs no PIN |
| KIDS-01 | In Kids only Kids-folder items within the allowed ratings appear anywhere (library, shelf, Continue Watching), and a stale link to an adult item is refused at playback |
| KIDS-02 | In Kids, a non-approved app cannot be opened from Unified TV by any route (dock, Apps, voice, search) |
| KIDS-03 | With the Kids app guard on, a non-approved app opened another way returns to Unified TV Home; its limits are shown honestly |
| KIDS-04 | "Show person alerts in Kids" off: no person toast in Kids; doorbell press and smoke/CO unchanged |
| CW-01 | Adults and Kids have separate Continue Watching for Unified TV's player, surviving restart and profile switches |
| DIAG-01 | Owner diagnostics (Settings, Adults, PIN) shows NAS discovery and catalogue, Home Assistant/Ring, Live TV's VPN state and its facts (OD-26), version and signing, without secrets |
| SEARCH-READY-01 | Library, Continue Watching and Kids policy share one catalogue item model (code review; no search UI) |

### 15.12 Hardware and service gates (GATE; PRODUCT_SPEC.md Sections 2, 2.1, 5–8)

| ID | Pass condition |
|---|---|
| GATE-TV-01 | Exact model, firmware, OS, memory and storage of each TV recorded privately |
| GATE-TV-02 | Native Tizen app validated on the Frame; no external streamer needed |
| GATE-CONN-01 | TCL remote can enter valid/invalid HA and Jellyfin endpoints; precise feedback |
| GATE-CONN-02 | Protected token storage, offline Home, no leakage to diagnostics |
| GATE-NAS-01 | Film, subtitled film and episode play in each embedded player on hardware |
| GATE-NAS-02 | Matching artwork, local access and server authorisation with the VPN policy active |
| GATE-NAS-03 | Watch-state round-trip through profile switch and restart |
| GATE-PLAYER-01 | Per-device codec/audio/subtitle matrix published |
| GATE-PLAYER-02 | Unsupported playback blocked without any VLC fallback |
| GATE-VPN-01 | VPN off: Live TV traffic fails |
| GATE-VPN-02 | VPN dropped mid-stream: no packets escape |
| GATE-VPN-03 | Sony, TCL and Frame pass independently |
| GATE-VPN-04 | No unprotected ReamSmartz packets after tunnel loss |
| GATE-VPN-05 | Normal LAN access and other apps unaffected under the chosen routing |
| GATE-AIRPLAY-01 | AirPin start-up, iPhone discovery, casting and return after restart; resources measured |
| GATE-RING-01 | All three TVs in Wallpaper/Art Mode show real Ring video for a doorbell press without input; a person gives only the toast |
| GATE-RING-02 | Dismiss restores the correct image and TV mode |
| GATE-RING-03 | Ring during Netflix/Live TV/music without lost playback |
| GATE-RING-04 | Correct after standby, HA restart, Ring delay, network loss and duplicates |

### 15.13 Capability tracking IDs (register)

| ID | Capability |
|---|---|
| G0-01 | Current Git/branch/build/package/signing inventory |
| G0-02 | Separate preview and opt-in launcher apps; owner selects TCL launcher after rollback proof |
| DOCK-01 | Editable six-app dock and long-OK menu |
| NAS-01 | DLNA discovery and exact selected roots |
| NAS-02 | Poster catalogue, search and Continue Watching through an authenticated source |
| PLAY-01 | Embedded NAS playback with audio, subtitles, seek and recovery |
| SONY-01 | Sony default Home, remote and Xbox HDMI 3 |
| FRAME-01 | Native signed Tizen app and Smart Remote |
| FRAME-02 | Native Art Mode and TV Plus preserved |
| FRAME-03 | CleanFrame-style Home reassignment: proposal only (G5P) |
| RING-01 | Doorbell and person events, live camera and restoration |
| PROFILE-01 | Child-safe NAS and profile separation |
| MUSIC-01 | Official Amazon Music hand-off |
| VOICE-01 | Voice, reminders and other optional integrations |
| ALERT-01 | Smoke/CO alarm display above Ring, as specified in Section 10 |

## 16. Outside initial scope

No mandatory bloatware removal; no Samsung SDB deletion in the normal module; no root, silent package changes or always-on ADB; no Plex-style interface; no VLC tile or player; no generic YouTube tile; no scoreboard; no permanent clock on native Frame Art Mode; no recommendation feeds or poster walls on Home; no unrelated wallpaper behind a highlighted item; no real-time full-screen refraction; no trailers on focus; no waking a fully off TV for Ring unless separately authorised; no unproven Amazon catalogue claims; no advertising or mandatory sign-in.

## 17. Document map

| Document | Role |
|---|---|
| `docs/SPEC.md` (this file) | Authoritative current specification |
| [PRODUCT_SPEC.md](PRODUCT_SPEC.md) | v3.3 detailed requirements, corrected in place |
| [SPEC_CHANGES_v3.3.md](SPEC_CHANGES_v3.3.md) | Corrections made for v3.3 |
| [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv) | **The only status record** |
| [FEATURE_STATUS.md](FEATURE_STATUS.md) | Readable summary of the register |
| [AI_HANDOVER.md](AI_HANDOVER.md) | Continuation guide for agents |
| [OWNER_TESTING.md](OWNER_TESTING.md) | Owner-run hardware test steps and evidence reporting |
| [SIGNING.md](SIGNING.md) | Key creation, custody, backup and update rules |
| [REFERENCES.md](design/REFERENCES.md) | Audit of visual references kept outside Git |
| [ARTWORK.md](design/ARTWORK.md) | Wallpaper artwork sources, licences and attribution |
| [COMPATIBILITY.md](COMPATIBILITY.md) | Supported versus unproven capabilities |
| [VPN_PROTECTION.md](VPN_PROTECTION.md) | Live TV VPN strategy |
| [INSTALLATION_AND_RECOVERY.md](INSTALLATION_AND_RECOVERY.md) | Install, consent, backup and rollback |
| [SPEC_RECONCILIATION.md](SPEC_RECONCILIATION.md) | Pointer for older reconciliation notes |
| [archive/](archive/README.md) | History only; never instructions |

*This specification describes requirements for a reference household and a reusable system. It is not a claim that any integration has been verified on the household's hardware.*
