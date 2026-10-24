<!-- Unified TV: public reference specification. No account credentials, local addresses, family photographs or private configuration belong in this repository. -->

> **Status: design requirements, not shipping claims.** This specification describes a reference household and a reusable system. Hardware abilities and third-party integrations require real-device validation. No local network credentials are published here.

# Household TV Experience: Product and Technical Specification

**Version:** 2.2 consolidated draft | **Date:** 24 October 2026 | **Status:** Consolidated requirements; native platform release gates outstanding

> **North star: Content first, underlying technology hidden.**
>
> The television should make it effortless to find and enjoy programmes, films, music and games. The person watching should not need to understand playback engines, the NAS, Jellyfin, Home Assistant, VPN connections or which operating system is running.

**Design baseline for the fork:** The quiet, full-screen, one-dock visual direction in the household's CleanFrame-style reference image is the approved starting point. A selected item must change the *whole* backdrop to imagery genuinely related to that app or NAS title. The detailed motion, glass, typography, progressive-reveal, accessibility and performance rules in Section 4 are mandatory acceptance criteria. Third-party references guide principles; our application must use original, appropriately licensed assets and its own implementation.

## 0. Latest owner decisions and document precedence

This is the complete consolidated product requirement, including the detailed earlier visual, functional and acceptance sections below. It describes the target product, not a statement that every feature is built. Read `FEATURE_STATUS.md` for implemented behaviour and evidence, and `AI_HANDOVER.md` for continuation instructions. Where older proposal/reconciliation/history text conflicts, this section and the current numbered requirement take precedence.

### 0.1 Delivery and platform decisions

- Native Android TV application for TCL and Sony; separate native Tizen Web Application for Samsung Frame. Do not make an external streamer a prerequisite for Frame.
- Keep the factory Home application, native Samsung Smart Hub, Samsung TV Plus and native Art Mode intact. The preview remains separately installable and does not register as Android HOME.
- Tizen startup, cross-app foreground launch, native Art Mode interruption, DRM overlays and Back behaviour are device test gates. Neither developer access nor a SmartThings API name proves these work. A cold boot and standby resume are separate tests.
- Feature parity means the same experience where supported, with truthful platform-specific availability. Do not fabricate SmartTube, VPN or native app support on Tizen.
- Install supported official apps where authorised and available. The owner's later request permits an independently installed VLC as an auxiliary tool, but Unified TV playback must remain embedded Media3/AVPlay. Do not require or invoke VLC as the library player.
- Work in versioned branches/releases; preserve previous releases, test evidence and rollback instructions. Never overwrite another agent's work, force-push, reset shared files, or merge unrelated work merely to publish a preview.

### 0.2 Latest UX decisions

- Keep premium liquid-glass material: feathered cached blur, light top edge, restrained reflection, lifted focus lens and soft contact shadow. Avoid flat opaque panels and harsh outlines except accessibility settings.
- Brand ambience retains depth; it must not become a heavy opaque red/blue overlay. Selected media artwork is correctly matched to its actual item.
- No permanent directional text under the header. Use a discoverable downward chevron for Films & TV.
- Apps uses compact cards and a remote-friendly grid. Hold OK opens Open, Move, Add/Remove from Home, App information. Curated defaults are retained at first setup; owner edits are allowed by this latest request.
- Settings uses six left-hand categories and one changing right-hand panel: Connections, Profiles, Notifications, Appearance, TV & performance, Privacy & security. Right/OK enters controls; Left returns to categories; Back returns predictably without losing focus.
- Empty library/gallery screens introduce the benefit and offer useful setup. Sources are shown only where supported.
- VPN state has Connected, Disconnected, Checking and Unknown with a clear icon. Connected refers only to detected tunnel connectivity until provider, routing and drop protection have separately passed. No automatic Live TV unlock on a status icon.

### 0.3 Latest library and music scope

- On first library entry with no configured source, automatically discover local UPnP/DLNA MediaServers. The primary path is a named server list and selection; no typed address is required for DLNA.
- Save only explicitly selected folder roots. The reference household scope is exactly `/video/Kids`, `/video/Movie`, `/video/TV Show`; ignore every other folder and local music. These logical collection names are authorised product requirements, not hardcoded server defaults. Discover object IDs locally and store them encrypted; do not assume DLNA object IDs equal filesystem paths.
- The default must not browse all server media once a source is saved. A child profile may not use unauthenticated DLNA to bypass account restrictions. Keep server-verified restricted accounts as the separate child pathway.
- DLNA folder listing and direct play are an initial connectivity implementation. The agreed poster-first catalogue, title detail, series hierarchy, search and Continue Watching remain requirements. DLNA itself does not establish Jellyfin progress synchronisation or reliable metadata.
- A NAS administration page is not a media API. Discover the actual ContentDirectory service. Do not change firmware, index unrelated folders, install a heavyweight server without checking compatibility, or claim that any MKV must play without conversion.
- First-version Music uses Amazon Music only. Install/open its official TV application where available. Local music indexing is out of scope for this version.
- Custom minimalist Now Playing, account playlists and searches remain desired. Implement them only with supported, authorised service access. Normal consumer sign-in is not proof of access to Amazon's closed-beta APIs. Use the official app as the truthful interim destination.

### 0.4 Images, private setup and acceptance

- The owner supplied five AI-generated JPGs and expressly authorised their inclusion as example gallery images in the repository. Preserve their originals, aspect ratio and existing borders; do not treat these as private family photos. Other private household photos/camera feeds remain excluded.
- Gallery rotates selected images, has timing and remote controls, no permanent UI, reduced-motion behaviour and sensible fitting. Local/USB/document-provider/network sources appear only when supported.
- Household hostnames, addresses, tokens, accounts, device IDs and signing secrets belong only in private local setup and encrypted device storage. Never publish them in Git, APK defaults, Actions logs, screenshots or handovers. Use symbolic placeholders in commands and keep the actual local inventory outside the repository.
- Testing must state exact build and level: policy/unit, simulated Android, local build/lint, PC network observation, and physical TV acceptance. These levels are not interchangeable.
- Take revised screenshots only from the tested revised APK and label their version. Existing 0.3 screenshots must never be presented as 0.4 evidence.

## 1. Product scope and non-negotiables

Build a premium, remote-first, Apple TV-inspired home experience for three household TVs. The layout, motion language, media catalogue and primary controls should feel the same everywhere; only deliberate device-specific features differ. Keep the interface fast on all hardware: the TCL has limited resources, the once-premium Sony is now becoming sluggish, and Samsung Frame/Tizen should also benefit from appropriate maintenance.

**P0 means required for release; P1 means agreed feature that can follow the stable foundation; P2 means an optional enhancement.** A technical dependency marked **GATE** must pass on actual hardware before promising the feature is working. An existing product claim is not a substitute for a device test.

### Guiding rules

1. **Content first:** no advertising, sponsored recommendations, crowded menu panels or technical plumbing in the foreground.
2. **One consistent interface:** same 16:9 layout, artwork treatment, animations, navigation and shared preferences on Sony, TCL and Samsung.
3. **Ring is mandatory:** a doorbell press **or person detection** automatically interrupts active wallpaper / ambient / Frame Art Mode and opens the live camera without pressing the remote; restore the previous state afterwards.
4. **Live TV is VPN-protected:** ReamSmartz must not send traffic without a verified NordVPN connection. This applies to all ways of launching Live TV, including the Spurs card and voice commands.
5. **Artwork is artwork:** on Samsung Frame Art Mode there is **no clock, VPN badge, widget, app row or permanent UI**. A triggered Ring camera is the deliberate exception.
6. **Hide the plumbing:** use embedded Media3/ExoPlayer on Android and Samsung AVPlay on Tizen. VLC is excluded from the application and playback path. Jellyfin / Synology / Home Assistant are not consumer-facing names in normal browsing.
7. **Optional performance cleanup on all TVs:** present one consistent, opt-in **TV Performance & Cleanup** entry for Sony, TCL and Samsung Frame. It must detect the underlying platform and show only operations proven safe and supported on that model/firmware. Android and Tizen have different permissions; a universal interface does **not** imply universal access to system packages. Never compromise DRM, remote, Wi-Fi, HDMI, Bluetooth, sound, Art Mode, updates, casting, accessibility or recovery.
8. **Alexa reminders:** while actively watching video, a due Alexa reminder should appear as a small, temporary toast above the picture, with its reminder text where the source exposes it. It must not pause playback, hijack focus or compete with a Ring event. Device/OS overlay restrictions are an explicit release gate.
9. **Preserve Samsung TV Plus:** Samsung Frame keeps its existing native Samsung TV Plus service, with a Frame-only branded tile. This is a separate source from ReamSmartz and is not subject to the ReamSmartz NordVPN gate. The optional Samsung cleanup must protect TV Plus and all required dependencies.
10. **No fabricated capabilities:** distinguish desired behaviour from what each firmware, app and service has been tested to support.

### Reconciliation of the shorter v1.7 architecture proposal

The short v1.7 proposal contains useful technical suggestions, but **this full v2.0 document supersedes it without deleting previously agreed requirements**. Adopt local on-TV Connections and a safe Windows-to-TCL Stage 0.5. Evaluate optional AirPin LITE, split tunnelling, hybrid voice commands and Media3 as engineering alternatives rather than declaring them working.

Keep six shared entertainment tiles as an **editable default**, not an immutable allowlist. Keep Samsung's native Art Mode and Samsung TV Plus through hardware-specific Tizen/Android handoffs, the existing optional reversible cleanup, Alexa reminder toasts and both Ring triggers. Do not claim guaranteed sub-500ms 4K NAS playback, undocumented NordVPN control Intents or a VPN kill switch based only on a connection icon. See [v1.7 architecture review](ARCHITECTURE_REVIEW_V1_7.md).


### Consolidation of the latest native proposal

Version 2.0 incorporates the owner's shorter v1.9 proposal and retains the complete design, editable dock, profiles, accessibility, household features and acceptance history below. The chosen delivery is two native codebases: Android for Sony/TCL and a Tizen Web Application for the Frame. The same visual and remote contracts apply, with honest OS capability differences. Sections 24 and 25 define the revised native platform contract and evidence gates. Those sections supersede any residual historical reference to an external Frame streamer or VLC. No external box is required. The four newly supplied Samsung tests use NT identifiers because AT-58 to AT-61 already identify dock editing journeys.

The proposal's automatic boot launch, arbitrary MKV direct play, SmartThings force-closing Netflix, and cross-app Back return are desired outcomes to investigate, not guaranteed Samsung APIs. Keep Ring interruption mandatory as a release goal; an unproven launch/Art Mode route blocks that capability's release rather than being labelled working.

## 2. Devices and runtime

| ID | Device | Runtime plan | Specific features |
|---|---|---|---|
| TV-SONY | Sony BRAVIA 65-inch OLED, Google TV | Native Android TV launcher | Xbox tile; Xbox on HDMI 3 (eARC connector); OLED-safe ambient mode; **equal-priority performance baseline + optional Android cleanup** |
| TV-TCL | TCL 55-inch, **Android TV 11 confirmed from TV settings** | Same Android launcher, lightweight rendering profile | Equal-priority performance baseline; installed **AirPin LITE** receiver; optional Android cleanup only after independent tests and owner consent |
| TV-FRAME | Samsung The Frame, Tizen | Native Tizen Web Application; Samsung Smart Hub remains the system Home | Native AVPlay; model-gated Art Mode and Ring handoff; retain Samsung TV Plus; Echo Studio audio path subject to tests; supported-settings maintenance only |

**GATE-TV-01:** Record exact model, firmware, OS version, accessible memory and storage privately. TCL Android TV 11 is confirmed; a Google TV-branded interface is not assumed. **GATE-TV-02:** validate the native Tizen Web Application on the Frame. No external streaming device is required by this product plan. Android-only services remain explicit capability gaps until a legitimate native alternative is approved.

The rendering system uses one shared design and per-TV capability configuration, for example `sony`, `tcl`, `frame`. No separate home-screen design forks. Maintain independent volume, wallpaper, brightness and camera-overlay preferences per display. Shared media listings and app ordering should update across the household without exposing configuration tools to viewers. The performance tool should offer the same entry point and language across devices but use *device capability adapters*; future LG webOS, Fire TV and other brands require separate, tested adapters rather than pretending Android tooling runs on every OS.

### 2.1 Local connections and on-TV configuration (P0)

External developers and GitHub Actions cannot assume access to the household's private LAN. The user must be able to configure Home Assistant Green, NAS/Jellyfin and optional room devices entirely with the TV remote.

- **Settings > Connections** includes **Home Assistant**, **Your Films & TV**, and optional **Device Actions / AirPlay**. Each connection has Enable, Hostname/IP, Port, HTTP/HTTPS, Test, Save, Edit and Forget. The OS on-screen keyboard and D-pad must suffice without a Windows PC. Optional device discovery is advisory, never mandatory.
- Allow IPv4, IPv6 and hostnames including local mDNS names. Never hardcode the household's actual TCL, NAS or Home Assistant IP, Wi-Fi network, MAC address, credentials or camera URLs into public code, GitHub documentation or APK assets.
- Show **Not configured**, **Reachable**, **Authentication required**, **Authorised** and **Offline** accurately. Reachability is not proof that Ring triggers, NAS media streaming or Xbox control work.
- Protect credentials using Android Keystore-backed encryption or a protected household service. Never place tokens in plaintext preferences, logs, URL query strings or published screenshots. Prefer authenticated HTTPS; if trusted-LAN HTTP is the only option, require explicit user consent and scope it to the entered host.
- Per-TV credentials are private and separate. Children can still use Home normally while an adult PIN protects editing. Unavailable integrations must not prevent basic app launching or the dock from working.

**GATE-CONN-01:** use the TCL remote to enter valid and invalid HA/Jellyfin endpoints, test/save/forget and verify precise feedback. **GATE-CONN-02:** verify protected token storage, offline Home operation and no leakage to public diagnostics.

## 3. Home screen information architecture (P0)

**Primary 16:9 structure, from top to bottom and progressively revealed:**

