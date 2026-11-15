package io.unifiedtv.preview;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * The playing track's own artwork for Now Playing (AGENTS rule 4: contextual artwork only), as the player hands it over:
 * a picture in its media session, or an address in it (OD-27). Never looked up elsewhere, never another album's art.
 *
 * <p>Always called off the main thread. Decoded at the size it is shown (Images), at 16 bits a pixel on a 2 GB TV
 * (MemoryBudget). An address is read only when it is the app's own content (content:, android.resource:), an https
 * address, or ha: (Home Assistant's own picture for an Echo, read through LocalApi with the token in a header, never in
 * the address). At most {@link #MAX_BYTES}; plain http, files and anything else are refused.
 */
final class MusicArt {
    private MusicArt() {}
    static final int MAX_BYTES = 6 * 1024 * 1024, CONNECT_MS = 5000, READ_MS = 8000;
    static final String HA = "ha:";

    /** Reads an artwork address into bytes; tests replace it. */
    interface Fetcher { byte[] fetch(Context c, String uri) throws Exception; }
    static volatile Fetcher fetcher = MusicArt::platform;

    static boolean allowed(String uri) {
        if (uri == null) return false;
        String u = uri.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("content://") || u.startsWith("android.resource://") || u.startsWith("https://") || (u.startsWith(HA + "/") && !u.startsWith(HA + "//"));
    }

    /**
     * The art for {@code s}, at most {@code width} x {@code height}; null when the player gave none or it couldn't be read.
     * Never on the main thread.
     */
    static Bitmap load(Context c, MusicSession s, int width, int height) { return load(c, s.bitmap(), s.artUri(), width, height); }
    /** The same for a library entry's own picture or address. */
    static Bitmap load(Context c, Bitmap b, String uri, int width, int height) {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("artwork is decoded off the main thread");
        boolean low = MemoryBudget.get(c).opaque565();
        if (b != null) return fit(b, width, height, low);
        if (!allowed(uri)) return null;
        try {
            byte[] data = fetcher.fetch(c, uri.trim());
            return data == null || data.length == 0 ? null : Images.decode(data, width, height, low);
        } catch (Exception e) { return null; }
    }

    /** A picture the session handed over, scaled down to the box (a copy; the session's own is never changed). */
    static Bitmap fit(Bitmap b, int width, int height, boolean low) {
        if (b.isRecycled()) return null;
        int[] plan = Images.plan(b.getWidth(), b.getHeight(), width, height);
        boolean smaller = plan[1] < b.getWidth() || plan[2] < b.getHeight();
        if (!smaller && !(low && b.getConfig() == Bitmap.Config.ARGB_8888 && !b.hasAlpha())) return b;
        Bitmap scaled = smaller ? Bitmap.createScaledBitmap(b, plan[1], plan[2], true) : b;
        if (low && !scaled.hasAlpha() && scaled.getConfig() != Bitmap.Config.RGB_565) {
            Bitmap small = scaled.copy(Bitmap.Config.RGB_565, false);
            if (small != null) { if (scaled != b) scaled.recycle(); return small; }
        }
        return scaled;
    }

    /** Home Assistant's picture path without any token in it: the request carries the token in its header instead. */
    static String withoutToken(String path) {
        int q = path.indexOf('?'); if (q < 0) return path;
        StringBuilder kept = new StringBuilder();
        for (String part : path.substring(q + 1).split("&")) {
            if (part.isEmpty() || part.toLowerCase(Locale.ROOT).startsWith("token=")) continue;
            kept.append(kept.length() == 0 ? "?" : "&").append(part);
        }
        return path.substring(0, q) + kept;
    }

    private static byte[] platform(Context c, String uri) throws Exception {
        if (uri.startsWith(HA)) return LocalApi.bytes(new Vault(c).read("ha"), withoutToken(uri.substring(HA.length())));
        Uri u = Uri.parse(uri);
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals(ContentResolver.SCHEME_CONTENT) || scheme.equals(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
            try (InputStream in = c.getContentResolver().openInputStream(u)) { return read(in); }
        }
        if (!scheme.equals("https")) throw new IOException("Not an allowed address");
        HttpURLConnection h = (HttpURLConnection) new URL(uri).openConnection();
        try {
            h.setConnectTimeout(CONNECT_MS); h.setReadTimeout(READ_MS); h.setInstanceFollowRedirects(true);
            int code = h.getResponseCode(); if (code < 200 || code >= 300) throw new IOException("Artwork not available");
            try (InputStream in = h.getInputStream()) { return read(in); }
        } finally { h.disconnect(); }
    }
    static byte[] read(InputStream in) throws IOException {
        if (in == null) throw new IOException("No artwork");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n;
        while ((n = in.read(b)) != -1) { if (out.size() + n > MAX_BYTES) throw new IOException("Artwork too large"); out.write(b, 0, n); }
        return out.toByteArray();
    }
}
