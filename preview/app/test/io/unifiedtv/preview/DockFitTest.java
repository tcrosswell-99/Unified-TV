package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/**
 * The owner's Sony BRAVIA (Android TV 10, run 106): two YouTube tiles that looked the same, a sixth tile with a generic
 * play-circle (Prime Video), and the dock running off the right edge of the screen. Pure rules: names and marks
 * (DockLabels, OD-20), fresh docks without lookalikes (DockPolicy), the dock always inside the screen (DockCapacity.fit)
 * and the app picker's list and placement (DockPicker).
 */
public class DockFitTest {
    private static final String SMARTTUBE="org.smarttube.beta",YOUTUBE="com.google.android.youtube.tv";

    // --- 1. SmartTube beside YouTube
    @Test public void smartTubeAndYouTubeBothInstalledLookAndReadDifferently(){
        boolean yt=DockLabels.youtube(Arrays.asList(SMARTTUBE,YOUTUBE));assertTrue(yt);
        assertEquals("SmartTube",DockLabels.name("smartutube",yt));assertEquals("SmartTube",DockLabels.name(SMARTTUBE,yt));
        assertNull("YouTube keeps its own installed name",DockLabels.name(YOUTUBE,yt));
        assertNotEquals("two distinct marks",DockLabels.glyph("smartutube",yt),DockLabels.glyph(YOUTUBE,yt));
        assertEquals("play",DockLabels.glyph(YOUTUBE,yt));assertEquals("smarttube",DockLabels.glyph(SMARTTUBE,yt));
        assertTrue(DockLabels.GLYPHS.contains("smarttube"));
        assertTrue("the phone build of YouTube counts too",DockLabels.youtube(Collections.singleton("com.google.android.youtube")));
    }
    @Test public void onlySmartTubeInstalledIsTheHouseholdsYouTube(){
        boolean yt=DockLabels.youtube(Collections.singleton(SMARTTUBE));assertFalse(yt);
        assertEquals("YouTube",DockLabels.name("smartutube",yt));assertEquals("play",DockLabels.glyph("smartutube",yt));
    }
    @Test public void aFreshDockNeverHoldsTwoAppsThatLookTheSame(){
        // The owner's Sony: SmartTube, YouTube, Netflix, Disney+, Amazon Music and Prime Video installed.
        List<String> sony=Arrays.asList(SMARTTUBE,YOUTUBE,"com.netflix.ninja","com.disney.disneyplus","com.amazon.music.tv","com.amazon.amazonvideo.livingroom","com.google.android.youtube.tvkids");
        List<String> dock=DockPolicy.suggest("sony",sony,false);
        boolean yt=DockLabels.youtube(sony);Set<String> looks=new HashSet<>();
        for(String id:dock){String g=DockLabels.glyph(id,yt);if(!"mono".equals(g))assertTrue("no lookalike: "+dock,looks.add(g));}
        assertTrue(dock.contains("smartutube"));assertTrue(dock.contains(YOUTUBE));
        assertFalse("YouTube Kids shares YouTube's mark, so it isn't suggested beside it",dock.contains("com.google.android.youtube.tvkids"));
        // Only SmartTube: it is YouTube, so YouTube Kids (the same mark) stays off too.
        assertFalse(DockPolicy.suggest("sony",Arrays.asList(SMARTTUBE,"com.google.android.youtube.tvkids"),false).contains("com.google.android.youtube.tvkids"));
    }

