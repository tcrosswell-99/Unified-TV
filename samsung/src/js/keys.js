/*
 * Unified TV for Samsung Frame: remote keys (spec 6.2, 6.4, PRODUCT_SPEC 24.1, 28.2).
 *
 * The arrows, OK (Enter) and Back (Return) reach every Tizen web app without registration. Only the media keys the
 * player uses are registered, and only where tizen.tvinputdevice says the TV supports them. Home, Smart Hub, volume,
 * mute, power, Source, the voice key, channel keys, the 123 on-screen keypad, Exit and Art Mode stay with the TV: this
 * app never registers or intercepts them, and never tries to catch a long Return (the TV's own exit).
 * Key codes are Samsung's documented defaults; which keys the owner's Smart Remote really delivers is unverified (RC-09).
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Keys = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  /** Delivered without registration. 27 (Escape) and 8 (Backspace) stand in for Back in a desktop browser only. */
  const BASIC = { 37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok', 10009: 'back', 27: 'back', 8: 'back' };
  /** Registered on the TV, used only inside Unified TV's own player. */
  const MEDIA = {
    MediaPlayPause: { code: 10252, action: 'playpause' },
    MediaPlay: { code: 415, action: 'play' },
    MediaPause: { code: 19, action: 'pause' },
    MediaStop: { code: 413, action: 'stop' },
    MediaFastForward: { code: 417, action: 'forward' },
    MediaRewind: { code: 412, action: 'rewind' }
  };
  /** Never registered: these belong to the TV or would be a route around the Live TV guard (spec 6.4). */
  const NEVER = ['Exit', 'Source', 'ChannelUp', 'ChannelDown', 'ChannelList', 'PreviousChannel', 'Guide', 'Menu', 'Tools',
    'Info', 'Caption', 'Search', 'Extra', 'E-Manual', 'PictureSize', 'Soccer', 'Teletext', 'MTS',
    'VolumeUp', 'VolumeDown', 'VolumeMute', 'Power', 'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'Minus'];
  const LONG_MS = 650;

  const BY_CODE = {};
  Object.keys(BASIC).forEach(function (code) { BY_CODE[code] = BASIC[code]; });
  Object.keys(MEDIA).forEach(function (name) { BY_CODE[MEDIA[name].code] = MEDIA[name].action; });

  /** The action for a key code, or null for anything this app does not handle (the TV keeps it). */
  function action(code, inBrowserField) {
    if (inBrowserField && (code === 8 || code === 27)) return null;
    return Object.prototype.hasOwnProperty.call(BY_CODE, code) ? BY_CODE[code] : null;
  }

  /**
   * Registers the media keys through tizen.tvinputdevice, if it exists. Feature-detected so the page and the tests run
   * in a desktop browser or Node; a key the TV doesn't list as supported is skipped, a refusal is recorded, nothing throws.
   */
  function register(env) {
    const result = { available: false, registered: [], skipped: [], failed: [] };
    const device = env && env.tizen && env.tizen.tvinputdevice;
    if (!device || typeof device.registerKey !== 'function') return result;
    result.available = true;
    let supported = null;
    try {
      if (typeof device.getSupportedKeys === 'function') {
        supported = {};
        device.getSupportedKeys().forEach(function (k) { supported[k.name] = true; });
      }
    } catch (e) { supported = null; }
    Object.keys(MEDIA).forEach(function (name) {
      if (NEVER.indexOf(name) !== -1) return;
      if (supported && !supported[name]) { result.skipped.push(name); return; }
      try { device.registerKey(name); result.registered.push(name); } catch (e) { result.failed.push(name); }
    });
    return result;
  }

  /**
   * OK held down: a long press opens the context menu instead of activating, and never both (spec 28.2, RC-11).
   * down() on every keydown (including the TV's auto-repeat), tick() from a timer, up() on keyup.
   * Each returns 'press', 'long' or null.
   */
  /**
   * Which firmware sends keyup for OK is unverified, so a press is never stranded: once a keyup has been seen, holding
   * past LONG_MS is a long press; before that, the TV's own auto-repeat proves the key is held, and with neither a
   * keyup nor a repeat after NO_RELEASE_MS the press is taken as an ordinary press.
   */
  class PressTracker {
    constructor(longMs, noReleaseMs) {
      this.longMs = longMs || LONG_MS; this.noReleaseMs = noReleaseMs || 1500;
      this.since = 0; this.fired = false; this.down_ = false; this.repeats = 0; this.releaseSeen = false;
    }
    down(now) {
      if (!this.down_) { this.down_ = true; this.since = now; this.fired = false; this.repeats = 0; return null; }
      this.repeats++;
      return this.tick(now);
    }
    tick(now) {
      if (!this.down_ || this.fired) return null;
      const held = now - this.since;
      if (held >= this.longMs && (this.releaseSeen || this.repeats > 0)) { this.fired = true; return 'long'; }
      if (!this.releaseSeen && this.repeats === 0 && held >= this.noReleaseMs) { this.down_ = false; return 'press'; }
      return null;
    }
    up(now) {
      this.releaseSeen = true;
      if (!this.down_) return null;
      const long = now - this.since >= this.longMs;
      const already = this.fired;
      this.down_ = false; this.fired = false;
      if (already) return null;
      return long ? 'long' : 'press';
    }
    cancel() { this.down_ = false; this.fired = false; }
    held() { return this.down_; }
  }

  return { BASIC: BASIC, MEDIA: MEDIA, NEVER: NEVER, LONG_MS: LONG_MS, action: action, register: register, PressTracker: PressTracker };
}));
