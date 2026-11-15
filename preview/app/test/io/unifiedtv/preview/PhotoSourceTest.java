package io.unifiedtv.preview;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URI;
import java.util.*;

/** My Photos Phase 1 (docs/design/MY_PHOTOS.md): pictures only, the right size, and never outside the chosen folders (WP-03). */
public class PhotoSourceTest {
    // Documentation addresses (RFC 5737) and synthetic entries only.
    static final URI ORIGIN = URI.create("http://192.0.2.10:50001/ctl/ContentDir");
    /** The same-server rule without DlnaClient's home-network check, which documentation addresses would (rightly) fail. */
    static String sameHost(String ref) { try { URI u = ORIGIN.resolve(ref.trim()); return ORIGIN.getHost().equals(u.getHost()) ? u.toString() : ""; } catch (Exception e) { return ""; } }
    static PhotoSource.Page parse(String didl, int returned, int total) throws Exception { return PhotoSource.parse(DlnaClientTest.soap(didl, returned, total), PhotoSourceTest::sameHost); }

    static String picture(String id, String res) { return "<item id='" + id + "'><dc:title>IMG_" + id + ".jpg</dc:title><upnp:class>object.item.imageItem.photo</upnp:class>" + res + "</item>"; }
    static String res(String mime, String extra, String path) { return "<res protocolInfo='http-get:*:" + mime + ":" + extra + "'>" + path + "</res>"; }

    @Test public void onlyPictureItemsAndPictureFoldersAreKept() throws Exception {
        String didl = picture("p1", res("image/jpeg", "*", "/pic/1.jpg"))
            + "<item id='v1'><dc:title>Film</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo='http-get:*:video/mp4:*'>/media/v1.mp4</res></item>"
            + "<item id='a1'><dc:title>Song</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo='http-get:*:audio/mpeg:*'>/media/a1.mp3</res></item>"
            + "<item id='p2'><dc:title>Odd</dc:title><upnp:class>object.item.imageItem</upnp:class><res protocolInfo='http-get:*:video/mp4:*'>/media/p2.mp4</res></item>"
            + "<item id='p3'><dc:title>No class</dc:title>" + res("image/png", "*", "/pic/3.png") + "</item>"
            + "<item id='p4'><dc:title>Raw</dc:title><upnp:class>object.item.imageItem</upnp:class>" + res("image/x-canon-cr2", "*", "/pic/4.cr2") + "</item>"
            + "<item id='p5'><dc:title>Elsewhere</dc:title><upnp:class>object.item.imageItem</upnp:class>" + res("image/jpeg", "*", "http://198.51.100.7/pic/5.jpg") + "</item>"
            + "<item id='p6'><upnp:class>object.item.imageItem</upnp:class><res protocolInfo='rtsp-rtp-udp:*:image/jpeg:*'>/pic/6.jpg</res></item>"
            + "<container id='c1'><dc:title>Holidays</dc:title><upnp:class>object.container.storageFolder</upnp:class></container>"
            + "<container id='c2'><dc:title>Albums</dc:title><upnp:class>object.container.album.musicAlbum</upnp:class></container>"
            + "<container id='c3'><dc:title>Movies</dc:title><upnp:class>object.container.genre.movieGenre</upnp:class></container>";
        PhotoSource.Page page = parse(didl, 11, 11);
        List<String> ids = new ArrayList<>(); for (PhotoSource.Photo p : page.photos) ids.add(p.id);
        assertEquals(Arrays.asList("p1", "p3"), ids);
        assertEquals("http://192.0.2.10:50001/pic/1.jpg", page.photos.get(0).url);
        assertEquals(1, page.folders.size()); assertEquals("c1", page.folders.get(0).id); assertEquals("Holidays", page.folders.get(0).title);
        assertFalse(page.more(0, PhotoSource.PAGE));
    }

    @Test public void aServerOffTheHomeNetworkIsNeverUsed() throws Exception {
        // The app's own parse checks the address is on the home network; a documentation (public) address is not.
        assertTrue(PhotoSource.parse(DlnaClientTest.soap(picture("p1", res("image/jpeg", "*", "/pic/1.jpg")), 1, 1), ORIGIN).photos.isEmpty());
    }

