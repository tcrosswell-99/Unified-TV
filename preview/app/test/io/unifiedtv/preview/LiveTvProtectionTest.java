package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.unifiedtv.preview.LiveTvProtection.State.*;
/** SET-063 (owner, 25 October 2026): automatic verification only; no attestation; fail closed. */
public class LiveTvProtectionTest {
    @Test public void withoutAnAutomaticCheckLiveTvStaysLockedEvenWithTheVpnUp(){
        assertEquals(LOCKED,LiveTvProtection.decide(true,false,null,false));
        assertEquals("a result without a verifier is ignored",LOCKED,LiveTvProtection.decide(true,false,true,false));
        assertFalse(LiveTvProtection.opens(LiveTvProtection.decide(true,false,true,false)));
    }
    @Test public void androidHasNoVerifierSoNothingCanEverBeVerified(){
        assertNull("no route verifier exists for a third-party Android app",LiveTvProtection.VERIFIER);
        for(boolean tunnel:new boolean[]{false,true})for(Boolean r:new Boolean[]{null,false,true})for(boolean failed:new boolean[]{false,true})
            assertNotEquals(VERIFIED,LiveTvProtection.decide(tunnel,LiveTvProtection.VERIFIER!=null,r,failed));
    }
    @Test public void onlyAnAutomaticPassOpensAndUnknownDisconnectedOrFailedLock(){
        assertEquals(VERIFIED,LiveTvProtection.decide(true,true,true,false));assertTrue(LiveTvProtection.opens(VERIFIED));
        assertEquals(VERIFYING,LiveTvProtection.decide(true,true,null,false));
        assertEquals(LOCKED,LiveTvProtection.decide(true,true,false,false));
        assertEquals(DISCONNECTED,LiveTvProtection.decide(false,true,true,false));
        assertEquals(ERROR,LiveTvProtection.decide(true,true,true,true));
        for(LiveTvProtection.State s:LiveTvProtection.State.values())if(s!=VERIFIED)assertFalse(s.name(),LiveTvProtection.opens(s));
    }
    @Test public void theStateWordsAreSet063s(){
        java.util.List<String> words=new java.util.ArrayList<>();for(LiveTvProtection.State s:LiveTvProtection.State.values())words.add(LiveTvProtection.word(s));
        assertEquals(java.util.Arrays.asList("locked","verifying","verified","disconnected","error"),words);
    }
    @Test public void everyLockedStateExplainsItselfPlainly(){
        for(LiveTvProtection.State d:LiveTvProtection.State.values()){if(d==VERIFIED)continue;String s=LiveTvProtection.message(d);assertFalse(s,s.isEmpty());
            assertFalse(s,s.toLowerCase().contains("verified"));
            for(String jargon:new String[]{"tunnel","routing","lockdown","uid","socket"})assertFalse(s,s.toLowerCase().contains(jargon));}
        assertTrue(LiveTvProtection.message(LOCKED).contains("stays locked on every route"));
    }
}
