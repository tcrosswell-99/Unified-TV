package io.unifiedtv.preview;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * TheSportsDB, the default football source (owner decision OD-22, 25 October 2026): parsing, the only requests it may
 * send, the team list built league by league, and the refresh pace. Synthetic replies shaped like TheSportsDB's v1 JSON
 * (field names as open-source clients read them; not yet checked against a live reply). No network.
 */
public class SportsDbTest {
    static String team(long id,String name,String shortName,String alternate,String sport,Object api,String c1,String c2){
        return "{\"idTeam\":\""+id+"\",\"strTeam\":\""+name+"\",\"strTeamShort\":"+(shortName==null?"null":"\""+shortName+"\"")+",\"strTeamAlternate\":\""+alternate+"\","
            +"\"strSport\":\""+sport+"\",\"strLeague\":\"English Premier League\",\"idLeague\":\"4328\",\"idAPIfootball\":"+(api==null?"null":"\""+api+"\"")+","
            +"\"strColour1\":\""+c1+"\",\"strColour2\":\""+c2+"\",\"strBadge\":\"https://example.invalid/badge.png\",\"strDescriptionEN\":\"A long description.\"}";
    }
    static final String PREMIER="{\"teams\":["+team(133616,"Tottenham Hotspur","TOT","Spurs","Soccer",47,"#132257","#FFFFFF")+","
        +team(133604,"Arsenal","ARS","Gunners","Soccer",42,"#EF0107","#FFFFFF")+","+team(999,"Tottenham Hotspur Women","TOT","","Soccer",null,"","")+","
        +team(5,"Example Rugby","EXR","","Rugby",null,"","")+",{\"idTeam\":null,\"strTeam\":\"Nobody\"},"+team(133616,"Tottenham Hotspur","TOT","Spurs","Soccer",47,"#132257","#FFFFFF")+"]}";
    static String event(long id,String timestamp,String date,String time,String status,String postponed){
        return "{\"idEvent\":\""+id+"\",\"strEvent\":\"Tottenham Hotspur vs Arsenal\",\"strSport\":\"Soccer\",\"idLeague\":\"4328\",\"strLeague\":\"English Premier League\","
            +"\"strHomeTeam\":\"Tottenham Hotspur\",\"strAwayTeam\":\"Arsenal\",\"idHomeTeam\":\"133616\",\"idAwayTeam\":\"133604\",\"intHomeScore\":\"3\",\"intAwayScore\":\"1\","
            +"\"strResult\":\"Spurs won 3-1\",\"strTimestamp\":"+(timestamp==null?"null":"\""+timestamp+"\"")+",\"dateEvent\":\""+date+"\",\"strTime\":"+(time==null?"null":"\""+time+"\"")+","
            +"\"strStatus\":"+(status==null?"null":"\""+status+"\"")+",\"strPostponed\":\""+postponed+"\",\"strThumb\":\"https://example.invalid/t.jpg\"}";
    }

    @Test public void teamsAreFootballClubsByNameWithTheirOtherNameApiNumberAndColours()throws Exception{
        List<SportsDb.TeamEntry> t=SportsDb.teams(new JSONObject(PREMIER));
        assertEquals("rugby, a team without a number and a repeat are left out",3,t.size());
        assertEquals(Arrays.asList("Arsenal","Tottenham Hotspur","Tottenham Hotspur Women"),Arrays.asList(t.get(0).name,t.get(1).name,t.get(2).name));
        SportsDb.TeamEntry spurs=t.get(1);
        assertEquals(133616,spurs.id);assertEquals("TOT",spurs.code);assertEquals("Spurs",spurs.also);assertEquals(47,spurs.api);
        assertEquals(0xFF132257,spurs.c1);assertEquals(0xFFFFFFFF,spurs.c2);
        assertEquals("no API-Football number: 0",0,t.get(2).api);assertEquals(0,t.get(2).c1);
        assertTrue("an empty league",SportsDb.teams(new JSONObject("{\"teams\":null}")).isEmpty());
        assertTrue(SportsDb.teams(new JSONObject()).isEmpty());assertTrue(SportsDb.teams(null).isEmpty());
        StringBuilder many=new StringBuilder("{\"teams\":[");for(int i=1;i<=500;i++)many.append(i>1?",":"").append(team(i,"Club "+i,null,"","Soccer",null,"",""));
        assertEquals("bounded whatever arrives",SportsDb.MAX_TEAMS,SportsDb.teams(new JSONObject(many+"]}")).size());
        assertEquals(0,SportsDb.colour("red"));assertEquals(0,SportsDb.colour(null));assertEquals(0xFF00A650,SportsDb.colour("00a650"));
    }

