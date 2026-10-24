#!/usr/bin/env python3
"""Create the Unified TV preview's durable signing key, once, on the owner's own computer (spec P05-01).

Android only installs an update signed with the same key as the app already on the TV. This makes that key:
  - a PKCS12 keystore with a long random password, in a private folder outside any git checkout
    (default: ~/UnifiedTV-signing);
  - keystore.base64.txt and secrets.txt beside it, holding the four values GitHub needs;
  - the certificate's SHA-256, which every release publishes in SIGNING.txt.
With --github, it also stores the four values as repository secrets through the GitHub CLI (`gh`, already
signed in), so tagged releases are signed with this key. Run with --github again later to re-send an existing
key's secrets; that never makes a new key. Nothing is ever written into the repository, and an
existing key is never overwritten: losing or replacing it means every TV would need a fresh install.

Usage:
    make_preview_key.py [--dir FOLDER] [--github] [--repo OWNER/NAME]

Exit status: 0 created, 1 refused or failed.
"""
import base64
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


def create(folder, tool):
    """Make the key in a new private folder. Returns (keystore path, the four secret values, SHA-256)."""
    if inside_git(folder):
        raise SystemExit(f"Refused: {folder} is inside a git checkout. Choose a folder outside any repository.")
    keystore = os.path.join(folder, KEYSTORE)
    if os.path.exists(keystore):
        raise SystemExit(f"Refused: a key already exists at {keystore}. It is never replaced: "
                         "a new key would stop every TV accepting updates.")
    os.makedirs(folder, mode=0o700, exist_ok=True)
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
    private_write(os.path.join(folder, "password.txt"), password + "\n")
    private_write(os.path.join(folder, "secrets.txt"),
                  "Unified TV preview signing key. Keep this folder private and backed up (a password manager is ideal).\n"
                  "Never commit it. Losing it means every TV needs a fresh install of the preview.\n\n"
                  f"Certificate SHA-256: {sha}\n\n"
                  "GitHub repository secrets (Settings > Secrets and variables > Actions):\n"
                  f"  UNIFIEDTV_PREVIEW_KEYSTORE_BASE64 = the contents of keystore.base64.txt\n"
                  f"  UNIFIEDTV_PREVIEW_KEYSTORE_PASSWORD = {password}\n"
                  f"  UNIFIEDTV_PREVIEW_KEY_ALIAS = {ALIAS}\n"
                  f"  UNIFIEDTV_PREVIEW_KEY_PASSWORD = {password}\n\n"
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
        with open(os.path.join(folder, "password.txt"), encoding="utf-8") as f:
            password = f.read().strip()
    except OSError:
        return None
    if not encoded or not password or not os.path.isfile(os.path.join(folder, KEYSTORE)):
        return None
    return dict(zip(SECRETS, (encoded, password, ALIAS, password)))


def repo_from_git():
    try:
        url = subprocess.run(["git", "remote", "get-url", "origin"], capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None
    for prefix in ("https://github.com/", "git@github.com:"):
        if url.startswith(prefix):
            return url[len(prefix):].removesuffix(".git")
    return None


def upload(values, repo, run=subprocess.run):
    """Store each value as a repository secret with the GitHub CLI; values go through stdin, never argv."""
    if not shutil.which("gh"):
        raise SystemExit("The GitHub CLI (gh) isn't installed. Add the four secrets by hand from secrets.txt.")
    for name in SECRETS:
        run(["gh", "secret", "set", name, "--repo", repo], input=values[name], text=True, check=True, capture_output=True)


def main(argv):
    folder = os.path.expanduser("~/UnifiedTV-signing")
    github, repo = "--github" in argv, None
    args = [a for a in argv if a != "--github"]
    while args:
        flag = args.pop(0)
        if flag in ("--dir", "--repo") and args:
            value = args.pop(0)
            if flag == "--dir":
                folder = os.path.expanduser(value)
            else:
                repo = value
        else:
            print(__doc__)
            return 1
    tool = keytool()
    if not tool and not (github and existing(folder)):
        print("keytool wasn't found. Install Android Studio or a Java JDK (17 or later), then run this again.")
        return 1
    values = existing(folder) if github else None
    if values:
        print(f"Using the existing key in {folder}; no new key was made.")
    else:
        keystore, values, sha = create(folder, tool)
        print(f"Created the preview signing key in {folder}")
        print(f"Certificate SHA-256: {sha}")
        print("Back up that folder now (a password manager is ideal). Never commit it.")
    if github:
        repo = repo or repo_from_git()
        if not repo:
            print("Couldn't tell which GitHub repository to use: pass --repo OWNER/NAME.")
            return 1
        upload(values, repo)
        print(f"Stored the four UNIFIEDTV_PREVIEW_* secrets in {repo}. Tagged releases are now signed with this key.")
    else:
        print("Next: add the four GitHub secrets listed in secrets.txt, or run this again with --github "
              "to have the GitHub CLI do it.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