1. **Subtle header:** one live 24-hour UK clock (default top-left, per the selected visual reference), with only the small truthful NordVPN state/exit flag, an optional ChatGPT glyph and a quiet Settings/More affordance at top-right. No news ticker, weather bar or second menu.
2. **Single glass app dock:** a centred, low-profile translucent strip in the lower portion of Home. The six shared choices are `SmartTube`, `Netflix`, `Disney+`, `BBC` (iPlayer), `Live TV` (ReamSmartz) and `Music` (Amazon Music experience). Sony alone adds `Xbox`; Frame alone adds `Samsung TV Plus`. **No VLC tile.**
3. **Your Films & TV:** one curated horizontal row of real Synology artwork with **View All**, *revealed by pressing Down* from the dock. It should not crowd the initial resting screen. The row rises naturally while the app dock becomes secondary; Up returns to the dock. The full catalogue is another deliberate action.
4. **Matchday card:** a small Tottenham Hotspur card appears only on the scheduled UK fixture date and only in a place that does not obscure the contextual hero. No fixture means no gap.
5. **Transient interaction layer:** Ring live camera, Alexa reminders, VPN launch checks and voice UI arrive only at their respective moments. They do not occupy permanent content slots.

**Background contract:** if an app tile or NAS title is visibly selected, its own appropriate artwork or brand-aware fallback controls the full-screen backdrop. The user's scenic/family wallpaper is only for a genuine neutral/no-focus state or artwork mode, never a misleading background behind a highlighted unrelated item. This corrects the previous generic-landscape mockup mismatch. The detailed state machine, durations, visual tokens and acceptance rules are in **Section 4**.

**Progressive disclosure:** keep only one visual focus and one dominant image at a time. Do not add a persistent second poster shelf, editorial banner, rows of promotions or separate side menus to the default Home. The settings interface and full media catalogue are intentionally separate destinations.

### 3.1 Remote interaction contract (P0)

- Directional pad changes focus predictably; **OK** activates; **Back** returns one navigation level; **Home** follows the OS: stock Home in the preview, our launcher only after a separately authorised Android Home change, and Samsung Smart Hub on Tizen.
- Focus must be obvious even without background images: tile scale, elevation/contrast and accessible outline. Do not use colour alone.
- On entering Home, restore the last relevant focus position; when dismissing an overlay, restore the underlying screen and playback state.
- **Hold OK** on a Home tile to access **Move**, **Remove from Home** and optional **App info**; short OK always launches. A parental edit lock may require a PIN. All editing works with the ordinary remote.
- Fast repeated D-pad presses must stay responsive. Cancel or coalesce outdated artwork requests so focus is never blocked by network imagery.
- When content is missing/offline, show a short human explanation and a safe next action, never an exception stack or raw URL.

### 3.2 Samsung-only TV Plus user journey (P0, GATE)

- Home shows **Samsung TV Plus** only on the Frame, using its familiar service identity and the same premium focus animation as other tiles.
- OK transfers viewing from our custom launcher to **native Samsung TV Plus**; retain the native channel guide and account/service setup already on the television.
- If our launcher is running on a separate Android HDMI device, the bridge must command the Samsung native OS to launch TV Plus; HDMI/input and native-app switching is subject to model-specific Home Assistant/Samsung API testing.
- Ring is still mandatory while native Samsung TV Plus plays: it must trigger the automatic live camera takeover and return path. A standard Android overlay cannot render over the native Tizen app.
- Alexa text overlays during native TV Plus playback are not promised without an independently tested Samsung-supported mechanism; this limitation must be visible in compatibility documentation.
- The cleanup module must mark Samsung TV Plus and dependencies as **protected** and exclude them from disable/remove proposals.

### 3.3 Curated defaults and owner-managed Home

The latest owner request restores an Apps screen with Open, Move, Add to Home / Remove from Home, and App information. This supersedes v2.1's fixed-membership decision. Keep the six curated defaults on a fresh installation; permit deliberate owner edits and preserve those edits. Adding/removing a shortcut does not install/uninstall its application. Reordering is remote-only, with OK to save and Back to cancel. Apply the same protected Live TV gate in Home, Apps, voice, search and matchday routes. Child edits require adult authorisation. Platform-only shortcuts must not leak between room profiles. The 0.4 implementation still has the older fixed-membership policy; editable Apps is an outstanding requirement, not a completed feature.

## 4. Premium interface and interaction system (P0: design release gate)

### 4.1 Creative direction and what it is not

**Experience statement:** A personal cinema with the calm of a gallery. **Content first, underlying technology hidden.** The launcher must be a distinctive, original design inspired by the *principles* of the tvOS 26/27 Liquid Glass era, not a pixel-perfect reproduction of Apple's screens or code. Apple introduced the Liquid Glass redesign in tvOS 26; tvOS 27 is the current reference point for its continued refinement. Our primary non-Apple visual reference is the user-supplied image of a quiet coastal backdrop with one translucent dock, a clock and three small top controls. Supporting references are CleanFrame, Vision Stream and the TV Interface motion study. No reference artwork, film still, logo, proprietary typeface, animation file or UI layout is cleared for redistribution merely because it is visible online.

**The visible result must feel:** quiet, cinematic, light in visual weight, instantly responsive, confident at living-room viewing distances, and personal to the household. Fluid motion adds clarity, never delay. The same personality applies on Sony, TCL and the native Frame application. Samsung's *native* Art Mode remains separate and unmodified except for the required Ring takeover.

**Design priorities, in order:**

1. **Relevance:** The image filling the screen must represent the currently highlighted app, film or programme. A generic landscape behind a highlighted Netflix, BBC or NAS tile **fails review**. Scenic or family wallpaper belongs to the neutral or artwork state.
2. **Clarity:** Only one primary selection and one dominant visual story at a time. Users should identify the focused action in under a second from the sofa.
3. **Restraint:** One principal translucent app dock on the resting Home. No permanent walls of posters, sidebars, content adverts, weather bars, duplicate clocks or competing recommendations.
4. **Continuity:** Focus, artwork, metadata and transition elements respond as parts of one scene rather than as isolated animated cards.
5. **Responsiveness:** The remote takes precedence over animation and network imagery, particularly on the ageing Sony and lower-memory TCL.
6. **Context:** A relevant function appears when needed, then withdraws: NAS catalogue when pressing Down, Spurs card on matchday, Ring on an event, Alexa reminder when due, and voice only when invited.
7. **Originality:** Implement our own materials, spacing, silhouettes and timings. Never describe, market or package the launcher as an official Apple interface.

### 4.2 Screen geometry, visual hierarchy and safe space

Design at a **1920 × 1080 logical 16:9 canvas**, then scale proportionally to both 55-inch and 65-inch televisions, checking actual safe areas on each. Treat the outer ~4-5% of width and ~5-6% of height as a no-critical-content margin. Main artwork extends to every screen edge; key text, icons, tiles and alert controls do not. Fit seven app tiles on Sony/Frame without text truncation. Smaller screens retain the same proportions, not a compact web/mobile layout.

**Resting Home composition:**

- **Background:** one beautiful, full-bleed image covering nearly the entire screen, with local contrast treatments only where needed. Do not put a solid full-screen dark panel over it. This image is either the user's neutral wallpaper before a selection, or the *correct contextual hero art for the focused item*. Artwork may remain largely unobscured above the dock.
- **Clock:** one restrained, white, 24-hour UK clock, default **upper-left** to suit the chosen reference image; optional upper-right preference if desired. It is not a card. Never show seconds or permanently animate the digits.
- **Status/utility controls:** a very small, aligned upper-right group: truthful NordVPN state and verified exit-country flag, ChatGPT voice affordance, and a compact Settings/More control. Keep visual separation; no row of seven icons. An optional gallery shortcut belongs in Settings or as a single quiet utility, not as a second navigation bar.
- **App dock:** one wide, low, rounded glass strip spanning the lower third, but occupying only roughly 15-20% of screen height. Six shared service tiles are shown, plus the appropriate Sony Xbox or Samsung TV Plus tile. The strip is the *only significant glass panel* on resting Home.
- **Under-dock hint:** show a subtle, discoverable `Films & TV` / downward-chevron hint on initial use; retire or soften it after the user has successfully visited the NAS row several times. It must not resemble a separate content shelf.
- **Content metadata:** not permanently visible. Introduce only one short title/line when focus rests on an app or NAS title whose artwork is shown. Let the picture carry most of the meaning.
- **Spurs matchday:** an elegant temporary compact card, placed away from the dock and hero subject when a fixture exists; no reserved empty slot on other days.

**Default full screen rule:** no top navigation bar labelled Home/Movies/TV/Music, no fixed side rail, no multi-row recommendation grid and no multiple stacked translucent containers. Complex structure is revealed only when navigating into it.

### 4.3 Glass as a material, not a decoration

Our material should suggest polished frosted glass with gentle optical depth. The interface is **not** a collection of grey rounded rectangles.

- Use a charcoal-to-slate, wallpaper-aware translucent dock, feathered background blur and a very fine light top edge. Slight colour from the actual image should come through the material.
- The focused item becomes a *single lifted lens* inside the dock: a small increase in scale, marginally stronger translucency/highlight, a clean silhouette and a soft contact shadow. Unfocused items recede rather than dimming into illegibility.
- Do not draw an aggressive white outline around every tile. A subtle bright perimeter is allowed only for accessible focus confirmation, high-contrast mode or a selected tile on an unusually complex image.
- Use restrained reflection and soft specular movement when focus changes; no continuously wobbling blur, strong chromatic aberration, oversaturated blue neon or oversized glowing borders.
- For performance, prefer **cached or pre-rendered blurred materials** and opacity/translation/scale transforms. Never require real-time per-pixel refraction to maintain the chosen appearance.
- Material responds to background brightness. Text and focused outlines must remain readable against snow, bright beaches, white family photos, deep shadows and film backdrops.
- Surface boundaries should be consistent: small top utility chips (if needed), one dock, and a few temporary overlays. **Do not put glass behind every logo, poster and sentence.**

### 4.4 Focus model: the app or title transforms the whole environment

A visible highlight always controls the backdrop. **An image unrelated to the highlighted item is a defect, not an acceptable placeholder.**

**Sequence when the remote moves onto a new item:**

1. **0-80 ms:** immediately confirm the new focus through lift, brightness and scale. The input must feel acknowledged even on an offline TV.
2. **After approximately 110-160 ms of stable focus:** commit that item's contextual art and metadata. This short settle interval prevents a blizzard of backgrounds when someone holds an arrow key.
3. **~320-480 ms:** cross-dissolve from previous hero into the next, with extremely restrained scale/position movement (for example, 1-2% visual drift) and a soft local vignette. Do not slide entire screens horizontally merely to change one tile.
4. **After the backdrop is established:** fade in a title or one useful metadata line where applicable. Text must not jump around or force the dock to move.
5. **On rapid additional input:** cancel superseded metadata requests and skip or shorten intermediate background animations. The final focused item always wins. Never queue transitions behind remote presses.

An active tile has a single, obvious focus state. Merely changing focus **must not launch an app, play a trailer, connect NordVPN, power on Xbox or start music**. OK is the separate, deliberate activation step. On Back, restore the previous screen and focused object. If background fetching fails, keep navigation working and show the item's own brand-derived fallback, never another item's hero art.

**Physical continuity, inspired by high-quality TV focus systems:** a focused logo/poster appears marginally closer to the viewer while the dock stays planted on the background plane. Use a tiny change in highlight direction and shadow, not theatrical 3D rotation. When **OK** enters our own film details or NAS library, preserve the selected poster's identity and interpolate its position, size and corner treatment into the destination; on **Back**, reverse that spatial relationship to the exact originating poster. For a *third-party application* launch, only animate our launcher up to the OS activity hand-off; never claim to control Netflix, BBC or Disney+'s internal page animation. Navigation easing should favour quick response followed by a calm settle, with consistent timing across all elements in the same action.

**Proposed motion tokens** (initial engineering values, adjusted only after physical device testing):

| Event | Baseline duration | Behaviour |
|---|---:|---|
| Focus feedback begins | within 80 ms | Remote-first visual acknowledgement; existing P0 p95 response target remains <100 ms |
| Tile elevation and return | 150-210 ms | Ease-out, roughly 1.06-1.09 scale; no large spring bounce |
| Metadata reveal | 130-180 ms | Short fade after a stable item is established |
| Contextual background dissolve | 320-480 ms | Image-to-image dissolve with tiny positional drift |
| Expand dock into NAS shelf | 260-380 ms | One connected spatial transition, focus retained |
| Enter/leave full NAS library | 280-400 ms | Poster/selection continuity; no full-screen white or black flash |
| Enter/leave artwork mode | 650-1,000 ms | Dock fades away and image becomes unobstructed |
| Ring appearance | ASAP on verified event | Interrupt immediately; never wait for decorative animation |
| Alexa reminder toast | 180-240 ms in/out | Does not steal focus or cover subtitles where avoidable |
| Dismiss overlay / return | 200-350 ms | Restore the exact previously focused object and playback state |

Animations are optional to understanding. Provide reduced-motion and reduced-transparency settings. Never gate input behind a motion sequence. Audio focus ticks are **off by default**; selection confirmation may use a barely audible tick if a household preference enables it.

### 4.5 Contextual hero backgrounds: app-by-app contract

Use legally sourced and maintainable visuals. The preferred source is approved official art or app-provided metadata where authorised; absent such a source, use an **original, clearly app-related branded atmosphere**. Do not scrape other apps' current shows or imitate their internal UI. Each app has a self-contained hero-asset configuration with a safe local fallback and cache policy.

