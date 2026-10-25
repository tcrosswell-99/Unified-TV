package io.unifiedtv.preview;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;

/**
 * Settings as the v3.4.7 Liquid Glass overlay (module 13; module 12 §2 for what each category holds). A translucent
 * activity (SettingsGlassTheme): Home stays exactly where it was underneath, paused but visible, and never moves, shrinks or
 * reflows. One dark glass sheet slides in from the right over about half the screen; the root lists the ten categories,
 * a category's detail replaces the content inside the same sheet, and a complex task widens the sheet to about three
 * quarters. Back (or Left with nothing further left) goes task → detail → root → closed; Home underneath still has the
 * Settings gear in focus. The overlay's states live in {@link SettingsOverlay}; the material in {@link SettingsGlass}.
 * A category whose integration isn't set up offers a short "Set up" entry instead of empty switches (SET-008), and a
 * control that can't work on this TV isn't shown (SET-003).
 */
public final class SettingsActivity extends Screen {
    static final String[] CATEGORIES={"Appearance & Home","Library & Media","Profiles & Family","Apps & Shortcuts","Notifications & Doorbell","Smart Home","Live TV & Network Protection","Connections & Devices","Privacy & Data","Updates & About"};
    static final int APPEARANCE=0,LIBRARY=1,PROFILES=2,APPS=3,NOTIFICATIONS=4,SMART_HOME=5,LIVE_TV=6,CONNECTIONS=7,PRIVACY=8,ABOUT=9;
    /** One-line subtitles (module 13 §C wording where it gives one). */
    private static final String[] BLURBS={"Make it yours","Your films and shows","Who's watching, the adult PIN and Kids","Choose what lives on Home","Doorbell, reminders and smoke or carbon monoxide alarms","Home Assistant and your team's fixtures","Live TV stays locked until its protection is verified","This TV's own settings, AirPlay and voice","What's kept on this TV and how it's protected","Version, licences and advanced checks"};
    /** Module 13 §C 00: small group labels over the root rows (module 12 order: 4, 4, 2). */
    private static final String[] GROUPS={"Personalise","Connected services","System"};private static final int[] GROUP_START={0,4,8};
    /** Tasks inside a category: a wider sheet for complex ones, the same width for a collapsed Advanced section. */
    static final String NAVIGATION="navigation",APPEARANCE_ADVANCED="appearance-advanced",ADVANCED="advanced";
    private static final Set<Integer> NAVIGATION_KEYS=new HashSet<>(Arrays.asList(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER,KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_TAB,KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_SELECT));

