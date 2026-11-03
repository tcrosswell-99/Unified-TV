package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProfilesTest {
    @Test public void storedValuesStayCompatibleAndLabelsAreAdultsAndKids(){
        assertEquals("child",Profiles.stored("child"));
        for(String old:new String[]{"adult",null,"","guest","Child"})assertEquals("anything else is Adults, as before","adult",Profiles.stored(old));
        assertEquals("Adults",Profiles.label("adult"));assertEquals("Kids",Profiles.label("child"));assertEquals("Adults",Profiles.label(null));
    }
    @Test public void movingToAdultsAlwaysNeedsThePin(){
        assertEquals(Profiles.NEEDS_PIN,Profiles.rule("child","adult",true));
        assertEquals(Profiles.SAME,Profiles.rule("adult","adult",true));assertEquals(Profiles.SAME,Profiles.rule("child","child",true));
    }
    @Test public void kidsNeedsNoPinButOnlyOnceOneExists(){
        assertEquals(Profiles.ALLOWED,Profiles.rule("adult","child",true));
        assertEquals(Profiles.PIN_FIRST,Profiles.rule("adult","child",false));
    }
    @Test public void kidsIsNeverATrapWhenThePinRecordIsGone(){assertEquals(Profiles.ALLOWED,Profiles.rule("child","adult",false));}
}
