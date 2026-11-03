package io.unifiedtv.preview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import java.util.List;

/**
 * One player Now Playing shows and controls (OD-27): an app's media session on this TV (MusicSessions.Controller, through
 * Android's official media controls), or an Echo speaker through Home Assistant (EchoSession). A thin wrapper, so the
 * screen never needs to know which, and tests can stand in for either without a TV.
 */
interface MusicSession {
    /** The app that owns the session (an Echo reports Amazon Music's id). */
    String packageName();
    /** What the household sees as the player's name: the app's own label, or the Echo's name. */
    String label();
    /** The same underlying session: the session token for an app, the entity for an Echo. */
    Object key();
    /** What the player reports now. Never fetched or guessed beyond that report. */
    NowPlaying read();
    /** Artwork the player handed over as a picture, or null. */
    Bitmap bitmap();
    /** Artwork the player handed over as an address (content:, android.resource:, https:, or ha: for Home Assistant), or null. */
    String artUri();
    /** The app's own extra buttons, as {action id, label}. */
    List<String[]> customActions();
    void play();
    void pause();
    void next();
    void previous();
    void seekTo(long ms);
    void playFromSearch(String query, Bundle extras);
    void playFromMediaId(String mediaId, Bundle extras);
    void custom(String action, Bundle args);
    /** Louder (+1) or quieter (-1): only an Echo; this TV's own volume stays with the TV's remote. */
    void volume(int direction);
    /** True when volume() does something (an Echo). */
    boolean ownVolume();
    /** Calls {@code changed} on {@code handler} whenever the player reports something new, until unwatch(). */
    void watch(Runnable changed, Handler handler);
    void unwatch();
}
