package io.unifiedtv.preview;

import android.view.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.time.Duration;

/** Spec 32 / AM-04, AM-05: Home's light drifts by default, and stops for the household's and the TV's own motion choices. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class AmbientDriftTest {
    private ActivityController<UnifiedPreviewActivity> controller;
    @Before public void start(){RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",RemoteJourneyTest.SEEDED).commit();}
    @After public void stop(){if(controller!=null)controller.pause().stop().destroy();scale(1f);}
    /** The TV's own animation setting (Android's animator duration scale; a hidden platform call, as Settings makes it). */
    private static void scale(float s){try{android.animation.ValueAnimator.class.getMethod("setDurationScale",float.class).invoke(null,s);}catch(Exception e){throw new AssertionError(e);}}
    private UnifiedPreviewActivity open(){controller=Robolectric.buildActivity(UnifiedPreviewActivity.class).setup();View content=controller.get().findViewById(android.R.id.content);content.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));content.layout(0,0,1920,1080);return controller.get();}
    private static AmbientLayer layer(UnifiedPreviewActivity a){ViewGroup stack=(ViewGroup)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);return (AmbientLayer)stack.getChildAt(0);}
    /** The first light of the group on show (the top child of the layer). */
    private static View light(AmbientLayer layer){ViewGroup group=(ViewGroup)layer.getChildAt(layer.getChildCount()-1);for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i).getTag() instanceof AmbientMesh.Light)return group.getChildAt(i);throw new AssertionError("no light");}
    private static void wait(int ms){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private static float[] at(View v){return new float[]{v.getTranslationX(),v.getTranslationY(),v.getScaleX()};}
    private static boolean moved(float[] a,float[] b){return Math.abs(a[0]-b[0])>.5f||Math.abs(a[1]-b[1])>.5f||Math.abs(a[2]-b[2])>.0005f;}

    @Test public void theLightDriftsByDefault(){
        AmbientLayer layer=layer(open());wait(100);float[] before=at(light(layer));wait(3000);
        assertTrue("the light moves within three seconds",moved(before,at(light(layer))));
        assertTrue(layer.drifting());
    }
    @Test public void driftResumesAfterAnotherScreenAndAfterLeavingHome(){
        AmbientLayer layer=layer(open());controller.pause();wait(100);float[] paused=at(light(layer));wait(2000);
        assertFalse("paused while Home is covered",moved(paused,at(light(layer))));
        controller.resume();wait(100);float[] back=at(light(layer));wait(3000);assertTrue("moving again after returning",moved(back,at(light(layer))));
        controller.pause().stop();controller.restart().start().resume();wait(100);back=at(light(layer));wait(3000);assertTrue("moving again after leaving Home",moved(back,at(light(layer))));
    }
    @Test public void keepStillReduceMotionAndLightweightStopIt(){
        for(String setting:new String[]{"stillBackground","reduceMotion"}){
            RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",RemoteJourneyTest.SEEDED).putBoolean(setting,true).commit();
            AmbientLayer layer=layer(open());wait(100);float[] before=at(light(layer));wait(3000);
            assertFalse(setting,moved(before,at(light(layer))));assertFalse(setting,layer.drifting());controller.pause().stop().destroy();controller=null;
        }
        RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0).edit().clear().putString("dock",RemoteJourneyTest.SEEDED).putBoolean("lightweight",true).commit();
        AmbientLayer layer=layer(open());assertEquals("Lightweight shows still frames instead",View.GONE,layer.getVisibility());
    }
    @Test public void theTvsOwnAnimationSettingIsRespected(){
        scale(0f);
        AmbientLayer layer=layer(open());wait(100);float[] before=at(light(layer));wait(3000);
        assertFalse("TV animations off: still",moved(before,at(light(layer))));
        assertEquals(AmbientLayer.TV_ANIMATIONS_OFF,AmbientLayer.stillReason(false,false,false,false));
    }
    @Test public void theReasonForAStillBackgroundIsInPlainWords(){
        assertNull(AmbientLayer.stillReason(false,false,false,true));
        assertNotNull(AmbientLayer.stillReason(true,false,false,true));assertNotNull(AmbientLayer.stillReason(false,true,false,true));assertNotNull(AmbientLayer.stillReason(false,false,true,true));
        String why=AmbientLayer.stillReason(false,false,false,false);
        for(String jargon:new String[]{"animator","scale","developer","adb"})assertFalse(why,why.toLowerCase(java.util.Locale.ROOT).contains(jargon));
    }
}
