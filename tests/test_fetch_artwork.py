"""preview/tools/fetch_artwork.py: only museum-marked public-domain, correctly titled, landscape works are kept (OD-03).
No network: the museum APIs are replaced by fixtures.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("fetch_artwork", ROOT / "preview" / "tools" / "fetch_artwork.py")
fa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fa)


def jpeg(width, height):
    """A minimal JPEG header with a SOF0 segment, enough for jpeg_size()."""
    return (b"\xff\xd8" + b"\xff\xe0\x00\x10" + b"JFIF\x00" + b"\x00" * 9
            + b"\xff\xc0\x00\x11\x08" + height.to_bytes(2, "big") + width.to_bytes(2, "big") + b"\x03" + b"\x00" * 9 + b"\xff\xd9")


class Rules(unittest.TestCase):
    def test_only_public_domain_matching_titles_with_an_image_are_accepted(self):
        good = {"title": "Under the Wave off Kanagawa", "public_domain": True, "image": "https://example.org/a.jpg"}
        self.assertIsNone(fa.refusal(good, "Kanagawa"))
        self.assertIn("public domain", fa.refusal(dict(good, public_domain=False), "Kanagawa"))
        self.assertIn("doesn't match", fa.refusal(good, "Water Lilies"))
        self.assertIn("image", fa.refusal(dict(good, image=""), "Kanagawa"))

    def test_jpeg_size_and_landscape(self):
        self.assertEqual((4000, 2600), fa.jpeg_size(jpeg(4000, 2600)))
        self.assertTrue(fa.landscape(4000, 2600))
        self.assertFalse(fa.landscape(2600, 4000))
        self.assertFalse(fa.landscape(0, 0))

    def test_met_and_aic_records_are_read_from_the_museums_own_fields(self):
        met = {"title": "Wheat Field with Cypresses", "artistDisplayName": "Vincent van Gogh", "objectDate": "1889",
               "isPublicDomain": True, "primaryImage": "https://images.metmuseum.org/x.jpg", "objectURL": "https://www.metmuseum.org/art/collection/search/436535"}
        r = fa.describe("met", 436535, lambda url: json.dumps(met).encode())
        self.assertTrue(r["public_domain"]); self.assertEqual("Vincent van Gogh", r["artist"])
        aic = {"data": {"title": "Water Lilies", "artist_display": "Claude Monet\nFrench, 1840-1926", "date_display": "1906",
                        "is_public_domain": True, "image_id": "abc"}}
        r = fa.describe("aic", 16568, lambda url: json.dumps(aic).encode())
        self.assertEqual("Claude Monet", r["artist"]); self.assertTrue(r["image"].startswith("https://www.artic.edu/iiif/2/abc/"))
        aic["data"]["is_public_domain"] = False
        self.assertFalse(fa.describe("aic", 16568, lambda url: json.dumps(aic).encode())["public_domain"])


class Run(unittest.TestCase):
    def test_a_run_keeps_good_works_skips_the_rest_and_writes_records(self):
        met_ok = {"title": "The Great Wave off Kanagawa", "artistDisplayName": "Hokusai", "objectDate": "ca. 1830",
                  "isPublicDomain": True, "primaryImage": "https://images.example/wave.jpg", "objectURL": "https://example/met/45434"}
        def fetch(url):
            if "objects/45434" in url: return json.dumps(met_ok).encode()
            if "objects/436535" in url: return json.dumps(dict(met_ok, title="Something else")).encode()
            if url == "https://images.example/wave.jpg": return jpeg(3000, 2000)
            if "artic.edu/api" in url:
                return json.dumps({"data": {"title": "Portrait", "is_public_domain": False, "image_id": None}}).encode()
            raise AssertionError("unexpected fetch " + url)
        shrink, fa.shrink = fa.shrink, lambda data: (data, False)   # the fixture is a header only, not a decodable picture
        self.addCleanup(setattr, fa, "shrink", shrink)
        with tempfile.TemporaryDirectory() as out:
            self.assertEqual(0, fa.main(["--out", out], fetch=fetch))
            files = sorted(os.listdir(out))
            self.assertIn("hokusai-great-wave.jpg", files)
            self.assertNotIn("van-gogh-wheat-field-cypresses.jpg", files)
            catalogue = json.load(open(os.path.join(out, "catalogue.json")))
            self.assertEqual(["wallpaper/hokusai-great-wave.jpg"], [c["file"] for c in catalogue])
            self.assertIn("CC0", catalogue[0]["credit"])
            rows = open(os.path.join(out, "ARTWORK-rows.md")).read()
            self.assertIn("retrieved", rows); self.assertIn("https://example/met/45434", rows)

    def test_nothing_kept_is_a_failure(self):
        with tempfile.TemporaryDirectory() as out:
            self.assertEqual(1, fa.main(["--out", out], fetch=lambda url: (_ for _ in ()).throw(OSError("offline"))))


if __name__ == "__main__":
    unittest.main()
