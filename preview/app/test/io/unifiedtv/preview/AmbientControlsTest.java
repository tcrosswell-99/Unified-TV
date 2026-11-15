package io.unifiedtv.preview;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import java.time.Duration;

/**
 * Memory and CPU pass (owner's TCL, 25 October 2026) and SET-022. Home's light drifts only while it can be seen and someone
 * is there: it rests when Home is covered, when the screen is off, after ten minutes without a key, and a minute after a
 * change previewed under the Settings sheet; it carries on from where it stopped (never a jump). Intensity and colour are
 * Appearance & Home > Ambient background rows that show on Home at once. Owner diagnostics can say what the light did.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class AmbientControlsTest {
    private ActivityController<UnifiedPreviewActivity> controller;
    private static SharedPreferences prefs(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0);}
    @Before public void start(){prefs().edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();}
    @After public void stop(){if(controller!=null)try{controller.pause().stop().destroy();}catch(RuntimeException ignored){}}
    private UnifiedPreviewActivity open(){controller=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();View content=controller.get().findViewById(android.R.id.content);content.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));content.layout(0,0,1920,1080);return controller.get();}
    private static AmbientLayer layer(UnifiedPreviewActivity a){ViewGroup stack=(ViewGroup)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);return (AmbientLayer)stack.getChildAt(0);}
    private static View light(AmbientLayer layer){ViewGroup g=layer.shown();for(int i=0;i<g.getChildCount();i++)if(g.getChildAt(i).getTag() instanceof AmbientMesh.Light)return g.getChildAt(i);throw new AssertionError("no light");}
    private static void pass(long ms){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private static float[] at(View v){return new float[]{v.getTranslationX(),v.getTranslationY(),v.getScaleX()};}

    @Test public void theLightRestsAfterTenMinutesWithoutAKeyAndWakesOnTheNext(){
        UnifiedPreviewActivity a=open();AmbientLayer layer=layer(a);pass(100);assertTrue(layer.drifting());
        pass(AmbientLayer.IDLE_STILL_MS+1000);
        assertFalse("nobody there: the light rests",layer.drifting());
        float[] rested=at(light(layer));pass(5000);assertArrayEquals("and stays exactly where it was",rested,at(light(layer)),0f);
        a.onUserInteraction();assertTrue("a key wakes it",layer.drifting());
        assertArrayEquals("from where it stopped: no jump",rested,at(light(layer)),0f);
    }
    @Test public void coveredOrScreenOffStopsItAndItCarriesOnWithoutAJump(){
        UnifiedPreviewActivity a=open();AmbientLayer layer=layer(a);pass(2000);
        controller.pause();float[] paused=at(light(layer));assertFalse(layer.drifting());pass(4000);
        controller.resume();assertTrue(layer.drifting());assertArrayEquals("no jump on return",paused,at(light(layer)),0f);
        RuntimeEnvironment.getApplication().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));pass(10);
        assertFalse("screen off: still",layer.drifting());
        RuntimeEnvironment.getApplication().sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));pass(10);
        assertTrue("screen on: moving again",layer.drifting());
        controller.pause().stop();assertFalse("another full-screen app (or the Wallpaper) in front",layer.drifting());
        prefs().edit().putBoolean("reduceMotion",true).putBoolean("reduceMotion",false).apply();
        assertFalse("stopped Home doesn't preview changes",layer.drifting());
        controller.restart().start().resume();assertTrue(layer.drifting());
    }
    @Test public void underTheSheetAChangePreviewsForAMinuteThenRests(){
        AmbientLayer layer=layer(open());controller.pause();assertFalse(layer.drifting());
        prefs().edit().putString(AmbientMesh.INTENSITY,AmbientMesh.SUBTLE_VALUE).apply();
        assertTrue("the change previews behind the sheet",layer.drifting());
        pass(AmbientLayer.PREVIEW_MS+1000);assertFalse("then the light rests under the sheet",layer.drifting());
        controller.resume();assertTrue("closing the sheet: moving",layer.drifting());
    }
    @Test public void intensityAndColourShowOnHomeAtOnce(){
        UnifiedPreviewActivity a=open();AmbientLayer layer=layer(a);String app=prefs().getString("scene","");
        assertFalse(app.isEmpty());assertFalse(app.contains("~"));
        controller.pause();   // the Settings sheet over Home
        prefs().edit().putString(AmbientMesh.COLOUR,AmbientMesh.NEUTRAL_VALUE).apply();
        assertEquals("the glass screens follow",AmbientMesh.NEUTRAL_LIGHT,prefs().getString("scene",""));
        assertEquals("Home's light is the neutral palette",AmbientMesh.palette(AmbientMesh.NEUTRAL_LIGHT).base,((ColorDrawable)layer.shown().getBackground()).getColor());
        prefs().edit().putString(AmbientMesh.COLOUR,AmbientMesh.FOLLOW).putString(AmbientMesh.INTENSITY,AmbientMesh.SUBTLE_VALUE).apply();
        assertEquals(app+AmbientMesh.SUBTLE,prefs().getString("scene",""));
        assertEquals("the app's own colours again",AmbientMesh.palette(app).base,((ColorDrawable)layer.shown().getBackground()).getColor());
        controller.resume();assertEquals(app+AmbientMesh.SUBTLE,prefs().getString("scene",""));
        for(String k:new String[]{AmbientMesh.INTENSITY,AmbientMesh.COLOUR})assertEquals(k,HomePreview.LIGHT,HomePreview.of(k));
    }
    @Test public void trimmingWhileHiddenLetsTheLightGoAndItComesBack(){
        UnifiedPreviewActivity a=open();AmbientLayer layer=layer(a);assertNotNull(layer.shown());
        controller.pause().stop();a.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        assertNull("hidden: the light's groups go",layer.shown());
        controller.restart().start().resume();assertNotNull("rebuilt when Home shows",layer.shown());assertTrue(layer.drifting());
    }
    @Test public void ownerDiagnosticsSayWhetherTheLightMoves(){
        open();pass(4000);controller.pause();
        String line=AmbientLayer.diagnosis(prefs(),true,android.os.SystemClock.uptimeMillis());
        assertTrue(line,line.startsWith("Background: moving · ")&&line.contains(" fps"));
        assertTrue(line,line.contains("Resting now because the Settings sheet is over Home"));
        prefs().edit().putBoolean("reduceMotion",true).commit();
        assertTrue(AmbientLayer.diagnosis(prefs(),true,0).startsWith("Background: still because reduce motion is on"));
        prefs().edit().putBoolean("reduceMotion",false).commit();
        assertTrue(AmbientLayer.diagnosis(prefs(),false,0).startsWith("Background: still because this TV has its animations switched off"));
        assertEquals("TV animation scales: window 1×, transition 1×, animator 0.5×",OwnerChecks.animationScales(1f,1f,.5f));
        assertTrue(OwnerChecks.animationScales(1f,1f,0f).endsWith("(animations off: Home's light stays still)"));
    }

    // --- Settings > Appearance & Home > Ambient background (SET-022)
    private Button find(View v,String start){if(v instanceof Button&&((Button)v).getText().toString().startsWith(start))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),start);if(b!=null)return b;}return null;}
    @Test public void intensityAndColourAreRowsInAmbientBackground(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity s=c.get();View root=s.findViewById(android.R.id.content);
            find(root,"Appearance & Home").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Button intensity=find(root,"Intensity: ");assertNotNull(intensity);assertEquals("Intensity: Standard",intensity.getText().toString());
            assertEquals("Colour: Follow the app in focus",find(root,"Colour: ").getText().toString());
            assertNotNull("the motion switches are still there",find(root,"Keep the Home background still"));
            intensity.performClick();Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(AmbientMesh.SUBTLE_VALUE,prefs().getString(AmbientMesh.INTENSITY,""));
            assertEquals("Intensity: Subtle",find(root,"Intensity: ").getText().toString());
            find(root,"Colour: ").performClick();Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(AmbientMesh.NEUTRAL_VALUE,prefs().getString(AmbientMesh.COLOUR,""));
            assertEquals("Colour: Neutral",find(root,"Colour: ").getText().toString());
        }
        assertNotNull("in the one schema (reset, backup, repair)",SettingsSchema.of(AmbientMesh.INTENSITY));
        assertFalse("a corrupt value is repaired",SettingsSchema.of(AmbientMesh.COLOUR).accepts("purple"));
    }
}
