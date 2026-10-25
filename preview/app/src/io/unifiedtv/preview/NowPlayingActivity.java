package io.unifiedtv.preview;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Music, Now Playing (spec 11, Music; owner's mockup in docs/design/REFERENCES.md). Shows and controls only what the
 * playing app reports through Android's media controls: Amazon has no public API, so nothing is looked up or fetched.
 * The album art is the session's own bitmap; with none, the scene is neutral and dark, never another album's art.
 * Remote: Left and Right move along the controls, Up reaches Settings, OK presses, Back returns.
 */
public final class NowPlayingActivity extends Screen {
    private enum Mode { NONE, ACCESS, NOTHING, PLAYING }
    private static final int PREVIOUS = 0, PLAY = 1, NEXT = 2, SHUFFLE = 3, SETTINGS = 4;
    private Mode mode = Mode.NONE;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private MediaSessionManager sessions;
    private MediaController controller;
    private NowPlaying now;
    private Bitmap art, backdrop;
    private String artId, appLabel = "";
    private int generation, focus = PLAY;
    private boolean shuffleOffered;
    private Stage stage;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = list -> choose(list != null ? list : MediaWatch.sessions(this));
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { choose(MediaWatch.sessions(NowPlayingActivity.this)); }
        @Override public void onMetadataChanged(android.media.MediaMetadata metadata) { update(); }
        @Override public void onSessionDestroyed() { choose(MediaWatch.sessions(NowPlayingActivity.this)); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            // Without access, look again in case the owner has just allowed it (for example with the ADB line).
            if (mode == Mode.ACCESS) { if (MediaWatch.granted(NowPlayingActivity.this)) { release(); start(); } else handler.postDelayed(this, 5000); return; }
            if (mode == Mode.NOTHING) choose(MediaWatch.sessions(NowPlayingActivity.this));
            else if (stage != null) stage.invalidate();
            handler.postDelayed(this, mode == Mode.PLAYING && now != null && now.playing() ? 1000 - SystemClock.uptimeMillis() % 1000 : 5000);
        }
    };

    @Override protected void onResume() { super.onResume(); start(); }
    private void start() {
        handler.removeCallbacks(tick); handler.postDelayed(tick, 1000);
        if (!MediaWatch.granted(this)) { show(Mode.ACCESS); return; }
        sessions = getSystemService(MediaSessionManager.class);
        try { if (sessions != null) sessions.addOnActiveSessionsChangedListener(changed, MediaWatch.component(this), handler); }
        catch (RuntimeException ignored) { sessions = null; }
        choose(MediaWatch.sessions(this));
    }
    /** Nothing keeps listening while the screen is hidden. */
    @Override protected void onPause() { release(); super.onPause(); }
    @Override protected void onStop() { release(); super.onStop(); }
    @Override protected void onDestroy() { release(); worker.shutdownNow(); super.onDestroy(); }

    private void release() {
        handler.removeCallbacksAndMessages(null);
        if (sessions != null) { try { sessions.removeOnActiveSessionsChangedListener(changed); } catch (RuntimeException ignored) { } sessions = null; }
        attach(null);
        artId = null; generation++;   // the backdrop is rebuilt from the session's own art on return
    }

    /** The session on screen stays while it is alive, so pausing it here never jumps to another app or to "Nothing". */
    private void choose(List<MediaController> list) {
        MediaController best = MediaWatch.best(this, list), kept = null;
        if (controller != null) for (MediaController m : list)
            if (m.getSessionToken().equals(controller.getSessionToken()) && NowPlaying.alive(MediaWatch.state(m))) { kept = m; break; }
        // A playing session wins (Amazon Music first); otherwise the one already on screen stays while it is alive.
        MediaController next = best != null && NowPlaying.playing(MediaWatch.state(best)) ? best : kept != null ? kept : best;
        if (next == null) { attach(null); now = null; show(Mode.NOTHING); return; }
        if (controller == null || !controller.getSessionToken().equals(next.getSessionToken())) attach(next);
        update();
    }
    private void attach(MediaController next) {
        if (controller != null) { try { controller.unregisterCallback(callback); } catch (RuntimeException ignored) { } }
        controller = next;
        if (next != null) {
            try { next.registerCallback(callback, handler); } catch (RuntimeException ignored) { }
            appLabel = label(next.getPackageName());
        }
    }
    private String label(String pkg) {
        try { PackageManager pm = getPackageManager(); return NowPlaying.bounded(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)), 40); }
        catch (Exception e) { return NowPlaying.amazon(pkg) ? "Amazon Music" : ""; }
    }

    private void update() {
        if (controller == null) return;
        now = MediaWatch.read(controller);
        Bitmap a = MediaWatch.art(controller);
        String id = now.trackKey() + "\u0000" + (a == null ? "none" : a.getWidth() + "x" + a.getHeight());
        if (!id.equals(artId)) {
            // A new track drops the old art and its backdrop at once, so a previous album never lingers.
            artId = id; art = a; backdrop = null; final int gen = ++generation;
            if (a != null) try {
                worker.execute(() -> {
                    Bitmap blurred; try { blurred = Backdrops.fromPoster(a); } catch (RuntimeException e) { return; }
                    handler.post(() -> { if (gen == generation) { backdrop = blurred; if (stage != null) stage.invalidate(); } });
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) { /* closing */ }
        }
        shuffleOffered = shuffle() != null;
        show(Mode.PLAYING);
        String who = now.artist.isEmpty() ? "" : " by " + now.artist;
        stage.setContentDescription("Now playing" + (now.title.isEmpty() ? "" : ": " + now.title + who) + (now.playing() ? "" : ". Paused")
                + ". Left and Right choose a control, OK presses it, Back returns.");
        stage.invalidate();
        handler.removeCallbacks(tick); handler.postDelayed(tick, 1000);
    }

    private void show(Mode next) {
        if (next == mode) return;
        mode = next;
        if (next == Mode.PLAYING) {
            if (stage == null) stage = new Stage(this);
            setContentView(stage); stage.requestFocus(); return;
        }
        if (next == Mode.ACCESS) access();
        else nothing();
    }

    /** Access not yet granted: a plain explanation, the setting, and the one ADB line for TVs without that setting. */
    private void access() {
        page("Now playing");
        text("To show what's playing, allow Unified TV to see media notifications", 24);
        text("Amazon has no public API, so this reads the TV's own media controls. Unified TV doesn't read, keep or send your notifications.", 19);
        button("Allow in Settings", () -> {
            if (!openAccessSettings()) note("This TV doesn't offer that setting. Use the computer step below.");
        });
        text("If your TV doesn't offer this setting, turn on network debugging, then run this one line from a computer on the same network:", 19);
        android.widget.TextView line = text(NowPlaying.adbLine(MediaWatch.component(this).flattenToString()), 18);
        line.setTypeface(Typeface.MONOSPACE);
        text("Turn network debugging off again afterwards.", 17);
        button("Back", this::finish);
    }
    private boolean openAccessSettings() {
        ComponentName me = MediaWatch.component(this);
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, me.flattenToString());
            if (start(detail)) return true;
        }
        return start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }
    private boolean start(Intent i) {
        // System settings: in Kids only behind the adult PIN, then the Kids guard lets them stay open a while.
        if (Profiles.kids(this)) { if (i.resolveActivity(getPackageManager()) == null) return false; Launch.system(this, i, "This TV doesn't offer that setting"); return true; }
        try { startActivity(i); return true; } catch (ActivityNotFoundException | SecurityException e) { return false; }
    }

    /** Access granted but no music session: say so plainly and offer the official app. */
    private void nothing() {
        page("Now playing");
        text("Nothing is playing", 30);
        text("Start something in Amazon Music and it appears here.", 20);
        button(MusicActivity.find(this) != null ? "Open Amazon Music" : "Find Amazon Music", () -> MusicActivity.open(this));
        button("Back", this::finish);
        text("Amazon has no public API, so this reads the TV's own media controls.", 17);
    }

    private void press(int which) {
        if (which == SETTINGS) { EditLock.ask(this, () -> startActivity(new Intent(this, SettingsActivity.class))); return; }
        if (controller == null || now == null) return;
        MediaController.TransportControls tc = controller.getTransportControls();
        try {
            switch (which) {
                case PREVIOUS: if (now.offers(NowPlaying.ACTION_SKIP_TO_PREVIOUS)) tc.skipToPrevious(); else unavailable("Previous"); break;
                case PLAY: if (now.playing()) tc.pause(); else tc.play(); break;
                case NEXT: if (now.offers(NowPlaying.ACTION_SKIP_TO_NEXT)) tc.skipToNext(); else unavailable("Next"); break;
                case SHUFFLE: {
                    PlaybackState.CustomAction a = shuffle();
                    if (a != null) tc.sendCustomAction(a, null); else unavailable("Shuffle");
                    break;
                }
                default: break;
            }
        } catch (RuntimeException e) { note((appLabel.isEmpty() ? "The app" : appLabel) + " didn't respond"); }
    }
    private void unavailable(String what) { note((appLabel.isEmpty() ? "This app" : appLabel) + " doesn't offer " + what + " to the TV's media controls"); }
    /** Shuffle only when the app itself offers a shuffle action; its current setting isn't reported, so none is shown. */
    private PlaybackState.CustomAction shuffle() {
        try {
            PlaybackState s = controller == null ? null : controller.getPlaybackState(); if (s == null) return null;
            List<PlaybackState.CustomAction> list = s.getCustomActions();
            String[] ids = new String[list.size()], names = new String[list.size()];
            for (int i = 0; i < list.size(); i++) { ids[i] = list.get(i).getAction(); names[i] = String.valueOf(list.get(i).getName()); }
            int i = NowPlaying.shuffleAction(ids, names); return i < 0 ? null : list.get(i);
        } catch (RuntimeException e) { return null; }
    }

    /** The custom-drawn screen on the 1920 x 1080 canvas, like Home. */
    private final class Stage extends View {
        private static final int W = 1920, H = 1080, WHITE = 0xFFFAFAFC, MUTED = 0xFFC8CCD5, INK = 0xFF101827;
        private static final float ART_L = 170, ART_T = 250, ART = 560, COL = 900, COL_R = 1824, BAR_T = 742, BAR_H = 124, CELL = 120, GAP = 16, PAD = 22;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint type = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private final SharedPreferences prefs;

        Stage(Context c) {
            super(c);
            prefs = c.getSharedPreferences("unified-tv-preview", Context.MODE_PRIVATE);
            setFocusable(true); setFocusableInTouchMode(true);
        }
        private boolean solid() { return prefs.getBoolean("opaque", false) || prefs.getBoolean("highContrast", false); }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: if (focus == SETTINGS) return true; focus = Math.max(PREVIOUS, focus - 1); invalidate(); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: if (focus == SETTINGS) return true; focus = Math.min(SHUFFLE, focus + 1); invalidate(); return true;
                case KeyEvent.KEYCODE_DPAD_UP: focus = SETTINGS; invalidate(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: if (focus == SETTINGS) { focus = PLAY; invalidate(); } return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (event.getRepeatCount() == 0) press(focus); return true;
                default: return super.onKeyDown(keyCode, event);
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.save(); c.scale(getWidth() / (float) W, getHeight() / (float) H);
            background(c); header(c); artwork(c); details(c); controls(c);
            text(c, "Amazon has no public API, so this reads the TV's own media controls.", 96, 1010, 20, MUTED, false, Paint.Align.LEFT, W);
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
            SimpleDateFormat df = new SimpleDateFormat("HH:mm", Locale.UK); df.setTimeZone(TimeZone.getTimeZone("Europe/London"));
            text(c, df.format(System.currentTimeMillis()), 96, 142, 66, WHITE, false, Paint.Align.LEFT, W);
            type.setTextSize(24); type.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float right = 1730, left = right - type.measureText("Music");
            glass(c, left - 32, 62, 1817, 145, 30);
            text(c, "Music", left, 113, 24, WHITE, true, Paint.Align.LEFT, W);
            boolean on = focus == SETTINGS;
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
            if (art != null) c.drawBitmap(art, crop(art), rect, paint);
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
            String from = appLabel.isEmpty() ? "" : appLabel;
            if (!n.playing()) from = from.isEmpty() ? "Paused" : from + "  ·  Paused";
            text(c, from, COL, 330, 24, MUTED, false, Paint.Align.LEFT, width);
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

        /** One glass bar: previous, play or pause, next, shuffle. The focused control is a light disc. */
        private void controls(Canvas c) {
            float right = COL + PAD * 2 + CELL * 4 + GAP * 3;
            glass(c, COL, BAR_T, right, BAR_T + BAR_H, BAR_H / 2);
            NowPlaying n = now; boolean shuffle = shuffleOffered;
            for (int i = PREVIOUS; i <= SHUFFLE; i++) {
                float cx = COL + PAD + CELL / 2 + i * (CELL + GAP), cy = BAR_T + BAR_H / 2;
                boolean on = focus == i;
                boolean offered = n != null && (i == PREVIOUS ? n.offers(NowPlaying.ACTION_SKIP_TO_PREVIOUS) : i == NEXT ? n.offers(NowPlaying.ACTION_SKIP_TO_NEXT) : i != SHUFFLE || shuffle);
                if (on) focusRing(c, cx, cy, i == PLAY ? 50 : 44);
                int colour = on ? INK : WHITE, alpha = offered ? 240 : 95;
                if (i == PREVIOUS) skip(c, cx, cy, -1, colour, alpha);
                else if (i == NEXT) skip(c, cx, cy, 1, colour, alpha);
                else if (i == PLAY) { if (n != null && n.playing()) pause(c, cx, cy, colour, alpha); else play(c, cx, cy, colour, alpha); }
                else shuffleGlyph(c, cx, cy, colour, alpha);
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
        private void shuffleGlyph(Canvas c, float cx, float cy, int colour, int alpha) {
            glyphPaint(colour, alpha, false); path.reset();
            path.moveTo(cx - 19, cy - 10); path.lineTo(cx - 9, cy - 10); path.cubicTo(cx + 1, cy - 10, cx + 1, cy + 10, cx + 11, cy + 10); path.lineTo(cx + 18, cy + 10);
            path.moveTo(cx - 19, cy + 10); path.lineTo(cx - 9, cy + 10); path.cubicTo(cx + 1, cy + 10, cx + 1, cy - 10, cx + 11, cy - 10); path.lineTo(cx + 18, cy - 10);
            path.moveTo(cx + 13, cy - 15); path.lineTo(cx + 18, cy - 10); path.lineTo(cx + 13, cy - 5);
            path.moveTo(cx + 13, cy + 5); path.lineTo(cx + 18, cy + 10); path.lineTo(cx + 13, cy + 15);
            c.drawPath(path, icon);
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
}
