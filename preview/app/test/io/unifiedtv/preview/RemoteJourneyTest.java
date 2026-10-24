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
        home=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
        key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
    }
    @After public void stop(){controller.pause().stop().destroy();}
    private void press(int code)throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,code));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,code));}
    private void hold()throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(560));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));}
    @Test public void downRevealsMediaWithoutChangingDock()throws Exception{
        press(KeyEvent.KEYCODE_DPAD_DOWN);Intent started=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(LibraryActivity.class.getName(),started.getComponent().getClassName());assertTrue(started.getBooleanExtra("shelf",false));
        assertFalse(activity.getSharedPreferences("unified-tv-preview",0).contains("dock"));
    }
    @Test public void moveCancelDoesNotPersistPartialOrder()throws Exception{
        hold();press(KeyEvent.KEYCODE_DPAD_CENTER);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_BACK);
        assertFalse(activity.getSharedPreferences("unified-tv-preview",0).contains("dock"));
    }
    @Test public void moveSavePersistsOrderAndSurvivesRecreation()throws Exception{
        hold();press(KeyEvent.KEYCODE_DPAD_CENTER);press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        String saved=activity.getSharedPreferences("unified-tv-preview",0).getString("dock","");assertTrue(saved.startsWith("netflix,smartutube,"));
        controller.recreate();assertEquals(saved,controller.get().getSharedPreferences("unified-tv-preview",0).getString("dock",""));
        View recreated=((ViewGroup)controller.get().findViewById(android.R.id.content)).getChildAt(0);
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
