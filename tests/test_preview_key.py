"""preview/tools/make_preview_key.py: the preview's durable signing key (spec P05-01) is made once, privately,
outside any checkout, and is never replaced. Uses the local keytool when there is one; nothing here talks to
GitHub or a TV.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import base64
import importlib.util
import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("make_preview_key", ROOT / "preview" / "tools" / "make_preview_key.py")
mk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mk)

KEYTOOL = mk.keytool()


class Folder(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = os.path.join(self.tmp.name, "signing")

    def test_a_folder_inside_a_checkout_is_refused(self):
        with self.assertRaises(SystemExit):
            mk.create(str(ROOT / "preview" / "signing"), "keytool")
        self.assertFalse((ROOT / "preview" / "signing").exists())

    def test_a_key_made_here_is_read_back_and_nothing_else_is(self):
        self.assertIsNone(mk.existing(self.folder))
        os.makedirs(self.folder)
        for name, text in (("keystore.base64.txt", "QUJD\n"), ("password.txt", "secret\n"), (mk.KEYSTORE, "ABC")):
            Path(self.folder, name).write_text(text)
        self.assertEqual(mk.existing(self.folder), {
            "UNIFIEDTV_PREVIEW_KEYSTORE_BASE64": "QUJD", "UNIFIEDTV_PREVIEW_KEYSTORE_PASSWORD": "secret",
            "UNIFIEDTV_PREVIEW_KEY_ALIAS": mk.ALIAS, "UNIFIEDTV_PREVIEW_KEY_PASSWORD": "secret"})
        os.remove(os.path.join(self.folder, mk.KEYSTORE))
        self.assertIsNone(mk.existing(self.folder), "no keystore, no key")


class RealKey(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = os.path.join(self.tmp.name, "signing")

    def test_makes_a_private_usable_key_once(self):
        argv = []
        real = subprocess.run

        def watch(args, **kw):
            argv.append(" ".join(args))
            return real(args, **kw)
        with mock.patch.object(mk.subprocess, "run", side_effect=watch):
            keystore, values, sha = mk.create(self.folder, KEYTOOL)
        password = values["UNIFIEDTV_PREVIEW_KEYSTORE_PASSWORD"]
        self.assertGreaterEqual(len(password), 30)
        self.assertEqual(password, values["UNIFIEDTV_PREVIEW_KEY_PASSWORD"])
        self.assertFalse(any(password in a for a in argv), "the password never appears in a command line")
        self.assertRegex(sha, r"^([0-9A-F]{2}:){31}[0-9A-F]{2}$")
        with open(keystore, "rb") as f:
            self.assertEqual(base64.b64decode(values["UNIFIEDTV_PREVIEW_KEYSTORE_BASE64"]), f.read())
        self.assertEqual(mk.existing(self.folder), values)
        if os.name == "posix":
            for name in (mk.KEYSTORE, "keystore.base64.txt", "README.txt"):
                self.assertEqual(stat.S_IMODE(os.stat(os.path.join(self.folder, name)).st_mode), 0o600, name)
            for name in ("password.txt", "local-build.txt"):
                self.assertEqual(stat.S_IMODE(os.stat(os.path.join(mk.password_folder(self.folder), name)).st_mode), 0o600, name)
        # v3.4 module 08, password separation: nothing in the key folder holds the password.
        for name in os.listdir(self.folder):
            with open(os.path.join(self.folder, name), "rb") as f:
                self.assertNotIn(password.encode(), f.read(), name)
        listed = subprocess.run([KEYTOOL, "-list", "-keystore", keystore, "-storetype", "PKCS12", "-alias", mk.ALIAS,
                                 "-storepass:env", "PW"], env=dict(os.environ, PW=password), capture_output=True, text=True)
        self.assertEqual(listed.returncode, 0, listed.stderr)
        with self.assertRaises(SystemExit):
            mk.create(self.folder, KEYTOOL)

    def test_nothing_is_ever_sent_to_github(self):
        """Owner decision (25 October 2026): the key and password never leave the owner's computer."""
        source = Path(mk.__file__).read_text(encoding="utf-8")
        for forbidden in ('"gh"', "secret set", "--github"):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
