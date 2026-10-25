package io.unifiedtv.preview;
import android.content.*;
import android.content.pm.*;
import java.util.*;

/**
 * Protection, not appearance (SET-063; owner, 25 October 2026: automatic verification only). Live TV opens only when
 * Unified TV itself has verified, automatically and just now, that Live TV's connection can only go through NordVPN.
 * No adult statement, recorded check or status icon can unlock it. Unknown, disconnected and failed checks always lock.
 *
 * On Android a third-party app has no way to make that check: it cannot see another app's routing, and the always-on
 * VPN "block connections without VPN" setting is readable only by a device or profile owner
 * (DevicePolicyManager.isAlwaysOnVpnLockdownEnabled). So there is no Verifier on Android, every Live TV route stays
 * locked (fail closed), and the screen says why in plain words. A future verifier plugs in below; nothing else changes.
 *
 * States, in SET-063's words: locked, verifying, verified, disconnected, error.
 */
final class LiveTvProtection {
    enum State {LOCKED,VERIFYING,VERIFIED,DISCONNECTED,ERROR}
    static final String NORDVPN="com.nordvpn.android";
    /** An automatic check of the protected route for one Live TV app: true only when verified now. */
    interface Verifier {boolean verify(String livePackage)throws Exception;}
    /** None on Android (see above). Never set from a setting, a file or a user action. */
    static final Verifier VERIFIER=null;

    /**
     * Pure. tunnel: a VPN is up; verifier: an automatic check exists; result: its answer (null while it runs); failed: it
     * could not run. Only an automatic "verified" opens Live TV.
     */
    static State decide(boolean tunnel,boolean verifier,Boolean result,boolean failed){
        if(failed)return State.ERROR;
        if(!tunnel)return State.DISCONNECTED;
        if(!verifier)return State.LOCKED;
        if(result==null)return State.VERIFYING;
        return result?State.VERIFIED:State.LOCKED;
    }
    static boolean opens(State s){return s==State.VERIFIED;}
    /** The state's word, as SET-063 names it. */
    static String word(State s){return s.name().toLowerCase(java.util.Locale.ROOT);}
    static String message(State s){switch(s){
        case LOCKED:return VERIFIER==null?"Live TV is locked. This TV doesn't let Unified TV check by itself that Live TV can only connect through NordVPN, so it stays locked on every route.":"Live TV is locked. The automatic check didn't confirm that Live TV can only connect through NordVPN.";
        case VERIFYING:return "Checking that Live TV can only connect through NordVPN…";
        case DISCONNECTED:return "Live TV is locked. Connect NordVPN first.";
        case ERROR:return "Live TV is locked. The protection check couldn't run.";
        default:return "Verified just now: Live TV can only connect through NordVPN.";
    }}
    // --- Android
    static String version(Context c,String pkg){if(pkg==null||pkg.isEmpty())return "";try{String v=c.getPackageManager().getPackageInfo(pkg,0).versionName;return v==null?"":v;}catch(Exception e){return "";}}
    /** The configured Live TV app, else the one installed app the protected-app rule recognises; never a guess between several. */
    static String livePackage(Context c){
        SharedPreferences p=c.getSharedPreferences("unified-tv-preview",0);String configured=p.getString("livePackage","");if(!configured.isEmpty())return configured;
        PackageManager pm=c.getPackageManager();Set<String> found=new LinkedHashSet<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){String pkg=r.activityInfo.packageName;if(Endpoint.protectedApp(pkg,String.valueOf(r.loadLabel(pm)),""))found.add(pkg);}
        return found.size()==1?found.iterator().next():"";
    }
    /** For the app actually being opened: automatic verification only. On Android there is no verifier, so never VERIFIED. */
    static State now(Context c,String target){
        boolean tunnel;try{tunnel=VpnGate.tunnel(c);}catch(RuntimeException e){return decide(false,false,null,true);}
        if(VERIFIER==null||target==null||target.isEmpty())return decide(tunnel,false,null,false);
        try{return decide(tunnel,true,VERIFIER.verify(target),false);}catch(Exception e){return decide(tunnel,true,null,true);}
    }
}
