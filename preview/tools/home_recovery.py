#!/usr/bin/env python3
"""Record and restore the TV's factory Home over ADB, for the G1 Home tests on the TCL (spec RC-10, HRC-10, OI-08).

Standard library only, so it runs on its own on a Windows PC with Python and Google's platform-tools. It uses the
adb program: %USERPROFILE%\\platform-tools\\adb.exe, then ADB (an environment variable), then adb on PATH.

    home_recovery.py status [--save]           which app answers the Home button, every Home candidate, and
                                               whether Unified TV's launcher is installed with its Home entry on.
                                               --save records the factory Home in home-evidence/ for restore.
    home_recovery.py restore [--component C] [--check]
                                               after you type "yes", sets the factory Home back with
                                               `cmd package set-home-activity`, then reads it back to confirm.
                                               --check prints what it would do and changes nothing.
    Both take --tv HOST:PORT (or set TV_HOST, and TV_PORT if it isn't 5555).

It never uninstalls, disables, hides or clears anything: every command passes a guard first. The TV's address is
used only to reach it: it is never printed or written to the evidence files, which hold component names, the TV
model, the Android version and the build number.

Exit status: 0 done (or nothing to do), 1 refused or failed, 2 usage, 3 restore sent but the TV still reports a
different Home.
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import time

UNIFIED_PREFIX = "io.unifiedtv."
LAUNCHER_PACKAGES = ("io.unifiedtv.launcher", "io.unifiedtv.launcher.devicecheck")
HOME_ENTRY = "io.unifiedtv.preview.HomeEntry"
EVIDENCE = "home-evidence"
HOME_INTENT = "-a android.intent.action.MAIN -c android.intent.category.HOME"
RESOLVE = f"cmd package resolve-activity --brief {HOME_INTENT}"
QUERY = f"cmd package query-activities --brief {HOME_INTENT}"
SET_HOME = "cmd package set-home-activity "
PROPS = {"model": "getprop ro.product.model", "android": "getprop ro.build.version.release",
         "build": "getprop ro.build.display.id"}
# The only commands this tool sends. Anything else, and anything that could remove, disable, hide or clear a
# package, is refused before it reaches the TV.
ALLOWED = (RESOLVE, QUERY, "pm list packages io.unifiedtv.launcher") + tuple(PROPS.values())
FORBIDDEN = re.compile(r"\b(uninstall|clear|clear-data|disable|disable-user|disable-until-used|hide|unhide|suspend|"
                       r"reset|wipe|install|rm|enable)\b|pm\s+hide", re.I)
COMPONENT = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*/\.?[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$")
CHOOSER = "com.android.internal.app.ResolverActivity"

for _stream in (sys.stdout, sys.stderr):   # old Windows code pages: print a replacement rather than stopping
    try:
        _stream.reconfigure(errors="replace")
    except (AttributeError, ValueError):
        pass


# --- pure functions (unit-tested in tests/test_home_recovery.py; nothing here talks to a TV)

def check_command(cmd):
    """The command, if this tool may send it; otherwise raise before anything reaches the TV."""
    if FORBIDDEN.search(cmd):
        raise RuntimeError(f"refusing to send {cmd!r}: this tool never uninstalls, disables, hides or clears anything")
    if cmd in ALLOWED:
        return cmd
    if cmd.startswith(SET_HOME) and valid_component(cmd[len(SET_HOME):]):
        return cmd
    raise RuntimeError(f"refusing to send {cmd!r}: not one of this tool's read-only checks or the Home restore")


def valid_component(text):
    return bool(COMPONENT.match(text or ""))


def normalise(component):
    """'pkg/.Cls' to 'pkg/pkg.Cls', so the short and long forms Android prints compare equal."""
    if "/" not in (component or ""):
        return component or ""
    pkg, cls = component.split("/", 1)
    return f"{pkg}/{pkg}{cls}" if cls.startswith(".") else f"{pkg}/{cls}"


def parse_resolve(text):
    """`cmd package resolve-activity --brief` to the component on its last line ('' when none)."""
    lines = [line.strip() for line in (text or "").strip().splitlines() if line.strip()]
    if not lines or "No activity found" in lines[-1]:
        return ""
    return lines[-1] if valid_component(lines[-1]) else ""


def parse_query(text):
    """`cmd package query-activities --brief` to its components, in Android's order, without repeats."""
    found = []
    for line in (text or "").splitlines():
        line = line.strip()
        if valid_component(line) and line not in found:
            found.append(line)
    return found


