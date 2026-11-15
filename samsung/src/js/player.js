/*
 * Unified TV for Samsung Frame: the embedded player (spec 9, PRODUCT_SPEC 24.4, NT-02, LIB-03).
 *
 * Samsung's AVPlay (webapis.avplay) plays NAS and DLNA files directly. No VLC, no hand-off to another player.
 * The lifecycle is explicit: closed -> preparing -> playing <-> paused / buffering -> ended | stopped | error -> closed,
 * and the previous item is always released before another opens. Hidden: the position is saved and AVPlay suspended;
 * visible again: it is restored and revalidated (spec 6.2).
 * Only plain http(s) addresses without credentials are accepted: no user names, passwords or tokens in a stream URL
 * (spec 13, NT-07). Private, authenticated playback on Tizen needs a validated media broker first and is not built.
 * webapis is feature-detected, so the state machine runs in Node with a fake (samsung/test/player.test.js).
 * Nothing here has played a file on the owner's Frame yet.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Player = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const SEEK_STEP_MS = 10000;
  const CREDENTIAL_QUERY = /[?&](access_?token|token|api_?key|apikey|auth|password|passwd|session|sid|x-[a-z-]*token)=/i;

  /** null when the address may be played, or the plain reason it may not. */
  function refuse(url) {
    if (typeof url !== 'string' || !/^https?:\/\/[^\s]+$/i.test(url)) return 'This file\'s address isn\'t one the TV can play.';
    const authority = url.replace(/^https?:\/\//i, '').split(/[/?#]/)[0];
    if (authority.indexOf('@') !== -1) return 'This file\'s address includes a password, so Unified TV won\'t use it.';
    if (CREDENTIAL_QUERY.test(url)) return 'This file\'s address includes a sign-in token, so Unified TV won\'t use it.';
    return null;
  }
  /** AVPlay's error names to words for the viewer. */
  function message(error) {
    const e = String(error || '');
    if (/NOT_SUPPORTED|UNSUPPORTED|NotSupported/i.test(e)) return 'This TV can\'t play this file\'s format.';
    if (/CONNECTION|NETWORK|TIMEOUT/i.test(e)) return 'The media server stopped answering. Check it is switched on, then try again.';
    if (/INVALID_URI|InvalidValues/i.test(e)) return 'This file\'s address isn\'t one the TV can play.';
    if (/SEEK/i.test(e)) return 'Couldn\'t move to that point in the video.';
    return 'This video stopped unexpectedly. Try again.';
  }

  class Player {
    /** env: { webapis, now() }; onChange(state, detail) after every change. */
    constructor(env, onChange) {
      this.av = env && env.webapis && env.webapis.avplay ? env.webapis.avplay : null;
      this.now = env && env.now ? env.now : function () { return Date.now(); };
      this.onChange = onChange || function () {};
      this.state = 'closed'; this.url = null; this.position = 0; this.duration = 0;
      this.openedAt = 0; this.firstFrameMs = null; this.error = null; this.suspended = false; this.generation = 0;
    }
    available() { return !!this.av; }
    set(state, detail) { this.state = state; this.onChange(state, detail || {}); }
    /** Opens an item, releasing whatever was there first. startMs resumes a locally remembered position. */
    open(url, startMs) {
      const why = refuse(url);
      if (why) { this.error = why; this.set('error', { message: why }); return false; }
      if (!this.av) { this.error = 'Video playback needs the TV\'s own player, which isn\'t available here.'; this.set('error', { message: this.error }); return false; }
      this.release();
      const gen = ++this.generation;
      const self = this; const av = this.av;
      this.url = url; this.position = startMs > 0 ? startMs : 0; this.duration = 0; this.error = null;
      this.openedAt = this.now(); this.firstFrameMs = null;
      try {
        av.open(url);
        av.setListener({
          onbufferingstart: function () { if (gen === self.generation && self.state === 'playing') self.set('buffering'); },
          onbufferingcomplete: function () { if (gen === self.generation && self.state === 'buffering') self.set('playing'); },
          oncurrentplaytime: function (ms) {
            if (gen !== self.generation) return;
            self.position = ms;
            if (self.firstFrameMs === null && ms > 0) { self.firstFrameMs = self.now() - self.openedAt; self.onChange(self.state, { firstFrameMs: self.firstFrameMs }); }
          },
          onstreamcompleted: function () { if (gen === self.generation) { self.release(); self.set('ended'); } },
          onerror: function (type) { if (gen === self.generation) self.fail(type); },
          onevent: function () {}
        });
        try { av.setDisplayRect(0, 0, 1920, 1080); } catch (e) { /* default rectangle */ }
      } catch (e) { this.fail(e && e.name); return false; }
      this.set('preparing');
      try {
        av.prepareAsync(function () {
          if (gen !== self.generation) return;
          try { self.duration = av.getDuration ? av.getDuration() : 0; } catch (e) { self.duration = 0; }
          if (self.position > 0) { try { av.seekTo(self.position, function () {}, function () {}); } catch (e) { /* start from the beginning */ } }
          try { av.play(); self.set('playing'); } catch (e) { self.fail(e && e.name); }
        }, function (e) { if (gen === self.generation) self.fail(e && e.name ? e.name : e); });
      } catch (e) { this.fail(e && e.name); return false; }
      return true;
    }
    fail(type) { this.release(); this.error = message(type); this.set('error', { message: this.error }); }
    toggle() { if (this.state === 'playing' || this.state === 'buffering') this.pause(); else if (this.state === 'paused') this.play(); }
    play() { if (this.state !== 'paused') return; try { this.av.play(); this.set('playing'); } catch (e) { this.fail(e && e.name); } }
    pause() { if (this.state !== 'playing' && this.state !== 'buffering') return; try { this.av.pause(); this.set('paused'); } catch (e) { this.fail(e && e.name); } }
    /** Moves by deltaMs, kept inside the item. */
    seek(deltaMs) {
      if (['playing', 'paused', 'buffering'].indexOf(this.state) === -1) return;
      const end = this.duration > 0 ? this.duration - 1000 : Infinity;
      const target = Math.max(0, Math.min(end, this.position + deltaMs));
      const self = this;
      try { this.av.seekTo(target, function () { self.position = target; self.onChange(self.state, { position: target }); }, function () { self.onChange(self.state, { message: message('SEEK') }); }); }
      catch (e) { this.onChange(this.state, { message: message('SEEK') }); }
    }
    /** Stop and Back: playback ends, AVPlay is released, the position is kept for a local resume. */
    stop() { if (this.state === 'closed') return; this.release(); this.set('stopped', { position: this.position }); }
    release() {
      this.generation++;
      if (!this.av) return;
      try { this.av.stop(); } catch (e) { /* not playing */ }
      try { this.av.close(); } catch (e) { /* not open */ }
      this.suspended = false;
    }
    /** The app is being hidden: keep the place, let go of the hardware. */
    hide() {
      if (['playing', 'paused', 'buffering', 'preparing'].indexOf(this.state) === -1) return;
      if (this.av && typeof this.av.suspend === 'function') {
        try { this.av.suspend(); this.suspended = true; this.set('suspended', { position: this.position }); return; } catch (e) { /* fall through */ }
      }
      const url = this.url, position = this.position;
      this.release(); this.url = url; this.position = position; this.set('suspended', { position: position });
    }
    /** Visible again: restore, or reopen at the saved position; the viewer presses Play to carry on. */
    show() {
      if (this.state !== 'suspended') return;
      if (this.suspended && this.av && typeof this.av.restore === 'function') {
        try { this.av.restore(this.url, this.position, true); this.suspended = false; this.set('paused', { position: this.position }); return; } catch (e) { /* reopen */ }
      }
      this.open(this.url, this.position);
    }
  }

  return { SEEK_STEP_MS: SEEK_STEP_MS, refuse: refuse, message: message, Player: Player };
}));
