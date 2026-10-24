#!/usr/bin/env python3
"""Safe install or update of the Unified TV preview APK on an Android TV, over ADB (spec P05-01).

Before installing, it reads what is on the TV and compares it with the APK on this computer:
  - the installed preview's versionName/versionCode and its signing certificate (from the installed APK itself,
    or, if that can't be read, the certificate hash `dumpsys package` prints);
  - the APK's own package, versions and certificate SHA-256 (apksigner when available, otherwise the APK's
    signature block read here);
  - which app owns the factory Home, before and after.
It refuses to install when the certificates differ, because Android would only accept that APK after the
configured preview had been uninstalled, losing its settings, and this tool never uninstalls or clears data.
It refuses a downgrade unless --allow-downgrade is passed. It installs with `pm install -r`, launches the app,
and writes an evidence record to preview-evidence/ (git-ignored) with no IP addresses, serials or account data.

Set TV_HOST (and TV_PORT if needed) as for tools/tv.py. Usage:
    install_preview.py APK [--allow-downgrade] [--check]
        --check            read and compare only: nothing is pushed, installed or launched
        --allow-downgrade  install an APK with a lower versionCode (pm install -r -d); data is kept

Exit status: 0 installed (or --check found nothing in the way), 1 refused or failed, 3 factory Home changed.
"""
import glob
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import zipfile

PREVIEW_PACKAGES = ("io.unifiedtv.preview", "io.unifiedtv.preview.devicecheck",
                    "io.unifiedtv.launcher", "io.unifiedtv.launcher.devicecheck")
REMOTE_APK = "/data/local/tmp/unifiedtv-preview-install.apk"
EVIDENCE = "preview-evidence"
HOME_QUERY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
# Shell commands this tool must never send, whatever the arguments: losing a configured preview's data is the
# failure P05-01 exists to prevent.
FORBIDDEN = re.compile(r"\b(uninstall|pm\s+clear|clear-data|reset|wipe|disable-user|disable)\b")

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
# Signature scheme block IDs, newest first: v3.1, v3, v2.
SIG_BLOCK_IDS = (0x1B93AD61, 0xF05368C0, 0x7109871A)
# android:versionCode, android:versionName as resource IDs, for compiled manifests without attribute names.
ATTR_IDS = {0x0101021B: "versionCode", 0x0101021C: "versionName"}

REFUSE_DIFFERENT_KEY = (
    "The APK is signed with a different key from the preview already on the TV. Android only accepts it after the "
    "installed preview is uninstalled, which deletes its saved connections, folders and settings. This tool never "
    "does that. Build with the same key (UNIFIEDTV_PREVIEW_* settings, see docs/PREVIEW_0_5_RELEASE.md), or use the "
    "separate deviceCheck build, which installs alongside with its own data.")


# --- pure functions (unit-tested in tests/test_preview_install.py; nothing here talks to a TV)

def check_command(cmd):
    """Raise before a forbidden command is sent to the TV."""
    if FORBIDDEN.search(cmd):
        raise RuntimeError(f"refusing to send {cmd!r}: this tool never uninstalls, clears or disables anything")
    return cmd


def java_array_hash(data):
    """Java's Arrays.hashCode(byte[]) as Integer.toHexString prints it: dumpsys shows a signature this way."""
    h = 1
    for b in data:
        h = (31 * h + (b - 256 if b > 127 else b)) & 0xFFFFFFFF
    return format(h, "x")


def parse_dumpsys(text, package):
    """`dumpsys package PACKAGE` to a dict, or None when the package is not installed.

    Keys: version_code (int or None), version_name, signature_hashes (list of hex strings), scheme,
    first_install, last_update."""
    lines = text.splitlines()
    start = None
    for i, line in enumerate(lines):
        if line.strip().startswith(f"Package [{package}] ("):
            start = i
            break
    if start is None:
        return None
    info = {"version_code": None, "version_name": "", "signature_hashes": [], "scheme": None,
            "first_install": "", "last_update": ""}
    indent = len(lines[start]) - len(lines[start].lstrip())
    for line in lines[start + 1:]:
        stripped = line.strip()
        if stripped and len(line) - len(line.lstrip()) <= indent:
            break   # the next package or section
        m = re.search(r"\bversionCode=(\d+)", stripped)
        if m and info["version_code"] is None:
            info["version_code"] = int(m.group(1))
        if stripped.startswith("versionName=") and not info["version_name"]:
            info["version_name"] = stripped.split("=", 1)[1]
        m = re.search(r"version:(\d+), signatures:\[([^\]]*)\]", stripped)
        if m and not info["signature_hashes"]:
            info["scheme"] = int(m.group(1))
            info["signature_hashes"] = [s.strip() for s in m.group(2).split(",") if s.strip()]
        if stripped.startswith("firstInstallTime=") and not info["first_install"]:
            info["first_install"] = stripped.split("=", 1)[1]
        if stripped.startswith("lastUpdateTime=") and not info["last_update"]:
            info["last_update"] = stripped.split("=", 1)[1]
    return info


