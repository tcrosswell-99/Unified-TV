package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class VpnGateTest {
    @Test public void checkStartedBeforeANetworkChangeIsDiscarded(){
        VpnGate.invalidate();int stale=VpnGate.begin();VpnGate.invalidate();
        assertFalse(VpnGate.commit(stale,VpnGate.State.CONNECTED,10));assertEquals(VpnGate.State.CHECKING,VpnGate.current());
        int fresh=VpnGate.begin();assertTrue(VpnGate.commit(fresh,VpnGate.State.DISCONNECTED,20));assertEquals(VpnGate.State.DISCONNECTED,VpnGate.current());
    }
    /** The shield's words say what Android reports, nothing more: never "protected", never a kill switch (OD-26). */
    @Test public void theShieldNeverClaimsProtection(){
        for(VpnGate.State s:VpnGate.State.values()){String l=VpnGate.statusLabel(s).toLowerCase();assertFalse(l,l.contains("protected"));assertFalse(l,l.contains("kill"));assertFalse(l,l.contains("live tv"));}
    }
}
