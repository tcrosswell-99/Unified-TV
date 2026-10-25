package io.unifiedtv.preview;
/** Household stream paths must never appear in library diagnostics. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PreviewApp extends android.app.Application {
    @Override public void onCreate(){super.onCreate();HouseholdSettings.ensure(this);androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF);}
}
