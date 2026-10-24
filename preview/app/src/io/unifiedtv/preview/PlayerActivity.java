package io.unifiedtv.preview;

import android.os.*;
import android.view.KeyEvent;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.util.concurrent.*;

/** Embedded direct-play trial. No arbitrary external URLs, tokens in query strings or VLC UI. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Screen {
    private ExoPlayer player; private PlayerView view; private JSONObject config; private String id; private boolean started;
    private int generation;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable progress=new Runnable(){public void run(){report("/Sessions/Playing/Progress");handler.postDelayed(this,10000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);id=getIntent().getStringExtra("item");page("Your Films & TV");text("Preparing playback…",24);}
    @Override protected void onStart(){super.onStart();prepare();}
    private void prepare(){
        final int ticket=++generation;
        worker.execute(()->{
            try{
                config=new Vault(this).read(Vault.mediaKey(this));
                JSONObject me=LocalApi.get(config,"/Users/Me");
                if(!me.optString("Id").equals(config.optString("user")))throw new SecurityException();
                if(EditLock.child(this)&&(me.optJSONObject("Policy")==null||me.optJSONObject("Policy").optBoolean("IsAdministrator")))throw new SecurityException();
                JSONObject item=LocalApi.get(config,"/Users/"+LocalApi.segment(config.optString("user"))+"/Items/"+LocalApi.segment(id));
                long resume=item.optJSONObject("UserData")==null?0:item.optJSONObject("UserData").optLong("PlaybackPositionTicks",0)/10000;
                String stream=config.getString("base")+"/Videos/"+LocalApi.segment(id)+"/stream?static=true";
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||!getLifecycleActive()||ticket!=generation)return;
                    view=new PlayerView(this);setContentView(view);
                    player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(()->new PrivateDataSource(config))).build();
                    view.setPlayer(player);view.setKeepScreenOn(true);view.setUseController(true);view.requestFocus();
                    player.addListener(new Player.Listener(){
                        @Override public void onIsPlayingChanged(boolean playing){if(playing&&!started){started=true;report("/Sessions/Playing");handler.post(progress);}}
                        @Override public void onPlayerError(PlaybackException e){release();page("Playback unavailable");text("This file could not be played. Check the connection or choose another title. Codec, audio and subtitle support still need TV tests.",24);button("Back to library",PlayerActivity.this::finish);}
                    });
                    player.setMediaItem(MediaItem.fromUri(stream));player.seekTo(resume);player.prepare();player.play();
                });
            }catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;page("Playback unavailable");text("Check the library connection and this profile's permissions.",24);button("Back",this::finish);});}
        });
    }
    private boolean foreground; private boolean getLifecycleActive(){return foreground;}
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onPause(){foreground=false;super.onPause();}
    private void report(String path){
        if(player==null||!started)return;
        long position=player.getCurrentPosition();boolean paused=!player.isPlaying();
        worker.execute(()->{try{LocalApi.post(config,path,new JSONObject().put("ItemId",id).put("PositionTicks",position*10000).put("IsPaused",paused).put("CanSeek",true).put("PlayMethod","DirectStream"));}catch(Exception ignored){/* Do not claim synchronisation succeeded. */}});
    }
    private void release(){handler.removeCallbacks(progress);if(player!=null){report("/Sessions/Playing/Stopped");view.setPlayer(null);player.release();player=null;}started=false;}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(view!=null&&view.dispatchKeyEvent(e))return true;return super.dispatchKeyEvent(e);}
    @Override protected void onStop(){generation++;release();super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
