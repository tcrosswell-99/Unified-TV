#!/usr/bin/env python3
"""Assemble the Unified TV DLNA discovery proof of concept into an UNSIGNED .wgt (a zip). Standard library only.

PROTOTYPE, separate package (UTVDLNAPOC), not the Unified TV app. The result,
samsung/poc-dlna/out/UnifiedTV-DLNA-POC-unsigned.wgt, is test-only: it installs on a TV only after the owner signs the
staged folder samsung/poc-dlna/out/UnifiedTV-DLNA-POC/ with their own Samsung certificate profile (README.md).
This script never reads, creates or expects a certificate.

Run from anywhere: python3 samsung/poc-dlna/tools/package.py
"""
import hashlib
import re
import shutil
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]          # samsung/poc-dlna/
REPO = HERE.parents[1]
OUT = HERE / "out"
STAGE = OUT / "UnifiedTV-DLNA-POC"
WGT = OUT / "UnifiedTV-DLNA-POC-unsigned.wgt"
SHIPPED = ["config.xml", "index.html", "css/poc.css", "js/report.js", "js/poc.js", "service/service.js"]
PRIVILEGES = {"http://tizen.org/privilege/internet", "http://tizen.org/privilege/application.launch"}
LOOPBACK_ORIGIN = "http://127.0.0.1:18731"
FIXED_TIME = (2026, 10, 25, 0, 0, 0)
NS = {"w": "http://www.w3.org/ns/widgets", "t": "http://tizen.org/ns/widgets"}


def fail(message):
    print("poc-package: " + message, file=sys.stderr)
    sys.exit(1)


def png(width, height, pixel):
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend(pixel(x, y))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b""))


def icon():
    """An original test icon: a dark field with three amber dots (a probe), 512 x 423."""
    def pixel(x, y):
        for cx in (176, 256, 336):
            if (x - cx) ** 2 + (y - 211) ** 2 <= 30 ** 2:
                return (242, 193, 78)
        return (13, 18, 26)
    return png(512, 423, pixel)


def check_config(path):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        fail(f"config.xml is not valid XML: {e}")
    found = {p.get("name") for p in root.findall("t:privilege", NS)}
    if found != PRIVILEGES:
        fail(f"privileges {sorted(found)} differ from the minimum {sorted(PRIVILEGES)}")
    app = root.find("t:application", NS)
    if app is None or app.get("package") != "UTVDLNAPOC" or app.get("id") != "UTVDLNAPOC.Probe":
        fail("the probe must keep its own package id UTVDLNAPOC and app id UTVDLNAPOC.Probe")
    services = root.findall("t:service", NS)
    if len(services) != 1 or services[0].get("id") != "UTVDLNAPOC.svc":
        fail("exactly one service, UTVDLNAPOC.svc, is expected")
    content = services[0].find("t:content", NS)
    if content is None or content.get("src") != "service/service.js":
        fail("the service must start service/service.js")
    access = [a.get("origin") for a in root.findall("w:access", NS)]
    if access != [LOOPBACK_ORIGIN]:
        fail(f"network access must be {LOOPBACK_ORIGIN} only, not {access}")
    csp = root.find("t:content-security-policy", NS)
    policy = csp.text if csp is not None else ""
    if "unsafe-eval" in policy or "unsafe-inline" in policy or f"connect-src {LOOPBACK_ORIGIN};" not in policy:
        fail("the Content-Security-Policy must allow connections to the loopback service only, with no unsafe-eval or unsafe-inline")


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    STAGE.mkdir(parents=True)
    check_config(HERE / "config.xml")
    for rel in SHIPPED:
        src = HERE / rel
        if not src.is_file():
            fail(f"missing {rel}")
        (STAGE / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, STAGE / rel)
    (STAGE / "icon.png").write_bytes(icon())

    for path in STAGE.rglob("*"):
        name = path.name.lower()
        if name == "author-signature.xml" or re.fullmatch(r"signature\d*\.xml", name) or path.suffix.lower() in (".p12", ".pfx", ".pem", ".key", ".jks"):
            fail(f"refusing to package signing material: {path.relative_to(STAGE)}")
    html = (STAGE / "index.html").read_text(encoding="utf-8")
    for ref in re.findall(r'(?:src|href)="([^"$:#]+)"', html):
        if not (STAGE / ref).is_file():
            fail(f"index.html refers to {ref}, which is not in the package")

    files = sorted(p for p in STAGE.rglob("*") if p.is_file())
    with zipfile.ZipFile(WGT, "w", zipfile.ZIP_DEFLATED) as z:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(STAGE).as_posix(), FIXED_TIME)
            info.compress_type = zipfile.ZIP_STORED if path.suffix.lower() == ".png" else zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, path.read_bytes())
    digest = hashlib.sha256(WGT.read_bytes()).hexdigest()
    (OUT / "SHA256SUMS.txt").write_text(f"{digest}  {WGT.name}\n", encoding="utf-8")
    version = ET.parse(HERE / "config.xml").getroot().get("version")
    (OUT / "manifest.txt").write_text(
        "Unified TV DLNA discovery proof of concept: UNSIGNED test package (prototype, not the Unified TV app)\n"
        f"package: UTVDLNAPOC (app UTVDLNAPOC.Probe, service UTVDLNAPOC.svc)\nversion: {version}\nfiles: {len(files)}\nsha256: {digest}\n"
        "status: test_passed_nonphysical at most; not installed or tested on the Frame\n"
        "signing: none. The owner signs samsung/poc-dlna/out/UnifiedTV-DLNA-POC/ with their own Samsung certificate profile\n",
        encoding="utf-8")
    print(f"poc-package: {WGT.relative_to(REPO)} ({WGT.stat().st_size // 1024} KB, {len(files)} files, sha256 {digest[:16]}...)")


if __name__ == "__main__":
    main()
