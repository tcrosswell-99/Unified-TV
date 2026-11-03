package io.unifiedtv.preview;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static io.unifiedtv.preview.LiveTvProtection.State.*;

/**
 * OD-26 (owner, 27 October 2026; amended 3 November 2026): Live TV opens when a VPN is connected. Android reports a VPN
 * and NordVPN is installed: open, whatever other VPN apps, system VPN services, notification access or NordVPN's own
 * notification say. NordVPN's notice only adds the country. No VPN: locked. Never called a kill switch.
 */
public class LiveTvProtectionTest {
    private static LiveTvProtection.Facts facts(VpnGate.State vpn,boolean nord,boolean access,Boolean says){
        LiveTvProtection.Facts f=new LiveTvProtection.Facts();f.vpn=vpn;f.nordInstalled=nord;f.notificationAccess=access;f.nordSays=says;return f;
    }
    private static final VpnGate.State UP=VpnGate.State.CONNECTED,DOWN=VpnGate.State.DISCONNECTED;

    @Test public void theDecisionTable(){
        assertEquals("VPN up and NordVPN's own notice says connected",OPEN,LiveTvProtection.decide(facts(UP,true,true,true)));
        assertEquals("VPN up, no notification access",OPEN,LiveTvProtection.decide(facts(UP,true,false,null)));
        assertEquals("VPN up, access allowed but NordVPN's notice unreadable",OPEN,LiveTvProtection.decide(facts(UP,true,true,null)));
        LiveTvProtection.Facts other=facts(UP,true,false,null);other.otherVpn="Data Saver";
        assertEquals("VPN up, another VPN app installed, no notification access (the owner's TCL)",OPEN,LiveTvProtection.decide(other));
        LiveTvProtection.Facts system=facts(UP,true,false,null);system.systemVpn=true;
        assertEquals("VPN up, a system VPN service on the TV",OPEN,LiveTvProtection.decide(system));
        system.otherVpn="Other VPN";system.notificationAccess=true;
        assertEquals("VPN up, other VPN app and system VPN, NordVPN says nothing",OPEN,LiveTvProtection.decide(system));
        assertEquals("NordVPN's notice never refuses: VPN up and the notice says not connected",OPEN,LiveTvProtection.decide(facts(UP,true,true,false)));
        assertEquals("no VPN: locked even if a stale notice said connected",DISCONNECTED,LiveTvProtection.decide(facts(DOWN,true,true,true)));
        assertEquals("no VPN",DISCONNECTED,LiveTvProtection.decide(facts(DOWN,true,false,null)));
        assertEquals(CHECKING,LiveTvProtection.decide(facts(VpnGate.State.CHECKING,true,true,true)));
        assertEquals("Android's answer unreadable",ERROR,LiveTvProtection.decide(facts(VpnGate.State.UNKNOWN,true,true,true)));
        assertEquals("no NordVPN on the TV",NO_NORDVPN,LiveTvProtection.decide(facts(UP,false,true,true)));
        assertEquals(ERROR,LiveTvProtection.decide(null));
    }
    /** Whatever NordVPN's notice says, only Android's VPN state decides. */
    @Test public void nordVpnsNoticeNeverDecides(){
        for(CharSequence[] words:new CharSequence[][]{{"NordVPN","Not connected"},{"You're not connected to VPN"},{"Unprotected"},{"No connection"},{"Connected devices: 3"},{"Connected to United Kingdom #2236"}}){
            Boolean says=NordCountry.status(words);
            for(boolean access:new boolean[]{true,false}){
                assertEquals(Arrays.toString(words),OPEN,LiveTvProtection.decide(facts(UP,true,access,says)));
                assertEquals(Arrays.toString(words),DISCONNECTED,LiveTvProtection.decide(facts(DOWN,true,access,says)));
            }
        }
    }
    @Test public void onlyOpenOpensAndOnlyCheckingIsNotADrop(){
        for(LiveTvProtection.State s:LiveTvProtection.State.values()){
            assertEquals(s.name(),s==OPEN,LiveTvProtection.opens(s));
            assertEquals(s.name(),s!=OPEN&&s!=CHECKING,LiveTvProtection.dropped(s));
        }
    }
    /** NordVPN's notice only words an open Live TV: its name and country when it says connected, else "A VPN is connected". */
    @Test public void nordVpnsNoticeOnlyAddsTheCountry(){
        LiveTvProtection.Facts f=facts(UP,true,true,true);f.country="GB";
        assertEquals("NordVPN is connected to United Kingdom. If NordVPN drops, Unified TV will cover Live TV.",LiveTvProtection.opened(f));
        f.country=null;assertEquals("NordVPN is connected. If NordVPN drops, Unified TV will cover Live TV.",LiveTvProtection.opened(f));
        f.country="GB";f.notificationAccess=false;assertEquals("a remembered word without access isn't used",
            "A VPN is connected. If NordVPN drops, Unified TV will cover Live TV.",LiveTvProtection.opened(f));
        assertEquals("A VPN is connected. If NordVPN drops, Unified TV will cover Live TV.",LiveTvProtection.opened(facts(UP,true,true,false)));
        assertEquals("A VPN is connected. If NordVPN drops, Unified TV will cover Live TV.",LiveTvProtection.opened(null));
    }
    @Test public void theWordsArePlainAndHonest(){
        List<String> words=new ArrayList<>();for(LiveTvProtection.State s:LiveTvProtection.State.values())words.add(LiveTvProtection.word(s));
        assertEquals(Arrays.asList("open","checking","disconnected","locked","error"),words);
        assertEquals("Live TV opens when a VPN is connected.",LiveTvProtection.RULE);
        assertEquals("Live TV is locked. Connect NordVPN first.",LiveTvProtection.message(DISCONNECTED));
        LiveTvProtection.last=null;
        try{
            for(LiveTvProtection.State s:LiveTvProtection.State.values()){String m=LiveTvProtection.message(s);assertFalse(m,m.isEmpty());
                String low=m.toLowerCase(Locale.ROOT);
                if(s!=OPEN&&s!=CHECKING)assertTrue(m,m.startsWith("Live TV is locked."));
                for(String gone:new String[]{"can't tell","can't confirm","notification access","another app"})assertFalse(m,low.contains(gone));
                for(String claim:new String[]{"verified","protected","safe","kill switch"})assertFalse(m,low.contains(claim));
                for(String jargon:new String[]{"tunnel","routing","lockdown","uid","socket","transport"})assertFalse(m,low.contains(jargon));}
        }finally{LiveTvProtection.last=null;}
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
