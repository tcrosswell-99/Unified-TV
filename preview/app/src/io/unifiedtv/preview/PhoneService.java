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
    static final String HA = "ha", FOOTBALL = "football", TMDB = "tmdb", BACKUP = "backup";
    static final String[] IDS = {HA, FOOTBALL, TMDB, BACKUP};
    static final String FOOTBALL_SIGNUP = "https://dashboard.api-football.com/register", TMDB_SIGNUP = "https://www.themoviedb.org/signup", TMDB_API = "https://www.themoviedb.org/settings/api";

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

    final String id, name, title, tvIntro, phoneIntro, note, signup, signupLabel, tvDone, phoneDone;
    final Step[] steps; final List<Field> fields; final Submit submit; final Custom custom;

    PhoneService(String id, String name, String title, String tvIntro, String phoneIntro, Step[] steps, List<Field> fields, String note,
                 String signup, String signupLabel, String tvDone, String phoneDone, Submit submit, Custom custom) {
        this.id = id; this.name = name; this.title = title; this.tvIntro = tvIntro; this.phoneIntro = phoneIntro; this.steps = steps; this.fields = fields; this.note = note;
        this.signup = signup; this.signupLabel = signupLabel; this.tvDone = tvDone; this.phoneDone = phoneDone; this.submit = submit; this.custom = custom;
    }

    /** The services this TV can set up by phone; null for an unknown id. {@code app} is the application context. */
    static PhoneService of(Context app, String id) {
        switch (id == null ? "" : id) {
            case HA: return new PhoneService(HA, "Home Assistant", "Connect Home Assistant",
                "Your phone finds Home Assistant and signs you in there. Nothing to type with the remote.", "", new Step[0], Collections.emptyList(), "",
                null, null, "Connected to Home Assistant.", "The TV is connected to Home Assistant.", null,
                (c, progress, background) -> {
                    Future<String> found = background.submit(() -> PhoneSetup.found(c));
                    return new PhoneFlows.Ha(() -> { try { return found.get(9, TimeUnit.SECONDS); } catch (Exception e) { return null; } },
                        new HaSignIn(c), (base, token, consent, how) -> PhoneSetup.saveHa(c, base, token, consent, how), progress, new java.security.SecureRandom(), PhoneSetup.clientName(c));
                });
            case FOOTBALL: return football(values -> {
                String key = values.get("key");
                try { int[] account = ApiFootball.account(ApiFootball.get(key, ApiFootball.STATUS)); FixtureFeed.saveKey(app, key); FixtureFeed.noteUsed(app, account[0]); TeamList.ensure(app); return PhoneFlows.Verdict.OK; }
                catch (Exception e) { return ApiFootball.verdict(e); }
            });
            case TMDB: return tmdb(values -> {
                String key = values.get("key"); PhoneFlows.Verdict v = TmdbKey.check(TmdbKey.BASE, key);
                if (v == PhoneFlows.Verdict.OK) TmdbKey.save(app, key);
                return v;
            });
            // SET-081: not a service sign-in, but the same phone page: the settings file goes to and from the phone.
            case BACKUP: return new PhoneService(BACKUP, "Settings backup", "Settings backup",
                "Your phone can save a copy of this TV's settings, or send a copy back. Passwords, keys, the PIN and camera pictures are never in it.", "", new Step[0], Collections.emptyList(), "",
                null, null, "The settings file arrived. Check what would change below.", "Sent to the TV.", null,
                (c, progress, background) -> new PhoneFlows.Backup(() -> SettingsBackup.export(c.getSharedPreferences(HouseholdSettings.PREFS, 0).getAll(), java.time.LocalDate.now().toString()),
                    SettingsBackup::offer, progress));
            default: return null;
        }
    }

    /** API-Football (football fixtures), with the given validate-and-save. */
    static PhoneService football(Submit submit) {
        return new PhoneService(FOOTBALL, "API-Football", "Football fixtures",
            "Your team's fixtures come from API-Football, with your own free key. Your phone shows how to get one, then you paste it there.",
            "Your team's fixtures come from API-Football. You need your own free key. It takes about two minutes.",
            new Step[]{new Step("Create a free API-Football account.", FOOTBALL_SIGNUP, "Open the API-Football sign-up page"),
                new Step("Confirm your email address, then sign in to your API-Football dashboard."),
                new Step("Copy the API key shown on your dashboard and paste it below.")},
            Collections.singletonList(Field.key("API-Football key", "[A-Za-z0-9]{16,64}", "an API-Football key")),
            "The free plan allows 100 requests a day. Unified TV stays well within that. Scores are never shown.",
            FOOTBALL_SIGNUP, "API-Football sign-up page", "Key saved. Now choose your team.", "Key saved. Now choose your team on the TV.", submit, null);
    }
    /** TMDB (film posters), with the given validate-and-save. */
    static PhoneService tmdb(Submit submit) {
        return new PhoneService(TMDB, "TMDB", "Film posters",
            "Film posters come from TMDB, with your own free key. Your phone shows how to get one, then you paste it there.",
            "Posters and film details come from TMDB (The Movie Database). It's free for personal use.",
            new Step[]{new Step("Create a free TMDB account.", TMDB_SIGNUP, "Open the TMDB sign-up page"),
                new Step("Open your account's API settings and ask for an API key (choose Developer, for personal use).", TMDB_API, "Open TMDB API settings"),
                new Step("Copy the API key, or the longer API read access token, and paste it below.")},
            Collections.singletonList(Field.key("TMDB API key or read access token", "(?:[a-f0-9]{32}|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)", "a TMDB key or token")),
            "This product uses the TMDB API but is not endorsed or certified by TMDB. Only film titles are sent to TMDB.",
            TMDB_SIGNUP, "TMDB sign-up page", "Key saved. Film posters are on.", "Key saved. Film posters will appear in your library.", submit, null);
    }
}
