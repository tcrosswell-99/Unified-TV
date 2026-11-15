package io.unifiedtv.preview;

import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * OD-27 Now Playing as the remote for the real Amazon Music app: it shows what the session reports, maps the remote's
 * keys to the session's own transport controls, offers only the extra buttons the app declares, decodes artwork off the
 * main thread, and stops listening when hidden (playback carries on in Amazon Music).
 */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class NowPlayingControlsTest {
    private MusicSessions.Source before; private MusicArt.Fetcher fetcher; private MusicFakes.Source source; private MusicFakes.Session amazon;

    @Before public void fake() {
        RuntimeEnvironment.getApplication().getSharedPreferences(HouseholdSettings.PREFS, 0).edit().clear().commit(); AmazonMusic.rescan();
        before = MusicSessions.source; fetcher = MusicArt.fetcher;
        source = new MusicFakes.Source(); amazon = new MusicFakes.Session(); source.sessions.add(amazon); MusicSessions.source = source;
    }
    @After public void restore() { MusicSessions.source = before; MusicArt.fetcher = fetcher; }

    private ActivityController<NowPlayingActivity> open() {
        ActivityController<NowPlayingActivity> c = Robolectric.buildActivity(NowPlayingActivity.class, new Intent(RuntimeEnvironment.getApplication(), NowPlayingActivity.class)).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return c;
    }
    private static void tap(NowPlayingActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.key(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.key(new KeyEvent(t, t + 50, KeyEvent.ACTION_UP, code, 0));
    }
    private static String words(View v) {
        StringBuilder b = new StringBuilder(); if (v.getContentDescription() != null) b.append(v.getContentDescription()).append(' ');
        if (v instanceof TextView) b.append(((TextView) v).getText()).append(' ');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }

    @Test public void showsWhatAmazonMusicReports() {
        amazon.title = "Bohemian Rhapsody"; amazon.artist = "Queen";
        try (ActivityController<NowPlayingActivity> c = open()) {
            NowPlayingActivity a = c.get();
            assertSame("Amazon Music's own session is on screen", amazon, a.sessionForTest());
            String shown = words(a.getWindow().getDecorView());
            assertTrue(shown, shown.contains("Bohemian Rhapsody") && shown.contains("Queen"));
            assertEquals("Now Playing listens for changes to the player", 1, amazon.watchers);
        }
    }

    @Test public void remoteKeysPressAmazonMusicsOwnControls() {
        try (ActivityController<NowPlayingActivity> c = open()) {
            NowPlayingActivity a = c.get();
            tap(a, KeyEvent.KEYCODE_DPAD_CENTER); assertEquals("OK while playing pauses", "pause", amazon.calls.get(0));
            tap(a, KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("Right skips to the next track", "next", amazon.calls.get(1));
            tap(a, KeyEvent.KEYCODE_DPAD_LEFT); assertEquals("Left skips back", "previous", amazon.calls.get(2));
            tap(a, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); assertEquals("the remote's Play/Pause key", "pause", amazon.calls.get(3));
            amazon.state = NowPlaying.STATE_PAUSED; amazon.changed.run();
            tap(a, KeyEvent.KEYCODE_DPAD_CENTER); assertEquals("OK while paused plays", "play", amazon.calls.get(4));
            tap(a, KeyEvent.KEYCODE_MEDIA_NEXT); assertEquals("next", amazon.calls.get(5));
        }
    }

    @Test public void holdingLeftOrRightMovesTenSecondsInsteadOfSkipping() {
        amazon.state = NowPlaying.STATE_PAUSED; amazon.position = 30_000;
        try (ActivityController<NowPlayingActivity> c = open()) {
            NowPlayingActivity a = c.get(); long t = SystemClock.uptimeMillis();
            a.key(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
            a.key(new KeyEvent(t, t + 500, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1));
            a.key(new KeyEvent(t, t + 550, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 2));   // too soon for another step
            a.key(new KeyEvent(t, t + 600, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
            assertEquals("one step forward, and no skip on release", java.util.Arrays.asList("seek:40000"), amazon.calls);
            a.key(new KeyEvent(t + 1000, t + 1000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0));
            a.key(new KeyEvent(t + 1000, t + 1500, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 1));
            a.key(new KeyEvent(t + 1000, t + 1600, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0));
            assertEquals("seek:20000", amazon.calls.get(1));
        }
    }

    @Test public void onlyButtonsTheAppDeclaresAreOffered() {
        try (ActivityController<NowPlayingActivity> c = open()) {
            java.util.List<String> extras = c.get().extrasForTest();
            assertFalse("no shuffle invented", extras.contains("Shuffle"));
            assertFalse("no repeat invented", extras.contains("Repeat"));
            assertTrue(extras.toString(), extras.contains("Play on: This TV"));
        }
        amazon.actions = NowPlaying.ACTION_PLAY | NowPlaying.ACTION_PAUSE | NowPlaying.ACTION_SET_REPEAT_MODE | NowPlaying.ACTION_SET_SHUFFLE_MODE;
        amazon.custom.add(new String[]{"com.amazon.THUMBS_UP", "Thumbs up"});
        try (ActivityController<NowPlayingActivity> c = open()) {
            NowPlayingActivity a = c.get(); java.util.List<String> extras = a.extrasForTest();
            assertEquals(extras.toString(), java.util.Arrays.asList("Shuffle", "Repeat", "Thumbs up", "Play on: This TV"), extras.subList(0, 4));
            tap(a, KeyEvent.KEYCODE_DPAD_DOWN); tap(a, KeyEvent.KEYCODE_DPAD_RIGHT); tap(a, KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals("Repeat asks with the media library's standard command", "custom:" + NowPlaying.COMPAT_SET_REPEAT, amazon.calls.get(0));
            assertEquals(NowPlaying.REPEAT_ALL, amazon.args.get(0).getInt(NowPlaying.COMPAT_REPEAT_ARG));
            tap(a, KeyEvent.KEYCODE_DPAD_RIGHT); tap(a, KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals("the app's own thumbs button", "custom:com.amazon.THUMBS_UP", amazon.calls.get(1));
        }
    }

    @Test public void listenersAreRemovedWhenHiddenAndPlaybackIsLeftAlone() {
        ActivityController<NowPlayingActivity> c = open();
        assertEquals(1, source.listeners); assertEquals(1, amazon.watchers);
        c.pause().stop();
        assertEquals("no session listener after onStop", 0, source.listeners);
        assertEquals("no player callback after onStop", 0, amazon.watchers);
        assertTrue("Back and hiding never stop Amazon Music", amazon.calls.isEmpty());
        c.destroy();
    }

    @Test public void artworkIsReadOffTheMainThread() throws Exception {
        AtomicReference<Boolean> onMain = new AtomicReference<>();
        MusicArt.fetcher = (ctx, uri) -> { onMain.set(Looper.myLooper() == Looper.getMainLooper()); return new byte[]{1, 2, 3}; };
        amazon.artUri = "https://art.example.invalid/cover.jpg";
        try (ActivityController<NowPlayingActivity> c = open()) {
            for (int i = 0; i < 100 && onMain.get() == null; i++) Thread.sleep(20);
            assertEquals("the address the session gave is read on the worker, never the main thread", Boolean.FALSE, onMain.get());
        }
        try { MusicArt.load(RuntimeEnvironment.getApplication(), amazon, 100, 100); fail("decoding on the main thread is refused"); }
        catch (IllegalStateException expected) { }
    }

    @Test public void artAddressesAreLimitedToTheAppsOwnSafeKinds() {
        assertTrue(MusicArt.allowed("content://com.amazon.music.tv/art/1"));
        assertTrue(MusicArt.allowed("https://m.media-amazon.com/images/I/x.jpg"));
        assertTrue(MusicArt.allowed("ha:/api/media_player_proxy/media_player.kitchen?cache=1"));
        assertFalse("plain http", MusicArt.allowed("http://example.com/a.jpg"));
        assertFalse("files", MusicArt.allowed("file:///sdcard/a.jpg"));
        assertFalse(MusicArt.allowed("ha://elsewhere/a.jpg"));
        assertEquals("/api/media_player_proxy/media_player.kitchen?cache=12", MusicArt.withoutToken("/api/media_player_proxy/media_player.kitchen?token=SECRET&cache=12"));
        assertEquals("/api/x", MusicArt.withoutToken("/api/x?token=SECRET"));
    }

    @Test public void withoutAccessItExplainsTheOneStep() {
        source.granted = false;
        try (ActivityController<NowPlayingActivity> c = open()) {
            String shown = words(c.get().getWindow().getDecorView());
            assertTrue(shown, shown.contains("allow Unified TV to see media notifications"));
            assertTrue(shown, shown.contains("play music by name"));
        }
    }
}
