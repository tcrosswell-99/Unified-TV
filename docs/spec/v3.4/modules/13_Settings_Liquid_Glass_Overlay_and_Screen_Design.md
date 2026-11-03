# Module 13 — Settings Liquid Glass Overlay & Screen-by-Screen Design (v3.4.7)

**Status: NORMATIVE OWNER-APPROVED DESIGN DIRECTION.** This module supersedes older references to a split-screen Settings page, a Home layout reflow, or a permanent opaque sidebar. Read alongside `modules/12_Settings_Onboarding_and_Configuration.md` (feature contracts), `modules/05_UX_Motion_Design.md` (Home visual language), Core, Safety and Testing. Where a Settings presentation rule conflicts, **this module wins**; feature safety/capability requirements still win over appearance. Nothing in this document is proof of implemented code or hardware support.

## A. Product vision — what it must feel like

**Design objective:** Opening Settings feels like a thin, beautifully crafted layer of luminous glass gliding over the TV you were just enjoying. The underlying Home stays *exactly where it is*. The user sees their familiar wallpaper, clock and dock behind the glass; there is no page switch, no shrinking or sliding of Home, no split-screen layout. Settings is calm, premium, fast, readable from a sofa, and remote-first. Inspired by Apple's **Liquid Glass** material qualities—translucency, edge light, depth and smooth movement—but use **original Unified TV components**, not an exact Apple UI reproduction or proprietary artwork.

**SET-GLASS-01 (non-negotiable):** Settings overlays Home. Opening it must **never** translate, resize, recenter, reflow, zoom or push the wallpaper, dock, clock or utility pill. Render a separate composited top layer that enters from screen-right. Home remains a full-screen, stationary background. When Settings is dismissed, Home must match its pre-open geometry pixel-for-pixel (apart from natural ambient animation and any intentional live preview changes).

**SET-GLASS-02 (width):** Standard overlay is **50% of safe viewport width** (target 48–52%, not hard-coded pixels). It anchors flush to the right screen edge, with a subtle illuminated left glass boundary. For complex flows, expand **over Home** to ~70–80% width; use full-screen modal only for compelling reasons such as secure account authorization, PIN entry or platform-managed handoff. Never push Home. On 720p/small displays, ensure minimum readable controls; adapt width if 50% is not usable. Maintain TV overscan-safe margins for content.

**SET-GLASS-03 (surface):** One coherent curved glass sheet, dark-tinted and translucent, not a solid grey sidebar. Material layering: (1) translucent charcoal/navy tint with background sampling where supported, (2) restrained frosted blur of *only the content behind the panel*, (3) fine white/blue edge highlight and soft internal top rim, (4) extremely subtle specular sheen reacting to panel entry/focus, (5) low-noise shadow separating panel from Home. **No mirror-like distortion, water ripples, prismatic rainbow edges, glossy bubbles or distracting perpetual reflections.** Avoid copying Apple control geometry one-for-one.

**SET-GLASS-04 (readability):** Glass is decorative; text clarity is mandatory. Use local dark scrims/raised translucent cards behind dense controls; never let bright wallpaper make labels unreadable. White/off-white primary text, muted blue-grey secondary text; clear focus outlines. Target WCAG-equivalent text contrast >=4.5:1 where applicable, non-text focus indicators >=3:1, and verify visually at typical 10-foot TV distance. Avoid tiny phone-style controls. Icon-only controls require accessible names and focus help.

**SET-GLASS-05 (motion):** Slide from right to rest in **250–350 ms**, ease-out with no rubber-band bounce; closing reverses smoothly. Expanding 50→75% feels like one glass sheet widening, not opening another app. Motion-reduced mode: short crossfade/instant overlay without slide. Respect OS reduced-motion where available. Keep ambient mesh subtle behind the panel; optionally reduce its intensity while Settings is open. Freeze nonessential Home focus animations. Avoid frame drops: if blur is expensive use pre-blurred backdrop or translucent static tint; never compromise D-pad responsiveness for shader fidelity.

**SET-GLASS-06 (focus and input):** Opening from top-right Settings gear transfers focus **directly into overlay**; no intermediary menu. The Home UI underneath is visible but **non-interactive** while Settings is open. D-pad navigates Settings; OK selects; Back from detail returns to parent retaining last focused row; Back from Settings root closes overlay and restores focus to the Settings gear in the top-right pill. Focus must not leak to dock, Home or platform behind overlay. Home button follows OS rules and never traps the user. If a platform-managed store/sign-in opens, preserve wizard state and restore sensible focus on return.

