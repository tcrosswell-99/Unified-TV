package io.unifiedtv.preview;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import org.json.JSONObject;
import java.util.*;

/**
 * SET-070 connectivity summary for Settings > Connections & Devices: the internet, the home network, the media server and
 * Home Assistant, each in plain words, with when it last worked. "Not set up" (nothing to check) is never shown as a
 * fault, and "can't reach" never as "not set up". The internet and the home network are told apart, so a NAS that works
 * while the internet is down says so. Checked only when Settings asks (Check now, or opening the page), never by scanning
 * the network in the background.
 */
final class Connectivity {
    private Connectivity(){}
    enum Status{OK,NOT_SET_UP,UNREACHABLE,SIGN_IN}
    static final String INTERNET="internet",HOME="home",MEDIA="media",HA="ha";
    static final String PREF="connectivity.";

    static final class Row{final String id,name;final Status status;final String detail;long lastOk;
        Row(String id,String name,Status status,String detail){this.id=id;this.name=name;this.status=status;this.detail=detail;}}

    // --- pure
    /** One plain line: "Internet: connected", "Media server: not set up", "Home Assistant: can't reach it". */
    static String line(Row r){
        switch(r.status){
            case OK:return r.name+": "+(INTERNET.equals(r.id)||HOME.equals(r.id)?"connected":"working");
            case NOT_SET_UP:return r.name+": not set up";
            case SIGN_IN:return r.name+": needs signing in again";
            default:return r.name+": can't reach it";
        }
    }
    /** What to say under the line: the reason and next step, or when it last worked. */
    static String detail(Row r,String lastOkWords){
        if(r.status==Status.NOT_SET_UP)return r.detail;
        String last=lastOkWords==null?"It hasn't worked on this TV yet.":"Last worked "+lastOkWords+".";
        return r.status==Status.OK?"Checked just now.":(r.detail==null||r.detail.isEmpty()?last:r.detail+" "+last);
    }
    static Row internet(boolean network,boolean validated){
        if(!network)return new Row(INTERNET,"Internet",Status.UNREACHABLE,"This TV isn't connected to a network. The TV's own Settings can check its connection.");
        return validated?new Row(INTERNET,"Internet",Status.OK,null):new Row(INTERNET,"Internet",Status.UNREACHABLE,"The TV is on a network, but the internet isn't answering. Things on your home network may still work.");
    }
    static Row home(boolean address){
        return address?new Row(HOME,"Home network",Status.OK,null):new Row(HOME,"Home network",Status.UNREACHABLE,"This TV isn't on your home network, so the media server, Home Assistant and phone setup can't be reached.");
    }
    /** {@code failure}: 0 when the server answered, a DlnaClient.Failure kind otherwise. */
    static Row media(boolean setUp,String name,int failure){
        String n="Media server"+(name==null||name.isEmpty()?"":" ("+name+")");
        if(!setUp)return new Row(MEDIA,"Media server",Status.NOT_SET_UP,"Choose your films and TV folders in Library & Media.");
        return failure==0?new Row(MEDIA,n,Status.OK,null):new Row(MEDIA,n,Status.UNREACHABLE,DlnaClient.Failure.plain(failure));
    }
    /** {@code checked}: a HaStatus word, or null when Home Assistant isn't set up. */
    static Row homeAssistant(String checked){
        if(checked==null||HaStatus.NOT_CONFIGURED.equals(checked))return new Row(HA,"Home Assistant",Status.NOT_SET_UP,"Set it up on your phone in Smart Home for doorbell and alarm alerts.");
        if(HaStatus.AUTHORISED.equals(checked))return new Row(HA,"Home Assistant",Status.OK,null);
        if(HaStatus.AUTH_REQUIRED.equals(checked))return new Row(HA,"Home Assistant",Status.SIGN_IN,HaStatus.explain(checked));
        return new Row(HA,"Home Assistant",Status.UNREACHABLE,HaStatus.explain(checked));
    }

    // --- Android: run off the main thread
    static List<Row> check(Context c){
        List<Row> out=new ArrayList<>();
        boolean network=false,validated=false;
        try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=cm==null?null:cm.getActiveNetwork();NetworkCapabilities caps=n==null?null:cm.getNetworkCapabilities(n);
            network=caps!=null;validated=caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}catch(RuntimeException ignored){}
        out.add(internet(network,validated));
        boolean home=LocalDiscovery.address(c)!=null;out.add(home(home));
        LibraryFolders.Library lib=MediaSources.library(c);int failure=0;
        if(lib.ready){
            try{if(DlnaClient.lan==null)DlnaClient.lan=LocalDiscovery.lan(c);String at=lib.source.optString("location");if(at.isEmpty())throw new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE);DlnaClient.describe(at);}
            catch(Exception e){failure=MediaSources.kind(e);}
        }
        out.add(media(lib.ready,MediaSources.name(c,lib),failure));
        String ha;
        try{JSONObject saved=new Vault(c).read("ha");ha=HaStatus.before(saved);if(ha==null){Exception error=null;try{LocalApi.get(saved,"/api/");}catch(Exception e){error=e;}ha=HaStatus.after(error);}}
        catch(Exception e){ha=null;}
        out.add(homeAssistant(ha));
        long now=System.currentTimeMillis();android.content.SharedPreferences p=c.getSharedPreferences(HouseholdSettings.PREFS,0);android.content.SharedPreferences.Editor e=p.edit();
        for(Row r:out){if(r.status==Status.OK){r.lastOk=now;e.putLong(PREF+r.id,now);}else try{r.lastOk=p.getLong(PREF+r.id,0);}catch(RuntimeException x){r.lastOk=0;}}
        e.apply();
        return out;
    }
    /** Before the first check: what is set up, in the same words, without touching the network. */
    static List<Row> unchecked(Context c){
        List<Row> out=new ArrayList<>();LibraryFolders.Library lib=MediaSources.library(c);
        if(!lib.ready)out.add(media(false,"",0));
        if(!SetupPages.haSaved(c))out.add(homeAssistant(null));
        return out;
    }
}
