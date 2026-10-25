package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/**
 * "Doorbell, alarms & this TV": what Home Assistant shows on this TV, inside the Settings glass sheet (owner, 25
 * October 2026: plain words, and only what applies to this TV). Each item is chosen by remote from the household's own
 * Home Assistant, and one clear match is filled in automatically. The Frame's items never appear here: the Frame runs
 * the separate Samsung app. Typing an item's technical name stays an advanced choice inside each list.
 */
final class HouseholdPage {
    /** Record key, the row's words, and how a found item is described. Xbox only on the Sony (DockPolicy). */
    static final String[][] ITEMS = {
        {"door", "Doorbell button", "your doorbell button"}, {"person", "Someone at the door", "person alerts at the door"},
        {"camera", "Doorbell camera", "your doorbell camera"}, {"calendar", "Football fixtures calendar", "your fixtures calendar"},
        {"xbox", "Turn on the Xbox", "the action that turns on the Xbox"}};
    /** The Frame's items, kept in old records but never offered on Android. */
    static final Set<String> FRAME_ONLY = new HashSet<>(Arrays.asList("tvplus", "art", "ringEnter", "ringExit"));

    private final SettingsActivity a;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private HaEntities.Snapshot snapshot;
    private boolean technical, loading;
    private final List<String> found = new ArrayList<>();

    HouseholdPage(SettingsActivity activity) { a = activity; }
    void close() { worker.shutdownNow(); }
    /** Leaving the page: the next visit reads Home Assistant again. */
    void reset() { snapshot = null; found.clear(); }

    private JSONObject config() { try { return new Vault(a).read("household"); } catch (Exception e) { return new JSONObject(); } }
    /** This TV: the saved TCL or Sony choice, else by maker. A Frame saved by an older version counts as unset. */
    static String room(String saved, String manufacturer) {
        for (String[] r : HaEntities.ANDROID_ROOMS) if (r[0].equals(saved)) return saved;
        return manufacturer != null && manufacturer.toLowerCase(Locale.ROOT).contains("sony") ? "sony" : "tcl";
    }
    private String room() { return room(a.getSharedPreferences(HouseholdSettings.PREFS, 0).getString("room", ""), Build.MANUFACTURER); }
    /** Which items this TV shows. */
    static boolean visible(String key, String room) {
        if (FRAME_ONLY.contains(key)) return false;
        return !key.equals("xbox") || room.equals("sony");
    }

    private void refresh() {
        if (loading) return; loading = true;
        android.content.Context app = a.getApplicationContext();
        worker.execute(() -> {
            JSONObject ha; try { ha = new Vault(app).read("ha"); } catch (Exception e) { ha = new JSONObject(); }
            HaEntities.Snapshot next = HaEntities.fetch(ha);
            a.runOnUiThread(() -> { loading = false; snapshot = next; autoSelect(); if (a.on(-1, SettingsActivity.HOUSEHOLD)) a.refresh(); });
        });
    }
    private void autoSelect() {
        found.clear(); if (snapshot == null || snapshot.entities == null) return;
        JSONObject config = config(); JSONArray decided = config.optJSONArray("decided"); boolean changed = false; String room = room();
        for (String[] item : ITEMS) {
            String key = item[0]; if (!config.optString(key).isEmpty() || contains(decided, key) || !visible(key, room)) continue;
            HaEntities.Candidate only = HaEntities.choices(key, snapshot.entities, config.optString("door")).auto();
            if (only == null) continue;
            try { put(config, key, only.id, only.name); changed = true; found.add("Found " + item[2] + ": " + only.name); } catch (Exception ignored) {}
        }
        if (changed) try { new Vault(a).save("household", config); } catch (Exception e) { found.clear(); a.note("Couldn't save on this TV. Nothing was changed."); }
    }

