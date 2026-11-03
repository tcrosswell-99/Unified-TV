package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/**
 * "Doorbell, alarms & this TV": what Home Assistant shows on this TV, inside the Settings glass sheet. Rebuilt for the
 * owner on the Sony (run 106, 3 November 2026: "why none chosen? do I need to do anything when someone presses my Ring
 * doorbell?"): a status card that says in plain words what happens now, the doorbell's three rows with a line each, a test
 * that shows the real card, Xbox and the alarms. Each choice opens a list inside the same sheet (one panel at a time, OD-19):
 * plain labels, Ring's own first, the current choice ticked, None, and "Show everything" for the rest. One clear doorbell is
 * chosen automatically. The Frame's items never appear here: the Frame runs the separate Samsung app.
 */
final class HouseholdPage {
    /** Record key, the row's words, and the list's title. Xbox only on the Sony (DockPolicy). */
    static final String[][] ITEMS = {
        {"door", "Doorbell press", "Doorbell press"}, {"motion", "Motion at the door (optional)", "Motion at the door"},
        {"person", "Person at the door", "Person at the door"},
        {"camera", "Picture", "Picture when it rings"}, {"calendar", "Football fixtures calendar", "Football fixtures calendar"},
        {"xbox", "Turn on the Xbox from this TV", "Turn on the Xbox"}};
    /** The Frame's items, kept in old records but never offered on Android. */
    static final Set<String> FRAME_ONLY = new HashSet<>(Arrays.asList("tvplus", "art", "ringEnter", "ringExit"));
    /** The rows' words when nothing is chosen (owner: never "none chosen"). */
    static final String NOT_SET = "Not set up — choose", OFF = "Off", XBOX_NOT_SET = "Not set up — choose a Home Assistant script", TICK = "  ✓";
    static final String NO_RING = "No Ring doorbell found in Home Assistant. Add the Ring integration in Home Assistant (Settings > Devices & services > Add integration > Ring), then come back.";
    /** One line under each row. */
    static final String DOOR_LINE = "When someone presses the doorbell, the TV shows who's there.",
        PERSON_LINE = "A small notice when someone moves at the door. Off unless you choose it.",
        PERSON_ONLY_LINE = "A small notice only when the doorbell is sure it saw a person.",
        CAMERA_LINE = "The picture shown when the doorbell rings.",
        TEST_LINE = "Shows exactly what appears when someone rings, marked TEST. Back closes it.",
        XBOX_LINE = "Pick the Home Assistant script or scene that turns your Xbox on. Unified TV runs it when you choose Xbox.",
        ALARM_LINE = "Alarms show over everything, even the doorbell. Your detectors work as normal.",
        ROOM_LINE = "How Home Assistant knows this TV, for showing alerts here.";
    static final String ROOM = "room";

    private final SettingsActivity a;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private HaEntities.Snapshot snapshot;
    private boolean loading;
    /** The list open inside the sheet (a record key, or ROOM), and whether it shows everything. */
    private String picking; private boolean everything;

