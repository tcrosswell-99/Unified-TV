package io.unifiedtv.preview;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * A list of every video inside the chosen folders, kept on this TV so the library opens at once and works while the
 * media server is asleep. Built by walking the folders with DLNA's own paging (24 at a time, Page.more/next), bounded in
 * depth, folders, requests and titles; refreshed in the background when it is older than half an hour.
 *
 * What is kept: the server's object ids and titles, the names of the folders a title sits in (for series and seasons),
 * ratings, and the stream and artwork addresses without their host (the host comes from the saved server each time, so
 * a NAS that changed address still plays). The file lives in the app's private storage, which is never backed up. No
 * address, key or server name is written into it.
 *
 * Virtual views that only repeat the same files ("Recently added", "Genre", "Actor", "Year"...) are not walked, nor a
 * server's "All videos" when a real folder view sits beside it. A folder that is itself chosen is walked once, as its own.
 */
final class LibraryIndex {
    static final int MAX_DEPTH=8,MAX_FOLDERS=1500,MAX_REQUESTS=1500,MAX_TITLES=8000;
    static final long FRESH_MS=30*60_000L;
    private static final Set<String> VIRTUAL=new HashSet<>(Arrays.asList("recently added","recently played","last played","recently watched","recently viewed",
        "genre","genres","actor","actors","artist","artists","director","directors","year","years","date","by date","by genre","by year","by actor","by director",
        "by rating","rating","ratings","album","albums","playlist","playlists","favourites","favorites","keyword","keywords","series view"));
    private static final Set<String> ALL_VIEWS=new HashSet<>(Arrays.asList("all videos","all video","all movies","all films","all tv","all"));
    private static final Set<String> FOLDER_VIEWS=new HashSet<>(Arrays.asList("folder","folders","by folder","browse folders","folder view"));

    /** One Browse page; DlnaClient.browse on a TV, a fake in tests. */
    interface Browser{DlnaClient.Page browse(JSONObject source,String id,int offset)throws Exception;}
    static volatile Browser browser=DlnaClient::browse;
    interface Progress{void found(int titles,int folders);}

    /** One video. size (bytes) and duration (ms) are the server's, 0 when unknown: they tell a film from its sample. */
    static final class Entry {
        String id="",root="",title="",url="",art="",upnpClass="";final List<String> ratings=new ArrayList<>(),pathNames=new ArrayList<>(),pathIds=new ArrayList<>();long seen,size,duration;
        JSONObject json()throws JSONException{
            JSONObject o=new JSONObject().put("i",id).put("t",title).put("u",url).put("s",seen);
            if(!art.isEmpty())o.put("a",art);if(!upnpClass.isEmpty())o.put("c",upnpClass);if(size>0)o.put("z",size);if(duration>0)o.put("d",duration);
            if(!ratings.isEmpty())o.put("r",new JSONArray(ratings));if(!pathIds.isEmpty()){o.put("pn",new JSONArray(pathNames));o.put("pi",new JSONArray(pathIds));}
            return o;
        }
        static Entry of(JSONObject o,String root){return of(o,root,null);}
        /** With {@code pool}, repeated words (folder names and ids, ratings, classes) are kept once for the whole list. */
        static Entry of(JSONObject o,String root,Map<String,String> pool){
            Entry e=new Entry();e.id=o.optString("i");e.root=root;e.title=o.optString("t");e.url=o.optString("u");e.art=o.optString("a");e.upnpClass=one(pool,o.optString("c"));e.seen=o.optLong("s");e.size=Math.max(0,o.optLong("z"));e.duration=Math.max(0,o.optLong("d"));
            strings(o.optJSONArray("r"),e.ratings,pool);strings(o.optJSONArray("pn"),e.pathNames,pool);strings(o.optJSONArray("pi"),e.pathIds,pool);
            while(e.pathIds.size()<e.pathNames.size())e.pathNames.remove(e.pathNames.size()-1);while(e.pathNames.size()<e.pathIds.size())e.pathIds.remove(e.pathIds.size()-1);
            return e;
        }
    }
    static final class Root {final String id;long at,created;boolean complete=true;final List<Entry> entries=new ArrayList<>();Root(String id){this.id=id;}}
    static final class Index {
        final Map<String,Root> roots=new LinkedHashMap<>();
        /** A folder couldn't be read this time; what was known before is still shown. */
        boolean stale;
        long oldest(){long t=Long.MAX_VALUE;for(Root r:roots.values())t=Math.min(t,r.at);return roots.isEmpty()?0:t;}
        int size(){int n=0;for(Root r:roots.values())n+=r.entries.size();return n;}
    }

