package io.unifiedtv.preview;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/**
 * Settings > Updates & About > Advanced > Owner diagnostics (owner, 25 October 2026): one scrolling page of read-only checks, Adults
 * only and behind the PIN, never on Home. Nothing is changed, saved or sent. No token or secret is shown; addresses only
 * after "Show advanced details". A settings screen, so the glass sidebar over a still Home (OD-11), wide (about three
 * quarters) for its long report lines.
 */
public final class OwnerDiagnosticsActivity extends GlassScreen {
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private volatile boolean active=true;
    private TextView advanced;private final List<String> addresses=Collections.synchronizedList(new ArrayList<>());

    @Override public void onCreate(Bundle state){super.onCreate(state);wide(true);
        page("Owner diagnostics","Updates & About > Advanced");
        if(Profiles.kids(prefs)){card();text("Owner diagnostics are for Adults. Switch to Adults first.",20);button("Back",this::finish).requestFocus();return;}
        card();text("Read-only. Nothing here changes the TV, the NAS or Home Assistant.",17);
        Button back=button("Back",this::finish);back.requestFocus();

        group("Media server (NAS)");card();TextView nas=text("Searching the home network…",17);
        DlnaClient.lan=LocalDiscovery.lan(this);
        worker.execute(()->{StringBuilder out=new StringBuilder();
            try{List<DlnaClient.Check> all=DlnaClient.survey(getApplicationContext(),true);
                if(all.isEmpty())out.append("No media server answered the search.");
                for(DlnaClient.Check c:all){out.append(out.length()==0?"":"\n").append(OwnerChecks.nas(c.device==null?"":c.device.name,c.device!=null,c.folders));addresses.add("Server "+(c.device==null||c.device.name.isEmpty()?"(unnamed)":c.device.name)+": "+OwnerChecks.host(c.location));}
            }catch(Exception e){out.append("The search couldn't run just now: the TV may be off the home network.");}
            try{JSONObject saved=new Vault(this).read("dlna"),kids=new Vault(this).read(KidsPolicy.STORE);
                out.append("\nHousehold library: ").append(saved.optBoolean("enabled")?KidsPolicy.roots(saved).size()+" folders on "+(saved.optString("name").isEmpty()?"a media server":saved.optString("name")):"not set up");
                out.append("\nKids folders: ").append(KidsPolicy.ready(kids)?KidsPolicy.roots(kids).size()+" chosen":"none");
                if(!saved.optString("control").isEmpty())addresses.add("Household library server: "+OwnerChecks.host(saved.optString("control")));
            }catch(Exception e){out.append("\nSaved library: couldn't be read.");}
            post(nas,out.toString());});

        group("Ring and Home Assistant");card();TextView ha=text("Checking…",17);
        worker.execute(()->{StringBuilder out=new StringBuilder();JSONObject config,house;
            try{config=new Vault(this).read("ha");house=new Vault(this).read("household");}catch(Exception e){post(ha,"Saved connection couldn't be read.");return;}
            String status=HaStatus.before(config);
            if(status==null){Exception error=null;try{LocalApi.get(config,"/api/");}catch(Exception e){error=e;}status=HaStatus.after(error);}
            out.append("Home Assistant: ").append(status).append(". ").append(HaStatus.explain(status));
            JSONObject names=house.optJSONObject("names");
            out.append("\nDoorbell: ").append(name(names,"door",house.optString("door"))).append("\nPerson sensor: ").append(name(names,"person",house.optString("person"))).append("\nCamera: ").append(name(names,"camera",house.optString("camera")));
            out.append("\nLast doorbell event on this TV: ").append(prefs.getString("householdLastEvent","none")).append(", ").append(OwnerChecks.when(this,prefs.getLong("householdLastEventAt",0)));
            out.append("\nPerson alerts in Kids: ").append(prefs.getBoolean(AlertPolicy.KIDS_PERSON_PREF,true)?"shown":"not shown");
            if(!config.optString("base").isEmpty())addresses.add("Home Assistant: "+OwnerChecks.host(config.optString("base")));
            post(ha,out.toString());});

        group("VPN and Live TV");card();
        String live=LiveTvProtection.livePackage(this);
        text(OwnerChecks.liveTv(VpnGate.statusLabel(VpnGate.inspect(this)),VpnGate.routed(this),LiveTvProtection.now(this,live)),17);
        text("Live TV app: "+(live.isEmpty()?"not identified":live)+". Android's always-on VPN with “block connections without VPN” is what stops traffic when NordVPN drops; this page can't test that.",16);

        group("This app");card();
        text(app(),17);

        group("Profile and Kids guard");card();
        String setting="";try{setting=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);}catch(RuntimeException ignored){}
        StringBuilder kids=new StringBuilder("Profile: "+Profiles.label(Profiles.current(prefs))+". Adult PIN: "+(prefs.getString("pin","").isEmpty()?"not set":"set")+".");
        kids.append("\nApps approved for Kids: ").append(KidsPolicy.apps(prefs.getString(KidsPolicy.APPS_PREF,"")).size());
        kids.append("\nKids app guard: ").append(prefs.getBoolean(KidsGuard.PREF,false)?"switched on in Unified TV":"switched off in Unified TV").append("; TV permission ").append(KidsGuard.enabledIn(setting,getPackageName(),KidsGuardService.class.getName())?"allowed.":"not allowed.");
        try{JSONArray log=new JSONArray(prefs.getString(KidsGuard.LOG,"[]"));
            if(log.length()==0)kids.append("\nGuard decisions: none yet.");
            for(int i=0;i<log.length();i++){JSONObject e=log.optJSONObject(i);if(e!=null)kids.append("\n").append(OwnerChecks.when(this,e.optLong("at"))).append("  ").append(e.optString("pkg")).append(": ").append(e.optString("did"));}
        }catch(Exception ignored){}
        text(kids.toString(),17);
        if(RemoteCheckActivity.allowed(this))button("Check your remote's buttons",()->startActivity(new Intent(this,RemoteCheckActivity.class)));
        button("Measure Home",()->startActivity(new Intent(this,PerformanceActivity.class)));

