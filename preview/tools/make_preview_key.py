#!/usr/bin/env python3
"""Create the Unified TV preview's durable signing key, once, on the owner's own computer (spec P05-01).

Android only installs an update signed with the same key as the app already on the TV. This makes that key:
  - a PKCS12 keystore with a long random password, in a private folder outside any git checkout
    (default: ~/UnifiedTV-signing), with keystore.base64.txt beside it;
  - the password, kept APART from the key (v3.4 module 08, "password separation"): password.txt and local-build.txt
    (settings for signing on this computer) go in a second private folder, the key folder's name plus "-password"
    (default: ~/UnifiedTV-signing-password). Put the password in a password manager; back the two up separately;
  - the certificate's SHA-256, which every release publishes in SIGNING.txt.
The key and its password stay on the owner's own computer and backups: never in the repository and never on
GitHub, not even as encrypted repository secrets (owner decision, 25 October 2026). An existing key is never
overwritten: losing or replacing it means every TV would need a fresh install.

Usage:
    make_preview_key.py [--dir FOLDER]

Exit status: 0 created, 1 refused or failed.
"""
import base64
import glob
import os
import secrets
import shutil
import subprocess
import sys

ALIAS = "unifiedtv-preview"
KEYSTORE = "unifiedtv-preview.p12"
SECRETS = ("UNIFIEDTV_PREVIEW_KEYSTORE_BASE64", "UNIFIEDTV_PREVIEW_KEYSTORE_PASSWORD",
           "UNIFIEDTV_PREVIEW_KEY_ALIAS", "UNIFIEDTV_PREVIEW_KEY_PASSWORD")


def keytool():
    """keytool from PATH, JAVA_HOME or Android Studio's bundled Java, or None."""
    found = shutil.which("keytool")
    if found:
        return found
    exe = "keytool.exe" if os.name == "nt" else "keytool"
    places = [os.path.join(os.environ.get("JAVA_HOME", ""), "bin")]
    if os.name == "nt":
        for root in (os.environ.get("ProgramFiles", r"C:\Program Files"), os.environ.get("LOCALAPPDATA", "")):
            places.append(os.path.join(root, "Android", "Android Studio", "jbr", "bin"))
            places.append(os.path.join(root, "Programs", "Android Studio", "jbr", "bin"))
        # A JDK installed a moment ago is often not on this window's PATH yet.
        program_files = os.environ.get("ProgramFiles", r"C:\Program Files")
        for vendor in ("Microsoft", "Eclipse Adoptium", "Java", "Zulu", "Amazon Corretto"):
            places += sorted(glob.glob(os.path.join(program_files, vendor, "*", "bin")), reverse=True)
    else:
        places += ["/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin",
                   os.path.expanduser("~/android-studio/jbr/bin"), "/opt/android-studio/jbr/bin"]
    for place in places:
        path = os.path.join(place, exe)
        if place and os.path.isfile(path):
            return path
    return None


def inside_git(folder):
    """True when the folder is inside a git checkout, where a key could be committed by mistake."""
    path = os.path.abspath(folder)
    while True:
        if os.path.exists(os.path.join(path, ".git")):
            return True
        parent = os.path.dirname(path)
        if parent == path:
            return False
        path = parent


def private_write(path, text):
    """Write a file only this user can read."""
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def fingerprint(tool, keystore, env):
    out = subprocess.run([tool, "-list", "-v", "-keystore", keystore, "-storetype", "PKCS12", "-alias", ALIAS,
                          "-storepass:env", "UNIFIEDTV_KEYTOOL_PASS"], capture_output=True, text=True, env=env, check=True).stdout
    for line in out.splitlines():
        if "SHA256:" in line.replace(" ", ""):
            return line.split(":", 1)[1].strip()
    return ""


def password_folder(folder):
    """Where the password lives: never beside the key, so one lost or copied backup doesn't give away both."""
    return os.path.abspath(folder).rstrip(os.sep) + "-password"


