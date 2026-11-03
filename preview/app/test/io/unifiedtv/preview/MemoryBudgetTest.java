package io.unifiedtv.preview;

import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.Collections;

/**
 * Spec 14 memory budget (owner's TCL: 2 GB, 25 October 2026): the tier is chosen from what Android reports, and every cache
 * respects it. Only cost changes with the tier; what is shown does not (UX parity: AGENTS rule 5).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class MemoryBudgetTest {
    private static final long GB = 1024L * 1024 * 1024;
    @After public void reset(){MemoryBudget.force(null);Posters.reset();}

    @Test public void theTierFollowsWhatAndroidReports(){
        assertEquals("Android says low RAM", MemoryBudget.Tier.LOW, MemoryBudget.classify(true, 8 * GB, 512));
        assertEquals("the owner's TCL: 2 GB in total", MemoryBudget.Tier.LOW, MemoryBudget.classify(false, 2 * GB - 200L * 1024 * 1024, 256));
        assertEquals("about 2.5 GB is still low", MemoryBudget.Tier.LOW, MemoryBudget.classify(false, MemoryBudget.LOW_TOTAL_BYTES, 256));
        assertEquals("a 192 MB memory class", MemoryBudget.Tier.LOW, MemoryBudget.classify(false, 4 * GB, 192));
        assertEquals("3 GB and a 256 MB class", MemoryBudget.Tier.STANDARD, MemoryBudget.classify(false, 3 * GB, 256));
        assertEquals("nothing known: standard", MemoryBudget.Tier.STANDARD, MemoryBudget.classify(false, 0, 0));
    }

    @Test public void lowCostsLessEverywhere(){
        MemoryBudget low = MemoryBudget.of(MemoryBudget.Tier.LOW), std = MemoryBudget.of(MemoryBudget.Tier.STANDARD);
        long heap = 192L << 20;
        assertTrue(low.imageCacheBytes(heap) < std.imageCacheBytes(heap));
        assertTrue("an eighth, at most 24 MB", std.imageCacheBytes(heap) == 24 << 20 && std.imageCacheBytes(512L << 20) == 24 << 20);
        assertTrue("at most 8 MB on a 2 GB TV", low.imageCacheBytes(512L << 20) <= 8 << 20 && low.imageCacheBytes(heap) >= 3 << 20);
        assertTrue(low.opaque565()); assertFalse(std.opaque565());
        assertTrue(low.spriteSize() < std.spriteSize());
        assertTrue(low.ambientGroups() < std.ambientGroups() && low.ambientGroups() >= 2);
        assertTrue("fewer drift steps, still at least 15 a second", low.driftStepMs() > std.driftStepMs() && low.driftStepMs() <= 67);
        assertTrue(low.frostKept() < std.frostKept() && low.backdropsKept() < std.backdropsKept() && low.backdropWidth() < std.backdropWidth());
        assertTrue(low.libraryTitles() < std.libraryTitles()); assertEquals(LibraryIndex.MAX_TITLES, std.libraryTitles());
    }

    @Test public void thePictureCacheRespectsTheTier(){
        MemoryBudget.force(MemoryBudget.Tier.LOW); Posters.reset();
        int low = Posters.get(RuntimeEnvironment.getApplication()).cacheLimit();
        assertTrue("low: 8 MB or less", low <= 8 << 20);
        MemoryBudget.force(MemoryBudget.Tier.STANDARD); Posters.reset();
        int std = Posters.get(RuntimeEnvironment.getApplication()).cacheLimit();
        assertTrue("standard holds more", std > low);
        Posters.trim(true); assertEquals("hidden: every decoded picture goes", 0, Posters.get(RuntimeEnvironment.getApplication()).cacheUsed());
    }

    @Test public void scenesAreSmallerAndFewerOnALowTv(){
        MemoryBudget.force(MemoryBudget.Tier.LOW);
        Backdrops low = new Backdrops();
        assertEquals(320, low.app("netflix").getWidth()); assertEquals(180, low.app("netflix").getHeight());
        for (String id : new String[]{"smartutube", "disney", "bbc", "live", "music"}) low.app(id);
        assertTrue("at most three kept", low.size() <= 3);
        low.trim("music"); assertEquals("trim keeps only the scene on screen", 1, low.size());
        low.trim(null); assertEquals(0, low.size());
        MemoryBudget.force(MemoryBudget.Tier.STANDARD);
        Backdrops std = new Backdrops();
        assertEquals(640, std.app("netflix").getWidth());
        for (String id : new String[]{"smartutube", "disney", "bbc", "live", "music", "xbox", "tvplus"}) std.app(id);
        assertEquals(6, std.size());
    }

    @Test public void theSavedLibraryListIsCappedOnALowTv() throws Exception {
        FakeServer s = new FakeServer().folder("0", "v", "Video");
        for (int i = 0; i < 4100; i++) s.video("v", "f" + i, "Film " + i + ".mkv");
        LibraryIndex.browser = s;
        try {
            MemoryBudget.force(MemoryBudget.Tier.LOW);
            LibraryIndex.Root low = LibraryIndex.walk(FakeServer.source(), "v", Collections.emptySet(), null, null, 1000);
            assertEquals(4000, low.entries.size()); assertFalse("the list says it is incomplete", low.complete);
            MemoryBudget.force(MemoryBudget.Tier.STANDARD);
            LibraryIndex.Root std = LibraryIndex.walk(FakeServer.source(), "v", Collections.emptySet(), null, null, 1000);
            assertEquals(4100, std.entries.size()); assertTrue(std.complete);
        } finally { LibraryIndex.browser = DlnaClient::browse; }
    }

    @Test public void repeatedFolderNamesAreKeptOnce() throws Exception {
        LibraryIndex.Index index = new LibraryIndex.Index(); LibraryIndex.Root r = new LibraryIndex.Root("v"); index.roots.put("v", r);
        for (int i = 0; i < 3; i++) { LibraryIndex.Entry e = new LibraryIndex.Entry(); e.id = "e" + i; e.title = "Episode " + i; e.url = "http|80|/v/" + i;
            e.pathNames.add(new String("Doctor Who")); e.pathIds.add(new String("show-1")); r.entries.add(e); }
        LibraryIndex.Index back = LibraryIndex.parse(LibraryIndex.json(index));
        java.util.List<LibraryIndex.Entry> all = back.roots.get("v").entries;
        assertEquals(3, all.size());
        assertSame("one copy of the folder name for the whole list", all.get(0).pathNames.get(0), all.get(2).pathNames.get(0));
        assertEquals("Doctor Who", all.get(1).pathNames.get(0));
    }

    /** Pictures are decoded at the size they are shown: never larger, never below it (Images.plan). */
    @Test public void picturesAreDecodedAtTheirShownSize(){
        int[] tmdb = Images.plan(342, 513, 226, 338);
        assertEquals("TMDB's w342 poster: no sampling, scaled to the tile", 1, tmdb[0]);
        assertTrue(tmdb[1] <= 226 && tmdb[2] <= 338 && (tmdb[1] >= 225 || tmdb[2] >= 337));
        int[] big = Images.plan(2000, 3000, 226, 338);
        assertEquals("a large picture is sampled down first", 8, big[0]);
        assertTrue("but never below the tile", big[3] >= big[1]);
        int[] small = Images.plan(100, 150, 226, 338);
        assertArrayEquals("never enlarged", new int[]{1, 100, 150, 100}, small);
        int[] shown = Posters.shownPoster(1920, 1080);
        assertArrayEquals("the library tile lifted on focus, at 1080p", new int[]{Posters.SHOWN_W, Posters.SHOWN_H}, shown);
        assertArrayEquals("4K UI: at most 300 x 450", new int[]{Posters.POSTER_W, Posters.POSTER_H}, Posters.shownPoster(3840, 2160));
    }
}
