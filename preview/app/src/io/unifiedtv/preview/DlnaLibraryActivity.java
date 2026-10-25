package io.unifiedtv.preview;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatic server search on first entry. Only explicitly selected folder roots become a library, and a saved viewer never browses above them.
 * Every load has a ticket and a time limit: a superseded or cancelled load can't paint over the screen, and nothing waits forever.
 *
 * Profiles (owner, 25 October 2026): Adults use the household library (Vault "dlna"). Kids use only the Kids folders an
 * adult chose (Vault "dlna-kids", KidsPolicy), with refused ratings left out, and never reach setup; with no Kids folders
 * they see "Ask an adult". An adult chooses Kids folders here too ("kidsSetup", from Kids settings, behind the PIN).
 * Zero configuration: servers are offered by friendly name only, and only when they validated and show folders
 * (DlnaClient.offered); no address is ever typed or shown here.
 */
public final class DlnaLibraryActivity extends Screen {
    /** Longest a folder may load, including one quiet search for a server whose address changed, before Retry is offered. */
    static final long LOAD_LIMIT_MS=25_000;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(),artwork=Executors.newFixedThreadPool(2);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject source;private int generation;private boolean setup;private Future<?> task;
    private DlnaScope.Trail trail=new DlnaScope.Trail();
    /** The folder or title to focus when its screen is shown again, so Back lands on the poster the viewer left. */
    private String focusId="";
    /** The single-server shortcut runs once per visit, so Back out of its folders reaches the search screen instead of looping. */
    private boolean autoChosen;
    /** Which saved library this visit reads and writes: "dlna", or "dlna-kids" for Kids and for choosing Kids folders. */
    private String store="dlna";private boolean kids,kidsSetup;

    @Override public void onCreate(Bundle state){super.onCreate(state);DlnaClient.lan=LocalDiscovery.lan(this);
        kids=Profiles.kids(this);kidsSetup=!kids&&getIntent().getBooleanExtra("kidsSetup",false);
        store=kids||kidsSetup?KidsPolicy.STORE:"dlna";
        // Kids never reach setup, whatever asked for it: only an adult in Adults chooses folders.
        setup=!kids&&(kidsSetup||getIntent().getBooleanExtra("setup",false));
        try{source=new Vault(this).read(store);}catch(Exception e){source=new JSONObject();}
        if(kids&&!KidsPolicy.ready(source)){page("Your Films & TV");text(KidsPolicy.ASK_ADULT,24);text("An adult chooses which folders on the media server Kids can watch, in Settings > Profiles > Kids settings.",18);button("Back",this::finish);return;}
        if(kidsSetup){startKidsSetup();return;}
        if(setup||!source.optBoolean("enabled")||source.optJSONArray("roots")==null){discover();return;}
        JSONArray roots=source.optJSONArray("roots");
        if(state!=null&&state.getString("trail")!=null){
            focusId=state.getString("focus","");
            DlnaScope.Trail saved=DlnaScope.Trail.restore(state.getString("trail"),roots);
            if(!saved.isEmpty()){trail=saved;show();}else roots();
            return;
        }
        // Home's poster row and saved-collection shortcuts open straight into a folder; anything else is ignored.
        Intent in=getIntent();
        DlnaScope.Frame start=DlnaScope.start(roots,in.getStringExtra("folder"),in.getStringExtra("name"),in.getBooleanExtra("fromShelf",false),in.getStringExtra("root"));
        if(start!=null){String root=DlnaScope.root(roots,start.id)!=null?start.id:in.getStringExtra("root");trail.start(start,root);show();}else roots();
    }
    /** Kids folders live on the household library's server when there is one: no second search. A fresh choice each time. */
    private void startKidsSetup(){
        JSONObject films;try{films=new Vault(this).read("dlna");}catch(Exception e){films=new JSONObject();}
        if(films.optBoolean("enabled")&&!films.optString("control").isEmpty()){source=server(films);trail.start(new DlnaScope.Frame("0",source.optString("name","Media server"),0),"");show();}
        else discover();
    }
    /** Only what is needed to reach a server again; no folders come with it. */
    private static JSONObject server(JSONObject from){JSONObject s=new JSONObject();try{for(String k:new String[]{"name","location","control","service","udn"})s.put(k,from.optString(k));s.put("enabled",true);}catch(JSONException ignored){}return s;}
    private boolean active(int ticket){return ticket==generation&&!isFinishing()&&!isDestroyed();}
    /** Supersedes whatever was loading: its result is ignored and its thread interrupted. */
    private int nextTicket(){if(task!=null)task.cancel(true);task=null;handler.removeCallbacksAndMessages(null);return ++generation;}