| Focused item | Full-screen background behaviour | What appears above the dock |
|---|---|---|
| **SmartTube** | Elegant, minimal video-centric composition based on genuinely available/authorised current content; otherwise a muted, service-relevant original scene. Never label it YouTube or show random film artwork. | `SmartTube`, optionally one concise line of current context if reliable |
| **Netflix** | Cinematic artwork for Netflix only when rights/source permit, otherwise a refined charcoal scene with a restrained red accent, not a random landscape. | `Netflix`; no invented recommendations |
| **Disney+** | Deep midnight-blue or star-lit original atmosphere with very subtle illumination; licensed imagery if available. | `Disney+` |
| **BBC** | A restrained BBC/iPlayer-related editorial atmosphere and approved identity. No unrelated Hollywood film art. | `BBC` with secondary `iPlayer` only when it helps explain the destination |
| **Live TV** | Original live broadcast/sports mood, abstract broadcast light or a relevant authorised TV still. Never promise a specific channel, fixture or broadcaster. | `Live TV`; show VPN blocking state only **after OK**, not a warning dominating idle focus |
| **Music** | Music-specific colour field drawn from permitted album artwork when track data exists; otherwise a clean, quiet music-themed composition. | `Music` and **Now Playing** only when reliable playback state exists |
| **Xbox, Sony only** | Original dark green gaming atmosphere with subtle depth; console status only if Home Assistant is connected. | `Xbox`; OK follows the Sony HDMI 3 wake/switch journey |
| **Samsung TV Plus, Frame only** | Native broadcast-appropriate imagery or tasteful service-colour background, with approved app identity. | `Samsung TV Plus`; OK starts native Tizen handoff through tested bridge |

**No misleading image rule:** if the service has no authorised dynamic imagery, stay with a high-quality *brand-aware fallback*. Do not fill every service with the same mountain wallpaper, randomly selected stock scenery, unlicensed film posters or guessed titles. Preserve the restrained character of the dock while the dominant image changes.

### 4.6 NAS focus is a cinematic reveal, not a separate-looking application

Press **Down** from the dock to reveal **Your Films & TV**. The dock gently retreats upwards or reduces prominence while a single row of posters rises into view as part of the same scene. Focus moves predictably to the best corresponding poster or the last visited position. Press **Up** to return to the dock without resetting the selected app. The full NAS catalogue appears only after choosing **View All**.

**When focusing a film poster:**

1. The selected poster lifts and scales modestly above neighbouring covers.
2. The *same film's* wide landscape backdrop becomes the full screen hero. Do not show an app-branded background while a NAS title has focus.
3. Reveal the title, year and runtime if known; one short synopsis line is enough on Home. Remove unsupported or mismatched metadata.
4. The rest of the posters recede in contrast, without vanishing or shifting unexpectedly.
5. **OK** opens the agreed title detail/play path and starts embedded native playback; users never browse Synology directories or VLC file lists.

**When focusing a series:** use the series' correct landscape backdrop, series title and a truthful next-episode/season label only when the underlying data proves it. Never label something `Continue Watching` without an actual tested watch position. A season or episode selection is explicit within the dedicated catalogue, not an unpredictable focus action.

**Artwork source order:** verified title-specific landscape backdrop > title-matched key art with a readable crop > blurred/tinted expansion of that **same title's** poster > original neutral backdrop **with the selected title/poster still clearly identified**. Never show a different film's imagery, a broken image icon or a raw NAS filename. Cache identities by item ID, not a guessed title alone; fail gracefully if the catalogue is offline.

**View All gallery:** a dedicated poster-led view with Films, Series, recently added and search; no Plex-like wall of controls. Keep the selected film's large background present during entry. Use a small translucent navigation strip only where the depth of the catalogue requires it. Back walks through episode > season > series > catalogue > Home in that order, preserving focus.

### 4.7 Remote journeys and explicit visual states

| State | Visible UI | Background and transition | Key remote action |
|---|---|---|---|
| **Neutral Home / arrival** | Time + tiny utilities; single app dock. No poster wall. | Chosen personal artwork while *no* app is visibly highlighted; as soon as focus is shown, that item's correct context takes over. | D-pad focuses previous app; Down reveals library |
| **App focused** | One raised app, other apps quiet, no fixed side panel. | Full-screen app-related hero, changed by stable focus. | Left/right changes focus; OK launches; Down opens NAS shelf |
| **NAS shelf focused** | Poster row plus discreet dock; View All at the end. | Exactly matched film/series art with restrained metadata. | Left/right poster; OK details/play; Up dock |
| **Full library** | Poster grid plus minimal category/search navigation. | Current category/selected-title art, never an unrelated app hero. | Arrows, OK, Back; preserve position |
| **App/film launching** | Last selected element gently recedes; no technical progress view unless needed. | Selected image remains until playback owns the screen. | Back cancels only where safe |
| **Live TV VPN blocked** | One compact, legible action sheet; no app launched. | Live TV hero remains behind a darkened scrim. | Connect/Retry/Back; prevent traffic leaking |
| **Music Now Playing** | Large album art, track and play/pause/skip; no busy playlists until requested. | Soft tones sampled from correct album art. | Playback controls; Back returns Home |
| **ChatGPT listening** | Small glass listening panel and clear microphone state. | Keep underlying context legible; no takeover or persistent chat page. | Back closes microphone |
| **Ring interruption** | Actual live feed, status, clear dismiss; highest priority. | Temporarily takes priority over wallpaper/hero/playing app. | No input required; return to prior state on dismissal |
| **Alexa reminder** | One small corner toast with text where available. | Current programme continues underneath. | Auto dismiss; never take focus |
| **Ambient / native Frame Art Mode** | **No permanent controls, clock, dock, status or logos.** | Selected family/scenic/art image only. | Remote reveals Home; Ring automatically interrupts |
| **Settings and TV cleanup** | Calm, text-forward administrative view, separate from entertainment. | Non-distracting dark material; no full-screen hero competition. | Back returns to exact Home focus |

**Focus persistence:** record selected app, last NAS poster/row, gallery page and the prior TV mode. Returning from Ring, music, voice, failed VPN, Settings or another app must not randomly jump to the first app tile. Where Android/Tizen cannot restore the precise activity or input, document a specific tested fallback.

### 4.8 Time-of-day personality without arbitrary colour overlays

The *active launcher*, not the family's original photographs, changes mood naturally. The selected app or NAS title remains the primary colour source, with a subtle daylight-dependent treatment of glass, highlights, shadow depth and text balance:

| Phase | Intended appearance | Principle |
|---|---|---|
| Morning | Crisp and gently luminous, restrained neutral-white edge light. | Calm, not artificially saturated |
| Day | The clearest glass and brightest legitimate artwork presentation. | Keep important text legible over bright photos |
| Dusk | Gradual, slight warming of focus highlights and reduced panel brightness. | No obvious palette switch at a clock boundary |
| Night | Deeper charcoal materials, softer highlights, lower intensity from utility chips. | Dark and comfortable, without crushing detail |

Use Europe/London time, current sunrise/sunset if an authorised local provider is available, and user-selectable fixed times otherwise. Crossfade phase adjustments gradually (roughly 10-20 minutes), never by flashing the entire display. Preserve the artwork's actual colours and metadata identity. Theme changes must not trigger navigation, move focus, change apps or start media.

### 4.9 Gallery/Art Mode is a separate, truly quiet state

After the selected inactivity timeout *while the launcher is foreground*, withdraw metadata first, then fade the dock and header out, leaving the chosen photograph or artwork edge to edge. No bouncing logos, screensaver clock by default, stock animations or content promotion. Personal family photographs must keep their identity, natural tone and sensible crop; do not apply heavy branded gradients or invent face detail. Crossfades between the chosen 10+ images should feel like a gallery, without gimmicky pan/zoom on every image.

On **Samsung native Art Mode**, there is **never a clock, VPN flag, ChatGPT icon, app dock, Alexa toast by default or permanent status**. This remains true regardless of time of day. A Ring doorbell press **or person detection** must automatically interrupt Art Mode into the real camera stream, then return to native Art Mode and artwork. This is an independently tested *hardware orchestration requirement*, not a promise that Tizen supports arbitrary third-party overlays.

On Sony OLED, use restrained brightness, changing artwork and OLED-safe inactivity behaviour; never leave a glowing static dock or clock for hours. The quieter gallery state does not run while another video app is actively playing.

### 4.10 Alerts that belong to the experience rather than hijack it

**Smoke and carbon monoxide: above Ring (owner decision, 24 October 2026).** When a Home Assistant sensor whose own device class is smoke or carbon monoxide turns on, its screen takes over above everything else, including a live doorbell card. It shows the alarm type, the household's own sensor names and one plain instruction, and asks Android to pause other sound so the alarm can be heard. A doorbell press during an alarm becomes a single line on that screen. Reminders are dropped. A household can switch it off; it is on by default once Home Assistant alerts are running. Only an "off" state clears it, because a sensor that goes unavailable may have failed in the fire. It adds to the home's alarms and never replaces them, and it can only reach a TV that is on with alerts running. Not yet tested on a TV.

**Ring: immediate priority 1 among everyday alerts, below a smoke or carbon monoxide alarm.** On Home or library browsing, a sizeable live camera card appears without moving underlying focus. On video playback, prefer a safe camera window in an unobtrusive area, with a platform-tested full-screen fallback where protected video or the OS blocks a floating overlay. In Sony/TCL wallpaper or Samsung native Art Mode, interrupt the artwork and show live video automatically. A truthful `Connecting` state is allowed before frames arrive; never label a still as Live. When dismissed, restore the app, soundtrack volume, selection and artwork precisely. Ring visuals should be clear and calm, not a loud flashing notification.

**Alexa reminder: priority 2, after Ring.** A short, single glass toast at a safe corner, showing the real Alexa reminder text and an optional small Alexa identity cue. No full-screen sheet, forced playback pause or cursor movement. Position adaptively to avoid covering subtitles or playback controls where possible. Group duplicates; let users disable individual reminder categories/TVs. Samsung native viewing has OS limitations; do not silently claim cross-app Tizen overlays work.

**VPN: priority 3 for Live TV activation.** The passive Home badge is only a tiny shield/flag. If the user *activates* Live TV while the verified VPN is off, show a simple Connect/Retry/Cancel sheet against the current Live TV background. Never launch the unprotected source, and do not mask failed verification with a successful green badge.

**Voice: priority 4 and opt-in.** The compact ChatGPT glyph expands to a small listening waveform/panel on deliberate activation; colour and movement are understated. Display a brief transcript or answer when useful, close on Back, and automatically remove the panel when finished. No permanent assistant portrait, conversation feed or advert-like suggestion chips on Home.

### 4.11 Typography, icons, affordances and readable distance

Use an original, redistribution-cleared modern sans family with strong legibility at normal sofa distances; do not bundle Apple's proprietary fonts. The design should feel typographically calm: one dominant heading when needed, one secondary line, consistent spacing and generous line height. Avoid long descriptions, all-caps labels, excessive bolding and dense specification tables in entertainment views.

**Starting values on 1920 × 1080 canvas** (subject to sofa-distance testing): 24-hour clock approximately 40-48 logical px; app label 26-30 px; hero title 44-64 px where shown; short metadata 23-28 px; toast body at least 25 px. Keep all essential text on safe backgrounds with strong contrast, target WCAG-like 4.5:1 for ordinary text when practical and never rely on colour alone for focused/disabled/protected states. Respect screen-reader labels, text scaling, increased contrast, reduced transparency and reduced motion.

Each service icon uses a recognisable and legally redistributable brand mark if permission and licensing permit; otherwise a clear text lockup. App tile styles are coordinated but brands retain their identity. Labels are `SmartTube`, `Netflix`, `Disney+`, `BBC`, `Live TV`, `Music`; `Xbox` only on Sony and `Samsung TV Plus` only on Frame. **Do not put VLC, Jellyfin, Synology, Home Assistant or NordVPN setup controls in the app dock.**

### 4.12 Technical rendering contract for the slower TVs

- Use one shared design system with **high**, **balanced** and **lightweight** *rendering profiles*, automatically selected after device profiling or manually overridden. They **must not change information hierarchy, service names, navigation or core visuals**.
- Keep remote focus changes synchronous on the UI thread and artwork loading asynchronous. Cache properly sized hero images; prefetch current and adjacent items and cancel requests on fast input.
- Prefer static/downsampled backdrop layers, baked blur, cached gradients, GPU-friendly transform/opacity animation and bounded memory. Do not rely on real-time blur over the whole 4K frame, huge animated video walls or background shaders at 60 fps.
- Use only one full-screen backdrop crossfade at a time. During fast scrolling, skip superseded transitions and show the final correct art after focus settles.
- Keep a placeholder ready *per focused item*. Image failures must not revert to the *previous item's* artwork after focus changes; clear or replace the image identity atomically.
- Target a visually smooth 60 fps where the hardware sustains it; retain honest measured input response and legibility when animation detail is reduced on TCL or ageing Sony. Do not hide lag behind long motion.
- Warm returning Home must be fast, even while Jellyfin, Home Assistant, NordVPN or online artwork sources are unavailable. The dock and installed apps appear from local cache; technical integrations catch up quietly.
- Log telemetry for animation/input performance locally and anonymously by default; never include family photos, media paths, doorbell images or personal tokens in public bug reports.

### 4.13 Mandatory design hand-off artefacts

The project is not ready for launcher development from only a single static hero image. Produce original **1920 × 1080 layouts and short motion studies** for all of these states, on the **same component and spacing system**:

1. Neutral Home with personal landscape, clock and six/seven-tile glass dock.
2. Netflix app focused with a properly Netflix-relevant backdrop (licensed material or original brand-aware fallback); repeat for BBC to prove the system generalises.
3. Live TV focused, then VPN disconnected *after OK*, displaying its clean blocked action sheet.
4. NAS film focused, using that exact film's poster + backdrop; include missing-backdrop fallback.
5. NAS series focused, with legitimate next-episode information; then **View All** and series/season/episode path.
6. Frame-specific dock with Samsung TV Plus; Sony-specific dock with Xbox; no platform leakage.
7. Tottenham matchday compact card and the normal day without it.
8. Samsung full-screen native Art Mode with **absolutely no chrome**, then automatic Ring takeover and return.
9. Netflix or Live TV playing with a short Alexa reminder toast; same scene with Ring taking precedence.
10. Amazon Music-inspired Now Playing, with actual album-derived backdrop where authorised.
11. ChatGPT icon at rest, active listening panel, short spoken answer and clean dismissal.
12. Day-to-dusk-to-night treatment of the *same* contextual hero, showing the mood shift without changing the subject.
13. Appearance/Accessibility settings showing Reduce Motion, High Contrast, theme preference and render quality.
14. Two performance recordings: rapid focus changes on TCL and Sony, plus matching screenshots to demonstrate visual parity.
15. Apps library and editable dock: add an installed app, open the hold-OK Move menu, reorder and save/cancel, remove and restore a Home shortcut, show a long horizontal dock and distinct Sony/Frame capabilities.

