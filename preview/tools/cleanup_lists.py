#!/usr/bin/env python3
"""Builds the Optional clean-up's tested lists for the Android apps from examples/*/debloat.txt.

    cleanup_lists.py           write preview/app/src/main/assets/cleanup/tcl-beyondtv4.json
    cleanup_lists.py --check   exit 1 if that file is not what the lists say (CI and tests/test_cleanup_lists.py)

Only the TCL BeyondTV4 lists on build RP1A.200622.001 (Android TV 11) are bundled: the owner's TCL reports the
fingerprint TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR10, the one examples/tcl-50c725k was tested on, and
examples/tcl-55c725 and examples/tcl-65c725 are the same device on the same build. Nothing is invented here: every
package, its group and its note come from the list files, and so do the packages their notes say to keep. Which
packages are risky, recommended or never allowed is decided on the TV (CleanupPolicy.java), not here.

A list is upstream evidence (AGENTS.md), so only its uncommented package lines are offered. A commented-out line was
not tested disabled and is never offered.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "preview/app/src/main/assets/cleanup/tcl-beyondtv4.json"
# The exact list for the owner's firmware first: on that TV its packages are "tested on this firmware".
PROFILES = ("tcl-50c725k", "tcl-55c725", "tcl-65c725")
FAMILY = {"brand": "TCL", "device": "BeyondTV4", "release": "11", "build": "RP1A.200622.001"}

PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
# A package named in a note: at least two dots, so file names (README.md, config.xml) are not mistaken for one.
NAMED_RE = re.compile(r"(?<![\w./-])([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+){2,})")
# brand/product/device:release/build/incremental, without the trailing ":user/release-keys" that a note may leave out;
# the TV compares the same part of its own fingerprint (CleanupLists.core).
FINGERPRINT_RE = re.compile(r"\b([A-Za-z0-9_]+/[A-Za-z0-9_]+/[A-Za-z0-9_]+:\d+/[A-Z0-9.]+/[A-Za-z0-9_]+)(?::[a-z]+/[a-z-]+)?")
# A note that names packages to leave alone.
KEEP_WORDS = re.compile(r"never disable|do not disable|\bkept\b|not disabled", re.I)


def comment(line):
    """The text of a comment line without its '#', or None for a package line or a blank line."""
    s = line.strip()
    return s[1:].strip() if s.startswith("#") else None


def commented_package(text):
    first = text.split()[0] if text.split() else ""
    return bool(PACKAGE_RE.fullmatch(first.rstrip(".,;:")))


def heading(text):
    """A group's short name: the first line of the paragraph above it, up to its first ':' or ' — '."""
    for cut in (":", " — ", ". "):
        if cut in text:
            text = text.split(cut, 1)[0]
    return text.strip().rstrip(".")


def parse(name):
    path = ROOT / "examples" / name / "debloat.txt"
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    text = "\n".join(lines)
    for key in ("device", "build"):
        if FAMILY[key] not in text:
            raise SystemExit(f"{path}: does not say {FAMILY[key]}; it is not in the TCL BeyondTV4 family")
    title = (comment(lines[0]) or name).split("(")[0].strip()
    product = re.search(r"\bproduct ([A-Za-z0-9_]+)", text)
    stock = re.search(r"Stock launcher:\s*([A-Za-z0-9_.]+?)[;,\s—]", text)
    fingerprints = sorted(set(FINGERPRINT_RE.findall(" ".join(comment(l) or "" for l in lines).replace("# ", ""))))

    packages, keep, seen = [], [], set()
    paragraph, group, keeping = [], None, False
    for line in lines:
        c = comment(line)
        if c is None and not line.strip():                      # a blank line ends a paragraph and a keep note
            paragraph, keeping = [], False
            continue
        if c is not None:
            if not c:                                            # "#" alone also ends them
                paragraph, keeping = [], False
                continue
            if KEEP_WORDS.search(c):
                keeping = True
            if keeping:
                keep += [m for m in NAMED_RE.findall(c) if m not in keep]
            if not commented_package(c):
                paragraph.append(c)
            continue
        entry, _, note = line.partition("#")
        package = entry.split()[0]
        if not PACKAGE_RE.fullmatch(package):
            raise SystemExit(f"{path}: not a package name: {package!r}")
        if paragraph:
            group, paragraph = heading(paragraph[0]), []
        keeping = False
        if package in seen:
            raise SystemExit(f"{path}: {package} is listed twice")
        seen.add(package)
        packages.append({"name": package, "group": group or "Other", "note": note.strip()})
    clash = sorted(seen & set(keep))
    if clash:
        raise SystemExit(f"{path}: its own notes say to keep {', '.join(clash)}, which it also lists")
    return {
        "id": name,
        "title": title,
        "source": f"examples/{name}/debloat.txt",
        "product": product.group(1) if product else None,
        "fingerprints": fingerprints,
        "stockLauncher": stock.group(1) if stock else None,
        "keep": sorted(keep),
        "packages": packages,
    }


def build():
    return {
        "about": "Generated from examples/*/debloat.txt by preview/tools/cleanup_lists.py. Do not edit by hand.",
        "family": FAMILY,
        "profiles": [parse(name) for name in PROFILES],
    }


def text():
    return json.dumps(build(), indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def main(args):
    want = text()
    if args == ["--check"]:
        have = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if have != want:
            sys.stderr.write(f"{OUT.relative_to(ROOT)} is out of date: run python3 preview/tools/cleanup_lists.py\n")
            return 1
        print(f"{OUT.relative_to(ROOT)} matches the lists")
        return 0
    if args:
        sys.stderr.write(__doc__)
        return 2
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(want, encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
