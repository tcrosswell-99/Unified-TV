package io.unifiedtv.preview;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/**
 * "Your Amazon Music" (OD-27, best effort): Amazon Music's library through Android's official MediaBrowser, only if
 * Amazon Music's MediaBrowserService agrees to connect with Unified TV. Many music apps allow only Android Auto and the
 * TV's own assistant, so a refusal is expected and is shown plainly (search stays). Tests replace
 * MusicLibraryActivity.library.
 */
interface MusicLibrary {
    /** One entry in the library, as Amazon Music describes it. */
    final class Item {
        final String id, title, subtitle, iconUri; final Bitmap icon; final boolean browsable, playable;
        Item(String id, String title, String subtitle, Bitmap icon, String iconUri, boolean browsable, boolean playable) {
            this.id = id; this.title = NowPlaying.bounded(title, NowPlaying.LINE_MAX); this.subtitle = NowPlaying.bounded(subtitle, NowPlaying.LINE_MAX);
            this.icon = icon; this.iconUri = iconUri; this.browsable = browsable; this.playable = playable;
        }
    }
    /** What the library says, always on the main thread. */
    interface Listener {
        void connected(String root);
        /** Amazon Music refused, had nothing to share, or didn't answer. */
        void refused();
        void children(String parent, List<Item> items);
    }
    /** Connects to Amazon Music's library, or returns null when it declares no library service. */
    interface Factory { MusicLibrary open(Context c, Listener l); }

    /** How long to wait for Amazon Music to answer before treating it as a refusal. */
    long ANSWER_MS = 6000;
    int MAX_ITEMS = 200;

    void connect();
    void children(String parentId);
    /** Plays the entry in Amazon Music (playFromMediaId on the library's own session). */
    boolean play(String mediaId);
    void close();

    /** Android's MediaBrowser, connected to Amazon Music's service. */
    final class Browser implements MusicLibrary {
        private final Context c; private final Listener l; private final MediaBrowser browser;
        private final Handler main = new Handler(Looper.getMainLooper());
        private boolean answered, closed;
        private final Runnable timeout = this::refuse;

        static MusicLibrary open(Context c, Listener l) {
            ComponentName service = AmazonMusic.browserService(c, AmazonMusic.installed(c));
            return service == null ? null : new Browser(c, l, service);
        }
        private Browser(Context c, Listener l, ComponentName service) {
            this.c = c; this.l = l;
            browser = new MediaBrowser(c, service, new MediaBrowser.ConnectionCallback() {
                @Override public void onConnected() { main.removeCallbacks(timeout); if (closed) return; String root = browser.getRoot(); if (root == null || root.isEmpty()) refuse(); else l.connected(root); }
                @Override public void onConnectionFailed() { refuse(); }
                @Override public void onConnectionSuspended() { refuse(); }
            }, null);
        }
        private void refuse() { main.removeCallbacks(timeout); if (closed || answered) return; answered = true; l.refused(); }
        @Override public void connect() { main.postDelayed(timeout, ANSWER_MS); try { browser.connect(); } catch (RuntimeException e) { refuse(); } }
        @Override public void children(String parentId) {
            try {
                browser.subscribe(parentId, new MediaBrowser.SubscriptionCallback() {
                    @Override public void onChildrenLoaded(String parent, List<MediaBrowser.MediaItem> list) {
                        answered = true; if (closed) return;
                        List<Item> out = new ArrayList<>();
                        for (MediaBrowser.MediaItem m : list) { if (out.size() >= MAX_ITEMS) break; out.add(item(m)); }
                        try { browser.unsubscribe(parent); } catch (RuntimeException ignored) { }
                        l.children(parent, out);
                    }
                    @Override public void onError(String parent) { if (!closed) l.children(parent, new ArrayList<>()); }
                });
            } catch (RuntimeException e) { l.children(parentId, new ArrayList<>()); }
        }
        private static Item item(MediaBrowser.MediaItem m) {
            MediaDescription d = m.getDescription();
            return new Item(m.getMediaId(), String.valueOf(d.getTitle() == null ? "" : d.getTitle()), String.valueOf(d.getSubtitle() == null ? "" : d.getSubtitle()),
                    d.getIconBitmap(), d.getIconUri() == null ? null : d.getIconUri().toString(), m.isBrowsable(), m.isPlayable());
        }
        @Override public boolean play(String mediaId) {
            try { new MediaController(c, browser.getSessionToken()).getTransportControls().playFromMediaId(mediaId, null); return true; }
            catch (RuntimeException e) { return false; }
        }
        @Override public void close() { closed = true; main.removeCallbacks(timeout); try { browser.disconnect(); } catch (RuntimeException ignored) { } }
    }
}
