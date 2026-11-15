package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

/** Spec 4.8 and OLED care: a gentle wash by time of day that never jumps, and a Sony-only idle dim and pixel orbit. */
public class DayMoodTest {
    private static int alpha(int argb) { return argb >>> 24; }

    @Test public void morningAndDayAddNothingDuskAndNightDeepen() {
        assertEquals(0, alpha(DayMood.wash(8 * 60)));
        assertEquals(0, alpha(DayMood.wash(13 * 60)));
        assertTrue(alpha(DayMood.wash(18 * 60)) > 0);
        assertTrue(alpha(DayMood.wash(23 * 60)) > alpha(DayMood.wash(18 * 60)));
        assertEquals("after midnight is still night", alpha(DayMood.wash(23 * 60)), alpha(DayMood.wash(2 * 60)));
    }

    @Test public void phasesBlendOverTwentyMinutesWithoutAJump() {
        for (int m = 0; m < 1440; m++) {
            int a = alpha(DayMood.wash(m)), b = alpha(DayMood.wash(m + 1));
            assertTrue("minute " + m, Math.abs(a - b) <= 4);
        }
    }

    @Test public void oledDimsOnlyAfterTwoIdleMinutesAndFadesIn() {
        assertEquals(0f, DayMood.oledDim(60_000), 0);
        assertEquals(0f, DayMood.oledDim(120_000), 0);
        assertTrue(DayMood.oledDim(125_000) > 0 && DayMood.oledDim(125_000) < .45f);
        assertEquals(.45f, DayMood.oledDim(600_000), 1e-6);
    }

    @Test public void pixelOrbitStaysWithinTwoPixelsAndMoves() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (long t = 0; t < 12 * 60_000; t += 60_000) { int[] s = DayMood.shift(t); assertTrue(s[0] <= 2 && s[1] <= 2); seen.add(s[0] + "," + s[1]); }
        assertEquals(4, seen.size());
    }
}
