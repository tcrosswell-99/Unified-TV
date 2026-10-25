package io.unifiedtv.preview;

import android.view.KeyEvent;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** SET-071 key help: every Unified TV button explained, and Home described as it really behaves on this TV. */
public class RemoteHelpTest {
    private static Map<String,String> keys(boolean launcher,boolean home){Map<String,String> m=new LinkedHashMap<>();for(RemoteHelp.Key k:RemoteHelp.keys(launcher,home))m.put(k.button,k.meaning);return m;}

    @Test public void theFixedDirectionsAreExplained(){
        Map<String,String> k=keys(false,false);
        assertEquals(Arrays.asList("OK","Hold OK","Up","Down","Left and Right","Back","Menu","Home","Volume, mute, power, input and voice"),new ArrayList<>(k.keySet()));
        assertTrue(k.get("Up").contains("top right"));assertTrue(k.get("Down").contains("Films & TV"));
        assertTrue(k.get("Hold OK").contains("move or remove"));assertTrue(k.get("Back").contains("never closes itself"));
        assertTrue("OS-reserved keys stay the TV's",k.get("Volume, mute, power, input and voice").contains("TV's own"));
    }
    @Test public void homeIsDescribedAsItReallyIs(){
        assertTrue("the preview is never Home",keys(false,false).get("Home").contains("TV's own Home screen"));
        assertTrue(keys(false,true).get("Home").contains("preview never"));
        assertTrue("the launcher before it is chosen",keys(true,false).get("Home").contains("isn't this TV's Home screen yet"));
        assertTrue(keys(true,true).get("Home").contains("back to Unified TV"));
    }
    @Test public void theDemonstrationNamesWhatAButtonDoes(){
        assertTrue(RemoteHelp.pressed(KeyEvent.KEYCODE_DPAD_UP).startsWith("Up:"));
        assertTrue(RemoteHelp.pressed(KeyEvent.KEYCODE_DPAD_DOWN).contains("Films & TV"));
        assertTrue(RemoteHelp.pressed(KeyEvent.KEYCODE_ENTER).startsWith("OK:"));
        assertNull("Back keeps its own job",RemoteHelp.pressed(KeyEvent.KEYCODE_BACK));
        assertNull(RemoteHelp.pressed(KeyEvent.KEYCODE_VOLUME_UP));
    }
}
