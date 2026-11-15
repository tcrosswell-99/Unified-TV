#!/usr/bin/env python3
"""How much memory and CPU Unified TV uses on a TV, measured over a few minutes (spec 14; docs/OWNER_TESTING.md).

Standard library only. It runs `adb shell` against the TV adb is already connected to (`adb connect` first; set
ANDROID_SERIAL if more than one device is connected; set ADB to use another adb binary). Every sample reads:

    dumpsys meminfo <package>          total PSS, Java heap, native heap, graphics, code (App Summary)
    dumpsys gfxinfo <package> reset    frames drawn and janky frames since the last sample (then resets them)
    /proc/<pid>/stat                   the app's CPU time, as a percentage of one core over the sample
    /proc/meminfo, /proc/pressure/memory   the TV's free memory and memory pressure (some avg10)

Usage:
    mem_check.py [--package PKG] [--minutes 3] [--every 15]

Leave Home on screen, or do whatever is being measured (move along the dock, open the library, leave it idle) while it
runs. Nothing on the TV is changed except gfxinfo's own frame counters. The report prints the model and Android
version, never a serial number, an IP address or an account.
"""
import argparse
import os
import re
import statistics
import subprocess
import sys
import time

CANDIDATES = ("io.unifiedtv.launcher.devicecheck", "io.unifiedtv.launcher", "io.unifiedtv.preview")
SUMMARY_ROWS = {"Java Heap": "java", "Native Heap": "native", "Code": "code", "Stack": "stack",
                "Graphics": "graphics", "Private Other": "other", "System": "system"}


# --- pure parsing (unit-tested in tests/test_mem_check.py)

def parse_meminfo(text):
    """`dumpsys meminfo <pkg>` to kB values: pss, java, native, graphics, code, stack, other, system, swap.

    Reads the App Summary (Android 8+); without one, the TOTAL row and the Native Heap / Dalvik Heap rows of the table.
    Missing values are absent from the result. Empty dict when the process isn't running.
    """
    out = {}
    m = re.search(r"TOTAL PSS:\s+(\d+)", text) or re.search(r"^\s*TOTAL:\s+(\d+)", text, re.M)
    if m:
        out["pss"] = int(m.group(1))
    m = re.search(r"TOTAL SWAP PSS:\s+(\d+)", text) or re.search(r"TOTAL SWAP \(KB\):\s+(\d+)", text)
    if m:
        out["swap"] = int(m.group(1))
    for label, key in SUMMARY_ROWS.items():
        m = re.search(r"^\s*" + re.escape(label) + r":\s+(\d+)", text, re.M)
        if m:
            out[key] = int(m.group(1))
    if "pss" not in out:
        m = re.search(r"^\s*TOTAL\s+(\d+)\s", text, re.M)
        if m:
            out["pss"] = int(m.group(1))
    if "native" not in out:
        m = re.search(r"^\s*Native Heap\s+(\d+)\s", text, re.M)
        if m:
            out["native"] = int(m.group(1))
    if "java" not in out:
        m = re.search(r"^\s*Dalvik Heap\s+(\d+)\s", text, re.M)
        if m:
            out["java"] = int(m.group(1))
    return out


def parse_gfxinfo(text):
    """`dumpsys gfxinfo <pkg>` to {frames, janky, janky_pct, p90, p95, p99} (ms for percentiles); the first block only."""
    out = {}
    m = re.search(r"Total frames rendered:\s*(\d+)", text)
    if m:
        out["frames"] = int(m.group(1))
    m = re.search(r"^\s*Janky frames:\s*(\d+)\s*\(([\d.]+)%\)", text, re.M)
    if m:
        out["janky"] = int(m.group(1))
        out["janky_pct"] = float(m.group(2))
    for p in (90, 95, 99):
        m = re.search(r"^\s*%dth percentile:\s*(\d+)ms" % p, text, re.M)
        if m:
            out["p%d" % p] = int(m.group(1))
    return out


def parse_pid(text):
    """`pidof <pkg>` (or `ps -o PID`) output to the first pid, or None."""
    for word in text.split():
        if word.isdigit():
            return int(word)
    return None


