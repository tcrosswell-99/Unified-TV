package io.unifiedtv.preview;

import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/**
 * OD-29: what Unified TV asks NordVPN for and the WireGuard set-up it builds, with made-up answers only (no network, no
 * real account, server or key). The JSON below is written for these tests in the shape NordVPN's API is documented to
 * use by its own Linux app and community guides; every key in it is a made-up 32-byte value.
 */
public class NordLynxTest {
    /** Made-up WireGuard keys: 32 bytes each, in Base64. */
    static final String PRIVATE = key(1), SERVER_KEY = key(101), OTHER_KEY = key(151);
    static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    static String key(int seed) { byte[] b = new byte[32]; for (int i = 0; i < 32; i++) b[i] = (byte) (seed + i); return Base64.getEncoder().encodeToString(b); }

    static final String CREDENTIALS = "{\"id\":1,\"created_at\":\"2026-01-01 00:00:00\",\"updated_at\":\"2026-01-01 00:00:00\","
        + "\"username\":\"made-up-user\",\"password\":\"made-up-password\",\"nordlynx_private_key\":\"" + PRIVATE + "\"}";
    static final String COUNTRIES = "[{\"id\":227,\"name\":\"United Kingdom\",\"code\":\"GB\",\"cities\":[{\"id\":1,\"name\":\"Example City\"}]},"
        + "{\"id\":81,\"name\":\"Germany\",\"code\":\"DE\"},{\"id\":-1,\"name\":\"Broken\",\"code\":\"XX\"},{\"id\":5,\"name\":\"\",\"code\":\"FR\"},"
        + "{\"id\":9,\"name\":\"No code\",\"code\":\"France\"}]";
    static String server(String name, String host, String station, String key, String code, String tech) {
        return "{\"id\":7,\"name\":\"" + name + "\",\"station\":\"" + station + "\",\"hostname\":\"" + host + "\",\"load\":12,"
            + "\"locations\":[{\"country\":{\"id\":227,\"name\":\"Somewhere\",\"code\":\"" + code + "\"}}],"
            + "\"technologies\":[{\"id\":3,\"name\":\"OpenVPN UDP\",\"identifier\":\"openvpn_udp\",\"metadata\":[]},"
            + "{\"id\":35,\"name\":\"Wireguard\",\"identifier\":\"" + tech + "\",\"metadata\":[{\"name\":\"public_key\",\"value\":\"" + key + "\"}]}]}";
    }
    static final String RECOMMENDED = "[" + server("United Kingdom #9001", "uk9001.example.nordvpn.com", "198.51.100.7", SERVER_KEY, "GB", "wireguard_udp") + ","
        + server("No WireGuard", "uk9002.example.nordvpn.com", "198.51.100.8", OTHER_KEY, "GB", "openvpn_tcp") + ","
        + server("Bad key", "uk9003.example.nordvpn.com", "198.51.100.9", "not-a-key", "GB", "wireguard_udp") + ","
        + server("By name only", "de9004.example.nordvpn.com", "not an address", OTHER_KEY, "DE", "wireguard_udp") + "]";

    /** A fake NordVPN: the answer for each URL, and every request it saw. */
    static final class FakeNord implements NordLynx.Http {
        final Map<String, NordLynx.Reply> answers = new HashMap<>(); final List<String> urls = new ArrayList<>(), auth = new ArrayList<>();
        boolean offline;
        FakeNord on(String url, int status, String body) { answers.put(url, new NordLynx.Reply(status, body)); return this; }
        @Override public NordLynx.Reply get(String url, String authorization) throws IOException {
            urls.add(url); auth.add(authorization);
            if (offline) throw new IOException("offline");
            for (Map.Entry<String, NordLynx.Reply> e : answers.entrySet()) if (url.startsWith(e.getKey())) return e.getValue();
            return new NordLynx.Reply(404, "");
        }
    }

