'use strict';
// Spec 7.4-7.6 and PRODUCT_SPEC 32.4: CSS gradients built from the shared palettes, drift by transform within the rule,
// a 400 ms opacity crossfade that starts at once and retargets (latest focus wins), and the motion settings.
const test = require('node:test');
const assert = require('node:assert');
const Mesh = require('../src/js/mesh.js');
const Focus = require('../src/js/focus.js');
const palettes = require('../src/palettes.json');

test('each light is a radial-gradient tinted by its own colour and alpha', () => {
  const light = palettes.palettes.netflix.lights[0];   // 0xC0800A10
  const css = Mesh.lightGradient(light);
  assert.match(css, /^radial-gradient\(circle closest-side, /);
  assert.match(css, /rgba\(128,10,16,0\.7529\) 0%/);
  assert.match(css, /rgba\(128,10,16,0\) 100%\)$/);
  const box = Mesh.lightBox(light, 1920, 1080);
  assert.deepStrictEqual(box, { left: Math.round(0.42 * 1920 - 768), top: Math.round(0.44 * 1080 - 768), size: 1536 });
});

test('unknown apps get the neutral fallback, never another app\'s palette', () => {
  assert.strictEqual(Mesh.palette(palettes, 'app:3201000000000'), palettes.neutral);
  assert.strictEqual(Mesh.palette(palettes, 'netflix'), palettes.palettes.netflix);
  assert.strictEqual(Mesh.known(palettes, 'constructor'), false);
});

