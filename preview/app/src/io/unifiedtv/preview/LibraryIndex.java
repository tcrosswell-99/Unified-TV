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
 * depth, folders, requests and titles; checked again in the background when it is older than half an hour.
 *
 * <p>Scanned once, kept for good (owner's TCL, 3 November 2026: about 2,000 films and episodes were found and shown, the
 * app was left, and a day later Films &amp; TV started again from nothing):
 * <ul>
 * <li>The list lives in the app's own files ({@code files/library}), never the cache folder, which Android empties when
 * storage runs short. It is written whole to a temporary file, synced and renamed over the old one, so a power cut or a
 * stopped app never leaves half a list.</li>
 * <li>It is saved while a walk goes on: once the first titles are found, about every {@link #saveMs}, and as soon as each
 * chosen folder has been read to the end. A 2 GB TV that stops Unified TV in the background keeps what was found, and the
 * next walk carries on from the folders still to read ({@link Walk}) instead of starting again.</li>
 * <li>A later check never empties what is shown: the saved list stays on screen ("Checking for new films…") while the
 * folders are read again, and only when a folder has been read to the end are its new titles added and its missing ones
 * removed. A media server that stops answering part-way ends the check with nothing removed; a single subfolder that
 * can't be read keeps the titles saved under it.</li>
 * <li>It is kept per media server by the server's UDN, which stays the same when the NAS gets a new address or port; a
 * server that sends no UDN is known by its name and host. The address itself is never part of the key.</li>
 * <li>Only an explicit action clears it: Settings &gt; Privacy, a reset, forgetting the media server ({@link #clear}),
 * or taking a folder out of the library (its titles leave at once and its part of the file at the next save).</li>
 * </ul>
 *
 * What is kept: the server's object ids and titles, the names of the folders a title sits in (for series and seasons),
 * ratings, and the stream and artwork addresses without their host (the host comes from the saved server each time, so
 * a NAS that changed address still plays). The file lives in the app's private storage, which is never backed up. No
 * address, key or server name is written into it.
 *
 * The walk itself is the app's background job (LibraryCollector); while it runs, {@link #latest} gives Home's row and the
 * library the titles found so far (a first walk) or the saved list (a check), so nothing waits for the whole list.
 *
 * Virtual views that only repeat the same files ("Recently added", "Genre", "Actor", "Year"...) are not walked, nor a
 * server's "All videos" when a real folder view sits beside it. A folder that is itself chosen is walked once, as its own.
 */
final class LibraryIndex {
    static final int MAX_DEPTH=8,MAX_FOLDERS=1500,MAX_REQUESTS=1500,MAX_TITLES=8000;
    static final long FRESH_MS=30*60_000L;
    /** A walk in progress shows what it has found after the first titles, then about this often. */
    static final int LIVE_STEP=200;static final long LIVE_MS=1500;
    /** A first walk in progress is saved about this often (and after each chosen folder); tests shorten it. */
    static volatile long saveMs=15_000;
    private static final Set<String> VIRTUAL=new HashSet<>(Arrays.asList("recently added","recently played","last played","recently watched","recently viewed",
        "genre","genres","actor","actors","artist","artists","director","directors","year","years","date","by date","by genre","by year","by actor","by director",
        "by rating","rating","ratings","album","albums","playlist","playlists","favourites","favorites","keyword","keywords","series view"));
    private static final Set<String> ALL_VIEWS=new HashSet<>(Arrays.asList("all videos","all video","all movies","all films","all tv","all"));
    private static final Set<String> FOLDER_VIEWS=new HashSet<>(Arrays.asList("folder","folders","by folder","browse folders","folder view"));

    /** One Browse page; DlnaClient.browse on a TV, a fake in tests. */
    interface Browser{DlnaClient.Page browse(JSONObject source,String id,int offset)throws Exception;}
    static volatile Browser browser=DlnaClient::browse;
    interface Progress{void found(int titles,int folders);}
    /**
     * Hears what a walk has found so far, as a copy: the first titles at once, then about every {@link #LIVE_STEP} titles
     * or {@link #LIVE_MS}. {@code urgent}: the media server stopped answering and the walk ends here (keep it now).
     */
    interface Found{void found(Walk sofar,boolean urgent);}

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
    /**
     * One chosen folder's titles. {@code entries} is what is shown: the list as last read to the end, or, while a first
     * walk has not finished ({@code at} 0), what it has found so far. {@code walk}: a walk part-way, carried on next time.
     * Never changed once shown (a walk makes a new Root), so screens read it without locks.
     */
    static final class Root {final String id;long at,created;boolean complete=true;final List<Entry> entries=new ArrayList<>();Walk walk;Root(String id){this.id=id;}}
    static final class Index {
        final Map<String,Root> roots=new LinkedHashMap<>();
        /** A folder couldn't be read this time; what was known before is still shown. */
        boolean stale;
        /** Why, when a folder couldn't be read (DlnaClient.Failure kinds); 0 when every folder answered. */
        int problem;
        long oldest(){long t=Long.MAX_VALUE;for(Root r:roots.values())t=Math.min(t,r.at);return roots.isEmpty()?0:t;}
        int size(){int n=0;for(Root r:roots.values())n+=r.entries.size();return n;}
    }
    /** A walk part-way through one chosen folder: what it has found, and the folders still to read (the next first). */
    static final class Walk {
        long at;boolean complete=true;int folders;final List<Entry> entries=new ArrayList<>();final List<Frame> pending=new ArrayList<>();
    }
    /** A folder to read. Part-way through it: the next page, and the subfolders and folder view seen on the pages before. */
    static final class Frame{
        final String id;final List<String> names,ids;final int depth;int offset;boolean folderView;final List<String[]> folders=new ArrayList<>();
        Frame(String id,List<String> names,List<String> ids,int depth){this.id=id;this.names=names;this.ids=ids;this.depth=depth;}
        Frame copy(){Frame f=new Frame(id,names,ids,depth);f.offset=offset;f.folderView=folderView;f.folders.addAll(folders);return f;}
        JSONObject json()throws JSONException{
            JSONArray kids=new JSONArray();for(String[] k:folders)kids.put(new JSONArray().put(k[0]).put(k[1]));
            return new JSONObject().put("i",id).put("pn",new JSONArray(names)).put("pi",new JSONArray(ids)).put("d",depth).put("o",offset).put("fv",folderView).put("f",kids);
        }
        static Frame of(JSONObject o,Map<String,String> pool){
            List<String> names=new ArrayList<>(),ids=new ArrayList<>();strings(o.optJSONArray("pn"),names,pool);strings(o.optJSONArray("pi"),ids,pool);
            while(ids.size()<names.size())names.remove(names.size()-1);while(names.size()<ids.size())ids.remove(ids.size()-1);
            Frame f=new Frame(o.optString("i"),names,ids,Math.max(0,Math.min(MAX_DEPTH,o.optInt("d"))));f.offset=Math.max(0,o.optInt("o"));f.folderView=o.optBoolean("fv");
            JSONArray kids=o.optJSONArray("f");
            if(kids!=null)for(int i=0;i<kids.length()&&i<MAX_FOLDERS;i++){JSONArray k=kids.optJSONArray(i);if(k!=null&&!k.optString(0).isEmpty())f.folders.add(new String[]{k.optString(0),k.optString(1)});}
            return f;
        }
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
    /**
     * Every video under one chosen folder. Throws when the folder itself can't be read; a folder further down that fails
     * is skipped and the result marked incomplete. Titles seen before keep the time they were first seen.
     */
    static Root walk(JSONObject source,String rootId,Set<String> otherRoots,Root previous,Progress progress,long now)throws Exception{
        return walk(source,rootId,otherRoots,previous,null,progress,now,null);
    }
    /**
     * As above, carrying on from {@code resume} (a walk cut short: its titles are kept and only the folders still to read
     * are read) and telling {@code found} what has been found so far. {@code previous}: the list as last read to the end.
     * A subfolder that can't be read keeps the titles {@code previous} had under it. A media server that stops answering
     * (or refuses this TV) ends the walk with an exception, so nothing it has stopped showing counts as removed.
     */
    static Root walk(JSONObject source,String rootId,Set<String> otherRoots,Root previous,Walk resume,Progress progress,long now,Found found)throws Exception{
        Walk w=new Walk();Deque<Frame> stack=new ArrayDeque<>();
        if(resume!=null){w.at=resume.at>0?resume.at:now;w.complete=resume.complete;w.folders=resume.folders;w.entries.addAll(resume.entries);for(int k=resume.pending.size()-1;k>=0;k--)stack.push(resume.pending.get(k).copy());}
        else{w.at=now;stack.push(new Frame(rootId,new ArrayList<>(),new ArrayList<>(),0));}
        Map<String,Long> seenBefore=new HashMap<>();if(previous!=null)for(Entry e:previous.entries)seenBefore.put(e.id,e.seen);
        Set<String> visited=new HashSet<>(),urls=new HashSet<>();for(Entry e:w.entries)urls.add(e.url);
        int requests=0;long[] live={-1,0};Browser b=browser;int maxTitles=MemoryBudget.current().libraryTitles();
        while(!stack.isEmpty()){
            stopIfInterrupted();
            Frame f=stack.peek();   // stays on the stack while it is read, so what is saved part-way includes it
            if(f.offset==0&&f.folders.isEmpty()){if(!visited.add(f.id)){stack.pop();continue;}w.folders++;}
            try{
                while(true){
                    if(requests>=MAX_REQUESTS){w.complete=false;break;}
                    DlnaClient.Page page=b.browse(source,f.id,f.offset);requests++;
                    for(DlnaClient.Item i:page.items){
                        if(i.folder){if(FOLDER_VIEWS.contains(lower(i.title)))f.folderView=true;f.folders.add(new String[]{i.id,i.title==null?"":i.title});continue;}
                        if(w.entries.size()>=maxTitles){w.complete=false;continue;}
                        String rel=rel(i.url);if(rel.isEmpty()||!urls.add(rel))continue;
                        Entry e=new Entry();e.id=i.id;e.root=rootId;e.title=i.title==null?"":i.title;e.url=rel;e.art=rel(i.art);e.upnpClass=i.upnpClass==null?"":i.upnpClass;
                        e.size=i.size;e.duration=i.duration;e.ratings.addAll(i.ratings);e.pathNames.addAll(f.names);e.pathIds.addAll(f.ids);Long s=seenBefore.get(i.id);e.seen=s==null?now:s;
                        w.entries.add(e);
                    }
                    boolean more=page.more(f.offset)&&page.returned>0;
                    if(more){f.offset=page.next(f.offset);report(w,stack,progress,found,live);}
                    LibraryCollector.between();   // the background job's gentle pace, and its pause when the TV is short of memory
                    if(!more)break;
                    stopIfInterrupted();
                }
            }catch(InterruptedIOException e){throw e;}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}
            catch(Exception e){
                stopIfInterrupted();
                if(f.depth==0||serverGone(e)){if(found!=null&&(resume!=null||!w.entries.isEmpty()))found.found(snapshot(w,stack),true);throw e;}
                stack.pop();w.complete=false;
                // This folder only: what was saved under it stays (a folder that can't be read is not a folder emptied).
                if(previous!=null)for(Entry old:previous.entries)if(old.pathIds.contains(f.id)&&w.entries.size()<maxTitles&&urls.add(old.url))w.entries.add(old);
                report(w,stack,progress,found,live);continue;
            }
            stack.pop();
            List<Frame> next=new ArrayList<>();
            for(String[] k:f.folders){
                String n=lower(k[1]);
                if(VIRTUAL.contains(n)||(f.folderView&&ALL_VIEWS.contains(n))||(otherRoots!=null&&otherRoots.contains(k[0])))continue;
                if(f.depth+1>MAX_DEPTH||w.folders+stack.size()+next.size()>=MAX_FOLDERS){w.complete=false;continue;}
                List<String> names=new ArrayList<>(f.names),ids=new ArrayList<>(f.ids);names.add(k[1]);ids.add(k[0]);
                next.add(new Frame(k[0],names,ids,f.depth+1));
            }
            // Depth first, in the server's own order.
            for(int k=next.size()-1;k>=0;k--)stack.push(next.get(k));
            report(w,stack,progress,found,live);
            if(requests>=MAX_REQUESTS){w.complete=false;break;}
        }
        Root out=new Root(rootId);out.at=now;out.created=previous!=null&&previous.created>0?previous.created:w.at;out.complete=w.complete;out.entries.addAll(w.entries);
        return out;
    }
    /** {@code live}: titles last handed on (-1: none yet) and when. */
    private static void report(Walk w,Deque<Frame> stack,Progress progress,Found found,long[] live){
        if(progress!=null)progress.found(w.entries.size(),w.folders);
        int n=w.entries.size();if(found==null||n==0)return;long clock=System.currentTimeMillis();
        if(live[0]<0||n-live[0]>=LIVE_STEP||clock-live[1]>=Math.min(LIVE_MS,saveMs)){found.found(snapshot(w,stack),false);live[0]=n;live[1]=clock;}
    }
    private static Walk snapshot(Walk w,Deque<Frame> stack){
        Walk s=new Walk();s.at=w.at;s.complete=w.complete;s.folders=w.folders;s.entries.addAll(w.entries);for(Frame f:stack)s.pending.add(f.copy());return s;
    }
    /** The media server itself stopped answering (or refused this TV), rather than one folder being unreadable. */
    static boolean serverGone(Exception e){
        if(e instanceof DlnaClient.Failure){int k=((DlnaClient.Failure)e).kind;return k==DlnaClient.Failure.UNREACHABLE||k==DlnaClient.Failure.VPN||k==DlnaClient.Failure.REFUSED;}
        return e instanceof IOException;
    }
    static int kind(Exception e){return e instanceof DlnaClient.Failure?((DlnaClient.Failure)e).kind:e instanceof IOException?DlnaClient.Failure.UNREACHABLE:DlnaClient.Failure.SERVER;}
    private static void stopIfInterrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    private static String lower(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
    private static void strings(JSONArray a,List<String> into,Map<String,String> pool){if(a!=null)for(int i=0;i<a.length()&&i<64;i++)into.add(one(pool,a.optString(i)));}
    private static String one(Map<String,String> pool,String s){if(pool==null||s==null)return s;String kept=pool.putIfAbsent(s,s);return kept==null?s:kept;}

    // --- storage
    static String json(Index index)throws JSONException{
        StringWriter out=new StringWriter();
        try{json(index,out);}catch(IOException e){throw new JSONException(String.valueOf(e.getMessage()));}
        return out.toString();
    }
    /** Written a title at a time, so a long list never needs one huge string in memory. */
    static void json(Index index,Writer w)throws IOException,JSONException{
        w.write("{\"v\":1,\"roots\":{");boolean first=true;
        for(Root r:index.roots.values()){
            if(!first)w.write(',');first=false;
            w.write(JSONObject.quote(r.id));w.write(":{\"at\":"+r.at+",\"created\":"+r.created+",\"complete\":"+r.complete+",\"items\":");
            items(w,r.at<=0&&r.walk!=null?Collections.emptyList():r.entries);   // a first walk's titles are written once, with the walk
            Walk k=r.walk;
            if(k!=null){
                JSONArray pending=new JSONArray();for(Frame f:k.pending)pending.put(f.json());
                w.write(",\"walk\":{\"at\":"+k.at+",\"complete\":"+k.complete+",\"folders\":"+k.folders+",\"pending\":");w.write(pending.toString());
                w.write(",\"items\":");items(w,k.entries);w.write('}');
            }
            w.write('}');
        }
        w.write("}}");
    }
    private static void items(Writer w,List<Entry> entries)throws IOException,JSONException{
        w.write('[');for(int i=0;i<entries.size();i++){if(i>0)w.write(',');w.write(entries.get(i).json().toString());}w.write(']');
    }
    static Index parse(String json){
        Index index=new Index();Map<String,String> pool=new HashMap<>();
        try{JSONObject o=new JSONObject(json);if(o.optInt("v")!=1)return index;JSONObject roots=o.optJSONObject("roots");if(roots==null)return index;
            for(Iterator<String> it=roots.keys();it.hasNext();){String id=it.next();JSONObject r=roots.optJSONObject(id);if(r==null)continue;Root root=new Root(id);
                root.at=r.optLong("at");root.created=r.optLong("created");root.complete=r.optBoolean("complete",true);
                entries(r.optJSONArray("items"),id,pool,root.entries);
                JSONObject k=r.optJSONObject("walk");
                if(k!=null){Walk walk=new Walk();walk.at=k.optLong("at");walk.complete=k.optBoolean("complete",true);walk.folders=Math.max(0,k.optInt("folders"));
                    JSONArray pending=k.optJSONArray("pending");
                    if(pending!=null)for(int i=0;i<pending.length()&&i<MAX_FOLDERS;i++){JSONObject f=pending.optJSONObject(i);if(f!=null&&!f.optString("i").isEmpty())walk.pending.add(Frame.of(f,pool));}
                    entries(k.optJSONArray("items"),id,pool,walk.entries);
                    root.walk=walk;if(root.at<=0)root.entries.addAll(walk.entries);}
                index.roots.put(id,root);}
        }catch(Exception ignored){/* A damaged list is rebuilt. */}
        return index;
    }
    private static void entries(JSONArray items,String root,Map<String,String> pool,List<Entry> into){
        if(items!=null)for(int i=0;i<items.length();i++){JSONObject e=items.optJSONObject(i);if(e!=null&&!e.optString("i").isEmpty())into.add(Entry.of(e,root,pool));}
    }
    /**
     * Which media server a list belongs to: its UDN, which stays the same when the NAS gets a new address or port. A
     * server that sends none is known by its name and host (so a new port keeps the list); failing that, its address.
     */
    static String server(JSONObject source){
        String udn=source.optString("udn").trim();if(!udn.isEmpty())return udn;
        String name=source.optString("name").trim();URI o=origin(source);
        return !name.isEmpty()&&o!=null&&o.getHost()!=null?"name:"+name+"@"+o.getHost().toLowerCase(Locale.ROOT):source.optString("control");
    }
    static File dir(Context c){return new File(c.getFilesDir(),"library");}
    static File file(Context c,JSONObject source){return new File(dir(c),"index-"+hash(server(source))+".json");}
    /** Before 3 November 2026 a server without a UDN had its list named after its full address: that list is kept. */
    private static void migrate(Context c,JSONObject source,File f){
        if(f.isFile()||!source.optString("udn").trim().isEmpty())return;
        File old=new File(dir(c),"index-"+hash(source.optString("control"))+".json");
        if(old.isFile()&&!old.equals(f)&&!old.renameTo(f))EventLog.record(c,EventLog.CONNECTION,"The saved library list couldn't be renamed");
    }
    static String hash(String s){
        try{byte[] d=MessageDigest.getInstance("SHA-256").digest(("unified-tv-library\n"+s).getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(int i=0;i<12;i++)b.append(String.format(Locale.ROOT,"%02x",d[i]&255));return b.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }

    /**
     * Locks (owner's Sony BRAVIA, build run 106, 3 November 2026: the library "locked the TV" while it was being checked).
     * WALK: one walk at a time. DISK: one read-and-parse or write-and-sync of the file at a time. MEMORY: only the moment a
     * new list is published (a reference swap and the rename that goes with it) and a clear; never held across reading,
     * parsing, writing or syncing. The main thread takes none of them for longer than that swap, and reads only the
     * published lists ({@link #loaded}, {@link #partial}): it never touches the file or the network.
     */
    private static final Object WALK=new Object(),DISK=new Object(),MEMORY=new Object();
    /** A list as published: the file it belongs to and the list itself. Never changed once made. */
    private static final class Loaded{final String key;final Index index;Loaded(String key,Index index){this.key=key;this.index=index;}}
    /** The saved list held in memory (the last one read or saved). */
    private static volatile Loaded loaded;
    /** Moves on with every {@link #clear}; a walk that started before one never writes its list back. Changed under MEMORY. */
    private static volatile int clears;
    /** Titles found so far by a walk in progress anywhere in the app (Home's row and the library share one walk). */
    static volatile int walking=-1;

    /** What a first walk has found so far (the folders done, the one being walked, earlier lists for the rest). */
    private static volatile Loaded partial;
    /**
     * The newest list there is, without touching the network: while a first walk is under way (LibraryCollector), the
     * titles it has found so far; otherwise the saved list (a check shows the saved list until a folder has been read to
     * the end). Off the main thread: the first call after a restart reads the file.
     */
    static Index latest(Context c,LibraryFolders.Library lib){
        Loaded p=partial;
        if(p!=null&&p.key.equals(file(c,lib.source).getPath()))return keep(p.index,lib);
        return cached(c,lib);
    }
    /** The saved list for this library, without touching the network; empty when there is none yet. Off the main thread. */
    static Index cached(Context c,LibraryFolders.Library lib){File f=file(c,lib.source);return keep(full(c,lib.source,f),lib);}
    /** Every folder's list for this server: from memory, else read and parsed (outside MEMORY) and published. */
    private static Index full(Context c,JSONObject source,File f){
        String key=f.getPath();Loaded m=loaded;if(m!=null&&m.key.equals(key))return m.index;
        OffMain.check("Reading the saved library list");
        synchronized(DISK){
            m=loaded;if(m!=null&&m.key.equals(key))return m.index;   // read meanwhile by another screen
            int at=clears;migrate(c,source,f);Index i=read(f);
            synchronized(MEMORY){if(clears==at){m=loaded;if(m!=null&&m.key.equals(key))return m.index;loaded=new Loaded(key,i);}}
            return i;
        }
    }
    /** Only the folders chosen now (a removed folder's titles leave at once). */
    private static Index keep(Index all,LibraryFolders.Library lib){
        Index out=new Index();out.stale=all.stale;out.problem=all.problem;for(LibraryFolders.Folder f:lib.folders){Root r=all.roots.get(f.id);if(r!=null)out.roots.put(f.id,r);}return out;
    }
    private static Index only(Index all,Set<String> ids){Index out=new Index();out.stale=all.stale;out.problem=all.problem;for(Root r:all.roots.values())if(ids.contains(r.id))out.roots.put(r.id,r);return out;}
    private static Index copy(Index i){Index out=new Index();out.stale=i.stale;out.problem=i.problem;out.roots.putAll(i.roots);return out;}
    private static Index read(File f){
        if(!f.isFile())return new Index();
        try(InputStream in=new FileInputStream(f)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return parse(out.toString("UTF-8"));}
        catch(Exception e){return new Index();}
    }
    /** Writes the list to a temporary file and syncs it to storage; the caller renames it over the old list. */
    private static File writeTemp(File f,Index index)throws Exception{
        File dir=f.getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("no folder");
        File tmp=new File(dir,f.getName()+".tmp");
        try(FileOutputStream fo=new FileOutputStream(tmp)){
            Writer w=new BufferedWriter(new OutputStreamWriter(fo,StandardCharsets.UTF_8),65536);json(index,w);w.flush();fo.getFD().sync();}
        return tmp;
    }
    /**
     * Saves whole or not at all (a temporary file, synced, renamed over the old list) and publishes it as the list in
     * memory. Writing and syncing hold only DISK; MEMORY is held just for the rename and the swap, so nothing that reads
     * the list waits on storage. Nothing is written once the list has been cleared (Privacy, a reset, a forgotten server).
     */
    private static void save(File f,Index index,int at){
        OffMain.check("Saving the library list");
        synchronized(DISK){
            if(clears!=at)return;
            File tmp=null;try{tmp=writeTemp(f,index);}catch(Exception ignored){/* still shown; saved at the next step */}
            synchronized(MEMORY){
                if(clears!=at){if(tmp!=null&&!tmp.delete())tmp.deleteOnExit();return;}
                if(tmp!=null&&!tmp.renameTo(f)&&!tmp.delete())tmp.deleteOnExit();
                loaded=new Loaded(f.getPath(),index);
            }
        }
    }
    /** True when a chosen folder has never been read to the end, was walked part-way, or was walked too long ago. */
    static boolean due(Index index,LibraryFolders.Library lib,long now){
        for(LibraryFolders.Folder f:lib.folders)if(due(index.roots.get(f.id),now))return true;return false;
    }
    private static boolean due(Root r,long now){return r==null||r.walk!=null||r.at<=0||now-r.at>FRESH_MS||now<r.at;}
    /** Every chosen folder has been read to the end at least once: a walk now is a check, and the saved list is shown. */
    static boolean settled(Index index,LibraryFolders.Library lib){
        if(lib.folders.isEmpty())return false;
        for(LibraryFolders.Folder f:lib.folders){Root r=index.roots.get(f.id);if(r==null||r.at<=0)return false;}return true;
    }
    /** A folder being walked, as shown: the list last read to the end (a check), or what a first walk has found so far. */
    private static Root inProgress(String id,Root committed,Walk sofar){
        Root r=new Root(id);r.walk=sofar;
        if(committed!=null){r.at=committed.at;r.created=committed.created;r.complete=committed.complete;r.entries.addAll(committed.entries);}
        else{r.created=sofar.at;r.complete=false;r.entries.addAll(sofar.entries);}
        return r;
    }
    /**
     * The folders whose titles this TV keeps for this server: every folder chosen for Adults or Kids. A walk for one
     * profile's library never drops the other's; a folder taken out of the library leaves at the next save.
     */
    private static Set<String> kept(Context c,LibraryFolders.Library lib){
        Set<String> ids=new HashSet<>();for(LibraryFolders.Folder f:lib.folders)ids.add(f.id);
        try{LibraryFolders.Library all=LibraryFolders.library(LibraryFolders.read(c,LibraryFolders.STORE),LibraryFolders.read(c,KidsPolicy.STORE),false);
            if(all.ready&&file(c,all.source).equals(file(c,lib.source)))for(LibraryFolders.Folder f:all.folders)ids.add(f.id);}
        catch(RuntimeException ignored){/* this library's folders only */}
        return ids;
    }
    /**
     * Walks what is due (or everything, when forced), saves, and returns the list for the chosen folders. One walk at a
     * time for the whole app; a second caller waits and then gets the fresh list. Throws only when nothing at all could be
     * read and nothing was saved before.
     *
     * <p>Never emptied first: a folder's saved titles stay until it has been read to the end again, and only then are its
     * new titles added and its missing ones removed. A walk cut short (the app stopped, the server gone quiet) is saved as
     * it stands and carried on next time; forced (Settings' Rescan), every folder is read again from the top.
     */
    static Index refresh(Context c,LibraryFolders.Library lib,boolean force,Progress progress)throws Exception{
        File f=file(c,lib.source);String key=f.getPath();
        synchronized(WALK){
            int at=clears;Index all=full(c,lib.source,f);
            long now=System.currentTimeMillis();Set<String> kept=kept(c,lib);
            if(!force&&!due(all,lib,now)){
                if(kept.containsAll(all.roots.keySet()))return keep(all,lib);
                Index pruned=only(all,kept);save(f,pruned,at);return keep(pruned,lib);   // a folder taken out of the library leaves the TV too
            }
            Index next=only(all,kept);next.stale=false;next.problem=0;Exception failure=null;
            Set<String> chosen=new LinkedHashSet<>();for(LibraryFolders.Folder x:lib.folders)chosen.add(x.id);
            int[] before={0};long[] savedAt={0};
            try{
                for(LibraryFolders.Folder folder:lib.folders){
                    Root old=all.roots.get(folder.id);
                    if(!force&&!due(old,now)){before[0]+=old.entries.size();continue;}
                    Root committed=old!=null&&old.at>0?old:null;Walk resume=force||old==null?null:old.walk;
                    Set<String> others=new HashSet<>(chosen);others.remove(folder.id);
                    int base=before[0];walking=base;
                    // A first walk: shown as it goes and saved every saveMs, so a stopped app keeps it. A check: the saved list
                    // is already shown and kept, so nothing is copied or written until the folder has been read to the end
                    // (owner's Sony, build run 106: rewriting the whole list every few seconds while checking it).
                    Found found=(sofar,urgent)->{
                        if(clears!=at||(committed!=null&&!urgent))return;
                        next.roots.put(folder.id,inProgress(folder.id,committed,sofar));Index shown=copy(next);
                        if(committed==null)partial=new Loaded(key,shown);
                        long clock=System.currentTimeMillis();
                        if(urgent||savedAt[0]==0||clock-savedAt[0]>=saveMs){save(f,shown,at);savedAt[0]=clock;}
                    };
                    try{
                        Root r=walk(lib.source,folder.id,others,committed,resume,(t,n)->{walking=base+t;if(progress!=null)progress.found(base+t,n);},now,found);
                        next.roots.put(folder.id,r);before[0]+=r.entries.size();
                        // Kept as soon as the folder has been read to the end: its new titles in, its missing ones out.
                        Index done=copy(next);if(partial!=null)partial=new Loaded(key,done);save(f,done,at);savedAt[0]=System.currentTimeMillis();
                    }
                    catch(InterruptedIOException e){throw e;}
                    catch(Exception e){
                        // What was saved (or found before the server went quiet) stays, and is carried on next time.
                        Root have=next.roots.get(folder.id);next.stale=true;next.problem=kind(e);
                        if(have!=null)before[0]+=have.entries.size();
                        if(have==null||have.entries.isEmpty())failure=e;
                    }
                }
                if(failure!=null&&keep(next,lib).size()==0)throw failure;
                if(clears!=at)throw new InterruptedIOException("cleared");   // Privacy, a reset or a forgotten server meanwhile
                save(f,next,at);
                if(clears!=at)throw new InterruptedIOException("cleared");
            }finally{walking=-1;partial=null;}   // the saved list now holds everything the walk found
            return keep(next,lib);
        }
    }
    /**
     * onTrimMemory (PreviewApp): no screen is showing, so the saved list held in memory goes; it is read back from this
     * TV's storage the next time Home's row or the library asks (a walk in progress keeps its own copy).
     */
    static void trim(){loaded=null;}
    /**
     * Settings > Privacy, a reset, or forgetting the media server: forget every saved list. The only way one is cleared.
     * A save under way finds the list cleared at its rename and deletes its own temporary file.
     */
    static void clear(Context c){LibraryCollector.cancel();synchronized(MEMORY){clears++;loaded=null;partial=null;File[] fs=dir(c).listFiles();if(fs!=null)for(File x:fs)if(!x.delete())x.deleteOnExit();}}
}
