package io.unifiedtv.preview;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Choosing, marking for Kids and removing folders (owner, 25 October 2026), stored as the two records Kids and Adults read. */
public class LibraryFoldersTest {
    static List<LibraryFolders.Folder> none(){return new ArrayList<>();}
    @Test public void addSaysPlainlyWhenFoldersOverlap(){
        LibraryFolders.Change a=LibraryFolders.add(none(),"t","TV Show",Arrays.asList("v"),false);assertEquals("Added.",a.message);
        LibraryFolders.Change parent=LibraryFolders.add(a.folders,"v","Video",null,false);
        assertTrue(parent.message,parent.message.contains("contains “TV Show”")&&parent.message.contains("show once"));assertEquals(2,parent.folders.size());
        LibraryFolders.Change child=LibraryFolders.add(LibraryFolders.add(none(),"v","Video",null,false).folders,"k","Kids",Arrays.asList("v"),true);
        assertTrue(child.message,child.message.contains("inside “Video”")&&child.message.contains("count as Kids"));
        LibraryFolders.Change twice=LibraryFolders.add(child.folders,"k","Kids",Arrays.asList("v"),true);assertEquals(2,twice.folders.size());assertTrue(twice.message.contains("already"));
        assertEquals("the server's top level is not a folder to add",0,LibraryFolders.add(none(),"0","Server",null,false).folders.size());
    }
    @Test public void removeAndKidsMark(){
        List<LibraryFolders.Folder> f=LibraryFolders.add(LibraryFolders.add(none(),"v","Video",null,false).folders,"k","Kids",Arrays.asList("v"),true).folders;
        LibraryFolders.Change r=LibraryFolders.remove(f,"v");assertEquals(1,r.folders.size());assertTrue(r.message.contains("Nothing was deleted from the media server"));
        LibraryFolders.Change k=LibraryFolders.kids(f,"k",false);assertFalse(LibraryFolders.find(k.folders,"k").kids);assertTrue("the original list is untouched",LibraryFolders.find(f,"k").kids);
        assertTrue(k.message.contains("no longer for Kids"));
    }
    @Test public void savedAsTheHouseholdAndKidsRecords()throws Exception{
        List<LibraryFolders.Folder> f=LibraryFolders.add(LibraryFolders.add(none(),"v","Video",null,false).folders,"k","Kids",Arrays.asList("v"),true).folders;
        JSONObject[] r=LibraryFolders.records(FakeServer.source(),f);
        assertEquals(2,r[0].getJSONArray("roots").length());assertEquals(1,r[1].getJSONArray("roots").length());assertEquals("k",r[1].getJSONArray("roots").getJSONObject(0).getString("id"));
        assertTrue(KidsPolicy.ready(r[1]));assertEquals(FakeServer.CONTROL,r[1].getString("control"));
        assertNull("no Kids folder, no Kids record",LibraryFolders.records(FakeServer.source(),LibraryFolders.kids(f,"k",false).folders)[1]);
        // Read back: the Kids mark comes from the Kids record, which Kids settings may also clear.
        LibraryFolders.Library adults=LibraryFolders.library(r[0],r[1],false);assertTrue(LibraryFolders.find(adults.folders,"k").kids);assertFalse(LibraryFolders.find(adults.folders,"v").kids);
        assertFalse(LibraryFolders.find(LibraryFolders.library(r[0],new JSONObject(),false).folders,"k").kids);
        LibraryFolders.Library child=LibraryFolders.library(r[0],r[1],true);assertEquals(1,child.folders.size());assertEquals(Collections.singleton("k"),child.kidsIds);
    }
    @Test public void kidsFoldersChosenEarlierFromKidsSettingsStillCount()throws Exception{
        JSONObject legacy=LibraryFolders.serverOnly(FakeServer.source()).put("roots",new JSONArray().put(new JSONObject().put("id","old-kids").put("name","Cartoons")));
        LibraryFolders.Library adults=LibraryFolders.library(new JSONObject(),legacy,false);
        assertTrue("Adults see the Kids folders even with no household folders",adults.ready);assertTrue(LibraryFolders.find(adults.folders,"old-kids").kids);
        JSONObject elsewhere=LibraryFolders.serverOnly(new JSONObject().put("udn","uuid:other").put("control","http://other.test:1/c")).put("roots",new JSONArray().put(new JSONObject().put("id","x")));
        JSONObject household=LibraryFolders.records(FakeServer.source(),Arrays.asList(new LibraryFolders.Folder("v","Video",null,false)))[0];
        assertEquals("another server's Kids folders aren't mixed in",1,LibraryFolders.library(household,elsewhere,false).folders.size());
        assertFalse(LibraryFolders.library(new JSONObject(),new JSONObject(),false).ready);
    }
}
