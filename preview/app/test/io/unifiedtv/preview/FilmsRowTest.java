package io.unifiedtv.preview;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.*;
import android.widget.ImageView;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Owner's TCL, build run 99: Home's Down row and Your Films &amp; TV while the list is still being made, and their pictures.
 * The row shows the titles found so far with one line of progress (never a bare "View all"); the library shows them too,
 * with the background wording, and Back leaves the job running. With no TMDB key the row says why there are no posters
 * and OK on that line opens the setting (Adults only). A title in focus brings its own backdrop, on Home's row as in the
 * library. A long title stays inside its tile. Fake media server and fake TMDB: no network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FilmsRowTest {
    static final String KEY="0123456789abcdef0123456789abcdef";
    LibraryJourneyTest.MapStore store;FakeServer server;Context app;final List<String> tmdb=Collections.synchronizedList(new ArrayList<>());byte[] jpeg;
    private ActivityController<UnifiedPreviewActivity> controller;private View home;private Method key;

    @Before public void start()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();
        store=new LibraryJourneyTest.MapStore();LibraryFolders.testStore=store;
        server=new FakeServer().folder("0","v","Video").folder("v","m","Movie").folder("v","k","Kids")
            .video("m","heat","Heat (1995).mkv").video("m","dune","Dune (2021).mkv").video("k","frozen","Frozen (2013).mkv","PG");
        LibraryIndex.browser=server;
        Bitmap b=Bitmap.createBitmap(30,45,Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,80,out);jpeg=out.toByteArray();
        Posters.reset();Posters.testKey="";
        Posters.transport=(url,headers,limit)->{tmdb.add(url);assertTrue(url,Tmdb.allowed(url));
            if(url.contains("image.tmdb.org"))return new Tmdb.Response(200,jpeg,0);
            try{JSONArray r=new JSONArray();
                if(url.contains("query=Heat"))r.put(new JSONObject().put("id",949).put("title","Heat").put("release_date","1995-12-15").put("poster_path","/heat.jpg").put("backdrop_path","/heatb.jpg").put("vote_count",7000).put("overview","A heist."));
                return new Tmdb.Response(200,new JSONObject().put("results",r).toString().getBytes("UTF-8"),0);}catch(Exception e){throw new java.io.IOException(e);}};
        LibraryIndex.clear(app);LibraryCollector.reset();
        LibraryFolders.save(app,FakeServer.source(),Collections.singletonList(new LibraryFolders.Folder("v","Video",null,false)));
    }
    @After public void stop(){
        CountDownLatch g=server.gate;if(g!=null)g.countDown();
        if(controller!=null)controller.pause().stop().destroy();
        LibraryCollector.reset();LibraryFolders.testStore=null;LibraryIndex.browser=DlnaClient::browse;
        Posters.testKey=null;Posters.transport=null;Posters.reset();LibraryIndex.clear(app);
    }
    LibraryCollector.Status status(){return LibraryCollector.get(app).status();}
    void openHome()throws Exception{
        controller=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();
        home=RemoteJourneyTest.homeIn(controller.get());key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
    }
    void press(int code)throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,code));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,code));}
    Object field(String name)throws Exception{Field f=home.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(home);}
    @SuppressWarnings("unchecked") List<String> rowTitles()throws Exception{List<String> out=new ArrayList<>();for(HomeShelf.Poster p:(List<HomeShelf.Poster>)field("posters"))out.add(p.title);return out;}
    /** The server answers the first two pages (Video, then Movie with Heat and Dune) and then slows right down. */
    void slowAfterMovie(){server.holdAfter=2;server.gate=new CountDownLatch(1);}

    // --- Home's Down row while the list is made
    @Test public void homesRowShowsTitlesAsTheyAreFoundWithOneLineOfProgress()throws Exception{
        slowAfterMovie();openHome();
        press(KeyEvent.KEYCODE_DPAD_DOWN);assertEquals(true,field("shelf"));
        LibraryJourneyTest.until("titles found so far on the row",()->rowTitles().containsAll(Arrays.asList("Heat","Dune")));
        assertEquals(LibraryCollector.Phase.SCANNING,status().phase);
        assertEquals("Scanning your film and TV library — 2 videos found so far. You can keep watching.",field("shelfLine"));
        server.gate.countDown();
        LibraryJourneyTest.until("the rest once the job is done",()->rowTitles().contains("Frozen"));
        LibraryJourneyTest.until("no key: the row says why there are no posters",()->Posters.ROW_HINT.equals(field("shelfLine")));
    }
    @Test public void beforeAnyFolderIsSavedTheRowStillOffersSetUp()throws Exception{
        LibraryFolders.save(app,FakeServer.source(),Collections.emptyList());openHome();
        press(KeyEvent.KEYCODE_DPAD_DOWN);LibraryJourneyTest.until("loaded",()->!(Boolean)field("postersLoading"));
        assertTrue(rowTitles().isEmpty());assertEquals("",field("shelfLine"));assertEquals(false,field("libraryReady"));
        assertEquals(LibraryCollector.Phase.IDLE,status().phase);
    }
    @Test public void withNoKeyOkOnTheRowsLineOpensFilmPosters()throws Exception{
        LibraryIndex.refresh(app,LibraryFolders.library(app),true,null);openHome();
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        LibraryJourneyTest.until("the no-key line",()->Posters.ROW_HINT.equals(field("shelfLine")));
        press(KeyEvent.KEYCODE_DPAD_UP);assertEquals("Up reaches the line, the row stays",true,field("shelf"));assertEquals(true,field("shelfHint"));
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent next=Shadows.shadowOf(controller.get()).getNextStartedActivity();assertNotNull(next);
        assertEquals(SettingsActivity.class.getName(),next.getComponent().getClassName());
        assertEquals(SettingsActivity.PHONE_TMDB,next.getStringExtra("task"));assertEquals(SettingsActivity.LIBRARY,next.getIntExtra("category",-1));
        press(KeyEvent.KEYCODE_DPAD_DOWN);assertEquals("Down goes back to the titles",false,field("shelfHint"));
        press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_DPAD_UP);assertEquals("Up again closes the row",false,field("shelf"));
    }
    @Test public void kidsSeeNoKeyLine()throws Exception{
        LibraryFolders.save(app,FakeServer.source(),Arrays.asList(new LibraryFolders.Folder("v","Video",null,false),new LibraryFolders.Folder("k","Kids",Collections.singletonList("v"),true)));
        LibraryIndex.refresh(app,LibraryFolders.library(app),true,null);
        app.getSharedPreferences("unified-tv-preview",0).edit().putString("profile","child").commit();openHome();
        press(KeyEvent.KEYCODE_DPAD_DOWN);LibraryJourneyTest.until("Kids titles",()->rowTitles().contains("Frozen"));LibraryJourneyTest.idle();
        assertEquals("",field("shelfLine"));
        press(KeyEvent.KEYCODE_DPAD_UP);assertEquals("Up closes the row as before",false,field("shelf"));
    }

    // --- the library while the list is made, and Back
    @Test public void theLibraryShowsTitlesSoFarAndBackLeavesTheJobRunning()throws Exception{
        slowAfterMovie();
        var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup();DlnaLibraryActivity a=c.get();
        LibraryJourneyTest.until("titles found so far, not the counting page",()->a.catalogue()!=null&&a.catalogue().titles.size()==2);
        assertFalse(LibraryJourneyTest.says(a,"Finding your films and TV…"));
        LibraryJourneyTest.until("one progress line with the background words",()->LibraryJourneyTest.says(a,"Finding your films… 2 so far. "+LibraryCollector.BACKGROUND));
        a.onBackPressed();c.pause().stop().destroy();
        assertEquals("still collecting after Back",LibraryCollector.Phase.SCANNING,status().phase);
        server.gate.countDown();
        LibraryJourneyTest.until("finished",()->status().phase==LibraryCollector.Phase.DONE);
        assertEquals(3,LibraryIndex.cached(app,LibraryFolders.library(app)).size());
    }
    @Test public void theCountingPageOnlyWhileNothingIsFoundAndItSaysBackIsFine()throws Exception{
        server.gate=new CountDownLatch(1);
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            LibraryJourneyTest.until("the counting page",()->LibraryJourneyTest.says(a,"Finding your films and TV…"));
            assertTrue(LibraryJourneyTest.says(a,"Your titles appear here as they are found. "+LibraryCollector.BACKGROUND));
            server.gate.countDown();
            LibraryJourneyTest.until("rows",()->a.catalogue()!=null&&a.catalogue().titles.size()==3);
        }
    }

    // --- backgrounds on focus
    @Test public void aFocusedTitleOnHomesRowBringsItsOwnBackdrop()throws Exception{
        Posters.testKey=KEY;LibraryIndex.refresh(app,LibraryFolders.library(app),true,null);openHome();
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        LibraryJourneyTest.until("the row",()->!rowTitles().isEmpty());
        int heat=rowTitles().indexOf("Heat");for(int i=0;i<heat;i++)press(KeyEvent.KEYCODE_DPAD_RIGHT);
        @SuppressWarnings("unchecked") HomeShelf.Poster p=((List<HomeShelf.Poster>)field("posters")).get(heat);
        Object backdrops=field("backdrops");Method get=backdrops.getClass().getDeclaredMethod("get",String.class);get.setAccessible(true);
        LibraryJourneyTest.until("Heat's own backdrop behind the row",()->get.invoke(backdrops,"poster:"+p.id)!=null);
        assertTrue("TMDB's backdrop, not another picture: "+tmdb,tmdb.stream().anyMatch(u->u.contains("/w780/heatb.jpg")));
        assertEquals("poster:"+p.id,field("scene"));
    }
    @Test public void aFocusedTitleInTheLibraryBringsItsOwnBackdrop()throws Exception{
        Posters.testKey=KEY;LibraryIndex.refresh(app,LibraryFolders.library(app),true,null);
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            LibraryJourneyTest.until("rows",()->!LibraryJourneyTest.all(a.getWindow().getDecorView(),PosterView.class).isEmpty());
            PosterView heat=null;for(PosterView v:LibraryJourneyTest.all(a.getWindow().getDecorView(),PosterView.class))if(v.card!=null&&v.card.name().equals("Heat"))heat=v;
            assertNotNull(heat);heat.requestFocus();
            LibraryJourneyTest.until("the backdrop shows",()->{for(ImageView v:LibraryJourneyTest.all(a.getWindow().getDecorView(),ImageView.class))if(v.getScaleType()==ImageView.ScaleType.CENTER_CROP&&v.getDrawable()!=null)return true;return false;});
            assertTrue("TMDB's backdrop: "+tmdb,tmdb.stream().anyMatch(u->u.contains("/w780/heatb.jpg")));
        }
    }

    // --- tile text
    @Test public void aLongTitleStaysInsideItsTileAt1080p(){
        String title="Guardians of the Galaxy Vol. 3";
        // Home's tile: 186 px wide, 16 px padding each side; a bold sans at 26 px is about 0.56 em a character.
        java.util.function.BiFunction<Float,String,Double> at=(size,t)->t.length()*size*0.56;
        float inner=186-32,size=TileText.size(title,inner,26,18,at);
        List<String> lines=TileText.lines(title,inner,TileText.LINES,t->at.apply(size,t));
        assertTrue(lines.toString(),lines.size()<=2);for(String l:lines)assertTrue(l+" fits",at.apply(size,l)<=inner);
        assertFalse("no year mixed into the title lines",String.join(" ",lines).contains("2023"));
        List<String> longer=TileText.lines("Guardians of the Galaxy Vol. 3 Extended Director's Cut Special Edition",inner,2,t->at.apply(18f,t));
        assertEquals(2,longer.size());assertTrue(longer.get(1).endsWith("…"));for(String l:longer)assertTrue(l,at.apply(18f,l)<=inner);
        // The library's tile (PosterView) lays out the same way, inside its card.
        LibraryCatalogue.Title t=new LibraryCatalogue.Title();t.id="g";t.name=title;t.year="2023";t.stableId="dlna:u:g";
        PosterView v=new PosterView(app,1f,true);v.show(new LibraryCatalogue.Card(t));v.measure(View.MeasureSpec.makeMeasureSpec(208,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(312,View.MeasureSpec.EXACTLY));
        v.layout(0,0,208,312);v.draw(new Canvas(Bitmap.createBitmap(208,312,Bitmap.Config.ARGB_8888)));
        assertNotNull(v.nameLines());assertTrue(v.nameLines().toString(),v.nameLines().size()<=2&&!v.nameLines().isEmpty());
        assertTrue("inside the card with padding",v.nameWidth()<=208-2*4-28+1);
        for(String l:v.nameLines())assertFalse("never the year: "+l,l.contains("2023"));
    }
}
