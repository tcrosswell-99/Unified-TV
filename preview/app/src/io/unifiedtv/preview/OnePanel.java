package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Only one panel is visible at a time (owner, 27 October 2026; DESIGN_SYSTEM "Settings and setup always use the sidebar",
 * SPEC OD-19 note). Glass is see-through, so a sidebar opened over another sidebar (Build My TV over the guided setup, a
 * folder picker over Settings) let the lower page's words show through the upper one. Now the panel that opens another
 * panel hides itself: a quick fade (none with Reduce motion or the TV's animations off), then it is not drawn at all, so
 * neither the eye nor TalkBack meets two pages at once. When the upper panel closes, the lower one comes back with the
 * control that opened it in focus.
 *
 * Used by {@link GlassScreen} for every setup screen and by {@link SettingsActivity} for its own sheet. Any of Unified
 * TV's own screens counts, not only another sheet: some open a sheet on their way (Films &amp; TV's "setup" request hands
 * over to the folder chooser), and content (the library, the player) covers the screen anyway, so hiding under it costs
 * nothing. Other apps (Google Play, the TV's settings) are left alone: they cover the screen themselves.
 */
final class OnePanel {
    /** Out quickly, so the upper panel slides in over nothing; back a little more gently. */
    static final int OUT_MS = 90, IN_MS = SettingsOverlay.CROSSFADE_MS;

    private View hidden, focus;
    private CharSequence focusWords;

    /** True when {@code i} opens one of Unified TV's own screens (a sheet, or a screen that may open one), not another app. */
    static boolean opensPanel(Context c, Intent i) {
        return i != null && i.getComponent() != null && c.getPackageName().equals(i.getComponent().getPackageName());
    }

    /** True while this panel is hidden under another. */
    boolean hidden() { return hidden != null; }

    /** Another panel is opening over {@code sheet}: remember what had focus, fade out, then stop drawing. */
    void hide(View sheet, boolean calm) {
        if (sheet == null || hidden == sheet) return;
        View f = sheet.findFocus(); focus = f; focusWords = words(f); hidden = sheet;
        sheet.animate().cancel();
        if (calm) { sheet.setAlpha(0); sheet.setVisibility(View.INVISIBLE); return; }
        sheet.animate().alpha(0).setDuration(OUT_MS).withEndAction(() -> { if (hidden == sheet) sheet.setVisibility(View.INVISIBLE); }).start();
    }

    /**
     * The panel above has gone (or never opened): show {@code sheet} again and put focus back on the control that opened
     * the other panel, or on the same words if the page was redrawn meanwhile. Call it from onPostResume, after the page's
     * own onResume redraw.
     */
    void show(View sheet, boolean calm) {
        if (hidden == null || sheet == null) return;
        hidden = null; sheet.animate().cancel(); sheet.setVisibility(View.VISIBLE);
        if (calm) sheet.setAlpha(1); else sheet.animate().alpha(1).setDuration(IN_MS).start();
        View was = focus; CharSequence words = focusWords; focus = null; focusWords = null;
        sheet.post(() -> {
            View target = was != null && was.isAttachedToWindow() && was.isShown() ? was : find(sheet, words);
            if (target != null) target.requestFocus();
        });
    }

    private static CharSequence words(View v) {
        if (v == null) return null;
        CharSequence d = v.getContentDescription();
        if (d != null && d.length() > 0) return d.toString();
        return v instanceof TextView ? ((TextView) v).getText().toString() : null;
    }
    /** The focusable control with these words, for a page that was drawn again while it was hidden. */
    private static View find(View v, CharSequence words) {
        if (words == null || v == null || v.getVisibility() != View.VISIBLE) return null;
        if (v.isFocusable() && words.toString().equals(String.valueOf(words(v)))) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { View f = find(((ViewGroup) v).getChildAt(i), words); if (f != null) return f; }
        return null;
    }
}