**SET-GLASS-07 (progressive disclosure):** Root Settings shows compact categories with glyph + 2–4-word title + optional tiny state indicator. No paragraphs. Detail screens use one-line subtitles only where useful, segmented controls, thumbnails, choice cards, toggles, device tiles and live previews. Advanced/diagnostic controls stay in collapsed **Advanced** sections. Every setting must have an understandable state and next action. Never show fake connected/installed/protected states.

**SET-GLASS-08 (composition):** 16:9 TV reference canvas 1920×1080; standard panel begins near x≈960 and covers right half. Internal content safe padding ~48–64 px at 1080p (scale with resolution); header at top with back chevron (detail), screen title, optional help/close icon. Scrollable body, anchored action footer only when essential. Primary focus targets ~56–72 px high at 1080p, generous vertical spacing. Values align to right, labels left; max two lines for routine items. Avoid horizontal scroll for standard settings. Panel's left edge may be softly rounded and illuminated; right edge may meet screen boundary. Responsive scaling, not fixed pixel replication.

**SET-GLASS-09 (Home preview):** Appearance edits should visibly update the Home *underneath* the translucent panel, without moving it. Live previews for wallpaper, dock 6/8, label modes, ambient intensity and clock, with Cancel/Restore when changes may harm usability. In other categories, do not change Home merely to animate the overlay. Changes should be scoped per Adults/Kids where specified.

**SET-GLASS-10 (content hierarchy):** Root has category list in **one overlay**; selecting a category replaces its internal content while panel stays anchored. A category with many controls can use an internal two-column **grid of cards** *inside the overlay* if readable; do not confuse this with a split-screen Home+Settings layout. Default is a clean single-column detail flow with card groups. Show position/scroll affordance unobtrusively. Category order matches module 12; Apps & Shortcuts can be surfaced prominently in first-run contexts.

### Visual tokens (starting points; calibrate on real TV)

| Token | Intended appearance / behaviour |
|---|---|
| Backdrop | Approved Home red/charcoal/blue atmospheric mesh, stationary |
| Panel tint | Dark blue-charcoal translucency, approximate 65–80% perceived opacity behind text; tune dynamically for contrast |
| Blur | Frosted background blur equivalent ~16–30 px at 1080p where supported; static fallback |
| Edge | Hairline light border with subtle white/ice-blue highlight; no neon outline |
| Corner | Soft outer rounding on exposed left side, approximately 20–28 px at 1080p |
| Raised card | Slightly brighter translucent layer; minimal outline and shadow |
| Focus | Soft lift/illumination, crisp visible border, ~120–200 ms transition; never rely on colour alone |
| Typography | Clean TV-scale sans-serif, generous line height; headline ~30–38 px, row title ~23–27 px, secondary ~18–22 px at 1080p, validate on devices |
| Colour | Mostly white/off-white, desaturated blue-grey, restrained pale-blue focus; no multicolour settings icons |
| Toggle | One clear binary control; label and state readable without colour discrimination |
| Select | Segmented buttons for 2–3 choices; focused selection visually distinct from saved selection |
| Scroll | Vertical D-pad scroll with retained focus and no hidden selected row |

## B. Navigation map and overlay state machine

`Home:SettingsGear → OPENING → SettingsRoot(50%) → CategoryDetail(50%) → OptionalExpandedTask(70–80%) → CategoryDetail → SettingsRoot → CLOSING → Home:SettingsGear`.

- OPENING/closing is interrupt-safe: rapid OK/Back must not leave a half-visible focus trap.
- Root row remembers its focus when returning from detail. Detail remembers last row when returning from subpage.
- A panel expansion must not change focus identity or reorder controls unexpectedly; collapse restores prior focus and scroll position.
- Native permission/store/sign-in dialogs may be outside overlay control; never spoof OS permission screens. Resume with honest status after returning.
- For 8-icon dock, the underlying Home stays fixed; only its intentional dock live-preview state changes when user edits capacity.
- Modal confirmation for destructive actions uses centered, focused, glass-styled dialog above overlay; Home remains below both layers.
- Busy states show a short spinner/progress and Cancel where safe; failures show Retry/Skip/Help rather than silent resets.

## C. Screen inventory — exact visible elements and actions

Each screen uses **header + short optional subtitle + grouped visual controls**. Listed controls are requirements subject to verified platform capability; unsupported features must be hidden or disabled with a clear reason, never fabricated.

