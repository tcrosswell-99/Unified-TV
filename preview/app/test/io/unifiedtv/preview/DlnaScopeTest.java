package io.unifiedtv.preview;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DlnaScopeTest {
    static JSONArray roots()throws Exception{
        return new JSONArray().put(new JSONObject().put("id","64$1").put("name","Kids")).put(new JSONObject().put("id","64$2").put("name","Movie")).put(new JSONObject().put("id","64$3").put("name","TV Show"));
    }
    @Test public void savedRootOpensDirectlyWithItsSavedName()throws Exception{
        DlnaScope.Frame f=DlnaScope.start(roots(),"64$2","Anything",false,null);
        assertEquals("64$2",f.id);assertEquals("Movie",f.name);
    }
    @Test public void shelfFolderNeedsItsSavedRoot()throws Exception{
        DlnaScope.Frame f=DlnaScope.start(roots(),"64$2$7","Toy_Story.Collection",true,"64$2");
        assertEquals("64$2$7",f.id);assertEquals("Toy Story Collection",f.name);
        assertNull("not from the shelf",DlnaScope.start(roots(),"64$2$7","x",false,"64$2"));
        assertNull("unknown root",DlnaScope.start(roots(),"64$9$1","x",true,"64$9"));
        assertNull("no root at all",DlnaScope.start(roots(),"64$2$7","x",true,null));
        assertNull("server top level is never a start",DlnaScope.start(roots(),"0","Root",false,null));
        assertNull(DlnaScope.start(roots(),"",null,true,"64$2"));
        assertNull(DlnaScope.start(null,"64$2",null,false,null));
    }
    @Test public void backNeverClimbsAboveTheStartingFolder()throws Exception{
        DlnaScope.Trail t=new DlnaScope.Trail();t.start(new DlnaScope.Frame("64$2","Movie",0),"64$2");
        t.open(new DlnaScope.Frame("64$2$7","Pixar",0));t.page(24);
        assertEquals(24,t.current().offset);
        assertEquals("64$2",t.back().id);
        assertNull("from the first folder Back goes to the saved collections",t.back());
        assertTrue(t.isEmpty());assertNull(t.back());
    }
    @Test public void savedTrailRestoresOnlyForAStillSavedRoot()throws Exception{
        DlnaScope.Trail t=new DlnaScope.Trail();t.start(new DlnaScope.Frame("64$2$7","Pixar",0),"64$2");t.open(new DlnaScope.Frame("64$2$7$1","Shorts",24));
        DlnaScope.Trail back=DlnaScope.Trail.restore(t.save(),roots());
        assertEquals(2,back.depth());assertEquals("64$2",back.rootId());assertEquals("Shorts",back.current().name);assertEquals(24,back.current().offset);
        assertEquals("64$2$7",back.back().id);
        assertTrue("root removed by the owner",DlnaScope.Trail.restore(t.save(),new JSONArray().put(new JSONObject().put("id","64$1"))).isEmpty());
        assertTrue(DlnaScope.Trail.restore("garbage",roots()).isEmpty());
    }
    @Test public void depthIsBounded()throws Exception{
        DlnaScope.Trail t=new DlnaScope.Trail();t.start(new DlnaScope.Frame("r","Root",0),"r");
        for(int i=0;i<100;i++)t.open(new DlnaScope.Frame("f"+i,"F",0));
        assertEquals(DlnaScope.MAX_DEPTH,t.depth());
    }
}
