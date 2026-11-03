package io.unifiedtv.preview;

import android.app.Activity;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPackageManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/**
 * The owner's Sony (run 106) on Home: an empty dock place is an action ("+", "Add an app", OK chooses an app for exactly
 * that place, "Leave empty" keeps it); Hold OK offers Replace with another app; Live TV with no app recognised asks for the
 * household's Live TV app, and whatever route opens that app goes through the one gate (OD-26) and the safety net watches
 * it; Apps shows one tile per app; tiles without a mark fill their place; and the dock fits the screen at the Sony's and
 * other sizes. Both flavours run this class.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class DockPlacesTest {
    private static final String SONY_SELECT="com.sony.select",REAM="com.example.reamsmartz",FAMILY="com.example.familychannels";
    private Context app(){return RuntimeEnvironment.getApplication();}
    private SharedPreferences prefs(){return app().getSharedPreferences("unified-tv-preview",0);}
    private static void idle(long ms){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private void install(String pkg,String label){
        ShadowPackageManager pm=Shadows.shadowOf(app().getPackageManager());
        ComponentName c=new ComponentName(pkg,pkg+".Main");ActivityInfo info=pm.addActivityIfNotPresent(c);info.nonLocalizedLabel=label;if(info.applicationInfo!=null)info.applicationInfo.nonLocalizedLabel=label;
        IntentFilter f=new IntentFilter(Intent.ACTION_MAIN);f.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        try{pm.addIntentFilterForActivity(c,f);}catch(Exception e){throw new AssertionError(e);}
    }
    @Before public void clean(){
        prefs().edit().clear().putString("dock","netflix,disney,bbc,music,library").commit();
        install("com.netflix.ninja","Netflix");install(SONY_SELECT,"Sony Select");install(REAM,"ReamSmartz");
        LiveTvProtection.forTests=facts(VpnGate.State.DISCONNECTED);ShadowDialog.reset();AppPickerActivity.testWorker=Runnable::run;
        while(Shadows.shadowOf((android.app.Application)app()).getNextStartedService()!=null){}
    }
    @After public void reset(){AppPickerActivity.testWorker=null;Comeback.disarm();NordConnect.stop();LiveTvProtection.forTests=null;LiveTvProtection.last=null;LiveTvGuard.running=false;}
    private static LiveTvProtection.Facts facts(VpnGate.State vpn){LiveTvProtection.Facts f=new LiveTvProtection.Facts();f.vpn=vpn;f.nordInstalled=true;return f;}

    // --- Home's dock, by reflection (Home is one drawn view)
    private View home;private Method key;
    private ActivityController<UnifiedPreviewActivity> home(){
        ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();
        try{home=RemoteJourneyTest.homeIn(c.get());key=home.getClass().getDeclaredMethod("key",KeyEvent.class);key.setAccessible(true);}catch(Exception e){throw new AssertionError(e);}
        Shadows.shadowOf(c.get()).getNextStartedActivity();return c;
    }
    private void press(int code)throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,code));key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,code));}
    private void hold()throws Exception{key.invoke(home,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));idle(700);key.invoke(home,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));}
    private Object field(String name)throws Exception{Field f=home.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(home);}
    private List<String> dockIds()throws Exception{List<String> out=new ArrayList<>();for(Object item:(List<?>)field("dock")){Field id=item.getClass().getDeclaredField("id");id.setAccessible(true);out.add((String)id.get(item));}return out;}
    private void toTheEmptyPlace()throws Exception{for(int i=0;i<6;i++)press(KeyEvent.KEYCODE_DPAD_RIGHT);}

    private static <T extends View> T find(View v,Class<T> type,String text){
        if(type.isInstance(v)&&v instanceof TextView&&((TextView)v).getText().toString().equals(text))return type.cast(v);
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){T t=find(((ViewGroup)v).getChildAt(i),type,text);if(t!=null)return t;}
        return null;
    }
    private static View described(View v,String start){
        if(v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(start))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View t=described(((ViewGroup)v).getChildAt(i),start);if(t!=null)return t;}
        return null;
    }
    private ActivityController<AppPickerActivity> picker(Intent started){
        assertNotNull("the picker opened",started);assertEquals(AppPickerActivity.class.getName(),started.getComponent().getClassName());
        return openPicker(started);
    }
    /** The picker, with its worker's jobs (here run at once) delivered to the main thread. */
    private static ActivityController<AppPickerActivity> openPicker(Intent i){ActivityController<AppPickerActivity> c=Robolectric.buildActivity(AppPickerActivity.class,i).setup();idle(0);idle(0);return c;}

    // --- The empty place
    @Test public void okOnTheEmptyPlaceOpensThePickerForThatPlace()throws Exception{
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            toTheEmptyPlace();
            assertEquals("focus reaches the first empty place",true,field("blank"));assertEquals("and no further",4,field("index"));
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();assertNotNull(started);
            assertEquals(AppPickerActivity.class.getName(),started.getComponent().getClassName());
            assertEquals(AppPickerActivity.PLACE,started.getStringExtra(AppPickerActivity.MODE));assertEquals(5,started.getIntExtra(AppPickerActivity.PLACE,-1));
            press(KeyEvent.KEYCODE_DPAD_LEFT);assertEquals(false,field("blank"));assertEquals(4,field("index"));
        }
    }
    @Test public void aChoiceFillsThatExactPlaceKeepsFocusAndSurvivesARestart()throws Exception{
        Intent started;
        try(ActivityController<UnifiedPreviewActivity> c=home()){toTheEmptyPlace();press(KeyEvent.KEYCODE_DPAD_CENTER);started=Shadows.shadowOf(c.get()).getNextStartedActivity();c.pause();}
        try(ActivityController<AppPickerActivity> p=picker(started)){
            AppPickerActivity a=p.get();assertEquals(DockPicker.TITLE,find(a.findViewById(android.R.id.content),TextView.class,DockPicker.TITLE).getText().toString());
            assertNotNull("a search field",find(a.findViewById(android.R.id.content),EditText.class,""));
            find(a.findViewById(android.R.id.content),Button.class,"Sony Select").performClick();assertTrue(a.isFinishing());
        }
        assertEquals("netflix,disney,bbc,music,library,"+SONY_SELECT,prefs().getString("dock",""));
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            assertEquals(Arrays.asList("netflix","disney","bbc","music","library",SONY_SELECT),dockIds());
            assertEquals("focus is on the new tile",5,field("index"));assertEquals(false,field("blank"));
        }
    }
    @Test public void leaveEmptyKeepsThePlaceBlank()throws Exception{
        Intent started;
        try(ActivityController<UnifiedPreviewActivity> c=home()){toTheEmptyPlace();press(KeyEvent.KEYCODE_DPAD_CENTER);started=Shadows.shadowOf(c.get()).getNextStartedActivity();c.pause();}
        try(ActivityController<AppPickerActivity> p=picker(started)){find(p.get().findViewById(android.R.id.content),Button.class,DockPicker.LEAVE).performClick();assertTrue(p.get().isFinishing());}
        assertEquals("netflix,disney,bbc,music,library",prefs().getString("dock",""));
        try(ActivityController<UnifiedPreviewActivity> c=home()){assertEquals(5,dockIds().size());assertEquals("focus is back on the empty place",true,field("blank"));}
    }
    @Test public void thePickerLeavesOutAppsAlreadyOnHome()throws Exception{
        try(ActivityController<AppPickerActivity> p=openPicker(new Intent(app(),AppPickerActivity.class).putExtra(AppPickerActivity.MODE,AppPickerActivity.PLACE).putExtra(AppPickerActivity.PLACE,5))){
            List<String> offered=p.get().offered();
            assertFalse("Netflix is on Home",offered.contains("Netflix"));assertTrue(offered.contains("Sony Select"));assertTrue(offered.contains("ReamSmartz"));
        }
    }
    @Test public void holdOkOffersReplaceWithAnotherAppUsingThePicker()throws Exception{
        Intent started;
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            press(KeyEvent.KEYCODE_DPAD_RIGHT);hold();assertEquals(true,field("menu"));
            assertEquals("Replace with another app",((String[])field("menuNow"))[1]);
            press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_CENTER);
            started=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals("disney",started.getStringExtra(AppPickerActivity.REPLACING));c.pause();
        }
        try(ActivityController<AppPickerActivity> p=picker(started)){find(p.get().findViewById(android.R.id.content),Button.class,"Sony Select").performClick();}
        assertEquals("Disney+ left Home; its place is Sony Select's","netflix,"+SONY_SELECT+",bbc,music,library",prefs().getString("dock",""));
    }
    @Test public void theEmptyPlaceDrawsAPlusAndAddAnApp(){
        assertEquals("plus",DockLabels.ADD);assertEquals("Add an app",DockLabels.ADD_LABEL);
        android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(200,200,android.graphics.Bitmap.Config.ARGB_8888);
        DockGlyphs.draw(new android.graphics.Canvas(b),DockLabels.ADD,100,100,new android.graphics.Paint(),new android.graphics.Path(),new android.graphics.RectF(),null);
    }

    // --- Live TV: the household's app, through the one gate
    @Test public void noLiveTvAppFoundOffersThePicker()throws Exception{
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            prefs().edit().remove(DockPicker.LIVE_PREF).commit();
            // Two apps that look like Live TV (ReamSmartz and an IPTV player): Automatic can't tell which is the household's.
            install("tv.example.iptvsmarters","IPTV Smarters");assertEquals("",LiveTvProtection.livePackage(c.get()));
            assertEquals(Launch.CHOOSE_LIVE,Launch.live(c.get()));
            Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();assertNotNull(started);
            assertEquals(AppPickerActivity.class.getName(),started.getComponent().getClassName());
            assertEquals(AppPickerActivity.LIVE,started.getStringExtra(AppPickerActivity.MODE));assertTrue(started.getBooleanExtra(AppPickerActivity.THEN_OPEN,false));
        }
    }
    @Test public void aChosenIptvAppOpensOnlyThroughTheGateAndIsWatched()throws Exception{
        install(FAMILY,"Family Channels");
        try(ActivityController<AppPickerActivity> p=openPicker(new Intent(app(),AppPickerActivity.class).putExtra(AppPickerActivity.MODE,AppPickerActivity.LIVE).putExtra(AppPickerActivity.THEN_OPEN,true))){
            find(p.get().findViewById(android.R.id.content),Button.class,"Family Channels").performClick();
            Intent next=Shadows.shadowOf(p.get()).getNextStartedActivity();assertNotNull("Home carries on into Live TV",next);assertTrue(next.getBooleanExtra(AppPickerActivity.OPEN_LIVE,false));
        }
        assertEquals(FAMILY,prefs().getString(DockPicker.LIVE_PREF,""));
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            Activity a=c.get();assertEquals(FAMILY,LiveTvProtection.livePackage(a));
            assertEquals("no VPN: the sheet explains","",Launch.live(a));assertFalse(opened(Shadows.shadowOf(a).getNextStartedActivity(),FAMILY));
            LiveTvProtection.forTests=facts(VpnGate.State.CONNECTED);
            assertNull(Launch.live(a));assertTrue(opened(Shadows.shadowOf(a).getNextStartedActivity(),FAMILY));
            Intent watch=Shadows.shadowOf((android.app.Application)app()).getNextStartedService();
            assertNotNull("the safety net watches it",watch);assertEquals(LiveTvGuard.class.getName(),watch.getComponent().getClassName());assertEquals(FAMILY,watch.getStringExtra(LiveTvGuard.LIVE));
        }
    }
    @Test public void aDockTileForTheChosenLiveTvAppGoesThroughTheGate()throws Exception{
        install(FAMILY,"Family Channels");
        prefs().edit().putString("dock","netflix,"+FAMILY).putString(DockPicker.LIVE_PREF,FAMILY).commit();
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            press(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals(1,field("index"));
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            assertFalse("locked from its own tile",opened(Shadows.shadowOf(c.get()).getNextStartedActivity(),FAMILY));
            assertNull("nothing to watch",Shadows.shadowOf((android.app.Application)app()).getNextStartedService());
            LiveTvProtection.forTests=facts(VpnGate.State.CONNECTED);press(KeyEvent.KEYCODE_DPAD_CENTER);
            assertTrue("open from its own tile",opened(Shadows.shadowOf(c.get()).getNextStartedActivity(),FAMILY));
            Intent watch=Shadows.shadowOf((android.app.Application)app()).getNextStartedService();assertNotNull(watch);assertEquals(FAMILY,watch.getStringExtra(LiveTvGuard.LIVE));
        }
    }
    private static boolean opened(Intent i,String pkg){return i!=null&&i.getComponent()!=null&&pkg.equals(i.getComponent().getPackageName());}

    // --- Review of a615a38
    @Test public void anEmptyPlaceNeverReplacesATileWhenTheSavedListHoldsHiddenApps()throws Exception{
        // com.x.iptv is in the saved list but no longer installed, so Home shows Netflix and Disney+ and an empty third place.
        prefs().edit().putString("dock","netflix,com.x.iptv,disney").commit();Intent started;
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            assertEquals(Arrays.asList("netflix","disney"),dockIds());toTheEmptyPlace();press(KeyEvent.KEYCODE_DPAD_CENTER);
            started=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals(2,started.getIntExtra(AppPickerActivity.PLACE,-1));
            assertEquals("the last shown tile","disney",started.getStringExtra(AppPickerActivity.AFTER));c.pause();
        }
        try(ActivityController<AppPickerActivity> p=picker(started)){find(p.get().findViewById(android.R.id.content),Button.class,"Sony Select").performClick();}
        assertEquals("Disney+ stays; the new app follows it","netflix,com.x.iptv,disney,"+SONY_SELECT,prefs().getString("dock",""));
        try(ActivityController<UnifiedPreviewActivity> c=home()){assertEquals(Arrays.asList("netflix","disney",SONY_SELECT),dockIds());assertEquals("focus on the new tile",2,field("index"));}
    }
    @Test public void anUninstalledLiveTvChoiceOffersThePickerAgain()throws Exception{
        install("tv.example.iptvsmarters","IPTV Smarters");   // with ReamSmartz: two candidates, so Automatic finds none
        prefs().edit().putString(DockPicker.LIVE_PREF,"com.example.gone").commit();
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            assertEquals("an app since uninstalled reads as unset","",LiveTvProtection.livePackage(c.get()));
            assertEquals(Launch.CHOOSE_LIVE,Launch.live(c.get()));
            Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();assertNotNull(started);assertEquals(AppPickerActivity.class.getName(),started.getComponent().getClassName());
        }
    }
    @Test public void theLiveTvChoicesLeaveOutVpnAppsSettingsAndUnifiedTv(){
        install(LiveTvProtection.NORDVPN,"NordVPN");install("com.android.tv.settings","Settings");install("com.example.privatevpn","Private Tunnel VPN");
        try(ActivityController<AppPickerActivity> p=openPicker(new Intent(app(),AppPickerActivity.class).putExtra(AppPickerActivity.MODE,AppPickerActivity.LIVE))){
            List<String> offered=p.get().offered();
            assertTrue(offered.toString(),offered.contains("ReamSmartz"));assertEquals("the likeliest first","ReamSmartz",offered.get(0));
            for(String no:new String[]{"NordVPN","Settings","Private Tunnel VPN"})assertFalse(no+" is never Live TV",offered.contains(no));
        }
        assertTrue(DockPicker.notLiveTv(app().getPackageName(),"Unified TV",app().getPackageName()));
    }
    @Test public void thePickerReadsAppsAndArtOffTheMainThreadAndOnlyForRowsOnScreen(){
        for(int i=0;i<30;i++)install("com.example.app"+(char)('a'+i%26)+i,"App "+(char)('A'+i%26)+i);
        List<Runnable> queued=new ArrayList<>();AppPickerActivity.testWorker=queued::add;
        try(ActivityController<AppPickerActivity> p=Robolectric.buildActivity(AppPickerActivity.class,new Intent(app(),AppPickerActivity.class).putExtra(AppPickerActivity.MODE,AppPickerActivity.PLACE).putExtra(AppPickerActivity.PLACE,5)).setup()){
            AppPickerActivity a=p.get();idle(0);
            assertFalse("nothing read on the main thread yet",a.scanned());assertTrue(a.offered().isEmpty());
            assertNotNull("a waiting line meanwhile",find(a.findViewById(android.R.id.content),TextView.class,AppPickerActivity.LOOKING));
            assertEquals("one job: the list",1,queued.size());queued.remove(0).run();idle(0);idle(0);
            assertTrue(a.scanned());assertTrue(a.offered().size()>30);assertNull(find(a.findViewById(android.R.id.content),TextView.class,AppPickerActivity.LOOKING));
            Set<String> asked=a.artAsked();
            assertFalse("some art is asked for",asked.isEmpty());assertTrue("only rows on screen: "+asked.size()+" of "+a.offered().size(),asked.size()<a.offered().size());
            assertEquals("one job per row asked",asked.size(),queued.size());
            String first=asked.iterator().next();
            assertTrue("a placeholder first",a.artOf(first) instanceof android.graphics.drawable.ColorDrawable);
            for(Runnable r:new ArrayList<>(queued))r.run();queued.clear();idle(0);
            android.graphics.drawable.Drawable d=a.artOf(first);assertTrue("then the app's own art",d instanceof android.graphics.drawable.BitmapDrawable);
            int h=Math.round(48*Math.max(.5f,Math.min(a.getResources().getDisplayMetrics().widthPixels/1920f,a.getResources().getDisplayMetrics().heightPixels/1080f)));
            assertEquals("drawn at the row's size",h,((android.graphics.drawable.BitmapDrawable)d).getBitmap().getHeight());
        }
    }

    // --- Apps: one tile per app
    @Test public void eachAppIsOneTileWithItsNameInside(){
        try(ActivityController<AppsActivity> c=Robolectric.buildActivity(AppsActivity.class).setup()){
            AppsActivity a=c.get();View tile=described(a.findViewById(android.R.id.content),"Sony Select");assertNotNull(tile);
            assertTrue("the whole tile takes focus",tile.isFocusable());assertTrue(tile instanceof ViewGroup);
            ViewGroup g=(ViewGroup)tile;ImageView art=null;TextView name=null;
            for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof Button)fail("no separate name button");if(v instanceof ImageView)art=(ImageView)v;else if(v instanceof TextView)name=(TextView)v;}
            assertNotNull("its icon or banner, in the tile",art);assertNotNull("its name, in the same tile",name);
            assertEquals("Sony Select",name.getText().toString());assertEquals(2,name.getMaxLines());assertEquals(android.text.TextUtils.TruncateAt.END,name.getEllipsize());
            assertFalse(name.isFocusable());
            tile.requestFocus();assertTrue(tile.isFocused());
            tile.performLongClick();assertTrue("Hold OK manages it",a.sideOpen());
        }
    }

    // --- Tiles without a mark fill their place; an unknown app without art shows its name
    @Test public void iconsAndBannersFillTheTileSensibly(){
        float[] icon=DockGlyphs.box(false,192,192);
        assertTrue("an icon is 60-70 % of the tile's height: "+icon[1],icon[1]>=.6f*DockGlyphs.TILE_H&&icon[1]<=.7f*DockGlyphs.TILE_H);
        assertEquals("and keeps its shape",icon[0],icon[1],.01f);
        float[] banner=DockGlyphs.box(true,320,180);
        assertTrue("a banner fits inside the tile",banner[0]<=DockGlyphs.TILE_W&&banner[1]<=DockGlyphs.TILE_H);
        assertTrue("and fills most of its width",banner[0]>=.85f*DockGlyphs.TILE_W);assertEquals(16f/9,banner[0]/banner[1],.01f);
        // Drawn: a 192 px (xxxhdpi) icon lands at the size box() gives.
        android.graphics.drawable.ColorDrawable d=new android.graphics.drawable.ColorDrawable(0xFFFFFFFF){@Override public int getIntrinsicWidth(){return 192;}@Override public int getIntrinsicHeight(){return 192;}};
        DockGlyphs.app(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(400,400,android.graphics.Bitmap.Config.ARGB_8888)),200,200,null,d,"Sony Select",new android.graphics.Paint(),new android.graphics.Path(),new android.graphics.RectF());
        assertEquals(Math.round(icon[1]),d.getBounds().height(),1);
        assertEquals("an unknown app with no art shows its name","Sony Select",DockGlyphs.words("mono",false,false,"Sony Select"));
        assertNull("a brand mark shows no words",DockGlyphs.words("prime",false,false,"Prime Video"));
        assertNull("art shows no words",DockGlyphs.words("mono",false,true,"Sony Select"));
        DockGlyphs.app(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(400,400,android.graphics.Bitmap.Config.ARGB_8888)),200,200,null,null,"A rather long application name",new android.graphics.Paint(),new android.graphics.Path(),new android.graphics.RectF());
        for(String g:new String[]{"prime","appletv","smarttube","plus"})DockGlyphs.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(200,200,android.graphics.Bitmap.Config.ARGB_8888)),g,100,100,new android.graphics.Paint(),new android.graphics.Path(),new android.graphics.RectF(),null);
    }

    // --- The dock fits on Home's real view
    private void homeFits()throws Exception{
        try(ActivityController<UnifiedPreviewActivity> c=home()){
            idle(50);int w=home.getWidth(),h=home.getHeight();assertTrue("laid out: "+w+"x"+h,w>0&&h>0);
            Method fit=home.getClass().getDeclaredMethod("dockFit",int.class);fit.setAccessible(true);
            for(int cap:DockCapacity.VALUES){DockCapacity.Fit f=(DockCapacity.Fit)fit.invoke(home,cap);float sx=w/1920f,margin=w*DockCapacity.SAFE/1920f;
                assertTrue(cap+" places on "+w+"x"+h+": left",f.left*sx>=margin-.5f);assertTrue(cap+" places on "+w+"x"+h+": right",(f.left+f.width)*sx<=w-margin+.5f);}
            home.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(Math.max(1,w/4),Math.max(1,h/4),android.graphics.Bitmap.Config.ARGB_8888)));
        }
    }
    @Test public void theDockFitsTheSonysScreen()throws Exception{homeFits();}   // 1920 x 1080 at xhdpi, as the Sony reports
    @Test @Config(qualifiers="w1920dp-h1080dp-land-mdpi") public void theDockFitsAt1080pMdpi()throws Exception{homeFits();}
    @Test @Config(qualifiers="w1280dp-h720dp-land-mdpi") public void theDockFitsAt720p()throws Exception{homeFits();}
    @Test @Config(qualifiers="w1920dp-h1080dp-land-xhdpi") public void theDockFitsAt2160p()throws Exception{homeFits();}
    @Test @Config(qualifiers="w960dp-h720dp-land-xhdpi") public void theDockFitsA4by3Window()throws Exception{homeFits();}
}