    @Test public void theRenditionChosenFitsTheScreenWhenTheServerOffersOne() throws Exception {
        String three = res("image/jpeg", "DLNA.ORG_PN=JPEG_LRG", "/pic/orig.jpg").replace("<res ", "<res resolution='4000x3000' size='6000000' ")
            + res("image/jpeg", "DLNA.ORG_PN=JPEG_MED", "/pic/med.jpg") + res("image/jpeg", "DLNA.ORG_PN=JPEG_TN", "/pic/tn.jpg");
        PhotoSource.Photo p = parse(picture("p", three), 1, 1).photos.get(0);
        assertEquals("http://192.0.2.10:50001/pic/med.jpg", p.url); assertEquals(1024, p.w);
        String hd = res("image/jpeg", "*", "/pic/hd.jpg").replace("<res ", "<res resolution='1920x1080' ") + res("image/jpeg", "DLNA.ORG_PN=JPEG_MED", "/pic/med.jpg");
        assertEquals("the biggest that still fits", "http://192.0.2.10:50001/pic/hd.jpg", parse(picture("p", hd), 1, 1).photos.get(0).url);
    }

    static PhotoSource.Rendition r(String url, int w, int h) { return new PhotoSource.Rendition(url, "image/jpeg", w, h, 0); }
    @Test public void renditionOrder() {
        assertEquals("portrait screen-sized copy fits", "b", PhotoSource.choose(Arrays.asList(r("a", 3024, 4032), r("b", 1080, 1440), r("c", 160, 160))).url);
        assertEquals("smallest larger copy, decoded down", "b", PhotoSource.choose(Arrays.asList(r("a", 6000, 4000), r("b", 2400, 1600), r("c", 640, 480))).url);
        assertEquals("unknown size (the original) before a thumbnail", "u", PhotoSource.choose(Arrays.asList(r("t", 160, 160), r("u", 0, 0))).url);
        assertEquals("only small copies: the largest", "s", PhotoSource.choose(Arrays.asList(r("t", 160, 160), r("s", 640, 480))).url);
        assertEquals("a huge file is never fetched", "ok", PhotoSource.choose(Arrays.asList(new PhotoSource.Rendition("huge", "image/jpeg", 0, 0, PhotoSource.MAX_BYTES + 1), r("ok", 640, 480))).url);
        assertNull(PhotoSource.choose(Collections.emptyList()));
    }

    @Test public void decodingNeverExceedsOneScreen() {
        assertEquals(1, PhotoSource.sampleSize(1920, 1080)); assertEquals(1, PhotoSource.sampleSize(1080, 1920));
        assertEquals(2, PhotoSource.sampleSize(3840, 2160));
        assertEquals(4, PhotoSource.sampleSize(4000, 3000)); assertEquals(4, PhotoSource.sampleSize(3024, 4032));
        for (int[] s : new int[][]{{8000, 6000}, {12000, 1000}, {1000, 12000}, {2000, 2000}}) { int n = PhotoSource.sampleSize(s[0], s[1]); assertTrue(PhotoSource.fits(s[0] / n, s[1] / n)); }
        assertArrayEquals(new int[]{1920, 1080}, PhotoSource.resolution("1920x1080"));
        assertArrayEquals(new int[]{0, 0}, PhotoSource.resolution("big"));
        assertArrayEquals(new int[]{640, 480}, PhotoSource.profile("DLNA.ORG_PN=JPEG_SM;DLNA.ORG_OP=01"));
    }

    @Test public void exifOrientationTurnsPhotosUpright() {
        assertArrayEquals(new int[]{0, 0}, PhotoSource.orientation(1)); assertArrayEquals(new int[]{90, 0}, PhotoSource.orientation(6));
        assertArrayEquals(new int[]{270, 0}, PhotoSource.orientation(8)); assertArrayEquals(new int[]{180, 0}, PhotoSource.orientation(3));
        assertArrayEquals(new int[]{0, 1}, PhotoSource.orientation(2)); assertArrayEquals(new int[]{0, 0}, PhotoSource.orientation(99));
    }

