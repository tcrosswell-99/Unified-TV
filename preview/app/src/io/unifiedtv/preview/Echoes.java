package io.unifiedtv.preview;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Play on: This TV / each Echo / Everywhere" (OD-27, owner, 27 October 2026). Amazon Music on the household's Echo
 * speakers, through the household's own Home Assistant and the community Alexa Media Player integration
 * (alandtse/alexa_media_player, installed by the owner through HACS, signed in to Amazon inside Home Assistant). Unified
 * TV never sees or handles an Amazon sign-in: it reads Home Assistant's states and calls its standard media_player
 * services through LocalApi (the saved "ha" record, its token in a header, the home-network route past NordVPN).
 *
 * <p>UNVERIFIED (not tested on the household's Home Assistant): that the integration lists Alexa's automatic
 * "Everywhere" group as an entity. Without one, "Everywhere" is one spoken-style request ("play … everywhere") to one
 * Echo, as the integration's wiki documents; never a separate play to each Echo, which would not be in sync.
 *
 * <p>Echo speakers are for Adults unless an adult turns them on for Kids. All network calls are off the main thread.
 */
final class Echoes {
    private Echoes() {}
    static final String TV = "tv", EVERYWHERE = "everywhere";
    static final String PLAY_ON_PREF = "music.playOn.", PLAY_ON_NAME_PREF = "music.playOnName.", KIDS_PREF = "music.echoKids",
            ERROR_PREF = "music.echoError", ERROR_AT_PREF = "music.echoErrorAt";
    static final String NOT_CONNECTED = "Home Assistant isn't connected", NOT_SET_UP = "Alexa Media Player isn't set up in Home Assistant",
            REFUSED = "The Echo didn't accept that request", MISSING = "That Echo isn't available right now",
            KIDS = "Echo speakers are for Adults. Ask an adult to turn them on for Kids.",
            SETUP_LINE = "To play on your Echo speakers, add Alexa Media Player to Home Assistant";
    static final String SERVICES = "/api/services/media_player/", AMAZON_MUSIC = "AMAZON_MUSIC", CUSTOM = "custom";
    static final String ENTITY = "media_player\\.[a-z0-9_]+";
    /** Home Assistant's own list of the integration's entities (template API); heuristics below when it can't answer. */
    static final String TEMPLATE = "{{ integration_entities('alexa_media') | select('match','media_player') | join(',') }}";
    /** Attributes only Alexa Media Player's media players carry. */
    static final Set<String> ALEXA_ATTRIBUTES = new HashSet<>(Arrays.asList("last_called", "last_called_timestamp", "last_called_summary", "connected_bluetooth", "bluetooth_list"));
    static final int MAX_ECHOES = 40;

    /** One Echo, or a speaker group the integration lists. */
    static final class Echo {
        final String id, name; final boolean available, group, playing;
        Echo(String id, String name, boolean available, boolean group) { this(id, name, available, group, false); }
        Echo(String id, String name, boolean available, boolean group, boolean playing) { this.id = id; this.name = name; this.available = available; this.group = group; this.playing = playing; }
    }
    /** What a look at Home Assistant found: a plain problem, or the Echos. */
    static final class Found {
        final String problem; final List<Echo> echoes;
        Found(String problem, List<Echo> echoes) { this.problem = problem; this.echoes = echoes; }
        boolean ok() { return problem == null && !echoes.isEmpty(); }
    }
    /** One Home Assistant service call: never more than one per request to play. */
    static final class Plan {
        final String service; final JSONObject body; final String name;
        Plan(String service, JSONObject body, String name) { this.service = service; this.body = body; this.name = name; }
    }

    /** Finds the Echos; tests replace it. Network: never on the main thread. */
    interface Finder { Found find(Context c); }
    static final Finder PLATFORM = c -> find(ha(c));
    static volatile Finder finder = PLATFORM;
    static JSONObject ha(Context c) { try { return new Vault(c).read("ha"); } catch (Exception e) { return new JSONObject(); } }

    // --- discovery

    static Found find(JSONObject ha) {
        if (HaStatus.before(ha) != null) return new Found(NOT_CONNECTED, new ArrayList<>());
        String states;
        try { states = new String(LocalApi.bytes(ha, "/api/states"), StandardCharsets.UTF_8); }
        catch (Exception e) { return new Found(NOT_CONNECTED, new ArrayList<>()); }
        Set<String> listed = null;
        try { listed = template(LocalApi.postText(ha, "/api/template", new JSONObject().put("template", TEMPLATE))); }
        catch (Exception ignored) { /* an older Home Assistant, or a token without that right: attributes decide */ }
        List<Echo> found = parse(states, listed);
        if (found == null) return new Found(NOT_CONNECTED, new ArrayList<>());
        return found.isEmpty() ? new Found(NOT_SET_UP, found) : new Found(null, found);
    }

    /** Pure: the template's answer as entity ids; empty when it listed none. */
    static Set<String> template(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        for (String s : text.split(",")) { String id = s.trim(); if (id.matches(ENTITY)) out.add(id); }
        return out;
    }

    /**
     * Pure: Alexa Media Player's media players among Home Assistant's states, by friendly name. {@code listed}: the
     * integration's own entities when Home Assistant said (non-empty); otherwise its own attributes, or "Echo", "Alexa" or
     * "Everywhere" in the id or name. Null when the states can't be read.
     */
    static List<Echo> parse(String json, Set<String> listed) {
        if (json == null || json.length() > LocalApi.LIMIT) return null;
        JSONArray states; try { states = new JSONArray(json); } catch (JSONException e) { return null; }
        List<Echo> out = new ArrayList<>();
        boolean byList = listed != null && !listed.isEmpty();
        for (int i = 0; i < states.length() && out.size() < MAX_ECHOES; i++) {
            JSONObject s = states.optJSONObject(i); if (s == null) continue;
            String id = s.optString("entity_id"); if (!id.matches(ENTITY)) continue;
            JSONObject attr = s.optJSONObject("attributes"); if (attr == null) attr = new JSONObject();
            String name = NowPlaying.bounded(attr.optString("friendly_name"), 60);
            if (name.isEmpty()) name = HaEntities.pretty(id.substring(id.indexOf('.') + 1));
            boolean alexa = byList ? listed.contains(id) : alexa(id, name, attr);
            if (!alexa) continue;
            boolean available = !"unavailable".equals(s.optString("state")) && attr.optBoolean("available", true);
            out.add(new Echo(id, name, available, everywhere(id, name), "playing".equals(s.optString("state"))));
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }
    static boolean alexa(String id, String name, JSONObject attr) {
        for (String a : ALEXA_ATTRIBUTES) if (attr.has(a)) return true;
        List<String> words = HaEntities.tokens(id.substring(id.indexOf('.') + 1) + " " + name);
        return words.contains("echo") || words.contains("alexa") || everywhere(id, name);
    }
    /** Alexa's automatic multi-room group, by its name (UNVERIFIED that the integration lists it). */
    static boolean everywhere(String id, String name) {
        return id.equals("media_player.everywhere") || HaEntities.tokens(name).contains("everywhere");
    }
    /** Which Echo Now Playing shows for "Everywhere": the group, else one that is playing, else the first that answers. */
    static Echo shown(List<Echo> echoes) {
        Echo g = group(echoes); if (g != null) return g;
        for (Echo e : echoes) if (e.playing) return e;
        for (Echo e : echoes) if (e.available) return e;
        return null;
    }
    static Echo group(List<Echo> echoes) { for (Echo e : echoes) if (e.group && e.available) return e; return null; }
    static Echo byId(List<Echo> echoes, String id) { for (Echo e : echoes) if (e.id.equals(id)) return e; return null; }
    /** The Echo a spoken name means: the same name, or the one whose name holds every spoken word; null when not one. */
    static Echo named(List<Echo> echoes, String spoken) {
        List<String> want = new ArrayList<>(HaEntities.tokens(spoken)); want.remove("the"); want.remove("my");
        if (want.isEmpty()) return null;
        Echo only = null; int matches = 0;
        for (Echo e : echoes) {
            List<String> have = HaEntities.tokens(e.name);
            if (have.equals(want)) return e;
            if (have.containsAll(want)) { only = e; matches++; }
        }
        return matches == 1 ? only : null;
    }

    // --- requests (pure: exactly one service call)

    static Plan plan(String target, String query, List<Echo> echoes) throws JSONException {
        if (EVERYWHERE.equals(target)) {
            Echo g = group(echoes);
            if (g != null) return playMedia(g, AMAZON_MUSIC, query);
            // No group entity: one spoken-style request to one Echo, never a play to each.
            for (Echo e : echoes) if (e.available && !e.group) return playMedia(e, CUSTOM, "play " + query + " everywhere");
            return null;
        }
        Echo e = byId(echoes, target);
        return e == null || !e.available ? null : playMedia(e, AMAZON_MUSIC, query);
    }
    static Plan playMedia(Echo e, String type, String content) throws JSONException {
        return new Plan("play_media", new JSONObject().put("entity_id", e.id).put("media_content_type", type).put("media_content_id", content), e.name);
    }
    /** A standard media_player service on one entity (pause, next, volume…). */
    static Plan control(String entity, String service, JSONObject extra) throws JSONException {
        JSONObject body = extra == null ? new JSONObject() : new JSONObject(extra.toString());
        return new Plan(service, body.put("entity_id", entity), entity);
    }
    static void send(JSONObject ha, Plan p) throws Exception {
        if (!p.service.matches("[a-z_]+") || !p.body.optString("entity_id").matches(ENTITY)) throw new IllegalArgumentException();
        LocalApi.post(ha, SERVICES + p.service, p.body);
    }

    /**
     * Plays {@code query} on {@code target} (an Echo's entity or EVERYWHERE): null when Home Assistant accepted it, else
     * the plain problem (also kept for Owner diagnostics, without any address or token). Network: off the main thread.
     */
    static String play(Context c, JSONObject ha, String target, String query) { return play(c, ha, target, query, null); }
    /** As above, with a look at Home Assistant already made ({@code found}), or null to look now. */
    static String play(Context c, JSONObject ha, String target, String query, Found found) {
        SharedPreferences p = c.getSharedPreferences(HouseholdSettings.PREFS, 0);
        if (!allowed(p)) return KIDS;
        String q = AmazonMusic.clean(query);
        if (q.isEmpty()) return "Type or say what you'd like to hear.";
        Found f = found != null ? found : finder == PLATFORM ? find(ha) : finder.find(c);
        if (f.problem != null) return problem(p, f.problem);
        Plan plan;
        try { plan = plan(target, q, f.echoes); } catch (JSONException e) { return problem(p, REFUSED); }
        if (plan == null) return problem(p, MISSING);
        try { send(ha, plan); }
        catch (LocalApi.ApiError e) { return problem(p, e.code == 401 || e.code == 403 ? NOT_CONNECTED : REFUSED); }
        catch (Exception e) { return problem(p, NOT_CONNECTED); }
        AmazonMusic.remember(c, q);
        p.edit().remove(ERROR_PREF).apply();
        return null;
    }
    static String problem(SharedPreferences p, String words) {
        p.edit().putString(ERROR_PREF, words).putLong(ERROR_AT_PREF, System.currentTimeMillis()).apply();
        return words;
    }

    // --- the household's choice, per profile

    /** Echo targets: Adults, or Kids when an adult turned them on. */
    static boolean allowed(SharedPreferences p) { return !Profiles.kids(p) || p.getBoolean(KIDS_PREF, false); }
    /** This profile's "Play on": TV, EVERYWHERE or an Echo's entity; This TV where Echos aren't allowed. */
    static String target(SharedPreferences p) {
        String t = p.getString(PLAY_ON_PREF + Profiles.current(p), TV);
        return !allowed(p) || !(TV.equals(t) || EVERYWHERE.equals(t) || t.matches(ENTITY)) ? TV : t;
    }
    static String targetName(SharedPreferences p) {
        String t = target(p);
        if (TV.equals(t)) return "This TV";
        if (EVERYWHERE.equals(t)) return "Everywhere";
        String n = p.getString(PLAY_ON_NAME_PREF + Profiles.current(p), "");
        return n.isEmpty() ? HaEntities.pretty(t.substring(t.indexOf('.') + 1)) : n;
    }
    static void choose(SharedPreferences p, String target, String name) {
        String profile = Profiles.current(p);
        p.edit().putString(PLAY_ON_PREF + profile, target).putString(PLAY_ON_NAME_PREF + profile, name == null ? "" : name).apply();
    }
    static boolean onEcho(SharedPreferences p) { return !TV.equals(target(p)); }

    static String diagnosis(Context c) {
        SharedPreferences p = c.getSharedPreferences(HouseholdSettings.PREFS, 0);
        String error = p.getString(ERROR_PREF, "");
        return "Play on: " + targetName(p) + "\nEcho speakers in Kids: " + (p.getBoolean(KIDS_PREF, false) ? "allowed" : "Adults only")
                + "\nLast Echo problem: " + (error.isEmpty() ? "none" : error + ", " + OwnerChecks.when(c, p.getLong(ERROR_AT_PREF, 0)));
    }
}
