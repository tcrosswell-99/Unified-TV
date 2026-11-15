/*
 * Unified TV for Samsung Frame: Adults and Kids (owner, 25 October 2026; spec 2 "Profiles").
 *
 * A port of Android's Profiles (preview/app/src/io/unifiedtv/preview/Profiles.java); samsung/test/profiles.test.js
 * mirrors ProfilesTest case by case. A lightweight layer over one Home: the TV opens in the last-used profile and never
 * asks on start. Moving to Adults always takes the adult PIN (pin.js, with its lock-out); moving to Kids takes none,
 * but Kids can only be switched on once a PIN exists. Stored values are "adult" and "child", as on Android; the words
 * on screen are "Adults" and "Kids". A launcher PIN doesn't switch accounts inside Netflix or other apps.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Profiles = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const ADULT = 'adult', CHILD = 'child';
  const SAME = 0, NEEDS_PIN = 1, ALLOWED = 2, PIN_FIRST = 3;

  /** Anything other than "child" is Adults. */
  function stored(raw) { return raw === CHILD ? CHILD : ADULT; }
  function label(raw) { return stored(raw) === CHILD ? 'Kids' : 'Adults'; }
  function kids(raw) { return stored(raw) === CHILD; }
  /**
   * What a switch needs. To Adults: the PIN. To Kids: nothing, once a PIN exists (otherwise set one first). The one
   * exception: Kids with no PIN on record (only possible if the PIN record itself was lost, for example Key Manager
   * refusing to read it) may return to Adults, because Kids must never become a trap the household can't leave.
   */
  function rule(from, to, pinSet) {
    const f = stored(from), t = stored(to);
    if (f === t) return SAME;
    if (t === ADULT) return pinSet ? NEEDS_PIN : ALLOWED;
    return pinSet ? ALLOWED : PIN_FIRST;
  }

  return { ADULT: ADULT, CHILD: CHILD, SAME: SAME, NEEDS_PIN: NEEDS_PIN, ALLOWED: ALLOWED, PIN_FIRST: PIN_FIRST,
    stored: stored, label: label, kids: kids, rule: rule };
}));
