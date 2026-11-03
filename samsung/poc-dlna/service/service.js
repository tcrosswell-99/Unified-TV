/*
 * Unified TV DLNA discovery proof of concept: the service (a Tizen web service on the TV's Node runtime).
 *
 * PROTOTYPE. Not part of the Unified TV app. Nothing here has run on the owner's Frame yet.
 *
 * Why a service: a Tizen web page can't send UDP, so it can't do SSDP. A <tizen:service> packaged in the same .wgt runs
 * on the TV's Node runtime and can. The architecture (a hybrid service on 127.0.0.1, started by the page; multicast,
 * broadcast and unicast M-SEARCH plus a passive NOTIFY listener) follows Tessel (PatrickSt1991/tessel-tizen-tv, MIT),
 * issue #131. This file is our own code written from that description; no Tessel code is copied.
 *
 * What it does, and nothing more:
 *   - listens on 127.0.0.1 only, never on a LAN interface;
 *   - answers only requests that carry the per-launch token the page passed in the launch ApplicationControl data;
 *   - finds media servers by SSDP four ways, each counted separately: multicast M-SEARCH, subnet directed broadcast,
 *     a unicast sweep of the TV's own /24 (never wider), and a passive NOTIFY listener on the SSDP group;
 *   - reads each answer's LOCATION only when it is a private IPv4 address and the same host that answered;
 *   - keeps the validated servers' ContentDirectory control URLs itself and browses only those, by server index.
 * It is not a proxy: it never fetches a URL the page names, and it never carries media (AVPlay would play from the NAS).
 *
 * ES5 and CommonJS only, no npm dependencies: the TV's Node build is old (Tessel reports one without Buffer.alloc).
 * Unit tests: samsung/poc-dlna/test/. Every network and timer dependency can be injected.
 */
'use strict';

var http = require('http');
var os = require('os');
var urlMod = require('url');
var dgram = null;
try { dgram = require('dgram'); } catch (e) { dgram = null; }

var VERSION = '0.1.0';
var PORT = 18731;                         // fixed so the page's Content-Security-Policy can name it
var LOOPBACK = '127.0.0.1';
var SSDP_GROUP = [239, 255, 255, 250].join('.');   // the standard SSDP multicast group (built from parts: samsung/test/source.test.js)
var SSDP_PORT = 1900;
var MEDIA_SERVER_ST = 'urn:schemas-upnp-org:device:MediaServer:1';
var CDS_TYPE = 'urn:schemas-upnp-org:service:ContentDirectory:1';
var TOKEN_KEY = 'utv-token';

var LIMITS = {
  repeatMs: 800,          // UDP gets lost: every search is sent twice, this far apart
  windowMs: 4000,         // answers are collected this long after the last search has left
  sweepChunk: 64,         // unicast targets per socket, so the send buffer never fills (Tessel #131)
  sweepDeadlineMs: 12000, // the unicast sweep gives up waiting for its queue to drain after this
  httpMs: 4000,           // per description or Browse request
  descriptionBytes: 262144,
  browseBytes: 2097152,
  browseCount: 50,
  maxCandidates: 24,      // distinct LOCATIONs read per run
  maxServers: 8,
  logLines: 300
};

/* ------------------------------------------------------------------ small helpers */

/** A Buffer from text on any Node: before 4.5, Buffer.from is TypedArray.from and must not be used. */
function bytes(text) {
  if (typeof Buffer.from === 'function' && Buffer.from !== Uint8Array.from) return Buffer.from(String(text), 'utf8');
  return new Buffer(String(text), 'utf8');   // eslint-disable-line no-buffer-constructor
}

var LOG = [];
function log(code, detail) {
  var line = new Date().toISOString() + ' ' + code + (detail === undefined ? '' : ' ' + String(detail));
  LOG.push(line);
  if (LOG.length > LIMITS.logLines) LOG.shift();
  try { console.log('[UTVPOC] ' + line); } catch (e) { /* no console */ }
}

/* ------------------------------------------------------------------ addresses */

function parseIPv4(s) {
  var m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(String(s || ''));
  if (!m) return null;
  var p = [+m[1], +m[2], +m[3], +m[4]];
  for (var i = 0; i < 4; i++) if (p[i] > 255) return null;
  return p;
}
function toInt(p) { return ((p[0] << 24) >>> 0) + (p[1] << 16) + (p[2] << 8) + p[3]; }
function fromInt(n) { return [(n >>> 24) & 255, (n >>> 16) & 255, (n >>> 8) & 255, n & 255].join('.'); }

