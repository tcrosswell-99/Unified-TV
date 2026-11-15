/*
 * Unified TV for Samsung Frame: saved settings (spec 6.3 settings persistence, 13, PRODUCT_SPEC 24.4).
 *
 * Ordinary preferences (motion, Wallpaper, dock order, the Home Assistant address) are kept in this TV's web storage
 * with a schema version. Loading never throws: unreadable data gives the defaults; data written by a newer version is
 * read as defaults but never overwritten, so going back a version doesn't destroy it; every value is checked on the way
 * in. Secrets never live here: anything that looks like a token, password or key is stripped. The Home Assistant token
 * and the chosen media folders go to the TV's Key Manager instead (secrets.js), or are not kept at all.
 * Every key is per-TV ("device" scope), including "profiles": which profile is in use (Adults or Kids), the apps an adult
 * approved for Kids, "Show person alerts in Kids" and the PIN lock-out count. The PIN itself and the Kids folders are
 * secrets (secrets.js). Places to resume are kept per profile by resume.js, not here.
 */
(function (root, factory) {
  'use strict';
  var api = factory(typeof require === 'function' ? require('./apps.js') : root.UTV.Apps,
    typeof require === 'function' ? require('./wallpaper.js') : root.UTV.Wallpaper);
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Settings = api; }
}(typeof self !== 'undefined' ? self : this, function (Apps, Wallpaper) {
  'use strict';

  const KEY = 'unifiedtv.frame.settings';
  const VERSION = 1;
  const SECRET_NAME = /token|password|passwd|secret|api[_-]?key|credential|cookie|auth/i;

  function defaults() {
    return {
      version: VERSION,
      dock: null,                       // null: never edited, so the Frame defaults; "" is an empty Home the owner chose
      appChoices: {},                   // tile id -> Tizen app id chosen by the owner
      motion: 'default',                // default | still | reduce | light
      highContrast: false,
      reducedTransparency: false,
      wallpaper: { source: Wallpaper.ARTWORK, clock: Wallpaper.AUTO, idle: Wallpaper.DEFAULT_IDLE, slide: Wallpaper.DEFAULT_SLIDE, hour12: false, date: false, dim: 0 },
      alerts: { alarms: true, reminders: false, door: '', person: '', doorName: '' },
      profiles: { current: 'adult', kidsApps: '', kidsPersonAlerts: true, pinFailures: 0, pinWait: 0 },
      ha: { enabled: false, host: '', port: 8123, https: true, httpConsentHost: '' },
      ui: { dockIndex: 0 }
    };
  }
  /**
   * Which keys are per-TV and which are per-profile. Everything here is per-TV: the household's Home, look and
   * connections are shared, and the profile layer itself (profiles) belongs to the TV. Per-profile data (local resume
   * places) lives in resume.js.
   */
  const SCOPE = { dock: 'device', appChoices: 'device', motion: 'device', highContrast: 'device', reducedTransparency: 'device',
    wallpaper: 'device', alerts: 'device', profiles: 'device', ha: 'device', ui: 'device' };
  const KIDS_APP = /^(?:[a-z][a-z0-9]{0,31}|app:[A-Za-z0-9][A-Za-z0-9._-]{0,63})$/;

  /** Future schema changes go here: MIGRATIONS[n] turns version n into version n + 1. */
  const MIGRATIONS = {};

  function oneOf(v, allowed, fallback) { return allowed.indexOf(v) !== -1 ? v : fallback; }
  function bool(v, fallback) { return typeof v === 'boolean' ? v : fallback; }
  function text(v, max, fallback) { return typeof v === 'string' && v.length <= max ? v : fallback; }
  function entity(v) { return typeof v === 'string' && /^(binary_sensor|event)\.[a-z0-9_]{1,80}$/.test(v) ? v : ''; }

  /** Drops anything secret-looking, at any depth, and says whether it did. */
  function stripSecrets(value) {
    let removed = false;
    (function walk(o) {
      if (!o || typeof o !== 'object') return;
      Object.keys(o).forEach(function (k) {
        if (SECRET_NAME.test(k)) { delete o[k]; removed = true; } else walk(o[k]);
      });
    }(value));
    return removed;
  }

  /** Every field checked against its allowed values; anything else is the default. */
  function sanitise(raw) {
    const d = defaults();
    const o = raw && typeof raw === 'object' ? raw : {};
    const out = defaults();
    if (o.dock === null || typeof o.dock === 'string') out.dock = o.dock === null ? null : Apps.dockOrder(o.dock).join(',');
    if (o.appChoices && typeof o.appChoices === 'object') {
      Object.keys(o.appChoices).forEach(function (k) {
        const v = o.appChoices[k];
        if (Apps.CATALOGUE[k] && typeof v === 'string' && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(v)) out.appChoices[k] = v;
      });
    }
    out.motion = oneOf(o.motion, ['default', 'still', 'reduce', 'light'], d.motion);
    out.highContrast = bool(o.highContrast, d.highContrast);
    out.reducedTransparency = bool(o.reducedTransparency, d.reducedTransparency);
    const w = o.wallpaper && typeof o.wallpaper === 'object' ? o.wallpaper : {};
    out.wallpaper = {
      source: Wallpaper.source(w.source), clock: Wallpaper.clock(w.clock), idle: Wallpaper.idle(w.idle), slide: Wallpaper.slide(w.slide),
      hour12: bool(w.hour12, false), date: bool(w.date, false), dim: oneOf(w.dim, [0, 20, 40], 0)
    };
    const a = o.alerts && typeof o.alerts === 'object' ? o.alerts : {};
    out.alerts = { alarms: bool(a.alarms, true), reminders: bool(a.reminders, false), door: entity(a.door), person: entity(a.person),
      doorName: text(a.doorName, 40, '').replace(/[\u0000-\u001f]/g, '').trim() };
    const p = o.profiles && typeof o.profiles === 'object' ? o.profiles : {};
    const approved = [];
    if (typeof p.kidsApps === 'string') {
      p.kidsApps.split(',').forEach(function (raw) {
        const id = raw.trim();
        if (KIDS_APP.test(id) && Apps.validOnFrame(id) && approved.indexOf(id) === -1) approved.push(id);
      });
    }
    const failures = Number(p.pinFailures), wait = Number(p.pinWait);
    out.profiles = {
      current: p.current === 'child' ? 'child' : 'adult', kidsApps: approved.join(','), kidsPersonAlerts: bool(p.kidsPersonAlerts, true),
      pinFailures: failures >= 0 && failures <= 1000 ? Math.floor(failures) : 0, pinWait: wait > 0 && wait < 1e15 ? Math.floor(wait) : 0
    };
    const h = o.ha && typeof o.ha === 'object' ? o.ha : {};
    const port = Number(h.port);
    out.ha = {
      enabled: bool(h.enabled, false), host: text(h.host, 253, '').trim(),
      port: port >= 1 && port <= 65535 && Math.floor(port) === port ? port : 8123,
      https: bool(h.https, true), httpConsentHost: text(h.httpConsentHost, 253, '')
    };
    const u = o.ui && typeof o.ui === 'object' ? o.ui : {};
    out.ui = { dockIndex: Math.max(0, Math.min(50, Number(u.dockIndex) | 0)) };
    return out;
  }

  /**
   * Reads the stored text. Returns { settings, readOnly, notes }:
   * readOnly is true when the data came from a newer version (it is left as it is); notes say what happened.
   * migrations and version can be passed by tests to exercise the upgrade path.
   */
  function migrate(stored, migrations, version) {
    const steps = migrations || MIGRATIONS;
    const target = version || VERSION;
    const notes = [];
    if (stored === null || stored === undefined || stored === '') return { settings: defaults(), readOnly: false, notes: ['fresh'] };
    let value;
    try { value = JSON.parse(stored); } catch (e) { return { settings: defaults(), readOnly: false, notes: ['unreadable'] }; }
    if (!value || typeof value !== 'object' || Array.isArray(value)) return { settings: defaults(), readOnly: false, notes: ['unreadable'] };
    let v = Number(value.version);
    if (!(v >= 1) || Math.floor(v) !== v) return { settings: defaults(), readOnly: false, notes: ['unversioned'] };
    if (v > target) return { settings: defaults(), readOnly: true, notes: ['newer'] };
    if (stripSecrets(value)) notes.push('secret-removed');
    while (v < target) {
      const step = steps[v];
      if (typeof step !== 'function') return { settings: defaults(), readOnly: false, notes: notes.concat(['no-migration-' + v]) };
      try { value = step(value) || value; } catch (e) { return { settings: defaults(), readOnly: false, notes: notes.concat(['migration-failed-' + v]) }; }
      v += 1; value.version = v; notes.push('migrated-' + v);
    }
    const settings = sanitise(value);
    settings.version = target;
    return { settings: settings, readOnly: false, notes: notes };
  }

  /** The settings store over window.localStorage (or a test double). Storage failures are reported, never thrown. */
  class Store {
    constructor(storage) { this.storage = storage || null; this.readOnly = false; this.notes = []; }
    load() {
      let raw = null;
      try { raw = this.storage ? this.storage.getItem(KEY) : null; } catch (e) { raw = null; }
      const r = migrate(raw);
      this.readOnly = r.readOnly; this.notes = r.notes;
      return r.settings;
    }
    save(settings) {
      if (this.readOnly || !this.storage) return false;
      const copy = JSON.parse(JSON.stringify(settings));
      stripSecrets(copy);
      copy.version = VERSION;
      try { this.storage.setItem(KEY, JSON.stringify(copy)); return true; } catch (e) { return false; }
    }
  }

  return { KEY: KEY, VERSION: VERSION, SCOPE: SCOPE, defaults: defaults, sanitise: sanitise, migrate: migrate, stripSecrets: stripSecrets, Store: Store };
}));
