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

    // Owner decision, 24 October 2026: smoke and carbon monoxide above the doorbell.
    @Test public void onlyHomeAssistantSmokeAndCoSensorsCountAsAlarms(){
        assertEquals("smoke",AlertPolicy.alarmKind("binary_sensor.hall","smoke"));
        assertEquals("carbon_monoxide",AlertPolicy.alarmKind("binary_sensor.boiler","carbon_monoxide"));
        assertNull("a name is never enough",AlertPolicy.alarmKind("binary_sensor.smoke_alarm","motion"));
        assertNull(AlertPolicy.alarmKind("sensor.hall","smoke"));assertNull(AlertPolicy.alarmKind(null,"smoke"));
    }
    @Test public void alarmSuppressesRemindersAndOnlyOffClearsIt(){AlertPolicy p=new AlertPolicy();
        assertTrue(p.alarm("binary_sensor.hall","smoke","Hall","on"));assertFalse("same sensor again",p.alarm("binary_sensor.hall","smoke","Hall","on"));
        assertTrue(p.alarming());assertFalse(p.reminderAllowed(1000,1000));
        p.alarm("binary_sensor.hall","smoke","Hall","unavailable");assertTrue("a sensor lost in the fire keeps the alarm",p.alarming());
        p.alarm("binary_sensor.hall","smoke","Hall","off");assertFalse(p.alarming());assertTrue(p.reminderAllowed(1000,1000));
    }
    @Test public void reminderHeldDuringRingIsDroppedIfAnAlarmStarts(){AlertPolicy p=new AlertPolicy();p.acceptRing("door",1000);p.defer("Bins out tonight",2000);
        p.alarm("binary_sensor.hall","smoke","Hall","on");p.dismiss();assertNull(p.release(3000));}
    @Test public void acknowledgedAlarmReturnsOnlyForAnotherSensorOrAFreshAlarm(){AlertPolicy p=new AlertPolicy();
        p.alarm("binary_sensor.hall","smoke","Hall","on");p.acknowledge();assertFalse(p.alarmShown());assertTrue(p.alarming());
        assertFalse(p.alarm("binary_sensor.hall","smoke","Hall","on"));assertFalse(p.alarmShown());
        assertTrue(p.alarm("binary_sensor.boiler","carbon_monoxide","Boiler cupboard","on"));assertTrue(p.alarmShown());
        p.acknowledge();p.alarm("binary_sensor.hall","smoke","Hall","off");p.alarm("binary_sensor.boiler","carbon_monoxide","","off");
        assertTrue(p.alarm("binary_sensor.hall","smoke","Hall","on"));assertTrue(p.alarmShown());
    }
    @Test public void wordsFollowWhatIsSounding(){AlertPolicy p=new AlertPolicy();
        p.alarm("binary_sensor.boiler","carbon_monoxide","Boiler cupboard","on");
        assertEquals("Carbon monoxide alarm",p.alarmTitle());assertTrue(p.alarmAdvice().contains("fresh air"));assertEquals("Boiler cupboard",p.alarmPlaces());
        p.alarm("binary_sensor.hall","smoke","Hall","on");
        assertEquals("Smoke and carbon monoxide alarms",p.alarmTitle());assertTrue("smoke advice wins",p.alarmAdvice().contains("stay out"));assertEquals("Boiler cupboard, Hall",p.alarmPlaces());
        p.alarm("binary_sensor.boiler","carbon_monoxide","Boiler cupboard","off");assertEquals("Smoke alarm",p.alarmTitle());
    }
    @Test public void connectingFindsAnAlarmAlreadySoundingAndClearsOnesThatStopped(){
        String states="{\"id\":3,\"type\":\"result\",\"success\":true,\"result\":["
            +"{\"entity_id\":\"binary_sensor.hall\",\"state\":\"on\",\"attributes\":{\"device_class\":\"smoke\",\"friendly_name\":\"Hall\"}},"
            +"{\"entity_id\":\"binary_sensor.landing\",\"state\":\"off\",\"attributes\":{\"device_class\":\"smoke\"}},"
            +"{\"entity_id\":\"binary_sensor.front_door\",\"state\":\"on\",\"attributes\":{\"device_class\":\"door\"}},"
            +"{\"entity_id\":\"light.lounge\",\"state\":\"on\"}]}";
        List<String[]> on=AlertPolicy.soundingIn(states,3);assertEquals(1,on.size());assertArrayEquals(new String[]{"binary_sensor.hall","smoke","Hall"},on.get(0));
        AlertPolicy p=new AlertPolicy();p.alarm("binary_sensor.kitchen","smoke","Kitchen","on");
        assertTrue(p.alarmSnapshot(on));assertEquals("Hall",p.alarmPlaces());
        assertFalse(p.alarmSnapshot(new ArrayList<>()));assertFalse(p.alarming());
    }
    @Test public void aRefusedOrUnrelatedReplyNeverClearsAnAlarm(){
        assertNull(AlertPolicy.soundingIn("{\"id\":3,\"type\":\"result\",\"success\":false,\"error\":{}}",3));
        assertNull(AlertPolicy.soundingIn("{\"id\":1,\"type\":\"result\",\"success\":true,\"result\":null}",3));
        assertNull(AlertPolicy.soundingIn("not json",3));
    }
}
