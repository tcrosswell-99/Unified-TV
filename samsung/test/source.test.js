'use strict';
// Whole-package checks: the UK clock, the 2021 engine's syntax limits, no network fonts or CDNs, no VLC, no household
// data, only the documented privileges, no certificate material, and a page that loads every module it needs.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const Clock = require('../src/js/clock.js');

const SAMSUNG = path.resolve(__dirname, '..');
function walk(dir) {
  let out = [];
  for (const name of fs.readdirSync(dir)) {
    if (name === 'out' || name === 'node_modules') continue;
    const p = path.join(dir, name);
    if (fs.statSync(p).isDirectory()) out = out.concat(walk(p)); else out.push(p);
  }
  return out;
}
const SRC = walk(path.join(SAMSUNG, 'src'));
const ALL = walk(SAMSUNG).concat([path.resolve(SAMSUNG, '..', 'docs', 'SAMSUNG_HOME_PROPOSAL.md'), path.resolve(SAMSUNG, '..', 'docs', 'devices', 'samsung-frame-2021.md'),
  path.resolve(SAMSUNG, '..', '.github', 'workflows', 'samsung.yml')]).filter((p) => fs.existsSync(p));
const read = (p) => fs.readFileSync(p, 'utf8');
const stripComments = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:'"\\])\/\/[^\n]*/g, '$1');

test('the clock is 24-hour UK time and follows British Summer Time, with or without Intl', () => {
  const bst = Date.parse('2026-10-25T00:30:00Z');   // still BST: 01:30
  const gmt = Date.parse('2026-10-25T01:30:00Z');   // clocks went back at 01:00 UTC: 01:30 GMT
  assert.strictEqual(Clock.format(bst, { manual: true }), '01:30');
  assert.strictEqual(Clock.format(gmt, { manual: true }), '01:30');
  assert.strictEqual(Clock.format(Date.parse('2026-07-01T23:05:00Z')), '00:05');
  assert.strictEqual(Clock.format(Date.parse('2026-01-15T13:07:00Z')), '13:07');
  assert.strictEqual(Clock.format(Date.parse('2026-03-29T00:59:00Z'), { manual: true }), '00:59');
  assert.strictEqual(Clock.format(Date.parse('2026-03-29T01:00:00Z'), { manual: true }), '02:00');
  assert.strictEqual(Clock.format(Date.parse('2026-01-15T13:07:00Z'), { hour12: true }), '1:07 pm');
  assert.strictEqual(Clock.date(Date.parse('2026-10-25T12:00:00Z')), 'Sunday 25 October');
  assert.strictEqual(Clock.untilNextMinute(60000 * 5 + 1500), 58500);
});

