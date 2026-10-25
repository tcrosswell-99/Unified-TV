package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.*;

/**
 * SET-006 capability registry with SET-003's honest states: what this TV can do, worked out from what Unified TV can read
 * on it now (Android version, whether Android offers the Home role, permissions granted, apps installed, what has been set
 * up), never from the manufacturer's name or model. CapabilitiesTest fails if this file ever reads one.
 *
 * Every feature is in exactly one of SET-003's five states, each with plain words and, where there is one, the next step:
 * <ul>
 * <li>{@code available}: works on this TV now;</li>
 * <li>{@code setup_required}: this TV can do it, but it hasn't been set up yet;</li>
 * <li>{@code permission_required}: it needs the household's OK in the TV's own settings;</li>
 * <li>{@code temporarily_unavailable}: set up, but not working right now;</li>
 * <li>{@code unsupported_on_device}: this TV (or this app) can't do it. Settings hides such rows or says why, never a
 *     switch that does nothing.</li>
 * </ul>
 * {@link #evaluate} is pure (tested with made-up facts); {@link #read} gathers the facts on the TV. "available" never
 * means more than Unified TV has seen: it is not a physical-TV pass.
 */
final class Capabilities {
    private Capabilities(){}

    enum State {
        AVAILABLE("available","Working"),
        SETUP_REQUIRED("setup_required","Not set up yet"),
        PERMISSION_REQUIRED("permission_required","Needs your OK"),
        TEMPORARILY_UNAVAILABLE("temporarily_unavailable","Not working right now"),
        UNSUPPORTED_ON_DEVICE("unsupported_on_device","Not available on this TV");
        /** SET-003's identifier, and the short words a chip or a heading shows. */
        final String id,words;
        State(String id,String words){this.id=id;this.words=words;}
    }

    // Feature identifiers (stable: tests and Settings refer to them).
    static final String HOME_SCREEN="home_screen",LIBRARY="library",POSTERS="posters",AIRPLAY="airplay",NORDVPN="nordvpn",
        LIVE_TV="live_tv",HOME_ASSISTANT="home_assistant",FOOTBALL="football",ALERTS_OVER_APPS="alerts_over_apps",
        NOW_PLAYING="now_playing",KIDS_GUARD="kids_guard",VOICE="voice",SOUND_OUTPUT="sound_output",GLASS="glass",REMOTE_CHECK="remote_check";
    /** The order "What this TV can do" lists them in, within each state. */
    static final List<String> ALL=Collections.unmodifiableList(Arrays.asList(HOME_SCREEN,LIBRARY,POSTERS,AIRPLAY,NORDVPN,LIVE_TV,
        HOME_ASSISTANT,FOOTBALL,ALERTS_OVER_APPS,NOW_PLAYING,KIDS_GUARD,VOICE,SOUND_OUTPUT,GLASS,REMOTE_CHECK));
    /** The order of the state groups on the page: what works, then what the household can do something about, then the rest. */
    static final List<State> ORDER=Collections.unmodifiableList(Arrays.asList(State.AVAILABLE,State.PERMISSION_REQUIRED,State.SETUP_REQUIRED,State.TEMPORARILY_UNAVAILABLE,State.UNSUPPORTED_ON_DEVICE));

    // Where the next step is: Settings routes these (SettingsActivity.go). Null: nothing to do on the TV.
    static final String GO_APPEARANCE="appearance",GO_LIBRARY="library",GO_TMDB="tmdb",GO_CONNECTIONS="connections",GO_LIVE_TV="live_tv",
        GO_HOME_ASSISTANT="home_assistant",GO_FOOTBALL="football",GO_NOTIFICATIONS="notifications",GO_KIDS="kids",GO_VOICE="voice";

    /** One feature on this TV: its plain name, state, why, and where its next step is (null when there is none). */
    static final class Capability {
        final String id,name,words,go;final State state;
        Capability(String id,String name,State state,String words,String go){this.id=id;this.name=name;this.state=state;this.words=words;this.go=go;}
        boolean shown(){return state!=State.UNSUPPORTED_ON_DEVICE;}
        @Override public String toString(){return id+"="+state.id;}
    }

    /** What Unified TV read on this TV. Plain fields so tests can make up any TV. */
    static final class Facts {
        int sdk;String release="";
        boolean launcherApp;      // this app carries the switched-off Home entry (the launcher, not the preview)
        boolean homeRole=true;    // Android lets the household choose an app as Home (RoleManager on Android 10+)
        boolean isHome;
        boolean librarySetUp;String libraryProblem;
        boolean tmdbKey;
        boolean airPin,airPlayReady;
        boolean nordVpn;
        boolean liveVerifier,liveVerified;String livePackage="";
        boolean haSaved,haConnected;
        boolean footballKey;
        boolean overlays;
        boolean notificationAccess;
        boolean kidsGuardAllowed;
        boolean speech,microphone,systemVoice;
        boolean outputs,exactOutput;
        boolean lowRam;
        boolean reviewBuild;
    }

