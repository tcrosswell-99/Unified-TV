package io.unifiedtv.preview;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;

/**
 * Unified TV's own list of English clubs (res/raw/football_teams.json), which completes TheSportsDB's free-key team
 * lists (10 a league, owner's TCL, build 93): marked with its season, the Premier League's 20 clubs and the Championship's
 * and League One's 24, names and colours only, and every name matching one club. Merged with a capped reply of 10, each
 * league is whole again. No network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FootballBundleTest {
    static String team(long id,String name,long league){
        return "{\"idTeam\":\""+id+"\",\"strTeam\":\""+name+"\",\"strSport\":\"Soccer\",\"idLeague\":\""+league+"\"}";
    }

    @Test public void theBundledListHasTheTopThreeDivisionsWholeAndMarkedWithItsSeason()throws Exception{
        Context app=RuntimeEnvironment.getApplication();
        JSONObject b=TeamList.bundle(app);
        assertTrue("marked with its season: "+TeamList.bundleSeason(b),TeamList.bundleSeason(b).matches("20[0-9]{2}-[0-9]{2}"));
        JSONArray leagues=b.getJSONArray("leagues");assertEquals(3,leagues.length());
        int[] sizes={20,24,24};Map<String,String> owner=new HashMap<>();
        for(int i=0;i<3;i++){
            JSONObject l=leagues.getJSONObject(i);SportsDb.League def=SportsDb.league(l.getInt("id"));
            assertNotNull(def);assertEquals("England",def.country);assertEquals(def.sdb,l.getLong("sdb"));
            JSONArray t=l.getJSONArray("teams");assertEquals(def.name,sizes[i],t.length());Set<String> codes=new HashSet<>();
            for(int j=0;j<t.length();j++){
                JSONObject c=t.getJSONObject(j);String name=c.getString("name");
                assertTrue(name,c.getString("short").matches("[A-Z]{3}"));assertTrue("codes unique in a league: "+name,codes.add(c.getString("short")));
                assertNotEquals(name,0,SportsDb.colour(c.getString("c1")));assertNotEquals(name,0,SportsDb.colour(c.getString("c2")));
                assertFalse("names and colours only, no pictures: "+name,c.toString().matches("(?s).*(http|png|svg|badge|crest).*"));
                List<String> names=new ArrayList<>();names.add(name);JSONArray a=c.getJSONArray("aliases");for(int k=0;k<a.length();k++)names.add(a.getString(k));
                for(String n:names){String prev=owner.put(TeamList.key(n),name);assertTrue("\""+n+"\" names one club only ("+prev+", "+name+")",prev==null||prev.equals(name));}
            }
        }
        // A free-key reply of 10 per league, as the TCL saw: merged, every league is whole.
        Map<Integer,String[]> capped=new HashMap<>();
        capped.put(1,new String[]{"Arsenal","Aston Villa","AFC Bournemouth","Brentford","Brighton and Hove Albion","Burnley","Chelsea","Crystal Palace","Everton","Fulham"});
        capped.put(2,new String[]{"Birmingham City","Blackburn Rovers","Bristol City","Charlton Athletic","Coventry City","Derby County","Hull City","Ipswich Town","Leicester City","Middlesbrough"});
        capped.put(3,new String[]{"AFC Wimbledon","Barnsley","Blackpool","Bolton Wanderers","Bradford City","Burton Albion","Cardiff City","Doncaster Rovers","Exeter City","Huddersfield Town"});
        JSONArray cl=new JSONArray();long id=140000;
        for(int lid=1;lid<=3;lid++){StringBuilder r=new StringBuilder("{\"teams\":[");String[] n=capped.get(lid);
            for(int j=0;j<n.length;j++)r.append(j>0?",":"").append(team(id++,n[j],SportsDb.league(lid).sdb));
            JSONObject entry=new JSONObject().put("id",lid).put("name",SportsDb.league(lid).name).put("country","England").put("sdb",SportsDb.league(lid).sdb);
            JSONArray teams=new JSONArray();for(SportsDb.TeamEntry e:SportsDb.teams(new JSONObject(r+"]}")))teams.put(new JSONObject().put("id",e.id).put("name",e.name).put("code",e.code));
            assertEquals(10,teams.length());cl.put(entry.put("teams",teams));}
        JSONObject merged=TeamList.merged(new JSONObject().put("leagues",cl),b);
        List<JSONObject> shown=TeamList.leagues(merged);
        for(int i=0;i<3;i++){List<TeamList.Entry> e=TeamList.teams(shown.get(i));assertEquals(shown.get(i).optString("name"),sizes[i],e.size());
            int numbered=0;for(TeamList.Entry x:e)if(x.id>0)numbered++;assertEquals("TheSportsDB's ten keep their numbers",10,numbered);}
        Set<String> pl=new HashSet<>();for(TeamList.Entry e:TeamList.teams(shown.get(0)))pl.add(e.name);
        assertTrue("TheSportsDB's spelling wins, no second Brighton",pl.contains("Brighton and Hove Albion")&&!pl.contains("Brighton & Hove Albion"));
        assertTrue(pl.contains("AFC Bournemouth")&&!pl.contains("Bournemouth"));
        assertTrue("from the bundle: a club the free key left out",pl.contains("Wolverhampton Wanderers"));
        assertEquals("Tottenham Hotspur",TeamList.search(merged,"spurs",3).get(0).name);
        assertEquals("Manchester United",TeamList.search(merged,"man utd",3).get(0).name);
        assertEquals("Wolverhampton Wanderers",TeamList.search(merged,"wolves",3).get(0).name);
        // The same through the app: the bundled leagues are there before anything is read.
        assertEquals(3,TeamList.leagues(FixtureFeed.teams(app)).size());
    }
}
