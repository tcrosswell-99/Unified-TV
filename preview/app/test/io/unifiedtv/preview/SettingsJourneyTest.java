package io.unifiedtv.preview;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.*;
import android.widget.*;

/**
 * Settings as the module 13 glass overlay: root rows → a category's detail inside the same sheet → an optional wider task →
 * Back to the detail → Back to the root → Back closes. Focus is asserted where the window actually has it
 * (getCurrentFocus), and every message says where it went instead.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class SettingsJourneyTest {
    private Button find(View v,String name){if(v instanceof Button&&name.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),name);if(b!=null)return b;}return null;}
    private TextView text(View v,String start){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(start))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=text(((ViewGroup)v).getChildAt(i),start);if(t!=null)return t;}return null;}
    private static String describe(View v){return v==null?"nothing":v.getClass().getSimpleName()+(v instanceof TextView?" '"+((TextView)v).getText()+"'":"")+(v!=null&&v.getContentDescription()!=null?" ("+v.getContentDescription()+")":"");}
    private SharedPreferences prefs(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0);}
    private static View root(SettingsActivity a){return a.findViewById(android.R.id.content);}
    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    /** Lets the slide (or the safety timer behind it) finish: a few hundred milliseconds of main-looper time. */
    private static void settle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));}
    private static boolean key(SettingsActivity a,int code){return a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));}
    /** Opens a category from the root the way OK does. */
    private void open(SettingsActivity a,String category){Button row=find(root(a),category);assertNotNull("root row "+category+"; phase "+a.overlay().phase(),row);row.performClick();idle();assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());}
    private static void focused(SettingsActivity a,String expected){View f=a.getCurrentFocus();assertTrue("focus should be on '"+expected+"' but is on "+describe(f),f instanceof TextView&&expected.contentEquals(((TextView)f).getText()));}
    @Before public void clean(){prefs().edit().clear().commit();}

    // Module 12 §2 / module 13 §C 00: ten category rows, in module 12's order, at the root of the sheet.
    @Test public void theTenCategoriesAreThereInOrder(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();View root=root(a);
            String[] expected={"Appearance & Home","Library & Media","Profiles & Family","Apps & Shortcuts","Notifications & Doorbell","Smart Home","Live TV & Network Protection","Connections & Devices","Privacy & Data","Updates & About"};
            assertArrayEquals(expected,SettingsActivity.CATEGORIES);for(String c:expected)assertNotNull(c,find(root,c));
            for(String g:new String[]{"Personalise","Connected services","System"})assertNotNull("group label "+g,text(root,g));
            assertNull("the root holds rows only, no category content",find(root,"Gallery & slideshow"));
        }
    }
    // SET-GLASS-06/10: focus goes straight into the sheet; Right (or OK) opens the detail inside it; Back returns to the row.
    @Test public void rootRowOpensItsDetailInsideTheSheetAndBackReturnsToTheRow(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();
            focused(a,"Appearance & Home");
            assertTrue("Right on a root row is handled by the overlay",key(a,KeyEvent.KEYCODE_DPAD_RIGHT));idle();
            assertEquals("Right opened the detail; focus is on "+describe(a.getCurrentFocus()),SettingsOverlay.Phase.DETAIL,a.overlay().page());
            assertEquals(SettingsActivity.APPEARANCE,a.overlay().category());
            assertNotNull(find(root(a),"Gallery & slideshow"));assertNull("the detail replaces the root rows",find(root(a),"Smart Home"));
            View f=a.getCurrentFocus();assertNotNull("focus moved into the detail, not lost",f);assertTrue("focus is inside the sheet: "+describe(f),f.getParent()!=null);
            focused(a,"Gallery & slideshow");
            a.onBackPressed();idle();
            assertEquals(SettingsOverlay.Phase.ROOT,a.overlay().page());focused(a,"Appearance & Home");
            // SET-008: an integration that isn't set up offers "Set up", not empty switches.
            open(a,"Smart Home");assertNotNull(find(root(a),"Set up on your phone"));assertNull(find(root(a),"Gallery & slideshow"));
            a.onBackPressed();idle();focused(a,"Smart Home");
            open(a,"Notifications & Doorbell");assertNull("no camera switches without Home Assistant",text(root(a),"Closes:"));
        }
    }
    // Module 13 §B: Left with nothing further left steps back; at the root it closes the overlay.
    @Test public void leftStepsBackAndAtTheRootCloses(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();settle();assertEquals(SettingsOverlay.Phase.ROOT,a.overlay().phase());
            open(a,"Privacy & Data");
            assertTrue(key(a,KeyEvent.KEYCODE_DPAD_LEFT));idle();assertEquals(SettingsOverlay.Phase.ROOT,a.overlay().phase());focused(a,"Privacy & Data");
            assertTrue(key(a,KeyEvent.KEYCODE_DPAD_LEFT));assertEquals(SettingsOverlay.Phase.CLOSING,a.overlay().phase());
            settle();assertEquals(SettingsOverlay.Phase.CLOSED,a.overlay().phase());assertTrue("the overlay finished; Home underneath keeps its focus",a.isFinishing());
        }
    }
    // GLASS-02/11: Back from the root closes; once closing, rapid keys are swallowed and nothing re-opens.
    @Test public void backFromTheRootClosesAndRapidKeysCannotTrap(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();
            for(int i=0;i<5;i++){assertTrue(key(a,KeyEvent.KEYCODE_DPAD_RIGHT));idle();a.onBackPressed();idle();}
            assertEquals("rapid OK/Back left it at the root, mid-slide or not",SettingsOverlay.Phase.ROOT,a.overlay().page());
            a.onBackPressed();assertEquals(SettingsOverlay.Phase.CLOSING,a.overlay().phase());
            assertTrue("keys are swallowed while closing",key(a,KeyEvent.KEYCODE_DPAD_RIGHT));a.onBackPressed();
            assertEquals(SettingsOverlay.Phase.CLOSING,a.overlay().phase());
            settle();assertTrue(a.isFinishing());assertEquals(SettingsOverlay.Phase.CLOSED,a.overlay().phase());
        }
    }
    // SET-GLASS-02: half the screen, anchored right; a complex task widens the same sheet to about three quarters.
    @Test @Config(qualifiers="w960dp-h540dp-land-xhdpi") public void theSheetCoversHalfAndAComplexTaskWidensIt(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();int screen=a.getResources().getDisplayMetrics().widthPixels;
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)a.sheet().getLayoutParams();
            assertEquals("anchored to the right edge",Gravity.END,lp.gravity);int standard=lp.width;
            assertTrue("about half of "+screen+": "+standard,standard>=screen*.48&&standard<=screen*.56);
            open(a,"Updates & About");assertEquals("a detail keeps the same width",standard,a.sheet().getLayoutParams().width);
            Button advanced=find(root(a),"Advanced");assertNotNull(advanced);advanced.requestFocus();advanced.performClick();idle();
            assertEquals(SettingsOverlay.Phase.TASK,a.overlay().page());assertTrue(a.overlay().wide());
            int wide=a.sheet().getLayoutParams().width;assertEquals("widened by a quarter of the screen",screen/4f,wide-standard,2);
            a.onBackPressed();idle();assertEquals(standard,a.sheet().getLayoutParams().width);
        }
    }
    @Test public void vpnStatesRemainDistinctAndCannotImplyRoutingVerification(){
        java.util.Set<String> labels=new java.util.HashSet<>();for(VpnGate.State state:VpnGate.State.values())labels.add(VpnGate.statusLabel(state));assertEquals(4,labels.size());for(boolean routed:new boolean[]{false,true})assertTrue(VpnGate.explanation(true,routed).contains("Live TV stays locked"));
    }
    @Test public void profilesStartsWithTheSwitchAndAdvancedHoldsDiagnostics(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();
            open(a,"Profiles & Family");assertNotNull(find(root(a),"Switch profile (now Adults)"));assertNotNull(find(root(a),"Kids settings"));
            assertNotNull("the exact reset command for this app",text(root(a),"adb shell am broadcast -a io.unifiedtv.RESET_PIN --include-stopped-packages -n "+a.getPackageName()+"/io.unifiedtv.preview.PinReset"));
            a.onBackPressed();idle();
            open(a,"Updates & About");assertNotNull(find(root(a),"Open-source licences"));assertNotNull(find(root(a),"Artwork"));assertNotNull(text(root(a),"Package: "+a.getPackageName()));
            assertNull("owner checks stay collapsed in Advanced",find(root(a),"Owner diagnostics"));
            Button advanced=find(root(a),"Advanced");advanced.requestFocus();advanced.performClick();idle();
            assertNotNull(find(root(a),"Owner diagnostics"));assertNotNull(find(root(a),"Performance check"));
            a.onBackPressed();idle();assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());focused(a,"Advanced");
        }
    }
    @Test public void kidsNeedsAPinFirstAndNothingChangesWithoutOne(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();open(a,"Profiles & Family");
            find(root(a),"Switch profile (now Adults)").performClick();
            AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);Shadows.shadowOf(d).clickOnItem(1);idle();
            assertEquals("adult",prefs().getString("profile","adult"));
        }
    }
    // SET-063: automatic verification only; no attestation button anywhere, never "verified" on this TV, root says Locked.
    @Test public void liveTvIsLockedWithNoWayToAttest(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();
            Button row=find(root(a),"Live TV & Network Protection");assertEquals("Live TV & Network Protection, Locked",String.valueOf(row.getContentDescription()));
            open(a,"Live TV & Network Protection");
            assertNotNull(text(root(a),"Live TV: "));assertNull(text(root(a),"Live TV: verified"));assertNull(find(root(a),"Record a passed check"));
        }
    }
    // Owner exception to SET-053: stopping alerts asks first, and then Settings says alarms are off.
    @Test public void stoppingAlertsAsksAndThenSaysAlarmsAreOff(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();open(a,"Notifications & Doorbell");
            assertNotNull(text(root(a),AlarmWatch.NOT_CONFIGURED_TEXT));assertNull(text(root(a),"Doorbell presses and smoke or carbon monoxide alarms always show"));
            find(root(a),"Stop household alerts").performClick();assertFalse("nothing stops before confirmation",prefs().getBoolean(AlarmWatch.STOPPED,false));
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            idle();   // a dialog button's click is delivered through the main looper
            assertTrue(prefs().getBoolean(AlarmWatch.STOPPED,false));assertNotNull(text(root(a),AlarmWatch.OFF_NOTICE));
            assertNotNull(find(root(a),"Start household alerts again"));
        }
    }
    // The alert notification's "Stop alerts" opens Notifications in the sheet and asks at once.
    @Test public void stopAlertsFromTheNotificationOpensNotificationsAndAsks(){
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),SettingsActivity.class).putExtra("category",SettingsActivity.NOTIFICATIONS).putExtra("stopAlerts",true);
        try(var controller=Robolectric.buildActivity(SettingsActivity.class,intent).setup()){
            SettingsActivity a=controller.get();
            assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());assertEquals(SettingsActivity.NOTIFICATIONS,a.overlay().category());
            AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);assertTrue(d.isShowing());
            assertFalse("nothing stops before confirmation",prefs().getBoolean(AlarmWatch.STOPPED,false));
            a.onBackPressed();idle();assertEquals("Back from a detail opened directly goes to the root, not out",SettingsOverlay.Phase.ROOT,a.overlay().page());
        }
    }
    // SET-026: label visibility per profile, Focused only by default.
    @Test public void labelVisibilityIsChosenPerProfile(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();open(a,"Appearance & Home");
            find(root(a),"App label visibility: Focused only").performClick();Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(1);idle();
            assertEquals("always",prefs().getString("dock.labelVisibility.adult",""));assertFalse(prefs().contains("dock.labelVisibility.child"));
            assertNotNull(find(root(a),"App label visibility: Always show labels"));
            assertEquals("a change re-renders in place",SettingsOverlay.Phase.DETAIL,a.overlay().page());
        }
    }
    // SET-093: a navigation change is a preview until applied; the editor is a wider task inside the sheet.
    @Test public void navigationChangesApplyOnlyOnConfirmation(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();open(a,"Appearance & Home");
            find(root(a),"Navigation & Shortcuts").performClick();idle();
            assertEquals(SettingsOverlay.Phase.TASK,a.overlay().page());assertEquals(SettingsActivity.NAVIGATION,a.overlay().task());
            find(root(a),"Change utilities").performClick();Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);
            Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);idle();   // AirPlay: Hide
            assertFalse("not applied yet",prefs().contains(HomeUtilities.PREF));assertNotNull(text(root(a),"Preview (not applied yet)"));
            assertEquals("still in the editor",SettingsActivity.NAVIGATION,a.overlay().task());
            find(root(a),"Apply navigation changes").performClick();assertEquals("vpn,voice,settings",prefs().getString(HomeUtilities.PREF,""));
        }
    }
    // Reduce transparency and High contrast: the sheet itself follows at once, and switches say On/Off as words.
    @Test public void readabilitySwitchesRestyleTheSheet(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();open(a,"Appearance & Home");
            View glassBefore=((ViewGroup)a.sheet()).getChildAt(0);
            CheckBox opaque=null;for(View v:a.sheet().getFocusables(View.FOCUS_DOWN))if(v instanceof CheckBox&&"Reduce transparency".contentEquals(((CheckBox)v).getText()))opaque=(CheckBox)v;
            assertNotNull(opaque);assertTrue("switches take focus in touch mode too",opaque.isFocusableInTouchMode());
            opaque.performClick();idle();assertTrue(prefs().getBoolean("opaque",false));
            assertNotSame("the glass was rebuilt without its frost layer",glassBefore,((ViewGroup)a.sheet()).getChildAt(0));
            assertFalse("no frost under a solid sheet",((ViewGroup)a.sheet()).getChildAt(0) instanceof SettingsGlass.Frost);
        }
    }
    // A profile switch recreates the activity: the overlay comes back on the same page without sliding in again.
    @Test public void recreatingKeepsThePage(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            open(controller.get(),"Profiles & Family");
            controller.recreate();SettingsActivity b=controller.get();
            assertEquals(SettingsOverlay.Phase.DETAIL,b.overlay().phase());assertEquals(SettingsActivity.PROFILES,b.overlay().category());
            assertNotNull(find(root(b),"Switch profile (now Adults)"));
        }
    }
    private java.util.List<Button> all(View v,String name,java.util.List<Button> out){if(v instanceof Button&&name.contentEquals(((Button)v).getText()))out.add((Button)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)all(((ViewGroup)v).getChildAt(i),name,out);return out;}
    private boolean anyText(View v,String part){if(v instanceof TextView&&((TextView)v).getText().toString().contains(part))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(anyText(((ViewGroup)v).getChildAt(i),part))return true;return false;}
    private static String title(SettingsActivity a){ViewGroup header=(ViewGroup)((ViewGroup)((ViewGroup)a.sheet()).getChildAt(((ViewGroup)a.sheet()).getChildCount()-1)).getChildAt(0);
        for(int i=0;i<header.getChildCount();i++)if(header.getChildAt(i) instanceof LinearLayout)return String.valueOf(((TextView)((LinearLayout)header.getChildAt(i)).getChildAt(0)).getText());return "";}

    // Owner, 25 October 2026: Smart Home is status first in one plain line and one action; no address, test, save or
    // "enable connection" juggling. Setting up happens on the phone, inside the same half-width sheet.
    @Test @Config(qualifiers="w960dp-h540dp-land-xhdpi") public void smartHomeIsStatusFirstAndPhoneSetupOpensInTheSheet(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();int standard=a.sheet().getLayoutParams().width;
            open(a,"Smart Home");
            assertNotNull("one plain status line",text(root(a),"Home Assistant: "));
            for(String gone:new String[]{"Test connection","Save","Forget connection","Enable connection","Use a different address","Set up Home Assistant"})assertNull(gone+" is gone",find(root(a),gone));
            for(String jargon:new String[]{"trusted LAN","Credentials not configured","long-lived","HTTP"})assertFalse(jargon,anyText(root(a),jargon));
            assertNull("Disconnect only once connected",find(root(a),"Disconnect"));
            assertNotNull(text(root(a),"Football fixtures: not set up"));
            assertEquals("Home Assistant's and football's own phone setup",2,all(root(a),"Set up on your phone",new java.util.ArrayList<>()).size());
            Button phone=find(root(a),"Set up on your phone");phone.requestFocus();phone.performClick();idle();
            assertEquals(SettingsOverlay.Phase.TASK,a.overlay().page());assertEquals(SettingsActivity.PHONE_HA,a.overlay().task());
            assertEquals("the same half-width sheet, never a full-screen page",standard,a.sheet().getLayoutParams().width);
            assertEquals("Home Assistant",title(a));
            assertTrue("the TV says what is happening",anyText(root(a),PhoneSetup.NO_NETWORK)||anyText(root(a),PhoneSetup.WAITING));
            a.onBackPressed();idle();
            assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());focused(a,"Set up on your phone");
            Button advanced=find(root(a),"Advanced");assertNotNull(advanced);advanced.requestFocus();advanced.performClick();idle();
            assertEquals(SettingsActivity.HA_ADVANCED,a.overlay().task());assertEquals(standard,a.sheet().getLayoutParams().width);
            assertNotNull("typing on the TV only under Advanced",find(root(a),"Paste a token on this TV"));assertNotNull(find(root(a),"Change the address"));
            a.onBackPressed();idle();focused(a,"Advanced");
            Button football=all(root(a),"Set up on your phone",new java.util.ArrayList<>()).get(1);football.requestFocus();football.performClick();idle();
            assertEquals(SettingsActivity.PHONE_FOOTBALL,a.overlay().task());assertEquals("Football fixtures",title(a));
            assertTrue(anyText(root(a),"API-Football"));
            a.onBackPressed();idle();a.onBackPressed();idle();focused(a,"Smart Home");
        }
    }
    // Settings > Library: film posters show whether a key is saved and open their phone setup in the sheet.
    @Test public void filmPostersOpenTheirPhoneSetup(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();open(a,"Library & Media");
            Button posters=find(root(a),"Film posters: not set up");assertNotNull(posters);posters.requestFocus();posters.performClick();idle();
            assertEquals(SettingsActivity.PHONE_TMDB,a.overlay().task());assertEquals(SettingsActivity.LIBRARY,a.overlay().category());assertEquals("Film posters",title(a));
            assertTrue(anyText(root(a),"TMDB"));assertNull("nothing to remove yet",find(root(a),"Remove the film poster key"));
            a.onBackPressed();idle();assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());focused(a,"Film posters: not set up");
        }
    }
    // Owner, 25 October 2026: household setup in plain words, only what applies to this TV, inside the sheet. The Frame
    // runs the separate Samsung app, so its rows never appear on Android, even for a room saved by an older version.
    @Test public void householdSetupIsInTheSheetInPlainWordsForThisTvOnly(){
        prefs().edit().putString("room","frame").commit();
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),SettingsActivity.class).putExtra("category",SettingsActivity.NOTIFICATIONS).putExtra("task",SettingsActivity.HOUSEHOLD);
        try(var controller=Robolectric.buildActivity(SettingsActivity.class,intent).setup()){
            SettingsActivity a=controller.get();
            assertEquals(SettingsOverlay.Phase.TASK,a.overlay().page());assertEquals(SettingsActivity.HOUSEHOLD,a.overlay().task());assertEquals(SettingsActivity.NOTIFICATIONS,a.overlay().category());
            assertEquals("Doorbell, alarms & this TV",title(a));
            assertNotNull(find(root(a),"This TV: TCL"));assertNotNull(find(root(a),"Doorbell button: none chosen"));
            for(String frame:new String[]{"Request native Frame Art Mode","Samsung TV Plus script","Art Mode return script","Doorbell entry script","Doorbell return script"})assertNull(frame,find(root(a),frame));
            assertFalse(anyText(root(a),"Samsung"));assertNull("Xbox is the Sony's",find(root(a),"Turn on the Xbox: none chosen"));
            for(String jargon:new String[]{"binary sensor","entity","owner script","HDMI","Experimental","APK","Tizen"})assertFalse(jargon,anyText(root(a),jargon));
            a.onBackPressed();idle();assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());assertEquals(SettingsActivity.NOTIFICATIONS,a.overlay().category());
        }
        prefs().edit().putString("room","sony").commit();
        try(var controller=Robolectric.buildActivity(SettingsActivity.class,intent).setup()){assertNotNull(find(root(controller.get()),"Turn on the Xbox: none chosen"));}
    }
    // The older entries hand over to the sheet: no full-screen setup page is left for Home Assistant.
    @Test public void olderEntriesOpenTheSheet(){
        try(var controller=Robolectric.buildActivity(HouseholdActivity.class).setup()){
            Intent next=Shadows.shadowOf(controller.get()).getNextStartedActivity();assertNotNull(next);
            assertEquals(SettingsActivity.class.getName(),next.getComponent().getClassName());assertEquals(SettingsActivity.HOUSEHOLD,next.getStringExtra("task"));assertTrue(controller.get().isFinishing());
        }
        Intent ha=new Intent(RuntimeEnvironment.getApplication(),ServiceDiscoveryActivity.class).putExtra("key","ha");
        try(var controller=Robolectric.buildActivity(ServiceDiscoveryActivity.class,ha).setup()){
            Intent next=Shadows.shadowOf(controller.get()).getNextStartedActivity();assertNotNull(next);
            assertEquals(SettingsActivity.class.getName(),next.getComponent().getClassName());assertEquals(SettingsActivity.PHONE_HA,next.getStringExtra("task"));
        }
        Intent typed=new Intent(RuntimeEnvironment.getApplication(),ConnectionsActivity.class).putExtra("key","ha");
        try(var controller=Robolectric.buildActivity(ConnectionsActivity.class,typed).setup()){
            Intent next=Shadows.shadowOf(controller.get()).getNextStartedActivity();assertNotNull(next);assertEquals(SettingsActivity.HA_ADVANCED,next.getStringExtra("task"));
        }
    }
}
