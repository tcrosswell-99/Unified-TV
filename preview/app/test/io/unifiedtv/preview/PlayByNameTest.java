package io.unifiedtv.preview;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.time.Duration;
import java.util.Arrays;
import static org.junit.Assert.*;

/**
 * OD-27 play by name: Amazon Music's live session first (playFromSearch), then Android's standard play-from-search request
 * addressed only to Amazon Music, then Amazon Music's own search with a plain sentence. Kids only with an adult's approval.
 */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk = 30)
public class PlayByNameTest {
    private MusicSessions.Source before; private MusicFakes.Source source;

    @Before public void fake() {
        RuntimeEnvironment.getApplication().getSharedPreferences(HouseholdSettings.PREFS, 0).edit().clear().commit(); AmazonMusic.rescan();
        before = MusicSessions.source; source = new MusicFakes.Source(); MusicSessions.source = source;
    }
    @After public void restore() { MusicSessions.source = before; }

    @Test public void routeOrderIsSessionThenIntentThenTheApp() {
        assertEquals(AmazonMusic.Route.SESSION, AmazonMusic.route(true, true, true));
        assertEquals(AmazonMusic.Route.INTENT, AmazonMusic.route(false, true, true));
        assertEquals(AmazonMusic.Route.APP, AmazonMusic.route(false, false, true));
        assertEquals(AmazonMusic.Route.STORE, AmazonMusic.route(true, true, false));
    }

