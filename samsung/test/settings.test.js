'use strict';
// Settings persistence (spec 6.3) and secrets (spec 13, PRODUCT_SPEC 24.4): versioned, never throws, a newer version is
// never overwritten, secrets never reach web storage, Key Manager fails closed.
const test = require('node:test');
const assert = require('node:assert');
const Settings = require('../src/js/settings.js');
const Secrets = require('../src/js/secrets.js');
const Apps = require('../src/js/apps.js');

class MemoryStorage {
  constructor(initial) { this.data = Object.assign({}, initial || {}); }
  getItem(k) { return Object.prototype.hasOwnProperty.call(this.data, k) ? this.data[k] : null; }
  setItem(k, v) { this.data[k] = String(v); }
}

test('a fresh TV gets the defaults: the Frame dock, gentle motion, artwork Wallpaper after 5 minutes', () => {
  const r = Settings.migrate(null);
  assert.deepStrictEqual(r.notes, ['fresh']);
  assert.strictEqual(r.settings.version, Settings.VERSION);
  assert.strictEqual(r.settings.dock, null);
  assert.deepStrictEqual(Apps.dockOrder(r.settings.dock), ['netflix', 'disney', 'bbc', 'live', 'music', 'tvplus']);
  assert.strictEqual(r.settings.motion, 'default');
  assert.deepStrictEqual([r.settings.wallpaper.source, r.settings.wallpaper.clock, r.settings.wallpaper.idle], ['artwork', 'auto', 5]);
  assert.strictEqual(r.settings.ha.https, true);
  assert.strictEqual(r.settings.ha.host, '', 'no household address in the defaults');
});

test('unreadable or unversioned data gives the defaults and never throws', () => {
  for (const raw of ['{not json', '[]', '42', '"text"', '{"motion":"still"}', '{"version":"one"}', '{"version":0}']) {
    const r = Settings.migrate(raw);
    assert.strictEqual(r.settings.motion, 'default', raw);
    assert.strictEqual(r.readOnly, false);
  }
});

test('data from a newer version is read as defaults and left untouched', () => {
  const newer = JSON.stringify({ version: Settings.VERSION + 1, motion: 'still', futureThing: true });
  const storage = new MemoryStorage({ [Settings.KEY]: newer });
  const store = new Settings.Store(storage);
  const s = store.load();
  assert.ok(store.readOnly); assert.strictEqual(s.motion, 'default');
  assert.strictEqual(store.save(s), false);
  assert.strictEqual(storage.getItem(Settings.KEY), newer, 'going back a version must not destroy newer settings');
});

test('migrations run in order from the stored version (exercised with a test migration to version 2)', () => {
  const steps = { 1: (v) => { v.motion = v.reduceMotion ? 'reduce' : 'default'; delete v.reduceMotion; return v; } };
  const r = Settings.migrate(JSON.stringify({ version: 1, reduceMotion: true }), steps, 2);
  assert.strictEqual(r.settings.version, 2); assert.strictEqual(r.settings.motion, 'reduce');
  assert.deepStrictEqual(r.notes, ['migrated-2']);
  const gap = Settings.migrate(JSON.stringify({ version: 1 }), {}, 2);
  assert.deepStrictEqual(gap.notes, ['no-migration-1']); assert.strictEqual(gap.settings.motion, 'default');
  const broken = Settings.migrate(JSON.stringify({ version: 1 }), { 1: () => { throw new Error('x'); } }, 2);
  assert.deepStrictEqual(broken.notes, ['migration-failed-1']);
});

test('every value is checked on the way in', () => {
  const r = Settings.migrate(JSON.stringify({ version: 1, motion: 'disco', highContrast: 'yes', dock: 'netflix,smartutube,netflix,xbox,app:3201907018807,app:../x,bbc',
    wallpaper: { source: 'photos', idle: 7, slide: 30, dim: 99 }, ha: { host: 'homeassistant.local', port: 99999, https: 'no' },
    alerts: { door: 'binary_sensor.front_door', person: 'camera.front; drop' }, appChoices: { netflix: '3201907018807', nonsense: 'x', bbc: 'bad id!' } }));
  const s = r.settings;
  assert.strictEqual(s.motion, 'default'); assert.strictEqual(s.highContrast, false);
  assert.strictEqual(s.dock, 'netflix,app:3201907018807,bbc', 'SmartTube and Xbox are not Frame apps; duplicates and bad ids dropped');
  assert.deepStrictEqual([s.wallpaper.source, s.wallpaper.idle, s.wallpaper.slide, s.wallpaper.dim], ['artwork', 5, 30, 0]);
  assert.deepStrictEqual([s.ha.host, s.ha.port, s.ha.https], ['homeassistant.local', 8123, true]);
  assert.deepStrictEqual([s.alerts.door, s.alerts.person], ['binary_sensor.front_door', '']);
  assert.deepStrictEqual(s.appChoices, { netflix: '3201907018807' });
});

