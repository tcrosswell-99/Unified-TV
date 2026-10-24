package io.unifiedtv.preview;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import okhttp3.*;
import org.json.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Explicitly enabled, authenticated HA client. No boot wakeups or unprotected exported receiver. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class HouseholdService extends Service {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AlertPolicy policy=new AlertPolicy();
    private OkHttpClient client;private WebSocket socket;private JSONObject config,house;
    private WindowManager windows;private View cameraOverlay,reminderOverlay;private ExoPlayer cameraPlayer;
    private boolean stopped,authorised,pendingCamera;private int retry=1000,command=10;private int streamId;private TextView cameraState;
    private static final int NOTIFICATION=42;
    private final Runnable timeout=this::dismiss;
    @Override public void onCreate(){super.onCreate();windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("household","Household alerts",NotificationManager.IMPORTANCE_LOW));
        update("Connecting to household alerts");
        client=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).pingInterval(25,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build();
    }
    private void update(String text){
        Intent dismiss=new Intent(this,HouseholdService.class).setAction("dismiss");
        PendingIntent close=PendingIntent.getService(this,1,dismiss,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,HouseholdService.class).setAction("stop");
        PendingIntent end=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"household").setContentTitle("Unified TV household alerts").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).addAction(new Notification.Action.Builder(null,"Dismiss camera",close).build())
            .addAction(new Notification.Action.Builder(null,"Stop alerts",end).build()).build();startForeground(NOTIFICATION,notification);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&"dismiss".equals(intent.getAction())){dismiss();return START_NOT_STICKY;}
        if(intent!=null&&"camera".equals(intent.getAction())){
            if(authorised){if(policy.acceptRing("manual:"+System.currentTimeMillis(),System.currentTimeMillis()))show();}
            else pendingCamera=true;
        }
        if(socket==null)connect();return START_NOT_STICKY;
    }
    private void connect(){
        if(stopped)return;
        try{
            config=new Vault(this).read("ha");house=new Vault(this).read("household");
            if(!config.optBoolean("enabled")||config.optString("token").isEmpty()||!Settings.canDrawOverlays(this))throw new IllegalArgumentException();
            String base=config.getString("base");if(base.startsWith("http:")&&!config.optBoolean("httpConsent"))throw new IllegalArgumentException();
            String url=base.replaceFirst("^http","ws")+"/api/websocket";
            socket=client.newWebSocket(new Request.Builder().url(url).build(),new WebSocketListener(){
                @Override public void onMessage(WebSocket ws,String text){if(text.length()>65536)return;main.post(()->message(ws,text));}
                @Override public void onFailure(WebSocket ws,Throwable t,Response response){main.post(()->lost(ws));}
                @Override public void onClosed(WebSocket ws,int code,String reason){main.post(()->lost(ws));}
            });
        }catch(Exception e){update("Not configured · open household settings");stopSelf();}
    }
    private void lost(WebSocket ws){if(stopped||ws!=socket)return;socket=null;authorised=false;update("Offline · retrying");main.postDelayed(this::connect,retry);retry=Math.min(30000,retry*2);}
    private void send(WebSocket ws,JSONObject command){ws.send(command.toString());}
    private void message(WebSocket ws,String text){
        if(stopped||ws!=socket)return;
        try{
            JSONObject value=new JSONObject(text);String type=value.optString("type");
            if(type.equals("auth_required"))send(ws,new JSONObject().put("type","auth").put("access_token",config.getString("token")));
            else if(type.equals("auth_invalid")){update("Authentication required · update your connection");ws.cancel();socket=null;stopSelf();}
            else if(type.equals("auth_ok")){
                authorised=true;
                retry=1000;send(ws,new JSONObject().put("id",1).put("type","subscribe_events").put("event_type","state_changed"));
                if(house.optBoolean("reminders"))send(ws,new JSONObject().put("id",2).put("type","subscribe_events").put("event_type","unified_tv_reminder"));
                update("Authorised · waiting for subscription confirmation");
                if(pendingCamera){pendingCamera=false;if(policy.acceptRing("manual:"+System.currentTimeMillis(),System.currentTimeMillis()))show();}
            }else if(type.equals("result")){
                if(value.optInt("id")==1)update(value.optBoolean("success")?"Listening · actual triggers and video need TV tests":"Subscription refused · check account permissions");
                if(value.optInt("id")==streamId&&streamId>0){
                    if(!value.optBoolean("success")){if(cameraState!=null)cameraState.setText("Camera unavailable");return;}
                    String path=value.getJSONObject("result").getString("url");
                    // HA returns a relative HLS URL; absolute URLs must match the configured origin.
                    if(path.startsWith(config.getString("base")))path=path.substring(config.getString("base").length());
                    String uri=Endpoint.resolve(config.getString("base"),path).toString();playCamera(uri);
                }
            }else if(type.equals("event"))event(value.getJSONObject("event"));
        }catch(Exception e){if(cameraState!=null)cameraState.setText("Camera unavailable");}
    }
    private void event(JSONObject event)throws Exception{
        JSONObject data=event.optJSONObject("data");if(data==null)return;String type=event.optString("event_type");
        long now=System.currentTimeMillis();long fired;
        try{fired=Instant.parse(event.getString("time_fired")).toEpochMilli();}catch(Exception e){return;}
        if(fired>now+5000||now-fired>30000)return;
        if(type.equals("state_changed")){
            JSONObject before=data.optJSONObject("old_state"),after=data.optJSONObject("new_state");if(before==null||after==null)return;
            String entity=data.optString("entity_id");
            if(AlertPolicy.rising(entity,house.optString("door"),house.optString("person"),before.optString("state"),after.optString("state"))){
                String id=entity+":"+after.optString("last_changed");if(policy.acceptRing(id,now))show();
            }
        }else if(type.equals("unified_tv_reminder")&&house.optBoolean("reminders")){
            String target=data.optString("room");if(!target.isEmpty()&&!target.equals(getSharedPreferences("unified-tv-preview",0).getString("room","tcl")))return;
            if(policy.ringing(now))policy.defer(data.optString("text"),fired);else if(policy.reminderAllowed(fired,now))reminder(data.optString("text"));
        }
    }
    private WindowManager.LayoutParams params(int w,int h,boolean focusable){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            focusable?WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.RIGHT;p.x=30;p.y=30;return p;
    }
    /** A press and a person detection moments apart are one visit: keep the live stream, extend its time. */
    private void show(){if(cameraOverlay!=null){main.removeCallbacks(timeout);main.postDelayed(timeout,45000);}else ring();}
    private void ring(){
        main.removeCallbacks(timeout);
        if(cameraOverlay==null&&getSharedPreferences("unified-tv-preview",0).getString("room","").equals("frame"))HouseholdActivity.backgroundAction(this,"ringEnter");
        dismissViews();if(!Settings.canDrawOverlays(this))return;
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(16,16,16,16);card.setBackgroundColor(0xf0101827);
        cameraState=new TextView(this);cameraState.setTextColor(-1);cameraState.setTextSize(22);cameraState.setText("Connecting to doorbell…");card.addView(cameraState);
        Button back=new Button(this);back.setText("Dismiss camera");back.setOnClickListener(v->dismiss());card.addView(back);
        card.setFocusableInTouchMode(true);card.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)dismiss();return true;}return false;});
        cameraOverlay=card;windows.addView(card,params(Math.min(900,getResources().getDisplayMetrics().widthPixels-60),-2,true));back.requestFocus();
        streamId=++command;try{send(socket,new JSONObject().put("id",streamId).put("type","camera/stream").put("entity_id",house.getString("camera")).put("format","hls"));}catch(Exception e){cameraState.setText("Camera unavailable · check the configured live camera");}
        main.postDelayed(timeout,45000);
    }
    private void playCamera(String uri){
        if(cameraOverlay==null)return;
        LinearLayout card=(LinearLayout)cameraOverlay;PlayerView view=new PlayerView(this);view.setUseController(false);card.addView(view,1,new LinearLayout.LayoutParams(-1,420));
        cameraPlayer=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(()->new PrivateDataSource(config))).build();view.setPlayer(cameraPlayer);cameraPlayer.setVolume(0);
        cameraPlayer.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){if(cameraState!=null)cameraState.setText("Doorbell · live stream");}
            @Override public void onPlaybackStateChanged(int state){if(cameraState!=null&&(state==Player.STATE_BUFFERING||state==Player.STATE_ENDED))cameraState.setText(state==Player.STATE_BUFFERING?"Connecting to doorbell…":"Camera unavailable");}
            @Override public void onPlayerError(PlaybackException e){if(cameraState!=null)cameraState.setText("Camera unavailable");}
        });
        cameraPlayer.setMediaItem(new MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build());cameraPlayer.prepare();cameraPlayer.play();
    }
    private void reminder(String text){
        if(text.trim().isEmpty())return;if(reminderOverlay!=null){windows.removeView(reminderOverlay);reminderOverlay=null;}
        TextView toast=new TextView(this);toast.setText(text.substring(0,Math.min(240,text.length())));toast.setTextSize(22);toast.setTextColor(-1);toast.setPadding(24,18,24,18);toast.setBackgroundColor(0xe820293b);
        reminderOverlay=toast;windows.addView(toast,params(Math.min(650,getResources().getDisplayMetrics().widthPixels-60),-2,false));
        main.postDelayed(()->{if(reminderOverlay==toast){windows.removeView(toast);reminderOverlay=null;}},7000);
    }
    private void dismissViews(){if(cameraPlayer!=null){cameraPlayer.release();cameraPlayer=null;}if(cameraOverlay!=null){windows.removeView(cameraOverlay);cameraOverlay=null;}if(reminderOverlay!=null){windows.removeView(reminderOverlay);reminderOverlay=null;}cameraState=null;}
    private void dismiss(){
        main.removeCallbacks(timeout);
        if(cameraOverlay!=null&&getSharedPreferences("unified-tv-preview",0).getString("room","").equals("frame"))HouseholdActivity.backgroundAction(this,"ringExit");
        streamId=0;policy.dismiss();dismissViews();
        String later=policy.release(System.currentTimeMillis());if(later!=null&&!stopped)reminder(later);
    }
    @Override public void onDestroy(){stopped=true;main.removeCallbacksAndMessages(null);if(socket!=null)socket.cancel();dismiss();if(client!=null)client.dispatcher().executorService().shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