### 00 — Settings root (50%)

**Header:** `Settings` plus discreet close glyph. **Body:** 10 category rows with monochrome glyph, name, optional status chip and right chevron: `Appearance & Home` (palette), `Apps & Shortcuts` (grid), `Library & Media` (film/hard-drive), `Profiles & Family` (users), `Notifications & Doorbell` (bell), `Smart Home` (house), `Live TV & Network` (TV/shield), `Connections & Devices` (cast), `Privacy & Data` (shield/lock), `Updates & About` (info). Group first 4 as Personalise, next 4 as Connected services, final 2 as System with *small* group labels if needed. **No wall of descriptive copy.** A row's small state may say `2 services`, `NAS found`, `Not connected`, `Update available`, but only if verified. **Interaction:** Up/Down rows, OK detail, Back closes. Root should be usable within 2–3 seconds of opening on target hardware.

### 01 — Appearance & Home (50%; live Home behind)

**Header:** `Appearance & Home`, small subtitle `Make it yours`. **Cards:**
1. **Wallpaper:** horizontal row of 3–5 artwork thumbnail previews, selected check; `Browse wallpapers` opens wider gallery; `Use my photos` opt-in and permissions.
2. **Dock & Icons:** large **live six/eight-icon miniature preview**; segmented `6 icons | 8 icons` (default 6), `App labels` segmented `Focused only | Always | Hidden` (default Focused only), `Edit dock` button. Switching 8→6 requires explicit management of extra shortcuts; never uninstall.
3. **Ambient background:** `Motion` (`Normal | Reduced | Off`), `Intensity` (`Subtle | Standard`), optional `Colour response` (`Neutral | Follow focus`); preview updates behind panel.
4. **Clock:** `Show clock` toggle, style/position thumbnails, depth effect `Auto | Off` only with prepared masks.
5. **Comfort & OLED:** `Dim when idle`, `Static element protection` status; do not override native Sony/Samsung protections.
6. **Advanced appearance** collapsed; restore appearance defaults with confirmation.
**Interaction:** left/right within segmented controls; OK thumbnails; Back returns. Preview state must not make controls unreadable.

### 02 — Apps & Shortcuts (50% overview; 75% picker/editor)

**Header:** `Apps & Shortcuts`, subtitle `Choose what lives on Home`. **Overview cards:**
1. **Your dock:** accurate 6/8-slot visual strip, count `4 of 6` etc., `Edit & reorder` expands panel. Selected apps show minimalist glyph and focused labels per preference.
2. **Build My TV:** prominent `Set up entertainment` / `Review services` card; completion state is truthful, optional to skip.
3. **Music:** card showing detected installed services and `Default: Spotify` only when chosen/known; `Change` opens chooser.
4. **Video & Live TV:** selected services and defaults with source/protection status.
5. **Installed apps:** remote-navigable icon grid, search deferred; `Add shortcut` only for launchable apps.
6. **Find more apps:** opens native approved store where supported; explicit confirmation, never silent installation.
**Multiple music services:** when >1 detected, present selection list with checkboxes `Show in Unified TV` for each, radio `Default Music app` among selected, and placement `Music group` or `Separate dock shortcuts` (subject to available dock slots). Do not automatically pick one. Exactly one installed music app → auto-configure and skip question; zero → optional Music Yes/No; detection unknown → manual selection without assuming absent. Preserve previous user choices on rescan. Native store handoff + rescan on return.
**Expanded dock editor:** visual row of 6/8 slots; D-pad pick-up/move/place (or accessible move-left/right actions), Remove, Replace, Cancel, Save; clear focus and capacity indicators. Do not auto-fill new slots.

### 03 — Library & Media (50% overview; 75% folder browser)

**Header:** `Library & Media`, subtitle `Your films and shows`. **Cards:** `Discovered servers` with device name, NAS/DLNA glyph, online/last-seen state; `Films folders`, `TV folders`, `Preferred source` if multiple; `Scan library` with progress/time; `Artwork & metadata` thumbnail style; `Playback` subtitles/audio/resume; `Photos` optional integration; `Troubleshooting` collapsible. **No manual IP/port/subnet fields in consumer flow.** Empty state: illustration of a media library, `Looking for your media...`, `Retry`, `Continue without Library`; avoid claiming discovery on Tizen before device proof. Expanded folder browser uses breadcrumb and selectable folder cards.

