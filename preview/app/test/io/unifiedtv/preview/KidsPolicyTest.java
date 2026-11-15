package io.unifiedtv.preview;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class KidsPolicyTest {
    static CatalogueItem item(String id,String parent,String root,String... ratings){return new CatalogueItem("dlna","uuid:nas",id,"Title","",CatalogueItem.Kind.VIDEO,parent,root,Arrays.asList(ratings),"");}
    @Test public void theOwnersRatingTableIsAllowed(){
        for(String r:new String[]{"U","Uc","PG","G","TV-Y","TV-Y7","TV-Y7-FV","TV-G","TV-PG"})assertEquals(r,KidsPolicy.Rating.ALLOWED,KidsPolicy.rating(r));
    }
    @Test public void anythingHigherOrUnknownIsRefused(){
        for(String r:new String[]{"12","12A","15","18","R18","PG-13","R","NC-17","TV-14","TV-MA","NR","Unrated","Not Rated","4","8.5","A","U/A","X"})assertEquals(r,KidsPolicy.Rating.REFUSED,KidsPolicy.rating(r));
    }
    @Test public void prefixesSpacingAndDashesAreReadTheSameWay(){
        for(String r:new String[]{"GB-PG","UK:U","US-G","BBFC U","MPAA PG","Rated PG","tv-y7","TV PG","TVPG","TV–G","TV-Y7 FV"," pg "})assertEquals(r,KidsPolicy.Rating.ALLOWED,KidsPolicy.rating(r));
        for(String r:new String[]{"GB-15","US-PG-13","Rated R","TV 14","UK:12A"})assertEquals(r,KidsPolicy.Rating.REFUSED,KidsPolicy.rating(r));
    }
    @Test public void noRatingIsNoRatingAndAnyRefusedRatingWins(){
        assertEquals(KidsPolicy.Rating.NONE,KidsPolicy.rating((String)null));assertEquals(KidsPolicy.Rating.NONE,KidsPolicy.rating("  "));
        assertEquals(KidsPolicy.Rating.NONE,KidsPolicy.rating(Collections.<String>emptyList()));
        assertEquals(KidsPolicy.Rating.REFUSED,KidsPolicy.rating(Arrays.asList("PG","15")));
        assertEquals(KidsPolicy.Rating.ALLOWED,KidsPolicy.rating(Arrays.asList("U","TV-G")));
    }
    @Test public void browsingStaysInsideKidsFoldersAndUnratedIsOnlyAllowedThere(){
        Set<String> roots=new HashSet<>(Arrays.asList("kids-1"));
        assertTrue(KidsPolicy.allows(item("v1","kids-1","kids-1"),roots));
        assertTrue(KidsPolicy.allows(item("v2","kids-1","kids-1","PG"),roots));
        assertFalse("rated above Kids",KidsPolicy.allows(item("v3","kids-1","kids-1","15"),roots));
        assertFalse("unrated outside a Kids folder",KidsPolicy.allows(item("v4","films","films"),roots));
        assertFalse("even rated U, outside a Kids folder",KidsPolicy.allows(item("v5","films","films","U"),roots));
        assertFalse(KidsPolicy.allows(null,roots));assertFalse(KidsPolicy.allows(item("v1","kids-1","kids-1"),Collections.<String>emptySet()));
    }
    @Test public void noKidsFoldersMeansNotReady()throws Exception{
        assertFalse(KidsPolicy.ready(new JSONObject()));
        JSONObject r=new JSONObject().put("enabled",true).put("control","http://nas.invalid/ctl").put("roots",new JSONArray());assertFalse(KidsPolicy.ready(r));
        r.put("roots",new JSONArray().put(new JSONObject().put("id","64$1").put("name","Kids")));assertTrue(KidsPolicy.ready(r));assertEquals(Collections.singleton("64$1"),KidsPolicy.roots(r));
    }
    static KidsPolicy.Lookup server(CatalogueItem... items){Map<String,CatalogueItem> m=new HashMap<>();for(CatalogueItem i:items)m.put(i.id,i);return m::get;}
    @Test public void playTimeWalksTheServersOwnParentsUpToAKidsFolder()throws Exception{
        Set<String> roots=Collections.singleton("kids");
        KidsPolicy.Lookup nas=server(item("film","season","kids"),item("season","show","kids"),item("show","kids","kids"),item("kids","video","kids"));
        assertTrue(KidsPolicy.playable("film",roots,nas));
        KidsPolicy.Lookup outside=server(item("film","films",""),item("films","video",""),item("video","0",""),item("0","-1",""));
        assertFalse("a title outside every Kids folder is refused at play time",KidsPolicy.playable("film",roots,outside));
    }
    @Test public void aRefusedRatingOnTheTitleOrAFolderAboveRefusesPlay()throws Exception{
        Set<String> roots=Collections.singleton("kids");
        assertFalse(KidsPolicy.playable("film",roots,server(item("film","kids","kids","18"))));
        assertFalse(KidsPolicy.playable("ep",roots,server(item("ep","show","kids"),item("show","kids","kids","TV-MA"))));
    }
    @Test public void brokenOrLoopingChainsAreRefused()throws Exception{
        Set<String> roots=Collections.singleton("kids");
        assertFalse(KidsPolicy.playable("a",roots,server(item("a","b",""),item("b","a",""))));
        assertFalse("unknown to the server",KidsPolicy.playable("ghost",roots,server()));
        assertFalse("a Kids folder isn't itself a title",KidsPolicy.playable("kids",roots,server(item("kids","0",""))));
        assertFalse(KidsPolicy.playable("",roots,server()));assertFalse(KidsPolicy.playable("a",Collections.<String>emptySet(),server(item("a","kids",""))));
        List<CatalogueItem> deep=new ArrayList<>();for(int i=0;i<40;i++)deep.add(item("n"+i,"n"+(i+1),""));deep.add(item("n40","kids",""));
        assertFalse("too deep is refused, never walked forever",KidsPolicy.playable("n0",roots,server(deep.toArray(new CatalogueItem[0]))));
    }
    @Test public void onlyApprovedAppsOpenInKidsAndAdultsAreUnlimited(){
        Set<String> approved=KidsPolicy.apps("com.example.kidsapp,xbox,not a package,,../x");
        assertEquals(new LinkedHashSet<>(Arrays.asList("com.example.kidsapp","xbox")),approved);
        assertTrue(KidsPolicy.appAllowed(true,approved,"com.example.kidsapp","io.unifiedtv.preview"));
        assertTrue("Unified TV itself",KidsPolicy.appAllowed(true,approved,"io.unifiedtv.preview","io.unifiedtv.preview"));
        assertFalse(KidsPolicy.appAllowed(true,approved,"com.example.other","io.unifiedtv.preview"));
        assertFalse(KidsPolicy.appAllowed(true,approved,"","io.unifiedtv.preview"));assertFalse(KidsPolicy.appAllowed(true,approved,null,"io.unifiedtv.preview"));
        assertTrue(KidsPolicy.appAllowed(false,Collections.<String>emptySet(),"com.example.other","io.unifiedtv.preview"));
    }
    @Test public void togglingAnAppAddsOrRemovesItOnce(){
        String s=KidsPolicy.toggle("","com.example.kidsapp");assertEquals("com.example.kidsapp",s);
        s=KidsPolicy.toggle(s,"tvplus");assertEquals("com.example.kidsapp,tvplus",s);
        assertEquals("tvplus",KidsPolicy.toggle(s,"com.example.kidsapp"));
    }
}
