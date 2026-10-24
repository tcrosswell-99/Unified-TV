package io.cleanyourtv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A shelf tile the person styled: a logo (a PNG from a URL, a file pushed with launcher-ui/config.sh, or the app's own
 * icon) on a background in their color, drawn like the built-in brands. Set in config.json "app_tiles" or on the TV
 * (hold OK on a shelf app, or Settings > Shelf and Menu on an app).
 */
final class CustomTiles {
    private CustomTiles() {}

    /** {logo source, "#RRGGBB"} for the package, or null when it keeps its own look. */
    static String[] get(String pkg) {
        String v = Prefs.tile(pkg);
        if (v.isEmpty()) return null;
        int bar = v.lastIndexOf('|');
        return bar < 0 ? new String[] {v, ""} : new String[] {v.substring(0, bar), v.substring(bar + 1)};
    }

    static int color(String pkg) {
        String[] t = get(pkg);
        try {
            return t == null || t[1].isEmpty() ? 0xFF1C1C1E : Color.parseColor(t[1]);
        } catch (IllegalArgumentException e) {
            return 0xFF1C1C1E;
        }
    }

    /** The tile's colors, top and bottom, like HomeView's built-in styles; the logo is drawn as it is. */
    static int[] style(String pkg) {
        int top = color(pkg);
        float[] hsv = new float[3];
        Color.colorToHSV(top, hsv);
        hsv[2] *= 0.62f;
        return new int[] {top, Color.HSVToColor(hsv), 0};
    }

    /** A logo larger than this download is refused (a tile logo is a few hundred KB at most). */
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    /** The largest side a logo is kept at. */
    private static final int FIT = 640;

