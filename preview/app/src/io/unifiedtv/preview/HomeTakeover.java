package io.unifiedtv.preview;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import io.unifiedtv.adb.AdbCommands;
import io.unifiedtv.adb.AdbException;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * OD-28 (owner, 27 October 2026): "Take over the TV's own Home button, with a way back in Settings; otherwise everything
 * stays in Unified TV, and Back never drops you out of Unified TV." Launcher app only. The steps, over the same session-only
 * link to this TV's own ADB as the Optional clean-up (Cleanup.Shell; HomeTakeoverActivity shows what these return):
 *
 * <ol>
 * <li>{@link #check}: read-only. This TV's firmware (it must be this TV), which tested list covers it, what the Home
 *     button opens now and which apps answer Home. Unified TV's own Home entry must already be one of them.</li>
 * <li>{@link #gentle}: Android's own route, {@code cmd package set-home-activity} naming Unified TV's own Home entry, and
 *     nothing else; then Home is read again.</li>
 * <li>{@link #takeOver}: only after the owner confirms, only when Unified TV's Home entry is a confirmed candidate, only on
 *     a TV a tested list covers, and only for the package that is the TV's own Home now and is the list's stock launcher
 *     ({@link #refusal}). The recovery record (CleanupRecord, the clean-up's own) is written first; then
 *     {@code pm disable-user --user 0 <that package>}, never uninstall; then set-home-activity again and Home read again.
 *     If another app now answers Home, that is reported and nothing more is turned off.</li>
 * <li>{@link #giveBack}: Return to the original TV interface. {@code pm enable} the package, only when the record says
 *     this flow turned it off; the previous Home set again; Unified TV's Home entry withdrawn; Home read again.</li>
 * </ol>
 * Never: accessibility key interception, reading or reassigning keys, root, uninstalling, or turning off any other package.
 * The general clean-up keeps refusing every Home app (CleanupPolicy); only this flow can touch the TV's own Home.
 */
final class HomeTakeover {
    private HomeTakeover() {}

    /** How long the owner has to press Home on the remote before the check says it didn't work. */
    static final long CONFIRM_MS = 20_000;
    static final String PREFS = "unified-tv-home-takeover", PENDING = "pending", ARRIVED = "arrived";

    // --- words (tests compare them)
    static final String UNKNOWN_TV = "This TV isn't one the tested lists cover, so Unified TV won't turn its own Home off. Nothing was turned off.";
    static final String NOT_CANDIDATE = "This TV doesn't list Unified TV as a Home screen yet, so nothing was turned off. Try again in a moment.";
    static final String CHOOSER_NOW = "Android has no Home screen set, so nothing was turned off. Press Home on your remote and choose Unified TV.";
    static final String RECORD_SAYS_OFF = "The undo record says the TV's own Home was already off before, so Unified TV can't record a safe way back. Nothing was turned off.";
    static final String UNINSTALL_WORDS = "Before uninstalling or replacing this build, choose Return to the original TV interface.";
    /** The TV-only route back, for the TV's own Home called {@code label} in its Settings. */
    static String tvOnlySteps(String label) { return "On the TV: Settings > Apps > See all apps > Show system apps > " + label + " > Enable."; }
    /** The computer's way back. */
    static String pcFix(String pkg) { return "adb shell pm enable " + pkg; }
    static String notStock(String pkg) { return "The Home button opens " + pkg + ", which isn't this TV's own Home on the tested list, so Unified TV won't turn it off. Nothing was turned off."; }

    /** The name the TV's own Settings shows for a stock Home app. */
    static String label(String pkg) {
        if ("com.google.android.tvlauncher".equals(pkg)) return "Android TV Home";
        if ("com.google.android.apps.tv.launcherx".equals(pkg)) return "Google TV Home";
        if ("com.google.android.leanbacklauncher".equals(pkg)) return "Android TV Launcher";
        if ("com.tcl.tvlauncher".equals(pkg)) return "TCL Home";
        return pkg;
    }

    /** Unified TV's own Home entry on this TV, as the TV prints it. */
    static String entry(String self) { return self + "/" + AdbCommands.HOME_ENTRY; }

    /** "pkg/.Main" → "pkg/pkg.Main", so a component compares the same however the TV printed it. */
    static String full(String component) {
        if (component == null) return null;
        int slash = component.indexOf('/');
        if (slash < 0) return component;
        String pkg = component.substring(0, slash), cls = component.substring(slash + 1);
        return cls.startsWith(".") ? pkg + "/" + pkg + cls : component;
    }
    static String pkgOf(String component) { int slash = component == null ? -1 : component.indexOf('/'); return slash > 0 ? component.substring(0, slash) : null; }

    /** Every component in a --brief query's answer ("com.example/.Main"), in order. */
    static List<String> components(String out) {
        List<String> found = new ArrayList<>();
        if (out != null) for (String line : out.split("\\r?\\n")) {
            String t = line.trim();
            if (CleanupRecord.home(t) != null && AdbCommands.isPackage(pkgOf(t)) && !found.contains(t)) found.add(t);
        }
        return found;
    }

    /** What the Home button opens now, and which apps answer Home: read from the TV. */
    static final class Reading {
        /** The component the Home button opens; null when Android would ask (its chooser) or nothing answers. */
        String home;
        final List<String> claims = new ArrayList<>();
        String homePackage() { return pkgOf(home); }
        boolean ours(String self) { return self.equals(homePackage()); }
        /** Unified TV's own Home entry is one of the apps that answer Home. */
        boolean candidate(String self) { String mine = full(entry(self)); for (String c : claims) if (mine.equals(full(c))) return true; return false; }
        /** The claimant belonging to {@code pkg}, as the TV printed it. */
        String claimOf(String pkg) { for (String c : claims) if (pkg.equals(pkgOf(c))) return c; return null; }
    }

    static Reading read(Cleanup.Shell sh) throws IOException {
        Reading r = new Reading();
        r.home = Cleanup.homeComponent(sh.run(AdbCommands.HOME));
        r.claims.addAll(components(sh.run(AdbCommands.HOME_CLAIMS)));
        return r;
    }

    static final class Check {
        String fingerprint = "";
        CleanupLists.Match match;
        Reading before;
    }

    /** Read-only. {@code expected} is this TV's own fingerprint: another answer means another device, and nothing more is read. */
    static Check check(Cleanup.Shell sh, String lists, String expected, String self) throws IOException {
        Check c = new Check();
        c.fingerprint = sh.run(AdbCommands.FINGERPRINT).trim();
        if (!c.fingerprint.equals(expected == null ? "" : expected.trim())) throw new AdbException(AdbException.Kind.NOT_THIS_TV, "Another device answered.");
        c.match = CleanupLists.match(lists, c.fingerprint);
        c.before = read(sh);
        return c;
    }

    /** Android's own route: set Unified TV's own Home entry as Home (only when it is a candidate), then read Home again. */
    static Reading gentle(Cleanup.Shell sh, Check c, String self) throws IOException {
        if (!c.before.candidate(self)) return c.before;
        sh.run(AdbCommands.setHome(entry(self)));
        return read(sh);
    }

    /**
     * Why the TV's own Home may not be turned off here, in plain words; null when it may (after the owner confirms). Only
     * a tested-list TV, only with Unified TV's Home entry a confirmed candidate, only the package the Home button opens now,
     * and only when that is the stock launcher a tested list names.
     */
    static String refusal(Check c, Reading now, String self) {
        if (c.match == null || !c.match.known()) return UNKNOWN_TV;
        if (!now.candidate(self)) return NOT_CANDIDATE;
        String pkg = now.homePackage();
        if (pkg == null) return CHOOSER_NOW;
        if (pkg.equals(self)) return null;
        if (!c.match.stockLaunchers.contains(pkg) || !AdbCommands.STOCK_HOMES.contains(pkg)) return notStock(pkg);
        return null;
    }

    /** The TV changed since the check, or the record forbids it: nothing was changed. The message is plain words, or null. */
    static final class Changed extends IllegalStateException { Changed(String words) { super(words); } }

    enum Outcome { TAKEN, OTHER_HOME, CHOOSER, FAILED, RETURNED }
    static final class Result {
        final Outcome outcome; final Reading after; final String pkg;
        Result(Outcome outcome, Reading after, String pkg) { this.outcome = outcome; this.after = after; this.pkg = pkg; }
        /** The app that answers Home now instead of Unified TV (OTHER_HOME). */
        String other() { return after == null ? null : after.homePackage(); }
    }

    /**
     * Turns the TV's own Home off, exactly as {@link #refusal} allows, after reading everything again. The record goes to
     * disk before the change; a failed write stops here with the TV untouched. Nothing else is ever turned off: if another
     * app answers Home afterwards, the result says so (OTHER_HOME) and the owner chooses.
     */
    static Result takeOver(Cleanup.Shell sh, File recordFile, Check c, String self, String created) throws IOException {
        Reading now = read(sh);
        String why = refusal(c, now, self);
        if (why != null || now.ours(self)) throw new Changed(why);
        String stock = now.homePackage();
        Set<String> installed = Cleanup.packages(sh.run(AdbCommands.PACKAGES)), disabled = Cleanup.packages(sh.run(AdbCommands.DISABLED));
        if (!CleanupRecord.ENABLED.equals(Cleanup.state(stock, installed, disabled))) throw new Changed(null);
        // The record first, the clean-up's own: the first state wins, so an entry already there is never overwritten.
        CleanupRecord record = CleanupRecord.read(recordFile);
        if (record == null) record = Cleanup.snapshot(sh, c.fingerprint, now.home, created);
        String earlier = record.packages.get(stock);
        if (earlier != null && !CleanupRecord.ENABLED.equals(earlier)) throw new Changed(RECORD_SAYS_OFF);
        boolean added = record.add(Collections.singletonMap(stock, CleanupRecord.ENABLED)).contains(stock);
        record.takeoverLauncher = stock; record.takeoverPrevious = now.home;
        CleanupRecord.write(recordFile, record);
        sh.run(AdbCommands.disable(stock));
        // What changed is what the TV reports now, not what pm printed.
        if (!CleanupRecord.DISABLED.equals(Cleanup.states(sh, Collections.singletonList(stock)).get(stock))) {
            if (added) record.packages.remove(stock);
            record.takeoverLauncher = null; record.takeoverPrevious = null;
            CleanupRecord.write(recordFile, record);
            return new Result(Outcome.FAILED, now, stock);
        }
        Reading mid = read(sh);
        if (mid.candidate(self)) sh.run(AdbCommands.setHome(entry(self)));   // again, as Android's own choice, whatever answers now
        Reading after = read(sh);
        if (after.ours(self)) return new Result(Outcome.TAKEN, after, stock);
        return new Result(after.homePackage() == null ? Outcome.CHOOSER : Outcome.OTHER_HOME, after, stock);
    }

    /**
     * Return to the original TV interface over the link: the TV's own Home on again (only if this record says this flow
     * turned it off), the previous Home set again (a stock Home's own component only), Unified TV's Home entry withdrawn
     * ({@code withdraw}, only once the TV's own Home is confirmed on again), and Home read again.
     */
    static Result giveBack(Cleanup.Shell sh, File recordFile, CleanupRecord record, Runnable withdraw) throws IOException {
        if (record == null || record.takeoverLauncher == null) throw new Changed(null);
        String stock = record.takeoverLauncher;
        if (CleanupRecord.ENABLED.equals(record.packages.get(stock))) sh.run(AdbCommands.enable(stock));
        if (!CleanupRecord.ENABLED.equals(Cleanup.states(sh, Collections.singletonList(stock)).get(stock))) return new Result(Outcome.FAILED, null, stock);
        String previous = record.takeoverPrevious;
        if (previous == null || !stock.equals(pkgOf(previous))) previous = read(sh).claimOf(stock);
        if (previous != null && AdbCommands.homeTarget(previous)) sh.run(AdbCommands.setHome(previous));
        // The marker goes before the entry: DefaultHome.withdraw refuses while the record says the TV's own Home is off.
        record.takeoverLauncher = null; record.takeoverPrevious = null;
        CleanupRecord.write(recordFile, record);
        if (withdraw != null) withdraw.run();
        return new Result(Outcome.RETURNED, read(sh), stock);
    }

    /**
     * The record is gone (Unified TV's storage cleared, or a damaged file) but a stock Home is still off: turns that one
     * package back on, only when it is a known stock Home and the TV reports it installed and off, sets it as Home, then
     * {@code withdraw} (only once it is on again), and Home read again. Nothing else is ever enabled here.
     */
    static Result restoreStock(Cleanup.Shell sh, String stock, Runnable withdraw) throws IOException {
        if (!AdbCommands.STOCK_HOMES.contains(stock)) throw new Changed(null);
        Set<String> installed = Cleanup.packages(sh.run(AdbCommands.PACKAGES)), disabled = Cleanup.packages(sh.run(AdbCommands.DISABLED));
        if (!CleanupRecord.DISABLED.equals(Cleanup.state(stock, installed, disabled))) throw new Changed(null);
        sh.run(AdbCommands.enable(stock));
        if (!CleanupRecord.ENABLED.equals(Cleanup.states(sh, Collections.singletonList(stock)).get(stock))) return new Result(Outcome.FAILED, null, stock);
        String previous = read(sh).claimOf(stock);
        if (previous != null && AdbCommands.homeTarget(previous)) sh.run(AdbCommands.setHome(previous));
        if (withdraw != null) withdraw.run();
        return new Result(Outcome.RETURNED, read(sh), stock);
    }

    /** A known stock Home that is installed but turned off on this TV, as Android tells this app; null when there is none. */
    static String disabledStock(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String pkg : new TreeSet<>(AdbCommands.STOCK_HOMES)) {
            try {
                int state = pm.getApplicationEnabledSetting(pkg);
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) return pkg;
            } catch (RuntimeException notInstalled) { /* not on this TV */ }
        }
        return null;
    }

    /** The stock Home that is off: the one the record names, else one Android reports off (record lost). */
    static String stockToRestore(Context c) { String r = turnedOff(c); return r != null ? r : disabledStock(c); }

    /** No record: {@code stock} is on again (turned on in the TV's settings, or with adb): Unified TV's Home entry goes. */
    static boolean reconcileLost(Context c, String stock) {
        try { int state = c.getPackageManager().getApplicationEnabledSetting(stock);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) return false; }
        catch (RuntimeException e) { return false; }
        return DefaultHome.withdraw(c);
    }

    // --- on this TV, without the link
    static File recordFile(Context c) { return CleanupRecord.file(c.getNoBackupFilesDir()); }
    /** The package this flow turned off, while it is still off as far as Unified TV knows; null otherwise. */
    static String turnedOff(Context c) {
        try { CleanupRecord r = CleanupRecord.read(recordFile(c)); return r == null ? null : r.takeoverLauncher; } catch (RuntimeException e) { return null; }
    }
    /** Unified TV turned the TV's own Home off (the uninstall guard, the crash screen, and withdraw's refusal). */
    static boolean stockOff(Context c) { return turnedOff(c) != null; }

    /**
     * The TV's own Home was turned back on without Unified TV (in the TV's settings, or with adb from a computer): the
     * Home button resolves to it again. Then the marker goes, and Unified TV's Home entry is withdrawn. True when it did.
     */
    static boolean reconcile(Context c) {
        String stock = turnedOff(c);
        if (stock == null) return false;
        String resolved = null;
        try { ResolveInfo r = c.getPackageManager().resolveActivity(DefaultHome.homeIntent(), PackageManager.MATCH_DEFAULT_ONLY); resolved = r == null || r.activityInfo == null ? null : r.activityInfo.packageName; }
        catch (RuntimeException ignored) { /* unknown: leave it */ }
        if (!stock.equals(resolved)) return false;
        File f = recordFile(c);
        CleanupRecord r = CleanupRecord.read(f);
        if (r == null) return false;
        r.takeoverLauncher = null; r.takeoverPrevious = null;
        try { CleanupRecord.write(f, r); } catch (IOException e) { return false; }
        DefaultHome.withdraw(c);
        return true;
    }

    // --- "Press the Home button on your remote now": Home tells this when it comes to the front
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }
    static long now() { return SystemClock.elapsedRealtime(); }
    /** Starts the 20-second window. */
    static void expect(Context c) { prefs(c).edit().putLong(PENDING, now()).remove(ARRIVED).commit(); }
    static long pending(Context c) { return prefs(c).getLong(PENDING, 0); }
    /** When Unified TV's Home came to the front by the Home button inside the window; 0 if it hasn't. */
    static long arrived(Context c) { return prefs(c).getLong(ARRIVED, 0); }
    static void cancel(Context c) { prefs(c).edit().remove(PENDING).remove(ARRIVED).commit(); }

    static final String WORKED = "Done. The Home button now opens Unified TV.";
    static final String DIDNT = "Pressing Home didn't open Unified TV. To undo, open Settings > Appearance & Home > Return to the original TV interface.";

    /**
     * Unified TV's Home came to the front ({@code byHome}: by the Home button) while the check waits: it worked. Opened
     * any other way once the 20 seconds are over: the Home button didn't open it. Returns the words for Home to show, or
     * null (nothing to say).
     */
    static String homeShown(Context c, boolean byHome) {
        long since = pending(c);
        if (since <= 0) return null;
        long t = now();
        if (byHome) { prefs(c).edit().remove(PENDING).putLong(ARRIVED, t).commit(); return WORKED; }
        if (t < since || t - since > CONFIRM_MS) { cancel(c); return DIDNT; }
        return null;
    }
}
