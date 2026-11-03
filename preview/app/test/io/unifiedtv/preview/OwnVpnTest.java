package io.unifiedtv.preview;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowToast;
import javax.crypto.KeyGenerator;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/**
 * OD-29 (owner, 3 November 2026: "Unified TV runs the VPN itself"), with fakes only: a fake NordVPN (NordLynxTest's), a
 * fake tunnel and a fake Android consent. No network, no real account, key or server. Both flavours run this class.
 */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk = 30, qualifiers = "w960dp-h540dp-land-xhdpi")
public class OwnVpnTest {
    static final String TOKEN = NordLynxTest.TOKEN, KEY = NordLynxTest.PRIVATE;
    private Context app() { return RuntimeEnvironment.getApplication(); }
    private static void idle() { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); }

    /** The tunnel, faked: what it was given, and whether it is up. */
    static final class FakeTunnel implements VpnTunnel {
        final List<String> configs = new ArrayList<>(); boolean up, fails, fallsAfterUp; int downs; long handshakeAt = 1; Runnable dropped;
        @Override public void up(String config) throws Exception { if (fails) throw new Exception("no tunnel"); configs.add(config); up = !fallsAfterUp; }
        @Override public long handshake() { return up ? handshakeAt : 0; }
        @Override public void down() { if (up) downs++; up = false; }
        @Override public boolean isUp() { return up; }
        @Override public void onDropped(Runnable r) { dropped = r; }
    }
    /** Android's consent, faked: the dialog's intent until the household says OK. */
    static final class FakeConsent implements OwnVpn.Consent {
        boolean given; int asked;
        @Override public Intent prepare(Context c) { asked++; return given ? null : new Intent("android.net.vpn.SETUP_VPN"); }
    }
    private NordLynxTest.FakeNord nord; private FakeTunnel tunnel; private FakeConsent consent; private final List<Runnable> queued = new ArrayList<>();

    @Before public void setUp() throws Exception {
        Vault.testKey = KeyGenerator.getInstance("AES").generateKey();
        for (String p : new String[]{OwnVpn.PREFS, "vault", "unified-tv-preview", EventLog.PREFS}) app().getSharedPreferences(p, 0).edit().clear().commit();
        OwnVpn.resetForTests(); LiveTvProtection.forTests = null; LiveTvProtection.last = null; ShadowDialog.reset();
        nord = new NordLynxTest.FakeNord().on(NordLynx.API + NordLynx.CREDENTIALS, 200, NordLynxTest.CREDENTIALS)
            .on(NordLynx.API + NordLynx.COUNTRIES, 200, NordLynxTest.COUNTRIES).on(NordLynx.API + NordLynx.RECOMMENDATIONS, 200, NordLynxTest.RECOMMENDED);
        tunnel = new FakeTunnel(); consent = new FakeConsent();
        OwnVpn.http = nord; OwnVpn.factory = c -> tunnel; OwnVpn.consent = consent; OwnVpn.worker = Runnable::run;
    }
    @After public void tearDown() { ShadowLog.stream = null; OwnVpn.resetForTests(); Vault.testKey = null; LiveTvProtection.forTests = null; }

    private Activity screen() { return Robolectric.buildActivity(Activity.class).setup().get(); }
    private static String words(View v) { StringBuilder b = new StringBuilder(); if (v instanceof TextView) b.append(((TextView) v).getText()).append(' '); if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i))); return b.toString(); }
    private static Button find(View v, String text) { if (v instanceof Button && ((Button) v).getText().toString().equals(text)) return (Button) v; if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = find(((ViewGroup) v).getChildAt(i), text); if (b != null) return b; } return null; }
    private void keySaved() throws Exception { OwnVpn.save(app(), KEY); }
    private PhoneFlows.Form phone(List<String> tv) { return new PhoneFlows.Form(PhoneService.of(app(), PhoneService.NORDLYNX), tv::add); }
    private static PhoneSetupServer.Request post(String token) { return PhoneFlowsTest.req("POST", PhoneFlowsTest.map("key", token), null); }

    // --- the token on the phone: only the key is kept, and nothing secret is ever said back
    @Test public void theTokenIsCheckedOnceAndOnlyTheKeyIsSaved() throws Exception {
        List<String> tv = new ArrayList<>();
        PhoneSetupServer.Response page = phone(tv).handle("save", post(TOKEN));
        assertTrue(page.success); assertTrue(OwnVpn.saved(app()));
        JSONObject record = new Vault(app()).read(OwnVpn.VAULT);
        assertEquals("only the key, under one name", Collections.singletonList("key"), keys(record)); assertEquals(KEY, record.getString("key"));
        String raw = app().getSharedPreferences("vault", 0).getString(OwnVpn.VAULT, "");
        assertFalse("encrypted", raw.contains(KEY)); assertFalse(raw.contains(TOKEN));
        for (String s : tv) { assertFalse(s, s.contains(TOKEN)); assertFalse(s, s.contains(KEY)); }
        assertFalse(page.html.contains(TOKEN)); assertFalse(page.html.contains(KEY));
        assertEquals("one request, to NordVPN's credentials", 1, nord.urls.size());
    }
    @Test public void aRefusedTokenSaysSoAndSavesNothing() throws Exception {
        nord.on(NordLynx.API + NordLynx.CREDENTIALS, 401, "");
        List<String> tv = new ArrayList<>();
        PhoneSetupServer.Response page = phone(tv).handle("save", post(TOKEN));
        assertFalse(page.success); assertFalse(OwnVpn.saved(app()));
        assertTrue(String.join("|", tv), tv.contains("NordVPN didn't accept that token. Your phone shows what to try."));
        assertTrue(page.html, page.html.contains("NordVPN didn&#39;t accept that token") || page.html.contains("NordVPN didn't accept that token"));
        assertFalse("never echoed", page.html.contains(TOKEN));
    }
    @Test public void offlineSaysTheTvCouldntReachNordVpn() throws Exception {
        nord.offline = true; List<String> tv = new ArrayList<>();
        PhoneSetupServer.Response page = phone(tv).handle("save", post(TOKEN));
        assertFalse(page.success); assertFalse(OwnVpn.saved(app()));
        assertTrue(String.join("|", tv), tv.contains("The TV couldn't reach NordVPN. Your phone shows what to try."));
        assertFalse(page.html.contains(TOKEN));
    }
    @Test public void thePhoneStepsSayWhereTheTokenIs() throws Exception {
        PhoneService s = PhoneService.of(null, PhoneService.NORDLYNX);
        String steps = String.join(" ", SetupPages.steps(s)), page = phone(new ArrayList<>()).handle("", PhoneFlowsTest.req("GET", null, null)).html;
        assertTrue(steps.contains("Set up NordVPN manually")); assertTrue(steps.contains("Generate new token"));
        assertTrue(page.contains("https://my.nordaccount.com/")); assertTrue(page.contains("type=\"password\"") || page.contains("autocomplete=\"off\""));
        assertEquals(SettingsActivity.LIVE_TV, SetupPages.category(SettingsActivity.PHONE_NORDLYNX, -1));
    }
    private static List<String> keys(JSONObject o) { List<String> k = new ArrayList<>(); for (Iterator<String> i = o.keys(); i.hasNext(); ) k.add(i.next()); return k; }

    // --- the shield: one press
    @Test public void withoutAKeyThePressOpensThePhoneStepWithOneLine() {
        Activity a = screen();
        OwnVpn.press(a);
        assertEquals(OwnVpn.NO_KEY, ShadowToast.getTextOfLatestToast());
        Intent next = Shadows.shadowOf(a).getNextStartedActivity();
        assertNotNull(next); assertEquals(SettingsActivity.class.getName(), next.getComponent().getClassName()); assertEquals(SettingsActivity.PHONE_NORDLYNX, next.getStringExtra("task"));
        assertTrue("no tunnel", tunnel.configs.isEmpty());
    }
    @Test public void androidsConsentIsExplainedAndAskedOnlyOnce() throws Exception {
        keySaved(); Activity a = screen();
        OwnVpn.press(a); idle();
        Dialog sheet = ShadowDialog.getLatestDialog(); assertNotNull("plain words first", sheet); assertSame(sheet, OwnVpn.explanation());
        assertTrue(words(sheet.getWindow().getDecorView()).contains(OwnVpn.EXPLAIN));
        assertNull("Android's dialog not yet", Shadows.shadowOf(a).getNextStartedActivityForResult());
        find(sheet.getWindow().getDecorView(), "Continue").performClick();
        Intent ask = Shadows.shadowOf(a).getNextStartedActivityForResult().intent;
        assertEquals("android.net.vpn.SETUP_VPN", ask.getAction());
        consent.given = true; OwnVpn.resumed(a); idle();     // the household chose OK; the screen comes back
        assertEquals(OwnVpn.Phase.ON, OwnVpn.phase()); assertTrue(tunnel.up);
        OwnVpn.press(a); idle(); assertEquals("a second press disconnects", OwnVpn.Phase.OFF, OwnVpn.phase()); assertFalse(tunnel.up);
        ShadowDialog.reset();
        OwnVpn.press(a); idle();
        assertEquals("and the next connects at once", OwnVpn.Phase.ON, OwnVpn.phase());
        assertNull("no explanation again", ShadowDialog.getLatestDialog()); assertNull("no dialog again", Shadows.shadowOf(a).getNextStartedActivityForResult());
    }
    @Test public void consentRefusedLeavesItOffAndSaysWhy() throws Exception {
        keySaved(); Activity a = screen();
        OwnVpn.press(a); idle();
        find(ShadowDialog.getLatestDialog().getWindow().getDecorView(), "Continue").performClick();
        OwnVpn.resumed(a); idle();
        assertEquals(OwnVpn.Phase.FAILED, OwnVpn.phase()); assertEquals(OwnVpn.NOT_ALLOWED, OwnVpn.problem()); assertTrue(tunnel.configs.isEmpty());
        assertEquals(OwnVpn.NOT_ALLOWED, ShadowToast.getTextOfLatestToast());
    }
    @Test public void theShieldShowsConnectingThenOnWithTheFlagOrAFailure() throws Exception {
        keySaved(); consent.given = true; OwnVpn.choose(app(), "GB");
        OwnVpn.worker = queued::add;
        List<OwnVpn.Phase> seen = new ArrayList<>(); OwnVpn.listen(() -> seen.add(OwnVpn.phase()));
        Activity a = screen();
        OwnVpn.press(a); idle();
        assertEquals(OwnVpn.Phase.CONNECTING, OwnVpn.phase());
        assertEquals("VPN ◌ Connecting…", OwnVpn.shieldWords());
        assertEquals(HomeUtilities.DIM, HomeUtilities.vpnLook(VpnGate.State.DISCONNECTED, OwnVpn.phase()).alpha); assertEquals(HomeUtilities.Mark.DOT, HomeUtilities.vpnLook(VpnGate.State.DISCONNECTED, OwnVpn.phase()).mark);
        OwnVpn.press(a); assertEquals("one connection at a time", OwnVpn.CONNECTING_WORDS, ShadowToast.getTextOfLatestToast()); assertEquals(1, queued.size());
        queued.remove(0).run(); idle();
        assertEquals(OwnVpn.Phase.ON, OwnVpn.phase()); assertEquals(Arrays.asList(OwnVpn.Phase.CONNECTING, OwnVpn.Phase.ON), seen);
        assertEquals("GB", OwnVpn.country()); assertEquals("United Kingdom #9001", OwnVpn.server());
        assertEquals("VPN ● Connected · Unified TV · United Kingdom", OwnVpn.shieldWords());
        HomeUtilities.Look on = HomeUtilities.vpnLook(VpnGate.State.DISCONNECTED, OwnVpn.phase()); assertEquals(HomeUtilities.BRIGHT, on.alpha); assertEquals(HomeUtilities.Mark.TICK, on.mark);
        assertTrue(OwnVpn.status(app()).startsWith("On · United Kingdom #9001 · connected since "));
        // A failure: plain words and a dim shield with the slash.
        OwnVpn.worker = Runnable::run; OwnVpn.disconnect(app(), null); idle(); tunnel.fails = true;
        OwnVpn.press(a); idle();
        assertEquals(OwnVpn.Phase.FAILED, OwnVpn.phase()); assertEquals(OwnVpn.NO_TUNNEL, OwnVpn.problem()); assertEquals("VPN ○ Couldn't connect", OwnVpn.shieldWords());
        assertEquals(HomeUtilities.Mark.OFF, HomeUtilities.vpnLook(VpnGate.State.DISCONNECTED, OwnVpn.phase()).mark); assertNull(OwnVpn.country());
        tunnel.fails = false; nord.offline = true; OwnVpn.connect(app(), null); idle();
        assertEquals(OwnVpn.OFFLINE, OwnVpn.problem());
    }
    @Test public void theShieldSaysWhoseVpnItIs() {
        assertEquals("VPN ● Connected · NordVPN's app", OwnVpn.shieldWords(OwnVpn.Phase.OFF, null, VpnGate.State.CONNECTED, Boolean.TRUE));
        assertEquals("VPN ● Connected · another app", OwnVpn.shieldWords(OwnVpn.Phase.OFF, null, VpnGate.State.CONNECTED, null));
        assertEquals("VPN ○ Disconnected", OwnVpn.shieldWords(OwnVpn.Phase.OFF, null, VpnGate.State.DISCONNECTED, null));
        assertEquals("VPN ● Connected · Unified TV", OwnVpn.shieldWords(OwnVpn.Phase.ON, null, VpnGate.State.CONNECTED, Boolean.TRUE));
    }

    // --- the country, and the configuration the tunnel gets
    @Test public void theChosenCountryIsKeptAndAskedFor() throws Exception {
        keySaved(); consent.given = true;
        assertEquals("Fastest by default", "", OwnVpn.chosen(app())); assertEquals(OwnVpn.FASTEST, OwnVpn.countryWords(OwnVpn.chosen(app())));
        OwnVpn.connect(app(), null); idle();
        assertTrue(nord.urls.toString(), nord.urls.get(nord.urls.size() - 1).endsWith("identifier%5D=wireguard_udp&limit=5"));
        OwnVpn.choose(app(), "de"); idle();   // on: it moves at once
        assertEquals("DE", app().getSharedPreferences(OwnVpn.PREFS, 0).getString(OwnVpn.COUNTRY, ""));
        assertTrue(nord.urls.toString(), nord.urls.get(nord.urls.size() - 1).contains("filters%5Bcountry_id%5D=81&"));
        assertEquals("the old server went down first", 1, tunnel.downs); assertEquals(OwnVpn.Phase.ON, OwnVpn.phase());
        int asked = nord.urls.size(); OwnVpn.connect(app(), null); idle();
        assertEquals("the week's country list is reused", asked + 1, nord.urls.size());
        assertEquals("DE", OwnVpn.chosen(app()));
        assertTrue(OwnVpn.countryWords("DE").endsWith("Germany"));
        String config = tunnel.configs.get(tunnel.configs.size() - 1);
        assertTrue(config.contains("PrivateKey = " + KEY)); assertTrue(config.contains("Address = 10.5.0.2/32")); assertTrue(config.contains("DNS = 103.86.96.100, 103.86.99.100"));
        assertTrue(config.contains("PersistentKeepalive = 25")); assertTrue(config.contains(":51820"));
        String allowed = config.substring(config.indexOf("AllowedIPs = ") + 13, config.indexOf('\n', config.indexOf("AllowedIPs = ")));
        assertFalse("the home network stays out", NordLynxTest.covered(Arrays.asList(allowed.split(", ")), "192.168.1.20"));
    }

    // --- Live TV (OD-26 as amended): Unified TV's own tunnel counts, and it knows when it drops
    @Test public void liveTvOpensWithUnifiedTvsOwnTunnelUp() throws Exception {
        keySaved(); consent.given = true;
        assertNotEquals(LiveTvProtection.State.OPEN, LiveTvProtection.now(app()));
        OwnVpn.connect(app(), null); idle();
        assertNotEquals("Unified TV's own word alone never opens Live TV: Android must report the VPN", LiveTvProtection.State.OPEN, LiveTvProtection.now(app()));
        android.net.Network vpn = androidVpn();
        assertEquals("open, even without NordVPN's app", LiveTvProtection.State.OPEN, LiveTvProtection.now(app()));
        assertEquals("Unified TV's NordVPN connection is on (United Kingdom). If it drops, Unified TV will cover Live TV.", LiveTvProtection.message(LiveTvProtection.State.OPEN));
        assertFalse("its own VPN service isn't 'another VPN app'", LiveTvProtection.vpnAppsDetail(app()).counted.contains(app().getPackageName()));
        tunnel.dropped.run(); idle();   // Android took it away (another VPN app connected)
        assertEquals(OwnVpn.Phase.OFF, OwnVpn.phase()); assertEquals(OwnVpn.DROPPED, OwnVpn.problem());
        assertTrue(LiveTvProtection.dropped(LiveTvProtection.now(app())));
        assertFalse("never fights for the VPN", app().getSharedPreferences(OwnVpn.PREFS, 0).getBoolean(OwnVpn.WANTED, true));
        Shadows.shadowOf(app().getSystemService(android.net.ConnectivityManager.class)).removeNetwork(vpn);
    }
    /** Android reports a VPN network, as it does while any VPN (Unified TV's included) is up. */
    private android.net.Network androidVpn() {
        android.net.ConnectivityManager cm = app().getSystemService(android.net.ConnectivityManager.class);
        android.net.Network n = org.robolectric.shadows.ShadowNetwork.newInstance(4242);
        android.net.NetworkCapabilities caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance();
        Shadows.shadowOf(caps).addTransportType(android.net.NetworkCapabilities.TRANSPORT_VPN);
        Shadows.shadowOf(cm).addNetwork(n, org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED, android.net.ConnectivityManager.TYPE_VPN, 0, true, android.net.NetworkInfo.State.CONNECTED));
        Shadows.shadowOf(cm).setNetworkCapabilities(n, caps);
        return n;
    }
    @Test public void aNewNetworkBringsItBackOnlyWhenWantedAndAllowed() {
        assertTrue(OwnVpn.reconnects(true, OwnVpn.Phase.FAILED, true, true)); assertTrue(OwnVpn.reconnects(true, OwnVpn.Phase.OFF, true, true));
        assertFalse(OwnVpn.reconnects(false, OwnVpn.Phase.OFF, true, true)); assertFalse(OwnVpn.reconnects(true, OwnVpn.Phase.ON, true, true));
        assertFalse(OwnVpn.reconnects(true, OwnVpn.Phase.CONNECTING, true, true)); assertFalse(OwnVpn.reconnects(true, OwnVpn.Phase.OFF, false, true));
        assertFalse("another VPN app has Android's consent now", OwnVpn.reconnects(true, OwnVpn.Phase.OFF, true, false));
    }
    @Test public void theTvTurningOnConnectsWhenHomeComesToTheFrontAndOnlyWhenAskedTo() throws Exception {
        keySaved(); consent.given = true; int[] finished = {0};
        OwnVpn.boot(app(), () -> finished[0]++); OwnVpn.catchUp(app()); idle();
        assertEquals(1, finished[0]); assertEquals("off by default", OwnVpn.Phase.OFF, OwnVpn.phase());
        OwnVpn.atStart(app(), true);
        OwnVpn.boot(app(), () -> finished[0]++); idle();
        assertEquals("the receiver never waits", 2, finished[0]); assertEquals("nothing from the background", OwnVpn.Phase.OFF, OwnVpn.phase());
        OwnVpn.catchUp(app()); idle();   // Home comes to the front
        assertEquals(OwnVpn.Phase.ON, OwnVpn.phase());
        OwnVpn.catchUp(app()); idle(); assertEquals("once per start", 1, tunnel.configs.size());
    }
    /** Review finding 2: a start-up while connecting or on finishes at once, and never touches the running tunnel. */
    @Test public void bootNeverWaitsOnOrTearsDownARunningTunnel() throws Exception {
        keySaved(); consent.given = true; OwnVpn.atStart(app(), true);
        OwnVpn.worker = queued::add; OwnVpn.connect(app(), null);
        int[] finished = {0}; OwnVpn.boot(app(), () -> finished[0]++);
        assertEquals(1, finished[0]); assertEquals(0L, app().getSharedPreferences(OwnVpn.PREFS, 0).getLong(OwnVpn.BOOT_PENDING, 0));
        queued.remove(0).run(); idle(); assertEquals(OwnVpn.Phase.ON, OwnVpn.phase());
        OwnVpn.boot(app(), () -> finished[0]++); OwnVpn.catchUp(app()); idle();
        assertEquals(2, finished[0]); assertTrue(queued.isEmpty()); assertEquals(0, tunnel.downs); assertEquals(1, tunnel.configs.size());
    }
    /** Review finding 2: a second caller while connecting gets the same attempt's answer (it is never dropped). */
    @Test public void everyoneWaitingGetsTheAnswer() throws Exception {
        keySaved(); consent.given = true; OwnVpn.worker = queued::add;
        List<Boolean> answers = new ArrayList<>();
        OwnVpn.connect(app(), answers::add); OwnVpn.connect(app(), answers::add);
        assertEquals("one attempt", 1, queued.size());
        queued.remove(0).run(); idle();
        assertEquals(Arrays.asList(true, true), answers);
    }
    /** Review finding 1: a drop or an Off while connecting is never overwritten by the attempt finishing. */
    @Test public void aDropOrOffWhileConnectingWins() throws Exception {
        keySaved(); consent.given = true;
        OwnVpn.connect(app(), null); idle(); OwnVpn.disconnect(app(), null); idle();   // the tunnel exists now
        OwnVpn.worker = queued::add; List<Boolean> answers = new ArrayList<>();
        OwnVpn.connect(app(), answers::add);
        tunnel.dropped.run(); idle();    // e.g. the old VPN service's onDestroy turning the new tunnel off
        queued.remove(0).run(); idle();
        assertEquals(OwnVpn.Phase.OFF, OwnVpn.phase()); assertEquals(OwnVpn.DROPPED, OwnVpn.problem()); assertEquals(Collections.singletonList(false), answers);
        assertNotEquals(LiveTvProtection.State.OPEN, LiveTvProtection.now(app()));
        OwnVpn.connect(app(), null); OwnVpn.disconnect(app(), null);
        while (!queued.isEmpty()) queued.remove(0).run();
        idle();
        assertEquals("Off pressed while connecting", OwnVpn.Phase.OFF, OwnVpn.phase()); assertFalse(tunnel.up);
    }
    /** Review finding 1: a tunnel that falls straight after coming up is not ON. */
    @Test public void aTunnelThatFallsAtOnceIsNotOn() throws Exception {
        keySaved(); consent.given = true; tunnel.fallsAfterUp = true;
        OwnVpn.connect(app(), null); idle();
        assertEquals(OwnVpn.Phase.FAILED, OwnVpn.phase()); assertNull(OwnVpn.country());
    }
    /** Review finding 4: no handshake with NordVPN within the wait is a failure, said plainly, and the tunnel goes down. */
    @Test public void noHandshakeIsNotConnected() throws Exception {
        keySaved(); consent.given = true; tunnel.handshakeAt = 0; OwnVpn.HANDSHAKE_MS = 50;
        OwnVpn.connect(app(), null); idle();
        assertEquals(OwnVpn.Phase.FAILED, OwnVpn.phase()); assertEquals(OwnVpn.NO_ANSWER, OwnVpn.problem()); assertFalse(tunnel.up);
        assertEquals("NordVPN didn't answer — check your NordVPN subscription, or paste a new token.", OwnVpn.NO_ANSWER);
        tunnel.handshakeAt = 1; OwnVpn.connect(app(), null); idle(); assertEquals(OwnVpn.Phase.ON, OwnVpn.phase());
    }
    /** Review finding 5: a new network while Unified TV is in the background waits for Home; start-up is offered only where it can work. */
    @Test public void aBackgroundReconnectWaitsForHome() throws Exception {
        keySaved(); consent.given = true; nord.offline = true;
        OwnVpn.connect(app(), null); idle(); assertEquals(OwnVpn.Phase.FAILED, OwnVpn.phase()); assertTrue(OwnVpn.watching());
        nord.offline = false; assertFalse(OwnVpn.foreground());
        OwnVpn.look(); idle();
        assertTrue(OwnVpn.deferred()); assertEquals("nothing started from the background", OwnVpn.Phase.FAILED, OwnVpn.phase());
        OwnVpn.catchUp(app()); idle();
        assertEquals(OwnVpn.Phase.ON, OwnVpn.phase()); assertFalse(OwnVpn.deferred());
        assertFalse("neither the Home screen nor 'Open Unified TV when the TV turns on'", OwnVpn.atStartOffered(app()));
    }
    /** Review finding 6: Android's immediate onAvailable for networks already there is not a new network. */
    @Test public void onlyANewNetworkCounts() {
        OwnVpn.Seen seen = new OwnVpn.Seen(Collections.singletonList("wifi"));
        assertFalse(seen.arrived("wifi")); assertTrue(seen.arrived("ethernet")); assertFalse(seen.arrived("ethernet"));
        seen.lost("ethernet"); assertTrue("back after it was lost", seen.arrived("ethernet"));
    }
    /** Review finding 3: in Kids nothing turns Unified TV's VPN on or off without the adult PIN. */
    @Test public void kidsCantTurnItOnOrOff() throws Exception {
        keySaved(); consent.given = true; OwnVpn.connect(app(), null); idle(); assertEquals(OwnVpn.Phase.ON, OwnVpn.phase());
        app().getSharedPreferences("unified-tv-preview", 0).edit().putString("pin", Pins.record("2468")).putString(Profiles.PREF, Profiles.CHILD).commit();
        try (ActivityController<VpnActivity> c = Robolectric.buildActivity(VpnActivity.class).setup()) {
            String all = words(c.get().body);
            assertNull(find(c.get().body, "Use NordVPN's own app instead")); assertNull(find(c.get().body, "VPN")); assertFalse(all, all.contains("Forget the NordVPN key"));
            OwnVpn.press(c.get()); idle(); assertEquals(OwnVpn.KIDS, ShadowToast.getTextOfLatestToast()); assertEquals(OwnVpn.Phase.ON, OwnVpn.phase());
        }
        OwnVpn.disconnect(app(), null); idle();
        Activity a = screen();
        OwnVpn.turnOn(a, null); idle();   // the Live TV sheet's Connect and the cover's Reconnect come here
        assertEquals("the adult PIN first", OwnVpn.Phase.OFF, OwnVpn.phase());
        android.app.AlertDialog pin = (android.app.AlertDialog) org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(pin);
        Pins.type(pin, "2468"); idle();
        assertEquals(OwnVpn.Phase.ON, OwnVpn.phase());
    }
    /** Review finding 8: the explanation sheet isn't kept once it closes. */
    @Test public void theExplanationIsNotKeptAfterItCloses() throws Exception {
        keySaved(); Activity a = screen();
        OwnVpn.press(a); idle(); assertNotNull(OwnVpn.explanation());
        find(OwnVpn.explanation().getWindow().getDecorView(), "Cancel").performClick(); idle();
        assertNull(OwnVpn.explanation()); assertEquals(OwnVpn.Phase.OFF, OwnVpn.phase());
    }
    /** Review findings 7 and 10: plain words about names on the home network, and the phone page doesn't say the token is kept. */
    @Test public void theWordsAreHonest() throws Exception {
        assertTrue(OwnVpn.LAN.contains(".local names"));
        String page = phone(new ArrayList<>()).handle("", PhoneFlowsTest.req("GET", null, null)).html;
        assertFalse(page, page.contains("never shown again")); assertTrue(page, page.contains("the token is not kept"));
        String tmdb = new PhoneFlows.Form(PhoneService.tmdb(v -> PhoneFlows.Verdict.OK), x -> {}).handle("", PhoneFlowsTest.req("GET", null, null)).html;
        assertTrue("other keys still say so", tmdb.contains("It is kept encrypted on the TV and never shown again."));
    }

    // --- secrets: never in logs, diagnostics, the event log, exports or the shield's words
    @Test public void noSecretInLogsDiagnosticsOrExports() throws Exception {
        java.io.ByteArrayOutputStream logged = new java.io.ByteArrayOutputStream(); ShadowLog.stream = new java.io.PrintStream(logged, true, "UTF-8");   // every android.util.Log line
        List<String> tv = new ArrayList<>();
        assertTrue(phone(tv).handle("save", post(TOKEN)).success);
        consent.given = true; OwnVpn.connect(app(), null); idle(); OwnVpn.choose(app(), "GB"); idle();
        List<String> seen = new ArrayList<>(tv);
        for (EventLog.Event e : EventLog.recent(app())) seen.add(e.words);
        seen.add(logged.toString("UTF-8"));
        seen.add(OwnVpn.diagnostics(app())); seen.add(OwnVpn.status(app())); seen.add(OwnVpn.shieldWords());
        seen.add(LiveTvProtection.message(LiveTvProtection.now(app())));
        seen.add(SettingsBackup.export(app().getSharedPreferences(HouseholdSettings.PREFS, 0).getAll(), "2026-11-03"));
        seen.add(app().getSharedPreferences(OwnVpn.PREFS, 0).getAll().toString());
        seen.add(app().getSharedPreferences("vault", 0).getAll().toString());
        for (String s : seen) { assertFalse(s, s.contains(TOKEN)); assertFalse(s, s.contains(KEY)); assertFalse(s, s.contains("198.51.100.7")); }
        assertTrue(EventLog.recent(app()).stream().anyMatch(e -> e.words.startsWith("Unified TV's VPN connected")));
        // Forgetting: the VPN goes off and the key goes.
        OwnVpn.forget(app()); idle();
        assertFalse(OwnVpn.saved(app())); assertEquals(OwnVpn.Phase.OFF, OwnVpn.phase()); assertFalse(tunnel.up);
        assertTrue(Arrays.stream(SettingsReset.CONNECTIONS).anyMatch(c -> OwnVpn.VAULT.equals(c[0])));
    }

    // --- the VPN page: the glass sidebar, from the shield or Settings
    @Test public void theVpnPageOffersOnOffCountryAndNordVpnsApp() throws Exception {
        try (ActivityController<VpnActivity> c = Robolectric.buildActivity(VpnActivity.class).setup()) {
            String all = words(c.get().body);
            assertTrue(all, all.contains("Add your NordVPN token on your phone"));
        }
        keySaved(); consent.given = true; assertEquals(2, OwnVpn.countries(app(), false).size());   // the week's list, from the fake
        try (ActivityController<VpnActivity> c = Robolectric.buildActivity(VpnActivity.class).setup()) {
            VpnActivity a = c.get(); String all = words(a.body);
            assertTrue(all, all.contains("Country")); assertEquals("Country, " + OwnVpn.FASTEST, find(a.body, "Country").getContentDescription()); assertTrue(all.contains(OwnVpn.LAN)); assertTrue(all.contains(OwnVpn.NOT_KILL_SWITCH));
            assertFalse("connect at start only where Unified TV comes to the front by itself", OwnVpn.atStartOffered(a)); assertTrue(all, all.contains("Connect when the TV turns on"));
            find(a.body, "VPN").performClick(); idle();
            assertEquals(OwnVpn.Phase.ON, OwnVpn.phase()); assertTrue(words(a.body).contains("On · United Kingdom #9001"));
            find(a.body, "Country").performClick(); idle();
            assertTrue(a.onCountries()); String list = words(a.body);
            assertTrue(list, list.contains(OwnVpn.FASTEST)); assertTrue(list, list.contains("Germany")); assertTrue(list, list.contains("United Kingdom"));
            find(a.body, OwnVpn.flagged("DE")).performClick(); idle();
            assertFalse(a.onCountries()); assertEquals("DE", OwnVpn.chosen(app()));
        }
    }
}
