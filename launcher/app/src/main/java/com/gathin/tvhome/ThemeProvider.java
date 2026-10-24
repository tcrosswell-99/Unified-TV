package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lets tools/theme.sh write the theme files into {@link Config#dir} on firmware where the adb shell is
 * locked out of Android/data (TCL G07, Android 11). Only the files {@link ThemeFiles} allows, and only for
 * callers holding WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps cannot get.
 *
 *   content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.gathin.tvhome.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content delete --uri content://com.gathin.tvhome.theme/config.json
 *   content delete --uri content://com.gathin.tvhome.theme/wallpapers      (every image in the collection)
 *
 * When the shell closes a config.json it wrote, the file's SHA-256 is recorded in internal storage: on Android 10,
 * where other apps can write Android/data too, Config applies only that file (ThemeTrust).
 */
public class ThemeProvider extends ContentProvider {

    private File file(Uri uri) throws FileNotFoundException {
        String path = ThemeFiles.path(uri.getPathSegments());
        File dir = Config.dir(getContext());
        if (dir == null || path == null) throw new FileNotFoundException(String.valueOf(uri));
        File f = new File(dir, path);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (mode.contains("w")) {
            int write = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
            if (!Config.FILE_NAME.equals(f.getName())) {
                return ParcelFileDescriptor.open(f, write);
            }
            // Recorded once the writer closes it; a writer that dies part-way leaves the old record, so its
            // half-written file is not applied on Android 10.
            try {
                return ParcelFileDescriptor.open(f, write, new Handler(Looper.getMainLooper()), e -> {
                    if (e == null) record(f);
                });
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File dir = Config.dir(getContext());
        if (dir != null && ThemeFiles.isCollection(uri.getPathSegments())) {
            File[] images = new File(dir, ThemeFiles.WALLPAPERS).listFiles((d, name) -> ThemeFiles.collectionImage(name));
            int n = 0;
            if (images != null) for (File f : images) if (f.delete()) n++;
            return n;
        }
        try {
            File f = file(uri);
            if (Config.FILE_NAME.equals(f.getName())) new File(getContext().getFilesDir(), ThemeTrust.RECORD_NAME).delete();
            return f.delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    /** Notes the SHA-256 of the config.json just written, for Config's check on Android 10. */
    private void record(File config) {
        byte[] bytes = Config.bytes(config);
        String sha = bytes == null ? null : ThemeTrust.sha256(bytes);
        File record = new File(getContext().getFilesDir(), ThemeTrust.RECORD_NAME);
        if (sha == null) {
            record.delete();
            return;
        }
        try (FileOutputStream out = new FileOutputStream(record)) {
            out.write(sha.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            Log.w("ThemeProvider", "could not record " + config + ": " + e.getMessage());
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
