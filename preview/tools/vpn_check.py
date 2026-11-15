#!/usr/bin/env python3
"""Network-level check that Live TV's traffic is protected by NordVPN, on the TV itself, over ADB.

Separate from the VPN *appearance* in the launcher: this reads the Live TV app's own sockets on the TV and
classifies each by the interface address it uses. A passing record is what lets an adult unlock Live TV in
Unified TV (Settings > Privacy & security > Live TV protection). See docs/VPN_PROTECTION.md.

Set TV_HOST (and TV_PORT if needed) as for tools/tv.py. Usage:
    vpn_check.py status   [--live PACKAGE]               settings, versions and Live TV's current sockets
    vpn_check.py route    [--live PACKAGE] [--seconds N] while Live TV plays: every connection must use the VPN
    vpn_check.py drop     [--live PACKAGE] [--seconds N] [--kill]
                                                         while Live TV plays: disconnect NordVPN, prove nothing
                                                         leaks, reconnect, prove it recovers; writes the record
    vpn_check.py lockdown --apply | --rollback BACKUP    always-on VPN + block connections without VPN
                                                         (asks first, saves a backup, reboot to take effect)

Records go to vpn-evidence/ (git-ignored) and hold no IP addresses, serial numbers or account details.
"""
import ipaddress
import json
import os
import struct
import sys
import time

NORDVPN = "com.nordvpn.android"
UNIFIED = "io.unifiedtv.preview"
VPN_IFACES = ("tun", "nordlynx", "wg", "ppp", "ipsec")
LAN_IFACES = ("wlan", "eth")
EVIDENCE = "vpn-evidence"


# --- pure parsing (unit-tested in tests/test_vpn_check.py)

def hex_ip(text):
    """/proc/net address field to an ipaddress object: IPv4 is one little-endian word, IPv6 four of them."""
    raw = bytes.fromhex(text)
    if len(raw) == 4:
        return ipaddress.IPv4Address(struct.pack("<I", struct.unpack(">I", raw)[0]))
    if len(raw) == 16:
        words = struct.unpack(">4I", raw)
        addr = ipaddress.IPv6Address(b"".join(struct.pack("<I", w) for w in words))
        return addr.ipv4_mapped or addr
    raise ValueError("bad address")


def sockets(proc_text, uid, proto):
    """Sockets owned by uid from /proc/net/{tcp,tcp6,udp,udp6} text: (proto, local, remote, state)."""
    out = []
    for line in proc_text.splitlines()[1:]:
        f = line.split()
        if len(f) < 8 or f[7] != str(uid):
            continue
        try:
            local, remote = hex_ip(f[1].split(":")[0]), hex_ip(f[2].split(":")[0])
        except ValueError:
            continue
        out.append((proto, local, remote, f[3]))
    return out


def interfaces(ip_text):
    """`ip -o addr` text to {'vpn': {addresses}, 'lan': {addresses}}."""
    found = {"vpn": set(), "lan": set()}
    for line in ip_text.splitlines():
        f = line.split()
        if len(f) < 4 or f[2] not in ("inet", "inet6"):
            continue
        name = f[1].split("@")[0]
        kind = "vpn" if name.startswith(VPN_IFACES) else "lan" if name.startswith(LAN_IFACES) else None
        if kind:
            found[kind].add(ipaddress.ip_address(f[3].split("/")[0]))
    return found


def classify(sock, ifaces):
    """vpn, leak, local (home-network peer or loopback), idle (listening/unconnected) or unknown."""
    proto, local, remote, state = sock
    if remote.is_unspecified or (proto.startswith("tcp") and state == "0A"):
        return "idle"
    if remote.is_loopback or local.is_loopback:
        return "local"
    if local in ifaces["vpn"]:
        return "vpn"
    if remote.is_private or remote.is_link_local or remote.is_multicast:
        return "local"
    if local in ifaces["lan"]:
        return "leak"
    return "unknown"


