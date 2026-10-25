package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.os.*;
import android.view.KeyEvent;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/**
 * Embedded direct play. No arbitrary external URLs, tokens in query strings or VLC UI.
 * The remote owns the player: OK or Play/Pause toggles, Left/Right seek (faster when held), Down or Menu chooses sound and subtitles,
 * Stop and Back return to the library. Leaving, standby and errors keep the place; nothing waits forever and nothing private is logged.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Screen {
    static final String RESUME_PREFS="unified-tv-resume";
    private ExoPlayer player; private PlayerView view; private JSONObject config; private String id; private boolean started;
    private int generation;private boolean dlna;private String resumeKey;
    /** Where to start: -1 until decided, then the server's place (account), this TV's place (NAS) or where it was before leaving. */
    private volatile long position=-1;private boolean playWhenReady=true;
    private PlaybackDiagnostics diagnostics;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable progress=new Runnable(){public void run(){report("/Sessions/Playing/Progress");handler.postDelayed(this,10000);}};
    private final Runnable stallCheck=()->{if(player!=null&&player.getPlaybackState()==Player.STATE_BUFFERING&&player.getPlayWhenReady())fail(PlaybackPolicy.Kind.STALLED,"STALLED");};

    @Override public void onCreate(Bundle state){super.onCreate(state);
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF);// Media3 errors can name the stream address.
        id=getIntent().getStringExtra("item");dlna=getIntent().hasExtra("dlnaUrl");
        if(state!=null){position=state.getLong("position",-1);playWhenReady=state.getBoolean("playWhenReady",true);}
        diagnostics=PlaybackDiagnostics.begin(dlna?"nas":"media-account",SystemClock.elapsedRealtime());
        preparing();
    }
    /** The title as the library showed it, already free of file extensions. */
    private String title(){String t=DlnaClient.title(getIntent().getStringExtra("title"));return t.length()>120?t.substring(0,120):t;}
    private void preparing(){page("Your Films & TV");text(title().isEmpty()?"Preparing playback…":"Preparing "+title()+"…",24);}
    /** A restart (after standby or another app) shows the preparing card, not a stale black player, until playback is back. */
    @Override protected void onStart(){super.onStart();if(generation>0)preparing();prepare();}
    private void prepare(){
        final int ticket=++generation;
        worker.execute(()->{
            try{
                if(dlna){prepareDlna(ticket);return;}
                config=new Vault(this).read(Vault.mediaKey(this));
                JSONObject me=LocalApi.get(config,"/Users/Me");
                MediaAccount.require(me,config.optString("user"),EditLock.child(this));
                if(position<0){JSONObject item=LocalApi.get(config,"/Users/"+LocalApi.segment(config.optString("user"))+"/Items/"+LocalApi.segment(id));
                    long resume=item.optJSONObject("UserData")==null?0:item.optJSONObject("UserData").optLong("PlaybackPositionTicks",0)/10000;position=resume;}
                String stream=config.getString("base")+"/Videos/"+LocalApi.segment(id)+"/stream?static=true";
                JSONObject source=config;
                runOnUiThread(()->play(ticket,stream,source));
            }catch(Exception e){runOnUiThread(()->{if(ticket==generation&&!isFinishing()&&!isDestroyed())unavailable(e instanceof KidsRefused?"This title isn't in the Kids folders, or its rating isn't for Kids. Ask an adult.":e instanceof SecurityException?"This title isn't available on this profile. Check this profile's media account.":"The film library didn't answer. Check that the media server is on and this profile is connected.",!(e instanceof SecurityException));});}
        });
    }
    /** Kids asked for something outside the Kids folders or the rating table. */
    static final class KidsRefused extends SecurityException{}
    private void prepareDlna(int ticket)throws Exception{
        boolean kids=Profiles.kids(this);
        JSONObject saved=new Vault(this).read(kids?KidsPolicy.STORE:"dlna");if(!saved.optBoolean("enabled")||saved.optJSONArray("roots")==null)throw kids?new KidsRefused():new SecurityException();
        String stream=getIntent().getStringExtra("dlnaUrl");java.net.URI uri=DlnaClient.local(stream),origin=DlnaClient.local(saved.getString("control"));
        if(!origin.getHost().equalsIgnoreCase(uri.getHost()))throw kids?new KidsRefused():new SecurityException();
        if(DlnaClient.lan==null)DlnaClient.lan=LocalDiscovery.lan(this);
        if(kids)kidsCheck(saved,stream);
        int port=uri.getPort()<0?("https".equals(uri.getScheme())?443:80):uri.getPort();
        JSONObject source=new JSONObject().put("enabled",true).put("httpConsent",true).put("dlna",true).put("base",Endpoint.base(uri.getHost(),""+port,"https".equals(uri.getScheme())));config=source;
        resumeKey=ResumeStore.key(stream);
        long here=position<0?resumeStore().position(resumeKey,System.currentTimeMillis()):0;
        runOnUiThread(()->{if(position<0&&here>0)askResume(ticket,here,stream,source);else play(ticket,stream,source);});
    }
    /**
     * Kids, at play time, whatever opened the player: the server itself must say this title sits inside a Kids folder
     * (its parentID chain, KidsPolicy.playable), the stream must be that title's own, and no rating on the way may be
     * refused. The library and the Home shelf already leave such titles out; this is the wall, not the poster.
     */
    private void kidsCheck(JSONObject kids,String stream)throws Exception{
        java.util.Set<String> roots=KidsPolicy.roots(kids);String item=getIntent().getStringExtra("dlnaItem"),root=getIntent().getStringExtra("root");
        if(item==null||root==null||!roots.contains(root))throw new KidsRefused();
        DlnaClient.Item self=DlnaClient.metadata(kids,item);
        if(self==null||self.folder||!stream.equals(self.url))throw new KidsRefused();
        String udn=kids.optString("udn");
        if(!KidsPolicy.playable(item,roots,id->{DlnaClient.Item i=id.equals(item)?self:DlnaClient.metadata(kids,id);return i==null?null:CatalogueItem.fromDlna(i,udn,root);}))throw new KidsRefused();
    }
    /** This profile's own places (Adults and Kids are kept apart). */
    private ResumeStore resumeStore(){return ResumeStore.load(this);}
    /** Local resume is labelled as this TV's own: it is not Continue Watching and is not shared with the server. */
    private void askResume(int ticket,long here,String stream,JSONObject source){
        if(ticket!=generation||isFinishing()||isDestroyed())return;
        page(title().isEmpty()?"Your Films & TV":title());text("You stopped watching at "+PlaybackPolicy.clock(here)+" on this TV.",22);
        button("Resume from "+PlaybackPolicy.clock(here)+" (on this TV)",()->{if(ticket!=generation)return;position=here;play(ticket,stream,source);});
        button("Start from the beginning",()->{if(ticket!=generation)return;position=0;play(ticket,stream,source);});
        button("Back",this::finish);
    }
    private void play(int ticket,String stream,JSONObject source){
        if(ticket!=generation||isFinishing()||isDestroyed()||player!=null)return;
        if(position<0)position=0;
        view=new PlayerView(this);setContentView(view);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(()->new PrivateDataSource(source))).build();
        view.setPlayer(player);view.setKeepScreenOn(true);view.setUseController(true);view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);view.setControllerShowTimeoutMs(3500);view.requestFocus();
        player.addListener(new Player.Listener(){
            @Override public void onIsPlayingChanged(boolean playing){if(playing&&!started&&!dlna){started=true;report("/Sessions/Playing");handler.post(progress);}}
            @Override public void onRenderedFirstFrame(){diagnostics.firstFrame(SystemClock.elapsedRealtime());}
            @Override public void onTracksChanged(Tracks tracks){PlaybackPolicy.Kind unplayable=record(tracks);if(unplayable!=null)handler.post(()->fail(unplayable,unplayable==PlaybackPolicy.Kind.PICTURE?"VIDEO_UNSUPPORTED":"AUDIO_UNSUPPORTED"));}
            @Override public void onPlaybackStateChanged(int state){
                handler.removeCallbacks(stallCheck);
                if(state==Player.STATE_BUFFERING&&player!=null&&player.getPlayWhenReady()){if(diagnostics.firstFrameMs()>=0)diagnostics.rebuffer();handler.postDelayed(stallCheck,PlaybackPolicy.STALL_MS);}
                if(state==Player.STATE_ENDED){remember();finish();}
            }
            @Override public void onPlayerError(PlaybackException e){PlaybackPolicy.Kind kind=PlaybackPolicy.kind(e.errorCode);String name=e.getErrorCodeName();handler.post(()->fail(kind,name));}
        });
        player.setMediaItem(MediaItem.fromUri(stream),position);player.setPlayWhenReady(playWhenReady);player.prepare();
        diagnostics.prepared(SystemClock.elapsedRealtime(),position);
        if(!playWhenReady)view.showController();
    }
    /** A plain explanation with Try again and Back; the viewer's place is kept for the retry. */
    private void fail(PlaybackPolicy.Kind kind,String codeName){
        if(player==null)return;
        diagnostics.failed(codeName,kind);remember();release();
        unavailable(PlaybackPolicy.message(kind),PlaybackPolicy.retryable(kind));
    }
    private void unavailable(String message,boolean retryFirst){
        page(title().isEmpty()?"Playback unavailable":title());text(message,22);
        android.widget.Button retry=button("Try again",()->{playWhenReady=true;preparing();prepare();});android.widget.Button back=button("Back to your films",this::finish);
        (retryFirst?retry:back).requestFocus();
    }
    /** Records what the file offers; returns why it can't play when every picture (or every sound) track is beyond this TV, so silence or a black screen is never shown as success. */
    private PlaybackPolicy.Kind record(Tracks tracks){
        List<PlaybackDiagnostics.Track> list=new ArrayList<>();String container="";
        for(Tracks.Group g:tracks.getGroups())for(int i=0;i<g.length;i++){Format f=g.getTrackFormat(i);
            int type=g.getType()==C.TRACK_TYPE_VIDEO?PlaybackDiagnostics.VIDEO:g.getType()==C.TRACK_TYPE_AUDIO?PlaybackDiagnostics.AUDIO:g.getType()==C.TRACK_TYPE_TEXT?PlaybackDiagnostics.TEXT:0;
            if(type==0)continue;if(container.isEmpty()&&f.containerMimeType!=null)container=f.containerMimeType;
            list.add(new PlaybackDiagnostics.Track(type,f.sampleMimeType,f.codecs,f.language,f.width,f.height,f.frameRate,f.channelCount,f.sampleRate,g.isTrackSupported(i),g.isTrackSelected(i)));}
        diagnostics.tracks(container,list);
        return PlaybackPolicy.unplayable(list);
    }
    /** Sound and subtitle choices from the file itself, offered only when there is a real choice. */
    private void chooseTracks(){
        if(player==null)return;
        List<String> labels=new ArrayList<>();List<TrackSelectionParameters> choices=new ArrayList<>();
        TrackSelectionParameters current=player.getTrackSelectionParameters();Locale here=Locale.getDefault();int sounds=0,subtitles=0;
        for(Tracks.Group g:player.getCurrentTracks().getGroups())for(int i=0;i<g.length;i++){
            if(!g.isTrackSupported(i))continue;Format f=g.getTrackFormat(i);String mark=g.isTrackSelected(i)?" (playing)":"";
            if(g.getType()==C.TRACK_TYPE_AUDIO){labels.add("Sound: "+PlaybackDiagnostics.soundLabel(f.language,f.channelCount,sounds++,here)+mark);choices.add(current.buildUpon().setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(),i)).build());}
            else if(g.getType()==C.TRACK_TYPE_TEXT){labels.add("Subtitles: "+PlaybackDiagnostics.subtitleLabel(f.language,subtitles++,here)+mark);choices.add(current.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(),i)).build());}
        }
        if(subtitles>0){labels.add("Subtitles off");choices.add(current.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());}
        if(sounds<2&&subtitles==0){note("This film has one soundtrack and no subtitles the TV can show.");return;}
        new AlertDialog.Builder(this).setTitle("Sound and subtitles").setItems(labels.toArray(new String[0]),(d,which)->{if(player!=null)player.setTrackSelectionParameters(choices.get(which));}).show();
    }
    private boolean playerKey(int code){
        switch(code){
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:case KeyEvent.KEYCODE_MEDIA_PLAY:case KeyEvent.KEYCODE_MEDIA_PAUSE:case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:case KeyEvent.KEYCODE_MEDIA_REWIND:case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:case KeyEvent.KEYCODE_MENU:return true;
            default:return false;
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int code=e.getKeyCode();
        if(player==null||view==null||!playerKey(code))return super.dispatchKeyEvent(e);
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
        boolean first=e.getRepeatCount()==0;
        switch(code){
            case KeyEvent.KEYCODE_MEDIA_PLAY:player.play();break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:player.pause();break;
            case KeyEvent.KEYCODE_MEDIA_STOP:if(first){remember();finish();}return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_MEDIA_REWIND:seek(-1,e.getRepeatCount());break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:seek(1,e.getRepeatCount());break;
            case KeyEvent.KEYCODE_DPAD_DOWN:case KeyEvent.KEYCODE_MENU:if(first)chooseTracks();return true;
            case KeyEvent.KEYCODE_DPAD_UP:break;
            default:if(first){if(player.getPlaybackState()==Player.STATE_ENDED)player.seekTo(0);if(player.getPlayWhenReady())player.pause();else player.play();}break;
        }
        view.showController();return true;
    }
    private void seek(int direction,int repeat){
        long step=PlaybackPolicy.seekStep(repeat);if(step==0)return;
        player.seekTo(PlaybackPolicy.seekTarget(player.getCurrentPosition(),direction*step,player.getDuration()));diagnostics.seek();
    }
    /** Keeps the place in memory for a restart and, for NAS titles, on this TV. Cleared when the film was watched to the end. */
    private void remember(){
        if(player==null)return;
        long at=player.getCurrentPosition(),duration=player.getDuration();if(at>0||position<0)position=Math.max(0,at);
        if(player.getPlaybackState()==Player.STATE_ENDED)position=0;
        if(dlna&&resumeKey!=null){ResumeStore store=resumeStore();store.record(resumeKey,player.getPlaybackState()==Player.STATE_ENDED?Math.max(at,duration):at,duration>0?duration:0,System.currentTimeMillis());ResumeStore.save(this,store);}
    }
    @Override protected void onResume(){super.onResume();if(player!=null&&playWhenReady)player.play();}
    /** Standby and anything covering the player pause it; onResume or a restart continues only if it was playing. */
    @Override protected void onPause(){if(player!=null){playWhenReady=player.getPlayWhenReady();player.pause();}super.onPause();}
    private void report(String path){
        if(dlna||player==null||!started)return;
        long at=player.getCurrentPosition();boolean paused=!player.isPlaying();
        worker.execute(()->{try{LocalApi.post(config,path,new JSONObject().put("ItemId",id).put("PositionTicks",at*10000).put("IsPaused",paused).put("CanSeek",true).put("PlayMethod","DirectStream"));}catch(Exception ignored){/* Do not claim synchronisation succeeded. */}});
    }
    private void release(){handler.removeCallbacks(progress);handler.removeCallbacks(stallCheck);if(player!=null){report("/Sessions/Playing/Stopped");view.setPlayer(null);player.release();player=null;}started=false;}
    @Override protected void onStop(){remember();generation++;release();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putLong("position",player!=null?player.getCurrentPosition():position);out.putBoolean("playWhenReady",playWhenReady);}
    @Override protected void onDestroy(){release();handler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