    private final SettingsOverlay overlay=new SettingsOverlay();
    /** The root's category rows; null while a detail is showing. */
    private final Button[] rows=new Button[CATEGORIES.length];
    private FrameLayout sheet;private View frost,glass;private LinearLayout header,body;private ScrollView scroll;private TextView hint;private String subtitle;
    private SharedPreferences prefs;
    /** SET-093: a navigation change is previewed here and applied only on confirmation; null when there is none. */
    private List<String> draftUtilities;
    private Thread teamWorker;
    private float unit;private int screenWidth;
    @Override public void onCreate(Bundle state){super.onCreate(state);autoFocusButtons=false;HouseholdSettings.ensure(this);prefs=getSharedPreferences(HouseholdSettings.PREFS,0);
        android.util.DisplayMetrics m=getResources().getDisplayMetrics();screenWidth=Math.max(1,m.widthPixels);unit=Math.max(.5f,Math.min(m.widthPixels/1920f,m.heightPixels/1080f));
        // The window is transparent and full screen: Home shows through and, while Settings is open, gets no keys or touches.
        FrameLayout root=new FrameLayout(this);root.setClipChildren(false);
        sheet=new FrameLayout(this);sheet.setClipChildren(false);
        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.VERTICAL);inner.setPadding(Math.round(shadow())+px(56),px(44),px(56),px(24));
        header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);inner.addView(header,new LinearLayout.LayoutParams(-1,-2));
        scroll=new ScrollView(this);scroll.setFocusable(false);scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);scroll.setVerticalFadingEdgeEnabled(true);scroll.setFadingEdgeLength(px(36));scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,px(8),0,px(24));scroll.addView(body,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams under=new LinearLayout.LayoutParams(-1,0,1);under.topMargin=px(14);inner.addView(scroll,under);
        sheet.addView(inner,new FrameLayout.LayoutParams(-1,-1));material();
        int start=state!=null?state.getInt("category",-1):getIntent().getIntExtra("category",-1);if(start>=CATEGORIES.length)start=-1;
        if(state!=null)overlay.restore(start,state.getString("task"),state.getBoolean("wide",false),state.getInt("row",0));else if(start>=0)overlay.open(start);
        root.addView(sheet,new FrameLayout.LayoutParams(sheetWidth(),-1,Gravity.END));
        // Attach first, then focus: a view focused before it is in the window never becomes the window's focus.
        setContentView(root);render(false);landing();
        if(state==null)slideIn();
        // The notification's "Stop alerts" lands here, so stopping always asks for the adult PIN (owner exception to SET-053).
        if(state==null&&getIntent().getBooleanExtra("stopAlerts",false))stopAlerts();
    }
    @Override protected void onDestroy(){if(teamWorker!=null)teamWorker.interrupt();super.onDestroy();}
    /** For tests and diagnostics: where the overlay is, and the sheet itself. */
    SettingsOverlay overlay(){return overlay;}
    View sheet(){return sheet;}

    // --- Material, size and motion (module 13 §A tokens, at 1080p and scaled to this screen)
    private int px(float at1080){return Math.round(at1080*unit);}
    private float textPx(float at1080){return at1080*unit*getResources().getConfiguration().fontScale;}
    private float shadow(){return px(28);}
    private boolean calm(){return prefs.getBoolean("reduceMotion",false)||!android.animation.ValueAnimator.areAnimatorsEnabled();}
    private boolean solid(){return prefs.getBoolean("opaque",false)||strong();}
    private boolean strong(){return prefs.getBoolean("highContrast",false);}
    /** A real blur only where the platform has one for a view's own content and the TV isn't asked to stay light. */
    private boolean blurs(){return Build.VERSION.SDK_INT>=Build.VERSION_CODES.S&&!prefs.getBoolean("lightweight",false)&&!((android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE)).isLowRamDevice();}
    /** (Re)builds the glass under the content: frost (unless transparency is reduced), then tint, sheen and edge. */
    private void material(){
        if(frost!=null)sheet.removeView(frost);if(glass!=null)sheet.removeView(glass);frost=null;
        float radius=px(24);boolean solid=solid();
        if(!solid){frost=new SettingsGlass.Frost(this,prefs.getString("scene","neutral"),blurs(),shadow(),radius,screenWidth);
            // API 31+: RenderEffect blurs this view's own still frame, never Home's live pixels (no app can sample those).
            if(blurs()&&Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)frost.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(px(24),px(24),android.graphics.Shader.TileMode.CLAMP));
            sheet.addView(frost,0,new FrameLayout.LayoutParams(-1,-1));}
        glass=new View(this);glass.setFocusable(false);glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);glass.setBackground(new SettingsGlass.Sheet(shadow(),radius,solid));
        sheet.addView(glass,frost==null?0:1,new FrameLayout.LayoutParams(-1,-1));
    }
    private int sheetWidth(){float dp=screenWidth/getResources().getDisplayMetrics().density;return Math.round(overlay.width(dp)*screenWidth+shadow());}
    private int hidden(){return sheet.getLayoutParams()==null?screenWidth:sheet.getLayoutParams().width+px(8);}
    /** SET-GLASS-05: in from the right, ease-out, no bounce; reduced motion is a short crossfade. Finite, never looping. */
    private void slideIn(){
        boolean calm=calm();int ms=SettingsOverlay.duration(true,calm);
        if(calm)sheet.setAlpha(0);else sheet.setTranslationX(hidden());
        sheet.post(()->{if(!overlay.acceptsInput())return;ViewPropertyAnimator a=sheet.animate().setDuration(ms).withEndAction(overlay::settled);
            if(calm)a.alpha(1);else a.translationX(0).setInterpolator(new DecelerateInterpolator(2f));a.start();});
        sheet.postDelayed(overlay::settled,ms+250);
    }
    /** Out the way it came, from wherever it is (interrupt-safe), then finish with no second window animation. */
    private void leave(){
        boolean calm=calm();int ms=SettingsOverlay.duration(false,calm);
        Runnable done=()->{if(overlay.finished()){finish();overridePendingTransition(0,0);}};
        sheet.animate().cancel();ViewPropertyAnimator a=sheet.animate().setDuration(ms).withEndAction(done);
        if(calm)a.alpha(0);else a.translationX(hidden()).setInterpolator(new AccelerateInterpolator(1.4f));a.start();
        sheet.postDelayed(done,ms+250);   // never a half-visible sheet holding focus, even if the animation is cut short
    }
    /** SET-GLASS-02/05: one sheet widening (or narrowing) from its right edge; content is laid out once, not per frame. */
    private void applyWidth(boolean motion){
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)sheet.getLayoutParams();if(lp==null)return;int w=sheetWidth(),old=lp.width;if(old==w)return;
        lp.width=w;sheet.setLayoutParams(lp);if(!motion||old<=0||calm())return;
        for(View v:new View[]{frost,glass}){if(v==null)continue;v.animate().cancel();v.setPivotX(w);v.setScaleX((float)old/w);v.animate().scaleX(1).setDuration(SettingsOverlay.WIDEN_MS).setInterpolator(new DecelerateInterpolator(1.6f)).start();}
    }

    // --- Pages
    /** Builds the header and body for where the overlay is; motion glides the new content in. */
    private void render(boolean motion){
        applyWidth(motion);header.removeAllViews();body.removeAllViews();Arrays.fill(rows,null);column=body;
        int c=overlay.category();
        switch(overlay.page()){
            case ROOT:head(false,"Settings",null);root();break;
            case DETAIL:head(true,CATEGORIES[c],BLURBS[c]);card();detail(c);break;
            default:head(true,taskTitle(),taskSubtitle());card();task();break;
        }
        for(int i=body.getChildCount()-1;i>=0;i--){View v=body.getChildAt(i);if(v instanceof LinearLayout&&((LinearLayout)v).getChildCount()==0)body.removeViewAt(i);}
        column=body;
        if(motion){scroll.scrollTo(0,0);if(!calm()){body.setAlpha(0);body.setTranslationX(px(24));body.animate().alpha(1).translationX(0).setDuration(SettingsOverlay.CONTENT_MS).setInterpolator(new DecelerateInterpolator()).start();}}
    }
    private void root(){
        for(int i=0;i<CATEGORIES.length;i++){
            for(int g=0;g<GROUPS.length;g++)if(GROUP_START[g]==i)group(GROUPS[g]);
            final int index=i;String state=status(i);Button b=row(CATEGORIES[i],()->open(index));
            b.setCompoundDrawablesRelative(new SettingsGlass.Glyph(i,px(30)),null,new SettingsGlass.Trailing(state,true,false,textPx(19)),null);b.setCompoundDrawablePadding(px(20));
            b.setContentDescription(state==null?CATEGORIES[i]:CATEGORIES[i]+", "+state);
            b.setOnFocusChangeListener((v,focused)->{if(focused)overlay.rootRow(index);});
            rows[i]=b;body.addView(b,rowParams());
        }
    }
    /** A small state on a root row, only when Unified TV has read it on this TV (never "connected" or "protected" by guess). */
    private String status(int i){
        switch(i){
            case PROFILES:return Profiles.label(Profiles.current(prefs));
            case LIBRARY:return HomeShelf.configured(this)?null:"Not set up";
            case NOTIFICATIONS:return prefs.getBoolean(AlarmWatch.STOPPED,false)?"Alerts off":null;
            case SMART_HOME:return haConfigured()?null:"Not set up";
            case LIVE_TV:return LiveTvProtection.opens(LiveTvProtection.now(this,""))?null:"Locked";
            default:return null;
        }
    }
    private void detail(int c){
        switch(c){
        case APPEARANCE:appearance();break;
        case LIBRARY:
            if(!HomeShelf.configured(this)){text(Profiles.kids(this)?"No Kids folders are chosen yet. An adult chooses them in Profiles & Family > Kids settings.":"No media library is set up yet. Unified TV looks for media servers on the home network; you never need to type an address.",17);
                if(!Profiles.kids(this))button("Set up Your Films & TV",()->startActivity(new Intent(this,DlnaLibraryActivity.class).putExtra("setup",true)));}
            else button("Your Films & TV",()->startActivity(new Intent(this,DlnaLibraryActivity.class).putExtra("setup",true)));
            button("Media server accounts",()->new AlertDialog.Builder(this).setTitle("Media account").setItems(new String[]{"Adults","Kids"},(d,n)->connection(n==0?"media-adult":"media-child")).show());
            text("Library opens from its own dock tile or by pressing Down on Home. Playback, resume and Kids limits stay on this TV.",16);break;
        case PROFILES:profiles();break;
        case APPS:
            button("All apps",()->startActivity(new Intent(this,AppsActivity.class)));
            text("On Home, hold OK on a tile to move or remove it. In All apps, hold OK on an app to add it to Home. A new TV suggests up to six apps that are already installed; empty places are fine.",16);
            // Build My TV (SET-BUILD, module 13 §C 02) goes here as a prominent card; it is being built separately and wired in afterwards.
            button("Amazon Music",()->startActivity(new Intent(this,MusicActivity.class)));
            text("Menu on the remote also opens All apps from Home.",15);break;
        case NOTIFICATIONS:notifications();break;
        case SMART_HOME:smartHome();break;
        case LIVE_TV:liveTv();break;
        case CONNECTIONS:
            button("Picture, sound, network & Bluetooth",()->Launch.unlocked(this,new Intent(Settings.ACTION_SETTINGS),"Use the TV's own Settings"));
            text("These belong to the TV itself; Unified TV opens them and changes nothing there.",15);
            section("AirPlay");airPlay();
            section("Voice");button("Ask your TV",()->startActivity(new Intent(this,VoiceActivity.class)));
            text("Voice uses the TV's own speech provider only when you start it, and may use its cloud service.",15);break;
        case PRIVACY:
            text("Profiles, watch history, preferences and connections stay on this TV. Credentials are encrypted locally and excluded from backup. Nothing is sent anywhere unless you connect a service.",16);
            button("Manage private connections",()->show(SMART_HOME));
            button("Integration readiness",()->new AlertDialog.Builder(this).setTitle("Preview limitations").setMessage("VPN tunnel visibility does not verify protected playback. NAS, Ring, reminders, microphone and manufacturer controls need their own acceptance tests. The Android APK cannot run on native Tizen.").setPositiveButton("Close",null).show());break;
        default:about();break;
        }
    }
    private String taskTitle(){return NAVIGATION.equals(overlay.task())?"Navigation & Shortcuts":APPEARANCE_ADVANCED.equals(overlay.task())?"Advanced appearance":"Advanced";}
    private String taskSubtitle(){return NAVIGATION.equals(overlay.task())?"Utilities, and where Down lands":APPEARANCE_ADVANCED.equals(overlay.task())?"Restore the defaults":"Owner checks, for Adults";}
    private void task(){String t=overlay.task();if(NAVIGATION.equals(t))navigation();else if(APPEARANCE_ADVANCED.equals(t))appearanceAdvanced();else advanced();}

    // --- Navigation inside the sheet (module 13 §B; SET-GLASS-06)
    private void open(int c){if(overlay.open(c)){render(true);focusAt(0);}}
    private void expand(String task,boolean wider){if(overlay.expand(task,wider,focusIndex())){render(true);focusAt(0);}}
    /** A refresh after a change re-renders the current page in place; another category opens it in the sheet. */
    private void show(int c){
        if(!overlay.acceptsInput())return;
        if(c!=overlay.category()||overlay.atRoot()){open(c);return;}
        int at=focusIndex(),y=scroll.getScrollY();render(false);scroll.scrollTo(0,y);focusAt(Math.max(0,at));
    }
    /** Back: task → detail (the row that opened it) → root (the category's row) → closed. */
    void back(){
        if(!overlay.acceptsInput())return;
        overlay.back();
        if(!overlay.acceptsInput()){leave();return;}
        render(true);
        if(overlay.atRoot())landing();else focusAt(overlay.detailRow());
    }
    private void closeNow(){if(overlay.close())leave();}
    /** Where focus lands on a page: the remembered root row, else the first control. */
    private void landing(){if(overlay.atRoot()&&rows[overlay.rootRow()]!=null)rows[overlay.rootRow()].requestFocus();else focusAt(0);}
    private int focusIndex(){return body.getFocusables(View.FOCUS_DOWN).indexOf(getCurrentFocus());}
    /** The n-th control of the body (clamped); with none at all, the header's own Back or Close, so focus is never lost. */
    private void focusAt(int n){
        ArrayList<View> all=body.getFocusables(View.FOCUS_DOWN);
        if(!all.isEmpty()){all.get(Math.max(0,Math.min(n,all.size()-1))).requestFocus();return;}
        if(header.getChildCount()>0)header.getChildAt(0).requestFocus();
    }
    @Override public void onWindowFocusChanged(boolean has){super.onWindowFocusChanged(has);if(has&&overlay.acceptsInput()&&getCurrentFocus()==null)landing();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        // Closing: navigation keys reach neither Home nor a half-gone sheet; volume, media and other keys still work.
        if(!overlay.acceptsInput()&&NAVIGATION_KEYS.contains(e.getKeyCode()))return true;
        if(e.getAction()==KeyEvent.ACTION_DOWN){View f=getCurrentFocus();
            switch(e.getKeyCode()){
                case KeyEvent.KEYCODE_DPAD_RIGHT:if(overlay.atRoot()){int i=Arrays.asList(rows).indexOf(f);if(i>=0){open(i);return true;}}break;
                // Left with nothing further left is Back (at the root it closes); the header's Close may step left to its Back.
                case KeyEvent.KEYCODE_DPAD_LEFT:if(!(f instanceof EditText)&&!(f!=null&&f.getParent()==header&&f!=header.getChildAt(0))){if(e.getRepeatCount()==0)back();return true;}break;   // a held key never runs out of the overlay
                case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:case KeyEvent.KEYCODE_DPAD_CENTER:if(f==null){landing();return true;}break;
                default:break;
            }
        }
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){if(overlay.phase()==SettingsOverlay.Phase.CLOSED)super.onBackPressed();else back();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("category",overlay.category());out.putString("task",overlay.task());out.putBoolean("wide",overlay.wide());out.putInt("row",overlay.rootRow());}

    // --- Building blocks: header, group labels, cards, rows, switches and text
    private void head(boolean back,String title,String sub){
        if(back)header.addView(icon(SettingsGlass.Glyph.BACK,"Back",this::back),iconParams(0,px(18)));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
        TextView t=label(title,36,SettingsGlass.TEXT);t.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD));words.addView(t);
        subtitle=sub;hint=label(sub==null?"":sub,21,SettingsGlass.SECONDARY);hint.setVisibility(sub==null?View.GONE:View.VISIBLE);words.addView(hint);
        header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(icon(SettingsGlass.Glyph.CLOSE,"Close Settings",this::closeNow),iconParams(px(18),0));
    }
    private LinearLayout.LayoutParams iconParams(int start,int end){int s=px(56);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(s,s);lp.setMarginStart(start);lp.setMarginEnd(end);return lp;}
    /** Icon-only header control: an accessible name, and that name shown in the subtitle line while it has focus. */
    private ImageView icon(int glyph,String name,Runnable action){
        ImageView v=new ImageView(this);v.setImageDrawable(new SettingsGlass.Glyph(glyph,px(26)));v.setScaleType(ImageView.ScaleType.CENTER);v.setContentDescription(name);
        v.setBackground(SettingsGlass.row(px(28),Math.max(2,px(2)),strong(),calm()?0:SettingsOverlay.FOCUS_MS));v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);
        v.setOnClickListener(x->{if(overlay.acceptsInput())action.run();});
        v.setOnFocusChangeListener((x,focused)->{if(hint==null)return;hint.setText(focused?name:subtitle==null?"":subtitle);hint.setVisibility(focused||subtitle!=null?View.VISIBLE:View.GONE);});
        return v;
    }
    private TextView label(String s,float size,int colour){TextView t=new TextView(this);t.setText(s);t.setTextColor(colour);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(size));t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;}
    private void group(String title){TextView t=label(title,18,SettingsGlass.GROUP);t.setLetterSpacing(.06f);t.setPadding(px(12),px(18),px(12),px(6));t.setFocusable(false);body.addView(t);}
    /** A raised translucent card that the following rows and text go into (a local scrim for readability). */
    private void card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(px(12),px(8),px(12),px(8));c.setBackground(SettingsGlass.card(px(18),strong()));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=px(4);lp.bottomMargin=px(10);body.addView(c,lp);column=c;}
    private void section(String title){group(title);card();}
    private LinearLayout.LayoutParams rowParams(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,px(4),0,px(4));return lp;}
    private Drawable rowBackground(){return SettingsGlass.row(px(14),Math.max(2,px(3)),strong(),calm()?0:SettingsOverlay.FOCUS_MS);}
    /** A focusable row, 64 px at 1080p, label on the left; it does nothing once the overlay is closing. */
    private Button row(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(26));b.setTextColor(SettingsGlass.textColours());b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setMinHeight(px(64));b.setMinimumHeight(px(64));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(px(24),px(12),px(24),px(12));
        b.setBackground(rowBackground());b.setStateListAnimator(null);b.setFocusable(true);b.setFocusableInTouchMode(true);
        b.setOnClickListener(v->{if(overlay.acceptsInput())action.run();});
        return b;
    }
    @Override Button button(String title,Runnable action){Button b=row(title,action);column.addView(b,rowParams());return b;}
    /** A row that opens a task or collapsed section inside the same sheet; wider ones expand the sheet to ~75%. */
    private Button taskRow(String title,String task,boolean wider,String state){Button b=button(title,()->expand(task,wider));b.setCompoundDrawablesRelative(null,null,new SettingsGlass.Trailing(state,true,false,textPx(19)),null);b.setCompoundDrawablePadding(px(16));return b;}
    /** Secondary (blue-grey, 21 px) for notes, primary (white, 25 px) for a state line, as the old sizes meant. */
    @Override TextView text(String s,int size){
        TextView t=new TextView(this);t.setText(s);boolean primary=size>=18;t.setTextColor(primary?SettingsGlass.TEXT:SettingsGlass.SECONDARY);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(size>=24?30:primary?25:21));t.setLineSpacing(0,1.18f);t.setPadding(px(12),px(8),px(12),px(8));t.setFocusable(false);column.addView(t);return t;
    }

    // --- Appearance & Home (module 13 §C 01: Wallpaper, Dock & Icons, Ambient background, Clock, Readability, Advanced)
    private void appearance(){
        section("Wallpaper");
        button("Gallery & slideshow",()->startActivity(new Intent(this,GalleryActivity.class).putExtra("edit",true)));
        wallpaper();
        section("Dock & Icons");
        // Dock size (SET-027, 6 or 8 icons) and "Edit dock" go first in this card; DockCapacity is being built separately.
        String key=DockLabels.key(Profiles.current(prefs)),labels=DockLabels.value(prefs.getString(key,DockLabels.FOCUSED_ONLY));
        button("App label visibility: "+DockLabels.label(labels),()->{String[] names=new String[DockLabels.VALUES.length];int now=0;for(int i=0;i<names.length;i++){names[i]=DockLabels.label(DockLabels.VALUES[i]);if(DockLabels.VALUES[i].equals(labels))now=i;}
            new AlertDialog.Builder(this).setTitle("App label visibility").setSingleChoiceItems(names,now,(d,n)->{prefs.edit().putString(key,DockLabels.VALUES[n]).apply();d.dismiss();show(APPEARANCE);}).setNegativeButton("Cancel",null).show();});
        text(DockLabels.FOCUSED_ONLY.equals(labels)?"Only the app in focus is named, under its tile.":DockLabels.ALWAYS.equals(labels)?"Every app on Home is named under its tile.":"No names under the tiles; the app in focus is named above the dock.",15);
        text("For "+Profiles.label(Profiles.current(prefs))+" only: each profile keeps its own choice. Tiles always show simple symbols, never names.",15);
        section("Ambient background");
        toggle("Reduce motion","reduceMotion",on->restyle());toggle("Keep the Home background still","stillBackground");toggle("Lightweight rendering","lightweight",on->restyle());
        section("Clock");
        String zone=prefs.getString(HouseholdSettings.ZONE,"");
        button("Time zone: "+(zone.isEmpty()?"Follow this TV ("+java.time.ZoneId.systemDefault().getId()+")":zone),this::chooseZone);
        text("The clock is "+(HouseholdSettings.twentyFour(this)?"24-hour":"12-hour")+", following this TV's own setting; words and dates follow its language.",15);
        section("Readability");
        toggle("High contrast","highContrast",on->restyle());toggle("Reduce transparency","opaque",on->restyle());
        section("Home navigation");
        taskRow("Navigation & Shortcuts",NAVIGATION,true,draftUtilities==null?null:"Preview");
        if(DefaultHome.available(this)){section("Home button");
            text(DefaultHome.isDefault(this)?"The Home button opens Unified TV.":"The Home button opens the original TV interface.",16);
            if(!DefaultHome.isDefault(this))button("Make Unified TV the Home screen",()->EditLock.ask(this,this::homeSetup));}
        else{section("Home button");text("This is the Unified TV preview. It never changes the Home button; the separate Unified TV app can.",15);}
        section("Advanced appearance");
        taskRow("Advanced appearance",APPEARANCE_ADVANCED,false,null);
    }
    /** Reduce motion, transparency, contrast or lightweight changed: the sheet and its rows follow at once (posted, so a
     *  switch is never removed while its own click is still running). */
    private void restyle(){material();body.post(()->show(overlay.category()));}
    private void appearanceAdvanced(){
        button("Reset appearance",()->new AlertDialog.Builder(this).setTitle("Reset appearance?").setMessage("Motion, contrast, transparency and lightweight rendering go back to their defaults, and app labels to Focused only for "+Profiles.label(Profiles.current(prefs))+". Your dock, Wallpaper and connections are kept.")
            .setPositiveButton("Reset",(d,w)->{prefs.edit().remove("reduceMotion").remove("stillBackground").remove("highContrast").remove("opaque").remove("lightweight").putString(DockLabels.key(Profiles.current(prefs)),DockLabels.FOCUSED_ONLY).apply();restyle();show(APPEARANCE);}).setNegativeButton("Cancel",null).show());
        text("Only Unified TV's own look changes. The TV's picture settings and its own screen protection are never touched.",15);
    }
    /** SET-090–093, a wider task: utilities shown, hidden or moved in a preview; nothing changes on Home until "Apply". */
    private void navigation(){
        text("Up: the utilities · Down: your films and TV · Left and Right: along a row · Back: the dock. These directions are fixed.",15);
        List<String> saved=HomeUtilities.order(prefs.getString(HomeUtilities.PREF,""));
        text("Utilities, left to right: "+names(saved),16);
        if(draftUtilities!=null&&!draftUtilities.equals(saved)){
            text("Preview (not applied yet): "+names(draftUtilities),16);
            button("Apply navigation changes",()->{prefs.edit().putString(HomeUtilities.PREF,HomeUtilities.write(draftUtilities)).apply();draftUtilities=null;note("Saved. Home uses the new order.");show(APPEARANCE);});
            button("Discard changes",()->{draftUtilities=null;show(APPEARANCE);});
        }
        button("Change utilities",this::utilities);
        String landing=prefs.getString(HomeUtilities.LANDING,"continue");
        button("Down lands on: "+("first".equals(landing)?"The first title":"Continue Watching, when there is one"),()->new AlertDialog.Builder(this).setTitle("Down from the dock lands on").setItems(new String[]{"Continue Watching, when there is one","The first title"},(d,n)->{prefs.edit().putString(HomeUtilities.LANDING,n==0?"continue":"first").apply();show(APPEARANCE);}).show());
        button("Reset navigation to default",()->new AlertDialog.Builder(this).setTitle("Reset navigation?").setMessage("The utilities go back to AirPlay, NordVPN, voice and Settings, and Down lands on Continue Watching again. Nothing else changes.")
            .setPositiveButton("Reset",(d,w)->{prefs.edit().remove(HomeUtilities.PREF).remove(HomeUtilities.LANDING).apply();draftUtilities=null;show(APPEARANCE);}).setNegativeButton("Cancel",null).show());
        text("Items this TV doesn't have (AirPin LITE, NordVPN) are left out of Home. Settings always stays, so there is always a way back here.",15);
    }
    private static String names(List<String> order){List<String> n=new ArrayList<>();for(String id:order)n.add(HomeUtilities.name(id));return String.join(" · ",n);}
    /** SET-090/093: show, hide or move one utility in a preview; nothing changes on Home until "Apply". */
    private void utilities(){
        List<String> base=draftUtilities!=null?draftUtilities:HomeUtilities.order(prefs.getString(HomeUtilities.PREF,""));
        String[] rows=new String[HomeUtilities.DEFAULT.size()];
        for(int i=0;i<rows.length;i++){String id=HomeUtilities.DEFAULT.get(i);rows[i]=HomeUtilities.name(id)+(base.contains(id)?"  ·  shown, place "+(base.indexOf(id)+1):"  ·  hidden")+(id.equals(HomeUtilities.SETTINGS)?" (always shown)":"");}
        new AlertDialog.Builder(this).setTitle("Change utilities").setItems(rows,(d,n)->{String id=HomeUtilities.DEFAULT.get(n);
            List<String> actions=new ArrayList<>();if(!id.equals(HomeUtilities.SETTINGS))actions.add(base.contains(id)?"Hide":"Show");if(base.contains(id)){actions.add("Move left");actions.add("Move right");}actions.add("Cancel");
            new AlertDialog.Builder(this).setTitle(HomeUtilities.name(id)).setItems(actions.toArray(new String[0]),(d2,m)->{String a=actions.get(m);
                if(a.equals("Hide")||a.equals("Show"))draftUtilities=HomeUtilities.toggle(base,id);else if(a.equals("Move left"))draftUtilities=HomeUtilities.move(base,id,-1);else if(a.equals("Move right"))draftUtilities=HomeUtilities.move(base,id,1);
                show(APPEARANCE);}).show();}).show();
    }
    private void chooseZone(){
        List<String> zones=HouseholdSettings.zones(java.time.ZoneId.getAvailableZoneIds());zones.add(0,"Follow this TV");
        new AlertDialog.Builder(this).setTitle("Time zone").setItems(zones.toArray(new String[0]),(d,n)->{if(n==0)prefs.edit().remove(HouseholdSettings.ZONE).apply();else prefs.edit().putString(HouseholdSettings.ZONE,zones.get(n)).apply();show(APPEARANCE);}).show();
    }

    // --- Profiles & Family
    private void profiles(){
        // The quick switch lives here (and in the voice command "switch profile"), not on Home's utility pill (UX-UTILITY-01).
        button("Switch profile (now "+Profiles.label(Profiles.current(prefs))+")",()->Profiles.choose(this));
        button("Set or change adult PIN",()->EditLock.ask(this,()->EditLock.set(this)));
        button("Kids settings",()->EditLock.ask(this,()->startActivity(new Intent(this,KidsActivity.class))));
        KidsPolicy.Scheme scheme=HouseholdSettings.scheme(this);
        button("Kids ratings: "+scheme.label,()->EditLock.ask(this,()->{KidsPolicy.Scheme[] all=KidsPolicy.Scheme.values();String[] names=new String[all.length];for(int i=0;i<all.length;i++)names[i]=all[i].label+": "+all[i].allowedWords();
            new AlertDialog.Builder(this).setTitle("Ratings Kids may watch").setItems(names,(d,n)->{prefs.edit().putString(HouseholdSettings.RATINGS,all[n].id).apply();show(PROFILES);}).show();}));
        text("Kids gets only "+scheme.allowedWords()+" where the media server sends a rating; anything else rated is left out.",15);
        text("Unified TV opens in the profile used last. Moving to Adults always asks for the adult PIN; Kids can be switched on once a PIN is set. You can also say \"switch profile\" to Ask your TV.",16);
        text("Kids limits what Unified TV shows and opens: chosen NAS folders, approved apps and an optional app guard. It is not a lock on Android and doesn't switch streaming-app accounts; Kids settings says exactly what it can and can't do.",16);
        section("Forgot the PIN?");
        text("An adult can reset it from a computer with ADB access to this TV. This clears the PIN, returns to Adults and switches the Kids app guard off; nothing else changes:",15);
        TextView command=text(PinReset.command(getPackageName()),15);command.setTypeface(android.graphics.Typeface.MONOSPACE);command.setTextIsSelectable(false);
    }

    // --- Notifications & Doorbell
    private boolean haConfigured(){try{org.json.JSONObject ha=new Vault(this).read("ha");return ha.optBoolean("enabled")&&!ha.optString("token").isEmpty();}catch(Exception e){return false;}}
    private void notifications(){
        boolean ha=haConfigured();
        AlarmWatch.State alarms=AlarmWatch.state(prefs.getBoolean(AlarmWatch.SHOWN,true),prefs.getBoolean(AlarmWatch.STOPPED,false),ha,prefs.getInt(AlarmWatch.DETECTORS,-1));
        TextView status=text(alarms==AlarmWatch.State.OFF?AlarmWatch.OFF_NOTICE:alarms==AlarmWatch.State.NOT_CONFIGURED?AlarmWatch.NOT_CONFIGURED_TEXT:"Smoke and carbon monoxide alarms: shown on this TV",19);
        status.setFocusable(false);text(AlarmWatch.words(alarms),15);
        if(!ha){text("Doorbell and alarm alerts come from Home Assistant, which isn't set up on this TV.",16);
            button("Set up Home Assistant",()->EditLock.ask(this,()->connection("ha")));}
        button("Doorbell, reminders & room setup",()->EditLock.ask(this,()->startActivity(new Intent(this,HouseholdActivity.class))));
        if(ha){
            section("Doorbell camera");
            String close=DoorbellCamera.close(prefs.getString(DoorbellCamera.CLOSE,""));
            button("Closes: "+DoorbellCamera.closeLabel(close),()->{String[] names=new String[DoorbellCamera.CLOSES.length];int now=0;for(int i=0;i<names.length;i++){names[i]=DoorbellCamera.closeLabel(DoorbellCamera.CLOSES[i]);if(DoorbellCamera.CLOSES[i].equals(close))now=i;}
                new AlertDialog.Builder(this).setTitle("Doorbell camera closes").setSingleChoiceItems(names,now,(d,n)->{prefs.edit().putString(DoorbellCamera.CLOSE,DoorbellCamera.CLOSES[n]).apply();d.dismiss();show(NOTIFICATIONS);}).setNegativeButton("Cancel",null).show();});
            String layout=DoorbellCamera.layout(prefs.getString(DoorbellCamera.LAYOUT,""));
            button("Layout: "+DoorbellCamera.layoutLabel(layout),()->{String[] names={DoorbellCamera.layoutLabel(DoorbellCamera.OVERLAY),DoorbellCamera.layoutLabel(DoorbellCamera.FULL)};
                new AlertDialog.Builder(this).setTitle("Doorbell camera layout").setSingleChoiceItems(names,DoorbellCamera.FULL.equals(layout)?1:0,(d,n)->{prefs.edit().putString(DoorbellCamera.LAYOUT,DoorbellCamera.LAYOUTS[n]).apply();d.dismiss();show(NOTIFICATIONS);}).setNegativeButton("Cancel",null).show();});
            text("A press opens the live camera; Back closes it at any time. Whether it can appear over other apps depends on this TV and is not yet tested on it.",15);
            // Owner, 25 October 2026: person toasts can be kept off the screen while Kids is watching. Doorbell presses and
            // smoke or carbon monoxide alarms are never affected, in any profile.
            toggle("Show person alerts in Kids",AlertPolicy.KIDS_PERSON_PREF,true,on->{});
            text("Doorbell presses and smoke or carbon monoxide alarms show whoever is watching.",15);
        }
        section("Household alerts");
        if(prefs.getBoolean(AlarmWatch.STOPPED,false))button("Start household alerts again",()->EditLock.ask(this,()->{prefs.edit().putBoolean(AlarmWatch.STOPPED,false).apply();
            if(Settings.canDrawOverlays(this))try{startForegroundService(new Intent(this,HouseholdService.class));}catch(RuntimeException ignored){}note("Household alerts started.");show(NOTIFICATIONS);}));
        else button("Stop household alerts",this::stopAlerts);
        text("Smoke and carbon monoxide alarms come first, then the doorbell. They add to your alarms and never replace them. Native Samsung and third-party protected-video behaviour require separate device tests.",15);
    }
    /** Owner exception to SET-053: stopping alerts takes the adult PIN and a confirmation, and Home then says alarms are off. */
    private void stopAlerts(){
        EditLock.ask(this,()->new AlertDialog.Builder(this).setTitle("Stop household alerts?").setMessage("Smoke and carbon monoxide alarms and the doorbell will not appear on this TV until an adult starts alerts again. Home will show \""+AlarmWatch.OFF_NOTICE+"\" meanwhile.")
            .setPositiveButton("Stop alerts",(d,w)->{prefs.edit().putBoolean(AlarmWatch.STOPPED,true).apply();stopService(new Intent(this,HouseholdService.class));note("Household alerts stopped");if(overlay.category()==NOTIFICATIONS)show(NOTIFICATIONS);})
            .setNegativeButton("Keep alerts on",null).show());
    }

    // --- Smart Home
    private void smartHome(){
        boolean ha=haConfigured();
        if(ha)button("Home Assistant",()->EditLock.ask(this,()->connection("ha")));
        else{text("Home Assistant isn't set up. It brings doorbell, alarm and room features to this TV.",16);button("Set up Home Assistant",()->EditLock.ask(this,()->connection("ha")));}
        section("Your team");
        boolean api=FixtureFeed.apiConfigured(this);Fixtures.Team team=FixtureFeed.team(this);
        if(!api){text("Football fixtures need a service. API-Football works with your own free key from api-football.com; it is kept encrypted on this TV and never shown again.",15);
            if(!Profiles.kids(this))button("Add a service: Football fixtures (API-Football)",()->EditLock.ask(this,this::addKey));}
        else{ApiFootball.Budget b=FixtureFeed.budget(this);text("Football fixtures: API-Football. Requests today: "+b.used+" of "+ApiFootball.DAILY_LIMIT+".",15);}
        button("Your team: "+(team==null?"none":team.displayName),()->EditLock.ask(this,this::chooseTeam));
        if(team!=null){
            button("Badge: "+team.shortName+" on "+Fixtures.colourName(team.primary)+" and "+Fixtures.colourName(team.secondary),()->badgeColours(team));
            if(!team.id.startsWith("api:"))button("Other names in the calendar: "+(team.aliases.isEmpty()?"none":String.join(", ",team.aliases)),()->aliases(team));
            button("No team",()->{FixtureFeed.saveTeam(this,null);show(SMART_HOME);});
        }
        text("The matchday card appears on Home only on matchday: the match, the kick-off time or \"Live\". Scores are never shown. The badge is drawn by Unified TV from your team's short name and colours; no club crest is used. Without API-Football, a fixtures calendar chosen in household setup is used.",15);
        if(api&&!Profiles.kids(this))button("Remove API-Football",()->EditLock.ask(this,()->new AlertDialog.Builder(this).setTitle("Remove API-Football?").setMessage("The key and the fixtures kept on this TV are deleted.").setPositiveButton("Remove",(d,w)->{FixtureFeed.forgetKey(this);show(SMART_HOME);}).setNegativeButton("Cancel",null).show()));
    }
    private void addKey(){
        EditText field=new EditText(this);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);field.setHint("Your API-Football key");field.setSaveEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        new AlertDialog.Builder(this).setTitle("Football fixtures (API-Football)").setMessage("Enter the key from your API-Football account. It is used only to read fixtures, at most 100 requests a day.").setView(field)
            .setPositiveButton("Save",(d,w)->{String key=field.getText().toString().trim();field.setText("");
                if(!key.matches("[A-Za-z0-9]{16,64}")){note("That doesn't look like an API-Football key. Nothing was saved.");return;}
                try{FixtureFeed.saveKey(this,key);note("Saved. Now choose your team.");}catch(Exception e){note("Couldn't save on this TV. Nothing was changed.");}show(SMART_HOME);})
            .setNegativeButton("Cancel",null).show();
    }
    /** API-Football: country, league, then team, from the list read from the API (kept a week). Otherwise a name for the calendar's team. */
    private void chooseTeam(){
        if(!FixtureFeed.apiConfigured(this)){
            EditText field=new EditText(this);field.setSingleLine(true);field.setHint("Your team's name, as you'd like it shown");Fixtures.Team t=FixtureFeed.team(this);if(t!=null)field.setText(t.displayName);
            new AlertDialog.Builder(this).setTitle("Your team").setMessage("Used with the fixtures calendar chosen in household setup.").setView(field).setPositiveButton("Save",(d,w)->{String name=field.getText().toString().trim();
                FixtureFeed.saveTeam(this,name.isEmpty()?null:new Fixtures.Team("calendar",name,"",t==null?Fixtures.COLOURS[0]:t.primary,t==null?Fixtures.COLOURS[1]:t.secondary,Fixtures.Sport.FOOTBALL,t==null?null:t.aliases));show(SMART_HOME);}).setNegativeButton("Cancel",null).show();
            return;
        }
        org.json.JSONObject cat=FixtureFeed.catalogue(this);
        if(cat.optLong("fetchedAt")>0){pickCountry(cat);if(ApiFootball.catalogueDue(cat.optLong("fetchedAt"),System.currentTimeMillis()))buildTeams(null);return;}
        AlertDialog wait=new AlertDialog.Builder(this).setTitle("Reading the team list").setMessage("Fetching leagues and teams for England, Scotland, Wales and Northern Ireland. This takes a few minutes the first time and is kept for a week.").setNegativeButton("Close",null).show();
        buildTeams(wait);
    }
    private void buildTeams(AlertDialog wait){
        if(teamWorker!=null&&teamWorker.isAlive())return;
        android.content.Context app=getApplicationContext();
        teamWorker=new Thread(()->FixtureFeed.build(app,(cat,problem)->runOnUiThread(()->{
            if(wait==null||!wait.isShowing())return;
            int[] p=ApiFootball.progress(cat);
            if(problem!=null)wait.setMessage(problem+(p[0]>0?"\n\nTeams from "+p[0]+" of "+p[1]+" leagues are ready to choose from.":""));
            else if(cat.optLong("fetchedAt")>0){wait.dismiss();pickCountry(cat);}
            else wait.setMessage("Read "+p[0]+" of "+p[1]+" leagues…");
            if(problem!=null&&p[0]>0)wait.setButton(AlertDialog.BUTTON_POSITIVE,"Choose from these",(d,w)->pickCountry(cat));
        })),"team-list");
        teamWorker.setDaemon(true);teamWorker.start();
    }
    private void pickCountry(org.json.JSONObject cat){
        String[] countries=ApiFootball.COUNTRIES;String[] names=new String[countries.length];for(int i=0;i<names.length;i++)names[i]=countries[i].replace('-',' ');
        new AlertDialog.Builder(this).setTitle("Your team's country").setItems(names,(d,n)->pickLeague(cat,countries[n])).show();
    }
    private void pickLeague(org.json.JSONObject cat,String country){
        org.json.JSONArray all=cat.optJSONArray("leagues");List<org.json.JSONObject> leagues=new ArrayList<>();
        if(all!=null)for(int i=0;i<all.length();i++){org.json.JSONObject l=all.optJSONObject(i);if(l!=null&&country.equalsIgnoreCase(l.optString("country").replace(' ','-'))&&l.optJSONArray("teams")!=null&&l.optJSONArray("teams").length()>0)leagues.add(l);}
        if(leagues.isEmpty()){note("No teams read for "+country.replace('-',' ')+" yet.");return;}
        String[] names=new String[leagues.size()];for(int i=0;i<names.length;i++)names[i]=leagues.get(i).optString("name");
        new AlertDialog.Builder(this).setTitle("League").setItems(names,(d,n)->pickTeam(leagues.get(n))).show();
    }
    private void pickTeam(org.json.JSONObject league){
        org.json.JSONArray teams=league.optJSONArray("teams");String[] names=new String[teams.length()];for(int i=0;i<names.length;i++)names[i]=teams.optJSONObject(i).optString("name");
        new AlertDialog.Builder(this).setTitle(league.optString("name")).setItems(names,(d,n)->{org.json.JSONObject t=teams.optJSONObject(n);Fixtures.Team old=FixtureFeed.team(this);
            FixtureFeed.saveTeam(this,new Fixtures.Team("api:"+t.optInt("id"),t.optString("name"),t.optString("code"),old==null?Fixtures.COLOURS[0]:old.primary,old==null?Fixtures.COLOURS[1]:old.secondary,Fixtures.Sport.FOOTBALL,null));
            note("Saved. Choose the badge colours next.");show(SMART_HOME);}).show();
    }
    private void badgeColours(Fixtures.Team t){
        new AlertDialog.Builder(this).setTitle("Badge: main colour").setItems(Fixtures.COLOUR_NAMES,(d,a)->new AlertDialog.Builder(this).setTitle("Badge: second colour").setItems(Fixtures.COLOUR_NAMES,(d2,b)->{
            FixtureFeed.saveTeam(this,new Fixtures.Team(t.id,t.displayName,t.shortName,Fixtures.COLOURS[a],Fixtures.COLOURS[b],t.sport,t.aliases));show(SMART_HOME);}).show()).show();
    }
    private void aliases(Fixtures.Team t){
        EditText field=new EditText(this);field.setSingleLine(true);field.setText(String.join(", ",t.aliases));field.setHint("Other names, separated by commas");
        new AlertDialog.Builder(this).setTitle("Other names in the calendar").setView(field).setPositiveButton("Save",(d,w)->{List<String> a=new ArrayList<>();for(String x:field.getText().toString().split(","))if(!x.trim().isEmpty())a.add(x.trim());
            FixtureFeed.saveTeam(this,new Fixtures.Team(t.id,t.displayName,t.shortName,t.primary,t.secondary,t.sport,a));show(SMART_HOME);}).setNegativeButton("Cancel",null).show();
    }

    // --- Live TV & Network Protection (SET-063: automatic verification only)
    private void liveTv(){
        String live=LiveTvProtection.livePackage(this);LiveTvProtection.State state=LiveTvProtection.now(this,live);
        TextView word=text("Live TV: "+LiveTvProtection.word(state),20);word.setFocusable(false);
        text(LiveTvProtection.message(state),16);
        text(live.isEmpty()?"No single Live TV app was recognised on this TV.":"Live TV app: "+live,15);
        button("Check again",()->{VpnGate.invalidate();show(LIVE_TV);});
        button("NordVPN",()->VpnGate.show(this));
        text("Unified TV unlocks Live TV only after checking by itself, every time, that Live TV can only connect through NordVPN. A VPN icon, a setting or an adult's word is never enough. This TV gives apps no way to make that check, so Live TV stays locked here; nothing in Settings can bypass it.",15);
    }

    // --- Updates & About
    private void about(){
        String version="unknown";long code=0;
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);version=info.versionName==null?"unknown":info.versionName;code=info.getLongVersionCode();}catch(Exception ignored){}
        boolean debug=(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        text("Version "+version+" ("+code+")"+(debug?", test build":""),18);
        text(About.flavour(getPackageName()),16);
        text("Package: "+getPackageName(),15);
        text("Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+") on "+Build.MANUFACTURER+" "+Build.MODEL+" · build "+Build.DISPLAY,15);
        text("Updates install over this app only when signed with the same key. The preview and the launcher are separate apps; one never upgrades the other.",15);
        section("Licences & artwork");
        button("Open-source licences",()->new AlertDialog.Builder(this).setTitle("Open-source licences").setMessage(About.licences()).setPositiveButton("Close",null).show());
        button("Artwork",()->{String json="";try(java.io.InputStream in=getAssets().open("wallpaper/catalogue.json")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);json=out.toString("UTF-8");}catch(Exception ignored){}
            List<String> lines=About.artwork(json);new AlertDialog.Builder(this).setTitle("Artwork").setMessage(lines.isEmpty()?"The artwork list couldn't be read.":String.join("\n\n",lines)).setPositiveButton("Close",null).show();});
        section("Advanced");
        if(Profiles.kids(this)){text("Advanced is for Adults.",15);return;}
        taskRow("Advanced",ADVANCED,true,null);
    }
    /** Collapsed Advanced (module 13 §C 10), a wider task: read-only owner checks; never on Home, never in Kids. */
    private void advanced(){
        if(Profiles.kids(this)){text("Advanced is for Adults.",15);return;}
        // Read-only checks for the owner; never on Home, never in Kids, always behind the PIN.
        button("Owner diagnostics",()->EditLock.ask(this,()->startActivity(new Intent(this,OwnerDiagnosticsActivity.class))));
        button("Performance check",()->startActivity(new Intent(this,PerformanceActivity.class)));
        // Spec 6.1: hardware key logging only in owner-authorised debug builds (the review build is one).
        if(RemoteCheckActivity.allowed(this))button("Check your remote's buttons",()->startActivity(new Intent(this,RemoteCheckActivity.class)));
        text("No automatic cleanup or factory package changes are performed.",15);
        // Spec 30: deliberately small and last; adult only; plain words. Only in the app that can be Home.
        if(DefaultHome.available(this)){Button original=button("Open original TV interface",()->EditLock.ask(this,this::original));original.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(21));original.setMinHeight(0);original.setMinimumHeight(px(52));original.setAlpha(.85f);}
    }
    /** Spec 7.1: AirPlay through AirPin LITE. Setup happens in AirPin itself; Unified TV only reports what it can confirm. */
    private void airPlay(){
        String pkg=AirPlayReceiver.installed(this);
        if(pkg==null){text("AirPlay from iPhone: install AirPin LITE from the TV's app store to receive AirPlay.",16);return;}
        text(AirPlayReceiver.ready?"AirPlay from iPhone: ready"+(AirPlayReceiver.advertisedAs.isEmpty()?".":", shown on iPhones as \""+AirPlayReceiver.advertisedAs+"\"."):"AirPlay from iPhone: AirPin LITE is installed but isn't offering AirPlay on the network right now. Open it once, and check its own setting to start with the TV.",16);
        button("Open AirPin LITE",()->{String error=Launch.open(this,pkg,"AirPin LITE");if(error!=null&&!error.isEmpty())note(error);});
    }
    /**
     * Spec 33: Settings → Appearance → Wallpaper. Artwork is bundled; the clock follows the picture (OD-03, OD-04). My Photos
     * (docs/design/MY_PHOTOS.md) is offered to the adult profile only, after a privacy confirmation; Child sees Artwork.
     */
    private void wallpaper(){
        SharedPreferences p=getSharedPreferences("unified-tv-preview",0);
        String profile=p.getString("profile",WallpaperPolicy.ADULT);boolean adult=WallpaperPolicy.photosAllowed(profile);
        String saved=WallpaperPolicy.source(p.getString("wallpaperSource",null)),source=WallpaperPolicy.sourceFor(saved,profile),clock=WallpaperPolicy.clock(p.getString("wallpaperClock",null));
        int idle=WallpaperPolicy.idle(p.getInt("wallpaperIdle",-1)),slide=WallpaperPolicy.slide(p.getInt("wallpaperSlide",0));
        text("Wallpaper: pictures and a clock when Home is left alone. Not Samsung Art Mode.",17);
        String[] sources=adult?new String[]{"Artwork","My Photos","Off"}:new String[]{"Artwork","Off"};
        button("Wallpaper: "+WallpaperPolicy.sourceLabel(source),()->choose("Wallpaper",sources,n->{String pick=sources[n];
            if(pick.equals("My Photos"))EditLock.ask(this,this::myPhotos);else p.edit().putString("wallpaperSource",pick.equals("Off")?WallpaperPolicy.OFF:WallpaperPolicy.ARTWORK).apply();}));
        if(WallpaperPolicy.PHOTOS.equals(saved)&&!adult)text("Kids shows Artwork, never personal photos.",16);
        if(WallpaperPolicy.PHOTOS.equals(source)){
            text(WallpaperPolicy.PHOTOS_LIMIT,16);
            int chosen=0;try{org.json.JSONArray r=new Vault(this).read("photos").optJSONArray("roots");chosen=r==null?0:r.length();}catch(Exception ignored){}
            button(chosen==0?"Choose photo folders":"Photo folders: "+chosen+" chosen",()->EditLock.ask(this,()->startActivity(new Intent(this,PhotoFoldersActivity.class))));
            toggle("Keep a copy for when the NAS is off","wallpaperKeepPhotos",on->{if(!on)new Thread(()->new PhotoCache(new java.io.File(getNoBackupFilesDir(),"photos"),PhotoCache.LIMIT).clear(),"photo-cache").start();});
            text("Copies stay on this TV only, up to about 200 MB. Without them, Artwork shows while the NAS is off.",15);
        }
        button("Clock: "+WallpaperPolicy.clockLabel(clock),()->choose("Clock",new String[]{WallpaperPolicy.clockLabel(WallpaperPolicy.AUTO),WallpaperPolicy.clockLabel(WallpaperPolicy.SIMPLE),WallpaperPolicy.clockLabel(WallpaperPolicy.NONE)},n->p.edit().putString("wallpaperClock",new String[]{WallpaperPolicy.AUTO,WallpaperPolicy.SIMPLE,WallpaperPolicy.NONE}[n]).apply()));
        String[] idles=new String[WallpaperPolicy.IDLE_MINUTES.length];for(int i=0;i<idles.length;i++)idles[i]=WallpaperPolicy.idleLabel(WallpaperPolicy.IDLE_MINUTES[i]);
        button("Start: "+WallpaperPolicy.idleLabel(idle).toLowerCase(java.util.Locale.ROOT),()->choose("Start the Wallpaper",idles,n->p.edit().putInt("wallpaperIdle",WallpaperPolicy.IDLE_MINUTES[n]).apply()));
        String[] slides=new String[WallpaperPolicy.SLIDE_SECONDS.length];for(int i=0;i<slides.length;i++)slides[i]=WallpaperPolicy.slideLabel(WallpaperPolicy.SLIDE_SECONDS[i]);
        button("Change picture every "+WallpaperPolicy.slideLabel(slide),()->choose("Change picture every",slides,n->p.edit().putInt("wallpaperSlide",WallpaperPolicy.SLIDE_SECONDS[n]).apply()));
        if(!WallpaperPolicy.OFF.equals(source))button("Start Wallpaper now",()->startActivity(new Intent(this,WallpaperActivity.class)));
    }
    /** Spec 33.2.6: a clear local privacy confirmation before personal photos appear on a shared TV. */
    private void myPhotos(){
        new AlertDialog.Builder(this).setTitle("Show my photos on this TV?").setMessage("Only the folders you choose are shown, and only in Adults; Kids shows Artwork. Nothing is uploaded.\n\n"+WallpaperPolicy.PHOTOS_LIMIT)
            .setPositiveButton("Show my photos",(d,w)->{getSharedPreferences("unified-tv-preview",0).edit().putString("wallpaperSource",WallpaperPolicy.PHOTOS).apply();
                int chosen=0;try{org.json.JSONArray r=new Vault(this).read("photos").optJSONArray("roots");chosen=r==null?0:r.length();}catch(Exception ignored){}
                if(chosen==0)startActivity(new Intent(this,PhotoFoldersActivity.class));if(overlay.category()==APPEARANCE)show(APPEARANCE);})
            .setNegativeButton("Cancel",null).show();
    }
    private void choose(String title,String[] options,java.util.function.IntConsumer pick){
        new AlertDialog.Builder(this).setTitle(title).setItems(options,(d,n)->{pick.accept(n);show(overlay.category());}).show();
    }
    private void toggle(String name,String key){toggle(name,key,on->{});}
    private void toggle(String name,String key,java.util.function.Consumer<Boolean> after){toggle(name,key,false,after);}
    /** Module 13 §D: one clear binary row; the label on the left, its state as a word (On/Off) on the right. */
    private void toggle(String name,String key,boolean byDefault,java.util.function.Consumer<Boolean> after){
        CheckBox c=new CheckBox(this);c.setText(name);c.setButtonDrawable(null);c.setTextColor(SettingsGlass.textColours());c.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(26));c.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        c.setMinHeight(px(64));c.setMinimumHeight(px(64));c.setPadding(px(24),px(12),px(24),px(12));c.setBackground(rowBackground());c.setStateListAnimator(null);
        c.setCompoundDrawablesRelative(null,null,new SettingsGlass.Trailing(null,false,true,textPx(19)),null);c.setCompoundDrawablePadding(px(16));
        c.setChecked(getSharedPreferences("unified-tv-preview",0).getBoolean(key,byDefault));c.setFocusable(true);c.setFocusableInTouchMode(true);
        c.setOnCheckedChangeListener((v,on)->{getSharedPreferences("unified-tv-preview",0).edit().putBoolean(key,on).apply();after.accept(on);});column.addView(c,rowParams());
    }
    /** Spec 28.3 onboarding: explain, let Android ask, then confirm what actually happened. */
    private void homeSetup(){
        new AlertDialog.Builder(this).setTitle("Make Unified TV the Home screen").setMessage("Pressing Home will open Unified TV instead of the original TV interface. The original interface stays on the TV, and you can switch back at any time from this screen.\n\nYour TV will ask you to choose: pick Unified TV.")
            .setPositiveButton("Continue",(d,w)->{homeAsked=true;settingsPass();DefaultHome.offer(this);}).setNegativeButton("Cancel",null).show();
    }
    private boolean homeAsked;
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==DefaultHome.REQUEST)homeChecked();}
    @Override protected void onResume(){super.onResume();if(homeAsked)homeChecked();}
    private void homeChecked(){
        homeAsked=false;boolean yes=DefaultHome.isDefault(this);
        // Not chosen: leave the TV exactly as it was, with no stray Home entry.
        if(!yes&&DefaultHome.offered(this))DefaultHome.withdraw(this);
        note(yes?"Done. The Home button now opens Unified TV.":"Nothing changed. The Home button still opens the original TV interface.");if(overlay.category()==APPEARANCE)show(APPEARANCE);
    }
    private void original(){
        boolean ours=DefaultHome.isDefault(this);
        java.util.List<String> choices=new java.util.ArrayList<>();choices.add("Open original interface for now");if(ours)choices.add("Make original interface the default");choices.add("Cancel");
        new AlertDialog.Builder(this).setTitle("Original TV interface").setItems(choices.toArray(new String[0]),(d,n)->{
            String pick=choices.get(n);
            if(pick.startsWith("Open")){settingsPass();if(!DefaultHome.openOriginal(this))new AlertDialog.Builder(this).setMessage("This TV doesn't let Unified TV open the original interface directly."+(ours?" Choose 'Make original interface the default' instead.":" Press the Home button on your remote to get there.")).setPositiveButton("OK",null).show();}
            else if(pick.startsWith("Make"))new AlertDialog.Builder(this).setTitle("Make original interface the default").setMessage("From now on, pressing Home will open the original TV interface until you choose Unified TV again in Settings > Appearance & Home.")
                .setPositiveButton("Make it the default",(d2,w2)->{DefaultHome.withdraw(this);note("The Home button now opens the original TV interface.");show(overlay.category());}).setNegativeButton("Cancel",null).show();
        }).show();
    }
    private void connection(String key){startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key",key));}
    /** An adult is here behind the PIN: if Kids is on, the Kids guard lets system screens opened from here stay a while. */
    private void settingsPass(){if(Profiles.kids(this))getSharedPreferences("unified-tv-preview",0).edit().putLong(KidsGuard.PASS,System.currentTimeMillis()+KidsGuard.PASS_MS).apply();}
}
