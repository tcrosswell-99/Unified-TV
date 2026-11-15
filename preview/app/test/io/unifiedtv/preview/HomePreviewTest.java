package io.unifiedtv.preview;

import android.content.SharedPreferences;
import android.view.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowView;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * SET-GLASS-09 / GLASS-04: Settings is a translucent sheet, so Home underneath is paused, not stopped. An appearance change
 * saved while the sheet is open shows on Home at once (dock size, labels, light, contrast, transparency, text size, pill,
 * clock), with focus where it was and nothing re-laid out or moved. "Paused" here is exactly what the sheet does to Home.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class HomePreviewTest {
    private ActivityController<UnifiedPreviewActivity> controller;private UnifiedPreviewActivity activity;private View home;private Method key;
    private boolean stopped;
    private static SharedPreferences prefs(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0);}
    @Before public void start()throws Exception{
        prefs().edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();
        controller=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();activity=controller.get();
        View content=activity.findViewById(android.R.id.content);
        content.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));content.layout(0,0,1920,1080);
        home=RemoteJourneyTest.homeIn(activity);key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
    }
    @After public void stop(){if(!stopped)controller.pause();controller.stop().destroy();}
    private void press(int code)throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,code));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,code));}
    private Object field(String name)throws Exception{java.lang.reflect.Field f=home.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(home);}
    private Object call(String name)throws Exception{Method m=home.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(home);}
    private static AmbientLayer layer(UnifiedPreviewActivity a){ViewGroup stack=(ViewGroup)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);return (AmbientLayer)stack.getChildAt(0);}
    /** The sheet slides over Home: Home is paused and keeps its window. */
    private ShadowView sheetOver(){controller.pause();ShadowView v=Shadows.shadowOf(home);v.clearWasInvalidated();v.setDidRequestLayout(false);return v;}
    private void stillInPlace(ShadowView v){
        assertFalse("nothing re-laid out",v.didRequestLayout());
        assertEquals(0,home.getLeft());assertEquals(0,home.getTop());assertEquals(1920,home.getWidth());assertEquals(1080,home.getHeight());
        assertEquals("Home never moves",0f,home.getTranslationX(),0f);assertEquals(0f,home.getTranslationY(),0f);assertEquals(1f,home.getScaleX(),0f);
    }

    @Test public void labelsContrastTransparencyAndTextSizeRepaintUnderTheSheet()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_RIGHT);int focus=(Integer)field("index");
        ShadowView v=sheetOver();
        prefs().edit().putString(DockLabels.key(Profiles.ADULT),DockLabels.ALWAYS).apply();
        assertEquals(DockLabels.ALWAYS,call("labels"));assertTrue("Home redraws with every label",v.wasInvalidated());
        for(String[] change:new String[][]{{"highContrast","b"},{"opaque","b"},{Accessibility.TEXT_SIZE,Accessibility.LARGEST}}){
            v.clearWasInvalidated();
            if(change[1].equals("b"))prefs().edit().putBoolean(change[0],true).apply();else prefs().edit().putString(change[0],change[1]).apply();
            assertTrue(change[0]+" repaints Home",v.wasInvalidated());
        }
        stillInPlace(v);assertEquals("focus stays on the same tile",focus,field("index"));
        v.clearWasInvalidated();prefs().edit().putString("householdLastEvent","person").apply();
        assertFalse("a setting that isn't about Home's look leaves it alone",v.wasInvalidated());
        controller.resume();
    }
    @Test public void dockSizeAndProfileShowAtOnceAndFocusStaysOnTheSameApp()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_RIGHT);
        ShadowView v=sheetOver();
        prefs().edit().putString(DockCapacity.key(Profiles.ADULT),"8").putInt(DockCapacity.SCHEMA_KEY,DockCapacity.SCHEMA).apply();
        assertEquals("eight places behind the sheet",8,field("capacity"));assertEquals("never auto-filled",6,((java.util.List<?>)field("dock")).size());
        assertEquals("the same app keeps focus",2,field("index"));assertTrue(v.wasInvalidated());stillInPlace(v);
        // Profiles & Family > Switch profile (or Ask your TV) while the sheet is open: Kids' own size and labels appear.
        prefs().edit().putString(Profiles.PREF,Profiles.CHILD).putString(DockLabels.key(Profiles.CHILD),DockLabels.HIDDEN).apply();
        assertEquals("Kids keeps six",6,field("capacity"));assertEquals(DockLabels.HIDDEN,call("labels"));
        prefs().edit().putString(Profiles.PREF,Profiles.ADULT).apply();assertEquals(8,field("capacity"));assertEquals(DockLabels.FOCUSED_ONLY,call("labels"));
        // Fewer tiles (an 8 → 6 shrink chose the focused one to leave): focus moves to a tile that is still there.
        prefs().edit().putString("dock","smartutube,netflix").apply();
        assertEquals(2,((java.util.List<?>)field("dock")).size());assertEquals(1,field("index"));assertEquals("netflix",field("scene"));
        controller.resume();assertEquals(8,field("capacity"));
    }
    @Test public void motionAndLightweightChangeTheLightAtOnce()throws Exception{
        AmbientLayer light=layer(activity);sheetOver();
        assertFalse("the light rests while the sheet is over Home",light.drifting());
        prefs().edit().putBoolean("lightweight",true).apply();assertEquals("Lightweight: the still frames instead",View.GONE,light.getVisibility());
        prefs().edit().putBoolean("lightweight",false).apply();assertEquals(View.VISIBLE,light.getVisibility());
        assertTrue("motion on again: the light drifts behind the sheet as a preview",light.drifting());
        prefs().edit().putBoolean("reduceMotion",true).apply();assertFalse("Reduce motion stops it at once",light.drifting());
        prefs().edit().putBoolean("reduceMotion",false).apply();assertTrue(light.drifting());
        prefs().edit().putBoolean("stillBackground",true).apply();assertFalse("Keep still stops it at once",light.drifting());
        controller.resume();assertFalse("still after closing too",light.drifting());
    }
    @Test public void thePillAndTheClockFollowTheirSettings()throws Exception{
        sheetOver();
        prefs().edit().putString(HomeUtilities.PREF,"settings,voice").apply();
        assertEquals(Arrays.asList("settings","voice"),field("pill"));
        prefs().edit().putString(HouseholdSettings.ZONE,"Pacific/Auckland").apply();
        assertEquals(java.time.ZoneId.of("Pacific/Auckland"),field("zone"));
        controller.resume();
    }
    @Test public void aClearedFileIsAppliedWhole()throws Exception{
        prefs().edit().putString(DockCapacity.key(Profiles.ADULT),"8").putInt(DockCapacity.SCHEMA_KEY,DockCapacity.SCHEMA).commit();controller.pause().resume();
        assertEquals(8,field("capacity"));
        sheetOver();Method preview=home.getClass().getDeclaredMethod("preview",String.class);preview.setAccessible(true);
        prefs().edit().remove(DockCapacity.key(Profiles.ADULT)).remove(DockCapacity.SCHEMA_KEY).commit();preview.invoke(home,(Object)null);
        assertEquals("no key: everything again",6,field("capacity"));
        controller.resume();
    }
    @Test public void stoppedHomeWaitsAndCatchesUpWhenSeenAgain()throws Exception{
        controller.pause().stop();stopped=true;
        prefs().edit().putString(DockCapacity.key(Profiles.ADULT),"8").putInt(DockCapacity.SCHEMA_KEY,DockCapacity.SCHEMA).apply();
        assertEquals("not listening while another app fills the screen",6,field("capacity"));
        controller.restart().start().resume();stopped=false;assertEquals(8,field("capacity"));
    }
    @Test public void homesOwnEditsAreNotReloadedUnderIt()throws Exception{
        // Hold OK > Move, Right, OK saves the order on Home itself: focus follows the moved tile, not a reload.
        key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(560));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
        press(KeyEvent.KEYCODE_DPAD_CENTER);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("netflix,smartutube,disney,bbc,live,music",prefs().getString("dock",""));assertEquals(1,field("index"));
        assertEquals("smartutube",((java.util.List<?>)field("dock")).stream().map(o->{try{java.lang.reflect.Field f=o.getClass().getDeclaredField("id");f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}).toArray()[1]);
    }
    @Test public void theKeysAreSortedByWhatTheyChange(){
        assertEquals(HomePreview.ALL,HomePreview.of(null));
        assertEquals(HomePreview.DOCK,HomePreview.of("dock"));assertEquals(HomePreview.DOCK,HomePreview.of(Profiles.PREF));assertEquals(HomePreview.DOCK,HomePreview.of(DockCapacity.key(Profiles.CHILD)));
        for(String k:AmbientLayer.MOTION_KEYS)assertEquals(k,HomePreview.LIGHT,HomePreview.of(k));
        assertEquals(HomePreview.PILL,HomePreview.of(HomeUtilities.PREF));assertEquals(HomePreview.CLOCK,HomePreview.of(HouseholdSettings.ZONE));
        assertEquals(HomePreview.LOOK,HomePreview.of(DockLabels.key(Profiles.ADULT)));assertEquals(HomePreview.LOOK,HomePreview.of("opaque"));
        for(String k:new String[]{"focus","scene","stillExplained","pin","wallpaperSource"})assertEquals(k,HomePreview.NONE,HomePreview.of(k));
    }
}
