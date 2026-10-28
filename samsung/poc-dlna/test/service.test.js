'use strict';
// The service: the token, the localhost-only API, the send-queue drain, the four discovery methods (fake sockets and
// real loopback UDP), the passive NOTIFY listener, validation and Browse against a fake media server on loopback.
const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const dgram = require('node:dgram');
const S = require('../service/service.js').internals;
const F = require('./fixtures.js');
const { ip, LOOP, MASK24 } = F;

const TOKEN = ['01234567', '89abcdef'].join('').repeat(2);   // a test value, built so it doesn't look like a stored secret
const FAST = { repeatMs: 30, windowMs: 200, sweepChunk: 64, sweepDeadlineMs: 2000, httpMs: 1500 };

function getJson(port, path) {
  return new Promise((resolve, reject) => {
    const post = path.startsWith('POST ');
    const req = http.request({ host: LOOP, port, path: post ? path.slice(5) : path, method: post ? 'POST' : 'GET' }, (res) => {
      let body = ''; res.on('data', (c) => { body += c; });
      res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(body) }));
    });
    req.on('error', reject); req.end();
  });
}
function listen(server) { return new Promise((r) => server.listen(0, LOOP, () => r(server.address().port))); }

test('the token: constant-time, exact, and a missing or short token never matches', () => {
  assert.ok(S.tokenOk(TOKEN, TOKEN));
  assert.ok(!S.tokenOk(TOKEN, TOKEN.slice(0, -1) + '0'));
  assert.ok(!S.tokenOk(TOKEN, TOKEN + '0'));
  assert.ok(!S.tokenOk(TOKEN, ''));
  assert.ok(!S.tokenOk(TOKEN, undefined));
  assert.ok(!S.tokenOk('', ''), 'no token received: nothing matches');
  assert.ok(!S.tokenOk('short', 'short'), 'a token under 16 characters is refused');
});

test('the token is read from the launch ApplicationControl data, in any order', () => {
  const ctl = (data) => ({ appControl: { operation: 'http://tizen.org/appcontrol/operation/pick', data } });
  assert.strictEqual(S.tokenFromAppControl(ctl([{ key: 'other', value: ['x'] }, { key: 'utv-token', value: [TOKEN] }])), TOKEN);
  assert.strictEqual(S.tokenFromAppControl(ctl([{ key: 'utv-token', value: ['not hex!'] }])), '');
  assert.strictEqual(S.tokenFromAppControl(ctl([])), '');
  assert.strictEqual(S.tokenFromAppControl(null), '');
});

test('the API refuses every request without this launch\'s token, and answers on loopback with it', async () => {
  const state = new S.State({ token: TOKEN, ifaces: () => [] });
  const server = http.createServer(S.handler(state));
  const port = await listen(server);
  try {
    assert.deepStrictEqual((await getJson(port, '/ping')).body, { ok: false, code: 'TOKEN_MISMATCH' });
    assert.strictEqual((await getJson(port, '/ping?t=' + 'f'.repeat(32))).status, 403);
    assert.strictEqual((await getJson(port, '/discover')).status, 403, 'discovery needs the token too');
    assert.strictEqual((await getJson(port, '/browse?s=0&id=0')).status, 403);
    const ok = await getJson(port, '/ping?t=' + TOKEN);
    assert.strictEqual(ok.status, 200); assert.strictEqual(ok.body.boundTo, LOOP); assert.strictEqual(ok.body.udp, true);
    assert.strictEqual((await getJson(port, 'POST /ping?t=' + TOKEN)).status, 405);
    assert.deepStrictEqual((await getJson(port, '/browse?s=0&id=0&t=' + TOKEN)).body, { ok: false, code: 'NO_SUCH_SERVER' });
    assert.deepStrictEqual((await getJson(port, '/discover?t=' + TOKEN)).body, { ok: false, code: 'NO_IFACE' });
    state.token = '';
    assert.deepStrictEqual((await getJson(port, '/ping?t=' + TOKEN)).body, { ok: false, code: 'TOKEN_NOT_RECEIVED' });
  } finally { server.close(); }
});

test('the service listens on 127.0.0.1 only', async () => {
  await new Promise((resolve) => S.start(0, resolve, { passive: false }));
  try {
    const a = S.running().server.address();
    assert.strictEqual(a.address, LOOP);
  } finally { S.stop(); }
  assert.strictEqual(S.running(), null);
});