    @Test public void fixturesAreReadInUtcAndNeverCarryAScore()throws Exception{
        String body="{\"events\":["+event(1,"2026-10-31T15:00:00",  "2026-10-31","15:00:00","Not Started","no")+","
            +event(2,"2026-11-08T16:30:00+00:00","2026-11-08","16:30:00",null,"no")+","
            +event(3,null,"2026-11-22","17:30:00+00:00","NS","no")+","
            +event(4,"2026-11-29T00:00:00","2026-11-29","00:00:00","Not Started","no")+","
            +event(5,"2026-12-06T15:00:00","2026-12-06","15:00:00","Not Started","yes")+","
            +"{\"idEvent\":\"6\",\"idHomeTeam\":null,\"idAwayTeam\":\"1\"},"+event(1,"2026-10-31T15:00:00","2026-10-31","15:00:00","Not Started","no")+"]}";
        List<Fixtures.Fixture> f=SportsDb.fixtures(new JSONObject(body),7);
        assertEquals("a fixture without both teams and a repeat are left out",5,f.size());
        assertEquals("sdb:1",f.get(0).id);assertEquals(Arrays.asList("sdb:133616","sdb:133604"),f.get(0).teamIds);
        assertEquals(Instant.parse("2026-10-31T15:00:00Z"),f.get(0).kickoff);assertEquals(Fixtures.Status.SCHEDULED,f.get(0).status);
        assertEquals(SportsDb.SOURCE,f.get(0).source);assertEquals(7,f.get(0).lastChanged);assertEquals("English Premier League",f.get(0).competition);
        assertEquals("no status at all is not started",Fixtures.Status.SCHEDULED,f.get(1).status);
        assertEquals("date and time when there is no timestamp",Instant.parse("2026-11-22T17:30:00Z"),f.get(2).kickoff);
        assertEquals("midnight UTC is a kick-off not yet decided",Fixtures.Status.TBC,f.get(3).status);assertTrue(f.get(3).unsettled());
        assertEquals(Fixtures.Status.POSTPONED,f.get(4).status);assertTrue(f.get(4).unsettled());
        for(Fixtures.Fixture x:f){String all=x.json().toString();assertFalse(all,all.contains("3-1")||all.contains("won")||all.contains("Score")||all.contains("example.invalid"));}
        // The card: kick-off on the UK clock, "Live" during the match, never a score.
        ZoneId uk=ZoneId.of("Europe/London");DateTimeFormatter hhmm=DateTimeFormatter.ofPattern("HH:mm");
        FixturePolicy.Card before=FixturePolicy.first(f,Instant.parse("2026-10-31T09:00:00Z"),uk,hhmm,null,null);
        assertEquals("Tottenham Hotspur v Arsenal",before.match);assertEquals("Kick-off 15:00",before.when);
        assertEquals("Live",FixturePolicy.first(f,Instant.parse("2026-10-31T15:20:00Z"),uk,hhmm,null,null).when);
        assertTrue("results under \"results\" (older eventslast replies)",SportsDb.fixtures(new JSONObject("{\"results\":["+event(9,"2026-10-24T14:00:00","2026-10-24","14:00:00","Match Finished","no")+"]}"),0).get(0).status==Fixtures.Status.FINISHED);
        assertTrue(SportsDb.fixtures(new JSONObject("{\"events\":null}"),0).isEmpty());assertTrue(SportsDb.fixtures(null,0).isEmpty());
    }

    @Test public void statusWordsAndCodesMap()throws Exception{
        for(String s:new String[]{"1H","HT","2H","Half Time","Second Half","In Progress","LIVE"})assertEquals(s,Fixtures.Status.LIVE,SportsDb.status(s,"no"));
        for(String s:new String[]{"FT","AET","PEN","Match Finished"})assertEquals(s,Fixtures.Status.FINISHED,SportsDb.status(s,""));
        for(String s:new String[]{"Cancelled","CANC","Abandoned","ABD"})assertEquals(s,Fixtures.Status.CANCELLED,SportsDb.status(s,null));
        assertEquals(Fixtures.Status.POSTPONED,SportsDb.status("Postponed",null));assertEquals(Fixtures.Status.POSTPONED,SportsDb.status("Not Started","yes"));
        assertEquals(Fixtures.Status.TBC,SportsDb.status("Time to be defined",null));assertEquals(Fixtures.Status.UNKNOWN,SportsDb.status("??",null));
    }

