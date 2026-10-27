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
 * <li>AirPlay: ready → the "how to connect" panel without opening AirPin; not ready → AirPin starts, and Unified TV comes
 *     back with the panel as soon as the service appears; never → the plain "didn't start", with Open AirPin.</li>
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
        LiveTvProtection.Facts f = new LiveTvProtection.Facts(); f.vpn = vpn; f.nordInstalled = true; f.onlyNordCanVpn = true; return f;
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
            assertNotNull(find(a.getWindow().getDecorView(), VpnGate.DEBUG_ACCESS)); assertNull("no dead button", find(a.getWindow().getDecorView(), "Allow notification access"));
            assertTrue(w.contains(MediaWatch.pcCommand(app())));
        }
    }
    private static List<View> all(View v) { List<View> out = new ArrayList<>(); out.add(v); if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) out.addAll(all(((ViewGroup) v).getChildAt(i))); return out; }

    // --- AirPlay
    @Test public void airPlayReadyShowsHowToConnectAndOpensNothing() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        AirPlayReceiver.ready = true; AirPlayReceiver.advertisedAs = "Living Room TV";
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            assertNull(AirPlayStart.press(a, AIRPIN));
            assertNull("AirPin is not opened", Shadows.shadowOf(a).getNextStartedActivity());
            View s = sheet(); assertNotNull(s);
            assertTrue(words(s), words(s).contains("AirPlay is ready. On your iPhone or Mac, open Control Centre > Screen Mirroring (or the AirPlay button) and choose 'Living Room TV'."));
            assertTrue(words(s).contains(AirPlayStart.TIP)); assertNotNull(find(s, "Open AirPin settings"));
        }
        assertTrue(AirPlayStart.readyWords("").endsWith("choose this TV."));
    }
    @Test public void airPlayNotReadyStartsAirPinAndComesBackWhenItAppears() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        int[] asked = {0}; AirPlayReceiver.probeForTests = c -> ++asked[0] < 3 ? null : "Living Room TV";
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            assertNull(AirPlayStart.press(a, AIRPIN));
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
    @Test public void airPlayThatNeverStartsSaysSoPlainly() {
        install(AIRPIN, AIRPIN + ".MainActivity", "AirPin LITE");
        int[] asked = {0}; AirPlayReceiver.probeForTests = c -> { asked[0]++; return null; };
        try (ActivityController<UnifiedPreviewActivity> c = Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()) {
            Activity a = c.get(); Shadows.shadowOf(a).getNextStartedActivity();
            AirPlayStart.press(a, AIRPIN); c.pause(); idle(10); c.resume(); idle(10);
            assertTrue("a bounded number of looks", asked[0] <= AirPlayStart.ATTEMPTS && asked[0] > 0);
            View s = sheet(); assertNotNull(s); assertTrue(words(s), words(s).contains(AirPlayStart.NOT_STARTED));
            Button open = find(s, "Open AirPin"); assertNotNull(open); open.performClick();
            assertTrue(is(Shadows.shadowOf(a).getNextStartedActivity(), AIRPIN));
        }
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
        assertEquals("part of the TV's system, with no app to open", LiveTvProtection.ignoredBecause("com.tcl.datasaver", true, true, false));
        assertEquals("turned off", LiveTvProtection.ignoredBecause("com.example.vpn", false, false, true));
        Map<String, String> ignored = new LinkedHashMap<>(); ignored.put("com.tcl.datasaver", "part of the TV's system, with no app to open");
        LiveTvProtection.Facts f = facts(VpnGate.State.CONNECTED);
        String line = OwnerChecks.liveFacts(f, Collections.singleton(NORD), ignored, false, null);
        assertTrue(line, line.contains("Apps that can run a VPN: com.nordvpn.android.")); assertTrue(line.contains("Ignored VPN apps: com.tcl.datasaver (part of the TV's system, with no app to open)."));
    }
}
