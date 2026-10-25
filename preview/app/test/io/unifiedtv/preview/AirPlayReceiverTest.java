package io.unifiedtv.preview;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Spec 7.1: the AirPlay glyph needs this TV's own address answering for AirPlay; an installed app alone is not enough. */
public class AirPlayReceiverTest {
    @Test public void airPinIsFoundByItsOwnNameInAnySpelling() {
        assertTrue(AirPlayReceiver.isAirPin("AirPin LITE"));
        assertTrue(AirPlayReceiver.isAirPin("AirPin(LITE)"));
        assertTrue(AirPlayReceiver.isAirPin("airpin pro"));
        assertFalse(AirPlayReceiver.isAirPin("AirScreen"));
        assertFalse(AirPlayReceiver.isAirPin(null));
    }
    @Test public void onlyAServiceAnsweringFromThisTvCounts() {
        Set<String> own = new HashSet<>(Arrays.asList("192.0.2.10"));
        assertTrue(AirPlayReceiver.ours(own, "http://192.0.2.10:7000"));
        assertFalse("another device on the network", AirPlayReceiver.ours(own, "http://192.0.2.44:7000"));
        assertFalse(AirPlayReceiver.ours(own, "not a url"));
        assertFalse(AirPlayReceiver.ours(Collections.emptySet(), "http://192.0.2.10:7000"));
    }
}
