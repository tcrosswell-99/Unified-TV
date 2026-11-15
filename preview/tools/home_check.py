#!/usr/bin/env python3
"""Fail the build if a fresh install could take over the TV's Home button (spec 29.3: owner opt-in only).

Reads `aapt2 dump xmltree --file AndroidManifest.xml` output. HOME may appear only:
  - inside <queries> (package visibility, not a component), or
  - on an <activity-alias> that ships with android:enabled=false (switched on only by the owner's guided choice),
    and only in the launcher app: with --no-home (the preview) any HOME component at all fails.
Usage: home_check.py [--no-home] out/manifest.txt
"""
import re
import sys


def problems(xmltree, no_home=False):
    """List of reasons the manifest is unsafe; empty when every HOME filter is opt-in only."""
    stack, out = [], []  # stack of (indent, tag, attrs)
    for line in xmltree.splitlines():
        m = re.match(r"^(\s*)(E|A): (.*)$", line)
        if not m:
            continue
        indent, kind, rest = len(m.group(1)), m.group(2), m.group(3)
        if kind == "E":
            tag = rest.split(" ")[0]
            while stack and stack[-1][0] >= indent:
                stack.pop()
            stack.append((indent, tag, []))
        elif stack:
            stack[-1][2].append(rest)
            if "android.intent.category.HOME" in rest and stack[-1][1] == "category":
                tags = [s[1] for s in stack]
                if "queries" in tags:
                    continue
                owner = next((s for s in reversed(stack) if s[1] in ("activity", "activity-alias")), None)
                if no_home:
                    out.append("HOME declared in the preview: only the separate launcher app may offer Home")
                elif owner is None:
                    out.append("HOME category outside any component")
                elif owner[1] != "activity-alias":
                    out.append("HOME declared on an activity: a fresh install would become a Home candidate")
                elif not any(re.search(r":enabled\(0x0101000e\)=(false|\(type 0x12\)0x0)", a) for a in owner[2]):
                    out.append("HOME alias is not shipped switched off (android:enabled=false)")
    return out


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a]
    found = problems(open(args[-1], encoding="utf-8").read(), no_home="--no-home" in args)
    for p in found:
        print("Preview Home safety:", p, file=sys.stderr)
    sys.exit(3 if found else 0)
