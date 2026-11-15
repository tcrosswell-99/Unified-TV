package io.unifiedtv.preview;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Spec 7, generalised (owner, 25 October 2026): any team, the household's clock, never a score. Test data only. */
public class FixturePolicyTest {
    static final ZoneId LONDON=ZoneId.of("Europe/London");
    static final DateTimeFormatter H24=DateTimeFormatter.ofPattern("HH:mm");
    static final Fixtures.Team SPURS=new Fixtures.Team("calendar","Tottenham","",Fixtures.COLOURS[1],Fixtures.COLOURS[0],Fixtures.Sport.FOOTBALL,Arrays.asList("Tottenham Hotspur FC","Tottenham Hotspur","Spurs"));
    static Fixtures.Fixture cal(String summary,String start,String end){return Fixtures.HaCalendar.fixture("calendar.fixtures",summary,start,end,0,LONDON);}
    static FixturePolicy.Card card(Fixtures.Fixture f,String now,Fixtures.Team team){return FixturePolicy.card(f,Instant.parse(now),LONDON,H24,team,null);}

    @Test public void scoresAndResultWordsAreRemovedFromTheMatchLine(){
        assertEquals("Tottenham v Arsenal",FixturePolicy.match("Tottenham Hotspur vs Arsenal",SPURS));
        assertEquals("Tottenham v Arsenal",FixturePolicy.match("Tottenham Hotspur 2 - 1 Arsenal (FT)",SPURS));
        assertEquals("Chelsea v Tottenham",FixturePolicy.match("Chelsea 0-3 Spurs",SPURS));
        assertEquals("Tottenham v AZ Alkmaar",FixturePolicy.match("Tottenham v AZ Alkmaar (agg 4-1) AET",SPURS));
        assertEquals("Tottenham v Brentford",FixturePolicy.match("Spurs v Brentford 3:2 pens",SPURS));
        assertEquals("the team's name goes in front when the text doesn't name it","Tottenham v Bayern Munich",FixturePolicy.match("Bayern Munich",SPURS));
        assertEquals("Tottenham",FixturePolicy.match("",SPURS));assertEquals("Tottenham",FixturePolicy.match((String)null,SPURS));
        for(String s:new String[]{"Tottenham 2-1 Arsenal","Spurs 4 - 0 Leeds FT","Tottenham Hotspur 1:1 Everton"})
            assertFalse(s,FixturePolicy.match(s,SPURS).matches(".*\\d.*"));
    }
    @Test public void withNoTeamSetTheCardSaysTheFixturesOwnText(){
        assertEquals("Leeds v Hull",FixturePolicy.match("Leeds 1-0 Hull FT",null));
        assertEquals("no club name is assumed","Bayern Munich",FixturePolicy.match("Bayern Munich",null));
        assertEquals("Matchday",FixturePolicy.match("",null));
        assertFalse(FixturePolicy.match("Spurs v Brentford",null).contains("Tottenham"));
    }
    @Test public void anyTeamWorksNotOnlyOne(){
        Fixtures.Team hearts=new Fixtures.Team("calendar","Hearts","HEA",Fixtures.COLOURS[8],Fixtures.COLOURS[1],Fixtures.Sport.FOOTBALL,Arrays.asList("Heart of Midlothian"));
        assertEquals("Hearts v Hibernian",FixturePolicy.match("Heart of Midlothian 2-2 Hibernian",hearts));
        assertEquals("Hearts v Celtic",FixturePolicy.match("Celtic",hearts));
    }
    @Test public void cardSaysKickOffThenLiveThenDisappears(){
        Fixtures.Fixture f=cal("Tottenham Hotspur vs Arsenal","2026-10-25T15:30:00Z","2026-10-25T17:30:00Z");
        FixturePolicy.Card before=card(f,"2026-10-25T09:00:00Z",SPURS);
        assertEquals("Tottenham v Arsenal",before.match);assertEquals("Kick-off 15:30",before.when);assertFalse(before.live);assertSame(SPURS,before.team);
        FixturePolicy.Card during=card(f,"2026-10-25T16:00:00Z",SPURS);assertTrue(during.live);assertEquals("Live",during.when);
        assertNotNull("within the 15-minute grace",card(f,"2026-10-25T17:40:00Z",SPURS));
        assertNull(card(f,"2026-10-25T17:46:00Z",SPURS));
        assertNull("not the day before",card(f,"2026-10-24T20:00:00Z",SPURS));
    }
    @Test public void noEndTimeUsesTheSportsUsualLength(){
        Fixtures.Fixture f=cal("x","2026-07-01T17:30:00Z","");
        assertNotNull(card(f,"2026-07-01T19:40:00Z",SPURS));assertNull(card(f,"2026-07-01T19:46:00Z",SPURS));
    }
    @Test public void kickOffTimesFollowTheHouseholdsZone(){
        assertEquals("Kick-off 20:00",card(cal("Spurs v Spurs Women","2026-07-01T19:00:00Z",""),"2026-07-01T10:00:00Z",SPURS).when);
        assertEquals("Kick-off 20:00",card(cal("x","2026-12-01T20:00:00Z",""),"2026-12-01T10:00:00Z",SPURS).when);
        FixturePolicy.Card ny=FixturePolicy.card(cal("x","2026-12-01T20:00:00Z",""),Instant.parse("2026-12-01T16:00:00Z"),ZoneId.of("America/New_York"),DateTimeFormatter.ofPattern("h:mm"),null,null);
        assertEquals("not UK time: the zone is a setting","Kick-off 3:00",ny.when);
    }
    @Test public void postponedOrUnreadableFixturesGiveNoCard(){
        assertNull(cal("Tottenham v Arsenal","TBC",""));assertNull(FixturePolicy.card(null,Instant.now(),LONDON,H24,null,null));
        assertNull(card(cal("Tottenham v Arsenal - POSTPONED","2026-10-25T15:30:00Z",""),"2026-10-25T09:00:00Z",SPURS));
        assertNull(card(cal("Tottenham v Arsenal (TBC)","2026-10-25T15:30:00Z",""),"2026-10-25T09:00:00Z",SPURS));
    }
    @Test public void aCalendarEventKeepsItsIdWhenItsTimeOrScoreChanges(){
        assertEquals(cal("Spurs v Arsenal","2026-10-25T15:30:00Z","").id,cal("Spurs 2-1 Arsenal FT","2026-10-26T19:45:00Z","").id);
    }

