'use strict';
// What Kids may see and open (owner, 25 October 2026; KIDS-01/02, SEARCH-READY-01). Mirrors
// preview/app/test/io/unifiedtv/preview/KidsPolicyTest.java case by case, then checks the Frame's own pieces: the DLNA
// detail the Kids path reads, the play-time walk against a fake media server, the one launch check and the storage.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const Kids = require('../src/js/kids.js');
const Catalogue = require('../src/js/catalogue.js');
const Dlna = require('../src/js/dlna.js');
const Secrets = require('../src/js/secrets.js');

const item = (id, parent, root, ...ratings) => Catalogue.item('dlna', 'uuid:nas', id, 'Title', '', 'video', parent, root, ratings, '');

test('the owner\'s rating table is allowed', () => {
  for (const r of ['U', 'Uc', 'PG', 'G', 'TV-Y', 'TV-Y7', 'TV-Y7-FV', 'TV-G', 'TV-PG']) assert.strictEqual(Kids.rating(r), Kids.ALLOWED, r);
});
test('anything higher or unknown is refused', () => {
  for (const r of ['12', '12A', '15', '18', 'R18', 'PG-13', 'R', 'NC-17', 'TV-14', 'TV-MA', 'NR', 'Unrated', 'Not Rated', '4', '8.5', 'A', 'U/A', 'X']) assert.strictEqual(Kids.rating(r), Kids.REFUSED, r);
});
test('prefixes, spacing and dashes are read the same way', () => {
  for (const r of ['GB-PG', 'UK:U', 'US-G', 'BBFC U', 'MPAA PG', 'Rated PG', 'tv-y7', 'TV PG', 'TVPG', 'TV–G', 'TV-Y7 FV', ' pg ']) assert.strictEqual(Kids.rating(r), Kids.ALLOWED, r);
  for (const r of ['GB-15', 'US-PG-13', 'Rated R', 'TV 14', 'UK:12A']) assert.strictEqual(Kids.rating(r), Kids.REFUSED, r);
});
test('no rating is no rating, and any refused rating wins', () => {
  assert.strictEqual(Kids.rating(null), Kids.NONE); assert.strictEqual(Kids.rating('  '), Kids.NONE);
  assert.strictEqual(Kids.ratings([]), Kids.NONE);
  assert.strictEqual(Kids.ratings(['PG', '15']), Kids.REFUSED);
  assert.strictEqual(Kids.ratings(['U', 'TV-G']), Kids.ALLOWED);
});
test('browsing stays inside Kids folders, and unrated is only allowed there', () => {
  const roots = ['kids-1'];
  assert.ok(Kids.allows(item('v1', 'kids-1', 'kids-1'), roots));
  assert.ok(Kids.allows(item('v2', 'kids-1', 'kids-1', 'PG'), roots));
  assert.ok(!Kids.allows(item('v3', 'kids-1', 'kids-1', '15'), roots), 'rated above Kids');
  assert.ok(!Kids.allows(item('v4', 'films', 'films'), roots), 'unrated outside a Kids folder');
  assert.ok(!Kids.allows(item('v5', 'films', 'films', 'U'), roots), 'even rated U, outside a Kids folder');
  assert.ok(!Kids.allows(null, roots)); assert.ok(!Kids.allows(item('v1', 'kids-1', 'kids-1'), []));
});
test('no Kids folders means not ready, and Kids folders belong to one media server', () => {
  const nas = { controlUrl: 'http://media-server.local:8200/cd/control' };
  assert.ok(!Kids.ready({}, nas)); assert.ok(!Kids.ready(null, nas));
  const r = { controlUrl: nas.controlUrl, roots: [] }; assert.ok(!Kids.ready(r, nas));
  r.roots = [{ id: '64$1', title: 'Kids' }]; assert.ok(Kids.ready(r, nas)); assert.deepStrictEqual(Kids.roots(r), ['64$1']);
  assert.ok(!Kids.ready(r, { controlUrl: 'http://other-server.local:8200/cd/control' }), 'another server: Ask an adult, never its library');
  assert.ok(!Kids.ready(r, null));
  const many = { roots: [{ id: 'a' }, { id: 'a' }, { id: '' }, { id: 'b' }, { id: 'c' }, { id: 'd' }, { id: 'e' }, { id: 'f' }, { id: 'g' }] };
  assert.deepStrictEqual(Kids.roots(many), ['a', 'b', 'c', 'd', 'e', 'f'], 'at most six, no duplicates or blanks');
});