    // --- 3. The sixth tile: brand marks, Live TV, unknown apps
    @Test public void primeVideoAndAppleTvHaveWhiteMarksAndLiveTvIsATvSet(){
        assertEquals("Amazon's TV package gets the Prime Video mark","prime",DockLabels.glyph("com.amazon.amazonvideo.livingroom"));
        assertEquals("appletv",DockLabels.glyph("com.apple.atve.androidtv.appletv"));
        assertEquals("tvset",DockLabels.glyph("live"));
        for(String g:new String[]{"prime","appletv","plus"})assertTrue(g,DockLabels.GLYPHS.contains(g));
        assertNotEquals("Prime Video is no longer the generic play-circle","playcircle",DockLabels.glyph("com.amazon.amazonvideo.livingroom"));
    }
    @Test public void liveTvOnASonyLikeTvIsTheTvSetTile(){
        // A Sony-like set: the tuner app, YouTube, Netflix and the household's IPTV app; Live TV recognised (liveTv true).
        List<String> pkgs=Arrays.asList("com.sony.dtv.tvx",YOUTUBE,"com.netflix.ninja","com.example.reamsmartz");
        List<String> dock=DockPolicy.suggest("sony",pkgs,true);
        assertTrue(dock.toString(),dock.contains("live"));assertEquals("tvset",DockLabels.glyph("live",DockLabels.youtube(pkgs)));
        assertEquals("Live TV",DockRepair.name("live",new HashMap<>()));
        assertTrue("the tuner app is never suggested as a tile of its own",!dock.contains("com.sony.dtv.tvx"));
    }
    @Test public void anUnknownAppHasNoMarkSoItShowsItsOwnArtOrName(){
        assertEquals("mono",DockLabels.glyph("com.example.someapp"));
        assertEquals("mono",DockLabels.glyph("com.example.reamsmartz"));
    }

    // --- 2. The dock fits the screen
    /** Every tested configuration: the real px each place lands on is inside the screen with the 5 % margin either side. */
    private static void fits(String what,int capacity,float w,float h,float visL,float visR){
        DockCapacity.Fit f=DockCapacity.fit(capacity,w,h,visL,visR);float sx=w/1920f,sy=h/1080f,margin=(visR-visL)*DockCapacity.SAFE/1920f;
        float left=f.left*sx,right=(f.left+f.width)*sx;
        assertTrue(what+": left "+left+" >= "+(visL+margin),left>=visL+margin-.5f);
        assertTrue(what+": right "+right+" <= "+(visR-margin),right<=visR-margin+.5f);
        assertEquals(what+": centred on what is seen",(visL+visR)/2,(left+right)/2,.5f);
        assertEquals(what+": square tiles on the glass",f.tileW*sx,f.tileH*sy,.5f);
        for(int i=1;i<capacity;i++)assertTrue(what+": no overlap",f.slot(i)>=f.slot(i-1)+f.tileW-.01f);
        assertTrue(what+": last place inside the glass",f.slot(capacity-1)+f.tileW<=f.left+f.width+.01f);
        assertTrue(what+": labels never collide",f.label(false)<=f.step);
        assertTrue(what+": never larger than the design",f.ky<=DockCapacity.scale(capacity)+1e-4f);
    }
    @Test public void theDockFitsAtEveryResolutionDensityAndCapacity(){
        // px sizes: 1920x1080 at mdpi or xhdpi (the Sony usually reports 1920x1080 at 320 dpi), 1280x720, 3840x2160.
        float[][] screens={{1920,1080},{1280,720},{3840,2160},{1920,1080}};
        for(int cap:DockCapacity.VALUES)for(float[] s:screens)fits(cap+" places on "+(int)s[0]+"x"+(int)s[1],cap,s[0],s[1],0,s[0]);
        // 16:9: exactly the approved geometry, so nothing moves on the TCL.
        DockCapacity.Fit six=DockCapacity.fit(6,1920,1080,0,1920);
        assertEquals(DockCapacity.width(6),six.width,.01f);assertEquals(DockCapacity.slotLeft(6,0,1920),six.slot(0),.01f);
        DockCapacity.Fit eight=DockCapacity.fit(8,3840,2160,0,3840);assertEquals(DockCapacity.slotLeft(8,7,1920),eight.slot(7),.01f);
    }
    @Test public void theDockFitsWhenTheViewIsNotTheScreen(){
        for(int cap:DockCapacity.VALUES){
            fits("a 4:3 view",cap,1440,1080,0,1440);
            fits("a narrow portrait-ish view",cap,1080,1920,0,1080);
            fits("an ultra-wide view",cap,2560,1080,0,2560);
            // A view wider than the screen (overscan, or a window larger than the display): centred on the part seen.
            fits("a view 20 % wider than the screen",cap,2304,1080,0,1920);
            fits("a view offset to the right",cap,1920,1080,192,1920);
        }
        float[] v=DockCapacity.visible(0,2304,0,1920);assertEquals(0,v[0],0);assertEquals(1920,v[1],0);
        v=DockCapacity.visible(-100,1920,0,1920);assertEquals(100,v[0],0);assertEquals(1920,v[1],0);
        v=DockCapacity.visible(0,1920,0,0);assertEquals("unknown: the whole view",1920,v[1],0);
        v=DockCapacity.visible(0,1920,0,500);assertEquals("implausible: the whole view",1920,v[1],0);
    }
    @Test public void eightThatCannotFitShrinkUniformly(){
        DockCapacity.Fit f=DockCapacity.fit(8,1080,1920,0,1080);
        assertTrue("smaller than the eight-place design",f.ky<DockCapacity.SCALE_8);assertEquals("uniformly: still square",f.tileW*(1080/1920f),f.tileH*(1920/1080f),.5f);
    }

