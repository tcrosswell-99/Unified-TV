package io.unifiedtv.preview;

import io.unifiedtv.adb.AdbCommands;
import io.unifiedtv.adb.AdbException;
import io.unifiedtv.adb.AdbLink;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;

/**
 * The Optional clean-up's steps, over any shell that runs the allowlisted commands (the TV's own ADB through AdbLink on a
 * TV, a simulated TV in tests). The page (CleanupActivity) only shows what these return.
 *
 * <ol>
 * <li>{@link #check}: read-only (AT-20). Firmware, which tested list applies, and for each listed package whether it is
 *     installed and on, its version, and whether it may be offered at all (CleanupPolicy).</li>
 * <li>{@link #apply}: only the packages the owner switched on and confirmed, one by one, with
 *     {@code pm disable-user --user 0}; the recovery record is written before the first one, and nothing is sent if it
 *     can't be.</li>
 * <li>{@link #states}: re-reads the packages afterwards; {@link #turnBackOn} re-enables any the record says were on.</li>
 * </ol>
 */
final class Cleanup {
    private Cleanup() {}

    /** Runs one allowed command on the TV and returns its output. */
    interface Shell { String run(String command) throws IOException; }
    interface Progress { void step(int done, int total, String pkg); }

    static final class Item {
        final CleanupLists.Entry entry;
        String state = CleanupRecord.ABSENT, version = "", blocked;
        CleanupPolicy.Risk risk = CleanupPolicy.Risk.LOW; String warning; boolean recommended;
        Item(CleanupLists.Entry entry) { this.entry = entry; }
        String pkg() { return entry.pkg; }
        String title() { return CleanupPolicy.title(entry); }
        boolean canTurnOff() { return blocked == null && CleanupRecord.ENABLED.equals(state); }
    }

    static final class Check {
        String fingerprint = "", adbSecure = "";
        CleanupLists.Match match;
        final List<Item> items = new ArrayList<>();
        final CleanupPolicy.Facts facts = new CleanupPolicy.Facts();
        Item item(String pkg) { for (Item i : items) if (i.pkg().equals(pkg)) return i; return null; }
        /** ro.adb.secure=0: network debugging lets any device on the network in without the prompt. */
        boolean openToAll() { return "0".equals(adbSecure); }
    }

