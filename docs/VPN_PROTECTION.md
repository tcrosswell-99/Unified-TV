# Live TV and NordVPN

**Appearance is not protection.** The VPN states in the Home header (Connected, Disconnected, Checking, Unknown) only show what Android reports. They prove nothing about where Live TV's traffic goes, and they can't stop it ([AGENTS.md](../AGENTS.md) rule 8). This page says what Unified TV does about Live TV and NordVPN, what it can't do, and how to add Android's own block, the only thing that actually stops traffic, and prove it on a TV.

## What Unified TV does (OD-26)

**Live TV opens when NordVPN is connected.** The owner chose this on 27 October 2026, after build run 96 on the TCL never opened Live TV: under the earlier rule (SET-063, "automatic verification only") Live TV stayed locked on every Android TV, because an ordinary app can't check another app's routing. It replaces that rule. The owner was offered three choices: an Android always-on/lockdown check, opening when NordVPN is connected, or staying locked. He chose opening when NordVPN is connected.

### The rule

One rule (`LiveTvProtection`) decides for every route into Live TV: the dock tile, Apps, voice ("Open Live TV"), the Live TV sheet's **Try again** and the notice's **Watch Live TV**. All of them go through the one gate (`Launch.open`), so none is a way round it. Live TV opens only when **Android reports a VPN** and **that VPN is NordVPN's**:

1. NordVPN is installed; and
2. either **NordVPN's own notification says it is connected** (Unified TV reads only NordVPN's notification, only with the notification access the household allowed, and keeps only "connected or not" and the country code in memory, as for the flag beside the shield); or
3. without that word, **NordVPN is the only app on the TV that can run a VPN**: the only installed app declaring a service for `android.net.VpnService`. The VPN Android reports can then only be NordVPN's. An app that is turned off doesn't count. A system VPN service with no app to open (the TV's own plumbing, such as a data saver) isn't named as "another VPN app", but because it could be the VPN that is up, its presence means this route never opens Live TV: NordVPN's own "Connected" notice is needed (step 2), which on a TV without a notification-access screen is turned on through the TV's debugging prompt (below). Owner diagnostics lists the apps that count and the ones left out, with why.

| What Unified TV reads | Live TV | What the household sees |
| --- | --- | --- |
| VPN up, NordVPN's notice says connected | opens | "NordVPN is connected. If NordVPN drops, Unified TV will cover Live TV." |
| VPN up, NordVPN the only VPN app | opens | the same |
| VPN up, another VPN app installed, no notification access | locked | "A VPN is connected, but Unified TV can't confirm it's NordVPN because *<that app>* can also run a VPN. Allow notification access…". The sheet offers **Turn on access using the TV's debugging prompt** (when the TV has no notification-access screen, as the TCL) or **Allow access** (when it has), then **Try again** and **Cancel**: never a dead end. |
| VPN up, another VPN app, NordVPN's notice says nothing | locked | "A VPN is connected, but NordVPN doesn't say it's connected. Connect NordVPN first." |
| NordVPN's notice says disconnected, connecting or paused | locked | "Connect NordVPN first", with **Connect** (opens NordVPN at its main screen) |
| No VPN | locked | the same |
| NordVPN not installed | locked | "It opens when NordVPN is connected, and NordVPN isn't installed on this TV." |
| Android's answer can't be read | locked | "Unified TV couldn't read the TV's connection. Try again." |

**Kids** rules are unchanged: Live TV, and NordVPN itself, open in Kids only if an adult approved them.

### Connect, then carry on

NordVPN has no public "connect" request another app may send, so **Connect** still opens NordVPN. When it comes from a refused Live TV launch, Unified TV remembers that launch for two minutes and listens to Android's VPN network callback (and NordVPN's own notice). As soon as the rule lets Live TV open, Unified TV comes back by itself ("Display over other apps", as OD-23) and opens Live TV with a short "NordVPN connected — opening Live TV": no Back presses. If the household comes back first, the same happens then; if the rule still refuses, the sheet says why. If the screen that pressed Connect has closed in the meantime (Ask your TV closes itself when idle), Unified TV comes back to Home and opens Live TV from there; it never reopens an empty copy of the closed screen. The toast shows only once Live TV has actually opened (in Kids, Live TV's own refusal shows instead). Settings suggests: "Tip: turn on Auto-connect in NordVPN's settings so it's always connected."

### Notification access on a TV without the screen

The owner's TCL has no notification-access screen. Without access, NordVPN's own notice can't be read, so with another VPN app installed Live TV could never open, and no flag shows. **Turn on access using the TV's debugging prompt** (in the sheet, and in Settings > Live TV & Network Protection) opens a page that, after the adult PIN, uses the Optional clean-up's session link to this TV's own network debugging (the same "Allow USB debugging?" prompt) to send exactly one command, `cmd notification allow_listener <this app>/io.unifiedtv.preview.MediaWatch` (the allowlist refuses any other listener or app). It checks with Android that access is on, closes the link, then guides the owner to turn network debugging off and checks it is off. The same access makes Now Playing work for Amazon Music, because it is the same listener. From a computer instead:

```bash
adb shell cmd notification allow_listener io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.MediaWatch
```

(use `io.unifiedtv.launcher` or `io.unifiedtv.preview` for those apps). Settings shows the exact line for the app it is in.

### If NordVPN drops while Live TV is on (the safety net)

When Unified TV opened Live TV, a small watcher (`LiveTvGuard`, a foreground service with a quiet notification, as for "Open Unified TV when the TV turns on") listens to Android's own VPN network callback and to NordVPN's notification. It does not poll and holds no wakelock. When the VPN is lost, or NordVPN's notification says it isn't connected, it waits 1.5 seconds (a reconnect is not a drop) and asks the same rule again. If the rule no longer opens Live TV:

- **With "Display over other apps"** for Unified TV (the permission OD-23 uses), Unified TV comes to the front with a full-screen glass notice over Live TV: **"NordVPN disconnected — Live TV is covered. Reconnect NordVPN."**, with **Reconnect** (opens NordVPN) and **Back to Home**. Back also goes to Home, never back to Live TV uncovered. When NordVPN is connected again, the notice says so and offers **Watch Live TV**, through the same gate.
- **Without it**, Android doesn't let Unified TV come forward, so a high-priority notification says "NordVPN disconnected. If Live TV is still on, it isn't covered. Open Unified TV to reconnect NordVPN."

NordVPN's notification counts as "not connected" whenever it says so in any form ("Not connected", "Disconnected", "Unprotected", "No connection", "Connecting", "Paused"), checked before anything else; it counts as connected only for "Connected to <country>" or a line that is exactly "Connected" or "VPN connected". Any other wording is treated as unsure, never as connected.

It covers once per drop, and is ready again once NordVPN is back. The notice itself doesn't end the watch: if the notice is left some other way than **Back to Home** or **Reconnect** (the TV's own Home, say) while NordVPN is still down, the notification above is posted, and if Live TV comes back to the front (seen through the front-app signal below) it is covered again.

