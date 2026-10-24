package io.unifiedtv.preview;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Automatic server search on first entry. Only explicitly selected folder roots become a library. */
public final class DlnaLibraryActivity extends Screen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private JSONObject source;private int generation;private boolean setup;private String currentFolder="Collection";private final ArrayDeque<String> history=new ArrayDeque<>();private final ArrayDeque<String> names=new ArrayDeque<>();private boolean inFolder;
    @Override public void onCreate(Bundle state){super.onCreate(state);setup=getIntent().getBooleanExtra("setup",false);try{source=new Vault(this).read("dlna");}catch(Exception e){source=new JSONObject();}
        if(EditLock.child(this)){page("Your Films & TV");text("Shared DLNA folders do not provide restricted Child accounts. Ask an adult to connect a restricted media account.",20);button("Back",this::finish);return;}
        if(!setup&&source.optBoolean("enabled")&&source.optJSONArray("roots")!=null)roots();else discover();
    }
    private boolean active(int ticket){return ticket==generation&&!isFinishing()&&!isDestroyed();}
    private void discover(){int ticket=++generation;page("Your Films & TV");text("Bring your film and TV collection together in one place.",23);TextView status=text("Looking for media servers on your network...",19);button("Search again",this::discover);button("Media server account setup",()->startActivity(new Intent(this,ConnectionsActivity.class).putExtra("key",Vault.mediaKey(this))));button("Back",this::finish);
        worker.execute(()->{try{List<DlnaClient.Device> devices=DlnaClient.discover();runOnUiThread(()->{if(!active(ticket))return;status.setText(devices.isEmpty()?"No media servers found. Check that the NAS media service is running, the TV is on the same network, and VPN settings allow local discovery.":"Select your media server");for(DlnaClient.Device device:devices)button(device.name.isEmpty()?"Media server":device.name,()->EditLock.ask(this,()->{try{source=device.json();setup=true;browse("0",0);}catch(Exception e){note("Unable to select that server");}}));});}catch(Exception e){runOnUiThread(()->{if(active(ticket))status.setText("Discovery unavailable. Check your local network and try again.");});}});
    }
    private void roots(){inFolder=false;history.clear();names.clear();page("Your Films & TV");text("Your selected collection",20);JSONArray roots=source.optJSONArray("roots");if(roots!=null)for(int i=0;i<roots.length();i++){JSONObject root=roots.optJSONObject(i);if(root!=null)button(root.optString("name","Collection"),()->{currentFolder=root.optString("name","Collection");browse(root.optString("id"),0); });}button("Change media server",()->EditLock.ask(this,()->{setup=true;discover();}));button("Back",this::finish);}
    private void browse(String id,int offset){inFolder=true;int ticket=++generation;page(setup?"Choose library folders":"Your Films & TV");TextView state=text("Loading folders...",19);
        if(setup&&!id.equals("0"))button("Add this folder to your library",()->{try{JSONArray roots=source.optJSONArray("roots");if(roots==null)roots=new JSONArray();boolean exists=false;for(int i=0;i<roots.length();i++)if(roots.getJSONObject(i).optString("id").equals(id))exists=true;if(!exists)roots.put(new JSONObject().put("id",id).put("name",currentFolder));source.put("roots",roots);note("Folder selected");}catch(Exception e){note("Unable to select folder");}});
        if(setup)button("Save selected folders",()->{try{if(source.optJSONArray("roots")==null||source.getJSONArray("roots").length()==0){note("Select at least one folder");return;}new Vault(this).save("dlna",source);getSharedPreferences("unified-tv-preview",0).edit().putString("librarySource","dlna").apply();setup=false;history.clear();roots();}catch(Exception e){note("Unable to save library securely");}});
        button("Back",()->{if(history.isEmpty()){if(setup)discover();else roots();}else {currentFolder=names.pop();browse(history.pop(),0);}});
        worker.execute(()->{try{List<DlnaClient.Item> entries=DlnaClient.browse(source,id,offset);runOnUiThread(()->{if(!active(ticket))return;state.setText(entries.isEmpty()?"No supported videos or folders found.":setup?"Open a folder, then add it. Only selected folders will appear in your library.":"");for(DlnaClient.Item item:entries)button((item.folder?"Folder  ":"")+item.title,()->{if(item.folder){history.push(id);names.push(currentFolder);currentFolder=item.title;browse(item.id,0);}else if(!setup)startActivity(new Intent(this,PlayerActivity.class).putExtra("dlnaUrl",item.url).putExtra("title",item.title));});if(entries.size()==24)button("Next page",()->browse(id,offset+24));});}catch(Exception e){runOnUiThread(()->{if(active(ticket)){state.setText("The media server is unavailable. Check its media service and local-network access.");button("Retry",()->browse(id,offset));}});}});
    }
    @Override public void onBackPressed(){if(inFolder){if(!history.isEmpty()){currentFolder=names.pop();browse(history.pop(),0);}else if(setup){inFolder=false;discover();}else roots();}else super.onBackPressed();}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();super.onDestroy();}
}
