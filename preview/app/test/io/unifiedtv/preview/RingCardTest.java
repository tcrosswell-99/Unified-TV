package io.unifiedtv.preview;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.shadows.ShadowWindowManagerImpl;
import java.time.Duration;
import java.util.*;

/**
 * RING-02 / RING-03 on screen (HouseholdService's own overlay windows, driven by its in-app TEST events, no Home
 * Assistant): the person toast (about 5 s, never focusable or touchable, never over an open doorbell card or an
 * emergency) and the doorbell card (takes the remote's focus, Back closes it and nothing reaches the screen underneath,
 * which keeps its own focus; never over an emergency, and an alarm closes it). What a real TV's window manager does with
 * focus after the card's window goes is for the TV check (register RING-03).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class RingCardTest {
    Context app;ServiceController<HouseholdService> service;WindowManager windows;
    @Before public void start(){
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();
        ShadowSettings.setCanDrawOverlays(true);
        // As Robolectric.buildService does (written out so the stand-in compile check's Robolectric stub needn't have it).
        service=ServiceController.of(new HouseholdService(),new Intent(app,HouseholdService.class)).create();windows=(WindowManager)service.get().getSystemService(Context.WINDOW_SERVICE);
    }
    @After public void stop(){service.destroy();ShadowSettings.setCanDrawOverlays(false);}
    private void test(String kind,String... state){
        Intent i=new Intent(app,HouseholdService.class).setAction("simulate").putExtra("kind",kind);if(state.length>0)i.putExtra("state",state[0]).putExtra("name","Hallway");
        service.withIntent(i).startCommand(0,1);idle(0);
    }
    private static void idle(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private List<View> overlays(){return new ArrayList<>(((ShadowWindowManagerImpl)Shadow.extract(windows)).getViews());}
    private TextView toast(){for(View v:overlays())if(v instanceof TextView&&((TextView)v).getText().toString().contains("Person at"))return (TextView)v;return null;}
    private View card(){for(View v:overlays())if(v instanceof android.widget.FrameLayout&&!(v instanceof android.widget.ScrollView)&&v.isFocusable())return v;return null;}
    private View alarm(){for(View v:overlays())if(v instanceof android.widget.ScrollView)return v;return null;}
    private static int flags(View v){return ((WindowManager.LayoutParams)v.getLayoutParams()).flags;}
    private static boolean back(View card){return card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK))&card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));}

    @Test public void aPersonGetsAShortToastThatNeverTakesFocus(){
        test("person");TextView t=toast();assertNotNull("the person toast",t);
        assertEquals(DoorbellCamera.person("Doorbell",true),t.getText().toString());
        assertTrue("never focusable",(flags(t)&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);assertTrue("never touchable",(flags(t)&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
        assertNull("no camera for a person",card());
        idle(AlertPolicy.PERSON_TOAST_MS-200);assertNotNull("still there just before five seconds",toast());
        idle(400);assertNull("gone after about five seconds",toast());
    }
    @Test public void aDoorbellPressShowsTheCardBackClosesItAndHomeKeepsItsFocus()throws Exception{
        ActivityController<UnifiedPreviewActivity> home=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();
        try{View surface=RemoteJourneyTest.homeIn(home.get());java.lang.reflect.Method key=surface.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
            key.invoke(surface,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));key.invoke(surface,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));
            java.lang.reflect.Field index=surface.getClass().getDeclaredField("index");index.setAccessible(true);
            test("door");View c=card();assertNotNull("the doorbell card",c);
            assertEquals("the remote's focus goes to the card",0,flags(c)&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            assertTrue("its own name, clearly a test",LibraryJourneyTest.all(c,TextView.class).stream().anyMatch(t->t.getText().toString().equals(DoorbellCamera.title("Doorbell",true))));
            assertTrue("no camera set up: said plainly",LibraryJourneyTest.all(c,TextView.class).stream().anyMatch(t->t.getText().toString().startsWith("Live view unavailable")));
            home.pause();   // the card's window is over Home
            assertTrue("Back is the card's",back(c));
            assertNull("Back closed it",card());
            home.resume();
            assertFalse("Back never reached Home (which would have left it)",home.get().isFinishing());
            assertEquals("Home's focus is where it was",1,index.get(surface));
        }finally{home.pause().stop().destroy();}
    }
    @Test public void theCardClosesByItselfAfterTheHouseholdsTime(){
        test("door");assertNotNull(card());idle(29_000);assertNotNull("30 s by default",card());idle(1_500);assertNull(card());
    }
    @Test public void aPersonToastNeverCoversAnOpenCard(){
        test("door");assertNotNull(card());idle(1000);test("person");assertNull("no toast over the doorbell card",toast());
    }
    @Test public void neitherEverCoversAnEmergency(){
        test("smoke","on");assertNotNull("the alarm screen",alarm());
        idle(1000);test("person");assertNull("no person toast during an alarm",toast());
        idle(1000);test("door");assertNull("no doorbell card during an alarm",card());
        assertTrue("the press is noted on the alarm screen",LibraryJourneyTest.all(alarm(),TextView.class).stream().anyMatch(t->t.getText().toString().startsWith("Doorbell rang at")));
        idle(1000);test("end");assertNull("alarm over",alarm());
        assertNotNull("then the doorbell, still relevant, is shown",card());
    }
    @Test public void anAlarmClosesAnOpenCard(){
        test("door");assertNotNull(card());idle(1000);
        test("carbon_monoxide","on");assertNull("the card gives way",card());assertNotNull(alarm());
    }
    @Test public void kidsCanBeSparedPersonToasts(){
        app.getSharedPreferences("unified-tv-preview",0).edit().putString(Profiles.PREF,Profiles.CHILD).putBoolean(AlertPolicy.KIDS_PERSON_PREF,false).commit();
        test("person");assertNull("RING-02a: off in Kids",toast());
        idle(1000);test("door");assertNotNull("a doorbell press still shows in Kids",card());
    }
}
