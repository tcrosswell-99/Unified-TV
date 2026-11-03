package io.unifiedtv.preview;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Settings > Library & Media's Favourites row (SET-032): counted per profile, and clearing one profile keeps the other's. */
public class LibraryRowsTest {
    @Test public void favouritesAreCountedAndClearedPerProfile(){
        MapPrefs p=new MapPrefs();
        p.edit().putString(Favourites.key(Profiles.ADULT),Favourites.write(Arrays.asList("t:dlna:a:1","t:dlna:a:2"))).putString(Favourites.key(Profiles.CHILD),Favourites.write(Arrays.asList("t:dlna:a:3"))).commit();
        assertEquals(2,Favourites.count(p,Profiles.ADULT));assertEquals(1,Favourites.count(p,Profiles.CHILD));
        assertEquals("Favourites: Adults 2 · Kids 1 · Clear",LibraryPage.favouritesLine(2,1));
        Favourites.clear(p,Profiles.CHILD);
        assertEquals(0,Favourites.count(p,Profiles.CHILD));assertEquals("the other profile's are kept",2,Favourites.count(p,Profiles.ADULT));
        assertEquals("nothing to clear: no Clear","Favourites: Adults 0 · Kids 0",LibraryPage.favouritesLine(0,0));
    }
}
