package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowSettings;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/**
 * "When the TV turns on" (owner, 3 November 2026): one choice in Settings > Appearance & Home, and an optional step of the
 * guided setup, in the launcher app only. The tick follows what is true on the TV (Unified TV holding Home: option 3;
 * OD-23 on: 2; otherwise 1); 2 turns OD-23 on and checks "Display over other apps"; 3 opens the OD-28 takeover, which asks
 * the adult PIN; 1 or 2 while Unified TV holds Home opens Return to the original TV interface first and is applied only
 * once Home was handed back. The old Home button rows are only on this page. Both flavours run this class: the preview's
 * journeys check that neither the page nor the step exists there.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class TurnOnChoiceTest {
    private static final String PIN = "2468";
    private Context app;

    private SharedPreferences prefs() { return app.getSharedPreferences(HouseholdSettings.PREFS, 0); }
    private boolean launcher() { return DefaultHome.available(app); }
    private static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800)); }
    private static View root(android.app.Activity a) { return a.findViewById(android.R.id.content); }
    private static Button starting(View v, String prefix) {
        if (v instanceof Button && v.isShown() && ((Button) v).getText().toString().startsWith(prefix)) return (Button) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = starting(((ViewGroup) v).getChildAt(i), prefix); if (b != null) return b; }
        return null;
    }
    private static String words(View v) {
        StringBuilder b = new StringBuilder(); if (v instanceof TextView && v.isShown()) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }
    private static void press(android.app.Activity a, String start) { Button b = starting(root(a), start); assertNotNull("'" + start + "' on: " + words(root(a)), b); b.requestFocus(); b.performClick(); idle(); }
    private static TurnOnChoice.Option tick(android.app.Activity a) {
        TurnOnChoice.Option found = null;
        for (TurnOnChoice.Option o : TurnOnChoice.Option.values()) {
            Button b = starting(root(a), o.title); assertNotNull(o.title, b);
            if (String.valueOf(b.getContentDescription()).equals(o.title + ", " + TurnOnChoice.CHOSEN)) { assertNull("one tick only", found); found = o; }
        }
        return found;
    }
    private ActivityController<TurnOnChoiceActivity> page() { ActivityController<TurnOnChoiceActivity> c = Robolectric.buildActivity(TurnOnChoiceActivity.class).setup(); idle(); return c; }
    private Intent next(android.app.Activity a) { return Shadows.shadowOf(a).getNextStartedActivity(); }
    /** The Home button opens Unified TV (as Android resolves it for this app), or not. */
    private void homeIsOurs(boolean ours) {
        org.robolectric.shadows.ShadowPackageManager pm = Shadows.shadowOf(app.getPackageManager());
        pm.removeResolveInfosForIntent(DefaultHome.homeIntent(), app.getPackageName());
        app.getPackageManager().setComponentEnabledSetting(DefaultHome.entry(app), ours ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        if (!ours) return;
        ResolveInfo ri = new ResolveInfo(); ri.activityInfo = new ActivityInfo(); ri.activityInfo.packageName = app.getPackageName(); ri.activityInfo.name = "io.unifiedtv.preview.HomeEntry";
        pm.addResolveInfoForIntent(DefaultHome.homeIntent(), ri);
    }
    private File record() { return HomeTakeover.recordFile(app); }

    @Before public void clean() {
        app = RuntimeEnvironment.getApplication(); prefs().edit().clear().commit(); ShadowAlertDialog.reset(); ShadowSettings.setCanDrawOverlays(false);
        if (record().exists()) assertTrue(record().delete());
    }
    @After public void reset() { ShadowSettings.setCanDrawOverlays(false); if (record().exists()) record().delete(); }

    // --- pure
    @Test public void theTickFollowsWhatIsTrue() {
        assertEquals(TurnOnChoice.Option.OWN_HOME, TurnOnChoice.current(false, false));
        assertEquals(TurnOnChoice.Option.QUICK, TurnOnChoice.current(false, true));
        assertEquals(TurnOnChoice.Option.HOME, TurnOnChoice.current(true, false));
        assertEquals("holding Home wins over OD-23's switch", TurnOnChoice.Option.HOME, TurnOnChoice.current(true, true));
        assertTrue(TurnOnChoice.holdsHome(true, false)); assertTrue("the TV's own Home off counts", TurnOnChoice.holdsHome(false, true)); assertFalse(TurnOnChoice.holdsHome(false, false));
        assertEquals("Show the TV's own Home", TurnOnChoice.Option.OWN_HOME.title);
        assertEquals("Open Unified TV (quick)", TurnOnChoice.Option.QUICK.title);
        assertEquals("The TV's own Home shows for a moment, then Unified TV comes to the front.", TurnOnChoice.Option.QUICK.line);
        assertEquals("Make Unified TV the TV's Home (instant)", TurnOnChoice.Option.HOME.title);
        assertEquals("Skip — choose later in Settings", TurnOnChoice.SKIP);
        assertEquals("Before uninstalling a review build, choose Return to the original TV interface.", HomeTakeoverActivity.REVIEW_BUILD_WORDS);
    }

    // --- the page (launcher app)
    @Test public void thePageTicksTheTrueStateInEachCase() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        try (ActivityController<TurnOnChoiceActivity> c = page()) { assertEquals("nothing chosen: the TV's own Home", TurnOnChoice.Option.OWN_HOME, tick(c.get())); }
        TurnOn.enabled(app, true);
        try (ActivityController<TurnOnChoiceActivity> c = page()) { assertEquals("OD-23 on", TurnOnChoice.Option.QUICK, tick(c.get())); }
        homeIsOurs(true);
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            assertEquals("the Home button opens Unified TV", TurnOnChoice.Option.HOME, tick(c.get()));
            assertTrue(words(root(c.get())).contains(TurnOnChoice.HOME_OURS));
            assertNotNull("the way back", starting(root(c.get()), HomeTakeoverActivity.RETURN_TITLE));
        }
        homeIsOurs(false); TurnOn.enabled(app, false);
        record().getParentFile().mkdirs(); Files.write(record().toPath(), HomeTakeoverTest.fixture("home-takeover-record.json").getBytes(StandardCharsets.UTF_8));
        assertTrue(HomeTakeover.stockOff(app));
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            assertEquals("OD-28 has the TV's own Home off", TurnOnChoice.Option.HOME, tick(c.get()));
            assertTrue(words(root(c.get())).contains(TurnOnChoice.HOME_OFF_OTHER));
        }
    }

    @Test public void appearanceHasOneRowAtTheTopAndNoDuplicates() {
        Assume.assumeTrue("launcher app only", launcher());
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); idle();
            press(a, "Appearance & Home");
            Button row = starting(root(a), TurnOnChoice.TITLE);
            assertNotNull(row);
            assertEquals("the top of the section", row, GlassScreen.firstFocusable((ViewGroup) row.getParent()));
            assertEquals(TurnOnChoice.TITLE + ", " + TurnOnChoice.Option.OWN_HOME.chip, String.valueOf(row.getContentDescription()));
            for (String old : new String[]{TurnOn.TITLE, HomeTakeoverActivity.TAKE_TITLE, HomeTakeoverActivity.RETURN_TITLE, TurnOnChoiceActivity.ANDROID_CHOICE})
                assertNull("only on When the TV turns on: " + old, starting(root(a), old));
            press(a, TurnOnChoice.TITLE);
            assertEquals(TurnOnChoiceActivity.class.getName(), next(a).getComponent().getClassName());
            // Back from the page: the chip shows what is true now.
            TurnOn.enabled(app, true); c.pause().resume(); idle();
            assertEquals(TurnOnChoice.TITLE + ", " + TurnOnChoice.Option.QUICK.chip, String.valueOf(starting(root(a), TurnOnChoice.TITLE).getContentDescription()));
        }
    }

    @Test public void choosingQuickTurnsOd23OnAndChecksThePermission() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        prefs().edit().putString("pin", Pins.record(PIN)).commit();
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            TurnOnChoiceActivity a = c.get();
            press(a, TurnOnChoice.Option.QUICK.title);
            AlertDialog pin = ShadowAlertDialog.getLatestAlertDialog(); assertEquals("Adult access", String.valueOf(Shadows.shadowOf(pin).getTitle()));
            Pins.type(pin, PIN); idle();
            assertTrue("OD-23's own switch", TurnOn.enabled(app));
            assertEquals(TurnOnChoice.Option.QUICK, tick(a));
            AlertDialog ask = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("the permission is asked for at once", TurnOnChoice.Option.QUICK.title, String.valueOf(Shadows.shadowOf(ask).getTitle()));
            assertTrue(String.valueOf(Shadows.shadowOf(ask).getMessage()).contains(TurnOn.PERMISSION_WORDS));
            assertTrue("and said plainly on the page", words(root(a)).contains(TurnOnChoice.PERMISSION_MISSING.trim()));
            assertEquals(TurnOn.State.NEEDS_PERMISSION, TurnOn.state(app));
        }
        // With the permission already allowed, nothing more is asked.
        prefs().edit().clear().commit(); ShadowAlertDialog.reset(); ShadowSettings.setCanDrawOverlays(true);
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            TurnOnChoiceActivity a = c.get();
            press(a, TurnOnChoice.Option.QUICK.title);
            assertEquals(TurnOn.State.ON, TurnOn.state(app));
            assertNull("no dialog", ShadowAlertDialog.getLatestAlertDialog());
            assertFalse(words(root(a)).contains(TurnOnChoice.PERMISSION_MISSING.trim()));
            press(a, TurnOnChoice.Option.OWN_HOME.title);
            assertFalse("option 1: OD-23 off", TurnOn.enabled(app)); assertEquals(TurnOnChoice.Option.OWN_HOME, tick(a));
        }
    }

    @Test public void choosingInstantOpensTheTakeoverWhichAsksTheAdultPin() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        prefs().edit().putString("pin", Pins.record(PIN)).commit();
        Intent started;
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            TurnOnChoiceActivity a = c.get();
            press(a, TurnOnChoice.Option.HOME.title);
            started = next(a);
            assertEquals(HomeTakeoverActivity.class.getName(), started.getComponent().getClassName());
            assertEquals(HomeTakeoverActivity.TAKE, started.getStringExtra(HomeTakeoverActivity.MODE));
            assertNull("nothing changed by choosing", ShadowAlertDialog.getLatestAlertDialog());
        }
        try (ActivityController<HomeTakeoverActivity> c = Robolectric.buildActivity(HomeTakeoverActivity.class, started).setup()) {
            idle();
            AlertDialog pin = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("the adult PIN is asked", pin); assertEquals("Adult access", String.valueOf(Shadows.shadowOf(pin).getTitle()));
            assertTrue(words(root(c.get())).contains("Adult access is needed for this."));
            Pins.type(pin, PIN); idle();
            String said = words(root(c.get()));
            assertTrue("what changes, before anything", said.contains(HomeTakeoverActivity.INTRO));
            assertTrue("the TV's debugging prompt", said.contains("Allow USB debugging?"));
            assertTrue(said.contains(HomeTakeoverActivity.REVIEW_BUILD_WORDS));
        }
    }

    @Test public void ownHomeOrQuickWhileUnifiedTvHoldsHomeOffersReturnFirst() {
        Assume.assumeTrue("launcher app only", launcher());
        homeIsOurs(true);
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            TurnOnChoiceActivity a = c.get();
            assertEquals(TurnOnChoice.Option.HOME, tick(a));
            for (TurnOnChoice.Option o : new TurnOnChoice.Option[]{TurnOnChoice.Option.OWN_HOME, TurnOnChoice.Option.QUICK}) {
                press(a, o.title);
                Intent started = next(a);
                assertNotNull(o.title + ": the way back first", started);
                assertEquals(HomeTakeoverActivity.class.getName(), started.getComponent().getClassName());
                assertEquals(HomeTakeoverActivity.RETURN, started.getStringExtra(HomeTakeoverActivity.MODE));
                assertEquals(TurnOnChoice.returnFirst(o), started.getStringExtra(HomeTakeoverActivity.REASON));
                assertNull("no PIN asked here: Return asks it itself", ShadowAlertDialog.getLatestAlertDialog());
                assertTrue("never withdrawn silently", DefaultHome.offered(app)); assertTrue(DefaultHome.isDefault(app));
                assertFalse("nothing else changed", TurnOn.enabled(app));
                // "Not now" on the Return page: still Unified TV's Home, and the choice is dropped.
                c.pause().resume(); idle();
                assertNull(a.flow().pending()); assertEquals(TurnOnChoice.Option.HOME, tick(a)); assertFalse(TurnOn.enabled(app));
            }
            // The Return page says why it opened.
            press(a, TurnOnChoice.Option.QUICK.title);
            Intent started = next(a);
            try (ActivityController<HomeTakeoverActivity> r = Robolectric.buildActivity(HomeTakeoverActivity.class, started).setup()) {
                idle(); assertTrue(words(root(r.get())).contains(TurnOnChoice.returnFirst(TurnOnChoice.Option.QUICK)));
            }
            // Home handed back there: coming back applies the waiting choice.
            homeIsOurs(false); ShadowSettings.setCanDrawOverlays(true);
            c.pause().resume(); idle();
            assertTrue("Open Unified TV (quick) once Home was handed back", TurnOn.enabled(app));
            assertEquals(TurnOnChoice.Option.QUICK, tick(a));
        }
    }

    // --- the guided setup
    private ActivityController<FirstRunActivity> guideAt(String step) {
        FirstRun.save(prefs(), FirstRun.at(FirstRun.startOver(), step));
        ActivityController<FirstRunActivity> c = Robolectric.buildActivity(FirstRunActivity.class).setup(); idle(); return c;
    }

    @Test public void theGuidedSetupOffersItBeforeTheSummaryAndCanSkipIt() {
        Assume.assumeTrue("launcher app only", launcher());
        List<String> steps = FirstRun.steps(app);
        assertEquals(FirstRun.LAUNCHER_STEPS, steps);
        assertTrue("after the apps step", steps.indexOf(FirstRun.TURN_ON) > steps.indexOf(FirstRun.APPS));
        assertEquals("just before the summary", steps.indexOf(FirstRun.FINISH) - 1, steps.indexOf(FirstRun.TURN_ON));
        ActivityController<FirstRunActivity> c = guideAt(FirstRun.LIBRARY);
        try {
            FirstRunActivity a = c.get();
            press(a, "Next");
            assertEquals(FirstRun.TURN_ON, a.step());
            String said = words(root(a));
            assertTrue(said, said.contains(TurnOnChoice.TITLE)); assertTrue(said.contains("Step 5 of 6"));
            assertEquals("the TV's own Home, ticked", TurnOnChoice.Option.OWN_HOME, tick(a));
            assertNull("no Next before a choice", starting(root(a), "Next"));
            press(a, TurnOnChoice.SKIP);
            assertEquals(FirstRun.FINISH, a.step());
            assertTrue(words(root(a)).contains(TurnOnChoice.TITLE + ": " + FirstRunActivity.SKIPPED));
            assertFalse("skipping changes nothing", TurnOn.enabled(app));
            a.onBackPressed(); idle();
            assertEquals("Back goes to it again", FirstRun.TURN_ON, a.step());
        } finally { c.pause().stop().destroy(); }
    }

    @Test public void theGuidedSetupChoiceIsMadeThereAndListedInTheSummary() {
        Assume.assumeTrue("launcher app only", launcher());
        ShadowSettings.setCanDrawOverlays(true);
        ActivityController<FirstRunActivity> c = guideAt(FirstRun.TURN_ON);
        try {
            FirstRunActivity a = c.get();
            press(a, TurnOnChoice.Option.QUICK.title);
            assertTrue(TurnOn.enabled(app)); assertEquals(TurnOnChoice.Option.QUICK, tick(a));
            assertEquals("still on the step, with Next", FirstRun.TURN_ON, a.step());
            press(a, "Next");
            assertEquals(FirstRun.FINISH, a.step());
            assertTrue(words(root(a)), words(root(a)).contains(TurnOnChoice.TITLE + ": " + TurnOnChoice.Option.QUICK.title));
        } finally { c.pause().stop().destroy(); }
        // Option 3 from the guide opens the takeover; the summary then says what is true.
        ActivityController<FirstRunActivity> again = guideAt(FirstRun.TURN_ON);
        try {
            FirstRunActivity a = again.get();
            press(a, TurnOnChoice.Option.HOME.title);
            Intent started = next(a);
            assertEquals(HomeTakeoverActivity.class.getName(), started.getComponent().getClassName());
            assertEquals(HomeTakeoverActivity.TAKE, started.getStringExtra(HomeTakeoverActivity.MODE));
        } finally { again.pause().stop().destroy(); }
    }

    // --- the preview has neither
    @Test public void thePreviewHasNoPageAndNoStep() {
        Assume.assumeFalse("the preview app", launcher());
        assertFalse(TurnOnChoice.offered(app));
        assertNull("no page", app.getPackageManager().resolveActivity(new Intent(app, TurnOnChoiceActivity.class), 0));
        assertEquals(FirstRun.STEPS, FirstRun.steps(app)); assertFalse(FirstRun.steps(app).contains(FirstRun.TURN_ON));
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); idle(); press(a, "Appearance & Home");
            assertNull(starting(root(a), TurnOnChoice.TITLE));
        }
        // A saved step the preview doesn't have resumes at Welcome; the summary never mentions it.
        ActivityController<FirstRunActivity> c = guideAt(FirstRun.TURN_ON);
        try { assertEquals(FirstRun.WELCOME, c.get().step()); } finally { c.pause().stop().destroy(); }
        ActivityController<FirstRunActivity> f = guideAt(FirstRun.FINISH);
        try { assertFalse(words(root(f.get())).contains(TurnOnChoice.TITLE)); assertTrue(words(root(f.get())).contains("Step 5 of 5")); } finally { f.pause().stop().destroy(); }
    }
}
