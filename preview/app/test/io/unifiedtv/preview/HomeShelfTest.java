package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class HomeShelfTest {
    static HomeShelf.Poster poster(String id,String url){return new HomeShelf.Poster(id,"Film "+id,"",""," ".equals(url)?"":url,false,"root");}
    @Test public void titlesWithThisProfilesPlaceComeFirstAndSaySoLocally(){
        ResumeStore store=new ResumeStore("");store.record(ResumeStore.key("stream-b"),600_000,6_000_000,1000);
        List<HomeShelf.Poster> out=HomeShelf.resumeFirst(Arrays.asList(poster("a","stream-a"),poster("b","stream-b"),poster("c","stream-c")),store,2000);
        assertEquals("b",out.get(0).id);assertEquals(600_000,out.get(0).resume);assertTrue(out.get(0).label().endsWith("resume on this TV"));
        assertEquals("a",out.get(1).id);assertFalse(out.get(1).label().contains("resume"));
    }
    @Test public void anotherProfilesPlacesAreNotRead(){
        ResumeStore kids=new ResumeStore("");List<HomeShelf.Poster> out=HomeShelf.resumeFirst(Arrays.asList(poster("a","stream-a")),kids,2000);
        assertEquals(0,out.get(0).resume);
    }
    @Test public void kidsRowKeepsOnlyWhatKidsMaySee(){
        DlnaClient.Item ok=new DlnaClient.Item();ok.id="1";ok.title="Ok";DlnaClient.Item rated=new DlnaClient.Item();rated.id="2";rated.title="No";rated.ratings.add("15");
        List<DlnaClient.Item> kept=HomeShelf.kidsOnly(Arrays.asList(ok,rated),"uuid:nas","kids",Collections.singleton("kids"));
        assertEquals(1,kept.size());assertEquals("1",kept.get(0).id);
        assertTrue("from a folder that isn't a Kids folder, nothing",HomeShelf.kidsOnly(Arrays.asList(ok),"uuid:nas","films",Collections.singleton("kids")).isEmpty());
    }
}
