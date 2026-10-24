package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
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
}
