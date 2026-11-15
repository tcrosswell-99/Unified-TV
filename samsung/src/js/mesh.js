/*
 * Unified TV for Samsung Frame: the ambient mesh (spec 7.3-7.6, PRODUCT_SPEC 32.4).
 *
 * Pure rules, shared by the page and the Node tests:
 * - each light is a CSS radial-gradient() on an absolutely positioned, clipped element, built from the same palette
 *   numbers as Android (palettes.json, checked against AmbientMesh.java by samsung/test/palettes.test.js);
 * - drift moves those few elements with transform: translate3d() scale() in CSS keyframes, 20-40 s paths, 2-5 % of the
 *   screen, scale 1.00-1.06; gradients are never rewritten per frame and nothing animates filter or backdrop-filter;
 * - two mesh containers crossfade by opacity over 400 ms, starting at once; the latest focus wins and retargets from
 *   whichever scene is most visible (Crossfade below).
 * ES2015 only: the 2021 Frame's browser engine is old.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Mesh = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  /** The soft white light Android pre-renders once (AmbientMesh.sprite): stop position and alpha. */
  const SPRITE_STOPS = [[0, 1], [0.38, 0x94 / 255], [0.72, 0x2E / 255], [1, 0]];
  const FADE_MS = 400;
  const LIGHT_FADE_MS = 200;

  /** "0xAARRGGBB" to {a (0-1), r, g, b}. */
  function colour(hex) {
    const v = parseInt(String(hex).replace(/^0x/i, ''), 16) >>> 0;
    return { a: ((v >>> 24) & 255) / 255, r: (v >>> 16) & 255, g: (v >>> 8) & 255, b: v & 255 };
  }
  function round(n, places) { const f = Math.pow(10, places); return Math.round(n * f) / f; }
  function rgba(c, alpha) { return 'rgba(' + c.r + ',' + c.g + ',' + c.b + ',' + round(alpha, 4) + ')'; }
  function rgb(hex) { const c = colour(hex); return 'rgb(' + c.r + ',' + c.g + ',' + c.b + ')'; }

  /** The palette for an app id; anything unknown gets the quiet neutral fallback (spec 32.2). */
  function palette(data, id) {
    if (data && data.palettes && Object.prototype.hasOwnProperty.call(data.palettes, id)) return data.palettes[id];
    return data ? data.neutral : null;
  }
  function known(data, id) { return !!(data && data.palettes && Object.prototype.hasOwnProperty.call(data.palettes, id)); }

  /** One light's background: the sprite's falloff, tinted by the light's own colour and alpha, as Android's SRC_IN tint. */
  function lightGradient(light) {
    const c = colour(light.colour);
    const stops = SPRITE_STOPS.map(function (s) { return rgba(c, c.a * s[1]) + ' ' + Math.round(s[0] * 100) + '%'; });
    return 'radial-gradient(circle closest-side, ' + stops.join(', ') + ')';
  }
  /** Where a light's element sits at rest, in screen pixels: a square of side 2r (r is a fraction of the width). */
  function lightBox(light, width, height) {
    const size = Math.round(light.r * width * 2);
    return { left: Math.round(light.x * width - size / 2), top: Math.round(light.y * height - size / 2), size: size };
  }

  /** Android's own drift formula (AmbientMesh.position), kept for parity checks. */
  function position(light, seconds) {
    const a = 2 * Math.PI * seconds / light.period + light.phase;
    return [light.x + light.ax * Math.sin(a), light.y + light.ay * Math.sin(a * 0.77 + 1.3), 1 + 0.03 * (1 + Math.sin(a * 0.61 + 0.4))];
  }

  /**
   * A closed drift path for CSS keyframes: one smooth loop per period, offsets as fractions of the screen and a scale
   * from 1.00 to 1.06. A CSS loop has to close, so the path uses one frequency per axis (Android's is quasi-periodic);
   * the bounds and speed are the same rule.
   */
  function driftKeyframes(light, steps) {
    const n = steps || 12;
    const out = [];
    for (let k = 0; k <= n; k++) {
      const a = 2 * Math.PI * k / n;
      out.push({ pct: round(100 * k / n, 3), dx: light.ax * Math.sin(a), dy: light.ay * Math.sin(a + 1.3), s: 1 + 0.03 * (1 + Math.sin(a + 0.4)) });
    }
    return out;
  }
  function keyframesCss(name, light, width, height) {
    const frames = driftKeyframes(light).map(function (f) {
      return f.pct + '%{transform:translate3d(' + round(f.dx * width, 2) + 'px,' + round(f.dy * height, 2) + 'px,0) scale(' + round(f.s, 4) + ')}';
    });
    return '@keyframes ' + name + '{' + frames.join('') + '}';
  }
  /**
   * The animation delay that puts a light where it would be had it been drifting since the page began: a negative delay
   * taken from one clock, so a new focus (or a new mesh container) never restarts the drift (spec 7.4).
   */
  function driftDelay(light, seconds) {
    const offset = light.phase / (2 * Math.PI) * light.period;
    const t = (seconds + offset) % light.period;
    return -round(t < 0 ? t + light.period : t, 3);
  }

  /**
   * Spec 7.6: the motion settings change cost, never hierarchy. System reduced motion is honoured where detectable.
   * default: drift on, 400 ms; still: drift off, 400 ms; reduce: drift off, instant; light: still frames, 200 ms.
   */
  function motion(setting, systemReduced) {
    const s = setting === 'still' || setting === 'reduce' || setting === 'light' ? setting : 'default';
    if (s === 'reduce' || (s === 'default' && systemReduced)) return { setting: 'reduce', fadeMs: 0, drift: false };
    if (s === 'light') return { setting: s, fadeMs: LIGHT_FADE_MS, drift: false };
    if (s === 'still') return { setting: s, fadeMs: FADE_MS, drift: false };
    return { setting: s, fadeMs: FADE_MS, drift: true };
  }

  /** Ease in and out, as Android's smoothstep: 0 to 1. */
  function ease(t) { const x = Math.max(0, Math.min(1, t)); return x * x * (3 - 2 * x); }

  /**
   * Two mesh containers, "a" and "b". The base sits underneath at full opacity; the top fades in over it. On a new focus:
   * - with no fade running, the top (now fully visible) becomes the base and the other container fades in the new scene;
   * - during a fade, whichever scene is most visible stays: under half-way the base is kept and the top is retargeted,
   *   past half-way the top is promoted to base first. Only one fade ever runs, so nothing queues (spec 32.1, 32.6).
   * commit() returns the operations for the page to apply, in order.
   */
  class Crossfade {
    constructor() {
      this.base = 'a'; this.top = 'b';
      this.keys = { a: null, b: null };
      this.scene = null; this.former = null;
      this.fadeAt = 0; this.fadeMs = 0;
    }
    fading(now) { return this.fadeAt !== 0 && now - this.fadeAt < this.fadeMs; }
    progress(now) { return this.fading(now) ? (now - this.fadeAt) / this.fadeMs : 1; }
    /** The scene most visible right now. */
    dominant(now) { return this.fading(now) && ease(this.progress(now)) < 0.5 ? this.keys[this.base] : this.scene; }
    commit(key, now, fadeMs) {
      if (key === this.scene) return [];
      const ops = [];
      if (!fadeMs || this.scene === null) {
        // Instant (reduce motion, or the very first scene): the base shows it, the top is cleared.
        this.keys[this.base] = key; this.keys[this.top] = null;
        ops.push({ layer: this.base, key: key, role: 'base', opacity: 1, ms: 0 });
        ops.push({ layer: this.top, key: null, role: 'top', opacity: 0, ms: 0 });
        this.scene = key; this.former = null; this.fadeAt = 0; this.fadeMs = 0;
        return ops;
      }
      const running = this.fading(now);
      const keepBase = running && ease(this.progress(now)) < 0.5;
      if (!keepBase) {
        // The top is the most visible scene: it becomes the base, unchanged on screen.
        const was = this.base; this.base = this.top; this.top = was;
        if (this.keys[this.base] === null) this.keys[this.base] = this.scene;
        ops.push({ layer: this.base, key: this.keys[this.base], role: 'base', opacity: 1, ms: 0 });
      }
      this.former = this.keys[this.base];
      this.keys[this.top] = key;
      ops.push({ layer: this.top, key: key, role: 'top', opacity: 0, ms: 0 });
      ops.push({ layer: this.top, key: key, role: 'top', opacity: 1, ms: fadeMs });
      this.scene = key; this.fadeAt = now || 1; this.fadeMs = fadeMs;
      return ops;
    }
  }

  /**
   * Spec 7.5 failure path: when frames come too slowly, fall back to a still mesh in the right palette. intervals are
   * frame-to-frame times in ms; a still mesh is chosen when the median is slower than about 20 fps.
   */
  function shouldFallBack(intervals) {
    if (!intervals || intervals.length < 20) return false;
    const sorted = intervals.slice().sort(function (x, y) { return x - y; });
    return sorted[Math.floor(sorted.length / 2)] > 50;
  }

  /** The darkened corners and header band (AmbientMesh.shade), as one static layer. */
  const SHADE = 'radial-gradient(ellipse 72% 72% at 50% 50%, rgba(0,0,0,0) 55%, rgba(0,0,0,0.6) 100%), ' +
    'linear-gradient(to bottom, rgba(0,0,0,0.4) 0%, rgba(0,0,0,0) 24%)';

  return {
    FADE_MS: FADE_MS, LIGHT_FADE_MS: LIGHT_FADE_MS, SPRITE_STOPS: SPRITE_STOPS, SHADE: SHADE,
    colour: colour, rgb: rgb, palette: palette, known: known, lightGradient: lightGradient, lightBox: lightBox,
    position: position, driftKeyframes: driftKeyframes, keyframesCss: keyframesCss, driftDelay: driftDelay,
    motion: motion, ease: ease, Crossfade: Crossfade, shouldFallBack: shouldFallBack
  };
}));
