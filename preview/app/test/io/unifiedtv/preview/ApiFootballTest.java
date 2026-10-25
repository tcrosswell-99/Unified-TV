package io.unifiedtv.preview;
import java.time.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
/** API-Football v3 adapter (owner, 25 October 2026): parsing, request budget, schedule. Synthetic replies shaped like the API's. */
public class ApiFootballTest {
    static final String LEAGUES="{\"get\":\"leagues\",\"parameters\":{\"country\":\"England\",\"current\":\"true\"},\"errors\":[],\"results\":3,\"paging\":{\"current\":1,\"total\":1},\"response\":["
        +"{\"league\":{\"id\":901,\"name\":\"Example League\",\"type\":\"League\",\"logo\":\"https://example.invalid/l.png\"},\"country\":{\"name\":\"England\",\"code\":\"GB\",\"flag\":null},\"seasons\":[{\"year\":2025,\"start\":\"2025-08-01\",\"end\":\"2026-05-30\",\"current\":false},{\"year\":2026,\"start\":\"2026-08-01\",\"end\":\"2027-05-30\",\"current\":true}]},"
        +"{\"league\":{\"id\":902,\"name\":\"Example Cup\",\"type\":\"Cup\",\"logo\":\"\"},\"country\":{\"name\":\"England\"},\"seasons\":[{\"year\":2026,\"current\":true}]},"
        +"{\"league\":{\"id\":903,\"name\":\"Old League\",\"type\":\"League\"},\"country\":{\"name\":\"England\"},\"seasons\":[{\"year\":2019,\"current\":false}]}]}";
    static final String TEAMS="{\"get\":\"teams\",\"parameters\":{\"league\":\"901\",\"season\":\"2026\"},\"errors\":[],\"results\":3,\"response\":["
        +"{\"team\":{\"id\":7002,\"name\":\"Zeta Town\",\"code\":null,\"country\":\"England\",\"founded\":1900,\"national\":false,\"logo\":\"https://example.invalid/t.png\"},\"venue\":{\"id\":1,\"name\":\"Ground\"}},"
        +"{\"team\":{\"id\":7001,\"name\":\"Alpha United\",\"code\":\"ALP\",\"country\":\"England\",\"national\":false},\"venue\":{}},"
        +"{\"team\":{\"id\":10,\"name\":\"England\",\"code\":\"ENG\",\"national\":true},\"venue\":{}}]}";
    static final String FIXTURES="{\"get\":\"fixtures\",\"parameters\":{\"team\":\"7001\",\"next\":\"10\"},\"errors\":[],\"results\":3,\"response\":["
        +fixture(5001,1793026800L,"NS","Not Started")+","+fixture(5002,1793631600L,"PST","Match Postponed")+","+fixture(5003,1794236400L,"TBD","Time to be defined")+"]}";
    static String fixture(long id,long ts,String shortStatus,String longStatus){
        return "{\"fixture\":{\"id\":"+id+",\"referee\":null,\"timezone\":\"UTC\",\"date\":\""+Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC)+"\",\"timestamp\":"+ts+",\"periods\":{\"first\":null,\"second\":null},\"venue\":{\"id\":1,\"name\":\"Ground\",\"city\":\"Town\"},\"status\":{\"long\":\""+longStatus+"\",\"short\":\""+shortStatus+"\",\"elapsed\":null}},"
            +"\"league\":{\"id\":901,\"name\":\"Example League\",\"country\":\"England\",\"season\":2026,\"round\":\"Regular Season - 9\"},"
            +"\"teams\":{\"home\":{\"id\":7001,\"name\":\"Alpha United\",\"logo\":\"x\",\"winner\":null},\"away\":{\"id\":7002,\"name\":\"Zeta Town\",\"logo\":\"x\",\"winner\":null}},"
            +"\"goals\":{\"home\":3,\"away\":1},\"score\":{\"halftime\":{\"home\":1,\"away\":0},\"fulltime\":{\"home\":3,\"away\":1},\"extratime\":{\"home\":null,\"away\":null},\"penalty\":{\"home\":null,\"away\":null}}}";
    }

    @Test public void leaguesAreLeaguesWithACurrentSeason()throws Exception{
        List<ApiFootball.League> l=ApiFootball.leagues(new JSONObject(LEAGUES));
        assertEquals(1,l.size());assertEquals(901,l.get(0).id);assertEquals(2026,l.get(0).season);assertEquals("England",l.get(0).country);
    }
    @Test public void teamsAreClubsSortedByName()throws Exception{
        List<ApiFootball.TeamEntry> t=ApiFootball.teams(new JSONObject(TEAMS));
        assertEquals(2,t.size());assertEquals("Alpha United",t.get(0).name);assertEquals("ALP",t.get(0).code);assertEquals("",t.get(1).code);
    }
    @Test public void fixturesNeverCarryAScoreAndPostponedOrTbdGiveNoCard()throws Exception{
        List<Fixtures.Fixture> f=ApiFootball.fixtures(new JSONObject(FIXTURES),0);
        assertEquals(3,f.size());assertEquals("api:5001",f.get(0).id);assertEquals(Arrays.asList("api:7001","api:7002"),f.get(0).teamIds);
        assertEquals(Fixtures.Status.SCHEDULED,f.get(0).status);assertEquals(Fixtures.Status.POSTPONED,f.get(1).status);assertEquals(Fixtures.Status.TBC,f.get(2).status);
        assertTrue(f.get(1).unsettled());assertTrue(f.get(2).unsettled());
        for(Fixtures.Fixture x:f){String all=x.json().toString();assertFalse(all,all.contains("\"goals\"")||all.contains("3-1")||all.contains("score"));}
        ZoneId z=ZoneId.of("Europe/London");Instant day=f.get(0).kickoff.minusSeconds(3600);
        FixturePolicy.Card c=FixturePolicy.first(f,day,z,java.time.format.DateTimeFormatter.ofPattern("HH:mm"),null,null);
        assertEquals("Alpha United v Zeta Town",c.match);assertFalse(c.match.matches(".*\\d.*"));
        assertNull(FixturePolicy.card(f.get(1),f.get(1).kickoff.minusSeconds(3600),z,java.time.format.DateTimeFormatter.ofPattern("HH:mm"),null,null));
    }
    @Test public void statusCodesMapAsDocumented(){
        for(String s:new String[]{"1H","HT","2H","ET","BT","P","SUSP","INT","LIVE"})assertEquals(s,Fixtures.Status.LIVE,ApiFootball.status(s));
        for(String s:new String[]{"FT","AET","PEN"})assertEquals(Fixtures.Status.FINISHED,ApiFootball.status(s));
        for(String s:new String[]{"CANC","ABD","AWD","WO"})assertEquals(Fixtures.Status.CANCELLED,ApiFootball.status(s));
        assertEquals(Fixtures.Status.UNKNOWN,ApiFootball.status("??"));
    }
    @Test public void errorsInA200ReplyAreRefusalsByKeyOnly()throws Exception{
        try{ApiFootball.fixtures(new JSONObject("{\"errors\":{\"requests\":\"You have reached the request limit for the day\"},\"response\":[]}"),0);fail();}
        catch(ApiFootball.Refused r){assertTrue(r.quota());assertFalse(r.key());assertFalse(r.getMessage().contains("limit for the day"));}
        try{ApiFootball.teams(new JSONObject("{\"errors\":{\"token\":\"Error/Missing application key\"},\"response\":[]}"));fail();}catch(ApiFootball.Refused r){assertTrue(r.key());}
        assertEquals(0,ApiFootball.fixtures(new JSONObject("{\"errors\":[],\"response\":[]}"),0).size());
    }
    @Test public void theBudgetIsAHundredAUtcDayWithRoomKeptForFixtures(){
        long day=Instant.parse("2026-10-25T23:59:00Z").toEpochMilli();
        ApiFootball.Budget b=ApiFootball.Budget.read("",day);for(int i=0;i<ApiFootball.CATALOGUE_SHARE;i++){assertTrue(b.allows(true));b=b.spend();}
        assertFalse("the team list stops at its share",b.allows(true));assertTrue("fixtures still have room",b.allows(false));
        for(int i=ApiFootball.CATALOGUE_SHARE;i<ApiFootball.DAILY_LIMIT;i++)b=b.spend();
        assertFalse(b.allows(false));assertEquals(0,b.left());
        ApiFootball.Budget saved=ApiFootball.Budget.read(b.write(),day+60_000);   // a minute later: midnight UTC has passed
        assertEquals(0,saved.used);assertTrue(saved.allows(false));
        assertEquals("kept across restarts on the same day",100,ApiFootball.Budget.read(b.write(),day).used);
        assertFalse(ApiFootball.Budget.read("",day).exhausted().allows(false));
    }
    @Test public void fixturesRefreshDailyHourlyOnMatchdayAndQuarterHourlyNearKickOff(){
        ZoneId z=ZoneId.of("Europe/London");long kick=Instant.parse("2026-10-31T15:00:00Z").toEpochMilli();
        List<Fixtures.Fixture> f=Collections.singletonList(new Fixtures.Fixture("api:1",null,"",Instant.ofEpochMilli(kick),null,Fixtures.Status.SCHEDULED,"api-football",0,"A","B",""));
        assertEquals(ApiFootball.DAY_MS,ApiFootball.interval(f,kick-2*ApiFootball.DAY_MS,z));
        assertEquals(ApiFootball.HOUR_MS,ApiFootball.interval(f,Instant.parse("2026-10-31T08:00:00Z").toEpochMilli(),z));
        assertEquals(ApiFootball.QUARTER_MS,ApiFootball.interval(f,kick-2*ApiFootball.HOUR_MS,z));
        assertEquals(ApiFootball.QUARTER_MS,ApiFootball.interval(f,kick+ApiFootball.HOUR_MS,z));
        assertTrue(ApiFootball.fixturesDue(0,f,kick,z));assertFalse(ApiFootball.fixturesDue(kick-ApiFootball.HOUR_MS-60_000,f,kick-ApiFootball.HOUR_MS,z));
        assertTrue(ApiFootball.catalogueDue(0,1));assertFalse(ApiFootball.catalogueDue(1,ApiFootball.WEEK_MS));assertTrue(ApiFootball.catalogueDue(1,ApiFootball.WEEK_MS+1));
        // A whole matchday at most: hourly for 24 h plus the quarter-hours near kick-off stays well under the 100 a day.
        int requests=0;long last=0;long start=Instant.parse("2026-10-31T00:00:00Z").toEpochMilli();
        for(long t=start;t<start+ApiFootball.DAY_MS;t+=60_000)if(ApiFootball.fixturesDue(last,f,t,z)){requests++;last=t;}
        assertTrue(String.valueOf(requests),requests<=ApiFootball.DAILY_LIMIT-ApiFootball.CATALOGUE_SHARE);
    }
    @Test public void theTeamListIsBuiltStepByStepWithoutLeagueIdsInSource()throws Exception{
        JSONObject cat=new JSONObject();
        assertEquals("/leagues?country=England&current=true",ApiFootball.next(cat));
        ApiFootball.apply(cat,ApiFootball.next(cat),new JSONObject(LEAGUES),1);
        for(String c:new String[]{"Scotland","Wales","Northern-Ireland"}){assertEquals("/leagues?country="+c+"&current=true",ApiFootball.next(cat));ApiFootball.apply(cat,ApiFootball.next(cat),new JSONObject("{\"errors\":[],\"response\":[]}"),1);}
        assertEquals("/teams?league=901&season=2026",ApiFootball.next(cat));assertArrayEquals(new int[]{0,1},ApiFootball.progress(cat));
        ApiFootball.apply(cat,ApiFootball.next(cat),new JSONObject(TEAMS),5);
        assertNull(ApiFootball.next(cat));assertEquals(5,cat.optLong("fetchedAt"));assertArrayEquals(new int[]{1,1},ApiFootball.progress(cat));
        assertEquals("Alpha United",cat.getJSONArray("leagues").getJSONObject(0).getJSONArray("teams").getJSONObject(0).getString("name"));
    }
}
