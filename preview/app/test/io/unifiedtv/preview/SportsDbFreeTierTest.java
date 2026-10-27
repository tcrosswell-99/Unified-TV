package io.unifiedtv.preview;
import java.time.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;

/**
 * TheSportsDB's free key (owner's TCL, build 93: only 10 teams a league): the team list completed from Unified TV's own
 * clubs and from a round of fixtures, a club's number found by name and its other names, and each step of the fixtures
 * fallback chain (the team's own match, its league's rounds, the season), with the per-league record that saves
 * requests. Replies are fakes shaped like TheSportsDB's v1 JSON, capped as the free key caps them. No network.
 */
public class SportsDbFreeTierTest {
    final List<String> sent=new ArrayList<>();final Map<String,Object> replies=new HashMap<>();
    static final long NOW=Instant.parse("2026-10-27T12:00:00Z").toEpochMilli();
    @Before public void fake(){
        sent.clear();replies.clear();
        SportsDb.http=path->{sent.add(path);assertTrue("not an allowed request: "+path,SportsDb.allowed(path));
            Object r=replies.get(path);if(r instanceof Exception)throw (Exception)r;return new JSONObject(r==null?"{\"events\":null}":(String)r);};
    }
    @After public void real(){SportsDb.http=SportsDb::fetch;}

    static String ev(long id,long home,String homeName,long away,String awayName,String when,int round,String season){
        return "{\"idEvent\":\""+id+"\",\"strSport\":\"Soccer\",\"strLeague\":\"English League Championship\",\"idHomeTeam\":\""+home+"\",\"idAwayTeam\":\""+away+"\","
            +"\"strHomeTeam\":\""+homeName+"\",\"strAwayTeam\":\""+awayName+"\",\"strTimestamp\":\""+when+"\",\"strStatus\":\"Not Started\",\"strPostponed\":\"no\","
            +"\"intRound\":\""+round+"\",\"strSeason\":\""+season+"\",\"intHomeScore\":null}";
    }
    static String events(String... e){return "{\"events\":["+String.join(",",e)+"]}";}
    static String team(long id,String name,String also,long league){
        return "{\"idTeam\":\""+id+"\",\"strTeam\":\""+name+"\",\"strTeamShort\":null,\"strTeamAlternate\":\""+also+"\",\"strSport\":\"Soccer\",\"idLeague\":\""+league+"\",\"strLeague\":\"English League Championship\"}";
    }
    // Wrexham (134000) at home on 1 November; away at Leicester (133000) on 8 November; Hull v Derby has nothing to do with it.
    static final String HOME=ev(901,134000,"Wrexham",133500,"Hull City","2026-11-01T15:00:00",14,"2026-2027"),
        AWAY=ev(902,133000,"Leicester City",134000,"Wrexham","2026-11-08T12:30:00",15,"2026-2027"),
        OTHER=ev(903,133500,"Hull City",133600,"Derby County","2026-11-08T15:00:00",15,"2026-2027"),
        OLD=ev(800,134000,"Wrexham",133600,"Derby County","2026-10-18T15:00:00",12,"2026-2027");

    @Test public void theTeamsOwnMatchIsReadAndItsLeagueLookedUpOnce()throws Exception{
        replies.put("lookupteam.php?id=134000","{\"teams\":["+team(134000,"Wrexham","",4329)+"]}");
        replies.put("eventsnext.php?id=134000",events(HOME));
        SportsDb.Found f=SportsDb.read("134000",null,Collections.singletonList("Wrexham"),new JSONObject(),SportsDb.SIX_HOURS_MS,NOW);
        assertEquals("the league is learnt for next time","4329",f.league);assertEquals("134000",f.team);
        assertEquals("sdb:901",f.fixtures.get(0).id);
        assertEquals(Arrays.asList("lookupteam.php?id=134000","eventsnextleague.php?id=4329","eventsseason.php?id=4329&s=2026-2027","eventsnext.php?id=134000","eventslast.php?id=134000"),sent);
        // A team with no league here ("0"): only its own match, nothing else asked.
        sent.clear();SportsDb.read("134000","0",null,new JSONObject(),SportsDb.SIX_HOURS_MS,NOW);
        assertEquals(Arrays.asList("eventsnext.php?id=134000","eventslast.php?id=134000"),sent);
    }

