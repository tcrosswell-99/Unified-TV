package io.unifiedtv.preview;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Every route into another app, from Home, Apps, voice, Music or Settings, comes through here, so neither the Live TV gate
 * nor the Kids list can be bypassed (P05-03; owner, 25 October 2026).
 */
final class Launch {
    /** Opens the app, or explains why not; null when it opened. A protected Live TV app needs the recorded protection check. */
    static String open(Activity a,String pkg,String label){
        if(pkg==null||pkg.isEmpty())return label+" is not installed on this TV";
        String kids=kidsRefusal(a,pkg,label);if(kids!=null)return kids;
        if(Endpoint.protectedApp(pkg,label,a.getSharedPreferences("unified-tv-preview",0).getString("livePackage",""))){
            LiveTvProtection.Decision d=LiveTvProtection.now(a,pkg);
            if(d!=LiveTvProtection.Decision.ALLOW){VpnGate.show(a,LiveTvProtection.message(d));return "";}
        }
        PackageManager pm=a.getPackageManager();Intent intent=pm.getLeanbackLaunchIntentForPackage(pkg);if(intent==null)intent=pm.getLaunchIntentForPackage(pkg);
        if(intent==null)return label+" can't be opened from here";
        boolean live=Endpoint.protectedApp(pkg,label,a.getSharedPreferences("unified-tv-preview",0).getString("livePackage",""));
        try{if(live)LiveTvGuard.start(a);a.startActivity(intent);return null;}catch(RuntimeException e){if(live)LiveTvGuard.stop(a);return "Unable to open "+label;}
    }
    static void info(Activity a,String pkg){system(a,new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.fromParts("package",pkg,null)),"App information isn't available on this TV");}
    /**
     * The one Kids check (KidsPolicy): null when allowed, else a friendly reason. Package names and Unified TV's own HDMI
     * shortcuts ("xbox", "tvplus") alike. Adults: always null.
     */
    static String kidsRefusal(Context c,String id,String label){
        android.content.SharedPreferences p=c.getSharedPreferences("unified-tv-preview",0);
        if(KidsPolicy.appAllowed(Profiles.kids(p),KidsPolicy.apps(p.getString(KidsPolicy.APPS_PREF,"")),id,c.getPackageName()))return null;
        return (label==null||label.isEmpty()?"That app":label)+" isn't on the Kids list. Ask an adult.";
    }
    /**
     * The TV's own settings screens. In Kids they need the adult PIN, and then the Kids guard lets system Settings stay
     * open for a short while (KidsGuard.PASS_MS); in Adults they simply open.
     */
    static void system(Activity a,Intent intent,String unavailable){
        if(Profiles.kids(a))EditLock.ask(a,()->unlocked(a,intent,unavailable));else unlocked(a,intent,unavailable);
    }
    /** From a screen that already took the adult PIN (Settings, Kids settings): no second PIN. */
    static void unlocked(Activity a,Intent intent,String unavailable){
        if(Profiles.kids(a))a.getSharedPreferences("unified-tv-preview",0).edit().putLong(KidsGuard.PASS,System.currentTimeMillis()+KidsGuard.PASS_MS).apply();
        try{a.startActivity(intent);}catch(RuntimeException e){android.widget.Toast.makeText(a,unavailable,android.widget.Toast.LENGTH_LONG).show();}
    }
    /**
     * Library (the dock entry, Apps and Home's poster row end card): the library when one is set up for this profile,
     * otherwise setup for Adults, or for Kids the "ask an adult" page. Never another app.
     */
    static void library(Activity a){
        Intent next;
        if(HomeShelf.configured(a))next=new Intent(a,LibraryActivity.class);
        else next=Profiles.kids(a)?new Intent(a,DlnaLibraryActivity.class):new Intent(a,DlnaLibraryActivity.class).putExtra("setup",true);
        try{a.startActivity(next);a.overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
        catch(RuntimeException e){android.widget.Toast.makeText(a,"Unable to open Films & TV",android.widget.Toast.LENGTH_LONG).show();}
    }
}
