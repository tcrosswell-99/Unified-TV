package io.unifiedtv.preview;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The UK team list for the football picker: built once in the background as soon as an API-Football key exists (one
 * builder for the whole app, so phone setup and Settings never spend the day's requests twice), and searched with a few
 * letters. The building itself is FixtureFeed.build (paced, budgeted, resumable); this only shares it and searches it.
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
        if (now.optLong("fetchedAt") > 0 && !ApiFootball.catalogueDue(now.optLong("fetchedAt"), System.currentTimeMillis())) return;
        worker = new Thread(() -> FixtureFeed.build(app, (cat, problem) -> { for (Listener l : LISTENERS) try { l.changed(cat, problem); } catch (RuntimeException ignored) {} }), "team-list");
        worker.setDaemon(true); worker.start();
    }

    /** One team, with the league it was read from. */
    static final class Entry {
        final int id; final String name, code, league, country;
        Entry(int id, String name, String code, String league, String country) { this.id = id; this.name = name; this.code = code; this.league = league; this.country = country; }
    }

    /** The leagues with their teams read, in the catalogue's order (each country's own order). */
    static List<JSONObject> leagues(JSONObject catalogue) {
        List<JSONObject> out = new ArrayList<>(); JSONArray all = catalogue == null ? null : catalogue.optJSONArray("leagues");
        if (all != null) for (int i = 0; i < all.length(); i++) { JSONObject l = all.optJSONObject(i); if (l != null && l.optJSONArray("teams") != null && l.optJSONArray("teams").length() > 0) out.add(l); }
        return out;
    }
    static List<Entry> teams(JSONObject league) {
        List<Entry> out = new ArrayList<>(); JSONArray t = league == null ? null : league.optJSONArray("teams");
        if (t != null) for (int i = 0; i < t.length(); i++) { JSONObject x = t.optJSONObject(i); if (x != null && x.optInt("id") > 0) out.add(new Entry(x.optInt("id"), x.optString("name"), x.optString("code"), league.optString("name"), league.optString("country"))); }
        return out;
    }

    /** "spurs" → "spurs", "Atlético" → "atletico": lower case, accents removed, only letters, digits and spaces. */
    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Teams whose name (or a word in it, or its short code) starts with the letters typed, then teams that merely contain
     * them; each group alphabetical; one row per team; at most {@code max}.
     */
    static List<Entry> search(JSONObject catalogue, String typed, int max) {
        String q = fold(typed);
        List<Entry> first = new ArrayList<>(), second = new ArrayList<>(); Set<Integer> seen = new HashSet<>();
        if (q.isEmpty()) return first;
        for (JSONObject l : leagues(catalogue)) for (Entry e : teams(l)) {
            if (!seen.add(e.id)) continue;
            String n = fold(e.name);
            if (n.startsWith(q) || (" " + n).contains(" " + q) || fold(e.code).equals(q)) first.add(e);
            else if (n.replace(" ", "").contains(q.replace(" ", ""))) second.add(e);
        }
        Comparator<Entry> byName = Comparator.comparing(e -> fold(e.name));
        first.sort(byName); second.sort(byName); first.addAll(second);
        return first.size() > max ? new ArrayList<>(first.subList(0, max)) : first;
    }
}
