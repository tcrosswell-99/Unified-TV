/*
 * Unified TV for Samsung Frame: the NAS over DLNA, by HTTP only (spec 9, LIB-01-03).
 *
 * Platform limit, stated plainly: a Tizen web app can't send the multicast UDP (SSDP) that finds media servers on the
 * network, so on the Frame Unified TV can't discover the NAS by itself. The owner enters the server's description
 * address once (Settings > Connections > Films & TV), and from then on this module talks plain HTTP to it: read the
 * description, find the ContentDirectory service and Browse it with SOAP. Nothing here pretends to discover anything.
 * Only the folders the owner chose are shown, by their DLNA object IDs (not file paths), and only video items.
 * Pages follow the server's NumberReturned and TotalMatches. DLNA gives titles and files; it does not give reliable
 * posters, search, series structure or synchronised progress, and none is invented.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Dlna = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const CDS = 'urn:schemas-upnp-org:service:ContentDirectory:1';
  const PAGE = 100;
  const MAX_ROOTS = 3;
  const FIND_YOUR_NAS = 'Samsung TVs don\'t let apps search the network for media servers, so Unified TV can\'t find your NAS by itself here. ' +
    'Enter your NAS\'s media server description address once (your NAS\'s media server settings show it, often ending in .xml). ' +
    'Unified TV then remembers it and lists your chosen folders.';

  function decode(s) {
    return String(s).replace(/&#x([0-9a-f]+);/gi, function (m, h) { return String.fromCharCode(parseInt(h, 16)); })
      .replace(/&#(\d+);/g, function (m, d) { return String.fromCharCode(parseInt(d, 10)); })
      .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, '\'').replace(/&amp;/g, '&');
  }
  function encode(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;'); }
  function tag(xml, name) {
    const m = new RegExp('<(?:[A-Za-z0-9]+:)?' + name + '(?:\\s[^>]*)?>([\\s\\S]*?)</(?:[A-Za-z0-9]+:)?' + name + '>').exec(xml);
    return m ? m[1] : null;
  }
  function attr(openTag, name) {
    const m = new RegExp('\\s' + name + '="([^"]*)"').exec(openTag);
    return m ? decode(m[1]) : null;
  }
  function absolute(href, base) {
    try { return new URL(href, base).toString(); } catch (e) { return null; }
  }
  /** A description address must be plain http(s) with no user name or password in it. */
  function validDescriptionUrl(url) {
    if (typeof url !== 'string' || !/^https?:\/\/[^\s/?#@]+(\/[^\s]*)?$/i.test(url.trim())) return false;
    return true;
  }

  /** The server's name and its ContentDirectory control address, or null. */
  function parseDescription(xml, descriptionUrl) {
    if (typeof xml !== 'string') return null;
    const name = tag(xml, 'friendlyName');
    const base = tag(xml, 'URLBase');
    const services = xml.match(/<(?:[A-Za-z0-9]+:)?service>[\s\S]*?<\/(?:[A-Za-z0-9]+:)?service>/g) || [];
    for (let i = 0; i < services.length; i++) {
      const type = tag(services[i], 'serviceType');
      const control = tag(services[i], 'controlURL');
      if (type && type.trim().indexOf('urn:schemas-upnp-org:service:ContentDirectory:') === 0 && control) {
        const url = absolute(decode(control.trim()), base ? decode(base.trim()) : descriptionUrl);
        if (url && /^https?:/i.test(url)) return { name: name ? decode(name.trim()) : 'Media server', controlUrl: url };
      }
    }
    return null;
  }
  /** The SOAP Browse request for one page of a folder's children (or, with flag BrowseMetadata, the object itself). */
  function browseBody(objectId, start, count, flag) {
    const browseFlag = flag === 'BrowseMetadata' ? 'BrowseMetadata' : 'BrowseDirectChildren';
    return '<?xml version="1.0" encoding="utf-8"?>' +
      '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>' +
      '<u:Browse xmlns:u="' + CDS + '"><ObjectID>' + encode(objectId) + '</ObjectID><BrowseFlag>' + browseFlag + '</BrowseFlag>' +
      '<Filter>*</Filter><StartingIndex>' + (start | 0) + '</StartingIndex><RequestedCount>' + (count | 0) + '</RequestedCount>' +
      '<SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>';
  }
  /** Every upnp:rating on one DIDL-Lite object, as the server wrote them (Kids reads them; nothing else does). */
  function ratingsIn(element) {
    const out = [];
    const re = /<(?:[A-Za-z0-9]+:)?rating\b[^>]*>([\s\S]*?)<\/(?:[A-Za-z0-9]+:)?rating>/g;
    let m;
    while ((m = re.exec(element)) !== null) { const r = decode(m[1].trim()); if (r) out.push(r); }
    return out;
  }
  /** Kids only (detail): the server's own parent id, ratings and UPnP class for one object. */
  function details(target, open, element) {
    target.parent = attr(open, 'parentID') || '';
    target.ratings = ratingsIn(element);
    target.upnpClass = (tag(element, 'class') || '').trim();
    return target;
  }
  function didlOf(result) {
    const raw = result.replace(/^\s*<!\[CDATA\[([\s\S]*)\]\]>\s*$/, '$1');
    return /<DIDL-Lite/.test(raw) ? raw : decode(raw);
  }
  /**
   * One Browse reply: folders, video items, and the server's own counts. With detail (Kids browsing only), each folder
   * and item also carries its parent id, ratings and UPnP class; without it the result is exactly as before.
   */
  function parseBrowse(xml, detail) {
    if (typeof xml !== 'string') return null;
    const result = tag(xml, 'Result');
    const returned = parseInt(tag(xml, 'NumberReturned'), 10);
    const total = parseInt(tag(xml, 'TotalMatches'), 10);
    if (result === null || isNaN(returned)) return null;
    const didl = didlOf(result);
    const containers = [], items = [];
    const containerTags = didl.match(/<container\b[^>]*>[\s\S]*?<\/container>/g) || [];
    containerTags.forEach(function (c) {
      const open = c.slice(0, c.indexOf('>') + 1);
      const id = attr(open, 'id');
      if (id === null) return;
      const folder = { id: id, title: decode((tag(c, 'title') || '').trim()) || 'Untitled folder' };
      containers.push(detail ? details(folder, open, c) : folder);
    });
    const itemTags = didl.match(/<item\b[^>]*>[\s\S]*?<\/item>/g) || [];
    itemTags.forEach(function (it) {
      const open = it.slice(0, it.indexOf('>') + 1);
      const cls = (tag(it, 'class') || '').trim();
      const resMatch = /<res\b([^>]*)>([\s\S]*?)<\/res>/.exec(it);
      const protocol = resMatch ? attr(' ' + resMatch[1], 'protocolInfo') || '' : '';
      const video = cls.indexOf('object.item.videoItem') === 0 || /:video\//i.test(protocol);
      if (!video || !resMatch) return;
      const entry = {
        id: attr(open, 'id'), title: decode((tag(it, 'title') || '').trim()) || 'Untitled video',
        url: decode(resMatch[2].trim()), duration: attr(' ' + resMatch[1], 'duration') || '', protocol: protocol
      };
      items.push(detail ? details(entry, open, it) : entry);
    });
    return { containers: containers, items: items, returned: returned, total: isNaN(total) ? returned : total };
  }
  /**
   * A BrowseMetadata reply: the one object asked about, folder or item of any class, as
   * { id, title, container, parent, ratings, upnpClass }; null when the reply holds none. Used by the Kids play-time
   * check to walk the server's own parent chain.
   */
  function parseMetadata(xml) {
    if (typeof xml !== 'string') return null;
    const result = tag(xml, 'Result');
    if (result === null) return null;
    const didl = didlOf(result);
    const m = /<(container|item)\b[^>]*>[\s\S]*?<\/\1>/.exec(didl);
    if (!m) return null;
    const element = m[0];
    const open = element.slice(0, element.indexOf('>') + 1);
    const id = attr(open, 'id');
    if (id === null) return null;
    return details({ id: id, title: decode((tag(element, 'title') || '').trim()), container: m[1] === 'container' }, open, element);
  }

  /** Spec 9: only the owner's chosen folders are collections, at most three, by object ID, no duplicates. */
  function collections(roots) {
    const out = [], seen = {};
    (Array.isArray(roots) ? roots : []).forEach(function (r) {
      if (r && typeof r.id === 'string' && r.id && !seen[r.id] && out.length < MAX_ROOTS) { seen[r.id] = true; out.push({ id: r.id, title: String(r.title || 'Folder') }); }
    });
    return out;
  }

  /**
   * request(method, url, headers, body, done(error, text)) does the HTTP (XMLHttpRequest on the TV, a fake in tests).
   */
  class Client {
    constructor(request) { this.request = request; }
    describe(descriptionUrl, done) {
      if (!validDescriptionUrl(descriptionUrl)) { done('address'); return; }
      this.request('GET', descriptionUrl.trim(), {}, null, function (err, text) {
        if (err) { done('offline'); return; }
        const d = parseDescription(text, descriptionUrl.trim());
        done(d ? null : 'not-a-media-server', d);
      });
    }
    /** Kids only: one object's own metadata (parent and ratings). done(error, object). */
    metadata(controlUrl, objectId, done) {
      this.request('POST', controlUrl, { 'Content-Type': 'text/xml; charset="utf-8"', SOAPACTION: '"' + CDS + '#Browse"' },
        browseBody(objectId, 0, 1, 'BrowseMetadata'), function (err, text) {
          if (err) { done('offline'); return; }
          const o = parseMetadata(text);
          done(o ? null : 'bad-reply', o);
        });
    }
    /** Every child of a folder, page by page, following NumberReturned and TotalMatches. detail: Kids browsing only. */
    browseAll(controlUrl, objectId, done, pageSize, detail) {
      const size = pageSize || PAGE;
      const self = this;
      const all = { containers: [], items: [], total: 0 };
      let start = 0, pages = 0;
      (function page() {
        self.request('POST', controlUrl, { 'Content-Type': 'text/xml; charset="utf-8"', SOAPACTION: '"' + CDS + '#Browse"' },
          browseBody(objectId, start, size), function (err, text) {
            if (err) { done('offline'); return; }
            const r = parseBrowse(text, detail);
            if (!r) { done('bad-reply'); return; }
            all.containers = all.containers.concat(r.containers); all.items = all.items.concat(r.items); all.total = r.total;
            start += r.returned; pages++;
            if (r.returned > 0 && start < r.total && pages < 200) page();
            else done(null, all);
          });
      }());
    }
  }

  function message(error) {
    if (error === 'address') return 'Enter the full description address, starting with http:// or https://.';
    if (error === 'offline') return 'The media server didn\'t answer. Check it is switched on and on the same network, then try again.';
    if (error === 'not-a-media-server') return 'That address answered, but it isn\'t a media server Unified TV can browse.';
    if (error === 'bad-reply') return 'The media server sent a reply Unified TV couldn\'t read.';
    return '';
  }

  return { CDS: CDS, PAGE: PAGE, MAX_ROOTS: MAX_ROOTS, FIND_YOUR_NAS: FIND_YOUR_NAS, decode: decode, validDescriptionUrl: validDescriptionUrl,
    parseDescription: parseDescription, browseBody: browseBody, parseBrowse: parseBrowse, parseMetadata: parseMetadata, collections: collections,
    Client: Client, message: message };
}));
