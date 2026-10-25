package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.net.Inet4Address;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

/**
 * Phone setup, the reusable part: one session of {@link PhoneSetupServer} for one task (Home Assistant, football key or
 * film-poster key), the words the TV shows while it runs, and what is saved when the phone finishes. The TV shows the
 * QR code ({@link #link}), the short address ({@link #origin}) and the 6-digit code ({@link #code}); it never shows a
 * secret. The screen that started it calls {@link #stop} when it closes. See PhoneSetupServer for the threat notes.
 */
final class PhoneSetup {
    static final String HA = PhoneService.HA, FOOTBALL = PhoneService.FOOTBALL, TMDB = PhoneService.TMDB;
    static final String WAITING = "Waiting for your phone…", OPENED = "Your phone is connected. Follow the steps there.",
        NO_NETWORK = "This TV isn't connected to your home network, so your phone can't reach it. Check the TV's network connection, then start again.",
        NO_START = "Phone setup couldn't start on this TV. Start again.", EXPIRED = "This code has run out. Choose Start again for a new one.",
        LOCKED = "Too many tries, so this code was stopped. Choose Start again for a new one.", STOPPED = "Phone setup stopped.";
    interface Listener { void changed(PhoneSetup setup); }

    final String kind;
    final PhoneService service;
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Listener listener;
    private PhoneSetupServer server;
    private volatile String status = WAITING;
    private volatile boolean succeeded, over;
    private ExecutorService look;

    private PhoneSetup(Context context, PhoneService service, Listener listener) { this.app = context.getApplicationContext(); this.service = service; this.kind = service.id; this.listener = listener; }

    /** Starts a session (never throws): when the TV has no home network, the session is over at once with a reason. */
    static PhoneSetup start(Context context, String id, Listener listener) {
        PhoneService service = PhoneService.of(context.getApplicationContext(), id);
        if (service == null) throw new IllegalArgumentException("unknown service");
        PhoneSetup s = new PhoneSetup(context, service, listener);
        Inet4Address tv = LocalDiscovery.address(s.app);
        if (tv == null) { s.status = NO_NETWORK; s.over = true; return s; }
        int prefix = prefix(s.app, tv);
        s.server = new PhoneSetupServer(tv, remote -> PhoneSetupServer.sameNetwork(tv, prefix, remote), s.flow(), new PhoneSetupServer.Events() {
            @Override public void opened() { if (!s.over) s.say(OPENED); }
            @Override public void ended(PhoneSetupServer.End why) { s.ended(why); }
        }, System::currentTimeMillis, new SecureRandom());
        try { s.server.start(); } catch (Exception e) { s.status = NO_START; s.over = true; s.server = null; }
        return s;
    }

    String status() { return status; }
    boolean succeeded() { return succeeded; }
    /** Ended for any reason (saved, run out, stopped, no network). */
    boolean over() { return over; }
    String link() { return server == null ? null : server.link(); }
    String origin() { return server == null ? null : server.origin(); }
    String code() { return server == null ? null : server.code(); }
    void listen(Listener l) { listener = l; }

    /** The screen closed or moved on: the page stops answering at once. */
    void stop() { listener = null; if (server != null) server.stop(); else over = true; if (look != null) look.shutdownNow(); }

    private void say(String words) { status = words; Listener l = listener; if (l != null) main.post(() -> { Listener now = listener; if (now != null) now.changed(this); }); }
    private void ended(PhoneSetupServer.End why) {
        over = true;
        if (why == PhoneSetupServer.End.SUCCEEDED) { succeeded = true; say(service.tvDone); }
        else say(why == PhoneSetupServer.End.EXPIRED ? EXPIRED : why == PhoneSetupServer.End.LOCKED ? LOCKED : STOPPED);
        if (look != null) look.shutdownNow();
    }

    /** What the phone sees and what is saved: the service's own flow, or the generic form drawn from its descriptor. */
    private PhoneSetupServer.Flow flow() {
        look = Executors.newSingleThreadExecutor();
        return service.custom != null ? service.custom.flow(app, this::say, look) : new PhoneFlows.Form(service, this::say);
    }

