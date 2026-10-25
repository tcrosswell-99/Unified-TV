package io.unifiedtv.preview;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class SettingsJourneyTest {
    private Button find(View v,String name){if(v instanceof Button&&name.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),name);if(b!=null)return b;}return null;}
    private TextView text(View v,String start){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(start))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=text(((ViewGroup)v).getChildAt(i),start);if(t!=null)return t;}return null;}
    private SharedPreferences prefs(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0);}
    @Before public void clean(){prefs().edit().clear().commit();}

    // Module 12 §2: ten categories, in the spec's order, on the left rail.
    @Test public void theTenCategoriesAreThereInOrder(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            View root=controller.get().findViewById(android.R.id.content);
            String[] expected={"Appearance & Home","Library & Media","Profiles & Family","Apps & Shortcuts","Notifications & Doorbell","Smart Home","Live TV & Network Protection","Connections & Devices","Privacy & Data","Updates & About"};
            assertArrayEquals(expected,SettingsActivity.CATEGORIES);for(String c:expected)assertNotNull(c,find(root,c));
        }
    }
    @Test public void categoryFocusChangesPanelAndRightEntersIt(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();View root=a.findViewById(android.R.id.content);Button appearance=find(root,"Appearance & Home");appearance.requestFocus();
            assertNotNull(find(root,"Gallery & slideshow"));assertNull(find(root,"Set up Home Assistant"));
            appearance.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));assertFalse(appearance.hasFocus());
            a.onBackPressed();assertTrue(appearance.hasFocus());
            // SET-008: an integration that isn't set up offers "Set up", not empty switches.
            find(root,"Smart Home").requestFocus();assertNotNull(find(root,"Set up Home Assistant"));assertNull(find(root,"Gallery & slideshow"));
            find(root,"Notifications & Doorbell").requestFocus();assertNull("no camera switches without Home Assistant",text(root,"Closes:"));
        }
    }
    @Test public void vpnStatesRemainDistinctAndCannotImplyRoutingVerification(){
        java.util.Set<String> labels=new java.util.HashSet<>();for(VpnGate.State state:VpnGate.State.values())labels.add(VpnGate.statusLabel(state));assertEquals(4,labels.size());for(boolean routed:new boolean[]{false,true})assertTrue(VpnGate.explanation(true,routed).contains("Live TV stays locked"));
    }
    @Test public void profilesStartsWithTheSwitchAndAdvancedHoldsDiagnostics(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();View root=a.findViewById(android.R.id.content);
            find(root,"Profiles & Family").requestFocus();assertNotNull(find(root,"Switch profile (now Adults)"));assertNotNull(find(root,"Kids settings"));
            assertNotNull("the exact reset command for this app",text(root,"adb shell am broadcast -a io.unifiedtv.RESET_PIN --include-stopped-packages -n "+a.getPackageName()+"/io.unifiedtv.preview.PinReset"));
            find(root,"Updates & About").requestFocus();assertNotNull(find(root,"Owner diagnostics"));assertNotNull(find(root,"Open-source licences"));assertNotNull(find(root,"Artwork"));
            assertNotNull(text(root,"Package: "+a.getPackageName()));
        }
    }
    @Test public void kidsNeedsAPinFirstAndNothingChangesWithoutOne(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            View root=controller.get().findViewById(android.R.id.content);find(root,"Profiles & Family").requestFocus();
            find(root,"Switch profile (now Adults)").performClick();
            AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);Shadows.shadowOf(d).clickOnItem(1);
            assertEquals("adult",prefs().getString("profile","adult"));
        }
    }
    // SET-063: automatic verification only; no attestation button anywhere, never "verified" on this TV.
    @Test public void liveTvIsLockedWithNoWayToAttest(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            View root=controller.get().findViewById(android.R.id.content);find(root,"Live TV & Network Protection").requestFocus();
            assertNotNull(text(root,"Live TV: "));assertNull(text(root,"Live TV: verified"));assertNull(find(root,"Record a passed check"));
        }
    }
    // Owner exception to SET-053: stopping alerts asks first, and then Settings says alarms are off.
    @Test public void stoppingAlertsAsksAndThenSaysAlarmsAreOff(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            View root=controller.get().findViewById(android.R.id.content);find(root,"Notifications & Doorbell").requestFocus();
            assertNotNull(text(root,AlarmWatch.NOT_CONFIGURED_TEXT));assertNull(text(root,"Doorbell presses and smoke or carbon monoxide alarms always show"));
            find(root,"Stop household alerts").performClick();assertFalse("nothing stops before confirmation",prefs().getBoolean(AlarmWatch.STOPPED,false));
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(prefs().getBoolean(AlarmWatch.STOPPED,false));assertNotNull(text(root,AlarmWatch.OFF_NOTICE));
        }
    }
    // SET-026: label visibility per profile, Focused only by default.
    @Test public void labelVisibilityIsChosenPerProfile(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            View root=controller.get().findViewById(android.R.id.content);find(root,"Appearance & Home").requestFocus();
            find(root,"App label visibility: Focused only").performClick();Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(1);
            assertEquals("always",prefs().getString("dock.labelVisibility.adult",""));assertFalse(prefs().contains("dock.labelVisibility.child"));
            assertNotNull(find(root,"App label visibility: Always show labels"));
        }
    }
    // SET-093: a navigation change is a preview until applied.
    @Test public void navigationChangesApplyOnlyOnConfirmation(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            View root=controller.get().findViewById(android.R.id.content);find(root,"Appearance & Home").requestFocus();
            find(root,"Change utilities").performClick();Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);
            Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0);   // AirPlay: Hide
            assertFalse("not applied yet",prefs().contains(HomeUtilities.PREF));assertNotNull(text(root,"Preview (not applied yet)"));
            find(root,"Apply navigation changes").performClick();assertEquals("vpn,voice,settings",prefs().getString(HomeUtilities.PREF,""));
        }
    }
    // SET-003: Frame-only rows appear only in the Frame room.
    @Test public void frameOnlyRowsStayInTheFrameRoom(){
        prefs().edit().putString("room","tcl").commit();
        try(var controller=Robolectric.buildActivity(HouseholdActivity.class).setup()){assertNull(find(controller.get().findViewById(android.R.id.content),"Request native Frame Art Mode"));}
        prefs().edit().putString("room","frame").commit();
        try(var controller=Robolectric.buildActivity(HouseholdActivity.class).setup()){assertNotNull(find(controller.get().findViewById(android.R.id.content),"Request native Frame Art Mode"));}
    }
}
