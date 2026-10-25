package io.unifiedtv.preview;
import android.content.Intent;
import android.os.*;
import android.widget.TextView;
import java.util.*;
import java.util.concurrent.*;

/**
 * Content first: a saved connection opens straight away, one server on the home network is chosen for you, and only
 * several ask. No address typing. Home Assistant is set up on the phone inside Settings (SetupPages), so "ha" goes there;
 * media servers keep this screen, as the glass sheet over Home (OD-11).
 */
public final class ServiceDiscoveryActivity extends GlassScreen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private String key;private TextView state;private boolean active;
    /** Settings opened on Smart Home: Home Assistant's status and phone setup (or its phone setup straight away when not set up). */
    static Intent homeAssistant(android.content.Context c){
        Intent i=new Intent(c,SettingsActivity.class).putExtra("category",SettingsActivity.SMART_HOME);
        return SetupPages.haSaved(c)?i:i.putExtra("task",SettingsActivity.PHONE_HA);
    }
    @Override public void onCreate(Bundle b){
        String asked=getIntent().getStringExtra("key");
        if(asked==null||asked.equals("ha")){super.onCreate(b);startActivity(homeAssistant(this));close();return;}
        super.onCreate(b);key=asked;boolean ha=false;active=true;
        page("Your Films & TV",key.endsWith("child")?"Kids":"Adults");state=busy("Looking on your home network…");
        try{if(!getIntent().getBooleanExtra("search",false)&&!new Vault(this).read(key).optString("base").isEmpty()){button("Back",this::close);open(null,null,true);return;}}catch(Exception ignored){}
        List<LocalDiscovery.Found> ready=LocalDiscovery.fresh()?(ha?LocalDiscovery.haFound:LocalDiscovery.mediaFound):null;
        if(ready!=null&&!ready.isEmpty()){show(ready);return;}
        worker.execute(()->{List<LocalDiscovery.Found> found=ha?LocalDiscovery.homeAssistant(getApplicationContext(),true):LocalDiscovery.jellyfin(getApplicationContext());runOnUiThread(()->{if(active)show(found);});});
    }
    private void show(List<LocalDiscovery.Found> found){
        if(found.size()==1){open(found.get(0).base,found.get(0).name,true);return;}
        boolean ha=key.equals("ha");
        if(state.getParent() instanceof android.view.View)((android.view.View)state.getParent()).setVisibility(android.view.View.GONE);state=text("",20);
        if(found.isEmpty())state.setText((ha?"Home Assistant wasn't found.":"Your media server wasn't found.")+" Make sure it's switched on and on the same home network as this TV. If NordVPN is on, let it see devices on your home network, then try again.");
        else state.setText("Choose yours, then sign in.");
        for(LocalDiscovery.Found f:found)button(f.name==null||f.name.isEmpty()?(ha?"Home Assistant":"Media server"):f.name,()->open(f.base,f.name,false));
        if(found.isEmpty())button("Try again",()->{startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key",key).putExtra("search",true));finish();});
        button("Enter an address instead (advanced)",()->open(null,null,false));button("Back",this::close);
    }
    private void open(String base,String name,boolean replace){EditLock.ask(this,()->{
        if(base!=null&&!key.equals("ha")){startActivity(new Intent(this,QuickConnectActivity.class).putExtra("key",key).putExtra("base",base).putExtra("name",name));if(replace)finish();return;}
        Intent i=new Intent(this,ConnectionsActivity.class).putExtra("key",key);if(base!=null)i.putExtra("discoveredBase",base).putExtra("discoveredName",name);startActivity(i);if(replace)finish();});}
    @Override protected void onDestroy(){active=false;worker.shutdownNow();super.onDestroy();}
}
