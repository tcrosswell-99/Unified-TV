package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Context;

/**
 * Spoken music requests (OD-27), shared by Ask your TV and the Music page's microphone. "… on <words>" is a place only
 * when the words name an Echo Home Assistant found; otherwise they are part of the title ("Carry On Wayward Son") and the
 * whole phrase plays where this profile plays. A place said aloud is for that request only (owner, 27 October 2026): the
 * profile's Play on choice never changes. Echo speakers keep their Kids rule; the network is never touched on the main
 * thread.
 */
final class SpokenMusic {
    private SpokenMusic() {}

    /** What the screen shows while and after a request goes. Always called on the main thread. */
    interface Reply {
        /** A plain sentence for the household (a problem, or "Asking …"). */
        void say(String words);
        /** It went: Now Playing is on its way. */
        void done();
    }

    /**
     * Plays {@code m}. {@code focus}, when not null and not ANY, replaces what the words implied (the Music page's own
     * "Looking for" choice).
     */
    static void play(Activity a, VoiceCommand.Music m, AmazonMusic.Focus focus, Reply r) {
        if (!m.maybeEcho()) { at(a, m.query, pick(focus, m.focus), m.place.isEmpty() ? null : m.place, null, null, r); return; }
        r.say("One moment…");
        Context app = a.getApplicationContext();
        Thread t = new Thread(() -> {
            Echoes.Found f = Echoes.finder.find(app);
            Echoes.Echo e = f.problem == null ? Echoes.named(f.echoes, m.place) : null;
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                if (e == null) at(a, m.whole, pick(focus, m.wholeFocus), null, null, null, r);
                else at(a, m.query, pick(focus, m.focus), e.id, e.name, f, r);
            });
        }, "spoken-echo-name");
        t.setDaemon(true); t.start();
    }
    private static AmazonMusic.Focus pick(AmazonMusic.Focus chosen, AmazonMusic.Focus said) { return chosen == null || chosen == AmazonMusic.Focus.ANY ? said : chosen; }

    /** Plays {@code query} at {@code place} (null: this profile's Play on choice), without remembering the place. */
    private static void at(Activity a, String query, AmazonMusic.Focus focus, String place, String name, Echoes.Found found, Reply r) {
        android.content.SharedPreferences prefs = a.getSharedPreferences(HouseholdSettings.PREFS, 0);
        String target = place == null ? Echoes.target(prefs) : place;
        if (Echoes.TV.equals(target)) {
            AmazonMusic.Result res = AmazonMusic.play(a, query, focus);
            if (res.words.isEmpty()) r.done(); else r.say(res.words);
            return;
        }
        if (!Echoes.allowed(prefs)) { r.say(Echoes.KIDS); return; }
        String shown = name != null ? name : place == null ? Echoes.targetName(prefs) : Echoes.EVERYWHERE.equals(target) ? "Everywhere" : target;
        r.say("Asking " + shown + " for “" + query + "”…");
        Context app = a.getApplicationContext();
        Thread t = new Thread(() -> {
            String no = Echoes.play(app, Echoes.ha(app), target, query, found);
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                if (no != null) { r.say(no); return; }
                a.startActivity(NowPlayingActivity.waiting(a, query, focus, AmazonMusic.Route.ECHO, "", place == null ? null : target, shown));
                r.done();
            });
        }, "spoken-echo");
        t.setDaemon(true); t.start();
    }
}