Provide each with a focus map (Up/Down/Left/Right/OK/Back), source of artwork, enter/exit transition, timing measurement, empty/error state, permission or integration dependency, and supported/unsupported TV matrix. Use **copyright-cleared original or properly licensed placeholder imagery** for published mockups; do not copy Apple's system screens or paid design concepts. Reference demonstrations from third parties are inspiration, not assets for the public repository.

### 4.14 Definition of premium: design review pass/fail rules

This is a **P0 release gate** alongside the hardware Ring and VPN gates:

- **Pass:** the selected Netflix tile controls a correctly Netflix-related backdrop. **Fail:** it shows a mountain or an unrelated film simply because the photograph looks nice.
- **Pass:** the highlighted NAS film uses its matching artwork and metadata. **Fail:** it shows artwork from a different title or raw filenames.
- **Pass:** resting Home is predominantly content image plus one glass dock. **Fail:** three busy poster rows, fixed side menus and stacked glass cards dominate the first screen.
- **Pass:** moving from app dock to NAS feels like one continuous scene. **Fail:** an unrelated page crashes in, resets focus or briefly flashes white.
- **Pass:** focus is acknowledged immediately despite background image loading. **Fail:** the remote feels sticky while images load or glass animates.
- **Pass:** 100 quick remote presses finish on the correct tile with the **correct final background**, without rendering stale art. **Fail:** the image lags behind, freezes or belongs to the previous focus.
- **Pass:** glass provides separation without hiding the artwork; labels read comfortably from sofa distance. **Fail:** panels and glows overwhelm films and family photos.
- **Pass:** Sony, TCL and native Frame application look recognisably identical in high/low rendering modes. **Fail:** the TCL looks like a visibly different, inferior launcher.
- **Pass:** Samsung native Art Mode contains only artwork until a Ring trigger. **Fail:** any clock, VPN badge or decorative controls remain onscreen.
- **Pass:** Ring pre-empts transitions and restores the prior scene. **Fail:** a decorative effect delays or conceals the doorbell video.
- **Pass:** voice, toasts, Spurs and VPN restrictions appear only when context requires them. **Fail:** they become permanent dashboard widgets.
- **Pass:** no image, source code, trademark usage, font file or proprietary animation is taken from another product without the necessary rights.

## 5. Personal films and TV from Synology (P0)

The NAS remains the media source. A Jellyfin-compatible service provides authenticated metadata, posters, backdrops, search, genre filters, seasons and episodes. Direct SMB or UPnP discovery alone does not provide this catalogue. Confirm server availability and capabilities without requiring the viewer to manage folders.

- Down reveals a curated Your Films & TV row; View All opens Films, Series, Search, Genres and Recently Added. Continue Watching appears only after progress round-trip has passed.
- Android uses embedded Media3/ExoPlayer. Tizen uses embedded AVPlay. VLC is excluded; no external player handoff or VLC fallback is part of this version.
- Prefer direct play. Validate containers, codecs, profiles, bitrate, HDR, audio passthrough, subtitles, network throughput and server capabilities independently. MKV is a container, not a guarantee of playback. Permit an explicitly supported server remux/transcode path when needed; explain unsupported media and never promise instant playback.
- Measure request-to-first-frame separately for cold and warm playback, buffering, seek and resume on representative 1080p and 4K content. Playback controls must support play/pause, seek, audio/subtitle selection and Back without accidental exit.
- Report authenticated session start, progress, pause and stop with correct user, item, media source, position and session identity. Persist through server state and reload after restart; local guesses cannot populate Continue Watching. Handle network loss and duplicate stop events safely.
- Use separate Adult and restricted Child server accounts. Validate identity and permissions; enforce access at the server for browse, search, images and playback. An adult server API key is not a Child account.
- Android requests use scoped credentials in headers and reject credential-bearing redirects. AVPlay does not establish support for arbitrary media authentication headers. Validate a protected LAN media broker or supported scoped cookie path before enabling private Tizen playback. Never put long-lived server tokens in URLs.
- Bound artwork/cache memory and cancel obsolete requests. On server loss, Home and installed app shortcuts continue to work; show honest unavailable feedback and cached artwork only where a cache actually exists.

**GATE-NAS-01:** play film, subtitled film and episode through each embedded player on its target hardware. **GATE-NAS-02:** verify matching artwork, local access and server authorisation with VPN policy active. **GATE-NAS-03:** prove watch-state round-trip including profile switch and restart. **GATE-PLAYER-01:** publish the measured per-device codec/audio/subtitle matrix. **GATE-PLAYER-02:** block unsupported playback without falling back to VLC.

## 6. Live TV and NordVPN protection (P0, security-critical)

- Visible label is **Live TV**; underlying application is **ReamSmartz**. Every launch origin shares one guarded action: tile, Tottenham card, voice, search and resumed sessions.
- Top-right minimalist badge shows VPN **connected/disconnected/connecting/unknown**, with a flag for the **verified exit country**. Do not display green from NordVPN being merely installed or from a remembered country selection.
- If the tunnel is unverified: do not launch Live TV; offer a clear Connect / Retry action. Do not claim NordVPN can be connected silently until a supported integration is demonstrated.
- **Fail closed during playback:** no unprotected ReamSmartz traffic when VPN drops, including after standby or reconnect. The initial launch check alone is insufficient. Investigate platform-supported always-on VPN + block-without-VPN, app routing and/or a segregated VPN network; test network paths and any split tunnelling carefully. It is acceptable to show a blocked state rather than permit traffic.
- Netflix, Disney+, BBC, native Samsung TV Plus, NAS and other permitted services should retain normal availability where technically possible; account for system-wide VPN lockdown trade-offs.
- NordVPN local-network discovery must preserve access to Home Assistant and the media server. A country flag is only shown where geolocation verification is trustworthy.

**GATE-VPN-01:** with VPN off, Live TV traffic fails; **GATE-VPN-02:** drop VPN mid-stream, confirm packets do not escape; **GATE-VPN-03:** Sony, TCL and native Frame application pass independently.

### 6.1 VPN split tunnelling and protection gate

Android VPN transport detection **does not prove that ReamSmartz's packets use the desired tunnel or stop when it fails**. NordVPN Android TV split tunnelling is an optional route configuration to test. Confirm what the installed NordVPN version actually supports; excluding other apps does not by itself protect one app from leaks. Android always-on VPN and Block connections without VPN have different consequences for excluded apps.

Do not promise a silent NordVPN Connect Intent without documented support. Test the actual ReamSmartz package, forced midstream VPN drop, DNS, IPv4/IPv6, wake/restart and access to the NAS/Home Assistant. An on-screen failure scrim is not a firewall. If no real fail-closed mechanism passes tests, keep Live TV blocked in the preview.

**GATE-VPN-04:** confirm no unprotected ReamSmartz packets after tunnel loss. **GATE-VPN-05:** independently prove normal LAN access and unaffected apps under the chosen routing method.

## 7. Tottenham matchday widget (P0)

- Follow Tottenham Hotspur men's first-team fixtures across domestic and European competitions; matchday means the local date in `Europe/London` (BST/GMT automatically).
- On matchday show Tottenham and opposition crests, competition and **UK kick-off time**. Prior to kick-off display the time; during the match use only `Live`, **never a score or result** (avoid spoilers).
- When match is over, hide after a limited grace period, no later than end of UK matchday; when no fixture is scheduled, no card or reserved blank space.
- Press OK -> protected Live TV launch through the **same VPN gate**. Do not imply ReamSmartz is the official broadcaster.
- Fixture provider must cover date/time changes, postponements and multiple competitions; refresh reliably, cache last verified fixture and avoid a false card if data becomes stale.

### 7.1 TCL AirPin LITE receiver integration (P1, optional)

The TCL settings photographs confirm **AirPin LITE** is installed. Earlier external notes called it **AirPro LITE**, which is the wrong product name and must not be repeated. AirPin is a third-party AirPlay/DLNA receiver; its developer documents background receiving after initial launch, but we have **not yet verified automatic startup after reboot on this Android TV 11 TCL**.

Do not make AirPin a permanent app dock tile. Under **Settings > Connections > AirPlay**, detect the actual installed package, offer a guided one-time receiver setup, and show a small AirPlay glyph in the header only when a supported operational state is confirmed. A configured app is not proof of active receiving. Respect Android 11 background activity restrictions and the receiver's own licensing; never bundle its paid code or change its settings without consent.

**GATE-AIRPLAY-01:** verify startup, discovery from an iPhone, casting and graceful return after restart/sleep, plus CPU/RAM use. If automatic startup is unavailable, report a manual setup fallback rather than a false success icon.

## 8. Ring camera override via Home Assistant Green (P0, absolute release gate)

**Reference deployment:** Home Assistant Green on the same local network as the televisions; configure the address and credentials privately, with Ring configured. Do not assume remote development tools have access to that private URL. Ring cloud access remains necessary even when HA and TV are on the same LAN.

**Trigger A:** actual Ring doorbell button pressed. **Trigger B:** **person detected**, not generic motion. Person detection requires proof of a suitable Ring Smart Alert event or supported ring-mqtt attribute; Ring subscription/device compatibility to be verified. Deduplicate repeated notifications.

**Presentation priority:**

1. **In wallpaper / ambient mode on Sony/TCL:** without user action, interrupt wallpaper and open the **live Ring feed** prominently, then restore exact previous artwork and ambience.
2. **In Samsung's native Art Mode:** without user action, command the TV into a real live-view state through a model-verified native app launch path, then restore **native Art Mode** and prior artwork. The Frame shows **no clock or other permanent UI** in Art Mode.
3. **Whilst browsing Home or a library:** immediate camera overlay over current screen.
4. **Whilst playing another app/music:** try an in-app-independent Android overlay (picture-in-picture); if OS/DRM blocks it, use a documented and tested full-screen interruption fallback. Restore focus and volume afterwards.
5. Ring alert has precedence over Alexa reminder toasts, ambient timeout, music, ChatGPT responses and standard notifications. Queue an Alexa reminder briefly if Ring interrupts, or discard it after its short relevance window. TV power **off** is separate from ambient: do not wake a fully off TV unless explicitly configured.

On event show a locally cached recent still with `Connecting` only if the still is available and correctly labelled; switch to **Live** once actual moving frames arrive. On stream failure show a clear `Camera unavailable` state; never pass a stale still off as live video. Dismiss after configurable period / Back; preserve previous playback. Volume ducking must restore original level.

Possible Android building blocks to test before writing new code: `vessosa/ha-video-toast-tv` and `nikitacoolagin/hass-camoverlay`. Candidate Frame control: `bonzanni/ha-samsung-tv-frame` or suitable Samsung Frame Art Mode integration. These are third-party dependencies needing security and firmware testing.

**GATE-RING-01:** all three TVs, in active wallpaper/Art Mode, automatically show actual Ring video from **both** triggers without remote input. **GATE-RING-02:** dismiss restores correct image and TV mode. **GATE-RING-03:** ring during Netflix/Live TV/music without lost playback. **GATE-RING-04:** correct action after standby, HA restart, Ring delay, TV network loss and duplicate events. A failure of Frame Art Mode switching blocks the intended three-TV release; do not quietly remove this requirement.

## 9. Ambient wallpaper and time-of-day mood (P0)

- User curates at least **10** images, combining family holidays, scenic photographs and artwork. Support 10-100 local images where feasible; no assumption that photos must be cloud hosted.
- Home screen transitions to a gallery presentation after configurable inactivity **on the launcher**, never during active video/music unless the relevant app has its own appropriate idle behaviour.
- In gallery mode: full-bleed photographs/artwork, gentle dissolve, discreet or no animations, no permanent controls. Group/weight Family, Travel and Art; rotate without immediate repeats. Simple selection and removal from Settings.
- On **Frame native Art Mode**: **no clock, widgets or permanent UI at all**; photo/art image only. Ring automatic override is the sole compulsory exception; Alexa reminder toasts are **off by default in Art Mode** and would require deliberate opt-in plus proven native platform support.
- On Sony/TCL ambient mode: image only by default; clock optional and off by default to honour gallery appearance. Use appropriate OLED care: dimming, image variation and no permanently static bright badges.
- Time-of-day influences *active interface* lighting: fresher/clearer by day, warmer at dusk, darker/subdued at night. Use local time and solar schedule if available; let users override. Gradual changes, not sudden full-screen flashes.
- Returning from ambient to Home should preserve the last useful focus state and transition smoothly.

## 10. Current time, header and statuses (P0)

- Active Home shows one small, legible **24-hour clock**, default **top-left** to follow the approved calm visual reference (with a user preference for top-right if required); it follows `Europe/London` GMT/BST and never shows seconds. No second clock in the dock or title area.
- Place the truthful NordVPN state and verified country flag, tiny optional ChatGPT control and discreet Settings/More affordance in a **single small top-right utility group**. Each stays the same location while focus and background art change. The hero image, not the status icons, receives attention.
- The current time and utilities vanish in artwork/ambient mode. In **Samsung native Art Mode there is no chrome at all** until Ring automatically interrupts it. Do not force a permanent header over Netflix, BBC, music, embedded playback or Xbox.
- Status chips stay silent during normal browsing. Only a relevant event, such as Live TV activation without a verified VPN, expands into a temporary action sheet.

## 11. BBC, core apps and Sony Xbox (P0)

