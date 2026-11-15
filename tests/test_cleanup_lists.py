"""The Optional clean-up's bundled lists stay what examples/*/debloat.txt say (preview/tools/cleanup_lists.py).

The Android app offers only these packages, and only on the TCL BeyondTV4 family; the owner's TV (fingerprint
TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR10) matches the 50C725K list exactly.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
from pathlib import Path
import re
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "preview/app/src/main/assets/cleanup/tcl-beyondtv4.json"
sys.path.insert(0, str(ROOT / "tools"))
import recovery  # noqa: E402


def uncommented(path):
    return set(recovery.read_list(path))


class CleanupListsTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(ASSET.read_text(encoding="utf-8"))

    def test_asset_is_generated_from_the_lists(self):
        result = subprocess.run([sys.executable, "-I", str(ROOT / "preview/tools/cleanup_lists.py"), "--check"],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_only_the_tcl_beyondtv4_family_on_android_11(self):
        self.assertEqual(self.data["family"], {"brand": "TCL", "device": "BeyondTV4", "release": "11",
                                               "build": "RP1A.200622.001"})
        self.assertEqual([p["id"] for p in self.data["profiles"]], ["tcl-50c725k", "tcl-55c725", "tcl-65c725"])
        owner = self.data["profiles"][0]
        self.assertIn("TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR10", owner["fingerprints"])
        self.assertEqual(owner["stockLauncher"], "com.google.android.tvlauncher")

    def test_every_package_is_an_uncommented_line_of_its_list(self):
        for profile in self.data["profiles"]:
            listed = uncommented(ROOT / profile["source"])
            names = [p["name"] for p in profile["packages"]]
            self.assertEqual(set(names), listed, profile["id"])
            self.assertEqual(len(names), len(set(names)), profile["id"])
            for p in profile["packages"]:
                self.assertTrue(recovery.PACKAGE_RE.fullmatch(p["name"]), p["name"])
                self.assertTrue(p["group"], p["name"])

    def test_keep_notes_are_never_offered_by_the_same_list(self):
        for profile in self.data["profiles"]:
            names = {p["name"] for p in profile["packages"]}
            self.assertFalse(names & set(profile["keep"]), profile["id"])
        owner = self.data["profiles"][0]
        for kept in ("com.tcl.tv", "com.tcl.tvinput", "com.tcl.settings", "com.tcl.versionUpdateApp",
                     "com.tcl.providers.config", "com.google.android.tungsten.setupwraith", "com.tcl.inputmethod.international"):
            self.assertIn(kept, owner["keep"])

    def test_no_core_android_package_is_bundled(self):
        for profile in self.data["profiles"]:
            self.assertEqual(recovery.core_packages([p["name"] for p in profile["packages"]]), [], profile["id"])

    def test_java_core_patterns_match_recovery_py(self):
        source = (ROOT / "preview/app/src/io/unifiedtv/preview/CleanupPolicy.java").read_text(encoding="utf-8")

        def java(name):
            found = re.search(name + r' = Pattern\.compile\("((?:[^"\\]|\\.)*)"\)', source)
            self.assertIsNotNone(found, name)
            return found.group(1).encode().decode("unicode_escape")

        self.assertEqual(java("CORE"), recovery.CORE_RE.pattern)
        self.assertEqual(java("CORE_OK"), recovery.CORE_OK.pattern)


if __name__ == "__main__":
    unittest.main()
