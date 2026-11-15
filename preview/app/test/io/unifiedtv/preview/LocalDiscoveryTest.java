package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.util.List;
public class LocalDiscoveryTest {
    static InetAddress ip(String s)throws Exception{return InetAddress.getByName(s);}
    @Test public void onlyHomeAssistantsOwnManifestCounts(){
        assertTrue(LocalDiscovery.homeAssistant("{\"name\":\"Home Assistant\",\"short_name\":\"Assistant\",\"start_url\":\"/?homescreen=1\"}"));
        assertFalse(LocalDiscovery.homeAssistant("{\"name\":\"Router admin\"}"));
        assertFalse(LocalDiscovery.homeAssistant("<html>Home Assistant</html>"));
        assertFalse(LocalDiscovery.homeAssistant(""));
    }
    @Test public void jellyfinAnswerMustComeFromTheServerItNames()throws Exception{
        String reply="{\"Address\":\"http://192.168.1.10:8096\",\"Id\":\"abc\",\"Name\":\"Living room NAS\",\"EndpointAddress\":null}";
        LocalDiscovery.Found f=LocalDiscovery.jellyfinReply(reply,ip("192.168.1.10"));
        assertEquals("Living room NAS",f.name);assertEquals("http://192.168.1.10:8096",f.base);
        assertNull("another host",LocalDiscovery.jellyfinReply(reply,ip("192.168.1.66")));
        assertNull("public address",LocalDiscovery.jellyfinReply("{\"Address\":\"http://8.8.8.8:8096\",\"Name\":\"x\"}",ip("8.8.8.8")));
        assertNull("not JSON",LocalDiscovery.jellyfinReply("hello",ip("192.168.1.10")));
        assertEquals("https://192.168.1.10:8920",LocalDiscovery.jellyfinReply("{\"Address\":\"https://192.168.1.10:8920\",\"Name\":\"x\"}",ip("192.168.1.10")).base);
    }
    @Test public void sweepStaysOnTheTvsOwnPrivateNetwork()throws Exception{
        List<InetAddress> hosts=LocalDiscovery.neighbours((Inet4Address)ip("192.168.1.42"));
        assertEquals(253,hosts.size());assertFalse(hosts.contains(ip("192.168.1.42")));assertFalse(hosts.contains(ip("192.168.1.0")));assertFalse(hosts.contains(ip("192.168.1.255")));
        for(InetAddress a:hosts)assertTrue(a.getHostAddress().startsWith("192.168.1."));
        assertTrue("never a public network",LocalDiscovery.neighbours((Inet4Address)ip("8.8.8.8")).isEmpty());
        assertTrue(LocalDiscovery.neighbours(null).isEmpty());
    }
    @Test public void ssdpLocationIsAcceptedOnlyFromItsOwnLocalHost()throws Exception{
        String reply="HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.1.10:50001/desc/device.xml\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
        assertEquals("http://192.168.1.10:50001/desc/device.xml",DlnaClient.location(reply,ip("192.168.1.10")));
        assertNull(DlnaClient.location(reply,ip("192.168.1.11")));
        assertNull(DlnaClient.location(reply.replace("192.168.1.10","8.8.8.8"),ip("8.8.8.8")));
        assertNull(DlnaClient.location("HTTP/1.1 200 OK\r\n\r\n",ip("192.168.1.10")));
    }
}
