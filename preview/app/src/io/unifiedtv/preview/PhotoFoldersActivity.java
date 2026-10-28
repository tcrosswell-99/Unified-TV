package io.unifiedtv.preview;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settings → Appearance → Wallpaper → My Photos (docs/design/MY_PHOTOS.md, Phase 1): the owner picks picture folders on
 * the NAS, found the way the film folders are (the film library's server first, else a search), with no typing. The
 * choice is saved encrypted in the Vault ("photos"), like the film folders; the Wallpaper only ever walks inside it.
 * Adults only: Child never reaches this screen or the pictures (WP-11). Shown as the glass sheet over a still Home
 * (OD-19), like Settings.
 *
 * <p>Made simple (owner's TCL, build run 99: going through folders one by one, and Back unclear):
 * <pre>Use all my photos              one OK: the server's picture folders, saved (PhotoSource.allPhotos)
 * Or choose folders              one list with ticks: OK ticks or unticks, Right opens a folder to tick inside it
 *   "2 folders ticked, about 3,400 photos"   (the photos when the server says how many, which costs nothing)
 *   Done                         saves what is ticked and closes</pre>
 * Back goes up a folder, ticks kept; from the top of the list it saves what is ticked and says so in a short line. Leaving
 * any other way (Home) saves too, so a choice is never lost without a word.
 */
public final class PhotoFoldersActivity extends GlassScreen {
    /** One page of folders and pictures; DlnaClient on a TV, a fake in tests (no network). */
    interface Lister{PhotoSource.Page list(JSONObject server,String id,int offset)throws Exception;}
    static volatile Lister lister=PhotoFoldersActivity::browse;
    /** Finds media servers on the home network (search: look again rather than use what was just found); tests replace it. */
    interface Finder{List<DlnaClient.Device> find(android.content.Context c,DlnaClient.Report report,boolean search)throws Exception;}
    static volatile Finder finder=(c,report,search)->{List<DlnaClient.Device> ready=LocalDiscovery.fresh()?LocalDiscovery.nasFound:null;
        return !search&&ready!=null&&!ready.isEmpty()?ready:DlnaClient.discover(c,true,report);};

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject record=new JSONObject();private int generation;private Future<?> task;
    private DlnaScope.Trail trail=new DlnaScope.Trail();
    /** What is ticked while choosing, by folder id (name and, when known, how many things are inside), in order. */
    private final LinkedHashMap<String,JSONObject> ticked=new LinkedHashMap<>();private boolean unsaved;
    /** The choice as it is saved now: another server with nothing ticked never replaces it (review, 27 October 2026). */
    private JSONObject saved=new JSONObject();
    private TextView countLine;
    /** Folder pages are shorter than the Wallpaper's walk: each folder is a row. */
    static final int PICK_PAGE=48;

    @Override public void onCreate(Bundle state){super.onCreate(state);DlnaClient.lan=LocalDiscovery.lan(this);
        if(!WallpaperPolicy.photosAllowed(getSharedPreferences("unified-tv-preview",0).getString("profile",WallpaperPolicy.ADULT))){page("My Photos");text("Personal photos are for Adults. Kids shows Artwork.",20);button("Back",this::finish);return;}
        try{record=new Vault(this).read("photos");}catch(Exception e){record=new JSONObject();}
        saved=copy(record);
        if(roots().length()>0&&!record.optString("control").isEmpty())summary();else find(false);
    }
    private JSONArray roots(){JSONArray r=record.optJSONArray("roots");return r==null?new JSONArray():r;}
    private boolean active(int ticket){return ticket==generation&&!isFinishing()&&!isDestroyed();}
    private int nextTicket(){if(task!=null)task.cancel(true);task=null;handler.removeCallbacksAndMessages(null);return ++generation;}
    /** For tests: the folder ids ticked now (not necessarily saved yet), and whether the chooser is showing. */
    List<String> ticked(){return new ArrayList<>(ticked.keySet());}
    boolean choosing(){return !trail.isEmpty();}

    /** The chosen folders, each removable, how many, and the plain-words limit of DLNA. */
    private void summary(){nextTicket();trail.clear();countLine=null;page("My Photos",record.optString("name").isEmpty()?null:"On "+record.optString("name"));
        JSONArray roots=roots();int photos=0;boolean known=true;
        for(int i=0;i<roots.length();i++){JSONObject r=roots.optJSONObject(i);if(r==null)continue;if(r.has("count"))photos+=r.optInt("count");else known=false;}
        card();text(PhotoSource.count(roots.length(),photos,known)+(roots.length()>0?" on the Wallpaper.":"."),20);
        for(int i=0;i<roots.length();i++){JSONObject r=roots.optJSONObject(i);if(r==null)continue;String name=r.optString("name","Folder"),id=r.optString("id");
            row(name,null,false,()->new android.app.AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Stop showing this folder","Keep it"},(d,n)->{if(n==0){remove(id);summary();}}).show());}
        card();
        row("Change folders",null,true,()->choose(record));
        row("Use another media server",null,true,()->find(true));
        row("Done",null,false,this::finish);
        text(WallpaperPolicy.PHOTOS_LIMIT,15);
    }
    private void remove(String id){JSONArray kept=new JSONArray(),roots=roots();for(int i=0;i<roots.length();i++){JSONObject r=roots.optJSONObject(i);if(r!=null&&!id.equals(r.optString("id")))kept.put(r);}try{record.put("roots",kept);}catch(JSONException ignored){}save();}

    /** The media server: the film library's own if it has one, otherwise (or when asked for another) a home network search. */
    private void find(boolean search){int ticket=nextTicket();trail.clear();countLine=null;page("My Photos");TextView status=busy("Looking for your media server…");button("Search again",()->find(true));button("Back",this::finish);
        JSONObject films;try{films=new Vault(this).read("dlna");}catch(Exception e){films=new JSONObject();}
        JSONObject known=films.optBoolean("enabled")&&!films.optString("control").isEmpty()?films:null;
        task=worker.submit(()->{List<JSONObject> servers=new ArrayList<>();Set<String> seen=new HashSet<>();DlnaClient.Report report=new DlnaClient.Report();
            if(known!=null){servers.add(server(known));seen.add(known.optString("udn"));}
            if(known==null||search)try{for(DlnaClient.Device d:finder.find(getApplicationContext(),report,search))if(seen.add(d.udn.isEmpty()?d.control:d.udn))servers.add(server(d.json()));}catch(Exception ignored){}
            runOnUiThread(()->{if(!active(ticket))return;
                if(servers.size()==1&&!search){choose(servers.get(0));return;}
                status.setText(servers.isEmpty()?report.plain():"Select your media server");
                for(JSONObject s:servers)button(s.optString("name").isEmpty()?"Media server":s.optString("name"),()->choose(s));});});
    }
    /** Only what is needed to reach the server again: no folders or settings of the film library come with it. */
    private static JSONObject server(JSONObject from){JSONObject s=new JSONObject();for(String k:new String[]{"name","location","control","service","udn"})try{s.put(k,from.optString(k));}catch(JSONException ignored){}return s;}
    /** The chooser for this server, starting at its top, with what is chosen now already ticked. */
    private void choose(JSONObject server){
        // Folder ids belong to one server: another server starts a fresh choice.
        String udn=server.optString("udn");
        boolean same=udn.isEmpty()?!server.optString("control").isEmpty()&&server.optString("control").equals(record.optString("control")):udn.equals(record.optString("udn"));
        JSONArray roots=same?roots():new JSONArray();JSONObject next=server(server);try{next.put("roots",roots);}catch(JSONException ignored){}
        // Another server alone changes nothing saved: only a folder ticked on it does (keep()).
        record=next;ticked.clear();
        for(int i=0;i<roots.length();i++){JSONObject r=roots.optJSONObject(i);if(r!=null&&!r.optString("id").isEmpty())ticked.put(r.optString("id"),r);}
        trail.start(new DlnaScope.Frame("0",server.optString("name","Media server"),0),"");show();
    }

    // --- choosing: one list with ticks
    private void show(){
        DlnaScope.Frame frame=trail.current();if(frame==null){summary();return;}
        String id=frame.id;int offset=frame.offset;int ticket=nextTicket();boolean top="0".equals(id);
        page(top?"My Photos":frame.name,top?"Folders on "+record.optString("name","your media server"):"Inside a folder");
        countLine=null;TextView state=busy("Loading folders…");
        AtomicBoolean finished=new AtomicBoolean();JSONObject from=record;
        task=worker.submit(()->{
            PhotoSource.Page page;JSONObject moved=null;
            try{page=lister.list(from,id,offset);}
            catch(Exception first){
                // The NAS may have restarted with a new address: look for the same server (same UDN) once, quietly.
                try{moved=first instanceof DlnaClient.Failure&&((DlnaClient.Failure)first).kind==DlnaClient.Failure.UNREACHABLE?DlnaClient.relocate(getApplicationContext(),from):null;
                    if(moved==null)throw first;page=lister.list(moved,id,offset);}
                catch(Exception e){if(finished.compareAndSet(false,true))runOnUiThread(()->failed(ticket,state,e));return;}
            }
            PhotoSource.Page result=page;JSONObject relocated=moved;
            if(!finished.compareAndSet(false,true))return;
            runOnUiThread(()->{if(!active(ticket))return;if(relocated!=null){JSONArray keep=roots();record=server(relocated);try{record.put("roots",keep);}catch(JSONException ignored){}}
                ((View)state.getParent()).setVisibility(View.GONE);render(frame,result);});
        });
        handler.postDelayed(()->{if(active(ticket)&&finished.compareAndSet(false,true)){if(task!=null)task.cancel(true);failed(ticket,state,new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE));}},DlnaLibraryActivity.LOAD_LIMIT_MS);
    }
    private static PhotoSource.Page browse(JSONObject server,String id,int offset)throws Exception{return PhotoSource.parse(DlnaClient.browseResult(server,id,offset,PICK_PAGE),DlnaClient.local(server.getString("control")));}
    private void render(DlnaScope.Frame frame,PhotoSource.Page page){
        boolean top="0".equals(frame.id)&&frame.offset==0;Button first=null;
        if(top){
            List<DlnaClient.Item> all=PhotoSource.allPhotos(page.folders);
            if(!all.isEmpty()){card();first=row("Use all my photos",null,false,()->useAll(all,page));
                List<String> names=new ArrayList<>();for(DlnaClient.Item f:all)names.add(f.title.isEmpty()?"Folder":f.title);
                text("Everything in "+String.join(", ",names)+", in one go.",15);}
            group("Or choose folders");
        }
        card();
        int pictures=page.photos.size();
        if(page.folders.isEmpty())text(pictures>0?pictures+(pictures==1?" picture":" pictures")+" here and no folders inside. Back, then tick this folder.":"No folders here.",15);
        else text("OK ticks a folder. Right opens it, to tick folders inside.",15);
        for(DlnaClient.Item f:page.folders){
            String name=f.title.isEmpty()?"Folder":f.title;Integer n=page.counts.get(f.id);
            Button b=row(name,ticked.containsKey(f.id)?"Ticked":null,true,null);
            b.setOnClickListener(v->{tick(f.id,name,n);b.setCompoundDrawablesRelative(null,null,new SettingsGlass.Trailing(ticked.containsKey(f.id)?"Ticked":null,true,false,textPx(19)),null);
                b.setContentDescription(ticked.containsKey(f.id)?name+", Ticked":name);});
            b.setOnKeyListener((v,code,e)->{if(code!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){trail.open(new DlnaScope.Frame(f.id,name,0));show();}return true;});
            if(first==null)first=b;
        }
        if(page.more(frame.offset,PICK_PAGE))row("Show more folders",null,false,()->{trail.page(page.next(frame.offset));show();});
        footer();
        View now=getCurrentFocus();if(first!=null&&(now==null||!isInside(now)||now.getParent()==header))first.requestFocus();
    }
    private void tick(String id,String name,Integer count){
        if(ticked.remove(id)==null){JSONObject r=new JSONObject();try{r.put("id",id).put("name",name);if(count!=null)r.put("count",count);}catch(JSONException ignored){}ticked.put(id,r);}
        unsaved=true;if(countLine!=null)countLine.setText(countWords());
    }
    /** "2 folders ticked, about 3,400 photos": the photos only when the server said how many each folder holds. */
    private String countWords(){
        if(ticked.isEmpty())return "No folders ticked yet.";
        int photos=0;boolean known=true;for(JSONObject r:ticked.values()){if(r.has("count"))photos+=r.optInt("count");else known=false;}
        return PhotoSource.count(ticked.size(),photos,known).replaceFirst("folders?","$0 ticked")+".";
    }
    /** "Use all my photos": the server's picture folders, saved in one OK. */
    private void useAll(List<DlnaClient.Item> all,PhotoSource.Page page){
        ticked.clear();for(DlnaClient.Item f:all){String name=f.title.isEmpty()?"Folder":f.title;Integer n=page.counts.get(f.id);tick(f.id,name,n);}
        if(keep())note("All your photos are on the Wallpaper.");summary();
    }
    /** Done: saves what is ticked and closes, saying so. */
    private void done(){if(keep()||!ticked.isEmpty())note(saidSaved());else keepOld();finish();}
    private String saidSaved(){return ticked.isEmpty()?"No photo folders chosen.":"Saved: "+(ticked.size()==1?"1 folder":ticked.size()+" folders")+" on the Wallpaper.";}
    /** Writes the ticked folders when they changed; true when something was saved. */
    private boolean keep(){
        if(!unsaved)return false;
        if(ticked.isEmpty()&&otherServer()){unsaved=false;return false;}   // nothing ticked on another server: the old choice stays
        JSONArray roots=new JSONArray();for(JSONObject r:ticked.values())roots.put(r);
        try{record.put("roots",roots);}catch(JSONException ignored){}
        if(!save())return false;unsaved=false;return true;
    }
    /** Under the list: how much is ticked, Done, and what Back does. */
    private void footer(){card();countLine=text(countWords(),18);row("Done",null,false,this::done);text("Back saves what is ticked, too.",15);}
    private void failed(int ticket,TextView state,Exception e){
        if(!active(ticket))return;int kind=e instanceof DlnaClient.Failure?((DlnaClient.Failure)e).kind:DlnaClient.Failure.SERVER;
        state.setText(DlnaClient.Failure.plain(kind));row("Try again",null,false,this::show).requestFocus();footer();
    }
    /**
     * Saved at once, encrypted. Other folders mean the kept copies and the remembered walk go, so nothing from a folder
     * no longer chosen can appear (WP-03).
     */
    private boolean save(){
        try{new Vault(this).save("photos",record);}catch(Exception e){note("Unable to save the folders securely");return false;}
        saved=copy(record);
        WallpaperActivity.forgetPhotos();PhotoCache cache=new PhotoCache(new File(getNoBackupFilesDir(),"photos"),PhotoCache.LIMIT);new Thread(cache::clear,"photo-cache").start();
        return true;
    }
    /** Back: up a folder (ticks kept); from the top of the list, save what is ticked, say so, and go back. */
    @Override public void onBackPressed(){
        if(trail.isEmpty()){super.onBackPressed();return;}
        if(trail.back()!=null){show();return;}
        if(keep())note(saidSaved());else keepOld();
        if(roots().length()>0)summary();else finish();
    }
    /** Leaving another way (Home, the TV's own button) still keeps what was ticked. */
    @Override protected void onStop(){if(!trail.isEmpty()){if(keep())note(saidSaved());else keepOld();}super.onStop();}
    /** Looking at another server whose folders were left unticked: the folders saved before stay, and the sheet says so. */
    private boolean otherServer(){
        if(roots(saved).length()==0)return false;
        String a=record.optString("udn"),b=saved.optString("udn");
        return !(a.isEmpty()&&b.isEmpty()?record.optString("control").equals(saved.optString("control")):a.equals(b));
    }
    private void keepOld(){
        if(!otherServer()||!ticked.isEmpty())return;
        String now=record.optString("name","this media server"),before=saved.optString("name","your media server");
        record=copy(saved);ticked.clear();unsaved=false;
        note("Nothing ticked on "+now+", so your photo folders on "+before+" stay as they were.");
    }
    private static JSONArray roots(JSONObject r){JSONArray a=r.optJSONArray("roots");return a==null?new JSONArray():a;}
    private static JSONObject copy(JSONObject o){try{return new JSONObject(o.toString());}catch(JSONException e){return new JSONObject();}}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