    @Test public void recentFixturesOnlyKeepAMatchThatHasStarted()throws Exception{
        long now=Instant.parse("2026-10-31T15:40:00Z").toEpochMilli();
        Fixtures.Fixture next=new Fixtures.Fixture("sdb:2",null,"",Instant.parse("2026-11-08T16:30:00Z"),null,Fixtures.Status.SCHEDULED,SportsDb.SOURCE,0,"A","B","");
        Fixtures.Fixture today=new Fixtures.Fixture("sdb:1",null,"",Instant.parse("2026-10-31T15:00:00Z"),null,Fixtures.Status.LIVE,SportsDb.SOURCE,0,"A","B","");
        Fixtures.Fixture old=new Fixtures.Fixture("sdb:0",null,"",Instant.parse("2026-10-24T15:00:00Z"),null,Fixtures.Status.FINISHED,SportsDb.SOURCE,0,"A","B","");
        List<Fixtures.Fixture> m=SportsDb.merge(Collections.singletonList(next),Arrays.asList(today,old,next),now);
        assertEquals(Arrays.asList("sdb:2","sdb:1"),Arrays.asList(m.get(0).id,m.get(1).id));assertEquals(2,m.size());
    }

    // Only the one HTTPS host, and only league names and numbers, team numbers, rounds and seasons, or a club's name.
    @Test public void onlyLeaguesTeamNumbersRoundsAndClubNamesAreSent()throws Exception{
        assertTrue(SportsDb.BASE.startsWith("https://www.thesportsdb.com/api/v1/json/"));
        assertEquals("search_all_teams.php?l=English%20Premier%20League",SportsDb.teamsOf("English Premier League"));
        for(SportsDb.League l:SportsDb.LEAGUES)for(String n:l.names)assertTrue(n,SportsDb.allowed(SportsDb.teamsOf(n)));
        assertTrue(SportsDb.allowed(SportsDb.upcoming("133616")));assertTrue(SportsDb.allowed(SportsDb.recent("133616")));assertTrue(SportsDb.allowed("lookupteam.php?id=133616"));
        for(String ok:new String[]{SportsDb.nextInLeague(4328),SportsDb.round(4328,11,"2026-2027"),SportsDb.season(4329,"2026-2027"),SportsDb.lookup("133616"),SportsDb.searchTeams("Brighton & Hove Albion"),SportsDb.searchTeams("Atlético Madrid")})
            assertTrue(ok,SportsDb.allowed(ok));
        assertEquals("searchteams.php?t=Brighton%20%26%20Hove%20Albion",SportsDb.searchTeams("Brighton & Hove Albion"));
        assertEquals("only letters, digits and a few marks; at most 40","searchteams.php?t=Spurs%20x%201",SportsDb.searchTeams("Spurs?x=1"));
        assertNull(SportsDb.searchTeams("  ?!  "));assertNull(SportsDb.searchTeams(null));
        for(String bad:new String[]{"searchteams.php?t=Spurs&x=1","eventsnext.php?id=1&x=2","eventsnext.php?id=abc","search_all_teams.php?l=a&s=Soccer","eventsround.php?id=1&r=1",
                "eventsround.php?id=1&r=1&s=x","eventsseason.php?id=x&s=2026-2027","eventsnextleague.php?id=","../v2/json/x","https://example.invalid/x",null})
            assertFalse(bad,SportsDb.allowed(bad));
        try{SportsDb.fetch("eventsnext.php?id=abc");fail();}catch(IllegalArgumentException expected){}catch(Exception e){fail(e.toString());}
        assertEquals("2026-2027",SportsDb.seasonOf(Instant.parse("2026-10-27T12:00:00Z").toEpochMilli()));
        assertEquals("2025-2026",SportsDb.seasonOf(Instant.parse("2026-05-01T12:00:00Z").toEpochMilli()));
    }

    @Test public void allUkLeaguesAreOffered()throws Exception{
        Map<String,Integer> per=new LinkedHashMap<>();for(SportsDb.League l:SportsDb.LEAGUES)per.merge(l.country,1,Integer::sum);
        assertEquals(Integer.valueOf(5),per.get("England"));assertEquals(Integer.valueOf(4),per.get("Scotland"));
        assertEquals(Integer.valueOf(1),per.get("Wales"));assertEquals(Integer.valueOf(1),per.get("Northern Ireland"));
        assertEquals(Arrays.asList(SportsDb.COUNTRIES),new ArrayList<>(per.keySet()));
        Set<Integer> ids=new HashSet<>();for(SportsDb.League l:SportsDb.LEAGUES)assertTrue("ids unique",ids.add(l.id));
    }

