package io.unifiedtv.preview;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Phone setup's temporary page: one-time path token, the typed code, expiry, single use, home network only, parsing. */
public class PhoneSetupServerTest {
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final AtomicReference<PhoneSetupServer.End> ended = new AtomicReference<>();
    private int opened;
    private PhoneSetupServer server;
    private InetAddress loopback;

    /** A flow whose "save" succeeds when the field says yes. */
    private final PhoneSetupServer.Flow flow = (action, r) -> {
        if (action.isEmpty()) return PhoneSetupServer.Response.page("<p>first page</p>");
        if (action.equals("save") && r.post()) return "yes".equals(r.param("ok")) ? PhoneSetupServer.Response.done("<p>saved</p>") : PhoneSetupServer.Response.page("<p>try again</p>");
        return null;
    };

    private PhoneSetupServer start(boolean local) throws IOException {
        loopback = InetAddress.getByName("127.0.0.1");
        server = new PhoneSetupServer(loopback, a -> local, flow, new PhoneSetupServer.Events() {
            @Override public void opened() { opened++; }
            @Override public void ended(PhoneSetupServer.End why) { ended.set(why); }
        }, clock::get, new SecureRandom());
        server.start();
        return server;
    }
    @After public void stop() { if (server != null) server.stop(); }

    static final class Reply { int status; Map<String, String> headers = new HashMap<>(); String body = ""; }
    private Reply send(String method, String path, String host, String body, String... extra) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(loopback, server.port()), 2000); s.setSoTimeout(3000);
            StringBuilder req = new StringBuilder(method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\n");
            for (String h : extra) req.append(h).append("\r\n");
            byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            if (body != null) req.append("Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ").append(b.length).append("\r\n");
            req.append("\r\n");
            OutputStream out = s.getOutputStream(); out.write(req.toString().getBytes(StandardCharsets.ISO_8859_1)); out.write(b); out.flush();
            ByteArrayOutputStream all = new ByteArrayOutputStream(); InputStream in = s.getInputStream(); byte[] buf = new byte[4096]; int n;
            try { while ((n = in.read(buf)) != -1) all.write(buf, 0, n); } catch (SocketException | SocketTimeoutException ignored) {}
            Reply r = new Reply(); String text = all.toString("UTF-8"); if (text.isEmpty()) { r.status = -1; return r; }
            int split = text.indexOf("\r\n\r\n"); String[] lines = text.substring(0, split).split("\r\n");
            r.status = Integer.parseInt(lines[0].split(" ")[1]);
            for (int i = 1; i < lines.length; i++) { int c = lines[i].indexOf(':'); r.headers.put(lines[i].substring(0, c).toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim()); }
            r.body = text.substring(split + 4); return r;
        }
    }
    private String host() { return "127.0.0.1:" + server.port(); }
    private String tokenPath() { return URI.create(server.link()).getPath(); }

    @Test public void bindsAHighRandomPortWithA128BitTokenAndSixDigits() throws Exception {
        start(true);
        assertTrue(server.port() >= 49152 && server.port() <= 65535);
        String token = tokenPath().replace("/", "");
        assertTrue("22 base64url characters: " + token, token.matches("[A-Za-z0-9_-]{22}"));
        assertTrue(server.code().matches("[0-9]{6}"));
        assertEquals("http://" + host(), server.origin());
        assertFalse("the typed address carries no secret", server.origin().contains(token));
    }

    @Test public void theTokenPageAnswersWithStrictHeadersAndOtherPathsDoNot() throws Exception {
        start(true);
        Reply ok = send("GET", tokenPath(), host(), null);
        assertEquals(200, ok.status); assertTrue(ok.body.contains("first page")); assertEquals(1, opened);
        assertTrue(ok.headers.get("content-security-policy").startsWith("default-src 'none'"));
        assertEquals("same-origin", ok.headers.get("referrer-policy")); assertEquals("no-store", ok.headers.get("cache-control"));
        assertEquals("DENY", ok.headers.get("x-frame-options")); assertEquals("close", ok.headers.get("connection"));
        assertEquals(404, send("GET", "/AAAAAAAAAAAAAAAAAAAAAA/", host(), null).status);
        assertEquals("a token prefix is not the token", 404, send("GET", tokenPath().substring(0, 10), host(), null).status);
        assertEquals(400, send("GET", tokenPath(), "evil.example:80", null).status);
        assertEquals("a foreign Origin can't post", 403, send("POST", tokenPath() + "save", host(), "ok=yes", "Origin: http://evil.example").status);
        assertEquals("Origin: null outside the session can't post", 403, send("POST", "/AAAAAAAAAAAAAAAAAAAAAA/save", host(), "ok=yes", "Origin: null").status);
        assertEquals(405, send("PUT", tokenPath(), host(), null).status);
        assertNull("nothing has ended", ended.get());
    }

    /** Owner's TCL, 28 Oct 2026: phones sent "Origin: null" (the old no-referrer policy) and every save said "Not allowed". */
    @Test public void aPhonesNullOriginInsideTheSessionMayPost() throws Exception {
        start(true);
        assertNotEquals(403, send("POST", tokenPath() + "save", host(), "ok=yes", "Origin: null").status);
    }

    @Test public void thisTvsOwnOriginMayPost() throws Exception {
        start(true);
        assertNotEquals(403, send("POST", tokenPath() + "save", host(), "ok=yes", "Origin: http://" + host()).status);
    }

    @Test public void theShortAddressNeedsTheCodeAndFiveWrongCodesStopIt() throws Exception {
        start(true);
        Reply page = send("GET", "/", host(), null);
        assertEquals(200, page.status); assertTrue(page.body.contains("6 digits")); assertFalse(page.body.contains(tokenPath().replace("/", "")));
        String wrong = server.code().equals("000000") ? "111111" : "000000";
        Reply right = send("POST", "/", host(), "code=" + server.code());
        assertEquals(303, right.status); assertEquals(tokenPath(), right.headers.get("location"));
        for (int i = 0; i < 4; i++) assertEquals(403, send("POST", "/", host(), "code=" + wrong).status);
        assertNull(ended.get());
        send("POST", "/", host(), "code=" + wrong);
        server.await(3000);
        assertEquals(PhoneSetupServer.End.LOCKED, ended.get()); assertFalse(server.running());
    }

    @Test public void theFirstSuccessfulSaveEndsTheSession() throws Exception {
        start(true);
        assertTrue(send("POST", tokenPath() + "save", host(), "ok=no").body.contains("try again"));
        assertNull(ended.get());
        Reply done = send("POST", tokenPath() + "save", host(), "ok=yes");
        assertEquals(200, done.status); assertTrue(done.body.contains("saved"));
        server.await(3000);
        assertEquals(PhoneSetupServer.End.SUCCEEDED, ended.get());
        try (Socket s = new Socket()) { s.connect(new InetSocketAddress(loopback, server.port()), 1000); fail("the page stops answering after a save"); }
        catch (IOException expected) {}
    }

    @Test public void itRunsOutAfterTenMinutes() throws Exception {
        start(true);
        assertEquals(200, send("GET", tokenPath(), host(), null).status);
        clock.addAndGet(PhoneSetupServer.LIFETIME_MS);
        server.await(3000);
        assertEquals(PhoneSetupServer.End.EXPIRED, ended.get());
    }

    @Test public void stoppingIsImmediateAndIdempotent() throws Exception {
        start(true); server.stop(); server.stop();
        assertEquals(PhoneSetupServer.End.STOPPED, ended.get()); assertFalse(server.running());
    }

    @Test public void addressesOutsideTheHomeNetworkGetNoAnswer() throws Exception {
        start(false);
        assertEquals("closed without a reply", -1, send("GET", tokenPath(), host(), null).status);
        assertEquals(0, opened);
    }

    @Test public void tooManyUnknownPathsEndTheSession() throws Exception {
        start(true);
        for (int i = 0; i <= PhoneSetupServer.MAX_BAD; i++) send("GET", "/guess" + i, host(), null);
        server.await(3000);
        assertEquals(PhoneSetupServer.End.LOCKED, ended.get());
    }

    @Test public void requestParsing() throws Exception {
        String form = "key=a%2Bb+c&empty=&k2=%C3%A9";
        String raw = "POST /abc/save?x=1&y=two%20words HTTP/1.1\r\nHost: h:1\r\nContent-Type: application/x-www-form-urlencoded; charset=UTF-8\r\nContent-Length: " + form.length() + "\r\n\r\n" + form;
        PhoneSetupServer.Request r = PhoneSetupServer.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)), null);
        assertEquals("POST", r.method); assertEquals("/abc/save", r.path); assertEquals("two words", r.query.get("y"));
        assertEquals("a+b c", r.param("key")); assertEquals("", r.param("empty")); assertEquals("1", r.param("x")); assertEquals("h:1", r.headers.get("host")); assertEquals("\u00e9", r.param("k2"));
        assertNull("nothing sent", PhoneSetupServer.read(new ByteArrayInputStream(new byte[0]), null));
        for (String bad : new String[]{"GET\r\n\r\n", "GET /x HTTP/1.1\r\nBroken\r\n\r\n", "GET x HTTP/1.1\r\n\r\n",
            "POST / HTTP/1.1\r\nContent-Length: 99999\r\n\r\n", "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n",
            "POST / HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}", "POST / HTTP/1.1\r\nContent-Length: 5\r\n\r\nab"})
            try { PhoneSetupServer.read(new ByteArrayInputStream(bad.getBytes(StandardCharsets.ISO_8859_1)), null); fail("refused: " + bad.replace("\r\n", "|")); } catch (IllegalArgumentException expected) {}
        StringBuilder big = new StringBuilder("GET / HTTP/1.1\r\nX: "); while (big.length() < PhoneSetupServer.HEAD_LIMIT + 10) big.append('a');
        try { PhoneSetupServer.read(new ByteArrayInputStream(big.append("\r\n\r\n").toString().getBytes(StandardCharsets.ISO_8859_1)), null); fail("header limit"); } catch (IllegalArgumentException expected) {}
        StringBuilder many = new StringBuilder(); for (int i = 0; i < 25; i++) many.append("f").append(i).append("=1&");
        try { PhoneSetupServer.decode(many.toString()); fail("field limit"); } catch (IllegalArgumentException expected) {}
        assertEquals("first value wins", "1", PhoneSetupServer.decode("a=1&a=2").get("a"));
    }

    @Test public void homeNetworkTest() throws Exception {
        InetAddress tv = InetAddress.getByName("192.168.1.20");
        assertTrue(PhoneSetupServer.sameNetwork(tv, 24, InetAddress.getByName("192.168.1.77")));
        assertFalse(PhoneSetupServer.sameNetwork(tv, 24, InetAddress.getByName("192.168.2.77")));
        assertTrue("a /16 home network", PhoneSetupServer.sameNetwork(tv, 16, InetAddress.getByName("192.168.2.77")));
        assertFalse("the TV itself", PhoneSetupServer.sameNetwork(tv, 24, tv));
        assertFalse("public addresses", PhoneSetupServer.sameNetwork(tv, 24, InetAddress.getByName("8.8.8.8")));
        assertFalse("a TV without a private address", PhoneSetupServer.sameNetwork(InetAddress.getByName("8.8.8.20"), 24, InetAddress.getByName("8.8.8.21")));
        assertFalse("IPv6", PhoneSetupServer.sameNetwork(tv, 24, InetAddress.getByName("fe80::1")));
        assertTrue("an implausible prefix falls back to /24", PhoneSetupServer.sameNetwork(tv, 2, InetAddress.getByName("192.168.1.9")));
        assertFalse(PhoneSetupServer.sameNetwork(tv, 2, InetAddress.getByName("192.168.9.9")));
    }

    @Test public void pagesEscapeEverythingAndUseNoOutsideResources() {
        String page = PhonePages.page("<b>", PhonePages.error("<script>x</script>") + PhonePages.link("https://example.org/?a=1&b=\"2\"", "go", true));
        assertFalse(page.contains("<script>")); assertTrue(page.contains("&lt;script&gt;")); assertTrue(page.contains("&amp;b=&quot;2&quot;"));
        assertTrue(page.contains("rel=\"noopener noreferrer\""));
        assertFalse("no external stylesheet, script, font or image", page.matches("(?s).*(<script|<link|<img|@import|url\\().*"));
    }
}
