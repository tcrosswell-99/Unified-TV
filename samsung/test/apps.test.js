'use strict';
// The Frame dock and app launching (spec 7.2, 11, PRODUCT_SPEC 24.2, NT-04, NT-09) and the Live TV guard (NT-10).
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const Apps = require('../src/js/apps.js');
const LiveTv = require('../src/js/livetv.js');

test('the Frame defaults: Netflix, Disney+, BBC, Live TV, Music, Samsung TV Plus; no SmartTube, Xbox or VLC', () => {
  assert.deepStrictEqual(Apps.FRAME_DEFAULTS, ['netflix', 'disney', 'bbc', 'live', 'music', 'tvplus']);
  assert.strictEqual(Apps.availability('smartutube'), 'unavailable');
  assert.strictEqual(Apps.availability('xbox'), 'unavailable');
  assert.strictEqual(Apps.availability('vlc'), 'unknown');
  assert.strictEqual(Apps.dockAdd(['netflix'], 'smartutube'), 'netflix', 'an Android-only app can\'t be added as a fake tile');
  assert.match(Apps.failure('smartutube', 'unavailable'), /Android app/);
});

test('app ids come from the TV\'s own list, by name; the owner\'s choice wins', () => {
  const installed = Apps.installedFrom([{ id: 'X1.Netflix', name: 'Netflix' }, { id: 'Y2.TvPlus', name: 'Samsung TV Plus' }, { id: 'Z3.Iplayer', name: 'BBC iPlayer' }, { name: 'no id' }]);
  assert.strictEqual(installed.length, 3);
  assert.deepStrictEqual(Apps.resolve('netflix', installed, {}), { appId: 'X1.Netflix', source: 'installed' });
  assert.deepStrictEqual(Apps.resolve('tvplus', installed, {}), { appId: 'Y2.TvPlus', source: 'installed' });
  assert.deepStrictEqual(Apps.resolve('bbc', installed, {}), { appId: 'Z3.Iplayer', source: 'installed' });
  assert.deepStrictEqual(Apps.resolve('netflix', installed, { netflix: 'Mine.App' }), { appId: 'Mine.App', source: 'owner' });
  assert.deepStrictEqual(Apps.resolve('music', installed, {}), { appId: null, source: null }, 'not installed: no guess');
});

test('Samsung TV Plus has no guessed native id: only its name on the TV or the owner\'s choice', () => {
  assert.deepStrictEqual(Apps.CATALOGUE.tvplus.candidates, []);
  assert.strictEqual(Apps.resolve('tvplus', null, {}).appId, null);
  assert.strictEqual(LiveTv.guards('tvplus'), false, 'TV Plus is not under the Live TV guard');
});

test('Live TV never resolves to an app and never launches', () => {
  assert.deepStrictEqual(Apps.resolve('live', [{ id: 'Any.Live', name: 'Live TV' }], { live: 'Any.Live' }), { appId: null, source: null });
  assert.strictEqual(Apps.availability('live'), 'guarded');
  assert.ok(LiveTv.guards('live'));
});

test('Live TV stays locked on the Frame by every route, whatever it is told', () => {
  for (const route of LiveTv.ROUTES.concat(['unknown-route'])) {
    const d = LiveTv.decide(route, { vpn: 'Connected', evidence: { nativeClient: true, provider: true, routing: true, dropProtection: true } });
    assert.strictEqual(d.locked, true, route);
    assert.match(d.message, /stays locked/);
  }
  assert.strictEqual(LiveTv.vpnLabel(), 'VPN unknown', 'a web app can\'t see a VPN, so it never claims Connected');
  const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'js', 'livetv.js'), 'utf8');
  assert.doesNotMatch(src, /locked:\s*false/, 'no code path unlocks Live TV in this build');
});

test('launching tries ids in order, reports failure plainly, and is feature-detected', () => {
  const tried = [];
  const env = { tizen: { application: { launch: (id, ok, err) => { tried.push(id); if (id === 'B') ok(); else err(new Error('no')); } } } };
  let result = null;
  Apps.launch(env, ['A', 'B', 'C'], (ok) => { result = ok; });
  assert.deepStrictEqual(tried, ['A', 'B']); assert.strictEqual(result, true);
  Apps.launch({}, ['A'], (ok, reason) => { result = [ok, reason]; });
  assert.deepStrictEqual(result, [false, 'no-launch']);
  Apps.launch(env, [], (ok, reason) => { result = [ok, reason]; });
  assert.deepStrictEqual(result, [false, 'not-found']);
  assert.doesNotMatch(Apps.failure('netflix', 'failed'), /\d{6,}|Error/, 'no ids or error codes on screen');
});

test('without the TV\'s list, the documented candidates are tried and marked unconfirmed', () => {
  const r = Apps.resolve('netflix', null, {});
  assert.strictEqual(r.source, 'candidate');
  assert.strictEqual(r.appId, Apps.CATALOGUE.netflix.candidates[0]);
});

test('dock edits: removing never uninstalls, the order persists, duplicates are refused', () => {
  const order = Apps.dockOrder('bbc,netflix,bbc');
  assert.deepStrictEqual(order, ['bbc', 'netflix']);
  assert.strictEqual(Apps.dockRemove(order, 'bbc'), 'netflix');
  assert.strictEqual(Apps.dockAdd(order, 'netflix'), 'bbc,netflix');
  assert.strictEqual(Apps.dockAdd(order, 'app:3201000000001'), 'bbc,netflix,app:3201000000001');
  const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'js', 'apps.js'), 'utf8') + fs.readFileSync(path.join(__dirname, '..', 'src', 'js', 'app.js'), 'utf8');
  assert.doesNotMatch(src, /uninstall\(|package\.uninstall|tizen\.package/, 'nothing can uninstall an app');
});
