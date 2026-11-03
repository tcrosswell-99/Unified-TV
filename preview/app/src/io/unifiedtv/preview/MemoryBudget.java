package io.unifiedtv.preview;

import android.app.ActivityManager;
import android.content.Context;

/**
 * Spec 14 "Memory: budget from measured TCL RAM; bounded caches" and AGENTS rule 5: how much Unified TV may hold on this
 * TV. The owner's TCL (BeyondTV4, Android 11, 2 GB, about 500 MB free with zram busy, 25 October 2026) stopped answering
 * the remote under memory pressure, so a TV like it is treated as {@link Tier#LOW}: the same screens, the same light, the
 * same navigation and timing, only cheaper (smaller sprites and caches, 16-bit opaque pictures, a slower drift step and a
 * cap on the saved library list held in memory). Nothing a household can see or do changes with the tier.
 *
 * <p>Low when Android itself says the device is low on RAM, when the TV has about 2.5 GB or less in total, or when its
 * per-app memory class is 192 MB or less (where the TCL and older Sony sets sit). Classified once per process.
 */
final class MemoryBudget {
    enum Tier { LOW, STANDARD }
    /** About 2.5 GB: a 2 GB TV reports a little under 2 GB here; a 3 GB TV a little under 3 GB. */
    static final long LOW_TOTAL_BYTES = 2_560L * 1024 * 1024;
    static final int LOW_MEMORY_CLASS_MB = 192;

    final Tier tier;
    private MemoryBudget(Tier tier) { this.tier = tier; }

    /** Pure: the tier for what Android reports (0 or less for "not known"). */
    static Tier classify(boolean lowRamDevice, long totalBytes, int memoryClassMb) {
        if (lowRamDevice) return Tier.LOW;
        if (totalBytes > 0 && totalBytes <= LOW_TOTAL_BYTES) return Tier.LOW;
        if (memoryClassMb > 0 && memoryClassMb <= LOW_MEMORY_CLASS_MB) return Tier.LOW;
        return Tier.STANDARD;
    }

    private static volatile MemoryBudget current, forced;
    /** This TV's budget, classified on first use. */
    static MemoryBudget get(Context c) {
        MemoryBudget f = forced; if (f != null) return f;
        MemoryBudget b = current; if (b != null) return b;
        boolean low = false; long total = 0; int cls = 0;
        try {
            ActivityManager am = (ActivityManager) c.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) { low = am.isLowRamDevice(); cls = am.getMemoryClass(); ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); total = mi.totalMem; }
        } catch (RuntimeException ignored) { }
        b = new MemoryBudget(classify(low, total, cls));
        current = b; return b;
    }
    /** The budget where no Context is at hand (static caches); STANDARD until the app has classified the TV. */
    static MemoryBudget current() { MemoryBudget f = forced; if (f != null) return f; MemoryBudget b = current; return b != null ? b : new MemoryBudget(Tier.STANDARD); }
    /** Tests only: pretend to be this tier (null: classify the device again). */
    static void force(Tier tier) { forced = tier == null ? null : new MemoryBudget(tier); if (tier == null) current = null; }
    static MemoryBudget of(Tier tier) { return new MemoryBudget(tier); }

    boolean low() { return tier == Tier.LOW; }

    // --- what each tier may hold (the household sees the same thing either way)
    /** Decoded posters and backgrounds in memory (Posters): an eighth of the heap up to 24 MB, or a sixteenth up to 8 MB. */
    int imageCacheBytes(long maxHeap) {
        long share = low() ? Math.min(8L << 20, maxHeap / 16) : Math.min(24L << 20, maxHeap / 8);
        return (int) Math.max(low() ? 3L << 20 : 4L << 20, share);
    }
    /** Opaque pictures (JPEG posters and backdrops) at 16 bits a pixel: half the memory, no transparency to lose. */
    boolean opaque565() { return low(); }
    /** The soft light sprite's side in pixels: it is magnified about six times either way, so the smaller one looks the same. */
    int spriteSize() { return low() ? 256 : 512; }
    /** App light groups kept ready behind Home (each is a handful of views sharing one sprite). */
    int ambientGroups() { return low() ? 2 : 4; }
    /**
     * Drift step: the light is moved by the compositor this often. Drift travels at most about 90 px a second on a 1080p
     * screen, so 15 steps a second still moves it in small steps on very soft edges; 25 on a TV with room to spare.
     */
    long driftStepMs() { return low() ? 66 : 40; }
    /** Small frosted frames kept for the glass (MaterialScene, about 57 KB each). */
    int frostKept() { return low() ? 6 : 12; }
    /** Full-screen scenes kept (Backdrops), and their size: they are soft, upscaled pictures either way. */
    int backdropsKept() { return low() ? 3 : 6; }
    int backdropWidth() { return low() ? 320 : 640; }
    /** Titles the saved library list keeps (LibraryIndex): the walk stops there and the list says it is incomplete. */
    int libraryTitles() { return low() ? 4000 : LibraryIndex.MAX_TITLES; }
    /** The background library job's rest between two pages from the media server (LibraryCollector): gentler on a 2 GB TV. */
    long indexGapMs() { return low() ? 60 : 20; }
    /** Poster lookups the background job queues at once (LibraryCollector); the tile in focus still goes first. */
    int posterBatch() { return low() ? 4 : 8; }

    @Override public String toString() { return low() ? "low (2 GB class: smaller caches, same screens)" : "standard"; }
}
