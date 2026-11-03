package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.io.File;
import java.time.Duration;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowSettings;
import org.w3c.dom.Element;
import static org.junit.Assert.*;

/**
 * OD-31 (owner, 3 November 2026): the remote button that opens Unified TV. Launcher app only, off by default, adults (PIN).
 * Only the chosen keycode is consumed and every other key passes on; Back, Home, the arrows, OK, volume, power, numbers
 * and players' buttons can't be chosen; pressing it brings Home forward without closing a film playing in Unified TV's
 * own player; off, nothing is consumed; the service is declared with key filtering and no window content; the page says
 * "Switched on" once Android's switch is on; a screen-on after the button opens Unified TV. Both flavours run this class:
 * the preview has none of it.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class RemoteButtonTest {
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
    /** The next start, after the main thread has run what is due (the service posts its launch). */
    private Intent next() { Shadows.shadowOf(Looper.getMainLooper()).idle(); return Shadows.shadowOf((android.app.Application) app).getNextStartedActivity(); }
    private void drain() { while (next() != null) { /* earlier starts */ } }
    private static KeyEvent down(int code) { return new KeyEvent(KeyEvent.ACTION_DOWN, code); }
    private static KeyEvent up(int code) { return new KeyEvent(KeyEvent.ACTION_UP, code); }
    /** As Robolectric.setupService does (written out so the stand-in compile check's Robolectric stub needn't have it), then bound. */
    private ButtonService service() {
        ButtonService s = org.robolectric.android.controller.ServiceController.of(new ButtonService(), new Intent(app, ButtonService.class)).create().get();
        s.onServiceConnected(); idle(); return s;
    }
    private void chosen(int key, boolean on) { prefs().edit().putInt(RemoteButton.KEY, key).putBoolean(RemoteButton.ON, on).commit(); }
    private void serviceSwitchedOn(boolean on) {
        Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            on ? "com.example.reader/.Reader:" + RemoteButton.component(app) : "com.example.reader/.Reader");
    }
    private ActivityController<TurnOnChoiceActivity> page() { ActivityController<TurnOnChoiceActivity> c = Robolectric.buildActivity(TurnOnChoiceActivity.class).setup(); idle(); return c; }
    private void accessibilityScreen() {
        ResolveInfo ri = new ResolveInfo(); ri.activityInfo = new ActivityInfo(); ri.activityInfo.packageName = "com.android.tv.settings"; ri.activityInfo.name = "com.android.tv.settings.Accessibility";
        Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), ri);
    }

    @Before public void clean() {
        app = RuntimeEnvironment.getApplication(); prefs().edit().clear().commit(); ShadowAlertDialog.reset(); ShadowSettings.setCanDrawOverlays(false);
        serviceSwitchedOn(false); RemoteButton.listen(null, 0); HouseholdService.alarmOnScreen = false; ButtonService.lastWake = null; ButtonService.lastFilter = null; drain();
    }
    @After public void reset() { RemoteButton.listen(null, 0); ShadowSettings.setCanDrawOverlays(false); }

    // --- pure
    @Test public void refusedButtonsCanNeverBeChosen() {
        int[] refused = {KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP,
            KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_UNKNOWN};
        for (int code : refused) {
            assertNotNull("refused: " + code, RemoteButton.refusal(code));
            RemoteButton.key(app, code);
            assertEquals("never saved: " + code, KeyEvent.KEYCODE_UNKNOWN, RemoteButton.key(app));
            assertEquals("and never consumed, even if it were somehow saved", RemoteButton.Press.PASS, RemoteButton.press(true, true, code, code, false, false));
        }
        for (int code : new int[]{KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_BOOKMARK})
            assertNull("allowed: " + code, RemoteButton.refusal(code));
        assertEquals("TV", RemoteButton.name(KeyEvent.KEYCODE_TV));
        assertEquals("Red", RemoteButton.name(KeyEvent.KEYCODE_PROG_RED));
        assertEquals("Up can't be used: you need it to get around Unified TV and the TV. Press another button, or Back to stop.", RemoteButton.refused(KeyEvent.KEYCODE_DPAD_UP));
        assertTrue(RemoteButton.refused(KeyEvent.KEYCODE_VOLUME_UP).startsWith("Volume up can't be used: the TV keeps it for itself"));
        assertTrue(RemoteButton.refused(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE).contains("players use it"));
    }

    @Test public void onlyTheChosenKeyIsConsumed() {
        int tv = KeyEvent.KEYCODE_TV;
        assertEquals(RemoteButton.Press.HOME, RemoteButton.press(true, true, tv, tv, false, false));
        assertEquals("any other key passes on", RemoteButton.Press.PASS, RemoteButton.press(true, true, tv, KeyEvent.KEYCODE_PROG_RED, false, false));
        assertEquals("off: nothing", RemoteButton.Press.PASS, RemoteButton.press(true, false, tv, tv, false, false));
        assertEquals("no button chosen: nothing", RemoteButton.Press.PASS, RemoteButton.press(true, true, KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_UNKNOWN, false, false));
        assertEquals("the preview: nothing", RemoteButton.Press.PASS, RemoteButton.press(false, true, tv, tv, false, false));
        assertEquals("a film playing in Unified TV's player is never closed", RemoteButton.Press.STAY, RemoteButton.press(true, true, tv, tv, false, true));
        assertEquals("the alarm screen keeps priority", RemoteButton.Press.STAY, RemoteButton.press(true, true, tv, tv, true, false));
        assertEquals(RemoteButton.State.OFF, RemoteButton.state(false, KeyEvent.KEYCODE_UNKNOWN, false));
        assertEquals(RemoteButton.State.NO_BUTTON, RemoteButton.state(true, KeyEvent.KEYCODE_UNKNOWN, true));
        assertEquals(RemoteButton.State.NEEDS_ANDROID, RemoteButton.state(true, tv, false));
        assertEquals(RemoteButton.State.ON, RemoteButton.state(true, tv, true));
        assertEquals(RemoteButton.State.OFF, RemoteButton.state(false, tv, true));
    }

    @Test public void aWakeOpensUnifiedTvUnlessSomethingIsInTheWay() {
        TurnOn.Now n = new TurnOn.Now(); n.launcherApp = true;
        assertEquals("no OD-23 switch or \"Display over other apps\" needed", TurnOn.Skip.NONE, RemoteButton.wake(n, false));
        n.playing = true;
        assertEquals("something else playing: stays, as OD-23", TurnOn.Skip.PLAYING, RemoteButton.wake(n, false));
        assertEquals("unless the household just pressed the button", TurnOn.Skip.NONE, RemoteButton.wake(n, true));
        n.playing = false; n.liveTv = true;
        assertEquals(TurnOn.Skip.LIVE_TV, RemoteButton.wake(n, false));
        assertEquals(TurnOn.Skip.NONE, RemoteButton.wake(n, true));
        n.liveTv = false; n.alarm = true;
        assertEquals("the alarm screen always wins", TurnOn.Skip.ALARM, RemoteButton.wake(n, true));
        n.alarm = false; n.showing = true;
        assertEquals(TurnOn.Skip.ALREADY_SHOWING, RemoteButton.wake(n, true));
        n.showing = false; n.homeIsOurs = true;
        assertEquals(TurnOn.Skip.HOME_IS_OURS, RemoteButton.wake(n, false));
        n.homeIsOurs = false; n.launcherApp = false;
        assertEquals(TurnOn.Skip.NOT_LAUNCHER, RemoteButton.wake(n, true));
    }

    // --- the service's declaration
    @Test public void theServiceAsksForKeysAndNothingElse() throws Exception {
        File xml = new File("launcher/res/xml/remote_button.xml");
        assertTrue("run from preview/app: " + xml.getAbsolutePath(), xml.isFile());
        String ns = "http://schemas.android.com/apk/res/android";
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        Element s = f.newDocumentBuilder().parse(xml).getDocumentElement();
        assertEquals("accessibility-service", s.getLocalName());
        assertEquals("flagRequestFilterKeyEvents", s.getAttributeNS(ns, "accessibilityFlags"));
        assertEquals("true", s.getAttributeNS(ns, "canRequestFilterKeyEvents"));
        assertEquals("no window content", "false", s.getAttributeNS(ns, "canRetrieveWindowContent"));
        assertFalse("no event types at all", s.hasAttributeNS(ns, "accessibilityEventTypes"));
        for (String never : new String[]{"canPerformGestures", "canRequestTouchExplorationMode", "canTakeScreenshot", "canRequestFingerprintGestures", "packageNames"})
            assertFalse(never, s.hasAttributeNS(ns, never));
        assertEquals("@string/remote_button_description", s.getAttributeNS(ns, "description"));

        String shared = new String(java.nio.file.Files.readAllBytes(new File("AndroidManifest.xml").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse("the preview app never declares it", shared.contains("ButtonService"));
        String flavour = new String(java.nio.file.Files.readAllBytes(new File("launcher/AndroidManifest.xml").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        int at = flavour.indexOf("io.unifiedtv.preview.ButtonService");
        assertTrue(at > 0);
        String decl = flavour.substring(flavour.lastIndexOf("<service", at), flavour.indexOf("</service>", at));
        assertTrue(decl.contains("android:permission=\"android.permission.BIND_ACCESSIBILITY_SERVICE\""));
        assertTrue(decl.contains("android:exported=\"true\""));
        assertTrue(decl.contains("android.accessibilityservice.AccessibilityService"));
        assertTrue(decl.contains("android:resource=\"@xml/remote_button\""));

        ComponentName c = new ComponentName(app, ButtonService.class);
        if (launcher()) {
            ServiceInfo info = app.getPackageManager().getServiceInfo(c, PackageManager.GET_META_DATA);
            assertEquals("only the system can bind it", "android.permission.BIND_ACCESSIBILITY_SERVICE", info.permission);
        } else {
            try { app.getPackageManager().getServiceInfo(c, 0); fail("the preview has no remote button service"); } catch (PackageManager.NameNotFoundException expected) { /* none */ }
        }
    }

    // --- the service
    @Test public void pressingTheChosenButtonBringsHomeForwardAndPassesEverythingElse() {
        Assume.assumeTrue("launcher app only", launcher());
        ButtonService s = service();
        chosen(KeyEvent.KEYCODE_TV, true);
        for (int other : new int[]{KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_HOME}) {
            assertFalse("passes on: " + other, s.onKeyEvent(down(other)));
            assertFalse("passes on: " + other, s.onKeyEvent(up(other)));
        }
        assertNull("nothing opened for other keys", next());
        assertTrue("the press is consumed", s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
        Intent home = next();
        assertNotNull(home);
        assertEquals(UnifiedPreviewActivity.class.getName(), home.getComponent().getClassName());
        assertTrue("lands on Home's main screen", home.getBooleanExtra(TurnOn.EXTRA, false));
        assertNotEquals(0, home.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
        assertNotEquals("the existing task, brought forward", 0, home.getFlags() & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        assertEquals("never CLEAR_TOP", 0, home.getFlags() & (Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue("and its release", s.onKeyEvent(up(KeyEvent.KEYCODE_TV)));
        assertNull("one press, one start", next());
        KeyEvent held = KeyEvent.changeTimeRepeat(down(KeyEvent.KEYCODE_TV), 0, 3);
        assertTrue(s.onKeyEvent(held));
        assertNull("holding it doesn't start Home again", next());

        // In Kids it still opens Unified TV (in the current profile).
        prefs().edit().putString(Profiles.PREF, Profiles.CHILD).commit();
        assertTrue(s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
        assertNotNull(next());
        s.onKeyEvent(up(KeyEvent.KEYCODE_TV));
    }

    @Test public void theKeyPathReadsNoPreferencesAndMakesNoBinderCalls() {
        Assume.assumeTrue("launcher app only", launcher());
        ButtonService s = service();
        chosen(KeyEvent.KEYCODE_TV, true);
        ButtonService.CONTEXT_READS.set(0);
        for (int i = 0; i < 50; i++) for (int other : new int[]{KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_PROG_RED}) {
            assertFalse(s.onKeyEvent(down(other))); assertFalse(s.onKeyEvent(up(other)));
        }
        assertTrue(s.onKeyEvent(down(KeyEvent.KEYCODE_TV))); assertTrue(s.onKeyEvent(up(KeyEvent.KEYCODE_TV)));
        assertEquals("no preferences, package manager or content resolver on the key path", 0, ButtonService.CONTEXT_READS.get());
        assertNotNull("the launch is posted, then runs", next());

        // A change of choice reaches the service by its listener, not by reading on each key.
        chosen(KeyEvent.KEYCODE_PROG_RED, true);
        ButtonService.CONTEXT_READS.set(0);
        assertFalse("the old button passes on now", s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
        assertTrue(s.onKeyEvent(down(KeyEvent.KEYCODE_PROG_RED))); s.onKeyEvent(up(KeyEvent.KEYCODE_PROG_RED));
        chosen(KeyEvent.KEYCODE_PROG_RED, false);
        ButtonService.CONTEXT_READS.set(0);
        assertFalse("switched off: passes on", s.onKeyEvent(down(KeyEvent.KEYCODE_PROG_RED)));
        // Choosing: the service reports to the page without reading anything either.
        int[] got = {KeyEvent.KEYCODE_UNKNOWN};
        RemoteButton.listen(code -> got[0] = code, android.os.SystemClock.uptimeMillis() + 10_000);
        assertEquals(0, ButtonService.CONTEXT_READS.get());
    }

    @Test public void anUnfinishedChoiceStopsByItselfAndTheServiceLetsKeysGo() {
        Assume.assumeTrue("launcher app only", launcher());
        ButtonService s = service();
        assertEquals("off: no keys asked for", Boolean.FALSE, ButtonService.lastFilter);
        // The service alone: a choice under way is looked at again when it runs out.
        RemoteButton.listen(code -> {}, android.os.SystemClock.uptimeMillis() + 1000);
        ButtonService.refilter(); idle();
        assertEquals(Boolean.TRUE, ButtonService.lastFilter);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));
        assertEquals("keys let go once the choice ran out", Boolean.FALSE, ButtonService.lastFilter);
        RemoteButton.listen(null, 0);

        // The page: after CHOOSE_MS it stops listening, says so, and the service stops taking keys.
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            TurnOnChoiceActivity a = c.get();
            press(a, RemoteButton.CHOOSE);
            assertTrue(a.button().listening());
            assertEquals(Boolean.TRUE, ButtonService.lastFilter);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RemoteButton.CHOOSE_MS + 1000));
            assertFalse(a.button().listening());
            assertNull(RemoteButton.chooser(android.os.SystemClock.uptimeMillis()));
            assertTrue(words(root(a)).contains(RemoteButton.TIMED_OUT));
            assertFalse(words(root(a)).contains(RemoteButton.PRESS));
            assertEquals(Boolean.FALSE, ButtonService.lastFilter);
            assertEquals("nothing changed", KeyEvent.KEYCODE_UNKNOWN, RemoteButton.key(app));
            assertFalse(s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
        }
    }

    @Test public void offNothingIsConsumed() {
        Assume.assumeTrue("launcher app only", launcher());
        ButtonService s = service();
        chosen(KeyEvent.KEYCODE_TV, false);
        assertFalse(s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
        assertFalse(s.onKeyEvent(up(KeyEvent.KEYCODE_TV)));
        prefs().edit().clear().putBoolean(RemoteButton.ON, true).commit();
        assertFalse("on, but no button chosen", s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
        assertNull(next());
        assertFalse("nothing to ask Android for", RemoteButton.wantsKeys(app, android.os.SystemClock.uptimeMillis()));
        chosen(KeyEvent.KEYCODE_TV, true);
        assertTrue(RemoteButton.wantsKeys(app, android.os.SystemClock.uptimeMillis()));
    }

    @Test public void thePreviewHasNoneOfIt() {
        Assume.assumeFalse("preview app only", launcher());
        chosen(KeyEvent.KEYCODE_TV, true);
        ButtonService s = service();
        assertFalse("never consumes a key", s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
        assertNull(next());
        assertFalse(RemoteButton.offered(app));
        assertFalse(RemoteButton.watchesWake(app));
        assertNull("no page for it", app.getPackageManager().resolveActivity(new Intent(app, TurnOnChoiceActivity.class), 0));
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            idle(); press(c.get(), "Appearance & Home");
            assertNull(starting(root(c.get()), RemoteButton.TITLE));
        }
    }

    @Test public void aFilmPlayingInUnifiedTvIsNeverClosed() {
        Assume.assumeTrue("launcher app only", launcher());
        ButtonService s = service();
        chosen(KeyEvent.KEYCODE_TV, true);
        PlayerActivity player = Robolectric.buildActivity(PlayerActivity.class).get();
        Comeback.SCREENS.onActivityResumed(player);
        try {
            assertTrue("consumed, so the TV doesn't switch to its own Live TV", s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
            assertTrue(s.onKeyEvent(up(KeyEvent.KEYCODE_TV)));
            assertNull("Home isn't started over the film", next());
        } finally { Comeback.SCREENS.onActivityPaused(player); }
        assertTrue(s.onKeyEvent(down(KeyEvent.KEYCODE_TV)));
        assertNotNull("once the player isn't in front, Home again", next());
    }

    @Test public void aScreenOnAfterTheButtonOpensUnifiedTv() {
        Assume.assumeTrue("launcher app only", launcher());
        ButtonService s = service();
        chosen(KeyEvent.KEYCODE_TV, true);
        assertTrue(RemoteButton.watchesWake(app));
        app.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF)); idle();
        // In standby the key may never reach an app; Android wakes the TV on it.
        app.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TurnOn.SETTLE_MS + 200));
        assertEquals(TurnOn.Skip.NONE, ButtonService.lastWake);
        Intent home = next();
        assertNotNull("Unified TV came forward", home);
        assertEquals(UnifiedPreviewActivity.class.getName(), home.getComponent().getClassName());
        assertTrue(home.getBooleanExtra(TurnOn.EXTRA, false));
        app.sendBroadcast(new Intent(Intent.ACTION_USER_PRESENT));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TurnOn.SETTLE_MS + 200));
        assertNull("once per wake", next());

        // The key seen as the TV wakes: it starts Home itself; the screen-on then finds nothing more to do twice over.
        app.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF)); idle();
        assertTrue(s.onKeyEvent(down(KeyEvent.KEYCODE_TV))); s.onKeyEvent(up(KeyEvent.KEYCODE_TV));
        assertNotNull(next());

        // Off: a wake opens nothing.
        chosen(KeyEvent.KEYCODE_TV, false); ButtonService.lastWake = null; drain();
        app.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF)); idle();
        app.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TurnOn.SETTLE_MS + 200));
        assertNull(ButtonService.lastWake); assertNull(next());

        // OD-23's own watcher on: it handles the wake, so the button's service stands aside (never two starts).
        chosen(KeyEvent.KEYCODE_TV, true); TurnOn.enabled(app, true); ShadowSettings.setCanDrawOverlays(true);
        assertTrue(TurnOn.active(app));
        assertFalse(RemoteButton.watchesWake(app));
    }

    // --- the page
    @Test public void choosingByPressingRefusesAndRecords() {
        Assume.assumeTrue("launcher app only", launcher());
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            TurnOnChoiceActivity a = c.get();
            assertNotNull("the TV button is suggested", starting(root(a), RemoteButton.SUGGEST));
            assertEquals(RemoteButton.CHOOSE + ", " + RemoteButton.NOT_CHOSEN, String.valueOf(starting(root(a), RemoteButton.CHOOSE).getContentDescription()));
            press(a, RemoteButton.CHOOSE);
            assertTrue(a.button().listening());
            assertTrue(words(root(a)).contains(RemoteButton.PRESS));
            for (int refused : new int[]{KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE}) {
                assertTrue(a.dispatchKeyEvent(down(refused))); a.dispatchKeyEvent(up(refused)); idle();
                assertTrue("still listening after " + refused, a.button().listening());
                assertTrue(words(root(a)).contains(RemoteButton.refused(refused)));
                assertEquals(KeyEvent.KEYCODE_UNKNOWN, RemoteButton.key(app));
            }
            assertTrue(a.dispatchKeyEvent(down(KeyEvent.KEYCODE_PROG_RED))); idle();
            assertFalse(a.button().listening());
            assertEquals(KeyEvent.KEYCODE_PROG_RED, RemoteButton.key(app));
            assertTrue(words(root(a)).contains("Chosen: Red."));
            assertFalse("choosing doesn't switch it on", RemoteButton.on(app));

            // Back stops without changing anything.
            press(a, RemoteButton.CHOOSE);
            assertTrue(a.dispatchKeyEvent(down(KeyEvent.KEYCODE_BACK))); idle();
            assertFalse(a.button().listening());
            assertEquals(KeyEvent.KEYCODE_PROG_RED, RemoteButton.key(app));
            assertFalse("the page is still open", a.isFinishing());

            // A button the TV keeps from apps (the TV button) arrives through the service while the page listens.
            ButtonService s = service();
            press(a, RemoteButton.CHOOSE);
            assertSame(a, Comeback.front());
            assertFalse("Back still reaches the page", s.onKeyEvent(down(KeyEvent.KEYCODE_DPAD_DOWN)));
            assertTrue(s.onKeyEvent(down(KeyEvent.KEYCODE_TV))); idle();
            assertTrue("its release too", s.onKeyEvent(up(KeyEvent.KEYCODE_TV)));
            assertEquals(KeyEvent.KEYCODE_TV, RemoteButton.key(app));
            assertFalse(a.button().listening());
            assertNull("choosing never opened Home", next());
            assertNull("the TV button is chosen: no suggestion", starting(root(a), RemoteButton.SUGGEST));

            // Leaving the page ends any choice, so the service stops taking keys for it.
            press(a, RemoteButton.CHOOSE);
            c.pause(); idle();
            assertNull(RemoteButton.chooser(android.os.SystemClock.uptimeMillis()));
            c.resume(); idle();
        }
    }

    @Test public void switchingOnGuidesToAndroidsSwitchAndShowsSwitchedOn() {
        Assume.assumeTrue("launcher app only", launcher());
        accessibilityScreen();
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            TurnOnChoiceActivity a = c.get();
            press(a, RemoteButton.SWITCH);
            assertFalse("a button first", RemoteButton.on(app));
            assertTrue(words(root(a)).contains(RemoteButton.CHOOSE_FIRST));
            press(a, RemoteButton.SUGGEST);
            assertEquals(KeyEvent.KEYCODE_TV, RemoteButton.key(app));
            press(a, RemoteButton.SWITCH);
            assertTrue(RemoteButton.on(app));
            assertEquals(RemoteButton.SWITCH + ", " + RemoteButton.State.NEEDS_ANDROID.words, String.valueOf(starting(root(a), RemoteButton.SWITCH).getContentDescription()));
            assertTrue(words(root(a)).contains(RemoteButton.WAKE_WORDS));
            assertTrue("honest about every wake", words(root(a)).contains(RemoteButton.ANY_WAKE));
            assertTrue(words(root(a)).contains(RemoteButton.WARNING_MEANS));
            AlertDialog ask = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("the guided step opens at once", RemoteButton.SERVICE_LABEL, String.valueOf(Shadows.shadowOf(ask).getTitle()));
            String msg = String.valueOf(Shadows.shadowOf(ask).getMessage());
            assertTrue(msg.contains("Device Preferences > Accessibility > " + RemoteButton.SERVICE_LABEL));
            assertTrue(msg.contains(RemoteButton.WARNING_MEANS));
            ask.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
            assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, Shadows.shadowOf(a).getNextStartedActivity().getAction());
            serviceSwitchedOn(true);
            c.pause().resume(); idle();
            assertTrue(words(root(a)).contains(RemoteButton.SWITCHED_ON + " " + RemoteButton.SERVICE_LABEL + " is on."));
            assertEquals(RemoteButton.ANDROID_ROW + ", " + RemoteButton.SWITCHED_ON, String.valueOf(starting(root(a), RemoteButton.ANDROID_ROW).getContentDescription()));
            assertEquals(RemoteButton.State.ON, RemoteButton.state(app));
            press(a, RemoteButton.SWITCH);
            assertFalse("and off again", RemoteButton.on(app));
        }
    }

    @Test public void withoutTheTvsScreenThePcLineAddsToTheList() {
        Assume.assumeTrue("launcher app only", launcher());
        chosen(KeyEvent.KEYCODE_TV, false);
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            press(c.get(), RemoteButton.ANDROID_ROW);
            String msg = String.valueOf(Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getMessage());
            assertTrue(msg.contains(RemoteButton.NO_SCREEN));
            assertTrue(msg.contains("io.unifiedtv.launcher/io.unifiedtv.preview.ButtonService"));
            assertTrue("appends, never replaces (the Kids guard stays on)", msg.contains("$cur:io.unifiedtv.launcher/io.unifiedtv.preview.ButtonService"));
            assertTrue(msg.contains("settings put secure accessibility_enabled 1"));
        }
    }

    @Test public void changingItNeedsTheAdultPin() throws Exception {
        Assume.assumeTrue("launcher app only", launcher());
        prefs().edit().putString("pin", Pins.record(PIN)).commit();
        chosen(KeyEvent.KEYCODE_TV, false);
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            TurnOnChoiceActivity a = c.get();
            press(a, RemoteButton.SWITCH);
            AlertDialog pin = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("Adult access", String.valueOf(Shadows.shadowOf(pin).getTitle()));
            assertFalse("nothing changes before the PIN", RemoteButton.on(app));
            Pins.type(pin, "0000"); idle();
            assertFalse(RemoteButton.on(app));
            press(a, RemoteButton.SWITCH);
            Pins.type(ShadowAlertDialog.getLatestAlertDialog(), PIN); idle();
            assertTrue(RemoteButton.on(app));

            press(a, RemoteButton.CHOOSE);
            assertFalse("choosing waits for the PIN", a.button().listening());
            Pins.type(ShadowAlertDialog.getLatestAlertDialog(), PIN); idle();
            assertTrue(a.button().listening());
            a.dispatchKeyEvent(down(KeyEvent.KEYCODE_BACK)); idle();
        }
        // In Kids, without an adult, nothing changes at all (the button still works: see the service test).
        prefs().edit().remove("pin").putString(Profiles.PREF, Profiles.CHILD).commit();
        try (ActivityController<TurnOnChoiceActivity> c = page()) {
            press(c.get(), RemoteButton.SWITCH);
            assertTrue(RemoteButton.on(app));
            assertTrue(words(root(c.get())).contains(RemoteButton.KIDS));
        }
    }

    @Test public void appearanceAndHomePointsToIt() {
        Assume.assumeTrue("launcher app only", launcher());
        try (ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a = c.get(); idle();
            press(a, "Appearance & Home");
            Button row = starting(root(a), RemoteButton.TITLE);
            assertNotNull(row);
            assertEquals(RemoteButton.TITLE + ", " + RemoteButton.State.OFF.words, String.valueOf(row.getContentDescription()));
            press(a, RemoteButton.TITLE);
            Intent open = Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(TurnOnChoiceActivity.class.getName(), open.getComponent().getClassName());
            assertTrue(open.getBooleanExtra(TurnOnChoiceActivity.BUTTON, false));
        }
    }
}
