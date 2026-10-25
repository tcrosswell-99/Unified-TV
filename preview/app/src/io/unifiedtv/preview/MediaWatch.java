package io.unifiedtv.preview;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import java.util.Collections;
import java.util.List;

/**
 * Lets Unified TV see which app is playing music and press its own Play, Pause and Skip (spec 11, Music). Android only
 * shares media sessions with an app the owner has allowed to see notifications, so this listener exists for that
 * permission alone. It reads no notification, stores nothing and sends nothing anywhere: as soon as Android connects
 * it, it asks to be disconnected again, so notifications are never delivered to it. The approval itself is what
 * MediaSessionManager checks, and it stays in place until the owner withdraws it.
 */
public final class MediaWatch extends NotificationListenerService {
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        try { requestUnbind(); } catch (RuntimeException ignored) { /* stays connected but still reads nothing */ }
    }

    static ComponentName component(Context c) { return new ComponentName(c, MediaWatch.class); }

    /** Whether the owner has allowed this app to see media notifications (API 27+, so always available from minSdk 28). */
    static boolean granted(Context c) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            return nm != null && nm.isNotificationListenerAccessGranted(component(c));
        } catch (RuntimeException e) { return false; }
    }

    /** The active media sessions, newest first as Android orders them; empty without access. */
    static List<MediaController> sessions(Context c) {
        if (!granted(c)) return Collections.emptyList();
        try {
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            List<MediaController> list = m == null ? null : m.getActiveSessions(component(c));
            return list == null ? Collections.<MediaController>emptyList() : list;
        } catch (RuntimeException e) { return Collections.emptyList(); }   // SecurityException when access was just withdrawn
    }

    /** The session to show: Amazon Music first, else any app that is playing (NowPlaying.pick); null when none. */
    static MediaController best(Context c) { return best(c, sessions(c)); }
    static MediaController best(Context c, List<MediaController> list) {
        String[] packages = new String[list.size()]; int[] states = new int[list.size()];
        for (int i = 0; i < list.size(); i++) { packages[i] = list.get(i).getPackageName(); states[i] = state(list.get(i)); }
        int i = NowPlaying.pick(packages, states, c.getPackageName());
        return i < 0 ? null : list.get(i);
    }

    /**
     * True when the named app (for example AirPin LITE's package from AirPlayReceiver.installed) holds a media session
     * that is playing. False without notification access, so a caller never shows an active state it cannot see.
     */
    static boolean sessionPlayingFor(Context c, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        for (MediaController m : sessions(c)) if (packageName.equals(m.getPackageName()) && NowPlaying.playing(state(m))) return true;
        return false;
    }

    static int state(MediaController m) {
        try { PlaybackState s = m.getPlaybackState(); return s == null ? NowPlaying.STATE_NONE : s.getState(); }
        catch (RuntimeException e) { return NowPlaying.STATE_NONE; }
    }

    /** The session's own report, as plain values. Only the metadata's title, artist, album and length are read. */
    static NowPlaying read(MediaController m) {
        android.media.MediaMetadata md = null; PlaybackState s = null;
        try { md = m.getMetadata(); s = m.getPlaybackState(); } catch (RuntimeException ignored) { }
        String title = md == null ? "" : text(md, android.media.MediaMetadata.METADATA_KEY_TITLE);
        if (title.isEmpty() && md != null) title = text(md, android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist = md == null ? "" : text(md, android.media.MediaMetadata.METADATA_KEY_ARTIST);
        if (artist.isEmpty() && md != null) artist = text(md, android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (artist.isEmpty() && md != null) artist = text(md, android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        String album = md == null ? "" : text(md, android.media.MediaMetadata.METADATA_KEY_ALBUM);
        long duration = md == null ? 0 : md.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
        return new NowPlaying(m.getPackageName(), title, artist, album, duration,
                s == null ? NowPlaying.STATE_NONE : s.getState(), s == null ? -1 : s.getPosition(),
                s == null ? 0 : s.getLastPositionUpdateTime(), s == null ? 1f : s.getPlaybackSpeed(), s == null ? 0 : s.getActions());
    }
    private static String text(android.media.MediaMetadata md, String key) {
        CharSequence v = md.getText(key); return v == null ? "" : v.toString();
    }

    /**
     * The track's own artwork, only as the session hands it over as a bitmap. A URI is never opened or fetched, and no
     * other album's art is ever used: no bitmap means the neutral scene.
     */
    static Bitmap art(MediaController m) {
        try {
            android.media.MediaMetadata md = m.getMetadata(); if (md == null) return null;
            Bitmap b = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
            return b == null || b.isRecycled() || b.getWidth() < 2 || b.getHeight() < 2 ? null : b;
        } catch (RuntimeException e) { return null; }
    }
}
