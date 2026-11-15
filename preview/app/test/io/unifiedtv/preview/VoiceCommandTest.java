package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class VoiceCommandTest {
    @Test public void protectedSourcesNeverBecomeGeneralAssistantActions(){for(String q:new String[]{"Open Live TV","play IPTV","launch ReamSmartz","ask assistant to open Smarters"})assertEquals("live",VoiceCommand.classify(q));}
    @Test public void onlyKnownLocalCommandsHaveActions(){assertEquals("bbc",VoiceCommand.classify("Open BBC"));assertEquals("camera",VoiceCommand.classify("Show Ring Camera"));assertEquals("find",VoiceCommand.classify("Find Casablanca"));assertEquals("discovery",VoiceCommand.classify("run a household script"));}
    @Test public void itvxAndFreeviewPlayOpenByName(){
        for(String q:new String[]{"Open ITVX","open itv x","Open ITV","open ITV Hub"})assertEquals(q,"itvx",VoiceCommand.classify(q));
        for(String q:new String[]{"Open Freeview","open Freeview Play"})assertEquals(q,"freeview",VoiceCommand.classify(q));
        assertNull("never sent to Amazon Music",VoiceCommand.music("play Coronation Street on ITVX"));
    }
}
