package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * AirPin LITE on the TCL (spec 7.1, GATE-AIRPLAY-01): the pill's AirPlay glyph is bright only when this TV is actually
 * offering AirPlay on the home network, which is what an iPhone looks for. An installed or configured app is not enough.
 *
 * How it is checked (off the main thread): Android's own service discovery is asked for AirPlay receivers, both the
 * video/screen service (`_airplay._tcp`) and the audio one (`_raop._tcp`), because AirPin LITE may offer either. One
 * counts when it resolves to one of this TV's own addresses (IPv4 or IPv6, or the TV's loopback, which Android reports
 * for a service on the same device on some firmware), or when its port answers on this TV itself. If discovery lists
 * nothing (some firmware never shows a device its own services), the standard AirPlay port is asked directly on this TV,
 * and only an answer that identifies itself as AirPlay counts. Anything else reads as not offering.
 *
 * When it is checked: whenever Home comes back, every two minutes while Home is showing, and, after AirPin was opened from
 * Unified TV, again a few times over the next half minute (FOLLOW_UP_MS), because AirPin starts its service only once it
 * is open and an earlier answer would be stale. A request while a check is running is kept and run straight after it.
 */
final class AirPlayReceiver {
    private AirPlayReceiver() {}
    /** Last confirmed state and when it was checked; read on the UI thread, written by the check. */
    static volatile boolean ready; static volatile long checkedAt; static volatile String advertisedAs = "";
    /** AirPin's package as last found by installed(); lets Launch recognise a visit to it from any route. */
    static volatile String packageSeen;
    private static volatile boolean checking, again; private static volatile Runnable afterwards;
    static final long RECHECK_MS = 120_000;
    /** Checks after coming back from AirPin, in ms after Home shows again: its service may take a few seconds to appear. */
    static final long[] FOLLOW_UP_MS = {1_500, 4_000, 9_000, 18_000, 30_000};
    static final String[] TYPES = {"_airplay._tcp.", "_raop._tcp."};
    /** AirPlay's standard port, asked directly only when discovery lists nothing. */
    static final int AIRPLAY_PORT = 7000;
    private static volatile long openedAt;

