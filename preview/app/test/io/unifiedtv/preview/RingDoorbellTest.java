package io.unifiedtv.preview;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.shadows.ShadowWindowManagerImpl;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Owner on the Sony (run 106, 3 November 2026): Home Assistant connected, Ring installed, and the doorbell list showed only
 * Alexa's "Amazon Kids" switches. Ring's real entities as Home Assistant publishes them (checked against the integration's
 * source, home-assistant/core homeassistant/components/ring: event.<device>_ding with class doorbell and event type "ding" up
 * to 2026.4 and "ring" from 2026.5; event.<device>_motion; camera.<device>_live_view and _last_recording; attribution "Data
 * provided by Ring.com"), the rebuilt "Doorbell, alarms & this TV" page, its lists inside the same sheet, and a ding through
 * HouseholdService's real path. Household names here are symbolic.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class RingDoorbellTest {
    static final String RING="Data provided by Ring.com";
    private static JSONObject state(String id,String state,String name,String deviceClass,String attribution,String... eventTypes)throws Exception{
        JSONObject attr=new JSONObject().put("friendly_name",name);if(deviceClass!=null)attr.put("device_class",deviceClass);if(attribution!=null)attr.put("attribution",attribution);
        if(eventTypes.length>0)attr.put("event_types",new JSONArray(eventTypes));
        return new JSONObject().put("entity_id",id).put("state",state).put("attributes",attr);
    }
    /** The owner's house as Home Assistant 2026.11 shows it: one Ring doorbell, Alexa Media Player's Amazon Kids, alarms, scripts. */
    static JSONArray house(boolean ring)throws Exception{
        JSONArray a=new JSONArray();
        if(ring){
            a.put(state("event.front_door_ding","2026-11-03T09:00:00.000+00:00","Front Door Ding","doorbell",RING,"ring"));
            a.put(state("event.front_door_motion","unknown","Front Door Motion","motion",RING,"motion"));
            a.put(state("camera.front_door_live_view","idle","Front Door Live view",null,RING));
            a.put(state("camera.front_door_last_recording","idle","Front Door Last recording",null,RING));
            a.put(state("sensor.front_door_battery","80","Front Door Battery","battery",RING));
        }
        for(String echo:new String[]{"anushka_s_echo","arch_echo","big_echo_studio"}){String n=HaEntities.pretty(echo);
            a.put(state("switch."+echo+"_amazon_kids","off",n+" Amazon Kids",null,null));
            a.put(state("binary_sensor."+echo+"_amazon_kids","off",n+" Amazon Kids",null,null));}
        a.put(state("binary_sensor.kitchen_smoke","off","Kitchen smoke alarm","smoke",null));
        a.put(state("binary_sensor.hall_co","off","Hall carbon monoxide","carbon_monoxide",null));
        a.put(state("script.good_morning","off","Good morning",null,null));
        a.put(state("script.xbox_on","off","Xbox on",null,null));
        a.put(state("scene.xbox_gaming","2026-11-01T20:00:00+00:00","Xbox gaming",null,null));
        a.put(state("button.xbox_power","unknown","Xbox power",null,null));
        a.put(state("switch.xbox_plug","off","Xbox plug",null,null));
        return a;
    }
    private static List<String> ids(List<HaEntities.Candidate> l){List<String> out=new ArrayList<>();for(HaEntities.Candidate c:l)out.add(c.id);return out;}
    private static List<String> labels(List<HaEntities.Candidate> l){List<String> out=new ArrayList<>();for(HaEntities.Candidate c:l)out.add(c.label);return out;}

    // --- What Home Assistant's Ring integration publishes, found and labelled

    @Test public void ringsOwnEntitiesAreFoundFirstInPlainWords()throws Exception{
        List<HaEntities.Entity> all=HaEntities.parse(house(true).toString());
        HaEntities.Choices door=HaEntities.choices("door",all,"");
        assertEquals(List.of("Front Door — doorbell press"),labels(door.ring));assertTrue(door.strong.isEmpty());
        assertEquals("one doorbell: chosen by itself","event.front_door_ding",door.auto().id);assertEquals("Front Door",door.auto().device);
        HaEntities.Choices person=HaEntities.choices("person",all,"event.front_door_ding");
        assertEquals(List.of("Front Door — motion"),labels(person.ring));assertNull("motion is optional: never chosen by itself",person.auto());
        HaEntities.Choices camera=HaEntities.choices("camera",all,"event.front_door_ding");
        assertEquals(List.of("Front Door — live view"),labels(camera.ring));assertEquals("Front Door live view",camera.auto().value);
        assertTrue("a recording is a still, never the live picture",labels(camera.other).contains("Front Door — last recording"));
        // Home Assistant up to 2026.4 said "ding"; a doorbell from another maker says doorbell.
        JSONArray older=new JSONArray().put(state("event.porch_ding","unknown","Porch Ding",null,RING,"ding")).put(state("event.gate_bell","unknown","Gate Bell","doorbell",null,"ring"));
        HaEntities.Choices both=HaEntities.choices("door",HaEntities.parse(older.toString()),"");
        assertEquals(List.of("event.porch_ding"),ids(both.ring));assertEquals(List.of("event.gate_bell"),ids(both.strong));assertNull("two doorbells: the household chooses",both.auto());
    }
    @Test public void amazonKidsSwitchesAreNeverListedByDefault()throws Exception{
        for(boolean ring:new boolean[]{true,false})for(String role:new String[]{"door","person","camera"}){
            HaEntities.Choices c=HaEntities.choices(role,HaEntities.parse(house(ring).toString()),"");
            for(HaEntities.Candidate x:c.best())assertFalse(role+": "+x.label,x.label.contains("Amazon Kids"));
            for(HaEntities.Candidate x:c.other)assertFalse("a switch is never a doorbell, even under Show everything",x.id.startsWith("switch."));
        }
        assertTrue("No Ring: nothing to offer",HaEntities.choices("door",HaEntities.parse(house(false).toString()),"").best().isEmpty());
    }
    @Test public void olderBinarySensorsStillWork()throws Exception{
        JSONArray old=new JSONArray().put(state("binary_sensor.front_door_ding","off","Front Door Ding","occupancy",RING))
            .put(state("binary_sensor.front_door_motion","off","Front Door Motion","motion",RING));
        List<HaEntities.Entity> all=HaEntities.parse(old.toString());
        assertEquals("binary_sensor.front_door_ding",HaEntities.choices("door",all,"").auto().id);
        assertEquals(List.of("Front Door — motion"),labels(HaEntities.choices("person",all,"").ring));
        assertEquals(AlertPolicy.RING_PRESS,AlertPolicy.ringKind("binary_sensor.front_door_ding","binary_sensor.front_door_ding","","off","on",null));
        // Both kinds on one doorbell (Home Assistant 2024.x): the event is the one, so it is still one clear answer.
        old.put(state("event.front_door_ding","unknown","Front Door Ding","doorbell",RING,"ding"));
        assertEquals("event.front_door_ding",HaEntities.choices("door",HaEntities.parse(old.toString()),"").auto().id);
    }
    @Test public void ringThatIsKnownButNotLoadedIsNamed()throws Exception{
        JSONObject ghost=state("event.front_door_ding","unavailable","Front Door Ding",null,null);ghost.getJSONObject("attributes").put("restored",true);
        List<HaEntities.Entity> all=HaEntities.parse(new JSONArray().put(ghost).toString());
        assertEquals(List.of("Front Door"),HaEntities.ringNotAnswering(all));assertTrue(HaEntities.choices("door",all,"").best().isEmpty());
        assertTrue(HouseholdPage.status(true,HaStatus.AUTHORISED,false,null,false,false,HaEntities.ringNotAnswering(all)).startsWith("Home Assistant knows Front Door but isn't hearing from Ring"));
    }
    @Test public void theXboxListOffersScriptsScenesAndButtonsXboxFirst()throws Exception{
        HaEntities.Choices x=HaEntities.choices("xbox",HaEntities.parse(house(true).toString()),"");
        assertEquals("by name",List.of("scene.xbox_gaming","script.xbox_on","button.xbox_power"),ids(x.strong));
        assertEquals(List.of("script.good_morning"),ids(x.other));
        for(String ok:new String[]{"script.xbox_on","scene.xbox_gaming","button.xbox_power"})assertTrue(HaEntities.valid("xbox",ok));
        assertFalse(HaEntities.valid("xbox","switch.xbox_plug"));
        assertEquals("/api/services/scene/turn_on",HaEntities.service("scene.xbox_gaming"));assertEquals("/api/services/button/press",HaEntities.service("button.xbox_power"));
        assertNull(HaEntities.service("switch.xbox_plug"));
    }
    @Test public void theStatusCardSaysWhatHappensInEveryState(){
        assertTrue(HouseholdPage.status(false,null,false,null,false,false,null).startsWith("Home Assistant isn't set up on this TV yet."));
        assertEquals("Checking Home Assistant…",HouseholdPage.status(true,null,false,null,false,false,null));
        assertTrue(HouseholdPage.status(true,HaStatus.OFFLINE,false,null,false,false,null).endsWith("The doorbell can't show here until it is connected again."));
        assertEquals("Home Assistant is connected. When someone rings Front Door, this TV shows who's there. Nothing else to set up in Home Assistant.",
            HouseholdPage.status(true,HaStatus.AUTHORISED,false,"Front Door",true,true,null));
        assertEquals("Your Ring doorbell isn't set up on this TV yet. Choose it below; nothing to do in Home Assistant.",HouseholdPage.status(true,HaStatus.AUTHORISED,false,null,true,true,null));
        assertEquals(HouseholdPage.NO_RING,HouseholdPage.status(true,HaStatus.AUTHORISED,false,null,false,false,null));
        assertEquals("On — found 2 alarms in Home Assistant",HouseholdPage.alarmRow(true,2));assertEquals("On — found 1 alarm in Home Assistant",HouseholdPage.alarmRow(true,1));
    }

    // --- The page and its lists, inside the Settings sheet

    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static void idle(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private static View root(SettingsActivity a){return a.findViewById(android.R.id.content);}
    private static List<TextView> texts(View v){List<TextView> out=new ArrayList<>();if(v instanceof TextView&&v.isShown())out.add((TextView)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(texts(((ViewGroup)v).getChildAt(i)));return out;}
    private static Button button(View v,String start){for(TextView t:texts(v))if(t instanceof Button&&t.getText().toString().startsWith(start))return (Button)t;return null;}
    private static boolean shows(View v,String words){for(TextView t:texts(v))if(t.getText().toString().contains(words))return true;return false;}
    private Context app;
    @Before public void start()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().putString("room","sony").commit();
        Vault.testKey=VaultTest.aes();new Vault(app).save("ha",new JSONObject().put("enabled",true).put("base","https://ha.example.invalid").put("token","t"));
        ShadowSettings.setCanDrawOverlays(true);
    }
    @After public void done(){HouseholdPage.snapshotForTests=null;Vault.testKey=null;ShadowSettings.setCanDrawOverlays(false);}
    private ActivityController<SettingsActivity> page(boolean ring)throws Exception{
        HouseholdPage.snapshotForTests=new HaEntities.Snapshot(HaStatus.AUTHORISED,HaEntities.parse(house(ring).toString()),false);
        Intent i=new Intent(app,SettingsActivity.class).putExtra("category",SettingsActivity.NOTIFICATIONS).putExtra("task",SettingsActivity.HOUSEHOLD);
        ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class,i).setup();
        for(int n=0;n<200&&shows(root(c.get()),"Checking Home Assistant");n++){Thread.sleep(10);idle();}
        assertFalse("Home Assistant read",shows(root(c.get()),"Checking Home Assistant"));return c;
    }
    private static HouseholdPage household(SettingsActivity a)throws Exception{
        java.lang.reflect.Field f=SettingsActivity.class.getDeclaredField("setup");f.setAccessible(true);return ((SetupPages)f.get(a)).household;
    }

    @Test public void theSingleRingDoorbellIsChosenAndTicked()throws Exception{
        try(ActivityController<SettingsActivity> c=page(true)){SettingsActivity a=c.get();
            assertTrue(shows(root(a),"Home Assistant is connected. When someone rings Front Door, this TV shows who's there."));
            assertNotNull(button(root(a),"Doorbell press: Front Door"+HouseholdPage.TICK));
            assertNotNull("motion is the household's choice",button(root(a),"Motion at the door (optional): Off"));
            assertNotNull(button(root(a),"Picture: Front Door live view"+HouseholdPage.TICK));
            for(String line:new String[]{HouseholdPage.DOOR_LINE,HouseholdPage.PERSON_LINE,HouseholdPage.CAMERA_LINE,HouseholdPage.XBOX_LINE,HouseholdPage.ROOM_LINE})assertTrue(line,shows(root(a),line));
            assertNotNull(button(root(a),"On — found 2 alarms in Home Assistant"));assertTrue(shows(root(a),"Kitchen smoke alarm, Hall carbon monoxide"));
            assertNotNull(button(root(a),"Turn on the Xbox from this TV: "+HouseholdPage.XBOX_NOT_SET));
            for(TextView t:texts(root(a))){String s=t.getText().toString().toLowerCase(Locale.ROOT);
                for(String never:new String[]{"none chosen","entity","binary sensor","binary_sensor","event."})assertFalse(never+" in '"+s+"'",s.contains(never));}
        }
    }
    @Test public void aListIsOneSolidPanelInsideTheSheetAndBackReturnsToItsRow()throws Exception{
        try(ActivityController<SettingsActivity> c=page(true)){SettingsActivity a=c.get();
            Button row=button(root(a),"Doorbell press:");row.requestFocus();row.performClick();idle();
            assertEquals("door",household(a).picking());
            assertNull("no dialog over the sheet",ShadowAlertDialog.getLatestAlertDialog());assertTrue("the sheet itself shows the list",a.panelShown());
            assertTrue(shows(root(a),"Ring doorbells"));
            Button chosen=button(root(a),"Front Door — doorbell press"+HouseholdPage.TICK);assertNotNull("the current choice, ticked",chosen);
            assertSame("focus on the current choice",chosen,a.getCurrentFocus());
            assertFalse("the page's rows are gone: one panel",shows(root(a),HouseholdPage.ALARM_LINE));
            assertFalse("Amazon Kids hidden",shows(root(a),"Amazon Kids"));assertFalse("no technical names",shows(root(a),"event.front_door_ding"));
            button(root(a),"Show everything").performClick();idle();
            assertTrue("everything, on request",shows(root(a),"Amazon Kids"));
            assertTrue("technical names only here",shows(root(a),"binary_sensor.arch_echo_amazon_kids"));assertFalse("never a switch",shows(root(a),"switch."));
            a.onBackPressed();idle();
            assertNull(household(a).picking());assertEquals(SettingsActivity.HOUSEHOLD,a.overlay().task());
            assertTrue("focus back on the row that opened the list",((TextView)a.getCurrentFocus()).getText().toString().startsWith("Doorbell press:"));
        }
    }
    @Test public void noDoorbellSaysHowToAddRing()throws Exception{
        try(ActivityController<SettingsActivity> c=page(false)){SettingsActivity a=c.get();
            assertTrue(shows(root(a),HouseholdPage.NO_RING));assertNotNull(button(root(a),"Doorbell press: "+HouseholdPage.NOT_SET));
            button(root(a),"Doorbell press:").performClick();idle();
            assertTrue("the list says it too",shows(root(a),HouseholdPage.NO_RING));assertFalse(shows(root(a),"Amazon Kids"));
        }
    }
    @Test public void theXboxListPutsXboxFirst()throws Exception{
        try(ActivityController<SettingsActivity> c=page(true)){SettingsActivity a=c.get();
            button(root(a),"Turn on the Xbox from this TV:").performClick();idle();
            List<String> rows=new ArrayList<>();for(TextView t:texts(root(a)))if(t instanceof Button)rows.add(t.getText().toString());
            int power=rows.indexOf("Xbox power"),on=rows.indexOf("Xbox on"),scene=rows.indexOf("Xbox gaming");
            assertTrue(rows.toString(),power>=0&&on>=0&&scene>=0);assertFalse("the rest waits for Show everything",rows.contains("Good morning"));
            assertTrue(rows.indexOf("Show everything")>power);
            button(root(a),"Xbox on").performClick();idle();
            assertNull(household(a).picking());assertNotNull(button(root(a),"Turn on the Xbox from this TV: Xbox on"+HouseholdPage.TICK));
        }
    }

    // --- The tests on the page, and a real ding, through HouseholdService's own path

    private List<View> overlays(ServiceController<HouseholdService> s){return new ArrayList<>(((ShadowWindowManagerImpl)Shadow.extract(s.get().getSystemService(Context.WINDOW_SERVICE))).getViews());}
    private View card(ServiceController<HouseholdService> s){for(View v:overlays(s))if(v instanceof FrameLayout&&!(v instanceof ScrollView)&&v.isFocusable())return v;return null;}
    private View alarm(ServiceController<HouseholdService> s){for(View v:overlays(s))if(v instanceof ScrollView)return v;return null;}
    private TextView toast(ServiceController<HouseholdService> s){for(View v:overlays(s))if(v instanceof TextView)return (TextView)v;return null;}
    private ServiceController<HouseholdService> service(){
        // The household record (chosen on the page) is read; Home Assistant itself is not reached.
        new Vault(app).forget("ha");
        return ServiceController.of(new HouseholdService(),new Intent(app,HouseholdService.class)).create();
    }
    private Intent started(){Intent i=Shadows.shadowOf((android.app.Application)app).getNextStartedService();assertNotNull("a test was asked of the household service",i);return i;}
    private static boolean back(View card){return card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK))&card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));}

    @Test public void testTheDoorbellShowsTheRealCardAndBackClosesIt()throws Exception{
        Intent test;
        try(ActivityController<SettingsActivity> c=page(true)){button(root(c.get()),"Test the doorbell").performClick();idle();test=started();}
        ServiceController<HouseholdService> s=service();
        try{s.withIntent(test).startCommand(0,1);idle(0);
            View card=card(s);assertNotNull("the doorbell card",card);
            assertTrue("the chosen doorbell's name, marked TEST",LibraryJourneyTest.all(card,TextView.class).stream().anyMatch(t->t.getText().toString().equals("TEST · Front Door")));
            assertTrue(back(card));assertNull("Back closed it",card(s));
        }finally{s.destroy();}
    }
    @Test public void testTheAlarmScreenUsesTheRealOverlay()throws Exception{
        Intent test,end;
        try(ActivityController<SettingsActivity> c=page(true)){
            button(root(c.get()),"Test the alarm screen").performClick();idle();test=started();
            button(root(c.get()),"End the alarm test").performClick();idle();end=started();}
        ServiceController<HouseholdService> s=service();
        try{s.withIntent(test).startCommand(0,1);idle(0);View screen=alarm(s);assertNotNull("the alarm screen",screen);
            assertTrue(LibraryJourneyTest.all(screen,TextView.class).stream().anyMatch(t->t.getText().toString().startsWith("TEST · SMOKE ALARM")));
            s.withIntent(end).startCommand(0,2);idle(0);assertNull("the test ends",alarm(s));
        }finally{s.destroy();}
    }
    /** A state_changed event as Home Assistant sends it over its WebSocket. */
    private static JSONObject changed(String entity,String before,String after,String type,long fired)throws Exception{
        JSONObject attr=new JSONObject().put("event_type",type).put("friendly_name","Front Door");
        return new JSONObject().put("event_type","state_changed").put("time_fired",Instant.ofEpochMilli(fired).toString())
            .put("data",new JSONObject().put("entity_id",entity).put("old_state",new JSONObject().put("state",before)).put("new_state",new JSONObject().put("state",after).put("attributes",attr).put("last_changed",after)));
    }
    private static void deliver(ServiceController<HouseholdService> s,JSONObject event)throws Exception{
        java.lang.reflect.Method m=HouseholdService.class.getDeclaredMethod("event",JSONObject.class);m.setAccessible(true);m.invoke(s.get(),event);idle(0);
    }
    @Test public void aRingDingEventRingsOnceAndOldOnesAreIgnored()throws Exception{
        JSONObject house=new JSONObject().put("door","event.front_door_ding").put("camera","camera.front_door_live_view").put("person","event.front_door_motion")
            .put("names",new JSONObject().put("door","Front Door").put("camera","Front Door"));
        ServiceController<HouseholdService> s=service();
        try{java.lang.reflect.Field f=HouseholdService.class.getDeclaredField("house");f.setAccessible(true);f.set(s.get(),house);
            long now=System.currentTimeMillis();String earlier=Instant.ofEpochMilli(now-3_600_000).toString(),fresh=Instant.ofEpochMilli(now-1000).toString();
            deliver(s,changed("event.front_door_ding","unavailable",earlier,"ring",now));assertNull("Home Assistant restoring an old ding is not a visit",card(s));
            deliver(s,changed("event.front_door_ding","unknown",earlier,"ring",now));assertNull("an hour-old ding replayed is not a visit",card(s));
            deliver(s,changed("event.front_door_ding",earlier,fresh,"intercom_unlock",now));assertNull("not a press",card(s));
            deliver(s,changed("event.front_door_ding",earlier,fresh,"ring",now));
            View card=card(s);assertNotNull("a new ding opens the card",card);
            assertTrue(LibraryJourneyTest.all(card,TextView.class).stream().anyMatch(t->t.getText().toString().equals("Front Door")));
            back(card);assertNull(card(s));
            deliver(s,changed("event.front_door_ding",earlier,fresh,"ring",now+500));assertNull("the same ding again is the same visit",card(s));
            idle(16_000);
            long later=System.currentTimeMillis();
            deliver(s,changed("event.front_door_motion","unknown",Instant.ofEpochMilli(later-500).toString(),"motion",later));
            TextView t=toast(s);assertNotNull("motion at the chosen sensor: the small notice",t);assertEquals(DoorbellCamera.movement("Front Door",false),t.getText().toString());
            assertNull("motion never opens the camera",card(s));
        }finally{s.destroy();}
    }
}
