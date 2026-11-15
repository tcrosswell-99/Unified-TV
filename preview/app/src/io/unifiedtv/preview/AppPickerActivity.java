package io.unifiedtv.preview;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.*;

/**
 * "Choose an app" (DockPicker's rules): the glass sidebar over a still Home (OD-19, one panel at a time). Each installed
 * app is a row with its own banner or icon and its name; a search field at the top (the TV's on-screen keyboard opens
 * when it is chosen) narrows the list as you type.
 *
 * <ul>
 * <li>{@link #place}: an empty dock place, or (with {@code replacing}) the tile Hold OK's "Replace with another app" was
 * chosen for. "Leave empty" (or "Keep it") changes nothing. A choice is saved in the household's dock in that place and
 * Home comes back with focus on it.</li>
 * <li>{@link #live}: the Live TV app. From Settings, "Automatic" goes back to Unified TV's own recognition. From a Live TV
 * route that found no app ({@code thenOpen}), the choice is saved and Home carries on into Live TV through the one gate
 * (Launch.open: OD-26's VPN rule, then the Kids list), so choosing never unlocks it. VPN apps, the TV's settings and
 * Unified TV itself are never offered (DockPicker.notLiveTv).</li>
 * </ul>
 *
 * Light on the 2 GB TCL (review of a615a38): nothing is read from the package manager on the main thread. The list of
 * apps and their names are read on a worker, with "Looking for apps…" meanwhile; each row starts with a plain placeholder,
 * and only rows on screen (and those scrolled or moved to) load their banner or icon, on the worker, drawn once into a
 * bitmap at the row's own size, so no full-size artwork is kept.
 */
public final class AppPickerActivity extends GlassScreen {
    static final String MODE="mode",PLACE="place",REPLACING="replacing",AFTER="after",LIVE="live",THEN_OPEN="thenOpen";
    /** Home's intent extra: carry on into Live TV now that its app is chosen (UnifiedPreviewActivity). */
    static final String OPEN_LIVE="openLiveTv";
    static final String LOOKING="Looking for apps…";

    /**
     * OK on Home's empty place {@code place} (its number on Home); {@code after} is the last tile Home shows (null: none), so
     * the choice goes right after it in the saved list, whatever that list also holds (DockPicker.insertAfter).
     */
    static void empty(Activity a,int place,String after){
        a.startActivity(new Intent(a,AppPickerActivity.class).putExtra(MODE,PLACE).putExtra(PLACE,place).putExtra(AFTER,after));
    }
    /** Replace with another app on the tile at {@code place} on Home (its dock id {@code replacing}). */
    static void place(Activity a,int place,String replacing){
        a.startActivity(new Intent(a,AppPickerActivity.class).putExtra(MODE,PLACE).putExtra(PLACE,place).putExtra(REPLACING,replacing));
    }
    /** Choose the Live TV app; {@code thenOpen}: a Live TV route found none, so carry on into Live TV after the choice. */
    static void live(Activity a,boolean thenOpen){
        a.startActivity(new Intent(a,AppPickerActivity.class).putExtra(MODE,LIVE).putExtra(THEN_OPEN,thenOpen));
    }

    /** For tests: run the worker's jobs here instead (null: the picker's own background thread). */
    static java.util.concurrent.Executor testWorker;
    private final java.util.concurrent.ExecutorService own=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean gone;
    private void work(Runnable job){try{(testWorker!=null?testWorker:own).execute(job);}catch(java.util.concurrent.RejectedExecutionException ignored){}}

