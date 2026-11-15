package io.unifiedtv.preview;

import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/**
 * The owner's Sony (Android TV 10): ITVX is ITV's phone build, air.ITVMobilePlayer (formerly ITV Hub), with only a phone
 * launcher (CATEGORY_LAUNCHER, no LEANBACK_LAUNCHER); Freeview Play is uk.co.freeview.android. Both are named by their
 * services and drawn as simple white marks (OD-20), listed with the streaming apps, and an app with only a phone launcher
 * opens from a dock tile, from Apps and by voice, through the one gate (Launch). Both flavours run this class.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class ItvxFreeviewTest {
    private static final String ITVX="air.ITVMobilePlayer",ITVX_MAIN="com.candyspace.itvplayer.ui.launcher.LauncherActivity",FREEVIEW="uk.co.freeview.android";
    private Context app(){return RuntimeEnvironment.getApplication();}
    private SharedPreferences prefs(){return app().getSharedPreferences("unified-tv-preview",0);}
    private static void idle(long ms){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    /** An app with one launch screen in {@code category} only. */
    private void install(String pkg,String activity,String label,String category){
        ShadowPackageManager pm=Shadows.shadowOf(app().getPackageManager());
        ComponentName c=new ComponentName(pkg,activity);ActivityInfo info=pm.addActivityIfNotPresent(c);info.nonLocalizedLabel=label;if(info.applicationInfo!=null)info.applicationInfo.nonLocalizedLabel=label;
        IntentFilter f=new IntentFilter(Intent.ACTION_MAIN);f.addCategory(category);
        try{pm.addIntentFilterForActivity(c,f);}catch(Exception e){throw new AssertionError(e);}
    }
    @Before public void tv(){
        prefs().edit().clear().commit();
        install("com.netflix.ninja","com.netflix.ninja.Main","Netflix",Intent.CATEGORY_LEANBACK_LAUNCHER);
        install(ITVX,ITVX_MAIN,"ITV Hub",Intent.CATEGORY_LAUNCHER);   // the phone build: no Android TV launcher entry
        install(FREEVIEW,FREEVIEW+".Main","Freeview Play",Intent.CATEGORY_LEANBACK_LAUNCHER);
        install("com.example.clock","com.example.clock.Main","Clock",Intent.CATEGORY_LEANBACK_LAUNCHER);
        LiveTvProtection.forTests=new LiveTvProtection.Facts();AppPickerActivity.testWorker=Runnable::run;
    }
    @After public void reset(){AppPickerActivity.testWorker=null;LiveTvProtection.forTests=null;LiveTvProtection.last=null;prefs().edit().clear().commit();}
    private static boolean opensItvx(Intent i){return i!=null&&i.getComponent()!=null&&ITVX.equals(i.getComponent().getPackageName())&&ITVX_MAIN.equals(i.getComponent().getClassName());}
    /** DockGlyphs' own bold face (Robolectric's typefaces don't report their weight). */
    private static Typeface bold(){try{Field f=DockGlyphs.class.getDeclaredField("BOLD");f.setAccessible(true);return (Typeface)f.get(null);}catch(Exception e){throw new AssertionError(e);}}
    /** The words a mark draws, in order, each with "*" after it when set in bold (a recording canvas: no pixels needed). */
    private static List<String> words(String glyph){
        List<String> out=new ArrayList<>();Typeface bold=bold();
        Canvas c=new Canvas(Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888)){
            @Override public void drawText(String text,float x,float y,Paint paint){
                assertEquals("white",0xFAFAFC,paint.getColor()&0xFFFFFF);
                out.add(text+(paint.getTypeface()==bold?"*":""));}
        };
        DockGlyphs.draw(c,glyph,100,100,new Paint(Paint.ANTI_ALIAS_FLAG),new Path(),new RectF(),null);
        return out;
    }

    // --- Names and marks
    @Test public void itvxIsNamedAndDrawnAsItvx(){
        assertEquals("ITVX",DockLabels.name(ITVX));assertEquals("ITVX",DockLabels.name(ITVX,true));
        assertEquals("its installed name may still be ITV Hub","ITVX",DockLabels.shown(ITVX,"ITV Hub"));
        assertEquals("itvx",DockLabels.glyph(ITVX));assertTrue(DockLabels.GLYPHS.contains("itvx"));assertEquals("a lower-case itv and a bold X",Arrays.asList("itv","X*"),words("itvx"));
        assertNotEquals("its own mark, never the generic one",DockLabels.glyph(ITVX),DockLabels.glyph("com.example.clock"));
        assertEquals("ITVX",DockCapacity.name(app(),ITVX));
    }
    @Test public void freeviewPlayIsNamedAndDrawnAsFreeviewPlay(){
        assertEquals("Freeview Play",DockLabels.name(FREEVIEW));assertEquals("Freeview Play",DockLabels.shown(FREEVIEW,"Freeview"));
        assertEquals("freeview",DockLabels.glyph(FREEVIEW));assertTrue(DockLabels.GLYPHS.contains("freeview"));assertEquals(Arrays.asList("freeview*","play"),words("freeview"));
        assertNotEquals(DockLabels.glyph(ITVX),DockLabels.glyph(FREEVIEW));
        assertEquals("Freeview Play",DockCapacity.name(app(),FREEVIEW));
        assertNull("an ordinary app keeps its own name",DockLabels.name("com.example.clock"));assertEquals("Clock",DockLabels.shown("com.example.clock","Clock"));
        assertEquals("SmartTube keeps its own label in lists","SmartTube",DockLabels.shown("org.smarttube.beta","SmartTube"));
    }
    @Test public void bothAreStreamingAppsInThePickerAndBuildMyTv(){
        assertTrue(DockPicker.streaming(ITVX));assertTrue(DockPicker.streaming(FREEVIEW));
        assertEquals(AppCatalogue.VIDEO,AppCatalogue.entry("itvx").category);assertEquals(AppCatalogue.VIDEO,AppCatalogue.entry("freeview").category);
        Map<String,String> tv=new HashMap<>();tv.put(ITVX,"ITV Hub");tv.put(FREEVIEW,"Freeview Play");tv.put("com.example.clock","Clock");
        List<String> found=AppCatalogue.detected(AppCatalogue.VIDEO,tv);assertTrue(found.contains("itvx"));assertTrue(found.contains("freeview"));
        assertEquals(ITVX,AppCatalogue.dockId(AppCatalogue.entry("itvx"),tv));
        assertNull("recognised when installed, never offered for install",AppCatalogue.storeUris(AppCatalogue.entry("itvx")));
        assertNull(AppCatalogue.storeUris(AppCatalogue.entry("freeview")));
        try(ActivityController<AppPickerActivity> p=Robolectric.buildActivity(AppPickerActivity.class,new Intent(app(),AppPickerActivity.class).putExtra(AppPickerActivity.MODE,AppPickerActivity.PLACE).putExtra(AppPickerActivity.PLACE,0)).setup()){
            idle(0);idle(0);List<String> offered=p.get().offered();
            assertTrue(offered.toString(),offered.contains("ITVX")&&offered.contains("Freeview Play"));
            assertTrue("streaming first: "+offered,offered.indexOf("ITVX")<offered.indexOf("Clock")&&offered.indexOf("Freeview Play")<offered.indexOf("Clock"));
            assertFalse("named once, by its service",offered.contains("ITV Hub"));
        }
    }

    // --- A phone-launcher-only app opens everywhere
    @Test public void aPhoneLauncherOnlyAppHasAnEntry(){
        Intent e=Launch.entry(app().getPackageManager(),ITVX);assertNotNull(e);
        assertEquals(ITVX_MAIN,e.getComponent().getClassName());
        Intent android=app().getPackageManager().getLaunchIntentForPackage(ITVX);
        assertNotNull(android);assertEquals("the screen getLaunchIntentForPackage names",android.getComponent(),e.getComponent());
        assertTrue(e.hasCategory(Intent.CATEGORY_LAUNCHER));assertTrue((e.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)!=0);
    }
    @Test public void itOpensFromItsDockTile()throws Exception{
        prefs().edit().putString("dock",ITVX+",netflix").commit();
        try(ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup()){
            View home=RemoteJourneyTest.homeIn(c.get());Method key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
            while(Shadows.shadowOf(c.get()).getNextStartedActivity()!=null){}
            Field dock=home.getClass().getDeclaredField("dock");dock.setAccessible(true);Object first=((List<?>)dock.get(home)).get(0);
            Field id=first.getClass().getDeclaredField("id"),name=first.getClass().getDeclaredField("name");id.setAccessible(true);name.setAccessible(true);
            assertEquals(ITVX,id.get(first));assertEquals("the tile is named ITVX","ITVX",name.get(first));
            key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
            assertTrue("OK on the tile opens ITVX",opensItvx(Shadows.shadowOf(c.get()).getNextStartedActivity()));
        }
    }
    @Test public void itOpensFromApps(){
        try(ActivityController<AppsActivity> c=Robolectric.buildActivity(AppsActivity.class).setup()){
            View tile=described(c.get().findViewById(android.R.id.content),"ITVX");assertNotNull("one tile named ITVX",tile);
            assertNull("never by its old name",described(c.get().findViewById(android.R.id.content),"ITV Hub"));
            tile.performClick();assertTrue("OK opens ITVX",opensItvx(Shadows.shadowOf(c.get()).getNextStartedActivity()));
        }
    }
    @Test public void openItvxByVoice(){
        assertEquals("itvx",VoiceCommand.classify("Open ITVX"));assertEquals("itvx",VoiceCommand.classify("open ITV X"));assertEquals("freeview",VoiceCommand.classify("Open Freeview Play"));
        try(ActivityController<VoiceActivity> c=Robolectric.buildActivity(VoiceActivity.class).setup()){
            VoiceActivity v=c.get();while(Shadows.shadowOf(v).getNextStartedActivity()!=null){}
            v.askForTest("Open ITVX");assertTrue("voice opens ITVX through Launch",opensItvx(Shadows.shadowOf(v).getNextStartedActivity()));
        }
    }
    private static View described(View v,String start){
        if(v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(start))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View t=described(((ViewGroup)v).getChildAt(i),start);if(t!=null)return t;}
        return null;
    }
}
