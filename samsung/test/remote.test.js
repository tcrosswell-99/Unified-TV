'use strict';
// Remote, focus and Back (spec 6.2, 6.4, PRODUCT_SPEC 28.2-28.3): only the keys used are registered, reserved keys are
// never touched, long OK never also activates, Back unwinds one layer and does nothing at Home.
const test = require('node:test');
const assert = require('node:assert');
const Keys = require('../src/js/keys.js');
const Focus = require('../src/js/focus.js');
const Nav = require('../src/js/nav.js');

test('the basic keys map without registration; anything else stays with the TV', () => {
  assert.strictEqual(Keys.action(37), 'left'); assert.strictEqual(Keys.action(38), 'up');
  assert.strictEqual(Keys.action(39), 'right'); assert.strictEqual(Keys.action(40), 'down');
  assert.strictEqual(Keys.action(13), 'ok'); assert.strictEqual(Keys.action(10009), 'back');
  assert.strictEqual(Keys.action(10252), 'playpause'); assert.strictEqual(Keys.action(413), 'stop');
  assert.strictEqual(Keys.action(10182), null, 'Exit belongs to the TV');
  assert.strictEqual(Keys.action(427), null, 'channel keys are never a route into Live TV');
  assert.strictEqual(Keys.action(48), null, 'number keys are not used');
  assert.strictEqual(Keys.action(8, true), null, 'Backspace inside a text field is typing, not Back');
});

test('no Tizen: registration is skipped without throwing (feature detection)', () => {
  assert.deepStrictEqual(Keys.register({}), { available: false, registered: [], skipped: [], failed: [] });
  assert.deepStrictEqual(Keys.register(undefined).registered, []);
});

test('only the player\'s media keys are registered, only if the TV supports them, and never a reserved key', () => {
  const calls = [];
  const env = { tizen: { tvinputdevice: {
    getSupportedKeys: () => ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'MediaFastForward', 'Exit', 'ChannelUp', 'ColorF0Red', '1'].map((name) => ({ name })),
    registerKey: (name) => { if (name === 'MediaStop') throw new Error('refused'); calls.push(name); }
  } } };
  const r = Keys.register(env);
  assert.deepStrictEqual(calls, ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaFastForward']);
  assert.deepStrictEqual(r.failed, ['MediaStop']);
  assert.deepStrictEqual(r.skipped, ['MediaRewind']);
  for (const reserved of Keys.NEVER) assert.ok(!Object.prototype.hasOwnProperty.call(Keys.MEDIA, reserved));
  for (const k of ['Exit', 'Source', 'ChannelUp', 'Guide', 'VolumeUp', 'Power']) assert.ok(Keys.NEVER.indexOf(k) !== -1);
});

test('OK: a short press activates once; a long press opens the menu and never also activates', () => {
  const p = new Keys.PressTracker(650);
  assert.strictEqual(p.down(0), null);
  assert.strictEqual(p.up(120), 'press');
  assert.strictEqual(p.down(1000), null);
  assert.strictEqual(p.down(1500), null, 'auto-repeat before the threshold');
  assert.strictEqual(p.down(1700), 'long', 'held past the threshold');
  assert.strictEqual(p.down(1800), null, 'only once');
  assert.strictEqual(p.up(1900), null, 'release after a long press does nothing');
});

test('OK on firmware that never sends keyup is still an ordinary press, not stranded', () => {
  const p = new Keys.PressTracker(650, 1500);
  p.down(0);
  assert.strictEqual(p.tick(700), null, 'no repeat and no keyup yet: not a long press');
  assert.strictEqual(p.tick(1500), 'press');
  const q = new Keys.PressTracker(650, 1500);
  q.down(0); q.up(50);                       // keyup works on this TV
  q.down(1000);
  assert.strictEqual(q.tick(1700), 'long', 'held without auto-repeat, keyup known to work');
});

test('Home focus: Left/Right stop at the ends, Up reaches the utilities, Down the Films & TV chevron, and back', () => {
  let f = Focus.initial(6, 0);
  f = Focus.move(f, 'left', 6); assert.strictEqual(f.dock, 0, 'no wrap');
  for (let i = 0; i < 10; i++) f = Focus.move(f, 'right', 6);
  assert.strictEqual(f.dock, 5, 'stops at the last tile');
  f = Focus.move(f, 'up', 6); assert.deepStrictEqual(Focus.target(f), { kind: 'util', id: 'settings' });
  f = Focus.move(f, 'left', 6); assert.deepStrictEqual(Focus.target(f), { kind: 'util', id: 'voice' });
  f = Focus.move(f, 'down', 6); assert.deepStrictEqual(Focus.target(f), { kind: 'dock', index: 5 }, 'returns to the same tile');
  f = Focus.move(f, 'down', 6); assert.deepStrictEqual(Focus.target(f), { kind: 'more' });
  f = Focus.move(f, 'up', 6); assert.strictEqual(f.dock, 5);
});

test('focus never launches anything and the title, palette and halo follow one focus', () => {
  const dock = [{ id: 'netflix', name: 'Netflix' }, { id: 'bbc', name: 'BBC' }];
  let f = Focus.initial(2, 1);
  assert.deepStrictEqual(Focus.scene(f, dock), { palette: 'bbc', title: 'BBC' });
  f = Focus.move(f, 'up', 2);
  assert.deepStrictEqual(Focus.scene(f, dock), { palette: 'bbc', title: '' }, 'utilities keep the scene, no flash');
  assert.deepStrictEqual(Focus.scene(Focus.initial(0, 0), []), { palette: 'neutral', title: '' });
});

test('an empty Home stays recoverable: one slot to add apps', () => {
  const f = Focus.move(Focus.initial(0, 3), 'right', 0);
  assert.deepStrictEqual(Focus.target(f), { kind: 'dock', index: 0 });
});

test('moving a tile: Left/Right carry it; ends are respected', () => {
  const order = ['netflix', 'disney', 'bbc'];
  assert.deepStrictEqual(Focus.shift(order, 1, 'left'), { order: ['disney', 'netflix', 'bbc'], index: 0 });
  assert.deepStrictEqual(Focus.shift(order, 0, 'left'), { order: order, index: 0 });
  assert.deepStrictEqual(order, ['netflix', 'disney', 'bbc'], 'the saved order is untouched until OK');
});

test('Back unwinds one layer, restores the focus underneath, and does nothing at Home', () => {
  const s = new Nav.Stack();
  assert.deepStrictEqual(s.back().action, 'none', 'Back at Home never exits');
  s.push('settings', { row: 'util', util: 1 });
  s.push('sheet', 3);
  let r = s.back();
  assert.strictEqual(r.closed, 'sheet'); assert.strictEqual(r.focus, 3);
  r = s.back();
  assert.strictEqual(r.closed, 'settings'); assert.deepStrictEqual(r.focus, { row: 'util', util: 1 });
  assert.strictEqual(s.back().action, 'none');
  for (let i = 0; i < 20; i++) assert.strictEqual(s.back().action, 'none', 'repeated Back stays on Home');
  assert.throws(() => s.push('exit'), /unknown layer/);
});

test('returning from another app closes overlays but keeps Home\'s focus', () => {
  const s = new Nav.Stack();
  s.push('library', { row: 'more' }); s.push('player');
  s.home();
  assert.ok(s.atRoot()); assert.deepStrictEqual(s.top().focus, { row: 'more' });
});
