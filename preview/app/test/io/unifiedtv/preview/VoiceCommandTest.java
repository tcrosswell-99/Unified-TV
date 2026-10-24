package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class VoiceCommandTest {
    @Test public void protectedSourcesNeverBecomeGeneralAssistantActions(){for(String q:new String[]{"Open Live TV","play IPTV","launch ReamSmartz","ask assistant to open Smarters"})assertEquals("blocked",VoiceCommand.classify(q));}
    @Test public void onlyKnownLocalCommandsHaveActions(){assertEquals("bbc",VoiceCommand.classify("Open BBC"));assertEquals("camera",VoiceCommand.classify("Show Ring Camera"));assertEquals("find",VoiceCommand.classify("Find Casablanca"));assertEquals("discovery",VoiceCommand.classify("run a household script"));}
}
