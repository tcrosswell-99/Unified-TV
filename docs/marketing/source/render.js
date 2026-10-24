// Renders each <section class="board"> in marketing.html to docs/marketing/<id>.png at its own size.
// Needs Playwright with Chromium: node docs/marketing/source/render.js
const path = require('path');
const { chromium } = require('playwright');

(async () => {
  const src = path.join(__dirname, 'marketing.html');
  const out = path.join(__dirname, '..');
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1800, height: 1000 }, deviceScaleFactor: 1 });
  await page.goto('file://' + src);
  await page.evaluate(() => document.fonts.ready);
  for (const board of await page.$$('section.board')) {
    const id = await board.getAttribute('id');
    await board.screenshot({ path: path.join(out, `${id}.png`) });
    console.log(`${id}.png`);
  }
  await browser.close();
})();
