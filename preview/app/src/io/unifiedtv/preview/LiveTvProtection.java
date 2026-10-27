package io.unifiedtv.preview;
import android.content.*;
import android.content.pm.*;
import java.util.*;

/**
 * OD-26 (owner, 27 October 2026): Live TV opens when NordVPN is connected. This replaces SET-063's "automatic verification
 * only" (OD-21/25 October), under which Live TV never opened on Android: a third-party app cannot see another app's
 * routing, and Android's "block connections without VPN" setting is readable only by a device or profile owner.
 *
 * <p>The one rule, for every route into Live TV (the dock tile, Apps, voice, the Settings sheet and anything Launch.open
 * recognises as Live TV), and for the safety net while Live TV runs (LiveTvGuard): Live TV opens only while Android reports
 * a VPN AND that VPN is NordVPN's. It is NordVPN's when NordVPN is installed and either
 * <ul>
 * <li>NordVPN's own notification says it is connected (read by MediaWatch with the notification access the household
 *     allowed; NordCountry), or</li>
 * <li>without that word, NordVPN is the only app on the TV that can run a VPN at all (the only one declaring
 *     android.net.VpnService), so the VPN Android reports can only be NordVPN's.</li>
 * </ul>
 * Another VPN app installed and no notification access: Unified TV can't tell which VPN is on, so Live TV stays locked
 * and says so. NordVPN's own notification saying it is not connected (disconnected, connecting, paused) always locks.
 *
 * <p>This is not a kill switch, and nothing here may call it one (AGENTS.md rule 8): if NordVPN drops while Live TV plays,
 * Android does not block Live TV's connection (unless the owner also set up Android's always-on block,
 * docs/VPN_PROTECTION.md). Unified TV covers Live TV and asks for NordVPN again (LiveTvGuard, LiveTvCoverActivity).
 * {@link #decide} is pure and tested with made-up facts (LiveTvProtectionTest).
 */
final class LiveTvProtection {
    private LiveTvProtection(){}
    enum State {
        OPEN("open"),CHECKING("checking"),DISCONNECTED("disconnected"),NO_NORDVPN("locked"),UNSURE("locked"),NOT_NORDVPN("locked"),ERROR("error");
        /** The word Settings and Owner diagnostics show ("Live TV: open"). */
        final String word;
        State(String word){this.word=word;}
    }
    static final String NORDVPN="com.nordvpn.android";
    /** OD-26 in the household's words. */
    static final String RULE="Live TV opens when NordVPN is connected.";
    static final String COVERS="If NordVPN drops while Live TV is on, Unified TV covers Live TV and asks you to reconnect NordVPN.";
    /** Honest limit, said wherever the rule is explained (AGENTS.md rule 8: a status alone is not a kill switch). */
    static final String NOT_A_KILL_SWITCH="This isn't a kill switch: if NordVPN drops, Live TV's connection isn't blocked by Android.";

    /** What was read at the moment of deciding. Plain fields so tests can make up any TV. */
    static final class Facts {
        /** What Android reports: CONNECTED when any network carries a VPN. */
        VpnGate.State vpn=VpnGate.State.UNKNOWN;
        boolean nordInstalled;
        /** The household allowed Unified TV to see notifications (MediaWatch). */
        boolean notificationAccess;
        /** NordVPN's own notification: TRUE says connected, FALSE says not connected, null said nothing (or no access). */
        Boolean nordSays;
        /** NordVPN is the only app on this TV that can run a VPN ({@link #onlyNord}). */
        boolean onlyNordCanVpn;
        /** The name of another app that can run a VPN, for the plain reason (UNSURE); null when none or unknown. */
        String otherVpn;
    }
    /** The facts the last decision used ({@link #now}): message() names the other VPN app from them. */
    static volatile Facts last;
    /** Tests only: the facts {@link #now} uses instead of reading the TV. */
    static volatile Facts forTests;

