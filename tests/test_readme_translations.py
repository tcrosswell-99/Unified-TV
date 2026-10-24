"""The front page, the translations and the pages they translate link each other.

Unified TV: the translations here are not translations of Unified TV's README.md. README.ja.md,
README.vi.md and README.zh-CN.md translate the original CleanYourTV guide, kept as
README.cleanyourtv.md; README.pt-BR.md translates the CleanYourTV UI README, archived as
docs/upstream/CLEANYOURTV_UI_README.md. So each translation has to link both its original (the
English page it is a translation of) and README.md (the project a reader has actually landed in), the
translations of the same original have to link each other, and each original links its translations.
README.md itself links every translation, saying what each one translates.

The checks before the fork, kept below in that shape:

A translation is only useful if a reader can reach it. README.md is the page everyone lands on, and
each translation opens with a line that offers the other languages, so a language added to one of
them and not to the rest leaves readers of that language without a way through. Nothing read those
links before: README.vi.md was linked from the two other translations and from nowhere on
README.md.

Every translation is found by its file name, so a fourth one is covered without touching this test.
Nothing here reads the prose, so translating or rewording any page stays free.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import posixpath
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
FRONT = "README.md"
# A language tag (ja, vi, zh-CN, pt-BR), so README.cleanyourtv.md, the inherited English guide, is not
# mistaken for a translation.
TRANSLATION = re.compile(r"^README\.[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*\.md$")
# `[text](target)` and `href="target"`, the two shapes the language rows use.
LINK = re.compile(r"\]\(([^)\s]+)\)|href=\"([^\"]+)\"")


# Which English page each translation translates. A translation not named here is taken to be of the
# inherited CleanYourTV guide, so a new one is still covered without touching this test.
ORIGINALS = {"README.pt-BR.md": "docs/upstream/CLEANYOURTV_UI_README.md"}
DEFAULT_ORIGINAL = "README.cleanyourtv.md"


def original_of(name):
    return ORIGINALS.get(name, DEFAULT_ORIGINAL)


def translations():
    return sorted(p.name for p in ROOT.iterdir() if p.is_file() and TRANSLATION.match(p.name) and p.name != "README.cleanyourtv.md")


def links(text):
    """The local files a page links to, without anchors or query strings."""
    found = set()
    for inline, href in LINK.findall(text):
        target = (inline or href).split("#")[0].split("?")[0].strip()
        for prefix in ("https://github.com/Gathin23/cleanyourtv/blob/main/", "blob/main/"):
            if target.startswith(prefix):
                target = target[len(prefix):]
        if target.startswith("./"):
            target = target[2:]
        if target and "/" not in target and not target.startswith(("http", "mailto", "data:")):
            found.add(target)
    return found


def missing(needed, text):
    """The names in `needed` a page does not link to."""
    found = links(text)
    return [name for name in needed if name not in found]


def local_links(page, text=None):
    """Every local file a page links to, as a path from the repository root.

    Unlike links(), this keeps targets in other folders, resolved from the page's own folder, so
    docs/upstream/CLEANYOURTV_UI_README.md linking ../../README.pt-BR.md reads as README.pt-BR.md.
    """
    if text is None:
        text = (ROOT / page).read_text(encoding="utf-8")
    folder = posixpath.dirname(page)
    found = set()
    for inline, href in LINK.findall(text):
        target = (inline or href).split("#")[0].split("?")[0].strip()
        for prefix in ("https://github.com/Gathin23/cleanyourtv/blob/main/", "blob/main/"):
            if target.startswith(prefix):
                target = target[len(prefix):]
        if not target or target.startswith(("http", "mailto", "data:")):
            continue
        found.add(posixpath.normpath(posixpath.join(folder, target)))
    return found


def missing_from(needed, page, text=None):
    """The repository paths in `needed` that `page` does not link to."""
    found = local_links(page, text)
    return [name for name in needed if name not in found]


class ReadmeTranslationsTest(unittest.TestCase):
    def test_every_translation_is_found(self):
        # A regex that matches nothing would make the checks below pass for any set of pages.
        self.assertGreaterEqual(len(translations()), 3, "no README.<language>.md files were found")
        self.assertIn("README.vi.md", translations())

    def test_the_front_page_links_every_translation(self):
        text = (ROOT / FRONT).read_text(encoding="utf-8")
        self.assertEqual([], missing(translations(), text),
                         "README.md does not link every translation its readers can open")

    def test_the_front_page_links_every_original(self):
        text = (ROOT / FRONT).read_text(encoding="utf-8")
        originals = sorted({original_of(name) for name in translations()})
        self.assertEqual([], missing_from(originals, FRONT, text),
                         "README.md does not link the English pages its translations come from")

    def test_every_original_exists(self):
        for name in translations():
            with self.subTest(page=name):
                self.assertTrue((ROOT / original_of(name)).is_file(), original_of(name))

    def test_every_translation_links_the_front_page_and_its_original(self):
        for name in translations():
            with self.subTest(page=name):
                needed = [FRONT, original_of(name)]
                self.assertEqual([], missing_from(needed, name), f"{name} does not link {needed}")

    def test_every_translation_links_the_others_of_the_same_original(self):
        for name in translations():
            with self.subTest(page=name):
                siblings = [other for other in translations()
                            if other != name and original_of(other) == original_of(name)]
                self.assertEqual([], missing_from(siblings, name), f"{name} does not link {siblings}")

    def test_every_original_links_its_translations(self):
        for original in sorted({original_of(name) for name in translations()}):
            with self.subTest(page=original):
                own = [name for name in translations() if original_of(name) == original]
                self.assertEqual([], missing_from(own, original), f"{original} does not link {own}")


class LinkReaderTest(unittest.TestCase):
    """The reader above has to see the shapes the pages really use, and nothing else."""

    def test_markdown_and_html_links_are_both_read(self):
        text = "[中文](README.zh-CN.md) · <a href=\"README.ja.md\">日本語</a>"
        self.assertEqual({"README.zh-CN.md", "README.ja.md"}, links(text))

    def test_an_anchor_and_a_leading_dot_slash_are_dropped(self):
        self.assertEqual({"README.vi.md"}, links("[vi](./README.vi.md#start)"))

    def test_an_absolute_repository_link_counts(self):
        text = "[ja](https://github.com/Gathin23/cleanyourtv/blob/main/README.ja.md)"
        self.assertEqual({"README.ja.md"}, links(text))

    def test_external_links_are_not_files(self):
        self.assertEqual(set(), links("[site](https://cleanyour.tv) [mail](mailto:a@b) [#top](#top)"))

    def test_a_missing_language_is_reported(self):
        text = "[English](README.md) · [日本語](README.ja.md)"
        self.assertEqual(["README.vi.md"], missing([FRONT, "README.ja.md", "README.vi.md"], text))

    def test_a_link_from_a_subfolder_resolves_from_the_root(self):
        text = "[pt](../../README.pt-BR.md) [img](../ui/home.jpg)"
        self.assertEqual({"README.pt-BR.md", "docs/ui/home.jpg"},
                         local_links("docs/upstream/PAGE.md", text))
        self.assertEqual(["README.vi.md"],
                         missing_from(["README.pt-BR.md", "README.vi.md"], "docs/upstream/PAGE.md", text))

    def test_a_page_that_links_nothing_reports_everything(self):
        self.assertEqual([FRONT, "README.vi.md"], missing([FRONT, "README.vi.md"], "no links here"))


if __name__ == "__main__":
    unittest.main()
