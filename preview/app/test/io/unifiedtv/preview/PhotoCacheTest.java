package io.unifiedtv.preview;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** "Keep a copy for when the NAS is off": bounded, oldest out first, whole files only (My Photos; WP-10). Synthetic bytes. */
public class PhotoCacheTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    static PhotoCache.Writer bytes(int n) { return out -> out.write(new byte[n]); }

    @Test public void copiesAreKeptUnderHashedNames() throws Exception {
        PhotoCache c = new PhotoCache(new File(tmp.getRoot(), "photos"), 1000);
        File f = c.put(PhotoSource.key("uuid:example", "p1"), bytes(100));
        assertTrue(c.has(PhotoSource.key("uuid:example", "p1"))); assertEquals(100, f.length());
        assertFalse(f.getName().contains("p1")); assertEquals(1, c.all().size()); assertEquals(100, c.size());
    }

    @Test public void theOldestCopiesGoFirstWhenOverTheLimit() throws Exception {
        PhotoCache c = new PhotoCache(tmp.newFolder("photos"), 250);
        File a = c.put("a", bytes(100)), b = c.put("b", bytes(100));
        a.setLastModified(1_000_000L); b.setLastModified(2_000_000L);
        File d = c.put("d", bytes(100));                    // 300 bytes: one must go, and it is the oldest
        assertFalse(a.exists()); assertTrue(b.exists()); assertTrue(d.exists()); assertTrue(c.size() <= 250);
        b.setLastModified(1_000_000L); d.setLastModified(2_000_000L);
        c.touch(b);                                          // just shown: now the newest
        c.put("e", bytes(100));
        assertTrue(b.exists()); assertFalse(d.exists());
    }

    @Test public void theDefaultLimitIsAbout200MB() { assertEquals(200L * 1024 * 1024, PhotoCache.LIMIT); }

    @Test public void aFailedWriteLeavesNothingBehind() throws Exception {
        PhotoCache c = new PhotoCache(tmp.newFolder("photos"), 1000);
        try { c.put("x", out -> { out.write(new byte[10]); throw new IOException("disk full"); }); fail(); } catch (IOException expected) { }
        assertFalse(c.has("x")); assertEquals(0, Objects.requireNonNull(c.dir.listFiles()).length);
    }

    @Test public void clearingEmptiesItAndAMissingFolderIsJustEmpty() throws Exception {
        PhotoCache c = new PhotoCache(tmp.newFolder("photos"), 1000);
        c.put("a", bytes(10)); c.put("b", bytes(10)); c.clear();
        assertTrue(c.all().isEmpty());
        PhotoCache none = new PhotoCache(new File(tmp.getRoot(), "never-made"), 1000);
        assertTrue(none.all().isEmpty()); assertEquals(0, none.size()); none.trim(); none.clear();
    }
}
