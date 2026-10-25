package io.unifiedtv.preview;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.*;

/**
 * Spec 32 / v3.3 index: Home's app light as compositor-transformed, pre-rendered layers. Each app's palette is a group of
 * views (a flat base and a few copies of one pre-rendered light sprite tinted by a colour filter); one pre-rendered
 * vignette sits over all of them. Focus changes fade groups' opacity over 400 ms from the moment focus moves; drift only
 * changes the light views' translation and scale. Neither ever redraws a picture: the system's compositor moves and blends
 * the layers, so the Home screen in front of it doesn't redraw for the light at all.
 *
 * <p>The drift is stepped by a delayed message ({@link MemoryBudget#driftStepMs}: 25 steps a second, 15 on a 2 GB TV), not an
 * endless animator, and positions are whole pixels, so a step that would move nothing costs no frame. It runs only while
 * the light can actually be seen and someone is there (memory and CPU pass, owner's TCL, 25 October 2026):
 * <ul>
 * <li>Home in front (resumed), with a key pressed on Home in the last {@link #IDLE_STILL_MS}; or, under the translucent
 *     Settings sheet, for {@link #PREVIEW_MS} after a light setting changed there (SET-GLASS-09 live preview);</li>
 * <li>Home's window shown (not covered by the Wallpaper or another full-screen app) and the screen on;</li>
 * <li>drift allowed by the household's and the TV's own motion settings ({@link #stillReason}).</li>
 * </ul>
 * Drift time only advances while it runs, so a light that rests (idle, covered, screen off) carries on from exactly where it
 * stopped: it never jumps.
 */
final class AmbientLayer extends FrameLayout {
    static final long FADE_MS = 400;
    /** No key on Home for this long: the light rests where it is until the next key (spec 7.5 / module 05: idle). */
    static final long IDLE_STILL_MS = 10 * 60_000L;
    /** Under the Settings sheet, a changed light setting previews this long, then the light rests again. */
    static final long PREVIEW_MS = 60_000L;
    private final long stepMs;
    private final int keep;
    private final LinkedHashMap<String, FrameLayout> groups = new LinkedHashMap<>(8, .75f, true);
    /** The groups, back to front; the vignette sits over them (one for all: it darkens whatever light is showing). */
    private final FrameLayout stage;
    private final ImageView shade;
    private boolean running, drift = true, foreground = true, windowShown = true, screenOn = true;
    private long lastInput = SystemClock.uptimeMillis(), previewUntil, lastStep;
    /** Drift time in seconds: advances only while the light drifts. */
    private double clock = SystemClock.uptimeMillis() / 1000.0;
    private final float[] at = new float[3];
    private final Runnable step = new Runnable() { @Override public void run() {
        if (!running) return;
        long now = SystemClock.uptimeMillis();
        if (!canRun(now)) { stop(now); return; }
        clock += Math.min(now - lastStep, 4 * stepMs) / 1000.0;   // a stalled thread slows the light rather than jumping it
        lastStep = now;
        if (place()) count(now);
        postDelayed(this, stepMs);
    } };
    private String current = "";
    /** The group on show (kept here so place() never looks it up while iterating the access-ordered map). */
    private FrameLayout on;
    private BroadcastReceiver screen;