test('send-queue drain: done only after every send has called back, errors counted', async () => {
  const sockets = F.fakeSocketFactory({ delay: (i) => (i * 7) % 40, sendError: (i) => (i % 50 === 49 ? 'ECANCELED' : null) });
  const sock = sockets.create();
  const targets = Array.from({ length: 200 }, (x, i) => ip(10, 0, 0, i + 1));
  const r = await new Promise((resolve) => S.sendAll(sock, S.bytes('M'), targets, 1900, resolve));
  assert.strictEqual(sock.pending, 0, 'nothing left in the queue when done is called');
  assert.strictEqual(r.sent + r.errors, 200);
  assert.strictEqual(r.errors, 4); assert.deepStrictEqual(r.codes, { ECANCELED: 4 });
  assert.deepStrictEqual(await new Promise((resolve) => S.sendAll(sock, S.bytes('M'), [], 1900, resolve)), { sent: 0, errors: 0, codes: {} });
});

test('the unicast sweep: the /24 in chunks of 64, each socket closed only after its queue drained', async () => {
  const sockets = F.fakeSocketFactory({ delay: (i, addr) => (addr.endsWith('.200') ? 300 : 5) });
  const report = await new Promise((resolve) => S.search({ iface: { address: ip(10, 0, 0, 37), netmask: MASK24 }, createSocket: sockets.create, limits: FAST }, resolve));
  const u = report.methods.uni;
  assert.strictEqual(u.targets, 253); assert.strictEqual(u.sockets, 4); assert.strictEqual(u.sent, 253);
  assert.strictEqual(u.drained, true); assert.strictEqual(u.undrained, 0); assert.strictEqual(u.code, 'OK');
  const uniSockets = sockets.made.filter((s) => s.sends.length && s.sends[0].addr !== ip(239, 255, 255, 250) && !s.sends[0].addr.endsWith('.255'));
  assert.strictEqual(uniSockets.length, 4);
  for (const s of uniSockets) { assert.ok(s.sends.length <= 64); assert.strictEqual(s.closedWithPending, 0, 'closed with an empty queue'); }
  assert.ok(!uniSockets.some((s) => s.sends.some((x) => x.addr === ip(10, 0, 0, 37))), 'never the TV itself');
  const mc = sockets.made.find((s) => s.sends.length && s.sends[0].addr === ip(239, 255, 255, 250));
  assert.strictEqual(mc.sends.length, 2, 'multicast sent twice');
  assert.strictEqual(report.methods.mc.code, 'OK'); assert.strictEqual(report.methods.bc.code, 'OK');
  assert.ok(sockets.made.some((s) => s.sends.length && s.sends[0].addr === ip(10, 0, 0, 255)), 'directed broadcast to the subnet');
});

test('a sweep whose queue never drains is reported, and the run still ends', async () => {
  const sockets = F.fakeSocketFactory({ delay: (i, addr) => (addr === ip(10, 0, 0, 100) ? null : 1) });
  const t0 = Date.now();
  const report = await new Promise((resolve) => S.search({ iface: { address: ip(10, 0, 0, 37), netmask: MASK24 }, createSocket: sockets.create, limits: FAST }, resolve));
  assert.strictEqual(report.methods.uni.code, 'NOT_DRAINED');
  assert.strictEqual(report.methods.uni.undrained, 64, 'the stuck chunk');
  assert.ok(Date.now() - t0 < FAST.sweepDeadlineMs + FAST.windowMs + FAST.repeatMs + 500, 'bounded by the hard stop');
});

test('a socket that fails to bind is reported per method and never hangs the run', async () => {
  const sockets = F.fakeSocketFactory({ bindError: true });
  const report = await new Promise((resolve) => S.search({ iface: { address: ip(10, 0, 0, 37), netmask: MASK24 }, createSocket: sockets.create, limits: FAST }, resolve));
  for (const m of ['mc', 'bc', 'uni']) assert.strictEqual(report.methods[m].code, 'BIND_ERR', m);
  assert.deepStrictEqual(report.candidates, []);
  const none = await new Promise((resolve) => S.search({ iface: null, limits: FAST }, resolve));
  assert.strictEqual(none.code, 'NO_IFACE');
});

