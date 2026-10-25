package io.unifiedtv.preview;

import android.app.Activity;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowPackageManager;
import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Owner feedback from the TCL, 25 October 2026: Back from Ask your TV and from short visits to AirPin and NordVPN comes
 * back to Unified TV where Android allows; NordVPN opens at its own main screen; Ask your TV is the small glass pane over
 * Home; and (OD-11) every setup or management screen is the glass sidebar over a still Home.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class ReturnJourneyTest {
    private static final String NORD=LiveTvProtection.NORDVPN;
    private Context app(){return RuntimeEnvironment.getApplication();}
    @Before public void clean(){app().getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();}
    private static void idle(int ms){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    /** Installs a fake TV app whose leanback entry is {@code main}, plus a settings screen it also lists first. */
    private void install(String pkg,String main,int launchMode,String label){
        ShadowPackageManager pm=Shadows.shadowOf(app().getPackageManager());
        for(String name:new String[]{pkg+".tv.TvSettingsActivity",main}){
            ComponentName c=new ComponentName(pkg,name);ActivityInfo info=pm.addActivityIfNotPresent(c);info.launchMode=name.equals(main)?launchMode:ActivityInfo.LAUNCH_MULTIPLE;info.nonLocalizedLabel=label;
            IntentFilter f=new IntentFilter(Intent.ACTION_MAIN);f.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            try{pm.addIntentFilterForActivity(c,f);}catch(Exception e){throw new AssertionError(e);}
        }
    }
    private static Object get(Object o,String name){try{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static boolean flag(Intent i,int f){return (i.getFlags()&f)!=0;}
    private static String words(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append(' ');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(words(((ViewGroup)v).getChildAt(i)));return b.toString();}
    private static Button find(View v,String start){if(v instanceof Button&&((Button)v).getText().toString().startsWith(start))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),start);if(b!=null)return b;}return null;}
    private static boolean translucent(Activity a){android.content.res.TypedArray t=a.getTheme().obtainStyledAttributes(new int[]{android.R.attr.windowIsTranslucent});try{return t.getBoolean(0,false);}finally{t.recycle();}}

    // --- 2(b) and 4: NordVPN and AirPin
    @Test public void nordVpnOpensAtItsMainScreenInsideUnifiedTvsTask(){
        install(NORD,NORD+".tv.TvMainActivity",ActivityInfo.LAUNCH_MULTIPLE,"NordVPN");
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            VpnGate.connect(c.get());Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull(started);assertEquals("its main screen, not the settings screen it lists first",NORD+".tv.TvMainActivity",started.getComponent().getClassName());
            assertFalse("inside Unified TV's task, so Back returns here",flag(started,Intent.FLAG_ACTIVITY_NEW_TASK));
            assertFalse("no launcher category: the app can't mistake it for a second launch",started.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER));
            // Back on Unified TV at once: the app refused to start inside another app, so it opens on its own instead.
            Launch.returned(c.get());Intent again=Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull(again);assertTrue(flag(again,Intent.FLAG_ACTIVITY_NEW_TASK));assertTrue("its main screen comes forward",flag(again,Intent.FLAG_ACTIVITY_CLEAR_TOP));
            Launch.returned(c.get());assertNull("only once",Shadows.shadowOf(c.get()).getNextStartedActivity());
        }
    }
    @Test public void aVisitLongEnoughIsNotOpenedTwice(){
        install(NORD,NORD+".tv.TvMainActivity",ActivityInfo.LAUNCH_MULTIPLE,"NordVPN");
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            VpnGate.connect(c.get());Shadows.shadowOf(c.get()).getNextStartedActivity();idle((int)Launch.REFUSED_MS+500);
            c.pause().resume();assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
        }
    }
    @Test public void anAppThatKeepsItsOwnTaskStillOpensAtItsMainScreen(){
        install(NORD,NORD+".tv.TvMainActivity",ActivityInfo.LAUNCH_SINGLE_TASK,"NordVPN");
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            VpnGate.connect(c.get());Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals(NORD+".tv.TvMainActivity",started.getComponent().getClassName());
            assertTrue(flag(started,Intent.FLAG_ACTIVITY_NEW_TASK));assertTrue("not whatever screen was left open",flag(started,Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
    }
    @Test public void filmsAndProgrammesKeepTheirOwnTask(){
        install("com.netflix.ninja","com.netflix.ninja.MainActivity",ActivityInfo.LAUNCH_MULTIPLE,"Netflix");
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            assertNull(Launch.open(c.get(),"com.netflix.ninja","Netflix"));Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertTrue("playback is never closed by coming back to Unified TV",flag(started,Intent.FLAG_ACTIVITY_NEW_TASK));assertEquals("com.netflix.ninja.MainActivity",started.getComponent().getClassName());
        }
    }
    @Test public void openingAirPinMakesHomeLookAgainAfterwards()throws Exception{
        install("com.waxrain.airplaydmr","com.waxrain.airplaydmr.WaxPlayer",ActivityInfo.LAUNCH_MULTIPLE,"AirPin(LITE)");
        AirPlayReceiver.takeReturn();
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            assertNull(Launch.visit(c.get(),"com.waxrain.airplaydmr","AirPin LITE"));assertNotNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
            c.pause().stop();idle(2000);c.restart().start().resume();
            View home=RemoteJourneyTest.homeIn(c.get());
            android.os.Handler handler=(android.os.Handler)get(home,"handler");Runnable check=(Runnable)get(home,"airPlayCheck");
            assertTrue("follow-up checks are waiting",handler.hasCallbacks(check));
            assertFalse("taken: one set of follow-ups per visit",AirPlayReceiver.takeReturn());
            c.pause();assertFalse("leaving Home cancels them",handler.hasCallbacks(check));
        }
    }

    // --- 2(a) and 6: Ask your TV
    @Test public void askYourTvIsASmallGlassPaneThatBackCloses(){
        try(ActivityController<VoiceActivity> c=Robolectric.buildActivity(VoiceActivity.class).setup()){
            VoiceActivity v=c.get();assertTrue("over a still Home",translucent(v));
            assertEquals(VoiceActivity.READY,v.stateText());assertFalse("the microphone is never open by itself",v.listening());
            int screen=v.getResources().getDisplayMetrics().widthPixels;assertTrue("a small pane, not a page",v.pane().getLayoutParams().width<screen/2);
            String all=words(v.findViewById(android.R.id.content));
            assertTrue(all,all.contains("General questions aren't connected yet"));
            for(String claim:new String[]{"ChatGPT","GPT","Claude","OpenAI"})assertFalse("no claim of an AI assistant: "+claim,all.contains(claim));
            v.onBackPressed();assertTrue(v.isFinishing());
        }
    }
    @Test public void anUnknownRequestIsAnsweredHonestlyAndOpensNothing(){
        try(ActivityController<VoiceActivity> c=Robolectric.buildActivity(VoiceActivity.class).setup()){
            VoiceActivity v=c.get();
            ViewGroup root=v.findViewById(android.R.id.content);EditText field=field(root);field.setText("What's the weather tomorrow?");v.ask();
            assertTrue(v.answerText(),v.answerText().contains("General questions aren't connected yet"));
            assertNull(Shadows.shadowOf(v).getNextStartedActivity());assertFalse(v.isFinishing());
            field.setText("Open Live TV");v.ask();assertTrue("voice is never a way round the Live TV lock",v.answerText().contains("Live TV stays locked"));
        }
    }
    @Test public void speakAsksBeforeTheMicrophoneIsUsed(){
        try(ActivityController<VoiceActivity> c=Robolectric.buildActivity(VoiceActivity.class).setup()){
            VoiceActivity v=c.get();find(v.findViewById(android.R.id.content),"Speak").performClick();
            String asked=Shadows.shadowOf(v).getLastRequestedPermission()==null?null:Shadows.shadowOf(v).getLastRequestedPermission().requestedPermissions[0];
            Intent system=Shadows.shadowOf(v).getNextStartedActivity();
            assertTrue("either the household is asked for the microphone, or the TV's own voice screen opens",
                android.Manifest.permission.RECORD_AUDIO.equals(asked)||(system!=null&&android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH.equals(system.getAction())));
            assertFalse(v.listening());
        }
    }
    private static EditText field(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText e=field(((ViewGroup)v).getChildAt(i));if(e!=null)return e;}return null;}
    @Test public void backFromAskYourTvLandsOnHomeAsItWas()throws Exception{
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            View home=RemoteJourneyTest.homeIn(c.get());Method key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
            java.lang.reflect.Field header=home.getClass().getDeclaredField("header"),index=home.getClass().getDeclaredField("index");header.setAccessible(true);index.setAccessible(true);
            int tile=index.getInt(home);
            for(int code:new int[]{KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_CENTER}){key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,code));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,code));}
            Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals(VoiceActivity.class.getName(),started.getComponent().getClassName());
            c.pause();c.resume();   // the pane is translucent: Home is only paused under it
            assertTrue("the microphone stays focused, as before",header.getBoolean(home));assertEquals(tile,index.getInt(home));assertFalse(c.get().isFinishing());
        }
    }

    // --- OD-11: setup and management as the glass sidebar over a still Home
    @Test public void setupScreensAreTheSidebarNotAFullScreenPage(){
        for(Class<? extends SheetScreen> type:java.util.Arrays.asList(BuildMyTvActivity.class,PerformanceActivity.class,RemoteCheckActivity.class,GalleryActivity.class)){
            Intent i=new Intent(app(),type).putExtra("edit",true);
            try(ActivityController<? extends SheetScreen> c=Robolectric.buildActivity(type,i).setup()){
                SheetScreen s=c.get();assertTrue(type.getSimpleName()+" over a still Home",translucent(s));assertNotNull(type.getSimpleName(),s.sheet());
                int screen=s.getResources().getDisplayMetrics().widthPixels,width=s.sheet().getLayoutParams().width;
                assertTrue(type.getSimpleName()+" is a sidebar ("+width+" of "+screen+")",width<screen*.8f);
                assertEquals(Gravity.END,((android.widget.FrameLayout.LayoutParams)s.sheet().getLayoutParams()).gravity);
            }
        }
    }
    @Test public void managingAnAppHappensInTheSidebarAndBackClosesIt(){
        try(ActivityController<AppsActivity> c=Robolectric.buildActivity(AppsActivity.class).setup()){
            AppsActivity a=c.get();Button library=find(a.findViewById(android.R.id.content),"Library");assertNotNull(library);
            library.performLongClick();assertTrue(a.sideOpen());assertNull("no centred dialog",ShadowAlertDialog.getLatestAlertDialog());
            assertNotNull(find(a.findViewById(android.R.id.content),"Open"));assertNotNull(find(a.findViewById(android.R.id.content),"App information"));
            a.onBackPressed();assertFalse(a.sideOpen());assertFalse("Back closed only the sidebar",a.isFinishing());
            a.onBackPressed();assertTrue(a.isFinishing());
        }
    }
}
