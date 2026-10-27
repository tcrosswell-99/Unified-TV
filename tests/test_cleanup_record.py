"""A recovery record written by the Android app's Optional clean-up replays with tools/recovery.py (restore.sh).

preview/app/test/fixtures/cleanup-record.json is byte for byte what the app writes for a known TV state (CleanupTest
checks that on the Java side). Here recovery.py reads it, preview/tools/cleanup_restore.py files it where restore.sh
looks, and restore's plan re-enables exactly what the clean-up turned off, on that TV only.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "preview/app/test/fixtures/cleanup-record.json"
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "preview/tools"))
import recovery  # noqa: E402
import cleanup_restore  # noqa: E402


class CleanupRecordTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.home = os.environ.get("HOME")
        os.environ["HOME"] = str(self.tmp / "home")
        self.downloads = self.tmp / "downloads"
        self.downloads.mkdir()
        self.record = self.downloads / "unifiedtv-cleanup-record.json"
        shutil.copy(FIXTURE, self.record)
        self.device = json.loads(FIXTURE.read_text())["device"]
        self.current_device = recovery.current_device

    def tearDown(self):
        recovery.current_device = self.current_device
        if self.home is None:
            os.environ.pop("HOME", None)
        else:
            os.environ["HOME"] = self.home
        shutil.rmtree(self.tmp)

    def test_recovery_py_reads_the_apps_record(self):
        record = recovery.load_record(FIXTURE)
        self.assertEqual(record["packages"], {"com.tcl.bi": "enabled", "com.tcl.esticker": "enabled"})
        self.assertTrue(recovery.has_device(record))
        self.assertEqual(record["source"], "Unified TV Optional clean-up")

    def test_same_text_recovery_py_would_write(self):
        text = FIXTURE.read_text(encoding="utf-8")
        self.assertEqual(json.dumps(json.loads(text), indent=2, sort_keys=True) + "\n", text)

    def test_restore_turns_back_on_only_what_the_cleanup_turned_off(self):
        list_path, filed, command = cleanup_restore.prepare(self.record, "tv.example:5555")
        self.assertTrue(filed.is_relative_to(self.tmp / "home" / ".cleanyourtv"))
        self.assertEqual(recovery.read_list(list_path), ["com.tcl.bi", "com.tcl.esticker"])
        self.assertIn("tools/restore.sh " + str(list_path), command)
        recovery.current_device = lambda: dict(self.device)
        commands = recovery.restore_commands(list_path, "com.google.android.tvlauncher", "tv.example:5555")
        self.assertIn("pm enable com.tcl.bi", commands)
        self.assertIn("pm enable com.tcl.esticker", commands)
        self.assertIn("cmd package set-home-activity --user 0 "
                      "com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity", commands)
        self.assertIn("settings delete global animator_duration_scale", commands)
        self.assertFalse([c for c in commands if "disable" in c or "uninstall" in c or "pm hide" in c])

    def test_restore_refuses_another_tv(self):
        list_path, _, _ = cleanup_restore.prepare(self.record, "tv.example")
        recovery.current_device = lambda: {"serial": "OTHERSERIAL9", "android_id": "ffffeeeeddddcccc"}
        with self.assertRaises(SystemExit):
            recovery.restore_commands(list_path, "com.google.android.tvlauncher", "tv.example")

    def test_the_phones_list_must_match_the_record(self):
        (self.downloads / "unifiedtv-cleanup.txt").write_text("com.tcl.bi\n")
        with self.assertRaises(SystemExit):
            cleanup_restore.prepare(self.record, "tv.example")

    def test_a_damaged_record_is_refused(self):
        self.record.write_text(FIXTURE.read_text().replace("com.tcl.bi", "com.tcl.bi;reboot"))
        with self.assertRaises(SystemExit):
            cleanup_restore.prepare(self.record, "tv.example")

    def test_an_existing_different_record_is_left_alone(self):
        list_path, filed, _ = cleanup_restore.prepare(self.record, "tv.example")
        other = json.loads(filed.read_text())
        other["packages"]["com.tcl.bi"] = "disabled"
        filed.write_text(json.dumps(other))
        with self.assertRaises(SystemExit):
            cleanup_restore.prepare(self.record, "tv.example")

    def test_androids_chooser_is_not_a_home(self):
        chooser = "android/com.android.internal.app.ResolverActivity"
        self.assertTrue(recovery.is_chooser(chooser))
        self.assertFalse(recovery.is_chooser("com.google.android.tvlauncher/.MainActivity"))

        class Dev:
            def __init__(self, answer):
                self.answer = answer

            def shell(self, command, read_timeout_s=None):
                return self.answer

        self.assertIsNone(recovery.query_home(Dev("priority=0 isDefault=false\n" + chooser + "\n")))
        self.assertIsNone(recovery.query_home(Dev("android\n")))
        self.assertEqual(recovery.query_home(Dev("com.google.android.tvlauncher/.MainActivity\n")),
                         "com.google.android.tvlauncher/.MainActivity")

    def test_a_record_holding_the_chooser_falls_back_to_the_stock_launcher(self):
        record = json.loads(FIXTURE.read_text())
        record["home"] = "android/com.android.internal.app.ResolverActivity"
        self.record.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n")
        list_path, _, _ = cleanup_restore.prepare(self.record, "tv.example")
        recovery.current_device = lambda: dict(self.device)
        commands = recovery.restore_commands(list_path, "com.google.android.tvlauncher", "tv.example")
        self.assertFalse([c for c in commands if "set-home-activity --user 0 android/" in c], commands)
        self.assertTrue([c for c in commands if "set-home-activity" in c and "resolve-activity" in c
                         and "com.google.android.tvlauncher" in c], commands)
        self.assertIn("pm enable com.google.android.tvlauncher", commands)


if __name__ == "__main__":
    unittest.main()
