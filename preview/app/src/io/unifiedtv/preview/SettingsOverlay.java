package io.unifiedtv.preview;

/**
 * Module 13 §B: the Settings overlay's state machine, kept free of Android so it can be tested on its own.
 *
 * <pre>Home gear → OPENING → ROOT (50%) → DETAIL (50%) → TASK (50% or 75%) → DETAIL → ROOT → CLOSING → CLOSED</pre>
 *
 * Interrupt-safe (GLASS-11): Back while opening closes; once closing, nothing else is accepted, so rapid OK/Back can't
 * leave a half-visible sheet holding focus. The root remembers its row and a detail remembers the row that opened a task.
 */
final class SettingsOverlay {
    enum Phase { OPENING, ROOT, DETAIL, TASK, CLOSING, CLOSED }
    /** SET-GLASS-02: half the screen normally, about three quarters for a complex task. */
    static final float STANDARD = .5f, EXPANDED = .75f;
    /** Below this a half-width sheet isn't readable (a small display), so it widens instead (SET-GLASS-02). */
    static final int MIN_READABLE_DP = 440;
    /** SET-GLASS-05: 250–350 ms ease-out in, a little quicker out; reduced motion is a short crossfade. */
    static final int OPEN_MS = 300, CLOSE_MS = 260, WIDEN_MS = 280, CROSSFADE_MS = 120, CONTENT_MS = 150, FOCUS_MS = 150;

    private Phase page = Phase.ROOT;        // where the sheet is: ROOT, DETAIL or TASK
    private boolean opening = true, closing, closed;
    private int category = -1, rootRow, detailRow;
    private String task;
    private boolean wide;

    /** The spec's name for where the overlay is now. */
    Phase phase() { return closed ? Phase.CLOSED : closing ? Phase.CLOSING : opening ? Phase.OPENING : page; }
    /** The page shown, even while the sheet is still sliding in. */
    Phase page() { return page; }
    int category() { return category; }
    String task() { return task; }
    boolean wide() { return page == Phase.TASK && wide; }
    /** Keys and clicks count only until closing starts. */
    boolean acceptsInput() { return !closing && !closed; }
    boolean atRoot() { return page == Phase.ROOT; }

    /** The slide-in finished (or was skipped). */
    void settled() { opening = false; }

    /** OK or Right on a root row; also a jump from one category to another. */
    boolean open(int c) {
        if (!acceptsInput() || c < 0) return false;
        page = Phase.DETAIL; category = c; rootRow = c; task = null; wide = false; return true;
    }
    /** A collapsed section or complex task inside the same sheet; remembers the detail row that opened it. */
    boolean expand(String name, boolean wider, int fromRow) {
        if (!acceptsInput() || page != Phase.DETAIL || name == null) return false;
        page = Phase.TASK; task = name; wide = wider; detailRow = Math.max(0, fromRow); return true;
    }
    /** Back (or Left with nothing further left): one level up, and from the root (or mid-slide) the overlay closes. */
    Phase back() {
        if (!acceptsInput()) return phase();
        switch (page) {
            case TASK: page = Phase.DETAIL; task = null; wide = false; break;
            case DETAIL: page = Phase.ROOT; category = -1; break;
            default: closing = true; break;
        }
        return phase();
    }
    /** Starts closing from wherever it is (the close glyph). */
    boolean close() { if (!acceptsInput()) return false; closing = true; return true; }
    /** The slide-out ended. True only the first time, so a timer and an animation can both report it safely. */
    boolean finished() { if (!closing || closed) return false; closed = true; return true; }

    int rootRow() { return rootRow; }
    void rootRow(int row) { if (row >= 0) rootRow = row; }
    int detailRow() { return detailRow; }

    /** Fraction of the screen width the sheet covers, widened on a small display so controls stay readable. */
    float width(float screenWidthDp) { return fraction(wide(), screenWidthDp); }
    static float fraction(boolean wider, float screenWidthDp) {
        float base = wider ? EXPANDED : STANDARD;
        if (screenWidthDp <= 0 || base * screenWidthDp >= MIN_READABLE_DP) return base;
        return Math.max(base, Math.min(.92f, MIN_READABLE_DP / screenWidthDp));
    }
    /** Motion length for an entry or exit; a crossfade when motion is reduced. */
    static int duration(boolean in, boolean reduced) { return reduced ? CROSSFADE_MS : in ? OPEN_MS : CLOSE_MS; }

    /** For a recreated activity (profile switch): restores the page without replaying the slide. */
    void restore(int c, String t, boolean wider, int row) {
        opening = false;
        if (c >= 0) { page = Phase.DETAIL; category = c; rootRow = c; }
        if (c >= 0 && t != null) { page = Phase.TASK; task = t; wide = wider; }
        if (c < 0) rootRow = Math.max(0, row);
    }
}
