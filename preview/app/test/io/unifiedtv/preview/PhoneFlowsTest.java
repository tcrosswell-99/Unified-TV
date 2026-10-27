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
        boolean isHa = true, unreachable; int checkStatus = 200; String minted = "own-long-lived-token-0123456789", exchangedCode, clientId, revoked, checkedToken, checkedBase, exchangedBase;
        /** What reachable() answers for a name (null: the base unchanged), and the VPN in the way after a failure. */
        String numeric, vpn; Exception exchangeError, mintError;
        @Override public boolean isHomeAssistant(String base) throws IOException { if (unreachable) throw new ConnectException("refused"); return isHa; }
        @Override public JSONObject exchange(String base, String code, String clientId) throws Exception {
            if (exchangeError != null) throw exchangeError;
            exchangedCode = code; this.clientId = clientId; exchangedBase = base;
            return new JSONObject().put("access_token", "short-access").put("refresh_token", "refresh-1").put("expires_in", 1800).put("token_type", "Bearer");
        }
        @Override public String mint(String base, String accessToken) throws Exception { if (mintError != null) throw mintError; assertEquals("short-access", accessToken); return minted; }
        @Override public void revoke(String base, String refreshToken) { revoked = refreshToken; }
        @Override public int check(String base, String token) throws IOException { if (checkStatus == -1) throw new SocketTimeoutException(); checkedToken = token; checkedBase = base; return checkStatus; }
        @Override public String reachable(String base) { return numeric != null && base.contains("homeassistant.local") ? numeric : base; }
        @Override public String vpn() { return vpn; }
    }
    static final class Saved { String base, token, how; boolean consent, broken; int times; }

    private final List<String> tv = new ArrayList<>(), steps = new ArrayList<>();
    private PhoneFlows.Ha ha(FakeHa net, Saved saved, String found) {
        return new PhoneFlows.Ha(() -> found, net, (b, t, c, h) -> { if (saved.broken) throw new IOException("keystore"); saved.base = b; saved.token = t; saved.consent = c; saved.how = h; saved.times++; }, tv::add, new SecureRandom(), "Unified TV on the TCL", PhoneFlows::display, steps::add);
    }
    /** Sign in pressed: the TV checks, then sends the phone to its own hand-over page (ha/go), which opens Home Assistant. */
    private String go(PhoneFlows.Ha flow, Map<String, String> form) throws Exception {
        PhoneSetupServer.Response begun = flow.handle("ha/start", req("POST", form, null));
        assertEquals("a redirect within this TV's page", 303, begun.status); assertEquals(SESSION + "/ha/go", begun.location);
        return flow.handle("ha/go", req("GET", null, null)).html;
    }
    private static String stateOf(String go) { return PhoneSetupServer.decode(URI.create(between(go, "content=\"0;url=", "\"").replace("&amp;", "&")).getRawQuery()).get("state"); }
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
        String go = go(flow, map("address", "http://192.168.1.5:8123", "how", "signin", "allowHttp", "yes"));
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
        go = go(flow, map("address", "http://192.168.1.5:8123", "how", "signin", "allowHttp", "yes"));
        state = stateOf(go);
        PhoneSetupServer.Response done = flow.handle("ha/done", req("GET", null, map("code", "the-code", "state", state)));
        assertTrue(done.success);
        assertEquals("the-code", net.exchangedCode); assertEquals(ORIGIN + "/", net.clientId);
        assertEquals("the TV's own token, checked, then saved", net.minted, net.checkedToken);
        assertEquals(1, saved.times); assertEquals("http://192.168.1.5:8123", saved.base); assertEquals(net.minted, saved.token); assertTrue(saved.consent); assertEquals("signin", saved.how);
        assertEquals("the sign-in's refresh token is retired", "refresh-1", net.revoked);
        assertFalse("no secret on the phone's page", done.html.contains(net.minted) || done.html.contains("short-access"));
        for (String line : tv) assertFalse("no secret on the TV: " + line, line.contains("token-0123") || line.contains("short-access") || line.contains("the-code"));
        assertTrue(tv.contains("Signing in to Home Assistant…"));
        assertTrue("only the forged answer was recorded as a problem", steps.equals(Collections.singletonList(PhoneFlows.STEP_MISMATCH)));
    }

    @Test public void cancelledOrFailedSignInSavesNothing() throws Exception {
        FakeHa net = new FakeHa(); Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, "https://ha.example.invalid");
        String go = go(flow, map("how", "signin"));
        assertTrue("https needs no question", go.contains("/auth/authorize"));
        assertTrue(flow.handle("ha/done", req("GET", null, map("error", "access_denied"))).html.contains("cancelled"));
        assertTrue(tv.contains(PhoneFlows.TV_CANCELLED)); assertTrue(steps.contains(PhoneFlows.STEP_CANCELLED));
        go = go(flow, map("how", "signin"));
        String state = stateOf(go);
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
        assertTrue(flow.handle("ha/start", req("POST", map("address", "https://ha.example.invalid", "how", "signin"), null)).html.contains("Something answered at ha.example.invalid:443, but it isn't Home Assistant"));
        assertEquals(PhoneFlows.TV_NOT_HA, tv.get(tv.size() - 1)); assertEquals(Collections.singletonList(PhoneFlows.STEP_NOT_HA), steps);
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
        // Nothing listens on port 1 of this machine, so the check can only fail to connect (a just-closed test port could
        // already belong to another test's server).
        assertEquals(PhoneFlows.Verdict.OFFLINE, TmdbKey.check("http://127.0.0.1:1", "0123456789abcdef0123456789abcdef"));
    }

    // --- owner's TCL, run 93: "connecting to Home Assistant does not work"
    @Test public void aNameLikeHomeassistantLocalIsOpenedAtItsNumberAddress() throws Exception {
        FakeHa net = new FakeHa(); net.numeric = "http://192.168.1.5:8123"; Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, null);
        String go = go(flow, map("address", "homeassistant.local:8123", "how", "signin", "allowHttp", "yes"));
        URI authorize = URI.create(between(go, "content=\"0;url=", "\"").replace("&amp;", "&"));
        assertEquals("the phone opens the number address: many phones can't look up .local names", "192.168.1.5", authorize.getHost());
        assertTrue("the name stays as words", go.contains("homeassistant.local:8123 (192.168.1.5:8123)"));
        assertTrue(tv.contains("Your phone is opening Home Assistant at 192.168.1.5:8123…"));
        assertTrue(flow.handle("ha/done", req("GET", null, map("code", "c", "state", stateOf(go)))).success);
        assertEquals("the TV signs in and saves the number address too", "http://192.168.1.5:8123", net.exchangedBase);
        assertEquals("http://192.168.1.5:8123", saved.base); assertEquals("http://192.168.1.5:8123", net.checkedBase);
    }
    @Test public void aPhoneThatComesBackWithoutSigningInIsToldWhy() throws Exception {
        FakeHa net = new FakeHa(); Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, "http://192.168.1.5:8123");
        String first = go(flow, map("how", "signin", "allowHttp", "yes"));
        assertTrue("the first visit hands over at once", first.contains("http-equiv=\"refresh\""));
        String again = flow.handle("ha/go", req("GET", null, null)).html;
        assertFalse("no second automatic hand-over", again.contains("http-equiv=\"refresh\""));
        assertTrue(again.contains("Your phone couldn't open Home Assistant at 192.168.1.5:8123, or you came back before signing in."));
        assertTrue(again.contains("home Wi-Fi")); assertTrue("still one tap from trying again", again.contains("/auth/authorize"));
        assertEquals(PhoneFlows.TV_CAME_BACK, tv.get(tv.size() - 1)); assertEquals(Collections.singletonList(PhoneFlows.STEP_PHONE), steps);
        // Trying again still works with the same sign-in.
        assertTrue(flow.handle("ha/done", req("GET", null, map("code", "c", "state", stateOf(first)))).success);
    }
    @Test public void aTvThatCantReachHomeAssistantSaysSoAndNamesTheVpn() throws Exception {
        FakeHa net = new FakeHa(); net.unreachable = true; net.vpn = "NordVPN"; Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, "http://192.168.1.5:8123");
        String page = flow.handle("ha/start", req("POST", map("how", "signin", "allowHttp", "yes"), null)).html;
        assertTrue(page, page.contains("The TV couldn't reach Home Assistant at 192.168.1.5:8123 to check the address. NordVPN on the TV may be blocking your home network"));
        assertTrue(page.contains("Local network discovery"));
        assertEquals("The TV couldn't reach Home Assistant — NordVPN may be blocking your home network. Your phone shows what to try.", tv.get(tv.size() - 1));
        assertEquals(PhoneFlows.STEP_FIND + PhoneFlows.VPN_STEP, steps.get(0));
        net.vpn = null; tv.clear(); steps.clear();
        page = flow.handle("ha/start", req("POST", map("address", "homeassistant.local", "how", "signin", "allowHttp", "yes"), null)).html;
        assertTrue("no VPN: switched on, same network, and the number address for a name", page.contains("try its number address instead"));
        assertEquals(PhoneFlows.TV_UNREACHED, tv.get(tv.size() - 1)); assertEquals(PhoneFlows.STEP_FIND, steps.get(0)); assertEquals(0, saved.times);
    }
    @Test public void eachSignInStepThatFailsSaysWhichOne() throws Exception {
        FakeHa net = new FakeHa(); Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, "http://192.168.1.5:8123");
        // The code is refused (used twice, run out, or for another client).
        net.exchangeError = new PhoneFlows.Refused("no", 400);
        String page = flow.handle("ha/done", req("GET", null, map("code", "c", "state", stateOf(go(flow, map("how", "signin", "allowHttp", "yes")))))).html;
        assertTrue(page.contains("Home Assistant refused the sign-in code.")); assertEquals(PhoneFlows.TV_CODE_REFUSED, tv.get(tv.size() - 1));
        assertEquals(PhoneFlows.STEP_CODE + " (400)", steps.get(steps.size() - 1));
        // The TV can't reach Home Assistant to finish.
        net.exchangeError = new SocketTimeoutException();
        page = flow.handle("ha/done", req("GET", null, map("code", "c", "state", stateOf(go(flow, map("how", "signin", "allowHttp", "yes")))))).html;
        assertTrue(page.contains("The TV couldn't reach Home Assistant at 192.168.1.5:8123 to finish signing in.")); assertEquals(PhoneFlows.STEP_CODE_REACH, steps.get(steps.size() - 1));
        // Home Assistant won't create the TV's key.
        net.exchangeError = null; net.mintError = new PhoneFlows.Refused("no", 0); net.revoked = null;
        page = flow.handle("ha/done", req("GET", null, map("code", "c", "state", stateOf(go(flow, map("how", "signin", "allowHttp", "yes")))))).html;
        assertTrue(page.contains("Couldn't create the TV's key in Home Assistant.")); assertEquals(PhoneFlows.TV_KEY_REFUSED, tv.get(tv.size() - 1));
        assertEquals(PhoneFlows.STEP_KEY, steps.get(steps.size() - 1)); assertEquals("refresh-1", net.revoked);
        // Its live connection can't be opened.
        net.mintError = new ConnectException();
        page = flow.handle("ha/done", req("GET", null, map("code", "c", "state", stateOf(go(flow, map("how", "signin", "allowHttp", "yes")))))).html;
        assertTrue(page.contains("to create the TV's key")); assertEquals(PhoneFlows.STEP_KEY_REACH, steps.get(steps.size() - 1));
        // The new key isn't accepted.
        net.mintError = null; net.checkStatus = 401;
        page = flow.handle("ha/done", req("GET", null, map("code", "c", "state", stateOf(go(flow, map("how", "signin", "allowHttp", "yes")))))).html;
        assertTrue(page.contains("didn't accept the TV's new key")); assertEquals(PhoneFlows.STEP_KEY_CHECK + " (401)", steps.get(steps.size() - 1));
        // The TV can't save it.
        net.checkStatus = 200; saved.broken = true;
        page = flow.handle("ha/done", req("GET", null, map("code", "c", "state", stateOf(go(flow, map("how", "signin", "allowHttp", "yes")))))).html;
        assertTrue(page.contains("The TV couldn't save the connection.")); assertEquals(PhoneFlows.TV_NOT_SAVED, tv.get(tv.size() - 1)); assertEquals(PhoneFlows.STEP_SAVE, steps.get(steps.size() - 1));
        assertEquals(0, saved.times);
        for (String step : steps) { assertFalse("no address in a recorded step: " + step, step.contains("192.168")); assertTrue(step, ("Home Assistant: " + step).length() <= EventLog.WORDS_MAX); }
        for (String line : tv) assertFalse("no secret on the TV: " + line, line.contains("short-access") || line.contains("token-0123"));
    }
    @Test public void aPastedTokenTheTvCantCheckIsNotCalledWrong() throws Exception {
        FakeHa net = new FakeHa(); net.checkStatus = -1; net.vpn = ""; Saved saved = new Saved(); PhoneFlows.Ha flow = ha(net, saved, "http://192.168.1.5:8123");
        String page = flow.handle("ha/start", req("POST", map("how", "token", "token", "eyJhbGciOiJIUzI1NiJ9.pasted.token-value", "allowHttp", "yes"), null)).html;
        assertTrue(page.contains("to check the token. A VPN on the TV may be blocking your home network")); assertFalse(page.contains("didn't accept"));
        assertEquals(PhoneFlows.STEP_TOKEN_REACH + PhoneFlows.VPN_STEP, steps.get(0)); assertEquals(0, saved.times);
    }

    @Test public void aNameBecomesItsHomeNetworkAddressOnlyForPlainHttp() throws Exception {
        java.util.function.Function<String, List<InetAddress>> home = h -> { try { return h.equals("homeassistant.local") ? Arrays.asList(InetAddress.getByName("fd00::5"), InetAddress.getByName("192.168.1.5")) : Collections.emptyList(); } catch (UnknownHostException e) { throw new AssertionError(e); } };
        assertEquals("http://192.168.1.5:8123", HaSignIn.numeric("http://homeassistant.local:8123", home));
        assertEquals("a certificate needs its name", "https://homeassistant.local:8123", HaSignIn.numeric("https://homeassistant.local:8123", home));
        assertEquals("http://192.168.1.9:8123", HaSignIn.numeric("http://192.168.1.9:8123", home));
        assertEquals("not found: unchanged", "http://ha.lan:8123", HaSignIn.numeric("http://ha.lan:8123", home));
        assertEquals("a public address isn't the home network's", "http://ha.example.org:8123", HaSignIn.numeric("http://ha.example.org:8123", h -> { try { return Collections.singletonList(InetAddress.getByName("203.0.113.7")); } catch (UnknownHostException e) { throw new AssertionError(e); } }));
        assertEquals("homeassistant.local:8123 (192.168.1.5:8123)", PhoneFlows.where("http://homeassistant.local:8123", "http://192.168.1.5:8123"));
        assertTrue(PhoneFlows.literal("http://192.168.1.5:8123")); assertTrue(PhoneFlows.literal("http://[fd00::5]:8123")); assertFalse(PhoneFlows.literal("http://homeassistant.local:8123"));
    }
    @Test public void theWebSocketAddressFollowsTheScheme() {
        assertEquals("ws://192.168.1.5:8123/api/websocket", HaSignIn.socketUrl("http://192.168.1.5:8123"));
        assertEquals("wss://ha.example.org:443/api/websocket", HaSignIn.socketUrl("https://ha.example.org:443"));
    }
    @Test public void homeNetworkHostsAreToldByTheirForm() {
        for (String h : new String[]{"192.168.1.5", "10.0.0.2", "172.16.4.4", "homeassistant.local", "[fd00::5]", "fe80::1"}) assertTrue(h, LocalDiscovery.homeHost(h));
        for (String h : new String[]{"127.0.0.1", "8.8.8.8", "ha.example.org", "", null, "[2001:db8::1]"}) assertFalse(String.valueOf(h), LocalDiscovery.homeHost(h));
    }
    @Test public void aRefusedSignInCodeIsHomeAssistantsRefusalNotAnOutage() throws Exception {
        String base = fakeHa(); HaSignIn net = new HaSignIn(null);
        try { net.exchange(base, "bad-code", ORIGIN + "/"); fail(); } catch (PhoneFlows.Refused e) { assertEquals(400, e.status); }
        // Something that isn't Home Assistant answers: false. Nothing answers at all: an error, so the TV can say which.
        MiniHttp other = new MiniHttp(x -> new MiniHttp.Response(404, "not here"));
        try { assertFalse(net.isHomeAssistant(other.base())); } finally { other.close(); }
        try { net.isHomeAssistant("http://127.0.0.1:1"); fail("nothing listens on port 1"); } catch (IOException expected) {}
    }
    @Test public void aKeyNameHomeAssistantAlreadyHasIsAskedForOnceMoreUnderAnother() throws Exception {
        HaSignIn.Mint m = new HaSignIn.Mint("ACCESS", "Unified TV on the TCL · 27 Oct 2026, 14:05", "Unified TV on the TCL · 27 Oct 2026, 14:05 (2)");
        m.on("{\"type\":\"auth_required\"}"); m.on("{\"type\":\"auth_ok\"}");
        // Home Assistant's answer when the name is taken: a ValueError it reports only as "Unknown error".
        JSONObject again = new JSONObject(m.on("{\"id\":1,\"type\":\"result\",\"success\":false,\"error\":{\"code\":\"unknown_error\",\"message\":\"Unknown error\"}}"));
        assertEquals(HaSignIn.Mint.RETRY_ID, again.getInt("id")); assertTrue(again.getString("client_name").endsWith("(2)")); assertFalse(m.finished());
        assertNull(m.on("{\"id\":2,\"type\":\"result\",\"success\":true,\"result\":\"LONG2\"}"));
        assertTrue(m.finished()); assertEquals("LONG2", m.token()); assertTrue(m.answered());
        HaSignIn.Mint twice = new HaSignIn.Mint("ACCESS", "n", "n (2)"); twice.on("{\"type\":\"auth_ok\"}");
        twice.on("{\"id\":1,\"type\":\"result\",\"success\":false}"); twice.on("{\"id\":2,\"type\":\"result\",\"success\":false}");
        assertTrue("asked once more, not forever", twice.finished()); assertNull(twice.token());
    }
    @Test public void theTvsKeyIsCreatedOverARealWebSocket() throws Exception {
        List<String> heard = Collections.synchronizedList(new ArrayList<>());
        try (FakeSocket ha = new FakeSocket(text -> {
                heard.add(text); JSONObject m = new JSONObject(text);
                if ("auth".equals(m.optString("type"))) return "ACCESS".equals(m.optString("access_token")) ? "{\"type\":\"auth_ok\",\"ha_version\":\"2026.10.0\"}" : "{\"type\":\"auth_invalid\"}";
                if ("auth/long_lived_access_token".equals(m.optString("type")))
                    return m.getInt("id") == 1 ? "{\"id\":1,\"type\":\"result\",\"success\":false,\"error\":{\"code\":\"unknown_error\",\"message\":\"Unknown error\"}}"
                        : "{\"id\":" + m.getInt("id") + ",\"type\":\"result\",\"success\":true,\"result\":\"LONG-LIVED\"}";
                return null; })) {
            assertEquals("LONG-LIVED", new HaSignIn(null).mint(ha.base(), "ACCESS"));
            assertEquals("/api/websocket", ha.path);
            assertEquals(3, heard.size()); assertEquals(3650, new JSONObject(heard.get(1)).getInt("lifespan"));
            assertTrue(new JSONObject(heard.get(2)).getString("client_name").endsWith("(2)"));
            try { new HaSignIn(null).mint(ha.base(), "WRONG"); fail(); } catch (PhoneFlows.Refused expected) {}
        }
        try { new HaSignIn(null).mint("http://127.0.0.1:1", "ACCESS"); fail(); }
        catch (PhoneFlows.Refused e) { fail("nothing answered: not a refusal"); } catch (IOException expected) {}
    }

    /**
     * Home Assistant's WebSocket API, just enough (RFC 6455): the upgrade, then "auth_required", then one reply per text
     * frame from the TV ({@code reply}; null sends nothing). Plain sockets: no desktop-only JDK classes.
     */
    static final class FakeSocket implements Closeable {
        interface Reply { String to(String text) throws Exception; }
        private final ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        volatile String path;
        FakeSocket(Reply reply) throws IOException {
            Thread t = new Thread(() -> { while (!server.isClosed()) try (Socket s = server.accept()) { talk(s, reply); } catch (Exception ignored) {} }, "fake-ha-ws");
            t.setDaemon(true); t.start();
        }
        String base() { return "http://127.0.0.1:" + server.getLocalPort(); }
        private void talk(Socket s, Reply reply) throws Exception {
            s.setSoTimeout(5000); InputStream in = new BufferedInputStream(s.getInputStream()); OutputStream out = s.getOutputStream();
            String key = null;
            for (String line = line(in); !line.isEmpty(); line = line(in)) {
                if (line.startsWith("GET ")) path = line.split(" ")[1];
                if (line.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")) key = line.substring(18).trim();
            }
            String accept = Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            send(out, "{\"type\":\"auth_required\",\"ha_version\":\"2026.10.0\"}");
            while (true) {
                int b0 = in.read(), b1 = in.read(); if (b0 < 0 || b1 < 0) return;
                long n = b1 & 0x7f; if (n == 126) n = (in.read() << 8) | in.read(); else if (n == 127) { n = 0; for (int i = 0; i < 8; i++) n = (n << 8) | in.read(); }
                byte[] mask = new byte[4]; if ((b1 & 0x80) != 0) for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
                byte[] data = new byte[(int) n]; for (int i = 0; i < n; i++) data[i] = (byte) (in.read() ^ mask[i % 4]);
                int op = b0 & 0x0f; if (op == 8) return; if (op != 1) continue;
                String answer = reply.to(new String(data, StandardCharsets.UTF_8));
                if (answer != null) send(out, answer);
            }
        }
        private static void send(OutputStream out, String text) throws IOException {
            byte[] d = text.getBytes(StandardCharsets.UTF_8); out.write(0x81);
            if (d.length < 126) out.write(d.length); else { out.write(126); out.write(d.length >> 8); out.write(d.length & 0xff); }
            out.write(d); out.flush();
        }
        private static String line(InputStream in) throws IOException { StringBuilder b = new StringBuilder(); for (int c; (c = in.read()) != -1 && c != '\n';) if (c != '\r') b.append((char) c); return b.toString(); }
        @Override public void close() throws IOException { server.close(); }
    }
}
