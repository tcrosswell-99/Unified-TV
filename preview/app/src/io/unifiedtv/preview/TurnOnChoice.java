package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;

/**
 * "When the TV turns on" (owner, 3 November 2026: "Will Unified TV load instantly when the TV wakes from standby?"). One
 * clear choice in place of two separate rows, in the launcher app only (the preview has no Home and never shows it):
 * Settings &gt; Appearance &amp; Home &gt; When the TV turns on ({@link TurnOnChoiceActivity}, the glass sidebar), and the
 * same choice as an optional step of the guided setup ({@link FirstRunActivity}).
 *
 * <ol>
 * <li>{@link Option#OWN_HOME}: nothing changes (the default). OD-23's switch is off.</li>
 * <li>{@link Option#QUICK}: OD-23's existing switch ({@link TurnOn}) is on. It needs Android's "Display over other apps",
 *     asked for (or the one-time computer step given) exactly as before, and said plainly while it is missing.</li>
 * <li>{@link Option#HOME}: the existing OD-28 Home takeover ({@link HomeTakeoverActivity}), with its adult PIN, the TV's
 *     "Allow USB debugging?" prompt, the explanation before anything changes and the way back. When it completes, OD-23's
 *     switch is turned off ({@link #homeTaken}): the Home button opens Unified TV, so it isn't needed.</li>
 * </ol>
 * The tick shows what is true on this TV now, never only what was last pressed: Unified TV holding Home (the Home button
 * opens it, or OD-28 has the TV's own Home off) is option 3, OD-23 on is option 2, anything else option 1. Choosing 1 or 2
 * while Unified TV holds Home opens Return to the original TV interface first, with a line saying why; the choice is
 * applied only once Home has been handed back. Home is never withdrawn silently.
 *
 * <p>Module 03 is unchanged: nothing here reads, intercepts or reassigns a key.
 */
final class TurnOnChoice {
    private TurnOnChoice() {}

    static final String TITLE = "When the TV turns on";
    static final String INTRO = "Choose what you see when the TV starts or wakes from standby. Press the one you want.";
    static final String GUIDE_INTRO = "What should you see when the TV starts or wakes from standby? Press one, or press Skip to choose later in Settings.";
    static final String ROW_LINE = "What you see when the TV starts or wakes from standby.";
    static final String SKIP = "Skip — choose later in Settings";
    /** The chosen option's chip, and what TalkBack says for it. */
    static final String TICK = "✓", CHOSEN = "chosen";
    /** The guided setup notes whether this step was answered ({@link #summary}); nothing else is kept. */
    static final String GUIDE = "setup.firstRun.turnOn", GUIDE_CHOSEN = "chosen", GUIDE_SKIPPED = "skipped";
    static final String ALREADY_HOME = "Unified TV is already the TV's Home: the Home button opens it.";
    /** What the Home button does now (the old Home button rows' words, on the Settings page). */
    static final String HOME_OURS = "The Home button opens Unified TV.", HOME_ORIGINAL = "The Home button opens the original TV interface.",
        HOME_TAKEN = "The Home button opens Unified TV. The TV's own Home is turned off; Return to the original TV interface turns it back on.",
        HOME_OFF_OTHER = "Unified TV turned the TV's own Home off, but the Home button doesn't open Unified TV. Return to the original TV interface turns it back on.";
    static final String PERMISSION_MISSING = "Not working yet: Unified TV doesn't have Android's \"Display over other apps\". ";

    enum Option {
        OWN_HOME("Show the TV's own Home", "TV's own Home", "Nothing changes: the TV's own Home appears, as it does now."),
        QUICK("Open Unified TV (quick)", "Quick", "The TV's own Home shows for a moment, then Unified TV comes to the front."),
        HOME("Make Unified TV the TV's Home (instant)", "Instant", "Unified TV appears straight away, and the Home button on your remote opens it too.");
        final String title, chip, line;
        Option(String title, String chip, String line) { this.title = title; this.chip = chip; this.line = line; }
    }

    // --- pure
    /** Option 3 while Unified TV holds Home, else 2 while OD-23's switch is on, else 1. */
    static Option current(boolean holdsHome, boolean turnOnSwitchedOn) {
        return holdsHome ? Option.HOME : turnOnSwitchedOn ? Option.QUICK : Option.OWN_HOME;
    }
    /** Unified TV holds Home: the Home button opens it, or OD-28 turned the TV's own Home off (Return is then the way back). */
    static boolean holdsHome(boolean homeIsOurs, boolean stockOff) { return homeIsOurs || stockOff; }
    /** What Return to the original TV interface says first when it was opened by choosing {@code o}. */
    static String returnFirst(Option o) {
        return "To choose " + o.title + ", Unified TV first hands the Home button back to the TV's own Home.";
    }

    // --- this TV
    /** Only the launcher app has it: the preview never has a Home entry. */
    static boolean offered(Context c) { return DefaultHome.available(c); }
    static boolean holdsHome(Context c) {
        boolean ours = false;
        try { ours = DefaultHome.isDefault(c); } catch (RuntimeException ignored) { /* unknown: not ours */ }
        return holdsHome(ours, HomeTakeover.stockOff(c));
    }
    static Option current(Context c) { return current(holdsHome(c), TurnOn.enabled(c)); }

    /** Unified TV now answers Home (OD-28 done, or Android's own choice): OD-23's switch isn't needed, so it goes off. */
    static void homeTaken(Context c) {
        if (!DefaultHome.available(c) || !TurnOn.enabled(c)) return;
        TurnOn.enabled(c, false); TurnOn.sync(c);
    }

