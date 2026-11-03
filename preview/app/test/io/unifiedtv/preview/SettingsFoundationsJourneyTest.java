package io.unifiedtv.preview;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.*;

/**
 * The v3.4.7 Settings foundations from the remote, all inside the glass sheet (OD-19): preview and revert (SET-025),
 * restore defaults and the top-level reset (SET-004), the privacy dashboard and event log (SET-080/055), quiet hours
 * (SET-052), the connectivity summary (SET-070), larger text (SET-073), media source and playback (SET-031/033), missing
 * dock apps (SET-021) and a settings file arriving from the phone (SET-081).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class SettingsFoundationsJourneyTest {
    private Context app;
    private SharedPreferences prefs(){return app.getSharedPreferences("unified-tv-preview",0);}
    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static void wait(int seconds){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds));}
    private static View root(SettingsActivity a){return a.findViewById(android.R.id.content);}
    private static Button find(View v,String name){if(v instanceof Button&&v.isShown()&&name.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),name);if(b!=null)return b;}return null;}
    private static CheckBox box(View v,String name){if(v instanceof CheckBox&&name.contentEquals(((CheckBox)v).getText()))return (CheckBox)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox b=box(((ViewGroup)v).getChildAt(i),name);if(b!=null)return b;}return null;}
    private static boolean says(View v,String part){if(v instanceof TextView&&!(v instanceof Button)&&((TextView)v).getText().toString().contains(part))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(says(((ViewGroup)v).getChildAt(i),part))return true;return false;}
    private static Button press(SettingsActivity a,String name){Button b=find(root(a),name);assertNotNull("row '"+name+"'",b);b.requestFocus();b.performClick();idle();return b;}
    private static void open(SettingsActivity a,String category){press(a,category);assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());}
    private static AlertDialog dialog(){AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull("a dialog asks first",d);return d;}
    private static String message(AlertDialog d){return String.valueOf(Shadows.shadowOf(d).getMessage());}
    private static void ok(AlertDialog d){d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();}

    @Before public void clean(){app=RuntimeEnvironment.getApplication();prefs().edit().clear().commit();EventLog.clear(app);app.getSharedPreferences(PlayerActivity.RESUME_PREFS,0).edit().clear().commit();SettingsBackup.discard();}
    @After public void reset(){SettingsActivity.haForTests=null;LibraryFolders.testStore=null;LibraryIndex.browser=DlnaClient::browse;SettingsBackup.discard();}

    // SET-025 + SET-073: a new text size is a preview that goes back by itself, unless kept.
    @Test public void aNewTextSizeRevertsAfterFifteenSecondsUnlessKept(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Appearance & Home");
            TextView before=find(root(a),"Text size: Standard");float standard=before.getTextSize();
            press(a,"Text size: Standard");Shadows.shadowOf(dialog()).clickOnItem(2);idle();
            assertEquals(Accessibility.LARGEST,prefs().getString(Accessibility.TEXT_SIZE,""));assertNotNull(a.preview());
            Button keep=find(root(a),"Keep this change");assertNotNull("the preview card offers Keep",keep);
            assertTrue("the sheet already uses the larger text",find(root(a),"Text size: Largest").getTextSize()>standard*1.3f);
            assertTrue(says(root(a),"goes back to Standard in"));
            wait(16);
            assertNull(a.preview());assertEquals("changed back by itself",Accessibility.NORMAL,prefs().getString(Accessibility.TEXT_SIZE,Accessibility.NORMAL));
            assertNull(find(root(a),"Keep this change"));assertNotNull(find(root(a),"Text size: Standard"));
            press(a,"Text size: Standard");Shadows.shadowOf(dialog()).clickOnItem(1);idle();
            press(a,"Keep this change");wait(16);
            assertEquals("kept",Accessibility.LARGE,prefs().getString(Accessibility.TEXT_SIZE,""));assertNull(a.preview());
        }
    }
    @Test public void switchingHighContrastOffIsAPreviewAndLeavingPutsItBack(){
        prefs().edit().putBoolean("highContrast",true).commit();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Appearance & Home");
            CheckBox contrast=box(root(a),"High contrast");assertTrue(contrast.isChecked());assertEquals("states are words, not colour","High contrast, On",String.valueOf(contrast.getContentDescription()));
            contrast.performClick();idle();
            assertFalse(prefs().getBoolean("highContrast",true));assertNotNull(a.preview());
            c.pause().stop();
            assertTrue("leaving Settings before Keep puts it back",prefs().getBoolean("highContrast",false));
        }
    }
    // SET-004: a section's own defaults, with confirmation and a plain list; alarms and other sections stay.
    @Test public void restoreDefaultsForOneSectionSaysWhatChangesAndKeepsAlarms(){
        prefs().edit().putBoolean("reduceMotion",true).putString(DoorbellCamera.CLOSE,"60").putBoolean(AlarmWatch.SHOWN,false).putBoolean(AlarmWatch.STOPPED,true).putString("dock","netflix").commit();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Appearance & Home");press(a,"Advanced appearance");
            press(a,"Restore defaults for Appearance & Home");
            AlertDialog d=dialog();assertTrue(message(d),message(d).startsWith("Goes back to its default: Reduce motion."));assertTrue(message(d),message(d).contains("Kept:"));
            assertTrue("nothing before OK",prefs().getBoolean("reduceMotion",false));
            ok(d);
            assertFalse(prefs().contains("reduceMotion"));
            assertEquals("another section's choice stays","60",prefs().getString(DoorbellCamera.CLOSE,""));assertEquals("netflix",prefs().getString("dock",""));
            assertFalse("alarms stay off exactly as an adult left them",prefs().getBoolean(AlarmWatch.SHOWN,true));assertTrue(prefs().getBoolean(AlarmWatch.STOPPED,false));
            assertFalse(EventLog.recent(app).isEmpty());assertEquals("Appearance & Home: defaults restored",EventLog.recent(app).get(0).words);
        }
    }
    @Test public void theTopLevelResetAsksTwiceAndNeverTouchesAlarmsOrLiveTv(){
        prefs().edit().putBoolean("reduceMotion",true).putString("dock","netflix").putBoolean(QuietHours.ON,true).putBoolean(AlarmWatch.SHOWN,false).putString("room","sony").putString("liveProtection","{}").commit();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Privacy & Data");
            press(a,"Reset Unified TV settings");ok(dialog());
            AlertDialog sure=dialog();assertTrue(message(sure),message(sure).contains("Kept: connections and keys"));
            ok(sure);
            for(String k:new String[]{"reduceMotion","dock",QuietHours.ON,"liveProtection"})assertFalse(k,prefs().contains(k));
            assertFalse(prefs().getBoolean(AlarmWatch.SHOWN,true));assertEquals("sony",prefs().getString("room",""));
        }
    }
    // SET-080 + SET-055: what is stored, cleared item by item; the event log as its own task.
    @Test public void thePrivacyDashboardClearsWatchHistoryPerProfileAndShowsTheEventLog(){
        ResumeStore adults=new ResumeStore(""),kids=new ResumeStore("");long now=System.currentTimeMillis();
        adults.record("a1",600_000,3_600_000,now);kids.record("k1",600_000,3_600_000,now);kids.record("k2",600_000,3_600_000,now);
        app.getSharedPreferences(PlayerActivity.RESUME_PREFS,0).edit().putString(ResumeStore.prefsKey(Profiles.ADULT),adults.json()).putString(ResumeStore.prefsKey(Profiles.CHILD),kids.json()).commit();
        EventLog.record(app,EventLog.DOORBELL,"Doorbell pressed: camera shown");
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Privacy & Data");
            assertNotNull(find(root(a),"Watch history for Kids: 2 titles"));
            press(a,"Watch history for Adults: 1 title");AlertDialog d=dialog();assertTrue(message(d).contains("Kids' places stay"));ok(d);
            assertEquals(0,PrivacyData.history(app,Profiles.ADULT));assertEquals(2,PrivacyData.history(app,Profiles.CHILD));
            assertTrue(says(root(a),"Watch history for Adults: none"));
            assertTrue(says(root(a),"Doorbell camera pictures are never saved"));
            press(a,"Event log: 1 event");
            assertEquals(SettingsOverlay.Phase.TASK,a.overlay().page());assertEquals(SettingsActivity.EVENTS,a.overlay().task());assertEquals(SettingsActivity.PRIVACY,a.overlay().category());
            assertTrue(says(root(a),"Doorbell pressed: camera shown"));
            press(a,"Clear the event log");ok(dialog());
            assertTrue(EventLog.recent(app).isEmpty());assertTrue(says(root(a),"Nothing recorded in the last 7 days."));
            box(root(a),"Keep an event log").performClick();idle();EventLog.record(app,EventLog.ALERT,"Smoke alarm shown");
            assertTrue("off records nothing",EventLog.recent(app).isEmpty());
        }
    }
    // SET-052: only with Home Assistant (nothing to quieten otherwise); smoke and CO are never mentioned as quietened.
    @Test public void quietHoursAreSetPerTv(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Notifications & Doorbell");assertNull("no switch that can't do anything",box(root(a),"Quiet hours"));
        }
        SettingsActivity.haForTests=true;
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Notifications & Doorbell");
            box(root(a),"Quiet hours").performClick();idle();
            assertTrue(prefs().getBoolean(QuietHours.ON,false));
            press(a,"From 22:00");Shadows.shadowOf(dialog()).clickOnItem(46);idle();
            assertEquals(23*60,prefs().getInt(QuietHours.START,0));assertNotNull(find(root(a),"From 23:00"));assertNotNull(find(root(a),"Until 07:00"));
            assertTrue(says(root(a),"Doorbell presses and smoke or carbon monoxide alarms always show."));
        }
    }
    // SET-070
    @Test public void connectivitySaysNotSetUpRatherThanFailing()throws Exception{
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Connections & Devices");
            LibraryJourneyTest.until("the check",a::linksChecked);
            assertTrue(says(root(a),"Media server: not set up"));assertTrue(says(root(a),"Home Assistant: not set up"));
            assertTrue(says(root(a),"Internet: "));assertTrue(says(root(a),"Home network: "));
            assertNotNull(find(root(a),"Check again"));assertNotNull("the TV's own settings stay",find(root(a),"Picture, sound, network & Bluetooth"));
        }
    }
    // SET-033 + SET-031
    @Test public void playbackChoicesAndManagingTheMediaServer()throws Exception{
        LibraryJourneyTest.MapStore store=new LibraryJourneyTest.MapStore();LibraryFolders.testStore=store;
        LibraryFolders.save(app,FakeServer.source(),Collections.singletonList(new LibraryFolders.Folder("v","Video",null,false)));
        prefs().edit().putString(MediaSources.NAME,"Loft").commit();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Library & Media");
            press(a,"When a film was left part-way: Ask each time");Shadows.shadowOf(dialog()).clickOnItem(1);idle();
            assertEquals(PlaybackPrefs.ALWAYS,prefs().getString(PlaybackPrefs.RESUME,""));assertNotNull(find(root(a),"When a film was left part-way: Always resume"));
            CheckBox auto=box(root(a),"Play the next episode automatically");assertFalse("off by default",auto.isChecked());auto.performClick();assertTrue(prefs().getBoolean(PlaybackPrefs.AUTOPLAY,false));
            press(a,"Subtitle size: Standard");Shadows.shadowOf(dialog()).clickOnItem(1);idle();assertEquals(Accessibility.LARGE,prefs().getString(PlaybackPrefs.SUBTITLES,""));
            assertNotNull("the local name",find(root(a),"Name on this TV: Loft"));assertNotNull(find(root(a),"Rescan the folders now"));
            press(a,"Forget this media server");AlertDialog d=dialog();assertTrue(message(d).contains("Watch history stays"));ok(d);
            assertTrue("the folders left this TV",store.read(LibraryFolders.STORE).length()==0);assertFalse(prefs().contains(MediaSources.NAME));
            assertNull(find(root(a),"Forget this media server"));
        }
    }
    // SET-021: a dock app that's gone is listed; removing it changes Home only.
    @Test public void aMissingDockAppCanBeRemovedOrReplaced(){
        prefs().edit().putString("dock","library,com.gone.app").commit();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Apps & Shortcuts");
            assertNull("Library is Unified TV's own",find(root(a),"Library: not installed"));
            press(a,"com.gone.app: not installed");
            AlertDialog d=dialog();assertTrue(message(d).contains("Nothing is installed or uninstalled"));Shadows.shadowOf(d).clickOnItem(1);idle();
            assertEquals("library",prefs().getString("dock",""));assertNull(find(root(a),"com.gone.app: not installed"));
        }
    }
    // SET-081: the phone page lives in the Privacy sheet; a checked file shows what would change before an adult applies it.
    @Test public void aSettingsFileFromThePhoneIsPreviewedThenApplied(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();int width=a.sheet().getLayoutParams().width;open(a,"Privacy & Data");
            press(a,"Back up or restore settings on your phone");
            assertEquals(SettingsActivity.PHONE_BACKUP,a.overlay().task());assertEquals(SettingsActivity.PRIVACY,a.overlay().category());assertEquals("the same half-width sheet",width,a.sheet().getLayoutParams().width);
            assertTrue(says(root(a),"Passwords, keys, the PIN and camera pictures are never in it."));
            Map<String,Object> other=new HashMap<>();other.put("reduceMotion",true);other.put(QuietHours.ON,true);
            SettingsBackup.offer(SettingsBackup.parse(SettingsBackup.export(other,"2026-10-25")));
            a.privacy().incoming();
            assertTrue(says(root(a),"Reduce motion: Off → On"));assertTrue(says(root(a),"Quiet hours: Off → On"));
            assertFalse("nothing before Apply",prefs().contains("reduceMotion"));
            press(a,"Apply these changes");
            assertTrue(prefs().getBoolean("reduceMotion",false));assertTrue(prefs().getBoolean(QuietHours.ON,false));assertNull(SettingsBackup.waiting());
            assertEquals("Settings restored from a backup file",EventLog.recent(app).get(0).words);
        }
    }

    // SET-033 autoplay: the player gets the following episodes, and each next player carries the rest of the queue.
    @Test public void theNextEpisodeQueueTravelsWithThePlayer(){
        LibraryCatalogue.Show show=new LibraryCatalogue.Show("s","Bluey","");
        for(int i=1;i<=3;i++){LibraryCatalogue.Title t=new LibraryCatalogue.Title();t.kind=LibraryCatalogue.Kind.EPISODE;t.season=1;t.episode=i;t.showName="Bluey";t.url="http|80|/b"+i;t.id="b"+i;t.root="k";t.stableId="sb"+i;t.show=show;show.episodes.add(t);}
        android.content.Intent first=PlaybackPrefs.player(app,show.episodes.get(0));
        assertEquals("http|80|/b1",first.getStringExtra("dlnaUrl"));assertEquals("Bluey S1 E1",first.getStringExtra("title"));
        assertArrayEquals(new String[]{"b2","b3"},first.getStringArrayExtra("nextItem"));
        android.content.Intent second=PlaybackPrefs.next(app,first);
        assertEquals("b2",second.getStringExtra("dlnaItem"));assertEquals("k",second.getStringExtra("root"));assertEquals("sb2",second.getStringExtra("stableId"));
        android.content.Intent third=PlaybackPrefs.next(app,second);assertEquals("b3",third.getStringExtra("dlnaItem"));
        assertNull("the last episode has nothing next",PlaybackPrefs.next(app,third));
        LibraryCatalogue.Title film=new LibraryCatalogue.Title();film.url="http|80|/f";film.name="Heat";
        assertNull("a film carries no queue",PlaybackPrefs.player(app,film).getStringArrayExtra("nextUrl"));
    }
}