    // --- addresses without their host
    /** "http|50001|/path?query": the stream or picture without its host. Empty for anything else. */
    static String rel(String url){
        try{if(url==null||url.isEmpty())return "";URI u=new URI(url);if(u.getScheme()==null||u.getRawPath()==null)return "";
            int port=u.getPort()<0?("https".equals(u.getScheme())?443:80):u.getPort();
            return u.getScheme()+"|"+port+"|"+u.getRawPath()+(u.getRawQuery()==null?"":"?"+u.getRawQuery());}
        catch(Exception e){return "";}
    }
    /** Back to a full address on the saved server's host. */
    static String absolute(String rel,URI origin){
        if(rel==null||rel.isEmpty()||origin==null||origin.getHost()==null)return "";
        String[] p=rel.split("\\|",3);if(p.length<3||!(p[0].equals("http")||p[0].equals("https"))||!p[1].matches("\\d{1,5}")||!p[2].startsWith("/"))return "";
        return p[0]+"://"+origin.getHost()+":"+p[1]+p[2];
    }
    static URI origin(JSONObject source){try{return new URI(source.optString("control"));}catch(Exception e){return null;}}

    // --- walking
    private static final class Frame{final String id;final List<String> names,ids;final int depth;Frame(String id,List<String> names,List<String> ids,int depth){this.id=id;this.names=names;this.ids=ids;this.depth=depth;}}
    /**
     * Every video under one chosen folder. Throws when the folder itself can't be read; a folder further down that fails
     * is skipped and the result marked incomplete. Titles seen before keep the time they were first seen.
     */
    static Root walk(JSONObject source,String rootId,Set<String> otherRoots,Root previous,Progress progress,long now)throws Exception{
        Root out=new Root(rootId);out.at=now;out.created=previous!=null&&previous.created>0?previous.created:now;
        Map<String,Long> seenBefore=new HashMap<>();if(previous!=null)for(Entry e:previous.entries)seenBefore.put(e.id,e.seen);
        Deque<Frame> stack=new ArrayDeque<>();stack.push(new Frame(rootId,new ArrayList<>(),new ArrayList<>(),0));
        Set<String> visited=new HashSet<>(),urls=new HashSet<>();int folders=0,requests=0;Browser b=browser;int maxTitles=MemoryBudget.current().libraryTitles();
        while(!stack.isEmpty()){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            Frame f=stack.pop();if(!visited.add(f.id))continue;folders++;
            List<DlnaClient.Item> children=new ArrayList<>();int offset=0;
            try{
                while(requests<MAX_REQUESTS){
                    DlnaClient.Page page=b.browse(source,f.id,offset);requests++;children.addAll(page.items);
                    if(!page.more(offset)||page.returned<=0)break;offset=page.next(offset);
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                }
            }catch(InterruptedIOException e){throw e;}
            catch(Exception e){if(f.depth==0)throw e;out.complete=false;continue;}
            boolean folderView=false;for(DlnaClient.Item i:children)if(i.folder&&FOLDER_VIEWS.contains(lower(i.title)))folderView=true;
            List<Frame> next=new ArrayList<>();
            for(DlnaClient.Item i:children){
                if(i.folder){
                    String n=lower(i.title);
                    if(VIRTUAL.contains(n)||(folderView&&ALL_VIEWS.contains(n))||(otherRoots!=null&&otherRoots.contains(i.id)))continue;
                    if(f.depth+1>MAX_DEPTH||folders+stack.size()+next.size()>=MAX_FOLDERS){out.complete=false;continue;}
                    List<String> names=new ArrayList<>(f.names),ids=new ArrayList<>(f.ids);names.add(i.title);ids.add(i.id);
                    next.add(new Frame(i.id,names,ids,f.depth+1));continue;
                }
                if(out.entries.size()>=maxTitles){out.complete=false;break;}
                String rel=rel(i.url);if(rel.isEmpty()||!urls.add(rel))continue;
                Entry e=new Entry();e.id=i.id;e.root=rootId;e.title=i.title==null?"":i.title;e.url=rel;e.art=rel(i.art);e.upnpClass=i.upnpClass==null?"":i.upnpClass;
                e.size=i.size;e.duration=i.duration;e.ratings.addAll(i.ratings);e.pathNames.addAll(f.names);e.pathIds.addAll(f.ids);Long s=seenBefore.get(i.id);e.seen=s==null?now:s;
                out.entries.add(e);
            }
            // Depth first, in the server's own order.
            for(int k=next.size()-1;k>=0;k--)stack.push(next.get(k));
            if(progress!=null)progress.found(out.entries.size(),folders);
            if(requests>=MAX_REQUESTS){out.complete=false;break;}
        }
        return out;
    }
    private static String lower(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
    private static void strings(JSONArray a,List<String> into,Map<String,String> pool){if(a!=null)for(int i=0;i<a.length()&&i<64;i++)into.add(one(pool,a.optString(i)));}
    private static String one(Map<String,String> pool,String s){if(pool==null||s==null)return s;String kept=pool.putIfAbsent(s,s);return kept==null?s:kept;}

    // --- storage
    static String json(Index index)throws JSONException{
        JSONObject roots=new JSONObject();
        for(Root r:index.roots.values()){JSONArray items=new JSONArray();for(Entry e:r.entries)items.put(e.json());roots.put(r.id,new JSONObject().put("at",r.at).put("created",r.created).put("complete",r.complete).put("items",items));}
        return new JSONObject().put("v",1).put("roots",roots).toString();
    }
    static Index parse(String json){
        Index index=new Index();Map<String,String> pool=new HashMap<>();
        try{JSONObject o=new JSONObject(json);if(o.optInt("v")!=1)return index;JSONObject roots=o.optJSONObject("roots");if(roots==null)return index;
            for(Iterator<String> it=roots.keys();it.hasNext();){String id=it.next();JSONObject r=roots.optJSONObject(id);if(r==null)continue;Root root=new Root(id);
                root.at=r.optLong("at");root.created=r.optLong("created");root.complete=r.optBoolean("complete",true);JSONArray items=r.optJSONArray("items");
                if(items!=null)for(int i=0;i<items.length();i++){JSONObject e=items.optJSONObject(i);if(e!=null&&!e.optString("i").isEmpty())root.entries.add(Entry.of(e,id,pool));}
                index.roots.put(id,root);}
        }catch(Exception ignored){/* A damaged list is rebuilt. */}
        return index;
    }
    static File file(Context c,JSONObject source){
        String server=source.optString("udn").isEmpty()?source.optString("control"):source.optString("udn");
        return new File(new File(c.getFilesDir(),"library"),"index-"+hash(server)+".json");
    }
    static String hash(String s){
        try{byte[] d=MessageDigest.getInstance("SHA-256").digest(("unified-tv-library\n"+s).getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(int i=0;i<12;i++)b.append(String.format(Locale.ROOT,"%02x",d[i]&255));return b.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }

    /** One walk at a time for the whole app (WALK); reading the saved list never waits for a walk (MEMORY). */
    private static final Object WALK=new Object(),MEMORY=new Object();
    private static String memoryKey;private static Index memory;
    /** Titles found so far by a walk in progress anywhere in the app (Home's row and the library share one walk). */
    static volatile int walking=-1;

    /** The saved list for this library, without touching the network; empty when there is none yet. */
    static Index cached(Context c,LibraryFolders.Library lib){
        File f=file(c,lib.source);
        synchronized(MEMORY){
            if(f.getPath().equals(memoryKey)&&memory!=null)return keep(memory,lib);
            Index i=read(f);memoryKey=f.getPath();memory=i;return keep(i,lib);
        }
    }
    /** Only the folders chosen now (a removed folder's titles leave at once). */
    private static Index keep(Index all,LibraryFolders.Library lib){
        Index out=new Index();out.stale=all.stale;for(LibraryFolders.Folder f:lib.folders){Root r=all.roots.get(f.id);if(r!=null)out.roots.put(f.id,r);}return out;
    }
    private static Index read(File f){
        if(!f.isFile())return new Index();
        try(InputStream in=new FileInputStream(f)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return parse(out.toString("UTF-8"));}
        catch(Exception e){return new Index();}
    }
    private static void write(File f,Index index)throws Exception{
        File dir=f.getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("no folder");
        File tmp=new File(dir,f.getName()+".tmp");
        try(OutputStream o=new FileOutputStream(tmp)){o.write(json(index).getBytes(StandardCharsets.UTF_8));}
        if(!tmp.renameTo(f)){tmp.delete();throw new IOException("cannot save");}
    }
    /** True when a chosen folder has never been walked or was walked too long ago. */
    static boolean due(Index index,LibraryFolders.Library lib,long now){
        for(LibraryFolders.Folder f:lib.folders){Root r=index.roots.get(f.id);if(r==null||now-r.at>FRESH_MS||now<r.at)return true;}return false;
    }
    /**
     * Walks what is due (or everything, when forced), saves, and returns the list for the chosen folders. One walk at a
     * time for the whole app; a second caller waits and then gets the fresh list. Throws only when nothing at all could be
     * read and nothing was saved before.
     */
    static Index refresh(Context c,LibraryFolders.Library lib,boolean force,Progress progress)throws Exception{
        File f=file(c,lib.source);
        synchronized(WALK){
            Index all;synchronized(MEMORY){all=f.getPath().equals(memoryKey)&&memory!=null?memory:read(f);}long now=System.currentTimeMillis();
            if(!force&&!due(all,lib,now))return keep(all,lib);
            Index next=new Index();Exception failure=null;Set<String> chosen=new LinkedHashSet<>();for(LibraryFolders.Folder x:lib.folders)chosen.add(x.id);
            int[] before={0};
            try{
                for(LibraryFolders.Folder folder:lib.folders){
                    Root old=all.roots.get(folder.id);
                    if(!force&&old!=null&&now-old.at<=FRESH_MS&&now>=old.at){next.roots.put(folder.id,old);before[0]+=old.entries.size();continue;}
                    Set<String> others=new HashSet<>(chosen);others.remove(folder.id);
                    int base=before[0];walking=base;
                    try{Root r=walk(lib.source,folder.id,others,old,(t,n)->{walking=base+t;if(progress!=null)progress.found(base+t,n);},now);next.roots.put(folder.id,r);before[0]+=r.entries.size();}
                    catch(InterruptedIOException e){throw e;}
                    catch(Exception e){if(old!=null){next.roots.put(folder.id,old);next.stale=true;before[0]+=old.entries.size();}else{failure=e;next.stale=true;}}
                }
            }finally{walking=-1;}
            if(next.roots.isEmpty()&&failure!=null)throw failure;
            synchronized(MEMORY){try{write(f,next);}catch(Exception ignored){/* Still shown this time. */}memoryKey=f.getPath();memory=next;}
            return keep(next,lib);
        }
    }
    /**
     * onTrimMemory (PreviewApp): no screen is showing, so the saved list held in memory goes; it is read back from this
     * TV's storage the next time Home's row or the library asks (a walk in progress keeps its own copy).
     */
    static void trim(){synchronized(MEMORY){memory=null;memoryKey=null;}}
    /** Settings > Privacy (or a reset): forget every saved list. */
    static void clear(Context c){synchronized(MEMORY){memory=null;memoryKey=null;File[] fs=new File(c.getFilesDir(),"library").listFiles();if(fs!=null)for(File x:fs)x.delete();}}
}