function server(...items) {
  const m = {}; for (const i of items) m[i.id] = i;
  return (id, cb) => cb(null, Object.prototype.hasOwnProperty.call(m, id) ? m[id] : null);
}
function playable(id, roots, lookup) { let out = null; Kids.playable(id, roots, lookup, (ok) => { out = ok; }); return out; }

test('play time walks the server\'s own parents up to a Kids folder', () => {
  const roots = ['kids'];
  const nas = server(item('film', 'season', 'kids'), item('season', 'show', 'kids'), item('show', 'kids', 'kids'), item('kids', 'video', 'kids'));
  assert.strictEqual(playable('film', roots, nas), true);
  const outside = server(item('film', 'films', ''), item('films', 'video', ''), item('video', '0', ''), item('0', '-1', ''));
  assert.strictEqual(playable('film', roots, outside), false, 'a title outside every Kids folder is refused at play time');
});
test('a refused rating on the title or a folder above refuses play', () => {
  const roots = ['kids'];
  assert.strictEqual(playable('film', roots, server(item('film', 'kids', 'kids', '18'))), false);
  assert.strictEqual(playable('ep', roots, server(item('ep', 'show', 'kids'), item('show', 'kids', 'kids', 'TV-MA'))), false);
});
test('broken or looping chains are refused', () => {
  const roots = ['kids'];
  assert.strictEqual(playable('a', roots, server(item('a', 'b', ''), item('b', 'a', ''))), false);
  assert.strictEqual(playable('ghost', roots, server()), false, 'unknown to the server');
  assert.strictEqual(playable('kids', roots, server(item('kids', '0', ''))), false, 'a Kids folder isn\'t itself a title');
  assert.strictEqual(playable('', roots, server()), false); assert.strictEqual(playable('a', [], server(item('a', 'kids', ''))), false);
  const deep = []; for (let i = 0; i < 40; i++) deep.push(item('n' + i, 'n' + (i + 1), '')); deep.push(item('n40', 'kids', ''));
  assert.strictEqual(playable('n0', roots, server(...deep)), false, 'too deep is refused, never walked forever');
});
test('a server that fails or throws refuses play (fail closed), and the answer comes once', () => {
  assert.strictEqual(playable('film', ['kids'], (id, cb) => cb('offline')), false);
  assert.strictEqual(playable('film', ['kids'], () => { throw new Error('boom'); }), false);
  const answers = [];
  Kids.playable('film', ['kids'], (id, cb) => { cb(null, item('film', 'kids', 'kids')); cb('late'); }, (ok) => answers.push(ok));
  assert.deepStrictEqual(answers, [true]);
});

test('only approved apps open in Kids and Adults are unlimited', () => {
  const approved = Kids.apps('netflix,app:3201907018807,not an app,,../x,netflix');
  assert.deepStrictEqual(approved, ['netflix', 'app:3201907018807']);
  assert.ok(Kids.appAllowed(true, approved, 'netflix'));
  assert.ok(Kids.appAllowed(true, approved, 'app:3201907018807'));
  assert.ok(!Kids.appAllowed(true, approved, 'disney'));
  assert.ok(!Kids.appAllowed(true, approved, '')); assert.ok(!Kids.appAllowed(true, approved, null)); assert.ok(!Kids.appAllowed(true, null, 'netflix'));
  assert.ok(Kids.appAllowed(false, [], 'disney'));
});
test('toggling an app adds or removes it once', () => {
  let s = Kids.toggle('', 'netflix'); assert.strictEqual(s, 'netflix');
  s = Kids.toggle(s, 'tvplus'); assert.strictEqual(s, 'netflix,tvplus');
  assert.strictEqual(Kids.toggle(s, 'netflix'), 'tvplus');
});

