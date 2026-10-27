package io.unifiedtv.preview;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Your Films & TV: poster rows over the highlighted title's own background (AGENTS rule 4), built from the saved list of
 * the chosen folders (LibraryIndex) so it opens at once and works while the NAS sleeps.
 *
 * <pre>Search · Your folders
 * [title, year, summary of the highlighted tile]
 * Continue Watching · Recently added · Films · TV · Kids (Adults only)   each row ends with "All"
 * OK: a film or episode plays; a programme opens its seasons; "All" opens the whole row as a grid.
 * Hold OK on a tile: Add to (or Remove from) Favourites; for Adults also "Wrong picture?" (SET-034, PictureFixActivity).</pre>
 *
 * Loading always says so: the first list shows a spinner and how many titles have been found; a later refresh shows a
 * small "Checking for new titles" while the saved list stays usable. Focus never waits for the network: pictures arrive
 * when ready (Posters), and a tile without one is a clean title card. With no TMDB key, Adults see one line saying where
 * to add it (Posters.HINT); a key added meanwhile (phone setup) is picked up when this screen comes back.
 *
 * Profiles (owner, 25 October 2026): Kids see only titles in folders marked for Kids, within the Kids ratings; with no
 * Kids folders they see "Ask an adult". Adults see everything, with a Kids row. The player checks Kids again at play time.
 * Setup (choosing folders) is the glass sheet LibrarySetupActivity; old "setup" requests are passed to it.
 */
public final class DlnaLibraryActivity extends Screen {
    /** Longest a folder may load before Retry is offered (also used by the Wallpaper's folder chooser). */
    static final long LOAD_LIMIT_MS=25_000;
    static final int GRID_CHUNK=84;
    enum Kind{ROWS,GRID,SHOW,SEARCH}
    private static final class Place{final Kind kind;final String title,rowId,showKey;String query="",focus="";int shown=GRID_CHUNK;
        Place(Kind kind,String title,String rowId,String showKey){this.kind=kind;this.title=title;this.rowId=rowId;this.showKey=showKey;}}