    AmbientLayer(Context c) {
        super(c);
        MemoryBudget budget = MemoryBudget.get(c);
        stepMs = budget.driftStepMs(); keep = budget.ambientGroups();
        setBackgroundColor(Color.BLACK);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        stage = new FrameLayout(c);
        addView(stage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        shade = new ImageView(c);
        shade.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(shade, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Shows this light (an app id, or a light id from AmbientMesh.lightId), fading over whatever is visible now; the latest call always wins. */
    void show(String id, boolean animate) {
        if (id.equals(current)) return;
        current = id;
        if (shade.getDrawable() == null) shade.setImageBitmap(AmbientMesh.shade());
        FrameLayout target = group(id); on = target;
        target.bringToFront();
        target.animate().cancel();
        if (!animate) {
            target.setAlpha(1f); hideOthers(target);
        } else {
            // From whatever opacity it had (a quick back-and-forth never restarts from black).
            target.animate().alpha(1f).setDuration(Math.round(FADE_MS * (1f - target.getAlpha())))
                    .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                    .withEndAction(() -> { if (id.equals(current)) hideOthers(target); }).start();
        }
        place();
    }

    /** Drift on or off; off leaves the lights at rest (Keep the background still, Reduce motion, the TV's own setting). */
    void drift(boolean on) { drift = on; place(); evaluate(); }
    /** Home is in front (onResume) or not (onPause: another screen, or the Settings sheet, is over it). */
    void foreground(boolean front) { foreground = front; if (front) lastInput = SystemClock.uptimeMillis(); else previewUntil = 0; evaluate(); }
    /** Home in front again: the loop restarts if the light may drift; safe to call any number of times. */
    void resume() { foreground(true); }
    void pause() { foreground(false); }
    /** A key on Home: the light wakes from an idle rest. */
    void input() { lastInput = SystemClock.uptimeMillis(); if (!running) evaluate(); }
    /** A light setting changed under the Settings sheet: let it drift there for a while as a live preview. */
    void previewChange() { if (!foreground) previewUntil = SystemClock.uptimeMillis() + PREVIEW_MS; }
    /** Whether the light is drifting right now (the step loop is running). */
    boolean drifting() { return running; }

    private boolean canRun(long now) {
        return drift && getVisibility() == VISIBLE && windowShown && screenOn
                && (foreground ? now - lastInput < IDLE_STILL_MS : now < previewUntil);
    }
    private void evaluate() { long now = SystemClock.uptimeMillis(); if (canRun(now)) start(now); else stop(now); }
    private void start(long now) {
        if (running) return;
        running = true; lastStep = now; countFrom = now; moves = 0; resting = null;
        removeCallbacks(step); postDelayed(step, stepMs);
    }
    private void stop(long now) {
        if (running) { running = false; count(now); }
        removeCallbacks(step);
        resting = !drift ? null : !screenOn ? "the screen is off" : !windowShown || getVisibility() != VISIBLE ? "Home is covered"
                : foreground ? "no key has been pressed for ten minutes" : "the Settings sheet is over Home";
        if (on != null || !groups.isEmpty()) note(resting);
    }

    // --- what Owner diagnostics can say (read on another screen, so kept as a snapshot)
    private int moves; private long countFrom; private String resting;
    private static volatile int fps = -1; private static volatile long fpsAt, notedAt; private static volatile String lastResting;
    private void count(long now) {
        if (running) moves++;
        long span = now - countFrom;
        if (span >= 2000 || (!running && span >= 500)) { if (span > 0 && moves > 0) { fps = Math.round(moves * 1000f / span); fpsAt = now; } moves = 0; countFrom = now; }
    }
    private static void note(String why) { lastResting = why; notedAt = SystemClock.uptimeMillis(); }
    /**
     * Owner diagnostics: one plain line about Home's background on this TV. "Background: moving · 15 fps" (light steps a
     * second the last time Home was on screen), or "Background: still because …" with the reason, and when it last rested.
     */
    static String diagnosis(android.content.SharedPreferences p, boolean tvAnimations, long now) {
        String why = stillReason(p.getBoolean("stillBackground", false), p.getBoolean("reduceMotion", false), p.getBoolean("lightweight", false), tvAnimations);
        if (why != null) return "Background: still because " + Character.toLowerCase(why.charAt(0)) + why.substring(1);
        int f = fps; long at = fpsAt;
        String line = f > 0 ? "Background: moving · " + f + " fps (measured on Home " + ago(now - at) + ")" : "Background: should be moving; not measured yet (show Home for a few seconds, then look again)";
        String r = lastResting;
        if (r != null && f > 0) line += "\nResting now because " + r + " (" + ago(now - notedAt) + "); it carries on from where it stopped.";
        return line + "\nLight travel: up to " + Math.round(AmbientMesh.TRAVEL * 5) + " % of the screen over 20 to 40 seconds.";
    }
    private static String ago(long ms) { long s = Math.max(0, ms / 1000); return s < 60 ? s + " s ago" : s / 60 + " min ago"; }

    /**
     * The saved settings that change this light (SET-GLASS-09): Home's preference listener re-applies them through
     * {@code aim()} the moment one changes, even under the Settings sheet, so switching motion back on lets the light drift
     * behind the sheet as a live preview, and Reduce motion, Keep still or Lightweight stop it at once.
     */
    static final java.util.Set<String> MOTION_KEYS=java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList("reduceMotion","stillBackground","lightweight")));
    /** Every saved setting the light shows (SET-022): the motion keys, plus intensity and colour mode. */
    static final java.util.Set<String> LIGHT_KEYS;
    static { java.util.Set<String> all = new java.util.HashSet<>(MOTION_KEYS); all.add(AmbientMesh.INTENSITY); all.add(AmbientMesh.COLOUR); LIGHT_KEYS = java.util.Collections.unmodifiableSet(all); }

    /** Said, in plain words, wherever the household looks for why Home's background isn't moving. */
    static final String TV_ANIMATIONS_OFF = "This TV has its animations switched off, so the Home background stays still. To see it move gently, switch animations back on in the TV's own settings.";
    /**
     * Why the light is still, or null when it drifts. Drift is on by default; it stops only for the household's own choices
     * (Keep the Home background still, Reduce motion, Lightweight rendering) or the TV's own animation setting, which
     * Unified TV respects rather than overrides (spec 32 / PRODUCT_SPEC: honour the system's reduced-motion preference).
     */
    static String stillReason(boolean still, boolean calm, boolean light, boolean tvAnimations) {
        if (calm) return "Reduce motion is on, so the Home background stays still.";
        if (light) return "Lightweight rendering is on, so Home shows a still picture of each app's light.";
        if (still) return "Keep the Home background still is on.";
        if (!tvAnimations) return TV_ANIMATIONS_OFF;
        return null;
    }
    /** The TV's own animation setting: false when the system has animations switched off (duration scale 0). */
    static boolean tvAnimations() { return android.animation.ValueAnimator.areAnimatorsEnabled(); }

    /**
     * onTrimMemory: Home hidden (UI_HIDDEN or worse) lets every group and the shared pictures go; they are rebuilt from the
     * palette when Home shows again. While Home is seen and memory runs low, only the groups not on show go.
     */
    void trim(boolean hidden) {
        for (Iterator<Map.Entry<String, FrameLayout>> it = groups.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, FrameLayout> e = it.next();
            if (!hidden && e.getValue() == on) continue;
            e.getValue().animate().cancel(); stage.removeView(e.getValue()); it.remove();
        }
        if (hidden) { on = null; current = ""; shade.setImageDrawable(null); AmbientMesh.trim(); }
    }
    /** For tests and diagnostics: the group on show, or null. */
    FrameLayout shown() { return on; }

    private void hideOthers(View keep) {
        for (FrameLayout g : groups.values()) if (g != keep) { g.animate().cancel(); g.setAlpha(0f); }
    }

    private FrameLayout group(String id) {
        FrameLayout g = groups.get(id);
        if (g != null) return g;
        AmbientMesh.Palette p = AmbientMesh.palette(id); float strength = AmbientMesh.strength(id);
        g = new FrameLayout(getContext());
        g.setBackgroundColor(p.base);
        g.setAlpha(0f);
        Bitmap sprite = AmbientMesh.sprite();
        for (AmbientMesh.Light l : p.lights) {
            ImageView light = new ImageView(getContext());
            light.setImageBitmap(sprite);
            light.setScaleType(ImageView.ScaleType.FIT_XY);
            light.setColorFilter(new PorterDuffColorFilter(AmbientMesh.colour(l, strength), PorterDuff.Mode.SRC_IN));
            light.setTag(l);
            g.addView(light, new LayoutParams(1, 1));   // sized in place()
        }
        stage.addView(g, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        groups.put(id, g);
        while (groups.size() > keep) {
            Map.Entry<String, FrameLayout> eldest = groups.entrySet().iterator().next();
            if (eldest.getKey().equals(current)) break;
            eldest.getValue().animate().cancel(); stage.removeView(eldest.getValue()); groups.remove(eldest.getKey());
        }
        return g;
    }

    /**
     * Positions every visible light for this moment of drift time: translation and scale only (no redraw), in whole pixels
     * and 1/1024 steps of scale, so an unchanged position costs nothing. True when anything moved.
     */
    private boolean place() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return false;
        boolean moved = false;
        for (FrameLayout g : groups.values()) {
            if (g.getAlpha() <= 0f && g != on) continue;
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = g.getChildAt(i);
                if (!(v.getTag() instanceof AmbientMesh.Light)) continue;
                AmbientMesh.Light l = (AmbientMesh.Light) v.getTag();
                int size = Math.round(2 * l.r * w);
                if (v.getLayoutParams().width != size) { v.setLayoutParams(new LayoutParams(size, size)); v.setPivotX(size / 2f); v.setPivotY(size / 2f); }
                if (drift) AmbientMesh.position(l, clock, at); else AmbientMesh.rest(l, at);
                float x = Math.round(at[0] * w - size / 2f), y = Math.round(at[1] * h - size / 2f), s = Math.round(at[2] * 1024) / 1024f;
                if (v.getTranslationX() != x || v.getTranslationY() != y || v.getScaleX() != s) moved = true;
                v.setTranslationX(x); v.setTranslationY(y); v.setScaleX(s); v.setScaleY(s);
            }
        }
        return moved;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); post(this::place); }
    // The loop follows the window and the screen as well as Home's own pause and resume, so it always restarts once Home is seen again.
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            screenOn = pm == null || pm.isInteractive();
            screen = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { screenOn = !Intent.ACTION_SCREEN_OFF.equals(i.getAction()); evaluate(); } };
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON);
            getContext().registerReceiver(screen, f);
        } catch (RuntimeException e) { screen = null; screenOn = true; }
        evaluate();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); windowShown = visibility == VISIBLE; evaluate(); }
    @Override protected void onVisibilityChanged(View changed, int visibility) { super.onVisibilityChanged(changed, visibility); if (changed == this) evaluate(); }
    @Override protected void onDetachedFromWindow() {
        running = false; removeCallbacks(step);
        if (screen != null) { try { getContext().unregisterReceiver(screen); } catch (RuntimeException ignored) { } screen = null; }
        super.onDetachedFromWindow();
    }
}
