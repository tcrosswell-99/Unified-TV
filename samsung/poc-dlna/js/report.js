/*
 * Unified TV DLNA discovery proof of concept: result codes, plain reasons and the summary line.
 * PROTOTYPE, not part of the Unified TV app. Pure functions, ES5, loaded by the page and by the Node tests.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.UTVPOC = api;
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var VERSION = '0.1.0';
  var METHODS = ['mc', 'bc', 'uni', 'notify'];
  var METHOD_NAMES = { mc: 'Multicast M-SEARCH', bc: 'Directed broadcast M-SEARCH', uni: 'Unicast sweep of the /24', notify: 'Passive NOTIFY listener' };

  /** Plain reasons for every result code the page or the service can give. */
  var REASONS = {
    SVC_PASS: 'The service started and answered on this TV.',
    SVC_NO_TIZEN: 'This isn\'t a Samsung TV (no Tizen), so the service can\'t start here.',
    SVC_LAUNCH_ERR: 'The TV refused to start the service. It may not allow web services for this certificate or firmware.',
    SVC_NO_ANSWER: 'The service didn\'t answer on 127.0.0.1. It may not have started, or the page isn\'t allowed to reach it.',
    SVC_TOKEN_NOT_RECEIVED: 'The service answered but never received this launch\'s token.',
    SVC_TOKEN_MISMATCH: 'The service holds a different token from this launch.',
    LOCAL_PASS: 'Only requests with this launch\'s token are answered, and the service listens on 127.0.0.1 only.',
    LOCAL_TOKEN_NOT_ENFORCED: 'A request without the right token was answered. This is a security failure.',
    LOCAL_NOT_LOOPBACK: 'The service isn\'t listening on 127.0.0.1 only.',
    LOCAL_UNEXPECTED: 'The token check gave an unexpected answer.',
    SSDP_FOUND: 'At least one media server answered.',
    SSDP_NOTHING: 'No media server answered by any method.',
    SSDP_FAILED: 'The discovery request failed.',
    NO_IFACE: 'The service found no private network address on this TV.',
    BUSY: 'A search was already running. Try again in a few seconds.',
    VALID_PASS: 'At least one answer is a media server with a ContentDirectory on a private address.',
    VALID_NONE: 'No answer could be confirmed as a usable media server.',
    BROWSE_PASS: 'The media server listed its top-level folders.',
    BROWSE_EMPTY: 'The media server answered but listed no folders at the top level.',
    BROWSE_SKIPPED: 'Nothing to browse: no media server was confirmed.',
    BROWSE_FAILED: 'The media server didn\'t list its folders.'
  };
  /** Per-method codes from the service. OK and LISTENING are normal. */
  var METHOD_REASONS = {
    OK: 'Sent.', LISTENING: 'Listening on the SSDP group.', NOT_RUN: 'Not run.', NO_UDP: 'No UDP on this TV\'s service runtime.',
    NO_SOCKET: 'Couldn\'t open a UDP socket.', BIND_ERR: 'Couldn\'t bind a UDP socket.', SEND_ERR: 'Every send failed.',
    SOCKET_ERR: 'The socket failed while sending.', NO_BROADCAST: 'No usable subnet mask, so no broadcast address.',
    BROADCAST_REFUSED: 'The TV refused broadcast on this socket.', NOT_DRAINED: 'Some unicast searches never left before the deadline.',
    NO_TARGETS: 'No addresses to sweep.', BIND_1900_ERR: 'Couldn\'t listen on port 1900 (the TV may hold it).',
    JOIN_ERR: 'Couldn\'t join the SSDP multicast group.', LISTEN_ERR: 'The listener stopped with an error.', NOT_STARTED: 'The listener hasn\'t started.'
  };
  function reason(code) { return REASONS[code] || METHOD_REASONS[code] || code; }
  function methodOk(code) { return code === 'OK' || code === 'LISTENING'; }

  function seconds(ms) { return (Math.round((ms || 0) / 100) / 10).toFixed(1) + 's'; }
  function step(result, detail) {
    if (!result) return 'SKIP';
    if (result.status === 'PASS') return 'PASS' + (detail ? '(' + detail + ')' : '');
    return result.status + '(' + (result.code || '?') + ')';
  }
  /** One method's figure for the summary: the count of media-server answers, with the code when it isn't normal. */
  function methodText(m) {
    if (!m) return '-';
    var n = m.answers || 0;
    return methodOk(m.code) ? String(n) : n + '(' + m.code + ')';
  }

  /**
   * The line the owner photographs, for example:
   * POC v0.1.0 svc=PASS(1.2s) local=PASS ssdp: mc=0 bc=1 uni=1 notify=1 valid=1 browse=PASS(5 folders)
   */
  function summaryLine(r) {
    var x = r || {};
    var parts = ['POC v' + VERSION, 'svc=' + step(x.svc, x.svc && x.svc.status === 'PASS' ? seconds(x.svc.ms) : ''), 'local=' + step(x.local)];
    var methods = x.ssdp && x.ssdp.methods;
    if (methods) parts.push('ssdp: ' + METHODS.map(function (k) { return k + '=' + methodText(methods[k]); }).join(' '));
    else parts.push('ssdp=' + step(x.ssdp));
    parts.push('valid=' + (x.valid ? String(x.valid.count || 0) : '-'));
    parts.push('browse=' + step(x.browse, x.browse && x.browse.status === 'PASS' ? x.browse.folders + ' folder' + (x.browse.folders === 1 ? '' : 's') : ''));
    return parts.join(' ');
  }

  /** A 32-character hex token from a getRandomValues-like function, or '' when there is none (the service then refuses). */
  function makeToken(getRandomValues) {
    if (typeof getRandomValues !== 'function' || typeof Uint8Array === 'undefined') return '';
    var b = new Uint8Array(16);
    getRandomValues(b);
    var out = '';
    for (var i = 0; i < b.length; i++) out += (b[i] < 16 ? '0' : '') + b[i].toString(16);
    return out;
  }

  return { VERSION: VERSION, METHODS: METHODS, METHOD_NAMES: METHOD_NAMES, REASONS: REASONS, METHOD_REASONS: METHOD_REASONS,
    reason: reason, methodOk: methodOk, summaryLine: summaryLine, makeToken: makeToken, seconds: seconds };
}));
