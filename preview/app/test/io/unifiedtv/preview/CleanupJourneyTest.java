package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import io.unifiedtv.adb.AdbException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Settings > Updates & About > Advanced > Optional clean-up, driven with the remote's OK against a simulated TV: Adults
 * only (PIN when set), skipping changes nothing and never asks again (AT-18), an unknown TV is read-only (AT-32), the
 * read-only check (AT-20), per-app switches all off, the summary, the change, the result, undo and the end of the link.
 * Every page is the glass sidebar over Home (OD-19).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class CleanupJourneyTest {
    private Context app;
    private FakeTv tv;
    private int opens;

    private SharedPreferences prefs() { return app.getSharedPreferences("unified-tv-preview", 0); }
    private static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private static View root(CleanupActivity a) { return a.findViewById(android.R.id.content); }
    private static Button find(View v, String name) {
        if (v instanceof Button && v.isShown() && ((Button) v).getText().toString().equals(name)) return (Button) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = find(((ViewGroup) v).getChildAt(i), name); if (b != null) return b; }
        return null;
    }
    private static List<CheckBox> switches(View v, List<CheckBox> out) {
        if (v instanceof CheckBox && v.isShown()) out.add((CheckBox) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) switches(((ViewGroup) v).getChildAt(i), out);
        return out;
    }
    private static String words(View v) {
        StringBuilder b = new StringBuilder(); if (v instanceof TextView && v.isShown()) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }
    private static void press(CleanupActivity a, String name) { Button b = find(root(a), name); assertNotNull("'" + name + "' on: " + words(root(a)), b); b.requestFocus(); b.performClick(); idle(); }
    private File recordFile() { return CleanupRecord.file(app.getNoBackupFilesDir()); }

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication(); prefs().edit().clear().commit(); ShadowAlertDialog.reset();
        app.getSharedPreferences(EventLog.PREFS, 0).edit().clear().commit();
        tv = new FakeTv(); opens = 0;
        CleanupLists.forTests = FakeTv.lists();
        CleanupActivity.fingerprintForTests = FakeTv.OWNER;
        CleanupActivity.backgroundForTests = Runnable::run;
        CleanupActivity.openerForTests = (c, prompt) -> { opens++; prompt.showing(); return new CleanupActivity.Opened(tv, tv); };
        File f = recordFile(); if (f.exists()) assertTrue(f.delete());
    }
    @After public void tearDown() { CleanupLists.forTests = null; CleanupActivity.fingerprintForTests = null; CleanupActivity.backgroundForTests = null; CleanupActivity.openerForTests = null; }

    private ActivityController<CleanupActivity> open() { ActivityController<CleanupActivity> c = Robolectric.buildActivity(CleanupActivity.class).setup(); idle(); return c; }

    @Test public void itIsTheSidebarOverHome() {
        try (ActivityController<CleanupActivity> c = open()) {
            CleanupActivity a = c.get();
            int screen = a.getResources().getDisplayMetrics().widthPixels;
            assertEquals(Gravity.END, ((FrameLayout.LayoutParams) a.sheet.getLayoutParams()).gravity);
            float share = a.sheet.getLayoutParams().width / (float) screen;
            assertTrue("about half the screen: " + share, share > .45f && share < .6f);
            assertTrue(words(root(a)).contains(CleanupActivity.TITLE));
            press(a, "Start the read-only check");
            share = a.sheet.getLayoutParams().width / (float) screen;
            assertTrue("the list is a wide task, still a sidebar: " + share, share > .7f && share < .82f);
        }
    }

    @Test public void skippingChangesNothingAndNeverAsksAgain() {
        Map<String, ?> before = new HashMap<>(prefs().getAll());
        try (ActivityController<CleanupActivity> c = open()) {
            CleanupActivity a = c.get();
            assertTrue("off until chosen", words(root(a)).contains("Nothing changes until you choose each app and confirm"));
            press(a, "Not now");
            assertTrue(a.isFinishing());
        }
        assertEquals("the TV was never contacted", 0, opens);
        assertTrue(tv.commands.isEmpty());
        assertFalse("no record", recordFile().exists());
        assertEquals("no setting written, so nothing can nag later (AT-18)", before, prefs().getAll());
    }

    @Test public void kidsCannotOpenIt() {
        prefs().edit().putString(Profiles.PREF, Profiles.CHILD).putString("pin", "x:y").commit();
        try (ActivityController<CleanupActivity> c = open()) {
            CleanupActivity a = c.get();
            assertTrue(words(root(a)).contains("for Adults"));
            assertNull(find(root(a), "Start the read-only check"));
            assertNull("no PIN box for Kids", ShadowAlertDialog.getLatestAlertDialog());
        }
        assertEquals(0, opens);
    }

    @Test public void theAdultPinIsAskedWhenSet() throws Exception {
        prefs().edit().putString("pin", Pins.record("2468")).commit();
        try (ActivityController<CleanupActivity> c = open()) {
            CleanupActivity a = c.get();
            assertNull("locked until the PIN", find(root(a), "Start the read-only check"));
            AlertDialog d = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull("Adult access", d);
            Pins.type(d, "2468");
            assertNotNull(find(root(a), "Start the read-only check"));
        }
    }

    @Test public void anUnknownTvGetsReadOnlyGuidance() {
        CleanupActivity.fingerprintForTests = FakeTv.SONY;
        try (ActivityController<CleanupActivity> c = open()) {
            CleanupActivity a = c.get();
            assertTrue(words(root(a)).contains("isn't one the clean-up lists were tested on"));
            assertNull("no way to change anything", find(root(a), "Start the read-only check"));
        }
        assertEquals("the TV's debugging isn't even used", 0, opens);
    }

    @Test public void checkChooseConfirmVerifyUndoAndClose() {
        try (ActivityController<CleanupActivity> c = open()) {
            CleanupActivity a = c.get();
            press(a, "Start the read-only check");
            assertEquals(1, opens);
            assertTrue("read-only so far", tv.changes().isEmpty());
            String page = words(root(a));
            assertTrue(page.contains("Read-only check: nothing has changed"));
            assertTrue(page.contains("Tested list: TCL 50C725K, on this exact firmware."));
            assertTrue("accessibility is shown, never offered", page.contains("com.google.android.marvin.talkback): kept."));
            List<CheckBox> all = switches(root(a), new ArrayList<>());
            assertFalse(all.isEmpty());
            for (CheckBox s : all) assertFalse("every switch starts off: " + s.getText(), s.isChecked());
            assertNull("talkback has no switch", find(root(a), "Turn off: Accessibility screen reader"));
            // An empty choice changes nothing.
            press(a, "Review my choices (none yet)");
            assertTrue(tv.changes().isEmpty());
            press(a, "Turn off: TCL analytics");
            press(a, "Turn off: Store demo sticker");
            press(a, "Review my choices (2)");
            page = words(root(a));
            assertTrue(page.contains("• TCL analytics (com.tcl.bi)")); assertTrue(page.contains("• Store demo sticker (com.tcl.esticker)"));
            assertTrue("still nothing changed before the confirm", tv.changes().isEmpty());
            assertFalse(recordFile().exists());
            press(a, "Turn off these 2 apps");
            assertEquals(Arrays.asList("pm disable-user --user 0 com.tcl.bi", "pm disable-user --user 0 com.tcl.esticker"), tv.changes());
            assertTrue("the record was written", recordFile().exists());
            page = words(root(a));
            assertTrue(page.contains("TCL analytics (com.tcl.bi): off"));
            List<EventLog.Event> log = EventLog.recent(app);
            assertEquals(2, log.size()); assertTrue(log.get(0).words.startsWith("Clean-up turned off com.tcl."));
            press(a, "Check the TV still works");
            assertTrue(words(root(a)).contains("Switch to each HDMI input"));
            a.onBackPressed(); idle();
            press(a, "Undo all (2)");
            assertFalse(tv.disabled.contains("com.tcl.bi")); assertFalse(tv.disabled.contains("com.tcl.esticker"));
            assertTrue(words(root(a)).contains("TCL analytics (com.tcl.bi): on"));
            press(a, "Finish");
            assertTrue("the link is closed at the end", tv.closed);
            assertTrue(words(root(a)).contains("Turn off USB debugging"));
            assertNotNull(find(root(a), "Check that it's off"));
            press(a, "Done");
            assertTrue(a.isFinishing());
        }
        assertTrue("never uninstall, hide or clear", String.join("\n", tv.commands).matches("(?s)^((?!uninstall|pm hide|pm clear|reboot).)*$"));
    }

    @Test public void theRecommendedOnesAreOnlyASuggestion() {
        try (ActivityController<CleanupActivity> c = open()) {
            CleanupActivity a = c.get();
            press(a, "Start the read-only check");
            Button choose = null; for (String w : words(root(a)).split("\n")) if (w.startsWith("Choose the recommended ones")) choose = find(root(a), w);
            assertNotNull(choose); choose.performClick(); idle();
            assertTrue("chosen, not changed", tv.changes().isEmpty());
            List<String> on = new ArrayList<>(); for (CheckBox s : switches(root(a), new ArrayList<>())) if (s.isChecked()) on.add(s.getText().toString());
            assertTrue(on.contains("Turn off: TCL analytics"));
            assertFalse("voice is never suggested", on.toString().contains("Google voice assistant"));
            assertFalse("streaming apps are never suggested", on.toString().contains("Streaming apps"));
        }
    }

    @Test public void aTvThatSaysNoIsExplained() {
        CleanupActivity.openerForTests = (c, prompt) -> { throw new AdbException(AdbException.Kind.NOT_ALLOWED, "no"); };
        try (ActivityController<CleanupActivity> c = open()) {
            CleanupActivity a = c.get();
            press(a, "Start the read-only check");
            assertTrue(words(root(a)).contains("Always allow from this computer"));
            assertNotNull(find(root(a), "Try again"));
        }
        assertFalse(recordFile().exists());
    }

    @Test public void leavingTheScreenClosesTheLink() {
        ActivityController<CleanupActivity> c = open();
        press(c.get(), "Start the read-only check");
        assertFalse(tv.closed);
        c.pause().stop(); idle();
        assertTrue("session-only: closed when the screen goes", tv.closed);
        c.destroy();
    }
}
