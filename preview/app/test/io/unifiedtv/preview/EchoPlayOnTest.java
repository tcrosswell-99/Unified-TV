package io.unifiedtv.preview;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONArray;
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
import javax.crypto.KeyGenerator;
import static org.junit.Assert.*;

/**
 * OD-27 "Play on: This TV / each Echo / Everywhere", through the household's Home Assistant and Alexa Media Player, against
 * a fake Home Assistant (MiniHttp): which Echos are found, the exact service calls (one Echo, the Everywhere group, the
 * spoken-style fallback), never a separate play to each Echo, never a token in an address, the plain line when Home
 * Assistant isn't there, and the Kids rule.
 */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class EchoPlayOnTest {
    static final String TOKEN = "SECRET-TOKEN-123";
    private MiniHttp ha; private final List<MiniHttp.Request> seen = new CopyOnWriteArrayList<>();
    private String states = "[]", template = ""; private int templateStatus = 200, serviceStatus = 200;
    private Echoes.Finder finder;

    static String state(String id, String name, String state, JSONObject extra) throws Exception {
        JSONObject a = extra == null ? new JSONObject() : extra; a.put("friendly_name", name);
        return new JSONObject().put("entity_id", id).put("state", state).put("attributes", a).toString();
    }
    static String house(boolean everywhere) throws Exception {
        List<String> s = new ArrayList<>(Arrays.asList(
            state("media_player.kitchen_echo", "Kitchen Echo", "idle", new JSONObject().put("last_called", false).put("available", true)),
            state("media_player.lounge", "Lounge", "playing", new JSONObject().put("last_called_timestamp", 1).put("available", true)),
            state("media_player.living_room_tv", "Living Room TV", "off", null),            // not an Echo
            state("binary_sensor.front_door_ding", "Front Door Ding", "off", null)));
        if (everywhere) s.add(state("media_player.everywhere", "Everywhere", "idle", new JSONObject().put("available", true)));
        return "[" + String.join(",", s) + "]";
    }
    private JSONObject config() throws Exception { return new JSONObject().put("enabled", true).put("kind", "ha").put("base", ha.base()).put("httpConsent", true).put("token", TOKEN); }
    private static Context app() { return RuntimeEnvironment.getApplication(); }
    private static SharedPreferences prefs() { return app().getSharedPreferences(HouseholdSettings.PREFS, 0); }
    private List<MiniHttp.Request> plays() { List<MiniHttp.Request> out = new ArrayList<>(); for (MiniHttp.Request r : seen) if (r.path.startsWith(Echoes.SERVICES)) out.add(r); return out; }

    @Before public void start() throws Exception {
        prefs().edit().clear().commit(); AmazonMusic.rescan(); finder = Echoes.finder;
        ha = new MiniHttp(r -> {
            seen.add(r);
            if (r.path.equals("/api/states")) return new MiniHttp.Response(200, states);
            if (r.path.equals("/api/template")) return new MiniHttp.Response(templateStatus, template);
            if (r.path.startsWith(Echoes.SERVICES)) return new MiniHttp.Response(serviceStatus, "[]");
            if (r.path.startsWith("/api/media_player_proxy/")) return new MiniHttp.Response(200, "picture");
            return new MiniHttp.Response(404, "");
        });
    }
    @After public void stop() { ha.close(); Echoes.finder = finder; Vault.testKey = null; }

    @Test public void echoesAreFoundByAlexaMediaPlayersOwnSigns() throws Exception {
        states = house(true); templateStatus = 403;   // a token without the template right: attributes decide
        Echoes.Found f = Echoes.find(config());
        assertNull(f.problem);
        List<String> names = new ArrayList<>(); for (Echoes.Echo e : f.echoes) names.add(e.name);
        assertEquals("by friendly name; the TV and the doorbell are not Echos", Arrays.asList("Everywhere", "Kitchen Echo", "Lounge"), names);
        assertEquals("media_player.everywhere", Echoes.group(f.echoes).id);
        assertEquals("Lounge", Echoes.named(f.echoes, "the lounge").name);
        assertEquals("Kitchen Echo", Echoes.named(f.echoes, "kitchen").name);
        assertNull(Echoes.named(f.echoes, "bathroom"));
    }

    @Test public void homeAssistantsOwnListWinsWhenItAnswers() throws Exception {
        states = house(false); template = "media_player.lounge, media_player.living_room_tv,not an id";
        Echoes.Found f = Echoes.find(config());
        List<String> ids = new ArrayList<>(); for (Echoes.Echo e : f.echoes) ids.add(e.id);
        assertEquals(Arrays.asList("media_player.living_room_tv", "media_player.lounge"), ids);
        JSONObject sent = new JSONObject(seen.stream().filter(r -> r.path.equals("/api/template")).findFirst().get().body);
        assertTrue(sent.getString("template").contains("integration_entities('alexa_media')"));
    }

    @Test public void noAlexaMediaPlayerOrNoHomeAssistantSaysSoPlainly() throws Exception {
        states = "[" + state("media_player.living_room_tv", "Living Room TV", "off", null) + "]";
        assertEquals(Echoes.NOT_SET_UP, Echoes.find(config()).problem);
        assertEquals(Echoes.NOT_CONNECTED, Echoes.find(new JSONObject()).problem);
        assertEquals(Echoes.NOT_CONNECTED, Echoes.find(new JSONObject().put("enabled", true).put("base", "http://127.0.0.1:1").put("httpConsent", true)).problem);
    }

    @Test public void oneEchoGetsExactlyTheDocumentedPlayMedia() throws Exception {
        states = house(true);
        assertNull(Echoes.play(app(), config(), "media_player.kitchen_echo", "Taylor Swift"));
        List<MiniHttp.Request> p = plays(); assertEquals(1, p.size());
        assertEquals("/api/services/media_player/play_media", p.get(0).path);
        JSONObject body = new JSONObject(p.get(0).body);
        assertEquals("media_player.kitchen_echo", body.getString("entity_id"));
        assertEquals("AMAZON_MUSIC", body.getString("media_content_type"));
        assertEquals("Taylor Swift", body.getString("media_content_id"));
        assertEquals(3, body.length());
    }

    @Test public void everywhereUsesTheGroupWhenThereIsOne() throws Exception {
        states = house(true);
        assertNull(Echoes.play(app(), config(), Echoes.EVERYWHERE, "jazz"));
        List<MiniHttp.Request> p = plays(); assertEquals("one request, never one per Echo", 1, p.size());
        JSONObject body = new JSONObject(p.get(0).body);
        assertEquals("media_player.everywhere", body.getString("entity_id"));
        assertEquals("AMAZON_MUSIC", body.getString("media_content_type"));
        assertEquals("jazz", body.getString("media_content_id"));
    }

    @Test public void everywhereWithoutAGroupIsOneSpokenStyleRequest() throws Exception {
        states = house(false);
        assertNull(Echoes.play(app(), config(), Echoes.EVERYWHERE, "jazz"));
        List<MiniHttp.Request> p = plays(); assertEquals("one request, never a separate play to each Echo", 1, p.size());
        JSONObject body = new JSONObject(p.get(0).body);
        assertEquals("custom", body.getString("media_content_type"));
        assertEquals("play jazz everywhere", body.getString("media_content_id"));
        assertTrue(body.getString("entity_id").equals("media_player.kitchen_echo") || body.getString("entity_id").equals("media_player.lounge"));
    }

    @Test public void theTokenIsOnlyEverInTheHeader() throws Exception {
        states = house(true); Vault.testKey = KeyGenerator.getInstance("AES").generateKey();
        new Vault(app()).save("ha", config());
        Echoes.play(app(), config(), "media_player.lounge", "Adele");
        MusicArt.fetcher.fetch(app(), "ha:/api/media_player_proxy/media_player.lounge?token=" + TOKEN + "&cache=7");
        assertFalse(seen.isEmpty());
        for (MiniHttp.Request r : seen) {
            String address = r.path + (r.query == null ? "" : "?" + r.query);
            assertFalse("no token in " + address, address.contains(TOKEN) || address.contains("token="));
            assertEquals("Bearer " + TOKEN, r.header("authorization"));
        }
        MiniHttp.Request picture = seen.get(seen.size() - 1);
        assertEquals("/api/media_player_proxy/media_player.lounge", picture.path); assertEquals("cache=7", picture.query);
    }

    @Test public void aRefusalIsSaidPlainlyAndKeptForDiagnostics() throws Exception {
        states = house(true); serviceStatus = 500;
        assertEquals(Echoes.REFUSED, Echoes.play(app(), config(), "media_player.lounge", "Adele"));
        assertTrue(Echoes.diagnosis(app()), Echoes.diagnosis(app()).contains(Echoes.REFUSED));
        assertFalse(Echoes.diagnosis(app()).contains(TOKEN));
        assertEquals(Echoes.NOT_CONNECTED, Echoes.play(app(), new JSONObject(), "media_player.lounge", "Adele"));
    }

    @Test public void kidsHaveNoEchoUnlessAnAdultTurnsItOn() throws Exception {
        states = house(true);
        prefs().edit().putString(Profiles.PREF, Profiles.CHILD).commit();
        Echoes.choose(prefs(), "media_player.lounge", "Lounge");
        assertFalse(Echoes.allowed(prefs()));
        assertEquals("Kids play on This TV", Echoes.TV, Echoes.target(prefs()));
        assertEquals(Echoes.KIDS, Echoes.play(app(), config(), "media_player.lounge", "Frozen"));
        assertTrue("nothing was asked of Home Assistant", seen.isEmpty());
        prefs().edit().putBoolean(Echoes.KIDS_PREF, true).commit();
        assertTrue(Echoes.allowed(prefs()));
        assertEquals("media_player.lounge", Echoes.target(prefs()));
        assertNull(Echoes.play(app(), config(), "media_player.lounge", "Frozen"));
    }

    @Test public void playOnIsRememberedPerProfile() {
        Echoes.choose(prefs(), Echoes.EVERYWHERE, "Everywhere");
        assertEquals("Everywhere", Echoes.targetName(prefs()));
        prefs().edit().putString(Profiles.PREF, Profiles.CHILD).putBoolean(Echoes.KIDS_PREF, true).commit();
        assertEquals("Kids have their own choice", "This TV", Echoes.targetName(prefs()));
    }

    private static String words(View v) {
        StringBuilder b = new StringBuilder(); if (v instanceof TextView && v.getVisibility() == View.VISIBLE) b.append(((TextView) v).getText()).append(' ');
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }
    private static String musicPage() throws Exception {
        MusicFakes.installAmazon(app(), true, false);
        try (ActivityController<MusicActivity> c = Robolectric.buildActivity(MusicActivity.class).setup()) {
            for (int i = 0; i < 100; i++) { Shadows.shadowOf(Looper.getMainLooper()).idle(); String w = words(c.get().getWindow().getDecorView()); if (w.contains("Play on") || w.contains(Echoes.SETUP_LINE) || w.contains(Echoes.KIDS)) return w; Thread.sleep(20); }
            return words(c.get().getWindow().getDecorView());
        }
    }

    @Test public void withoutHomeAssistantTheMusicPageShowsOnePlainLine() throws Exception {
        Echoes.finder = c -> new Echoes.Found(Echoes.NOT_CONNECTED, new ArrayList<>());
        String page = musicPage();
        assertTrue(page, page.contains(Echoes.SETUP_LINE));
        assertFalse("no Echo options", page.contains("Play on:"));
    }

    @Test public void withEchoesTheMusicPageOffersPlayOn() throws Exception {
        Echoes.finder = c -> new Echoes.Found(null, new ArrayList<>(Arrays.asList(new Echoes.Echo("media_player.lounge", "Lounge", true, false))));
        String page = musicPage();
        assertTrue(page, page.contains("Play on: This TV"));
        prefs().edit().putString(Profiles.PREF, Profiles.CHILD).commit();
        assertTrue("Kids: the Kids line, no Echo choice", musicPage().contains(Echoes.KIDS));
    }

    @Test public void anEchosStateReadsAsNowPlaying() throws Exception {
        long wall = 1_800_000_000_000L, elapsed = 5_000_000L;
        JSONObject s = new JSONObject().put("state", "playing").put("attributes", new JSONObject().put("media_title", "Hello").put("media_artist", "Adele")
            .put("media_duration", 295.0).put("media_position", 60.0).put("media_position_updated_at", java.time.Instant.ofEpochMilli(wall - 10_000).toString())
            .put("supported_features", EchoSession.HA_PAUSE | EchoSession.HA_NEXT | EchoSession.HA_PLAY));
        NowPlaying n = EchoSession.parse("echo:media_player.lounge", s, wall, elapsed);
        assertTrue(n.playing()); assertEquals("Hello", n.title); assertEquals("Adele", n.artist); assertEquals(295_000, n.durationMs);
        assertEquals("ten seconds since Home Assistant's report", 70_000, n.position(elapsed));
        assertTrue(n.offers(NowPlaying.ACTION_SKIP_TO_NEXT)); assertFalse(n.offers(NowPlaying.ACTION_SKIP_TO_PREVIOUS));
        assertFalse(NowPlaying.alive(EchoSession.parse("p", new JSONObject().put("state", "unavailable"), wall, elapsed).state));
        try { new EchoSession(app(), new JSONObject(), "media_player.x/../../api", "x"); fail("only a media player entity"); } catch (IllegalArgumentException expected) { }
        assertTrue(new JSONArray(house(true)).length() > 0);
    }
}
