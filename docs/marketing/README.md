# Marketing images

Concept artwork for the README, the GitHub social preview and posts about the project. They show the **planned** Unified TV design, not screenshots of a finished build, and each image says so in its corner.

| Image | Size | Use |
| --- | --- | --- |
| [`hero.png`](hero.png) | 1600×800 | Top of the README |
| [`every-room.png`](every-room.png) | 1600×900 | "Same apps, same order, every room" |
| [`before-after.png`](before-after.png) | 1600×900 | Different brands today versus one harmonised home |
| [`home-view.png`](home-view.png) | 1600×900 | Optional Home Assistant alerts over viewing |
| [`alert-priority.png`](alert-priority.png) | 1200×850 | Alert order, see [Home view](../HOME_VIEW.md) |
| [`social-preview.png`](social-preview.png) | 1280×640 | Repository settings → Social preview |

## Rules these images follow

- Drawn with CSS only: no photographs, no brand logos, no family, child or Ring images ([AGENTS.md](../../AGENTS.md) rule 6). App tiles are generic (Videos, Films, Kids, News, Live TV, Music).
- Brand names appear only as plain text describing which TV a room has. The "before" mock-ups are invented and labelled as not real brands.
- Every TV focused on the same item shows the same artwork, as the design system requires.
- Nothing is presented as working today. The Home view and doorbell picture are labelled as planned and needing real-TV testing ([AGENTS.md](../../AGENTS.md) rule 1).

## Editing and rendering

The source is [`source/marketing.html`](source/marketing.html): one `<section class="board">` per image. Render all of them with Playwright and Chromium:

```bash
node docs/marketing/source/render.js
```

Text uses Inter, bundled in `launcher-ui/app/assets/fonts` under the SIL Open Font Licence. The images and their source are covered by the repository's [MIT licence](../../LICENSE).
