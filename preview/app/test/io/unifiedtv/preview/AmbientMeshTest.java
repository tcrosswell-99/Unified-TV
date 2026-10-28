package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

/** Spec 32: the six palettes follow app identity, and drift stays slow, small and continuous. */
public class AmbientMeshTest {
    private static final String[] DOCK = {"smartutube", "netflix", "disney", "bbc", "live", "music"};

    @Test public void everyDockAppHasItsOwnPaletteAndOthersGetTheQuietFallback() {
        java.util.Set<Integer> accents = new java.util.HashSet<>();
        for (String id : DOCK) { assertTrue(id, AmbientMesh.known(id)); assertTrue(AmbientMesh.palette(id).lights.length >= 2); accents.add(AmbientMesh.accent(id)); }
        assertEquals("six distinct palettes", DOCK.length, accents.size());
        assertFalse(AmbientMesh.known("com.example.unknown"));
        assertSame(AmbientMesh.palette("com.example.unknown"), AmbientMesh.palette("another.unknown"));
    }

    @Test public void basesStayDarkSoWhiteTypeRemainsReadable() {
        for (String id : DOCK) {
            int base = AmbientMesh.palette(id).base;
            int r = (base >> 16) & 255, g = (base >> 8) & 255, b = base & 255;
            assertTrue(id + " base too bright", r < 48 && g < 48 && b < 64);
        }
    }

    @Test public void driftPathsAreSlowAndSmall() {
        for (String id : DOCK) for (AmbientMesh.Light l : AmbientMesh.palette(id).lights) {
            assertTrue(id + " period", l.period >= 20 && l.period <= 40);
            assertTrue(id + " authored amplitude (shared with the Frame)", l.ax <= .05f && l.ay <= .05f);
            for (double t = 0; t < 120; t += 0.5) {
                float[] at = AmbientMesh.position(l, t);
                // Owner, TCL, 25 October 2026: travel is TRAVEL times the authored path, at most a fifth of the screen.
                float most = AmbientMesh.TRAVEL * .05f + 1e-6f;
                assertTrue(Math.abs(at[0] - l.x) <= most && Math.abs(at[1] - l.y) <= most);
                assertTrue("scale 1.00 to 1.06", at[2] >= 1f - 1e-6 && at[2] <= 1.06f + 1e-6);
            }
        }
    }

    @Test public void driftIsContinuousSoNothingJumps() {
        AmbientMesh.Light l = AmbientMesh.palette("music").lights[0];
        for (double t = 0; t < 60; t += 1 / 30.0) {
            float[] a = AmbientMesh.position(l, t), b = AmbientMesh.position(l, t + 1 / 30.0);
            assertTrue("under a quarter of a percent of the screen per frame", Math.abs(a[0] - b[0]) < .0025f && Math.abs(a[1] - b[1]) < .0025f);
        }
    }

    /** Owner, TCL, 25 October 2026 ("the blobs don't move at all"): every light visibly travels, a tenth of the screen or more. */
    @Test public void everyLightTravelsFarEnoughToBeSeen() {
        for (String id : DOCK) for (AmbientMesh.Light l : AmbientMesh.palette(id).lights) {
            float minX = 9, maxX = -9, minY = 9, maxY = -9;
            for (double t = 0; t < 2 * l.period; t += .25) { float[] at = AmbientMesh.position(l, t); minX = Math.min(minX, at[0]); maxX = Math.max(maxX, at[0]); minY = Math.min(minY, at[1]); maxY = Math.max(maxY, at[1]); }
            assertTrue(id + " travels at least a tenth of the screen", Math.max(maxX - minX, maxY - minY) >= .1f);
            // ...and never faster than about 90 px a second at 1080p (calm, not busy).
            for (double t = 0; t < l.period; t += .1) { float[] a = AmbientMesh.position(l, t), b = AmbientMesh.position(l, t + 1);
                assertTrue(id + " speed", Math.abs(a[0] - b[0]) * 1920 < 95 && Math.abs(a[1] - b[1]) * 1080 < 95); }
        }
    }

    /** SET-022: subtle dims every light to the same share; neutral uses the quiet palette; the light id carries both. */
    @Test public void intensityAndColourModeNameTheLight() {
        assertEquals("netflix", AmbientMesh.lightId("netflix", false, false));
        assertEquals("netflix~subtle", AmbientMesh.lightId("netflix", false, true));
        assertEquals(AmbientMesh.NEUTRAL_LIGHT, AmbientMesh.lightId("netflix", true, false));
        assertSame(AmbientMesh.palette("netflix"), AmbientMesh.palette("netflix~subtle"));
        assertSame(AmbientMesh.palette("unknown.app"), AmbientMesh.palette(AmbientMesh.NEUTRAL_LIGHT + AmbientMesh.SUBTLE));
        assertEquals(1f, AmbientMesh.strength("netflix"), 0f);
        assertEquals(AmbientMesh.SUBTLE_STRENGTH, AmbientMesh.strength("netflix~subtle"), 0f);
        AmbientMesh.Light l = AmbientMesh.palette("netflix").lights[0];
        assertEquals(l.colour & 0xFFFFFF, AmbientMesh.colour(l, .6f) & 0xFFFFFF);
        assertEquals(Math.round(((l.colour >>> 24) & 255) * .6f), AmbientMesh.colour(l, .6f) >>> 24);
        assertEquals("the dock halo follows the light", AmbientMesh.accent("unknown.app"), AmbientMesh.accent(AmbientMesh.NEUTRAL_LIGHT));
    }
}
