package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/**
 * Settings pages for setting things up (owner feedback, 25 October 2026): Smart Home with Home Assistant status first in
 * one plain line and one action, "Set up on your phone"; football with the key added on the phone and the team picked
 * from a list; film posters; and phone setup itself (QR code, short address, 6-digit code, live progress). Everything is
 * drawn inside the Settings glass sheet with its own rows; nothing here opens a full-screen page. Technical details
 * (address, connection type, pasting a token on the TV) live only under Advanced.
 */
final class SetupPages {
    private final SettingsActivity a;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** This visit's look at Home Assistant: null while checking or looking. */
    private String haChecked; private Boolean haFound; private boolean haChecking, haLooking;
    private TextView haLine, phoneStatus, teamProgress;
    final HouseholdPage household;
    private PhoneSetup phone;
    private boolean signupCode;
    /** Team picker inside its task: a league opened, or the letters searched; Back clears them first. */
    private int teamLeague; private String teamQuery, teamProblem; private int teamReady = -1;
    private final TeamList.Listener teamListener;

    SetupPages(SettingsActivity activity) {
        a = activity; household = new HouseholdPage(activity);
        teamListener = (cat, problem) -> a.runOnUiThread(() -> teamsChanged(cat, problem));
        TeamList.listen(teamListener);
    }

    /** The tasks drawn here, the category each belongs under, and their headers. */
    static boolean owns(String task) {
        return task != null && (task.startsWith("phone-") || task.equals(SettingsActivity.HA_ADVANCED) || task.equals(SettingsActivity.TEAM) || task.equals(SettingsActivity.HOUSEHOLD));
    }
    static int category(String task, int current) {
        if (SettingsActivity.PHONE_TMDB.equals(task)) return SettingsActivity.LIBRARY;
        if ((SettingsActivity.HOUSEHOLD.equals(task) || SettingsActivity.PHONE_HA.equals(task)) && current == SettingsActivity.NOTIFICATIONS) return current;
        return SettingsActivity.SMART_HOME;
    }
    static String title(String task) {
        switch (task) {
            case SettingsActivity.PHONE_HA: return "Home Assistant";
            case SettingsActivity.PHONE_FOOTBALL: return "Football fixtures";
            case SettingsActivity.PHONE_TMDB: return "Film posters";
            case SettingsActivity.HA_ADVANCED: return "Home Assistant: Advanced";
            case SettingsActivity.TEAM: return "Your team";
            case SettingsActivity.HOUSEHOLD: return "Doorbell, alarms & this TV";
            default: { PhoneService s = task.startsWith("phone-") ? PhoneService.of(null, task.substring(6)) : null; return s == null ? "Set up" : s.title; }
        }
    }
    static String subtitle(String task) {
        switch (task) {
            case SettingsActivity.HA_ADVANCED: return "Address and sign-in details";
            case SettingsActivity.TEAM: return "Choose from the UK teams";
            case SettingsActivity.HOUSEHOLD: return "What Home Assistant shows on this TV";
            default: return "Set up on your phone";
        }
    }

    /** Called on every render: a phone session lives only while its own page is showing. */
    void showing(String task) {
        if (phone != null && !(task != null && task.equals("phone-" + phone.kind))) { phone.stop(); phone = null; signupCode = false; }
        if (!SettingsActivity.TEAM.equals(task)) { teamLeague = 0; teamQuery = null; }
        if (!SettingsActivity.HOUSEHOLD.equals(task)) household.reset();
    }
    /** A detail opened from the root: look again this visit. */
    void entered(int category) { if (category == SettingsActivity.SMART_HOME) { haChecked = null; haFound = null; } }
    /** Back inside a task's own sub-page (a league, a search); true when handled. */
    boolean back(String task) {
        if (SettingsActivity.TEAM.equals(task) && (teamLeague != 0 || teamQuery != null)) { teamLeague = 0; teamQuery = null; a.redraw(); return true; }
        return false;
    }
    /** The screen left (Home pressed, another app): an unfinished phone session ends now. */
    void paused() { if (phone != null && !phone.succeeded()) { phone.stop(); phone = null; signupCode = false; } }
    void close() { if (phone != null) phone.stop(); phone = null; TeamList.unlisten(teamListener); worker.shutdownNow(); household.close(); }

