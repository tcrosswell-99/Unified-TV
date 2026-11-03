package io.unifiedtv.preview;
import android.content.pm.ActivityInfo;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Owner, TCL, 25 October 2026: apps open at their normal screen, and a short visit comes back to Unified TV where Android allows. */
public class LaunchTest {
    @Test public void theNormalEntryIsNeverASettingsScreen(){
        assertEquals("com.nordvpn.android.tv.TvMainActivity",Launch.pick(Arrays.asList("com.nordvpn.android.tv.TvSettingsActivity","com.nordvpn.android.tv.TvMainActivity")));
        assertEquals("a.Home",Launch.pick(Arrays.asList("a.PreferencesActivity","a.OptionsScreen","a.Home")));
        assertEquals("only a settings screen: still opens",Launch.pick(Arrays.asList("a.SettingsActivity")),"a.SettingsActivity");
        assertNull(Launch.pick(Collections.emptyList()));assertNull(Launch.pick(null));
    }
    @Test public void aVisitJoinsUnifiedTvsTaskOnlyWhereTheAppAllowsIt(){
        assertTrue(Launch.inOurTask(ActivityInfo.LAUNCH_MULTIPLE,ActivityInfo.DOCUMENT_LAUNCH_NONE,false));
        assertTrue(Launch.inOurTask(ActivityInfo.LAUNCH_SINGLE_TOP,ActivityInfo.DOCUMENT_LAUNCH_NONE,false));
        assertFalse("its own task by design",Launch.inOurTask(ActivityInfo.LAUNCH_SINGLE_TASK,ActivityInfo.DOCUMENT_LAUNCH_NONE,false));
        assertFalse(Launch.inOurTask(ActivityInfo.LAUNCH_SINGLE_INSTANCE,ActivityInfo.DOCUMENT_LAUNCH_NONE,false));
        assertFalse("a document",Launch.inOurTask(ActivityInfo.LAUNCH_MULTIPLE,ActivityInfo.DOCUMENT_LAUNCH_INTO_EXISTING,false));
        assertFalse("as the chosen Home, Home itself is underneath",Launch.inOurTask(ActivityInfo.LAUNCH_MULTIPLE,ActivityInfo.DOCUMENT_LAUNCH_NONE,true));
    }
}
