package io.unifiedtv.preview;
import android.content.*;
import android.content.pm.*;
import java.util.*;

/**
 * OD-26 (owner, 27 October 2026; amended 3 November 2026): Live TV opens when a VPN is connected. The 27 October rule
 * also asked Unified TV to prove the VPN was NordVPN's, and on the owner's TCL it couldn't (another app able to run a VPN,
 * no notification-access screen), so Live TV never opened with NordVPN connected. The owner: "if a VPN is connected, it's
 * connected. So use that."
 *
 * <p>The one rule, for every route into Live TV (the dock tile, Apps, voice, the Settings sheet and anything Launch.open
 * recognises as Live TV), and for the safety net while Live TV runs (LiveTvGuard): Live TV opens while Android reports a
 * VPN on any network (VpnGate.inspect, validated or not) and NordVPN is installed. Nothing else decides: other apps that
 * can run a VPN, a system VPN service, notification access and NordVPN's own notification are shown in Owner diagnostics
 * as information only. NordVPN's notification (when Unified TV may read it) only adds its country and the word
 * "NordVPN is connected" to what the household sees; it never refuses Live TV.
 *
 * <p>This is not a kill switch, and nothing here may call it one (AGENTS.md rule 8): if the VPN drops while Live TV plays,
 * Android does not block Live TV's connection (unless the owner also set up Android's always-on block,
 * docs/VPN_PROTECTION.md). Unified TV covers Live TV and asks for NordVPN again (LiveTvGuard, LiveTvCoverActivity).
 * {@link #decide} is pure and tested with made-up facts (LiveTvProtectionTest).
 */
final class LiveTvProtection {
    private LiveTvProtection(){}
    enum State {
        OPEN("open"),CHECKING("checking"),DISCONNECTED("disconnected"),NO_NORDVPN("locked"),ERROR("error");
        /** The word Settings and Owner diagnostics show ("Live TV: open"). */
        final String word;
        State(String word){this.word=word;}
    }
    static final String NORDVPN="com.nordvpn.android";
    /** OD-26 as amended, in the household's words. */
    static final String RULE="Live TV opens when a VPN is connected.";
    static final String COVERS="If NordVPN drops while Live TV is on, Unified TV covers Live TV and asks you to reconnect NordVPN.";
    /** Honest limit, said wherever the rule is explained (AGENTS.md rule 8: a status alone is not a kill switch). */
    static final String NOT_A_KILL_SWITCH="This isn't a kill switch: if NordVPN drops, Live TV's connection isn't blocked by Android.";

    /** What was read at the moment of deciding. Plain fields so tests can make up any TV. */
    static final class Facts {
        /** What Android reports: CONNECTED when any network carries a VPN. The only fact besides nordInstalled that decides. */
        VpnGate.State vpn=VpnGate.State.UNKNOWN;
        boolean nordInstalled;
        // Information only (Owner diagnostics, and the words of an open Live TV): none of these ever refuses Live TV.
        /** The household allowed Unified TV to see notifications (MediaWatch). */
        boolean notificationAccess;
        /** NordVPN's own notification: TRUE says connected, FALSE says not connected, null said nothing (or no access). */
        Boolean nordSays;
        /** The country NordVPN's notification says it is connected to (NordCountry), or null. */
        String country;
        /** The name of another app that can run a VPN, or null when none. */
        String otherVpn;
        /** An enabled system VPN service with no app to open (a TCL data saver, say) is on the TV. */
        boolean systemVpn;
    }
    /** The facts the last decision used ({@link #now}): message() words an open Live TV from them. */
    static volatile Facts last;
    /** Tests only: the facts {@link #now} uses instead of reading the TV. */
    static volatile Facts forTests;

