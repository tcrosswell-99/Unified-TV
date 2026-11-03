package io.unifiedtv.preview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import io.unifiedtv.adb.AdbCommands;
import io.unifiedtv.adb.AdbException;
import io.unifiedtv.adb.AdbLink;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notification access through the TV's debugging prompt (owner's TCL, build run 99: the TV has no notification-access
 * screen, so "Allow access" led nowhere and Live TV's sheet looped). The glass sidebar over a still Home (OD-19), adults
 * only (the PIN when one is set).
 *
 * <p>It uses the Optional clean-up's own session link to this TV's ADB (CleanupActivity.openLink: this TV only, the same
 * key and "Allow USB debugging?" prompt) to send exactly one command, {@code cmd notification allow_listener
 * <this app>/io.unifiedtv.preview.MediaWatch} (AdbCommands refuses any other listener), checks with Android that access is
 * now on, closes the link and guides the owner to turn network debugging off, then checks it is off. MediaWatch is the one
 * listener for NordVPN's "Connected" notice (the flag beside the shield; Live TV doesn't need it) and Now Playing (Amazon
 * Music and other apps). Reached from Settings (Live TV &amp; Network Protection).
 */
public final class NotificationAccessActivity extends GlassScreen {
    static final String TITLE = "Notification access";
    /** Settings' button for this page, on a TV with no notification-access screen. */
    static final String DEBUG_ACCESS = "Turn on access using the TV's debugging prompt";
    static final String WHY = "Notification access lets Unified TV see NordVPN's own \"Connected\" notice, so NordVPN's country shows beside the shield, "
        + "and see what Amazon Music or another app is playing, for Now Playing. Only NordVPN's notification is read; nothing is stored or sent.";
    static final String NO_SCREEN = "This TV has no screen for notification access, so Unified TV can turn it on through the TV's network debugging, which must already be on. "
        + "It talks only to this TV, and only while this page is open. The first time, the TV asks \"Allow USB debugging?\": tick \"Always allow from this computer\", then choose OK.";
    static final String ON = "Notification access is on.";

    static void open(Context c) {
        Intent i = new Intent(c, NotificationAccessActivity.class);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (RuntimeException ignored) { /* nothing to open it from */ }
    }

    /** This app can send the one command (AdbCommands.LISTENER_APPS); otherwise only the computer route is offered. */
    static boolean available(Context c) { return AdbCommands.LISTENER_APPS.contains(c.getPackageName()); }

