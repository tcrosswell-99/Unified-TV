package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class DockPolicyTest {
    @Test public void migrationDropsArbitraryPinnedPackagesAndRestoresMissingApprovedChoices(){
        List<String> order=DockPolicy.order("tcl","com.unapproved.app,netflix,netflix,bbc");
        assertEquals(Arrays.asList("netflix","bbc","smartutube","disney","live","music"),order);
    }
    @Test public void platformChoicesCannotLeakAcrossRooms(){
        assertFalse(DockPolicy.order("tcl","xbox,tvplus").contains("xbox"));
        assertFalse(DockPolicy.order("sony","tvplus").contains("tvplus"));
        assertEquals(7,DockPolicy.order("sony","").size());
        assertEquals(7,DockPolicy.order("frame","").size());
    }
    @Test public void allApprovedChoicesSurviveReordering(){
        assertEquals(Arrays.asList("music","live","bbc","disney","netflix","smartutube"),DockPolicy.order("tcl","music,live,bbc,disney,netflix,smartutube"));
    }
}
