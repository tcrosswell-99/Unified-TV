package io.unifiedtv.preview;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settings → Appearance → Wallpaper → My Photos (docs/design/MY_PHOTOS.md, Phase 1): the owner picks one or more picture
 * folders on the NAS, found the way the film folders are (the film library's server first, else a search), with no
 * typing. The choice is saved encrypted in the Vault ("photos"), like the film folders; the Wallpaper only ever walks
 * inside it. Adults only: Child never reaches this screen or the pictures (WP-11). Shown as the glass sheet over a still
 * Home (OD-11), like Settings.
 */
public final class PhotoFoldersActivity extends GlassScreen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject record=new JSONObject();private int generation;private Future<?> task;private boolean changed;
    private DlnaScope.Trail trail=new DlnaScope.Trail();
    /** Folder pages are shorter than the Wallpaper's walk: each folder is a button. */
    static final int PICK_PAGE=48;

    @Override public void onCreate(Bundle state){super.onCreate(state);DlnaClient.lan=LocalDiscovery.lan(this);
        if(!WallpaperPolicy.photosAllowed(getSharedPreferences("unified-tv-preview",0).getString("profile",WallpaperPolicy.ADULT))){page("My Photos");text("Personal photos are for Adults. Kids shows Artwork.",20);button("Back",this::finish);return;}
        try{record=new Vault(this).read("photos");}catch(Exception e){record=new JSONObject();}
        if(roots().length()>0&&!record.optString("control").isEmpty())summary();else find(false);
    }
    private JSONArray roots(){JSONArray r=record.optJSONArray("roots");return r==null?new JSONArray():r;}
    private boolean active(int ticket){return ticket==generation&&!isFinishing()&&!isDestroyed();}
    private int nextTicket(){if(task!=null)task.cancel(true);task=null;handler.removeCallbacksAndMessages(null);return ++generation;}

    /** The chosen folders, each removable, and the plain-words limit of DLNA. */
    private void summary(){nextTicket();trail.clear();page("My Photos");
        text(WallpaperPolicy.PHOTOS_LIMIT,17);
        JSONArray roots=roots();text(roots.length()==0?"No folders chosen yet.":"Shown on the Wallpaper:",20);
        for(int i=0;i<roots.length();i++){JSONObject r=roots.optJSONObject(i);if(r==null)continue;String name=r.optString("name","Folder"),id=r.optString("id");
            button(name,()->new android.app.AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Stop showing this folder","Keep it"},(d,n)->{if(n==0){remove(id);summary();}}).show());}
        button("Add a folder",()->{trail.start(new DlnaScope.Frame("0",record.optString("name","Media server"),0),"");show();});
        button("Use another media server",()->find(true));
        button(changed?"Save and finish":"Done",this::finish);
    }
    private void remove(String id){JSONArray kept=new JSONArray(),roots=roots();for(int i=0;i<roots.length();i++){JSONObject r=roots.optJSONObject(i);if(r!=null&&!id.equals(r.optString("id")))kept.put(r);}try{record.put("roots",kept);}catch(JSONException ignored){}save();}

    /** The media server: the film library's own if it has one, otherwise (or when asked for another) a home network search. */
    private void find(boolean search){int ticket=nextTicket();trail.clear();page("My Photos");TextView status=text("Looking for your media server…",19);button("Search again",()->find(true));button("Back",this::finish);
        JSONObject films;try{films=new Vault(this).read("dlna");}catch(Exception e){films=new JSONObject();}
        JSONObject known=films.optBoolean("enabled")&&!films.optString("control").isEmpty()?films:null;
        List<DlnaClient.Device> ready=LocalDiscovery.fresh()?LocalDiscovery.nasFound:null;
        task=worker.submit(()->{List<JSONObject> servers=new ArrayList<>();Set<String> seen=new HashSet<>();DlnaClient.Report report=new DlnaClient.Report();
            if(known!=null){servers.add(server(known));seen.add(known.optString("udn"));}
            if(known==null||search)try{for(DlnaClient.Device d:!search&&ready!=null&&!ready.isEmpty()?ready:DlnaClient.discover(getApplicationContext(),true,report))if(seen.add(d.udn.isEmpty()?d.control:d.udn))servers.add(server(d.json()));}catch(Exception ignored){}
            runOnUiThread(()->{if(!active(ticket))return;
                if(servers.size()==1&&!search){choose(servers.get(0));return;}
                status.setText(servers.isEmpty()?report.plain():"Select your media server");
                for(JSONObject s:servers)button(s.optString("name").isEmpty()?"Media server":s.optString("name"),()->choose(s));});});
    }
    /** Only what is needed to reach the server again: no folders or settings of the film library come with it. */
    private static JSONObject server(JSONObject from){JSONObject s=new JSONObject();for(String k:new String[]{"name","location","control","service","udn"})try{s.put(k,from.optString(k));}catch(JSONException ignored){}return s;}
    private void choose(JSONObject server){
        // Folder ids belong to one server: another server starts a fresh choice.
        boolean same=!server.optString("udn").isEmpty()&&server.optString("udn").equals(record.optString("udn"));
        JSONArray roots=same?roots():new JSONArray();record=server;try{record.put("roots",roots);}catch(JSONException ignored){}
        trail.start(new DlnaScope.Frame("0",server.optString("name","Media server"),0),"");show();
    }

    private void show(){
        DlnaScope.Frame frame=trail.current();if(frame==null){summary();return;}
        String id=frame.id;int offset=frame.offset;int ticket=nextTicket();
        page("Choose picture folders");if(!id.equals("0")&&!frame.name.isEmpty())text(frame.name,22);TextView state=text("Loading…",19);
        // A chosen folder is unchosen the same way (owner, 25 October 2026): its row says so.
        if(!id.equals("0"))button(DlnaScope.root(roots(),id)!=null?"Selected — press OK to remove":"Show this folder on the Wallpaper",()->{
            try{if(DlnaScope.root(roots(),id)==null){JSONArray roots=roots();roots.put(new JSONObject().put("id",id).put("name",frame.name.isEmpty()?"Photos":frame.name));record.put("roots",roots);save();note("Folder chosen");}
                else{remove(id);note("This folder is no longer shown on the Wallpaper. Nothing was deleted from the media server.");}show();}catch(Exception e){note("Unable to choose that folder");}});
        button("Finished choosing",this::summary);
        button("Back",this::back);
        AtomicBoolean done=new AtomicBoolean();JSONObject from=record;
        task=worker.submit(()->{
            PhotoSource.Page page;JSONObject moved=null;
            try{page=browse(from,id,offset);}
            catch(Exception first){
                // The NAS may have restarted with a new address: look for the same server (same UDN) once, quietly.
                try{moved=first instanceof DlnaClient.Failure&&((DlnaClient.Failure)first).kind==DlnaClient.Failure.UNREACHABLE?DlnaClient.relocate(getApplicationContext(),from):null;
                    if(moved==null)throw first;page=browse(moved,id,offset);}
                catch(Exception e){if(done.compareAndSet(false,true))runOnUiThread(()->failed(ticket,state,e));return;}
            }
            PhotoSource.Page result=page;JSONObject relocated=moved;
            if(!done.compareAndSet(false,true))return;
            runOnUiThread(()->{if(!active(ticket))return;if(relocated!=null){record=relocated;if(roots().length()>0)save();}render(state,frame,result);});
        });
        handler.postDelayed(()->{if(active(ticket)&&done.compareAndSet(false,true)){if(task!=null)task.cancel(true);failed(ticket,state,new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE));}},DlnaLibraryActivity.LOAD_LIMIT_MS);
    }
    private static PhotoSource.Page browse(JSONObject server,String id,int offset)throws Exception{return PhotoSource.parse(DlnaClient.browseResult(server,id,offset,PICK_PAGE),DlnaClient.local(server.getString("control")));}
    private void render(TextView state,DlnaScope.Frame frame,PhotoSource.Page page){
        int pictures=page.photos.size();
        state.setText(page.folders.isEmpty()&&pictures==0?(page.more(frame.offset,PICK_PAGE)?"Nothing to show here yet.":"No pictures or folders here."):
            (pictures==0?"":pictures+(pictures==1?" picture":" pictures")+(page.more(frame.offset,PICK_PAGE)?" or more":"")+" here. ")+(page.folders.isEmpty()?"":"Open a folder, or choose this one."));
        for(DlnaClient.Item f:page.folders){String name=f.title.isEmpty()?"Folder":f.title;button((DlnaScope.root(roots(),f.id)!=null?"✓ ":"")+name,()->{trail.open(new DlnaScope.Frame(f.id,name,0));show();});}
        if(page.more(frame.offset,PICK_PAGE))button("Show more",()->{trail.page(page.next(frame.offset));show();});
    }
    private void failed(int ticket,TextView state,Exception e){
        if(!active(ticket))return;int kind=e instanceof DlnaClient.Failure?((DlnaClient.Failure)e).kind:DlnaClient.Failure.SERVER;
        state.setText(DlnaClient.Failure.plain(kind));button("Try again",this::show).requestFocus();
    }
    private void back(){if(trail.back()!=null)show();else summary();}
    /**
     * Saved at once, encrypted. Other folders mean the kept copies and the remembered walk go, so nothing from a folder
     * no longer chosen can appear (WP-03).
     */
    private void save(){
        try{new Vault(this).save("photos",record);changed=true;}catch(Exception e){note("Unable to save the folders securely");return;}
        WallpaperActivity.forgetPhotos();PhotoCache cache=new PhotoCache(new File(getNoBackupFilesDir(),"photos"),PhotoCache.LIMIT);new Thread(cache::clear,"photo-cache").start();
    }
    @Override public void onBackPressed(){if(!trail.isEmpty())back();else super.onBackPressed();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
