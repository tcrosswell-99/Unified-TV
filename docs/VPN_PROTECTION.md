# Live TV protection

**Appearance is not protection.** The VPN states in the Home header (Connected, Disconnected, Checking, Unknown) only show what Android reports. They prove nothing about where Live TV's traffic goes, and they can't stop it. This page is the single protection strategy, and how to prove it works on a TV, network check included ([AGENTS.md](../AGENTS.md) rule 8).

Live TV stays locked until this check has passed on that TV and been recorded.

## The strategy

1. **Android's own block does the protecting.** NordVPN is set as the TV's *always-on VPN*, with **Block connections without VPN** turned on. Android then refuses any app's internet traffic that isn't going through NordVPN. If NordVPN disconnects, crashes, updates or is stopped (including during playback), Android blocks the traffic before it leaves the TV. No launcher can do this; only Android can.
2. **Only Unified TV is exempt, and only so it can reach the home network.** It keeps finding the NAS and Home Assistant while the VPN is down. Live TV (ReamSmartz) is **never** exempt. NordVPN must not be set to leave Live TV out.
3. **Proven at network level on each TV.** `preview/tools/vpn_check.py` reads the Live TV app's own connections on the TV and checks which network each one uses. Results are below.
4. **Unified TV opens Live TV only while the proven setup is unchanged.** An adult records the passed check on the TV. At every launch, Unified TV confirms:
   - NordVPN is connected, and Unified TV's own traffic is going through it. Under the proven block that is true for every app, so if it isn't, the setup has changed.
   - NordVPN and the Live TV app are the same versions that were checked.
   - It is the same Live TV app that was checked.

   If anything differs, Live TV locks again and says why, in plain words.
5. **If NordVPN drops during playback,** Android has already stopped Live TV's traffic (step 1). Unified TV then brings the viewer back to Home with "Live TV paused. NordVPN disconnected…". That message is for the viewer's benefit; the protection is step 1.

### Why this and not something in the app

- A launcher can't see or block another app's traffic. Watching the VPN status and reacting always leaves a gap, and in that gap traffic could leave unprotected. Android's block has no gap.
- NordVPN's own kill switch depends on the NordVPN app's settings, which change with its updates. Android's always-on block works whatever the VPN app does.
- Unified TV's own checks can only catch a setup that has *changed* since it was proven. They are not the protection.

## Setting it up on a TV

The TCL (Android TV 11) has no VPN settings screen, so the setting is applied over ADB. It changes system settings, so it:

- asks first and changes nothing without "yes";
- saves the previous values;
- can be undone with one command.

```bash
export TV_HOST=<the TV's address>             # as for tools/tv.py; never commit it
python3 preview/tools/vpn_check.py lockdown --apply
# reboot the TV so Android applies it, then:
python3 preview/tools/vpn_check.py status --live <Live TV package>
```

To undo it, run `vpn_check.py lockdown --rollback vpn-evidence/lockdown-backup-<time>.json`, then reboot.

On TVs that have the setting in their menus, the same result is: **Settings → Network → VPN → NordVPN → Always-on VPN** and **Block connections without VPN**.

In NordVPN itself:

- turn split tunnelling off, or make sure Live TV is not excluded;
- if NordVPN offers "allow local network" or "LAN discovery", turn it on so the NAS and Home Assistant stay reachable through the VPN.

## Proving it (network level)

Run these from a computer on the same network, with the TV's Live TV app playing a channel:

```bash
python3 preview/tools/vpn_check.py route --live <Live TV package> --seconds 30
python3 preview/tools/vpn_check.py drop  --live <Live TV package> --seconds 30
python3 preview/tools/vpn_check.py drop  --live <Live TV package> --seconds 30 --kill
```

**How it judges each connection.** It reads every connection the Live TV app owns on the TV (`/proc/net/tcp`, `tcp6`, `udp` and `udp6`, by the app's user id), and the TV's network addresses (`ip -o addr`). Each connection is classed as:

| Class | Meaning |
| --- | --- |
| **vpn** | Leaves through the VPN interface (`tun`, `nordlynx`, `wg`, …). |
| **leak** | Leaves through Wi-Fi or Ethernet to the internet, outside the VPN. |
| local | Stays on the home network or the TV itself. |
| idle | Listening or not connected. |
| unknown | Can't be placed. Treated like a leak. |

**What each step does.**

- **`route`:** while NordVPN is on, every internet connection must be **vpn**.
- **`drop`:** you disconnect NordVPN with its own button, or `--kill` force-stops it. For the whole window, Live TV must have **no leak and no unknown** connection. You then reconnect, and Live TV must return to the VPN within a minute.

**The verdict.**

- **PASS** needs all of the following:
  - always-on and the block both set;
  - Live TV traffic seen on the VPN;
  - no leak before, during or after the drop;
  - recovery after reconnecting.
- **FAIL:** any of those is missing, or anything leaks.
- **INCOMPLETE:** the run couldn't prove it either way, for example nothing was playing.

Each run writes a record to `vpn-evidence/`, which is git-ignored. Records hold the TV model, Android version, both app versions, the settings, the counts and the verdict. They never hold IP addresses, serial numbers or account details.

**What it can't see.** A connection the app tries but Android blocks may never appear as a socket. The check proves that nothing *got out* outside the VPN, which is the property that matters.

For an independent second view, watch the TV's traffic at the router during the drop. With the block on, only NordVPN's own reconnection attempts should leave the TV.

## Recording it on the TV

Once the check passes, an adult opens **Settings → Privacy & security → Live TV protection** and chooses **Record a passed check**.

- **What it captures:** the Live TV app and the NordVPN and Live TV versions, automatically. Nothing is typed.
- **Locking again:** **Lock Live TV** forgets the record.
- **When to repeat the check:**
  - after any NordVPN or Live TV update (Unified TV locks Live TV automatically when either changes);
  - after a TV firmware update;
  - after changing the VPN settings.

Record results in [FEATURE_STATUS.md](FEATURE_STATUS.md) with the build, date and verdict. Until that has happened on a TV, this is **not tested**.

## Limits

- **The block covers the whole TV, not just Live TV.** While NordVPN is down, every app except Unified TV loses internet access. That is the price of no gaps. Unified TV says so plainly when it happens.
- **Live TV opens only from the Home dock.** Voice and matchday shortcuts to Live TV stay locked. They can be opened later through the same decision.
- **Firmware.** Android 8 and 9 TVs behave the same in principle, but the exemption for Unified TV needs Android 10 or later. Each TV and firmware must pass its own check; one TV's pass says nothing about another.
- **Samsung (Tizen)** has no equivalent to this, and needs its own strategy.
