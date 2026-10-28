package io.unifiedtv.preview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.net.Inet4Address;
import java.security.SecureRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Optional clean-up's undo files on the phone: one phone setup session ({@link PhoneSetupServer}, with all its
 * protections: home network only, one-time secret in the QR code, ten minutes, no script) whose page offers two
 * downloads, the recovery record and the list of apps Unified TV turned off. With them a computer can put everything
 * back with tools/restore.sh, without Unified TV (preview/tools/cleanup_restore.py files the record where restore.sh
 * looks for it). Nothing is uploaded; the session ends when the TV page closes or after ten minutes.
 *
 * <p>The record holds this TV's serial number and android_id (recovery.py checks them so it can't restore another TV);
 * the phone page says so. It never holds the ADB key.
 */
final class CleanupPhone {
    static final String RECORD_ACTION = "cleanup/record", LIST_ACTION = "cleanup/list";
    static final String RECORD_TYPE = "application/json; charset=utf-8", LIST_TYPE = "text/plain; charset=utf-8";
    static final String WAITING = "Waiting for your phone…", OPENED = "Your phone is connected. Tap both downloads there.",
        SAVED = "Your phone downloaded the undo file. Keep it with the list.", NO_NETWORK = PhoneSetup.NO_NETWORK, NO_START = PhoneSetup.NO_START,
        EXPIRED = PhoneSetup.EXPIRED, LOCKED = PhoneSetup.LOCKED, STOPPED = PhoneSetup.STOPPED;

    /** The phone's pages: two downloads and how to use them. Pure, so tests call it directly. */
    static final class Flow implements PhoneSetupServer.Flow {
        private final Supplier<String> record, list; private final Consumer<String> progress;
        Flow(Supplier<String> record, Supplier<String> list, Consumer<String> progress) { this.record = record; this.list = list; this.progress = progress; }

        @Override public PhoneSetupServer.Response handle(String action, PhoneSetupServer.Request r) {
            switch (action) {
                case "": return PhoneSetupServer.Response.page(page(r.session));
                case RECORD_ACTION: { String text = record.get(); if (text == null) return null; progress.accept(SAVED);
                    return PhoneSetupServer.Response.file(text, RECORD_TYPE, CleanupRecord.FILE); }
                case LIST_ACTION: { String text = list.get(); if (text == null) return null;
                    return PhoneSetupServer.Response.file(text, LIST_TYPE, CleanupRecord.LIST); }
                default: return null;
            }
        }

        static String page(String session) {
            String s = PhonePages.esc(session);
            return PhonePages.page("Undo files for your TV",
                "<p>These two small files say which apps Unified TV turned off on your TV, and how they were before. Keep them together.</p>"
                + "<a class=\"button\" href=\"" + s + "/" + RECORD_ACTION + "\" download=\"" + CleanupRecord.FILE + "\">Download the undo record</a>"
                + "<a class=\"button quiet\" href=\"" + s + "/" + LIST_ACTION + "\" download=\"" + CleanupRecord.LIST + "\">Download the list</a>"
                + "<p class=\"muted\">The undo record contains your TV's serial number and Android ID, so a computer can check it is putting back the right TV. Keep it private. It never contains a password or key.</p>"
                + "<h2>Putting everything back</h2>"
                + PhonePages.steps(
                    "In Unified TV: Settings &gt; Updates &amp; About &gt; Advanced &gt; Optional clean-up, then Turn apps back on.",
                    "Without Unified TV: on the TV, open Settings &gt; Apps &gt; See all apps &gt; Show system apps, choose the app and Enable.",
                    "From a computer with a copy of Unified TV&#39;s files: put both files in one folder and run <code>python3 preview/tools/cleanup_restore.py unifiedtv-cleanup-record.json</code>, then the <code>tools/restore.sh</code> line it prints.")
                + "<p class=\"muted\">Nothing is sent from your phone to the TV on this page.</p>");
        }
    }

    interface Listener { void changed(CleanupPhone phone); }

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Listener listener;
    private PhoneSetupServer server;
    private volatile String status = WAITING;
    private volatile boolean over;

    private CleanupPhone(Listener listener) { this.listener = listener; }

    /** Starts a session (never throws): with no home network, it is over at once with a reason. */
    static CleanupPhone start(Context context, Supplier<String> record, Supplier<String> list, Listener listener) {
        Context app = context.getApplicationContext();
        CleanupPhone p = new CleanupPhone(listener);
        Inet4Address tv = LocalDiscovery.address(app);
        if (tv == null) { p.status = NO_NETWORK; p.over = true; return p; }
        int prefix = PhoneSetup.prefix(app, tv);
        p.server = new PhoneSetupServer(tv, remote -> PhoneSetupServer.sameNetwork(tv, prefix, remote), new Flow(record, list, p::say),
            new PhoneSetupServer.Events() {
                @Override public void opened() { if (!p.over) p.say(OPENED); }
                @Override public void ended(PhoneSetupServer.End why) { p.over = true;
                    p.say(why == PhoneSetupServer.End.EXPIRED ? EXPIRED : why == PhoneSetupServer.End.LOCKED ? LOCKED : STOPPED); }
            }, System::currentTimeMillis, new SecureRandom());
        try { p.server.start(); } catch (Exception e) { p.status = NO_START; p.over = true; p.server = null; }
        return p;
    }

    String status() { return status; }
    boolean over() { return over; }
    String link() { return server == null ? null : server.link(); }
    String origin() { return server == null ? null : server.origin(); }
    String code() { return server == null ? null : server.code(); }
    /** The page closed: the phone page stops answering at once. */
    void stop() { listener = null; if (server != null) server.stop(); over = true; }

    private void say(String words) {
        status = words; Listener l = listener;
        if (l != null) main.post(() -> { Listener now = listener; if (now != null) now.changed(this); });
    }
}
