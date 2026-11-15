"""preview/tools/vpn_check.py: the network-level Live TV protection check reads the right sockets and never
passes on appearance alone. Pure parsing only; nothing here talks to a TV.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import ipaddress
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("vpn_check", ROOT / "preview" / "tools" / "vpn_check.py")
vc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vc)

HEAD = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n"
# 10.5.0.2:40000 -> 203.0.113.9:443 ESTABLISHED (uid 10123), 192.168.1.42:40001 -> 8.8.8.8:443 outside the VPN (uid 10123),
# 0.0.0.0:8080 LISTEN (uid 10123), 192.168.1.42:40002 -> 192.168.1.10:8096 (uid 10123), another app (uid 10999).
TCP = HEAD + "\n".join([
    "   0: 0200050A:9C40 0971CBCB:01BB 01 00000000:00000000 00:00000000 00000000 10123        0 1 1 0",
    "   1: 2A01A8C0:9C41 08080808:01BB 01 00000000:00000000 00:00000000 00000000 10123        0 2 1 0",
    "   2: 00000000:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10123        0 3 1 0",
    "   3: 2A01A8C0:9C42 0A01A8C0:1FA0 01 00000000:00000000 00:00000000 00000000 10123        0 4 1 0",
    "   4: 2A01A8C0:9C43 0971CBCB:01BB 01 00000000:00000000 00:00000000 00000000 10999        0 5 1 0",
])
IP = "\n".join([
    "1: lo    inet 127.0.0.1/8 scope host lo",
    "3: wlan0    inet 192.168.1.42/24 brd 192.168.1.255 scope global wlan0",
    "9: tun0    inet 10.5.0.2/16 scope global tun0",
])


class Parsing(unittest.TestCase):
    def test_proc_net_addresses_are_little_endian_words(self):
        self.assertEqual(vc.hex_ip("0100007F"), ipaddress.ip_address("127.0.0.1"))
        self.assertEqual(vc.hex_ip("0000000000000000FFFF00002A01A8C0"), ipaddress.ip_address("192.168.1.42"))
        self.assertEqual(vc.hex_ip("B80D0120000000000000000001000000"), ipaddress.ip_address("2001:db8::1"))

    def test_only_the_live_tv_apps_sockets_are_read(self):
        socks = vc.sockets(TCP, 10123, "tcp")
        self.assertEqual(4, len(socks))
        self.assertTrue(all(s[0] == "tcp" for s in socks))

    def test_interfaces_split_vpn_from_home_network(self):
        i = vc.interfaces(IP)
        self.assertEqual({ipaddress.ip_address("10.5.0.2")}, i["vpn"])
        self.assertEqual({ipaddress.ip_address("192.168.1.42")}, i["lan"])

    def test_each_connection_is_classified_by_the_interface_it_uses(self):
        counts = vc.tally(vc.sockets(TCP, 10123, "tcp"), vc.interfaces(IP))
        self.assertEqual({"vpn": 1, "leak": 1, "local": 1, "idle": 1, "unknown": 0}, counts)

    def test_package_uid_and_version(self):
        self.assertEqual(10123, vc.package_uid("package:tv.example.live uid:10123\n", "tv.example.live"))
        self.assertIsNone(vc.package_uid("package:tv.other uid:10999\n", "tv.example.live"))
        self.assertEqual("9.15.2-tv", vc.version_name("  Packages:\n    versionCode=1 minSdk=21\n    versionName=9.15.2-tv\n"))


class Verdict(unittest.TestCase):
    GOOD = {"config": {"always_on": True, "lockdown": True}, "route": {"vpn": 20, "leak": 0, "unknown": 0},
            "drop": {"vpn": 0, "leak": 0, "unknown": 0, "recovered": True}}

    def with_(self, **parts):
        record = {k: dict(v) for k, v in self.GOOD.items()}
        for k, v in parts.items():
            record[k].update(v)
        return record

    def test_pass_needs_every_part(self):
        self.assertEqual("PASS", vc.verdict(self.GOOD))

    def test_vpn_appearance_alone_never_passes(self):
        self.assertTrue(vc.verdict(self.with_(config={"lockdown": False})).startswith("FAIL"))
        self.assertTrue(vc.verdict({"config": self.GOOD["config"], "route": self.GOOD["route"]}).startswith("INCOMPLETE"))

    def test_any_leak_fails(self):
        self.assertTrue(vc.verdict(self.with_(route={"leak": 1})).startswith("FAIL"))
        self.assertTrue(vc.verdict(self.with_(drop={"leak": 1})).startswith("FAIL"))
        self.assertTrue(vc.verdict(self.with_(drop={"unknown": 2})).startswith("FAIL"))

    def test_no_playback_or_no_recovery_is_incomplete_not_pass(self):
        self.assertTrue(vc.verdict(self.with_(route={"vpn": 0})).startswith("INCOMPLETE"))
        self.assertTrue(vc.verdict(self.with_(drop={"recovered": False})).startswith("INCOMPLETE"))


if __name__ == "__main__":
    unittest.main()