    // --- reading what the TV prints
    static Set<String> packages(String out) {
        Set<String> s = new LinkedHashSet<>();
        if (out != null) for (String line : out.split("\\r?\\n")) { line = line.trim(); if (line.startsWith("package:")) { String p = line.substring(8).trim(); if (AdbCommands.isPackage(p)) s.add(p); } }
        return s;
    }
    /** Packages of the components in a --brief query's answer ("com.example/.Main"). */
    static Set<String> components(String out) {
        Set<String> s = new LinkedHashSet<>();
        if (out != null) for (String line : out.split("\\r?\\n")) { line = line.trim(); int slash = line.indexOf('/'); if (slash > 0 && AdbCommands.isPackage(line.substring(0, slash))) s.add(line.substring(0, slash)); }
        return s;
    }
    /**
     * The component that owns Home (resolve-activity's last line), or null for nothing, a bare "android", or Android's
     * chooser "android/com.android.internal.app.ResolverActivity" (no Home is the default).
     */
    static String homeComponent(String out) {
        if (out == null) return null;
        String[] lines = out.trim().split("\\r?\\n");
        return CleanupRecord.home(lines[lines.length - 1].trim());
    }
    /** Packages of the active screensaver(s): {@code screensaver_components} is a comma-separated list, or "null". */
    static Set<String> screensavers(String out) {
        Set<String> s = new LinkedHashSet<>();
        if (out != null) for (String part : out.split("[,\\s]+")) { int slash = part.indexOf('/'); if (slash > 0 && AdbCommands.isPackage(part.substring(0, slash))) s.add(part.substring(0, slash)); }
        return s;
    }
    /** Stock Home apps, whichever this TV has (the lists' own, and the Google ones). */
    static final List<String> STOCK = Arrays.asList("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.tcl.tvlauncher");
    static String versionName(String dumpsys) {
        if (dumpsys == null) return "";
        for (String line : dumpsys.split("\\r?\\n")) { String t = line.trim(); if (t.startsWith("versionName=")) return t.substring(12).trim(); }
        return "";
    }
    /** As disable.sh: TV_INPUT_HARDWARE in the package's own "requested permissions" block marks an input driver (#321). */
    static boolean inputDriver(String dumpsys) {
        if (dumpsys == null) return false;
        boolean inBlock = false;
        for (String line : dumpsys.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("requested permissions:")) { inBlock = true; continue; }
            if (inBlock && (t.startsWith("install permissions:") || t.startsWith("runtime permissions:") || t.matches("User \\d+:.*"))) inBlock = false;
            if (inBlock && t.startsWith("android.permission.TV_INPUT_HARDWARE")) return true;
        }
        return false;
    }

    /**
     * The read-only check. {@code expected} is this TV's own fingerprint (Build.FINGERPRINT): if the shell answers with
     * another, it isn't this TV, and nothing more is read.
     */
    static Check check(Shell sh, String lists, String expected, String self, String liveTv) throws IOException {
        Check c = new Check();
        c.fingerprint = sh.run(AdbCommands.FINGERPRINT).trim();
        if (!c.fingerprint.equals(expected == null ? "" : expected.trim())) throw new AdbException(AdbException.Kind.NOT_THIS_TV, "Another device answered.");
        c.match = CleanupLists.match(lists, c.fingerprint);
        c.adbSecure = sh.run(AdbCommands.ADB_SECURE).trim();
        if (!c.match.known()) return c;   // AT-32: read-only guidance, nothing about packages is even read
        facts(sh, c.facts, c.match, self, liveTv);
        Set<String> installed = packages(sh.run(AdbCommands.PACKAGES)), disabled = packages(sh.run(AdbCommands.DISABLED));
        live(sh, c.facts, installed, disabled);
        for (CleanupLists.Entry e : c.match.items) {
            Item i = new Item(e);
            i.state = state(e.pkg, installed, disabled);
            if (CleanupRecord.ENABLED.equals(i.state)) {
                String dump = sh.run(AdbCommands.dumpsys(e.pkg));
                i.version = versionName(dump);
                if (inputDriver(dump)) c.facts.inputDrivers.add(e.pkg);
            }
            i.blocked = CleanupPolicy.blocked(e.pkg, c.facts);
            i.risk = CleanupPolicy.risk(e); i.warning = CleanupPolicy.warning(e.pkg); i.recommended = i.blocked == null && CleanupPolicy.recommended(e);
            c.items.add(i);
        }
        return c;
    }

    private static void facts(Shell sh, CleanupPolicy.Facts f, CleanupLists.Match m, String self, String liveTv) throws IOException {
        f.keep.addAll(m.keep); f.stockLaunchers.addAll(m.stockLaunchers); f.self = self; f.liveTv = liveTv;
        f.channelApps.addAll(components(sh.run(AdbCommands.CHANNEL_APPS)));
        f.settingsApps.addAll(components(sh.run(AdbCommands.SETTINGS_APPS)));
    }

    /** What can change between the check and the change: Home, its claimants, the screensaver, the stock launcher. Returns Home. */
    private static String live(Shell sh, CleanupPolicy.Facts f, Set<String> installed, Set<String> disabled) throws IOException {
        String home = homeComponent(sh.run(AdbCommands.HOME));
        f.homeOwner = home == null ? null : home.substring(0, home.indexOf('/'));
        f.homeClaims.addAll(components(sh.run(AdbCommands.HOME_CLAIMS)));
        f.screensavers.clear(); f.screensavers.addAll(screensavers(sh.run(AdbCommands.SCREENSAVER)));
        Set<String> stock = new LinkedHashSet<>(STOCK); stock.addAll(f.stockLaunchers);
        f.stockLauncherOn = false;
        for (String l : stock) if (installed.contains(l) && !disabled.contains(l)) f.stockLauncherOn = true;
        return home;
    }

    static String state(String pkg, Set<String> installed, Set<String> disabled) {
        return !installed.contains(pkg) ? CleanupRecord.ABSENT : disabled.contains(pkg) ? CleanupRecord.DISABLED : CleanupRecord.ENABLED;
    }

    /** Each package's state now. */
    static Map<String, String> states(Shell sh, Collection<String> pkgs) throws IOException {
        Set<String> installed = packages(sh.run(AdbCommands.PACKAGES)), disabled = packages(sh.run(AdbCommands.DISABLED));
        Map<String, String> out = new LinkedHashMap<>();
        for (String p : pkgs) out.put(p, state(p, installed, disabled));
        return out;
    }

    /**
     * Turns off exactly the chosen packages, which must each be offered by the check and still on. Before anything is
     * sent: Home and the package states are read again (nothing may have started answering Home since), and the recovery
     * record is written; if either fails, nothing is changed. Returns the packages the TV reports as turned off.
     */
    static List<String> apply(Shell sh, File recordFile, Check check, Collection<String> chosen, String created, Progress progress, Consumer<String> changed) throws IOException {
        List<String> batch = new ArrayList<>(new LinkedHashSet<>(chosen));
        if (batch.isEmpty()) throw new IllegalArgumentException("nothing chosen");
        for (String p : batch) { Item i = check.item(p); if (i == null || !i.canTurnOff()) throw new IllegalArgumentException("not offered: " + p); }
        // Read again: Home, the screensaver, the stock launcher or a package could have changed since the check.
        CleanupPolicy.Facts now = check.facts;
        Set<String> installed = packages(sh.run(AdbCommands.PACKAGES)), disabled = packages(sh.run(AdbCommands.DISABLED));
        String home = live(sh, now, installed, disabled);
        Map<String, String> before = new LinkedHashMap<>();
        for (String p : batch) before.put(p, state(p, installed, disabled));
        for (String p : batch)
            if (CleanupPolicy.blocked(p, now) != null || !CleanupRecord.ENABLED.equals(before.get(p))) throw new IllegalStateException("changed since the check: " + p);
        // The record goes to disk before the first change; a failed write stops here with the TV untouched.
        CleanupRecord record = CleanupRecord.read(recordFile);
        if (record == null) record = snapshot(sh, check.fingerprint, home, created);
        // Only the chosen packages: Home is recorded as a component ("home") but never changed here, so its package is not
        // listed (tools/recovery.py lists it because a debloat list may disable it; this page never can).
        List<String> added = record.add(before);
        CleanupRecord.write(recordFile, record);
        List<String> said = new ArrayList<>();
        for (int n = 0; n < batch.size(); n++) {
            String p = batch.get(n);
            if (progress != null) progress.step(n, batch.size(), p);
            if (said(sh.run(AdbCommands.disable(p)), p, "disabled-user")) said.add(p);
        }
        if (progress != null) progress.step(batch.size(), batch.size(), null);
        // What actually changed is what the TV reports now, not what pm printed.
        List<String> done = confirmed(sh, batch, said, CleanupRecord.DISABLED);
        // A package this batch added to the record but couldn't turn off was never changed: it leaves the record (and the
        // phone's undo list) again. Entries from earlier batches are never touched.
        boolean pruned = false;
        for (String p : added) if (!done.contains(p)) { record.packages.remove(p); pruned = true; }
        if (pruned) CleanupRecord.write(recordFile, record);
        if (changed != null) for (String p : done) changed.accept(p);
        return done;
    }

    /** pm's own success line, exactly: "Package <pkg> new state: <state>". */
    static boolean said(String out, String pkg, String state) {
        if (out == null) return false;
        for (String line : out.split("\\r?\\n")) if (line.trim().equals("Package " + pkg + " new state: " + state)) return true;
        return false;
    }

    /** The packages now in {@code state}, read again from the TV; if that read fails, what pm said. */
    private static List<String> confirmed(Shell sh, List<String> batch, List<String> said, String state) {
        try {
            Map<String, String> now = states(sh, batch);
            List<String> out = new ArrayList<>(); for (String p : batch) if (state.equals(now.get(p))) out.add(p);
            return out;
        } catch (IOException e) { return said; }
    }

    /** A new record: this TV's ids, Home and animation scales now, as tools/recovery.py --save reads them. */
    static CleanupRecord snapshot(Shell sh, String fingerprint, String home, String created) throws IOException {
        CleanupRecord r = new CleanupRecord();
        r.created = created; r.fingerprint = fingerprint; r.home = home;
        r.serial = CleanupRecord.usableId(sh.run(AdbCommands.SERIAL));
        r.androidId = CleanupRecord.usableId(sh.run(AdbCommands.ANDROID_ID));
        for (String key : AdbCommands.SCALES) { String v = CleanupRecord.scale(sh.run(AdbCommands.scale(key))); r.settings.put(key, v == null ? "null" : v); }
        return r;
    }

    /**
     * Turns back on the given packages, but only those the record says were on before Unified TV turned them off; a
     * package that was already off before stays off (as tools/restore.sh). Returns the ones the TV reports as on.
     */
    static List<String> turnBackOn(Shell sh, CleanupRecord record, Collection<String> which, Progress progress, Consumer<String> changed) throws IOException {
        List<String> batch = new ArrayList<>();
        for (String p : which) if (record != null && CleanupRecord.ENABLED.equals(record.packages.get(p))) batch.add(p);
        List<String> said = new ArrayList<>();
        for (int n = 0; n < batch.size(); n++) {
            String p = batch.get(n);
            if (progress != null) progress.step(n, batch.size(), p);
            if (said(sh.run(AdbCommands.enable(p)), p, "enabled")) said.add(p);
        }
        if (progress != null) progress.step(batch.size(), batch.size(), null);
        List<String> done = batch.isEmpty() ? said : confirmed(sh, batch, said, CleanupRecord.ENABLED);
        if (changed != null) for (String p : done) changed.accept(p);
        return done;
    }

    /** As tools/adb-closed.py: true when nothing answers on this TV's port (refused, or no answer in time). */
    static boolean portClosed(int port, int timeoutMs) {
        try (Socket s = new Socket()) { s.connect(new InetSocketAddress(AdbLink.loopback(), port), timeoutMs); return false; }
        catch (IOException e) { return true; }   // refused, or no answer in time: closed
    }
}
