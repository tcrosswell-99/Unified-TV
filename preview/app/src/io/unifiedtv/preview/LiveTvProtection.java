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
    }
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
    static String message(State s){switch(s){
        case OPEN:return "NordVPN is connected. If NordVPN drops, Unified TV will cover Live TV.";
        case CHECKING:return "Checking NordVPN…";
        case DISCONNECTED:return "Live TV is locked. Connect NordVPN first.";
        case NO_NORDVPN:return "Live TV is locked. It opens when NordVPN is connected, and NordVPN isn't installed on this TV.";
        case UNSURE:return "Live TV is locked. Can't tell which VPN is on. Allow notification access so Unified TV can see NordVPN.";
        case NOT_NORDVPN:return "Live TV is locked. A VPN is on, but NordVPN doesn't say it's connected. Connect NordVPN first.";
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
    /**
     * The apps on this TV that can run a VPN: those declaring a service for android.net.VpnService, guarded by
     * BIND_VPN_SERVICE as Android requires (the manifest's queries make them visible). Package names only.
     */
    static Set<String> vpnApps(Context c){
        Set<String> found=new LinkedHashSet<>();
        try{for(ResolveInfo r:c.getPackageManager().queryIntentServices(new Intent(android.net.VpnService.SERVICE_INTERFACE),0))
            if(r.serviceInfo!=null&&android.Manifest.permission.BIND_VPN_SERVICE.equals(r.serviceInfo.permission))found.add(r.serviceInfo.packageName);}
        catch(RuntimeException ignored){}
        return found;
    }
    static Facts read(Context c){
        Facts f=new Facts();f.vpn=VpnGate.inspect(c);
        Set<String> apps=vpnApps(c);
        f.nordInstalled=apps.contains(NORDVPN)||LocalDiscovery.nordVpn(c);
        f.onlyNordCanVpn=onlyNord(apps);
        f.notificationAccess=MediaWatch.granted(c);
        f.nordSays=f.notificationAccess?NordCountry.connected:null;
        return f;
    }
    /** The rule now, for any route into Live TV and for the safety net while it runs. */
    static State now(Context c){
        Facts t=forTests;if(t!=null)return decide(t);
        try{return decide(read(c));}catch(RuntimeException e){return State.ERROR;}
    }
}
