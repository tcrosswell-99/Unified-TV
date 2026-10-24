package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class VpnGateTest {
    @Test public void checkStartedBeforeANetworkChangeIsDiscarded(){
        VpnGate.invalidate();int stale=VpnGate.begin();VpnGate.invalidate();
        assertFalse(VpnGate.commit(stale,VpnGate.State.CONNECTED,10));assertEquals(VpnGate.State.CHECKING,VpnGate.current());
        int fresh=VpnGate.begin();assertTrue(VpnGate.commit(fresh,VpnGate.State.DISCONNECTED,20));assertEquals(VpnGate.State.DISCONNECTED,VpnGate.current());
    }
    @Test public void noExplanationEverUnlocksLiveTv(){
        for(boolean tunnel:new boolean[]{false,true})for(boolean routed:new boolean[]{false,true}){String s=VpnGate.explanation(tunnel,routed);assertFalse(s,s.toLowerCase().contains("protected"+" and ready"));assertTrue(s,tunnel?s.contains("Live TV stays locked"):s.contains("Connect NordVPN"));}
        assertTrue(VpnGate.explanation(true,false).contains("leave some apps out"));
        assertTrue(VpnGate.explanation(true,true).contains("until its protection has been checked"));
        // Content first, underlying technology hidden: no network jargon in what a viewer reads.
        for(boolean t:new boolean[]{false,true})for(boolean r:new boolean[]{false,true}){String s=VpnGate.explanation(t,r).toLowerCase();for(String jargon:new String[]{"tunnel","routing","split","transport","ip "})assertFalse(s,s.contains(jargon));}
    }
}
