package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResumeStoreTest {
    static final String STREAM="http://192.168.50.20:50001/MediaItems/42.mkv";
    @Test public void keysAreOneWayAndStable(){
        String key=ResumeStore.key(STREAM);
        assertEquals(32,key.length());assertEquals(key,ResumeStore.key(STREAM));assertNotEquals(key,ResumeStore.key(STREAM+"x"));
        ResumeStore store=new ResumeStore("");store.record(key,1_800_000,6_000_000,1000);
        assertFalse("no address, title or file name is stored",store.json().contains("192.168")||store.json().contains("42.mkv"));
    }
    @Test public void remembersClearsAndExpires(){
        ResumeStore store=new ResumeStore(null);String key=ResumeStore.key(STREAM);
        store.record(key,1_800_000,6_000_000,1000);assertEquals(1_800_000,store.position(key,2000));
        ResumeStore reloaded=new ResumeStore(store.json());assertEquals(1_800_000,reloaded.position(key,2000));
        assertEquals("expired",0,reloaded.position(key,1000+ResumeStore.EXPIRY_MS+1));
        store.record(key,5_990_000,6_000_000,3000);assertEquals("watched to the end clears the place",0,store.position(key,4000));assertEquals(0,store.size());
        store.record(key,1_800_000,6_000_000,1000);store.record(key,10_000,6_000_000,5000);assertEquals("barely started again clears it",0,store.position(key,6000));
    }
    @Test public void boundedOldestFirst(){
        ResumeStore store=new ResumeStore("");
        for(int i=0;i<ResumeStore.MAX+15;i++)store.record(ResumeStore.key("film"+i),600_000,6_000_000,10_000+i);
        assertEquals(ResumeStore.MAX,store.size());
        assertEquals(0,store.position(ResumeStore.key("film0"),20_000));
        assertEquals(600_000,store.position(ResumeStore.key("film"+(ResumeStore.MAX+14)),20_000));
    }
    @Test public void damagedStoreStartsEmpty(){assertEquals(0,new ResumeStore("{not json").size());}
    // Owner, 25 October 2026: Adults and Kids keep separate places; everything saved before then was an adult's.
    @Test public void eachProfileHasItsOwnStore(){
        assertEquals("places-adults",ResumeStore.prefsKey("adult"));assertEquals("places-kids",ResumeStore.prefsKey("child"));
        assertEquals("unknown values are Adults, as the profile itself is",ResumeStore.prefsKey(null),ResumeStore.prefsKey("adult"));
        assertNotEquals(ResumeStore.LEGACY,ResumeStore.prefsKey("child"));
    }
    @Test public void legacyPlacesMoveToAdultsAndTheNewerPlaceWins(){
        ResumeStore legacy=new ResumeStore("");legacy.record(ResumeStore.key("a"),600_000,6_000_000,1000);legacy.record(ResumeStore.key("b"),700_000,6_000_000,1000);
        ResumeStore adults=new ResumeStore("");adults.record(ResumeStore.key("b"),900_000,6_000_000,5000);
        ResumeStore merged=new ResumeStore(ResumeStore.merge(legacy.json(),adults.json()));
        assertEquals(600_000,merged.position(ResumeStore.key("a"),6000));assertEquals("the newer adult place is kept",900_000,merged.position(ResumeStore.key("b"),6000));
        assertEquals(2,new ResumeStore(ResumeStore.merge(legacy.json(),"")).size());assertEquals(0,new ResumeStore(ResumeStore.merge("{broken","")).size());
    }
}
