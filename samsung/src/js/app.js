/*
 * Unified TV for Samsung Frame: the page. Wires the pure modules (mesh, focus, keys, nav, apps, alerts, wallpaper,
 * settings, secrets, player, dlna) to the DOM and the Tizen APIs, all feature-detected.
 *
 * Status: implemented_unverified. None of this has run on the owner's Frame yet. Every platform call is guarded so the
 * page also opens in a desktop browser for layout work, where it says it is a browser simulation.
 */
(function () {
  'use strict';
  const U = window.UTV;
  const env = { tizen: window.tizen, webapis: window.webapis };
  const W = 1920, H = 1080;
  const $ = function (id) { return document.getElementById(id); };
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }
  function mono() { return window.performance && performance.now ? performance.now() : Date.now(); }
  function hhmm(ms) { return U.Clock.format(ms); }

  // ---------------------------------------------------------------- state
  function safeStorage() { try { return window.localStorage; } catch (e) { return null; } }
  const store = new U.Settings.Store(safeStorage());
  let S = store.load();
  const vault = new U.Secrets.Vault(env);
  const nav = new U.Nav.Stack();
  const policy = new U.Alerts.Policy();
  const crossfade = new U.Mesh.Crossfade();
  const press = new U.Keys.PressTracker();
  const keyInfo = U.Keys.register(env);
  let palettes = window.UTV_PALETTE_DATA || loadJson('palettes.json');
  let installed = null;
  let dock = [];
  let focus = U.Focus.initial(0, 0);
  let ha = null, haStatus = U.Ha.STATUS.NOT_CONFIGURED;
  let moving = null;
  let fallbackStill = false;
  const diagnostics = [];
  let pressTimer = null, idleTimer = null, doorbellTimer = null, toastTimer = null, clockTimer = null, slideTimer = null;
  let doorbellAt = 0;
  let wp = { active: false, list: [], index: 0, count: 0 };
  let alarmIndex = 0, alarmEntity = null;   // focus follows the detector, not its position in the list
  const simulation = !env.tizen;

  function save() { store.save(S); }
  function loadJson(path) {
    try {
      const x = new XMLHttpRequest();
      x.open('GET', path, false); x.send(null);
      return x.status === 200 || (x.status === 0 && x.responseText) ? JSON.parse(x.responseText) : null;
    } catch (e) { return null; }
  }

  // ---------------------------------------------------------------- ambient mesh (spec 7.3-7.6, 32.4)
  const meshes = { a: $('mesh-a'), b: $('mesh-b') };
  const driftStyle = document.createElement('style');
  document.head.appendChild(driftStyle);
  const keyframesMade = {};
  $('shade').style.background = U.Mesh.SHADE;
  function systemReduced() { try { return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches); } catch (e) { return false; } }
  function motion() {
    const m = U.Mesh.motion(S.motion, systemReduced());
    if (fallbackStill) m.drift = false;
    return m;
  }
  function renderLayer(layer, key) {
    layer.innerHTML = '';
    layer.setAttribute('data-key', key === null ? '' : key);
    if (key === null || !palettes) { layer.style.backgroundColor = '#0c121b'; return; }
    const p = U.Mesh.palette(palettes, key);
    const m = motion();
    const t = mono() / 1000;
    layer.style.backgroundColor = U.Mesh.rgb(p.base);
    p.lights.forEach(function (light, i) {
      const box = U.Mesh.lightBox(light, W, H);
      const d = el('div', 'light');
      d.style.left = box.left + 'px'; d.style.top = box.top + 'px';
      d.style.width = box.size + 'px'; d.style.height = box.size + 'px';
      d.style.background = U.Mesh.lightGradient(light);
      if (m.drift) {
        const name = 'utv-' + (U.Mesh.known(palettes, key) ? key : 'neutral').replace(/[^a-z0-9]/gi, '') + '-' + i;
        if (!keyframesMade[name]) { driftStyle.appendChild(document.createTextNode(U.Mesh.keyframesCss(name, light, W, H))); keyframesMade[name] = true; }
        d.style.animationName = name;
        d.style.animationDuration = light.period + 's';
        d.style.animationDelay = U.Mesh.driftDelay(light, t) + 's';
      }
      layer.appendChild(d);
    });
  }
  function applyOps(ops) {
    ops.forEach(function (op) {
      const layer = meshes[op.layer];
      layer.style.zIndex = op.role === 'base' ? '1' : '2';
      const want = op.key === null ? '' : op.key;
      if (layer.getAttribute('data-key') !== want) renderLayer(layer, op.key);
      if (!op.ms) { layer.style.transitionDuration = '0ms'; layer.style.opacity = String(op.opacity); void layer.offsetWidth; }
      else { layer.style.transitionDuration = op.ms + 'ms'; layer.style.opacity = String(op.opacity); }
    });
  }
  /** The focused item's palette: starts at once, 400 ms, latest focus wins (Crossfade retargets). */
  function setScene(key) { applyOps(crossfade.commit(key, mono(), motion().fadeMs)); }
  /** A motion setting changed: redraw both layers in place, without a fade. */
  function redrawMesh() {
    ['a', 'b'].forEach(function (k) { const key = meshes[k].getAttribute('data-key'); renderLayer(meshes[k], key ? key : null); });
  }
  /** Spec 7.5: if this TV draws the drift too slowly, keep a still mesh in the right palette instead. */
  function watchFrames() {
    if (!window.requestAnimationFrame || !motion().drift) return;
    const gaps = []; let last = 0;
    function frame(t) {
      if (last) gaps.push(t - last);
      last = t;
      if (gaps.length < 90) { window.requestAnimationFrame(frame); return; }
      if (U.Mesh.shouldFallBack(gaps)) {
        fallbackStill = true; redrawMesh();
        diagnostics.push('The Home background was kept still because this TV drew it slowly.');
      }
    }
    window.setTimeout(function () { window.requestAnimationFrame(frame); }, 2500);
  }

  function applyAppearance() {
    const b = document.body;
    b.classList.toggle('contrast', !!S.highContrast);
    b.classList.toggle('solid', !!S.reducedTransparency);
    b.classList.toggle('reduce', motion().setting === 'reduce');
    redrawMesh();
  }

  // ---------------------------------------------------------------- clock and header
  function tickClock() {
    const now = Date.now();
    $('clock').textContent = hhmm(now);
    if (wp.active) renderWallpaperClock();
    clockTimer = window.setTimeout(tickClock, U.Clock.untilNextMinute(now) + 50);
  }
  $('vpn').textContent = U.LiveTv.vpnLabel();

  // ---------------------------------------------------------------- dock
  let tiles = [], first = 0;
  function displayName(id) {
    const e = U.Apps.entry(id);
    if (e && e.installed && installed) {
      const found = installed.filter(function (a) { return a.id === e.appId; })[0];
      if (found && found.name) return found.name;
    }
    return e ? e.name : id;
  }
  function buildDock() {
    dock = U.Apps.dockOrder(S.dock).map(function (id) { return { id: id, name: displayName(id), entry: U.Apps.entry(id) }; });
    focus = { row: focus.row, dock: Math.max(0, Math.min(Math.max(0, dock.length - 1), focus.dock)), util: focus.util };
    renderDock(true);
  }
  function accentCss(id) {
    const p = U.Mesh.palette(palettes, id);
    const c = p ? U.Mesh.colour(p.accent) : { r: 124, g: 147, b: 174 };
    return 'radial-gradient(circle closest-side, rgba(' + c.r + ',' + c.g + ',' + c.b + ',0.36), rgba(' + c.r + ',' + c.g + ',' + c.b + ',0))';
  }
  function renderDock(rebuild) {
    const order = moving ? moving.order : dock.map(function (d) { return d.id; });
    const count = Math.max(1, order.length);
    const visible = Math.min(6, count);
    const index = moving ? moving.index : focus.dock;
    const nextFirst = Math.max(0, Math.min(count - visible, index - Math.floor(visible / 2)));
    const dockEl = $('dock');
    if (rebuild || nextFirst !== first || tiles.length !== visible) {
      first = nextFirst;
      const full = visible * 190 + (visible - 1) * 24 + 70;
      dockEl.style.left = Math.round((W - full) / 2) + 'px';
      dockEl.style.width = full + 'px';
      dockEl.innerHTML = ''; tiles = [];
      for (let i = first; i < first + visible; i++) {
        const id = order[i];
        const e = id ? U.Apps.entry(id) : null;
        const t = el('div', 'tile');
        t.style.left = (35 + (i - first) * 214) + 'px';
        const halo = el('div', 'halo'); halo.style.background = accentCss(id || 'neutral'); t.appendChild(halo);
        const face = el('div', 'face');
        const colours = e ? e.tile : ['#2A3140', '#11151D'];
        face.style.background = 'linear-gradient(to bottom, ' + colours[0] + ', ' + colours[1] + ')';
        const symbol = e ? e.symbol : '+';
        face.appendChild(el('div', 'symbol' + (symbol.length > 2 ? ' small' : ''), symbol));
        t.appendChild(face);
        t.appendChild(el('div', 'label', id ? displayName(id) : 'Add apps'));
        if (id === 'live') t.classList.add('locked');
        dockEl.appendChild(t); tiles.push(t);
      }
    }
    tiles.forEach(function (t, k) {
      const i = first + k;
      t.classList.toggle('focused', !moving && focus.row === 'dock' && focus.dock === i);
      t.classList.toggle('moving', !!moving && moving.index === i);
    });
  }
  function renderFocus() {
    renderDock(false);
    $('util-voice').classList.toggle('focused', focus.row === 'util' && focus.util === 0);
    $('util-settings').classList.toggle('focused', focus.row === 'util' && focus.util === 1);
    $('more').classList.toggle('focused', focus.row === 'more');
    // The title, halo and light change together (spec 32.6).
    const scene = U.Focus.scene(focus, dock);
    $('title').textContent = moving ? 'Move ' + displayName(moving.order[moving.index]) : scene.title;
    setScene(scene.palette);
  }

  // ---------------------------------------------------------------- sheets (dialogs, menus, pickers)
  function showMessage(text, ms) {
    const t = $('toast');
    t.textContent = text; t.hidden = false;
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () { t.hidden = true; }, ms || 7000);
  }
  function focusable(row) { return row && row.kind !== 'info' && row.kind !== 'heading'; }
  function firstFocusable(rows, from) {
    for (let i = from || 0; i < rows.length; i++) if (focusable(rows[i])) return i;
    return -1;
  }
  function stepFocus(rows, index, direction) {
    const d = direction === 'up' ? -1 : 1;
    for (let i = index + d; i >= 0 && i < rows.length; i += d) if (focusable(rows[i])) return i;
    return index;
  }
  function renderRows(container, rows, index, active) {
    container.innerHTML = '';
    let focusedEl = null;
    rows.forEach(function (row, i) {
      const r = el('div', 'row ' + (row.kind === 'info' ? 'info' : row.kind === 'heading' ? 'heading' : '') + (row.small ? ' small' : '') + (row.disabled ? ' disabled' : '') + (row.selected ? ' selected' : ''));
      if (row.kind === 'info' || row.kind === 'heading') r.textContent = row.label;
      else {
        r.appendChild(el('span', 'label', row.label));
        if (row.value !== undefined && row.value !== null && row.value !== '') r.appendChild(el('span', 'value', row.value));
      }
      if (active && i === index) { r.classList.add('focused'); focusedEl = r; }
      container.appendChild(r);
    });
    if (focusedEl && focusedEl.scrollIntoView) { try { focusedEl.scrollIntoView({ block: 'nearest' }); } catch (e) { focusedEl.scrollIntoView(false); } }
  }

  /** opts: { title, text, rows: [{label, value, run}], onBack, input: {secret, value, placeholder, save(text)} } */
  function openSheet(opts) {
    const layer = nav.push('sheet');
    const ctl = { opts: opts, index: 0 };
    layer.ctl = ctl;
    ctl.key = function (a) {
      const rows = ctl.rows;
      if (a === 'up' || a === 'down') {
        if (opts.input && a === 'up' && ctl.index === firstFocusable(rows)) { editField(ctl); return; }
        ctl.index = stepFocus(rows, ctl.index, a); ctl.render();
      } else if (a === 'ok') { const row = rows[ctl.index]; if (row && row.run) row.run(); }
      else if (a === 'back') { closeTop(); if (opts.onBack) opts.onBack(); }
    };
    ctl.render = function () {
      const box = $('sheet');
      box.innerHTML = '';
      box.appendChild(el('h2', '', opts.title));
      if (opts.text) String(opts.text).split('\n\n').forEach(function (p) { box.appendChild(el('p', '', p)); });
      if (opts.input) {
        const input = el('input');
        input.type = opts.input.secret ? 'password' : 'text';
        input.setAttribute('autocomplete', 'off'); input.setAttribute('spellcheck', 'false');
        input.placeholder = opts.input.placeholder || '';
        input.value = ctl.draft !== undefined ? ctl.draft : (opts.input.value || '');
        box.appendChild(input); ctl.inputEl = input;
      }
      const list = el('div', ''); box.appendChild(list);
      ctl.rows = opts.rows;
      if (firstFocusable(ctl.rows) !== -1 && !focusable(ctl.rows[ctl.index])) ctl.index = firstFocusable(ctl.rows);
      renderRows(list, ctl.rows, ctl.index, !ctl.editing);
      box.hidden = false;
    };
    ctl.close = function () { $('sheet').hidden = true; $('sheet').innerHTML = ''; };
    ctl.render();
    if (opts.input) editField(ctl);
    return ctl;
  }
  /** Text entry uses the TV's own on-screen keyboard: focusing a real input opens it. */
  function editField(ctl) {
    if (!ctl.inputEl) return;
    ctl.editing = true; ctl.render();
    try { ctl.inputEl.focus(); } catch (e) { /* the keyboard may not open in a desktop browser */ }
  }
  function fieldDone(commit) {
    const top = nav.top();
    const ctl = top.ctl;
    if (!ctl || !ctl.inputEl) return;
    ctl.draft = ctl.inputEl.value;
    ctl.editing = false;
    try { ctl.inputEl.blur(); } catch (e) { /* nothing focused */ }
    ctl.render();
    if (!commit) { closeTop(); if (ctl.opts.onBack) ctl.opts.onBack(); }
  }
  function inputSheet(title, text, input, after) {
    const ctl = openSheet({
      title: title, text: text, input: input,
      rows: [
        { label: 'Save', run: function () { const v = ctl.inputEl ? ctl.inputEl.value : ctl.draft; closeTop(); input.save(String(v || '').trim()); if (after) after(); } },
        { label: 'Cancel', run: function () { closeTop(); if (after) after(); } }
      ]
    });
  }
  function confirmSheet(title, text, yesLabel, yes) {
    openSheet({ title: title, text: text, rows: [
      { label: yesLabel, run: function () { closeTop(); yes(); } },
      { label: 'Cancel', run: function () { closeTop(); } }
    ] });
  }
  function infoSheet(title, text) { openSheet({ title: title, text: text, rows: [{ label: 'Back', run: function () { closeTop(); } }] }); }

  /** Back, one layer. The layer underneath is redrawn with the focus it had. */
  function closeTop() {
    const top = nav.top();
    if (nav.atRoot()) return;
    if (top.ctl && top.ctl.close) top.ctl.close();
    nav.back();
    const now = nav.top();
    if (now.ctl && now.ctl.resume) now.ctl.resume();
    else if (now.ctl && now.ctl.render) now.ctl.render();
  }

  // ---------------------------------------------------------------- Home
  const homeLayer = nav.top();
  homeLayer.ctl = {
    handlesLong: true,
    key: function (a) {
      if (moving) { moveKey(a); return; }
      if (a === 'left' || a === 'right' || a === 'up' || a === 'down') { focus = U.Focus.move(focus, a, dock.length); renderFocus(); return; }
      if (a === 'ok') activate();
      else if (a === 'long') { if (focus.row === 'dock' && dock.length) tileMenu(); else activate(); }
      // Back at Home does nothing: Unified TV never closes itself from an ordinary Back press (PRODUCT_SPEC 30.3).
    },
    resume: function () { showHome(true); renderFocus(); }
  };
  function showHome(visible) {
    $('home').hidden = !visible;
    $('page').hidden = true;
  }
  function activate() {
    const t = U.Focus.target(focus);
    if (t.kind === 'util' && t.id === 'voice') { infoSheet('Voice', 'Voice isn\'t available on this TV yet. Everything works with the remote.'); return; }
    if (t.kind === 'util' && t.id === 'settings') { openSettings(); return; }
    if (t.kind === 'more') { openLibrary(); return; }
    if (!dock.length) { openApps(); return; }
    openTile(dock[t.index].id);
  }
  function openTile(id) {
    if (U.LiveTv.guards(id)) { const d = U.LiveTv.decide('tile'); infoSheet(d.title, d.message); return; }
    if (U.Apps.availability(id) === 'unavailable') { showMessage(U.Apps.failure(id, 'unavailable')); return; }
    const r = U.Apps.resolve(id, installed, S.appChoices);
    if (!r.appId) { showMessage(U.Apps.failure(id, 'not-found')); return; }
    saveUi();
    U.Apps.launch(env, [r.appId].concat(r.tryAlso || []), function (ok, reason) { if (!ok) showMessage(U.Apps.failure(id, reason)); });
  }
  function tileMenu() {
    const item = dock[focus.dock];
    openSheet({ title: item.name, rows: [
      { label: 'Open', run: function () { closeTop(); openTile(item.id); } },
      { label: 'Move', run: function () { closeTop(); startMove(); } },
      { label: 'Remove from Home', value: 'The app stays installed', run: function () {
        closeTop(); S.dock = U.Apps.dockRemove(dock.map(function (d) { return d.id; }), item.id); save(); buildDock(); renderFocus();
        showMessage(item.name + ' was removed from Home. Nothing was uninstalled.');
      } },
      { label: 'Add an app', run: function () { closeTop(); openApps(); } },
      { label: 'Cancel', run: function () { closeTop(); } }
    ] });
  }
  function startMove() { moving = { original: dock.map(function (d) { return d.id; }), order: dock.map(function (d) { return d.id; }), index: focus.dock }; renderFocus(); }
  function moveKey(a) {
    if (a === 'left' || a === 'right') { const r = U.Focus.shift(moving.order, moving.index, a); moving.order = r.order; moving.index = r.index; renderFocus(); }
    else if (a === 'ok' || a === 'long') { S.dock = moving.order.join(','); save(); focus.dock = moving.index; moving = null; buildDock(); renderFocus(); }
    else if (a === 'back') { moving = null; buildDock(); renderFocus(); }   // cancelled: nothing saved (AT-58)
  }
  function saveUi() { S.ui.dockIndex = focus.dock; save(); }

  // ---------------------------------------------------------------- pages
  /** Opens a full page with a single list (wide) or Settings' two columns. */
  function openPage(title, note, build, name) {
    const layer = nav.push(name || 'apps');
    const ctl = { index: -1, title: title };
    layer.ctl = ctl;
    ctl.render = function () {
      const page = $('page');
      page.innerHTML = '';
      page.appendChild(el('h1', '', title));
      if (note) page.appendChild(el('div', 'note', note));
      const cols = el('div', 'columns'); const panel = el('div', 'panel wide glass'); const rows = el('div', 'rows');
      panel.appendChild(rows); cols.appendChild(panel); page.appendChild(cols);
      ctl.rows = build();
      if (ctl.index < 0 || !focusable(ctl.rows[ctl.index])) ctl.index = firstFocusable(ctl.rows);
      renderRows(rows, ctl.rows, ctl.index, true);
      $('home').hidden = true; page.hidden = false;
    };
    ctl.key = function (a) {
      if (a === 'up' || a === 'down') { ctl.index = stepFocus(ctl.rows, ctl.index, a); ctl.render(); }
      else if (a === 'ok') { const r = ctl.rows[ctl.index]; if (r && r.run) r.run(); }
      else if (a === 'back') closeTop();
    };
    ctl.close = function () { $('page').hidden = true; $('home').hidden = false; };
    ctl.render();
    return ctl;
  }

  function openApps() {
    const ctl = openPage('Add an app', 'Adding an app only puts it on Home. Removing it later never uninstalls it.', function () {
      const order = dock.map(function (d) { return d.id; });
      const rows = [{ kind: 'heading', label: 'Unified TV apps' }];
      ['netflix', 'disney', 'bbc', 'live', 'music', 'tvplus', 'smartutube'].forEach(function (id) {
        if (order.indexOf(id) !== -1) return;
        const e = U.Apps.entry(id);
        if (e.unavailable) rows.push({ label: e.name, value: 'Not available on this TV', disabled: true, run: function () { showMessage(e.unavailable); } });
        else rows.push({ label: e.name, value: id === 'live' ? 'Locked on this TV' : 'Add', run: function () { add(id); } });
      });
      rows.push({ kind: 'heading', label: 'Installed on this TV' });
      if (!installed) rows.push({ kind: 'info', label: 'This TV didn\'t share its list of installed apps.' });
      else installed.slice(0, 60).forEach(function (a) {
        const id = 'app:' + a.id;
        if (order.indexOf(id) === -1 && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(a.id)) rows.push({ label: a.name || a.id, value: 'Add', run: function () { add(id); } });
      });
      return rows;
    });
    function add(id) {
      S.dock = U.Apps.dockAdd(dock.map(function (d) { return d.id; }), id); save(); buildDock();
      showMessage(displayName(id) + ' is on Home.'); ctl.render();
    }
  }

  // ---------------------------------------------------------------- Settings (six categories, one changing right panel)
  function row(label, value, run, extra) { const r = { label: label, value: value, run: run }; if (extra) Object.keys(extra).forEach(function (k) { r[k] = extra[k]; }); return r; }
  function info(text) { return { kind: 'info', label: text }; }
  function heading(text) { return { kind: 'heading', label: text }; }
  function toggle(label, get, set) { return row(label, get() ? 'On' : 'Off', function () { set(!get()); save(); }); }
  function choice(label, options, get, set) {
    const i = Math.max(0, options.map(function (o) { return o[0]; }).indexOf(get()));
    return row(label, options[i][1], function () { set(options[(i + 1) % options.length][0]); save(); });
  }
  function nas() { const raw = vault.get('nas'); try { const v = raw ? JSON.parse(raw) : null; return v && typeof v === 'object' ? v : null; } catch (e) { return null; } }
  function saveNas(value, done) { vault.put('nas', value ? JSON.stringify(value) : '', done); }
  function haLine() { return S.ha.enabled ? haStatus : U.Ha.STATUS.NOT_CONFIGURED; }

  const CATEGORIES = [
    { name: 'Connections', rows: function () {
      const tokenSaved = !!vault.get('ha-token');
      const ep = U.Ha.endpoint(S.ha);
      const n = nas();
      const r = [heading('Home Assistant'), info('Status: ' + haLine()),
        toggle('Use Home Assistant', function () { return S.ha.enabled; }, function (v) { S.ha.enabled = v; restartHa(); }),
        row('Address', S.ha.host || 'Not set', function () {
          inputSheet('Home Assistant address', 'A name or IP address on your home network, without http:// or a path.',
            { value: S.ha.host, placeholder: 'homeassistant.local', save: function (v) { S.ha.host = v; save(); restartHa(); } }, renderSettings);
        }),
        row('Port', String(S.ha.port), function () {
          inputSheet('Port', 'Usually 8123.', { value: String(S.ha.port), save: function (v) { const p = Number(v); if (p >= 1 && p <= 65535 && Math.floor(p) === p) { S.ha.port = p; save(); restartHa(); } else showMessage(U.Ha.endpointMessage('port')); } }, renderSettings);
        }),
        choice('Connection', [[true, 'HTTPS (encrypted)'], [false, 'HTTP (home network only)']], function () { return S.ha.https; }, function (v) { S.ha.https = v; restartHa(); })];
      if (!S.ha.https) {
        r.push(row('Trust HTTP for this address', S.ha.httpConsentHost && S.ha.httpConsentHost === S.ha.host ? 'Trusted' : 'Not trusted', function () {
          if (!S.ha.host) { showMessage(U.Ha.endpointMessage('host')); return; }
          confirmSheet('Trust HTTP for this address?', 'HTTP isn\'t encrypted. Only allow it for a Home Assistant on your own home network.\n\nThis applies to "' + S.ha.host + '" only.',
            'Trust this address', function () { S.ha.httpConsentHost = S.ha.host; save(); restartHa(); renderSettings(); });
        }));
      }
      r.push(row('Access token', tokenSaved ? 'Saved' : 'Not saved', function () {
        inputSheet('Home Assistant access token', 'Create a long-lived access token in your Home Assistant profile. It is never shown again here.\n\n' + vault.label(),
          { secret: true, value: '', placeholder: 'Paste or type the token', save: function (v) {
            if (!v) return;
            vault.put('ha-token', v, function (kept) { showMessage(kept ? 'Token saved.' : 'Token kept only until Unified TV closes: secure storage isn\'t available.'); restartHa(); renderSettings(); });
          } }, renderSettings);
      }));
      r.push(info(vault.label()));
      if (!ep.ok && S.ha.host) r.push(info(U.Ha.endpointMessage(ep.error)));
      r.push(row('Test connection', '', function () { restartHa(); showMessage('Connecting to Home Assistant…'); }));
      r.push(row('Forget Home Assistant', '', function () {
        confirmSheet('Forget Home Assistant?', 'This removes the address, the token and the trust choice from this TV.', 'Forget', function () {
          vault.forget('ha-token'); S.ha = U.Settings.defaults().ha; save(); restartHa(); renderSettings();
        });
      }));
      r.push(heading('Films & TV (your NAS)'));
      r.push(info(U.Dlna.FIND_YOUR_NAS));
      r.push(info(n && n.name ? 'Media server: ' + n.name + (n.roots && n.roots.length ? ' · ' + n.roots.length + ' of 3 folders chosen' : ' · no folders chosen') : 'No media server set.'));
      r.push(row('Media server address and folders', 'In TV & performance, Advanced', function () { openCategory(4); }));
      return r;
    } },
    { name: 'Profiles', rows: function () {
      return [info('Profiles aren\'t set up on this TV yet. Everything here is the same for everyone.'),
        info('A child profile would need its own restricted media account on the NAS. That isn\'t built for the Frame yet, and Unified TV can\'t change accounts inside Netflix, Disney+ or other apps.')];
    } },
    { name: 'Notifications', rows: function () {
      return [info('This TV only shows alerts while Unified TV is open and on screen. It can\'t show them over other apps, in Art Mode or when the TV is off. It adds to your smoke and carbon monoxide alarms; it never replaces them.'),
        heading('Smoke and carbon monoxide'),
        toggle('Smoke and carbon monoxide alarms', function () { return S.alerts.alarms; }, function (v) { S.alerts.alarms = v; restartHa(); }),
        row('Test the alarm screen', 'TEST', function () { testSheet(); }),
        heading('Doorbell'),
        row('Doorbell press', S.alerts.door || 'Not set', function () {
          inputSheet('Doorbell entity', 'The Home Assistant entity for a doorbell press, for example a binary_sensor or event entity.', { value: S.alerts.door, placeholder: 'event.front_door_ding', save: function (v) { S.alerts.door = /^(binary_sensor|event)\.[a-z0-9_]{1,80}$/.test(v) ? v : ''; save(); restartHa(); } }, renderSettings);
        }),
        row('Person detected', S.alerts.person || 'Not set', function () {
          inputSheet('Person detection entity', 'The Home Assistant entity for person detection (not general motion).', { value: S.alerts.person, placeholder: 'binary_sensor.front_door_person', save: function (v) { S.alerts.person = /^(binary_sensor|event)\.[a-z0-9_]{1,80}$/.test(v) ? v : ''; save(); restartHa(); } }, renderSettings);
        }),
        info('The doorbell card has no live camera on the Frame yet. A smoke or carbon monoxide alarm always comes first.'),
        heading('Reminders'),
        toggle('Alexa reminders', function () { return S.alerts.reminders; }, function (v) { S.alerts.reminders = v; restartHa(); })];
    } },
    { name: 'Appearance', rows: function () {
      const w = S.wallpaper;
      return [heading('Home'),
        choice('Home background', [['default', 'Gentle movement'], ['still', 'Keep the Home background still'], ['reduce', 'Reduce motion'], ['light', 'Lightweight rendering']],
          function () { return S.motion; }, function (v) { S.motion = v; applyAppearance(); }),
        toggle('High contrast', function () { return S.highContrast; }, function (v) { S.highContrast = v; applyAppearance(); }),
        toggle('Reduced transparency', function () { return S.reducedTransparency; }, function (v) { S.reducedTransparency = v; applyAppearance(); }),
        heading('Wallpaper'),
        info(U.Wallpaper.NOT_ART_MODE),
        choice('Wallpaper', [['artwork', 'Artwork'], ['off', 'Off']], function () { return w.source; }, function (v) { w.source = v; resetIdle(); }),
        choice('Clock', [['auto', U.Wallpaper.clockLabel('auto')], ['simple', 'Simple clock'], ['none', 'No clock']], function () { return w.clock; }, function (v) { w.clock = v; }),
        choice('Start after', U.Wallpaper.IDLE_MINUTES.map(function (m) { return [m, U.Wallpaper.idleLabel(m)]; }), function () { return w.idle; }, function (v) { w.idle = v; resetIdle(); }),
        choice('Change the picture every', U.Wallpaper.SLIDE_SECONDS.map(function (s) { return [s, U.Wallpaper.slideLabel(s)]; }), function () { return w.slide; }, function (v) { w.slide = v; }),
        toggle('12-hour clock', function () { return w.hour12; }, function (v) { w.hour12 = v; }),
        toggle('Show the date', function () { return w.date; }, function (v) { w.date = v; }),
        choice('Dimming', [[0, 'Off'], [20, '20 %'], [40, '40 %']], function () { return w.dim; }, function (v) { w.dim = v; }),
        row('Start Wallpaper now', wp.list.length ? '' : 'No artwork in this build', function () {
          if (!wp.list.length) { showMessage('There is no artwork in this build, so Wallpaper can\'t start.'); return; }
          nav.home(); settingsCtl = null; $('sheet').hidden = true; $('page').hidden = true; showHome(true); enterWallpaper();
        }),
        info('The depth clock needs a prepared mask for a picture. None is prepared yet, so the simple clock is used.')];
    } },
    { name: 'TV & performance', rows: function () {
      const r = [heading('About this TV'), info('Samsung The Frame (2021), QE50LS03AAUXXU. ' + (simulation ? 'Browser simulation: the TV\'s own features aren\'t here.' : 'Nothing below has been tested on this TV yet.'))];
      U.Capabilities.list().forEach(function (c) { r.push(row(c.label, U.Capabilities.label(c.value), function () { infoSheet(c.label, 'Status: ' + U.Capabilities.label(c.value) + '.\n\nWhat we expect: ' + c.expected + '.'); }, { small: true })); });
      r.push(info('Remote: ' + (keyInfo.available ? 'media keys registered: ' + (keyInfo.registered.join(', ') || 'none') : 'the TV\'s key registration isn\'t available here') + '. Home, volume, Source, voice and Art Mode stay with the TV.'));
      diagnostics.forEach(function (d) { r.push(info(d)); });
      r.push(heading('Advanced'));
      dock.forEach(function (d) {
        if (U.Apps.availability(d.id) !== 'available' || d.entry.installed) return;
        const res = U.Apps.resolve(d.id, installed, S.appChoices);
        const shown = res.appId ? (res.source === 'owner' ? 'Your choice' : res.source === 'installed' ? 'Found on this TV' : 'Not confirmed') : 'Not found';
        r.push(row('App for ' + d.name, shown, function () { chooseApp(d.id); }, { small: true }));
      });
      const n = nas();
      r.push(row('Media server address', n && n.url ? (n.name || 'Set') : 'Not set', function () { editNasAddress(); }));
      r.push(info(U.Dlna.FIND_YOUR_NAS));
      r.push(row('Choose your film and TV folders', n && n.roots ? n.roots.length + ' of 3 chosen' : 'None chosen', function () { if (!n || !n.controlUrl) { editNasAddress(); return; } pickFolders(); }));
      r.push(row('Forget the media server', '', function () { confirmSheet('Forget the media server?', 'This removes its address and your chosen folders from this TV.', 'Forget', function () { vault.forget('nas'); renderSettings(); }); }));
      r.push(row('Open original TV interface', '', function () { originalInterface(); }, { small: true }));
      return r;
    } },
    { name: 'Privacy & security', rows: function () {
      return [info('Secrets: ' + vault.label()),
        info('Other settings, such as the Home Assistant address and the look of Home, are kept in this TV\'s app storage.'),
        info('Unified TV sends nothing anywhere except to the Home Assistant and media server addresses you enter. No usage data is collected.'),
        row('Forget all connections', '', function () { confirmSheet('Forget all connections?', 'This removes Home Assistant and the media server from this TV.', 'Forget all', function () { vault.forgetAll(); S.ha = U.Settings.defaults().ha; save(); restartHa(); renderSettings(); }); }),
        row('Reset all settings', '', function () { confirmSheet('Reset all settings?', 'Home, Wallpaper and connection choices go back to how they were at first. Installed apps are not touched.', 'Reset', function () {
          vault.forgetAll(); S = U.Settings.defaults(); save(); restartHa(); buildDock(); applyAppearance(); renderSettings();
        }); })];
    } }
  ];

  let settingsCtl = null;
  function openSettings() {
    const layer = nav.push('settings');
    const ctl = { cat: 0, zone: 'cats', index: -1 };
    layer.ctl = ctl; settingsCtl = ctl;
    ctl.render = function () {
      const page = $('page');
      page.innerHTML = '';
      page.appendChild(el('h1', '', 'Settings'));
      const cols = el('div', 'columns');
      const cats = el('div', 'cats glass'); const catRows = el('div', 'rows'); cats.appendChild(catRows);
      const panel = el('div', 'panel glass'); const rows = el('div', 'rows'); panel.appendChild(rows);
      cols.appendChild(cats); cols.appendChild(panel); page.appendChild(cols);
      renderRows(catRows, CATEGORIES.map(function (c, i) { return { label: c.name, selected: i === ctl.cat }; }), ctl.cat, ctl.zone === 'cats');
      ctl.rows = CATEGORIES[ctl.cat].rows();
      if (ctl.index < 0 || !focusable(ctl.rows[ctl.index])) ctl.index = firstFocusable(ctl.rows);
      renderRows(rows, ctl.rows, ctl.index, ctl.zone === 'rows');
      $('home').hidden = true; page.hidden = false;
    };
    ctl.key = function (a) {
      if (ctl.zone === 'cats') {
        if (a === 'up' || a === 'down') { ctl.cat = U.Focus.list(ctl.cat, a, CATEGORIES.length); ctl.index = -1; ctl.render(); }
        else if (a === 'right' || a === 'ok') { if (firstFocusable(CATEGORIES[ctl.cat].rows()) !== -1) { ctl.zone = 'rows'; ctl.index = -1; ctl.render(); } }
        else if (a === 'back') closeTop();
      } else {
        if (a === 'up' || a === 'down') { ctl.index = stepFocus(ctl.rows, ctl.index, a); ctl.render(); }
        else if (a === 'left' || a === 'back') { ctl.zone = 'cats'; ctl.render(); }
        else if (a === 'ok') { const r = ctl.rows[ctl.index]; if (r && r.run) { r.run(); if (nav.top().ctl === ctl) ctl.render(); } }
      }
    };
    ctl.close = function () { settingsCtl = null; $('page').hidden = true; $('home').hidden = false; };
    ctl.render();
  }
  function renderSettings() { if (settingsCtl && nav.top().ctl === settingsCtl) settingsCtl.render(); }
  function openCategory(i) { if (!settingsCtl) return; settingsCtl.cat = i; settingsCtl.zone = 'rows'; settingsCtl.index = -1; settingsCtl.render(); }

  function chooseApp(id) {
    const e = U.Apps.entry(id);
    if (!installed) { infoSheet('App for ' + e.name, 'This TV didn\'t share its list of installed apps, so there is nothing to choose from here.'); return; }
    const rows = [{ label: 'Choose automatically', value: S.appChoices[id] ? '' : 'Current', run: function () { delete S.appChoices[id]; save(); closeTop(); } }];
    installed.forEach(function (a) { rows.push({ label: a.name || a.id, value: S.appChoices[id] === a.id ? 'Current' : '', run: function () { S.appChoices[id] = a.id; save(); closeTop(); } }); });
    openSheet({ title: 'App for ' + e.name, text: 'Pick the installed app this tile opens.', rows: rows });
  }
  function originalInterface() {
    openSheet({ title: 'Open original TV interface',
      text: 'On this TV, the Home button on your remote opens Samsung Smart Hub, the TV\'s own interface. Unified TV can\'t change what Home does, and there is no option to make either one the default.\n\nYou can also close Unified TV here. What appears next is up to your TV; this hasn\'t been tested on your Frame yet.',
      rows: [
        { label: 'Close Unified TV', run: function () {
          saveUi();
          try { env.tizen.application.getCurrentApplication().exit(); } catch (e) { closeTop(); showMessage('Press the Home button on your remote to open Samsung Smart Hub.'); }
        } },
        { label: 'Cancel', run: function () { closeTop(); } }
      ] });
  }
  function testSheet() {
    function sim(kind, state, label) {
      return { label: label, run: function () {
        const now = Date.now();
        const r = policy.update('simulated.' + kind + (state === 'on' ? '' : '_' + state), kind, 'Test', state, now, true);
        closeTop();
        if (r !== U.Alerts.NONE) renderAlarms(r === U.Alerts.NEW_ALARM);
      } };
    }
    openSheet({ title: 'Test the alarm screen', text: 'These are TEST events inside Unified TV only. No detector or Home Assistant is involved, and a test never counts as a real alarm check.',
      rows: [sim('smoke', 'on', 'TEST smoke alarm'), sim('carbon_monoxide', 'on', 'TEST carbon monoxide alarm'), sim('smoke', 'unavailable', 'TEST detector offline'),
        { label: 'TEST doorbell', run: function () { closeTop(); const now = Date.now(); if (policy.acceptRing('test:' + now, now)) { if (policy.emergency()) { policy.queueRing(now); renderAlarms(false); } else showDoorbell(); } } },
        { label: 'End the test', run: function () { closeTop(); policy.endSimulation(); renderAlarms(false); } },
        { label: 'Cancel', run: function () { closeTop(); } }] });
  }

  // ---------------------------------------------------------------- NAS (DLNA over HTTP; no discovery is possible here)
  function request(method, url, headers, body, done) {
    let x;
    try { x = new XMLHttpRequest(); x.open(method, url, true); } catch (e) { done('network'); return; }
    x.timeout = 8000;
    Object.keys(headers || {}).forEach(function (h) { try { x.setRequestHeader(h, headers[h]); } catch (e) { /* refused header */ } });
    x.onload = function () { done(x.status >= 200 && x.status < 300 ? null : 'status', x.responseText); };
    x.onerror = function () { done('network'); };
    x.ontimeout = function () { done('timeout'); };
    try { x.send(body); } catch (e) { done('network'); }
  }
  const dlna = new U.Dlna.Client(request);
  function editNasAddress() {
    const n = nas() || {};
    inputSheet('Media server address', U.Dlna.FIND_YOUR_NAS, { value: n.url || '', placeholder: 'http://your-nas:port/description.xml', save: function (v) {
      dlna.describe(v, function (err, d) {
        if (err) { showMessage(U.Dlna.message(err)); return; }
        saveNas({ url: v, name: d.name, controlUrl: d.controlUrl, roots: n.url === v && n.roots ? n.roots : [] }, function (kept) {
          showMessage('Found ' + d.name + '.' + (kept ? '' : ' It will be forgotten when Unified TV closes: secure storage isn\'t available.'));
          renderSettings();
        });
      });
    } }, renderSettings);
  }
  function pickFolders() {
    const n = nas();
    const trail = [{ id: '0', title: n.name || 'Media server' }];
    let entries = { containers: [], items: [] };
    let loading = false;
    const ctl = openPage('Choose your film and TV folders', 'Choose up to three folders. Only these are shown in Films & TV, and only their videos.', function () {
      const here = trail[trail.length - 1];
      const roots = U.Dlna.collections(n.roots);
      const chosen = roots.some(function (r) { return r.id === here.id; });
      const rows = [info('Chosen: ' + (roots.length ? roots.map(function (r) { return r.title; }).join(', ') : 'none') + '.')];
      if (here.id !== '0') rows.push({ label: chosen ? 'Stop using “' + here.title + '”' : 'Use “' + here.title + '”', value: chosen ? 'Chosen' : roots.length >= 3 ? 'Three already chosen' : '', run: function () {
        if (chosen) n.roots = roots.filter(function (r) { return r.id !== here.id; });
        else if (roots.length < 3) n.roots = roots.concat([{ id: here.id, title: here.title }]);
        else { showMessage('Three folders are already chosen. Stop using one first.'); return; }
        saveNas(n); ctl.render();
      } });
      if (loading) rows.push(info('Loading…'));
      entries.containers.forEach(function (c) { rows.push({ label: c.title, value: 'Open', run: function () { trail.push(c); load(); } }); });
      if (!loading && !entries.containers.length) rows.push(info('No folders inside this one.'));
      return rows;
    });
    const baseKey = ctl.key;
    ctl.key = function (a) { if (a === 'back' && trail.length > 1) { trail.pop(); load(); return; } baseKey(a); };
    function load() {
      loading = true; ctl.index = -1; ctl.render();
      dlna.browseAll(n.controlUrl, trail[trail.length - 1].id, function (err, all) {
        loading = false; entries = err ? { containers: [], items: [] } : all;
        if (err) showMessage(U.Dlna.message(err));
        if (nav.top().ctl === ctl) ctl.render();
      });
    }
    load();
  }
  function openLibrary() {
    setScene('neutral');   // DLNA has no artwork: a neutral backdrop with the title named, never another item's art
    const n = nas();
    const trail = [];
    let entries = null, loading = false;
    const ctl = openPage('Films & TV', 'Titles come straight from your NAS. DLNA has no posters, search or synchronised progress, so none are shown.', function () {
      if (!n || !n.controlUrl) return [info(U.Dlna.FIND_YOUR_NAS), { label: 'Enter the media server address', run: function () { closeTop(); openSettings(); openCategory(4); } }];
      const roots = U.Dlna.collections(n.roots);
      if (!roots.length) return [info('No folders are chosen yet.'), { label: 'Choose your film and TV folders', run: function () { pickFolders(); } }];
      if (!trail.length) return roots.map(function (r) { return { label: r.title, value: 'Open', run: function () { trail.push(r); load(); } }; });
      const rows = [];
      if (loading) rows.push(info('Loading…'));
      if (entries) {
        entries.containers.forEach(function (c) { rows.push({ label: c.title, value: 'Open', run: function () { trail.push(c); load(); } }); });
        entries.items.forEach(function (it) { rows.push({ label: it.title, value: it.duration ? it.duration.replace(/\.\d+$/, '') : '', run: function () { play(it); } }); });
        if (!entries.containers.length && !entries.items.length) rows.push(info('Nothing to watch in this folder.'));
      }
      return rows;
    }, 'library');
    const baseKey = ctl.key;
    ctl.key = function (a) { if (a === 'back' && trail.length) { trail.pop(); entries = null; if (trail.length) load(); else { ctl.index = -1; ctl.render(); } return; } baseKey(a); };
    const baseClose = ctl.close;
    ctl.close = function () { baseClose(); };
    ctl.resume = function () { ctl.render(); };
    function load() {
      loading = true; ctl.index = -1; ctl.render();
      dlna.browseAll(n.controlUrl, trail[trail.length - 1].id, function (err, all) {
        loading = false; entries = err ? { containers: [], items: [] } : all;
        if (err) showMessage(U.Dlna.message(err));
        if (nav.top().ctl === ctl) ctl.render();
      });
    }
  }

  // ---------------------------------------------------------------- player (AVPlay)
  let barTimer = null;
  const player = new U.Player.Player({ webapis: env.webapis, now: Date.now }, function (state, detail) { renderPlayer(state, detail); });
  function time(ms) {
    const s = Math.max(0, Math.floor(ms / 1000)), h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, sec = s % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (sec < 10 ? '0' : '') + sec;
  }
  function renderPlayer(state, detail) {
    const text = state === 'preparing' ? 'Opening…' : state === 'buffering' ? 'Loading…' : state === 'paused' ? 'Paused · ' + time(player.position)
      : state === 'playing' ? 'Playing' + (player.duration ? ' · ' + time(player.position) + ' of ' + time(player.duration) : '')
      : state === 'error' ? (detail && detail.message) || player.error : state === 'ended' ? 'Finished' : state === 'suspended' ? 'Paused' : '';
    $('player-state').textContent = detail && detail.message && state !== 'error' ? detail.message : text;
    const bar = $('player-bar');
    bar.classList.remove('quiet');
    window.clearTimeout(barTimer);
    if (state === 'playing') barTimer = window.setTimeout(function () { bar.classList.add('quiet'); }, 4000);
    if (state === 'ended' && nav.top().name === 'player') closeTop();
  }
  function play(item) {
    const layer = nav.push('player');
    const ctl = {};
    layer.ctl = ctl;
    ctl.key = function (a) {
      if (a === 'ok' || a === 'playpause') player.toggle();
      else if (a === 'play') player.play();
      else if (a === 'pause') player.pause();
      else if (a === 'left' || a === 'rewind') player.seek(-U.Player.SEEK_STEP_MS);
      else if (a === 'right' || a === 'forward') player.seek(U.Player.SEEK_STEP_MS);
      else if (a === 'back' || a === 'stop') closeTop();
      else renderPlayer(player.state, {});
    };
    ctl.close = function () {
      player.stop();
      document.body.classList.remove('playing');
      $('player').hidden = true; $('scene').hidden = false; $('page').hidden = false;
    };
    $('player-title').textContent = item.title;
    $('scene').hidden = true; $('page').hidden = true; $('home').hidden = true; $('player').hidden = false;
    document.body.classList.add('playing');
    player.open(item.url, 0);
  }

  // ---------------------------------------------------------------- Wallpaper (Unified TV's own; never Art Mode)
  function loadCatalogue() {
    const list = window.UTV_WALLPAPER_DATA || null;
    wp.list = U.Wallpaper.catalogue(list);
  }
  function resetIdle() {
    window.clearTimeout(idleTimer);
    const minutes = U.Wallpaper.idle(S.wallpaper.idle);
    if (!minutes || U.Wallpaper.source(S.wallpaper.source) === U.Wallpaper.OFF || !wp.list.length) return;
    idleTimer = window.setTimeout(function () {
      if (nav.atRoot() && !moving && $('alarm').hidden && $('doorbell').hidden && document.visibilityState !== 'hidden') enterWallpaper();
      else resetIdle();
    }, minutes * 60000);
  }
  function enterWallpaper() {
    if (!wp.list.length || wp.active) return;
    wp.active = true; wp.count = 0;
    document.body.classList.add('paused');
    const box = $('wallpaper');
    box.hidden = false; void box.offsetWidth; box.classList.add('shown');
    showSlide();
  }
  function showSlide() {
    window.clearTimeout(slideTimer);
    const pic = wp.list[wp.index % wp.list.length];
    const img = $('wallpaper-image');
    img.onload = function () { placeClock(img); };
    img.onerror = function () { $('wallpaper-clock').hidden = false; };
    img.style.opacity = String(1 - (S.wallpaper.dim || 0) / 100);
    img.src = pic.file;
    renderWallpaperClock();
    slideTimer = window.setTimeout(function () { wp.index++; wp.count++; showSlide(); }, U.Wallpaper.slide(S.wallpaper.slide) * 1000);
  }
  function placeClock(img) {
    let grid = null;
    try {
      const c = document.createElement('canvas'); c.width = U.Wallpaper.GW; c.height = U.Wallpaper.GH;
      const g = c.getContext('2d'); g.drawImage(img, 0, 0, c.width, c.height);
      grid = U.Wallpaper.luminanceGrid(g.getImageData(0, 0, c.width, c.height).data);
    } catch (e) { grid = null; }
    if (!grid) { grid = []; for (let i = 0; i < U.Wallpaper.GW * U.Wallpaper.GH; i++) grid.push(0.3); }
    const kind = U.Wallpaper.clockFor(U.Wallpaper.clock(S.wallpaper.clock), null);   // no prepared masks yet: never depth
    const clock = $('wallpaper-clock');
    clock.hidden = kind === U.Wallpaper.NONE;
    const s = U.Wallpaper.spot(grid, wp.count);
    const cell = W / U.Wallpaper.GW;
    clock.style.left = Math.round(s[0] * cell) + 'px'; clock.style.top = Math.round(s[1] * cell) + 'px';
    clock.style.width = Math.round((s[2] - s[0]) * cell) + 'px';
    clock.style.textAlign = s[0] >= 8 ? 'right' : s[0] >= 5 ? 'center' : 'left';
    clock.classList.toggle('shade', U.Wallpaper.needsShade(grid, s));
  }
  function renderWallpaperClock() {
    const now = Date.now();
    $('wallpaper-clock').querySelector('.time').textContent = U.Clock.format(now, { hour12: S.wallpaper.hour12 });
    $('wallpaper-clock').querySelector('.date').textContent = S.wallpaper.date ? U.Clock.date(now) : '';
  }
  /** The first key only leaves Wallpaper and shows Home with its focus; it never also opens an app (spec 8). */
  function exitWallpaper() {
    if (!wp.active) return;
    wp.active = false;
    window.clearTimeout(slideTimer);
    const box = $('wallpaper');
    box.classList.remove('shown');
    window.setTimeout(function () { if (!wp.active) box.hidden = true; }, 850);
    document.body.classList.remove('paused');
    renderFocus(); resetIdle();
  }

  // ---------------------------------------------------------------- household alerts (spec 10)
  function restartHa() {
    if (ha) { ha.stop(); ha = null; }
    if (!S.ha.enabled) { haStatus = U.Ha.STATUS.NOT_CONFIGURED; renderNotice(); renderSettings(); return; }
    const ep = U.Ha.endpoint(S.ha);
    const token = vault.get('ha-token');
    if (!ep.ok || !token || typeof window.WebSocket !== 'function') { haStatus = U.Ha.STATUS.NOT_CONFIGURED; renderNotice(); renderSettings(); return; }
    ha = new U.Ha.Client({ url: ep.url, token: token, WebSocket: window.WebSocket, policy: policy, now: Date.now,
      setTimer: function (f, ms) { return window.setTimeout(f, ms); }, clearTimer: function (id) { window.clearTimeout(id); },
      onUpdate: onHa, alarms: S.alerts.alarms, reminders: S.alerts.reminders, door: S.alerts.door, person: S.alerts.person, room: 'frame' });
    ha.start();
  }
  function onHa(kind, d) {
    if (kind === 'status') { haStatus = d.text; renderNotice(); renderSettings(); if (!$('alarm').hidden) renderAlarms(false); }
    else if (kind === 'alarms') renderAlarms(d.fresh);
    else if (kind === 'ring') showDoorbell();
    else if (kind === 'reminder') showMessage(d.text, 8000);
  }
  /** Mirrors Android's render(): the full screen while any alarm is unacknowledged, then a banner while any is active. */
  function renderAlarms(fresh) {
    const emergency = policy.emergency(), screen = policy.screen();
    if (screen) {
      if (fresh || $('alarm').hidden) {
        dismissDoorbell(true); $('toast').hidden = true; exitWallpaper();
        if (player.state === 'playing' || player.state === 'buffering') player.pause();
        alarmIndex = -1; alarmEntity = null;
      }
      alarmScreen();
    } else $('alarm').hidden = true;
    if (emergency && !screen) alarmBanner(); else $('alarm-banner').hidden = true;
    renderNotice();
    if (!emergency) {
      const at = policy.queuedRingAt();
      const ring = policy.ringAfterAlarm(Date.now());
      if (ring > 0 && policy.acceptRing('after-alarm:' + at, Date.now())) showDoorbell(at);
      else if (ring < 0) showMessage('Doorbell rang at ' + hhmm(at) + ' during the alarm.', 10000);
    }
  }
  function alarmEntryText(d) { return d.title() + (d.name ? ' — ' + d.name : '') + (d.since > 0 ? ' — ' + hhmm(d.since) : ''); }
  function alarmScreen() {
    const box = $('alarm');
    box.innerHTML = '';
    const on = policy.alarms();
    const targets = [];
    on.forEach(function (d) {
      const entry = el('div', 'entry');
      entry.appendChild(el('div', 'what', alarmEntryText(d)));
      if (d.lost) entry.appendChild(el('div', 'advice', 'The detector stopped answering. Treat the alarm as real.'));
      entry.appendChild(el('div', 'advice', d.advice()));
      const ack = el('div', 'ack' + (d.acknowledged ? ' done' : ''), d.acknowledged ? 'Acknowledged on this TV' : 'Acknowledge');
      if (!d.acknowledged) targets.push({ el: ack, entity: d.entity });
      entry.appendChild(ack);
      box.appendChild(entry);
    });
    if (policy.queuedRingAt() > 0) box.appendChild(el('div', 'line', 'Doorbell rang at ' + hhmm(policy.queuedRingAt()) + '. It will be shown when the alarm has ended.'));
    if (!policy.connected()) box.appendChild(el('div', 'line', 'Connection to Home Assistant lost: alarm status unknown.'));
    policy.warnings().forEach(function (w) { box.appendChild(el('div', 'line', w.warning() + (w.name ? ': ' + w.name : '') + (w.since > 0 ? ' (since ' + hhmm(w.since) + ')' : ''))); });
    box.appendChild(el('div', 'line', 'Acknowledging only clears this screen. It never silences or resets a detector.'));
    box.appendChild(el('div', 'line', 'This screen adds to your alarms. It never replaces them.'));
    // A redraw (doorbell press, name change, warning) keeps focus on the same detector, so OK never acknowledges another.
    const kept = targets.map(function (t) { return t.entity; }).indexOf(alarmEntity);
    if (kept >= 0) alarmIndex = kept;
    else if (alarmIndex < 0 || alarmIndex >= targets.length) alarmIndex = 0;
    alarmEntity = targets[alarmIndex] ? targets[alarmIndex].entity : null;
    if (targets[alarmIndex]) targets[alarmIndex].el.classList.add('focused');
    box.targets = targets;
    box.hidden = false;
  }
  function alarmKey(a) {
    const targets = $('alarm').targets || [];
    if (!targets.length) return;
    if (a === 'left' || a === 'up') alarmIndex = Math.max(0, alarmIndex - 1);
    else if (a === 'right' || a === 'down') alarmIndex = Math.min(targets.length - 1, alarmIndex + 1);
    else if (a === 'ok' || a === 'long') { policy.acknowledge(targets[alarmIndex].entity); alarmIndex = 0; alarmEntity = null; renderAlarms(false); return; }
    alarmEntity = targets[alarmIndex].entity;
    // Back does nothing here: the emergency screen is never dismissed by Back or by time.
    alarmScreen();
  }
  function alarmBanner() {
    const on = policy.alarms();
    const b = $('alarm-banner');
    b.textContent = on.slice(0, 3).map(alarmEntryText).join('   ·   ') + (on.length > 3 ? '   (+' + (on.length - 3) + ')' : '') +
      (policy.queuedRingAt() > 0 ? '   ·   Doorbell rang at ' + hhmm(policy.queuedRingAt()) : '') +
      (!policy.connected() ? '   ·   Alarm status unknown' : '');
    b.hidden = false;
  }
  /** Household notices (HOUSEHOLD_IMPORTANT): detector warnings and a lost connection, never styled as an emergency. */
  function renderNotice() {
    const n = $('notice');
    const lines = [];
    if (S.ha.enabled && S.alerts.alarms && haStatus !== U.Ha.STATUS.AUTHORISED && haStatus !== U.Ha.STATUS.NOT_CONFIGURED && haStatus !== U.Ha.STATUS.CONNECTING && haStatus !== U.Ha.STATUS.REACHABLE) {
      lines.push(haStatus === U.Ha.STATUS.AUTH_REQUIRED ? 'Home Assistant needs a new token: alarm status unknown.' : 'Alarm status unknown: no connection to Home Assistant.');
    }
    if (!policy.emergency()) policy.warnings().forEach(function (w) { lines.push(w.warning() + (w.name ? ': ' + w.name : '')); });
    n.textContent = lines.slice(0, 3).join('\n');
    n.style.whiteSpace = 'pre-line';
    n.hidden = !lines.length;
  }
  function showDoorbell(at) {
    if (policy.emergency()) return;
    doorbellAt = at || Date.now();
    const card = $('doorbell');
    card.innerHTML = '';
    card.appendChild(el('h2', '', 'Doorbell'));
    card.appendChild(el('p', '', 'Someone is at the door · ' + hhmm(doorbellAt)));
    card.appendChild(el('p', '', 'The live camera isn\'t available in the Frame app yet.'));
    card.appendChild(el('p', '', 'Press Back to close.'));
    card.hidden = false;
    window.clearTimeout(doorbellTimer);
    doorbellTimer = window.setTimeout(function () { dismissDoorbell(); }, U.Alerts.RING_STILL_RELEVANT);
  }
  function dismissDoorbell(quiet) {
    window.clearTimeout(doorbellTimer);
    if ($('doorbell').hidden) return;
    $('doorbell').hidden = true;
    policy.dismiss();
    const later = policy.release(Date.now());
    if (later && !quiet) showMessage(later, 8000);
  }

  // ---------------------------------------------------------------- keys
  function dispatch(a) {
    resetIdle();
    if (!$('alarm').hidden) { alarmKey(a); return; }
    if (a === 'back' && !$('doorbell').hidden) { dismissDoorbell(); return; }
    if (wp.active) { if (U.Wallpaper.dismissKey(a) || a === 'long') exitWallpaper(); return; }
    const top = nav.top();
    if (!top.ctl || !top.ctl.key) return;
    if (a === 'long' && !top.ctl.handlesLong) a = 'ok';
    if (['playpause', 'play', 'pause', 'stop', 'forward', 'rewind'].indexOf(a) !== -1 && top.name !== 'player') return;
    top.ctl.key(a);
  }
  /** The tracker says 'press' or 'long'; the rest of the page speaks in actions. */
  function okAction(result) { return result === 'long' ? 'long' : 'ok'; }
  function stopPressTimer() { if (pressTimer !== null) { window.clearInterval(pressTimer); pressTimer = null; } }
  document.addEventListener('keydown', function (e) {
    const active = document.activeElement;
    if (active && active.tagName === 'INPUT') {
      // The TV's own keyboard: Done (65376) or Enter keeps the text; Cancel (65385) or Back leaves it.
      if (e.keyCode === 65376 || e.keyCode === 13) { e.preventDefault(); fieldDone(true); }
      else if (e.keyCode === 65385 || e.keyCode === 10009 || e.keyCode === 27) { e.preventDefault(); fieldDone(false); }
      return;
    }
    const a = U.Keys.action(e.keyCode, false);
    if (!a) return;   // not ours: the TV keeps it
    e.preventDefault();
    if (a === 'ok') {
      if (wp.active) { press.cancel(); stopPressTimer(); resetIdle(); exitWallpaper(); return; }
      const r = press.down(Date.now());
      if (pressTimer === null) pressTimer = window.setInterval(function () { const t = press.tick(Date.now()); if (t) { stopPressTimer(); dispatch(okAction(t)); } }, 80);
      if (r) { stopPressTimer(); dispatch(okAction(r)); }
      return;
    }
    if (e.repeat && a === 'back') return;   // a held Back is one Back (RC-11); a long Return stays the TV's
    dispatch(a);
  });
  document.addEventListener('keyup', function (e) {
    if (e.keyCode !== 13) return;
    const active = document.activeElement;
    if (active && active.tagName === 'INPUT') return;
    stopPressTimer();
    const r = press.up(Date.now());
    if (r) dispatch(okAction(r));
  });

  // ---------------------------------------------------------------- lifecycle (spec 6.2, NT-06)
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) {
      saveUi();
      document.body.classList.add('paused');
      window.clearTimeout(idleTimer);
      player.hide();
      if (ha) { ha.stop(); haStatus = U.Ha.STATUS.OFFLINE; }
    } else {
      if (!wp.active) document.body.classList.remove('paused');
      U.Apps.listInstalled(env, function (list) { installed = list; buildDock(); renderFocus(); });
      restartHa();
      if (nav.top().name === 'player') player.show();
      resetIdle();
      window.clearTimeout(clockTimer); tickClock();
    }
  });

  function fit() {
    const s = Math.min(window.innerWidth / W, window.innerHeight / H);
    $('stage').style.transform = s > 0 && Math.abs(s - 1) > 0.01 ? 'scale(' + s + ')' : '';
  }

  // ---------------------------------------------------------------- start
  window.addEventListener('resize', fit);
  fit();
  loadCatalogue();
  applyAppearance();
  focus = U.Focus.initial(U.Apps.dockOrder(S.dock).length, S.ui.dockIndex);
  buildDock();
  renderFocus();
  tickClock();
  U.Apps.listInstalled(env, function (list) { installed = list; buildDock(); renderFocus(); });
  restartHa();
  resetIdle();
  watchFrames();
  if (simulation) showMessage('Browser simulation: the TV\'s own features aren\'t available here.', 6000);
}());
