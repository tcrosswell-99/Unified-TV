/*
 * Unified TV for Samsung Frame: the Home focus graph (spec 3.1, 6.3, 28.2), as pure rules.
 *
 * Home has three rows: the small utility group at the top right (the profile name, voice, Settings), the dock, and the quiet
 * "Films & TV" chevron below it. Left and Right move along a row and stop at its ends (never wrap, never trap);
 * Up and Down move between rows and come back to the same tile. Focus changes never launch anything: OK is the only
 * activation (spec 7.4).
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Focus = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  /** The top row. As on Android, Up reaches it and Left reaches the profile name ("Adults" or "Kids"): OK there switches. */
  const UTILITIES = ['profile', 'voice', 'settings'];
  const SETTINGS = UTILITIES.indexOf('settings');

  function initial(dockCount, savedIndex) {
    const count = Math.max(0, dockCount | 0);
    const index = count === 0 ? 0 : Math.max(0, Math.min(count - 1, savedIndex | 0));
    return { row: 'dock', dock: index, util: SETTINGS };
  }
  function clamp(n, count) { return count <= 0 ? 0 : Math.max(0, Math.min(count - 1, n)); }

  /**
   * One D-pad press. dockCount may be 0 (an empty Home): the dock then holds one "Add apps" slot so it stays
   * recoverable (AT-56). Returns a new state; the old one is never changed.
   */
  function move(state, direction, dockCount) {
    const slots = Math.max(1, dockCount | 0);
    const s = { row: state.row, dock: clamp(state.dock, slots), util: clamp(state.util, UTILITIES.length) };
    if (s.row === 'dock') {
      if (direction === 'left') s.dock = clamp(s.dock - 1, slots);
      else if (direction === 'right') s.dock = clamp(s.dock + 1, slots);
      else if (direction === 'up') s.row = 'util';
      else if (direction === 'down') s.row = 'more';
    } else if (s.row === 'util') {
      if (direction === 'left') s.util = clamp(s.util - 1, UTILITIES.length);
      else if (direction === 'right') s.util = clamp(s.util + 1, UTILITIES.length);
      else if (direction === 'down') s.row = 'dock';
    } else if (s.row === 'more') {
      if (direction === 'up') s.row = 'dock';
    }
    return s;
  }

  /** What has focus: {kind: 'dock', index} | {kind: 'util', id} | {kind: 'more'}. */
  function target(state) {
    if (state.row === 'util') return { kind: 'util', id: UTILITIES[state.util] };
    if (state.row === 'more') return { kind: 'more' };
    return { kind: 'dock', index: state.dock };
  }

  /**
   * The scene for the current focus, as one atomic decision (spec 32.6): the focused app's id drives the palette, the
   * title and the halo together. Utilities and the chevron keep the last dock app's scene so nothing flashes.
   */
  function scene(state, dock) {
    if (!dock || dock.length === 0) return { palette: 'neutral', title: '' };
    const app = dock[clamp(state.dock, dock.length)];
    if (state.row === 'more') return { palette: app.id, title: 'Films & TV' };
    return { palette: app.id, title: state.row === 'dock' ? app.name : '' };
  }

  /** A move inside a dock being rearranged: the lifted tile travels left or right; nothing else changes. */
  function shift(order, from, direction) {
    const to = direction === 'left' ? from - 1 : direction === 'right' ? from + 1 : from;
    if (to < 0 || to >= order.length || to === from) return { order: order.slice(), index: from };
    const next = order.slice();
    const item = next.splice(from, 1)[0];
    next.splice(to, 0, item);
    return { order: next, index: to };
  }

  /** Vertical lists (Settings, menus, sheets): Up and Down stop at the ends. */
  function list(index, direction, count) {
    if (direction === 'up') return clamp(index - 1, count);
    if (direction === 'down') return clamp(index + 1, count);
    return clamp(index, count);
  }

  return { UTILITIES: UTILITIES, initial: initial, move: move, target: target, scene: scene, shift: shift, list: list };
}));