    private void discover(){int ticket=nextTicket();trail.clear();page(kidsSetup?"Kids folders":"Your Films & TV");
        text(kidsSetup?"First, the media server that has the Kids folders.":"Bring your film and TV collection together in one place.",23);TextView status=text("Looking for your media server…",19);
        LinearLayout found=new LinearLayout(this);found.setOrientation(LinearLayout.VERTICAL);column.addView(found);
        Button again=button("Search again",this::discover);button("Back",this::finish);
        List<DlnaClient.Device> ready=LocalDiscovery.fresh()?LocalDiscovery.nasFound:null;
        task=worker.submit(()->{try{DlnaClient.Report report=new DlnaClient.Report();List<DlnaClient.Device> devices=ready!=null&&!ready.isEmpty()?ready:DlnaClient.discover(getApplicationContext(),true,report);runOnUiThread(()->{if(!active(ticket))return;
            if(devices.isEmpty()){status.setText(report.plain());troubleshoot(found);again.requestFocus();return;}
            // One media server on the home network: choose it for the viewer and go straight to their collection's folders.
            if(devices.size()==1&&(kidsSetup||!source.optBoolean("enabled"))&&!autoChosen){autoChosen=true;DlnaClient.Device only=devices.get(0);EditLock.ask(this,()->choose(only));}
            status.setText("Select your media server");List<String> names=DlnaClient.labels(devices);Button first=null;
            for(int i=0;i<devices.size();i++){DlnaClient.Device device=devices.get(i);Button b=styled(names.get(i),()->EditLock.ask(this,()->choose(device)));
                LinearLayout.LayoutParams at=new LinearLayout.LayoutParams(-1,-2);at.setMargins(0,dp(5),0,dp(5));found.addView(b,at);if(first==null)first=b;}
            if(first!=null)first.requestFocus();});}
            catch(Exception e){runOnUiThread(()->{if(!active(ticket))return;status.setText("The TV couldn't search your home network just now.");troubleshoot(found);again.requestFocus();});}});
    }
    /** The owner's checklist, in plain words. No address, and no typed setup: Search again is the only next step. */
    private void troubleshoot(LinearLayout into){
        for(String line:new String[]{"Check these, then search again:","•  The NAS is switched on and has finished starting.","•  The TV and the NAS are on the same home network.",
            "•  The NAS's media server (DLNA / UPnP) is switched on and sharing your video folders.","•  Guest Wi-Fi keeps devices apart: connect the TV to your main network, not the guest one.",
            "•  If NordVPN is on, allow it to see devices on your home network."}){TextView t=new TextView(this);t.setText(line);t.setTextColor(0xffffffff);t.setTextSize(18);t.setPadding(8,4,8,4);into.addView(t);}
    }
    private void choose(DlnaClient.Device device){try{source=device.json();setup=true;trail.start(new DlnaScope.Frame("0",device.name,0),"");show();}catch(Exception e){note("Unable to select that server");}}