- Six shared consumer app tiles: **SmartTube, Netflix, Disney+, BBC (iPlayer), Live TV (ReamSmartz) and Music**. A missing app should show install/help only in Settings, not a dead tile.
- `BBC` should clearly identify iPlayer without pretending to be a different provider. Preserve each app's internal UX when launched; this project replaces Home, not Netflix or Disney+.
- Sony only: beautifully branded **Xbox** tile. OK -> send Home Assistant Xbox wake, wait for ready, switch **Sony HDMI 3 (eARC)**, then hand off to Xbox. Clear timeout/error feedback; don't show on TCL or Frame. Validate wake state and HDMI input name against real entities. Xbox power mode may require Sleep, with standby power trade-off.
- Samsung Frame only: branded **Samsung TV Plus** tile. Keep Samsung TV Plus installed and functional as a native Tizen service. Selecting the tile should open Samsung TV Plus, preferably using a verified Samsung/Home Assistant native-app launch command; from the native Tizen application use a discovered and verified application identifier or a tested household controller action. If native app deep linking cannot be reliably automated on the specific Frame firmware, expose a clearly defined tested fallback. Record the previous input/launcher state so Back/Home and Ring recovery are as predictable as Samsung permits. Never show this tile on Sony or TCL.
- **TV Plus and ReamSmartz are distinct services.** The NordVPN fail-closed rule applies to ReamSmartz/`Live TV`, including Spurs and voice pathways, but not automatically to Samsung TV Plus. A separate Samsung TV Plus connection should be tested for normal playback and audio routing.
- Be aware that HDMI-CEC, ARC/eARC and input routing vary. Never change sound output without validating the real HDMI setup.

## 12. Samsung Frame audio: Echo Studio (P0, GATE)

- Whenever leaving native Art Mode for TV/app viewing, the Samsung should output sound to the paired **Echo Studio automatically**. No manual Bluetooth selection on every wake.
- Test Samsung's persistent Bluetooth audio output, power-on/Art Mode transition and competing Bluetooth pairings first. Test Home Assistant-supported Samsung audio routing only if the exact model exposes it.
- Fallback for a compatible **original** Echo Studio with mini optical input: test optical from Frame / One Connect, audio delay and format. The newer Echo Studio may lack this connector; identify exact Echo model before selecting wiring.
- Do not fake a successful audio reconnect. If Bluetooth cannot be made reliable, present the optical / hardware alternative and a temporary manual action.

## 13. Amazon Music experience (P1, agreed design; integration GATE)

- Home tile named **Music**, using the existing **Amazon Music subscription**. Avoid displaying Amazon's technical integration details.
- Intended custom Now Playing screen is Apple Music inspired: large album cover, colour-derived soft background, song, artist, album, progress, prominent Play/Pause, Previous/Next, volume; remote-friendly focus. Discover personal playlists, recent music and favourites only when authorised APIs make that possible.
- **Technical gate:** Amazon Music playback/library Web API is currently closed beta. Do **not** assume a custom catalogue or independent streaming is available. Phase 1 may open the official app; investigate Android MediaSession state, casting and Home Assistant Alexa Media Player/Echo Studio metadata for a richer Now Playing view without claiming guaranteed control.
- Long music listening should use OLED-friendly dimming; Ring overlay still takes precedence. Frame native Art Mode remains visually clean when selected.

## 14. OpenAI voice assistant (P1, agreed design; microphone GATE)

- Tiny optional assistant icon in active Home header, no main app tile and no always-listening microphone by default.
- Tap/OK to start voice session, clear listening state, UK English voice with calm British accent, natural interruption and short spoken responses. Close on Back / timeout; never leave an invisible active microphone.
- Answer general questions and perform safe TV tasks: find NAS content, open BBC, protected Live TV, Sony Xbox, show Ring camera, navigate Home. Use contextual per-TV device capabilities and report failures plainly.
- Use the **currently supported OpenAI GPT-Live/Realtime API** appropriate at implementation time; not the consumer ChatGPT voice session or its subscription entitlement. API credentials live on a protected household service (ideally Synology if resource appropriate), not in TV APKs.
- Never expose private Home Assistant tokens to model output. Give the model only allowlisted commands and read-only content search; confirmations for sensitive home actions. Audio retention/privacy and running API costs must be visible in Settings.
- Test microphone input on actual Sony/TCL remote; the standard remote voice button may be reserved by Google, so an on-screen icon is the dependable starting interaction.

### 14.1 Hybrid voice as a measured option

Use Home Assistant Assist or an allowlisted local phrase recogniser for deterministic TV commands **only when it proves faster on the reference hardware**. Home Assistant Green is modest hardware; do not require on-device Whisper for every instruction without measuring latency. Preserve optional ChatGPT Live/API voice with UK English responses for **general questions**, open discussion and content discovery, not just NAS searches. Maintain explicit microphone consent and a single discreet voice affordance.

## 15. Architecture and separation of concerns

Android Sony and TCL use the native Android application with embedded playback and an optional permission-based alert service. Samsung Frame uses the native Tizen Web Application with embedded AVPlay. Both implement the same interaction contract and local configuration model. Home Assistant supplies household events and an external, model-specific Frame launch and Art Mode coordinator. The media server supplies catalogue, artwork and watch state. A protected local media broker is the proposed Tizen streaming authentication boundary, subject to implementation and tests.


- Main Android launcher, camera overlay, reminder-toast receiver and background integration service must have **separate lifecycle**: Ring and Alexa reminders may not depend on Home being foreground. On Samsung native apps, third-party overlays remain unsupported unless a genuine OS-supported mechanism is demonstrated; the native Frame app may display alerts only while visible; an external household coordinator must handle launch and Art Mode transitions.
- Preference/configuration data centrally synced where useful, with last-known-good local copies. A TV that loses HA/network must still open installed apps and show wallpaper.
- Avoid placing heavyweight metadata indexing and AI requests on the TCL. Cache resized JPEG/WebP artwork; bound caches and cancel obsolete fetches.
- Use properly licensed fonts, icons, posters and logos, with attribution and no redistribution of third-party paid assets. No copying Apple or streaming app interface source.

## 16. Performance, resilience and security (P0)

These are **engineering targets to validate**, not measured performance of your current hardware:

| Metric / control | Release target |
|---|---|
| Focus feedback | Under 100 ms at p95, tested across 100 rapid remote events; visible focus always responds before an image arrives |
| Background correctness | The final item after rapid input has correctly matched art or its own fallback, never stale art from a prior focus |
| Animation smoothness | Aim for 60 fps with no persistent dropped-frame clusters on the weakest TCL or ageing Sony; reduce effects rather than input responsiveness |
| Visual parity | Identical information hierarchy and navigation on all TVs; lightweight rendering reduces cost, not quality of layout or content identity |
| Home usable after warm return | Target under 2 seconds on TCL, Sony and native Frame application |
| Artwork fetch | Never block D-pad focus; placeholder immediately, image replaces asynchronously |
| Background footprint | Memory budget established using actual TCL RAM; no continuously running recommendation polling or full-resolution bitmap cache |
| Idle/OLED | Bright UI fades out; varied imagery; screensaver does not leave fixed high-intensity regions for extended periods |
| Offline | Installed apps and wallpaper remain navigable without NAS, external metadata providers or Home Assistant |
| Recovery | Documented tested restoration of stock Home, packages, remote controls and display/sound settings |
| Secrets | No keys/tokens in public repo, logs or TV artwork; least-privilege service credentials; network debugging off after setup |

**Per-TV assessment sequence (Sony, TCL, Frame):** (1) inventory exact model, firmware, platform, permitted diagnostics and installed applications; (2) establish a comparable pre-change baseline for startup, Home responsiveness, app launch and standby/wake, using RAM figures only when actually accessible; (3) verify the launcher and integrations before changing packages or settings; (4) offer the *optional* tune-up only when the owner chooses it; (5) compare user-visible performance before/after on **each** TV. The Sony's original flagship status is not a reason to skip cleanup testing. Never require cleanup to use the launcher. Full product rules follow below.

### 16.1 Optional TV Performance & Cleanup: multi-platform module (P1, user opt-in)

**Goal:** offer genuine responsiveness improvements on **Sony Google TV, TCL Google TV and Samsung The Frame**, with the same simple, optional admin workflow. Treat Sony and TCL equally; test both before and after. The module is a **portable product feature with OS-specific adapters**, not an Android package script indiscriminately used on every brand. It should be independently usable by people who install the public repo. The entertainment launcher must work unchanged if the user skips it.

**Owner-facing entry point and flow:**

1. Keep it outside the entertainment interface: **Settings > Advanced > TV Performance & Cleanup**, guarded by an adult/admin profile. First install permits **Skip** without repeated prompting. If a TV is controlled through an external Android streamer, show separate named targets: **Samsung Frame (Tizen)** and **Connected Android device**.
2. Start with **Read-only Check TV**. Detect brand/model/firmware/OS, supported access level, installed apps (only where APIs permit), free storage (only if accurately available), and measured Home/app navigation speed. Never infer processor speed or free RAM just from model age or count of installed apps.
3. Explain **what is genuinely possible on this TV**: standard settings-only tidying; removal of user-installed apps through official OS flows; reversible Android package disabling on *verified* profiles; and unsupported actions. Where the TV has no safe automation method, show a guided checklist or **No supported changes found**, not a misleading Cleanup button.
4. Preview each candidate with name, exact identifier where known, user impact, dependencies, platform restrictions, test evidence, risk and expected measured benefit if established. No 'remove everything' preset. No 'one-click boost' claims.
5. Require confirmation for every change batch; capture the original state and a per-TV performance baseline. Export a manifest and instructions to a PC/USB where the OS permits it. Do not present factory reset or firmware reflash as equivalent to a simple undo operation.
6. Carry out **only changes the selected platform supports**, in small batches; pause for error, cancellation, unknown model/firmware or failing regression. No remote silent disabling, root, exploitation, unlocking protected boot paths or mandatory developer mode.
7. Re-test content playback and DRM (Netflix, Disney+, BBC), remote/Home/Back, CEC and HDMI sources, tuner where present, Bluetooth and the Samsung Echo Studio, native Samsung Art Mode, NordVPN policy, Ring interruption, Alexa reminder toasts, voice/microphone, casting, NAS media, accessibility, standby and OTA updates. Show before/after timings, and recommend undo where there is no benefit or a regression.
8. Provide **Undo last change / Restore original state** wherever the underlying operation is genuinely reversible. Document any action that only supports a factory reset and **exclude it from the normal cleanup workflow**. No telemetry or cloud upload by default.

**Android TV adapter: Sony + TCL, plus optional external Android streamer**

- The Android launcher by itself has no general permission to disable other packages. Deliver a separate owner-authorised **desktop/ADB setup helper** or manual instructions. Debugging is deliberately enabled for setup, never left active permanently. A TV that declines ADB still receives the read-only advice it supports.
- Prefer reversible per-user disable/enable operations and a recorded rollback script. Keep firmware-specific, verified allowlists; refuse unknown package IDs or mismatched versions. Disable only genuinely optional promotional apps/recommendation services and unused vendor apps after checking their dependencies.
- Protect stock recovery and a usable Home route, DRM/HDCP, Android System WebView when required, Play Services, app stores, input/tuner, Bluetooth/audio, network/remote, accessibility, updates, voice/microphone, Ring and Home Assistant, NordVPN, casting and all household apps. Never uninstall system partitions/APKs or rely on root.
- Profile **Sony and TCL separately**. Restore the original state if either TV becomes less reliable or gains no material improvement. The performance-light launcher profile is a separate feature from package cleanup.

**Samsung Tizen adapter: native Frame TV**

- Offer the **same optional entry point**, but run a *Tizen-specific capability check*. A standard Android APK or ADB script must never be pointed at Samsung's native OS. The Android streaming device, if fitted, is scanned as a **separate target**, not confused with native Samsung software.
- Initial supported scope: guided Samsung settings to uninstall **user-removable** apps, remove unnecessary Home favourites, review allowed recommendations/autoplay settings, inspect storage and app troubleshooting options, and assess firmware-specific non-destructive maintenance. Present only controls proven present on the exact Frame model; never promise Samsung permits removal of every factory-installed app.
- Samsung's official developer documentation says preinstalled apps are not removable using normal app tooling. Community work (`ardazeytin/samsung-tizen-tv-debloat`) reports an SDB uninstall approach on **one 2018 Tizen 4.0 model**, but warns of launcher damage and restoration requiring reset/reflash. This is **experimental research only**, not a default or one-click cleanup action for the Frame. On a modern Frame, require independent hardware proof and an independently validated recovery plan before even considering a separate *expert-only* tool. Such removals must not be called reversible.
- Preserve native Art Mode, **Samsung TV Plus and its channel-guide/service dependencies**, One Connect/HDMI input switching, Bluetooth/Echo Studio pairing and source state, Samsung update/recovery paths and Home Assistant control. If Tizen exposes no safe package control, complete the safe settings review and state the limit honestly.

**Future TV brands and operating systems:** use `capabilities -> scan -> preview -> operation -> verification -> rollback` adapters for additional platforms (for example LG webOS, Fire OS and Roku) only after actual vendor API/firmware research. **'Any TV' means a common entry point and honest platform-specific support, not identical privileged access or guaranteed removal on every model.** An unknown television supports an informational checklist at most. Firmware changes always trigger a new scan and new owner consent.

**Failure and security boundary:** all restoration instructions must work without the custom launcher. Do not disable the original launch/recovery path before a proven alternative exists. No secrets or device tokens in public logs; avoid untrusted local web panels. A setup error or unplugged PC must not stop normal TV viewing.

### 16.2 Alexa reminder toast overlay (P1, agreement; cross-platform GATE)

**User outcome:** whenever a chosen Alexa reminder becomes due **during active viewing**, display a discreet, elegant, non-blocking text toast above the current programme/game/music, even when our launcher is not foregrounded. For example: a small Alexa glyph and `Reminder: Put the bins out` appearing near the top right for around 6-10 seconds. This should feel like a system notification, not an Alexa screen takeover.

**Source of truth:** evaluate Home Assistant's official **Alexa Devices** integration, which exposes **the next scheduled reminder's timestamp and label** for supported devices. It polls Amazon roughly every five minutes by default; reminder additions and modifications are **not guaranteed to arrive instantly**, and Alexa Household account linkage can affect visibility. Do not claim reliable second-accurate reminder delivery until due-time, cancellation and fresh-reminder tests pass on the user's Amazon account. Alternatively evaluate a permitted, better-timed event source if the official integration proves insufficient. The existing Ring integration alone does not imply Alexa Devices is configured.

