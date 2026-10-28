package io.unifiedtv.preview;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Collections;
import java.util.List;

/**
 * Lets Unified TV see which app is playing music and press its own Play, Pause and Skip (spec 11, Music). Android only
 * shares media sessions with an app the owner has allowed to see notifications, so this listener exists for that
 * permission. The approval itself is what MediaSessionManager checks, and it stays in place until the owner withdraws it.
 *
 * One notification is read (owner request, 25 October 2026): NordVPN's own, for the country beside Home's VPN shield
 * (NordCountry). Only when NordVPN is installed does the listener stay connected; every other app's notification is
 * ignored the moment it arrives, and only NordVPN's country code is kept, in memory. Nothing is stored or sent anywhere.
 * Without NordVPN it asks to be disconnected as soon as Android connects it, as before.
 *
 * OD-26 (owner, 27 October 2026): the same notification's connected-or-not word (NordCountry.connected) tells
 * LiveTvProtection that the VPN Android reports is NordVPN's, and a "disconnected" word while Live TV runs asks the Live
 * TV safety net (LiveTvGuard) to look again.
 */
public final class MediaWatch extends NotificationListenerService {
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        if (LocalDiscovery.nordVpn(this)) {
            try { StatusBarNotification[] now = getActiveNotifications(); NordCountry.code = null; NordCountry.connected = null; if (now != null) for (StatusBarNotification n : now) posted(n); }
            catch (RuntimeException ignored) {}
            return;
        }
        try { requestUnbind(); } catch (RuntimeException ignored) { /* stays connected but still reads nothing */ }
    }
    @Override public void onListenerDisconnected() { NordCountry.code = null; NordCountry.connected = null; super.onListenerDisconnected(); }
    @Override public void onNotificationPosted(StatusBarNotification n) { posted(n); }
    @Override public void onNotificationRemoved(StatusBarNotification n) {
        if (n != null && LiveTvProtection.NORDVPN.equals(n.getPackageName())) { NordCountry.code = null; NordCountry.connected = null; }
    }
    /** NordVPN's notification only: its country, or nothing when it doesn't say connected. Others are not read. */
    private static void posted(StatusBarNotification n) {
        if (n == null || !LiveTvProtection.NORDVPN.equals(n.getPackageName())) return;
        android.os.Bundle x = n.getNotification() == null ? null : n.getNotification().extras;
        if (x == null) return;
        CharSequence[] texts = {x.getCharSequence(android.app.Notification.EXTRA_TITLE), x.getCharSequence(android.app.Notification.EXTRA_TEXT),
            x.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT), x.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)};
        String iso = NordCountry.from(texts);
        if (iso != null) NordCountry.code = iso; else if (NordCountry.stopped(texts)) NordCountry.code = null;   // other NordVPN notices change nothing
        Boolean says = NordCountry.status(texts);
        if (says == null) return;
        NordCountry.connected = says;
        if (!says) LiveTvGuard.nordStopped();   // the safety net looks again (it decides with the same rule)
        else NordConnect.nordSaid();            // a Connect for Live TV waiting on NordVPN looks again
    }

    static ComponentName component(Context c) { return new ComponentName(c, MediaWatch.class); }

    /**
     * Opens the TV's own notification-access screen for Unified TV (the detail page where Android has one); false when the
     * TV offers neither, so the caller can show the one ADB line. In Kids it needs the adult PIN (Launch.system).
     */
    static boolean openAccessSettings(android.app.Activity a) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.content.Intent detail = new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(a).flattenToString());
            if (startSettings(a, detail)) return true;
        }
        return startSettings(a, new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }
    private static boolean startSettings(android.app.Activity a, android.content.Intent i) {
        // System settings: in Kids only behind the adult PIN, then the Kids guard lets them stay open a while.
        if (Profiles.kids(a)) { if (i.resolveActivity(a.getPackageManager()) == null) return false; Launch.system(a, i, "This TV doesn't offer that setting"); return true; }
        try { a.startActivity(i); return true; } catch (android.content.ActivityNotFoundException | SecurityException e) { return false; }
    }

    /**
     * Android's own notification access screen, or null when this TV has none (the owner's TCL: build run 99). Then access
     * can only be given through the TV's debugging (NotificationAccessActivity) or from a computer ({@link #pcCommand}).
     */
    static android.content.Intent accessScreen(Context c) {
        android.content.Intent i = accessSettings(c);
        try { return i.resolveActivity(c.getPackageManager()) != null ? i : null; } catch (RuntimeException e) { return null; }
    }
    /** The one computer command that gives this app's listener access (as NotificationAccessActivity sends it on the TV). */
    static String pcCommand(Context c) { return "adb shell cmd notification allow_listener " + c.getPackageName() + "/" + io.unifiedtv.adb.AdbCommands.LISTENER; }

    /** Android's own notification access screen: this app's page where the TV offers it (Android 11+), else the list. */
    static android.content.Intent accessSettings(Context c) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.content.Intent detail = new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(c).flattenToString());
            try { if (detail.resolveActivity(c.getPackageManager()) != null) return detail; } catch (RuntimeException ignored) { }
        }
        return new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }

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
        int i = NowPlaying.pick(packages, states, c.getPackageName(), AmazonMusic.packages(c));
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
     * The track's own artwork as the session hands it over as a bitmap (album art, art, then its display icon). An address
     * the session gives instead is read by MusicArt, off the main thread (OD-27). No other album's art is ever used: none
     * means the neutral scene.
     */
    static Bitmap art(MediaController m) {
        try {
            android.media.MediaMetadata md = m.getMetadata(); if (md == null) return null;
            Bitmap b = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            return b == null || b.isRecycled() || b.getWidth() < 2 || b.getHeight() < 2 ? null : b;
        } catch (RuntimeException e) { return null; }
    }
}
