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

    /** This trip's own token (Comeback): a step armed by AirPlay or notification access is never delivered from here. */
    private static Comeback.Token token;

    /**
     * Connect was pressed for a refused Live TV launch of {@code pkg} from {@code from}. The step keeps only the package and
     * label, never a screen: it runs on whichever screen Comeback hands it.
     */
    static void after(Activity from, String pkg, String label) {
        token = Comeback.arm(from, WINDOW_MS, on -> { stop(); open(on, pkg, label); return true; });
        watch(from.getApplicationContext(), pkg, label);
    }
    /**
     * Live TV through the one gate (Launch.open: the rule, then the Kids list). The toast only once it actually opened; a
     * refusal shows its own reason (the sheet, or Kids' words).
     */
    static void open(Activity on, String pkg, String label) {
        String why = Launch.open(on, pkg, label);
        if (why == null) Toast.makeText(on, OPENING, Toast.LENGTH_SHORT).show();
        else if (!why.isEmpty()) Toast.makeText(on, why, Toast.LENGTH_LONG).show();
    }
    private static String pkg = "", label = "";
    private static void watch(Context c, String livePkg, String liveLabel) {
        stop(); app = c; pkg = livePkg; label = liveLabel;
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
        Context c = app; Comeback.Token t = token;
        if (c == null || !Comeback.armed(t)) { stop(); return; }
        if (!LiveTvProtection.opens(LiveTvProtection.now(c))) return;
        stop();
        if (Comeback.deliver(c, t) != Comeback.Result.GONE) return;
        // The screen that pressed Connect has closed (Ask your TV's idle close, say): carry on from Home instead, never a
        // fresh empty copy of that screen.
        String p = pkg, l = label;
        token = Comeback.armFor(UnifiedPreviewActivity.class, WINDOW_MS, on -> { open(on, p, l); return true; });
        Comeback.deliver(c, token);
    }
    static void stop() {
        MAIN.removeCallbacks(CHECK); MAIN.removeCallbacks(END);
        ConnectivityManager.NetworkCallback cb = callback; Context c = app; callback = null;
        if (cb != null && c != null) try { c.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(cb); } catch (RuntimeException ignored) {}
    }
    /** For tests: the VPN watch is running. */
    static boolean watching() { return callback != null; }
}