    /** Pure: the one rule. A VPN up and NordVPN installed opens Live TV; nothing else is asked. */
    static State decide(Facts f){
        if(f==null)return State.ERROR;
        if(!f.nordInstalled)return State.NO_NORDVPN;
        switch(f.vpn){
            case CONNECTED:return State.OPEN;
            case CHECKING:return State.CHECKING;
            case DISCONNECTED:return State.DISCONNECTED;
            default:return State.ERROR;
        }
    }
    static boolean opens(State s){return s==State.OPEN;}
    /** While Live TV runs: the rule no longer holds (CHECKING is a moment's wait, not a drop). */
    static boolean dropped(State s){return s!=State.OPEN&&s!=State.CHECKING;}
    static String word(State s){return s.word;}
    /**
     * Pure: the words for an open Live TV. "NordVPN is connected" (with its country) only when NordVPN's own notification
     * says so; otherwise just that a VPN is connected. Either way Live TV is open.
     */
    static String opened(Facts f){
        boolean nordSaid=f!=null&&f.notificationAccess&&Boolean.TRUE.equals(f.nordSays);
        String country=nordSaid&&f.country!=null?NordCountry.name(f.country):"";
        return (nordSaid?"NordVPN is connected"+(country.isEmpty()?"":" to "+country)+".":"A VPN is connected.")+" If NordVPN drops, Unified TV will cover Live TV.";
    }
    static String message(State s){switch(s){
        case OPEN:return opened(last);
        case CHECKING:return "Checking the VPN…";
        case DISCONNECTED:return "Live TV is locked. Connect NordVPN first.";
        case NO_NORDVPN:return "Live TV is locked. Install NordVPN on this TV and connect it first.";
        default:return "Live TV is locked. Unified TV couldn't read the TV's connection. Try again.";
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
    /** The apps that can run a VPN, and the ones left out with why: Owner diagnostics only, never the decision. */
    static final class VpnApps {
        final Set<String> counted=new LinkedHashSet<>();final Map<String,String> ignored=new LinkedHashMap<>();final Map<String,String> labels=new HashMap<>();
    }
    /**
     * Pure: why an app declaring a VPN service doesn't count, or null when it does. A disabled app can't run one; a system
     * app with no Home-screen entry is the TV's own plumbing (a TCL data saver, say), not a VPN the household chose.
     * NordVPN always counts while enabled.
     */
    static final String OFF="turned off",PLUMBING="part of the TV's system, with no app to open";
    static String ignoredBecause(String pkg,boolean enabled,boolean system,boolean launchable){
        if(!enabled)return OFF;
        if(NORDVPN.equals(pkg))return null;
        if(system&&!launchable)return PLUMBING;
        return null;
    }
    /**
     * The apps on this TV that can run a VPN: those declaring a service for android.net.VpnService, guarded by
     * BIND_VPN_SERVICE as Android requires (the manifest's queries make them visible), less those ignoredBecause() leaves
     * out. Package names only.
     */
    static VpnApps vpnAppsDetail(Context c){
        VpnApps out=new VpnApps();PackageManager pm=c.getPackageManager();
        try{for(ResolveInfo r:pm.queryIntentServices(new Intent(android.net.VpnService.SERVICE_INTERFACE),PackageManager.MATCH_DISABLED_COMPONENTS)){
            if(r.serviceInfo==null||!android.Manifest.permission.BIND_VPN_SERVICE.equals(r.serviceInfo.permission))continue;
            String pkg=r.serviceInfo.packageName;if(out.counted.contains(pkg)||out.ignored.containsKey(pkg))continue;
            android.content.pm.ApplicationInfo ai=r.serviceInfo.applicationInfo;
            boolean enabled=ai==null||ai.enabled,system=ai!=null&&(ai.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0;
            boolean launchable=false;try{launchable=pm.getLeanbackLaunchIntentForPackage(pkg)!=null||pm.getLaunchIntentForPackage(pkg)!=null;}catch(RuntimeException ignored){}
            String why=ignoredBecause(pkg,enabled,system,launchable);
            if(why==null)out.counted.add(pkg);else out.ignored.put(pkg,why);
            try{out.labels.put(pkg,String.valueOf(ai==null?pkg:pm.getApplicationLabel(ai)));}catch(RuntimeException e){out.labels.put(pkg,pkg);}
        }}catch(RuntimeException ignored){}
        return out;
    }
    static Set<String> vpnApps(Context c){return vpnAppsDetail(c).counted;}
    static Facts read(Context c){
        Facts f=new Facts();f.vpn=VpnGate.inspect(c);
        VpnApps detail=vpnAppsDetail(c);Set<String> apps=detail.counted;
        f.nordInstalled=apps.contains(NORDVPN)||LocalDiscovery.nordVpn(c);
        // Information only, for Owner diagnostics: never part of the decision.
        for(String p:apps)if(!NORDVPN.equals(p)){f.otherVpn=detail.labels.get(p);break;}
        f.systemVpn=detail.ignored.containsValue(PLUMBING);
        f.notificationAccess=MediaWatch.granted(c);
        f.nordSays=f.notificationAccess?NordCountry.connected:null;
        f.country=f.notificationAccess?NordCountry.code:null;
        return f;
    }
    /** The rule now, for any route into Live TV and for the safety net while it runs. */
    static State now(Context c){
        Facts t=forTests;if(t!=null){last=t;return decide(t);}
        try{Facts f=read(c);last=f;return decide(f);}catch(RuntimeException e){return State.ERROR;}
    }
}
