package io.unifiedtv.preview;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
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
 * (Launch.open: OD-26's VPN rule, then the Kids list), so choosing never unlocks it.</li>
 * </ul>
 */
public final class AppPickerActivity extends GlassScreen {
    static final String MODE="mode",PLACE="place",REPLACING="replacing",LIVE="live",THEN_OPEN="thenOpen";
    /** Home's intent extra: carry on into Live TV now that its app is chosen (UnifiedPreviewActivity). */
    static final String OPEN_LIVE="openLiveTv";

    /** OK on an empty place (replacing null), or Replace with another app on a tile (its dock id). */
    static void place(Activity a,int place,String replacing){
        a.startActivity(new Intent(a,AppPickerActivity.class).putExtra(MODE,PLACE).putExtra(PLACE,place).putExtra(REPLACING,replacing));
    }
    /** Choose the Live TV app; {@code thenOpen}: a Live TV route found none, so carry on into Live TV after the choice. */
    static void live(Activity a,boolean thenOpen){
        a.startActivity(new Intent(a,AppPickerActivity.class).putExtra(MODE,LIVE).putExtra(THEN_OPEN,thenOpen));
    }

    private String mode,replacing,room;private int place;private boolean thenOpen;
    private final Map<String,String> launchable=new LinkedHashMap<>();
    private final Map<String,ResolveInfo> entries=new HashMap<>();
    private final Map<String,Drawable> art=new HashMap<>();
    private LinearLayout list;private EditText search;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Intent i=getIntent();mode=LIVE.equals(i.getStringExtra(MODE))?LIVE:PLACE;place=i.getIntExtra(PLACE,-1);replacing=i.getStringExtra(REPLACING);thenOpen=i.getBooleanExtra(THEN_OPEN,false);
        room=prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");
        scan();
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
        fill();
    }
    /** The installed launchable apps, as Apps lists them (not Unified TV itself). */
    private void scan(){
        PackageManager pm=getPackageManager();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            List<ResolveInfo> found;try{found=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0);}catch(RuntimeException e){continue;}
            for(ResolveInfo r:found){if(r.activityInfo==null)continue;String pkg=r.activityInfo.packageName;if(pkg.equals(getPackageName())||launchable.containsKey(pkg))continue;
                launchable.put(pkg,String.valueOf(r.loadLabel(pm)));entries.put(pkg,r);}
        }
    }
    /** The rows for what is typed now; focus stays in the search field while typing. */
    private void fill(){
        list.removeAllViews();LinearLayout was=column;column=list;
        String q=search==null?"":search.getText().toString();
        List<DockPicker.Choice> choices=LIVE.equals(mode)?DockPicker.liveChoices(launchable,q):DockPicker.choices(room,launchable,onHomeNow(),q);
        String configured=prefs.getString(DockPicker.LIVE_PREF,"");
        for(DockPicker.Choice c:choices){
            Button b=row(c.name,LIVE.equals(mode)&&c.pkg.equals(configured)?"Chosen":c.streaming?"Streaming":null,false,()->choose(c));
            Drawable d=artFor(c.pkg);if(d!=null){b.setCompoundDrawablesRelative(d,null,b.getCompoundDrawablesRelative()[2],null);b.setCompoundDrawablePadding(px(20));}
        }
        if(choices.isEmpty())text(q.trim().isEmpty()?"No other apps are installed.":"No app matches “"+q.trim()+"”.",15);
        column=was;
    }
    /** Home's tiles for this profile: the picker never offers what is already there. */
    private List<String> onHomeNow(){return DockPolicy.visible(HouseholdSettings.dock(this,room),DockCapacity.get(prefs,Profiles.current(prefs)));}
    /** The app's own Android TV banner (16:9), else its icon, sized for a row. */
    private Drawable artFor(String pkg){
        if(art.containsKey(pkg))return art.get(pkg);
        Drawable d=null;boolean banner=false;ResolveInfo r=entries.get(pkg);PackageManager pm=getPackageManager();
        try{if(r!=null){d=r.activityInfo.loadBanner(pm);banner=d!=null;if(d==null)d=r.loadIcon(pm);}}catch(RuntimeException ignored){}
        if(d!=null){int h=px(48),w=banner?Math.round(h*16f/9):h;d.setBounds(0,0,w,h);}
        art.put(pkg,d);return d;
    }
    private void choose(DockPicker.Choice c){
        if(LIVE.equals(mode)){
            prefs.edit().putString(DockPicker.LIVE_PREF,c.pkg).apply();
            if(thenOpen){finish();startActivity(new Intent(this,UnifiedPreviewActivity.class).putExtra(OPEN_LIVE,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));return;}
            note("Live TV app: "+c.name);finish();return;
        }
        List<String> dock=HouseholdSettings.dock(this,room);int capacity=DockCapacity.get(prefs,Profiles.current(prefs));
        int at=replacing!=null?dock.indexOf(DockPolicy.canonical(replacing)):place;
        List<String> next=at<0?null:DockPicker.put(room,dock,at,c.id,capacity);
        if(next==null){note("That place has changed. Try again from Home.");finish();return;}
        prefs.edit().putString("dock",String.join(",",next)).putInt("focus",at).apply();
        finish();
    }
    /** For tests: the names offered now, in order. */
    List<String> offered(){List<String> out=new ArrayList<>();String q=search.getText().toString();
        for(DockPicker.Choice c:LIVE.equals(mode)?DockPicker.liveChoices(launchable,q):DockPicker.choices(room,launchable,onHomeNow(),q))out.add(c.name);return out;}
}
