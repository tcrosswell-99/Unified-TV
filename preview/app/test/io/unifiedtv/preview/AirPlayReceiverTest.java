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
    // AirPin LITE may be found under its own address, IPv6, or the TV's loopback when the service is on the same TV.
    @Test public void aServiceOnThisTvCountsWhateverAddressAndroidReports() throws Exception {
        Set<String> own = new HashSet<>(Arrays.asList("192.0.2.10", java.net.InetAddress.getByName("fe80::1").getHostAddress()));   // as the TV lists its own
        assertTrue(AirPlayReceiver.ours(own, java.net.InetAddress.getByName("127.0.0.1")));
        assertTrue(AirPlayReceiver.ours(own, java.net.InetAddress.getByName("192.0.2.10")));
        assertTrue(AirPlayReceiver.ours(own, java.net.InetAddress.getByName("fe80::1")));
        assertFalse(AirPlayReceiver.ours(own, java.net.InetAddress.getByName("192.0.2.44")));
        assertFalse(AirPlayReceiver.ours(own, (java.net.InetAddress) null));
    }
    @Test public void onlyAnAnswerThatIsAirPlayCounts() {
        assertTrue(AirPlayReceiver.airPlayAnswer("HTTP/1.1 200 OK\r\nServer: AirTunes/220.68\r\n\r\n"));
        assertTrue(AirPlayReceiver.airPlayAnswer("RTSP/1.0 200 OK\r\nContent-Type: application/x-apple-binary-plist\r\n\r\n"));
        assertTrue(AirPlayReceiver.airPlayAnswer("HTTP/1.1 200 OK\r\nContent-Type: text/x-apple-plist+xml\r\n\r\n"));
        assertFalse("any other web server", AirPlayReceiver.airPlayAnswer("HTTP/1.1 200 OK\r\nServer: nginx\r\n\r\n"));
        assertFalse(AirPlayReceiver.airPlayAnswer("SSH-2.0-OpenSSH airplay"));
        assertFalse(AirPlayReceiver.airPlayAnswer(null));
        assertFalse("nothing listening", AirPlayReceiver.answers(0));
    }
    @Test public void openingAirPinAsksHomeToLookAgainOnce() {
        AirPlayReceiver.takeReturn(); assertFalse(AirPlayReceiver.takeReturn());
        AirPlayReceiver.opened(); assertTrue(AirPlayReceiver.takeReturn()); assertFalse("only once", AirPlayReceiver.takeReturn());
        assertTrue("several checks over half a minute", AirPlayReceiver.FOLLOW_UP_MS.length >= 3 && AirPlayReceiver.FOLLOW_UP_MS[AirPlayReceiver.FOLLOW_UP_MS.length - 1] <= 30_000);
        assertEquals(Arrays.asList("_airplay._tcp.", "_raop._tcp."), Arrays.asList(AirPlayReceiver.TYPES));
    }
}
