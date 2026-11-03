package io.unifiedtv.preview;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Small household choices: doorbell camera (SET-051/054), alarm switch (SET-053 exception), dock labels (SET-026), PIN reset (SET-043), About (SET-084), rating schemes (SET-074). */
public class HouseholdChoicesTest {
    @Test public void doorbellCameraClosesAfter30sByDefault60sOrOnlyWithBack(){
        assertEquals(30_000,DoorbellCamera.closeMs(null));assertEquals(30_000,DoorbellCamera.closeMs("junk"));assertEquals(30_000,DoorbellCamera.closeMs("30"));
        assertEquals(60_000,DoorbellCamera.closeMs("60"));assertEquals(0,DoorbellCamera.closeMs("manual"));
        assertEquals("the queued-visit rule is separate and unchanged",45_000,AlertPolicy.RING_STILL_RELEVANT);
        assertEquals("Only with Back",DoorbellCamera.closeLabel("manual"));assertEquals("Back to close",DoorbellCamera.hint("manual"));
        assertEquals(DoorbellCamera.OVERLAY,DoorbellCamera.layout(null));assertEquals(DoorbellCamera.FULL,DoorbellCamera.layout("full"));
        assertEquals("Large overlay (where supported)",DoorbellCamera.layoutLabel(""));
    }
    @Test public void testEventsSayTestOnTheCardAndTheToast(){
        assertEquals("TEST · Front Door",DoorbellCamera.title("Front Door",true));assertEquals("Front Door",DoorbellCamera.title("Front Door",false));
        assertEquals("Doorbell",DoorbellCamera.title(" ",false));
        assertEquals("TEST · Person at Doorbell",DoorbellCamera.person(null,true));assertEquals("Person at Porch",DoorbellCamera.person("Porch",false));
    }
    @Test public void alarmsOffIsSaidPlainlyAndNotConfiguredClaimsNothing(){
        assertEquals(AlarmWatch.State.OFF,AlarmWatch.state(false,false,true,3));
        assertEquals("stopping alerts counts as off",AlarmWatch.State.OFF,AlarmWatch.state(true,true,true,3));
        assertEquals(AlarmWatch.State.NOT_CONFIGURED,AlarmWatch.state(true,false,false,3));
        assertEquals("no detector paired",AlarmWatch.State.NOT_CONFIGURED,AlarmWatch.state(true,false,true,0));
        assertEquals(AlarmWatch.State.NOT_CONFIGURED,AlarmWatch.state(true,false,true,-1));
        assertEquals(AlarmWatch.State.ON,AlarmWatch.state(true,false,true,2));
        assertTrue(AlarmWatch.homeNotice(false,false));assertTrue(AlarmWatch.homeNotice(true,true));assertFalse(AlarmWatch.homeNotice(true,false));
        assertTrue(AlarmWatch.words(AlarmWatch.State.NOT_CONFIGURED).startsWith("Not configured — no live monitoring"));
        assertFalse(AlarmWatch.words(AlarmWatch.State.NOT_CONFIGURED).contains("always show"));
        assertTrue("never claims to be a certified alarm",AlarmWatch.words(AlarmWatch.State.ON).contains("not a certified alarm"));
    }
    @Test public void dockLabelsAreFocusedOnlyUnlessChosenAndNeverTwice(){
        assertEquals(DockLabels.FOCUSED_ONLY,DockLabels.value(null));assertEquals(DockLabels.FOCUSED_ONLY,DockLabels.value("sometimes"));
        assertTrue(DockLabels.shows("focused_only",2,2));assertFalse(DockLabels.shows("focused_only",1,2));assertFalse("focus in the pill: no label",DockLabels.shows(null,0,-1));
        assertTrue(DockLabels.shows("always",1,-1));assertFalse(DockLabels.shows("hidden",2,2));
        assertTrue(DockLabels.titleAbove("hidden"));assertFalse(DockLabels.titleAbove("focused_only"));assertFalse(DockLabels.titleAbove("always"));
        assertNotEquals(DockLabels.key("adult"),DockLabels.key("child"));assertEquals("dock.labelVisibility.adult",DockLabels.key("anything"));
        for(String id:new String[]{"smartutube","netflix","disney","bbc","live","music","xbox","tvplus","library","com.example.app"})assertTrue(id,DockLabels.GLYPHS.contains(DockLabels.glyph(id)));
        assertEquals("mono",DockLabels.glyph("com.example.app"));assertNotEquals("mono",DockLabels.glyph("netflix"));
    }
    @Test public void pinResetClearsThePinAndKidsAndNothingElse(){
        Map<String,Object> before=new HashMap<>();before.put("pin","salt:hash");before.put("pinFailures",4);before.put("pinWait",99L);before.put(Profiles.PREF,Profiles.CHILD);
        before.put(KidsGuard.PREF,true);before.put(KidsGuard.PASS,5L);before.put("dock","netflix");before.put(KidsPolicy.APPS_PREF,"com.example.kids");
        Map<String,Object> after=PinReset.after(before);
        for(String k:new String[]{"pin","pinFailures","pinWait",KidsGuard.PASS})assertFalse(k,after.containsKey(k));
        assertEquals(Profiles.ADULT,after.get(Profiles.PREF));assertEquals(false,after.get(KidsGuard.PREF));
        assertEquals("netflix",after.get("dock"));assertEquals("com.example.kids",after.get(KidsPolicy.APPS_PREF));
        assertEquals("adb shell am broadcast -a io.unifiedtv.RESET_PIN --include-stopped-packages -n io.unifiedtv.launcher/io.unifiedtv.preview.PinReset",PinReset.command("io.unifiedtv.launcher"));
    }
    @Test public void aboutListsLicencesAndArtworkCredits(){
        assertTrue(About.licences().contains("Apache License 2.0"));assertTrue(About.licences().contains("MIT License"));
        List<String> art=About.artwork("[{\"file\":\"a.jpg\",\"title\":\"Example\",\"credit\":\"Made for the test\"},{\"file\":\"b.jpg\"}]");
        assertEquals(Arrays.asList("Example: Made for the test"),art);assertTrue(About.artwork("not json").isEmpty());
        assertTrue(About.flavour("io.unifiedtv.preview").contains("never the Home screen"));assertTrue(About.flavour("io.unifiedtv.launcher.devicecheck").contains("review build"));
    }
    @Test public void ratingSchemesAreDataAndUnknownIsNeverLooser(){
        assertEquals(KidsPolicy.Rating.ALLOWED,KidsPolicy.rating("PG",KidsPolicy.Scheme.UK_BBFC));
        assertEquals(KidsPolicy.Rating.REFUSED,KidsPolicy.rating("TV-G",KidsPolicy.Scheme.UK_BBFC));
        assertEquals(KidsPolicy.Rating.REFUSED,KidsPolicy.rating("U",KidsPolicy.Scheme.US_MPA_TV));
        assertEquals(KidsPolicy.Rating.ALLOWED,KidsPolicy.rating("GB-U",KidsPolicy.Scheme.UK_BBFC));
        assertEquals(KidsPolicy.Rating.ALLOWED,KidsPolicy.rating("TV-G",KidsPolicy.Scheme.UK_AND_US));
        assertEquals(KidsPolicy.Scheme.UK_BBFC,KidsPolicy.Scheme.of("nonsense"));
        assertEquals("PG, U and Uc",KidsPolicy.Scheme.UK_BBFC.allowedWords());
        assertEquals(KidsPolicy.Rating.REFUSED,KidsPolicy.rating(Arrays.asList("PG","TV-G"),KidsPolicy.Scheme.UK_BBFC));
    }
}