    private final ExecutorService walker=Executors.newSingleThreadExecutor(),quick=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LibraryFolders.Library lib;private LibraryCatalogue.Catalogue cat;private Posters posters;private String udn="";
    private float unit;private boolean light,calm,postersOn,postersKnown;private int generation;
    private ImageView[] backs=new ImageView[2];private int front=0;private String shownBack="",focusKey="",wantedBack="";
    private TextView heroTitle,heroMeta,heroText,status,keyHint;private ProgressBar statusSpin;
    private FrameLayout area;private LinearLayout bar;
    private final ArrayDeque<Place> stack=new ArrayDeque<>();
    private boolean refreshing,firstLoad;
    /** While the first list is made (perhaps by Home's row, already walking), the count follows the shared walk. */
    private final Runnable countLater=new Runnable(){public void run(){if(!firstLoad)return;int n=LibraryIndex.walking;if(n>=0)loading(n);handler.postDelayed(this,500);}};
    private final Runnable backdropLater=()->{LibraryCatalogue.Card c=cat==null?null:findCard(wantedBack);if(c!=null)backdrop(c);};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);autoFocusButtons=false;DlnaClient.lan=LocalDiscovery.lan(this);
        boolean kids=Profiles.kids(this);Intent in=getIntent();
        // Choosing folders is the glass sheet now; Kids never reach it.
        if(!kids&&(in.getBooleanExtra("setup",false)||in.getBooleanExtra("kidsSetup",false))){setup();finish();overridePendingTransition(0,0);return;}
        android.util.DisplayMetrics m=getResources().getDisplayMetrics();unit=Math.max(.5f,Math.min(m.widthPixels/1920f,m.heightPixels/1080f));
        android.content.SharedPreferences prefs=getSharedPreferences(HouseholdSettings.PREFS,0);
        calm=prefs.getBoolean("reduceMotion",false)||!android.animation.ValueAnimator.areAnimatorsEnabled();
        light=prefs.getBoolean("lightweight",false)||((android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE)).isLowRamDevice();
        posters=Posters.get(this);   // whether there is a key is read off the main thread (onResume), never per focus move
        lib=LibraryFolders.library(this);udn=lib.udn();
        frame();keyHint();
        if(kids&&!lib.ready){message(KidsPolicy.ASK_ADULT,"An adult chooses the folders for Kids in the Adults profile.",null,null);return;}
        if(!lib.ready){message("Your Films & TV isn't set up yet.","Unified TV finds the media server on your home network. Choose the folders that hold your films and programmes.","Set up Your Films & TV",this::setup);return;}
        String search=in.getStringExtra("search");
        stack.push(new Place(Kind.ROWS,"",null,null));
        // Home's row opens a programme straight at its seasons; Back then shows the rows.
        String showKey=in.getStringExtra("show");if(showKey!=null&&!showKey.isEmpty())stack.push(new Place(Kind.SHOW,"",null,showKey));
        if(search!=null&&!search.trim().isEmpty()){Place p=new Place(Kind.SEARCH,"Search",null,null);p.query=search.trim();stack.push(p);}
        load();
    }
    @Override protected void onRestart(){super.onRestart();if(cat!=null&&lib!=null&&lib.ready)rebuild(false);}   // Continue Watching may have changed
    /** Back from "Wrong picture?" (a sidebar: this screen was only paused): the tiles and background ask for the chosen picture. */
    @Override protected void onResume(){super.onResume();
        if(fixing!=null&&cat!=null){fixing=null;handler.removeCallbacks(backdropLater);toBack(null,"");refreshPage();return;}
        // A key added (or changed) while this screen was away, perhaps by phone setup: every tile asks again. The key is in
        // the Keystore-backed Vault, so it is read on the worker, never on the main thread.
        if(posters==null||lib==null)return;Posters p=posters;
        try{quick.execute(()->{boolean changed=p.refreshKey(),on=p.usable();handler.post(()->keyRead(changed,on));});}catch(RuntimeException ignored){/* closing */}
    }
    private void keyRead(boolean changed,boolean on){
        if(isFinishing()||isDestroyed())return;boolean first=!postersKnown;postersKnown=true;
        if(!changed&&!first&&on==postersOn)return;
        postersOn=on;keyHint();
        if(cat!=null&&(changed||!first)){handler.removeCallbacks(backdropLater);toBack(null,"");refreshPage();}
    }
    /** The one-line "add your TMDB key" hint: Adults only (Kids can't add one), and only while there is no key. */
    private void keyHint(){if(keyHint!=null)keyHint.setVisibility(postersKnown&&!postersOn&&lib!=null&&lib.ready&&!lib.kids?View.VISIBLE:View.GONE);}
    /** For tests: whether the no-key hint is showing. */
    boolean keyHintShown(){return keyHint!=null&&keyHint.getVisibility()==View.VISIBLE;}
    /** The tile whose picture is being corrected, while "Wrong picture?" is open. */
    private String fixing;
    /** The focused view straight from the window's views (as SettingsActivity reads it), so a refresh keeps the same tile. */
    @Override public View getCurrentFocus(){View decor=getWindow().peekDecorView();return decor==null?null:decor.findFocus();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);walker.shutdownNow();quick.shutdownNow();super.onDestroy();}
    private void setup(){try{startActivity(new Intent(this,LibrarySetupActivity.class));overridePendingTransition(0,0);}catch(RuntimeException e){note("Unable to open Films & TV setup");}}
    private int px(float at1080){return Math.round(at1080*unit);}
    private TextView words(String s,float size,int colour,boolean bold,int lines){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(colour);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*unit);t.setMaxLines(lines);t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if(bold)t.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD));t.setFocusable(false);return t;
    }

    // --- the frame: background, hero text, top bar, page area
    private void frame(){
        FrameLayout root=new FrameLayout(this);root.setBackground(MaterialScene.backdrop(this));
        for(int i=0;i<2;i++){ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setAlpha(0f);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);backs[i]=v;root.addView(v,new FrameLayout.LayoutParams(-1,-1));}
        // A scrim so the words stay readable over any picture: darker on the left and along the bottom.
        View scrim=new View(this);GradientDrawable left=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xE6060A12,0x99060A12,0x33060A12});
        scrim.setBackground(left);root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        View foot=new View(this);foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00060A12,0xCC060A12,0xF2060A12}));
        root.addView(foot,new FrameLayout.LayoutParams(-1,px(640),Gravity.BOTTOM));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(px(96),px(48),px(64),0);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);top.addView(bar,new LinearLayout.LayoutParams(0,-2,1));
        statusSpin=new ProgressBar(this,null,android.R.attr.progressBarStyleSmall);statusSpin.setIndeterminate(true);statusSpin.setVisibility(View.GONE);statusSpin.setContentDescription("Loading");
        top.addView(statusSpin,new LinearLayout.LayoutParams(px(30),px(30)));
        status=words("",22,0xFFC3CEDD,false,1);status.setPadding(px(12),0,0,0);top.addView(status);
        content.addView(top,new LinearLayout.LayoutParams(-1,px(64)));
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(0,px(18),0,px(12));
        heroTitle=words("Your Films & TV",48,0xFFF4F7FB,true,1);heroMeta=words("",24,0xFFC3CEDD,false,1);heroText=words("",22,0xFFDCE4EE,false,3);
        heroMeta.setPadding(0,px(6),0,px(8));hero.addView(heroTitle);hero.addView(heroMeta);hero.addView(heroText,new LinearLayout.LayoutParams(px(1000),-2));
        content.addView(hero,new LinearLayout.LayoutParams(-1,px(250)));
        keyHint=words(Posters.HINT,22,0xFFC3CEDD,false,1);keyHint.setPadding(px(6),0,0,px(6));keyHint.setVisibility(View.GONE);content.addView(keyHint);
        area=new FrameLayout(this);content.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);column=null;
    }
    private Button pill(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,24*unit);b.setTextColor(SettingsGlass.textColours());
        b.setBackground(SettingsGlass.row(px(28),Math.max(2,px(3)),false,calm?0:120));b.setStateListAnimator(null);b.setPadding(px(28),px(8),px(28),px(8));b.setMinHeight(px(52));b.setMinimumHeight(px(52));
        b.setFocusableInTouchMode(true);b.setOnClickListener(v->action.run());b.setOnFocusChangeListener((v,f)->{if(f)hero(null);});return b;
    }
    private void topBar(){
        bar.removeAllViews();if(lib==null||!lib.ready)return;
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginEnd(px(16));
        bar.addView(pill("Search",()->{Place p=new Place(Kind.SEARCH,"Search",null,null);rememberFocus();stack.push(p);show();}),lp);
        if(!lib.kids)bar.addView(pill("Your folders",()->EditLock.ask(this,this::setup)),new LinearLayout.LayoutParams(lp));
    }
    /** A page with a message and at most one action (not set up, Ask an adult, nothing found). */
    private void message(String title,String detail,String action,Runnable run){
        area.removeAllViews();heroTitle.setText("Your Films & TV");heroMeta.setText("");heroText.setText("");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.addView(words(title,34,0xFFF4F7FB,true,3));TextView d=words(detail,24,0xFFC3CEDD,false,4);d.setPadding(0,px(10),0,px(24));box.addView(d,new LinearLayout.LayoutParams(px(1100),-2));
        Button first=null;
        if(action!=null){first=pill(action,run);box.addView(first,new LinearLayout.LayoutParams(-2,-2));}
        Button back=pill("Back",this::finish);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=px(12);box.addView(back,lp);
        area.addView(box);(first!=null?first:back).requestFocus();
    }
    private void setStatus(boolean spinning,String text){statusSpin.setVisibility(spinning?View.VISIBLE:View.GONE);status.setText(text==null?"":text);}

    // --- loading
    private void load(){
        int mine=++generation;
        // Cached list first (no network): rows appear at once when there is one.
        quick.execute(()->{
            LibraryIndex.Index index=LibraryIndex.cached(getApplicationContext(),lib);
            LibraryCatalogue.Catalogue built=index.size()>0?build(index):null;
            handler.post(()->{if(mine!=generation||isFinishing())return;
                if(built!=null){cat=built;topBar();show();}
                else{firstLoad=true;loading(0);handler.postDelayed(countLater,500);}
                if(built==null||LibraryIndex.due(index,lib,System.currentTimeMillis()))refresh(built==null);
            });
        });
    }
    private LibraryCatalogue.Catalogue build(LibraryIndex.Index index){
        return LibraryCatalogue.build(index,lib,HouseholdSettings.scheme(getApplicationContext()),ResumeStore.load(getApplicationContext()),
            Favourites.load(getSharedPreferences(HouseholdSettings.PREFS,0)),System.currentTimeMillis());
    }
    /** The first time: a spinner and a count, never a blank screen. */
    private void loading(int found){
        if(!firstLoad)return;
        TextView count=area.findViewWithTag("count");
        if(count==null){area.removeAllViews();heroTitle.setText("Your Films & TV");heroMeta.setText("");heroText.setText("");
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER_HORIZONTAL);
            ProgressBar spin=new ProgressBar(this,null,android.R.attr.progressBarStyleLarge);spin.setIndeterminate(true);spin.setContentDescription("Loading");spin.setTag("spinner");
            box.addView(spin,new LinearLayout.LayoutParams(px(96),px(96)));
            TextView t=words("Finding your films and TV…",32,0xFFF4F7FB,true,2);t.setGravity(Gravity.CENTER);t.setPadding(0,px(24),0,px(8));box.addView(t);
            count=words("",24,0xFFC3CEDD,false,2);count.setGravity(Gravity.CENTER);count.setTag("count");box.addView(count);
            TextView once=words("This happens once. Next time your library opens straight away.",22,0xFFC3CEDD,false,2);once.setGravity(Gravity.CENTER);once.setPadding(0,px(8),0,0);box.addView(once);
            Button back=pill("Back",this::finish);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=px(28);box.addView(back,lp);
            FrameLayout.LayoutParams at=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL);at.topMargin=px(40);area.addView(box,at);back.requestFocus();}
        count.setText(found<=0?"Reading your folders":found==1?"1 video found so far":found+" videos found so far");
    }
    private void refresh(boolean first){
        if(refreshing)return;refreshing=true;int mine=generation;
        if(!first)setStatus(true,"Checking for new titles…");
        walker.execute(()->{
            LibraryIndex.Index index=null;Exception failure=null;
            try{index=LibraryIndex.refresh(getApplicationContext(),lib,false,(titles,folders)->handler.post(()->{if(mine==generation)loading(titles);}));}
            catch(Exception e){failure=e;}
            LibraryCatalogue.Catalogue built=index!=null?build(index):null;
            LibraryIndex.Index got=index;Exception problem=failure;
            handler.post(()->{if(mine!=generation||isFinishing())return;refreshing=false;
                if(built==null){setStatus(false,"");
                    if(cat==null){firstLoad=false;int kind=problem instanceof DlnaClient.Failure?((DlnaClient.Failure)problem).kind:DlnaClient.Failure.UNREACHABLE;
                        message("Your films and TV aren't answering.",DlnaClient.Failure.plain(kind),"Try again",()->{firstLoad=true;loading(0);refresh(true);});}
                    else setStatus(false,"Showing your saved list. The media server isn't answering.");
                    return;}
                setStatus(false,got.stale?"Showing your saved list. Some folders didn't answer.":"");
                boolean wasEmpty=cat==null;LibraryCatalogue.Catalogue old=cat;cat=built;firstLoad=false;
                if(wasEmpty){topBar();show();}
                else if(changed(old,built))refreshPage();
            });
        });
    }
    private static boolean changed(LibraryCatalogue.Catalogue a,LibraryCatalogue.Catalogue b){
        if(a.titles.size()!=b.titles.size()||a.rows.size()!=b.rows.size())return true;
        for(int i=0;i<a.titles.size();i++)if(!a.titles.get(i).stableId.equals(b.titles.get(i).stableId))return true;return false;
    }
    /** Continue Watching after the player, or a refreshed list: same page, same tile in focus. */
    private void rebuild(boolean force){
        int mine=generation;
        quick.execute(()->{LibraryCatalogue.Catalogue built=build(LibraryIndex.cached(getApplicationContext(),lib));
            handler.post(()->{if(mine!=generation||isFinishing()||built.isEmpty())return;cat=built;refreshPage();});});
    }
    private void refreshPage(){View f=getCurrentFocus();if(f instanceof PosterView&&((PosterView)f).card!=null&&!stack.isEmpty())stack.peek().focus=((PosterView)f).card.key();show();}
    private LibraryCatalogue.Card findCard(String key){
        if(cat==null||key==null||key.isEmpty())return null;
        for(LibraryCatalogue.Row r:cat.rows)for(LibraryCatalogue.Card c:r.all)if(c.key().equals(key))return c;
        return cat.find(key);
    }

    // --- pages
    private void show(){
        if(cat==null||stack.isEmpty())return;Place p=stack.peek();area.removeAllViews();
        switch(p.kind){
            case ROWS:rows(p);break;
            case GRID:{LibraryCatalogue.Row r=cat.row(p.rowId);if(r==null){stack.pop();show();return;}grid(p,r.label,r.all);break;}
            case SHOW:{LibraryCatalogue.Show s=cat.shows.get(p.showKey);if(s==null){stack.pop();show();return;}season(p,s);break;}
            case SEARCH:search(p);break;
        }
    }
    private void rows(Place p){
        if(cat.isEmpty()){message(lib.kids?"Nothing in the Kids folders yet.":"No videos found in your folders yet.",lib.kids?"Ask an adult to add some.":"Check that the folders you chose hold your films and programmes.",lib.kids?null:"Your folders",lib.kids?null:(Runnable)this::setup);return;}
        ScrollView sv=new ScrollView(this);sv.setFocusable(false);sv.setVerticalScrollBarEnabled(false);sv.setClipToPadding(false);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,0,0,px(80));sv.addView(list);
        PosterView again=null,first=null;
        for(LibraryCatalogue.Row r:cat.rows){
            List<LibraryCatalogue.Card> cards=new ArrayList<>(r.shown());
            TextView label=words(r.label+(r.all.size()>3&&!r.id.equals("continue")?"  ·  "+r.all.size():""),28,0xFFF4F7FB,true,1);label.setPadding(px(6),px(14),0,px(6));list.addView(label);
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setFocusable(false);hs.setClipToPadding(false);hs.setPadding(px(8),px(8),px(80),px(8));
            LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);hs.addView(line);
            for(int i=0;i<cards.size();i++){PosterView v=tile(cards.get(i),cards,i);line.addView(v,tileParams());if(first==null)first=v;if(cards.get(i).key().equals(p.focus))again=v;}
            if(!r.id.equals("continue")&&r.all.size()>3){PosterView all=action("All "+r.label.toLowerCase(Locale.ROOT).replace("tv","TV"),r.all.size()==1?"1 title":r.all.size()+" titles",()->{rememberFocus();stack.push(new Place(Kind.GRID,r.label,r.id,null));show();});line.addView(all,tileParams());}
            list.addView(hs);
        }
        area.addView(sv);
        PosterView target=again!=null?again:first;p.focus="";if(target!=null){PosterView t=target;t.requestFocus();handler.post(t::requestFocus);}
    }
    private LinearLayout.LayoutParams tileParams(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px(208),px(312));lp.setMarginEnd(px(20));return lp;}
    private void grid(Place p,String title,List<LibraryCatalogue.Card> all){
        heading(title,all.size()==1?"1 title, A to Z":all.size()+" titles, A to Z");
        List<LibraryCatalogue.Card> sorted=new ArrayList<>(all);if(!"continue".equals(p.rowId))sorted.sort(LibraryCatalogue.A_TO_Z);
        cards(p,sorted,null);
    }
    private void season(Place p,LibraryCatalogue.Show s){
        heading(s.name,(s.year.isEmpty()?"":s.year+"  ·  ")+(s.episodes.size()==1?"1 episode":s.episodes.size()+" episodes"));
        ScrollView sv=new ScrollView(this);sv.setFocusable(false);sv.setVerticalScrollBarEnabled(false);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,0,0,px(80));sv.addView(list);
        PosterView again=null,first=null;
        for(int season:s.seasons()){
            List<LibraryCatalogue.Card> cards=new ArrayList<>();for(LibraryCatalogue.Title t:s.episodes)if(t.season==season)cards.add(new LibraryCatalogue.Card(t));
            list.addView(words(season<0?"Episodes":season==0?"Specials":"Season "+season,28,0xFFF4F7FB,true,1));
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setFocusable(false);hs.setClipToPadding(false);hs.setPadding(px(8),px(8),px(80),px(8));
            LinearLayout line=new LinearLayout(this);hs.addView(line);
            for(int i=0;i<cards.size();i++){PosterView v=tile(cards.get(i),cards,i);line.addView(v,tileParams());if(first==null)first=v;if(cards.get(i).key().equals(p.focus))again=v;}
            list.addView(hs);
        }
        area.addView(sv);PosterView target=again!=null?again:first;p.focus="";if(target!=null){PosterView t=target;t.requestFocus();handler.post(t::requestFocus);}
    }
    private void search(Place p){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        EditText field=new EditText(this);field.setSingleLine(true);field.setText(p.query);field.setHint("Type a title, or speak into your remote");field.setTextColor(0xFFF4F7FB);field.setHintTextColor(0xFF93A5BC);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX,30*unit);field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);field.setContentDescription("Search your films and TV");field.setSaveEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0x33FFFFFF);bg.setCornerRadius(px(14));bg.setStroke(Math.max(1,px(2)),0x80FFFFFF);field.setBackground(bg);field.setPadding(px(24),px(14),px(24),px(14));
        LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(px(1100),-2);fl.bottomMargin=px(16);box.addView(field,fl);
        FrameLayout results=new FrameLayout(this);box.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        area.addView(box);heading("Search","Your films and programmes, on this TV");
        int[] ticket={0};
        Runnable run=()->{int mine=++ticket[0];String q=p.query;
            if(q.trim().isEmpty()){results.removeAllViews();TextView t=words("Search your films and programmes by name.",24,0xFFC3CEDD,false,2);results.addView(t);return;}
            LibraryCatalogue.Catalogue c=cat;
            quick.execute(()->{List<LibraryCatalogue.Card> hits=LibraryCatalogue.search(c,q);
                handler.post(()->{if(mine!=ticket[0]||isFinishing()||stack.peek()!=p)return;results.removeAllViews();
                    if(hits.isEmpty()){results.addView(words("Nothing called “"+q.trim()+"” in your library.",24,0xFFC3CEDD,false,2));return;}
                    LinearLayout holder=new LinearLayout(this);holder.setOrientation(LinearLayout.VERTICAL);results.addView(holder);
                    cardsInto(holder,p,hits,(hits.size()==1?"1 result":hits.size()+" results"));});});
        };
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(Editable e){p.query=e.toString();handler.removeCallbacks(run);handler.postDelayed(run,250);}});
        field.setOnEditorActionListener((v,action,ev)->{handler.removeCallbacks(run);run.run();View f=results.findFocus();if(f==null){View first=firstPoster(results);if(first!=null)first.requestFocus();}return true;});
        run.run();
        if(p.focus.isEmpty())field.requestFocus();
    }
    private static View firstPoster(View v){if(v instanceof PosterView)return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=firstPoster(((ViewGroup)v).getChildAt(i));if(f!=null)return f;}return null;}
    private void heading(String title,String meta){heroTitle.setText(title);heroMeta.setText(meta);heroText.setText("");}
    /** A grid of tiles, a chunk at a time ("More" adds the next): bounded views however large the library. */
    private void cards(Place p,List<LibraryCatalogue.Card> all,String caption){
        FrameLayout holder=new FrameLayout(this);area.addView(holder);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);holder.addView(box);
        cardsInto(box,p,all,caption);
    }
    private void cardsInto(LinearLayout box,Place p,List<LibraryCatalogue.Card> all,String caption){
        box.removeAllViews();
        if(caption!=null){TextView c=words(caption,24,0xFFC3CEDD,false,1);c.setPadding(px(6),0,0,px(6));box.addView(c);}
        ScrollView sv=new ScrollView(this);sv.setFocusable(false);sv.setVerticalScrollBarEnabled(false);sv.setClipToPadding(false);
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);grid.setPadding(px(8),px(8),0,px(80));sv.addView(grid);box.addView(sv,new LinearLayout.LayoutParams(-1,-1));
        int columns=Math.max(3,(getResources().getDisplayMetrics().widthPixels-px(160))/(px(208)+px(20)));
        int shown=Math.min(all.size(),Math.max(GRID_CHUNK,p.shown));List<LibraryCatalogue.Card> visible=all.subList(0,shown);
        PosterView again=null,first=null;LinearLayout line=null;
        for(int i=0;i<visible.size();i++){
            if(i%columns==0){line=new LinearLayout(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.bottomMargin=px(20);grid.addView(line,lp);}
            PosterView v=tile(visible.get(i),visible,i);line.addView(v,tileParams());if(first==null)first=v;if(visible.get(i).key().equals(p.focus))again=v;
        }
        if(shown<all.size()){
            if(line==null||line.getChildCount()>=columns){line=new LinearLayout(this);grid.addView(line);}
            int more=Math.min(GRID_CHUNK,all.size()-shown);
            PosterView m=action("Show more",more+" more",()->{p.shown=shown+GRID_CHUNK;p.focus=visible.get(visible.size()-1).key();cardsInto(box,p,all,caption);});line.addView(m,tileParams());
        }
        PosterView target=again!=null?again:(p.kind==Kind.SEARCH?null:first);p.focus="";
        if(target!=null){PosterView t=target;t.requestFocus();handler.post(t::requestFocus);}
    }

    // --- tiles
    private PosterView tile(LibraryCatalogue.Card card,List<LibraryCatalogue.Card> neighbours,int at){
        PosterView v=new PosterView(this,unit,calm);v.show(card);
        Bitmap b=posters.cachedPoster(card);if(b!=null)v.art(b);else posters.poster(card,udn,false,bmp->{if(v.card==card&&bmp!=null)v.art(bmp);});
        v.setOnClickListener(x->open(card));
        v.setOnLongClickListener(x->{actions(card);return true;});   // hold OK
        v.setOnFocusChangeListener((x,focused)->{if(!focused)return;hero(card);
            // The tile in focus and its neighbours come first in the picture queue.
            for(int d=-2;d<=4;d++){int i=at+d;if(i<0||i>=neighbours.size())continue;LibraryCatalogue.Card n=neighbours.get(i);if(posters.cachedPoster(n)==null)posters.poster(n,udn,true,d==0?bmp->{if(v.card==card&&bmp!=null&&!v.hasArt())v.art(bmp);}:null);}});
        return v;
    }
    private PosterView action(String title,String detail,Runnable run){
        PosterView v=new PosterView(this,unit,calm);v.showAction(title,detail);v.setOnClickListener(x->run.run());
        v.setOnFocusChangeListener((x,f)->{if(f){heroTitle.setText(title);heroMeta.setText(detail);heroText.setText("");focusKey="";wantedBack="";handler.removeCallbacks(backdropLater);toBack(null,"");}});return v;
    }
    /**
     * The highlighted title's words at once, and its own background asked for at the same moment (module 05 single focus
     * timing contract: no settle delay; the former 160 ms wait is gone). The picture arrives asynchronously and crossfades in
     * 280 ms; only the newest request is kept (Posters.latestBackdrop), so fast presses never queue a background per tile.
     * Never another title's picture.
     */
    private void hero(LibraryCatalogue.Card c){
        if(c==null){focusKey="";return;}
        focusKey=c.key();heroTitle.setText(c.name());
        List<String> meta=new ArrayList<>();if(!c.year().isEmpty())meta.add(c.year());
        if(c.show!=null)meta.add(c.show.episodes.size()==1?"1 episode":c.show.episodes.size()+" episodes");else if(!c.detail().isEmpty())meta.add(c.detail());else meta.add("Film");
        if(c.kids()&&!lib.kids)meta.add("Kids");
        if(c.resume>0)meta.add("Resume from "+PlaybackPolicy.clock(c.resume)+" (on this TV)");
        heroMeta.setText(String.join("  ·  ",meta));
        String[] details=posters.details(c,udn);String overview=details==null?"":details[0];
        heroText.setText(overview);
        wantedBack=c.key();handler.removeCallbacks(backdropLater);
        Bitmap b=posters.cachedBackdrop(c,light);
        if(b!=null){toBack(b,c.key());return;}
        // Not ready: fade to the calm neutral scene at once (no stale picture), then the title's own when it arrives.
        if(!shownBack.isEmpty())toBack(null,"");
        backdrop(c);
    }
    private void backdrop(LibraryCatalogue.Card c){
        String key=c.key();
        posters.latestBackdrop(c,udn,light,bmp->{if(bmp!=null&&key.equals(wantedBack)&&key.equals(focusKey)){toBack(bmp,key);
            String[] details=posters.details(c,udn);if(details!=null&&!details[0].isEmpty()&&key.equals(focusKey))heroText.setText(details[0]);}});
    }
    /** Crossfades to a picture (null: the neutral scene). Reduce motion: no fade. */
    private void toBack(Bitmap b,String key){
        if(key.equals(shownBack)&&(b!=null||shownBack.isEmpty()))return;shownBack=key;
        ImageView from=backs[front],to=backs[1-front];front=1-front;
        to.animate().cancel();from.animate().cancel();to.setImageBitmap(b);
        if(calm||light){to.setAlpha(b==null?0f:1f);from.setAlpha(0f);if(b==null)to.setImageDrawable(null);from.setImageDrawable(null);return;}
        to.setAlpha(0f);if(b!=null)to.animate().alpha(1f).setDuration(280).start();
        from.animate().alpha(0f).setDuration(280).withEndAction(()->{if(backs[front]!=from)from.setImageDrawable(null);}).start();
    }
    private void open(LibraryCatalogue.Card c){
        rememberFocus();
        if(c.show!=null){stack.push(new Place(Kind.SHOW,c.show.name,null,c.show.key));show();return;}
        LibraryCatalogue.Title t=c.title;
        Intent play=PlaybackPrefs.player(this,t);   // with the following episodes, for SET-033's autoplay
        try{startActivity(play);}catch(RuntimeException e){note("Unable to play this title");}
    }
    /**
     * Hold OK on a tile (SET-032, SET-034): Favourites for this profile, kept by the title's server id; and, for Adults, the
     * "Wrong picture?" sidebar to choose the right poster or none. Kids can keep favourites but never change pictures.
     */
    private void actions(LibraryCatalogue.Card c){
        android.content.SharedPreferences prefs=getSharedPreferences(HouseholdSettings.PREFS,0);
        boolean favourite=Favourites.has(prefs,c.key());List<String> items=new ArrayList<>();items.add(Favourites.action(favourite));
        if(!lib.kids)items.add(PictureFixActivity.ACTION);items.add("Cancel");
        new android.app.AlertDialog.Builder(this).setTitle(c.name()+(c.year().isEmpty()?"":" ("+c.year()+")")).setItems(items.toArray(new String[0]),(d,n)->{
            String pick=items.get(n);
            if(pick.equals(PictureFixActivity.ACTION)){fixing=c.key();try{startActivity(PictureFixActivity.intent(this,c,udn));overridePendingTransition(0,0);}catch(RuntimeException e){fixing=null;note("Unable to open Wrong picture?");}}
            else if(!pick.equals("Cancel")){rememberFocus();boolean now=Favourites.toggle(prefs,c.key());note(now?"Added to Favourites":"Removed from Favourites");rebuild(true);}
        }).show();
    }
    private void rememberFocus(){View f=getCurrentFocus();if(f instanceof PosterView&&((PosterView)f).card!=null&&!stack.isEmpty())stack.peek().focus=((PosterView)f).card.key();}
    @Override public void onBackPressed(){
        if(stack.size()>1){stack.pop();show();return;}
        super.onBackPressed();
    }
    /** For tests: the page now showing, and the catalogue. */
    Kind page(){return stack.isEmpty()?null:stack.peek().kind;}
    LibraryCatalogue.Catalogue catalogue(){return cat;}
}
