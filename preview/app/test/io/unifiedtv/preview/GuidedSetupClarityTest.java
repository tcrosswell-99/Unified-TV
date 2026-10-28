package io.unifiedtv.preview;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowPackageManager;
import java.time.Duration;
import java.util.*;

/**
 * The owner's walkthrough of build run 99 on the TCL (the guided setup, steps 1 to 5), on a 1080p TV in both apps:
 * only one panel is visible at a time; the remote step is a look that fits one screen with only Back and Next to press;
 * one OK chooses the Music app and it sticks (Home's Music place opens it even with Amazon Music paused); what was only
 * found is one line of text, not rows; every Build My TV page starts with what to do; the last step names what is on Home
 * now; Back walks the steps backwards and never leaves Unified TV.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class GuidedSetupClarityTest {
    static final String YT_MUSIC="com.google.android.youtube.tvmusic";
    private Context app;private MusicSessions.Source sessions;
    private SharedPreferences prefs(){return app.getSharedPreferences("unified-tv-preview",0);}
    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    /** Long enough for every fade and slide to finish. */
    private static void settle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));}
    @Before public void clean(){app=RuntimeEnvironment.getApplication();prefs().edit().clear().commit();sessions=MusicSessions.source;MusicFakes.Source none=new MusicFakes.Source();none.granted=false;MusicSessions.source=none;}
    @After public void restore(){MusicSessions.source=sessions;SettingsActivity.haForTests=null;}

    // --- looking at the screen as the household does
    private static View root(Activity a){return a.findViewById(android.R.id.content);}
    private static Button button(View v,String start){if(v instanceof Button&&v.isShown()&&((Button)v).getText().toString().startsWith(start))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=button(((ViewGroup)v).getChildAt(i),start);if(b!=null)return b;}return null;}
    private static boolean says(View v,String part){if(v instanceof TextView&&!(v instanceof Button)&&v.isShown()&&((TextView)v).getText().toString().contains(part))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(says(((ViewGroup)v).getChildAt(i),part))return true;return false;}
    private static TextView saying(View v,String part){if(v instanceof TextView&&!(v instanceof Button)&&v.isShown()&&((TextView)v).getText().toString().contains(part))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=saying(((ViewGroup)v).getChildAt(i),part);if(t!=null)return t;}return null;}
    /** Everything the remote can land on, by its words. */
    private static List<String> focusables(View v){List<String> out=new ArrayList<>();collect(v,out);return out;}
    private static void collect(View v,List<String> out){if(!v.isShown())return;if(v.isFocusable())out.add(v.getContentDescription()!=null&&!(v instanceof TextView)?v.getContentDescription().toString():v instanceof TextView?((TextView)v).getText().toString():String.valueOf(v));
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),out);}
    /** The first words in the page body that aren't a control: what to do on this page. */
    private static String firstLine(GlassScreen s){TextView t=firstText(s.body);return t==null?"":t.getText().toString();}
    private static TextView firstText(View v){if(!v.isShown())return null;if(v instanceof TextView&&!(v instanceof Button)&&!(v instanceof EditText)&&((TextView)v).length()>0)return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=firstText(((ViewGroup)v).getChildAt(i));if(t!=null)return t;}return null;}
    private static String focused(Activity a){View f=a.getWindow().getDecorView().findFocus();return f==null?"nothing":f.getContentDescription()!=null?f.getContentDescription().toString():f instanceof TextView?((TextView)f).getText().toString():String.valueOf(f);}
    private static void press(Activity a,String start){Button b=button(root(a),start);assertNotNull("'"+start+"' on screen",b);b.performClick();idle();}

    /** An installed TV app with a launch screen, as the TV lists it. */
    static void install(Context c,String pkg,String label){
        ShadowPackageManager spm=Shadows.shadowOf(c.getPackageManager());
        PackageInfo pi=new PackageInfo();pi.packageName=pkg;ApplicationInfo ai=new ApplicationInfo();ai.packageName=pkg;ai.nonLocalizedLabel=label;ai.enabled=true;pi.applicationInfo=ai;spm.installPackage(pi);
        ComponentName main=new ComponentName(pkg,pkg+".Main");spm.addActivityIfNotPresent(main);
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){IntentFilter f=new IntentFilter(Intent.ACTION_MAIN);f.addCategory(category);spm.addIntentFilterForActivity(main,f);}
    }
    private ActivityController<FirstRunActivity> guideAt(String step){
        FirstRun.save(prefs(),FirstRun.at(FirstRun.startOver(),step));
        ActivityController<FirstRunActivity> c=Robolectric.buildActivity(FirstRunActivity.class).setup();settle();
        assertEquals(step,c.get().step());return c;
    }

    // --- 1. One panel at a time
    @Test public void aPanelOpenedFromAPanelLeavesExactlyOneVisible(){
        ActivityController<FirstRunActivity> c=guideAt(FirstRun.APPS);FirstRunActivity guide=c.get();
        try{
            assertTrue(guide.panelShown());
            Button open=button(root(guide),"Open Build My TV");open.requestFocus();open.performClick();settle();
            assertFalse("the guided setup hides while Build My TV is open over it",guide.panelShown());
            assertFalse("and isn't drawn at all, so none of its words show through",guide.sheet.isShown());
            Intent next=Shadows.shadowOf(guide).getNextStartedActivity();assertEquals(BuildMyTvActivity.class.getName(),next.getComponent().getClassName());
            try(ActivityController<BuildMyTvActivity> b=Robolectric.buildActivity(BuildMyTvActivity.class,next).setup()){settle();
                assertTrue(b.get().panelShown());
                assertEquals("exactly one panel visible",1,(guide.panelShown()?1:0)+(b.get().panelShown()?1:0));
                b.get().onBackPressed();assertTrue("Back from Build My TV's first page returns to the guide",b.get().isFinishing());
            }
            c.pause().resume();settle();
            assertTrue("back from Build My TV, the guide shows again",guide.panelShown());assertEquals(1f,guide.sheet.getAlpha(),.001f);
            assertEquals("with focus on the row that opened it",FirstRun.APPS,guide.step());assertEquals("Open Build My TV",focused(guide));
        }finally{c.pause().stop().destroy();}
    }
    @Test public void settingsHidesUnderASetupPanelAndComesBackWithTheSameRowInFocus(){
        try(ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup()){settle();SettingsActivity s=c.get();
            assertTrue(s.panelShown());
            Button row=null;for(View v:all(root(s)))if(v instanceof Button&&v.isShown()&&((Button)v).getText().toString().contains("Apps & Shortcuts"))row=(Button)v;
            assertNotNull("the Apps & Shortcuts row",row);row.requestFocus();
            s.startActivity(new Intent(s,PerformanceActivity.class));settle();
            assertFalse("Settings hides while a setup panel is over it",s.panelShown());
            c.pause().resume();settle();
            assertTrue(s.panelShown());assertTrue("focus is back on the same row: "+focused(s),focused(s).contains("Apps & Shortcuts"));
            // Another app (Google Play, the TV's settings) covers the screen itself: Settings stays as it is.
            s.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));settle();
            assertTrue(s.panelShown());
        }
    }
    @Test public void withReduceMotionTheLowerPanelHidesAtOnce(){
        prefs().edit().putBoolean("reduceMotion",true).commit();
        ActivityController<FirstRunActivity> c=guideAt(FirstRun.APPS);FirstRunActivity guide=c.get();
        try{press(guide,"Open Build My TV");assertFalse("no fade to wait for",guide.panelShown());
            c.pause().resume();idle();assertTrue(guide.panelShown());}
        finally{c.pause().stop().destroy();}
    }
    @Test public void aPanelOpenedDuringTheSlideInComesBackFullyAcross(){
        // Real frame timing, so the 300 ms slide-in is still part-way after 100 ms (Robolectric otherwise ends it at once).
        ShadowChoreographer.setPaused(true);ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        try{slideInThenOpen();}finally{ShadowChoreographer.setPaused(false);}
    }
    private void slideInThenOpen(){
        FirstRun.save(prefs(),FirstRun.at(FirstRun.startOver(),FirstRun.APPS));
        ActivityController<FirstRunActivity> c=Robolectric.buildActivity(FirstRunActivity.class).setup();FirstRunActivity guide=c.get();
        try{
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            assertTrue("still sliding in ("+guide.sheet.getTranslationX()+")",guide.sheet.getTranslationX()>0);
            press(guide,"Open Build My TV");settle();assertFalse(guide.panelShown());
            c.pause().resume();settle();
            assertEquals("fully across, not stuck part-way",0f,guide.sheet.getTranslationX(),.001f);
            assertEquals(1f,guide.sheet.getAlpha(),.001f);assertTrue(guide.panelShown());
        }finally{c.pause().stop().destroy();}
        try(ActivityController<SettingsActivity> sc=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity s=sc.get();View sheet=settingsSheet(s);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            assertTrue("Settings still sliding in ("+sheet.getTranslationX()+")",sheet.getTranslationX()>0);
            s.startActivity(new Intent(s,PerformanceActivity.class));settle();assertFalse(s.panelShown());
            sc.pause().resume();settle();
            assertEquals(0f,sheet.getTranslationX(),.001f);assertEquals(1f,sheet.getAlpha(),.001f);assertTrue(s.panelShown());
        }
    }
    private static View settingsSheet(SettingsActivity s){try{java.lang.reflect.Field f=SettingsActivity.class.getDeclaredField("sheet");f.setAccessible(true);return (View)f.get(s);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static List<View> all(View v){List<View> out=new ArrayList<>();out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(all(((ViewGroup)v).getChildAt(i)));return out;}

    // --- 2. The remote step: a look, on one screen
    @Test public void theRemoteStepFitsOneScreenWithOnlyBackAndNextToPress(){
        ActivityController<FirstRunActivity> c=guideAt(FirstRun.REMOTE);FirstRunActivity a=c.get();
        try{
            assertEquals("the first line says there is nothing to do",RemoteHelp.LOOK_ONLY,firstLine(a));
            assertTrue("laid out at 1080p",a.scroll.getHeight()>500);
            assertFalse("fits without scrolling ("+a.body.getHeight()+" in "+a.scroll.getHeight()+")",a.scroll.canScrollVertically(1));
            assertTrue(a.body.getHeight()<=a.scroll.getHeight());
            assertEquals("only Back and Next can be focused",Arrays.asList("Back","Next"),focusables(a.sheet));
            boolean launcher=DefaultHome.available(a);
            List<RemoteHelp.Key> keys=RemoteHelp.glance(launcher,launcher&&DefaultHome.isDefault(a));
            assertTrue("a glance: at most eight",keys.size()<=RemoteHelp.GLANCE_MAX);
            for(RemoteHelp.Key k:keys){TextView t=saying(a.sheet,k.meaning);assertNotNull(k.button+" is explained",t);assertFalse(t.isFocusable());}
            assertTrue("this app's Back, told truly",says(a.sheet,launcher?"Stays in Unified TV":"closes the preview"));
            assertTrue(says(a.sheet,RemoteHelp.TV_KEYS));
            assertFalse("no 'Try a button' task",says(a.sheet,"Try a button"));
            // A button pressed here still says what it does on Home.
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));idle();
            assertTrue(says(a.sheet,"Up: on Home"));
        }finally{c.pause().stop().destroy();}
    }
    @Test public void theGlanceIsTrueForBothAppsAndBothHomeButtons(){
        assertTrue(RemoteHelp.glance(true,false).toString().contains("Back: One step back. Stays in Unified TV"));
        assertTrue(RemoteHelp.glance(false,false).toString().contains("closes the preview"));
        assertTrue(RemoteHelp.glance(true,true).toString().contains("Home: Back to Unified TV"));
        assertTrue(RemoteHelp.glance(true,false).toString().contains("Home: The TV's own Home screen"));
        for(boolean l:new boolean[]{true,false})for(RemoteHelp.Key k:RemoteHelp.glance(l,l))assertTrue(k+" is short",k.meaning.length()<=42);
    }

    // --- 3. Build My TV: music, one OK, and it sticks
    @Test public void oneOkChoosesTheMusicAppAndItSticks()throws Exception{
        MusicFakes.installAmazon(app,false,false);install(app,YT_MUSIC,"YouTube Music");
        try(ActivityController<BuildMyTvActivity> c=Robolectric.buildActivity(BuildMyTvActivity.class).setup()){settle();BuildMyTvActivity b=c.get();
            press(b,"Start with Music");
            assertEquals("Which music app do you use? Pick one — it opens from the Music place on Home.",firstLine(b));
            Button yt=button(root(b),"YouTube Music");assertNotNull(yt);assertFalse(yt.getText().toString().contains("✓"));
            yt.performClick();idle();
            assertEquals("one OK: it is the Music app","youtubemusic",HubBuilder.load(prefs(),Profiles.ADULT).preferred(AppCatalogue.MUSIC));
            yt=button(root(b),"YouTube Music");assertTrue("the tick shows at once",yt.getText().toString().contains("✓  Your Music app"));
            assertSame("focus stays on it: "+focused(b),yt,b.getWindow().getDecorView().findFocus());
            assertFalse(button(root(b),"Amazon Music").getText().toString().contains("✓"));
            assertNull("no separate default step",button(root(b),"Make default"));assertNull(button(root(b),"Default"));
            yt.performClick();idle();assertEquals("OK again keeps it","youtubemusic",HubBuilder.load(prefs(),Profiles.ADULT).preferred(AppCatalogue.MUSIC));
        }
        // Leave Build My TV and come back: still YouTube Music, never back to Amazon Music.
        try(ActivityController<BuildMyTvActivity> c=Robolectric.buildActivity(BuildMyTvActivity.class).setup()){settle();BuildMyTvActivity b=c.get();
            Button yt=button(root(b),"YouTube Music");assertNotNull("resumes on the Music page",yt);assertTrue(yt.getText().toString().contains("✓"));
            assertFalse(button(root(b),"Amazon Music").getText().toString().contains("✓"));
        }
        assertEquals("youtubemusic",HubBuilder.load(prefs(),Profiles.ADULT).preferred(AppCatalogue.MUSIC));
        // Home's Music place opens it, even while Amazon Music has a paused session.
        MusicFakes.Source paused=new MusicFakes.Source();paused.sessions.add(new MusicFakes.Session(MusicFakes.AMAZON,NowPlaying.STATE_PAUSED));MusicSessions.source=paused;
        prefs().edit().putString("dock",RemoteJourneyTest.SEEDED).commit();
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){idle();
            View home=RemoteJourneyTest.homeIn(c.get());
            java.lang.reflect.Field dock=home.getClass().getDeclaredField("dock");dock.setAccessible(true);Object music=null;
            for(Object item:(List<?>)dock.get(home)){java.lang.reflect.Field id=item.getClass().getDeclaredField("id");id.setAccessible(true);if("music".equals(id.get(item)))music=item;}
            assertNotNull("Music is on Home",music);
            Shadows.shadowOf(c.get()).clearNextStartedActivities();
            java.lang.reflect.Method launch=home.getClass().getDeclaredMethod("launch",music.getClass());launch.setAccessible(true);launch.invoke(home,music);
            Intent opened=Shadows.shadowOf(c.get()).getNextStartedActivity();assertNotNull(opened);
            String where=opened.getComponent()!=null?opened.getComponent().getPackageName():opened.getPackage();
            assertEquals("the Music place opens YouTube Music, not Amazon Music",YT_MUSIC,where);
        }
    }
    @Test public void theMusicPlaceRule(){
        assertEquals(HubBuilder.MusicTile.CHOSEN_APP,HubBuilder.musicTile(YT_MUSIC,false,true));
        assertEquals(HubBuilder.MusicTile.NOW_PLAYING,HubBuilder.musicTile(MusicFakes.AMAZON,true,true));
        assertEquals(HubBuilder.MusicTile.MUSIC_PAGE,HubBuilder.musicTile(MusicFakes.AMAZON,true,false));
        assertEquals(HubBuilder.MusicTile.NOW_PLAYING,HubBuilder.musicTile(null,false,true));
        assertEquals(HubBuilder.MusicTile.MUSIC_PAGE,HubBuilder.musicTile(null,false,false));
    }

    // --- 3. Build My TV: what was only found is one line; every page says what to do first
    @Test public void whatWasOnlyFoundIsOneLineAndEveryPageSaysWhatToDo(){
        install(app,"com.netflix.ninja","Netflix");install(app,"com.amazon.amazonvideo.livingroom","Prime Video");MusicFakes.installAmazon(app,false,false);
        SettingsActivity.haForTests=false;prefs().edit().putString("dock","netflix,bbc,live,music").commit();
        try(ActivityController<BuildMyTvActivity> c=Robolectric.buildActivity(BuildMyTvActivity.class).setup()){settle();BuildMyTvActivity b=c.get();
            assertTrue("the overview says what to do: "+firstLine(b),firstLine(b).contains("Press Start with Music"));
            assertTrue("and, once, that Home needn't be full",says(b.body,"Home's places don't all need filling"));
            press(b,"Start with Music");
            Set<String> seen=new LinkedHashSet<>();
            for(AppCatalogue.Category cat:AppCatalogue.CATEGORIES){
                String line=firstLine(b);seen.add(cat.id);
                assertTrue(cat.title+" starts with what to do: "+line,line.matches(".*(Press|press Next|Pick one|Tick the apps|\\?).*"));
                if(AppCatalogue.VIDEO.equals(cat.id)){
                    assertEquals("Tick the apps you want on Home. The others stay in Apps.",line);
                    for(String f:focusables(b.body))assertTrue("only things that do something are focusable: "+f,
                        f.startsWith("Netflix")||f.startsWith("Prime Video")||f.startsWith("Get ")||f.startsWith("Next: ")||f.equals("All categories"));
                    TextView rest=saying(b.body,"Not on this TV: Disney+, BBC iPlayer, Apple TV.");assertNotNull("the rest is one line of names",rest);assertFalse(rest.isFocusable());
                    assertTrue("a tick shows what is on Home now",button(b.body,"Netflix").getText().toString().contains("✓  On Home"));
                    Button prime=button(b.body,"Prime Video");assertTrue(prime.getText().toString(),prime.getText().toString().contains("In Apps"));
                    prime.performClick();idle();
                    assertTrue("ticked for Home: "+button(b.body,"Prime Video").getText(),button(b.body,"Prime Video").getText().toString().contains("✓  On Home"));assertTrue("saved as wanted on Home",HubBuilder.load(prefs(),Profiles.ADULT).onDock("primevideo"));
                }
                String next=AppCatalogue.next(cat.id);
                press(b,next==null?"Next: arrange Home":"Next: "+AppCatalogue.category(next).title);
            }
            assertEquals(AppCatalogue.CATEGORIES.size(),seen.size());
            assertTrue("Arrange says what to do: "+firstLine(b),firstLine(b).contains("Then press Save to Home"));
        }
    }

    // --- 4. The last step: what is on Home now
    @Test public void theLastStepNamesWhatIsOnHomeNow(){
        prefs().edit().putString("dock","netflix,disney,bbc,live,music").commit();
        ActivityController<FirstRunActivity> c=guideAt(FirstRun.FINISH);FirstRunActivity a=c.get();
        try{
            String room=prefs().getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");List<String> names=new ArrayList<>();
            for(String id:DockPolicy.visible(HouseholdSettings.dock(app,room),DockCapacity.get(prefs(),Profiles.ADULT)))names.add(DockCapacity.name(app,id));
            assertTrue(names.containsAll(Arrays.asList("Netflix","Disney+","BBC","Live TV","Music")));
            assertEquals("On Home: "+String.join(", ",names)+".",firstLine(a));
            assertTrue(says(a.body,"You can change these any time: hold OK on an app on Home."));
            assertFalse("never 'not chosen yet' when Home has apps",says(a.body,"not chosen yet"));
            assertTrue(says(a.body,"Films & TV: skipped — set up any time in Settings"));
            assertTrue(says(a.body,"Photos: skipped — set up any time in Settings"));
            assertTrue(says(a.body,"Smart home: skipped — set up any time in Settings"));
            assertEquals("only Back and Go to Home",Arrays.asList("Back","Go to Home"),focusables(a.sheet));
        }finally{c.pause().stop().destroy();}
        SettingsActivity.haForTests=true;
        HubBuilder.State s=HubBuilder.load(prefs(),Profiles.ADULT);HubBuilder.chooseMusic(s,"spotify");HubBuilder.save(prefs(),Profiles.ADULT,s);
        ActivityController<FirstRunActivity> again=guideAt(FirstRun.FINISH);
        try{assertTrue(says(again.get().body,"Smart home: Home Assistant is set up"));assertTrue(says(again.get().body,"Music: Spotify"));}
        finally{again.pause().stop().destroy();}
    }

    // --- 5. Every step: what to do first, and Back walks backwards
    @Test public void everyStepSaysWhatToDoAndBackWalksBackwards(){
        ActivityController<FirstRunActivity> c=Robolectric.buildActivity(FirstRunActivity.class).setup();settle();FirstRunActivity a=c.get();
        try{
            List<String> order=new ArrayList<>();
            for(int i=0;i<FirstRun.STEPS.size();i++){
                order.add(a.step());String line=firstLine(a);
                assertTrue(a.step()+" starts with what to do: "+line,line.startsWith("On Home:")||line.startsWith("Home has no apps yet")||line.matches(".*(Press|press|Nothing to do).*"));
                if(i==0)press(a,"Start");else if(i<FirstRun.STEPS.size()-1)press(a,"Next");
            }
            assertEquals(FirstRun.STEPS,order);
            for(int i=FirstRun.STEPS.size()-1;i>0;i--){a.onBackPressed();idle();assertEquals("Back goes one step back",FirstRun.STEPS.get(i-1),a.step());assertFalse(a.isFinishing());}
            // Left with nothing further left is Back too: from the remote step's Next, one step back, not out.
            press(a,"Start");Button next=button(root(a),"Next");next.requestFocus();
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));idle();
            assertEquals(FirstRun.WELCOME,a.step());assertFalse(a.isFinishing());
        }finally{c.pause().stop().destroy();}
    }
}
