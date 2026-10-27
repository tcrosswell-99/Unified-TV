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
    /**
     * The waiting step: true when it is done, false to keep waiting (the goal isn't reached yet). It gets the screen to run
     * on and must not hold any screen itself (review: no Activity kept alive by a waiting step).
     */
    interface Step { boolean run(Activity on); }
    /** Which trip is waiting: only its own token can deliver it or ask about it (NordConnect, AirPlayStart). */
    static final class Token { private Token() {} }
    /** What deliver() did. */
    enum Result { RAN, BROUGHT, WAITING, GONE }

    private static Token token; private static WeakReference<Activity> owner = new WeakReference<>(null);
    private static Class<?> screen; private static Step step; private static long until;
    private static WeakReference<Activity> front = new WeakReference<>(null);
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * Waits up to {@code ms} for this very screen ({@code from}, held weakly) to show again, then runs {@code step} there.
     * Replaces any earlier one. Returns the trip's token.
     */
    static synchronized Token arm(Activity from, long ms, Step s) { return set(from, from.getClass(), ms, s); }
    /** The same for whichever instance of {@code cls} shows next (Home, when the screen that started the trip is gone). */
    static synchronized Token armFor(Class<? extends Activity> cls, long ms, Step s) { return set(null, cls, ms, s); }
    private static Token set(Activity from, Class<?> cls, long ms, Step s) {
        token = new Token(); ownerless = from == null; owner = new WeakReference<>(from); screen = cls; step = s; until = SystemClock.elapsedRealtime() + ms; return token;
    }
    static synchronized void disarm() { ownerless = false; token = null; owner = new WeakReference<>(null); screen = null; step = null; until = 0; }
    static synchronized void disarm(Token t) { if (t != null && t == token) disarm(); }
    /** For tests and owner checks: something is waiting now. */
    static synchronized boolean armed() { return step != null && SystemClock.elapsedRealtime() < until; }
    /** This trip is still the one waiting. */
    static synchronized boolean armed(Token t) { return t != null && t == token && armed(); }
    /** The screen this trip waits for is gone (closed by itself, say), so it can never show again. */
    private static boolean gone(Activity o, boolean any) { return !any && (o == null || o.isFinishing() || o.isDestroyed()); }

    /**
     * The trip's goal is reached: run the step on its screen if that is showing, else bring that screen to the front (the
     * step then runs as it resumes). Main thread. GONE (and disarmed) when the screen that started the trip has closed:
     * nothing is opened afresh in its place.
     */
    static Result deliver(Context c, Token t) {
        Activity o; Class<?> target; boolean any;
        synchronized (Comeback.class) { if (!armed(t)) return Result.GONE; o = owner.get(); target = screen; any = isAny(); }
        if (gone(o, any)) { disarm(t); return Result.GONE; }
        Activity a = front.get();
        if (a != null && !a.isFinishing() && (any ? a.getClass() == target : a == o)) { resumed(a); return Result.RAN; }
        if (!TurnOn.permitted(c)) return Result.WAITING;   // Android won't let Unified TV come forward: it runs when the screen shows
        try { c.startActivity(new Intent(c, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); return Result.BROUGHT; }
        catch (RuntimeException e) { return Result.WAITING; }
    }
    private static boolean isAny() { return ownerless; }
    private static boolean ownerless;
    /** A Unified TV screen is showing: the waiting step runs if it is this trip's screen. */
    static void resumed(Activity a) {
        Step s;
        synchronized (Comeback.class) {
            if (step == null) return;
            if (SystemClock.elapsedRealtime() >= until) { disarm(); return; }
            if (a.getClass() != screen) return;
            Activity o = owner.get();
            if (!ownerless && o != a) { if (gone(o, false)) disarm(); return; }
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