    private String mode,replacing,after,room;private int place;private boolean thenOpen,scanned;
    private final Map<String,String> launchable=new LinkedHashMap<>();
    private final Map<String,ResolveInfo> entries=new HashMap<>();
    private final Set<String> vpnApps=new HashSet<>();
    /** Art at row size, by package, once loaded; which packages were asked for; the row showing each package now. */
    private final Map<String,Drawable> art=new HashMap<>();
    private final Set<String> asked=new HashSet<>();
    private final Map<String,Button> rows=new LinkedHashMap<>();
    private LinearLayout list;private EditText search;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Intent i=getIntent();mode=LIVE.equals(i.getStringExtra(MODE))?LIVE:PLACE;place=i.getIntExtra(PLACE,-1);replacing=i.getStringExtra(REPLACING);after=i.getStringExtra(AFTER);thenOpen=i.getBooleanExtra(THEN_OPEN,false);
        room=prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");
        boolean live=LIVE.equals(mode);
        page(live?DockPicker.LIVE_TITLE:replacing!=null?DockPicker.REPLACE_TITLE:DockPicker.TITLE,
            live?LiveTvProtection.RULE+" Choosing an app here never unlocks it.":replacing!=null?"Instead of "+DockCapacity.name(this,replacing)+". It stays installed and in Apps.":"Place "+(place+1)+" on Home");
        search=new EditText(this);search.setSingleLine(true);search.setHint("Search apps");search.setContentDescription("Search apps");
        search.setTextColor(SettingsGlass.TEXT);search.setHintTextColor(SettingsGlass.SECONDARY);search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,textPx(26));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setSaveEnabled(false);search.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        search.setPadding(px(24),px(12),px(24),px(12));search.setBackground(SettingsGlass.row(px(14),Math.max(2,px(3)),prefs.getBoolean("highContrast",false),0));
        body.addView(search,rowParams());
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){fill();}
        });
        column=body;
        if(live){if(!thenOpen)row(DockPicker.AUTOMATIC+" (Unified TV recognises it)",null,false,()->{prefs.edit().remove(DockPicker.LIVE_PREF).apply();note("Live TV app: Automatic");finish();});}
        else row(replacing!=null?"Keep "+DockCapacity.name(this,replacing):DockPicker.LEAVE,null,false,this::finish);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list,new LinearLayout.LayoutParams(-1,-2));
        column=list;busy(LOOKING);column=body;
        scroll.setOnScrollChangeListener((v,x,y,ox,oy)->visibleArt());
        scan();
    }
    @Override protected void onDestroy(){gone=true;own.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    /** The installed launchable apps, as Apps lists them (not Unified TV itself), and the apps that can run a VPN: on the worker. */
    private void scan(){
        android.content.Context app=getApplicationContext();String self=getPackageName();boolean live=LIVE.equals(mode);
        work(()->{
            Map<String,String> found=new LinkedHashMap<>();Map<String,ResolveInfo> infos=new HashMap<>();Set<String> vpn=new HashSet<>();
            PackageManager pm=app.getPackageManager();
            for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
                List<ResolveInfo> list;try{list=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0);}catch(RuntimeException e){continue;}
                for(ResolveInfo r:list){if(r.activityInfo==null)continue;String pkg=r.activityInfo.packageName;if(pkg.equals(self)||found.containsKey(pkg))continue;
                    String label;try{label=String.valueOf(r.loadLabel(pm));}catch(RuntimeException e){label=pkg;}found.put(pkg,label);infos.put(pkg,r);}
            }
            if(live)try{vpn.addAll(LiveTvProtection.vpnApps(app));}catch(RuntimeException ignored){}
            main.post(()->{if(gone)return;launchable.putAll(found);entries.putAll(infos);vpnApps.addAll(vpn);scanned=true;fill();});
        });
    }
    private List<DockPicker.Choice> choicesNow(){
        String q=search.getText().toString();
        return LIVE.equals(mode)?DockPicker.liveChoices(launchable,q,vpnApps,getPackageName()):DockPicker.choices(room,launchable,onHomeNow(),q);
    }
    /** The rows for what is typed now; focus stays in the search field while typing. Each starts with a placeholder. */
    private void fill(){
        if(!scanned)return;
        list.removeAllViews();rows.clear();LinearLayout was=column;column=list;
        String q=search.getText().toString();List<DockPicker.Choice> choices=choicesNow();
        String configured=prefs.getString(DockPicker.LIVE_PREF,"");
        for(DockPicker.Choice c:choices){
            Button b=row(c.name,LIVE.equals(mode)&&c.pkg.equals(configured)?"Chosen":c.streaming?"Streaming":null,false,()->choose(c));
            setArt(b,art.containsKey(c.pkg)?art.get(c.pkg):placeholder());rows.put(c.pkg,b);
            b.setOnFocusChangeListener((v,f)->{if(f)main.post(this::visibleArt);});
        }
        if(choices.isEmpty())text(q.trim().isEmpty()?"No other apps are installed.":"No app matches “"+q.trim()+"”.",15);
        column=was;
        list.post(this::visibleArt);
    }
    private int artHeight(){return px(48);}
    /** A plain square where the art will be, so rows never jump when it arrives. */
    private Drawable placeholder(){ColorDrawable d=new ColorDrawable(0x26FFFFFF);d.setBounds(0,0,artHeight(),artHeight());return d;}
    private void setArt(Button b,Drawable d){b.setCompoundDrawablesRelative(d,null,b.getCompoundDrawablesRelative()[2],null);b.setCompoundDrawablePadding(px(20));}
    /** Asks for the art of the rows on screen now (only once each). */
    private void visibleArt(){
        if(gone)return;Rect seen=new Rect();
        for(Map.Entry<String,Button> e:rows.entrySet()){String pkg=e.getKey();Button b=e.getValue();
            if(asked.contains(pkg)||!b.isShown()||!b.getGlobalVisibleRect(seen))continue;asked.add(pkg);load(pkg);}
    }
    /** The app's own Android TV banner (16:9), else its icon, drawn once at the row's size on the worker. */
    private void load(String pkg){
        ResolveInfo r=entries.get(pkg);if(r==null)return;PackageManager pm=getApplicationContext().getPackageManager();
        android.content.res.Resources res=getResources();int h=artHeight();
        work(()->{
            Drawable d=null;boolean banner=false;
            try{d=r.activityInfo.loadBanner(pm);banner=d!=null;if(d==null)d=r.loadIcon(pm);}catch(RuntimeException ignored){}
            if(d==null)return;
            int w=banner?Math.round(h*16f/9):h;Bitmap bm=Bitmap.createBitmap(Math.max(1,w),Math.max(1,h),Bitmap.Config.ARGB_8888);
            d.setBounds(0,0,w,h);d.draw(new Canvas(bm));
            BitmapDrawable small=new BitmapDrawable(res,bm);small.setBounds(0,0,w,h);
            main.post(()->{if(gone)return;art.put(pkg,small);Button now=rows.get(pkg);if(now!=null)setArt(now,small);});
        });
    }
    /** Home's tiles for this profile: the picker never offers what is already there. */
    private List<String> onHomeNow(){return DockPolicy.visible(HouseholdSettings.dock(this,room),DockCapacity.get(prefs,Profiles.current(prefs)));}
    private void choose(DockPicker.Choice c){
        if(LIVE.equals(mode)){
            prefs.edit().putString(DockPicker.LIVE_PREF,c.pkg).apply();
            if(thenOpen){finish();startActivity(new Intent(this,UnifiedPreviewActivity.class).putExtra(OPEN_LIVE,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));return;}
            note("Live TV app: "+c.name);finish();return;
        }
        List<String> dock=HouseholdSettings.dock(this,room);int capacity=DockCapacity.get(prefs,Profiles.current(prefs));
        List<String> next;
        if(replacing!=null){int at=dock.indexOf(DockPolicy.canonical(replacing));next=at<0?null:DockPicker.put(room,dock,at,c.id,Math.max(capacity,dock.size()));}
        else next=DockPicker.insertAfter(room,dock,after,c.id,place,capacity);
        if(next==null){note("That place has changed. Try again from Home.");finish();return;}
        // Focus on Home: the place as Home counts it (Home's own list), not an index into the saved one.
        prefs.edit().putString("dock",String.join(",",next)).putInt("focus",place).apply();
        finish();
    }
    /** For tests: the names offered now, in order (empty until the worker has read the apps). */
    List<String> offered(){List<String> out=new ArrayList<>();if(!scanned)return out;for(DockPicker.Choice c:choicesNow())out.add(c.name);return out;}
    /** For tests: whether the apps have been read, and the packages whose art was asked for. */
    boolean scanned(){return scanned;}
    Set<String> artAsked(){return new HashSet<>(asked);}
    Drawable artOf(String pkg){Button b=rows.get(pkg);return b==null?null:b.getCompoundDrawablesRelative()[0];}
}
