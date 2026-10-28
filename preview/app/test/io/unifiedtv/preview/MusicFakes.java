package io.unifiedtv.preview;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowPackageManager;

/**
 * Stand-ins for OD-27's music tests: a media session that records every call (in place of Amazon Music's MediaController),
 * a session source (in place of MediaSessionManager), and an installed "Amazon Music" app in Robolectric's package manager.
 */
final class MusicFakes {
    static final String AMAZON = "com.amazon.music.tv";

    /** One player: what it reports, and every control pressed. */
    static final class Session implements MusicSession {
        String pkg = AMAZON, title = "Song", artist = "Artist", album = "Album", artUri; Bitmap bitmap;
        int state = NowPlaying.STATE_PLAYING; long actions = 0, position = 30_000, duration = 200_000;
        final List<String[]> custom = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        final List<Bundle> args = new ArrayList<>();
        int watchers; Runnable changed; Handler handler;
        Session() {}
        Session(String pkg, int state) { this.pkg = pkg; this.state = state; }
        @Override public String packageName() { return pkg; }
        @Override public String label() { return AmazonMusic.NAME; }
        @Override public Object key() { return "key:" + pkg; }
        @Override public NowPlaying read() { return new NowPlaying(pkg, title, artist, album, duration, state, position, android.os.SystemClock.elapsedRealtime(), 1f, actions); }
        @Override public Bitmap bitmap() { return bitmap; }
        @Override public String artUri() { return artUri; }
        @Override public List<String[]> customActions() { return custom; }
        @Override public void play() { calls.add("play"); }
        @Override public void pause() { calls.add("pause"); }
        @Override public void next() { calls.add("next"); }
        @Override public void previous() { calls.add("previous"); }
        @Override public void seekTo(long ms) { calls.add("seek:" + ms); }
        @Override public void playFromSearch(String query, Bundle extras) { calls.add("search:" + query); args.add(extras); }
        @Override public void playFromMediaId(String mediaId, Bundle extras) { calls.add("mediaId:" + mediaId); }
        @Override public void custom(String action, Bundle a) { calls.add("custom:" + action); args.add(a); }
        @Override public void volume(int direction) { calls.add("volume:" + direction); }
        @Override public boolean ownVolume() { return false; }
        @Override public void watch(Runnable changed, Handler handler) { watchers++; this.changed = changed; this.handler = handler; }
        @Override public void unwatch() { if (watchers > 0) watchers--; changed = null; }
    }

    /** The TV's sessions as a test sets them. */
    static final class Source implements MusicSessions.Source {
        boolean granted = true; final List<MusicSession> sessions = new ArrayList<>(); int listeners;
        @Override public boolean granted(Context c) { return granted; }
        @Override public List<MusicSession> active(Context c) { return granted ? new ArrayList<>(sessions) : new ArrayList<>(); }
        @Override public void listen(Context c, Runnable changed, Handler h) { listeners++; }
        @Override public void unlisten(Context c, Runnable changed) { listeners--; }
    }

    /** "Amazon Music" installed with a TV launch screen; optionally handling play-from-search and plain search. */
    static void installAmazon(Context c, boolean playFromSearch, boolean search) {
        AmazonMusic.rescan();
        ShadowPackageManager spm = Shadows.shadowOf(c.getPackageManager());
        PackageInfo pi = new PackageInfo(); pi.packageName = AMAZON;
        ApplicationInfo ai = new ApplicationInfo(); ai.packageName = AMAZON; ai.nonLocalizedLabel = AmazonMusic.NAME; ai.enabled = true; pi.applicationInfo = ai;
        spm.installPackage(pi);
        ComponentName main = new ComponentName(AMAZON, AMAZON + ".Main");
        spm.addActivityIfNotPresent(main);
        IntentFilter launcher = new IntentFilter(Intent.ACTION_MAIN); launcher.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        spm.addIntentFilterForActivity(main, launcher);
        if (playFromSearch) {
            ComponentName player = new ComponentName(AMAZON, AMAZON + ".PlayFromSearch");
            spm.addActivityIfNotPresent(player);
            IntentFilter f = new IntentFilter(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH); f.addCategory(Intent.CATEGORY_DEFAULT);
            spm.addIntentFilterForActivity(player, f);
        }
        if (search) {
            ComponentName find = new ComponentName(AMAZON, AMAZON + ".Search");
            spm.addActivityIfNotPresent(find);
            IntentFilter f = new IntentFilter(Intent.ACTION_SEARCH); f.addCategory(Intent.CATEGORY_DEFAULT);
            spm.addIntentFilterForActivity(find, f);
        }
    }
}
