package io.unifiedtv.preview;
import java.net.URI;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * The words of Owner diagnostics, kept pure so they can be tested. Every line states what was seen, never more: no
 * "protected", "working" or "verified" without the evidence that word needs. No secret, token or entity id is ever
 * formatted here; an address appears only through {@link #host}, for the owner's "advanced details" line.
 */
final class OwnerChecks {
    private OwnerChecks(){}
    /** Hardware tests the owner can work through on each TV (docs/ACCEPTANCE_REGISTER.csv holds the detail). */
    static final String[] REGISTER={
        "RC-01, RC-05: every remote button recorded (Check your remote's buttons)",
        "HRC-01, HRC-10: Home, Back and return from other apps; the original interface still reachable",
        "P05-02 to P05-10: find the NAS, choose folders, play, pause, seek, Back",
        "LIB-DOCK-01: Library on the dock opens and returns to the same tile",
        "PROFILE-01 to PROFILE-03: opens in the last profile; Adults needs the PIN; Kids needs a PIN to exist",
        "KIDS-01 to KIDS-04: Kids folders, ratings, approved apps, the app guard",
        "CW-01: resume places kept apart for Adults and Kids",
        "NAS-ZC-01 to NAS-ZC-03: servers offered by name only after the catalogue check; failure checklist; same server found again",
        "RING-02, RING-03: person toast (and its Kids setting); doorbell live card",
        "SAFE-01 to SAFE-10 and GATE-VPN-01 to GATE-VPN-05: alarms, and Live TV staying locked without the VPN"};

    /** Which of the two apps (or the review build) this is, from its package name. */
    static String flavour(String pkg){
        if(pkg==null)return "unknown";
        if(pkg.endsWith(".devicecheck"))return "review build (installs beside the real apps)";
        if(pkg.startsWith("io.unifiedtv.launcher"))return "Unified TV launcher";
        if(pkg.startsWith("io.unifiedtv.preview"))return "Unified TV Preview";
        return "unknown";
    }
    /** SHA-256 of a signing certificate, as the signing record writes it (AA:BB:...). */
    static String fingerprint(byte[] certificate)throws Exception{
        byte[] d=MessageDigest.getInstance("SHA-256").digest(certificate);StringBuilder out=new StringBuilder();
        for(int i=0;i<d.length;i++){if(i>0)out.append(':');out.append(String.format(Locale.ROOT,"%02X",d[i]&0xff));}return out.toString();
    }
    /**
     * Debug or not, from the certificate's subject. A non-debug key is not thereby the owner's durable key: only comparing
     * the fingerprint with the owner's signing record shows that.
     */
    static String keyKind(String subject){
        if(subject!=null&&subject.replace(" ","").toUpperCase(Locale.ROOT).contains("CN=ANDROIDDEBUG"))return "Android debug key (a test signature, not the durable key)";
        return "Not a debug key. Compare the fingerprint with the owner's durable key record to confirm it.";
    }
    /** One media server after a survey: whether it described itself and whether its top level showed folders. */
    static String nas(String name,boolean described,int folders){
        if(!described)return "A server answered, but its description couldn't be read: not offered.";
        String who=name==null||name.trim().isEmpty()?"Media server":name.trim();
        if(folders>0)return who+": description read; top level shows "+folders+(folders==1?" folder":" folders")+". Offered.";
        if(folders==0)return who+": description read; top level shows no folders. Not offered.";
        return who+": description read; the top level couldn't be browsed. Not offered.";
    }
    /** Live TV's fail-closed state in plain words: "can open" only with a recorded passed check that still holds. */
    static String liveTv(String vpnLabel,boolean routed,LiveTvProtection.Decision decision){
        String vpn=vpnLabel+(routed?"; this app's traffic uses the VPN.":"; this app's traffic does not use a VPN.");
        if(decision==LiveTvProtection.Decision.ALLOW)return vpn+" Live TV can open: a passed protection check is recorded and nothing it depended on has changed. This is that record, not a new test of the kill switch.";
        return vpn+" Live TV stays locked: "+LiveTvProtection.message(decision);
    }
    /** For "advanced details" only: the host of a saved or found address, nothing else of it. */
    static String host(String url){try{String h=URI.create(url).getHost();return h==null?"":h;}catch(RuntimeException e){return "";}}
    /** A time on the household's clock (Europe/London), or "none yet". */
    static String when(long at){
        if(at<=0)return "none yet";
        SimpleDateFormat f=new SimpleDateFormat("d MMM HH:mm",Locale.UK);f.setTimeZone(TimeZone.getTimeZone("Europe/London"));return f.format(new Date(at));
    }
}
