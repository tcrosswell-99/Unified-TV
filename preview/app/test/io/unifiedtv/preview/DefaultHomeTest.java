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

    // --- Owner's TCL, 25 October 2026: left on the original Home's apps screen, and Home stayed with tvlauncher.
    private static final String TV_LAUNCHER="com.google.android.tvlauncher";
    private static void homeResolvesTo(Activity a,String pkg){
        android.content.pm.ResolveInfo r=new android.content.pm.ResolveInfo();r.activityInfo=new android.content.pm.ActivityInfo();r.activityInfo.packageName=pkg;r.activityInfo.name=pkg+".MainActivity";
        Shadows.shadowOf(a.getPackageManager()).addResolveInfoForIntent(DefaultHome.homeIntent(),r);
    }
    @Test public void whatAndroidRecordsInPlainStates(){
        assertEquals(DefaultHome.State.OURS,DefaultHome.state("io.unifiedtv.launcher",false,"io.unifiedtv.launcher"));
        assertEquals(DefaultHome.State.OURS,DefaultHome.state("io.unifiedtv.launcher",true,null));
        assertEquals("Android's chooser is no default",DefaultHome.State.OURS,DefaultHome.state("io.unifiedtv.launcher",true,"android"));
        assertEquals("the role held, but the Home button still opens the TV's own",DefaultHome.State.KEPT,DefaultHome.state("io.unifiedtv.launcher",true,TV_LAUNCHER));
        assertEquals(DefaultHome.State.NOT,DefaultHome.state("io.unifiedtv.launcher",false,TV_LAUNCHER));
        assertTrue(DefaultHome.chooserHelps(null));assertTrue(DefaultHome.chooserHelps("android"));assertFalse("pressing Home would just open tvlauncher",DefaultHome.chooserHelps(TV_LAUNCHER));
        assertEquals("This TV keeps its own Home button. Unified TV opens as an app instead.",DefaultHome.words(DefaultHome.State.KEPT));
        assertTrue(DefaultHome.words(DefaultHome.State.OURS).startsWith("Done."));assertTrue(DefaultHome.words(DefaultHome.State.NOT).startsWith("Nothing changed."));
    }
    @Test public void aHeldRoleTheHomeButtonIgnoresIsRecordedNotClaimed(){
        try(var c=Robolectric.buildActivity(Activity.class).setup()){Activity a=c.get();ShadowRoleManager r=roles(a);r.addHeldRole(RoleManager.ROLE_HOME);homeResolvesTo(a,TV_LAUNCHER);
            assertFalse("not the Home button's, so Back leaves Unified TV as any app",DefaultHome.isDefault(a));
            List<DefaultHome.State> seen=new ArrayList<>();DefaultHome.settle(a,seen::add);idle(3000);
            assertEquals(Collections.singletonList(DefaultHome.State.KEPT),seen);assertTrue("Settings stops offering the switch",DefaultHome.fixed(a));
            DefaultHome.withdraw(a);assertFalse("handing Home back clears it",DefaultHome.fixed(a));}
    }
    @Test public void withADefaultHomeAndNoDialogNothingIsLaunched(){
        try(var c=Robolectric.buildActivity(Activity.class).setup()){Activity a=c.get();homeResolvesTo(a,TV_LAUNCHER);
            assertEquals("no bare Home intent that would open tvlauncher's apps screen",DefaultHome.Route.NONE,DefaultHome.choose(a));
            assertNull(Shadows.shadowOf(a).getNextStartedActivity());}
    }
    @Test public void theRoleDialogIsAskedForWhenThereIsOne(){
        try(var c=Robolectric.buildActivity(Activity.class).setup()){Activity a=c.get();roles(a);
            assertEquals(DefaultHome.Route.ROLE,DefaultHome.choose(a));}
    }
    @Test public void afterTheChoiceUnifiedTvsHomeComesBackToTheFront(){
        android.content.Intent back=DefaultHome.backHome(org.robolectric.RuntimeEnvironment.getApplication());
        assertEquals(UnifiedPreviewActivity.class.getName(),back.getComponent().getClassName());assertTrue(back.getBooleanExtra(DefaultHome.REPORT,false));
        int flags=android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
        assertEquals(flags,back.getFlags()&flags);
        try(var c=Robolectric.buildActivity(UnifiedPreviewActivity.class,back).setup()){
            idle(3000);assertFalse("Home stays: the owner is never stranded",c.get().isFinishing());
            assertFalse("the report is said once",c.get().getIntent().getBooleanExtra(DefaultHome.REPORT,false));}
    }
}
