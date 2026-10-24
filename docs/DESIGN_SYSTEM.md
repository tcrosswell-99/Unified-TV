# Unified TV design system

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

1. Focus moves **immediately**. The selected tile gently rises; it does not launch, play a trailer or start a VPN.
2. If focus is stable for roughly 110–160 ms, request that item's genuine artwork.
3. The previous hero fades into the correct current one over roughly 320–480 ms, using modest blur/soft lighting.
4. Supporting title and metadata fade in *only when useful*, without creating another large card.
5. If remote input continues, discard superseded art and finish on the **actual final focused item**.

**Absolute visual rule:** highlighting Netflix must never leave a generic scenic landscape or another film's artwork in place as if it describes Netflix. When highlighting a film, the background must represent **that film**, with fallback based on the *same title's poster* if full-size art is missing.

Remote interaction outranks animation and network imagery on the Sony and low-memory TCL. Use precalculated blur, bounded image memory, cached gradients and transform/opacity motion rather than full-screen real-time refraction.

## Interactions that remain hidden until needed

- **Ring live view:** interrupt artwork automatically on doorbell press or detected person, restore prior state afterward; show true loading state, never call a static preview live. Highest priority.
- **Alexa reminder:** brief text toast over viewing, without stealing playback focus; lower priority than Ring.
- **VPN:** tiny truthful status/exit country when connected; show a Connect/Retry/Back choice only when opening guarded Live TV without verified VPN.
- **Tottenham:** fixture card only on matchday, no scores.
- **ChatGPT:** tiny glyph at rest; short-lived waveform and spoken/visual answer only when activated.
- **Music:** large album art, soft album-derived atmosphere, familiar transport actions.
- **Artwork mode:** edge-to-edge photographs/art only. Samsung native Art Mode has **no clock or app controls**.

## Motion and accessibility

Initial timing tokens are engineering *starting values*, not a claim that they have been tested. Aim for acknowledgement within 80 ms, a focus movement around 150–210 ms, and a background dissolve of 320–480 ms. Test rapid repeated D-pad presses. Back restores the prior item and screen. Offer reduced motion, increased contrast and reduced translucency without loss of information.

Build original components and use cleared assets. Contemporary Apple tvOS, [CleanFrame by Nathan Clark](https://github.com/nthnclrk/CleanFrame) and Dribbble examples are **style references**; do not copy proprietary layouts, artwork, fonts, icon files or animations into the public codebase. CleanFrame's MIT-licensed source has not been incorporated into Unified TV. See [Credits](../CREDITS.md) for author attribution and required checks before any future reuse.
