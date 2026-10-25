package io.unifiedtv.preview;

import android.app.AlertDialog;
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
    @Before public void start()throws Exception{
        Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
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
        assertFalse(activity.getSharedPreferences("unified-tv-preview",0).contains("dock"));
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
        assertFalse(activity.getSharedPreferences("unified-tv-preview",0).contains("dock"));
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
        assertFalse(activity.getSharedPreferences("unified-tv-preview",0).contains("dock"));assertEquals(6,((java.util.List<?>)field("dock")).size());
    }
    @Test public void appsIsReachableFromTheControls()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_UP);AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Apps",d.getListView().getAdapter().getItem(0));
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
    @Test public void upOpensNativeControls()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_UP);AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);assertTrue(d.isShowing());assertEquals(5,d.getListView().getCount());
    }
}
