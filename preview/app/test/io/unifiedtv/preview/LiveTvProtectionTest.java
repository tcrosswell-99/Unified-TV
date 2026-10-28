package io.unifiedtv.preview;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static io.unifiedtv.preview.LiveTvProtection.State.*;

/**
 * OD-26 (owner, 27 October 2026): Live TV opens when NordVPN is connected, replacing SET-063's "automatic verification
 * only". The VPN Android reports must be NordVPN's: its own notification says connected, or it is the only app that can
 * run a VPN. Another VPN app and no notification access: can't tell, so locked. Never called a kill switch.
 */
public class LiveTvProtectionTest {
    private static LiveTvProtection.Facts facts(VpnGate.State vpn,boolean nord,boolean access,Boolean says,boolean onlyNord){
        LiveTvProtection.Facts f=new LiveTvProtection.Facts();f.vpn=vpn;f.nordInstalled=nord;f.notificationAccess=access;f.nordSays=says;f.onlyNordCanVpn=onlyNord;return f;
    }
    private static final VpnGate.State UP=VpnGate.State.CONNECTED,DOWN=VpnGate.State.DISCONNECTED;

    @Test public void theDecisionTable(){
        assertEquals("VPN up and NordVPN's own notice says connected",OPEN,LiveTvProtection.decide(facts(UP,true,true,true,false)));
        assertEquals("VPN up and NordVPN is the only VPN app (no notification access)",OPEN,LiveTvProtection.decide(facts(UP,true,false,null,true)));
        assertEquals("VPN up, NordVPN the only VPN app, access allowed but no notice read",OPEN,LiveTvProtection.decide(facts(UP,true,true,null,true)));
        assertEquals("VPN up, another VPN app installed, no notification access: can't tell",UNSURE,LiveTvProtection.decide(facts(UP,true,false,null,false)));
        assertEquals("VPN up, another VPN app, NordVPN says nothing",NOT_NORDVPN,LiveTvProtection.decide(facts(UP,true,true,null,false)));
        assertEquals("NordVPN's own notice says it isn't connected: locked whatever else",DISCONNECTED,LiveTvProtection.decide(facts(UP,true,true,false,true)));
        assertEquals("no VPN: locked even if a stale notice said connected",DISCONNECTED,LiveTvProtection.decide(facts(DOWN,true,true,true,true)));
        assertEquals("no VPN",DISCONNECTED,LiveTvProtection.decide(facts(DOWN,true,false,null,true)));
        assertEquals(CHECKING,LiveTvProtection.decide(facts(VpnGate.State.CHECKING,true,true,true,true)));
        assertEquals("Android's answer unreadable",ERROR,LiveTvProtection.decide(facts(VpnGate.State.UNKNOWN,true,true,true,true)));
        assertEquals("no NordVPN on the TV",NO_NORDVPN,LiveTvProtection.decide(facts(UP,false,true,true,true)));
        assertEquals(ERROR,LiveTvProtection.decide(null));
    }
    /** Review 2: NordVPN saying "Not connected" while another app's VPN is up never opens Live TV. */
    @Test public void nordVpnsNegativeWordsLockWhileAnotherVpnIsUp(){
        for(CharSequence[] words:new CharSequence[][]{{"NordVPN","Not connected"},{"You're not connected to VPN"},{"Unprotected"},{"No connection"}}){
            Boolean says=NordCountry.status(words);
            assertEquals(Arrays.toString(words),DISCONNECTED,LiveTvProtection.decide(facts(UP,true,true,says,false)));
            assertFalse(LiveTvProtection.opens(LiveTvProtection.decide(facts(UP,true,true,says,true))));
        }
        assertEquals("unsure wording, another VPN app: locked",NOT_NORDVPN,LiveTvProtection.decide(facts(UP,true,true,NordCountry.status("Connected devices: 3"),false)));
        assertEquals(OPEN,LiveTvProtection.decide(facts(UP,true,true,NordCountry.status("Connected to United Kingdom #2236"),false)));
    }
    /** Review (rule 8): a system VPN service with no app (a TCL data saver) could be the VPN that is up: NordVPN's word needed. */
    @Test public void aSystemVpnServiceNeedsNordVpnsOwnWord(){
        LiveTvProtection.Facts f=facts(UP,true,false,null,true);f.systemVpn=true;
        assertEquals("never open on 'only VPN app' alone",UNSURE,LiveTvProtection.decide(f));
        f.notificationAccess=true;assertEquals(NOT_NORDVPN,LiveTvProtection.decide(f));
        f.nordSays=true;assertEquals(OPEN,LiveTvProtection.decide(f));
        f.nordSays=false;assertEquals(DISCONNECTED,LiveTvProtection.decide(f));
        f.systemVpn=false;f.nordSays=null;f.notificationAccess=false;assertEquals("no system VPN: as before",OPEN,LiveTvProtection.decide(f));
    }
    @Test public void notificationWordsCountOnlyWithAccess(){
        assertEquals("a remembered word without access is ignored",UNSURE,LiveTvProtection.decide(facts(UP,true,false,true,false)));
        assertEquals("a remembered 'disconnected' without access is ignored",OPEN,LiveTvProtection.decide(facts(UP,true,false,false,true)));
    }
    @Test public void onlyOpenOpensAndOnlyCheckingIsNotADrop(){
        for(LiveTvProtection.State s:LiveTvProtection.State.values()){
            assertEquals(s.name(),s==OPEN,LiveTvProtection.opens(s));
            assertEquals(s.name(),s!=OPEN&&s!=CHECKING,LiveTvProtection.dropped(s));
        }
    }
    @Test public void nordVpnMustBeTheOnlyAppThatCanRunAVpn(){
        assertTrue(LiveTvProtection.onlyNord(Collections.singleton(LiveTvProtection.NORDVPN)));
        assertFalse("another VPN app",LiveTvProtection.onlyNord(Arrays.asList(LiveTvProtection.NORDVPN,"com.example.othervpn")));
        assertFalse("NordVPN not seen as a VPN app",LiveTvProtection.onlyNord(Collections.singleton("com.example.othervpn")));
        assertFalse("nothing seen",LiveTvProtection.onlyNord(Collections.<String>emptyList()));assertFalse(LiveTvProtection.onlyNord(null));
    }
    @Test public void theWordsArePlainAndHonest(){
        List<String> words=new ArrayList<>();for(LiveTvProtection.State s:LiveTvProtection.State.values())words.add(LiveTvProtection.word(s));
        assertEquals(Arrays.asList("open","checking","disconnected","locked","locked","locked","error"),words);
        assertEquals("NordVPN is connected. If NordVPN drops, Unified TV will cover Live TV.",LiveTvProtection.message(OPEN));
        assertEquals("Live TV is locked. Connect NordVPN first.",LiveTvProtection.message(DISCONNECTED));
        assertTrue(LiveTvProtection.unsure("TCL Data Saver").contains("A VPN is connected, but Unified TV can't confirm it's NordVPN because TCL Data Saver can also run a VPN. Allow notification access"));
        assertTrue("never a bare contradiction",LiveTvProtection.unsure(null).contains("because another app can also run a VPN"));
        for(String old:new String[]{"Can't tell","can't tell"})assertFalse(LiveTvProtection.unsure("X").contains(old));
        for(LiveTvProtection.State s:LiveTvProtection.State.values()){String m=LiveTvProtection.message(s);assertFalse(m,m.isEmpty());
            String low=m.toLowerCase(Locale.ROOT);
            if(s!=OPEN&&s!=CHECKING)assertTrue(m,m.startsWith("Live TV is locked."));
            for(String claim:new String[]{"verified","protected","safe","kill switch"})assertFalse(m,low.contains(claim));
            for(String jargon:new String[]{"tunnel","routing","lockdown","uid","socket","transport"})assertFalse(m,low.contains(jargon));}
    }
    /** AGENTS.md rule 8: "kill switch" appears only to say this isn't one. */
    static void neverClaimsAKillSwitch(String text){
        String low=text.toLowerCase(Locale.ROOT).replace('’','\'');int i=low.indexOf("kill switch");
        while(i>=0){String before=low.substring(Math.max(0,i-12),i);
            assertTrue("\"kill switch\" only to deny it: "+text,before.endsWith("isn't a ")||before.endsWith("not a "));
            i=low.indexOf("kill switch",i+1);}
    }
    @Test public void theRuleIsNeverCalledAKillSwitch(){
        assertTrue(LiveTvProtection.NOT_A_KILL_SWITCH.startsWith("This isn't a kill switch"));
        List<String> all=new ArrayList<>(Arrays.asList(LiveTvProtection.RULE,LiveTvProtection.COVERS,LiveTvProtection.NOT_A_KILL_SWITCH,LiveTvCoverActivity.NOTICE,LiveTvCoverActivity.AGAIN));
        for(LiveTvProtection.State s:LiveTvProtection.State.values()){all.add(LiveTvProtection.message(s));all.add(OwnerChecks.liveTv("VPN ● Connected",true,s));}
        for(String s:all)neverClaimsAKillSwitch(s);
        assertEquals("NordVPN disconnected — Live TV is covered. Reconnect NordVPN.",LiveTvCoverActivity.NOTICE);
    }
    @Test public void theSafetyNetEndsOnlyWhenAnotherAppIsInFront(){
        String live="com.example.livetv",own="io.unifiedtv.preview",kb="com.example.keyboard";
        assertTrue(LiveTvGuard.left("com.tcl.launcher",live,own,kb));
        assertTrue(LiveTvGuard.left("com.netflix.ninja",live,own,kb));
        for(String stay:new String[]{live,LiveTvProtection.NORDVPN,own,kb,"android","com.android.systemui",null,""})assertFalse(String.valueOf(stay),LiveTvGuard.left(stay,live,own,kb));
        assertFalse("nothing known about the session",LiveTvGuard.left("com.tcl.launcher","",own,kb));
    }
}
