package io.unifiedtv.preview;

import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Owner's TCL test, 25 October 2026: phone setup said "Wrong address", the steps were unclear, the QR code swapped to a
 * sign-up page, API-Football's key was hard to find (and blank behind a VPN), and the voice pane implied an assistant.
 */
public class PhoneSetupClarityTest {
    private final List<String> tv = new ArrayList<>();
    private static PhoneSetupServer.Request get() { return PhoneFlowsTest.req("GET", null, null); }

    // --- 8: the phone's Host header
    @Test public void theTvsOwnAddressIsAcceptedHoweverThePhoneWritesIt() throws Exception {
        InetAddress tv4 = InetAddress.getByName("192.168.1.5");
        for (String ok : new String[]{"192.168.1.5:51234", "192.168.1.5", "192.168.1.5.:51234", " 192.168.1.5:51234 ", "192.168.1.5."})
            assertTrue(ok, PhoneSetupServer.host(ok, tv4, 51234));
        for (String bad : new String[]{"192.168.1.5:51235", "192.168.1.6:51234", "evil.example:51234", "localhost:51234", "tv.local", "192.168.1.5:51234:1", "", "[::1]:51234", "192.168.001.5x"})
            assertFalse("refused: " + bad, PhoneSetupServer.host(bad, tv4, 51234));
        assertFalse(PhoneSetupServer.host(null, tv4, 51234));
        InetAddress tv6 = InetAddress.getByName("fd00::5");
        assertTrue(PhoneSetupServer.host("[fd00::5]:51234", tv6, 51234)); assertTrue(PhoneSetupServer.host("[FD00:0:0:0:0:0:0:5]", tv6, 51234));
        assertTrue("a zone id is ignored", PhoneSetupServer.host("[fd00::5%25wlan0]:51234", tv6, 51234));
        assertFalse(PhoneSetupServer.host("fd00::5", tv6, 51234));
    }
    @Test public void aBrowserTryingHttpsFirstIsClosedAtOnce() throws Exception {
        byte[] hello = {0x16, 0x03, 0x01, 0x02, 0x00, 0x01};
        assertNull(PhoneSetupServer.read(new ByteArrayInputStream(hello), null));
        PhoneSetupServer.Request r = PhoneSetupServer.read(new ByteArrayInputStream("GET http://192.168.1.5:51234/abc/ HTTP/1.1\r\nHost: proxy\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1)), null);
        assertEquals("/abc/", r.path); assertEquals("the absolute form's address is the host", "192.168.1.5:51234", r.headers.get("host"));
    }

    // --- 9: plain numbered steps; one QR code that always opens this TV's page
    @Test public void everyTvScreenStartsWithWhatTheOneCodeOpens() {
        for (String id : PhoneService.IDS) {
            PhoneService s = PhoneService.of(null, id); String[] steps = SetupPages.steps(s);
            assertTrue(id, steps.length >= 2); assertEquals(id, "1. " + PhoneService.SCAN, steps[0]);
            for (int i = 0; i < steps.length; i++) { assertTrue(steps[i].startsWith((i + 1) + ". ")); assertFalse("the TV never offers a second code", steps[i].toLowerCase(Locale.ROOT).contains("sign-up page code")); }
        }
        assertTrue(PhoneService.SCAN.contains("opens a setup page from this TV"));
        assertEquals("Opens a setup page on this TV", SetupPages.OPENS);
        assertEquals("No camera? On your phone, open this TV's address 192.168.1.5:51234 and enter the code 123 456.", SetupPages.fallback("http://192.168.1.5:51234", "123456"));
        String football = String.join(" ", SetupPages.steps(PhoneService.of(null, PhoneService.FOOTBALL)));
        assertTrue(football.contains("Get a free key")); assertTrue(football.contains("Account → My Access"));
        assertTrue(SetupPages.foundWords("homeassistant.local").startsWith("We found Home Assistant at homeassistant.local."));
        assertTrue(SetupPages.VPN_HINT.contains("NordVPN may be blocking your home network"));
    }
    @Test public void theFootballPhonePageHasTheKeyLinksTheWhereAndThePasteBox() throws Exception {
        String page = new PhoneFlows.Form(PhoneService.football(v -> PhoneFlows.Verdict.OK), tv::add).handle("", get()).html;
        assertTrue("Get a free key: prominent, new tab", page.contains("<a class=\"button\" href=\"https://dashboard.api-football.com/register\" target=\"_blank\" rel=\"noopener noreferrer\">Get a free key</a>"));
        assertTrue(page.contains("href=\"https://dashboard.api-football.com/profile?access\"")); assertTrue(page.contains("Already signed up? Open your key"));
        assertTrue(page.contains(PhoneService.FOOTBALL_WHERE)); assertTrue(page.contains("iCloud Private Relay")); assertTrue(page.contains("Limit IP Address Tracking"));
        assertTrue(page.contains("One key works for all the API-SPORTS sports"));
        assertTrue("the paste box is on the same page", page.contains("<textarea id=\"key\" name=\"key\""));
        assertTrue(page.contains("<h2>What happens next</h2>")); assertTrue(page.indexOf("<ol>") < page.indexOf("<textarea"));
    }
    @Test public void homeAssistantIsNamedAsTheHouseholdKnowsIt() throws Exception {
        InetAddress ha = InetAddress.getByName("192.168.1.9");
        assertEquals("homeassistant.local", PhoneSetup.haName("http://192.168.1.9:8123", n -> Collections.singletonList(ha)));
        assertEquals("192.168.1.9", PhoneSetup.haName("http://192.168.1.9:8123", n -> Collections.emptyList()));
        assertEquals("192.168.1.9:8443", PhoneSetup.haName("http://192.168.1.9:8443", n -> Collections.emptyList()));
        assertEquals("homeassistant.local", PhoneSetup.haName("http://homeassistant.local:8123", n -> { throw new AssertionError("no lookup needed"); }));
    }

    // --- API-Football refusing because of a VPN or proxy (owner's screenshot)
    @Test public void aVpnRefusalIsNotABadKey() throws Exception {
        try { ApiFootball.response(new JSONObject("{\"errors\":{\"access\":\"It seems that you are using a Proxy or a VPN. Please disable these and log back in.\"},\"response\":[]}")); fail(); }
        catch (ApiFootball.Refused r) {
            assertTrue(r.proxy()); assertTrue(r.vpn(false)); assertFalse("the wording isn't kept", r.getMessage().contains("disable"));
            assertEquals(PhoneFlows.Verdict.VPN, ApiFootball.verdict(r, false));
        }
        try { ApiFootball.response(new JSONObject("{\"errors\":{\"access\":\"Your access is restricted\"},\"response\":[]}")); fail(); }
        catch (ApiFootball.Refused r) { assertFalse(r.vpn(false)); assertTrue("an access refusal while the TV is on a VPN", r.vpn(true)); }
        assertTrue(new ApiFootball.Refused("403").vpn(true)); assertFalse(new ApiFootball.Refused("403").vpn(false));
        assertEquals(PhoneFlows.Verdict.REFUSED, ApiFootball.verdict(new ApiFootball.Refused("token"), true));
        assertTrue(ApiFootball.proxyWords("Proxy detected")); assertFalse(ApiFootball.proxyWords("Missing application key"));
        assertEquals("API-Football refused the request because NordVPN is on. Fixtures update when NordVPN is off, or allow API-Football in NordVPN's split tunnelling if it offers it.", ApiFootball.VPN_WORDS);
        String key = "0123456789abcdef0123456789abcdef";
        PhoneFlows.Form flow = new PhoneFlows.Form(PhoneService.football(v -> PhoneFlows.Verdict.VPN), tv::add);
        String page = flow.handle("save", PhoneFlowsTest.req("POST", PhoneFlowsTest.map("key", key), null)).html;
        assertTrue(page.contains("because a VPN is on")); assertFalse(page.contains(key));
        assertTrue(tv.contains(ApiFootball.VPN_WORDS)); for (String line : tv) assertFalse(line.contains(key));
    }

    // --- 3: the household's assistant, set up on the phone, labelled not tested
    @Test public void theAssistantIsSavedNotClaimed() throws Exception {
        assertEquals("https://relay.example.org/ask", Assistant.endpoint("https://relay.example.org/ask/"));
        assertEquals("http://192.168.1.30:8080", Assistant.endpoint("http://192.168.1.30:8080"));
        assertEquals("http://relay.local", Assistant.endpoint("http://relay.local"));
        for (String bad : new String[]{"", "http://relay.example.org", "ftp://x.local", "https://u:p@relay.example.org", "https://relay.example.org/?k=1", "relay.example.org"})
            assertNull(bad, Assistant.endpoint(bad));
        PhoneService s = PhoneService.of(null, PhoneService.ASSISTANT); assertNotNull(s);
        List<Map<String, String>> got = new ArrayList<>();
        PhoneFlows.Form flow = new PhoneFlows.Form(PhoneService.assistant(v -> { got.add(v); return PhoneFlows.Verdict.OK; }), tv::add);
        String page = flow.handle("", get()).html;
        assertTrue(page.contains("not tested")); for (String claim : new String[]{"GPT", "OpenAI", "Claude"}) assertFalse(claim, page.contains(claim));
        assertTrue(flow.handle("save", PhoneFlowsTest.req("POST", PhoneFlowsTest.map("endpoint", "http://example.org", "key", "abcdefghijklmnop1234"), null)).html.contains("doesn't look like an https:// address"));
        PhoneSetupServer.Response ok = flow.handle("save", PhoneFlowsTest.req("POST", PhoneFlowsTest.map("endpoint", "https://relay.example.org", "key", "abcdefghijklmnop1234"), null));
        assertTrue(ok.success); assertTrue(ok.html.contains("not tested")); assertEquals("https://relay.example.org", got.get(0).get("endpoint"));
        assertTrue(HomeUtilities.voiceLabel(false).contains("no assistant set up")); assertEquals("Ask your TV", HomeUtilities.voiceLabel(true));
    }
}