def parse_proc_stat(text):
    """/proc/<pid>/stat to utime + stime in clock ticks (fields 14 and 15; the name may contain spaces)."""
    tail = text[text.rfind(")") + 1:].split()
    if len(tail) < 13:
        return None
    return int(tail[11]) + int(tail[12])


def cpu_percent(ticks_before, ticks_after, seconds, hz=100):
    """CPU used between two /proc/<pid>/stat reads, as a percentage of one core (top's %CPU)."""
    if ticks_before is None or ticks_after is None or seconds <= 0 or ticks_after < ticks_before:
        return None
    return round(100.0 * (ticks_after - ticks_before) / hz / seconds, 1)


def parse_proc_meminfo(text):
    """/proc/meminfo to {total, available} in kB."""
    out = {}
    for key, name in (("MemTotal", "total"), ("MemAvailable", "available")):
        m = re.search(r"^%s:\s+(\d+) kB" % key, text, re.M)
        if m:
            out[name] = int(m.group(1))
    return out


def parse_psi(text):
    """/proc/pressure/memory to the 'some' avg10 (percent of time stalled on memory), or None."""
    m = re.search(r"^some avg10=([\d.]+)", text, re.M)
    return float(m.group(1)) if m else None


def mb(kb):
    return "%.1f" % (kb / 1024.0) if kb is not None else "?"


def describe(sample):
    """One line per sample, in MB and percent."""
    m, g = sample.get("mem", {}), sample.get("gfx", {})
    parts = ["t+%4ds" % sample.get("t", 0), "PSS %s MB" % mb(m.get("pss")), "native %s" % mb(m.get("native")),
             "java %s" % mb(m.get("java")), "graphics %s" % mb(m.get("graphics"))]
    if sample.get("cpu") is not None:
        parts.append("CPU %s%%" % sample["cpu"])
    if g.get("frames") is not None:
        parts.append("frames %d janky %s%%" % (g["frames"], g.get("janky_pct", "?")))
    if sample.get("available") is not None:
        parts.append("TV free %s MB" % mb(sample["available"]))
    if sample.get("psi") is not None:
        parts.append("pressure %s" % sample["psi"])
    return "  ".join(parts)


def summarise(samples):
    """Over all samples: PSS min/median/max, the largest native/java/graphics, mean and peak CPU, overall jank."""
    def values(key):
        return [s["mem"][key] for s in samples if key in s.get("mem", {})]
    out = {"samples": len(samples)}
    pss = values("pss")
    if pss:
        out["pss_min"], out["pss_median"], out["pss_max"] = min(pss), int(statistics.median(pss)), max(pss)
    for key in ("native", "java", "graphics"):
        v = values(key)
        if v:
            out[key + "_max"] = max(v)
    cpu = [s["cpu"] for s in samples if s.get("cpu") is not None]
    if cpu:
        out["cpu_mean"], out["cpu_max"] = round(sum(cpu) / len(cpu), 1), max(cpu)
    frames = sum(s["gfx"].get("frames", 0) for s in samples if s.get("gfx"))
    janky = sum(s["gfx"].get("janky", 0) for s in samples if s.get("gfx"))
    out["frames"], out["janky"] = frames, janky
    out["janky_pct"] = round(100.0 * janky / frames, 1) if frames else None
    free = [s["available"] for s in samples if s.get("available") is not None]
    if free:
        out["free_min"] = min(free)
    psi = [s["psi"] for s in samples if s.get("psi") is not None]
    if psi:
        out["psi_max"] = max(psi)
    return out