def tally(socks, ifaces):
    counts = {"vpn": 0, "leak": 0, "local": 0, "idle": 0, "unknown": 0}
    for s in socks:
        counts[classify(s, ifaces)] += 1
    return counts


def package_uid(list_text, package):
    """`cmd package list packages -U PACKAGE` output to the uid, or None."""
    for line in list_text.splitlines():
        f = dict(p.split(":", 1) for p in line.split() if ":" in p)
        if f.get("package") == package and f.get("uid", "").isdigit():
            return int(f["uid"])
    return None


def version_name(dumpsys_text):
    for line in dumpsys_text.splitlines():
        line = line.strip()
        if line.startswith("versionName="):
            return line.split("=", 1)[1]
    return ""


def verdict(record):
    """PASS only with lockdown on, Live TV seen on the VPN, no leak before/during/after the drop, and recovery."""
    route, drop = record.get("route", {}), record.get("drop", {})
    if not (record.get("config", {}).get("always_on") and record.get("config", {}).get("lockdown")):
        return "FAIL: always-on VPN with 'block connections without VPN' is not set"
    if route.get("vpn", 0) == 0:
        return "INCOMPLETE: no Live TV traffic was seen; start playback first"
    if route.get("leak", 0) or route.get("unknown", 0):
        return "FAIL: Live TV used a connection outside the VPN while NordVPN was on"
    if not drop:
        return "INCOMPLETE: run the drop test"
    if drop.get("leak", 0) or drop.get("unknown", 0):
        return "FAIL: Live TV reached the internet without the VPN after NordVPN disconnected"
    if not drop.get("recovered"):
        return "INCOMPLETE: Live TV did not return to the VPN after reconnecting"
    return "PASS"


# --- TV access

def tv():
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools"))
    import tv as tvmod  # noqa: E402  (the inherited ADB client; TV_HOST/TV_PORT)
    return tvmod.connect()


def sh(dev, cmd):
    return dev.shell(cmd, read_timeout_s=60.0)


def live_socks(dev, uid):
    socks = []
    for proto in ("tcp", "tcp6", "udp", "udp6"):
        socks += sockets(sh(dev, f"cat /proc/net/{proto}"), uid, proto)
    return socks


def config(dev):
    get = lambda k: sh(dev, f"settings get secure {k}").strip()
    allow = get("always_on_vpn_lockdown_whitelist")
    return {"always_on": get("always_on_vpn_app") == NORDVPN, "lockdown": get("always_on_vpn_lockdown") == "1",
            "allowlist": [] if allow in ("", "null") else allow.split(",")}


def sample(dev, uid, seconds):
    total = {"vpn": 0, "leak": 0, "local": 0, "idle": 0, "unknown": 0, "samples": 0}
    end = time.time() + seconds
    while time.time() < end:
        counts = tally(live_socks(dev, uid), interfaces(sh(dev, "ip -o addr")))
        for k, v in counts.items():
            total[k] += v
        total["samples"] += 1
        if counts["leak"] or counts["unknown"]:
            print(f"  ! {counts['leak']} connection(s) outside the VPN")
        time.sleep(1)
    return total


def header(dev, live):
    uid = package_uid(sh(dev, f"cmd package list packages -U {live}"), live)
    if uid is None:
        raise SystemExit(f"{live} is not installed; pass --live with the Live TV app's package name")
    return uid, {"time": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                 "tv": {"model": sh(dev, "getprop ro.product.model").strip(), "android": sh(dev, "getprop ro.build.version.release").strip()},
                 "nordvpn": version_name(sh(dev, f"dumpsys package {NORDVPN}")),
                 "live": {"package": live, "version": version_name(sh(dev, f"dumpsys package {live}"))},
                 "config": config(dev)}


def save(record):
    os.makedirs(EVIDENCE, exist_ok=True)
    path = os.path.join(EVIDENCE, "vpn-check-" + time.strftime("%Y%m%d-%H%M%S") + ".json")
    with open(path, "w") as f:
        json.dump(record, f, indent=2, default=str)
    return path