def parse_packages(text):
    """`pm list packages` to a set of package names."""
    return {line.split(":", 1)[1].strip() for line in (text or "").splitlines() if line.startswith("package:")}


def kind(component):
    """unified, chooser, placeholder (Android's start-up FallbackHome) or factory."""
    full = normalise(component)
    if not full:
        return "none"
    pkg, cls = full.split("/", 1)
    if pkg.startswith(UNIFIED_PREFIX):
        return "unified"
    if cls == CHOOSER:
        return "chooser"
    if cls.endswith("FallbackHome"):
        return "placeholder"
    return "factory"


KIND_WORDS = {"unified": "Unified TV", "chooser": "no default set: Android asks which app to use",
              "placeholder": "Android's start-up placeholder; never choose it", "factory": "the TV's own Home",
              "none": "nothing"}


def factory_choice(home_now, candidates):
    """(component, how it was found) for the factory Home, or ('', why not)."""
    if kind(home_now) == "factory":
        return home_now, "home_button"
    factory = [c for c in candidates if kind(c) == "factory"]
    if len(factory) == 1:
        return factory[0], "only_candidate"
    return "", "none_found" if not factory else "several_candidates"


def state_from(outputs):
    """Everything status shows, from the raw command outputs keyed by command."""
    home_now = parse_resolve(outputs.get(RESOLVE, ""))
    candidates = parse_query(outputs.get(QUERY, ""))
    installed = parse_packages(outputs.get("pm list packages io.unifiedtv.launcher", ""))
    offered = {normalise(c).split("/", 1)[0] for c in candidates if normalise(c).endswith("/" + HOME_ENTRY)}
    factory, source = factory_choice(home_now, candidates)
    return {
        "tv": {k: outputs.get(cmd, "").strip() for k, cmd in PROPS.items()},
        "home_now": home_now, "home_now_kind": kind(home_now),
        "candidates": [{"component": c, "kind": kind(c)} for c in candidates],
        "unified_launcher": {p: {"installed": p in installed, "home_entry_on": p in offered} for p in LAUNCHER_PACKAGES},
        "factory_home": factory, "factory_source": source,
    }


def describe(state):
    """The status as plain lines for the console."""
    out = [f"The Home button opens: {state['home_now'] or '(could not be read)'} ({KIND_WORDS[state['home_now_kind']]})",
           "Home candidates on this TV:"]
    for c in state["candidates"] or [{"component": "(none listed)", "kind": "none"}]:
        out.append(f"  {c['component']}  ({KIND_WORDS[c['kind']]})")
    for pkg, info in state["unified_launcher"].items():
        name = "Unified TV review build" if pkg.endswith(".devicecheck") else "Unified TV launcher"
        if not info["installed"]:
            out.append(f"{name} ({pkg}): not installed")
        else:
            out.append(f"{name} ({pkg}): installed; Home entry "
                       + ("switched on (offered to Android as a Home)" if info["home_entry_on"] else "switched off"))
    if state["factory_home"]:
        how = "the Home button" if state["factory_source"] == "home_button" else "the only factory candidate"
        out.append(f"Factory Home to restore: {state['factory_home']} (from {how})")
    else:
        out.append("Factory Home to restore: not clear from this TV right now"
                   + (" (several candidates; restore will ask you to pick one)" if state["factory_source"] == "several_candidates" else ""))
    return out


def record(state, action, **extra):
    """An evidence record: component names, model, Android version and build only."""
    return dict({"tool": "preview/tools/home_recovery.py", "action": action, "time": time.strftime("%Y-%m-%dT%H:%M:%S%z")},
                **state, **extra)


def pick_saved(records, model):
    """The newest saved factory Home taken on this TV model, preferring one read from the Home button itself."""
    usable = [r for r in records if r.get("factory_home") and valid_component(r["factory_home"])
              and kind(r["factory_home"]) == "factory" and r.get("tv", {}).get("model", "") == model]
    usable.sort(key=lambda r: (r.get("factory_source") == "home_button", r.get("time", "")), reverse=True)
    return usable[0] if usable else None


