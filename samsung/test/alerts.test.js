'use strict';
// Owner decision OD-07 (spec 10): mirrors preview/app/test/io/unifiedtv/preview/AlertPolicyTest.java case by case, so the
// Frame and the Android TVs follow the same alert rules.
const test = require('node:test');
const assert = require('node:assert');
const A = require('../src/js/alerts.js');
const P = () => new A.Policy();

test('only doorbell and person rising edges trigger', () => {
  assert.ok(A.rising('binary_sensor.door', 'binary_sensor.door', 'binary_sensor.person', 'off', 'on'));
  assert.ok(A.rising('binary_sensor.person', 'binary_sensor.door', 'binary_sensor.person', 'off', 'on'));
  assert.ok(!A.rising('binary_sensor.motion', 'binary_sensor.door', 'binary_sensor.person', 'off', 'on'));
  assert.ok(!A.rising('binary_sensor.person', 'binary_sensor.door', 'binary_sensor.person', 'unknown', 'on'));
  assert.ok(!A.rising('binary_sensor.person', 'binary_sensor.door', 'binary_sensor.person', 'on', 'on'));
});
test('event doorbells ring on each new press but not after a restart', () => {
  const door = 'event.front_door_ding';
  assert.ok(A.rising(door, door, '', '2026-10-25T19:00:00+00:00', '2026-10-25T19:42:00+00:00'));
  assert.ok(A.rising(door, door, '', 'unknown', '2026-10-25T19:42:00+00:00'), 'first press after start-up');
  assert.ok(!A.rising(door, door, '', 'unavailable', '2026-10-25T19:42:00+00:00'), 'back from a restart');
  assert.ok(!A.rising(door, door, '', '2026-10-25T19:42:00+00:00', 'unavailable'));
  assert.ok(!A.rising(door, door, '', '2026-10-25T19:42:00+00:00', '2026-10-25T19:42:00+00:00'), 'same press repeated');
  assert.ok(!A.rising('event.back_door', door, '', 'a', 'b'), 'not the chosen doorbell');
});
test('a repeated event is deduplicated but another is accepted', () => {
  const p = P(); assert.ok(p.acceptRing('door-event', 1000)); assert.ok(!p.acceptRing('door-event', 1200)); assert.ok(p.acceptRing('person-event', 1300));
});
test('Ring suppresses reminders and expired ones stay hidden', () => {
  const p = P(); p.acceptRing('door', 1000); assert.ok(!p.reminderAllowed(1100, 1200)); p.dismiss(); assert.ok(p.reminderAllowed(1100, 1200)); assert.ok(!p.reminderAllowed(1100, 32000));
});
test('an expired Ring window releases priority', () => {
  const p = P(); p.acceptRing('door', 1000); assert.ok(p.reminderAllowed(46000, 46001)); assert.ok(!p.reminderAllowed(52000, 46001));
});
test('a reminder from a slightly fast server clock is shown', () => {
  const p = P(); assert.ok(p.reminderAllowed(2000, 1000)); assert.ok(p.reminderAllowed(6000, 1000)); assert.ok(!p.reminderAllowed(6001, 1000));
});
test('a reminder during Ring waits, then shows if still relevant, once', () => {
  const p = P(); p.acceptRing('door', 1000); assert.ok(p.ringing(2000));
  p.defer('Bins out tonight', 2000); p.dismiss(); assert.ok(!p.ringing(3000));
  assert.strictEqual(p.release(10000), 'Bins out tonight'); assert.strictEqual(p.release(10001), null, 'released once');
});
test('a reminder held too long during Ring is dropped', () => {
  const p = P(); p.acceptRing('door', 1000); p.defer('Bins out tonight', 2000); p.dismiss(); assert.strictEqual(p.release(40000), null);
});
test('only the latest held reminder is kept and blank ones are ignored', () => {
  const p = P(); p.acceptRing('door', 1000); p.defer('First', 2000); p.defer('Second', 3000); p.defer('  ', 4000); p.dismiss(); assert.strictEqual(p.release(5000), 'Second');
});
test('a person detected during the visit keeps ringing', () => {
  const p = P(); assert.ok(p.acceptRing('door:t1', 1000)); assert.ok(p.acceptRing('person:t2', 30000)); assert.ok(p.ringing(70000)); assert.ok(!p.ringing(75001));
});

