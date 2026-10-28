package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * OK on Home's AirPlay icon (owner's TCL, build run 99: it took him out into AirPin; "just connect and that's it").
 * <ul>
 * <li>AirPlay ready (AirPin offering {@code _airplay._tcp} on the network, AirPlayReceiver): a small glass panel says how to
 *     connect from an iPhone or Mac, with the name AirPin advertises, and AirPin is not opened. "Open AirPin settings" is
 *     there if wanted.</li>
 * <li>AirPin installed but not offering it: "Starting AirPlay…", AirPin opens (its own task, so Unified TV can come back
 *     over it), and Unified TV watches for the service for up to {@link #WINDOW_MS}. As soon as it appears, Unified TV comes
 *     back by itself ("Display over other apps", Comeback) with the "AirPlay is ready" panel, so AirPin only flashes. If it
 *     never appears, the panel says so plainly, with "Open AirPin".</li>
 * <li>AirPin not installed: as before (Launch says so).</li>
 * </ul>
 * The watch is a few service-discovery checks in a row on one worker thread, each a few seconds long; never on the main
 * thread, never after the window. Nothing reads or consumes a key.
 */
final class AirPlayStart {
    private AirPlayStart() {}
    static final long WINDOW_MS = 15_000;
    static final int ATTEMPTS = 12;
    static final String STARTING = "Starting AirPlay…";
    static final String TIP = "Tip: in AirPin's settings, turn on start at boot / run in background, so AirPlay is always ready.";
    static final String NOT_STARTED = "AirPlay didn't start. AirPin LITE opened, but this TV isn't offering AirPlay on your network yet. Open AirPin to check it, and that the TV and your iPhone are on the same network.";
    /** Tests only: run the watch here (inline) and wait this long between checks. */
    static volatile Executor workerForTests; static volatile long pauseMs = 1000;
    private static volatile boolean watching;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Pure: the words that tell the household how to connect, with the name AirPin advertises when it is known. */
    static String readyWords(String name) {
        String n = name == null ? "" : name.trim();
        return "AirPlay is ready. On your iPhone or Mac, open Control Centre > Screen Mirroring (or the AirPlay button) and choose "
            + (n.isEmpty() ? "this TV" : "'" + n + "'") + ".";
    }

    /** OK on the icon. Returns a reason to show when nothing could be done, else null. */
    static String press(Activity a, String airPin) {
        if (airPin == null) return Launch.visit(a, null, "AirPin LITE");   // not installed: as before
        if (AirPlayReceiver.ready) { panel(a, airPin, true); return null; }
        Toast.makeText(a, STARTING, Toast.LENGTH_SHORT).show();
        // Back on this screen: the panel, once the watch has an answer (an early return keeps waiting for it).
        // The step keeps only AirPin's package, never a screen, and expires: if AirPlay never starts, the plain panel shows
        // whenever the household comes back to this screen within the window, and Unified TV never pulls them out of anything.
        Comeback.Token t = Comeback.arm(a, WINDOW_MS + EXPIRES_MS, on -> { if (watching) return false; panel(on, airPin, AirPlayReceiver.ready); return true; });
        String why = Launch.open(a, airPin, "AirPin LITE");
        if (why != null) { Comeback.disarm(t); return why; }
        watch(a.getApplicationContext(), t);
        return null;
    }
    /** Checks for AirPin's service until it appears or the window ends, then brings Unified TV back (Comeback). */
    /** How long after the window the "didn't start" answer waits for the household to come back. */
    static final long EXPIRES_MS = 10 * 60_000;
    static void watch(Context app, Comeback.Token t) {
        watching = true;
        Runnable job = () -> {
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
        };
        Executor e = workerForTests;
        if (e != null) e.execute(job);
        else { Thread thread = new Thread(job, "airplay-start"); thread.setDaemon(true); thread.start(); }
    }
    /** For tests: the watch is running. */
    static boolean watching() { return watching; }

    /** The small glass panel: how to connect when ready; otherwise the plain "didn't start", with Open AirPin. */
    static void panel(Activity a, String airPin, boolean ready) {
        List<String> names = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        android.app.Dialog[] d = new android.app.Dialog[1];
        Runnable open = () -> { d[0].dismiss(); String why = Launch.visit(a, airPin, "AirPin LITE"); if (why != null && !why.isEmpty()) Toast.makeText(a, why, Toast.LENGTH_LONG).show(); };
        if (ready) { names.add("Done"); actions.add(() -> d[0].dismiss()); names.add("Open AirPin settings"); actions.add(open); }
        else { names.add("Open AirPin"); actions.add(open); names.add("Close"); actions.add(() -> d[0].dismiss()); }
        d[0] = VpnGate.sheet(a, "AirPlay", (ready ? readyWords(AirPlayReceiver.advertisedAs) : NOT_STARTED) + "\n\n" + TIP, names, actions, null);
    }
}
