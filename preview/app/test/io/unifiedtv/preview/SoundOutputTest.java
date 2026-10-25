package io.unifiedtv.preview;

import android.media.AudioDeviceInfo;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** SET-072: where the sound plays, in plain words, and honest about when Android only lists the outputs. */
public class SoundOutputTest {
    private static SoundOutput.Out out(int type,String name){return new SoundOutput.Out(type,name);}

    @Test public void outputsHavePlainNames(){
        assertEquals("TV speakers",SoundOutput.words(out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,"BRAVIA")));
        assertEquals("HDMI ARC",SoundOutput.words(out(AudioDeviceInfo.TYPE_HDMI_ARC,"HDMI ARC")));
        assertEquals("HDMI ARC (Sonos Beam)",SoundOutput.words(out(AudioDeviceInfo.TYPE_HDMI_ARC,"Sonos Beam")));
        assertEquals("HDMI eARC",SoundOutput.words(out(SoundOutput.HDMI_EARC,"")));
        assertEquals("Bluetooth: Echo Studio",SoundOutput.words(out(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP," Echo Studio ")));
        assertEquals("A Bluetooth device",SoundOutput.words(out(SoundOutput.BLE_SPEAKER,null)));
        assertEquals("Headphones",SoundOutput.words(out(AudioDeviceInfo.TYPE_WIRED_HEADPHONES,"")));
        assertEquals("Digital audio out (optical)",SoundOutput.words(out(AudioDeviceInfo.TYPE_LINE_DIGITAL,"")));
        assertNull("not for listening",SoundOutput.words(out(AudioDeviceInfo.TYPE_TELEPHONY,"")));
        assertNull(SoundOutput.words(out(AudioDeviceInfo.TYPE_REMOTE_SUBMIX,"")));
    }
    @Test public void eachOutputIsListedOnce(){
        List<SoundOutput.Out> list=Arrays.asList(out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,""),out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,""),out(AudioDeviceInfo.TYPE_TELEPHONY,""),out(AudioDeviceInfo.TYPE_HDMI_ARC,""));
        assertEquals(Arrays.asList("TV speakers","HDMI ARC"),SoundOutput.names(list));
    }
    @Test public void theLikelyOutputFollowsAndroidsUsualOrder(){
        SoundOutput.Out speaker=out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,""),arc=out(AudioDeviceInfo.TYPE_HDMI_ARC,""),bt=out(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,"Echo Studio");
        assertSame(speaker,SoundOutput.likely(Collections.singletonList(speaker)));
        assertSame(arc,SoundOutput.likely(Arrays.asList(speaker,arc)));
        assertSame(bt,SoundOutput.likely(Arrays.asList(speaker,arc,bt)));
        assertNull(SoundOutput.likely(Collections.singletonList(out(AudioDeviceInfo.TYPE_TELEPHONY,""))));
    }
    @Test public void androidThirteenSaysForCertain(){
        List<SoundOutput.Out> connected=Arrays.asList(out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,""),out(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,"Echo Studio"));
        SoundOutput.Summary s=SoundOutput.summary(Collections.singletonList(out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,"")),connected);
        assertEquals("Sound plays through: TV speakers",s.line);assertEquals("Also connected: Bluetooth: Echo Studio.",s.note);
    }
    @Test public void olderAndroidIsOnlyLikely(){
        SoundOutput.Summary s=SoundOutput.summary(null,Arrays.asList(out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,""),out(AudioDeviceInfo.TYPE_HDMI_ARC,"")));
        assertEquals("Sound probably plays through: HDMI ARC",s.line);
        assertTrue(s.note,s.note.startsWith("Connected: TV speakers · HDMI ARC.")&&s.note.contains("doesn't tell apps for certain"));
        SoundOutput.Summary none=SoundOutput.summary(null,Collections.<SoundOutput.Out>emptyList());
        assertEquals("Sound output: not reported",none.line);
        SoundOutput.Summary emptyNow=SoundOutput.summary(Collections.<SoundOutput.Out>emptyList(),Collections.singletonList(out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,"")));
        assertEquals("an empty answer falls back to the likely one","Sound probably plays through: TV speakers",emptyNow.line);
    }
}