const HALL = 'binary_sensor.hall', BOILER = 'binary_sensor.boiler';
test('only Home Assistant smoke and CO binary sensors count as alarms', () => {
  assert.strictEqual(A.alarmKind(HALL, 'smoke'), 'smoke');
  assert.strictEqual(A.alarmKind(BOILER, 'carbon_monoxide'), 'carbon_monoxide');
  assert.strictEqual(A.alarmKind('binary_sensor.smoke_alarm', 'motion'), null, 'a name is never enough');
  assert.strictEqual(A.alarmKind('sensor.hall_co', 'carbon_monoxide'), null, 'a CO sensor.* is a ppm reading, not an alarm');
  assert.strictEqual(A.alarmKind(null, 'smoke'), null);
});
test('standard states map, and only exposed test or fault are used', () => {
  assert.strictEqual(A.reading('on'), A.ALARM); assert.strictEqual(A.reading('off'), A.NORMAL);
  assert.strictEqual(A.reading('unavailable'), A.OFFLINE); assert.strictEqual(A.reading('unknown'), A.OFFLINE);
  assert.strictEqual(A.reading('test'), A.TEST); assert.strictEqual(A.reading('fault'), A.FAULT);
  assert.strictEqual(A.reading('smoke'), null, 'anything else is ignored, never guessed');
});
test('smoke and CO are separate entries with room and time', () => {
  const p = P();
  assert.strictEqual(p.update(BOILER, 'carbon_monoxide', 'Boiler cupboard', 'on', 2000, false), A.NEW_ALARM);
  assert.strictEqual(p.update(HALL, 'smoke', 'Hall', 'on', 1000, false), A.NEW_ALARM);
  const on = p.alarms(); assert.strictEqual(on.length, 2);
  assert.strictEqual(on[0].name, 'Hall', 'oldest first'); assert.strictEqual(on[0].since, 1000); assert.strictEqual(on[0].title(), 'SMOKE ALARM');
  assert.strictEqual(on[1].title(), 'CARBON MONOXIDE ALARM'); assert.ok(on[1].advice().includes('fresh air')); assert.ok(on[0].advice().includes('stay out'));
  assert.strictEqual(p.update(HALL, 'smoke', 'Hall', 'on', 1000, false), A.NONE, 'a duplicate event (same last_changed) changes nothing');
  assert.strictEqual(p.update(HALL, 'smoke', 'Hall', 'on', 500, false), A.NONE, 'an older, out-of-order event keeps the first time'); assert.strictEqual(p.alarms()[0].since, 1000);
});
test('acknowledging one alarm leaves the other on screen, and a banner remains', () => {
  const p = P();
  p.update(HALL, 'smoke', 'Hall', 'on', 1000, false); p.update(BOILER, 'carbon_monoxide', 'Boiler', 'on', 2000, false);
  assert.ok(p.acknowledge(HALL)); assert.ok(p.screen(), 'CO still unacknowledged');
  assert.ok(p.acknowledge(BOILER)); assert.ok(!p.screen()); assert.ok(p.emergency(), 'banner while still active');
  assert.ok(!p.acknowledge(HALL), 'acknowledging twice does nothing');
  assert.strictEqual(p.update('binary_sensor.landing', 'smoke', 'Landing', 'on', 3000, false), A.NEW_ALARM, 'a new alarm brings the screen back'); assert.ok(p.screen());
  assert.ok(p.alarms()[0].acknowledged, 'acknowledged entries stay acknowledged');
});
test('normal clears only that detector, and a quiet detector keeps its alarm', () => {
  const p = P();
  p.update(HALL, 'smoke', 'Hall', 'on', 1000, false);
  assert.strictEqual(p.update(HALL, 'smoke', 'Hall', 'unavailable', 5000, false), A.CHANGED);
  assert.ok(p.emergency(), 'may have failed in the fire'); assert.ok(p.alarms()[0].lost); assert.strictEqual(p.alarms()[0].since, 1000);
  assert.strictEqual(p.update(HALL, 'smoke', 'Hall', 'off', 6000, false), A.CHANGED); assert.ok(!p.emergency()); assert.strictEqual(p.warnings().length, 0);
});
test('offline detectors are warnings, never emergencies', () => {
  const p = P();
  assert.strictEqual(p.update(HALL, 'smoke', 'Landing', 'unavailable', 1000, false), A.CHANGED);
  assert.ok(!p.emergency()); assert.ok(!p.screen()); assert.strictEqual(p.warnings().length, 1); assert.strictEqual(p.warnings()[0].warning(), 'Smoke detector offline');
  assert.ok(p.reminderAllowed(1000, 1000), 'an offline detector doesn\'t block reminders');
  p.update(BOILER, 'carbon_monoxide', 'Boiler', 'fault', 1000, false); assert.strictEqual(p.warnings()[1].warning(), 'Carbon monoxide detector fault');
});
test('reminders are dropped during an emergency', () => {
  const p = P(); p.acceptRing('door', 1000); p.defer('Bins out tonight', 2000);
  p.update(HALL, 'smoke', 'Hall', 'on', 2500, false); p.dismiss(); assert.strictEqual(p.release(3000), null); assert.ok(!p.reminderAllowed(3000, 3000));
});
test('Ring waits until every alarm has cleared, then shows (under 45 s) or leaves a note', () => {
  const p = P();
  p.update(HALL, 'smoke', 'Hall', 'on', 1000, false); p.acknowledge(HALL); p.queueRing(10000);
  assert.strictEqual(p.ringAfterAlarm(20000), 0, 'not while any alarm is active, acknowledged or not');
  p.update(HALL, 'smoke', 'Hall', 'off', 21000, false); assert.strictEqual(p.ringAfterAlarm(30000), 1, 'recent visit: show the card'); assert.strictEqual(p.ringAfterAlarm(30001), 0, 'only once');
  p.update(HALL, 'smoke', 'Hall', 'on', 40000, false); p.queueRing(41000); p.update(HALL, 'smoke', 'Hall', 'off', 200000, false);
  assert.strictEqual(p.ringAfterAlarm(200000), -1, 'old visit: a note instead');
});
test('the 45-second boundary: under 45 s shows the card (spec: "under 45 seconds old"), exactly 45 s is a note', () => {
  const p = P(); p.update(HALL, 'smoke', 'Hall', 'on', 0, false); p.queueRing(1000); p.update(HALL, 'smoke', 'Hall', 'off', 2000, false);
  assert.strictEqual(p.ringAfterAlarm(45999), 1);
  const q = P(); q.update(HALL, 'smoke', 'Hall', 'on', 0, false); q.queueRing(1000); q.update(HALL, 'smoke', 'Hall', 'off', 2000, false);
  assert.strictEqual(q.ringAfterAlarm(46000), -1);
});
test('repeated Ring during an alarm keeps only the latest visit (no growing queue)', () => {
  const p = P(); p.update(HALL, 'smoke', 'Hall', 'on', 0, false);
  for (let t = 1000; t < 100000; t += 1000) p.queueRing(t);
  assert.strictEqual(p.queuedRingAt(), 99000);
  for (let i = 0; i < 500; i++) p.acceptRing('visit-' + i, i);
  assert.ok(p.seen.size <= 128, 'the duplicate filter stays bounded');
});
test('connecting reads every detector and drops ones no longer reported', () => {
  const states = '{"id":3,"type":"result","success":true,"result":[' +
    '{"entity_id":"binary_sensor.hall","state":"on","last_changed":"2026-10-25T19:42:00+00:00","attributes":{"device_class":"smoke","friendly_name":"Hall"}},' +
    '{"entity_id":"binary_sensor.landing","state":"unavailable","attributes":{"device_class":"smoke","friendly_name":"Landing"}},' +
    '{"entity_id":"binary_sensor.boiler","state":"off","attributes":{"device_class":"carbon_monoxide"}},' +
    '{"entity_id":"sensor.boiler_co","state":"12","attributes":{"device_class":"carbon_monoxide"}},' +
    '{"entity_id":"binary_sensor.front_door","state":"on","attributes":{"device_class":"door"}}]}';
  const all = A.detectorsIn(states, 3); assert.strictEqual(all.length, 3);
  assert.deepStrictEqual(all[0], ['binary_sensor.hall', 'smoke', 'Hall', 'on', String(Date.parse('2026-10-25T19:42:00Z'))]);
  const p = P(); p.update('binary_sensor.kitchen', 'smoke', 'Kitchen', 'on', 1, false); p.update('simulated.smoke', 'smoke', 'Test', 'on', 1, true);
  p.connection(false); assert.ok(!p.connected());
  assert.strictEqual(p.snapshot(all), A.NEW_ALARM); assert.ok(p.connected());
  assert.deepStrictEqual(p.alarms().map((d) => d.name), ['Kitchen', 'Test', 'Hall'], 'kitchen missing but kept, test kept, hall new');
  assert.ok(p.alarms()[0].lost, 'a missing alarm is marked not responding, never cleared');
  assert.strictEqual(p.warnings().length, 1);
});
test('a refused or unrelated reply never clears an alarm', () => {
  assert.strictEqual(A.detectorsIn('{"id":3,"type":"result","success":false,"error":{}}', 3), null);
  assert.strictEqual(A.detectorsIn('{"id":1,"type":"result","success":true,"result":null}', 3), null);
  assert.strictEqual(A.detectorsIn('not json', 3), null);
});
test('tests are always labelled, and ending a test leaves real alarms', () => {
  const p = P();
  p.update('simulated.smoke', 'smoke', 'Kitchen', 'on', 1, true); p.update(HALL, 'smoke', 'Hall', 'on', 2, false);
  assert.ok(p.alarms()[0].title().startsWith('TEST')); assert.ok(!p.alarms()[1].title().startsWith('TEST'));
  assert.strictEqual(p.endSimulation(), A.CHANGED); assert.strictEqual(p.alarms().length, 1); assert.strictEqual(p.alarms()[0].name, 'Hall');
});
test('names are capped at 60 characters and HA microsecond times are read', () => {
  assert.strictEqual(A.name({ friendly_name: 'x'.repeat(80) }).length, 60);
  assert.strictEqual(A.time('2026-10-25T19:42:00.123456+00:00'), Date.parse('2026-10-25T19:42:00.123Z'));
  assert.strictEqual(A.time('nonsense'), 0);
});

