package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.Method;

/**
 * HRC-03 (owner's TCL, build run 93, Unified TV not the TV's chosen Home): repeated and long Back never leave the
 * launcher app's Home; the preview, an ordinary app that can never be the Home screen, still closes on Back at Home.
 * Runs in both flavours (run.py --all; CI's testPreviewDebugUnitTest and testLauncherDebugUnitTest).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class BackJourneyTest {
    private ActivityController<UnifiedPreviewActivity> controller;private UnifiedPreviewActivity activity;private View home;private Method key;
    private Context app(){return RuntimeEnvironment.getApplication();}
    private boolean launcher(){return DefaultHome.available(app());}

    @Before public void start()throws Exception{
        app().getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();
        controller=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();activity=controller.get();
        home=RemoteJourneyTest.homeIn(activity);key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
    }
    @After public void stop(){controller.pause().stop().destroy();}
    private void press(int code)throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,code));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,code));}
    /** A held Back: the first press, then the repeats Android sends while it is held, then the release. */
    private void holdBack()throws Exception{
        for(int repeat=0;repeat<12;repeat++)key.invoke(home,new KeyEvent(0,repeat*50L,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,repeat));
        key.invoke(home,new KeyEvent(0,700,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0));
    }
    private Object field(String name)throws Exception{java.lang.reflect.Field f=home.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(home);}
    private String message()throws Exception{return (String)field("message");}

    // --- the launcher app
    @Test public void backAtHomeNeverLeavesTheLauncherApp()throws Exception{
        Assume.assumeTrue("launcher app only",launcher());
        assertFalse("not the TV's chosen Home in this test, as on the owner's TCL",DefaultHome.isDefault(activity));
        press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals(2,field("index"));
        press(KeyEvent.KEYCODE_BACK);
        assertFalse("Back at Home stays in Unified TV",activity.isFinishing());assertEquals("focus goes to the first tile",0,field("index"));
        assertEquals("said once, the first time",UnifiedPreviewActivity.STAYS_OPEN,message());
        assertTrue(app().getSharedPreferences("unified-tv-preview",0).getBoolean(UnifiedPreviewActivity.BACK_HINT,false));
        java.lang.reflect.Field m=home.getClass().getDeclaredField("message");m.setAccessible(true);m.set(home,null);
        for(int i=0;i<20;i++)press(KeyEvent.KEYCODE_BACK);
        assertFalse("twenty more Backs: still Home",activity.isFinishing());assertNull("the hint isn't repeated",message());
        holdBack();assertFalse("a long Back: still Home",activity.isFinishing());
        assertNull("Back never opens anything else",Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void backClosesWhatIsOpenFirstThenStaysOnTheFirstTile()throws Exception{
        Assume.assumeTrue("launcher app only",launcher());
        press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_DOWN);assertEquals(true,field("shelf"));
        press(KeyEvent.KEYCODE_BACK);assertEquals("Films & TV closes first",false,field("shelf"));assertEquals("keeping the tile",1,field("index"));
        press(KeyEvent.KEYCODE_DPAD_UP);assertEquals(true,field("header"));
        holdBack();assertEquals(false,field("header"));assertEquals("a long Back is one Back",1,field("index"));assertFalse(activity.isFinishing());
        press(KeyEvent.KEYCODE_BACK);assertEquals(0,field("index"));assertFalse(activity.isFinishing());
    }
    @Test public void androidsOwnBackNeverFinishesTheLauncherApp()throws Exception{
        Assume.assumeTrue("launcher app only",launcher());
        // Before the window has focus (just back from another app), Back reaches Activity.onBackPressed.
        for(int i=0;i<5;i++)activity.onBackPressed();
        assertFalse(activity.isFinishing());assertEquals(0,field("index"));
    }

    // --- the preview
    @Test public void thePreviewIsAnOrdinaryAppAndClosesOnBackAtHome()throws Exception{
        Assume.assumeFalse("the preview app",launcher());
        press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_BACK);
        assertEquals(false,field("shelf"));assertFalse("Back closes Films & TV first",activity.isFinishing());
        press(KeyEvent.KEYCODE_DPAD_DOWN);holdBack();
        assertEquals(false,field("shelf"));assertFalse("a long Back closes Films & TV only, never the app as well",activity.isFinishing());
        press(KeyEvent.KEYCODE_BACK);
        assertTrue("at Home, Back leaves the preview (it is never the Home screen)",activity.isFinishing());
        assertNotEquals(UnifiedPreviewActivity.STAYS_OPEN,message());
    }

    // --- both
    @Test public void aNotificationsScreenOpensOverHome(){
        Intent settings=new Intent(app(),SettingsActivity.class).putExtra("category",SettingsActivity.NOTIFICATIONS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent[] stack=Launch.overHome(app(),settings);
        assertEquals(2,stack.length);
        assertEquals("Home underneath, so Back from the screen lands on Unified TV",UnifiedPreviewActivity.class.getName(),stack[0].getComponent().getClassName());
        assertTrue((stack[0].getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)!=0);
        assertEquals(SettingsActivity.class.getName(),stack[1].getComponent().getClassName());
        assertEquals((stack[1].getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK),0);
        assertEquals(SettingsActivity.NOTIFICATIONS,stack[1].getIntExtra("category",-1));
    }
}
