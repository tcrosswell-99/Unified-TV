package io.unifiedtv.preview;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class CatalogueItemTest {
    @Test public void aDlnaEntryMapsWithAStableIdAndNoAddress(){
        DlnaClient.Item i=new DlnaClient.Item();i.id="64$3$1";i.title="Paddington";i.year="2014";i.parent="64$3";i.upnpClass="object.item.videoItem.movie";i.url="http://192.0.2.10/media/1.mkv";i.ratings.add("PG");
        CatalogueItem c=CatalogueItem.fromDlna(i,"uuid:nas-1","64$3");
        assertEquals("dlna:uuid:nas-1:64$3$1",c.stableId);assertEquals(CatalogueItem.Kind.FILM,c.kind);assertEquals("64$3",c.folder);assertEquals("64$3",c.root);
        assertEquals(java.util.Collections.singletonList("PG"),c.ratings);assertFalse("no address in the id",c.stableId.contains("192.0.2"));
    }
    @Test public void kindsAreNeverGuessed(){
        assertEquals(CatalogueItem.Kind.FOLDER,CatalogueItem.dlnaKind("object.container.storageFolder",true));
        assertEquals(CatalogueItem.Kind.VIDEO,CatalogueItem.dlnaKind("object.item.videoItem",false));
        assertEquals(CatalogueItem.Kind.VIDEO,CatalogueItem.dlnaKind(null,false));
    }
    @Test public void aMediaAccountItemMapsToTheSameShape()throws Exception{
        CatalogueItem c=CatalogueItem.fromMediaAccount(new JSONObject("{\"Id\":\"abc\",\"Name\":\"Bluey\",\"Type\":\"Series\",\"ProductionYear\":2018,\"OfficialRating\":\"TV-Y\",\"ParentId\":\"p\",\"ImageTags\":{\"Primary\":\"t\"}}"),"server-1");
        assertEquals("media-account:server-1:abc",c.stableId);assertEquals(CatalogueItem.Kind.SERIES,c.kind);assertEquals("2018",c.year);assertEquals("TV-Y",c.ratings.get(0));assertEquals("Primary",c.poster);
        assertTrue(CatalogueItem.fromMediaAccount(new JSONObject("{\"Id\":\"x\"}"),"s").ratings.isEmpty());
    }
}