    /** A synthetic server: folder id → (subfolders, picture count). Records every folder asked for. */
    static final class Fake implements PhotoSource.Browser {
        final Map<String, List<String>> sub = new HashMap<>(); final Map<String, Integer> pics = new HashMap<>(); final List<String> asked = new ArrayList<>();
        final Set<String> down = new HashSet<>();
        Fake folder(String id, int pictures, String... children) { sub.put(id, Arrays.asList(children)); pics.put(id, pictures); return this; }
        @Override public PhotoSource.Page browse(String id, int offset) throws Exception {
            asked.add(id); if (down.contains(id) || down.contains("*")) throw new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE);
            // Entries are the subfolders, then the pictures, served PAGE at a time; an endless folder reports no total.
            PhotoSource.Page p = new PhotoSource.Page(); List<String> kids = sub.getOrDefault(id, Collections.emptyList()); int n = pics.getOrDefault(id, 0);
            long all = (long) kids.size() + n;
            for (long i = offset; i < offset + PhotoSource.PAGE && i < all; i++) {
                if (i < kids.size()) { DlnaClient.Item f = new DlnaClient.Item(); f.id = kids.get((int) i); f.title = f.id; f.folder = true; p.folders.add(f); }
                else { long k = i - kids.size(); p.photos.add(new PhotoSource.Photo(id + "/" + k, r("http://192.0.2.10/" + id + "/" + k + ".jpg", 1920, 1080))); }
            }
            p.returned = p.folders.size() + p.photos.size(); p.total = n == Integer.MAX_VALUE ? 0 : (int) all; return p;
        }
    }
    static JSONArray roots(String... ids) throws Exception { JSONArray a = new JSONArray(); for (String id : ids) a.put(new JSONObject().put("id", id).put("name", "Folder")); return a; }

    @Test public void theWalkStaysInsideTheChosenFolders() throws Exception {
        Fake nas = new Fake().folder("0", 0, "photo", "video", "private").folder("photo", 2, "holidays", "family")
            .folder("holidays", 3, "holidays/beach", "0").folder("holidays/beach", 2, "holidays").folder("family", 4).folder("private", 9);
        List<PhotoSource.Photo> got = PhotoSource.collect(roots("holidays"), nas);
        Set<String> ids = new HashSet<>(); for (PhotoSource.Photo p : got) ids.add(p.id);
        assertEquals(new HashSet<>(Arrays.asList("holidays/0", "holidays/1", "holidays/2", "holidays/beach/0", "holidays/beach/1")), ids);
        // A server may list anything as a "subfolder"; the walk still never opens the top level, and opens each folder once.
        assertFalse(nas.asked.contains("0")); assertFalse(nas.asked.contains("photo")); assertFalse(nas.asked.contains("family"));
        assertEquals(1, Collections.frequency(nas.asked, "holidays"));
    }

    @Test public void theServerTopLevelIsNeverAChosenFolder() throws Exception {
        Fake nas = new Fake().folder("0", 5, "a").folder("a", 5);
        assertTrue(PhotoSource.collect(roots("0", ""), nas).isEmpty()); assertTrue(nas.asked.isEmpty());
        assertTrue(PhotoSource.collect(null, nas).isEmpty());
    }

    @Test public void pagesAreFollowedAndTheWalkIsBounded() throws Exception {
        Fake nas = new Fake().folder("big", 250);
        assertEquals(250, PhotoSource.collect(roots("big"), nas).size()); assertEquals(3, nas.asked.size());
        Fake endless = new Fake().folder("all", Integer.MAX_VALUE);
        assertEquals(PhotoSource.MAX_PHOTOS, PhotoSource.collect(roots("all"), endless).size()); assertTrue(endless.asked.size() <= PhotoSource.MAX_REQUESTS);
        Fake deep = new Fake(); for (int i = 0; i < 20; i++) deep.folder("d" + i, 1, "d" + (i + 1));
        assertEquals(PhotoSource.MAX_DEPTH, PhotoSource.collect(roots("d0"), deep).size());
    }

    @Test public void anAbsentNasIsReportedAndAPartlyMissingOneIsNot() throws Exception {
        Fake off = new Fake().folder("a", 3); off.down.add("*");
        try { PhotoSource.collect(roots("a"), off); fail("an absent NAS must be reported"); } catch (DlnaClient.Failure f) { assertEquals(DlnaClient.Failure.UNREACHABLE, f.kind); }
        Fake partly = new Fake().folder("a", 3).folder("gone", 1); partly.down.add("gone");
        assertEquals(3, PhotoSource.collect(roots("gone", "a"), partly).size());
    }

    @Test public void cacheKeysCarryNoNames() {
        String key = PhotoSource.key("uuid:example-nas", "64$7$2"); String name = PhotoCache.name(key);
        assertTrue(name.matches("[0-9a-f]{32}\\.jpg")); assertEquals(name, PhotoCache.name(PhotoSource.key("uuid:example-nas", "64$7$2")));
        assertNotEquals(name, PhotoCache.name(PhotoSource.key("uuid:example-nas", "64$7$3")));
    }
}
