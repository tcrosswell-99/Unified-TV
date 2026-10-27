package io.unifiedtv.preview;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.ArrayList;
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
 * Owner, TCL build run 99: a microphone beside the field (and the remote's own voice key on this page) fills the field
 * from speech, through the same route as Ask your TV (the TV's speech service inside the page after the microphone is
 * allowed, else the TV's own voice screen), and plays at once with the "Looking for" choice; "… on <Echo>" and
 * "… everywhere" work for that request only (SpokenMusic). Under the field, "Your playlists" when Amazon Music shares its
 * library, else "Recently played" from what Unified TV has seen play (MusicHistory); hidden when empty.
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
    static final int MIC = 41, SYSTEM_VOICE = 42;
    static final String PROMPT = "Say an artist, album, song or playlist", NO_SPEECH = "This TV has no voice input for apps, so type what you'd like instead.";
    static final int TILE = 180, TILE_ART = 150;
    private EditText field; private Button mic; private LinearLayout shelf;
    /** Amazon Music's shared playlists on this visit (null: not known yet). */
    private List<MusicLibrary.Item> playlists;
    private SpeechRecognizer recogniser; private boolean listening, paneFailed;
    /** Pictures for the shelf: their own thread, so a slow Home Assistant look never holds them up. */
    private final ExecutorService pictures = Executors.newSingleThreadExecutor();

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
            field=search();
            shelf=new LinearLayout(this);shelf.setOrientation(LinearLayout.VERTICAL);shelf.setVisibility(View.GONE);column.addView(shelf,new LinearLayout.LayoutParams(-1,-2));
            Button[] kind=new Button[1];   // changes in place, so the words typed and the focus stay
            kind[0]=row("Looking for: "+focus.words,null,false,()->{focus=focus.next();kind[0].setText("Looking for: "+focus.words);kind[0].setContentDescription("Looking for: "+focus.words);});
            EditText typed=field;button("Play",()->play(typed.getText().toString()));
            field.setOnEditorActionListener((v,action,e)->{if(action==EditorInfo.IME_ACTION_SEARCH||action==EditorInfo.IME_ACTION_DONE||action==EditorInfo.IME_NULL){play(v.getText().toString());return true;}return false;});
        }
        playOnSlot=new LinearLayout(this);playOnSlot.setOrientation(LinearLayout.VERTICAL);column.addView(playOnSlot,new LinearLayout.LayoutParams(-1,-2));
        echoes(mine);
        if(pkg!=null){
            List<String> recent=AmazonMusic.recent(this);
            if(!recent.isEmpty()){group("Recent searches");card();for(String q:recent)row(q,"Play",false,()->play(q));}
            if(!recent.isEmpty()||!MusicHistory.list(this).isEmpty()){if(recent.isEmpty())card();button("Clear recent searches and plays",this::clearRecent);}
            group("Your music");card();
            library=row(MusicLibraryActivity.TITLE,null,true,()->startActivity(new Intent(this,MusicLibraryActivity.class)));
            library.setVisibility(browse!=null&&browse?View.VISIBLE:View.GONE);
            if(browse==null)probe();else shelf();
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
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(f,new LinearLayout.LayoutParams(0,-2,1));
        mic=styled("Speak",this::listen);mic.setGravity(Gravity.CENTER);mic.setContentDescription("Speak: "+PROMPT);
        Drawable glyph=new MicGlyph(px(30));glyph.setBounds(0,0,px(30),px(30));mic.setCompoundDrawablesRelative(glyph,null,null,null);mic.setCompoundDrawablePadding(px(10));
        LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-2,-2);ml.setMarginStart(px(14));line.addView(mic,ml);
        column.addView(line,rowParams());
        // No speech service of any kind on this TV: no microphone, and one line saying why.
        if(!speech()){mic.setVisibility(View.GONE);text(NO_SPEECH,17);}
        return f;
    }

    // --- speaking instead of typing (the same route as Ask your TV)

    /** Whether this TV can turn speech into words for an app: its speech service, or its own voice screen. */
    boolean speech(){
        boolean service;try{service=SpeechRecognizer.isRecognitionAvailable(this);}catch(RuntimeException e){service=false;}
        return service||AmazonMusic.resolves(this,systemIntent());
    }
    private Intent systemIntent(){
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-GB")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,PROMPT)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1).putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
    }
    /** The microphone (or the remote's voice key): inside the page when the TV allows it, else the TV's own voice screen. */
    void listen(){
        if(field==null||mic==null||mic.getVisibility()!=View.VISIBLE)return;
        if(listening){stopListening();return;}
        paneFailed=false;
        boolean service;try{service=SpeechRecognizer.isRecognitionAvailable(this);}catch(RuntimeException e){service=false;}
        // Ask your TV found the pane heard nothing on this TV once: go straight to the TV's own voice screen, as it does.
        if(prefs.getBoolean(VoiceActivity.SYSTEM_FIRST,false)||!service){systemVoice();return;}
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},MIC);return;}
        listenHere();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request!=MIC)return;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)listenHere();else systemVoice();
    }
    private void listenHere(){
        try{
            if(recogniser==null){recogniser=SpeechRecognizer.createSpeechRecognizer(this);recogniser.setRecognitionListener(heard);}
            recogniser.startListening(systemIntent());listening=true;mic.setText("Stop");say("Listening… "+PROMPT);
        }catch(RuntimeException e){listening=false;systemVoice();}
    }
    private void stopListening(){listening=false;if(mic!=null)mic.setText("Speak");if(recogniser!=null)try{recogniser.stopListening();}catch(RuntimeException ignored){}}
    private final RecognitionListener heard=new RecognitionListener(){
        @Override public void onReadyForSpeech(Bundle b){}
        @Override public void onBeginningOfSpeech(){}
        @Override public void onRmsChanged(float db){}
        @Override public void onBufferReceived(byte[] buffer){}
        @Override public void onEndOfSpeech(){if(listening)say("One moment…");}
        @Override public void onError(int error){listening=false;if(mic!=null)mic.setText("Speak");
            if(VoiceActivity.fallBack(error)){paneFailed=true;systemVoice();return;}
            say(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Allow the microphone for Unified TV in the TV's app settings, or type instead.":"Listening isn't working right now. Type instead, or press Speak again.");}
        @Override public void onResults(Bundle b){listening=false;if(mic!=null)mic.setText("Speak");
            ArrayList<String> words=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String w=words==null||words.isEmpty()||words.get(0)==null?"":words.get(0).trim();
            if(w.isEmpty()){paneFailed=true;systemVoice();return;}spoken(w);}
        @Override public void onPartialResults(Bundle b){}
        @Override public void onEvent(int type,Bundle b){}
    };
    /** The TV's own voice screen, which returns here with the words. */
    private void systemVoice(){
        try{startActivityForResult(systemIntent(),SYSTEM_VOICE);say(PROMPT);}catch(RuntimeException e){say(NO_SPEECH);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=SYSTEM_VOICE)return;
        ArrayList<String> got=result==RESULT_OK&&data!=null?data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS):null;
        String w=got==null||got.isEmpty()||got.get(0)==null?"":got.get(0).trim();
        if(w.isEmpty()){say("I didn't catch that. Press Speak to try again, or type instead.");return;}
        // The page heard nothing but the TV's own screen did: on this TV, go straight there next time (as Ask your TV).
        if(paneFailed)prefs.edit().putBoolean(VoiceActivity.SYSTEM_FIRST,true).apply();
        spoken(w);
    }
    /** Words heard: into the field, then played at once with the "Looking for" choice; a place said is for this time only. */
    void spoken(String words){
        String w=NowPlaying.bounded(words,AmazonMusic.QUERY_MAX).replaceAll("[.!?]+$","").trim();
        if(field!=null){field.setText(w);field.setSelection(field.getText().length());}
        VoiceCommand.Music m=VoiceCommand.music("play "+w);
        if(m==null){say("That can't be played from here. Try an artist, album, song or playlist.");return;}
        SpokenMusic.play(this,m,focus,new SpokenMusic.Reply(){
            @Override public void say(String words){MusicActivity.this.say(words);}
            @Override public void done(){MusicActivity.this.say("");}
        });
    }
    /** The remote's own voice or search key on this page does what the microphone does. */
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int k=e.getKeyCode();
        if((k==KeyEvent.KEYCODE_SEARCH||k==KeyEvent.KEYCODE_VOICE_ASSIST)&&!onPlayOn&&mic!=null&&mic.getVisibility()==View.VISIBLE){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)listen();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
    /** A microphone drawn in the sheet's own white; an original drawing. */
    static final class MicGlyph extends Drawable{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF r=new RectF();private final float size;
        MicGlyph(float size){this.size=size;p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(SettingsGlass.TEXT);}
        @Override public void draw(Canvas c){
            float s=getBounds().width()/38f,cx=getBounds().exactCenterX(),cy=getBounds().exactCenterY()-2*s;p.setStrokeWidth(Math.max(1.5f,3*s));
            r.set(cx-7*s,cy-14*s,cx+7*s,cy+6*s);c.drawRoundRect(r,7*s,7*s,p);
            r.set(cx-12*s,cy-4*s,cx+12*s,cy+12*s);c.drawArc(r,0,180,false,p);
            c.drawLine(cx,cy+12*s,cx,cy+18*s,p);c.drawLine(cx-6*s,cy+18*s,cx+6*s,cy+18*s,p);
        }
        @Override public int getIntrinsicWidth(){return Math.round(size);}
        @Override public int getIntrinsicHeight(){return Math.round(size);}
        @Override public void setAlpha(int a){p.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    private void say(String words){if(status==null)return;status.setText(words);status.setVisibility(words==null||words.isEmpty()?View.GONE:View.VISIBLE);}

    /** Play by name on this profile's Play on choice: This TV (AmazonMusic), or an Echo through Home Assistant. */
    void play(String query){play(query,focus);}
    void play(String query,AmazonMusic.Focus focus){
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
        if(Launch.kidsRefusal(this,AmazonMusic.installed(this),AmazonMusic.NAME)!=null){shelf();return;}
        if(probe!=null)probe.close();
        probe=MusicLibraryActivity.library.open(this,new MusicLibrary.Listener(){
            String root,lists;
            @Override public void connected(String r){root=r;if(probe!=null)probe.children(r);}
            @Override public void refused(){AmazonMusic.browsing(MusicActivity.this,false);done(false);}
            @Override public void children(String parent,List<MusicLibrary.Item> items){
                if(gone)return;
                if(parent.equals(root)){
                    boolean any=!items.isEmpty();AmazonMusic.browsing(MusicActivity.this,any);
                    if(!any){done(false);return;}
                    done(true);
                    // Amazon Music's own "Playlists" shelf when it has one; otherwise what its top level offers to play.
                    MusicLibrary.Item node=playlistsNode(items);
                    if(node!=null&&probe!=null){lists=node.id;probe.children(node.id);}else{playlists=playable(items);shelf();}
                }else if(parent.equals(lists)){playlists=playable(items);shelf();}
            }
        });
        if(probe==null){AmazonMusic.browsing(this,false);browse=false;shelf();return;}
        probe.connect();
    }
    /** Shown or hidden "Your Amazon Music"; a refusal closes the connection (an answer keeps it open to play from). */
    private void done(boolean available){browse=available;if(library!=null&&!gone)library.setVisibility(available?View.VISIBLE:View.GONE);
        if(!available){if(probe!=null){probe.close();probe=null;}shelf();}}
    static MusicLibrary.Item playlistsNode(List<MusicLibrary.Item> items){
        for(MusicLibrary.Item i:items)if(i.browsable&&i.title.toLowerCase(Locale.ROOT).contains("playlist"))return i;return null;
    }
    static List<MusicLibrary.Item> playable(List<MusicLibrary.Item> items){
        List<MusicLibrary.Item> out=new ArrayList<>();for(MusicLibrary.Item i:items)if(i.playable&&out.size()<MusicHistory.MAX*2)out.add(i);return out;
    }

    // --- under the field: "Your playlists", else "Recently played"; hidden when empty

    private void shelf(){
        if(shelf==null||gone)return;
        if(Boolean.TRUE.equals(browse)){if(playlists!=null)tiles("Your playlists",playlists.size(),i->{MusicLibrary.Item p=playlists.get(i);
            return new Tile(p.title,p.subtitle,()->{Bitmap b=MusicArt.load(getApplicationContext(),p.icon,p.iconUri,px(TILE_ART),px(TILE_ART));return b;},()->playFromLibrary(p));});
            return;}
        Context app=getApplicationContext();int mine=visit;
        try{pictures.execute(()->{List<MusicHistory.Entry> list=MusicHistory.list(app);runOnUiThread(()->{if(gone||mine!=visit)return;
            tiles("Recently played",list.size(),i->{MusicHistory.Entry e=list.get(i);
                return new Tile(e.name,e.by,()->MusicHistory.picture(app,e,px(TILE_ART)),()->play(e.name,e.focus));});});});}
        catch(RejectedExecutionException ignored){}
    }
    private void playFromLibrary(MusicLibrary.Item p){
        if(probe==null||!probe.play(p.id)){say("Amazon Music didn't accept that. Try it in the Amazon Music app.");return;}
        startActivity(NowPlayingActivity.waiting(this,p.title,AmazonMusic.Focus.PLAYLIST,AmazonMusic.Route.LIBRARY,""));
    }
    /** One tile: its words, how to get its own picture (off the main thread), and what OK does. */
    private static final class Tile{
        final String title,sub;final java.util.concurrent.Callable<Bitmap> art;final Runnable ok;
        Tile(String title,String sub,java.util.concurrent.Callable<Bitmap> art,Runnable ok){this.title=title;this.sub=sub;this.art=art;this.ok=ok;}
    }
    private void tiles(String heading,int count,java.util.function.IntFunction<Tile> make){
        shelf.removeAllViews();
        if(count==0){shelf.setVisibility(View.GONE);return;}
        TextView h=label(heading,18,SettingsGlass.GROUP);h.setPadding(px(12),px(14),px(12),px(6));shelf.addView(h);
        HorizontalScrollView sv=new HorizontalScrollView(this);sv.setHorizontalScrollBarEnabled(false);sv.setFocusable(false);sv.setClipToPadding(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);sv.addView(row);
        int art=px(TILE_ART);
        for(int i=0;i<count;i++){Tile t=make.apply(i);
            Button b=styled(t.title,t.ok);b.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.TOP);b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(20));b.setContentDescription(t.sub.isEmpty()?t.title:t.title+", "+t.sub);
            Drawable blank=new ColorDrawable(0x33FFFFFF);blank.setBounds(0,0,art,art);b.setCompoundDrawablesRelative(null,blank,null,null);b.setCompoundDrawablePadding(px(8));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px(TILE),-2);lp.setMarginEnd(px(12));row.addView(b,lp);
            try{pictures.execute(()->{Bitmap bmp;try{bmp=t.art.call();}catch(Exception e){bmp=null;}if(bmp==null)return;Bitmap got=bmp;
                runOnUiThread(()->{if(gone)return;Drawable d=new BitmapDrawable(getResources(),got);d.setBounds(0,0,art,art);b.setCompoundDrawablesRelative(null,d,null,null);});});}
            catch(RejectedExecutionException ignored){}
        }
        shelf.addView(sv,new LinearLayout.LayoutParams(-1,-2));shelf.setVisibility(View.VISIBLE);
    }
    private void clearRecent(){
        AmazonMusic.forget(this);Context app=getApplicationContext();
        try{pictures.execute(()->{MusicHistory.forget(app);runOnUiThread(()->{if(!gone)main();});});}catch(RejectedExecutionException ignored){main();}
    }

    @Override protected void onDestroy(){gone=true;if(probe!=null)probe.close();if(recogniser!=null)try{recogniser.destroy();}catch(RuntimeException ignored){}
        worker.shutdownNow();pictures.shutdownNow();super.onDestroy();}

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
