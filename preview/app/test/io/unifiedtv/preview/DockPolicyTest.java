package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class DockPolicyTest {
    // SET-011 (owner, 25 October 2026): a fresh install suggests from what is installed; nothing is placed that isn't.
    @Test public void freshInstallSuggestsOnlyInstalledAppsUpToSix(){
        assertTrue("nothing installed: an empty dock, never placeholders",DockPolicy.suggest("tcl",Collections.emptySet(),false).isEmpty());
        assertTrue(DockPolicy.order("tcl",null).isEmpty());
        assertEquals(Arrays.asList("netflix","com.google.android.youtube.tv"),DockPolicy.suggest("tcl",new HashSet<>(Arrays.asList("com.example.weather","com.google.android.youtube.tv","com.netflix.ninja")),false));
        Set<String> lots=new HashSet<>(Arrays.asList("org.smarttube.beta","com.netflix.ninja","com.disney.disneyplus","bbc.iplayer.android","com.amazon.mp3","com.google.android.youtube.tv","com.spotify.tv.android"));
        assertEquals(Arrays.asList("smartutube","netflix","disney","bbc","live","music"),DockPolicy.suggest("tcl",lots,true));
        assertEquals("no Live TV app: no Live TV tile",Arrays.asList("smartutube","netflix","disney","bbc","music","com.google.android.youtube.tv"),DockPolicy.suggest("tcl",lots,false));
        assertFalse("HDMI shortcuts are never suggested",DockPolicy.suggest("sony",lots,true).contains("xbox"));
        assertEquals(Arrays.asList("netflix"),DockPolicy.order("tcl",null,Arrays.asList("com.netflix.ninja")));
        assertEquals("a saved dock wins over any suggestion",Arrays.asList("music"),DockPolicy.order("tcl","music",Arrays.asList("netflix")));
    }
    @Test public void appsOffersUnifiedTvsOwnShortcutsPerRoom(){
        assertEquals(Arrays.asList("library","live"),DockPolicy.shortcuts("tcl"));assertTrue(DockPolicy.shortcuts("sony").contains("xbox"));assertTrue(DockPolicy.shortcuts("frame").contains("tvplus"));
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
    // Owner, 25 October 2026: Library is a dock entry like any tile, in every room, but not added to existing layouts.
    @Test public void libraryCanBePlacedMovedAndRemoved(){
        assertFalse(DockPolicy.order("tcl",null).contains("library"));
        assertEquals("netflix,library",DockPolicy.add("tcl",Arrays.asList("netflix"),"library"));
        assertEquals(Arrays.asList("library","netflix"),DockPolicy.order("sony","library,netflix,library"));
        assertEquals("netflix",DockPolicy.remove(Arrays.asList("library","netflix"),"library"));
    }
}