    /** What the chosen option still needs on this TV, in plain words; null when nothing. */
    static String needs(Context c, Option now) {
        if (now != Option.QUICK || TurnOn.permitted(c)) return null;
        return PERMISSION_MISSING + (TurnOn.permissionScreen(c) != null ? TurnOn.PERMISSION_WORDS + " Press " + Option.QUICK.title + " to open that setting."
            : TurnOn.PERMISSION_WORDS + " " + TurnOn.NO_SCREEN_WORDS + " " + TurnOn.adb(c));
    }

    /** The guided setup's summary line: what is true now if the step was answered, otherwise that it was skipped. */
    static String summary(Context c) {
        String answer = c.getSharedPreferences(HouseholdSettings.PREFS, 0).getString(GUIDE, "");
        if (!GUIDE_CHOSEN.equals(answer)) return TITLE + ": " + FirstRunActivity.SKIPPED;
        Option now = current(c);
        return TITLE + ": " + now.title + (needs(c, now) != null ? " (needs \"Display over other apps\")" : "");
    }
    static void answered(Context c, String how) { c.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putString(GUIDE, how).apply(); }

    /**
     * The choice on one page (the Settings page or the guided setup's step). The page draws {@link #options}, calls
     * {@link #resumed} from onResume and redraws when {@code changed} runs.
     */
    static final class Flow {
        private final GlassScreen a;
        private final Runnable changed;
        /** Runs when an option is pressed (the guided setup notes that the step was answered). */
        Runnable picked;
        /** Option 1 or 2, chosen while Unified TV held Home: applied once Return to the original TV interface has finished. */
        private Option pending;
        private boolean overlayAsked;

        Flow(GlassScreen a, Runnable changed) { this.a = a; this.changed = changed; }
        Option pending() { return pending; }

        /** The three options, the true one ticked, each with one plain line, then what it still needs. Returns the ticked row. */
        Button options() {
            HomeTakeover.reconcile(a);   // OD-28: the TV's own Home turned back on outside Unified TV: tidy up first
            Option now = current(a);
            Button ticked = null;
            for (Option o : Option.values()) {
                boolean on = o == now;
                Button b = a.row(o.title, on ? TICK : null, false, () -> choose(o));
                b.setContentDescription(on ? o.title + ", " + CHOSEN : o.title);
                if (on) ticked = b;
                a.text(o.line, 15);
            }
            String need = needs(a, now);
            if (need != null) a.text(need, 18);
            return ticked;
        }

        void choose(Option o) {
            if (picked != null) picked.run();
            HomeTakeover.reconcile(a);
            if (o == Option.HOME) {
                pending = null;
                if (holdsHome(a)) { a.note(HomeTakeover.stockOff(a) && !DefaultHome.isDefault(a) ? HOME_OFF_OTHER : ALREADY_HOME); changed.run(); return; }
                HomeTakeoverActivity.open(a, true);   // asks the adult PIN itself, then explains before anything changes
                return;
            }
            // Never withdraw Home silently: the way back first, with its own PIN, explanation and checks.
            if (holdsHome(a)) { pending = o; HomeTakeoverActivity.open(a, false, returnFirst(o)); return; }
            EditLock.ask(a, () -> apply(o));
        }

        private void apply(Option o) {
            TurnOn.enabled(a, o == Option.QUICK); TurnOn.sync(a);
            changed.run();
            if (o == Option.QUICK && !TurnOn.permitted(a)) askPermission();
        }

        /** OD-23's own ask: the TV's "Display over other apps" screen when it has one, otherwise the one-time computer step. */
        void askPermission() {
            Intent screen = TurnOn.permissionScreen(a);
            AlertDialog.Builder ask = new AlertDialog.Builder(a).setTitle(Option.QUICK.title)
                .setNeutralButton("Don't open Unified TV", (d, w) -> apply(Option.OWN_HOME));
            if (screen != null) ask.setMessage(TurnOn.PERMISSION_WORDS).setNegativeButton("Cancel", null)
                .setPositiveButton("Open the TV's setting", (d, w) -> {
                    KidsGuard.grantPass(a); overlayAsked = true;
                    try { a.startActivity(screen); } catch (RuntimeException e) { overlayAsked = false; a.note(TurnOn.NO_SCREEN_WORDS + " " + TurnOn.adb(a)); }
                });
            else ask.setMessage(TurnOn.PERMISSION_WORDS + "\n\n" + TurnOn.NO_SCREEN_WORDS + "\n" + TurnOn.adb(a)).setPositiveButton("OK", null);
            ask.show();
        }

        /**
         * From the page's onResume: back from Android's permission screen (the watcher follows what was chosen there), or
         * back from Return to the original TV interface (the waiting choice is applied only if Home really was handed back).
         */
        void resumed() {
            if (overlayAsked) { overlayAsked = false; TurnOn.sync(a); }
            Option p = pending;
            if (p == null) return;
            pending = null;
            HomeTakeover.reconcile(a);
            if (holdsHome(a)) return;   // "Not now", or it didn't work: nothing else changes
            TurnOn.enabled(a, p == Option.QUICK); TurnOn.sync(a);
            if (p == Option.QUICK && !TurnOn.permitted(a)) a.getWindow().getDecorView().post(() -> { if (!a.isFinishing()) askPermission(); });
        }
    }
}
