package io.unifiedtv.preview;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Automatic server search on first entry. Only explicitly selected folder roots become a library. */
public final class DlnaLibraryActivity extends Screen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(),artwork=Executors.newFixedThreadPool(2);private JSONObject source;private int generation;private boolean setup;private String currentFolder="Collection";private final ArrayDeque<String> history=new ArrayDeque<>();private final ArrayDeque<String> names=new ArrayDeque<>();private boolean inFolder;
    @Override public void onCreate(Bundle state){super.onCreate(state);DlnaClient.lan=LocalDiscovery.lan(this);setup=getIntent().getBooleanExtra("setup",false);try{source=new Vault(this).read("dlna");}catch(Exception e){source=new JSONObject();}
        if(EditLock.child(this)){page("Your Films & TV");text("Shared DLNA folders do not provide restricted Child accounts. Ask an adult to connect a restricted media account.",20);button("Back",this::finish);return;}
        if(!setup&&source.optBoolean("enabled")&&source.optJSONArray("roots")!=null)roots();else discover();
    }
    private boolean active(int ticket){return ticket==generation&&!isFinishing()&&!isDestroyed();}
    private void discover(){int ticket=++generation;page("Your Films & TV");text("Bring your film and TV collection together in one place.",23);TextView status=text("One moment...",19);button("Search again",this::discover);button("Media server account setup",()->startActivity(new Intent(this,ConnectionsActivity.class).putExtra("key",Vault.mediaKey(this))));button("Back",this::finish);
        List<DlnaClient.Device> ready=LocalDiscovery.fresh()?LocalDiscovery.nasFound:null;
        worker.execute(()->{try{List<DlnaClient.Device> devices=ready!=null&&!ready.isEmpty()?ready:DlnaClient.discover(getApplicationContext(),true);runOnUiThread(()->{if(!active(ticket))return;
            // One media server on the home network: choose it for the viewer and go straight to their collection's folders.
            if(devices.size()==1&&!source.optBoolean("enabled")){DlnaClient.Device only=devices.get(0);EditLock.ask(this,()->{try{source=only.json();setup=true;browse("0",0);}catch(Exception e){note("Unable to select that server");}});}status.setText(devices.isEmpty()?"No media servers found. Check that the NAS media service is running, the TV is on the same network, and VPN settings allow local discovery.":"Select your media server");for(DlnaClient.Device device:devices)button(device.name.isEmpty()?"Media server":device.name,()->EditLock.ask(this,()->{try{source=device.json();setup=true;browse("0",0);}catch(Exception e){note("Unable to select that server");}}));});}catch(Exception e){runOnUiThread(()->{if(active(ticket))status.setText("Discovery unavailable. Check your local network and try again.");});}});
    }
    private void roots(){inFolder=false;history.clear();names.clear();page("Your Films & TV");text("Your selected collection",20);JSONArray roots=source.optJSONArray("roots");if(roots!=null)for(int i=0;i<roots.length();i++){JSONObject root=roots.optJSONObject(i);if(root!=null)button(root.optString("name","Collection"),()->{currentFolder=root.optString("name","Collection");browse(root.optString("id"),0); });}button("Change media server",()->EditLock.ask(this,()->{setup=true;discover();}));button("Back",this::finish);}
    private void browse(String id,int offset){inFolder=true;int ticket=++generation;page(setup?"Choose library folders":"Your Films & TV");TextView state=text("Loading folders...",19);
        if(setup&&!id.equals("0"))button("Add this folder to your library",()->{try{JSONArray roots=source.optJSONArray("roots");if(roots==null)roots=new JSONArray();boolean exists=false;for(int i=0;i<roots.length();i++)if(roots.getJSONObject(i).optString("id").equals(id))exists=true;if(!exists)roots.put(new JSONObject().put("id",id).put("name",currentFolder));source.put("roots",roots);note("Folder selected");}catch(Exception e){note("Unable to select folder");}});
        if(setup)button("Save selected folders",()->{try{if(source.optJSONArray("roots")==null||source.getJSONArray("roots").length()==0){note("Select at least one folder");return;}new Vault(this).save("dlna",source);getSharedPreferences("unified-tv-preview",0).edit().putString("librarySource","dlna").apply();setup=false;history.clear();roots();}catch(Exception e){note("Unable to save library securely");}});
        button("Back",()->{if(history.isEmpty()){if(setup)discover();else roots();}else {currentFolder=names.pop();browse(history.pop(),0);}});
        worker.execute(()->{try{DlnaClient.Page page=DlnaClient.browse(source,id,offset);List<DlnaClient.Item> entries=page.items;runOnUiThread(()->{if(!active(ticket))return;state.setText(entries.isEmpty()?(page.more(offset)?"Nothing to show here yet.":"No supported videos or folders found."):setup?"Open a folder, then add it. Only selected folders will appear in your library.":"");
            LinearLayout row=null;for(int i=0;i<entries.size();i++){DlnaClient.Item item=entries.get(i);if(i%4==0){row=new LinearLayout(this);column.addView(row);}row.addView(card(item,ticket,()->{if(item.folder){history.push(id);names.push(currentFolder);currentFolder=item.title;browse(item.id,0);}else if(!setup)startActivity(new Intent(this,PlayerActivity.class).putExtra("dlnaUrl",item.url).putExtra("title",item.title));}),new LinearLayout.LayoutParams(0,-2,1));}
            if(page.more(offset))button("Show more",()->browse(id,page.next(offset)));});}catch(Exception e){runOnUiThread(()->{if(active(ticket)){state.setText("The media server is unavailable. Check its media service and local-network access.");button("Retry",()->browse(id,offset));}});}});
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
    @Override public void onBackPressed(){if(inFolder){if(!history.isEmpty()){currentFolder=names.pop();browse(history.pop(),0);}else if(setup){inFolder=false;discover();}else roots();}else super.onBackPressed();}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();artwork.shutdownNow();super.onDestroy();}
}