def choose_target(state, saved, component=None, ask=input):
    """(component, source) for restore, or ('', reason to refuse)."""
    candidates = [c["component"] for c in state["candidates"]]
    present = {normalise(c) for c in candidates}
    factory = [c for c in candidates if kind(c) == "factory"]
    if component:
        if not valid_component(component) or kind(component) != "factory":
            return "", f"{component} is not a factory Home component (Unified TV and Android's placeholder are excluded)"
        if normalise(component) not in present:
            return "", f"{component} is not a Home candidate on this TV now"
        return component, "chosen with --component"
    if saved:
        if normalise(saved["factory_home"]) in present:
            return saved["factory_home"], f"saved by status --save on {saved.get('time', '?')}"
        return "", (f"the saved factory Home {saved['factory_home']} is not a Home candidate on this TV now; "
                    "nothing was changed. Pick one with --component from the status list")
    if len(factory) == 1:
        return factory[0], "the only factory Home candidate"
    if not factory:
        return "", "no factory Home candidate is listed, and none was saved; nothing was changed"
    print("No saved factory Home for this TV. Which one is the TV's own Home?")
    for i, c in enumerate(factory, 1):
        print(f"  {i}. {c}")
    answer = ask("Type its number (anything else cancels): ").strip()
    if answer.isdigit() and 1 <= int(answer) <= len(factory):
        return factory[int(answer) - 1], "picked from the candidate list"
    return "", "cancelled: nothing was changed"


def tv_address(arg, env):
    """HOST:PORT from --tv, or TV_HOST (with TV_PORT, default 5555); '' when neither is set."""
    raw = (arg or env.get("TV_HOST") or "").strip()
    if not raw:
        return ""
    if raw.startswith("["):
        return raw if "]:" in raw else f"{raw}:{env.get('TV_PORT') or 5555}"
    if raw.count(":") == 1:
        return raw
    if raw.count(":") > 1:
        return f"[{raw}]:{env.get('TV_PORT') or 5555}"
    return f"{raw}:{env.get('TV_PORT') or 5555}"


def find_adb(env, isfile=os.path.isfile, which=shutil.which):
    """adb from %USERPROFILE%\\platform-tools, then ADB, then ~/platform-tools, then PATH; '' if none."""
    home = env.get("USERPROFILE") or os.path.expanduser("~")
    for path in (os.path.join(home, "platform-tools", "adb.exe"), env.get("ADB", ""),
                 os.path.join(home, "platform-tools", "adb")):
        if path and isfile(path):
            return path
    return which("adb") or ""


# --- the TV

class AdbError(RuntimeError):
    pass


class Adb:
    """adb over the network. run is subprocess.run, or a fake in the tests. The address never reaches the output."""

    def __init__(self, adb, address, run=subprocess.run):
        self.adb, self.address, self.run = adb, address, run

    def redact(self, text):
        text = text or ""
        if self.address:
            host = self.address.rsplit(":", 1)[0].strip("[]")
            text = text.replace(self.address, "the TV").replace(host, "the TV")
        return re.sub(r"\b\d{1,3}(\.\d{1,3}){3}(:\d+)?\b", "the TV", text)

    def _call(self, args, timeout=60):
        try:
            r = self.run([self.adb] + args, capture_output=True, text=True, encoding="utf-8", errors="replace",
                         timeout=timeout)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise AdbError(self.redact(f"adb did not respond ({error})")) from None
        return r.returncode, (r.stdout or "") + (r.stderr or "")

    def connect(self):
        _code, out = self._call(["connect", self.address], timeout=30)
        code, state = self._call(["-s", self.address, "get-state"], timeout=30)
        state = state.strip()
        if code == 0 and state == "device":
            return
        if "unauthorized" in state or "unauthorized" in out:
            raise AdbError("The TV has not allowed this PC yet: accept \"Allow USB debugging?\" on the TV, then run this again.")
        raise AdbError("Could not reach the TV over ADB. Check that network debugging is on and the address is right. "
                       "adb said: " + self.redact(out.strip() or state))

    def shell(self, cmd):
        code, out = self._call(["-s", self.address, "shell", check_command(cmd)])
        if code != 0:
            raise AdbError("adb shell failed: " + self.redact(out.strip()))
        return out


def read_state(dev):
    return state_from({cmd: dev.shell(cmd) for cmd in ALLOWED})


