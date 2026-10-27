package io.unifiedtv.preview;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.InterruptedIOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * The one background job that makes Your Films &amp; TV ready before anyone opens it (owner's TCL, build run 99, 27 October
 * 2026): as soon as the folders are saved (first-run step 4, Settings &gt; Library), it makes the saved list of titles
 * (LibraryIndex) and then, when the household has added its TMDB key, looks each new title's poster and details up
 * (Posters). A key added later (phone setup, TmdbKey.save) starts the poster part on its own.
 *
 * <p>It belongs to the app, not to a screen: leaving the setup sheet, pressing Back in the library or on Home never stops
 * it. Home's row, the library and Settings only read its {@link Status} and the list found so far
 * (LibraryIndex.latest), and hear about changes at most twice a second on the main thread, so focus never waits for it
 * (AGENTS rule 5). One walk at a time for the whole app (LibraryIndex), on one low-priority thread, resting between pages
 * from the media server (MemoryBudget.indexGapMs) and queueing a few poster lookups at a time behind the tile in focus
 * (MemoryBudget.posterBatch). No wakelock: it runs while Unified TV is running and carries on from the saved list next
 * time. When Android says memory is short ({@link ComponentCallbacks2#onTrimMemory}), it pauses where it is and goes on
 * once memory has come back.
 */
final class LibraryCollector {
    enum Phase{IDLE,SCANNING,POSTERS,DONE,FAILED}
    /** Said wherever its progress shows (owner, build run 99: "would Back stop it?"). */
    static final String BACKGROUND="This carries on in the background — you can press Back.";
    static final String KEY_HINT="Add your free TMDB key to see posters";
    /** Listeners hear about changes at most this often. */
    static final long NOTIFY_MS=500;
    /** At most this many posters are kept on the TV ahead of time (about 50 MB); the rest arrive as tiles are shown. */
    static final int WARM_PICTURES=1500;
    /** How long a pause for memory lasts before memory is checked again. */
    static volatile long pauseMs=30_000;

    /** What the job is doing, for the lines on screen. Never changes once made. */
    static final class Status {
        final Phase phase;final int found,postersDone,postersTotal,problem;final boolean noKey,paused,stale;
        /** Which run of the job this is (1, 2…): a screen tells a run it has already seen finish from a new one. */
        final int pass;
        Status(int pass,Phase phase,int found,int postersDone,int postersTotal,boolean noKey,boolean paused,boolean stale,int problem){
            this.pass=pass;this.phase=phase;this.found=Math.max(0,found);this.postersDone=Math.max(0,postersDone);this.postersTotal=Math.max(0,postersTotal);this.noKey=noKey;this.paused=paused;this.stale=stale;this.problem=problem;}
        Status paused(boolean p){return new Status(pass,phase,found,postersDone,postersTotal,noKey,p,stale,problem);}
        boolean scanning(){return phase==Phase.SCANNING;}
        boolean busy(){return phase==Phase.SCANNING||phase==Phase.POSTERS;}
        /** "Finding your films… 120 so far" or "Getting posters: 80 of 200"; empty when nothing is under way. */
        String progress(){
            String p=phase==Phase.SCANNING?(found==0?"Finding your films…":"Finding your films… "+number(found)+" so far")
                :phase==Phase.POSTERS?"Getting posters: "+number(postersDone)+" of "+number(postersTotal):"";
            return p.isEmpty()||!paused?p:p+" (resting while the TV needs its memory)";
        }
        /** The library's and Settings' line: the progress and that it carries on by itself. Empty when idle. */
        String line(){String p=progress();return p.isEmpty()?"":p+". "+BACKGROUND;}
        /** The setup sheet's line after Save. */
        String setup(){
            switch(phase){
                case SCANNING:return progress()+" — you can carry on.";
                case POSTERS:return "Found "+videos(found)+". "+progress()+".";
                case DONE:return "Found "+videos(found)+(postersTotal>0&&postersDone>=postersTotal?". Posters are ready.":".");
                case FAILED:return "The media server isn't answering just now. Unified TV tries again when you open Your Films & TV.";
                default:return "";
            }
        }
        /** Home's row (owner, build run 99): never a bare "View all" while the list is being made. */
        String home(){
            if(phase==Phase.SCANNING)return (found==0?"Scanning your film and TV library…":"Scanning your film and TV library — "+videos(found)+" found so far.")+" You can keep watching.";
            if(phase==Phase.POSTERS)return progress()+". You can keep watching.";
            return "";
        }
    }
    static String number(int n){return String.format(Locale.UK,"%,d",n);}
    static String videos(int n){return number(n)+(n==1?" video":" videos");}

    private static volatile LibraryCollector instance;
    static LibraryCollector get(Context c){
        Context app=c.getApplicationContext();LibraryCollector i=instance;if(i!=null&&i.app==app)return i;
        synchronized(LibraryCollector.class){
            if(instance!=null&&instance.app==app)return instance;
            if(instance!=null)reset();   // JVM tests: each test has an application of its own
            instance=new LibraryCollector(app);return instance;}
    }
    /** Tests only: stop the job and forget it. */
    static void reset(){
        LibraryCollector i;synchronized(LibraryCollector.class){i=instance;instance=null;}
        if(i==null)return;i.listeners.clear();i.worker.shutdownNow();
        try{i.app.unregisterComponentCallbacks(i.memory);}catch(RuntimeException ignored){}
        try{i.worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }

    private final Context app;private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->{
        Thread t=new Thread(r,"library-collector");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    private final CopyOnWriteArraySet<Runnable> listeners=new CopyOnWriteArraySet<>();
    private volatile Status status=new Status(0,Phase.IDLE,0,0,0,false,false,false,0);private int passes;
    private final Object lock=new Object();private boolean running,again;private long pausedUntil;private volatile Thread thread;
    /**
     * Review, 27 October 2026: the job can be stopped part-way. {@code epoch} moves on with every start (folders saved
     * again, a key added) and every clear; the poster loop stops when it moves, so new folders are walked next. {@code
     * cancels} moves on only when the list or the posters are cleared (Privacy, a reset, forgetting the server): the walk
     * stops at its next page and nothing it found is written back (LibraryIndex.clear).
     */
    private final java.util.concurrent.atomic.AtomicInteger epoch=new java.util.concurrent.atomic.AtomicInteger(),cancels=new java.util.concurrent.atomic.AtomicInteger();
    private volatile int passCancels;
    private final Object notifyLock=new Object();private boolean posted;private long notifiedAt;
    private final ComponentCallbacks2 memory=new ComponentCallbacks2(){
        @Override public void onTrimMemory(int level){if(level>=TRIM_MEMORY_RUNNING_LOW&&level!=TRIM_MEMORY_UI_HIDDEN)lowMemory();}
        @Override public void onLowMemory(){lowMemory();}
        @Override public void onConfigurationChanged(Configuration c){}
    };

    private LibraryCollector(Context app){
        this.app=app;worker.allowCoreThreadTimeOut(true);
        try{app.registerComponentCallbacks(memory);}catch(RuntimeException ignored){/* no pause on memory, still bounded */}
    }

    // --- for screens
    Status status(){return status;}
    /** Runs on the main thread when the status changes (at most every {@link #NOTIFY_MS}); remove it when the screen goes. */
    void listen(Runnable r){if(r!=null)listeners.add(r);}
    void unlisten(Runnable r){if(r!=null)listeners.remove(r);}
    /**
     * Makes the list (what is due: new folders, or a list older than half an hour) and then looks up posters, in the
     * background. Called again while running, it goes round once more when it is done (folders saved again, a key added).
     */
    void start(){
        epoch.incrementAndGet();
        synchronized(lock){if(running){again=true;return;}running=true;again=false;}
        try{worker.execute(this::run);}catch(RejectedExecutionException e){synchronized(lock){running=false;}}
    }
    /**
     * The saved list was cleared (LibraryIndex.clear: Privacy, a reset, the media server forgotten): stop now, write
     * nothing back, and don't go round again.
     */
    static void cancel(){
        LibraryCollector i=instance;if(i==null)return;
        i.cancels.incrementAndGet();i.epoch.incrementAndGet();
        synchronized(i.lock){i.again=false;i.lock.notifyAll();}
        Thread t=i.thread;if(t!=null)t.interrupt();
    }
    /** Posters and details were cleared (Posters.clear): the poster loop stops instead of fetching them all again. */
    static void postersCleared(){LibraryCollector i=instance;if(i!=null)i.epoch.incrementAndGet();}
    /** Memory is short: rest where it is (between two pages or two poster batches) until it has come back. */
    void lowMemory(){synchronized(lock){pausedUntil=System.currentTimeMillis()+pauseMs;lock.notifyAll();}}
    /** Tests only: memory has come back. */
    void resume(){synchronized(lock){pausedUntil=0;lock.notifyAll();}}

    // --- the job
    private void run(){
        thread=Thread.currentThread();
        try{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);}catch(RuntimeException|LinkageError ignored){/* JVM tests */}
        try{
            while(true){
                pass();
                if(Thread.currentThread().isInterrupted())return;
                synchronized(lock){if(!again){running=false;return;}again=false;}
            }
        }finally{thread=null;synchronized(lock){running=false;}}
    }
    private void pass(){
        LibraryFolders.Library lib=LibraryFolders.library(LibraryFolders.read(app,LibraryFolders.STORE),LibraryFolders.read(app,KidsPolicy.STORE),false);
        if(!lib.ready){set(make(Phase.IDLE,0,0,0,false,false,false,0));return;}
        try{DlnaClient.lan=LocalDiscovery.lan(app);}catch(RuntimeException ignored){}
        passCancels=cancels.get();Thread.interrupted();   // a cancel before this pass is not this pass's
        passes++;set(make(Phase.SCANNING,0,0,0,false,false,false,0));
        LibraryIndex.Index index;
        try{index=LibraryIndex.refresh(app,lib,false,(titles,folders)->set(make(Phase.SCANNING,titles,0,0,false,status.paused,false,0)));}
        catch(InterruptedIOException e){Thread.currentThread().interrupt();set(make(Phase.IDLE,0,0,0,false,false,false,0));return;}
        catch(Exception e){set(make(Phase.FAILED,0,0,0,false,false,true,e instanceof DlnaClient.Failure?((DlnaClient.Failure)e).kind:DlnaClient.Failure.UNREACHABLE));return;}
        if(cancels.get()!=passCancels){set(make(Phase.IDLE,0,0,0,false,false,false,0));return;}
        posters(lib,index);
    }
    private boolean superseded(int at){if(epoch.get()!=at)return true;synchronized(lock){return again;}}
    /** Every tile not looked up yet, in the library's own row order, a few at a time behind whatever is on screen. */
    private void posters(LibraryFolders.Library lib,LibraryIndex.Index index){
        int found=index.size();boolean stale=index.stale;
        int at=epoch.get();Posters p=Posters.get(app);p.refreshKey();
        if(!p.usable()){set(make(Phase.DONE,found,0,0,true,false,stale,0));return;}
        LibraryCatalogue.Catalogue cat=LibraryCatalogue.build(index,lib,HouseholdSettings.scheme(app),null,System.currentTimeMillis());
        String udn=lib.udn();List<LibraryCatalogue.Card> todo=new ArrayList<>();Set<String> keys=new HashSet<>();int total=0,done=0;
        for(LibraryCatalogue.Row r:cat.rows)for(LibraryCatalogue.Card c:r.all){if(!keys.add(c.lookupKey()))continue;total++;if(p.known(udn,c.lookupKey()))done++;else todo.add(c);}
        set(make(Phase.POSTERS,found,done,total,false,false,stale,0));
        int batch=Math.max(1,MemoryBudget.current().posterBatch()),pictures=0;boolean stopped=false;
        p.collecting(true);   // the match list is saved every few seconds, not after every batch
        try{
            for(int i=0;i<todo.size();i+=batch){
                between();
                // Folders saved again, a key added, or the posters cleared: stop here (the next round starts from the list).
                if(superseded(at)){stopped=true;break;}
                if(p.resting())break;   // TMDB refused the key or is out of reach: the rest come as tiles are shown, or next time
                List<LibraryCatalogue.Card> part=todo.subList(i,Math.min(todo.size(),i+batch));CountDownLatch left=new CountDownLatch(part.size());
                for(LibraryCatalogue.Card c:part)p.warm(c,udn,pictures++<WARM_PICTURES,left::countDown);
                if(!left.await(2,TimeUnit.MINUTES))break;
                done+=part.size();set(make(Phase.POSTERS,found,done,total,false,status.paused,stale,0));
            }
        }catch(InterruptedIOException|InterruptedException e){Thread.currentThread().interrupt();stopped=true;}
        finally{p.collecting(false);}   // and once more now, so nothing found is lost
        if(cancels.get()!=passCancels){set(make(Phase.IDLE,0,0,0,false,false,false,0));return;}
        if(stopped&&superseded(at)&&againSet())return;   // another round follows at once and says where it is
        set(make(Phase.DONE,found,done,total,false,false,stale,0));
    }

    /**
     * Between two pages from the media server (LibraryIndex.walk) and two poster batches: a short rest, and the pause when
     * memory is short. Only the job's own thread ever waits here; any other walk (Settings' Rescan) goes straight on.
     */
    static void between()throws InterruptedIOException{LibraryCollector i=instance;if(i!=null)i.breathe();}
    private void breathe()throws InterruptedIOException{
        if(Thread.currentThread()!=thread)return;
        if(cancels.get()!=passCancels)throw new InterruptedIOException("cleared");
        try{
            long gap=MemoryBudget.current().indexGapMs();if(gap>0)Thread.sleep(gap);
            boolean rested=false;
            synchronized(lock){
                while(true){
                    long now=System.currentTimeMillis();
                    if(pausedUntil<=now){
                        if(pausedUntil==0||!stillShort())break;
                        pausedUntil=now+pauseMs;   // still short: rest a while longer
                    }
                    if(!rested){rested=true;set(status.paused(true));}
                    lock.wait(Math.max(1,Math.min(pausedUntil-now,1000)));
                }
                pausedUntil=0;
            }
            if(rested)set(status.paused(false));
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}
    }
    private boolean againSet(){synchronized(lock){return again;}}
    private boolean stillShort(){
        try{android.app.ActivityManager am=(android.app.ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);if(am==null)return false;
            android.app.ActivityManager.MemoryInfo mi=new android.app.ActivityManager.MemoryInfo();am.getMemoryInfo(mi);return mi.lowMemory;}
        catch(RuntimeException e){return false;}
    }

    private Status make(Phase phase,int found,int postersDone,int postersTotal,boolean noKey,boolean paused,boolean stale,int problem){
        return new Status(passes,phase,found,postersDone,postersTotal,noKey,paused,stale,problem);}

    // --- telling the screens
    private void set(Status s){
        status=s;long wait;
        synchronized(notifyLock){if(posted)return;posted=true;wait=Math.max(0,notifiedAt+NOTIFY_MS-SystemClock.uptimeMillis());}
        main.postDelayed(this::tell,wait);
    }
    private void tell(){
        synchronized(notifyLock){posted=false;notifiedAt=SystemClock.uptimeMillis();}
        for(Runnable r:listeners){try{r.run();}catch(RuntimeException ignored){/* one screen's trouble is not the job's */}}
    }
}