        group("Hardware tests to work through");card();
        StringBuilder list=new StringBuilder();for(String r:OwnerChecks.REGISTER)list.append(list.length()==0?"":"\n").append("•  ").append(r);
        text(list.toString(),16);

        card();button("Show advanced details (addresses)",()->{if(advanced==null){advanced=text("",15);}synchronized(addresses){advanced.setText(addresses.isEmpty()?"No addresses found yet.":String.join("\n",addresses));}});
    }
    private static String name(JSONObject names,String key,String entity){
        String n=names==null?"":names.optString(key,"").trim();
        if(entity==null||entity.isEmpty())return "not set up";
        return n.isEmpty()?"set up (no name given)":n;
    }
    private String app(){
        String version="",kind="unknown",print="unavailable";
        try{PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);version=info.versionName;
            Signature[] sigs=info.signingInfo==null?null:info.signingInfo.hasMultipleSigners()?info.signingInfo.getApkContentsSigners():info.signingInfo.getSigningCertificateHistory();
            if(sigs!=null&&sigs.length>0){byte[] der=sigs[sigs.length-1].toByteArray();print=OwnerChecks.fingerprint(der);
                java.security.cert.X509Certificate cert=(java.security.cert.X509Certificate)java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(der));
                kind=OwnerChecks.keyKind(cert.getSubjectX500Principal().getName());}
        }catch(Exception ignored){}
        return "Version "+version+" · "+OwnerChecks.flavour(getPackageName())+"\nPackage: "+getPackageName()+"\nSigning certificate SHA-256: "+print+"\n"+kind;
    }
    private void post(TextView into,String words){runOnUiThread(()->{if(active)into.setText(words);});}
    @Override protected void onDestroy(){active=false;worker.shutdownNow();super.onDestroy();}
}