    void task(String task) {
        switch (task) {
            case SettingsActivity.HA_ADVANCED: haAdvanced(); break;
            case SettingsActivity.TEAM: teams(); break;
            case SettingsActivity.HOUSEHOLD: household.build(); break;
            default: if (task.startsWith("phone-")) phonePage(task.substring(6)); break;
        }
    }

    // ------------------------------------------------------------------ Smart Home
    static boolean haSaved(android.content.Context c) {
        try { JSONObject ha = new Vault(c).read("ha"); return ha.optBoolean("enabled") && !ha.optString("token").isEmpty(); } catch (Exception e) { return false; }
    }

    void smartHome() {
        boolean saved = haSaved(a), kids = Profiles.kids(a);
        a.section("Home Assistant");
        haLine = a.text(HaStatus.line(saved, haChecked, haFound), 19); haLine.setFocusable(false);
        look(saved);
        boolean connected = saved && HaStatus.AUTHORISED.equals(haChecked), signIn = saved && HaStatus.AUTH_REQUIRED.equals(haChecked);
        if (kids) a.text("An adult can set this up in Adults.", 15);
        else {
            if (!saved) { a.button("Set up on your phone", () -> EditLock.ask(a, () -> a.expandTask(SettingsActivity.PHONE_HA))); a.text("Brings doorbell, alarm and room features to this TV. You sign in on your phone; nothing to type here.", 15); }
            if (signIn) a.button("Sign in again on your phone", () -> EditLock.ask(a, () -> a.expandTask(SettingsActivity.PHONE_HA)));
            if (saved) a.button("Doorbell, alarms & this TV", () -> EditLock.ask(a, () -> a.expandTask(SettingsActivity.HOUSEHOLD)));
            if (saved) a.button("Disconnect", this::disconnect);
            Button advanced = a.button("Advanced", () -> EditLock.ask(a, () -> a.expandTask(SettingsActivity.HA_ADVANCED)));
            a.chevron(advanced);
        }
        if (connected) a.text("Home Assistant accepted this TV just now.", 15);
        football(kids);
    }

    /** One check (or one look on the network) per visit, off the main thread; the page redraws in place with the result. */
    private void look(boolean saved) {
        android.content.Context app = a.getApplicationContext();
        if (saved && haChecked == null && !haChecking) {
            haChecking = true;
            worker.execute(() -> {
                String result;
                try { JSONObject ha = new Vault(app).read("ha"); result = HaStatus.before(ha); if (result == null) { Exception error = null; try { LocalApi.get(ha, "/api/"); } catch (Exception e) { error = e; } result = HaStatus.after(error); } }
                catch (Exception e) { result = HaStatus.NOT_CONFIGURED; }
                String r = result;
                a.runOnUiThread(() -> { haChecking = false; haChecked = r; if (a.on(SettingsActivity.SMART_HOME, null)) a.refresh(); });
            });
        }
        if ((!saved || HaStatus.NOT_CONFIGURED.equals(haChecked)) && haFound == null && !haLooking) {
            List<LocalDiscovery.Found> ready = LocalDiscovery.fresh() ? LocalDiscovery.haFound : null;
            if (ready != null) { haFound = !ready.isEmpty(); if (haLine != null) haLine.setText(HaStatus.line(saved, haChecked, haFound)); return; }
            haLooking = true;
            worker.execute(() -> {
                boolean any;
                try { String s = new Vault(app).read("ha").optString("base"); any = !s.isEmpty() || !LocalDiscovery.homeAssistant(app, false).isEmpty(); } catch (Exception e) { any = false; }
                boolean f = any;
                a.runOnUiThread(() -> { haLooking = false; haFound = f; if (haLine != null && a.on(SettingsActivity.SMART_HOME, null)) haLine.setText(HaStatus.line(haSaved(a), haChecked, haFound)); });
            });
        }
    }

    private void disconnect() {
        EditLock.ask(a, () -> new AlertDialog.Builder(a).setTitle("Disconnect Home Assistant?")
            .setMessage("This TV forgets its Home Assistant sign-in. Doorbell and alarm alerts stop on this TV until it is set up again. Nothing changes in Home Assistant itself; you can also remove the TV there.")
            .setPositiveButton("Disconnect", (d, w) -> {
                try { a.stopService(new android.content.Intent(a, HouseholdService.class)); } catch (RuntimeException ignored) {}
                new Vault(a).forget("ha"); haChecked = null; haFound = null; a.note("Home Assistant disconnected."); a.refresh();
            }).setNegativeButton("Cancel", null).show());
    }

