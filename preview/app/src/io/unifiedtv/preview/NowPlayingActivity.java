package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Music, Now Playing (spec 11, Music; OD-27; owner's mockup in docs/design/REFERENCES.md). Unified TV as the remote and
 * the screen for the real Amazon Music app, through Android's official media controls only (MusicSession): Amazon Music
 * keeps playing in its own app, and Back returns to Home without stopping it. With "Play on" set to an Echo, the same
 * screen shows and controls that Echo through Home Assistant (EchoSession).
 *
 * <p>The background is the track's own artwork, blurred and darkened; with none, a neutral dark scene, never another
 * album's art (AGENTS rule 4). Artwork is decoded off the main thread at the size it is shown (MusicArt).
 *
 * <p>Remote: OK or Play/Pause plays and pauses; Left and Right skip to the previous or next track, and held they move
 * 10 seconds back or forward; Down reaches the buttons below (shuffle and repeat when the app offers them, its own like or
 * thumbs buttons, Play on, Open Amazon Music), where Left and Right move and OK presses; Up reaches Settings; Menu opens
 * the Amazon Music app; Back returns. Every listener is removed when the screen is hidden.
 */
public final class NowPlayingActivity extends Screen {
    static final String EXTRA_QUERY = "music.query", EXTRA_FOCUS = "music.focus", EXTRA_ROUTE = "music.route", EXTRA_BEFORE = "music.before",
            EXTRA_PLACE = "music.place", EXTRA_PLACE_NAME = "music.placeName";
    /** How long a skip's light stays on the button pressed. */
    static final long FLASH_MS = 250;
    private enum Mode { NONE, ACCESS, NOTHING, PLAYING }
    /** Where focus is: the play controls, the row of buttons under them, or Settings at the top. */
    private enum Zone { TRANSPORT, EXTRAS, SETTINGS }
    private enum Kind { SHUFFLE, REPEAT, LIKE, QUIETER, LOUDER, PLAY_ON, OPEN_APP }
    private static final class Extra {
        final Kind kind; final String label, action;
        Extra(Kind kind, String label, String action) { this.kind = kind; this.label = label; this.action = action; }
    }
    private static final int PREVIOUS = 0, PLAY = 1, NEXT = 2;

    private Mode mode = Mode.NONE; private String nothingWords = "";
    private Zone zone = Zone.TRANSPORT; private int extraIndex;
    private final List<Extra> extras = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private MusicSession session;
    private NowPlaying now;
    private Bitmap art, backdrop;
    private String artId, appLabel = "", amazonPkg;
    private int generation, repeatAsked = NowPlaying.REPEAT_NONE, flash = -1;
    private long flashAt, seekAt;
    private boolean listening, shuffleAsked, held;
    private int holdDirection;
    private Stage stage;
    /** Play by name in progress: what was asked, how, and the track that was on before it. */
    private String waitQuery, waitBefore = "", status;
    private AmazonMusic.Focus waitFocus = AmazonMusic.Focus.ANY;
    private AmazonMusic.Route waitRoute;
    private long waitUntil, statusUntil;
    private boolean waitRetried;
    /**
     * Where this visit's request played, when it wasn't this profile's Play on choice (owner, 27 October 2026: a place said
     * by voice is for that request only): Echoes.TV, Echoes.EVERYWHERE or an Echo's entity; null for the profile's choice.
     */
    private String place, placeName;
    /** The last track remembered for "Recently played", and whether its artwork was with it. */
    private String rememberedKey; private boolean rememberedWithArt;

    /** Now Playing waiting for Amazon Music to start {@code query} (play by name, the library). */
    static Intent waiting(Context c, String query, AmazonMusic.Focus focus, AmazonMusic.Route route, String before) {
        // A request played on this TV is shown from this TV, whatever the profile's Play on choice.
        return waiting(c, query, focus, route, before, route == AmazonMusic.Route.ECHO ? null : Echoes.TV, null);
    }
    /** The same, shown from {@code place} (Echoes.TV, Echoes.EVERYWHERE or an Echo's entity; null: the profile's choice). */
    static Intent waiting(Context c, String query, AmazonMusic.Focus focus, AmazonMusic.Route route, String before, String place, String placeName) {
        return new Intent(c, NowPlayingActivity.class).putExtra(EXTRA_PLACE, place).putExtra(EXTRA_PLACE_NAME, placeName).putExtra(EXTRA_QUERY, query).putExtra(EXTRA_FOCUS, focus.name())
                .putExtra(EXTRA_ROUTE, route.name()).putExtra(EXTRA_BEFORE, before == null ? "" : before);
    }

    private final Runnable sessionsChanged = this::choose;
    private final Runnable playerChanged = this::update;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            // Without access, look again in case the owner has just allowed it (for example with the ADB line).
            if (mode == Mode.ACCESS) { if (MusicSessions.source.granted(NowPlayingActivity.this)) start(); else handler.postDelayed(this, 5000); return; }
            waited();
            if (session == null && listening) choose();
            else if (stage != null) stage.invalidate();
            boolean running = mode == Mode.PLAYING && now != null && now.playing() || waitQuery != null;
            handler.postDelayed(this, running ? 1000 - SystemClock.uptimeMillis() % 1000 : 5000);
        }
    };

    @Override protected void onCreate(Bundle state) { super.onCreate(state); readWait(getIntent()); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); readWait(intent); }
    private void readWait(Intent i) {
        String q = i == null ? null : i.getStringExtra(EXTRA_QUERY);
        String p = i == null ? null : i.getStringExtra(EXTRA_PLACE);
        if (p != null) { place = p; String n = i.getStringExtra(EXTRA_PLACE_NAME); placeName = n == null || n.isEmpty() ? null : n; }
        if (q == null || q.isEmpty()) return;
        waitQuery = q; waitFocus = AmazonMusic.Focus.of(i.getStringExtra(EXTRA_FOCUS));
        String r = i.getStringExtra(EXTRA_ROUTE); waitRoute = r == null ? AmazonMusic.Route.SESSION : AmazonMusic.Route.valueOf(r);
        String b = i.getStringExtra(EXTRA_BEFORE); waitBefore = b == null ? "" : b;
        waitUntil = SystemClock.uptimeMillis() + AmazonMusic.WAIT_MS; waitRetried = false;
        say((waitRoute == AmazonMusic.Route.ECHO ? "Asking " + targetName() : "Asking " + AmazonMusic.NAME) + " for “" + q + "”…", AmazonMusic.WAIT_MS);
    }

    @Override protected void onResume() { super.onResume(); start(); }
    /** Nothing keeps listening while the screen is hidden; playback carries on in Amazon Music (or on the Echo). */
    @Override protected void onStop() { release(); super.onStop(); }
    @Override protected void onDestroy() { release(); worker.shutdownNow(); super.onDestroy(); }

    private SharedPreferences prefs() { return getSharedPreferences(HouseholdSettings.PREFS, 0); }

    private void start() {
        release();
        amazonPkg = AmazonMusic.installed(this);
        handler.postDelayed(tick, 1000);
        if (!Echoes.TV.equals(target())) { echo(); return; }
        MusicSessions.Source src = MusicSessions.source;
        if (!src.granted(this)) { show(Mode.ACCESS); return; }
        src.listen(this, sessionsChanged, handler); listening = true;
        choose();
    }

    private void release() {
        handler.removeCallbacksAndMessages(null);
        if (listening) { MusicSessions.source.unlisten(this, sessionsChanged); listening = false; }
        attach(null);
        artId = null; generation++;   // the backdrop is rebuilt from the session's own art on return
    }

    /** "Play on" an Echo: that Echo's player, through Home Assistant. "Everywhere": the group, or an Echo that is playing. */
    private void echo() {
        String target = target(), name = targetName();
        if (!Echoes.EVERYWHERE.equals(target)) { attachEcho(target, name); return; }
        nothing("Looking for your Echo speakers…", true);
        final int gen = generation; Context app = getApplicationContext();
        try {
            worker.execute(() -> {
                Echoes.Found f = Echoes.finder.find(app); Echoes.Echo e = f.problem == null ? Echoes.shown(f.echoes) : null;
                handler.post(() -> {
                    if (gen != generation) return;
                    if (e == null) nothing(f.problem != null ? f.problem : Echoes.MISSING, true); else attachEcho(e.id, e.group ? "Everywhere" : e.name);
                });
            });
        } catch (RejectedExecutionException ignored) { /* closing */ }
    }
    private void attachEcho(String entity, String name) {
        try { attach(new EchoSession(this, Echoes.ha(this), entity, name)); } catch (IllegalArgumentException e) { nothing(Echoes.MISSING, true); return; }
        update();
    }

    /** The session on screen stays while it is alive, so pausing it here never jumps to another app or to "Nothing". */
    private void choose() {
        List<MusicSession> list = MusicSessions.source.active(this);
        MusicSession best = MusicSessions.best(this, list), kept = null;
        if (session != null) for (MusicSession m : list)
            if (m.key() != null && m.key().equals(session.key()) && NowPlaying.alive(m.read().state)) { kept = session; break; }
        // A playing session wins (Amazon Music first); otherwise the one already on screen stays while it is alive.
        MusicSession next = best != null && best.read().playing() ? best : kept != null ? kept : best;
        if (next == null) { attach(null); now = null; nothing(waitQuery != null ? status : "Nothing is playing", false); return; }
        if (session == null || !session.key().equals(next.key())) attach(next);
        update();
    }
    private void attach(MusicSession next) {
        if (session != null) { try { session.unwatch(); } catch (RuntimeException ignored) { } }
        session = next;
        if (next != null) {
            appLabel = next.label();
            try { next.watch(playerChanged, handler); } catch (RuntimeException ignored) { }
        }
    }

    private void update() {
        if (session == null) return;
        if (!(session instanceof EchoSession) && listening && !NowPlaying.alive(session.read().state)) { choose(); return; }
        now = session.read();
        if (session instanceof EchoSession) {
            EchoSession e = (EchoSession) session;
            if (e.problem != null) say(e.problem, 6000);
            if (!e.known()) { nothing("Checking " + e.name + "…", true); return; }
            if (!NowPlaying.alive(now.state) && waitQuery == null) { nothing("Nothing is playing on " + e.name, true); return; }
        }
        // Play by name: done once the player is playing something other than what was on before.
        if (waitQuery != null && now.playing() && (waitBefore.isEmpty() || !now.trackKey().equals(waitBefore))) { waitQuery = null; status = null; }
        artwork();
        remember();
        extras();
        show(Mode.PLAYING);
        String who = now.artist.isEmpty() ? "" : " by " + now.artist;
        stage.setContentDescription("Now playing" + (now.title.isEmpty() ? "" : ": " + now.title + who) + (now.playing() ? "" : ". Paused")
                + ". OK plays or pauses, Left and Right skip, hold them to move 10 seconds, Down for more, Back returns.");
        stage.invalidate();
        handler.removeCallbacks(tick); handler.postDelayed(tick, 1000);
    }

    /** The track's own art, read off the main thread at the size it is shown; a new track drops the old art at once. */
    private void artwork() {
        MusicSession s = session; Bitmap given = s.bitmap(); String uri = given == null ? s.artUri() : null;
        String id = now.trackKey() + "\u0000" + (given != null ? given.getWidth() + "x" + given.getHeight() : uri == null ? "none" : uri);
        if (id.equals(artId)) return;
        artId = id; art = null; backdrop = null; final int gen = ++generation;
        if (given == null && uri == null) return;
        int size = Math.max(64, Math.round(Stage.ART * getResources().getDisplayMetrics().widthPixels / (float) Stage.W));
        Context app = getApplicationContext();
        try {
            worker.execute(() -> {
                Bitmap a; Bitmap blurred;
                try { a = MusicArt.load(app, s, size, size); if (a == null) return; blurred = Backdrops.fromPoster(a); } catch (RuntimeException e) { return; }
                handler.post(() -> { if (gen == generation) { art = a; backdrop = blurred; remember(); if (stage != null) stage.invalidate(); } });
            });
        } catch (RejectedExecutionException ignored) { /* closing */ }
    }

    /**
     * The Music page's "Recently played" (MusicHistory): an album or artist Amazon Music (or an Echo) is playing, once per
     * track, again when its artwork arrives. Written on the worker, never the main thread.
     */
    private void remember() {
        MusicSession s = session; NowPlaying n = now;
        if (s == null || n == null || !n.playing() || !(s instanceof EchoSession || AmazonMusic.packages(this).contains(s.packageName()))) return;
        String key = n.trackKey(); Bitmap a = art; boolean withArt = a != null;
        if (key.equals(rememberedKey) && (rememberedWithArt || !withArt)) return;
        rememberedKey = key; rememberedWithArt = withArt;
        Context app = getApplicationContext();
        try { worker.execute(() -> MusicHistory.remember(app, n, a)); } catch (RejectedExecutionException ignored) { /* closing */ }
    }

    /** The buttons under the controls: only what the player itself offers, then Play on and the app. */
    private void extras() {
        extras.clear();
        long a = now.actions;
        if (shuffle() != null || (a & NowPlaying.ACTION_SET_SHUFFLE_MODE) != 0) extras.add(new Extra(Kind.SHUFFLE, shuffleAsked ? "Shuffle on" : "Shuffle", null));
        if ((a & NowPlaying.ACTION_SET_REPEAT_MODE) != 0) extras.add(new Extra(Kind.REPEAT, repeatAsked == NowPlaying.REPEAT_NONE ? "Repeat" : NowPlaying.repeatWords(repeatAsked), null));
        List<String[]> custom = session.customActions();
        String[] ids = new String[custom.size()], names = new String[custom.size()];
        for (int i = 0; i < custom.size(); i++) { ids[i] = custom.get(i)[0]; names[i] = custom.get(i)[1]; }
        for (int i : NowPlaying.likeActions(ids, names)) extras.add(new Extra(Kind.LIKE, NowPlaying.bounded(names[i], 24), ids[i]));
        if (session.ownVolume()) { extras.add(new Extra(Kind.QUIETER, "Quieter", null)); extras.add(new Extra(Kind.LOUDER, "Louder", null)); }
        extras.add(new Extra(Kind.PLAY_ON, "Play on: " + Echoes.targetName(prefs()), null));
        if (amazonPkg != null) extras.add(new Extra(Kind.OPEN_APP, "Open Amazon Music app", null));
        if (extraIndex >= extras.size()) extraIndex = Math.max(0, extras.size() - 1);
        if (zone == Zone.EXTRAS && extras.isEmpty()) zone = Zone.TRANSPORT;
    }

    private void show(Mode next) {
        if (next == mode && next != Mode.NOTHING) return;
        mode = next;
        if (next == Mode.PLAYING) {
            nothingWords = "";
            if (stage == null) stage = new Stage(this);
            setContentView(stage); stage.requestFocus(); return;
        }
        if (next == Mode.ACCESS) access();
    }

    /** Access not yet granted: a plain explanation, the setting, and the one ADB line for TVs without that setting. */
    private void access() {
        page("Now playing");
        text("To show and control what's playing, allow Unified TV to see media notifications", 24);
        text("This uses the TV's own media controls; Amazon Music keeps playing in its own app. Unified TV doesn't read, keep or send your notifications. You can still play music by name from the Music page.", 19);
        button("Allow in Settings", () -> { if (!MediaWatch.openAccessSettings(this)) note("This TV doesn't offer that setting. Use the computer step below."); });
        text("If your TV doesn't offer this setting, turn on network debugging, then run this one line from a computer on the same network:", 19);
        android.widget.TextView line = text(NowPlaying.adbLine(MediaWatch.component(this).flattenToString()), 18);
        line.setTypeface(Typeface.MONOSPACE);
        text("Turn network debugging off again afterwards.", 17);
        button("Back", this::finish);
    }

    /** Nothing to show: say so plainly and offer the official app (or Play on, for an Echo). */
    private void nothing(String words, boolean echo) {
        String w = words == null || words.isEmpty() ? "Nothing is playing" : words;
        if (mode == Mode.NOTHING && w.equals(nothingWords)) return;
        mode = Mode.NOTHING; nothingWords = w;
        page("Now playing");
        text(w, 30);
        if (echo) {
            text("Playing on: " + targetName() + ", through Home Assistant.", 20);
            button("Play on…", this::playOn);
        } else {
            text("Start something in Amazon Music, or play by name from the Music page, and it appears here.", 20);
            button(MusicActivity.find(this) != null ? "Open Amazon Music" : "Find Amazon Music", () -> MusicActivity.open(this));
        }
        button("Back", this::finish);
    }

    /** A short line in place of the app's name (a request, a mode asked for, a problem). */
    private void say(String words, long ms) { status = words; statusUntil = SystemClock.uptimeMillis() + ms; if (stage != null) stage.invalidate(); }

    /** Play by name that hasn't started yet: after the session, Android's request to the app; then a plain sentence. */
    private void waited() {
        if (waitQuery == null || SystemClock.uptimeMillis() < waitUntil) return;
        if (waitRoute == AmazonMusic.Route.SESSION && !waitRetried) {
            waitRetried = true;
            if (AmazonMusic.retryWithIntent(this, waitQuery, waitFocus)) { waitUntil = SystemClock.uptimeMillis() + AmazonMusic.WAIT_MS; return; }
        }
        String who = waitRoute == AmazonMusic.Route.ECHO ? targetName() : AmazonMusic.NAME;
        String q = waitQuery; waitQuery = null;
        say(who + " didn't start “" + q + "”." + (waitRoute == AmazonMusic.Route.ECHO ? "" : " Press Menu to open Amazon Music and choose it there."), 10_000);
        if (mode == Mode.NOTHING) nothing(status, waitRoute == AmazonMusic.Route.ECHO);
    }

    // --- the remote

    private void playPause() {
        if (session == null || now == null) return;
        try {
            if (now.playing()) { if (now.offers(NowPlaying.ACTION_PAUSE) || now.offers(NowPlaying.ACTION_PLAY_PAUSE)) session.pause(); else unavailable("Pause"); }
            else session.play();
        } catch (RuntimeException e) { noAnswer(); }
    }
    private void skip(int direction) {
        if (session == null || now == null) return;
        long action = direction > 0 ? NowPlaying.ACTION_SKIP_TO_NEXT : NowPlaying.ACTION_SKIP_TO_PREVIOUS;
        if (!now.offers(action)) { unavailable(direction > 0 ? "Next" : "Previous"); return; }
        flash = direction > 0 ? NEXT : PREVIOUS; flashAt = SystemClock.uptimeMillis();
        try { if (direction > 0) session.next(); else session.previous(); } catch (RuntimeException e) { noAnswer(); }
        if (stage != null) { stage.invalidate(); stage.postInvalidateDelayed(FLASH_MS); }
    }
    private void seek(int direction) {
        if (session == null || now == null) return;
        if (!now.offers(NowPlaying.ACTION_SEEK_TO)) { unavailable("moving within a track"); return; }
        try { session.seekTo(NowPlaying.seekTarget(now.position(SystemClock.elapsedRealtime()), now.durationMs, direction)); } catch (RuntimeException e) { noAnswer(); return; }
        say(direction > 0 ? "Forward 10 seconds" : "Back 10 seconds", 1500);
    }
    private void pressExtra() {
        if (extraIndex < 0 || extraIndex >= extras.size() || session == null) return;
        Extra x = extras.get(extraIndex);
        try {
            switch (x.kind) {
                case SHUFFLE: {
                    String[] own = shuffle();
                    if (own != null) { session.custom(own[0], null); say("Asked " + appName() + " to shuffle", 3000); break; }
                    shuffleAsked = !shuffleAsked; Bundle b = new Bundle(); b.putInt(NowPlaying.COMPAT_SHUFFLE_ARG, shuffleAsked ? 1 : 0);
                    session.custom(NowPlaying.COMPAT_SET_SHUFFLE, b); say(shuffleAsked ? "Shuffle on" : "Shuffle off", 3000); break;
                }
                case REPEAT: {
                    repeatAsked = NowPlaying.nextRepeat(repeatAsked); Bundle b = new Bundle(); b.putInt(NowPlaying.COMPAT_REPEAT_ARG, repeatAsked);
                    session.custom(NowPlaying.COMPAT_SET_REPEAT, b); say(NowPlaying.repeatWords(repeatAsked), 3000); break;
                }
                case LIKE: session.custom(x.action, null); say("Sent “" + x.label + "” to " + appName(), 3000); break;
                case QUIETER: session.volume(-1); break;
                case LOUDER: session.volume(1); break;
                case PLAY_ON: playOn(); return;
                case OPEN_APP: openApp(); return;
                default: break;
            }
        } catch (RuntimeException e) { noAnswer(); }
        extras(); if (stage != null) stage.invalidate();
    }
    /** This visit's place, else the profile's Play on choice; This TV where Echos aren't allowed (Kids). */
    private String target() {
        SharedPreferences p = prefs();
        if (place == null) return Echoes.target(p);
        return Echoes.TV.equals(place) || Echoes.allowed(p) ? place : Echoes.TV;
    }
    private String targetName() {
        String t = target();
        if (place == null || !t.equals(place)) return Echoes.targetName(prefs());
        if (Echoes.TV.equals(t)) return "This TV";
        if (Echoes.EVERYWHERE.equals(t)) return "Everywhere";
        return placeName != null ? placeName : HaEntities.pretty(t.substring(t.indexOf('.') + 1));
    }
    /** Choosing where music plays: from then on, this screen follows the profile's choice. */
    private void playOn() { place = null; placeName = null; startActivity(new Intent(this, MusicActivity.class).putExtra(MusicActivity.PLAY_ON, true)); }
    /** The real Amazon Music app; playback carries on either way. */
    private void openApp() {
        String pkg = AmazonMusic.installed(this);
        if (pkg == null) { MusicActivity.open(this); return; }
        String why = Launch.open(this, pkg, AmazonMusic.NAME);
        if (why != null && !why.isEmpty()) note(why);
    }
    private String appName() { return appLabel.isEmpty() ? "the app" : appLabel; }
    private void noAnswer() { say((appLabel.isEmpty() ? "The app" : appLabel) + " didn't respond", 4000); }
    private void unavailable(String what) { say((appLabel.isEmpty() ? "This app" : appLabel) + " doesn't offer " + what + " to the TV's media controls", 4000); }
    /** The app's own shuffle button, when it offers one: {action id, label}. */
    private String[] shuffle() {
        if (session == null) return null;
        List<String[]> list = session.customActions();
        String[] ids = new String[list.size()], names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) { ids[i] = list.get(i)[0]; names[i] = list.get(i)[1]; }
        int i = NowPlaying.shuffleAction(ids, names); return i < 0 ? null : list.get(i);
    }

    /** Keys, for the Stage and for tests: true when handled. */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode(); boolean down = e.getAction() == KeyEvent.ACTION_DOWN; int repeat = e.getRepeatCount();
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_HEADSETHOOK: if (down && repeat == 0) playPause(); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY: if (down && repeat == 0 && now != null && !now.playing()) playPause(); return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: if (down && repeat == 0 && now != null && now.playing()) playPause(); return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT: if (down && repeat == 0) skip(1); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: if (down && repeat == 0) skip(-1); return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: if (down) seek(1); return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND: if (down) seek(-1); return true;
            case KeyEvent.KEYCODE_MENU: if (down && repeat == 0) openApp(); return true;
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (session == null || !session.ownVolume()) return false;
                if (down) session.volume(code == KeyEvent.KEYCODE_VOLUME_UP ? 1 : -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) zone = zone == Zone.EXTRAS ? Zone.TRANSPORT : Zone.SETTINGS;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) zone = zone == Zone.SETTINGS ? Zone.TRANSPORT : !extras.isEmpty() ? Zone.EXTRAS : zone;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (zone == Zone.EXTRAS) { if (down) extraIndex = Math.max(0, Math.min(extras.size() - 1, extraIndex + dir)); break; }
                if (zone != Zone.TRANSPORT) break;
                // A press skips on release; held, it moves 10 seconds at a time instead.
                if (down && repeat == 0) { holdDirection = dir; held = false; seekAt = e.getDownTime(); }
                else if (down && holdDirection == dir && e.getEventTime() - seekAt >= NowPlaying.SEEK_EVERY_MS) { held = true; seekAt = e.getEventTime(); seek(dir); }
                else if (!down) { if (holdDirection == dir && !held && !e.isCanceled()) skip(dir); holdDirection = 0; held = false; }
                break;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && repeat == 0) {
                    if (zone == Zone.SETTINGS) EditLock.ask(this, () -> startActivity(new Intent(this, SettingsActivity.class)));
                    else if (zone == Zone.EXTRAS) pressExtra();
                    else playPause();
                }
                return true;
            default: return false;
        }
        if (stage != null) stage.invalidate();
        return true;
    }

    /** The custom-drawn screen on the 1920 x 1080 canvas, like Home. */
    private final class Stage extends View {
        static final int W = 1920, H = 1080;
        static final float ART = 560;
        private static final int WHITE = 0xFFFAFAFC, MUTED = 0xFFC8CCD5, INK = 0xFF101827;
        private static final float ART_L = 170, ART_T = 250, COL = 900, COL_R = 1824, BAR_T = 742, BAR_H = 124, CELL = 120, GAP = 16, PAD = 22,
                ROW_T = 892, ROW_H = 64, CHIP_GAP = 16, CHIP_PAD = 28;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint type = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private final SharedPreferences prefs;

        Stage(Context c) {
            super(c);
            prefs = c.getSharedPreferences(HouseholdSettings.PREFS, Context.MODE_PRIVATE);
            setFocusable(true); setFocusableInTouchMode(true);
        }
        private boolean solid() { return prefs.getBoolean("opaque", false) || prefs.getBoolean("highContrast", false); }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) { return key(event) || super.onKeyDown(keyCode, event); }
        @Override public boolean onKeyUp(int keyCode, KeyEvent event) { return key(event) || super.onKeyUp(keyCode, event); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.save(); c.scale(getWidth() / (float) W, getHeight() / (float) H);
            background(c); header(c); artwork(c); details(c); controls(c); extrasRow(c);
            String foot = session instanceof EchoSession ? "Playing on " + session.label() + " through Home Assistant."
                    : "Through the TV's own media controls. " + (appLabel.isEmpty() ? "The app" : appLabel) + " keeps playing when you go back.";
            text(c, foot, 96, 1044, 20, MUTED, false, Paint.Align.LEFT, W);
            c.restore();
        }

        /** The album art heavily blurred and darkened; with no art, a neutral dark scene. */
        private void background(Canvas c) {
            paint.setShader(null); paint.setAlpha(255);
            if (backdrop != null) {
                c.drawBitmap(backdrop, null, new RectF(0, 0, W, H), paint);
                paint.setColor(solid() ? 0xd205070c : 0x9905070c); c.drawRect(0, 0, W, H, paint);
            } else {
                paint.setShader(new LinearGradient(0, 0, 0, H, 0xff0e1522, 0xff05080e, Shader.TileMode.CLAMP)); c.drawRect(0, 0, W, H, paint);
            }
            paint.setColor(0xff000000);
            paint.setShader(new RadialGradient(W * .5f, H * .45f, W * .75f, new int[]{0x00000000, 0x00000000, 0x9903060c}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, paint); paint.setShader(null);
        }

        /** Clock top-left, as on Home; "Music" and Settings in one glass group top-right. */
        private void header(Canvas c) {
            text(c, HouseholdSettings.time(getContext(), System.currentTimeMillis()), 96, 142, 66, WHITE, false, Paint.Align.LEFT, W);
            type.setTextSize(24); type.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float right = 1730, left = right - type.measureText("Music");
            glass(c, left - 32, 62, 1817, 145, 30);
            text(c, "Music", left, 113, 24, WHITE, true, Paint.Align.LEFT, W);
            boolean on = zone == Zone.SETTINGS;
            if (on) { focusRing(c, 1773, 104, 31); }
            gear(c, 1773, 104, on ? INK : WHITE, 235);
        }

        /** Large album art on the left, with a record just showing at its right edge. */
        private void artwork(Canvas c) {
            float cx = ART_L + ART / 2, cy = ART_T + ART / 2, vx = cx + 112, vr = ART / 2 - 14;
            paint.setColor(0xff0b0c10); paint.setShadowLayer(24, 0, 10, 0x80000000); c.drawCircle(vx, cy, vr, paint); paint.clearShadowLayer();
            icon.setStyle(Paint.Style.STROKE); icon.setStrokeWidth(1.2f); icon.setShader(null);
            for (float r = vr - 8; r > vr * .42f; r -= 7) { icon.setColor(r % 21 < 7 ? 0x1cffffff : 0x0effffff); c.drawCircle(vx, cy, r, icon); }
            icon.setStrokeWidth(2); icon.setColor(0x33ffffff); c.drawCircle(vx, cy, vr - 1, icon);   // the fine light edge
            rect.set(ART_L, ART_T, ART_L + ART, ART_T + ART);
            paint.setColor(0xff151b26); paint.setShadowLayer(34, 0, 16, 0x99000000); c.drawRoundRect(rect, 18, 18, paint); paint.clearShadowLayer();
            path.reset(); path.addRoundRect(rect, 18, 18, Path.Direction.CW);
            c.save(); c.clipPath(path);
            if (art != null && !art.isRecycled()) c.drawBitmap(art, crop(art), rect, paint);
            else {
                paint.setShader(new LinearGradient(0, ART_T, 0, ART_T + ART, 0xff1d2533, 0xff0f141d, Shader.TileMode.CLAMP)); c.drawRect(rect, paint); paint.setShader(null);
                musicNote(c, cx, cy);
            }
            c.restore();
            icon.setColor(0x2effffff); icon.setStrokeWidth(1.5f); c.drawRoundRect(rect, 18, 18, icon);
        }
        private android.graphics.Rect crop(Bitmap b) {
            int w = b.getWidth(), h = b.getHeight(), s = Math.min(w, h);
            return new android.graphics.Rect((w - s) / 2, (h - s) / 2, (w - s) / 2 + s, (h - s) / 2 + s);
        }

        /** Title huge, artist and album below, then the thin progress line with elapsed and total. */
        private void details(Canvas c) {
            NowPlaying n = now; if (n == null) return;
            float width = COL_R - COL;
            String from = appLabel;
            if (!n.playing()) from = from.isEmpty() ? "Paused" : from + "  ·  Paused";
            boolean saying = status != null && SystemClock.uptimeMillis() < statusUntil;
            text(c, saying ? status : from, COL, 330, 24, saying ? WHITE : MUTED, false, Paint.Align.LEFT, width);
            String title = n.title.isEmpty() ? "Unknown title" : n.title;
            type.setTextSize(78); type.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text(c, title, COL, 432, type.measureText(title) > width ? 62 : 78, WHITE, true, Paint.Align.LEFT, width);
            if (!n.artist.isEmpty()) text(c, n.artist, COL, 502, 38, WHITE, false, Paint.Align.LEFT, width);
            if (!n.album.isEmpty()) text(c, n.album, COL, 556, 30, MUTED, false, Paint.Align.LEFT, width);
            long at = n.position(SystemClock.elapsedRealtime());
            float f = NowPlaying.fraction(at, n.durationMs), y = 640;
            paint.setStyle(Paint.Style.FILL); paint.setShader(null);
            paint.setColor(0x40ffffff); c.drawRoundRect(COL, y - 2, COL_R, y + 2, 2, 2, paint);
            if (f > 0) { paint.setColor(WHITE); c.drawRoundRect(COL, y - 2, COL + width * f, y + 2, 2, 2, paint); c.drawCircle(COL + width * f, y, 7, paint); }
            text(c, NowPlaying.clock(at), COL, 688, 24, WHITE, false, Paint.Align.LEFT, width / 2);
            text(c, n.durationMs > 0 ? NowPlaying.clock(n.durationMs) : "", COL_R, 688, 24, MUTED, false, Paint.Align.RIGHT, width / 2);
        }

        /** One glass bar: previous, play or pause, next. Focus is the light disc on Play; a skip lights its button briefly. */
        private void controls(Canvas c) {
            float right = COL + PAD * 2 + CELL * 3 + GAP * 2;
            glass(c, COL, BAR_T, right, BAR_T + BAR_H, BAR_H / 2);
            NowPlaying n = now; boolean lit = flash >= 0 && SystemClock.uptimeMillis() - flashAt < FLASH_MS;
            for (int i = PREVIOUS; i <= NEXT; i++) {
                float cx = COL + PAD + CELL / 2 + i * (CELL + GAP), cy = BAR_T + BAR_H / 2;
                boolean on = zone == Zone.TRANSPORT && (lit ? flash == i : i == PLAY);
                boolean offered = n != null && (i == PREVIOUS ? n.offers(NowPlaying.ACTION_SKIP_TO_PREVIOUS) : i != NEXT || n.offers(NowPlaying.ACTION_SKIP_TO_NEXT));
                if (on) focusRing(c, cx, cy, i == PLAY ? 50 : 44);
                int colour = on ? INK : WHITE, alpha = offered ? 240 : 95;
                if (i == PREVIOUS) skip(c, cx, cy, -1, colour, alpha);
                else if (i == NEXT) skip(c, cx, cy, 1, colour, alpha);
                else if (n != null && n.playing()) pause(c, cx, cy, colour, alpha); else play(c, cx, cy, colour, alpha);
            }
        }

        /** The row of buttons below: glass pills, the focused one light. */
        private void extrasRow(Canvas c) {
            float x = ART_L;
            for (int i = 0; i < extras.size(); i++) {
                String label = extras.get(i).label;
                type.setTextSize(24); type.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                float w = Math.min(type.measureText(label) + CHIP_PAD * 2, COL_R - x);
                if (w < CHIP_PAD * 3) break;
                boolean on = zone == Zone.EXTRAS && i == extraIndex;
                if (on) {
                    paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(0xfff3f6ff);
                    rect.set(x, ROW_T, x + w, ROW_T + ROW_H); c.drawRoundRect(rect, ROW_H / 2, ROW_H / 2, paint);
                    icon.setShader(null); icon.setStyle(Paint.Style.STROKE); icon.setStrokeWidth(3); icon.setColor(0xff88b7ff); c.drawRoundRect(rect, ROW_H / 2, ROW_H / 2, icon);
                } else glass(c, x, ROW_T, x + w, ROW_T + ROW_H, ROW_H / 2);
                text(c, label, x + CHIP_PAD, ROW_T + ROW_H / 2 + 9, 24, on ? INK : WHITE, true, Paint.Align.LEFT, w - CHIP_PAD * 2);
                x += w + CHIP_GAP;
            }
        }

        private void focusRing(Canvas c, float cx, float cy, float r) {
            paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(0xfff3f6ff);
            paint.setShadowLayer(18, 0, 6, 0x66000000); c.drawCircle(cx, cy, r, paint); paint.clearShadowLayer();
            icon.setShader(null); icon.setStyle(Paint.Style.STROKE); icon.setStrokeWidth(3); icon.setColor(0xff88b7ff); c.drawCircle(cx, cy, r, icon);
        }

        /** Glass in the same manner as Home's (MaterialScene.glass), but lit by this screen's own album backdrop. */
        private void glass(Canvas c, float l, float t, float r, float b, float radius) {
            rect.set(l, t, r, b); path.reset(); path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            c.save(); c.clipPath(path);
            paint.setShader(null); paint.setStyle(Paint.Style.FILL);
            if (solid()) { paint.setColor(0xff192334); c.drawRect(rect, paint); }
            else {
                if (backdrop != null) { paint.setAlpha(150); c.drawBitmap(backdrop, null, new RectF(0, 0, W, H), paint); paint.setAlpha(255); }
                paint.setShader(new LinearGradient(0, t, 0, b, 0x38ffffff, 0x1affffff, Shader.TileMode.CLAMP)); c.drawRect(rect, paint);
            }
            paint.setShader(new LinearGradient(l, t, r, t, new int[]{0x05ffffff, 0x66ffffff, 0x12ffffff}, null, Shader.TileMode.CLAMP));
            c.drawRect(l, t, r, t + 1.4f, paint); paint.setShader(null); c.restore();
            icon.setShader(null); icon.setStyle(Paint.Style.STROKE); icon.setStrokeWidth(1); icon.setColor(0x30dce8f3); c.drawRoundRect(rect, radius, radius, icon);
        }

        private void glyphPaint(int colour, int alpha, boolean fill) {
            icon.setShader(null); icon.setColor(colour); icon.setAlpha(alpha); icon.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
            icon.setStrokeWidth(3.5f); icon.setStrokeCap(Paint.Cap.ROUND); icon.setStrokeJoin(Paint.Join.ROUND);
        }
        private void play(Canvas c, float cx, float cy, int colour, int alpha) {
            glyphPaint(colour, alpha, true); path.reset();
            path.moveTo(cx - 11, cy - 19); path.lineTo(cx + 20, cy); path.lineTo(cx - 11, cy + 19); path.close(); c.drawPath(path, icon);
        }
        private void pause(Canvas c, float cx, float cy, int colour, int alpha) {
            glyphPaint(colour, alpha, true);
            c.drawRoundRect(cx - 15, cy - 19, cx - 5, cy + 19, 3, 3, icon); c.drawRoundRect(cx + 5, cy - 19, cx + 15, cy + 19, 3, 3, icon);
        }
        /** Next (dir 1) or previous (dir -1): a triangle against a bar. */
        private void skip(Canvas c, float cx, float cy, int dir, int colour, int alpha) {
            glyphPaint(colour, alpha, true); path.reset();
            path.moveTo(cx - 13 * dir, cy - 15); path.lineTo(cx + 9 * dir, cy); path.lineTo(cx - 13 * dir, cy + 15); path.close(); c.drawPath(path, icon);
            float bx = cx + 12 * dir; c.drawRoundRect(Math.min(bx, bx + 5 * dir), cy - 15, Math.max(bx, bx + 5 * dir), cy + 15, 2, 2, icon);
        }
        /** A plain note for the no-art square: an original drawing, not an album or brand image. */
        private void musicNote(Canvas c, float cx, float cy) {
            glyphPaint(WHITE, 70, true);
            c.drawOval(cx - 58, cy + 30, cx - 4, cy + 70, icon);
            c.drawRect(cx - 12, cy - 80, cx - 2, cy + 50, icon);
            path.reset(); path.moveTo(cx - 2, cy - 80); path.cubicTo(cx + 30, cy - 70, cx + 60, cy - 50, cx + 44, cy - 10);
            path.cubicTo(cx + 46, cy - 40, cx + 24, cy - 48, cx - 2, cy - 50); path.close(); c.drawPath(path, icon);
        }
        private void gear(Canvas c, float cx, float cy, int colour, int alpha) {
            glyphPaint(colour, alpha, false); icon.setStrokeWidth(3);
            c.drawCircle(cx, cy, 11, icon); c.drawCircle(cx, cy, 4.5f, icon);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4;
                c.drawLine(cx + (float) (Math.cos(a) * 13), cy + (float) (Math.sin(a) * 13), cx + (float) (Math.cos(a) * 18), cy + (float) (Math.sin(a) * 18), icon);
            }
        }

        private void text(Canvas c, String s, float x, float y, int size, int colour, boolean bold, Paint.Align align, float max) {
            if (s == null || s.isEmpty()) return;
            type.setShader(null); type.setColor(colour); type.setTextSize(size); type.setTextAlign(align);
            type.setTypeface(Typeface.create(bold ? "sans-serif-medium" : size >= 66 ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
            type.setShadowLayer(3, 0, 1, 0x52000000);
            c.drawText(TextUtils.ellipsize(s, type, max, TextUtils.TruncateAt.END).toString(), x, y, type);
            type.clearShadowLayer();
        }
    }

    // --- for tests
    MusicSession sessionForTest() { return session; }
    String statusForTest() { return status; }
    List<String> extrasForTest() { List<String> out = new ArrayList<>(); for (Extra x : extras) out.add(x.label); return out; }
    boolean waitingForTest() { return waitQuery != null; }
    Bitmap artForTest() { return art; }
}
