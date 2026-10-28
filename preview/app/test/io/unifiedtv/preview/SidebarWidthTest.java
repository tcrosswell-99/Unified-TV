package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.util.*;

/**
 * OD-19 on a 1080p TV (960 × 540 dp at xhdpi): representative settings and setup screens open as the glass sidebar on the
 * right, about half the screen (three quarters for a wide task), over a still, visible Home. SidebarRuleTest makes sure
 * every settings or setup screen is one of these.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class SidebarWidthTest {
    private LibrarySetupActivity.Finder finder;
    @Before public void clean(){RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        finder=LibrarySetupActivity.finder;LibrarySetupActivity.finder=(c,r)->Collections.emptyList();}   // no network in tests
    @After public void restore(){LibrarySetupActivity.finder=finder;}

    private static boolean translucent(Activity a){android.content.res.TypedArray t=a.getTheme().obtainStyledAttributes(new int[]{android.R.attr.windowIsTranslucent});try{return t.getBoolean(0,false);}finally{t.recycle();}}
    private static String words(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append(' ');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(words(((ViewGroup)v).getChildAt(i)));return b.toString();}

    /** The sheet's share of the screen, after checking it is the sidebar: on the right, on screen, holding the page. */
    private static float share(GlassScreen s,String title){
        String name=s.getClass().getSimpleName();
        assertTrue(name+" is translucent, so Home stays visible behind it",translucent(s));
        assertNotNull(name+" has a sheet",s.sheet);
        assertTrue(name+": the sheet is what is on screen",s.sheet.isAttachedToWindow());
        assertEquals(name+" sits on the right",Gravity.END,((FrameLayout.LayoutParams)s.sheet.getLayoutParams()).gravity);
        assertEquals(name+" runs top to bottom",ViewGroup.LayoutParams.MATCH_PARENT,s.sheet.getLayoutParams().height);
        assertTrue(name+" shows its page in the sheet: "+words(s.sheet),words(s.sheet).contains(title));
        int screen=s.getResources().getDisplayMetrics().widthPixels;assertEquals("a 1080p TV",1920,screen);
        return s.sheet.getLayoutParams().width/(float)screen;
    }
    private <T extends GlassScreen> float open(Class<T> type,Intent extras,String title){
        Intent i=new Intent(RuntimeEnvironment.getApplication(),type);if(extras!=null)i.putExtras(extras);
        try(ActivityController<T> c=Robolectric.buildActivity(type,i).setup()){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();return share(c.get(),title);}
    }
    private static void half(String name,float share){assertTrue(name+" is about half the screen, not a full page ("+share+")",share>.45f&&share<.6f);}
    private static void wide(String name,float share){assertTrue(name+" is about three quarters for a wide task ("+share+")",share>.7f&&share<.82f);}

    @Test public void kidsSettingsIsHalfTheScreen(){half("Kids settings",open(KidsActivity.class,null,"Kids settings"));}
    @Test public void musicIsHalfTheScreen(){half("Music",open(MusicActivity.class,null,"Music"));}
    @Test public void performanceIsHalfTheScreen(){half("Performance",open(PerformanceActivity.class,null,"TV Performance & Cleanup"));}
    @Test public void choosingFoldersIsHalfTheScreen(){half("Library setup",open(LibrarySetupActivity.class,null,"Find your media server"));}
    @Test public void ownerDiagnosticsIsWide(){
        // Kids: the page says it is for Adults and stops there, so nothing searches the network in a test.
        RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0).edit().putString(Profiles.PREF,Profiles.CHILD).commit();
        wide("Owner diagnostics",open(OwnerDiagnosticsActivity.class,null,"Owner diagnostics"));
    }
    @Test public void buildMyTvIsWide(){wide("Build My TV",open(BuildMyTvActivity.class,null,"Build My TV"));}
    @Test public void backClosesKidsSettings(){
        try(ActivityController<KidsActivity> c=Robolectric.buildActivity(KidsActivity.class).setup()){
            KidsActivity k=c.get();k.onBackPressed();assertTrue("Back closes Kids settings",k.isFinishing());
        }
    }
}
