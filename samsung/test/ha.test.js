'use strict';
// The Home Assistant client (spec 10.4, 11, 13), against a fake WebSocket: the token goes only in the auth message,
// alarms are read on connect, a lost connection never clears an alarm, Ring waits during an emergency, plain HTTP needs
// consent for that exact host.
const test = require('node:test');
const assert = require('node:assert');
const Ha = require('../src/js/ha.js');
const A = require('../src/js/alerts.js');

// Documentation-only addresses (RFC 5737 and RFC 3849), built from parts so no address literal sits in the source.
const DOC_V4 = [192, 0, 2, 10].join('.');
const DOC_V6 = ['2001', 'db8', '', '10'].join(':');
const TOKEN = 'placeholder';

class FakeSocket {
  constructor(url) { this.url = url; this.sent = []; this.closed = false; FakeSocket.last = this; FakeSocket.all.push(this); }
  send(text) { this.sent.push(JSON.parse(text)); }
  close() { this.closed = true; }
  receive(value) { this.onmessage({ data: JSON.stringify(value) }); }
  drop() { this.onclose(); }
}
FakeSocket.all = [];

function client(extra) {
  const updates = []; const timers = [];
  let now = Date.parse('2026-10-25T19:42:05Z');
  const policy = new A.Policy();
  const c = new Ha.Client(Object.assign({
    url: 'wss://homeassistant.local:8123/api/websocket', token: TOKEN, WebSocket: FakeSocket, policy: policy,
    now: () => now, setTimer: (f, ms) => { timers.push({ f, ms }); return timers.length; }, clearTimer: () => {},
    onUpdate: (kind, detail) => updates.push([kind, detail]), alarms: true, reminders: true,
    door: 'binary_sensor.front_door_ding', person: '', room: 'frame'
  }, extra || {}));
  return { c, policy, updates, timers, setNow: (t) => { now = t; } };
}
function connected(h) {
  h.c.start();
  const ws = FakeSocket.last;
  ws.onopen();
  ws.receive({ type: 'auth_required' });
  ws.receive({ type: 'auth_ok' });
  return ws;
}
function stateEvent(entity, oldState, newState, deviceClass, timeFired) {
  return { type: 'event', event: { event_type: 'state_changed', time_fired: timeFired || '2026-10-25T19:42:04+00:00', data: {
    entity_id: entity,
    old_state: { state: oldState, attributes: { device_class: deviceClass } },
    new_state: { state: newState, last_changed: '2026-10-25T19:42:04+00:00', attributes: { device_class: deviceClass, friendly_name: 'Kitchen' } } } } };
}

test('endpoints: host names, IPv4 and IPv6 are accepted; paths, user names and bad ports are refused', () => {
  assert.deepStrictEqual(Ha.endpoint({ host: 'homeassistant.local', port: 8123, https: true }), { ok: true, url: 'wss://homeassistant.local:8123/api/websocket', secure: true });
  assert.strictEqual(Ha.endpoint({ host: DOC_V4, port: 8123 }).url, 'wss://' + DOC_V4 + ':8123/api/websocket');
  assert.strictEqual(Ha.endpoint({ host: DOC_V6, port: 8123 }).url, 'wss://[' + DOC_V6 + ']:8123/api/websocket');
  assert.strictEqual(Ha.endpoint({ host: 'http://homeassistant.local', port: 8123 }).error, 'host');
  assert.strictEqual(Ha.endpoint({ host: 'user@homeassistant.local', port: 8123 }).error, 'host');
  assert.strictEqual(Ha.endpoint({ host: 'homeassistant.local/api', port: 8123 }).error, 'host');
  assert.strictEqual(Ha.endpoint({ host: [999, 1, 1, 1].join('.'), port: 8123 }).error, 'host');
  assert.strictEqual(Ha.endpoint({ host: 'homeassistant.local', port: 70000 }).error, 'port');
});

