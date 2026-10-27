# Unified TV design system

**Current Android candidate: Preview 0.4.** Two-column Settings, automatic local media-server discovery, encrypted selected folders, embedded DLNA playback trial, five authorised gallery examples and four VPN states. See [feature status](FEATURE_STATUS.md), [complete v2.2 specification](PRODUCT_SPEC.md) and [AI handover](AI_HANDOVER.md). Local Android checks passed; new TCL/Frame hardware acceptance remains outstanding. The latest Apps request allows owner pin/unpin, but that menu is not implemented in this build. It supersedes older fixed-membership requirement text below.


**Latest owner direction, 24 October 2026:** the dock is now a fixed approved set, with rearrangement only. Add/remove/pin and the arbitrary Apps picker are removed. This supersedes earlier editable-shortlist language. The approved shared six are SmartTube, Netflix, Disney+, BBC, Live TV and Music; Sony adds Xbox and the native Frame plan adds Samsung TV Plus. See [Preview 0.3](PREVIEW_0_3.md) for implementation and remaining VPN gates.

**North star:** Content first, underlying technology hidden.

The intended experience is a personal cinema with the calm of a gallery. This document is a shorter development entry point to the detailed [Product Spec, section 4](PRODUCT_SPEC.md#4-premium-interface-and-interaction-system-p0-design-release-gate).

## Resting home screen

Use a single, centred translucent dock near the bottom of a **full-screen relevant image**. Place a restrained 24-hour clock and tiny status/voice/settings affordances above, leaving generous empty space. Do not show a static grid of dozens of applications, promotions, permanent recommendations or technical diagnostics.

Six default choices in the reference deployment: **SmartTube, Netflix, Disney+, BBC, Live TV, Music**. Sony adds an Xbox shortcut; Samsung Frame adds native Samsung TV Plus access, pending the relevant bridge. Keep the layout and remote controls consistent, not the exact same set of tiles in every room.

**Press Down** to reveal a curated personal NAS films/TV poster row. The end of this row contains **View All**, which opens a custom library with films, shows, seasons, episodes and search. Media plays through embedded Media3 on Android and AVPlay on native Tizen; VLC is excluded.

## Rearrange approved services

Only the approved six or platform-specific seven services belong to Home. Hold OK offers Move and Cancel; arrows move, OK saves and Back restores. No Add, Remove or arbitrary Apps picker. Migrate old layouts through DockPolicy so missing approved entries return and arbitrary packages disappear.

Use cached low-resolution backdrop blur, a translucent vertical tint and fine top highlight for the dock. The selected tile lifts with a feathered contact shadow and subtle lens material; reserve strong outlines for High Contrast. Brand ambience uses original overlapping radial light fields without unrelated scenery. Neutral Home uses the bundled original AI-generated coastal landscape. Administrative pages use darkened blurred scene continuity; keep setup text readable. Replace permanent directional text with a quiet downward chevron.

## Every selection changes the atmosphere

1. Focus moves **immediately**. The selected tile gently rises (185 ms) with a crisp outline and a soft halo in the app's colour; it does not launch, play a trailer or start a VPN.
2. **Dock apps:** the background crossfades to that app's own light over **400 ms, starting the moment focus moves** (ease in and out). The app's name changes with it.
3. **Films and series:** a title's own poster, softened and darkened, crossfades in after a 135 ms settle, because its artwork may still be decoding.
4. If remote input continues, the latest focus always wins: the crossfade retargets from what is visible and never queues.
5. Supporting title and metadata appear *only when useful*, without creating another large card.

**Absolute visual rule:** highlighting Netflix must never leave a generic scenic landscape or another film's artwork in place as if it describes Netflix. When highlighting a film, the background must represent **that film**, with fallback based on the *same title's poster* if full-size art is missing.

Remote interaction outranks animation and network imagery on the Sony and low-memory TCL. Use precalculated blur, bounded image memory, cached gradients and transform/opacity motion rather than full-screen real-time refraction.

### Tokens in use (Preview 0.5)

| Token | Value | Where |
| --- | --- | --- |
| Dock crossfade | 400 ms, ease in and out, from the focus change | `UnifiedPreviewActivity`, `AmbientLayer` |
| Title settle | 135 ms (films and series only) | `UnifiedPreviewActivity` |
| Tile lift | 185 ms, scale 1.07 | `UnifiedPreviewActivity` |
| Ambient drift | 20–40 s paths, 2–5 % of the screen, scale 1.00–1.06, about 30 updates a second, compositor-moved | `AmbientMesh`, `AmbientLayer` |
| Palettes | SmartTube crimson, Netflix dark red, Disney+ navy with cyan, BBC magenta, Live TV violet, Music peach and pink; Xbox, TV Plus, neutral slate | `AmbientMesh` (shared with Samsung via `samsung/src/palettes.json`) |
| Time-of-day wash | none, none, warm 13 %, charcoal 26 %; 20-minute blends | `DayMood` |
| Wallpaper crossfade | 900 ms; zoom 1.00–1.03 per picture | `WallpaperActivity` |
| OLED care (Sony) | dim to 45 % after 2 minutes idle; 2-pixel orbit every 3 minutes | `DayMood` |
| Reduce motion | crossfades instant, no drift, no zoom | all |
| Keep background still | no drift; crossfade kept | Home |
| Lightweight | still frames, 200 ms crossfade | Home |

## Settings and setup always use the sidebar (never break)

Owner design principle, 25 October 2026. **Every settings or setup screen opens as the glass sidebar that overlays a still Home screen**: Settings itself, choosing NAS folders and media servers, Home Assistant, football, film posters, phone (QR) setup, household setup, apps and dock management, Kids and profiles, taking over the Home button and returning to the original TV interface (OD-28), and anything added later. Home stays visible and does not move behind it; the sidebar is half the screen, widening to about three quarters only for wide tasks (module 13). A full-screen settings or setup page is a defect, whatever it is for. Content you watch or browse (the film library itself, the player, Wallpaper) is not a settings screen and may use the full screen. The one exception is Home's safe screen (OD-28), shown in place of Home after two crashes within a minute of starting: plain full-screen text and two buttons, deliberately without the glass or anything else from Home that could fail again.

## Interactions that remain hidden until needed

- **Smoke or carbon monoxide alarm:** the highest priority (owner decision OD-07). Full-screen, each alarm separate with its room and time, acknowledged one at a time; a banner while any is active; the doorbell waits until every alarm has cleared.
- **Ring live view:** a large glass-framed card on doorbell press or detected person; "Connecting…" until real frames, then **Live**; never call a still live. Second only to an alarm.
- **Alexa reminder:** brief text toast over viewing, without stealing playback focus; lower priority than Ring.
- **Home view:** optional Home Assistant glances and alerts, one at a time in the order set in [Home view](HOME_VIEW.md#alert-priority); nothing appears when disconnected.
- **VPN:** tiny truthful status/exit country when connected; show a Connect/Retry/Back choice only when opening guarded Live TV without verified VPN.
- **Tottenham:** fixture card only on matchday, no scores.
- **ChatGPT:** tiny glyph at rest; short-lived waveform and spoken/visual answer only when activated.
- **Music:** large album art, soft album-derived atmosphere, familiar transport actions (Now Playing, from the TV's own media session).
- **AirPlay:** a small glyph in the utility group only while AirPin LITE is really receiving; brighter with a halo while casting; otherwise nothing.
- **Artwork mode:** edge-to-edge photographs/art only. Samsung native Art Mode has **no clock or app controls**.

## Motion and accessibility

Timing tokens are engineering *starting values*, not a claim that they have been tested. Aim for acknowledgement within 80 ms and a focus movement around 150–210 ms; the background crossfade is 400 ms (see the tokens table). Test rapid repeated D-pad presses. Back restores the prior item and screen. Offer reduced motion, increased contrast and reduced translucency without loss of information.

Build original components and use cleared assets. Contemporary Apple tvOS, [CleanFrame by Nathan Clark](https://github.com/nthnclrk/CleanFrame) and Dribbble examples are **style references**; do not copy proprietary layouts, artwork, fonts, icon files or animations into the public codebase. CleanFrame's MIT-licensed source has not been incorporated into Unified TV. See [Credits](../CREDITS.md) for author attribution and required checks before any future reuse.