    /** Pure: the one rule. */
    static State decide(Facts f){
        if(f==null)return State.ERROR;
        if(!f.nordInstalled)return State.NO_NORDVPN;
        if(f.vpn==VpnGate.State.CHECKING)return State.CHECKING;
        if(f.vpn==VpnGate.State.DISCONNECTED)return State.DISCONNECTED;
        if(f.vpn!=VpnGate.State.CONNECTED)return State.ERROR;
        // A VPN is up. Whose? NordVPN's own word first, where the household lets Unified TV see it.
        if(f.notificationAccess&&Boolean.FALSE.equals(f.nordSays))return State.DISCONNECTED;
        if(f.notificationAccess&&Boolean.TRUE.equals(f.nordSays))return State.OPEN;
        // No word from NordVPN: the VPN is NordVPN's only if nothing else on this TV could have started one.
        if(f.onlyNordCanVpn)return State.OPEN;
        return f.notificationAccess?State.NOT_NORDVPN:State.UNSURE;
    }
    static boolean opens(State s){return s==State.OPEN;}
    /** While Live TV runs: the rule no longer holds (CHECKING is a moment's wait, not a drop). */
    static boolean dropped(State s){return s!=State.OPEN&&s!=State.CHECKING;}
    static String word(State s){return s.word;}
    /**
     * Owner's TCL, build run 99: "VPN connected" and "can't tell which VPN" read as a contradiction. Both are true, so the
     * reason says how: a VPN is connected, and another named app could be running it.
     */
    static String unsure(String other){
        return "Live TV is locked. A VPN is connected, but Unified TV can't confirm it's NordVPN because "+(other==null||other.trim().isEmpty()?"another app":other.trim())
            +" can also run a VPN. Allow notification access so Unified TV can see NordVPN's own \"Connected\" notice.";
    }
    static String message(State s){Facts l=last;switch(s){
        case OPEN:return "NordVPN is connected. If NordVPN drops, Unified TV will cover Live TV.";
        case CHECKING:return "Checking NordVPN…";
        case DISCONNECTED:return "Live TV is locked. Connect NordVPN first.";
        case NO_NORDVPN:return "Live TV is locked. It opens when NordVPN is connected, and NordVPN isn't installed on this TV.";
        case UNSURE:return unsure(l==null?null:l.otherVpn);
        case NOT_NORDVPN:return "Live TV is locked. A VPN is connected, but NordVPN doesn't say it's connected. Connect NordVPN first.";
        default:return "Live TV is locked. Unified TV couldn't read the TV's connection. Try again.";
    }}
    /** Pure: of the apps that can run a VPN, NordVPN is there and is the only one. */
    static boolean onlyNord(Collection<String> vpnApps){
        if(vpnApps==null||!vpnApps.contains(NORDVPN))return false;
        for(String p:vpnApps)if(!NORDVPN.equals(p))return false;
        return true;
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
    /** The apps that can run a VPN, and the ones left out with why (Owner diagnostics). */
    static final class VpnApps {
        final Set<String> counted=new LinkedHashSet<>();final Map<String,String> ignored=new LinkedHashMap<>();final Map<String,String> labels=new HashMap<>();
    }
    /**
     * Pure: why an app declaring a VPN service doesn't count, or null when it does. A disabled app can't run one; a system
     * app with no Home-screen entry is the TV's own plumbing (a TCL data saver, say), not a VPN the household chose.
     * NordVPN always counts while enabled.
     */
    static String ignoredBecause(String pkg,boolean enabled,boolean system,boolean launchable){
        if(!enabled)return "turned off";
        if(NORDVPN.equals(pkg))return null;
        if(system&&!launchable)return "part of the TV's system, with no app to open";
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
        f.onlyNordCanVpn=onlyNord(apps);
        for(String p:apps)if(!NORDVPN.equals(p)){f.otherVpn=detail.labels.get(p);break;}
        f.notificationAccess=MediaWatch.granted(c);
        f.nordSays=f.notificationAccess?NordCountry.connected:null;
        return f;
    }
    /** The rule now, for any route into Live TV and for the safety net while it runs. */
    static State now(Context c){
        Facts t=forTests;if(t!=null){last=t;return decide(t);}
        try{Facts f=read(c);last=f;return decide(f);}catch(RuntimeException e){return State.ERROR;}
    }
}
