package io.unifiedtv.preview;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The Optional clean-up's tested lists (SPEC "per-firmware allowlists"), from the bundled asset that
 * preview/tools/cleanup_lists.py generates out of examples/tcl-50c725k, tcl-55c725 and tcl-65c725 (debloat.txt). Only
 * these packages can ever be offered, and only on the TCL BeyondTV4 family on build RP1A.200622.001 (Android TV 11).
 *
 * <ul>
 * <li><b>This firmware</b>: the TV's fingerprint is one a list was tested on (the owner's TCL:
 *     TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR10, the 50C725K list).</li>
 * <li><b>Same family</b>: a TCL BeyondTV4 on the same Android 11 build, but not a tested fingerprint. Offered with a
 *     higher risk and never as "recommended".</li>
 * <li><b>Unknown</b>: anything else. Read-only guidance only; nothing can be changed (AT-32).</li>
 * </ul>
 * The packages the lists' own notes say to keep are never offered, from any list of the family.
 */
final class CleanupLists {
    static final String ASSET = "cleanup/tcl-beyondtv4.json";
    /** Tests only: the asset's text (the local test runner doesn't package assets). */
    static volatile String forTests;
    private CleanupLists() {}

    enum Level { THIS_FIRMWARE, SAME_FAMILY, UNKNOWN }

    static final class Entry {
        final String pkg, group, note, list;
        /** True when this list was tested on this very firmware. */
        final boolean thisFirmware;
        Entry(String pkg, String group, String note, String list, boolean thisFirmware) { this.pkg = pkg; this.group = group; this.note = note; this.list = list; this.thisFirmware = thisFirmware; }
    }

    static final class Profile {
        String id, title, source, product, stockLauncher;
        final List<String> fingerprints = new ArrayList<>(), keep = new ArrayList<>();
        final List<String[]> packages = new ArrayList<>();   // name, group, note
    }

    static final class Match {
        final Level level; final Profile list; final List<Entry> items; final Set<String> keep, stockLaunchers; final String fingerprint;
        Match(Level level, Profile list, List<Entry> items, Set<String> keep, Set<String> stockLaunchers, String fingerprint) {
            this.level = level; this.list = list; this.items = items; this.keep = keep; this.stockLaunchers = stockLaunchers; this.fingerprint = fingerprint;
        }
        boolean known() { return level != Level.UNKNOWN; }
        /** In plain words: which list, and how close a match. */
        String describe() {
            switch (level) {
                case THIS_FIRMWARE: return "Tested list: " + list.title + ", on this exact firmware.";
                case SAME_FAMILY: return "Closest tested list: " + list.title + ". Same TCL family and Android build, but not this exact firmware, so every item counts as riskier.";
                default: return "This TV isn't one the lists were tested on.";
            }
        }
    }

    /** The asset's text, or "" when it can't be read (then every TV is unknown). */
    static String load(Context c) {
        String t = forTests; if (t != null) return t;
        try (InputStream in = c.getAssets().open(ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) { return ""; }
    }

    /** brand/product/device:release/build/incremental, without ":user/release-keys" (lists sometimes leave it out). */
    static String core(String fingerprint) {
        if (fingerprint == null) return "";
        String f = fingerprint.trim();
        int first = f.indexOf(':'), last = f.lastIndexOf(':');
        return first >= 0 && last > first ? f.substring(0, last) : f;
    }

    static List<Profile> profiles(JSONObject root) {
        List<Profile> out = new ArrayList<>();
        JSONArray all = root.optJSONArray("profiles");
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i); if (o == null) continue;
            Profile p = new Profile();
            p.id = o.optString("id"); p.title = o.optString("title", p.id); p.source = o.optString("source");
            p.product = o.isNull("product") ? null : o.optString("product"); p.stockLauncher = o.isNull("stockLauncher") ? null : o.optString("stockLauncher");
            strings(o.optJSONArray("fingerprints"), p.fingerprints); strings(o.optJSONArray("keep"), p.keep);
            JSONArray pk = o.optJSONArray("packages");
            for (int j = 0; pk != null && j < pk.length(); j++) {
                JSONObject e = pk.optJSONObject(j); if (e == null) continue;
                String name = e.optString("name");
                if (io.unifiedtv.adb.AdbCommands.isPackage(name)) p.packages.add(new String[]{name, e.optString("group", "Other"), e.optString("note")});
            }
            out.add(p);
        }
        return out;
    }
    private static void strings(JSONArray a, List<String> into) { for (int i = 0; a != null && i < a.length(); i++) { String s = a.optString(i); if (!s.isEmpty()) into.add(s); } }

    /** Which tested list (if any) covers the TV with this fingerprint, and the packages it may offer. */
    static Match match(String json, String fingerprint) {
        Set<String> none = Collections.emptySet();
        JSONObject root;
        try { root = new JSONObject(json == null ? "" : json); } catch (Exception e) { return new Match(Level.UNKNOWN, null, Collections.emptyList(), none, none, fingerprint); }
        List<Profile> profiles = profiles(root);
        if (profiles.isEmpty()) return new Match(Level.UNKNOWN, null, Collections.emptyList(), none, none, fingerprint);
        String core = core(fingerprint);
        Profile primary = null; Level level = Level.UNKNOWN;
        for (Profile p : profiles) if (p.fingerprints.contains(core)) { primary = p; level = Level.THIS_FIRMWARE; break; }
        if (primary == null && family(root.optJSONObject("family"), core)) {
            level = Level.SAME_FAMILY;
            String product = part(core, 1);
            for (Profile p : profiles) if (product.equals(p.product)) { primary = p; break; }
            if (primary == null) primary = profiles.get(0);
        }
        if (primary == null) return new Match(Level.UNKNOWN, null, Collections.emptyList(), none, none, fingerprint);
        Set<String> keep = new TreeSet<>(), launchers = new TreeSet<>();
        for (Profile p : profiles) { keep.addAll(p.keep); if (p.stockLauncher != null) launchers.add(p.stockLauncher); }
        List<Entry> items = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (String[] e : primary.packages) if (seen.add(e[0])) items.add(new Entry(e[0], e[1], e[2], primary.title, level == Level.THIS_FIRMWARE));
        for (Profile p : profiles) {
            if (p == primary) continue;
            for (String[] e : p.packages) if (seen.add(e[0])) items.add(new Entry(e[0], "Also tested on a similar TCL (" + p.title + ")", e[2], p.title, false));
        }
        return new Match(level, primary, Collections.unmodifiableList(items), Collections.unmodifiableSet(keep), Collections.unmodifiableSet(launchers), fingerprint);
    }

    /** brand/product/device:release/build/incremental, by position: 0 brand, 1 product, 2 device, 3 release, 4 build. */
    static String part(String core, int index) {
        String[] bits = core.replace(':', '/').split("/");
        return index < bits.length ? bits[index] : "";
    }
    private static boolean family(JSONObject f, String core) {
        if (f == null) return false;
        return part(core, 0).equalsIgnoreCase(f.optString("brand", "\u0000")) && part(core, 2).equals(f.optString("device"))
            && part(core, 3).equals(f.optString("release")) && part(core, 4).equals(f.optString("build"));
    }
}