    // --- pure
    static List<Capability> evaluate(Facts f){List<Capability> out=new ArrayList<>();for(String id:ALL)out.add(evaluate(f,id));return out;}
    static Capability evaluate(Facts f,String id){
        switch(id){
            case HOME_SCREEN:
                if(!f.launcherApp)return c(id,"Unified TV as the Home screen",State.UNSUPPORTED_ON_DEVICE,"This is the Unified TV preview, which never becomes the Home screen, so the Home button always opens the TV's own. The separate Unified TV app can.",null);
                if(!f.homeRole)return c(id,"Unified TV as the Home screen",State.UNSUPPORTED_ON_DEVICE,"This TV doesn't let an app be chosen as its Home screen, so the Home button opens the TV's own.",null);
                if(f.isHome)return c(id,"Unified TV as the Home screen",State.AVAILABLE,"The Home button opens Unified TV. You can switch back to the TV's own Home screen at any time.",GO_APPEARANCE);
                return c(id,"Unified TV as the Home screen",State.SETUP_REQUIRED,"The Home button still opens the TV's own Home screen. Choose Unified TV in Appearance & Home > Home button.",GO_APPEARANCE);
            case LIBRARY:
                if(!f.librarySetUp)return c(id,"Your Films & TV",State.SETUP_REQUIRED,"No media server is chosen yet. Unified TV looks for one on your home network; you never type an address.",GO_LIBRARY);
                if(f.libraryProblem!=null)return c(id,"Your Films & TV",State.TEMPORARILY_UNAVAILABLE,"Set up, but the last check didn't work: "+f.libraryProblem,GO_LIBRARY);
                return c(id,"Your Films & TV",State.AVAILABLE,"Set up, and the media server answered last time.",GO_LIBRARY);
            case POSTERS:
                return f.tmdbKey?c(id,"Film posters",State.AVAILABLE,"Posters and details come from TMDB with your own key.",GO_TMDB)
                    :c(id,"Film posters",State.SETUP_REQUIRED,"Add your own free TMDB key on your phone to show posters and details.",GO_TMDB);
            case AIRPLAY:
                if(!f.airPin)return c(id,"AirPlay from iPhone",State.SETUP_REQUIRED,"Needs AirPin LITE, from the TV's app store if it offers it. Setup then happens in AirPin itself.",GO_CONNECTIONS);
                return f.airPlayReady?c(id,"AirPlay from iPhone",State.AVAILABLE,"AirPin LITE is offering AirPlay on your home network.",GO_CONNECTIONS)
                    :c(id,"AirPlay from iPhone",State.TEMPORARILY_UNAVAILABLE,"AirPin LITE is installed but isn't offering AirPlay on the network right now. Open it once.",GO_CONNECTIONS);
            case NORDVPN:
                return f.nordVpn?c(id,"NordVPN",State.AVAILABLE,"Installed; it opens from the icons at the top right. NordVPN being on doesn't unlock Live TV by itself.",GO_LIVE_TV)
                    :c(id,"NordVPN",State.SETUP_REQUIRED,"Not installed. Install it from the TV's app store if it offers it.",GO_LIVE_TV);
            case LIVE_TV:
                if(f.liveVerified)return c(id,"Live TV",State.AVAILABLE,"Unified TV checked by itself that Live TV can only connect through NordVPN.",GO_LIVE_TV);
                if(!f.liveVerifier)return c(id,"Live TV",State.UNSUPPORTED_ON_DEVICE,"This TV gives apps no way to check that Live TV can only connect through NordVPN, so Unified TV keeps Live TV locked here.",GO_LIVE_TV);
                if(f.livePackage==null||f.livePackage.isEmpty())return c(id,"Live TV",State.SETUP_REQUIRED,"No single Live TV app was recognised on this TV.",GO_LIVE_TV);
                return c(id,"Live TV",State.TEMPORARILY_UNAVAILABLE,"Locked: the automatic check didn't confirm that Live TV can only connect through NordVPN.",GO_LIVE_TV);
            case HOME_ASSISTANT:
                if(!f.haSaved)return c(id,"Home Assistant",State.SETUP_REQUIRED,"Not set up. You set it up on your phone, from a code on the TV.",GO_HOME_ASSISTANT);
                return f.haConnected?c(id,"Home Assistant",State.AVAILABLE,"Set up, and it accepted Unified TV's sign-in last time it connected.",GO_HOME_ASSISTANT)
                    :c(id,"Home Assistant",State.TEMPORARILY_UNAVAILABLE,"Set up, but it hasn't accepted a connection from this TV yet. Smart Home shows why.",GO_HOME_ASSISTANT);
            case FOOTBALL:
                return f.footballKey?c(id,"Football fixtures",State.AVAILABLE,"Set up with your own API-Football key.",GO_FOOTBALL)
                    :c(id,"Football fixtures",State.SETUP_REQUIRED,"Add your own free API-Football key on your phone.",GO_FOOTBALL);
            case ALERTS_OVER_APPS:
                return f.overlays?c(id,"Doorbell and alarms over other apps",State.AVAILABLE,"Allowed. Whether they appear over every app hasn't been tested on this TV.",GO_NOTIFICATIONS)
                    :c(id,"Doorbell and alarms over other apps",State.PERMISSION_REQUIRED,"Needs \"Display over other apps\" for Unified TV in the TV's own settings. Without it, alerts show only while Unified TV is open.",GO_NOTIFICATIONS);
            case NOW_PLAYING:
                return f.notificationAccess?c(id,"Now Playing for other apps' music",State.AVAILABLE,"Unified TV can see what another app is playing, to show it and its controls.",null)
                    :c(id,"Now Playing for other apps' music",State.PERMISSION_REQUIRED,"Needs notification access for Unified TV in the TV's own settings. Without it, Now Playing can't see other apps.",null);
            case KIDS_GUARD:
                return f.kidsGuardAllowed?c(id,"Kids app guard",State.AVAILABLE,"Allowed in the TV's accessibility settings. It works only while Kids is on and the guard is switched on.",GO_KIDS)
                    :c(id,"Kids app guard",State.PERMISSION_REQUIRED,"Needs Unified TV switched on in the TV's accessibility settings. Kids settings explains how.",GO_KIDS);
            case VOICE:
                if(f.speech)return f.microphone?c(id,"Speaking to Ask your TV",State.AVAILABLE,"Uses this TV's speech service, only when you start it.",GO_VOICE)
                    :c(id,"Speaking to Ask your TV",State.PERMISSION_REQUIRED,"Unified TV asks for the microphone the first time you speak. You can always type instead.",GO_VOICE);
                if(f.systemVoice)return c(id,"Speaking to Ask your TV",State.AVAILABLE,"Uses the TV's own voice input screen, only when you start it.",GO_VOICE);
                return c(id,"Speaking to Ask your TV",State.UNSUPPORTED_ON_DEVICE,"This TV has no speech service for apps. You can type a request in Ask your TV instead.",GO_VOICE);
            case SOUND_OUTPUT:
                if(!f.outputs)return c(id,"Where the sound plays",State.UNSUPPORTED_ON_DEVICE,"This TV doesn't tell apps its sound outputs.",null);
                return c(id,"Where the sound plays",State.AVAILABLE,f.exactOutput?"Shown in Connections & Devices. Change it in the TV's own settings.":"Shown in Connections & Devices as the likely output: this Android version doesn't tell apps for certain. Change it in the TV's own settings.",GO_CONNECTIONS);
            case GLASS:
                if(f.sdk<31)return c(id,"Frosted glass in Settings",State.UNSUPPORTED_ON_DEVICE,"Android "+(f.release==null||f.release.isEmpty()?"on this TV":f.release)+" can't blur behind an app, so Settings uses tinted glass without blur. Everything works the same.",null);
                if(f.lowRam)return c(id,"Frosted glass in Settings",State.UNSUPPORTED_ON_DEVICE,"This TV reports little memory, so Settings uses tinted glass without blur. Everything works the same.",null);
                return c(id,"Frosted glass in Settings",State.AVAILABLE,"Settings blurs a still copy of its own background. Lightweight rendering turns it off.",GO_APPEARANCE);
            case REMOTE_CHECK:
                return f.reviewBuild?c(id,"Remote button check",State.AVAILABLE,"Lists every button your remote sends. Nothing leaves the TV.",GO_CONNECTIONS)
                    :c(id,"Remote button check",State.UNSUPPORTED_ON_DEVICE,"Only in the Unified TV review build, so remote buttons are never recorded in an everyday build.",null);
            default:throw new IllegalArgumentException("unknown capability "+id);
        }
    }
    private static Capability c(String id,String name,State s,String words,String go){return new Capability(id,name,s,words,go);}
    /** The features in one state, in the page's order. */
    static List<Capability> in(List<Capability> all,State s){List<Capability> out=new ArrayList<>();for(Capability c:all)if(c.state==s)out.add(c);return out;}
    /** One line for a summary or a test: "Working: 3 · Not set up yet: 5 …", empty states left out. */
    static String summary(List<Capability> all){List<String> parts=new ArrayList<>();for(State s:ORDER){int n=in(all,s).size();if(n>0)parts.add(s.words+": "+n);}return String.join(" · ",parts);}