    private void roots(){nextTicket();trail.clear();page("Your Films & TV");text(kids?"Kids folders":"Your selected collection",20);JSONArray roots=source.optJSONArray("roots");Button again=null;
        if(roots!=null)for(int i=0;i<roots.length();i++){JSONObject root=roots.optJSONObject(i);if(root==null)continue;String id=root.optString("id"),name=root.optString("name","Collection");
            Button b=button(name,()->{trail.start(new DlnaScope.Frame(id,name,0),id);show();});if(id.equals(focusId))again=b;}
        if(!kids)button("Change media server",()->EditLock.ask(this,()->{setup=true;discover();}));button("Back",this::finish);
        if(again!=null)again.requestFocus();focusId="";
    }
    private void open(DlnaClient.Item folder){trail.open(new DlnaScope.Frame(folder.id,folder.title,0));show();}
    /** Back one folder; from the first folder a viewer returns to the saved collections, never the server's top level. */
    private void back(){
        DlnaScope.Frame leaving=trail.current();DlnaScope.Frame parent=trail.back();focusId=leaving==null?"":leaving.id;
        if(parent!=null)show();else if(setup)discover();else roots();
    }
    private void show(){
        DlnaScope.Frame frame=trail.current();if(frame==null){roots();return;}
        String id=frame.id;int offset=frame.offset;int ticket=nextTicket();
        page(setup?(kidsSetup?"Choose Kids folders":"Choose library folders"):"Your Films & TV");if(!frame.name.isEmpty()&&!id.equals("0"))text(frame.name,22);TextView state=text("Loading…",19);
        if(setup&&!id.equals("0"))button(kidsSetup?"Add this folder for Kids":"Add this folder to your library",()->{try{JSONArray roots=source.optJSONArray("roots");if(roots==null)roots=new JSONArray();if(DlnaScope.root(roots,id)==null)roots.put(new JSONObject().put("id",id).put("name",frame.name.isEmpty()?"Collection":frame.name));source.put("roots",roots);note("Folder selected");}catch(Exception e){note("Unable to select folder");}});
        if(setup)button(kidsSetup?"Save Kids folders":"Save selected folders",()->{try{if(source.optJSONArray("roots")==null||source.getJSONArray("roots").length()==0){note("Select at least one folder");return;}new Vault(this).save(store,source);
            if(kidsSetup){note("Kids folders saved");finish();return;}
            getSharedPreferences("unified-tv-preview",0).edit().putString("librarySource","dlna").apply();setup=false;roots();}catch(Exception e){note("Unable to save library securely");}});
        button("Back",this::back);
        AtomicBoolean done=new AtomicBoolean();
        JSONObject from=source;
        task=worker.submit(()->{
            DlnaClient.Page page;JSONObject moved=null;
            try{page=DlnaClient.browse(from,id,offset);}
            catch(Exception first){
                // The NAS may have restarted with a new address: look for the same server (same UDN) once, quietly.
                try{moved=first instanceof DlnaClient.Failure&&((DlnaClient.Failure)first).kind==DlnaClient.Failure.UNREACHABLE?relocate(from):null;
                    if(moved==null)throw first;page=DlnaClient.browse(moved,id,offset);}
                catch(Exception e){if(done.compareAndSet(false,true))runOnUiThread(()->failed(ticket,state,e));return;}
            }
            DlnaClient.Page result=page;JSONObject relocated=moved;
            if(!done.compareAndSet(false,true))return;
            runOnUiThread(()->{if(!active(ticket))return;if(relocated!=null)keep(relocated);render(ticket,state,frame,result);});
        });
        handler.postDelayed(()->{if(active(ticket)&&done.compareAndSet(false,true)){if(task!=null)task.cancel(true);failed(ticket,state,new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE));}},LOAD_LIMIT_MS);
    }
    /** The same server (matched by its UDN, never by name) at a new local address, or null. */
    private JSONObject relocate(JSONObject from)throws Exception{return DlnaClient.relocate(getApplicationContext(),from);}
    /** Saves the server's new address for this library; folders and their IDs are unchanged. */
    private void keep(JSONObject relocated){source=relocated;if(!setup)try{new Vault(this).save(store,source);}catch(Exception ignored){/* Still usable for this visit. */}}
    private void failed(int ticket,TextView state,Exception e){
        if(!active(ticket))return;
        int kind=e instanceof DlnaClient.Failure?((DlnaClient.Failure)e).kind:DlnaClient.Failure.SERVER;
        state.setText(DlnaClient.Failure.plain(kind));
        Button retry=button("Try again",this::show);retry.requestFocus();
        if(kind==DlnaClient.Failure.MISSING&&!setup&&!kids)button("Choose folders again",()->EditLock.ask(this,()->{setup=true;discover();}));
    }
    private void render(int ticket,TextView state,DlnaScope.Frame frame,DlnaClient.Page page){
        List<DlnaClient.Item> entries=page.items;int offset=frame.offset;
        // Kids: the walls are the Kids folders (the trail never climbs above one) and the rating table; refused titles never appear.
        if(kids)entries=HomeShelf.kidsOnly(entries,source.optString("udn"),trail.rootId(),KidsPolicy.roots(source));
        state.setText(entries.isEmpty()?(page.more(offset)?"Nothing to show here yet.":"No supported videos or folders found."):setup?(kidsSetup?"Open a folder, then add it. Kids see only the folders you add, and only titles whose rating suits Kids.":"Open a folder, then add it. Only selected folders will appear in your library."):"");
        LinearLayout row=null;Button again=null;String root=trail.rootId();
        for(int i=0;i<entries.size();i++){DlnaClient.Item item=entries.get(i);if(i%4==0){row=new LinearLayout(this);column.addView(row);}
            LinearLayout card=card(item,ticket,()->{focusId=item.id;if(item.folder)open(item);else if(!setup)startActivity(new Intent(this,PlayerActivity.class).putExtra("dlnaUrl",item.url).putExtra("title",item.title).putExtra("dlnaItem",item.id).putExtra("root",root));});
            row.addView(card,new LinearLayout.LayoutParams(0,-2,1));if(item.id.equals(focusId))again=(Button)card.getChildAt(card.getChildCount()-1);}
        if(page.more(offset))button("Show more",()->{trail.page(page.next(offset));show();});
        if(again!=null)again.requestFocus();
    }
    /** Poster-first: a title shows its own server artwork, never another title's. No artwork means a plain card. */
    private LinearLayout card(DlnaClient.Item item,int ticket,Runnable open){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(6),dp(6),dp(6),dp(10));
        String label=item.title+(item.year.isEmpty()?"":" ("+item.year+")");
        if(!item.art.isEmpty()){ImageView poster=new ImageView(this);poster.setContentDescription(item.title);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(poster,new LinearLayout.LayoutParams(-1,dp(220)));
            artwork.execute(()->{try{if(!active(ticket))return;android.graphics.Bitmap image=Images.decode(DlnaClient.request(item.art,null,null),320,440);runOnUiThread(()->{if(active(ticket)&&image!=null)poster.setImageBitmap(image);});}catch(Exception ignored){}});}
        Button b=styled(label,open);b.setTextSize(18);b.setContentDescription((item.folder?"Open ":"Play ")+item.title);card.addView(b,new LinearLayout.LayoutParams(-1,-2));
        if(autoFocusButtons&&column.findFocus()==null)b.requestFocus();return card;
    }
    @Override public void onBackPressed(){if(!trail.isEmpty())back();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(!setup&&!trail.isEmpty()){out.putString("trail",trail.save());out.putString("focus",focusId);}}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();artwork.shutdownNow();super.onDestroy();}
}