### 04 — Profiles & Family (50%; PIN modal)

**Header:** `Profiles & Family`. Two large cards: `Adults` and `Kids`, with restrained distinct avatar glyphs and current-profile check. **Adults detail:** dock/layout preference, history, optional profile switch protection. **Kids detail:** `Set/change PIN`, `Allowed apps` thumbnail grid, `Allowed library content`, `Viewing history`, `Restrictions status`, `Kids dock`, `App label visibility`. Kids security must not be falsely promised when OS/system app routes bypass restrictions. PIN input uses OS-safe secure entry, obscured digits and confirmation; never reveal existing PIN. Separate histories, dock sizes and label preferences. No extra household profiles in initial scope.

### 05 — Notifications & Doorbell (50%; overlay preview card)

**Header:** `Notifications & Doorbell`. **Cards:** `Ring` status `Connected/Not connected` and connect action; `Person alerts` toggle with **~5-second small toast preview** (ordinary motion ignored); `Doorbell press` `Live view when supported` with a sample PiP-shaped preview; `Sound & quiet hours` concise controls; `Notification test` only for synthetic UI preview clearly marked TEST; `Emergency sources` real connected smoke/CO status, priority explanation in a single short line. **Never** imply detectors exist when absent. Emergency alerts always outrank Ring, cannot be suppressed by quiet hours, and require real supported sources. Live view limitations explicit.

### 06 — Smart Home (50%; 75% room picker)

**Header:** `Smart Home`. **Cards:** `Home Assistant` connection state and connect/disconnect; `Rooms` tile grid from discovered rooms; `Favourite scenes` (e.g. Movie Night only if actually configured); `Lighting` on/off/brightness with current-state indication; `Heating` temperature/status and `Boost` only if integration supports it; `Devices` count; `Manage permissions` / `Advanced`. Never hardcode an owner's rooms, bulbs, or Hive setup for other households. Empty state `Connect a smart home` and Skip. Device action shows progress, confirmation or failure.

### 07 — Live TV & Network (50%; warning/diagnostics expansion)

**Header:** `Live TV & Network`. **Top status card:** `Protected` / `Not protected` / `Unknown` based on verified route, not icon colour or app installed state. **Cards:** `Live TV app/source` selection; `VPN provider` detected compatible service (NordVPN only if actually installed/configured); `Protection requirement` explained succinctly, **fail-closed cannot be silently disabled**; `Connection status` and `Check protection` action; `Favourite channels` if supported; `Network health` friendly connected/offline status; `Troubleshoot` advanced. If VPN cannot be verified, Live TV remains locked with one-line reason and Retry/Help. No fake protection claims.

### 08 — Connections & Devices (50%; device picker expansion)

**Header:** `Connections & Devices`. **Cards:** `Casting / AirPlay` with receiver state and availability; `Audio output` current device when OS exposes it; `Bluetooth` paired devices where permitted; `Remote` help/test and focus hints; `External devices` supported handoff/HDMI information; `Network devices` discovered endpoints with friendly names; `Connection diagnostics` advanced. Use connected device cards and status chips, not raw MAC/IP values. Tizen/Android system-managed settings should deep-link to native UI when supported; otherwise explain limitation. Never suggest app controls native Samsung Art Mode.

### 09 — Privacy & Data (50%; confirmation modals)

**Header:** `Privacy & Data`. **Cards:** `Viewing history` view/clear per profile; `Personal photos` permission/source and remove cached photos; `Connected accounts` review/revoke; `Diagnostics sharing` opt-in only; `Backup & restore` with sensitive token exclusions; `Reset appearance` / `Reset all preferences` / `Factory reset Unified TV` separated into **Danger zone**. Each destructive action shows exactly what will be removed, requires deliberate OK confirmation, supports Cancel, and does not uninstall third-party apps. Do not use deceptive toggles or assume permissions.

### 10 — Updates & About (50%; advanced logs 75%)

**Header:** `Updates & About`. **Top card:** Unified TV logo/glyph, version/build, platform and `Up to date / Update available / Unable to check` truthful status. **Cards:** `Check for updates`, `What's new`, `Update channel` only if supported, `Compatibility` supported/limited features, `Help & support`, `Licences`, `Recovery & safety`, `Owner diagnostics` collapsed. Expanded logs may show technical data; never clutter normal screen. No auto-install of unsupported update packages; maintain signing, recovery and Tizen constraints.

## D. Shared micro-interactions and empty/error states

