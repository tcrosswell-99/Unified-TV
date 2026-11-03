package io.unifiedtv.preview;
import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Owner's Sony BRAVIA 4K UR2 (Android TV 10, 2.3 GB), review build run 106, 3 November 2026: "I went to see the library
 * and it was stuck on 'checking for new films' and locked the TV so I could not move away. It then showed the movies and
 * still kept the message." The library and Home's row never wait on the background job: the saved list's locks held by a
 * walk or a long save, the file, the encrypted folders and the network all stay off the main thread (OffMain.strict makes
 * any of them on the main thread fail here), the remote answers within a frame budget, and the checking line always ends:
 * when the check finishes, fails, is cancelled or breaks, when the screen comes back, and after two minutes without a step.
 * Pretend media server: no network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class LibraryResponsiveTest {
    /** Main-thread time allowed for opening the screen or handling one key (Robolectric's main thread, generous). */
    static final long BUDGET_MS=250;
    /**
     * Opening the screen (Robolectric inflates it, which takes a few hundred ms on its own): a wait on a held lock would
     * take the whole hold (30 s), so this still tells the two apart.
     */
    static final long OPEN_BUDGET_MS=1000;
    /** Classes and resources loaded once, so the timings that follow are the screen's own. */
    void warmUp()throws Exception{
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){LibraryJourneyTest.until("warm",()->c.get().catalogue()!=null);}   // its worker done
        LibraryCollector.reset();}
    LibraryJourneyTest.MapStore store;FakeServer server;Context app;
    final ExecutorService helper=Executors.newCachedThreadPool();final List<CountDownLatch> held=new ArrayList<>();
    /** Set part-way through a check: every folder below the top one fails this way from then on. */
    volatile Throwable trouble;

    @Before public void start()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",RemoteJourneyTest.SEEDED).putString(MediaSources.PREFERRED,MediaSources.FOLDERS).commit();
        store=new LibraryJourneyTest.MapStore();LibraryFolders.testStore=store;
        server=new FakeServer().folder("0","v","Video").folder("v","m","Movie").folder("v","k","Kids")
            .video("m","heat","Heat (1995).mkv").video("m","dune","Dune (2021).mkv").video("k","frozen","Frozen (2013).mkv","PG");
        for(int i=0;i<10;i++)server.video("m","x"+i,"Film "+i+" ("+(1980+i)+").mkv");
        FakeServer fake=server;
        LibraryIndex.browser=(src,id,off)->{DlnaClient.Page p=fake.browse(src,id,off);Throwable t=trouble;
            if(t!=null&&!id.equals("v")){if(t instanceof Error)throw (Error)t;throw (Exception)t;}return p;};
        Posters.reset();Posters.testKey="";Posters.transport=(u,h,l)->{throw new java.io.IOException("no network in tests");};
        LibraryIndex.clear(app);LibraryCollector.reset();
        LibraryFolders.save(app,FakeServer.source(),Collections.singletonList(new LibraryFolders.Folder("v","Video",null,false)));
        off(()->LibraryIndex.refresh(app,LibraryFolders.library(app),true,null));   // a saved list of 13 titles
    }
    @After public void stop(){
        OffMain.strict=false;for(CountDownLatch l:held)l.countDown();helper.shutdownNow();
        CountDownLatch g=server.gate;if(g!=null)g.countDown();
        LibraryCollector.reset();LibraryCollector.stallMs=120_000;LibraryFolders.testStore=null;LibraryIndex.browser=DlnaClient::browse;
        Posters.testKey=null;Posters.transport=null;Posters.reset();LibraryIndex.clear(app);
    }
    interface Work<T>{T run()throws Exception;}
    /** Runs on a worker thread (as the app does), never on the main thread. */
    <T> T off(Work<T> w)throws Exception{return helper.submit(w::run).get(10,TimeUnit.SECONDS);}
    LibraryCollector.Status status(){return LibraryCollector.get(app).status();}
    LibraryFolders.Library lib()throws Exception{return off(()->LibraryFolders.library(app));}
    /** Holds one of LibraryIndex's locks on another thread, as a walk or a long save on a slow TV would. */
    void hold(String lock)throws Exception{
        Field f=LibraryIndex.class.getDeclaredField(lock);f.setAccessible(true);Object o=f.get(null);
        CountDownLatch taken=new CountDownLatch(1),release=new CountDownLatch(1);held.add(release);
        helper.execute(()->{synchronized(o){taken.countDown();try{release.await(30,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}});
        assertTrue(taken.await(5,TimeUnit.SECONDS));
    }
    void release(){for(CountDownLatch l:held)l.countDown();}
    /**
     * One key as the remote sends it: to the window first, then (not handled there) the focus move ViewRootImpl makes for
     * an arrow; Back as the activity hears it. Returns the main thread's time for it, queued work included.
     */
    static long press(android.app.Activity a,int code){
        long t0=System.nanoTime();
        if(code==KeyEvent.KEYCODE_BACK)a.onBackPressed();
        else{
            boolean handled=a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));
            int dir=code==KeyEvent.KEYCODE_DPAD_RIGHT?View.FOCUS_RIGHT:code==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:code==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN;
            View f=a.getWindow().getDecorView().findFocus();
            if(!handled&&f!=null){View n=f.focusSearch(dir);if(n!=null)n.requestFocus(dir);}
        }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        return (System.nanoTime()-t0)/1_000_000;
    }
    static void dayOld(Context app)throws Exception{
        java.io.File f=LibraryIndex.file(app,FakeServer.source());LibraryIndex.Index i=LibraryIndex.parse(new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8"));
        for(LibraryIndex.Root r:i.roots.values())r.at-=24*60*60_000L;java.nio.file.Files.write(f.toPath(),LibraryIndex.json(i).getBytes("UTF-8"));LibraryIndex.trim();
    }

    // --- 1. the remote never waits on the job
    @Test public void theStrictCheckCatchesDiskWorkOnTheMainThread()throws Exception{
        OffMain.strict=true;
        LibraryFolders.Library l=LibraryFolders.library(store.read(LibraryFolders.STORE),new org.json.JSONObject(),false);
        LibraryIndex.trim();   // not in memory: it would have to be read from storage
        try{LibraryIndex.cached(app,l);fail("the saved list read on the main thread");}catch(IllegalStateException expected){}
        try{LibraryFolders.library(app);fail("the encrypted folders read on the main thread");}catch(IllegalStateException expected){}
    }
    @Test public void theLibraryOpensAndAnswersTheRemoteWhileTheJobHoldsItsLocks()throws Exception{
        warmUp();off(()->LibraryIndex.cached(app,LibraryFolders.library(app)));   // the list already in memory (Home's row read it)
        hold("WALK");hold("DISK");   // a check under way, in the middle of a long save to slow storage
        OffMain.strict=true;
        long t0=System.nanoTime();
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            long opened=(System.nanoTime()-t0)/1_000_000;assertTrue("opened in "+opened+" ms",opened<OPEN_BUDGET_MS);
            LibraryJourneyTest.until("the saved titles, without waiting for the save",()->a.catalogue()!=null&&a.catalogue().titles.size()==13);
            View before=a.getWindow().getDecorView().findFocus();assertTrue("a tile has focus: "+before,before instanceof PosterView);
            long right=press(a,KeyEvent.KEYCODE_DPAD_RIGHT);
            View after=a.getWindow().getDecorView().findFocus();
            assertTrue("Right answered in "+right+" ms",right<BUDGET_MS);assertNotSame("focus moved",before,after);assertTrue(after instanceof PosterView);
            long back=press(a,KeyEvent.KEYCODE_BACK);assertTrue("Back answered in "+back+" ms",back<BUDGET_MS);assertTrue("Back leaves",a.isFinishing());
        }
    }
    @Test public void aColdListBehindALongSaveShowsASkeletonAndBackStillWorks()throws Exception{
        warmUp();LibraryIndex.trim();hold("DISK");OffMain.strict=true;
        long t0=System.nanoTime();
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            long opened=(System.nanoTime()-t0)/1_000_000;assertTrue("opened in "+opened+" ms",opened<OPEN_BUDGET_MS);
            LibraryJourneyTest.idle();
            assertTrue("a skeleton row while the list is read",a.skeleton());
            View f=a.getWindow().getDecorView().findFocus();assertNotNull("focus is never lost",f);
            long down=press(a,KeyEvent.KEYCODE_DPAD_DOWN);assertTrue("Down answered in "+down+" ms",down<BUDGET_MS);
            release();
            LibraryJourneyTest.until("the saved titles once the save is done",()->a.catalogue()!=null&&a.catalogue().titles.size()==13&&!a.skeleton());
        }
        // Behind a long save again: Back leaves at once.
        LibraryIndex.trim();hold("DISK");
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();LibraryJourneyTest.idle();
            long back=press(a,KeyEvent.KEYCODE_BACK);assertTrue("Back answered in "+back+" ms",back<BUDGET_MS);assertTrue(a.isFinishing());
        }
    }
    @Test public void homesRowAndOpeningTheLibraryDoNoDiskWorkOnTheMainThread()throws Exception{
        ActivityController<UnifiedPreviewActivity> home=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();
        try{
            View surface=RemoteJourneyTest.homeIn(home.get());Method key=surface.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);
            OffMain.strict=true;dayOld(app);hold("DISK");   // a check about to start, the list behind a long save
            long t0=System.nanoTime();
            key.invoke(surface,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));key.invoke(surface,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN));
            LibraryJourneyTest.idle();long ms=(System.nanoTime()-t0)/1_000_000;assertTrue("the row opened in "+ms+" ms",ms<BUDGET_MS);
            release();
            Field posters=surface.getClass().getDeclaredField("posters");posters.setAccessible(true);
            LibraryJourneyTest.until("the row's titles",()->((List<?>)posters.get(surface)).size()>=3);
            // OK on the row's end card (Launch.library): straight to Films & TV, no encrypted read on the way.
            Launch.library(home.get());
            android.content.Intent next=org.robolectric.Shadows.shadowOf(home.get()).getNextStartedActivity();
            assertEquals(DlnaLibraryActivity.class.getName(),next.getComponent().getClassName());
            LibraryJourneyTest.until("the check is done",()->status().phase==LibraryCollector.Phase.DONE);
        }finally{OffMain.strict=false;home.pause().stop().destroy();}
    }

    // --- 2. "Checking for new films…" always ends
    /** Opens the library on a day-old list, so a check starts; the media server answers {@code answered} pages, then waits. */
    ActivityController<DlnaLibraryActivity> checking(int answered)throws Exception{
        dayOld(app);server.gate=new CountDownLatch(1);server.holdAfter=server.calls.size()+answered;
        ActivityController<DlnaLibraryActivity> c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup();DlnaLibraryActivity a=c.get();
        LibraryJourneyTest.until("checking, the saved list on screen",()->a.catalogue()!=null&&status().checking&&LibraryJourneyTest.says(a,LibraryCollector.CHECKING));
        return c;
    }
    void lineGone(DlnaLibraryActivity a)throws Exception{
        LibraryJourneyTest.until("the checking line has gone",()->!LibraryJourneyTest.says(a,LibraryCollector.CHECKING));
        assertNotEquals(LibraryCollector.CHECKING,HomeShelf.line(status(),false,true,true));
        assertEquals("the saved titles still there",13,a.catalogue().titles.size());
    }
    @Test public void endsWhenTheCheckFinishes()throws Exception{
        try(var c=checking(1)){server.gate.countDown();LibraryJourneyTest.until("done",()->status().phase==LibraryCollector.Phase.DONE);lineGone(c.get());}
    }
    @Test public void endsWhenTheServerStopsAnswering()throws Exception{
        LibraryCollector.relocator=(c,s)->null;
        try(var c=checking(1)){
            trouble=new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE);server.gate.countDown();
            LibraryJourneyTest.until("ended",()->!status().busy());lineGone(c.get());
        }finally{LibraryCollector.relocator=DlnaClient::relocate;}
    }
    @Test public void endsWhenSomethingBreaks()throws Exception{
        try(var c=checking(1)){
            trouble=new LibraryKeptTest.Killed();server.gate.countDown();
            LibraryJourneyTest.until("the job is no longer busy",()->!status().busy());assertEquals(LibraryCollector.Phase.IDLE,status().phase);lineGone(c.get());
        }
    }
    @Test public void endsWhenCancelled()throws Exception{
        try(var c=checking(1)){
            off(()->{LibraryIndex.clear(app);return null;});   // Privacy: Clear the saved list
            server.gate.countDown();LibraryJourneyTest.until("stopped",()->!status().busy());
            LibraryJourneyTest.until("the line has gone",()->!LibraryJourneyTest.says(c.get(),LibraryCollector.CHECKING));
        }
    }
    @Test public void endsWhenTheScreenComesBackEvenIfItMissedTheNews()throws Exception{
        try(var c=checking(1)){
            c.pause().stop();
            // The screen hears nothing while it is away (as if its listener had gone with onPause).
            Field f=LibraryCollector.class.getDeclaredField("listeners");f.setAccessible(true);Collection<?> listeners=(Collection<?>)f.get(LibraryCollector.get(app));List<Object> kept=new ArrayList<>(listeners);listeners.clear();
            server.gate.countDown();LibraryJourneyTest.until("done while away",()->status().phase==LibraryCollector.Phase.DONE);LibraryJourneyTest.idle();
            assertTrue("still showing the old line while away",LibraryJourneyTest.says(c.get(),LibraryCollector.CHECKING));
            c.restart().start().resume();LibraryJourneyTest.idle();
            assertFalse("read again on resume",LibraryJourneyTest.says(c.get(),LibraryCollector.CHECKING));
            @SuppressWarnings("unchecked") Collection<Object> back=(Collection<Object>)listeners;back.addAll(kept);
        }
    }
    @Test public void aCheckWithNoStepForTwoMinutesStopsSayingSo()throws Exception{
        LibraryCollector.stallMs=2000;
        try(var c=checking(1)){DlnaLibraryActivity a=c.get();
            // The media server stops answering mid-page and nothing moves.
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2500));
            LibraryJourneyTest.until("no line, no spinner",()->status().stalled&&!LibraryJourneyTest.says(a,LibraryCollector.CHECKING));
            assertEquals("Home's row says nothing either","",status().home());assertEquals("",status().line());
            assertEquals("the saved titles still there",13,a.catalogue().titles.size());
            server.gate.countDown();   // it moves again, then finishes
            LibraryJourneyTest.until("done, and the line gone",()->status().phase==LibraryCollector.Phase.DONE&&!status().stalled&&!LibraryJourneyTest.says(a,LibraryCollector.CHECKING));
        }
    }
}
