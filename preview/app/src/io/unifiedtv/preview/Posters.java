package io.unifiedtv.preview;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Posters and backgrounds for the library, never blocking focus (AGENTS rule 5) and never another title's picture
 * (rule 4). Order of preference for a tile: the TMDB poster matched to that title (when the household has added its own
 * key), then the media server's own picture for it (albumArtURI), then a plain title card drawn by the tile. Backgrounds:
 * the matched TMDB backdrop, or that title's poster softened (Backdrops.fromPoster); otherwise the neutral scene.
 *
 * Everything is cached: matches by server UDN and item id (a "no match" too, retried after two weeks), pictures on disk
 * in the app's private storage (about 80 MB, oldest first out), decoded pictures in a small memory cache. With the cache,
 * the library works offline. Lookups run one at a time at a gentle pace (TMDB's rate limits; a "slow down" reply waits
 * as asked). The tile in focus and its neighbours jump the queue.
 *
 * SET-034 manual correction: the household can choose a tile's picture itself ("Wrong picture?", PictureFixActivity): one
 * of TMDB's candidates, or no picture at all. The choice is kept per item (server UDN and item id, or the programme's key)
 * in its own file, wins over any automatic match and over the server's own picture, and survives clearing the cache.
 *
 * A key added later (owner's TCL, 27 October 2026): a title shown without a key has nothing saved against it, so it is
 * looked up as soon as there is one; when the key changes ({@link #refreshKey}, the library calls it on coming back),
 * pictures held in memory (perhaps the server's own) are dropped so every tile asks again. A refused key or no internet
 * pauses lookups briefly instead of asking for every tile. Owner diagnostics read what happened ({@link #diagnosis}).
 */
final class Posters {
    static final long DISK=80L*1024*1024,MISS_RETRY_MS=14L*24*60*60*1000,KEY_TTL_MS=60_000,GAP_MS=120;
    static final int MATCH_MAX=6000,POSTER_W=300,POSTER_H=450,BACK_W=960,BACK_H=540;
    static final String HINT="Add your free TMDB key in Settings > Library & Media > Film posters to see posters";
    /** After TMDB refused the key, or couldn't be reached: lookups wait this long (or until the key changes). */
    static final long REFUSED_PAUSE_MS=10*60_000L,OFFLINE_PAUSE_MS=30_000L;
    /** The last TMDB request's result, for Owner diagnostics. */
    static final String OK="ok",REFUSED="refused",OFFLINE="offline",BUSY="busy",ERROR="error",NO_KEY="no key";
    static final int UNMATCHED_KEPT=5;

    interface Callback{void done(Bitmap b);}
    /** Tests only: a fake TMDB and key (no network in tests). */
    static volatile Tmdb.Transport transport;
    static volatile String testKey;

    private static Posters instance;
    static synchronized Posters get(Context c){if(instance==null)instance=new Posters(c.getApplicationContext());return instance;}
    /** Tests only: forget everything held in memory. */
    static synchronized void reset(){if(instance!=null){instance.lookups.shutdownNow();instance.pictures.shutdownNow();}instance=null;}

    private final Context app;private final File dir;private final PhotoCache disk;private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> memory;
    /** MemoryBudget: JPEG pictures at 16 bits a pixel (2 GB TVs), and the poster size actually shown on this screen. */
    private final boolean opaque565;private final int posterW,posterH;
    private final LinkedBlockingDeque<Runnable> lookupQueue=new LinkedBlockingDeque<>(),pictureQueue=new LinkedBlockingDeque<>();
    private final ThreadPoolExecutor lookups=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,lookupQueue),pictures=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,pictureQueue);
    private final Map<String,List<Callback>> waiting=new HashMap<>();
    /** Summaries of matched titles for the main thread: filled as matches are read, never locked against the disk. */
    private final ConcurrentHashMap<String,String[]> summaries=new ConcurrentHashMap<>();
    private JSONObject matches,fixes;private boolean dirty,keyRead;private long savedAt,lastCall,keyAt;private String key="";
    private volatile long pausedUntil;
    /** Owner diagnostics: the last request's result and HTTP status, when, and the last titles TMDB had no match for. */
    private String lastResult;private int lastCode;private long lastAt;private final ArrayDeque<String> unmatched=new ArrayDeque<>();

    private Posters(Context app){
        this.app=app;dir=new File(app.getFilesDir(),"posters");disk=new PhotoCache(new File(dir,"img"),DISK);
        MemoryBudget budget=MemoryBudget.get(app);opaque565=budget.opaque565();
        memory=new LruCache<String,Bitmap>(budget.imageCacheBytes(Runtime.getRuntime().maxMemory())){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
        android.util.DisplayMetrics m=app.getResources().getDisplayMetrics();int[] shown=shownPoster(m.widthPixels,m.heightPixels);posterW=shown[0];posterH=shown[1];
        lookups.allowCoreThreadTimeOut(true);pictures.allowCoreThreadTimeOut(true);
    }

    /**
     * The largest a poster is shown on this screen: the library's tile (208 x 312 at 1080p) lifted on focus, at most
     * 300 x 450. Pictures are decoded at this size (Images), never larger.
     */
    static int[] shownPoster(int screenW,int screenH){
        float unit=Math.max(.5f,Math.min(screenW/1920f,screenH/1080f));
        return new int[]{Math.min(POSTER_W,Math.round(SHOWN_W*unit)),Math.min(POSTER_H,Math.round(SHOWN_H*unit))};
    }
    static final int SHOWN_W=226,SHOWN_H=338;
    /** The memory cache's limit and use in bytes (for tests and Owner diagnostics). */
    int cacheLimit(){return memory.maxSize();}
    int cacheUsed(){return memory.size();}
    /** onTrimMemory (PreviewApp): every decoded picture when no screen shows them; half when memory runs low. Disk stays. */
    static void trim(boolean hidden){Posters p;synchronized(Posters.class){p=instance;}if(p==null)return;if(hidden)p.memory.evictAll();else p.memory.trimToSize(p.memory.maxSize()/2);}

    // --- the household's key
    /** The household's TMDB key or token from the Vault ("tmdb", "key"); empty when there is none. */
    static String key(Context c){
        String t=testKey;if(t!=null)return t;
        try{return new Vault(c).read("tmdb").optString("key","").trim();}catch(Exception e){return "";}
    }
    /** For Settings > Library & Media (another screen shows it): posters on or off, in plain words. */
    static String status(Context c){
        return new Tmdb(key(c),null).usable()?"On: posters and backgrounds come from TMDB, matched by title":"Off: add your free TMDB key from your phone to show posters";
    }
    static boolean on(Context c){return new Tmdb(key(c),null).usable();}
    private Tmdb tmdb(){
        boolean changed=false;Tmdb t;
        synchronized(this){long now=System.currentTimeMillis();
            if(now-keyAt>KEY_TTL_MS||now<keyAt){String k=key(app);changed=keyRead&&!k.equals(key);key=k;keyAt=now;keyRead=true;}
            t=new Tmdb(key,transport);}
        if(changed)keyChanged();
        return t.usable()?t:null;
    }
    /**
     * Reads the key again now (the library calls this when it comes back to the front, perhaps after phone setup added
     * one). True when it changed: then the pictures held in memory are dropped so every tile asks again.
     */
    boolean refreshKey(){
        String k=key(app);boolean changed;
        synchronized(this){changed=keyRead&&!k.equals(key);key=k;keyAt=System.currentTimeMillis();keyRead=true;}
        if(changed)keyChanged();
        return changed;
    }
    /** Whether the key last read is one TMDB could accept (no Vault read: see {@link #refreshKey}). */
    synchronized boolean usable(){if(!keyRead){key=key(app);keyAt=System.currentTimeMillis();keyRead=true;}return new Tmdb(key,null).usable();}
    private void keyChanged(){pausedUntil=0;memory.evictAll();summaries.clear();}

    // --- public: tiles and backgrounds
    /** A picture already in memory for this tile, or null (then ask with {@link #poster}). */
    Bitmap cachedPoster(LibraryCatalogue.Card card){return memory.get("p:"+card.lookupKey());}
    Bitmap cachedBackdrop(LibraryCatalogue.Card card,boolean light){return memory.get((light?"bl:":"b:")+card.lookupKey());}

    /** The tile's poster, delivered on the main thread (null when there is none). Urgent ones go first. */
    void poster(LibraryCatalogue.Card card,String udn,boolean urgent,Callback cb){
        String k="p:"+card.lookupKey();Bitmap b=memory.get(k);if(b!=null){if(cb!=null)cb.done(b);return;}
        if(!wait(k,cb))return;
        LookupJob job=new LookupJob(card,udn);
        submit(lookups,lookupQueue,urgent,()->{
            Tmdb.Match m=match(job);String path=m!=null?m.poster:"";boolean none=noPicture(job.udn,job.lookupKey);
            submit(pictures,pictureQueue,urgent,()->{
                Bitmap out=null;
                if(!path.isEmpty())out=tmdbImage(path,"w342",posterW,posterH);
                if(out==null&&!none&&!job.serverArt.isEmpty())out=serverImage(job.serverArt,posterW,posterH);
                if(out!=null)memory.put(k,out);deliver(k,out);
            });
        });
    }
    /**
     * The tile's background: its TMDB backdrop, or its poster softened. Light mode (Lightweight rendering, a low-memory TV)
     * always uses the softened poster: one small picture, no large decode.
     */
    void backdrop(LibraryCatalogue.Card card,String udn,boolean light,Callback cb){backdrop(card,udn,light,cb,false);}
    /**
     * The library's background for the tile in focus (module 05: asked the moment focus moves, no settle delay). Only the
     * newest matters: an earlier one still waiting in the queue is dropped (its callers hear null), so a run of fast
     * presses looks up and decodes one background, not one per tile passed.
     */
    void latestBackdrop(LibraryCatalogue.Card card,String udn,boolean light,Callback cb){backdrop(card,udn,light,cb,true);}
    private Runnable queuedBackdrop;private String queuedBackdropKey;
    private void backdrop(LibraryCatalogue.Card card,String udn,boolean light,Callback cb,boolean latestOnly){
        String k=(light?"bl:":"b:")+card.lookupKey();Bitmap b=memory.get(k);if(b!=null){cb.done(b);return;}
        if(latestOnly)dropQueuedBackdrop(k);
        if(!wait(k,cb))return;
        LookupJob job=new LookupJob(card,udn);
        Runnable lookup=()->{
            Tmdb.Match m=match(job);String back=m!=null&&!light?m.backdrop:"",poster=m!=null?m.poster:"";boolean none=noPicture(job.udn,job.lookupKey);
            submit(pictures,pictureQueue,true,()->{
                Bitmap out=null;
                if(!back.isEmpty())out=tmdbImage(back,"w780",BACK_W,BACK_H);
                if(out==null){Bitmap p=memory.get("p:"+job.lookupKey);
                    if(p==null&&!poster.isEmpty())p=tmdbImage(poster,"w342",posterW,posterH);
                    if(p==null&&!none&&!job.serverArt.isEmpty())p=serverImage(job.serverArt,posterW,posterH);
                    if(p!=null){memory.put("p:"+job.lookupKey,p);out=Backdrops.fromPoster(p);}}
                if(out!=null)memory.put(k,out);deliver(k,out);
            });
        };
        if(latestOnly)synchronized(this){queuedBackdrop=lookup;queuedBackdropKey=k;}
        submit(lookups,lookupQueue,true,lookup);
    }
    private void dropQueuedBackdrop(String next){
        Runnable r;String k;synchronized(this){r=queuedBackdrop;k=queuedBackdropKey;queuedBackdrop=null;queuedBackdropKey=null;}
        if(r==null)return;
        if(k.equals(next)){synchronized(this){if(queuedBackdrop==null){queuedBackdrop=r;queuedBackdropKey=k;}}return;}
        if(lookupQueue.remove(r))deliver(k,null);   // not started yet: nothing was looked up or decoded for it
    }
    /** Warms the cache for tiles about to be seen (not urgent; nothing is shown). */
    void prefetch(List<LibraryCatalogue.Card> cards,String udn){for(LibraryCatalogue.Card c:cards)if(memory.get("p:"+c.lookupKey())==null)poster(c,udn,false,null);}

    // --- inside
    /** What a lookup needs, copied so that no view or catalogue is held by the queue. */
    private static final class LookupJob{final String lookupKey,title,year,serverArt,udn;final boolean tv;
        LookupJob(LibraryCatalogue.Card c,String udn){lookupKey=c.lookupKey();title=c.lookupTitle();year=c.lookupYear();serverArt=c.serverArt()==null?"":c.serverArt();tv=c.tv();this.udn=udn==null?"":udn;}}
    private synchronized boolean wait(String k,Callback cb){List<Callback> l=waiting.get(k);if(l!=null){if(cb!=null)l.add(cb);return false;}l=new ArrayList<>();if(cb!=null)l.add(cb);waiting.put(k,l);return true;}
    private void deliver(String k,Bitmap b){List<Callback> l;synchronized(this){l=waiting.remove(k);}if(l==null||l.isEmpty())return;main.post(()->{for(Callback c:l)c.done(b);});}
    private void submit(ThreadPoolExecutor ex,LinkedBlockingDeque<Runnable> q,boolean urgent,Runnable r){
        try{if(ex.isShutdown())return;if(ex.getPoolSize()<ex.getCorePoolSize())ex.prestartAllCoreThreads();if(urgent)q.offerFirst(r);else q.offerLast(r);}
        catch(RuntimeException ignored){}
    }

    /** The cached match, or a TMDB lookup (on the lookup thread). Null when there is none, no key, or no network. */
    private Tmdb.Match match(LookupJob j){
        String id=id(j.udn,j.lookupKey),q=hash((j.tv?"tv":"movie")+"\n"+j.title+"\n"+j.year);long now=System.currentTimeMillis();
        JSONObject chosen=fix(j.udn,j.lookupKey);   // the household's own choice wins, and is never looked up again
        if(chosen!=null){if(chosen.optBoolean("none"))return null;summaries.put(id,new String[]{chosen.optString("o"),chosen.optString("y")});
            return new Tmdb.Match(chosen.optString("t"),chosen.optLong("id"),chosen.optString("p"),chosen.optString("b"),chosen.optString("n"),chosen.optString("y"));}
        JSONObject m=matches().optJSONObject(id);
        if(m!=null&&q.equals(m.optString("q"))){
            if(!m.optBoolean("miss")){summaries.put(id,new String[]{m.optString("o"),m.optString("y")});return new Tmdb.Match(m.optString("t"),m.optLong("id"),m.optString("p"),m.optString("b"),"","");}
            if(now-m.optLong("at")<MISS_RETRY_MS)return null;
        }
        if(MediaName.normal(j.title).isEmpty())return null;
        Tmdb t=tmdb();if(t==null){note(NO_KEY,0,null);return null;}   // nothing saved: looked up once there is a key
        if(System.currentTimeMillis()<pausedUntil)return null;
        for(int attempt=0;attempt<2;attempt++){
            try{
                long gap=lastCall+GAP_MS-System.currentTimeMillis();if(gap>0)Thread.sleep(gap);lastCall=System.currentTimeMillis();
                Tmdb.Match found=t.find(j.tv,j.title,j.year);
                JSONObject rec=new JSONObject().put("q",q).put("at",now);
                if(found==null)rec.put("miss",true);else rec.put("t",found.type).put("id",found.id).put("p",found.poster).put("b",found.backdrop).put("o",found.overview).put("y",found.year);
                note(OK,200,found==null?j.title+(j.year.isEmpty()?"":" ("+j.year+")"):null);
                remember(id,rec);if(found!=null)summaries.put(id,new String[]{found.overview,found.year});return found;
            }catch(Tmdb.Busy b){note(BUSY,429,null);try{Thread.sleep(Math.min(15_000,b.waitMs));}catch(InterruptedException e){Thread.currentThread().interrupt();return null;}}
            catch(Tmdb.HttpError e){   // refused (401/403) or a TMDB error: nothing is cached, so it is tried again later
                note(e.refused()?REFUSED:ERROR,e.code,null);if(e.refused())pausedUntil=System.currentTimeMillis()+REFUSED_PAUSE_MS;return null;}
            catch(InterruptedException e){Thread.currentThread().interrupt();return null;}
            catch(Exception e){note(OFFLINE,0,null);pausedUntil=System.currentTimeMillis()+OFFLINE_PAUSE_MS;return null;}   // offline: tried again shortly
        }
        return null;
    }
    private Bitmap tmdbImage(String path,String size,int w,int h){
        String k="tmdb:"+size+path;
        try{File f=disk.file(k);
            if(!f.isFile()){Tmdb t=tmdb();if(t==null)return null;byte[] data=t.image(path,size);disk.put(k,o->o.write(data));}
            else disk.touch(f);
            return decode(f,w,h,opaque565);}catch(Exception e){return null;}
    }
    private Bitmap serverImage(String url,int w,int h){
        String k="nas:"+url;
        try{File f=disk.file(k);
            if(!f.isFile()){byte[] data=DlnaClient.request(url,null,null);disk.put(k,o->o.write(data));}
            else disk.touch(f);
            return decode(f,w,h,opaque565);}catch(Exception e){return null;}
    }
    private static Bitmap decode(File f,int w,int h,boolean opaque565)throws IOException{
        byte[] data;try(InputStream in=new FileInputStream(f)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);data=out.toByteArray();}
        return Images.decode(data,w,h,opaque565);
    }

    // --- the match list on disk
    private synchronized JSONObject matches(){
        if(matches!=null)return matches;
        File f=new File(dir,"matches.json");
        try(InputStream in=new FileInputStream(f)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);matches=new JSONObject(out.toString("UTF-8"));}
        catch(Exception e){matches=new JSONObject();}
        return matches;
    }
    private synchronized void remember(String id,JSONObject rec){
        try{matches().put(id,rec);}catch(JSONException ignored){return;}
        if(matches.length()>MATCH_MAX)trim();
        dirty=true;long now=System.currentTimeMillis();
        if(now-savedAt>3000||lookupQueue.isEmpty())save();
    }
    private void trim(){
        List<String> keys=new ArrayList<>();for(Iterator<String> it=matches.keys();it.hasNext();)keys.add(it.next());
        keys.sort(Comparator.comparingLong(k->matches.optJSONObject(k)==null?0:matches.optJSONObject(k).optLong("at")));
        for(int i=0;i<keys.size()-MATCH_MAX;i++)matches.remove(keys.get(i));
    }
    private synchronized void save(){
        if(!dirty||matches==null)return;
        try{if(!dir.isDirectory()&&!dir.mkdirs())return;File tmp=new File(dir,"matches.tmp"),f=new File(dir,"matches.json");
            try(OutputStream o=new FileOutputStream(tmp)){o.write(matches.toString().getBytes(StandardCharsets.UTF_8));}
            if(tmp.renameTo(f)){dirty=false;savedAt=System.currentTimeMillis();}}catch(Exception ignored){}
    }
    /** Cache key: the server's UDN and the item's id (or the programme's key), hashed. */
    static String id(String udn,String lookupKey){return hash((udn==null?"":udn)+"\n"+lookupKey);}
    static String hash(String s){
        try{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(int i=0;i<12;i++)b.append(String.format(Locale.ROOT,"%02x",d[i]&255));return b.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    /** The matched title's summary and year, when a match is saved for this tile (main thread safe; no lookup). */
    String[] details(LibraryCatalogue.Card card,String udn){return summaries.get(id(udn,card.lookupKey()));}
    /** For tests: the match saved for a tile, or null. */
    synchronized JSONObject saved(String udn,String lookupKey){return matches().optJSONObject(id(udn,lookupKey));}
    /** Settings > Privacy (or a reset): forget matches and pictures. The household's own corrections (SET-034) stay. */
    synchronized void clear(){memory.evictAll();summaries.clear();matches=new JSONObject();dirty=true;save();disk.clear();
        lastResult="";lastCode=0;lastAt=0;unmatched.clear();new File(dir,"status.json").delete();}

    // --- Owner diagnostics: what happened with TMDB on this TV
    /** Keeps the last request's result; saved when it changes or a title had no match (cleaned titles only, never a file name). */
    private synchronized void note(String result,int code,String missed){
        status();boolean changed=!result.equals(lastResult)||code!=lastCode;
        lastResult=result;lastCode=code;lastAt=System.currentTimeMillis();
        if(missed!=null&&!missed.trim().isEmpty()){unmatched.remove(missed);unmatched.addFirst(missed);while(unmatched.size()>UNMATCHED_KEPT)unmatched.removeLast();changed=true;}
        if(changed)saveStatus();
    }
    private synchronized void status(){
        if(lastResult!=null)return;lastResult="";
        try(InputStream in=new FileInputStream(new File(dir,"status.json"))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            JSONObject o=new JSONObject(out.toString("UTF-8"));lastResult=o.optString("r");lastCode=o.optInt("c");lastAt=o.optLong("at");
            JSONArray u=o.optJSONArray("u");if(u!=null)for(int i=0;i<u.length()&&unmatched.size()<UNMATCHED_KEPT;i++)unmatched.addLast(u.optString(i));}
        catch(Exception ignored){/* none yet */}
    }
    private synchronized void saveStatus(){
        try{if(!dir.isDirectory()&&!dir.mkdirs())return;File tmp=new File(dir,"status.tmp"),f=new File(dir,"status.json");
            JSONObject o=new JSONObject().put("r",lastResult).put("c",lastCode).put("at",lastAt).put("u",new JSONArray(new ArrayList<>(unmatched)));
            try(OutputStream out=new FileOutputStream(tmp)){out.write(o.toString().getBytes(StandardCharsets.UTF_8));}
            if(!tmp.renameTo(f))tmp.delete();}catch(Exception ignored){}
    }
    /** The last request's result ("" when there was none), its HTTP status and when (ms), for tests and diagnostics. */
    synchronized String lastResult(){status();return lastResult;}
    synchronized int lastCode(){status();return lastCode;}
    synchronized long lastAt(){status();return lastAt;}
    synchronized List<String> unmatched(){status();return new ArrayList<>(unmatched);}
    /** {matched, looked up}: titles TMDB matched, of all those looked up on this TV (saved matches and no-matches). */
    synchronized int[] matchCounts(){
        int hit=0,all=0;JSONObject m=matches();for(Iterator<String> it=m.keys();it.hasNext();){JSONObject r=m.optJSONObject(it.next());if(r==null)continue;all++;if(!r.optBoolean("miss"))hit++;}
        return new int[]{hit,all};
    }
    /** Owner diagnostics' "Film posters" lines (reads this TV's storage: off the main thread). {@code when} words lastAt. */
    String diagnosis(boolean keySaved,java.util.function.LongFunction<String> when){
        int[] n=matchCounts();long at=lastAt();
        return report(keySaved,lastResult(),lastCode(),at>0&&when!=null?when.apply(at):"",n[0],n[1],unmatched());
    }
    /** Pure: the lines themselves. Never the key, a file name or an address: the cleaned titles only. */
    static String report(boolean keySaved,String result,int code,String when,int matched,int total,List<String> unmatched){
        StringBuilder b=new StringBuilder("TMDB key: ").append(keySaved?"saved":"not saved. "+HINT+".");
        b.append("\nLast TMDB request: ").append(plain(result,code)).append(when==null||when.isEmpty()?"":" ("+when+")");
        b.append("\nMatched: ").append(matched).append(" of ").append(total).append(total==1?" title looked up":" titles looked up");
        if(unmatched!=null&&!unmatched.isEmpty())b.append("\nNo match lately: ").append(String.join("; ",unmatched));
        return b.toString();
    }
    static String plain(String result,int code){
        if(result==null||result.isEmpty())return "none yet on this TV";
        switch(result){
            case OK:return "ok, HTTP "+code;
            case REFUSED:return "refused, HTTP "+code+": TMDB didn't accept the key. Add it again from your phone";
            case OFFLINE:return "offline: TMDB couldn't be reached. Check this TV's internet connection";
            case BUSY:return "busy, HTTP 429: TMDB asked Unified TV to slow down";
            case NO_KEY:return "not sent: no TMDB key on this TV";
            default:return "error, HTTP "+code;
        }
    }

    // --- SET-034: the household's own choice of picture
    /** The saved choice for this tile ({"none":true}, or a TMDB match), or null when the picture is chosen automatically. */
    synchronized JSONObject fix(String udn,String lookupKey){return fixes().optJSONObject(id(udn,lookupKey));}
    /** Whether the household chose "No picture" for this tile: no TMDB picture and not the server's own either. */
    boolean noPicture(String udn,String lookupKey){JSONObject f=fix(udn,lookupKey);return f!=null&&f.optBoolean("none");}
    /** Keeps the household's choice for this tile: {@code m} one of TMDB's candidates, or null for no picture. */
    void choose(String udn,String lookupKey,Tmdb.Match m){
        try{JSONObject rec=m==null?new JSONObject().put("none",true)
                :new JSONObject().put("t",m.type).put("id",m.id).put("p",m.poster).put("b",m.backdrop).put("o",m.overview).put("y",m.year).put("n",m.title);
            synchronized(this){fixes().put(id(udn,lookupKey),rec.put("at",System.currentTimeMillis()));saveFixes();}}
        catch(JSONException ignored){}
        forget(udn,lookupKey);
    }
    /** Back to the automatic picture (TMDB's cautious match, then the server's): the choice is removed and looked up again. */
    void automatic(String udn,String lookupKey){
        synchronized(this){fixes().remove(id(udn,lookupKey));matches().remove(id(udn,lookupKey));dirty=true;save();saveFixes();}
        forget(udn,lookupKey);
    }
    /** For Settings (Library &amp; Media or Privacy &amp; Data): how many titles have a picture chosen by hand, and forgetting them all. */
    synchronized int choices(){return fixes().length();}
    synchronized void forgetChoices(){fixes=new JSONObject();saveFixes();memory.evictAll();summaries.clear();}
    /** Drops this tile's pictures and summary from memory, so the next ask uses the new choice. */
    private void forget(String udn,String lookupKey){
        for(String prefix:new String[]{"p:","b:","bl:"})memory.remove(prefix+lookupKey);
        summaries.remove(id(udn,lookupKey));
    }
    /**
     * Up to {@code max} of TMDB's results for a title the household chose (the cleaned title, or one typed on the TV), for
     * picking by eye: not filtered by the cautious matching, which the household is overriding. Throws when there is no
     * key or TMDB can't be reached (Busy when it asks to wait). Only the title (and nothing else) is sent.
     */
    List<Tmdb.Match> candidates(boolean tv,String title,String year,int max)throws IOException{
        Tmdb t=tmdb();if(t==null)throw new IOException("no key");if(MediaName.normal(title).isEmpty())return new ArrayList<>();
        return Tmdb.candidates(t.search(tv,title.trim(),""),tv,year,max);
    }
    /** A candidate's small poster for the chooser (cached like any other), or null. Off the main thread. */
    Bitmap candidatePoster(Tmdb.Match m){return m==null||m.poster.isEmpty()?null:tmdbImage(m.poster,"w185",POSTER_W/2,POSTER_H/2);}
    private synchronized JSONObject fixes(){
        if(fixes!=null)return fixes;
        try(InputStream in=new FileInputStream(new File(dir,"fixes.json"))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);fixes=new JSONObject(out.toString("UTF-8"));}
        catch(Exception e){fixes=new JSONObject();}
        return fixes;
    }
    private synchronized void saveFixes(){
        try{if(!dir.isDirectory()&&!dir.mkdirs())return;File tmp=new File(dir,"fixes.tmp"),f=new File(dir,"fixes.json");
            try(OutputStream o=new FileOutputStream(tmp)){o.write(fixes().toString().getBytes(StandardCharsets.UTF_8));}
            if(!tmp.renameTo(f))tmp.delete();}catch(Exception ignored){}
    }
}