    // ------------------------------------------------------------------ Advanced (Home Assistant)
    private void haAdvanced() {
        JSONObject ha; try { ha = new Vault(a).read("ha"); } catch (Exception e) { ha = new JSONObject(); }
        String base = ha.optString("base");
        a.text("Address: " + (base.isEmpty() ? "not set (phone setup looks for it)" : base), 17);
        if (!base.isEmpty()) a.text(base.startsWith("https:") ? "Encrypted connection." : ha.optBoolean("httpConsent") ? "Unencrypted connection on your home network: allowed." : "Unencrypted connection on your home network: not allowed yet.", 15);
        if (haSaved(a)) a.text("signin".equals(ha.optString("how")) ? "Signed in with Home Assistant. This TV appears in your Home Assistant profile, under Security." : "Using a long-lived token.", 15);
        a.button("Set up again on your phone", () -> a.expandTask(SettingsActivity.PHONE_HA));
        a.button("Change the address", this::typeAddress);
        a.button("Paste a token on this TV", this::typeToken);
        a.button("Look for Home Assistant again", () -> { haFound = null; LocalDiscovery.haFound = null; a.note("Looking on your home network…"); worker.execute(() -> {
            List<LocalDiscovery.Found> f = LocalDiscovery.homeAssistant(a.getApplicationContext(), true);
            a.runOnUiThread(() -> a.note(f.isEmpty() ? "Home Assistant wasn't found. Check it's on and on the same home network as this TV." : "Found Home Assistant. Choose Set up again on your phone."));
            if (!f.isEmpty()) LocalDiscovery.haFound = f; }); });
        a.text("Phone setup is easier: it finds Home Assistant and signs in for you.", 15);
    }