test('scripts stay within ES2015 for the 2021 Frame\'s browser engine', () => {
  const forbidden = [[/\?\./, 'optional chaining'], [/\?\?/, 'nullish coalescing'], [/\basync\s+(function|\(|[a-z])/, 'async functions'], [/\bawait\s/, 'await'],
    [/\*\*/, 'exponent operator'], [/\.includes\(/, 'Array/String includes (ES2016)'], [/Object\.(entries|values|fromEntries)\(/, 'ES2017+ Object helpers'],
    [/\.padStart\(|\.padEnd\(/, 'string padding (ES2017)'], [/\.flat(Map)?\(/, 'flat (ES2019)'], [/catch\s*\{/, 'optional catch binding (ES2019)'],
    [/\{\s*\.\.\.|,\s*\.\.\.[a-z]/i, 'object spread'], [/\bimport\s|\bexport\s/, 'modules']];
  for (const file of SRC.filter((p) => p.endsWith('.js'))) {
    const code = stripComments(read(file)).replace(/'(?:[^'\\\n]|\\.)*'/g, "''").replace(/\/(?:[^/\\\n]|\\.)+\/[gimuy]*/g, '/r/');
    for (const [re, what] of forbidden) assert.doesNotMatch(code, re, path.basename(file) + ' uses ' + what);
  }
});

test('no CDNs, network fonts, frameworks or remote scripts', () => {
  for (const file of SRC.filter((p) => /\.(html|css|js)$/.test(p))) {
    const text = stripComments(read(file));
    assert.doesNotMatch(text, /<script[^>]+src="https?:|@import|@font-face|fonts\.googleapis|cdn\.|unpkg|jsdelivr|<link[^>]+href="https?:/i, path.basename(file));
  }
});

test('no VLC anywhere in the app, and no hand-off to another player', () => {
  for (const file of SRC) if (!/\.(png|jpg)$/.test(file)) assert.doesNotMatch(stripComments(read(file)), /vlc|videolan/i, path.basename(file));
});

test('no IP addresses, tokens, passwords, serial numbers or personal paths in any Samsung file', () => {
  const ipv4 = /\b(?:\d{1,3}\.){3}\d{1,3}\b/;
  const secretValue = /(token|password|secret|api[_-]?key)["']?\s*[:=]\s*["'][A-Za-z0-9._\-]{12,}["']/i;
  for (const file of ALL.filter((p) => !/\.(png|jpg)$/.test(p))) {
    const text = read(file);
    assert.doesNotMatch(text, ipv4, path.relative(SAMSUNG, file) + ' contains an IPv4 address');
    assert.doesNotMatch(text, secretValue, path.relative(SAMSUNG, file) + ' contains what looks like a secret');
    assert.doesNotMatch(text, /\/home\/[a-z]|C:\\Users\\|\/Users\/[a-z]/, path.relative(SAMSUNG, file) + ' contains a personal path');
    assert.doesNotMatch(text, new RegExp('BEGIN (RSA |EC )?PRIVATE' + ' KEY|BEGIN CERT' + 'IFICATE'), path.relative(SAMSUNG, file) + ' contains key or certificate material');
  }
});

test('config.xml asks only for the privileges the code uses, and has a placeholder identity', () => {
  const raw = read(path.join(SAMSUNG, 'config.xml'));
  const xml = raw.replace(/<!--[\s\S]*?-->/g, '');
  const privileges = (xml.match(/<tizen:privilege name="([^"]+)"/g) || []).map((m) => m.replace(/.*name="|"$/g, '')).sort();
  assert.deepStrictEqual(privileges, ['http://tizen.org/privilege/application.info', 'http://tizen.org/privilege/application.launch',
    'http://tizen.org/privilege/internet', 'http://tizen.org/privilege/tv.inputdevice']);
  const code = SRC.filter((p) => p.endsWith('.js')).map(read).join('\n');
  assert.match(code, /registerKey/); assert.match(code, /application\.launch|app\.launch\(/); assert.match(code, /getAppsInfo/); assert.match(code, /WebSocket/);
  assert.match(xml, /package="UTVFRAME00"/); assert.match(raw, /PLACEHOLDER IDENTITY/);
  assert.match(xml, /<tizen:profile name="tv-samsung"\/>/);
  assert.doesNotMatch(xml, /tv\.window|tv\.channel|filesystem|appmanager\.kill|packagemanager/i);
  for (const name of walk(SAMSUNG).map((p) => path.basename(p).toLowerCase())) {
    assert.ok(!/^(author-)?signature\d*\.xml$|\.(p12|pfx|pem|jks|keystore)$/.test(name), 'no signing material: ' + name);
  }
});

test('the page loads every module before app.js, and the build generates the palette script', () => {
  const html = read(path.join(SAMSUNG, 'src', 'index.html'));
  const scripts = (html.match(/<script src="([^"]+)"/g) || []).map((m) => m.replace(/.*src="|"$/g, ''));
  assert.strictEqual(scripts[scripts.length - 1], 'js/app.js');
  for (const s of scripts) {
    if (s.indexOf('$WEBAPIS') === 0 || s === 'js/palettes.data.js' || s === 'js/wallpaper.data.js') continue;
    assert.ok(fs.existsSync(path.join(SAMSUNG, 'src', s)), s + ' exists');
  }
  assert.ok(scripts.indexOf('js/alerts.js') < scripts.indexOf('js/ha.js'), 'ha.js needs alerts.js first');
  assert.ok(scripts.indexOf('js/apps.js') < scripts.indexOf('js/settings.js') && scripts.indexOf('js/wallpaper.js') < scripts.indexOf('js/settings.js'));
  assert.match(read(path.join(SAMSUNG, 'tools', 'package.py')), /palettes\.data\.js[\s\S]*wallpaper\.data\.js/);
});

test('animations touch only opacity and transform; nothing animates blur or backdrop-filter', () => {
  const css = read(path.join(SAMSUNG, 'src', 'css', 'home.css'));
  assert.doesNotMatch(stripComments(css), /backdrop-filter|filter\s*:\s*blur/);
  for (const m of stripComments(css).match(/transition(-property)?\s*:[^;]+/g) || []) assert.match(m, /opacity|transform|background-color|none/, m);
});

test('the modules load in a browser-like global without Node\'s require', () => {
  const vm = require('node:vm');
  const sandbox = { self: {}, Date, Math, JSON, Map, Array, Object, String, Number, RegExp, Error, parseInt, parseFloat, isNaN, Infinity, URL };
  sandbox.self = sandbox;
  vm.createContext(sandbox);
  for (const f of ['mesh', 'focus', 'keys', 'nav', 'clock', 'apps', 'livetv', 'alerts', 'ha', 'wallpaper', 'settings', 'secrets', 'player', 'dlna', 'capabilities']) {
    vm.runInContext(read(path.join(SAMSUNG, 'src', 'js', f + '.js')), sandbox, { filename: f + '.js' });
  }
  for (const k of ['Mesh', 'Focus', 'Keys', 'Nav', 'Clock', 'Apps', 'LiveTv', 'Alerts', 'Ha', 'Wallpaper', 'Settings', 'Secrets', 'Player', 'Dlna', 'Capabilities']) assert.ok(sandbox.UTV[k], k);
  assert.strictEqual(sandbox.UTV.Settings.defaults().motion, 'default');
  assert.ok(sandbox.UTV.Capabilities.list().every((c) => c.value === 'unknown'), 'nothing is claimed as verified on the Frame');
});

// A Content-Security-Policy is not set yet: the wrong policy could block AVPlay's <object> or the HA and NAS connections on
// the Frame, and that can only be checked on the TV. Until then the page stays ready for one: no inline script, no inline
// event handlers, no style attributes and no eval, so adding the policy later needs no code changes.
test('the page stays ready for a Content-Security-Policy', () => {
  const html = read(path.join(SAMSUNG, 'src', 'index.html')).replace(/<!--[\s\S]*?-->/g, '');
  assert.ok(!/<script(?![^>]*\bsrc=)[^>]*>/i.test(html), 'inline <script> in index.html');
  assert.ok(!/\son[a-z]+\s*=/i.test(html), 'inline event handler in index.html');
  assert.ok(!/\sstyle\s*=/i.test(html), 'style attribute in index.html');
  for (const p of SRC.filter((f) => f.endsWith('.js'))) {
    const s = stripComments(read(p));
    assert.ok(!/\beval\s*\(|new\s+Function\s*\(|set(Timeout|Interval)\s*\(\s*['"`]/.test(s), 'eval-like code in ' + path.basename(p));
    assert.ok(!/setAttribute\(\s*['"](style|on[a-z]+)['"]/.test(s), 'inline style or handler attribute set in ' + path.basename(p));
  }
});