    // --- The app picker's rules
    private static Map<String,String> apps(){
        Map<String,String> m=new LinkedHashMap<>();
        m.put("com.example.weather","Weather");m.put("com.netflix.ninja","Netflix");m.put("com.amazon.amazonvideo.livingroom","Prime Video");
        m.put("com.example.reamsmartz","ReamSmartz");m.put("com.disney.disneyplus","Disney+");m.put("com.sony.select","Sony Select");m.put(SMARTTUBE,"SmartTube");m.put(YOUTUBE,"YouTube");
        return m;
    }
    private static List<String> names(List<DockPicker.Choice> cs){List<String> out=new ArrayList<>();for(DockPicker.Choice c:cs)out.add(c.name);return out;}
    @Test public void thePickerOffersAppsNotOnHomeStreamingFirst(){
        List<String> onHome=Arrays.asList("netflix","disney","music");
        List<String> offered=names(DockPicker.choices("sony",apps(),onHome,""));
        assertFalse("Netflix is on Home already",offered.contains("Netflix"));assertFalse(offered.contains("Disney+"));
        assertEquals("streaming first, in the dock's order",Arrays.asList("SmartTube","YouTube","Prime Video","ReamSmartz","Sony Select","Weather"),offered);
        assertEquals("search narrows it",Arrays.asList("Prime Video"),names(DockPicker.choices("sony",apps(),onHome,"prime")));
        assertEquals("by package too",Arrays.asList("Weather"),names(DockPicker.choices("sony",apps(),onHome,"example.weather")));
        assertTrue(DockPicker.choices("sony",apps(),onHome,"nothing like it").isEmpty());
    }
    @Test public void aChoiceFillsExactlyThatPlace(){
        List<String> dock=Arrays.asList("smartutube","netflix","disney","music","com.amazon.amazonvideo.livingroom");
        assertEquals("the sixth place",Arrays.asList("smartutube","netflix","disney","music","com.amazon.amazonvideo.livingroom","com.sony.select"),DockPicker.put("sony",dock,5,"com.sony.select",6));
        assertNull("no seventh place with six",DockPicker.put("sony",dock,6,"com.sony.select",6));
        assertNull("places fill from the left: not past the first free one",DockPicker.put("sony",dock,6,"com.sony.select",8));
        assertEquals("Replace: the tile's own place",Arrays.asList("smartutube","netflix","com.sony.select","music","com.amazon.amazonvideo.livingroom"),DockPicker.put("sony",dock,2,"com.sony.select",6));
        assertEquals("a known app by its package keeps its tile id","netflix",DockPicker.put("sony",Arrays.asList("music"),1,"com.netflix.ninja",6).get(1));
        assertNull("room-only tiles stay in their room",DockPicker.put("tcl",dock,5,"xbox",6));
    }
    @Test public void theLiveTvListPutsLikelyLiveTvAppsFirst(){
        List<String> offered=names(DockPicker.liveChoices(apps(),""));
        assertEquals("ReamSmartz",offered.get(0));assertEquals(apps().size(),offered.size());
        assertTrue(DockPicker.liveLike("com.example.reamsmartz","ReamSmartz"));assertFalse(DockPicker.liveLike("com.example.weather","Weather"));
    }
}
