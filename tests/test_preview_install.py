"""preview/tools/install_preview.py: the safe preview install (spec P05-01) reads the TV's state correctly and
refuses anything that could lose a configured preview's data. Pure functions only; nothing here talks to a TV
or the network.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import hashlib
import importlib.util
import io
import json
import re
import struct
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("install_preview", ROOT / "preview" / "tools" / "install_preview.py")
ip = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ip)

CERT = bytes(range(256)) * 3 + b"\x30\x82 fake certificate DER"
OTHER_CERT = b"\x30\x82 another key"

DUMPSYS = """Activity Resolver Table:
  Non-Data Actions:
      android.intent.action.MAIN:
        1a2b3c io.unifiedtv.preview/.UnifiedPreviewActivity filter 4d5e6f

Packages:
  Package [io.unifiedtv.preview] (8c1d2e3):
    userId=10123
    pkg=Package{5f6a7b io.unifiedtv.preview}
    codePath=/data/app/~~abc==/io.unifiedtv.preview-xyz==
    versionCode=5 minSdk=28 targetSdk=33
    versionName=0.5-preview
    signatures=PackageSignatures{9d2a5bf version:2, signatures:[%s], past signatures:[]}
    timeStamp=2026-10-20 10:11:12
    firstInstallTime=2026-10-01 09:00:00
    lastUpdateTime=2026-10-20 10:11:12
    User 0: ceDataInode=12345 installed=true hidden=false

Queries:
  system apps queryable: false
""" % ip.java_array_hash(CERT)

NOT_INSTALLED = """Activity Resolver Table:

Dexopt state:
  Unable to find package: io.unifiedtv.preview
"""

HOME = """priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.google.android.tvlauncher/.MainActivity
"""

APKSIGNER = """Signer #1 certificate DN: CN=Android Debug, O=Android, C=US
Signer #1 certificate SHA-256 digest: 0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9
Signer #1 certificate SHA-1 digest: 0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d
"""


# --- builders for synthetic APK pieces

def lp(data):
    return struct.pack("<I", len(data)) + data


def signed_apk(cert, block_id=0x7109871A):
    """A ZIP with an APK signature block holding one signer with one certificate, inserted before the
    central directory with the end-of-central-directory offset moved to match."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("AndroidManifest.xml", b"placeholder")
        zf.writestr("classes.dex", b"dex\n035\0")
    data = buf.getvalue()
    eocd = data.rfind(b"PK\x05\x06")
    (cd,) = struct.unpack_from("<I", data, eocd + 16)
    signed_data = lp(lp(lp(struct.pack("<I", 0x0103) + lp(b"digest")))) + lp(lp(cert)) + lp(b"")
    signer = lp(signed_data) + lp(b"sigs") + lp(b"public key")
    value = lp(lp(signer))
    pair = struct.pack("<Q", len(value) + 4) + struct.pack("<I", block_id) + value
    size = len(pair) + 8 + 16
    block = struct.pack("<Q", size) + pair + struct.pack("<Q", size) + ip.APK_SIG_BLOCK_MAGIC
    out = bytearray(data[:cd] + block + data[cd:])
    struct.pack_into("<I", out, eocd + len(block) + 16, cd + len(block))
    return bytes(out)


def axml(package, version_code, version_name):
    """A minimal compiled AndroidManifest.xml: UTF-16 string pool, resource map, <manifest> start element."""
    strings = ["versionCode", "versionName", "package", "manifest", "http://schemas.android.com/apk/res/android",
               package, version_name]
    body = b""
    offsets = []
    for s in strings:
        offsets.append(len(body))
        body += struct.pack("<H", len(s)) + s.encode("utf-16-le") + b"\0\0"
    while len(body) % 4:
        body += b"\0"
    header = 28
    strings_start = header + 4 * len(strings)
    pool = struct.pack("<HHIIIIII", 0x0001, header, strings_start + len(body), len(strings), 0, 0,
                       strings_start, 0) + struct.pack(f"<{len(strings)}I", *offsets) + body
    resmap_ids = [0x0101021B, 0x0101021C]
    resmap = struct.pack("<HHI", 0x0180, 8, 8 + 4 * len(resmap_ids)) + struct.pack("<2I", *resmap_ids)
    ns = 4
    attrs = [
        struct.pack("<IIIHBBI", ns, 0, 0xFFFFFFFF, 8, 0, 0x10, version_code),
        struct.pack("<IIIHBBI", ns, 1, 6, 8, 0, 0x03, 6),
        struct.pack("<IIIHBBI", 0xFFFFFFFF, 2, 5, 8, 0, 0x03, 5),
    ]
    ext = struct.pack("<IIHHHHHH", 0xFFFFFFFF, 3, 20, 20, len(attrs), 0, 0, 0) + b"".join(attrs)
    start = struct.pack("<HHIII", 0x0102, 16, 16 + len(ext), 1, 0xFFFFFFFF) + ext
    chunks = pool + resmap + start
    return struct.pack("<HHI", 0x0003, 8, 8 + len(chunks)) + chunks


