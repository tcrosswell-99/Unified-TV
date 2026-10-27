package io.unifiedtv.preview;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;

/**
 * OD-26's safety net (owner, 27 October 2026): while Live TV that Unified TV opened is on, watch NordVPN, and if it drops,
 * cover Live TV with a full-screen notice (LiveTvCoverActivity: "NordVPN disconnected — Live TV is covered. Reconnect
 * NordVPN", Reconnect, Back to Home).
 *
 * <p>Honest limits. This is not a kill switch: Android does not stop Live TV's connection when NordVPN drops (unless the
 * owner set up Android's own always-on block, docs/VPN_PROTECTION.md), and in the second or two before the notice appears
 * Live TV keeps playing. Coming to the front from the background needs "Display over other apps" (as OD-23's TurnOn);
 * without it a high-priority notification says NordVPN disconnected instead, and Live TV is not covered.
 *
 * <p>How it watches, with no polling: Android's own VPN network callback (lost, back), and NordVPN's own notification
 * saying it is not connected (MediaWatch, when allowed). Each signal waits {@link #SETTLE_MS} (a VPN lost while it
 * reconnects is not a drop) and then asks the same rule that opened Live TV ({@link LiveTvProtection#now}). One small
 * foreground service with a quiet notification, as TurnOnService: no wakelock, no screen, nothing drawn until a drop.
 *
 * <p>It stops when the Live TV session is over: any Unified TV screen other than the cover comes to the front (Home
 * included), the TV goes to standby (screen off), another app comes to the front where Android lets Unified TV see that
 * (the optional Kids app guard's accessibility service, which reads only the front app's package name), or after
 * {@link #MAX_SESSION_MS}. Without that front-app signal it covers only in the first {@link #NO_SIGNAL_MS} of a session,
 * then says a drop in a notification, so it never covers an unrelated app later on. The cover keeps the watch going: if
 * Live TV comes back to the front while NordVPN is still down (seen by the signal), it is covered again.
 * Module 03: it never reads, consumes or reassigns a key.
 */
public final class LiveTvGuard extends Service {
    static final String LIVE="live";
    /** A VPN lost while NordVPN reconnects comes back within this: only then is the rule asked again. */
    static final long SETTLE_MS=1500;
    /** However long Live TV is left on, the watch ends after this (the TV left on Live TV overnight, say). */
    static final long MAX_SESSION_MS=6L*60*60*1000;
    /**
     * Without a front-app signal (the Kids app guard's accessibility service off), Unified TV can't tell that the owner has
     * left Live TV for another app. After this long it no longer covers the screen (it might be Netflix by then): a drop is
     * told in a notification instead.
     */
    static final long NO_SIGNAL_MS=15L*60*1000;
    private static final String CHANNEL="livetv",DROP_CHANNEL="livetvDrop";
    static final int ONGOING=43,DROPPED=44;
    private static final int OPEN_COVER=3;
    /** Live TV is playing behind this guard: "Open Unified TV when the TV turns on" (TurnOn) stays out of the way. */
    static volatile boolean running;
    private static volatile LiveTvGuard current;
    /** For tests and owner checks: what the last drop did ("covered" or "notified"), or null. */
    static volatile String lastAction;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable look=this::check,end=this::stopSelf;
    private ConnectivityManager.NetworkCallback callback;private BroadcastReceiver screen;
    private boolean fired,seen;private long startedAt;private String live="";

    static void start(Context c,String live){try{c.startForegroundService(new Intent(c,LiveTvGuard.class).putExtra(LIVE,live==null?"":live));}catch(RuntimeException ignored){}}
    static void stop(Context c){try{c.stopService(new Intent(c,LiveTvGuard.class));}catch(RuntimeException ignored){}}

