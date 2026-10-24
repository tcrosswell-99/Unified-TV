package io.unifiedtv.preview;
import android.content.Intent;
import android.os.*;
import android.widget.TextView;
import java.util.*;
import java.util.concurrent.*;

/** Content first: a saved connection opens straight away, one server on the home network is chosen for you, and only several ask. No address typing. */
public final class ServiceDiscoveryActivity extends Screen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private String key;private TextView state;private boolean active;
    @Override public void onCreate(Bundle b){super.onCreate(b);key=getIntent().getStringExtra("key");if(key==null)key="ha";boolean ha=key.equals("ha");active=true;
        page(ha?"Home Assistant":"Your Films & TV · "+(key.endsWith("child")?"Child":"Adult"));state=text("One moment...",20);
        try{if(!getIntent().getBooleanExtra("search",false)&&!new Vault(this).read(key).optString("base").isEmpty()){button("Back",this::finish);open(null,null,true);return;}}catch(Exception ignored){}
        List<LocalDiscovery.Found> ready=LocalDiscovery.fresh()?(ha?LocalDiscovery.haFound:LocalDiscovery.mediaFound):null;
        if(ready!=null&&!ready.isEmpty()){show(ready);return;}
        worker.execute(()->{List<LocalDiscovery.Found> found=ha?LocalDiscovery.homeAssistant(getApplicationContext(),true):LocalDiscovery.jellyfin(getApplicationContext());runOnUiThread(()->{if(active)show(found);});});
    }
    private void show(List<LocalDiscovery.Found> found){
        if(found.size()==1){open(found.get(0).base,found.get(0).name,true);return;}
        boolean ha=key.equals("ha");
        if(found.isEmpty())state.setText((ha?"Home Assistant wasn't found.":"Your media server wasn't found.")+" Make sure it's switched on and on the same home network as this TV. If NordVPN is on, let it see devices on your home network, then try again.");
        else state.setText("Choose yours, then sign in.");
        for(LocalDiscovery.Found f:found)button(f.name==null||f.name.isEmpty()?(ha?"Home Assistant":"Media server"):f.name,()->open(f.base,f.name,false));
        if(found.isEmpty())button("Try again",()->{startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key",key).putExtra("search",true));finish();});
        button("Enter an address instead (advanced)",()->open(null,null,false));button("Back",this::finish);
    }
    private void open(String base,String name,boolean replace){EditLock.ask(this,()->{
        if(base!=null&&!key.equals("ha")){startActivity(new Intent(this,QuickConnectActivity.class).putExtra("key",key).putExtra("base",base).putExtra("name",name));if(replace)finish();return;}
        Intent i=new Intent(this,ConnectionsActivity.class).putExtra("key",key);if(base!=null)i.putExtra("discoveredBase",base).putExtra("discoveredName",name);startActivity(i);if(replace)finish();});}
    @Override protected void onDestroy(){active=false;worker.shutdownNow();super.onDestroy();}
}
