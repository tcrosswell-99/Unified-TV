# Wallpaper artwork: sources, licences and attribution

The owner decided on 25 October 2026 (OD-03, OD-04):

- The default Wallpaper source is **curated artwork** that is free and **explicitly licensed for this use**.
- It is bundled in the app; displaying a wallpaper never depends on downloading anything.
- The depth clock uses **prepared foreground masks** for selected artwork only. The TCL never segments images itself; without a suitable mask the standard clock is shown.
- Synology photos are a separate, later task, whose access method (DLNA folders or the Synology Photos API) must be designed first.

Every image shipped as artwork has a row here **before** it is committed. No row, no image.

## What counts as "explicitly licensed for our use"

| Accepted | Not accepted |
| --- | --- |
| Public domain or CC0, stated by the source for that specific image (for example Metropolitan Museum Open Access with `isPublicDomain: true`, or Art Institute of Chicago CC0 images) | "Free to download" with no stated licence |
| CC BY, with the attribution recorded here and shown in Settings → Appearance → Wallpaper → About this picture | Any NonCommercial (NC) or NoDerivatives (ND) licence: the app crops, dims and masks images |
| Original images made for the project, or owner-supplied images the owner has authorised for public inclusion | Stock-site licences that forbid redistribution or bundling (Unsplash and Pexels images are not used for bundled artwork) |
| | The owner's design mockups and Apple or other marketing images (references only; see [REFERENCES.md](REFERENCES.md)) |

## Bundled today

The Wallpaper shows exactly the pictures listed in `preview/app/src/main/assets/wallpaper/catalogue.json` (file, title, credit, and optionally `mask`). An image is added to that list only after its row exists below.

These images are already in the repository and may be offered as artwork without further licensing work.

| File | What it is | Source and licence | Attribution | Size | SHA-256 | Mask |
| --- | --- | --- | --- | --- | --- | --- |
| `assets/unified-preview-coast.png` | Coastal landscape | Generated for the project on 24 October 2026; see [THIRD_PARTY.md](../../preview/THIRD_PARTY.md) | None required | 1672 × 941 | `f338ae11…46dd12` | No subject: simple clock |
| `assets/gallery/example-1.jpg` … `example-5.jpg` | Five AI-generated example pictures | Supplied by the owner and authorised for public inclusion on 24 October 2026 ([CREDITS.md](../../CREDITS.md)) | None required | 2752 × 1536 | see below | Not yet assessed |

Gallery hashes: `example-1` `b0797ebd…5fe1`, `example-2` `c18c2847…481d`, `example-3` `115af49c…fcb7`, `example-4` `920e3c35…7896`, `example-5` `9d3fd2e6…ad78`. The coastal image is below 1080p height; it is fine as a fallback but not as a 4K hero image.

## Planned curated set (not yet fetched)

This cloud session cannot reach any artwork source: Wikimedia, NASA, the Metropolitan Museum and the Art Institute of Chicago all refuse connections from it. The curated set will therefore be fetched on the owner's PC by a small script, `preview/tools/fetch_artwork.py` (to be written). It:

1. reads a fixed list of museum object IDs from this page;
2. asks the museum's own API for each one and **refuses any image not marked public domain/CC0 for that object**;
3. downloads it once, records the source URL, licence statement, retrieval date and SHA-256 here, and resizes it to 3840 × 2160 and 1920 × 1080 JPEG for the app;
4. never runs on the TV; the app ships the resized files.

The owner then sends the files, or commits them, and they are added with their rows. Target: 8 to 12 calm, landscape-format works (seascapes, landscapes, botanical studies, Japanese woodblock prints), avoiding faces so the simple clock is always legible.

## Depth masks

A mask is a separate PNG (white = foreground in front of the clock) prepared on a computer, checked by eye against the picture, and committed beside the image with the suffix `-mask.png`. A picture only uses the depth clock when it has a reviewed mask and the clock area would not cover a face. Otherwise the standard clock is placed in the calmest area, or no clock is shown, as set in Settings. No mask has been prepared yet.
