package io.unifiedtv.preview;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The UK team list for the football picker, from TheSportsDB (owner decision OD-22: no account or key): built in the
 * background when the picker first opens (one builder for the whole app), kept for a week, and searched with a few
 * letters on the TV itself (what is typed never leaves the TV). The building is FixtureFeed.build (paced, resumable);
 * this only shares it and searches it.
 */
final class TeamList {
    private TeamList() {}
    interface Listener { void changed(JSONObject catalogue, String problem); }
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static Thread worker;

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

    /**
     * One team, with the league it was read from: its other name ("Spurs"), its API-Football number when TheSportsDB
     * knows it (0 otherwise), and its own two colours for the drawn badge (0 when unknown).
     */
    static final class Entry {
        final long id, api; final String name, code, also, league, country; final int c1, c2;
        Entry(long id, String name, String code, String also, String league, String country, long api, int c1, int c2) {
            this.id = id; this.name = name; this.code = code; this.also = also == null ? "" : also; this.league = league; this.country = country; this.api = api; this.c1 = c1; this.c2 = c2;
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
        if (t != null) for (int i = 0; i < t.length(); i++) { JSONObject x = t.optJSONObject(i); if (x != null && x.optLong("id") > 0)
            out.add(new Entry(x.optLong("id"), x.optString("name"), x.isNull("code") ? "" : x.optString("code"), x.optString("also"), league.optString("name"), league.optString("country"), x.optLong("api"), x.optInt("c1"), x.optInt("c2"))); }
        return out;
    }

    /** "spurs" → "spurs", "Atlético" → "atletico": lower case, accents removed, only letters, digits and spaces. */
    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Teams whose name (or a word in it, its other name, or its short code) starts with the letters typed, then teams
     * that merely contain them; each group alphabetical; one row per team; at most {@code max}.
     */
    static List<Entry> search(JSONObject catalogue, String typed, int max) {
        String q = fold(typed);
        List<Entry> first = new ArrayList<>(), second = new ArrayList<>(); Set<Long> seen = new HashSet<>();
        if (q.isEmpty()) return first;
        for (JSONObject l : leagues(catalogue)) for (Entry e : teams(l)) {
            if (!seen.add(e.id)) continue;
            String n = fold(e.name), o = fold(e.also);
            if (n.startsWith(q) || (" " + n).contains(" " + q) || (!o.isEmpty() && (" " + o).contains(" " + q)) || fold(e.code).equals(q)) first.add(e);
            else if (n.replace(" ", "").contains(q.replace(" ", ""))) second.add(e);
        }
        Comparator<Entry> byName = Comparator.comparing(e -> fold(e.name));
        first.sort(byName); second.sort(byName); first.addAll(second);
        return first.size() > max ? new ArrayList<>(first.subList(0, max)) : first;
    }
}