def save(rec, folder=EVIDENCE, prefix="factory-home"):
    os.makedirs(folder, exist_ok=True)
    stem = os.path.join(folder, f"{prefix}-" + time.strftime("%Y%m%d-%H%M%S"))
    path, n = stem + ".json", 1
    while os.path.exists(path):   # never overwrite an earlier record
        path, n = f"{stem}-{n}.json", n + 1
    with open(path, "x", encoding="utf-8") as f:
        json.dump(rec, f, indent=2)
    return path


def load_records(folder=EVIDENCE):
    out = []
    for path in sorted(glob.glob(os.path.join(folder, "factory-home-*.json"))):
        try:
            with open(path, encoding="utf-8") as f:
                out.append(json.load(f))
        except (OSError, ValueError):
            print(f"Skipping {path}: not a readable record.")
    return out


def status(dev, save_it, folder=EVIDENCE):
    state = read_state(dev)
    for line in describe(state):
        print(line)
    if save_it:
        if not state["factory_home"]:
            print("Not saved: the factory Home isn't clear. Run this while the TV's own Home answers the Home button.")
            return 1
        print("Saved:", save(record(state, "status"), folder))
    return 0


def restore(dev, component=None, check=False, ask=input, folder=EVIDENCE):
    state = read_state(dev)
    print(f"The Home button opens now: {state['home_now'] or '(could not be read)'} ({KIND_WORDS[state['home_now_kind']]})")
    saved = pick_saved(load_records(folder), state["tv"]["model"])
    target, source = choose_target(state, saved, component, ask)
    if not target:
        print("Refused:", source)
        return 1
    print(f"Factory Home: {target} ({source})")
    if normalise(state["home_now"]) == normalise(target):
        print("That is already the Home app. Nothing to do.")
        return 0
    command = check_command(SET_HOME + target)
    if check:
        print(f"Would run: adb shell {command}")
        print("Then it would read the Home app back to confirm. --check: nothing was changed.")
        return 0
    print(f"This runs: adb shell {command}")
    print("It changes only which app answers the Home button. Nothing is uninstalled, disabled or cleared.")
    if ask(f"Type yes to make {target} the Home app again: ").strip().lower() != "yes":
        print("Not confirmed: nothing was changed.")
        return 1
    out = dev.shell(command).strip()
    after = parse_resolve(dev.shell(RESOLVE))
    ok = normalise(after) == normalise(target)
    rec = record(state, "restore", target=target, target_source=source, set_home_output=dev.redact(out)[:200],
                 home_after=after, result="restored" if ok else "not confirmed")
    print("Android said:", dev.redact(out) or "(nothing)")
    print(f"The Home button now opens: {after or '(could not be read)'}")
    print("Evidence:", save(rec, folder, "restore"))
    if ok:
        print("Restored. Press Home on the remote: the TV's own Home should appear.")
        return 0
    print("The TV still reports a different Home. Use the TV's own Settings (Apps or System > Home screen), "
          "or remove the Unified TV review build, which also hands Home back.", file=sys.stderr)
    return 3


def main(argv, env=os.environ, run=subprocess.run, ask=input):
    tv = argparse.ArgumentParser(add_help=False)
    tv.add_argument("--tv", metavar="HOST:PORT", help="the TV's address (or set TV_HOST); never printed or saved")
    parser = argparse.ArgumentParser(description="Record and restore the TV's factory Home (Unified TV G1).")
    sub = parser.add_subparsers(dest="action", required=True)
    s = sub.add_parser("status", parents=[tv], help="show the Home app and candidates")
    s.add_argument("--save", action="store_true", help="record the factory Home in home-evidence/")
    r = sub.add_parser("restore", parents=[tv], help="set the factory Home back, after you type yes")
    r.add_argument("--component", help="a factory Home component from the status list")
    r.add_argument("--check", action="store_true", help="show what would happen; change nothing")
    args = parser.parse_args(argv)
    address = tv_address(args.tv, env)
    if not address:
        print("Give the TV's address with --tv HOST:PORT, or in PowerShell: $env:TV_HOST = \"<the TV's address>\"")
        return 2
    adb = find_adb(env)
    if not adb:
        print("adb was not found. Install Google's platform-tools in your home folder (docs/OWNER_TESTING.md).")
        return 2
    dev = Adb(adb, address, run)
    try:
        dev.connect()
        if args.action == "status":
            return status(dev, args.save)
        return restore(dev, args.component, args.check, ask)
    except (AdbError, RuntimeError) as error:
        print(dev.redact(str(error)), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