    @Override public void onCreate(){super.onCreate();running=true;current=this;
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager!=null){manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Live TV",NotificationManager.IMPORTANCE_LOW));
            manager.createNotificationChannel(new NotificationChannel(DROP_CHANNEL,"NordVPN disconnected during Live TV",NotificationManager.IMPORTANCE_HIGH));}
        startForeground(ONGOING,new Notification.Builder(this,CHANNEL).setContentTitle("Live TV").setContentText("If NordVPN drops, Unified TV covers Live TV.").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build());
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        callback=new ConnectivityManager.NetworkCallback(){
            @Override public void onLost(Network n){soon();}
            @Override public void onAvailable(Network n){soon();}
        };
        try{if(cm!=null)cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),callback);else callback=null;}catch(RuntimeException e){callback=null;}
        // Standby ends the session. A system broadcast only; no other app can reach this receiver.
        screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(i!=null&&Intent.ACTION_SCREEN_OFF.equals(i.getAction()))stopSelf();}};
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)registerReceiver(screen,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen,f);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        String pkg=i==null?null:i.getStringExtra(LIVE);live=pkg==null?"":pkg;fired=false;seen=false;startedAt=SystemClock.elapsedRealtime();
        main.removeCallbacks(end);main.postDelayed(end,MAX_SESSION_MS);
        return START_NOT_STICKY;
    }
    /** A signal arrived: look once, after the VPN has had a moment to come back. */
    private void soon(){main.removeCallbacks(look);main.postDelayed(look,SETTLE_MS);}
    /** Asks the same rule that opened Live TV. Covers once per drop; NordVPN back on readies it for the next one. */
    void check(){
        LiveTvProtection.State s=LiveTvProtection.now(this);
        if(!LiveTvProtection.dropped(s)){
            if(LiveTvProtection.opens(s)&&fired){fired=false;cancelNotice();}
            return;
        }
        if(fired)return;fired=true;
        if(TurnOn.permitted(this)&&mayCover(frontSignal(),SystemClock.elapsedRealtime()-startedAt)&&cover())return;
        notifyDrop();
    }
    /** Pure: the full-screen cover only while Unified TV can tell Live TV is still what's on screen (see NO_SIGNAL_MS). */
    static boolean mayCover(boolean frontSignal,long sinceStart){return frontSignal||sinceStart<NO_SIGNAL_MS;}
    /** A front-app signal exists: one has arrived this session, or the Kids app guard's accessibility service is on. */
    private boolean frontSignal(){
        if(seen)return true;
        try{return KidsGuard.enabledIn(android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),getPackageName(),KidsGuardService.class.getName());}
        catch(RuntimeException e){return false;}
    }
    private boolean cover(){
        try{startActivity(LiveTvCoverActivity.intent(this,live));lastAction="covered";return true;}catch(RuntimeException e){return false;}
    }
    /** Without "Display over other apps", or when Live TV may no longer be on screen: a notification instead. */
    private void notifyDrop(){
        PendingIntent open=PendingIntent.getActivity(this,OPEN_COVER,LiveTvCoverActivity.intent(this,live),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager!=null)try{manager.notify(DROPPED,new Notification.Builder(this,DROP_CHANNEL).setContentTitle("NordVPN disconnected")
            .setContentText("If Live TV is still on, it isn't covered. Open Unified TV to reconnect NordVPN.").setSmallIcon(android.R.drawable.ic_lock_lock)
            .setCategory(Notification.CATEGORY_STATUS).setContentIntent(open).setAutoCancel(true).build());}catch(RuntimeException ignored){}
        lastAction="notified";
    }
    private void cancelNotice(){NotificationManager manager=getSystemService(NotificationManager.class);if(manager!=null)try{manager.cancel(DROPPED);}catch(RuntimeException ignored){}}

    /** NordVPN's own notification says it isn't connected (MediaWatch): look again with the rule. */
    static void nordStopped(){LiveTvGuard g=current;if(g!=null)g.soon();}
    /** The front app changed (the Kids app guard's accessibility service, when it is on): the session ends if Live TV left. */
    static void front(Context c,String pkg,String keyboard){
        LiveTvGuard g=current;if(g==null)return;
        if(left(pkg,g.live,c.getPackageName(),keyboard)){g.main.post(g::stopSelf);return;}
        g.main.post(()->{g.seen=true;if(pkg!=null&&pkg.equals(g.live))g.back();});
    }
    /** Live TV is in front again (front-app signal) after a drop: cover it again while NordVPN is still down. */
    private void back(){
        if(!fired||!LiveTvProtection.dropped(LiveTvProtection.now(this)))return;
        if(!TurnOn.permitted(this)||!cover())notifyDrop();
    }
    /**
     * The cover was left some other way than Back to Home or Reconnect (the TV's own Home, say) while NordVPN is still
     * down: the watch goes on, and a notification says Live TV may be uncovered.
     */
    static void coverLeft(){LiveTvGuard g=current;if(g!=null)g.main.post(()->{if(g.fired&&LiveTvProtection.dropped(LiveTvProtection.now(g)))g.notifyDrop();});}
    /**
     * Pure: whether {@code front} coming to the front means Live TV has left. Live TV itself, NordVPN (reconnecting), Unified
     * TV (its own screens end the session anyway), the keyboard and Android's own system windows don't count.
     */
    static boolean left(String front,String live,String own,String keyboard){
        if(front==null||front.isEmpty()||live==null||live.isEmpty())return false;
        if(front.equals(live)||front.equals(LiveTvProtection.NORDVPN)||front.equals(own)||front.equals(keyboard))return false;
        return !front.equals("android")&&!front.startsWith("com.android.systemui");
    }
    /** For tests: the Live TV package this session watches. */
    String live(){return live;}

    @Override public void onDestroy(){if(current==this)current=null;running=false;main.removeCallbacksAndMessages(null);cancelNotice();
        if(callback!=null)try{getSystemService(ConnectivityManager.class).unregisterNetworkCallback(callback);}catch(RuntimeException ignored){}
        if(screen!=null)try{unregisterReceiver(screen);}catch(RuntimeException ignored){}
        callback=null;screen=null;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}

    /** Any Unified TV screen coming to the front ends the Live TV session: Live TV is no longer what's on screen. */
    static final Application.ActivityLifecycleCallbacks SCREENS=new Application.ActivityLifecycleCallbacks(){
        // The cover itself is the guard's own notice, not the household leaving Live TV: the watch goes on.
        @Override public void onActivityStarted(Activity a){if(running&&!(a instanceof LiveTvCoverActivity))stop(a);}
        @Override public void onActivityCreated(Activity a,Bundle b){}
        @Override public void onActivityResumed(Activity a){}
        @Override public void onActivityPaused(Activity a){}
        @Override public void onActivityStopped(Activity a){}
        @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
        @Override public void onActivityDestroyed(Activity a){}
    };
}