def parse_resolve(text):
    """`cmd package resolve-activity --brief` output to the component on its last line ('' when none)."""
    lines = [line.strip() for line in text.strip().splitlines() if line.strip()]
    if not lines or "No activity found" in lines[-1]:
        return ""
    return lines[-1] if "/" in lines[-1] else ""


def parse_pm_path(text):
    """`pm path PACKAGE` to the base APK path on the TV, or ''."""
    paths = [line.split(":", 1)[1].strip() for line in text.splitlines() if line.startswith("package:")]
    for p in paths:
        if p.endswith("/base.apk"):
            return p
    return paths[0] if paths else ""


def parse_apksigner(text):
    """First signer's certificate SHA-256 from `apksigner verify --print-certs`, lower-case hex, or ''."""
    m = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", text)
    return m.group(1).replace(":", "").lower() if m else ""


def _prefixed(buf, pos):
    """One uint32-length-prefixed field of an APK signature block: (bytes, next position)."""
    if pos + 4 > len(buf):
        raise ValueError("truncated signature block")
    (n,) = struct.unpack_from("<I", buf, pos)
    if pos + 4 + n > len(buf):
        raise ValueError("truncated signature block")
    return buf[pos + 4:pos + 4 + n], pos + 4 + n


def signing_block_certificate(data):
    """DER of the first signer's certificate from an APK's v3.1/v3/v2 signature block, or None.

    Layout: the block sits just before the ZIP central directory and ends with its size and the magic;
    inside, uint64-length ID/value pairs; each scheme value is a sequence of signers, each starting with
    its signed data: digests, then certificates."""
    eocd = data.rfind(b"PK\x05\x06", max(0, len(data) - 65557))
    if eocd < 0 or eocd + 20 > len(data):
        return None
    (cd_offset,) = struct.unpack_from("<I", data, eocd + 16)
    if cd_offset < 32 or data[cd_offset - 16:cd_offset] != APK_SIG_BLOCK_MAGIC:
        return None
    (size,) = struct.unpack_from("<Q", data, cd_offset - 24)
    start = cd_offset - size - 8
    if start < 0 or struct.unpack_from("<Q", data, start)[0] != size:
        return None
    pairs, pos, end = {}, start + 8, cd_offset - 24
    while pos + 12 <= end:
        (n,) = struct.unpack_from("<Q", data, pos)
        if n < 4 or pos + 8 + n > end:
            return None
        (block_id,) = struct.unpack_from("<I", data, pos + 8)
        pairs[block_id] = data[pos + 12:pos + 8 + n]
        pos += 8 + n
    for block_id in SIG_BLOCK_IDS:
        if block_id not in pairs:
            continue
        signers, _ = _prefixed(pairs[block_id], 0)
        signer, _ = _prefixed(signers, 0)
        signed, _ = _prefixed(signer, 0)
        _digests, p = _prefixed(signed, 0)
        certs, _ = _prefixed(signed, p)
        cert, _ = _prefixed(certs, 0)
        return bytes(cert)
    return None


def v1_certificate(zf):
    """DER of the signer certificate from a v1 (JAR) signature, or None. Needs the cryptography package."""
    names = [n for n in zf.namelist() if re.fullmatch(r"META-INF/[^/]+\.(RSA|DSA|EC)", n, re.I)]
    if not names:
        return None
    try:
        from cryptography.hazmat.primitives.serialization import Encoding, pkcs7
    except ImportError:
        return None
    certs = pkcs7.load_der_pkcs7_certificates(zf.read(names[0]))
    return certs[0].public_bytes(Encoding.DER) if certs else None


