/*
 * Unified TV for Samsung Frame: the adult PIN (owner, 25 October 2026; spec 2 "Profiles", 13).
 *
 * The same rules as Android's EditLock: 4 to 12 digits, kept only as a salted PBKDF2-HMAC-SHA-256 hash (120,000
 * iterations, 16-byte random salt, 32-byte result), and five wrong tries in a row lock it for 30 seconds, again after
 * every further wrong try. The record is "pbkdf2-sha256$<iterations>$<salt base64>$<hash base64>"; the digits are never
 * stored. app.js keeps the record only in the TV's Key Manager through secrets.js, and fails closed: with no Key Manager
 * there is no PIN, and without a PIN Kids can't be switched on.
 *
 * SubtleCrypto's PBKDF2 is used where the TV offers it. A Tizen app page may not count as a secure context, so it may
 * be missing on the 2021 Frame; then the plain-JavaScript PBKDF2 in hash.js gives the identical result (a test checks
 * both against each other). How long the fallback takes on the Frame is unknown until it is timed there.
 * A random salt needs crypto.getRandomValues: without it no PIN is made at all.
 */
(function (root, factory) {
  'use strict';
  var api = factory(typeof require === 'function' ? require('./hash.js') : root.UTV.Hash);
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Pin = api; }
}(typeof self !== 'undefined' ? self : this, function (Hash) {
  'use strict';

  const ITERATIONS = 120000, SALT_BYTES = 16, LOCK_AFTER = 5, WAIT_MS = 30000;
  const PREFIX = 'pbkdf2-sha256';

  function valid(pin) { return typeof pin === 'string' && /^[0-9]{4,12}$/.test(pin); }

  /** A stored record as {iterations, salt, hash}; null when it is missing or malformed. */
  function parse(record) {
    if (typeof record !== 'string') return null;
    const p = record.split('$');
    if (p.length !== 4 || p[0] !== PREFIX || !/^[1-9][0-9]{0,6}$/.test(p[1])) return null;
    const salt = Hash.unbase64(p[2]), hash = Hash.unbase64(p[3]);
    if (!salt || salt.length < 8 || !hash || hash.length !== 32) return null;
    return { iterations: parseInt(p[1], 10), salt: salt, hash: hash };
  }
  function format(iterations, salt, hash) { return PREFIX + '$' + iterations + '$' + Hash.base64(salt) + '$' + Hash.base64(hash); }

  /** PBKDF2 through SubtleCrypto when present, otherwise in JavaScript. done(bytes). Never throws. */
  function derive(pin, salt, iterations, env, done) {
    const c = env && env.crypto;
    const subtle = c && c.subtle;
    function fallback() { let out = null; try { out = Hash.pbkdf2(pin, salt, iterations); } catch (e) { out = null; } done(out); }
    if (!subtle || typeof subtle.importKey !== 'function' || typeof subtle.deriveBits !== 'function' || (env && env.forceFallback)) { fallback(); return; }
    let settled = false;
    try {
      subtle.importKey('raw', Hash.utf8(pin), { name: 'PBKDF2' }, false, ['deriveBits'])
        .then(function (key) { return subtle.deriveBits({ name: 'PBKDF2', salt: salt, iterations: iterations, hash: 'SHA-256' }, key, 256); })
        .then(function (bits) { settled = true; done(new Uint8Array(bits)); }, function () { if (!settled) { settled = true; fallback(); } });
    } catch (e) { if (!settled) { settled = true; fallback(); } }
  }

  /** Makes a record for a new PIN. done(error, record): error 'invalid' or 'no-random'. */
  function make(pin, env, done) {
    if (!valid(pin)) { done('invalid', null); return; }
    const c = env && env.crypto;
    if (!c || typeof c.getRandomValues !== 'function') { done('no-random', null); return; }
    const salt = new Uint8Array(SALT_BYTES);
    try { c.getRandomValues(salt); } catch (e) { done('no-random', null); return; }
    const iterations = env && env.iterations > 0 ? env.iterations : ITERATIONS;
    derive(pin, salt, iterations, env, function (hash) {
      if (!hash || hash.length !== 32) done('failed', null); else done(null, format(iterations, salt, hash));
    });
  }
  /** done(true) only when the PIN matches the record. A missing or damaged record never matches. */
  function verify(pin, record, env, done) {
    const r = parse(record);
    if (!r || !valid(pin)) { done(false); return; }
    derive(pin, r.salt, r.iterations, env, function (hash) { done(!!hash && Hash.equal(hash, r.hash)); });
  }

  /**
   * Lock-out, as on Android: { failures, until }. Milliseconds still to wait, or 0. A wait further ahead than one lock-out
   * (the TV's clock moved back) is treated as over, so the PIN can never be locked for longer than 30 seconds.
   */
  function locked(state, now) {
    const until = state && Number(state.until) > 0 ? Number(state.until) : 0;
    const left = until - now;
    return left > 0 && left <= WAIT_MS ? left : 0;
  }
  function failed(state, now) {
    const failures = Math.min(1000, (state && Number(state.failures) > 0 ? Math.floor(Number(state.failures)) : 0) + 1);
    return { failures: failures, until: failures >= LOCK_AFTER ? now + WAIT_MS : 0 };
  }
  function succeeded() { return { failures: 0, until: 0 }; }

  return { ITERATIONS: ITERATIONS, SALT_BYTES: SALT_BYTES, LOCK_AFTER: LOCK_AFTER, WAIT_MS: WAIT_MS,
    valid: valid, parse: parse, make: make, verify: verify, locked: locked, failed: failed, succeeded: succeeded };
}));
