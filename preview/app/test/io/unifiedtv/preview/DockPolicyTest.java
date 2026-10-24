package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class DockPolicyTest {
    @Test public void freshInstallGetsTheCuratedDefaults(){
        assertEquals(Arrays.asList("smartutube","netflix","disney","bbc","live","music"),DockPolicy.order("tcl",null));
        assertEquals(7,DockPolicy.order("sony",null).size());assertTrue(DockPolicy.order("sony",null).contains("xbox"));
        assertEquals(7,DockPolicy.order("frame",null).size());assertTrue(DockPolicy.order("frame",null).contains("tvplus"));
    }
    @Test public void ownerEditsSurviveIncludingRemovalsAndAddedApps(){
        assertEquals(Arrays.asList("com.example.weather","netflix"),DockPolicy.order("tcl","com.example.weather,netflix"));
        assertTrue("a deliberately empty Home stays empty",DockPolicy.order("tcl","").isEmpty());
    }
    @Test public void noDuplicatesAndACuratedAppsPackageMapsToItsTile(){
        assertEquals(Arrays.asList("netflix","bbc"),DockPolicy.order("tcl","netflix,com.netflix.ninja,bbc,bbc"));
        assertEquals("netflix,bbc",DockPolicy.add("tcl",Arrays.asList("netflix","bbc"),"com.netflix.ninja"));
        assertEquals("bbc",DockPolicy.remove(Arrays.asList("netflix","bbc"),"com.netflix.ninja"));
    }
    @Test public void platformChoicesCannotLeakAcrossRooms(){
        assertFalse(DockPolicy.order("tcl","xbox,tvplus").contains("xbox"));
        assertFalse(DockPolicy.order("sony","tvplus").contains("tvplus"));
        assertEquals("netflix",DockPolicy.add("tcl",Arrays.asList("netflix"),"xbox"));
    }
    @Test public void junkNeverSurvives(){assertEquals(Arrays.asList("music"),DockPolicy.order("tcl","not a package,../x,,music"));}
    @Test public void allChoicesSurviveReordering(){
        assertEquals(Arrays.asList("music","live","bbc","disney","netflix","smartutube"),DockPolicy.order("tcl","music,live,bbc,disney,netflix,smartutube"));
    }
}
