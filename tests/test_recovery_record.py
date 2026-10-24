"""Run tools/recovery.py and the shell that calls it against a simulated TV (#239).

The property worth testing is the one no TV reproduces on demand: what undo does to a package the
owner had already disabled before cleanup, and whether the record survives a retry. tools/tv.py is
replaced by a fake that answers the queries from a JSON file, so the real recovery.py logic runs and
only the ADB transport is simulated.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
STOCK_HOME = "com.google.android.tvlauncher/.MainActivity"
TV_A = "SERIALA0001"
TV_B = "SERIALB0002"

# Stands in for tools/tv.py: answers the queries recovery.py makes, and records what a batch sends.
FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


state = load()


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


def answer(command):
    if command == "pm list packages":
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command == "pm list packages -d":
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if "resolve-activity" in command:
        return (state["home"] or "") + "\n"
    if command.startswith("settings get global "):
        key = command.split()[-1]
        return str(state["settings"].get(key, "null")) + "\n"
    # The TV's own ids, which tell two TVs behind the same address apart.
    if command == "getprop ro.serialno":
        return (state.get("serial") or "") + "\n"
    if command == "settings get secure android_id":
        return (state.get("android_id") or "null") + "\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        return answer(command)

    def close(self):
        pass


def connect():
    if state.get("unreachable"):
        raise ConnectionError("could not connect to 192.168.1.50:5555")
    return Device()


def main():
    args = sys.argv[1:]
    if "--batch" in args:
        for line in sys.stdin:
            line = line.strip()
            if line and not line.startswith("#"):
                state["commands"].append(line)
        save()
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''


class RecoveryRecordTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        for name in ("disable.sh", "restore.sh", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, tools / name)
        (tools / "tv.py").write_text(FAKE_TV)
        # The scripts call tools/venv/bin/python; run the real interpreter, which imports the fake
        # tv.py for the transport and the real recovery.py for the logic.
        client = tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)

        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.list_path = self.base / "debloat.txt"
        self.write_list(["com.example.enabled", "com.example.already_off", "com.example.absent"])

    def write_list(self, names):
        self.list_path.write_text("# My TV\n" + "".join(f"{name}\n" for name in names))

    def tv(self, **overrides):
        state = {
            "installed": ["com.example.enabled", "com.example.already_off",
                          "com.google.android.tvlauncher"],
            "disabled": ["com.example.already_off"],
            "home": STOCK_HOME,
            "settings": {"window_animation_scale": "0.5",
                         "transition_animation_scale": "null",
                         "animator_duration_scale": "1.0"},
            "commands": [],
            "unreachable": False,
            "serial": TV_A,
            "android_id": "a1b2c3d4e5f60718",
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def run_script(self, script, *args, **extra_env):
        env = {k: v for k, v in os.environ.items() if k not in ("TV_PORT", "RESTORE_WITHOUT_RECORD")}
        env.update(TV_HOST="192.168.1.50", HOME=str(self.home), FAKE_TV_STATE=str(self.state_path))
        env.update(extra_env)
        return subprocess.run(
            ["bash", str(self.repo / "tools" / script), *args],
            cwd=self.repo, env=env, capture_output=True, text=True, timeout=120,
        )

    def record(self):
        files = list((self.home / ".cleanyourtv").glob("recovery-*.json"))
        self.assertLessEqual(len(files), 1, "one record per TV and list")
        return json.loads(files[0].read_text()) if files else None

    def sent(self):
        with open(self.state_path) as f:
            return json.load(f)["commands"]

    def test_save_records_the_state_undo_needs(self):
        self.tv()
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        record = self.record()
        self.assertIsNotNone(record)
        self.assertEqual(record["packages"], {
            "com.example.enabled": "enabled",
            "com.example.already_off": "disabled",
            "com.example.absent": "absent",
            # Home's package is recorded even though the list does not mention it: undo has to put it
            # back if the list disabled it.
            "com.google.android.tvlauncher": "enabled",
        })
        self.assertEqual(record["home"], STOCK_HOME)
        self.assertEqual(record["settings"]["window_animation_scale"], "0.5")
        # The list itself still reaches the TV, after the record is on disk.
        self.assertEqual(self.sent()[:3], [
            "pm disable-user --user 0 com.example.enabled",
            "pm disable-user --user 0 com.example.already_off",
            "pm disable-user --user 0 com.example.absent",
        ])

    def test_retry_keeps_the_original_record(self):
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        first = self.record()
        # A second run happens on a TV where the list has already been applied: enabled is now off.
        self.tv(installed=["com.example.already_off", "com.google.android.tvlauncher"],
                disabled=["com.example.enabled", "com.example.already_off"])
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("keeping the recovery record", result.stdout)
        self.assertEqual(self.record()["packages"], first["packages"])

    def test_failed_save_disables_nothing(self):
        self.tv(unreachable=True)
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.sent(), [], "a failed record must not be followed by a mutation")
        self.assertIsNone(self.record())

    def test_corrupt_record_is_not_overwritten(self):
        self.tv()
        folder = self.home / ".cleanyourtv"
        # Take a record, then damage it the way an interrupted copy would.
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        path = next(folder.glob("recovery-*.json"))
        path.write_text("{not json")
        with open(self.state_path) as f:
            state = json.load(f)
        state["commands"] = []
        self.state_path.write_text(json.dumps(state))

        result = self.run_script("disable.sh", str(self.list_path))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not a usable recovery record", result.stderr)
        self.assertEqual(path.read_text(), "{not json", "the original file is left for inspection")
        self.assertEqual(self.sent(), [], "nothing was disabled")

    def test_restore_replays_recorded_states(self):
        self.tv(settings={"window_animation_scale": "0.5",
                          "transition_animation_scale": "null",
                          "animator_duration_scale": "3.0"})
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        with open(self.state_path) as f:
            state = json.load(f)
        state["commands"] = []
        self.state_path.write_text(json.dumps(state))

        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertIn("pm enable com.example.enabled", sent)
        self.assertNotIn("pm enable com.example.already_off",
                         sent, "a package that was already disabled stays disabled")
        self.assertNotIn("pm enable com.example.absent", sent)
        # Home is the component that owned it, not a package name resolved on the fly.
        self.assertIn(f"cmd package set-home-activity --user 0 {STOCK_HOME}", sent)
        # Settings go back to the recorded values, including the one that was unset before.
        self.assertIn("settings put global window_animation_scale 0.5", sent)
        self.assertIn("settings delete global transition_animation_scale", sent)
        self.assertIn("settings put global animator_duration_scale 3.0", sent)
        self.assertEqual(sent[-1], "pm list packages -d")
        self.assertIn("leaving com.example.already_off alone", result.stderr)

    def test_restore_without_a_record_sends_nothing_unless_asked(self):
        # Enabling the whole list also enables what the owner had switched off before cleanup, so it
        # is not what restore does on its own.
        self.tv()
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("no recovery record", result.stderr)
        self.assertIn("RESTORE_WITHOUT_RECORD=1", result.stderr)
        self.assertEqual(self.sent(), [], "nothing reached the TV")

    def test_restore_without_a_record_falls_back_when_asked(self):
        self.tv()
        result = self.run_script("restore.sh", str(self.list_path), RESTORE_WITHOUT_RECORD="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertIn("no recovery record", result.stderr)
        self.assertEqual(sent[:3], [
            "pm enable com.example.enabled",
            "pm enable com.example.already_off",
            "pm enable com.example.absent",
        ])
        self.assertIn("pm enable com.google.android.tvlauncher", sent)
        self.assertIn("settings put global window_animation_scale 1.0", sent)

    def test_edited_list_leaves_new_names_alone(self):
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.write_list(["com.example.enabled", "com.example.added_later"])
        with open(self.state_path) as f:
            state = json.load(f)
        state["commands"] = []
        self.state_path.write_text(json.dumps(state))

        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("pm enable com.example.added_later", self.sent())
        self.assertIn("not in the record, leaving alone: com.example.added_later", result.stderr)

    def clear_sent(self):
        with open(self.state_path) as f:
            state = json.load(f)
        state["commands"] = []
        self.state_path.write_text(json.dumps(state))

    def test_rerun_with_edited_list_records_the_new_names(self):
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        first = self.record()
        # The list gains a name; the second run happens on a TV where the first list is applied.
        self.write_list(["com.example.enabled", "com.example.already_off", "com.example.absent",
                         "com.example.added_later"])
        self.tv(installed=["com.example.enabled", "com.example.already_off",
                           "com.example.added_later", "com.google.android.tvlauncher"],
                disabled=["com.example.enabled", "com.example.already_off"])
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        packages = self.record()["packages"]
        self.assertEqual(packages["com.example.added_later"], "enabled")
        # Entries from the first run keep the state from before it, not the half-applied one.
        for name, state in first["packages"].items():
            self.assertEqual(packages[name], state, name)
        self.assertEqual(self.record()["created"], first["created"])

        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertIn("pm enable com.example.added_later", sent,
                      "a name added to the list later is not left disabled forever")
        self.assertIn("pm enable com.example.enabled", sent)
        self.assertNotIn("pm enable com.example.already_off", sent)

    def test_custom_launcher_home_restores_the_stock_launcher(self):
        custom = "com.gathin.tvhome/.MainActivity"
        self.tv(installed=["com.example.enabled", "com.example.already_off",
                           "com.google.android.tvlauncher", "com.gathin.tvhome"],
                home=custom)
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.assertEqual(self.record()["home"], custom)
        self.clear_sent()

        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertIn("pm enable com.google.android.tvlauncher", sent)
        self.assertNotIn(f"cmd package set-home-activity --user 0 {custom}", sent)
        home = [c for c in sent if c.startswith("cmd package set-home-activity")]
        self.assertEqual(len(home), 1, sent)
        self.assertIn("resolve-activity", home[0])
        self.assertIn("com.google.android.tvlauncher", home[0])
        self.assertLess(sent.index("pm enable com.google.android.tvlauncher"), sent.index(home[0]))

    def test_stock_launcher_disabled_before_cleanup_stays_disabled(self):
        custom = "com.gathin.tvhome/.MainActivity"
        self.write_list(["com.example.enabled", "com.google.android.tvlauncher"])
        self.tv(installed=["com.example.enabled", "com.google.android.tvlauncher",
                           "com.gathin.tvhome"],
                disabled=["com.google.android.tvlauncher"], home=custom)
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.assertEqual(self.record()["packages"]["com.google.android.tvlauncher"], "disabled")
        self.clear_sent()

        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("pm enable com.google.android.tvlauncher", self.sent())

    def test_stock_launcher_enabled_once_when_it_owned_home(self):
        # The usual case: Home's package is recorded as enabled, so it is enabled exactly once.
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertEqual(sent.count("pm enable com.google.android.tvlauncher"), 1)
        self.assertIn(f"cmd package set-home-activity --user 0 {STOCK_HOME}", sent)

    def test_list_that_is_not_package_names_is_refused(self):
        self.tv()
        self.list_path.write_text("com.example.good\ncom.example.evil;settings put global adb_enabled 0\n")
        for script in ("disable.sh", "restore.sh"):
            with self.subTest(script=script):
                result = self.run_script(script, str(self.list_path))
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("not a package name", result.stderr)
                self.assertIn("com.example.evil;settings", result.stderr)
                self.assertEqual(self.sent(), [])

    def test_valid_record_restores_the_same_batch(self):
        # The whole batch for the usual case, so the checks on the record cannot change it.
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.sent(), [
            "pm enable com.example.enabled",
            "pm enable com.google.android.tvlauncher",
            f"cmd package set-home-activity --user 0 {STOCK_HOME}",
            "settings put global window_animation_scale 0.5",
            "settings delete global transition_animation_scale",
            "settings put global animator_duration_scale 1.0",
            "pm list packages -d",
        ])

    def test_edited_record_is_refused_and_left_alone(self):
        # The record is replayed on the TV's shell, so a hand-edited one gets the same checks the list
        # does: nothing reaches the TV and the file stays as it was for inspection.
        edits = {
            "package name": lambda r: r["packages"].update(
                {"com.example.x; settings put global adb_enabled 0 #": "enabled"}),
            "package state": lambda r: r["packages"].update({"com.example.enabled": "on"}),
            "home": lambda r: r.update(home=f"{STOCK_HOME}; settings put global adb_enabled 0"),
            "settings": lambda r: r.update(settings=["window_animation_scale", "0.5"]),
            "device": lambda r: r.update(device={"serial": "x; settings put global adb_enabled 0"}),
        }
        for field, edit in edits.items():
            for script in ("restore.sh", "disable.sh"):
                with self.subTest(field=field, script=script):
                    shutil.rmtree(self.home / ".cleanyourtv", ignore_errors=True)
                    self.tv()
                    self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
                    path = next((self.home / ".cleanyourtv").glob("recovery-*.json"))
                    record = json.loads(path.read_text())
                    edit(record)
                    path.write_text(json.dumps(record))
                    before = path.read_bytes()
                    self.clear_sent()

                    result = self.run_script(script, str(self.list_path))
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn(str(path), result.stderr)
                    self.assertIn("not a usable recovery record", result.stderr)
                    self.assertEqual(self.sent(), [], "nothing reached the TV")
                    self.assertEqual(path.read_bytes(), before, "the file is left for inspection")

    # ---- which TV a record belongs to ---------------------------------------------------------

    def records(self):
        return sorted((self.home / ".cleanyourtv").glob("recovery-*.json"))

    def test_record_holds_the_tv_id_and_port(self):
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        record = self.record()
        self.assertEqual(record["device"], {"serial": TV_A, "android_id": "a1b2c3d4e5f60718"})
        self.assertEqual((record["host"], record["port"]), ("192.168.1.50", "5555"))

    def test_two_tvs_behind_relays_get_their_own_records(self):
        # tools/relay.md: one relay per TV, each on its own port, every TV at 127.0.0.1.
        relay_a = {"TV_HOST": "127.0.0.1", "TV_PORT": "15555"}
        relay_b = {"TV_HOST": "127.0.0.1", "TV_PORT": "15556"}
        self.tv(serial=TV_A, android_id="aaaaaaaaaaaa0001")
        self.assertEqual(self.run_script("disable.sh", str(self.list_path), **relay_a).returncode, 0)
        # TV B had com.example.enabled switched off by its owner, and nothing else.
        self.tv(serial=TV_B, android_id="bbbbbbbbbbbb0002", disabled=["com.example.enabled"])
        result = self.run_script("disable.sh", str(self.list_path), **relay_b)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("keeping the recovery record", result.stdout, "B must not reuse A's record")

        records = [json.loads(path.read_text()) for path in self.records()]
        self.assertEqual(len(records), 2, self.records())
        by_serial = {r["device"]["serial"]: r for r in records}
        self.assertEqual(by_serial[TV_A]["packages"]["com.example.already_off"], "disabled")
        self.assertEqual(by_serial[TV_B]["packages"]["com.example.enabled"], "disabled")
        self.assertEqual(by_serial[TV_B]["packages"]["com.example.already_off"], "enabled")

        # Undo on B replays B's states, not A's.
        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path), **relay_b)
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertIn("pm enable com.example.already_off", sent)
        self.assertNotIn("pm enable com.example.enabled", sent)

    def test_record_from_another_tv_is_refused(self):
        self.tv(serial=TV_A)
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        path = self.records()[0]
        before = path.read_bytes()
        # Another TV now answers at the same address (a new DHCP lease, a relay pointed elsewhere).
        self.tv(serial=TV_B, android_id="bbbbbbbbbbbb0002")
        for script in ("restore.sh", "disable.sh"):
            with self.subTest(script=script):
                self.clear_sent()
                result = self.run_script(script, str(self.list_path))
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("taken on another TV", result.stderr)
                self.assertIn(TV_A, result.stderr)
                self.assertIn(TV_B, result.stderr)
                self.assertEqual(self.sent(), [], "nothing reached the TV")
                self.assertEqual(path.read_bytes(), before, "A's record is left as it was")
                self.assertEqual(self.records(), [path])

    def test_placeholder_serial_is_told_apart_by_android_id(self):
        # Many cheap boxes all report the same serial; android_id still differs.
        self.tv(serial="0123456789ABCDEF", android_id="aaaaaaaaaaaa0001")
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.assertEqual(self.record()["device"], {"serial": None, "android_id": "aaaaaaaaaaaa0001"})
        self.tv(serial="0123456789ABCDEF", android_id="bbbbbbbbbbbb0002")
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("taken on another TV", result.stderr)
        self.assertEqual(self.sent(), [])

    def test_record_is_found_after_the_address_changes(self):
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        first = self.record()
        # The router gave the same TV a new address, and the list is already applied.
        self.tv(installed=["com.example.already_off", "com.google.android.tvlauncher"],
                disabled=["com.example.enabled", "com.example.already_off"])
        result = self.run_script("disable.sh", str(self.list_path), TV_HOST="192.168.1.77")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("keeping the recovery record", result.stdout)
        self.assertEqual(self.record()["packages"], first["packages"], "still one record, unchanged")

        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path), TV_HOST="192.168.1.77")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("using the record taken on this TV at 192.168.1.50", result.stderr)
        self.assertIn("pm enable com.example.enabled", self.sent())
        self.assertNotIn("pm enable com.example.already_off", self.sent())

    def test_record_from_before_the_id_still_restores(self):
        # A record written by an earlier version: keyed on TV_HOST alone, with no port and no id.
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        path = self.records()[0]
        record = json.loads(path.read_text())
        for key in ("device", "port"):
            del record[key]
        legacy = path.with_name(path.name.replace("-5555-", "-"))
        legacy.write_text(json.dumps(record))
        path.unlink()

        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("does not say which TV it was taken on", result.stderr)
        sent = self.sent()
        self.assertIn("pm enable com.example.enabled", sent)
        self.assertNotIn("pm enable com.example.already_off", sent)

        # disable keeps using it too: no second record that would describe a half-applied TV.
        self.clear_sent()
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("keeping the recovery record", result.stdout)
        self.assertEqual(self.records(), [legacy])

    def test_tv_that_gives_no_id_cannot_use_a_record_that_has_one(self):
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.tv(serial="", android_id="null")
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("did not give an id", result.stderr)
        self.assertEqual(self.sent(), [])


if __name__ == "__main__":
    unittest.main()
