'use strict';
// Spec 32.6: one canonical palette mapping across all three TVs. samsung/src/palettes.json must equal the numbers in
// Android's AmbientMesh.java; this fails the moment either side changes without the other.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..');
const JAVA = path.join(ROOT, 'preview', 'app', 'src', 'io', 'unifiedtv', 'preview', 'AmbientMesh.java');
const JSON_FILE = path.join(ROOT, 'samsung', 'src', 'palettes.json');

function num(s) { return parseFloat(s.replace(/f$/, '')); }
function parseJava(source) {
  const palettes = {}; let neutral = null;
  const palette = /(?:PALETTES\.put\("(\w+)",\s*|NEUTRAL\s*=\s*)new Palette\((0x[0-9A-Fa-f]{8}),\s*(0x[0-9A-Fa-f]{8}),([\s\S]*?\))\);/g;
  const light = /new Light\(\s*([\d.]+f?),\s*([\d.]+f?),\s*([\d.]+f?),\s*(0x[0-9A-Fa-f]{8}),\s*([\d.]+f?),\s*([\d.]+f?),\s*([\d.]+f?),\s*([\d.]+f?)\)/g;
  let m;
  while ((m = palette.exec(source))) {
    const lights = []; let l; light.lastIndex = 0;
    while ((l = light.exec(m[4]))) {
      lights.push({ x: num(l[1]), y: num(l[2]), r: num(l[3]), colour: l[4].toUpperCase().replace('0X', '0x'), period: num(l[5]), phase: num(l[6]), ax: num(l[7]), ay: num(l[8]) });
    }
    const p = { base: m[2].toUpperCase().replace('0X', '0x'), accent: m[3].toUpperCase().replace('0X', '0x'), lights: lights };
    if (m[1]) palettes[m[1]] = p; else neutral = p;
  }
  return { palettes: palettes, neutral: neutral };
}

const java = parseJava(fs.readFileSync(JAVA, 'utf8'));
const shared = JSON.parse(fs.readFileSync(JSON_FILE, 'utf8'));

test('the Android source still parses into palettes (a parse failure must not pass silently)', () => {
  assert.ok(Object.keys(java.palettes).length >= 6, 'expected the six dock palettes in AmbientMesh.java');
  assert.ok(java.neutral && java.neutral.lights.length >= 2, 'expected the neutral fallback');
});

test('palettes.json has exactly the Android palettes, with the same numbers', () => {
  assert.deepStrictEqual(Object.keys(shared.palettes).sort(), Object.keys(java.palettes).sort());
  for (const id of Object.keys(java.palettes)) assert.deepStrictEqual(shared.palettes[id], java.palettes[id], 'palette ' + id + ' drifted from Android');
  assert.deepStrictEqual(shared.neutral, java.neutral, 'neutral fallback drifted from Android');
});

test('the six dock palettes plus TV Plus are present and distinct', () => {
  const ids = ['smartutube', 'netflix', 'disney', 'bbc', 'live', 'music', 'tvplus'];
  const accents = new Set(ids.map((id) => shared.palettes[id].accent));
  assert.strictEqual(accents.size, ids.length);
  for (const id of ids) assert.ok(shared.palettes[id].lights.length >= 2, id + ' needs at least two lights');
});

test('bases stay dark so white type remains readable; drift stays slow and small', () => {
  for (const [id, p] of Object.entries(shared.palettes)) {
    const v = parseInt(p.base.slice(2), 16) >>> 0;
    assert.ok(((v >> 16) & 255) < 48 && ((v >> 8) & 255) < 48 && (v & 255) < 64, id + ' base too bright');
    for (const l of p.lights) {
      assert.ok(l.period >= 20 && l.period <= 40, id + ' period');
      assert.ok(l.ax >= 0.02 && l.ax <= 0.05 && l.ay >= 0.01 && l.ay <= 0.05, id + ' amplitude');
    }
  }
});
