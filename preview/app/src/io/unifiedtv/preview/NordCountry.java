package io.unifiedtv.preview;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The country NordVPN says it is connected to, for a small round flag beside Home's VPN shield (owner request, 25 October
 * 2026). Read only from NordVPN's own ongoing notification ("Connected to United Kingdom #1234") through MediaWatch, with
 * notification access the owner has allowed. Only the two-letter country code is kept, in memory; nothing is stored or
 * sent. Shown only while NordVPN reports connected AND Android reports a VPN (VpnGate); it says where NordVPN's server
 * is, nothing more: it is never a claim that Live TV, or anything else, is protected.
 */
final class NordCountry {
    private NordCountry() {}
    /** The ISO 3166 code NordVPN last reported connected to, or null. */
    static volatile String code;

    private static final Pattern CONNECTED = Pattern.compile("(?i)\\bconnected to:?\\s+([\\p{L}][\\p{L} .'&()-]{1,60}?)\\s*(?:#\\s*\\d+|[-–,·|(]|\\.$|$)");
    private static final Pattern SERVER = Pattern.compile("^\\s*([\\p{L}][\\p{L} .'&-]{1,60}?)\\s*#\\s*\\d+\\s*$");

    /**
     * The country in NordVPN's notification texts (title, text, sub-text, big text in any order), or null when it doesn't
     * say it is connected. "Disconnected", "Connecting" and "Paused" never count.
     */
    static String from(CharSequence... texts) {
        boolean connected = false; String server = null;
        for (CharSequence t : texts) {
            if (t == null) continue; String s = t.toString();
            String low = s.toLowerCase(Locale.ROOT);
            if (low.contains("disconnect") || low.contains("connecting") || low.contains("paused")) return null;
            Matcher m = CONNECTED.matcher(s);
            if (m.find()) { String iso = iso(m.group(1)); if (iso != null) return iso; }
            if (low.matches(".*\\bconnected\\b.*")) connected = true;
            Matcher n = SERVER.matcher(s); if (n.find()) server = n.group(1);
        }
        return connected && server != null ? iso(server) : null;
    }

    /** NordVPN says it is not connected (disconnected, connecting or paused). */
    static boolean stopped(CharSequence... texts) {
        for (CharSequence t : texts) { if (t == null) continue; String low = t.toString().toLowerCase(Locale.ROOT); if (low.contains("disconnect") || low.contains("connecting") || low.contains("paused")) return true; }
        return false;
    }
    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        String[][] a = {{"uk", "GB"}, {"great britain", "GB"}, {"britain", "GB"}, {"england", "GB"}, {"scotland", "GB"}, {"wales", "GB"},
            {"usa", "US"}, {"us", "US"}, {"united states of america", "US"}, {"america", "US"}, {"uae", "AE"}, {"czech republic", "CZ"},
            {"south korea", "KR"}, {"korea", "KR"}, {"russia", "RU"}, {"vietnam", "VN"}, {"taiwan", "TW"}, {"hong kong", "HK"},
            {"bosnia and herzegovina", "BA"}, {"north macedonia", "MK"}, {"moldova", "MD"}, {"turkey", "TR"}, {"türkiye", "TR"},
            {"ivory coast", "CI"}, {"holland", "NL"}, {"the netherlands", "NL"}, {"laos", "LA"}, {"bolivia", "BO"}, {"venezuela", "VE"}};
        for (String[] x : a) ALIASES.put(x[0], x[1]);
    }
    /** A country name as NordVPN writes it, to its two-letter code; null when it isn't a country. */
    static String iso(String name) {
        if (name == null) return null;
        String n = name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return null;
        String alias = ALIASES.get(n); if (alias != null) return alias;
        for (String c : Locale.getISOCountries()) if (new Locale("", c).getDisplayCountry(Locale.UK).toLowerCase(Locale.ROOT).equals(n)) return c;
        for (String c : Locale.getISOCountries()) if (new Locale("", c).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT).equals(n)) return c;
        return null;
    }
    /** The flag as two regional-indicator letters (the TV's emoji font draws it); null for anything but two letters. */
    static String flag(String iso) {
        if (iso == null || !iso.matches("[A-Za-z]{2}")) return null;
        String u = iso.toUpperCase(Locale.ROOT);
        return new String(Character.toChars(0x1F1E6 + u.charAt(0) - 'A')) + new String(Character.toChars(0x1F1E6 + u.charAt(1) - 'A'));
    }
    /** The country's name for the shield's focus label. */
    static String name(String iso) { return iso == null ? "" : new Locale("", iso).getDisplayCountry(Locale.UK); }
    /** Shown only while NordVPN says connected and Android reports a VPN. */
    static String shown(String code, VpnGate.State state) { return state == VpnGate.State.CONNECTED ? code : null; }
}
