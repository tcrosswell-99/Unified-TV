package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

/**
 * Settings &gt; Appearance &amp; Home &gt; When the TV turns on (owner, 3 November 2026), launcher app only (declared in
 * launcher/AndroidManifest.xml; the preview has no Home and no such page). The glass sidebar over a still Home (OD-19):
 * the three options of {@link TurnOnChoice}, the true one ticked, each with one plain line; then the Home button's own
 * rows, which now live only here: what the Home button opens, Android's own Home choice (spec 29.3, "Make Unified TV the
 * Home screen") and Return to the original TV interface (OD-28; also in Updates &amp; About &gt; Advanced and on Home's
 * safe screen). Choosing 1 or 2 asks the adult PIN; option 3 and Return ask it themselves.
 */
public final class TurnOnChoiceActivity extends GlassScreen {
    static final String ANDROID_CHOICE = "Make Unified TV the Home screen";
    static final String ANDROID_NOTE = "Android's own Home choice only: no network debugging and nothing turned off. Some TVs, like the TCL, keep their own Home button anyway; then choose " + TurnOnChoice.Option.HOME.title + " above.";
    static final String PREVIEW_WORDS = "This is the Unified TV preview. It never changes what the TV shows when it turns on; the separate Unified TV app can.";

    private TurnOnChoice.Flow flow;
    /** Android's own Home choice was asked: what it recorded is read when the household comes back. */
    private boolean homeAsked;

    static void open(Context c) {
        Intent i = new Intent(c, TurnOnChoiceActivity.class);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        flow = new TurnOnChoice.Flow(this, this::show);
        if (!TurnOnChoice.offered(this)) { page(TurnOnChoice.TITLE, "Appearance & Home"); card(); text(PREVIEW_WORDS, 18); button("Back", this::finish); }
    }
    /** Drawn on every return: back from the takeover, Return, the PIN or Android's own screens, it shows what is true now. */
    @Override protected void onResume() {
        super.onResume();
        if (isFinishing() || !TurnOnChoice.offered(this)) return;
        if (homeAsked) homeChecked();
        flow.resumed();
        show();
    }
    TurnOnChoice.Flow flow() { return flow; }

    void show() {
        if (isFinishing()) return;
        page(TurnOnChoice.TITLE, "Appearance & Home");
        card();
        text(TurnOnChoice.INTRO, 18);
        Button ticked = flow.options();
        // The Home button's own rows (formerly Appearance & Home > Home button): only here now.
        group("Home button");
        card();
        boolean ours = DefaultHome.isDefault(this), off = HomeTakeover.stockOff(this), kept = !ours && !off && DefaultHome.fixed(this);
        text(ours ? (off ? TurnOnChoice.HOME_TAKEN : TurnOnChoice.HOME_OURS) : off ? TurnOnChoice.HOME_OFF_OTHER : kept ? DefaultHome.KEPT_WORDS : TurnOnChoice.HOME_ORIGINAL, 16);
        if (!ours && !kept && !off) { row(ANDROID_CHOICE, null, false, () -> EditLock.ask(this, this::homeSetup)); text(ANDROID_NOTE, 15); }
        if (ours || off || DefaultHome.offered(this)) row(HomeTakeoverActivity.RETURN_TITLE, null, false, () -> HomeTakeoverActivity.open(this, false));
        if (off) text(HomeTakeoverActivity.REVIEW_BUILD_WORDS, 15);
        if (ticked != null) focusOn(ticked);
    }

    /** Spec 28.3 onboarding, as it was in Appearance & Home: explain, let Android ask, then confirm what actually happened. */
    private void homeSetup() {
        new AlertDialog.Builder(this).setTitle(ANDROID_CHOICE).setMessage("Pressing Home will open Unified TV instead of the original TV interface. The original interface stays on the TV, and you can switch back at any time from this screen.\n\nYour TV will ask you to choose: pick Unified TV.")
            .setPositiveButton("Continue", (d, w) -> { homeAsked = true; KidsGuard.grantPass(this);
                // No dialog, Home settings or chooser on this TV: nothing can be chosen, so nothing is left switched on.
                if (DefaultHome.offer(this) == DefaultHome.Route.NONE) { homeAsked = false; DefaultHome.withdraw(this); DefaultHome.fixed(this, true); note(DefaultHome.KEPT_WORDS); show(); } })
            .setNegativeButton("Cancel", null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) { super.onActivityResult(request, result, data); if (request == DefaultHome.REQUEST) homeChecked(); }
    /**
     * Back from Android's choice: Home comes to the front (so the household is never left on another Home) and reports
     * what Android recorded (DefaultHome.settle: an unchosen entry is withdrawn, and OD-23 goes off once Home is ours).
     */
    private void homeChecked() {
        if (!homeAsked) return;
        homeAsked = false;
        try { startActivity(DefaultHome.backHome(this)); overridePendingTransition(0, 0); }
        catch (RuntimeException e) { DefaultHome.settle(this, s -> { note(DefaultHome.words(s)); show(); }); }
    }
}
