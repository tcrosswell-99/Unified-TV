package io.unifiedtv.preview;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Exact configured origin only; never follows redirects or writes remote details to logs. */
final class LocalApi {
    static final int LIMIT = 4 * 1024 * 1024;
    static HttpURLConnection open(JSONObject config, String path, String method) throws Exception {
        if (!config.optBoolean("enabled")) throw new IOException("Not configured");
        URI target = Endpoint.resolve(config.getString("base"), path);
        if ("http".equals(target.getScheme()) && !config.optBoolean("httpConsent")) throw new IOException("HTTP consent required");
        HttpURLConnection c = (HttpURLConnection)target.toURL().openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(false); c.setRequestMethod(method);
        String token = config.optString("token");
        if (!token.isEmpty()) c.setRequestProperty(config.optString("kind").equals("ha") ? "Authorization" : "X-Emby-Token",
            config.optString("kind").equals("ha") ? "Bearer " + token : token);
        return c;
    }
    static byte[] bytes(JSONObject config, String path) throws Exception {
        HttpURLConnection c = open(config, path, "GET");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new ApiError(code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n=in.read(b))!=-1) { if (out.size()+n > LIMIT) throw new IOException("Response too large"); out.write(b,0,n); }
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }
    static JSONObject get(JSONObject c, String p) throws Exception { return new JSONObject(new String(bytes(c,p), StandardCharsets.UTF_8)); }
    static void post(JSONObject config, String path, JSONObject body) throws Exception {
        HttpURLConnection c = open(config,path,"POST");
        try {
            c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
            try (OutputStream out=c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code=c.getResponseCode(); if (code<200 || code>=300) throw new ApiError(code);
        } finally { c.disconnect(); }
    }
    static String segment(String s) { try { return java.net.URLEncoder.encode(s,"UTF-8").replace("+","%20"); } catch(Exception e) { throw new IllegalArgumentException(); } }
    static final class ApiError extends IOException { final int code; ApiError(int code) { super("Service request failed"); this.code=code; } }
}
