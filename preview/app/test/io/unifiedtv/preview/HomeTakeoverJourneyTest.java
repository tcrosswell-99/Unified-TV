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
import io.unifiedtv.adb.AdbLink;
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
    /** Links opened; a command that fails with {@link #failWith}; the stock Home's priority as Android lists it to this app. */
    private int opens;
    private String failOn;
    private AdbException failWith;
    private int stockPriority;

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
        app.getSharedPreferences(LinkSteps.PREFS, 0).edit().clear().commit();
        opens = 0; failOn = null; failWith = null; stockPriority = 0;
        HomeTakeover.cancel(app); HomeSafety.reset(app);
        tv = new FakeTv(); homes = new FakeHome(app.getPackageName()); tv.homes = homes;
        homes.ours = () -> launcher() && DefaultHome.offered(app);
        refuse = null;
        CleanupLists.forTests = FakeTv.lists();
        CleanupActivity.fingerprintForTests = FakeTv.OWNER;
        CleanupActivity.backgroundForTests = Runnable::run;
        // The TV as Android shows it to this app follows the simulated TV: tvlauncher on (a Home) or off (none).
        stockHome(true);
        Cleanup.Shell mirrored = command -> { if (command.equals(failOn)) throw failWith; String out = tv.run(command);
            if (command.equals(AdbCommands.enable(STOCK))) stockHome(!tv.disabled.contains(STOCK));
            if (command.equals(AdbCommands.disable(STOCK))) stockHome(!tv.disabled.contains(STOCK));
            return out; };
        CleanupActivity.openerForTests = (c, prompt, opening) -> { opens++; if (refuse != null) throw refuse; prompt.showing(); tv.closed = false; return new CleanupActivity.Opened(mirrored, tv); };
        HomeTakeoverActivity.candidateWaitMs = 0;
        if (record().exists()) assertTrue(record().delete());
    }
    @After public void tearDown() {
        CleanupLists.forTests = null; CleanupActivity.fingerprintForTests = null; CleanupActivity.backgroundForTests = null; CleanupActivity.openerForTests = null;
        HomeTakeoverActivity.candidateWaitMs = 600; HomeTakeover.cancel(app); HomeSafety.reset(app);
    }

    /** tvlauncher installed, and on (answering Home) or off, as Android reports it to this app. */
    private void stockHome(boolean on) {
        org.robolectric.shadows.ShadowPackageManager pm = Shadows.shadowOf(app.getPackageManager());
        android.content.pm.PackageInfo info = new android.content.pm.PackageInfo(); info.packageName = STOCK;
        info.applicationInfo = new android.content.pm.ApplicationInfo(); info.applicationInfo.packageName = STOCK; info.applicationInfo.enabled = true;
        try { app.getPackageManager().getPackageInfo(STOCK, 0); } catch (PackageManager.NameNotFoundException e) { pm.installPackage(info); }
        app.getPackageManager().setApplicationEnabledSetting(STOCK, on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
        pm.removeResolveInfosForIntent(DefaultHome.homeIntent(), STOCK);
        if (on) { android.content.pm.ResolveInfo r = new android.content.pm.ResolveInfo(); r.activityInfo = new android.content.pm.ActivityInfo();
            r.activityInfo.packageName = STOCK; r.activityInfo.name = STOCK + ".MainActivity"; r.activityInfo.applicationInfo = info.applicationInfo; r.priority = stockPriority;
            pm.addResolveInfoForIntent(DefaultHome.homeIntent(), r); }
    }

    private ActivityController<HomeTakeoverActivity> open(boolean take) {
        Intent i = new Intent(app, HomeTakeoverActivity.class).putExtra(HomeTakeoverActivity.MODE, take ? HomeTakeoverActivity.TAKE : HomeTakeoverActivity.RETURN);
        ActivityController<HomeTakeoverActivity> c = Robolectric.buildActivity(HomeTakeoverActivity.class, i).setup(); idle(); return c;
    }

    // --- take over
    @Test public void takingOverExplainsFirstRecordsThenAsksForTheHomeButton() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        TurnOn.enabled(app, true);   // "Open Unified TV (quick)" was chosen before
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            assertTrue(words(root(a)).contains(HomeTakeoverActivity.INTRO));
            assertTrue("the review build reminder, before anything", words(root(a)).contains(HomeTakeoverActivity.REVIEW_BUILD_WORDS));
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
            assertFalse("When the TV turns on: option 3 done, so OD-23's switch is off (not needed)", TurnOn.enabled(app));
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
            List<LinkSteps.Entry> steps = LinkSteps.history(app);
            assertFalse(steps.isEmpty());
            for (LinkSteps.Entry e : steps) assertFalse("never what the TV printed: " + e.words, e.words.contains(tv.serial) || e.words.contains(tv.androidId) || e.words.contains("BeyondTV6/"));
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

    // --- the owner's Sony BRAVIA 4K UR2 (Android TV 10, QTG3.200305.006.S97, 3 November 2026): no tested list
    private void sony() { CleanupActivity.fingerprintForTests = FakeTv.SONY_UR2; tv.fingerprint = FakeTv.SONY_UR2; }

    @Test public void anUntestedTvIsToldFirstAndOnlyAndroidsOwnChoiceIsTried() {
        Assume.assumeTrue("launcher app only", launcher());
        sony();
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            String said = words(root(a));
            assertTrue(said, said.contains(HomeTakeover.UNTESTED));
            assertFalse("not the tested TV's words", said.contains(HomeTakeoverActivity.INTRO));
            assertEquals("no link before the explanation", 0, opens);
            assertTrue(tv.commands.isEmpty());
            assertFalse("nothing switched on yet", DefaultHome.offered(app));
            press(a, "Start");
            assertEquals(1, opens);
            assertEquals("Android's own route only", Collections.singletonList(AdbCommands.setHome(HomeTakeover.entry(app.getPackageName()))), tv.changes());
            for (String sent : tv.commands) assertFalse("never turned off: " + sent, sent.startsWith("pm disable-user"));
            said = words(root(a));
            assertTrue(said, said.contains(HomeTakeover.KEEPS_OWN));
            assertEquals("This TV keeps its own Home. Use 'Open Unified TV (quick)' instead.", HomeTakeover.KEEPS_OWN);
            assertNull("nothing to confirm", find(root(a), "Turn off Android TV Home and use Unified TV"));
            assertFalse("the Home entry is handed back: nothing changed", DefaultHome.offered(app));
            assertFalse(record().exists());
            assertTrue("the link is closed", tv.closed);
            press(a, HomeTakeover.USE_QUICK);
            Intent next = Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(TurnOnChoiceActivity.class.getName(), next.getComponent().getClassName());
            assertEquals(TurnOnChoice.Option.QUICK.name(), next.getStringExtra(TurnOnChoiceActivity.CHOOSE));
            assertTrue(a.isFinishing());
            try (ActivityController<TurnOnChoiceActivity> page = Robolectric.buildActivity(TurnOnChoiceActivity.class, next).setup()) {
                idle();
                assertTrue("option 2 is chosen on the When the TV turns on page", TurnOn.enabled(app));
                assertNull("once only", page.get().getIntent().getStringExtra(TurnOnChoiceActivity.CHOOSE));
            }
        }
    }

    @Test public void anUntestedTvWhoseOwnHomeRanksFirstIsToldWithoutOpeningTheLink() {
        Assume.assumeTrue("launcher app only", launcher());
        sony(); stockPriority = 2; stockHome(true);   // Android lists Android TV Home at priority 2, as the TCL's
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            String said = words(root(a));
            assertTrue(said, said.contains(HomeTakeover.ranksFirst("Android TV Home")));
            assertTrue(said.contains(HomeTakeover.KEEPS_OWN));
            assertEquals("decided before any link", 0, opens);
            assertTrue(tv.commands.isEmpty());
            assertFalse(DefaultHome.offered(app));
            assertNotNull(find(root(a), HomeTakeover.USE_QUICK));
            press(a, "Try anyway");
            assertEquals(1, opens);
            assertTrue(words(root(a)).contains(HomeTakeover.KEEPS_OWN));
            for (String sent : tv.changes()) assertTrue("Android's own route only: " + sent, sent.startsWith("cmd package set-home-activity"));
        }
    }

    @Test public void onAnUntestedTvAndroidsOwnChoiceCanWinAndNothingIsTurnedOff() {
        Assume.assumeTrue("launcher app only", launcher());
        sony(); homes.priority.put(FakeTv.HOME, 0);   // a TV whose own Home doesn't rank itself first
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            press(a, "Start");
            assertTrue(words(root(a)).contains("Android now opens Unified TV when you press Home. Nothing was turned off."));
            assertEquals(Collections.singletonList(AdbCommands.setHome(HomeTakeover.entry(app.getPackageName()))), tv.changes());
            assertFalse(record().exists());
            press(a, "Next"); press(a, "Next: try the Home button");
            idle(HomeTakeover.CONFIRM_MS + 1000);   // the TV's own Home came up instead
            String said = words(root(a));
            assertTrue(said, said.contains(HomeTakeover.KEEPS_OWN));
            assertNotNull(find(root(a), HomeTakeover.USE_QUICK));
            assertNotNull(find(root(a), "Undo: " + HomeTakeoverActivity.RETURN_TITLE));
        }
    }

    @Test public void theGentleRouteRefusesEveryOtherChangeUnsent() throws Exception {
        String self = app.getPackageName();
        tv.fingerprint = FakeTv.SONY_UR2; tv.homes = homes;
        Cleanup.Shell g = HomeTakeover.gentleOnly(tv, self);
        for (String change : new String[]{AdbCommands.disable(STOCK), AdbCommands.enable(STOCK), AdbCommands.setHome(FakeTv.HOME), AdbCommands.allowListener(self)}) {
            try { g.run(change); fail("sent: " + change); } catch (HomeTakeover.Changed e) { assertEquals(HomeTakeover.UNKNOWN_TV, e.getMessage()); }
        }
        assertTrue("nothing reached the TV", tv.commands.isEmpty());
        assertEquals(FakeTv.SONY_UR2 + "\n", g.run(AdbCommands.FINGERPRINT));
        HomeTakeover.Check c = HomeTakeover.check(g, FakeTv.lists(), FakeTv.SONY_UR2, self);
        assertEquals("no tested list: refused", HomeTakeover.UNKNOWN_TV, HomeTakeover.refusal(c, c.before, self));
        try { HomeTakeover.takeOver(g, record(), c, self, "2026-11-03T12:00:00+0000"); fail("never on an untested TV"); } catch (HomeTakeover.Changed expected) { /* refused */ }
        for (String sent : tv.commands) assertFalse("never turned off: " + sent, AdbCommands.changes(sent) && sent.startsWith("pm "));
        assertFalse(record().exists());
    }

    @Test public void theRouteIsDecidedFromThisTvAlone() {
        Map<String, Integer> tcl = Collections.singletonMap(STOCK, 2), plain = Collections.singletonMap(STOCK, 0);
        assertEquals(HomeTakeover.Route.TESTED, HomeTakeover.plan(true, tcl, 0).route);
        assertEquals(HomeTakeover.Route.KEEPS_OWN, HomeTakeover.plan(false, tcl, 0).route);
        assertEquals(STOCK, HomeTakeover.plan(false, tcl, 0).ahead);
        assertEquals(HomeTakeover.Route.GENTLE, HomeTakeover.plan(false, plain, 0).route);
        assertEquals("nothing listed: the gentle route says what happens", HomeTakeover.Route.GENTLE, HomeTakeover.plan(false, Collections.emptyMap(), 0).route);
        assertTrue(HomeTakeover.plan(false, plain, 0).gentleOnly());
        assertFalse(HomeTakeover.plan(true, plain, 0).gentleOnly());
        assertEquals("the owner's TCL", HomeTakeover.Route.TESTED, HomeTakeover.plan(app, FakeTv.lists(), FakeTv.OWNER).route);
        assertEquals("the owner's Sony", HomeTakeover.Route.GENTLE, HomeTakeover.plan(app, FakeTv.lists(), FakeTv.SONY_UR2).route);
    }

    // --- never waiting without end: each failed step said plainly, the link closed, recorded
    @Test public void eachLinkFailureSaysWhichStepClosesTheLinkAndIsRecorded() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        sony();
        Object[][] cases = {
            {new AdbException(AdbException.Kind.TIMED_OUT, AdbLink.Step.HELLO, "x"), LinkSteps.NO_ANSWER, "Stopped (no answer in time) at: " + AdbLink.Step.HELLO.words},
            {new AdbException(AdbException.Kind.TIMED_OUT, AdbLink.Step.ALLOW, "x"), LinkSteps.ALLOW_TIMEOUT, "Stopped (no answer in time) at: " + AdbLink.Step.ALLOW.words},
            {new AdbException(AdbException.Kind.CLOSED, AdbLink.Step.CONNECT, "x"), LinkSteps.CONNECT_REFUSED, "Stopped (debugging service closed) at: " + AdbLink.Step.CONNECT.words},
            {new AdbException(AdbException.Kind.LOST, AdbLink.Step.SIGN_IN, "x"), LinkSteps.CLOSED_EARLY, "Stopped at: " + AdbLink.Step.SIGN_IN.words},
        };
        for (Object[] k : cases) {
            refuse = (AdbException) k[0];
            try (ActivityController<HomeTakeoverActivity> c = open(true)) {
                HomeTakeoverActivity a = c.get();
                press(a, "Start");
                String said = words(root(a));
                assertTrue(said, said.contains((String) k[1]));
                assertTrue(said, said.contains(LinkSteps.LAST + k[2]));
                assertNotNull(find(root(a), "Try again")); assertNotNull(find(root(a), "Cancel"));
                assertFalse("nothing chosen: the Home entry is handed back", DefaultHome.offered(app));
            }
            boolean logged = false; for (EventLog.Event e : EventLog.recent(app)) logged |= e.words.equals(("Debugging link: " + k[2]).substring(0, Math.min(80, ("Debugging link: " + k[2]).length())));
            assertTrue("in the event log: " + k[2], logged);
        }
        // A command the TV takes too long to answer: said plainly, and the link closed.
        refuse = null; failOn = AdbCommands.HOME; failWith = new AdbException(AdbException.Kind.TIMED_OUT, AdbLink.Step.COMMAND, "x");
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            press(a, "Start");
            assertTrue(words(root(a)).contains(LinkSteps.COMMAND_TIMEOUT));
            assertTrue("the link is closed", tv.closed);
            assertTrue(tv.changes().isEmpty());
        }
        // Owner diagnostics: the last step, and the last attempt's steps.
        try (ActivityController<OwnerDiagnosticsActivity> d = Robolectric.buildActivity(OwnerDiagnosticsActivity.class).setup()) {
            idle();
            String said = words(root(d.get()));
            assertTrue(said, said.contains(LinkSteps.LAST + "Stopped (no answer in time) at: " + AdbLink.Step.COMMAND.words));
            assertTrue(said.contains("Command: " + AdbCommands.FINGERPRINT));
            assertTrue(said.contains("Started: " + HomeTakeoverActivity.TAKE_TITLE));
        }
    }

    /** A link that waits (the TV's Allow) on a real worker thread, released only when the page closes what it is opening. */
    private CleanupActivity.Opener waitsForAllow(boolean[] closed) {
        return (c, prompt, opening) -> {
            opens++;
            java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(1);
            opening.accept(() -> { closed[0] = true; released.countDown(); });
            prompt.showing();
            released.await(10, java.util.concurrent.TimeUnit.SECONDS);
            throw new AdbException(AdbException.Kind.LOST, AdbLink.Step.ALLOW, "Stopped.");
        };
    }
    private static void until(android.app.Activity a, String shown) throws InterruptedException {
        for (int i = 0; i < 300 && !words(root(a)).contains(shown); i++) { idle(); Thread.sleep(10); }
        assertTrue("'" + shown + "' on: " + words(root(a)), words(root(a)).contains(shown));
    }

    @Test public void backCancelsWhileTheTvWaitsForAllow() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        sony(); CleanupActivity.backgroundForTests = null;   // the page's own worker thread: the UI never waits
        boolean[] closed = {false};
        CleanupActivity.openerForTests = waitsForAllow(closed);
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            press(a, "Start");
            until(a, LinkSteps.WAITING_ALLOW);
            assertTrue(words(root(a)).contains(LinkSteps.BACK_CANCELS));
            org.robolectric.shadows.ShadowToast.reset();
            a.onBackPressed(); idle();
            assertTrue("the connection being opened is closed", closed[0]);
            assertNull("Back isn't refused", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            Thread.sleep(200); idle();   // the worker ends; its late answer doesn't replace the page
            assertTrue("back on the explanation", words(root(a)).contains(HomeTakeover.UNTESTED));
            assertTrue(tv.commands.isEmpty());
            assertFalse("the unused Home entry is handed back", DefaultHome.offered(app));
        }
    }

    @Test public void aWholeAttemptStopsAfterTwoMinutes() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        sony(); CleanupActivity.backgroundForTests = null;
        boolean[] closed = {false};
        CleanupActivity.openerForTests = waitsForAllow(closed);
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            HomeTakeoverActivity a = c.get();
            press(a, "Start");
            until(a, LinkSteps.WAITING_ALLOW);
            idle(LinkSteps.FLOW_MS + 1000);
            assertTrue("the link being opened is closed", closed[0]);
            String said = words(root(a));
            assertTrue(said, said.contains(LinkSteps.FLOW_TIMEOUT));
            assertTrue(said.contains(LinkSteps.LAST + "Stopped after two minutes"));
            assertNotNull(find(root(a), "Try again")); assertNotNull(find(root(a), "Cancel"));
            assertFalse(DefaultHome.offered(app));
            Thread.sleep(200); idle();
            assertTrue("a late answer doesn't replace the page", words(root(a)).contains(LinkSteps.FLOW_TIMEOUT));
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
        writeRecord("home-takeover-record.json"); entry(true); tv.disabled.add(STOCK); stockHome(false);
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
        writeRecord("home-takeover-record.json"); entry(true); stockHome(false);
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
            assertNull("no duplicate rows in Appearance & Home", find(root(a), HomeTakeoverActivity.RETURN_TITLE));
            assertNotNull("one row: When the TV turns on", starting(root(a), TurnOnChoice.TITLE));
        }
        try (ActivityController<TurnOnChoiceActivity> c = Robolectric.buildActivity(TurnOnChoiceActivity.class).setup()) {
            TurnOnChoiceActivity a = c.get(); idle();
            assertNotNull("the way back is prominent on the page", find(root(a), HomeTakeoverActivity.RETURN_TITLE));
            assertTrue("the TV's own Home is off: option 3", String.valueOf(starting(root(a), TurnOnChoice.Option.HOME.title).getContentDescription()).endsWith(TurnOnChoice.CHOSEN));
            assertNull(find(root(a), TurnOnChoiceActivity.ANDROID_CHOICE));
            assertTrue(words(root(a)).contains(HomeTakeoverActivity.REVIEW_BUILD_WORDS));
        }
        assertTrue("record unchanged by looking", HomeTakeover.stockOff(app));
        assertTrue(record().delete()); stockHome(true);
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
            assertNull("only on When the TV turns on", find(root(a), HomeTakeoverActivity.TAKE_TITLE));
            category(a, TurnOnChoice.TITLE);
            Intent page = Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(TurnOnChoiceActivity.class.getName(), page.getComponent().getClassName());
        }
        try (ActivityController<TurnOnChoiceActivity> c = Robolectric.buildActivity(TurnOnChoiceActivity.class).setup()) {
            TurnOnChoiceActivity a = c.get(); idle();
            assertTrue(words(root(a)).contains(TurnOnChoice.Option.HOME.line));
            press(a, TurnOnChoice.Option.HOME.title);
            Intent started = Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(HomeTakeoverActivity.class.getName(), started.getComponent().getClassName());
            assertEquals(HomeTakeoverActivity.TAKE, started.getStringExtra(HomeTakeoverActivity.MODE));
        }
    }

    // --- crash safety
    @Test public void twoEarlyCrashesWhileHoldingHomeShowTheSafeScreen() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        writeRecord("home-takeover-record.json"); entry(true); tv.disabled.add(STOCK); stockHome(false);
        assertFalse("not yet", HomeSafety.appStart((android.app.Application) app));
        try (ActivityController<UnifiedPreviewActivity> first = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            idle();
            long now = SystemClock.elapsedRealtime();
            HomeSafety.crashed(app, true, now + HomeSafety.EARLY_MS + 1);
            assertEquals("a crash after the first minute isn't counted", 0, HomeSafety.crashes(app));
            HomeSafety.crashed(app, false, now + 10);
            assertEquals("Home is the resumed screen: any thread counts", 1, HomeSafety.crashes(app));
            HomeSafety.crashed(app, true, now + 20);
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

    @Test public void theSafeScreenSaysTheWaysBackWithoutUnifiedTv() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        writeRecord("home-takeover-record.json"); entry(true); stockHome(false);
        app.getSharedPreferences(HomeSafety.PREFS, 0).edit().putInt(HomeSafety.CRASHES, 2).commit();
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            String said = words(root(c.get()));
            assertTrue(said, said.contains(HomeTakeover.tvOnlySteps("Android TV Home")));
            assertTrue(said.contains(HomeTakeover.pcFix(STOCK)));
        }
    }

    @Test public void aCrashWhileTheAppStartsCountsAndTheNextStartShowsTheSafeScreen() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        writeRecord("home-takeover-record.json"); entry(true); tv.disabled.add(STOCK); stockHome(false);
        android.app.Application application = (android.app.Application) app;
        // Two starts that each crash inside PreviewApp.onCreate (main thread, no screen resumed yet).
        assertFalse(HomeSafety.appStart(application));
        HomeSafety.crashed(app, true, SystemClock.elapsedRealtime() + 5);
        assertFalse(HomeSafety.appStart(application));
        HomeSafety.crashed(app, true, SystemClock.elapsedRealtime() + 5);
        assertEquals(2, HomeSafety.crashes(app));
        assertTrue("the next start makes PreviewApp's own steps best-effort", HomeSafety.appStart(application));
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            idle(); assertTrue(words(root(c.get())).contains(HomeSafety.TITLE)); assertFalse(hasHome(c.get()));
        }
    }

    @Test public void crashesInOtherScreensOrLaterDontCount() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        assertTrue("app start, nothing resumed, any thread", HomeSafety.counts(false, false, false, false, 10));
        assertTrue("Home in front, any thread", HomeSafety.counts(false, true, true, false, 10));
        assertTrue("Home still starting, main thread", HomeSafety.counts(true, true, false, true, 10));
        assertFalse("Home still starting, another thread", HomeSafety.counts(false, true, false, true, 10));
        assertFalse("another screen in front (Settings), main thread", HomeSafety.counts(true, true, false, false, 10));
        assertFalse("after the first minute", HomeSafety.counts(true, false, true, true, HomeSafety.EARLY_MS + 1));
        writeRecord("home-takeover-record.json"); entry(true);
        HomeSafety.appStart((android.app.Application) app);
        try (ActivityController<SettingsActivity> settings = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            idle();
            HomeSafety.crashed(app, true, SystemClock.elapsedRealtime() + 5);
            HomeSafety.crashed(app, false, SystemClock.elapsedRealtime() + 5);
            assertEquals("Settings in front: not Home's crash", 0, HomeSafety.crashes(app));
        }
        assertNotNull("the handler is in place", Thread.getDefaultUncaughtExceptionHandler());
    }

    // --- the record is lost (storage cleared) while the TV's own Home is still off
    @Test public void withdrawRefusesWhenNoOtherHomeIsLeft() {
        Assume.assumeTrue("launcher app only", launcher());
        entry(true); stockHome(false);
        assertFalse("no record says so, but no other Home is on", HomeTakeover.stockOff(app));
        assertFalse(DefaultHome.withdraw(app));
        assertTrue("the Home entry stays", DefaultHome.offered(app));
        assertEquals(STOCK, HomeTakeover.disabledStock(app));
        stockHome(true);
        assertTrue(DefaultHome.withdraw(app));
        assertFalse(DefaultHome.offered(app));
    }

    @Test public void returnWithALostRecordTurnsTheStockHomeOnFirst() {
        Assume.assumeTrue("launcher app only", launcher());
        entry(true); tv.disabled.add(STOCK); stockHome(false);
        try (ActivityController<HomeTakeoverActivity> c = open(false)) {
            HomeTakeoverActivity a = c.get();
            assertTrue(words(root(a)).contains("Unified TV has no record of it"));
            press(a, HomeTakeoverActivity.RETURN_TITLE);
            assertEquals("on first, then set as Home", Arrays.asList(AdbCommands.enable(STOCK), AdbCommands.setHome(FakeTv.HOME)), tv.changes());
            assertFalse(tv.disabled.contains(STOCK));
            assertFalse("then the Home entry withdrawn", DefaultHome.offered(app));
            assertTrue(words(root(a)).contains("The Home button opens Android TV Home again."));
        }
    }

    @Test public void lostRecordWithoutTheLinkGivesTheTvOnlyRouteAndNeverWithdrawsFirst() {
        Assume.assumeTrue("launcher app only", launcher());
        entry(true); tv.disabled.add(STOCK); stockHome(false);
        refuse = new AdbException(AdbException.Kind.CLOSED, "Nothing answers on port 5555: network debugging is off.");
        try (ActivityController<HomeTakeoverActivity> c = open(false)) {
            HomeTakeoverActivity a = c.get();
            press(a, HomeTakeoverActivity.RETURN_TITLE);
            String said = words(root(a));
            assertTrue(said, said.contains(HomeTakeover.tvOnlySteps("Android TV Home")));
            assertTrue(said.contains(HomeTakeover.pcFix(STOCK)));
            assertTrue("never withdrawn first", DefaultHome.offered(app));
            press(a, "Check again");
            assertTrue("still off: the entry stays", DefaultHome.offered(app));
            stockHome(true);   // the owner turned Android TV Home on in the TV's settings
            press(a, "Check again");
            assertFalse(DefaultHome.offered(app));
            assertTrue(words(root(a)).contains("The Home button opens the original TV interface again."));
        }
    }

    @Test public void returnIsOpenToKidsBehindTheAdultPin() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        app.getSharedPreferences("unified-tv-preview", 0).edit().putString(Profiles.PREF, Profiles.CHILD).putString("pin", Pins.record("2468")).commit();
        assertTrue(Profiles.kids(app));
        try (ActivityController<HomeTakeoverActivity> c = open(true)) {
            assertTrue("taking over stays for Adults", words(root(c.get())).contains("This is for Adults"));
        }
        try (ActivityController<HomeTakeoverActivity> c = open(false)) {
            HomeTakeoverActivity a = c.get();
            assertFalse("the way back never refuses Kids", words(root(a)).contains("This is for Adults"));
            android.app.AlertDialog pin = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("but the adult PIN is asked", pin);
            Pins.type(pin, "2468");
            assertNotNull(find(root(a), HomeTakeoverActivity.RETURN_TITLE));
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
            assertNull(starting(root(a), TurnOnChoice.TITLE));
        }
        assertNull("nor the choice's page", app.getPackageManager().resolveActivity(new Intent(app, TurnOnChoiceActivity.class), 0));
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
