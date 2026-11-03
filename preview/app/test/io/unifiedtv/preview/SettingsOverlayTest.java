package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.unifiedtv.preview.SettingsOverlay.Phase.*;

/** Module 13 §B overlay state machine, SET-GLASS-02 widths and SET-GLASS-05 timings, as pure logic. */
public class SettingsOverlayTest {
    @Test public void opensAtTheRootAndSettles(){
        SettingsOverlay o=new SettingsOverlay();
        assertEquals(OPENING,o.phase());assertEquals(ROOT,o.page());assertTrue(o.acceptsInput());assertTrue(o.atRoot());
        o.settled();assertEquals(ROOT,o.phase());assertEquals(-1,o.category());
    }
    @Test public void fullJourneyRootDetailTaskAndBackOut(){
        SettingsOverlay o=new SettingsOverlay();o.settled();
        assertTrue(o.open(SettingsActivity.ABOUT));assertEquals(DETAIL,o.phase());assertEquals(SettingsActivity.ABOUT,o.category());
        assertTrue(o.expand(SettingsActivity.ADVANCED,true,2));assertEquals(TASK,o.phase());assertTrue(o.wide());
        assertEquals(DETAIL,o.back());assertFalse(o.wide());assertNull(o.task());assertEquals("the row that opened the task",2,o.detailRow());
        assertEquals(ROOT,o.back());assertEquals("the root keeps the category's row",SettingsActivity.ABOUT,o.rootRow());
        assertEquals(CLOSING,o.back());assertFalse(o.acceptsInput());
        assertTrue(o.finished());assertEquals(CLOSED,o.phase());assertFalse("reported once",o.finished());
    }
    @Test public void backWhileOpeningAtTheRootCloses(){
        SettingsOverlay o=new SettingsOverlay();assertEquals(CLOSING,o.back());
        o.settled();assertEquals("settling late can't reopen it",CLOSING,o.phase());
    }
    @Test public void nothingIsAcceptedOnceClosing(){
        SettingsOverlay o=new SettingsOverlay();o.settled();o.open(SettingsActivity.PROFILES);assertTrue(o.close());
        assertFalse(o.open(SettingsActivity.APPEARANCE));assertFalse(o.expand("x",true,0));assertFalse(o.close());
        assertEquals(CLOSING,o.back());assertEquals("still the detail underneath; no flicker back to the root",DETAIL,o.page());
    }
    @Test public void rapidOpenBackNeverStrands(){
        SettingsOverlay o=new SettingsOverlay();
        for(int i=0;i<50;i++){o.open(i%10);if(i%3==0)o.expand("t",i%2==0,1);if(i%5==0)o.settled();while(o.page()!=ROOT)o.back();}
        assertEquals(ROOT,o.page());assertTrue(o.acceptsInput());
        o.back();assertFalse(o.acceptsInput());assertTrue(o.finished());
    }
    @Test public void aTaskNeedsADetailFirst(){
        SettingsOverlay o=new SettingsOverlay();o.settled();
        assertFalse(o.expand("navigation",true,0));assertEquals(ROOT,o.phase());
        o.open(0);assertFalse(o.expand(null,true,0));assertEquals(DETAIL,o.phase());
    }
    @Test public void openingAnotherCategoryReplacesTheDetail(){
        SettingsOverlay o=new SettingsOverlay();o.settled();o.open(SettingsActivity.PRIVACY);o.open(SettingsActivity.SMART_HOME);
        assertEquals(SettingsActivity.SMART_HOME,o.category());assertEquals(ROOT,o.back());assertEquals(SettingsActivity.SMART_HOME,o.rootRow());
        assertFalse(o.open(-1));
    }
    @Test public void widthIsHalfAndTasksWidenOnlyWhenAsked(){
        assertEquals(.5f,SettingsOverlay.fraction(false,960),0);assertEquals(.75f,SettingsOverlay.fraction(true,960),0);
        SettingsOverlay o=new SettingsOverlay();o.open(0);assertEquals(.5f,o.width(960),0);
        o.expand(SettingsActivity.APPEARANCE_ADVANCED,false,0);assertEquals("a collapsed section keeps the width",.5f,o.width(960),0);o.back();
        o.expand(SettingsActivity.NAVIGATION,true,0);assertEquals(.75f,o.width(960),0);
        o.back();assertEquals(.5f,o.width(960),0);
    }
    @Test public void aSmallDisplayGetsAReadableWidth(){
        float f=SettingsOverlay.fraction(false,640);assertTrue(f*640>=SettingsOverlay.MIN_READABLE_DP-1);assertTrue(f<=.92f);
        assertEquals(.92f,SettingsOverlay.fraction(false,320),0.001);assertTrue(SettingsOverlay.fraction(true,640)>=.75f);
        assertEquals("unknown size: the standard half",.5f,SettingsOverlay.fraction(false,0),0);
    }
    @Test public void motionStaysInTheSpecWindow(){
        assertTrue(SettingsOverlay.OPEN_MS>=250&&SettingsOverlay.OPEN_MS<=350);assertTrue(SettingsOverlay.CLOSE_MS>=250&&SettingsOverlay.CLOSE_MS<=350);
        assertTrue(SettingsOverlay.FOCUS_MS>=120&&SettingsOverlay.FOCUS_MS<=200);
        assertEquals(SettingsOverlay.CROSSFADE_MS,SettingsOverlay.duration(true,true));assertEquals(SettingsOverlay.OPEN_MS,SettingsOverlay.duration(true,false));
        assertTrue("reduced motion is shorter",SettingsOverlay.duration(false,true)<SettingsOverlay.duration(false,false));
    }
    @Test public void restoreComesBackWithoutSliding(){
        SettingsOverlay o=new SettingsOverlay();o.restore(SettingsActivity.APPEARANCE,SettingsActivity.NAVIGATION,true,0);
        assertEquals(TASK,o.phase());assertTrue(o.wide());assertEquals(DETAIL,o.back());
        SettingsOverlay r=new SettingsOverlay();r.restore(-1,null,false,7);assertEquals(ROOT,r.phase());assertEquals(7,r.rootRow());
    }
}
