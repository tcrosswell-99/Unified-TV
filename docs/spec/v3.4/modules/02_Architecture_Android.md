# Android TV architecture — v3.4

**Targets:** TCL Android TV 11 first; Sony BRAVIA Google TV separately after G3. Exact TCL/Sony model and remote mappings remain unverified.

## Preview and launcher are distinct installable identities

- **Preview:** existing application remains installed, retains its package ID and signing lineage; **no HOME intent filter**, not a selectable system launcher. Do not uninstall, clear its data or silently convert it.
- **Launcher:** new independently identified **package ID**, separate installed application, own durable release signing identity **before first daily-use install**. Declare only supported Android HOME categories/intents; owner chooses through the standard launcher selection flow **after** documented stock launcher recovery succeeds.
- No requirement to rotate the lost GitHub throwaway key for the old preview. Android signing rotation exists since Android 9, but rotation cannot be executed without the prior key or an otherwise valid signing lineage. The old preview's reported lost key is a **repository/installed-APK verification item**, not an excuse to uninstall it.
- Verify build variants, manifests, package names, signatures, installed versions and data migration boundaries. Separate package IDs do **not** share app-private data automatically: offer explicit user-mediated settings transfer or fresh one-time setup, not a fictional seamless migration.

## Device behaviour

Test root Back, Home from external apps, OEM shortcut buttons, repeated Back, app launch/return, cold boot, process death, standby/wake, D-pad hold/long press and owner-accessible Settings escape. App cannot intercept manufacturer-reserved keys inside other apps. Prove stock launcher recovery independently before asking owner to select Unified TV as default.

## Rendering and media

Android 11 TCL cannot depend on Android 12-only `RenderEffect`; use compatible compositing/blur fallbacks. Reuse cached/pre-rendered small light-field textures transformed on compositor; do not repaint full-screen radial gradients every frame. Media3 for supported embedded playback; real codec/subtitle/seek proof on TV. Preserve accessibility and focus responsiveness under reduced motion.

## Physical proof

Cloud agent writes code, CI/tests and a copy/paste checklist. **Owner runs PC/device commands** (including `install_preview.py`, `vpn_check.py` and signing inspection tool where present), captures outputs and selects default launcher only after explicit go/no-go. Do not assume cloud access to TVs or mark G1 passed without owner evidence.
