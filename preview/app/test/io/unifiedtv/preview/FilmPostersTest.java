package io.unifiedtv.preview;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Button;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Owner's TCL, 27 October 2026: no posters and no poster backgrounds. A film in its own folder is looked up by the
 * folder's title and year; a key added later is used for titles shown without one (the server's own picture held in memory
 * no longer hides it); a refused key is not asked again for every tile; Owner diagnostics say what happened; with no key
 * the library says in one line where to add it. Fake TMDB and fake media server: no network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FilmPostersTest {
    static final String KEY="0123456789abcdef0123456789abcdef",UDN="uuid:test-nas";
    final List<String> calls=Collections.synchronizedList(new ArrayList<>());byte[] jpeg;volatile int status=200;Context app;
    private final DlnaClient.Opener original=DlnaClient.opener;

    @Before public void start(){
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        Bitmap b=Bitmap.createBitmap(30,45,Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,80,out);jpeg=out.toByteArray();
        Posters.reset();Posters.testKey="";
        Posters.transport=(url,headers,limit)->{calls.add(url);assertTrue(url,Tmdb.allowed(url));if(status!=200)return new Tmdb.Response(status,null,0);
            if(url.contains("image.tmdb.org"))return new Tmdb.Response(200,jpeg,0);
            try{JSONArray r=new JSONArray();
                if(url.contains("query=Other+Film"))r.put(new JSONObject().put("id",77).put("title","Other Film").put("release_date","2020-02-01").put("poster_path","/other.jpg").put("backdrop_path","/otherb.jpg").put("vote_count",900).put("overview","Another film."));
                return new Tmdb.Response(200,new JSONObject().put("results",r).toString().getBytes("UTF-8"),0);}catch(JSONException|UnsupportedEncodingException e){throw new IOException(e);}};
    }
    @After public void stop(){LibraryCollector.reset();Posters.testKey=null;Posters.transport=null;Posters.reset();DlnaClient.opener=original;LibraryIndex.browser=DlnaClient::browse;LibraryFolders.testStore=null;LibraryIndex.clear(app);}

    Bitmap poster(LibraryCatalogue.Card c)throws Exception{
        Bitmap[] got={null};boolean[] done={false};Posters.get(app).poster(c,UDN,true,b->{got[0]=b;done[0]=true;});
        LibraryJourneyTest.until("poster answered",()->done[0]);return got[0];
    }
    /** "Films/Other Film (2020)/grp-of1080p.mkv" beside a sample, as the saved list makes it. */
    static LibraryCatalogue.Card folderFilm()throws Exception{
        FakeServer s=new FakeServer().folder("0","f","Films").folder("f","of","Other Film (2020)").video("of","of-main","grp-of1080p.mkv",6L<<30,110*60_000L).video("of","of-s","grp-of1080p-sample.mkv",50L<<20,60_000L);
        LibraryIndex.browser=s;
        LibraryFolders.Library lib=LibraryFolders.library(LibraryFolders.records(FakeServer.source(),Collections.singletonList(new LibraryFolders.Folder("f","Films",null,false)))[0],new JSONObject(),false);
        LibraryIndex.Index index=new LibraryIndex.Index();index.roots.put("f",LibraryIndex.walk(lib.source,"f",new HashSet<>(),null,null,1000));
        LibraryCatalogue.Catalogue c=LibraryCatalogue.build(index,lib,KidsPolicy.Scheme.UK_BBFC,new ResumeStore(""),5000);
        assertEquals("the sample is not a title",1,c.titles.size());
        return c.row("films").all.get(0);
    }

    @Test public void aFolderFilmIsLookedUpByItsFoldersTitleAndYear()throws Exception{
        Posters.testKey=KEY;LibraryCatalogue.Card card=folderFilm();
        assertNotNull(poster(card));
        String search=calls.get(0);assertTrue(search,search.contains("/search/movie?query=Other+Film&")&&search.contains("year=2020"));
        for(String leak:new String[]{"grp","1080p","mkv","test-nas","sample"})assertFalse("only the cleaned title and year: "+search,search.contains(leak));
        assertTrue(calls.get(calls.size()-1).endsWith("/other.jpg"));
        assertEquals(Posters.OK,Posters.get(app).lastResult());assertEquals(200,Posters.get(app).lastCode());
    }
    @Test public void aKeyAddedLaterIsUsedForTitlesShownWithoutOne()throws Exception{
        // The server's own picture, served by a pretend NAS (no network).
        DlnaClient.opener=(url,network)->new Picture(url,jpeg);
        LibraryCatalogue.Card card=folderFilm();card.title.art="http://192.168.50.20:50002/art/of.jpg";
        Posters posters=Posters.get(app);
        assertNotNull("without a key: the server's own picture",poster(card));assertTrue("TMDB not asked",calls.isEmpty());
        assertEquals(Posters.NO_KEY,posters.lastResult());assertNull("nothing saved against the title",posters.saved(UDN,card.lookupKey()));
        assertNotNull(posters.cachedPoster(card));
        // The household adds its key on the phone; the library comes back to the front.
        Posters.testKey=KEY;assertTrue(posters.refreshKey());assertTrue(posters.usable());
        assertNull("the picture shown without the key is dropped",posters.cachedPoster(card));
        assertNotNull(poster(card));
        assertTrue("looked up now: "+calls,calls.get(0).contains("query=Other+Film"));assertTrue(calls.get(calls.size()-1).endsWith("/other.jpg"));
        assertFalse("the same key again changes nothing",posters.refreshKey());
    }
    @Test public void aRefusedKeyIsNotAskedForEveryTileAndDiagnosticsSaySo()throws Exception{
        Posters.testKey=KEY;status=401;LibraryCatalogue.Card card=folderFilm();Posters posters=Posters.get(app);
        assertNull(poster(card));assertEquals(1,calls.size());
        assertEquals(Posters.REFUSED,posters.lastResult());assertEquals(401,posters.lastCode());
        assertNull(poster(PostersTest.film("x","Heat","1995")));assertEquals("paused after a refusal",1,calls.size());
        String report=posters.diagnosis(true,at->"just now");
        assertTrue(report,report.contains("TMDB key: saved")&&report.contains("refused, HTTP 401")&&report.contains("(just now)"));
        assertFalse("never the key",report.contains(KEY));
        // A new key ends the pause; a title TMDB doesn't know is listed by its cleaned name only.
        status=200;Posters.testKey="fedcba9876543210fedcba9876543210";assertTrue(posters.refreshKey());
        assertNotNull(poster(card));assertNull(poster(PostersTest.film("odd","Some Home Video","2011")));
        assertEquals(Collections.singletonList("Some Home Video (2011)"),posters.unmatched());
        assertArrayEquals(new int[]{1,2},posters.matchCounts());
        report=posters.diagnosis(true,null);assertTrue(report,report.contains("Last TMDB request: ok, HTTP 200")&&report.contains("Matched: 1 of 2 titles looked up")&&report.contains("No match lately: Some Home Video (2011)"));
        Posters.reset();assertEquals("kept on the TV",Collections.singletonList("Some Home Video (2011)"),Posters.get(app).unmatched());
        Posters.get(app).clear();assertTrue("forgotten with the rest (Privacy)",Posters.get(app).unmatched().isEmpty());
    }
    @Test public void diagnosticsWithoutAKeySayWhereToAddIt(){
        String r=Posters.report(false,Posters.NO_KEY,0,"",0,0,Collections.emptyList());
        assertTrue(r,r.startsWith("TMDB key: not saved. "+Posters.HINT));assertTrue(r.contains("not sent: no TMDB key on this TV"));assertTrue(r.contains("Matched: 0 of 0"));
        assertTrue(Posters.report(true,"",0,"",0,0,null).contains("none yet on this TV"));
        assertTrue(Posters.plain(Posters.OFFLINE,0).startsWith("offline"));
    }
    @Test public void withNoKeyTheLibrarySaysWhereToAddItInOneLine()throws Exception{
        LibraryJourneyTest.MapStore store=new LibraryJourneyTest.MapStore();LibraryFolders.testStore=store;
        LibraryIndex.browser=new FakeServer().folder("0","f","Films").folder("f","hf","Heat (1995)").video("hf","heat","Heat.1995.1080p.mkv");
        LibraryFolders.save(app,FakeServer.source(),Collections.singletonList(new LibraryFolders.Folder("f","Films",null,false)));
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            LibraryJourneyTest.until("rows",()->a.catalogue()!=null&&!LibraryJourneyTest.all(a.getWindow().getDecorView(),PosterView.class).isEmpty());
            LibraryJourneyTest.until("the key is read off the main thread",a::keyHintShown);assertTrue(LibraryJourneyTest.says(a,Posters.ROW_HINT));
            // OK on the line opens Settings' Film posters step (phone setup).
            Button add=LibraryJourneyTest.button(a,Posters.ROW_HINT);assertNotNull(add);assertTrue(add.isFocusable());add.performClick();
            android.content.Intent next=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(SettingsActivity.class.getName(),next.getComponent().getClassName());assertEquals(SettingsActivity.PHONE_TMDB,next.getStringExtra("task"));
            assertEquals("Heat",a.catalogue().titles.get(0).name);
            // The key is added on the phone while the library is away; coming back hides the hint and asks TMDB.
            c.pause().stop();Posters.testKey=KEY;c.restart().start().resume();
            LibraryJourneyTest.until("the hint goes once the key is read",()->!a.keyHintShown());
        }
        Posters.reset();
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){LibraryJourneyTest.until("rows",()->c.get().catalogue()!=null);LibraryJourneyTest.idle();assertFalse("with a key: no hint",c.get().keyHintShown());}
    }

    /** A pretend NAS picture: 200 and the bytes. */
    static final class Picture extends HttpURLConnection {
        final byte[] body;Picture(URL u,byte[] body){super(u);this.body=body;}
        @Override public void connect(){}
        @Override public int getResponseCode(){return 200;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(body);}
        @Override public void disconnect(){}
        @Override public boolean usingProxy(){return false;}
    }
}
