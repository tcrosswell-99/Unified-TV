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
    private boolean stopped,authorised,simulationOnly;private long pendingCamera;private int retry=1000,command=10;private int streamId;private TextView cameraState;
    private static final int NOTIFICATION=42,STATES=3,STATES_MAX=8_000_000;
    /** The alarm screen is up: "Open Unified TV when the TV turns on" (TurnOn) never comes forward over it. */
    static volatile boolean alarmOnScreen;
    /** Waiting for the one large get_states reply; any other oversized message is still refused. */
    private volatile boolean statesPending;
    private final Runnable timeout=this::dismiss,reconnect=this::connect;
    /** A camera asked for while Home Assistant was away is only opened if the connection returns within this time. */
    private static final long CAMERA_REQUEST_MS=45000;
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
        // Owner exception to SET-053: "Stop alerts" no longer stops anything by itself. It opens Unified TV's Settings,
        // which asks for the adult PIN and a confirmation first.
        Intent stop=new Intent(this,SettingsActivity.class).putExtra("category",SettingsActivity.NOTIFICATIONS).putExtra("stopAlerts",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent end=PendingIntent.getActivity(this,2,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"household").setContentTitle("Unified TV household alerts").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).addAction(new Notification.Action.Builder(null,"Dismiss camera",close).build())
            .addAction(new Notification.Action.Builder(null,"Stop alerts",end).build()).build();startForeground(NOTIFICATION,notification);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"stop".equals(intent.getAction()))return START_STICKY;   // an old notification's Stop: ignored (the PIN is needed)
        if(intent!=null&&"dismiss".equals(intent.getAction())){dismiss();return START_STICKY;}
        if(intent!=null&&"simulate".equals(intent.getAction())){simulationOnly=true;simulate(intent);if(socket==null)connect();return START_STICKY;}
        getSharedPreferences("unified-tv-preview",0).edit().putBoolean(AlarmWatch.STOPPED,false).apply();   // started on purpose: alerts are on
        if(intent!=null&&"camera".equals(intent.getAction())){
            if(authorised){long now=System.currentTimeMillis();if(policy.acceptRing("manual:"+now,now)){if(policy.emergency()){policy.queueRing(now);render(false);}else show();}}
            else pendingCamera=System.currentTimeMillis();
        }
        // Sticky: if the system ends the process, it restarts the service and monitoring resumes with a fresh full picture.
        if(socket==null)connect();return START_STICKY;
    }
    private void connect(){
        main.removeCallbacks(reconnect);
        if(stopped||socket!=null)return;
        try{
            config=new Vault(this).read("ha");house=new Vault(this).read("household");
            getSharedPreferences("unified-tv-preview",0).edit().putBoolean(AlarmWatch.SHOWN,house.optBoolean("alarms",true)).apply();
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
            getSharedPreferences("unified-tv-preview",0).edit().putBoolean(AlarmWatch.CONFIGURED,false).apply();
            update("Not configured · open household settings");stopSelf();
        }
    }
    private void lost(WebSocket ws){if(stopped||ws!=socket)return;socket=null;if(linked){linked=false;EventLog.record(this,EventLog.CONNECTION,"Home Assistant connection lost; retrying");}authorised=false;policy.connection(false);if(policy.emergency())render(false);update("Offline · retrying");main.postDelayed(reconnect,retry);retry=Math.min(30000,retry*2);}
    private void send(WebSocket ws,JSONObject command){ws.send(command.toString());}
    private void message(WebSocket ws,String text){
        if(stopped||ws!=socket)return;
        try{
            JSONObject value=new JSONObject(text);String type=value.optString("type");
            if(type.equals("auth_required"))send(ws,new JSONObject().put("type","auth").put("access_token",config.getString("token")));
            else if(type.equals("auth_invalid")){
                ws.cancel();socket=null;authorised=false;
                // An alarm already on screen stays, with its status unknown: losing access never clears an alarm.
                if(policy.emergency()){policy.connection(false);render(false);update("Authentication required · alarm status unknown");return;}
                update("Authentication required · update your connection");stopSelf();}
            else if(type.equals("auth_ok")){
                authorised=true;getSharedPreferences("unified-tv-preview",0).edit().putBoolean(AlarmWatch.CONFIGURED,true).apply();
                if(!linked){linked=true;EventLog.record(this,EventLog.CONNECTION,"Household alerts listening to Home Assistant");}
                retry=1000;send(ws,new JSONObject().put("id",1).put("type","subscribe_events").put("event_type","state_changed"));
                if(house.optBoolean("reminders"))send(ws,new JSONObject().put("id",2).put("type","subscribe_events").put("event_type","unified_tv_reminder"));
                // An alarm already sounding when the TV connects or reconnects is shown straight away.
                if(alarms()){statesPending=true;send(ws,new JSONObject().put("id",STATES).put("type","get_states"));}
                else if(policy.forgetDetectors()!=AlertPolicy.NONE)render(false);   // alarms switched off in household settings
                update("Authorised · waiting for subscription confirmation");
                long asked=pendingCamera;pendingCamera=0;
                if(asked>0&&System.currentTimeMillis()-asked<CAMERA_REQUEST_MS){long now=System.currentTimeMillis();if(policy.acceptRing("manual:"+now,now)){if(policy.emergency()){policy.queueRing(now);render(false);}else show();}}
            }else if(type.equals("result")){
                if(value.optInt("id")==STATES){statesPending=false;List<String[]> all=AlertPolicy.detectorsIn(text,STATES);if(all!=null)snapshot(ws,all);return;}
                if(value.optInt("id")==1)update(value.optBoolean("success")?"Listening · actual triggers and video need TV tests":"Subscription refused · check account permissions");
                if(value.optInt("id")==streamId&&streamId>0){
                    if(!value.optBoolean("success")){unavailable();return;}
                    String path=value.getJSONObject("result").getString("url");
                    // HA returns a relative HLS URL; absolute URLs must match the configured origin.
                    if(path.startsWith(config.getString("base")))path=path.substring(config.getString("base").length());
                    String uri=Endpoint.resolve(config.getString("base"),path).toString();playCamera(uri);
                }
            }else if(type.equals("event"))event(value.getJSONObject("event"));
        }catch(Exception e){if(cameraState!=null)unavailable();}
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
            int kind=AlertPolicy.ringKind(entity,house.optString("door"),house.optString("person"),before.optString("state"),after.optString("state"),after.optJSONObject("attributes"));
            if(kind!=AlertPolicy.RING_NONE)lastEvent(kind==AlertPolicy.RING_PRESS?"doorbell press":"person",now);
            if(kind==AlertPolicy.RING_PRESS){
                String id=entity+":"+after.optString("last_changed");if(policy.acceptRing(id,now)){EventLog.record(this,EventLog.DOORBELL,policy.emergency()?"Doorbell pressed during an alarm: camera held back":"Doorbell pressed: camera shown");if(policy.emergency()){policy.queueRing(now);render(false);}else show();}
            }else if(kind==AlertPolicy.RING_PERSON&&policy.acceptPerson(entity,now))person();
        }else if(type.equals("unified_tv_reminder")&&house.optBoolean("reminders")){
            String target=data.optString("room");if(!target.isEmpty()&&!target.equals(getSharedPreferences("unified-tv-preview",0).getString("room","tcl")))return;
            if(policy.emergency())return;   // spec 4.10: reminders arriving during an emergency are dropped, not held
            if(policy.ringing(now))policy.defer(data.optString("text"),fired);else if(policy.reminderAllowed(fired,now))reminder(data.optString("text"));
        }
    }
    /** For Owner diagnostics: the kind and time of the last doorbell event that reached this TV. No entity, name or address. */
    private void lastEvent(String kind,long at){getSharedPreferences("unified-tv-preview",0).edit().putString("householdLastEvent",kind).putLong("householdLastEventAt",at).apply();}
    private WindowManager.LayoutParams params(int w,int h,boolean focusable){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            focusable?WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.RIGHT;p.x=30;p.y=30;return p;
    }
    /** A press and a person detection moments apart are one visit: keep the live stream, extend its time. */
    /** SET-051: the household's auto-close (30 s by default, 60 s, or only with Back); 0 means the card waits for Back. */
    private long closeMs(){android.content.SharedPreferences p=getSharedPreferences("unified-tv-preview",0);return Accessibility.time(p,DoorbellCamera.closeMs(p.getString(DoorbellCamera.CLOSE,"")));}   // SET-073 longer times
    private void closeLater(){main.removeCallbacks(timeout);long ms=closeMs();if(ms>0)main.postDelayed(timeout,ms);}
    /** SET-054: whether the visit on screen is a test (household setup's "Test: someone rings the doorbell"). */
    private boolean testVisit;
    private void show(){show(false);}
    private void show(boolean test){if(cameraOverlay!=null){if(!test)testVisit=false;closeLater();}else{testVisit=test;ring();}}
    private void ring(){
        main.removeCallbacks(timeout);
        if(policy.emergency())return;
        if(cameraOverlay==null&&getSharedPreferences("unified-tv-preview",0).getString("room","").equals("frame"))HouseholdActivity.backgroundAction(this,"ringEnter");
        dismissViews();if(!Settings.canDrawOverlays(this))return;
        // RING-03 and the owner's "Front Door" design: a large picture-in-picture card in the lower right, so whatever is
        // playing stays visible and keeps playing (the camera is muted and takes no audio focus). The camera's own name,
        // "Live" only once real frames arrive, and the time. Back closes it and focus returns to what was there before.
        // SET-051: a large overlay (half the width, lower right) where this TV allows it, or a full-screen card.
        boolean full=DoorbellCamera.FULL.equals(DoorbellCamera.layout(getSharedPreferences("unified-tv-preview",0).getString(DoorbellCamera.LAYOUT,"")));
        int width=full?getResources().getDisplayMetrics().widthPixels:Math.round(getResources().getDisplayMetrics().widthPixels*.5f),height=full?getResources().getDisplayMetrics().heightPixels:Math.round(width*9f/16f);
        android.widget.FrameLayout card=new android.widget.FrameLayout(this);card.setPadding(14,14,14,14);
        android.graphics.drawable.GradientDrawable frame=new android.graphics.drawable.GradientDrawable();frame.setCornerRadius(40);frame.setColor(0xCC1A1F2A);frame.setStroke(2,0x55FFFFFF);card.setBackground(frame);
        android.widget.FrameLayout screen=new android.widget.FrameLayout(this);screen.setBackgroundColor(0xFF05070B);
        screen.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,android.graphics.Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),28);}});screen.setClipToOutline(true);
        card.addView(screen,new android.widget.FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(40,32,40,0);
        TextView title=new TextView(this);title.setText(DoorbellCamera.title(cameraName(),testVisit));title.setTextColor(-1);title.setTextSize(40);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);title.setShadowLayer(8,0,2,0x99000000);heading.addView(title);
        cameraState=pill("Connecting…",0xB3303846);LinearLayout.LayoutParams pillAt=new LinearLayout.LayoutParams(-2,-2);pillAt.topMargin=10;heading.addView(cameraState,pillAt);
        screen.addView(heading,new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.START));
        cameraClock=pill("",0xA6000000);android.widget.FrameLayout.LayoutParams clockAt=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);clockAt.setMargins(0,0,28,24);screen.addView(cameraClock,clockAt);
        TextView hint=new TextView(this);hint.setText(DoorbellCamera.hint(getSharedPreferences("unified-tv-preview",0).getString(DoorbellCamera.CLOSE,"")));hint.setTextColor(0xCCFFFFFF);hint.setTextSize(16);hint.setShadowLayer(6,0,1,0x99000000);
        android.widget.FrameLayout.LayoutParams hintAt=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);hintAt.setMargins(40,0,0,30);screen.addView(hint,hintAt);
        cameraScreen=screen;tick.run();
        card.setFocusable(true);card.setFocusableInTouchMode(true);card.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)dismiss();return true;}return false;});
        WindowManager.LayoutParams at=full?params(-1,-1,true):params(width+28,height+28,true);
        if(full){at.gravity=Gravity.CENTER;at.x=0;at.y=0;frame.setCornerRadius(0);}else{at.gravity=Gravity.BOTTOM|Gravity.RIGHT;at.x=48;at.y=48;}
        cameraOverlay=card;windows.addView(card,at);card.requestFocus();
        streamId=++command;
        try{if(socket==null)throw new IllegalStateException();send(socket,new JSONObject().put("id",streamId).put("type","camera/stream").put("entity_id",house.getString("camera")).put("format","hls"));main.postDelayed(noPicture,NO_PICTURE_MS);}
        catch(Exception e){unavailable();}
        closeLater();
    }
    private android.widget.FrameLayout cameraScreen;private TextView cameraClock,cameraNote;
    /** RING-03 fallback: no live picture within this time (or no camera set up) says so plainly; the card still shows who rang. */
    private static final long NO_PICTURE_MS=12000;
    private final Runnable noPicture=this::unavailable;
    private void unavailable(){
        main.removeCallbacks(noPicture);if(cameraOverlay==null||cameraScreen==null)return;livePill("Live view unavailable",false);
        if(cameraNote!=null)return;
        cameraNote=new TextView(this);cameraNote.setText("The doorbell was pressed at "+clock(System.currentTimeMillis())+".\nThe live camera isn't available right now; the Ring app can show it.");
        cameraNote.setTextColor(-1);cameraNote.setTextSize(22);cameraNote.setGravity(Gravity.CENTER);cameraNote.setPadding(40,0,40,0);
        cameraScreen.addView(cameraNote,new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
    }
    private View personToast;
    /**
     * RING-02: a confirmed person gets a small toast naming the doorbell, for 5 seconds. It never opens the camera, never
     * takes focus or sound, and never appears during an emergency or over an open doorbell card.
     */
    private void person(){person(false);}
    private void person(boolean test){
        if(stopped||policy.emergency()||cameraOverlay!=null||!Settings.canDrawOverlays(this))return;
        android.content.SharedPreferences prefs=getSharedPreferences("unified-tv-preview",0);
        String who=test?"Test: person at the door":"Person at the door";
        if(!AlertPolicy.personToastAllowed(Profiles.kids(prefs),prefs.getBoolean(AlertPolicy.KIDS_PERSON_PREF,true))){EventLog.record(this,EventLog.PERSON,who+": not shown while Kids is watching");return;}
        // SET-052: quiet hours hold back person toasts only; doorbell presses and smoke or carbon monoxide alarms never ask.
        if(!QuietHours.personAllowed(prefs,HouseholdSettings.zone(this),System.currentTimeMillis())){EventLog.record(this,EventLog.PERSON,who+": not shown in quiet hours");return;}
        EventLog.record(this,EventLog.PERSON,who+": shown");
        removeView(personToast,()->personToast=null);
        TextView toast=new TextView(this);toast.setText(DoorbellCamera.person(cameraName(),test));toast.setTextSize(22);toast.setTextColor(-1);toast.setPadding(28,18,28,18);
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setCornerRadius(28);g.setColor(0xE620293B);g.setStroke(2,0x40FFFFFF);toast.setBackground(g);
        WindowManager.LayoutParams p=params(-2,-2,false);p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        personToast=toast;windows.addView(toast,p);
        main.postDelayed(()->{if(personToast==toast)removeView(toast,()->personToast=null);},Accessibility.time(prefs,AlertPolicy.PERSON_TOAST_MS));
    }
    /** The camera's own name from household setup, else the doorbell's, else plain "Doorbell". */
    private String cameraName(){
        JSONObject names=house==null?null:house.optJSONObject("names");
        String name=names==null?"":names.optString("camera",names.optString("door",""));
        return name.trim().isEmpty()?"Doorbell":name.trim().length()>40?name.trim().substring(0,40):name.trim();
    }
    private TextView pill(String text,int colour){
        TextView t=new TextView(this);t.setText(text);t.setTextColor(-1);t.setTextSize(20);t.setPadding(22,8,22,8);
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setCornerRadius(40);g.setColor(colour);t.setBackground(g);return t;
    }
    private void livePill(String text,boolean live){
        if(cameraState==null)return;cameraState.setText(live?"● "+text:text);
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setCornerRadius(40);g.setColor(live?0xE62E9E4F:0xB3303846);cameraState.setBackground(g);
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(cameraClock==null||cameraOverlay==null)return;
        cameraClock.setText(java.time.ZonedDateTime.now(HouseholdSettings.zone(HouseholdService.this)).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy "+(HouseholdSettings.twentyFour(HouseholdService.this)?"HH:mm:ss":"h:mm:ss a"),java.util.Locale.getDefault())));
        main.postDelayed(this,1000);}};
    private void playCamera(String uri){
        if(cameraOverlay==null||cameraScreen==null)return;
        PlayerView view=new PlayerView(this);view.setUseController(false);view.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);cameraScreen.addView(view,0,new android.widget.FrameLayout.LayoutParams(-1,-1));
        cameraPlayer=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(()->new PrivateDataSource(config))).build();view.setPlayer(cameraPlayer);cameraPlayer.setVolume(0);
        cameraPlayer.addListener(new Player.Listener(){
            // "Live" only once real frames are on screen (spec 4.10: never label a still as live).
            @Override public void onRenderedFirstFrame(){main.removeCallbacks(noPicture);if(cameraNote!=null){cameraScreen.removeView(cameraNote);cameraNote=null;}livePill("Live",true);}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_BUFFERING)livePill("Connecting…",false);else if(state==Player.STATE_ENDED)unavailable();}
            @Override public void onPlayerError(PlaybackException e){unavailable();}
        });
        cameraPlayer.setMediaItem(new MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build());cameraPlayer.prepare();cameraPlayer.play();
    }
    private boolean alarms(){return house!=null&&house.optBoolean("alarms",true);}
    private void snapshot(WebSocket ws,List<String[]> all){
        if(stopped||ws!=socket)return;statesPending=false;
        getSharedPreferences("unified-tv-preview",0).edit().putInt(AlarmWatch.DETECTORS,all.size()).apply();   // SET-053: none paired is "not configured"
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
    /** SET-055: whether Home Assistant was last recorded as connected, and the alarms already in the event log. */
    private boolean linked;private final java.util.Set<String> loggedAlarms=new java.util.HashSet<>();
    private void logAlarms(){
        for(AlertPolicy.Detector d:policy.alarms())if(loggedAlarms.add(d.entity+":"+d.since))
            EventLog.record(this,EventLog.ALERT,(d.simulated?"Test: ":"")+("smoke".equals(d.kind)?"Smoke alarm shown":"Carbon monoxide alarm shown"));
        if(loggedAlarms.size()>64)loggedAlarms.clear();
    }
    private void render(boolean fresh){
        if(stopped)return;
        if(fresh)logAlarms();
        boolean screen=policy.screen(),emergency=policy.emergency();
        if(!Settings.canDrawOverlays(this))screen=emergency=false;
        if(screen){
            if(fresh||alarmOverlay==null){dismiss();removeView(personToast,()->personToast=null);}   // a doorbell card, reminder or person toast gives way
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
    private String clock(long at){
        if(at<=0)return "";return HouseholdSettings.time(this,at);
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
            alarmOverlay=scroll;windows.addView(scroll,p);alarmOnScreen=true;
            // Ask other apps to step back so the alarm can be heard. Whether they pause, lower their sound or ignore it is
            // up to each app; nothing here can pause or mute them.
            alarmFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            try{((AudioManager)getSystemService(AUDIO_SERVICE)).requestAudioFocus(alarmFocus);}catch(RuntimeException ignored){}
        }
        // Redraws (a doorbell press, a name change, a warning) keep focus on the same alarm's Acknowledge, so OK never
        // acknowledges a different detector than the one the viewer had chosen.
        View had=alarmOverlay.findFocus();Object focused=had==null?null:had.getTag();
        alarmList.removeAllViews();Button first=null,again=null;
        for(AlertPolicy.Detector d:policy.alarms()){
            LinearLayout entry=new LinearLayout(this);entry.setOrientation(LinearLayout.VERTICAL);entry.setGravity(Gravity.CENTER);entry.setPadding(24,20,24,20);
            line(entry,d.title()+(d.name.isEmpty()?"":" — "+d.name),40,true,-1);
            String since=d.since>0?"Since "+clock(d.since):"";
            line(entry,since+(d.lost?(since.isEmpty()?"":" · ")+"Detector not responding: treat as still sounding":"")+(d.acknowledged?(since.isEmpty()?"":" · ")+"Acknowledged, still active":""),22,false,0xFFFFE3E3);
            line(entry,d.advice(),26,false,-1);
            if(!d.acknowledged){Button ack=new Button(this);ack.setText("Acknowledge");ack.setTextSize(22);String entity=d.entity;
                ack.setTag(entity);ack.setOnKeyListener(backKeepsFocus);
                ack.setOnClickListener(v->{if(policy.acknowledge(entity))render(false);});entry.addView(ack);if(first==null)first=ack;if(entity.equals(focused))again=ack;}
            alarmList.addView(entry);
        }
        if(policy.queuedRingAt()>0)line(alarmList,"Doorbell rang at "+clock(policy.queuedRingAt())+". It will be shown when the alarm has ended.",20,false,0xFFFFE3E3);
        if(!policy.connected())line(alarmList,"Connection to Home Assistant lost: alarm status unknown.",20,true,0xFFFFE3E3);
        for(AlertPolicy.Detector w:policy.warnings())line(alarmList,w.warning()+(w.name.isEmpty()?"":": "+w.name)+(w.since>0?" (since "+clock(w.since)+")":""),18,false,0xFFE8C9C9);
        line(alarmList,"This screen adds to your alarms. It never replaces them.",16,false,0xFFE8C9C9);
        alarmOverlay.setFocusableInTouchMode(true);alarmOverlay.setOnKeyListener(backKeepsFocus);
        if(again!=null)again.requestFocus();else if(first!=null)first.requestFocus();
    }
    /** Back on the alarm screen never acknowledges or closes anything: it is consumed, and focus stays where it is. */
    private final View.OnKeyListener backKeepsFocus=(v,key,e)->key==KeyEvent.KEYCODE_BACK;
    private void removeAlarmScreen(){
        removeView(alarmOverlay,()->{alarmOverlay=null;alarmList=null;});alarmOnScreen=false;
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
        String now=all.toString();if(now.equals(lastWarnings))return;
        if(policy.warnings().isEmpty()){lastWarnings=now;return;}
        // Not yet shown while the alarm screen is up (it lists them itself): the notice follows once the screen goes.
        if(screen||!Settings.canDrawOverlays(this))return;lastWarnings=now;
        AlertPolicy.Detector w=policy.warnings().get(0);
        removeView(warningToast,()->warningToast=null);
        TextView toast=new TextView(this);toast.setText(w.warning()+(w.name.isEmpty()?"":": "+w.name)+(policy.warnings().size()>1?" (+"+(policy.warnings().size()-1)+")":""));
        toast.setTextSize(22);toast.setTextColor(-1);toast.setPadding(24,18,24,18);toast.setBackgroundColor(0xE8352A12);
        warningToast=toast;windows.addView(toast,params(Math.min(760,getResources().getDisplayMetrics().widthPixels-60),-2,false));
        main.postDelayed(()->{if(warningToast==toast)removeView(toast,()->warningToast=null);},Accessibility.time(getSharedPreferences("unified-tv-preview",0),10000));
    }
    private void removeView(View v,Runnable after){if(v!=null){try{windows.removeView(v);}catch(RuntimeException ignored){}after.run();}}
    /** In-app test only (adult household settings): clearly labelled TEST events; no detector or Home Assistant involved. */
    private void simulate(Intent intent){
        String kind=intent.getStringExtra("kind"),state=intent.getStringExtra("state"),name=intent.getStringExtra("name");long now=System.currentTimeMillis();
        if("end".equals(kind)){policy.endSimulation();render(false);return;}
        if("door".equals(kind)){if(policy.acceptRing("test:"+now,now)){if(policy.emergency()){policy.queueRing(now);render(false);}else show(true);}return;}
        if("person".equals(kind)){if(policy.acceptPerson("test:"+now,now))person(true);return;}
        if(!"smoke".equals(kind)&&!"carbon_monoxide".equals(kind))return;
        int result=policy.update("simulated."+kind+("on".equals(state)?"":"_"+state),kind,name==null?"":name,state,now,true);
        if(result!=AlertPolicy.NONE)render(result==AlertPolicy.NEW_ALARM);
    }
    private void reminder(String text){
        if(text.trim().isEmpty())return;if(reminderOverlay!=null){windows.removeView(reminderOverlay);reminderOverlay=null;}
        TextView toast=new TextView(this);toast.setText(text.substring(0,Math.min(240,text.length())));toast.setTextSize(22);toast.setTextColor(-1);toast.setPadding(24,18,24,18);toast.setBackgroundColor(0xe820293b);
        reminderOverlay=toast;windows.addView(toast,params(Math.min(650,getResources().getDisplayMetrics().widthPixels-60),-2,false));
        main.postDelayed(()->{if(reminderOverlay==toast){windows.removeView(toast);reminderOverlay=null;}},Accessibility.time(getSharedPreferences("unified-tv-preview",0),7000));
    }
    private void dismissViews(){if(cameraPlayer!=null){cameraPlayer.release();cameraPlayer=null;}if(cameraOverlay!=null){windows.removeView(cameraOverlay);cameraOverlay=null;}main.removeCallbacks(tick);main.removeCallbacks(noPicture);cameraScreen=null;cameraClock=null;cameraNote=null;if(reminderOverlay!=null){windows.removeView(reminderOverlay);reminderOverlay=null;}cameraState=null;}
    private void dismiss(){
        main.removeCallbacks(timeout);testVisit=false;
        if(cameraOverlay!=null&&getSharedPreferences("unified-tv-preview",0).getString("room","").equals("frame"))HouseholdActivity.backgroundAction(this,"ringExit");
        streamId=0;policy.dismiss();dismissViews();
        String later=policy.release(System.currentTimeMillis());if(later!=null&&!stopped)reminder(later);
    }
    @Override public void onDestroy(){stopped=true;main.removeCallbacksAndMessages(null);if(socket!=null)socket.cancel();dismiss();removeAlarmScreen();removeView(alarmBanner,()->alarmBanner=null);removeView(warningToast,()->warningToast=null);removeView(personToast,()->personToast=null);if(client!=null)client.dispatcher().executorService().shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
