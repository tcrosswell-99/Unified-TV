/*
 * Unified TV for Samsung Frame: the Home Assistant connection (spec 10.4, 11, 13).
 *
 * A small WebSocket client: authenticate, subscribe to state_changed, read every smoke and CO detector with get_states,
 * and feed the alert policy (alerts.js). Losing the connection never clears an alarm: it becomes "Alarm status unknown".
 * The access token travels only inside the WebSocket's auth message: never in a URL, a log or local storage.
 * Plain ws:// (HTTP) is refused unless the owner has explicitly trusted that exact host on the home network.
 * This runs only while Unified TV is open and visible. When the app is hidden the socket is closed on purpose: a hidden
 * Tizen app may be suspended, so it is not a reliable alert channel (spec 6.2, 10.8). On return it reconnects and reads
 * the full picture again.
 */
(function (root, factory) {
  'use strict';
  var api = factory(typeof require === 'function' ? require('./alerts.js') : root.UTV.Alerts);
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Ha = api; }
}(typeof self !== 'undefined' ? self : this, function (Alerts) {
  'use strict';

  const STATES_ID = 3;
  const STATUS = {
    NOT_CONFIGURED: 'Not configured', CONNECTING: 'Connecting', REACHABLE: 'Reachable',
    AUTH_REQUIRED: 'Authentication required', AUTHORISED: 'Authorised', OFFLINE: 'Offline'
  };

  const LABEL = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$/;
  /** A host name, an IPv4 address or an IPv6 address (with or without brackets). Nothing else: no paths, no user names. */
  function hostKind(host) {
    const h = String(host || '').trim();
    if (!h || h.length > 253) return null;
    if (/^\d{1,3}(\.\d{1,3}){3}$/.test(h)) return h.split('.').every(function (p) { return +p <= 255; }) ? 'ipv4' : null;
    const bare = h.charAt(0) === '[' && h.charAt(h.length - 1) === ']' ? h.slice(1, -1) : h;
    if (bare.indexOf(':') !== -1) return /^[0-9A-Fa-f:.]+$/.test(bare) && bare.split('::').length <= 2 ? 'ipv6' : null;
    return h.split('.').every(function (l) { return LABEL.test(l); }) ? 'name' : null;
  }
  /**
   * The WebSocket address for the owner's settings, or the reason it can't be used. https -> wss://. http -> ws:// only
   * with consent for this very host (spec 11: trusted-LAN HTTP only with explicit consent).
   */
  function endpoint(cfg) {
    const host = String(cfg && cfg.host || '').trim();
    const kind = hostKind(host);
    if (!kind) return { ok: false, error: 'host' };
    const port = Number(cfg.port);
    if (!(port >= 1 && port <= 65535 && Math.floor(port) === port)) return { ok: false, error: 'port' };
    const secure = cfg.https !== false;
    if (!secure && cfg.httpConsentHost !== host) return { ok: false, error: 'http-consent' };
    const shown = kind === 'ipv6' && host.charAt(0) !== '[' ? '[' + host + ']' : host;
    return { ok: true, url: (secure ? 'wss://' : 'ws://') + shown + ':' + port + '/api/websocket', secure: secure };
  }
  function endpointMessage(error) {
    if (error === 'host') return 'Enter the Home Assistant address as a name or an IP address, without http:// or a path.';
    if (error === 'port') return 'The port must be a number from 1 to 65535.';
    if (error === 'http-consent') return 'This address uses HTTP, which isn\'t encrypted. Choose "Trust HTTP for this address" to allow it on your home network.';
    return '';
  }

  /**
   * opts: { url, token, WebSocket, policy, now(), setTimer(fn, ms), clearTimer(id), onUpdate(kind, detail),
   *         alarms (bool), reminders (bool), door, person (entity ids, optional), room ('frame') }.
   * onUpdate kinds: 'status' (text), 'alarms' ({fresh}), 'ring' ({entity}: a doorbell press), 'person' ({entity}: a
   * confirmed person, toast only), 'reminder' ({text}).
   */
  class Client {
    constructor(opts) {
      this.o = opts; this.policy = opts.policy; this.socket = null; this.stopped = true;
      this.retry = 1000; this.timer = null; this.status = STATUS.NOT_CONFIGURED; this.statesPending = false;
    }
    setStatus(text) { this.status = text; this.o.onUpdate('status', { text: text }); }
    start() {
      if (!this.o.token || !this.o.url) { this.setStatus(STATUS.NOT_CONFIGURED); return; }
      this.stopped = false; this.connect();
    }
    /** Stops for good (Forget, or the app hidden). An alarm on screen stays, with its status unknown. */
    stop() {
      this.stopped = true;
      if (this.timer !== null) { this.o.clearTimer(this.timer); this.timer = null; }
      const ws = this.socket; this.socket = null;
      if (ws) { try { ws.close(); } catch (e) { /* already closed */ } }
      this.policy.connection(false);
      if (this.policy.emergency()) this.o.onUpdate('alarms', { fresh: false });
    }
    connect() {
      if (this.stopped) return;
      this.setStatus(STATUS.CONNECTING);
      let ws;
      try { ws = new this.o.WebSocket(this.o.url); } catch (e) { this.lost(null); return; }
      this.socket = ws;
      const self = this;
      ws.onopen = function () { if (self.socket === ws) self.setStatus(STATUS.REACHABLE); };
      ws.onmessage = function (e) { if (self.socket === ws) self.handle(ws, e.data); };
      ws.onclose = function () { self.lost(ws); };
      ws.onerror = function () { /* onclose follows */ };
    }
    lost(ws) {
      if (this.stopped || (ws && ws !== this.socket)) return;
      this.socket = null;
      this.policy.connection(false);
      if (this.policy.emergency()) this.o.onUpdate('alarms', { fresh: false });
      this.setStatus(STATUS.OFFLINE);
      const self = this;
      this.timer = this.o.setTimer(function () { self.timer = null; self.connect(); }, this.retry);
      this.retry = Math.min(30000, this.retry * 2);
    }
    send(ws, value) { try { ws.send(JSON.stringify(value)); } catch (e) { /* lost() follows */ } }
    handle(ws, text) {
      let value;
      try { value = JSON.parse(text); } catch (e) { return; }
      if (!value || typeof value !== 'object') return;
      const type = value.type;
      if (type === 'auth_required') this.send(ws, { type: 'auth', access_token: this.o.token });
      else if (type === 'auth_invalid') {
        this.stopped = true; this.socket = null;
        try { ws.close(); } catch (e) { /* closed */ }
        this.policy.connection(false);
        // An alarm already on screen stays, with its status unknown: losing access never clears an alarm.
        if (this.policy.emergency()) this.o.onUpdate('alarms', { fresh: false });
        this.setStatus(STATUS.AUTH_REQUIRED);
      } else if (type === 'auth_ok') {
        this.retry = 1000;
        this.setStatus(STATUS.AUTHORISED);
        this.send(ws, { id: 1, type: 'subscribe_events', event_type: 'state_changed' });
        if (this.o.reminders) this.send(ws, { id: 2, type: 'subscribe_events', event_type: 'unified_tv_reminder' });
        // An alarm already sounding when the TV connects or reconnects is shown straight away.
        if (this.o.alarms) { this.statesPending = true; this.send(ws, { id: STATES_ID, type: 'get_states' }); }
        else if (this.policy.forgetDetectors() !== Alerts.NONE) this.o.onUpdate('alarms', { fresh: false });   // alarms switched off
      } else if (type === 'result') {
        if (value.id === STATES_ID) {
          this.statesPending = false;
          const all = Alerts.detectorsIn(value, STATES_ID);
          if (all) { const r = this.policy.snapshot(all); this.o.onUpdate('alarms', { fresh: r === Alerts.NEW_ALARM }); }
        }
      } else if (type === 'event' && value.event) this.event(value.event);
    }
    event(event) {
      const data = event.data;
      if (!data || typeof data !== 'object') return;
      const type = event.event_type;
      // Alarms first, and before the clock check: the new state is true now, whatever the TV's clock says.
      if (type === 'state_changed' && this.o.alarms) {
        const after = data.new_state;
        const entity = String(data.entity_id || '');
        const attrs = after && after.attributes;
        const kind = after ? Alerts.alarmKind(entity, attrs ? attrs.device_class : '') : null;
        if (kind) {
          const r = this.policy.update(entity, kind, Alerts.name(attrs), after.state, Alerts.time(after.last_changed), false);
          if (r !== Alerts.NONE) this.o.onUpdate('alarms', { fresh: r === Alerts.NEW_ALARM });
          return;
        }
      }
      const now = this.o.now();
      const fired = Alerts.time(event.time_fired);
      if (!fired || fired > now + Alerts.SKEW || now - fired > Alerts.RELEVANT) return;
      if (type === 'state_changed') {
        const before = data.old_state, after = data.new_state;
        if (!before || !after) return;
        const entity = String(data.entity_id || '');
        const kind = Alerts.ringKind(entity, this.o.door || '', this.o.person || '', before.state, after.state, after.attributes);
        if (kind === Alerts.RING_PRESS) {
          // RING-03/04: a press gives the doorbell card; during an emergency it is kept for later, the latest only.
          if (this.policy.acceptRing(entity + ':' + after.last_changed, now)) {
            if (this.policy.emergency()) { this.policy.queueRing(now); this.o.onUpdate('alarms', { fresh: false }); }
            else this.o.onUpdate('ring', { entity: entity });
          }
        } else if (kind === Alerts.RING_PERSON) {
          // RING-02/04: a confirmed person is one toast per sensor per minute. During an emergency nothing is shown and
          // nothing is queued (a toast would cover the alarm). Ordinary motion never gets here (RING-01).
          if (!this.policy.emergency() && this.policy.acceptPerson(entity, now)) this.o.onUpdate('person', { entity: entity });
        }
      } else if (type === 'unified_tv_reminder' && this.o.reminders) {
        const target = typeof data.room === 'string' ? data.room : '';
        if (target && target !== (this.o.room || 'frame')) return;
        const text = typeof data.text === 'string' ? data.text.substring(0, 240) : '';
        if (this.policy.emergency()) return;   // spec 10.2: reminders arriving during an emergency are dropped, not held
        if (this.policy.ringing(now)) this.policy.defer(text, fired);
        else if (this.policy.reminderAllowed(fired, now) && text.trim()) this.o.onUpdate('reminder', { text: text });
      }
    }
  }

  return { STATUS: STATUS, STATES_ID: STATES_ID, hostKind: hostKind, endpoint: endpoint, endpointMessage: endpointMessage, Client: Client };
}));