test('the passive listener joins the SSDP group and remembers media-server NOTIFYs only', async () => {
  const sockets = F.fakeSocketFactory();
  let t = 1000;
  const p = new S.Passive({ createSocket: sockets.create, now: () => t });
  p.start([{ address: ip(10, 0, 0, 37) }]);
  await new Promise((r) => setImmediate(r));
  assert.strictEqual(p.code, 'LISTENING');
  assert.deepStrictEqual(sockets.made[0].membership, [[ip(239, 255, 255, 250), ip(10, 0, 0, 37)]]);
  const nas = ip(10, 0, 0, 20);
  sockets.made[0].emit('message', Buffer.from(F.ssdpNotify('http://' + nas + ':8200/rootDesc.xml')), { address: nas });
  sockets.made[0].emit('message', Buffer.from(F.ssdpNotify('http://' + ip(10, 0, 0, 30) + '/p.xml', 'urn:schemas-upnp-org:device:Printer:1')), { address: ip(10, 0, 0, 30) });
  sockets.made[0].emit('message', Buffer.from(F.ssdpNotify('http://' + ip(10, 0, 0, 31) + '/x.xml', undefined, 'ssdp:byebye')), { address: ip(10, 0, 0, 31) });
  sockets.made[0].emit('message', Buffer.from(S.searchMessage()), { address: ip(10, 0, 0, 37) });
  assert.deepStrictEqual(Object.keys(p.heard), ['http://' + nas + ':8200/rootDesc.xml']);
  assert.strictEqual(p.heard['http://' + nas + ':8200/rootDesc.xml'].from, nas);
  p.stop(); assert.ok(sockets.made[0].closed);
  const busy = new S.Passive({ createSocket: F.fakeSocketFactory({ bindError: true }).create });
  busy.start([{ address: ip(10, 0, 0, 37) }]);
  await new Promise((r) => setImmediate(r));
  assert.strictEqual(busy.code, 'BIND_1900_ERR');
  const nojoin = new S.Passive({ createSocket: F.fakeSocketFactory({ joinError: true }).create });
  nojoin.start([{ address: ip(10, 0, 0, 37) }]);
  await new Promise((r) => setImmediate(r));
  assert.strictEqual(nojoin.code, 'JOIN_ERR');
});

test('validation sets aside answers from the wrong host or a public address without requesting them', async () => {
  const servers = await new Promise((resolve) => S.validate([
    { location: 'http://' + ip(10, 0, 0, 20) + ':8200/rootDesc.xml', from: ip(10, 0, 0, 21), methods: ['mc'] },
    { location: 'http://' + ip(198, 51, 100, 7) + '/d.xml', from: ip(198, 51, 100, 7), methods: ['notify'] }
  ], {}, (s, rejected) => resolve({ s, rejected })));
  assert.deepStrictEqual(servers.s, []);
  assert.deepStrictEqual(servers.rejected.map((r) => r.code), ['LOC_HOST_MISMATCH', 'LOC_NOT_PRIVATE']);
});

test('HTTP to the NAS: no public address, no redirects, a size cap and a timeout', async () => {
  const refused = await new Promise((r) => S.fetchText('http://' + ip(203, 0, 113, 9) + '/x', 'GET', {}, null, 100, {}, (c) => r(c)));
  assert.strictEqual(refused, 'URL_REFUSED');
  const server = http.createServer((req, res) => {
    if (req.url === '/redirect') { res.writeHead(302, { Location: 'http://' + ip(203, 0, 113, 9) + '/' }); res.end(); return; }
    if (req.url === '/big') { res.writeHead(200); res.end('x'.repeat(5000)); return; }
    if (req.url === '/slow') return;   // never answers
    res.writeHead(200); res.end('fine');
  });
  const port = await listen(server);
  const base = 'http://' + LOOP + ':' + port;
  const lo = { allowLoopback: true, httpMs: 300 };
  const call = (p, max) => new Promise((r) => S.fetchText(base + p, 'GET', {}, null, max || 1000, lo, (c, s, t) => r([c, t])));
  try {
    assert.deepStrictEqual(await call('/ok'), [null, 'fine']);
    assert.strictEqual((await call('/redirect'))[0], 'HTTP_302');
    assert.strictEqual((await call('/big', 1000))[0], 'TOO_LARGE');
    assert.strictEqual((await call('/slow'))[0], 'TIMEOUT');
    assert.strictEqual(await new Promise((r) => S.fetchText(base + '/ok', 'GET', {}, null, 100, {}, (c) => r(c))), 'URL_REFUSED', 'loopback refused outside tests');
  } finally { server.closeAllConnections && server.closeAllConnections(); server.close(); }
});

