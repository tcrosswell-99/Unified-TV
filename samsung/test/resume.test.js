'use strict';
// Local resume, one store per profile (owner, 25 October 2026; CW-01). Mirrors
// preview/app/test/io/unifiedtv/preview/ResumeStoreTest.java case by case, with a documentation-only address.
const test = require('node:test');
const assert = require('node:assert');
const nodeCrypto = require('node:crypto');
const Resume = require('../src/js/resume.js');

// RFC 5737 documentation address, built from parts so no address literal sits in the source.
const STREAM = 'http://' + [192, 0, 2, 20].join('.') + ':50001/MediaItems/42.mkv';

test('keys are one-way and stable, and the same as Android\'s', () => {
  const key = Resume.key(STREAM);
  assert.strictEqual(key.length, 32); assert.strictEqual(key, Resume.key(STREAM)); assert.notStrictEqual(key, Resume.key(STREAM + 'x'));
  assert.strictEqual(key, nodeCrypto.createHash('sha256').update('unified-tv-resume\n' + STREAM).digest('hex').substring(0, 32), 'first 16 bytes of SHA-256, as ResumeStore.key');
  const store = new Resume.Store(''); store.record(key, 1800000, 6000000, 1000);
  const json = store.json();
  for (const part of [STREAM, [192, 0, 2, 20].join('.'), '42.mkv', 'MediaItems']) assert.strictEqual(json.indexOf(part), -1, 'no address, title or file name is stored');
});
test('remembers, clears and expires', () => {
  const store = new Resume.Store(null); const key = Resume.key(STREAM);
  store.record(key, 1800000, 6000000, 1000); assert.strictEqual(store.position(key, 2000), 1800000);
  const reloaded = new Resume.Store(store.json()); assert.strictEqual(reloaded.position(key, 2000), 1800000);
  assert.strictEqual(reloaded.position(key, 1000 + Resume.EXPIRY_MS + 1), 0, 'expired');
  store.record(key, 5990000, 6000000, 3000); assert.strictEqual(store.position(key, 4000), 0, 'watched to the end clears the place'); assert.strictEqual(store.size(), 0);
  store.record(key, 1800000, 6000000, 1000); store.record(key, 10000, 6000000, 5000); assert.strictEqual(store.position(key, 6000), 0, 'barely started again clears it');
});
test('bounded, oldest first', () => {
  const store = new Resume.Store('');
  for (let i = 0; i < Resume.MAX + 15; i++) store.record(Resume.key('film' + i), 600000, 6000000, 10000 + i);
  assert.strictEqual(store.size(), Resume.MAX);
  assert.strictEqual(store.position(Resume.key('film0'), 20000), 0);
  assert.strictEqual(store.position(Resume.key('film' + (Resume.MAX + 14)), 20000), 600000);
});
test('a damaged store starts empty, and junk entries are dropped', () => {
  assert.strictEqual(new Resume.Store('{not json').size(), 0);
  assert.strictEqual(new Resume.Store('[1,2]').size(), 0);
  assert.strictEqual(new Resume.Store(JSON.stringify({ 'not-a-key': { pos: 1, at: 1 }, ['a'.repeat(32)]: { pos: 'x', at: 1 } })).size(), 0);
});
test('the resume rules match PlaybackPolicy', () => {
  assert.strictEqual(Resume.resumePoint(29999, 6000000), 0, 'under 30 seconds is not worth offering');
  assert.strictEqual(Resume.resumePoint(30000, 6000000), 30000);
  assert.ok(Resume.finished(5910000, 6000000), 'the last 90 seconds count as watched');
  assert.ok(!Resume.finished(5909999, 6000000));
  assert.ok(Resume.finished(540000, 600000), 'a short video: its last tenth');
  assert.ok(!Resume.finished(1000000, 0), 'an unknown length is never finished');
});
test('each profile has its own store', () => {
  assert.strictEqual(Resume.storageKey('adult'), 'unifiedtv.frame.places-adults'); assert.strictEqual(Resume.storageKey('child'), 'unifiedtv.frame.places-kids');
  assert.strictEqual(Resume.storageKey(null), Resume.storageKey('adult'), 'unknown values are Adults, as the profile itself is');
  const data = {};
  const storage = { getItem: (k) => (k in data ? data[k] : null), setItem: (k, v) => { data[k] = String(v); } };
  const adults = Resume.load(storage, 'adult'); adults.record(Resume.key(STREAM), 900000, 6000000, 1000); assert.ok(Resume.save(storage, 'adult', adults));
  assert.strictEqual(Resume.load(storage, 'child').position(Resume.key(STREAM), 2000), 0, 'Kids never sees an adult\'s place');
  assert.strictEqual(Resume.load(storage, 'adult').position(Resume.key(STREAM), 2000), 900000, 'and it survives a restart');
  const broken = { getItem() { throw new Error('denied'); }, setItem() { throw new Error('full'); } };
  assert.strictEqual(Resume.load(broken, 'adult').size(), 0); assert.strictEqual(Resume.save(broken, 'adult', adults), false); assert.strictEqual(Resume.save(null, 'adult', adults), false);
});