// KIDS-02: one central launch check. Every launch in app.js goes through openTile, and openTile asks first.
test('app.js has exactly one launch path, and it starts with the Kids check', () => {
  const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'js', 'app.js'), 'utf8');
  assert.strictEqual((src.match(/U\.Apps\.launch\(/g) || []).length, 1, 'one call to launch');
  const open = src.indexOf('function openTile(id) {');
  assert.ok(open !== -1 && src.indexOf('U.Apps.launch(', open) > open, 'the launch is inside openTile');
  assert.match(src.slice(open, open + 200), /^function openTile\(id\) \{\s*if \(!launchAllowed\(id\)\)/, 'the first thing openTile does is the Kids check');
  assert.match(src, /function launchAllowed\(id\) \{ return U\.Kids\.appAllowed\(kids\(\), approvedApps\(\), id\); \}/);
  assert.match(src, /there is no Kids app guard on this TV/, 'the Kids settings say honestly what the Frame can\'t do');
});

// The catalogue item model (SEARCH-READY-01).
test('one catalogue item shape, from a DLNA entry, with no address or path as its id', () => {
  const ctl = 'http://media-server.local:8200/cd/control';
  const i = Catalogue.fromDlna({ id: '64$1$2', title: 'A Film', parent: '64$1', ratings: ['PG'], upnpClass: 'object.item.videoItem.movie' }, ctl, '64$1');
  assert.deepStrictEqual(Object.keys(i).sort(), ['folder', 'id', 'kind', 'poster', 'ratings', 'root', 'source', 'stableId', 'title', 'year']);
  assert.strictEqual(i.kind, 'film'); assert.strictEqual(i.folder, '64$1'); assert.strictEqual(i.root, '64$1'); assert.deepStrictEqual(i.ratings, ['PG']);
  assert.strictEqual(i.stableId, 'dlna:' + ctl + ':64$1$2');
  assert.strictEqual(Catalogue.dlnaKind('object.item.videoItem', false), 'video', 'never guessed to be an episode');
  assert.strictEqual(Catalogue.dlnaKind('object.container', true), 'folder');
  assert.strictEqual(Catalogue.item('dlna', 's', 'x', 't', '', 'nonsense', '', '', null, '').kind, 'video');
});

// The DLNA detail the Kids path reads. The household path is unchanged: without detail the result is as before.
const DIDL = '&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dc="http://purl.org/dc/elements/1.1/"&gt;' +
  '&lt;container id="64$0$1" parentID="64$0"&gt;&lt;dc:title&gt;Grown-up&lt;/dc:title&gt;&lt;upnp:class&gt;object.container&lt;/upnp:class&gt;&lt;upnp:rating&gt;15&lt;/upnp:rating&gt;&lt;/container&gt;' +
  '&lt;item id="64$0$2" parentID="64$0"&gt;&lt;dc:title&gt;A Film&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem.movie&lt;/upnp:class&gt;&lt;upnp:rating&gt;PG&lt;/upnp:rating&gt;' +
  '&lt;res protocolInfo="http-get:*:video/mp4:*"&gt;http://media-server.local:8200/MediaItems/2.mp4&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;';