def _string_pool(buf, pos):
    """Strings from an Android binary XML string pool chunk at pos."""
    _t, header_size, _size, count, _styles, flags, strings_start = struct.unpack_from("<HHIIIII", buf, pos)
    utf8 = bool(flags & 0x100)
    offsets = struct.unpack_from(f"<{count}I", buf, pos + header_size)
    base = pos + strings_start
    out = []
    for off in offsets:
        p = base + off
        if utf8:
            n = buf[p]
            p += 2 if n & 0x80 else 1
            n = buf[p]
            if n & 0x80:
                n = ((n & 0x7F) << 8) | buf[p + 1]
                p += 1
            p += 1
            out.append(buf[p:p + n].decode("utf-8", "replace"))
        else:
            (n,) = struct.unpack_from("<H", buf, p)
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", buf, p)[0]
                p += 2
            out.append(buf[p:p + 2 * n].decode("utf-16-le", "replace"))
    return out


def manifest_identity(axml):
    """package, versionCode and versionName from a compiled AndroidManifest.xml (Android binary XML)."""
    strings, res_ids, pos = [], [], 8
    while pos + 8 <= len(axml):
        chunk_type, header_size, size = struct.unpack_from("<HHI", axml, pos)
        if size < 8:
            break
        if chunk_type == 0x0001:
            strings = _string_pool(axml, pos)
        elif chunk_type == 0x0180:
            res_ids = list(struct.unpack_from(f"<{(size - header_size) // 4}I", axml, pos + header_size))
        elif chunk_type == 0x0102:
            name = struct.unpack_from("<I", axml, pos + 20)[0]
            if strings[name] == "manifest":
                attr_start, attr_size, attr_count = struct.unpack_from("<HHH", axml, pos + 24)
                found = {}
                for i in range(attr_count):
                    a = pos + 16 + attr_start + i * attr_size
                    _ns, aname, raw, _vs, _r, dtype, value = struct.unpack_from("<IIIHBBI", axml, a)
                    key = ATTR_IDS.get(res_ids[aname]) if aname < len(res_ids) else None
                    key = key or (strings[aname] if aname < len(strings) else "")
                    if dtype == 0x03 or raw != 0xFFFFFFFF:
                        found[key] = strings[raw] if raw < len(strings) else ""
                    else:
                        found[key] = value
                code = found.get("versionCode")
                return {"package": str(found.get("package", "")),
                        "version_code": int(code) if code not in (None, "") else None,
                        "version_name": str(found.get("versionName", ""))}
        pos += size
    raise ValueError("no <manifest> element in AndroidManifest.xml")


def decide(installed, apk, allow_downgrade=False):
    """('install', why) or ('refuse', why) from the TV's state and the APK's.

    installed: None when the package isn't on the TV, otherwise a dict with version_code and cert_match
    (True/False, or None when the installed certificate could not be read). apk: dict with version_code."""
    if installed is None:
        return "install", "not installed on this TV: fresh install"
    if installed.get("cert_match") is None:
        return "refuse", "could not read the installed preview's signing certificate, so an update can't be shown to be safe"
    if not installed["cert_match"]:
        return "refuse", REFUSE_DIFFERENT_KEY
    have, want = installed.get("version_code"), apk.get("version_code")
    if have is None or want is None:
        return "refuse", "could not read both versionCodes, so a downgrade can't be ruled out"
    if want < have:
        if not allow_downgrade:
            return "refuse", (f"the APK (versionCode {want}) is older than the installed preview ({have}); "
                              "pass --allow-downgrade only if you mean to go back")
        return "install", f"downgrade from versionCode {have} to {want}, same key, data kept (--allow-downgrade)"
    if want == have:
        return "install", f"same versionCode {have}, same key: reinstall keeps data"
    return "install", f"update from versionCode {have} to {want}, same key: data kept"


def install_command(allow_downgrade):
    return check_command(f"pm install -r {'-d ' if allow_downgrade else ''}{REMOTE_APK}")


def evidence(apk, before, after, decision, result, home_before, home_after, tv_info, method, launched):
    """The evidence record: hashes, versions and outcomes only; no addresses, serials or account data."""
    return {
        "tool": "preview/tools/install_preview.py",
        "time": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "tv": tv_info,
        "apk": {"file": os.path.basename(apk["path"]), "sha256": apk["sha256"], "package": apk["package"],
                "version_code": apk["version_code"], "version_name": apk["version_name"],
                "cert_sha256_prefix": apk["cert_sha256"][:16]},
        "before": _installed_summary(before),
        "after": _installed_summary(after) if after is not None else None,   # None: nothing was installed
        "signature_check": method,
        "decision": decision[0], "reason": decision[1],
        "data_retained": (None if not (before and after) else before.get("first_install") == after.get("first_install")),
        "factory_home_before": home_before, "factory_home_after": home_after,
        "factory_home_unchanged": "yes" if home_before == home_after and home_before else "no",
        "launched": launched,
        "result": result,
    }


