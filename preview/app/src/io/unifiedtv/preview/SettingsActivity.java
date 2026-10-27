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
    private static final String[] BLURBS={"Make it yours","Your films and shows","Who's watching, the adult PIN and Kids","Choose what lives on Home","Doorbell, reminders and smoke or carbon monoxide alarms","Home Assistant and your team's fixtures","Live TV opens when NordVPN is connected","This TV's own settings, AirPlay and voice","What's kept on this TV and how it's protected","Version, licences and advanced checks"};
    /** Module 13 §C 00: small group labels over the root rows (module 12 order: 4, 4, 2). */
    private static final String[] GROUPS={"Personalise","Connected services","System"};private static final int[] GROUP_START={0,4,8};
    /** Tasks inside a category: a wider sheet for complex ones, the same width for a collapsed Advanced section. */
    static final String NAVIGATION="navigation",APPEARANCE_ADVANCED="appearance-advanced",ADVANCED="advanced";
    /** Tasks about this TV (DevicePage): what it can do (SET-006, Updates & About) and what the remote's buttons do (SET-071). */
    static final String CAPABILITIES="capabilities",REMOTE_HELP="remote-help";
    /** Setup tasks (SetupPages): phone setup per service ("phone-" + PhoneService id), Home Assistant's Advanced, the team
     *  picker and household setup. All inside this sheet: no setup screen is a full-screen page (OD-19). */
    static final String PHONE_HA="phone-"+PhoneService.HA,PHONE_FOOTBALL="phone-"+PhoneService.FOOTBALL,PHONE_TMDB="phone-"+PhoneService.TMDB,
        HA_ADVANCED="ha-advanced",TEAM="team",HOUSEHOLD="household";
    /** Privacy & Data tasks: the event log (SET-055) and settings backup on the phone (SET-081). */
    static final String EVENTS="events",PHONE_BACKUP="phone-"+PhoneService.BACKUP;
    /** Tests only: whether Home Assistant counts as set up (the Vault needs Android's keystore, which tests don't have). */
    static volatile Boolean haForTests;
    private static final Set<Integer> NAVIGATION_KEYS=new HashSet<>(Arrays.asList(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER,KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_TAB,KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_SELECT));

    private final SettingsOverlay overlay=new SettingsOverlay();
    /** The root's category rows; null while a detail is showing. */
    private final Button[] rows=new Button[CATEGORIES.length];
    private FrameLayout sheet;private View frost,glass;private LinearLayout header,body;private ScrollView scroll;private TextView hint;private String subtitle;
    private SharedPreferences prefs;
    /** SET-093: a navigation change is previewed here and applied only on confirmation; null when there is none. */
    private List<String> draftUtilities;
    private SetupPages setup;private PrivacyPage privacy;private LibraryPage library;private DevicePage device;
    /** The guided setup was opened from here: its row shows the new progress when the household comes back. */
    private boolean guidedSetupOpen;
    /** SET-025: a change being previewed (null when none), its countdown line, and the main-thread clock that ends it. */
    private PreviewRevert preview;private TextView previewLine;
    private final android.os.Handler clock=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable previewTick=new Runnable(){@Override public void run(){if(preview==null)return;long now=android.os.SystemClock.uptimeMillis();
        if(preview.due(now)){revert(true);return;}if(previewLine!=null)previewLine.setText(preview.words(now));clock.postDelayed(this,1000);}};
    /** SET-070: this visit's connectivity check (null until it has run), and whether one is running. */
    private java.util.List<Connectivity.Row> links;private boolean linksChecking;
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
        setup=new SetupPages(this);privacy=new PrivacyPage(this);library=new LibraryPage(this);device=new DevicePage(this);
        setContentView(root);render(false);landing();
        if(state==null)slideIn();
        // The notification's "Stop alerts" lands here, so stopping always asks for the adult PIN (owner exception to SET-053).
        if(state==null&&getIntent().getBooleanExtra("stopAlerts",false))stopAlerts();
        // Another screen asked for a setup task (Build My TV, an older setup entry): adults only, behind the PIN.
        String asked=getIntent().getStringExtra("task");
        if(state==null&&asked!=null&&SetupPages.owns(asked))EditLock.ask(this,()->expandTask(asked));
    }
    @Override protected void onDestroy(){if(setup!=null)setup.close();if(library!=null)library.close();clock.removeCallbacksAndMessages(null);super.onDestroy();}
    /** Phone setup stops answering as soon as Settings leaves the screen; coming back shows a fresh code. */
    @Override protected void onStop(){if(setup!=null)setup.paused();if(preview!=null)revert(false);super.onStop();}
    @Override protected void onRestart(){super.onRestart();String t=overlay.task();if(overlay.page()==SettingsOverlay.Phase.TASK&&t!=null&&t.startsWith("phone-"))refresh();}
    /** For tests and diagnostics: where the overlay is, and the sheet itself. */
    SettingsOverlay overlay(){return overlay;}
    View sheet(){return sheet;}
    PreviewRevert preview(){return preview;}
    PrivacyPage privacy(){return privacy;}
    boolean linksChecked(){return links!=null&&!linksChecking;}

    // --- Material, size and motion (module 13 §A tokens, at 1080p and scaled to this screen)
    int px(float at1080){return Math.round(at1080*unit);}
    /** Sizes follow the TV's own font scale and the household's Text size (SET-073). */
    private float textPx(float at1080){return at1080*unit*getResources().getConfiguration().fontScale*Accessibility.scale(prefs);}
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
        if(setup!=null)setup.showing(overlay.page()==SettingsOverlay.Phase.TASK?overlay.task():null);
        applyWidth(motion);header.removeAllViews();body.removeAllViews();Arrays.fill(rows,null);column=body;
        int c=overlay.category();previewLine=null;
        if(preview!=null&&overlay.page()!=SettingsOverlay.Phase.ROOT)previewCard();
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
            case LIVE_TV:return LiveTvProtection.opens(LiveTvProtection.now(this))?"Open":"Locked";
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
            if(!Profiles.kids(this)){section("Film posters");
                chevron(button("Film posters: "+(TmdbKey.saved(this)?"key saved":"not set up"),()->EditLock.ask(this,()->expandTask(PHONE_TMDB))));
                text("Posters and film details from TMDB, with your own free key. You add it on your phone.",15);library.pictures();section("About the library");}
            text("Library opens from its own dock tile or by pressing Down on Home. Playback, resume and Kids limits stay on this TV.",16);
            library.source();library.playback();library.favourites();restoreRow(LIBRARY);break;
        case PROFILES:profiles();restoreRow(PROFILES);break;
        case APPS:
            button("All apps",()->startActivity(new Intent(this,AppsActivity.class)));
            text("On Home, hold OK on a tile to move or remove it. In All apps, hold OK on an app to add it to Home. A new TV suggests up to six apps that are already installed; empty places are fine.",16);
            // Build My TV (SET-BUILD, module 13 §C 02): resumable, so the row says where the household left it.
            String build=HubBuilder.load(prefs,Profiles.current(prefs)).status;
            button("Build My TV"+(HubBuilder.IN_PROGRESS.equals(build)?": continue where you left off":HubBuilder.COMPLETED.equals(build)?": done":""),()->EditLock.ask(this,()->BuildMyTvActivity.start(this)));
            text("Choose your music, films and TV apps step by step, then arrange your dock. Nothing is installed without your OK.",15);
            button("Amazon Music",()->startActivity(new Intent(this,MusicActivity.class)));
            text("Menu on the remote also opens All apps from Home.",15);
            dockRepair();restoreRow(APPS);break;
        case NOTIFICATIONS:notifications();quietHours();restoreRow(NOTIFICATIONS);break;
        case SMART_HOME:smartHome();break;
        case LIVE_TV:liveTv();break;
        case CONNECTIONS:
            connectivity();section("This TV");
            button("Picture, sound, network & Bluetooth",()->Launch.unlocked(this,new Intent(Settings.ACTION_SETTINGS),"Use the TV's own Settings"));
            text("These belong to the TV itself; Unified TV opens them and changes nothing there.",15);
            device.sound();device.remote();
            section("AirPlay");airPlay();
            section("Voice");button("Ask your TV",()->startActivity(new Intent(this,VoiceActivity.class)));
            // SET-003: say only what this TV can do. Typing a request always works; speaking needs a speech service.
            text(Capabilities.shown(this,Capabilities.VOICE)?"Voice uses the TV's own speech provider only when you start it, and may use its cloud service."
                :"This TV has no speech service for apps, so Ask your TV takes typed requests.",15);break;
        case PRIVACY:
            privacy.build();
            button("Integration readiness",()->new AlertDialog.Builder(this).setTitle("Preview limitations").setMessage("VPN tunnel visibility does not verify protected playback. NAS, Ring, reminders, microphone and manufacturer controls need their own acceptance tests. The Android APK cannot run on native Tizen.").setPositiveButton("Close",null).show());break;
        default:about();break;
        }
    }
    private String taskTitle(){if(SetupPages.owns(overlay.task()))return SetupPages.title(overlay.task());if(CAPABILITIES.equals(overlay.task()))return "What this TV can do";if(REMOTE_HELP.equals(overlay.task()))return "What the buttons do";return NAVIGATION.equals(overlay.task())?"Navigation & Shortcuts":APPEARANCE_ADVANCED.equals(overlay.task())?"Advanced appearance":EVENTS.equals(overlay.task())?"Event log":"Advanced";}
    private String taskSubtitle(){if(SetupPages.owns(overlay.task()))return SetupPages.subtitle(overlay.task());if(CAPABILITIES.equals(overlay.task()))return "Checked on this TV now";if(REMOTE_HELP.equals(overlay.task()))return "In Unified TV, on this TV";return NAVIGATION.equals(overlay.task())?"Utilities, and where Down lands":APPEARANCE_ADVANCED.equals(overlay.task())?"Restore the defaults":EVENTS.equals(overlay.task())?"The last 7 days, on this TV only":"Owner checks, for Adults";}
    private void task(){String t=overlay.task();if(SetupPages.owns(t))setup.task(t);else if(CAPABILITIES.equals(t))device.capabilities();else if(REMOTE_HELP.equals(t))device.remoteHelp();else if(NAVIGATION.equals(t))navigation();else if(APPEARANCE_ADVANCED.equals(t))appearanceAdvanced();else if(EVENTS.equals(t))privacy.events();else advanced();}

    // --- Navigation inside the sheet (module 13 §B; SET-GLASS-06)
    private void open(int c){if(overlay.open(c)){if(setup!=null)setup.entered(c);render(true);focusAt(0);}}
    private void expand(String task,boolean wider){if(overlay.expand(task,wider,focusIndex())){render(true);focusAt(0);}}
    /** Opens a setup task from wherever the sheet is: under its own category (keeping the current one when it fits), in
     *  the same half-width sheet. Back returns to that category's detail. */
    void expandTask(String task){
        if(!overlay.acceptsInput())return;
        int c=SetupPages.category(task,overlay.atRoot()?-1:overlay.category()),row=focusIndex();
        if(overlay.page()==SettingsOverlay.Phase.TASK)overlay.back();
        if(overlay.atRoot()||overlay.category()!=c){overlay.open(c);row=0;}
        if(overlay.expand(task,false,row)){render(true);focusAt(0);}
    }
    /** Whether the sheet is on this category's detail (category ≥ 0, task null) or on this task (task non-null). */
    boolean on(int category,String task){
        if(!overlay.acceptsInput())return false;
        if(task!=null)return overlay.page()==SettingsOverlay.Phase.TASK&&task.equals(overlay.task());
        return overlay.page()==SettingsOverlay.Phase.DETAIL&&overlay.category()==category;
    }
    /** Redraws the page in place after a change, keeping focus and scroll. */
    void refresh(){if(overlay.acceptsInput()&&!overlay.atRoot())show(overlay.category());}
    /** A new sub-page inside a task (a league, a search): drawn from the top. */
    void redraw(){if(!overlay.acceptsInput())return;render(true);focusAt(0);}
    /** A refresh after a change re-renders the current page in place; another category opens it in the sheet. */
    void show(int c){
        if(!overlay.acceptsInput())return;
        if(c!=overlay.category()||overlay.atRoot()){open(c);return;}
        int at=focusIndex(),y=scroll.getScrollY();render(false);scroll.scrollTo(0,y);focusAt(Math.max(0,at));
    }
    /** Back: task → detail (the row that opened it) → root (the category's row) → closed. */
    void back(){
        if(!overlay.acceptsInput())return;
        if(overlay.page()==SettingsOverlay.Phase.TASK&&setup!=null&&setup.back(overlay.task()))return;
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
    /** The focused view, read from the window's own view tree exactly as PhoneWindow does. Robolectric's ShadowActivity
     *  answers null here whatever has focus, which hid Right's row lookup and the focus the journey test checks. */
    @Override public View getCurrentFocus(){View decor=getWindow().peekDecorView();return decor==null?null:decor.findFocus();}
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
    void group(String title){TextView t=label(title,18,SettingsGlass.GROUP);t.setLetterSpacing(.06f);t.setPadding(px(12),px(18),px(12),px(6));t.setFocusable(false);body.addView(t);}
    /** A raised translucent card that the following rows and text go into (a local scrim for readability). */
    void card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(px(12),px(8),px(12),px(8));c.setBackground(SettingsGlass.card(px(18),strong()));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=px(4);lp.bottomMargin=px(10);body.addView(c,lp);column=c;}
    void section(String title){group(title);card();}
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
    /** A trailing chevron on a row that opens a task. */
    Button chevron(Button b){b.setCompoundDrawablesRelative(null,null,new SettingsGlass.Trailing(null,true,false,textPx(19)),null);b.setCompoundDrawablePadding(px(16));return b;}
    Button taskRow(String title,String task,boolean wider,String state){Button b=button(title,()->expand(task,wider));b.setCompoundDrawablesRelative(null,null,new SettingsGlass.Trailing(state,true,false,textPx(19)),null);b.setCompoundDrawablePadding(px(16));return b;}
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
        // Dock size (SET-027, 6 or 8 icons): shrinking asks which tiles leave; they stay installed and in All apps.
        button("Dock size: "+DockCapacity.label(DockCapacity.get(prefs,Profiles.current(prefs))),()->EditLock.ask(this,()->DockCapacity.choose(this,()->show(APPEARANCE))));
        String key=DockLabels.key(Profiles.current(prefs)),labels=DockLabels.value(prefs.getString(key,DockLabels.FOCUSED_ONLY));
        button("App label visibility: "+DockLabels.label(labels),()->{String[] names=new String[DockLabels.VALUES.length];int now=0;for(int i=0;i<names.length;i++){names[i]=DockLabels.label(DockLabels.VALUES[i]);if(DockLabels.VALUES[i].equals(labels))now=i;}
            new AlertDialog.Builder(this).setTitle("App label visibility").setSingleChoiceItems(names,now,(d,n)->{prefs.edit().putString(key,DockLabels.VALUES[n]).apply();d.dismiss();show(APPEARANCE);}).setNegativeButton("Cancel",null).show();});
        text(DockLabels.FOCUSED_ONLY.equals(labels)?"Only the app in focus is named, under its tile.":DockLabels.ALWAYS.equals(labels)?"Every app on Home is named under its tile.":"No names under the tiles; the app in focus is named above the dock.",15);
        text("For "+Profiles.label(Profiles.current(prefs))+" only: each profile keeps its own choice. Tiles always show simple symbols, never names.",15);
        section("Ambient background");
        // Why Home's light isn't moving, in plain words, whenever it is still (the household's switches or the TV's own setting).
        TextView[] still=new TextView[1];Runnable why=()->{String r=AmbientLayer.stillReason(prefs.getBoolean("stillBackground",false),prefs.getBoolean("reduceMotion",false),prefs.getBoolean("lightweight",false),AmbientLayer.tvAnimations());
            still[0].setText(r==null?"":r);still[0].setVisibility(r==null?View.GONE:View.VISIBLE);};
        toggle("Reduce motion","reduceMotion",on->{why.run();restyle();});toggle("Keep the Home background still","stillBackground",on->why.run());toggle("Lightweight rendering","lightweight",on->{why.run();restyle();});
        // SET-022: intensity (standard or subtle) and colour (the app in focus, or neutral); Home behind the sheet shows each at once (HomePreview).
        ambientChoice("Intensity",AmbientMesh.INTENSITY,AmbientMesh.STANDARD,AmbientMesh.INTENSITIES,AmbientMesh::intensityLabel);
        ambientChoice("Colour",AmbientMesh.COLOUR,AmbientMesh.FOLLOW,AmbientMesh.COLOURS,AmbientMesh::colourLabel);
        still[0]=text("",15);why.run();
        section("Clock");
        String zone=prefs.getString(HouseholdSettings.ZONE,"");
        button("Time zone: "+(zone.isEmpty()?"Follow this TV ("+java.time.ZoneId.systemDefault().getId()+")":zone),this::chooseZone);
        text("The clock is "+(HouseholdSettings.twentyFour(this)?"24-hour":"12-hour")+", following this TV's own setting; words and dates follow its language.",15);
        section("Readability");
        String size=Accessibility.size(prefs.getString(Accessibility.TEXT_SIZE,Accessibility.NORMAL));
        button("Text size: "+Accessibility.label(size),()->{String[] names=new String[Accessibility.SIZES.length];for(int i=0;i<names.length;i++)names[i]=Accessibility.label(Accessibility.SIZES[i]);
            new AlertDialog.Builder(this).setTitle("Text size").setSingleChoiceItems(names,Arrays.asList(Accessibility.SIZES).indexOf(size),(d,n)->{d.dismiss();String next=Accessibility.SIZES[n];
                prefs.edit().putString(Accessibility.TEXT_SIZE,next).apply();previewed(Accessibility.TEXT_SIZE,size,next);restyle();}).setNegativeButton("Cancel",null).show();});
        text("Settings, setup and the names under Home's tiles. A new size goes back by itself after "+PreviewRevert.SECONDS+" seconds unless you keep it.",15);
        toggle("High contrast","highContrast",on->{previewed("highContrast",!on,on);restyle();});toggle("Reduce transparency","opaque",on->{previewed("opaque",!on,on);restyle();});
        toggle("Longer on-screen times",Accessibility.LONGER,false,on->{});
        text("Doorbell, person and reminder messages, and the doorbell camera, stay twice as long.",15);
        section("Home navigation");
        taskRow("Navigation & Shortcuts",NAVIGATION,true,draftUtilities==null?null:"Preview");
        if(DefaultHome.available(this)){section("Home button");
            HomeTakeover.reconcile(this);   // OD-28: the TV's own Home turned back on outside Unified TV (TV settings, adb): tidy up
            boolean ours=DefaultHome.isDefault(this),off=HomeTakeover.stockOff(this),kept=!ours&&!off&&DefaultHome.fixed(this);
            // Chosen once, but this TV keeps its own Home button (owner's TCL, 25 October 2026): said plainly, no dead switch.
            text(ours?(off?HOME_TAKEN:"The Home button opens Unified TV."):off?HOME_OFF_OTHER:kept?DefaultHome.KEPT_WORDS:"The Home button opens the original TV interface.",16);
            if(!ours&&!kept&&!off)button("Make Unified TV the Home screen",()->EditLock.ask(this,this::homeSetup));
            // OD-28: take over the TV's own Home button (asks the PIN itself), and always a way back while Unified TV has it.
            if(!ours&&!off){button(HomeTakeoverActivity.TAKE_TITLE,()->HomeTakeoverActivity.open(this,true));text(TAKE_NOTE,15);}
            if(ours||off||DefaultHome.offered(this))button(HomeTakeoverActivity.RETURN_TITLE,()->HomeTakeoverActivity.open(this,false));
            // OD-23: only while the Home button doesn't open Unified TV (TurnOn); the preview never shows it.
            if(!ours)turnOnRow();}
        else{section("Home button");text("This is the Unified TV preview. It never changes the Home button; the separate Unified TV app can.",15);}
        section("Advanced appearance");
        taskRow("Advanced appearance",APPEARANCE_ADVANCED,false,null);
    }
    static final String HOME_TAKEN="The Home button opens Unified TV. The TV's own Home is turned off; Return to the original TV interface turns it back on.",
        HOME_OFF_OTHER="Unified TV turned the TV's own Home off, but the Home button doesn't open Unified TV. Return to the original TV interface turns it back on.",
        TAKE_NOTE="For a TV that keeps its own Home button. Uses the TV's network debugging, asks before turning the TV's own Home off (never removed), and is undone here.";
    /** Reduce motion, transparency, contrast or lightweight changed: the sheet and its rows follow at once (posted, so a
     *  switch is never removed while its own click is still running). */
    void restyle(){material();body.post(()->{if(overlay.acceptsInput()&&!overlay.atRoot())show(overlay.category());});}
    private void appearanceAdvanced(){
        restoreRow(APPEARANCE);
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
    private boolean haConfigured(){Boolean t=haForTests;if(t!=null)return t;try{org.json.JSONObject ha=new Vault(this).read("ha");return ha.optBoolean("enabled")&&!ha.optString("token").isEmpty();}catch(Exception e){return false;}}
    private void notifications(){
        boolean ha=haConfigured();
        AlarmWatch.State alarms=AlarmWatch.state(prefs.getBoolean(AlarmWatch.SHOWN,true),prefs.getBoolean(AlarmWatch.STOPPED,false),ha,prefs.getInt(AlarmWatch.DETECTORS,-1));
        TextView status=text(alarms==AlarmWatch.State.OFF?AlarmWatch.OFF_NOTICE:alarms==AlarmWatch.State.NOT_CONFIGURED?AlarmWatch.NOT_CONFIGURED_TEXT:"Smoke and carbon monoxide alarms: shown on this TV",19);
        status.setFocusable(false);text(AlarmWatch.words(alarms),15);
        if(!ha){text("Doorbell and alarm alerts come from Home Assistant, which isn't set up on this TV.",16);
            chevron(button("Set up Home Assistant on your phone",()->EditLock.ask(this,()->expandTask(PHONE_HA))));}
        else chevron(button("Doorbell, alarms & this TV",()->EditLock.ask(this,()->expandTask(HOUSEHOLD))));
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

    // --- Smart Home (SetupPages: Home Assistant status first, set up on the phone; football; Advanced in the sheet)
    private void smartHome(){setup.smartHome();}

    // --- Live TV & Network Protection (OD-26: Live TV opens when NordVPN is connected; not a kill switch)
    private void liveTv(){
        String live=LiveTvProtection.livePackage(this);LiveTvProtection.State state=LiveTvProtection.now(this);
        TextView word=text("Live TV: "+LiveTvProtection.word(state),20);word.setFocusable(false);
        text(LiveTvProtection.message(state),16);
        text(live.isEmpty()?"No single Live TV app was recognised on this TV.":"Live TV app: "+live,15);
        button("Check again",()->{VpnGate.invalidate();show(LIVE_TV);});
        button("NordVPN",()->VpnGate.show(this));
        // NordVPN's own "Connected" notice (MediaWatch, NordCountry): how Unified TV tells NordVPN's VPN from another app's.
        if(LocalDiscovery.nordVpn(this)&&!MediaWatch.granted(this)){
            text("Allow notification access so Unified TV can see NordVPN's own \"Connected\" notice. Without it, Live TV opens only while NordVPN is the only VPN app on this TV, and no country flag shows beside the shield on Home. The same access makes Now Playing work for Amazon Music.",15);
            // Owner's TCL, build run 99: no notification-access screen. Then through the TV's debugging prompt, or a computer.
            Intent screen=MediaWatch.accessScreen(this);
            if(screen!=null)button("Allow notification access",()->Launch.unlocked(this,screen,"This TV doesn't offer notification access"));
            else{if(NotificationAccessActivity.available(this))button(VpnGate.DEBUG_ACCESS,()->NotificationAccessActivity.open(this));
                text("This TV has no notification-access screen. From a computer with ADB instead: "+MediaWatch.pcCommand(this),14).setTypeface(android.graphics.Typeface.MONOSPACE);}
        }
        text(NordConnect.AUTO_TIP,15);
        text(LiveTvProtection.RULE+" "+LiveTvProtection.COVERS+" "+LiveTvProtection.NOT_A_KILL_SWITCH,15);
        if(!TurnOn.permitted(this))text("Covering Live TV needs \"Display over other apps\" for Unified TV in the TV's own settings. Without it, a notification says NordVPN disconnected and Live TV stays on screen.",15);
    }

    // --- Updates & About
    private void about(){
        // OD-28 uninstall guard: with the TV's own Home off, removing Unified TV would leave the TV without a Home.
        if(HomeTakeover.stockOff(this)){text(HomeTakeover.UNINSTALL_WORDS,20);button(HomeTakeoverActivity.RETURN_TITLE,()->HomeTakeoverActivity.open(this,false));}
        String version="unknown";long code=0;
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);version=info.versionName==null?"unknown":info.versionName;code=info.getLongVersionCode();}catch(Exception ignored){}
        boolean debug=(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        text("Version "+version+" ("+code+")"+(debug?", test build":""),18);
        text(About.build(),16);   // tells builds apart: commit, CI run and date (owner, 25 October 2026)
        text(About.flavour(getPackageName()),16);
        text("Package: "+getPackageName(),15);
        text("Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+") on "+Build.MANUFACTURER+" "+Build.MODEL+" · build "+Build.DISPLAY,15);
        text("Updates install over this app only when signed with the same key. The preview and the launcher are separate apps; one never upgrades the other.",15);
        // SET-006 / module 13 §C 10 "Compatibility": what this TV can do, read from it now.
        section("This TV");
        taskRow("What this TV can do",CAPABILITIES,false,null);
        text("What works, what needs setting up or your OK, and what this TV can't do, in plain words.",15);
        // SET-010 / SET-015: the guided setup again, whole or one part. Adults only, like the setup itself.
        if(!Profiles.kids(this)){section("Guided setup");
            button(FirstRun.status(FirstRun.load(prefs)),()->EditLock.ask(this,()->{if(!FirstRun.IN_PROGRESS.equals(FirstRun.load(prefs).status))FirstRun.save(prefs,FirstRun.startOver());guidedSetupOpen=true;FirstRun.start(this,null);}));
            button("Just one part",()->EditLock.ask(this,()->new AlertDialog.Builder(this).setTitle("Run one part").setItems(new String[]{"Your remote","Your apps","Your films and TV"},
                (d,n)->{guidedSetupOpen=true;FirstRun.start(this,new String[]{FirstRun.REMOTE,FirstRun.APPS,FirstRun.LIBRARY}[n]);}).setNegativeButton("Cancel",null).show()));
            text("Short, optional steps over Home: the remote, your apps, and your films and TV. Nothing you've chosen is lost.",15);}
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
        // Optional clean-up (SPEC "Optional TV Performance & Cleanup"): off until an adult chooses each app; asks for the PIN itself.
        button("Optional clean-up",()->startActivity(new Intent(this,CleanupActivity.class)));
        text("Nothing is cleaned up automatically. Optional clean-up turns off only the apps you choose, one by one, and can undo it.",15);
        // Spec 30: deliberately small and last; adult only; plain words. Only in the app that can be Home.
        if(DefaultHome.available(this))button(HomeTakeoverActivity.RETURN_TITLE,()->HomeTakeoverActivity.open(this,false));   // OD-28, asks the PIN itself
        if(DefaultHome.available(this)){Button original=button("Open original TV interface",()->EditLock.ask(this,this::original));original.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(21));original.setMinHeight(0);original.setMinimumHeight(px(52));original.setAlpha(.85f);}
    }
    /** Spec 7.1: AirPlay through AirPin LITE. Setup happens in AirPin itself; Unified TV only reports what it can confirm. */
    private void airPlay(){
        String pkg=AirPlayReceiver.installed(this);
        if(pkg==null){text("AirPlay from iPhone: install AirPin LITE from the TV's app store to receive AirPlay.",16);return;}
        text(AirPlayReceiver.ready?"AirPlay from iPhone: ready"+(AirPlayReceiver.advertisedAs.isEmpty()?".":", shown on iPhones as \""+AirPlayReceiver.advertisedAs+"\"."):"AirPlay from iPhone: AirPin LITE is installed but isn't offering AirPlay on the network right now. Open it once, and check its own setting to start with the TV.",16);
        button("Open AirPin LITE",()->{String error=Launch.open(this,pkg,"AirPin LITE");if(error!=null&&!error.isEmpty())note(error);});
        text(AirPlayStart.TIP,15);
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
    /** SET-022: one Ambient background choice as a row ("Intensity: Standard"); OK lists the values, the choice saves at once. */
    private void ambientChoice(String title,String key,String fallback,String[] values,java.util.function.Function<String,String> label){
        String now=prefs.getString(key,fallback);if(!Arrays.asList(values).contains(now))now=fallback;
        String[] names=new String[values.length];for(int i=0;i<values.length;i++)names[i]=label.apply(values[i]);int at=Arrays.asList(values).indexOf(now);
        button(title+": "+label.apply(now),()->new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(names,at,(d,n)->{prefs.edit().putString(key,values[n]).apply();d.dismiss();show(APPEARANCE);}).setNegativeButton("Cancel",null).show());
    }
    private void toggle(String name,String key){toggle(name,key,on->{});}
    private void toggle(String name,String key,java.util.function.Consumer<Boolean> after){toggle(name,key,false,after);}
    /** Module 13 §D: one clear binary row; the label on the left, its state as a word (On/Off) on the right. */
    CheckBox toggle(String name,String key,boolean byDefault,java.util.function.Consumer<Boolean> after){
        CheckBox c=new CheckBox(this);c.setText(name);c.setButtonDrawable(null);c.setTextColor(SettingsGlass.textColours());c.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(26));c.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        c.setMinHeight(px(64));c.setMinimumHeight(px(64));c.setPadding(px(24),px(12),px(24),px(12));c.setBackground(rowBackground());c.setStateListAnimator(null);
        c.setCompoundDrawablesRelative(null,null,new SettingsGlass.Trailing(null,false,true,textPx(19)),null);c.setCompoundDrawablePadding(px(16));
        c.setChecked(getSharedPreferences("unified-tv-preview",0).getBoolean(key,byDefault));c.setFocusable(true);c.setFocusableInTouchMode(true);
        c.setOnCheckedChangeListener((v,on)->{getSharedPreferences("unified-tv-preview",0).edit().putBoolean(key,on).apply();after.accept(on);});column.addView(c,rowParams());
        c.setContentDescription(name+", "+(c.isChecked()?"On":"Off"));c.setOnFocusChangeListener((v,f)->c.setContentDescription(name+", "+(c.isChecked()?"On":"Off")));
        return c;
    }
    /** Spec 28.3 onboarding: explain, let Android ask, then confirm what actually happened. */
    private void homeSetup(){
        new AlertDialog.Builder(this).setTitle("Make Unified TV the Home screen").setMessage("Pressing Home will open Unified TV instead of the original TV interface. The original interface stays on the TV, and you can switch back at any time from this screen.\n\nYour TV will ask you to choose: pick Unified TV.")
            .setPositiveButton("Continue",(d,w)->{homeAsked=true;settingsPass();
                // No dialog, Home settings or chooser on this TV: nothing can be chosen, so nothing is left switched on.
                if(DefaultHome.offer(this)==DefaultHome.Route.NONE){homeAsked=false;DefaultHome.withdraw(this);DefaultHome.fixed(this,true);note(DefaultHome.KEPT_WORDS);show(APPEARANCE);}})
            .setNegativeButton("Cancel",null).show();
    }
    private boolean homeAsked;
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==DefaultHome.REQUEST)homeChecked();}
    @Override protected void onResume(){super.onResume();if(homeAsked)homeChecked();if(guidedSetupOpen){guidedSetupOpen=false;refresh();}
        if(overlayAsked){overlayAsked=false;TurnOn.sync(this);refresh();}}
    /** Back from Android's "Display over other apps" screen: the row shows whatever was chosen there. */
    private boolean overlayAsked;
    /** Only one panel at a time (OnePanel): this sheet hides while a setup panel is open over it, and comes back after. */
    private final OnePanel panels=new OnePanel();
    @Override public void startActivityForResult(Intent intent,int code,Bundle options){
        boolean panel=OnePanel.opensPanel(this,intent);if(panel)panels.hide(sheet,calm());
        try{super.startActivityForResult(intent,code,options);}catch(RuntimeException e){if(panel)panels.show(sheet,true);throw e;}
    }
    @Override protected void onPostResume(){super.onPostResume();panels.show(sheet,calm());}
    /** True when the sheet is on screen: not hidden under another panel (OnePanel). */
    boolean panelShown(){return sheet!=null&&sheet.isAttachedToWindow()&&sheet.getVisibility()==View.VISIBLE&&sheet.getAlpha()>0&&!panels.hidden();}
    /**
     * OD-23 "Open Unified TV when the TV turns on" (launcher app; Home button not Unified TV's): one row behind the adult
     * PIN, its state said honestly (On, Needs permission, Off). The permission is Android's "Display over other apps",
     * used only to come to the front: the TV's own screen for it when there is one, otherwise a one-time computer step.
     */
    private void turnOnRow(){
        TurnOn.State s=TurnOn.state(this);
        button(TurnOn.TITLE+": "+s.words,()->EditLock.ask(this,this::turnOnChange));
        text(TurnOn.EXPLAIN,15);
        if(s==TurnOn.State.NEEDS_PERMISSION)text(TurnOn.permissionScreen(this)!=null?TurnOn.PERMISSION_WORDS+" Press the row to open that setting."
            :TurnOn.PERMISSION_WORDS+" "+TurnOn.NO_SCREEN_WORDS+" "+TurnOn.adb(this),15);
    }
    /** Behind the PIN: Off becomes On (asking for the permission if it is missing), On becomes Off. */
    private void turnOnChange(){
        TurnOn.State s=TurnOn.state(this);
        if(s==TurnOn.State.ON){TurnOn.enabled(this,false);TurnOn.sync(this);show(APPEARANCE);return;}
        if(s==TurnOn.State.OFF){TurnOn.enabled(this,true);TurnOn.sync(this);show(APPEARANCE);if(TurnOn.permitted(this))return;}
        Intent screen=TurnOn.permissionScreen(this);
        AlertDialog.Builder ask=new AlertDialog.Builder(this).setTitle(TurnOn.TITLE)
            .setNeutralButton("Switch it off",(d,w)->{TurnOn.enabled(this,false);TurnOn.sync(this);show(APPEARANCE);});
        if(screen!=null)ask.setMessage(TurnOn.PERMISSION_WORDS).setNegativeButton("Cancel",null)
            .setPositiveButton("Open the TV's setting",(d,w)->{settingsPass();overlayAsked=true;try{startActivity(screen);}catch(RuntimeException e){overlayAsked=false;note(TurnOn.NO_SCREEN_WORDS+" "+TurnOn.adb(this));}});
        else ask.setMessage(TurnOn.PERMISSION_WORDS+"\n\n"+TurnOn.NO_SCREEN_WORDS+"\n"+TurnOn.adb(this)).setPositiveButton("OK",null);
        ask.show();
    }
    /**
     * A feature's next step, from What this TV can do (Capabilities.GO_*): its category inside this sheet, its phone setup,
     * or its own screen. Kids settings and phone setup are behind the adult PIN, as everywhere else.
     */
    void go(String place){
        if(!overlay.acceptsInput()||place==null)return;
        switch(place){
            case Capabilities.GO_APPEARANCE:open(APPEARANCE);break;
            case Capabilities.GO_LIBRARY:open(LIBRARY);break;
            case Capabilities.GO_TMDB:EditLock.ask(this,()->expandTask(PHONE_TMDB));break;
            case Capabilities.GO_CONNECTIONS:open(CONNECTIONS);break;
            case Capabilities.GO_LIVE_TV:open(LIVE_TV);break;
            case Capabilities.GO_HOME_ASSISTANT:case Capabilities.GO_FOOTBALL:open(SMART_HOME);break;
            case Capabilities.GO_NOTIFICATIONS:open(NOTIFICATIONS);break;
            case Capabilities.GO_KIDS:EditLock.ask(this,()->startActivity(new Intent(this,KidsActivity.class)));break;
            case Capabilities.GO_VOICE:startActivity(new Intent(this,VoiceActivity.class));break;
            default:break;
        }
    }
    /**
     * Back from Android's choice (any result): Unified TV's own Home comes to the front at once, so the owner is never
     * left on another Home, and Home reports what Android recorded (DefaultHome.settle: an unchosen entry is withdrawn).
     */
    private void homeChecked(){
        if(!homeAsked)return;homeAsked=false;
        try{startActivity(DefaultHome.backHome(this));overridePendingTransition(0,0);}catch(RuntimeException e){DefaultHome.settle(this,s->{note(DefaultHome.words(s));if(overlay.category()==APPEARANCE)show(APPEARANCE);});}
    }
    private void original(){
        boolean ours=DefaultHome.isDefault(this);
        java.util.List<String> choices=new java.util.ArrayList<>();choices.add("Open original interface for now");if(ours)choices.add("Make original interface the default");choices.add("Cancel");
        new AlertDialog.Builder(this).setTitle("Original TV interface").setItems(choices.toArray(new String[0]),(d,n)->{
            String pick=choices.get(n);
            if(pick.startsWith("Open")){settingsPass();if(!DefaultHome.openOriginal(this))new AlertDialog.Builder(this).setMessage("This TV doesn't let Unified TV open the original interface directly."+(ours?" Choose 'Make original interface the default' instead.":" Press the Home button on your remote to get there.")).setPositiveButton("OK",null).show();}
            else if(pick.startsWith("Make"))new AlertDialog.Builder(this).setTitle("Make original interface the default").setMessage("From now on, pressing Home will open the original TV interface until you choose Unified TV again in Settings > Appearance & Home.")
                .setPositiveButton("Make it the default",(d2,w2)->{if(HomeTakeover.stockOff(this)||!DefaultHome.withdraw(this)){HomeTakeoverActivity.open(this,false);return;}note("The Home button now opens the original TV interface.");show(overlay.category());}).setNegativeButton("Cancel",null).show();
        }).show();
    }
    private void connection(String key){startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key",key));}
    /** An adult is here behind the PIN: if Kids is on, the Kids guard lets system screens opened from here stay a while. */
    private void settingsPass(){if(Profiles.kids(this))getSharedPreferences("unified-tv-preview",0).edit().putLong(KidsGuard.PASS,System.currentTimeMillis()+KidsGuard.PASS_MS).apply();}

    // --- SET-025 preview and revert
    /** A change that could make Settings unreadable or unusable is a preview: it goes back by itself unless kept. */
    void previewed(String key,Object before,Object after){
        if(!PreviewRevert.risky(key,before,after))return;
        Object original=preview!=null&&preview.key.equals(key)?preview.before:before;
        if(preview!=null&&!preview.key.equals(key))revert(false);
        if(java.util.Objects.equals(original,after)){preview=null;clock.removeCallbacks(previewTick);return;}
        preview=new PreviewRevert(key,original,after,android.os.SystemClock.uptimeMillis());
        clock.removeCallbacks(previewTick);clock.postDelayed(previewTick,1000);
    }
    /** The card at the top of the page while a preview runs: Keep (focused, so OK keeps it) or go back now. */
    private void previewCard(){
        card();previewLine=text(preview.words(android.os.SystemClock.uptimeMillis()),19);previewLine.setFocusable(false);
        Button keep=button("Keep this change",this::keep);button("Go back now",()->revert(true));
        body.post(()->{if(preview!=null&&keep.isAttachedToWindow())keep.requestFocus();});
        column=body;
    }
    private void keep(){if(preview==null)return;preview=null;clock.removeCallbacks(previewTick);note("Kept.");show(overlay.category());}
    /** Puts the previewed setting back as it was (a missing value stays missing); redraws when the sheet is showing. */
    private void revert(boolean redraw){
        PreviewRevert p=preview;if(p==null)return;preview=null;clock.removeCallbacks(previewTick);
        SharedPreferences.Editor e=prefs.edit();
        HouseholdSettings.put(e,p.key,p.before);
        e.commit();
        if(redraw){note("Changed back.");material();if(overlay.acceptsInput()&&!overlay.atRoot())show(overlay.category());}
    }

    // --- SET-004 restore defaults, per section
    /** The section's own "Restore defaults": the plan's words in the confirmation, behind the adult PIN. */
    void restoreRow(int section){
        if(!SettingsReset.offered(section)||Profiles.kids(this))return;
        section("Restore defaults");
        button("Restore defaults for "+CATEGORIES[section],()->EditLock.ask(this,()->{SettingsReset.Plan plan=SettingsReset.section(section,prefs.getAll());
            new AlertDialog.Builder(this).setTitle("Restore defaults for "+CATEGORIES[section]+"?").setMessage(plan.message())
                .setPositiveButton("Restore defaults",(d,w)->{SharedPreferences.Editor e=prefs.edit();for(String k:plan.keys)e.remove(k);e.commit();
                    if(preview!=null&&plan.keys.contains(preview.key)){preview=null;clock.removeCallbacks(previewTick);}
                    EventLog.record(this,EventLog.SETTINGS,CATEGORIES[section]+": defaults restored");note("Defaults restored.");restyle();})
                .setNegativeButton("Cancel",null).show();}));
        text("Asks first, and says what changes and what is kept.",15);
    }

    // --- SET-021 apps on Home that are no longer installed
    private String room(){return prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");}
    /** {package: label} of every app with a TV or phone launcher entry (Unified TV itself left out). */
    private Map<String,String> launchable(){
        Map<String,String> out=new HashMap<>();android.content.pm.PackageManager pm=getPackageManager();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            try{for(android.content.pm.ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))
                if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(getPackageName()))out.put(r.activityInfo.packageName,String.valueOf(r.loadLabel(pm)));}catch(RuntimeException ignored){}
        return out;
    }
    private void dockRepair(){
        if(Profiles.kids(this))return;
        Map<String,String> apps=launchable();List<String> dock=HouseholdSettings.dock(this,room());
        List<String> missing=DockRepair.missing(dock,apps,!LiveTvProtection.livePackage(this).isEmpty());
        if(missing.isEmpty())return;
        section("Not installed on this TV");
        for(String id:missing){String name=DockRepair.name(id,apps);
            button(name+": not installed",()->EditLock.ask(this,()->new AlertDialog.Builder(this).setTitle(name+" isn't installed").setMessage("Its place on Home is kept until you choose. Nothing is installed or uninstalled.")
                .setItems(new String[]{"Replace it with another app","Remove it from Home","Keep it for now"},(d,n)->{
                    List<String> now=HouseholdSettings.dock(this,room());
                    if(n==0){List<String> c=DockRepair.candidates(room(),now,apps);if(c.isEmpty()){note("Every installed app is already on Home.");return;}
                        String[] names=new String[c.size()];for(int i=0;i<names.length;i++)names[i]=DockRepair.name(c.get(i),apps);
                        new AlertDialog.Builder(this).setTitle("Replace "+name+" with").setItems(names,(d2,m)->{prefs.edit().putString("dock",DockPolicy.replace(room(),now,id,c.get(m))).apply();note(names[m]+" takes "+name+"'s place.");show(APPS);}).setNegativeButton("Cancel",null).show();}
                    else if(n==1){prefs.edit().putString("dock",DockPolicy.remove(now,id)).apply();note(name+" removed from Home.");show(APPS);}
                }).show()));}
        text("Home keeps these places until you choose; they open nothing until the app is back.",15);
    }

    // --- SET-052 quiet hours (per TV; never smoke or carbon monoxide)
    private void quietHours(){
        boolean on=QuietHours.on(prefs);
        if(!haConfigured()&&!on)return;   // person alerts come from Home Assistant: nothing to quieten without it (SET-003)
        section("Quiet hours");
        boolean kids=Profiles.kids(this);
        if(kids){text("Quiet hours: "+QuietHours.summary(prefs),17);return;}
        toggle("Quiet hours",QuietHours.ON,false,yes->body.post(()->{if(on(NOTIFICATIONS,null))show(NOTIFICATIONS);}));
        if(on){
            button("From "+QuietHours.clock(QuietHours.start(prefs)),()->pickTime("Quiet hours start",QuietHours.START));
            button("Until "+QuietHours.clock(QuietHours.end(prefs)),()->pickTime("Quiet hours end",QuietHours.END));
        }
        text("No person alerts on this TV between these times. Doorbell presses and smoke or carbon monoxide alarms always show.",15);
    }
    private void pickTime(String title,String key){
        int[] times=QuietHours.choices();String[] names=new String[times.length];for(int i=0;i<times.length;i++)names[i]=QuietHours.clock(times[i]);
        new AlertDialog.Builder(this).setTitle(title).setItems(names,(d,n)->{prefs.edit().putInt(key,times[n]).apply();show(NOTIFICATIONS);}).show();
    }

    // --- SET-070 connectivity summary
    private void connectivity(){
        section("Connections");
        if(links==null&&!linksChecking)checkLinks();
        List<Connectivity.Row> rows=links!=null?links:Connectivity.unchecked(this);
        if(links==null)text("Internet and home network: checking…",17).setFocusable(false);
        for(Connectivity.Row r:rows){text(Connectivity.line(r),17).setFocusable(false);
            text(Connectivity.detail(r,r.lastOk>0?OwnerChecks.when(this,r.lastOk):null),15);}
        button(linksChecking?"Checking…":"Check again",()->{if(!linksChecking){links=null;checkLinks();show(CONNECTIONS);}});
    }
    private void checkLinks(){
        linksChecking=true;android.content.Context app=getApplicationContext();
        new Thread(()->{List<Connectivity.Row> r=Connectivity.check(app);runOnUiThread(()->{linksChecking=false;links=r;if(on(CONNECTIONS,null))refresh();});},"connectivity").start();
    }
}
