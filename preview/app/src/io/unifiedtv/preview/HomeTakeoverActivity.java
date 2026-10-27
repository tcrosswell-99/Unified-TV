package io.unifiedtv.preview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import io.unifiedtv.adb.AdbException;
import io.unifiedtv.adb.AdbLink;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OD-28, launcher app only: Settings > Appearance & Home > Take over the Home button, and Return to the original TV
 * interface (also in Updates & About > Advanced, and on Home's safe screen). Adults only, behind the PIN when one is set.
 * The glass sidebar over a still Home (OD-19). The steps are HomeTakeover's; this page only shows them and asks.
 *
 * <p>Take over: Unified TV's Home entry is switched on, then the same session-only link to this TV's own ADB as the
 * Optional clean-up (same "Allow USB debugging?" prompt, same key) reads Home and asks Android directly. If the TV keeps
 * its own Home in front, the page explains plainly and only on the owner's confirm turns that Home off (recorded first).
 * Then the link closes, the owner is guided to turn network debugging off (checked), and finally asked to press Home on
 * the remote: Home reports whether it came to the front within 20 seconds, and the way back is offered if not.
 *
 * <p>Return: when this flow turned the TV's own Home off, the link turns it back on, sets it as Home and withdraws Unified
 * TV's Home entry. When network debugging is off, the page gives the TV-only route and the computer command instead.
 * Nothing on this page calls finish() on Home.
 */
public final class HomeTakeoverActivity extends GlassScreen {
    static final String MODE = "mode", TAKE = "take", RETURN = "return";
    static final String TAKE_TITLE = "Take over the Home button", RETURN_TITLE = "Return to the original TV interface";
    static final String PREVIEW_WORDS = "This is the Unified TV preview. It never changes the Home button; the separate Unified TV app can.";
    static final String INTRO = "Make the Home button on your remote open Unified TV. Unified TV first asks Android directly. If this TV keeps its own Home in front anyway, Unified TV can turn the TV's own Home off: never removed, only after you confirm, and only on a TV the tested lists cover.";
    static final String WAY_BACK = "The way back is always in Settings > Appearance & Home > Return to the original TV interface.";
    static final String ADB_WORDS = "Unified TV uses this TV's network debugging, which must already be on. It talks only to this TV, and only while this page is open. "
        + "The first time, the TV asks \"Allow USB debugging?\": tick \"Always allow from this computer\", then choose OK.";
    static final String PRESS_HOME = "Press the Home button on your remote now.";
    static final String NO_LINK = "Network debugging is off, so Unified TV can't turn the TV's own Home back on by itself. Do one of these, then choose Check again:";

    static void open(Context c, boolean take) {
        Intent i = new Intent(c, HomeTakeoverActivity.class).putExtra(MODE, take ? TAKE : RETURN);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private ExecutorService worker;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private volatile Cleanup.Shell shell;
    private volatile Closeable link, opening;
    private volatile boolean busy, changing, stopped, destroyed;
    private volatile int generation;
    private boolean take;
    private HomeTakeover.Check check;
    private Runnable back;
    private TextView progress, prompt, portLine;
    private long waitingSince;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        worker = Executors.newSingleThreadExecutor();
        take = !RETURN.equals(getIntent().getStringExtra(MODE));
        screen(take ? TAKE_TITLE : RETURN_TITLE, "Appearance & Home", null);
        if (!DefaultHome.available(this)) { card(); text(PREVIEW_WORDS, 18); button("Back", this::finish).requestFocus(); return; }
        if (Profiles.kids(this)) { card(); text("This is for Adults. Switch to Adults first.", 20); button("Back", this::finish).requestFocus(); return; }
        card(); text("Adult access is needed for this.", 20);
        Runnable in = take ? this::takeIntro : this::returnIntro;
        button("Unlock", () -> EditLock.ask(this, in)); button("Back", this::finish);
        EditLock.ask(this, in);   // no PIN set: straight in
    }

    private void screen(String title, String sub, Runnable back) {
        clock.removeCallbacksAndMessages(null);
        this.back = back; progress = null; prompt = null; portLine = null;
        wide(false); page(title, sub);
    }

    // ------------------------------------------------------------------ take over
    void takeIntro() {
        screen(TAKE_TITLE, "Appearance & Home", null);
        card();
        text(INTRO, 20);
        text(ADB_WORDS, 17);
        text(WAY_BACK, 17);
        button("Start", this::takeNow).requestFocus();
        button("Not now", this::finish);
    }

    /** Step a: the Home entry on; b: read Home; c: Android's own route. */
    private void takeNow() {
        screen("Reading this TV", "Nothing is turned off at this step", this::takeIntro);
        card(); progress = busy("Connecting to this TV…"); prompt = text("", 17);
        String self = getPackageName();
        entryWasOn = DefaultHome.offered(this);
        getPackageManager().setComponentEnabledSetting(DefaultHome.entry(this), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        work(true, sh -> {
            HomeTakeover.Check c = HomeTakeover.check(sh, CleanupLists.load(this), fingerprint(), self);
            if (!c.before.candidate(self)) { Thread.sleep(candidateWaitMs); c.before = HomeTakeover.read(sh); }   // the entry can take a moment to register
            if (c.before.ours(self)) return () -> { check = c; taken(true); };
            if (!c.before.candidate(self)) return () -> { check = c; refused(HomeTakeover.NOT_CANDIDATE); };
            say("Asking Android to use Unified TV…");
            HomeTakeover.Reading after = HomeTakeover.gentle(sh, c, self);
            c.before = after;
            if (after.ours(self)) return () -> { check = c; EventLog.record(getApplicationContext(), EventLog.SETTINGS, "Home button: Unified TV set as Home"); taken(true); };
            String why = HomeTakeover.refusal(c, after, self);
            return () -> { check = c; if (why != null) refused(why); else explain(); };
        });
    }
    /** The Home entry was already switched on before this attempt (an earlier choice): a refusal leaves it as it was. */
    private boolean entryWasOn;
    /** Not taken over: the Home entry goes back off, unless it was on before or the TV says Unified TV is Home. */
    private void handBackEntry() { if (!entryWasOn && (check == null || !check.before.ours(getPackageName()))) DefaultHome.withdraw(this); }
    /** Tests only: how long to wait before reading the Home entry again. */
    static volatile long candidateWaitMs = 600;

    /** Nothing can be turned off here: said plainly, and the Home entry handed back (it wasn't chosen). */
    private void refused(String why) {
        closeLink();
        handBackEntry();
        screen("The Home button stays as it is", null, null);
        card(); text(why, 18);
        text("Unified TV can still open when the TV turns on: Settings > Appearance & Home > " + TurnOn.TITLE + ".", 17);
        button("Close", this::finish).requestFocus();
    }

    /** Step d, before anything changes: what will happen, the way back, and the one button that does it. */
    void explain() {
        String home = check.before.homePackage(), name = HomeTakeover.label(home);
        screen("This TV keeps its own Home in front", "Nothing has changed yet", null);
        card();
        text("This TV puts its own Home, " + name + " (" + home + "), ahead of other Home screens, so Android's own choice isn't enough.", 18);
        text("Unified TV can turn " + name + " off. It stays on the TV and can be turned back on at any time. Before it changes anything, Unified TV saves an undo record, the same one the Optional clean-up uses.", 17);
        text("Nothing else is turned off. On some TCL TVs, with the TV's own Home off, a few of the remote's app buttons may stop working; the TV's Settings still open from Unified TV.", 17);
        text("Ways back: in Unified TV, Settings > Appearance & Home > Return to the original TV interface. " + HomeTakeover.tvOnlySteps(name) + " From a computer: " + HomeTakeover.pcFix(home), 16);
        button("Turn off " + name + " and use Unified TV", this::takeOverNow).requestFocus();
        button("Not now", () -> { closeLink(); handBackEntry(); finish(); });
    }

    private void takeOverNow() {
        screen("Turning the TV's own Home off", "Saving the undo record first", null);
        card(); progress = busy("Saving the undo record first…");
        File file = HomeTakeover.recordFile(this); String created = CleanupActivity.created(); String self = getPackageName(); Context app = getApplicationContext();
        HomeTakeover.Check c = check;
        work(true, sh -> {
            HomeTakeover.Result r = HomeTakeover.takeOver(sh, file, c, self, created);
            if (r.outcome != HomeTakeover.Outcome.FAILED) EventLog.record(app, EventLog.SETTINGS, "Home button: turned off " + r.pkg);
            return () -> result(r);
        });
    }

    private void result(HomeTakeover.Result r) {
        String name = HomeTakeover.label(r.pkg);
        switch (r.outcome) {
            case TAKEN: DefaultHome.fixed(this, false); taken(false); return;
            case CHOOSER:
                closeLink();
                screen("Choose Unified TV once", name + " is off", null);
                card(); text(name + " is off. When you press Home, Android asks which Home to use: choose Unified TV.", 18);
                button("Next", () -> finishPage(this::pressHome)).requestFocus();
                button(RETURN_TITLE, this::returnIntro);
                return;
            case OTHER_HOME:
                screen("Another app answers Home", name + " is off", null);
                card();
                text(name + " is off, but the Home button now opens " + r.other() + " instead of Unified TV. Unified TV doesn't turn anything else off.", 18);
                text("Turn " + name + " back on to undo, or keep it like this and decide later.", 17);
                button("Undo: turn " + name + " back on", this::giveBackNow).requestFocus();
                button("Keep it like this", () -> { closeLink(); finishPage(null); });
                return;
            default:
                closeLink();
                screen("Nothing was turned off", null, null);
                card(); text("The TV didn't turn " + name + " off, so nothing changed. The Home button still opens it.", 18);
                handBackEntry();
                button("Close", this::finish).requestFocus();
        }
    }

    /** Unified TV answers Home now (read from the TV): the link closes, then debugging off, then the Home button test. */
    private void taken(boolean gentle) {
        closeLink();
        screen("Unified TV is the Home screen", "Read again from the TV", null);
        card();
        text(gentle ? "Android now opens Unified TV when you press Home. Nothing was turned off." : "The TV's own Home is off, and Android now opens Unified TV when you press Home.", 18);
        text(WAY_BACK, 17);
        button("Next", () -> finishPage(this::pressHome)).requestFocus();
    }

    /** Step e: the owner presses Home; Home itself says whether it came to the front (HomeTakeover.homeShown). */
    void pressHome() {
        screen(PRESS_HOME, "Checking for 20 seconds", null);
        card();
        progress = busy("Waiting for the Home button…");
        text("Unified TV should come to the front. If the TV's own Home appears instead, come back here or choose Settings > Appearance & Home > Return to the original TV interface.", 17);
        HomeTakeover.expect(this);
        waitingSince = HomeTakeover.now();
        clock.postDelayed(this::waitForHome, 500);
    }
    private void waitForHome() {
        if (destroyed) return;
        if (HomeTakeover.arrived(this) >= waitingSince) { worked(); return; }
        if (HomeTakeover.now() - waitingSince > HomeTakeover.CONFIRM_MS) { HomeTakeover.cancel(this); didnt(); return; }
        clock.postDelayed(this::waitForHome, 500);
    }
    private void worked() {
        screen("It works", null, null);
        card(); text(HomeTakeover.WORKED, 18); text(WAY_BACK, 17);
        button("Done", this::finish).requestFocus();
    }
    void didnt() {
        screen("The Home button didn't open Unified TV", "Nothing more was changed", null);
        card();
        text("Unified TV didn't come to the front within 20 seconds of pressing Home.", 18);
        button("Try again", this::pressHome).requestFocus();
        button("Undo: " + RETURN_TITLE, this::returnIntro);
        button("Close", this::finish);
    }

    // ------------------------------------------------------------------ return
    void returnIntro() {
        closeLink();
        String off = HomeTakeover.turnedOff(this);
        screen(RETURN_TITLE, "Appearance & Home", null);
        card();
        text("The Home button will open the TV's own Home again. Unified TV stays installed and opens from the TV's apps.", 20);
        if (off != null) { text("Unified TV turned " + HomeTakeover.label(off) + " (" + off + ") off. It will be turned back on first.", 17); text(ADB_WORDS, 17); }
        button(RETURN_TITLE, off != null ? this::giveBackNow : this::withdrawOnly).requestFocus();
        button("Not now", this::finish);
    }

    /** Nothing was turned off: switching the Home entry off hands Home straight back. */
    private void withdrawOnly() {
        DefaultHome.withdraw(this);
        EventLog.record(getApplicationContext(), EventLog.SETTINGS, "Home button: handed back to the original TV interface");
        returned(null);
    }

    void giveBackNow() {
        CleanupRecord rec = CleanupRecord.read(HomeTakeover.recordFile(this));
        if (rec == null || rec.takeoverLauncher == null) { withdrawOnly(); return; }
        screen("Turning the TV's own Home back on", null, null);
        card(); progress = busy("Connecting to this TV…"); prompt = text("", 17);
        File file = HomeTakeover.recordFile(this); Context app = getApplicationContext(); String stock = rec.takeoverLauncher;
        work(true, sh -> {
            HomeTakeover.Result r = HomeTakeover.giveBack(sh, file, rec, () -> DefaultHome.withdraw(app));
            if (r.outcome == HomeTakeover.Outcome.RETURNED) EventLog.record(app, EventLog.SETTINGS, "Home button: turned " + stock + " back on");
            return () -> { if (r.outcome == HomeTakeover.Outcome.RETURNED) returned(r); else tvOnly(stock, "The TV didn't turn " + HomeTakeover.label(stock) + " back on."); };
        });
    }

    private void returned(HomeTakeover.Result r) {
        closeLink();
        screen("The original TV interface is back", r == null ? null : "Read again from the TV", null);
        card();
        String now = r == null || r.after == null ? null : r.after.homePackage();
        text(now == null ? "The Home button opens the original TV interface again." : "The Home button opens " + HomeTakeover.label(now) + " again.", 18);
        text("Press Home on your remote to check: the TV's own Home should appear.", 17);
        button(r == null ? "Done" : "Next", r == null ? this::finish : () -> finishPage(null)).requestFocus();
    }

    /** Network debugging is off (or the TV refused): the TV-only route and the computer command. */
    void tvOnly(String stock, String why) {
        closeLink();
        screen("Turn the TV's own Home back on", null, null);
        card();
        if (why != null) text(why, 18);
        text(NO_LINK, 18);
        text("1. " + HomeTakeover.tvOnlySteps(HomeTakeover.label(stock)), 17);
        text("2. Or from a computer: " + HomeTakeover.pcFix(stock), 17);
        text("3. Or turn on USB debugging in Settings > System > Developer options, then choose Try again.", 17);
        button("Check again", () -> { if (HomeTakeover.reconcile(this)) returned(null); else note("The Home button doesn't open " + HomeTakeover.label(stock) + " yet."); }).requestFocus();
        button("Open the TV's settings", () -> openSettings(Settings.ACTION_SETTINGS));
        button("Try again", this::giveBackNow);
        button("Close", this::finish);
    }

    // ------------------------------------------------------------------ the end of the link
    /** The link is closed; the owner is guided to turn network debugging off and to check it is off. */
    void finishPage(Runnable next) {
        closeLink();
        screen("Turn off network debugging", "Recommended when you're done", null);
        card();
        text("Unified TV has closed its link. To close the TV's network debugging too:", 18);
        text("1. On the TV, open Settings > System > Developer options (on some TCL TVs: Settings > Device Preferences > Developer options).", 17);
        text("2. Turn off USB debugging. That also turns off network debugging.", 17);
        text("3. Come back here and choose Check that it's off.", 17);
        Button first = next != null ? button("Next: try the Home button", next) : null;
        Button open = button("Open the TV's settings", () -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        button("Check that it's off", this::checkPort);
        portLine = text("", 18);
        if (next == null) button("Done", this::finish);
        (first != null ? first : open).requestFocus();
    }
    private void openSettings(String action) {
        for (String a : new String[]{action, Settings.ACTION_SETTINGS}) {
            try { startActivity(new Intent(a)); return; } catch (ActivityNotFoundException | SecurityException ignored) { /* the next one */ }
        }
        note("The TV's settings couldn't be opened from here. Use the Settings button on the remote.");
    }
    private void checkPort() {
        if (portLine != null) portLine.setText("Checking…");
        TextView line = portLine;
        background(() -> { boolean closed = Cleanup.portClosed(AdbLink.PORT, CleanupActivity.PORT_CHECK_MS);
            runOnUiThread(() -> { if (line != null) line.setText(closed ? "Network debugging is off: nothing answers on port " + AdbLink.PORT + "."
                : "Still on: port " + AdbLink.PORT + " answers. Turn off USB debugging in Developer options, then check again."); }); });
    }

    // ------------------------------------------------------------------ the link and the work (as the Optional clean-up)
    private interface Job { Runnable run(Cleanup.Shell sh) throws Exception; }

    private void work(boolean change, Job job) {
        busy = true; changing = false; int mine = ++generation;
        background(() -> {
            Runnable ui;
            try { ui = job.run(shell(mine, change)); }
            catch (AdbException e) { closeLink(); AdbException.Kind k = e.kind; ui = () -> problem(k); }
            catch (HomeTakeover.Changed e) { String w = e.getMessage(); ui = () -> problem(w); }
            catch (IllegalStateException e) { ui = () -> problem((String) null); }
            catch (Exception e) { closeLink(); ui = () -> problem(AdbException.Kind.LOST); }
            Runnable show = ui;
            runOnUiThread(() -> {
                boolean current = mine == generation;
                if (current) { busy = false; changing = false; }
                if (stopped || destroyed || isFinishing()) closeLink();
                if (current && !isFinishing() && !destroyed) show.run();
            });
        });
    }
    private boolean gone(int mine) { return mine != generation || stopped || destroyed || isFinishing(); }

    private Cleanup.Shell shell(int mine, boolean change) throws Exception {
        Cleanup.Shell s = shell;
        if (s == null) {
            CleanupActivity.Opener o = CleanupActivity.openerForTests;
            if (o == null) o = CleanupActivity::openLink;   // the clean-up's own: this TV only, the same key and prompt
            CleanupActivity.Opened opened = o.open(this, () -> runOnUiThread(() -> {
                if (prompt != null) prompt.setText(CleanupActivity.PROMPT_WORDS);
                if (progress != null) progress.setText("Waiting for you to allow it on the TV…");
            }), c -> opening = c);
            opening = null;
            if (gone(mine)) {
                try { opened.link.close(); } catch (IOException ignored) { /* already closed */ }
                throw new AdbException(AdbException.Kind.LOST, "The page was left.");
            }
            link = opened.link; shell = opened.shell; s = opened.shell;
            say("Connected. Reading…");
        }
        if (gone(mine)) throw new AdbException(AdbException.Kind.LOST, "The page was left.");
        if (change) changing = true;
        return s;
    }

    private void closeLink() {
        Closeable l = link, o = opening; link = null; shell = null; opening = null;
        for (Closeable c : new Closeable[]{l, o}) if (c != null) try { c.close(); } catch (IOException ignored) { /* already closed */ }
    }

    private void problem(AdbException.Kind kind) {
        String stock = HomeTakeover.turnedOff(this);
        // Handing Home back needs the link; without it, the TV-only route.
        if (!take && stock != null && kind != AdbException.Kind.NOT_THIS_TV) { tvOnly(stock, null); return; }
        if (stock != null && kind == AdbException.Kind.CLOSED) { tvOnly(stock, null); return; }
        String words;
        switch (kind) {
            case CLOSED: words = "Network debugging is off on this TV. To continue, turn on USB debugging in Settings > System > Developer options, then try again. Nothing was turned off."; break;
            case NOT_ALLOWED: words = "The TV didn't allow Unified TV. Try again, and when the TV asks \"Allow USB debugging?\", tick \"Always allow from this computer\" and choose OK."; break;
            case NOT_THIS_TV: words = "Something other than this TV answered, so nothing was read or changed."; break;
            case PROTOCOL: words = "This TV's debugging answered in a way Unified TV doesn't understand (for example, wireless-debugging pairing only). Nothing was turned off."; break;
            default: words = "The TV stopped answering. Anything already changed is in the undo record, and Return to the original TV interface puts it back."; break;
        }
        if (take && HomeTakeover.turnedOff(this) == null) handBackEntry();
        problemPage(words);
    }
    private void problem(String words) {
        closeLink();
        if (take && HomeTakeover.turnedOff(this) == null) handBackEntry();
        problemPage(words != null ? words : "Something changed on the TV since the check (what answers Home, or the TV's own Home), so nothing was turned off. Try again.");
    }
    private void problemPage(String words) {
        screen("Nothing more was changed", null, null);
        card(); text(words, 18);
        button("Try again", take ? this::takeIntro : this::returnIntro).requestFocus();
        button("Close", this::finish);
    }

    private void say(String words) { runOnUiThread(() -> { if (progress != null) progress.setText(words); }); }
    private void background(Runnable r) { Executor e = CleanupActivity.backgroundForTests; if (e != null) e.execute(r); else worker.execute(r); }
    private static String fingerprint() { String f = CleanupActivity.fingerprintForTests; return f != null ? f : Build.FINGERPRINT; }

    @Override public void onBackPressed() {
        if (changing) { note("Please wait: the TV is being changed."); return; }
        if (busy) { generation++; busy = false; closeLink(); }
        if (waitingSince > 0) { HomeTakeover.cancel(this); waitingSince = 0; }   // left the Home button check
        Runnable b = back; if (b != null) b.run(); else close();
    }
    @Override protected void onStart() { super.onStart(); stopped = false; }
    @Override protected void onStop() { super.onStop(); stopped = true; if (!busy) closeLink(); }
    @Override protected void onDestroy() { destroyed = true; clock.removeCallbacksAndMessages(null); closeLink(); worker.shutdownNow(); super.onDestroy(); }
}