def report(summary):
    lines = ["Summary over %d samples:" % summary["samples"]]
    if "pss_max" in summary:
        lines.append("  PSS %s / %s / %s MB (min / median / max)" % (mb(summary["pss_min"]), mb(summary["pss_median"]), mb(summary["pss_max"])))
    lines.append("  largest native heap %s MB, Java heap %s MB, graphics %s MB" % (
        mb(summary.get("native_max")), mb(summary.get("java_max")), mb(summary.get("graphics_max"))))
    if "cpu_mean" in summary:
        lines.append("  CPU %s%% on average, %s%% at most (one core = 100%%)" % (summary["cpu_mean"], summary["cpu_max"]))
    if summary.get("frames"):
        lines.append("  %d frames drawn, %d janky (%s%%)" % (summary["frames"], summary["janky"], summary["janky_pct"]))
    else:
        lines.append("  no frames drawn (nothing on screen changed, or the app isn't on screen)")
    if "free_min" in summary:
        lines.append("  TV free memory at least %s MB" % mb(summary["free_min"]))
    if "psi_max" in summary:
        lines.append("  memory pressure (some avg10) at most %s" % summary["psi_max"])
    return "\n".join(lines)


# --- the TV (through the adb binary; nothing about the connection is printed)

def adb(*args, timeout=60):
    cmd = [os.environ.get("ADB", "adb"), "shell", *args]
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        return None
    return p.stdout if p.returncode == 0 else None


def choose_package(listing, wanted=None):
    """The package to measure: the one asked for, or the first Unified TV build installed (pm list packages output)."""
    installed = {line.split(":", 1)[1].strip() for line in listing.splitlines() if line.startswith("package:")}
    if wanted:
        return wanted if wanted in installed else None
    for p in CANDIDATES:
        if p in installed:
            return p
    return None


def main(argv=None):
    ap = argparse.ArgumentParser(description="Unified TV memory, CPU and frame check over adb.")
    ap.add_argument("--package", help="package to measure (default: the Unified TV build installed)")
    ap.add_argument("--minutes", type=float, default=3.0)
    ap.add_argument("--every", type=float, default=15.0, help="seconds between samples")
    a = ap.parse_args(argv)
    listing = adb("pm", "list", "packages", "io.unifiedtv")
    if listing is None:
        sys.exit("adb could not reach the TV. Connect it first (adb connect, then accept the prompt on the TV).")
    pkg = choose_package(listing, a.package)
    if not pkg:
        sys.exit("No Unified TV build is installed on this TV" + (" under that name." if a.package else "."))
    model = (adb("getprop", "ro.product.model") or "?").strip()
    release = (adb("getprop", "ro.build.version.release") or "?").strip()
    build = (adb("getprop", "ro.build.id") or "?").strip()
    print("TV: %s, Android %s (%s). Measuring %s for %.0f min, every %.0f s." % (model, release, build, pkg, a.minutes, a.every))
    total = (parse_proc_meminfo(adb("cat", "/proc/meminfo") or "").get("total"))
    if total:
        print("TV memory: %s MB in total." % mb(total))
    adb("dumpsys", "gfxinfo", pkg, "reset")
    samples, start = [], time.time()
    pid = parse_pid(adb("pidof", pkg) or "")
    ticks, ticks_at = (parse_proc_stat(adb("cat", "/proc/%d/stat" % pid) or "") if pid else None), time.time()
    while True:
        time.sleep(a.every)
        now = time.time()
        sample = {"t": int(now - start), "mem": parse_meminfo(adb("dumpsys", "meminfo", pkg) or ""),
                  "gfx": parse_gfxinfo(adb("dumpsys", "gfxinfo", pkg, "reset") or "")}
        new_pid = parse_pid(adb("pidof", pkg) or "")
        new_ticks = parse_proc_stat(adb("cat", "/proc/%d/stat" % new_pid) or "") if new_pid else None
        sample["cpu"] = cpu_percent(ticks, new_ticks, now - ticks_at) if new_pid == pid else None
        if new_pid != pid:
            print("  (the app restarted or stopped)")
        pid, ticks, ticks_at = new_pid, new_ticks, now
        sample["available"] = parse_proc_meminfo(adb("cat", "/proc/meminfo") or "").get("available")
        sample["psi"] = parse_psi(adb("cat", "/proc/pressure/memory") or "")
        samples.append(sample)
        print(describe(sample), flush=True)
        if now - start >= a.minutes * 60:
            break
    print(report(summarise(samples)))


if __name__ == "__main__":
    main()
