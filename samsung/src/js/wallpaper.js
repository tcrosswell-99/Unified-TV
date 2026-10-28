/*
 * Unified TV for Samsung Frame: Wallpaper rules (spec 8, owner decisions OD-03 and OD-04, 25 October 2026).
 *
 * A port of Android's WallpaperPolicy (preview/app/src/io/unifiedtv/preview/WallpaperPolicy.java): the default source is
 * the bundled artwork; the simple clock goes where the picture is calmest; the depth clock is used only when a prepared
 * mask really overlaps the clock. No image is ever downloaded and no mask is ever guessed.
 * Unified TV's Wallpaper is its own screen inside this app. It is not Samsung Art Mode, never calls itself Art Mode, and
 * never tries to start, stop or change Art Mode: Art Mode stays with the TV's own controls.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Wallpaper = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const ARTWORK = 'artwork', OFF = 'off';
  const AUTO = 'auto', SIMPLE = 'simple', NONE = 'none';
  const IDLE_MINUTES = [0, 2, 5, 10, 15];
  const SLIDE_SECONDS = [30, 60, 300, 600];
  const DEFAULT_IDLE = 5, DEFAULT_SLIDE = 300;
  /** The grid the picture and mask are measured on: 16 x 9 cells over the screen. */
  const GW = 16, GH = 9;
  /** Shown in Settings beside the Wallpaper options. */
  const NOT_ART_MODE = 'This is Unified TV\'s own Wallpaper. It isn\'t Samsung Art Mode, and it doesn\'t change Art Mode: use your TV\'s own controls for that.';

  function source(saved) { return saved === OFF ? OFF : ARTWORK; }
  function clock(saved) { return saved === SIMPLE || saved === NONE ? saved : AUTO; }
  function idle(saved) { return IDLE_MINUTES.indexOf(saved) !== -1 ? saved : DEFAULT_IDLE; }
  function slide(saved) { return SLIDE_SECONDS.indexOf(saved) !== -1 ? saved : DEFAULT_SLIDE; }

  function sourceLabel(s) { return s === OFF ? 'Off' : 'Artwork'; }
  function clockLabel(c) { return c === SIMPLE ? 'Simple clock' : c === NONE ? 'No clock' : 'Depth clock when the picture suits it'; }
  function idleLabel(m) { return m === 0 ? 'Never' : 'After ' + m + ' minutes'; }
  function slideLabel(s) { return s < 60 ? s + ' seconds' : s === 60 ? '1 minute' : (s / 60) + ' minutes'; }

  /** Cell rectangles [left, top, right, bottom] where a simple clock may sit, clear of the TV's overscan edge. */
  const SIMPLE_SPOTS = [[1, 1, 6, 3], [10, 1, 15, 3], [1, 6, 6, 8], [10, 6, 15, 8], [5, 1, 11, 3]];
  /** Where the large depth clock sits: the upper middle, behind a subject's head and shoulders. */
  const DEPTH_SPOT = [3, 1, 13, 5];

  function mean(grid, r) {
    let sum = 0, n = 0;
    for (let y = r[1]; y < r[3]; y++) for (let x = r[0]; x < r[2]; x++) { sum += grid[y * GW + x]; n++; }
    return n === 0 ? 0 : sum / n;
  }
  function variance(grid, r) {
    const m = mean(grid, r); let sum = 0, n = 0;
    for (let y = r[1]; y < r[3]; y++) for (let x = r[0]; x < r[2]; x++) { const d = grid[y * GW + x] - m; sum += d * d; n++; }
    return n === 0 ? 0 : sum / n;
  }
  /** The calmest places for the simple clock, best first: lowest brightness variation inside the spot. */
  function calmest(luminance) {
    const score = SIMPLE_SPOTS.map(function (s) { return variance(luminance, s); });
    const order = SIMPLE_SPOTS.map(function (s, i) { return i; });
    order.sort(function (a, b) { return score[a] - score[b]; });
    return order;
  }
  /** The spot for this slide: alternate between the two calmest, so the clock never sits in one place for hours (OLED). */
  function spot(luminance, slideIndex) {
    const best = calmest(luminance);
    return SIMPLE_SPOTS[best[Math.min(best.length - 1, slideIndex % 2)]];
  }
  /** A bright spot needs a soft shade behind white numerals; dark ones don't. */
  function needsShade(luminance, s) { return mean(luminance, s) > 0.55; }
  /** The depth clock suits a picture only when its prepared mask covers some of the clock, but not so much the time can't be read. */
  function depthSuits(mask) {
    if (!mask) return false;
    const covered = mean(mask, DEPTH_SPOT);
    return covered >= 0.08 && covered <= 0.55;
  }
  /** What to draw for this picture under the chosen clock setting. */
  function clockFor(setting, mask) {
    if (setting === NONE) return NONE;
    if (setting === AUTO && depthSuits(mask)) return 'depth';
    return SIMPLE;
  }

  /**
   * The 16 x 9 luminance grid from RGBA pixels already scaled down to 16 x 9 (a canvas does that on the TV, once per
   * picture, off the focus path). Values 0 to 1.
   */
  function luminanceGrid(rgba) {
    const out = [];
    for (let i = 0; i < GW * GH; i++) {
      const r = rgba[i * 4], g = rgba[i * 4 + 1], b = rgba[i * 4 + 2];
      out.push((0.2126 * r + 0.7152 * g + 0.0722 * b) / 255);
    }
    return out;
  }
  /** The bundled catalogue, checked: a picture needs a file inside the app and a credit; a mask is used only if listed. */
  function catalogue(list) {
    return (Array.isArray(list) ? list : []).filter(function (p) {
      return p && typeof p.file === 'string' && /^wallpaper\/[A-Za-z0-9._-]+\.(jpg|jpeg|png)$/.test(p.file) && typeof p.credit === 'string' && p.credit;
    });
  }
  /** Spec 8: the first press of D-pad, OK or Back only leaves Wallpaper and shows Home with focus; it never opens an app. */
  function dismissKey(action) { return action === 'up' || action === 'down' || action === 'left' || action === 'right' || action === 'ok' || action === 'back'; }

  return {
    ARTWORK: ARTWORK, OFF: OFF, AUTO: AUTO, SIMPLE: SIMPLE, NONE: NONE, IDLE_MINUTES: IDLE_MINUTES, SLIDE_SECONDS: SLIDE_SECONDS,
    DEFAULT_IDLE: DEFAULT_IDLE, DEFAULT_SLIDE: DEFAULT_SLIDE, GW: GW, GH: GH, NOT_ART_MODE: NOT_ART_MODE,
    SIMPLE_SPOTS: SIMPLE_SPOTS, DEPTH_SPOT: DEPTH_SPOT,
    source: source, clock: clock, idle: idle, slide: slide, sourceLabel: sourceLabel, clockLabel: clockLabel,
    idleLabel: idleLabel, slideLabel: slideLabel, mean: mean, variance: variance, calmest: calmest, spot: spot,
    needsShade: needsShade, depthSuits: depthSuits, clockFor: clockFor, luminanceGrid: luminanceGrid,
    catalogue: catalogue, dismissKey: dismissKey
  };
}));
