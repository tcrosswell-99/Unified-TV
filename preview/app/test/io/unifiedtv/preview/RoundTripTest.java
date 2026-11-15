package io.unifiedtv.preview;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import io.unifiedtv.adb.AdbCommands;
import io.unifiedtv.adb.AdbException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.shadows.ShadowToast;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Owner's TCL, build run 99: short trips to another app that Unified TV finishes by itself (Comeback).
 * <ul>
 * <li>Connect for a refused Live TV launch opens NordVPN; once the rule allows, Unified TV comes back and opens Live TV
 *     ("NordVPN connected — opening Live TV"); if the household comes back first and the rule still refuses, the reason.</li>
 * <li>Notification access through the TV's debugging prompt: exactly one allow_listener command for Unified TV's own
 *     listener, checked with Android, the link closed, then the debugging-off check.</li>
 * <li>AirPlay (OD-30): the icon never opens AirPin. Ready → the "how to connect" panel; not ready → "AirPlay isn't running",
 *     with Check again (looks in place) and Start AirPin now, the only way into AirPin: it starts, and Unified TV comes back
 *     with the panel as soon as the service appears; never → the plain "didn't start". Settings says the same.</li>
 * </ul>
 * Both flavours run this class. Nothing here reads or consumes a key.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class RoundTripTest {
    private static final String LIVE = "tv.example.iptvsmarters", NORD = LiveTvProtection.NORDVPN, AIRPIN = "com.waxrain.airplaydmr";
    private Context app() { return RuntimeEnvironment.getApplication(); }
    private static void idle(long ms) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
    private void install(String pkg, String cls, String label) {
        ShadowPackageManager pm = Shadows.shadowOf(app().getPackageManager());
        ComponentName c = new ComponentName(pkg, cls); ActivityInfo info = pm.addActivityIfNotPresent(c); info.nonLocalizedLabel = label;
        IntentFilter f = new IntentFilter(Intent.ACTION_MAIN); f.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        try { pm.addIntentFilterForActivity(c, f); } catch (Exception e) { throw new AssertionError(e); }
    }
    private static LiveTvProtection.Facts facts(VpnGate.State vpn) {
        LiveTvProtection.Facts f = new LiveTvProtection.Facts(); f.vpn = vpn; f.nordInstalled = true; return f;
    }
    private static String words(View v) { StringBuilder b = new StringBuilder(); if (v instanceof TextView && v.isShown()) b.append(((TextView) v).getText()).append('\n'); if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i))); return b.toString(); }
    private static Button find(View v, String text) { if (v instanceof Button && v.isShown() && ((Button) v).getText().toString().equals(text)) return (Button) v; if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = find(((ViewGroup) v).getChildAt(i), text); if (b != null) return b; } return null; }
    private static View sheet() { Dialog d = ShadowDialog.getLatestDialog(); return d == null || !d.isShowing() ? null : d.getWindow().getDecorView(); }
    private static boolean is(Intent i, String pkg) { return i != null && i.getComponent() != null && pkg.equals(i.getComponent().getPackageName()); }

    @Before public void clean() {
        app().getSharedPreferences("unified-tv-preview", 0).edit().clear().putString("dock", RemoteJourneyTest.SEEDED).commit();
        install(LIVE, LIVE + ".MainActivity", "IPTV Smarters"); install(NORD, NORD + ".tv.TvMainActivity", "NordVPN");
        LiveTvProtection.forTests = facts(VpnGate.State.DISCONNECTED); ShadowSettings.setCanDrawOverlays(true); ShadowDialog.reset(); ShadowToast.reset();
        AirPlayReceiver.ready = false; AirPlayReceiver.advertisedAs = ""; AirPlayStart.workerForTests = Runnable::run; AirPlayStart.pauseMs = 0;
    }
    @After public void reset() {
        Comeback.disarm(); NordConnect.stop(); LiveTvProtection.forTests = null; ShadowSettings.setCanDrawOverlays(false);
        AirPlayReceiver.probeForTests = null; AirPlayReceiver.ready = false; AirPlayStart.workerForTests = null; AirPlayStart.pauseMs = 1000;
        CleanupActivity.openerForTests = null; CleanupActivity.backgroundForTests = null;
    }

    // --- Connect, then carry on into Live TV
    private void refusedThenConnect(Activity a) {
        assertEquals("", Launch.open(a, LIVE, "Live TV"));
        View s = sheet(); assertNotNull(s); assertTrue(words(s), words(s).contains("Connect NordVPN first"));
        find(s, "Connect").performClick();
        Intent nord = Shadows.shadowOf(a).getNextStartedActivity(); assertTrue("Connect opens NordVPN", is(nord, NORD));
        assertTrue("the refused launch waits", Comeback.armed()); assertTrue("watching the VPN", NordConnect.watching());
    }
    @Test public void connectedInNordVpnBringsUnifiedTvBackIntoLiveTv() {
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            refusedThenConnect(a); c.pause(); idle(5000);   // NordVPN is in front now, for a while
            NordConnect.check(); assertTrue("not connected yet: nothing happens", Comeback.armed());
            LiveTvProtection.forTests = facts(VpnGate.State.CONNECTED);
            NordConnect.check();
            Intent back = Shadows.shadowOf((android.app.Application) app()).getNextStartedActivity();
            assertNotNull("Unified TV comes back by itself", back); assertEquals(UnifiedPreviewActivity.class.getName(), back.getComponent().getClassName());
            assertTrue((back.getFlags() & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0);
            assertFalse(NordConnect.watching());
            c.resume();
            // Only after Home's own onResume (which stops any old Live TV watch) has finished: then Live TV and its watch.
            Intent early; boolean opened = false; while ((early = Shadows.shadowOf(a).getNextStartedActivity()) != null) opened |= is(early, LIVE);
            assertFalse("not inside Home's onResume", opened);
            while (Shadows.shadowOf((android.app.Application) app()).getNextStoppedService() != null) { /* Home's own stop, before */ }
            idle(10);
            Intent next; do next = Shadows.shadowOf(a).getNextStartedActivity(); while (next != null && !is(next, LIVE));
            assertTrue("straight into Live TV, no Back presses", is(next, LIVE));
            Intent watch = Shadows.shadowOf((android.app.Application) app()).getNextStartedService();
            while (watch != null && !LiveTvGuard.class.getName().equals(watch.getComponent().getClassName())) watch = Shadows.shadowOf((android.app.Application) app()).getNextStartedService();
            assertNotNull("the safety net watches it", watch);
            assertNull("and nothing stopped it afterwards", Shadows.shadowOf((android.app.Application) app()).getNextStoppedService());
            assertEquals(NordConnect.OPENING, ShadowToast.getTextOfLatestToast()); assertFalse(Comeback.armed());
        }
    }
    @Test public void comingBackFirstStillRefusedSaysWhy() {
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            refusedThenConnect(a); ShadowDialog.reset(); c.pause(); idle(5000); c.resume(); idle(10);
            assertFalse(is(Shadows.shadowOf(a).getNextStartedActivity(), LIVE));
            View s = sheet(); assertNotNull("the reason, again", s); assertTrue(words(s).contains("Live TV is locked. Connect NordVPN first."));
            assertFalse("once", Comeback.armed()); assertFalse(NordConnect.watching());
        }
    }
    @Test public void alreadyBackWhenNordVpnConnectsCarriesOnThere() {
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            refusedThenConnect(a);   // Home still in front (NordVPN closed at once, say)
            LiveTvProtection.forTests = facts(VpnGate.State.CONNECTED); NordConnect.check();
            assertTrue(is(Shadows.shadowOf(a).getNextStartedActivity(), LIVE));
        }
    }
    @Test public void theWatchEndsAfterTwoMinutes() {
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); refusedThenConnect(a); c.pause();
            idle(NordConnect.WINDOW_MS + 1000);
            assertFalse(NordConnect.watching()); assertFalse(Comeback.armed());
        }
    }

    // --- notification access through the TV's debugging prompt
    @Test public void notificationAccessThroughTheDebuggingPrompt() {
        List<String> sent = new ArrayList<>(); boolean[] closed = {false};
        ComponentName listener = MediaWatch.component(app());
        Cleanup.Shell shell = command -> { sent.add(command);
            if (command.equals(AdbCommands.allowListener(app().getPackageName()))) Shadows.shadowOf(app().getSystemService(NotificationManager.class)).setNotificationListenerAccessGranted(listener, true);
            return ""; };
        CleanupActivity.openerForTests = (c, prompt, opening) -> { prompt.showing(); return new CleanupActivity.Opened(shell, () -> closed[0] = true); };
        CleanupActivity.backgroundForTests = Runnable::run;
        try (ActivityController<NotificationAccessActivity> c = Robolectric.buildActivity(NotificationAccessActivity.class).setup()) {
            NotificationAccessActivity a = c.get(); idle(100);
            String intro = words(a.findViewById(android.R.id.content));
            assertTrue(intro, intro.contains("Amazon Music")); assertTrue(intro.contains(MediaWatch.pcCommand(app())));
            find(a.findViewById(android.R.id.content), "Start").performClick(); idle(100);
            assertEquals("exactly one command, for this app's own listener", Collections.singletonList("cmd notification allow_listener " + app().getPackageName() + "/io.unifiedtv.preview.MediaWatch"), sent);
            assertTrue("checked with Android", MediaWatch.granted(app()));
            assertTrue("the link closes", closed[0]);
            String done = words(a.findViewById(android.R.id.content));
            assertTrue(done, done.contains(NotificationAccessActivity.ON)); assertTrue(done.contains("Turn off USB debugging"));
            assertNotNull(find(a.findViewById(android.R.id.content), "Check that it's off"));
        }
        assertEquals("adb shell cmd notification allow_listener " + app().getPackageName() + "/io.unifiedtv.preview.MediaWatch", MediaWatch.pcCommand(app()));
    }
    @Test public void debuggingOffSaysSoWithTheComputerCommand() {
        CleanupActivity.openerForTests = (c, prompt, opening) -> { throw new AdbException(AdbException.Kind.CLOSED, "off"); };
        CleanupActivity.backgroundForTests = Runnable::run;
        try (ActivityController<NotificationAccessActivity> c = Robolectric.buildActivity(NotificationAccessActivity.class).setup()) {
            NotificationAccessActivity a = c.get(); idle(100);
            find(a.findViewById(android.R.id.content), "Start").performClick(); idle(100);
            String w = words(a.findViewById(android.R.id.content));
            assertTrue(w, w.contains("Network debugging is off")); assertTrue(w.contains(MediaWatch.pcCommand(app())));
            assertNotNull(find(a.findViewById(android.R.id.content), "Try again"));
        }
    }
    @Test public void settingsOffersTheDebuggingPromptWhenTheTvHasNoScreen() {
        LiveTvProtection.forTests = null;
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get();
            Button row = null; for (View v : all(a.getWindow().getDecorView())) if (v instanceof Button && ((Button) v).getText().toString().startsWith("Live TV & Network Protection")) row = (Button) v;
            assertNotNull(row); row.performClick(); idle(500);
            String w = words(a.getWindow().getDecorView());
            assertTrue(w, w.contains(NordConnect.AUTO_TIP));
            assertNotNull(find(a.getWindow().getDecorView(), NotificationAccessActivity.DEBUG_ACCESS)); assertNull("no dead button", find(a.getWindow().getDecorView(), "Allow notification access"));
            assertTrue(w.contains(MediaWatch.pcCommand(app())));
        }
    }
    private static List<View> all(View v) { List<View> out = new ArrayList<>(); out.add(v); if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) out.addAll(all(((ViewGroup) v).getChildAt(i))); return out; }

    // --- AirPlay (OD-30: the icon never takes the household into AirPin)
    private static Button firstButton(View v) { if (v instanceof Button && v.isShown()) return (Button) v; if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = firstButton(((ViewGroup) v).getChildAt(i)); if (b != null) return b; } return null; }
    @Test public void airPlayReadyShowsHowToConnectAndOpensNothing() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        AirPlayReceiver.ready = true; AirPlayReceiver.advertisedAs = "Living Room TV";
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            assertNull(AirPlayStart.press(a, AIRPIN)); idle(10);
            assertNull("AirPin is not opened", Shadows.shadowOf(a).getNextStartedActivity());
            assertNull(Shadows.shadowOf((android.app.Application) app()).getNextStartedActivity());
            View s = sheet(); assertNotNull(s);
            assertTrue(words(s), words(s).contains("AirPlay is ready. On your iPhone or Mac, open Control Centre > Screen Mirroring (or the AirPlay button) and choose 'Living Room TV'."));
            assertTrue(words(s).contains(AirPlayStart.TIP)); assertNotNull(find(s, "Done"));
            assertNull("no way into AirPin from the ready panel", find(s, "Open AirPin settings")); assertNull(find(s, AirPlayStart.START_NOW));
            assertFalse(Comeback.armed()); assertFalse(AirPlayStart.watching());
        }
        assertTrue(AirPlayStart.readyWords("").endsWith("choose this TV."));
    }
    @Test public void airPlayNotReadyShowsThePanelAndOpensNothing() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        int[] asked = {0}; AirPlayReceiver.probeForTests = c -> { asked[0]++; return "Living Room TV"; };
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            assertNull(AirPlayStart.press(a, AIRPIN)); idle(AirPlayStart.WINDOW_MS + 1000);
            assertNull("AirPin is never opened by the icon", Shadows.shadowOf(a).getNextStartedActivity());
            assertNull(Shadows.shadowOf((android.app.Application) app()).getNextStartedActivity());
            assertNotEquals(AirPlayStart.STARTING, ShadowToast.getTextOfLatestToast());
            assertEquals("no look until the household asks for one", 0, asked[0]);
            assertFalse(Comeback.armed()); assertFalse(AirPlayStart.watching());
            View s = sheet(); assertNotNull(s);
            assertTrue(words(s), words(s).contains("AirPlay isn't running. Turn on 'start at boot' (or 'run in background') in AirPin's settings once, and AirPlay will always be ready."));
            Button again = firstButton(s); assertNotNull(again);
            assertEquals("Check again comes first, with focus", AirPlayStart.CHECK_AGAIN, again.getText().toString()); assertTrue(again.isFocused());
            assertNotNull(find(s, AirPlayStart.START_NOW)); assertNull(find(s, "Open AirPin"));
        }
    }
    @Test public void checkAgainLooksInPlace() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        String[] seen = {null}; String[] answer = {null}; int[] asked = {0};
        AirPlayReceiver.probeForTests = c -> { asked[0]++; View s = sheet(); seen[0] = s == null ? null : words(s); return answer[0]; };
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            AirPlayStart.press(a, AIRPIN); idle(10);
            Dialog panel = ShadowDialog.getLatestDialog();
            find(sheet(), AirPlayStart.CHECK_AGAIN).performClick();
            assertEquals("one look", 1, asked[0]); assertTrue(seen[0], seen[0].contains(AirPlayStart.CHECKING));
            assertTrue("still looking until the answer is back on screen", AirPlayStart.checking());
            idle(10);
            assertFalse(AirPlayStart.checking()); assertSame("the same panel", panel, ShadowDialog.getLatestDialog()); assertTrue(panel.isShowing());
            assertTrue(words(sheet()), words(sheet()).contains(AirPlayStart.STILL_NOT_RUNNING));
            answer[0] = "Living Room TV";
            find(sheet(), AirPlayStart.CHECK_AGAIN).performClick(); idle(10);
            assertEquals(2, asked[0]); assertTrue(AirPlayReceiver.ready); assertFalse(panel.isShowing());
            View s = sheet(); assertNotNull(s); assertTrue(words(s), words(s).contains("choose 'Living Room TV'")); assertNotNull(find(s, "Done"));
            assertNull("checking never opens AirPin", Shadows.shadowOf(a).getNextStartedActivity());
        }
    }
    @Test public void startAirPinNowStartsAirPinAndComesBackWhenItAppears() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        int[] asked = {0}; AirPlayReceiver.probeForTests = c -> ++asked[0] < 3 ? null : "Living Room TV";
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            assertNull(AirPlayStart.press(a, AIRPIN)); idle(10);
            assertNull(Shadows.shadowOf(a).getNextStartedActivity());
            find(sheet(), AirPlayStart.START_NOW).performClick();
            assertEquals(AirPlayStart.STARTING, ShadowToast.getTextOfLatestToast());
            assertTrue("AirPin starts", is(Shadows.shadowOf(a).getNextStartedActivity(), AIRPIN));
            c.pause(); idle(10);   // AirPin in front; the service appeared on the third look
            assertEquals(3, asked[0]); assertTrue(AirPlayReceiver.ready);
            Intent back = Shadows.shadowOf((android.app.Application) app()).getNextStartedActivity();
            assertNotNull("Unified TV comes back by itself", back); assertEquals(UnifiedPreviewActivity.class.getName(), back.getComponent().getClassName());
            c.resume(); idle(10);
            View s = sheet(); assertNotNull(s); assertTrue(words(s), words(s).contains("choose 'Living Room TV'"));
        }
    }
    @Test public void startAirPinNowThatNeverStartsSaysSoPlainly() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        int[] asked = {0}; AirPlayReceiver.probeForTests = c -> { asked[0]++; return null; };
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            AirPlayStart.press(a, AIRPIN); idle(10); find(sheet(), AirPlayStart.START_NOW).performClick();
            Shadows.shadowOf(a).getNextStartedActivity(); c.pause(); idle(10);
            // Review: never pulled out of AirPin's own prompts, or another app, when AirPlay didn't start.
            assertNull("nothing brought forward", Shadows.shadowOf((android.app.Application) app()).getNextStartedActivity());
            assertTrue("it waits for the household to come back", Comeback.armed());
            c.resume(); idle(10);
            assertTrue("a bounded number of looks", asked[0] <= AirPlayStart.ATTEMPTS && asked[0] > 0);
            View s = sheet(); assertNotNull(s); assertTrue(words(s), words(s).contains(AirPlayStart.NOT_STARTED));
            assertNotNull(find(s, AirPlayStart.CHECK_AGAIN));
            Button start = find(s, AirPlayStart.START_NOW); assertNotNull(start); start.performClick();
            assertTrue(is(Shadows.shadowOf(a).getNextStartedActivity(), AIRPIN));
        }
    }
    @Test public void settingsAirPlaySaysTheSameAndChecksAgain() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        AirPlayReceiver.probeForTests = c -> "Living Room TV";
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); View root = a.getWindow().getDecorView();
            Button row = null; for (View v : all(root)) if (v instanceof Button && ((Button) v).getText().toString().startsWith("Connections & Devices")) row = (Button) v;
            assertNotNull(row); row.performClick(); idle(500);
            String w = words(root);
            assertTrue(w, w.contains(AirPlayStart.NOT_RUNNING)); assertTrue(w.contains(AirPlayStart.HOW_TO));
            // AirPlay's own Check again: the one just above Open AirPin LITE (Connections has another, for the network).
            Button again = null; for (View v : all(root)) { if (v instanceof Button && ((Button) v).getText().toString().equals("Open AirPin LITE")) break; if (v instanceof Button && ((Button) v).getText().toString().equals(AirPlayStart.CHECK_AGAIN)) again = (Button) v; }
            assertNotNull(again); again.performClick(); idle(500);
            w = words(root);
            assertTrue(w, w.contains(AirPlayStart.readyWords("Living Room TV"))); assertFalse(w.contains(AirPlayStart.NOT_RUNNING));
            assertNull("checking opens nothing", Shadows.shadowOf(a).getNextStartedActivity());
        }
    }
    // --- review: a closed screen is never reopened, and each trip delivers only its own step
    @Test public void aClosedScreenIsNeverReopenedLiveTvCarriesOnFromHome() {
        Activity from;
        try (ActivityController<AppsActivity> c = Robolectric.buildActivity(AppsActivity.class).setup()) {
            from = c.get(); Shadows.shadowOf(from).getNextStartedActivity();
            refusedThenConnect(from); c.pause().stop();
            from.finish(); c.destroy();   // closed by itself while NordVPN is open (Ask your TV's idle close, say)
        }
        LiveTvProtection.forTests = facts(VpnGate.State.CONNECTED); NordConnect.check();
        Intent back = Shadows.shadowOf((android.app.Application) app()).getNextStartedActivity();
        assertNotNull(back); assertEquals("Home, never a fresh copy of the closed screen", UnifiedPreviewActivity.class.getName(), back.getComponent().getClassName());
        try (ActivityController<UnifiedPreviewActivity> h = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            idle(10); Intent next; do next = Shadows.shadowOf(h.get()).getNextStartedActivity(); while (next != null && !is(next, LIVE));
            assertTrue("Live TV opens from Home", is(next, LIVE)); assertEquals(NordConnect.OPENING, ShadowToast.getTextOfLatestToast());
        }
    }
    @Test public void aGoneScreenDisarmsInsteadOfBeingRecreated() {
        try (ActivityController<AppsActivity> c = Robolectric.buildActivity(AppsActivity.class).setup()) {
            Activity a = c.get(); boolean[] ran = {false};
            Comeback.Token t = Comeback.arm(a, 60_000, on -> ran[0] = true);
            a.finish();
            assertEquals(Comeback.Result.GONE, Comeback.deliver(app(), t)); assertFalse(Comeback.armed()); assertFalse(ran[0]);
            assertNull(Shadows.shadowOf((android.app.Application) app()).getNextStartedActivity());
        }
    }
    @Test public void eachTripDeliversOnlyItsOwnStep() {
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            refusedThenConnect(a); c.pause();
            boolean[] other = {false}; Comeback.Token airplay = Comeback.arm(a, 60_000, on -> other[0] = true);   // AirPlay or access re-armed
            LiveTvProtection.forTests = facts(VpnGate.State.CONNECTED); NordConnect.check();
            assertNull("NordVPN connecting never delivers another trip's step", Shadows.shadowOf((android.app.Application) app()).getNextStartedActivity());
            assertFalse(other[0]); assertTrue(Comeback.armed(airplay)); assertFalse(NordConnect.watching());
        }
    }
    @Test public void theToastOnlyWhenLiveTvActuallyOpened() {
        LiveTvProtection.forTests = facts(VpnGate.State.CONNECTED);
        app().getSharedPreferences("unified-tv-preview", 0).edit().putString(Profiles.PREF, Profiles.CHILD).putString(KidsPolicy.APPS_PREF, "").commit();
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            ShadowToast.reset(); NordConnect.open(c.get(), LIVE, "Live TV");
            assertEquals("Kids: the refusal, not \"opening Live TV\"", "Live TV isn't on the Kids list. Ask an adult.", ShadowToast.getTextOfLatestToast());
            assertFalse(is(Shadows.shadowOf(c.get()).getNextStartedActivity(), LIVE));
        }
    }
    @Test public void anUnknownBuildNeverCrashesTheAccessRoute() {
        Context other = new ContextWrapper(app()) { @Override public String getPackageName() { return "com.example.other"; } };
        assertEquals("adb shell cmd notification allow_listener com.example.other/io.unifiedtv.preview.MediaWatch", MediaWatch.pcCommand(other));
        assertFalse(NotificationAccessActivity.available(other));
        Context deviceCheck = new ContextWrapper(app()) { @Override public String getPackageName() { return "io.unifiedtv.preview.devicecheck"; } };
        assertTrue("this app's own review build", NotificationAccessActivity.available(deviceCheck));
        assertTrue(AdbCommands.allowed(AdbCommands.allowListener("io.unifiedtv.preview.devicecheck")));
    }

    @Test public void noAirPinKeepsTheOldAnswer() {
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            assertEquals("AirPin LITE is not installed on this TV", AirPlayStart.press(c.get(), null));
        }
    }

    // --- build run 99 (b): which apps count as able to run a VPN
    @Test public void tvPlumbingDoesNotCountAsAnotherVpnApp() {
        assertNull(LiveTvProtection.ignoredBecause(NORD, true, false, true));
        assertNull("an app the household installed", LiveTvProtection.ignoredBecause("com.example.vpn", true, false, true));
        assertNull("a system app with an app to open still counts", LiveTvProtection.ignoredBecause("com.tcl.vpn", true, true, true));
        assertEquals(LiveTvProtection.PLUMBING, LiveTvProtection.ignoredBecause("com.tcl.datasaver", true, true, false));
        assertEquals("turned off", LiveTvProtection.ignoredBecause("com.example.vpn", false, false, true));
        Map<String, String> ignored = new LinkedHashMap<>(); ignored.put("com.tcl.datasaver", "part of the TV's system, with no app to open");
        LiveTvProtection.Facts f = facts(VpnGate.State.CONNECTED);
        String line = OwnerChecks.liveFacts(f, Collections.singleton(NORD), ignored, false, null);
        assertTrue(line, line.contains("Apps that can run a VPN: com.nordvpn.android.")); assertTrue(line.contains("Ignored VPN apps: com.tcl.datasaver (part of the TV's system, with no app to open)."));
    }
}
