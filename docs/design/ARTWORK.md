# Wallpaper artwork: sources, licences and attribution

The owner decided on 25 October 2026 (OD-03, OD-04):

- The default Wallpaper source is **curated artwork** that is free and **explicitly licensed for this use**.
- It is bundled in the app; displaying a wallpaper never depends on downloading anything.
- The depth clock uses **prepared foreground masks** for selected artwork only. The TCL never segments images itself; without a suitable mask the standard clock is shown.
- Synology photos are a separate, later task; the access design is [MY_PHOTOS.md](MY_PHOTOS.md) (DLNA folders first, Synology Photos albums later), awaiting the owner's approval.

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

This cloud session cannot reach any artwork source: Wikimedia, NASA, the Metropolitan Museum and the Art Institute of Chicago all refuse connections from it. The curated set is therefore fetched on the owner's PC by `preview/tools/fetch_artwork.py` (steps in [OWNER_TESTING.md](../OWNER_TESTING.md#fetching-the-wallpaper-artwork)). It:

1. holds a fixed list of museum objects: Hokusai's *Great Wave* and van Gogh's *Wheat Field with Cypresses* (The Met); Monet's *Water Lilies*, Seurat's *La Grande Jatte*, van Gogh's *The Bedroom* and Hokusai's *Under the Wave off Kanagawa* (Art Institute of Chicago);
2. asks each museum's own API and **refuses any image the museum does not mark public domain** (both museums publish these as CC0), any title that doesn't match (a wrong object number) and any portrait-format image;
3. downloads each once, records the source page, licence, retrieval date and SHA-256, and (with Pillow installed) saves a 1920-pixel-wide JPEG for the app;
4. writes `catalogue.json` and the rows for this page; it never runs on the TV, and the app ships only the saved files.

The owner then sends the files, or commits them, and they are added with their rows. Target: 8 to 12 calm, landscape-format works (seascapes, landscapes, botanical studies, Japanese woodblock prints), avoiding faces so the simple clock is always legible.

## Depth masks

A mask is a separate PNG (white = foreground in front of the clock) prepared on a computer, checked by eye against the picture, and committed beside the image with the suffix `-mask.png`. A picture only uses the depth clock when it has a reviewed mask and the clock area would not cover a face. Otherwise the standard clock is placed in the calmest area, or no clock is shown, as set in Settings. No mask has been prepared yet.
