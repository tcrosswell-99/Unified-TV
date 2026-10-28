'use strict';
// SSDP parsing, the M-SEARCH message, the private-address guard, the LOCATION check and the /24 sweep plan.
const test = require('node:test');
const assert = require('node:assert');
const S = require('../service/service.js').internals;
const { ip, LOOP, MASK24, ssdpResponse, ssdpNotify } = require('./fixtures.js');

test('SSDP replies and notifications are parsed; anything else is ignored', () => {
  const r = S.parseSsdp(ssdpResponse('http://' + ip(10, 0, 0, 20) + ':50001/desc/device.xml'));
  assert.strictEqual(r.kind, 'response');
  assert.strictEqual(r.headers.location, 'http://' + ip(10, 0, 0, 20) + ':50001/desc/device.xml');
  assert.ok(S.aboutMediaServer(r));
  const n = S.parseSsdp(ssdpNotify('http://' + ip(10, 0, 0, 21) + ':8200/rootDesc.xml', 'urn:schemas-upnp-org:service:ContentDirectory:1'));
  assert.strictEqual(n.kind, 'notify'); assert.ok(S.aboutMediaServer(n));
  assert.ok(!S.aboutMediaServer(S.parseSsdp(ssdpNotify('http://' + ip(10, 0, 0, 22) + '/d.xml', 'urn:schemas-upnp-org:device:Printer:1'))));
  assert.strictEqual(S.parseSsdp(S.searchMessage()), null, 'our own M-SEARCH is not an answer');
  assert.strictEqual(S.parseSsdp('HTTP/1.1 404 Not Found\r\n\r\n'), null);
  assert.strictEqual(S.parseSsdp(''), null);
  assert.strictEqual(S.parseSsdp('garbage'), null);
});

test('the M-SEARCH asks for media servers on the SSDP group', () => {
  const m = S.searchMessage();
  assert.match(m, /^M-SEARCH \* HTTP\/1\.1\r\n/);
  assert.ok(m.indexOf('HOST: ' + ip(239, 255, 255, 250) + ':1900\r\n') !== -1);
  assert.match(m, /MAN: "ssdp:discover"\r\n/); assert.match(m, /MX: 2\r\n/);
  assert.match(m, /ST: urn:schemas-upnp-org:device:MediaServer:1\r\n/);
  assert.match(m, /\r\n\r\n$/);
});

test('only private IPv4 addresses pass the guard; loopback only when a test asks', () => {
  for (const a of [ip(10, 0, 0, 1), ip(10, 255, 255, 254), ip(172, 16, 0, 1), ip(172, 31, 255, 254), ip(192, 168, 0, 1), ip(192, 168, 255, 254)]) assert.ok(S.isPrivateIPv4(a), a);
  for (const a of [ip(172, 15, 255, 255), ip(172, 32, 0, 1), ip(192, 169, 0, 1), ip(192, 0, 2, 10), ip(198, 51, 100, 7), ip(203, 0, 113, 9), ip(8, 8, 8, 8),
    ip(169, 254, 1, 1), ip(100, 64, 0, 1), ip(0, 0, 0, 0), ip(255, 255, 255, 255), LOOP, ip(10, 0, 0, 256), 'nas.local', '', null]) {
    assert.ok(!S.isPrivateIPv4(a), String(a));
  }
  assert.ok(!S.hostAllowed(LOOP)); assert.ok(S.hostAllowed(LOOP, { allowLoopback: true }));
  assert.ok(!S.hostAllowed(ip(192, 0, 2, 10), { allowLoopback: true }));
});

test('a LOCATION is read only on a private address and only from the host that answered', () => {
  const nas = ip(192, 168, 1, 20);
  assert.deepStrictEqual(S.checkLocation('http://' + nas + ':50001/desc/device.xml', nas), { ok: true, url: 'http://' + nas + ':50001/desc/device.xml', host: nas });
  assert.strictEqual(S.checkLocation('http://' + nas + ':50001/d.xml', ip(192, 168, 1, 21)).code, 'LOC_HOST_MISMATCH');
  assert.strictEqual(S.checkLocation('http://' + ip(203, 0, 113, 9) + '/d.xml', ip(203, 0, 113, 9)).code, 'LOC_NOT_PRIVATE');
  assert.strictEqual(S.checkLocation('http://nas.local/d.xml', nas).code, 'LOC_NOT_IPV4');
  assert.strictEqual(S.checkLocation('https://' + nas + '/d.xml', nas).code, 'LOC_BAD_URL');
  assert.strictEqual(S.checkLocation('http://user:pw@' + nas + '/d.xml', nas).code, 'LOC_CREDENTIALS');
  assert.strictEqual(S.checkLocation('file:///etc/passwd', nas).code, 'LOC_BAD_URL');
  assert.strictEqual(S.checkLocation('http://' + LOOP + ':18731/ping', LOOP).code, 'LOC_NOT_PRIVATE', 'never the service itself');
});

test('the unicast sweep covers the TV\'s own /24 at most, never the TV itself', () => {
  const tv = ip(192, 168, 1, 37);
  const t = S.sweepTargets(tv, MASK24);
  assert.strictEqual(t.length, 253);
  assert.ok(t.indexOf(tv) === -1, 'not the TV');
  assert.ok(t.indexOf(ip(192, 168, 1, 0)) === -1 && t.indexOf(ip(192, 168, 1, 255)) === -1, 'no network or broadcast address');
  assert.strictEqual(t[0], ip(192, 168, 1, 1)); assert.strictEqual(t[252], ip(192, 168, 1, 254));
  const wide = S.sweepTargets(ip(10, 0, 5, 9), ip(255, 255, 0, 0));
  assert.strictEqual(wide.length, 253, 'a /16 is still swept as the /24 only');
  assert.ok(wide.every((a) => a.indexOf(ip(10, 0, 5, 0).slice(0, -1)) === 0));
  assert.strictEqual(S.sweepTargets(ip(10, 0, 0, 70), ip(255, 255, 255, 192)).length, 61, 'a /26 narrows it');
  assert.strictEqual(S.sweepTargets(ip(10, 0, 0, 70), 'nonsense').length, 253);
  assert.deepStrictEqual(S.sweepTargets('not an address', MASK24), []);
  assert.strictEqual(S.directedBroadcast(tv, MASK24), ip(192, 168, 1, 255));
  assert.strictEqual(S.directedBroadcast(ip(10, 0, 5, 9), ip(255, 255, 0, 0)), ip(10, 0, 255, 255));
  assert.strictEqual(S.directedBroadcast(tv, ip(255, 0, 255, 0)), null, 'a non-contiguous mask is unusable');
});

test('only private, external IPv4 interfaces are used', () => {
  const list = S.privateInterfaces({
    lo: [{ address: LOOP, netmask: ip(255, 0, 0, 0), family: 'IPv4', internal: true }],
    wlan0: [{ address: ip(192, 168, 1, 37), netmask: MASK24, family: 4, internal: false }, { address: 'fe80::1', family: 'IPv6', internal: false }],
    eth9: [{ address: ip(203, 0, 113, 5), netmask: MASK24, family: 'IPv4', internal: false }]
  });
  assert.deepStrictEqual(list, [{ name: 'wlan0', address: ip(192, 168, 1, 37), netmask: MASK24 }]);
});
