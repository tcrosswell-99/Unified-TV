package io.unifiedtv.preview;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.ref.WeakReference;

/**
 * A short trip to another app that Unified TV finishes for the household (owner's TCL, build run 99): Connect for Live TV
 * opens NordVPN, and AirPlay's icon starts AirPin. One thing waits at a time ({@link #arm}): which Unified TV screen
 * started the trip, what to do there, and for how long. When the trip's goal is reached ({@link #deliver}: NordVPN
 * connected, AirPlay offered), Unified TV comes back to that screen by itself where Android lets it ("Display over other
 * apps", as TurnOn) and the waiting step runs there, so no Back presses are needed. Without that permission, or when the
 * household comes back first, the step runs as soon as that screen shows again.
 *
 * <p>Nothing here reads or consumes a key, and nothing is drawn over another app: the screen simply comes to the front.
 */
final class Comeback {
    private Comeback() {}
    /** The waiting step: true when it is done, false to keep waiting (the goal isn't reached yet). */
    interface Step { boolean run(Activity on); }

    private static Class<?> screen; private static Step step; private static long until;
    private static WeakReference<Activity> front = new WeakReference<>(null);
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Waits up to {@code ms} for {@code from}'s screen to show again, then runs {@code step} there. Replaces any earlier one. */
    static synchronized void arm(Activity from, long ms, Step s) { screen = from.getClass(); step = s; until = SystemClock.elapsedRealtime() + ms; }
    static synchronized void disarm() { screen = null; step = null; until = 0; }
    /** For tests and owner checks: something is waiting now. */
    static synchronized boolean armed() { return step != null && SystemClock.elapsedRealtime() < until; }

    /**
     * The trip's goal is reached: run the step on its screen if that is showing, else bring the screen to the front (the
     * step then runs as it resumes). Main thread. Returns true when Unified TV came forward or the step ran.
     */
    static boolean deliver(Context c) {
        Class<?> target; synchronized (Comeback.class) { if (!armed()) return false; target = screen; }
        Activity a = front.get();
        if (a != null && a.getClass() == target && !a.isFinishing()) { resumed(a); return true; }
        if (!TurnOn.permitted(c)) return false;   // Android won't let Unified TV come forward: it runs when the screen shows
        try { c.startActivity(new Intent(c, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); return true; }
        catch (RuntimeException e) { return false; }
    }
    /** A Unified TV screen is showing: the waiting step runs if it is this screen's. */
    static void resumed(Activity a) {
        Step s;
        synchronized (Comeback.class) {
            if (step == null) return;
            if (SystemClock.elapsedRealtime() >= until) { disarm(); return; }
            if (a.getClass() != screen) return;
            s = step;
        }
        boolean done;
        try { done = s.run(a); } catch (RuntimeException e) { done = true; }
        if (done) synchronized (Comeback.class) { if (step == s) disarm(); }
    }

    /** PreviewApp: which Unified TV screen is in front, and the waiting step when its screen shows. */
    static final Application.ActivityLifecycleCallbacks SCREENS = new Application.ActivityLifecycleCallbacks() {
        // After the screen's own onResume has finished (Home stops a Live TV watch there): only then the waiting step.
        @Override public void onActivityResumed(Activity a) { front = new WeakReference<>(a); MAIN.post(() -> { if (front.get() == a && !a.isFinishing()) resumed(a); }); }
        @Override public void onActivityPaused(Activity a) { if (front.get() == a) front = new WeakReference<>(null); }
        @Override public void onActivityCreated(Activity a, Bundle b) {}
        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        @Override public void onActivityDestroyed(Activity a) {}
    };
}
