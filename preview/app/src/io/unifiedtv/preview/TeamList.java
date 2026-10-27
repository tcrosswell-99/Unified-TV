package io.unifiedtv.preview;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The UK team list for the football picker, from TheSportsDB (owner decision OD-22: no account or key): built in the
 * background when the picker first opens (one builder for the whole app), kept for a week, and searched with a few
 * letters on the TV itself (what is typed never leaves the TV unless an adult asks TheSportsDB by name). The building is
 * FixtureFeed.build (paced, resumable); this shares it, completes it and searches it.
 *
 * TheSportsDB's free key lists only about 10 teams a league (owner's TCL, build 93), so the list is completed twice
 * over: from one round of each league's fixtures (every club plays in it), and, for the Premier League, Championship
 * and League One, from Unified TV's own list (res/raw/football_teams.json: names, other names and kit colours, marked
 * with its season), which is there at once, before anything is read. TheSportsDB's names and numbers win; a bundled
 * club TheSportsDB places in another league moves there; a bundled club it doesn't list stays, found by name later.
 */
final class TeamList {
    private TeamList() {}
    interface Listener { void changed(JSONObject catalogue, String problem); }
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static Thread worker;
    private static volatile JSONObject bundle;

    static void listen(Listener l) { LISTENERS.add(l); }
    static void unlisten(Listener l) { LISTENERS.remove(l); }
    static synchronized boolean building() { return worker != null && worker.isAlive(); }

    /** Starts (or continues) building the list unless it is complete and fresh, or already being built. */
    static synchronized void ensure(Context context) {
        if (building()) return;
        Context app = context.getApplicationContext();
        JSONObject now = FixtureFeed.catalogue(app);
        if (now.optLong("fetchedAt") > 0 && !SportsDb.catalogueDue(now.optLong("fetchedAt"), System.currentTimeMillis())) return;
        worker = new Thread(() -> FixtureFeed.build(app, (cat, problem) -> { for (Listener l : LISTENERS) try { l.changed(cat, problem); } catch (RuntimeException ignored) {} }), "team-list");
        worker.setDaemon(true); worker.start();
    }

    /** The bundled list (res/raw/football_teams.json), read once; an empty object if it can't be read. */
    static JSONObject bundle(Context c) {
        JSONObject b = bundle;
        if (b != null) return b;
        try (InputStream in = c.getResources().openRawResource(R.raw.football_teams); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            b = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) { b = new JSONObject(); }
        bundle = b; return b;
    }
    /** The season the bundled list was written for ("2025-26"), or "". */
    static String bundleSeason(JSONObject bundle) { return bundle == null ? "" : bundle.optString("season"); }

