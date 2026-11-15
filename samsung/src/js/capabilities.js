/*
 * Unified TV for Samsung Frame: the capability registry (spec 6.3).
 *
 * Every capability is supported_verified, unsupported_verified or unknown, and is never inferred from the platform's
 * name. Nothing has been tested on the owner's Frame (QE50LS03AAUXXU, firmware T-NKM2DEUC-2310.0) with this app, so
 * every value is unknown. The "expected" column is what Samsung's documentation suggests: a plan, not a result.
 * Mirrors docs/devices/samsung-frame-2021.md.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Capabilities = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const VALUES = ['supported_verified', 'unsupported_verified', 'unknown'];
  const FRAME = [
    { id: 'wgt-install', label: 'Signed app installs and opens', value: 'unknown', expected: 'Needs the owner\'s Samsung certificates (NT-11)' },
    { id: 'remote-keys', label: 'Which remote keys reach the app', value: 'unknown', expected: 'Arrows, OK and Return; media keys after registration (RC-09)' },
    { id: 'home-key', label: 'Home key', value: 'unknown', expected: 'Opens Samsung Smart Hub; the app can\'t change that (NT-05)' },
    { id: 'autostart', label: 'Opens by itself after power-on', value: 'unknown', expected: 'Model-dependent (NT-01)' },
    { id: 'app-launch', label: 'Opening Netflix, Disney+, BBC, Music', value: 'unknown', expected: 'tizen.application.launch with ids found on the TV' },
    { id: 'tv-plus', label: 'Opening Samsung TV Plus', value: 'unknown', expected: 'Found by name; native id not confirmed (NT-04)' },
    { id: 'avplay', label: 'NAS playback in AVPlay', value: 'unknown', expected: 'Direct play of supported formats (NT-02)' },
    { id: 'dlna-discovery', label: 'Finding the NAS automatically', value: 'unknown', expected: 'Not possible from a web app: no SSDP; address entered once' },
    { id: 'key-manager', label: 'Key Manager for secrets', value: 'unknown', expected: 'Available to web apps; hardware backing unknown' },
    { id: 'hidden-events', label: 'Alerts while the app is hidden', value: 'unknown', expected: 'Not reliable: a hidden app may be suspended (NT-06)' },
    { id: 'overlay', label: 'Drawing over other apps or Art Mode', value: 'unknown', expected: 'Not possible for a Tizen web app (spec 10.8)' },
    { id: 'art-mode', label: 'Art Mode left untouched', value: 'unknown', expected: 'The app never controls Art Mode (WP-12)' },
    { id: 'vpn', label: 'NordVPN protection for Live TV', value: 'unknown', expected: 'No per-app VPN on Tizen; Live TV stays locked (NT-10)' },
    { id: 'echo-studio', label: 'Echo Studio audio after Art Mode', value: 'unknown', expected: 'Depends on the audio connection (AT-12)' },
    { id: 'pin-crypto', label: 'Adult PIN hashing (SubtleCrypto)', value: 'unknown', expected: 'May be missing outside a secure context; the JavaScript fallback is used then' },
    { id: 'kids-guard', label: 'Stopping apps opened outside Unified TV in Kids', value: 'unknown', expected: 'Not possible for a web app: only launches from Unified TV are checked (KIDS-02)' },
    { id: 'ring-person', label: 'Person toast and doorbell card', value: 'unknown', expected: 'Only while Unified TV is open and in front; no live camera yet (RING-05)' }
  ];
  function list() { return FRAME.map(function (c) { return { id: c.id, label: c.label, value: c.value, expected: c.expected }; }); }
  function label(value) { return value === 'supported_verified' ? 'Works (tested)' : value === 'unsupported_verified' ? 'Doesn\'t work (tested)' : 'Not tested yet'; }

  return { VALUES: VALUES, list: list, label: label };
}));
