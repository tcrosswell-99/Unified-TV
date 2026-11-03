"""preview/tools/mem_check.py: the owner's memory, CPU and frame check reads dumpsys and /proc text correctly.
Pure parsing only; nothing here talks to a TV. The meminfo sample carries the owner's TCL figures (25 October 2026:
TOTAL PSS 67153, Native Heap 35364, Java Heap 7692); the other numbers are made up.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("mem_check", ROOT / "preview" / "tools" / "mem_check.py")
mc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mc)

# Android 11's layout (ActivityThread dumpMemInfoTable): the table, then the App Summary.
MEMINFO = """Applications Memory Usage (in Kilobytes):
Uptime: 81234567 Realtime: 81234567

** MEMINFO in pid 4321 [io.unifiedtv.launcher.devicecheck] **
                   Pss  Private  Private  SwapPss      Rss     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty    Total     Size    Alloc     Free
                ------   ------   ------   ------   ------   ------   ------   ------
  Native Heap    35364    35300        0      120    37024    49152    41213     7938
  Dalvik Heap     5120     5072        0       60     9876    12288     6144     6144
 Dalvik Other     1544     1540        0        0     2312
        Stack      468      468        0        0      476
       Ashmem       16        0        0        0      520
    Other dev       28        0       28        0      312
     .so mmap     2961      312      680        0    15420
    .apk mmap     4302        0     3904        0    12000
    .dex mmap      911        4      880        0     2100
    .oat mmap      202        0        0        0     4100
    .art mmap     2860     2508        0       40    11232
   Other mmap      164        8       60        0     1000
   EGL mtrack     4224     4224        0        0     4224
    GL mtrack     2068     2068        0        0     2068
      Unknown      821      816        0        3     1300
        TOTAL    67153    52248     5552      223   111312    61440    47357    14082

 App Summary
                       Pss(KB)                        Rss(KB)
                        ------                         ------
           Java Heap:     7692                          21108
         Native Heap:    35364                          37024
                Code:     9068                          33620
               Stack:      468                            476
            Graphics:     6292                           6292
       Private Other:     2916
              System:     5353
             Unknown:                                    2792

           TOTAL PSS:    67153            TOTAL RSS:   111312       TOTAL SWAP PSS:      223

 Objects
               Views:      142         ViewRootImpl:        1
"""

# Older layout without an App Summary: only the table's rows.
MEMINFO_OLD = """** MEMINFO in pid 99 [io.unifiedtv.preview] **
                 Pss  Private  Private  Swapped     Heap     Heap     Heap
               Total    Dirty    Clean    Dirty     Size    Alloc     Free
  Native Heap    20000    19900        0        0    30000    25000     5000
  Dalvik Heap     6000     5900        0        0    12000     9000     3000
        TOTAL    41000    30000     2000        0    42000    34000     8000
"""

GFXINFO = """Applications Graphics Acceleration Info:
Uptime: 81234567 Realtime: 81234567

** Graphics info for pid 4321 [io.unifiedtv.launcher.devicecheck] **

Stats since: 81000000000ns
Total frames rendered: 412
Janky frames: 13 (3.16%)
50th percentile: 9ms
90th percentile: 17ms
95th percentile: 21ms
99th percentile: 40ms
Number Missed Vsync: 2
Number High input latency: 0
Number Slow UI thread: 5

Profile data in ms:

