package io.unifiedtv.preview;
import android.content.Intent;
import android.net.nsd.*;
import android.os.*;
import android.widget.TextView;
import java.util.*;

/** Standard local service discovery. Selection pre-fills setup; no credentials or consent inferred. */
public final class ServiceDiscoveryActivity extends Screen {
    private NsdManager manager;private NsdManager.DiscoveryListener listener;private boolean active;
    private final Handler timer=new Handler(Looper.getMainLooper());private final Set<String> found=new HashSet<>();
    @Override public void onCreate(Bundle b){super.onCreate(b);page("Home Assistant");text("Select your home connection",23);TextView state=text("Looking on your local network...",19);
        button("Advanced connection setup",()->open(null));button("Back",this::finish);active=true;manager=(NsdManager)getSystemService(NSD_SERVICE);
        listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}
            public void onServiceFound(NsdServiceInfo info){if(!active)return;manager.resolveService(info,new NsdManager.ResolveListener(){
                public void onResolveFailed(NsdServiceInfo s,int error){}
                public void onServiceResolved(NsdServiceInfo s){runOnUiThread(()->{if(!active||s.getHost()==null)return;String base=Endpoint.base(s.getHost().getHostAddress(),""+s.getPort(),false);if(found.add(base)){state.setText("Select a connection, then authorise it securely in setup.");button(s.getServiceName(),()->open(base));}});}
            });}
            public void onServiceLost(NsdServiceInfo info){}
            public void onDiscoveryStopped(String t){}
            public void onStartDiscoveryFailed(String t,int error){runOnUiThread(()->{if(active)state.setText("Discovery unavailable. Check local-network access or use advanced setup.");});}
            public void onStopDiscoveryFailed(String t,int error){}
        };
        try{manager.discoverServices("_home-assistant._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);}catch(RuntimeException e){state.setText("Discovery unavailable on this TV.");}
        timer.postDelayed(()->{if(active&&found.isEmpty())state.setText("No home connection found. Check that it is running and local-network discovery is allowed.");stopDiscovery();},12000);
    }
    private void open(String base){EditLock.ask(this,()->{Intent i=new Intent(this,ConnectionsActivity.class).putExtra("key","ha");if(base!=null)i.putExtra("discoveredBase",base);startActivity(i);});}
    private void stopDiscovery(){if(manager!=null&&listener!=null){try{manager.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}listener=null;}}
    @Override protected void onDestroy(){active=false;timer.removeCallbacksAndMessages(null);stopDiscovery();super.onDestroy();}
}