    @Test public void theTokenGoesOnceAsBasicAuthAndOnlyTheKeyComesBack() {
        FakeNord nord = new FakeNord().on(NordLynx.API + NordLynx.CREDENTIALS, 200, CREDENTIALS);
        NordLynx.KeyResult r = NordLynx.fetchKey(nord, TOKEN);
        assertEquals(PhoneFlows.Verdict.OK, r.verdict); assertEquals(PRIVATE, r.key);
        assertEquals(Collections.singletonList("https://api.nordvpn.com/v1/users/services/credentials"), nord.urls);
        assertEquals("Basic " + Base64.getEncoder().encodeToString(("token:" + TOKEN).getBytes(StandardCharsets.UTF_8)), nord.auth.get(0));
    }
    @Test public void refusedBusyAndOfflineAreToldApart() {
        assertEquals(PhoneFlows.Verdict.REFUSED, NordLynx.fetchKey(new FakeNord().on(NordLynx.API, 401, "{\"errors\":{\"message\":\"Unauthorized\"}}"), TOKEN).verdict);
        assertEquals(PhoneFlows.Verdict.REFUSED, NordLynx.fetchKey(new FakeNord().on(NordLynx.API, 403, ""), TOKEN).verdict);
        assertEquals(PhoneFlows.Verdict.BUSY, NordLynx.fetchKey(new FakeNord().on(NordLynx.API, 429, ""), TOKEN).verdict);
        assertEquals(PhoneFlows.Verdict.OFFLINE, NordLynx.fetchKey(new FakeNord().on(NordLynx.API, 503, ""), TOKEN).verdict);
        FakeNord down = new FakeNord(); down.offline = true;
        assertEquals(PhoneFlows.Verdict.OFFLINE, NordLynx.fetchKey(down, TOKEN).verdict);
        // An answer without a usable key is a refusal (an account without NordVPN, say); nothing is returned.
        NordLynx.KeyResult none = NordLynx.fetchKey(new FakeNord().on(NordLynx.API, 200, "{\"username\":\"x\",\"nordlynx_private_key\":\"short\"}"), TOKEN);
        assertEquals(PhoneFlows.Verdict.REFUSED, none.verdict); assertNull(none.key);
        assertEquals(PhoneFlows.Verdict.REFUSED, NordLynx.fetchKey(new FakeNord(), "").verdict);
    }
    @Test public void keysAreThirtyTwoBytesOfBase64() {
        assertTrue(NordLynx.validKey(PRIVATE)); assertFalse(NordLynx.validKey(null)); assertFalse(NordLynx.validKey("abc="));
        assertFalse(NordLynx.validKey(PRIVATE.substring(1) + "A")); assertNull(NordLynx.privateKey("not json")); assertNull(NordLynx.privateKey("{}"));
    }

    @Test public void countriesKeepIdNameAndCodeOnly() {
        List<NordLynx.Country> c = NordLynx.countries(COUNTRIES);
        assertEquals(2, c.size()); assertEquals("Germany", c.get(0).name); assertEquals(81, c.get(0).id);
        assertEquals("GB", NordLynx.country(c, "gb").code); assertNull(NordLynx.country(c, "FR"));
        String kept = NordLynx.countriesJson(c); assertFalse("no cities kept", kept.contains("Example City"));
        assertEquals(2, NordLynx.countries(kept).size()); assertTrue(NordLynx.countries("oops").isEmpty());
    }
    @Test public void onlyServersOfferingNordLynxWithAKeyAreUsed() {
        List<NordLynx.Server> s = NordLynx.servers(RECOMMENDED);
        assertEquals(2, s.size());
        NordLynx.Server uk = s.get(0);
        assertEquals("United Kingdom #9001", uk.name); assertEquals(SERVER_KEY, uk.publicKey); assertEquals("GB", uk.country);
        assertEquals("the address, so no DNS look-up is needed", "198.51.100.7:51820", uk.endpoint());
        assertEquals("by name when NordVPN gave no address", "de9004.example.nordvpn.com:51820", s.get(1).endpoint());
        assertTrue(NordLynx.servers("[]").isEmpty()); assertTrue(NordLynx.servers("{\"error\":1}").isEmpty());
    }
    @Test public void recommendationsAskForNordLynxAndTheChosenCountry() {
        String fastest = NordLynx.recommendations(null, 5), uk = NordLynx.recommendations(227, 5);
        assertEquals("https://api.nordvpn.com/v1/servers/recommendations?filters%5Bservers_technologies%5D%5Bidentifier%5D=wireguard_udp&limit=5", fastest);
        assertTrue(uk.contains("&filters%5Bcountry_id%5D=227&")); assertTrue(NordLynx.recommendations(null, 99).endsWith("limit=10"));
    }

