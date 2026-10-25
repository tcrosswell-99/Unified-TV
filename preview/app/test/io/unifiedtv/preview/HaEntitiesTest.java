package io.unifiedtv.preview;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class HaEntitiesTest {
    /** One Home Assistant state, shaped like /api/states. Symbolic household names only. */
    private static JSONObject state(String id,String state,String name,String deviceClass,String... eventTypes)throws JSONException{
        JSONObject attr=new JSONObject();if(name!=null)attr.put("friendly_name",name);if(deviceClass!=null)attr.put("device_class",deviceClass);
        if(eventTypes.length>0)attr.put("event_types",new JSONArray(eventTypes));
        return new JSONObject().put("entity_id",id).put("state",state).put("attributes",attr).put("last_changed","2026-10-25T14:32:00+00:00");
    }
    /** A mixed house: Ring (both the older binary sensors and the newer event entities), UniFi, Frigate, Aqara, Nest, scripts. */
    private static JSONArray house()throws JSONException{
        JSONArray a=new JSONArray();
        a.put(state("binary_sensor.front_door_ding","off","Front Door Ding","occupancy"));
        a.put(state("binary_sensor.front_door_motion","off","Front Door Motion","motion"));
        a.put(state("binary_sensor.front_door_battery","off","Front Door Battery","battery"));
        a.put(state("camera.front_door_live_view","idle","Front Door Live view",null));
        a.put(state("camera.front_door_last_recording","idle","Front Door Last recording",null));
        a.put(state("event.front_door_ding","2026-10-25T14:30:00.000+00:00","Front Door Ding","doorbell","ding"));
        a.put(state("event.front_door_motion","unknown","Front Door Motion","motion","motion"));
        a.put(state("binary_sensor.side_door_ding","unavailable","Side Door Ding","occupancy"));
        JSONObject ghost=state("binary_sensor.old_porch_ding","unavailable","Old Porch Ding","occupancy");ghost.getJSONObject("attributes").put("restored",true);a.put(ghost);
        a.put(state("binary_sensor.g4_doorbell_is_dark","off","G4 Doorbell Is dark",null));
        a.put(state("binary_sensor.g4_doorbell_person_detected","off","G4 Doorbell Person detected","occupancy"));
        a.put(state("binary_sensor.driveway_person_occupancy","off","Driveway Person occupancy","occupancy"));
        a.put(state("binary_sensor.hallway_motion_occupancy","off","Hallway Motion","motion"));
        a.put(state("binary_sensor.lumi_sensor_magnet_aq2_opening","off","Front door","door"));
        a.put(state("binary_sensor.kitchen_smoke","off","Kitchen Smoke","smoke"));
        a.put(state("sensor.hallway_carbon_monoxide","3","Hallway CO","carbon_monoxide"));
        a.put(state("event.nest_doorbell_chime","unknown","Porch Chime","doorbell","doorbell_chime"));
        a.put(state("camera.garden",          "idle","Garden",null));
        a.put(state("camera.porch_snapshot","idle","Porch Snapshot",null));
        a.put(state("calendar.tottenham_hotspur_fixtures","off","Tottenham Hotspur fixtures",null));
        a.put(state("calendar.family","off","Family",null));
        a.put(state("calendar.uk_holidays","off",null,null));
        a.put(state("script.xbox_on_hdmi_3","off","Xbox on",null));
        a.put(state("script.samsung_tv_plus","off","Open Samsung TV Plus",null));
        a.put(state("script.frame_art_mode","off","Restore Art Mode",null));
        a.put(state("script.frame_ring_entry","off","Frame Ring entry",null));
        a.put(state("script.frame_ring_return","off","Frame Ring return to Art Mode",null));
        a.put(state("script.good_morning","off","Good morning",null));
        a.put(new JSONObject().put("entity_id","Bad.Entity").put("state","on"));
        a.put("not an object");
        return a;
    }
    private static List<HaEntities.Entity> entities()throws JSONException{return HaEntities.parse(house().toString());}
    private static List<String> ids(List<HaEntities.Candidate> list){List<String> out=new ArrayList<>();for(HaEntities.Candidate c:list)out.add(c.id);return out;}

    @Test public void parsesFriendlyNamesAndSkipsGhostsAndBadIds()throws Exception{
        List<HaEntities.Entity> all=entities();
        List<String> seen=new ArrayList<>();for(HaEntities.Entity e:all)seen.add(e.id);
        assertFalse("restored entities no longer exist in the integration",seen.contains("binary_sensor.old_porch_ding"));
        assertFalse(seen.contains("Bad.Entity"));
        for(HaEntities.Entity e:all)if(e.id.equals("calendar.uk_holidays"))assertEquals("Uk holidays",e.name);
        for(HaEntities.Entity e:all)if(e.id.equals("binary_sensor.front_door_ding"))assertEquals("Front Door Ding",e.name);
        assertNull(HaEntities.parse("{\"message\":\"not a list\"}"));
        assertNull(HaEntities.parse("<html>"));
        assertNull(HaEntities.parse(null));
    }

    @Test public void oneClearDoorbellIsFoundAndOthersSitBelowTheDivider()throws Exception{
        HaEntities.Choices door=HaEntities.choices("door",entities(),"",false);
        assertEquals(List.of("binary_sensor.front_door_ding"),ids(door.strong));
        assertEquals("Front Door Ding",door.auto().name);
        List<String> other=ids(door.other);
        assertTrue("an unresponsive doorbell is listed, not chosen",other.contains("binary_sensor.side_door_ding"));
        assertTrue(other.contains("binary_sensor.front_door_motion"));
        assertTrue("'Is dark' on a doorbell device is not a press",other.contains("binary_sensor.g4_doorbell_is_dark"));
        for(String never:new String[]{"binary_sensor.kitchen_smoke","binary_sensor.front_door_battery","binary_sensor.lumi_sensor_magnet_aq2_opening","sensor.hallway_carbon_monoxide","event.front_door_ding"})
            assertFalse(never,other.contains(never)||ids(door.strong).contains(never));
        assertEquals("Other sensors",door.otherHeading);
        for(HaEntities.Candidate c:door.other)if(c.id.equals("binary_sensor.side_door_ding"))assertFalse(c.responding);
    }

    @Test public void twoPlausibleDoorbellsAreNeverChosenSilently()throws Exception{
        JSONArray a=house();a.put(state("binary_sensor.back_gate_ding","off","Back Gate Ding",null));a.put(state("binary_sensor.annexe_visitor","off","Annexe Visitor",null));
        HaEntities.Choices door=HaEntities.choices("door",HaEntities.parse(a.toString()),"",false);
        assertEquals(List.of("binary_sensor.annexe_visitor","binary_sensor.back_gate_ding","binary_sensor.front_door_ding"),ids(door.strong));
        assertNull(door.auto());
    }

    @Test public void eventDoorbellsAreOfferedNowTheServiceListensForThem()throws Exception{
        assertTrue("AlertPolicy.rising handles event entities (a new press time)",HaEntities.EVENT_DOORBELLS);
        assertTrue("nothing left waiting",HaEntities.waitingDoorbells(entities()).isEmpty());
        HaEntities.Choices door=HaEntities.choices("door",entities(),"");
        assertTrue(ids(door.strong).containsAll(List.of("event.front_door_ding","event.nest_doorbell_chime","binary_sensor.front_door_ding")));
        assertFalse("a motion event is not a press",ids(door.strong).contains("event.front_door_motion"));
        assertNull("several doorbells: the household chooses",door.auto());
        assertTrue(HaEntities.valid("door","event.front_door_ding"));
    }

    @Test public void personDetectionIsNotGenericMotion()throws Exception{
        HaEntities.Choices person=HaEntities.choices("person",entities(),"binary_sensor.front_door_ding",false);
        assertEquals(List.of("binary_sensor.driveway_person_occupancy","binary_sensor.g4_doorbell_person_detected"),ids(person.strong));
        assertNull("two person sensors: the household chooses",person.auto());
        assertTrue(ids(person.other).contains("binary_sensor.front_door_motion"));
        assertTrue(ids(person.other).contains("binary_sensor.hallway_motion_occupancy"));
        assertFalse("a doorbell press is not a person",ids(person.other).contains("binary_sensor.front_door_ding"));
        assertTrue(person.otherHeading.contains("motion alone never shows anything"));
        // Beside the chosen doorbell first.
        assertEquals("binary_sensor.front_door_motion",person.other.get(0).id);
    }

    @Test public void theLiveCameraBesideTheDoorbellIsFoundAndStillsAreNot()throws Exception{
        HaEntities.Choices camera=HaEntities.choices("camera",entities(),"binary_sensor.front_door_ding",false);
        assertEquals(List.of("camera.front_door_live_view"),ids(camera.strong));
        assertEquals("Front Door Live view",camera.auto().name);
        assertTrue(ids(camera.other).containsAll(List.of("camera.front_door_last_recording","camera.garden","camera.porch_snapshot")));
        HaEntities.Choices noDoor=HaEntities.choices("camera",entities(),"",false);
        assertEquals(List.of("camera.front_door_live_view","camera.garden"),ids(noDoor.strong));
        assertNull(noDoor.auto());
        assertEquals("front_door",HaEntities.devicePrefix("binary_sensor.front_door_ding"));
        assertEquals("g4_doorbell",HaEntities.devicePrefix("binary_sensor.g4_doorbell_doorbell"));
        assertNull(HaEntities.devicePrefix(""));assertNull(HaEntities.devicePrefix("binary_sensor.ding"));
    }

    @Test public void fixturesCalendarAndOwnerScriptsAreMatchedOnWholeWords()throws Exception{
        List<HaEntities.Entity> all=entities();
        HaEntities.Choices calendar=HaEntities.choices("calendar",all,"",false);
        assertEquals("calendar.tottenham_hotspur_fixtures",calendar.auto().id);
        assertEquals(List.of("calendar.family","calendar.uk_holidays"),ids(calendar.other));
        String[][] expected={{"xbox","script.xbox_on_hdmi_3"},{"tvplus","script.samsung_tv_plus"},{"art","script.frame_art_mode"},
            {"ringEnter","script.frame_ring_entry"},{"ringExit","script.frame_ring_return"}};
        for(String[] e:expected){HaEntities.Choices c=HaEntities.choices(e[0],all,"",false);assertEquals(e[0],List.of(e[1]),ids(c.strong));assertEquals(e[1],c.auto().id);
            assertTrue("'morning' is not 'ring'",ids(c.other).contains("script.good_morning"));assertEquals("Other scripts",c.otherHeading);}
    }

    @Test public void anEntryAndReturnScriptInOneIsNotAClearAnswer()throws Exception{
        JSONArray a=new JSONArray();a.put(state("script.ring_start_and_restore","off","Ring start and restore",null));
        assertNull(HaEntities.choices("ringEnter",HaEntities.parse(a.toString()),"",false).auto());
        assertNull(HaEntities.choices("ringExit",HaEntities.parse(a.toString()),"",false).auto());
        assertTrue(HaEntities.choices("door",null,"",false).isEmpty());
    }

    @Test public void typedFallbackKeepsThePreviousValidation(){
        assertTrue(HaEntities.valid("door",""));assertTrue(HaEntities.valid("door","binary_sensor.front_door_ding"));
        assertFalse(HaEntities.valid("door","camera.front_door"));assertFalse(HaEntities.valid("person","binary_sensor.Front"));
        assertTrue(HaEntities.valid("camera","camera.front_door_live_view"));assertTrue(HaEntities.valid("calendar","calendar.fixtures"));
        assertFalse(HaEntities.valid("calendar","calendar."));assertTrue(HaEntities.valid("ringExit","script.x"));
        assertFalse(HaEntities.valid("xbox","script.x; rm"));assertFalse(HaEntities.valid("art","binary_sensor.x"));
        assertEquals("binary_sensor",HaEntities.domain("person"));assertEquals("script",HaEntities.domain("tvplus"));
        assertEquals("Samsung The Frame",HaEntities.roomName("frame"));assertEquals("Sony BRAVIA",HaEntities.roomName("sony"));
        assertTrue(HaEntities.room("tcl"));assertFalse(HaEntities.room("bedroom"));
    }

    @Test public void hugePayloadsAreBounded()throws Exception{
        StringBuilder big=new StringBuilder("[");while(big.length()<=HaEntities.MAX_CHARS)big.append("{\"entity_id\":\"script.filler\",\"state\":\"off\"},");
        assertNull("over the limit is refused, never parsed",HaEntities.parse(big.toString()));
        JSONArray many=new JSONArray();for(int i=0;i<12_000;i++)many.put(new JSONObject().put("entity_id","script.s"+i).put("state","off"));
        List<HaEntities.Entity> all=HaEntities.parse(many.toString());
        assertEquals(HaEntities.MAX_ENTITIES,all.size());
        HaEntities.Choices c=HaEntities.choices("xbox",all,"",false);
        assertEquals(HaEntities.MAX_LISTED,c.other.size());assertTrue(c.strong.isEmpty());
    }

    @Test public void connectionWordsFollowTheSpec(){
        assertEquals("Not configured",HaStatus.before(null));
        assertEquals("Not configured",HaStatus.before(new JSONObject()));
        assertEquals("Not configured",HaStatus.before(cfg("http://ha.example.invalid:8123",false)));
        assertNull(HaStatus.before(cfg("http://ha.example.invalid:8123",true)));
        assertNull(HaStatus.before(cfg("https://ha.example.invalid",false)));
        assertEquals("Authorised",HaStatus.after(null));
        assertEquals("Authentication required",HaStatus.after(new LocalApi.ApiError(401)));
        assertEquals("Authentication required",HaStatus.after(new LocalApi.ApiError(403)));
        assertEquals("Reachable",HaStatus.after(new LocalApi.ApiError(404)));
        assertEquals("Reachable",HaStatus.after(new LocalApi.ApiError(302)));
        assertEquals("Reachable",HaStatus.after(new JSONException("x")));
        assertEquals("Offline",HaStatus.after(new SocketTimeoutException()));
        assertEquals("Offline",HaStatus.after(new UnknownHostException()));
        assertEquals("Offline",HaStatus.after(new javax.net.ssl.SSLHandshakeException("x")));
        assertEquals("Offline",HaStatus.after(new ConnectException()));
        assertEquals("Not configured",HaStatus.after(new IllegalArgumentException()));
        for(String s:new String[]{"Not configured","Reachable","Authentication required","Authorised","Offline"})assertFalse(HaStatus.explain(s).isEmpty());
    }
    private static JSONObject cfg(String base,boolean consent){
        try{return new JSONObject().put("enabled",true).put("base",base).put("kind","ha").put("token","t").put("httpConsent",consent);}catch(JSONException e){throw new AssertionError(e);}
    }

    /** The real request path: LocalApi's bearer token, status codes and size bound, against a local stand-in server. */
    @Test public void fetchReadsStatesOnceWithTheHouseholdToken()throws Exception{
        AtomicReference<String> auth=new AtomicReference<>("");AtomicReference<byte[]> reply=new AtomicReference<>();AtomicReference<Integer> code=new AtomicReference<>(200);
        try(ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))){
            Thread serving=new Thread(()->{
                while(!server.isClosed())try(Socket socket=server.accept()){
                    BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    String request=reader.readLine(),line;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("authorization:"))auth.set(line.substring(14).trim());
                    byte[] body=request.startsWith("GET /api/states ")?reply.get():new byte[0];
                    OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+code.get()+" Result\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(body);out.flush();
                }catch(Exception ignored){}
            });serving.setDaemon(true);serving.start();
            JSONObject ha=new JSONObject().put("enabled",true).put("base","http://127.0.0.1:"+server.getLocalPort()).put("kind","ha").put("token","private-test-token").put("httpConsent",true);

            reply.set(house().toString().getBytes(StandardCharsets.UTF_8));
            HaEntities.Snapshot ok=HaEntities.fetch(ha);
            assertEquals("Authorised",ok.status);assertEquals("Bearer private-test-token",auth.get());assertEquals(entities().size(),ok.entities.size());

            reply.set("<html>login</html>".getBytes(StandardCharsets.UTF_8));
            HaEntities.Snapshot odd=HaEntities.fetch(ha);assertEquals("Reachable",odd.status);assertNull(odd.entities);

            reply.set(new byte[LocalApi.LIMIT+1]);
            HaEntities.Snapshot large=HaEntities.fetch(ha);assertEquals("Authorised",large.status);assertTrue(large.tooLarge);assertNull(large.entities);

            code.set(401);reply.set(new byte[0]);
            assertEquals("Authentication required",HaEntities.fetch(ha).status);
            server.close();serving.join(2000);
            assertEquals("Offline",HaEntities.fetch(ha).status);
        }
        assertEquals("Not configured",HaEntities.fetch(new JSONObject()).status);
    }
}
