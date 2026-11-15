'use strict';
// Adults and Kids (owner, 25 October 2026; PROFILE-01/02). Mirrors preview/app/test/io/unifiedtv/preview/ProfilesTest.java
// case by case, plus the Frame's own settings storage of the profile layer.
const test = require('node:test');
const assert = require('node:assert');
const Profiles = require('../src/js/profiles.js');
const Settings = require('../src/js/settings.js');

test('stored values stay compatible and labels are Adults and Kids', () => {
  assert.strictEqual(Profiles.stored('child'), 'child');
  for (const old of ['adult', null, undefined, '', 'guest', 'Child']) assert.strictEqual(Profiles.stored(old), 'adult', 'anything else is Adults');
  assert.strictEqual(Profiles.label('adult'), 'Adults'); assert.strictEqual(Profiles.label('child'), 'Kids'); assert.strictEqual(Profiles.label(null), 'Adults');
});
test('moving to Adults always needs the PIN', () => {
  assert.strictEqual(Profiles.rule('child', 'adult', true), Profiles.NEEDS_PIN);
  assert.strictEqual(Profiles.rule('adult', 'adult', true), Profiles.SAME); assert.strictEqual(Profiles.rule('child', 'child', true), Profiles.SAME);
});
test('Kids needs no PIN, but only once one exists', () => {
  assert.strictEqual(Profiles.rule('adult', 'child', true), Profiles.ALLOWED);
  assert.strictEqual(Profiles.rule('adult', 'child', false), Profiles.PIN_FIRST);
});
test('Kids is never a trap when the PIN record is gone', () => {
  assert.strictEqual(Profiles.rule('child', 'adult', false), Profiles.ALLOWED);
});

// PROFILE-01: the last-used profile is kept and opens without asking.
class MemoryStorage {
  constructor() { this.data = {}; }
  getItem(k) { return Object.prototype.hasOwnProperty.call(this.data, k) ? this.data[k] : null; }
  setItem(k, v) { this.data[k] = String(v); }
}
test('the last-used profile survives a restart; a fresh TV opens in Adults', () => {
  const storage = new MemoryStorage();
  const first = new Settings.Store(storage);
  const s = first.load();
  assert.strictEqual(s.profiles.current, 'adult');
  assert.strictEqual(s.profiles.kidsPersonAlerts, true, '"Show person alerts in Kids" is on by default');
  s.profiles.current = 'child'; assert.ok(first.save(s));
  assert.strictEqual(new Settings.Store(storage).load().profiles.current, 'child', 'restart: still Kids, nothing asked');
});
test('profile settings are checked on the way in', () => {
  const r = Settings.migrate(JSON.stringify({ version: 1, profiles: { current: 'teen', kidsApps: 'netflix,xbox,smartutube,app:3201907018807,not an app,,netflix,../x',
    kidsPersonAlerts: 'no', pinFailures: -4, pinWait: 'soon' } }));
  const p = r.settings.profiles;
  assert.strictEqual(p.current, 'adult');
  assert.strictEqual(p.kidsApps, 'netflix,app:3201907018807', 'Android-only apps, junk and duplicates are dropped');
  assert.strictEqual(p.kidsPersonAlerts, true); assert.strictEqual(p.pinFailures, 0); assert.strictEqual(p.pinWait, 0);
  assert.strictEqual(Settings.migrate(JSON.stringify({ version: 1, profiles: { kidsPersonAlerts: false } })).settings.profiles.kidsPersonAlerts, false);
});
test('the PIN itself never reaches web storage: only its lock-out count does', () => {
  const s = Settings.defaults();
  assert.deepStrictEqual(Object.keys(s.profiles).sort(), ['current', 'kidsApps', 'kidsPersonAlerts', 'pinFailures', 'pinWait']);
  const src = require('node:fs').readFileSync(require('node:path').join(__dirname, '..', 'src', 'js', 'app.js'), 'utf8');
  assert.match(src, /vault\.put\('pin'/, 'the PIN record goes to the vault');
  assert.doesNotMatch(src, /S\.profiles\.pin\s*=|localStorage\.setItem\([^)]*pin/i);
});
test('the doorbell name is plain, short text', () => {
  const a = Settings.migrate(JSON.stringify({ version: 1, alerts: { doorName: ' Front Door\u0007 ' } })).settings.alerts;
  assert.strictEqual(a.doorName, 'Front Door');
  assert.strictEqual(Settings.migrate(JSON.stringify({ version: 1, alerts: { doorName: 'x'.repeat(41) } })).settings.alerts.doorName, '');
});