**Data flow:** Alexa reminder entity (scheduled time + label) -> Home Assistant due-reminder automation/event gateway -> de-duplicated household notification -> each opted-in TV overlay receiver. Guard against duplicate sensors/devices, restarts, stale reminders, time-zone changes and reminders being deleted or modified; only display a message that was confirmed due. No reminder text may be fabricated. The bridge should not need the launcher open. Store minimal reminder data briefly and do not leak household reminders to external services or logs.

**Presentation:** subtle frosted/dark toast and gentle entrance/exit; reminder title and concise message with truncation for long text. Configurable duration, mute/snooze display, per-TV opt-in and privacy option to show only `Alexa reminder` without the personal label. No playback pause, focus theft, volume change or permanent UI; Back dismisses where technically supported. **Ring live camera override has absolute precedence over reminders** (a smoke or carbon monoxide alarm ranks above both, owner decision 24 October 2026). Delay/queue reminder briefly during Ring viewing, with expiry to prevent a stale popup.

**Device behaviour and platform gate:**

- **Sony and TCL running Android/Google TV:** use a TV-wide overlay service such as the Home Assistant `Notifications for Android TV / Fire TV` integration for text, or our own receiver, once the owner grants notification and **Display over other apps** permissions. Test above Netflix, BBC, SmartTube, VLC and Xbox HDMI as applicable: protected video/DRM or HDMI input modes can block third-party overlays even if normal Android apps work. If an HDMI source is not overlay-capable, record it as unsupported rather than promise every source.
- **Samsung Frame native application:** the application can display its own alerts while visible; it cannot draw over other native Tizen apps (including **Samsung TV Plus**) or native Art Mode. Samsung's developer documentation states third-party overlays over other Tizen applications are unsupported. For native Samsung playback, the desired notification requires an independently demonstrated Samsung-supported notification channel; otherwise it is a documented parity gap. Switching HDMI solely for a minor reminder is **not** an acceptable surprise interruption.
- **Samsung native Art Mode:** no Alexa reminder toasts by default, keeping the artwork pristine. Ring remains the mandatory, automatic Art Mode interruption. In fully powered-off state, Alexa reminders do not wake the screen.

**Acceptance gate:** prove one reminder arriving within a measured interval of its Alexa due time, with exact label, on Sony/TCL and inside the visible native Samsung application; record external-app restrictions. Test creation just before due, edits, cancellation, multiple speakers, standby/wake, notifications during DRM playback, and Ring colliding with a reminder. Report the actual latency and any native Tizen gaps before claiming support across every screen. If reliability is insufficient, keep reminders disabled rather than show stale or misleading alerts.


Do not make an untrusted Home Assistant HTTP or MQTT endpoint publicly accessible. Use a protected LAN and least-privilege credentials. Disable ADB / wireless debugging once installation and verification are complete. Review any fork's build scripts before executing them on a family television.

## 17. Acceptance journeys (core release and optional-module gates)

| ID | From | User action / event | Pass condition |
|---|---|---|---|
| AT-01 | Home | Move focus Netflix -> Disney+ -> BBC | Each tile lifts, background changes smoothly, remote remains responsive |
| AT-02 | NAS row | Highlight film, open details/play | Matching poster/backdrop; film opens in the embedded platform player and sound/subtitles work |
| AT-03 | NAS row | View All -> Series -> season -> episode | Native catalogue flow; correct episode plays in the embedded platform player; no folder interface |
| AT-04 | TV Home | Select Live TV with VPN off | App does not send unprotected data; useful blocked/connecting state |
| AT-05 | ReamSmartz playing | Force VPN disconnect | No unprotected stream continues; user sees an explanation |
| AT-06 | Any TV Home | Spurs matchday | Card on matchday only, correct UK kick off, never scores, opens protected Live TV |
| AT-07 | All TVs ambient/Frame Art Mode | Ring button pressed | Live camera shown automatically; restore exact previous state |
| AT-08 | All TVs ambient/Frame Art Mode | Ring **person detected** | Same automatic live view; ordinary motion does **not** trigger it |
| AT-09 | Netflix/music in progress | Ring event | Visible camera with verified OS behaviour, playback/audio restored |
| AT-10 | Frame art | No notification | Only chosen picture; never clock, VPN, sports, audio controls or tiles |
| AT-11 | Sony Home | Select Xbox | Xbox wakes, Sony HDMI 3 selected; other TVs never show Xbox tile |
| AT-12 | Samsung Frame | Exit Art Mode and launch BBC/Netflix | Echo Studio audio active without manual Bluetooth reconnect |
| AT-13 | Sony and TCL independently | 100 fast D-pad focus changes after standby, before and after cleanup | Meets tested latency targets on both; no memory crash, no stuck artwork |
| AT-14 | Active Home | Check header / wait for evening | Real UK time, verified VPN state, gradual time-responsive lighting |
| AT-15 | Music | Open Music, play/pause/skip | Only controls proven supported by chosen Amazon playback path exposed |
| AT-16 | Voice mode | Ask 'Find a film on my NAS' | Appropriate shortlist; speaking/listening indicator clear; Back dismisses |
| AT-17 | Device reboot | Restart then open Home, Ring event | Launcher, permissions, Home Assistant triggers and restoration survive reboot |
| AT-18 | Fresh Android TV installation | Skip optional cleanup | Full launcher works; no packages changed; prompt does not recur unexpectedly |
| AT-19 | Sony, TCL, Frame | Open TV Performance on each display, and skip it on another | Common owner-facing experience; no packages changed unless user opts in; correct OS/target detected |
| AT-20 | Sony and TCL, optional Android cleanup | Preview proposed package changes independently | Exact model/firmware allowlist, rationale, risks, dependencies and dry-run visible; no package changed before confirmation |
| AT-21 | Supported model after one cleanup batch | Run full streaming, remote, VPN, Ring, HDMI/audio and standby suite | All essential functions still work, with measured comparison against baseline |
| AT-22 | Cleanup already applied | Undo last batch, then Restore all | Original per-user package states restored and TV remains usable without custom launcher |
| AT-23 | Samsung Frame native Tizen | Open optional TV Performance, review native options | Model/firmware-specific guided cleanup offered; Android commands absent; factory packages not promised removable |
| AT-24 | Samsung Frame native Tizen | Attempt unsupported SDB/root-based cleanup or firmware changes | Ordinary module refuses it; no factory package removal; Art Mode, audio, recovery and updates preserved |
| AT-25 | Native Frame | Open performance settings | Only supported native Tizen operations are offered; Android package operations are unavailable |
| AT-26 | Android TV, Alexa reminder due | Create a visible, non-sensitive Alexa reminder and watch Netflix/BBC | Correct text toast overlays video for 6-10 seconds without taking focus or pausing; actual latency recorded |
| AT-27 | Alexa reminder scheduled then edited/cancelled | Change or cancel before due | No old message appears; revised label/time shown only if confirmed by Alexa data source |
| AT-28 | Alexa reminders on several devices | Same Alexa reminder appears in multiple sensors | Each opted-in TV sees no more than one toast; privacy and per-TV settings honoured |
| AT-29 | Alexa reminder and Ring doorbell | Trigger both concurrently | Live Ring feed wins; no reminder obscures it, and no stale reminder appears later |
| AT-30 | Native Frame | Alexa reminder due inside and outside our app | Toast works inside our visible app; no third-party overlay claim or forced viewing interruption for a minor reminder |
| AT-31 | Native Samsung Art Mode | Alexa reminder due with defaults | Artwork remains unobstructed; Ring remains the only mandatory interruption |
| AT-32 | Unknown TV platform | Open optional cleanup module | Safe read-only guidance only, no unsupported Android/Tizen commands or misleading promises |
| AT-33 | Samsung Frame Home | Focus and select Samsung TV Plus tile | Correct branding and themed backdrop; existing native Samsung TV Plus opens with working channels and guide; no TV Plus tile on Sony/TCL |
| AT-34 | Samsung Frame | Launch Samsung TV Plus while ReamSmartz VPN policy is enabled | Samsung TV Plus does not mistakenly inherit ReamSmartz VPN gate; Live TV/ReamSmartz remains protected |
| AT-35 | Samsung Frame, TV Plus playing | Ring doorbell pressed or person detected | Automatically displays live camera as the highest-priority event; verifies return to previous channel/app or documents firmware limitation before release |
| AT-36 | Samsung Frame, optional cleanup | Preview all proposed changes and run regression check | Samsung TV Plus app, channel list and required services cannot be disabled; playback, guide, Art Mode, audio and system recovery still work |
| AT-37 | Neutral Home arrival on each TV | Show Home without moving remote | Full image, one low glass dock, one clock, tiny utilities only; no three-row content wall or unrelated promotional hero |
| AT-38 | App dock | Highlight Netflix, BBC, Music, Live TV in turn | Background is unmistakably related to the highlighted service or its own brand-aware fallback; generic unrelated scenery never remains under an active selection |
| AT-39 | NAS shelf | Highlight three distinct film posters and one series | Every backdrop, title and metadata element matches that exact item ID; no stale prior film art, guessed metadata or generic app imagery |
| AT-40 | App focus | Hold Right rapidly across all tiles and release | Visual focus follows input without delay, and the final settled background belongs to the final focused app; no queued, old artwork appears later |
| AT-41 | App dock | Press Down then Up | Single Synology row emerges naturally; dock recedes without disappearing; Up restores dock and last app focus without a white flash |
| AT-42 | NAS shelf | Select View All then navigate Series > season > episode and Back | One consistent poster-led visual language; each Back action restores previous focus and context |
| AT-43 | Sony, TCL and native Frame application | Compare the same focus states | Shared layout and names are visually recognisable on all three; reduced render profile changes motion cost only |
| AT-44 | Home with bright snowy/photo background | Focus tiles and read app labels from living-room seating | Glass/titles maintain accessible readability; no hard-coded dark-on-dark or white-on-white failure |
| AT-45 | App dock with source image unavailable | Disconnect network or remove an optional hero asset, then focus item | Brand-aware asset or correct neutral fallback appears; no unrelated image, missing-image icon or navigation freeze |
| AT-46 | Evening transition | Keep the same app selected across day/dusk/night boundary | Only material warmth/lighting changes gently; context, artwork identity, position and focus stay stable |
| AT-47 | Sony OLED and TCL | Leave launcher inactive for configured timeout | Dock and clock vanish, artwork fills entire screen without permanent bright static controls; input returns to previous focus |
| AT-48 | Samsung native Art Mode | Observe with no Ring event | Family/artwork only: no clock, HUD, VPN, apps, Alexas or music controls, regardless of time of day |
| AT-49 | Any state including Frame artwork | Ring press or verified person detection | Ring live view interrupts immediately even during transition, then restores exact prior visual/playback state; no animation causes a missed event |
| AT-50 | Netflix playing | Trigger Alexa reminder during playback, then trigger Ring | Reminder remains a small focus-neutral toast; Ring takes priority without stale toast lingering |
| AT-51 | Voice on Home | Activate ChatGPT then press Back | Small temporary listening pane appears without obscuring main content; Back returns to identical Home focus and backdrop |
| AT-52 | Live TV focus with VPN off | Move focus onto tile, then press OK | Focus alone never starts VPN or Live TV; OK opens protected, clearly explained Connect/Retry/Back flow |
| AT-53 | Music screen | Play music then leave controls idle | Genuine album artwork drives the background, compact play/pause/skip is legible; quiet state reduces persistent OLED-bright UI |
| AT-54 | Settings accessibility | Turn on reduced motion, high contrast and reduced transparency | All content and commands remain available, focus unmistakable; movement and glass complexity fall back without a redesign |
| AT-55 | Public project design hand-off | Inspect layouts and short motion studies for all Section 4.13 states | Assets are cleared, input maps and source permissions supplied, source-of-truth design tokens present, and videos/screenshots show the same visual system |
| AT-56 | Saved layout migration | Load arbitrary package and missing approved choices | Only the complete approved set remains; approved relative order preserved |
| AT-57 | Home dock | Hold OK, Move with Left/Right, press OK, restart | Smooth reorder saves and persists without confusing focus |
| AT-58 | Home edit mode | Reorder then Back; repeat with a Ring alert | Back restores old order, interruption does not half-save and Ring wins |
| AT-59 | Home edit menu | Try to remove or add a tile | Only Move and Cancel are available; no arbitrary app picker |
| AT-60 | Sony/TCL/Frame | Use different dock order and Xbox/TV Plus shortcuts | Per-TV capabilities remain correct and Live TV stays VPN protected |
| AT-61 | Six/seven-item dock | Exercise approved room choices, child lock and reduced motion | Readable single dock with persisted deliberate owner edits and platform-appropriate shortcuts |
| AT-62 | TCL Settings > Connections | Enter valid and invalid Home Assistant/Jellyfin settings by remote | Correct authentication/connectivity states, protected local storage and offline fallback |
| AT-63 | TCL Windows pilot | Build separate preview and connect using supported Android 11 debugging or offline sideload | Stock HOME untouched, launch and edit controls work, clean uninstall |
| AT-64 | TCL AirPin LITE | After authorised setup, restart and cast from iOS device | Receiver operational status is real; no misleading glyph; resource use measured |
| AT-65 | TCL/SONY NAS | Play representative 4K/HD file with embedded Media3 and AVPlay on their respective devices | Audio/subtitles work where supported; first frame measured; supported remux/transcode or unavailable outcome documented |
| AT-66 | TCL/SONY Live TV | Force VPN drop during protected ReamSmartz traffic | Target app cannot leak unprotected packets; otherwise remains blocked |
| AT-67 | Family Home | Use six starting shortcuts, add an eighth and rearrange | One uncluttered dock, remote-only management, per-TV persistence |
| AT-68 | Samsung Frame | Return from Ring interruption to native Art Mode and native TV Plus | Existing native services remain accessible; actual model verified |
| AT-69 | Voice | Ask to open BBC and a broad general knowledge question | Local/ChatGPT route operates correctly, UK English voice and visible microphone |
| AT-70 | Public GitHub | Inspect project files and generated test APK | No personal LAN IP, SSID, MAC, real camera pictures or API secrets committed |


