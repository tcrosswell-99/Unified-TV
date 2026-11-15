#!/usr/bin/env python3
"""Put back what Unified TV's Optional clean-up turned off, from a computer, without Unified TV.

    TV_HOST=<tv ip> python3 preview/tools/cleanup_restore.py <unifiedtv-cleanup-record.json> [--stock <launcher package>]

The clean-up writes its recovery record on the TV in tools/recovery.py's own format, and the phone can download it
(Settings > Updates & About > Advanced > Optional clean-up > Save the undo files on your phone). This script checks the
record exactly as recovery.py does, writes the list of packages it turned off next to it (unifiedtv-cleanup.txt) if the
phone's copy is not there, files the record where tools/restore.sh looks for it (~/.cleanyourtv/), and prints the
restore.sh line to run. It changes nothing on the TV itself; restore.sh does, after checking it is talking to the TV
the record was taken on (its serial number or android_id).

restore.sh then re-enables only the packages the record holds as "enabled" (the ones the clean-up turned off), puts
Home back to the component recorded then, and the animation scales to their recorded values. Packages that were
already off before the clean-up stay off.

Without a computer: on the TV, Settings > Apps > See all apps > Show system apps > the app > Enable.
"""
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import recovery  # noqa: E402  (tools/recovery.py: the record format and where restore.sh finds it)

LIST_NAME = "unifiedtv-cleanup.txt"
SOURCE = "Unified TV Optional clean-up"
DEFAULT_STOCK = "com.google.android.tvlauncher"


def list_text(record):
    names = sorted(name for name, state in record["packages"].items() if state == "enabled")
    head = ("# Unified TV Optional clean-up: the apps it turned off on this TV.\n"
            "# Written by preview/tools/cleanup_restore.py from the recovery record; restore with tools/restore.sh.\n")
    return head + "".join(f"{name}\n" for name in names)


def prepare(record_path, raw_host, stock=DEFAULT_STOCK):
    """Checks the record, writes the list beside it when needed and files the record for restore.sh.

    Returns (list path, filed record path, restore command)."""
    record_path = Path(record_path)
    record = recovery.load_record(record_path)          # the same checks restore.sh's records get
    if record.get("source") != SOURCE:
        sys.stderr.write(f"note: {record_path} was not written by Unified TV's clean-up; using it as a recovery.py record.\n")
    if not raw_host:
        raise SystemExit("Set TV_HOST to the TV's address (as for tools/restore.sh), e.g. TV_HOST=<tv ip>.")
    if not recovery.PACKAGE_RE.fullmatch(stock):
        raise SystemExit(f"not a package name: {stock!r}")
    changed = sorted(name for name, state in record["packages"].items() if state == "enabled")
    if not changed:
        raise SystemExit(f"{record_path}: the clean-up turned nothing off, so there is nothing to restore.")

    list_path = record_path.with_name(LIST_NAME)
    if list_path.exists():
        listed = recovery.read_list(list_path)
        if sorted(listed) != changed:
            raise SystemExit(f"{list_path} does not hold the same packages as {record_path}. Keep the two files that were "
                             "downloaded together, or move the list aside and run this again to write it from the record.")
    else:
        list_path.write_text(list_text(record), encoding="utf-8")

    host, port = recovery.endpoint(raw_host)
    filed = recovery.record_path(list_path, host, port)
    record["host"], record["port"], record["list"] = host, port, str(list_path.resolve())
    if filed.exists():
        existing = recovery.load_record(filed)
        if existing.get("packages") != record["packages"] or existing.get("device") != record.get("device"):
            raise SystemExit(f"{filed} already holds a different record for this TV and list. It holds the state from "
                             "before the first change, so it is left alone: move it aside deliberately if it is stale.")
    else:
        recovery.write_record(filed, record)
    command = f"TV_HOST={raw_host} tools/restore.sh {list_path} {stock}"
    return list_path, filed, command


def main(args):
    stock = DEFAULT_STOCK
    if "--stock" in args:
        i = args.index("--stock")
        if i + 1 >= len(args):
            sys.stderr.write(__doc__)
            return 2
        stock = args[i + 1]
        args = args[:i] + args[i + 2:]
    if len(args) != 1 or args[0] in ("-h", "--help"):
        sys.stderr.write(__doc__)
        return 2
    list_path, filed, command = prepare(args[0], os.environ.get("TV_HOST", ""), stock)
    record = json.loads(Path(filed).read_text(encoding="utf-8"))
    print(f"list: {list_path}")
    print(f"record filed for restore.sh: {filed}")
    print(f"taken {record.get('created')} on {record.get('fingerprint') or 'this TV'}; "
          f"{sum(1 for s in record['packages'].values() if s == 'enabled')} apps to turn back on.")
    print("Now run, from the repository root:")
    print(f"  {command}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