    // --- change detection
    static Fixtures.Fixture api(String id,String kickoff,Fixtures.Status s){return new Fixtures.Fixture(id,Arrays.asList("api:47","api:42"),"Premier League",Instant.parse(kickoff),null,s,"api-football",0,"Tottenham Hotspur","Arsenal","");}
    @Test public void aMoveIsReportedOnlyForAKnownFixtureWithASettledTime(){
        Map<String,Long> before=new HashMap<>();before.put("api:1",Instant.parse("2026-10-25T15:00:00Z").getEpochSecond());before.put("api:2",Instant.parse("2026-11-01T15:00:00Z").getEpochSecond());
        List<Fixtures.Move> m=Fixtures.moves(before,Arrays.asList(api("api:1","2026-10-25T18:45:00Z",Fixtures.Status.SCHEDULED),api("api:2","2026-11-01T15:00:00Z",Fixtures.Status.SCHEDULED),api("api:3","2026-11-08T15:00:00Z",Fixtures.Status.SCHEDULED)));
        assertEquals(1,m.size());assertEquals("api:1",m.get(0).id);assertFalse(m.get(0).otherDay(LONDON));
        assertTrue("a postponement is not a move",Fixtures.moves(before,Arrays.asList(api("api:1","2026-12-25T15:00:00Z",Fixtures.Status.POSTPONED))).isEmpty());
    }
    @Test public void theTrackerSaysKickOffMovedToOnTheDay()throws Exception{
        org.json.JSONObject t=Fixtures.track(null,Arrays.asList(api("api:1","2026-10-25T15:00:00Z",Fixtures.Status.SCHEDULED)));
        Fixtures.Fixture moved=api("api:1","2026-10-25T18:45:00Z",Fixtures.Status.SCHEDULED);
        t=Fixtures.track(t,Arrays.asList(moved));
        FixturePolicy.Card c=FixturePolicy.first(Arrays.asList(moved),Instant.parse("2026-10-25T09:00:00Z"),LONDON,H24,null,t);
        assertEquals("Kick-off moved to 18:45",c.when);assertEquals("Tottenham Hotspur v Arsenal",c.match);
        t=Fixtures.track(new org.json.JSONObject(t.toString()),Arrays.asList(moved));
        assertNotNull("the move survives a restart and the next read",Fixtures.moveOf(t,moved));
        assertNull("a later different time replaces it",Fixtures.moveOf(Fixtures.track(t,Arrays.asList(api("api:1","2026-10-25T19:00:00Z",Fixtures.Status.SCHEDULED))),moved));
    }
    @Test public void aFixtureMovedToAnotherDaySaysSoOnItsOldMatchday(){
        org.json.JSONObject t=Fixtures.track(null,Arrays.asList(api("api:1","2026-10-25T15:00:00Z",Fixtures.Status.SCHEDULED)));
        Fixtures.Fixture moved=api("api:1","2026-11-01T15:00:00Z",Fixtures.Status.SCHEDULED);t=Fixtures.track(t,Arrays.asList(moved));
        FixturePolicy.Card c=FixturePolicy.first(Arrays.asList(moved),Instant.parse("2026-10-25T09:00:00Z"),LONDON,H24,null,t);
        assertTrue(c.when,c.when.startsWith("Moved to "));assertFalse(c.live);
        FixturePolicy.Card onTheDay=FixturePolicy.first(Arrays.asList(moved),Instant.parse("2026-11-01T09:00:00Z"),LONDON,H24,null,t);
        assertEquals("Kick-off 15:00",onTheDay.when);
    }
    @Test public void theHouseholdsNameForItsTeamIsUsedOnApiFixtures(){
        Fixtures.Team t=new Fixtures.Team("api:47","Spurs","TOT",0,0,Fixtures.Sport.FOOTBALL,null);
        assertEquals("Spurs v Arsenal",FixturePolicy.match(api("api:1","2026-10-25T15:00:00Z",Fixtures.Status.SCHEDULED),t));
    }
    @Test public void teamsRoundTripAndBadgesAreGeneratedText(){
        Fixtures.Team back=Fixtures.Team.read(SPURS.json().toString());assertEquals("Tottenham",back.displayName);assertEquals(SPURS.aliases,back.aliases);assertEquals(SPURS.primary,back.primary);
        assertNull("none by default",Fixtures.Team.read(""));assertNull(Fixtures.Team.read("not json"));
        assertEquals("TOT",Fixtures.badgeText("TOT","Tottenham Hotspur"));assertEquals("QPR",Fixtures.badgeText("","Queens Park Rangers"));assertEquals("HUL",Fixtures.badgeText(null,"Hull"));
        assertEquals(0xFF111111,Fixtures.ink(0xFFFFFFFF));assertEquals(0xFFFFFFFF,Fixtures.ink(0xFF132257));
    }
}
