package io.unifiedtv.preview;
import android.content.Context;
import android.graphics.Bitmap;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Posters: matched once, kept on the TV (match and picture), offline afterwards; a "no match" is remembered too. Fake TMDB. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class PostersTest {
    final List<String> calls=Collections.synchronizedList(new ArrayList<>());byte[] jpeg;boolean offline;
    @Before public void start(){
        Bitmap b=Bitmap.createBitmap(30,45,Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,80,out);jpeg=out.toByteArray();
        Posters.reset();Posters.testKey="0123456789abcdef0123456789abcdef";
        Posters.transport=(url,headers,limit)->{calls.add(url);if(offline)throw new java.io.IOException("offline");
            assertTrue(url,Tmdb.allowed(url));
            if(url.contains("image.tmdb.org"))return new Tmdb.Response(200,jpeg,0);
            try{JSONArray r=new JSONArray();if(url.contains("query=Heat"))r.put(new JSONObject().put("id",949).put("title","Heat").put("release_date","1995-12-15").put("poster_path","/heat.jpg").put("backdrop_path","/heatb.jpg").put("vote_count",7000).put("overview","A heist."));
                return new Tmdb.Response(200,new JSONObject().put("results",r).toString().getBytes("UTF-8"),0);}catch(Exception e){throw new java.io.IOException(e);}};
    }
    @After public void stop(){Posters.testKey=null;Posters.transport=null;Posters.reset();}
    static LibraryCatalogue.Card film(String id,String name,String year){LibraryCatalogue.Title t=new LibraryCatalogue.Title();t.id=id;t.name=name;t.year=year;t.stableId="dlna:uuid:test-nas:"+id;return new LibraryCatalogue.Card(t);}
    Bitmap get(LibraryCatalogue.Card c)throws Exception{
        Bitmap[] got={null};boolean[] done={false};Context app=RuntimeEnvironment.getApplication();
        Posters.get(app).poster(c,"uuid:test-nas",true,b->{got[0]=b;done[0]=true;});
        LibraryJourneyTest.until("poster answered",()->done[0]);return got[0];
    }
    @Test public void matchedOnceThenFromTheTvItself()throws Exception{
        assertNotNull(get(film("heat","Heat","1995")));
        assertEquals("one search, one picture",2,calls.size());assertTrue(calls.get(0).contains("query=Heat")&&calls.get(0).contains("year=1995"));
        assertFalse("only the title and year are sent",calls.get(0).contains("test-nas")||calls.get(0).contains("mkv"));
        JSONObject saved=Posters.get(RuntimeEnvironment.getApplication()).saved("uuid:test-nas","t:dlna:uuid:test-nas:heat");assertEquals("/heat.jpg",saved.getString("p"));
        assertEquals("A heist.",Posters.get(RuntimeEnvironment.getApplication()).details(film("heat","Heat","1995"),"uuid:test-nas")[0]);
        Posters.reset();offline=true;calls.clear();
        assertNotNull("offline, from the saved match and picture",get(film("heat","Heat","1995")));assertTrue(calls.isEmpty());
    }
    @Test public void noMatchIsRememberedAndNothingIsShown()throws Exception{
        assertNull(get(film("odd","Some Home Video","")));int asked=calls.size();assertTrue(asked>=1);
        Posters.reset();assertNull(get(film("odd","Some Home Video","")));assertEquals("not asked again",asked,calls.size());
    }
    @Test public void withoutAKeyNothingIsAskedAndTheStatusSaysHow()throws Exception{
        Posters.testKey="";assertNull(get(film("heat","Heat","1995")));assertTrue(calls.isEmpty());
        assertTrue(Posters.status(RuntimeEnvironment.getApplication()).startsWith("Off"));
        Posters.testKey="0123456789abcdef0123456789abcdef";assertTrue(Posters.status(RuntimeEnvironment.getApplication()).startsWith("On"));
    }
}
