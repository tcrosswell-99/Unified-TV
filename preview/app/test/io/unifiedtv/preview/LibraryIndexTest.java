package io.unifiedtv.preview;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URI;
import java.util.*;

/** Walking the chosen folders: paging, bounds, virtual views, nested choices, and what is kept on the TV. */
public class LibraryIndexTest {
    @Test public void walksEveryPageAndFolderAndKeepsNoHost()throws Exception{
        FakeServer s=new FakeServer().folder("0","v","Video");
        for(int i=0;i<60;i++)s.video("v","f"+i,"Film "+i+" (2001).mkv");
        s.folder("v","tv","Bluey").folder("tv","s1","Season 1").video("s1","e1","Bluey S01E01.mkv");
        LibraryIndex.browser=s;
        int[] last={0};
        LibraryIndex.Root r=LibraryIndex.walk(FakeServer.source(),"v",Collections.emptySet(),null,(t,f)->last[0]=t,1000);
        assertEquals(61,r.entries.size());assertTrue(r.complete);assertEquals(61,last[0]);
        assertTrue("three pages of 24 for 61 entries",s.calls.containsAll(Arrays.asList("v@0","v@24","v@48")));
        LibraryIndex.Entry ep=null;for(LibraryIndex.Entry e:r.entries)if(e.id.equals("e1"))ep=e;
        assertEquals(Arrays.asList("Bluey","Season 1"),ep.pathNames);assertEquals(Arrays.asList("tv","s1"),ep.pathIds);
        LibraryIndex.Index index=new LibraryIndex.Index();index.roots.put("v",r);String json=LibraryIndex.json(index);
        assertFalse("no host is written",json.contains(FakeServer.HOST));
        assertEquals("http://other.test:50002/v/e1.mkv",LibraryIndex.absolute(ep.url,new URI("http://other.test:50001/ctl")));
        LibraryIndex.Index back=LibraryIndex.parse(json);assertEquals(61,back.roots.get("v").entries.size());assertEquals(Arrays.asList("Bluey","Season 1"),back.roots.get("v").entries.get(60).pathNames);
    }
    @Test public void virtualViewsAndOtherChosenFoldersAreNotWalkedTwice()throws Exception{
        FakeServer s=new FakeServer().folder("0","v","Video").folder("v","rec","Recently Added").folder("v","gen","Genre").folder("v","all","All Videos").folder("v","fold","Folder")
            .folder("fold","kids","Kids").video("fold","f1","Heat (1995).mkv").video("kids","k1","Bluey S01E01.mkv").alias("rec","r1","f1").alias("all","a1","f1");
        LibraryIndex.browser=s;
        LibraryIndex.Root r=LibraryIndex.walk(FakeServer.source(),"v",new HashSet<>(Collections.singleton("kids")),null,null,1000);
        for(String skipped:new String[]{"rec@0","gen@0","all@0","kids@0"})assertFalse(skipped,s.calls.contains(skipped));
        assertEquals(1,r.entries.size());assertEquals("f1",r.entries.get(0).id);
    }
    @Test public void aBrokenSubfolderIsSkippedButABrokenRootFails()throws Exception{
        FakeServer s=new FakeServer().folder("0","v","Video").folder("v","bad","Bad").video("v","f1","Heat.mkv");s.broken.add("bad");LibraryIndex.browser=s;
        LibraryIndex.Root r=LibraryIndex.walk(FakeServer.source(),"v",null,null,null,1000);assertEquals(1,r.entries.size());assertFalse(r.complete);
        s.broken.add("v");try{LibraryIndex.walk(FakeServer.source(),"v",null,null,null,1000);fail();}catch(DlnaClient.Failure expected){}
    }
    @Test public void titlesKeepWhenTheyWereFirstSeen()throws Exception{
        FakeServer s=new FakeServer().folder("0","v","Video").video("v","f1","Heat.mkv");LibraryIndex.browser=s;
        LibraryIndex.Root first=LibraryIndex.walk(FakeServer.source(),"v",null,null,null,1000);
        s.video("v","f2","Up.mkv");
        LibraryIndex.Root second=LibraryIndex.walk(FakeServer.source(),"v",null,first,null,99_000);
        assertEquals(1000,second.created);
        for(LibraryIndex.Entry e:second.entries)assertEquals(e.id,e.id.equals("f1")?1000:99_000,e.seen);
    }
    @Test public void dueWhenNeverWalkedOrOld()throws Exception{
        LibraryFolders.Library lib=LibraryFolders.library(FakeServer.source().put("roots",new org.json.JSONArray().put(new JSONObject().put("id","v").put("name","Video"))),new JSONObject(),false);
        LibraryIndex.Index i=new LibraryIndex.Index();assertTrue(LibraryIndex.due(i,lib,1000));
        LibraryIndex.Root r=new LibraryIndex.Root("v");r.at=1000;i.roots.put("v",r);
        assertFalse(LibraryIndex.due(i,lib,1000+LibraryIndex.FRESH_MS));assertTrue(LibraryIndex.due(i,lib,1001+LibraryIndex.FRESH_MS));
    }
    @Test public void addressesWithoutHostRoundTrip()throws Exception{
        assertEquals("http|50002|/v/1.mkv?x=1",LibraryIndex.rel("http://nas.test:50002/v/1.mkv?x=1"));assertEquals("http|80|/a.jpg",LibraryIndex.rel("http://nas.test/a.jpg"));
        assertEquals("",LibraryIndex.rel(""));assertEquals("",LibraryIndex.absolute("javascript|1|/x",new URI(FakeServer.CONTROL)));assertEquals("",LibraryIndex.absolute("http|80|nopath",new URI(FakeServer.CONTROL)));
        assertEquals(0,LibraryIndex.parse("{broken").roots.size());
    }
}
