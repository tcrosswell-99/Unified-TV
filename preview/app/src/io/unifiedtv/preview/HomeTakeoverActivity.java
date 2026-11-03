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
 * OD-28, launcher app only: Settings > Appearance & Home > When the TV turns on > Make Unified TV the TV's Home (instant),
 * and Return to the original TV interface (on that page, in Updates & About > Advanced, and on Home's safe screen). Adults only, behind the PIN when one is set.
 * The glass sidebar over a still Home (OD-19). The steps are HomeTakeover's; this page only shows them and asks.
 *
 * <p>Take over: Unified TV's Home entry is switched on, then the same session-only link to this TV's own ADB as the
 * Optional clean-up (same "Allow USB debugging?" prompt, same key) reads Home and asks Android directly. If the TV keeps
 * its own Home in front, the page explains plainly and only on the owner's confirm turns that Home off (recorded first).
 * Then the link closes, the owner is guided to turn network debugging off (checked), and finally asked to press Home on
 * the remote: Home reports whether it came to the front within 20 seconds, and the way back is offered if not.
 *
 * <p>Before the link (owner's Sony BRAVIA 4K UR2, Android TV 10, 3 November 2026): HomeTakeover.plan decides on this TV
 * alone. A TV no tested list covers gets Android's own choice only, said up front (HomeTakeover.UNTESTED), with nothing
 * ever turned off (HomeTakeover.gentleOnly); if it doesn't stick, the page says so plainly (HomeTakeover.KEEPS_OWN) and
 * offers "Open Unified TV (quick)" on the When the TV turns on page. Every link wait is limited (AdbLink, and
 * LinkSteps.FLOW_MS for the whole attempt): a failure says which step, in plain words, records it for Owner diagnostics
 * and the event log, closes the link and offers Try again or Cancel. Back cancels while the TV waits for Allow, and is
 * refused only while a change is actually being sent.
 *
 * <p>Return: when this flow turned the TV's own Home off, the link turns it back on, sets it as Home and withdraws Unified
 * TV's Home entry. When network debugging is off, the page gives the TV-only route and the computer command instead.
 * Nothing on this page calls finish() on Home.
 */
public final class HomeTakeoverActivity extends GlassScreen {
    static final String MODE = "mode", TAKE = "take", RETURN = "return";
    /** Optional: why Return was opened (When the TV turns on: option 1 or 2 chosen while Unified TV holds Home). */
    static final String REASON = "reason";
    static final String TAKE_TITLE = "Take over the Home button", RETURN_TITLE = "Return to the original TV interface";
    static final String PREVIEW_WORDS = "This is the Unified TV preview. It never changes the Home button; the separate Unified TV app can.";
    static final String INTRO = "Make the Home button on your remote open Unified TV. Unified TV first asks Android directly. If this TV keeps its own Home in front anyway, Unified TV can turn the TV's own Home off: never removed, only after you confirm, and only on a TV the tested lists cover.";
    static final String WAY_BACK = "The way back is always in Settings > Appearance & Home > When the TV turns on > Return to the original TV interface.";
    /** Owner, 3 November 2026: said before anything changes and once it has. */
    static final String REVIEW_BUILD_WORDS = "Before uninstalling a review build, choose Return to the original TV interface.";
    static final String ADB_WORDS = "Unified TV uses this TV's network debugging, which must already be on. It talks only to this TV, and only while this page is open. "
        + "The first time, the TV asks \"Allow USB debugging?\": tick \"Always allow from this computer\", then choose OK.";
    static final String PRESS_HOME = "Press the Home button on your remote now.";
    static final String NO_LINK = "Network debugging is off, so Unified TV can't turn the TV's own Home back on by itself. Do one of these, then choose Check again:";

    static void open(Context c, boolean take) { open(c, take, null); }
    static void open(Context c, boolean take, String reason) {
        Intent i = new Intent(c, HomeTakeoverActivity.class).putExtra(MODE, take ? TAKE : RETURN);
        if (reason != null) i.putExtra(REASON, reason);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private ExecutorService worker;
    private final Handler clock = new Handler(Looper.getMainLooper());
    /** The whole-attempt limit (LinkSteps.FLOW_MS), apart from {@link #clock}, which each new page clears. */
    private final Handler watch = new Handler(Looper.getMainLooper());
    /** The running job changes the TV throughout (take over, give back, restore): the clock never stops it. */
    private volatile boolean changeJob;
    /** What this TV allows, decided before any link (HomeTakeover.plan). */
    private HomeTakeover.Plan plan;
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
        // Taking over is for Adults. The way back never refuses Kids (Home's safe screen can't switch profile): the adult PIN,
        // when one is set, is still asked below.
        if (take && Profiles.kids(this)) { card(); text("This is for Adults. Switch to Adults first.", 20); button("Back", this::finish).requestFocus(); return; }
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
    /** Decided before any link, from this TV alone: the tested route, the gentle route only, or "keeps its own Home". */
    void takeIntro() {
        plan = HomeTakeover.plan(this, CleanupLists.load(this), fingerprint());
        if (plan.route == HomeTakeover.Route.KEEPS_OWN) { keepsOwnHome(true); return; }
        screen(TAKE_TITLE, "Appearance & Home", null);
        card();
        text(plan.gentleOnly() ? HomeTakeover.UNTESTED : INTRO, 20);
        text(ADB_WORDS, 17);
        text(WAY_BACK, 17);
        text(REVIEW_BUILD_WORDS, 17);
        button("Start", this::takeNow).requestFocus();
        button("Not now", this::finish);
    }

    /**
     * This TV keeps its own Home: said plainly, with "Open Unified TV (quick)" one press away. {@code before}: known from
     * Android's own list before any link (nothing was opened or changed); otherwise Android's own choice was tried over
     * the link and didn't stick, and the Home entry is handed back, so nothing changed.
     */
    private void keepsOwnHome(boolean before) {
        if (!before) { closeLink(); handBackEntry(); EventLog.record(getApplicationContext(), EventLog.SETTINGS, "Home button: this TV keeps its own Home"); }
        screen("This TV keeps its own Home", "Nothing was changed", null);
        card();
        if (before && plan != null && plan.ahead != null) text(HomeTakeover.ranksFirst(HomeTakeover.label(plan.ahead)), 18);
        text(HomeTakeover.KEEPS_OWN, 18);
        text("Nothing was changed.", 17);
        button(HomeTakeover.USE_QUICK, this::useQuick).requestFocus();
        if (before) button("Try anyway", this::takeNow);
        button("Close", this::finish);
    }

    /** Option 2 on the When the TV turns on page (its own PIN and checks); an unused Home entry is handed back first. */
    private void useQuick() {
        if (!TurnOnChoice.holdsHome(this)) DefaultHome.withdraw(this);
        TurnOnChoiceActivity.open(this, TurnOnChoice.Option.QUICK);
        finish();
    }

    /** Step a: the Home entry on; b: read Home; c: Android's own route. On a TV no tested list covers, only that. */
    private void takeNow() {
        if (plan == null) plan = HomeTakeover.plan(this, CleanupLists.load(this), fingerprint());
        boolean gentleOnly = plan.gentleOnly();
        screen("Reading this TV", "Nothing is turned off at this step", this::takeIntro);
        card(); progress = busy("Connecting to this TV…"); prompt = text("", 17);
        String self = getPackageName();
        entryWasOn = DefaultHome.offered(this);
        getPackageManager().setComponentEnabledSetting(DefaultHome.entry(this), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        // Reading: Back still works. Only the set-home-activity command itself refuses Back while it is sent (see shell()).
        work(false, link -> {
            Cleanup.Shell sh = gentleOnly ? HomeTakeover.gentleOnly(link, self) : link;
            HomeTakeover.Check c = HomeTakeover.check(sh, CleanupLists.load(this), fingerprint(), self);
            if (!c.before.candidate(self)) { Thread.sleep(candidateWaitMs); c.before = HomeTakeover.read(sh); }   // the entry can take a moment to register
            if (c.before.ours(self)) return () -> { check = c; taken(true); };
            if (!c.before.candidate(self)) return () -> { check = c; refused(HomeTakeover.NOT_CANDIDATE); };
            say("Asking Android to use Unified TV…");
            HomeTakeover.Reading after = HomeTakeover.gentle(sh, c, self);
            c.before = after;
            if (after.ours(self)) return () -> { check = c; EventLog.record(getApplicationContext(), EventLog.SETTINGS, "Home button: Unified TV set as Home"); taken(true); };
            if (gentleOnly) return () -> { check = c; keepsOwnHome(false); };
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
        text("Unified TV can still open when the TV turns on: Settings > Appearance & Home > " + TurnOnChoice.TITLE + " > " + TurnOnChoice.Option.QUICK.title + ".", 17);
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
        text("Ways back: in Unified TV, Settings > Appearance & Home > " + TurnOnChoice.TITLE + " > Return to the original TV interface. " + HomeTakeover.tvOnlySteps(name) + " From a computer: " + HomeTakeover.pcFix(home), 16);
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
        TurnOnChoice.homeTaken(this);   // the Home button opens Unified TV now: "Open Unified TV (quick)" isn't needed
        screen("Unified TV is the Home screen", "Read again from the TV", null);
        card();
        text(gentle ? "Android now opens Unified TV when you press Home. Nothing was turned off." : "The TV's own Home is off, and Android now opens Unified TV when you press Home.", 18);
        text(WAY_BACK, 17);
        text(REVIEW_BUILD_WORDS, 17);
        button("Next", () -> finishPage(this::pressHome)).requestFocus();
    }

    /** Step e: the owner presses Home; Home itself says whether it came to the front (HomeTakeover.homeShown). */
    void pressHome() {
        screen(PRESS_HOME, "Checking for 20 seconds", null);
        card();
        progress = busy("Waiting for the Home button…");
        text("Unified TV should come to the front. If the TV's own Home appears instead, come back here or choose Settings > Appearance & Home > " + TurnOnChoice.TITLE + " > Return to the original TV interface.", 17);
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
        TurnOnChoice.homeTaken(this);
        screen("It works", null, null);
        card(); text(HomeTakeover.WORKED, 18); text(WAY_BACK, 17); text(REVIEW_BUILD_WORDS, 17);
        button("Done", this::finish).requestFocus();
    }
    void didnt() {
        screen("The Home button didn't open Unified TV", "Nothing more was changed", null);
        card();
        text("Unified TV didn't come to the front within 20 seconds of pressing Home.", 18);
        boolean untested = plan != null && plan.gentleOnly();
        if (untested) text(HomeTakeover.KEEPS_OWN, 18);
        Button first = button("Try again", this::pressHome);
        if (untested) first = button(HomeTakeover.USE_QUICK, this::useQuick);
        first.requestFocus();
        button("Undo: " + RETURN_TITLE, this::returnIntro);
        button("Close", this::finish);
    }

    // ------------------------------------------------------------------ return
    void returnIntro() {
        closeLink();
        String off = HomeTakeover.turnedOff(this);
        // No record (storage cleared, or a damaged file), but Android reports a stock Home off: turn it on first, never
        // withdraw first (that would leave the TV with no Home at all).
        lost = off == null ? HomeTakeover.disabledStock(this) : null;
        screen(RETURN_TITLE, "Appearance & Home", null);
        card();
        String reason = getIntent().getStringExtra(REASON);
        if (reason != null) text(reason, 20);
        text("The Home button will open the TV's own Home again. Unified TV stays installed and opens from the TV's apps.", 20);
        if (off != null) { text("Unified TV turned " + HomeTakeover.label(off) + " (" + off + ") off. It will be turned back on first.", 17); text(ADB_WORDS, 17); }
        if (lost != null) { text(HomeTakeover.label(lost) + " (" + lost + ") is turned off, and Unified TV has no record of it (its storage may have been cleared). It will be turned back on first.", 17); text(ADB_WORDS, 17); }
        button(RETURN_TITLE, off != null ? this::giveBackNow : lost != null ? this::restoreLost : this::withdrawOnly).requestFocus();
        button("Not now", this::finish);
    }
    /** A stock Home Android reports off with no record of it (Return, when the record is gone). */
    private String lost;

    /** Nothing was turned off: switching the Home entry off hands Home straight back, unless no other Home would be left. */
    private void withdrawOnly() {
        if (!DefaultHome.withdraw(this)) {
            String stock = HomeTakeover.disabledStock(this);
            if (stock != null) { lost = stock; restoreLost(); return; }
            screen(RETURN_TITLE, null, null);
            card(); text("Unified TV is the only Home screen this TV has turned on, so it stays, or the TV would have none. Nothing was changed.", 18);
            text(HomeTakeover.tvOnlySteps(HomeTakeover.label("com.google.android.tvlauncher")) + " From a computer: " + HomeTakeover.pcFix("com.google.android.tvlauncher"), 17);
            button("Close", this::finish).requestFocus();
            return;
        }
        EventLog.record(getApplicationContext(), EventLog.SETTINGS, "Home button: handed back to the original TV interface");
        returned(null);
    }

    /** The record is gone: the stock Home Android reports off goes back on over the link, then the Home entry. */
    void restoreLost() {
        String stock = lost;
        if (stock == null) { withdrawOnly(); return; }
        screen("Turning the TV's own Home back on", null, null);
        card(); progress = busy("Connecting to this TV…"); prompt = text("", 17);
        Context app = getApplicationContext();
        work(true, sh -> {
            HomeTakeover.Result r = HomeTakeover.restoreStock(sh, stock, () -> DefaultHome.withdraw(app));
            if (r.outcome == HomeTakeover.Outcome.RETURNED) EventLog.record(app, EventLog.SETTINGS, "Home button: turned " + stock + " back on");
            return () -> { if (r.outcome == HomeTakeover.Outcome.RETURNED) returned(r); else tvOnly(stock, "The TV didn't turn " + HomeTakeover.label(stock) + " back on."); };
        });
    }

    void giveBackNow() {
        CleanupRecord rec = CleanupRecord.read(HomeTakeover.recordFile(this));
        if (rec == null || rec.takeoverLauncher == null) { lost = HomeTakeover.disabledStock(this); if (lost != null) restoreLost(); else withdrawOnly(); return; }
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
        boolean recorded = stock.equals(HomeTakeover.turnedOff(this));
        button("Check again", () -> { if (recorded ? HomeTakeover.reconcile(this) : HomeTakeover.reconcileLost(this, stock)) returned(null); else note("The Home button doesn't open " + HomeTakeover.label(stock) + " yet."); }).requestFocus();
        button("Open the TV's settings", () -> openSettings(Settings.ACTION_SETTINGS));
        button("Try again", recorded ? this::giveBackNow : () -> { lost = stock; restoreLost(); });
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
        watch.removeCallbacksAndMessages(null);
        watch.postDelayed(() -> tooLong(mine), LinkSteps.FLOW_MS);
        Context app = getApplicationContext();
        background(() -> {
            Runnable ui;
            try { ui = job.run(shell(mine, change)); }
            catch (AdbException e) { closeLink(); if (mine == generation) LinkSteps.failed(app, e); ui = () -> problem(e); }
            catch (HomeTakeover.Changed e) { String w = e.getMessage(); ui = () -> problem(w); }
            catch (IllegalStateException e) { ui = () -> problem((String) null); }
            catch (Exception e) { closeLink(); ui = () -> problem(new AdbException(AdbException.Kind.LOST, "failed")); }
            Runnable show = ui;
            runOnUiThread(() -> {
                boolean current = mine == generation;
                if (current) { busy = false; changing = false; watch.removeCallbacksAndMessages(null); }
                if (stopped || destroyed || isFinishing()) closeLink();
                if (current && !isFinishing() && !destroyed) show.run();
            });
        });
    }
    private boolean gone(int mine) { return mine != generation || stopped || destroyed || isFinishing(); }

    /** Two minutes and still waiting: stopped, the link closed, said plainly; a fresh worker so Try again never queues. */
    private void tooLong(int mine) {
        if (mine != generation || !busy || destroyed) return;
        // Never part-way through a change: a change job runs to its end (each command has its own limit); a reading job's
        // one set-home-activity is let finish, then the clock looks again.
        if (changing) { if (!changeJob) watch.postDelayed(() -> tooLong(mine), 1000); return; }
        generation++; busy = false; changing = false;
        closeLink();
        ExecutorService old = worker; worker = Executors.newSingleThreadExecutor(); old.shutdownNow();
        LinkSteps.failed(getApplicationContext(), "Stopped after two minutes");
        problem(LinkSteps.FLOW_TIMEOUT + (HomeTakeover.turnedOff(this) == null ? " Nothing was turned off."
            : " Anything already changed is in the undo record, and Return to the original TV interface puts it back."));
    }

    private Cleanup.Shell shell(int mine, boolean change) throws Exception {
        Cleanup.Shell s = shell;
        if (s == null) {
            CleanupActivity.Opener o = CleanupActivity.openerForTests;
            if (o == null) o = CleanupActivity::openLink;   // the clean-up's own: this TV only, the same key and prompt
            LinkSteps.begin(this, (take ? TAKE_TITLE : RETURN_TITLE) + (take && plan != null && plan.gentleOnly() ? " (untested TV: Android's own choice only)" : ""));
            CleanupActivity.Opened opened = o.open(this, () -> runOnUiThread(() -> {
                if (prompt != null) prompt.setText(CleanupActivity.PROMPT_WORDS + " " + LinkSteps.BACK_CANCELS);
                if (progress != null) progress.setText(LinkSteps.WAITING_ALLOW);
            }), c -> opening = c);
            opening = null;
            CleanupActivity.settle(mine == generation, () -> stopped && !destroyed && !isFinishing() && mine == generation);
            if (gone(mine)) {
                try { opened.link.close(); } catch (IOException ignored) { /* already closed */ }
                throw new AdbException(AdbException.Kind.LOST, "The page was left.");
            }
            link = opened.link; shell = LinkSteps.traced(this, opened.shell); s = shell;
            say("Connected. Reading…");
        }
        if (gone(mine)) throw new AdbException(AdbException.Kind.LOST, "The page was left.");
        changeJob = change;
        if (change) { changing = true; watch.removeCallbacksAndMessages(null); return s; }   // from here, each command's own limit only
        // A reading job: Back is refused only while a change command is actually being sent.
        Cleanup.Shell reading = s;
        return command -> {
            if (!io.unifiedtv.adb.AdbCommands.changes(command)) return reading.run(command);
            changing = true;
            try { return reading.run(command); } finally { changing = false; }
        };
    }

    private void closeLink() {
        Closeable l = link, o = opening; link = null; shell = null; opening = null;
        if (l != null) LinkSteps.step(getApplicationContext(), "Link closed");
        for (Closeable c : new Closeable[]{l, o}) if (c != null) try { c.close(); } catch (IOException ignored) { /* already closed */ }
    }

    /** What failed, at which step, in plain words (LinkSteps.words), then what that means for the Home button. */
    private void problem(AdbException e) {
        AdbException.Kind kind = e.kind;
        String stock = HomeTakeover.turnedOff(this);
        if (stock == null && !take) stock = lost;
        // Handing Home back needs the link; without it, the TV-only route.
        if (!take && stock != null && kind != AdbException.Kind.NOT_THIS_TV) { tvOnly(stock, LinkSteps.words(e)); return; }
        if (stock != null && kind == AdbException.Kind.CLOSED) { tvOnly(stock, null); return; }
        String words = LinkSteps.words(e);
        switch (kind) {
            case CLOSED: case PROTOCOL: words += " Nothing was turned off."; break;
            case NOT_ALLOWED: case NOT_THIS_TV: break;
            default: words += stock == null ? " Nothing was turned off." : " Anything already changed is in the undo record, and Return to the original TV interface puts it back."; break;
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
        text(LinkSteps.last(this), 15);
        button("Try again", take ? this::takeIntro : this::returnIntro).requestFocus();
        button("Cancel", this::finish);
    }

    private void say(String words) { runOnUiThread(() -> { if (progress != null) progress.setText(words); }); }
    private void background(Runnable r) { Executor e = CleanupActivity.backgroundForTests; if (e != null) e.execute(r); else worker.execute(r); }
    private static String fingerprint() { String f = CleanupActivity.fingerprintForTests; return f != null ? f : Build.FINGERPRINT; }

    @Override public void onBackPressed() {
        if (changing) { note("Please wait: the TV is being changed."); return; }
        // Still reading, or the TV still asking "Allow USB debugging?": stop, close, and hand an unused Home entry back.
        if (busy) { generation++; busy = false; closeLink(); watch.removeCallbacksAndMessages(null); if (take && HomeTakeover.turnedOff(this) == null) handBackEntry(); }
        if (waitingSince > 0) { HomeTakeover.cancel(this); waitingSince = 0; }   // left the Home button check
        Runnable b = back; if (b != null) b.run(); else close();
    }
    @Override protected void onStart() { super.onStart(); stopped = false; }
    @Override protected void onStop() { super.onStop(); stopped = true; if (!busy) closeLink(); }
    @Override protected void onDestroy() { destroyed = true; clock.removeCallbacksAndMessages(null); watch.removeCallbacksAndMessages(null); closeLink(); worker.shutdownNow(); super.onDestroy(); }
}