/** Private IPv4 only: 10/8, 172.16/12, 192.168/16. */
function isPrivateIPv4(s) {
  var p = parseIPv4(s);
  if (!p) return false;
  return p[0] === 10 || (p[0] === 172 && p[1] >= 16 && p[1] <= 31) || (p[0] === 192 && p[1] === 168);
}
function isLoopbackIPv4(s) { var p = parseIPv4(s); return !!p && p[0] === 127; }
/** The guard for every outgoing HTTP request. Loopback is allowed only when a test asks for it. */
function hostAllowed(host, opts) {
  return isPrivateIPv4(host) || (!!(opts && opts.allowLoopback) && isLoopbackIPv4(host));
}

function prefixOf(mask) {
  var p = parseIPv4(mask);
  if (!p) return -1;
  var n = toInt(p), bits = 0, seenZero = false;
  for (var i = 31; i >= 0; i--) {
    if ((n >>> i) & 1) { if (seenZero) return -1; bits++; } else seenZero = true;
  }
  return bits;
}

/**
 * The unicast sweep's targets: the TV's own /24, never wider. A narrower subnet (/25 to /30) limits it further.
 * The TV's own address, the network address and the broadcast address are left out.
 */
function sweepTargets(address, netmask) {
  var p = parseIPv4(address);
  if (!p) return [];
  var prefix = prefixOf(netmask);
  if (prefix < 24 || prefix > 30) prefix = 24;
  var size = Math.pow(2, 32 - prefix);
  var self = toInt(p);
  var network = Math.floor(self / size) * size;
  var out = [];
  for (var n = network + 1; n < network + size - 1; n++) if (n !== self) out.push(fromInt(n));
  return out;
}
/** The subnet's directed broadcast address, or null when the mask is unusable. */
function directedBroadcast(address, netmask) {
  var p = parseIPv4(address), prefix = prefixOf(netmask);
  if (!p || prefix < 8 || prefix > 30) return null;
  var size = Math.pow(2, 32 - prefix);
  return fromInt(Math.floor(toInt(p) / size) * size + size - 1);
}
/** The TV's private IPv4 interfaces from os.networkInterfaces(). */
function privateInterfaces(table) {
  var out = [];
  Object.keys(table || {}).forEach(function (name) {
    (table[name] || []).forEach(function (a) {
      var v4 = a && (a.family === 'IPv4' || a.family === 4);
      if (v4 && !a.internal && isPrivateIPv4(a.address)) out.push({ name: name, address: a.address, netmask: a.netmask || '' });
    });
  });
  return out;
}

/* ------------------------------------------------------------------ SSDP */

function searchMessage(st, group, port) {
  return 'M-SEARCH * HTTP/1.1\r\nHOST: ' + (group || SSDP_GROUP) + ':' + (port || SSDP_PORT) + '\r\n' +
    'MAN: "ssdp:discover"\r\nMX: 2\r\nST: ' + (st || MEDIA_SERVER_ST) + '\r\nUSER-AGENT: UnifiedTV-POC/' + VERSION + ' UPnP/1.1\r\n\r\n';
}
/** An SSDP datagram: { kind: 'response' | 'notify', headers } with lower-case header names, or null. */
function parseSsdp(text) {
  var lines = String(text || '').split(/\r?\n/);
  var kind = /^HTTP\/1\.[01]\s+200\b/i.test(lines[0]) ? 'response' : /^NOTIFY\s+\*\s+HTTP\/1\.[01]/i.test(lines[0]) ? 'notify' : null;
  if (!kind) return null;
  var headers = {};
  for (var i = 1; i < lines.length; i++) {
    var c = lines[i].indexOf(':');
    if (c > 0) headers[lines[i].slice(0, c).trim().toLowerCase()] = lines[i].slice(c + 1).trim();
  }
  return { kind: kind, headers: headers };
}
/** Whether an SSDP datagram is about a media server (search target or notification type). */
function aboutMediaServer(msg) {
  var t = msg && (msg.headers.st || msg.headers.nt || '');
  return /:device:MediaServer:\d/i.test(t) || /:service:ContentDirectory:\d/i.test(t);
}
/**
 * A LOCATION may be read only when it is plain http to a private IPv4 address, with no user name or password, and on
 * the same host that sent the SSDP datagram. { ok, url } or { ok: false, code }.
 */
