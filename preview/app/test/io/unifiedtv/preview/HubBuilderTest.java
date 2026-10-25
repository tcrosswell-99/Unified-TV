package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
/** v3.4.5/3.4.6 SET-BUILD-02, -04, -06, -09, -10: music first, detect before asking, versioned resumable progress. */
public class HubBuilderTest {
    @Test public void musicDetectsBeforeAsking(){
        assertEquals("exactly one: chosen without asking",HubBuilder.Music.AUTO_DEFAULT,HubBuilder.music(Arrays.asList("spotify"),true,""));
        assertEquals("two or more: the household chooses",HubBuilder.Music.CHOOSE,HubBuilder.music(Arrays.asList("spotify","amazonmusic"),true,""));
        assertEquals(HubBuilder.Music.CHOOSE,HubBuilder.music(Arrays.asList("spotify","amazonmusic","tidal"),true,""));
        assertEquals("none found reliably: ask Yes/No",HubBuilder.Music.ASK,HubBuilder.music(Collections.emptyList(),true,""));
        assertEquals("unknown is not absent: a manual choice",HubBuilder.Music.MANUAL,HubBuilder.music(Collections.emptyList(),false,""));
        assertEquals("a saved, still-installed choice: no prompt again",HubBuilder.Music.KEEP,HubBuilder.music(Arrays.asList("spotify","amazonmusic"),true,"amazonmusic"));
        assertEquals("the chosen app was removed: ask again",HubBuilder.Music.AUTO_DEFAULT,HubBuilder.music(Arrays.asList("spotify"),true,"amazonmusic"));
    }
    @Test public void oneMusicAppBecomesTheDefaultButIsNotForcedOntoTheDock(){
        HubBuilder.State s=new HubBuilder.State();
        assertTrue(HubBuilder.autoMusic(s,Arrays.asList("spotify"),true));
        assertEquals("spotify",s.preferred("music"));assertFalse(s.onDock("spotify"));
        assertTrue(HubBuilder.wantedOnDock(s,e->"com.spotify.tv.android").isEmpty());
        assertFalse("and is not re-asked or re-applied",HubBuilder.autoMusic(s,Arrays.asList("spotify"),true));
        HubBuilder.State two=new HubBuilder.State();assertFalse("never silently picks among several",HubBuilder.autoMusic(two,Arrays.asList("spotify","tidal"),true));assertEquals("",two.preferred("music"));
    }
    @Test public void severalProvidersOneDefaultAndPerServicePlacement(){
        HubBuilder.State s=new HubBuilder.State();
        HubBuilder.select(s,"spotify",true);HubBuilder.select(s,"amazonmusic",true);
        assertEquals("the first chosen is the default until changed","spotify",s.preferred("music"));
        HubBuilder.prefer(s,"amazonmusic");assertEquals("amazonmusic",s.preferred("music"));
        HubBuilder.place(s,"spotify",true);HubBuilder.place(s,"amazonmusic",true);
        Map<String,String> ids=new HashMap<>();ids.put("spotify","com.spotify.tv.android");ids.put("amazonmusic","music");
        assertEquals("both as separate tiles, the default first",Arrays.asList("music","com.spotify.tv.android"),HubBuilder.wantedOnDock(s,ids::get));
        HubBuilder.select(s,"amazonmusic",false);assertEquals("the default moves to the one left","spotify",s.preferred("music"));assertFalse(s.dockAssignments.containsKey("amazonmusic"));
        HubBuilder.select(s,"spotify",false);assertEquals("",s.preferred("music"));
        HubBuilder.select(s,"netflix",true);HubBuilder.place(s,"netflix",true);HubBuilder.place(s,HubBuilder.LIVE_TILE,true);HubBuilder.place(s,HubBuilder.LIBRARY_TILE,true);
        Map<String,String> more=new HashMap<>();more.put("netflix","netflix");
        assertEquals(Arrays.asList("netflix","library","live"),HubBuilder.wantedOnDock(s,more::get));
        HubBuilder.select(s,"disney",true);HubBuilder.place(s,"disney",true);
        assertFalse("not installed: never placed",HubBuilder.wantedOnDock(s,more::get).contains(null));
    }
    @Test public void yesNoAndLaterAreIndependent(){
        HubBuilder.State s=new HubBuilder.State();
        HubBuilder.select(s,"netflix",true);HubBuilder.answer(s,"video",false);
        assertTrue(s.declinedCategories.contains("video"));assertTrue("No clears that category's choices",s.selected("video").isEmpty());
        HubBuilder.answer(s,"video",false);assertEquals(1,s.declinedCategories.size());
        HubBuilder.answer(s,"music",true);assertTrue(s.selectedCategories.contains("music"));assertEquals(HubBuilder.IN_PROGRESS,s.status);
        HubBuilder.answer(s,"nonsense",true);assertFalse(s.selectedCategories.contains("nonsense"));
    }
    @Test public void progressIsVersionedResumableAndSurvivesARoundTrip(){
        HubBuilder.State s=new HubBuilder.State();assertEquals(HubBuilder.NOT_STARTED,s.status);
        HubBuilder.go(s,"music");assertEquals(HubBuilder.IN_PROGRESS,s.status);assertEquals("music",s.stepId);
        HubBuilder.go(s,HubBuilder.OVERVIEW);assertEquals("looking at the overview keeps the step to resume","music",s.stepId);
        HubBuilder.select(s,"spotify",true);HubBuilder.select(s,"tidal",true);HubBuilder.prefer(s,"tidal");HubBuilder.place(s,"spotify",true);s.pendingInstall="netflix";
        HubBuilder.State back=HubBuilder.parse(s.toJson());
        assertEquals(1,back.schemaVersion);assertEquals(HubBuilder.IN_PROGRESS,back.status);assertEquals("music",back.stepId);
        assertEquals(Arrays.asList("spotify","tidal"),back.selected("music"));assertEquals("tidal",back.preferred("music"));assertTrue(back.onDock("spotify"));
        assertEquals("netflix",back.pendingInstall);assertEquals(Arrays.asList("music"),back.selectedCategories);
        assertFalse("identifiers and choices only",s.toJson().toLowerCase(Locale.ROOT).matches(".*(password|token|credential).*"));
    }
    @Test public void malformedOrForeignValuesFallBackSafely(){
        assertEquals(HubBuilder.NOT_STARTED,HubBuilder.parse(null).status);assertEquals(HubBuilder.NOT_STARTED,HubBuilder.parse("not json").status);
        HubBuilder.State s=HubBuilder.parse("{\"schemaVersion\":1,\"status\":\"hacked\",\"stepId\":\"../x\",\"pendingInstall\":\"evil\",\"providerSelections\":{\"music\":[\"netflix\",\"spotify\",\"spotify\"],\"bogus\":[\"x\"]},"
            +"\"preferredProviderByCategory\":{\"music\":\"tidal\"},\"dockAssignments\":{\"spotify\":\"dock\",\"x y\":\"dock\",\"tidal\":\"roof\"}}");
        assertEquals(HubBuilder.NOT_STARTED,s.status);assertEquals(HubBuilder.OVERVIEW,s.stepId);assertEquals("",s.pendingInstall);
        assertEquals("only music entries in Music, once",Arrays.asList("spotify"),s.selected("music"));
        assertEquals("a default must be one of the chosen","",s.preferred("music"));
        assertEquals(Collections.singletonMap("spotify","dock"),s.dockAssignments);
    }
    @Test public void startOverTouchesOnlyTheWizard(){
        HubBuilder.State s=HubBuilder.startOver();assertEquals(HubBuilder.IN_PROGRESS,s.status);assertEquals(HubBuilder.OVERVIEW,s.stepId);assertTrue(s.providerSelections.isEmpty());
        assertNotEquals(HubBuilder.key("adult"),HubBuilder.key("child"));assertEquals("setup.hubBuilder.adult",HubBuilder.key("x"));
    }
    @Test public void savedPerProfileAndOfferedOnceToAFreshAdultsHome(){
        MapPrefs p=new MapPrefs();assertTrue(HubBuilder.shouldOffer(p));
        HubBuilder.State s=HubBuilder.load(p,"adult");HubBuilder.select(s,"spotify",true);HubBuilder.save(p,"adult",s);
        assertEquals(Arrays.asList("spotify"),HubBuilder.load(p,"adult").selected("music"));assertTrue("Kids has its own choices",HubBuilder.load(p,"child").selected("music").isEmpty());
        Map<String,String> tv=new HashMap<>();tv.put("com.spotify.tv.android","Spotify");
        assertEquals("com.spotify.tv.android",HubBuilder.musicPackage(p,"adult",tv));assertNull(HubBuilder.musicPackage(p,"child",tv));
        assertNull("uninstalled: the Music tile falls back",HubBuilder.musicPackage(p,"adult",new HashMap<>()));
        HubBuilder.markOffered(p);assertFalse("once only",HubBuilder.shouldOffer(p));
        MapPrefs earlier=new MapPrefs();earlier.edit().putString("dock","netflix").apply();assertFalse("an earlier install isn't interrupted",HubBuilder.shouldOffer(earlier));
        MapPrefs kids=new MapPrefs();kids.edit().putString(Profiles.PREF,Profiles.CHILD).apply();assertFalse("never in Kids",HubBuilder.shouldOffer(kids));
        MapPrefs started=new MapPrefs();HubBuilder.State st=new HubBuilder.State();HubBuilder.go(st,"music");HubBuilder.save(started,"adult",st);assertFalse(HubBuilder.shouldOffer(started));
    }
}
