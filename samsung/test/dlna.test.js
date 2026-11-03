'use strict';
// DLNA over HTTP (spec 9, LIB-01-03): no discovery is claimed; the description is read, ContentDirectory is browsed with
// SOAP, pages follow NumberReturned and TotalMatches, only video items and only the owner's folders are shown.
const test = require('node:test');
const assert = require('node:assert');
const Dlna = require('../src/js/dlna.js');

const DESCRIPTION = '<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device>' +
  '<friendlyName>Family NAS &amp; Media</friendlyName><serviceList>' +
  '<service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><controlURL>/cm/control</controlURL></service>' +
  '<service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><controlURL>/cd/control</controlURL></service>' +
  '</serviceList></device></root>';

function didl(inner) {
  const x = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">' + inner + '</DIDL-Lite>';
  return x.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function reply(inner, returned, total) {
  return '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">' +
    '<Result>' + didl(inner) + '</Result><NumberReturned>' + returned + '</NumberReturned><TotalMatches>' + total + '</TotalMatches><UpdateID>1</UpdateID></u:BrowseResponse></s:Body></s:Envelope>';
}
const video = (id, title) => '<item id="' + id + '" parentID="64"><dc:title>' + title + '</dc:title><upnp:class>object.item.videoItem.movie</upnp:class>' +
  '<res protocolInfo="http-get:*:video/x-matroska:*" duration="1:42:10.000">http://media-server.local:8200/MediaItems/' + id + '.mkv</res></item>';

test('there is no pretend discovery: the limitation is stated in plain words', () => {
  assert.match(Dlna.FIND_YOUR_NAS, /can't find your NAS by itself/);
  assert.doesNotMatch(require('node:fs').readFileSync(require('node:path').join(__dirname, '..', 'src', 'js', 'dlna.js'), 'utf8'), /M-SEARCH|239\.255|createSocket|dgram/i);
});

test('the description gives the server name and the ContentDirectory control address', () => {
  const d = Dlna.parseDescription(DESCRIPTION, 'http://media-server.local:8200/rootDesc.xml');
  assert.deepStrictEqual(d, { name: 'Family NAS & Media', controlUrl: 'http://media-server.local:8200/cd/control' });
  assert.strictEqual(Dlna.parseDescription('<root><device><friendlyName>Printer</friendlyName></device></root>', 'http://printer.local/'), null);
  assert.ok(!Dlna.validDescriptionUrl('http://user:pw@media-server.local/desc.xml'));
  assert.ok(!Dlna.validDescriptionUrl('media-server.local/desc.xml'));
  assert.ok(Dlna.validDescriptionUrl('http://media-server.local:8200/rootDesc.xml'));
});

test('the Browse request escapes the object id and asks for one page', () => {
  const body = Dlna.browseBody('64$1&<x>', 100, 50);
  assert.match(body, /<ObjectID>64\$1&amp;&lt;x&gt;<\/ObjectID>/);
  assert.match(body, /<StartingIndex>100<\/StartingIndex><RequestedCount>50<\/RequestedCount>/);
  assert.match(body, /BrowseDirectChildren/);
});

test('a reply gives folders and videos only; music and photos are never listed', () => {
  const r = Dlna.parseBrowse(reply('<container id="64$0" parentID="64" childCount="3"><dc:title>Kids</dc:title><upnp:class>object.container</upnp:class></container>' +
    video('m1', 'A Film') + '<item id="s1"><dc:title>Song</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo="http-get:*:audio/mpeg:*">http://media-server.local/s1.mp3</res></item>' +
    '<item id="p1"><dc:title>Photo</dc:title><upnp:class>object.item.imageItem.photo</upnp:class><res protocolInfo="http-get:*:image/jpeg:*">http://media-server.local/p1.jpg</res></item>', 4, 4));
  assert.deepStrictEqual(r.containers, [{ id: '64$0', title: 'Kids' }]);
  assert.deepStrictEqual(r.items.map((i) => i.title), ['A Film']);
  assert.strictEqual(r.items[0].url, 'http://media-server.local:8200/MediaItems/m1.mkv');
  assert.strictEqual(r.items[0].duration, '1:42:10.000');
  assert.strictEqual(r.returned, 4); assert.strictEqual(r.total, 4);
  assert.strictEqual(Dlna.parseBrowse('<html>error</html>'), null);
});

test('pages follow NumberReturned and TotalMatches, not the number of accepted videos', () => {
  const pages = [
    reply(video('a', 'A') + '<item id="x"><dc:title>Song</dc:title><upnp:class>object.item.audioItem</upnp:class><res protocolInfo="http-get:*:audio/mpeg:*">http://m/x.mp3</res></item>', 2, 5),
    reply(video('b', 'B') + video('c', 'C'), 2, 5),
    reply(video('d', 'D'), 1, 5)
  ];
  const starts = [];
  const client = new Dlna.Client((method, url, headers, body, done) => {
    assert.strictEqual(method, 'POST'); assert.match(headers.SOAPACTION, /ContentDirectory:1#Browse/);
    starts.push(Number(/<StartingIndex>(\d+)/.exec(body)[1]));
    done(null, pages[starts.length - 1]);
  });
  let result = null;
  client.browseAll('http://media-server.local:8200/cd/control', '64', (err, all) => { result = [err, all]; }, 2);
  assert.deepStrictEqual(starts, [0, 2, 4]);
  assert.strictEqual(result[0], null);
  assert.deepStrictEqual(result[1].items.map((i) => i.title), ['A', 'B', 'C', 'D']);
});

test('a server that returns nothing more never loops forever', () => {
  let calls = 0;
  const client = new Dlna.Client((m, u, h, b, done) => { calls++; done(null, reply('', 0, 500)); });
  let err;
  client.browseAll('http://m/cd', '0', (e) => { err = e; });
  assert.strictEqual(calls, 1); assert.strictEqual(err, null);
});

test('offline and unreadable servers give plain messages', () => {
  const offline = new Dlna.Client((m, u, h, b, done) => done('network'));
  let err; offline.describe('http://media-server.local/desc.xml', (e) => { err = e; });
  assert.strictEqual(err, 'offline'); assert.match(Dlna.message(err), /didn't answer/);
  const odd = new Dlna.Client((m, u, h, b, done) => done(null, '<html/>'));
  odd.describe('http://media-server.local/desc.xml', (e) => { err = e; });
  assert.strictEqual(err, 'not-a-media-server');
  offline.describe('not an address', (e) => { err = e; });
  assert.strictEqual(err, 'address');
});

test('only the owner\'s chosen folders are collections: at most three, by object ID, no duplicates', () => {
  const roots = Dlna.collections([{ id: '64$0', title: 'Kids' }, { id: '64$1', title: 'Movie' }, { id: '64$0', title: 'Kids again' }, { id: '64$2', title: 'TV Show' }, { id: '64$3', title: 'Music' }, { title: 'no id' }]);
  assert.deepStrictEqual(roots.map((r) => r.id), ['64$0', '64$1', '64$2']);
  assert.deepStrictEqual(Dlna.collections(null), []);
});
