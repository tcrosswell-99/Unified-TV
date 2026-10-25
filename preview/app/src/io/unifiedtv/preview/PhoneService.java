package io.unifiedtv.preview;

import android.content.Context;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A service that needs an account, sign-in or key, described once so phone setup can offer it (owner decision OD-17,
 * 25 October 2026: every such service is set up by QR code on the phone; typing on the TV is only an Advanced fallback).
 * A descriptor gives the name, the sign-up link, plain steps, the fields to paste, and one validate-and-save callback;
 * {@link PhoneFlows.Form} draws the phone page from it. A service with its own sign-in (Home Assistant) supplies a
 * {@link Custom} flow instead. To add a service: one more entry in {@link #of}, and a row in Settings that opens the
 * task "phone-" + its id.
 */
final class PhoneService {
    static final String HA = "ha", FOOTBALL = "football", TMDB = "tmdb", BACKUP = "backup", ASSISTANT = "assistant";
    static final String[] IDS = {HA, FOOTBALL, TMDB, BACKUP, ASSISTANT};
    /**
     * API-Football (owner's sign-up on a phone, 25 October 2026): the key is at Account → My Access in the dashboard's
     * sidebar (behind ☰ on a phone), labelled API-KEY and masked until tapped. "Reset API key" does not show it.
     */
    static final String FOOTBALL_KEY = "https://dashboard.api-football.com/profile?access",
        FOOTBALL_WHERE = "On a phone, tap ☰, then Account → My Access. Tap the hidden key to show it, then copy it.",
        /** Owner, 25 October 2026: the key was blank under "It seems that you are using a Proxy or a VPN". */
        FOOTBALL_PROXY = "If your key is blank and the page mentions a Proxy or VPN, switch off any VPN on your phone and iCloud Private Relay (iPhone: Settings → your name → iCloud → Private Relay; and Wi-Fi → ⓘ → Limit IP Address Tracking), log out and back in, then copy the key.";
    static final String FOOTBALL_SIGNUP = "https://dashboard.api-football.com/register", TMDB_SIGNUP = "https://www.themoviedb.org/signup", TMDB_API = "https://www.themoviedb.org/settings/api";
    /** The first TV step for every service: what the one QR code opens (owner, 25 October 2026: it was unclear). */
    static final String SCAN = "Scan this code with your phone's camera. It opens a setup page from this TV.";

    /** One step for the phone; an optional link opens in a new tab with no referrer. */
    static final class Step {
        final String text, link, linkLabel;
        Step(String text) { this(text, null, null); }
        Step(String text, String link, String linkLabel) { this.text = text; this.link = link; this.linkLabel = linkLabel; }
    }
    /** One thing to paste or type on the phone. Secret fields are never pre-filled, echoed or shown on the TV. */
    static final class Field {
        final String name, label, hint, pattern, example; final boolean secret, required;
        Field(String name, String label, String hint, String pattern, String example, boolean secret, boolean required) {
            this.name = name; this.label = label; this.hint = hint; this.pattern = pattern; this.example = example; this.secret = secret; this.required = required;
        }
        static Field key(String label, String pattern, String example) { return new Field("key", label, "Paste it here", pattern, example, true, true); }
    }
    /** Checks the values with the service (once) and, only when they work, saves them on the TV. */
    interface Submit { PhoneFlows.Verdict submit(Map<String, String> values) throws Exception; }
    /** A service with its own sign-in page: it supplies the whole flow. */
    interface Custom { PhoneSetupServer.Flow flow(Context app, Consumer<String> progress, ExecutorService background); }

    /**
     * tvSteps: the numbered steps beside the one QR code on the TV (the first is always SCAN). steps: the numbered steps
     * on the phone page. next: what happens after saving, said on the phone page. signup: the "Get a free key" link,
     * shown on the phone only; the TV's QR code never changes to it.
     */
    final String id, name, title, tvIntro, phoneIntro, note, signup, signupLabel, next, tvDone, phoneDone;
    /** What the TV says when the service refused a request because of a VPN on the TV (Verdict.VPN); null for most. */
    String vpnWords;
    final String[] tvSteps; final Step[] steps; final List<Field> fields; final Submit submit; final Custom custom;

    PhoneService(String id, String name, String title, String tvIntro, String[] tvSteps, String phoneIntro, Step[] steps, List<Field> fields, String note,
                 String signup, String signupLabel, String next, String tvDone, String phoneDone, Submit submit, Custom custom) {
        this.id = id; this.name = name; this.title = title; this.tvIntro = tvIntro; this.tvSteps = tvSteps; this.phoneIntro = phoneIntro; this.steps = steps; this.fields = fields; this.note = note;
        this.signup = signup; this.signupLabel = signupLabel; this.next = next; this.tvDone = tvDone; this.phoneDone = phoneDone; this.submit = submit; this.custom = custom;
    }

    /** The services this TV can set up by phone; null for an unknown id. {@code app} is the application context. */
    static PhoneService of(Context app, String id) {
        switch (id == null ? "" : id) {
            case HA: return new PhoneService(HA, "Home Assistant", "Connect Home Assistant",
                "Your phone signs you in to Home Assistant on its own page. Nothing to type with the remote.",
                new String[]{SCAN, "On your phone, tap Sign in with Home Assistant. Home Assistant's own sign-in page opens: sign in there as usual.",
                    "Your phone comes back to the setup page, and the TV saves the connection. Unified TV never sees your password."},
                "", new Step[0], Collections.emptyList(), "",
                null, null, "", "Connected to Home Assistant.", "The TV is connected to Home Assistant.", null,
                (c, progress, background) -> {
                    Future<String> found = background.submit(() -> PhoneSetup.found(c));
                    return new PhoneFlows.Ha(() -> { try { return found.get(9, TimeUnit.SECONDS); } catch (Exception e) { return null; } },
                        new HaSignIn(c), (base, token, consent, how) -> PhoneSetup.saveHa(c, base, token, consent, how), progress, new java.security.SecureRandom(), PhoneSetup.clientName(c),
                        PhoneSetup::haName);
                });
            case FOOTBALL: return football(values -> {
                String key = values.get("key");
                try { int[] account = ApiFootball.account(ApiFootball.http.get(key, ApiFootball.STATUS)); FixtureFeed.saveKey(app, key); FixtureFeed.noteUsed(app, account[0]); TeamList.ensure(app); return PhoneFlows.Verdict.OK; }
                catch (Exception e) { return ApiFootball.verdict(e, VpnGate.routed(app)); }
            });
            case TMDB: return tmdb(values -> {
                String key = values.get("key"); PhoneFlows.Verdict v = TmdbKey.check(TmdbKey.BASE, key);
                if (v == PhoneFlows.Verdict.OK) TmdbKey.save(app, key);
                return v;
            });
            // SET-081: not a service sign-in, but the same phone page: the settings file goes to and from the phone.
            case BACKUP: return new PhoneService(BACKUP, "Settings backup", "Settings backup",
                "Your phone can save a copy of this TV's settings, or send a copy back. Passwords, keys, the PIN and camera pictures are never in it.",
                new String[]{SCAN, "On your phone, tap Download the settings file to keep a copy, or choose a file you saved earlier and tap Send to the TV.",
                    "If you sent a file, the TV shows what would change. Nothing changes until an adult confirms here."},
                "", new Step[0], Collections.emptyList(), "",
                null, null, "", "The settings file arrived. Check what would change below.", "Sent to the TV.", null,
                (c, progress, background) -> new PhoneFlows.Backup(() -> SettingsBackup.export(c.getSharedPreferences(HouseholdSettings.PREFS, 0).getAll(), java.time.LocalDate.now().toString()),
                    SettingsBackup::offer, progress));
            case ASSISTANT: return assistant(values -> {
                String endpoint = Assistant.endpoint(values.get("endpoint"));
                if (endpoint == null) return PhoneFlows.Verdict.REFUSED;
                Assistant.save(app, endpoint, values.get("key"));
                return PhoneFlows.Verdict.OK;
            });
            default: return null;
        }
    }

    /**
     * API-Football (optional "more detail" for football fixtures, OD-22), with the given validate-and-save. Fixtures work
     * without it, from TheSportsDB; this page is only for a household that wants to add its own key.
     */
    static PhoneService football(Submit submit) {
        PhoneService s = new PhoneService(FOOTBALL, "API-Football", "More football detail",
            "Optional. Your team's fixtures already come from TheSportsDB with no account. Add your own free API-Football key here only if you want it asked first.",
            new String[]{SCAN, "On your phone, tap Get a free key if you don't have an API-Football account. It opens their sign-up page.",
                "Already signed up? Tap Open your key. On a phone, tap ☰, then Account → My Access, and tap the hidden key to show it. A blank key means a VPN or Private Relay is on: the phone page says what to switch off.",
                "Copy your key, come back to the setup page and paste it. The TV checks it and saves it."},
            "Optional. Your team's fixtures already come from TheSportsDB, with no account. If you add your own free API-Football key, the TV asks API-Football first. It takes about two minutes. One key works for all the API-SPORTS sports.",
            new Step[]{new Step("No API-Football account yet? Tap Get a free key. Their sign-up page opens in a new tab: create the account and confirm your email address.", FOOTBALL_SIGNUP, "Get a free key"),
                new Step("Already have an account? Your key is on your API-Football dashboard.", FOOTBALL_KEY, "Already signed up? Open your key"),
                new Step(FOOTBALL_WHERE), new Step(FOOTBALL_PROXY),
                new Step("Come back to this page, paste the key below and tap Save on the TV.")},
            Collections.singletonList(Field.key("API-Football key", "[A-Za-z0-9]{16,64}", "an API-Football key")),
            "The free plan allows 100 requests a day. Unified TV stays well within that. Scores are never shown.",
            FOOTBALL_SIGNUP, "API-Football sign-up page", "The TV checks the key with API-Football and saves it. Then you choose your team on the TV.",
            "Key saved. Now choose your team.", "Key saved. Now choose your team on the TV.", submit, null);
        s.vpnWords = ApiFootball.VPN_WORDS;
        return s;
    }
    /** TMDB (film posters), with the given validate-and-save. */
    static PhoneService tmdb(Submit submit) {
        return new PhoneService(TMDB, "TMDB", "Film posters",
            "Film posters come from TMDB (The Movie Database), with your own free key.",
            new String[]{SCAN, "On your phone, tap Get a free key if you don't have a TMDB account. It opens their sign-up page.",
                "Ask TMDB for an API key, copy it, come back to the setup page and paste it. The TV checks it and saves it."},
            "Posters and film details come from TMDB (The Movie Database). It's free for personal use.",
            new Step[]{new Step("No TMDB account yet? Tap Get a free key. Their sign-up page opens in a new tab: create the account and confirm your email address.", TMDB_SIGNUP, "Get a free key"),
                new Step("Open your account's API settings and ask for an API key (choose Developer, for personal use).", TMDB_API, "Open TMDB API settings"),
                new Step("Copy the API key, or the longer API read access token, come back to this page, paste it below and tap Save on the TV.")},
            Collections.singletonList(Field.key("TMDB API key or read access token", "(?:[a-f0-9]{32}|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)", "a TMDB key or token")),
            "This product uses the TMDB API but is not endorsed or certified by TMDB. Only film titles are sent to TMDB.",
            TMDB_SIGNUP, "TMDB sign-up page", "The TV checks the key with TMDB and saves it. Posters then appear in your library.",
            "Key saved. Film posters are on.", "Key saved. Film posters will appear in your library.", submit, null);
    }
    /**
     * The household's own assistant relay (Assistant): its address and key, saved encrypted. NOT tested: the TV doesn't
     * send questions to it yet, and the pages say so (AGENTS rule 1).
     */
    static PhoneService assistant(Submit submit) {
        return new PhoneService(ASSISTANT, "your assistant", "Your assistant",
            "For general questions, Ask your TV needs your household's own assistant service: its address and key. This is not tested yet.",
            new String[]{SCAN, "On your phone, paste your assistant service's address and its key.",
                "The TV saves them, encrypted. Answering questions through it is not tested yet."},
            "Ask your TV can open apps and find films by itself. For general questions it needs your household's own assistant service (not tested yet).",
            new Step[]{new Step("Copy the address of your household's assistant service (it starts with https://, or http:// on your home network)."),
                new Step("Copy its key."), new Step("Paste both below and tap Save on the TV.")},
            Arrays.asList(new Field("endpoint", "Assistant address", "https://…", "(?i)https://\\S{3,290}|http://(?:10|192\\.168|172\\.(?:1[6-9]|2\\d|3[01]))\\.[0-9.]+(?::\\d+)?(?:/\\S*)?|http://[A-Za-z0-9-]+\\.local(?::\\d+)?(?:/\\S*)?", "an https:// address (or http:// on your home network)", false, true),
                Field.key("Assistant key", "[A-Za-z0-9._~+/=-]{16,512}", "a key")),
            "Not tested: the TV keeps these details but doesn't send your questions anywhere yet.",
            null, null, "The TV saves the address and key, encrypted. Nothing is sent to your assistant yet.",
            "Saved. Answering questions through it is not tested yet.", "Saved on the TV. Answering questions through it is not tested yet.", submit, null);
    }
}