    private ExecutorService worker;
    /** The whole-attempt limit (LinkSteps.FLOW_MS). */
    private final Handler watch = new Handler(Looper.getMainLooper());
    /** The one change is being sent: the two-minute limit no longer applies (the command has its own). */
    private volatile boolean sending;
    private volatile Closeable link, opening;
    private volatile boolean busy, stopped, destroyed;
    private volatile int generation;
    private TextView progress, prompt, portLine;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        worker = Executors.newSingleThreadExecutor();
        page(TITLE, "NordVPN's flag and Now Playing");
        if (MediaWatch.granted(this)) { done(); return; }
        card(); text("Adult access is needed for this.", 20);
        button("Unlock", () -> EditLock.ask(this, this::intro)); button("Back", this::finish);
        EditLock.ask(this, this::intro);   // no PIN set: straight in
    }

    void intro() {
        page(TITLE, "NordVPN's flag and Now Playing");
        card();
        text(WHY, 18);
        if (!available(this)) {   // a build this list doesn't know: never a crash, the computer route only
            text("From a computer with ADB connected to this TV: " + MediaWatch.pcCommand(this), 17).setTypeface(android.graphics.Typeface.MONOSPACE);
            button("Back", this::finish).requestFocus(); return;
        }
        text(NO_SCREEN, 17);
        text("Or from a computer with ADB connected to this TV: " + MediaWatch.pcCommand(this), 15).setTypeface(android.graphics.Typeface.MONOSPACE);
        button("Start", this::start).requestFocus();
        button("Not now", this::finish);
    }

    private void start() {
        page("Turning on notification access", "One change, only to Unified TV");
        card(); progress = busy("Connecting to this TV…"); prompt = text("", 17);
        String command;
        try { command = AdbCommands.allowListener(getPackageName()); } catch (IllegalArgumentException e) { intro(); return; }
        busy = true; sending = false; int mine = ++generation;
        watch.removeCallbacksAndMessages(null);
        watch.postDelayed(() -> tooLong(mine), LinkSteps.FLOW_MS);
        Context app = getApplicationContext();
        background(() -> {
            Runnable ui;
            try {
                Cleanup.Shell sh = shell(mine);
                say("Turning it on…");
                sending = true; watch.removeCallbacksAndMessages(null);   // the one change: its own command limit only
                sh.run(command);
                boolean on = MediaWatch.granted(getApplicationContext());
                ui = () -> finishPage(on);
            }
            catch (AdbException e) { if (mine == generation) LinkSteps.failed(app, e); ui = () -> problem(e); }
            catch (Exception e) { ui = () -> problem(new AdbException(AdbException.Kind.LOST, "failed")); }
            Runnable show = ui;
            runOnUiThread(() -> {
                boolean current = mine == generation; if (current) { busy = false; watch.removeCallbacksAndMessages(null); }
                closeLink();   // one command: the link never stays open
                if (current && !isFinishing() && !destroyed) show.run();
            });
        });
    }

    private Cleanup.Shell shell(int mine) throws Exception {
        CleanupActivity.Opener o = CleanupActivity.openerForTests;
        if (o == null) o = CleanupActivity::openLink;   // the clean-up's own: this TV only, the same key and prompt
        LinkSteps.begin(this, TITLE);
        CleanupActivity.Opened opened = o.open(this, () -> runOnUiThread(() -> {
            if (prompt != null) prompt.setText(CleanupActivity.PROMPT_WORDS + " " + LinkSteps.BACK_CANCELS);
            if (progress != null) progress.setText(LinkSteps.WAITING_ALLOW);
        }), c -> opening = c);
        opening = null;
        CleanupActivity.settle(mine == generation, () -> stopped && !destroyed && !isFinishing() && mine == generation);
        if (mine != generation || stopped || destroyed || isFinishing()) {
            try { opened.link.close(); } catch (IOException ignored) { /* already closed */ }
            throw new AdbException(AdbException.Kind.LOST, "The page was left.");
        }
        link = opened.link;
        return LinkSteps.traced(this, opened.shell);
    }

    /** Two minutes and still waiting: stopped, the link closed, said plainly; a fresh worker so Try again never queues. */
    private void tooLong(int mine) {
        if (mine != generation || !busy || destroyed || sending) return;   // never while the change is being sent
        generation++; busy = false;
        closeLink();
        ExecutorService old = worker; worker = Executors.newSingleThreadExecutor(); old.shutdownNow();
        LinkSteps.failed(getApplicationContext(), "Stopped after two minutes");
        problemPage(LinkSteps.FLOW_TIMEOUT + " Nothing was changed.");
    }

    /** The link is closed; the owner is guided to turn network debugging off and to check it is off. */
    void finishPage(boolean on) {
        closeLink();
        page(on ? ON : "Notification access didn't turn on", "Then turn off network debugging");
        card();
        text(on ? ON + " Live TV can now see NordVPN's own notice, and Now Playing works for Amazon Music."
            : "Android didn't turn it on. From a computer, try: " + MediaWatch.pcCommand(this), 18);
        text("Unified TV has closed its link. To close the TV's network debugging too:", 17);
        text("1. On the TV, open Settings > System > Developer options (on some TCL TVs: Settings > Device Preferences > Developer options).", 16);
        text("2. Turn off USB debugging. That also turns off network debugging.", 16);
        text("3. Come back here and choose Check that it's off.", 16);
        Button first = button("Check that it's off", this::checkPort);
        button("Open the TV's settings", this::openDeveloperSettings);
        portLine = text("", 18);
        button("Done", this::finish);
        first.requestFocus();
    }
    /** Already on: nothing to do. */
    private void done() { card(); text(ON, 20); text(WHY, 17); button("Done", this::finish).requestFocus(); }

    private void openDeveloperSettings() {
        for (String a : new String[]{Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Settings.ACTION_SETTINGS}) {
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

    /** What failed, at which step, in plain words (LinkSteps.words), and the computer route. */
    private void problem(AdbException e) {
        String words = LinkSteps.words(e);
        switch (e.kind) {
            case CLOSED: words += " Or, from a computer: " + MediaWatch.pcCommand(this); break;
            case NOT_ALLOWED: case NOT_THIS_TV: break;
            default: words += " Nothing was changed. From a computer: " + MediaWatch.pcCommand(this); break;
        }
        problemPage(words);
    }
    private void problemPage(String words) {
        page("Notification access is still off", null);
        card(); text(words, 18);
        text(LinkSteps.last(this), 15);
        button("Try again", this::intro).requestFocus();
        button("Cancel", this::finish);
    }

    private void closeLink() {
        Closeable l = link, o = opening; link = null; opening = null;
        if (l != null) LinkSteps.step(getApplicationContext(), "Link closed");
        for (Closeable c : new Closeable[]{l, o}) if (c != null) try { c.close(); } catch (IOException ignored) { /* already closed */ }
    }
    private void say(String words) { runOnUiThread(() -> { if (progress != null) progress.setText(words); }); }
    private void background(Runnable r) { Executor e = CleanupActivity.backgroundForTests; if (e != null) e.execute(r); else worker.execute(r); }

    @Override public void onBackPressed() { if (busy) { generation++; busy = false; closeLink(); } super.onBackPressed(); }
    @Override protected void onStart() { super.onStart(); stopped = false; }
    @Override protected void onStop() { super.onStop(); stopped = true; if (!busy) closeLink(); }
    @Override protected void onDestroy() { destroyed = true; watch.removeCallbacksAndMessages(null); closeLink(); worker.shutdownNow(); super.onDestroy(); }
}