const REPLY = '<s:Envelope><s:Body><u:BrowseResponse><Result>' + DIDL + '</Result><NumberReturned>2</NumberReturned><TotalMatches>2</TotalMatches></u:BrowseResponse></s:Body></s:Envelope>';
test('Kids browsing reads each object\'s parent and ratings; the household path is unchanged', () => {
  const plain = Dlna.parseBrowse(REPLY);
  assert.deepStrictEqual(plain.containers, [{ id: '64$0$1', title: 'Grown-up' }], 'without detail: exactly as before');
  assert.strictEqual(plain.items[0].ratings, undefined);
  const kids = Dlna.parseBrowse(REPLY, true);
  assert.deepStrictEqual([kids.containers[0].parent, kids.containers[0].ratings], ['64$0', ['15']]);
  assert.deepStrictEqual([kids.items[0].parent, kids.items[0].ratings, kids.items[0].upnpClass], ['64$0', ['PG'], 'object.item.videoItem.movie']);
  assert.match(Dlna.browseBody('64$0', 0, 1, 'BrowseMetadata'), /<BrowseFlag>BrowseMetadata<\/BrowseFlag>/);
  assert.match(Dlna.browseBody('64$0', 0, 1), /<BrowseFlag>BrowseDirectChildren<\/BrowseFlag>/, 'the household browse is unchanged');
});
test('BrowseMetadata gives the server\'s own parent for the play-time walk', () => {
  const meta = '<s:Envelope><s:Body><u:BrowseResponse><Result>&lt;DIDL-Lite&gt;&lt;container id="64$1" parentID="64"&gt;&lt;dc:title&gt;Kids&lt;/dc:title&gt;&lt;upnp:class&gt;object.container&lt;/upnp:class&gt;&lt;/container&gt;&lt;/DIDL-Lite&gt;</Result><NumberReturned>1</NumberReturned><TotalMatches>1</TotalMatches></u:BrowseResponse></s:Body></s:Envelope>';
  assert.deepStrictEqual(Dlna.parseMetadata(meta), { id: '64$1', title: 'Kids', container: true, parent: '64', ratings: [], upnpClass: 'object.container' });
  assert.strictEqual(Dlna.parseMetadata('<x/>'), null);
  const sent = [];
  const client = new Dlna.Client((method, url, headers, body, done) => { sent.push(body); done(null, meta); });
  let got = null; client.metadata('http://media-server.local:8200/cd/control', '64$1', (err, o) => { got = [err, o && o.parent]; });
  assert.deepStrictEqual(got, [null, '64']); assert.match(sent[0], /BrowseMetadata/);
  const offline = new Dlna.Client((m, u, h, b, done) => done('network'));
  offline.metadata('http://media-server.local:8200/cd/control', '64$1', (err) => { got = err; });
  assert.strictEqual(got, 'offline');
});

// Kids folders and the PIN are secrets: Key Manager only, fail closed.
test('Kids folders and the PIN are kept like the other secrets; "Forget all connections" keeps the PIN', () => {
  assert.deepStrictEqual(Secrets.NAMES, ['ha-token', 'nas', 'nas-kids', 'pin']);
  const store = {};
  const km = {
    saveData: (alias, value, pw, ok) => { store[alias] = value; ok(); },
    getData: (alias) => { const n = typeof alias === 'string' ? alias : alias.name; if (!(n in store)) throw new Error('none'); return store[n]; },
    removeAlias: (alias) => { delete store[typeof alias === 'string' ? alias : alias.name]; }
  };
  const v = new Secrets.Vault({ tizen: { keymanager: km } });
  v.put('nas-kids', JSON.stringify({ controlUrl: 'x', roots: [{ id: '64$1', title: 'Kids' }] }));
  v.put('pin', 'pbkdf2-sha256$1$AAAAAAAAAAA=$' + 'A'.repeat(43) + '=');
  v.put('ha-token', 'example-value');
  v.forgetConnections();
  const fresh = new Secrets.Vault({ tizen: { keymanager: km } });
  assert.strictEqual(fresh.get('nas-kids'), null); assert.strictEqual(fresh.get('ha-token'), null);
  assert.ok(fresh.get('pin'), 'the adult PIN stays');
  fresh.forgetAll(); assert.strictEqual(new Secrets.Vault({ tizen: { keymanager: km } }).get('pin'), null, 'Reset all settings forgets it too');
  const none = new Secrets.Vault({ tizen: {} });
  assert.ok(!none.remembers(), 'no Key Manager: nothing is remembered, so app.js offers no PIN and no Kids');
});
