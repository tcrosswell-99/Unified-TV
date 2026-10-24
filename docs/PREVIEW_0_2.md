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

Child mode separates this app's media account. It does not secure Android, prevent third-party app launches, implement a kiosk or select their accounts. Configure restricted libraries/ratings on the server. Never use a global API key for Child. PIN retries are throttled after five failures. This remains an app editing boundary, not a hardened kiosk. Owner recovery is preview data reset/uninstall; a friendlier recovery flow remains release work.

App artwork uses original brand-aware gradients. Gallery is an initial implementation; configurable timing, weighting, transitions, time-of-day mood and OLED measurements remain. The media shelf opens as a separate progressive destination; the final rising Home transition is pending. Offline poster caching,  complete Home accessibility and polished visual handoff remain.

Experimental household adapters now cover HA doorbell/person subscriptions, HLS camera overlays, private reminder events, configured room scripts, fixture-calendar display and bounded local voice commands. These are source implementations, not verified household integrations. Now Playing, OpenAI realtime discovery, verified VPN routing, shared room preferences and model-safe cleanup remain unimplemented. Native Samsung actions require actual owner scripts and supported TV integrations. Full v1.9 requirements remain in force. No TV installation or validation has occurred.

## Build

JDK 17, checksum-pinned wrapper in `launcher/`, SDK platform 35 and build-tools 35.0.0. `bash preview/build.sh` produces a test-signed APK, checksum, compiled manifest and unit/lint reports. Windows: `launcher\gradlew.bat -p preview testDebugUnitTest lintDebug assembleDebug`. No TV connection is needed.

CI uploads `UnifiedTV-Preview-test-only` and `UnifiedTV-test-and-lint-reports`. Fresh CI runners may have different debug keys. A different-key update requires uninstalling only the preview, losing local data. Stable private signing and migrations are required before supported release.

See [Windows sideload and rollback](FIRST_ANDROID_PREVIEW.md).

See [household adapter setup](HOUSEHOLD_ADAPTERS.md) and the [all-TV test matrix](ALL_TV_TEST_PLAN.md). A read-only Performance & Cleanup screen reports available memory/storage and partial local input-to-draw samples; no cleanup is enabled.

Catalogue browsing now pages 24 items at a time and includes Genres, limiting poster memory while retaining access to the full server catalogue. General assistant requests remain disabled pending a scoped discovery relay; no unconstrained conversation agent can launch Live TV indirectly.
