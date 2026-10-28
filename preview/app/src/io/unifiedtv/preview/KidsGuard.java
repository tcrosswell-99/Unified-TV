package io.unifiedtv.preview;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * The Kids app guard's decision, kept pure so it can be tested (KidsGuardService only reports what came to the front).
 *
 * What this is, honestly: an accessibility service that notices which app came to the front and, in Kids, brings Unified
 * TV Home back when the app isn't on the Kids list. It is not a lock. Android gives an app no hard block without
 * device-owner provisioning (a factory reset). A determined child could still switch the service off in system Settings,
 * which is why system Settings is guarded too while Kids is active (an adult opens it through Unified TV's Settings,
 * behind the PIN, which allows it for a short while). A force stop of Unified TV from system Settings also switches the
 * service off on some TVs. It does nothing in Adults or while the adult has it switched off.
 *
 * Handled explicitly: Unified TV itself; the system UI, system dialogs and permission prompts; the keyboard in use; the
 * TV voice assistant (the voice key belongs to the TV; any app it opens is still checked); the TV's input and HDMI
 * switchers, which on these TVs are often part of the broadcast TV app, so Kids can reach broadcast TV with the Input
 * button; and system Settings (adults only, with the pass). The package names are best-known candidates, not yet seen on
 * the household's TVs: the guard's last decisions are listed in Owner diagnostics so the list can be checked. A Settings
 * app that isn't listed is treated like any other app (returned from, pass or not): the safe direction.
 */
final class KidsGuard {
    static final String PREF="kidsGuard",PASS="kidsSettingsUntil",LOG="kidsGuardLog";
    static final long PASS_MS=10*60_000L,REPEAT_MS=1500;
    static final int ALLOW=0,RETURN=1;
    static final Set<String> SYSTEM=new HashSet<>(Arrays.asList(
        "android","com.android.systemui","com.android.permissioncontroller","com.google.android.permissioncontroller",
        "com.android.packageinstaller","com.google.android.packageinstaller","com.google.android.katniss"));
    static final Set<String> INPUTS=new HashSet<>(Arrays.asList(
        "com.android.tv","com.google.android.tv","com.mediatek.wwtv.tvcenter","com.sony.dtv.tvx"));
    static final Set<String> SETTINGS=new HashSet<>(Arrays.asList(
        "com.android.settings","com.android.tv.settings"));

    private KidsGuard(){}
    /** One decision for the app now in front. */
    static int decide(String pkg,String self,boolean kids,boolean on,Set<String> approved,boolean settingsPass,String keyboard){
        if(!on||!kids||pkg==null||pkg.isEmpty())return ALLOW;
        if(pkg.equals(self)||SYSTEM.contains(pkg)||INPUTS.contains(pkg)||pkg.equals(keyboard))return ALLOW;
        if(SETTINGS.contains(pkg))return settingsPass?ALLOW:RETURN;
        return approved!=null&&approved.contains(pkg)?ALLOW:RETURN;
    }
    static boolean pass(long until,long now){return now<until&&until-now<=PASS_MS;}
    /** The keyboard's package from Android's "package/.Service" setting. */
    static String keyboard(String setting){if(setting==null)return "";int slash=setting.indexOf('/');return slash>0?setting.substring(0,slash):"";}
    /** Whether Android's list of switched-on accessibility services includes this one (full or short class form). */
    static boolean enabledIn(String setting,String pkg,String cls){
        if(setting==null||setting.isEmpty())return false;
        for(String s:setting.split(":")){s=s.trim();if(s.equalsIgnoreCase(pkg+"/"+cls)||(cls.startsWith(pkg+".")&&s.equalsIgnoreCase(pkg+"/"+cls.substring(pkg.length()))))return true;}
        return false;
    }
    /**
     * The computer alternative to the Settings path. It adds this service to the list instead of replacing the list,
     * which would switch off every other accessibility service on the TV.
     */
    static String adbLine(String component){
        return "adb shell 'cur=$(settings get secure enabled_accessibility_services); case \"$cur\" in \"\"|null) new="+component+";; *"+component+"*) new=\"$cur\";; *) new=\"$cur:"+component+"\";; esac; settings put secure enabled_accessibility_services \"$new\"; settings put secure accessibility_enabled 1'";
    }
    /** The last few decisions, newest first, for Owner diagnostics: package, what happened, when. Bounded. */
    static String log(String saved,String pkg,int decision,long now){
        JSONArray old;try{old=new JSONArray(saved==null||saved.isEmpty()?"[]":saved);}catch(Exception e){old=new JSONArray();}
        JSONArray out=new JSONArray();
        try{out.put(new JSONObject().put("pkg",pkg).put("did",decision==RETURN?"returned to Home":"allowed").put("at",now));}catch(Exception ignored){}
        for(int i=0;i<old.length()&&out.length()<8;i++)if(old.optJSONObject(i)!=null)out.put(old.optJSONObject(i));
        return out.toString();
    }
}
