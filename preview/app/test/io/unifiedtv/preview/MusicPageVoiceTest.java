package io.unifiedtv.preview;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowPackageManager;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/**
 * Owner, TCL build run 99: the Music page's microphone (and the remote's voice key) fills the field and plays at once with
 * the "Looking for" choice; with no speech service the microphone is hidden. Under the field, "Your playlists" when Amazon
 * Music shares its library, else "Recently played" from what Unified TV saw play; hidden when empty.
 */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class MusicPageVoiceTest {
    private MusicLibrary.Factory factory; private Echoes.Finder finder; private MusicSessions.Source sessions;
    private final List<String> played = new ArrayList<>();

    /** A library with the given children per shelf. */
    private final class Library implements MusicLibrary {
        final Listener l; final Map<String, List<Item>> tree;
        Library(Listener l, Map<String, List<Item>> tree) { this.l = l; this.tree = tree; }
        @Override public void connect() { if (tree == null) l.refused(); else l.connected("root"); }
        @Override public void children(String parentId) { l.children(parentId, tree.getOrDefault(parentId, new ArrayList<>())); }
        @Override public boolean play(String mediaId) { played.add(mediaId); return true; }
        @Override public void close() { }
    }

    private static Context app() { return RuntimeEnvironment.getApplication(); }

    @Before public void setUp() {
        app().getSharedPreferences(HouseholdSettings.PREFS, 0).edit().clear().commit(); AmazonMusic.rescan();
        factory = MusicLibraryActivity.library; finder = Echoes.finder; sessions = MusicSessions.source;
        MusicFakes.Source none = new MusicFakes.Source(); none.granted = false; MusicSessions.source = none;   // nothing playing: the page stays
        Echoes.finder = c -> new Echoes.Found(Echoes.NOT_CONNECTED, new ArrayList<>());
        MusicLibraryActivity.library = (c, l) -> new Library(l, null);   // refused unless a test says otherwise
        MusicFakes.installAmazon(app(), true, false);
    }
    @After public void restore() { MusicLibraryActivity.library = factory; Echoes.finder = finder; MusicSessions.source = sessions; org.robolectric.shadows.ShadowSpeechRecognizer.reset(); }

    /** The TV's speech service, usable inside the page, and the microphone allowed. */
    private static void speechService() {
        ComponentName service = new ComponentName("com.example.speech", "com.example.speech.Service");
        Shadows.shadowOf(app().getPackageManager()).addServiceIfNotPresent(service);
        Shadows.shadowOf(app().getPackageManager()).addIntentFilterForService(service, new IntentFilter(android.speech.RecognitionService.SERVICE_INTERFACE));
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO);
    }
    private static org.robolectric.shadows.ShadowSpeechRecognizer recogniser() {
        return Shadows.shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer());
    }
    private static android.os.Bundle heard(String w) {
        android.os.Bundle b = new android.os.Bundle(); b.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(Arrays.asList(w))); return b;
    }

    private static void speechScreen() {
        ShadowPackageManager spm = Shadows.shadowOf(app().getPackageManager());
        ComponentName voice = new ComponentName("com.example.tvvoice", "com.example.tvvoice.Listen");
        spm.addActivityIfNotPresent(voice);
        IntentFilter f = new IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); f.addCategory(Intent.CATEGORY_DEFAULT);
        spm.addIntentFilterForActivity(voice, f);
    }
    private static Button button(View v, String text) {
        if (v instanceof Button && v.getVisibility() == View.VISIBLE && ((Button) v).getText().toString().startsWith(text)) return (Button) v;
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = button(((ViewGroup) v).getChildAt(i), text); if (b != null) return b; }
        return null;
    }
    private static EditText field(View v) {
        if (v instanceof EditText) return (EditText) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { EditText e = field(((ViewGroup) v).getChildAt(i)); if (e != null) return e; }
        return null;
    }
    private static String words(View v) {
        StringBuilder b = new StringBuilder(); if (v instanceof TextView && v.getVisibility() == View.VISIBLE) b.append(((TextView) v).getText()).append(' ');
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }
    private static void until(BooleanSupplier done) throws InterruptedException {
        for (int i = 0; i < 150 && !done.getAsBoolean(); i++) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20); }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private static View root(Activity a) { return a.getWindow().getDecorView(); }
    /** The next request Android's play-from-search got, skipping anything else started. */
    private static Intent sentToAmazon(ShadowActivity s) {
        for (Intent i; (i = s.getNextStartedActivity()) != null; ) if (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(i.getAction())) return i;
        return null;
    }

    @Test public void spokenWordsFillTheFieldAndPlayWithTheChosenKind() {
        speechScreen();
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            MusicActivity a = c.get(); Shadows.shadowOf(Looper.getMainLooper()).idle();
            button(root(a), "Looking for").performClick(); button(root(a), "Looking for").performClick();   // Anything, Artist, Album
            assertTrue(words(root(a)).contains("Looking for: Album"));
            Button mic = button(root(a), "Speak"); assertNotNull("a microphone beside the field", mic); assertTrue(mic.isFocusable());
            mic.performClick();
            ShadowActivity s = Shadows.shadowOf(a);
            Intent listen = s.getNextStartedActivityForResult().intent;
            assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, listen.getAction());
            assertEquals(MusicActivity.PROMPT, listen.getStringExtra(RecognizerIntent.EXTRA_PROMPT));
            s.receiveResult(listen, Activity.RESULT_OK, new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, new ArrayList<>(Arrays.asList("Rumours"))));
            assertEquals("the field shows what was heard", "Rumours", field(root(a)).getText().toString());
            Intent sent = sentToAmazon(s); assertNotNull("played at once", sent);
            assertEquals(MusicFakes.AMAZON, sent.getPackage());
            assertEquals(MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE, sent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS));
            assertEquals("Rumours", sent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM));
        }
    }

    @Test public void stopCancelsAndALateAnswerPlaysNothing() {
        speechService();
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            MusicActivity a = c.get(); Shadows.shadowOf(Looper.getMainLooper()).idle();
            Button mic = button(root(a), "Speak"); mic.performClick(); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue("listening inside the page", a.listeningForTest());
            button(root(a), "Stop").performClick(); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(a.listeningForTest());
            recogniser().triggerOnPartialResults(heard("Rum"));
            recogniser().triggerOnResults(heard("Rumours")); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("nothing heard after Stop is used", "", field(root(a)).getText().toString());
            assertNull("nothing plays", sentToAmazon(Shadows.shadowOf(a)));
            assertNull("and no voice screen opens instead", Shadows.shadowOf(a).getNextStartedActivityForResult());
        }
    }

    @Test public void leavingThePageWhileListeningCancelsIt() {
        speechService();
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            MusicActivity a = c.get(); Shadows.shadowOf(Looper.getMainLooper()).idle();
            button(root(a), "Speak").performClick(); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(a.listeningForTest());
            c.pause(); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse("the TV's Home (or anything else) over the page stops the microphone", a.listeningForTest());
            recogniser().triggerOnResults(heard("Rumours")); recogniser().triggerOnError(android.speech.SpeechRecognizer.ERROR_NO_MATCH);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull("nothing starts Amazon Music from the background", sentToAmazon(Shadows.shadowOf(a)));
            assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
        }
    }

    @Test public void noSpeechServiceHidesTheMicrophoneAndSaysWhy() {
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            assertNull("no microphone", button(root(c.get()), "Speak"));
            assertTrue(words(root(c.get())).contains(MusicActivity.NO_SPEECH));
            c.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEARCH));
            assertNull("the voice key starts nothing either", Shadows.shadowOf(c.get()).getNextStartedActivityForResult());
        }
    }

    @Test public void theRemotesVoiceKeyStartsListening() {
        speechScreen();
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            assertTrue(c.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEARCH)));
            ShadowActivity.IntentForResult r = Shadows.shadowOf(c.get()).getNextStartedActivityForResult();
            assertNotNull(r); assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, r.intent.getAction());
            c.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SEARCH));
            assertTrue(c.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST)));
            assertNotNull("the assistant key too", Shadows.shadowOf(c.get()).getNextStartedActivityForResult());
        }
    }

    @Test public void yourPlaylistsAppearWhenAmazonMusicShares() throws Exception {
        Map<String, List<MusicLibrary.Item>> tree = new HashMap<>();
        tree.put("root", Arrays.asList(new MusicLibrary.Item("lists", "Playlists", "", null, null, true, false), new MusicLibrary.Item("recent", "Recently played", "", null, null, true, false)));
        tree.put("lists", Arrays.asList(new MusicLibrary.Item("pl:1", "Friday Mix", "Playlist", null, null, false, true)));
        MusicLibraryActivity.library = (c, l) -> new Library(l, tree);
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            until(() -> words(root(c.get())).contains("Your playlists"));
            String page = words(root(c.get()));
            assertTrue(page, page.contains("Your playlists") && page.contains("Friday Mix"));
            assertFalse(page.contains("Recently played Friday"));
            button(root(c.get()), "Friday Mix").performClick();
            assertEquals(Arrays.asList("pl:1"), played);
            Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals(NowPlayingActivity.class.getName(), next.getComponent().getClassName());
        }
    }

    @Test public void recentlyPlayedAppearsWhenBrowsingIsRefusedAndPlaysByName() throws Exception {
        MusicHistory.remember(app(), new NowPlaying(MusicFakes.AMAZON, "Dreams", "Fleetwood Mac", "Rumours", 0, NowPlaying.STATE_PLAYING, 0, 0, 1f, 0), null);
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            until(() -> words(root(c.get())).contains("Recently played"));
            assertFalse(words(root(c.get())).contains("Your playlists"));
            button(root(c.get()), "Rumours").performClick();
            Intent sent = sentToAmazon(Shadows.shadowOf(c.get())); assertNotNull(sent);
            assertEquals("Rumours", sent.getStringExtra(android.app.SearchManager.QUERY));
            assertEquals("played as the album it was", MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE, sent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS));
        }
    }

    @Test public void theRowIsHiddenWhenThereIsNothingToShow() throws Exception {
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            until(() -> false);
            String page = words(root(c.get()));
            assertFalse(page, page.contains("Recently played") || page.contains("Your playlists"));
        }
    }

    @Test public void recentlyPlayedIsKeptPerProfileNewestFirst() {
        MusicHistory.Entry a = new MusicHistory.Entry("Rumours", AmazonMusic.Focus.ALBUM, "Fleetwood Mac", null), b = new MusicHistory.Entry("Adele", AmazonMusic.Focus.ARTIST, "", null);
        List<MusicHistory.Entry> l = MusicHistory.add(MusicHistory.add(new ArrayList<>(), a), b);
        assertEquals("Adele", l.get(0).name);
        assertEquals(2, MusicHistory.add(l, new MusicHistory.Entry("rumours", AmazonMusic.Focus.ALBUM, "", null)).size());
        List<MusicHistory.Entry> many = new ArrayList<>(); for (int i = 0; i < 20; i++) many = MusicHistory.add(many, new MusicHistory.Entry("A" + i, AmazonMusic.Focus.ARTIST, "", null));
        assertEquals(MusicHistory.MAX, many.size());
        assertNull("a track that names neither album nor artist", MusicHistory.of(new NowPlaying("p", "T", "", "", 0, 3, 0, 0, 1f, 0)));
        MusicHistory.remember(app(), new NowPlaying("p", "T", "Adele", "", 0, 3, 0, 0, 1f, 0), null);
        app().getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putString(Profiles.PREF, Profiles.CHILD).commit();
        assertTrue("Kids have their own list", MusicHistory.list(app()).isEmpty());
    }

    @Test public void nowPlayingRemembersWhatAmazonMusicPlayed() throws Exception {
        MusicFakes.Source source = new MusicFakes.Source(); MusicFakes.Session s = new MusicFakes.Session(); s.album = "Rumours"; s.artist = "Fleetwood Mac";
        source.sessions.add(s); MusicSessions.source = source;
        try (ActivityController<NowPlayingActivity> c = Robolectric.buildActivity(NowPlayingActivity.class).setup()) {
            until(() -> !MusicHistory.list(app()).isEmpty());
            assertEquals("Rumours", MusicHistory.list(app()).get(0).name);
            assertEquals(AmazonMusic.Focus.ALBUM, MusicHistory.list(app()).get(0).focus);
        }
    }
}