    HouseholdPage(SettingsActivity activity) { a = activity; }
    void close() { worker.shutdownNow(); }
    /** Leaving the page: the next visit reads Home Assistant again. */
    void reset() { snapshot = null; picking = null; everything = false; }
    /** Tests: what Home Assistant would answer, without a network. */
    static volatile HaEntities.Snapshot snapshotForTests;
    /** Which list is open inside the sheet; null on the page itself. */
    String picking() { return picking; }

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
            HaEntities.Snapshot next = snapshotForTests;
            if (next == null) { JSONObject ha; try { ha = new Vault(app).read("ha"); } catch (Exception e) { ha = new JSONObject(); } next = HaEntities.fetch(ha); }
            HaEntities.Snapshot done = next;
            a.runOnUiThread(() -> { loading = false; snapshot = done; autoSelect(); if (a.on(-1, SettingsActivity.HOUSEHOLD)) a.refresh(); });
        });
    }
    /** One clear doorbell (and the camera beside it) is chosen by itself; the household can change it. Motion never is. */
    private void autoSelect() {
        if (snapshot == null || snapshot.entities == null) return;
        JSONObject config = config(); JSONArray decided = config.optJSONArray("decided"); boolean changed = false; String room = room();
        for (String[] item : ITEMS) {
            String key = item[0]; if (!config.optString(key).isEmpty() || contains(decided, key) || !visible(key, room)) continue;
            HaEntities.Candidate only = HaEntities.choices(key, snapshot.entities, config.optString("door")).auto();
            if (only == null) continue;
            try { put(config, key, only.id, only.device); changed = true; } catch (Exception ignored) {}
        }
        if (changed) try { new Vault(a).save("household", config); } catch (Exception e) { a.note("Couldn't save on this TV. Nothing was changed."); }
    }

    /**
     * The status card's first words: what happens on this TV now, in plain words. {@code door} is the chosen doorbell's
     * name (null when none), {@code found} whether a doorbell was found, {@code ring} whether it is Ring's, and
     * {@code silent} the Ring doorbells Home Assistant knows but isn't hearing from.
     */
    static String status(boolean saved, String haStatus, boolean tooLarge, String door, boolean found, boolean ring, List<String> silent) {
        if (!saved) return "Home Assistant isn't set up on this TV yet. Set it up on your phone, then your doorbell can show here.";
        if (haStatus == null) return "Checking Home Assistant…";
        if (!HaStatus.AUTHORISED.equals(haStatus)) return HaStatus.line(true, haStatus, Boolean.TRUE) + ". The doorbell can't show here until it is connected again.";
        if (door != null) return "Home Assistant is connected. When someone rings " + door + ", this TV shows who's there. Nothing else to set up in Home Assistant.";
        if (silent != null && !silent.isEmpty()) return "Home Assistant knows " + silent.get(0) + " but isn't hearing from Ring right now. In Home Assistant, open Settings > Devices & services > Ring and sign in again if it asks.";
        if (tooLarge) return "Home Assistant is connected, but has too many devices to list here. Choose a row, then Show everything to type a name.";
        if (found) return "Your " + (ring ? "Ring " : "") + "doorbell isn't set up on this TV yet. Choose it below; nothing to do in Home Assistant.";
        return NO_RING;
    }

    void build() {
        if (snapshot == null) refresh();
        if (picking != null) { picker(); return; }
        JSONObject config = config(); String room = room(); boolean saved = SetupPages.haSaved(a);
        String status = snapshot == null ? null : snapshot.status; List<HaEntities.Entity> entities = snapshot == null ? null : snapshot.entities;
        HaEntities.Choices doors = HaEntities.choices("door", entities, config.optString("door"));
        String door = config.optString("door").isEmpty() ? null : name(config, "door");
        // The status card: what happens now. No Home Assistant automation is needed for any of it.
        a.text(status(saved, status, snapshot != null && snapshot.tooLarge, door, !doors.best().isEmpty(), !doors.ring.isEmpty(), HaEntities.ringNotAnswering(entities)), 19).setFocusable(false);
        if (status != null && !HaStatus.AUTHORISED.equals(status) || !saved) {
            if (saved && status != null) a.text(HaStatus.explain(status), 15);
            a.button(saved ? "Sign in again on your phone" : "Set up on your phone", () -> a.expandTask(SettingsActivity.PHONE_HA));
            if (saved) a.button("Check again", () -> { snapshot = null; a.refresh(); });
        }

        a.section("Doorbell");
        row(config, "door", NOT_SET, DOOR_LINE);
        // "Motion at the door" is its own opt-in ("motion", off by default). A person choice saved by an older version keeps
        // its old meaning (a confirmed person only), shown here so it can be changed or switched off.
        row(config, "motion", OFF, PERSON_LINE);
        if (!config.optString("person").isEmpty()) row(config, "person", OFF, PERSON_ONLY_LINE);
        row(config, "camera", NOT_SET, CAMERA_LINE);
        a.button("Test the doorbell", () -> simulate("door", null, null));
        a.text(TEST_LINE, 15);
        // Each test shows the notice that choice really gives: movement, or a person.
        if (!config.optString("motion").isEmpty()) { String test = notice(config, "motion"); a.button("Test the " + test + " notice", () -> simulate("person", test, null)); }
        if (!config.optString("person").isEmpty()) a.button("Test the person notice", () -> simulate("person", "person", null));
        if (!FixtureFeed.listed(a)) { a.section("Football"); row(config, "calendar", NOT_SET, "Only needed if you'd rather use your own calendar than choose your team in Smart Home > Football."); }
        if (visible("xbox", room)) { a.section("Xbox (optional)"); row(config, "xbox", XBOX_NOT_SET, XBOX_LINE); }

        a.section("Smoke and carbon monoxide");
        boolean alarms = config.optBoolean("alarms", true); List<String> found = HaEntities.alarms(entities);
        // Owner exception to SET-053: switching alarms off takes the adult PIN and a confirmation; switching them on never does.
        a.button(alarmRow(alarms, entities == null ? -1 : found.size()), () -> {
            if (!alarms) { saveFlag("alarms", true); a.refresh(); return; }
            EditLock.ask(a, () -> new AlertDialog.Builder(a).setTitle("Switch alarms off on this TV?").setMessage("Smoke and carbon monoxide alarms will not appear on this TV. Home will show \"" + AlarmWatch.OFF_NOTICE + "\" until an adult switches them back on. Your detectors and their own alarms are not affected.")
                .setPositiveButton("Switch off", (d, w) -> { saveFlag("alarms", false); a.refresh(); }).setNegativeButton("Keep them on", null).show());
        });
        if (alarms && !found.isEmpty()) a.text(String.join(", ", found), 15);
        a.text(ALARM_LINE, 15);
        // The household's own tests (SAFE-01, 04, 05): the real alarm screen, marked TEST; the rooms are examples.
        a.button("Test the alarm screen", () -> simulate("smoke", "on", "Kitchen"));
        a.button("Test a carbon monoxide alarm as well", () -> simulate("carbon_monoxide", "on", "Hallway"));
        a.button("Test a detector going offline", () -> simulate("smoke", "unavailable", "Landing"));
        a.button("End the alarm test", () -> simulate("end", null, null));

        a.section("Reminders");
        boolean reminders = config.optBoolean("reminders");
        a.button("Reminders from Home Assistant: " + (reminders ? "on" : "off"), () -> { saveFlag("reminders", !reminders); a.refresh(); });

        a.section("Showing over other apps");
        boolean overlay = Settings.canDrawOverlays(a);
        a.text(overlay ? "Alerts can appear over other apps on this TV." : "Alerts can't appear over other apps yet.", 17).setFocusable(false);
        if (!overlay) a.button("Allow alerts over other apps", () -> Launch.unlocked(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + a.getPackageName())), "Open this TV's special app access settings"));
        boolean stopped = a.getSharedPreferences(HouseholdSettings.PREFS, 0).getBoolean(AlarmWatch.STOPPED, false);
        if (stopped) a.button("Start household alerts", this::start);

        a.section("This TV");
        a.button("This TV: " + HaEntities.roomName(room), () -> open(ROOM));
        a.text(ROOM_LINE, 15);

        a.section("Start over");
        a.button("Forget these choices", () -> new AlertDialog.Builder(a).setTitle("Forget these choices?").setMessage("The doorbell, camera, calendar and Xbox choices on this TV are forgotten. Home Assistant itself is not changed.")
            .setPositiveButton("Forget", (d, w) -> { try { a.stopService(new Intent(a, HouseholdService.class)); } catch (RuntimeException ignored) {} new Vault(a).forget("household"); a.refresh(); }).setNegativeButton("Cancel", null).show());
    }
    /** "On — found 2 alarms in Home Assistant"; -1 while Home Assistant hasn't answered. */
    static String alarmRow(boolean on, int count) {
        if (!on) return "Off on this TV — choose to switch on";
        if (count < 0) return "On";
        return count == 0 ? "On — no alarms found in Home Assistant yet" : "On — found " + count + (count == 1 ? " alarm" : " alarms") + " in Home Assistant";
    }
    private void row(JSONObject config, String key, String empty, String line) {
        a.button(label(key) + ": " + current(config, key, empty), () -> open(key));
        a.text(line, 15);
    }

    private void start() {
        a.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putBoolean(AlarmWatch.STOPPED, false).apply();
        if (!Settings.canDrawOverlays(a)) { a.note("Allow alerts over other apps first."); a.refresh(); return; }
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) { a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 8); return; }
        try { a.startForegroundService(new Intent(a, HouseholdService.class)); a.note("Household alerts started."); } catch (RuntimeException e) { a.note("Household alerts couldn't start."); }
        a.refresh();
    }

    /** The chosen item in plain words ("Front Door", "Front Door live view"), ticked when it answers; never its technical name. */
    private String current(JSONObject config, String key, String empty) {
        String id = config.optString(key); if (id.isEmpty()) return empty;
        HaEntities.Candidate c = candidate(config, key, id);
        if (c != null) return c.value + (c.responding ? TICK : " (not responding)");
        return name(config, key);
    }
    private HaEntities.Candidate candidate(JSONObject config, String key, String id) {
        if (snapshot == null || snapshot.entities == null) return null;
        HaEntities.Candidate c = HaEntities.choices(key, snapshot.entities, config.optString("door"), true).find(id);
        if (c != null) return c;
        for (HaEntities.Entity e : snapshot.entities) if (e.id.equals(id)) { String[] w = HaEntities.words(key, e); return new HaEntities.Candidate(e, w[0], w[1], w[2], false); }
        return null;
    }
    /** The chosen item's saved name, else its device as Home Assistant names it now. */
    private String name(JSONObject config, String key) {
        String id = config.optString(key);
        HaEntities.Candidate c = candidate(config, key, id); if (c != null) return c.device;
        JSONObject names = config.optJSONObject("names"); String name = names == null ? "" : names.optString(key);
        return name.isEmpty() ? HaEntities.pretty(id.substring(id.indexOf('.') + 1)) : name;
    }
    /** The notice a choice gives: "person" for a sensor or event that reports people, otherwise "motion". */
    private String notice(JSONObject config, String key) {
        if (key.equals("person")) return "person";
        HaEntities.Candidate c = candidate(config, key, config.optString(key));
        return c != null && c.label.endsWith(" — person") ? "person" : "motion";
    }
    static String label(String key) { for (String[] i : ITEMS) if (i[0].equals(key)) return i[1]; return key; }
    private static String title(String key) { if (ROOM.equals(key)) return "Which TV is this?"; for (String[] i : ITEMS) if (i[0].equals(key)) return i[2]; return key; }
    private static String line(String key) {
        switch (key) {
            case "door": return DOOR_LINE; case "motion": return PERSON_LINE; case "person": return PERSON_ONLY_LINE; case "camera": return CAMERA_LINE;
            case "xbox": return XBOX_LINE; case ROOM: return ROOM_LINE; default: return "Choose your fixtures calendar.";
        }
    }

    // --- The list, inside the same sheet (one panel at a time)

    private void open(String key) { picking = key; everything = false; a.redraw(); focus(TICK, true); }
    /** Back from a list: the page again, with the row that opened it in focus. True when a list was open. */
    boolean back() {
        if (picking == null) return false;
        String key = picking; picking = null; everything = false; a.redraw(); focus(rowStart(key), false); return true;
    }
    private static String rowStart(String key) { return ROOM.equals(key) ? "This TV: " : label(key) + ": "; }
    /** Focus the first row whose words start with (or, for the tick, end with) {@code words}. */
    private void focus(String words, boolean ending) {
        View v = find(a.sheet(), words, ending); if (v != null) v.requestFocus();
    }
    private static View find(View v, String words, boolean ending) {
        if (v == null || v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof Button && v.isFocusable()) { String t = ((Button) v).getText().toString(); if (ending ? t.split("\n")[0].endsWith(words) : t.startsWith(words)) return v; }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { View f = find(((ViewGroup) v).getChildAt(i), words, ending); if (f != null) return f; }
        return null;
    }

    private void picker() {
        String key = picking; JSONObject config = config(); String now = config.optString(key);
        a.text(title(key), 24).setFocusable(false);
        a.text(line(key), 15);
        if (ROOM.equals(key)) {
            String room = room();
            for (String[] r : HaEntities.ANDROID_ROOMS) a.button(r[1] + (r[0].equals(room) ? TICK : ""), () -> {
                a.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putString("room", r[0]).apply(); a.note("Saved. Home updates its shortcuts next time it opens."); back();
            });
            return;
        }
        List<HaEntities.Entity> entities = snapshot == null ? null : snapshot.entities;
        HaEntities.Choices choices = HaEntities.choices(key, entities, config.optString("door"));
        boolean doorbell = key.equals("door") || key.equals("motion") || key.equals("person") || key.equals("camera");
        if (snapshot == null) a.text("Reading Home Assistant…", 15);
        if (!choices.ring.isEmpty()) { a.section(key.equals("camera") ? "Ring cameras" : "Ring doorbells"); for (HaEntities.Candidate c : choices.ring) choice(key, c, now); }
        if (!choices.strong.isEmpty()) { a.section(choices.ring.isEmpty() ? "Best matches" : "Others"); for (HaEntities.Candidate c : choices.strong) choice(key, c, now); }
        if (choices.best().isEmpty() && entities != null) a.text(doorbell && HaEntities.choices("door", entities, "").best().isEmpty() ? NO_RING
            : key.equals("xbox") ? "No Xbox script found in Home Assistant." : "Nothing suitable found in Home Assistant.", 15);
        a.section("Other choices");
        a.button((optional(key) ? OFF : "None") + (now.isEmpty() ? TICK : ""), () -> pick(key, null));
        if (!everything) { a.button("Show everything", () -> { everything = true; a.redraw(); focus("Show fewer", false); }); return; }
        if (!choices.other.isEmpty()) { a.section(choices.otherHeading); for (HaEntities.Candidate c : choices.other) choice(key, c, now); }
        a.button("Type its name instead", () -> typeIt(key));
        a.button("Show fewer", () -> { everything = false; a.redraw(); focus("Show everything", false); });
    }
    /** One row of a list: its plain label, ticked when chosen; under "Show everything" its technical name, smaller and dimmer. */
    private void choice(String key, HaEntities.Candidate c, String now) {
        String title = c.label + (c.responding ? "" : " (not responding)") + (c.id.equals(now) ? TICK : "");
        Button b = a.button(title, () -> pick(key, c));
        if (!everything) return;
        SpannableStringBuilder s = new SpannableStringBuilder(title).append("\n"); int start = s.length(); s.append(c.id);
        s.setSpan(new RelativeSizeSpan(0.7f), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new Dimmer(), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setText(s); b.setContentDescription(title);
    }
    /** Secondary words in a row: the row's own colour (light, or dark when focused), dimmed. */
    private static final class Dimmer extends CharacterStyle { @Override public void updateDrawState(TextPaint p) { p.setAlpha(170); } }
    private static boolean optional(String key) { return key.equals("motion") || key.equals("person"); }
    private void pick(String key, HaEntities.Candidate c) {
        try { JSONObject config = config(); put(config, key, c == null ? "" : c.id, c == null ? "" : c.device); new Vault(a).save("household", config); }
        catch (Exception e) { a.note("Couldn't save on this TV. Nothing was changed."); return; }
        if (c == null) a.note(label(key) + ": " + (optional(key) ? "off" : "none"));
        else if (key.equals("motion") && !c.label.endsWith(" — person")) a.note("Saved. Movement also comes from cars, pets and passers-by, so a small notice may appear for them too.");
        else a.note("Saved: " + c.label);
        back();
    }
    /** Advanced only: the item's technical name, with the same validation as before. */
    private void typeIt(String key) {
        EditText field = new EditText(a); field.setSingleLine(true); field.setText(config().optString(key)); field.setSelectAllOnFocus(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); field.setHint(HaEntities.domain(key) + ".");
        new AlertDialog.Builder(a).setTitle(title(key) + " (advanced)").setView(field).setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
            String v = field.getText().toString().trim();
            if (!HaEntities.valid(key, v)) { a.note("That name isn't one Home Assistant uses. Nothing was changed."); return; }
            try { JSONObject config = config(); put(config, key, v, v.isEmpty() ? "" : HaEntities.pretty(v.substring(v.indexOf('.') + 1))); new Vault(a).save("household", config); }
            catch (Exception e) { a.note("Couldn't save on this TV. Nothing was changed."); return; }
            a.note(v.isEmpty() ? label(key) + ": none" : "Saved."); back();
        }).show();
    }
    private void saveFlag(String key, boolean on) {
        try { JSONObject config = config(); config.put(key, on); new Vault(a).save("household", config);
            if (key.equals("alarms")) a.getSharedPreferences(HouseholdSettings.PREFS, 0).edit().putBoolean(AlarmWatch.SHOWN, on).apply();   // Home's notice reads this
        } catch (Exception e) { a.note("Couldn't save on this TV. Nothing was changed."); }
    }
    /** The household record keeps the ID HouseholdService reads, its plain name for display, and which items were decided. */
    static void put(JSONObject config, String key, String id, String name) throws Exception {
        config.put(key, id); JSONObject names = config.optJSONObject("names"); if (names == null) names = new JSONObject(); names.put(key, name); config.put("names", names);
        JSONArray decided = config.optJSONArray("decided"); if (decided == null) decided = new JSONArray(); if (!contains(decided, key)) decided.put(key); config.put("decided", decided);
    }
    static boolean contains(JSONArray list, String key) { if (list != null) for (int i = 0; i < list.length(); i++) if (key.equals(list.optString(i))) return true; return false; }

    /** The tests use HouseholdService's real path (the same card, toast and alarm screen), marked TEST. */
    private void simulate(String kind, String state, String name) {
        if (!Settings.canDrawOverlays(a)) { a.note("Allow alerts over other apps first, so the test can appear over them."); return; }
        try { a.startForegroundService(new Intent(a, HouseholdService.class).setAction("simulate").putExtra("kind", kind).putExtra("state", state).putExtra("name", name)); } catch (RuntimeException e) { a.note("The test couldn't start."); }
    }
}
