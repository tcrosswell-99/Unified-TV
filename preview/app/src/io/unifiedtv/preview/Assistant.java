package io.unifiedtv.preview;

import android.content.Context;
import org.json.JSONObject;
import java.net.URI;
import java.util.Locale;

/**
 * The household's own AI assistant for "Ask your TV" (PRODUCT_SPEC §14, §24.6): an address for the household's protected
 * relay and its key, set up on the phone (OD-17, PhoneService.ASSISTANT) and kept encrypted in the Vault. Owner's TCL,
 * 25 October 2026: the microphone looked ready although no assistant was set up.
 *
 * Honest limits (AGENTS rule 1): saving these does NOT make general questions work. Unified TV does not yet send
 * anything to the relay; that is not tested and the TV says so. Local requests (open an app, find a title) never need it.
 */
final class Assistant {
    private Assistant() {}
    static final String VAULT = "assistant", CONFIGURED = "assistant.configured";
    static final String NOT_SET_UP = "No assistant is set up yet. Opening apps and finding films still work.",
        SAVED_NOT_TESTED = "Your assistant's details are saved. Answering general questions through it is not tested yet, so the TV doesn't send it anything.";

    /** Read from preferences, not the Vault: cheap enough for Home's pill and the pane. */
    static boolean configured(Context c) {
        try { return c.getSharedPreferences(HouseholdSettings.PREFS, 0).getBoolean(CONFIGURED, false); } catch (RuntimeException e) { return false; }
    }

    /**
     * The relay's address as typed: https anywhere, or plain http only on the home network (a private address or a .local
     * name). No user, query or fragment. Null when it can't be one.
     */
    static String endpoint(String typed) {
        String t = typed == null ? "" : typed.trim();
        if (t.isEmpty() || t.length() > 300 || t.matches(".*\\s.*")) return null;
        try {
            URI u = new URI(t);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT), host = u.getHost();
            if (host == null || u.getRawUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null) return null;
            if (scheme.equals("https")) return t.replaceAll("/+$", "");
            if (!scheme.equals("http")) return null;
            String h = host.toLowerCase(Locale.ROOT);
            boolean home = h.endsWith(".local") || h.matches("10\\.\\d+\\.\\d+\\.\\d+") || h.matches("192\\.168\\.\\d+\\.\\d+") || h.matches("172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+");
            return home ? t.replaceAll("/+$", "") : null;
        } catch (Exception e) { return null; }
    }

    static void save(Context c, String endpoint, String key) throws Exception {
        new Vault(c).save(VAULT, new JSONObject().put("endpoint", endpoint).put("key", key).put("enabled", true).put("tested", false));
        c.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putBoolean(CONFIGURED, true).apply();
        EventLog.record(c, EventLog.CONNECTION, "Assistant details saved (not tested)");
    }

    static void forget(Context c) {
        new Vault(c).forget(VAULT);
        c.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().remove(CONFIGURED).apply();
    }
}
