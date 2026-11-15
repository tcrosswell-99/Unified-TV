package io.unifiedtv.preview;

import org.junit.Test;
import static org.junit.Assert.*;

/** Owner, 25 October 2026: white brand marks identify YouTube, Netflix, Disney+ and BBC iPlayer; Live TV is a TV set. */
public class DockMarksTest {
    @Test public void eachTileDrawsItsMark() {
        assertEquals("play", DockLabels.glyph("com.google.android.youtube.tv"));
        assertEquals("SmartTube's tile, named YouTube, shows YouTube's mark", "play", DockLabels.glyph("smartutube"));
        assertEquals("YouTube", DockLabels.name("smartutube"));
        assertEquals("n", DockLabels.glyph("netflix")); assertEquals("arcplus", DockLabels.glyph("disney")); assertEquals("blocks", DockLabels.glyph("bbc"));
        assertEquals("a television, not a mast", "tvset", DockLabels.glyph("live")); assertEquals("note", DockLabels.glyph("music"));
        assertNotEquals("Live TV and other TV apps look different", DockLabels.glyph("live"), DockLabels.glyph("tvplus"));
        assertFalse(DockLabels.GLYPHS.contains("broadcast"));
        for (AppCatalogue.Category c : AppCatalogue.CATEGORIES) assertTrue(c.glyph, DockLabels.GLYPHS.contains(c.glyph));
    }
}
