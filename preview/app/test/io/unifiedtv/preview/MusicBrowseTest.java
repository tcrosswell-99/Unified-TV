package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/**
 * OD-27 "Your Amazon Music" (best effort): shown only when Amazon Music's MediaBrowserService agrees to share; a refusal
 * hides it, keeps search, and is recorded for Owner diagnostics. OK on an entry plays it through playFromMediaId.
 */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class MusicBrowseTest {
    private MusicLibrary.Factory factory; private Echoes.Finder finder; private MusicSessions.Source sessions;

    /** A library that answers as a test says. */
    static final class FakeLibrary implements MusicLibrary {
        final Listener l; final boolean refuse; final List<Item> rootItems; final List<String> played = new ArrayList<>(); boolean closed;
        FakeLibrary(Listener l, boolean refuse, List<Item> rootItems) { this.l = l; this.refuse = refuse; this.rootItems = rootItems; }
        @Override public void connect() { if (refuse) l.refused(); else l.connected("root"); }
        @Override public void children(String parentId) { l.children(parentId, "root".equals(parentId) ? rootItems : new ArrayList<>()); }
        @Override public boolean play(String mediaId) { played.add(mediaId); return true; }
        @Override public void close() { closed = true; }
    }
    private FakeLibrary last;

    @Before public void setUp() {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().clear().commit(); AmazonMusic.rescan();
        factory = MusicLibraryActivity.library; finder = Echoes.finder; sessions = MusicSessions.source;
        MusicFakes.Source none = new MusicFakes.Source(); none.granted = false; MusicSessions.source = none;
        Echoes.finder = c -> new Echoes.Found(Echoes.NOT_CONNECTED, new ArrayList<>());
        MusicFakes.installAmazon(app, true, false);
    }
    @After public void restore() { MusicLibraryActivity.library = factory; Echoes.finder = finder; MusicSessions.source = sessions; }

    private static Button button(View v, String text) {
        if (v instanceof Button && ((Button) v).getText().toString().startsWith(text)) return (Button) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = button(((ViewGroup) v).getChildAt(i), text); if (b != null) return b; }
        return null;
    }
    private static String words(View v) {
        StringBuilder b = new StringBuilder(); if (v instanceof TextView && v.getVisibility() == View.VISIBLE) b.append(((TextView) v).getText()).append(' ');
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }
    private static String pref(String key) { return RuntimeEnvironment.getApplication().getSharedPreferences(HouseholdSettings.PREFS, 0).getString(key, ""); }

    @Test public void refusedBrowsingIsHiddenAndRecorded() {
        MusicLibraryActivity.library = (c, l) -> last = new FakeLibrary(l, true, null);
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Button lib = button(c.get().getWindow().getDecorView(), MusicLibraryActivity.TITLE);
            assertNotNull(lib); assertEquals("hidden when Amazon Music refuses", View.GONE, lib.getVisibility());
            assertNotNull("search stays", button(c.get().getWindow().getDecorView(), "Play"));
            assertEquals(AmazonMusic.BROWSE_REFUSED, pref(AmazonMusic.BROWSE_PREF));
            assertTrue("the probe is closed", last.closed);
            assertTrue(AmazonMusic.diagnosis(c.get()), AmazonMusic.diagnosis(c.get()).contains("Amazon Music browsing: refused"));
        }
    }

    @Test public void anEmptyLibraryCountsAsRefused() {
        MusicLibraryActivity.library = (c, l) -> last = new FakeLibrary(l, false, new ArrayList<>());
        try (ActivityController<MusicLibraryActivity> c = Robolectric.buildActivity(MusicLibraryActivity.class).setup()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(words(c.get().getWindow().getDecorView()).contains(MusicLibraryActivity.REFUSED_WORDS));
            assertEquals(AmazonMusic.BROWSE_REFUSED, pref(AmazonMusic.BROWSE_PREF));
        }
    }

    @Test public void sharedLibraryIsShownAndPlaysFromMediaId() {
        List<MusicLibrary.Item> items = Arrays.asList(
            new MusicLibrary.Item("recent", "Recently played", "", null, null, true, false),
            new MusicLibrary.Item("pl:1", "Friday Mix", "Playlist", null, null, false, true));
        MusicLibraryActivity.library = (c, l) -> last = new FakeLibrary(l, false, items);
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, button(c.get().getWindow().getDecorView(), MusicLibraryActivity.TITLE).getVisibility());
            assertEquals(AmazonMusic.BROWSE_AVAILABLE, pref(AmazonMusic.BROWSE_PREF));
        }
        try (ActivityController<MusicLibraryActivity> c = Robolectric.buildActivity(MusicLibraryActivity.class).setup()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            View root = c.get().getWindow().getDecorView();
            assertTrue(words(root), words(root).contains("Recently played"));
            button(root, "Friday Mix").performClick();
            assertEquals(Arrays.asList("pl:1"), last.played);
            Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals(NowPlayingActivity.class.getName(), next.getComponent().getClassName());
        }
    }

    @Test public void kidsWithoutApprovalSeeNoLibrary() {
        RuntimeEnvironment.getApplication().getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putString(Profiles.PREF, Profiles.CHILD).commit();
        MusicLibraryActivity.library = (c, l) -> { fail("Kids without approval never connect"); return null; };
        try (ActivityController<MusicLibraryActivity> c = Robolectric.buildActivity(MusicLibraryActivity.class).setup()) {
            assertTrue(words(c.get().getWindow().getDecorView()).contains("Ask an adult"));
        }
    }
}
