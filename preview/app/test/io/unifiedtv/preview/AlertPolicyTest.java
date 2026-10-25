package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class AlertPolicyTest {
    @Test public void onlyDoorbellAndPersonRisingEdgesTrigger(){
        assertTrue(AlertPolicy.rising("binary_sensor.door","binary_sensor.door","binary_sensor.person","off","on"));
        assertTrue(AlertPolicy.rising("binary_sensor.person","binary_sensor.door","binary_sensor.person","off","on"));
        assertFalse(AlertPolicy.rising("binary_sensor.motion","binary_sensor.door","binary_sensor.person","off","on"));
        assertFalse(AlertPolicy.rising("binary_sensor.person","binary_sensor.door","binary_sensor.person","unknown","on"));
        assertFalse(AlertPolicy.rising("binary_sensor.person","binary_sensor.door","binary_sensor.person","on","on"));
    }
    @Test public void eventDoorbellsRingOnEachNewPressButNotAfterARestart(){
        String door="event.front_door_ding";
        assertTrue(AlertPolicy.rising(door,door,"","2026-10-25T19:00:00+00:00","2026-10-25T19:42:00+00:00"));
        assertTrue("first press after start-up",AlertPolicy.rising(door,door,"","unknown","2026-10-25T19:42:00+00:00"));
        assertFalse("back from a restart",AlertPolicy.rising(door,door,"","unavailable","2026-10-25T19:42:00+00:00"));
        assertFalse(AlertPolicy.rising(door,door,"","2026-10-25T19:42:00+00:00","unavailable"));
        assertFalse("same press repeated",AlertPolicy.rising(door,door,"","2026-10-25T19:42:00+00:00","2026-10-25T19:42:00+00:00"));
        assertFalse("not the chosen doorbell",AlertPolicy.rising("event.back_door",door,"","a","b"));
    }
    @Test public void repeatedEventIsDeduplicatedButAnotherIsAccepted(){AlertPolicy p=new AlertPolicy();assertTrue(p.acceptRing("door-event",1000));assertFalse(p.acceptRing("door-event",1200));assertTrue(p.acceptRing("person-event",1300));}
    @Test public void ringSuppressesRemindersAndExpiredOnesStayHidden(){AlertPolicy p=new AlertPolicy();p.acceptRing("door",1000);assertFalse(p.reminderAllowed(1100,1200));p.dismiss();assertTrue(p.reminderAllowed(1100,1200));assertFalse(p.reminderAllowed(1100,32000));}
    @Test public void expiredRingWindowReleasesPriority(){AlertPolicy p=new AlertPolicy();p.acceptRing("door",1000);assertTrue(p.reminderAllowed(46000,46001));assertFalse(p.reminderAllowed(52000,46001));}
    // A TV clock a second or two behind Home Assistant must not silently drop every reminder.
    @Test public void reminderFromASlightlyFastServerClockIsShown(){AlertPolicy p=new AlertPolicy();assertTrue(p.reminderAllowed(2000,1000));assertTrue(p.reminderAllowed(6000,1000));assertFalse(p.reminderAllowed(6001,1000));}
    @Test public void reminderDuringRingWaitsThenShowsIfStillRelevant(){AlertPolicy p=new AlertPolicy();p.acceptRing("door",1000);assertTrue(p.ringing(2000));
        p.defer("Bins out tonight",2000);p.dismiss();assertFalse(p.ringing(3000));assertEquals("Bins out tonight",p.release(10000));assertNull("released once",p.release(10001));}
    @Test public void reminderHeldTooLongDuringRingIsDropped(){AlertPolicy p=new AlertPolicy();p.acceptRing("door",1000);p.defer("Bins out tonight",2000);p.dismiss();assertNull(p.release(40000));}
    @Test public void onlyTheLatestHeldReminderIsKeptAndBlankOnesIgnored(){AlertPolicy p=new AlertPolicy();p.acceptRing("door",1000);p.defer("First",2000);p.defer("Second",3000);p.defer("  ",4000);p.dismiss();assertEquals("Second",p.release(5000));}
    @Test public void personDetectedDuringTheVisitKeepsRinging(){AlertPolicy p=new AlertPolicy();assertTrue(p.acceptRing("door:t1",1000));assertTrue(p.acceptRing("person:t2",30000));assertTrue(p.ringing(70000));assertFalse(p.ringing(75001));}

    // Owner decision OD-07, 25 October 2026: smoke and carbon monoxide above the doorbell, each alarm on its own.
    private static final String HALL="binary_sensor.hall",BOILER="binary_sensor.boiler";
    @Test public void onlyHomeAssistantSmokeAndCoBinarySensorsCountAsAlarms(){
        assertEquals("smoke",AlertPolicy.alarmKind(HALL,"smoke"));
        assertEquals("carbon_monoxide",AlertPolicy.alarmKind(BOILER,"carbon_monoxide"));
        assertNull("a name is never enough",AlertPolicy.alarmKind("binary_sensor.smoke_alarm","motion"));
        assertNull("a CO sensor.* is a ppm reading, not an alarm",AlertPolicy.alarmKind("sensor.hall_co","carbon_monoxide"));
        assertNull(AlertPolicy.alarmKind(null,"smoke"));
    }
    @Test public void standardStatesMapAndOnlyExposedTestOrFaultAreUsed(){
        assertEquals(AlertPolicy.ALARM,AlertPolicy.reading("on"));assertEquals(AlertPolicy.NORMAL,AlertPolicy.reading("off"));
        assertEquals(AlertPolicy.OFFLINE,AlertPolicy.reading("unavailable"));assertEquals(AlertPolicy.OFFLINE,AlertPolicy.reading("unknown"));
        assertEquals(AlertPolicy.TEST,AlertPolicy.reading("test"));assertEquals(AlertPolicy.FAULT,AlertPolicy.reading("fault"));
        assertNull("anything else is ignored, never guessed",AlertPolicy.reading("smoke"));
    }
    @Test public void smokeAndCoAreSeparateEntriesWithRoomAndTime(){AlertPolicy p=new AlertPolicy();
        assertEquals(AlertPolicy.NEW_ALARM,p.update(BOILER,"carbon_monoxide","Boiler cupboard","on",2000,false));
        assertEquals(AlertPolicy.NEW_ALARM,p.update(HALL,"smoke","Hall","on",1000,false));
        List<AlertPolicy.Detector> on=p.alarms();assertEquals(2,on.size());
        assertEquals("oldest first","Hall",on.get(0).name);assertEquals(1000,on.get(0).since);assertEquals("SMOKE ALARM",on.get(0).title());
        assertEquals("CARBON MONOXIDE ALARM",on.get(1).title());assertTrue(on.get(1).advice().contains("fresh air"));assertTrue(on.get(0).advice().contains("stay out"));
        assertEquals("a duplicate event (same last_changed) changes nothing",AlertPolicy.NONE,p.update(HALL,"smoke","Hall","on",1000,false));
        assertEquals("an older, out-of-order event keeps the first time",AlertPolicy.NONE,p.update(HALL,"smoke","Hall","on",500,false));assertEquals(1000,p.alarms().get(0).since);
    }
    @Test public void acknowledgingOneAlarmLeavesTheOtherOnScreenAndABannerRemains(){AlertPolicy p=new AlertPolicy();
        p.update(HALL,"smoke","Hall","on",1000,false);p.update(BOILER,"carbon_monoxide","Boiler","on",2000,false);
        assertTrue(p.acknowledge(HALL));assertTrue("CO still unacknowledged",p.screen());
        assertTrue(p.acknowledge(BOILER));assertFalse(p.screen());assertTrue("banner while still active",p.emergency());
        assertFalse("acknowledging twice does nothing",p.acknowledge(HALL));
        assertEquals("a new alarm brings the screen back",AlertPolicy.NEW_ALARM,p.update("binary_sensor.landing","smoke","Landing","on",3000,false));assertTrue(p.screen());
        assertTrue("acknowledged entries stay acknowledged",p.alarms().get(0).acknowledged);
    }
    @Test public void normalClearsOnlyThatDetectorAndAQuietDetectorKeepsItsAlarm(){AlertPolicy p=new AlertPolicy();
        p.update(HALL,"smoke","Hall","on",1000,false);
        assertEquals(AlertPolicy.CHANGED,p.update(HALL,"smoke","Hall","unavailable",5000,false));
        assertTrue("may have failed in the fire",p.emergency());assertTrue(p.alarms().get(0).lost);assertEquals(1000,p.alarms().get(0).since);
        assertEquals(AlertPolicy.CHANGED,p.update(HALL,"smoke","Hall","off",6000,false));assertFalse(p.emergency());assertTrue(p.warnings().isEmpty());
    }
    @Test public void offlineDetectorsAreWarningsNeverEmergencies(){AlertPolicy p=new AlertPolicy();
        assertEquals(AlertPolicy.CHANGED,p.update(HALL,"smoke","Landing","unavailable",1000,false));
        assertFalse(p.emergency());assertFalse(p.screen());assertEquals(1,p.warnings().size());assertEquals("Smoke detector offline",p.warnings().get(0).warning());
        assertTrue("an offline detector doesn't block reminders",p.reminderAllowed(1000,1000));
        p.update(BOILER,"carbon_monoxide","Boiler","fault",1000,false);assertEquals("Carbon monoxide detector fault",p.warnings().get(1).warning());
    }
    @Test public void remindersAreDroppedDuringAnEmergency(){AlertPolicy p=new AlertPolicy();p.acceptRing("door",1000);p.defer("Bins out tonight",2000);
        p.update(HALL,"smoke","Hall","on",2500,false);p.dismiss();assertNull(p.release(3000));assertFalse(p.reminderAllowed(3000,3000));}
    @Test public void ringWaitsUntilEveryAlarmHasClearedThenShowsOrNotes(){AlertPolicy p=new AlertPolicy();
        p.update(HALL,"smoke","Hall","on",1000,false);p.acknowledge(HALL);p.queueRing(10000);
        assertEquals("not while any alarm is active, acknowledged or not",0,p.ringAfterAlarm(20000));
        p.update(HALL,"smoke","Hall","off",21000,false);assertEquals("recent visit: show the camera",1,p.ringAfterAlarm(30000));assertEquals("only once",0,p.ringAfterAlarm(30001));
        p.update(HALL,"smoke","Hall","on",40000,false);p.queueRing(41000);p.update(HALL,"smoke","Hall","off",200000,false);
        assertEquals("old visit: a note instead",-1,p.ringAfterAlarm(200000));
    }
    @Test public void connectingReadsEveryDetectorAndDropsOnesNoLongerReported(){
        String states="{\"id\":3,\"type\":\"result\",\"success\":true,\"result\":["
            +"{\"entity_id\":\"binary_sensor.hall\",\"state\":\"on\",\"last_changed\":\"2026-10-25T19:42:00+00:00\",\"attributes\":{\"device_class\":\"smoke\",\"friendly_name\":\"Hall\"}},"
            +"{\"entity_id\":\"binary_sensor.landing\",\"state\":\"unavailable\",\"attributes\":{\"device_class\":\"smoke\",\"friendly_name\":\"Landing\"}},"
            +"{\"entity_id\":\"binary_sensor.boiler\",\"state\":\"off\",\"attributes\":{\"device_class\":\"carbon_monoxide\"}},"
            +"{\"entity_id\":\"sensor.boiler_co\",\"state\":\"12\",\"attributes\":{\"device_class\":\"carbon_monoxide\"}},"
            +"{\"entity_id\":\"binary_sensor.front_door\",\"state\":\"on\",\"attributes\":{\"device_class\":\"door\"}}]}";
        List<String[]> all=AlertPolicy.detectorsIn(states,3);assertEquals(3,all.size());
        assertArrayEquals(new String[]{"binary_sensor.hall","smoke","Hall","on",String.valueOf(java.time.Instant.parse("2026-10-25T19:42:00Z").toEpochMilli())},all.get(0));
        AlertPolicy p=new AlertPolicy();p.update("binary_sensor.kitchen","smoke","Kitchen","on",1,false);p.update("simulated.smoke","smoke","Test","on",1,true);
        p.connection(false);assertFalse(p.connected());
        assertEquals(AlertPolicy.NEW_ALARM,p.snapshot(all));assertTrue(p.connected());
        List<String> names=new ArrayList<>();for(AlertPolicy.Detector d:p.alarms())names.add(d.name);
        assertEquals("kitchen missing but kept, test kept, hall new",Arrays.asList("Kitchen","Test","Hall"),names);
        assertTrue("a missing alarm is marked not responding, never cleared",p.alarms().get(0).lost);
        assertEquals(1,p.warnings().size());
    }
    @Test public void aRefusedOrUnrelatedReplyNeverClearsAnAlarm(){
        assertNull(AlertPolicy.detectorsIn("{\"id\":3,\"type\":\"result\",\"success\":false,\"error\":{}}",3));
        assertNull(AlertPolicy.detectorsIn("{\"id\":1,\"type\":\"result\",\"success\":true,\"result\":null}",3));
        assertNull(AlertPolicy.detectorsIn("not json",3));
    }
    @Test public void testsAreAlwaysLabelledAndEndingATestLeavesRealAlarms(){AlertPolicy p=new AlertPolicy();
        p.update("simulated.smoke","smoke","Kitchen","on",1,true);p.update(HALL,"smoke","Hall","on",2,false);
        assertTrue(p.alarms().get(0).title().startsWith("TEST"));assertFalse(p.alarms().get(1).title().startsWith("TEST"));
        assertEquals(AlertPolicy.CHANGED,p.endSimulation());assertEquals(1,p.alarms().size());assertEquals("Hall",p.alarms().get(0).name);
    }
    @Test public void anAlarmThatReSoundedWhileAwayComesBackUnacknowledged(){AlertPolicy p=new AlertPolicy();
        p.update(HALL,"smoke","Hall","on",1000,false);p.acknowledge(HALL);
        assertEquals("same alarm: acknowledgement kept",AlertPolicy.NONE,p.snapshot(Collections.singletonList(new String[]{HALL,"smoke","Hall","on","1000"})));assertFalse(p.screen());
        assertEquals(AlertPolicy.NEW_ALARM,p.snapshot(Collections.singletonList(new String[]{HALL,"smoke","Hall","on","5000"})));
        assertTrue(p.screen());assertEquals(5000,p.alarms().get(0).since);
    }
    @Test public void aMissingAlarmStaysUntilOffAndAMissingWarningIsDropped(){AlertPolicy p=new AlertPolicy();
        p.update(HALL,"smoke","Hall","on",1000,false);p.update("binary_sensor.landing","smoke","Landing","unavailable",1000,false);
        assertEquals(AlertPolicy.CHANGED,p.snapshot(new ArrayList<>()));assertTrue(p.emergency());assertTrue(p.alarms().get(0).lost);assertEquals(0,p.warnings().size());
        assertEquals("already marked",AlertPolicy.NONE,p.snapshot(new ArrayList<>()));
        p.snapshot(Collections.singletonList(new String[]{HALL,"smoke","Hall","off","9000"}));assertFalse(p.emergency());
    }
    @Test public void switchingAlarmsOffForgetsRealDetectorsAndKeepsTests(){AlertPolicy p=new AlertPolicy();
        p.update(HALL,"smoke","Hall","on",1,false);p.update("simulated.smoke","smoke","Test","on",1,true);
        assertEquals(AlertPolicy.CHANGED,p.forgetDetectors());assertEquals(1,p.alarms().size());assertEquals("Test",p.alarms().get(0).name);
        assertEquals(AlertPolicy.NONE,p.forgetDetectors());
    }
    @Test public void aVisitExactly45SecondsOldIsANote(){AlertPolicy p=new AlertPolicy();
        p.update(HALL,"smoke","Hall","on",0,false);p.queueRing(1000);p.update(HALL,"smoke","Hall","off",2000,false);assertEquals(1,p.ringAfterAlarm(45999));
        AlertPolicy q=new AlertPolicy();q.update(HALL,"smoke","Hall","on",0,false);q.queueRing(1000);q.update(HALL,"smoke","Hall","off",2000,false);assertEquals(-1,q.ringAfterAlarm(46000));
    }
    // Ring rules RING-01..04 (owner, 25 October 2026).
    private static org.json.JSONObject attrs(String json){try{return new org.json.JSONObject(json);}catch(Exception e){throw new RuntimeException(e);}}
    @Test public void ring01OrdinaryMotionNeverCounts(){
        String door="binary_sensor.front_door_ding",person="binary_sensor.front_door_motion";
        assertEquals(AlertPolicy.RING_NONE,AlertPolicy.ringKind(person,door,person,"off","on",attrs("{\"friendly_name\":\"Front Door Motion\"}")));
        assertEquals("motion that isn't the chosen person sensor",AlertPolicy.RING_NONE,AlertPolicy.ringKind("binary_sensor.drive_motion",door,"binary_sensor.front_door_person","off","on",null));
        assertEquals("a Ring motion event is not a person",AlertPolicy.RING_NONE,AlertPolicy.ringKind("event.front_door_motion",door,"event.front_door_motion","2026-10-25T10:00:00Z","2026-10-25T10:05:00Z",attrs("{\"event_type\":\"motion\"}")));
    }
    @Test public void ring02OnlyAConfirmedPersonCounts(){
        String door="binary_sensor.front_door_ding";
        assertEquals("ring-mqtt motion sensor that saw a person",AlertPolicy.RING_PERSON,AlertPolicy.ringKind("binary_sensor.front_door_motion",door,"binary_sensor.front_door_motion","off","on",attrs("{\"personDetected\":true}")));
        assertEquals("a dedicated person sensor",AlertPolicy.RING_PERSON,AlertPolicy.ringKind("binary_sensor.g4_person_detected",door,"binary_sensor.g4_person_detected","off","on",attrs("{\"friendly_name\":\"G4 Person detected\"}")));
        assertEquals("an event entity that says person",AlertPolicy.RING_PERSON,AlertPolicy.ringKind("event.front_door_motion",door,"event.front_door_motion","2026-10-25T10:00:00Z","2026-10-25T10:05:00Z",attrs("{\"event_type\":\"human\"}")));
        assertEquals("staying on is not a new person",AlertPolicy.RING_NONE,AlertPolicy.ringKind("binary_sensor.g4_person_detected",door,"binary_sensor.g4_person_detected","on","on",null));
    }
    @Test public void ring02RepeatedPersonEventsGiveOneToast(){AlertPolicy p=new AlertPolicy();
        assertTrue(p.acceptPerson("binary_sensor.p",1000));assertFalse(p.acceptPerson("binary_sensor.p",30000));assertFalse(p.acceptPerson("binary_sensor.p",60999));
        assertTrue(p.acceptPerson("binary_sensor.p",61000));assertTrue("another camera is its own visit",p.acceptPerson("binary_sensor.q",30000));
        assertEquals(5000,AlertPolicy.PERSON_TOAST_MS);
    }
    @Test public void ring03APressIsAPressAndNeverAPerson(){
        String door="binary_sensor.front_door_ding";
        assertEquals(AlertPolicy.RING_PRESS,AlertPolicy.ringKind(door,door,"binary_sensor.front_door_person","off","on",null));
        assertEquals(AlertPolicy.RING_PRESS,AlertPolicy.ringKind("event.front_door_ding","event.front_door_ding","","2026-10-25T10:00:00Z","2026-10-25T10:05:00Z",attrs("{\"event_type\":\"ding\"}")));
        assertEquals("a restart is not a press",AlertPolicy.RING_NONE,AlertPolicy.ringKind("event.front_door_ding","event.front_door_ding","","unavailable","2026-10-25T10:05:00Z",null));
    }
    @Test public void ring04APressDuringAnEmergencyIsKeptForLater(){AlertPolicy p=new AlertPolicy();
        p.update(HALL,"smoke","Hall","on",0,false);assertTrue(p.acceptRing("door:1",1000));p.queueRing(1000);
        assertEquals("never while the alarm is active",0,p.ringAfterAlarm(5000));assertEquals(1000,p.queuedRingAt());
        p.update(HALL,"smoke","Hall","off",6000,false);assertEquals(1,p.ringAfterAlarm(7000));
    }
}
