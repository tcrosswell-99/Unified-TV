package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

/** Spec 33, OD-03/OD-04: artwork by default, a calm place for the clock, and the depth clock only where a mask suits it. */
public class WallpaperPolicyTest {
    private static float[] flat(float v) { float[] g = new float[WallpaperPolicy.GW * WallpaperPolicy.GH]; java.util.Arrays.fill(g, v); return g; }

    @Test public void defaultsAreArtworkAutomaticDepthFiveMinutesAndUnknownValuesFallBack() {
        assertEquals(WallpaperPolicy.ARTWORK, WallpaperPolicy.source(null));
        assertEquals(WallpaperPolicy.OFF, WallpaperPolicy.source("off"));
        assertEquals(WallpaperPolicy.ARTWORK, WallpaperPolicy.source("sparkly"));
        assertEquals(WallpaperPolicy.PHOTOS, WallpaperPolicy.source("photos"));
        assertEquals(WallpaperPolicy.AUTO, WallpaperPolicy.clock("sparkly"));
        assertEquals(5, WallpaperPolicy.idle(7)); assertEquals(0, WallpaperPolicy.idle(0)); assertEquals(15, WallpaperPolicy.idle(15));
        assertEquals(300, WallpaperPolicy.slide(1)); assertEquals(30, WallpaperPolicy.slide(30));
    }

    @Test public void theSimpleClockGoesWhereThePictureIsCalmest() {
        float[] g = flat(.3f);
        // Busy detail everywhere except the lower right.
        for (int y = 0; y < WallpaperPolicy.GH; y++) for (int x = 0; x < WallpaperPolicy.GW; x++) if (!(x >= 10 && y >= 6)) g[y * WallpaperPolicy.GW + x] = (x + y) % 2 == 0 ? .1f : .9f;
        assertArrayEquals(new int[]{10, 6, 15, 8}, WallpaperPolicy.SIMPLE_SPOTS[WallpaperPolicy.calmest(g)[0]]);
    }

    @Test public void theClockMovesBetweenTheTwoCalmestSpotsOnEachSlide() {
        float[] g = flat(.3f);
        int[] first = WallpaperPolicy.spot(g, 0), second = WallpaperPolicy.spot(g, 1), third = WallpaperPolicy.spot(g, 2);
        assertNotSame(first, second); assertSame(first, third);
    }

    @Test public void brightSkiesGetAShadeBehindTheNumerals() {
        assertTrue(WallpaperPolicy.needsShade(flat(.8f), WallpaperPolicy.SIMPLE_SPOTS[0]));
        assertFalse(WallpaperPolicy.needsShade(flat(.2f), WallpaperPolicy.SIMPLE_SPOTS[0]));
    }

    @Test public void depthClockOnlyWithAMaskThatReallyOverlapsTheClock() {
        assertEquals("no mask: simple clock", WallpaperPolicy.SIMPLE, WallpaperPolicy.clockFor(WallpaperPolicy.AUTO, null));
        assertEquals("subject elsewhere", WallpaperPolicy.SIMPLE, WallpaperPolicy.clockFor(WallpaperPolicy.AUTO, flat(0)));
        assertEquals("subject would hide the time", WallpaperPolicy.SIMPLE, WallpaperPolicy.clockFor(WallpaperPolicy.AUTO, flat(1)));
        float[] head = flat(0);
        for (int y = 2; y < WallpaperPolicy.GH; y++) for (int x = 6; x < 10; x++) head[y * WallpaperPolicy.GW + x] = 1;   // a figure in the middle
        assertEquals("depth", WallpaperPolicy.clockFor(WallpaperPolicy.AUTO, head));
        assertEquals("the owner's choice wins", WallpaperPolicy.SIMPLE, WallpaperPolicy.clockFor(WallpaperPolicy.SIMPLE, head));
        assertEquals(WallpaperPolicy.NONE, WallpaperPolicy.clockFor(WallpaperPolicy.NONE, head));
    }

    // My Photos (docs/design/MY_PHOTOS.md): adults only, and a fallback order that never leaves the screen blank.
    static final String P = WallpaperPolicy.PHOTOS, A = WallpaperPolicy.ARTWORK;

    @Test public void childGuestAndUnknownProfilesAlwaysGetArtwork() {
        assertEquals(P, WallpaperPolicy.sourceFor(P, "adult"));
        for (String profile : new String[]{"child", "guest", null, "", "Adult"}) {
            assertEquals(String.valueOf(profile), A, WallpaperPolicy.sourceFor(P, profile));
            assertFalse(WallpaperPolicy.photosAllowed(profile));
            // Even with the NAS up and copies kept, a non-adult profile sees artwork (WP-11).
            assertEquals(A, WallpaperPolicy.from(P, profile, true, true, 40, true, 12));
        }
        assertEquals("Off stays off for everyone", WallpaperPolicy.OFF, WallpaperPolicy.sourceFor("off", "child"));
    }

    @Test public void photosThenKeptCopiesIfAllowedThenArtwork() {
        assertEquals(WallpaperPolicy.LIVE, WallpaperPolicy.from(P, "adult", true, true, 40, false, 0));
        assertEquals("NAS off, copies allowed", WallpaperPolicy.CACHE, WallpaperPolicy.from(P, "adult", true, false, 40, true, 12));
        assertEquals("NAS off, copies not allowed", A, WallpaperPolicy.from(P, "adult", true, false, 40, false, 12));
        assertEquals("NAS off, nothing kept yet", A, WallpaperPolicy.from(P, "adult", true, false, 0, true, 0));
        assertEquals("folders empty of pictures", WallpaperPolicy.CACHE, WallpaperPolicy.from(P, "adult", true, true, 0, true, 3));
        assertEquals("no folders chosen yet", A, WallpaperPolicy.from(P, "adult", false, true, 0, false, 0));
        assertEquals("Artwork source never reads photos", A, WallpaperPolicy.from(A, "adult", true, true, 40, true, 12));
    }

    @Test public void myPhotosLabelLimitLineAndPortraitFit() {
        assertEquals("My Photos", WallpaperPolicy.sourceLabel(P));
        assertEquals("These pictures are visible to any device on your home network that can see your media server.", WallpaperPolicy.PHOTOS_LIMIT);
        assertTrue(WallpaperPolicy.letterbox(1080, 1440)); assertTrue(WallpaperPolicy.letterbox(1000, 1000));
        assertFalse(WallpaperPolicy.letterbox(1920, 1080)); assertFalse(WallpaperPolicy.letterbox(1440, 1080)); assertFalse(WallpaperPolicy.letterbox(0, 0));
    }

    @Test public void photosGetTheCalmestSpotButNeverTheDepthClock() {
        // Owner photos have no prepared mask: the automatic setting gives the simple clock where the picture is calmest.
        assertEquals(WallpaperPolicy.SIMPLE, WallpaperPolicy.clockFor(WallpaperPolicy.AUTO, null));
        assertEquals(WallpaperPolicy.NONE, WallpaperPolicy.clockFor(WallpaperPolicy.NONE, null));
    }
}