It stops when the Live TV session is over: a Unified TV screen other than the notice (Home included) comes to the front, the TV goes to standby, another app comes to the front where Unified TV can see that (only while the optional Kids app guard's accessibility service is on, which reads only the front app's package name), or after six hours. **Without that front-app signal** Unified TV can't tell that the owner has left Live TV for another app, so it covers the screen only in the first 15 minutes after opening Live TV; after that a drop is told in the notification instead, never by covering what may by then be another app. It never reads, consumes or reassigns a key.

### What it is not

**This isn't a kill switch.** If NordVPN drops, Android doesn't block Live TV's connection, and Unified TV can't: an ordinary app can't see or stop another app's traffic. In the second or two before the notice appears, and for as long as Live TV keeps streaming behind it (its sound may carry on until Live TV pauses itself), Live TV's traffic can leave outside the VPN. Settings > Live TV & Network Protection says so in those words. Nothing in Unified TV calls this protection, "verified" or a kill switch.

## Limits

- **Whose VPN.** The rule can't see which app owns the VPN. With notification access it relies on NordVPN's own wording ("Connected to …", "Disconnected", "Connecting", "Paused"); if NordVPN changes its notification, the word may not be read, and the rule then falls back to the only-VPN-app check. Without access, a VPN set up by something other than an app (Android's built-in VPN profiles, or a VPN profile another app hands to Android without its own VPN service) would look like NordVPN's when NordVPN is the only VPN app. Neither is set up on the owner's TVs.
- **Split tunnelling.** NordVPN connected does not prove Live TV's own traffic goes through it. If NordVPN is set to leave Live TV out, Live TV opens unprotected. Keep split tunnelling off, or Live TV not excluded.
- **Seeing Live TV leave.** Without the Kids app guard's accessibility service, Unified TV can't tell that Live TV has left the front for another app or the TV's own Home. A drop in the first 15 minutes may still show the notice over another app the owner switched to; after 15 minutes only the notification is used, so a drop while Live TV is still on is no longer covered.
- **Firmware.** Not tested on any TV yet (see [COMPATIBILITY.md](COMPATIBILITY.md)). Android TV may not show heads-up notifications, so without "Display over other apps" the notice may only appear in the TV's notification list.
- **Samsung (Tizen)** has no equivalent, and needs its own strategy.

## Adding Android's own block (optional, the only real protection)

Android itself can stop traffic outside the VPN: NordVPN as the TV's **always-on VPN** with **Block connections without VPN**. Android then refuses any app's internet traffic that isn't going through NordVPN, including while NordVPN disconnects, crashes, updates or is stopped. No launcher can do this. With it set up, the notice above is just an explanation; without it, the notice is all there is. Live TV opening does not depend on it (OD-26), and Unified TV can't read whether it is on (only a device or profile owner can).

The cost: the block covers the whole TV. While NordVPN is down, every app loses internet access, except Unified TV, which can be exempted (Android 10 or later) so it still reaches the NAS and Home Assistant. Live TV must **never** be exempt, and NordVPN must not be set to leave Live TV out.

### Setting it up on a TV

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

NordVPN's own kill switch depends on the NordVPN app's settings, which change with its updates; Android's always-on block works whatever the VPN app does.

### Proving it (network level)

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

Without the block, expect `drop` to **FAIL**: Unified TV's notice covers Live TV but does not stop its traffic. The acceptance checks that ask for no unprotected traffic on a drop (AT-05, AT-66, GATE-VPN-02 and GATE-VPN-04) need the block; OD-26 alone does not pass them.

There is no "Record a passed check" on the TV any more (removed with SET-063 on 25 October 2026): a pass proves Android's block on that TV, and changes nothing in what Unified TV opens. Record results in [FEATURE_STATUS.md](FEATURE_STATUS.md) with the build, date and verdict, and repeat the check after any NordVPN, Live TV or TV firmware update, or a change to the VPN settings. Until that has happened on a TV, it is **not tested**.