\tio.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.UnifiedPreviewActivity/android.view.ViewRootImpl@1234 (visibility=0)
Stats since: 81000000000ns
Total frames rendered: 400
Janky frames: 12 (3.00%)
"""

STAT = "4321 (unifiedtv.launc) S 300 300 0 0 -1 1077952832 22000 0 570 0 1200 340 0 0 20 0 40 0 8000 2000000000 17000 18446744073709551615"


class MemCheckParsingTest(unittest.TestCase):
    def test_the_owners_tcl_summary(self):
        m = mc.parse_meminfo(MEMINFO)
        self.assertEqual(m["pss"], 67153)
        self.assertEqual(m["native"], 35364)
        self.assertEqual(m["java"], 7692)
        self.assertEqual(m["graphics"], 6292)
        self.assertEqual(m["code"], 9068)
        self.assertEqual(m["swap"], 223)

    def test_an_older_table_without_a_summary(self):
        m = mc.parse_meminfo(MEMINFO_OLD)
        self.assertEqual((m["pss"], m["native"], m["java"]), (41000, 20000, 6000))

    def test_no_process_is_empty(self):
        self.assertEqual(mc.parse_meminfo("No process found for: io.unifiedtv.preview\n"), {})

    def test_gfxinfo_reads_the_process_block(self):
        g = mc.parse_gfxinfo(GFXINFO)
        self.assertEqual((g["frames"], g["janky"], g["janky_pct"]), (412, 13, 3.16))
        self.assertEqual((g["p90"], g["p95"], g["p99"]), (17, 21, 40))
        self.assertEqual(mc.parse_gfxinfo(""), {})

    def test_cpu_from_proc_stat(self):
        before = mc.parse_proc_stat(STAT)
        self.assertEqual(before, 1540)
        after = mc.parse_proc_stat(STAT.replace(" 1200 340 ", " 1500 400 "))
        self.assertEqual(mc.cpu_percent(before, after, 10.0), 36.0)
        self.assertIsNone(mc.cpu_percent(before, None, 10.0))
        self.assertIsNone(mc.cpu_percent(after, before, 10.0), "a restarted process is not negative CPU")
        self.assertEqual(mc.parse_proc_stat("12 (name with ) parens) S " + " ".join(["0"] * 10) + " 7 8 0"), 15)

    def test_system_memory_and_pressure(self):
        self.assertEqual(mc.parse_proc_meminfo("MemTotal:        2097152 kB\nMemFree:  10000 kB\nMemAvailable:     514000 kB\n"),
                         {"total": 2097152, "available": 514000})
        self.assertEqual(mc.parse_psi("some avg10=26.12 avg60=20.00 avg300=9.50 total=123\nfull avg10=3.00 avg60=1 avg300=0 total=4\n"), 26.12)
        self.assertIsNone(mc.parse_psi(""))
        self.assertEqual(mc.parse_pid("4321\n"), 4321)
        self.assertIsNone(mc.parse_pid(""))

    def test_package_choice(self):
        listing = "package:io.unifiedtv.preview\npackage:io.unifiedtv.launcher.devicecheck\n"
        self.assertEqual(mc.choose_package(listing), "io.unifiedtv.launcher.devicecheck")
        self.assertEqual(mc.choose_package(listing, "io.unifiedtv.preview"), "io.unifiedtv.preview")
        self.assertIsNone(mc.choose_package(listing, "io.unifiedtv.launcher"))
        self.assertIsNone(mc.choose_package(""))

    def test_summary_and_report(self):
        samples = [{"t": 15, "mem": mc.parse_meminfo(MEMINFO), "gfx": mc.parse_gfxinfo(GFXINFO), "cpu": 12.0, "available": 514000, "psi": 26.12},
                   {"t": 30, "mem": {"pss": 60000, "native": 30000}, "gfx": {"frames": 88, "janky": 2}, "cpu": 4.0, "available": 600000, "psi": 5.0}]
        s = mc.summarise(samples)
        self.assertEqual((s["pss_min"], s["pss_max"], s["native_max"]), (60000, 67153, 35364))
        self.assertEqual((s["cpu_mean"], s["cpu_max"]), (8.0, 12.0))
        self.assertEqual((s["frames"], s["janky"], s["janky_pct"]), (500, 15, 3.0))
        text = mc.report(s) + "\n" + "\n".join(mc.describe(x) for x in samples)
        self.assertIn("PSS 58.6 / 62.1 / 65.6 MB", text)
        self.assertIn("CPU 8.0% on average, 12.0% at most", text)
        self.assertIn("pressure 26.12", text)

    def test_no_serials_or_addresses_are_printed(self):
        source = (ROOT / "preview" / "tools" / "mem_check.py").read_text()
        for leak in ("adb devices", "ro.serialno", "getprop ro.boot.serialno", "ip addr", "wlan0", "get-serialno"):
            self.assertNotIn(leak, source)


if __name__ == "__main__":
    unittest.main()