def _installed_summary(info):
    if not info:
        return {"installed": False}
    return {"installed": True, "version_code": info.get("version_code"), "version_name": info.get("version_name"),
            "cert_sha256_prefix": (info.get("cert_sha256") or "")[:16] or None,
            "first_install": info.get("first_install")}


# --- local APK

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def find_build_tool(name):
    found = shutil.which(name)
    if found:
        return found
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    candidates = sorted(glob.glob(os.path.join(home, "build-tools", "*", name))) if home else []
    return candidates[-1] if candidates else ""


def apk_certificate(path):
    """(certificate SHA-256 hex, DER or None, method). Empty digest when nothing could read it."""
    der = None
    with open(path, "rb") as f:
        data = f.read()
    try:
        der = signing_block_certificate(data)
    except (ValueError, struct.error, IndexError):
        der = None
    if der is None:
        try:
            with zipfile.ZipFile(path) as zf:
                der = v1_certificate(zf)
        except (zipfile.BadZipFile, ValueError):
            der = None
    parsed = hashlib.sha256(der).hexdigest() if der else ""
    apksigner = find_build_tool("apksigner")
    if apksigner:
        r = subprocess.run([apksigner, "verify", "--print-certs", path], capture_output=True, text=True)
        signed = parse_apksigner(r.stdout) if r.returncode == 0 else ""
        if signed:
            if parsed and parsed != signed:
                raise SystemExit("apksigner and the signature block disagree about the APK's certificate; stopping.")
            return signed, der, "apksigner"
    return parsed, der, "signature block" if parsed else ""


def apk_identity(path):
    with zipfile.ZipFile(path) as zf:
        return manifest_identity(zf.read("AndroidManifest.xml"))


# --- TV access

def tv():
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools"))
    import tv as tvmod  # noqa: E402  (the inherited ADB client; TV_HOST/TV_PORT)
    return tvmod.connect()


def sh(dev, cmd, timeout=120.0):
    return dev.shell(check_command(cmd), read_timeout_s=timeout)


def installed_state(dev, package, apk_cert_sha, apk_der, workdir):
    """dumpsys info plus cert_sha256/cert_match, or None when the package isn't installed."""
    info = parse_dumpsys(sh(dev, f"dumpsys package {package}"), package)
    if info is None:
        return None, ""
    info["cert_sha256"], method = "", ""
    remote = parse_pm_path(sh(dev, f"pm path {package}"))
    if remote:
        local = os.path.join(workdir, "installed.apk")
        try:
            dev.pull(remote, local, transport_timeout_s=300.0, read_timeout_s=300.0)
            info["cert_sha256"] = apk_certificate(local)[0]
            method = "installed APK certificate SHA-256"
        except Exception as error:  # noqa: BLE001  (a TV that refuses the pull still has the dumpsys hash)
            print(f"Could not read the installed APK ({error}); comparing dumpsys signature hashes instead.")
    if info["cert_sha256"]:
        info["cert_match"] = info["cert_sha256"] == apk_cert_sha
    elif apk_der is not None and info["signature_hashes"]:
        info["cert_match"] = info["signature_hashes"] == [java_array_hash(apk_der)]
        method = "dumpsys signature hash"
    else:
        info["cert_match"] = None
    return info, method


def push(dev, local, sha):
    """Copy the APK to the TV and verify it by SHA-256; falls back to tools/b64push.py. Returns the device."""
    try:
        dev.push(local, REMOTE_APK, transport_timeout_s=300.0, read_timeout_s=300.0)
        if sha in sh(dev, f"sha256sum {REMOTE_APK}"):
            return dev
        print("The copy on the TV didn't match; retrying in chunks.")
    except Exception as error:  # noqa: BLE001
        print(f"Direct push failed ({error}); retrying in chunks.")
    dev.close()
    tools = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools")
    r = subprocess.run([sys.executable, "-I", os.path.join(tools, "b64push.py"), local, REMOTE_APK])
    dev = tv()
    if r.returncode or sha not in sh(dev, f"sha256sum {REMOTE_APK}"):
        sh(dev, f"rm -f {REMOTE_APK}")
        dev.close()
        raise SystemExit("Could not copy the APK to the TV intact. Nothing was installed.")
    return dev


