package io.unifiedtv.preview;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;
import java.util.Locale;

/** Uses the official installed app. No simulated account playlists or unsupported playback API. */
public final class MusicActivity extends Screen {
    @Override public void onCreate(Bundle b){super.onCreate(b);page("Music");text("Your music, playlists and search",24);
        Intent launch=find();
        if(launch!=null){text("Listen with Amazon Music. Account playlists, search and playback open in its app.",20);button("Open Amazon Music",()->{try{startActivity(launch);}catch(RuntimeException e){note("Amazon Music could not be opened");}});}
        else{text("Amazon Music is not installed on this TV. Check its availability in your TV's app store.",20);button("Find Amazon Music",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q=Amazon%20Music")));}catch(RuntimeException e){note("Open the TV's app store to find Amazon Music");}});}
        text("Unified TV's own account playlists and player are awaiting authorised Amazon integration.",17);button("Back",this::finish);
    }
    private Intent find(){PackageManager pm=getPackageManager();for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0)){
        String pkg=ri.activityInfo.packageName;String name=ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
        if(pkg.equals("com.amazon.mp3")||pkg.equals("com.amazon.music.tv")||name.equals("amazon music"))return pm.getLeanbackLaunchIntentForPackage(pkg);
    }return null;}
}
