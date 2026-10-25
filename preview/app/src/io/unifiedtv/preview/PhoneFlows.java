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
    enum Verdict { OK, REFUSED, OFFLINE, BUSY }

    // ---------------------------------------------------------------- Home Assistant
    interface HaNet {
        /** Home Assistant answers at this base (its public manifest names itself). */
        boolean isHomeAssistant(String base) throws Exception;
        /** OAuth: trades the one-time code for {access_token, refresh_token, ...}. */
        JSONObject exchange(String base, String code, String clientId) throws Exception;
        /** Creates this TV's own long-lived token, using a fresh access token (Home Assistant's WebSocket API). */
        String mint(String base, String accessToken) throws Exception;
        /** Best effort: retires the sign-in's refresh token once the TV has its own token. */
        void revoke(String base, String refreshToken);
        /** HTTP status of GET /api/ with this token (200 when Home Assistant accepts it). */
        int check(String base, String token) throws Exception;
    }
    interface HaSave { void save(String base, String token, boolean httpConsent, String how) throws Exception; }

    /**
     * Home Assistant: prefilled with the one the TV found (zero typing), "Sign in with Home Assistant" through its own
     * OAuth (IndieAuth-style: client_id is this page's origin, redirect_uri on the same origin; the code is exchanged on
     * the TV), or a pasted long-lived token as the fallback. Plain HTTP needs one plain-words "Allow" on the phone.
     *
     * Home Assistant's access tokens last 30 minutes and this app's other parts (alerts, household setup, fixtures) use one
     * saved token without refreshing it, so after signing in the TV creates its own long-lived token named for this TV
     * (visible and revocable in the household's Home Assistant profile) and retires the sign-in's refresh token: exactly
     * one credential is kept, in the Vault, as before.
     */
    static final class Ha implements PhoneSetupServer.Flow {
        private final Supplier<String> found;
        private final HaNet net;
        private final HaSave save;
        private final Consumer<String> progress;
        private final SecureRandom random;
        private final String clientName;
        // One sign-in at a time, held only in memory for this session.
        private String state, pendingBase, pendingToken;
        private boolean pendingConsent;

        Ha(Supplier<String> found, HaNet net, HaSave save, Consumer<String> progress, SecureRandom random, String clientName) {
            this.found = found; this.net = net; this.save = save; this.progress = progress; this.random = random; this.clientName = clientName;
        }

        @Override public PhoneSetupServer.Response handle(String action, PhoneSetupServer.Request r) throws Exception {
            switch (action) {
                case "": return PhoneSetupServer.Response.page(start(r, null, null));
                case "ha/start": return r.post() ? begin(r) : PhoneSetupServer.Response.redirect(r.session + "/");
                case "ha/done": return done(r);
                default: return null;
            }
        }

        private String start(PhoneSetupServer.Request r, String problem, String typed) {
            String base = typed == null ? found.get() : typed.isEmpty() ? null : typed;
            StringBuilder b = new StringBuilder(PhonePages.error(problem));
            if (base != null && typed == null) b.append("<p class=\"ok\">Home Assistant was found on your home network.</p>");
            else if (base == null) b.append("<p>The TV couldn't find Home Assistant on your home network. Enter its address below, as you'd type it in a browser.</p>");
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
            boolean ha;
            try { ha = net.isHomeAssistant(base); } catch (Exception e) { ha = false; }
            if (!ha) { progress.accept("Home Assistant didn't answer. Your phone shows what to try."); return PhoneSetupServer.Response.page(start(r, "No Home Assistant answered at that address. Check the address, and that your phone and TV are on the same home network.", display(base))); }
            if ("token".equals(how)) {
                String t = pendingToken; pendingToken = null;
                progress.accept("Checking the token…");
                int status;
                try { status = net.check(base, t); } catch (Exception e) { status = -1; }
                if (status == 401 || status == 403) { progress.accept("Home Assistant didn't accept that token. Your phone shows what to try."); return PhoneSetupServer.Response.page(start(r, "Home Assistant didn't accept that token. Create a new one and paste all of it.", display(base))); }
                if (status != 200) { progress.accept("Home Assistant couldn't be reached. Your phone shows what to try."); return PhoneSetupServer.Response.page(start(r, "The TV couldn't check the token with Home Assistant. Try again in a moment.", display(base))); }
                save.save(base, t, base.startsWith("http:"), "token");
                return PhoneSetupServer.Response.done(PhonePages.done("Connected", "The TV is connected to Home Assistant."));
            }
            byte[] s = new byte[16]; random.nextBytes(s); state = Base64.getUrlEncoder().withoutPadding().encodeToString(s);
            pendingBase = base; pendingConsent = base.startsWith("http:");
            String to = authorize(base, r.origin + "/", r.session + "/ha/done", state);
            progress.accept("Your phone is signing in to Home Assistant…");
            // A redirect after a form is held to the page's form-action policy, so the hand-over is a link the page follows.
            return PhoneSetupServer.Response.page(PhonePages.page("Opening Home Assistant",
                "<p>Opening Home Assistant's sign-in page…</p>" + "<a class=\"button\" href=\"" + PhonePages.esc(to) + "\" rel=\"noreferrer\">Continue to Home Assistant</a>")
                .replace("<title>", "<meta http-equiv=\"refresh\" content=\"0;url=" + PhonePages.esc(to) + "\"><title>"));
        }

        private PhoneSetupServer.Response done(PhoneSetupServer.Request r) throws Exception {
            String base = pendingBase, expected = state;
            if (base == null || expected == null) return PhoneSetupServer.Response.page(PhonePages.page("Start again", "<p>This sign-in has already been used or has run out.</p><a class=\"button\" href=\"" + PhonePages.esc(r.session) + "/\">Start again</a>"));
            if (!r.param("error").isEmpty()) { state = null; progress.accept("Sign-in was cancelled on the phone."); return PhoneSetupServer.Response.page(start(r, "Sign-in was cancelled. You can try again.", null)); }
            String code = r.param("code"), got = r.param("state");
            // The state proves this answer belongs to the sign-in this page started; it is used once.
            if (!expected.equals(got) || code.isEmpty() || code.length() > 512) { state = null; return PhoneSetupServer.Response.page(start(r, "That sign-in didn't match. Please try again.", null)); }
            state = null;
            progress.accept("Signing in to Home Assistant…");
            JSONObject tokens;
            try { tokens = net.exchange(base, code, r.origin + "/"); } catch (Exception e) { tokens = null; }
            String access = tokens == null ? "" : tokens.optString("access_token"), refresh = tokens == null ? "" : tokens.optString("refresh_token");
            if (access.isEmpty()) { progress.accept("Sign-in didn't finish. Your phone shows what to try."); return PhoneSetupServer.Response.page(start(r, "Home Assistant didn't finish the sign-in. Please try again.", null)); }
            String own;
            try { own = net.mint(base, access); } catch (Exception e) { own = null; }
            if (own == null || own.isEmpty()) { if (!refresh.isEmpty()) net.revoke(base, refresh); progress.accept("Sign-in didn't finish. Your phone shows what to try."); return PhoneSetupServer.Response.page(start(r, "Signed in, but Home Assistant didn't let the TV create its own access. Try again, or paste a long-lived token instead.", null)); }
            int status;
            try { status = net.check(base, own); } catch (Exception e) { status = -1; }
            if (status != 200) { if (!refresh.isEmpty()) net.revoke(base, refresh); return PhoneSetupServer.Response.page(start(r, "Home Assistant didn't accept the TV's new access. Please try again.", null)); }
            save.save(base, own, pendingConsent, "signin");
            if (!refresh.isEmpty()) net.revoke(base, refresh);
            pendingBase = null;
            return PhoneSetupServer.Response.done(PhonePages.done("Connected", "The TV is connected to Home Assistant. It appears in your Home Assistant profile as \"" + clientName + "\", where you can remove it at any time."));
        }

        String clientName() { return clientName; }
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
                case BUSY: progress.accept(service.name + " is busy. Your phone shows what to try.");
                    return PhoneSetupServer.Response.page(form(r, service.name + " is busy right now. Wait a minute, then try again."));
                default: progress.accept("The TV couldn't reach " + service.name + ". Your phone shows what to try.");
                    return PhoneSetupServer.Response.page(form(r, "The TV couldn't reach " + service.name + ". Check the TV is online, then try again."));
            }
        }
        private String form(PhoneSetupServer.Request r, String problem) {
            StringBuilder b = new StringBuilder(PhonePages.error(problem)).append("<p>").append(PhonePages.esc(service.phoneIntro)).append("</p>");
            String[] steps = new String[service.steps.length];
            for (int i = 0; i < steps.length; i++) { PhoneService.Step s = service.steps[i]; steps[i] = PhonePages.esc(s.text) + (s.link == null ? "" : " " + PhonePages.link(s.link, s.linkLabel, true)); }
            b.append(PhonePages.steps(steps)).append("<form method=\"post\" action=\"").append(PhonePages.esc(r.session)).append("/save\">");
            for (PhoneService.Field f : service.fields) b.append(f.secret ? PhonePages.secret(f.name, f.label, f.hint)
                : "<label for=\"" + PhonePages.esc(f.name) + "\">" + PhonePages.esc(f.label) + "</label><input id=\"" + PhonePages.esc(f.name) + "\" name=\"" + PhonePages.esc(f.name) + "\" autocapitalize=\"none\" autocorrect=\"off\" spellcheck=\"false\" placeholder=\"" + PhonePages.esc(f.hint) + "\">");
            return PhonePages.page(service.title, b.append("<button type=\"submit\">Save on the TV</button></form><p class=\"muted\">").append(PhonePages.esc(service.note))
                .append(service.fields.stream().anyMatch(f -> f.secret) ? " It is kept encrypted on the TV and never shown again." : "").append("</p>").toString());
        }
    }
}
