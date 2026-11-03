package io.unifiedtv.preview;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * OK on Home's AirPlay icon (OD-30, owner, 3 November 2026: the icon never takes him into AirPin; AirPin's own start-at-boot
 * setting keeps AirPlay ready).
 * <ul>
 * <li>AirPlay ready (AirPin offering {@code _airplay._tcp} on the network, AirPlayReceiver): a small glass panel says how to
 *     connect from an iPhone or Mac, with the name AirPin advertises. Nothing opens.</li>
 * <li>AirPin installed but not offering it: a panel says AirPlay isn't running and how to keep it ready (AirPin's own
 *     "start at boot" or "run in background", once). Nothing opens. <b>Check again</b> (focused first) looks again in place
 *     ("Checking…"); <b>Start AirPin now</b> is the only way from here into AirPin: it opens (its own task, so Unified TV can
 *     come back over it), and Unified TV watches for the service for up to {@link #WINDOW_MS}. As soon as it appears,
 *     Unified TV comes back by itself ("Display over other apps", Comeback) with the "AirPlay is ready" panel, so AirPin only
 *     flashes. If it never appears, the panel says so plainly when the household comes back.</li>
 * <li>AirPin not installed: as before (Launch says so).</li>
 * </ul>
 * Every check is a service-discovery look on a worker thread, a few seconds long; never on the main thread, never after
 * the window. Nothing reads or consumes a key.
 */
final class AirPlayStart {
    private AirPlayStart() {}
    static final long WINDOW_MS = 15_000;
    static final int ATTEMPTS = 12;
    static final String STARTING = "Starting AirPlay…";
    static final String CHECKING = "Checking…";
    static final String CHECK_AGAIN = "Check again", START_NOW = "Start AirPin now";
    static final String TIP = "Tip: in AirPin's settings, turn on start at boot / run in background, so AirPlay is always ready.";
    static final String KEEP_READY = "Turn on 'start at boot' (or 'run in background') in AirPin's settings once, and AirPlay will always be ready.";
    static final String NOT_RUNNING = "AirPlay isn't running. " + KEEP_READY;
    static final String STILL_NOT_RUNNING = "AirPlay still isn't running. " + KEEP_READY;
    static final String NOT_STARTED = "AirPlay didn't start. AirPin LITE opened, but this TV isn't offering AirPlay on your network yet. Check that the TV and your iPhone are on the same network. " + KEEP_READY;
    /** Settings → Connections → AirPlay: the one-time how-to. */
    static final String HOW_TO = "How to keep AirPlay ready: open AirPin LITE, go to its settings, and turn on 'start at boot' (or 'run in background'). After that, AirPlay is ready whenever the TV is on, and the AirPlay icon on Home is bright.";
    /** Tests only: run the checks here (inline) and wait this long between the watch's looks. */
    static volatile Executor workerForTests; static volatile long pauseMs = 1000;
    private static volatile boolean watching, checking;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Pure: the words that tell the household how to connect, with the name AirPin advertises when it is known. */
    static String readyWords(String name) {
        String n = name == null ? "" : name.trim();
        return "AirPlay is ready. On your iPhone or Mac, open Control Centre > Screen Mirroring (or the AirPlay button) and choose "
            + (n.isEmpty() ? "this TV" : "'" + n + "'") + ".";
    }

    /** OK on the icon: the panel only, never AirPin. Returns a reason to show when nothing could be done, else null. */
    static String press(Activity a, String airPin) {
        if (airPin == null) return Launch.visit(a, null, "AirPin LITE");   // not installed: as before
        panel(a, airPin, AirPlayReceiver.ready, NOT_RUNNING);
        return null;
    }

    /**
     * "Start AirPin now", the only route from the AirPlay panel into AirPin: "Starting AirPlay…", AirPin opens, and the watch
     * brings Unified TV back with the panel as soon as AirPlay appears. Returns a reason when AirPin could not be opened.
     */
    static String start(Activity a, String airPin) {
        Toast.makeText(a, STARTING, Toast.LENGTH_SHORT).show();
        // Back on this screen: the panel, once the watch has an answer (an early return keeps waiting for it).
        // The step keeps only AirPin's package, never a screen, and expires: if AirPlay never starts, the plain panel shows
        // whenever the household comes back to this screen within the window, and Unified TV never pulls them out of anything.
        Comeback.Token t = Comeback.arm(a, WINDOW_MS + EXPIRES_MS, on -> { if (watching) return false; panel(on, airPin, AirPlayReceiver.ready, NOT_STARTED); return true; });
        String why = Launch.open(a, airPin, "AirPin LITE");
        if (why != null) { Comeback.disarm(t); return why; }
        watch(a.getApplicationContext(), t);
        return null;
    }
    /** How long after the window the "didn't start" answer waits for the household to come back. */
    static final long EXPIRES_MS = 10 * 60_000;
    /** Checks for AirPin's service until it appears or the window ends, then brings Unified TV back (Comeback). */
    static void watch(Context app, Comeback.Token t) {
        watching = true;
        run(() -> {
            long start = System.nanoTime(); boolean found = false;
            try {
                for (int i = 0; i < ATTEMPTS && (System.nanoTime() - start) / 1_000_000 < WINDOW_MS; i++) {
                    if (AirPlayReceiver.checkNow(app) != null) { found = true; break; }
                    long pause = pauseMs; if (pause > 0) Thread.sleep(pause);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally {
                boolean ready = found;
                // Only AirPlay actually appearing brings Unified TV back. Otherwise the household stays where it is (AirPin's
                // own first-run or permission prompts, or another app it moved on to), and the step waits for its return.
                MAIN.post(() -> { watching = false; if (ready) Comeback.deliver(app, t); });
            }
        }, "airplay-start");
    }
    /** For tests: the watch is running. */
    static boolean watching() { return watching; }

    /**
     * One look now ("Check again", on the panel or in Settings), on a worker thread; done runs on the main thread afterwards,
     * with AirPlayReceiver.ready updated. A press while a look is running is ignored (false).
     */
    static boolean check(Context c, Runnable done) {
        synchronized (AirPlayStart.class) { if (checking) return false; checking = true; }
        Context app = c.getApplicationContext();
        run(() -> {
            try { AirPlayReceiver.checkNow(app); }
            finally { MAIN.post(() -> { checking = false; if (done != null) done.run(); }); }
        }, "airplay-check-again");
        return true;
    }
    /** For tests and Settings: a "Check again" look is running. */
    static boolean checking() { return checking; }

    private static void run(Runnable job, String name) {
        Executor e = workerForTests;
        if (e != null) e.execute(job);
        else { Thread thread = new Thread(job, name); thread.setDaemon(true); thread.start(); }
    }

    /**
     * The small glass panel. Ready: how to connect, with Done. Not ready: the given words, with Check again (focused first)
     * and Start AirPin now.
     */
    static void panel(Activity a, String airPin, boolean ready, String notReady) {
        List<String> names = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        Dialog[] d = new Dialog[1]; TextView[] words = new TextView[1];
        if (ready) { names.add("Done"); actions.add(() -> d[0].dismiss()); }
        else {
            names.add(CHECK_AGAIN); actions.add(() -> {
                if (checking) return;
                words[0].setText(CHECKING);
                check(a, () -> {
                    if (d[0] == null || !d[0].isShowing() || a.isFinishing()) return;
                    if (AirPlayReceiver.ready) { d[0].dismiss(); panel(a, airPin, true, notReady); }
                    else words[0].setText(STILL_NOT_RUNNING);
                });
            });
            names.add(START_NOW); actions.add(() -> {
                d[0].dismiss(); String why = start(a, airPin);
                if (why != null && !why.isEmpty()) Toast.makeText(a, why, Toast.LENGTH_LONG).show();
            });
        }
        d[0] = VpnGate.sheet(a, "AirPlay", ready ? readyWords(AirPlayReceiver.advertisedAs) + "\n\n" + TIP : notReady, names, actions, words);
    }
}
