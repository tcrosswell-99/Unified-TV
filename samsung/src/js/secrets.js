/*
 * Unified TV for Samsung Frame: where secrets go (spec 6.2, 13, PRODUCT_SPEC 24.4).
 *
 * The Home Assistant access token and the chosen media folders are kept with the TV's Tizen Key Manager
 * (tizen.keymanager), and nowhere else. If Key Manager is missing or refuses, this fails closed: nothing is written to
 * localStorage or any other plain store, and the connection simply isn't remembered.
 * Honest limits: Key Manager is the platform's protected store; whether it is hardware-backed on the 2021 Frame, and
 * whether it works at all for a developer-installed app on firmware T-NKM2DEUC-2310.0, is unknown until tested on the TV.
 * In a desktop browser (no tizen object at all) values are kept in memory only, for this session, and the page says so.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Secrets = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const PREFIX = 'unifiedtv-';
  const NAMES = ['ha-token', 'nas'];

  /**
   * mode: 'keymanager' (the TV's Key Manager), 'simulation' (a desktop browser: memory only, labelled), or
   * 'unavailable' (a TV without a usable Key Manager: kept for this session only, never written anywhere).
   */
  class Vault {
    constructor(env) {
      const km = env && env.tizen && env.tizen.keymanager;
      this.km = km && typeof km.saveData === 'function' && typeof km.getData === 'function' ? km : null;
      this.mode = this.km ? 'keymanager' : (env && env.tizen ? 'unavailable' : 'simulation');
      this.memory = {};
    }
    label() {
      if (this.mode === 'keymanager') return 'Kept in this TV\'s Key Manager, not in ordinary app storage.';
      if (this.mode === 'simulation') return 'Browser simulation: kept in memory only and forgotten when the page closes.';
      return 'This TV\'s secure storage isn\'t available, so this is kept only until Unified TV closes.';
    }
    remembers() { return this.mode === 'keymanager'; }
    alias(name) { if (NAMES.indexOf(name) === -1) throw new Error('unknown secret'); return PREFIX + name; }
    /** Reads a value; null when absent or when Key Manager refuses (fail closed). */
    get(name) {
      const alias = this.alias(name);
      if (Object.prototype.hasOwnProperty.call(this.memory, alias)) return this.memory[alias];
      if (!this.km) return null;
      const km = this.km;
      const attempts = [function () { return km.getData({ name: alias }, null); }, function () { return km.getData(alias, null); }];
      for (let i = 0; i < attempts.length; i++) {
        try {
          const v = attempts[i]();
          if (typeof v === 'string') { this.memory[alias] = v; return v; }
        } catch (e) { /* try the other form, then give up */ }
      }
      return null;
    }
    /** Stores a value. done(true) when remembered by Key Manager; done(false) otherwise (kept for this session only). */
    put(name, value, done) {
      const alias = this.alias(name);
      const finish = typeof done === 'function' ? done : function () {};
      if (typeof value !== 'string' || !value) { this.forget(name); finish(true); return; }
      this.memory[alias] = value;
      if (!this.km) { finish(false); return; }
      this.removeStored(alias);
      try {
        this.km.saveData(alias, value, null, function () { finish(true); }, function () { finish(false); });
      } catch (e) { finish(false); }
    }
    removeStored(alias) {
      if (!this.km || typeof this.km.removeAlias !== 'function') return;
      try { this.km.removeAlias({ name: alias }); } catch (e) {
        try { this.km.removeAlias(alias); } catch (e2) { /* nothing stored */ }
      }
    }
    /** Forget: removes the value from memory and from Key Manager. */
    forget(name) { const alias = this.alias(name); delete this.memory[alias]; this.removeStored(alias); }
    forgetAll() { const self = this; NAMES.forEach(function (n) { self.forget(n); }); }
  }

  return { Vault: Vault, NAMES: NAMES };
}));
