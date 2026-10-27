package io.unifiedtv.preview;

import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowSettings;
import java.lang.reflect.Method;
import java.time.Duration;
import static org.junit.Assert.*;

/**
 * OD-26 (owner, 27 October 2026): Live TV opens when NordVPN is connected, on every route (dock, Apps, voice, the sheet's
 * Retry), through one rule (LiveTvProtection) and one gate (Launch.open). While Live TV that Unified TV opened is on, the
 * safety net (LiveTvGuard) covers it with a full-screen notice if NordVPN drops, or posts a notification without "Display
 * over other apps", and stops when Live TV leaves. Kids rules are unchanged. Both flavours run this class.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class LiveTvSafetyNetTest {
    /** A Live TV app the protected-app rule recognises by its name. */
    private static final String LIVE="tv.example.iptvsmarters",MAIN=LIVE+".MainActivity";
    private Context app(){return RuntimeEnvironment.getApplication();}
    private SharedPreferences prefs(){return app().getSharedPreferences("unified-tv-preview",0);}
    private static void idle(long ms){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private static LiveTvProtection.Facts facts(VpnGate.State vpn){
        LiveTvProtection.Facts f=new LiveTvProtection.Facts();f.vpn=vpn;f.nordInstalled=true;f.onlyNordCanVpn=true;return f;
    }
    private static void nordConnected(){LiveTvProtection.forTests=facts(VpnGate.State.CONNECTED);}
    private static void nordDropped(){LiveTvProtection.forTests=facts(VpnGate.State.DISCONNECTED);}

    @Before public void clean(){
        prefs().edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();
        ShadowPackageManager pm=Shadows.shadowOf(app().getPackageManager());
        ComponentName c=new ComponentName(LIVE,MAIN);ActivityInfo info=pm.addActivityIfNotPresent(c);info.nonLocalizedLabel="IPTV Smarters";
        IntentFilter f=new IntentFilter(Intent.ACTION_MAIN);f.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        try{pm.addIntentFilterForActivity(c,f);}catch(Exception e){throw new AssertionError(e);}
        nordDropped();ShadowSettings.setCanDrawOverlays(false);LiveTvGuard.lastAction=null;ShadowDialog.reset();
        while(Shadows.shadowOf((android.app.Application)app()).getNextStartedService()!=null){}
        while(Shadows.shadowOf((android.app.Application)app()).getNextStoppedService()!=null){}
    }
    @After public void reset(){LiveTvProtection.forTests=null;ShadowSettings.setCanDrawOverlays(false);LiveTvGuard.running=false;}

    private static String words(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append(' ');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(words(((ViewGroup)v).getChildAt(i)));return b.toString();}
    private static Button find(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),text);if(b!=null)return b;}return null;}
    private static String sheet(){Dialog d=ShadowDialog.getLatestDialog();return d==null?"":words(d.getWindow().getDecorView());}
    private Intent nextService(){return Shadows.shadowOf((android.app.Application)app()).getNextStartedService();}
    private static boolean opened(Intent i){return i!=null&&i.getComponent()!=null&&MAIN.equals(i.getComponent().getClassName());}

    // --- the one gate
    @Test public void theGateOpensLiveTvOnlyWhileNordVpnIsConnected(){
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            Activity a=c.get();Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(LIVE,LiveTvProtection.livePackage(a));
            assertEquals("the sheet explains",  "",Launch.open(a,LIVE,"Live TV"));
            assertFalse(opened(Shadows.shadowOf(a).getNextStartedActivity()));assertNull("no watch without Live TV",nextService());
            assertTrue(sheet(),sheet().contains("Live TV is locked. Connect NordVPN first."));assertNotNull(find(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Connect"));
            // Retry on the sheet opens Live TV once NordVPN is connected: the refused route runs again through the gate.
            nordConnected();find(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Retry").performClick();idle(10);
            assertTrue("Retry opened Live TV",opened(Shadows.shadowOf(a).getNextStartedActivity()));
            Intent watch=nextService();assertNotNull(watch);assertEquals(LiveTvGuard.class.getName(),watch.getComponent().getClassName());assertEquals(LIVE,watch.getStringExtra(LiveTvGuard.LIVE));
            assertNull("connected: opens at once",Launch.open(a,LIVE,"Live TV"));assertTrue(opened(Shadows.shadowOf(a).getNextStartedActivity()));
        }
    }
    @Test public void anotherVpnAppWithoutNotificationAccessSaysWhyAndOffersAccess(){
        LiveTvProtection.Facts f=facts(VpnGate.State.CONNECTED);f.onlyNordCanVpn=false;LiveTvProtection.forTests=f;
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            Activity a=c.get();Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals("",Launch.open(a,LIVE,"Live TV"));assertFalse(opened(Shadows.shadowOf(a).getNextStartedActivity()));
            assertTrue(sheet(),sheet().contains("Can't tell which VPN is on. Allow notification access so Unified TV can see NordVPN"));
            assertNotNull(find(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Allow access"));
        }
    }
    @Test public void theDockTileUsesTheRule()throws Exception{
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            UnifiedPreviewActivity a=c.get();Shadows.shadowOf(a).getNextStartedActivity();
            View home=RemoteJourneyTest.homeIn(a);Method key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
            java.lang.reflect.Field dock=home.getClass().getDeclaredField("dock");dock.setAccessible(true);
            java.lang.reflect.Field index=home.getClass().getDeclaredField("index");index.setAccessible(true);
            int live=-1;java.util.List<?> items=(java.util.List<?>)dock.get(home);
            for(int i=0;i<items.size();i++){java.lang.reflect.Field id=items.get(i).getClass().getDeclaredField("id");id.setAccessible(true);if("live".equals(id.get(items.get(i))))live=i;}
            assertTrue("the Live TV tile is on the dock",live>=0);
            while((Integer)index.get(home)<live){key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));}
            Runnable ok=()->{try{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));}catch(Exception e){throw new AssertionError(e);}};
            ok.run();assertFalse("locked from the dock",opened(Shadows.shadowOf(a).getNextStartedActivity()));assertTrue(sheet().contains("Connect NordVPN first"));
            nordConnected();ok.run();assertTrue("open from the dock",opened(Shadows.shadowOf(a).getNextStartedActivity()));
            assertNotNull("the safety net starts with it",nextService());
        }
    }
    @Test public void appsUsesTheRule()throws Exception{
        try(ActivityController<AppsActivity> c=Robolectric.buildActivity(AppsActivity.class).setup()){
            AppsActivity a=c.get();Shadows.shadowOf(a).getNextStartedActivity();
            Method shortcut=AppsActivity.class.getDeclaredMethod("shortcut",String.class);shortcut.setAccessible(true);
            shortcut.invoke(a,"live");assertFalse("locked from Apps",opened(Shadows.shadowOf(a).getNextStartedActivity()));
            nordConnected();shortcut.invoke(a,"live");assertTrue("open from Apps",opened(Shadows.shadowOf(a).getNextStartedActivity()));
        }
    }
    @Test public void voiceUsesTheRule(){
        try(ActivityController<VoiceActivity> c=Robolectric.buildActivity(VoiceActivity.class).setup()){
            VoiceActivity v=c.get();EditText field=field(v.findViewById(android.R.id.content));
            field.setText("Open Live TV");v.ask();
            assertTrue(v.answerText(),v.answerText().contains("Live TV is locked. Connect NordVPN first."));assertFalse(opened(Shadows.shadowOf(v).getNextStartedActivity()));
            nordConnected();field.setText("Open Live TV");v.ask();
            assertTrue("open by voice",opened(Shadows.shadowOf(v).getNextStartedActivity()));assertTrue(v.isFinishing());
        }
    }
    private static EditText field(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText e=field(((ViewGroup)v).getChildAt(i));if(e!=null)return e;}return null;}
    @Test public void kidsRulesAreUnchanged(){
        nordConnected();prefs().edit().putString(Profiles.PREF,Profiles.CHILD).putString(KidsPolicy.APPS_PREF,"").commit();
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            Activity a=c.get();Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals("Live TV isn't on the Kids list. Ask an adult.",Launch.open(a,LIVE,"Live TV"));assertFalse(opened(Shadows.shadowOf(a).getNextStartedActivity()));
            prefs().edit().putString(KidsPolicy.APPS_PREF,LIVE).commit();
            assertNull("approved by an adult, and NordVPN connected",Launch.open(a,LIVE,"Live TV"));assertTrue(opened(Shadows.shadowOf(a).getNextStartedActivity()));
        }
    }

    // --- the safety net
    private ServiceController<LiveTvGuard> watching(){
        nordConnected();
        // As Robolectric.buildService does (written out so the stand-in compile check's Robolectric stub needn't have it).
        ServiceController<LiveTvGuard> s=ServiceController.of(new LiveTvGuard(),new Intent(app(),LiveTvGuard.class).putExtra(LiveTvGuard.LIVE,LIVE)).create().startCommand(0,1);
        assertTrue(LiveTvGuard.running);assertEquals(LIVE,s.get().live());return s;
    }
    @Test public void aDropCoversLiveTvWithTheNotice(){
        ShadowSettings.setCanDrawOverlays(true);
        ServiceController<LiveTvGuard> s=watching();
        idle(LiveTvGuard.SETTLE_MS*2);assertNull("NordVPN connected: nothing happens",Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity());
        nordDropped();LiveTvGuard.nordStopped();idle(LiveTvGuard.SETTLE_MS-100);
        assertNull("waits a moment: a reconnect is not a drop",Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity());
        idle(200);
        Intent cover=Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity();
        assertNotNull(cover);assertEquals(LiveTvCoverActivity.class.getName(),cover.getComponent().getClassName());assertEquals(LIVE,cover.getStringExtra(LiveTvGuard.LIVE));
        assertEquals("covered",LiveTvGuard.lastAction);
        s.get().check();assertNull("once per drop",Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity());
        try(ActivityController<LiveTvCoverActivity> c=Robolectric.buildActivity(LiveTvCoverActivity.class,cover).setup()){
            LiveTvCoverActivity a=c.get();String all=words(a.findViewById(android.R.id.content));
            assertTrue(all,all.contains("NordVPN disconnected — Live TV is covered. Reconnect NordVPN."));
            LiveTvProtectionTest.neverClaimsAKillSwitch(all);assertTrue(all.contains("This isn't a kill switch"));
            assertNull("the notice is the guard's own: the watch goes on",Shadows.shadowOf((android.app.Application)app()).getNextStoppedService());
            Button reconnect=find(a.findViewById(android.R.id.content),"Reconnect"),home=find(a.findViewById(android.R.id.content),"Back to Home");
            assertNotNull(reconnect);assertNotNull(home);assertTrue("Reconnect has the focus",reconnect.isFocused());
            home.performClick();Intent next=Shadows.shadowOf(a).getNextStartedActivity();
            assertNotNull(next);assertEquals(UnifiedPreviewActivity.class.getName(),next.getComponent().getClassName());assertTrue(a.isFinishing());
        }
        s.destroy();assertFalse(LiveTvGuard.running);
    }
    /** Review 7: the cover keeps the watch; leaving it another way says so, and Live TV back in front is covered again. */
    @Test public void theCoverKeepsWatching(){
        ShadowSettings.setCanDrawOverlays(true);ServiceController<LiveTvGuard> s=watching();
        NotificationManager nm=app().getSystemService(NotificationManager.class);
        nordDropped();s.get().check();Intent cover=Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity();assertNotNull(cover);
        try(ActivityController<LiveTvCoverActivity> c=Robolectric.buildActivity(LiveTvCoverActivity.class,cover).setup()){
            assertNull("not stopped by its own notice",Shadows.shadowOf((android.app.Application)app()).getNextStoppedService());
            // The TV's own Home over the cover (not Back to Home, not Reconnect): the watch says Live TV may be uncovered.
            c.pause().stop();idle(10);
            assertNotNull(Shadows.shadowOf(nm).getNotification(LiveTvGuard.DROPPED));assertTrue(LiveTvGuard.running);
            assertFalse(Shadows.shadowOf(s.get()).isStoppedBySelf());
        }
        // Back to Live TV while NordVPN is still down (front-app signal): covered again.
        LiveTvGuard.front(app(),LIVE,"");idle(10);
        Intent again=Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity();
        assertNotNull("covered again",again);assertEquals(LiveTvCoverActivity.class.getName(),again.getComponent().getClassName());
        // Reconnect is not leaving: no notice for it.
        nm.cancel(LiveTvGuard.DROPPED);
        try(ActivityController<LiveTvCoverActivity> c=Robolectric.buildActivity(LiveTvCoverActivity.class,again).setup()){
            find(c.get().findViewById(android.R.id.content),"Reconnect").performClick();c.pause().stop();idle(10);
            assertNull(Shadows.shadowOf(nm).getNotification(LiveTvGuard.DROPPED));
        }
        // Home on screen ends it.
        try(ActivityController<UnifiedPreviewActivity> h=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            Intent stopped=Shadows.shadowOf((android.app.Application)app()).getNextStoppedService();
            assertNotNull("Home ends the watch",stopped);assertEquals(LiveTvGuard.class.getName(),stopped.getComponent().getClassName());
        }
        s.destroy();
    }
    /** Review 6: without a front-app signal, the full-screen cover only in the first 15 minutes; then a notification. */
    @Test public void withoutAFrontAppSignalItCoversOnlyAtFirst(){
        assertTrue(LiveTvGuard.mayCover(false,LiveTvGuard.NO_SIGNAL_MS-1));assertFalse(LiveTvGuard.mayCover(false,LiveTvGuard.NO_SIGNAL_MS));
        assertTrue("a front-app signal: any time",LiveTvGuard.mayCover(true,LiveTvGuard.MAX_SESSION_MS));
        ShadowSettings.setCanDrawOverlays(true);
        ServiceController<LiveTvGuard> s=watching();idle(LiveTvGuard.NO_SIGNAL_MS+60_000);
        nordDropped();s.get().check();
        assertNull("may be another app by now: never covered",Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity());
        assertEquals("notified",LiveTvGuard.lastAction);
        assertNotNull(Shadows.shadowOf(app().getSystemService(NotificationManager.class)).getNotification(LiveTvGuard.DROPPED));
        s.destroy();
        s=watching();LiveTvGuard.front(app(),LIVE,"");idle(LiveTvGuard.NO_SIGNAL_MS+60_000);
        nordDropped();s.get().check();
        Intent cover=Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity();
        assertNotNull("the signal says Live TV is still in front",cover);assertEquals("covered",LiveTvGuard.lastAction);
        s.destroy();
    }
    @Test public void backOnTheNoticeGoesHomeNeverToLiveTvUncovered(){
        try(ActivityController<LiveTvCoverActivity> c=Robolectric.buildActivity(LiveTvCoverActivity.class,LiveTvCoverActivity.intent(app(),LIVE)).setup()){
            LiveTvCoverActivity a=c.get();a.onBackPressed();
            Intent next=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(UnifiedPreviewActivity.class.getName(),next.getComponent().getClassName());assertTrue(a.isFinishing());
        }
    }
    @Test public void nordVpnBackOnTheNoticeOffersLiveTvThroughTheGate(){
        try(ActivityController<LiveTvCoverActivity> c=Robolectric.buildActivity(LiveTvCoverActivity.class,LiveTvCoverActivity.intent(app(),LIVE)).setup()){
            LiveTvCoverActivity a=c.get();assertTrue(a.words(),a.words().startsWith(LiveTvCoverActivity.NOTICE));
            nordConnected();c.pause().resume();
            assertTrue(a.words(),a.words().startsWith(LiveTvCoverActivity.AGAIN));
            Button watch=find(a.findViewById(android.R.id.content),"Watch Live TV");assertNotNull(watch);watch.performClick();
            assertTrue(opened(Shadows.shadowOf(a).getNextStartedActivity()));assertTrue(a.isFinishing());
        }
    }
    @Test public void withoutDisplayOverOtherAppsANotificationSaysSo(){
        ServiceController<LiveTvGuard> s=watching();
        NotificationManager nm=app().getSystemService(NotificationManager.class);
        nordDropped();s.get().check();
        assertNull("Android won't let Unified TV come forward",Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity());
        Notification n=Shadows.shadowOf(nm).getNotification(LiveTvGuard.DROPPED);assertNotNull(n);assertEquals("notified",LiveTvGuard.lastAction);
        assertEquals("NordVPN disconnected",String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE)));
        assertTrue("honest: not covered",String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT)).contains("isn't covered"));
        assertEquals(NotificationManager.IMPORTANCE_HIGH,nm.getNotificationChannel(n.getChannelId()).getImportance());
        Intent opens=Shadows.shadowOf(n.contentIntent).getSavedIntent();assertEquals(LiveTvCoverActivity.class.getName(),opens.getComponent().getClassName());
        // NordVPN back: the notice goes, and the next drop is told again.
        nordConnected();s.get().check();assertNull(Shadows.shadowOf(nm).getNotification(LiveTvGuard.DROPPED));
        nordDropped();s.get().check();assertNotNull(Shadows.shadowOf(nm).getNotification(LiveTvGuard.DROPPED));
        s.destroy();assertNull("the session's notice goes with it",Shadows.shadowOf(nm).getNotification(LiveTvGuard.DROPPED));
    }
    @Test public void theWatchStopsWhenLiveTvLeaves(){
        ServiceController<LiveTvGuard> s=watching();
        LiveTvGuard.front(app(),LIVE,"");LiveTvGuard.front(app(),LiveTvProtection.NORDVPN,"");LiveTvGuard.front(app(),"com.android.systemui","");idle(10);
        assertFalse("Live TV, NordVPN and system windows don't end it",Shadows.shadowOf(s.get()).isStoppedBySelf());
        LiveTvGuard.front(app(),"com.tcl.launcher","");idle(10);assertTrue("another app in front ends it",Shadows.shadowOf(s.get()).isStoppedBySelf());
        s.destroy();
        s=watching();app().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle(10);assertTrue("standby ends it",Shadows.shadowOf(s.get()).isStoppedBySelf());s.destroy();
        s=watching();idle(LiveTvGuard.MAX_SESSION_MS);assertTrue("never left watching for ever",Shadows.shadowOf(s.get()).isStoppedBySelf());s.destroy();
        s=watching();
        try(ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            Intent stopped=Shadows.shadowOf((android.app.Application)app()).getNextStoppedService();
            assertNotNull("a Unified TV screen in front ends it",stopped);assertEquals(LiveTvGuard.class.getName(),stopped.getComponent().getClassName());
        }
        s.destroy();assertFalse(LiveTvGuard.running);
        LiveTvGuard.nordStopped();LiveTvGuard.front(app(),"com.tcl.launcher","");idle(LiveTvGuard.SETTLE_MS*2);   // no session: nothing to do
        assertNull(Shadows.shadowOf((android.app.Application)app()).getNextStartedActivity());
    }
    @Test public void theWatchNeverReadsKeys(){
        for(Method m:LiveTvGuard.class.getDeclaredMethods())for(Class<?> p:m.getParameterTypes())assertNotEquals(m.getName(),KeyEvent.class,p);
        for(Method m:LiveTvCoverActivity.class.getDeclaredMethods())assertFalse("only its own buttons and Back: "+m.getName(),m.getName().startsWith("onKey")||m.getName().equals("dispatchKeyEvent"));
    }
    @Test public void settingsSaysItIsNotAKillSwitch(){
        nordConnected();
        try(ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();Button row=null;
            for(View v:allViews(a.getWindow().getDecorView()))if(v instanceof Button&&((Button)v).getText().toString().startsWith("Live TV & Network Protection"))row=(Button)v;
            assertNotNull(row);assertEquals("Live TV & Network Protection, Open",String.valueOf(row.getContentDescription()));
            row.requestFocus();row.performClick();idle(500);
            String all=words(a.getWindow().getDecorView());
            assertTrue(all,all.contains("Live TV: open"));assertTrue(all,all.contains("This isn't a kill switch: if NordVPN drops, Live TV's connection isn't blocked by Android."));
            LiveTvProtectionTest.neverClaimsAKillSwitch(all);
        }
    }
    private static java.util.List<View> allViews(View v){java.util.List<View> out=new java.util.ArrayList<>();out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(allViews(((ViewGroup)v).getChildAt(i)));return out;}
}
