package io.unifiedtv.preview;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Phone setup's Home Assistant network calls, made by the TV on the home network: the manifest check, the OAuth code
 * exchange, the TV's own long-lived token over Home Assistant's WebSocket API, and a token check. Exact origin only
 * (Endpoint.resolve), no redirects, short timeouts, bounded replies, and nothing is logged. A null Context (tests) uses
 * the default route.
 */
final class HaSignIn implements PhoneFlows.HaNet {
    static final int LIMIT = 64 * 1024;
    private final Context context;
    HaSignIn(Context context) { this.context = context; }

    @Override public boolean isHomeAssistant(String base) throws Exception {
        return LocalDiscovery.homeAssistant(LocalDiscovery.fetch(context, Endpoint.resolve(base, "/manifest.json").toString()));
    }

    @Override public JSONObject exchange(String base, String code, String clientId) throws Exception {
        Reply r = request(base, "/auth/token", "POST", null, "grant_type=authorization_code&code=" + PhoneFlows.enc(code) + "&client_id=" + PhoneFlows.enc(clientId));
        if (r.status != 200) throw new IOException("Home Assistant refused the sign-in (" + r.status + ")");
        return new JSONObject(r.body);
    }

    @Override public void revoke(String base, String refreshToken) {
        try { request(base, "/auth/revoke", "POST", null, "token=" + PhoneFlows.enc(refreshToken)); } catch (Exception ignored) {}
    }

    @Override public int check(String base, String token) throws Exception { return request(base, "/api/", "GET", token, null).status; }

    @Override public String mint(String base, String accessToken) throws Exception {
        okhttp3.OkHttpClient.Builder b = new okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(6, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS);
        android.net.Network lan = context == null ? null : LocalDiscovery.lan(context);
        if (lan != null) b.socketFactory(lan.getSocketFactory());
        okhttp3.OkHttpClient client = b.build();
        Mint mint = new Mint(accessToken, PhoneSetup.clientName(context));
        CountDownLatch finished = new CountDownLatch(1);
        String url = Endpoint.resolve(base, "/api/websocket").toString().replaceFirst("^http", "ws");
        okhttp3.WebSocket socket = client.newWebSocket(new okhttp3.Request.Builder().url(url).build(), new okhttp3.WebSocketListener() {
            @Override public void onMessage(okhttp3.WebSocket ws, String text) {
                String reply = mint.on(text);
                if (reply != null) ws.send(reply);
                if (mint.finished()) finished.countDown();
            }
            @Override public void onFailure(okhttp3.WebSocket ws, Throwable t, okhttp3.Response response) { mint.fail(); finished.countDown(); }
            @Override public void onClosed(okhttp3.WebSocket ws, int code, String reason) { mint.fail(); finished.countDown(); }
        });
        try { finished.await(20, TimeUnit.SECONDS); }
        finally { socket.close(1000, null); client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); }
        if (mint.token == null) throw new IOException("Home Assistant didn't create the TV's access");
        return mint.token;
    }

    /**
     * Home Assistant's WebSocket conversation for one long-lived token: auth_required → auth → auth_ok → request →
     * result. Pure, so it is tested without a socket. The lifespan is ten years; the household can remove it in Home
     * Assistant (profile, Security) at any time, and Disconnect on the TV forgets it.
     */
    static final class Mint {
        static final int ID = 1, LIFESPAN_DAYS = 3650;
        private final String access, name;
        private volatile String token;
        private volatile boolean done;
        Mint(String access, String name) { this.access = access; this.name = name; }
        boolean finished() { return done; }
        String token() { return token; }
        void fail() { done = true; }
        /** The reply to send for one incoming message, or null. */
        String on(String text) {
            if (done) return null;
            try {
                JSONObject m = new JSONObject(text);
                switch (m.optString("type")) {
                    case "auth_required": return new JSONObject().put("type", "auth").put("access_token", access).toString();
                    case "auth_ok": return new JSONObject().put("id", ID).put("type", "auth/long_lived_access_token").put("client_name", name).put("lifespan", LIFESPAN_DAYS).toString();
                    case "auth_invalid": done = true; return null;
                    case "result":
                        if (m.optInt("id") != ID) return null;
                        Object result = m.opt("result");
                        if (m.optBoolean("success") && result instanceof String && !((String) result).isEmpty()) token = (String) result;
                        done = true; return null;
                    default: return null;
                }
            } catch (Exception e) { done = true; return null; }
        }
    }

    static final class Reply { final int status; final String body; Reply(int status, String body) { this.status = status; this.body = body; } }

    private Reply request(String base, String path, String method, String bearer, String form) throws Exception {
        HttpURLConnection c = LocalDiscovery.open(context, Endpoint.resolve(base, path).toURL());
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setRequestMethod(method); c.setUseCaches(false);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            if (form != null) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream out = c.getOutputStream()) { out.write(form.getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Reply(status, read(in));
        } finally { c.disconnect(); }
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
