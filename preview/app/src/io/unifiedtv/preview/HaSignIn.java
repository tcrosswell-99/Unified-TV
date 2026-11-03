package io.unifiedtv.preview;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Phone setup's Home Assistant network calls, made by the TV on the home network: the manifest check, the OAuth code
 * exchange, the TV's own long-lived token over Home Assistant's WebSocket API, and a token check. Exact origin only
 * (Endpoint.resolve), no redirects, short timeouts, bounded replies, and nothing is logged. Requests go straight over
 * Wi-Fi/Ethernet under a VPN where Android allows it, with one retry over the default route when the VPN refuses that
 * (LocalDiscovery.request), as the media server's do. A null Context (tests) uses the default route.
 *
 * <p>Checked against Home Assistant's own code (core, homeassistant/components/auth, October 2026): client_id is an
 * http(s) URL on a private address, and the redirect_uri on the same scheme and host:port is accepted without Home
 * Assistant fetching anything; the code is looked up by the exact (client_id, code) pair, works once and for ten minutes;
 * a long-lived token's name must be new for that user, or the WebSocket answers "Unknown error".
 */
final class HaSignIn implements PhoneFlows.HaNet {
    static final int LIMIT = 64 * 1024, CONNECT_MS = 5000, READ_MS = 8000;
    private final Context context;
    HaSignIn(Context context) { this.context = context; }

    /** Home Assistant's WebSocket API on the same origin: ws:// for http, wss:// for https, at /api/websocket. */
    static String socketUrl(String base) { return Endpoint.resolve(base, "/api/websocket").toString().replaceFirst("^http", "ws"); }

    /** True when Home Assistant's public manifest answers; false when something else answers; IOException when nothing does. */
    @Override public boolean isHomeAssistant(String base) throws Exception {
        Reply r = request(base, "/manifest.json", "GET", null, null);
        return r.status == 200 && LocalDiscovery.homeAssistant(r.body);
    }

    @Override public JSONObject exchange(String base, String code, String clientId) throws Exception {
        Reply r = request(base, "/auth/token", "POST", null, "grant_type=authorization_code&code=" + PhoneFlows.enc(code) + "&client_id=" + PhoneFlows.enc(clientId));
        if (r.status != 200) throw new PhoneFlows.Refused("Home Assistant refused the sign-in code", r.status);
        try { return new JSONObject(r.body); } catch (org.json.JSONException e) { throw new PhoneFlows.Refused("Home Assistant's answer wasn't readable", r.status); }
    }

    @Override public void revoke(String base, String refreshToken) {
        try { request(base, "/auth/revoke", "POST", null, "token=" + PhoneFlows.enc(refreshToken)); } catch (Exception ignored) {}
    }

    @Override public int check(String base, String token) throws Exception { return request(base, "/api/", "GET", token, null).status; }

    /** The home network's number address for a Home Assistant named by a name (homeassistant.local), else base itself. */
    @Override public String reachable(String base) {
        String numeric = numeric(base, LocalDiscovery::named);
        if (!numeric.equals(base) || context == null) return numeric;
        try {
            // Android 11 TVs often can't look .local names up at all; Home Assistant also announces itself by mDNS.
            java.net.URI u = java.net.URI.create(base);
            if (u.getHost() == null || !u.getHost().toLowerCase(java.util.Locale.ROOT).endsWith(".local") || !"http".equals(u.getScheme())) return base;
            return announced(base, LocalDiscovery.mdns(context, "_home-assistant._tcp.", 3000));
        } catch (RuntimeException ignored) {}
        return base;
    }
    /** Pure: the announced Home Assistant on the same port as {@code base} (ports compared as used: 80/443 when unwritten), else base. */
    static String announced(String base, java.util.List<LocalDiscovery.Found> found) {
        try {
            int port = port(java.net.URI.create(base));
            if (found != null) for (LocalDiscovery.Found f : found) if (port(java.net.URI.create(f.base)) == port) return f.base;
        } catch (RuntimeException ignored) {}
        return base;
    }
    /** The port a URL is actually reached on: its own, else its scheme's (PhoneFlows.haBase writes 8123 when no scheme was typed). */
    static int port(java.net.URI u) { return u.getPort() > 0 ? u.getPort() : "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80; }
    /**
     * Pure: a plain-http base named by a host name, as its private IPv4 address on the home network when {@code resolve}
     * gives one; anything else unchanged (https keeps its name, which its certificate needs; a public address is not
     * the home network's).
     */
    static String numeric(String base, java.util.function.Function<String, java.util.List<java.net.InetAddress>> resolve) {
        try {
            java.net.URI u = java.net.URI.create(base); String host = u.getHost();
            if (host == null || !"http".equals(u.getScheme()) || host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.startsWith("[") || host.contains(":")) return base;
            for (java.net.InetAddress a : resolve.apply(host))
                if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) return Endpoint.base(a.getHostAddress(), "" + u.getPort(), false);
        } catch (RuntimeException ignored) {}
        return base;
    }

    @Override public String vpn() { return LocalDiscovery.vpnInTheWay() ? LocalDiscovery.vpnApp : null; }

