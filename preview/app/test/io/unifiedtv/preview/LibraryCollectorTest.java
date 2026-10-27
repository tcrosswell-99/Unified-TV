package io.unifiedtv.preview;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Owner's TCL, build run 99: saving the folders starts collecting at once, in the background. Save makes the list and
 * then queues the posters; leaving the sheet (or Back in the library) never stops it; with no TMDB key only the list is
 * made and the sheet says once how to add one; a key added later starts the posters; memory running short pauses it.
 * Fake media server and fake TMDB: no network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class LibraryCollectorTest {
    static final String KEY="0123456789abcdef0123456789abcdef";
    LibraryJourneyTest.MapStore store;FakeServer server;Context app;final List<String> tmdb=Collections.synchronizedList(new ArrayList<>());byte[] jpeg;

    @Before public void start(){
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        store=new LibraryJourneyTest.MapStore();LibraryFolders.testStore=store;
        server=new FakeServer().folder("0","v","Video").folder("v","m","Movie").folder("v","k","Kids")
            .video("m","heat","Heat (1995).mkv").video("m","dune","Dune (2021).mkv").video("k","frozen","Frozen (2013).mkv","PG");
        LibraryIndex.browser=server;LibrarySetupActivity.finder=(c,r)->Collections.singletonList(FakeServer.device());
        Bitmap b=Bitmap.createBitmap(30,45,Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,80,out);jpeg=out.toByteArray();
        Posters.reset();Posters.testKey="";
        Posters.transport=(url,headers,limit)->{tmdb.add(url);assertTrue(url,Tmdb.allowed(url));
            if(url.contains("image.tmdb.org"))return new Tmdb.Response(200,jpeg,0);
            try{JSONArray r=new JSONArray();
                if(url.contains("query=Heat"))r.put(new JSONObject().put("id",949).put("title","Heat").put("release_date","1995-12-15").put("poster_path","/heat.jpg").put("backdrop_path","/heatb.jpg").put("vote_count",7000).put("overview","A heist."));
                return new Tmdb.Response(200,new JSONObject().put("results",r).toString().getBytes("UTF-8"),0);}catch(Exception e){throw new java.io.IOException(e);}};
        LibraryIndex.clear(app);LibraryCollector.reset();
    }
    @After public void stop(){
        CountDownLatch g=server.gate;if(g!=null)g.countDown();
        LibraryCollector.reset();LibraryCollector.pauseMs=30_000;LibraryFolders.testStore=null;LibraryIndex.browser=DlnaClient::browse;
        Posters.testKey=null;Posters.transport=null;Posters.reset();Vault.testKey=null;LibraryIndex.clear(app);
    }
    LibraryCollector.Status status(){return LibraryCollector.get(app).status();}
    void saveLibrary()throws Exception{LibraryFolders.save(app,FakeServer.source(),Collections.singletonList(new LibraryFolders.Folder("v","Video",null,false)));}
    int saved(){return LibraryIndex.cached(app,LibraryFolders.library(app)).size();}
    /** Chooses Video in the sheet and presses Save. */
    void chooseAndSave(LibrarySetupActivity a)throws Exception{chooseAndSave(a,null);}
    void chooseAndSave(LibrarySetupActivity a,Runnable beforeSave)throws Exception{
        LibraryJourneyTest.until("the server's folders",()->LibraryJourneyTest.button(a,"Video")!=null);
        LibraryJourneyTest.button(a,"Video").performClick();LibraryJourneyTest.until("inside Video",()->LibraryJourneyTest.button(a,"Add this folder")!=null);
        LibraryJourneyTest.button(a,"Add this folder").performClick();LibraryJourneyTest.idle();
        if(beforeSave!=null)beforeSave.run();
        LibraryJourneyTest.button(a,"Save changes").performClick();LibraryJourneyTest.idle();
        assertEquals(LibrarySetupActivity.Page.SAVED,a.page());
    }

    @Test public void saveMakesTheListThenLooksUpThePosters()throws Exception{
        Posters.testKey=KEY;
        try(var c=Robolectric.buildActivity(LibrarySetupActivity.class).setup()){LibrarySetupActivity a=c.get();
            chooseAndSave(a);
            LibraryJourneyTest.until("the list and posters are done",()->status().phase==LibraryCollector.Phase.DONE);
            assertEquals("every video in the saved folder, before Films & TV is opened",3,saved());
            assertTrue("then each title looked up: "+tmdb,tmdb.stream().anyMatch(u->u.contains("query=Heat")));
            assertTrue("the poster kept on the TV",tmdb.stream().anyMatch(u->u.endsWith("/heat.jpg")));
            assertNotNull(Posters.get(app).saved("uuid:test-nas","t:dlna:uuid:test-nas:heat"));
            assertEquals(3,status().postersTotal);assertEquals(3,status().postersDone);
            LibraryJourneyTest.until("the sheet says so",()->LibraryJourneyTest.says(a,"Found 3 videos. Posters are ready."));
            assertFalse("no key hint with a key",LibraryJourneyTest.says(a,LibraryCollector.KEY_HINT));
        }
    }
    @Test public void leavingTheSheetDoesNotStopIt()throws Exception{
        var c=Robolectric.buildActivity(LibrarySetupActivity.class).setup();LibrarySetupActivity a=c.get();
        // The server answers the collector's first page, then slows right down.
        chooseAndSave(a,()->{server.holdAfter=server.calls.size()+1;server.gate=new CountDownLatch(1);});
        LibraryJourneyTest.until("collecting",()->status().phase==LibraryCollector.Phase.SCANNING);
        LibraryJourneyTest.until("the sheet says how far, and that it carries on",()->LibraryJourneyTest.says(a,"Finding your films")&&LibraryJourneyTest.says(a,LibraryCollector.BACKGROUND));
        a.onBackPressed();c.pause().stop().destroy();assertTrue(a.isDestroyed());
        server.gate.countDown();
        LibraryJourneyTest.until("finished without the sheet",()->status().phase==LibraryCollector.Phase.DONE);
        assertEquals(3,saved());
    }
    @Test public void noKeyMakesTheListOnlyAndSaysOnceHowToAddOne()throws Exception{
        try(var c=Robolectric.buildActivity(LibrarySetupActivity.class).setup()){LibrarySetupActivity a=c.get();
            chooseAndSave(a);
            LibraryJourneyTest.until("the list is done",()->status().phase==LibraryCollector.Phase.DONE);
            assertEquals(3,saved());assertTrue(status().noKey);assertTrue("TMDB never asked",tmdb.isEmpty());
            LibraryJourneyTest.until("the hint",()->LibraryJourneyTest.says(a,LibraryCollector.KEY_HINT));
            assertTrue(LibraryJourneyTest.says(a,LibraryCollector.KEY_HINT+". You add it from your phone: Settings > Library & Media > Film posters."));
            LibraryJourneyTest.button(a,"Add your TMDB key from your phone").performClick();
            Intent next=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(SettingsActivity.class.getName(),next.getComponent().getClassName());
            assertEquals(SettingsActivity.PHONE_TMDB,next.getStringExtra("task"));
        }
        // Said once on this TV.
        try(var c=Robolectric.buildActivity(LibrarySetupActivity.class).setup()){LibrarySetupActivity a=c.get();LibraryJourneyTest.idle();
            LibraryJourneyTest.button(a,"Video").performClick();LibraryJourneyTest.idle();
            LibraryJourneyTest.button(a,"This folder is for Kids").performClick();LibraryJourneyTest.idle();a.onBackPressed();LibraryJourneyTest.idle();
            LibraryJourneyTest.button(a,"Save changes").performClick();LibraryJourneyTest.idle();
            LibraryJourneyTest.until("done again",()->status().phase==LibraryCollector.Phase.DONE&&status().pass==2);Thread.sleep(100);LibraryJourneyTest.idle();
            assertFalse("not said a second time",LibraryJourneyTest.says(a,LibraryCollector.KEY_HINT));
        }
    }
    @Test public void aKeyAddedLaterStartsThePosters()throws Exception{
        saveLibrary();Posters.testKey=null;Vault.testKey=javax.crypto.KeyGenerator.getInstance("AES").generateKey();
        LibraryCollector.get(app).start();
        LibraryJourneyTest.until("the list, no key",()->status().phase==LibraryCollector.Phase.DONE);
        assertTrue(status().noKey);assertTrue(tmdb.isEmpty());
        TmdbKey.save(app,KEY);   // phone setup saves the household's key
        LibraryJourneyTest.until("the posters are looked up",()->tmdb.stream().anyMatch(u->u.contains("query=Heat")));
        LibraryJourneyTest.until("done",()->status().phase==LibraryCollector.Phase.DONE&&!status().noKey);
        assertEquals(3,status().postersDone);
    }
    @Test public void memoryRunningShortPausesItUntilMemoryComesBack()throws Exception{
        saveLibrary();server.gate=new CountDownLatch(1);server.holdAfter=1;
        LibraryCollector job=LibraryCollector.get(app);job.start();
        LibraryJourneyTest.until("the first page answered, the second held",()->server.calls.size()==1);Thread.sleep(50);
        RuntimeEnvironment.getApplication().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);   // Android: memory is short
        server.gate.countDown();
        LibraryJourneyTest.until("paused",()->status().paused);
        assertTrue("the line says why",status().line().contains("resting while the TV needs its memory"));
        int asked=server.calls.size();Thread.sleep(300);assertEquals("nothing more asked of the media server while paused",asked,server.calls.size());
        assertNotEquals(LibraryCollector.Phase.DONE,status().phase);
        job.resume();
        LibraryJourneyTest.until("carries on and finishes",()->status().phase==LibraryCollector.Phase.DONE);
        assertFalse(status().paused);assertEquals(3,saved());
        // Leaving a screen (UI hidden) is not memory pressure.
        LibraryCollector.Status before=status();RuntimeEnvironment.getApplication().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);job.start();
        LibraryJourneyTest.until("goes round again unpaused",()->status().pass==before.pass+1&&status().phase==LibraryCollector.Phase.DONE);
    }
    @Test public void progressWordsSayItCarriesOnInTheBackground(){
        LibraryCollector.Status scan=new LibraryCollector.Status(1,LibraryCollector.Phase.SCANNING,2000,0,0,false,false,false,0);
        assertEquals("Finding your films… 2,000 so far. This carries on in the background — you can press Back.",scan.line());
        assertEquals("Finding your films… 2,000 so far — you can carry on.",scan.setup());
        assertEquals("Scanning your film and TV library — 2,000 videos found so far. You can keep watching.",scan.home());
        LibraryCollector.Status art=new LibraryCollector.Status(1,LibraryCollector.Phase.POSTERS,200,80,200,false,false,false,0);
        assertEquals("Getting posters: 80 of 200. "+LibraryCollector.BACKGROUND,art.line());
        LibraryCollector.Status idle=new LibraryCollector.Status(1,LibraryCollector.Phase.DONE,200,200,200,false,false,false,0);
        assertEquals("",idle.line());assertEquals("",idle.home());
        assertEquals("Kids see no key line",scan.home(),HomeShelf.line(scan,true,true,false));
        assertEquals(Posters.ROW_HINT,HomeShelf.line(idle,false,true,false));assertEquals("",HomeShelf.line(idle,true,true,false));
        assertEquals("",HomeShelf.line(idle,false,true,true));
    }

    // --- review, 27 October 2026: the job can be stopped part-way, and the match list is not rewritten after every batch
    final List<String> events=Collections.synchronizedList(new ArrayList<>());volatile CountDownLatch hold;final CountDownLatch held=new CountDownLatch(1);
    /** A library of 22 films in Movie (three poster batches) and Kids beside it; the first TMDB search waits for {@code hold}. */
    void bigLibrary(String... folders)throws Exception{
        server=new FakeServer().folder("0","m","Movie").folder("0","k","Kids").video("m","heat","Heat (1995).mkv").video("k","frozen","Frozen (2013).mkv","PG");
        for(int i=0;i<21;i++)server.video("m","x"+i,"Film "+i+" ("+(1980+i)+").mkv");
        FakeServer fake=server;LibraryIndex.browser=(src,id,off)->{events.add("dlna:"+id);return fake.browse(src,id,off);};
        Tmdb.Transport answer=Posters.transport;
        Posters.transport=(url,headers,limit)->{
            if(url.contains("/search/")){events.add("search");CountDownLatch h=hold;if(h!=null){held.countDown();try{h.await(20,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){throw new java.io.IOException(e);}}}
            else if(url.contains("image.tmdb.org"))events.add("image");
            return answer.get(url,headers,limit);};
        List<LibraryFolders.Folder> chosen=new ArrayList<>();for(String f:folders)chosen.add(new LibraryFolders.Folder(f,f.equals("m")?"Movie":"Kids",null,false));
        LibraryFolders.save(app,FakeServer.source(),chosen);
    }
    long count(String what){synchronized(events){return events.stream().filter(what::equals).count();}}
    @Test public void foldersSavedAgainMidPostersAreWalkedNext()throws Exception{
        Posters.testKey=KEY;bigLibrary("m");hold=new CountDownLatch(1);
        LibraryCollector job=LibraryCollector.get(app);job.start();
        assertTrue("a poster lookup under way",held.await(10,java.util.concurrent.TimeUnit.SECONDS));
        LibraryFolders.save(app,FakeServer.source(),Arrays.asList(new LibraryFolders.Folder("m","Movie",null,false),new LibraryFolders.Folder("k","Kids",null,false)));
        job.start();   // the household adds Kids while posters are being looked up
        hold.countDown();
        LibraryJourneyTest.until("the new folder walked and everything done",()->events.contains("dlna:k")&&status().phase==LibraryCollector.Phase.DONE);
        int k=events.indexOf("dlna:k");long before;synchronized(events){before=events.subList(0,k).stream().filter("search"::equals).count();}
        // A title TMDB doesn't know is searched twice (with its year, then without), so a batch is at most twice its size.
        assertTrue("Kids walked after at most the batch under way ("+before+" searches), not after all 22 titles",before<=2*MemoryBudget.current().posterBatch());
        assertEquals("then every title looked up",23,status().postersTotal);assertEquals(23,status().postersDone);
    }
    @Test public void aResetMidWalkLeavesNothingWrittenBack()throws Exception{
        saveLibrary();server.gate=new CountDownLatch(1);server.holdAfter=1;
        LibraryCollector.get(app).start();
        LibraryJourneyTest.until("walking, the second page held",()->server.calls.size()==1);Thread.sleep(50);
        LibraryIndex.clear(app);   // Privacy: forget the saved list (also a reset, or forgetting the media server)
        server.gate.countDown();
        LibraryJourneyTest.until("stopped",()->!status().busy());Thread.sleep(200);
        java.io.File[] files=new java.io.File(app.getFilesDir(),"library").listFiles();
        assertTrue("nothing written back: "+Arrays.toString(files),files==null||files.length==0);
        assertEquals(0,LibraryIndex.latest(app,LibraryFolders.library(app)).size());
        assertEquals(LibraryCollector.Phase.IDLE,status().phase);
    }
    @Test public void clearingPostersMidLoopStopsTheDownloads()throws Exception{
        Posters.testKey=KEY;bigLibrary("m");hold=new CountDownLatch(1);
        LibraryCollector.get(app).start();
        assertTrue(held.await(10,java.util.concurrent.TimeUnit.SECONDS));
        PrivacyData.clearPosters(app);   // Privacy & Data: Clear film posters and details
        hold.countDown();
        LibraryJourneyTest.until("stopped",()->status().phase==LibraryCollector.Phase.DONE);Thread.sleep(200);
        assertTrue("only the lookup already under way (one title: at most two searches): "+count("search"),count("search")<=2);assertEquals("no picture fetched again",0,count("image"));
        assertArrayEquals("nothing saved again",new int[]{0,0},Posters.get(app).matchCounts());
    }
    @Test public void theMatchListIsNotRewrittenAfterEveryBatch()throws Exception{
        Posters.testKey=KEY;bigLibrary("m");
        LibraryCollector.get(app).start();
        LibraryJourneyTest.until("done",()->status().phase==LibraryCollector.Phase.DONE);
        assertEquals(22,status().postersTotal);
        assertTrue("at most the first match and the final save, not one per batch: "+Posters.get(app).matchWrites(),Posters.get(app).matchWrites()<=2);
        Posters.reset();assertEquals("everything found was written when the job finished",22,Posters.get(app).matchCounts()[1]);
    }
}