test('an empty Home the owner chose stays empty', () => {
  const s = Settings.migrate(JSON.stringify({ version: 1, dock: '' })).settings;
  assert.strictEqual(s.dock, ''); assert.deepStrictEqual(Apps.dockOrder(s.dock), []);
});

test('secrets never reach web storage, even if something put them there', () => {
  const r = Settings.migrate(JSON.stringify({ version: 1, ha: { host: 'homeassistant.local', token: 'abc', accessToken: 'def' }, password: 'x' }));
  assert.ok(r.notes.indexOf('secret-removed') !== -1);
  const storage = new MemoryStorage();
  const store = new Settings.Store(storage);
  const s = store.load(); s.ha.token = 'kept-out'; s.apiKey = 'nor-this';
  assert.ok(store.save(s));
  assert.doesNotMatch(storage.getItem(Settings.KEY), /kept-out|nor-this|token|apiKey/i);
});

test('a broken storage never breaks the app', () => {
  const store = new Settings.Store({ getItem() { throw new Error('denied'); }, setItem() { throw new Error('full'); } });
  assert.strictEqual(store.load().motion, 'default');
  assert.strictEqual(store.save(Settings.defaults()), false);
});

test('every setting is per-TV for now', () => {
  for (const k of Object.keys(Settings.defaults())) if (k !== 'version') assert.strictEqual(Settings.SCOPE[k], 'device', k);
});

test('Key Manager: values are saved and read there, and Forget removes them', () => {
  const store = {}; const removed = [];
  const km = {
    saveData: (alias, value, pw, ok) => { if (store[alias]) throw new Error('exists'); store[alias] = value; ok(); },
    getData: (alias) => { const name = typeof alias === 'string' ? alias : alias.name; if (!(name in store)) throw new Error('none'); return store[name]; },
    removeAlias: (alias) => { const name = typeof alias === 'string' ? alias : alias.name; removed.push(name); delete store[name]; }
  };
  const v = new Secrets.Vault({ tizen: { keymanager: km } });
  assert.strictEqual(v.mode, 'keymanager'); assert.ok(v.remembers());
  let kept = null; v.put('ha-token', 'example-value', (k) => { kept = k; });
  assert.strictEqual(kept, true);
  assert.strictEqual(new Secrets.Vault({ tizen: { keymanager: km } }).get('ha-token'), 'example-value', 'read back by a new session');
  v.forget('ha-token');
  assert.strictEqual(new Secrets.Vault({ tizen: { keymanager: km } }).get('ha-token'), null);
  assert.throws(() => v.get('anything-else'), /unknown secret/);
});

test('Key Manager missing or refusing: fail closed, nothing written anywhere else', () => {
  const v = new Secrets.Vault({ tizen: {} });
  assert.strictEqual(v.mode, 'unavailable'); assert.ok(!v.remembers());
  let kept = null; v.put('ha-token', 'example-value', (k) => { kept = k; });
  assert.strictEqual(kept, false); assert.match(v.label(), /isn't available/);
  const refusing = new Secrets.Vault({ tizen: { keymanager: { saveData: (a, b, c, ok, err) => err(new Error('no')), getData: () => { throw new Error('no'); } } } });
  refusing.put('nas', '{}', (k) => { kept = k; });
  assert.strictEqual(kept, false);
  assert.strictEqual(new Secrets.Vault({ tizen: { keymanager: { saveData() {}, getData: () => { throw new Error('no'); } } } }).get('nas'), null);
  const sim = new Secrets.Vault({});
  assert.strictEqual(sim.mode, 'simulation'); assert.match(sim.label(), /Browser simulation/);
});

test('the secrets module never touches localStorage or any web store', () => {
  const src = require('node:fs').readFileSync(require('node:path').join(__dirname, '..', 'src', 'js', 'secrets.js'), 'utf8');
  assert.doesNotMatch(src.replace(/\/\*[\s\S]*?\*\//g, ''), /localStorage|sessionStorage|indexedDB|document\.cookie/);
});