    /** The Home Assistant to offer: the saved address, else what the quiet look-ahead found, else a short look now. */
    static String found(Context c) {
        try { String saved = new Vault(c).read("ha").optString("base"); if (!saved.isEmpty()) return saved; } catch (Exception ignored) {}
        String recent = lastFound; if (recent != null && System.currentTimeMillis() - lastFoundAt < 60_000) return recent;
        List<LocalDiscovery.Found> ready = LocalDiscovery.fresh() ? LocalDiscovery.haFound : null;
        if (ready == null || ready.isEmpty()) ready = LocalDiscovery.homeAssistant(c, false);
        String base = ready == null || ready.isEmpty() ? null : ready.get(0).base;
        if (base != null) { lastFound = base; lastFoundAt = System.currentTimeMillis(); }
        return base;
    }
    /** The last look, shared by the TV screen and the phone page for a minute, so they name the same Home Assistant. */
    private static volatile String lastFound; private static volatile long lastFoundAt;

    /**
     * The found Home Assistant as the household knows it (owner, 25 October 2026): "homeassistant.local" when that name
     * leads to the same address, else the address as found. Looked up once per address; a .local lookup is bounded.
     */
    static String haName(String base) {
        if (base == null) return null;
        String known = names.get(base); if (known != null) return known;
        known = haName(base, LocalDiscovery::named); names.put(base, known); return known;
    }
    private static final java.util.Map<String, String> names = new java.util.concurrent.ConcurrentHashMap<>();
    /** Pure: {@code resolve} answers a host name's addresses (empty when it can't). */
    static String haName(String base, java.util.function.Function<String, List<java.net.InetAddress>> resolve) {
        if (base == null) return null;
        try {
            java.net.URI u = java.net.URI.create(base); String host = u.getHost(); if (host == null) return base;
            String port = u.getPort() > 0 && u.getPort() != 8123 ? ":" + u.getPort() : "";
            if (host.equalsIgnoreCase("homeassistant.local")) return "homeassistant.local" + port;
            java.net.InetAddress at = java.net.InetAddress.getByName(host.replaceAll("^\\[|\\]$", ""));
            for (java.net.InetAddress a : resolve.apply("homeassistant.local")) if (a.equals(at)) return "homeassistant.local" + port;
            return host + port;
        } catch (Exception e) { return base; }
    }

    /** Saved exactly as the app keeps Home Assistant: one Vault record; the alert service reconnects with it. */
    static void saveHa(Context c, String base, String token, boolean httpConsent, String how) throws Exception {
        JSONObject old;
        try { old = new Vault(c).read("ha"); } catch (Exception e) { old = new JSONObject(); }
        old.put("base", base).put("enabled", true).put("kind", "ha").put("token", token).put("user", "").put("httpConsent", httpConsent).put("how", how);
        new Vault(c).save("ha", old);
        EventLog.record(c, EventLog.CONNECTION, "Home Assistant connected");
        restartAlerts(c);
    }
    /** The household alert service uses the saved connection: restart it if alerts are meant to be on. */
    static void restartAlerts(Context c) {
        try { c.stopService(new Intent(c, HouseholdService.class)); } catch (RuntimeException ignored) {}
        if (c.getSharedPreferences(HouseholdSettings.PREFS, 0).getBoolean(AlarmWatch.STOPPED, false)) return;
        try { if (android.provider.Settings.canDrawOverlays(c)) c.startForegroundService(new Intent(c, HouseholdService.class)); } catch (RuntimeException ignored) {}
    }

    /** This TV's name in Home Assistant's list of tokens: unique (Home Assistant refuses a repeated name) and plain. */
    static String clientName(Context c) {
        String when = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK));
        if (c == null) return "Unified TV · " + when;
        String room = c.getSharedPreferences(HouseholdSettings.PREFS, 0).getString("room", android.os.Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony") ? "sony" : "tcl");
        return "Unified TV on the " + HaEntities.roomName(room) + " · " + when;
    }

    /** The subnet prefix of the TV's own address (24 when Android doesn't say). */
    static int prefix(Context c, Inet4Address tv) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE); Network n = LocalDiscovery.home(c);
            LinkProperties link = n == null || cm == null ? null : cm.getLinkProperties(n);
            if (link != null) for (LinkAddress a : link.getLinkAddresses()) if (tv.equals(a.getAddress())) return a.getPrefixLength();
        } catch (RuntimeException ignored) {}
        return 24;
    }
}
