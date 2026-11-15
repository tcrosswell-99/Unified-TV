/*
 * Unified TV for Samsung Frame: SHA-256, HMAC-SHA-256 and PBKDF2-HMAC-SHA-256 in plain ES2015.
 *
 * Why this exists: the adult PIN is kept as a salted PBKDF2 hash (pin.js), and local resume keys are one-way hashes of
 * the stream address (resume.js), exactly as on Android (EditLock, ResumeStore). The browser's SubtleCrypto is used for
 * PBKDF2 where the TV offers it, but a Tizen app page may not count as a "secure context", so SubtleCrypto may be
 * missing on the 2021 Frame; this file is the fallback. Its output is checked against published test vectors and against
 * Node's own crypto in samsung/test/pin.test.js. No secrets live here; it is arithmetic only.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Hash = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const K = new Uint32Array([
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2]);
  const IV = new Uint32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
  const W = new Uint32Array(64);

  /** One SHA-256 compression: out = init + f(init, block of 16 big-endian words). out may be init. */
  function compress(out, init, block) {
    let i;
    for (i = 0; i < 16; i++) W[i] = block[i];
    for (i = 16; i < 64; i++) {
      const x = W[i - 15], y = W[i - 2];
      const s0 = ((x >>> 7) | (x << 25)) ^ ((x >>> 18) | (x << 14)) ^ (x >>> 3);
      const s1 = ((y >>> 17) | (y << 15)) ^ ((y >>> 19) | (y << 13)) ^ (y >>> 10);
      W[i] = (W[i - 16] + s0 + W[i - 7] + s1) | 0;
    }
    let a = init[0], b = init[1], c = init[2], d = init[3], e = init[4], f = init[5], g = init[6], h = init[7];
    for (i = 0; i < 64; i++) {
      const S1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
      const ch = (e & f) ^ (~e & g);
      const t1 = (h + S1 + ch + K[i] + W[i]) | 0;
      const S0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const t2 = (S0 + maj) | 0;
      h = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0;
    }
    out[0] = init[0] + a; out[1] = init[1] + b; out[2] = init[2] + c; out[3] = init[3] + d;
    out[4] = init[4] + e; out[5] = init[5] + f; out[6] = init[6] + g; out[7] = init[7] + h;
  }

  /** Text to UTF-8 bytes, without TextEncoder (not assumed on the 2021 engine). */
  function utf8(text) {
    const s = String(text);
    const out = [];
    for (let i = 0; i < s.length; i++) {
      let c = s.charCodeAt(i);
      if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length) {
        const lo = s.charCodeAt(i + 1);
        if (lo >= 0xdc00 && lo <= 0xdfff) { c = 0x10000 + ((c - 0xd800) << 10) + (lo - 0xdc00); i++; }
      }
      if (c < 0x80) out.push(c);
      else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
      else if (c < 0x10000) out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
      else out.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
    }
    return new Uint8Array(out);
  }
  function bytes(v) { return typeof v === 'string' ? utf8(v) : new Uint8Array(v); }

  /** Hashes message bytes, continuing from state after prefixLength bytes were already compressed. */
  function finish(state, prefixLength, message) {
    const m = bytes(message);
    const total = prefixLength + m.length;
    const padded = ((m.length + 9 + 63) >> 6) << 6;
    const buf = new Uint8Array(padded);
    buf.set(m); buf[m.length] = 0x80;
    const hi = Math.floor(total / 0x20000000), lo = (total * 8) >>> 0;
    buf[padded - 8] = hi >>> 24; buf[padded - 7] = hi >>> 16; buf[padded - 6] = hi >>> 8; buf[padded - 5] = hi;
    buf[padded - 4] = lo >>> 24; buf[padded - 3] = lo >>> 16; buf[padded - 2] = lo >>> 8; buf[padded - 1] = lo;
    const h = new Uint32Array(state);
    const block = new Uint32Array(16);
    for (let off = 0; off < padded; off += 64) {
      for (let i = 0; i < 16; i++) { const p = off + i * 4; block[i] = (buf[p] << 24) | (buf[p + 1] << 16) | (buf[p + 2] << 8) | buf[p + 3]; }
      compress(h, h, block);
    }
    return wordsToBytes(h);
  }
  function wordsToBytes(h) {
    const out = new Uint8Array(32);
    for (let i = 0; i < 8; i++) { out[i * 4] = h[i] >>> 24; out[i * 4 + 1] = h[i] >>> 16; out[i * 4 + 2] = h[i] >>> 8; out[i * 4 + 3] = h[i]; }
    return out;
  }
  /** SHA-256 of a string (UTF-8) or bytes; 32 bytes. */
  function sha256(message) { return finish(IV, 0, message); }

  /** The inner and outer states after the HMAC key block, so each message costs only its own blocks. */
  function hmacKey(key) {
    let k = bytes(key);
    if (k.length > 64) k = sha256(k);
    const inner = new Uint32Array(16), outer = new Uint32Array(16);
    for (let i = 0; i < 16; i++) {
      let w = 0;
      for (let j = 0; j < 4; j++) { const p = i * 4 + j; w = (w << 8) | (p < k.length ? k[p] : 0); }
      inner[i] = w ^ 0x36363636; outer[i] = w ^ 0x5c5c5c5c;
    }
    const ki = new Uint32Array(8), ko = new Uint32Array(8);
    compress(ki, IV, inner); compress(ko, IV, outer);
    return { inner: ki, outer: ko };
  }
  function hmacWith(keyed, message) { return finish(keyed.outer, 64, finish(keyed.inner, 64, message)); }
  function hmac(key, message) { return hmacWith(hmacKey(key), message); }

  /**
   * PBKDF2-HMAC-SHA-256 with a 32-byte result (one block, as the PIN record uses). Synchronous: each iteration is two
   * compressions on a precomputed key, which is about as fast as plain JavaScript gets.
   */
  function pbkdf2(password, salt, iterations) {
    const keyed = hmacKey(password);
    const s = bytes(salt);
    const first = new Uint8Array(s.length + 4);
    first.set(s); first[s.length + 3] = 1;
    const u = hmacWith(keyed, first);
    const t = new Uint32Array(8), cur = new Uint32Array(8);
    for (let i = 0; i < 8; i++) { cur[i] = (u[i * 4] << 24) | (u[i * 4 + 1] << 16) | (u[i * 4 + 2] << 8) | u[i * 4 + 3]; t[i] = cur[i]; }
    // A 32-byte message after a 64-byte key block always pads the same way: 0x80, zeros, length 96 bytes = 768 bits.
    const block = new Uint32Array(16); block[8] = 0x80000000; block[15] = 768;
    const mid = new Uint32Array(8);
    for (let n = 1; n < iterations; n++) {
      for (let i = 0; i < 8; i++) block[i] = cur[i];
      compress(mid, keyed.inner, block);
      for (let i = 0; i < 8; i++) block[i] = mid[i];
      compress(cur, keyed.outer, block);
      for (let i = 0; i < 8; i++) t[i] ^= cur[i];
    }
    return wordsToBytes(t);
  }

  function hex(b) {
    let s = '';
    for (let i = 0; i < b.length; i++) s += (b[i] < 16 ? '0' : '') + b[i].toString(16);
    return s;
  }
  const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  function base64(b) {
    let s = '';
    for (let i = 0; i < b.length; i += 3) {
      const n = (b[i] << 16) | ((i + 1 < b.length ? b[i + 1] : 0) << 8) | (i + 2 < b.length ? b[i + 2] : 0);
      s += B64.charAt(n >> 18) + B64.charAt((n >> 12) & 63) + (i + 1 < b.length ? B64.charAt((n >> 6) & 63) : '=') + (i + 2 < b.length ? B64.charAt(n & 63) : '=');
    }
    return s;
  }
  /** Standard base64 to bytes; null for anything malformed. */
  function unbase64(s) {
    if (typeof s !== 'string' || !/^[A-Za-z0-9+/]*={0,2}$/.test(s) || s.length % 4 !== 0) return null;
    const body = s.replace(/=+$/, '');
    const out = [];
    let bits = 0, acc = 0;
    for (let i = 0; i < body.length; i++) {
      acc = (acc << 6) | B64.indexOf(body.charAt(i)); bits += 6;
      if (bits >= 8) { bits -= 8; out.push((acc >> bits) & 255); }
    }
    return new Uint8Array(out);
  }
  /** Same length and same bytes, without stopping at the first difference. */
  function equal(a, b) {
    if (!a || !b || a.length !== b.length) return false;
    let d = 0;
    for (let i = 0; i < a.length; i++) d |= a[i] ^ b[i];
    return d === 0;
  }

  return { sha256: sha256, hmac: hmac, pbkdf2: pbkdf2, utf8: utf8, hex: hex, base64: base64, unbase64: unbase64, equal: equal };
}));
