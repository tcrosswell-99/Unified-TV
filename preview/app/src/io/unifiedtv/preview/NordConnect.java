package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Connect, then carry on (owner's TCL, build run 99). NordVPN has no public "connect" request another app may send, so
 * Connect still opens NordVPN. But when Connect comes from a refused Live TV launch, Unified TV remembers it: for up to
 * {@link #WINDOW_MS} it listens to Android's own VPN network callback (and NordVPN's notification, MediaWatch), and as
 * soon as the one rule (LiveTvProtection) lets Live TV open, Unified TV comes back by itself (Comeback; "Display over other
 * apps") and opens Live TV with a short "NordVPN connected — opening Live TV". If the household comes back first, the same
 * happens then; if the rule still refuses, the sheet says why. No polling: one callback, one timeout.
 */
final class NordConnect {
    private NordConnect() {}
    static final long WINDOW_MS = 120_000;
    static final String OPENING = "NordVPN connected — opening Live TV";
    static final String AUTO_TIP = "Tip: turn on Auto-connect in NordVPN's settings so it's always connected.";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ConnectivityManager.NetworkCallback callback; private static Context app;
    private static final Runnable CHECK = NordConnect::check, END = NordConnect::stop;

    /** Connect was pressed for a refused Live TV launch from {@code from}: {@code open} is that launch, run again. */
    static void after(Activity from, Runnable open) {
        Comeback.arm(from, WINDOW_MS, on -> {
            if (from.isFinishing() || from.isDestroyed()) return true;
            stop();
            if (LiveTvProtection.opens(LiveTvProtection.now(on))) Toast.makeText(on, OPENING, Toast.LENGTH_SHORT).show();
            open.run();   // opens Live TV, or shows the sheet with the rule's reason
            return true;
        });
        watch(from.getApplicationContext());
    }
    private static void watch(Context c) {
        stop(); app = c;
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { soon(); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) { soon(); }
        };
        try { if (cm != null) cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback); else callback = null; }
        catch (RuntimeException e) { callback = null; }
        MAIN.postDelayed(END, WINDOW_MS);
    }
    /** NordVPN's notification said connected (MediaWatch): look again, as for the network callback. */
    static void nordSaid() { if (callback != null) soon(); }
    private static void soon() { MAIN.removeCallbacks(CHECK); MAIN.postDelayed(CHECK, LiveTvGuard.SETTLE_MS); }
    /** The VPN changed: when the rule lets Live TV open, Unified TV comes back and carries on (Comeback). */
    static void check() {
        Context c = app; if (c == null || !Comeback.armed()) { stop(); return; }
        if (LiveTvProtection.opens(LiveTvProtection.now(c))) { stop(); Comeback.deliver(c); }
    }
    static void stop() {
        MAIN.removeCallbacks(CHECK); MAIN.removeCallbacks(END);
        ConnectivityManager.NetworkCallback cb = callback; Context c = app; callback = null;
        if (cb != null && c != null) try { c.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(cb); } catch (RuntimeException ignored) {}
    }
    /** For tests: the VPN watch is running. */
    static boolean watching() { return callback != null; }
}
