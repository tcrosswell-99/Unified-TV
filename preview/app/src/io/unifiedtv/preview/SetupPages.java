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
 * one plain line and one action, "Set up on your phone"; football with the team picked from a list (TheSportsDB, no
 * account) and an optional API-Football key added on the phone; film posters; and phone setup itself (QR code, short address, 6-digit code, live progress). Everything is
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
    /** The Home Assistant phone setup found, as the household knows it (PhoneSetup.haName); null until looked. */
    private String haShown; private boolean haNaming;
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
        if (SettingsActivity.PHONE_BACKUP.equals(task)) return SettingsActivity.PRIVACY;
        if ((SettingsActivity.HOUSEHOLD.equals(task) || SettingsActivity.PHONE_HA.equals(task)) && current == SettingsActivity.NOTIFICATIONS) return current;
        return SettingsActivity.SMART_HOME;
    }
    static String title(String task) {
        switch (task) {
            case SettingsActivity.PHONE_HA: return "Home Assistant";
            case SettingsActivity.PHONE_FOOTBALL: return "More football detail";
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
            case SettingsActivity.PHONE_BACKUP: return "Using your phone";
            default: return "Set up on your phone";
        }
    }

    /** Called on every render: a phone session lives only while its own page is showing. */
    void showing(String task) {
        if (phone != null && !(task != null && task.equals("phone-" + phone.kind))) { phone.stop(); phone = null; }
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
    void paused() { if (phone != null && !phone.succeeded()) { phone.stop(); phone = null; } }
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
                new Vault(a).forget("ha"); EventLog.record(a, EventLog.CONNECTION, "Home Assistant disconnected"); haChecked = null; haFound = null; a.note("Home Assistant disconnected."); a.refresh();
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
    /**
     * One phone setup screen (owner, 25 October 2026: "couldn't tell what to do or what each QR opens"): numbered plain
     * steps, then ONE QR code that always opens this TV's setup page (it is never swapped for a sign-up page; the phone
     * page links there), "Opens a setup page on this TV" under it, and the typed address with the 6-digit code only as a
     * small fallback. One session per screen: it lives while this page shows.
     */
    private void phonePage(String kind) {
        if (Profiles.kids(a)) { a.text("An adult can set this up in Adults.", 16); return; }
        if (PhoneService.of(a.getApplicationContext(), kind) == null) { a.text("This service can't be set up on this TV.", 16); return; }
        if (phone == null) { if (PhoneService.BACKUP.equals(kind)) SettingsBackup.discard(); phone = PhoneSetup.start(a, kind, s -> phoneChanged(s)); haShown = null; }
        PhoneService service = phone.service;
        a.text(service.tvIntro, 16);
        if (PhoneService.HA.equals(kind) && !phone.succeeded()) haLine(a.text(haShown == null ? "Looking for Home Assistant on your home network…" : foundWords(haShown), 17));
        boolean live = !phone.over() && phone.link() != null;
        if (live) {
            String[] steps = steps(service);
            for (int i = 0; i < steps.length; i++) a.text(steps[i], 17);
            FrameLayout holder = new FrameLayout(a); QrView qr = new QrView(a); int side = a.px(300);
            qr.setText(phone.link(), QR_LABEL);
            holder.addView(qr, new FrameLayout.LayoutParams(side, side, Gravity.CENTER));
            a.column.addView(holder, new LinearLayout.LayoutParams(-1, side + a.px(12)));
            TextView under = a.text(OPENS, 17); under.setGravity(Gravity.CENTER_HORIZONTAL);
            a.text(fallback(phone.origin(), phone.code()), 14).setTypeface(Typeface.MONOSPACE);
            // Owner's TCL, 25 October 2026: with NordVPN connected, the phone may not reach the TV at all.
            if (VpnGate.tunnel(a)) a.text(VPN_HINT, 15);
        }
        phoneStatus = a.text(phone.status(), 19); phoneStatus.setFocusable(false);
        if (phone.succeeded()) {
            // SET-081: the phone sent a settings file; the TV shows what would change, and only an adult applies it.
            if (PhoneService.BACKUP.equals(kind)) { a.privacy().incoming(); return; }
            if (PhoneSetup.FOOTBALL.equals(kind)) a.button("Choose your team", () -> a.expandTask(SettingsActivity.TEAM));
            a.button("Done", a::back);
            return;
        }
        if (phone.over()) a.button("Start again", () -> { if (phone != null) phone.stop(); phone = null; a.redraw(); });
        else a.button("Cancel", a::back);
        if (PhoneSetup.TMDB.equals(kind) && TmdbKey.saved(a)) a.button("Remove the film poster key", () -> new AlertDialog.Builder(a).setTitle("Remove the film poster key?")
            .setMessage("Film posters and details stop updating. Posters already shown may stay until the library refreshes.").setPositiveButton("Remove", (d, w) -> { TmdbKey.forget(a); a.note("Film poster key removed."); a.refresh(); }).setNegativeButton("Cancel", null).show());
        if (PhoneService.ASSISTANT.equals(kind) && Assistant.configured(a)) a.button("Remove the assistant details", () -> new AlertDialog.Builder(a).setTitle("Remove the assistant details?")
            .setMessage("Ask your TV keeps opening apps and finding films.").setPositiveButton("Remove", (d, w) -> { Assistant.forget(a); a.note("Assistant details removed."); a.refresh(); }).setNegativeButton("Cancel", null).show());
        if (live) a.text("Your phone needs to be on the same home network as this TV. This code works for 10 minutes, once.", 15);
    }
    static final String QR_LABEL = "Code to scan with your phone: opens a setup page on this TV", OPENS = "Opens a setup page on this TV",
        VPN_HINT = "If your phone can't open the page, NordVPN may be blocking your home network. Allow local network devices in NordVPN, or disconnect it for a moment.";
    /** The TV's numbered steps for a service. */
    static String[] steps(PhoneService service) {
        String[] out = new String[service.tvSteps.length];
        for (int i = 0; i < out.length; i++) out[i] = (i + 1) + ". " + service.tvSteps[i];
        return out;
    }
    /** The small typed fallback; the address is the TV's own, so it is introduced as such (it is not a mistake). */
    static String fallback(String origin, String code) {
        return "No camera? On your phone, open this TV's address " + (origin == null ? "" : origin.replaceFirst("^http://", "")) + " and enter the code " + spaced(code) + ".";
    }
    static String foundWords(String name) { return name.isEmpty() ? "Home Assistant wasn't found yet. Your phone can enter its address." : "We found Home Assistant at " + name + ". Tap Sign in on your phone."; }
    /** Looks up, once per session and off the main thread, which Home Assistant the phone page will offer, by name. */
    private void haLine(TextView line) {
        if (haShown != null || haNaming) return;
        haNaming = true; android.content.Context app = a.getApplicationContext();
        worker.execute(() -> {
            String base = PhoneSetup.found(app), name = base == null ? "" : PhoneSetup.haName(base);
            a.runOnUiThread(() -> { haNaming = false; haShown = name == null ? "" : name; if (line.isAttachedToWindow()) line.setText(foundWords(haShown)); });
        });
    }
    static String spaced(String code) { return code == null || code.length() != 6 ? String.valueOf(code) : code.substring(0, 3) + " " + code.substring(3); }

    private void phoneChanged(PhoneSetup s) {
        if (s != phone) return;
        if (s.over()) { if (s.succeeded() && PhoneSetup.HA.equals(s.kind)) { haChecked = null; haFound = null; } a.refresh(); return; }
        if (phoneStatus != null) phoneStatus.setText(s.status());
    }

    // ------------------------------------------------------------------ Football
    static final String MORE_DETAIL = "More detail (optional)", ADD_KEY = "Add your own API-Football key on your phone",
        NO_ACCOUNT = "Fixtures come from TheSportsDB, free and with no account, key or phone step.";
    /**
     * Owner decision OD-22: choosing a team is all it takes (TheSportsDB). API-Football is an optional extra below, with
     * the household's own key added on a phone (OD-17); a calendar in Home Assistant stays the other alternative.
     */
    private void football(boolean kids) {
        a.section("Football");
        boolean api = FixtureFeed.apiConfigured(a); Fixtures.Team team = FixtureFeed.team(a);
        boolean listed = team != null && team.listed();
        boolean calendar = false; try { calendar = !new Vault(a).read("household").optString("calendar").isEmpty(); } catch (Exception ignored) {}
        a.text(team == null ? "Football fixtures: choose your team" : listed ? "Football fixtures: " + team.displayName : "Football fixtures: " + team.displayName + ", from your calendar", 19).setFocusable(false);
        a.chevron(a.button("Your team: " + (listed ? team.displayName : "choose from the UK teams"), () -> EditLock.ask(a, () -> a.expandTask(SettingsActivity.TEAM))));
        if (calendar || (team != null && !listed)) a.button("Use your calendar instead" + (team != null && !listed ? ": " + team.displayName : ""), () -> EditLock.ask(a, this::calendarTeam));
        if (team != null) {
            a.button("Badge: " + team.shortName + " on " + Fixtures.colourName(team.primary) + " and " + Fixtures.colourName(team.secondary), () -> badgeColours(team));
            if (!listed) a.button("Other names in the calendar: " + (team.aliases.isEmpty() ? "none" : String.join(", ", team.aliases)), () -> aliases(team));
            a.button("No team", () -> { FixtureFeed.saveTeam(a, null); a.refresh(); });
        }
        a.text(NO_ACCOUNT + " On matchday, Home shows the match and the kick-off time, or \"Live\". Scores are never shown. The badge is drawn from your team's short name and colours; no club crest is used.", 15);
        a.group(MORE_DETAIL);
        if (!api) {
            if (kids) a.text("An adult can add an API-Football key in Adults.", 15);
            else { a.chevron(a.button(ADD_KEY, () -> EditLock.ask(a, () -> a.expandTask(SettingsActivity.PHONE_FOOTBALL)))); a.text("Not needed: without a key, fixtures come from TheSportsDB. With your own free key, API-Football is asked first.", 15); }
        } else {
            ApiFootball.Budget b = FixtureFeed.budget(a);
            String via = FixtureFeed.lastSource(a);
            a.text("API-Football: key saved · " + b.used + " of " + ApiFootball.DAILY_LIMIT + " requests used today", 17).setFocusable(false);
            if (team != null && listed && team.at("api") == null) a.text("TheSportsDB doesn't give API-Football's number for " + team.displayName + ", so its fixtures come from TheSportsDB.", 15);
            else if (SportsDb.SOURCE.equals(via) && listed) a.text("API-Football didn't answer last time, so fixtures came from TheSportsDB.", 15);
            String refused = FixtureFeed.problem(a); if (refused != null) a.text(refused, 16);
            if (!kids) a.button("Remove the football key", () -> EditLock.ask(a, () -> new AlertDialog.Builder(a).setTitle("Remove the football key?").setMessage("The key and the fixtures kept on this TV are deleted. Fixtures carry on from TheSportsDB.")
                .setPositiveButton("Remove", (d, w) -> { FixtureFeed.forgetKey(a); a.refresh(); }).setNegativeButton("Cancel", null).show()));
        }
    }
    /** The calendar alternative: the name of the team as the household's fixtures calendar writes it. */
    private void calendarTeam() {
        Fixtures.Team t = FixtureFeed.team(a); boolean listed = t != null && t.listed();
        EditText f = field("Your team's name, as you'd like it shown", false); if (t != null) f.setText(t.displayName);
        new AlertDialog.Builder(a).setTitle("Your team, from your calendar").setMessage("Used with the fixtures calendar chosen in Doorbell, alarms & this TV, instead of TheSportsDB.").setView(f).setPositiveButton("Save", (d, w) -> { String name = f.getText().toString().trim();
            FixtureFeed.saveTeam(a, name.isEmpty() ? null : new Fixtures.Team("calendar", name, "", t == null ? Fixtures.COLOURS[0] : t.primary, t == null ? Fixtures.COLOURS[1] : t.secondary, Fixtures.Sport.FOOTBALL, t == null || listed ? null : t.aliases)); a.refresh(); })
            .setNegativeButton("Cancel", null).show();
    }
    private void badgeColours(Fixtures.Team t) {
        new AlertDialog.Builder(a).setTitle("Badge: main colour").setItems(Fixtures.COLOUR_NAMES, (d, x) -> new AlertDialog.Builder(a).setTitle("Badge: second colour").setItems(Fixtures.COLOUR_NAMES, (d2, y) -> {
            FixtureFeed.saveTeam(a, t.with(Fixtures.COLOURS[x], Fixtures.COLOURS[y], t.aliases)); a.refresh(); }).show()).show();
    }
    private void aliases(Fixtures.Team t) {
        EditText f = field("Other names, separated by commas", false); f.setText(String.join(", ", t.aliases));
        new AlertDialog.Builder(a).setTitle("Other names in the calendar").setView(f).setPositiveButton("Save", (d, w) -> { List<String> names = new ArrayList<>(); for (String x : f.getText().toString().split(",")) if (!x.trim().isEmpty()) names.add(x.trim());
            FixtureFeed.saveTeam(a, t.with(t.primary, t.secondary, names)); a.refresh(); }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ Team picker (a task in the sheet)
    /** By league (country, then league, then team) or by a few letters; no account or key needed (TheSportsDB). */
    private void teams() {
        TeamList.ensure(a);
        JSONObject cat = FixtureFeed.catalogue(a); List<JSONObject> leagues = TeamList.leagues(cat); teamReady = leagues.size();
        Fixtures.Team current = FixtureFeed.team(a);
        if (current != null) a.text("Now: " + current.displayName, 17).setFocusable(false);
        teamProgress = a.text(progress(cat), 15);
        if (teamLeague != 0) {
            JSONObject league = null; for (JSONObject l : leagues) if (l.optInt("id") == teamLeague) league = l;
            if (league == null) { teamLeague = 0; } else {
                a.group(league.optString("country") + ": " + league.optString("name"));
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
        for (String country : SportsDb.COUNTRIES) {
            boolean header = false;
            for (JSONObject l : leagues) {
                if (!country.equalsIgnoreCase(l.optString("country"))) continue;
                if (!header) { a.group(country); header = true; }
                int id = l.optInt("id"), n = l.optJSONArray("teams").length();
                a.button(l.optString("name") + " · " + n + (n == 1 ? " team" : " teams"), () -> { teamLeague = id; a.redraw(); });
            }
        }
        a.text(SportsDb.ATTRIBUTION, 14).setFocusable(false);
    }
    private String progress(JSONObject cat) {
        if (teamProblem != null) return teamProblem;
        if (cat.optLong("fetchedAt") > 0) return "All UK leagues are ready.";
        int[] p = SportsDb.progress(cat);
        return p[0] == 0 ? "Reading the list of UK teams from TheSportsDB. This takes about a minute the first time and is kept for a week." : "Reading the list of UK teams: " + p[0] + " of " + p[1] + " leagues so far.";
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
    /** The team, with its API-Football number when TheSportsDB knows it, and its own colours unless the household chose some. */
    private void pick(TeamList.Entry e) {
        Fixtures.Team old = FixtureFeed.team(a);
        boolean keep = old != null && old.id.equals("sdb:" + e.id);
        int c1 = keep ? old.primary : e.c1 != 0 ? e.c1 : old == null ? Fixtures.COLOURS[0] : old.primary;
        // The second colour: the club's own when it differs from the first, else lettering that reads on the first.
        int c2 = keep ? old.secondary : e.c1 == 0 ? (old == null ? Fixtures.COLOURS[1] : old.secondary) : e.c2 != 0 && e.c2 != e.c1 ? e.c2 : Fixtures.ink(e.c1);
        FixtureFeed.saveTeam(a, new Fixtures.Team("sdb:" + e.id, e.name, e.code, c1, c2, Fixtures.Sport.FOOTBALL, null, e.api > 0 ? "api:" + e.api : ""));
        teamLeague = 0; teamQuery = null;
        a.note("Your team: " + e.name + ". Fixtures appear on Home on matchday. You can change the badge colours in Smart Home.");
        a.back();
    }
}