function checkLocation(location, from, opts) {
  var u;
  try { u = urlMod.parse(String(location || '')); } catch (e) { return { ok: false, code: 'LOC_BAD_URL' }; }
  if (!u || u.protocol !== 'http:' || !u.hostname) return { ok: false, code: 'LOC_BAD_URL' };
  if (u.auth) return { ok: false, code: 'LOC_CREDENTIALS' };
  if (!parseIPv4(u.hostname)) return { ok: false, code: 'LOC_NOT_IPV4' };
  if (!hostAllowed(u.hostname, opts)) return { ok: false, code: 'LOC_NOT_PRIVATE' };
  if (u.hostname !== from) return { ok: false, code: 'LOC_HOST_MISMATCH' };
  return { ok: true, url: u.href, host: u.hostname };
}

/* ------------------------------------------------------------------ XML (machine-written, one shape per server) */

function unescapeXml(s) {
  return String(s || '').replace(/&(lt|gt|quot|apos|amp|#\d+|#x[0-9a-f]+);/gi, function (m, e) {
    if (e.charAt(0) === '#') {
      var code = e.charAt(1).toLowerCase() === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return code > 0 && code < 0x10000 ? String.fromCharCode(code) : '';
    }
    return { lt: '<', gt: '>', quot: '"', apos: '\'', amp: '&' }[e.toLowerCase()];
  });
}
function escapeXml(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
function blocks(xml, name) {
  var re = new RegExp('<(?:[\\w.-]+:)?' + name + '\\b([^>]*)>([\\s\\S]*?)</(?:[\\w.-]+:)?' + name + '\\s*>', 'gi');
  var out = [], m;
  while ((m = re.exec(String(xml || '')))) out.push({ attrs: m[1], body: m[2] });
  return out;
}
function text(xml, name) { var b = blocks(xml, name); return b.length ? unescapeXml(b[0].body).trim() : ''; }
function attr(attrs, name) {
  var m = new RegExp('(?:^|\\s)' + name + '\\s*=\\s*"([^"]*)"').exec(attrs || '');
  return m ? unescapeXml(m[1]) : '';
}

/**
 * A device description must name a MediaServer device and a ContentDirectory service whose control URL is on the same
 * host as the description. { ok, udn, name, model, controlUrl } or { ok: false, code }.
 */
function parseDescription(xml, location, opts) {
  if (typeof xml !== 'string' || !/<(?:[\w.-]+:)?root\b/i.test(xml)) return { ok: false, code: 'DESC_NOT_XML' };
  var devices = blocks(xml, 'device');
  var types = (xml.match(/<(?:[\w.-]+:)?deviceType\s*>[\s\S]*?<\//gi) || []).map(function (t) { return unescapeXml(t.replace(/<[^>]*>|<\//g, '')).trim(); });
  if (!devices.length || !types.some(function (t) { return /^urn:schemas-upnp-org:device:MediaServer:\d+$/i.test(t); })) return { ok: false, code: 'NOT_MEDIA_SERVER' };
  var control = '';
  blocks(xml, 'service').forEach(function (s) {
    if (!control && /^urn:schemas-upnp-org:service:ContentDirectory:\d+$/i.test(text(s.body, 'serviceType'))) control = text(s.body, 'controlURL');
  });
  if (!control) return { ok: false, code: 'NO_CONTENT_DIRECTORY' };
  var base = text(xml, 'URLBase') || location;
  var abs = urlMod.resolve(base, control);
  var u = urlMod.parse(abs), l = urlMod.parse(String(location));
  if (u.protocol !== 'http:' || u.auth || !hostAllowed(u.hostname, opts) || u.hostname !== l.hostname) return { ok: false, code: 'CONTROL_HOST_MISMATCH' };
  return { ok: true, udn: text(xml, 'UDN'), name: text(xml, 'friendlyName') || 'Media server', model: text(xml, 'modelName'), controlUrl: u.href };
}

function browseBody(objectId, start, count) {
  return '<?xml version="1.0" encoding="utf-8"?>' +
    '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>' +
    '<u:Browse xmlns:u="' + CDS_TYPE + '"><ObjectID>' + escapeXml(objectId) + '</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag>' +
    '<Filter>*</Filter><StartingIndex>' + (start | 0) + '</StartingIndex><RequestedCount>' + (count | 0) + '</RequestedCount>' +
    '<SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>';
}
/** A Browse reply: { ok, containers: [{ id, title, childCount }], items, returned, total } or { ok: false, code }. */
function parseBrowse(xml) {
  if (typeof xml !== 'string') return { ok: false, code: 'BROWSE_BAD_REPLY' };
  var resultBlock = blocks(xml, 'Result');
  var returned = parseInt(text(xml, 'NumberReturned'), 10);
  if (!resultBlock.length || isNaN(returned)) return { ok: false, code: 'BROWSE_BAD_REPLY' };
  var raw = resultBlock[0].body.replace(/^\s*<!\[CDATA\[([\s\S]*)\]\]>\s*$/, '$1');
  var didl = /<DIDL-Lite/.test(raw) ? raw : unescapeXml(raw);
  var containers = blocks(didl, 'container').map(function (c) {
    return { id: attr(c.attrs, 'id'), title: text(c.body, 'title') || 'Untitled folder', childCount: parseInt(attr(c.attrs, 'childCount'), 10) || 0 };
  }).filter(function (c) { return c.id !== ''; });
  var total = parseInt(text(xml, 'TotalMatches'), 10);
  return { ok: true, containers: containers, items: blocks(didl, 'item').length, returned: returned, total: isNaN(total) ? returned : total };
}

/* ------------------------------------------------------------------ the token */

/** Constant-time comparison; an expected token shorter than 16 characters never matches (fail closed). */
function tokenOk(expected, given) {
  if (typeof expected !== 'string' || expected.length < 16 || typeof given !== 'string' || given.length !== expected.length) return false;
  var diff = 0;
  for (var i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ given.charCodeAt(i);
  return diff === 0;
}
/** The token from the launch ApplicationControl data (key "utv-token"), or ''. The data's order isn't guaranteed. */
function tokenFromAppControl(reqAppControl) {
  var data = reqAppControl && reqAppControl.appControl && reqAppControl.appControl.data;
  if (!data || typeof data.length !== 'number') return '';
  for (var i = 0; i < data.length; i++) {
    var v = data[i] && data[i].key === TOKEN_KEY && data[i].value ? data[i].value[0] : null;
    if (typeof v === 'string' && /^[0-9a-f]{32,128}$/.test(v)) return v;
  }
  return '';
}

/* ------------------------------------------------------------------ UDP */

/**
 * Sends one message to every target and calls done({ sent, errors, codes }) only when every send has called back,
 * so a socket is never closed while datagrams wait in its queue (the cause of Tessel #131's lost sweep).
 */
function sendAll(sock, message, targets, port, done) {
  var pending = targets.length, sent = 0, errors = 0, codes = {};
  if (!pending) { done({ sent: 0, errors: 0, codes: codes }); return; }
  function back(err) {
    if (err) { errors++; codes[err.code || 'SEND_ERR'] = (codes[err.code || 'SEND_ERR'] || 0) + 1; } else sent++;
    if (--pending === 0) done({ sent: sent, errors: errors, codes: codes });
  }
  targets.forEach(function (t) {
    try { sock.send(message, 0, message.length, port, t, back); } catch (e) { back(e); }
  });
}

/**
 * The passive listener: joins the SSDP group on port 1900 and remembers every media-server NOTIFY it hears.
 * It runs for the service's lifetime, because servers announce rarely (MiniDLNA's default is every 895 s).
 */
function Passive(options) {
  var o = options || {};
  this.createSocket = o.createSocket || (dgram ? function (t) { return dgram.createSocket(t); } : null);
  this.port = o.port || SSDP_PORT;
  this.group = o.group || SSDP_GROUP;
  this.now = o.now || Date.now;
  this.heard = {};       // location -> { from, at, nt }
  this.code = 'NOT_STARTED';
  this.joined = 0;
  this.since = 0;
  this.sock = null;
}
Passive.prototype.start = function (ifaces) {
  var self = this;
  if (this.sock) return;
  if (!this.createSocket) { this.code = 'NO_UDP'; return; }
  var sock;
  try { sock = this.createSocket({ type: 'udp4', reuseAddr: true }); } catch (e) { this.code = 'NO_SOCKET'; log('NOTIFY_SOCKET_ERR', e && e.message); return; }
  this.sock = sock;
  sock.on('message', function (buf, rinfo) { self.take(String(buf), rinfo && rinfo.address); });
  sock.on('error', function (e) { self.code = self.joined ? 'LISTEN_ERR' : 'BIND_1900_ERR'; log('NOTIFY_ERR', e && e.message); try { sock.close(); } catch (x) { /* closed */ } self.sock = null; });
  try {
    sock.bind(this.port, function () {
      (ifaces || []).forEach(function (i) {
        try { sock.addMembership(self.group, i.address); self.joined++; } catch (e) { log('NOTIFY_JOIN_ERR', e && e.message); }
      });
      self.code = self.joined ? 'LISTENING' : 'JOIN_ERR';
      self.since = self.now();
      log('NOTIFY_' + self.code, self.joined + ' interface(s)');
    });
  } catch (e) { this.code = 'BIND_1900_ERR'; log('NOTIFY_BIND_ERR', e && e.message); }
};
Passive.prototype.take = function (raw, from) {
  var msg = parseSsdp(raw);
  if (!msg || msg.kind !== 'notify' || !aboutMediaServer(msg)) return;
  var loc = msg.headers.location;
  if (/ssdp:byebye/i.test(msg.headers.nts || '')) return;
  if (loc && from) this.heard[loc] = { from: from, at: this.now(), nt: msg.headers.nt || '' };
};
Passive.prototype.stop = function () { if (this.sock) { try { this.sock.close(); } catch (e) { /* closed */ } } this.sock = null; };

/**
 * One discovery run, every method at once, each on its own sockets so answers are counted per method:
 * mc (multicast M-SEARCH), bc (directed broadcast), uni (unicast sweep of the /24), notify (the passive listener).
 * opts: { iface: { address, netmask }, createSocket, passive, ssdpPort, group, timers, limits }.
 * done(report) with report.methods[m] = { code, sent, errors, ... } and report.candidates = [{ location, from, methods }].
 */
function search(opts, done) {
  var o = opts || {};
  var L = o.limits || LIMITS;
  var timers = o.timers || { set: setTimeout, clear: clearTimeout };
  var createSocket = o.createSocket || (dgram ? function (t) { return dgram.createSocket(t); } : null);
  var port = o.ssdpPort || SSDP_PORT, group = o.group || SSDP_GROUP;
  var iface = o.iface;
  var methods = { mc: { code: 'NOT_RUN', sent: 0, errors: 0, answers: 0 }, bc: { code: 'NOT_RUN', sent: 0, errors: 0, answers: 0 },
    uni: { code: 'NOT_RUN', targets: 0, sockets: 0, sent: 0, errors: 0, drained: false, undrained: 0, answers: 0 },
    notify: { code: o.passive ? o.passive.code : 'NOT_RUN', heard: 0, listeningMs: 0 } };
  var found = {};    // location -> { location, from, methods: [] }
  var sockets = [];
  var outstanding = 0, finished = false, closed = false, hardStop = null, deadline = null;
  var message = bytes(searchMessage(MEDIA_SERVER_ST, group, port));

  if (!iface || !parseIPv4(iface.address)) { done({ code: 'NO_IFACE', methods: methods, candidates: [] }); return; }
  if (!createSocket) { ['mc', 'bc', 'uni'].forEach(function (m) { methods[m].code = 'NO_UDP'; }); }

  function record(method) {
    return function (buf, rinfo) {
      var msg = parseSsdp(String(buf));
      if (!msg || !aboutMediaServer(msg) || !msg.headers.location || !rinfo) return;
      var key = msg.headers.location;
      var f = found[key] || (found[key] = { location: key, from: rinfo.address, methods: [] });
      if (f.methods.indexOf(method) === -1) { f.methods.push(method); methods[method].answers++; }
    };
  }
  /** A socket for one method. Its work counts as outstanding until it calls finish, or until binding fails. */
  function open(method, onReady) {
    var sock, ready = false, released = false;
    function release() { if (!released) { released = true; outstanding--; settle(); } }
    try { sock = createSocket({ type: 'udp4' }); } catch (e) { methods[method].code = 'NO_SOCKET'; return; }
    sockets.push(sock);
    sock.on('message', record(method));
    sock.on('error', function (e) {
      log('SSDP_SOCKET_ERR', method + ' ' + (e && (e.code || e.message)));
      if (!ready) { methods[method].code = 'BIND_ERR'; release(); }
      else if (methods[method].code === 'NOT_RUN') methods[method].code = 'SOCKET_ERR';
    });
    outstanding++;
    try {
      sock.bind(0, iface.address, function () { ready = true; onReady(sock, release); });
    } catch (e) { methods[method].code = 'BIND_ERR'; release(); }
  }
  function twice(sock, method, targets, finish) {
    var m = methods[method];
    sendAll(sock, message, targets, port, function (r1) {
      m.sent += r1.sent; m.errors += r1.errors;
      timers.set(function () {
        sendAll(sock, message, targets, port, function (r2) {
          m.sent += r2.sent; m.errors += r2.errors;
          m.code = m.sent ? 'OK' : 'SEND_ERR';
          finish();
        });
      }, L.repeatMs);
    });
  }
  function settle() {
    if (outstanding > 0 || finished || !begun) return;
    finished = true;
    timers.set(close, L.windowMs);
  }
  function close() {
    if (closed) return;
    closed = true;
    if (hardStop !== null) timers.clear(hardStop);
    if (deadline !== null) timers.clear(deadline);
    sockets.forEach(function (s) { try { s.close(); } catch (e) { /* closed */ } });
    if (o.passive) {
      methods.notify.code = o.passive.code;
      methods.notify.listeningMs = o.passive.since ? Math.max(0, (o.now || Date.now)() - o.passive.since) : 0;
      Object.keys(o.passive.heard).forEach(function (loc) {
        var h = o.passive.heard[loc];
        var f = found[loc] || (found[loc] = { location: loc, from: h.from, methods: [] });
        if (f.methods.indexOf('notify') === -1) { f.methods.push('notify'); methods.notify.answers = (methods.notify.answers || 0) + 1; }
      });
      methods.notify.heard = methods.notify.answers || 0;
    }
    var candidates = Object.keys(found).map(function (k) { return found[k]; });
    done({ code: 'SSDP_DONE', methods: methods, candidates: candidates });
  }
  var begun = false;
  // Whatever happens to a socket, a run ends: the sweep's deadline plus the answer window, then everything is closed.
  hardStop = timers.set(function () { log('SSDP_HARD_STOP', outstanding + ' outstanding'); close(); }, L.sweepDeadlineMs + L.windowMs + L.repeatMs);

  if (createSocket) {
    // Multicast M-SEARCH to the SSDP group.
    open('mc', function (sock, finish) {
      try { if (typeof sock.setMulticastTTL === 'function') sock.setMulticastTTL(2); } catch (e) { /* default TTL */ }
      try { if (typeof sock.setMulticastInterface === 'function') sock.setMulticastInterface(iface.address); } catch (e) { /* Node < 8.6 */ }
      twice(sock, 'mc', [group], finish);
    });
    // Directed broadcast to the subnet.
    var bcast = directedBroadcast(iface.address, iface.netmask);
    if (!bcast) methods.bc.code = 'NO_BROADCAST';
    else open('bc', function (sock, finish) {
      try { sock.setBroadcast(true); } catch (e) { methods.bc.code = 'BROADCAST_REFUSED'; }
      twice(sock, 'bc', [bcast], finish);
    });
    // Unicast sweep of the /24, in chunks, each socket closed only after its queue has drained.
    var targets = sweepTargets(iface.address, iface.netmask);
    var u = methods.uni;
    u.targets = targets.length;
    var chunks = [];
    for (var k = 0; k < targets.length; k += L.sweepChunk) chunks.push(targets.slice(k, k + L.sweepChunk));
    var undrained = targets.length;
    deadline = timers.set(function () {
      if (!u.drained) { u.undrained = undrained; u.code = 'NOT_DRAINED'; log('SWEEP_NOT_DRAINED', undrained); }
    }, L.sweepDeadlineMs);
    var chunksLeft = chunks.length;
    chunks.forEach(function (chunk) {
      open('uni', function (sock, finish) {
        u.sockets++;
        sendAll(sock, message, chunk, port, function (r) {
          u.sent += r.sent; u.errors += r.errors; undrained -= chunk.length;
          if (--chunksLeft === 0) {
            u.drained = true; u.undrained = 0; timers.clear(deadline);
            u.code = u.sent ? 'OK' : 'SEND_ERR';
          }
          finish();
        });
      });
    });
    if (!chunks.length) { u.code = 'NO_TARGETS'; timers.clear(deadline); }
  }
  begun = true;
  settle();
}

/* ------------------------------------------------------------------ HTTP to the NAS (private addresses only) */

/** GET or POST with a timeout and a size cap, no redirects: done(code, status, body). code is null on success. */
function fetchText(target, method, headers, body, maxBytes, opts, done) {
  var finished = false;
  function finish(code, status, text) { if (finished) return; finished = true; done(code, status, text); }
  var u = urlMod.parse(target);
  if (u.protocol !== 'http:' || u.auth || !hostAllowed(u.hostname, opts)) { finish('URL_REFUSED'); return; }
  var req;
  try {
    req = http.request({ host: u.hostname, port: u.port || 80, path: u.path, method: method, headers: headers || {}, agent: false }, function (res) {
      var chunks = [], size = 0;
      res.on('data', function (c) {
        size += c.length;
        if (size > maxBytes) { try { req.abort(); } catch (e) { /* gone */ } finish('TOO_LARGE', res.statusCode); return; }
        chunks.push(c);
      });
      res.on('end', function () { finish(res.statusCode === 200 ? null : 'HTTP_' + res.statusCode, res.statusCode, Buffer.concat(chunks).toString('utf8')); });
      res.on('error', function () { finish('READ_ERR', res.statusCode); });
    });
    req.setTimeout((opts && opts.httpMs) || LIMITS.httpMs, function () { try { req.abort(); } catch (e) { /* gone */ } finish('TIMEOUT'); });
    req.on('error', function (e) { finish(e && e.code ? 'NET_' + e.code : 'NET_ERR'); });
    req.end(body || undefined);
  } catch (e) { finish('NET_ERR'); }
}

/** Reads every candidate's description; keeps MediaServers with a ContentDirectory, one per UDN. */
function validate(candidates, opts, done) {
  var servers = [], rejected = [], byUdn = {};
  var list = candidates.slice(0, LIMITS.maxCandidates);
  var i = 0, active = 0;
  if (!list.length) { done(servers, rejected); return; }
  function next() {
    while (active < 4 && i < list.length) one(list[i++]);
    if (active === 0 && i >= list.length) done(servers, rejected);
  }
  function one(c) {
    var check = checkLocation(c.location, c.from, opts);
    if (!check.ok) { rejected.push({ code: check.code, methods: c.methods }); return; }
    active++;
    fetchText(check.url, 'GET', {}, null, LIMITS.descriptionBytes, opts, function (code, status, xml) {
      active--;
      if (code) rejected.push({ code: 'DESC_' + code, methods: c.methods });
      else {
        var d = parseDescription(xml, check.url, opts);
        if (!d.ok) rejected.push({ code: d.code, methods: c.methods });
        else {
          var key = d.udn || check.url;
          var s = byUdn[key];
          if (s) c.methods.forEach(function (m) { if (s.foundBy.indexOf(m) === -1) s.foundBy.push(m); });
          else if (servers.length < LIMITS.maxServers) {
            s = { index: servers.length, udn: d.udn, name: d.name, model: d.model, foundBy: c.methods.slice(), controlUrl: d.controlUrl };
            byUdn[key] = s; servers.push(s);
          }
        }
      }
      next();
    });
  }
  next();
}

function browse(server, objectId, opts, done) {
  fetchText(server.controlUrl, 'POST', { 'Content-Type': 'text/xml; charset="utf-8"', SOAPACTION: '"' + CDS_TYPE + '#Browse"' },
    browseBody(objectId, 0, LIMITS.browseCount), LIMITS.browseBytes, opts, function (code, status, xml) {
      if (code) { done({ ok: false, code: 'BROWSE_' + code }); return; }
      done(parseBrowse(xml));
    });
}

/* ------------------------------------------------------------------ the localhost API */

/** The service's state: the token, the passive listener and the servers validated in the latest run. */
function State(options) {
  var o = options || {};
  this.token = o.token || '';
  this.passive = o.passive || null;
  this.servers = [];
  this.searching = false;
  this.started = Date.now();
  this.opts = o.opts || {};
  this.ifaces = o.ifaces || function () { return privateInterfaces(os.networkInterfaces()); };
  this.search = o.search || search;
}

function send(res, status, body) {
  var json = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' });
  res.end(json);
}

/** The request handler. Every route needs the token (query t); without it nothing is done. */
function handler(state) {
  return function (req, res) {
    var u = urlMod.parse(req.url, true);
    var q = u.query || {};
    if (req.method !== 'GET') { send(res, 405, { ok: false, code: 'METHOD_NOT_ALLOWED' }); return; }
    if (!state.token) { send(res, 403, { ok: false, code: 'TOKEN_NOT_RECEIVED' }); return; }
    if (!tokenOk(state.token, typeof q.t === 'string' ? q.t : '')) { send(res, 403, { ok: false, code: 'TOKEN_MISMATCH' }); return; }
    var addr = req.socket && req.socket.localAddress;
    if (u.pathname === '/ping') {
      send(res, 200, { ok: true, code: 'PONG', version: VERSION, node: process.version, udp: !!dgram, boundTo: addr || '', uptimeMs: Date.now() - state.started,
        notify: state.passive ? state.passive.code : 'NOT_RUN' });
      return;
    }
    if (u.pathname === '/discover') {
      if (state.searching) { send(res, 409, { ok: false, code: 'BUSY' }); return; }
      var ifaces = state.ifaces();
      if (!ifaces.length) { send(res, 200, { ok: false, code: 'NO_IFACE' }); return; }
      if (state.passive && !state.passive.sock && state.passive.code !== 'LISTENING') state.passive.start(ifaces);
      state.searching = true;
      var t0 = Date.now();
      var o = {};
      Object.keys(state.opts).forEach(function (k) { o[k] = state.opts[k]; });
      o.iface = ifaces[0]; o.passive = state.passive;
      state.search(o, function (report) {
        validate(report.candidates || [], state.opts, function (servers, rejected) {
          state.searching = false;
          state.servers = servers;
          log('DISCOVER_DONE', servers.length + ' valid, ' + rejected.length + ' rejected');
          send(res, 200, { ok: true, code: report.code, ms: Date.now() - t0, interfaces: ifaces.length,
            sweepHosts: report.methods.uni.targets, methods: report.methods, candidates: (report.candidates || []).length,
            servers: servers.map(function (s) { return { index: s.index, name: s.name, model: s.model, udn: s.udn, foundBy: s.foundBy }; }),
            rejected: rejected });
        });
      });
      return;
    }
    if (u.pathname === '/browse') {
      var s = state.servers[parseInt(q.s, 10)];
      var id = typeof q.id === 'string' ? q.id : '0';
      if (!s) { send(res, 404, { ok: false, code: 'NO_SUCH_SERVER' }); return; }
      if (!id || id.length > 256) { send(res, 400, { ok: false, code: 'BAD_OBJECT_ID' }); return; }
      browse(s, id, state.opts, function (r) { send(res, 200, r); });
      return;
    }
    if (u.pathname === '/log') { send(res, 200, { ok: true, lines: LOG.slice() }); return; }
    if (u.pathname === '/stop') { send(res, 200, { ok: true, code: 'STOPPING' }); setTimeout(stop, 50); return; }
    send(res, 404, { ok: false, code: 'NOT_FOUND' });
  };
}

/* ------------------------------------------------------------------ lifecycle */

var running = null;   // { server, state }

/** Starts the localhost API (once). options.passive === false leaves the NOTIFY listener out (tests). */
function start(port, done, options) {
  if (running) { if (done) done(null, running); return; }
  var listen = !(options && options.passive === false);
  var state = new State({ passive: listen ? new Passive() : null });
  var server = http.createServer(handler(state));
  running = { server: server, state: state };
  server.on('error', function (e) { log('HTTP_ERR', e && e.code); });
  server.listen(port === undefined ? PORT : port, LOOPBACK, function () {
    log('LISTENING', LOOPBACK + ':' + server.address().port + ' v' + VERSION + ' node ' + process.version);
    if (state.passive) state.passive.start(privateInterfaces(os.networkInterfaces()));
    if (done) done(null, running);
  });
}
function stop() {
  if (!running) return;
  try { running.server.close(); } catch (e) { /* closed */ }
  if (running.state.passive) running.state.passive.stop();
  running = null;
  log('STOPPED');
  try { if (typeof tizen !== 'undefined') tizen.application.getCurrentApplication().exit(); } catch (e) { /* not on a TV */ }
}
function takeToken() {
  try {
    var t = tokenFromAppControl(tizen.application.getCurrentApplication().getRequestedAppControl());
    if (t && running) { running.state.token = t; log('TOKEN_RECEIVED'); } else if (!t) log('TOKEN_MISSING');
  } catch (e) { log('TOKEN_READ_ERR', e && e.message); }
}

module.exports.onStart = function () { start(); };
module.exports.onRequest = function () { start(undefined, takeToken); };
module.exports.onStop = function () { stop(); };
module.exports.onExit = function () { stop(); };

// For the unit tests only; the TV's runtime uses the callbacks above.
module.exports.internals = {
  VERSION: VERSION, PORT: PORT, LOOPBACK: LOOPBACK, SSDP_GROUP: SSDP_GROUP, LIMITS: LIMITS, TOKEN_KEY: TOKEN_KEY,
  parseIPv4: parseIPv4, isPrivateIPv4: isPrivateIPv4, hostAllowed: hostAllowed, prefixOf: prefixOf, sweepTargets: sweepTargets,
  directedBroadcast: directedBroadcast, privateInterfaces: privateInterfaces, searchMessage: searchMessage, parseSsdp: parseSsdp,
  aboutMediaServer: aboutMediaServer, checkLocation: checkLocation, parseDescription: parseDescription, browseBody: browseBody,
  parseBrowse: parseBrowse, tokenOk: tokenOk, tokenFromAppControl: tokenFromAppControl, sendAll: sendAll, Passive: Passive,
  search: search, fetchText: fetchText, validate: validate, browse: browse, State: State, handler: handler, bytes: bytes, log: LOG,
  start: start, stop: stop, running: function () { return running; }
};

// On the TV only: a stray exception is logged instead of ending the service. Never under the test runner.
if (typeof tizen !== 'undefined') process.on('uncaughtException', function (e) { log('UNCAUGHT', e && e.message); });
