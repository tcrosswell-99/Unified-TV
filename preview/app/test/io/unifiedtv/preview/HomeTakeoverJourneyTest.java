package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import io.unifiedtv.adb.AdbCommands;
import io.unifiedtv.adb.AdbException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/**
 * OD-28 with the remote's OK, launcher app: Take over the Home button against a simulated TCL (its tvlauncher at priority
 * 2), the explanation before anything is turned off, the record, the link closed and debugging-off check, "Press the Home
 * button on your remote now" answered by Home itself, the 20-second timeout with its undo, refusal on unknown firmware,
 * Return to the original TV interface with and without network debugging, the uninstall notice in Updates & About, and
 * Home's safe screen after two early crashes. The preview app has none of it. Both flavours run this class.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class HomeTakeoverJourneyTest {
    private static final String STOCK = "com.google.android.tvlauncher";
    private Context app;
    private FakeTv tv;
    private FakeHome homes;
    private AdbException refuse;

    private boolean launcher() { return DefaultHome.available(app); }
    private static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private static void idle(long ms) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
    private static View root(android.app.Activity a) { return a.findViewById(android.R.id.content); }
    private static Button find(View v, String name) {
        if (v instanceof Button && v.isShown() && ((Button) v).getText().toString().equals(name)) return (Button) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = find(((ViewGroup) v).getChildAt(i), name); if (b != null) return b; }
        return null;
    }
    private static String words(View v) {
        StringBuilder b = new StringBuilder(); if (v instanceof TextView && v.isShown()) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }
    private static void press(android.app.Activity a, String name) { Button b = find(root(a), name); assertNotNull("'" + name + "' on: " + words(root(a)), b); b.requestFocus(); b.performClick(); idle(); }
    private File record() { return HomeTakeover.recordFile(app); }
    private void writeRecord(String fixture) throws Exception { record().getParentFile().mkdirs(); Files.write(record().toPath(), HomeTakeoverTest.fixture(fixture).getBytes(StandardCharsets.UTF_8)); }
    private void entry(boolean on) { app.getPackageManager().setComponentEnabledSetting(DefaultHome.entry(app), on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); }

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication(); ShadowAlertDialog.reset();
        app.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().clear().commit();
        app.getSharedPreferences("unified-tv-preview", 0).edit().clear().putString("dock", RemoteJourneyTest.SEEDED).commit();
        app.getSharedPreferences(EventLog.PREFS, 0).edit().clear().commit();
        HomeTakeover.cancel(app); HomeSafety.reset(app);
        tv = new FakeTv(); homes = new FakeHome(app.getPackageName()); tv.homes = homes;
        homes.ours = () -> launcher() && DefaultHome.offered(app);
        refuse = null;
        CleanupLists.forTests = FakeTv.lists();
        CleanupActivity.fingerprintForTests = FakeTv.OWNER;
        CleanupActivity.backgroundForTests = Runnable::run;
        CleanupActivity.openerForTests = (c, prompt, opening) -> { if (refuse != null) throw refuse; prompt.showing(); tv.closed = false; return new CleanupActivity.Opened(tv, tv); };
        HomeTakeoverActivity.candidateWaitMs = 0;
        if (record().exists()) assertTrue(record().delete());
    }
    @After public void tearDown() {
        CleanupLists.forTests = null; CleanupActivity.fingerprintForTests = null; CleanupActivity.backgroundForTests = null; CleanupActivity.openerForTests = null;
        HomeTakeoverActivity.candidateWaitMs = 600; HomeTakeover.cancel(app); HomeSafety.reset(app);
    }

    private ActivityController<HomeTakeoverActivity> open(boolean take) {
        Intent i = new Intent(app, HomeTakeoverActivity.class).putExtra(HomeTakeoverActivity.MODE, take ? HomeTakeoverActivity.TAKE : HomeTakeoverActivity.RETURN);
        ActivityController<HomeTakeoverActivity> c = Robolectric.buildActivity(HomeTakeoverActivity.class, i).setup(); idle(); return c;
    }

    // --- take over
    @Test public void takingOverExplainsFirstRecordsThenAsksForTheHomeButton() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            assertTrue(words(root(a)).contains(HomeTakeoverActivity.INTRO));
            assertTrue("the way back is said before anything", words(root(a)).contains(HomeTakeoverActivity.WAY_BACK));
            assertTrue("nothing yet", tv.commands.isEmpty());
            press(a, "Start");
            assertTrue("step a: Unified TV's Home entry is on", DefaultHome.offered(app));
            assertEquals("step c only: Android's own route", Collections.singletonList(AdbCommands.setHome(HomeTakeover.entry(app.getPackageName()))), tv.changes());
            String said = words(root(a));
            assertTrue(said, said.contains("Android TV Home (" + STOCK + ")"));
            assertTrue(said.contains("adb shell pm enable " + STOCK));
            assertTrue(said.contains("Settings > Apps > See all apps > Show system apps > Android TV Home > Enable"));
            assertFalse("nothing turned off before the confirm", tv.disabled.contains(STOCK));
            press(a, "Turn off Android TV Home and use Unified TV");
            assertTrue(tv.disabled.contains(STOCK));
            assertEquals(STOCK, HomeTakeover.turnedOff(app));
            assertTrue("the link is closed when done", tv.closed);
            assertTrue(words(root(a)).contains("Unified TV is the Home screen"));
            press(a, "Next");
            assertTrue("the debugging-off check", words(root(a)).contains("Turn off USB debugging"));
            assertNotNull(find(root(a), "Check that it's off"));
            press(a, "Next: try the Home button");
            assertTrue(words(root(a)).contains(HomeTakeoverActivity.PRESS_HOME));
            // The owner presses Home: Unified TV's Home comes to the front with the Home intent, and says so.
            try (ActivityController<UnifiedPreviewActivity> home = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
                idle();
                home.newIntent(DefaultHome.homeIntent()); idle();
                assertEquals(HomeTakeover.WORKED, message(home.get()));
                assertFalse("Home is never finished", home.get().isFinishing());
            }
            idle(600);
            assertTrue(words(root(a)).contains(HomeTakeover.WORKED));
            boolean logged = false; for (EventLog.Event e : EventLog.recent(app)) logged |= e.words.equals("Home button: turned off " + STOCK);
            assertTrue("in the event log, package name only", logged);
        }
    }

    @Test public void noHomeButtonWithinTwentySecondsOffersTheWayBack() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            press(a, "Start"); press(a, "Turn off Android TV Home and use Unified TV"); press(a, "Next"); press(a, "Next: try the Home button");
            idle(HomeTakeover.CONFIRM_MS + 1000);
            assertTrue(words(root(a)).contains("didn't come to the front within 20 seconds"));
            assertNotNull(find(root(a), "Undo: " + HomeTakeoverActivity.RETURN_TITLE));
            assertEquals("no longer waiting", 0, HomeTakeover.pending(app));
            press(a, "Undo: " + HomeTakeoverActivity.RETURN_TITLE);
            press(a, HomeTakeoverActivity.RETURN_TITLE);
            assertFalse("the TV's own Home is on again", tv.disabled.contains(STOCK));
            assertFalse("Unified TV's Home entry withdrawn", DefaultHome.offered(app));
            assertEquals(STOCK, homes.resolve(tv).substring(0, STOCK.length()));
            assertNull(HomeTakeover.turnedOff(app));
        }
    }

    @Test public void unknownFirmwareIsRefusedAndTheEntryHandedBack() {
        Assume.assumeTrue("launcher app only", launcher());
        CleanupActivity.fingerprintForTests = FakeTv.SONY; tv.fingerprint = FakeTv.SONY;
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            press(a, "Start");
            assertTrue(words(root(a)).contains(HomeTakeover.UNKNOWN_TV));
            assertTrue(tv.disabled.isEmpty() || !tv.disabled.contains(STOCK));
            for (String sent : tv.changes()) assertFalse("never turned off: " + sent, sent.startsWith("pm disable-user"));
            assertFalse("not chosen: the Home entry is off again", DefaultHome.offered(app));
            assertFalse(record().exists());
            assertTrue(tv.closed);
        }
    }

    // --- the way back
    @Test public void returnWithoutNetworkDebuggingGivesTheTvOnlyRoute() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        writeRecord("home-takeover-record.json"); entry(true);
        refuse = new AdbException(AdbException.Kind.CLOSED, "Nothing answers on port 5555: network debugging is off.");
        try (ActivityController<HomeTakeoverActivity> c = open(false)) {
            HomeTakeoverActivity a = c.get();
            assertTrue(words(root(a)).contains("Unified TV turned Android TV Home (" + STOCK + ") off"));
            press(a, HomeTakeoverActivity.RETURN_TITLE);
            String said = words(root(a));
            assertTrue(said, said.contains(HomeTakeoverActivity.NO_LINK));
            assertTrue(said.contains("Settings > Apps > See all apps > Show system apps > Android TV Home > Enable"));
            assertTrue(said.contains("adb shell pm enable " + STOCK));
            assertEquals("nothing changed: the marker stays", STOCK, HomeTakeover.turnedOff(app));
            assertTrue("the Home entry stays while the TV's own Home is off", DefaultHome.offered(app));
            assertFalse("and withdraw refuses", DefaultHome.withdraw(app));
            assertTrue(DefaultHome.offered(app));
        }
    }

    @Test public void returnOverTheLinkRestoresTheStockHomeAndWithdrawsTheEntry() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        writeRecord("home-takeover-record.json"); entry(true); tv.disabled.add(STOCK);
        assertEquals(HomeTakeover.entry(app.getPackageName()), homes.resolve(tv));
        try (ActivityController<HomeTakeoverActivity> c = open(false)) {
            HomeTakeoverActivity a = c.get();
            press(a, HomeTakeoverActivity.RETURN_TITLE);
            assertEquals(Arrays.asList(AdbCommands.enable(STOCK), AdbCommands.setHome(FakeTv.HOME)), tv.changes());
            assertFalse(tv.disabled.contains(STOCK));
            assertFalse(DefaultHome.offered(app));
            assertNull(HomeTakeover.turnedOff(app));
            assertTrue(words(root(a)).contains("The Home button opens Android TV Home again."));
            assertTrue(tv.closed);
            press(a, "Next");
            assertTrue(words(root(a)).contains("Turn off USB debugging"));
        }
    }

    // --- Settings
    @Test public void settingsShowTheUninstallGuardAndTheWayBackWhileTheStockHomeIsOff() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        writeRecord("home-takeover-record.json"); entry(true);
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); idle();
            category(a, "Updates & About");
            assertTrue(words(root(a)).contains(HomeTakeover.UNINSTALL_WORDS));
            press(a, HomeTakeoverActivity.RETURN_TITLE);
            Intent started = Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(HomeTakeoverActivity.class.getName(), started.getComponent().getClassName());
            assertEquals(HomeTakeoverActivity.RETURN, started.getStringExtra(HomeTakeoverActivity.MODE));
        }
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); idle();
            category(a, "Appearance & Home");
            assertNotNull("the way back is prominent here too", find(root(a), HomeTakeoverActivity.RETURN_TITLE));
            assertNull("not offered again while it holds Home", find(root(a), HomeTakeoverActivity.TAKE_TITLE));
            assertNull(find(root(a), "Make Unified TV the Home screen"));
        }
        assertTrue("record unchanged by looking", HomeTakeover.stockOff(app));
        assertTrue(record().delete());
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); idle();
            category(a, "Updates & About");
            assertFalse("no notice while the TV's own Home is on", words(root(a)).contains(HomeTakeover.UNINSTALL_WORDS));
        }
    }

    @Test public void appearanceOffersTheTakeoverWhileTheTvKeepsItsOwnHome() {
        Assume.assumeTrue("launcher app only", launcher());
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); idle();
            category(a, "Appearance & Home");
            assertTrue(words(root(a)).contains(SettingsActivity.TAKE_NOTE));
            press(a, HomeTakeoverActivity.TAKE_TITLE);
            Intent started = Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(HomeTakeoverActivity.class.getName(), started.getComponent().getClassName());
            assertEquals(HomeTakeoverActivity.TAKE, started.getStringExtra(HomeTakeoverActivity.MODE));
        }
    }

    // --- crash safety
    @Test public void twoEarlyCrashesWhileHoldingHomeShowTheSafeScreen() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        writeRecord("home-takeover-record.json"); entry(true);
        try (ActivityController<UnifiedPreviewActivity> first = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            idle();
            long now = SystemClock.elapsedRealtime();
            HomeSafety.crashed(app, now + HomeSafety.EARLY_MS + 1);
            assertEquals("a crash after the first minute isn't counted", 0, HomeSafety.crashes(app));
            HomeSafety.crashed(app, now + 10); HomeSafety.crashed(app, now + 20);
            assertEquals(2, HomeSafety.crashes(app));
        }
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            UnifiedPreviewActivity home = c.get(); idle();
            assertTrue(words(root(home)).contains(HomeSafety.TITLE));
            assertFalse("Home itself isn't built", hasHome(home));
            home.onBackPressed(); assertFalse("Back stays on the safe screen; Home is never finished", home.isFinishing());
            press(home, HomeSafety.RETURN);
            Intent started = Shadows.shadowOf(home).getNextStartedActivity();
            assertEquals(HomeTakeoverActivity.class.getName(), started.getComponent().getClassName());
            assertEquals(HomeTakeoverActivity.RETURN, started.getStringExtra(HomeTakeoverActivity.MODE));
            press(home, HomeSafety.AGAIN);
            assertEquals(0, HomeSafety.crashes(app));
        }
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            idle(); assertTrue("Try again: Home as usual", hasHome(c.get()));
        }
    }

    @Test public void earlyCrashesWhileNotHoldingHomeStillOpenHome() {
        Assume.assumeTrue("launcher app only", launcher());
        app.getSharedPreferences(HomeSafety.PREFS, 0).edit().putInt(HomeSafety.CRASHES, 3).commit();
        assertFalse(HomeSafety.holdsHome(app));
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            idle(); assertTrue(hasHome(c.get()));
        }
    }

    // --- the preview has none of it
    @Test public void thePreviewHasNoneOfIt() throws Exception {
        Assume.assumeFalse("the preview app", launcher());
        writeRecord("home-takeover-record.json");
        app.getSharedPreferences(HomeSafety.PREFS, 0).edit().putInt(HomeSafety.CRASHES, 5).commit();
        assertFalse("no safe screen in the preview", HomeSafety.needed(app));
        assertNull("the page isn't even in the preview", app.getPackageManager().resolveActivity(new Intent(app, HomeTakeoverActivity.class), 0));
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); idle();
            category(a, "Appearance & Home");
            assertNull(find(root(a), HomeTakeoverActivity.TAKE_TITLE));
            assertNull(find(root(a), HomeTakeoverActivity.RETURN_TITLE));
        }
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            idle(); assertTrue(hasHome(c.get()));
        }
    }

    private static boolean hasHome(android.app.Activity a) { try { return RemoteJourneyTest.homeIn(a) != null; } catch (Throwable e) { return false; } }
    /** A Settings category row, whatever chip follows its name. */
    private static void category(android.app.Activity a, String name) { Button b = starting(root(a), name); assertNotNull(name, b); b.requestFocus(); b.performClick(); idle(); }
    private static Button starting(View v, String prefix) {
        if (v instanceof Button && v.isShown() && ((Button) v).getText().toString().startsWith(prefix)) return (Button) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = starting(((ViewGroup) v).getChildAt(i), prefix); if (b != null) return b; }
        return null;
    }

    private static String message(UnifiedPreviewActivity a) throws Exception {
        View home = RemoteJourneyTest.homeIn(a);
        java.lang.reflect.Field m = home.getClass().getDeclaredField("message"); m.setAccessible(true); return (String) m.get(home);
    }
}
