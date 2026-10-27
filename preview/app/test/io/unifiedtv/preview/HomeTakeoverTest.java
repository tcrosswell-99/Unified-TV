package io.unifiedtv.preview;

import android.content.Context;
import io.unifiedtv.adb.AdbCommands;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

/**
 * OD-28, the steps under the Home takeover page, against a simulated TCL 50C725K (FakeTv with FakeHome): the exact
 * commands each step may send, the gentle route, the disable route only after Unified TV's Home entry is a confirmed
 * candidate and only after the record is on disk, refusals (unknown firmware, not the stock Home, no candidate), another
 * Home claimant reported and never turned off, the record shared with the Optional clean-up (first state wins), and the
 * way back. The launcher app's package is used as "self" in both flavours: these are the pure steps, not the page.
 * tests/test_cleanup_record.py replays the record written here (test/fixtures/home-takeover-record.json) with recovery.py.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30)
public class HomeTakeoverTest {
    static final String SELF = "io.unifiedtv.launcher", MINE = SELF + "/io.unifiedtv.preview.HomeEntry";
    static final String STOCK = "com.google.android.tvlauncher", CREATED = "2026-10-27T12:00:00+0000";
    private Context app;
    private FakeTv tv;
    private FakeHome homes;
    private File record;

    static String fixture(String name) throws Exception { return new String(Files.readAllBytes(new File(SidebarRuleTest.app(), "test/fixtures/" + name).toPath()), StandardCharsets.UTF_8); }

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        tv = new FakeTv(); homes = new FakeHome(SELF); tv.homes = homes;
        record = new File(app.getCacheDir(), "takeover/recovery.json");
        if (record.exists()) assertTrue(record.delete());
    }

    private HomeTakeover.Check check(String fingerprint) throws Exception { tv.fingerprint = fingerprint; return HomeTakeover.check(tv, FakeTv.lists(), fingerprint, SELF); }

    // --- the allowlist
    @Test public void setHomeIsAllowedOnlyForUnifiedTvsOwnEntryOrAStockHome() {
        assertTrue(AdbCommands.allowed("cmd package set-home-activity --user 0 " + MINE));
        assertTrue("the review build", AdbCommands.allowed("cmd package set-home-activity --user 0 io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.HomeEntry"));
        assertTrue("handing back to the stock Home", AdbCommands.allowed("cmd package set-home-activity --user 0 " + FakeTv.HOME));
        assertTrue(AdbCommands.allowed("cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity"));
        assertTrue(AdbCommands.changes("cmd package set-home-activity --user 0 " + MINE));
        for (String no : new String[]{
            "cmd package set-home-activity --user 0 io.unifiedtv.preview/io.unifiedtv.preview.HomeEntry",          // the preview never
            "cmd package set-home-activity --user 0 io.unifiedtv.launcher/io.unifiedtv.preview.UnifiedPreviewActivity",
            "cmd package set-home-activity --user 0 com.gathin.tvhome/.Home",
            "cmd package set-home-activity --user 0 " + FakeHome.SETUP,
            "cmd package set-home-activity --user 0 " + MINE + ";reboot",
            "cmd package set-home-activity --user 0 " + MINE + " && pm uninstall " + STOCK,
            "cmd package set-home-activity " + MINE,
            "cmd role add-role-holder android.app.role.HOME " + SELF,
            "pm uninstall " + STOCK, "pm hide " + STOCK})
            assertFalse(no, AdbCommands.allowed(no));
        try { AdbCommands.setHome(FakeHome.SETUP); fail("not a Home this can set"); } catch (IllegalArgumentException expected) { /* refused */ }
    }

    // --- the gentle route
    @Test public void gentleRouteWhenAndroidsChoiceIsEnough() throws Exception {
        homes.priority.put(FakeTv.HOME, 0);   // a TV whose own Home doesn't claim a higher priority
        HomeTakeover.Check c = check(FakeTv.OWNER);
        assertNull("two equal Homes and no choice yet: Android's chooser", c.before.home);
        assertTrue(c.before.candidate(SELF));
        assertTrue("read-only so far", tv.changes().isEmpty());
        HomeTakeover.Reading after = HomeTakeover.gentle(tv, c, SELF);
        assertTrue(after.ours(SELF));
        assertEquals("Android's own route only", Collections.singletonList(AdbCommands.setHome(MINE)), tv.changes());
        assertFalse("no record: nothing was turned off", record.exists());
    }

    // --- the disable route
    @Test public void stockHomeIsTurnedOffOnlyAfterTheEntryIsConfirmedAndTheRecordIsWritten() throws Exception {
        HomeTakeover.Check c = check(FakeTv.OWNER);
        assertEquals(FakeTv.HOME, c.before.home);
        HomeTakeover.Reading after = HomeTakeover.gentle(tv, c, SELF);
        assertEquals("priority 2 keeps the TV's own Home in front", FakeTv.HOME, after.home);
        assertNull("a tested-list TV, our entry a candidate, the stock Home in front", HomeTakeover.refusal(c, after, SELF));
        tv.beforeChange = () -> {
            CleanupRecord r = CleanupRecord.read(record);
            assertNotNull("the record is on disk before the TV's Home is turned off", r);
            assertEquals(STOCK, r.takeoverLauncher); assertEquals(CleanupRecord.ENABLED, r.packages.get(STOCK)); assertEquals(FakeTv.HOME, r.home);
        };
        HomeTakeover.Result r = HomeTakeover.takeOver(tv, record, c, SELF, CREATED);
        assertEquals(HomeTakeover.Outcome.TAKEN, r.outcome);
        assertTrue(r.after.ours(SELF));
        assertEquals("exactly these changes, in this order", Arrays.asList(AdbCommands.setHome(MINE), AdbCommands.disable(STOCK), AdbCommands.setHome(MINE)), tv.changes());
        assertTrue(tv.disabled.contains(STOCK));
        assertTrue("never uninstalled", tv.installed.contains(STOCK));
        assertEquals("recovery.py's own format (tests/test_cleanup_record.py replays it)", fixture("home-takeover-record.json"), new String(Files.readAllBytes(record.toPath()), StandardCharsets.UTF_8));
        for (String sent : tv.commands) assertTrue("only the allowlist: " + sent, AdbCommands.allowed(sent));
    }

    @Test public void notWhileUnifiedTvsEntryIsntACandidate() throws Exception {
        homes.ours = () -> false;
        HomeTakeover.Check c = check(FakeTv.OWNER);
        assertFalse(c.before.candidate(SELF));
        assertSame(c.before, HomeTakeover.gentle(tv, c, SELF));
        assertEquals(HomeTakeover.NOT_CANDIDATE, HomeTakeover.refusal(c, c.before, SELF));
        try { HomeTakeover.takeOver(tv, record, c, SELF, CREATED); fail("refused"); } catch (HomeTakeover.Changed e) { assertEquals(HomeTakeover.NOT_CANDIDATE, e.getMessage()); }
        assertTrue("nothing sent that changes the TV", tv.changes().isEmpty());
        assertFalse(record.exists());
    }

    @Test public void refusesOnFirmwareNoListCovers() throws Exception {
        HomeTakeover.Check c = check(FakeTv.SONY);
        assertFalse(c.match.known());
        HomeTakeover.Reading after = HomeTakeover.gentle(tv, c, SELF);
        assertEquals(HomeTakeover.UNKNOWN_TV, HomeTakeover.refusal(c, after, SELF));
        try { HomeTakeover.takeOver(tv, record, c, SELF, CREATED); fail("refused"); } catch (HomeTakeover.Changed e) { assertEquals(HomeTakeover.UNKNOWN_TV, e.getMessage()); }
        assertEquals("Android's own route only", Collections.singletonList(AdbCommands.setHome(MINE)), tv.changes());
        assertFalse(tv.disabled.contains(STOCK));
    }

    @Test public void onlyTheStockHomeTheListNames() throws Exception {
        // Home answered by something that isn't the list's stock launcher (here the setup wizard): never turned off.
        homes.priority.put(FakeHome.SETUP, 5);
        HomeTakeover.Check c = check(FakeTv.OWNER);
        assertEquals(HomeTakeover.notStock("com.google.android.tungsten.setupwraith"), HomeTakeover.refusal(c, c.before, SELF));
        try { HomeTakeover.takeOver(tv, record, c, SELF, CREATED); fail("refused"); } catch (HomeTakeover.Changed expected) { /* refused */ }
        assertTrue(tv.changes().isEmpty());
        // The general clean-up still refuses every Home app, the stock one included.
        CleanupPolicy.Facts f = new CleanupPolicy.Facts();
        assertNotNull(CleanupPolicy.blocked(STOCK, f));
        f.homeClaims.add("com.google.android.tungsten.setupwraith");
        assertNotNull(CleanupPolicy.blocked("com.google.android.tungsten.setupwraith", f));
    }

    @Test public void anotherHomeClaimantIsReportedAndNotTurnedOff() throws Exception {
        homes.priority.put(FakeHome.SETUP, 1);   // the setup wizard still answers Home, below tvlauncher (examples/tcl-55c725 notes)
        HomeTakeover.Check c = check(FakeTv.OWNER);
        HomeTakeover.Reading after = HomeTakeover.gentle(tv, c, SELF);
        HomeTakeover.Result r = HomeTakeover.takeOver(tv, record, c, SELF, CREATED);
        assertEquals(HomeTakeover.Outcome.OTHER_HOME, r.outcome);
        assertEquals("com.google.android.tungsten.setupwraith", r.other());
        assertNotNull(after);
        assertEquals("only the stock Home was turned off", Collections.singletonList(AdbCommands.disable(STOCK)), disables());
        assertFalse(tv.disabled.contains("com.google.android.tungsten.setupwraith"));
        assertEquals("the record says what to undo", STOCK, CleanupRecord.read(record).takeoverLauncher);
    }

    @Test public void aFailedTurnOffLeavesNothingBehind() throws Exception {
        tv.failDisable.add(STOCK);
        HomeTakeover.Check c = check(FakeTv.OWNER);
        HomeTakeover.gentle(tv, c, SELF);
        HomeTakeover.Result r = HomeTakeover.takeOver(tv, record, c, SELF, CREATED);
        assertEquals(HomeTakeover.Outcome.FAILED, r.outcome);
        CleanupRecord rec = CleanupRecord.read(record);
        assertNull("no marker", rec.takeoverLauncher);
        assertFalse("never changed, so not in the record", rec.packages.containsKey(STOCK));
    }

    // --- one record with the Optional clean-up
    @Test public void theCleanUpsRecordIsAppendedToAndItsFirstStateWins() throws Exception {
        record.getParentFile().mkdirs();
        Files.write(record.toPath(), fixture("cleanup-record.json").getBytes(StandardCharsets.UTF_8));
        CleanupRecord before = CleanupRecord.read(record);
        HomeTakeover.Check c = check(FakeTv.OWNER);
        HomeTakeover.gentle(tv, c, SELF);
        assertEquals(HomeTakeover.Outcome.TAKEN, HomeTakeover.takeOver(tv, record, c, SELF, "2026-10-28T09:00:00+0000").outcome);
        CleanupRecord after = CleanupRecord.read(record);
        assertEquals("the first record's time", before.created, after.created);
        assertEquals(before.home, after.home);
        assertEquals("enabled", after.packages.get("com.tcl.bi")); assertEquals("enabled", after.packages.get("com.tcl.esticker"));
        assertEquals("appended", CleanupRecord.ENABLED, after.packages.get(STOCK));
        assertEquals(Arrays.asList("com.tcl.bi", "com.tcl.esticker"), after.changedApps());
        assertTrue("the restore list still names it", after.changed().contains(STOCK));
        assertTrue(after.listText().contains(STOCK + "\n"));
    }

    @Test public void aRecordSayingTheStockHomeWasOffBeforeStopsIt() throws Exception {
        record.getParentFile().mkdirs();
        Files.write(record.toPath(), fixture("cleanup-record.json").replace("\"com.tcl.bi\": \"enabled\"", "\"com.google.android.tvlauncher\": \"disabled\",\n    \"com.tcl.bi\": \"enabled\"").getBytes(StandardCharsets.UTF_8));
        assertEquals(CleanupRecord.DISABLED, CleanupRecord.read(record).packages.get(STOCK));
        HomeTakeover.Check c = check(FakeTv.OWNER);
        HomeTakeover.gentle(tv, c, SELF);
        try { HomeTakeover.takeOver(tv, record, c, SELF, CREATED); fail("refused"); } catch (HomeTakeover.Changed e) { assertEquals(HomeTakeover.RECORD_SAYS_OFF, e.getMessage()); }
        assertTrue(disables().isEmpty());
    }

    // --- the way back
    @Test public void returnTurnsTheStockHomeBackOnSetsItAndWithdrawsTheEntry() throws Exception {
        HomeTakeover.Check c = check(FakeTv.OWNER);
        HomeTakeover.gentle(tv, c, SELF);
        HomeTakeover.takeOver(tv, record, c, SELF, CREATED);
        tv.commands.clear();
        boolean[] withdrawn = {false};
        HomeTakeover.Result r = HomeTakeover.giveBack(tv, record, CleanupRecord.read(record), () -> {
            assertFalse("withdrawn only once the stock Home is on again", tv.disabled.contains(STOCK));
            assertNull("and once the marker is gone (DefaultHome.withdraw refuses otherwise)", CleanupRecord.read(record).takeoverLauncher);
            withdrawn[0] = true; homes.ours = () -> false; });
        assertTrue(withdrawn[0]);
        assertEquals(HomeTakeover.Outcome.RETURNED, r.outcome);
        assertEquals(STOCK, r.after.homePackage());
        assertEquals(Arrays.asList(AdbCommands.enable(STOCK), AdbCommands.setHome(FakeTv.HOME)), tv.changes());
        CleanupRecord rec = CleanupRecord.read(record);
        assertNull(rec.takeoverLauncher);
        assertEquals("the first state stays recorded", CleanupRecord.ENABLED, rec.packages.get(STOCK));
    }

    @Test public void returnNeverEnablesWhatThisFlowDidntTurnOff() throws Exception {
        record.getParentFile().mkdirs();
        Files.write(record.toPath(), fixture("cleanup-record.json").getBytes(StandardCharsets.UTF_8));
        try { HomeTakeover.giveBack(tv, record, CleanupRecord.read(record), null); fail("nothing to give back"); } catch (HomeTakeover.Changed expected) { /* refused */ }
        assertTrue(tv.commands.isEmpty());
    }

    @Test public void homeButtonCheckSaysWhatHappened() {
        HomeTakeover.cancel(app);
        assertNull("nothing pending, nothing said", HomeTakeover.homeShown(app, true));
        HomeTakeover.expect(app);
        assertNull("opened another way inside the window: still waiting", HomeTakeover.homeShown(app, false));
        assertEquals(HomeTakeover.WORKED, HomeTakeover.homeShown(app, true));
        assertTrue(HomeTakeover.arrived(app) > 0);
        assertNull("said once", HomeTakeover.homeShown(app, true));
        HomeTakeover.expect(app);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(HomeTakeover.CONFIRM_MS + 1000));
        assertEquals("the Home button never brought it", HomeTakeover.DIDNT, HomeTakeover.homeShown(app, false));
        assertEquals(0, HomeTakeover.pending(app));
    }

    @Test public void recordRoundTripsTheMarkerAndRefusesADamagedOne() throws Exception {
        String text = fixture("home-takeover-record.json");
        CleanupRecord r = CleanupRecord.parse(text);
        assertEquals(STOCK, r.takeoverLauncher); assertEquals(FakeTv.HOME, r.takeoverPrevious);
        assertEquals(text, r.json());
        assertTrue("the clean-up's own undo leaves the Home alone", r.changedApps().isEmpty());
        assertNull(CleanupRecord.parse(text.replace("\"launcher\": \"com.google.android.tvlauncher\"", "\"launcher\": \"x;reboot\"")));
        r.takeoverLauncher = null; r.takeoverPrevious = null;
        assertFalse("handed back: no marker in the file", r.json().contains("home_takeover"));
    }

    private List<String> disables() { List<String> out = new ArrayList<>(); for (String c : tv.commands) if (c.startsWith("pm disable-user")) out.add(c); return out; }
}