def lockdown_apply(dev):
    keys = ("always_on_vpn_app", "always_on_vpn_lockdown", "always_on_vpn_lockdown_whitelist")
    before = {k: sh(dev, f"settings get secure {k}").strip() for k in keys}
    print("Current:", json.dumps(before))
    print(f"This sets NordVPN as the always-on VPN, blocks connections without it, and lets only {UNIFIED} reach the home")
    print("network when the VPN is down. Live TV is not exempted. A reboot is needed for Android to apply it.")
    if input("Type yes to change these settings: ").strip() != "yes":
        raise SystemExit("Nothing changed.")
    os.makedirs(EVIDENCE, exist_ok=True)
    backup = os.path.join(EVIDENCE, "lockdown-backup-" + time.strftime("%Y%m%d-%H%M%S") + ".json")
    with open(backup, "w") as f:
        json.dump(before, f)
    for k, v in (("always_on_vpn_app", NORDVPN), ("always_on_vpn_lockdown", "1"), ("always_on_vpn_lockdown_whitelist", UNIFIED)):
        sh(dev, f"settings put secure {k} {v}")
    print(f"Saved the previous values to {backup}. Reboot the TV, then run: vpn_check.py status")


def lockdown_rollback(dev, backup):
    with open(backup) as f:
        before = json.load(f)
    for k, v in before.items():
        if k not in ("always_on_vpn_app", "always_on_vpn_lockdown", "always_on_vpn_lockdown_whitelist"):
            continue
        sh(dev, f"settings delete secure {k}" if v in ("", "null") else f"settings put secure {k} {v}")
    print("Restored the previous values. Reboot the TV for Android to apply them.")


def main(argv):
    if not argv or argv[0] not in ("status", "route", "drop", "lockdown"):
        print(__doc__)
        return 2
    opts = {"--live": "", "--seconds": "20"}
    for i, a in enumerate(argv):
        if a in opts and i + 1 < len(argv):
            opts[a] = argv[i + 1]
    dev = tv()
    try:
        if argv[0] == "lockdown":
            if "--apply" in argv:
                lockdown_apply(dev)
            elif "--rollback" in argv:
                lockdown_rollback(dev, argv[argv.index("--rollback") + 1])
            else:
                print(__doc__)
                return 2
            return 0
        live = opts["--live"] or os.environ.get("LIVE_TV_PACKAGE", "")
        if not live:
            raise SystemExit("Pass --live with the Live TV app's package name (or set LIVE_TV_PACKAGE).")
        uid, record = header(dev, live)
        seconds = max(5, int(opts["--seconds"]))
        print(f"Always-on NordVPN: {record['config']['always_on']}; block without VPN: {record['config']['lockdown']}")
        if argv[0] == "status":
            print(json.dumps(tally(live_socks(dev, uid), interfaces(sh(dev, "ip -o addr")))))
            return 0
        print(f"Checking Live TV's connections for {seconds}s. Keep a channel playing.")
        record["route"] = sample(dev, uid, seconds)
        if argv[0] == "drop":
            if "--kill" in argv:
                sh(dev, f"am force-stop {NORDVPN}")
                print("NordVPN stopped. Android should block traffic until it restarts.")
            else:
                input("Now disconnect NordVPN with its own Disconnect button, then press Enter...")
            drop = sample(dev, uid, seconds)
            if "--kill" not in argv:
                input("Now reconnect NordVPN, then press Enter...")
            drop["recovered"] = False
            end = time.time() + 60
            while time.time() < end and not drop["recovered"]:
                counts = tally(live_socks(dev, uid), interfaces(sh(dev, "ip -o addr")))
                drop["recovered"] = counts["vpn"] > 0 and counts["leak"] == 0
                time.sleep(2)
            record["drop"] = drop
        record["verdict"] = verdict(record)
        print(record["verdict"])
        print("Record:", save(record))
        return 0 if record["verdict"] == "PASS" else 1
    finally:
        dev.close()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
