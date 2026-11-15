package io.unifiedtv.preview;
import android.content.Context;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.time.*;
import java.util.*;

/**
 * Where Home's matchday card gets its fixtures (owner decision OD-22, 25 October 2026): TheSportsDB by default with no
 * key; API-Football first only when the household added its own key, and TheSportsDB whenever API-Football refuses.
 * Both services are fakes here: every request is recorded and none reaches the network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FootballFeedTest {
    Context app;final List<String> sent=new ArrayList<>(),apiSent=new ArrayList<>();
    long kick;String sdbReply,apiReply;Exception apiFails;
    @Before public void start()throws Exception{
        app=RuntimeEnvironment.getApplication();FixtureFeed.prefs(app).edit().clear().commit();app.getSharedPreferences("vault",0).edit().clear().commit();
        Vault.testKey=VaultTest.aes();
        kick=LocalDate.now(ZoneOffset.UTC).plusDays(3).atTime(12,0).toInstant(ZoneOffset.UTC).toEpochMilli();   // midday UTC: the same day on the UK clock, moved or not
        sdbReply=events(kick);apiReply=null;apiFails=null;
        SportsDb.http=path->{sent.add(path);if(!SportsDb.allowed(path))throw new AssertionError("not an allowed request: "+path);
            return path.startsWith("eventsnext")?new JSONObject(sdbReply):new JSONObject("{\"results\":null}");};
        ApiFootball.http=(key,path)->{apiSent.add(path);if(apiFails!=null)throw apiFails;return new JSONObject(apiReply);};
        FixtureFeed.forget(app,"fixtures.json");
    }
    @After public void stop(){SportsDb.http=SportsDb::fetch;ApiFootball.http=ApiFootball::get;Vault.testKey=null;}
    static String events(long kickMs){
        String ts=Instant.ofEpochMilli(kickMs).atOffset(ZoneOffset.UTC).toLocalDateTime().toString();
        return "{\"events\":[{\"idEvent\":\"2070001\",\"strSport\":\"Soccer\",\"strLeague\":\"English Premier League\",\"idHomeTeam\":\"133616\",\"idAwayTeam\":\"133604\","
            +"\"strHomeTeam\":\"Tottenham Hotspur\",\"strAwayTeam\":\"Arsenal\",\"strTimestamp\":\""+ts+"\",\"strStatus\":\"Not Started\",\"strPostponed\":\"no\",\"intHomeScore\":null}]}";
    }
    static Fixtures.Team spurs(String alt){return new Fixtures.Team("sdb:133616","Tottenham Hotspur","TOT",0xFF132257,0xFFFFFFFF,Fixtures.Sport.FOOTBALL,null,alt);}

    @Test public void noTeamNoCalendarNothingIsFetched(){
        assertTrue(FixtureFeed.refresh(app).isEmpty());assertTrue(sent.isEmpty());assertTrue(apiSent.isEmpty());
    }
    @Test public void aTeamFromTheListIsAllItTakes(){
        FixtureFeed.saveTeam(app,spurs("api:47"));
        assertFalse("no key saved",FixtureFeed.apiConfigured(app));assertTrue(FixtureFeed.listed(app));
        List<Fixtures.Fixture> f=FixtureFeed.refresh(app);
        assertEquals(1,f.size());assertEquals("sdb:2070001",f.get(0).id);assertEquals(SportsDb.SOURCE,f.get(0).source);
        assertEquals("only the team's number is sent: its record once (for its league), its next and last match",
            Arrays.asList("lookupteam.php?id=133616","eventsnext.php?id=133616","eventslast.php?id=133616"),sent);
        Fixtures.Team learnt=FixtureFeed.team(app);
        assertEquals("no league in the reply: not asked again","0",learnt.league);assertEquals("sdb:133616",learnt.id);assertEquals("api:47",learnt.alt);assertEquals(0xFF132257,learnt.primary);
        assertTrue("API-Football is never asked without a key",apiSent.isEmpty());
        assertEquals(SportsDb.SOURCE,FixtureFeed.lastSource(app));
        // Kept on the TV: the next look inside six hours sends nothing and shows the same fixture.
        sent.clear();assertEquals("sdb:2070001",FixtureFeed.refresh(app).get(0).id);assertTrue(sent.isEmpty());
    }
    @Test public void aMovedKickOffRaisesTheChangeNotice(){
        FixtureFeed.saveTeam(app,spurs(""));FixtureFeed.refresh(app);
        sdbReply=events(kick+2*3_600_000L);FixtureFeed.forget(app,"fixtures.json");   // due again
        Fixtures.Fixture moved=FixtureFeed.refresh(app).get(0);
        Fixtures.Move m=Fixtures.moveOf(FixtureFeed.track(app),moved);
        assertNotNull("the move is recorded",m);assertEquals(kick/1000,m.from.getEpochSecond());assertEquals(kick/1000+7200,m.to.getEpochSecond());
        ZoneId uk=ZoneId.of("Europe/London");
        FixturePolicy.Card card=FixturePolicy.first(Collections.singletonList(moved),moved.kickoff.minusSeconds(3600),uk,java.time.format.DateTimeFormatter.ofPattern("HH:mm"),spurs(""),FixtureFeed.track(app));
        assertTrue(card.when,card.when.startsWith("Kick-off moved to "));
    }
    @Test public void anUnreachableSourceKeepsTheLastFixtures(){
        FixtureFeed.saveTeam(app,spurs(""));FixtureFeed.refresh(app);
        SportsDb.http=path->{throw new IOException("offline");};FixtureFeed.forget(app,"fixtures.json");
        assertTrue("nothing kept: nothing invented",FixtureFeed.refresh(app).isEmpty());
        SportsDb.http=path->new JSONObject(sdbReply);FixtureFeed.refresh(app);
        JSONObject cache=FixtureFeed.load(app,"fixtures.json");long fetched=cache.optLong("fetchedAt");
        SportsDb.http=path->{throw new IOException("offline");};
        assertEquals("not due: the kept fixtures",1,FixtureFeed.refresh(app).size());assertEquals(fetched,FixtureFeed.load(app,"fixtures.json").optLong("fetchedAt"));
    }
    @Test public void aSavedKeyIsAskedFirstThenTheSportsDbWhenItRefuses()throws Exception{
        FixtureFeed.saveKey(app,"0123456789abcdef0123456789abcdef");FixtureFeed.saveTeam(app,spurs("api:47"));
        apiReply="{\"errors\":[],\"response\":["+ApiFootballTest.fixture(9001,kick/1000,"NS","Not Started")+"]}";
        List<Fixtures.Fixture> f=FixtureFeed.refresh(app);
        assertEquals(Collections.singletonList("/fixtures?team=47&next="+ApiFootball.NEXT),apiSent);assertTrue("TheSportsDB not needed",sent.isEmpty());
        assertEquals("api:9001",f.get(0).id);assertEquals(ApiFootball.SOURCE,FixtureFeed.lastSource(app));assertEquals(1,FixtureFeed.budget(app).used);
        // API-Football refuses (VPN or proxy words): TheSportsDB answers, and Settings can say why.
        apiFails=new ApiFootball.Refused("access,"+ApiFootball.PROXY);FixtureFeed.forget(app,"fixtures.json");
        f=FixtureFeed.refresh(app);
        assertEquals(SportsDb.SOURCE,f.get(0).source);assertEquals("sdb:2070001",f.get(0).id);assertEquals(ApiFootball.VPN_WORDS,FixtureFeed.problem(app));
        // Today's API-Football requests used up: TheSportsDB, without asking API-Football at all.
        apiFails=null;apiSent.clear();FixtureFeed.noteUsed(app,ApiFootball.DAILY_LIMIT);FixtureFeed.forget(app,"fixtures.json");
        assertEquals(SportsDb.SOURCE,FixtureFeed.refresh(app).get(0).source);assertTrue(apiSent.isEmpty());
    }
    @Test public void aTeamWithoutAnApiFootballNumberStaysOnTheSportsDbEvenWithAKey()throws Exception{
        FixtureFeed.saveKey(app,"0123456789abcdef0123456789abcdef");FixtureFeed.saveTeam(app,spurs(""));
        assertEquals(SportsDb.SOURCE,FixtureFeed.refresh(app).get(0).source);assertTrue(apiSent.isEmpty());
    }
    @Test public void anEarlierApiFootballTeamKeepsWorkingWithItsKey()throws Exception{
        FixtureFeed.saveKey(app,"0123456789abcdef0123456789abcdef");
        FixtureFeed.saveTeam(app,new Fixtures.Team("api:47","Spurs","TOT",0,0,Fixtures.Sport.FOOTBALL,null));
        apiReply="{\"errors\":[],\"response\":["+ApiFootballTest.fixture(9001,kick/1000,"NS","Not Started")+"]}";
        assertEquals("api:9001",FixtureFeed.refresh(app).get(0).id);assertTrue(sent.isEmpty());
    }
    /** Replies by request, for the tests below; anything else is TheSportsDB's "nothing". */
    private void replies(Map<String,String> r){
        SportsDb.http=path->{sent.add(path);if(!SportsDb.allowed(path))throw new AssertionError("not an allowed request: "+path);String b=r.get(path);return new JSONObject(b==null?"{\"events\":null}":b);};
    }
    static String wrexham(long id,long home,String homeName,long away,String awayName,long kickMs,int round){
        String ts=Instant.ofEpochMilli(kickMs).atOffset(ZoneOffset.UTC).toLocalDateTime().toString();
        return "{\"idEvent\":\""+id+"\",\"strSport\":\"Soccer\",\"strLeague\":\"English League Championship\",\"idHomeTeam\":\""+home+"\",\"idAwayTeam\":\""+away+"\","
            +"\"strHomeTeam\":\""+homeName+"\",\"strAwayTeam\":\""+awayName+"\",\"strTimestamp\":\""+ts+"\",\"strStatus\":\"Not Started\",\"intRound\":\""+round+"\",\"strSeason\":\"2026-2027\"}";
    }
    @Test public void aBundledClubIsNumberedByItsFirstReadAndItsAwayMatchFoundInItsLeague(){
        Fixtures.Team club=new Fixtures.Team(Fixtures.Team.CLUB,"Wrexham","WRE",0xFFD71920,0xFFFFFFFF,Fixtures.Sport.FOOTBALL,Collections.singletonList("Wrexham AFC"),"","4329");
        FixtureFeed.saveTeam(app,club);assertTrue("picked from the list",FixtureFeed.listed(app));
        Map<String,String> r=new HashMap<>();
        r.put("eventsnextleague.php?id=4329","{\"events\":["+wrexham(1,133500,"Hull City",133600,"Derby County",kick,14)+"]}");
        r.put("eventsround.php?id=4329&r=14&s=2026-2027","{\"events\":["+wrexham(1,133500,"Hull City",133600,"Derby County",kick,14)+","+wrexham(2,133000,"Leicester City",134000,"Wrexham",kick,14)+"]}");
        r.put("eventsnext.php?id=134000","{\"events\":["+wrexham(3,134000,"Wrexham",133500,"Hull City",kick+7*86_400_000L,15)+"]}");
        replies(r);
        List<Fixtures.Fixture> f=FixtureFeed.refresh(app);
        assertEquals("the away match (league round) first, then the home one (team's own)",Arrays.asList("sdb:2","sdb:3"),Arrays.asList(f.get(0).id,f.get(1).id));
        Fixtures.Team now=FixtureFeed.team(app);
        assertEquals("numbered from its league's round, no name sent","sdb:134000",now.id);assertEquals("4329",now.league);assertEquals("Wrexham",now.displayName);assertEquals(0xFFD71920,now.primary);
        for(String s:sent)assertFalse(s,s.startsWith("searchteams"));
        assertNotNull("the change record is kept",FixtureFeed.track(app).optJSONObject("seen").opt("sdb:2"));
        assertFalse(FixtureFeed.noneFound(app));
        assertTrue("each league's rounds are kept",FixtureFeed.load(app,FixtureFeed.LEAGUES).has("4329"));
    }
    @Test public void aTeamPickedWhileAReadIsOutNeverGetsTheOldTeamsFixtures(){
        FixtureFeed.saveTeam(app,spurs(""));
        Fixtures.Team wrexham=new Fixtures.Team(Fixtures.Team.CLUB,"Wrexham","WRE",0xFFD71920,0xFFFFFFFF,Fixtures.Sport.FOOTBALL,null,"","0");
        // Home's read for Spurs is out when the adult picks Wrexham in Settings (saveTeam clears what was kept).
        SportsDb.http=path->{sent.add(path);if(path.startsWith("eventsnext.php?id=133616")){FixtureFeed.saveTeam(app,wrexham);return new JSONObject(sdbReply);}return new JSONObject("{\"events\":null}");};
        assertTrue("the old team's fixtures are not shown",FixtureFeed.refresh(app).isEmpty());
        assertFalse("nor kept",FixtureFeed.load(app,"fixtures.json").has("fetchedAt"));
        sent.clear();FixtureFeed.refresh(app);
        assertTrue("the new team is read at once, not \"not due\"",sent.contains("searchteams.php?t=Wrexham"));
        assertTrue("and nothing was found for it",FixtureFeed.noneFound(app));
        // Fixtures kept for one team never count for another, even if the team changed without clearing them.
        FixtureFeed.forget(app,"fixtures.json");FixtureFeed.saveTeam(app,spurs(""));
        SportsDb.http=path->new JSONObject(path.startsWith("eventsnext")?sdbReply:"{\"events\":null}");
        assertEquals(1,FixtureFeed.refresh(app).size());
        FixtureFeed.prefs(app).edit().putString(FixtureFeed.TEAM,wrexham.json().toString()).commit();
        assertFalse("Spurs' kept empty-or-not list says nothing about Wrexham",FixtureFeed.noneFound(app));
        sent.clear();SportsDb.http=path->{sent.add(path);return new JSONObject("{\"events\":null}");};
        assertTrue(FixtureFeed.refresh(app).isEmpty());assertFalse("read again for Wrexham",sent.isEmpty());
        // A bundled club numbered by its own read is still the same team: its fixtures are kept.
        assertTrue(FixtureFeed.sameTeam(wrexham,wrexham.learnt("134000","4329")));assertFalse(FixtureFeed.sameTeam(wrexham,spurs("")));
    }
    @Test public void aClubWithNoFixturesFoundIsSaidPlainly(){
        FixtureFeed.saveTeam(app,new Fixtures.Team(Fixtures.Team.CLUB,"Nowhere Town","",0,0,Fixtures.Sport.FOOTBALL,null,"","4396"));
        SportsDb.http=path->{throw new IOException("offline");};
        assertTrue(FixtureFeed.refresh(app).isEmpty());assertFalse("unreachable is not \"none found\"",FixtureFeed.noneFound(app));
        replies(new HashMap<>());
        assertTrue(FixtureFeed.refresh(app).isEmpty());assertTrue("answered, and nothing for this club",FixtureFeed.noneFound(app));
        assertEquals("No fixtures found yet for Nowhere Town.",SetupPages.noFixtures("Nowhere Town"));
        assertTrue(sent.contains("searchteams.php?t=Nowhere%20Town"));assertEquals("still known only by name",Fixtures.Team.CLUB,FixtureFeed.team(app).id);
    }
    @Test public void theTeamListIsBuiltWithoutAKeyAndCarriesOnAfterAnOutage(){
        SportsDb.paceMs=0;
        try{
            int[] calls={0};
            SportsDb.http=path->{if(++calls[0]==3)throw new IOException("offline");sent.add(path);return path.contains("English%20Premier%20League")?new JSONObject(SportsDbTest.PREMIER):new JSONObject("{\"teams\":null}");};
            String[] problem={null};FixtureFeed.build(app,(cat,p)->problem[0]=p);
            assertEquals(FixtureFeed.UNREACHABLE,problem[0]);assertEquals("the Premier League's list and its next event",1,SportsDb.progress(FixtureFeed.catalogue(app))[0]);
            FixtureFeed.build(app,(cat,p)->problem[0]=p);
            assertNull(problem[0]);JSONObject cat=FixtureFeed.catalogue(app);assertTrue(cat.optLong("fetchedAt")>0);
            assertEquals("each league asked once, an empty one under its other names too",new HashSet<>(sent).size(),sent.size());
            assertEquals("Tottenham Hotspur",TeamList.search(cat,"tott",3).get(0).name);
            sent.clear();FixtureFeed.build(app,(c,p)->{});assertTrue("a complete list is kept for a week",sent.isEmpty());
            assertFalse("no key was needed",FixtureFeed.apiConfigured(app));
        }finally{SportsDb.paceMs=2_500;}
    }
}