test('end to end on loopback: a real UDP responder answers the sweep; the description is validated; Browse lists folders', async () => {
  // A fake media server: SSDP on loopback UDP, description and ContentDirectory on loopback HTTP.
  const httpServer = http.createServer((req, res) => {
    if (req.url === '/desc.xml') { res.writeHead(200, { 'Content-Type': 'text/xml' }); res.end(F.description()); return; }
    if (req.url === '/cd/control' && req.method === 'POST') {
      let body = ''; req.on('data', (c) => { body += c; });
      req.on('end', () => {
        const id = /<ObjectID>([^<]*)<\/ObjectID>/.exec(body)[1];
        res.writeHead(200, { 'Content-Type': 'text/xml' });
        res.end(id === '0' ? F.browseReply(F.folder('1', 'Films') + F.folder('2', 'TV') + F.folder('3', 'Kids'), 3, 3) : F.browseReply(F.folder('11', 'Drama') + F.video('v1', 'Clip'), 2, 2));
      });
      return;
    }
    res.writeHead(404); res.end();
  });
  const httpPort = await listen(httpServer);
  const responder = dgram.createSocket('udp4');
  let searches = 0;
  responder.on('message', (msg, rinfo) => {
    if (!/^M-SEARCH/.test(String(msg))) return;
    searches++;
    const reply = Buffer.from(F.ssdpResponse('http://' + LOOP + ':' + httpPort + '/desc.xml'));
    responder.send(reply, 0, reply.length, rinfo.port, rinfo.address);
  });
  const ssdpPort = await new Promise((r) => responder.bind(0, LOOP, () => r(responder.address().port)));
  const passive = { code: 'LISTENING', since: Date.now(), heard: {}, sock: {}, start() {} };
  const state = new S.State({ token: TOKEN, passive, ifaces: () => [{ address: ip(127, 0, 0, 9), netmask: MASK24 }],
    opts: { allowLoopback: true, ssdpPort, limits: FAST, httpMs: 1500 } });
  const api = http.createServer(S.handler(state));
  const apiPort = await listen(api);
  try {
    const d = await getJson(apiPort, '/discover?t=' + TOKEN);
    assert.strictEqual(d.status, 200); assert.strictEqual(d.body.ok, true);
    const u = d.body.methods.uni;
    assert.strictEqual(u.targets, 253); assert.strictEqual(u.drained, true); assert.strictEqual(u.sent, 253);
    assert.ok(u.answers >= 1, 'the unicast sweep reached the responder');
    assert.ok(searches >= 1);
    assert.strictEqual(d.body.servers.length, 1);
    const s = d.body.servers[0];
    assert.deepStrictEqual([s.name, s.model, s.udn], ['Family NAS', 'DS220+', 'uuid:4d696e69-444c-164e-9d41-000000000001']);
    assert.ok(s.foundBy.indexOf('uni') !== -1);
    assert.ok(!('controlUrl' in s), 'the control address stays inside the service');
    assert.ok(JSON.stringify(d.body).indexOf(ip(127, 0, 0, 9)) === -1, 'no address of the TV in the report');
    const top = await getJson(apiPort, '/browse?s=0&id=0&t=' + TOKEN);
    assert.deepStrictEqual(top.body.containers.map((c) => c.title), ['Films', 'TV', 'Kids']);
    const inner = await getJson(apiPort, '/browse?s=0&id=1&t=' + TOKEN);
    assert.strictEqual(inner.body.containers.length, 1); assert.strictEqual(inner.body.items, 1);
    assert.strictEqual((await getJson(apiPort, '/browse?s=1&id=0&t=' + TOKEN)).body.code, 'NO_SUCH_SERVER', 'only servers validated in this run');
  } finally { responder.close(); api.close(); httpServer.close(); }
});
