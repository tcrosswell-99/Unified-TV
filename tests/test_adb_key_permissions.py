"""tools/tv.py keeps its ADB private key readable only by the account that made it.

The key is a credential: a TV that has accepted it gives a shell to anyone holding it, with no
"Allow USB debugging?" prompt. adb_shell's keygen writes the file with a plain open(), so without
help it lands at 0666 & ~umask (0644 under the usual umask) and every other account on the machine
can read it. Google's own adb keeps ~/.android/adbkey at 0600.

keygen is stubbed here so the test needs no adb_shell and no TV: the stub writes the key exactly the
way the real one does (plain open(), no chmod), which is the behaviour tv.py has to make up for.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]

# Enough of adb_shell for `import tv` to succeed. keygen writes the key the way the real one does.
STUB_DEVICE = "class AdbDeviceTcp:\n    def __init__(self, *a, **k):\n        pass\n"
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, *a, **k):\n        pass\n"
STUB_KEYGEN = (
    "import os\n"
    "def keygen(filepath):\n"
    "    # adb_shell/auth/keygen.py: a plain open(), no chmod.\n"
    "    with open(filepath, 'wb') as f:\n"
    "        f.write(b'-----BEGIN PRIVATE KEY-----\\nstub\\n-----END PRIVATE KEY-----\\n')\n"
    "    with open(filepath + '.pub', 'wb') as f:\n"
    "        f.write(b'stub pub\\n')\n"
    "    # Not part of the real keygen: the mode the key had before tv.py could chmod it.\n"
    "    os.environ['STUB_MODE_AT_WRITE'] = oct(os.stat(filepath).st_mode & 0o777)\n"
)


@unittest.skipIf(os.name == "nt", "POSIX permission bits; the chmod is a no-op on Windows")
class AdbKeyPermissionsTest(unittest.TestCase):
    def make_key(self, prepare=None):
        """Run tv.get_signer() against a copy of tools/tv.py in a throwaway tree; return the key path.

        `prepare(tmp)` runs first, to lay out a key folder or a stock ~/.android key."""
        tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, tmp, ignore_errors=True)
        if prepare:
            prepare(tmp)

        tools = tmp / "tools"
        tools.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / "tools" / "tv.py", tools / "tv.py")

        stub = tmp / "stub" / "adb_shell"
        (stub / "auth").mkdir(parents=True)
        (stub / "__init__.py").write_text("")
        (stub / "adb_device.py").write_text(STUB_DEVICE)
        (stub / "auth" / "__init__.py").write_text("")
        (stub / "auth" / "keygen.py").write_text(STUB_KEYGEN)
        (stub / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER)

        # HOME points at the throwaway tree, so the user's real ~/.android/adbkey is never read or
        # touched by this test; only test_stock_adb_key_is_left_alone puts a stock key there.
        env = dict(os.environ, TV_HOST="127.0.0.1", HOME=str(tmp),
                   PYTHONPATH=os.pathsep.join([str(tmp / "stub"), str(tools)]))
        code = ("import os, tv; os.umask(0o022); tv.get_signer(); "
                "print(os.environ.get('STUB_MODE_AT_WRITE', '')); print(oct(os.umask(0o022)))")
        result = subprocess.run([sys.executable, "-c", code],
                                cwd=tmp, env=env, capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.mode_at_write, self.umask_after = (result.stdout.splitlines() + ["", ""])[:2]
        return tools / "adbkey" / "adbkey"

    def test_private_key_is_not_readable_by_group_or_others(self):
        key = self.make_key()
        self.assertTrue(key.exists(), "tv.py generated no key")
        mode = stat.S_IMODE(key.stat().st_mode)
        self.assertEqual(mode & 0o077, 0,
                         f"the ADB private key is {oct(mode)}: any other account on this machine can "
                         f"read it and take a shell on every TV that has accepted it")

    def test_key_directory_is_private(self):
        directory = self.make_key().parent
        mode = stat.S_IMODE(directory.stat().st_mode)
        self.assertEqual(mode & 0o077, 0, f"{directory.name}/ is {oct(mode)}, expected owner only")

    def test_key_is_private_from_the_moment_it_is_written(self):
        # Under the usual 022 umask, a plain open() gives 0644 until the chmod; the umask closes that
        # window, and is put back afterwards.
        self.make_key()
        self.assertEqual(self.mode_at_write, "0o600")
        self.assertEqual(self.umask_after, "0o22", "tv.py left the process umask changed")

    def test_existing_open_key_directory_is_repaired(self):
        def open_folder(tmp):
            folder = tmp / "tools" / "adbkey"
            folder.mkdir(parents=True)
            folder.chmod(0o755)
        directory = self.make_key(open_folder).parent
        mode = stat.S_IMODE(directory.stat().st_mode)
        self.assertEqual(mode & 0o077, 0, f"an existing {directory.name}/ stayed {oct(mode)}")

    def test_stock_adb_key_is_left_alone(self):
        # With ~/.android/adbkey present, tv.py reuses it; its permissions belong to the user's adb.
        def stock_key(tmp):
            android = tmp / ".android"
            android.mkdir()
            for name in ("adbkey", "adbkey.pub"):
                (android / name).write_text("stock\n")
                (android / name).chmod(0o644)
            android.chmod(0o755)
        key = self.make_key(stock_key)
        self.assertFalse(key.exists(), "a new key was generated although the stock one exists")
        android = key.parents[2] / ".android"
        self.assertEqual(stat.S_IMODE((android / "adbkey").stat().st_mode), 0o644)
        self.assertEqual(stat.S_IMODE(android.stat().st_mode), 0o755)


if __name__ == "__main__":
    unittest.main()
