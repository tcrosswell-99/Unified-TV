package io.unifiedtv.preview;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Every route into another app, from Home, Apps, voice, Music or Settings, comes through here, so neither the Live TV gate
 * nor the Kids list can be bypassed (P05-03; owner, 25 October 2026).
 *
 * Back from another app (owner, TCL, 25 October 2026). Android, not Unified TV, decides where Back goes when another app
 * closes. Two things are done where Android allows them, and nothing else (Home is never intercepted):
 * <ul>
 * <li>Every app opens at its normal TV entry (entry()): its own leanback launch screen, never a settings screen it also
 * lists, and a short visit (visit(): AirPin, NordVPN) comes back to that screen rather than to whatever was left open.</li>
 * <li>While Unified TV is not the TV's chosen Home, a short visit opens inside Unified TV's own task when the app's entry
 * allows it (standard or single-top launch mode, not a document), so when the app closes on Back, Unified TV is what is
 * underneath. If the app closes at once there (some apps refuse to start inside another app), it is opened again on its
 * own (returned()). An app that sends itself to the background, or opens the TV's own Home, on Back still lands on the
 * TCL's Home while the TCL's launcher holds Home: that is the app's choice and can't be changed from here.</li>
 * </ul>
 * Films, programmes and Live TV keep their own task, so their playback is never closed by coming back to Unified TV.
 */
final class Launch {
    /**
     * Opens the app, or explains why not; null when it opened, "" when the Live TV sheet explains. Live TV opens only by
     * LiveTvProtection's one rule (OD-26: NordVPN connected), and then the safety net (LiveTvGuard) watches it.
     */
    static String open(Activity a,String pkg,String label){return open(a,pkg,label,false);}
    /** A short visit to a utility app (AirPin, NordVPN) that the household comes straight back from: see the class notes. */
    static String visit(Activity a,String pkg,String label){return open(a,pkg,label,true);}
    private static String open(Activity a,String pkg,String label,boolean visit){
        if(pkg==null||pkg.isEmpty())return label+" is not installed on this TV";
        String kids=kidsRefusal(a,pkg,label);if(kids!=null)return kids;
        boolean live=Endpoint.protectedApp(pkg,label,a.getSharedPreferences("unified-tv-preview",0).getString("livePackage",""));
        if(live&&!LiveTvProtection.opens(LiveTvProtection.now(a))){VpnGate.show(a,pkg,label);return "";}
        PackageManager pm=a.getPackageManager();Intent intent=entry(pm,pkg);
        if(intent==null)return label+" can't be opened from here";
        if(AirPlayReceiver.isAirPin(label)||pkg.equals(AirPlayReceiver.packageSeen))AirPlayReceiver.opened();
        try{
            if(live)LiveTvGuard.start(a,pkg);
            if(visit&&!live){
                Intent own=intent.cloneFilter().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                android.content.pm.ActivityInfo info=info(pm,intent);
                if(info!=null&&inOurTask(info.launchMode,info.documentLaunchMode,DefaultHome.isDefault(a))){
                    // No category and no new task: the app's entry screen, opened as a fresh screen on top of Unified TV.
                    a.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(intent.getComponent()));
                    visiting=own;visitAt=android.os.SystemClock.uptimeMillis();return null;
                }
                a.startActivity(own);return null;
            }
            a.startActivity(intent);return null;
        }catch(RuntimeException e){if(live)LiveTvGuard.stop(a);return "Unable to open "+label;}
    }
    /** The visit just opened inside Unified TV's task, and when; read by returned(). */
    private static Intent visiting;private static long visitAt;
    /** Back on Unified TV within this long of a visit means the app closed at once inside our task. */
    static final long REFUSED_MS=1200;
    /**
     * Home is showing again: if the app of a visit closed straight away inside Unified TV's task, open it on its own
     * instead, so OK on AirPin or NordVPN always opens it. Otherwise nothing.
     */
    static void returned(Activity a){
        Intent again=visiting;long at=visitAt;visiting=null;
        if(again==null||android.os.SystemClock.uptimeMillis()-at>REFUSED_MS)return;
        try{a.startActivity(again);}catch(RuntimeException ignored){}
    }
    /** Pure: whether a visit may open inside Unified TV's own task (see the class notes). Never as the chosen Home. */
    static boolean inOurTask(int launchMode,int documentLaunchMode,boolean chosenHome){
        return !chosenHome&&documentLaunchMode==android.content.pm.ActivityInfo.DOCUMENT_LAUNCH_NONE
            &&(launchMode==android.content.pm.ActivityInfo.LAUNCH_MULTIPLE||launchMode==android.content.pm.ActivityInfo.LAUNCH_SINGLE_TOP);
    }
    private static android.content.pm.ActivityInfo info(PackageManager pm,Intent intent){
        try{if(intent.getComponent()!=null)return pm.getActivityInfo(intent.getComponent(),0);}catch(Exception ignored){}
        try{android.content.pm.ResolveInfo r=pm.resolveActivity(intent,0);return r==null?null:r.activityInfo;}catch(RuntimeException e){return null;}
    }
    /**
     * The app's normal TV entry: its leanback launch screen (else its ordinary launch screen), as an explicit component.
     * When an app lists more than one, the one that is not a settings screen (pick()).
     */
    static Intent entry(PackageManager pm,String pkg){
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            java.util.List<android.content.pm.ResolveInfo> found;
            try{found=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(pkg),0);}catch(RuntimeException e){found=null;}
            if(found==null||found.isEmpty())continue;
            java.util.List<String> names=new java.util.ArrayList<>();for(android.content.pm.ResolveInfo r:found)if(r.activityInfo!=null)names.add(r.activityInfo.name);
            String chosen=pick(names);if(chosen==null)continue;
            return new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(new android.content.ComponentName(pkg,chosen)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        Intent intent=pm.getLeanbackLaunchIntentForPackage(pkg);if(intent==null)intent=pm.getLaunchIntentForPackage(pkg);return intent;
    }
    /** Pure: of an app's launch screens, the first that isn't a settings, preferences or options screen; else the first. */
    static String pick(java.util.List<String> names){
        if(names==null||names.isEmpty())return null;
        for(String n:names){String l=n.toLowerCase(java.util.Locale.ROOT);if(!l.contains("setting")&&!l.contains("preference")&&!l.contains("option")&&!l.contains("config"))return n;}
        return names.get(0);
    }
    /**
     * A Unified TV screen opened from outside Unified TV (a notification's action), so that Back from it never skips past
     * Home (HRC-03) and nothing already open is closed. The notification opens HouseholdActivity's hand-over with the
     * screen as {@link #SCREEN}; at that moment route() decides, from whether Unified TV already had a task.
     */
    static final String SCREEN="screen";
    static Intent viaHandOver(Context c,Intent screen){
        Intent top=new Intent(screen);top.setFlags(top.getFlags()&~Intent.FLAG_ACTIVITY_NEW_TASK);
        return new Intent(c,HouseholdActivity.class).putExtra(SCREEN,top).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    /**
     * Pure: what the hand-over starts. {@code root}: it is the first screen of a new task (Unified TV wasn't open), so Home
     * goes underneath, then the screen. Otherwise Unified TV's task is already there with whatever is open in it (a film
     * playing, say): the screen goes on top of it, nothing is cleared, and Back returns to what was open. Home is
     * single-task, so starting it would close everything above it; it is never started then.
     */
    static Intent[] route(boolean root,Context c,Intent screen){
        Intent top=new Intent(screen);top.setFlags(top.getFlags()&~(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        if(!root)return new Intent[]{top};
        return new Intent[]{new Intent(c,UnifiedPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),top};
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
