package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
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
 * OD-28 crash safety, launcher app only. While Unified TV is the TV's Home (and above all once the TV's own Home is turned
 * off), a Home that crashes at every start would leave the TV with nothing to show. So: an uncaught-exception handler
 * counts crashes that happen within a minute of Home starting (saved at once, before Android ends the process); a Home
 * that lasts a minute clears the count. At the next start, two such crashes in a row while Unified TV holds Home open a
 * minimal safe screen instead of Home: "Return to the original TV interface" and "Try again". Nothing here reads or
 * reassigns keys, and nothing calls finish() on Home.
 */
final class HomeSafety {
    private HomeSafety() {}
    static final String PREFS = "unified-tv-home-safety", CRASHES = "earlyCrashes";
    static final long EARLY_MS = 60_000;
    static final int LIMIT = 2;
    static final String TITLE = "Unified TV stopped twice while starting";
    static final String WORDS = "To keep this TV usable, Home is paused here. Go back to the TV's original interface, or try Unified TV again.";
    static final String RETURN = "Return to the original TV interface", AGAIN = "Try again";

    private static volatile long startedAt;
    private static volatile Context app;
    private static volatile boolean installed;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable survived = () -> { Context c = app; if (c != null) reset(c); };

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }
    static int crashes(Context c) { return prefs(c).getInt(CRASHES, 0); }
    static void reset(Context c) { prefs(c).edit().remove(CRASHES).commit(); }

    /** Home is starting (launcher app only): the minute starts, and the handler is in place. */
    static void started(Activity a) {
        if (!DefaultHome.available(a)) return;
        app = a.getApplicationContext(); startedAt = SystemClock.elapsedRealtime();
        main.removeCallbacks(survived); main.postDelayed(survived, EARLY_MS);
        if (installed) return;
        installed = true;
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { Context c = app; if (c != null) crashed(c, SystemClock.elapsedRealtime()); } catch (Throwable ignored) { /* never in the way of the crash */ }
            if (before != null) before.uncaughtException(thread, error);
        });
    }

    /** A crash at {@code now}: counted only within a minute of Home starting. Written at once, the process is ending. */
    static void crashed(Context c, long now) {
        long since = startedAt;
        if (since <= 0 || now < since || now - since > EARLY_MS) return;
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
