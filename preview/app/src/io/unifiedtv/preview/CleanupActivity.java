package io.unifiedtv.preview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.unifiedtv.adb.AdbException;
import io.unifiedtv.adb.AdbKey;
import io.unifiedtv.adb.AdbLink;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings > Updates & About > Advanced > Optional clean-up (SPEC "Optional TV Performance & Cleanup", AGENTS.md rule 9;
 * owner, 27 October 2026: "all the features to clean the TV of the bloatware, in the menu, to turn on optionally").
 *
 * <p>Adults only, behind the PIN when one is set, off by default: opening it and leaving changes nothing and nothing asks
 * again (AT-18). A TV whose firmware isn't on a tested list gets read-only guidance and nothing else (AT-32). Otherwise:
 * <ol>
 * <li>A read-only check through the TV's own network debugging (AdbLink: this TV only, for as long as this page is open).
 *     The first time, the TV asks "Allow USB debugging?"; the page says to tick "Always allow from this computer" and
 *     choose OK. The check lists each tested package: installed or not, on or off, what it is, its risk, and whether it is
 *     recommended or only "optional, your choice". Nothing changes (AT-20).</li>
 * <li>Every app has its own switch, all off to start with. A summary lists exactly what will change; only its confirm
 *     button changes anything. The recovery record is written before the first change (CleanupRecord), then apps are
 *     turned off one by one with progress, and each change goes in the event log (package name only).</li>
 * <li>Afterwards the states are read again; each app can be turned back on, or all of them ("Undo all"); "Check the TV
 *     still works" lists what to try (AT-21); the undo files can go to the phone (CleanupPhone) for a computer restore.</li>
 * <li>Finally the page closes the link and guides the owner to turn network debugging off, then checks port 5555 is
 *     closed, as tools/adb-closed.py does. No ADB stays open because of Unified TV.</li>
 * </ol>
 * Never: uninstall, hide, clear data, root, reboot, or any command outside io.unifiedtv.adb.AdbCommands. A settings
 * screen, so the glass sidebar over a still Home (OD-19).
 */
public final class CleanupActivity extends GlassScreen {
    /** The open link: what runs commands, and what closes it. */
    static final class Opened { final Cleanup.Shell shell; final Closeable link; Opened(Cleanup.Shell shell, Closeable link) { this.shell = shell; this.link = link; } }
    interface Opener { Opened open(Context c, AdbLink.Prompt prompt) throws Exception; }
    /** Tests only: a simulated TV, a same-thread executor and the TV's fingerprint. Production never sets them. */
    static volatile Opener openerForTests;
    static volatile Executor backgroundForTests;
    static volatile String fingerprintForTests;
    static final int PORT_CHECK_MS = 3000;

    static final String TITLE = "Optional clean-up";
    static final String ADB_WORDS = "To read and change this TV's apps, Unified TV uses the TV's network debugging, which is already on. "
        + "It talks only to this TV, and only while this page is open.";
    static final String PROMPT_WORDS = "Look at the TV: a box asks \"Allow USB debugging?\". Tick \"Always allow from this computer\", then choose OK. "
        + "It names this TV's own key, unifiedtv@tv.";
    static final String UNKNOWN_WORDS = "This TV isn't one the clean-up lists were tested on, so nothing can be changed here, and nothing has been. "
        + "Turning off the wrong app can stop the remote, the picture, the network or updates. If you want to tidy this TV, do it from a computer "
        + "with a list made and tested for this exact model, which saves a backup first and can be undone (docs/INSTALLATION_AND_RECOVERY.md). "
        + "Never turn off the TV's Home, Settings, Live TV and inputs, remote, network, keyboard, accessibility or updates.";

