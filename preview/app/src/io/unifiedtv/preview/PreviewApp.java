package io.unifiedtv.preview;
/** Household stream paths must never appear in library diagnostics. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PreviewApp extends android.app.Application {
    @Override public void onCreate(){super.onCreate();MemoryBudget.get(this);HouseholdSettings.ensure(this);TurnOn.track(this);androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF);}

    /**
     * Memory pass (owner's TCL: 2 GB, lmkd and kswapd busy, 25 October 2026): the app-wide caches give memory back when
     * Android asks. No screen showing (UI_HIDDEN, BACKGROUND or worse): decoded posters, frosted frames, the light's shared
     * pictures and the saved library list held in memory all go, to be made again from storage when next needed. Still
     * on screen but memory running low: half the posters and all but the latest frosted frame. Disk caches stay.
     * Each screen trims its own pictures too (Home, Wallpaper).
     */
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        boolean hidden=level>=TRIM_MEMORY_UI_HIDDEN;
        if(!hidden&&level<TRIM_MEMORY_RUNNING_LOW)return;
        Posters.trim(hidden);MaterialScene.trim(hidden);
        if(hidden){LibraryIndex.trim();AmbientMesh.trim();}
    }
}
