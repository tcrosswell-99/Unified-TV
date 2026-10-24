package io.unifiedtv.preview;
import android.content.*;
import android.content.pm.*;
import org.json.JSONObject;
import java.util.*;

/**
 * Protection, not appearance. Android's always-on VPN with "block connections without VPN" is what stops Live TV
 * traffic when NordVPN drops; this app cannot. Live TV opens only after that has been proven on this TV at network
 * level (preview/tools/vpn_check.py, docs/VPN_PROTECTION.md) and recorded by an adult, and only while nothing the
 * proof depended on has changed. Everything else keeps it locked.
 */
final class LiveTvProtection {
    enum Decision {ALLOW,NOT_CHECKED,VPN_OFF,NOT_COVERED,CHANGED}
    static final String NORDVPN="com.nordvpn.android";
    /** Pure: the record of a passed check against what is true now. */
    static Decision decide(JSONObject record,boolean tunnel,boolean routed,String nordNow,String livePackage,String liveNow){
        if(record==null||record.optString("live").isEmpty())return Decision.NOT_CHECKED;
        if(!record.optString("live").equals(livePackage)||!record.optString("nordVersion").equals(nordNow)||!record.optString("liveVersion").equals(liveNow)||nordNow.isEmpty()||liveNow.isEmpty())return Decision.CHANGED;
        if(!tunnel)return Decision.VPN_OFF;
        // Under the proven lockdown every app's traffic uses the VPN: if ours doesn't, the setup is no longer the one tested.
        if(!routed)return Decision.NOT_COVERED;
        return Decision.ALLOW;
    }
    static String message(Decision d){switch(d){
        case NOT_CHECKED:return "Live TV stays locked until its protection has been checked on this TV.";
        case VPN_OFF:return "Connect NordVPN to watch Live TV.";
        case NOT_COVERED:return "NordVPN isn't covering this TV the way it was checked. Live TV stays locked until it's checked again.";
        case CHANGED:return "NordVPN or Live TV has been updated since protection was checked. Live TV stays locked until it's checked again.";
        default:return "";
    }}
    static JSONObject record(String livePackage,String liveVersion,String nordVersion,long now)throws Exception{
        if(livePackage.isEmpty()||liveVersion.isEmpty()||nordVersion.isEmpty())throw new IllegalArgumentException("Live TV and NordVPN must both be installed");
        return new JSONObject().put("live",livePackage).put("liveVersion",liveVersion).put("nordVersion",nordVersion).put("checkedAt",now);
    }
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
    static JSONObject saved(Context c){try{return new JSONObject(c.getSharedPreferences("unified-tv-preview",0).getString("liveProtection",""));}catch(Exception e){return null;}}
    static void save(Context c,JSONObject record){c.getSharedPreferences("unified-tv-preview",0).edit().putString("liveProtection",record==null?"":record.toString()).apply();}
    /** For the app actually being opened: a different Live TV app than the one checked is not covered by the check. */
    static Decision now(Context c,String target){return decide(saved(c),VpnGate.tunnel(c),VpnGate.routed(c),version(c,NORDVPN),target,version(c,target));}
}