class Parsing(unittest.TestCase):
    def test_dumpsys_versions_signature_and_install_time(self):
        info = ip.parse_dumpsys(DUMPSYS, "io.unifiedtv.preview")
        self.assertEqual(info["version_code"], 5)
        self.assertEqual(info["version_name"], "0.5-preview")
        self.assertEqual(info["signature_hashes"], [ip.java_array_hash(CERT)])
        self.assertEqual(info["scheme"], 2)
        self.assertEqual(info["first_install"], "2026-10-01 09:00:00")

    def test_dumpsys_without_the_package_means_not_installed(self):
        self.assertIsNone(ip.parse_dumpsys(NOT_INSTALLED, "io.unifiedtv.preview"))

    def test_dumpsys_of_the_preview_is_not_read_as_the_devicecheck_variant(self):
        self.assertIsNone(ip.parse_dumpsys(DUMPSYS, "io.unifiedtv.preview.devicecheck"))

    def test_java_array_hash_matches_java(self):
        # Arrays.hashCode(new byte[0]) == 1; Arrays.hashCode(new byte[]{1,2,3}) == 30817;
        # Arrays.hashCode(new byte[]{-1}) == 30 (31 + -1), printed by Integer.toHexString.
        self.assertEqual(ip.java_array_hash(b""), "1")
        self.assertEqual(ip.java_array_hash(bytes([1, 2, 3])), format(30817, "x"))
        self.assertEqual(ip.java_array_hash(bytes([255])), "1e")
        self.assertRegex(ip.java_array_hash(CERT), r"^[0-9a-f]{1,8}$")

    def test_factory_home_component(self):
        self.assertEqual(ip.parse_resolve(HOME), "com.google.android.tvlauncher/.MainActivity")
        self.assertEqual(ip.parse_resolve("No activity found\n"), "")
        self.assertEqual(ip.parse_resolve(""), "")

    def test_pm_path_prefers_base_apk(self):
        text = "package:/data/app/x/split_config.xhdpi.apk\npackage:/data/app/x/base.apk\n"
        self.assertEqual(ip.parse_pm_path(text), "/data/app/x/base.apk")
        self.assertEqual(ip.parse_pm_path(""), "")

    def test_apksigner_digest(self):
        self.assertEqual(ip.parse_apksigner(APKSIGNER), "0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9")
        self.assertEqual(ip.parse_apksigner("DOES NOT VERIFY"), "")


class ApkReading(unittest.TestCase):
    def test_v2_signature_block_certificate(self):
        self.assertEqual(ip.signing_block_certificate(signed_apk(CERT)), CERT)

    def test_v3_block_is_read_too(self):
        self.assertEqual(ip.signing_block_certificate(signed_apk(OTHER_CERT, 0xF05368C0)), OTHER_CERT)

    def test_unsigned_zip_has_no_signature_block(self):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as zf:
            zf.writestr("a", b"b")
        self.assertIsNone(ip.signing_block_certificate(buf.getvalue()))
        self.assertIsNone(ip.signing_block_certificate(b"not a zip"))

    def test_apk_certificate_digest_from_file(self):
        import tempfile
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "x.apk"
            path.write_bytes(signed_apk(CERT))
            original = ip.find_build_tool
            ip.find_build_tool = lambda name: ""   # no apksigner: the block parser alone
            try:
                digest, der, method = ip.apk_certificate(str(path))
            finally:
                ip.find_build_tool = original
        self.assertEqual(digest, hashlib.sha256(CERT).hexdigest())
        self.assertEqual(der, CERT)
        self.assertEqual(method, "signature block")

    def test_binary_manifest_identity(self):
        ident = ip.manifest_identity(axml("io.unifiedtv.preview", 5, "0.5-preview"))
        self.assertEqual(ident, {"package": "io.unifiedtv.preview", "version_code": 5, "version_name": "0.5-preview"})