    void build() {
        if (snapshot == null) refresh();
        JSONObject config = config(); String room = room(); boolean saved = SetupPages.haSaved(a);
        String status = snapshot == null ? null : snapshot.status;
        a.text(HaStatus.line(saved, status, saved ? Boolean.TRUE : null), 19).setFocusable(false);
        if (status != null && !HaStatus.AUTHORISED.equals(status)) {
            a.text(snapshot.tooLarge ? "Your Home Assistant has too many devices to list here. Each list below has an advanced choice to type a name." : HaStatus.explain(status), 15);
            a.button(saved ? "Sign in again on your phone" : "Set up on your phone", () -> a.expandTask(SettingsActivity.PHONE_HA));
            if (saved) a.button("Check again", () -> { snapshot = null; a.refresh(); });
        }
        for (String line : found) a.text(line + ". Change it below if that's wrong.", 15);

        a.section("This TV");
        a.button("This TV: " + HaEntities.roomName(room), this::chooseRoom);
        a.section("Doorbell");
        for (String key : new String[]{"door", "person", "camera"}) a.button(label(key) + ": " + current(config, key), () -> choose(key));
        List<String> waiting = snapshot == null ? null : HaEntities.waitingDoorbells(snapshot.entities);
        if (waiting != null && !waiting.isEmpty() && config.optString("door").isEmpty())
            a.text("Home Assistant also shows " + waiting.get(0) + " as a newer kind of doorbell. This TV can't use that kind yet, so choose a doorbell sensor if you have one.", 15);
        if (!FixtureFeed.listed(a)) { a.section("Football"); a.button(label("calendar") + ": " + current(config, "calendar"), () -> choose("calendar")); a.text("Only needed if you'd rather use your own calendar than choose your team from the list in Smart Home > Football.", 15); }
        if (visible("xbox", room)) { a.section("Xbox"); a.button(label("xbox") + ": " + current(config, "xbox"), () -> choose("xbox")); a.text("Choose the Home Assistant action that wakes the Xbox and switches the TV to it.", 15); }

        a.section("Alarms and reminders");
        boolean alarms = config.optBoolean("alarms", true);
        // Owner exception to SET-053: switching alarms off takes the adult PIN and a confirmation; switching them on never does.
        a.button("Smoke and carbon monoxide alarms: " + (alarms ? "shown" : "off on this TV"), () -> {
            if (!alarms) { saveFlag("alarms", true); a.refresh(); return; }
            EditLock.ask(a, () -> new AlertDialog.Builder(a).setTitle("Switch alarms off on this TV?").setMessage("Smoke and carbon monoxide alarms will not appear on this TV. Home will show \"" + AlarmWatch.OFF_NOTICE + "\" until an adult switches them back on. Your detectors and their own alarms are not affected.")
                .setPositiveButton("Switch off", (d, w) -> { saveFlag("alarms", false); a.refresh(); }).setNegativeButton("Keep them on", null).show());
        });
        a.text("Found from Home Assistant's own smoke and carbon monoxide sensors. They show above the doorbell.", 15);
        boolean reminders = config.optBoolean("reminders");
        a.button("Reminders from Home Assistant: " + (reminders ? "on" : "off"), () -> { saveFlag("reminders", !reminders); a.refresh(); });

        a.section("Showing over other apps");
        boolean overlay = Settings.canDrawOverlays(a);
        a.text(overlay ? "Alerts can appear over other apps on this TV." : "Alerts can't appear over other apps yet.", 17).setFocusable(false);
        if (!overlay) a.button("Allow alerts over other apps", () -> Launch.unlocked(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + a.getPackageName())), "Open this TV's special app access settings"));
        boolean stopped = a.getSharedPreferences(HouseholdSettings.PREFS, 0).getBoolean(AlarmWatch.STOPPED, false);
        if (stopped) a.button("Start household alerts", this::start);

        a.section("Try it");
        a.text("Shows exactly what a real alert would, marked TEST. Nothing is sent to Home Assistant.", 15);
        a.button("Test: smoke alarm in the kitchen", () -> simulate("smoke", "on", "Kitchen"));
        a.button("Test: carbon monoxide alarm in the hallway", () -> simulate("carbon_monoxide", "on", "Hallway"));
        a.button("Test: a smoke detector goes offline", () -> simulate("smoke", "unavailable", "Landing"));
        a.button("Test: someone rings the doorbell", () -> simulate("door", null, null));
        a.button("Test: someone at the door (small notice only)", () -> simulate("person", null, null));
        a.button("End the test", () -> simulate("end", null, null));

        a.section("Start over");
        a.button("Forget these choices", () -> new AlertDialog.Builder(a).setTitle("Forget these choices?").setMessage("The doorbell, camera, calendar and Xbox choices on this TV are forgotten. Home Assistant itself is not changed.")
            .setPositiveButton("Forget", (d, w) -> { try { a.stopService(new Intent(a, HouseholdService.class)); } catch (RuntimeException ignored) {} new Vault(a).forget("household"); found.clear(); a.refresh(); }).setNegativeButton("Cancel", null).show());
    }

    private void start() {
        a.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putBoolean(AlarmWatch.STOPPED, false).apply();
        if (!Settings.canDrawOverlays(a)) { a.note("Allow alerts over other apps first."); a.refresh(); return; }
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) { a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 8); return; }
        try { a.startForegroundService(new Intent(a, HouseholdService.class)); a.note("Household alerts started."); } catch (RuntimeException e) { a.note("Household alerts couldn't start."); }
        a.refresh();
    }

    /** The choice in plain words: its friendly name, never the technical name. */
    private String current(JSONObject config, String key) {
        String id = config.optString(key); if (id.isEmpty()) return "none chosen";
        if (snapshot != null && snapshot.entities != null) for (HaEntities.Entity e : snapshot.entities) if (e.id.equals(id)) return e.name + (e.responding() ? "" : " (not responding)");
        JSONObject names = config.optJSONObject("names"); String name = names == null ? "" : names.optString(key);
        return name.isEmpty() ? HaEntities.pretty(id.substring(id.indexOf('.') + 1)) : name;
    }
    static String label(String key) { for (String[] i : ITEMS) if (i[0].equals(key)) return i[1]; return key; }

    private void chooseRoom() {
        String[][] rooms = HaEntities.ANDROID_ROOMS; String[] names = new String[rooms.length]; int now = 0; String room = room();
        for (int i = 0; i < rooms.length; i++) { names[i] = rooms[i][1]; if (rooms[i][0].equals(room)) now = i; }
        new AlertDialog.Builder(a).setTitle("Which TV is this?").setSingleChoiceItems(names, now, (d, which) -> {
            d.dismiss(); a.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putString("room", rooms[which][0]).apply(); a.note("Saved. Home updates its shortcuts next time it opens."); a.refresh();
        }).setNegativeButton("Cancel", null).show();
    }

    /** A remote-friendly list: clear matches, a divider, other items, None, then the advanced choices. */
    private void choose(String key) {
        JSONObject config = config(); String label = label(key);
        HaEntities.Choices choices = snapshot == null || snapshot.entities == null ? new HaEntities.Choices(new ArrayList<>(), new ArrayList<>(), "") : HaEntities.choices(key, snapshot.entities, config.optString("door"));
        List<CharSequence> rows = new ArrayList<>(); List<HaEntities.Candidate> picks = new ArrayList<>(); List<Boolean> enabled = new ArrayList<>();
        for (HaEntities.Candidate c : choices.strong) { rows.add(row(c)); picks.add(c); enabled.add(true); }
        if (!choices.other.isEmpty()) { rows.add("--- " + choices.otherHeading + " ---"); picks.add(null); enabled.add(false);
            for (HaEntities.Candidate c : choices.other) { rows.add(row(c)); picks.add(c); enabled.add(true); } }
        if (choices.isEmpty() && snapshot != null && snapshot.entities != null) { rows.add("Nothing suitable found in Home Assistant"); picks.add(null); enabled.add(false); }
        if (snapshot == null) { rows.add("Still reading Home Assistant…"); picks.add(null); enabled.add(false); }
        int none = rows.size(); rows.add("None"); picks.add(null); enabled.add(true);
        int typed = rows.size(); rows.add("Type its name instead (advanced)"); picks.add(null); enabled.add(true);
        int names = rows.size(); rows.add(technical ? "Hide technical names (advanced)" : "Show technical names (advanced)"); picks.add(null); enabled.add(true);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(a, android.R.layout.simple_list_item_1, rows) {
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) { return enabled.get(position); }
        };
        new AlertDialog.Builder(a).setTitle(label).setAdapter(adapter, (d, which) -> {
            if (which == none) { pick(key, null, null, false); return; }
            if (which == typed) { typeIt(key); return; }
            if (which == names) { technical = !technical; choose(key); return; }
            HaEntities.Candidate chosen = picks.get(which); if (chosen == null) return;
            pick(key, chosen.id, chosen.name, !choices.strong.contains(chosen));
        }).setNegativeButton("Cancel", null).show();
    }
    private CharSequence row(HaEntities.Candidate c) {
        String title = c.name + (c.responding ? "" : " (not responding)"); if (!technical) return title;
        SpannableStringBuilder s = new SpannableStringBuilder(title).append("\n"); int start = s.length(); s.append(c.id);
        s.setSpan(new RelativeSizeSpan(0.7f), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); return s;
    }
    private void pick(String key, String id, String name, boolean below) {
        try { JSONObject config = config(); put(config, key, id == null ? "" : id, name == null ? "" : name); new Vault(a).save("household", config); }
        catch (Exception e) { a.note("Couldn't save on this TV. Nothing was changed."); return; }
        found.clear();
        if (id == null) a.note(label(key) + ": none");
        else if (key.equals("person") && below) a.note("Saved. Motion isn't the same as a person: the camera may also appear for cars, pets and passers-by.");
        else a.note("Saved: " + name);
        a.refresh();
    }
    /** Advanced only: the item's technical name, with the same validation as before. */
    private void typeIt(String key) {
        EditText field = new EditText(a); field.setSingleLine(true); field.setText(config().optString(key)); field.setSelectAllOnFocus(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); field.setHint(HaEntities.domain(key) + ".");
        new AlertDialog.Builder(a).setTitle(label(key) + " (advanced)").setView(field).setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
            String v = field.getText().toString().trim();
            if (!HaEntities.valid(key, v)) { a.note("That should start with " + HaEntities.domain(key) + ". and use only lower-case letters, numbers and _. Nothing was changed."); return; }
            pick(key, v.isEmpty() ? null : v, v.isEmpty() ? null : HaEntities.pretty(v.substring(v.indexOf('.') + 1)), false);
        }).show();
    }
    private void saveFlag(String key, boolean on) {
        try { JSONObject config = config(); config.put(key, on); new Vault(a).save("household", config);
            if (key.equals("alarms")) a.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putBoolean(AlarmWatch.SHOWN, on).apply();   // Home's notice reads this
        } catch (Exception e) { a.note("Couldn't save on this TV. Nothing was changed."); }
    }
    /** The household record keeps the ID HouseholdService reads, its friendly name for display, and which items were decided. */
    static void put(JSONObject config, String key, String id, String name) throws Exception {
        config.put(key, id); JSONObject names = config.optJSONObject("names"); if (names == null) names = new JSONObject(); names.put(key, name); config.put("names", names);
        JSONArray decided = config.optJSONArray("decided"); if (decided == null) decided = new JSONArray(); if (!contains(decided, key)) decided.put(key); config.put("decided", decided);
    }
    static boolean contains(JSONArray list, String key) { if (list != null) for (int i = 0; i < list.length(); i++) if (key.equals(list.optString(i))) return true; return false; }

    private void simulate(String kind, String state, String name) {
        if (!Settings.canDrawOverlays(a)) { a.note("Allow alerts over other apps first, so the test can appear over them."); return; }
        try { a.startForegroundService(new Intent(a, HouseholdService.class).setAction("simulate").putExtra("kind", kind).putExtra("state", state).putExtra("name", name)); } catch (RuntimeException e) { a.note("The test couldn't start."); }
    }
}
