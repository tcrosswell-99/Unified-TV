package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

/** Spec 33, OD-03/OD-04: artwork by default, a calm place for the clock, and the depth clock only where a mask suits it. */
public class WallpaperPolicyTest {
    private static float[] flat(float v) { float[] g = new float[WallpaperPolicy.GW * WallpaperPolicy.GH]; java.util.Arrays.fill(g, v); return g; }

    @Test public void defaultsAreArtworkAutomaticDepthFiveMinutesAndUnknownValuesFallBack() {
        assertEquals(WallpaperPolicy.ARTWORK, WallpaperPolicy.source(null));
        assertEquals(WallpaperPolicy.OFF, WallpaperPolicy.source("off"));
        assertEquals(WallpaperPolicy.ARTWORK, WallpaperPolicy.source("photos"));
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
}
