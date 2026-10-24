# Preview 0.3 material and VPN pass

This is an Android preview, not a completed all-TV release. It retains the separate non-HOME identity. The factory launcher is preserved.

## Changes

- Approved dock membership with Move/Cancel only; old arbitrary pins are discarded and missing approved choices restored. No Apps picker or add/remove path.
- Bundled original AI-generated coastal landscape for neutral Home and an explicit Gallery selection. No network or personal photo is required for that gallery option.
- Original ambient radial mesh per service, softer palette, cached bounded blur, frosted dock tint, fine top light and lifted tile with soft contact shadow. Full-screen live blur is avoided on TCL.
- Quiet header typography and downward chevron, removing permanent navigation text. Basic accessibility preferences remain; full audit is outstanding.
- Darkened blurred active-scene material behind administrative screens and translucent unfocused controls.
- Connect/Retry/Cancel sheet for protected Live TV. Connect opens NordVPN through its standard Android launch intent. There is no claimed private NordVPN connection API and no invented silent-connect intent.
- Android VPN transport observation with lifecycle-bound callback and cached rendering status. VPN seen means a transport is visible; it does not certify provider, protected-app routing or kill-switch behaviour. Retry never unlocks playback merely because the signal is present.

## TCL NordVPN evidence and genuine blocker

The owner-authorised TCL has NordVPN 9.15.2-tv installed. Its supported Secure my connection action established a UK connection over NordLynx. No credentials were entered or exported. Private connection screenshots and network addresses remain outside GitHub.

Read-only inspection found auto-connect Off, split tunnelling Off and no Android always-on/lockdown configuration. The standard android.settings.VPN_SETTINGS intent is not available on this TCL firmware. A working NordVPN connection is therefore proven, but the required per-app isolation and no-leak-on-drop behaviour are not. No privileged system VPN settings were changed, and Live TV remains blocked. No packet-capture or dropout acceptance claim is made.

NordVPN's documented Android TV path is its own Quick Connect and settings UI, with supported auto-connect, split-tunnel exclusions and LAN discovery options. Its Android kill-switch guidance depends on OS/ROM support. A normal third-party launcher has no established vendor API to silently toggle this installation. Research sources:
- https://support.nordvpn.com/hc/en-us/articles/19928244437777-Installing-and-using-NordVPN-on-Android-TV-or-Nvidia-Shield
- https://support.nordvpn.com/hc/en-us/articles/19618692366865-What-is-Split-Tunneling-and-how-to-use-it-with-NordVPN
- https://support.nordvpn.com/hc/en-us/articles/19509682644369-NordVPN-Kill-Switch-how-does-it-work

## Validation boundaries

Automated tests cover approved-set migration, room separation, reorder persistence and cancellation without removal, alongside the earlier preview policy tests. Lint and APK assembly must pass. Actual TCL screenshots and device observations are recorded separately during verification; a successful build does not validate household playback, Ring, Alexa, native Samsung, all remote keys, 60fps or VPN leak protection.

Media, Ring, reminder, profile and voice adapters from 0.2 remain experimental. Native Tizen work is not part of this APK. Stable release signing is still outstanding; the isolated deviceCheck package permits local checks without deleting the installed CI preview's configuration.
