/*
 * Unified TV for Samsung Frame: where each film was left, on this TV only (spec 9 "Continue Watching", owner
 * 25 October 2026).
 *
 * A port of Android's ResumeStore and PlaybackPolicy's resume rules (preview/app/src/io/unifiedtv/preview/
 * ResumeStore.java, PlaybackPolicy.java); samsung/test/resume.test.js mirrors ResumeStoreTest. One store per profile:
 * Adults and Kids never see each other's places. Keys are one-way SHA-256 hashes of the stream address, so no titles,
 * folders or addresses are stored. Bounded to 60 places, oldest first, and a place expires after 180 days. Kept in this
 * TV's web storage (nothing secret: hashes and numbers). It is offered as "Resume from ... (on this TV)", never shown as
 * Continue Watching and never sent to a server. The Frame had no saved places before this, so there is nothing to move.
 */
(function (root, factory) {
  'use strict';
  var api = factory(typeof require === 'function' ? require('./hash.js') : root.UTV.Hash);
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Resume = api; }
}(typeof self !== 'undefined' ? self : this, function (Hash) {
  'use strict';

  const MAX = 60, EXPIRY_MS = 180 * 24 * 60 * 60 * 1000;
  const MIN_RESUME_MS = 30000, END_MARGIN_MS = 90000;

  /** Near enough the end to count as watched, so the place is cleared. */
  function finished(position, duration) { return duration > 0 && position >= duration - Math.min(END_MARGIN_MS, duration / 10); }
  /** The place to offer on this TV, or 0 to start from the beginning. */
  function resumePoint(saved, duration) { return saved < MIN_RESUME_MS || finished(saved, duration) ? 0 : saved; }
  function key(stableId) { return Hash.hex(Hash.sha256('unified-tv-resume\n' + String(stableId))).substring(0, 32); }
  function storageKey(profile) { return profile === 'child' ? 'unifiedtv.frame.places-kids' : 'unifiedtv.frame.places-adults'; }

  class Store {
    constructor(json) {
      let parsed = null;
      try { parsed = typeof json === 'string' && json ? JSON.parse(json) : null; } catch (e) { parsed = null; }
      this.entries = {};
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return;
      const self = this;
      Object.keys(parsed).forEach(function (k) {
        const e = parsed[k];
        if (/^[0-9a-f]{32}$/.test(k) && e && typeof e === 'object' && typeof e.pos === 'number' && typeof e.at === 'number') {
          self.entries[k] = { pos: Math.max(0, e.pos), dur: typeof e.dur === 'number' ? Math.max(0, e.dur) : 0, at: e.at };
        }
      });
      this.trim();
    }
    /** Saved position in milliseconds, or 0 when there is none or it has expired. */
    position(k, now) {
      const e = Object.prototype.hasOwnProperty.call(this.entries, k) ? this.entries[k] : null;
      if (!e || now - e.at > EXPIRY_MS) return 0;
      return Math.max(0, e.pos);
    }
    /** Records a position, or clears it when the film was watched to the end or barely started. */
    record(k, position, duration, now) {
      if (resumePoint(position, duration) === 0) { delete this.entries[k]; return; }
      this.entries[k] = { pos: position, dur: Math.max(0, duration || 0), at: now };
      this.trim();
    }
    remove(k) { delete this.entries[k]; }
    size() { return Object.keys(this.entries).length; }
    trim() {
      const keys = Object.keys(this.entries);
      if (keys.length <= MAX) return;
      const self = this;
      keys.sort(function (a, b) { return self.entries[a].at - self.entries[b].at; });
      keys.slice(0, keys.length - MAX).forEach(function (k) { delete self.entries[k]; });
    }
    json() { return JSON.stringify(this.entries); }
  }

  /** The profile's places from web storage; an unreadable or blocked store starts empty. */
  function load(storage, profile) {
    let raw = null;
    try { raw = storage ? storage.getItem(storageKey(profile)) : null; } catch (e) { raw = null; }
    return new Store(raw);
  }
  function save(storage, profile, store) {
    if (!storage) return false;
    try { storage.setItem(storageKey(profile), store.json()); return true; } catch (e) { return false; }
  }

  return { MAX: MAX, EXPIRY_MS: EXPIRY_MS, MIN_RESUME_MS: MIN_RESUME_MS, END_MARGIN_MS: END_MARGIN_MS, finished: finished,
    resumePoint: resumePoint, key: key, storageKey: storageKey, Store: Store, load: load, save: save };
}));
