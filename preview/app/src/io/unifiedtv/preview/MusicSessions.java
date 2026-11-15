package io.unifiedtv.preview;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The TV's media sessions as MusicSession (OD-27), through Android's own media APIs only: MediaSessionManager (allowed by
 * the owner's notification access for MediaWatch) and each session's MediaController. No Amazon API, no scraping. Tests
 * replace {@link #source}.
 */
final class MusicSessions {
    private MusicSessions() {}

    /** Where sessions come from. */
    interface Source {
        /** Whether the owner has allowed Unified TV to see media sessions. */
        boolean granted(Context c);
        /** The active sessions, newest first; empty without access. */
        List<MusicSession> active(Context c);
        /** Calls {@code changed} on {@code handler} when sessions start or end, until unlisten(). */
        void listen(Context c, Runnable changed, Handler handler);
        void unlisten(Context c, Runnable changed);
    }

    static volatile Source source = new Platform();

    /** The session to show: Amazon Music first (AmazonMusic.packages), else any app that is playing; null when none. */
    static MusicSession best(Context c, List<MusicSession> list) {
        String[] packages = new String[list.size()]; int[] states = new int[list.size()];
        for (int i = 0; i < list.size(); i++) { packages[i] = list.get(i).packageName(); states[i] = list.get(i).read().state; }
        int i = NowPlaying.pick(packages, states, c.getPackageName(), AmazonMusic.packages(c));
        return i < 0 ? null : list.get(i);
    }
    /** Amazon Music's own session that is still alive (playing or paused), or null. */
    static MusicSession amazon(Context c, List<MusicSession> list) {
        Set<String> amazon = AmazonMusic.packages(c); MusicSession paused = null;
        for (MusicSession s : list) {
            if (!amazon.contains(s.packageName())) continue;
            int state = s.read().state;
            if (NowPlaying.playing(state)) return s;
            if (paused == null && NowPlaying.alive(state)) paused = s;
        }
        return paused;
    }

    /** Android's own media sessions. */
    static final class Platform implements Source {
        private final Map<Runnable, MediaSessionManager.OnActiveSessionsChangedListener> listeners = new IdentityHashMap<>();
        @Override public boolean granted(Context c) { return MediaWatch.granted(c); }
        @Override public List<MusicSession> active(Context c) {
            List<MusicSession> out = new ArrayList<>();
            for (MediaController m : MediaWatch.sessions(c)) out.add(new Controller(c, m));
            return out;
        }
        @Override public void listen(Context c, Runnable changed, Handler handler) {
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            if (m == null || !granted(c)) return;
            MediaSessionManager.OnActiveSessionsChangedListener l = list -> changed.run();
            try { m.addOnActiveSessionsChangedListener(l, MediaWatch.component(c), handler); synchronized (listeners) { listeners.put(changed, l); } }
            catch (RuntimeException ignored) { /* access withdrawn a moment ago */ }
        }
        @Override public void unlisten(Context c, Runnable changed) {
            MediaSessionManager.OnActiveSessionsChangedListener l; synchronized (listeners) { l = listeners.remove(changed); }
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            if (l != null && m != null) try { m.removeOnActiveSessionsChangedListener(l); } catch (RuntimeException ignored) { }
        }
    }

    /** One app's session, through its MediaController. Every call is guarded: a session can end at any moment. */
    static final class Controller implements MusicSession {
        private final MediaController m; private final String label; private MediaController.Callback callback;
        Controller(Context c, MediaController m) { this.m = m; this.label = AmazonMusic.label(c, m.getPackageName()); }
        @Override public String packageName() { return m.getPackageName(); }
        @Override public String label() { return label; }
        @Override public Object key() { return m.getSessionToken(); }
        @Override public NowPlaying read() { return MediaWatch.read(m); }
        @Override public Bitmap bitmap() { return MediaWatch.art(m); }
        @Override public String artUri() {
            try {
                MediaMetadata md = m.getMetadata(); if (md == null) return null;
                for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
                    String u = md.getString(k); if (u != null && MusicArt.allowed(u)) return u;
                }
            } catch (RuntimeException ignored) { }
            return null;
        }
        @Override public List<String[]> customActions() {
            try {
                PlaybackState s = m.getPlaybackState(); if (s == null) return Collections.emptyList();
                List<String[]> out = new ArrayList<>();
                for (PlaybackState.CustomAction a : s.getCustomActions()) out.add(new String[]{a.getAction(), String.valueOf(a.getName())});
                return out;
            } catch (RuntimeException e) { return Collections.emptyList(); }
        }
        private MediaController.TransportControls tc() { return m.getTransportControls(); }
        @Override public void play() { tc().play(); }
        @Override public void pause() { tc().pause(); }
        @Override public void next() { tc().skipToNext(); }
        @Override public void previous() { tc().skipToPrevious(); }
        @Override public void seekTo(long ms) { tc().seekTo(ms); }
        @Override public void playFromSearch(String query, Bundle extras) { tc().playFromSearch(query, extras); }
        @Override public void playFromMediaId(String mediaId, Bundle extras) { tc().playFromMediaId(mediaId, extras); }
        @Override public void custom(String action, Bundle args) { tc().sendCustomAction(action, args); }
        @Override public void volume(int direction) { }
        @Override public boolean ownVolume() { return false; }
        @Override public void watch(Runnable changed, Handler handler) {
            unwatch();
            callback = new MediaController.Callback() {
                @Override public void onPlaybackStateChanged(PlaybackState state) { changed.run(); }
                @Override public void onMetadataChanged(MediaMetadata metadata) { changed.run(); }
                @Override public void onSessionDestroyed() { changed.run(); }
            };
            try { m.registerCallback(callback, handler); } catch (RuntimeException ignored) { callback = null; }
        }
        @Override public void unwatch() {
            if (callback == null) return;
            try { m.unregisterCallback(callback); } catch (RuntimeException ignored) { }
            callback = null;
        }
    }
}
