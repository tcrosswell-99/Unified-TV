# Visual reference audit

Audited 25 October 2026 against what the specification (v3.2 sections 32.2, 32.8 and 33.7) says was supplied. Visual comparisons that need a missing reference are marked **pending owner references** in [ACCEPTANCE_REGISTER.csv](../ACCEPTANCE_REGISTER.csv). Implementation does not wait for them.

**None of the reference images are committed.** The supplied mockups contain third-party logos (Netflix, Disney+, BBC, Apple TV, Hulu, YouTube and others), and one contains a commercial album cover. They guide the design only; the app draws its own backgrounds. The SHA-256 values below identify the exact files, so a later comparison can confirm the same reference is meant.

## What the developer package contained

The v3.2 package was four Markdown documents only: the specification, build plan, AI handover and feature status. **It contained no images**, and the "v2.8 specification + reference-images ZIP" it mentions was never supplied.

## Supplied in chat (25 October 2026), 2000 × 1116 JPEG

| Spec reference | Supplied? | What it shows | SHA-256 |
| --- | --- | --- | --- |
| `references/01-smarttube.jpg` | **Equivalent supplied** | Home, SmartTube focused: crimson light from the lower left, a softer band to the upper right | `ea3cb3dd89f5b526f62070b23b9184eb9ea928b6e6eb48e34b696974e0db012d` |
| `references/02-netflix.jpg` | **Equivalent supplied** (sent twice) | Home, Netflix focused: dark red central glow, black corners | `135608eea55e53ed7a70e2f73a3f2e972d815e839d22be12dd7e2d227b586d24` |
| `references/03-disney-plus.jpg` | **Equivalent supplied** (sent twice) | Home, Disney+ focused: navy and indigo, cyan bloom behind the dock | `ec0365ef4bb91f14e210df6df6631b09698f3a57f051ebcb125d37087abc62d8` |
| `references/04-bbc.jpg` | **Equivalent supplied** | Home, BBC focused: magenta and pink wash; the other dock tiles are blank (a mockup artefact) | `8590f2013a365d2eef88b6e11db633590dfc43e93a0b0df2f3dab78464513757` |
| `references/05-live-tv.jpg` | **Equivalent supplied** | Home, Live TV focused: violet and indigo pools | `b227420979ed8bd2e9ef14a9ff48220e3501f9acbfe5be91d9099b294878c430` |
| `references/06-music.jpg` | **Equivalent supplied** | Home, Music focused: peach, coral and pink | `f21572bdc773589be3aeaeac43a8cfab26c92e087ae2a5a6c3a0de8a425f1f03` |
| (not in the spec) Doorbell card | Supplied | A "Front Door · Live" camera card with a timestamp over a blurred Home | `188b3515ea99e70c2f9fa3e39505c7425023613eb853b65fbc95822269533673` |
| (not in the spec) Music now playing | Supplied | A full-screen "now playing" view with album art, progress and controls | `e4a758413ab396877a6cc81200227e6dbb8353b575b7efe15c463adbe52a4c06` |

"Equivalent supplied" means a mockup for that app's palette arrived, though not under the file name the specification uses.

## Missing

| Spec reference | Used by | Status |
| --- | --- | --- |
| The v2.8 reference ZIP (`references/01…06.jpg` under those names) | AM-01, AM-12 | Covered by the chat mockups above; the named files were never supplied. |
| `wallpaper-reference-01.png` (music and glass controls) | Section 33.7, WP visual checks | **Missing.** Pending owner references. |
| `wallpaper-reference-02.png` (liquid-glass Home controls) | Section 33.7 | **Missing.** Pending owner references. |
| `wallpaper-reference-03.png`, `-04.png`, `-05.png` (large depth clock behind a person) | WP-04, depth clock | **Missing.** Pending owner references. |

## What the mockups tell the implementation

- **Palettes and light positions** for all six dock apps match spec 32.2. They are implemented procedurally in `AmbientMesh.java`, with no image files.
- **Layout matches the current Home:** large clock top left; VPN, microphone and Settings in one glass capsule top right; the app's name centred above a frosted dock of six tiles; the focused tile lifted with a light outline.
- **Disagreements to note, not copy:**
  - The mockups show a permanent "↑ Controls ↓ Your Films & TV" hint. The specification forbids permanent instructional text, so Home keeps only the small Down chevron.
  - Unfocused tile logos are greyed in some mockups and coloured in others. Unified TV keeps them coloured, which spec 32.3 requires.
  - The Music and BBC mockups are bright enough that white type falls below the 4.5:1 contrast target and would be hard on the Sony OLED. The implementation darkens those palettes and adds a vignette behind text.
  - The doorbell mockup shows a different dock (Hulu, Apple TV and others) behind the card: a mockup artefact.
- **Music now playing:** Amazon has no public playback API for TV apps. A similar screen is possible only by reading the Amazon Music app's own Android media session, which needs notification access the owner grants once. It is recorded as an idea, not planned work.
