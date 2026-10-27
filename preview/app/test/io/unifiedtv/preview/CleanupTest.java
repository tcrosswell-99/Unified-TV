package io.unifiedtv.preview;

import io.unifiedtv.adb.AdbCommands;
import io.unifiedtv.adb.AdbException;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

/**
 * The Optional clean-up's rules against a simulated TV (FakeTv): which TVs a tested list covers (and that an unknown one
 * is read-only, AT-32), the deny-list, the read-only check (AT-20), the recovery record written before the first change
 * in tools/recovery.py's format, refusals, and undo. tests/test_cleanup_record.py reads the same fixture with recovery.py.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30)
public class CleanupTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private FakeTv tv;
    private File recordFile;
    private static final String CREATED = "2026-10-27T12:00:00+0000";

    @Before public void setUp() { tv = new FakeTv(); recordFile = CleanupRecord.file(tmp.getRoot()); }

    private Cleanup.Check check() throws Exception { return Cleanup.check(tv, FakeTv.lists(), FakeTv.OWNER, "io.unifiedtv.preview", null); }
    private static String fixture() throws Exception { return new String(Files.readAllBytes(new File(SidebarRuleTest.app(), "test/fixtures/cleanup-record.json").toPath()), StandardCharsets.UTF_8); }

    // ---------------------------------------------------------------- which TV
    @Test public void theOwnersFirmwareHasItsOwnTestedList() {
        CleanupLists.Match m = CleanupLists.match(FakeTv.lists(), FakeTv.OWNER);
        assertEquals(CleanupLists.Level.THIS_FIRMWARE, m.level);
        assertEquals("TCL 50C725K", m.list.title);
        Map<String, CleanupLists.Entry> by = new HashMap<>(); for (CleanupLists.Entry e : m.items) by.put(e.pkg, e);
        assertTrue("from the 50C725K list, on this firmware", by.get("com.tcl.bi").thisFirmware);
        assertEquals("TCL and Google content, ads, promos and telemetry", by.get("com.tcl.bi").group);
        assertEquals("TCL analytics", by.get("com.tcl.bi").note);
        assertFalse("from the 55C725 list: a similar TCL, not this firmware", by.get("com.tcl.waterfall.overseas").thisFirmware);
        assertTrue("the lists' keep notes apply to the whole family", m.keep.contains("com.google.android.tungsten.setupwraith"));
        assertTrue(m.stockLaunchers.contains("com.google.android.tvlauncher"));
        assertTrue(m.stockLaunchers.contains("com.google.android.apps.tv.launcherx"));
    }

    @Test public void theSameFamilyOnAnotherFirmwareIsRiskierAndNeverRecommended() {
        CleanupLists.Match m = CleanupLists.match(FakeTv.lists(), "TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR12:user/release-keys");
        assertEquals(CleanupLists.Level.SAME_FAMILY, m.level);
        for (CleanupLists.Entry e : m.items) { assertFalse(e.pkg, e.thisFirmware); assertFalse(e.pkg, CleanupPolicy.recommended(e)); assertNotEquals(e.pkg, CleanupPolicy.Risk.LOW, CleanupPolicy.risk(e)); }
        assertEquals("the 55C725's product picks its list", "TCL 55C725", CleanupLists.match(FakeTv.lists(), "TCL/G03_4K_GB/BeyondTV4:11/RP1A.200622.001/AR11:user/release-keys").list.title);
        assertEquals("a list's fingerprint without ':user/release-keys' still matches", CleanupLists.Level.THIS_FIRMWARE,
            CleanupLists.match(FakeTv.lists(), "TCL/G03_4K_GB/BeyondTV4:11/RP1A.200622.001/AR09:user/release-keys").level);
    }

    @Test public void anUnknownTvIsReadOnly() throws Exception {
        for (String fp : new String[]{FakeTv.SONY, "TCL/G10/BeyondTV9:12/SP1A.210812.016/X:user/release-keys", "", "TCL/BeyondTV6/BeyondTV4:12/RP1A.200622.001/AR10:user/release-keys"})
            assertEquals(fp, CleanupLists.Level.UNKNOWN, CleanupLists.match(FakeTv.lists(), fp).level);
        assertFalse("no lists at all: unknown", CleanupLists.match("", FakeTv.OWNER).known());
        tv.fingerprint = FakeTv.SONY;
        Cleanup.Check c = Cleanup.check(tv, FakeTv.lists(), FakeTv.SONY, "io.unifiedtv.preview", null);
        assertFalse(c.match.known());
        assertTrue("nothing to offer", c.items.isEmpty());
        assertEquals("only the firmware and ro.adb.secure were read", Arrays.asList(AdbCommands.FINGERPRINT, AdbCommands.ADB_SECURE), tv.commands);
    }

    @Test public void anotherDeviceAnsweringIsRefused() throws Exception {
        tv.fingerprint = "TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR10:user/test-keys";
        try { check(); fail("not this TV"); } catch (AdbException e) { assertEquals(AdbException.Kind.NOT_THIS_TV, e.kind); }
        assertEquals("nothing more was read", Collections.singletonList(AdbCommands.FINGERPRINT), tv.commands);
    }

    // ---------------------------------------------------------------- never offered
    @Test public void theDenyListHoldsWhateverAListSays() {
        CleanupPolicy.Facts f = new CleanupPolicy.Facts();
        f.self = "io.unifiedtv.preview"; f.liveTv = "com.example.livetv"; f.homeOwner = "com.example.home";
        f.homeClaims.add("com.example.claims.home"); f.channelApps.add("com.example.channels"); f.settingsApps.add("com.example.opens.settings");
        f.inputDrivers.add("com.example.hdmi.driver"); f.keep.add("com.tcl.MultiScreenInteraction_TV"); f.stockLaunchers.add("com.example.stock");
        String[] never = {"android", "com.android.systemui", "com.android.shell", "com.android.providers.settings", "com.google.android.permissioncontroller",
            "com.google.android.overlay.modules.ext.services", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.example.home",
            "com.example.claims.home", "com.example.stock", "com.tcl.tv", "com.tcl.tvinput", "com.example.channels", "com.example.hdmi.driver",
            "com.tcl.versionUpdateApp", "com.tcl.ota.service", "com.android.tv.settings", "com.tcl.settings", "com.example.opens.settings",
            "com.tcl.tcl_bt_rcu_service", "com.tcl.autopair", "com.android.bluetooth", "com.google.android.tv.remote.service", "com.tcl.system.server",
            "com.tcl.providers.config", "com.tcl.initsetup", "com.tcl.factory.view", "com.google.android.tungsten.setupwraith", "com.android.wifi.resources",
            "com.tcl.inputmethod.international", "com.google.android.leanback.ime", "com.google.android.marvin.talkback", "com.google.android.tts",
            "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.nordvpn.android", "com.example.livetv",
            "io.unifiedtv.preview", "io.unifiedtv.launcher", "io.unifiedtv.preview.devicecheck", "com.gathin.tvhome", "com.google.android.webview",
            "com.tcl.MultiScreenInteraction_TV", "com.tcl.audio.dolby", "com.android.tv.cec"};
        for (String p : never) assertNotNull("never offered: " + p, CleanupPolicy.blocked(p, f));
        for (String p : new String[]{"com.tcl.bi", "com.tcl.esticker", "com.google.android.feedback", "com.netflix.ninja", "com.tcl.dashboard"})
            assertNull("may be offered: " + p, CleanupPolicy.blocked(p, f));
        assertNotNull("not a package name", CleanupPolicy.blocked("com.tcl.bi;reboot", f));
        assertNull("content providers the tested lists turn off (as recovery.py's CORE_OK)", CleanupPolicy.blocked("com.android.providers.calendar", f));
    }

    @Test public void everyBundledPackageEitherPassesOrSaysWhyNot() {
        CleanupPolicy.Facts f = new CleanupPolicy.Facts();
        CleanupLists.Match m = CleanupLists.match(FakeTv.lists(), FakeTv.OWNER); f.keep.addAll(m.keep); f.stockLaunchers.addAll(m.stockLaunchers);
        for (CleanupLists.Entry e : m.items) { String why = CleanupPolicy.blocked(e.pkg, f); assertTrue(e.pkg, why == null || why.endsWith(".")); }
        for (String kept : m.keep) assertNotNull("the lists' notes say keep: " + kept, CleanupPolicy.blocked(kept, f));
    }

    // ---------------------------------------------------------------- the read-only check
    @Test public void theCheckReadsAndChangesNothing() throws Exception {
        tv.inputDrivers.add("com.tcl.guard"); tv.homeClaims.add("com.tcl.browser");
        Cleanup.Check c = check();
        assertTrue("read-only (AT-20): " + tv.changes(), tv.changes().isEmpty());
        assertEquals(CleanupLists.Level.THIS_FIRMWARE, c.match.level);
        Cleanup.Item bi = c.item("com.tcl.bi");
        assertTrue(bi.canTurnOff()); assertEquals("1.2.10", bi.version); assertTrue("adverts and tracking on this firmware", bi.recommended);
        assertEquals("TCL analytics", bi.title());
        assertEquals("already off before", CleanupRecord.DISABLED, c.item("com.tcl.notereminder").state);
        assertEquals("not on this TV", CleanupRecord.ABSENT, c.item("com.tcl.smartalexa").state);
        assertNotNull("accessibility stays", c.item("com.google.android.marvin.talkback").blocked);
        assertNotNull("the lists' notes keep it", c.item("com.tcl.MultiScreenInteraction_TV").blocked);
        assertNotNull("asks for TV_INPUT_HARDWARE (#321)", c.item("com.tcl.guard").blocked);
        assertNotNull("answers Home on this TV", c.item("com.tcl.browser").blocked);
        Cleanup.Item katniss = c.item("com.google.android.katniss");
        assertTrue(katniss.canTurnOff()); assertFalse("voice: only your choice", katniss.recommended); assertEquals(CleanupPolicy.Risk.HIGH, katniss.risk); assertNotNull(katniss.warning);
        assertFalse("a streaming app is never suggested", c.item("com.netflix.ninja").recommended);
        assertFalse("ro.adb.secure=1", c.openToAll());
        tv.adbSecure = "0"; assertTrue(check().openToAll());
    }

    // ---------------------------------------------------------------- changing, recording, undoing
    @Test public void theRecordIsWrittenBeforeTheFirstChangeInRecoveryPyFormat() throws Exception {
        Cleanup.Check c = check();
        tv.beforeChange = () -> { CleanupRecord r = CleanupRecord.read(recordFile); assertNotNull("the record is on disk before the first change", r);
            assertEquals(CleanupRecord.ENABLED, r.packages.get("com.tcl.bi")); assertEquals(CleanupRecord.ENABLED, r.packages.get("com.tcl.esticker")); };
        List<String> steps = new ArrayList<>(), logged = new ArrayList<>();
        List<String> done = Cleanup.apply(tv, recordFile, c, Arrays.asList("com.tcl.esticker", "com.tcl.bi"), CREATED, (n, t, p) -> steps.add(n + "/" + t), logged::add);
        assertEquals(Arrays.asList("com.tcl.esticker", "com.tcl.bi"), done);
        assertEquals("one by one, in the chosen order", Arrays.asList("pm disable-user --user 0 com.tcl.esticker", "pm disable-user --user 0 com.tcl.bi"), tv.changes());
        assertEquals(Arrays.asList("0/2", "1/2", "2/2"), steps);
        assertEquals("each change is logged", done, logged);
        assertEquals("exactly the text recovery.py writes for this record (tests/test_cleanup_record.py loads it)",
            fixture(), new String(Files.readAllBytes(recordFile.toPath()), StandardCharsets.UTF_8));
        CleanupRecord r = CleanupRecord.read(recordFile);
        assertEquals(Arrays.asList("com.tcl.bi", "com.tcl.esticker"), r.changed());
        assertTrue(r.listText().contains("\ncom.tcl.bi\ncom.tcl.esticker\n"));
        assertEquals(CleanupRecord.DISABLED, Cleanup.states(tv, done).get("com.tcl.bi"));
    }

    @Test public void theFirstRecordWins() throws Exception {
        Cleanup.apply(tv, recordFile, check(), Collections.singletonList("com.tcl.bi"), CREATED, null, null);
        Cleanup.apply(tv, recordFile, check(), Collections.singletonList("com.tcl.esticker"), "2026-10-28T09:00:00+0000", null, null);
        CleanupRecord r = CleanupRecord.read(recordFile);
        assertEquals("still the first record", CREATED, r.created);
        assertEquals(CleanupRecord.ENABLED, r.packages.get("com.tcl.bi"));
        assertEquals("added, as it was before this batch", CleanupRecord.ENABLED, r.packages.get("com.tcl.esticker"));
        r.add(Collections.singletonMap("com.tcl.bi", CleanupRecord.DISABLED));
        assertEquals("an entry is never overwritten", CleanupRecord.ENABLED, r.packages.get("com.tcl.bi"));
    }

    @Test public void nothingChangesWithoutARecord() throws Exception {
        Cleanup.Check c = check();
        assertTrue(new File(tmp.getRoot(), "cleanup").createNewFile());   // a file where the record's folder should be
        try { Cleanup.apply(tv, recordFile, c, Collections.singletonList("com.tcl.bi"), CREATED, null, null); fail("no record, no change"); }
        catch (java.io.IOException expected) { /* the write failed */ }
        assertTrue(tv.changes().isEmpty());
    }

    @Test public void onlyOfferedPackagesCanBeChanged() throws Exception {
        Cleanup.Check c = check();
        for (String p : new String[]{"com.tcl.tv", "com.google.android.tvlauncher", "com.google.android.marvin.talkback", "com.tcl.notereminder", "com.tcl.smartalexa", "com.example.not.listed"}) {
            try { Cleanup.apply(tv, recordFile, c, Collections.singletonList(p), CREATED, null, null); fail("changed " + p); } catch (IllegalArgumentException expected) { /* refused */ }
        }
        try { Cleanup.apply(tv, recordFile, c, Collections.emptyList(), CREATED, null, null); fail("nothing chosen"); } catch (IllegalArgumentException expected) { /* refused */ }
        assertTrue(tv.changes().isEmpty());
        assertFalse("no record either", recordFile.exists());
    }

    @Test public void aTvThatChangedSinceTheCheckIsLeftAlone() throws Exception {
        Cleanup.Check c = check();
        tv.home = "com.tcl.bi/.Home";   // now something on the list answers Home
        try { Cleanup.apply(tv, recordFile, c, Arrays.asList("com.tcl.esticker", "com.tcl.bi"), CREATED, null, null); fail("Home moved"); } catch (IllegalStateException expected) { /* refused */ }
        tv.home = FakeTv.HOME; tv.disabled.add("com.tcl.esticker");
        try { Cleanup.apply(tv, recordFile, check(), Collections.singletonList("com.tcl.bi"), CREATED, null, null); } catch (Exception e) { fail("still fine: " + e); }
        assertEquals(Collections.singletonList("pm disable-user --user 0 com.tcl.bi"), tv.changes());
    }

    @Test public void undoTurnsBackOnOnlyWhatTheCleanupTurnedOff() throws Exception {
        Cleanup.apply(tv, recordFile, check(), Arrays.asList("com.tcl.bi", "com.tcl.esticker"), CREATED, null, null);
        CleanupRecord r = CleanupRecord.read(recordFile);
        r.packages.put("com.tcl.notereminder", CleanupRecord.DISABLED);   // already off before: undo leaves it
        List<String> logged = new ArrayList<>();
        List<String> back = Cleanup.turnBackOn(tv, r, Arrays.asList("com.tcl.bi", "com.tcl.esticker", "com.tcl.notereminder", "com.tcl.tv"), null, logged::add);
        assertEquals(Arrays.asList("com.tcl.bi", "com.tcl.esticker"), back);
        assertEquals(back, logged);
        assertFalse(tv.disabled.contains("com.tcl.bi")); assertFalse(tv.disabled.contains("com.tcl.esticker"));
        assertTrue("left as it was before", tv.disabled.contains("com.tcl.notereminder"));
        assertFalse("never touched", tv.commands.contains("pm enable com.tcl.tv"));
        assertTrue("the record stays (it is the only copy of the state before)", recordFile.exists());
    }

    @Test public void theRecordReadsBackAndRefusesDamage() throws Exception {
        CleanupRecord r = CleanupRecord.parse(fixture());
        assertNotNull(r);
        assertEquals("TESTSERIAL0001", r.serial); assertEquals(FakeTv.HOME, r.home); assertEquals(fixture(), r.json());
        assertNull(CleanupRecord.parse(fixture().replace("\"enabled\"", "\"gone\"")));
        assertNull(CleanupRecord.parse(fixture().replace("com.tcl.bi", "com.tcl.bi;reboot")));
        assertNull(CleanupRecord.parse(fixture().replace("\"version\": 1", "\"version\": 2")));
        assertNull(CleanupRecord.parse("not json"));
        assertNull("recovery.py's placeholder serial is no id", CleanupRecord.usableId("0123456789abcdef"));
        assertNull(CleanupRecord.usableId("unknown")); assertNull(CleanupRecord.usableId("0000")); assertEquals("ABC123", CleanupRecord.usableId(" ABC123\n"));
    }

    @Test public void thePortCheckSeesOpenAndClosed() throws Exception {
        try (ServerSocket open = new ServerSocket(0, 1, io.unifiedtv.adb.AdbLink.loopback())) {
            assertFalse("something answers", Cleanup.portClosed(open.getLocalPort(), 2000));
            int port = open.getLocalPort(); open.close();
            assertTrue("nothing answers", Cleanup.portClosed(port, 2000));
        }
    }

    // ---------------------------------------------------------------- review fixes (regressions)
    @Test public void androidsChooserIsNotRecordedAsHome() throws Exception {
        String chooser = "android/com.android.internal.app.ResolverActivity";
        assertNull(Cleanup.homeComponent("priority=0\n" + chooser + "\n"));
        assertNull(Cleanup.homeComponent("android\n"));
        assertEquals(FakeTv.HOME, Cleanup.homeComponent(FakeTv.HOME + "\n"));
        tv.home = chooser;
        Cleanup.apply(tv, recordFile, check(), Collections.singletonList("com.tcl.bi"), CREATED, null, null);
        CleanupRecord r = CleanupRecord.read(recordFile);
        assertNull("no Home to replay", r.home);
        assertTrue(r.json().contains("\"home\": null"));
        CleanupRecord old = CleanupRecord.parse(fixture().replace(FakeTv.HOME, chooser));
        assertNotNull("an older record with the chooser still reads", old);
        assertNull(old.home);
        assertNull("a damaged Home is still refused", CleanupRecord.parse(fixture().replace(FakeTv.HOME, "not a component")));
    }

    @Test public void successIsWhatTheTvReportsNotASubstring() throws Exception {
        Cleanup.apply(tv, recordFile, check(), Collections.singletonList("com.tcl.messagebox"), CREATED, null, null);   // an earlier batch
        tv.failDisable.add("com.tcl.esticker");   // pm's error mentions "disabled", and the app stays on
        List<String> logged = new ArrayList<>();
        List<String> done = Cleanup.apply(tv, recordFile, check(), Arrays.asList("com.tcl.bi", "com.tcl.esticker"), CREATED, null, logged::add);
        assertEquals(Collections.singletonList("com.tcl.bi"), done);
        assertEquals("only real changes are logged", done, logged);
        CleanupRecord r = CleanupRecord.read(recordFile);
        assertFalse("never changed, so not in the record or the phone's undo list", r.packages.containsKey("com.tcl.esticker"));
        assertFalse(r.listText().contains("com.tcl.esticker"));
        assertTrue("earlier entries are untouched", r.packages.containsKey("com.tcl.messagebox"));
        assertTrue(Cleanup.said("Package com.tcl.bi new state: disabled-user\n", "com.tcl.bi", "disabled-user"));
        assertFalse(Cleanup.said("Error: com.tcl.bi not disabled-user\n", "com.tcl.bi", "disabled-user"));
        assertFalse(Cleanup.said("Package com.tcl.bi new state: enabled\n", "com.tcl.bi", "disabled-user"));
        tv.failEnable.add("com.tcl.bi");   // its error mentions "enabled", and the app stays off
        assertTrue("not turned back on", Cleanup.turnBackOn(tv, r, Arrays.asList("com.tcl.bi", "com.tcl.messagebox"), null, null).equals(Collections.singletonList("com.tcl.messagebox")));
        assertTrue(tv.disabled.contains("com.tcl.bi"));
    }

    @Test public void webviewsAreNeverOffered() {
        CleanupPolicy.Facts f = new CleanupPolicy.Facts();
        for (String p : new String[]{"com.tcl.android.webview", "org.chromium.webview_shell", "com.google.android.webview", "com.android.webview", "com.example.WebViewProvider"})
            assertNotNull(p, CleanupPolicy.blocked(p, f));
    }

    @Test public void recommendationsStayWhileTheStockHomeIsOn() throws Exception {
        tv.installed.add(CleanupPolicy.RECOMMENDATIONS);
        assertNotNull("the stock launcher is on", check().item(CleanupPolicy.RECOMMENDATIONS).blocked);
        tv.disabled.add("com.google.android.tvlauncher");
        Cleanup.Item i = check().item(CleanupPolicy.RECOMMENDATIONS);
        assertNull("the stock Home is off: offered", i.blocked);
        assertFalse("from a sibling list: never recommended", i.recommended);
        assertEquals(CleanupPolicy.Risk.HIGH, i.risk);
        Cleanup.Check c = check();
        tv.disabled.remove("com.google.android.tvlauncher");   // the stock Home came back before the confirm
        try { Cleanup.apply(tv, recordFile, c, Collections.singletonList(CleanupPolicy.RECOMMENDATIONS), CREATED, null, null); fail("re-checked"); } catch (IllegalStateException expected) { /* refused */ }
        assertTrue(tv.changes().isEmpty());
    }

    @Test public void theActiveScreensaverIsNeverOffered() throws Exception {
        String dream = "com.google.android.apps.tv.dreamx";
        tv.installed.add(dream);
        tv.screensaver = dream + "/.DreamService,com.google.android.backdrop/.Backdrop";
        assertNotNull("it is the screensaver now", check().item(dream).blocked);
        assertTrue(tv.commands.contains(io.unifiedtv.adb.AdbCommands.SCREENSAVER));
        assertEquals(new LinkedHashSet<>(Arrays.asList(dream, "com.google.android.backdrop")), Cleanup.screensavers(tv.screensaver));
        assertTrue(Cleanup.screensavers("null").isEmpty());
        tv.screensaver = "com.google.android.backdrop/.Backdrop";
        assertNull("another screensaver: offered", check().item(dream).blocked);
        assertTrue(io.unifiedtv.adb.AdbCommands.allowed(io.unifiedtv.adb.AdbCommands.SCREENSAVER));
        assertFalse(io.unifiedtv.adb.AdbCommands.allowed("settings put secure screensaver_components x/.Y"));
    }

    @Test public void siblingListsAreOptionalOnTheOwnersFirmware() throws Exception {
        Cleanup.Check c = check();
        int siblings = 0;
        for (Cleanup.Item i : c.items) if (!i.entry.thisFirmware) { siblings++; assertFalse(i.pkg(), i.recommended); assertNotEquals(i.pkg(), CleanupPolicy.Risk.LOW, i.risk); }
        assertTrue("55C725 and 65C725 entries are still listed", siblings > 0);
    }

    // ---------------------------------------------------------------- the phone's undo files
    @Test public void thePhoneGetsTheRecordAndTheListAndNothingElse() throws Exception {
        String record = fixture(), list = CleanupRecord.parse(record).listText();
        List<String> said = new ArrayList<>();
        CleanupPhone.Flow flow = new CleanupPhone.Flow(() -> record, () -> list, said::add);
        PhoneSetupServer.Request r = new PhoneSetupServer.Request("GET", "/t/", new HashMap<>(), new HashMap<>(), new HashMap<>(), null);
        r.session = "http://192.0.2.1:50000/t";
        String page = flow.handle("", r).html;
        assertTrue(page.contains("/t/cleanup/record")); assertTrue(page.contains("/t/cleanup/list"));
        assertTrue("says the record holds the serial", page.contains("serial number"));
        assertFalse("no script", page.contains("<script"));
        PhoneSetupServer.Response file = flow.handle(CleanupPhone.RECORD_ACTION, r);
        assertEquals(CleanupRecord.FILE, file.filename); assertEquals(record, file.html); assertEquals(CleanupPhone.RECORD_TYPE, file.type);
        assertEquals(CleanupRecord.LIST, flow.handle(CleanupPhone.LIST_ACTION, r).filename);
        assertEquals(Collections.singletonList(CleanupPhone.SAVED), said);
        assertNull("anything else: not found", flow.handle("cleanup/key", r));
        assertNull("no record yet: not found", new CleanupPhone.Flow(() -> null, () -> null, said::add).handle(CleanupPhone.RECORD_ACTION, r));
    }
}
