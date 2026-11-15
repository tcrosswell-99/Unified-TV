/*
 * Unified TV for Samsung Frame: the Live TV guard (spec 2 rule 8, 11, PRODUCT_SPEC 24.5, NT-10).
 *
 * Live TV is fail-closed. On the Frame there is no Live TV app (ReamSmartz is Android-only) and nothing has proven that
 * NordVPN protects this TV: Tizen has no per-app VPN routing, and a router or VLAN route would need its own fail-closed,
 * DNS and IPv6 evidence. So every route into Live TV (the tile, a matchday card, voice, search, a resume, a channel or
 * Guide key) asks decide(), and in this build decide() always answers "locked". There is no setting, flag or saved value
 * that unlocks it here; unlocking needs a later, reviewed change backed by hardware evidence.
 * Samsung TV Plus is a separate service and is not under this guard.
 * The VPN label is truthful: a web app on the Frame can't observe a VPN, so it always reads "VPN unknown".
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.LiveTv = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const ROUTES = ['tile', 'matchday', 'voice', 'search', 'resume', 'channel-key', 'guide-key'];
  /** What would have to be proven on this TV before Live TV could ever open (none is, on the Frame). */
  const REQUIRED_EVIDENCE = ['nativeClient', 'provider', 'routing', 'dropProtection', 'afterStandby', 'dns', 'ipv6', 'lanStillWorks'];
  const VPN_STATES = ['Connected', 'Disconnected', 'Checking', 'Unknown'];

  const LOCKED = {
    locked: true,
    title: 'Live TV is locked on this TV',
    message: 'There is no Live TV app for this Samsung TV yet, and nothing has shown that NordVPN protects it. ' +
      'Until both are checked on this TV, Live TV stays locked.',
    actions: ['Back']
  };

  /** Every route asks here. The answer on the Frame is always the locked sheet, whatever it is given. */
  function decide(route) {
    const r = ROUTES.indexOf(route) === -1 ? 'unknown' : route;
    return { locked: LOCKED.locked, route: r, title: LOCKED.title, message: LOCKED.message, actions: LOCKED.actions.slice() };
  }
  /** Samsung TV Plus is its own native service, outside the ReamSmartz guard (spec 11, AT-34). */
  function guards(appId) { return appId === 'live'; }
  /** The utility group's text: a Tizen web app has no way to see a VPN, so the honest state is Unknown. */
  function vpnLabel() { return 'VPN unknown'; }

  return { ROUTES: ROUTES, REQUIRED_EVIDENCE: REQUIRED_EVIDENCE, VPN_STATES: VPN_STATES, decide: decide, guards: guards, vpnLabel: vpnLabel };
}));
