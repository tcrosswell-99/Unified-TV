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
    // v3.4.6 UX-DOCK-08..10, SET-BUILD-04: the dock size is respected and a full dock is never overwritten silently.
    @Test public void addingRespectsTheDockSize(){
        List<String> six=Arrays.asList("netflix","bbc","music","library","live","disney");
        assertNull("six of six: the household must choose a replacement",DockPolicy.add("tcl",six,"com.spotify.tv.android",6));
        assertEquals("netflix,bbc,music,library,live,disney,com.spotify.tv.android",DockPolicy.add("tcl",six,"com.spotify.tv.android",8));
        assertEquals("already on Home: unchanged",String.join(",",six),DockPolicy.add("tcl",six,"com.netflix.ninja",6));
        assertEquals(Arrays.asList("netflix","bbc"),DockPolicy.visible(Arrays.asList("netflix","bbc","music"),2));
    }
    @Test public void replacingTakesThePlaceAndNeverDuplicates(){
        List<String> dock=Arrays.asList("netflix","bbc","music");
        assertEquals("netflix,com.spotify.tv.android,music",DockPolicy.replace("tcl",dock,"bbc","com.spotify.tv.android"));
        assertEquals("moving a tile already on Home","music,bbc",DockPolicy.replace("tcl",dock,"netflix","music"));
        assertEquals("unknown old tile: unchanged","netflix,bbc,music",DockPolicy.replace("tcl",dock,"disney","com.spotify.tv.android"));
        assertEquals("not for this room: unchanged","netflix,bbc,music",DockPolicy.replace("tcl",dock,"bbc","xbox"));
        assertEquals("itself: unchanged","netflix,bbc,music",DockPolicy.replace("tcl",dock,"bbc","bbc"));
    }
    @Test public void placingFillsFreePlacesOnlyAndReportsTheRest(){
        List<String> left=new ArrayList<>();
        assertEquals(Arrays.asList("netflix","bbc","com.spotify.tv.android","music"),DockPolicy.place("tcl",Arrays.asList("netflix","bbc"),Arrays.asList("com.spotify.tv.android","music","netflix"),4,left));
        assertTrue(left.isEmpty());
        List<String> full=DockPolicy.place("tcl",Arrays.asList("netflix","bbc","live","library","disney"),Arrays.asList("com.spotify.tv.android","music"),6,left);
        assertEquals(6,full.size());assertEquals(Arrays.asList("music"),left);
        assertEquals("never auto-filled to eight",Arrays.asList("netflix"),DockPolicy.place("tcl",Arrays.asList("netflix"),Collections.emptyList(),8,null));
        assertEquals(Arrays.asList("bbc","netflix"),DockPolicy.move(Arrays.asList("netflix","bbc"),0,1));
        assertEquals("unchanged at the end",Arrays.asList("netflix","bbc"),DockPolicy.move(Arrays.asList("netflix","bbc"),1,1));
    }
}
