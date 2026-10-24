# Standalone preview 0.2

Package `io.unifiedtv.preview`, min Android 9/API 28, target API 33. Android TV 11 is within range. No HOME registration, boot receiver, accessibility service, cleanup or exported setup activity.

## Implemented in source

- Six curated shortcuts, Apps picker, pin/unpin, hold-OK Move, save/cancel, local order/focus persistence. Fixed-size tiles scroll through one dock. Removed curated defaults remain available in Apps. Known ReamSmartz/Smarters aliases stay blocked when pinned independently.
- Up opens Apps / Settings / Profiles / Gallery; Down opens the recently-added shelf and View All. Native setup/media controls provide D-pad and screen-reader semantics. Canvas Home accessibility needs a full audit.
- Home Assistant and separate Adult/Child media connections: Enable, host, port, HTTPS, explicit HTTP consent, masked token, user ID, Test/Save/Edit/Forget. Testing does not save. No household endpoints embedded.
- AES-GCM Android Keystore-backed encrypted connection/gallery records; backups disabled. IPv4, IPv6 and local hostnames. No redirects or TLS trust bypass. Trusted HTTP is limited by the app to the configured origin.
- Film/series catalogue, search, season/episode navigation, bounded authenticated posters and exact-item hero requests with stale-result guards. Child requires its own non-admin user token. Recently added is not called Continue Watching.
- Embedded Media3 direct-play trial, remote controls, same-origin authenticated streaming, seeking and real position reports. No URL tokens. Household playback, codec support and progress persistence are untested. No instant-4K, automatic transcoding or reliable-resume claims.
- PIN editing/profile boundary, appearance controls, gallery document selection, 5-minute foreground Home idle entry. Persisted document access, bounded image decoding, no gallery chrome. Native TV settings and manual AirPin setup; no false receiver status.

## Boundaries and remaining work

Child mode separates this app's media account. It does not secure Android, prevent third-party app launches, implement a kiosk or select their accounts. Configure restricted libraries/ratings on the server. Never use a global API key for Child. PIN rate limiting and owner recovery are release work.

App artwork uses original brand-aware gradients. Gallery is an initial implementation; configurable timing, weighting, transitions, time-of-day mood and OLED measurements remain. The media shelf opens as a separate progressive destination; the final rising Home transition is pending. Offline poster caching, full pagination/genres, complete Home accessibility and polished visual handoff remain.

Ring automatic takeover, Alexa overlays, voice, fixtures, Now Playing, verified VPN routing, Samsung TV Plus/Art Mode, Xbox actions, shared room preferences and model-safe cleanup remain unimplemented. Full v1.9 requirements remain in force. No TV installation or validation has occurred.

## Build

JDK 17, checksum-pinned wrapper in `launcher/`, SDK platform 35 and build-tools 35.0.0. `bash preview/build.sh` produces a test-signed APK, checksum, compiled manifest and unit/lint reports. Windows: `launcher\gradlew.bat -p preview testDebugUnitTest lintDebug assembleDebug`. No TV connection is needed.

CI uploads `UnifiedTV-Preview-test-only` and `UnifiedTV-test-and-lint-reports`. Fresh CI runners may have different debug keys. A different-key update requires uninstalling only the preview, losing local data. Stable private signing and migrations are required before supported release.

See [Windows sideload and rollback](FIRST_ANDROID_PREVIEW.md).
