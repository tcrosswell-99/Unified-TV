package io.unifiedtv.preview;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.unifiedtv.preview.HomeUtilities.*;
/** The utility pill (UX-UTILITY-01..03, NAV-02/03/05/07, SET-090..093): order, presence, landing and movement. */
public class HomeUtilitiesTest {
    @Test public void thePillHoldsAirPlayVpnVoiceAndSettingsInThatOrderAndNoProfile(){
        assertEquals(Arrays.asList(AIRPLAY,VPN,VOICE,SETTINGS),items(DEFAULT,true,true));
        assertFalse(items(DEFAULT,true,true).contains("profile"));
    }
    @Test public void whatIsntOnThisTvIsLeftOutButSettingsAlwaysStays(){
        assertEquals(Arrays.asList(VOICE,SETTINGS),items(DEFAULT,false,false));
        assertEquals(Arrays.asList(SETTINGS),items(order("settings"),false,false));
        assertEquals("Settings can't be removed",Arrays.asList(VPN,SETTINGS),order("vpn"));
        assertEquals(Arrays.asList(SETTINGS),toggle(Arrays.asList(SETTINGS),SETTINGS));
        assertEquals("junk dropped",Arrays.asList(VOICE,SETTINGS),order("voice,profile,../x,voice"));
    }
    @Test public void upLandsOnTheFirstIconAndLeftRightStayInsideThePill(){
        List<String> p=items(DEFAULT,true,true);assertEquals(0,landing(p));
        assertEquals(1,step(0,1,4));assertEquals(3,step(3,1,4));assertEquals(0,step(0,-1,4));assertEquals(-1,step(0,1,0));
        assertEquals("AirPlay went away: focus stays on the same icon",1,keep(p,2,items(DEFAULT,false,true)));
    }
    @Test public void householdOrderCanBePreviewedHiddenMovedAndReset(){
        List<String> draft=move(DEFAULT,SETTINGS,-1);assertEquals(Arrays.asList(AIRPLAY,VPN,SETTINGS,VOICE),draft);
        draft=toggle(draft,AIRPLAY);assertEquals(Arrays.asList(VPN,SETTINGS,VOICE),draft);
        assertEquals("shown again at its default place",Arrays.asList(AIRPLAY,VPN,SETTINGS,VOICE),toggle(draft,AIRPLAY));
        assertEquals(draft,order(write(draft)));assertEquals(DEFAULT,order(""));assertEquals(DEFAULT,order(null));
        assertEquals(DEFAULT,move(DEFAULT,AIRPLAY,-1));
    }
    @Test public void focusLabelsAreShortAndTruthful(){
        assertEquals("Settings",label(SETTINGS,"",false));assertTrue(label(AIRPLAY,"",false).contains("isn't offering"));
        assertEquals("VPN ○ Disconnected",label(VPN,"VPN ○ Disconnected",true));
    }
    @Test public void downLandsOnContinueWatchingThenTheFirstTitleThenTheLibraryCard(){
        assertEquals(2,libraryLanding(new long[]{0,0,5000},"continue"));
        assertEquals(0,libraryLanding(new long[]{0,0,5000},"first"));
        assertEquals(0,libraryLanding(new long[]{0,0},"continue"));
        assertEquals("no titles (no NAS, offline, empty Kids): the Library card",0,libraryLanding(new long[0],"continue"));
    }
    // Owner, TCL, 25 October 2026: after opening AirPin, the label says it is checking rather than repeating a stale answer.
    @Test public void airPlayLabelSaysCheckingWhileItLooksAgain(){
        assertEquals("AirPlay ready",label(AIRPLAY,"",true,true));
        assertEquals("AirPlay: checking AirPin…",label(AIRPLAY,"",false,true));
        assertEquals("AirPlay: AirPin isn't offering AirPlay right now",label(AIRPLAY,"",false,false));
        assertEquals(label(AIRPLAY,"",false),label(AIRPLAY,"",false,false));
    }
}
