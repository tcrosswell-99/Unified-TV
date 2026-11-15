package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javax.crypto.KeyGenerator;
import static org.junit.Assert.*;

/**
 * OD-27 voice and Echos: "… on <words>" is a place only when the words name an Echo Home Assistant found, so titles such
 * as "Carry On Wayward Son" play whole (in Kids too); and a place said by voice is for that request only (owner, 27
 * October 2026): the profile's Play on choice never changes.
 */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class VoiceEchoTest {
    private MusicSessions.Source sessions; private Echoes.Finder finder; private MusicFakes.Session amazon; private MiniHttp ha;
    private final List<MiniHttp.Request> calls = new CopyOnWriteArrayList<>();

    private static Context app() { return RuntimeEnvironment.getApplication(); }
    private static SharedPreferences prefs() { return app().getSharedPreferences(HouseholdSettings.PREFS, 0); }

    @Before public void setUp() throws Exception {
        prefs().edit().clear().commit(); AmazonMusic.rescan();
        sessions = MusicSessions.source; finder = Echoes.finder;
        MusicFakes.installAmazon(app(), true, false);
        MusicFakes.Source source = new MusicFakes.Source(); amazon = new MusicFakes.Session(MusicFakes.AMAZON, NowPlaying.STATE_PAUSED);
        amazon.actions = NowPlaying.ACTION_PLAY | NowPlaying.ACTION_PLAY_FROM_SEARCH; source.sessions.add(amazon); MusicSessions.source = source;
        Echoes.finder = c -> new Echoes.Found(null, new ArrayList<>(Arrays.asList(new Echoes.Echo("media_player.kitchen", "Kitchen", true, false))));
        ha = new MiniHttp(r -> { calls.add(r); return new MiniHttp.Response(200, "[]"); });
        Vault.testKey = KeyGenerator.getInstance("AES").generateKey();
        new Vault(app()).save("ha", new JSONObject().put("enabled", true).put("kind", "ha").put("base", ha.base()).put("httpConsent", true).put("token", "T"));
    }
    @After public void restore() { MusicSessions.source = sessions; Echoes.finder = finder; ha.close(); Vault.testKey = null; }

    private static void until(BooleanSupplier done) throws InterruptedException {
        for (int i = 0; i < 250 && !done.getAsBoolean(); i++) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20); }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private ActivityController<VoiceActivity> say(String words) throws InterruptedException {
        ActivityController<VoiceActivity> c = Robolectric.buildActivity(VoiceActivity.class).setup();
        c.get().askForTest(words);
        return c;
    }
    private JSONObject played() throws Exception {
        List<MiniHttp.Request> p = new ArrayList<>(); for (MiniHttp.Request r : calls) if (r.path.startsWith(Echoes.SERVICES)) p.add(r);
        assertEquals("one request", 1, p.size()); return new JSONObject(p.get(0).body);
    }

    @Test public void songTitlesWithOnPlayWhole() throws Exception {
        for (String title : new String[]{"carry on wayward son", "shine on you crazy diamond"}) {
            amazon.calls.clear();
            try (ActivityController<VoiceActivity> c = say("play " + title)) {
                until(() -> !amazon.calls.isEmpty());
                assertEquals("the whole title, on This TV", Arrays.asList("search:" + title), amazon.calls);
            }
        }
        assertTrue("no Echo was asked", calls.isEmpty());
    }

    @Test public void inKidsATitleWithOnPlaysNormally() throws Exception {
        prefs().edit().putString(Profiles.PREF, Profiles.CHILD).putString(KidsPolicy.APPS_PREF, MusicFakes.AMAZON).commit();
        try (ActivityController<VoiceActivity> c = say("play carry on wayward son")) {
            until(() -> !amazon.calls.isEmpty());
            assertEquals(Arrays.asList("search:carry on wayward son"), amazon.calls);
        }
    }

    @Test public void aNamedEchoPlaysThereOnceAndPlayOnIsUnchanged() throws Exception {
        try (ActivityController<VoiceActivity> c = say("play jazz on kitchen")) {
            until(() -> !calls.isEmpty() && Shadows.shadowOf(c.get()).peekNextStartedActivity() != null);
            JSONObject body = played();
            assertEquals("media_player.kitchen", body.getString("entity_id")); assertEquals("jazz", body.getString("media_content_id"));
            Intent shown = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals(NowPlayingActivity.class.getName(), shown.getComponent().getClassName());
            assertEquals("Now Playing shows the Kitchen this time", "media_player.kitchen", shown.getStringExtra(NowPlayingActivity.EXTRA_PLACE));
        }
        assertEquals("the profile still plays on This TV", Echoes.TV, Echoes.target(prefs()));
        assertTrue(amazon.calls.isEmpty());
    }

    @Test public void everywhereAndThisTvAreForThatRequestOnly() throws Exception {
        try (ActivityController<VoiceActivity> c = say("play jazz everywhere")) {
            until(() -> !calls.isEmpty());
            assertEquals("custom", played().getString("media_content_type"));
        }
        assertEquals(Echoes.TV, Echoes.target(prefs()));
        Echoes.choose(prefs(), "media_player.kitchen", "Kitchen");
        try (ActivityController<VoiceActivity> c = say("play Adele on this TV")) {
            until(() -> !amazon.calls.isEmpty());
            assertEquals(Arrays.asList("search:Adele"), amazon.calls);
            Intent shown = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals("Now Playing shows this TV this time", Echoes.TV, shown.getStringExtra(NowPlayingActivity.EXTRA_PLACE));
        }
        assertEquals("the profile still plays on the Kitchen", "media_player.kitchen", Echoes.target(prefs()));
    }
}
