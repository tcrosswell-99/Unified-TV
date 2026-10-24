package io.unifiedtv.preview;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;
public class FixturePolicyTest {
    @Test public void fixtureUsesUkTimeAndNoScore(){assertEquals("Tottenham · 18:30",FixturePolicy.label("2026-07-01T17:30:00Z","2026-07-01T19:30:00Z",Instant.parse("2026-07-01T12:00:00Z")));}
    @Test public void hiddenOutsideUkMatchdayOrAfterGrace(){assertEquals("",FixturePolicy.label("2026-07-01T17:30:00Z","2026-07-01T19:30:00Z",Instant.parse("2026-07-02T12:00:00Z")));assertEquals("",FixturePolicy.label("2026-07-01T17:30:00Z","2026-07-01T19:30:00Z",Instant.parse("2026-07-01T19:46:00Z")));}
    @Test public void liveStateNeverDisplaysAResult(){assertEquals("Tottenham · Live",FixturePolicy.label("2026-10-25T15:00:00Z","2026-10-25T17:00:00Z",Instant.parse("2026-10-25T16:00:00Z")));assertEquals("",FixturePolicy.label("postponed","",Instant.now()));}
}
