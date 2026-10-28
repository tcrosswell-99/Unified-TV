package io.unifiedtv.preview;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.KeyEvent;
import android.widget.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * SET-034 "Wrong picture?" against a pretend TMDB (no network): up to eight candidates for the cleaned title, a typed title
 * instead, "No picture", back to automatic; the choice is kept per item, survives a restart and wins in Posters. Only the
 * title is sent. The sheet is the glass sidebar (SidebarRuleTest lists it in SETUP).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class PictureFixTest {
    static final String UDN="uuid:test-nas";
    final List<String> calls=Collections.synchronizedList(new ArrayList<>());byte[] jpeg;boolean offline;Context app;
    @Before public void start(){
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        Bitmap b=Bitmap.createBitmap(30,45,Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,80,out);jpeg=out.toByteArray();
        Posters.reset();Posters.testKey="0123456789abcdef0123456789abcdef";PictureFixActivity.testWorker=Runnable::run;
        Posters.transport=(url,headers,limit)->{calls.add(url);if(offline)throw new java.io.IOException("offline");assertTrue(url,Tmdb.allowed(url));
            if(url.contains("image.tmdb.org"))return new Tmdb.Response(200,jpeg,0);
            try{JSONArray r=new JSONArray();
                if(url.contains("query=Heat")){
                    r.put(result(949,"Heat","1995-12-15","/heat.jpg"));r.put(result(10,"Heat","1986-03-14","/heat86.jpg"));r.put(new JSONObject().put("id",11).put("title","Heat").put("release_date","2001").put("adult",true).put("poster_path","/x.jpg"));
                    r.put(new JSONObject().put("id",12).put("title","Heat (no picture)").put("release_date","2010-01-01").put("poster_path",JSONObject.NULL).put("backdrop_path",JSONObject.NULL));
                    for(int i=0;i<10;i++)r.put(result(100+i,"Heat Wave "+i,(1970+i)+"-01-01","/w"+i+".jpg"));}
                if(url.contains("query=Dune"))r.put(result(438631,"Dune","2021-09-15","/dune.jpg"));
                return new Tmdb.Response(200,new JSONObject().put("results",r).toString().getBytes("UTF-8"),0);}catch(Exception e){throw new java.io.IOException(e);}};
    }
    static JSONObject result(long id,String title,String date,String poster)throws JSONException{return new JSONObject().put("id",id).put("title",title).put("release_date",date).put("poster_path",poster).put("backdrop_path",poster.replace(".jpg","b.jpg")).put("vote_count",10).put("overview","About "+title+".");}
    @After public void stop(){Posters.testKey=null;Posters.transport=null;Posters.reset();PictureFixActivity.testWorker=null;}

    static LibraryCatalogue.Card heat(){return PostersTest.film("heat","Heat","1995");}
    private ActivityController<PictureFixActivity> open(){
        ActivityController<PictureFixActivity> c=Robolectric.buildActivity(PictureFixActivity.class,PictureFixActivity.intent(app,heat(),UDN)).setup();LibraryJourneyTest.idle();return c;}
    static Button button(PictureFixActivity a,String start){for(Button b:LibraryJourneyTest.all(a.getWindow().getDecorView(),Button.class))if(b.getText().toString().startsWith(start))return b;return null;}
    static List<String> rows(PictureFixActivity a){List<String> out=new ArrayList<>();for(Button b:LibraryJourneyTest.all(a.getWindow().getDecorView(),Button.class))out.add(b.getText().toString());return out;}
    Bitmap poster(LibraryCatalogue.Card c)throws Exception{
        Bitmap[] got={null};boolean[] done={false};Posters.get(app).poster(c,UDN,true,b->{got[0]=b;done[0]=true;});
        LibraryJourneyTest.until("poster answered",()->done[0]);return got[0];
    }

    @Test public void upToEightCandidatesForTheCleanedTitleAndOnlyTheTitleIsSent(){
        try(var c=open()){PictureFixActivity a=c.get();
            assertFalse(a.searching());assertEquals(PictureFixActivity.MAX,a.candidates().size());
            assertEquals("the file's year first",949L,a.candidates().get(0).id);assertEquals("then TMDB's own order",10L,a.candidates().get(1).id);
            for(Tmdb.Match m:a.candidates()){assertNotEquals("never an adult title",11L,m.id);assertNotEquals("never one with nothing to show",12L,m.id);}
            String search=calls.get(0);assertTrue(search,search.contains("/search/movie?query=Heat&"));
            for(String leak:new String[]{"test-nas","mkv","heat%3A","dlna"})assertFalse("only the title: "+search,search.contains(leak));
            assertNotNull(button(a,"Heat (1995)"));assertNotNull(button(a,"Heat (1986)"));assertNotNull(button(a,"No picture"));assertNotNull(button(a,"Search for a different title"));
            assertNull("nothing chosen yet",button(a,"Use the automatic picture"));
            assertNotNull("each candidate shows its own small poster",button(a,"Heat (1986)").getCompoundDrawablesRelative()[0]);
            assertTrue("focus is in the sheet, on the first candidate: "+a.getCurrentFocus(),a.getCurrentFocus() instanceof Button&&((Button)a.getCurrentFocus()).getText().toString().equals("Heat (1995)"));
        }
    }
    @Test public void aChoiceIsKeptForThatTitleAndPostersUseIt()throws Exception{
        try(var c=open()){PictureFixActivity a=c.get();
            button(a,"Heat (1986)").performClick();assertTrue("the sheet closes",a.isFinishing());
            assertEquals("Picture changed to Heat (1986)",ShadowToast.getTextOfLatestToast());
        }
        assertEquals(10L,Posters.get(app).fix(UDN,heat().lookupKey()).optLong("id"));
        Posters.reset();calls.clear();   // a restart: read from the TV's own storage
        assertNotNull(poster(heat()));
        assertEquals("no search: the household's choice wins",1,calls.size());assertTrue(calls.get(0),calls.get(0).endsWith("/heat86.jpg"));
        assertEquals("About Heat.",Posters.get(app).details(heat(),UDN)[0]);
        assertNull("only that title: another server's Heat is untouched",Posters.get(app).fix("uuid:other",heat().lookupKey()));
        assertEquals(1,Posters.get(app).choices());
        try(var c=open()){PictureFixActivity a=c.get();
            assertEquals("Heat (1986)",((Button)button(a,"Heat (1986)")).getText().toString());
            assertTrue("the chosen one is marked",button(a,"Heat (1986)").getContentDescription().toString().endsWith("Chosen"));
            button(a,"Use the automatic picture").performClick();
        }
        assertNull(Posters.get(app).fix(UDN,heat().lookupKey()));calls.clear();
        assertNotNull(poster(heat()));assertTrue("looked up again, cautiously",calls.get(0).contains("query=Heat"));assertTrue(calls.get(calls.size()-1).endsWith("/heat.jpg"));
    }
    @Test public void noPictureMeansAPlainTitleCard()throws Exception{
        try(var c=open()){button(c.get(),"No picture").performClick();}
        assertTrue(Posters.get(app).noPicture(UDN,heat().lookupKey()));
        calls.clear();
        LibraryCatalogue.Card withServerArt=heat();withServerArt.title.art="http://"+FakeServer.HOST+":50002/art/heat.jpg";
        assertNull("no TMDB picture and not the server's either",poster(withServerArt));assertTrue("nothing asked",calls.isEmpty());
        Bitmap[] back={null};boolean[] done={false};Posters.get(app).backdrop(withServerArt,UDN,false,b->{back[0]=b;done[0]=true;});LibraryJourneyTest.until("backdrop",()->done[0]);
        assertNull("no background either: the neutral scene",back[0]);
    }
    @Test public void aDifferentTitleCanBeTypedWithTheTvKeyboard(){
        try(var c=open()){PictureFixActivity a=c.get();
            button(a,"Search for a different title").performClick();LibraryJourneyTest.idle();
            EditText field=LibraryJourneyTest.all(a.getWindow().getDecorView(),EditText.class).get(0);assertEquals("starts from the cleaned title","Heat",field.getText().toString());
            assertTrue("the TV keyboard has focus",field.hasFocus());
            field.setText("Dune");field.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);LibraryJourneyTest.idle();
            String dune=null;synchronized(calls){for(String u:calls)if(u.contains("query=Dune"))dune=u;}
            assertNotNull("the typed title was searched",dune);assertFalse("a typed title is searched without the file's year",dune.contains("year="));
            assertEquals(1,a.candidates().size());assertNotNull(button(a,"Dune (2021)"));
            button(a,"Dune (2021)").performClick();
        }
        assertEquals(438631L,Posters.get(app).fix(UDN,heat().lookupKey()).optLong("id"));
    }
    @Test public void backFromTypingReturnsToTheList(){
        try(var c=open()){PictureFixActivity a=c.get();
            button(a,"Search for a different title").performClick();LibraryJourneyTest.idle();
            a.onBackPressed();LibraryJourneyTest.idle();assertFalse(a.isFinishing());assertNotNull(button(a,"Heat (1995)"));
            a.onBackPressed();assertTrue("Back again closes the sheet, nothing chosen",a.isFinishing());
        }
        assertNull(Posters.get(app).fix(UDN,heat().lookupKey()));
    }
    @Test public void offlineItSaysSoAndOffersTryAgain(){
        offline=true;
        try(var c=open()){PictureFixActivity a=c.get();
            assertTrue(LibraryJourneyTest.says(a,"TMDB isn't answering"));assertNotNull(button(a,"Try again"));assertNotNull("No picture still works offline",button(a,"No picture"));
            offline=false;button(a,"Try again").performClick();LibraryJourneyTest.idle();assertEquals(PictureFixActivity.MAX,a.candidates().size());
        }
    }
    @Test public void withoutAKeyItSaysWhereOneIsAdded(){
        Posters.testKey="";
        try(var c=open()){PictureFixActivity a=c.get();
            assertTrue(LibraryJourneyTest.says(a,"Posters from TMDB aren't set up"));assertTrue("nothing asked",calls.isEmpty());
            assertNull(button(a,"Search for a different title"));assertNotNull(button(a,"No picture"));
        }
    }
    @Test public void choicesCanAllBeForgotten(){
        try(var c=open()){button(c.get(),"No picture").performClick();}
        assertEquals(1,Posters.get(app).choices());Posters.get(app).forgetChoices();
        assertEquals(0,Posters.get(app).choices());assertFalse(Posters.get(app).noPicture(UDN,heat().lookupKey()));
        Posters.reset();assertEquals("forgotten on the TV's storage too",0,Posters.get(app).choices());
    }
    @Test public void kidsNeverReachIt(){
        app.getSharedPreferences("unified-tv-preview",0).edit().putString(Profiles.PREF,Profiles.CHILD).commit();
        try(var c=Robolectric.buildActivity(PictureFixActivity.class,PictureFixActivity.intent(app,heat(),UDN)).setup()){assertTrue(c.get().isFinishing());assertTrue(calls.isEmpty());}
    }
    @Test public void candidatesArePickedByEyeNotByTheCautiousMatch()throws Exception{
        JSONArray r=new JSONArray().put(result(1,"Heat","1995-12-15","/a.jpg")).put(result(2,"Heat","1986-01-01","/b.jpg")).put(result(3,"Heat","1996-01-01","/c.jpg"));
        List<Tmdb.Match> m=Tmdb.candidates(r,false,"1995",8);
        assertEquals("within a year first, then TMDB's order",Arrays.asList(1L,3L,2L),Arrays.asList(m.get(0).id,m.get(1).id,m.get(2).id));
        assertEquals(2,Tmdb.candidates(r,false,"",2).size());assertTrue(Tmdb.candidates(null,false,"",8).isEmpty());
        assertNull("the cautious automatic match would refuse two equal Heats from 1986 and 1996 with no year",Tmdb.pick(new JSONArray().put(result(2,"Heat","1986-01-01","/b.jpg")).put(result(3,"Heat","1996-01-01","/c.jpg")),false,"Heat",""));
    }
}
