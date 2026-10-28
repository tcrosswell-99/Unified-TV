'use strict';
// The page's result codes and summary line, and whole-package rules for the proof of concept.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const R = require('../js/report.js');

const POC = path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(POC, p), 'utf8');

test('the summary line the owner photographs', () => {
  const pass = { svc: { status: 'PASS', ms: 1240 }, local: { status: 'PASS' },
    ssdp: { status: 'PASS', methods: { mc: { code: 'OK', answers: 0 }, bc: { code: 'OK', answers: 1 }, uni: { code: 'OK', answers: 1 }, notify: { code: 'LISTENING', answers: 1 } } },
    valid: { status: 'PASS', count: 1 }, browse: { status: 'PASS', folders: 5 } };
  assert.strictEqual(R.summaryLine(pass), 'POC v0.1.0 svc=PASS(1.2s) local=PASS ssdp: mc=0 bc=1 uni=1 notify=1 valid=1 browse=PASS(5 folders)');
  const fail = { svc: { status: 'FAIL', code: 'SVC_LAUNCH_ERR' } };
  assert.strictEqual(R.summaryLine(fail), 'POC v0.1.0 svc=FAIL(SVC_LAUNCH_ERR) local=SKIP ssdp=SKIP valid=- browse=SKIP');
  const odd = { svc: { status: 'PASS', ms: 300 }, local: { status: 'PASS' },
    ssdp: { status: 'FAIL', methods: { mc: { code: 'SEND_ERR', answers: 0 }, bc: { code: 'OK', answers: 0 }, uni: { code: 'NOT_DRAINED', answers: 0 }, notify: { code: 'BIND_1900_ERR', answers: 0 } } },
    valid: { status: 'FAIL', count: 0 }, browse: { status: 'SKIP', code: 'BROWSE_SKIPPED' } };
  assert.strictEqual(R.summaryLine(odd), 'POC v0.1.0 svc=PASS(0.3s) local=PASS ssdp: mc=0(SEND_ERR) bc=0 uni=0(NOT_DRAINED) notify=0(BIND_1900_ERR) valid=0 browse=SKIP(BROWSE_SKIPPED)');
  assert.strictEqual(R.summaryLine({ browse: { status: 'PASS', folders: 1 } }).slice(-21), 'browse=PASS(1 folder)');
});

test('a fresh 32-character token per launch, or none at all without secure random numbers', () => {
  let n = 0;
  const t = R.makeToken((b) => { for (let i = 0; i < b.length; i++) b[i] = (n++ * 53) & 255; return b; });
  assert.match(t, /^[0-9a-f]{32}$/);
  assert.strictEqual(R.makeToken(null), '');
  assert.match(R.makeToken((b) => require('node:crypto').randomFillSync(b)), /^[0-9a-f]{32}$/);
});

test('every result code the page and the service can show has a plain reason', () => {
  const page = read('js/poc.js');
  const used = new Set();
  for (const m of page.matchAll(/set\('[a-z]+', '(?:PASS|FAIL|SKIP|WAIT)', '([A-Z_]+)'/g)) used.add(m[1]);
  for (const m of page.matchAll(/lastCode = '([A-Z_]+)'/g)) used.add(m[1]);
  assert.ok(used.size >= 12, 'found the codes: ' + [...used].join(' '));
  for (const c of used) assert.ok(R.REASONS[c], 'reason for ' + c);
  const service = read('service/service.js');
  for (const m of service.matchAll(/methods\.[a-z]+\.code = '([A-Z_0-9]+)'|\bu\.code = '([A-Z_0-9]+)'|self\.code = [^;]*'([A-Z_0-9]+)'/g)) {
    const c = m[1] || m[2] || m[3];
    assert.ok(R.METHOD_REASONS[c], 'method reason for ' + c);
  }
});

test('the package asks only for internet and application.launch, reaches only its loopback service, and has its own id', () => {
  const xml = read('config.xml').replace(/<!--[\s\S]*?-->/g, '');
  const privileges = [...xml.matchAll(/<tizen:privilege name="([^"]+)"/g)].map((m) => m[1]).sort();
  assert.deepStrictEqual(privileges, ['http://tizen.org/privilege/application.launch', 'http://tizen.org/privilege/internet']);
  assert.match(xml, /<tizen:application id="UTVDLNAPOC\.Probe" package="UTVDLNAPOC"/);
  assert.match(xml, /<tizen:service id="UTVDLNAPOC\.svc"[^>]*>\s*<tizen:content src="service\/service\.js"\/>/);
  assert.deepStrictEqual([...xml.matchAll(/<access origin="([^"]+)"/g)].map((m) => m[1]), ['http://127.0.0.1:18731']);
  for (const policy of [/<tizen:content-security-policy>([^<]+)</.exec(xml)[1], /http-equiv="Content-Security-Policy" content="([^"]+)"/.exec(read('index.html'))[1]]) {
    assert.match(policy, /connect-src http:\/\/127\.0\.0\.1:18731;/);
    assert.doesNotMatch(policy, /unsafe-eval|unsafe-inline|\*/);
  }
  assert.doesNotMatch(read('index.html').replace(/<!--[\s\S]*?-->/g, ''), /<script(?![^>]*\bsrc=)[^>]*>|\son[a-z]+\s*=|\sstyle\s*=/i, 'no inline script, handler or style');
});

test('the service binds to loopback only and is never a proxy for an address from the page', () => {
  const s = read('service/service.js');
  assert.match(s, /server\.listen\([^)]*LOOPBACK/);
  assert.doesNotMatch(s, /listen\([^)]*'0\.0\.0\.0'|listen\(\s*PORT\s*\)/);
  const handler = s.slice(s.indexOf('function handler('), s.indexOf('/* ------------------------------------------------------------------ lifecycle'));
  assert.doesNotMatch(handler, /fetchText\(\s*q\.|fetchText\(\s*u\.query/, 'no URL from the query is fetched');
  assert.match(handler, /state\.servers\[parseInt\(q\.s, 10\)\]/, 'Browse is by the index of a server validated in this run');
  assert.doesNotMatch(s, /\.pipe\(|createReadStream|require\('fs'\)/, 'no streaming proxy and no file access');
});

test('the page and the service stay ES5 for the TV\'s old engine and Node build', () => {
  const strip = (x) => x.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:'"\\])\/\/[^\n]*/g, '$1').replace(/'(?:[^'\\\n]|\\.)*'/g, "''");
  for (const f of ['js/report.js', 'js/poc.js', 'service/service.js']) {
    const code = strip(read(f));
    for (const [re, what] of [[/=>/, 'arrow functions'], [/\b(let|const)\s/, 'let/const'], [/`/, 'template literals'], [/\bclass\s+[A-Z]/, 'classes'],
      [/\basync\b|\bawait\b/, 'async'], [/\.\.\.[a-z]/i, 'spread'], [/\?\.|\?\?/, 'optional chaining or nullish coalescing']]) {
      assert.doesNotMatch(code, re, f + ' uses ' + what);
    }
  }
});
