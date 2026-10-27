package io.unifiedtv.preview;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Music (OD-27, owner, 27 October 2026): the real Amazon Music app controlled from inside Unified TV, with Android's
 * official media APIs only (AmazonMusic). Play by name (a search field with the on-screen keyboard, what kind of thing,
 * and this profile's recent searches), "Play on" (This TV, each Echo, Everywhere: Echoes, through Home Assistant), "Your
 * Amazon Music" when Amazon Music shares its library (MusicLibraryActivity), Now Playing, and the app itself. Without
 * notification access it explains the one step to allow it, and play by name still works through Android's request.
 *
 * OD-19: this page shows no music of its own (no artwork or playback); it only chooses what to play and where. A choice
 * page like that is the glass sidebar over a still Home, never a full-screen page. Now Playing and the Amazon Music app
 * stay full screen. Opened with {@link #PLAY_ON}, it shows only the Play on choice (from Now Playing).
 */
public final class MusicActivity extends GlassScreen {
    static final String PLAY_ON = "music.playOnOnly";
    private AmazonMusic.Focus focus = AmazonMusic.Focus.ANY;
    private TextView status; private Button library; private LinearLayout playOnSlot;
    private MusicLibrary probe;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean gone;
    private int visit;private boolean onPlayOn;
    /** Whether Amazon Music shared its library on this visit (null: not known yet); asked once per visit. */
    private Boolean browse;

    @Override public void onCreate(Bundle b){super.onCreate(b);
        if(getIntent().getBooleanExtra(PLAY_ON,false)){playOn();return;}
        // Something is already playing on this TV: Now Playing, as before.
        MusicSessions.Source src=MusicSessions.source;
        if(!Echoes.onEcho(prefs)&&src.granted(this)&&MusicSessions.best(this,src.active(this))!=null){startActivity(new Intent(this,NowPlayingActivity.class));finish();return;}
        main();
    }

    private void main(){
        int mine=++visit;onPlayOn=false;
        page("Music","Amazon Music, played and controlled from here");
        String pkg=AmazonMusic.installed(this);
        card();
        status=text("",17);status.setVisibility(View.GONE);
        if(pkg==null){text("Amazon Music is not installed on this TV. Check its availability in your TV's app store.",20);button("Find Amazon Music",()->open(this));}
        else{
            text("Play by name",20);
            EditText field=search();
            Button[] kind=new Button[1];   // changes in place, so the words typed and the focus stay
            kind[0]=row("Looking for: "+focus.words,null,false,()->{focus=focus.next();kind[0].setText("Looking for: "+focus.words);kind[0].setContentDescription("Looking for: "+focus.words);});
            button("Play",()->play(field.getText().toString()));
            field.setOnEditorActionListener((v,action,e)->{if(action==EditorInfo.IME_ACTION_SEARCH||action==EditorInfo.IME_ACTION_DONE||action==EditorInfo.IME_NULL){play(v.getText().toString());return true;}return false;});
        }
        playOnSlot=new LinearLayout(this);playOnSlot.setOrientation(LinearLayout.VERTICAL);column.addView(playOnSlot,new LinearLayout.LayoutParams(-1,-2));
        echoes(mine);
        if(pkg!=null){
            List<String> recent=AmazonMusic.recent(this);
            if(!recent.isEmpty()){group("Recent searches");card();
                for(String q:recent)row(q,"Play",false,()->play(q));
                button("Clear recent searches",()->{AmazonMusic.forget(this);main();});}
            group("Your music");card();
            library=row(MusicLibraryActivity.TITLE,null,true,()->startActivity(new Intent(this,MusicLibraryActivity.class)));
            library.setVisibility(browse!=null&&browse?View.VISIBLE:View.GONE);
            if(browse==null)probe();
        }else{group("Your music");card();}
        button("Now playing",()->startActivity(new Intent(this,NowPlayingActivity.class)));
        if(pkg!=null)button("Open Amazon Music app",()->open(this));
        if(!MusicSessions.source.granted(this)){group("See what's playing");card();
            text("To see and control what's playing here, allow Unified TV to see media notifications once. Play by name works either way.",17);
            button("Allow in Settings",()->{if(!MediaWatch.openAccessSettings(this))startActivity(new Intent(this,NowPlayingActivity.class));});}
    }

    /** The search field: plain text, the TV's own on-screen keyboard, nothing remembered by Android's autofill. */
    private EditText search(){
        EditText f=new EditText(this);f.setSingleLine(true);f.setHint("Artist, album, song or playlist");f.setTextColor(SettingsGlass.TEXT);f.setHintTextColor(SettingsGlass.GROUP);
        f.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(26));f.setImeOptions(EditorInfo.IME_ACTION_SEARCH);f.setContentDescription("Play by name: type what you'd like to hear");
        f.setSaveEnabled(false);f.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        f.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0x33FFFFFF);bg.setCornerRadius(px(14));bg.setStroke(Math.max(1,px(2)),0x80FFFFFF);f.setBackground(bg);f.setPadding(px(24),px(14),px(24),px(14));
        f.setFocusable(true);f.setFocusableInTouchMode(true);
        column.addView(f,rowParams());return f;
    }

    private void say(String words){if(status==null)return;status.setText(words);status.setVisibility(words==null||words.isEmpty()?View.GONE:View.VISIBLE);}

    /** Play by name on this profile's Play on choice: This TV (AmazonMusic), or an Echo through Home Assistant. */
    void play(String query){
        String q=AmazonMusic.clean(query);if(q.isEmpty()){say("Type what you'd like to hear, then press Play.");return;}
        String target=Echoes.target(prefs);
        if(Echoes.TV.equals(target)){AmazonMusic.Result r=AmazonMusic.play(this,q,focus);say(r.words);return;}
        say("Asking "+Echoes.targetName(prefs)+" for “"+q+"”…");Context app=getApplicationContext();
        try{worker.execute(()->{String no=Echoes.play(app,Echoes.ha(app),target,q);
            runOnUiThread(()->{if(gone)return;if(no==null){say("");startActivity(NowPlayingActivity.waiting(this,q,focus,AmazonMusic.Route.ECHO,""));}else say(no);});});}
        catch(RejectedExecutionException ignored){}
    }

    // --- Play on

    /** On the main page: "Play on: …" once Echos are found and allowed; otherwise one plain line. */
    private void echoes(int mine){
        Context app=getApplicationContext();
        try{worker.execute(()->{Echoes.Found f=Echoes.finder.find(app);runOnUiThread(()->{if(gone||mine!=visit||playOnSlot==null)return;
            LinearLayout keep=column;column=playOnSlot;playOnSlot.removeAllViews();
            if(f.ok()&&Echoes.allowed(prefs))row("Play on: "+Echoes.targetName(prefs),null,true,this::playOn);
            else if(f.ok())text(Echoes.KIDS,17);
            else text(Echoes.SETUP_LINE,17);
            column=keep;});});}
        catch(RejectedExecutionException ignored){}
    }

    /** Where music plays: This TV, Everywhere, or one Echo; remembered for this profile. */
    private void playOn(){
        int mine=++visit;onPlayOn=true;
        page("Play on","Where your music plays");card();
        String now=Echoes.target(prefs);
        row("This TV",Echoes.TV.equals(now)?"Chosen":null,false,()->chosen(Echoes.TV,""));
        TextView looking=busy("Looking for your Echo speakers…");
        LinearLayout slot=column;Context app=getApplicationContext();
        try{worker.execute(()->{Echoes.Found f=Echoes.finder.find(app);runOnUiThread(()->{if(gone||mine!=visit)return;
            ((View)looking.getParent()).setVisibility(View.GONE);column=slot;
            if(!f.ok()){text(Echoes.SETUP_LINE,17);if(f.problem!=null&&!Echoes.NOT_SET_UP.equals(f.problem))text(f.problem+".",17);return;}
            if(!Echoes.allowed(prefs)){text(Echoes.KIDS,17);return;}
            row("Everywhere",Echoes.EVERYWHERE.equals(now)?"Chosen":null,false,()->chosen(Echoes.EVERYWHERE,"Everywhere"));
            for(Echoes.Echo e:f.echoes){if(e.group&&Echoes.group(f.echoes)==e)continue;
                row(e.name,e.id.equals(now)?"Chosen":e.available?null:"Not answering",false,()->{if(e.available)chosen(e.id,e.name);else note(e.name+" isn't answering right now.");});}
            text("Echo speakers play through Home Assistant's Alexa Media Player, which you set up there. Unified TV never sees your Amazon sign-in.",16);
            if(!Profiles.kids(prefs)){boolean on=prefs.getBoolean(Echoes.KIDS_PREF,false);
                row("Echo speakers in Kids",on?"On":"Off",false,()->EditLock.ask(this,()->{prefs.edit().putBoolean(Echoes.KIDS_PREF,!on).apply();playOn();}));}
        });});}
        catch(RejectedExecutionException ignored){}
    }
    private void chosen(String target,String name){
        Echoes.choose(prefs,target,name);
        if(getIntent().getBooleanExtra(PLAY_ON,false)){finish();return;}
        main();
    }
    @Override public void onBackPressed(){
        if(onPlayOn&&!getIntent().getBooleanExtra(PLAY_ON,false)){main();return;}
        super.onBackPressed();
    }

    // --- "Your Amazon Music": shown only when Amazon Music agrees to share its library

    private void probe(){
        if(Launch.kidsRefusal(this,AmazonMusic.installed(this),AmazonMusic.NAME)!=null)return;
        if(probe!=null)probe.close();
        probe=MusicLibraryActivity.library.open(this,new MusicLibrary.Listener(){
            String root;
            @Override public void connected(String r){root=r;if(probe!=null)probe.children(r);}
            @Override public void refused(){AmazonMusic.browsing(MusicActivity.this,false);done(false);}
            @Override public void children(String parent,List<MusicLibrary.Item> items){if(!parent.equals(root))return;boolean any=!items.isEmpty();AmazonMusic.browsing(MusicActivity.this,any);done(any);}
        });
        if(probe==null){AmazonMusic.browsing(this,false);browse=false;return;}
        probe.connect();
    }
    private void done(boolean available){browse=available;if(library!=null&&!gone)library.setVisibility(available?View.VISIBLE:View.GONE);if(probe!=null){probe.close();probe=null;}}

    @Override protected void onDestroy(){gone=true;if(probe!=null)probe.close();worker.shutdownNow();super.onDestroy();}

    // --- the app itself

    /** The official Amazon Music app's launch intent, found by package or its own name; null when it isn't installed. */
    static Intent find(Context c){PackageManager pm=c.getPackageManager();for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0)){
        String pkg=ri.activityInfo.packageName;String name=ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
        if(NowPlaying.amazon(pkg)||name.equals("amazon music"))return pm.getLeanbackLaunchIntentForPackage(pkg);
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
