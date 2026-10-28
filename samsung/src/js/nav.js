/*
 * Unified TV for Samsung Frame: one navigation stack (spec 6.3, PRODUCT_SPEC 28.3).
 *
 * UnifiedHome -> Apps / Library / Settings -> Detail -> Player, with overlays (sheets, menus, the voice pane) on top.
 * Back unwinds exactly one layer and the layer underneath gets back the focus it had. At Home, Back does nothing:
 * Unified TV never closes itself from an ordinary Back press (PRODUCT_SPEC 30.3). Leaving the app is the TV's own
 * business (the Home key opens Smart Hub; a long Return is the TV's exit, which this app never intercepts) or the
 * deliberate "Open original TV interface" row in Settings.
 * The smoke and carbon monoxide screen is not part of this stack: nothing here can dismiss it.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Nav = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const PAGES = ['home', 'apps', 'library', 'settings', 'detail', 'player', 'wallpaper'];
  const OVERLAYS = ['sheet', 'menu', 'voice', 'picker', 'doorbell'];

  class Stack {
    constructor() { this.layers = [{ name: 'home', focus: null }]; }
    top() { return this.layers[this.layers.length - 1]; }
    atRoot() { return this.layers.length === 1; }
    names() { return this.layers.map(function (l) { return l.name; }); }
    /** Opens a layer, remembering the focus of the one underneath so Back can restore it. */
    push(name, focusBelow) {
      if (PAGES.indexOf(name) === -1 && OVERLAYS.indexOf(name) === -1) throw new Error('unknown layer ' + name);
      this.top().focus = focusBelow === undefined ? this.top().focus : focusBelow;
      this.layers.push({ name: name, focus: null });
      return this.top();
    }
    /** Back: one layer. At Home nothing happens and nothing exits. */
    back() {
      if (this.atRoot()) return { action: 'none', top: this.top() };
      const closed = this.layers.pop();
      return { action: 'closed', closed: closed.name, top: this.top(), focus: this.top().focus };
    }
    /** Closes everything above Home (a new emergency, a return from another app), keeping Home's saved focus. */
    home() { this.layers = [this.layers[0]]; return this.top(); }
    has(name) { return this.layers.some(function (l) { return l.name === name; }); }
  }

  return { PAGES: PAGES, OVERLAYS: OVERLAYS, Stack: Stack };
}));
