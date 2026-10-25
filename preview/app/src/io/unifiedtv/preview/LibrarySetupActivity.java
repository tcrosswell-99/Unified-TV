package io.unifiedtv.preview;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Choosing the folders for Your Films & TV, as the same right-hand glass sheet as Settings (module 13): Home stays still
 * underneath. Zero configuration: the media server is found on the home network and offered by name only; no address.
 *
 * <pre>Your folders (server, chosen folders with their Kids mark, Add a folder, Save)
 *   → a folder: This folder is for Kids (On/Off) · Remove from library (asks first; nothing on the server is deleted)
 *   → Add a folder: the server's folders, one level at a time: Add this folder · Add this folder for Kids
 *   → Change media server: search, then the server's folders</pre>
 *
 * Nothing is saved until Save (the adult PIN, when one is set); Back with unsaved changes asks. Kids never reach this
 * screen. Adding a folder that contains, or sits inside, one already chosen says plainly that its titles show once.
 */
public final class LibrarySetupActivity extends GlassScreen {
    enum Page{MANAGE,DISCOVER,BROWSE,FOLDER,SAVED,KIDS}
    /** Finds media servers; tests replace it (no network in tests). */
    interface Finder{List<DlnaClient.Device> find(Context c,DlnaClient.Report report)throws Exception;}
    static volatile Finder finder=(c,report)->{List<DlnaClient.Device> ready=LocalDiscovery.fresh()?LocalDiscovery.nasFound:null;return ready!=null&&!ready.isEmpty()?ready:DlnaClient.discover(c,true,report);};

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Future<?> task;private int ticket;
    private Page page=Page.MANAGE;
    private JSONObject server=new JSONObject();private List<LibraryFolders.Folder> folders=new ArrayList<>();private boolean dirty;
    /** Folders opened while adding, from the server's top ("0") down. */
    private final List<DlnaScope.Frame> trail=new ArrayList<>();
    private String folderId="",focusId="";private boolean autoChosen;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);DlnaClient.lan=LocalDiscovery.lan(this);
        if(Profiles.kids(this)){page=Page.KIDS;render();return;}
        JSONObject household=LibraryFolders.read(this,LibraryFolders.STORE),kids=LibraryFolders.read(this,KidsPolicy.STORE);
        LibraryFolders.Library lib=LibraryFolders.library(household,kids,false);
        if(lib.ready){server=LibraryFolders.serverOnly(lib.source);folders=new ArrayList<>(lib.folders);}
        if(server.optString("control").isEmpty())discover();else render();
    }
    @Override protected void onDestroy(){ticket++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    /** For tests: where the sheet is, and the folders as they would be saved. */
    Page page(){return page;}
    List<LibraryFolders.Folder> folders(){return folders;}

    // --- pages
    private void render(){
        ticket++;if(task!=null)task.cancel(true);clear();
        switch(page){
            case KIDS:head("Your Films & TV",null);card();text(KidsPolicy.ASK_ADULT,25);text("An adult chooses the folders, and marks the ones for Kids, from the Adults profile.",15);button("Close",this::close);break;
            case MANAGE:manage();break;
            case FOLDER:folder();break;
            case SAVED:saved();break;
            default:break;
        }
        focusFirst();
    }
    private void manage(){
        head("Your Films & TV","Folders on "+serverName());
        group("Media server");card();
        row(serverName(),"Change",true,()->{if(folders.isEmpty()){discover();return;}
            new AlertDialog.Builder(this).setTitle("Change media server?").setMessage("The folders chosen on "+serverName()+" leave the list. Nothing is deleted from the media server.")
                .setPositiveButton("Change",(d,w)->discover()).setNegativeButton("Cancel",null).show();});
        group(folders.isEmpty()?"Folders":folders.size()==1?"1 folder in your library":folders.size()+" folders in your library");card();
        Button again=null;
        if(folders.isEmpty())text("No folders yet. Add the folders that hold your films and programmes.",15);
        else text("Choose a folder to remove it, or to mark it for Kids.",15);
        for(LibraryFolders.Folder f:folders){Button b=row(f.name,f.kids?"For Kids":null,true,()->{folderId=f.id;page=Page.FOLDER;render();});if(f.id.equals(focusId))again=b;}
        row("Add a folder",null,true,()->browse(new DlnaScope.Frame("0",serverName(),0),true));
        if(dirty){card();Button save=row("Save changes",null,false,this::save);text("Not saved yet.",15);if(again==null&&focusId.equals("save"))again=save;}
        else if(!folders.isEmpty()){card();row("Open Your Films & TV",null,false,this::openLibrary);}
        text("Kids see only the folders marked for Kids, and only titles whose rating suits Kids. Adults see everything.",15);
        Button target=again;focusId="";if(target!=null)body.post(target::requestFocus);
    }
    private void folder(){
        LibraryFolders.Folder f=LibraryFolders.find(folders,folderId);if(f==null){page=Page.MANAGE;render();return;}
        head(f.name,"In your library");card();
        row("This folder is for Kids",f.kids?"On":"Off",false,()->{LibraryFolders.Change c=LibraryFolders.kids(folders,f.id,!f.kids);changed(c);render();});
        text(f.kids?"Kids can watch titles here whose rating suits Kids.":"Only Adults see this folder.",15);
        card();
        row("Remove from library",null,false,()->new AlertDialog.Builder(this).setTitle("Remove “"+f.name+"”?").setMessage("It leaves Your Films & TV. Nothing is deleted from the media server.")
            .setPositiveButton("Remove",(d,w)->{changed(LibraryFolders.remove(folders,f.id));page=Page.MANAGE;render();}).setNegativeButton("Cancel",null).show());
    }
    private void saved(){
        head("Saved","Your Films & TV");card();
        text(folders.size()==1?"1 folder is in your library.":folders.size()+" folders are in your library.",25);
        text("The first time, the TV makes a list of your titles. That can take a minute; it says how far it has got.",15);
        row("Open Your Films & TV",null,false,this::openLibrary);row("Done",null,false,this::close);
    }
    private void changed(LibraryFolders.Change c){folders=c.folders;dirty=true;if(c.message!=null&&!c.message.isEmpty())note(c.message);}
    private String serverName(){String n=server.optString("name").trim();return n.isEmpty()?"your media server":n;}
    private void save(){
        EditLock.ask(this,()->{
            try{LibraryFolders.save(this,server,folders);prefs.edit().putString("librarySource","dlna").apply();dirty=false;page=Page.SAVED;render();}
            catch(Exception e){note("Couldn't save on this TV. Nothing was changed.");}
        });
    }
    private void openLibrary(){
        if(dirty){save();return;}
        try{startActivity(new Intent(this,DlnaLibraryActivity.class));}catch(RuntimeException ignored){}
        close();
    }

    // --- finding the server
    private void discover(){
        page=Page.DISCOVER;int mine=++ticket;if(task!=null)task.cancel(true);clear();
        head("Find your media server","On your home network");card();
        TextView state=busy("Looking for your media server…");
        LinearLayout found=new LinearLayout(this);found.setOrientation(LinearLayout.VERTICAL);column.addView(found);
        Context app=getApplicationContext();
        task=worker.submit(()->{DlnaClient.Report report=new DlnaClient.Report();List<DlnaClient.Device> devices;Exception failed=null;
            try{devices=finder.find(app,report);}catch(Exception e){devices=new ArrayList<>();failed=e;}
            List<DlnaClient.Device> list=devices;Exception problem=failed;
            handler.post(()->{if(mine!=ticket||isFinishing())return;
                ((View)state.getParent()).setVisibility(View.GONE);column=found;
                if(list.isEmpty()){text(problem!=null?"The TV couldn't search your home network just now.":report.plain(),25);
                    for(String line:new String[]{"The NAS is switched on and has finished starting.","The TV and the NAS are on the same home network, not a guest one.","The NAS's media server (DLNA / UPnP) is on and sharing your video folders.","If NordVPN is on, allow it to see devices on your home network."})text("•  "+line,15);
                    row("Search again",null,false,this::discover).requestFocus();return;}
                // One media server and nothing chosen yet: go straight to its folders.
                if(list.size()==1&&folders.isEmpty()&&!autoChosen){autoChosen=true;choose(list.get(0));return;}
                text("Choose your media server",25);List<String> names=DlnaClient.labels(list);Button first=null;
                for(int i=0;i<list.size();i++){DlnaClient.Device d=list.get(i);Button b=row(names.get(i),null,true,()->choose(d));if(first==null)first=b;}
                if(first!=null)first.requestFocus();});
        });
    }
    private void choose(DlnaClient.Device d){
        try{JSONObject next=LibraryFolders.serverOnly(d.json());
            if(!LibraryFolders.sameServer(next,server)&&!folders.isEmpty()){folders=new ArrayList<>();dirty=true;}
            if(!LibraryFolders.sameServer(next,server))dirty=true;server=next;
            browse(new DlnaScope.Frame("0",serverName(),0),true);}
        catch(Exception e){note("Unable to choose that media server.");}
    }

    // --- adding folders: one level at a time
    private void browse(DlnaScope.Frame frame,boolean fresh){
        if(fresh)trail.clear();
        if(trail.isEmpty()||!trail.get(trail.size()-1).id.equals(frame.id))trail.add(frame);else trail.set(trail.size()-1,frame);
        page=Page.BROWSE;int mine=++ticket;if(task!=null)task.cancel(true);clear();
        boolean top="0".equals(frame.id);
        head(top?"Add a folder":frame.name,top?"Folders on "+serverName():"Add a folder");
        if(!top){card();LibraryFolders.Folder chosen=LibraryFolders.find(folders,frame.id);
            if(chosen!=null){
                // Unselecting works the way selecting did, before anything is saved.
                row("Selected — press OK to remove",chosen.kids?"For Kids":null,false,()->{changed(LibraryFolders.remove(folders,chosen.id));browse(frame,false);});
                row("This folder is for Kids",chosen.kids?"On":"Off",false,()->{changed(LibraryFolders.kids(folders,chosen.id,!chosen.kids));browse(frame,false);});}
            else{row("Add this folder",null,false,()->add(frame,false));row("Add this folder for Kids",null,false,()->add(frame,true));}}
        card();TextView state=busy("Loading folders…");
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);column.addView(list);
        JSONObject from=server;int offset=frame.offset;
        task=worker.submit(()->{DlnaClient.Page result=null;Exception problem=null;
            try{result=LibraryIndex.browser.browse(from,frame.id,offset);}catch(Exception e){problem=e;}
            DlnaClient.Page got=result;Exception failed=problem;
            handler.post(()->{if(mine!=ticket||isFinishing())return;column=list;
                if(failed!=null){state.setText(DlnaClient.Failure.plain(failed instanceof DlnaClient.Failure?((DlnaClient.Failure)failed).kind:DlnaClient.Failure.SERVER));
                    row("Try again",null,false,()->browse(frame,false)).requestFocus();return;}
                ((View)state.getParent()).setVisibility(View.GONE);
                int sub=0,videos=0;Button first=null;
                for(DlnaClient.Item i:got.items){if(!i.folder){videos++;continue;}sub++;
                    LibraryFolders.Folder chosen=LibraryFolders.find(folders,i.id);
                    Button b=row(i.title,chosen==null?null:chosen.kids?"For Kids":"Added",true,()->browse(new DlnaScope.Frame(i.id,i.title,0),false));if(first==null)first=b;}
                if(sub==0)text(videos>0?videos+(videos==1?" video here, and no folders inside.":" videos here, and no folders inside."):"No folders inside.",15);
                else if(videos>0)text("And "+videos+(videos==1?" video.":" videos."),15);
                if(got.more(offset))row("Show more folders",null,false,()->browse(new DlnaScope.Frame(frame.id,frame.name,got.next(offset)),false));
                if(getCurrentFocus()==null||!isInside(getCurrentFocus())){View f=firstFocusable(body);if(f!=null)f.requestFocus();}
            });
        });
        focusFirst();
    }
    private void add(DlnaScope.Frame frame,boolean kids){
        List<String> path=new ArrayList<>();for(DlnaScope.Frame f:trail)if(!"0".equals(f.id)&&!f.id.equals(frame.id))path.add(f.id);
        LibraryFolders.Change c=LibraryFolders.add(folders,frame.id,frame.name,path,kids);
        boolean added=c.folders.size()>folders.size();folders=c.folders;if(added)dirty=true;
        if(c.message!=null&&!c.message.isEmpty())note(c.message);
        if(added){focusId="save";trail.clear();page=Page.MANAGE;render();}
    }
    private void backInBrowse(){
        if(trail.size()>1){trail.remove(trail.size()-1);DlnaScope.Frame up=trail.get(trail.size()-1);browse(new DlnaScope.Frame(up.id,up.name,0),false);}
        else{trail.clear();page=Page.MANAGE;render();}
    }

    @Override public void onBackPressed(){
        switch(page){
            case BROWSE:backInBrowse();return;
            case FOLDER:focusId=folderId;page=Page.MANAGE;render();return;
            case DISCOVER:if(!server.optString("control").isEmpty()){page=Page.MANAGE;render();}else close();return;
            case MANAGE:
                if(dirty){new AlertDialog.Builder(this).setTitle("Save your changes?").setMessage("Your folder choices aren't saved yet.")
                    .setPositiveButton("Save",(d,w)->save()).setNegativeButton("Don't save",(d,w)->close()).setNeutralButton("Keep editing",null).show();return;}
                close();return;
            default:close();
        }
    }
}
