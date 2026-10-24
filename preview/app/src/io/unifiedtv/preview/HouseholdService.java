package io.unifiedtv.preview;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Explicitly enabled, authenticated HA client. No boot wakeups or unprotected exported receiver. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class HouseholdService extends Service {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AlertPolicy policy=new AlertPolicy();
    private OkHttpClient client;private WebSocket socket;private JSONObject config,house;
    private WindowManager windows;private View cameraOverlay,reminderOverlay,alarmOverlay;private ExoPlayer cameraPlayer;private AudioFocusRequest alarmFocus;
    private boolean stopped,authorised,pendingCamera,simulationOnly;private int retry=1000,command=10;private int streamId;private TextView cameraState;
    private static final int NOTIFICATION=42,STATES=3,STATES_MAX=8_000_000;
    /** Waiting for the one large get_states reply; any other oversized message is still refused. */
    private volatile boolean statesPending;
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
        if(intent!=null&&"simulate".equals(intent.getAction())){simulationOnly=true;simulate(intent);if(socket==null)connect();return START_NOT_STICKY;}
        if(intent!=null&&"camera".equals(intent.getAction())){
            if(authorised){long now=System.currentTimeMillis();if(policy.acceptRing("manual:"+now,now)){if(policy.emergency()){policy.queueRing(now);render(false);}else show();}}
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
                @Override public void onMessage(WebSocket ws,String text){
                    if(text.length()<=65536){main.post(()->message(ws,text));return;}
                    // A whole house of states can be large: read only the sounding alarms here, off the main thread.
                    if(!statesPending||text.length()>STATES_MAX)return;List<String[]> all=AlertPolicy.detectorsIn(text,STATES);if(all!=null)main.post(()->snapshot(ws,all));
                }
                @Override public void onFailure(WebSocket ws,Throwable t,Response response){main.post(()->lost(ws));}
                @Override public void onClosed(WebSocket ws,int code,String reason){main.post(()->lost(ws));}
            });
        }catch(Exception e){
            // An alarm test runs without Home Assistant; otherwise there is nothing to listen to.
            if(simulationOnly){update("Alarm test · Home Assistant not connected");return;}
            update("Not configured · open household settings");stopSelf();
        }
    }
    private void lost(WebSocket ws){if(stopped||ws!=socket)return;socket=null;authorised=false;policy.connection(false);if(policy.emergency())render(false);update("Offline · retrying");main.postDelayed(this::connect,retry);retry=Math.min(30000,retry*2);}
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
                // An alarm already sounding when the TV connects or reconnects is shown straight away.
                if(alarms()){statesPending=true;send(ws,new JSONObject().put("id",STATES).put("type","get_states"));}
                update("Authorised · waiting for subscription confirmation");
                if(pendingCamera){pendingCamera=false;long now=System.currentTimeMillis();if(policy.acceptRing("manual:"+now,now)){if(policy.emergency()){policy.queueRing(now);render(false);}else show();}}
            }else if(type.equals("result")){
                if(value.optInt("id")==STATES){statesPending=false;List<String[]> all=AlertPolicy.detectorsIn(text,STATES);if(all!=null)snapshot(ws,all);return;}
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
        // Alarms first, and before the clock check: the new state is true now, whatever the TV's clock says.
        if(type.equals("state_changed")&&alarms()){JSONObject after=data.optJSONObject("new_state");String entity=data.optString("entity_id");
            String kind=after==null?null:AlertPolicy.alarmKind(entity,after.optJSONObject("attributes")==null?"":after.getJSONObject("attributes").optString("device_class"));
            if(kind!=null){int r=policy.update(entity,kind,AlertPolicy.name(after.optJSONObject("attributes")),after.optString("state"),AlertPolicy.time(after.optString("last_changed")),false);
                if(r!=AlertPolicy.NONE)render(r==AlertPolicy.NEW_ALARM);return;}}
        long now=System.currentTimeMillis();long fired;
        try{fired=Instant.parse(event.getString("time_fired")).toEpochMilli();}catch(Exception e){return;}
        if(fired>now+5000||now-fired>30000)return;
        if(type.equals("state_changed")){
            JSONObject before=data.optJSONObject("old_state"),after=data.optJSONObject("new_state");if(before==null||after==null)return;
            String entity=data.optString("entity_id");
            if(AlertPolicy.rising(entity,house.optString("door"),house.optString("person"),before.optString("state"),after.optString("state"))){
                String id=entity+":"+after.optString("last_changed");if(policy.acceptRing(id,now)){if(policy.emergency()){policy.queueRing(now);render(false);}else show();}
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
        if(policy.emergency())return;
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
    private boolean alarms(){return house!=null&&house.optBoolean("alarms",true);}
    private void snapshot(WebSocket ws,List<String[]> all){
        if(stopped||ws!=socket)return;statesPending=false;
        render(policy.snapshot(all)==AlertPolicy.NEW_ALARM);
    }
    private static final int ALARM_RED=0xF4430B0D;
    private LinearLayout alarmList;private View alarmBanner,warningToast;private String lastWarnings="";
    /**
     * Owner decisions OD-07 (25 October 2026): smoke and carbon monoxide alarms take the screen above everything else, each
     * listed separately with its room and time and acknowledged on its own. While any alarm is active after acknowledgement,
     * a banner stays. Detector warnings are shown apart from emergencies. The doorbell waits until every alarm has cleared.
     * Nothing here touches a detector, and the screen adds to the home's own alarms; it never replaces them.
     */
    private void render(boolean fresh){
        if(stopped)return;
        boolean screen=policy.screen(),emergency=policy.emergency();
        if(!Settings.canDrawOverlays(this))screen=emergency=false;
        if(screen){
            if(fresh||alarmOverlay==null)dismiss();   // a doorbell card or reminder gives way
            alarmScreen();
        }else removeAlarmScreen();
        if(emergency&&!screen)alarmBanner();else removeView(alarmBanner,()->alarmBanner=null);
        warnings(screen);
        update(status());
        if(!policy.emergency()){
            long at=policy.queuedRingAt();int ring=policy.ringAfterAlarm(System.currentTimeMillis());
            if(ring>0&&policy.acceptRing("after-alarm:"+at,System.currentTimeMillis()))show();
            else if(ring<0)reminder("Doorbell rang at "+clock(at)+" during the alarm.");
        }
    }
    private String status(){
        List<AlertPolicy.Detector> on=policy.alarms();
        if(!on.isEmpty()){AlertPolicy.Detector d=on.get(0);return d.title()+(d.name.isEmpty()?"":" — "+d.name)+(on.size()>1?" (+"+(on.size()-1)+")":"");}
        List<AlertPolicy.Detector> w=policy.warnings();
        if(!w.isEmpty())return w.get(0).warning()+(w.get(0).name.isEmpty()?"":": "+w.get(0).name);
        return authorised?"Listening · actual triggers and video need TV tests":"Connecting to household alerts";
    }
    private static String clock(long at){
        if(at<=0)return "";java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.UK);return f.format(new java.util.Date(at));
    }
    private TextView line(LinearLayout parent,String text,int size,boolean bold,int colour){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(colour);t.setGravity(Gravity.CENTER);t.setPadding(0,6,0,6);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);parent.addView(t);return t;
    }
    /** The full screen: one entry per alarm, oldest first, each with its own Acknowledge. */
    private void alarmScreen(){
        if(alarmOverlay==null){
            ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(ALARM_RED);scroll.setFillViewport(true);
            alarmList=new LinearLayout(this);alarmList.setOrientation(LinearLayout.VERTICAL);alarmList.setGravity(Gravity.CENTER);alarmList.setPadding(96,48,96,48);
            scroll.addView(alarmList,new ScrollView.LayoutParams(-1,-1));
            WindowManager.LayoutParams p=params(-1,-1,true);p.gravity=Gravity.CENTER;p.x=0;p.y=0;
            alarmOverlay=scroll;windows.addView(scroll,p);
            // Ask other apps to step back so the alarm can be heard. Whether they pause, lower their sound or ignore it is
            // up to each app; nothing here can pause or mute them.
            alarmFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            try{((AudioManager)getSystemService(AUDIO_SERVICE)).requestAudioFocus(alarmFocus);}catch(RuntimeException ignored){}
        }
        alarmList.removeAllViews();Button first=null;
        for(AlertPolicy.Detector d:policy.alarms()){
            LinearLayout entry=new LinearLayout(this);entry.setOrientation(LinearLayout.VERTICAL);entry.setGravity(Gravity.CENTER);entry.setPadding(24,20,24,20);
            line(entry,d.title()+(d.name.isEmpty()?"":" — "+d.name),40,true,-1);
            String since=d.since>0?"Since "+clock(d.since):"";
            line(entry,since+(d.lost?(since.isEmpty()?"":" · ")+"Detector not responding: treat as still sounding":"")+(d.acknowledged?(since.isEmpty()?"":" · ")+"Acknowledged, still active":""),22,false,0xFFFFE3E3);
            line(entry,d.advice(),26,false,-1);
            if(!d.acknowledged){Button ack=new Button(this);ack.setText("Acknowledge");ack.setTextSize(22);String entity=d.entity;
                ack.setOnClickListener(v->{if(policy.acknowledge(entity))render(false);});entry.addView(ack);if(first==null)first=ack;}
            alarmList.addView(entry);
        }
        if(policy.queuedRingAt()>0)line(alarmList,"Doorbell rang at "+clock(policy.queuedRingAt())+". It will be shown when the alarm has ended.",20,false,0xFFFFE3E3);
        if(!policy.connected())line(alarmList,"Connection to Home Assistant lost: alarm status unknown.",20,true,0xFFFFE3E3);
        for(AlertPolicy.Detector w:policy.warnings())line(alarmList,w.warning()+(w.name.isEmpty()?"":": "+w.name)+(w.since>0?" (since "+clock(w.since)+")":""),18,false,0xFFE8C9C9);
        line(alarmList,"This screen adds to your alarms. It never replaces them.",16,false,0xFFE8C9C9);
        alarmOverlay.setFocusableInTouchMode(true);
        // Back never acknowledges anything by itself: it only brings focus back to the first Acknowledge.
        final Button target=first;
        alarmOverlay.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP&&target!=null)target.requestFocus();return true;}return false;});
        if(first!=null)first.requestFocus();
    }
    private void removeAlarmScreen(){
        removeView(alarmOverlay,()->{alarmOverlay=null;alarmList=null;});
        if(alarmFocus!=null){try{((AudioManager)getSystemService(AUDIO_SERVICE)).abandonAudioFocusRequest(alarmFocus);}catch(RuntimeException ignored){}alarmFocus=null;}
    }
    /** After acknowledgement: a banner that never takes focus, for as long as any alarm is active. */
    private void alarmBanner(){
        removeView(alarmBanner,()->alarmBanner=null);
        LinearLayout banner=new LinearLayout(this);banner.setOrientation(LinearLayout.VERTICAL);banner.setPadding(28,14,28,14);banner.setBackgroundColor(ALARM_RED);
        List<AlertPolicy.Detector> on=policy.alarms();
        for(int i=0;i<Math.min(3,on.size());i++){AlertPolicy.Detector d=on.get(i);
            line(banner,d.title()+(d.name.isEmpty()?"":" — "+d.name)+(d.since>0?" · since "+clock(d.since):"")+" · still active",22,true,-1);}
        if(on.size()>3)line(banner,"+"+(on.size()-3)+" more",18,false,-1);
        if(policy.queuedRingAt()>0)line(banner,"Doorbell rang at "+clock(policy.queuedRingAt())+": shown when the alarm ends",18,false,0xFFFFE3E3);
        if(!policy.connected())line(banner,"Alarm status unknown: connection lost",18,false,0xFFFFE3E3);
        WindowManager.LayoutParams p=params(-2,-2,false);p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.x=0;p.y=24;
        p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        alarmBanner=banner;windows.addView(banner,p);
    }
    /** Detector warnings stay apart from emergencies: a short notice when they change, never styled as an alarm. */
    private void warnings(boolean screen){
        StringBuilder all=new StringBuilder();for(AlertPolicy.Detector w:policy.warnings())all.append(w.entity).append(w.state).append('\n');
        String now=all.toString();if(now.equals(lastWarnings))return;lastWarnings=now;
        if(screen||policy.warnings().isEmpty()||!Settings.canDrawOverlays(this))return;
        AlertPolicy.Detector w=policy.warnings().get(0);
        removeView(warningToast,()->warningToast=null);
        TextView toast=new TextView(this);toast.setText(w.warning()+(w.name.isEmpty()?"":": "+w.name)+(policy.warnings().size()>1?" (+"+(policy.warnings().size()-1)+")":""));
        toast.setTextSize(22);toast.setTextColor(-1);toast.setPadding(24,18,24,18);toast.setBackgroundColor(0xE8352A12);
        warningToast=toast;windows.addView(toast,params(Math.min(760,getResources().getDisplayMetrics().widthPixels-60),-2,false));
        main.postDelayed(()->{if(warningToast==toast)removeView(toast,()->warningToast=null);},10000);
    }
    private void removeView(View v,Runnable after){if(v!=null){try{windows.removeView(v);}catch(RuntimeException ignored){}after.run();}}
    /** In-app test only (adult household settings): clearly labelled TEST events; no detector or Home Assistant involved. */
    private void simulate(Intent intent){
        String kind=intent.getStringExtra("kind"),state=intent.getStringExtra("state"),name=intent.getStringExtra("name");long now=System.currentTimeMillis();
        if("end".equals(kind)){policy.endSimulation();render(false);return;}
        if("door".equals(kind)){if(policy.acceptRing("test:"+now,now)){if(policy.emergency()){policy.queueRing(now);render(false);}else show();}return;}
        if(!"smoke".equals(kind)&&!"carbon_monoxide".equals(kind))return;
        int result=policy.update("simulated."+kind+("on".equals(state)?"":"_"+state),kind,name==null?"":name,state,now,true);
        if(result!=AlertPolicy.NONE)render(result==AlertPolicy.NEW_ALARM);
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
    @Override public void onDestroy(){stopped=true;main.removeCallbacksAndMessages(null);if(socket!=null)socket.cancel();dismiss();removeAlarmScreen();removeView(alarmBanner,()->alarmBanner=null);removeView(warningToast,()->warningToast=null);if(client!=null)client.dispatcher().executorService().shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
