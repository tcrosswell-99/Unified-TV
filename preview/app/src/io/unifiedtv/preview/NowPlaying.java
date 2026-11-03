package io.unifiedtv.preview;

import java.util.Locale;

/**
 * What Android's own media controls report about one playing session, as plain values (spec 11, Music). Amazon has no
 * public playback API for TV apps, so this is only ever what the Amazon Music app itself tells Android: nothing is
 * looked up, fetched or guessed. Pure Java, so it can be tested without a TV; MediaWatch fills it from a session.
 */
final class NowPlaying {
    /** The Amazon Music apps we prefer when several apps hold a media session. */
    static final String[] AMAZON = {"com.amazon.mp3", "com.amazon.music.tv"};
    static final int TITLE_MAX = 120, LINE_MAX = 100;
    // android.media.session.PlaybackState values, copied so this class stays free of the framework.
    static final int STATE_NONE = 0, STATE_STOPPED = 1, STATE_PAUSED = 2, STATE_PLAYING = 3, STATE_FAST_FORWARDING = 4,
            STATE_REWINDING = 5, STATE_BUFFERING = 6, STATE_ERROR = 7, STATE_CONNECTING = 8, STATE_SKIPPING_TO_PREVIOUS = 9,
            STATE_SKIPPING_TO_NEXT = 10, STATE_SKIPPING_TO_QUEUE_ITEM = 11;
    static final long ACTION_PAUSE = 2, ACTION_PLAY = 4, ACTION_SKIP_TO_PREVIOUS = 16, ACTION_SKIP_TO_NEXT = 32, ACTION_SEEK_TO = 256,
            ACTION_PLAY_PAUSE = 512, ACTION_PLAY_FROM_MEDIA_ID = 1024, ACTION_PLAY_FROM_SEARCH = 2048;
    /**
     * Shuffle and repeat as the AndroidX media library (MediaSessionCompat, also inside Media3) declares them in the same
     * actions mask, and the standard commands it accepts for them through the framework's custom-action channel. Sent only
     * when the app declares the action; the app's current shuffle or repeat setting is not readable from here.
     */
    static final long ACTION_SET_REPEAT_MODE = 1L << 18, ACTION_SET_SHUFFLE_MODE = 1L << 21;
    static final String COMPAT_SET_SHUFFLE = "android.support.v4.media.session.action.SET_SHUFFLE_MODE",
            COMPAT_SHUFFLE_ARG = "android.support.v4.media.session.action.ARGUMENT_SHUFFLE_MODE",
            COMPAT_SET_REPEAT = "android.support.v4.media.session.action.SET_REPEAT_MODE",
            COMPAT_REPEAT_ARG = "android.support.v4.media.session.action.ARGUMENT_REPEAT_MODE";
    /** Repeat modes in the order a press steps through them (the library's values: none 0, one 1, all 2). */
    static final int REPEAT_NONE = 0, REPEAT_ONE = 1, REPEAT_ALL = 2;
    /** Hold Left or Right on Now Playing: this far back or forward, at most this often while held. */
    static final long SEEK_STEP_MS = 10_000, SEEK_EVERY_MS = 400;

    final String packageName, title, artist, album;
    final long durationMs, positionMs, updatedAt, actions;
    final float speed;
    final int state;

    NowPlaying(String packageName, String title, String artist, String album, long durationMs, int state, long positionMs, long updatedAt, float speed, long actions) {
        this.packageName = packageName == null ? "" : packageName;
        this.title = bounded(title, TITLE_MAX);
        this.artist = bounded(artist, LINE_MAX);
        this.album = bounded(album, LINE_MAX);
        this.durationMs = Math.max(0, durationMs);
        this.state = state;
        this.positionMs = positionMs;
        this.updatedAt = updatedAt;
        this.speed = Float.isNaN(speed) || Float.isInfinite(speed) ? 1f : speed;
        this.actions = actions;
    }

    /** Playing, or on its way to playing: the clock runs and the button offers Pause. */
    static boolean playing(int state) {
        return state == STATE_PLAYING || state == STATE_BUFFERING || state == STATE_CONNECTING || state == STATE_FAST_FORWARDING
                || state == STATE_REWINDING || state == STATE_SKIPPING_TO_NEXT || state == STATE_SKIPPING_TO_PREVIOUS
                || state == STATE_SKIPPING_TO_QUEUE_ITEM;
    }
    boolean playing() { return playing(state); }
    /** A session that has finished, failed or never started has nothing worth showing. */
    static boolean alive(int state) { return state != STATE_NONE && state != STATE_STOPPED && state != STATE_ERROR; }

    static boolean amazon(String pkg) { for (String a : AMAZON) if (a.equals(pkg)) return true; return false; }
    private static java.util.Set<String> known() { return new java.util.HashSet<>(java.util.Arrays.asList(AMAZON)); }

