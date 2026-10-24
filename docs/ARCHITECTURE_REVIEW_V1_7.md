# v1.7 architecture review and confirmed TCL hardware

This decision record accompanies the **full v1.8 specification**. The short proposed v1.7 draft is not a replacement for all previous requirements.

| Proposal | Decision |
| --- | --- |
| Enter Home Assistant and NAS/Jellyfin addresses locally with the remote | Adopt as P0, with tested secure token handling and offline fallback |
| Lock the dock to exactly six choices, removing Add/Remove | Reject. Six are defaults; family members can pin and rearrange apps, with optional parent edit lock |
| CI build then Windows PC to TCL sideload before replacing Home | Adopt as mandatory Stage 0.5, with a separate non-HOME app and safe uninstall |
| Switch from VLC to embedded Media3/ExoPlayer immediately | Compare against known-working VLC on real formats and subtitles before selecting |
| 500 ms guaranteed 4K start | Not a verified target; benchmark real hardware |
| Split tunnelling plus VPN status/overlay guarantees protected ReamSmartz | Reject guarantee. Packet-level tests under drop and reboot are needed; block protected Live TV until proven |
| Silent NordVPN connection through third-party Intent | Only with documented supported app control; do not invent an API |
| Third-party receiver called AirPro LITE | Corrected: owner's TCL image identifies **AirPin LITE**. Startup/service behaviour still needs checking |
| Eliminate Samsung native Tizen features | Reject. Preserve native Art Mode and Samsung TV Plus as previously agreed, even with an external Android streamer |
| Local voice plus OpenAI | Investigate faster local commands without sacrificing ChatGPT's general spoken answers |
| Replace app-relevant artwork with unrelated trending shows | Reject. Use genuinely matching cleared artwork, or brand-related original atmospheric fallback |

## Verified from owner's TCL photographs (24 October 2026)

- TCL Android TV, **Android TV OS version 11**, system identifies the Android TV model as **Smart TV Pro**.
- Android TV security patch **5 March 2024**, build displayed **RP1A.200622.001 release-keys**.
- **AirPin LITE** is installed and visible on the Home screen. Installation alone does not prove its boot startup or AirPlay discovery.
- TCL is connected to its Wi-Fi network. Private network name, full addresses and hardware identifiers are deliberately excluded from this public record.
- Official wireless ADB pairing on TVs requires checking OS/version-specific support. On this Android TV 11 unit, do not assume Android TV 13+ pairing screens exist. Use a device-supported developer network debugging route only with explicit permission; otherwise investigate manual APK sideloading.

## References

- https://developer.android.com/tools/adb
- https://developer.android.com/develop/connectivity/vpn
- https://developer.android.com/media/media3/exoplayer/supported-formats

No cleanup, network probing or test APK installation is implied by this document.