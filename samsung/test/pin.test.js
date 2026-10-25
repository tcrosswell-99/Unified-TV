'use strict';
// The adult PIN (owner, 25 October 2026; PROFILE-02): a salted PBKDF2-HMAC-SHA-256 hash, SubtleCrypto where present and
// an identical plain-JavaScript fallback where not, and the same lock-out as Android's EditLock.
const test = require('node:test');
const assert = require('node:assert');
const nodeCrypto = require('node:crypto');
const Hash = require('../src/js/hash.js');
const Pin = require('../src/js/pin.js');

const webcrypto = nodeCrypto.webcrypto;
const fallbackOnly = { getRandomValues: (a) => webcrypto.getRandomValues(a) };   // a TV without SubtleCrypto
const run = (fn) => new Promise((resolve) => fn(resolve));

test('SHA-256, HMAC and PBKDF2 match published vectors and Node\'s own crypto', () => {
  assert.strictEqual(Hash.hex(Hash.sha256('abc')), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  assert.strictEqual(Hash.hex(Hash.sha256('')), 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  for (const n of [55, 56, 63, 64, 65, 119, 120, 1000]) {
    const s = 'a'.repeat(n) + 'é😀';
    assert.strictEqual(Hash.hex(Hash.sha256(s)), nodeCrypto.createHash('sha256').update(s).digest('hex'), 'length ' + n);
  }
  assert.strictEqual(Hash.hex(Hash.hmac('key', 'The quick brown fox jumps over the lazy dog')), 'f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8');
  assert.strictEqual(Hash.hex(Hash.hmac('k'.repeat(100), 'm')), nodeCrypto.createHmac('sha256', 'k'.repeat(100)).update('m').digest('hex'), 'a long key is hashed first');
  // RFC 7914 section 11 / the widely published PBKDF2-HMAC-SHA256 vector for P="password", S="salt", c=1.
  assert.strictEqual(Hash.hex(Hash.pbkdf2('password', 'salt', 1)), '120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b');
  assert.strictEqual(Hash.hex(Hash.pbkdf2('password', 'salt', 4096)), nodeCrypto.pbkdf2Sync('password', 'salt', 4096, 32, 'sha256').toString('hex'));
  const r = new Uint8Array([1, 2, 3, 250, 251]);
  assert.strictEqual(Hash.base64(r), Buffer.from(r).toString('base64')); assert.deepStrictEqual(Array.from(Hash.unbase64(Hash.base64(r))), [1, 2, 3, 250, 251]);
  assert.strictEqual(Hash.unbase64('not base64!'), null);
  assert.ok(Hash.equal(new Uint8Array([1, 2]), new Uint8Array([1, 2]))); assert.ok(!Hash.equal(new Uint8Array([1, 2]), new Uint8Array([1, 3]))); assert.ok(!Hash.equal(new Uint8Array([1]), new Uint8Array([1, 2])));
});

test('PINs are 4 to 12 digits', () => {
  for (const ok of ['1234', '000000000000']) assert.ok(Pin.valid(ok), ok);
  for (const bad of ['123', '1234567890123', '12a4', ' 1234', '', null, 1234]) assert.ok(!Pin.valid(bad), String(bad));
});

test('a new PIN is stored as a salted hash, never as the digits, and verifies with SubtleCrypto', async () => {
  const record = await run((done) => Pin.make('4826', { crypto: webcrypto }, (err, r) => { assert.strictEqual(err, null); done(r); }));
  assert.match(record, /^pbkdf2-sha256\$120000\$[A-Za-z0-9+/]{22}==\$[A-Za-z0-9+/]{43}=$/);
  assert.ok(record.indexOf('4826') === -1);
  assert.strictEqual(await run((done) => Pin.verify('4826', record, { crypto: webcrypto }, done)), true);
  assert.strictEqual(await run((done) => Pin.verify('4827', record, { crypto: webcrypto }, done)), false);
  const again = await run((done) => Pin.make('4826', { crypto: webcrypto }, (err, r) => done(r)));
  assert.notStrictEqual(again, record, 'a fresh random salt every time');
  const parsed = Pin.parse(record);
  assert.strictEqual(parsed.iterations, Pin.ITERATIONS); assert.strictEqual(parsed.salt.length, 16);
  assert.strictEqual(Hash.hex(parsed.hash), nodeCrypto.pbkdf2Sync('4826', Buffer.from(parsed.salt), 120000, 32, 'sha256').toString('hex'), 'the same PBKDF2 as Android\'s EditLock');
});

test('without SubtleCrypto the JavaScript fallback gives the identical record, both ways round', async () => {
  const env = { crypto: fallbackOnly, iterations: 2000 };
  const made = await run((done) => Pin.make('90210', env, (err, r) => done(r)));
  assert.strictEqual(await run((done) => Pin.verify('90210', made, { crypto: webcrypto }, done)), true, 'made in JavaScript, checked by SubtleCrypto');
  const subtleMade = await run((done) => Pin.make('90210', { crypto: webcrypto, iterations: 2000 }, (err, r) => done(r)));
  assert.strictEqual(await run((done) => Pin.verify('90210', subtleMade, { crypto: fallbackOnly }, done)), true, 'made by SubtleCrypto, checked in JavaScript');
  assert.strictEqual(await run((done) => Pin.verify('90211', subtleMade, { crypto: fallbackOnly }, done)), false);
  const refusing = { getRandomValues: fallbackOnly.getRandomValues, subtle: { importKey: () => Promise.reject(new Error('NotSupportedError')), deriveBits: () => Promise.reject(new Error('x')) } };
  assert.strictEqual(await run((done) => Pin.verify('90210', made, { crypto: refusing }, done)), true, 'a SubtleCrypto that refuses PBKDF2 falls back');
});

test('fail closed: no random source, no record; a damaged record never matches', async () => {
  assert.strictEqual(await run((done) => Pin.make('1234', { crypto: null }, (err) => done(err))), 'no-random');
  assert.strictEqual(await run((done) => Pin.make('1234', { crypto: { getRandomValues: () => { throw new Error('no'); } } }, (err) => done(err))), 'no-random');
  assert.strictEqual(await run((done) => Pin.make('12', { crypto: webcrypto }, (err) => done(err))), 'invalid');
  for (const bad of [null, '', '1234', 'pbkdf2-sha256$120000$$', 'md5$1$AAAAAAAAAAA=$' + 'A'.repeat(43) + '=', 'pbkdf2-sha256$0$AAAAAAAAAAA=$' + 'A'.repeat(43) + '=']) {
    assert.strictEqual(Pin.parse(bad), null, String(bad));
    assert.strictEqual(await run((done) => Pin.verify('1234', bad, { crypto: webcrypto }, done)), false);
  }
});

test('lock-out: five wrong tries wait 30 seconds, and again after every further wrong try', () => {
  let s = Pin.succeeded();
  for (let i = 1; i < 5; i++) { s = Pin.failed(s, 1000); assert.strictEqual(Pin.locked(s, 1000), 0, 'try ' + i); }
  s = Pin.failed(s, 1000);
  assert.strictEqual(s.failures, 5); assert.strictEqual(Pin.locked(s, 1000), 30000); assert.strictEqual(Pin.locked(s, 30999), 1); assert.strictEqual(Pin.locked(s, 31000), 0);
  s = Pin.failed(s, 40000); assert.strictEqual(Pin.locked(s, 40000), 30000, 'the sixth wrong try locks again');
  assert.deepStrictEqual(Pin.succeeded(), { failures: 0, until: 0 }, 'a right PIN clears it');
  assert.strictEqual(Pin.locked({ failures: 5, until: 1e12 }, 1000), 0, 'a wait further ahead than one lock-out (clock moved) never locks for good');
  assert.strictEqual(Pin.locked(null, 1000), 0); assert.strictEqual(Pin.failed(null, 0).failures, 1);
});