test('an alarm that cleared and re-sounded while the TV was away comes back as new, unacknowledged', () => {
  const p = P(); p.update(HALL, 'smoke', 'Hall', 'on', 1000, false); p.acknowledge(HALL);
  assert.strictEqual(p.snapshot([[HALL, 'smoke', 'Hall', 'on', '1000']]), A.NONE, 'same alarm: acknowledgement kept');
  assert.ok(!p.screen());
  assert.strictEqual(p.snapshot([[HALL, 'smoke', 'Hall', 'on', '5000']]), A.NEW_ALARM);
  assert.ok(p.screen()); assert.strictEqual(p.alarms()[0].since, 5000);
});
test('a missing alarm stays until its detector reports off; a missing warning is dropped', () => {
  const p = P(); p.update(HALL, 'smoke', 'Hall', 'on', 1000, false); p.update('binary_sensor.landing', 'smoke', 'Landing', 'unavailable', 1000, false);
  assert.strictEqual(p.snapshot([]), A.CHANGED);
  assert.ok(p.emergency()); assert.ok(p.alarms()[0].lost); assert.strictEqual(p.warnings().length, 0);
  assert.strictEqual(p.snapshot([]), A.NONE, 'already marked');
  p.snapshot([[HALL, 'smoke', 'Hall', 'off', '9000']]); assert.ok(!p.emergency());
});
test('switching alarms off forgets real detectors and keeps tests', () => {
  const p = P(); p.update(HALL, 'smoke', 'Hall', 'on', 1, false); p.update('simulated.smoke', 'smoke', 'Test', 'on', 1, true);
  assert.strictEqual(p.forgetDetectors(), A.CHANGED); assert.deepStrictEqual(p.alarms().map((d) => d.name), ['Test']);
  assert.strictEqual(p.forgetDetectors(), A.NONE);
});