test('plain HTTP only with explicit consent for that very host', () => {
  assert.strictEqual(Ha.endpoint({ host: 'homeassistant.local', port: 8123, https: false }).error, 'http-consent');
  assert.strictEqual(Ha.endpoint({ host: 'homeassistant.local', port: 8123, https: false, httpConsentHost: 'other.local' }).error, 'http-consent');
  assert.strictEqual(Ha.endpoint({ host: 'homeassistant.local', port: 8123, https: false, httpConsentHost: 'homeassistant.local' }).url, 'ws://homeassistant.local:8123/api/websocket');
  assert.match(Ha.endpointMessage('http-consent'), /isn't encrypted/);
});

test('the token travels only in the auth message, never in the URL; then subscribe and read every detector', () => {
  const h = client();
  const ws = connected(h);
  assert.ok(!ws.url.includes(TOKEN));
  assert.deepStrictEqual(ws.sent[0], { type: 'auth', access_token: TOKEN });
  assert.deepStrictEqual(ws.sent[1], { id: 1, type: 'subscribe_events', event_type: 'state_changed' });
  assert.deepStrictEqual(ws.sent[2], { id: 2, type: 'subscribe_events', event_type: 'unified_tv_reminder' });
  assert.deepStrictEqual(ws.sent[3], { id: 3, type: 'get_states' });
  assert.strictEqual(h.c.status, Ha.STATUS.AUTHORISED);
  for (const [, detail] of h.updates) assert.ok(!JSON.stringify(detail).includes(TOKEN), 'no token in any update');
});

test('an alarm already sounding is shown on connect', () => {
  const h = client(); const ws = connected(h);
  ws.receive({ id: 3, type: 'result', success: true, result: [{ entity_id: 'binary_sensor.kitchen_smoke', state: 'on', last_changed: '2026-10-25T19:40:00+00:00', attributes: { device_class: 'smoke', friendly_name: 'Kitchen' } }] });
  assert.ok(h.policy.screen());
  assert.deepStrictEqual(h.updates.filter((u) => u[0] === 'alarms').pop(), ['alarms', { fresh: true }]);
});

test('a refused token stops for good and says authentication is required', () => {
  const h = client(); h.c.start(); const ws = FakeSocket.last;
  ws.receive({ type: 'auth_required' }); ws.receive({ type: 'auth_invalid' });
  assert.strictEqual(h.c.status, Ha.STATUS.AUTH_REQUIRED);
  assert.ok(ws.closed); assert.strictEqual(h.timers.length, 0, 'no retry with a bad token');
});

test('losing the connection keeps the alarm, marks status unknown, and reconnects with backoff', () => {
  const h = client(); const ws = connected(h);
  ws.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke'));
  assert.ok(h.policy.emergency());
  ws.drop();
  assert.ok(h.policy.emergency(), 'never cleared by a lost connection');
  assert.ok(!h.policy.connected(), 'shown as alarm status unknown');
  assert.strictEqual(h.c.status, Ha.STATUS.OFFLINE);
  assert.strictEqual(h.timers[0].ms, 1000);
  h.timers[0].f(); FakeSocket.last.drop();
  assert.strictEqual(h.timers[1].ms, 2000, 'backoff doubles');
});

test('a reconnect snapshot restores the picture without hiding a new genuine alarm', () => {
  const h = client(); let ws = connected(h);
  ws.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke'));
  h.policy.acknowledge('binary_sensor.kitchen_smoke');
  ws.drop(); h.timers[0].f(); ws = FakeSocket.last; ws.onopen(); ws.receive({ type: 'auth_required' }); ws.receive({ type: 'auth_ok' });
  ws.receive({ id: 3, type: 'result', success: true, result: [
    { entity_id: 'binary_sensor.kitchen_smoke', state: 'on', last_changed: '2026-10-25T19:42:04+00:00', attributes: { device_class: 'smoke', friendly_name: 'Kitchen' } },
    { entity_id: 'binary_sensor.boiler_co', state: 'on', last_changed: '2026-10-25T19:43:00+00:00', attributes: { device_class: 'carbon_monoxide', friendly_name: 'Boiler' } }] });
  assert.ok(h.policy.connected());
  assert.ok(h.policy.alarms()[0].acknowledged, 'the kitchen acknowledgement survives');
  assert.ok(h.policy.screen(), 'the new CO alarm brings the full screen back');
});

test('Ring during an emergency gets no card: it is queued; outside one it rings once', () => {
  const h = client(); const ws = connected(h);
  ws.receive(stateEvent('binary_sensor.front_door_ding', 'off', 'on', ''));
  assert.strictEqual(h.updates.filter((u) => u[0] === 'ring').length, 1);
  ws.receive(stateEvent('binary_sensor.front_door_ding', 'off', 'on', ''));
  assert.strictEqual(h.updates.filter((u) => u[0] === 'ring').length, 1, 'duplicate ignored');
  const g = client(); const ws2 = connected(g);
  ws2.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke'));
  ws2.receive(stateEvent('binary_sensor.front_door_ding', 'off', 'on', ''));
  assert.strictEqual(g.updates.filter((u) => u[0] === 'ring').length, 0);
  assert.ok(g.policy.queuedRingAt() > 0);
});

test('stale or future events are ignored, but an alarm is believed whatever the clock says', () => {
  const h = client(); const ws = connected(h);
  ws.receive(stateEvent('binary_sensor.front_door_ding', 'off', 'on', '', '2026-10-25T19:00:00+00:00'));
  assert.strictEqual(h.updates.filter((u) => u[0] === 'ring').length, 0, 'a doorbell from 40 minutes ago');
  ws.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke', '2026-10-25T19:00:00+00:00'));
  assert.ok(h.policy.emergency());
});

test('reminders: shown for this room, dropped during an emergency, held during Ring', () => {
  const h = client(); const ws = connected(h);
  const reminder = (text, room) => ({ type: 'event', event: { event_type: 'unified_tv_reminder', time_fired: '2026-10-25T19:42:04+00:00', data: { text: text, room: room } } });
  ws.receive(reminder('Bins out tonight', 'frame'));
  ws.receive(reminder('For the Sony', 'sony'));
  assert.deepStrictEqual(h.updates.filter((u) => u[0] === 'reminder').map((u) => u[1].text), ['Bins out tonight']);
  ws.receive(stateEvent('binary_sensor.front_door_ding', 'off', 'on', ''));
  ws.receive(reminder('Held', 'frame'));
  assert.strictEqual(h.updates.filter((u) => u[0] === 'reminder').length, 1, 'held while Ring shows');
  h.policy.dismiss(); assert.strictEqual(h.policy.release(Date.parse('2026-10-25T19:42:10Z')), 'Held');
  ws.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke'));
  ws.receive(reminder('Dropped', 'frame'));
  assert.strictEqual(h.updates.filter((u) => u[0] === 'reminder').length, 1, 'dropped during the emergency');
});

test('hiding the app closes the socket on purpose and leaves status unknown', () => {
  const h = client(); const ws = connected(h);
  ws.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke'));
  h.c.stop();
  assert.ok(ws.closed); assert.ok(!h.policy.connected()); assert.ok(h.policy.emergency());
  ws.drop(); assert.strictEqual(h.timers.length, 0, 'no reconnect after a deliberate stop');
});

test('no token or address: nothing connects', () => {
  const before = FakeSocket.all.length;
  const h = client({ token: '' }); h.c.start();
  assert.strictEqual(FakeSocket.all.length, before);
  assert.strictEqual(h.c.status, Ha.STATUS.NOT_CONFIGURED);
});

test('a refused token during an alarm keeps the alarm on screen with its status unknown', () => {
  const h = client(); const ws = connected(h);
  ws.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke'));
  const before = h.updates.filter((u) => u[0] === 'alarms').length;
  ws.receive({ type: 'auth_invalid' });
  assert.ok(h.policy.emergency()); assert.ok(!h.policy.connected());
  assert.strictEqual(h.updates.filter((u) => u[0] === 'alarms').length, before + 1, 'the alarm screen is redrawn with "status unknown"');
});

test('a reminder during an emergency is dropped, not held for after the alarm', () => {
  const h = client(); const ws = connected(h);
  ws.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke'));
  ws.receive(stateEvent('binary_sensor.front_door_ding', 'off', 'on', ''));   // queued: Ring waits for the alarm
  ws.receive({ type: 'event', event: { event_type: 'unified_tv_reminder', time_fired: '2026-10-25T19:42:04+00:00', data: { text: 'Later', room: 'frame' } } });
  h.policy.update('binary_sensor.kitchen_smoke', 'smoke', '', 'off', 0, false); h.policy.dismiss();
  assert.strictEqual(h.policy.release(Date.parse('2026-10-25T19:42:10Z')), null);
});

test('with alarms switched off, reconnecting forgets detectors from before', () => {
  const policy = new A.Policy(); policy.update('binary_sensor.kitchen_smoke', 'smoke', 'Kitchen', 'on', 1, false);
  const h = client({ alarms: false, policy: policy }); connected(h);
  assert.ok(!policy.emergency());
  assert.ok(h.updates.some((u) => u[0] === 'alarms'), 'the screen is redrawn without them');
});

// Ring rules RING-01..04 through the client (owner, 25 October 2026).
function ringEvent(entity, oldState, newState, attributes, timeFired) {
  return { type: 'event', event: { event_type: 'state_changed', time_fired: timeFired || '2026-10-25T19:42:04+00:00', data: {
    entity_id: entity, old_state: { state: oldState, attributes: attributes || {} },
    new_state: { state: newState, last_changed: '2026-10-25T19:42:04+00:00', attributes: attributes || {} } } } };
}
const PERSON = 'binary_sensor.front_door_person';
const kinds = (h, k) => h.updates.filter((u) => u[0] === k).length;

test('RING-01: motion from the person sensor without a person does nothing at all', () => {
  const h = client({ person: 'binary_sensor.front_door_motion' }); const ws = connected(h);
  ws.receive(ringEvent('binary_sensor.front_door_motion', 'off', 'on', { friendly_name: 'Front Door Motion' }));
  ws.receive(ringEvent('binary_sensor.drive_motion', 'off', 'on', { friendly_name: 'Drive Motion' }));
  assert.strictEqual(kinds(h, 'person'), 0); assert.strictEqual(kinds(h, 'ring'), 0);
});
test('RING-02: a confirmed person is one toast per sensor per minute, and never the doorbell card', () => {
  const h = client({ person: PERSON }); const ws = connected(h);
  ws.receive(ringEvent(PERSON, 'off', 'on', { friendly_name: 'Front Door Person' }));
  assert.strictEqual(kinds(h, 'person'), 1); assert.strictEqual(kinds(h, 'ring'), 0, 'a person never opens the doorbell card');
  assert.deepStrictEqual(h.updates.filter((u) => u[0] === 'person')[0][1], { entity: PERSON });
  ws.receive(ringEvent(PERSON, 'on', 'off', {})); ws.receive(ringEvent(PERSON, 'off', 'on', {}));
  assert.strictEqual(kinds(h, 'person'), 1, 'again within a minute: no second toast');
  h.setNow(Date.parse('2026-10-25T19:43:06Z'));
  ws.receive(ringEvent(PERSON, 'on', 'off', {}, '2026-10-25T19:43:05+00:00')); ws.receive(ringEvent(PERSON, 'off', 'on', {}, '2026-10-25T19:43:05+00:00'));
  assert.strictEqual(kinds(h, 'person'), 2, 'a minute later is a new visit');
});
test('RING-04: during an emergency a person shows nothing and is not queued; a press is queued', () => {
  const h = client({ person: PERSON }); const ws = connected(h);
  ws.receive(stateEvent('binary_sensor.kitchen_smoke', 'off', 'on', 'smoke'));
  ws.receive(ringEvent(PERSON, 'off', 'on', {}));
  assert.strictEqual(kinds(h, 'person'), 0); assert.strictEqual(h.policy.queuedRingAt(), 0, 'a person is never queued');
  ws.receive(ringEvent('binary_sensor.front_door_ding', 'off', 'on', {}));
  assert.strictEqual(kinds(h, 'ring'), 0); assert.ok(h.policy.queuedRingAt() > 0, 'the press waits for the alarm to end');
});
test('a stale person event is ignored like any doorbell event', () => {
  const h = client({ person: PERSON }); const ws = connected(h);
  ws.receive(ringEvent(PERSON, 'off', 'on', {}, '2026-10-25T19:00:00+00:00'));
  assert.strictEqual(kinds(h, 'person'), 0);
});
