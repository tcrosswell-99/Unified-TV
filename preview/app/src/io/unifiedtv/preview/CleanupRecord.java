package io.unifiedtv.preview;

import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The Optional clean-up's recovery record: how each package was before Unified TV turned it off, written to this app's
 * storage before the first change (AGENTS.md rule 9, SPEC "records a backup"). It is the same format tools/recovery.py
 * writes and reads (version 1: created, host, port, device ids, list, home, animation scales, package states), so
 * tools/restore.sh can replay it from a computer without Unified TV (preview/tools/cleanup_restore.py files it where
 * restore.sh looks). Two extra keys, "fingerprint" and "source", are ignored by recovery.py.
 *
 * <p>As in recovery.py, the first record wins: a later clean-up only adds packages the record doesn't hold yet, so a
 * retry after a half-finished batch can never record the broken state as the original.
 *
 * <p>It holds the TV's serial number and android_id (recovery.py uses them to refuse another TV's record), so it is kept
 * in the no-backup folder, never logged, and only leaves the TV when the owner downloads it to their phone.
 */
final class CleanupRecord {
    static final int VERSION = 1;
    /** The address the record was taken at, as recovery.py would file it: this TV's own loopback ADB. */
    static final String HOST = "127.0.0.1", PORT = "5555";
    static final String LIST = "unifiedtv-cleanup.txt", FILE = "unifiedtv-cleanup-record.json", SOURCE = "Unified TV Optional clean-up";
    static final String ENABLED = "enabled", DISABLED = "disabled", ABSENT = "absent";
    static final String[] SCALES = io.unifiedtv.adb.AdbCommands.SCALES;
    /** recovery.py's DEVICE_ID_RE and PLACEHOLDER_IDS. */
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9._:-]{4,64}");
    private static final Set<String> PLACEHOLDERS = new HashSet<>(Arrays.asList("unknown", "null", "none", "0123456789abcdef"));
    private static final Pattern HOME = Pattern.compile("[A-Za-z_][\\w.]*/[\\w.$]+"), SCALE = Pattern.compile("-?\\d+(\\.\\d+)?|null");

    String created = "", fingerprint = "", home, serial, androidId;
    final Map<String, String> settings = new TreeMap<>();
    /** Package → its state before Unified TV changed anything: enabled, disabled or absent. */
    final Map<String, String> packages = new TreeMap<>();

    /** recovery.py's usable_id: an id that tells one TV from another, else null. */
    static String usableId(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (!DEVICE_ID.matcher(v).matches() || PLACEHOLDERS.contains(v.toLowerCase(Locale.ROOT)) || v.matches("0+")) return null;
        return v;
    }
    static String home(String component) { return component != null && HOME.matcher(component).matches() ? component : null; }
    static String scale(String value) { String v = value == null ? "" : value.trim(); return SCALE.matcher(v).matches() ? v : null; }

    /** Packages Unified TV turned off (recorded as on before), in name order. */
    List<String> changed() { List<String> out = new ArrayList<>(); for (Map.Entry<String, String> e : packages.entrySet()) if (ENABLED.equals(e.getValue())) out.add(e.getKey()); return out; }

    /** Adds what this record doesn't hold yet; never overwrites an entry (recovery.py: the first state wins). */
    List<String> add(Map<String, String> states) {
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, String> e : states.entrySet())
            if (!packages.containsKey(e.getKey()) && io.unifiedtv.adb.AdbCommands.isPackage(e.getKey())) { packages.put(e.getKey(), e.getValue()); added.add(e.getKey()); }
        return added;
    }

    // --- the file: recovery.py's json.dump(record, indent=2, sort_keys=True) plus a newline, so the two are the same text
    String json() {
        Map<String, Object> root = new TreeMap<>();
        root.put("version", VERSION);
        root.put("created", created);
        root.put("host", HOST);
        root.put("port", PORT);
        Map<String, Object> device = new TreeMap<>(); device.put("serial", serial); device.put("android_id", androidId); root.put("device", device);
        root.put("list", LIST);
        root.put("home", home);
        root.put("settings", new TreeMap<String, Object>(settings));
        root.put("packages", new TreeMap<String, Object>(packages));
        root.put("fingerprint", fingerprint);
        root.put("source", SOURCE);
        StringBuilder b = new StringBuilder(); write(b, root, 0); return b.append('\n').toString();
    }
    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v, int depth) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof Integer) { b.append(v); return; }
        if (v instanceof String) { quote(b, (String) v); return; }
        Map<String, Object> m = (Map<String, Object>) v;
        if (m.isEmpty()) { b.append("{}"); return; }
        b.append("{\n"); int i = 0;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            indent(b, depth + 1); quote(b, e.getKey()); b.append(": "); write(b, e.getValue(), depth + 1);
            b.append(++i < m.size() ? ",\n" : "\n");
        }
        indent(b, depth); b.append('}');
    }
    private static void indent(StringBuilder b, int depth) { for (int i = 0; i < depth * 2; i++) b.append(' '); }
    /** As Python's json with ensure_ascii: anything outside printable ASCII is \\uXXXX. */
    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break; case '\b': b.append("\\b"); break; case '\f': b.append("\\f"); break;
                default: if (c < 0x20 || c > 0x7f) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c)); else b.append(c);
            }
        }
        b.append('"');
    }

    /** A record read back, checked as recovery.py's load_record checks it; null for a damaged or foreign file. */
    static CleanupRecord parse(String text) {
        try {
            JSONObject o = new JSONObject(text);
            if (o.optInt("version") != VERSION) return null;
            JSONObject p = o.optJSONObject("packages"); if (p == null) return null;
            CleanupRecord r = new CleanupRecord();
            r.created = o.optString("created"); r.fingerprint = o.optString("fingerprint");
            r.home = o.isNull("home") ? null : home(o.optString("home"));
            if (!o.isNull("home") && r.home == null) return null;
            for (Iterator<String> k = p.keys(); k.hasNext(); ) {
                String name = k.next(), state = p.optString(name);
                if (!io.unifiedtv.adb.AdbCommands.isPackage(name) || !(ENABLED.equals(state) || DISABLED.equals(state) || ABSENT.equals(state))) return null;
                r.packages.put(name, state);
            }
            JSONObject s = o.optJSONObject("settings");
            if (s != null) for (Iterator<String> k = s.keys(); k.hasNext(); ) { String key = k.next(); r.settings.put(key, s.optString(key)); }
            JSONObject d = o.optJSONObject("device");
            if (d != null) { r.serial = d.isNull("serial") ? null : usableId(d.optString("serial")); r.androidId = d.isNull("android_id") ? null : usableId(d.optString("android_id")); }
            return r;
        } catch (Exception e) { return null; }
    }

    /** The list file for tools/restore.sh: one package per line, the ones recorded as on before (what Unified TV turned off). */
    String listText() {
        StringBuilder b = new StringBuilder("# Unified TV Optional clean-up: the apps it turned off on this TV.\n")
            .append("# Restore from a computer: python3 preview/tools/cleanup_restore.py ").append(FILE).append(", then the tools/restore.sh line it prints.\n")
            .append("# Or on the TV: Settings > Apps > See all apps > Show system apps > the app > Enable.\n");
        for (String pkg : changed()) b.append(pkg).append('\n');
        return b.toString();
    }

    // --- storage: this app's no-backup folder
    static File file(File dir) { return new File(new File(dir, "cleanup"), "recovery.json"); }

    static CleanupRecord read(File file) {
        if (!file.isFile()) return null;
        try { return parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)); } catch (IOException e) { return null; }
    }

    /** Written next to its final name first, synced, then renamed: a crash can't leave half a record (as recovery.py). */
    static void write(File file, CleanupRecord record) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("no folder for the record");
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(record.json().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        if (!tmp.renameTo(file)) { if (!tmp.delete()) tmp.deleteOnExit(); throw new IOException("record not saved"); }
    }
}