    // --- on the TV
    /** Every feature, read now. Main thread is fine: all local reads (package manager, settings, the vault). */
    static List<Capability> read(Context c){Facts f=facts(c,new HashSet<>(ALL));return evaluate(f);}
    /** Just one feature, reading only what it needs (for hiding a row while a page is drawn). */
    static Capability check(Context c,String id){return evaluate(facts(c,Collections.singleton(id)),id);}
    static boolean shown(Context c,String id){return check(c,id).shown();}

    /** Tests only: facts to use instead of reading the TV (null: read it). */
    static volatile Facts forTests;

    static Facts facts(Context c,Set<String> need){
        Facts t=forTests;if(t!=null)return t;
        Facts f=new Facts();f.sdk=Build.VERSION.SDK_INT;f.release=Build.VERSION.RELEASE==null?"":Build.VERSION.RELEASE;
        android.content.SharedPreferences p=c.getSharedPreferences(HouseholdSettings.PREFS,0);PackageManager pm=c.getPackageManager();
        if(need.contains(HOME_SCREEN)){
            f.launcherApp=DefaultHome.available(c);
            // Android 10+: whether the system offers apps the Home role at all (an older TV always has the Home chooser).
            if(Build.VERSION.SDK_INT>=29)try{android.app.role.RoleManager roles=c.getSystemService(android.app.role.RoleManager.class);f.homeRole=roles!=null&&roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME);}catch(RuntimeException e){f.homeRole=false;}
            try{f.isHome=f.launcherApp&&DefaultHome.isDefault(c);}catch(RuntimeException ignored){}
        }
        if(need.contains(LIBRARY)){f.librarySetUp=HomeShelf.configured(c);f.libraryProblem=f.librarySetUp?MediaSources.lastProblem(c):null;}
        if(need.contains(POSTERS))f.tmdbKey=TmdbKey.saved(c);
        if(need.contains(AIRPLAY)){f.airPin=AirPlayReceiver.installed(c)!=null;f.airPlayReady=f.airPin&&AirPlayReceiver.ready;}
        if(need.contains(NORDVPN))f.nordVpn=launchable(pm,LiveTvProtection.NORDVPN);
        if(need.contains(LIVE_TV)){f.liveVerifier=LiveTvProtection.VERIFIER!=null;
            try{f.livePackage=LiveTvProtection.livePackage(c);f.liveVerified=f.liveVerifier&&LiveTvProtection.opens(LiveTvProtection.now(c,f.livePackage));}catch(RuntimeException ignored){}}
        if(need.contains(HOME_ASSISTANT)){Boolean saved=SettingsActivity.haForTests;
            if(saved==null)try{org.json.JSONObject ha=new Vault(c).read("ha");saved=ha.optBoolean("enabled")&&!ha.optString("token").isEmpty();}catch(Exception e){saved=false;}
            f.haSaved=saved;f.haConnected=saved&&p.getBoolean(AlarmWatch.CONFIGURED,false);}
        if(need.contains(FOOTBALL))try{f.footballKey=FixtureFeed.apiConfigured(c);}catch(Exception ignored){}
        if(need.contains(ALERTS_OVER_APPS))try{f.overlays=android.provider.Settings.canDrawOverlays(c);}catch(RuntimeException ignored){}
        if(need.contains(NOW_PLAYING))f.notificationAccess=MediaWatch.granted(c);
        if(need.contains(KIDS_GUARD))try{f.kidsGuardAllowed=KidsGuard.enabledIn(android.provider.Settings.Secure.getString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),c.getPackageName(),KidsGuardService.class.getName());}catch(RuntimeException ignored){}
        if(need.contains(VOICE)){
            try{f.speech=android.speech.SpeechRecognizer.isRecognitionAvailable(c);}catch(RuntimeException ignored){}
            f.microphone=c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
            try{f.systemVoice=pm.resolveActivity(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH),0)!=null;}catch(RuntimeException ignored){}
        }
        if(need.contains(SOUND_OUTPUT)){f.outputs=!SoundOutput.read(c).isEmpty();f.exactOutput=f.sdk>=33;}
        if(need.contains(GLASS))try{f.lowRam=((android.app.ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).isLowRamDevice();}catch(RuntimeException ignored){}
        if(need.contains(REMOTE_CHECK))f.reviewBuild=RemoteCheckActivity.allowed(c);
        return f;
    }
    private static boolean launchable(PackageManager pm,String pkg){
        try{return pm.getLeanbackLaunchIntentForPackage(pkg)!=null||pm.getLaunchIntentForPackage(pkg)!=null;}catch(RuntimeException e){return false;}
    }
}
