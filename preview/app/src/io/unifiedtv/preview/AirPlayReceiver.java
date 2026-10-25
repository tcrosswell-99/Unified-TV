package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.net.*;
import java.util.*;

/**
 * AirPin LITE on the TCL (spec 7.1, GATE-AIRPLAY-01): the header shows the AirPlay glyph only when this TV is actually
 * offering AirPlay on the home network, which is exactly what an iPhone looks for: an `_airplay._tcp` service answering
 * from one of this TV's own addresses. An installed or configured app is not enough. Anything else (not installed,
 * stopped, crashed, offline) hides the glyph completely; there is never a grey or error state.
 */
final class AirPlayReceiver {
    private AirPlayReceiver() {}
    /** Last confirmed state and when it was checked; read on the UI thread, written by the check. */
    static volatile boolean ready; static volatile long checkedAt; static volatile String advertisedAs = "";
    private static volatile boolean checking;
    static final long RECHECK_MS = 120_000;

    /** The installed AirPin app's package, found by its own name rather than a guessed package id; null if none. */
    static String installed(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER})
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0))
                if (isAirPin(String.valueOf(r.loadLabel(pm)))) return r.activityInfo.packageName;
        return null;
    }
    static boolean isAirPin(String label) { return label != null && label.toLowerCase(Locale.ROOT).replace(" ", "").contains("airpin"); }

    /** True when a discovered AirPlay service answers from one of this TV's own addresses. */
    static boolean ours(Collection<String> own, String base) {
        try { String host = new URI(base).getHost(); return host != null && own.contains(host); } catch (URISyntaxException e) { return false; }
    }
    static Set<String> ownAddresses() {
        Set<String> out = new HashSet<>();
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) if (a instanceof Inet4Address) out.add(a.getHostAddress());
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Re-checks in the background when the last answer is older than RECHECK_MS; done runs on a worker thread. */
    static void refresh(Context c, Runnable done) {
        long now = System.currentTimeMillis();
        if (checking || now - checkedAt < RECHECK_MS) return;
        Context app = c.getApplicationContext(); checking = true;
        Thread t = new Thread(() -> {
            boolean was = ready;
            try {
                if (installed(app) == null) { ready = false; advertisedAs = ""; }
                else {
                    boolean found = false; String name = ""; Set<String> own = ownAddresses();
                    for (LocalDiscovery.Found f : LocalDiscovery.mdns(app, "_airplay._tcp.", 3000))
                        if (ours(own, f.base)) { found = true; name = f.name; break; }
                    ready = found; advertisedAs = name;
                }
            } catch (Exception e) { ready = false; }
            finally { checkedAt = System.currentTimeMillis(); checking = false; }
            if (was != ready && done != null) done.run();
        }, "airplay-check");
        t.setDaemon(true); t.start();
    }
}
