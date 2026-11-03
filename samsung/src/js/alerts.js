/*
 * Unified TV for Samsung Frame: the alert model (spec 10, owner decision OD-07, 25 October 2026).
 *
 * A line-for-line port of Android's AlertPolicy (preview/app/src/io/unifiedtv/preview/AlertPolicy.java), so both TVs
 * follow the same rules; samsung/test/alerts.test.js mirrors AlertPolicyTest case by case.
 * Smoke and carbon monoxide alarms come first and are equal, then Ring, then detector warnings, then reminders.
 * Ring never covers an active alarm; it waits until every alarm has cleared. Duplicates are bounded, a reminder during
 * Ring waits briefly, stale reminders are dropped, and reminders during an emergency are dropped.
 * Ring rules RING-01..04: ordinary motion is ignored; a confirmed person gives one discreet 5-second toast per sensor per
 * minute (never during an emergency, never queued, never a camera); a doorbell press gives the doorbell card, kept for
 * later during an emergency.
 * On the Frame this only runs while Unified TV is open and in front: Tizen may suspend a hidden app, so nothing here
 * claims to reach a viewer who is in another app, in Art Mode or has the TV off (spec 10.8, PRODUCT_SPEC 24.3).
 * This is a supplementary display, never a certified alarm.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Alerts = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  /** Home Assistant and TV clocks are independently set: allow the same small lead the event filter accepts. */
  const SKEW = 5000, RELEVANT = 30000;
  const ALARM = 'alarm', NORMAL = 'normal', OFFLINE = 'offline', TEST = 'test', FAULT = 'fault';
  const NONE = 0, CHANGED = 1, NEW_ALARM = 2;
  const RING_STILL_RELEVANT = 45000, RING_WINDOW = 45000, RING_REPEAT = 15000;

  /** One detector's current reading. An alarm keeps its first time; "lost" marks an alarm whose detector stopped answering. */
  class Detector {
    constructor(entity, kind, name, state, since, simulated) {
      this.entity = entity; this.kind = kind; this.name = name; this.state = state; this.since = since;
      this.simulated = simulated; this.acknowledged = false; this.lost = false;
    }
    alarm() { return this.state === ALARM; }
    /** "SMOKE ALARM" or "CARBON MONOXIDE ALARM"; simulated ones always say TEST. */
    title() { return (this.simulated ? 'TEST · ' : '') + (this.kind === 'smoke' ? 'SMOKE ALARM' : 'CARBON MONOXIDE ALARM'); }
    advice() {
      return this.kind === 'smoke' ? 'Get everyone out, stay out, and call the fire service.'
        : 'Get everyone outside into fresh air and call for help. Don\'t go back in until it\'s safe.';
    }
    warning() {
      const what = this.kind === 'smoke' ? 'Smoke detector' : 'Carbon monoxide detector';
      return (this.simulated ? 'TEST · ' : '') + what + (this.state === TEST ? ' test' : this.state === FAULT ? ' fault' : ' offline');
    }
  }

  function alarmKind(entity, deviceClass) {
    // binary_sensor only: a sensor.* with device class carbon_monoxide is a concentration in ppm, not an alarm.
    if (typeof entity !== 'string' || entity.indexOf('binary_sensor.') !== 0) return null;
    return deviceClass === 'smoke' || deviceClass === 'carbon_monoxide' ? deviceClass : null;
  }
  /** Home Assistant's state to ours. Test and fault exist only where an integration reports them; nothing is assumed. */
  function reading(state) {
    switch (state) {
      case 'on': return ALARM;
      case 'off': return NORMAL;
      case 'unavailable': case 'unknown': return OFFLINE;
      case 'test': return TEST;
      case 'fault': return FAULT;
      default: return null;
    }
  }
  function rising(entity, door, person, oldState, next) {
    if (!entity || !(entity === door || entity === person)) return false;
    return edge(entity, oldState, next);
  }
  /** A new press or detection: off to on, or for an event entity any new event time (not a return from unavailable). */
  function edge(entity, oldState, next) {
    // Newer Ring, Nest and UniFi integrations report a press as an event entity whose state is the time of the last
    // press: any new time is a new press. Coming back from unavailable (a restart) is not.
    if (entity.indexOf('event.') === 0) {
      return typeof next === 'string' && next !== oldState && next !== 'unavailable' && next !== 'unknown' && oldState !== 'unavailable';
    }
    return oldState === 'off' && next === 'on';
  }

  // Ring rules RING-01..04 (owner, 25 October 2026): a doorbell press opens the doorbell card; a confirmed person gets a
  // discreet toast only; ordinary motion does nothing at all. Mirrors AlertPolicy.ringKind/personConfirmed/acceptPerson.
  const RING_NONE = 0, RING_PRESS = 1, RING_PERSON = 2;
  const PERSON_REPEAT = 60000, PERSON_TOAST_MS = 5000;
  const PERSON_WORDS = ['person', 'human', 'people', 'pedestrian'];
  function attrText(a, k) { const v = a[k]; return typeof v === 'string' ? v : v === null || v === undefined ? '' : String(v); }
  /** As org.json's optBoolean: true, or the text "true" in any case. */
  function attrTrue(a, k) { const v = a[k]; return v === true || (typeof v === 'string' && v.toLowerCase() === 'true'); }
  /** What one state change means for the doorbell: a press, a confirmed person, or nothing. */
  function ringKind(entity, door, person, oldState, next, attributes) {
    if (typeof entity !== 'string' || !entity) return RING_NONE;
    if (entity === door) return edge(entity, oldState, next) ? RING_PRESS : RING_NONE;
    if (entity === person) return edge(entity, oldState, next) && personConfirmed(entity, attributes) ? RING_PERSON : RING_NONE;
    return RING_NONE;
  }
  /**
   * RING-01/02: only a confirmed person counts. Confirmation is what the integration itself says: an event type or
   * detection type of person/human (Ring Smart Alerts, UniFi, Nest), or ring-mqtt's personDetected. A sensor without that
   * evidence counts only if it is a dedicated person sensor; anything that names itself motion is ordinary motion.
   */
  function personConfirmed(entity, a) {
    const attrs = a && typeof a === 'object' ? a : null;
    if (attrs) {
      const type = attrText(attrs, 'event_type').toLowerCase();
      const detected = (Object.prototype.hasOwnProperty.call(attrs, 'detection_type') ? attrText(attrs, 'detection_type') : attrText(attrs, 'detectionType')).toLowerCase();
      if (PERSON_WORDS.indexOf(type) !== -1 || PERSON_WORDS.indexOf(detected) !== -1 || attrTrue(attrs, 'personDetected') || attrTrue(attrs, 'person_detected')) return true;
    }
    if (entity.indexOf('event.') === 0) return false;   // an event entity must say "person" itself
    const words = (entity + ' ' + (attrs ? attrText(attrs, 'friendly_name') : '')).toLowerCase();
    return words.indexOf('motion') === -1;
  }
  /**
   * Owner, 25 October 2026: "Show person alerts in Kids" (on unless an adult turns it off). Off means no person toast
   * while Kids is watching. Only the person toast: doorbell presses and smoke/CO are the same in every profile.
   */
  function personToastAllowed(kids, showInKids) { return !kids || !!showInKids; }
  /** HA's last_changed (ISO 8601, often with microseconds) to epoch ms; 0 when unreadable. */
  function time(iso) {
    if (typeof iso !== 'string' || !iso) return 0;
    const trimmed = iso.replace(/(\.\d{3})\d+/, '$1');
    const ms = Date.parse(trimmed);
    return isNaN(ms) ? 0 : ms;
  }
  function name(attributes) {
    const n = attributes && typeof attributes.friendly_name === 'string' ? attributes.friendly_name : '';
    return n.length > 60 ? n.substring(0, 60) : n;
  }
  /** Every smoke and CO detector in a successful get_states result, as [entity, kind, name, state, since]; null for anything else, so a refusal never clears an alarm. */
  function detectorsIn(message, id) {
    let value;
    try { value = typeof message === 'string' ? JSON.parse(message) : message; } catch (e) { return null; }
    if (!value || typeof value !== 'object' || value.id !== id || value.type !== 'result' || value.success !== true) return null;
    const states = value.result;
    if (!Array.isArray(states)) return null;
    const out = [];
    states.forEach(function (s) {
      if (!s || typeof s !== 'object') return;
      const attr = s.attributes && typeof s.attributes === 'object' ? s.attributes : null;
      const entity = typeof s.entity_id === 'string' ? s.entity_id : '';
      const kind = alarmKind(entity, attr ? attr.device_class : '');
      if (kind) out.push([entity, kind, name(attr), String(s.state), String(time(s.last_changed))]);
    });
    return out;
  }

  class Policy {
    constructor() {
      this.seen = new Map(); this.ringUntil = 0; this.deferred = ''; this.deferredFired = 0;
      this.detectors = new Map(); this.connected_ = true; this.queuedRing = 0; this.people = new Map();
    }
    /** RING-02: repeated person events from one entity within a minute are one visit, so one toast. */
    acceptPerson(entity, now) {
      const previous = this.people.get(entity);
      if (previous !== undefined && now - previous < PERSON_REPEAT) return false;
      this.people.delete(entity); this.people.set(entity, now);
      while (this.people.size > 32) this.people.delete(this.people.keys().next().value);
      return true;
    }
    acceptRing(id, now) {
      const previous = this.seen.get(id);
      if (previous !== undefined && now - previous < RING_REPEAT) return false;
      this.seen.set(id, now);
      while (this.seen.size > 128) this.seen.delete(this.seen.keys().next().value);
      this.ringUntil = now + RING_WINDOW;
      return true;
    }
    reminderAllowed(fired, now) { return !this.emergency() && now >= this.ringUntil && fired - now <= SKEW && now - fired <= RELEVANT; }
    ringing(now) { return now < this.ringUntil; }
    /** Spec 10.2: a reminder that arrives during Ring is held (latest only), then shown if still relevant. */
    defer(text, fired) { if (typeof text === 'string' && text.trim()) { this.deferred = text; this.deferredFired = fired; } }
    release(now) { const text = this.deferred; this.deferred = ''; return text && this.reminderAllowed(this.deferredFired, now) ? text : null; }
    dismiss() { this.ringUntil = 0; }

    /**
     * Records one detector's state. NEW_ALARM when an alarm starts (the full screen comes back), CHANGED when anything
     * shown changes. Normal removes it: that is "no alarm from this detector", never "safe". An alarm whose detector
     * goes quiet stays an alarm, marked lost, because the detector may have failed in the fire. An alarm that Home
     * Assistant says started again later than the one shown (it cleared and re-sounded while the TV was away) is new.
     */
    update(entity, kind, nm, haState, since, simulated) {
      const state = reading(haState);
      if (!kind || !state) return NONE;
      const d = this.detectors.get(entity);
      const n = nm || '';
      if (state === NORMAL) return this.detectors.delete(entity) ? CHANGED : NONE;
      if (d && d.alarm() && (state === OFFLINE || state === FAULT)) { if (d.lost) return NONE; d.lost = true; return CHANGED; }
      if (d && d.alarm() && state === ALARM && !d.simulated && d.since > 0 && since > d.since) {
        this.detectors.set(entity, new Detector(entity, kind, n, state, since, false)); return NEW_ALARM;
      }
      if (d && d.alarm() && state === ALARM) { const changed = d.lost || d.name !== n; d.lost = false; d.name = n; return changed ? CHANGED : NONE; }
      if (d && d.state === state && d.name === n) return NONE;
      this.detectors.set(entity, new Detector(entity, kind, n, state, since, !!simulated));
      return state === ALARM ? NEW_ALARM : CHANGED;
    }
    /**
     * The full picture after (re)connecting: every smoke and CO detector with its state. Warnings no longer reported are
     * dropped; simulated detectors are kept. A missing alarm is never cleared by its absence: it stays, marked lost, until
     * its detector reports off. Acknowledgements survive for alarms still sounding.
     */
    snapshot(all) {
      this.connected_ = true;
      let result = NONE;
      const seen = {};
      const self = this;
      all.forEach(function (d) {
        seen[d[0]] = true;
        const since = parseInt(d[4], 10);
        result = Math.max(result, self.update(d[0], d[1], d[2], d[3], isNaN(since) ? 0 : since, false));
      });
      Array.from(this.detectors.values()).forEach(function (d) {
        if (d.simulated || seen[d.entity]) return;
        if (!d.alarm()) { self.detectors.delete(d.entity); result = Math.max(result, CHANGED); }
        else if (!d.lost) { d.lost = true; result = Math.max(result, CHANGED); }
      });
      return result;
    }
    /** Losing Home Assistant never clears an alarm; it is shown as "status unknown" until the next full picture. */
    connection(up) { this.connected_ = !!up; }
    connected() { return this.connected_; }
    emergency() { return Array.from(this.detectors.values()).some(function (d) { return d.alarm(); }); }
    /** The full screen is shown while any alarm is unacknowledged. */
    screen() { return Array.from(this.detectors.values()).some(function (d) { return d.alarm() && !d.acknowledged; }); }
    alarms() {
      return Array.from(this.detectors.values()).filter(function (d) { return d.alarm(); })
        .sort(function (a, b) { return a.since - b.since; });
    }
    warnings() { return Array.from(this.detectors.values()).filter(function (d) { return !d.alarm(); }); }
    /** Acknowledges one alarm only; others stay on screen. The detector itself is never touched. */
    acknowledge(entity) {
      const d = this.detectors.get(entity);
      if (!d || !d.alarm() || d.acknowledged) return false;
      d.acknowledged = true; return true;
    }
    /** Alarms switched off in household settings: forget every real detector (tests are ended separately). */
    forgetDetectors() {
      let n = 0; const self = this;
      Array.from(this.detectors.values()).forEach(function (d) { if (!d.simulated) { self.detectors.delete(d.entity); n++; } });
      return n > 0 ? CHANGED : NONE;
    }
    /** Ends a test: removes simulated detectors only. */
    endSimulation() {
      let n = 0; const self = this;
      Array.from(this.detectors.values()).forEach(function (d) { if (d.simulated) { self.detectors.delete(d.entity); n++; } });
      return n > 0 ? CHANGED : NONE;
    }
    /** A doorbell visit during an emergency: no camera, only the latest visit is remembered. */
    queueRing(now) { this.queuedRing = now; }
    /** After every alarm has cleared: 1 to show the doorbell card (visit still recent), -1 for a "rang during the alarm" note, 0 for nothing. */
    ringAfterAlarm(now) {
      if (this.queuedRing === 0 || this.emergency()) return 0;
      const at = this.queuedRing; this.queuedRing = 0;
      return now - at < RING_STILL_RELEVANT ? 1 : -1;
    }
    queuedRingAt() { return this.queuedRing; }
  }

  return {
    SKEW: SKEW, RELEVANT: RELEVANT, ALARM: ALARM, NORMAL: NORMAL, OFFLINE: OFFLINE, TEST: TEST, FAULT: FAULT,
    NONE: NONE, CHANGED: CHANGED, NEW_ALARM: NEW_ALARM, RING_STILL_RELEVANT: RING_STILL_RELEVANT,
    RING_NONE: RING_NONE, RING_PRESS: RING_PRESS, RING_PERSON: RING_PERSON, PERSON_REPEAT: PERSON_REPEAT, PERSON_TOAST_MS: PERSON_TOAST_MS,
    Detector: Detector, Policy: Policy, alarmKind: alarmKind, reading: reading, rising: rising, ringKind: ringKind,
    personConfirmed: personConfirmed, personToastAllowed: personToastAllowed, time: time, name: name, detectorsIn: detectorsIn
  };
}));
