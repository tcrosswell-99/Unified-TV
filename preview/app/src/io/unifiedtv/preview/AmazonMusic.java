package io.unifiedtv.preview;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Controlling the real Amazon Music app from inside Unified TV (OD-27, owner, 27 October 2026), with Android's official
 * media APIs only: the app's own media session (play from search, transport controls), Android's standard "play from
 * search" request sent explicitly to Amazon Music, and its MediaBrowserService if it agrees to share. No unofficial Amazon
 * API, no scraping, no DRM workaround. Amazon Music keeps playing in its own app; Unified TV is the remote and the screen.
 *
 * <p>Play by name tries, in order: (1) {@code playFromSearch} on Amazon Music's live session; (2) the standard
 * MEDIA_PLAY_FROM_SEARCH request addressed only to Amazon Music, when it says it handles one; (3) Amazon Music's own
 * search, or the app, with a plain sentence saying so. Kids: only when an adult approved Amazon Music (Launch is the one
 * gate). Nothing here is sent anywhere but to the Amazon Music app on this TV.
 */
final class AmazonMusic {
    private AmazonMusic() {}
    static final String NAME = "Amazon Music";
    /** How long Now Playing waits for Amazon Music to start what was asked for. */
    static final long WAIT_MS = 8_000;
    static final int RECENT_MAX = 6, QUERY_MAX = 100;
    static final String RECENT_PREF = "music.recent.", ROUTE_PREF = "music.lastRoute", ROUTE_AT_PREF = "music.lastRouteAt",
            BROWSE_PREF = "music.browse", BROWSE_AT_PREF = "music.browseAt";
    static final String BROWSE_AVAILABLE = "available", BROWSE_REFUSED = "refused";
    static final String BROWSER_SERVICE = "android.media.browse.MediaBrowserService";

    /** What the household is asking for, as Android's MEDIA_PLAY_FROM_SEARCH names it. */
    enum Focus {
        ANY("Anything", "vnd.android.cursor.item/*"),
        ARTIST("Artist", MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE),
        ALBUM("Album", MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE),
        PLAYLIST("Playlist", MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE),
        SONG("Song", MediaStore.Audio.Media.ENTRY_CONTENT_TYPE);
        final String words, mime;
        Focus(String words, String mime) { this.words = words; this.mime = mime; }
        Focus next() { return values()[(ordinal() + 1) % values().length]; }
        static Focus of(String name) { for (Focus f : values()) if (f.name().equals(name)) return f; return ANY; }
    }

    /** Which way a request went, for the household's message and Owner diagnostics. */
    enum Route { SESSION, INTENT, APP, STORE, REFUSED, EMPTY, LIBRARY, ECHO }

    // --- finding the app (by its known ids, or by its own name "Amazon Music"; never guessed)

    static boolean isAmazonLabel(CharSequence label) { return label != null && label.toString().trim().toLowerCase(Locale.ROOT).equals("amazon music"); }

