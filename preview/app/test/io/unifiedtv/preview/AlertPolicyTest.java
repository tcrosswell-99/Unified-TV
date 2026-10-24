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
    @Test public void expiredRingWindowReleasesPriority(){AlertPolicy p=new AlertPolicy();p.acceptRing("door",1000);assertTrue(p.reminderAllowed(46000,46001));assertFalse(p.reminderAllowed(47000,46001));}
}
