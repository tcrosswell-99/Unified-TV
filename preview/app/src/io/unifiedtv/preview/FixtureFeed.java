package io.unifiedtv.preview;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Where Home's matchday card gets its fixtures (spec 7; owner decision OD-22, 25 October 2026). The source follows what the
 * household set up:
 *   a team picked from the list: TheSportsDB (SportsDb), no account or key; when the household also added its own
 *     API-Football key on a phone and the team has an API-Football number, API-Football is asked first and TheSportsDB
 *     answers whenever API-Football refuses or can't be reached;
 *   otherwise the Home Assistant calendar chosen in household setup (earlier installs keep working this way).
 * Nothing is fetched without a team or a calendar.
 *
 * Kept on this TV (no backup): the fixtures last read and when, the team list, and API-Football's daily request count; the
 * change record lives in preferences. Every network read runs off the main thread.
 */
final class FixtureFeed {
    private FixtureFeed(){}
    static final String TEAM="team",TRACK="fixtureTrack",BUDGET="apiFootballBudget";
    private static final Object LOCK=new Object(),READING=new Object();

    static SharedPreferences prefs(Context c){return c.getSharedPreferences(HouseholdSettings.PREFS,0);}
    /** API-Football last refused this TV because of its VPN (ApiFootball.Refused.vpn); cleared by the next good read. */
    static final String VPN_REFUSED="football.vpnRefused";
    static void vpnRefused(Context c,boolean yes){try{if(yes!=prefs(c).getBoolean(VPN_REFUSED,false))prefs(c).edit().putBoolean(VPN_REFUSED,yes).apply();}catch(RuntimeException ignored){}}
    /** The plain words for Settings when API-Football is refusing this TV's VPN, else null. Never the key. */
    static String problem(Context c){try{return prefs(c).getBoolean(VPN_REFUSED,false)?ApiFootball.VPN_WORDS:null;}catch(RuntimeException e){return null;}}
    static Fixtures.Team team(Context c){return Fixtures.Team.read(prefs(c).getString(TEAM,""));}
    static void saveTeam(Context c,Fixtures.Team t){prefs(c).edit().putString(TEAM,t==null?"":t.json().toString()).remove(TRACK).apply();forget(c,"fixtures.json");}
    static String key(Context c){try{return new Vault(c).read(ApiFootball.VAULT).optString("key");}catch(Exception e){return "";}}
    /** A team picked from a list (TheSportsDB, or API-Football's on an earlier install), rather than named for a calendar. */
    static boolean listed(Context c){Fixtures.Team t=team(c);return t!=null&&t.listed();}
    static boolean apiConfigured(Context c){return !key(c).isEmpty();}
    static void saveKey(Context c,String key)throws Exception{new Vault(c).save(ApiFootball.VAULT,new JSONObject().put("key",key.trim()));}
    static void forgetKey(Context c){new Vault(c).forget(ApiFootball.VAULT);forget(c,"fixtures.json");}

