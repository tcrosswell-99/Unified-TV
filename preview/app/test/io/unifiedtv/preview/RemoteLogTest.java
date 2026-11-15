package io.unifiedtv.preview;
import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteLogTest {
    private static RemoteLog.Outcome press(RemoteLog log,int code,String name,int scan,long at,long heldMs){
        RemoteLog.Outcome o=log.record(code,name,scan,true,0,at,at,"TCL remote");
        for(long t=at+500,r=1;t<at+heldMs;t+=50,r++){o=log.record(code,name,scan,true,(int)r,t,at,"TCL remote");if(o==RemoteLog.Outcome.FINISH)return o;}
        log.record(code,name,scan,false,0,at+heldMs,at,"TCL remote");
        return o;
    }
    @Test public void aShortPressIsReceivedWithItsCodes(){
        RemoteLog log=new RemoteLog();
        assertEquals(RemoteLog.Outcome.NONE,press(log,KeyEvent.KEYCODE_DPAD_UP,"KEYCODE_DPAD_UP",103,1000,80));
        RemoteLog.Entry e=log.entries().get(0);
        assertEquals(1,e.presses);assertEquals(0,e.longPresses);assertTrue(e.scanCodes.contains(103));
        assertTrue(log.received(KeyEvent.KEYCODE_DPAD_UP));
        assertTrue(log.liveText().contains("Up: KEYCODE_DPAD_UP (19), scancode 103; 1 press"));
        assertTrue(log.lastLine().contains("released after 80 ms"));
    }
    @Test public void heldOrRepeatedCountsAsLongPress(){
        RemoteLog log=new RemoteLog();
        press(log,KeyEvent.KEYCODE_MENU,"KEYCODE_MENU",139,1000,900);
        RemoteLog.Entry e=log.entries().get(0);
        assertEquals(1,e.longPresses);assertEquals(900,e.maxHeldMs);assertTrue(e.maxRepeat>0);
        // A later short press is short, even though an earlier press of the same button repeated.
        press(log,KeyEvent.KEYCODE_MENU,"KEYCODE_MENU",139,5000,60);
        assertEquals(2,e.presses);assertEquals(1,e.longPresses);
    }
    @Test public void holdingOkForTwoSecondsFinishes(){
        RemoteLog log=new RemoteLog();
        assertEquals(RemoteLog.Outcome.NONE,press(log,KeyEvent.KEYCODE_DPAD_CENTER,"KEYCODE_DPAD_CENTER",28,1000,1500));
        assertEquals(RemoteLog.Outcome.FINISH,press(log,KeyEvent.KEYCODE_DPAD_CENTER,"KEYCODE_DPAD_CENTER",28,5000,2600));
        assertTrue(log.summary("").contains("includes the hold that finished the check"));
    }
    @Test public void backTwiceFinishesButSlowBacksDoNot(){
        RemoteLog log=new RemoteLog();
        assertEquals(RemoteLog.Outcome.BACK_ONCE,press(log,KeyEvent.KEYCODE_BACK,"KEYCODE_BACK",158,1000,50));
        assertEquals(RemoteLog.Outcome.BACK_ONCE,press(log,KeyEvent.KEYCODE_BACK,"KEYCODE_BACK",158,4000,50));
        assertEquals(RemoteLog.Outcome.FINISH,press(log,KeyEvent.KEYCODE_BACK,"KEYCODE_BACK",158,4800,50));
        assertEquals(3,log.entries().get(0).presses);
    }
    @Test public void unknownKeysAreToldApartByScancode(){
        RemoteLog log=new RemoteLog();
        press(log,KeyEvent.KEYCODE_UNKNOWN,"KEYCODE_UNKNOWN",400,1000,50);
        press(log,KeyEvent.KEYCODE_UNKNOWN,"KEYCODE_UNKNOWN",401,2000,50);
        assertEquals(2,log.entries().size());
        assertTrue(log.liveText().contains("Unknown to Android"));
    }
    @Test public void osKeysArePassedOnAndOthersAreNot(){
        assertTrue(RemoteLog.passToAndroid(KeyEvent.KEYCODE_VOLUME_UP));
        assertTrue(RemoteLog.passToAndroid(KeyEvent.KEYCODE_VOLUME_MUTE));
        assertTrue(RemoteLog.passToAndroid(KeyEvent.KEYCODE_TV_INPUT));
        assertTrue(RemoteLog.passToAndroid(KeyEvent.KEYCODE_POWER));
        assertFalse(RemoteLog.passToAndroid(KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(RemoteLog.passToAndroid(KeyEvent.KEYCODE_CHANNEL_UP));
        assertFalse(RemoteLog.passToAndroid(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
    }
    @Test public void summaryExplainsHomePlainlyAndListsWhatIsMissing(){
        RemoteLog log=new RemoteLog();
        press(log,KeyEvent.KEYCODE_DPAD_UP,"KEYCODE_DPAD_UP",103,1000,80);
        press(log,KeyEvent.KEYCODE_VOLUME_UP,"KEYCODE_VOLUME_UP",115,2000,80);
        log.interrupted();
        String s=log.summary("Unified TV 0.5 (io.unifiedtv.launcher)\nTV: TCL 55C735, Android 11, build V8-R51");
        assertTrue(s.startsWith("Remote check (RC-05, HRC-09)\nUnified TV 0.5"));
        assertTrue(s.contains("Home never reaches apps, because Android itself takes it. That is expected, not a fault."));
        assertTrue(s.contains("Not received here: Home, Power"));
        assertTrue(s.contains("Received by Unified TV (2 buttons)"));
        assertTrue(s.contains("passed on to Android"));
        assertTrue(s.contains("Common buttons not seen: Down, Left, Right, OK, Back"));
        assertTrue(s.contains("lost the screen 1 time: after Volume up"));
        assertTrue(s.contains("Escape button (HRC-01)"));
        assertFalse("no Unusually line when Home did not arrive",s.contains("Unusually"));
    }
    @Test public void aReservedKeyThatArrivesIsReportedAsUnusual(){
        RemoteLog log=new RemoteLog();
        press(log,KeyEvent.KEYCODE_SETTINGS,"KEYCODE_SETTINGS",0,1000,50);
        assertTrue(log.summary(null).contains("Unusually, these did reach Unified TV: Settings."));
    }
    @Test public void sourceNamesLoseHardwareAddresses(){
        assertEquals("TCL RC802V",RemoteLog.cleanSource("TCL RC802V 00:1A:7D:DA:71:13"));
        assertEquals("",RemoteLog.cleanSource(null));
        assertTrue(RemoteLog.cleanSource("x".repeat(80)).length()<=48);
    }
    @Test public void emptyLogStillSummarises(){
        RemoteLog log=new RemoteLog();
        assertEquals("No buttons yet.",log.liveText());
        assertTrue(log.summary("").contains("Received by Unified TV (0 buttons):\n  none"));
        assertTrue(log.summary("").contains("kept the screen throughout"));
    }
}
