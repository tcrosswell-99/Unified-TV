package io.unifiedtv.preview;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import android.view.*;
import android.widget.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class SettingsJourneyTest {
    private Button find(View v,String name){if(v instanceof Button&&name.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),name);if(b!=null)return b;}return null;}
    @Test public void categoryFocusChangesPanelAndRightEntersIt(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();View root=a.findViewById(android.R.id.content);Button appearance=find(root,"Appearance");appearance.requestFocus();assertNotNull(find(root,"Gallery & slideshow"));assertNull(find(root,"Home Assistant"));
            appearance.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));assertFalse(appearance.hasFocus());
            a.onBackPressed();assertTrue(appearance.hasFocus());
            find(root,"Connections").requestFocus();assertNotNull(find(root,"Home Assistant"));assertNull(find(root,"Gallery & slideshow"));
        }
    }
    @Test public void vpnStatesRemainDistinctAndCannotImplyRoutingVerification(){
        java.util.Set<String> labels=new java.util.HashSet<>();for(VpnGate.State state:VpnGate.State.values())labels.add(VpnGate.statusLabel(state));assertEquals(4,labels.size());for(boolean routed:new boolean[]{false,true})assertTrue(VpnGate.explanation(true,routed).contains("Live TV stays locked"));
    }
    @Test public void profilesOffersTheSwitchAndKidsSettingsAndDiagnosticsAreForAdults(){
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=controller.get();View root=a.findViewById(android.R.id.content);
            find(root,"Profiles").requestFocus();assertNotNull(find(root,"Switch profile"));assertNotNull(find(root,"Kids settings"));
            find(root,"TV & performance").requestFocus();assertNotNull(find(root,"Owner diagnostics"));
        }
    }
}
