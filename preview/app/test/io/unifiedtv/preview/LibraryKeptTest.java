package io.unifiedtv.preview;
import android.content.Context;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Owner's TCL, 3 November 2026: about 2,000 films and episodes were found and shown; he left the app, and a day later Films
 * &amp; TV had nothing and started again from zero. The library is scanned once and kept on the TV: it survives the app
 * being stopped (part-way through a walk too, and carries on from there), a cache wipe, and the NAS moving address or
 * port; a later check shows the saved list throughout and only then adds new titles and removes deleted ones; a server
 * going quiet part-way removes nothing. Pretend media server: no network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class LibraryKeptTest {
    LibraryJourneyTest.MapStore store;FakeServer server;Context app;

    @Before public void start()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        store=new LibraryJourneyTest.MapStore();LibraryFolders.testStore=store;
        server=new FakeServer().folder("0","v","Video").folder("v","m","Movie").folder("v","k","Kids")
            .video("m","heat","Heat (1995).mkv").video("m","dune","Dune (2021).mkv").video("k","frozen","Frozen (2013).mkv","PG");
        LibraryIndex.browser=server;Posters.reset();Posters.testKey="";Posters.transport=(u,h,l)->{throw new java.io.IOException("no network in tests");};
        LibraryIndex.clear(app);LibraryCollector.reset();
        LibraryFolders.save(app,FakeServer.source(),Collections.singletonList(new LibraryFolders.Folder("v","Video",null,false)));
    }
    @After public void stop(){
        CountDownLatch g=server.gate;if(g!=null)g.countDown();
        LibraryCollector.reset();LibraryCollector.relocator=DlnaClient::relocate;LibraryIndex.saveMs=8000;
        LibraryFolders.testStore=null;LibraryIndex.browser=DlnaClient::browse;Posters.testKey=null;Posters.transport=null;Posters.reset();LibraryIndex.clear(app);
    }
    LibraryCollector.Status status(){return LibraryCollector.get(app).status();}
    LibraryFolders.Library lib(){return LibraryFolders.library(app);}
    Set<String> ids(LibraryIndex.Index i){Set<String> out=new TreeSet<>();for(LibraryIndex.Root r:i.roots.values())for(LibraryIndex.Entry e:r.entries)out.add(e.id);return out;}
    /** One run of the background job, to the end. */
    void collect()throws Exception{int pass=status().pass;LibraryCollector.get(app).start();LibraryJourneyTest.until("the job has run",()->status().pass>pass&&status().phase==LibraryCollector.Phase.DONE);}
    /** A new process: nothing in memory, a new job; only what is on the TV's storage is left. */
    void restart(){LibraryCollector.reset();LibraryIndex.trim();Posters.reset();}
    /** The saved list, as if it had been made a day ago (so the next visit checks it). */
    void dayOld()throws Exception{
        File f=LibraryIndex.file(app,lib().source);LibraryIndex.Index i=LibraryIndex.parse(new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8"));
        for(LibraryIndex.Root r:i.roots.values())r.at-=24*60*60_000L;
        java.nio.file.Files.write(f.toPath(),LibraryIndex.json(i).getBytes("UTF-8"));LibraryIndex.trim();
    }
    /** Thrown from inside a browse: Android stopping the app there (nothing after it runs, not even a catch). */
    static final class Killed extends Error{Killed(){super("process stopped by Android");}}

    @Test public void theListIsKeptInTheAppsOwnFilesAndSurvivesARestartAndACacheWipe()throws Exception{
        collect();assertEquals(3,LibraryIndex.cached(app,lib()).size());
        File f=LibraryIndex.file(app,lib().source);
        assertTrue("in files/library, never the cache: "+f,f.getPath().startsWith(new File(app.getFilesDir(),"library").getPath())&&f.isFile());
        // Android empties the cache folder when storage runs short, and stops the app in the background.
        File[] cache=app.getCacheDir().listFiles();if(cache!=null)for(File x:cache)assertTrue(x.delete()||!x.exists());
        restart();int asked=server.calls.size();
        assertEquals("read back by a new process",new TreeSet<>(Arrays.asList("dune","frozen","heat")),ids(LibraryIndex.cached(app,lib())));
        // The next visit shows the saved list at once and asks nothing of the media server (it is fresh).
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            LibraryJourneyTest.until("the saved titles at once",()->a.catalogue()!=null&&a.catalogue().titles.size()==3);
            assertFalse("never the counting page",LibraryJourneyTest.says(a,"Finding your films"));
            Thread.sleep(200);LibraryJourneyTest.idle();assertEquals("no walk for a fresh list",asked,server.calls.size());
        }
    }

    @Test public void aWalkCutShortIsKeptAndCarriedOnNotStartedAgain()throws Exception{
        server=new FakeServer().folder("0","v","Video");for(String d:new String[]{"a","b","c"}){server.folder("v",d,"Folder "+d);for(int i=0;i<30;i++)server.video(d,d+i,"Film "+d+i+" ("+(1950+i)+").mkv");}
        FakeServer fake=server;LibraryIndex.saveMs=0;
        LibraryIndex.browser=(src,id,off)->{if((id+"@"+off).equals("b@24"))throw new Killed();return fake.browse(src,id,off);};
        try{LibraryIndex.refresh(app,lib(),false,null);fail("stopped part-way");}catch(Killed expected){}
        restart();
        LibraryIndex.Index kept=LibraryIndex.cached(app,lib());
        assertEquals("Folder a and the first page of b were kept",54,kept.size());
        assertTrue("shown as found so far, and due to carry on",LibraryIndex.due(kept,lib(),System.currentTimeMillis())&&!LibraryIndex.settled(kept,lib()));
        // The next run carries on where it stopped: the rest of b, then c. Nothing already read is asked for again.
        fake.calls.clear();LibraryIndex.browser=fake;
        LibraryIndex.Index done=LibraryIndex.refresh(app,lib(),false,null);
        assertEquals(Arrays.asList("b@24","c@0","c@24"),fake.calls);
        assertEquals(90,done.size());assertEquals(90,ids(done).size());assertTrue(LibraryIndex.settled(done,lib()));
        restart();assertEquals("saved whole",90,LibraryIndex.cached(app,lib()).size());
        assertFalse(LibraryIndex.due(LibraryIndex.cached(app,lib()),lib(),System.currentTimeMillis()));
    }

    @Test public void theJobKeepsWhatItFoundWhenTheAppIsStoppedInTheBackground()throws Exception{
        server.folder("v","z","Zzz").video("z","up","Up (2009).mkv");
        LibraryIndex.saveMs=0;server.gate=new CountDownLatch(1);server.holdAfter=3;   // Video, Movie, Kids answered; then held
        LibraryCollector.get(app).start();
        LibraryJourneyTest.until("the first titles found",()->server.calls.size()>=3&&LibraryIndex.latest(app,lib()).size()==3);
        Thread.sleep(100);
        // Android stops the app here: the job's thread and everything in memory go.
        LibraryCollector.reset();LibraryIndex.trim();server.gate.countDown();
        assertEquals("what was found is on the TV",3,LibraryIndex.cached(app,lib()).size());
    }

    @Test public void aCheckShowsTheSavedListThroughoutThenAddsAndRemoves()throws Exception{
        collect();restart();dayOld();
        // On the NAS meanwhile: one film added, one deleted.
        server.video("m","up","Up (2009).mkv");server.children.get("m").removeIf(i->i.id.equals("dune"));
        List<Integer> shown=Collections.synchronizedList(new ArrayList<>());FakeServer fake=server;
        LibraryIndex.browser=(src,id,off)->{shown.add(LibraryIndex.latest(app,lib()).size());return fake.browse(src,id,off);};
        server.gate=new CountDownLatch(1);server.holdAfter=server.calls.size()+2;
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            LibraryJourneyTest.until("the saved titles at once",()->a.catalogue()!=null&&a.catalogue().titles.size()==3);
            LibraryJourneyTest.until("checking quietly",()->status().checking&&LibraryJourneyTest.says(a,LibraryCollector.CHECKING));
            assertFalse("never counting from zero over the saved list",LibraryJourneyTest.says(a,"Finding your films"));
            assertEquals("the saved list while checking",new TreeSet<>(Arrays.asList("dune","frozen","heat")),ids(LibraryIndex.latest(app,lib())));
            assertEquals("Home's row says the same",LibraryCollector.CHECKING,HomeShelf.line(status(),false,true,true));
            server.gate.countDown();
            LibraryJourneyTest.until("checked",()->status().phase==LibraryCollector.Phase.DONE);
            LibraryJourneyTest.until("the new film in, the deleted one out",()->a.catalogue().titles.size()==3&&ids(LibraryIndex.latest(app,lib())).contains("up"));
        }
        assertEquals(new TreeSet<>(Arrays.asList("frozen","heat","up")),ids(LibraryIndex.latest(app,lib())));
        assertFalse(shown.isEmpty());for(int n:shown)assertEquals("the list was never emptied or cut down while checking: "+shown,3,n);
        restart();assertEquals(new TreeSet<>(Arrays.asList("frozen","heat","up")),ids(LibraryIndex.cached(app,lib())));
    }

    @Test public void aServerThatGoesQuietPartWayRemovesNothing()throws Exception{
        collect();restart();dayOld();
        // The NAS answers the top folder, then goes to sleep (or drops off the network) before its subfolders.
        FakeServer fake=server;
        LibraryIndex.browser=(src,id,off)->{if(!id.equals("v"))throw new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE);return fake.browse(src,id,off);};
        LibraryCollector.relocator=(c,s)->null;
        collect();
        assertEquals("every saved title still there",new TreeSet<>(Arrays.asList("dune","frozen","heat")),ids(LibraryIndex.latest(app,lib())));
        assertTrue("said as a saved list",status().stale);
        restart();assertEquals(3,LibraryIndex.cached(app,lib()).size());
        // A single folder the server can't show (but the server answering) keeps what was saved under it.
        dayOld();LibraryIndex.browser=(src,id,off)->{if(id.equals("k"))throw new DlnaClient.Failure(DlnaClient.Failure.SERVER);return fake.browse(src,id,off);};
        LibraryIndex.Index i=LibraryIndex.refresh(app,lib(),false,null);
        assertEquals(new TreeSet<>(Arrays.asList("dune","frozen","heat")),ids(i));
    }

    @Test public void aNewAddressOrPortForTheSameServerKeepsTheLibrary()throws Exception{
        collect();restart();dayOld();
        // After a NAS restart, DHCP gave it a new address and its media server a new port: the saved one doesn't answer.
        String moved="http://nas2.test:50101/ctl";FakeServer fake=server;List<String> where=Collections.synchronizedList(new ArrayList<>());
        LibraryIndex.browser=(src,id,off)->{where.add(src.optString("control"));if(!src.optString("control").equals(moved))throw new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE);return fake.browse(src,id,off);};
        LibraryCollector.relocator=(c,s)->s.optString("udn").equals("uuid:test-nas")?new JSONObject(s.toString()).put("control",moved).put("location","http://nas2.test:50101/desc.xml"):null;
        assertEquals("shown at once, before anything answers",3,LibraryIndex.cached(app,lib()).size());
        server.video("m","up","Up (2009).mkv");
        collect();
        assertEquals("found at its new address and checked there",moved,where.get(where.size()-1));
        assertEquals("the new address is saved, folders kept",moved,store.read(LibraryFolders.STORE).optString("control"));
        assertEquals(1,store.read(LibraryFolders.STORE).optJSONArray("roots").length());
        assertEquals("same list, plus the new film",new TreeSet<>(Arrays.asList("dune","frozen","heat","up")),ids(LibraryIndex.latest(app,lib())));
        assertFalse(status().stale);
        // Kept by UDN: the same file whatever the address.
        assertEquals(LibraryIndex.file(app,FakeServer.source()),LibraryIndex.file(app,lib().source));
    }

    @Test public void aServerWithoutAUdnKeepsItsListOnANewPort()throws Exception{
        JSONObject a=FakeServer.source().put("udn",""),b=new JSONObject(a.toString()).put("control","http://nas.test:50999/ctl");
        assertEquals("known by its name and host",LibraryIndex.file(app,a),LibraryIndex.file(app,b));
        assertNotEquals(LibraryIndex.file(app,a),LibraryIndex.file(app,new JSONObject(a.toString()).put("name","Another server")));
        // A list saved by an earlier build (named after the full address) is still found.
        LibraryIndex.Index i=new LibraryIndex.Index();LibraryIndex.Root r=new LibraryIndex.Root("v");r.at=System.currentTimeMillis();LibraryIndex.Entry e=new LibraryIndex.Entry();e.id="heat";e.title="Heat";e.url="http|50002|/v/heat.mkv";r.entries.add(e);i.roots.put("v",r);
        File old=new File(new File(app.getFilesDir(),"library"),"index-"+LibraryIndex.hash(a.optString("control"))+".json");
        assertTrue(old.getParentFile().isDirectory()||old.getParentFile().mkdirs());java.nio.file.Files.write(old.toPath(),LibraryIndex.json(i).getBytes("UTF-8"));
        LibraryFolders.save(app,a,Collections.singletonList(new LibraryFolders.Folder("v","Video",null,false)));LibraryIndex.trim();
        assertEquals(1,LibraryIndex.cached(app,lib()).size());assertTrue(LibraryIndex.file(app,a).isFile());
    }

    @Test public void onlyAnExplicitActionClears()throws Exception{
        LibraryFolders.save(app,FakeServer.source(),Arrays.asList(new LibraryFolders.Folder("m","Movie",Collections.singletonList("v"),false),new LibraryFolders.Folder("k","Kids",Collections.singletonList("v"),true)));
        collect();assertEquals(3,LibraryIndex.cached(app,lib()).size());
        // A Kids-only walk (Kids' own library) never drops the Adults' folders from the file.
        LibraryFolders.Library kids=LibraryFolders.library(store.read(LibraryFolders.STORE),store.read(KidsPolicy.STORE),true);
        LibraryIndex.refresh(app,kids,true,null);restart();assertEquals(3,LibraryIndex.cached(app,lib()).size());
        // Memory running short, the screen going, a new job: nothing is cleared.
        RuntimeEnvironment.getApplication().onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);collect();assertEquals(3,LibraryIndex.cached(app,lib()).size());
        // Taking a folder out: its titles leave at once, and leave the file at the next save.
        LibraryFolders.save(app,FakeServer.source(),Collections.singletonList(new LibraryFolders.Folder("k","Kids",Collections.singletonList("v"),true)));
        assertEquals(1,LibraryIndex.latest(app,lib()).size());
        LibraryIndex.refresh(app,lib(),false,null);
        String json=new String(java.nio.file.Files.readAllBytes(LibraryIndex.file(app,lib().source).toPath()),"UTF-8");
        assertFalse("Movie's titles left the TV",json.contains("heat"));assertTrue(json.contains("frozen"));
        // Privacy: clear the saved list.
        PrivacyData.clearLibrary(app);assertEquals(0,LibraryIndex.cached(app,lib()).size());assertFalse(LibraryIndex.file(app,lib().source).exists());
    }

    @Test public void checkingWords(){
        LibraryCollector.Status s=new LibraryCollector.Status(2,LibraryCollector.Phase.SCANNING,2000,0,0,false,false,false,0,true);
        assertEquals(LibraryCollector.CHECKING,s.line());assertEquals(LibraryCollector.CHECKING,s.home());assertEquals(LibraryCollector.CHECKING,s.setup());
        assertEquals(LibraryCollector.CHECKING+" (resting while the TV needs its memory)",s.paused(true).line());
        assertFalse("only while scanning",new LibraryCollector.Status(2,LibraryCollector.Phase.DONE,2000,0,0,false,false,false,0,true).checking);
    }
}
