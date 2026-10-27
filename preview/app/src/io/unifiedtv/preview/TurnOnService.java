package io.unifiedtv.preview;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/**
 * OD-23's small watcher (launcher app only, declared in launcher/AndroidManifest.xml): runs only while "Open Unified TV
 * when the TV turns on" is switched on and allowed ({@link TurnOn#active}), and stops as soon as it isn't.
 *
 * <p>Android delivers "screen on" and "user present" only to a receiver registered by a running process, so this keeps
 * one small foreground service alive for it, with a quiet (low-importance) notification. Its whole cost is that receiver
 * and a Handler: no screens, no pictures, no network (owner's TCL: 2 GB). It does not reuse HouseholdService: that one
 * runs only while Home Assistant alerts are set up and stops itself otherwise, and it would tie this feature to them.
 *
 * <p>Module 03: no keys, no accessibility, no watching other apps' screens. It only hears that the screen came on and, once
 * per wake, asks {@link TurnOn#act} whether to bring Unified TV's Home to the front.
 *
 * <p>Target API 34 will need a foreground service type for this (specialUse, with its explanation property).
 */
public final class TurnOnService extends Service {
    static final String BOOT="io.unifiedtv.TURNED_ON_BOOT";
    private static final String CHANNEL="turnOn";
    private static final int NOTIFICATION=45,OPEN_SETTINGS=5;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final TurnOn.Wake wake=new TurnOn.Wake();
    private final Runnable look=this::look;
    private BroadcastReceiver screen;
    /** The last decision (NONE: Unified TV came to the front); read by tests and owner checks only. */
    static volatile TurnOn.Skip lastSkip;

    /** Starts the watcher; {@code action} BOOT also counts as a wake. Android may refuse (then nothing happens). */
    static void start(Context c,String action){
        try{c.startForegroundService(new Intent(c,TurnOnService.class).setAction(action));}catch(RuntimeException ignored){}
    }
    static void stop(Context c){try{c.stopService(new Intent(c,TurnOnService.class));}catch(RuntimeException ignored){}}

    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager!=null)manager.createNotificationChannel(new NotificationChannel(CHANNEL,TurnOn.TITLE,NotificationManager.IMPORTANCE_LOW));
        Intent settings=new Intent(this,SettingsActivity.class).putExtra("category",SettingsActivity.APPEARANCE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open=PendingIntent.getActivities(this,OPEN_SETTINGS,Launch.overHome(this,settings),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);   // Back from it: Home
        startForeground(NOTIFICATION,new Notification.Builder(this,CHANNEL).setContentTitle(TurnOn.TITLE).setContentText("On. Change it in Settings > Appearance & Home.")
            .setSmallIcon(android.R.drawable.ic_lock_power_off).setOngoing(true).setContentIntent(open).build());
        screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){heard(i==null?null:i.getAction());}};
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);
        // System broadcasts only; nothing outside Android can send these, and no other app can reach this receiver.
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)registerReceiver(screen,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen,f);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(!TurnOn.active(this)){stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&BOOT.equals(intent.getAction())){wake.boot();soon();}
        // Restarted by Android after its process ended: watch again, but a restart is never a wake (Wake starts disarmed).
        return START_STICKY;
    }
    private void heard(String action){
        if(Intent.ACTION_SCREEN_OFF.equals(action)){main.removeCallbacks(look);wake.off();return;}
        if(Intent.ACTION_SCREEN_ON.equals(action)||Intent.ACTION_USER_PRESENT.equals(action))soon();
    }
    /** The first wake signal of a wake: look once, after the TV has settled. */
    private void soon(){if(wake.wake(SystemClock.elapsedRealtime())){main.removeCallbacks(look);main.postDelayed(look,TurnOn.SETTLE_MS);}}
    private void look(){
        if(!TurnOn.active(this)){stopSelf();return;}
        lastSkip=TurnOn.act(this);
    }
    @Override public void onDestroy(){
        main.removeCallbacksAndMessages(null);
        if(screen!=null)try{unregisterReceiver(screen);}catch(RuntimeException ignored){}
        screen=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