    @Test public void theConfigHasNordLynxsAddressDnsPortAndKeepalive() {
        String config = NordLynx.config(PRIVATE, NordLynx.servers(RECOMMENDED).get(0));
        assertTrue(config.startsWith("[Interface]\nPrivateKey = " + PRIVATE + "\nAddress = 10.5.0.2/32\nDNS = 103.86.96.100, 103.86.99.100\n"));
        assertTrue(config.contains("\n[Peer]\nPublicKey = " + SERVER_KEY + "\n"));
        assertTrue(config.contains("\nEndpoint = 198.51.100.7:51820\n")); assertTrue(config.endsWith("\nPersistentKeepalive = 25\n"));
        try { NordLynx.config("bad", NordLynx.servers(RECOMMENDED).get(0)); fail(); } catch (IllegalArgumentException e) { assertFalse("no key in the message", String.valueOf(e.getMessage()).contains(PRIVATE)); }
    }
    @Test public void theHomeNetworkStaysOutOfTheTunnel() {
        List<String> allowed = NordLynx.allowedIps();
        String line = String.join(", ", allowed);
        assertFalse("never everything", allowed.contains("0.0.0.0/0") || allowed.contains("::/0"));
        // Home: the NAS, Home Assistant, AirPlay and DLNA discovery (SSDP, mDNS), the phone setup page.
        for (String home : new String[]{"192.168.1.20", "10.0.0.5", "172.16.4.2", "172.31.255.254", "169.254.10.1", "127.0.0.1", "224.0.0.251", "239.255.255.250", "255.255.255.255",
            "fe80::1", "fd12:3456::1", "ff02::fb", "::1"})
            assertFalse(home + " must stay on the home network: " + line, covered(allowed, home));
        // The internet, NordVPN's DNS and public neighbours of the private ranges go through NordVPN, IPv4 and IPv6.
        for (String out : new String[]{"8.8.8.8", "1.1.1.1", "103.86.96.100", "103.86.99.100", "172.15.255.255", "172.32.0.1", "192.167.1.1", "192.169.0.1", "11.0.0.1", "223.255.255.255",
            "2001:4860:4860::8888", "2a00:1450::1", "fbff::1", "fec0::1"})
            assertTrue(out + " must go through NordVPN: " + line, covered(allowed, out));
        // Exactly everything else: the blocks don't overlap and add up to the whole IPv4 space less the home ranges.
        BigInteger v4 = BigInteger.ZERO, home = BigInteger.ZERO;
        for (String c : allowed) if (!c.contains(":")) v4 = v4.add(NordLynx.Block.parse(c).size());
        for (String c : NordLynx.LOCAL_V4) home = home.add(NordLynx.Block.parse(c).size());
        assertEquals(BigInteger.ONE.shiftLeft(32).subtract(home), v4);
        for (int i = 0; i < allowed.size(); i++) for (int j = i + 1; j < allowed.size(); j++) {
            NordLynx.Block a = NordLynx.Block.parse(allowed.get(i)), b = NordLynx.Block.parse(allowed.get(j));
            if (a.bits == b.bits) assertTrue(allowed.get(i) + " overlaps " + allowed.get(j), a.last().compareTo(b.first()) < 0 || b.last().compareTo(a.first()) < 0);
        }
    }
    @Test public void blocksAreWrittenPlainly() {
        assertEquals(Arrays.asList("0.0.0.0/1", "128.0.0.0/2", "192.0.0.0/3"), NordLynx.except("0.0.0.0/0", "224.0.0.0/3"));
        assertEquals(Arrays.asList("::/1", "8000::/2", "c000::/3", "e000::/4", "f000::/5", "f800::/6", "fe00::/7"), NordLynx.except("::/0", "fc00::/7"));
        assertEquals(Collections.singletonList("2001:db8::1/128"), NordLynx.except("2001:db8::/127", "2001:db8::/128"));
        assertTrue(NordLynx.contains("10.0.0.0/8", "10.200.3.4")); assertFalse(NordLynx.contains("10.0.0.0/8", "11.0.0.0")); assertFalse(NordLynx.contains("10.0.0.0/8", "::1"));
    }
    static boolean covered(List<String> blocks, String address) { for (String b : blocks) if (NordLynx.contains(b, address)) return true; return false; }
}
