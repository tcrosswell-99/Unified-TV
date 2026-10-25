package io.unifiedtv.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowSettings;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/**
 * OD-23 "Open Unified TV when the TV turns on": launcher app only, off by default, behind the adult PIN, said honestly
 * (On, Needs permission, Off); the watcher runs only while it is on and allowed; boot and "screen on again" bring Unified
 * TV's Home to the front once per wake, never on a restart, and never over playback, a call, Live TV or the alarm screen.
 * Both apps run this class (CI's testPreviewDebugUnitTest and testLauncherDebugUnitTest): launcher-only journeys are
 * skipped in the preview, and the preview's own journey checks it never shows or starts anything.
 * Module 03: nothing here involves keys; the class under test has no key handling to test.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class TurnOnTest {
    private static final String PIN="2468";
    private Context app;
    private SharedPreferences prefs(){return app.getSharedPreferences(HouseholdSettings.PREFS,0);}
    private static void idle(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private boolean launcher(){return DefaultHome.available(app);}

    @Before public void clean(){
        app=RuntimeEnvironment.getApplication();prefs().edit().clear().commit();ShadowAlertDialog.reset();ShadowSettings.setCanDrawOverlays(false);
        HouseholdService.alarmOnScreen=false;LiveTvGuard.running=false;TurnOnService.lastSkip=null;
    }
    @After public void reset(){ShadowSettings.setCanDrawOverlays(false);HouseholdService.alarmOnScreen=false;LiveTvGuard.running=false;Capabilities.forTests=null;}

    // --- pure
    @Test public void theSwitchSaysItsStateHonestly(){
        assertEquals(TurnOn.State.OFF,TurnOn.state(false,false));
        assertEquals("off whatever the permission",TurnOn.State.OFF,TurnOn.state(false,true));
        assertEquals(TurnOn.State.NEEDS_PERMISSION,TurnOn.state(true,false));
        assertEquals(TurnOn.State.ON,TurnOn.state(true,true));
        assertEquals("On",TurnOn.State.ON.words);assertEquals("Needs permission",TurnOn.State.NEEDS_PERMISSION.words);assertEquals("Off",TurnOn.State.OFF.words);
        assertEquals("When the TV starts or wakes from standby, Unified TV comes to the front. The Home button still opens the TV's own Home screen.",TurnOn.EXPLAIN);
        assertFalse("off by default",TurnOn.enabled(app));
    }
    @Test public void offeredOnlyInTheLauncherAndNotOnceTheHomeButtonOpensUnifiedTv(){
        assertFalse("never in the preview",TurnOn.offered(false,DefaultHome.State.NOT));
        assertTrue(TurnOn.offered(true,DefaultHome.State.NOT));
        assertTrue("the TV keeps its own Home button: exactly the case it is for",TurnOn.offered(true,DefaultHome.State.KEPT));
        assertFalse("not needed while Home is Unified TV",TurnOn.offered(true,DefaultHome.State.OURS));
        assertEquals("this build's own answer",launcher(),TurnOn.offered(app));
    }
    private static TurnOn.Now ready(){TurnOn.Now n=new TurnOn.Now();n.launcherApp=true;n.switchedOn=true;n.permitted=true;return n;}
    @Test public void itStaysOutOfTheWay(){
        assertEquals(TurnOn.Skip.NONE,TurnOn.decide(ready()));
        TurnOn.Now n=ready();n.launcherApp=false;assertEquals(TurnOn.Skip.NOT_LAUNCHER,TurnOn.decide(n));
        n=ready();n.switchedOn=false;assertEquals(TurnOn.Skip.OFF,TurnOn.decide(n));
        n=ready();n.homeIsOurs=true;assertEquals(TurnOn.Skip.HOME_IS_OURS,TurnOn.decide(n));
        n=ready();n.permitted=false;assertEquals(TurnOn.Skip.NO_PERMISSION,TurnOn.decide(n));
        n=ready();n.alarm=true;assertEquals(TurnOn.Skip.ALARM,TurnOn.decide(n));
        n=ready();n.call=true;assertEquals(TurnOn.Skip.CALL,TurnOn.decide(n));
        n=ready();n.liveTv=true;assertEquals(TurnOn.Skip.LIVE_TV,TurnOn.decide(n));
        n=ready();n.playing=true;assertEquals(TurnOn.Skip.PLAYING,TurnOn.decide(n));
        n=ready();n.showing=true;assertEquals(TurnOn.Skip.ALREADY_SHOWING,TurnOn.decide(n));
        n=ready();n.alarm=true;n.playing=true;n.call=true;assertEquals("the alarm screen comes first",TurnOn.Skip.ALARM,TurnOn.decide(n));
    }
    @Test public void onceAWakeAndNeverOnARestart(){
        TurnOn.Wake w=new TurnOn.Wake();
        assertFalse("a new watcher (the switch, Home opening, Android restarting it) never acts by itself",w.wake(1_000));
        w.off();assertTrue("the screen came back on",w.wake(100_000));
        assertFalse("'user present' in the same wake",w.wake(100_100));
        w.off();assertFalse("flickering off and on within the debounce",w.wake(100_000+TurnOn.DEBOUNCE_MS-1));
        w.off();assertTrue("a later wake",w.wake(100_000+TurnOn.DEBOUNCE_MS+5));
        TurnOn.Wake b=new TurnOn.Wake();b.boot();assertTrue("the TV started",b.wake(5_000));assertFalse("screen on during the same start",b.wake(6_000));
    }
    @Test public void whatThisTvCanDoSaysItHonestly(){
        Capabilities.Facts f=new Capabilities.Facts();f.sdk=30;f.release="11";
        Capabilities.Capability c=Capabilities.evaluate(f,Capabilities.TURN_ON);
        assertEquals("not available in the preview",Capabilities.State.UNSUPPORTED_ON_DEVICE,c.state);assertTrue(c.words.contains("preview"));
        f.launcherApp=true;c=Capabilities.evaluate(f,Capabilities.TURN_ON);
        assertEquals(Capabilities.State.PERMISSION_REQUIRED,c.state);assertTrue(c.words.contains("Display over other apps"));assertEquals(Capabilities.GO_APPEARANCE,c.go);
        f.overlays=true;c=Capabilities.evaluate(f,Capabilities.TURN_ON);assertEquals(Capabilities.State.AVAILABLE,c.state);assertTrue(c.words.contains("Switch it on"));
        f.turnOn=true;c=Capabilities.evaluate(f,Capabilities.TURN_ON);assertTrue(c.words.startsWith("On."));assertTrue(c.words.contains("Home button still opens the TV's own"));
        f.isHome=true;f.overlays=false;c=Capabilities.evaluate(f,Capabilities.TURN_ON);assertEquals(Capabilities.State.AVAILABLE,c.state);assertTrue(c.words.contains("Not needed"));
        assertEquals("this build's own answer",launcher()?Capabilities.State.PERMISSION_REQUIRED:Capabilities.State.UNSUPPORTED_ON_DEVICE,Capabilities.check(app,Capabilities.TURN_ON).state);
    }

    // --- the watcher (launcher app)
    private void onAndAllowed(){TurnOn.enabled(app,true);ShadowSettings.setCanDrawOverlays(true);}
    private ServiceController<TurnOnService> watcher(String action){
        return ServiceController.of(new TurnOnService(),new Intent(app,TurnOnService.class).setAction(action)).create().startCommand(0,1);
    }
    private void screen(String action){app.sendBroadcast(new Intent(action));idle(0);}
    private Intent nextActivity(){return Shadows.shadowOf((android.app.Application)app).getNextStartedActivity();}
    private void assertHomeCameForward(){
        Intent i=nextActivity();assertNotNull("Unified TV came to the front",i);
        assertEquals(UnifiedPreviewActivity.class.getName(),i.getComponent().getClassName());
        assertTrue(i.getBooleanExtra(TurnOn.EXTRA,false));assertTrue((i.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)!=0);
        assertNull("once",nextActivity());
    }
    @Test public void theWatcherStartsAndStopsWithTheSwitch(){
        Assume.assumeTrue("launcher app only",launcher());
        org.robolectric.shadows.ShadowApplication s=Shadows.shadowOf((android.app.Application)app);
        TurnOn.sync(app);assertNull("off by default: nothing runs",s.getNextStartedService());
        TurnOn.enabled(app,true);TurnOn.sync(app);assertNull("on without permission: nothing runs",s.getNextStartedService());
        ShadowSettings.setCanDrawOverlays(true);TurnOn.sync(app);
        Intent started=s.getNextStartedService();assertNotNull(started);assertEquals(TurnOnService.class.getName(),started.getComponent().getClassName());
        TurnOn.enabled(app,false);TurnOn.sync(app);
        Intent stopped=s.getNextStoppedService();assertNotNull("switched off: stopped",stopped);assertEquals(TurnOnService.class.getName(),stopped.getComponent().getClassName());
        ServiceController<TurnOnService> w=watcher(null);
        assertTrue("a watcher started while off stops itself",Shadows.shadowOf(w.get()).isStoppedBySelf());w.destroy();
    }
    @Test public void afterBootHomeComesForwardOnlyWhenOnAndAllowed(){
        Assume.assumeTrue("launcher app only",launcher());
        org.robolectric.shadows.ShadowApplication s=Shadows.shadowOf((android.app.Application)app);
        Intent boot=new Intent(Intent.ACTION_BOOT_COMPLETED);
        new TurnOnBoot().onReceive(app,boot);assertNull("off: the TV starts as it always did",s.getNextStartedService());
        TurnOn.enabled(app,true);new TurnOnBoot().onReceive(app,boot);assertNull("no permission: nothing",s.getNextStartedService());
        ShadowSettings.setCanDrawOverlays(true);new TurnOnBoot().onReceive(app,new Intent("io.example.NOT_BOOT"));assertNull("only Android's start counts",s.getNextStartedService());
        new TurnOnBoot().onReceive(app,boot);Intent started=s.getNextStartedService();
        assertNotNull(started);assertEquals(TurnOnService.BOOT,started.getAction());
        ServiceController<TurnOnService> w=watcher(TurnOnService.BOOT);
        idle(TurnOn.SETTLE_MS/2);assertNull("waits for the TV to settle",nextActivity());
        idle(TurnOn.SETTLE_MS);assertHomeCameForward();assertEquals(TurnOn.Skip.NONE,TurnOnService.lastSkip);
        screen(Intent.ACTION_SCREEN_ON);screen(Intent.ACTION_USER_PRESENT);idle(TurnOn.SETTLE_MS*2);assertNull("the same start is one wake",nextActivity());
        w.destroy();
    }
    @Test public void wakingFromStandbyBringsHomeForwardOnceAWake(){
        Assume.assumeTrue("launcher app only",launcher());
        onAndAllowed();ServiceController<TurnOnService> w=watcher(null);
        screen(Intent.ACTION_SCREEN_ON);idle(TurnOn.SETTLE_MS*2);assertNull("starting the watcher is not a wake",nextActivity());
        screen(Intent.ACTION_SCREEN_OFF);screen(Intent.ACTION_SCREEN_ON);screen(Intent.ACTION_USER_PRESENT);idle(TurnOn.SETTLE_MS*2);
        assertHomeCameForward();
        screen(Intent.ACTION_SCREEN_OFF);screen(Intent.ACTION_SCREEN_ON);idle(TurnOn.SETTLE_MS*2);assertNull("debounced: never a loop",nextActivity());
        idle(TurnOn.DEBOUNCE_MS);
        screen(Intent.ACTION_SCREEN_OFF);screen(Intent.ACTION_SCREEN_ON);idle(TurnOn.SETTLE_MS*2);assertHomeCameForward();
        // Off again before it looked (a quick standby): nothing.
        idle(TurnOn.DEBOUNCE_MS);screen(Intent.ACTION_SCREEN_OFF);screen(Intent.ACTION_SCREEN_ON);idle(TurnOn.SETTLE_MS/2);screen(Intent.ACTION_SCREEN_OFF);idle(TurnOn.SETTLE_MS*2);
        assertNull("the screen went off again before it looked",nextActivity());
        // Switched off while running: the next wake stops the watcher instead.
        TurnOn.enabled(app,false);idle(TurnOn.DEBOUNCE_MS);screen(Intent.ACTION_SCREEN_ON);idle(TurnOn.SETTLE_MS*2);
        assertNull(nextActivity());assertTrue(Shadows.shadowOf(w.get()).isStoppedBySelf());
        w.destroy();
    }
    private TurnOn.Skip wakeWith(Runnable situation){
        ServiceController<TurnOnService> w=watcher(null);situation.run();TurnOnService.lastSkip=null;
        screen(Intent.ACTION_SCREEN_OFF);screen(Intent.ACTION_SCREEN_ON);idle(TurnOn.SETTLE_MS*2);
        TurnOn.Skip why=TurnOnService.lastSkip;w.destroy();return why;
    }
    @Test public void neverOverPlaybackACallLiveTvOrTheAlarmScreen(){
        Assume.assumeTrue("launcher app only",launcher());
        onAndAllowed();AudioManager audio=app.getSystemService(AudioManager.class);
        assertEquals(TurnOn.Skip.PLAYING,wakeWith(()->Shadows.shadowOf(audio).setIsMusicActive(true)));Shadows.shadowOf(audio).setIsMusicActive(false);
        assertEquals(TurnOn.Skip.CALL,wakeWith(()->audio.setMode(AudioManager.MODE_IN_COMMUNICATION)));audio.setMode(AudioManager.MODE_NORMAL);
        assertEquals(TurnOn.Skip.LIVE_TV,wakeWith(()->LiveTvGuard.running=true));LiveTvGuard.running=false;
        assertEquals(TurnOn.Skip.ALARM,wakeWith(()->HouseholdService.alarmOnScreen=true));HouseholdService.alarmOnScreen=false;
        try(var shown=Robolectric.buildActivity(Activity.class).setup()){
            assertEquals("a Unified TV screen is already showing",TurnOn.Skip.ALREADY_SHOWING,wakeWith(()->{}));}
        assertNull("none of those opened anything",nextActivity());
        assertEquals("with nothing in the way it does",TurnOn.Skip.NONE,wakeWith(()->{}));assertHomeCameForward();
    }

    // --- Settings > Appearance & Home > Home button
    private static View root(SettingsActivity a){return a.findViewById(android.R.id.content);}
    private static Button starting(View v,String prefix){if(v instanceof Button&&v.isShown()&&((Button)v).getText().toString().startsWith(prefix))return (Button)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=starting(((ViewGroup)v).getChildAt(i),prefix);if(b!=null)return b;}return null;}
    private static boolean says(View v,String part){if(v instanceof TextView&&!(v instanceof Button)&&v.isShown()&&((TextView)v).getText().toString().contains(part))return true;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(says(((ViewGroup)v).getChildAt(i),part))return true;return false;}
    private static void press(Button b){b.requestFocus();b.performClick();idle(0);}
    private static SettingsActivity appearance(org.robolectric.android.controller.ActivityController<SettingsActivity> c){
        SettingsActivity a=c.get();Button row=starting(root(a),"Appearance & Home");assertNotNull(row);press(row);assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());return a;
    }
    private static Button row(SettingsActivity a){return starting(root(a),TurnOn.TITLE);}
    private static AlertDialog latest(){AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull("a dialog",d);return d;}

    @Test public void thePreviewNeverShowsOrStartsIt(){
        Assume.assumeFalse("the preview app",launcher());
        onAndAllowed();org.robolectric.shadows.ShadowApplication s=Shadows.shadowOf((android.app.Application)app);
        TurnOn.sync(app);new TurnOnBoot().onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNull("no watcher in the preview",s.getNextStartedService());
        assertEquals(TurnOn.Skip.NOT_LAUNCHER,TurnOn.decide(TurnOn.read(app)));
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=appearance(c);
            assertNull("no switch in the preview",row(a));assertFalse(says(root(a),TurnOn.EXPLAIN));
            assertTrue(says(root(a),"This is the Unified TV preview"));}
    }
    @Test public void theSwitchNeedsTheAdultPinAndSaysWhatItNeeds()throws Exception{
        Assume.assumeTrue("launcher app only",launcher());
        prefs().edit().putString("pin",Pins.record(PIN)).commit();
        org.robolectric.shadows.ShadowApplication s=Shadows.shadowOf((android.app.Application)app);
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=appearance(c);
            Button r=row(a);assertNotNull("the switch is in Home button",r);assertEquals(TurnOn.TITLE+": Off",r.getText().toString());
            assertTrue("the plain explanation",says(root(a),TurnOn.EXPLAIN));
            press(r);AlertDialog pin=latest();assertEquals("Adult access",String.valueOf(Shadows.shadowOf(pin).getTitle()));
            Pins.type(pin,"0000");assertFalse("a wrong PIN changes nothing",TurnOn.enabled(app));assertEquals(TurnOn.TITLE+": Off",row(a).getText().toString());
            press(row(a));Pins.type(latest(),PIN);
            assertTrue(TurnOn.enabled(app));assertEquals("said honestly",TurnOn.TITLE+": Needs permission",row(a).getText().toString());
            assertNull("nothing runs without the permission",s.getNextStartedService());
            // This (Robolectric) TV has no "Display over other apps" screen: the one-time computer step, with the real package.
            String adb="adb shell appops set "+app.getPackageName()+" SYSTEM_ALERT_WINDOW allow";
            assertEquals(adb,TurnOn.adb(app));assertTrue(says(root(a),adb));
            AlertDialog how=latest();assertEquals(TurnOn.TITLE,String.valueOf(Shadows.shadowOf(how).getTitle()));assertTrue(String.valueOf(Shadows.shadowOf(how).getMessage()).contains(adb));
            how.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle(0);
            // Allowed from the computer: Home's next start (or Settings) starts the watcher, and the row says On.
            ShadowSettings.setCanDrawOverlays(true);TurnOn.sync(app);assertNotNull(s.getNextStartedService());
            a.refresh();idle(0);assertEquals(TurnOn.TITLE+": On",row(a).getText().toString());assertFalse(says(root(a),adb));
            press(row(a));Pins.type(latest(),PIN);
            assertFalse("switched off",TurnOn.enabled(app));assertEquals(TurnOn.TITLE+": Off",row(a).getText().toString());
            Intent stopped=s.getNextStoppedService();assertNotNull("the watcher stops",stopped);assertEquals(TurnOnService.class.getName(),stopped.getComponent().getClassName());
        }
    }
    @Test public void whenTheTvHasTheScreenItOpensIt(){
        Assume.assumeTrue("launcher app only",launcher());
        Intent screen=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+app.getPackageName()));
        ResolveInfo ri=new ResolveInfo();ri.activityInfo=new ActivityInfo();ri.activityInfo.packageName="com.android.tv.settings";ri.activityInfo.name="com.android.tv.settings.Overlay";
        Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(screen,ri);
        assertNotNull(TurnOn.permissionScreen(app));
        TurnOn.enabled(app,true);
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=appearance(c);
            assertEquals(TurnOn.TITLE+": Needs permission",row(a).getText().toString());assertTrue(says(root(a),"Press the row to open that setting"));
            press(row(a));
            AlertDialog how=latest();assertFalse("no computer step when the TV has the screen",String.valueOf(Shadows.shadowOf(how).getMessage()).contains("adb"));
            how.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle(0);
            Intent opened=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(opened);assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,opened.getAction());
            // Allowed there, and back: the row says On.
            ShadowSettings.setCanDrawOverlays(true);c.pause().resume();idle(0);assertEquals(TurnOn.TITLE+": On",row(a).getText().toString());
            // No adult PIN set on this TV: an adult switches it off with one press.
            press(row(a));assertFalse(TurnOn.enabled(app));assertEquals(TurnOn.TITLE+": Off",row(a).getText().toString());
        }
    }
    @Test public void hiddenOnceTheHomeButtonOpensUnifiedTv(){
        Assume.assumeTrue("launcher app only",launcher());
        ResolveInfo ri=new ResolveInfo();ri.activityInfo=new ActivityInfo();ri.activityInfo.packageName=app.getPackageName();ri.activityInfo.name="io.unifiedtv.preview.HomeEntry";
        Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(DefaultHome.homeIntent(),ri);
        assertTrue(DefaultHome.isDefault(app));assertFalse(TurnOn.offered(app));
        TurnOn.enabled(app,true);ShadowSettings.setCanDrawOverlays(true);assertFalse("nothing runs while Home is Unified TV",TurnOn.active(app));
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=appearance(c);
            assertTrue(says(root(a),"The Home button opens Unified TV."));assertNull("not offered",row(a));}
    }
}
