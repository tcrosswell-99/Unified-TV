'use strict';
// Description validation (MediaServer with a ContentDirectory on the same private host) and Browse parsing.
const test = require('node:test');
const assert = require('node:assert');
const S = require('../service/service.js').internals;
const { ip, description, browseReply, folder, video } = require('./fixtures.js');

const NAS = ip(192, 168, 1, 20);
const LOC = 'http://' + NAS + ':50001/desc/device.xml';

test('a media server with a ContentDirectory gives its name, model, UDN and control address', () => {
  const d = S.parseDescription(description(), LOC);
  assert.deepStrictEqual(d, { ok: true, udn: 'uuid:4d696e69-444c-164e-9d41-000000000001', name: 'Family NAS', model: 'DS220+', controlUrl: 'http://' + NAS + ':50001/cd/control' });
  const escaped = S.parseDescription(description({ name: 'Films &amp; TV' }), LOC);
  assert.strictEqual(escaped.name, 'Films & TV');
  const based = S.parseDescription(description({ base: 'http://' + NAS + ':8200/', services: [['urn:schemas-upnp-org:service:ContentDirectory:1', 'ctl/ContentDir']] }), 'http://' + NAS + ':8200/rootDesc.xml');
  assert.strictEqual(based.controlUrl, 'http://' + NAS + ':8200/ctl/ContentDir');
});

test('anything else is set aside with a code', () => {
  assert.strictEqual(S.parseDescription(description({ type: 'urn:schemas-upnp-org:device:Printer:1' }), LOC).code, 'NOT_MEDIA_SERVER');
  assert.strictEqual(S.parseDescription(description({ type: 'urn:schemas-upnp-org:device:MediaRenderer:1' }), LOC).code, 'NOT_MEDIA_SERVER');
  assert.strictEqual(S.parseDescription(description({ services: [['urn:schemas-upnp-org:service:ConnectionManager:1', '/cm']] }), LOC).code, 'NO_CONTENT_DIRECTORY');
  assert.strictEqual(S.parseDescription(description({ services: [['urn:schemas-upnp-org:service:ContentDirectory:1', 'http://' + ip(192, 168, 1, 99) + '/cd']] }), LOC).code, 'CONTROL_HOST_MISMATCH');
  assert.strictEqual(S.parseDescription(description({ base: 'http://' + ip(203, 0, 113, 9) + '/' }), LOC).code, 'CONTROL_HOST_MISMATCH');
  assert.strictEqual(S.parseDescription(description({ services: [['urn:schemas-upnp-org:service:ContentDirectory:1', 'https://' + NAS + '/cd']] }), LOC).code, 'CONTROL_HOST_MISMATCH');
  assert.strictEqual(S.parseDescription('<html>login</html>', LOC).code, 'DESC_NOT_XML');
  assert.strictEqual(S.parseDescription(null, LOC).code, 'DESC_NOT_XML');
});

test('Browse replies give folders and an item count; CDATA and escaped DIDL both work', () => {
  const r = S.parseBrowse(browseReply(folder('1', 'Films') + folder('2', 'TV &amp; Series') + video('v1', 'Clip'), 3, 3));
  assert.strictEqual(r.ok, true);
  assert.deepStrictEqual(r.containers.map((c) => [c.id, c.title]), [['1', 'Films'], ['2', 'TV & Series']]);
  assert.strictEqual(r.items, 1); assert.strictEqual(r.returned, 3); assert.strictEqual(r.total, 3);
  const cdata = '<s:Envelope><s:Body><u:BrowseResponse><Result><![CDATA[<DIDL-Lite>' + folder('9', 'Kids') + '</DIDL-Lite>]]></Result><NumberReturned>1</NumberReturned><TotalMatches>1</TotalMatches></u:BrowseResponse></s:Body></s:Envelope>';
  assert.deepStrictEqual(S.parseBrowse(cdata).containers.map((c) => c.title), ['Kids']);
  assert.strictEqual(S.parseBrowse('<s:Fault>no</s:Fault>').code, 'BROWSE_BAD_REPLY');
  assert.strictEqual(S.parseBrowse(undefined).code, 'BROWSE_BAD_REPLY');
});

test('the Browse request asks for the direct children of one object, escaped', () => {
  const b = S.browseBody('64$1&<x>', 0, 50);
  assert.match(b, /<ObjectID>64\$1&amp;&lt;x&gt;<\/ObjectID>/);
  assert.match(b, /<BrowseFlag>BrowseDirectChildren<\/BrowseFlag>/);
  assert.match(b, /<StartingIndex>0<\/StartingIndex><RequestedCount>50<\/RequestedCount>/);
});