def create(folder, tool):
    """Make the key in a new private folder. Returns (keystore path, the four secret values, SHA-256)."""
    if inside_git(folder):
        raise SystemExit(f"Refused: {folder} is inside a git checkout. Choose a folder outside any repository.")
    keystore = os.path.join(folder, KEYSTORE)
    if os.path.exists(keystore):
        raise SystemExit(f"Refused: a key already exists at {keystore}. It is never replaced: "
                         "a new key would stop every TV accepting updates.")
    words = password_folder(folder)
    if inside_git(words):
        raise SystemExit(f"Refused: {words} is inside a git checkout. Choose a folder outside any repository.")
    if os.path.exists(os.path.join(words, "password.txt")):
        raise SystemExit(f"Refused: {words} already holds a password; it belongs to an earlier key and is never replaced.")
    os.makedirs(folder, mode=0o700, exist_ok=True)
    os.makedirs(words, mode=0o700, exist_ok=True)
    # PKCS12 uses one password for the store and the key; it reaches keytool through the environment, not argv.
    password = secrets.token_urlsafe(30)
    env = dict(os.environ, UNIFIEDTV_KEYTOOL_PASS=password)
    subprocess.run([tool, "-genkeypair", "-keystore", keystore, "-storetype", "PKCS12", "-alias", ALIAS,
                    "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000", "-dname", "CN=Unified TV Preview",
                    "-storepass:env", "UNIFIEDTV_KEYTOOL_PASS", "-keypass:env", "UNIFIEDTV_KEYTOOL_PASS"],
                   env=env, check=True, capture_output=True)
    try:
        os.chmod(keystore, 0o600)
    except OSError:
        pass
    with open(keystore, "rb") as f:
        encoded = base64.b64encode(f.read()).decode("ascii")
    values = dict(zip(SECRETS, (encoded, password, ALIAS, password)))
    sha = fingerprint(tool, keystore, env)
    private_write(os.path.join(folder, "keystore.base64.txt"), encoded + "\n")
    private_write(os.path.join(folder, "README.txt"),
                  "Unified TV launcher signing key (keystore only). The password is NOT here: it is in\n"
                  f"{words} and should be in your password manager. Keep the two backups apart.\n"
                  f"Certificate SHA-256: {sha}\n")
    private_write(os.path.join(words, "password.txt"), password + "\n")
    private_write(os.path.join(words, "local-build.txt"),
                  "Unified TV launcher signing key: settings for signing on THIS computer only.\n"
                  "Never put the key or this password on GitHub (not even as repository secrets), in a chat or anywhere online.\n\n"
                  f"Certificate SHA-256 (public, safe to share): {sha}\n\n"
                  "Local builds (preview/build.sh), as environment variables:\n"
                  f"  UNIFIEDTV_PREVIEW_KEYSTORE = {os.path.abspath(keystore)}\n"
                  f"  UNIFIEDTV_PREVIEW_KEYSTORE_PASSWORD = {password}\n"
                  f"  UNIFIEDTV_PREVIEW_KEY_ALIAS = {ALIAS}\n"
                  f"  UNIFIEDTV_PREVIEW_KEY_PASSWORD = {password}\n")
    return keystore, values, sha


def existing(folder):
    """The four secret values of a key this tool made earlier, or None when there is no such key."""
    try:
        with open(os.path.join(folder, "keystore.base64.txt"), encoding="utf-8") as f:
            encoded = f.read().strip()
        # The password lives in its own folder; keys made before that change kept it beside the keystore.
        for place in (password_folder(folder), folder):
            path = os.path.join(place, "password.txt")
            if os.path.isfile(path):
                with open(path, encoding="utf-8") as f:
                    password = f.read().strip()
                break
        else:
            return None
    except OSError:
        return None
    if not encoded or not password or not os.path.isfile(os.path.join(folder, KEYSTORE)):
        return None
    return dict(zip(SECRETS, (encoded, password, ALIAS, password)))


def main(argv):
    folder = os.path.expanduser("~/UnifiedTV-signing")
    args = list(argv)
    while args:
        flag = args.pop(0)
        if flag == "--dir" and args:
            folder = os.path.expanduser(args.pop(0))
        else:
            print(__doc__)
            return 1
    tool = keytool()
    if not tool:
        print("keytool wasn't found. Install Android Studio or a Java JDK (17 or later), then run this again.")
        return 1
    keystore, values, sha = create(folder, tool)
    print(f"Created the signing key in {folder}")
    print(f"Its password is in a separate folder: {password_folder(folder)}")
    print(f"Certificate SHA-256 (public, safe to share): {sha}")
    print("Now: put the password (password.txt) in your password manager; back up the key folder to a USB stick "
          "kept elsewhere. Keep the key and the password apart. Never put either on GitHub or anywhere online.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
