package io.unifiedtv.preview;

import org.json.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What each phone setup asks and does. Pure: the network and storage come in through small interfaces, so the whole
 * conversation (including Home Assistant's sign-in) is tested against fakes. Words for the phone are plain; words for the
 * TV go through {@code progress} and never contain a secret.
 */
final class PhoneFlows {
    private PhoneFlows() {}

    /** A key check's outcome, in the household's terms. */
    /** VPN: the service refused the request because of a VPN or proxy on the TV, not the key (ApiFootball.Refused.vpn). */
    enum Verdict { OK, REFUSED, OFFLINE, BUSY, VPN }

    // ---------------------------------------------------------------- Home Assistant
    /** Home Assistant answered and said no (an HTTP status, or a refusal on its WebSocket), as against not being reached. */
    static final class Refused extends java.io.IOException {
        final int status;
        Refused(String what, int status) { super(what); this.status = status; }
    }
    interface HaNet {
        /** Home Assistant answers at this base (its public manifest names itself); false when something else answers there. Throws when nothing can be reached. */
        boolean isHomeAssistant(String base) throws Exception;
        /** OAuth: trades the one-time code for {access_token, refresh_token, ...}; Refused when Home Assistant says no. */
        JSONObject exchange(String base, String code, String clientId) throws Exception;
        /** Creates this TV's own long-lived token, using a fresh access token (Home Assistant's WebSocket API); Refused when Home Assistant says no. */
        String mint(String base, String accessToken) throws Exception;
        /** Best effort: retires the sign-in's refresh token once the TV has its own token. */
        void revoke(String base, String refreshToken);
        /** HTTP status of GET /api/ with this token (200 when Home Assistant accepts it). */
        int check(String base, String token) throws Exception;
        /**
         * The same Home Assistant at its number address on the home network when {@code base} names it by a name such as
         * homeassistant.local (many phones and TVs can't look .local names up); otherwise {@code base}.
         */
        default String reachable(String base) { return base; }
        /** After a failed request: the VPN standing between the TV and the home network ("NordVPN", or "" when unknown), else null. */
        default String vpn() { return null; }
    }
    interface HaSave { void save(String base, String token, boolean httpConsent, String how) throws Exception; }

    /**
     * Words for each Home Assistant step that can fail: what the TV shows (never a secret; the phone shows what to try)
     * and the short step recorded for Owner diagnostics and the event log after "Home Assistant: " (no address, no secret).
     */
    static final String TV_UNREACHED = "The TV couldn't reach Home Assistant. Your phone shows what to try.",
        TV_NOT_HA = "That address isn't Home Assistant. Your phone shows what to try.",
        TV_TOKEN_REFUSED = "Home Assistant didn't accept that token. Your phone shows what to try.",
        TV_CAME_BACK = "Your phone came back without signing in. If it couldn't open Home Assistant, check the phone is on your home Wi-Fi.",
        TV_CANCELLED = "Sign-in was cancelled on the phone.",
        TV_CODE_REFUSED = "Home Assistant refused the sign-in code. Your phone shows what to try.",
        TV_KEY_REFUSED = "Couldn't create the TV's key in Home Assistant. Your phone shows what to try.",
        TV_KEY_NOT_ACCEPTED = "Home Assistant didn't accept the TV's new key. Your phone shows what to try.",
        TV_NOT_SAVED = "The TV couldn't save the connection. Nothing was changed.";
    static final String STEP_FIND = "TV couldn't reach it to check the address", STEP_NOT_HA = "the address answered, but isn't Home Assistant",
        STEP_TOKEN = "the pasted token wasn't accepted", STEP_TOKEN_REACH = "TV couldn't reach it to check the token",
        STEP_PHONE = "phone came back without signing in", STEP_CANCELLED = "sign-in cancelled on the phone", STEP_MISMATCH = "sign-in answer didn't match",
        STEP_CODE = "refused the sign-in code", STEP_CODE_REACH = "TV couldn't reach it to finish signing in",
        STEP_KEY = "couldn't create the TV's key", STEP_KEY_REACH = "TV couldn't reach it to create its key",
        STEP_KEY_CHECK = "didn't accept the TV's new key", STEP_SAVE = "TV couldn't save the connection", VPN_STEP = " (a VPN was in the way)";

    /** The TV's words when it couldn't reach Home Assistant: the VPN named when one stood in the way. */
    static String tvUnreached(String vpn) {
        if (vpn == null) return TV_UNREACHED;
        return "The TV couldn't reach Home Assistant — " + ("NordVPN".equals(vpn) ? "NordVPN" : "a VPN on the TV") + " may be blocking your home network. Your phone shows what to try.";
    }
    /** The phone's words when the TV couldn't reach Home Assistant at {@code where} while trying to {@code doing}. */
    static String phoneUnreached(String where, String doing, String vpn, boolean named) {
        String s = "The TV couldn't reach Home Assistant at " + where + " to " + doing + ".";
        if (vpn != null) return s + " " + ("NordVPN".equals(vpn) ? "NordVPN" : "A VPN") + " on the TV may be blocking your home network: in "
            + ("NordVPN".equals(vpn) ? "NordVPN's" : "the VPN app's") + " settings, allow devices on your local network (often called Local network discovery), or disconnect it for a moment, then try again.";
        return s + " Check Home Assistant is switched on and on the same home network as the TV"
            + (named ? ". If you typed a name such as homeassistant.local, try its number address instead (Home Assistant shows it under Settings, System, Network)." : ", then try again.");
    }

    /**
     * Home Assistant: prefilled with the one the TV found (zero typing), "Sign in with Home Assistant" through its own
     * OAuth (IndieAuth-style: client_id is this page's origin, redirect_uri on the same origin; the code is exchanged on
     * the TV), or a pasted long-lived token as the fallback. Plain HTTP needs one plain-words "Allow" on the phone.
     *
     * Home Assistant's access tokens last 30 minutes and this app's other parts (alerts, household setup, fixtures) use one
     * saved token without refreshing it, so after signing in the TV creates its own long-lived token named for this TV
     * (visible and revocable in the household's Home Assistant profile) and retires the sign-in's refresh token: exactly
     * one credential is kept, in the Vault, as before.
     *
     * Owner's TCL, run 93 ("connecting to Home Assistant does not work"): the phone is sent to Home Assistant's number
     * address (a phone without .local look-ups can't open homeassistant.local; the name stays as words), through a page of
     * this TV's own (ha/go), so a phone that comes back without signing in is told what that means; and every step that
     * can fail says which one, on the phone and on the TV, and is recorded (no address, no secret) through {@code failed}.
     */
    static final class Ha implements PhoneSetupServer.Flow {
        private final Supplier<String> found;
        private final HaNet net;
        private final HaSave save;
        private final Consumer<String> progress, failed;
        private final SecureRandom random;
        private final String clientName;
        /** The found Home Assistant as a person knows it ("homeassistant.local" when that name leads to it), else its address. */
        private final java.util.function.Function<String, String> name;
        // One sign-in at a time, held only in memory for this session.
        private String state, pendingBase, pendingToken, pendingWhere, to;
        private boolean pendingConsent;
        private int goVisits;

        Ha(Supplier<String> found, HaNet net, HaSave save, Consumer<String> progress, SecureRandom random, String clientName) {
            this(found, net, save, progress, random, clientName, PhoneFlows::display);
        }
        Ha(Supplier<String> found, HaNet net, HaSave save, Consumer<String> progress, SecureRandom random, String clientName, java.util.function.Function<String, String> name) {
            this(found, net, save, progress, random, clientName, name, step -> {});
        }
        Ha(Supplier<String> found, HaNet net, HaSave save, Consumer<String> progress, SecureRandom random, String clientName, java.util.function.Function<String, String> name, Consumer<String> failed) {
            this.found = found; this.net = net; this.save = save; this.progress = progress; this.random = random; this.clientName = clientName; this.name = name; this.failed = failed;
        }

        @Override public PhoneSetupServer.Response handle(String action, PhoneSetupServer.Request r) throws Exception {
            switch (action) {
                case "": return PhoneSetupServer.Response.page(start(r, null, null));
                case "ha/start": return r.post() ? begin(r) : PhoneSetupServer.Response.redirect(r.session + "/");
                case "ha/go": return go(r);
                case "ha/done": return done(r);
                default: return null;
            }
        }

        /** A failed step: the TV's words, and the step recorded for Owner diagnostics (never an address or a secret). */
        private void fail(String tv, String step) {
            progress.accept(tv);
            try { failed.accept(step); } catch (RuntimeException ignored) {}
        }
        /** The TV couldn't reach Home Assistant: said on the TV and the phone, naming a VPN that stood in the way. */
        private PhoneSetupServer.Response unreached(PhoneSetupServer.Request r, String where, String doing, String step, boolean named, String typed) {
            String vpn; try { vpn = net.vpn(); } catch (RuntimeException e) { vpn = null; }
            fail(tvUnreached(vpn), step + (vpn == null ? "" : VPN_STEP));
            return PhoneSetupServer.Response.page(start(r, phoneUnreached(where, doing, vpn, named), typed));
        }

        private String start(PhoneSetupServer.Request r, String problem, String typed) {
            String base = typed == null ? found.get() : typed.isEmpty() ? null : typed;
            StringBuilder b = new StringBuilder(PhonePages.error(problem));
            if (base != null && typed == null) {
                String known; try { known = name.apply(base); } catch (RuntimeException e) { known = null; }
                b.append("<p class=\"ok\">We found Home Assistant at <b>").append(PhonePages.esc(known == null || known.isEmpty() ? display(base) : known)).append("</b>.</p>");
            }
            else if (base == null) b.append("<p>The TV couldn't find Home Assistant on your home network. Enter its address below, as you'd type it in a browser.</p>");
            b.append(PhonePages.steps("Tap <b>Sign in with Home Assistant</b> below.", "Home Assistant's own sign-in page opens. Sign in there as usual.",
                "You come back to this page, and the TV saves the connection. You'll see Connected here and on the TV."));
            b.append("<form method=\"post\" action=\"").append(PhonePages.esc(r.session)).append("/ha/start\">");
            String field = "<label for=\"address\">Home Assistant address</label><input id=\"address\" name=\"address\" inputmode=\"url\" autocapitalize=\"none\" autocorrect=\"off\" spellcheck=\"false\" placeholder=\"homeassistant.local:8123\" value=\""
                + PhonePages.esc(base == null ? "" : display(base)) + "\">";
            b.append(base == null ? field : "<details><summary>Use a different address</summary>" + field + "</details>");
            b.append("<button type=\"submit\" name=\"how\" value=\"signin\">Sign in with Home Assistant</button>")
             .append("<p class=\"muted\">You'll sign in on Home Assistant's own page. Unified TV never sees your password.</p>")
             .append("<details><summary>Paste a long-lived token instead</summary><p class=\"muted\">In Home Assistant, open your profile, then Security, and create a long-lived access token. Copy it and paste it here.</p>")
             .append(PhonePages.secret("token", "Long-lived access token", "Paste the token"))
             .append("<button type=\"submit\" name=\"how\" value=\"token\" class=\"quiet\">Use this token</button></details></form>");
            return PhonePages.page("Connect Home Assistant", b.toString());
        }

        private PhoneSetupServer.Response begin(PhoneSetupServer.Request r) throws Exception {
            String typed = r.param("address").trim(), how = r.param("how");
            String base = typed.isEmpty() ? found.get() : haBase(typed);
            if (base == null) return PhoneSetupServer.Response.page(start(r, typed.isEmpty() ? "Enter your Home Assistant's address." : "That address doesn't look right. Try something like homeassistant.local:8123.", typed));
            String token = r.param("token").replaceAll("\\s", "");
            if ("token".equals(how)) {
                if (!token.isEmpty()) pendingToken = token;
                if (pendingToken == null || !pendingToken.matches("[A-Za-z0-9._-]{20,4096}"))
                    { pendingToken = null; return PhoneSetupServer.Response.page(start(r, "That doesn't look like a Home Assistant token. Copy the whole token and paste it again.", typed.isEmpty() ? null : base)); }
            }
            boolean consent = base.startsWith("https:") || "yes".equals(r.param("allowHttp"));
            if (!consent) {
                pendingBase = base;
                return PhoneSetupServer.Response.page(PhonePages.page("Allow an unencrypted connection?",
                    "<p>Your Home Assistant uses an unencrypted connection on your home network. Allow it?</p>"
                    + "<p class=\"muted\">This is normal for Home Assistant at home. The TV will only use it for this one address.</p>"
                    + "<form method=\"post\" action=\"" + PhonePages.esc(r.session) + "/ha/start\">" + PhonePages.hidden("address", display(base)) + PhonePages.hidden("how", "token".equals(how) ? "token" : "signin")
                    + PhonePages.hidden("allowHttp", "yes") + "<button type=\"submit\">Allow</button></form>"
                    + "<a class=\"button quiet\" href=\"" + PhonePages.esc(r.session) + "/\">Not now</a>"));
            }
            progress.accept("Checking Home Assistant…");
            // A name (homeassistant.local) is used as its number address from here on: the phone and the TV both open that.
            String named = base, reach;
            try { reach = net.reachable(base); } catch (RuntimeException e) { reach = null; }
            if (reach == null || reach.isEmpty()) reach = base;
            boolean byName = !literal(named);
            String where = where(named, reach);
            Boolean ha;
            try { ha = net.isHomeAssistant(reach); } catch (Exception e) { ha = null; }
            if (ha == null) return unreached(r, where, "check the address", STEP_FIND, byName && reach.equals(named), display(named));
            if (!ha) { fail(TV_NOT_HA, STEP_NOT_HA); return PhoneSetupServer.Response.page(start(r, "Something answered at " + where + ", but it isn't Home Assistant. Check the address and the port (usually 8123).", display(named))); }
            if ("token".equals(how)) {
                String t = pendingToken; pendingToken = null;
                progress.accept("Checking the token…");
                int status;
                try { status = net.check(reach, t); } catch (Exception e) { status = -1; }
                if (status == 401 || status == 403) { fail(TV_TOKEN_REFUSED, STEP_TOKEN); return PhoneSetupServer.Response.page(start(r, "Home Assistant didn't accept that token. Create a new one and paste all of it.", display(named))); }
                if (status == -1) return unreached(r, where, "check the token", STEP_TOKEN_REACH, false, display(named));
                if (status != 200) { fail(TV_TOKEN_REFUSED, STEP_TOKEN + " (" + status + ")"); return PhoneSetupServer.Response.page(start(r, "Home Assistant answered, but couldn't check the token (error " + status + "). Try again in a moment.", display(named))); }
                try { save.save(reach, t, reach.startsWith("http:"), "token"); }
                catch (Exception e) { fail(TV_NOT_SAVED, STEP_SAVE); return PhoneSetupServer.Response.page(start(r, "The TV couldn't save the connection. Try again.", display(named))); }
                return PhoneSetupServer.Response.done(PhonePages.done("Connected", "The TV is connected to Home Assistant."));
            }
            byte[] s = new byte[16]; random.nextBytes(s); state = Base64.getUrlEncoder().withoutPadding().encodeToString(s);
            pendingBase = reach; pendingConsent = reach.startsWith("http:"); pendingWhere = where; goVisits = 0;
            to = authorize(reach, r.origin + "/", r.session + "/ha/done", state);
            progress.accept("Your phone is opening Home Assistant at " + hostPort(reach) + "…");
            // To this TV's own page first (same origin, so the form's redirect is allowed), which then hands over to Home
            // Assistant: if the phone can't open Home Assistant and comes back, that page says so instead of a resubmit.
            return PhoneSetupServer.Response.redirect(r.session + "/ha/go");
        }

        /** The hand-over to Home Assistant's sign-in page; seen again only when the phone came back without signing in. */
        private PhoneSetupServer.Response go(PhoneSetupServer.Request r) {
            if (state == null || pendingBase == null || to == null) return PhoneSetupServer.Response.page(PhonePages.page("Start again", "<p>This sign-in has already been used or has run out.</p><a class=\"button\" href=\"" + PhonePages.esc(r.session) + "/\">Start again</a>"));
            String at = PhonePages.esc(pendingWhere == null ? hostPort(pendingBase) : pendingWhere);
            if (goVisits++ == 0) return PhoneSetupServer.Response.page(PhonePages.page("Opening Home Assistant",
                "<p>Opening Home Assistant's sign-in page at <b>" + at + "</b>…</p>" + "<a class=\"button\" href=\"" + PhonePages.esc(to) + "\" rel=\"noreferrer\">Continue to Home Assistant</a>"
                + "<p class=\"muted\">If your phone says the page can't be opened, it couldn't reach Home Assistant: check the phone is on your home Wi-Fi, not mobile data.</p>")
                .replace("<title>", "<meta http-equiv=\"refresh\" content=\"0;url=" + PhonePages.esc(to) + "\"><title>"));
            fail(TV_CAME_BACK, STEP_PHONE);
            return PhoneSetupServer.Response.page(PhonePages.page("Didn't Home Assistant open?",
                PhonePages.error("Your phone couldn't open Home Assistant at " + (pendingWhere == null ? hostPort(pendingBase) : pendingWhere) + ", or you came back before signing in.")
                + "<p>Check your phone is on your home Wi-Fi (not mobile data or a VPN), then try again.</p>"
                + "<a class=\"button\" href=\"" + PhonePages.esc(to) + "\" rel=\"noreferrer\">Try again</a>"
                + "<a class=\"button quiet\" href=\"" + PhonePages.esc(r.session) + "/\">Paste a long-lived token instead</a>"));
        }

        private PhoneSetupServer.Response done(PhoneSetupServer.Request r) throws Exception {
            String base = pendingBase, expected = state, where = pendingWhere == null ? hostPort(base) : pendingWhere;
            if (base == null || expected == null) return PhoneSetupServer.Response.page(PhonePages.page("Start again", "<p>This sign-in has already been used or has run out.</p><a class=\"button\" href=\"" + PhonePages.esc(r.session) + "/\">Start again</a>"));
            if (!r.param("error").isEmpty()) { state = null; fail(TV_CANCELLED, STEP_CANCELLED); return PhoneSetupServer.Response.page(start(r, "Sign-in was cancelled. You can try again.", null)); }
            String code = r.param("code"), got = r.param("state");
            // The state proves this answer belongs to the sign-in this page started; it is used once.
            if (!expected.equals(got) || code.isEmpty() || code.length() > 512) { state = null; fail("That sign-in didn't match. Your phone shows what to try.", STEP_MISMATCH); return PhoneSetupServer.Response.page(start(r, "That sign-in didn't match. Please try again.", null)); }
            state = null;
            progress.accept("Signing in to Home Assistant…");
            JSONObject tokens;
            try { tokens = net.exchange(base, code, r.origin + "/"); }
            catch (Refused e) { fail(TV_CODE_REFUSED, STEP_CODE + (e.status > 0 ? " (" + e.status + ")" : "")); return PhoneSetupServer.Response.page(start(r, "Home Assistant refused the sign-in code. Start again: each code works once, for a few minutes.", null)); }
            catch (Exception e) { return unreached(r, where, "finish signing in", STEP_CODE_REACH, false, null); }
            String access = tokens == null ? "" : tokens.optString("access_token"), refresh = tokens == null ? "" : tokens.optString("refresh_token");
            if (access.isEmpty()) { fail(TV_CODE_REFUSED, STEP_CODE); return PhoneSetupServer.Response.page(start(r, "Home Assistant refused the sign-in code. Start again: each code works once, for a few minutes.", null)); }
            progress.accept("Creating the TV's key in Home Assistant…");
            String own;
            try { own = net.mint(base, access); }
            catch (Refused e) { own = null; }
            catch (Exception e) { if (!refresh.isEmpty()) net.revoke(base, refresh); return unreached(r, where, "create the TV's key", STEP_KEY_REACH, false, null); }
            if (own == null || own.isEmpty()) { if (!refresh.isEmpty()) net.revoke(base, refresh); fail(TV_KEY_REFUSED, STEP_KEY); return PhoneSetupServer.Response.page(start(r, "Couldn't create the TV's key in Home Assistant. Try again, or paste a long-lived token instead.", null)); }
            int status;
            try { status = net.check(base, own); } catch (Exception e) { status = -1; }
            if (status == -1) { if (!refresh.isEmpty()) net.revoke(base, refresh); return unreached(r, where, "check the TV's new key", STEP_KEY_REACH, false, null); }
            if (status != 200) { if (!refresh.isEmpty()) net.revoke(base, refresh); fail(TV_KEY_NOT_ACCEPTED, STEP_KEY_CHECK + " (" + status + ")"); return PhoneSetupServer.Response.page(start(r, "Home Assistant didn't accept the TV's new key. Please try again.", null)); }
            try { save.save(base, own, pendingConsent, "signin"); }
            catch (Exception e) { if (!refresh.isEmpty()) net.revoke(base, refresh); fail(TV_NOT_SAVED, STEP_SAVE); return PhoneSetupServer.Response.page(start(r, "The TV couldn't save the connection. Try again.", null)); }
            if (!refresh.isEmpty()) net.revoke(base, refresh);
            pendingBase = null; to = null;
            return PhoneSetupServer.Response.done(PhonePages.done("Connected", "The TV is connected to Home Assistant. It appears in your Home Assistant profile as \"" + clientName + "\", where you can remove it at any time."));
        }

        String clientName() { return clientName; }
    }

    /** A base's host and port as a person reads it ("192.168.1.5:8123"). */
    static String hostPort(String base) {
        if (base == null) return "";
        try { URI u = URI.create(base); return u.getRawAuthority() == null ? base : u.getRawAuthority(); } catch (RuntimeException e) { return base; }
    }
    /** Where Home Assistant is, in words: the name and, when the TV turned it into one, the number address too. */
    static String where(String named, String reach) {
        return reach == null || reach.equals(named) ? hostPort(named) : hostPort(named) + " (" + hostPort(reach) + ")";
    }
    /** The base's host is an address, not a name. */
    static boolean literal(String base) {
        try { String h = URI.create(base).getHost(); return h != null && (h.matches("\\d{1,3}(\\.\\d{1,3}){3}") || h.startsWith("[") || h.contains(":")); } catch (RuntimeException e) { return false; }
    }

    /** Home Assistant's OAuth authorise address on its own origin. */
    static String authorize(String base, String clientId, String redirect, String state) {
        return base + "/auth/authorize?response_type=code&client_id=" + enc(clientId) + "&redirect_uri=" + enc(redirect) + "&state=" + enc(state);
    }
    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { throw new IllegalArgumentException(); } }

    /**
     * An address as a person types it: "homeassistant.local:8123", a bare home-network address, "https://ha.example.org" or with a
     * trailing slash. No path, query, user or fragment. Without a scheme it is Home Assistant's usual http on 8123.
     * Null when it can't be an address.
     */
    static String haBase(String typed) {
        String t = typed == null ? "" : typed.trim();
        if (t.isEmpty() || t.length() > 255 || t.matches(".*\\s.*")) return null;
        boolean scheme = t.contains("://");
        if (scheme && !t.toLowerCase(java.util.Locale.ROOT).matches("https?://.*")) return null;
        try {
            URI u = new URI(scheme ? t : "http://" + t);
            if (u.getHost() == null || u.getRawUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null) return null;
            if (u.getRawPath() != null && !u.getRawPath().isEmpty() && !u.getRawPath().equals("/")) return null;
            boolean https = "https".equalsIgnoreCase(u.getScheme());
            int port = u.getPort() > 0 ? u.getPort() : !scheme ? 8123 : https ? 443 : 80;
            return Endpoint.base(u.getHost(), "" + port, https);
        } catch (Exception e) { return null; }
    }
    /** A base as shown to a person: without "http://" for plain addresses on the usual port. */
    static String display(String base) { return base; }

    // ---------------------------------------------------------------- settings backup (SET-081)
    /**
     * The phone's settings backup page: download this TV's settings file, or send one back. The file is checked on the TV
     * (SettingsBackup.parse) and only handed over when usable; nothing changes until an adult confirms on the TV with the
     * PIN, so this page never says "restored".
     */
    static final class Backup implements PhoneSetupServer.Flow {
        private final Supplier<String> export; private final Consumer<SettingsBackup.Incoming> received; private final Consumer<String> progress;
        Backup(Supplier<String> export, Consumer<SettingsBackup.Incoming> received, Consumer<String> progress) { this.export = export; this.received = received; this.progress = progress; }

        @Override public PhoneSetupServer.Response handle(String action, PhoneSetupServer.Request r) throws Exception {
            switch (action) {
                case "": return PhoneSetupServer.Response.page(start(r, null));
                case "backup/file":
                    progress.accept("Your phone saved a copy of this TV's settings.");
                    return PhoneSetupServer.Response.file(export.get(), SettingsBackup.TYPE, SettingsBackup.FILE);
                case "backup/restore": {
                    if (!r.post()) return PhoneSetupServer.Response.redirect(r.session + "/");
                    SettingsBackup.Incoming in = SettingsBackup.parse(r.param("file"));
                    if (!in.usable()) return PhoneSetupServer.Response.page(start(r, in.problem));
                    received.accept(in);
                    return PhoneSetupServer.Response.done(PhonePages.done("Sent to the TV", "The TV shows what would change. Nothing changes until an adult confirms there with the PIN."));
                }
                default: return null;
            }
        }
        private String start(PhoneSetupServer.Request r, String problem) {
            return PhonePages.page("Settings backup", PhonePages.error(problem)
                + "<h2>Save a copy</h2><p>A small file with this TV's look, Home and dock, playback, notifications and Kids choices.</p>"
                + "<p class=\"muted\">It never contains passwords, keys, the adult PIN, camera pictures, photos or addresses. Connections are set up again after a restore.</p>"
                + "<a class=\"button\" href=\"" + PhonePages.esc(r.session) + "/backup/file\" download=\"" + SettingsBackup.FILE + "\">Download the settings file</a>"
                + "<h2>Restore from a copy</h2><form method=\"post\" enctype=\"multipart/form-data\" action=\"" + PhonePages.esc(r.session) + "/backup/restore\">"
                + "<label for=\"file\">The settings file</label><input id=\"file\" type=\"file\" name=\"file\" accept=\".json,application/json\" required>"
                + "<button type=\"submit\">Send to the TV</button></form><p class=\"muted\">The TV shows what would change before anything does.</p>");
        }
    }

    // ---------------------------------------------------------------- any other service: a form from its descriptor
    /**
     * The generic phone page for a {@link PhoneService}: its plain steps (with the sign-up link), its fields, then one
     * validate-and-save on the TV. The page never shows a value back, so a secret is never echoed.
     */
    static final class Form implements PhoneSetupServer.Flow {
        private final PhoneService service; private final Consumer<String> progress;
        Form(PhoneService service, Consumer<String> progress) { this.service = service; this.progress = progress; }

        @Override public PhoneSetupServer.Response handle(String action, PhoneSetupServer.Request r) throws Exception {
            if (action.isEmpty()) return PhoneSetupServer.Response.page(form(r, null));
            if (!action.equals("save")) return null;
            if (!r.post()) return PhoneSetupServer.Response.redirect(r.session + "/");
            java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
            for (PhoneService.Field f : service.fields) {
                String v = f.secret ? r.param(f.name).replaceAll("\\s", "") : r.param(f.name).trim();
                if (v.isEmpty() && f.required) return PhoneSetupServer.Response.page(form(r, "Paste your " + f.label.toLowerCase(java.util.Locale.ROOT) + " first."));
                if (!v.isEmpty() && f.pattern != null && !v.matches(f.pattern)) return PhoneSetupServer.Response.page(form(r, "That doesn't look like " + f.example + ". Copy all of it and paste it again."));
                values.put(f.name, v);
            }
            progress.accept("Checking with " + service.name + "…");
            Verdict v;
            try { v = service.submit.submit(values); } catch (Exception e) { v = Verdict.OFFLINE; }
            switch (v) {
                case OK: return PhoneSetupServer.Response.done(PhonePages.done("Saved", service.phoneDone));
                case REFUSED: progress.accept(service.name + " didn't accept that. Your phone shows what to try.");
                    return PhoneSetupServer.Response.page(form(r, service.name + " didn't accept that. Check you copied all of it, and that your account is confirmed."));
                case VPN: progress.accept(service.vpnWords != null ? service.vpnWords : service.name + " refused the request because a VPN is on.");
                    return PhoneSetupServer.Response.page(form(r, service.name + " refused the TV's request because a VPN is on (on the TV, not your phone). Your key may be fine: see the TV for what to do."));
                case BUSY: progress.accept(service.name + " is busy. Your phone shows what to try.");
                    return PhoneSetupServer.Response.page(form(r, service.name + " is busy right now. Wait a minute, then try again."));
                default: progress.accept("The TV couldn't reach " + service.name + ". Your phone shows what to try.");
                    return PhoneSetupServer.Response.page(form(r, "The TV couldn't reach " + service.name + ". Check the TV is online, then try again."));
            }
        }
        private String form(PhoneSetupServer.Request r, String problem) {
            StringBuilder b = new StringBuilder(PhonePages.error(problem)).append("<p>").append(PhonePages.esc(service.phoneIntro)).append("</p>");
            // Numbered steps; the sign-up ("Get a free key") is the one prominent link, and the paste box is on this same page.
            String[] steps = new String[service.steps.length];
            for (int i = 0; i < steps.length; i++) { PhoneService.Step s = service.steps[i];
                steps[i] = PhonePages.esc(s.text) + (s.link == null ? "" : s.link.equals(service.signup) ? PhonePages.primary(s.link, s.linkLabel) : " " + PhonePages.link(s.link, s.linkLabel, true)); }
            b.append(PhonePages.steps(steps)).append("<form method=\"post\" action=\"").append(PhonePages.esc(r.session)).append("/save\">");
            for (PhoneService.Field f : service.fields) b.append(f.secret ? PhonePages.secret(f.name, f.label, f.hint)
                : "<label for=\"" + PhonePages.esc(f.name) + "\">" + PhonePages.esc(f.label) + "</label><input id=\"" + PhonePages.esc(f.name) + "\" name=\"" + PhonePages.esc(f.name) + "\" autocapitalize=\"none\" autocorrect=\"off\" spellcheck=\"false\" placeholder=\"" + PhonePages.esc(f.hint) + "\">");
            b.append("<button type=\"submit\">Save on the TV</button></form>");
            if (service.next != null && !service.next.isEmpty()) b.append("<h2>What happens next</h2><p>").append(PhonePages.esc(service.next)).append("</p>");
            return PhonePages.page(service.title, b.append("<p class=\"muted\">").append(PhonePages.esc(service.note))
                .append(service.fields.stream().anyMatch(f -> f.secret) ? " It is kept encrypted on the TV and never shown again." : "").append("</p>").toString());
        }
    }
}
