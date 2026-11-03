'use strict';
// The AVPlay wrapper (spec 9, PRODUCT_SPEC 24.4, NT-02, NT-06, NT-07) against a fake webapis.avplay.
const test = require('node:test');
const assert = require('node:assert');
const Player = require('../src/js/player.js');

function fakeAvplay(options) {
  const o = options || {};
  const av = {
    calls: [], listener: null, state: 'NONE',
    open(url) { this.calls.push('open ' + url); if (o.openThrows) { const e = new Error('x'); e.name = 'NotSupportedError'; throw e; } this.state = 'IDLE'; },
    setListener(l) { this.listener = l; }, setDisplayRect() { this.calls.push('rect'); },
    prepareAsync(ok, err) { this.calls.push('prepare'); av.pending = { ok, err }; },
    play() { this.calls.push('play'); this.state = 'PLAYING'; }, pause() { this.calls.push('pause'); this.state = 'PAUSED'; },
    seekTo(ms, ok) { this.calls.push('seek ' + ms); ok(); }, stop() { this.calls.push('stop'); }, close() { this.calls.push('close'); },
    getDuration() { return 600000; }, suspend() { this.calls.push('suspend'); }, restore(url, ms) { this.calls.push('restore ' + ms); }
  };
  return av;
}
function make(av) {
  const states = []; let t = 1000;
  const p = new Player.Player({ webapis: { avplay: av }, now: () => t }, (s, d) => states.push([s, d]));
  return { p, states, tick: (ms) => { t += ms; } };
}
const URL_OK = 'http://media-server.local:8200/MediaItems/42.mkv';

test('open, prepare, play, pause, seek and stop follow one explicit lifecycle', () => {
  const av = fakeAvplay(); const { p, states, tick } = make(av);
  assert.ok(p.open(URL_OK, 0));
  assert.strictEqual(p.state, 'preparing');
  av.pending.ok();
  assert.strictEqual(p.state, 'playing'); assert.strictEqual(p.duration, 600000);
  tick(850); av.listener.oncurrentplaytime(40);
  assert.strictEqual(p.firstFrameMs, 850, 'first frame time is recorded');
  p.toggle(); assert.strictEqual(p.state, 'paused');
  p.toggle(); assert.strictEqual(p.state, 'playing');
  av.listener.oncurrentplaytime(5000);
  p.seek(10000); assert.ok(av.calls.indexOf('seek 15000') !== -1);
  p.seek(-60000); assert.ok(av.calls.indexOf('seek 0') !== -1, 'never before the start');
  p.position = 595000; p.seek(10000); assert.ok(av.calls.indexOf('seek 599000') !== -1, 'never past the end');
  av.listener.onbufferingstart(); assert.strictEqual(p.state, 'buffering');
  av.listener.onbufferingcomplete(); assert.strictEqual(p.state, 'playing');
  p.stop();
  assert.strictEqual(p.state, 'stopped');
  assert.deepStrictEqual(av.calls.slice(-2), ['stop', 'close'], 'AVPlay is released');
  assert.ok(states.length > 5);
});

test('the previous item is always released before another opens, and its late events are ignored', () => {
  const av = fakeAvplay(); const { p } = make(av);
  p.open(URL_OK, 0); av.pending.ok();
  const old = av.listener;
  p.open('http://media-server.local:8200/MediaItems/43.mp4', 0);
  const opens = av.calls.map((c, i) => [c, i]).filter((x) => x[0].indexOf('open') === 0);
  const closeBefore = av.calls.lastIndexOf('close', opens[1][1]);
  assert.ok(closeBefore > opens[0][1] && closeBefore < opens[1][1], 'closed between the two opens');
  old.onerror('PLAYER_ERROR_CONNECTION_FAILED');
  assert.strictEqual(p.state, 'preparing', 'the old item\'s error doesn\'t touch the new one');
});

test('errors are plain words, and AVPlay is released', () => {
  const av = fakeAvplay(); const { p } = make(av);
  p.open(URL_OK, 0); av.pending.ok();
  av.listener.onerror('PLAYER_ERROR_NOT_SUPPORTED_FILE');
  assert.strictEqual(p.state, 'error'); assert.strictEqual(p.error, 'This TV can\'t play this file\'s format.');
  assert.match(Player.message('PLAYER_ERROR_CONNECTION_FAILED'), /stopped answering/);
  assert.match(Player.message('anything'), /stopped unexpectedly/);
  const thrower = fakeAvplay({ openThrows: true }); const t = make(thrower);
  assert.strictEqual(t.p.open(URL_OK, 0), false); assert.strictEqual(t.p.state, 'error');
});

test('no credentials in a stream address: user names, passwords and tokens are refused (NT-07)', () => {
  assert.strictEqual(Player.refuse(URL_OK), null);
  assert.match(Player.refuse('http://user:secret@media-server.local/a.mp4'), /password/);
  assert.match(Player.refuse('http://media-server.local/a.mp4?api_key=abc'), /token/);
  assert.match(Player.refuse('http://media-server.local/a.mp4?X-Emby-Token=abc'), /token/);
  assert.match(Player.refuse('http://media-server.local/a.mp4?token=abc'), /token/);
  assert.match(Player.refuse('file:///etc/passwd'), /isn't one/);
  assert.match(Player.refuse('rtsp://camera.local/stream'), /isn't one/);
  const av = fakeAvplay(); const { p } = make(av);
  assert.strictEqual(p.open('http://media-server.local/a.mp4?access_token=abc', 0), false);
  assert.strictEqual(av.calls.length, 0, 'AVPlay never sees it');
});

test('hidden: position kept and AVPlay suspended; visible: restored, paused, ready to carry on', () => {
  const av = fakeAvplay(); const { p } = make(av);
  p.open(URL_OK, 0); av.pending.ok(); av.listener.oncurrentplaytime(120000);
  p.hide(); assert.strictEqual(p.state, 'suspended'); assert.ok(av.calls.indexOf('suspend') !== -1);
  p.show(); assert.strictEqual(p.state, 'paused'); assert.ok(av.calls.indexOf('restore 120000') !== -1);
});

test('without suspend support, hiding releases AVPlay and showing reopens at the same place', () => {
  const av = fakeAvplay(); delete av.suspend; delete av.restore; const { p } = make(av);
  p.open(URL_OK, 0); av.pending.ok(); av.listener.oncurrentplaytime(30000);
  p.hide(); assert.strictEqual(p.state, 'suspended'); assert.strictEqual(av.calls.slice(-2).join(','), 'stop,close');
  p.show(); assert.strictEqual(p.state, 'preparing'); av.pending.ok();
  assert.ok(av.calls.indexOf('seek 30000') !== -1); assert.strictEqual(p.state, 'playing');
});

test('no AVPlay (a desktop browser): a plain message, never a fallback player', () => {
  const p = new Player.Player({}, () => {});
  assert.strictEqual(p.available(), false);
  assert.strictEqual(p.open(URL_OK, 0), false);
  assert.match(p.error, /TV's own player/);
});

test('the end of a file releases AVPlay', () => {
  const av = fakeAvplay(); const { p } = make(av);
  p.open(URL_OK, 0); av.pending.ok(); av.listener.onstreamcompleted();
  assert.strictEqual(p.state, 'ended'); assert.deepStrictEqual(av.calls.slice(-2), ['stop', 'close']);
});
