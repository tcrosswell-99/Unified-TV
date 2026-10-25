package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Unified TV Wallpaper (spec 33; OD-03, OD-04): full-screen bundled artwork with a clock, no dock or status chrome. Not
 * Samsung Art Mode and never pretends to be. The first key press of any kind closes it and shows Home as it was, without
 * launching anything. Pictures come only from assets/wallpaper/catalogue.json (licences in docs/design/ARTWORK.md);
 * nothing is downloaded. Images decode off the UI thread at no more than 1920 × 1080.
 */
public final class WallpaperActivity extends Activity {
    private static final long FADE_MS = 900;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<JSONObject> pictures = new ArrayList<>();
    private Scene scene; private SharedPreferences prefs; private int next, slides, ticket; private boolean active;
    private final Runnable advance = new Runnable() { @Override public void run() { load(); timer.postDelayed(this, WallpaperPolicy.slide(prefs.getInt("wallpaperSlide", 0)) * 1000L); } };
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
    }
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
    @Override protected void onResume() { super.onResume(); active = true; timer.post(advance); timer.post(minute); }
    @Override protected void onPause() { active = false; ticket++; timer.removeCallbacksAndMessages(null); if (scene != null) scene.animate().cancel(); super.onPause(); }
    @Override protected void onDestroy() { timer.removeCallbacksAndMessages(null); loader.shutdownNow(); super.onDestroy(); }

    /** Any key closes the Wallpaper and reveals Home; the press is used for that alone (spec 33.5, WP-07). */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        // Volume, mute and power stay with the TV.
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE || k == KeyEvent.KEYCODE_POWER) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent e) { finish(); return true; }

    private void load() {
        if (!active || pictures.isEmpty()) return;
        final JSONObject picture = pictures.get(next++ % pictures.size()); final int mine = ++ticket; final int slide = slides++;
        loader.execute(() -> {
            Slide s = Slide.decode(this, picture, slide, WallpaperPolicy.clock(prefs.getString("wallpaperClock", null)));
            long ms = WallpaperPolicy.slide(prefs.getInt("wallpaperSlide", 0)) * 1000L;
            runOnUiThread(() -> { if (active && mine == ticket && s != null) scene.show(s, !prefs.getBoolean("reduceMotion", false), ms); });
        });
    }

    /** One decoded picture with everything measured once: where the clock goes, and the cut-out for the depth clock. */
    static final class Slide {
        Bitmap image, foreground; String clock; int[] spot; boolean shade;
        static Slide decode(Context c, JSONObject p, int slide, String clockSetting) {
            try {
                Slide s = new Slide();
                s.image = read(c, p.getString("file"), Bitmap.Config.ARGB_8888);
                if (s.image == null) return null;
                float[] lum = grid(s.image, false);
                float[] mask = null; Bitmap maskImage = null;
                if (!p.optString("mask").isEmpty()) {
                    maskImage = read(c, p.getString("mask"), Bitmap.Config.ARGB_8888);
                    if (maskImage != null) { maskImage = Bitmap.createScaledBitmap(maskImage, s.image.getWidth(), s.image.getHeight(), true); mask = grid(maskImage, true); }
                }
                s.clock = WallpaperPolicy.clockFor(clockSetting, mask);
                if ("depth".equals(s.clock)) s.foreground = cutOut(s.image, maskImage);
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
            int[] px = new int[WallpaperPolicy.GW * WallpaperPolicy.GH]; small.getPixels(px, 0, WallpaperPolicy.GW, 0, 0, WallpaperPolicy.GW, WallpaperPolicy.GH);
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
        void show(Slide s, boolean motion, long slideMs) {
            previous = current; current = s; fadeAt = SystemClock.uptimeMillis(); invalidate();
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
            if (t < 1) postInvalidateDelayed(16); else if (previous != null) previous = null;
        }
        private void cover(Bitmap b) {
            float w = getWidth(), h = getHeight(), scale = Math.max(w / b.getWidth(), h / b.getHeight());
            float bw = b.getWidth() * scale, bh = b.getHeight() * scale;
            dst.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        }
        private void draw(Canvas c, Slide s, int alpha) {
            cover(s.image); paint.setAlpha(alpha); c.drawBitmap(s.image, null, dst, paint);
            if (!WallpaperPolicy.NONE.equals(s.clock)) clock(c, s, alpha);
            if (s.foreground != null) { cover(s.foreground); paint.setAlpha(alpha); c.drawBitmap(s.foreground, null, dst, paint); }
            paint.setAlpha(255);
        }
        private void clock(Canvas c, Slide s, int alpha) {
            String time = new SimpleDateFormat("HH:mm", Locale.UK).format(new Date());
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
