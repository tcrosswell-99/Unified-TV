/*
 * Unified TV for Samsung Frame: what Kids may see and open (owner, 25 October 2026; spec 2, 9).
 *
 * A port of Android's KidsPolicy (preview/app/src/io/unifiedtv/preview/KidsPolicy.java); samsung/test/kids.test.js
 * mirrors KidsPolicyTest case by case. Pure: the screens and the player ask, nothing here touches the network or the TV.
 *
 * NAS: an adult (Adults, behind the PIN) chooses Kids folders on the media server. They are kept like the household's
 * folders, in Key Manager (secrets "nas-kids"), with the media server they belong to. In Kids, browsing and playback stay
 * inside them, and before every Kids title plays, the server's own parent chain is walked up to a Kids folder again
 * (playable). Hiding a title is never the only wall.
 *
 * Ratings, where the server sends one (DIDL-Lite upnp:rating), are read against one table, the same as Android's:
 *   allowed   UK (BBFC): U, Uc, PG          US film (MPAA): G, PG
 *             US TV: TV-Y, TV-Y7 (and TV-Y7-FV), TV-G, TV-PG
 *   refused   everything else that is a rating: 12, 12A, 15, 18, R18, PG-13, R, NC-17, TV-14, TV-MA, NR, Unrated,
 *             any number and anything not in the table.
 * A country or authority prefix ("GB-", "UK:", "US-", "BBFC ", "MPAA ", "Rated ") is ignored. An item with no rating at
 * all is allowed, and only ever inside a Kids folder. Any refused rating on an item (or, at play time, on a folder above
 * it) refuses it.
 *
 * Honest limit: DLNA has no accounts, so this restricts Unified TV only; any other DLNA app on the home network can
 * still see every shared folder.
 *
 * Apps: in Kids, only apps an adult approved open from Unified TV (app.js has one launch check). The Frame can't stop
 * apps opened through Samsung's own Home: a web app can neither see nor close other apps, so there is no Kids app guard.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Kids = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const ALLOWED = 'allowed', REFUSED = 'refused', NONE = 'none';
  const TABLE = ['U', 'UC', 'PG', 'G', 'TV-Y', 'TV-Y7', 'TV-Y7-FV', 'TV-G', 'TV-PG'];
  const MAX_DEPTH = 32, MAX_ROOTS = 6;
  const ASK_ADULT = 'Ask an adult to choose Kids folders.';
  const NOT_FOR_KIDS = 'This isn\'t available in Kids.';

  /** One rating as a source wrote it, against the table above. */
  function rating(raw) {
    if (raw === null || raw === undefined) return NONE;
    let r = String(raw).trim().toUpperCase().replace(/[‐-―]/g, '-').replace(/\s+/g, ' ');
    if (!r) return NONE;
    for (let before = ''; before !== r;) { before = r; r = r.replace(/^(?:(?:GB|UK|US|USA|BBFC|MPAA)\s*[-:\/ ]\s*|RATED\s+)/, '').trim(); }
    if (r.indexOf('TV') === 0) r = r.replace(/^TV[\s-]*/, 'TV-').replace(/^TV-Y7[\s-]*FV$/, 'TV-Y7-FV');
    return TABLE.indexOf(r) !== -1 ? ALLOWED : REFUSED;
  }
  /** Several ratings on one item: any refused one refuses it. */
  function ratings(all) {
    let out = NONE;
    if (!Array.isArray(all)) return out;
    for (let i = 0; i < all.length; i++) {
      const one = rating(all[i]);
      if (one === REFUSED) return REFUSED;
      if (one === ALLOWED) out = ALLOWED;
    }
    return out;
  }

  /** The Kids folders in a saved record { controlUrl, roots: [{id, title}] }, at most six, no duplicates. */
  function folders(record) {
    const out = [], seen = {};
    const list = record && Array.isArray(record.roots) ? record.roots : [];
    list.forEach(function (r) {
      if (r && typeof r.id === 'string' && r.id && !seen[r.id] && out.length < MAX_ROOTS) { seen[r.id] = true; out.push({ id: r.id, title: String(r.title || 'Folder') }); }
    });
    return out;
  }
  function roots(record) { return folders(record).map(function (r) { return r.id; }); }
  /** Ready only when Kids folders exist for the media server in use: no folders means "Ask an adult", never the household library. */
  function ready(record, nas) {
    return !!(record && nas && typeof nas.controlUrl === 'string' && nas.controlUrl && record.controlUrl === nas.controlUrl && roots(record).length);
  }
  /** Browsing: inside a Kids folder, and no refused rating. item is a catalogue item (catalogue.js). */
  function allows(item, kidsRoots) {
    if (!item || !Array.isArray(kidsRoots) || kidsRoots.indexOf(item.root) === -1) return false;
    return ratings(item.ratings) !== REFUSED;
  }
  /**
   * Play time: walks the server's own parent chain from the title up to a Kids folder. Refused when the chain ends,
   * loops, fails or runs too deep before reaching one, or when the title or any folder on the way carries a refused
   * rating. lookup(id, cb(error, catalogueItem | null)) asks the server; done(true | false), once.
   */
  function playable(itemId, kidsRoots, lookup, done) {
    let finished = false;
    function end(ok) { if (!finished) { finished = true; done(ok); } }
    if (typeof itemId !== 'string' || !itemId || !Array.isArray(kidsRoots) || !kidsRoots.length) { end(false); return; }
    const seen = {};
    (function step(id, depth) {
      if (depth > MAX_DEPTH) { end(false); return; }
      if (depth > 0 && kidsRoots.indexOf(id) !== -1) { end(true); return; }
      if (Object.prototype.hasOwnProperty.call(seen, id)) { end(false); return; }
      seen[id] = true;
      try {
        lookup(id, function (err, here) {
          if (finished) return;
          if (err || !here || ratings(here.ratings) === REFUSED) { end(false); return; }
          const parent = typeof here.folder === 'string' ? here.folder : '';
          if (!parent || parent === '-1') { end(false); return; }
          step(parent, depth + 1);
        });
      } catch (e) { end(false); }
    }(itemId, 0));
  }

  // --- apps
  const APP_ID = /^(?:[a-z][a-z0-9]{0,31}|app:[A-Za-z0-9][A-Za-z0-9._-]{0,63})$/;
  /** The approved list: Home tile ids ("netflix", "tvplus", "app:<Tizen id>"). Junk never survives. */
  function apps(saved) {
    const out = [];
    if (typeof saved !== 'string') return out;
    saved.split(',').forEach(function (raw) { const id = raw.trim(); if (APP_ID.test(id) && out.indexOf(id) === -1) out.push(id); });
    return out;
  }
  function toggle(saved, id) {
    const a = apps(saved);
    const at = a.indexOf(id);
    if (at !== -1) a.splice(at, 1); else a.push(id);
    return apps(a.join(',')).join(',');
  }
  /** Adults: anything. Kids: approved apps only. */
  function appAllowed(kids, approved, id) {
    if (!kids) return true;
    if (typeof id !== 'string' || !id) return false;
    return Array.isArray(approved) && approved.indexOf(id) !== -1;
  }

  return { ALLOWED: ALLOWED, REFUSED: REFUSED, NONE: NONE, TABLE: TABLE, MAX_DEPTH: MAX_DEPTH, MAX_ROOTS: MAX_ROOTS,
    ASK_ADULT: ASK_ADULT, NOT_FOR_KIDS: NOT_FOR_KIDS, rating: rating, ratings: ratings, folders: folders, roots: roots,
    ready: ready, allows: allows, playable: playable, apps: apps, toggle: toggle, appAllowed: appAllowed };
}));
