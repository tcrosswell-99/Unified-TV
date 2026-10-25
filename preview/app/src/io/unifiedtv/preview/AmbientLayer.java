package io.unifiedtv.preview;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.*;

/**
 * Spec 32 / v3.3 index: Home's app light as compositor-transformed, pre-rendered layers. Each app's palette is a group of
 * views (a flat base, a few copies of one pre-rendered light sprite tinted by a colour filter, and the pre-rendered
 * vignette). Focus changes fade groups' opacity over 400 ms from the moment focus moves; drift only changes the light
 * views' translation and scale. Neither ever redraws a picture: the system's compositor moves and blends the layers, so
 * the Home screen in front of it doesn't redraw for the light at all. The drift is stepped by a delayed message about 30
 * times a second (not an endless animator), so it stops cleanly and never keeps the main thread permanently busy.
 */
final class AmbientLayer extends FrameLayout {
    static final long FADE_MS = 400;
    /** Groups kept ready (app palettes seen recently); older ones are dropped. */
    private static final int KEEP = 4;
    private final LinkedHashMap<String, FrameLayout> groups = new LinkedHashMap<>(8, .75f, true);
    private static final long STEP_MS = 33;
    private boolean running;
    private final Runnable step = new Runnable() { @Override public void run() {
        if (!running) return;
        place(android.os.SystemClock.uptimeMillis() / 1000.0);
        postDelayed(this, STEP_MS);
    } };
    private String current = "";
    private boolean drift = true;

    AmbientLayer(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    /** Shows this app's light, fading over whatever is visible now; the latest call always wins. */
    void show(String id, boolean animate) {
        if (id.equals(current)) return;
        current = id;
        FrameLayout target = group(id);
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
        place(android.os.SystemClock.uptimeMillis() / 1000.0);
    }

    /** Drift on or off; off leaves the lights at rest (Keep the background still, Reduce motion). */
    void drift(boolean on) { drift = on; place(android.os.SystemClock.uptimeMillis() / 1000.0); if (!on) pause(); else resume(); }
    void resume() { if (drift && !running && getVisibility() == VISIBLE) { running = true; postDelayed(step, STEP_MS); } }
    void pause() { running = false; removeCallbacks(step); }

    private void hideOthers(View keep) {
        for (FrameLayout g : groups.values()) if (g != keep) { g.animate().cancel(); g.setAlpha(0f); }
    }

    private FrameLayout group(String id) {
        FrameLayout g = groups.get(id);
        if (g != null) return g;
        AmbientMesh.Palette p = AmbientMesh.palette(id);
        g = new FrameLayout(getContext());
        g.setBackgroundColor(p.base);
        g.setAlpha(0f);
        Bitmap sprite = AmbientMesh.sprite();
        for (AmbientMesh.Light l : p.lights) {
            ImageView light = new ImageView(getContext());
            light.setImageBitmap(sprite);
            light.setScaleType(ImageView.ScaleType.FIT_XY);
            light.setColorFilter(new PorterDuffColorFilter(l.colour, PorterDuff.Mode.SRC_IN));
            light.setTag(l);
            g.addView(light, new LayoutParams(1, 1));   // sized in place()
        }
        ImageView shade = new ImageView(getContext());
        shade.setImageBitmap(AmbientMesh.shade());
        shade.setScaleType(ImageView.ScaleType.FIT_XY);
        g.addView(shade, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(g, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        groups.put(id, g);
        while (groups.size() > KEEP) {
            Map.Entry<String, FrameLayout> eldest = groups.entrySet().iterator().next();
            if (eldest.getKey().equals(current)) break;
            removeView(eldest.getValue()); groups.remove(eldest.getKey());
        }
        return g;
    }

    /** Positions every visible light for this moment: translation and scale only (no redraw). */
    private void place(double seconds) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        for (FrameLayout g : groups.values()) {
            if (g.getAlpha() <= 0f && g != groups.get(current)) continue;
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = g.getChildAt(i);
                if (!(v.getTag() instanceof AmbientMesh.Light)) continue;
                AmbientMesh.Light l = (AmbientMesh.Light) v.getTag();
                int size = Math.round(2 * l.r * w);
                if (v.getLayoutParams().width != size) { v.setLayoutParams(new LayoutParams(size, size)); v.setPivotX(size / 2f); v.setPivotY(size / 2f); }
                float[] at = drift ? AmbientMesh.position(l, seconds) : new float[]{l.x, l.y, 1.03f};
                v.setTranslationX(at[0] * w - size / 2f); v.setTranslationY(at[1] * h - size / 2f);
                v.setScaleX(at[2]); v.setScaleY(at[2]);
            }
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); post(() -> place(android.os.SystemClock.uptimeMillis() / 1000.0)); }
    @Override protected void onDetachedFromWindow() { pause(); super.onDetachedFromWindow(); }
}