    /** The installed AirPin app's package, found by its own name rather than a guessed package id; null if none. */
    static String installed(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER})
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0))
                if (isAirPin(String.valueOf(r.loadLabel(pm)))) return packageSeen = r.activityInfo.packageName;
        return null;
    }
    static boolean isAirPin(String label) { return label != null && label.toLowerCase(Locale.ROOT).replace(" ", "").contains("airpin"); }

    /** AirPin was just opened from Unified TV (Launch): Home re-checks on return and a few times after. */
    static void opened() { openedAt = System.currentTimeMillis(); }
    /** True once after AirPin was opened (within ten minutes): the caller schedules FOLLOW_UP_MS checks. */
    static boolean takeReturn() { long at = openedAt; openedAt = 0; return at > 0 && System.currentTimeMillis() - at < 600_000; }
    /** A check is running now (the pill says "Checking" rather than a stale "not offering"). */
    static boolean checking() { return checking; }

    /** True when a discovered AirPlay service answers from one of this TV's own addresses. */
    static boolean ours(Collection<String> own, String base) {
        try { String host = new URI(base).getHost(); return host != null && own.contains(host.replace("[", "").replace("]", "")); } catch (URISyntaxException e) { return false; }
    }
    /** The same, for a resolved address: one of this TV's own, or its loopback. */
    static boolean ours(Collection<String> own, InetAddress host) { return host != null && (host.isLoopbackAddress() || own.contains(plain(host))); }
    private static String plain(InetAddress a) { String s = a.getHostAddress(); int pct = s == null ? -1 : s.indexOf('%'); return pct < 0 ? s : s.substring(0, pct); }
    static Set<String> ownAddresses() {
        Set<String> out = new HashSet<>();
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) out.add(plain(a));
            }
        } catch (Exception ignored) { }
        return out;
    }
    /**
     * Pure: whether the first bytes a port sent back identify an AirPlay receiver (an HTTP or RTSP reply naming AirTunes,
     * AirPlay or Apple's AirPlay content types). Anything else on the port does not count.
     */
    static boolean airPlayAnswer(String head) {
        if (head == null) return false;
        String h = head.toLowerCase(Locale.ROOT);
        if (!(h.startsWith("http/1.") || h.startsWith("rtsp/1."))) return false;
        return h.contains("airtunes") || h.contains("airplay") || h.contains("x-apple-") || h.contains("apple-binary-plist");
    }
    /** Asks a port on this TV itself whether an AirPlay receiver answers there; bounded to a fraction of a second. */
    static boolean answers(int port) {
        if (port <= 0 || port > 65535) return false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 400); s.setSoTimeout(800);
            OutputStream out = s.getOutputStream();
            out.write(("GET /info HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nUser-Agent: AirPlay/381.13\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); out.flush();
            InputStream in = s.getInputStream(); byte[] b = new byte[2048]; int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) { n += r; if (new String(b, 0, n, StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) break; }
            return airPlayAnswer(new String(b, 0, n, StandardCharsets.ISO_8859_1));
        } catch (Exception e) { return false; }
    }

    /** Checks now, whatever the last answer's age (Home showing again, a follow-up after AirPin). */
    static void now(Context c, Runnable done) { checkedAt = 0; refresh(c, done); }
    /** Re-checks in the background when the last answer is older than RECHECK_MS; done runs on a worker thread afterwards. */
    static void refresh(Context c, Runnable done) {
        long now = System.currentTimeMillis();
        synchronized (AirPlayReceiver.class) {
            if (checking) { if (checkedAt == 0) { again = true; afterwards = done; } return; }
            if (now - checkedAt < RECHECK_MS) return;
            checking = true; again = false;
        }
        Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                if (installed(app) == null) { ready = false; advertisedAs = ""; }
                else { String name = find(app); ready = name != null; advertisedAs = name == null ? "" : name; }
            } catch (Exception e) { ready = false; }
            finally {
                Runnable next;
                synchronized (AirPlayReceiver.class) { checkedAt = System.currentTimeMillis(); checking = false; next = again ? afterwards : null; if (again) checkedAt = 0; again = false; afterwards = null; }
                if (done != null) done.run();
                if (next != null) refresh(app, next);
            }
        }, "airplay-check");
        t.setDaemon(true); t.start();
    }

    /** Tests only: what a check finds (the advertised name, "" for the direct answer, null for nothing). */
    static volatile java.util.function.Function<Context, String> probeForTests;
    /**
     * One check now, on the calling worker thread (AirPlayStart's watch): the name this TV's receiver is advertised as
     * ("" when only the direct check answered), or null. Updates ready and advertisedAs.
     */
    static String checkNow(Context c) {
        String name;
        try { java.util.function.Function<Context, String> p = probeForTests; name = p != null ? p.apply(c) : installed(c) == null ? null : find(c); }
        catch (RuntimeException e) { name = null; }
        ready = name != null; advertisedAs = name == null ? "" : name; checkedAt = System.currentTimeMillis();
        return name;
    }
    /** The name iPhones see for this TV's receiver ("" when only the direct check answered), or null when none is offered. */
    private static String find(Context c) {
        Set<String> own = ownAddresses(); List<NsdServiceInfo> seen = discover(c, 3000);
        for (NsdServiceInfo s : seen) {
            NsdServiceInfo r = resolve(c, s);
            if (r != null && (ours(own, r.getHost()) || (r.getPort() > 0 && answers(r.getPort())))) return r.getServiceName() == null ? "" : r.getServiceName();
        }
        return answers(AIRPLAY_PORT) ? "" : null;
    }
    /** Both AirPlay service types for a short window; at most eight services. */
    private static List<NsdServiceInfo> discover(Context c, long window) {
        List<NsdServiceInfo> out = Collections.synchronizedList(new ArrayList<>()); NsdManager nsd = (NsdManager) c.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return out;
        List<NsdManager.DiscoveryListener> listeners = new ArrayList<>();
        for (String type : TYPES) {
            NsdManager.DiscoveryListener l = new NsdManager.DiscoveryListener() {
                public void onDiscoveryStarted(String t) {}
                public void onServiceFound(NsdServiceInfo info) { if (out.size() < 8) out.add(info); }
                public void onServiceLost(NsdServiceInfo info) {}
                public void onDiscoveryStopped(String t) {}
                public void onStartDiscoveryFailed(String t, int error) {}
                public void onStopDiscoveryFailed(String t, int error) {}
            };
            try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l); listeners.add(l); } catch (RuntimeException ignored) {}
        }
        try { Thread.sleep(window); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        for (NsdManager.DiscoveryListener l : listeners) try { nsd.stopServiceDiscovery(l); } catch (RuntimeException ignored) {}
        synchronized (out) { return new ArrayList<>(out); }
    }
    /** One resolve at a time (older Android NSD fails overlapping ones), each bounded to two seconds. */
    private static NsdServiceInfo resolve(Context c, NsdServiceInfo info) {
        NsdManager nsd = (NsdManager) c.getSystemService(Context.NSD_SERVICE); if (nsd == null) return null;
        CountDownLatch done = new CountDownLatch(1); NsdServiceInfo[] got = new NsdServiceInfo[1];
        try {
            nsd.resolveService(info, new NsdManager.ResolveListener() {
                public void onResolveFailed(NsdServiceInfo s, int error) { done.countDown(); }
                public void onServiceResolved(NsdServiceInfo s) { got[0] = s; done.countDown(); }
            });
            done.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); } catch (RuntimeException ignored) {}
        return got[0];
    }
}
