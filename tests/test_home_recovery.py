"""preview/tools/home_recovery.py: records the TV's factory Home and puts it back (G1; RC-10, HRC-10, OI-08).

A fake adb answers like an Android 11 TCL, so the real parsing, guard, confirmation and verification run
without a TV and without starting any process.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import io
import json
import os
import subprocess
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("home_recovery", ROOT / "preview" / "tools" / "home_recovery.py")
hr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hr)

ADDRESS = "192.0.2.44:5555"   # a documentation-only address (RFC 5737)
FACTORY = "com.google.android.tvlauncher/.MainActivity"
UNIFIED = "io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.HomeEntry"

RESOLVE_FACTORY = """priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.google.android.tvlauncher/.MainActivity
"""
RESOLVE_CHOOSER = """priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false
android/com.android.internal.app.ResolverActivity
"""
QUERY_THREE = """3 activities found:
  Activity #0:
    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
    com.google.android.tvlauncher/.MainActivity
  Activity #1:
    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
    io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.HomeEntry
  Activity #2:
    priority=-1000 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
    com.android.tv.settings/.FallbackHome
"""


class FakeTv:
    """Plays the adb program: connect, get-state and shell, against a small TV state."""

    def __init__(self, home=FACTORY, candidates=(FACTORY, UNIFIED, "com.android.tv.settings/.FallbackHome"),
                 packages=("io.unifiedtv.launcher.devicecheck",), honour_set=True, state="device"):
        self.home, self.candidates, self.packages = home, list(candidates), list(packages)
        self.honour_set, self.state, self.calls = honour_set, state, []

    def query(self):
        out = f"{len(self.candidates)} activities found:\n"
        for i, c in enumerate(self.candidates):
            out += f"  Activity #{i}:\n    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n    {c}\n"
        return out if self.candidates else "No activities found\n"

    def answer(self, cmd):
        if cmd == hr.RESOLVE:
            if self.home == "chooser":
                return RESOLVE_CHOOSER
            return f"priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n{self.home}\n"
        if cmd == hr.QUERY:
            return self.query()
        if cmd.startswith("pm list packages"):
            return "".join(f"package:{p}\n" for p in self.packages)
        if cmd.startswith("getprop"):
            return {"ro.product.model": "55C735\n", "ro.build.version.release": "11\n",
                    "ro.build.display.id": "V8-T653T01-LF1V123\n"}[cmd.split()[-1]]
        if cmd.startswith(hr.SET_HOME):
            if self.honour_set:
                self.home = cmd[len(hr.SET_HOME):]
            return f"Success: set home to {ADDRESS}\n" if self.honour_set else "Error: role not changed\n"
        raise AssertionError(f"unexpected command {cmd!r}")

    def __call__(self, args, **kwargs):
        self.calls.append(args)
        assert args[0] == "adb"
        if args[1] == "connect":
            return subprocess.CompletedProcess(args, 0, f"connected to {args[2]}\n", "")
        assert args[1:3] == ["-s", ADDRESS]
        if args[3] == "get-state":
            return subprocess.CompletedProcess(args, 0 if self.state == "device" else 1, self.state + "\n", "")
        assert args[3] == "shell" and len(args) == 5
        return subprocess.CompletedProcess(args, 0, self.answer(args[4]), "")

    def shell_commands(self):
        return [a[4] for a in self.calls if len(a) > 3 and a[3] == "shell"]


class Parsing(unittest.TestCase):
    def test_resolve_reads_the_component_on_the_last_line(self):
        self.assertEqual(hr.parse_resolve(RESOLVE_FACTORY), FACTORY)
        self.assertEqual(hr.parse_resolve("No activity found\n"), "")
        self.assertEqual(hr.kind(hr.parse_resolve(RESOLVE_CHOOSER)), "chooser")

    def test_query_lists_every_candidate_in_order(self):
        self.assertEqual(hr.parse_query(QUERY_THREE), [FACTORY, UNIFIED, "com.android.tv.settings/.FallbackHome"])
        self.assertEqual(hr.parse_query("No activities found\n"), [])

    def test_kinds_and_short_forms(self):
        self.assertEqual(hr.normalise(FACTORY), "com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity")
        self.assertEqual(hr.kind(FACTORY), "factory")
        self.assertEqual(hr.kind(UNIFIED), "unified")
        self.assertEqual(hr.kind("com.android.tv.settings/.FallbackHome"), "placeholder")

    def test_state_reports_unified_entry_and_factory_home(self):
        outputs = {hr.RESOLVE: RESOLVE_FACTORY, hr.QUERY: QUERY_THREE,
                   "pm list packages io.unifiedtv.launcher": "package:io.unifiedtv.launcher.devicecheck\n"}
        state = hr.state_from(outputs)
        self.assertEqual(state["factory_home"], FACTORY)
        self.assertEqual(state["factory_source"], "home_button")
        self.assertEqual(state["unified_launcher"]["io.unifiedtv.launcher.devicecheck"], {"installed": True, "home_entry_on": True})
        self.assertEqual(state["unified_launcher"]["io.unifiedtv.launcher"], {"installed": False, "home_entry_on": False})
        text = "\n".join(hr.describe(state))
        self.assertIn("Home entry switched on", text)
        self.assertIn("never choose it", text)

    def test_factory_found_from_candidates_when_unified_is_home(self):
        self.assertEqual(hr.factory_choice(UNIFIED, hr.parse_query(QUERY_THREE)), (FACTORY, "only_candidate"))
        self.assertEqual(hr.factory_choice("", [FACTORY, "com.tcl.home/.Main"])[1], "several_candidates")

    def test_address_and_adb_lookup(self):
        self.assertEqual(hr.tv_address(None, {"TV_HOST": "192.0.2.44"}), ADDRESS)
        self.assertEqual(hr.tv_address("192.0.2.44:5556", {}), "192.0.2.44:5556")
        self.assertEqual(hr.tv_address(None, {"TV_HOST": "192.0.2.44", "TV_PORT": "5557"}), "192.0.2.44:5557")
        self.assertEqual(hr.tv_address(None, {}), "")
        windows = os.path.join("C:\\Users\\owner", "platform-tools", "adb.exe")
        self.assertEqual(hr.find_adb({"USERPROFILE": "C:\\Users\\owner"}, isfile=lambda p: p == windows, which=lambda n: None), windows)
        self.assertEqual(hr.find_adb({"USERPROFILE": "/nowhere"}, isfile=lambda p: False, which=lambda n: "/usr/bin/adb"), "/usr/bin/adb")


class Guard(unittest.TestCase):
    def test_dangerous_commands_are_refused(self):
        for cmd in ("pm uninstall com.google.android.tvlauncher", "pm clear com.google.android.tvlauncher",
                    "pm disable-user --user 0 com.google.android.tvlauncher", "pm hide com.google.android.tvlauncher",
                    "cmd package set-home-activity x/.Y; pm uninstall z", "pm disable com.tcl.home",
                    "cmd package set-home-activity com.google.android.tvlauncher/.MainActivity && rm -rf /sdcard"):
            with self.assertRaises(RuntimeError, msg=cmd):
                hr.check_command(cmd)

    def test_only_known_commands_pass(self):
        self.assertEqual(hr.check_command(hr.RESOLVE), hr.RESOLVE)
        self.assertEqual(hr.check_command(hr.SET_HOME + FACTORY), hr.SET_HOME + FACTORY)
        for cmd in ("reboot", "settings put global x 1", "am start -n x/.Y", "cmd package set-home-activity not a component"):
            with self.assertRaises(RuntimeError, msg=cmd):
                hr.check_command(cmd)

    def test_the_guard_runs_before_adb(self):
        fake = FakeTv()
        dev = hr.Adb("adb", ADDRESS, fake)
        with self.assertRaises(RuntimeError):
            dev.shell("pm uninstall io.unifiedtv.launcher.devicecheck")
        self.assertEqual(fake.calls, [])


class Flow(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = os.path.join(self.tmp.name, "home-evidence")
        self.find_adb = hr.find_adb
        hr.find_adb = lambda env, **kwargs: "adb"   # the fake plays the adb program; nothing is started

    def tearDown(self):
        hr.find_adb = self.find_adb
        self.tmp.cleanup()

    def run_main(self, argv, fake, answers=()):
        replies = iter(answers)
        out, err = io.StringIO(), io.StringIO()
        cwd = os.getcwd()
        os.chdir(self.tmp.name)
        try:
            with redirect_stdout(out), redirect_stderr(err):
                code = hr.main(argv + ["--tv", ADDRESS], env={"USERPROFILE": "/nowhere"}, run=fake,
                               ask=lambda prompt: next(replies, ""))
        finally:
            os.chdir(cwd)
        return code, out.getvalue() + err.getvalue()

    def evidence(self):
        return sorted(Path(self.folder).glob("*.json")) if os.path.isdir(self.folder) else []

    def test_status_save_then_restore_picks_the_saved_factory_home_and_verifies(self):
        code, out = self.run_main(["status", "--save"], FakeTv())
        self.assertEqual(code, 0, out)
        self.assertIn("Factory Home to restore: " + FACTORY, out)
        # Later Unified TV became Home; a second, unrelated factory-looking app is also a candidate now.
        fake = FakeTv(home=UNIFIED, candidates=(FACTORY, UNIFIED, "com.tcl.overlay/.Home"))
        code, out = self.run_main(["restore"], fake, answers=["yes"])
        self.assertEqual(code, 0, out)
        self.assertIn("saved by status --save", out)
        self.assertEqual(fake.home, FACTORY)
        self.assertIn(hr.SET_HOME + FACTORY, fake.shell_commands())
        self.assertEqual(fake.shell_commands()[-1], hr.RESOLVE, "it reads the Home app back after setting it")
        restore = [p for p in self.evidence() if p.name.startswith("restore-")]
        self.assertEqual(len(restore), 1)
        self.assertEqual(json.loads(restore[0].read_text())["result"], "restored")

    def test_restore_refuses_without_yes(self):
        for answer in ("", "no", "y", "YES please"):
            fake = FakeTv(home=UNIFIED)
            code, out = self.run_main(["restore"], fake, answers=[answer])
            self.assertEqual(code, 1, answer)
            self.assertIn("nothing was changed", out)
            self.assertFalse(any(c.startswith(hr.SET_HOME) for c in fake.shell_commands()))
            self.assertEqual(fake.home, UNIFIED)

    def test_check_is_a_dry_run(self):
        fake = FakeTv(home=UNIFIED)
        code, out = self.run_main(["restore", "--check"], fake)
        self.assertEqual(code, 0, out)
        self.assertIn("Would run: adb shell " + hr.SET_HOME + FACTORY, out)
        self.assertFalse(any(c.startswith(hr.SET_HOME) for c in fake.shell_commands()))

    def test_restore_reports_when_the_tv_does_not_take_it(self):
        fake = FakeTv(home=UNIFIED, honour_set=False)
        code, out = self.run_main(["restore"], fake, answers=["yes"])
        self.assertEqual(code, 3, out)
        self.assertIn("still reports a different Home", out)

    def test_restore_never_offers_unified_or_the_placeholder(self):
        fake = FakeTv(home="chooser", candidates=(UNIFIED, "com.android.tv.settings/.FallbackHome"))
        code, out = self.run_main(["restore"], fake, answers=["yes"])
        self.assertEqual(code, 1, out)
        code, out = self.run_main(["restore", "--component", UNIFIED], fake, answers=["yes"])
        self.assertEqual(code, 1, out)
        self.assertIn("not a factory Home component", out)

    def test_several_candidates_ask_for_a_number(self):
        fake = FakeTv(home="chooser", candidates=(FACTORY, "com.tcl.home/.Main", UNIFIED))
        code, out = self.run_main(["restore"], fake, answers=["2", "yes"])
        self.assertEqual(code, 0, out)
        self.assertEqual(fake.home, "com.tcl.home/.Main")

    def test_a_record_from_another_tv_model_is_not_used(self):
        os.makedirs(self.folder)
        with open(os.path.join(self.folder, "factory-home-20261001-000000.json"), "w") as f:
            json.dump({"factory_home": "com.sony.dtv.home/.Main", "factory_source": "home_button",
                       "tv": {"model": "BRAVIA"}, "time": "2026-10-01"}, f)
        fake = FakeTv(home=UNIFIED)
        code, out = self.run_main(["restore"], fake, answers=["yes"])
        self.assertEqual(code, 0, out)
        self.assertEqual(fake.home, FACTORY)
        self.assertIn("the only factory Home candidate", out)

    def test_no_address_in_evidence_or_output(self):
        _, out1 = self.run_main(["status", "--save"], FakeTv())
        _, out2 = self.run_main(["restore"], FakeTv(home=UNIFIED), answers=["yes"])
        files = self.evidence()
        self.assertEqual(len(files), 2)
        host = ADDRESS.split(":")[0]
        for p in files:
            text = p.read_text()
            self.assertNotIn(host, text, p.name)
            record = json.loads(text)
            self.assertEqual(record["tv"], {"model": "55C735", "android": "11", "build": "V8-T653T01-LF1V123"})
        self.assertNotIn(host, out1 + out2)

    def test_unreachable_or_unauthorised_tv(self):
        code, out = self.run_main(["status"], FakeTv(state="unauthorized"))
        self.assertEqual(code, 1)
        self.assertIn("Allow USB debugging", out)
        self.assertNotIn(ADDRESS.split(":")[0], out)


if __name__ == "__main__":
    unittest.main()