    /**
     * Which session to show: an Amazon Music session that is playing, else any app's playing session, else a paused
     * Amazon Music session. Never this app's own session. -1 when none qualifies.
     */
    static int pick(String[] packages, int[] states, String self) { return pick(packages, states, self, known()); }
    /** As above, with the Amazon Music packages found on this TV (AmazonMusic.packages: known ids and the app's own name). */
    static int pick(String[] packages, int[] states, String self, java.util.Set<String> amazonPackages) {
        int amazonPaused = -1, otherPlaying = -1;
        for (int i = 0; i < packages.length && i < states.length; i++) {
            String p = packages[i];
            if (p == null || p.equals(self)) continue;
            boolean isAmazon = amazonPackages.contains(p);
            if (playing(states[i])) { if (isAmazon) return i; if (otherPlaying < 0) otherPlaying = i; }
            else if (isAmazon && alive(states[i]) && amazonPaused < 0) amazonPaused = i;
        }
        return otherPlaying >= 0 ? otherPlaying : amazonPaused;
    }

    /** Where the track is now: the reported position, moved on by the time since it was reported, at the reported speed. */
    long position(long elapsedRealtimeNow) {
        if (positionMs < 0) return -1;
        long at = positionMs;
        if (playing() && state != STATE_BUFFERING && state != STATE_CONNECTING && updatedAt > 0 && elapsedRealtimeNow > updatedAt)
            at += (long) ((elapsedRealtimeNow - updatedAt) * (double) speed);
        if (at < 0) at = 0;
        if (durationMs > 0 && at > durationMs) at = durationMs;
        return at;
    }

    /** 0..1 along the progress line; 0 when the length or the position is unknown. */
    static float fraction(long position, long duration) {
        if (duration <= 0 || position <= 0) return 0f;
        return Math.min(1f, position / (float) duration);
    }

    /** m:ss, or h:mm:ss for an hour or more; empty when unknown, so nothing invented is shown. */
    static String clock(long ms) {
        if (ms < 0) return "";
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** True when the app declares the action, or declares none at all (many apps leave the mask empty). */
    boolean offers(long action) { return actions == 0 || (actions & action) != 0; }

    /** The next repeat mode a press asks for: none, then all, then one, then none again. */
    static int nextRepeat(int mode) { return mode == REPEAT_NONE ? REPEAT_ALL : mode == REPEAT_ALL ? REPEAT_ONE : REPEAT_NONE; }
    static String repeatWords(int mode) { return mode == REPEAT_ALL ? "Repeat all" : mode == REPEAT_ONE ? "Repeat one" : "Repeat off"; }

    /** Where a held Left (-1) or Right (+1) moves to: ten seconds along, kept inside the track (or at 0 when unknown). */
    static long seekTarget(long position, long duration, int direction) {
        long at = Math.max(0, position) + direction * SEEK_STEP_MS;
        if (at < 0) at = 0;
        if (duration > 0 && at > duration) at = duration;
        return at;
    }

    /**
     * Custom actions an app offers for liking or rating the track (thumbs, like, love, heart, favourite), by id or label;
     * Unified TV never invents one. Indexes into the app's own list, at most three.
     */
    static java.util.List<Integer> likeActions(String[] ids, String[] names) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length && out.size() < 3; i++) {
            String id = ids[i] == null ? "" : ids[i].toLowerCase(Locale.ROOT);
            String name = i < names.length && names[i] != null ? names[i].toLowerCase(Locale.ROOT) : "";
            for (String w : new String[]{"thumb", "like", "love", "heart", "favourite", "favorite"})
                if (id.contains(w) || name.contains(w)) { out.add(i); break; }
        }
        return out;
    }

    /** The index of a custom action that toggles shuffle, by its id or label; -1 when the app offers none. */
    static int shuffleAction(String[] ids, String[] names) {
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i] == null ? "" : ids[i].toLowerCase(Locale.ROOT);
            String name = i < names.length && names[i] != null ? names[i].toLowerCase(Locale.ROOT) : "";
            if (id.contains("shuffle") || name.contains("shuffle")) return i;
        }
        return -1;
    }

    /** One line of plain text: control characters dropped, whitespace collapsed, cut at max with an ellipsis. */
    static String bounded(CharSequence s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(Math.min(s.length(), max + 1));
        boolean space = false;
        for (int i = 0; i < s.length() && b.length() <= max; i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) { space = b.length() > 0; continue; }
            if (space) { b.append(' '); space = false; }
            b.append(ch);
        }
        String out = b.toString();
        if (out.length() > max) {
            int cut = max - 1;
            if (cut > 0 && Character.isHighSurrogate(out.charAt(cut - 1))) cut--;
            out = out.substring(0, cut).trim() + "…";
        }
        return out;
    }

    /** Same track (so the art and the progress line belong together); a new track clears the old art at once. */
    String trackKey() { return packageName + "\u0000" + title + "\u0000" + artist + "\u0000" + album; }

    /** The one ADB line the owner can run from a computer when the TV has no notification-access screen. */
    static String adbLine(String flattenedComponent) { return "adb shell cmd notification allow_listener " + flattenedComponent; }
}
