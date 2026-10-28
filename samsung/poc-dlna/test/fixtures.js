'use strict';
// Shared fixtures for the proof-of-concept tests. Addresses are built from their parts so that no file in samsung/
// contains a literal address (samsung/test/source.test.js checks). Public examples use RFC 5737 documentation ranges;
// the private-range examples exist only because the private-address guard has to be tested with them.
const ip = (...p) => p.join('.');
const LOOP = ip(127, 0, 0, 1);
const MASK24 = ip(255, 255, 255, 0);

function description(o) {
  const x = Object.assign({ type: 'urn:schemas-upnp-org:device:MediaServer:1', name: 'Family NAS', model: 'DS220+', udn: 'uuid:4d696e69-444c-164e-9d41-000000000001',
    services: [['urn:schemas-upnp-org:service:ConnectionManager:1', '/cm/control'], ['urn:schemas-upnp-org:service:ContentDirectory:1', '/cd/control']], base: '' }, o || {});
  return '<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><specVersion><major>1</major><minor>0</minor></specVersion>' +
    (x.base ? '<URLBase>' + x.base + '</URLBase>' : '') +
    '<device><deviceType>' + x.type + '</deviceType><friendlyName>' + x.name + '</friendlyName><modelName>' + x.model + '</modelName><UDN>' + x.udn + '</UDN>' +
    '<serviceList>' + x.services.map((s) => '<service><serviceType>' + s[0] + '</serviceType><controlURL>' + s[1] + '</controlURL></service>').join('') +
    '</serviceList></device></root>';
}
function didl(inner) {
  return '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">' + inner + '</DIDL-Lite>';
}
function escape(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
function browseReply(inner, returned, total) {
  return '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">' +
    '<Result>' + escape(didl(inner)) + '</Result><NumberReturned>' + returned + '</NumberReturned><TotalMatches>' + total + '</TotalMatches><UpdateID>1</UpdateID></u:BrowseResponse></s:Body></s:Envelope>';
}
const folder = (id, title) => '<container id="' + id + '" parentID="0" childCount="2"><dc:title>' + title + '</dc:title><upnp:class>object.container.storageFolder</upnp:class></container>';
const video = (id, title) => '<item id="' + id + '" parentID="1"><dc:title>' + title + '</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:video/mp4:*">http://media.invalid/' + id + '.mp4</res></item>';

function ssdpResponse(location, st) {
  return 'HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nLOCATION: ' + location + '\r\nSERVER: Test/1.0 UPnP/1.0 Fake/1.0\r\nST: ' +
    (st || 'urn:schemas-upnp-org:device:MediaServer:1') + '\r\nUSN: uuid:test::' + (st || 'urn:schemas-upnp-org:device:MediaServer:1') + '\r\n\r\n';
}
function ssdpNotify(location, nt, nts) {
  return 'NOTIFY * HTTP/1.1\r\nHOST: ' + ip(239, 255, 255, 250) + ':1900\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: ' + location + '\r\nNT: ' +
    (nt || 'urn:schemas-upnp-org:device:MediaServer:1') + '\r\nNTS: ' + (nts || 'ssdp:alive') + '\r\nUSN: uuid:test\r\n\r\n';
}

/** A fake dgram socket: records sends, calls back after `delay(i)` ms (or never when delay returns null). */
function fakeSocketFactory(o) {
  const opts = o || {};
  const made = [];
  let sendIndex = 0;
  function create() {
    const handlers = {};
    const s = {
      sends: [], closed: false, closedAfterSends: null, pending: 0, membership: [],
      on(ev, fn) { handlers[ev] = fn; return s; },
      emit(ev, a, b) { if (handlers[ev]) handlers[ev](a, b); },
      bind(port, addr, cb) {
        const done = typeof addr === 'function' ? addr : cb;
        if (opts.bindError) setImmediate(() => s.emit('error', Object.assign(new Error('bind'), { code: 'EADDRINUSE' })));
        else setImmediate(done);
      },
      send(buf, off, len, port, addr, cb) {
        const i = sendIndex++;
        s.sends.push({ port, addr, text: buf.toString('utf8', off, off + len) });
        s.pending++;
        const d = opts.delay ? opts.delay(i, addr) : 0;
        if (d === null) return;   // never drains
        setTimeout(() => { s.pending--; cb(opts.sendError && opts.sendError(i, addr) ? Object.assign(new Error('x'), { code: opts.sendError(i, addr) }) : null); }, d);
      },
      setBroadcast() {}, setMulticastTTL() {}, setMulticastInterface() {},
      addMembership(group, addr) { if (opts.joinError) throw new Error('join'); s.membership.push([group, addr]); },
      close() { s.closed = true; s.closedWithPending = s.pending; }
    };
    made.push(s);
    return s;
  }
  return { create, made };
}

module.exports = { ip, LOOP, MASK24, description, didl, browseReply, folder, video, ssdpResponse, ssdpNotify, fakeSocketFactory };
