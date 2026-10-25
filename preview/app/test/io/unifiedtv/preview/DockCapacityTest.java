package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
/** v3.4.6 UX-DOCK-08..10, SET-027: 6 or 8 places per profile, default 6; shrinking asks; one centred dock that fits. */
public class DockCapacityTest {
    @Test public void sixByDefaultEightOnlyWhenChosenAndPerProfile(){
        assertEquals(6,DockCapacity.value(null,0,false));assertEquals(6,DockCapacity.value("7",3,true));assertEquals(6,DockCapacity.value("",6,false));
        assertEquals(8,DockCapacity.value("8",0,true));assertEquals(6,DockCapacity.value("6",9,false));
        assertNotEquals(DockCapacity.key("adult"),DockCapacity.key("child"));assertEquals("dock.capacity.adult",DockCapacity.key("anything"));
        assertEquals("6 icons (default)",DockCapacity.label(6));assertEquals("8 icons",DockCapacity.label(8));
    }
    @Test public void anEarlierLongDockKeepsWhatItShowedUntilASizeIsChosen(){
        assertEquals("seven tiles before any choice: eight places, nothing disappears",8,DockCapacity.value(null,7,false));
        assertEquals("once a size is chosen anywhere, an unset profile is 6",6,DockCapacity.value(null,7,true));
    }
    @Test public void shrinkingNeedsExactlyTheRightTilesAndKeepsTheRestInOrder(){
        List<String> dock=Arrays.asList("netflix","bbc","music","library","live","disney","smartutube","com.spotify.tv.android");
        assertEquals(2,DockCapacity.mustLeave(8,6));assertEquals(0,DockCapacity.mustLeave(5,6));assertEquals(0,DockCapacity.mustLeave(3,8));
        assertFalse("one is not enough",DockCapacity.ready(dock,Arrays.asList("bbc"),6));
        assertFalse("not on Home",DockCapacity.ready(dock,Arrays.asList("bbc","com.example.app"),6));
        assertFalse("the same tile twice",DockCapacity.ready(dock,Arrays.asList("bbc","bbc"),6));
        assertTrue(DockCapacity.ready(dock,Arrays.asList("bbc","live"),6));
        assertEquals(Arrays.asList("netflix","music","library","disney","smartutube","com.spotify.tv.android"),DockCapacity.without(dock,Arrays.asList("bbc","live")));
        assertTrue("six tiles or fewer: nothing to choose",DockCapacity.ready(dock.subList(0,6),Collections.emptyList(),6));
    }
    @Test public void eightPlacesScaleIntoOneCentredDockInsideTheSafeArea(){
        assertEquals("six keeps the approved geometry",1330f,DockCapacity.width(6),.01f);
        assertEquals(295f+35f,DockCapacity.slotLeft(6,0,1920),.01f);
        float w8=DockCapacity.width(8);assertTrue("eight is wider than six",w8>DockCapacity.width(6));
        assertTrue("and stays inside the 5% overscan safe area",(1920-w8)/2>=DockCapacity.SAFE);
        assertTrue("eight tiles are smaller, not crammed",DockCapacity.scale(8)<1&&DockCapacity.scale(8)>.75f);
        for(int cap:DockCapacity.VALUES){float s=DockCapacity.scale(cap);
            for(int i=1;i<cap;i++)assertTrue("no overlap",DockCapacity.slotLeft(cap,i,1920)>=DockCapacity.slotLeft(cap,i-1,1920)+DockCapacity.TILE*s);
            float first=DockCapacity.slotLeft(cap,0,1920),last=DockCapacity.slotLeft(cap,cap-1,1920)+DockCapacity.TILE*s;
            assertEquals("centred",1920-last,first,.01f);
            assertTrue("labels never collide with Always show",DockCapacity.labelWidth(cap,false)<=(DockCapacity.TILE+DockCapacity.GAP)*s);
        }
    }
    @Test public void longNamesEndInAnEllipsisWithinTheirPlace(){
        java.util.function.ToDoubleFunction<String> width=t->t.length()*12;   // 12 px a character
        assertEquals("Netflix",DockLabels.fit("Netflix",120,width));
        String fitted=DockLabels.fit("Samsung TV Plus Channels",120,width);assertTrue(fitted.endsWith("…"));assertTrue(fitted.length()*12<=120);
        assertEquals("…",DockLabels.fit("Anything",5,width));
    }
    @Test public void savedPerProfileAndAFirstChoiceNeverFlipsTheOtherProfile(){
        MapPrefs p=new MapPrefs();assertEquals(6,DockCapacity.get(p,"adult"));
        p.edit().putString("dock","smartutube,netflix,disney,bbc,live,music,xbox").apply();
        assertEquals("an earlier seven-tile Sony dock keeps all seven",8,DockCapacity.get(p,"adult"));assertEquals(8,DockCapacity.get(p,"child"));
        DockCapacity.set(p,"child",6);
        assertEquals(6,DockCapacity.get(p,"child"));assertEquals("Adults keeps what it showed",8,DockCapacity.get(p,"adult"));
        assertEquals(DockCapacity.SCHEMA,p.getInt(DockCapacity.SCHEMA_KEY,0));assertEquals("8",p.getString("dock.capacity.adult",null));
        MapPrefs fresh=new MapPrefs();DockCapacity.set(fresh,"adult",8);assertEquals(8,DockCapacity.get(fresh,"adult"));assertEquals("Kids stays at the default",6,DockCapacity.get(fresh,"child"));
    }
    @Test public void shrinkSavesTheDockAndTheSizeTogetherOrNothing(){
        MapPrefs p=new MapPrefs();List<String> dock=Arrays.asList("netflix","bbc","music","library","live","disney","smartutube","com.spotify.tv.android");
        p.edit().putString("dock",String.join(",",dock)).putString(DockCapacity.key("adult"),"8").putInt(DockCapacity.SCHEMA_KEY,1).apply();
        assertFalse("cancel or a wrong count: nothing changes",DockCapacity.shrink(p,"adult",dock,Arrays.asList("bbc")));
        assertEquals(String.join(",",dock),p.getString("dock",""));assertEquals(8,DockCapacity.get(p,"adult"));
        assertTrue(DockCapacity.shrink(p,"adult",dock,Arrays.asList("bbc","com.spotify.tv.android")));
        assertEquals("netflix,music,library,live,disney,smartutube",p.getString("dock",""));assertEquals(6,DockCapacity.get(p,"adult"));
    }
}
