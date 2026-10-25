package io.unifiedtv.preview;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

/**
 * SET-006 / SET-003: every feature is in one of the five states, worked out from what was read on the TV, never from the
 * manufacturer's name; and each state has plain words and an honest reason.
 */
public class CapabilitiesTest {
    private static Capabilities.Facts nothing(){Capabilities.Facts f=new Capabilities.Facts();f.sdk=30;f.release="11";return f;}
    private static Capabilities.State state(Capabilities.Facts f,String id){return Capabilities.evaluate(f,id).state;}

    @Test public void theFiveStatesAreSet003sVocabulary(){
        List<String> ids=new ArrayList<>();for(Capabilities.State s:Capabilities.State.values())ids.add(s.id);
        assertEquals(Arrays.asList("available","setup_required","permission_required","temporarily_unavailable","unsupported_on_device"),ids);
        assertEquals("the page lists every state, once",new HashSet<>(Arrays.asList(Capabilities.State.values())),new HashSet<>(Capabilities.ORDER));
        assertEquals(Capabilities.State.values().length,Capabilities.ORDER.size());
    }
    @Test public void everyFeatureHasAStateAPlainNameAndAReason(){
        for(Capabilities.Facts f:Arrays.asList(nothing(),everything())){
            List<Capabilities.Capability> all=Capabilities.evaluate(f);assertEquals(Capabilities.ALL.size(),all.size());
            for(Capabilities.Capability c:all){assertNotNull(c.id,c.state);assertFalse(c.id,c.name.isEmpty());assertTrue(c.id+": a sentence, not a code",c.words.length()>20&&c.words.endsWith("."));
                assertFalse(c.id+" shows no package names",c.words.matches(".*\\b(com|io)\\.[a-z]+\\..*"));}
        }
    }
    private static Capabilities.Facts everything(){
        Capabilities.Facts f=nothing();f.sdk=34;f.release="14";f.launcherApp=true;f.homeRole=true;f.isHome=true;f.librarySetUp=true;f.tmdbKey=true;f.airPin=true;f.airPlayReady=true;
        f.nordVpn=true;f.liveVerifier=true;f.liveVerified=true;f.livePackage="tv.example.live";f.haSaved=true;f.haConnected=true;f.footballKey=true;f.overlays=true;
        f.notificationAccess=true;f.kidsGuardAllowed=true;f.speech=true;f.microphone=true;f.outputs=true;f.exactOutput=true;f.reviewBuild=true;return f;
    }
    @Test public void aTvWithEverythingWorkingIsAllAvailable(){
        for(Capabilities.Capability c:Capabilities.evaluate(everything()))assertEquals(c.id,Capabilities.State.AVAILABLE,c.state);
        assertEquals("Working: "+Capabilities.ALL.size(),Capabilities.summary(Capabilities.evaluate(everything())));
    }
    @Test public void aFreshPreviewOnAndroid11(){
        Capabilities.Facts f=nothing();f.outputs=true;
        assertEquals("the preview is never Home",Capabilities.State.UNSUPPORTED_ON_DEVICE,state(f,Capabilities.HOME_SCREEN));
        assertTrue(Capabilities.evaluate(f,Capabilities.HOME_SCREEN).words.contains("preview"));
        for(String id:new String[]{Capabilities.LIBRARY,Capabilities.POSTERS,Capabilities.AIRPLAY,Capabilities.NORDVPN,Capabilities.HOME_ASSISTANT,Capabilities.FOOTBALL})
            assertEquals(id,Capabilities.State.SETUP_REQUIRED,state(f,id));
        for(String id:new String[]{Capabilities.ALERTS_OVER_APPS,Capabilities.NOW_PLAYING,Capabilities.KIDS_GUARD})
            assertEquals(id,Capabilities.State.PERMISSION_REQUIRED,state(f,id));
        assertEquals("no verifier on Android: Live TV stays locked",Capabilities.State.UNSUPPORTED_ON_DEVICE,state(f,Capabilities.LIVE_TV));
        assertEquals("no live blur before Android 12",Capabilities.State.UNSUPPORTED_ON_DEVICE,state(f,Capabilities.GLASS));
        assertTrue(Capabilities.evaluate(f,Capabilities.GLASS).words.contains("Android 11"));
        assertEquals(Capabilities.State.UNSUPPORTED_ON_DEVICE,state(f,Capabilities.VOICE));
        assertEquals("everyday build: no key logging",Capabilities.State.UNSUPPORTED_ON_DEVICE,state(f,Capabilities.REMOTE_CHECK));
        assertTrue(Capabilities.evaluate(f,Capabilities.SOUND_OUTPUT).words.contains("doesn't tell apps for certain"));
    }
    @Test public void homeScreenFollowsWhatAndroidOffers(){
        Capabilities.Facts f=nothing();f.launcherApp=true;
        f.homeRole=false;assertEquals(Capabilities.State.UNSUPPORTED_ON_DEVICE,state(f,Capabilities.HOME_SCREEN));
        f.homeRole=true;assertEquals(Capabilities.State.SETUP_REQUIRED,state(f,Capabilities.HOME_SCREEN));
        f.isHome=true;assertEquals(Capabilities.State.AVAILABLE,state(f,Capabilities.HOME_SCREEN));
    }
    @Test public void setUpButNotWorkingIsTemporarilyUnavailable(){
        Capabilities.Facts f=nothing();f.librarySetUp=true;f.libraryProblem="The media server didn't answer.";
        Capabilities.Capability lib=Capabilities.evaluate(f,Capabilities.LIBRARY);assertEquals(Capabilities.State.TEMPORARILY_UNAVAILABLE,lib.state);assertTrue(lib.words.contains("didn't answer"));
        f.libraryProblem=null;assertEquals(Capabilities.State.AVAILABLE,state(f,Capabilities.LIBRARY));
        f.airPin=true;assertEquals(Capabilities.State.TEMPORARILY_UNAVAILABLE,state(f,Capabilities.AIRPLAY));
        f.haSaved=true;assertEquals("saved is not connected",Capabilities.State.TEMPORARILY_UNAVAILABLE,state(f,Capabilities.HOME_ASSISTANT));
        f.haConnected=true;assertEquals(Capabilities.State.AVAILABLE,state(f,Capabilities.HOME_ASSISTANT));
        f.liveVerifier=true;assertEquals("no single Live TV app",Capabilities.State.SETUP_REQUIRED,state(f,Capabilities.LIVE_TV));
        f.livePackage="tv.example.live";assertEquals("locked until verified",Capabilities.State.TEMPORARILY_UNAVAILABLE,state(f,Capabilities.LIVE_TV));
    }
    @Test public void aVpnBeingInstalledNeverMeansLiveTvIsProtected(){
        Capabilities.Facts f=nothing();f.nordVpn=true;f.livePackage="tv.example.live";
        assertEquals(Capabilities.State.AVAILABLE,state(f,Capabilities.NORDVPN));
        assertNotEquals(Capabilities.State.AVAILABLE,state(f,Capabilities.LIVE_TV));
        assertTrue(Capabilities.evaluate(f,Capabilities.NORDVPN).words.contains("doesn't unlock Live TV"));
    }
    @Test public void voiceNeedsASpeechServiceAndTheMicrophone(){
        Capabilities.Facts f=nothing();f.speech=true;
        assertEquals(Capabilities.State.PERMISSION_REQUIRED,state(f,Capabilities.VOICE));
        f.microphone=true;assertEquals(Capabilities.State.AVAILABLE,state(f,Capabilities.VOICE));
        f.speech=false;f.systemVoice=true;assertEquals("the TV's own voice input screen",Capabilities.State.AVAILABLE,state(f,Capabilities.VOICE));
        f.systemVoice=false;assertFalse("hidden from routine menus",Capabilities.evaluate(f,Capabilities.VOICE).shown());
        assertTrue(Capabilities.evaluate(f,Capabilities.VOICE).words.contains("type"));
    }
    @Test public void glassFollowsTheAndroidVersionAndMemory(){
        Capabilities.Facts f=nothing();f.sdk=31;assertEquals(Capabilities.State.AVAILABLE,state(f,Capabilities.GLASS));
        f.lowRam=true;assertEquals(Capabilities.State.UNSUPPORTED_ON_DEVICE,state(f,Capabilities.GLASS));
    }
    @Test public void soundOutputSaysWhenItIsOnlyLikely(){
        Capabilities.Facts f=nothing();assertEquals(Capabilities.State.UNSUPPORTED_ON_DEVICE,state(f,Capabilities.SOUND_OUTPUT));
        f.outputs=true;f.exactOutput=true;assertFalse(Capabilities.evaluate(f,Capabilities.SOUND_OUTPUT).words.contains("likely"));
    }
    @Test public void statesGroupAndSummariseInThePagesOrder(){
        List<Capabilities.Capability> all=Capabilities.evaluate(nothing());
        int total=0;for(Capabilities.State s:Capabilities.ORDER)total+=Capabilities.in(all,s).size();assertEquals(all.size(),total);
        String summary=Capabilities.summary(all);
        assertTrue(summary,summary.indexOf("Needs your OK")<summary.indexOf("Not set up yet"));assertFalse("empty states are left out",summary.contains("Working:"));
    }
    @Test public void nextStepsAreOnlyPlacesSettingsKnows(){
        Set<String> known=new HashSet<>(Arrays.asList(Capabilities.GO_APPEARANCE,Capabilities.GO_LIBRARY,Capabilities.GO_TMDB,Capabilities.GO_CONNECTIONS,Capabilities.GO_LIVE_TV,
            Capabilities.GO_HOME_ASSISTANT,Capabilities.GO_FOOTBALL,Capabilities.GO_NOTIFICATIONS,Capabilities.GO_KIDS,Capabilities.GO_VOICE));
        for(Capabilities.Facts f:Arrays.asList(nothing(),everything()))for(Capabilities.Capability c:Capabilities.evaluate(f))if(c.go!=null)assertTrue(c.id+" → "+c.go,known.contains(c.go));
        try{Capabilities.evaluate(nothing(),"teleport");fail("an unknown feature is an error, not a guess");}catch(IllegalArgumentException expected){}
    }
    /** SET-006: "feature flags derive from verified capabilities, not assumptions based on OS label" (or brand). */
    @Test public void neverDecidedByTheManufacturersName()throws Exception{
        String src=new String(Files.readAllBytes(new File(SidebarRuleTest.app(),"src/io/unifiedtv/preview/Capabilities.java").toPath()),StandardCharsets.UTF_8);
        String code=src.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("//[^\\n]*","");
        for(String banned:new String[]{"MANUFACTURER","BRAND","Build.MODEL","Build.PRODUCT","Build.DEVICE","\"sony\"","\"tcl\"","\"samsung\"","room"})
            assertFalse("Capabilities must not read "+banned,code.contains(banned));
    }
}
