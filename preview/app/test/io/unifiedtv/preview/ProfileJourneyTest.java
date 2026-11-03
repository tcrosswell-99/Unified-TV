package io.unifiedtv.preview;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;

/**
 * SET-040 / PROFILE-01: Unified TV opens in the profile used last, after a restart, without asking. SET-041: Settings
 * opened while Kids is on (an adult unlocked the gear with the PIN) keeps adult-only things hidden or behind the PIN:
 * Advanced and Film posters are not shown, and changing the PIN, Kids settings, stopping alerts or moving to Adults all
 * ask for the adult PIN first. With no PIN on record, Kids can still return to Adults (never a trap).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class ProfileJourneyTest {
    static final String PIN="2468";
    private static SharedPreferences prefs(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0);}
    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static Button find(View v,String start){if(v instanceof Button&&((Button)v).getText().toString().startsWith(start)&&v.isShown())return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),start);if(b!=null)return b;}return null;}
    private static Button find(SettingsActivity a,String start){return find(a.findViewById(android.R.id.content),start);}
    private static void open(SettingsActivity a,String category){Button row=find(a,category);assertNotNull(category,row);row.performClick();idle();assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());}
    private static void back(SettingsActivity a){a.onBackPressed();idle();}
    private static AlertDialog dialog(){AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull("a dialog",d);assertTrue(d.isShowing());return d;}
    private static String title(AlertDialog d){return String.valueOf(Shadows.shadowOf(d).getTitle());}
    @Before public void clean(){prefs().edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();ShadowAlertDialog.reset();}
    private void kids(boolean pin)throws Exception{SharedPreferences.Editor e=prefs().edit().putString(Profiles.PREF,Profiles.CHILD);if(pin)e.putString("pin",Pins.record(PIN));e.commit();}

    // --- SET-040 / PROFILE-01
    @Test public void opensInTheProfileUsedLastAfterARestartWithoutAsking()throws Exception{
        for(String profile:new String[]{Profiles.CHILD,Profiles.ADULT,Profiles.CHILD}){
            prefs().edit().putString(Profiles.PREF,profile).commit();ShadowAlertDialog.reset();
            ActivityController<UnifiedPreviewActivity> c=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();
            try{assertEquals("the stored profile, unchanged by start-up",profile,Profiles.current(prefs()));
                assertNull("no profile question at start",ShadowAlertDialog.getLatestAlertDialog());
                assertNull("nothing else opens instead of Home",Shadows.shadowOf(c.get()).getNextStartedActivity());
                View home=RemoteJourneyTest.homeIn(c.get());java.lang.reflect.Method labels=home.getClass().getDeclaredMethod("labels");labels.setAccessible(true);
                prefs().edit().putString(DockLabels.key(Profiles.CHILD),DockLabels.HIDDEN).commit();
                assertEquals("that profile's own settings",Profiles.CHILD.equals(profile)?DockLabels.HIDDEN:DockLabels.FOCUSED_ONLY,labels.invoke(home));
                prefs().edit().remove(DockLabels.key(Profiles.CHILD)).commit();
            }finally{c.pause().stop().destroy();}
        }
    }
    @Test public void aSwitchIsWhatTheNextStartUses()throws Exception{
        prefs().edit().putString("pin",Pins.record(PIN)).commit();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=c.get();
            open(a,"Profiles & Family");find(a,"Switch profile").performClick();idle();
            Shadows.shadowOf(dialog()).clickOnItem(1);idle();   // Kids: no PIN needed to go to Kids
        }
        assertEquals(Profiles.CHILD,Profiles.current(prefs()));
        ActivityController<UnifiedPreviewActivity> home=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();
        try{assertTrue("restarted in Kids",Profiles.kids(home.get()));}finally{home.pause().stop().destroy();}
    }

    // --- SET-041
    @Test public void adultOnlyThingsAreHiddenInKids()throws Exception{
        kids(true);
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=c.get();
            open(a,"Updates & About");
            assertNull("no Advanced in Kids",find(a,"Advanced"));
            assertTrue(LibraryJourneyTest.says(a,"Advanced is for Adults."));
            back(a);open(a,"Library & Media");
            assertNull("no Film posters (TMDB key) in Kids",find(a,"Film posters"));assertNull("no library setup in Kids",find(a,"Set up Your Films & TV"));
        }
    }
    @Test public void kidsToAdultsNeedsThePin()throws Exception{
        kids(true);
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=c.get();
            open(a,"Profiles & Family");Button sw=find(a,"Switch profile");assertNotNull(sw);assertTrue(sw.getText().toString().contains("Kids"));
            sw.performClick();idle();AlertDialog who=dialog();assertEquals("Who's watching?",title(who));
            Shadows.shadowOf(who).clickOnItem(0);idle();
            AlertDialog pin=dialog();assertEquals("the adult PIN first","Adult access",title(pin));
            assertEquals("still Kids until the PIN is right",Profiles.CHILD,Profiles.current(prefs()));
            Pins.type(pin,"1111");assertEquals(Profiles.CHILD,Profiles.current(prefs()));assertEquals("PIN not recognised",ShadowToast.getTextOfLatestToast());
            find(a,"Switch profile").performClick();idle();Shadows.shadowOf(dialog()).clickOnItem(0);idle();
            Pins.type(dialog(),PIN);
            assertEquals("the right PIN: Adults",Profiles.ADULT,Profiles.current(prefs()));
        }
    }
    @Test public void pinKidsSettingsAndAlertsAskForThePinInKids()throws Exception{
        kids(true);
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=c.get();
            open(a,"Profiles & Family");
            find(a,"Set or change adult PIN").performClick();idle();assertEquals("not the new-PIN box: the adult PIN first","Adult access",title(dialog()));dialog().dismiss();
            find(a,"Kids settings").performClick();idle();assertEquals("Adult access",title(dialog()));dialog().dismiss();
            assertNull("Kids settings didn't open",Shadows.shadowOf(a).getNextStartedActivity());
            back(a);open(a,"Notifications & Doorbell");
            find(a,"Stop household alerts").performClick();idle();assertEquals("Adult access",title(dialog()));dialog().dismiss();
            assertFalse("alerts still on",prefs().getBoolean(AlarmWatch.STOPPED,false));
        }
    }
    @Test public void kidsWithNoPinOnRecordCanReturnToAdultsButChangeNothingElse()throws Exception{
        kids(false);
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){SettingsActivity a=c.get();
            open(a,"Notifications & Doorbell");ShadowAlertDialog.reset();
            find(a,"Stop household alerts").performClick();idle();
            assertNull("nothing to unlock with: Kids can't stop alerts",ShadowAlertDialog.getLatestAlertDialog());assertFalse(prefs().getBoolean(AlarmWatch.STOPPED,false));
            back(a);open(a,"Profiles & Family");find(a,"Switch profile").performClick();idle();Shadows.shadowOf(dialog()).clickOnItem(0);idle();
            assertEquals("never a trap: back to Adults",Profiles.ADULT,Profiles.current(prefs()));
        }
    }
}
