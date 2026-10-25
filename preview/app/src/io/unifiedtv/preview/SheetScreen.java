package io.unifiedtv.preview;

import android.os.Build;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

/**
 * OD-11 (owner, 25 October 2026; SPEC §3, DESIGN_SYSTEM "Settings and setup always use the sidebar"): a settings or setup
 * screen built on Screen's page()/text()/button() opens as the module 13 glass sidebar over a still Home: half the screen,
 * about three quarters for a wide task (wide()), from the right, with Settings' own frost, tint, sheen and edge
 * (SettingsGlass), its focus rows and its slide in (a short crossfade with Reduce motion or the TV's animations off). The
 * activity must use the translucent SettingsGlassTheme so Home stays visible and still behind it.
 *
 * page() keeps the sheet and replaces only its contents, so moving between steps never re-slides or flashes. A screen that
 * shows content full screen in between (the Gallery slideshow) gets the sheet back on its next page().
 */
class SheetScreen extends Screen {
    private FrameLayout root, sheet; private LinearLayout inner; private ScrollView scroll; private TextView heading;
    private boolean wide; private float unit = 1; private int screenWidth = 1920;
    /** Three quarters wide for a task that needs it (module 13 "wide tasks"); half width otherwise. */
    void wide(boolean on) { if (wide == on) return; wide = on; if (sheet != null) sheet.setLayoutParams(new FrameLayout.LayoutParams(width(), -1, Gravity.END)); }
    private android.content.SharedPreferences prefs() { return getSharedPreferences("unified-tv-preview", 0); }
    int px(float at1080) { return Math.round(at1080 * unit); }
    private float shadow() { return px(28); }
    boolean calm() { return prefs().getBoolean("reduceMotion", false) || !AmbientLayer.tvAnimations(); }
    private boolean strong() { return prefs().getBoolean("highContrast", false); }
    private boolean solid() { return prefs().getBoolean("opaque", false) || strong(); }
    private boolean blurs() { return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !prefs().getBoolean("lightweight", false) && !((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).isLowRamDevice(); }
    private int width() { float dp = screenWidth / getResources().getDisplayMetrics().density; return Math.round(SettingsOverlay.fraction(wide, dp) * screenWidth + shadow()); }
    /** For tests: the sheet, or null before the first page. */
    View sheet() { return sheet; }

    @Override void page(String title) {
        boolean fresh = root == null || root.getParent() == null;
        if (fresh) build();
        column.removeAllViews(); heading.setText(title); scroll.scrollTo(0, 0);
        if (fresh) { setContentView(root); slideIn(); }
    }
    private void build() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics(); screenWidth = Math.max(1, m.widthPixels); unit = Math.max(.5f, Math.min(m.widthPixels / 1920f, m.heightPixels / 1080f));
        root = new FrameLayout(this); sheet = new FrameLayout(this);
        float radius = px(24); boolean solid = solid();
        if (!solid) {
            View frost = new SettingsGlass.Frost(this, prefs().getString("scene", "neutral"), blurs(), shadow(), radius, screenWidth);
            if (blurs() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) frost.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(px(24), px(24), android.graphics.Shader.TileMode.CLAMP));
            sheet.addView(frost, new FrameLayout.LayoutParams(-1, -1));
        }
        View glass = new View(this); glass.setFocusable(false); glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        glass.setBackground(new SettingsGlass.Sheet(shadow(), radius, solid)); sheet.addView(glass, new FrameLayout.LayoutParams(-1, -1));
        inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL); inner.setPadding(Math.round(shadow()) + px(56), px(44), px(56), px(24));
        heading = new TextView(this); heading.setTextColor(SettingsGlass.TEXT); heading.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(36));
        heading.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)); heading.setAccessibilityHeading(true);
        inner.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(this); scroll.setFocusable(false); scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS); scroll.setVerticalFadingEdgeEnabled(true); scroll.setFadingEdgeLength(px(36));
        column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(0, px(8), 0, px(24));
        scroll.addView(column, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, 0, 1); under.topMargin = px(14); inner.addView(scroll, under);
        sheet.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        root.addView(sheet, new FrameLayout.LayoutParams(width(), -1, Gravity.END));
    }
    private void slideIn() {
        if (calm()) { sheet.setAlpha(0); sheet.post(() -> sheet.animate().alpha(1).setDuration(SettingsOverlay.CROSSFADE_MS).start()); return; }
        sheet.setTranslationX(screenWidth); sheet.post(() -> sheet.animate().translationX(0).setDuration(SettingsOverlay.OPEN_MS).setInterpolator(new DecelerateInterpolator(2f)).start());
    }
    /** Closes with no second window animation (the sheet is the motion). */
    @Override public void finish() { super.finish(); overridePendingTransition(0, 0); }

    /** Settings' own rows: barely raised at rest, a pale lit fill with a crisp edge on focus. */
    @Override Button styled(String s, Runnable action) {
        Button b = super.styled(s, action);
        if (root == null) return b;
        b.setBackground(SettingsGlass.row(px(20), Math.max(2, px(2)), strong(), calm() ? 0 : SettingsOverlay.FOCUS_MS)); b.setStateListAnimator(null);
        b.setTextColor(SettingsGlass.textColours()); b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); b.setPadding(px(24), px(12), px(24), px(12));
        return b;
    }
}
