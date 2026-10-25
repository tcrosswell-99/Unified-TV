package io.unifiedtv.preview;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.*;
import android.widget.*;

/**
 * SET-010 guided setup from the remote, in the glass sidebar over Home (OD-19): Welcome → remote → apps → media server →
 * finish, with Skip on every step, Back one step at a time, progress saved and resumed, never in Kids, and one part on
 * its own from Settings (SET-015).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FirstRunJourneyTest {
    private Context app;
    private SharedPreferences prefs(){return app.getSharedPreferences("unified-tv-preview",0);}
    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static View root(FirstRunActivity a){return a.findViewById(android.R.id.content);}
    private static Button find(View v,String name){if(v instanceof Button&&v.isShown()&&name.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),name);if(b!=null)return b;}return null;}
    private static boolean says(View v,String part){if(v instanceof TextView&&!(v instanceof Button)&&((TextView)v).getText().toString().contains(part))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(says(((ViewGroup)v).getChildAt(i),part))return true;return false;}
    private static void press(FirstRunActivity a,String name){Button b=find(root(a),name);assertNotNull("'"+name+"' on step "+a.step(),b);b.performClick();idle();}
    private static String focused(FirstRunActivity a){View f=a.getWindow().getDecorView().findFocus();return f instanceof TextView?String.valueOf(((TextView)f).getText()):String.valueOf(f);}
    private ActivityController<FirstRunActivity> start(String only){
        Intent i=new Intent(app,FirstRunActivity.class);if(only!=null)i.putExtra(FirstRunActivity.ONLY,only);
        ActivityController<FirstRunActivity> c=Robolectric.buildActivity(FirstRunActivity.class,i).setup();idle();return c;
    }
    @Before public void clean(){app=RuntimeEnvironment.getApplication();prefs().edit().clear().commit();}

    @Test public void fiveOptionalStepsFromWelcomeToHome(){
        ActivityController<FirstRunActivity> c=start(null);FirstRunActivity a=c.get();
        try{
            assertEquals(FirstRun.WELCOME,a.step());assertTrue(says(root(a),"Step 1 of 5"));assertTrue(says(root(a),"Nothing here asks for an account, a password or an address."));
            assertEquals("OK starts it","Start",focused(a));assertNotNull("Skip is always there",find(root(a),"Skip the guided setup"));
            press(a,"Start");
            assertEquals(FirstRun.REMOTE,a.step());assertEquals(FirstRun.REMOTE,FirstRun.load(prefs()).step);assertEquals(FirstRun.IN_PROGRESS,FirstRun.load(prefs()).status);
            assertNotNull(find(root(a),"Up · From the apps on Home, straight to the small icons at the top right (Settings is always there)."));
            // The live demonstration: a button says what it does on Home, and still moves focus here.
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));idle();
            assertTrue(says(root(a),"Down: on Home, this shows your Films & TV."));
            press(a,"Next");
            assertEquals(FirstRun.APPS,a.step());
            press(a,"Open Build My TV");
            assertEquals(BuildMyTvActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
            c.pause().resume();idle();assertEquals("back from Build My TV, on the same step",FirstRun.APPS,a.step());
            press(a,"Next");
            assertEquals(FirstRun.LIBRARY,a.step());assertTrue(says(root(a),"You never type an address."));
            press(a,"Find my media server");
            Intent lib=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(DlnaLibraryActivity.class.getName(),lib.getComponent().getClassName());assertTrue(lib.getBooleanExtra("setup",false));
            press(a,"Next");
            assertEquals(FirstRun.FINISH,a.step());assertTrue(says(root(a),"Films & TV: not set up yet"));assertNull("nothing left to skip",find(root(a),"Skip the guided setup"));
            press(a,"Go to Home");
            assertTrue(a.isFinishing());assertEquals(FirstRun.COMPLETED,FirstRun.load(prefs()).status);
        }finally{c.pause().stop().destroy();}
    }
    @Test public void backGoesOneStepBackAndFromWelcomeLeaves(){
        ActivityController<FirstRunActivity> c=start(null);FirstRunActivity a=c.get();
        try{
            press(a,"Start");press(a,"Next");assertEquals(FirstRun.APPS,a.step());
            a.onBackPressed();idle();assertEquals(FirstRun.REMOTE,a.step());
            a.onBackPressed();idle();assertEquals(FirstRun.WELCOME,a.step());assertFalse(a.isFinishing());
            a.onBackPressed();idle();assertTrue("Back from Welcome is 'not now'",a.isFinishing());
            assertEquals(FirstRun.SKIPPED,FirstRun.load(prefs()).status);
        }finally{c.pause().stop().destroy();}
    }
    @Test public void skipOnAnyStepEndsItWithoutChangingAnythingElse(){
        prefs().edit().putString("dock","netflix,bbc").commit();
        ActivityController<FirstRunActivity> c=start(null);FirstRunActivity a=c.get();
        try{
            press(a,"Start");press(a,"Next");press(a,"Skip the guided setup");
            assertTrue(a.isFinishing());assertEquals(FirstRun.SKIPPED,FirstRun.load(prefs()).status);
            assertEquals("the dock is untouched","netflix,bbc",prefs().getString("dock",""));
        }finally{c.pause().stop().destroy();}
    }
    @Test public void leavingPartWayResumesAtTheSameStep(){
        ActivityController<FirstRunActivity> c=start(null);
        press(c.get(),"Start");press(c.get(),"Next");assertEquals(FirstRun.APPS,c.get().step());
        c.pause().stop().destroy();   // the Home button, another app, or the TV going to standby
        ActivityController<FirstRunActivity> again=start(null);
        try{assertEquals("resumed",FirstRun.APPS,again.get().step());assertTrue(says(root(again.get()),"Step 3 of 5"));}
        finally{again.pause().stop().destroy();}
    }
    @Test public void neverShownInKids(){
        prefs().edit().putString(Profiles.PREF,Profiles.CHILD).commit();
        ActivityController<FirstRunActivity> c=Robolectric.buildActivity(FirstRunActivity.class).setup();
        try{assertTrue(c.get().isFinishing());assertEquals(FirstRun.NOT_STARTED,FirstRun.load(prefs()).status);}
        finally{c.pause().stop().destroy();}
    }
    @Test public void onePartOnItsOwnLeavesTheSavedProgressAlone(){
        FirstRun.save(prefs(),FirstRun.finished());
        ActivityController<FirstRunActivity> c=start(FirstRun.REMOTE);FirstRunActivity a=c.get();
        try{
            assertEquals(FirstRun.REMOTE,a.step());assertTrue(says(root(a),"One part of the guided setup"));
            assertNull("no Next into other parts",find(root(a),"Next"));assertNull(find(root(a),"Skip the guided setup"));
            press(a,"Done");assertTrue(a.isFinishing());
            assertEquals(FirstRun.COMPLETED,FirstRun.load(prefs()).status);assertEquals(FirstRun.FINISH,FirstRun.load(prefs()).step);
        }finally{c.pause().stop().destroy();}
    }
}
