package io.unifiedtv.preview;
import android.app.Activity;
import android.app.role.RoleManager;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowRoleManager;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** HRC-02: the owner's choice in Android's Home dialog is read from the Home role, and never undone too early. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class DefaultHomeTest {
    private static ShadowRoleManager roles(Activity a){ShadowRoleManager r=Shadow.extract(a.getSystemService(RoleManager.class));r.addAvailableRole(RoleManager.ROLE_HOME);return r;}
    private static void idle(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    @Test public void holdingTheHomeRoleIsBeingTheDefault(){
        try(var c=Robolectric.buildActivity(Activity.class).setup()){Activity a=c.get();ShadowRoleManager r=roles(a);
            assertFalse(DefaultHome.isDefault(a));r.addHeldRole(RoleManager.ROLE_HOME);assertTrue(DefaultHome.isDefault(a));}
    }
    @Test public void aChoiceThatAppliesAMomentLaterIsConfirmed(){
        try(var c=Robolectric.buildActivity(Activity.class).setup()){Activity a=c.get();ShadowRoleManager r=roles(a);List<Boolean> seen=new ArrayList<>();
            DefaultHome.confirm(a,seen::add);assertTrue("still waiting for Android",seen.isEmpty());
            idle(500);r.addHeldRole(RoleManager.ROLE_HOME);idle(3000);
            assertEquals(Collections.singletonList(true),seen);}
    }
    @Test public void noChoiceIsReportedOnceAfterWaiting(){
        try(var c=Robolectric.buildActivity(Activity.class).setup()){Activity a=c.get();roles(a);List<Boolean> seen=new ArrayList<>();
            DefaultHome.confirm(a,seen::add);idle(1000);assertTrue("not concluded before Android has had time",seen.isEmpty());
            idle(3000);assertEquals(Collections.singletonList(false),seen);}
    }
}
