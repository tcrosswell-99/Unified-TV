# Unified TV design system

**North star:** Content first, underlying technology hidden.

The intended experience is a personal cinema with the calm of a gallery. This document is a shorter development entry point to the detailed [Product Spec, section 4](PRODUCT_SPEC.md#4-premium-interface-and-interaction-system-p0-design-release-gate).

## Resting home screen

Use a single, centred translucent dock near the bottom of a **full-screen relevant image**. Place a restrained 24-hour clock and tiny status/voice/settings affordances above, leaving generous empty space. Do not show a static grid of dozens of applications, promotions, permanent recommendations or technical diagnostics.

Six default choices in the reference deployment: **SmartTube, Netflix, Disney+, BBC, Live TV, Music**. Sony adds an Xbox shortcut; Samsung Frame adds native Samsung TV Plus access, pending the relevant bridge. Keep the layout and remote controls consistent, not the exact same set of tiles in every room.

**Press Down** to reveal a curated personal NAS films/TV poster row. The end of this row contains **View All**, which opens a custom library with films, shows, seasons, episodes and search. Selecting media should play through VLC without showing a VLC launcher tile.

## Add and rearrange favourites

The resting dock is editable without a phone or developer tool. Open the subtle **Apps** grid icon to find installed apps and select **Add to Home**. **Remove from Home** hides the shortcut but leaves the app installed.

On Home, **hold OK** over a tile for **Move**, **Remove from Home** and optional **App info**. **Move** lifts the tile; Left/Right adjusts its position, OK saves and Back cancels. Keep the movement restrained and remote response immediate. Short OK always launches.

Retain one horizontally scrollable dock when more apps are pinned than fit. Persist order per room without changing the other TVs' layouts; Sony Xbox and Frame Samsung TV Plus remain device-only actions. Optionally lock editing with a PIN for children. All supported apps remain recoverable from the Apps library.

See [full app personalisation requirements](APP_CUSTOMISATION.md).

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
