package io.github.nickld.frigateglance;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * install.sh writes config.json here with `content write`, as tools/theme.sh does for the launcher, so the
 * passwords never pass through shared storage. Write-only, one file name, and only the shell can call it
 * (WRITE_SECURE_SETTINGS on the provider). The file is staged in private storage and imported once it is closed.
 */
public class ConfigProvider extends ContentProvider {
    private static final String NAME = "config.json";

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.contains("w") || !NAME.equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        File dir = getContext().getFilesDir();
        try {
            return ParcelFileDescriptor.open(new File(dir, ConfigStore.STAGED_NAME),
                    ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE,
                    new Handler(Looper.getMainLooper()),
                    e -> {
                        if (e == null && new ConfigStore(getContext()).importPushed()) GlanceService.reload(getContext());
                    });
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
}