    /**
     * One team, with the league it was read from: its other name ("Spurs") and other names, its API-Football number when
     * TheSportsDB knows it (0 otherwise), its own two colours for the drawn badge (0 when unknown), and its league's
     * TheSportsDB number (0 when unknown). id is 0 for a bundled club whose TheSportsDB number isn't known yet.
     */
    static final class Entry {
        final long id, api, sdbLeague; final String name, code, also, league, country; final int c1, c2; final List<String> aliases;
        Entry(long id, String name, String code, String also, String league, String country, long api, int c1, int c2) { this(id, name, code, also, league, country, api, c1, c2, null, 0); }
        Entry(long id, String name, String code, String also, String league, String country, long api, int c1, int c2, List<String> aliases, long sdbLeague) {
            this.id = id; this.name = name; this.code = code; this.also = also == null ? "" : also; this.league = league; this.country = country; this.api = api; this.c1 = c1; this.c2 = c2;
            this.aliases = aliases == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(aliases)); this.sdbLeague = sdbLeague;
        }
        /** Its names for matching: the name, then its other names, then TheSportsDB's "also" names, each once. */
        List<String> names() {
            List<String> out = new ArrayList<>(); out.add(name); out.addAll(aliases);
            for (String a : also.split("[,;/]")) if (!a.trim().isEmpty()) out.add(a.trim());
            List<String> unique = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (String n : out) if (!key(n).isEmpty() && seen.add(key(n))) unique.add(n);
            return unique;
        }
    }

    /** The leagues with their teams read, in the catalogue's order (each country's own order). */
    static List<JSONObject> leagues(JSONObject catalogue) {
        List<JSONObject> out = new ArrayList<>(); JSONArray all = catalogue == null ? null : catalogue.optJSONArray("leagues");
        if (all != null) for (int i = 0; i < all.length(); i++) { JSONObject l = all.optJSONObject(i); if (l != null && l.optJSONArray("teams") != null && l.optJSONArray("teams").length() > 0) out.add(l); }
        return out;
    }
    static List<Entry> teams(JSONObject league) {
        List<Entry> out = new ArrayList<>(); JSONArray t = league == null ? null : league.optJSONArray("teams");
        if (t != null) for (int i = 0; i < t.length(); i++) { JSONObject x = t.optJSONObject(i); if (x != null && (x.optLong("id") > 0 || !x.optString("name").trim().isEmpty()))
            out.add(new Entry(Math.max(0, x.optLong("id")), x.optString("name"), x.isNull("code") ? "" : x.optString("code"), x.optString("also"), league.optString("name"), league.optString("country"),
                x.optLong("api"), x.optInt("c1"), x.optInt("c2"), strings(x.optJSONArray("aliases")), league.optLong("sdb"))); }
        return out;
    }
    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>(); if (a != null) for (int i = 0; i < a.length(); i++) if (!a.optString(i).trim().isEmpty()) out.add(a.optString(i).trim());
        return out;
    }

    /**
     * The catalogue completed (pure; neither argument is changed): each league's teams from TheSportsDB's list, then the
     * clubs seen in its round ("more"), then, for the bundled leagues, Unified TV's own clubs. Matched by name (key):
     * TheSportsDB's name, number and colours win, and the bundled other names and colours fill gaps. A bundled club that
     * TheSportsDB lists in another league is left out of this one. Bundled leagues are shown even before anything is
     * read. Teams are in alphabetical order.
     */
    static JSONObject merged(JSONObject catalogue, JSONObject bundle) {
        try {
            Map<Integer, JSONObject> read = new HashMap<>(); JSONArray all = catalogue == null ? null : catalogue.optJSONArray("leagues");
            if (all != null) for (int i = 0; i < all.length(); i++) { JSONObject l = all.optJSONObject(i); if (l != null) read.put(l.optInt("id"), l); }
            Map<Integer, JSONObject> own = new HashMap<>(); JSONArray b = bundle == null ? null : bundle.optJSONArray("leagues");
            if (b != null) for (int i = 0; i < b.length(); i++) { JSONObject l = b.optJSONObject(i); if (l != null && SportsDb.league(l.optInt("id")) != null) own.put(l.optInt("id"), l); }
            // Where TheSportsDB places each club (by name), so a bundled club that changed division is shown once, there.
            Map<String, Integer> placed = new HashMap<>();
            for (JSONObject l : read.values()) for (String list : new String[]{"teams", "more"}) { JSONArray t = l.optJSONArray(list); if (t != null) for (int i = 0; i < t.length(); i++) {
                JSONObject x = t.optJSONObject(i); if (x == null) continue; placed.putIfAbsent(key(x.optString("name")), l.optInt("id"));
                for (String a : x.optString("also").split("[,;/]")) if (!key(a).isEmpty()) placed.putIfAbsent(key(a), l.optInt("id")); } }
            JSONArray leagues = new JSONArray();
            for (SportsDb.League def : SportsDb.LEAGUES) {
                JSONObject l = read.get(def.id), mine = own.get(def.id);
                if (l == null && mine == null) continue;
                long sdb = l != null && l.optLong("sdb") > 0 ? l.optLong("sdb") : mine != null && mine.optLong("sdb") > 0 ? mine.optLong("sdb") : def.sdb;
                JSONObject out = new JSONObject().put("id", def.id).put("name", def.name).put("country", def.country); if (sdb > 0) out.put("sdb", sdb);
                Map<String, JSONObject> byKey = new LinkedHashMap<>(); Set<Long> ids = new HashSet<>();
                JSONArray t = l == null ? null : l.optJSONArray("teams");
                if (t != null) for (int i = 0; i < t.length(); i++) { JSONObject x = t.optJSONObject(i); if (x == null || x.optLong("id") <= 0 || !ids.add(x.optLong("id"))) continue;
                    JSONObject c = new JSONObject(x.toString()); byKey.put(key(c.optString("name")), c); }
                JSONArray more = l == null ? null : l.optJSONArray("more");
                if (more != null) for (int i = 0; i < more.length(); i++) { JSONObject x = more.optJSONObject(i); if (x == null || x.optLong("id") <= 0 || !ids.add(x.optLong("id")) || x.optString("name").trim().isEmpty()) continue;
                    if (find(byKey, Collections.singletonList(x.optString("name"))) != null) continue;
                    byKey.put(key(x.optString("name")), new JSONObject().put("id", x.optLong("id")).put("name", x.optString("name"))); }
                JSONArray bundled = mine == null ? null : mine.optJSONArray("teams");
                if (bundled != null) for (int i = 0; i < bundled.length(); i++) {
                    JSONObject x = bundled.optJSONObject(i); if (x == null || x.optString("name").trim().isEmpty()) continue;
                    List<String> names = new ArrayList<>(); names.add(x.optString("name")); names.addAll(strings(x.optJSONArray("aliases")));
                    JSONObject match = find(byKey, names);
                    if (match == null) {
                        Integer elsewhere = null; for (String n : names) { Integer at = placed.get(key(n)); if (at != null) { elsewhere = at; break; } }
                        if (elsewhere != null && elsewhere != def.id) continue;   // TheSportsDB has it in another league now
                        match = new JSONObject().put("id", 0).put("name", x.optString("name")); byKey.put(key(x.optString("name")), match);
                    }
                    JSONArray aliases = new JSONArray(); Set<String> seen = new HashSet<>(); seen.add(key(match.optString("name")));
                    for (String n : names) if (!key(n).isEmpty() && seen.add(key(n))) aliases.put(n.trim());
                    if (aliases.length() > 0) match.put("aliases", aliases);
                    if (match.optString("code").isEmpty() && x.optString("short").matches("[A-Za-z0-9]{2,5}")) match.put("code", x.optString("short"));
                    if (match.optInt("c1") == 0 && SportsDb.colour(x.optString("c1")) != 0) { match.put("c1", SportsDb.colour(x.optString("c1"))); if (SportsDb.colour(x.optString("c2")) != 0) match.put("c2", SportsDb.colour(x.optString("c2"))); }
                }
                List<JSONObject> sorted = new ArrayList<>(byKey.values()); sorted.sort(Comparator.comparing(x -> fold(x.optString("name"))));
                out.put("teams", new JSONArray(sorted));
                leagues.put(out);
            }
            JSONObject result = new JSONObject().put("leagues", leagues);
            if (catalogue != null && catalogue.optLong("fetchedAt") > 0) result.put("fetchedAt", catalogue.optLong("fetchedAt"));
            return result;
        } catch (Exception e) { return catalogue == null ? new JSONObject() : catalogue; }
    }
    private static JSONObject find(Map<String, JSONObject> byKey, List<String> names) {
        for (String n : names) { JSONObject m = byKey.get(key(n)); if (m != null) return m; }
        for (JSONObject x : byKey.values()) for (String a : x.optString("also").split("[,;/]")) for (String n : names) if (!key(a).isEmpty() && key(a).equals(key(n))) return x;
        return null;
    }

    /** The TheSportsDB league number for a team number, from the catalogue's lists and rounds; "" when not there. */
    static String leagueOf(JSONObject catalogue, long team) {
        JSONArray all = catalogue == null ? null : catalogue.optJSONArray("leagues");
        if (all != null && team > 0) for (int i = 0; i < all.length(); i++) { JSONObject l = all.optJSONObject(i); if (l == null) continue;
            SportsDb.League def = SportsDb.league(l.optInt("id")); long sdb = l.optLong("sdb") > 0 ? l.optLong("sdb") : def == null ? 0 : def.sdb; if (sdb <= 0) continue;
            for (String list : new String[]{"teams", "more"}) { JSONArray t = l.optJSONArray(list); if (t != null) for (int j = 0; j < t.length(); j++) { JSONObject x = t.optJSONObject(j); if (x != null && x.optLong("id") == team) return String.valueOf(sdb); } } }
        return "";
    }

    /** "spurs" → "spurs", "Atlético" → "atletico": lower case, accents removed, only letters, digits and spaces. */
    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
    /**
     * A club's name for matching between sources: folded, without "FC", "AFC", "and", "the" or "&". "Brighton & Hove
     * Albion" and "Brighton and Hove Albion" match; "AFC Bournemouth" and "Bournemouth" match. A name made only of those
     * words is kept as it is.
     */
    static String key(String s) {
        String f = fold(s), k = (" " + f + " ").replaceAll(" (fc|afc|and|the|cf) ", " ").replaceAll(" (fc|afc|and|the|cf) ", " ").trim();
        return k.isEmpty() ? f : k;
    }

    /**
     * Teams whose name (or a word in it, its other names, or its short code) starts with the letters typed, then teams
     * that merely contain them; each group alphabetical; one row per team; at most {@code max}.
     */
    static List<Entry> search(JSONObject catalogue, String typed, int max) {
        String q = fold(typed);
        List<Entry> first = new ArrayList<>(), second = new ArrayList<>(); Set<String> seen = new HashSet<>();
        if (q.isEmpty()) return first;
        for (JSONObject l : leagues(catalogue)) for (Entry e : teams(l)) {
            if (!seen.add(e.id > 0 ? "#" + e.id : key(e.name))) continue;
            String n = fold(e.name), o = fold(e.also + " " + String.join(" ", e.aliases));
            if (n.startsWith(q) || (" " + n).contains(" " + q) || (!o.isEmpty() && (" " + o).contains(" " + q)) || fold(e.code).equals(q)) first.add(e);
            else if (n.replace(" ", "").contains(q.replace(" ", ""))) second.add(e);
        }
        Comparator<Entry> byName = Comparator.comparing(e -> fold(e.name));
        first.sort(byName); second.sort(byName); first.addAll(second);
        return first.size() > max ? new ArrayList<>(first.subList(0, max)) : first;
    }
}
