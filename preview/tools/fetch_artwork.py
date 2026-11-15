#!/usr/bin/env python3
"""Fetch the curated Wallpaper artwork on the owner's PC, keeping only images the museum itself marks public domain.

Owner decision OD-03 (25 October 2026): the default Wallpaper is curated artwork that is free and explicitly licensed for
this use, bundled in the app (docs/design/ARTWORK.md). This tool runs on a computer, never on a TV. For each work in
CURATED it asks the museum's own open-access API and:
  - refuses it unless the museum marks that object public domain (The Met: isPublicDomain; Art Institute of Chicago:
    is_public_domain), both published by the museums as CC0;
  - refuses it if the title doesn't match what is expected (a guard against a wrong object number);
  - refuses portrait-format images (the Wallpaper is 16:9);
  - downloads the image once, records the source URL, licence, retrieval date and SHA-256, and, if Pillow is installed,
    saves a 1920-pixel-wide JPEG for the app.
It writes the files, a catalogue.json for preview/app/src/main/assets/wallpaper/ and ARTWORK-rows.md for
docs/design/ARTWORK.md into the output folder. Nothing is sent anywhere else.

Usage:
    fetch_artwork.py [--out FOLDER]        (default: ~/UnifiedTV-artwork)
Exit status: 0 when at least one work was kept, 1 otherwise.
"""
import datetime
import hashlib
import io
import json
import os
import re
import sys
import urllib.request

# (museum, object id, words the title must contain, file stem). Calm, landscape-format, few or no close faces.
CURATED = [
    ("met", 45434, "Kanagawa", "hokusai-great-wave"),
    ("met", 436535, "Wheat Field with Cypresses", "van-gogh-wheat-field-cypresses"),
    ("aic", 16568, "Water Lilies", "monet-water-lilies"),
    ("aic", 27992, "La Grande Jatte", "seurat-la-grande-jatte"),
    ("aic", 28560, "Bedroom", "van-gogh-bedroom"),
    ("aic", 24645, "Kanagawa", "hokusai-under-the-wave-aic"),
]
USER_AGENT = "UnifiedTV-artwork-fetch/1.0 (owner-run; public-domain images only)"
LICENCE = {"met": "Public domain, The Metropolitan Museum of Art Open Access (CC0)",
           "aic": "Public domain, Art Institute of Chicago open access (CC0)"}


def get(url, limit=60_000_000):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "AIC-User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read(limit + 1)
    if len(data) > limit:
        raise ValueError("response too large")
    return data


def describe(museum, number, fetch=get):
    """The museum's own record, reduced to what we need: title, artist, public-domain flag, image URL, page URL."""
    if museum == "met":
        m = json.loads(fetch(f"https://collectionapi.metmuseum.org/public/collection/v1/objects/{number}"))
        return {"title": m.get("title", ""), "artist": m.get("artistDisplayName", ""), "date": m.get("objectDate", ""),
                "public_domain": m.get("isPublicDomain") is True, "image": m.get("primaryImage", ""),
                "page": m.get("objectURL", "")}
    if museum == "aic":
        d = json.loads(fetch(f"https://api.artic.edu/api/v1/artworks/{number}"
                             "?fields=id,title,artist_display,date_display,is_public_domain,image_id"))["data"]
        image = f"https://www.artic.edu/iiif/2/{d['image_id']}/full/1686,/0/default.jpg" if d.get("image_id") else ""
        return {"title": d.get("title", ""), "artist": (d.get("artist_display") or "").split("\n")[0],
                "date": d.get("date_display", ""), "public_domain": d.get("is_public_domain") is True, "image": image,
                "page": f"https://www.artic.edu/artworks/{number}"}
    raise ValueError("unknown museum")


def refusal(record, expected):
    """Why a work can't be used, or None when it can."""
    if not record["public_domain"]:
        return "not marked public domain by the museum"
    if expected.lower() not in record["title"].lower():
        return f"title '{record['title']}' doesn't match '{expected}' (wrong object number?)"
    if not re.match(r"^https://", record["image"]):
        return "no downloadable image"
    return None


def landscape(width, height):
    return width > 0 and height > 0 and width / height >= 1.2


def jpeg_size(data):
    """(width, height) of a JPEG from its header, without Pillow."""
    i = 2
    while i < len(data) - 9:
        if data[i] != 0xFF:
            i += 1
            continue
        marker, length = data[i + 1], int.from_bytes(data[i + 2:i + 4], "big")
        if marker in (0xC0, 0xC1, 0xC2):
            return int.from_bytes(data[i + 7:i + 9], "big"), int.from_bytes(data[i + 5:i + 7], "big")
        i += 2 + length
    return 0, 0


def shrink(data):
    """A 1920-pixel-wide JPEG if Pillow is available; otherwise the original bytes."""
    try:
        from PIL import Image
    except ImportError:
        return data, False
    img = Image.open(io.BytesIO(data)).convert("RGB")
    if img.width > 1920:
        img = img.resize((1920, round(img.height * 1920 / img.width)), Image.LANCZOS)
    out = io.BytesIO()
    img.save(out, "JPEG", quality=88, optimize=True, progressive=True)
    return out.getvalue(), True


def entry(stem, record, museum, original_sha, app_sha, size, retrieved):
    credit = f"{record['artist']}, {record['title']}" + (f" ({record['date']})" if record["date"] else "")
    catalogue = {"file": f"wallpaper/{stem}.jpg", "title": record["title"], "credit": credit + ". " + LICENCE[museum]}
    row = (f"| `assets/wallpaper/{stem}.jpg` | {credit} | {LICENCE[museum]}; [source]({record['page']}); retrieved {retrieved} "
           f"| Not required (CC0); credited in the app | {size[0]} × {size[1]} | original `{original_sha[:8]}…{original_sha[-6:]}`, "
           f"app `{app_sha[:8]}…{app_sha[-6:]}` | None yet |")
    return catalogue, row


def main(argv, fetch=get):
    out = os.path.expanduser("~/UnifiedTV-artwork")
    if "--out" in argv:
        out = os.path.expanduser(argv[argv.index("--out") + 1])
    os.makedirs(out, exist_ok=True)
    retrieved = datetime.date.today().isoformat()
    catalogue, rows, kept = [], [], 0
    for museum, number, expected, stem in CURATED:
        try:
            record = describe(museum, number, fetch)
            why = refusal(record, expected)
            if why:
                print(f"skip {museum} {number}: {why}")
                continue
            data = fetch(record["image"])
            width, height = jpeg_size(data)
            if not landscape(width, height):
                print(f"skip {museum} {number}: not landscape ({width} × {height})")
                continue
            app, resized = shrink(data)
            with open(os.path.join(out, stem + ".jpg"), "wb") as f:
                f.write(app)
            c, r = entry(stem, record, museum, hashlib.sha256(data).hexdigest(), hashlib.sha256(app).hexdigest(),
                         jpeg_size(app), retrieved)
            catalogue.append(c)
            rows.append(r)
            kept += 1
            print(f"kept {stem}.jpg: {record['title']}" + ("" if resized else " (original size: install Pillow to shrink it)"))
        except Exception as e:  # one failed work never stops the rest
            print(f"skip {museum} {number}: {type(e).__name__}: {e}")
    with open(os.path.join(out, "catalogue.json"), "w", encoding="utf-8") as f:
        json.dump(catalogue, f, indent=2, ensure_ascii=False)
    with open(os.path.join(out, "ARTWORK-rows.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    print(f"{kept} of {len(CURATED)} works kept in {out}. Send the .jpg files, catalogue.json and ARTWORK-rows.md back.")
    return 0 if kept else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