test('drift keyframes move only by translate3d and scale, within 2-5 % and 1.00-1.06, and close the loop', () => {
  for (const p of Object.values(palettes.palettes)) {
    for (const l of p.lights) {
      const frames = Mesh.driftKeyframes(l);
      assert.strictEqual(frames[0].pct, 0); assert.strictEqual(frames[frames.length - 1].pct, 100);
      assert.ok(Math.abs(frames[0].dx - frames[frames.length - 1].dx) < 1e-9 && Math.abs(frames[0].s - frames[frames.length - 1].s) < 1e-9, 'loop closes');
      for (const f of frames) {
        assert.ok(Math.abs(f.dx) <= 0.05 + 1e-9 && Math.abs(f.dy) <= 0.05 + 1e-9);
        assert.ok(f.s >= 1 - 1e-9 && f.s <= 1.06 + 1e-9);
      }
      const css = Mesh.keyframesCss('x', l, 1920, 1080);
      assert.match(css, /translate3d\(/);
      assert.doesNotMatch(css, /filter|blur|radial-gradient|background/);
    }
  }
});

test('Android\'s own drift formula stays inside the same bounds (parity)', () => {
  const l = palettes.palettes.music.lights[0];
  for (let t = 0; t < 120; t += 0.5) {
    const [x, y, s] = Mesh.position(l, t);
    assert.ok(Math.abs(x - l.x) <= 0.05 + 1e-6 && Math.abs(y - l.y) <= 0.05 + 1e-6 && s >= 1 - 1e-6 && s <= 1.06 + 1e-6);
  }
});

test('drift never restarts on a focus change: the delay comes from one clock, not from when the layer was made', () => {
  const l = palettes.palettes.bbc.lights[1];
  const a = Mesh.driftDelay(l, 1000), b = Mesh.driftDelay(l, 1000);
  assert.strictEqual(a, b);
  assert.ok(a <= 0 && a > -l.period);
  const later = Mesh.driftDelay(l, 1001);
  assert.ok(Math.abs((a - 1) - later) < 1e-6 || Math.abs((a - 1 + l.period) - later) < 1e-6, 'one second later is one second further along');
});

test('motion settings: default 400 ms with drift; still 400 ms; reduce instant; lightweight 200 ms still frames', () => {
  assert.deepStrictEqual(Mesh.motion('default', false), { setting: 'default', fadeMs: 400, drift: true });
  assert.deepStrictEqual(Mesh.motion('still', false), { setting: 'still', fadeMs: 400, drift: false });
  assert.deepStrictEqual(Mesh.motion('reduce', false), { setting: 'reduce', fadeMs: 0, drift: false });
  assert.deepStrictEqual(Mesh.motion('light', false), { setting: 'light', fadeMs: 200, drift: false });
  assert.strictEqual(Mesh.motion('default', true).fadeMs, 0, 'system reduced motion is honoured');
  assert.strictEqual(Mesh.motion('sparkly', false).setting, 'default');
});

test('the crossfade starts immediately and runs 400 ms by opacity', () => {
  const c = new Mesh.Crossfade();
  c.commit('netflix', 1000, 400);
  const ops = c.commit('disney', 2000, 400);
  const fade = ops.filter((o) => o.ms > 0);
  assert.strictEqual(fade.length, 1);
  assert.deepStrictEqual(fade[0], { layer: fade[0].layer, key: 'disney', role: 'top', opacity: 1, ms: 400 });
  assert.ok(ops.indexOf(fade[0]) === ops.length - 1 && ops[ops.length - 2].opacity === 0, 'from 0 at once, then to 1');
  assert.strictEqual(c.dominant(2100), 'netflix');
  assert.strictEqual(c.dominant(2399), 'disney');
  assert.strictEqual(c.dominant(2400), 'disney');
});

test('rapid presses: the latest focus wins, only one fade ever runs, nothing queues', () => {
  const c = new Mesh.Crossfade();
  c.commit('netflix', 0, 400);
  let t = 1000;
  for (const id of ['disney', 'bbc', 'live', 'music', 'tvplus', 'bbc']) {
    const ops = c.commit(id, t, 400);
    assert.strictEqual(ops.filter((o) => o.ms > 0).length, 1);
    t += 40;
  }
  assert.strictEqual(c.scene, 'bbc');
  assert.strictEqual(c.dominant(t + 400), 'bbc');
  assert.strictEqual(c.keys[c.top], 'bbc');
});

test('retargeting keeps whichever scene is most visible', () => {
  const c = new Mesh.Crossfade();
  c.commit('netflix', 0, 400);
  c.commit('disney', 1000, 400);
  const early = c.commit('bbc', 1100, 400);           // disney barely visible: netflix stays as the base
  assert.ok(!early.some((o) => o.role === 'base'), 'base untouched');
  assert.strictEqual(c.keys[c.base], 'netflix');
  c.commit('live', 1400, 400);                          // bbc now mostly visible: it becomes the base
  assert.strictEqual(c.keys[c.base], 'bbc');
  assert.strictEqual(c.keys[c.top], 'live');
});

test('reduce motion switches instantly; the same focus twice does nothing', () => {
  const c = new Mesh.Crossfade();
  c.commit('netflix', 0, 0);
  const ops = c.commit('disney', 10, 0);
  assert.ok(ops.every((o) => o.ms === 0));
  assert.strictEqual(c.keys[c.base], 'disney');
  assert.deepStrictEqual(c.commit('disney', 20, 0), []);
});

test('a slow TV falls back to a still mesh; a healthy one keeps the drift', () => {
  assert.strictEqual(Mesh.shouldFallBack(new Array(60).fill(16.7)), false);
  assert.strictEqual(Mesh.shouldFallBack(new Array(60).fill(70)), true);
  assert.strictEqual(Mesh.shouldFallBack([90, 90]), false, 'too few samples to judge');
});

test('100 rapid presses end on the correct tile with that tile\'s palette and title (spec 7.7, AT-40)', () => {
  const dock = ['netflix', 'disney', 'bbc', 'live', 'music', 'tvplus'].map((id) => ({ id, name: id }));
  let f = Focus.initial(dock.length, 0);
  const c = new Mesh.Crossfade();
  c.commit(Focus.scene(f, dock).palette, 0, 400);
  const dirs = [];
  for (let i = 0; i < 100; i++) dirs.push(i % 7 === 3 ? 'left' : 'right');
  let t = 10;
  for (const d of dirs) { f = Focus.move(f, d, dock.length); c.commit(Focus.scene(f, dock).palette, t, 400); t += 30; }
  assert.strictEqual(f.dock, dock.length - 1);
  const s = Focus.scene(f, dock);
  assert.deepStrictEqual(s, { palette: 'tvplus', title: 'tvplus' });
  assert.strictEqual(c.dominant(t + 400), 'tvplus');
});
