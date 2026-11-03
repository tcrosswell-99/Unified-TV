package io.unifiedtv.preview;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class KidsGuardTest {
    static final String SELF="io.unifiedtv.launcher";
    static final Set<String> APPROVED=new HashSet<>(Collections.singletonList("com.example.kidsapp"));
    static int kids(String pkg,boolean pass){return KidsGuard.decide(pkg,SELF,true,true,APPROVED,pass,"com.example.keyboard");}
    @Test public void doesNothingInAdultsOrWhileSwitchedOff(){
        assertEquals(KidsGuard.ALLOW,KidsGuard.decide("com.example.other",SELF,false,true,APPROVED,false,""));
        assertEquals(KidsGuard.ALLOW,KidsGuard.decide("com.example.other",SELF,true,false,APPROVED,false,""));
    }
    @Test public void aNonApprovedAppIsSentBackToHome(){
        assertEquals(KidsGuard.RETURN,kids("com.example.other",false));
        assertEquals("the TV's own Home lists every app",KidsGuard.RETURN,kids("com.google.android.apps.tv.launcherx",false));
        assertEquals("the sibling Unified TV app is another app",KidsGuard.RETURN,kids("io.unifiedtv.preview",false));
    }
    @Test public void approvedAppsUnifiedTvAndTheTvsOwnEssentialsStay(){
        assertEquals(KidsGuard.ALLOW,kids("com.example.kidsapp",false));
        assertEquals(KidsGuard.ALLOW,kids(SELF,false));
        for(String p:new String[]{"com.android.systemui","android","com.google.android.permissioncontroller","com.example.keyboard","com.mediatek.wwtv.tvcenter","com.google.android.katniss"})assertEquals(p,KidsGuard.ALLOW,kids(p,false));
        assertEquals("an unknown event is never acted on",KidsGuard.ALLOW,kids(null,false));
    }
    @Test public void systemSettingsOnlyWithTheAdultsPass(){
        assertEquals(KidsGuard.RETURN,kids("com.android.tv.settings",false));
        assertEquals(KidsGuard.ALLOW,kids("com.android.tv.settings",true));
        assertTrue(KidsGuard.pass(70_000,10_000));assertFalse("expired",KidsGuard.pass(10_000,10_001));
        assertFalse("a pass longer than ten minutes is not one Unified TV gave",KidsGuard.pass(10_000+KidsGuard.PASS_MS+1,10_000));
    }
    @Test public void keyboardAndServiceListAreReadFromAndroidsOwnSettings(){
        assertEquals("com.example.keyboard",KidsGuard.keyboard("com.example.keyboard/.Ime"));assertEquals("",KidsGuard.keyboard(null));assertEquals("",KidsGuard.keyboard("broken"));
        String cls="io.unifiedtv.preview.KidsGuardService";
        assertTrue(KidsGuard.enabledIn("com.example.other/.Svc:io.unifiedtv.preview/.KidsGuardService","io.unifiedtv.preview",cls));
        assertTrue(KidsGuard.enabledIn("io.unifiedtv.launcher/io.unifiedtv.preview.KidsGuardService","io.unifiedtv.launcher",cls));
        assertFalse(KidsGuard.enabledIn("io.unifiedtv.preview/.KidsGuardService","io.unifiedtv.launcher",cls));
        assertFalse(KidsGuard.enabledIn("",SELF,cls));assertFalse(KidsGuard.enabledIn(null,SELF,cls));
    }
    @Test public void theComputerStepAddsToTheListInsteadOfReplacingIt(){
        String line=KidsGuard.adbLine("io.unifiedtv.preview/.KidsGuardService");
        assertTrue(line.contains("settings get secure enabled_accessibility_services"));assertTrue(line.contains("\"$cur:io.unifiedtv.preview/.KidsGuardService\""));
        assertFalse("never a bare put that wipes other services",line.contains("enabled_accessibility_services io.unifiedtv"));
    }
    @Test public void decisionLogIsBoundedNewestFirst()throws Exception{
        String log="";for(int i=0;i<12;i++)log=KidsGuard.log(log,"com.example.app"+i,KidsGuard.RETURN,1000+i);
        JSONArray a=new JSONArray(log);assertEquals(8,a.length());assertEquals("com.example.app11",a.getJSONObject(0).getString("pkg"));
        assertEquals(1,new JSONArray(KidsGuard.log("{broken","com.example.app",KidsGuard.RETURN,1)).length());
    }
}