class Decision(unittest.TestCase):
    APK = {"version_code": 5}

    def test_not_installed_installs(self):
        self.assertEqual(ip.decide(None, self.APK)[0], "install")

    def test_same_certificate_newer_installs(self):
        self.assertEqual(ip.decide({"version_code": 4, "cert_match": True}, self.APK)[0], "install")

    def test_same_certificate_same_version_installs(self):
        self.assertEqual(ip.decide({"version_code": 5, "cert_match": True}, self.APK)[0], "install")

    def test_different_certificate_refuses_and_explains(self):
        action, why = ip.decide({"version_code": 4, "cert_match": False}, self.APK)
        self.assertEqual(action, "refuse")
        self.assertIn("uninstall", why)
        self.assertIn("never", why)

    def test_different_certificate_refuses_even_with_allow_downgrade(self):
        self.assertEqual(ip.decide({"version_code": 9, "cert_match": False}, self.APK, True)[0], "refuse")

    def test_unreadable_certificate_refuses(self):
        self.assertEqual(ip.decide({"version_code": 4, "cert_match": None}, self.APK)[0], "refuse")

    def test_downgrade_refuses_unless_allowed(self):
        installed = {"version_code": 6, "cert_match": True}
        self.assertEqual(ip.decide(installed, self.APK)[0], "refuse")
        self.assertEqual(ip.decide(installed, self.APK, allow_downgrade=True)[0], "install")

    def test_unknown_versions_refuse(self):
        self.assertEqual(ip.decide({"version_code": None, "cert_match": True}, self.APK)[0], "refuse")

    def test_install_command_never_uninstalls(self):
        self.assertEqual(ip.install_command(False), "pm install -r " + ip.REMOTE_APK)
        self.assertEqual(ip.install_command(True), "pm install -r -d " + ip.REMOTE_APK)

    def test_forbidden_commands_are_refused_before_sending(self):
        for cmd in ("pm uninstall io.unifiedtv.preview", "pm uninstall -k io.unifiedtv.preview",
                    "pm clear io.unifiedtv.preview", "cmd package uninstall io.unifiedtv.preview",
                    "pm disable-user --user 0 com.google.android.tvlauncher"):
            with self.assertRaises(RuntimeError, msg=cmd):
                ip.check_command(cmd)
        for cmd in ("dumpsys package io.unifiedtv.preview", ip.HOME_QUERY, "pm path io.unifiedtv.preview",
                    "sha256sum " + ip.REMOTE_APK, "rm -f " + ip.REMOTE_APK):
            self.assertEqual(ip.check_command(cmd), cmd)

    def test_source_never_names_an_uninstall_or_clear_command(self):
        source = (ROOT / "preview" / "tools" / "install_preview.py").read_text(encoding="utf-8")
        self.assertNotRegex(source, r"[\"']pm (uninstall|clear)")


class Evidence(unittest.TestCase):
    def record(self, home_after="com.google.android.tvlauncher/.MainActivity"):
        apk = {"path": "/home/someone/builds/UnifiedTV-Preview.apk", "sha256": "ab" * 32,
               "package": "io.unifiedtv.preview", "version_code": 5, "version_name": "0.5-preview",
               "cert_sha256": "cd" * 32}
        before = {"version_code": 4, "version_name": "0.4-preview", "cert_sha256": "cd" * 32,
                  "first_install": "2026-10-01 09:00:00"}
        after = dict(before, version_code=5, version_name="0.5-preview")
        return ip.evidence(apk, before, after, ("install", "update"), "installed",
                           "com.google.android.tvlauncher/.MainActivity", home_after,
                           {"model": "Smart TV", "android": "11", "build": "R.1"}, "installed APK certificate SHA-256", True)

    def test_record_holds_hashes_versions_and_outcomes(self):
        rec = self.record()
        self.assertEqual(rec["apk"]["sha256"], "ab" * 32)
        self.assertEqual(rec["apk"]["cert_sha256_prefix"], "cd" * 8)
        self.assertEqual(rec["apk"]["file"], "UnifiedTV-Preview.apk")
        self.assertEqual(rec["factory_home_unchanged"], "yes")
        self.assertTrue(rec["data_retained"])
        self.assertEqual(rec["before"]["version_code"], 4)
        self.assertEqual(rec["after"]["version_code"], 5)

    def test_changed_home_is_recorded_as_no(self):
        self.assertEqual(self.record(home_after="io.unifiedtv.preview/.Home")["factory_home_unchanged"], "no")

    def test_record_has_no_addresses_serials_or_local_paths(self):
        text = json.dumps(self.record())
        self.assertNotRegex(text, r"\b\d{1,3}(\.\d{1,3}){3}\b")
        self.assertNotIn("/home/someone", text)
        self.assertNotRegex(text, re.compile(r"serial", re.I))


class LauncherFirstInstallTest(unittest.TestCase):
    """v3.4 module 08: the household launcher is never first installed with a throwaway debug key."""
    DEBUG = b"0\x82...CN=Android Debug,O=Android,C=US..."
    DURABLE = b"0\x82...CN=Unified TV Preview..."

    def test_debug_signed_launcher_first_install_is_refused(self):
        self.assertIn("TEST-SIGNED", ip.first_install_guard("io.unifiedtv.launcher", None, self.DEBUG))

    def test_unreadable_certificate_refuses_the_first_launcher_install(self):
        self.assertIsNotNone(ip.first_install_guard("io.unifiedtv.launcher", None, None))

    def test_durable_launcher_installs(self):
        self.assertIsNone(ip.first_install_guard("io.unifiedtv.launcher", None, self.DURABLE))

    def test_preview_and_review_build_may_be_test_signed(self):
        for package in ("io.unifiedtv.preview", "io.unifiedtv.launcher.devicecheck"):
            self.assertIsNone(ip.first_install_guard(package, None, self.DEBUG), package)

    def test_updates_are_left_to_the_certificate_match_rule(self):
        self.assertIsNone(ip.first_install_guard("io.unifiedtv.launcher", {"version_code": 1, "cert_match": True}, self.DEBUG))

    def test_test_key_flag_overrides(self):
        self.assertIsNone(ip.first_install_guard("io.unifiedtv.launcher", None, self.DEBUG, test_key=True))


if __name__ == "__main__":
    unittest.main()
