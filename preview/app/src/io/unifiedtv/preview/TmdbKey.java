package io.unifiedtv.preview;

import android.content.Context;
import org.json.JSONObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Film posters' key (TMDB, The Movie Database), added through phone setup and kept in the Vault as
 * {@code {"key": value}} under "tmdb", where the library reads it. The value is either a v3 API key (32 hex characters,
 * sent as the api_key query parameter, which is the only way TMDB accepts it) or a v4 API read access token (a JWT
 * starting "eyJ", sent as a Bearer header). Use {@link #bearer} to tell them apart. Never logged or shown again.
 * TMDB's terms ask for the notice "This product uses the TMDB API but is not endorsed or certified by TMDB."
 */
final class TmdbKey {
    private TmdbKey() {}
    static final String VAULT = "tmdb", BASE = "https://api.themoviedb.org";

    static boolean bearer(String key) { return key != null && key.startsWith("eyJ"); }

    static PhoneFlows.Verdict verdict(int status) {
        if (status == 200) return PhoneFlows.Verdict.OK;
        if (status == 401 || status == 403) return PhoneFlows.Verdict.REFUSED;
        if (status == 429) return PhoneFlows.Verdict.BUSY;
        return PhoneFlows.Verdict.OFFLINE;
    }

    /** One request to TMDB's authentication check; base is fixed except in tests. */
    static PhoneFlows.Verdict check(String base, String key) {
        try {
            String path = "/3/authentication" + (bearer(key) ? "" : "?api_key=" + PhoneFlows.enc(key));
            HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            if (bearer(key)) c.setRequestProperty("Authorization", "Bearer " + key);
            try {
                int status = c.getResponseCode();
                if (status == 200) {
                    String body = HaSignIn.read(c.getInputStream());
                    if (!new JSONObject(body).optBoolean("success", true)) return PhoneFlows.Verdict.REFUSED;
                }
                return verdict(status);
            } finally { c.disconnect(); }
        } catch (IOException | org.json.JSONException | RuntimeException e) { return PhoneFlows.Verdict.OFFLINE; }
    }

    static boolean saved(Context c) { try { return !new Vault(c).read(VAULT).optString("key").isEmpty(); } catch (Exception e) { return false; } }
    static void save(Context c, String key) throws Exception { new Vault(c).save(VAULT, new JSONObject().put("key", key)); }
    static void forget(Context c) { new Vault(c).forget(VAULT); }
}
