package io.unifiedtv.preview;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Phone setup's temporary web page on the TV: a household member scans the TV's QR code (or types a short address and
 * the TV's 6-digit code) and finishes a setup step on their phone instead of typing with the remote (owner, 25 October
 * 2026: "let them do it on their phone"). Pure Java: Android only supplies the address, the network test and the flow.
 *
 * <h3>Threat notes (read before changing anything here)</h3>
 * <ul>
 * <li><b>Plain HTTP on the home network.</b> The page is served without TLS (a TV has no certificate a phone would trust),
 *     so whatever the phone submits (a Home Assistant token, an API key) crosses the home network unencrypted and could be
 *     read by another device on the same network that can sniff traffic (for example open or shared Wi-Fi). The mitigations
 *     below limit when and to whom the page answers; they do not encrypt. Home Assistant's own sign-in page is served by
 *     Home Assistant itself, so this page never sees the household's password.</li>
 * <li><b>Bound to the TV's home-network address only</b>, never 0.0.0.0, so nothing answers on a VPN tunnel or any other
 *     interface, on a random high port (49152–65535).</li>
 * <li><b>Home network only:</b> a connection from an address outside the TV's own subnet is closed without a reply
 *     ({@code local}). The Host header must name this TV's own address (any case, the port optional but never another one,
 *     a trailing dot allowed) and never a host name, which defeats DNS rebinding from a web page; a POST carrying a
 *     foreign Origin is refused. A TLS hello (a browser trying https:// first) is closed at once.</li>
 * <li><b>One-time secret in the path:</b> 128 random bits (base64url) from SecureRandom, shown only in the TV's QR code.
 *     The short typed address carries no secret: it asks for the TV's 6-digit code, and five wrong codes stop the
 *     session. The path token is a session capability, not a credential for any service, and it dies with the session.</li>
 * <li><b>Short-lived and single use:</b> ten minutes, or the first successful save, whichever comes first; then the
 *     socket is closed. The TV screen also stops it when it closes.</li>
 * <li><b>Rate limits:</b> at most {@value #MAX_REQUESTS} requests, {@value #MAX_BAD} requests for unknown paths or
 *     tokens, {@value #MAX_CODE_TRIES} code attempts and {@value #MAX_SUBMITS} form submissions per session; past any of
 *     them the session ends. Requests are read one at a time with a short timeout and size limits (header 8 KB, body
 *     16 KB), so a slow or oversized client can't hold the TV.</li>
 * <li><b>No third-party resources and no script:</b> inline CSS only; Content-Security-Policy {@code default-src 'none'},
 *     no-store caching, no framing, and {@code Referrer-Policy: same-origin} (and {@code rel="noreferrer"} on every outside link) so the session
 *     path never leaks to the sign-up sites the page links to, while the phone still names this TV as a form's Origin.</li>
 * <li><b>Files:</b> the settings backup (SET-081) is the only download, sent as an attachment under the same session path
 *     and headers; it never contains a secret (SettingsBackup). The only upload is that file back, as
 *     multipart/form-data within the same 16 KB body limit; the TV checks it and an adult confirms on the TV.</li>
 * <li><b>Never logged:</b> this class writes no logs at all, and no request body, key, token or code is ever put in a
 *     log, a notification or on the TV screen.</li>
 * </ul>
 * Residual risks: a device on the same network that can sniff plain HTTP during the minutes a phone is submitting, and
 * anyone who can see the TV screen while the code is shown (they are in the room). HTTPS on the TV is not offered.
 */
final class PhoneSetupServer {
    static final long LIFETIME_MS = 10 * 60_000L;
    static final int MAX_REQUESTS = 150, MAX_BAD = 20, MAX_CODE_TRIES = 5, MAX_SUBMITS = 12;
    static final int HEAD_LIMIT = 8192, BODY_LIMIT = 16384, READ_TIMEOUT_MS = 5000, PORT_MIN = 49152, PORT_MAX = 65535;
    private static final String CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

    /** Why a session ended, for the TV's words. */
    enum End { SUCCEEDED, EXPIRED, LOCKED, STOPPED }

    /** One parsed request: method, path, query and form fields; never logged. */
    static final class Request {
        final String method, path; final Map<String, String> headers, query, form; final InetAddress from;
        /** "http://address:port" and that plus "/token": where the phone's pages live. */
        String origin, session;
        Request(String method, String path, Map<String, String> headers, Map<String, String> query, Map<String, String> form, InetAddress from) {
            this.method = method; this.path = path; this.headers = headers; this.query = query; this.form = form; this.from = from;
        }
        /** A form field, else a query parameter, else "". */
        String param(String name) { String v = form.get(name); if (v == null) v = query.get(name); return v == null ? "" : v; }
        boolean post() { return "POST".equals(method); }
    }

    static final class Response {
        final int status; final String html, location; final boolean success;
        /** A download instead of a page (settings backup, SET-081): its media type and the file name the phone saves. */
        String type = "text/html; charset=utf-8", filename;
        private Response(int status, String html, String location, boolean success) { this.status = status; this.html = html; this.location = location; this.success = success; }
        static Response page(String html) { return new Response(200, html, null, false); }
        static Response status(int status, String html) { return new Response(status, html, null, false); }
        static Response redirect(String location) { return new Response(303, "", location, false); }
        /** The step is finished: this response is sent, then the session ends. */
        static Response done(String html) { return new Response(200, html, null, true); }
        /** A file for the phone to save; the session carries on. The name is plain letters, digits, dots and dashes. */
        static Response file(String body, String type, String filename) {
            if (filename == null || !filename.matches("[A-Za-z0-9._-]{1,64}") || type == null || !type.matches("[a-z]+/[a-z0-9.+-]+(; charset=utf-8)?")) throw new IllegalArgumentException("file");
            Response r = new Response(200, body, null, false); r.type = type; r.filename = filename; return r;
        }
    }

    /** What the phone sees under the session path; {@code action} is the rest of the path ("" for the first page). */
    interface Flow { Response handle(String action, Request request) throws Exception; }
    interface Events { void opened(); void ended(End why); }

    private final InetAddress bind;
    private final Predicate<InetAddress> local;
    private final Flow flow;
    private final Events events;
    private final LongSupplier clock;
    private final SecureRandom random;
    private ServerSocket server;
    private Thread thread;
    private volatile boolean stopping;
    private volatile End ended;
    private String token, code, origin, hostPort;
    private long expires;
    private int requests, bad, codeTries, submits;
    private boolean opened;

    PhoneSetupServer(InetAddress bind, Predicate<InetAddress> local, Flow flow, Events events, LongSupplier clock, SecureRandom random) {
        this.bind = bind; this.local = local; this.flow = flow; this.events = events; this.clock = clock; this.random = random;
    }

    /** Binds a random high port on the given address and starts answering. */
    synchronized void start() throws IOException {
        if (server != null) throw new IllegalStateException("started");
        IOException last = null;
        for (int i = 0; i < 30 && server == null; i++) {
            int port = PORT_MIN + random.nextInt(PORT_MAX - PORT_MIN + 1);
            ServerSocket s = new ServerSocket();
            try { s.setReuseAddress(false); s.bind(new InetSocketAddress(bind, port), 8); s.setSoTimeout(500); server = s; }
            catch (IOException e) { last = e; s.close(); }
        }
        if (server == null) throw last != null ? last : new IOException("no port");
        byte[] secret = new byte[16]; random.nextBytes(secret);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String host = bind instanceof Inet6Address ? "[" + bind.getHostAddress().replaceAll("%.*$", "") + "]" : bind.getHostAddress();
        hostPort = host + ":" + server.getLocalPort(); origin = "http://" + hostPort;
        expires = clock.getAsLong() + LIFETIME_MS;
        thread = new Thread(this::loop, "phone-setup"); thread.setDaemon(true); thread.start();
    }

    /** Short address for typing ("http://" + the TV's home-network address + ":" + port); asks for the code. */
    String origin() { return origin; }
    /** The full address in the QR code, with the one-time secret. */
    String link() { return origin + "/" + token + "/"; }
    String code() { return code; }
    int port() { return server == null ? -1 : server.getLocalPort(); }
    boolean running() { return server != null && ended == null; }
    End ended() { return ended; }
    long expiresAt() { return expires; }

    /** Ends the session now (the TV screen closed); idempotent. */
    void stop() { finish(End.STOPPED); }

    private void finish(End why) {
        synchronized (this) { if (ended != null) return; ended = why; stopping = true; }
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        if (events != null) events.ended(why);
    }

    /** Joins the worker (tests). */
    void await(long ms) throws InterruptedException { Thread t = thread; if (t != null) t.join(ms); }

    private void loop() {
        while (!stopping) {
            if (clock.getAsLong() >= expires) { finish(End.EXPIRED); break; }
            Socket s;
            try { s = server.accept(); } catch (SocketTimeoutException e) { continue; } catch (IOException e) { break; }
            boolean success = false;
            try (Socket socket = s) {
                // Not from the home network: closed without a word.
                if (!local.test(socket.getInetAddress())) continue;
                socket.setSoTimeout(READ_TIMEOUT_MS);
                success = serve(socket);
            } catch (Exception ignored) {
                // Nothing about a failed request is recorded anywhere.
            }
            if (success) { finish(End.SUCCEEDED); break; }
        }
        if (ended == null) finish(End.STOPPED);
    }

    /** One request on one connection; true when the flow finished successfully. */
    private boolean serve(Socket socket) throws IOException {
        OutputStream out = socket.getOutputStream();
        if (++requests > MAX_REQUESTS) { write(out, Response.status(429, PhonePages.message("Too many requests", "Start phone setup again on the TV."))); finish(End.LOCKED); return false; }
        Request r;
        try { r = read(socket.getInputStream(), socket.getInetAddress()); }
        catch (IllegalArgumentException e) { write(out, Response.status(400, PhonePages.message("Something went wrong", "Reload the page and try again."))); return false; }
        if (r == null) return false;
        if (!host(r.headers.getOrDefault("host", ""), bind, server.getLocalPort())) { write(out, Response.status(400, PhonePages.message("Wrong address", "Scan the code on the TV again."))); return false; }
        if (!"GET".equals(r.method) && !"POST".equals(r.method)) { write(out, Response.status(405, PhonePages.message("Not allowed", ""))); return false; }
        String origin = r.headers.get("origin");
        if (r.post() && !originAllowed(origin, r.path)) { write(out, Response.status(403, PhonePages.message("Not allowed", "Scan the code on the TV again and try once more."))); return false; }
        r.origin = this.origin; r.session = this.origin + "/" + token;
        if (r.path.equals("/favicon.ico")) { write(out, Response.status(404, "")); return false; }
        // The short typed address: the code shown on the TV, then on to the session's own pages.
        if (r.path.equals("/")) {
            if (!r.post()) { write(out, Response.page(PhonePages.code(null))); return false; }
            if (++submits > MAX_SUBMITS) { write(out, Response.status(429, PhonePages.message("Too many tries", "Start phone setup again on the TV."))); finish(End.LOCKED); return false; }
            if (same(r.param("code").replaceAll("\\s", ""), code)) { write(out, Response.redirect("/" + token + "/")); return false; }
            if (++codeTries >= MAX_CODE_TRIES) { write(out, Response.status(403, PhonePages.message("Too many tries", "That code was stopped. Start phone setup again on the TV for a new one."))); finish(End.LOCKED); return false; }
            write(out, Response.status(403, PhonePages.code("That code didn't match. Check the 6 digits on the TV and try again."))); return false;
        }
        String prefix = "/" + token;
        if (!(r.path.equals(prefix) || r.path.startsWith(prefix + "/")) || !same(r.path.substring(1, 1 + token.length()), token)) {
            write(out, Response.status(404, PhonePages.message("Page not found", "Scan the code on the TV again.")));
            if (++bad > MAX_BAD) finish(End.LOCKED);
            return false;
        }
        String action = r.path.length() <= prefix.length() + 1 ? "" : r.path.substring(prefix.length() + 1);
        if (!action.matches("[a-z/]{0,32}") || action.endsWith("/")) { write(out, Response.status(404, PhonePages.message("Page not found", ""))); if (++bad > MAX_BAD) finish(End.LOCKED); return false; }
        if (r.post() && ++submits > MAX_SUBMITS) { write(out, Response.status(429, PhonePages.message("Too many tries", "Start phone setup again on the TV."))); finish(End.LOCKED); return false; }
        if (!opened) { opened = true; if (events != null) try { events.opened(); } catch (RuntimeException ignored) {} }
        Response response;
        try { response = flow.handle(action, r); }
        catch (Exception e) { response = Response.status(500, PhonePages.message("Something went wrong on the TV", "Try again. If it keeps happening, start again on the TV.")); }
        if (response == null) response = Response.status(404, PhonePages.message("Page not found", ""));
        write(out, response);
        return response.success;
    }

    /**
     * A form post's Origin (owner's TCL, 28 October 2026: every save from the phone said "Not allowed"). Browsers send
     * {@code Origin: null} for a same-page form post when the page's referrer policy is {@code no-referrer}, which these
     * pages used to send; pages now say {@code same-origin}, so the real origin comes back. Accepted: no Origin, this
     * TV's own origin (Host rules), and {@code null} only on a path inside the session (the one-time secret in the path
     * already proves the page came from this TV). Refused: any other site.
     */
    private boolean originAllowed(String origin, String path) {
        if (origin == null || origin.equals(this.origin)) return true;
        if (origin.trim().equalsIgnoreCase("null")) return token != null && path.length() > token.length() && same(path.substring(1, 1 + token.length()), token);
        return origin.regionMatches(true, 0, "http://", 0, 7) && host(origin.substring(7), bind, server.getLocalPort());
    }

    /**
     * The Host header names this TV (owner's TCL, 25 October 2026: a phone was told "Wrong address"). Accepted: the TV's
     * own address in any case, with or without the port, with a trailing dot, an IPv6 address in brackets with or without
     * its zone. Refused: any other port, and every host NAME, even one that resolves here (DNS rebinding); no name is
     * ever looked up.
     */
    static boolean host(String header, InetAddress tv, int port) {
        if (header == null || tv == null) return false;
        String h = header.trim().toLowerCase(Locale.ROOT), name, p = null;
        if (h.isEmpty() || h.length() > 80) return false;
        if (h.startsWith("[")) {
            int close = h.indexOf(']'); if (close < 0) return false;
            name = h.substring(1, close); String rest = h.substring(close + 1);
            if (!rest.isEmpty()) { if (!rest.startsWith(":")) return false; p = rest.substring(1); }
        } else {
            int colon = h.indexOf(':');
            if (colon >= 0 && h.indexOf(':', colon + 1) >= 0) return false;   // a bare IPv6 address is never a valid Host
            name = colon < 0 ? h : h.substring(0, colon); p = colon < 0 ? null : h.substring(colon + 1);
        }
        if (p != null && !p.equals(String.valueOf(port))) return false;
        if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        name = name.replaceAll("%.*$", "");
        boolean literal = tv instanceof Inet4Address ? name.matches("\\d{1,3}(\\.\\d{1,3}){3}") : name.matches("[0-9a-f:.]{2,45}") && name.contains(":");
        if (!literal) return false;
        try { return InetAddress.getByName(name).equals(tv instanceof Inet6Address ? stripScope((Inet6Address) tv) : tv); } catch (Exception e) { return false; }
    }
    private static InetAddress stripScope(Inet6Address a) { try { return InetAddress.getByAddress(a.getAddress()); } catch (UnknownHostException e) { return a; } }

    private static boolean same(String a, String b) { return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }

    // --- HTTP/1.1, just enough: one request per connection, Content-Length bodies only.
    /** Parses one request; null when the client sent nothing. Throws IllegalArgumentException for anything malformed. */
    static Request read(InputStream raw, InetAddress from) throws IOException {
        InputStream in = new BufferedInputStream(raw);
        // A browser trying https:// first (an HTTPS-only mode, an in-camera viewer) sends a TLS hello to this plain page:
        // closed at once, so the phone falls back to http:// without waiting out the read timeout.
        in.mark(1); int firstByte = in.read(); if (firstByte < 0) return null; if (firstByte == 0x16) return null; in.reset();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int last = 0, b;   // the last four bytes read, to find the blank line that ends the head
        while (last != 0x0d0a0d0a) {
            b = in.read();
            if (b < 0) { if (head.size() == 0) return null; throw new IllegalArgumentException("truncated"); }
            if (head.size() >= HEAD_LIMIT) throw new IllegalArgumentException("head too large");
            head.write(b); last = (last << 8) | (b & 0xff);
        }
        String[] lines = head.toString("ISO-8859-1").split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length != 3 || !first[2].startsWith("HTTP/1.")) throw new IllegalArgumentException("request line");
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            int colon = lines[i].indexOf(':'); if (colon <= 0) throw new IllegalArgumentException("header");
            headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        String target = first[1];
        // Absolute form ("GET http://address:port/path"), as some phone proxies send it: its authority is the host.
        if (target.regionMatches(true, 0, "http://", 0, 7)) {
            int slash = target.indexOf('/', 7); String authority = slash < 0 ? target.substring(7) : target.substring(7, slash);
            headers.put("host", authority); target = slash < 0 ? "/" : target.substring(slash);
        }
        if (!target.startsWith("/")) throw new IllegalArgumentException("target");
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q), query = q < 0 ? "" : target.substring(q + 1);
        Map<String, String> form = new HashMap<>();
        if (headers.containsKey("transfer-encoding")) throw new IllegalArgumentException("chunked");
        String length = headers.get("content-length");
        if (length != null) {
            int n; try { n = Integer.parseInt(length); } catch (NumberFormatException e) { throw new IllegalArgumentException("length"); }
            if (n < 0 || n > BODY_LIMIT) throw new IllegalArgumentException("body too large");
            byte[] body = new byte[n]; int got = 0;
            while (got < n) { int k = in.read(body, got, n - got); if (k < 0) throw new IllegalArgumentException("truncated body"); got += k; }
            String type = headers.getOrDefault("content-type", "");
            if (n > 0 && type.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) form = multipart(body, boundary(type));
            else if (n > 0 && !type.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) throw new IllegalArgumentException("content type");
            else form = decode(new String(body, StandardCharsets.UTF_8));
        }
        return new Request(first[0], path, headers, decode(query), form, from);
    }

    /** application/x-www-form-urlencoded, at most 20 fields; the first value of a repeated name wins. */
    static Map<String, String> decode(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) return out;
        String[] pairs = s.split("&");
        if (pairs.length > 20) throw new IllegalArgumentException("too many fields");
        for (String p : pairs) {
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            try {
                String k = URLDecoder.decode(eq < 0 ? p : p.substring(0, eq), "UTF-8"), v = eq < 0 ? "" : URLDecoder.decode(p.substring(eq + 1), "UTF-8");
                out.putIfAbsent(k, v);
            } catch (UnsupportedEncodingException | IllegalArgumentException e) { throw new IllegalArgumentException("encoding"); }
        }
        return out;
    }

    /** The boundary of a multipart/form-data content type (RFC 7578): 1 to 70 safe characters, quoted or not. */
    static String boundary(String contentType) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i);\\s*boundary=(?:\"([^\"]{1,70})\"|([0-9A-Za-z'()+_,./:=?-]{1,70}))").matcher(contentType == null ? "" : contentType);
        if (!m.find()) throw new IllegalArgumentException("boundary");
        return m.group(1) != null ? m.group(1) : m.group(2);
    }
    /**
     * multipart/form-data, just enough for one small file and a few fields (the settings backup upload): at most 20 parts,
     * each named with plain letters; a part's text is read as UTF-8; the first part of a repeated name wins. Anything
     * malformed throws IllegalArgumentException. The whole body is already within BODY_LIMIT.
     */
    static Map<String, String> multipart(byte[] body, String boundary) {
        String all = new String(body, StandardCharsets.ISO_8859_1), delimiter = "--" + boundary;
        Map<String, String> out = new LinkedHashMap<>();
        int at = all.indexOf(delimiter);
        if (at < 0) throw new IllegalArgumentException("multipart");
        int parts = 0;
        while (true) {
            at += delimiter.length();
            if (all.startsWith("--", at)) return out;                       // the closing delimiter
            if (!all.startsWith("\r\n", at)) throw new IllegalArgumentException("multipart");
            int head = all.indexOf("\r\n\r\n", at + 2), next = all.indexOf("\r\n" + delimiter, at + 2);
            if (head < 0 || next < 0 || head > next) throw new IllegalArgumentException("multipart");
            if (++parts > 20) throw new IllegalArgumentException("too many parts");
            String name = null;
            for (String line : all.substring(at + 2, head).split("\r\n")) {
                if (!line.toLowerCase(Locale.ROOT).startsWith("content-disposition:")) continue;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i);\\s*name=\"([a-z]{1,20})\"").matcher(line);
                if (m.find()) name = m.group(1);
            }
            if (name == null) throw new IllegalArgumentException("multipart name");
            out.putIfAbsent(name, new String(all.substring(head + 4, next).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
            at = next + 2;
        }
    }

    static void write(OutputStream out, Response r) throws IOException {
        byte[] body = (r.html == null ? "" : r.html).getBytes(StandardCharsets.UTF_8);
        StringBuilder h = new StringBuilder("HTTP/1.1 ").append(r.status).append(' ').append(reason(r.status)).append("\r\n");
        if (r.location != null) h.append("Location: ").append(r.location).append("\r\n");
        if (r.filename != null) h.append("Content-Disposition: attachment; filename=\"").append(r.filename).append("\"\r\n");
        h.append("Content-Type: ").append(r.type).append("\r\nContent-Length: ").append(body.length).append("\r\n")
         .append("Cache-Control: no-store\r\nPragma: no-cache\r\nReferrer-Policy: same-origin\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\n")
         .append("Content-Security-Policy: ").append(CSP).append("\r\nConnection: close\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1)); out.write(body); out.flush();
    }
    private static String reason(int status) {
        switch (status) { case 200: return "OK"; case 303: return "See Other"; case 400: return "Bad Request"; case 403: return "Forbidden"; case 404: return "Not Found";
            case 405: return "Method Not Allowed"; case 429: return "Too Many Requests"; default: return "Internal Server Error"; }
    }

    // --- home network test (pure)
    /** True when {@code remote} is an IPv4 address in the TV's own subnet (prefix clamped to /16–/30). */
    static boolean sameNetwork(InetAddress tv, int prefix, InetAddress remote) {
        if (!(tv instanceof Inet4Address) || !(remote instanceof Inet4Address) || !tv.isSiteLocalAddress()) return false;
        int p = prefix < 16 || prefix > 30 ? 24 : prefix;
        int a = toInt(tv.getAddress()), b = toInt(remote.getAddress()), mask = p == 0 ? 0 : -1 << (32 - p);
        return (a & mask) == (b & mask) && a != b;
    }
    private static int toInt(byte[] x) { return ((x[0] & 0xff) << 24) | ((x[1] & 0xff) << 16) | ((x[2] & 0xff) << 8) | (x[3] & 0xff); }
}