    @Test public void theRequestIsAndroidsStandardOneAddressedOnlyToAmazonMusic() {
        Intent i = AmazonMusic.searchIntent(MusicFakes.AMAZON, "Adele", AmazonMusic.Focus.ARTIST);
        assertEquals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH, i.getAction());
        assertEquals("explicit: never a chooser or another app", MusicFakes.AMAZON, i.getPackage());
        assertEquals("Adele", i.getStringExtra(SearchManager.QUERY));
        assertEquals(MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE, i.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS));
        assertEquals("Adele", i.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST));
        Bundle album = AmazonMusic.extras("21", AmazonMusic.Focus.ALBUM);
        assertEquals(MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE, album.getString(MediaStore.EXTRA_MEDIA_FOCUS)); assertEquals("21", album.getString(MediaStore.EXTRA_MEDIA_ALBUM));
        assertEquals(MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE, AmazonMusic.extras("Chill", AmazonMusic.Focus.PLAYLIST).getString(MediaStore.EXTRA_MEDIA_FOCUS));
        assertEquals("Hello", AmazonMusic.extras("Hello", AmazonMusic.Focus.SONG).getString(MediaStore.EXTRA_MEDIA_TITLE));
        assertEquals("unstructured", "vnd.android.cursor.item/*", AmazonMusic.extras("jazz", AmazonMusic.Focus.ANY).getString(MediaStore.EXTRA_MEDIA_FOCUS));
    }

    @Test public void amazonMusicsLiveSessionIsAskedFirst() {
        MusicFakes.installAmazon(RuntimeEnvironment.getApplication(), true, true);
        MusicFakes.Session s = new MusicFakes.Session(MusicFakes.AMAZON, NowPlaying.STATE_PAUSED);
        s.actions = NowPlaying.ACTION_PLAY | NowPlaying.ACTION_PLAY_FROM_SEARCH; source.sessions.add(s);
        try (ActivityController<Activity> c = Robolectric.buildActivity(Activity.class).setup()) {
            AmazonMusic.Result r = AmazonMusic.play(c.get(), "Adele", AmazonMusic.Focus.ARTIST);
            assertEquals(AmazonMusic.Route.SESSION, r.route);
            assertEquals(Arrays.asList("search:Adele"), s.calls);
            assertEquals("Adele", s.args.get(0).getString(MediaStore.EXTRA_MEDIA_ARTIST));
            Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals("Now Playing waits for it", NowPlayingActivity.class.getName(), next.getComponent().getClassName());
            assertEquals("Adele", next.getStringExtra(NowPlayingActivity.EXTRA_QUERY));
            assertNull("nothing else was opened", Shadows.shadowOf(c.get()).getNextStartedActivity());
            assertEquals(Arrays.asList("Adele"), AmazonMusic.recent(c.get()));
        }
    }

    @Test public void withoutASessionAndroidsRequestGoesToAmazonMusic() {
        MusicFakes.installAmazon(RuntimeEnvironment.getApplication(), true, true);
        try (ActivityController<Activity> c = Robolectric.buildActivity(Activity.class).setup()) {
            AmazonMusic.Result r = AmazonMusic.play(c.get(), "Chill Mix", AmazonMusic.Focus.PLAYLIST);
            assertEquals(AmazonMusic.Route.INTENT, r.route);
            Intent sent = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH, sent.getAction());
            assertEquals(MusicFakes.AMAZON, sent.getPackage());
            assertEquals("Chill Mix", sent.getStringExtra(MediaStore.EXTRA_MEDIA_PLAYLIST));
        }
    }

    @Test public void whenNeitherWorksAmazonMusicsOwnSearchOpensAndItSaysSo() {
        MusicFakes.installAmazon(RuntimeEnvironment.getApplication(), false, true);
        try (ActivityController<Activity> c = Robolectric.buildActivity(Activity.class).setup()) {
            AmazonMusic.Result r = AmazonMusic.play(c.get(), "Abba", AmazonMusic.Focus.ANY);
            assertEquals(AmazonMusic.Route.APP, r.route);
            assertTrue(r.words, r.words.contains("didn't take a request from another app"));
            Intent sent = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals(Intent.ACTION_SEARCH, sent.getAction()); assertEquals(MusicFakes.AMAZON, sent.getPackage());
            assertEquals("Abba", sent.getStringExtra(SearchManager.QUERY));
        }
    }

    @Test public void aSessionThatStartsNothingIsFollowedByAndroidsRequest() {
        MusicFakes.installAmazon(RuntimeEnvironment.getApplication(), true, false);
        MusicFakes.Session s = new MusicFakes.Session(MusicFakes.AMAZON, NowPlaying.STATE_PAUSED); source.sessions.add(s);
        Intent wait = NowPlayingActivity.waiting(RuntimeEnvironment.getApplication(), "Blur", AmazonMusic.Focus.ANY, AmazonMusic.Route.SESSION, s.read().trackKey());
        try (ActivityController<NowPlayingActivity> c = Robolectric.buildActivity(NowPlayingActivity.class, wait).setup()) {
            assertTrue(c.get().waitingForTest());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(AmazonMusic.WAIT_MS + 1500));
            Intent sent = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull("the play-from-search request followed", sent);
            assertEquals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH, sent.getAction());
            assertEquals(MusicFakes.AMAZON, sent.getPackage());
        }
    }

    @Test public void kidsNeedAnAdultsApprovalForAmazonMusic() {
        MusicFakes.installAmazon(RuntimeEnvironment.getApplication(), true, true);
        RuntimeEnvironment.getApplication().getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putString(Profiles.PREF, Profiles.CHILD).commit();
        try (ActivityController<Activity> c = Robolectric.buildActivity(Activity.class).setup()) {
            AmazonMusic.Result r = AmazonMusic.play(c.get(), "Frozen", AmazonMusic.Focus.ANY);
            assertEquals(AmazonMusic.Route.REFUSED, r.route);
            assertNull("nothing opened", Shadows.shadowOf(c.get()).getNextStartedActivity());
        }
    }

    @Test public void recentSearchesAreNewestFirstWithoutRepeats() {
        assertEquals(Arrays.asList("b", "a"), AmazonMusic.add(Arrays.asList("a", "B"), "b"));
        java.util.List<String> many = Arrays.asList("1", "2", "3", "4", "5", "6");
        assertEquals(AmazonMusic.RECENT_MAX, AmazonMusic.add(many, "7").size());
        assertEquals("7", AmazonMusic.add(many, "7").get(0));
    }

    @Test public void amazonMusicIsFoundByItsOwnName() {
        MusicFakes.installAmazon(RuntimeEnvironment.getApplication(), false, false);
        assertTrue(AmazonMusic.packages(RuntimeEnvironment.getApplication()).contains(MusicFakes.AMAZON));
        assertEquals(MusicFakes.AMAZON, AmazonMusic.installed(RuntimeEnvironment.getApplication()));
        assertTrue(AmazonMusic.isAmazonLabel(" Amazon Music ")); assertFalse(AmazonMusic.isAmazonLabel("Amazon Prime Video"));
    }
}
