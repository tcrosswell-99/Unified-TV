# Apps and home dock customisation

**Status:** required Unified TV feature (P0), not yet implemented in the inherited launcher as specified here.

The home screen is a **personal shortlist**, not a fixed collection of apps. The six shared services are defaults, not a permanent allowlist; parents can optionally protect layout editing. A viewer must be able to add, remove and reorder applications using only the television's normal remote. The design should feel as calm as the rest of the glass home screen: **content first, underlying technology hidden**.

## Installed apps library: Add to Home

An unobtrusive grid icon in Home's utility area (or **Apps & Layout** under Settings) opens the installed app library. It is not another row permanently cluttering Home. The grid lists available launchable apps with their correct identities and a clear **Add to Home** action. Already pinned choices say **On Home** or **Remove from Home**. A new installed app appears without manually editing a configuration file.

Pressing **Add to Home** adds the shortcut once, returns to Home with the new tile focused, and shows that app's correct contextual artwork. The action never launches the app. Unpinning the shortcut never uninstalls or disables its application, logs the household out, clears app data or changes bloatware settings. The library remains available even if all dock shortcuts are removed.

## Move the dock tiles

1. **Short OK** on a focused dock tile always launches it. **Hold OK** opens a small, anchored glass menu: **Move**, **Remove from Home**, and **App info** where available.
2. Choose **Move**: the focused tile rises slightly above its neighbours, with a subtle **Left / Right to move · OK to save · Back to cancel** instruction. Directional presses reorder the tiles with smooth, low-cost sliding animation.
3. **OK** stores the new order. **Back** cancels, restores the exact previous order and refocuses the original app. An incoming Ring overlay or sleep event must not save a half-finished drag.
4. Return from playback, standby and a device restart preserves order, selection and any hidden shortcuts.

No gesture, mouse, keyboard, phone, developer connection or ADB is required for these everyday edits. An optional parental PIN protects layout changes without blocking normal viewing.

## Per-TV personalisation without visual inconsistency

The dock's order, pinned apps and related preferences are **stored per television**. A household may copy a template between rooms, but one room's changes must never silently overwrite another's.

- Sony has the additional **Xbox** device action, when configured for its console and HDMI input.
- Samsung Frame has **Samsung TV Plus** only through an explicitly tested native Tizen bridge; do not display a false working Android tile.
- **Live TV** always retains its NordVPN launch and traffic safety checks, regardless of position.
- Unavailable applications or actions should show a clear unavailable state, not launch an unrelated programme.

More pinned apps must **not** force a second dock row or microscopic type. Scroll the single dock horizontally with restrained edge cues. Maintain the same dimensions, focus treatment and remote navigation across TCL, Sony and the Frame external device. Respect reduced motion and accessibility settings.

## Acceptance tests

- **AT-56:** Install a supported app; open Apps; choose Add to Home; tile appears with correct logo/context and launches with ordinary OK.
- **AT-57:** Hold OK, choose Move, reposition using arrows, save; new order persists after standby/reboot.
- **AT-58:** Begin Move, reposition and press Back; original order is fully restored. Repeat with a Ring alert during editing.
- **AT-59:** Remove from Home, verify the application remains installed and retains its data, then add the shortcut again.
- **AT-60:** Configure Sony Xbox and Frame Samsung TV Plus separately; confirm other TVs do not show those actions and the Live TV VPN gate survives reordering.
- **AT-61:** Pin more than seven items, test horizontal scrolling, child edit lock and reduced motion. No overlapping, illegible or missing items.

Implementation and tests must follow [the full product specification](PRODUCT_SPEC.md), [design system](DESIGN_SYSTEM.md) and [device compatibility matrix](COMPATIBILITY.md).