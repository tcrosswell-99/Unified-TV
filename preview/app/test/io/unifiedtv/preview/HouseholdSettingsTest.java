package io.unifiedtv.preview;
import java.time.ZoneId;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** SET-011/SET-074 (owner, 25 October 2026): household settings, and the migration that keeps an earlier install as it was. */
public class HouseholdSettingsTest {
    @Test public void timeFollowsTheTvUnlessOverridden(){
        ZoneId tv=ZoneId.of("Asia/Tokyo");
        assertEquals(tv,HouseholdSettings.zone("",tv));assertEquals(tv,HouseholdSettings.zone(null,tv));assertEquals(tv,HouseholdSettings.zone("Not/AZone",tv));
        assertEquals(ZoneId.of("Europe/Paris"),HouseholdSettings.zone("Europe/Paris",tv));
        assertEquals("HH:mm",HouseholdSettings.clockPattern(true));assertEquals("h:mm",HouseholdSettings.clockPattern(false));
        List<String> z=HouseholdSettings.zones(Arrays.asList("Etc/GMT+1","Europe/London","America/New_York","SystemV/AST4","UTC"));
        assertEquals(Arrays.asList("America/New_York","Europe/London"),z);
    }
    @Test public void kidsRatingsFollowTheRegion(){
        assertEquals(KidsPolicy.Scheme.UK_BBFC,HouseholdSettings.defaultScheme("GB"));
        assertEquals(KidsPolicy.Scheme.US_MPA_TV,HouseholdSettings.defaultScheme("US"));
        assertEquals(KidsPolicy.Scheme.US_MPA_TV,HouseholdSettings.defaultScheme(""));
    }
    @Test public void aFreshInstallSavesNoDockAndRegionRatings(){
        Map<String,Object> m=HouseholdSettings.migrate(new HashMap<>(),"tcl","GB");
        assertFalse("the dock follows installed apps until edited",m.containsKey("dock"));
        assertEquals("uk",m.get(HouseholdSettings.RATINGS));assertEquals(HouseholdSettings.CURRENT,m.get(HouseholdSettings.VERSION));
    }
    @Test public void anEarlierInstallKeepsExactlyWhatItHad(){
        Map<String,Object> before=new HashMap<>();before.put("focus",2);before.put("scene","netflix");before.put("liveProtection","{\"live\":\"tv.example\"}");
        Map<String,Object> m=HouseholdSettings.migrate(before,"sony","US");
        assertEquals("the six it showed (and Xbox in the Sony room) become its saved dock","smartutube,netflix,disney,bbc,live,music,xbox",m.get("dock"));
        assertEquals("the UK and US table it used",KidsPolicy.Scheme.UK_AND_US.id,m.get(HouseholdSettings.RATINGS));
        assertTrue("an adult's recorded check no longer unlocks Live TV",m.containsKey("liveProtection")&&m.get("liveProtection")==null);
        before.put("dock","music");before.put(HouseholdSettings.RATINGS,"us");
        Map<String,Object> again=HouseholdSettings.migrate(before,"tcl","GB");
        assertFalse("a saved dock is never overwritten",again.containsKey("dock"));assertFalse(again.containsKey(HouseholdSettings.RATINGS));
        before.put(HouseholdSettings.VERSION,HouseholdSettings.CURRENT);assertTrue("runs once",HouseholdSettings.migrate(before,"tcl","GB").isEmpty());
    }
}
