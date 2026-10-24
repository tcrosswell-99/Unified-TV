package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Handles market://launch?id=<package> (Android TV Remote "launch app" by package name), which the
 *  stock launcher normally serves: opens the app's TV launch intent, then the phone launch intent.
 *
 *  Exported and BROWSABLE as the stock launcher's is, so a web page or any app can send it too. That is
 *  accepted on purpose: it can only open an installed app's own launcher entry (the tile Home would open, never
 *  a deep link, extra or non-exported activity), with no data passed on, and this app's settings screens have
 *  no launcher entry. Keep it that way: never resolve anything but the two launch intents below. */
public class AppLaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Uri data = getIntent().getData();
            String pkg = data == null ? null : data.getQueryParameter("id");
            if (pkg != null) {
                Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (ActivityNotFoundException | SecurityException ignored) {
        } finally {
            finish();
        }
    }
}