    @Test public void theLeaguesNextRoundsAddTheAwayMatchTheFreeKeyLeavesOut()throws Exception{
        replies.put("eventsnext.php?id=134000",events(HOME));   // free key: one home match only
        replies.put("eventsnextleague.php?id=4329",events(OTHER.replace("\"intRound\":\"15\"","\"intRound\":\"14\"")));
        replies.put("eventsround.php?id=4329&r=14&s=2026-2027",events(HOME));
        replies.put("eventsround.php?id=4329&r=15&s=2026-2027",events(AWAY,OTHER));
        SportsDb.Found f=SportsDb.read("134000","4329",null,new JSONObject(),SportsDb.SIX_HOURS_MS,NOW);
        List<String> ids=new ArrayList<>();for(Fixtures.Fixture x:f.fixtures)ids.add(x.id);
        assertEquals("both of the team's matches, in kick-off order; nobody else's","[sdb:901, sdb:902]",ids.toString());
        assertFalse("a round answered: no need for the season",sent.contains("eventsseason.php?id=4329&s=2026-2027"));
    }

    @Test public void withNoRoundNamedTheSeasonStandsIn()throws Exception{
        replies.put("eventsnextleague.php?id=4329","{\"events\":null}");
        replies.put("eventsseason.php?id=4329&s=2026-2027",events(OLD,AWAY,OTHER));
        SportsDb.Found f=SportsDb.read("134000","4329",null,new JSONObject(),SportsDb.SIX_HOURS_MS,NOW);
        assertEquals("the season's match for the team; a match over a day ago is dropped",1,f.fixtures.size());assertEquals("sdb:902",f.fixtures.get(0).id);
    }

    @Test public void aRefusedRoundIsSkippedAndTooManyRequestsKeepsWhatTheTeamGave()throws Exception{
        replies.put("eventsnext.php?id=134000",events(HOME));
        replies.put("eventsnextleague.php?id=4329",events(HOME));
        replies.put("eventsround.php?id=4329&r=14&s=2026-2027",new SportsDb.Answered(404));
        replies.put("eventsround.php?id=4329&r=15&s=2026-2027",events(AWAY));
        assertEquals(2,SportsDb.read("134000","4329",null,new JSONObject(),SportsDb.SIX_HOURS_MS,NOW).fixtures.size());
        replies.put("eventsround.php?id=4329&r=15&s=2026-2027",new SportsDb.Answered(429));
        JSONObject leagues=new JSONObject();
        SportsDb.Found f=SportsDb.read("134000","4329",null,leagues,SportsDb.SIX_HOURS_MS,NOW);
        assertEquals("the team's own match still counts",1,f.fixtures.size());assertFalse("nothing half-read is kept",leagues.has("4329"));
        // Nothing answered at all: the caller keeps what it had.
        for(String k:new ArrayList<>(replies.keySet()))replies.put(k,new java.io.IOException("offline"));
        replies.put("eventsnextleague.php?id=4329",new java.io.IOException("offline"));replies.put("eventslast.php?id=134000",new java.io.IOException("offline"));
        try{SportsDb.read("134000","4329",null,new JSONObject(),SportsDb.SIX_HOURS_MS,NOW);fail("unreachable is not \"no fixtures\"");}catch(java.io.IOException expected){}
    }

    @Test public void eachLeagueIsReadOncePerIntervalWhicheverTeamAsks()throws Exception{
        replies.put("eventsnextleague.php?id=4329",events(HOME));
        replies.put("eventsround.php?id=4329&r=14&s=2026-2027",events(HOME));
        replies.put("eventsround.php?id=4329&r=15&s=2026-2027",events(AWAY,OTHER));
        JSONObject leagues=new JSONObject();
        SportsDb.read("134000","4329",null,leagues,SportsDb.SIX_HOURS_MS,NOW);
        assertEquals(14,leagues.getJSONObject("4329").optInt("round"));assertEquals("2026-2027",leagues.getJSONObject("4329").optString("season"));
        sent.clear();SportsDb.Found hull=SportsDb.read("133500","4329",null,leagues,SportsDb.SIX_HOURS_MS,NOW+SportsDb.HOUR_MS);
        assertEquals("only the team's own requests",Arrays.asList("eventsnext.php?id=133500","eventslast.php?id=133500"),sent);
        assertEquals("from the kept rounds",2,hull.fixtures.size());
        sent.clear();SportsDb.read("133500","4329",null,leagues,SportsDb.SIX_HOURS_MS,NOW+SportsDb.SIX_HOURS_MS);
        assertTrue("read again once older than the interval",sent.contains("eventsnextleague.php?id=4329"));
        sent.clear();SportsDb.read("133500","4329",null,leagues,SportsDb.QUARTER_MS,NOW+SportsDb.SIX_HOURS_MS+SportsDb.QUARTER_MS);
        assertTrue("near kick-off: every quarter of an hour",sent.contains("eventsnextleague.php?id=4329"));
    }

