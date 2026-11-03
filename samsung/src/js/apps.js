/*
 * Unified TV for Samsung Frame: the app table and launching (spec 7.2, 11, PRODUCT_SPEC 24.2).
 *
 * One table holds every Home app. The Frame's defaults are Netflix, Disney+, BBC, Live TV, Music and Samsung TV Plus.
 * SmartTube is an Android app: on the Frame it is listed as unavailable and can't be added, never shown as a fake tile.
 * App ids are resolved at run time from the apps the TV reports (tizen.application.getAppsInfo), matched by name; the
 * owner can pick a different installed app for any tile in Settings. A few widely reported Samsung app ids are kept as
 * candidates, but none of them has been checked on the owner's Frame. Samsung TV Plus is found by its name only: its
 * native id has not been confirmed for this TV and none is guessed here.
 * Live TV never launches anything from this table: it goes through the Live TV guard (livetv.js), which stays locked.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Apps = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const CATALOGUE = {
    netflix: { name: 'Netflix', symbol: 'N', tile: ['#28242B', '#07090C'], match: ['netflix'],
      candidates: ['3201907018807', '11101200001'] },
    disney: { name: 'Disney+', symbol: 'D+', tile: ['#15356F', '#071128'], match: ['disney+', 'disney plus'],
      candidates: ['3201901017640'] },
    bbc: { name: 'BBC', symbol: 'BBC', tile: ['#494A51', '#25262A'], match: ['bbc iplayer', 'iplayer'], candidates: [] },
    live: { name: 'Live TV', symbol: 'TV', tile: ['#6D45A3', '#27194F'], guarded: true },
    music: { name: 'Music', symbol: '♫', tile: ['#E85C75', '#8E2548'], match: ['amazon music'], candidates: [] },
    tvplus: { name: 'Samsung TV Plus', symbol: 'TV+', tile: ['#154477', '#07192F'], match: ['samsung tv plus', 'tv plus'],
      candidates: [] },
    smartutube: { name: 'SmartTube', symbol: '▶', tile: ['#3A1216', '#15080A'],
      unavailable: 'SmartTube is an Android app. There is no Samsung version, so it isn\'t available on this TV.' },
    xbox: { name: 'Xbox', symbol: 'X', tile: ['#14783F', '#07351E'],
      unavailable: 'The Xbox shortcut is only for the Sony TV.' }
  };
  const FRAME_DEFAULTS = ['netflix', 'disney', 'bbc', 'live', 'music', 'tvplus'];
  /** An installed app added by the owner is stored as "app:<Tizen app id>". */
  const INSTALLED = /^app:[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;

  function entry(id) {
    if (Object.prototype.hasOwnProperty.call(CATALOGUE, id)) return CATALOGUE[id];
    if (INSTALLED.test(id)) return { name: id.slice(4), symbol: id.slice(4, 6), tile: ['#2A3140', '#11151D'], appId: id.slice(4), installed: true };
    return null;
  }
  /** available | unavailable | guarded | unknown. */
  function availability(id) {
    const e = entry(id);
    if (!e) return 'unknown';
    if (e.unavailable) return 'unavailable';
    if (e.guarded) return 'guarded';
    return 'available';
  }
  /** A dock entry may be any catalogue app that is available or guarded here, or an installed app. */
  function validOnFrame(id) { const a = availability(id); return a === 'available' || a === 'guarded'; }

  /** The TV's own list, as plain {id, name}. Anything without an id is ignored. */
  function installedFrom(list) {
    const out = [];
    (list || []).forEach(function (a) { if (a && typeof a.id === 'string' && a.id) out.push({ id: a.id, name: String(a.name || '') }); });
    return out;
  }
  function norm(s) { return String(s || '').toLowerCase().replace(/\s+/g, ' ').trim(); }

  /**
   * Which Tizen app a tile opens: the owner's own choice first, then an installed app whose name matches, then a
   * candidate id the TV reports as installed. installed is null when the TV could not be asked: then the candidates are
   * tried in turn, and the result says it is unconfirmed.
   */
  function resolve(id, installed, overrides) {
    const e = entry(id);
    if (!e || e.unavailable || e.guarded) return { appId: null, source: null };
    if (e.installed) return { appId: e.appId, source: 'owner' };
    const chosen = overrides && overrides[id];
    if (chosen && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(chosen)) return { appId: chosen, source: 'owner' };
    if (installed) {
      const byName = installed.filter(function (a) {
        const n = norm(a.name);
        return (e.match || []).some(function (m) { return n === m; });
      });
      const loose = byName.length ? byName : installed.filter(function (a) {
        const n = norm(a.name);
        return (e.match || []).some(function (m) { return n.indexOf(m) !== -1; });
      });
      if (loose.length) return { appId: loose[0].id, source: 'installed' };
      const ids = installed.map(function (a) { return a.id; });
      const present = (e.candidates || []).filter(function (c) { return ids.indexOf(c) !== -1; });
      if (present.length) return { appId: present[0], source: 'installed' };
      return { appId: null, source: null };
    }
    return { appId: (e.candidates || [])[0] || null, source: (e.candidates || []).length ? 'candidate' : null, tryAlso: (e.candidates || []).slice(1) };
  }

  /** Plain messages for the viewer; no ids, no error codes on screen. */
  function failure(id, reason) {
    const e = entry(id); const name = e ? e.name : 'That app';
    if (reason === 'unavailable') return e && e.unavailable ? e.unavailable : name + ' isn\'t available on this TV.';
    if (reason === 'not-found') return name + ' wasn\'t found on this TV. If it is installed, choose it in Settings, TV & performance, Advanced.';
    if (reason === 'no-launch') return 'This TV didn\'t let Unified TV open ' + name + '.';
    return name + ' didn\'t open. Try again, or open it from Samsung Smart Hub.';
  }

  /**
   * Opens a Tizen app. env.tizen.application.launch is feature-detected; ids are tried in order and the first success
   * wins. done(true) or done(false, reason). The caller saves Home's focus before calling (PRODUCT_SPEC 28.3).
   */
  function launch(env, ids, done) {
    const app = env && env.tizen && env.tizen.application;
    if (!app || typeof app.launch !== 'function') { done(false, 'no-launch'); return; }
    const queue = ids.filter(function (x) { return !!x; });
    if (!queue.length) { done(false, 'not-found'); return; }
    (function next() {
      const id = queue.shift();
      try {
        app.launch(id, function () { done(true); }, function () { if (queue.length) next(); else done(false, 'failed'); });
      } catch (e) { if (queue.length) next(); else done(false, 'failed'); }
    }());
  }

  /** Asks the TV which apps are installed (needs the application.info privilege). done(list) or done(null). */
  function listInstalled(env, done) {
    const app = env && env.tizen && env.tizen.application;
    if (!app || typeof app.getAppsInfo !== 'function') { done(null); return; }
    try { app.getAppsInfo(function (list) { done(installedFrom(list)); }, function () { done(null); }); } catch (e) { done(null); }
  }

  /** Spec 3.3: a fresh install gets the Frame defaults; after that the owner's edits stand, including an empty Home. */
  function dockOrder(saved) {
    if (saved === null || saved === undefined) return FRAME_DEFAULTS.slice();
    const out = [];
    String(saved).split(',').forEach(function (raw) {
      const id = raw.trim();
      if (id && validOnFrame(id) && out.indexOf(id) === -1) out.push(id);
    });
    return out;
  }
  function dockAdd(order, id) { const next = order.slice(); if (validOnFrame(id) && next.indexOf(id) === -1) next.push(id); return next.join(','); }
  /** Removing a tile only takes it off Home; it never uninstalls anything. */
  function dockRemove(order, id) { return order.filter(function (x) { return x !== id; }).join(','); }

  return {
    CATALOGUE: CATALOGUE, FRAME_DEFAULTS: FRAME_DEFAULTS, entry: entry, availability: availability, validOnFrame: validOnFrame,
    installedFrom: installedFrom, resolve: resolve, failure: failure, launch: launch, listInstalled: listInstalled,
    dockOrder: dockOrder, dockAdd: dockAdd, dockRemove: dockRemove
  };
}));
