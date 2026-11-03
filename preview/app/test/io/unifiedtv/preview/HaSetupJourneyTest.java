package io.unifiedtv.preview;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;

/**
 * Owner's TCL, build run 93: "when I try to connect to Home Assistant it does not work". Which phone-setup step failed
 * is recorded in plain words (no address, no secret) for Owner diagnostics and the event log, and a later success
 * clears it.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class HaSetupJourneyTest {
    private Context app(){return RuntimeEnvironment.getApplication();}
    @Before public void clean(){app().getSharedPreferences(HouseholdSettings.PREFS,0).edit().clear().commit();EventLog.clear(app());}
    @After public void done(){Vault.testKey=null;}
    private static String words(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append('\n');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(words(((ViewGroup)v).getChildAt(i)));return b.toString();}

    @Test public void theFailedStepIsRecordedThenClearedBySuccess()throws Exception{
        PhoneSetup.haProblem(app(),PhoneFlows.STEP_CODE+" (400)");
        assertEquals(PhoneFlows.STEP_CODE+" (400)",app().getSharedPreferences(HouseholdSettings.PREFS,0).getString(PhoneSetup.HA_PROBLEM,""));
        assertEquals("Home Assistant: refused the sign-in code (400)",EventLog.recent(app()).get(0).words);
        Vault.testKey=VaultTest.aes();
        PhoneSetup.saveHa(app(),"http://192.168.1.5:8123","own-long-lived-token-0123456789",true,"signin");
        assertFalse("a success clears it",app().getSharedPreferences(HouseholdSettings.PREFS,0).contains(PhoneSetup.HA_PROBLEM));
        assertEquals("Home Assistant connected",EventLog.recent(app()).get(0).words);
    }
    @Test public void ownerDiagnosticsNamesTheLastFailedStep()throws Exception{
        PhoneSetup.haProblem(app(),PhoneFlows.STEP_FIND+PhoneFlows.VPN_STEP);
        try(ActivityController<OwnerDiagnosticsActivity> c=Robolectric.buildActivity(OwnerDiagnosticsActivity.class).setup()){
            String all="";
            for(int i=0;i<100&&!all.contains("Last phone setup problem");i++){Thread.sleep(50);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();all=words(c.get().findViewById(android.R.id.content));}
            assertTrue(all,all.contains("Last phone setup problem: TV couldn't reach it to check the address (a VPN was in the way), "));
        }
    }
}
