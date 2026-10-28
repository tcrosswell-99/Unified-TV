"""preview/tools/home_check.py: a fresh install of the preview can never take over the TV's Home button (spec 29.3).

Fixtures are shaped like `aapt2 dump xmltree --file AndroidManifest.xml` output.
Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("home_check", ROOT / "preview" / "tools" / "home_check.py")
hc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hc)

QUERIES = """N: android=http://schemas.android.com/apk/res/android (line=2)
  E: manifest (line=2)
    E: queries (line=11)
      E: intent (line=14)
        E: action (line=14)
          A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.intent.action.MAIN" (Raw: "android.intent.action.MAIN")
        E: category (line=14)
          A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.intent.category.HOME" (Raw: "android.intent.category.HOME")
    E: application (line=16)
"""


def alias(enabled_line):
    return QUERIES + f"""      E: activity-alias (line=50)
        A: http://schemas.android.com/apk/res/android:name(0x01010003)="io.unifiedtv.preview.HomeEntry" (Raw: ".HomeEntry")
{enabled_line}
        A: http://schemas.android.com/apk/res/android:exported(0x01010010)=true
        E: intent-filter (line=52)
          E: action (line=53)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.intent.action.MAIN" (Raw: "android.intent.action.MAIN")
          E: category (line=54)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.intent.category.HOME" (Raw: "android.intent.category.HOME")
"""


class HomeCheck(unittest.TestCase):
    def test_visibility_query_alone_is_safe(self):
        self.assertEqual([], hc.problems(QUERIES))

    def test_switched_off_alias_is_safe(self):
        self.assertEqual([], hc.problems(alias("        A: http://schemas.android.com/apk/res/android:enabled(0x0101000e)=false")))
        self.assertEqual([], hc.problems(alias("        A: http://schemas.android.com/apk/res/android:enabled(0x0101000e)=(type 0x12)0x0")))

    def test_the_preview_may_not_carry_any_home_entry(self):
        self.assertEqual([], hc.problems(QUERIES, no_home=True))
        self.assertTrue(hc.problems(alias("        A: http://schemas.android.com/apk/res/android:enabled(0x0101000e)=false"), no_home=True))

    def test_alias_shipped_switched_on_fails(self):
        self.assertTrue(hc.problems(alias("        A: http://schemas.android.com/apk/res/android:enabled(0x0101000e)=true")))
        self.assertTrue(hc.problems(alias("")))

    def test_home_on_an_activity_fails(self):
        activity = QUERIES + """      E: activity (line=36)
        A: http://schemas.android.com/apk/res/android:name(0x01010003)="io.unifiedtv.preview.UnifiedPreviewActivity"
        E: intent-filter (line=38)
          E: category (line=40)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.intent.category.HOME" (Raw: "android.intent.category.HOME")
"""
        self.assertTrue(hc.problems(activity))


if __name__ == "__main__":
    unittest.main()
