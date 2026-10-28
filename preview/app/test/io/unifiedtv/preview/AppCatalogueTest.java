package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
/** v3.4.5/3.4.6 SET-BUILD-01..05: an honest catalogue, grouped by purpose, with no claimed availability. */
public class AppCatalogueTest {
    private static Map<String,String> apps(String... pairs){Map<String,String> m=new HashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(pairs[i],pairs[i+1]);return m;}
    @Test public void groupedByPurposeMusicFirst(){
        assertEquals(AppCatalogue.MUSIC,AppCatalogue.CATEGORIES.get(0).id);
        Set<String> ids=new HashSet<>();for(AppCatalogue.Category c:AppCatalogue.CATEGORIES)ids.add(c.id);
        assertEquals(new HashSet<>(Arrays.asList("music","video","youtube","live","local","casting","photos","smarthome")),ids);
        for(AppCatalogue.Entry e:AppCatalogue.ENTRIES)assertNotNull(e.id,AppCatalogue.category(e.category));
        for(String m:new String[]{"spotify","applemusic","amazonmusic","youtubemusic"})assertEquals(AppCatalogue.MUSIC,AppCatalogue.entry(m).category);
        for(String v:new String[]{"netflix","disney","primevideo","bbc"})assertEquals(AppCatalogue.VIDEO,AppCatalogue.entry(v).category);
        assertEquals(AppCatalogue.YOUTUBE,AppCatalogue.entry("smarttube").category);assertEquals(AppCatalogue.YOUTUBE,AppCatalogue.entry("youtube").category);
        assertTrue("setup categories have nothing to install",AppCatalogue.entries(AppCatalogue.LOCAL).isEmpty()&&AppCatalogue.entries(AppCatalogue.LIVE).isEmpty());
        assertEquals("video",AppCatalogue.next("music"));assertNull(AppCatalogue.next("smarthome"));
    }
    @Test public void sixHonestStates(){
        AppCatalogue.Entry spotify=AppCatalogue.entry("spotify"),bbc=AppCatalogue.entry("bbc"),smart=AppCatalogue.entry("smarttube");
        Map<String,String> tv=apps("com.spotify.tv.android","Spotify","com.android.tv.settings","Settings");
        assertEquals(AppCatalogue.State.INSTALLED_AND_LAUNCHABLE,AppCatalogue.state(spotify,tv,true,true,"android"));
        assertEquals(AppCatalogue.State.NOT_INSTALLED_STORE_AVAILABLE,AppCatalogue.state(AppCatalogue.entry("netflix"),tv,true,true,"android"));
        assertEquals("no store on this TV",AppCatalogue.State.NOT_INSTALLED_STORE_UNKNOWN,AppCatalogue.state(AppCatalogue.entry("netflix"),tv,true,false,"android"));
        assertEquals("an unverified id never gets a store handoff",AppCatalogue.State.NOT_INSTALLED_STORE_UNKNOWN,AppCatalogue.state(bbc,tv,true,true,"android"));
        assertEquals("SmartTube is not in Google Play",AppCatalogue.State.NOT_INSTALLED_STORE_UNKNOWN,AppCatalogue.state(smart,tv,true,true,"android"));
        assertEquals("SmartTube is not claimed for Tizen",AppCatalogue.State.NOT_AVAILABLE_ON_PLATFORM,AppCatalogue.state(smart,tv,true,true,"tizen"));
        assertEquals("unknown is not absent",AppCatalogue.State.DETECTION_UNAVAILABLE,AppCatalogue.state(spotify,new HashMap<>(),false,true,"android"));
        assertEquals(AppCatalogue.State.NEEDS_CONFIGURATION,AppCatalogue.linkState(false));assertEquals(AppCatalogue.State.INSTALLED_AND_LAUNCHABLE,AppCatalogue.linkState(true));
        assertFalse(AppCatalogue.reliable(true,40));assertFalse(AppCatalogue.reliable(false,0));assertTrue(AppCatalogue.reliable(false,3));
        Set<String> ids=new HashSet<>();for(AppCatalogue.State s:AppCatalogue.State.values())ids.add(s.id);
        assertEquals(new HashSet<>(Arrays.asList("installed_and_launchable","not_installed_store_available","not_installed_store_unknown","not_available_on_platform","needs_configuration","detection_unavailable")),ids);
    }
    @Test public void detectionByPackageOrInstalledNameAndDockIds(){
        Map<String,String> tv=apps("com.netflix.ninja","Netflix","com.example.airpinlite","AirPin LITE","com.amazon.mp3","Amazon Music","com.spotify.tv.android","Spotify");
        assertEquals(Arrays.asList("spotify","amazonmusic"),AppCatalogue.detected("music",tv));
        assertEquals("com.example.airpinlite",AppCatalogue.installedPackage(AppCatalogue.entry("airpin"),tv));
        assertEquals("netflix",AppCatalogue.dockId(AppCatalogue.entry("netflix"),tv));
        assertEquals("Amazon Music takes Unified TV's own Music tile","music",AppCatalogue.dockId(AppCatalogue.entry("amazonmusic"),tv));
        assertEquals("com.spotify.tv.android",AppCatalogue.dockId(AppCatalogue.entry("spotify"),tv));
        assertNull("not installed: no dock id, never placed",AppCatalogue.dockId(AppCatalogue.entry("disney"),tv));
    }
    @Test public void storeHandoffOnlyForTrustedListingsMarketFirst(){
        assertArrayEquals(new String[]{"market://details?id=com.spotify.tv.android","https://play.google.com/store/apps/details?id=com.spotify.tv.android"},AppCatalogue.storeUris(AppCatalogue.entry("spotify")));
        for(AppCatalogue.Entry e:AppCatalogue.ENTRIES)if(!e.verified)assertNull(e.id+" is unverified: no store page",AppCatalogue.storeUris(e));
        assertNull(AppCatalogue.storeUris(AppCatalogue.entry("smarttube")));
        assertTrue("sign-in is stated, never collected",AppCatalogue.describe(AppCatalogue.entry("netflix"),AppCatalogue.State.INSTALLED_AND_LAUNCHABLE).contains("sign-in, in its own app"));
        for(AppCatalogue.Entry e:AppCatalogue.ENTRIES)for(String p:e.packages)assertTrue(p,p.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"));
    }
    // Owner, 25 October 2026: SmartTube is the household's YouTube, so its tile is named YouTube and draws the YouTube symbol.
    @Test public void smartTubeIsShownAsYouTube(){
        assertEquals("play",DockLabels.glyph("smartutube"));
        for(String p:DockPolicy.packages("smartutube")){assertEquals(p,"play",DockLabels.glyph(p));assertEquals(p,"YouTube",DockLabels.name(p));}
        assertEquals(DockLabels.glyph("smartutube"),DockLabels.glyph("com.google.android.youtube.tv"));
        assertEquals("YouTube",DockLabels.name("smartutube"));assertNull("an ordinary app keeps its own name",DockLabels.name("com.netflix.ninja"));
        assertTrue("Build My TV still says which YouTube it is",AppCatalogue.entry("smarttube").name.contains("YouTube")&&AppCatalogue.entry("smarttube").name.contains("SmartTube"));
        assertEquals("detection is unchanged",DockPolicy.packages("smartutube").length,AppCatalogue.entry("smarttube").packages.length);
        assertNotEquals("two music apps on Home look different",DockLabels.glyph("music"),DockLabels.glyph("com.spotify.tv.android"));
        for(AppCatalogue.Category c:AppCatalogue.CATEGORIES)assertTrue(c.glyph,DockLabels.GLYPHS.contains(c.glyph));
        for(AppCatalogue.Entry e:AppCatalogue.ENTRIES)for(String p:e.packages)assertTrue(p,DockLabels.GLYPHS.contains(DockLabels.glyph(p)));
    }
}
