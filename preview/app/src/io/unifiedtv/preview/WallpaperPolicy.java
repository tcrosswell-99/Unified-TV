package io.unifiedtv.preview;

import java.util.*;

/**
 * Spec 33 and owner decisions OD-03/OD-04 (25 October 2026), as pure rules: the default source is the bundled curated
 * artwork; the clock is placed from the picture itself; the depth clock is used only when a prepared mask really
 * overlaps the clock; otherwise the simple clock goes where the picture is calmest. Artwork is never downloaded; My Photos
 * (docs/design/MY_PHOTOS.md, Phase 1) reads the owner's chosen NAS folders on the home network, adults only.
 */
final class WallpaperPolicy {
    private WallpaperPolicy() {}
    static final String ARTWORK = "artwork", OFF = "off", PHOTOS = "photos";
    /** Where the next picture comes from in My Photos: the NAS now, the kept copies, or the curated artwork. */
    static final String LIVE = "live", CACHE = "cache";
    /** The profile that may see private pictures. Child, Guest (when it exists) and anything unknown get Artwork. */
    static final String ADULT = "adult";
    /** Shown on screen with the My Photos setting: DLNA has no accounts (MY_PHOTOS.md, route A). */
    static final String PHOTOS_LIMIT = "These pictures are visible to any device on your home network that can see your media server.";
    static final String AUTO = "auto", SIMPLE = "simple", NONE = "none";
    static final int[] IDLE_MINUTES = {0, 2, 5, 10, 15};
    static final int[] SLIDE_SECONDS = {30, 60, 300, 600};
    static final int DEFAULT_IDLE = 5, DEFAULT_SLIDE = 300;
    /** The grid the picture and mask are measured on: 16 × 9 cells over the screen. */
    static final int GW = 16, GH = 9;

    static String source(String saved) { return OFF.equals(saved) || PHOTOS.equals(saved) ? saved : ARTWORK; }
    static boolean photosAllowed(String profile) { return ADULT.equals(profile); }
    /** The source this profile actually gets: the TV's choice, except that private pictures are for the adult profile only (WP-11). */
    static String sourceFor(String saved, String profile) { String s = source(saved); return PHOTOS.equals(s) && !photosAllowed(profile) ? ARTWORK : s; }
    /**
     * My Photos' fallback order (spec 33.2, WP-10): the chosen folders while the NAS answers and has pictures; then the
     * kept copies, only if the owner allowed keeping them; then the curated artwork, so the screen is never blank. A
     * profile that may not see private pictures gets artwork whatever is cached (WP-11).
     */
    static String from(String saved, String profile, boolean folders, boolean reachable, int live, boolean keep, int cached) {
        if (!PHOTOS.equals(sourceFor(saved, profile))) return ARTWORK;
        if (folders && reachable && live > 0) return LIVE;
        if (keep && cached > 0) return CACHE;
        return ARTWORK;
    }
    /** Portrait and square-ish photos are shown whole over a soft blurred fill instead of cropped to 16:9 (spec 33.4). */
    static boolean letterbox(int w, int h) { return w > 0 && h > 0 && w / (float) h < 1.2f; }
    static String clock(String saved) { return SIMPLE.equals(saved) || NONE.equals(saved) ? saved : AUTO; }
    static int idle(int saved) { for (int m : IDLE_MINUTES) if (m == saved) return m; return DEFAULT_IDLE; }
    static int slide(int saved) { for (int s : SLIDE_SECONDS) if (s == saved) return s; return DEFAULT_SLIDE; }

    static String sourceLabel(String s) { return OFF.equals(s) ? "Off" : PHOTOS.equals(s) ? "My Photos" : "Artwork"; }
    static String clockLabel(String c) { return SIMPLE.equals(c) ? "Simple clock" : NONE.equals(c) ? "No clock" : "Depth clock when the picture suits it"; }
    static String idleLabel(int m) { return m == 0 ? "Never" : "After " + m + " minutes"; }
    static String slideLabel(int s) { return s < 60 ? s + " seconds" : s == 60 ? "1 minute" : (s / 60) + " minutes"; }

    /** Cell rectangles {left, top, right, bottom} where a simple clock may sit, clear of the TV's overscan edge. */
    static final int[][] SIMPLE_SPOTS = {{1, 1, 6, 3}, {10, 1, 15, 3}, {1, 6, 6, 8}, {10, 6, 15, 8}, {5, 1, 11, 3}};
    /** Where the large depth clock sits: the upper middle, behind a subject's head and shoulders. */
    static final int[] DEPTH_SPOT = {3, 1, 13, 5};

    /**
     * The calmest places for the simple clock, best first: lowest brightness variation inside the spot, so the time sits
     * on sky, sea or wall rather than detail. luminance is GW × GH values from 0 to 1.
     */
    static int[] calmest(float[] luminance) {
        Integer[] order = new Integer[SIMPLE_SPOTS.length];
        double[] score = new double[SIMPLE_SPOTS.length];
        for (int i = 0; i < order.length; i++) { order[i] = i; score[i] = variance(luminance, SIMPLE_SPOTS[i]); }
        Arrays.sort(order, (a, b) -> Double.compare(score[a], score[b]));
        int[] out = new int[order.length]; for (int i = 0; i < out.length; i++) out[i] = order[i]; return out;
    }
    /**
     * The spot for this slide: alternate between the two calmest, so a clock never sits in exactly the same place for
     * hours (OLED; spec 33.6).
     */
    static int[] spot(float[] luminance, int slide) { int[] best = calmest(luminance); return SIMPLE_SPOTS[best[Math.min(best.length - 1, slide % 2)]]; }

    static double mean(float[] grid, int[] r) {
        double sum = 0; int n = 0;
        for (int y = r[1]; y < r[3]; y++) for (int x = r[0]; x < r[2]; x++) { sum += grid[y * GW + x]; n++; }
        return n == 0 ? 0 : sum / n;
    }
    static double variance(float[] grid, int[] r) {
        double m = mean(grid, r), sum = 0; int n = 0;
        for (int y = r[1]; y < r[3]; y++) for (int x = r[0]; x < r[2]; x++) { double d = grid[y * GW + x] - m; sum += d * d; n++; }
        return n == 0 ? 0 : sum / n;
    }
    /** A bright spot needs a soft shade behind white numerals; dark ones don't. */
    static boolean needsShade(float[] luminance, int[] spot) { return mean(luminance, spot) > .55; }

    /**
     * Whether the depth clock suits this picture: a prepared mask (GW × GH coverage, 0 to 1) must cover some of the clock,
     * so the subject really stands in front of it, but not so much that the time can't be read.
     */
    static boolean depthSuits(float[] mask) {
        if (mask == null) return false;
        double covered = mean(mask, DEPTH_SPOT);
        return covered >= .08 && covered <= .55;
    }
    /** What to draw for this picture under the chosen clock setting. My Photos have no prepared mask, so never the depth clock. */
    static String clockFor(String setting, float[] mask) {
        if (NONE.equals(setting)) return NONE;
        if (AUTO.equals(setting) && depthSuits(mask)) return "depth";
        return SIMPLE;
    }
}
