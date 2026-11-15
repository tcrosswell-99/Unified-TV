package io.unifiedtv.preview;

import org.junit.Test;
import static org.junit.Assert.*;

/** Owner, 25 October 2026: every build said 0.5-preview. Updates & About names the commit, the CI run and the date. */
public class BuildIdentityTest {
    @Test public void buildsCanBeToldApart() {
        assertEquals("Build 1a2b3c4 · run 57 · 2026-10-25", About.build("1a2b3c4", "57", "2026-10-25"));
        assertEquals("Build local · run local", About.build("local", "local", ""));
        assertEquals("nothing odd is shown", "Build local · run local · 2026-10-25", About.build("not-a-sha!", "x1", "2026-10-25"));
        assertTrue(About.build().startsWith("Build "));
        assertTrue(About.licences().contains("names and marks belong to their owners"));
    }
}
