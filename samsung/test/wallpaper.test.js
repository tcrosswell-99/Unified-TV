'use strict';
// Spec 8, OD-03/OD-04: mirrors preview/app/test/io/unifiedtv/preview/WallpaperPolicyTest.java, plus the Frame rule that
// Wallpaper is never presented as Art Mode and never tries to control it (WP-12).
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const Wp = require('../src/js/wallpaper.js');

function flat(v) { return new Array(Wp.GW * Wp.GH).fill(v); }

test('defaults are artwork, automatic depth, five minutes; unknown values fall back', () => {
  assert.strictEqual(Wp.source(null), Wp.ARTWORK); assert.strictEqual(Wp.source('off'), Wp.OFF); assert.strictEqual(Wp.source('photos'), Wp.ARTWORK);
  assert.strictEqual(Wp.clock('sparkly'), Wp.AUTO);
  assert.strictEqual(Wp.idle(7), 5); assert.strictEqual(Wp.idle(0), 0); assert.strictEqual(Wp.idle(15), 15);
  assert.strictEqual(Wp.slide(1), 300); assert.strictEqual(Wp.slide(30), 30);
});
test('the simple clock goes where the picture is calmest', () => {
  const g = flat(0.3);
  for (let y = 0; y < Wp.GH; y++) for (let x = 0; x < Wp.GW; x++) if (!(x >= 10 && y >= 6)) g[y * Wp.GW + x] = (x + y) % 2 === 0 ? 0.1 : 0.9;
  assert.deepStrictEqual(Wp.SIMPLE_SPOTS[Wp.calmest(g)[0]], [10, 6, 15, 8]);
});
test('the clock moves between the two calmest spots on each slide (OLED care)', () => {
  const g = flat(0.3);
  const first = Wp.spot(g, 0), second = Wp.spot(g, 1), third = Wp.spot(g, 2);
  assert.notStrictEqual(first, second); assert.strictEqual(first, third);
});
test('bright skies get a shade behind the numerals', () => {
  assert.ok(Wp.needsShade(flat(0.8), Wp.SIMPLE_SPOTS[0])); assert.ok(!Wp.needsShade(flat(0.2), Wp.SIMPLE_SPOTS[0]));
});
test('the depth clock only with a mask that really overlaps the clock', () => {
  assert.strictEqual(Wp.clockFor(Wp.AUTO, null), Wp.SIMPLE, 'no mask: simple clock');
  assert.strictEqual(Wp.clockFor(Wp.AUTO, flat(0)), Wp.SIMPLE, 'subject elsewhere');
  assert.strictEqual(Wp.clockFor(Wp.AUTO, flat(1)), Wp.SIMPLE, 'subject would hide the time');
  const head = flat(0);
  for (let y = 2; y < Wp.GH; y++) for (let x = 6; x < 10; x++) head[y * Wp.GW + x] = 1;
  assert.strictEqual(Wp.clockFor(Wp.AUTO, head), 'depth');
  assert.strictEqual(Wp.clockFor(Wp.SIMPLE, head), Wp.SIMPLE, 'the owner\'s choice wins');
  assert.strictEqual(Wp.clockFor(Wp.NONE, head), Wp.NONE);
});
test('luminance is read from a 16 x 9 thumbnail', () => {
  const rgba = new Array(Wp.GW * Wp.GH * 4).fill(255);
  const g = Wp.luminanceGrid(rgba);
  assert.strictEqual(g.length, 144); assert.ok(Math.abs(g[0] - 1) < 1e-9);
});
test('only bundled pictures with a credit are used; nothing is downloaded', () => {
  const list = Wp.catalogue([
    { file: 'wallpaper/coast.png', credit: 'Made for Unified TV' },
    { file: 'https://example.invalid/picture.jpg', credit: 'x' },
    { file: 'wallpaper/no-credit.jpg' },
    { file: '../outside.jpg', credit: 'x' }]);
  assert.deepStrictEqual(list.map((p) => p.file), ['wallpaper/coast.png']);
  const sources = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'wallpaper.json'), 'utf8')).pictures;
  for (const p of sources) {
    assert.ok(fs.existsSync(path.join(__dirname, '..', '..', p.from)), p.from + ' must already be in the repository (docs/design/ARTWORK.md)');
    assert.strictEqual(Wp.catalogue([p]).length, 1, p.file);
  }
});
test('the first D-pad, OK or Back only leaves Wallpaper; it never launches anything', () => {
  for (const a of ['up', 'down', 'left', 'right', 'ok', 'back']) assert.ok(Wp.dismissKey(a));
  assert.ok(!Wp.dismissKey('playpause'));
});
test('Wallpaper is never presented as Art Mode and never touches Art Mode', () => {
  assert.match(Wp.NOT_ART_MODE, /isn't Samsung Art Mode/);
  const src = path.join(__dirname, '..', 'src');
  const files = ['js/wallpaper.js', 'js/app.js', 'index.html', 'css/home.css'].map((f) => fs.readFileSync(path.join(src, f), 'utf8')).join('\n');
  assert.doesNotMatch(files, /artmode|ArtMode|art_mode|art-mode-on/, 'no Art Mode control calls');
  const appText = fs.readFileSync(path.join(src, 'js', 'app.js'), 'utf8');
  for (const m of appText.match(/'[^'\n]*Art Mode[^'\n]*'/g) || []) {
    assert.ok(/isn't|can't|not |stay with the TV|stay with your TV|Art Mode,|in Art Mode/.test(m), 'every mention of Art Mode is a limitation: ' + m);
  }
});
