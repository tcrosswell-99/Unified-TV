package io.unifiedtv.preview;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * OD-28 crash safety, launcher app only. While Unified TV holds the TV's Home with the TV's own Home turned off, a Home that
 * crashes at every start would leave the TV with nothing to show. So, from the very top of the app's start
 * (PreviewApp.onCreate, {@link #appStart}), an uncaught-exception handler counts the crashes that matter, within a minute
 * of the app or Home starting, saved at once before Android ends the process, and then hands the crash on to the handler
 * that was there before. Counted: a crash during the app's start before any screen has resumed (any thread), a crash
 * while Home is the resumed screen (any thread), and a crash on the main thread while Home is still starting. Not counted:
 * crashes in other screens once they are in front, or after the first minute. A Home that lasts a minute clears the count.
 *
 * <p>At the next start, two counted crashes in a row while Unified TV holds Home make PreviewApp's own start steps
 * best-effort ({@link #appStart} returns true) and open a minimal safe screen in place of Home, checked before anything
 * else Home does: "Return to the original TV interface" and "Try again", with the TV-only and computer ways back. Nothing
 * here reads or reassigns keys, and nothing calls finish() on Home.
 */
final class HomeSafety {
    private HomeSafety() {}
    static final String PREFS = "unified-tv-home-safety", CRASHES = "earlyCrashes";
    static final long EARLY_MS = 60_000;
    static final int LIMIT = 2;
    static final String TITLE = "Unified TV stopped twice while starting";
    static final String WORDS = "To keep this TV usable, Home is paused here. Go back to the TV's original interface, or try Unified TV again.";
    static final String RETURN = "Return to the original TV interface", AGAIN = "Try again";

    /** When this process started (PreviewApp.onCreate), and when Home last started; 0 when not yet. */
    private static volatile long processStart, homeStart;
    /** Some screen has resumed since the process started; Home is the resumed screen; Home is created but not yet resumed. */
    private static volatile boolean anyResumed, homeResumed, homeStarting;
    private static volatile Context app;
    private static volatile boolean installed;
    private static volatile Application watched;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable survived = () -> { Context c = app; if (c != null) reset(c); };

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }
    static int crashes(Context c) { return prefs(c).getInt(CRASHES, 0); }
    static void reset(Context c) { prefs(c).edit().remove(CRASHES).commit(); }

    /**
     * The first thing PreviewApp.onCreate does. Launcher app only, and only while Unified TV has the TV's own Home turned
     * off: the start time, the screen watcher and the handler. Returns true when the safe screen will show, so the app's
     * own start steps should not be allowed to crash it again.
     */
    static boolean appStart(Application a) {
        boolean holding;
        try { holding = DefaultHome.available(a) && HomeTakeover.stockOff(a); } catch (RuntimeException e) { holding = false; }
        if (!holding) return false;
        app = a.getApplicationContext(); processStart = SystemClock.elapsedRealtime(); homeStart = 0; anyResumed = false; homeResumed = false; homeStarting = false;
        if (watched != a) { watched = a; a.registerActivityLifecycleCallbacks(SCREENS); }
        install();
        try { return needed(a); } catch (RuntimeException e) { return crashes(a) >= LIMIT; }
    }

    /** Home is starting: its minute starts (cleared after a minute without a crash). */
    static void started(Activity a) {
        if (!DefaultHome.available(a)) return;
        app = a.getApplicationContext(); homeStart = SystemClock.elapsedRealtime(); homeStarting = true;
        main.removeCallbacks(survived); main.postDelayed(survived, EARLY_MS);
    }

    private static void install() {
        if (installed) return;
        installed = true;
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { Context c = app; if (c != null) crashed(c, thread == Looper.getMainLooper().getThread(), SystemClock.elapsedRealtime()); }
            catch (Throwable ignored) { /* never in the way of the crash */ }
            if (before != null) before.uncaughtException(thread, error);   // Android's own handler still ends the process
        });
    }

    /** Which screen is in front, for {@link #counts}. */
    static final Application.ActivityLifecycleCallbacks SCREENS = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity a) { anyResumed = true; homeResumed = a instanceof UnifiedPreviewActivity; if (homeResumed) homeStarting = false; }
        @Override public void onActivityPaused(Activity a) { if (a instanceof UnifiedPreviewActivity) homeResumed = false; }
        @Override public void onActivityCreated(Activity a, Bundle b) {}
        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        @Override public void onActivityDestroyed(Activity a) { if (a instanceof UnifiedPreviewActivity) homeStarting = false; }
    };

    /** Pure: whether a crash counts towards the safe screen. */
    static boolean counts(boolean mainThread, boolean anyResumed, boolean homeResumed, boolean homeStarting, long sinceStartMs) {
        if (sinceStartMs < 0 || sinceStartMs > EARLY_MS) return false;
        return !anyResumed || homeResumed || (homeStarting && mainThread);
    }

    /** A crash at {@code now}: counted (written at once, the process is ending) only when {@link #counts} says so. */
    static void crashed(Context c, boolean mainThread, long now) {
        long since = homeStart > 0 ? homeStart : processStart;
        if (since <= 0) return;
        if (!counts(mainThread, anyResumed, homeResumed, homeStarting, now - since)) return;
        prefs(c).edit().putInt(CRASHES, crashes(c) + 1).commit();
    }

    /** Unified TV is the TV's Home now, or turned the TV's own Home off. */
    static boolean holdsHome(Context c) { return HomeTakeover.stockOff(c) || DefaultHome.isDefault(c); }

    /** At Home's start: show the safe screen instead of Home. */
    static boolean needed(Context c) { return DefaultHome.available(c) && crashes(c) >= LIMIT && holdsHome(c); }

    /** The safe screen: plain views only, nothing from Home that could crash again. */
    static View view(Activity a, Runnable giveBack, Runnable again) {
        float unit = Math.max(.5f, Math.min(a.getResources().getDisplayMetrics().widthPixels / 1920f, a.getResources().getDisplayMetrics().heightPixels / 1080f));
        FrameLayout root = new FrameLayout(a); root.setBackgroundColor(Color.rgb(12, 16, 24));
        LinearLayout column = new LinearLayout(a); column.setOrientation(LinearLayout.VERTICAL); column.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(48 * unit); column.setPadding(pad, pad, pad, pad);
        column.addView(words(a, TITLE, 40 * unit, Color.WHITE));
        column.addView(words(a, WORDS, 26 * unit, Color.rgb(200, 210, 225)));
        Button back = button(a, RETURN, unit, giveBack), retry = button(a, AGAIN, unit, again);
        column.addView(back); column.addView(retry);
        // Without Unified TV at all: the TV's own Settings, or a computer.
        String stock = null;
        try { stock = HomeTakeover.stockToRestore(a); } catch (RuntimeException ignored) { /* the usual one below */ }
        if (stock == null) stock = "com.google.android.tvlauncher";
        column.addView(words(a, "Without Unified TV: " + HomeTakeover.tvOnlySteps(HomeTakeover.label(stock)) + " From a computer: " + HomeTakeover.pcFix(stock), 22 * unit, Color.rgb(170, 182, 200)));
        root.addView(column, new FrameLayout.LayoutParams(Math.round(1100 * unit), -2, Gravity.CENTER));
        back.requestFocus();
        return root;
    }
    private static TextView words(Context c, String s, float px, int colour) {
        TextView t = new TextView(c); t.setText(s); t.setTextColor(colour); t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px); t.setGravity(Gravity.CENTER);
        t.setPadding(0, Math.round(px / 2), 0, Math.round(px / 2)); return t;
    }
    private static Button button(Context c, String s, float unit, Runnable action) {
        Button b = new Button(c); b.setText(s); b.setAllCaps(false); b.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * unit);
        b.setFocusable(true); b.setFocusableInTouchMode(true); b.setMinHeight(Math.round(72 * unit)); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = Math.round(16 * unit); b.setLayoutParams(lp);
        return b;
    }
}
