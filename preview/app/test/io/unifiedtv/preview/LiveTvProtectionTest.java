package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import static io.unifiedtv.preview.LiveTvProtection.Decision.*;
public class LiveTvProtectionTest {
    static final String LIVE="tv.example.live";
    static JSONObject checked()throws Exception{return LiveTvProtection.record(LIVE,"3.1","9.15.2-tv",1000);}
    @Test public void vpnAppearanceAloneNeverOpensLiveTv(){
        assertEquals(NOT_CHECKED,LiveTvProtection.decide(null,true,true,"9.15.2-tv",LIVE,"3.1"));
        assertEquals(NOT_CHECKED,LiveTvProtection.decide(new JSONObject(),true,true,"9.15.2-tv",LIVE,"3.1"));
    }
    @Test public void aPassedCheckOpensItOnlyWhileTheVpnCoversThisTv()throws Exception{
        assertEquals(ALLOW,LiveTvProtection.decide(checked(),true,true,"9.15.2-tv",LIVE,"3.1"));
        assertEquals(VPN_OFF,LiveTvProtection.decide(checked(),false,false,"9.15.2-tv",LIVE,"3.1"));
        assertEquals(NOT_COVERED,LiveTvProtection.decide(checked(),true,false,"9.15.2-tv",LIVE,"3.1"));
    }
    @Test public void anUpdateOrADifferentLiveAppNeedsANewCheck()throws Exception{
        assertEquals(CHANGED,LiveTvProtection.decide(checked(),true,true,"9.16.0-tv",LIVE,"3.1"));
        assertEquals(CHANGED,LiveTvProtection.decide(checked(),true,true,"9.15.2-tv",LIVE,"3.2"));
        assertEquals(CHANGED,LiveTvProtection.decide(checked(),true,true,"9.15.2-tv","tv.other.live","3.1"));
        assertEquals("NordVPN uninstalled",CHANGED,LiveTvProtection.decide(checked(),true,true,"",LIVE,"3.1"));
    }
    @Test public void aCheckCannotBeRecordedWithoutBothApps(){
        assertThrows(IllegalArgumentException.class,()->LiveTvProtection.record("","3.1","9.15.2-tv",1));
        assertThrows(IllegalArgumentException.class,()->LiveTvProtection.record(LIVE,"3.1","",1));
    }
    @Test public void everyLockedStateExplainsItselfPlainly(){
        for(LiveTvProtection.Decision d:LiveTvProtection.Decision.values()){if(d==ALLOW)continue;String s=LiveTvProtection.message(d);assertFalse(s,s.isEmpty());
            for(String jargon:new String[]{"tunnel","routing","lockdown","uid","socket"})assertFalse(s,s.toLowerCase().contains(jargon));}
    }
}
