package io.unifiedtv.preview;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.*;
import android.os.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

/**
 * P05-03: every installed app, in a compact grid. Hold OK for Open, Add to / Remove from Home and App information.
 * Unified TV's own entries come first (Library, and Live TV, Xbox or TV Plus where the room has them), so a removed one
 * can always be put back. In Kids only the apps an adult approved are listed; opening is still checked by Launch, because
 * a hidden tile is not a lock.
 *
 * Dock size (v3.4.6 UX-DOCK-08, SET-BUILD-04): "On Home" means on this profile's visible dock (DockCapacity, 6 or 8
 * places). Adding to a full dock never overwrites a tile by itself: the household picks the tile to replace (it stays
 * installed and here in Apps), or keeps the app in Apps.
 *
 * OD-11 (owner, 25 October 2026): the grid is content and fills the screen, but managing an app or the dock (Hold OK:
 * Open, Add to / Remove from Home, App information; and choosing the tile to replace) happens in the module 13 glass
 * sidebar from the right, over the grid, which stays still and can't take focus until the sidebar closes (Back).
 */
public final class AppsActivity extends Screen {
    private SharedPreferences prefs;private String room;
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("unified-tv-preview",0);room=prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");show(null);}
    private List<String> dock(){return HouseholdSettings.dock(this,room);}
    private int capacity(){return DockCapacity.get(prefs,Profiles.current(prefs));}
    /** The tiles this profile's Home shows. */
    private List<String> onHome(){return DockPolicy.visible(dock(),capacity());}
    private void show(String focus){
        boolean kids=Profiles.kids(prefs);java.util.Set<String> approved=KidsPolicy.apps(prefs.getString(KidsPolicy.APPS_PREF,""));
        page("Apps");text(kids?"Apps an adult has allowed for Kids. Ask an adult to add more.":"Hold OK on an app to add it to Home or remove it.",17);
        PackageManager pm=getPackageManager();Map<String,ResolveInfo> apps=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){if(r.activityInfo==null||r.activityInfo.packageName.equals(getPackageName()))continue;String label=String.valueOf(r.loadLabel(pm));if(!apps.containsKey(label+"\u0000"+r.activityInfo.packageName))apps.put(label+"\u0000"+r.activityInfo.packageName,r);}
        List<String> home=onHome();Set<String> seen=new HashSet<>();LinearLayout row=null;int n=0;Button target=null;
        // Unified TV's own shortcuts (no app of their own) come first, so a removed one can always be put back.
        List<String> own=DockPolicy.shortcuts(room);
        for(String id:own){String label=SHORTCUTS.getOrDefault(id,id);
            if(kids&&!id.equals(DockPolicy.LIBRARY)&&!(id.equals("live")?approved.contains(LiveTvProtection.livePackage(this)):approved.contains(id)))continue;
            if(n++%5==0){row=new LinearLayout(this);column.addView(row);}
            boolean onHome=home.contains(id);Button open=styled(label+(onHome?"  ·  On Home":""),()->shortcut(id));open.setTextSize(16);open.setOnLongClickListener(v->{menu(id,label);return true;});
            open.setContentDescription(label+(onHome?", on Home":"")+". Hold OK for options.");row.addView(open,new LinearLayout.LayoutParams(0,-2,1));if(id.equals(focus)||(target==null&&n==1))target=open;}
        for(Map.Entry<String,ResolveInfo> e:apps.entrySet()){String pkg=e.getValue().activityInfo.packageName;if(!seen.add(pkg)||(kids&&!approved.contains(pkg)))continue;String label=e.getKey().substring(0,e.getKey().indexOf('\u0000'));
            if(n++%5==0){row=new LinearLayout(this);column.addView(row);}
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(6),dp(6),dp(6),dp(6));
            ImageView icon=new ImageView(this);try{icon.setImageDrawable(pm.getApplicationIcon(pkg));}catch(Exception ignored){}icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(icon,new LinearLayout.LayoutParams(-1,dp(56)));
            boolean onHome=home.contains(DockPolicy.canonical(pkg));
            Button open=styled(label+(onHome?"  ·  On Home":""),()->launch(pkg,label));open.setTextSize(16);open.setOnLongClickListener(v->{menu(pkg,label);return true;});
            open.setContentDescription(label+(onHome?", on Home":"")+". Hold OK for options.");card.addView(open,new LinearLayout.LayoutParams(-1,-2));
            row.addView(card,new LinearLayout.LayoutParams(0,-2,1));if(pkg.equals(focus))target=open;else if(target==null&&n==1)target=open;
        }
        if(apps.isEmpty())text("No apps found.",20);
        button("Back to Home",this::finish);if(target!=null)target.requestFocus();
        stage();
    }
    // --- The management sidebar (OD-11)
    private FrameLayout stage;private View grid,side;private LinearLayout rows;private String sideFor;
    /** Puts the grid on a stage, so the sidebar can come in over it. */
    private void stage(){
        ViewGroup content=findViewById(android.R.id.content);grid=(View)column.getParent();content.removeView(grid);
        stage=new FrameLayout(this);stage.addView(grid,new FrameLayout.LayoutParams(-1,-1));setContentView(stage);side=null;sideFor=null;
    }
    private float unit(){android.util.DisplayMetrics m=getResources().getDisplayMetrics();return Math.max(.5f,Math.min(m.widthPixels/1920f,m.heightPixels/1080f));}
    private int px(float at1080){return Math.round(at1080*unit());}
    private boolean calm(){return prefs.getBoolean("reduceMotion",false)||!AmbientLayer.tvAnimations();}
    /** Opens (or refills) the sidebar for this app: its name, then one row per action. */
    private void sidebar(String title,String sub){
        if(side==null){
            int screen=getResources().getDisplayMetrics().widthPixels;float dp=screen/getResources().getDisplayMetrics().density;
            // Nearly solid here: behind it is the busy grid, not Home's quiet light, so the actions stay easy to read.
            FrameLayout sheet=new FrameLayout(this);View glass=new View(this);glass.setBackground(new SettingsGlass.Sheet(px(28),px(24),true));glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);sheet.addView(glass,new FrameLayout.LayoutParams(-1,-1));
            ScrollView scroll=new ScrollView(this);scroll.setFocusable(false);rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(px(84),px(44),px(56),px(24));scroll.addView(rows);
            sheet.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
            stage.addView(sheet,new FrameLayout.LayoutParams(Math.round(SettingsOverlay.fraction(false,dp)*screen+px(28)),-1,android.view.Gravity.END));side=sheet;
            if(calm()){sheet.setAlpha(0);sheet.animate().alpha(1).setDuration(SettingsOverlay.CROSSFADE_MS).start();}
            else{sheet.setTranslationX(screen);sheet.animate().translationX(0).setDuration(SettingsOverlay.OPEN_MS).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();}
        }
        ((ViewGroup)grid).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);grid.setAlpha(.45f);rows.removeAllViews();
        TextView t=new TextView(this);t.setText(title);t.setTextColor(SettingsGlass.TEXT);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(36));t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);rows.addView(t);
        if(sub!=null){TextView u=new TextView(this);u.setText(sub);u.setTextColor(SettingsGlass.SECONDARY);u.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(21));u.setPadding(0,px(6),0,px(10));rows.addView(u);}
    }
    private Button row(String words,Runnable action){
        Button b=new Button(this);b.setText(words);b.setAllCaps(false);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(24));b.setTextColor(SettingsGlass.textColours());
        b.setBackground(SettingsGlass.row(px(20),Math.max(2,px(2)),prefs.getBoolean("highContrast",false),calm()?0:SettingsOverlay.FOCUS_MS));b.setStateListAnimator(null);
        b.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);b.setPadding(px(24),px(12),px(24),px(12));b.setMinHeight(px(64));b.setMinimumHeight(px(64));
        b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=px(10);rows.addView(b,lp);
        if(rows.findFocus()==null)b.requestFocus();return b;
    }
    /** Closes the sidebar; focus goes back to the app it was opened for. */
    private void closeSide(){
        if(side==null)return;stage.removeView(side);side=null;grid.setAlpha(1f);((ViewGroup)grid).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        String back=sideFor;sideFor=null;if(back!=null)show(back);
    }
    /** For tests: whether the management sidebar is open. */
    boolean sideOpen(){return side!=null;}
    @Override public void onBackPressed(){if(side!=null)closeSide();else super.onBackPressed();}
    private static final Map<String,String> SHORTCUTS=new HashMap<>();
    static{SHORTCUTS.put(DockPolicy.LIBRARY,"Library");SHORTCUTS.put("live","Live TV");SHORTCUTS.put("xbox","Xbox");SHORTCUTS.put("tvplus","Samsung TV Plus");}
    private void shortcut(String id){
        if(id.equals(DockPolicy.LIBRARY)){Launch.library(this);return;}
        if(id.equals("live")){launch(LiveTvProtection.livePackage(this),"Live TV");return;}
        String no=Launch.kidsRefusal(this,id,SHORTCUTS.getOrDefault(id,id));if(no!=null){note(no);return;}
        HouseholdActivity.action(this,id);
    }
    private void launch(String pkg,String label){String why=Launch.open(this,pkg,label);if(why!=null&&!why.isEmpty())note(why);}
    private void menu(String pkg,String label){
        boolean onHome=onHome().contains(DockPolicy.canonical(pkg));
        sidebar(label,onHome?"On Home":null);sideFor=pkg;
        row("Open",()->{closeSide();if(SHORTCUTS.containsKey(pkg))shortcut(pkg);else launch(pkg,label);});
        row(onHome?"Remove from Home":"Add to Home",()->EditLock.ask(this,()->{String next=onHome?DockPolicy.remove(dock(),pkg):DockPolicy.add(room,dock(),pkg,capacity());
            if(next==null){full(pkg,label);return;}
            prefs.edit().putString("dock",next).apply();note(onHome?"Removed from Home":"Added to Home");closeSide();}));
        row("App information",()->{if(SHORTCUTS.containsKey(pkg))note(label+" is a Unified TV shortcut");else Launch.info(this,pkg);});
        row("Cancel",this::closeSide);
    }
    /** Home is full: replace a tile the household picks, or keep the app in Apps. Keep in Apps or Back changes nothing. */
    private void full(String pkg,String label){
        List<String> shown=onHome();
        sidebar("Home is full","Replace which tile with "+label+"? It stays installed and here in Apps. ("+shown.size()+" of "+capacity()+" places)");sideFor=pkg;
        for(String id:shown){String name=DockCapacity.name(this,id);row(name,()->{
            prefs.edit().putString("dock",DockPolicy.replace(room,dock(),id,pkg)).apply();
            note(name+" left Home and stays here in Apps");closeSide();});}
        row("Keep in Apps",this::closeSide);
    }
}
