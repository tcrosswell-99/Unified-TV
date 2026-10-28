package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Unified TV Wallpaper (spec 33; OD-03, OD-04): full-screen bundled artwork with a clock, no dock or status chrome. Not
 * Samsung Art Mode and never pretends to be. The first key press of any kind closes it and shows Home as it was, without
 * launching anything. Artwork comes only from assets/wallpaper/catalogue.json (licences in docs/design/ARTWORK.md) and
 * is never downloaded. My Photos (adult profile only; docs/design/MY_PHOTOS.md) comes from the owner's chosen NAS folders,
 * then the kept copies if allowed, then artwork: never a blank screen. Every picture decodes off the UI thread at no
 * more than 1920 × 1080; the NAS is read on threads of its own, so a slow NAS can't hold up artwork or the remote.
 */
public final class WallpaperActivity extends Activity {
    private static final long FADE_MS = 900;
    /** The longest the screen may stay empty while a picture is on its way; then artwork shows (WP-10). */
    private static final long RESCUE_MS = 2500;
    /** A first wait for the chosen folders, inside the Wallpaper's own entrance time, so a photo can show first. */
    private static final long FIRST_WAIT_MS = 1000;
    /** An absent NAS is looked for again this often; a walk of the folders is reused for this long. */
    private static final long RETRY_MS = 5 * 60_000L, KNOWN_MS = 30 * 60_000L;
    private final Handler timer = new Handler(Looper.getMainLooper());
    // Bundled artwork and kept copies (local, quick); pictures from the NAS; walking the chosen folders.
    private final ExecutorService loader = Executors.newSingleThreadExecutor(), fetcher = Executors.newSingleThreadExecutor(), finder = Executors.newSingleThreadExecutor();
    private final List<JSONObject> pictures = new ArrayList<>();
    private Scene scene; private SharedPreferences prefs; private int next, slides, ticket, photoNext, failures; private boolean active, looking;
    private long lookedAt; private String showing = WallpaperPolicy.ARTWORK; private PhotoCache cache;
    // My Photos: the server, its pictures from the last walk, whether it answered, and how many copies are kept.
    private volatile JSONObject server = new JSONObject(); private volatile List<PhotoSource.Photo> live = Collections.emptyList();
    private volatile boolean reachable, folders; private volatile int cached;
    /** The last walk, in memory only (never stored), so the next Wallpaper can start on a photo without waiting. */
    private static volatile List<PhotoSource.Photo> known; private static volatile String knownFor = ""; private static volatile long knownAt;
    /** Other folders were chosen: the next Wallpaper walks them afresh. */
    static void forgetPhotos() { known = null; knownFor = ""; }
    private final Runnable advance = new Runnable() { @Override public void run() {
        if (photos() && !looking && !reachable && SystemClock.uptimeMillis() - lookedAt > RETRY_MS) look();
        load(); timer.postDelayed(this, slideMs()); } };
    private final Runnable rescue = () -> { if (active && scene.empty()) artwork(++ticket, slides++); };
    private final Runnable minute = new Runnable() { @Override public void run() {
        if (scene != null) scene.invalidate();
        long now = System.currentTimeMillis(); timer.postDelayed(this, 60_000 - now % 60_000 + 50); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("unified-tv-preview", 0);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        scene = new Scene(this); setContentView(scene);
        pictures.addAll(catalogue(this));
        if (pictures.isEmpty()) { finish(); return; }
        next = new Random().nextInt(pictures.size());
        cache = new PhotoCache(new File(getNoBackupFilesDir(), "photos"), PhotoCache.LIMIT);
        DlnaClient.lan = LocalDiscovery.lan(this);
    }
    private String saved() { return prefs.getString("wallpaperSource", null); }
    private String profile() { return prefs.getString("profile", WallpaperPolicy.ADULT); }
    private boolean keep() { return prefs.getBoolean("wallpaperKeepPhotos", false); }
    /** My Photos for whoever is watching now: a switch to Child takes effect on the very next picture (WP-11). */
    private boolean photos() { return WallpaperPolicy.PHOTOS.equals(WallpaperPolicy.sourceFor(saved(), profile())); }
    private String from() { return WallpaperPolicy.from(saved(), profile(), folders, reachable, live.size(), keep(), cached); }
    private String clock() { return WallpaperPolicy.clock(prefs.getString("wallpaperClock", null)); }
    private long slideMs() { return WallpaperPolicy.slide(prefs.getInt("wallpaperSlide", 0)) * 1000L; }
    static List<JSONObject> catalogue(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("wallpaper/catalogue.json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) { if (bytes.size() + n > 65536) throw new java.io.IOException("Catalogue too large"); bytes.write(buf, 0, n); }
            JSONArray all = new JSONArray(bytes.toString("UTF-8"));
            for (int i = 0; i < all.length(); i++) { JSONObject p = all.optJSONObject(i); if (p != null && !p.optString("file").isEmpty()) out.add(p); }
        } catch (Exception ignored) { }
        return out;
    }
    @Override protected void onResume() {
        super.onResume(); active = true; timer.post(minute);
        boolean waiting = photos() && known == null;
        if (photos()) look();
        if (waiting && scene.empty()) timer.postDelayed(advance, FIRST_WAIT_MS); else timer.post(advance);
    }
    @Override protected void onPause() { active = false; ticket++; timer.removeCallbacksAndMessages(null); if (scene != null) scene.animate().cancel(); super.onPause(); }
    /** Out of sight (another app, the TV's own screensaver, standby): the Wallpaper ends and gives its memory back. */
    @Override protected void onStop() { super.onStop(); if (scene != null) scene.clear(); if (!isFinishing()) finish(); }
    @Override public void onTrimMemory(int level) { super.onTrimMemory(level); if (scene != null && level >= TRIM_MEMORY_RUNNING_LOW) scene.trim(); }
    @Override protected void onDestroy() { timer.removeCallbacksAndMessages(null); loader.shutdownNow(); fetcher.shutdownNow(); finder.shutdownNow(); super.onDestroy(); }

    /** Any key closes the Wallpaper and reveals Home; the press is used for that alone (spec 33.5, WP-07). */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        // Volume, mute and power stay with the TV.
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE || k == KeyEvent.KEYCODE_POWER) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent e) { finish(); return true; }

    /** The next picture from wherever the fallback order says (WallpaperPolicy.from). */
    private void load() {
        if (!active) return;
        String from = from(); final int mine = ++ticket, slide = slides++;
        if (scene.empty()) { timer.removeCallbacks(rescue); timer.postDelayed(rescue, RESCUE_MS); }
        List<PhotoSource.Photo> now = live;
        if (WallpaperPolicy.LIVE.equals(from) && !now.isEmpty()) photo(mine, slide, now.get(photoNext++ % now.size()));
        else if (WallpaperPolicy.CACHE.equals(from)) kept(mine, slide);
        else artwork(mine, slide);
    }
    private void artwork(int mine, int slide) {
        if (pictures.isEmpty()) return;
        final JSONObject picture = pictures.get(next++ % pictures.size()); final String clock = clock();
        loader.execute(() -> { Slide s = Slide.decode(this, picture, slide, clock); runOnUiThread(() -> present(mine, s, WallpaperPolicy.ARTWORK)); });
    }
    /** A kept copy, while the NAS is away. An unreadable one counts the cache as empty, and artwork follows. */
    private void kept(int mine, int slide) {
        final String clock = clock();
        loader.execute(() -> {
            List<File> files = cache.all(); Slide s = null;
            try { if (!files.isEmpty()) s = Slide.photo(decodeFile(files.get(new Random().nextInt(files.size()))), slide, clock); } catch (Exception | OutOfMemoryError ignored) { }
            Slide shown = s;
            runOnUiThread(() -> { if (shown != null) present(mine, shown, WallpaperPolicy.CACHE); else { cached = 0; if (active && mine == ticket) load(); } });
        });
    }
    /**
     * A picture from the chosen folders: its kept copy if there is one, otherwise fetched (at the size PhotoSource chose)
     * into a temporary file, decoded down to the screen, and kept only if the owner allowed it. A NAS that has gone away, or
     * three failures in a row, sends the next picture down the fallback order straight away, so nothing stays blank.
     */
    private void photo(int mine, int slide, PhotoSource.Photo p) {
        final JSONObject from = server; final boolean keep = keep(); final String clock = clock();
        fetcher.execute(() -> {
            Slide s = null; boolean away = false;
            try {
                String key = PhotoSource.key(from.optString("udn"), p.id); Bitmap image = null;
                if (keep && cache.has(key)) { File f = cache.file(key); image = decodeFile(f); if (image != null) cache.touch(f); }
                if (image == null) {
                    File tmp = new File(getCacheDir(), "wallpaper-photo.tmp");
                    try { DlnaClient.download(p.url, tmp, PhotoSource.MAX_BYTES); image = decodeFile(tmp); } finally { tmp.delete(); }
                    if (image != null && keep) { final Bitmap copy = image; try { cache.put(key, o -> copy.compress(Bitmap.CompressFormat.JPEG, 88, o)); cached = cache.all().size(); } catch (IOException ignored) { } }
                }
                s = Slide.photo(image, slide, clock);
            } catch (DlnaClient.Failure f) { away = f.kind == DlnaClient.Failure.UNREACHABLE; }
            catch (Exception | OutOfMemoryError ignored) { }
            Slide shown = s; boolean gone = away;
            runOnUiThread(() -> {
                if (shown != null) { failures = 0; present(mine, shown, WallpaperPolicy.LIVE); return; }
                if (gone || ++failures >= 3) { reachable = false; failures = 0; lookedAt = SystemClock.uptimeMillis(); }
                if (active && mine == ticket) load();
            });
        });
    }
    private void present(int mine, Slide s, String kind) {
        if (s == null) return;
        // A late picture (the slideshow moved on, or the Wallpaper is closing) is freed at once, never left for the collector.
        if (!active || mine != ticket) { s.release(); return; }
        showing = kind; timer.removeCallbacks(rescue); scene.show(s, !prefs.getBoolean("reduceMotion", false), slideMs());
    }
    /** Starts the slideshow again now, for when photos become available while artwork (or nothing) is showing. */
    private void restart() { timer.removeCallbacks(advance); timer.post(advance); }

    /**
     * Walks the chosen folders (Vault "photos", saved by PhotoFoldersActivity) on the finder thread. A NAS that moved to
     * a new address is found again by its UDN, once, as the film library does. Without "Keep a copy" any old copies are
     * deleted here too.
     */
    private void look() {
        if (looking) return; looking = true; lookedAt = SystemClock.uptimeMillis(); final boolean keep = keep();
        finder.execute(() -> {
            JSONObject record; try { record = new Vault(this).read("photos"); } catch (Exception e) { record = new JSONObject(); }
            JSONArray roots = record.optJSONArray("roots"); boolean chosen = roots != null && roots.length() > 0;
            if (!keep) cache.clear();
            int copies = keep ? cache.all().size() : 0;
            String memo = chosen ? record.optString("udn") + "\n" + roots : "";
            List<PhotoSource.Photo> found = Collections.emptyList(); boolean answered = false; List<PhotoSource.Photo> k = known;
            if (chosen && k != null && memo.equals(knownFor) && System.currentTimeMillis() - knownAt < KNOWN_MS) { found = k; answered = true; }
            else if (chosen) {
                try { found = walk(record, roots); answered = true; }
                catch (Exception e) {
                    try { JSONObject moved = e instanceof DlnaClient.Failure && ((DlnaClient.Failure) e).kind == DlnaClient.Failure.UNREACHABLE ? DlnaClient.relocate(getApplicationContext(), record) : null;
                        if (moved != null) { found = walk(moved, roots); answered = true; record = moved; try { new Vault(this).save("photos", moved); } catch (Exception ignored) { } } }
                    catch (Exception ignored) { }
                }
                if (answered) { found = new ArrayList<>(found); Collections.shuffle(found); known = found; knownFor = memo; knownAt = System.currentTimeMillis(); }
            }
            final JSONObject used = record; final List<PhotoSource.Photo> result = found; final boolean ok = answered;
            runOnUiThread(() -> {
                looking = false; server = used; folders = chosen; cached = copies; live = result; reachable = ok; failures = 0; photoNext = 0;
                if (active && (scene.empty() || WallpaperPolicy.ARTWORK.equals(showing)) && !WallpaperPolicy.ARTWORK.equals(from())) restart();
            });
        });
    }
    private static List<PhotoSource.Photo> walk(JSONObject record, JSONArray roots) throws Exception {
        java.net.URI origin = DlnaClient.local(record.getString("control"));
        return PhotoSource.collect(roots, (id, offset) -> PhotoSource.parse(DlnaClient.browseResult(record, id, offset, PhotoSource.PAGE), origin));
    }
    /** A picture file decoded to at most one screen (power-of-two steps), turned upright from its EXIF orientation. */
    static Bitmap decodeFile(File f) {
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false; o.inSampleSize = PhotoSource.sampleSize(o.outWidth, o.outHeight); o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o); if (b == null) return null;
        int tag = 1; try { tag = new android.media.ExifInterface(f.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1); } catch (Exception ignored) { }
        int[] turn = PhotoSource.orientation(tag); if (turn[0] == 0 && turn[1] == 0) return b;
        Matrix m = new Matrix(); if (turn[0] != 0) m.postRotate(turn[0]); if (turn[1] != 0) m.postScale(-1, 1);
        Bitmap upright = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true); if (upright != b) b.recycle(); return upright;
    }

    /** One decoded picture with everything measured once: where the clock goes, and the cut-out for the depth clock. */
    static final class Slide {
        Bitmap image, foreground, fill; String clock; int[] spot; boolean shade, fit;
        /** Frees the pixels now rather than whenever the collector runs: the TCL has little memory to spare. */
        void release() { for (Bitmap b : new Bitmap[]{image, foreground, fill}) if (b != null && !b.isRecycled()) b.recycle(); image = foreground = fill = null; }
        /**
         * One of the owner's photos: no prepared mask, so the simple clock (or none) in the calmest part of what is on screen.
         * A portrait photo is shown whole over a soft, dimmed fill made from itself: a tiny copy stretched by the GPU.
         */
        static Slide photo(Bitmap image, int slide, String clockSetting) {
            if (image == null) return null;
            try {
                Slide s = new Slide(); s.image = image; s.fit = WallpaperPolicy.letterbox(image.getWidth(), image.getHeight());
                float[] lum;
                if (s.fit) {
                    s.fill = Bitmap.createScaledBitmap(image, 24, Math.max(1, Math.round(24f * image.getHeight() / image.getWidth())), true);
                    // Measure the composed screen, fill and all, so the clock lands on what the viewer actually sees.
                    Bitmap screen = Bitmap.createBitmap(WallpaperPolicy.GW * 8, WallpaperPolicy.GH * 8, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(screen); Paint p = new Paint(Paint.FILTER_BITMAP_FLAG); RectF r = new RectF();
                    place(r, s.fill, screen.getWidth(), screen.getHeight(), false); p.setColorFilter(DIM); c.drawBitmap(s.fill, null, r, p); p.setColorFilter(null);
                    place(r, image, screen.getWidth(), screen.getHeight(), true); c.drawBitmap(image, null, r, p);
                    lum = grid(screen, false); screen.recycle();
                } else lum = grid(image, false);
                s.clock = WallpaperPolicy.clockFor(clockSetting, null);
                s.spot = WallpaperPolicy.spot(lum, slide); s.shade = WallpaperPolicy.needsShade(lum, s.spot);
                return s;
            } catch (Exception | OutOfMemoryError e) { return null; }
        }
        static final ColorFilter DIM = new LightingColorFilter(0xff707070, 0);
        /** Where a picture goes on a w × h screen: filling it (cropped), or whole inside it. */
        static void place(RectF dst, Bitmap b, float w, float h, boolean whole) {
            float scale = whole ? Math.min(w / b.getWidth(), h / b.getHeight()) : Math.max(w / b.getWidth(), h / b.getHeight());
            float bw = b.getWidth() * scale, bh = b.getHeight() * scale;
            dst.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        }
        static Slide decode(Context c, JSONObject p, int slide, String clockSetting) {
            try {
                Slide s = new Slide();
                s.image = read(c, p.getString("file"), Bitmap.Config.RGB_565);
                if (s.image == null) return null;
                float[] lum = grid(s.image, false);
                float[] mask = null; Bitmap maskImage = null;
                if (!p.optString("mask").isEmpty()) {
                    maskImage = read(c, p.getString("mask"), Bitmap.Config.RGB_565);
                    if (maskImage != null) { Bitmap scaled = Bitmap.createScaledBitmap(maskImage, s.image.getWidth(), s.image.getHeight(), true); if (scaled != maskImage) maskImage.recycle(); maskImage = scaled; mask = grid(maskImage, true); }
                }
                s.clock = WallpaperPolicy.clockFor(clockSetting, mask);
                if ("depth".equals(s.clock)) s.foreground = cutOut(s.image, maskImage);
                if (maskImage != null) maskImage.recycle();
                s.spot = WallpaperPolicy.spot(lum, slide);
                s.shade = WallpaperPolicy.needsShade(lum, s.spot);
                return s;
            } catch (Exception | OutOfMemoryError e) { return null; }
        }
        private static Bitmap read(Context c, String file, Bitmap.Config config) throws Exception {
            if (!file.matches("[a-z0-9_/.-]+\\.(jpg|jpeg|png|webp)")) return null;   // bundled assets only
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            try (InputStream in = c.getAssets().open(file)) { BitmapFactory.decodeStream(in, null, o); }
            o.inJustDecodeBounds = false; o.inSampleSize = 1; o.inPreferredConfig = config;
            while (o.outWidth / (o.inSampleSize * 2) >= 1920 || o.outHeight / (o.inSampleSize * 2) >= 1080) o.inSampleSize *= 2;
            try (InputStream in = c.getAssets().open(file)) { return BitmapFactory.decodeStream(in, null, o); }
        }
        /** GW × GH averages: brightness of the picture, or how much of each cell the mask's subject covers. */
        static float[] grid(Bitmap b, boolean mask) {
            Bitmap small = Bitmap.createScaledBitmap(b, WallpaperPolicy.GW, WallpaperPolicy.GH, true);
            int[] px = new int[WallpaperPolicy.GW * WallpaperPolicy.GH]; small.getPixels(px, 0, WallpaperPolicy.GW, 0, 0, WallpaperPolicy.GW, WallpaperPolicy.GH); if (small != b) small.recycle();
            float[] out = new float[px.length];
            for (int i = 0; i < px.length; i++) {
                int r = (px[i] >> 16) & 255, g = (px[i] >> 8) & 255, bl = px[i] & 255;
                float lum = (0.2126f * r + 0.7152f * g + 0.0722f * bl) / 255f;
                out[i] = mask ? lum * ((px[i] >>> 24) / 255f) : lum;
            }
            return out;
        }
        /** The subject alone (white in the mask), to draw over the clock so the clock sits behind it. */
        private static Bitmap cutOut(Bitmap image, Bitmap mask) {
            Bitmap alpha = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(alpha); Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            // The mask's brightness becomes the cut-out's opacity.
            p.setColorFilter(new ColorMatrixColorFilter(new float[]{0,0,0,0,0, 0,0,0,0,0, 0,0,0,0,0, .2126f,.7152f,.0722f,0,0}));
            c.drawBitmap(mask, 0, 0, p);
            Bitmap out = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas o = new Canvas(out); o.drawBitmap(image, 0, 0, null);
            Paint in = new Paint(); in.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN)); o.drawBitmap(alpha, 0, 0, in);
            alpha.recycle(); return out;
        }
    }

    /** Draws the current picture (crossfading from the last), the clock, and for the depth clock the subject in front. */
    static final class Scene extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint type = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF dst = new RectF();
        private Slide current, previous; private long fadeAt; private final long started = SystemClock.uptimeMillis();
        Scene(Context c) { super(c); setBackgroundColor(Color.BLACK); type.setColor(Color.WHITE); type.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); }
        boolean empty() { return current == null; }
        /** Drops every picture (leaving, or Android asking for memory back). */
        void clear() { Slide a = previous, b = current; previous = null; current = null; invalidate(); post(() -> { if (a != null) a.release(); if (b != null) b.release(); }); }
        /** Drops only the picture fading out. */
        void trim() { Slide a = previous; previous = null; if (a != null) post(a::release); invalidate(); }
        void show(Slide s, boolean motion, long slideMs) {
            // A slide replaced mid-fade is never drawn again; free it once this frame is done with it.
            Slide dropped = previous; previous = current; current = s; fadeAt = SystemClock.uptimeMillis(); invalidate();
            if (dropped != null && dropped != previous && dropped != current) post(dropped::release);
            // A slow drift in scale, in and out on alternate slides so it never jumps back; run by the system's animator,
            // not by redrawing the picture. Off with Reduce motion.
            animate().cancel();
            if (!motion) { setScaleX(1f); setScaleY(1f); return; }
            float target = getScaleX() < 1.015f ? 1.03f : 1f;
            animate().scaleX(target).scaleY(target).setDuration(slideMs).setInterpolator(new android.view.animation.LinearInterpolator()).start();
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (current == null) return;
            long now = SystemClock.uptimeMillis();
            float t = Math.min(1f, (now - fadeAt) / (float) FADE_MS);
            if (previous != null && t < 1) draw(c, previous, 255);
            draw(c, current, Math.round(255 * t));
            if (t < 1) postInvalidateDelayed(16); else if (previous != null) { Slide done = previous; previous = null; post(done::release); }
        }
        private void cover(Bitmap b) { Slide.place(dst, b, getWidth(), getHeight(), false); }
        private void draw(Canvas c, Slide s, int alpha) {
            if (s.fit && s.fill != null) {
                cover(s.fill); paint.setAlpha(alpha); paint.setColorFilter(Slide.DIM); c.drawBitmap(s.fill, null, dst, paint); paint.setColorFilter(null);
                Slide.place(dst, s.image, getWidth(), getHeight(), true);
            } else cover(s.image);
            paint.setAlpha(alpha); c.drawBitmap(s.image, null, dst, paint);
            if (!WallpaperPolicy.NONE.equals(s.clock)) clock(c, s, alpha);
            if (s.foreground != null) { cover(s.foreground); paint.setAlpha(alpha); c.drawBitmap(s.foreground, null, dst, paint); }
            paint.setAlpha(255);
        }
        private void clock(Canvas c, Slide s, int alpha) {
            String time = HouseholdSettings.time(getContext(), System.currentTimeMillis());
            float w = getWidth(), h = getHeight(), cellW = w / WallpaperPolicy.GW, cellH = h / WallpaperPolicy.GH;
            // Long idle sessions dim the clock (OLED; spec 33.6).
            boolean late = SystemClock.uptimeMillis() - started > 30 * 60_000L;
            int clockAlpha = Math.round(alpha * (late ? .55f : .9f));
            if ("depth".equals(s.clock)) {
                int[] r = WallpaperPolicy.DEPTH_SPOT; float size = (r[3] - r[1]) * cellH * .9f;
                type.setTextSize(size); type.setTextAlign(Paint.Align.CENTER); type.setShadowLayer(0, 0, 0, 0);
                type.setAlpha(Math.round(clockAlpha * .85f));
                c.drawText(time, (r[0] + r[2]) / 2f * cellW, r[1] * cellH + size * .86f, type);
            } else {
                int[] r = s.spot; float size = cellH * 1.25f, x = (r[0] + r[2]) / 2f * cellW, y = (r[1] + r[3]) / 2f * cellH + size * .35f;
                if (s.shade) {
                    paint.setShader(new RadialGradient(x, y - size * .35f, size * 2.2f, new int[]{0x66000000, 0}, null, Shader.TileMode.CLAMP));
                    paint.setAlpha(alpha); c.drawCircle(x, y - size * .35f, size * 2.2f, paint); paint.setShader(null);
                }
                type.setTextSize(size); type.setTextAlign(Paint.Align.CENTER); type.setShadowLayer(size * .06f, 0, size * .02f, 0x80000000);
                type.setAlpha(clockAlpha); c.drawText(time, x, y, type);
            }
        }
    }
}