### 16.3 Performance claims must be measured

Collect cold/warm start, D-pad latency, focus-to-background timing, dropped frames, RAM and first-frame playback on the real Android TV 11 TCL and Sony. The reported third-party "90% faster" and proposed 500ms 4K start are **not** measured Unified TV results. No bloatware changes form part of the first pilot.

## 18. Delivery stages and decision gates

**Stage 0: Hardware audit and safe proof of concept.** Collect model/firmware IDs for **Sony, TCL and Samsung Frame**, record individual performance baselines **before any changes**, identify safe official settings and rollback limits by OS, validate Home Assistant Ring **and Alexa reminder** entities, VPN networking and embedded playback. No irreversible changes and no mandatory cleanup.

**Stage 0.5: Safe CI/CD and Windows-to-TCL sideloaded test (P0).** Build a test-signed **standalone** Android preview package, separate from the existing HOME application, in GitHub Actions and verify the APK manifest and signature. Confirm Windows can connect to TCL **Android TV 11** through a *supported, user-enabled* debug route. Google's modern Wireless debugging pairing method is officially specified for **Android TV 13+**, so inspect the TCL Developer options for manufacturer **Network debugging** and its stated port before suggesting any ADB commands. If neither supported network nor USB debugging is available, use an APK USB/manual installation route and test UI without ADB. Run read-only model/build checks before any authorised installation. Verify Home, remote focus, app pin/move, safe return, distinct APK package identity and clean uninstall while stock Google TV Home remains unchanged. Do not scan the LAN, change factory packages or start cleanup. Build **Settings > Connections** and test valid/invalid local endpoints as the next Stage 0.5 deliverable. The actual private IP is never in public GitHub. See [TCL testing plan](LOCAL_CONNECTIONS_AND_TCL_TESTING.md).

**Stage 1: Two critical proofs.** Establish Ring automatic live video over Android wallpaper and **Samsung native Art Mode**, both triggers and return path. Prove VPN fail-closed Live TV. These decide the architecture; repeat Samsung interruption tests while **native Samsung TV Plus is playing**.

**Stage 2: Core launcher and design-system proof on TCL, with Sony benchmark in parallel.** First build the quiet one-dock Home plus the full Section 4 focused-item background behaviour, NAS poster reveal, View All, embedded playback, clock, ambient images and time-of-day mood. Approve Section 4.13 visual hand-off and Section 4.14 design gates before completing other modules. Verify Ring and Alexa text overlay receiver above active playback. Performance profiles and revert paths for **both** Android TVs. A separate **opt-in TV Performance & Cleanup** module follows only after safe, model/firmware-specific tests.

**Stage 3: Household rollout.** Sony with Xbox, native Frame application with native art handoff, **Frame-only Samsung TV Plus tile and verified Tizen launch/return path**, Echo Studio audio fix, and per-device settings. Offer the Samsung **supported-settings** tune-up alongside optional, reversible Sony/TCL Android cleanup; test Alexa toasts inside the visible native Frame application and document native Tizen limitations. Only release after per-TV acceptance matrix passes.

**Stage 4: Delight features.** Music Now Playing if supported by Amazon, ChatGPT Live with a British voice, refinements to content suggestions and advanced personalisation.

## 19. Open factual checks, not undecided design preferences

- Exact TCL, Sony and Frame model identifiers, firmware revisions, hardware constraints, recoverability, supported Samsung cleanup settings, **Samsung TV Plus native app launch controls and switch-back behaviour**, and native Frame launch capabilities.
- Ring model, Ring subscription and **real person detection entity/event** through Ring or ring-mqtt.
- Whether the **Alexa Devices** integration is installed and exposes the correct Alexa reminder timestamp/label for this Amazon account and Echo Studio; actual polling delay, Alexa Household impact and tested reminder due behaviour.
- Whether the Frame's current Home Assistant integration supports reliable Art Mode interruption, source change and exact return after playback.
- Which Echo Studio generation is present and which Frame audio-routing commands / optical ports are available.
- NAS model, storage paths, Jellyfin support, media codecs, subtitles and dependable embedded playback.
- NordVPN actual Android TV version, always-on/lockdown support and effects on permitted apps/local LAN.
- Voice microphone route, GPT-Live developer API version, cost and private service host.
- Legitimate streaming-device compatibility for SmartTube, Netflix, Disney+, BBC iPlayer, ReamSmartz and Amazon Music.

## 20. Explicitly outside initial scope

No mandatory removal of bloatware, no promise that every manufacturer permits factory app removal, no untested/unrecoverable Samsung SDB deletion in the normal cleanup module, no root-only optimisations, no silent package disabling or always-on ADB; no Plex interface; no VLC home tile; no generic YouTube app tile; no persistent football scoreboard; no permanent ambient clock on Samsung Frame; no unrelated trending/recommendation feeds; no full-time poster walls on neutral Home; no generic unrelated wallpaper behind a highlighted app or film; no mandatory real-time full-screen refraction; no automatic trailer previews on focus; no automatically switching on a fully powered-off TV for Ring unless separately authorised; no untested claims of access to Amazon's music catalogue; no dependence on always-on ADB; no unsolicited advertising or mandatory sign-in to a discovery service.

## 21. Reference links and research

- https://github.com/matheus-caldeira/cleanyourtv-ui
- https://github.com/Gathin23/cleanyourtv
- https://github.com/simbasjungles/free-my-tv
- https://github.com/nthnclrk/CleanFrame
- https://github.com/Ansub/tv-detox (optional Android TV cleanup candidate; never automatic)
- https://dribbble.com/shots/5773537-TV-Interface-motion (motion inspiration only)
- https://dribbble.com/shots/21647921-Vision-Stream-Next-Gen-Smart-TV-App-UI (cinematic presentation inspiration only)
- Reference screenshot discussed during product discovery (24 October 2026): calm coastal photograph, one translucent app dock, discreet clock and utility icons; use visual principles only
- https://support.apple.com/en-gb/106336 (tvOS 26 introduction of Liquid Glass)
- https://support.apple.com/en-ie/106336 (tvOS 27 software updates)
- https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos
- https://developer.apple.com/design/human-interface-guidelines/focus-and-selection/
- https://developer.apple.com/design/human-interface-guidelines/materials
- https://developer.apple.com/design/human-interface-guidelines/images
- https://developer.apple.com/design/human-interface-guidelines/layout
- https://gitlab.com/flauncher/flauncher
- https://gitlab.com/piermark/omarchytv
- https://github.com/vessosa/ha-video-toast-tv
- https://github.com/nikitacoolagin/hass-camoverlay
- https://github.com/bonzanni/ha-samsung-tv-frame
- https://github.com/ardazeytin/samsung-tizen-tv-debloat (experimental reference; 2018 device only)
- https://www.samsung.com/uk/support/tv-audio-video/how-do-i-manage-apps-on-my-smart-tv/
- https://developer.samsung.com/smarttv/develop/faq/application-installation.html
- https://developer.samsung.com/smarttv/develop/faq/other-features.html
- https://www.home-assistant.io/integrations/alexa_devices/
- https://www.home-assistant.io/integrations/nfandroidtv/
- https://www.home-assistant.io/integrations/ring/
- https://github.com/tsightler/ring-mqtt
- https://www.home-assistant.io/integrations/xbox
- https://www.home-assistant.io/integrations/braviatv
- https://support.nordvpn.com/hc/en-us/articles/19509682644369-NordVPN-Kill-Switch-how-does-it-work
- https://developer.amazon.com/docs/music/API_playback_overview_v2.html
- https://developers.openai.com/api/docs/guides/audio

*This document is a build specification, not a claim that each integration has already been verified on the household's hardware.*

## 22. Change log

- **v1.8 (24 October 2026):** Consolidated the short v1.7 architecture proposal into the complete requirements without removing v1.5 functions; confirmed TCL Android TV 11 and AirPin LITE, added remote-based private Connections UI, secure local settings, separate Windows-to-TCL Stage 0.5 with Android 11-specific debugging constraints, player comparison, honest VPN routing tests, optional AirPin and hybrid voice, nine new tests (AT-62..70). No household network identifiers belong in public GitHub.


- **v1.5 (24 October 2026):** Added P0 Apps library and remote-only Home dock pin/move/remove, persistent per-TV layout, child edit lock, single-row overflow, a required design state and six acceptance tests (AT-56..61). Refer to `docs/APP_CUSTOMISATION.md`.
- **v1.4 (24 October 2026):** Replaced the brief cinematic-focus paragraph with a **full P0 visual and interaction specification** for an original Liquid Glass-era-inspired, CleanFrame-calm interface. Defined one-dock resting Home, progressive reveal of NAS row, exact-app/film hero background contract and fallback source priority, focus timing and state machine, glass/material tokens, UK time and minimalist statuses, four time-of-day modes, remote journeys, accessibility, reduced-resource rendering on TCL and Sony, Ring/Alexa/VPN/voice presentation, 14 required design hand-off screens, 12 design pass/fail rules and 19 new acceptance tests (AT-37..55). Corrected previous generic-background mockup mismatch. tvOS 26 introduced Liquid Glass; tvOS 27 is the continued version reference. No other functional requirements weakened.
- **v1.3 (24 October 2026):** Required Samsung TV Plus as a Frame-only tile, preserved its native Tizen app and related services during optional TV cleanup, excluded TV Plus from the ReamSmartz VPN check, and added native-app/Ring acceptance tests.

- **v1.2 (24 October 2026):** Expanded optional TV Performance & Cleanup from Android-only to a universal, capability-detected **Sony + TCL + Samsung** feature, with safe Tizen settings guidance, a distinct Android streamer target and future platform adapters. Gave ageing Sony equal performance priority. Added Home Assistant/Alexa reminder text toasts over active playback, strict Ring alert priority, privacy controls, official Alexa Devices polling limitations, Samsung native Tizen overlay limitations, and nine extra acceptance journeys.
- **v1.1 (24 October 2026):** Expanded brief TCL debloat guidance into an optional, administrator-only TV Performance & Cleanup module for the public Android fork. Added dry-run previews, explicit package consent, versioned device rules, separate ADB helper, rollback independent of the launcher, platform exclusions and six acceptance journeys. Core launcher remains fully functional without cleanup.
## 23. Additional proposal reconciliation (v1.9)

See [decision record](SPEC_RECONCILIATION.md). Preserve every earlier feature and release gate. Add Adult/Child media profiles with separate restricted server credentials, adult-PIN protected editing and adult return. Never imply launcher profiles switch streaming-app accounts. Server restrictions must govern catalogue and playback, not just hide posters.

Embedded Media3 direct playback and real position reporting are now a preview implementation track. Native Media3 and AVPlay are the selected engines; VLC is excluded. Codec, subtitle, seek and hardware acceptance remain mandatory. Continue Watching requires proven server round-trip. Native Wi-Fi/Bluetooth/audio/OLED/CEC controls remain authoritative.

- AT-71: a Child user cannot browse or play an Adult-only server item by changing screens, search, or player requests; use its own restricted server token.
- AT-72: PIN protects adult return and editing; external app/OS limitations are clearly explained. Test PIN recovery and repeated failures before release.
- AT-73: report real playback position on pause/stop; reload from the server after restart; suppress Continue Watching until proven.
- AT-74: refuse credential-bearing redirects and non-consented HTTP; accept IPv4/IPv6/hostnames; encrypted data survives same-key updates and is removed by Forget.
- AT-75: native TV settings still control sound, picture, Wi-Fi, Bluetooth and CEC; no cleanup or hardware override occurs automatically.

v1.9 (24 October 2026) adds these requirements and reconciles the new shorter proposal without deleting app personalisation, native Samsung features or VPN/Ring release gates. Preview implementation status is tracked separately in [0.2 notes](PREVIEW_0_2.md).


## 24. Native Samsung architecture and platform boundaries

### 24.1 Native application and lifecycle

Deliver an installable Tizen Web Application package built with supported tooling, signed with valid author and distributor certificates for the target device. Tizen Studio is a development and installation tool, not the application hosting service. Keep certificate private keys, passwords and device identifiers outside the public repository and published build logs. Same-author update continuity is a release requirement. An unsigned WGT or browser demo is a development artefact, not a tested TV installation.

The app runs full screen but cannot replace Samsung Smart Hub. Physical Home opens Samsung Home. Back navigates within our app and returns from details/player to the prior screen; follow Samsung exit behaviour at our root, and do not intercept long Return. Back from another company's app cannot be promised to reopen Unified TV. Provide an ordinary installed-app launch route and a model-tested shortcut where available.

Automatic startup remains model-dependent. Evaluate available Autorun Last App behaviour separately for standby wake, cold power-on and reboot, and record whether developer-installed packages participate. If unsupported, show the actual supported launch route; do not claim autostart passes. No developer tooling, ADB or SDB session may be a permanent runtime dependency.

Tizen can suspend JavaScript when the app is hidden. Save navigation, playback state and current profile before hiding; stop/release playback as required, close obsolete requests, and revalidate credentials, events, network and media position when visible again. A WebSocket inside a hidden app is not a reliable Ring delivery service. Register only supported extra remote keys and keep all screens usable with a basic directional pad, OK and Back.

### 24.2 Service availability and shared appearance

Use identical hierarchy, tile sizing, safe areas, artwork identity, typography, focus behaviour, motion intent and accessible alternatives across the two implementations. Shared design tokens and fixtures should drive screenshot comparison. The lightweight profile reduces rendering work without replacing the product's layout. Native system dialogs may differ; recognise these differences rather than claiming pixel-identical OS screens.