// Ring rules RING-01..04 (owner, 25 October 2026): mirrors the ring01..ring04 cases of AlertPolicyTest.
test('RING-01: ordinary motion never counts', () => {
  const door = 'binary_sensor.front_door_ding', person = 'binary_sensor.front_door_motion';
  assert.strictEqual(A.ringKind(person, door, person, 'off', 'on', { friendly_name: 'Front Door Motion' }), A.RING_NONE);
  assert.strictEqual(A.ringKind('binary_sensor.drive_motion', door, 'binary_sensor.front_door_person', 'off', 'on', null), A.RING_NONE, 'motion that isn\'t the chosen person sensor');
  assert.strictEqual(A.ringKind('event.front_door_motion', door, 'event.front_door_motion', '2026-10-25T10:00:00Z', '2026-10-25T10:05:00Z', { event_type: 'motion' }), A.RING_NONE, 'a Ring motion event is not a person');
});
test('RING-02: only a confirmed person counts', () => {
  const door = 'binary_sensor.front_door_ding';
  assert.strictEqual(A.ringKind('binary_sensor.front_door_motion', door, 'binary_sensor.front_door_motion', 'off', 'on', { personDetected: true }), A.RING_PERSON, 'ring-mqtt motion sensor that saw a person');
  assert.strictEqual(A.ringKind('binary_sensor.g4_person_detected', door, 'binary_sensor.g4_person_detected', 'off', 'on', { friendly_name: 'G4 Person detected' }), A.RING_PERSON, 'a dedicated person sensor');
  assert.strictEqual(A.ringKind('event.front_door_motion', door, 'event.front_door_motion', '2026-10-25T10:00:00Z', '2026-10-25T10:05:00Z', { event_type: 'human' }), A.RING_PERSON, 'an event entity that says person');
  assert.strictEqual(A.ringKind('binary_sensor.g4_person_detected', door, 'binary_sensor.g4_person_detected', 'on', 'on', null), A.RING_NONE, 'staying on is not a new person');
});
test('RING-02: what counts as confirmation (Frame extras)', () => {
  assert.ok(A.personConfirmed('event.porch', { detection_type: 'Person' }), 'detection type, any case');
  assert.ok(A.personConfirmed('event.porch', { detectionType: 'pedestrian' }));
  assert.ok(A.personConfirmed('binary_sensor.porch_motion', { person_detected: 'true' }), 'the text "true" counts, as org.json reads it');
  assert.ok(!A.personConfirmed('binary_sensor.porch_motion', { personDetected: false }));
  assert.ok(!A.personConfirmed('event.porch', null), 'an event entity must say person itself');
  assert.ok(!A.personConfirmed('binary_sensor.porch', { friendly_name: 'Porch Motion' }), 'a sensor that calls itself motion is motion');
  assert.ok(A.personConfirmed('binary_sensor.porch', { detection_type: 'person', friendly_name: 'Porch Motion' }), 'unless the integration says person');
  assert.strictEqual(A.ringKind('', '', '', 'off', 'on', null), A.RING_NONE);
  assert.strictEqual(A.ringKind(null, 'a', 'b', 'off', 'on', null), A.RING_NONE);
});
test('RING-02: repeated person events give one toast', () => {
  const p = P();
  assert.ok(p.acceptPerson('binary_sensor.p', 1000)); assert.ok(!p.acceptPerson('binary_sensor.p', 30000)); assert.ok(!p.acceptPerson('binary_sensor.p', 60999));
  assert.ok(p.acceptPerson('binary_sensor.p', 61000)); assert.ok(p.acceptPerson('binary_sensor.q', 30000), 'another camera is its own visit');
  assert.strictEqual(A.PERSON_TOAST_MS, 5000);
});
test('RING-02: the person memory is bounded', () => {
  const p = P();
  for (let i = 0; i < 100; i++) p.acceptPerson('binary_sensor.p' + i, 1000);
  assert.ok(p.people.size <= 32);
});
test('RING-03: a press is a press and never a person', () => {
  const door = 'binary_sensor.front_door_ding';
  assert.strictEqual(A.ringKind(door, door, 'binary_sensor.front_door_person', 'off', 'on', null), A.RING_PRESS);
  assert.strictEqual(A.ringKind('event.front_door_ding', 'event.front_door_ding', '', '2026-10-25T10:00:00Z', '2026-10-25T10:05:00Z', { event_type: 'ding' }), A.RING_PRESS);
  assert.strictEqual(A.ringKind('event.front_door_ding', 'event.front_door_ding', '', 'unavailable', '2026-10-25T10:05:00Z', null), A.RING_NONE, 'a restart is not a press');
});
test('RING-04: a press during an emergency is kept for later', () => {
  const p = P();
  p.update(HALL, 'smoke', 'Hall', 'on', 0, false); assert.ok(p.acceptRing('door:1', 1000)); p.queueRing(1000);
  assert.strictEqual(p.ringAfterAlarm(5000), 0, 'never while the alarm is active'); assert.strictEqual(p.queuedRingAt(), 1000);
  p.update(HALL, 'smoke', 'Hall', 'off', 6000, false); assert.strictEqual(p.ringAfterAlarm(7000), 1);
});
test('person toasts in Kids follow the setting, and Adults always see them', () => {
  assert.ok(A.personToastAllowed(false, false)); assert.ok(A.personToastAllowed(false, true));
  assert.ok(A.personToastAllowed(true, true), 'on by default'); assert.ok(!A.personToastAllowed(true, false));
});
