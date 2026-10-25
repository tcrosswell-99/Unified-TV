package io.unifiedtv.preview;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.shadows.ShadowAlertDialog;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Settings rows about this TV, from the remote and inside the glass sheet (OD-19): What this TV can do (SET-006/003),
 * the sound output (SET-072), the remote's key help (SET-071), Favourites and picture choices (SET-032/034), and the
 * guided setup's Settings rows (SET-010/015).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class TvSettingsJourneyTest {
    private Context app;
    private SharedPreferences prefs(){return app.getSharedPreferences("unified-tv-preview",0);}
    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static View root(SettingsActivity a){return a.findViewById(android.R.id.content);}
    private static Button find(View v,String name){if(v instanceof Button&&v.isShown()&&name.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),name);if(b!=null)return b;}return null;}
    private static boolean says(View v,String part){if(v instanceof TextView&&!(v instanceof Button)&&v.isShown()&&((TextView)v).getText().toString().contains(part))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(says(((ViewGroup)v).getChildAt(i),part))return true;return false;}
    private static Button press(SettingsActivity a,String name){Button b=find(root(a),name);assertNotNull("row '"+name+"'",b);b.requestFocus();b.performClick();idle();return b;}
    private static void open(SettingsActivity a,String category){press(a,category);assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());}
    private static AlertDialog dialog(){AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull("a dialog asks first",d);return d;}
    private static void ok(AlertDialog d){d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();}
    private static Capabilities.Facts preview(){Capabilities.Facts f=new Capabilities.Facts();f.sdk=30;f.release="11";f.outputs=true;return f;}

    @Before public void clean(){app=RuntimeEnvironment.getApplication();prefs().edit().clear().commit();ShadowAlertDialog.reset();Posters.reset();}
    @After public void reset(){Capabilities.forTests=null;SettingsActivity.haForTests=null;Posters.get(app).forgetChoices();Posters.reset();}

    // --- SET-006 / SET-003
    @Test public void whatThisTvCanDoListsEveryFeatureByStateInsideTheSheet(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Updates & About");
            press(a,"What this TV can do");
            assertEquals(SettingsOverlay.Phase.TASK,a.overlay().page());assertEquals(SettingsActivity.CAPABILITIES,a.overlay().task());
            assertFalse("half the screen, like every Settings page",a.overlay().wide());
            assertTrue(says(root(a),"Never guessed from the TV's brand"));
            // Read from this (Robolectric) TV: the preview package, nothing set up, Android 11.
            for(String feature:new String[]{"Unified TV as the Home screen","Your Films & TV","Film posters","Live TV","Frosted glass in Settings"})assertNotNull(feature,find(root(a),feature));
            assertTrue("the preview says why it is never Home",says(root(a),"This is the Unified TV preview"));
            assertTrue(says(root(a),Capabilities.State.SETUP_REQUIRED.words));assertTrue(says(root(a),Capabilities.State.UNSUPPORTED_ON_DEVICE.words));
            assertEquals("each row's state is spoken with it","Your Films & TV, "+Capabilities.State.SETUP_REQUIRED.words,String.valueOf(find(root(a),"Your Films & TV").getContentDescription()));
            // OK on a feature goes to its next step inside the same sheet; Back comes out of it.
            press(a,"Your Films & TV");
            assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());assertEquals(SettingsActivity.LIBRARY,a.overlay().category());
            a.onBackPressed();idle();assertEquals(SettingsOverlay.Phase.ROOT,a.overlay().page());
        }
    }
    @Test public void aFeatureWithNoNextStepExplainsItself(){
        Capabilities.forTests=preview();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Updates & About");press(a,"What this TV can do");
            press(a,"Frosted glass in Settings");
            AlertDialog d=dialog();assertEquals("Frosted glass in Settings",String.valueOf(Shadows.shadowOf(d).getTitle()));
            assertTrue(String.valueOf(Shadows.shadowOf(d).getMessage()).startsWith("Not available on this TV. Android 11"));
            assertEquals("still on the same page",SettingsActivity.CAPABILITIES,a.overlay().task());
        }
    }
    @Test public void aTvWhereEverythingWorksShowsOnlyWorking(){
        Capabilities.Facts f=preview();f.sdk=34;f.launcherApp=true;f.homeRole=true;f.isHome=true;f.librarySetUp=true;f.tmdbKey=true;f.airPin=true;f.airPlayReady=true;f.nordVpn=true;
        f.liveVerifier=true;f.liveVerified=true;f.haSaved=true;f.haConnected=true;f.footballKey=true;f.overlays=true;f.notificationAccess=true;f.kidsGuardAllowed=true;
        f.speech=true;f.microphone=true;f.exactOutput=true;f.reviewBuild=true;Capabilities.forTests=f;
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Updates & About");press(a,"What this TV can do");
            assertTrue(says(root(a),"Working"));
            for(Capabilities.State s:Capabilities.State.values())if(s!=Capabilities.State.AVAILABLE)assertFalse("no empty "+s.words+" group",says(root(a),s.words));
        }
    }

    // --- SET-003: rows for features this TV doesn't have are hidden or say why
    @Test public void unsupportedFeaturesAreHiddenOrExplained(){
        Capabilities.forTests=preview();   // everyday build, no speech service
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Connections & Devices");
            assertNull("no remote button check outside the review build",find(root(a),"Check your remote's buttons"));
            assertTrue(says(root(a),"takes typed requests"));assertFalse(says(root(a),"speech provider only when you start it"));
            assertNotNull("typing still works, so Ask your TV stays",find(root(a),"Ask your TV"));
        }
        Capabilities.Facts f=preview();f.reviewBuild=true;f.speech=true;Capabilities.forTests=f;
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Connections & Devices");
            assertNotNull(find(root(a),"Check your remote's buttons"));assertTrue(says(root(a),"speech provider only when you start it"));
            press(a,"Check your remote's buttons");
            assertEquals(RemoteCheckActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
        }
    }

    // --- SET-072
    @Test public void connectionsShowsWhereTheSoundPlaysAndOpensTheTvsOwnSettings(){
        AudioManager am=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
        Shadows.shadowOf(am).setOutputDevices(Arrays.asList(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build(),
            AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_HDMI_ARC).build()));
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Connections & Devices");
            assertTrue("Android 11 lists outputs; Unified TV says it's the likely one",says(root(a),"Sound probably plays through: HDMI ARC"));
            assertTrue(says(root(a),"Connected: TV speakers · HDMI ARC"));
            assertTrue("no promise of changing it",says(root(a),"changed with the remote and in the TV's own settings"));
            assertNull("no volume or output switch of its own",find(root(a),"Volume"));
            press(a,"Open the TV's sound settings");
            Intent started=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(started);
            assertTrue(started.getAction(),android.provider.Settings.ACTION_SOUND_SETTINGS.equals(started.getAction())||android.provider.Settings.ACTION_SETTINGS.equals(started.getAction()));
        }
    }

    // --- SET-071
    @Test public void remoteHelpExplainsTheButtonsAndHomeOnThisTv(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Connections & Devices");
            assertTrue("Home's behaviour, said plainly on the page",says(root(a),"Home: Opens the TV's own Home screen"));
            press(a,"What the buttons do");
            assertEquals(SettingsActivity.REMOTE_HELP,a.overlay().task());
            for(String key:new String[]{"OK","Hold OK","Up","Down","Back","Home"}){Button b=find(root(a),key);assertNotNull(key,b);assertTrue("focusable, so the remote can move through the list",b.isFocusable());}
            assertTrue(says(root(a),"straight to the small icons at the top right"));assertTrue(says(root(a),"This preview never becomes the Home screen"));
            Button up=find(root(a),"Up");up.performClick();idle();assertEquals("a help row does nothing",SettingsActivity.REMOTE_HELP,a.overlay().task());
            a.onBackPressed();idle();assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());assertEquals(SettingsActivity.CONNECTIONS,a.overlay().category());
        }
    }

    // --- SET-032 / SET-034
    @Test public void favouritesAreCountedPerProfileAndClearedAfterAConfirmation(){
        prefs().edit().putString(Favourites.key(Profiles.ADULT),Favourites.write(Arrays.asList("t:dlna:a:1","t:dlna:a:2"))).putString(Favourites.key(Profiles.CHILD),Favourites.write(Arrays.asList("t:dlna:a:3"))).commit();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Library & Media");
            press(a,"Favourites: Adults 2 · Kids 1 · Clear");
            AlertDialog pick=dialog();Shadows.shadowOf(pick).clickOnItem(1);idle();   // Kids'
            AlertDialog sure=dialog();assertNotSame(pick,sure);assertTrue(String.valueOf(Shadows.shadowOf(sure).getMessage()).contains("the other profile's favourites are kept"));
            assertEquals("nothing cleared before OK",1,Favourites.count(prefs(),Profiles.CHILD));
            ok(sure);
            assertEquals(0,Favourites.count(prefs(),Profiles.CHILD));assertEquals(2,Favourites.count(prefs(),Profiles.ADULT));
            assertNotNull("the row redraws with the new count",find(root(a),"Favourites: Adults 2 · Kids 0 · Clear"));
        }
    }
    @Test public void pictureChoicesCanBeForgottenAfterAConfirmation(){
        Posters.get(app).choose("uuid:nas","t:dlna:nas:1",null);Posters.get(app).choose("uuid:nas","t:dlna:nas:2",null);
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Library & Media");
            press(a,"Picture choices: 2 · Forget");
            AlertDialog d=dialog();assertEquals("Forget 2 picture choices?",String.valueOf(Shadows.shadowOf(d).getTitle()));
            assertEquals("nothing forgotten before OK",2,Posters.get(app).choices());
            ok(d);assertEquals(0,Posters.get(app).choices());
            assertNull(find(root(a),"Picture choices: 2 · Forget"));assertTrue(says(root(a),"Picture choices: none"));
        }
    }

    // --- SET-010 / SET-015 from Settings
    @Test public void theGuidedSetupRunsAgainWholeOrOnePartFromSettings(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Updates & About");
            press(a,"Run the guided setup");
            Intent started=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(FirstRunActivity.class.getName(),started.getComponent().getClassName());
            assertNull(started.getStringExtra(FirstRunActivity.ONLY));
            assertEquals(FirstRun.IN_PROGRESS,FirstRun.load(prefs()).status);
            FirstRun.save(prefs(),FirstRun.at(FirstRun.load(prefs()),FirstRun.LIBRARY));
            c.pause().resume();idle();
            assertNotNull("the row says where it was left",find(root(a),"Continue the guided setup (step 4 of 5)"));
            press(a,"Just one part");Shadows.shadowOf(dialog()).clickOnItem(1);idle();
            Intent part=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(FirstRunActivity.class.getName(),part.getComponent().getClassName());
            assertEquals(FirstRun.APPS,part.getStringExtra(FirstRunActivity.ONLY));
        }
    }
    @Test public void kidsSeesNoGuidedSetupRows(){
        prefs().edit().putString(Profiles.PREF,Profiles.CHILD).commit();
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Updates & About");
            assertNull(find(root(a),"Run the guided setup"));assertNull(find(root(a),"Just one part"));
            assertNotNull("what the TV can do is still readable",find(root(a),"What this TV can do"));
        }
    }
}
