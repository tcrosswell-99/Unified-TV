package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import java.lang.reflect.Method;
import java.util.List;

/**
 * GLASS-02 / SET-GLASS-06: closing Settings puts focus back on the gear in Home's pill (the Home button still lands on the
 * dock). Module 05 single focus timing contract / NAV-04: a scene's crossfade starts at once on a focus move (no settle
 * wait, none before Films & TV's scene on Down either), crossfades stay within 400 ms (Lightweight shorter, Reduce motion
 * none), and Down never shows a loading screen.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class HomeFocusTest {
    private ActivityController<UnifiedPreviewActivity> controller;private UnifiedPreviewActivity activity;private View home;private Method key;
    private static SharedPreferences prefs(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0);}
    @Before public void start()throws Exception{
        prefs().edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();
        controller=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();activity=controller.get();
        home=RemoteJourneyTest.homeIn(activity);key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
    }
    @After public void stop(){controller.pause().stop().destroy();}
    private void press(int code)throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,code));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,code));}
    private Object field(String name)throws Exception{java.lang.reflect.Field f=home.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(home);}
    private long fadeMs()throws Exception{Method m=home.getClass().getDeclaredMethod("fadeMs");m.setAccessible(true);return (Long)m.invoke(home);}
    private String focusedUtility()throws Exception{if(!(Boolean)field("header"))return null;return (String)((List<?>)field("pill")).get((Integer)field("headerIndex"));}
    /** Up to the pill, along to the gear, OK: Settings opens over Home (which is then paused under the sheet). */
    private void openSettingsFromTheGear()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_UP);for(int i=0;i<4;i++)press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("settings",focusedUtility());press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull("Settings opened",started);
        assertEquals(SettingsActivity.class.getName(),started.getComponent().getClassName());
        controller.pause();
    }

    // --- GLASS-02 / SET-GLASS-06
    @Test public void closingSettingsReturnsFocusToTheGear()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_RIGHT);int tile=(Integer)field("index");
        openSettingsFromTheGear();
        controller.resume();   // Back from the Settings root closes the sheet
        assertEquals("focus is on the gear again",HomeUtilities.SETTINGS,focusedUtility());
        press(KeyEvent.KEYCODE_DPAD_DOWN);assertEquals("and Down still returns to the same tile",tile,field("index"));assertEquals(false,field("header"));
    }
    @Test public void theGearKeepsFocusWhenSettingsMovedIt()throws Exception{
        openSettingsFromTheGear();
        // Navigation & Shortcuts, applied inside Settings: Settings first, then voice.
        prefs().edit().putString(HomeUtilities.PREF,"settings,voice").apply();
        controller.resume();
        assertEquals(0,field("headerIndex"));assertEquals(HomeUtilities.SETTINGS,focusedUtility());
    }
    @Test public void theHomeButtonFromSettingsLandsOnTheDock()throws Exception{
        openSettingsFromTheGear();
        Method newIntent=UnifiedPreviewActivity.class.getDeclaredMethod("onNewIntent",Intent.class);newIntent.setAccessible(true);
        newIntent.invoke(activity,new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        controller.resume();
        assertNull("the Home button: the dock, not the gear",focusedUtility());
    }
    @Test public void aReturnFromAnotherUtilityIsNotTheGear()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_UP);assertEquals("voice",focusedUtility());press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(VoiceActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        controller.pause().resume();assertEquals("still on voice, where it was",HomeUtilities.VOICE,focusedUtility());
    }
    @Test public void kidsWithAPinAreAskedBeforeSettings()throws Exception{
        prefs().edit().putString("pin",Pins.record("2468")).putString(Profiles.PREF,Profiles.CHILD).commit();controller.pause().resume();
        press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertNull("nothing opens without the PIN",Shadows.shadowOf(activity).getNextStartedActivity());
        AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);assertTrue(d.isShowing());
        Pins.type(d,"2468");
        assertEquals(SettingsActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        controller.pause().resume();assertEquals(HomeUtilities.SETTINGS,focusedUtility());
    }
    @Test public void kidsWithNoPinOnRecordCanStillReachSettings()throws Exception{
        prefs().edit().putString(Profiles.PREF,Profiles.CHILD).commit();controller.pause().resume();
        press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("the gear is never dead: Settings > Profiles & Family can return to Adults (Profiles.rule)",started);
        assertEquals(SettingsActivity.class.getName(),started.getComponent().getClassName());
        assertEquals(Profiles.ALLOWED,Profiles.rule(Profiles.CHILD,Profiles.ADULT,false));
    }

    // --- focus timing (module 05, NAV-04)
    @Test public void downRevealsFilmsAndStartsItsSceneAtOnceWithNoLoadingScreen()throws Exception{
        assertEquals("smartutube",field("scene"));
        long before=SystemClock.uptimeMillis();press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("the row is up straight away",true,field("shelf"));assertEquals("its slide starts on the key",before,(long)(Long)field("shelfAt"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();   // no time passes
        assertEquals("the scene changed with no settle wait","neutral",field("scene"));assertEquals("Set up Your Films & TV",field("sceneTitle"));
        assertEquals("its crossfade began on the key",before,(long)(Long)field("fadeAt"));
        assertNull("no loading screen, no other page",Shadows.shadowOf(activity).getNextStartedActivity());
        press(KeyEvent.KEYCODE_DPAD_UP);Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("back on the same app at once","smartutube",field("scene"));
    }
    @Test public void aDockMoveStartsItsCrossfadeAtOnce()throws Exception{
        prefs().edit().putBoolean("lightweight",true).commit();controller.pause().resume();
        long before=SystemClock.uptimeMillis();press(KeyEvent.KEYCODE_DPAD_RIGHT);Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("netflix",field("scene"));assertEquals(before,(long)(Long)field("fadeAt"));
    }
    @Test public void crossfadesStayWithinFourHundredMilliseconds()throws Exception{
        assertEquals(400,fadeMs());assertTrue(AmbientLayer.FADE_MS<=400);
        java.lang.reflect.Field shelf=home.getClass().getDeclaredField("SHELF_MS");shelf.setAccessible(true);assertTrue("the row's slide",(Long)shelf.get(null)<=400);
        prefs().edit().putBoolean("lightweight",true).commit();long light=fadeMs();assertTrue("Lightweight: shorter, never longer",light>0&&light<=400);
        prefs().edit().putBoolean("reduceMotion",true).commit();assertEquals("Reduce motion: no crossfade",0,fadeMs());
        assertTrue("Settings' own motion stays inside 400 ms too",SettingsOverlay.OPEN_MS<=400&&SettingsOverlay.CROSSFADE_MS<=400);
    }
}
