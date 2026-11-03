/*
 * Unified TV DLNA discovery proof of concept: the page. PROTOTYPE, not part of the Unified TV app.
 *
 * Runs six steps and shows each as PASS or FAIL with a plain reason and a result code:
 *   1 service startup, 2 localhost token check, 3 SSDP by four methods, 4 validation, 5 Browse, 6 the summary line.
 * The page talks only to the service on 127.0.0.1:18731 (the Content-Security-Policy allows nothing else). It never
 * shows an IP address. Text from the network is set with textContent only. ES5 for the 2021 Frame's engine.
 */
(function () {
  'use strict';
  var R = window.UTVPOC;
  var PORT = 18731;
  var BASE = 'http://127.0.0.1:' + PORT;
  var OPERATION = 'http://tizen.org/appcontrol/operation/pick';   // as in Samsung's SimpleService sample
  var TOKEN_KEY = 'utv-token';
  var STARTUP_LIMIT_MS = 20000, POLL_MS = 300;

  var cryptoObj = window.crypto || window.msCrypto;
  var token = R.makeToken(cryptoObj && cryptoObj.getRandomValues ? function (b) { return cryptoObj.getRandomValues(b); } : null);
  var results = {};
  var running = false;

  var STEPS = [
    { id: 'svc', title: '1 Service startup' }, { id: 'local', title: '2 Localhost and token' }, { id: 'ssdp', title: '3 SSDP discovery' },
    { id: 'valid', title: '4 Validation' }, { id: 'browse', title: '5 Browsing' }, { id: 'summary', title: '6 Summary' }
  ];
  var $ = function (id) { return document.getElementById(id); };
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; }
  function now() { return window.performance && performance.now ? performance.now() : Date.now(); }

  function render() {
    var list = $('steps');
    list.textContent = '';
    STEPS.forEach(function (s) {
      var r = results[s.id] || { status: running ? 'WAIT' : 'SKIP', detail: running ? 'Waiting…' : 'Not run.', code: '' };
      var li = el('li');
      li.appendChild(el('span', 'badge ' + r.status, r.status));
      li.appendChild(el('span', 'title', s.title));
      li.appendChild(el('span', 'detail', r.detail || R.reason(r.code)));
      li.appendChild(el('span', 'code', r.code || ''));
      list.appendChild(li);
    });
    $('summary').textContent = R.summaryLine(results);
  }
  function set(id, status, code, detail, extra) {
    var r = { status: status, code: code, detail: detail || R.reason(code) };
    if (extra) Object.keys(extra).forEach(function (k) { r[k] = extra[k]; });
    results[id] = r;
    render();
    return r;
  }

  /** GET on the service. auth: true for this launch's token, a string for a chosen token, false for none. */
  function get(path, auth, timeoutMs, done) {
    var x;
    var t = auth === true ? token : auth;
    var url = BASE + path + (t ? (path.indexOf('?') === -1 ? '?' : '&') + 't=' + encodeURIComponent(t) : '');
    try { x = new XMLHttpRequest(); x.open('GET', url, true); } catch (e) { done('NET', 0, null); return; }
    x.timeout = timeoutMs;
    x.onload = function () { var body = null; try { body = JSON.parse(x.responseText); } catch (e) { body = null; } done(null, x.status, body); };
    x.onerror = function () { done('NET', 0, null); };
    x.ontimeout = function () { done('TIMEOUT', 0, null); };
    try { x.send(null); } catch (e) { done('NET', 0, null); }
  }

  // ---------------------------------------------------------------- 1 service startup
  function startService(next) {
    var t0 = now();
    if (!window.tizen || !tizen.application) { set('svc', 'FAIL', 'SVC_NO_TIZEN'); finish(); return; }
    if (!token) { set('svc', 'FAIL', 'SVC_LAUNCH_ERR', 'This TV gave the page no secure random numbers, so no token could be made.'); finish(); return; }
    var pkg = 'UTVDLNAPOC';
    try { pkg = tizen.application.getCurrentApplication().appInfo.packageId; } catch (e) { /* keep the default */ }
    var serviceId = pkg + '.svc';
    var lastCode = 'SVC_NO_ANSWER';
    function poll() {
      get('/ping', true, 1500, function (err, status, body) {
        if (status === 200 && body && body.ok) { set('svc', 'PASS', 'SVC_PASS', 'Answered after ' + R.seconds(now() - t0) + '. Service ' + body.version + ', Node ' + body.node + (body.udp ? ', UDP available.' : ', no UDP.'), { ms: now() - t0, ping: body }); next(); return; }
        if (status === 403 && body && body.code === 'TOKEN_NOT_RECEIVED') lastCode = 'SVC_TOKEN_NOT_RECEIVED';
        else if (status === 403 && body && body.code === 'TOKEN_MISMATCH') lastCode = 'SVC_TOKEN_MISMATCH';
        if (now() - t0 > STARTUP_LIMIT_MS) { set('svc', 'FAIL', lastCode); finish(); return; }
        window.setTimeout(poll, POLL_MS);
      });
    }
    try {
      var control = new tizen.ApplicationControl(OPERATION, null, null, null, [new tizen.ApplicationControlData(TOKEN_KEY, [token])]);
      tizen.application.launchAppControl(control, serviceId, function () { poll(); }, function (e) {
        set('svc', 'FAIL', 'SVC_LAUNCH_ERR', R.reason('SVC_LAUNCH_ERR') + ' (' + ((e && e.name) || 'error') + ')'); finish();
      });
    } catch (e) { set('svc', 'FAIL', 'SVC_LAUNCH_ERR', R.reason('SVC_LAUNCH_ERR') + ' (' + ((e && e.name) || 'exception') + ')'); finish(); }
  }

  // ---------------------------------------------------------------- 2 localhost and token
  function checkLocal(next) {
    get('/ping', false, 3000, function (e1, s1, b1) {
      var wrong = R.makeToken(function (b) { for (var i = 0; i < b.length; i++) b[i] = (i * 37 + 11) & 255; return b; });
      get('/ping', wrong, 3000, function (e2, s2, b2) {
        var bound = results.svc && results.svc.ping && results.svc.ping.boundTo;
        if (s1 === 200 || s2 === 200) set('local', 'FAIL', 'LOCAL_TOKEN_NOT_ENFORCED');
        else if (s1 !== 403 || s2 !== 403 || !b2 || b2.code !== 'TOKEN_MISMATCH') set('local', 'FAIL', 'LOCAL_UNEXPECTED', R.reason('LOCAL_UNEXPECTED') + ' (' + s1 + ', ' + s2 + ')');
        else if (bound !== '127.0.0.1') set('local', 'FAIL', 'LOCAL_NOT_LOOPBACK');
        else set('local', 'PASS', 'LOCAL_PASS');
        next();
      });
    });
  }

  // ---------------------------------------------------------------- 3 SSDP and 4 validation
  function discover(next) {
    set('ssdp', 'WAIT', '', 'Searching four ways for about 15 seconds…');
    get('/discover', true, 40000, function (err, status, body) {
      if (!body || status !== 200) { set('ssdp', 'FAIL', 'SSDP_FAILED', R.reason('SSDP_FAILED') + ' (' + (err || status) + ')'); set('valid', 'FAIL', 'VALID_NONE'); set('browse', 'SKIP', 'BROWSE_SKIPPED'); finish(); return; }
      if (!body.ok) { set('ssdp', 'FAIL', body.code); set('valid', 'FAIL', 'VALID_NONE'); set('browse', 'SKIP', 'BROWSE_SKIPPED'); finish(); return; }
      var m = body.methods;
      var any = R.METHODS.some(function (k) { return m[k] && m[k].answers > 0; });
      var detail = R.METHODS.map(function (k) {
        var x = m[k] || {};
        return k + ' ' + (x.answers || 0) + (R.methodOk(x.code) ? '' : ' ' + x.code);
      }).join(' · ') + ' · swept ' + (body.sweepHosts || 0) + ' addresses' +
        (m.notify && m.notify.listeningMs ? ' · listening ' + Math.round(m.notify.listeningMs / 1000) + ' s' : '');
      set('ssdp', any ? 'PASS' : 'FAIL', any ? 'SSDP_FOUND' : 'SSDP_NOTHING', detail, { methods: m });
      var servers = body.servers || [];
      var rejected = (body.rejected || []).map(function (r) { return r.code; });
      set('valid', servers.length ? 'PASS' : 'FAIL', servers.length ? 'VALID_PASS' : 'VALID_NONE',
        servers.length + ' confirmed' + (rejected.length ? ', ' + rejected.length + ' set aside: ' + rejected.join(', ') : ''), { count: servers.length });
      showServers(servers);
      if (servers.length) next(servers[0]); else { set('browse', 'SKIP', 'BROWSE_SKIPPED'); finish(); }
    });
  }
  function showServers(servers) {
    var box = $('servers');
    box.textContent = '';
    box.hidden = !servers.length;
    servers.forEach(function (s) {
      box.appendChild(el('h2', '', s.name + (s.model ? ' — ' + s.model : '')));
      box.appendChild(el('p', '', 'UDN ' + (s.udn || 'not given') + ' · found by ' + s.foundBy.map(function (k) { return R.METHOD_NAMES[k] || k; }).join(', ')));
    });
  }

  // ---------------------------------------------------------------- 5 browsing
  function browse(server) {
    get('/browse?s=' + server.index + '&id=0', true, 15000, function (err, status, r) {
      if (!r || !r.ok) { set('browse', 'FAIL', 'BROWSE_FAILED', R.reason('BROWSE_FAILED') + ' (' + ((r && r.code) || err || status) + ')'); finish(); return; }
      var names = r.containers.map(function (c) { return c.title; });
      if (!names.length) { set('browse', 'FAIL', 'BROWSE_EMPTY', R.reason('BROWSE_EMPTY') + ' (' + r.items + ' items)', { folders: 0 }); finish(); return; }
      var first = r.containers[0];
      get('/browse?s=' + server.index + '&id=' + encodeURIComponent(first.id), true, 15000, function (e2, s2, r2) {
        var inner = r2 && r2.ok ? ' Inside “' + first.title + '”: ' + r2.containers.length + ' folders, ' + r2.items + ' items.' : ' Opening “' + first.title + '” failed (' + ((r2 && r2.code) || e2 || s2) + ').';
        set('browse', 'PASS', 'BROWSE_PASS', names.length + ' folders: ' + names.slice(0, 8).join(', ') + (names.length > 8 ? '…' : '') + '.' + inner, { folders: names.length });
        finish();
      });
    });
  }

  function finish() {
    running = false;
    STEPS.forEach(function (s) { if (!results[s.id] && s.id !== 'summary') results[s.id] = { status: 'SKIP', code: '', detail: 'Not reached.' }; });
    results.summary = { status: 'PASS', code: '', detail: 'Photograph this screen, including the line below.' };
    render();
  }
  function run() {
    if (running) return;
    running = true; results = {};
    $('servers').hidden = true;
    render();
    startService(function () { checkLocal(function () { discover(browse); }); });
  }
  /** The service's own log, newest last, for when the TV doesn't allow sdb dlog. It may name addresses on the home network. */
  function toggleLog() {
    var box = $('log');
    if (!box.hidden) { box.hidden = true; return; }
    box.hidden = false;
    box.textContent = 'Reading the service log…';
    get('/log', true, 3000, function (err, status, body) {
      box.textContent = body && body.ok ? 'Service log (newest last). It may show addresses on your home network: cover them before sharing a photo.\n\n' +
        body.lines.slice(-30).join('\n') : 'The service log isn\'t available (' + (err || status) + ').';
    });
  }
  function quit() {
    get('/stop', true, 1500, function () {
      try { tizen.application.getCurrentApplication().exit(); } catch (e) { /* a desktop browser */ }
    });
  }

  document.addEventListener('keydown', function (e) {
    if (e.keyCode === 13) run();
    else if (e.keyCode === 40) toggleLog();
    else if (e.keyCode === 10009 || e.keyCode === 8 || e.keyCode === 27) quit();
  });
  render();
  run();
}());