    @Test public void theTeamListIsBuiltLeagueByLeagueTryingAnotherNameWhenOneIsEmpty()throws Exception{
        JSONObject cat=new JSONObject();int requests=0;
        for(String r=SportsDb.next(cat);r!=null;r=SportsDb.next(cat)){
            assertTrue(r,SportsDb.allowed(r));assertTrue("at most two names, a next event and a round a league",++requests<=4*SportsDb.LEAGUES.length);
            JSONObject reply=r.equals(SportsDb.teamsOf("English Premier League"))?new JSONObject(PREMIER)
                :r.equals(SportsDb.teamsOf("Scottish Premier League"))?new JSONObject("{\"teams\":["+team(133647,"Heart of Midlothian","HEA","Hearts","Soccer",254,"#7A263A","#FFFFFF")+"]}")
                :new JSONObject("{\"teams\":null}");
            assertEquals(SportsDb.LEAGUES.length,SportsDb.progress(cat)[1]);
            SportsDb.apply(cat,r,reply,42);
        }
        assertEquals(42,cat.optLong("fetchedAt"));assertArrayEquals(new int[]{11,11},SportsDb.progress(cat));
        assertEquals("no round named: none asked for",0,cat.getJSONArray("leagues").getJSONObject(0).optInt("round",-1));
        List<JSONObject> leagues=TeamList.leagues(cat);assertEquals("leagues with no teams are left out of the picker",2,leagues.size());
        assertEquals("Premier League",leagues.get(0).optString("name"));assertEquals("England",leagues.get(0).optString("country"));assertEquals(4328,leagues.get(0).optLong("sdb"));
        assertEquals("the second name was tried for Scotland's top flight","Premiership",leagues.get(1).optString("name"));
        JSONArray all=cat.getJSONArray("leagues");for(int i=1;i<all.length();i++)assertTrue("in the table's order",all.getJSONObject(i-1).optInt("id")<all.getJSONObject(i).optInt("id"));
        TeamList.Entry spurs=TeamList.search(cat,"spurs",5).get(0);
        assertEquals("found by its other name","Tottenham Hotspur",spurs.name);assertEquals(133616,spurs.id);assertEquals(47,spurs.api);assertEquals(0xFF132257,spurs.c1);
        assertEquals("Heart of Midlothian",TeamList.search(cat,"hearts",5).get(0).name);
    }

    @Test public void fixturesAreReadAFewTimesADayAndEveryQuarterHourNearKickOff()throws Exception{
        long kick=Instant.parse("2026-10-31T15:00:00Z").toEpochMilli();
        List<Fixtures.Fixture> f=Collections.singletonList(new Fixtures.Fixture("sdb:1",null,"",Instant.ofEpochMilli(kick),null,Fixtures.Status.SCHEDULED,SportsDb.SOURCE,0,"A","B",""));
        assertEquals(SportsDb.SIX_HOURS_MS,SportsDb.interval(f,kick-SportsDb.DAY_MS));
        assertEquals(SportsDb.QUARTER_MS,SportsDb.interval(f,kick-2*SportsDb.HOUR_MS));assertEquals(SportsDb.QUARTER_MS,SportsDb.interval(f,kick+2*SportsDb.HOUR_MS));
        assertTrue(SportsDb.fixturesDue(0,f,kick));assertTrue("a clock that went back",SportsDb.fixturesDue(kick,f,kick-1));
        int quiet=0,matchday=0;long last=0,start=Instant.parse("2026-10-29T00:00:00Z").toEpochMilli();
        for(long t=start;t<start+SportsDb.DAY_MS;t+=60_000)if(SportsDb.fixturesDue(last,f,t)){quiet++;last=t;}
        assertTrue("a day without a match: at most four reads, "+quiet,quiet<=4);
        start=Instant.parse("2026-10-31T00:00:00Z").toEpochMilli();
        for(long t=start;t<start+SportsDb.DAY_MS;t+=60_000)if(SportsDb.fixturesDue(last,f,t)){matchday++;last=t;}
        assertTrue("matchday: a few plus the quarter-hours near kick-off, "+matchday,matchday<=4+25);
        assertTrue(SportsDb.catalogueDue(0,1));assertFalse(SportsDb.catalogueDue(1,SportsDb.WEEK_MS));assertTrue(SportsDb.catalogueDue(1,SportsDb.WEEK_MS+1));
    }
}