    // --- files under no-backup storage
    private static File file(Context c,String name){File d=new File(c.getNoBackupFilesDir(),"football");if(!d.exists())d.mkdirs();return new File(d,name);}
    static JSONObject load(Context c,String name){
        try(InputStream in=new FileInputStream(file(c,name))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
        catch(Exception e){return new JSONObject();}
    }
    /** Atomic: written beside, then renamed over the old copy. */
    static void store(Context c,String name,JSONObject value){
        File target=file(c,name),temp=new File(target.getPath()+".new");
        try(OutputStream out=new FileOutputStream(temp)){out.write(value.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException e){return;}
        if(!temp.renameTo(target))temp.delete();
    }
    static void forget(Context c,String name){file(c,name).delete();}

    // --- budget
    static ApiFootball.Budget budget(Context c){return ApiFootball.Budget.read(prefs(c).getString(BUDGET,""),System.currentTimeMillis());}
    /** Takes one API-Football request from today's budget, or false when the day's requests are spent. */
    static boolean take(Context c){
        synchronized(LOCK){ApiFootball.Budget b=budget(c);if(!b.allows())return false;prefs(c).edit().putString(BUDGET,b.spend().write()).commit();return true;}
    }
    /** API-Football's own count for today (from a key check): the local count never goes below it. */
    static void noteUsed(Context c,int used){
        synchronized(LOCK){ApiFootball.Budget b=budget(c);if(used>b.used)prefs(c).edit().putString(BUDGET,new ApiFootball.Budget(b.day,Math.min(ApiFootball.DAILY_LIMIT,used)).write()).commit();}
    }
    private static void exhausted(Context c){synchronized(LOCK){prefs(c).edit().putString(BUDGET,budget(c).exhausted().write()).commit();}}

    // --- fixtures
    /**
     * Worker thread only. Reads the household's source if due, records moves, and returns the fixtures to show (the cached
     * ones when nothing was due or the read failed: an unreachable source never invents or clears a card by itself).
     */
    static List<Fixtures.Fixture> refresh(Context c){
        // One read at a time (Home's card and Settings' check after a pick may ask together); the second finds it done.
        synchronized(READING){return refreshNow(c);}
    }
    private static List<Fixtures.Fixture> refreshNow(Context c){
        long now=System.currentTimeMillis();Fixtures.Team team=team(c);
        JSONObject cache=load(c,"fixtures.json");List<Fixtures.Fixture> cached=read(cache);
        Fixtures.FixtureSource source=source(c,team);
        if(source==null)return new ArrayList<>();
        if(!source.id().equals(cache.optString("source")))cached=new ArrayList<>();
        if(!due(source.id(),cache.optLong("fetchedAt"),cached,now,HouseholdSettings.zone(c)))return cached;
        try{
            List<Fixtures.Fixture> fresh=source.fetch(Instant.ofEpochMilli(now));
            JSONArray all=new JSONArray();for(Fixtures.Fixture f:fresh)all.put(f.json());
            store(c,"fixtures.json",new JSONObject().put("source",source.id()).put("fetchedAt",now).put("fixtures",all));
            JSONObject track;try{track=new JSONObject(prefs(c).getString(TRACK,"{}"));}catch(Exception e){track=new JSONObject();}
            prefs(c).edit().putString(TRACK,Fixtures.track(track,fresh).toString()).apply();
            return fresh;
        }catch(Exception e){return cached;}
    }
    /** Whether the source's fixtures are due a read: each source keeps its own pace. */
    static boolean due(String source,long fetchedAt,List<Fixtures.Fixture> cached,long now,java.time.ZoneId zone){
        if(ApiFootball.SOURCE.equals(source))return ApiFootball.fixturesDue(fetchedAt,cached,now,zone);
        if(SportsDb.SOURCE.equals(source))return SportsDb.fixturesDue(fetchedAt,cached,now);
        return now-fetchedAt>=ApiFootball.QUARTER_MS||now<fetchedAt;
    }
    /** Where the fixtures last shown came from ("sportsdb", "api-football", "calendar"), or "" before the first read. */
    static String lastSource(Context c){
        JSONObject cache=load(c,"fixtures.json");List<Fixtures.Fixture> f=read(cache);return f.isEmpty()?cache.optString("source"):f.get(0).source;
    }
    static List<Fixtures.Fixture> read(JSONObject cache){
        List<Fixtures.Fixture> out=new ArrayList<>();JSONArray a=cache.optJSONArray("fixtures");
        if(a!=null)for(int i=0;i<a.length();i++){Fixtures.Fixture f=Fixtures.Fixture.read(a.optJSONObject(i));if(f!=null)out.add(f);}
        return out;
    }
    static JSONObject track(Context c){try{return new JSONObject(prefs(c).getString(TRACK,"{}"));}catch(Exception e){return new JSONObject();}}
    /**
     * True when the household's team has been read from its source and nothing was found ("No fixtures found yet for
     * <club>" in Settings): a club TheSportsDB doesn't know by that name, or none of its free replies included it yet.
     */
    static boolean noneFound(Context c){
        Fixtures.Team t=team(c);if(t==null||!t.listed())return false;
        JSONObject cache=load(c,"fixtures.json");return cache.optLong("fetchedAt")>0&&!cache.optString("source").isEmpty()&&read(cache).isEmpty();
    }
    /** The household's source, or null when none is set up. */
    static Fixtures.FixtureSource source(Context c,Fixtures.Team team){
        if(team!=null&&team.listed()){
            String sdb=team.at("sdb"),api=team.at("api"),key=api==null?"":key(c);
            Fixtures.FixtureSource free=sdb==null&&!Fixtures.Team.CLUB.equals(team.id)?null:sportsDb(c,team);
            if(api!=null&&!key.isEmpty())return api(c,key,api,free);
            if(free!=null)return free;
            // An API-Football team whose key was removed: the calendar, if any, as before.
        }
        String calendar;JSONObject ha;
        try{calendar=new Vault(c).read("household").optString("calendar");ha=new Vault(c).read("ha");}catch(Exception e){return null;}
        if(!calendar.matches("calendar\\.[a-z0-9_]+"))return null;
        return new Fixtures.HaCalendar(calendar,HouseholdSettings.zone(c),entity->LocalApi.get(ha,"/api/states/"+LocalApi.segment(entity)).getJSONObject("attributes"));
    }
    /** Each league's rounds as last read (SportsDb.league), so a league is read once per refresh interval. */
    static final String LEAGUES="leagues-sdb.json";
    /**
     * TheSportsDB: no key. Sends the team's number, its league's number and, for a bundled club whose number isn't known
     * yet, its names. What the read learns (the number, the league) is saved with the team, keeping everything else.
     */
    static Fixtures.FixtureSource sportsDb(Context c,Fixtures.Team team){
        return new Fixtures.FixtureSource(){
            @Override public String id(){return SportsDb.SOURCE;}
            @Override public List<Fixtures.Fixture> fetch(Instant now)throws Exception{
                long ms=now.toEpochMilli();String sdb=team.at("sdb"),league=team.league;
                if(league.isEmpty()&&sdb!=null)league=TeamList.leagueOf(catalogue(c),Long.parseLong(sdb));
                List<String> names=new ArrayList<>();names.add(team.displayName);names.addAll(team.aliases);
                JSONObject leagues=load(c,LEAGUES);
                long maxAge=SportsDb.interval(read(load(c,"fixtures.json")),ms);
                SportsDb.Found found=SportsDb.read(sdb,league.isEmpty()?null:league,names,leagues,maxAge,ms);
                store(c,LEAGUES,leagues);
                learn(c,team,found);
                return found.fixtures;
            }
        };
    }
    /** Saves what a read learnt about the team, unless the household chose another team meanwhile. Keeps the change record. */
    static void learn(Context c,Fixtures.Team asked,SportsDb.Found found){
        if(found==null)return;
        synchronized(LOCK){
            Fixtures.Team now=team(c);if(now==null||!now.id.equals(asked.id)||!now.displayName.equals(asked.displayName))return;
            Fixtures.Team learnt=now.learnt(found.team,found.league==null?now.league:found.league);
            if(!learnt.json().toString().equals(now.json().toString()))prefs(c).edit().putString(TEAM,learnt.json().toString()).apply();
        }
    }
    /**
     * API-Football with the household's key, then TheSportsDB (fallback, may be null) whenever API-Football refuses, has no
     * requests left today or can't be reached. A refusal is still recorded (the day's quota, a VPN) for Settings.
     */
    static Fixtures.FixtureSource api(Context c,String key,String teamId,Fixtures.FixtureSource fallback){
        return new Fixtures.FixtureSource(){
            @Override public String id(){return ApiFootball.SOURCE;}
            @Override public List<Fixtures.Fixture> fetch(Instant now)throws Exception{
                try{
                    if(!teamId.matches("[0-9]+"))throw new IOException("No API-Football team");
                    if(!take(c))throw new IOException("Today's API-Football requests are used up");
                    List<Fixtures.Fixture> got=ApiFootball.fixtures(ApiFootball.http.get(key,"/fixtures?team="+teamId+"&next="+ApiFootball.NEXT),now.toEpochMilli());
                    vpnRefused(c,false);return got;
                }catch(Exception e){
                    if(e instanceof ApiFootball.Refused){ApiFootball.Refused r=(ApiFootball.Refused)e;if(r.quota())exhausted(c);vpnRefused(c,r.vpn(VpnGate.routed(c)));}
                    if(fallback==null)throw e;
                    return fallback.fetch(now);
                }
            }
        };
    }

    // --- the team list (TheSportsDB: no key, so it is read as soon as the picker opens)
    interface Progress{void update(JSONObject catalogue,String problem);}
    static final String TEAMS="teams-sdb.json",TEAMS_NEXT="teams-sdb-next.json";
    static final String UNREACHABLE="TheSportsDB couldn't be reached. The list carries on next time you open this page.";
    /** The team list completed with Unified TV's own clubs (TeamList.merged): what the picker shows and searches. */
    static JSONObject teams(Context c){return TeamList.merged(catalogue(c),TeamList.bundle(c));}
    /** The saved team list as read from TheSportsDB (possibly partial). */
    static JSONObject catalogue(Context c){return load(c,TEAMS);}
    /**
     * Worker thread only: continues building the team list, one paced request at a time, until it is complete, TheSportsDB
     * can't be reached, or the thread is interrupted (the screen closed). A complete list is kept for a week; the weekly
     * re-read is built beside it, and the old list stays in use until the new one is complete.
     */
    static void build(Context c,Progress progress){
        // The API-Football team list of earlier installs is no longer used.
        forget(c,"teams.json");forget(c,"teams-next.json");
        JSONObject current=catalogue(c);long now=System.currentTimeMillis();boolean refreshing=current.optLong("fetchedAt")>0;
        if(refreshing&&!SportsDb.catalogueDue(current.optLong("fetchedAt"),now)){progress.update(current,null);return;}
        String name=refreshing?TEAMS_NEXT:TEAMS;JSONObject cat=load(c,name);
        while(!Thread.currentThread().isInterrupted()){
            String request=SportsDb.next(cat);
            if(request==null){if(refreshing){store(c,TEAMS,cat);forget(c,TEAMS_NEXT);}progress.update(cat,null);return;}
            JSONObject shown=refreshing?current:cat;
            JSONObject reply;
            try{reply=SportsDb.http.get(request);}
            catch(SportsDb.Answered e){
                // A league's next event or round refused (not "too many requests"): recorded as empty, the list goes on.
                if(!SportsDb.optional(request)||e.code==429){progress.update(shown,UNREACHABLE);return;}
                reply=new JSONObject();
            }catch(Exception e){progress.update(shown,UNREACHABLE);return;}
            try{SportsDb.apply(cat,request,reply,System.currentTimeMillis());store(c,name,cat);if(!refreshing)progress.update(cat,null);}
            catch(Exception e){progress.update(shown,UNREACHABLE);return;}
            long pace=SportsDb.paceMs;
            if(pace>0)try{Thread.sleep(pace);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
        }
    }
}
