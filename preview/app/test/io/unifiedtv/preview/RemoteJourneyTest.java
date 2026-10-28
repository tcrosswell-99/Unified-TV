package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.view.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import java.lang.reflect.Method;
import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class RemoteJourneyTest {
    private ActivityController<UnifiedPreviewActivity> controller;private UnifiedPreviewActivity activity;private View home;private Method key;
    /** An earlier install's dock (HouseholdSettings migration keeps it exactly); a fresh one has none until apps are installed. */
    static final String SEEDED="smartutube,netflix,disney,bbc,live,music";
    @Before public void start()throws Exception{
        Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",SEEDED).commit();
        controller=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();activity=controller.get();
        home=homeIn(activity);
        key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
    }
    @After public void stop(){controller.pause().stop().destroy();}
    private void press(int code)throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,code));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,code));}
    private void hold()throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(560));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));}
    /** Home sits in front of the AmbientLayer: the view that handles the remote. */
    static View homeIn(android.app.Activity a)throws Exception{ViewGroup stack=(ViewGroup)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);for(int i=0;i<stack.getChildCount();i++){for(java.lang.reflect.Method m:stack.getChildAt(i).getClass().getDeclaredMethods())if(m.getName().equals("key"))return stack.getChildAt(i);}throw new AssertionError("no Home view");}
    private Object field(String name)throws Exception{java.lang.reflect.Field f=home.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(home);}
    // Spec 4.6: Down raises Films & TV within Home itself, not a separate-looking screen, and leaves the dock untouched.
    @Test public void downRevealsMediaWithoutChangingDock()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertNull("no separate screen",Shadows.shadowOf(activity).getNextStartedActivity());assertEquals(true,field("shelf"));
        assertEquals(SEEDED,activity.getSharedPreferences("unified-tv-preview",0).getString("dock",""));
    }
    @Test public void upFromFilmsReturnsToTheSameApp()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_RIGHT);int before=(Integer)field("index");
        press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(false,field("shelf"));assertEquals(before,field("index"));assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void backClosesFilmsBeforeAnythingElse()throws Exception{
        int before=(Integer)field("index");press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_BACK);
        assertEquals(false,field("shelf"));assertEquals("Back from Films keeps the dock focus",before,field("index"));
    }
    @Test public void withNoLibraryTheEndCardOpensSetup()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_DOWN);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(started);
        assertEquals(DlnaLibraryActivity.class.getName(),started.getComponent().getClassName());assertTrue(started.getBooleanExtra("setup",false));
    }
    @Test public void moveCancelDoesNotPersistPartialOrder()throws Exception{
        hold();press(KeyEvent.KEYCODE_DPAD_CENTER);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_BACK);
        assertEquals(SEEDED,activity.getSharedPreferences("unified-tv-preview",0).getString("dock",""));
    }
    // Spec 3.3 / v2.3: Home is owner-editable. Removing saves the owner's choice; Cancel changes nothing.
    @Test public void removeFromHomeIsSavedAndNeverUninstalls()throws Exception{
        hold();press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(5,((java.util.List<?>)field("dock")).size());
        assertEquals("netflix,disney,bbc,live,music",activity.getSharedPreferences("unified-tv-preview",0).getString("dock",""));
        assertNull("removing never opens or uninstalls anything",Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void cancelInTheEditMenuChangesNothing()throws Exception{
        hold();for(int i=0;i<3;i++)press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(SEEDED,activity.getSharedPreferences("unified-tv-preview",0).getString("dock",""));assertEquals(6,((java.util.List<?>)field("dock")).size());
    }
    // Owner, 25 October 2026 and v3.4.4 UX-UTILITY-03: a single Up focuses the utility pill directly; no menu opens.
    @Test public void upFocusesThePillDirectlyWithNoMenu()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_UP);assertEquals(true,field("header"));assertEquals(0,field("headerIndex"));
        assertNull("no intermediate menu",ShadowAlertDialog.getLatestAlertDialog());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void thePillHoldsNoProfileAndOkOpensTheFocusedUtility()throws Exception{
        java.util.List<?> pill=(java.util.List<?>)field("pill");assertFalse(pill.contains("profile"));
        assertEquals("no AirPin or NordVPN on this test TV: voice and Settings only",java.util.Arrays.asList("voice","settings"),pill);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);int before=(Integer)field("index");
        press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_DPAD_LEFT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(started);assertEquals(VoiceActivity.class.getName(),started.getComponent().getClassName());
        press(KeyEvent.KEYCODE_DPAD_DOWN);assertEquals(false,field("header"));assertEquals("the dock keeps its tile",before,field("index"));
    }
    @Test public void theGearOpensSettingsDirectly()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals("clamped at the end",1,field("headerIndex"));
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(started);assertEquals(SettingsActivity.class.getName(),started.getComponent().getClassName());
    }
    @Test public void freshInstallWithNothingInstalledHasAnEmptyDockNotPlaceholders()throws Exception{
        activity.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();controller.pause().resume();
        assertEquals(0,((java.util.List<?>)field("dock")).size());assertFalse(activity.getSharedPreferences("unified-tv-preview",0).contains("dock"));
        press(KeyEvent.KEYCODE_DPAD_UP);assertEquals("Settings stays reachable",true,field("header"));
    }
    @Test public void labelsAreFocusedOnlyByDefaultPerProfile()throws Exception{
        Method labels=home.getClass().getDeclaredMethod("labels");labels.setAccessible(true);assertEquals("focused_only",labels.invoke(home));
        activity.getSharedPreferences("unified-tv-preview",0).edit().putString("dock.labelVisibility.adult","always").putString("dock.labelVisibility.child","hidden").commit();
        assertEquals("always",labels.invoke(home));
    }
    @Test public void alarmsOffShowsTheNoticeOnHome()throws Exception{
        assertEquals(false,field("alarmsOff"));
        activity.getSharedPreferences("unified-tv-preview",0).edit().putBoolean(AlarmWatch.SHOWN,false).commit();controller.pause().resume();
        assertEquals(true,field("alarmsOff"));
    }
    @Test public void libraryTileOpensSetupAndHomeKeepsItsPlace()throws Exception{
        activity.getSharedPreferences("unified-tv-preview",0).edit().putString("dock","netflix,library").commit();
        controller.pause().resume();press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1,field("index"));press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(started);
        assertEquals(DlnaLibraryActivity.class.getName(),started.getComponent().getClassName());assertTrue(started.getBooleanExtra("setup",false));
        controller.pause().stop().restart().start().resume();
        assertEquals("focus stays on Library",1,field("index"));assertEquals("library",field("scene"));
    }
    @Test public void moveSavePersistsOrderAndSurvivesRecreation()throws Exception{
        hold();press(KeyEvent.KEYCODE_DPAD_CENTER);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        String saved=activity.getSharedPreferences("unified-tv-preview",0).getString("dock","");assertTrue(saved.startsWith("netflix,smartutube,"));
        controller.recreate();assertEquals(saved,controller.get().getSharedPreferences("unified-tv-preview",0).getString("dock",""));
        View recreated=homeIn(controller.get());
        java.lang.reflect.Field index=recreated.getClass().getDeclaredField("index");index.setAccessible(true);
        assertEquals("The moved shortcut must retain focus after restart",1,index.getInt(recreated));
    }
    @Test public void hundredRapidPressesCannotLaunchAnything()throws Exception{
        for(int i=0;i<100;i++)press(i%2==0?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void liveTvRemainsBlockedAfterNavigation()throws Exception{
        for(int i=0;i<4;i++)press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void menuOpensAllAppsDirectly()throws Exception{
        press(KeyEvent.KEYCODE_MENU);assertNull(ShadowAlertDialog.getLatestAlertDialog());
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(started);assertEquals(AppsActivity.class.getName(),started.getComponent().getClassName());
    }
    @Test public void backLeavesTheTopRowForTheSameTile()throws Exception{
        int before=(Integer)field("index");press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_BACK);
        assertEquals(false,field("header"));assertEquals(before,field("index"));assertFalse(activity.isFinishing());
    }
    // v3.4.6 UX-DOCK-08/09: eight places add empty ones only; focus moves across the household's tiles and skips them.
    @Test public void eightPlacesAddEmptyPlacesThatFocusSkips()throws Exception{
        activity.getSharedPreferences("unified-tv-preview",0).edit().putString(DockCapacity.key("adult"),"8").putInt(DockCapacity.SCHEMA_KEY,1).commit();controller.pause().resume();
        assertEquals(8,field("capacity"));assertEquals("never auto-filled",6,((java.util.List<?>)field("dock")).size());
        for(int i=0;i<10;i++)press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("the last tile, not an empty place",5,field("index"));
        press(KeyEvent.KEYCODE_DPAD_UP);assertEquals("Up still reaches the pill",true,field("header"));press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(SEEDED,activity.getSharedPreferences("unified-tv-preview",0).getString("dock",""));
    }
    @Test public void sixPlacesKeepTheOtherTilesSavedButOffThisHome()throws Exception{
        activity.getSharedPreferences("unified-tv-preview",0).edit().putString("dock",SEEDED+",library").putString(DockCapacity.key("adult"),"6").putInt(DockCapacity.SCHEMA_KEY,1).commit();controller.pause().resume();
        assertEquals(6,((java.util.List<?>)field("dock")).size());
        hold();press(KeyEvent.KEYCODE_DPAD_CENTER);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        String saved=activity.getSharedPreferences("unified-tv-preview",0).getString("dock","");
        assertTrue(saved.startsWith("netflix,smartutube,"));assertTrue("the seventh tile is kept, not dropped",saved.endsWith(",library"));
    }
    @Test public void eachProfileHasItsOwnDockSize()throws Exception{
        activity.getSharedPreferences("unified-tv-preview",0).edit().putString(DockCapacity.key("adult"),"8").putInt(DockCapacity.SCHEMA_KEY,1).putString(Profiles.PREF,Profiles.CHILD).commit();controller.pause().resume();
        assertEquals("Kids keeps the default",6,field("capacity"));
    }
    // SET-010 (in place of the UX-BUILD-01 card): a fresh install opens the guided setup over Home once; Home stays.
    @Test public void aFreshInstallOpensTheGuidedSetupOnce()throws Exception{
        activity.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();controller.pause().resume();
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull("the guided setup opened over Home",started);
        assertEquals(FirstRunActivity.class.getName(),started.getComponent().getClassName());
        assertNull("the whole setup, not one part",started.getStringExtra(FirstRunActivity.ONLY));
        assertFalse("Home itself stays underneath",activity.isFinishing());
        controller.pause().resume();assertNull("never again by itself",Shadows.shadowOf(activity).getNextStartedActivity());
        press(KeyEvent.KEYCODE_DPAD_RIGHT);assertNull("Home's keys open nothing else",Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void kidsIsNeverShownTheGuidedSetup()throws Exception{
        activity.getSharedPreferences("unified-tv-preview",0).edit().clear().putString(Profiles.PREF,Profiles.CHILD).commit();controller.pause().resume();
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void anEarlierInstallIsNotOffered()throws Exception{
        assertNull("a dock of its own: not asked",Shadows.shadowOf(activity).getNextStartedActivity());
        activity.getSharedPreferences("unified-tv-preview",0).edit().clear().putBoolean(HubBuilder.OFFERED,true).commit();controller.pause().resume();
        assertNull("already saw the old Build My TV card: not asked again",Shadows.shadowOf(activity).getNextStartedActivity());
    }
}