    @Test public void aClubKnownOnlyByNameIsFoundInItsLeaguesRoundWithoutSearching()throws Exception{
        replies.put("eventsnextleague.php?id=4329",events(OTHER));
        replies.put("eventsround.php?id=4329&r=15&s=2026-2027",events(AWAY,OTHER));
        replies.put("eventsnext.php?id=134000",events(HOME));
        SportsDb.Found f=SportsDb.read(null,"4329",Arrays.asList("Wrexham","Wrexham AFC"),new JSONObject(),SportsDb.SIX_HOURS_MS,NOW);
        assertEquals("its number, from the round","134000",f.team);assertEquals(2,f.fixtures.size());
        for(String r:sent)assertFalse("no name sent: "+r,r.startsWith("searchteams"));
    }

    @Test public void aClubsNumberIsFoundByNameThenItsOtherNames()throws Exception{
        replies.put("searchteams.php?t=Brighton%20and%20Hove%20Albion","{\"teams\":["+team(133619,"Brighton and Hove Albion","Brighton",4328)+"]}");
        SportsDb.TeamEntry e=SportsDb.resolve(Arrays.asList("Brighton & Hove Albion","Brighton and Hove Albion","Brighton","Seagulls"),4328);
        assertEquals(133619,e.id);assertEquals(4328,e.leagueId);
        assertEquals("the name as written first, then its other spelling",Collections.singletonList("searchteams.php?t=Brighton%20%26%20Hove%20Albion"),sent.subList(0,1));
        assertEquals(2,sent.size());
        // Another club with the name asked for is not taken, unless it is the only one and in the expected league.
        sent.clear();replies.put("searchteams.php?t=Wolves","{\"teams\":["+team(55,"Wolves Women","",9999)+"]}");
        assertNull(SportsDb.resolve(Collections.singletonList("Wolves"),4328));
        replies.put("searchteams.php?t=Wolves","{\"teams\":["+team(133599,"Wolverhampton Wanderers","",4328)+"]}");
        assertEquals(133599,SportsDb.resolve(Collections.singletonList("Wolves"),4328).id);
        // At most four names are sent for one club.
        sent.clear();assertNull(SportsDb.resolve(Arrays.asList("A1","B1","C1","D1","E1","F1"),0));assertEquals(SportsDb.MAX_NAMES,sent.size());
        // Through read: the number found by name, then the team's own matches.
        sent.clear();replies.put("eventsnext.php?id=133619",events(ev(950,133619,"Brighton and Hove Albion",1,"X","2026-11-01T15:00:00",10,"2026-2027")));
        SportsDb.Found f=SportsDb.read(null,null,Arrays.asList("Brighton & Hove Albion","Brighton and Hove Albion"),new JSONObject(),SportsDb.SIX_HOURS_MS,NOW);
        assertEquals("133619",f.team);assertEquals("its league, from the search","4328",f.league);assertEquals("sdb:950",f.fixtures.get(0).id);
    }

    @Test public void aClubNobodyKnowsGivesNoFixturesRatherThanAnError()throws Exception{
        SportsDb.Found f=SportsDb.read(null,"4396",Arrays.asList("Nowhere Town"),new JSONObject(),SportsDb.SIX_HOURS_MS,NOW);
        assertNull(f.team);assertTrue(f.fixtures.isEmpty());
    }