    /** Amazon Music's package names on this TV: the known ids, plus any launchable app named "Amazon Music". */
    static Set<String> packages(Context c) {
        long now = SystemClock.uptimeMillis();
        Set<String> kept = found;
        if (kept != null && now >= foundAt && now - foundAt < KEEP_MS) return kept;
        Set<String> out = scan(c);
        found = java.util.Collections.unmodifiableSet(out); foundAt = now;
        return found;
    }
    /** The scan above is kept this long: it reads every launchable app, which is slow on a 2 GB TV (AGENTS rule 5). */
    static final long KEEP_MS = 30_000;
    private static volatile Set<String> found; private static volatile long foundAt;
    /** Tests: forget the last scan. */
    static void rescan() { found = null; }
    private static Set<String> scan(Context c) {
        Set<String> out = new LinkedHashSet<>(Arrays.asList(NowPlaying.AMAZON));
        PackageManager pm = c.getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> found;
            try { found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0); } catch (RuntimeException e) { continue; }
            if (found == null) continue;
            for (ResolveInfo r : found) {
                if (r.activityInfo == null) continue;
                try { if (isAmazonLabel(r.loadLabel(pm))) out.add(r.activityInfo.packageName); } catch (RuntimeException ignored) { }
            }
        }
        return out;
    }

    /** The installed Amazon Music package (the TV build first), or null. */
    static String installed(Context c) {
        PackageManager pm = c.getPackageManager();
        String byName = null;
        for (String p : packages(c)) {
            boolean launchable = pm.getLeanbackLaunchIntentForPackage(p) != null || pm.getLaunchIntentForPackage(p) != null;
            if (!launchable) continue;
            if (Arrays.asList(NowPlaying.AMAZON).contains(p)) return p;
            if (byName == null) byName = p;
        }
        return byName;
    }

    /** The app's own name as Android has it; "Amazon Music" for its known ids when the label can't be read. */
    static String label(Context c, String pkg) {
        try { PackageManager pm = c.getPackageManager(); return NowPlaying.bounded(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)), 40); }
        catch (Exception e) { return NowPlaying.amazon(pkg) ? NAME : ""; }
    }

    // --- play by name

    /** Pure: which route a request takes, in the owner's order. */
    static Route route(boolean sessionTakesSearch, boolean intentResolves, boolean installed) {
        if (!installed) return Route.STORE;
        if (sessionTakesSearch) return Route.SESSION;
        if (intentResolves) return Route.INTENT;
        return Route.APP;
    }

    /** Whether a session takes a play-from-search request: declared, or nothing declared at all (many apps leave it empty). */
    static boolean takesSearch(NowPlaying n) { return n != null && n.offers(NowPlaying.ACTION_PLAY_FROM_SEARCH); }

    /** The search words as sent: one line, at most QUERY_MAX characters. */
    static String clean(String query) { return NowPlaying.bounded(query == null ? "" : query, QUERY_MAX); }

    /** The extras Android defines for MEDIA_PLAY_FROM_SEARCH: the focus, and the matching field when one was chosen. */
    static Bundle extras(String query, Focus focus) {
        Bundle b = new Bundle();
        b.putString(SearchManager.QUERY, query);
        b.putString(MediaStore.EXTRA_MEDIA_FOCUS, focus.mime);
        switch (focus) {
            case ARTIST: b.putString(MediaStore.EXTRA_MEDIA_ARTIST, query); break;
            case ALBUM: b.putString(MediaStore.EXTRA_MEDIA_ALBUM, query); break;
            case PLAYLIST: b.putString(MediaStore.EXTRA_MEDIA_PLAYLIST, query); break;
            case SONG: b.putString(MediaStore.EXTRA_MEDIA_TITLE, query); break;
            default: break;
        }
        return b;
    }

    /** Android's standard request, addressed only to Amazon Music's package (never a chooser, never another app). */
    static Intent searchIntent(String pkg, String query, Focus focus) {
        return new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(pkg).putExtras(extras(query, focus))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** What happened, and the plain sentence for the household (empty when Now Playing will say it). */
    static final class Result {
        final Route route; final String words;
        Result(Route route, String words) { this.route = route; this.words = words; }
    }

    /**
     * Plays {@code query} in Amazon Music on this TV, and opens Now Playing when the app's session is visible. From the
     * Music page and from voice. Main thread only.
     */
    static Result play(Activity a, String query, Focus focus) {
        String q = clean(query);
        if (q.isEmpty()) return new Result(Route.EMPTY, "Type or say what you'd like to hear.");
        String pkg = installed(a);
        String no = Launch.kidsRefusal(a, pkg == null ? "" : pkg, pkg == null ? "The app store" : NAME);
        if (no != null) return record(a, new Result(Route.REFUSED, no));
        if (pkg == null) { MusicActivity.open(a); return record(a, new Result(Route.STORE, NAME + " isn't installed on this TV, so the TV's app store is open to find it.")); }
        remember(a, q);
        MusicSessions.Source src = MusicSessions.source;
        MusicSession session = src.granted(a) ? MusicSessions.amazon(a, src.active(a)) : null;
        Intent intent = searchIntent(pkg, q, focus);
        boolean resolves = resolves(a, intent);
        Route r = route(session != null && takesSearch(session.read()), resolves, true);
        if (r == Route.SESSION) {
            try {
                String before = session.read().trackKey();
                session.playFromSearch(q, extras(q, focus));
                a.startActivity(NowPlayingActivity.waiting(a, q, focus, Route.SESSION, before));
                return record(a, new Result(r, ""));
            } catch (RuntimeException e) { r = resolves ? Route.INTENT : Route.APP; }   // the session didn't take it: ask the app itself
        }
        if (r == Route.INTENT) {
            try { a.startActivity(intent); }
            catch (RuntimeException e) { return record(a, openApp(a, pkg, q)); }
            boolean watching = src.granted(a);
            if (watching) bringBack(a.getApplicationContext());
            return record(a, new Result(Route.INTENT, watching ? ""
                    : "Asked " + NAME + " to play “" + q + "”. To see it and control it here, allow notification access on the Music page."));
        }
        return record(a, openApp(a, pkg, q));
    }

    /** Fallback: Amazon Music's own search screen with the words, else the app; said plainly. */
    static Result openApp(Activity a, String pkg, String q) {
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (resolves(a, search)) {
            try { a.startActivity(search); return new Result(Route.APP, NAME + " didn't take a request from another app, so its own search is open with “" + q + "”."); }
            catch (RuntimeException ignored) { }
        }
        String why = Launch.open(a, pkg, NAME);
        if (why != null && !why.isEmpty()) return new Result(Route.APP, why);
        return new Result(Route.APP, NAME + " didn't take a request from another app, so it's open for you to search for “" + q + "” there.");
    }

    /** The intent route after the session didn't start anything (Now Playing's wait ran out). True when it was sent. */
    static boolean retryWithIntent(Activity a, String q, Focus focus) {
        String pkg = installed(a); if (pkg == null) return false;
        Intent intent = searchIntent(pkg, q, focus);
        if (!resolves(a, intent)) return false;
        try { a.startActivity(intent); } catch (RuntimeException e) { return false; }
        record(a, new Result(Route.INTENT, ""));
        bringBack(a.getApplicationContext());
        return true;
    }

    static boolean resolves(Context c, Intent i) {
        try { return c.getPackageManager().resolveActivity(i, 0) != null; } catch (RuntimeException e) { return false; }
    }

    private static Result record(Context c, Result r) {
        c.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putString(ROUTE_PREF, r.route.name()).putLong(ROUTE_AT_PREF, System.currentTimeMillis()).apply();
        return r;
    }

    /** Polls for this long, this often, after the intent route, for Amazon Music's session to start. */
    static final long POLL_MS = 500;

    /**
     * After the request went to Amazon Music's own screen: once its session is playing (within WAIT_MS), Now Playing
     * comes back to the front. Nothing more if it never starts; Amazon Music's screen stays, which is the household's.
     */
    static void bringBack(Context app) {
        Handler h = new Handler(Looper.getMainLooper());
        long until = SystemClock.uptimeMillis() + WAIT_MS;
        Runnable[] look = new Runnable[1];
        look[0] = () -> {
            MusicSession s = null;
            try { s = MusicSessions.amazon(app, MusicSessions.source.active(app)); } catch (RuntimeException ignored) { }
            if (s != null && s.read().playing()) {
                try { app.startActivity(new Intent(app, NowPlayingActivity.class).putExtra(NowPlayingActivity.EXTRA_PLACE, Echoes.TV)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)); }
                catch (RuntimeException ignored) { /* Android may not let an app come back to the front by itself */ }
                return;
            }
            if (SystemClock.uptimeMillis() < until) h.postDelayed(look[0], POLL_MS);
        };
        h.postDelayed(look[0], POLL_MS);
    }

    // --- recent searches (this profile's own, on this TV only)

    static String recentKey(SharedPreferences p) { return RECENT_PREF + Profiles.current(p); }
    static List<String> recent(Context c) {
        SharedPreferences p = c.getSharedPreferences(HouseholdSettings.PREFS, 0);
        List<String> out = new ArrayList<>();
        try { JSONArray a = new JSONArray(p.getString(recentKey(p), "[]")); for (int i = 0; i < a.length() && out.size() < RECENT_MAX; i++) { String s = clean(a.optString(i)); if (!s.isEmpty()) out.add(s); } }
        catch (Exception ignored) { }
        return out;
    }
    /** Pure: the newest first, no repeats (ignoring case), at most RECENT_MAX. */
    static List<String> add(List<String> before, String q) {
        List<String> out = new ArrayList<>(); out.add(q);
        for (String s : before) if (!s.equalsIgnoreCase(q) && out.size() < RECENT_MAX) out.add(s);
        return out;
    }
    static void remember(Context c, String q) {
        SharedPreferences p = c.getSharedPreferences(HouseholdSettings.PREFS, 0);
        p.edit().putString(recentKey(p), new JSONArray(add(recent(c), q)).toString()).apply();
    }
    static void forget(Context c) { SharedPreferences p = c.getSharedPreferences(HouseholdSettings.PREFS, 0); p.edit().remove(recentKey(p)).apply(); }

    // --- library browsing (best effort)

    /** Amazon Music's MediaBrowserService, if it declares one; null otherwise. */
    static ComponentName browserService(Context c, String pkg) {
        if (pkg == null) return null;
        try {
            List<ResolveInfo> found = c.getPackageManager().queryIntentServices(new Intent(BROWSER_SERVICE).setPackage(pkg), 0);
            if (found == null) return null;
            for (ResolveInfo r : found) if (r.serviceInfo != null) return new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name);
        } catch (RuntimeException ignored) { }
        return null;
    }
    static void browsing(Context c, boolean available) {
        c.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putString(BROWSE_PREF, available ? BROWSE_AVAILABLE : BROWSE_REFUSED).putLong(BROWSE_AT_PREF, System.currentTimeMillis()).apply();
    }

    /** Owner diagnostics' Music lines: what this TV showed, no account details. */
    static String diagnosis(Context c) {
        SharedPreferences p = c.getSharedPreferences(HouseholdSettings.PREFS, 0);
        String pkg = installed(c);
        StringBuilder b = new StringBuilder(NAME + ": ").append(pkg == null ? "not installed" : "installed (" + pkg + ")");
        b.append("\nMedia notification access: ").append(MediaWatch.granted(c) ? "allowed" : "not allowed");
        String browse = p.getString(BROWSE_PREF, "");
        b.append("\nAmazon Music browsing: ").append(browse.isEmpty() ? "not tried yet" : browse + ", " + OwnerChecks.when(c, p.getLong(BROWSE_AT_PREF, 0)));
        String route = p.getString(ROUTE_PREF, "");
        b.append("\nLast play by name: ").append(route.isEmpty() ? "none yet" : routeWords(route) + ", " + OwnerChecks.when(c, p.getLong(ROUTE_AT_PREF, 0)));
        return b.toString();
    }
    static String routeWords(String route) {
        switch (route) {
            case "SESSION": return "asked Amazon Music's playing session";
            case "INTENT": return "sent Android's play-from-search request to Amazon Music";
            case "APP": return "opened Amazon Music's own search";
            case "STORE": return "Amazon Music not installed";
            case "REFUSED": return "not allowed in this profile";
            default: return route.toLowerCase(Locale.ROOT);
        }
    }
}
