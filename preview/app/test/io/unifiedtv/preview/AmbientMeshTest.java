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
            assertTrue(id + " amplitude", l.ax <= .05f && l.ay <= .05f);
            for (double t = 0; t < 120; t += 0.5) {
                float[] at = AmbientMesh.position(l, t);
                assertTrue(Math.abs(at[0] - l.x) <= .05f + 1e-6 && Math.abs(at[1] - l.y) <= .05f + 1e-6);
                assertTrue("scale 1.00 to 1.06", at[2] >= 1f - 1e-6 && at[2] <= 1.06f + 1e-6);
            }
        }
    }

    @Test public void driftIsContinuousSoNothingJumps() {
        AmbientMesh.Light l = AmbientMesh.palette("music").lights[0];
        for (double t = 0; t < 60; t += 1 / 30.0) {
            float[] a = AmbientMesh.position(l, t), b = AmbientMesh.position(l, t + 1 / 30.0);
            assertTrue("under 0.1% of the screen per frame", Math.abs(a[0] - b[0]) < .001f && Math.abs(a[1] - b[1]) < .001f);
        }
    }
}
