/*
 * Unified TV for Samsung Frame: one catalogue item model (owner, 25 October 2026: universal search later).
 *
 * A port of Android's CatalogueItem (preview/app/src/io/unifiedtv/preview/CatalogueItem.java): the NAS library, the
 * Kids policy and local resume use one shape, so a search index can be added later without rebuilding them. No search,
 * no streaming services, nothing stored here: it is a plain value, made on demand.
 * stableId is source, server and the server's own id: never a file path. On the Frame the "server" is the media
 * server's control address the owner typed, which never leaves the TV.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Catalogue = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const KINDS = ['film', 'episode', 'series', 'video', 'folder'];
  function str(v) { return typeof v === 'string' ? v : ''; }

  /** { source, stableId, id, title, year, kind, folder, root, ratings, poster } */
  function item(source, server, id, title, year, kind, folder, root, ratings, poster) {
    const s = str(source), i = str(id);
    return {
      source: s, stableId: s + ':' + str(server) + ':' + i, id: i, title: str(title), year: str(year),
      kind: KINDS.indexOf(kind) !== -1 ? kind : 'video', folder: str(folder), root: str(root),
      ratings: Array.isArray(ratings) ? ratings.filter(function (r) { return typeof r === 'string'; }) : [], poster: str(poster)
    };
  }
  /** UPnP's own class names; anything not plainly a film is a video, never guessed to be an episode. */
  function dlnaKind(upnpClass, folder) {
    if (folder) return 'folder';
    return str(upnpClass).toLowerCase().indexOf('object.item.videoitem.movie') === 0 ? 'film' : 'video';
  }
  /** A DLNA entry (dlna.js with detail) found inside the saved folder root on the server. DLNA gives no poster or year. */
  function fromDlna(entry, server, root) {
    const e = entry || {};
    return item('dlna', server, e.id, e.title, '', dlnaKind(e.upnpClass, !!e.container), e.parent, root, e.ratings, '');
  }

  return { KINDS: KINDS, item: item, dlnaKind: dlnaKind, fromDlna: fromDlna };
}));