    @Test public void theListReadsOneRoundOfEachLeagueForEveryClub()throws Exception{
        StringBuilder ten=new StringBuilder();for(int i=0;i<10;i++)ten.append(i>0?",":"").append(team(140000+i,"Club "+i,"",4329));
        StringBuilder round=new StringBuilder();for(int i=0;i<12;i++)round.append(i>0?",":"").append(ev(5000+i,140000+2*i,"Club "+(2*i),140001+2*i,"Club "+(2*i+1),"2026-11-01T15:00:00",14,"2026-2027"));
        JSONObject cat=new JSONObject();List<String> asked=new ArrayList<>();
        for(String r=SportsDb.next(cat);r!=null;r=SportsDb.next(cat)){asked.add(r);
            String body=r.equals(SportsDb.teamsOf("English League Championship"))?"{\"teams\":["+ten+"]}"
                :r.equals(SportsDb.nextInLeague(4329))?events(ev(5000,140000,"Club 0",140001,"Club 1","2026-11-01T15:00:00",14,"2026-2027"))
                :r.equals(SportsDb.round(4329,14,"2026-2027"))?"{\"events\":["+round+"]}":"{\"teams\":null}";
            SportsDb.apply(cat,r,new JSONObject(body),7);}
        assertTrue(asked.contains("eventsround.php?id=4329&r=14&s=2026-2027"));
        assertTrue("leagues with no number known skip the round steps",!asked.contains("eventsnextleague.php?id=0"));
        JSONObject champ=null;JSONArray all=cat.getJSONArray("leagues");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).optInt("id")==2)champ=all.getJSONObject(i);
        assertEquals("the free key's ten",10,champ.getJSONArray("teams").length());assertEquals("every club in the round",24,champ.getJSONArray("more").length());
        JSONObject merged=TeamList.merged(cat,null);
        for(JSONObject l:TeamList.leagues(merged))if(l.optInt("id")==2)assertEquals("ten and the round, once each",24,TeamList.teams(l).size());
        assertEquals("4329",TeamList.leagueOf(cat,140023));assertEquals("",TeamList.leagueOf(cat,1));
        // A refused round (optional) completes the list instead of stopping it.
        assertTrue(SportsDb.optional(SportsDb.round(4329,14,"2026-2027")));assertTrue(SportsDb.optional(SportsDb.nextInLeague(4329)));assertFalse(SportsDb.optional(SportsDb.teamsOf("x")));
    }

    @Test public void aCappedListIsCompletedFromTheBundleAndTheSportsDbWins()throws Exception{
        JSONObject bundle=new JSONObject("{\"season\":\"2025-26\",\"leagues\":[{\"id\":1,\"sdb\":4328,\"teams\":["
            +"{\"name\":\"Tottenham Hotspur\",\"short\":\"TOT\",\"aliases\":[\"Spurs\",\"Tottenham\"],\"c1\":\"#132257\",\"c2\":\"#FFFFFF\"},"
            +"{\"name\":\"Brighton & Hove Albion\",\"short\":\"BHA\",\"aliases\":[\"Brighton\"],\"c1\":\"#0057B8\",\"c2\":\"#FFFFFF\"},"
            +"{\"name\":\"Wolverhampton Wanderers\",\"short\":\"WOL\",\"aliases\":[\"Wolves\"],\"c1\":\"#FDB913\",\"c2\":\"#231F20\"},"
            +"{\"name\":\"Relegated Rovers\",\"short\":\"RR\",\"aliases\":[],\"c1\":\"#FF0000\",\"c2\":\"#FFFFFF\"}]},"
            +"{\"id\":2,\"sdb\":4329,\"teams\":[{\"name\":\"Wrexham\",\"short\":\"WRE\",\"aliases\":[],\"c1\":\"#D71920\",\"c2\":\"#FFFFFF\"}]}]}");
        assertEquals("2025-26",TeamList.bundleSeason(bundle));
        JSONObject cat=new JSONObject("{\"leagues\":[{\"id\":1,\"name\":\"Premier League\",\"country\":\"England\",\"sdb\":4328,"
            +"\"teams\":[{\"id\":133616,\"name\":\"Tottenham Hotspur\",\"code\":\"TOT\",\"also\":\"Spurs\",\"c1\":-15588777},{\"id\":133619,\"name\":\"Brighton and Hove Albion\",\"code\":\"\"}],"
            +"\"more\":[{\"id\":133616,\"name\":\"Tottenham Hotspur\"},{\"id\":133599,\"name\":\"Wolves\"},{\"id\":134777,\"name\":\"Promoted United\"}]},"
            +"{\"id\":2,\"name\":\"Championship\",\"country\":\"England\",\"sdb\":4329,\"teams\":[{\"id\":134900,\"name\":\"Relegated Rovers\",\"code\":\"\"}]}]}");
        JSONObject merged=TeamList.merged(cat,bundle);
        Map<String,TeamList.Entry> pl=new LinkedHashMap<>();
        for(JSONObject l:TeamList.leagues(merged))if(l.optInt("id")==1)for(TeamList.Entry e:TeamList.teams(l))pl.put(e.name,e);
        assertEquals("TheSportsDB's names win; a promoted club is added; a bundled club now in another league moves there",
            Arrays.asList("Brighton and Hove Albion","Promoted United","Tottenham Hotspur","Wolves"),new ArrayList<>(pl.keySet()));
        TeamList.Entry brighton=pl.get("Brighton and Hove Albion");
        assertEquals(133619,brighton.id);assertEquals("the bundled short name and colours fill gaps","BHA",brighton.code);assertEquals(0xFF0057B8,brighton.c1);
        assertTrue("the bundled names are kept for matching",brighton.aliases.contains("Brighton"));assertEquals(4328,brighton.sdbLeague);
        assertEquals("TheSportsDB's colour wins",-15588777,pl.get("Tottenham Hotspur").c1);
        assertEquals("Wolves, numbered from the round",133599,pl.get("Wolves").id);assertEquals(0xFFFDB913,pl.get("Wolves").c1);
        List<String> champ=new ArrayList<>();long wrexham=-1;
        for(JSONObject l:TeamList.leagues(merged))if(l.optInt("id")==2)for(TeamList.Entry e:TeamList.teams(l)){champ.add(e.name);if(e.name.equals("Wrexham"))wrexham=e.id;}
        assertEquals(Arrays.asList("Relegated Rovers","Wrexham"),champ);assertEquals("a bundled club not listed yet stays, with no number",0,wrexham);
        assertEquals("found by its bundled other name","Wolves",TeamList.search(merged,"wolverhampton",5).get(0).name);
        assertEquals("Brighton and Hove Albion",TeamList.search(merged,"brighton",5).get(0).name);
        // Before anything is read, the bundled leagues are shown complete.
        JSONObject first=TeamList.merged(new JSONObject(),bundle);
        assertEquals(2,TeamList.leagues(first).size());assertEquals(4,TeamList.teams(TeamList.leagues(first).get(0)).size());
        assertEquals("England",TeamList.leagues(first).get(0).optString("country"));
        assertEquals(TeamList.key("AFC Bournemouth"),TeamList.key("Bournemouth"));assertEquals(TeamList.key("Brighton & Hove Albion"),TeamList.key("Brighton and Hove Albion"));
        assertEquals("fc",TeamList.key("FC"));
    }

    @Test public void aBundledClubIsSavedByNameAndNumberedLater()throws Exception{
        Fixtures.Team club=new Fixtures.Team(Fixtures.Team.CLUB,"Wrexham","WRE",0xFFD71920,0xFFFFFFFF,Fixtures.Sport.FOOTBALL,Arrays.asList("Wrexham AFC"),"","4329");
        assertTrue("picked from a list",club.listed());assertNull(club.at("sdb"));assertEquals("4329",club.league);
        Fixtures.Team back=Fixtures.Team.read(club.json().toString());assertEquals("4329",back.league);assertEquals(Fixtures.Team.CLUB,back.id);
        Fixtures.Team numbered=club.learnt("134000","4329");
        assertEquals("sdb:134000",numbered.id);assertEquals("134000",numbered.at("sdb"));assertEquals(0xFFD71920,numbered.primary);assertEquals(club.aliases,numbered.aliases);
        assertEquals("a numbered team keeps its number","sdb:134000",numbered.learnt("1","0").id);assertEquals("0",numbered.learnt(null,"0").league);
        assertEquals("not a number: ignored","",new Fixtures.Team("sdb:1","A","",0,0,null,null,"","x").league);
    }
}