- **Selected vs focused:** Focus is where remote is pointing; selected is persisted value. Use separate border/filled state; don't make them indistinguishable.
- **Toggle:** OK changes state, brief haptic-equivalent visual response (no mandatory sound), label says On/Off. Disable only with explanatory reason.
- **Picker:** Open in-place glass card when 2–4 options; expand panel for long app/device lists. Restore focus to triggering row on close.
- **Connection card:** `Connected`, `Connecting`, `Not connected`, `Permission needed`, `Unavailable` with accurate icon + short action; never a meaningless green dot alone.
- **Loading:** Show subtle progress, bounded timeout, Retry/Skip; Home stays visible, overlay doesn't disappear unexpectedly.
- **Empty:** One glyph, one short sentence, one primary action, optional Skip. No large paragraphs.
- **Error:** Explain what happened in plain language, what is safe to try next; do not dump stack traces outside Advanced.
- **Tooltips:** Icon-only controls get focus label; do not permanently overlay text on approved Home.
- **Overscan/long text:** Dynamic layout, safe margins, ellipsize with focused detail/tooltip; localization-ready.
- **Privacy:** Never expose account tokens, Wi-Fi passwords, PINs or private photo previews in screenshots/logs by default.

## E. Engineering / platform constraints

**Android TV / Google TV:** Prefer a composited panel over a full-screen launcher surface, with blur only if efficient and supported. Avoid a separate OS window that steals focus unpredictably. Preview build **must not register as HOME**. Ensure Back and native app/store handoffs restore focus and allow recovery.

**Samsung Tizen 2021 Frame:** Implement glass effect using supported web compositor/CSS and performant fallback; don't assume CSS `backdrop-filter` works reliably at required resolution. Test on physical TV; avoid unverified background blur cost. Never replace or interfere with native Art Mode or Smart Hub. Do not claim universal app enumeration, store installation or casting APIs.

**Across platforms:** Separate data schema (setting state), capabilities (what hardware/platform allows) and view (glass presentation). Device performance dictates fallback material, **not** changing overlay-vs-push interaction. Remote focus and accessible text always take precedence over effects.

## F. Acceptance tests — add to live register, don't self-certify

| ID | Acceptance scenario | Required evidence |
|---|---|---|
| GLASS-01 | Open Settings: Home geometry unchanged, panel slides over right ~50% | Same-resolution before/after screenshots and layout comparison |
| GLASS-02 | Back closes overlay, returns focus to Settings gear; no focus leakage | Remote video or key-event log on real TV |
| GLASS-03 | Detail → expanded 75% → detail → root, no Home reflow | Video and screenshots |
| GLASS-04 | Wallpaper and 6/8 dock preview updates Home behind panel, Cancel works | Before/after screenshot sequence |
| GLASS-05 | Focused-only label default; Adults/Kids settings independent | Profile switch/restart evidence |
| GLASS-06 | Multiple music apps: user chooses default and one/both/all; one auto-configures | Mocked tests + installed-app real device evidence |
| GLASS-07 | No app silent installs; native store return rechecks status | User-run installation/cancel test |
| GLASS-08 | Contrast, 10-foot readability, long names, overscan and reduced motion | Photos and QA checklist |
| GLASS-09 | Blur fallback preserves FPS, D-pad latency and readable contrast | Device profiling + screenshots |
| GLASS-10 | Unsupported integrations honestly labelled; Live TV VPN fail-closed; emergency priority | Platform-specific state tests |
| GLASS-11 | No trap after repeated rapid Open/Back or native permissions | Remote stress test |
| GLASS-12 | Samsung Art Mode and Android preview recovery unaffected | Owner-run platform proof |

**Implementation order:** (1) audit existing Settings/navigation and acceptance register, (2) build overlay shell + focus trap/back restore, (3) glass material/fallback + responsive width, (4) root and shared controls, (5) Appearance live preview, (6) Apps/Build My TV and 6/8 dock editor, (7) other categories incrementally behind capability gates, (8) automated unit/UI tests, (9) owner-run TCL then Sony/Samsung physical verification. Do not mark acceptance as hardware-tested before evidence.

## G. Explicit non-goals

No reflowing Home into a left column; no 50/50 split layout; no opaque Settings app page; no pixel-for-pixel Apple clone; no illegible transparent text; no always-on glass shimmer; no automatic app installation; no unverified Samsung app-store or network APIs; no silent bypass of VPN/Kids/safety rules.
