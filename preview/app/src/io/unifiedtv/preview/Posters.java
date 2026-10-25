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
 */
final class Posters {
    static final long DISK=80L*1024*1024,MISS_RETRY_MS=14L*24*60*60*1000,KEY_TTL_MS=60_000,GAP_MS=120;
    static final int MATCH_MAX=6000,POSTER_W=300,POSTER_H=450,BACK_W=960,BACK_H=540;
    static final String HINT="Posters and backgrounds can be added in Settings > Library & Media.";

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
    private final LinkedBlockingDeque<Runnable> lookupQueue=new LinkedBlockingDeque<>(),pictureQueue=new LinkedBlockingDeque<>();
    private final ThreadPoolExecutor lookups=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,lookupQueue),pictures=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,pictureQueue);
    private final Map<String,List<Callback>> waiting=new HashMap<>();
    /** Summaries of matched titles for the main thread: filled as matches are read, never locked against the disk. */
    private final ConcurrentHashMap<String,String[]> summaries=new ConcurrentHashMap<>();
    private JSONObject matches;private boolean dirty;private long savedAt,lastCall,keyAt;private String key="";

    private Posters(Context app){
        this.app=app;dir=new File(app.getFilesDir(),"posters");disk=new PhotoCache(new File(dir,"img"),DISK);
        int budget=(int)Math.min(24L*1024*1024,Runtime.getRuntime().maxMemory()/8);
        memory=new LruCache<String,Bitmap>(Math.max(4*1024*1024,budget)){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
        lookups.allowCoreThreadTimeOut(true);pictures.allowCoreThreadTimeOut(true);
    }

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
    private synchronized Tmdb tmdb(){
        long now=System.currentTimeMillis();if(now-keyAt>KEY_TTL_MS||now<keyAt){key=key(app);keyAt=now;}
        Tmdb t=new Tmdb(key,transport);return t.usable()?t:null;
    }

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
            Tmdb.Match m=match(job);String path=m!=null?m.poster:"";
            submit(pictures,pictureQueue,urgent,()->{
                Bitmap out=null;
                if(!path.isEmpty())out=tmdbImage(path,"w342",POSTER_W,POSTER_H);
                if(out==null&&!job.serverArt.isEmpty())out=serverImage(job.serverArt,POSTER_W,POSTER_H);
                if(out!=null)memory.put(k,out);deliver(k,out);
            });
        });
    }
    /**
     * The tile's background: its TMDB backdrop, or its poster softened. Light mode (Lightweight rendering, a low-memory TV)
     * always uses the softened poster: one small picture, no large decode.
     */
    void backdrop(LibraryCatalogue.Card card,String udn,boolean light,Callback cb){
        String k=(light?"bl:":"b:")+card.lookupKey();Bitmap b=memory.get(k);if(b!=null){cb.done(b);return;}
        if(!wait(k,cb))return;
        LookupJob job=new LookupJob(card,udn);
        submit(lookups,lookupQueue,true,()->{
            Tmdb.Match m=match(job);String back=m!=null&&!light?m.backdrop:"",poster=m!=null?m.poster:"";
            submit(pictures,pictureQueue,true,()->{
                Bitmap out=null;
                if(!back.isEmpty())out=tmdbImage(back,"w780",BACK_W,BACK_H);
                if(out==null){Bitmap p=memory.get("p:"+job.lookupKey);
                    if(p==null&&!poster.isEmpty())p=tmdbImage(poster,"w342",POSTER_W,POSTER_H);
                    if(p==null&&!job.serverArt.isEmpty())p=serverImage(job.serverArt,POSTER_W,POSTER_H);
                    if(p!=null){memory.put("p:"+job.lookupKey,p);out=Backdrops.fromPoster(p);}}
                if(out!=null)memory.put(k,out);deliver(k,out);
            });
        });
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
        JSONObject m=matches().optJSONObject(id);
        if(m!=null&&q.equals(m.optString("q"))){
            if(!m.optBoolean("miss")){summaries.put(id,new String[]{m.optString("o"),m.optString("y")});return new Tmdb.Match(m.optString("t"),m.optLong("id"),m.optString("p"),m.optString("b"),"","");}
            if(now-m.optLong("at")<MISS_RETRY_MS)return null;
        }
        Tmdb t=tmdb();if(t==null||MediaName.normal(j.title).isEmpty())return null;
        for(int attempt=0;attempt<2;attempt++){
            try{
                long gap=lastCall+GAP_MS-System.currentTimeMillis();if(gap>0)Thread.sleep(gap);lastCall=System.currentTimeMillis();
                Tmdb.Match found=t.find(j.tv,j.title,j.year);
                JSONObject rec=new JSONObject().put("q",q).put("at",now);
                if(found==null)rec.put("miss",true);else rec.put("t",found.type).put("id",found.id).put("p",found.poster).put("b",found.backdrop).put("o",found.overview).put("y",found.year);
                remember(id,rec);if(found!=null)summaries.put(id,new String[]{found.overview,found.year});return found;
            }catch(Tmdb.Busy b){try{Thread.sleep(Math.min(15_000,b.waitMs));}catch(InterruptedException e){Thread.currentThread().interrupt();return null;}}
            catch(InterruptedException e){Thread.currentThread().interrupt();return null;}
            catch(Exception e){return null;}   // offline or refused: nothing is cached, so it is tried again next time
        }
        return null;
    }
    private Bitmap tmdbImage(String path,String size,int w,int h){
        String k="tmdb:"+size+path;
        try{File f=disk.file(k);
            if(!f.isFile()){Tmdb t=tmdb();if(t==null)return null;byte[] data=t.image(path,size);disk.put(k,o->o.write(data));}
            else disk.touch(f);
            return decode(f,w,h);}catch(Exception e){return null;}
    }
    private Bitmap serverImage(String url,int w,int h){
        String k="nas:"+url;
        try{File f=disk.file(k);
            if(!f.isFile()){byte[] data=DlnaClient.request(url,null,null);disk.put(k,o->o.write(data));}
            else disk.touch(f);
            return decode(f,w,h);}catch(Exception e){return null;}
    }
    private static Bitmap decode(File f,int w,int h)throws IOException{
        byte[] data;try(InputStream in=new FileInputStream(f)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);data=out.toByteArray();}
        return Images.decode(data,w,h);
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
    /** Settings > Privacy (or a reset): forget matches and pictures. */
    synchronized void clear(){memory.evictAll();summaries.clear();matches=new JSONObject();dirty=true;save();disk.clear();}
}
