package io.unifiedtv.preview;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;
import java.util.Locale;

/**
 * Uses the official installed app. No simulated account playlists or unsupported playback API. When the owner has let
 * Unified TV see media notifications and something is playing, Music opens Now Playing, which shows and controls only
 * what that app reports to Android (spec 11, Music).
 */
public final class MusicActivity extends Screen {
    @Override public void onCreate(Bundle b){super.onCreate(b);
        if(MediaWatch.best(this)!=null){startActivity(new Intent(this,NowPlayingActivity.class));finish();return;}
        page("Music");text("Your music, playlists and search",24);
        Intent launch=find(this);
        if(launch!=null){text("Listen with Amazon Music. Account playlists, search and playback open in its app.",20);button("Open Amazon Music",()->open(this));}
        else{text("Amazon Music is not installed on this TV. Check its availability in your TV's app store.",20);button("Find Amazon Music",()->open(this));}
        button("Now playing",()->startActivity(new Intent(this,NowPlayingActivity.class)));
        text("Unified TV's own account playlists and player are awaiting authorised Amazon integration.",17);button("Back",this::finish);
    }
    /** The official Amazon Music app's launch intent, found by package or its own name; null when it isn't installed. */
    static Intent find(Context c){PackageManager pm=c.getPackageManager();for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0)){
        String pkg=ri.activityInfo.packageName;String name=ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
        if(pkg.equals("com.amazon.mp3")||pkg.equals("com.amazon.music.tv")||name.equals("amazon music"))return pm.getLeanbackLaunchIntentForPackage(pkg);
    }return null;}
    /** Opens Amazon Music, or the TV's app store search for it when it isn't installed. */
    static void open(Activity a){
        Intent launch=find(a);
        // Kids: Amazon Music only when an adult approved it, and never the app store (Launch is the one gate).
        String no=Launch.kidsRefusal(a,launch==null||launch.getComponent()==null?"":launch.getComponent().getPackageName(),launch==null?"The app store":"Amazon Music");
        if(no!=null){toast(a,no);return;}
        if(launch!=null){try{a.startActivity(launch);}catch(RuntimeException e){toast(a,"Amazon Music could not be opened");}}
        else{try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q=Amazon%20Music")));}catch(RuntimeException e){toast(a,"Open the TV's app store to find Amazon Music");}}
    }
    private static void toast(Activity a,String s){android.widget.Toast.makeText(a,s,android.widget.Toast.LENGTH_LONG).show();}
}
