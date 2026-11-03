package io.unifiedtv.preview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An Echo speaker as Now Playing's player (OD-27 "Play on"), through Home Assistant's standard media_player entity that
 * Alexa Media Player provides: its state and attributes (media_title, media_artist, entity_picture…) read every few seconds
 * while Now Playing is open, and its standard services (media_play, media_pause, media_next_track, volume_up…). Every
 * request runs on this session's own worker thread, never the main thread. The token stays in LocalApi's header; the
 * picture is read through MusicArt's ha: route, which drops any token from the address.
 */
final class EchoSession implements MusicSession {
    static final long POLL_MS = 3000;
    /** Home Assistant's media_player feature bits (MediaPlayerEntityFeature). */
    static final int HA_PAUSE = 1, HA_SEEK = 2, HA_PREVIOUS = 16, HA_NEXT = 32, HA_PLAY = 16384;

    private final android.content.Context app; private final JSONObject ha; final String entity, name;
    private volatile NowPlaying now; private volatile String picture; private volatile boolean known;
    private ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean watching; private Handler handler; private Runnable changed;
    /** The last service call's problem in plain words, or null; Now Playing shows it. */
    volatile String problem;

    EchoSession(android.content.Context c, JSONObject ha, String entity, String name) {
        if (entity == null || !entity.matches(Echoes.ENTITY)) throw new IllegalArgumentException("Not a media player");
        this.app = c.getApplicationContext(); this.ha = ha; this.entity = entity; this.name = name == null || name.isEmpty() ? "Echo" : name;
        now = new NowPlaying(packageName(), "", "", "", 0, NowPlaying.STATE_NONE, -1, 0, 1f, 0);
    }
    boolean known() { return known; }

    @Override public String packageName() { return "echo:" + entity; }
    @Override public String label() { return name; }
    @Override public Object key() { return entity; }
    @Override public NowPlaying read() { return now; }
    @Override public Bitmap bitmap() { return null; }
    @Override public String artUri() {
        String p = picture; if (p == null || p.isEmpty()) return null;
        if (p.startsWith("/") && !p.startsWith("//")) return MusicArt.HA + p;
        return p.startsWith("https://") ? p : null;
    }
    @Override public List<String[]> customActions() { return Collections.emptyList(); }
    @Override public void play() { call("media_play", null); }
    @Override public void pause() { call("media_pause", null); }
    @Override public void next() { call("media_next_track", null); }
    @Override public void previous() { call("media_previous_track", null); }
    @Override public void seekTo(long ms) { try { call("media_seek", new JSONObject().put("seek_position", ms / 1000.0)); } catch (Exception ignored) { } }
    @Override public void playFromSearch(String query, Bundle extras) {
        run(() -> { String no = Echoes.play(app, ha, entity, query); problem = no; refresh(); });
    }
    @Override public void playFromMediaId(String mediaId, Bundle extras) { }
    @Override public void custom(String action, Bundle args) { }
    @Override public void volume(int direction) { call(direction > 0 ? "volume_up" : "volume_down", null); }
    @Override public boolean ownVolume() { return true; }

    @Override public void watch(Runnable changed, Handler handler) {
        this.changed = changed; this.handler = handler; watching = true;
        if (worker.isShutdown()) worker = Executors.newSingleThreadScheduledExecutor();
        try { worker.scheduleWithFixedDelay(this::refresh, 0, POLL_MS, TimeUnit.MILLISECONDS); } catch (RejectedExecutionException ignored) { }
    }
    @Override public void unwatch() { watching = false; changed = null; worker.shutdownNow(); }
    private void refresh() {
        try {
            JSONObject state = LocalApi.get(ha, "/api/states/" + entity);
            now = parse(packageName(), state, System.currentTimeMillis(), SystemClock.elapsedRealtime());
            JSONObject attr = state.optJSONObject("attributes");
            picture = attr == null ? null : attr.optString("entity_picture", null);
        } catch (Exception e) { now = new NowPlaying(packageName(), "", "", "", 0, NowPlaying.STATE_NONE, -1, 0, 1f, 0); }
        known = true;
        Handler h = handler; Runnable r = changed;
        if (h != null && r != null && watching) h.post(r);
    }
    private void call(String service, JSONObject extra) {
        run(() -> {
            try { Echoes.send(ha, Echoes.control(entity, service, extra)); problem = null; }
            catch (LocalApi.ApiError e) { problem = Echoes.problem(app.getSharedPreferences(HouseholdSettings.PREFS, 0), Echoes.REFUSED); }
            catch (Exception e) { problem = Echoes.problem(app.getSharedPreferences(HouseholdSettings.PREFS, 0), Echoes.NOT_CONNECTED); }
            refresh();
        });
    }
    private void run(Runnable r) { try { worker.execute(r); } catch (RejectedExecutionException ignored) { /* closing */ } }

    /** Pure: one media_player state as Now Playing's model. */
    static NowPlaying parse(String pkg, JSONObject state, long wallNow, long elapsedNow) {
        JSONObject a = state.optJSONObject("attributes"); if (a == null) a = new JSONObject();
        int s;
        switch (state.optString("state")) {
            case "playing": s = NowPlaying.STATE_PLAYING; break;
            case "paused": s = NowPlaying.STATE_PAUSED; break;
            case "buffering": s = NowPlaying.STATE_BUFFERING; break;
            case "idle": case "on": case "standby": s = NowPlaying.STATE_STOPPED; break;
            default: s = NowPlaying.STATE_NONE; break;
        }
        long duration = Math.round(a.optDouble("media_duration", 0) * 1000), position = a.has("media_position") ? Math.round(a.optDouble("media_position", 0) * 1000) : -1;
        long updated = 0;
        try {
            String at = a.optString("media_position_updated_at");
            if (!at.isEmpty()) updated = elapsedNow - Math.max(0, wallNow - java.time.OffsetDateTime.parse(at).toInstant().toEpochMilli());
        } catch (RuntimeException ignored) { updated = 0; }
        int f = a.optInt("supported_features", 0); long actions = 0;
        if ((f & HA_PAUSE) != 0) actions |= NowPlaying.ACTION_PAUSE;
        if ((f & HA_PLAY) != 0) actions |= NowPlaying.ACTION_PLAY;
        if ((f & HA_PREVIOUS) != 0) actions |= NowPlaying.ACTION_SKIP_TO_PREVIOUS;
        if ((f & HA_NEXT) != 0) actions |= NowPlaying.ACTION_SKIP_TO_NEXT;
        if ((f & HA_SEEK) != 0) actions |= NowPlaying.ACTION_SEEK_TO;
        if (f != 0) actions |= NowPlaying.ACTION_PLAY_FROM_SEARCH;   // play_media is how every request is sent
        return new NowPlaying(pkg, a.optString("media_title"), a.optString("media_artist"), a.optString("media_album_name"),
                duration, s, position, updated, 1f, actions);
    }
}
