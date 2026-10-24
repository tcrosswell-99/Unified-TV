# Unified TV Android Preview 0.4

This separately installable test preview adds two-column Settings; automatic named UPnP/DLNA server search and encrypted selected-folder configuration; a native Media3 DLNA direct-play trial; five owner-authorised AI gallery images with timing and remote controls; four VPN connectivity states; and an official Amazon Music launch/store route. BBC and SmartTube package recognition is updated.

Local verification: 37 Android tests, zero failures/errors/skips; lint and debug/deviceCheck APK builds passed. The tagged release workflow independently repeats preview verification and publishes test reports, manifest, APK and SHA-256. Full repository CI is a separate check on the pull request.

The NAS MediaServer and ContentDirectory were discovered from the PC after the owner restored the service. The TCL developer connection was unavailable at the latest check. This update has not been physically installed/tested on TCL or Samsung, and there are no new 0.4 screenshots. Existing 0.3 evidence must not be presented as this version.

Important limitations:

- Latest editable Apps/pin/unpin requirement is not implemented; current dock still has fixed approved membership with reorder.
- DLNA folder UI is an initial connectivity implementation. Poster-first parity, sparse pagination, complete playback/codec tests and Continue Watching remain unfinished.
- Live TV remains blocked pending verified VPN routing/drop protection. Connected status only observes an Android VPN transport.
- Amazon Music custom playlists/search/player, actual official app installation/sign-in, HA setup, real Ring/reminder flows and general OpenAI voice remain unverified or unfinished.
- Android APK does not run on Samsung Tizen. No native Frame package, boot foreground, TV Plus or Art Mode acceptance is claimed.
- No factory launcher replacement or cleanup. Household settings remain private; no server is hardcoded.
- This APK is debug-signed. CI signing keys may differ between builds. Do not uninstall/clear an existing configured preview to resolve a signature mismatch. Back up and establish an intentional signing/migration plan, or use a distinct test variant built with your local key.

The source is isolated on its own branch to preserve concurrent agent work. Partial untracked Samsung work is excluded. Older releases/tags are retained. Read the accompanying specification, feature status, handover and Windows instructions before installation.
