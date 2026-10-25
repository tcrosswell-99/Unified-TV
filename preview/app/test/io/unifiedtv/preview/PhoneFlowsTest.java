package io.unifiedtv.preview;

import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * What phone setup asks and saves: Home Assistant's sign-in (OAuth code exchanged on the TV, then the TV's own token),
 * the pasted-token fallback, plain-HTTP consent, and the generic key form every other service uses.
 */
public class PhoneFlowsTest {
    static final String ORIGIN = "http://192.168.1.20:50000", SESSION = ORIGIN + "/TOKENTOKENTOKENTOKEN12";
    static PhoneSetupServer.Request req(String method, Map<String, String> form, Map<String, String> query) {
        PhoneSetupServer.Request r = new PhoneSetupServer.Request(method, "/", new HashMap<>(), query == null ? new HashMap<>() : query, form == null ? new HashMap<>() : form, null);
        r.origin = ORIGIN; r.session = SESSION; return r;
    }
    static Map<String, String> map(String... kv) { Map<String, String> m = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]); return m; }

    /** A Home Assistant that records what the TV did. */
    static final class FakeHa implements PhoneFlows.HaNet {
        boolean isHa = true; int checkStatus = 200; String minted = "own-long-lived-token-0123456789", exchangedCode, clientId, revoked, checkedToken;
        @Override public boolean isHomeAssistant(String base) { return isHa; }
        @Override public JSONObject exchange(String base, String code, String clientId) throws Exception {
            exchangedCode = code; this.clientId = clientId;
            return new JSONObject().put("access_token", "short-access").put("refresh_token", "refresh-1").put("expires_in", 1800).put("token_type", "Bearer");
        }
        @Override public String mint(String base, String accessToken) { assertEquals("short-access", accessToken); return minted; }
        @Override public void revoke(String base, String refreshToken) { revoked = refreshToken; }
        @Override public int check(String base, String token) { checkedToken = token; return checkStatus; }
    }
    static final class Saved { String base, token, how; boolean consent; int times; }

    private final List<String> tv = new ArrayList<>();
    private PhoneFlows.Ha ha(FakeHa net, Saved saved, String found) {
        return new PhoneFlows.Ha(() -> found, net, (b, t, c, h) -> { saved.base = b; saved.token = t; saved.consent = c; saved.how = h; saved.times++; }, tv::add, new SecureRandom(), "Unified TV on the TCL");
    }
    private static String between(String s, String a, String b) { int i = s.indexOf(a) + a.length(); return s.substring(i, s.indexOf(b, i)); }

    @Test public void signInWithHomeAssistantExchangesTheCodeOnTheTvAndKeepsOneToken() throws Exception {
        FakeHa net = new FakeHa(); Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, "http://192.168.1.5:8123");
        String first = flow.handle("", req("GET", null, null)).html;
        assertTrue("prefilled with what the TV found, named", first.contains("We found Home Assistant at <b>http://192.168.1.5:8123</b>."));
        assertTrue("numbered steps say what happens", first.contains("<ol>") && first.contains("Home Assistant's own sign-in page opens")); assertTrue(first.contains("value=\"http://192.168.1.5:8123\""));
        assertTrue(first.contains("Sign in with Home Assistant")); assertTrue(first.contains("Paste a long-lived token instead"));
        // Plain HTTP: one plain question, nothing started yet.
        String ask = flow.handle("ha/start", req("POST", map("address", "http://192.168.1.5:8123", "how", "signin"), null)).html;
        assertTrue(ask.contains("Your Home Assistant uses an unencrypted connection on your home network. Allow it?"));
        assertFalse(ask.contains("/auth/authorize"));
        String go = flow.handle("ha/start", req("POST", map("address", "http://192.168.1.5:8123", "how", "signin", "allowHttp", "yes"), null)).html;
        String to = between(go, "content=\"0;url=", "\"").replace("&amp;", "&");
        URI authorize = URI.create(to);
        assertEquals("/auth/authorize", authorize.getPath()); assertEquals("192.168.1.5", authorize.getHost());
        Map<String, String> q = PhoneSetupServer.decode(authorize.getRawQuery());
        assertEquals("code", q.get("response_type")); assertEquals("client_id is this page's origin", ORIGIN + "/", q.get("client_id"));
        assertEquals("redirect_uri on the same origin, inside the session", SESSION + "/ha/done", q.get("redirect_uri"));
        String state = q.get("state"); assertTrue(state.length() >= 20);
        // A forged answer with the wrong state saves nothing and uses up the sign-in.
        PhoneSetupServer.Response forged = flow.handle("ha/done", req("GET", null, map("code", "c1", "state", "wrong")));
        assertFalse(forged.success); assertEquals(0, saved.times); assertNull(net.exchangedCode);
        assertTrue(flow.handle("ha/done", req("GET", null, map("code", "c1", "state", state))).html.contains("already been used"));
        // Again, properly.
        go = flow.handle("ha/start", req("POST", map("address", "http://192.168.1.5:8123", "how", "signin", "allowHttp", "yes"), null)).html;
        state = PhoneSetupServer.decode(URI.create(between(go, "content=\"0;url=", "\"").replace("&amp;", "&")).getRawQuery()).get("state");
        PhoneSetupServer.Response done = flow.handle("ha/done", req("GET", null, map("code", "the-code", "state", state)));
        assertTrue(done.success);
        assertEquals("the-code", net.exchangedCode); assertEquals(ORIGIN + "/", net.clientId);
        assertEquals("the TV's own token, checked, then saved", net.minted, net.checkedToken);
        assertEquals(1, saved.times); assertEquals("http://192.168.1.5:8123", saved.base); assertEquals(net.minted, saved.token); assertTrue(saved.consent); assertEquals("signin", saved.how);
        assertEquals("the sign-in's refresh token is retired", "refresh-1", net.revoked);
        assertFalse("no secret on the phone's page", done.html.contains(net.minted) || done.html.contains("short-access"));
        for (String line : tv) assertFalse("no secret on the TV: " + line, line.contains("token-0123") || line.contains("short-access") || line.contains("the-code"));
        assertTrue(tv.contains("Signing in to Home Assistant…"));
    }

    @Test public void cancelledOrFailedSignInSavesNothing() throws Exception {
        FakeHa net = new FakeHa(); Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, "https://ha.example.invalid");
        String go = flow.handle("ha/start", req("POST", map("how", "signin"), null)).html;
        assertTrue("https needs no question", go.contains("/auth/authorize"));
        assertTrue(flow.handle("ha/done", req("GET", null, map("error", "access_denied"))).html.contains("cancelled"));
        go = flow.handle("ha/start", req("POST", map("how", "signin"), null)).html;
        String state = PhoneSetupServer.decode(URI.create(between(go, "content=\"0;url=", "\"").replace("&amp;", "&")).getRawQuery()).get("state");
        net.minted = null;
        assertFalse(flow.handle("ha/done", req("GET", null, map("code", "c", "state", state))).success);
        assertEquals(0, saved.times); assertEquals("a failed attempt still retires its refresh token", "refresh-1", net.revoked);
    }

    @Test public void notHomeAssistantOrABadAddressIsSaidPlainly() throws Exception {
        FakeHa net = new FakeHa(); net.isHa = false; Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, null);
        String first = flow.handle("", req("GET", null, null)).html;
        assertTrue(first.contains("couldn't find Home Assistant")); assertFalse("the address field is open when nothing was found", first.contains("<summary>Use a different address"));
        assertTrue(flow.handle("ha/start", req("POST", map("address", "", "how", "signin"), null)).html.contains("Enter your Home Assistant's address"));
        assertTrue(flow.handle("ha/start", req("POST", map("address", "ha.local/admin?x", "how", "signin"), null)).html.contains("doesn't look right"));
        assertTrue(flow.handle("ha/start", req("POST", map("address", "https://ha.example.invalid", "how", "signin"), null)).html.contains("No Home Assistant answered"));
        assertEquals(0, saved.times);
    }

    @Test public void pastedTokenIsCheckedThenSavedAndNeverEchoed() throws Exception {
        FakeHa net = new FakeHa(); Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, "http://192.168.1.5:8123");
        String token = "eyJhbGciOiJIUzI1NiJ9.pasted.token-value";
        // Consent first: the token waits in memory and is not written into the question page.
        String ask = flow.handle("ha/start", req("POST", map("how", "token", "token", token, "address", "http://192.168.1.5:8123"), null)).html;
        assertTrue(ask.contains("Allow it?")); assertFalse(ask.contains(token));
        net.checkStatus = 401;
        String refused = flow.handle("ha/start", req("POST", map("how", "token", "allowHttp", "yes", "address", "http://192.168.1.5:8123"), null)).html;
        assertTrue(refused.contains("didn't accept that token")); assertFalse(refused.contains(token)); assertEquals(0, saved.times);
        net.checkStatus = 200;
        assertTrue(flow.handle("ha/start", req("POST", map("how", "token", "token", "short", "allowHttp", "yes"), null)).html.contains("doesn't look like a Home Assistant token"));
        PhoneSetupServer.Response ok = flow.handle("ha/start", req("POST", map("how", "token", "token", " " + token + "\n", "allowHttp", "yes", "address", "192.168.1.5"), null));
        assertTrue(ok.success); assertEquals(token, saved.token); assertEquals("token", saved.how); assertTrue(saved.consent); assertEquals("http://192.168.1.5:8123", saved.base);
    }

    @Test public void addressesAsPeopleTypeThem() {
        assertEquals("http://homeassistant.local:8123", PhoneFlows.haBase("homeassistant.local:8123"));
        assertEquals("http://homeassistant.local:8123", PhoneFlows.haBase("homeassistant.local"));
        assertEquals("http://192.168.1.5:8123", PhoneFlows.haBase(" 192.168.1.5 "));
        assertEquals("http://192.168.1.5:8123", PhoneFlows.haBase("http://192.168.1.5:8123/"));
        assertEquals("https://ha.example.org:443", PhoneFlows.haBase("https://ha.example.org"));
        assertEquals("http://[fd00::5]:8123", PhoneFlows.haBase("[fd00::5]:8123"));
        for (String bad : new String[]{"", "ha.local/lovelace", "http://user:pw@ha.local", "ftp://ha.local", "ha.local?x=1", "ha local", "http://ha.local:99999"})
            assertNull(bad, PhoneFlows.haBase(bad));
    }

    // --- the generic form (football, film posters, any later service)
    private PhoneService service(PhoneService.Submit submit) { return PhoneService.football(submit); }

    @Test public void keyFormChecksFormatThenTheServiceThenSaves() throws Exception {
        List<String> got = new ArrayList<>(); PhoneFlows.Verdict[] next = {PhoneFlows.Verdict.REFUSED};
        PhoneFlows.Form flow = new PhoneFlows.Form(service(v -> { got.add(v.get("key")); return next[0]; }), tv::add);
        String page = flow.handle("", req("GET", null, null)).html;
        assertTrue(page.contains("https://dashboard.api-football.com/register")); assertTrue(page.contains("<ol>")); assertTrue(page.contains("100 requests a day"));
        assertTrue(flow.handle("save", req("POST", map("key", "nope!"), null)).html.contains("doesn't look like an API-Football key"));
        assertTrue(got.isEmpty());
        String key = "0123456789abcdef0123456789abcdef";
        String refused = flow.handle("save", req("POST", map("key", key), null)).html;
        assertTrue(refused.contains("didn't accept that")); assertFalse("never echoed", refused.contains(key));
        next[0] = PhoneFlows.Verdict.OFFLINE; assertTrue(flow.handle("save", req("POST", map("key", key), null)).html.contains("couldn't reach API-Football"));
        next[0] = PhoneFlows.Verdict.BUSY; assertTrue(flow.handle("save", req("POST", map("key", key), null)).html.contains("busy"));
        next[0] = PhoneFlows.Verdict.OK;
        PhoneSetupServer.Response ok = flow.handle("save", req("POST", map("key", " 0123456789abcdef\n0123456789abcdef "), null));
        assertTrue(ok.success); assertEquals("whitespace from pasting is removed", key, got.get(got.size() - 1));
        assertTrue(ok.html.contains("choose your team on the TV"));
        for (String line : tv) assertFalse(line.contains(key));
        assertNull("unknown pages", flow.handle("other", req("GET", null, null)));
    }

    @Test public void tmdbAcceptsAKeyOrAReadAccessToken() throws Exception {
        List<String> got = new ArrayList<>();
        PhoneFlows.Form flow = new PhoneFlows.Form(PhoneService.tmdb(v -> { got.add(v.get("key")); return PhoneFlows.Verdict.OK; }), tv::add);
        assertTrue(flow.handle("", req("GET", null, null)).html.contains("not endorsed or certified by TMDB"));
        assertTrue(flow.handle("save", req("POST", map("key", "0123456789abcdef0123456789abcdef"), null)).success);
        assertTrue(flow.handle("save", req("POST", map("key", "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0.c2lnbmF0dXJl"), null)).success);
        assertFalse(flow.handle("save", req("POST", map("key", "not-a-key"), null)).success);
        assertTrue(TmdbKey.bearer("eyJabc")); assertFalse(TmdbKey.bearer("0123"));
        assertEquals(PhoneFlows.Verdict.OK, TmdbKey.verdict(200)); assertEquals(PhoneFlows.Verdict.REFUSED, TmdbKey.verdict(401)); assertEquals(PhoneFlows.Verdict.BUSY, TmdbKey.verdict(429)); assertEquals(PhoneFlows.Verdict.OFFLINE, TmdbKey.verdict(500));
    }

    @Test public void everyServiceDescribesItself() {
        for (String id : PhoneService.IDS) {
            PhoneService s = PhoneService.of(null, id);
            assertNotNull(id, s); assertEquals(id, s.id); assertFalse(s.title.isEmpty()); assertFalse(s.tvIntro.isEmpty()); assertFalse(s.tvDone.isEmpty());
            assertTrue(id + " has a custom flow or a form", s.custom != null || !s.fields.isEmpty());
            if (s.signup != null) assertTrue(s.signup.startsWith("https://"));
        }
        assertNull(PhoneService.of(null, "unknown"));
    }

    @Test public void apiFootballKeyCheckReadsTheAccountAndItsCount() throws Exception {
        assertArrayEquals(new int[]{12, 100}, ApiFootball.account(new JSONObject("{\"errors\":[],\"response\":{\"account\":{\"firstname\":\"x\"},\"subscription\":{\"plan\":\"Free\",\"active\":true},\"requests\":{\"current\":12,\"limit_day\":100}}}")));
        try { ApiFootball.account(new JSONObject("{\"errors\":{\"token\":\"Error/Missing application key\"},\"response\":[]}")); fail(); }
        catch (ApiFootball.Refused r) { assertEquals(PhoneFlows.Verdict.REFUSED, ApiFootball.verdict(r)); }
        try { ApiFootball.account(new JSONObject("{\"errors\":[],\"response\":{\"subscription\":{\"active\":false}}}")); fail(); }
        catch (ApiFootball.Refused r) { assertEquals(PhoneFlows.Verdict.REFUSED, ApiFootball.verdict(r)); }
        assertEquals(PhoneFlows.Verdict.BUSY, ApiFootball.verdict(new ApiFootball.Refused("requests")));
        assertEquals(PhoneFlows.Verdict.OFFLINE, ApiFootball.verdict(new IOException()));
    }

    // --- against a fake Home Assistant on this machine
    private MiniHttp fake;
    private final List<String> seen = Collections.synchronizedList(new ArrayList<>());
    @After public void down() { if (fake != null) fake.close(); }
    private String fakeHa() throws IOException {
        fake = new MiniHttp(x -> {
            String body = x.body, path = x.path, auth = x.header("Authorization");
            seen.add(x.method + " " + path + " " + body + (auth == null ? "" : " auth=" + auth));
            if (path.equals("/manifest.json")) return new MiniHttp.Response(200, "{\"name\":\"Home Assistant\",\"short_name\":\"Assistant\"}");
            if (path.equals("/auth/token")) {
                Map<String, String> f = PhoneSetupServer.decode(body);
                boolean ok = "authorization_code".equals(f.get("grant_type")) && "good-code".equals(f.get("code")) && "http://192.168.1.20:50000/".equals(f.get("client_id"));
                return new MiniHttp.Response(ok ? 200 : 400, ok ? "{\"access_token\":\"ACCESS\",\"expires_in\":1800,\"refresh_token\":\"REFRESH\",\"token_type\":\"Bearer\"}" : "{\"error\":\"invalid_request\"}");
            }
            if (path.equals("/api/")) { boolean ok = "Bearer GOOD".equals(auth); return new MiniHttp.Response(ok ? 200 : 401, ok ? "{\"message\":\"API running.\"}" : "401: Unauthorized"); }
            if (path.equals("/auth/revoke")) return new MiniHttp.Response(200, "");
            return new MiniHttp.Response(302, "").header("Location", "http://elsewhere.invalid/");
        });
        return fake.base();
    }

    @Test public void oauthCodeExchangeAgainstAFakeHomeAssistant() throws Exception {
        String base = fakeHa(); HaSignIn net = new HaSignIn(null);
        assertTrue(net.isHomeAssistant(base));
        JSONObject tokens = net.exchange(base, "good-code", ORIGIN + "/");
        assertEquals("ACCESS", tokens.getString("access_token")); assertEquals("REFRESH", tokens.getString("refresh_token"));
        try { net.exchange(base, "bad-code", ORIGIN + "/"); fail("a refused code is an error"); } catch (IOException expected) {}
        assertEquals(200, net.check(base, "GOOD")); assertEquals(401, net.check(base, "BAD"));
        net.revoke(base, "REFRESH");
        assertTrue(seen.contains("POST /auth/revoke token=REFRESH"));
        assertTrue(seen.stream().anyMatch(s -> s.startsWith("POST /auth/token grant_type=authorization_code&code=good-code&client_id=http%3A%2F%2F192.168.1.20%3A50000%2F")));
    }

    @Test public void longLivedTokenConversation() throws Exception {
        HaSignIn.Mint m = new HaSignIn.Mint("ACCESS", "Unified TV on the TCL · 25 Oct 2026, 21:04");
        JSONObject auth = new JSONObject(m.on("{\"type\":\"auth_required\",\"ha_version\":\"2026.10.0\"}"));
        assertEquals("auth", auth.getString("type")); assertEquals("ACCESS", auth.getString("access_token"));
        JSONObject ask = new JSONObject(m.on("{\"type\":\"auth_ok\"}"));
        assertEquals("auth/long_lived_access_token", ask.getString("type")); assertEquals(3650, ask.getInt("lifespan")); assertTrue(ask.getString("client_name").startsWith("Unified TV"));
        assertNull(m.on("{\"id\":7,\"type\":\"result\",\"success\":true,\"result\":\"other\"}")); assertFalse(m.finished());
        assertNull(m.on("{\"id\":1,\"type\":\"result\",\"success\":true,\"result\":\"LONG\"}"));
        assertTrue(m.finished()); assertEquals("LONG", m.token());
        HaSignIn.Mint refused = new HaSignIn.Mint("ACCESS", "n"); refused.on("{\"type\":\"auth_invalid\"}"); assertTrue(refused.finished()); assertNull(refused.token());
        HaSignIn.Mint failed = new HaSignIn.Mint("ACCESS", "n"); failed.on("{\"type\":\"auth_ok\"}"); failed.on("{\"id\":1,\"type\":\"result\",\"success\":false,\"error\":{\"code\":\"invalid_format\"}}"); assertNull(failed.token());
    }

    @Test public void tmdbCheckAgainstAFakeServer() throws Exception {
        fake = new MiniHttp(x -> {
            if (!x.path.equals("/3/authentication")) return new MiniHttp.Response(404, "");
            boolean ok = "api_key=0123456789abcdef0123456789abcdef".equals(x.query) || "Bearer eyJgood.a.b".equals(x.header("Authorization"));
            return new MiniHttp.Response(ok ? 200 : 401, ok ? "{\"success\":true,\"status_code\":1}" : "{\"success\":false,\"status_code\":7}");
        });
        String base = fake.base();
        assertEquals(PhoneFlows.Verdict.OK, TmdbKey.check(base, "0123456789abcdef0123456789abcdef"));
        assertEquals(PhoneFlows.Verdict.OK, TmdbKey.check(base, "eyJgood.a.b"));
        assertEquals(PhoneFlows.Verdict.REFUSED, TmdbKey.check(base, "ffffffffffffffffffffffffffffffff"));
        fake.close(); fake = null;
        assertEquals(PhoneFlows.Verdict.OFFLINE, TmdbKey.check(base, "0123456789abcdef0123456789abcdef"));
    }
}
