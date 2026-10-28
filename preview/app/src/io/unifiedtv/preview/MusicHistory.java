package io.unifiedtv.preview;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Recently played" on the Music page (owner, TCL build run 99), for when Amazon Music won't share its library: the albums
 * and artists Unified TV itself has seen Amazon Music (or an Echo) play, newest first, the last {@link #MAX} for each
 * profile, each with the artwork that played with it. Kept only on this TV (the names in preferences, a small picture
 * per entry in the app's own files), never sent anywhere; "Clear recent searches and plays" on the Music page removes it.
 * OK on one sends its name as play by name. File work is never on the main thread.
 */
final class MusicHistory {
    private MusicHistory() {}
    static final int MAX = 12, THUMB = 200, JPEG_QUALITY = 85;
    static final String PREF = "music.played.", DIR = "music-played";

    /** One thing that played: its name, what kind of thing it is (album or artist), who by, and its picture's file. */
    static final class Entry {
        final String name, by, art; final AmazonMusic.Focus focus;
        Entry(String name, AmazonMusic.Focus focus, String by, String art) { this.name = name; this.focus = focus; this.by = by == null ? "" : by; this.art = art == null ? "" : art; }
        String key() { return focus.name() + "\u0000" + name.toLowerCase(Locale.ROOT); }
    }

    /** Pure: what a track is remembered as (its album, else its artist); null when it says neither. */
    static Entry of(NowPlaying n) {
        if (n == null) return null;
        if (!n.album.isEmpty()) return new Entry(n.album, AmazonMusic.Focus.ALBUM, n.artist, null);
        if (!n.artist.isEmpty()) return new Entry(n.artist, AmazonMusic.Focus.ARTIST, "", null);
        return null;
    }
    /** Pure: the newest first, no repeats, at most MAX. */
    static List<Entry> add(List<Entry> before, Entry e) {
        List<Entry> out = new ArrayList<>(); out.add(e);
        for (Entry x : before) if (!x.key().equals(e.key()) && out.size() < MAX) out.add(x);
        return out;
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(HouseholdSettings.PREFS, 0); }
    private static String key(SharedPreferences p) { return PREF + Profiles.current(p); }

    static List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(key(prefs(c)), "[]"));
            for (int i = 0; i < a.length() && out.size() < MAX; i++) {
                JSONObject o = a.optJSONObject(i); if (o == null) continue;
                String name = NowPlaying.bounded(o.optString("name"), NowPlaying.LINE_MAX); if (name.isEmpty()) continue;
                out.add(new Entry(name, AmazonMusic.Focus.of(o.optString("focus")), NowPlaying.bounded(o.optString("by"), NowPlaying.LINE_MAX), o.optString("art")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Remembers what played, with a small copy of its artwork when there is one. Off the main thread. */
    static synchronized void remember(Context c, NowPlaying n, Bitmap art) {
        Entry e = of(n); if (e == null) return;
        String file = "";
        if (art != null && !art.isRecycled()) {
            try {
                File dir = new File(c.getFilesDir(), DIR); if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException();
                String name = hash(Profiles.current(prefs(c)) + "\u0000" + e.key()) + ".jpg";
                int[] plan = Images.plan(art.getWidth(), art.getHeight(), THUMB, THUMB);
                Bitmap small = Bitmap.createScaledBitmap(art, plan[1], plan[2], true);
                try (FileOutputStream out = new FileOutputStream(new File(dir, name))) { small.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out); }
                if (small != art) small.recycle();
                file = name;
            } catch (Exception ignored) { file = ""; }
        }
        List<Entry> before = list(c), next = add(before, new Entry(e.name, e.focus, e.by, file.isEmpty() ? art(before, e) : file));
        save(c, next);
        tidy(c);
    }
    /** The picture already kept for the same entry, so a play without art doesn't lose it. */
    private static String art(List<Entry> before, Entry e) { for (Entry x : before) if (x.key().equals(e.key())) return x.art; return ""; }
    private static void save(Context c, List<Entry> list) {
        JSONArray a = new JSONArray();
        try { for (Entry e : list) a.put(new JSONObject().put("name", e.name).put("focus", e.focus.name()).put("by", e.by).put("art", e.art)); } catch (Exception ignored) { }
        prefs(c).edit().putString(key(prefs(c)), a.toString()).apply();
    }
    /** Pictures no profile's list still names are deleted. */
    private static void tidy(Context c) {
        File dir = new File(c.getFilesDir(), DIR); File[] files = dir.listFiles(); if (files == null) return;
        Set<String> kept = new HashSet<>();
        for (String profile : new String[]{Profiles.ADULT, Profiles.CHILD}) {
            try { JSONArray a = new JSONArray(prefs(c).getString(PREF + profile, "[]")); for (int i = 0; i < a.length(); i++) kept.add(a.optJSONObject(i) == null ? "" : a.optJSONObject(i).optString("art")); }
            catch (Exception ignored) { }
        }
        for (File f : files) if (!kept.contains(f.getName())) { if (!f.delete()) f.deleteOnExit(); }
    }
    /** This profile's list and its pictures. Off the main thread. */
    static synchronized void forget(Context c) { prefs(c).edit().remove(key(prefs(c))).commit(); tidy(c); }

    /** An entry's picture at {@code size}, or null. Off the main thread. */
    static Bitmap picture(Context c, Entry e, int size) {
        if (e.art.isEmpty() || !e.art.matches("[0-9a-f]{40}\\.jpg")) return null;
        File f = new File(new File(c.getFilesDir(), DIR), e.art); if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = Images.plan(o.outWidth, o.outHeight, size, size)[0];
        o = new BitmapFactory.Options(); o.inSampleSize = sample; if (MemoryBudget.get(c).opaque565()) o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x)); return b.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }
}
