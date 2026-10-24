# Additional architecture proposal: decisions

The owner's pasted proposal was reviewed against the full v1.8 draft and prior v1.7 decision record. The full specification becomes v1.9 draft. This is requirements reconciliation, not a hardware completion claim.

| Proposal | Latest decision |
| --- | --- |
| Adult / Child profiles | Adopt separate local media accounts and server-enforced permissions. Require an adult PIN to return from Child or edit. Changing launcher profile does not switch SmartTube or another app's account. Android and external apps are outside this boundary. |
| Embedded ExoPlayer | Implement a Media3 preview trial with direct server streams and real progress reports. Keep VLC compatibility comparison in the release plan until actual files, codecs, subtitles and seek pass. No VLC dock tile. |
| Accurate Continue Watching | Report actual embedded-player position. Do not label a row Continue Watching until server persistence and interrupted-playback tests pass. |
| Fixed dock allowlist | Preserve agreed Add, Remove and Move, optionally PIN protected. Six choices are defaults, not a compulsory ceiling. |
| Replace native Samsung features | Preserve native Frame Art Mode and Samsung TV Plus. An Android FAST app can be an additional installed shortcut, not a silent replacement. |
| Split tunnelling guarantees no leaks / zero delay | Treat as a routing configuration to prove. ConnectivityManager cannot prove NordVPN ownership, exit country or firewall enforcement. A foreground scrim cannot stop another app's traffic. Preview keeps Live TV blocked. |
| Silent NordVPN Intents | Do not invent unsupported commands. Opening VPN settings differs from connecting and verifying protection. |
| Background AirPro LITE | Correct name: AirPin LITE. Provide setup; do not auto-start or show an operational icon without supported status and reboot evidence. |
| Hardware symbiosis | Adopt: delegate Wi-Fi, Bluetooth, sound, CEC and OLED care to native settings. No automatic package changes. |
| Local voice plus cloud discovery | Retain as a future two-tier option. Speech recognition has latency and hardware constraints; no zero-latency guarantee or cloud key in APK. |
| Ambient app fallbacks | Only original brand-aware imagery behind a highlighted app. Scenic imagery belongs to neutral/gallery states. |

New acceptance work: profile isolation through browsing/playback, PIN boundary, restricted server account validation, actual progress round-trip, native settings access, refused credential redirects, bounded network/image loading. Hardware evidence remains mandatory for Ring, VPN, Samsung, audio and performance claims.