Preserve SmartTube, Netflix, Disney+, BBC, Live TV and Music as the curated shared default intent, plus Xbox only on Sony and Samsung TV Plus only on Frame. Resolve installed app IDs at runtime and allow owner-approved mappings. Android-only apps do not become native Tizen apps through a launcher tile. SmartTube and ReamSmartz remain unavailable on Frame unless a supported client or explicitly approved substitute exists. Do not silently substitute YouTube, promise access to Amazon Music's full catalogue or expose a non-working shortcut as playable. App absence produces a clear message, and ordinary removable/pinnable dock behaviour remains available.

### 24.3 Ring event coordinator and recovery

An external household coordinator in Home Assistant must receive authorised doorbell and person events, identify target rooms, reject stale/duplicate events, capture source and Art Mode state, request a model-supported launch, and wait for acknowledgement that the camera's first frame is visible. Android's overlay service remains a separate implementation. Foreground camera connections alone do not prove background interruption.

Use the Ring integration's currently exposed event model; normalise legacy binary sensors and current event entities through an adapter. Doorbell press and person classification are separate acceptance cases; generic motion must not be relabelled person detection. Live view and a last-recorded clip are separate sources. Label Connecting, Live, unavailable and stale honestly; do not label a recorded clip live. Avoid camera URLs and household images in logs.

SmartThings capabilities vary by device. The consumer SmartThings app's Art Mode controls do not prove a universal cloud command for closing Netflix, launching a developer app or restoring artwork. Evaluate Home Assistant's Samsung integration and a model-specific local Samsung or Art Mode adapter; use SmartThings only for commands actually exposed and tested. Do not force-stop DRM applications through undocumented APIs. App launch acknowledgement and camera first-frame acknowledgement are different steps.

On dismiss or timeout, restore only the captured state that the platform permits. Art Mode should regain its prior artwork without chrome. An interrupted third-party stream may require the viewer to resume manually; preserve navigation/source where supported and report the limitation. Repeated Ring events must not build an infinite queue. A fully off TV stays off unless an explicit wake policy is later enabled. If a launch or restoration command fails, show a truthful status in the household controller and retain a safe remote recovery path. Ring has priority over reminders, voice and transitions; reminders never force the Frame out of Netflix or Art Mode.

### 24.4 Embedded playback and private authentication

AVPlay owns the supported playback surface. Keep its lifecycle explicit: open, prepare, first frame, playing, paused, buffering, error, stopped and closed. Release the previous player before opening another item. Test aspect ratio, subtitles, audio selection, seek precision, resume, end-of-item and repeated Back on the Frame. The Android Media3 implementation must meet the same viewer outcomes.

The proposed private LAN media broker authenticates the TV using a scoped revocable session, resolves only authorised server items and attaches the correct server credentials upstream. Restrict origins and paths, enforce profile permissions, validate range requests, rewrite or reject unsafe HLS segment/redirect destinations, and expire sessions. AVPlay authentication may use a documented scoped cookie if validated on the target firmware. A generic AVPlay DRM-header option is not evidence of arbitrary non-DRM HTTP header support. Never embed an administrator token in a stream URL, JavaScript bundle or image source. This broker is a design proposal pending implementation and security tests, not an existing working service.

Use platform Key Manager secure storage for native Tizen credentials; fail closed if it is unavailable. Do not fall back to plaintext localStorage. Non-secret UI preferences can use local storage. Browser simulation may keep credentials only in memory and must identify that mode. Encrypt Android credentials using Keystore-backed storage, exclude them from backup/transfer and support Forget. Private connections stay configurable locally by the owner.

### 24.5 VPN and platform controls

Android Live TV remains blocked until verified NordVPN routing and tunnel-drop prevention apply to the actual playback app, including deep links, matchday actions and voice. System VPN indicators alone are insufficient. LAN access to media and Home Assistant must still work. OS-level VPN protection and bypass paths require independent testing.

Tizen does not inherit Android split tunnelling. A compatible native client and network policy are prerequisites. A proposed router or VLAN route must include fail-closed behaviour, correct DNS and IPv6 handling, and proof that other TV services and local integrations still work. Routing the whole TV is not equivalent to routing one app. If app availability or routing cannot be verified, Frame Live TV remains explicitly unavailable. Samsung TV Plus is separate and stays accessible without the ReamSmartz gate.

Sony input selection and Xbox wake are separate operations. Use a supported Xbox or Home Assistant integration for console wake and a model-verified Sony input control for HDMI 3. Preserve eARC/audio behaviour. Correct the receiver name to AirPin LITE on TCL; its installation does not prove boot startup or active AirPlay receiving. Echo Studio output depends on the real connection topology and supported codecs; preserve native audio controls and avoid promising universal Bluetooth, eARC or multiroom parity.

### 24.6 Voice and secondary household features

Keep remote control sufficient for every essential journey. Initial bounded voice commands may use an available local/system recogniser with a keyboard alternative. Never route an unrecognised command to a general home-control service that can bypass Live TV restrictions. Optional OpenAI live voice requires a server-held API key with short-lived client credentials or a server relay, visible microphone state, explicit activation and cancellation; select and test an appropriate UK voice rather than promising an exact accent. No long-lived API key belongs in either app.

Retain Amazon Music-inspired album artwork, the quiet matchday-only Spurs card without scores, room preferences, personal gallery, day/dusk/night mood, accessibility and optional safe performance advice. These features remain requirements even when their service adapters are unfinished. Personal gallery and native Samsung Art Mode are distinct modes; native artwork must contain no permanent interface. OS profiles, external streaming accounts and parental restrictions remain outside a launcher-only PIN's control. Explain these boundaries in owner settings without cluttering viewing screens.

## 25. Acceptance evidence and release plan

### 25.1 Native Samsung acceptance additions

The submitted Samsung AT-58 to AT-61 are mapped to NT-01 to NT-04 below, preserving the existing dock tests with those AT numbers. Each test records app revision, model, OS/firmware, setup, actual observations and evidence. A desktop simulation result never becomes a hardware pass.

| ID | Journey | Pass condition and failure boundary |
|---|---|---|
| NT-01 | Frame startup | Test standby, cold boot and reboot independently. App reaches foreground automatically only where supported; otherwise mark autostart unsupported or blocked and document manual launch. |
| NT-02 | Frame NAS playback | Representative supported files play in AVPlay with audio, subtitles, seek and resume. Record first-frame and buffering; unsupported codecs have a clear outcome. No blanket MKV or no-transcode promise. |
| NT-03 | Ring during native Netflix | Real doorbell and person events separately reach the coordinator; app launch and camera first frame are acknowledged without remote action; restoration and any resume limitation recorded. Failure blocks this release claim. |
| NT-04 | Samsung TV Plus | Discover verified native application, launch it, retain channel guide, recover our app using a documented supported route; preserve native installation and dependencies. |
| NT-05 | Home and Back | Physical Home opens Samsung Smart Hub; Back inside our app returns predictably; long Return retains OS exit behaviour. |
| NT-06 | Hidden lifecycle | Switch apps and resume repeatedly; no claim that suspended JavaScript delivered background events; restore focus and revalidate network and media state. |
| NT-07 | Private playback | Reject wrong-profile items, unsafe redirects, expired sessions and non-consented HTTP; no server tokens in URLs, logs or localStorage. |
| NT-08 | Frame protection | Ring returns active Art Mode to the prior artwork; reminders do not interrupt it; a fully off television stays off. |
| NT-09 | Service absence | Missing Android-only services show honest availability; no fake SmartTube/ReamSmartz native launch or silent replacement. |
| NT-10 | Live TV isolation | No traffic escapes during VPN loss or DNS/IPv6 bypass; per-app versus whole-TV routing is stated; otherwise keep the tile unavailable. |
| NT-11 | Signed install and update | Valid signed WGT installs on authorised target; same-author update retains configuration; rollback preserves stock Smart Hub and TV Plus. |
| NT-12 | Visual and remote parity | Compare shared mockup states, 100 rapid directional inputs, edit/save/cancel, accessibility and artwork identity across native codebases. |

### 25.2 Test levels and current evidence

Use four separate evidence levels: automated policy/unit tests; desktop/emulator integration; physical TV interaction; and household service end-to-end tests. Each capability lists its actual highest completed level and remaining gates. No all-TV-ready label is permitted while one required target or mandatory Ring/VPN path is unproven.

On 24 October 2026, the standalone CI preview was installed on the authorised Android TV 11 TCL with stock Home retained. Its home screen, controls, unconfigured library response, protected Live TV refusal, hold-OK edit menu, move cancellation, saved layout across restart and stock Home recovery were exercised. Hardware testing identified weak Settings focus visibility and focus restoration after reorder; fixes and subsequent results belong in the dated TCL report. A 100-directional-event smoke check is not an input-latency benchmark, and observed process memory is not a proven budget. No configured Jellyfin playback, Ring, VPN tunnel, Alexa, gallery-source or voice service acceptance is implied.

The Android preview has a passing earlier GitHub Actions build and separate current local checks. Native Tizen code is work in progress, with no signed household WGT, AVPlay hardware pass, autostart proof or Frame Ring/Art Mode handoff yet. Sony hardware remains untested in this phase. Requirements are not implementation claims.

### 25.3 Delivery sequence and definition of done

1. Finish the safe Android preview and real TCL remote checks, preserving the factory launcher. Record startup/input/memory baselines and fix regressions. Keep local device addresses and raw diagnostics private.
2. Complete native Tizen shell, local secure settings, installed-app discovery, remote focus, dock editing, catalogue and embedded-player lifecycle with automated and simulation tests. Share design tokens and journey fixtures; never present browser fallback playback as AVPlay proof.
3. Implement and test the protected media route, server-restricted profiles and real watch-state round-trip on both runtimes. Add bounded offline caches and accurate readiness screens.
4. Prove external Frame event coordination, native app launch and Art Mode recovery; prove Android alert overlays separately, including DRM/input limitations. Keep all unsupported gaps explicit.
5. Prove VPN isolation and failure behaviour before enabling protected Live TV. Validate music, fixtures, AirPlay, audio and optional voice according to actual service permissions.
6. Produce reproducible APK and WGT builds with dependency/licence review, secret scanning, stable signing and release artefact hashes. GitHub Actions tests and packages Android; native Tizen packaging and signing need their own supported toolchain and protected owner credentials.
7. Release only against the complete AT and NT matrix with per-TV evidence, remaining limitations, install/update/recovery instructions and owner-controlled configuration. Claim tested support only for models and firmware actually exercised. Other TVs remain potential adapter targets, not blanket compatibility claims.

### 25.4 Official technical sources for these revisions

Research reviewed 24 October 2026. Vendor documentation defines API and lifecycle boundaries; household behaviour still requires tests. The media broker and external Ring coordinator are proposed engineering designs inferred from those boundaries.

- Samsung multitasking https://developer.samsung.com/smarttv/develop/guides/fundamentals/multitasking.html
- Samsung termination and Return https://developer.samsung.com/smarttv/develop/guides/fundamentals/terminating-applications.html
- Samsung remote input https://developer.samsung.com/smarttv/develop/guides/user-interaction/remote-control.html
- Tizen Application API https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/application-api.html
- Samsung AVPlay https://developer.samsung.com/smarttv/develop/api-references/samsung-product-api-references/avplay-api.html
- Tizen Key Manager https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/keymanager-api.html
- Samsung web engines https://developer.samsung.com/smarttv/develop/specifications/web-engine-specifications.html
- Samsung signing https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html
- Samsung consumer Art Mode https://www.samsung.com/us/support/answer/ANS10005239/
- Home Assistant Samsung https://www.home-assistant.io/integrations/samsungtv
- SmartThings capabilities https://developer.smartthings.com/docs/devices/capabilities/
- Home Assistant Xbox https://www.home-assistant.io/integrations/xbox/
- Home Assistant Ring https://www.home-assistant.io/integrations/ring/
- Media3 formats https://developer.android.com/media/media3/exoplayer/supported-formats
- OpenAI voice security https://developers.openai.com/api/docs/guides/voice-webrtc

Version 2.0 preserves the detailed earlier design and acceptance requirements while replacing the external Frame runtime and VLC playback decisions. Unimplemented features remain requirements and do not become working capabilities through this document.


v2.1 historically recorded the fixed-dock decision, material rendering pass and VPN handoff/status work. Native Tizen and earlier household integration gates remain unfinished.


## 26. v2.2 continuation acceptance additions

| ID | Requirement | Pass evidence |
|---|---|---|
| UX-01 | Two-column Settings | Remote navigates every category and control; focus and Back behaviour checked on TCL and simulated Android. |
| UX-02 | Editable curated Home | Fresh six defaults, Open/Move/pin/unpin/info menu, persistence, cancellation and protected-app checks pass; child authorisation cannot be bypassed. |
| LIB-01 | Automatic discovery | No saved source leads directly to search; actual NAS appears by name without typed address; timeout/Retry remain usable. |
| LIB-02 | Exact library scope | The three locally chosen roots are the only viewer collections; wrong/other server roots are never automatically exposed. |
| LIB-03 | Native DLNA direct play | Representative files play, pause, seek, stop and return correctly on each supported runtime; unsupported codecs fail clearly. |
| LIB-04 | Progress truth | Jellyfin start/progress/stop reaches the server and resume is verified; DLNA-only playback is never labelled server-synchronised. |
| GAL-01 | Five example images | All five original images load, rotate, fit and respond to pause/next/previous/Back without private data appearing. |
| VPN-01 | Four states | Distinct Connected, Disconnected, Checking, Unknown; no state alone unlocks Live TV. |
| MUS-01 | Official Music route | Correct installed Amazon Music app opens; missing-app route uses the supported store; account search/playlists work in the official app after sign-in. |
| MUS-02 | Custom Music gate | Authorised API/media control access is proven before claiming custom playlist/search/Now Playing support. |
| REL-01 | Version retention | GitHub version/tag, APK, hash, notes, limitations and test results retained; no overwrite of previous release or another agent's code. |
| REL-02 | Updated UX evidence | Fresh screenshots belong to the exact physically tested APK; no substitution of old screenshots. |

Version 2.2 incorporates the latest owner directions for editable Apps, two-column Settings, automatic DLNA, exact media scope, Amazon Music, authorised sample art, privacy and retained GitHub releases. Platform limitations remain explicit and implementation status is kept separately.