    private ExecutorService worker;
    private volatile Cleanup.Shell shell;
    private volatile Closeable link;
    private volatile boolean busy, changing, stopped;
    /** Bumped when the owner leaves a page while it is still reading, so its late result doesn't replace the page they went to. */
    private volatile int generation;
    private Cleanup.Check check;
    private final LinkedHashSet<String> chosen = new LinkedHashSet<>();
    private Map<String, String> after = new LinkedHashMap<>();
    private CleanupPhone phone;
    private Runnable back;
    private TextView progress, prompt, portLine;
    private final List<Button> review = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        worker = Executors.newSingleThreadExecutor();
        screen(TITLE, "Updates & About > Advanced", null);
        if (Profiles.kids(this)) { card(); text("Optional clean-up is for Adults. Switch to Adults first.", 20); button("Back", this::finish).requestFocus(); return; }
        card(); text("Adult access is needed for this.", 20);
        button("Unlock", () -> EditLock.ask(this, this::intro)); button("Back", this::finish);
        EditLock.ask(this, this::intro);   // no PIN set: straight in
    }

    // ------------------------------------------------------------------ pages
    /** A new page in the same sheet; Back on it runs {@code back} (null: Back closes the clean-up). */
    private void screen(String title, String sub, Runnable back) {
        if (phone != null) { phone.stop(); phone = null; }
        this.back = back; progress = null; prompt = null; portLine = null; review.clear();
        wide(false); page(title, sub);
    }

    /** The first page: what this is, which TV this is, and the way in. Reading Build here touches nothing on the TV. */
    void intro() {
        screen(TITLE, "Turn off the apps you don't use. Off until you choose.", null);
        String fp = fingerprint();
        CleanupLists.Match m = CleanupLists.match(CleanupLists.load(this), fp);
        card();
        text("Your TV maker put apps on this TV that show adverts, report what you watch, or just take memory. Here you can turn off the ones you choose. They are turned off, never removed, and each one can be turned back on.", 20);
        text("Nothing changes until you choose each app and confirm. Skipping this changes nothing, and Unified TV won't ask again.", 18);
        group("This TV"); card();
        text(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE, 18);
        text("Firmware: " + fp, 16);
        text(m.describe(), 18);
        if (!m.known()) { group("What you can do"); card(); text(UNKNOWN_WORDS, 17); button("Back", this::finish).requestFocus(); return; }
        group("Before you start"); card();
        text(ADB_WORDS, 17);
        text("The first time, the TV asks \"Allow USB debugging?\". Tick \"Always allow from this computer\", then choose OK.", 17);
        Button start = button("Start the read-only check", this::checkNow);
        CleanupRecord rec = record();
        if (rec != null && !rec.changed().isEmpty()) {
            group("Earlier clean-up"); card();
            text(rec.changed().size() + " apps were turned off by Unified TV" + (rec.created.length() >= 10 ? " on " + rec.created.substring(0, 10) : "") + ".", 18);
            button("Turn apps back on", this::undoPage);
            button("Save the undo files on your phone", this::phonePage);
        }
        button("Not now", this::finish);
        start.requestFocus();
    }

    private void checkNow() {
        screen("Reading this TV", "Read-only: nothing changes", this::intro);
        card(); progress = busy("Connecting to this TV…"); prompt = text("", 17);
        work(false, sh -> {
            Cleanup.Check c = Cleanup.check(sh, CleanupLists.load(this), fingerprint(), getPackageName(), LiveTvProtection.livePackage(getApplicationContext()));
            return () -> { check = c; chosen.clear(); results(); };
        });
    }

    /** The read-only result: every listed app with its own switch, all off. */
    void results() {
        screen("What's on this TV", "Read-only check: nothing has changed", this::intro); wide(true);
        card();
        text(Build.MANUFACTURER + " " + Build.MODEL + " · firmware " + check.fingerprint, 17);
        text(check.match.describe(), 17);
        if (check.openToAll()) text("Note: this TV lets any device on your network use network debugging without asking (ro.adb.secure=0). Turn network debugging off when you're done.", 16);
        if (!check.match.known()) { text(UNKNOWN_WORDS, 17); button("Back", this::intro).requestFocus(); return; }
        int offered = 0, recommended = 0;
        for (Cleanup.Item i : check.items) if (i.canTurnOff()) { offered++; if (i.recommended) recommended++; }
        text(offered == 0 ? "Nothing on the tested list is turned on on this TV, so there is nothing to do."
            : offered + " apps on the tested list can be turned off. Every switch starts off: turn on only the ones you want gone.", 18);
        if (recommended > 0) button("Choose the recommended ones (" + recommended + ")", () -> { for (Cleanup.Item i : check.items) if (i.recommended && i.canTurnOff()) chosen.add(i.pkg()); results(); });
        if (offered > 0) review.add(button(reviewWords(), this::summary));
        Map<String, List<Cleanup.Item>> groups = new LinkedHashMap<>();
        for (Cleanup.Item i : check.items) groups.computeIfAbsent(i.entry.group, k -> new ArrayList<>()).add(i);
        for (Map.Entry<String, List<Cleanup.Item>> g : groups.entrySet()) {
            group(g.getKey()); card();
            for (Cleanup.Item i : g.getValue()) {
                if (i.canTurnOff()) {
                    CheckBox s = switchRow("Turn off: " + i.title(), chosen.contains(i.pkg()), on -> { if (on) chosen.add(i.pkg()); else chosen.remove(i.pkg()); for (Button r : review) r.setText(reviewWords()); });
                    s.setContentDescription("Turn off " + i.title() + ". " + details(i));
                    text(details(i), 16);
                } else text(i.title() + " (" + i.pkg() + "): " + status(i), 16);
            }
        }
        if (offered > 0) review.add(button(reviewWords(), this::summary));
        button("Close and change nothing", this::finish);
        focusFirst();
    }
    private String reviewWords() { return chosen.isEmpty() ? "Review my choices (none yet)" : "Review my choices (" + chosen.size() + ")"; }

    static String details(Cleanup.Item i) {
        StringBuilder b = new StringBuilder(i.pkg()).append(" · on now");
        if (!i.version.isEmpty()) b.append(" · version ").append(i.version);
        b.append(" · ").append(i.risk.words()).append(" · ").append(i.recommended ? "Recommended" : "Optional, your choice");
        if (i.warning != null) b.append("\n").append(i.warning);
        if (!i.entry.thisFirmware) b.append("\nTested on a ").append(i.entry.list).append(", not on this exact firmware.");
        return b.toString();
    }
    static String status(Cleanup.Item i) {
        if (i.blocked != null) return "kept. " + i.blocked;
        if (CleanupRecord.ABSENT.equals(i.state)) return "not on this TV.";
        if (CleanupRecord.DISABLED.equals(i.state)) return "already off.";
        return "on.";
    }

    /** Exactly what will change, and the one button that changes it. */
    void summary() {
        if (chosen.isEmpty()) { note("Turn on the switch of at least one app first."); return; }
        screen("Check before anything changes", chosen.size() + (chosen.size() == 1 ? " app" : " apps") + " will be turned off", this::results);
        card();
        text("These apps will be turned off. They stay on the TV and can each be turned back on:", 18);
        for (String p : chosen) { Cleanup.Item i = check.item(p); text("• " + i.title() + " (" + p + ")" + (i.warning != null ? ". " + i.warning : ""), 17); }
        text("Nothing else changes. Before the first one, Unified TV saves how they are now, so you can undo it here, from a computer, or in the TV's own settings.", 17);
        button("Turn off " + (chosen.size() == 1 ? "this app" : "these " + chosen.size() + " apps"), this::applyNow).requestFocus();
        button("Change my choices", this::results);
    }

    private void applyNow() {
        List<String> batch = new ArrayList<>(chosen);
        screen("Turning apps off", "One at a time", null);
        card(); progress = busy("Saving the undo record first…");
        File file = recordFile(); String created = created(); Context app = getApplicationContext();
        work(true, sh -> {
            Cleanup.apply(sh, file, check, batch, created, (n, total, p) -> say(p == null ? "Checking the result…" : "Turning off " + (n + 1) + " of " + total + ": " + title(p)),
                p -> EventLog.record(app, EventLog.SETTINGS, "Clean-up turned off " + p));
            Map<String, String> states = Cleanup.states(sh, batch);
            return () -> { chosen.clear(); after = states; resultPage("Done: here's what changed"); };
        });
    }

    /** After a batch or an undo: each app's state now, a way back for each, and the checks to do. */
    void resultPage(String title) {
        screen(title, "Read again from the TV", this::intro);
        CleanupRecord rec = record();
        card();
        for (Map.Entry<String, String> e : after.entrySet())
            text(title(e.getKey()) + " (" + e.getKey() + "): " + (CleanupRecord.DISABLED.equals(e.getValue()) ? "off" : CleanupRecord.ENABLED.equals(e.getValue()) ? "on" : "not on this TV"), 17);
        Button checks = button("Check the TV still works", this::checklist);
        List<String> off = new ArrayList<>();
        if (rec != null) for (String p : rec.changed()) if (CleanupRecord.DISABLED.equals(after.get(p))) off.add(p);
        if (!off.isEmpty()) {
            group("Turn back on"); card();
            for (String p : off) button("Turn back on: " + title(p), () -> undo(Collections.singletonList(p)));
            button("Undo all (" + off.size() + ")", () -> undo(off));
        }
        group("Keep a copy"); card();
        button("Save the undo files on your phone", this::phonePage);
        button("Finish", this::finishPage);
        checks.requestFocus();
    }

    /** From the first page: what Unified TV turned off before, read from the TV now. */
    void undoPage() {
        CleanupRecord rec = record();
        if (rec == null) { intro(); return; }
        screen("Turn apps back on", "Reading this TV", this::intro);
        card(); progress = busy("Connecting to this TV…"); prompt = text("", 17);
        work(false, sh -> { Map<String, String> states = Cleanup.states(sh, rec.changed()); return () -> { after = states; resultPage("Apps Unified TV turned off"); }; });
    }

    private void undo(List<String> which) {
        CleanupRecord rec = record();
        screen("Turning apps back on", "One at a time", null);
        card(); progress = busy("Turning apps back on…");
        Context app = getApplicationContext();
        work(true, sh -> {
            Cleanup.turnBackOn(sh, rec, which, (n, total, p) -> say(p == null ? "Checking the result…" : "Turning on " + (n + 1) + " of " + total + ": " + title(p)),
                p -> EventLog.record(app, EventLog.SETTINGS, "Clean-up turned back on " + p));
            Map<String, String> states = Cleanup.states(sh, rec == null ? which : rec.changed());
            return () -> { after = states; resultPage("Turned back on"); };
        });
    }

    /** AT-21: what to try before putting the remote down. */
    void checklist() {
        screen("Check the TV still works", "Try each of these now", () -> resultPage("Done: here's what changed"));
        card();
        String[] steps = {
            "Open each streaming app you use and play something for a few seconds.",
            "Press Home, Back, the volume and app buttons on the remote, and try its microphone if you use voice.",
            "Open NordVPN and check it connects. Live TV stays locked until Unified TV has checked the VPN itself.",
            "Switch to each HDMI input: check the picture and the sound, including the soundbar.",
            "If you use the doorbell, check its alerts still appear.",
            "Put the TV in standby with the remote, wait a few seconds, and wake it again."};
        for (int n = 0; n < steps.length; n++) text((n + 1) + ". " + steps[n], 18);
        text("If anything is wrong, choose Undo all. Every app comes back as it was.", 17);
        button("Everything works", this::finishPage).requestFocus();
        CleanupRecord rec = record();
        if (rec != null && !rec.changed().isEmpty()) button("Something is wrong: undo all", () -> undo(rec.changed()));
    }

    /** The end: the link closes, and the owner is guided to turn network debugging off and to check it is off. */
    void finishPage() {
        closeLink();
        screen("Turn off network debugging", "Recommended when you're done", null);
        card();
        text("Unified TV has closed its link. To close the TV's network debugging too:", 18);
        text("1. On the TV, open Settings > System > Developer options (on some TCL TVs: Settings > Device Preferences > Developer options).", 17);
        text("2. Turn off USB debugging. That also turns off network debugging.", 17);
        text("3. Come back here and choose Check that it's off.", 17);
        text("If you use ADB from a computer, you can leave it on for now; turn it off when you're done.", 16);
        Button open = button("Open the TV's settings", this::openDeveloperSettings);
        button("Check that it's off", this::checkPort);
        portLine = text("", 18);
        button("Done", this::finish);
        open.requestFocus();
    }
    private void openDeveloperSettings() {
        for (String action : new String[]{Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Settings.ACTION_SETTINGS}) {
            try { startActivity(new Intent(action)); return; } catch (ActivityNotFoundException | SecurityException ignored) { /* the next one */ }
        }
        note("The TV's settings couldn't be opened from here. Use the Settings button on the remote.");
    }
    private void checkPort() {
        if (portLine != null) portLine.setText("Checking…");
        TextView line = portLine;
        background(() -> { boolean closed = Cleanup.portClosed(AdbLink.PORT, PORT_CHECK_MS);
            runOnUiThread(() -> { if (line != null) line.setText(closed ? "Network debugging is off: nothing answers on port " + AdbLink.PORT + "."
                : "Still on: port " + AdbLink.PORT + " answers. Turn off USB debugging in Developer options, then check again."); }); });
    }

    /** The undo files on the phone, by QR code, as every phone setup screen. */
    void phonePage() {
        CleanupRecord rec = record();
        if (rec == null) { note("There is nothing to undo yet."); return; }
        Runnable previous = back;
        screen("Save the undo files on your phone", "So everything can be put back without Unified TV", previous == null ? this::intro : previous);
        card();
        text("1. " + PhoneService.SCAN, 17);
        text("2. On your phone, tap Download the undo record, then Download the list. Keep the two files together.", 17);
        text("3. With them, a computer can put every app back with the project's restore tool, even if Unified TV is gone.", 17);
        File file = recordFile();
        phone = CleanupPhone.start(this, () -> { CleanupRecord r = CleanupRecord.read(file); return r == null ? null : r.json(); },
            () -> { CleanupRecord r = CleanupRecord.read(file); return r == null ? null : r.listText(); }, p -> { if (progress != null) progress.setText(p.status()); });
        if (!phone.over() && phone.link() != null) {
            FrameLayout holder = new FrameLayout(this); QrView qr = new QrView(this); int side = px(300);
            qr.setText(phone.link(), SetupPages.QR_LABEL);
            holder.addView(qr, new FrameLayout.LayoutParams(side, side, Gravity.CENTER));
            column.addView(holder, new LinearLayout.LayoutParams(-1, side + px(12)));
            text(SetupPages.fallback(phone.origin(), phone.code()), 14).setTypeface(android.graphics.Typeface.MONOSPACE);
            text("The undo record contains this TV's serial number and Android ID, so a computer can check it's the right TV. Keep it private. It never holds a password or key.", 15);
        }
        progress = text(phone.status(), 19);
        button("Back", this::onBackPressed).requestFocus();
    }

    // ------------------------------------------------------------------ the link and the work
    private interface Job { Runnable run(Cleanup.Shell sh) throws Exception; }

    /** Runs a job with the link (opening it first), then its result on the page. {@code change}: it changes the TV. */
    private void work(boolean change, Job job) {
        busy = true; changing = change; int mine = ++generation;
        background(() -> {
            Runnable ui;
            try { ui = job.run(shell()); }
            catch (AdbException e) { closeLink(); ui = () -> problem(e.kind); }
            catch (IllegalStateException e) { ui = () -> problem(null); }
            catch (Exception e) { closeLink(); ui = () -> problem(AdbException.Kind.LOST); }
            Runnable show = ui;
            runOnUiThread(() -> { if (mine != generation) return; busy = false; changing = false; if (!isFinishing() && !isDestroyed()) show.run(); if (stopped) closeLink(); });
        });
    }

    private Cleanup.Shell shell() throws Exception {
        Cleanup.Shell s = shell; if (s != null) return s;
        Opener o = openerForTests;
        if (o == null) o = CleanupActivity::openLink;
        Opened opened = o.open(this, () -> runOnUiThread(() -> {
            if (prompt != null) prompt.setText(PROMPT_WORDS);
            if (progress != null) progress.setText("Waiting for you to allow it on the TV…");
        }));
        say("Connected. Reading…");
        link = opened.link; shell = opened.shell; return opened.shell;
    }

    /** This TV's own ADB, checked to be this TV (the banner's device, then the fingerprint in Cleanup.check). */
    static Opened openLink(Context c, AdbLink.Prompt prompt) throws Exception {
        AdbKey key = AdbKeys.get(c);
        AdbLink l = AdbLink.open(key, prompt);
        String device = l.bannerProperty("ro.product.device");
        if (!device.isEmpty() && !device.equals(Build.DEVICE)) { l.close(); throw new AdbException(AdbException.Kind.NOT_THIS_TV, "Another device answered."); }
        return new Opened(l::shell, l);
    }

    private void closeLink() {
        Closeable l = link; link = null; shell = null;
        if (l != null) try { l.close(); } catch (IOException ignored) { /* already closed */ }
    }

    private void problem(AdbException.Kind kind) {
        screen("Nothing more was changed", null, this::intro);
        card();
        String words;
        if (kind == null) words = "Something changed on the TV since the check (an app, or what answers Home), so nothing was changed. Run the check again.";
        else switch (kind) {
            case CLOSED: words = "Network debugging is off on this TV. To use the clean-up, turn on USB debugging in Settings > System > Developer options, then try again. Turn it off again when you're done."; break;
            case NOT_ALLOWED: words = "The TV didn't allow Unified TV. Try again, and when the TV asks \"Allow USB debugging?\", tick \"Always allow from this computer\" and choose OK."; break;
            case NOT_THIS_TV: words = "Something other than this TV answered, so nothing was read or changed."; break;
            case PROTOCOL: words = "This TV's debugging answered in a way Unified TV doesn't understand (for example, wireless-debugging pairing only). Nothing was changed."; break;
            default: words = "The TV stopped answering. Anything already changed is in the undo record, and can be turned back on here or in the TV's settings."; break;
        }
        text(words, 18);
        button("Try again", this::intro).requestFocus();
        button("Close", this::finish);
    }

    private void say(String words) { runOnUiThread(() -> { if (progress != null) progress.setText(words); }); }
    private void background(Runnable r) { Executor e = backgroundForTests; if (e != null) e.execute(r); else worker.execute(r); }

    // ------------------------------------------------------------------ small things
    private String fingerprint() { String f = fingerprintForTests; return f != null ? f : Build.FINGERPRINT; }
    File recordFile() { return CleanupRecord.file(getNoBackupFilesDir()); }
    CleanupRecord record() { return CleanupRecord.read(recordFile()); }
    static String created() { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).format(new Date()); }
    private String title(String pkg) {
        Cleanup.Item i = check == null ? null : check.item(pkg);
        if (i != null) return i.title();
        CleanupLists.Match m = CleanupLists.match(CleanupLists.load(this), fingerprint());
        for (CleanupLists.Entry e : m.items) if (e.pkg.equals(pkg)) return CleanupPolicy.title(e);
        return pkg;
    }

    @Override public void onBackPressed() {
        if (changing) { note("Please wait: apps are being changed one at a time."); return; }
        if (busy) { generation++; busy = false; closeLink(); }   // still reading: stop, nothing was changed
        Runnable b = back; if (b != null) b.run(); else close();
    }
    @Override protected void onStart() { super.onStart(); stopped = false; }
    /** Leaving the screen closes the link (unless a step is still running: it closes when that step ends). */
    @Override protected void onStop() { super.onStop(); stopped = true; if (!busy) closeLink(); }
    @Override protected void onDestroy() { if (phone != null) { phone.stop(); phone = null; } closeLink(); worker.shutdownNow(); super.onDestroy(); }
}
