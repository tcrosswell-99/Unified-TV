package io.unifiedtv.preview;

import android.content.Context;
import java.io.File;
import java.util.Locale;

/**
 * SET-080 privacy dashboard figures: what Unified TV keeps on this TV, item by item, so each can be seen and cleared on
 * its own. Watch history per profile, the poster and film details cache, the saved list of library titles, photo copies
 * for the Wallpaper, and the event log. Settings and connections are listed by Settings itself. No telemetry exists to
 * list: nothing is sent anywhere unless the household connects a service.
 */
final class PrivacyData {
    private PrivacyData(){}

    /** "Nothing", "Under 1 MB", "12 MB" or "1.2 GB". */
    static String size(long bytes){
        if(bytes<=0)return "Nothing";
        if(bytes<1024L*1024)return "Under 1 MB";
        if(bytes<1024L*1024*1024)return (bytes/(1024L*1024))+" MB";
        return String.format(Locale.UK,"%.1f GB",bytes/(1024.0*1024*1024));
    }
    static String count(int n,String one,String many){return n==0?"none":n==1?"1 "+one:n+" "+many;}

    /** Every file under a folder (bounded depth), for cache sizes. */
    static long bytes(File dir){return bytes(dir,0);}
    private static long bytes(File f,int depth){
        if(f==null||!f.exists())return 0;if(f.isFile())return f.length();if(depth>4)return 0;
        long n=0;File[] all=f.listFiles();if(all!=null)for(File x:all)n+=bytes(x,depth+1);return n;
    }

    // --- Android
    static File posters(Context c){return new File(c.getFilesDir(),"posters");}
    static File library(Context c){return new File(c.getFilesDir(),"library");}
    static File photos(Context c){return new File(c.getNoBackupFilesDir(),"photos");}
    /** Saved places (Continue Watching) for a profile. */
    static int history(Context c,String profile){
        try{return new ResumeStore(c.getSharedPreferences(PlayerActivity.RESUME_PREFS,0).getString(ResumeStore.prefsKey(profile),"")).size();}catch(RuntimeException e){return 0;}
    }
    static void clearHistory(Context c,String profile){
        android.content.SharedPreferences.Editor e=c.getSharedPreferences(PlayerActivity.RESUME_PREFS,0).edit().remove(ResumeStore.prefsKey(profile));
        if(Profiles.ADULT.equals(Profiles.stored(profile)))e.remove(ResumeStore.LEGACY);   // places from before profiles were Adults'
        e.commit();
    }
    static void clearPosters(Context c){Posters.get(c).clear();}
    static void clearLibrary(Context c){LibraryIndex.clear(c);}
    static void clearPhotos(Context c){new PhotoCache(photos(c),PhotoCache.LIMIT).clear();}
}
