package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class OwnerChecksTest {
    @Test public void flavourComesFromThePackage(){
        assertEquals("Unified TV launcher",OwnerChecks.flavour("io.unifiedtv.launcher"));assertEquals("Unified TV Preview",OwnerChecks.flavour("io.unifiedtv.preview"));
        assertTrue(OwnerChecks.flavour("io.unifiedtv.launcher.devicecheck").startsWith("review build"));assertEquals("unknown",OwnerChecks.flavour(null));
    }
    @Test public void fingerprintIsSha256InTheSigningRecordsForm()throws Exception{
        String f=OwnerChecks.fingerprint("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(95,f.length());assertTrue(f.startsWith("BA:78:16:BF"));
    }
    @Test public void aDebugKeyIsNamedAndANonDebugKeyIsNotCalledDurable(){
        assertTrue(OwnerChecks.keyKind("CN=Android Debug,O=Android,C=US").startsWith("Android debug key"));
        String other=OwnerChecks.keyKind("CN=Household,O=Example");assertTrue(other.contains("Compare the fingerprint"));assertFalse(other.toLowerCase().contains("verified"));
    }
    @Test public void nasLinesSayWhatWasSeen(){
        assertTrue(OwnerChecks.nas("Home NAS",true,3).endsWith("Offered."));
        assertTrue(OwnerChecks.nas("Home NAS",true,0).endsWith("Not offered."));
        assertTrue(OwnerChecks.nas("",true,-1).startsWith("Media server"));
        assertTrue(OwnerChecks.nas(null,false,5).contains("not offered"));
    }
    @Test public void liveTvIsNeverCalledProtected(){
        for(LiveTvProtection.Decision d:LiveTvProtection.Decision.values()){String s=OwnerChecks.liveTv("VPN ● Connected",true,d).toLowerCase();assertFalse(s,s.contains("protected"));}
        assertTrue(OwnerChecks.liveTv("VPN ○ Disconnected",false,LiveTvProtection.Decision.VPN_OFF).contains("stays locked"));
        assertTrue(OwnerChecks.liveTv("VPN ● Connected",true,LiveTvProtection.Decision.ALLOW).contains("not a new test"));
    }
    @Test public void advancedDetailsShowTheHostOnly(){
        assertEquals("192.0.2.10",OwnerChecks.host("http://192.0.2.10:50001/desc.xml?token=x"));assertEquals("",OwnerChecks.host("not a url"));
    }
    @Test public void timesUseTheHouseholdClock(){assertEquals("none yet",OwnerChecks.when(0));assertEquals("25 Oct 15:30",OwnerChecks.when(1792942200000L));}
}
