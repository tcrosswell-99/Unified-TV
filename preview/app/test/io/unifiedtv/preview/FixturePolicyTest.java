package io.unifiedtv.preview;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;
public class FixturePolicyTest {
    @Test public void fixtureUsesUkTimeAndNoScore(){assertEquals("Tottenham · 18:30",FixturePolicy.label("2026-07-01T17:30:00Z","2026-07-01T19:30:00Z",Instant.parse("2026-07-01T12:00:00Z")));}
    @Test public void hiddenOutsideUkMatchdayOrAfterGrace(){assertEquals("",FixturePolicy.label("2026-07-01T17:30:00Z","2026-07-01T19:30:00Z",Instant.parse("2026-07-02T12:00:00Z")));assertEquals("",FixturePolicy.label("2026-07-01T17:30:00Z","2026-07-01T19:30:00Z",Instant.parse("2026-07-01T19:46:00Z")));}
    @Test public void liveStateNeverDisplaysAResult(){assertEquals("Tottenham · Live",FixturePolicy.label("2026-10-25T15:00:00Z","2026-10-25T17:00:00Z",Instant.parse("2026-10-25T16:00:00Z")));assertEquals("",FixturePolicy.label("postponed","",Instant.now()));}

    // Spec 7: the card names the match and never shows a score, whatever the calendar's text says.
    @Test public void scoresAndResultWordsAreRemovedFromTheMatchLine(){
        assertEquals("Tottenham v Arsenal",FixturePolicy.match("Tottenham Hotspur vs Arsenal"));
        assertEquals("Tottenham v Arsenal",FixturePolicy.match("Tottenham Hotspur 2 - 1 Arsenal (FT)"));
        assertEquals("Chelsea v Tottenham",FixturePolicy.match("Chelsea 0-3 Spurs"));
        assertEquals("Tottenham v AZ Alkmaar",FixturePolicy.match("Tottenham v AZ Alkmaar (agg 4-1) AET"));
        assertEquals("Tottenham v Brentford",FixturePolicy.match("Spurs v Brentford 3:2 pens"));
        assertEquals("Tottenham v Bayern Munich",FixturePolicy.match("Bayern Munich"));
        assertEquals("Tottenham",FixturePolicy.match(""));assertEquals("Tottenham",FixturePolicy.match(null));
        for(String s:new String[]{"Tottenham 2-1 Arsenal","Spurs 4 - 0 Leeds FT","Tottenham Hotspur 1:1 Everton"})
            assertFalse(s,FixturePolicy.match(s).matches(".*\\d.*"));
    }
    @Test public void cardSaysKickOffThenLiveThenDisappears(){
        FixturePolicy.Fixture f=FixturePolicy.fixture("Tottenham Hotspur vs Arsenal","2026-10-25T15:30:00Z","2026-10-25T17:30:00Z");
        FixturePolicy.Card before=FixturePolicy.card(f,Instant.parse("2026-10-25T09:00:00Z"));
        assertEquals("Tottenham v Arsenal",before.match);assertEquals("Kick-off 15:30",before.when);assertFalse(before.live);
        FixturePolicy.Card during=FixturePolicy.card(f,Instant.parse("2026-10-25T16:00:00Z"));
        assertTrue(during.live);assertEquals("Live",during.when);
        assertNotNull("within the 15-minute grace",FixturePolicy.card(f,Instant.parse("2026-10-25T17:40:00Z")));
        assertNull(FixturePolicy.card(f,Instant.parse("2026-10-25T17:46:00Z")));
        assertNull("not the day before",FixturePolicy.card(f,Instant.parse("2026-10-24T20:00:00Z")));
    }
    @Test public void bstAndGmtKickOffTimesAreUkLocal(){
        assertEquals("Kick-off 20:00",FixturePolicy.card(FixturePolicy.fixture("Spurs v Spurs Women","2026-07-01T19:00:00Z",""),Instant.parse("2026-07-01T10:00:00Z")).when);
        assertEquals("Kick-off 20:00",FixturePolicy.card(FixturePolicy.fixture("x","2026-12-01T20:00:00Z",""),Instant.parse("2026-12-01T10:00:00Z")).when);
    }
    @Test public void unreadableTimesGiveNoCard(){assertNull(FixturePolicy.fixture("Tottenham v Arsenal","TBC",""));assertNull(FixturePolicy.card(null,Instant.now()));}
}