    /** One plain question before plain HTTP is used for an address (recorded as the connection's httpConsent). */
    private void consent(String base, Runnable allowed) {
        if (!base.startsWith("http:")) { allowed.run(); return; }
        new AlertDialog.Builder(a).setTitle("Allow an unencrypted connection?").setMessage("Your Home Assistant uses an unencrypted connection on your home network. Allow it?")
            .setPositiveButton("Allow", (d, w) -> allowed.run()).setNegativeButton("Not now", null).show();
    }
    private EditText field(String hint, boolean secret) {
        EditText f = new EditText(a); f.setSingleLine(true); f.setHint(hint); f.setSaveEnabled(false); f.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        f.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI);
        return f;
    }
    private void typeAddress() {
        EditText f = field("homeassistant.local:8123", false);
        new AlertDialog.Builder(a).setTitle("Home Assistant address").setMessage("As you'd type it in a browser.").setView(f).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> {
                String base = PhoneFlows.haBase(f.getText().toString());
                if (base == null) { a.note("That address doesn't look right. Nothing was changed."); return; }
                consent(base, () -> {
                    try { JSONObject ha; try { ha = new Vault(a).read("ha"); } catch (Exception e) { ha = new JSONObject(); }
                        boolean token = !ha.optString("token").isEmpty();
                        ha.put("base", base).put("kind", "ha").put("httpConsent", base.startsWith("http:")).put("enabled", token);
                        new Vault(a).save("ha", ha); if (token) PhoneSetup.restartAlerts(a);
                        haChecked = null; a.note("Address saved."); a.refresh();
                    } catch (Exception e) { a.note("Couldn't save on this TV. Nothing was changed."); }
                });
            }).show();
    }
    private void typeToken() {
        String saved; try { saved = new Vault(a).read("ha").optString("base"); } catch (Exception e) { saved = ""; }
        String base = !saved.isEmpty() ? saved : LocalDiscovery.haFound != null && !LocalDiscovery.haFound.isEmpty() ? LocalDiscovery.haFound.get(0).base : null;
        if (base == null) { a.note("Set the address first, or use phone setup."); return; }
        EditText f = field("Long-lived access token", true);
        new AlertDialog.Builder(a).setTitle("Paste a token").setMessage("Create a long-lived access token in your Home Assistant profile, under Security.").setView(f).setNegativeButton("Cancel", null)
            .setPositiveButton("Check and save", (d, w) -> {
                String token = f.getText().toString().replaceAll("\\s", ""); f.setText("");
                if (!token.matches("[A-Za-z0-9._-]{20,4096}")) { a.note("That doesn't look like a Home Assistant token. Nothing was saved."); return; }
                consent(base, () -> { a.note("Checking with Home Assistant…"); android.content.Context app = a.getApplicationContext(); worker.execute(() -> {
                    int status; try { status = new HaSignIn(app).check(base, token); } catch (Exception e) { status = -1; }
                    String words;
                    if (status == 200) { try { PhoneSetup.saveHa(app, base, token, base.startsWith("http:"), "token"); words = "Connected to Home Assistant."; } catch (Exception e) { words = "Couldn't save on this TV. Nothing was changed."; } }
                    else words = status == 401 || status == 403 ? "Home Assistant didn't accept that token. Nothing was saved." : "Home Assistant couldn't be reached. Nothing was saved.";
                    String w2 = words; a.runOnUiThread(() -> { haChecked = null; a.note(w2); a.refresh(); });
                }); });
            }).show();
    }

    // ------------------------------------------------------------------ Phone setup
    private void phonePage(String kind) {
        if (Profiles.kids(a)) { a.text("An adult can set this up in Adults.", 16); return; }
        if (PhoneService.of(a.getApplicationContext(), kind) == null) { a.text("This service can't be set up on this TV.", 16); return; }
        if (phone == null) phone = PhoneSetup.start(a, kind, s -> phoneChanged(s));
        PhoneService service = phone.service;
        a.text(service.tvIntro, 16);
        boolean live = !phone.over() && phone.link() != null;
        if (live) {
            boolean signup = signupCode && service.signup != null;
            FrameLayout holder = new FrameLayout(a); QrView qr = new QrView(a); int side = a.px(330);
            qr.setText(signup ? service.signup : phone.link(), signup ? "Code to scan for the " + service.signupLabel : "Code to scan with your phone");
            holder.addView(qr, new FrameLayout.LayoutParams(side, side, Gravity.CENTER));
            a.column.addView(holder, new LinearLayout.LayoutParams(-1, side + a.px(12)));
            if (signup) a.text("Scan to open the " + service.signupLabel + ". When you have your key, show the setup code again.", 16);
            else {
                a.text("Scan with your phone's camera. Or open this address on your phone:", 16);
                TextView address = a.text(phone.origin().replaceFirst("^http://", ""), 19); address.setTypeface(Typeface.MONOSPACE);
                a.text("and enter the code:", 16);
                TextView code = a.text(spaced(phone.code()), 24); code.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)); code.setLetterSpacing(.25f);
            }
        }
        phoneStatus = a.text(phone.status(), 19); phoneStatus.setFocusable(false);
        if (phone.succeeded()) {
            if (PhoneSetup.FOOTBALL.equals(kind)) a.button("Choose your team", () -> a.expandTask(SettingsActivity.TEAM));
            a.button("Done", a::back);
            return;
        }
        if (live && service.signup != null) a.button(signupCode ? "Show the setup code" : "No account yet? Show the sign-up page code", () -> { signupCode = !signupCode; a.refresh(); });
        if (phone.over()) a.button("Start again", () -> { if (phone != null) phone.stop(); phone = null; a.redraw(); });
        else a.button("Cancel", a::back);
        if (PhoneSetup.TMDB.equals(kind) && TmdbKey.saved(a)) a.button("Remove the film poster key", () -> new AlertDialog.Builder(a).setTitle("Remove the film poster key?")
            .setMessage("Film posters and details stop updating. Posters already shown may stay until the library refreshes.").setPositiveButton("Remove", (d, w) -> { TmdbKey.forget(a); a.note("Film poster key removed."); a.refresh(); }).setNegativeButton("Cancel", null).show());
        if (live) a.text("Your phone needs to be on the same home network as this TV. This code works for 10 minutes, once.", 15);
    }
    static String spaced(String code) { return code == null || code.length() != 6 ? String.valueOf(code) : code.substring(0, 3) + " " + code.substring(3); }

    private void phoneChanged(PhoneSetup s) {
        if (s != phone) return;
        if (s.over()) { if (s.succeeded() && PhoneSetup.HA.equals(s.kind)) { haChecked = null; haFound = null; } a.refresh(); return; }
        if (phoneStatus != null) phoneStatus.setText(s.status());
    }

    // ------------------------------------------------------------------ Football
    private void football(boolean kids) {
        a.section("Football");
        boolean api = FixtureFeed.apiConfigured(a); Fixtures.Team team = FixtureFeed.team(a);
        if (!api) {
            a.text("Football fixtures: not set up", 19).setFocusable(false);
            if (!kids) { a.button("Set up on your phone", () -> EditLock.ask(a, () -> a.expandTask(SettingsActivity.PHONE_FOOTBALL))); a.text("You'll need a free API-Football key. Your phone shows how to get one.", 15); }
        } else {
            ApiFootball.Budget b = FixtureFeed.budget(a);
            a.text("Football fixtures: ready · " + b.used + " of " + ApiFootball.DAILY_LIMIT + " requests used today", 19).setFocusable(false);
        }
        boolean calendar = false; try { calendar = !new Vault(a).read("household").optString("calendar").isEmpty(); } catch (Exception ignored) {}
        if (api) a.button("Your team: " + (team == null ? "choose from a list" : team.displayName), () -> EditLock.ask(a, () -> a.expandTask(SettingsActivity.TEAM)));
        else if (calendar || team != null) a.button("Your team: " + (team == null ? "none" : team.displayName), () -> EditLock.ask(a, this::calendarTeam));
        if (team != null) {
            a.button("Badge: " + team.shortName + " on " + Fixtures.colourName(team.primary) + " and " + Fixtures.colourName(team.secondary), () -> badgeColours(team));
            if (!team.id.startsWith("api:")) a.button("Other names in the calendar: " + (team.aliases.isEmpty() ? "none" : String.join(", ", team.aliases)), () -> aliases(team));
            a.button("No team", () -> { FixtureFeed.saveTeam(a, null); a.refresh(); });
        }
        a.text("On matchday, Home shows the match and the kick-off time, or \"Live\". Scores are never shown. The badge is drawn from your team's short name and colours; no club crest is used.", 15);
        if (api && !kids) a.button("Remove the football key", () -> EditLock.ask(a, () -> new AlertDialog.Builder(a).setTitle("Remove the football key?").setMessage("The key and the fixtures kept on this TV are deleted.")
            .setPositiveButton("Remove", (d, w) -> { FixtureFeed.forgetKey(a); a.refresh(); }).setNegativeButton("Cancel", null).show()));
    }
    /** Without API-Football: the name of the team as the household's fixtures calendar writes it. */
    private void calendarTeam() {
        Fixtures.Team t = FixtureFeed.team(a); EditText f = field("Your team's name, as you'd like it shown", false); if (t != null) f.setText(t.displayName);
        new AlertDialog.Builder(a).setTitle("Your team").setMessage("Used with the fixtures calendar chosen in Doorbell, alarms & this TV.").setView(f).setPositiveButton("Save", (d, w) -> { String name = f.getText().toString().trim();
            FixtureFeed.saveTeam(a, name.isEmpty() ? null : new Fixtures.Team("calendar", name, "", t == null ? Fixtures.COLOURS[0] : t.primary, t == null ? Fixtures.COLOURS[1] : t.secondary, Fixtures.Sport.FOOTBALL, t == null ? null : t.aliases)); a.refresh(); })
            .setNegativeButton("Cancel", null).show();
    }
    private void badgeColours(Fixtures.Team t) {
        new AlertDialog.Builder(a).setTitle("Badge: main colour").setItems(Fixtures.COLOUR_NAMES, (d, x) -> new AlertDialog.Builder(a).setTitle("Badge: second colour").setItems(Fixtures.COLOUR_NAMES, (d2, y) -> {
            FixtureFeed.saveTeam(a, new Fixtures.Team(t.id, t.displayName, t.shortName, Fixtures.COLOURS[x], Fixtures.COLOURS[y], t.sport, t.aliases)); a.refresh(); }).show()).show();
    }
    private void aliases(Fixtures.Team t) {
        EditText f = field("Other names, separated by commas", false); f.setText(String.join(", ", t.aliases));
        new AlertDialog.Builder(a).setTitle("Other names in the calendar").setView(f).setPositiveButton("Save", (d, w) -> { List<String> names = new ArrayList<>(); for (String x : f.getText().toString().split(",")) if (!x.trim().isEmpty()) names.add(x.trim());
            FixtureFeed.saveTeam(a, new Fixtures.Team(t.id, t.displayName, t.shortName, t.primary, t.secondary, t.sport, names)); a.refresh(); }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ Team picker (a task in the sheet)
    private void teams() {
        if (!FixtureFeed.apiConfigured(a)) { a.text("Set up football fixtures first.", 16); a.button("Set up on your phone", () -> a.expandTask(SettingsActivity.PHONE_FOOTBALL)); return; }
        TeamList.ensure(a);
        JSONObject cat = FixtureFeed.catalogue(a); List<JSONObject> leagues = TeamList.leagues(cat); teamReady = leagues.size();
        Fixtures.Team current = FixtureFeed.team(a);
        if (current != null) a.text("Now: " + current.displayName, 17).setFocusable(false);
        teamProgress = a.text(progress(cat), 15);
        if (teamLeague != 0) {
            JSONObject league = null; for (JSONObject l : leagues) if (l.optInt("id") == teamLeague) league = l;
            if (league == null) { teamLeague = 0; } else {
                a.group(league.optString("name"));
                for (TeamList.Entry e : TeamList.teams(league)) a.button(e.name, () -> pick(e));
                return;
            }
        }
        a.button(teamQuery == null ? "Search by name" : "Search: " + teamQuery, this::search);
        if (teamQuery != null) {
            List<TeamList.Entry> found = TeamList.search(cat, teamQuery, 30);
            if (found.isEmpty()) a.text(leagues.isEmpty() ? "The team list is still being read." : "No team matches \"" + teamQuery + "\".", 16);
            for (TeamList.Entry e : found) a.button(e.name + " · " + e.league, () -> pick(e));
            a.button("Show all leagues", () -> { teamQuery = null; a.redraw(); });
            return;
        }
        for (String country : ApiFootball.COUNTRIES) {
            boolean header = false;
            for (JSONObject l : leagues) {
                if (!country.equalsIgnoreCase(l.optString("country").replace(' ', '-'))) continue;
                if (!header) { a.group(country.replace('-', ' ')); header = true; }
                int id = l.optInt("id"), n = l.optJSONArray("teams").length();
                a.button(l.optString("name") + " · " + n + " teams", () -> { teamLeague = id; a.redraw(); });
            }
        }
    }
    private String progress(JSONObject cat) {
        if (teamProblem != null) return teamProblem;
        if (cat.optLong("fetchedAt") > 0) return "All UK leagues are ready.";
        int[] p = ApiFootball.progress(cat);
        return p[1] == 0 ? "Reading the list of UK teams. This takes a few minutes the first time and is kept for a week." : "Reading the list of UK teams: " + p[0] + " of " + p[1] + " leagues so far.";
    }
    private void teamsChanged(JSONObject cat, String problem) {
        teamProblem = problem;
        if (!a.on(-1, SettingsActivity.TEAM)) return;
        if (TeamList.leagues(cat).size() != teamReady && teamLeague == 0 && teamQuery == null) a.refresh();
        else if (teamProgress != null) teamProgress.setText(progress(cat));
    }
    private void search() {
        EditText f = field("A few letters, for example spurs or hearts", false); if (teamQuery != null) f.setText(teamQuery);
        new AlertDialog.Builder(a).setTitle("Search for your team").setView(f).setNegativeButton("Cancel", null)
            .setPositiveButton("Search", (d, w) -> { String q = f.getText().toString().trim(); teamQuery = q.isEmpty() ? null : q; a.redraw(); }).show();
    }
    private void pick(TeamList.Entry e) {
        Fixtures.Team old = FixtureFeed.team(a);
        FixtureFeed.saveTeam(a, new Fixtures.Team("api:" + e.id, e.name, e.code, old == null ? Fixtures.COLOURS[0] : old.primary, old == null ? Fixtures.COLOURS[1] : old.secondary, Fixtures.Sport.FOOTBALL, null));
        teamLeague = 0; teamQuery = null;
        a.note("Your team: " + e.name + ". You can change the badge colours in Smart Home.");
        a.back();
    }
}
