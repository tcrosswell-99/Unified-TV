package io.unifiedtv.preview;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * OD-29 (owner, 3 November 2026): "Unified TV runs the VPN itself." NordVPN's Android TV app offers no way for another
 * app to connect or disconnect it, so Unified TV runs its own WireGuard tunnel to NordVPN's NordLynx servers: one press
 * on Home's shield connects or disconnects, and a second control picks the country, all inside Unified TV.
 *
 * <p>What it keeps. The NordLynx private key, encrypted in the Vault as {@code {"key": …}} under {@link #VAULT} (added on
 * the phone with a NordVPN access token, OD-17; the token itself is never kept). In its own plain preferences
 * ({@link #PREFS}, never exported or backed up): the chosen country's two-letter code (empty is Fastest), "Connect when
 * the TV turns on", whether the household wants the VPN on, NordVPN's country list (a week), and two "said once" marks.
 *
 * <p>How it behaves. The first connection needs Android's VPN consent (VpnService.prepare: a system dialog, one OK),
 * explained in plain words first. Android allows one VPN at a time: connecting replaces NordVPN's own app's connection,
 * said once. Home stays reachable: private, link-local and multicast addresses never enter the tunnel (NordLynx). With
 * no polling: one network callback while the VPN is wanted (a new network brings a dropped tunnel back), WireGuard's own
 * 25-second keepalive, and Android's own "the TV has started" for "Connect when the TV turns on" (TurnOnBoot, launcher
 * app only). If Android takes the tunnel away (another VPN app connected), it stays off: Unified TV never fights for it.
 *
 * <p>Live TV (OD-26 as amended): this tunnel up counts as a VPN connected, and Unified TV knows its state exactly, so the
 * safety net (LiveTvGuard) looks again the moment it drops. It is not a kill switch unless the owner also makes Unified
 * TV Android's always-on VPN with "Block connections without VPN" (docs/VPN_PROTECTION.md). Not tested on a TV.
 */
final class OwnVpn {
    private OwnVpn() {}
    static final String VAULT = "nordlynx", PREFS = "own-vpn";
    static final String COUNTRY = "country", BOOT = "connectAtStart", WANTED = "wanted", EXPLAINED = "consentExplained",
        REPLACE_SAID = "replaceSaid", COUNTRIES = "countries", COUNTRIES_AT = "countriesAt";
    static final long COUNTRIES_MS = 7L * 24 * 60 * 60 * 1000;
    /** A new network settles before a dropped tunnel is brought back (one look, not a loop). */
    static final long SETTLE_MS = 3000;

    enum Phase { OFF, CONNECTING, ON, FAILED }

    // --- the household's words
    static final String TITLE = "VPN", SUBTITLE = "NordVPN, run by Unified TV", FASTEST = "Fastest",
        PHONE_TITLE = "NordVPN for Unified TV", CONNECTING_WORDS = "Connecting…",
        NO_KEY = "To use the VPN here, add your NordVPN token on your phone: scan the code on the next screen.",
        EXPLAIN = "Unified TV now runs NordVPN itself. Android will ask once whether Unified TV may set up a VPN connection: choose OK. It is used only for NordVPN.",
        AGAIN = "Android asks again because another VPN app was used since. Choose OK to let Unified TV connect.",
        REPLACES = "NordVPN's own app was connected. Android allows one VPN at a time, so Unified TV's connection replaces it.",
        NOT_ALLOWED = "Android's VPN permission wasn't given, so the VPN stays off.",
        NO_SCREEN = "This TV doesn't show Android's VPN permission screen, so Unified TV can't run the VPN. Use NordVPN's own app instead.",
        OFFLINE = "Unified TV couldn't reach NordVPN. Check the TV is online, then try again.",
        NO_SERVER = "NordVPN didn't offer a server just now. Try again, or choose another country.",
        BAD_KEY = "The NordVPN key on this TV doesn't work any more. Add a new token on your phone.",
        NO_TUNNEL = "The VPN couldn't start on this TV. Try again, or use NordVPN's own app.",
        NO_ANSWER = "NordVPN didn't answer — check your NordVPN subscription, or paste a new token.",
        KIDS = "An adult turns the VPN on or off, in Adults.",
        DROPPED = "Android turned Unified TV's VPN off (another VPN app may have connected).",
        LAN = "Your home network stays reachable while the VPN is on: the NAS, Home Assistant and AirPlay don't go through it. Names from your router may not work while it is on (it uses NordVPN's DNS), so use their IP addresses or .local names.",
        NOT_KILL_SWITCH = "This isn't a kill switch: if the VPN drops, Android doesn't block other apps' traffic unless you set Unified TV as the TV's always-on VPN with \"Block connections without VPN\".",
        UNOFFICIAL = "Unified TV uses NordVPN's servers the way NordVPN's own Linux app does. NordVPN doesn't publish this for other apps, so it may change.";

    // --- state (one tunnel per app)
    private static volatile Phase phase = Phase.OFF;
    private static volatile String problem, server, serverCountry;
    private static volatile long since;
    private static VpnTunnel tunnel;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // --- seams for tests: the network, the tunnel, Android's consent and the worker
    static volatile NordLynx.Http http = NordLynx.HTTPS;
    interface Factory { VpnTunnel make(Context app); }
    static volatile Factory factory = WireGuardTunnel::new;
    interface Consent { Intent prepare(Context c); }
    static volatile Consent consent = android.net.VpnService::prepare;
    private static final Executor WORKER = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "own-vpn"); t.setDaemon(true); return t; });
    static volatile Executor worker = WORKER;

    /** Tests only: back to a fresh app (state, tunnel and seams). */
    static synchronized void resetForTests() {
        phase = Phase.OFF; problem = null; server = null; serverCountry = null; since = 0; tunnel = null; pending = null; LISTENERS.clear();
        generation++; attempt = null; HANDSHAKE_MS = 10_000; deferred = false; resumed = 0; explanation = null;
        http = NordLynx.HTTPS; factory = WireGuardTunnel::new; consent = android.net.VpnService::prepare; worker = WORKER; unwatch();
    }

    static Phase phase() { return phase; }
    /** Unified TV's own tunnel is up now (Live TV's rule counts it as a VPN connected). */
    static boolean on() { return phase == Phase.ON; }
    /** The plain reason the last attempt failed, or null. */
    static String problem() { return problem; }
    static String server() { return server; }
    /** The two-letter country of the server in use, or null. */
    static String country() { return phase == Phase.ON ? serverCountry : null; }
    static long since() { return since; }

    static Runnable listen(Runnable r) { if (r != null) LISTENERS.add(r); return r; }
    static void unlisten(Runnable r) { LISTENERS.remove(r); }
    /** Every change: the shield's cached answer is stale, screens redraw, and Live TV's safety net looks again. */
    private static void changed() {
        MAIN.post(() -> {
            VpnGate.invalidate();
            for (Runnable r : LISTENERS) try { r.run(); } catch (RuntimeException ignored) {}
            LiveTvGuard.nordStopped();
        });
    }

    // --- the key (Vault) and plain preferences
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }
    /** A key is saved: the Vault's encrypted record exists (read without decrypting, so it is cheap). */
    static boolean saved(Context c) { try { return c.getSharedPreferences("vault", 0).contains(VAULT); } catch (RuntimeException e) { return false; } }
    static void save(Context c, String key) throws Exception {
        if (!NordLynx.validKey(key)) throw new IllegalArgumentException("not a key");
        new Vault(c).save(VAULT, new JSONObject().put("key", key));
        EventLog.record(c, EventLog.CONNECTION, "NordVPN key saved for Unified TV's VPN");
        appStarted(c);
    }
    private static String key(Context c) {
        try { String k = new Vault(c).read(VAULT).optString("key", ""); return NordLynx.validKey(k) ? k : null; } catch (Exception e) { return null; }
    }
    /** Privacy & Data and the phone page: the VPN goes off and the key is forgotten. */
    static void forget(Context c) {
        Context app = c.getApplicationContext();
        prefs(app).edit().putBoolean(WANTED, false).apply(); unwatch();
        new Vault(app).forget(VAULT);
        if (phase != Phase.OFF) disconnect(app, null);
        EventLog.record(app, EventLog.CONNECTION, "NordVPN key forgotten");
    }
    /** The chosen country's code, or "" for Fastest (wherever NordVPN suggests). */
    static String chosen(Context c) { String s = prefs(c).getString(COUNTRY, ""); return s.matches("[A-Z]{2}") ? s : ""; }
    /** A new country is remembered; a running VPN moves to it at once. */
    static void choose(Context c, String code) {
        String v = code == null || !code.toUpperCase(Locale.ROOT).matches("[A-Z]{2}") ? "" : code.toUpperCase(Locale.ROOT);
        prefs(c).edit().putString(COUNTRY, v).apply();
        if (phase == Phase.ON) connect(c, null);
    }
    static boolean atStart(Context c) { return prefs(c).getBoolean(BOOT, false); }
    static void atStart(Context c, boolean on) { prefs(c).edit().putBoolean(BOOT, on).apply(); }
    /**
     * "Connect when the TV turns on" works only in the launcher app (told that the TV has started), and only where Unified
     * TV comes to the front by itself afterwards: it is the Home screen, or "Open Unified TV when the TV turns on" is on
     * (Android won't let it start the VPN from the background; it connects when Home appears).
     */
    static boolean atStartOffered(Context c) {
        if (!DefaultHome.available(c)) return false;
        try { return DefaultHome.isDefault(c) || TurnOn.active(c); } catch (RuntimeException e) { return false; }
    }

    /** NordVPN's countries: from the week's copy, else asked for (blocking; call off the main thread). */
    static List<NordLynx.Country> countries(Context c, boolean fresh) {
        SharedPreferences p = prefs(c); List<NordLynx.Country> kept = cached(c);
        if (!fresh && !kept.isEmpty() && System.currentTimeMillis() - p.getLong(COUNTRIES_AT, 0) < COUNTRIES_MS) return kept;
        try {
            NordLynx.Reply r = http.get(NordLynx.API + NordLynx.COUNTRIES, null);
            List<NordLynx.Country> got = r.status == 200 ? NordLynx.countries(r.body) : Collections.emptyList();
            if (!got.isEmpty()) { p.edit().putString(COUNTRIES, NordLynx.countriesJson(got)).putLong(COUNTRIES_AT, System.currentTimeMillis()).apply(); return got; }
        } catch (IOException | RuntimeException ignored) {}
        return kept;
    }
    static List<NordLynx.Country> cached(Context c) { return NordLynx.countries(prefs(c).getString(COUNTRIES, "[]")); }

    // --- connecting and disconnecting (off the main thread; the answer comes back on it)
    /**
     * One connection attempt: its generation and everyone waiting for its answer. disconnect() and dropped() start a new
     * generation, so an attempt that finishes after them can never turn the state back to ON (security review, 3 November
     * 2026: a drop while connecting was overwritten and Live TV opened with no VPN).
     */
    private static final class Attempt { final int generation; final List<Consumer<Boolean>> waiters = new ArrayList<>(); Attempt(int g) { generation = g; } }
    private static int generation;
    private static Attempt attempt;
    /** How long a new tunnel may take to complete its first handshake with NordVPN before it counts as failed. */
    static volatile long HANDSHAKE_MS = 10_000;
    static final long HANDSHAKE_STEP_MS = 250;

    /**
     * Connects (or moves to the chosen country when already on). {@code done} gets true once the tunnel is up and NordVPN
     * has answered; while an attempt is already running, {@code done} waits for that attempt's answer.
     */
    static void connect(Context c, Consumer<Boolean> done) {
        Context app = c.getApplicationContext();
        Attempt mine;
        synchronized (OwnVpn.class) {
            if (phase == Phase.CONNECTING && attempt != null) { if (done != null) attempt.waiters.add(done); return; }
            phase = Phase.CONNECTING; problem = null; mine = attempt = new Attempt(++generation); if (done != null) mine.waiters.add(done);
        }
        prefs(app).edit().putBoolean(WANTED, true).apply();
        watch(app);
        changed();
        try { worker.execute(() -> { String why; try { why = bringUp(app, mine); } catch (Throwable t) { why = NO_TUNNEL; } finish(app, why, mine); }); }
        catch (RuntimeException e) { finish(app, NO_TUNNEL, mine); }
    }
    private static boolean current(Attempt a) { synchronized (OwnVpn.class) { return a.generation == generation; } }
    private static String bringUp(Context app, Attempt mine) throws Exception {
        String key = key(app);
        if (key == null) return saved(app) ? BAD_KEY : NO_KEY;
        Intent ask; try { ask = consent.prepare(app); } catch (RuntimeException e) { return NO_SCREEN; }
        if (ask != null) return NOT_ALLOWED;
        String code = chosen(app); Integer id = null;
        if (!code.isEmpty()) {
            NordLynx.Country k = NordLynx.country(countries(app, false), code);
            if (k == null) return "NordVPN doesn't list " + NordCountry.name(code) + " just now. Choose another country, or Fastest.";
            id = k.id;
        }
        NordLynx.Reply r;
        try { r = http.get(NordLynx.recommendations(id, 5), null); } catch (IOException e) { return OFFLINE; }
        if (r.status != 200) return OFFLINE;
        List<NordLynx.Server> servers = NordLynx.servers(r.body);
        if (servers.isEmpty()) return NO_SERVER;
        if (!current(mine)) return STALE;
        VpnTunnel t = tunnel(app);
        if (t.isUp()) t.down();   // moving to another server or country (the real tunnel waits until the old VPN service has gone)
        for (int i = 0; i < Math.min(2, servers.size()); i++) {
            NordLynx.Server s = servers.get(i);
            try { t.up(NordLynx.config(key, s)); } catch (Exception e) { try { t.down(); } catch (Exception ignored) {} continue; }
            // "Up" is only the interface: ON needs NordVPN's answer (a handshake) and the tunnel still up afterwards.
            if (!answered(t, mine)) { try { t.down(); } catch (Exception ignored) {} return current(mine) ? NO_ANSWER : STALE; }
            server = s.name; serverCountry = s.country.isEmpty() ? (code.isEmpty() ? null : code) : s.country; since = System.currentTimeMillis();
            return null;
        }
        return NO_TUNNEL;
    }
    /** Waits (on the worker) for the first handshake, at most HANDSHAKE_MS; false when none came, the tunnel fell, or the attempt was superseded. */
    private static boolean answered(VpnTunnel t, Attempt mine) {
        long waited = 0;
        while (true) {
            if (!t.isUp() || !current(mine)) return false;
            if (t.handshake() > 0) return t.isUp();
            if (waited >= HANDSHAKE_MS) return false;
            try { Thread.sleep(HANDSHAKE_STEP_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            waited += HANDSHAKE_STEP_MS;
        }
    }
    private static synchronized VpnTunnel tunnel(Context app) {
        if (tunnel == null) { tunnel = factory.make(app); tunnel.onDropped(() -> dropped(app)); }
        return tunnel;
    }
    /** Marks an attempt that was superseded (disconnect, a drop): its answer changes nothing. */
    static final String STALE = "superseded";
    private static void finish(Context app, String why, Attempt mine) {
        boolean ok = why == null, stale;
        synchronized (OwnVpn.class) {
            stale = mine.generation != generation || STALE.equals(why);
            if (!stale) { phase = ok ? Phase.ON : Phase.FAILED; problem = why; if (!ok) { server = null; serverCountry = null; since = 0; } attempt = null; }
        }
        List<Consumer<Boolean>> waiting; synchronized (OwnVpn.class) { waiting = new ArrayList<>(mine.waiters); mine.waiters.clear(); }
        if (stale) {
            // The tunnel this attempt may have raised is not wanted any more (disconnect queued its own down after this).
            if (ok) { VpnTunnel t; synchronized (OwnVpn.class) { t = tunnel; } if (phase != Phase.ON && phase != Phase.CONNECTING && t != null) try { t.down(); } catch (Throwable ignored) {} }
            for (Consumer<Boolean> w : waiting) MAIN.post(() -> w.accept(false));
            return;
        }
        if (!ok && (NOT_ALLOWED.equals(why) || NO_KEY.equals(why) || BAD_KEY.equals(why) || NO_SCREEN.equals(why))) { prefs(app).edit().putBoolean(WANTED, false).apply(); unwatch(); }
        EventLog.record(app, EventLog.CONNECTION, ok ? "Unified TV's VPN connected: " + server : "Unified TV's VPN couldn't connect");
        changed();
        for (Consumer<Boolean> w : waiting) MAIN.post(() -> w.accept(ok));
    }
    /** Turns the VPN off; the household no longer wants it on. An attempt still running is superseded. */
    static void disconnect(Context c, Runnable done) {
        Context app = c.getApplicationContext();
        prefs(app).edit().putBoolean(WANTED, false).apply(); unwatch();
        synchronized (OwnVpn.class) { generation++; attempt = null; phase = Phase.OFF; problem = null; server = null; serverCountry = null; since = 0; }
        changed();
        Runnable off = () -> {
            VpnTunnel t; synchronized (OwnVpn.class) { t = tunnel; }
            try { if (t != null) t.down(); } catch (Throwable ignored) {}
            EventLog.record(app, EventLog.CONNECTION, "Unified TV's VPN turned off");
            changed();
            if (done != null) MAIN.post(done);
        };
        try { worker.execute(off); } catch (RuntimeException e) { off.run(); }
    }
    /** Android took the tunnel down by itself: it stays off (never a fight with another VPN app), and says why. */
    private static void dropped(Context app) {
        synchronized (OwnVpn.class) {
            if (phase == Phase.OFF) return;
            generation++; attempt = null; phase = Phase.OFF; problem = DROPPED; server = null; serverCountry = null; since = 0;
        }
        prefs(app).edit().putBoolean(WANTED, false).apply(); unwatch();
        EventLog.record(app, EventLog.CONNECTION, "Unified TV's VPN was turned off by Android");
        changed();
    }

    // --- one press on the shield, and Android's consent
    /**
     * Home's shield (and the VPN page's On/Off): no key yet opens the phone setup step with one line saying why;
     * connecting says so; on turns it off; otherwise it turns on, through Android's consent when that is still needed.
     */
    static void press(Activity a) {
        // Kids: an adult turns Unified TV's VPN on or off; without a key, NordVPN's app as before (if an adult approved it).
        if (Profiles.kids(a)) { if (saved(a)) say(a, KIDS); else VpnGate.connect(a); return; }
        if (!saved(a)) { say(a, NO_KEY); setup(a); return; }
        switch (phase) {
            case CONNECTING: say(a, CONNECTING_WORDS); return;
            case ON: disconnect(a, null); return;
            default: turnOn(a, null);
        }
    }
    /** The phone setup step for the NordVPN token (Settings > Live TV & Network Protection, behind the adult PIN). */
    static void setup(Activity a) {
        try { a.startActivity(new Intent(a, SettingsActivity.class).putExtra("task", SettingsActivity.PHONE_NORDLYNX)); } catch (RuntimeException ignored) {}
    }
    /** The consent request Unified TV is waiting on (the activity that asked and what follows), or null. */
    private static final class Pending { final Activity from; final Consumer<Boolean> done; Pending(Activity from, Consumer<Boolean> done) { this.from = from; this.done = done; } }
    private static volatile Pending pending;
    static final int REQUEST = 4129;
    /**
     * The explanation sheet shown before Android's consent dialog, held weakly and cleared when it closes, so a closed
     * screen is never kept alive by it. For tests.
     */
    private static volatile java.lang.ref.WeakReference<Dialog> explanation;
    static Dialog explanation() { java.lang.ref.WeakReference<Dialog> w = explanation; return w == null ? null : w.get(); }

    /** Turns the VPN on from a screen: says once that it replaces NordVPN's app, and asks for Android's consent first if needed. */
    static void turnOn(Activity a, Consumer<Boolean> done) {
        if (!saved(a)) { say(a, NO_KEY); setup(a); if (done != null) done.accept(false); return; }
        // Kids never turn Unified TV's VPN on (or give Android's VPN consent) by themselves: the adult PIN first, on every
        // route (the Live TV sheet's Connect, the cover's Reconnect).
        if (Profiles.kids(a)) { say(a, KIDS); EditLock.ask(a, () -> adultTurnOn(a, done)); return; }
        adultTurnOn(a, done);
    }
    private static void adultTurnOn(Activity a, Consumer<Boolean> done) {
        SharedPreferences p = prefs(a);
        if (VpnGate.inspect(a) == VpnGate.State.CONNECTED && phase != Phase.ON && !p.getBoolean(REPLACE_SAID, false)) {
            p.edit().putBoolean(REPLACE_SAID, true).apply(); say(a, REPLACES);
        }
        Intent ask; try { ask = consent.prepare(a); } catch (RuntimeException e) { fail(a, NO_SCREEN, done); return; }
        if (ask == null) { connect(a, done); return; }
        Runnable show = () -> {
            pending = new Pending(a, done);
            try { a.startActivityForResult(ask, REQUEST); } catch (RuntimeException e) { pending = null; fail(a, NO_SCREEN, done); }
        };
        if (p.getBoolean(EXPLAINED, false)) { say(a, AGAIN); show.run(); return; }
        // First time: plain words before Android's own dialog.
        Dialog[] d = new Dialog[1];
        d[0] = VpnGate.sheet(a, "Unified TV's VPN", EXPLAIN, Arrays.asList("Continue", "Cancel"),
            Arrays.asList(() -> { d[0].dismiss(); p.edit().putBoolean(EXPLAINED, true).apply(); show.run(); },
                () -> { d[0].dismiss(); if (done != null) done.accept(false); }), null);
        explanation = new java.lang.ref.WeakReference<>(d[0]);
        d[0].setOnDismissListener(x -> explanation = null);
    }
    private static void fail(Activity a, String why, Consumer<Boolean> done) {
        synchronized (OwnVpn.class) { phase = Phase.FAILED; problem = why; }
        say(a, why); changed(); if (done != null) done.accept(false);
    }
    /** Back from Android's consent dialog (the screen that asked resumed): connect if it was given. */
    static void resumed(Activity a) {
        Pending p = pending; if (p == null || p.from != a) return;
        pending = null;
        Intent still; try { still = consent.prepare(a); } catch (RuntimeException e) { still = new Intent(); }
        if (still == null) connect(a, p.done); else fail(a, NOT_ALLOWED, p.done);
    }
    /** Registered once in PreviewApp: the consent answer arrives as the asking screen resumes. */
    static final Application.ActivityLifecycleCallbacks SCREENS = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity a) { resumed++; resumed(a); if (a instanceof UnifiedPreviewActivity || a instanceof VpnActivity) catchUp(a); }
        @Override public void onActivityPaused(Activity a) { if (resumed > 0) resumed--; }
        @Override public void onActivityDestroyed(Activity a) { Pending p = pending; if (p != null && p.from == a) pending = null; }
        @Override public void onActivityCreated(Activity a, Bundle b) {}
        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    };
    private static void say(Activity a, String words) { try { Toast.makeText(a, words, Toast.LENGTH_LONG).show(); } catch (RuntimeException ignored) {} }

    // --- coming back by itself: a new network, the TV starting, Android's always-on VPN
    /**
     * Android 8 and later refuse to start a service from the background, and the WireGuard library starts its VpnService
     * when a tunnel first comes up. So a reconnect (a new network) or "Connect when the TV turns on" that arrives while no
     * Unified TV screen is on screen waits for the next time Home or the VPN page comes to the front (security review,
     * 3 November 2026). The TV starting is remembered across the process (BOOT_PENDING), a new network only in memory.
     */
    private static volatile boolean deferred;
    /** Unified TV screens resumed now (0: Unified TV is in the background). */
    private static volatile int resumed;
    static final String BOOT_PENDING = "bootPending";
    /** How long after the TV starts "Connect when the TV turns on" still applies when Home first comes to the front. */
    static final long BOOT_WINDOW_MS = 15L * 60 * 1000;
    static boolean foreground() { return resumed > 0; }
    /** For tests: a reconnect is waiting for Home or the VPN page. */
    static boolean deferred() { return deferred; }
    /** Home or the VPN page is in front: a reconnect or start-up connection that was waiting runs now. */
    static void catchUp(Context c) {
        Context app = c.getApplicationContext(); SharedPreferences p = prefs(app);
        long boot = p.getLong(BOOT_PENDING, 0);
        boolean atBoot = boot > 0 && System.currentTimeMillis() - boot < BOOT_WINDOW_MS && atStart(app);
        if (boot > 0) p.edit().remove(BOOT_PENDING).apply();
        boolean again = deferred; deferred = false;
        if (atBoot && phase != Phase.ON && phase != Phase.CONNECTING && saved(app) && consented(app)) { connect(app, null); return; }
        if (again && reconnects(p.getBoolean(WANTED, false), phase, saved(app), consented(app))) connect(app, null);
    }

    private static ConnectivityManager.NetworkCallback network; private static Context watched;
    private static final Runnable LOOK = OwnVpn::look;
    /**
     * Networks already there when the watch started. Android calls onAvailable at once for each of them; only a network
     * that wasn't there (or came back after it was lost) counts, so a quick failure isn't retried straight away.
     */
    static final class Seen {
        private final Set<Object> known = new HashSet<>();
        Seen(Collection<?> already) { if (already != null) known.addAll(already); }
        synchronized boolean arrived(Object n) { return n != null && known.add(n); }
        synchronized void lost(Object n) { known.remove(n); }
    }
    /** While the VPN is wanted: Android's network callback for real (non-VPN) networks, no polling. */
    private static synchronized void watch(Context app) {
        if (network != null) return;
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class); if (cm == null) return;
        List<Network> now = new ArrayList<>();
        try { for (Network n : cm.getAllNetworks()) { NetworkCapabilities caps = cm.getNetworkCapabilities(n); if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) now.add(n); } } catch (RuntimeException ignored) {}
        Seen seen = new Seen(now);
        ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { if (!seen.arrived(n)) return; MAIN.removeCallbacks(LOOK); MAIN.postDelayed(LOOK, SETTLE_MS); }
            @Override public void onLost(Network n) { seen.lost(n); }
        };
        try { cm.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), cb); network = cb; watched = app; }
        catch (RuntimeException ignored) {}
    }
    private static synchronized void unwatch() {
        MAIN.removeCallbacks(LOOK);
        ConnectivityManager.NetworkCallback cb = network; Context c = watched; network = null; watched = null;
        if (cb != null && c != null) try { c.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(cb); } catch (RuntimeException ignored) {}
    }
    /** For tests: the network watch is running. */
    static boolean watching() { return network != null; }
    /**
     * A new network came: when the household wants the VPN on and it isn't (it couldn't connect while the TV was
     * offline, say), connect once, or, with Unified TV in the background, when Home next comes to the front. WireGuard
     * itself carries a running tunnel across a network change.
     */
    static void look() {
        Context c = watched; if (c == null) return;
        if (!reconnects(prefs(c).getBoolean(WANTED, false), phase, saved(c), consented(c))) return;
        if (!foreground()) { deferred = true; return; }
        connect(c, null);
    }
    /** Pure: whether a new network brings the VPN back. Never while connecting or on, and never without consent. */
    static boolean reconnects(boolean wanted, Phase now, boolean saved, boolean consented) {
        return wanted && saved && consented && (now == Phase.OFF || now == Phase.FAILED);
    }
    private static boolean consented(Context c) { try { return consent.prepare(c) == null; } catch (RuntimeException e) { return false; } }
    /**
     * The TV has started (TurnOnBoot, launcher app). {@code finished} runs at once, always: the receiver never waits on
     * NordVPN. With "Connect when the TV turns on" the connection is made when Home first comes to the front (catchUp);
     * nothing running is ever torn down here.
     */
    static void boot(Context c, Runnable finished) {
        Context app = c.getApplicationContext();
        try {
            if (!atStart(app) || !saved(app) || phase == Phase.ON || phase == Phase.CONNECTING) return;
            prefs(app).edit().putLong(BOOT_PENDING, System.currentTimeMillis()).apply();
            if (foreground()) catchUp(app);
        } finally { finished.run(); }
    }
    /** App start: Android's always-on VPN, when the owner chose Unified TV there, may start the tunnel by itself. */
    static void appStarted(Context c) {
        if (!saved(c) || factory == null) return;
        Context app = c.getApplicationContext();
        try { WireGuardTunnel.alwaysOn(() -> { if (phase != Phase.ON) connect(app, null); }); } catch (RuntimeException | LinkageError ignored) {}
    }

    // --- words
    /** The shield's words (pure): Unified TV's own state first, then whose VPN Android reports. */
    static String shieldWords(Phase p, String countryCode, VpnGate.State android, Boolean nordSays) {
        switch (p) {
            case CONNECTING: return "VPN ◌ " + CONNECTING_WORDS;
            case ON: return "VPN ● Connected · Unified TV" + (countryCode == null ? "" : " · " + NordCountry.name(countryCode));
            case FAILED: return "VPN ○ Couldn't connect";
            default: break;
        }
        String s = VpnGate.statusLabel(android);
        return android == VpnGate.State.CONNECTED ? s + " · " + (Boolean.TRUE.equals(nordSays) ? "NordVPN's app" : "another app") : s;
    }
    static String shieldWords() { return shieldWords(phase, serverCountry, VpnGate.current(), NordCountry.connected); }
    /** The VPN page's status line, in plain words (never a key, token or address). */
    static String status(Context c) {
        switch (phase) {
            case CONNECTING: return CONNECTING_WORDS;
            case ON: {
                String when = since == 0 ? "" : " · connected since " + java.time.Instant.ofEpochMilli(since).atZone(HouseholdSettings.zone(c)).format(HouseholdSettings.clock(c));
                return "On · " + (server == null ? "NordVPN" : server) + when;
            }
            case FAILED: return "Off · " + (problem == null ? NO_TUNNEL : problem);
            default:
                if (problem != null) return "Off · " + problem;
                return VpnGate.inspect(c) == VpnGate.State.CONNECTED ? "Off here · another app's VPN is connected" + (Boolean.TRUE.equals(NordCountry.connected) ? " (NordVPN's app)" : "") : "Off";
        }
    }
    static String countryWords(String code) { return code == null || code.isEmpty() ? FASTEST : flagged(code); }
    static String flagged(String code) { String f = NordCountry.flag(code); return (f == null ? "" : f + "  ") + NordCountry.name(code); }
    /** Owner diagnostics: what Unified TV's own VPN is doing, with no secret. */
    static String diagnostics(Context c) {
        return "Unified TV's VPN: " + phase.name().toLowerCase(Locale.ROOT) + (server == null ? "" : " (" + server + ")") + ". NordVPN key: " + (saved(c) ? "saved" : "not set up")
            + ". Country: " + countryWords(chosen(c)) + ". Connect when the TV turns on: " + (atStart(c) ? "on" : "off") + (problem == null ? "" : ". Last problem: " + problem);
    }
}
