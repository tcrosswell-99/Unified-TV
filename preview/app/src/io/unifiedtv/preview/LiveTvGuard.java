package io.unifiedtv.preview;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;

/**
 * While Live TV plays: if NordVPN drops, bring the viewer back to Unified TV with a plain message. The protection
 * itself is Android's "block connections without VPN", proven by the network-level check; this only tells the viewer
 * why the picture stopped. Stops when Unified TV comes back to the front.
 */
public final class LiveTvGuard extends Service {
    private ConnectivityManager.NetworkCallback callback;private final Handler main=new Handler(Looper.getMainLooper());private boolean fired;
    /** Live TV is playing behind this guard: "Open Unified TV when the TV turns on" (TurnOn) stays out of the way. */
    static volatile boolean running;
    static void start(Context c){try{c.startForegroundService(new Intent(c,LiveTvGuard.class));}catch(RuntimeException ignored){}}
    static void stop(Context c){c.stopService(new Intent(c,LiveTvGuard.class));}
    @Override public void onCreate(){super.onCreate();running=true;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("livetv","Live TV",NotificationManager.IMPORTANCE_LOW));
        startForeground(43,new Notification.Builder(this,"livetv").setContentTitle("Live TV").setContentText("Watching for NordVPN disconnecting").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build());
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        callback=new ConnectivityManager.NetworkCallback(){@Override public void onLost(Network n){main.postDelayed(LiveTvGuard.this::check,1500);}};
        try{cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),callback);}catch(RuntimeException e){callback=null;}
    }
    /** A VPN network lost while another comes up (a reconnect) is not a drop. */
    private void check(){
        if(fired||VpnGate.tunnel(this))return;fired=true;
        Intent home=new Intent(this,UnifiedPreviewActivity.class).putExtra("liveTvPaused",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        // Bringing ourselves forward from the background needs "Display over other apps"; otherwise say so in a notification.
        if(android.provider.Settings.canDrawOverlays(this))try{startActivity(home);}catch(RuntimeException ignored){}
        PendingIntent open=PendingIntent.getActivity(this,3,home,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(44,new Notification.Builder(this,"livetv").setContentTitle("Live TV paused").setContentText("NordVPN disconnected").setSmallIcon(android.R.drawable.ic_lock_lock).setContentIntent(open).setAutoCancel(true).build());
    }
    @Override public int onStartCommand(Intent i,int flags,int id){return START_NOT_STICKY;}
    @Override public void onDestroy(){running=false;main.removeCallbacksAndMessages(null);if(callback!=null)try{((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(callback);}catch(RuntimeException ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