def launch(dev, package):
    for category in ("android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER"):
        component = parse_resolve(sh(dev, f"cmd package resolve-activity --brief -a android.intent.action.MAIN -c {category} {package}"))
        if component:
            out = sh(dev, f"am start -W -n {component}")
            return "Status: ok" in out and "Error" not in out
    return False


def save(record):
    os.makedirs(EVIDENCE, exist_ok=True)
    stem = os.path.join(EVIDENCE, "install-" + time.strftime("%Y%m%d-%H%M%S"))
    path, n = stem + ".json", 1
    while os.path.exists(path):   # never overwrite an earlier record from the same second
        path, n = f"{stem}-{n}.json", n + 1
    with open(path, "x") as f:
        json.dump(record, f, indent=2)
    return path


def main(argv):
    paths = [a for a in argv if not a.startswith("--")]
    unknown = [a for a in argv if a.startswith("--") and a not in ("--allow-downgrade", "--check")]
    if len(paths) != 1 or unknown or not os.path.isfile(paths[0]):
        print(__doc__)
        return 2
    allow_downgrade, check_only = "--allow-downgrade" in argv, "--check" in argv
    path = paths[0]
    ident = apk_identity(path)
    if ident["package"] not in PREVIEW_PACKAGES:
        raise SystemExit(f"{ident['package'] or 'This APK'} is not the Unified TV preview; this tool installs only "
                         + " or ".join(PREVIEW_PACKAGES))
    cert, der, local_method = apk_certificate(path)
    if not cert:
        raise SystemExit("Could not read the APK's signing certificate: no apksigner (Android build-tools) and no "
                         "readable signature block. Nothing was checked or installed.")
    apk = dict(ident, path=path, sha256=sha256_file(path), cert_sha256=cert)
    print(f"APK {ident['package']} {ident['version_name']} ({ident['version_code']}), "
          f"certificate SHA-256 {cert[:16]}... ({local_method})")

    dev = tv()
    workdir = tempfile.mkdtemp(prefix="unifiedtv-install-")
    after, launched, result, status = None, False, "", 1
    try:
        tv_info = {"model": sh(dev, "getprop ro.product.model").strip(),
                   "android": sh(dev, "getprop ro.build.version.release").strip(),
                   "build": sh(dev, "getprop ro.build.display.id").strip()}
        home_before = parse_resolve(sh(dev, HOME_QUERY))
        print("Factory Home now:", home_before or "(could not be read)")
        before, method = installed_state(dev, ident["package"], cert, der, workdir)
        if before:
            print(f"Installed: {before['version_name']} ({before['version_code']}), certificate "
                  f"{'matches' if before['cert_match'] else 'DIFFERS' if before['cert_match'] is False else 'unreadable'}"
                  f" ({method})")
        decision = decide(before, ident, allow_downgrade)
        if not home_before:
            decision = ("refuse", "could not read which app owns Home, so a change could not be detected")
        print(("Will install: " if decision[0] == "install" else "Refused: ") + decision[1])
        home_after = home_before
        if decision[0] == "refuse":
            result = "refused: " + decision[1]
        elif check_only:
            result, status = "checked only (--check): nothing installed", 0
        else:
            dev = push(dev, path, apk["sha256"])
            out = sh(dev, install_command(allow_downgrade) + f"; rm -f {REMOTE_APK}", timeout=300.0)
            print(out.strip())
            after = parse_dumpsys(sh(dev, f"dumpsys package {ident['package']}"), ident["package"])
            home_after = parse_resolve(sh(dev, HOME_QUERY))
            if "Success" not in out:
                result = "failed: pm install did not report Success; the installed preview was left as it was"
            elif not after or after["version_code"] != ident["version_code"]:
                result = "failed: the TV does not report the new versionCode after install"
            else:
                if der is not None and after["signature_hashes"] and after["signature_hashes"] != [java_array_hash(der)]:
                    result = "failed: the installed signature does not match the APK after install"
                else:
                    launched = launch(dev, ident["package"])
                    result, status = "installed" + ("" if launched else "; launch not confirmed"), 0
            if home_after != home_before:
                print(f"FACTORY HOME CHANGED: was {home_before}, now {home_after or '(none)'}. "
                      "Check the TV's Home setting before using it further.", file=sys.stderr)
                result, status = result + "; FACTORY HOME CHANGED", 3
        record = evidence(apk, before, after, decision, result, home_before, home_after, tv_info,
                          method or local_method, launched)
        print(result)
        print("Evidence:", save(record))
        return status
    finally:
        shutil.rmtree(workdir, ignore_errors=True)
        dev.close()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