    @Override public String mint(String base, String accessToken) throws Exception {
        android.net.Network lan = context == null ? null : LocalDiscovery.lan(context);
        Mint mint = talk(base, accessToken, lan);
        // A VPN that refuses a connection bound to the home network: once more over the default route.
        if (!mint.answered() && lan != null && LocalDiscovery.refused(mint.error)) { LocalDiscovery.blocked = true; mint = talk(base, accessToken, null); }
        if (mint.token() != null) return mint.token();
        if (mint.answered()) throw new PhoneFlows.Refused("Home Assistant didn't create the TV's key", 0);
        throw new IOException("Home Assistant's live connection couldn't be opened");
    }
    private Mint talk(String base, String accessToken, android.net.Network lan) throws InterruptedException {
        okhttp3.OkHttpClient.Builder b = new okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(6, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS);
        if (lan != null) b.socketFactory(lan.getSocketFactory());
        okhttp3.OkHttpClient client = b.build();
        String name = PhoneSetup.clientName(context);
        Mint mint = new Mint(accessToken, name, name + " (2)");
        CountDownLatch finished = new CountDownLatch(1);
        okhttp3.WebSocket socket = client.newWebSocket(new okhttp3.Request.Builder().url(socketUrl(base)).build(), new okhttp3.WebSocketListener() {
            @Override public void onMessage(okhttp3.WebSocket ws, String text) {
                String reply = mint.on(text);
                if (reply != null) ws.send(reply);
                if (mint.finished()) finished.countDown();
            }
            @Override public void onFailure(okhttp3.WebSocket ws, Throwable t, okhttp3.Response response) { mint.fail(t); finished.countDown(); }
            @Override public void onClosing(okhttp3.WebSocket ws, int code, String reason) { mint.fail(null); finished.countDown(); }
            @Override public void onClosed(okhttp3.WebSocket ws, int code, String reason) { mint.fail(null); finished.countDown(); }
        });
        try { finished.await(20, TimeUnit.SECONDS); }
        finally { socket.close(1000, null); client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); }
        return mint;
    }

    /**
     * Home Assistant's WebSocket conversation for one long-lived token: auth_required → auth → auth_ok → request (id 1) →
     * result. Home Assistant refuses a second token with the same name for the same user, answering only "Unknown error",
     * so a refused request is asked once more (id 2) under {@code retryName}. Pure, so it is tested without a socket. The
     * lifespan is ten years; the household can remove it in Home Assistant (profile, Security) at any time, and
     * Disconnect on the TV forgets it.
     */
    static final class Mint {
        static final int ID = 1, RETRY_ID = 2, LIFESPAN_DAYS = 3650;
        private final String access, name, retryName;
        private volatile String token;
        private volatile boolean done, answered, retried;
        /** Why the connection failed before Home Assistant said anything, when it did (a VPN's EPERM, say). */
        volatile Throwable error;
        Mint(String access, String name) { this(access, name, null); }
        Mint(String access, String name, String retryName) { this.access = access; this.name = name; this.retryName = retryName; }
        boolean finished() { return done; }
        /**
         * Home Assistant refused (auth_invalid) or answered the request (a result): a missing token was its refusal. A
         * connection that drops before that, even after auth_required, counts as not reached.
         */
        boolean answered() { return answered; }
        String token() { return token; }
        void fail() { done = true; }
        void fail(Throwable t) { if (t != null && error == null) error = t; done = true; }
        private String request(int id, String client) throws org.json.JSONException {
            return new JSONObject().put("id", id).put("type", "auth/long_lived_access_token").put("client_name", client).put("lifespan", LIFESPAN_DAYS).toString();
        }
        /** The reply to send for one incoming message, or null. */
        String on(String text) {
            if (done) return null;
            try {
                JSONObject m = new JSONObject(text);
                switch (m.optString("type")) {
                    case "auth_required": return new JSONObject().put("type", "auth").put("access_token", access).toString();
                    case "auth_ok": return request(ID, name);
                    case "auth_invalid": answered = true; done = true; return null;
                    case "result": {
                        answered = true;
                        int id = m.optInt("id");
                        if (id != ID && !(retried && id == RETRY_ID)) return null;
                        Object result = m.opt("result");
                        if (m.optBoolean("success") && result instanceof String && !((String) result).isEmpty()) { token = (String) result; done = true; return null; }
                        if (id == ID && retryName != null && !retried) { retried = true; return request(RETRY_ID, retryName); }
                        done = true; return null;
                    }
                    default: return null;
                }
            } catch (Exception e) { done = true; return null; }
        }
    }

    static final class Reply { final int status; final String body; Reply(int status, String body) { this.status = status; this.body = body; } }

    private Reply request(String base, String path, String method, String bearer, String form) throws Exception {
        return LocalDiscovery.request(context, Endpoint.resolve(base, path).toURL(), c -> {
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(CONNECT_MS); c.setReadTimeout(READ_MS); c.setRequestMethod(method); c.setUseCaches(false);
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            if (form != null) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream out = c.getOutputStream()) { out.write(form.getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Reply(status, read(in));
        });
    }
    static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = i.read(b)) != -1) { if (out.size() + n > LIMIT) throw new IOException("Response too large"); out.write(b, 0, n); }
            return out.toString("UTF-8");
        }
    }
}