    /**
     * The logo: the cached download, the pushed file, or the app's icon. A URL not cached yet is downloaded only when
     * `download` is true (the shelf's background load); until then this returns null and the shelf keeps the TV art.
     * A URL that answered but gave no usable picture (too big, not an image, 4xx) leaves a ".none" file beside the
     * cache, like HomeApps' lifted logos, so it isn't fetched again on every resume; saving the tile again retries it.
     */
    static Bitmap logo(Context c, String pkg, boolean download) {
        String[] t = get(pkg);
        if (t == null) return null;
        String source = t[0].trim();
        if (source.isEmpty()) return icon(c, pkg);
        if (!source.startsWith("https://")) {
            if (!TvConfig.allowedName(source)) return null;
            return decode(new File(TvConfig.dir(c), new File(source).getName()));
        }
        File cached = cacheFile(c, pkg, source), none = new File(cached.getPath() + ".none");
        if (cached.isFile()) return decode(cached);
        if (!download || none.exists()) return null;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(source).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            byte[] bytes;
            try {
                int code = conn.getResponseCode();
                if (code >= 400 && code < 500) {
                    AppLog.w("tile logo for " + pkg + ": HTTP " + code);
                    markNone(none);
                    return null;
                }
                try (InputStream in = conn.getInputStream()) {
                    bytes = conn.getContentLengthLong() > MAX_BYTES ? null : readCapped(in);
                }
            } finally {
                conn.disconnect();
            }
            Bitmap b = bytes == null ? null : decode(bytes);
            if (b == null) {
                AppLog.w("tile logo for " + pkg + ": not a picture, or over " + (MAX_BYTES >> 20) + " MB");
                markNone(none);
                return null;
            }
            //noinspection ResultOfMethodCallIgnored
            cached.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(cached)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return b;
        } catch (Exception e) {   // offline or a server error: tried again on the next load
            AppLog.w("tile logo for " + pkg + ": " + e.getMessage());
            return null;
        }
    }

    private static File cacheFile(Context c, String pkg, String source) {
        return new File(new File(c.getFilesDir(), "tiles"), Integer.toHexString((pkg + source).hashCode()) + ".png");
    }

    /** Saving the tile again (or a changed config.json) retries a logo that failed before. */
    static void forgetFailure(Context c, String pkg, String source) {
        source = source == null ? "" : source.trim();
        if (!source.startsWith("https://")) return;
        //noinspection ResultOfMethodCallIgnored
        new File(cacheFile(c, pkg, source).getPath() + ".none").delete();
    }

    private static void markNone(File none) {
        //noinspection ResultOfMethodCallIgnored
        none.getParentFile().mkdirs();
        try {
            //noinspection ResultOfMethodCallIgnored
            none.createNewFile();
        } catch (Exception ignored) {
        }
    }

    /** The body, or null when it's over MAX_BYTES (read no further than that). */
    private static byte[] readCapped(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > MAX_BYTES) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static Bitmap decode(File f) {
        if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (!sample(o)) return null;
        try {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            return b == null ? null : fit(b);
        } catch (OutOfMemoryError e) {
            AppLog.w("tile logo too large: " + f.getName());
            return null;
        }
    }

    private static Bitmap decode(byte[] bytes) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        if (!sample(o)) return null;
        try {
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            return b == null ? null : fit(b);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * After a bounds-only pass: false if it isn't a picture; otherwise sets up the real decode, skipping pixels by
     * powers of two while the picture stays at least FIT on its longer side (fit() then scales to FIT exactly, as
     * before), so a huge logo never lands in memory at full size. A logo up to FIT is decoded as it is.
     */
    private static boolean sample(BitmapFactory.Options o) {
        if (o.outWidth <= 0 || o.outHeight <= 0) return false;
        int largest = Math.max(o.outWidth, o.outHeight), s = 1;
        while (largest / (s * 2) >= FIT) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return true;
    }

    private static Bitmap fit(Bitmap b) {
        int largest = Math.max(b.getWidth(), b.getHeight());
        if (largest <= FIT) return b;
        float s = (float) FIT / largest;
        return Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * s), Math.round(b.getHeight() * s), true);
    }

    private static Bitmap icon(Context c, String pkg) {
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 384, 384);
            d.draw(new Canvas(b));
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** The logo-and-color form; `saved` runs after Save or Original (null: nothing to refresh). */
    static void edit(Activity a, String pkg, String label, Runnable saved) {
        String[] now = get(pkg);
        float k = TvUi.k(a);
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * k);
        form.setPadding(pad, pad, pad, 0);
        EditText url = new EditText(a);
        url.setSingleLine(true);
        url.setHint(Ui.tr("URL de um PNG (vazio: ícone do app)", "A PNG's URL (empty: the app's icon)"));
        url.setText(now == null ? "" : now[0]);
        EditText color = new EditText(a);
        color.setSingleLine(true);
        color.setHint(Ui.tr("Cor de fundo, ex.: #E53935", "Background color, e.g. #E53935"));
        color.setText(now == null ? "" : now[1]);
        form.addView(url);
        form.addView(color);
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Ui.tr("Logo e cor: ", "Logo and color: ") + label)
                .setView(form)
                .setPositiveButton(Ui.tr("Salvar", "Save"), (d, w) -> {
                    String hex = color.getText().toString().trim();
                    if (!hex.isEmpty() && !hex.startsWith("#")) hex = "#" + hex;
                    try {
                        if (!hex.isEmpty()) Color.parseColor(hex);
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(a, Ui.tr("Cor inválida: use #RRGGBB.", "Invalid color: use #RRGGBB."), Toast.LENGTH_LONG).show();
                        return;
                    }
                    forgetFailure(a, pkg, url.getText().toString());
                    Prefs.setTile(pkg, url.getText().toString(), hex.isEmpty() ? "#1C1C1E" : hex);
                    if (saved != null) saved.run();
                })
                .setNeutralButton(Ui.tr("Original", "Original"), (d, w) -> {
                    Prefs.setTile(pkg, null, null);
                    if (saved != null) saved.run();
                })
                .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                .show();
    }
}
