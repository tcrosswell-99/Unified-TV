package io.unifiedtv.preview;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetwork;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Owner's TCL, 25 October 2026: with NordVPN connected the media server "wasn't answering". A VPN that refuses apps a way
 * round it (EPERM when a connection is bound to Wi-Fi) must not fail every request: the TV retries at once over the default
 * route, which NordVPN's own "allow local network" setting lets through; and when nothing answers that way either, the
 * words name the VPN, never the server being off. Fake connections only: nothing here touches a network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30)
public class VpnHomeNetworkTest {
    /** A connection that answers 200 with a body, or fails as a no-bypass VPN or a silent server would. */
    static final class Fake extends HttpURLConnection {
        final IOException fail; final String body;
        Fake(URL u, IOException fail, String body) { super(u); this.fail = fail; this.body = body; }
        @Override public void connect() throws IOException { if (fail != null) throw fail; }
        @Override public int getResponseCode() throws IOException { connect(); return 200; }
        @Override public InputStream getInputStream() throws IOException { connect(); return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
        @Override public OutputStream getOutputStream() throws IOException { connect(); return new ByteArrayOutputStream(); }
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
    }
    static final IOException EPERM = new SocketException("Binding socket to network 101 failed: EPERM (Operation not permitted)");
    private final List<String> routes = new ArrayList<>();
    private final DlnaClient.Opener original = DlnaClient.opener;

    @After public void reset() {
        DlnaClient.opener = original; DlnaClient.lan = null;
        LocalDiscovery.vpn = false; LocalDiscovery.blocked = false; LocalDiscovery.vpnApp = "";
    }

    @Test public void aVpnThatRefusesTheHomeNetworkIsGoneRoundAtOnce() throws Exception {
        DlnaClient.lan = ShadowNetwork.newInstance(101); LocalDiscovery.vpn = true;
        DlnaClient.opener = (url, network) -> { routes.add(network == null ? "default" : "home"); return new Fake(url, network != null ? EPERM : null, "<ok/>"); };
        assertEquals("<ok/>", new String(DlnaClient.request("http://192.168.1.20:50001/desc.xml", null, null), StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("home", "default"), routes);
        assertTrue("remembered until the network changes", LocalDiscovery.blocked);
        routes.clear(); DlnaClient.request("http://192.168.1.20:50001/desc.xml", null, null);
        assertEquals("later requests skip the refused route", Collections.singletonList("default"), routes);
    }

    @Test public void silenceBehindAVpnNamesTheVpnNotTheServer() {
        DlnaClient.lan = null; LocalDiscovery.vpn = true; LocalDiscovery.vpnApp = "NordVPN";
        DlnaClient.opener = (url, network) -> new Fake(url, new SocketTimeoutException("connect timed out"), "");
        try { DlnaClient.request("http://192.168.1.20:50001/desc.xml", null, null); fail(); }
        catch (DlnaClient.Failure f) {
            assertEquals(DlnaClient.Failure.VPN, f.kind);
            String words = DlnaClient.Failure.plain(f.kind);
            assertTrue(words, words.startsWith("NordVPN is blocking your home network, so Unified TV can't reach your media server."));
            assertTrue(words.contains("allow devices on your local network (often called Local network discovery)"));
            assertFalse("never says the server is off", words.contains("switched on"));
        } catch (Exception e) { fail(e.toString()); }
    }

    @Test public void silenceStraightOverTheHomeNetworkIsTheServer() {
        DlnaClient.lan = ShadowNetwork.newInstance(102); LocalDiscovery.vpn = true;
        DlnaClient.opener = (url, network) -> new Fake(url, new ConnectException("refused"), "");
        try { DlnaClient.request("http://192.168.1.20:50001/desc.xml", null, null); fail(); }
        catch (DlnaClient.Failure f) { assertEquals("the VPN was gone round, so the server really didn't answer", DlnaClient.Failure.UNREACHABLE, f.kind); assertFalse(LocalDiscovery.blocked); }
        catch (Exception e) { fail(e.toString()); }
    }

    @Test public void pureRules() {
        assertEquals(DlnaClient.Failure.VPN, DlnaClient.Failure.unanswered(false, true));
        assertEquals(DlnaClient.Failure.UNREACHABLE, DlnaClient.Failure.unanswered(true, true));
        assertEquals(DlnaClient.Failure.UNREACHABLE, DlnaClient.Failure.unanswered(false, false));
        assertTrue(LocalDiscovery.refused(EPERM)); assertTrue(LocalDiscovery.refused(new IOException("wrapped", EPERM)));
        assertFalse(LocalDiscovery.refused(new SocketTimeoutException("connect timed out"))); assertFalse(LocalDiscovery.refused(new ConnectException("Connection refused")));
        String other = DlnaClient.Failure.plain(DlnaClient.Failure.VPN, "");
        assertTrue(other, other.startsWith("A VPN on this TV is blocking your home network")); assertFalse("NordVPN named only when it is the VPN", other.contains("NordVPN"));
        assertFalse(other.matches(".*\\d.*"));
    }

    @Test public void aSearchBehindAVpnSaysSo() {
        DlnaClient.Report r = new DlnaClient.Report(); r.vpn = true; r.vpnApp = "NordVPN";
        assertTrue(r.plain(), r.plain().startsWith("NordVPN is blocking your home network, so Unified TV can't find your media server."));
        assertTrue(r.plain().endsWith("then search again."));
        r.answered = 1; r.unreadable = 1; assertTrue("a server that answered is not the VPN", r.plain().startsWith("A media server answered"));
        assertTrue(new DlnaClient.Report().plain().startsWith("No media server found"));
    }

    @Test public void homeSaysNordVpnRatherThanNotAnswering() {
        assertTrue(HomeShelf.unanswered(true, "NordVPN").startsWith("NordVPN is blocking your home network."));
        assertTrue(HomeShelf.unanswered(true, "").startsWith("A VPN is blocking"));
        assertTrue(HomeShelf.unanswered(false, "NordVPN").startsWith("Your films and TV aren't answering"));
    }
}
