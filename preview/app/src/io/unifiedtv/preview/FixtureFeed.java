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
 * Where Home's matchday card gets its fixtures (spec 7; owner, 25 October 2026). The source is chosen by what the
 * household set up: API-Football when a key is saved and a team was picked from its list; otherwise the Home Assistant
 * calendar chosen in household setup (earlier installs keep working this way). Nothing is fetched without either.
 *
 * Kept on this TV (no backup): the fixtures last read and when, the team list, and the day's request count; the change
 * record lives in preferences. Every network read runs off the main thread and is bounded by ApiFootball's budget.
 */
final class FixtureFeed {
    private FixtureFeed(){}
    static final String TEAM="team",TRACK="fixtureTrack",BUDGET="apiFootballBudget";
    private static final Object LOCK=new Object();

    static SharedPreferences prefs(Context c){return c.getSharedPreferences(HouseholdSettings.PREFS,0);}
    static Fixtures.Team team(Context c){return Fixtures.Team.read(prefs(c).getString(TEAM,""));}
    static void saveTeam(Context c,Fixtures.Team t){prefs(c).edit().putString(TEAM,t==null?"":t.json().toString()).remove(TRACK).apply();forget(c,"fixtures.json");}
    static String key(Context c){try{return new Vault(c).read(ApiFootball.VAULT).optString("key");}catch(Exception e){return "";}}
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
    /** Takes one request from today's budget, or false when the day's share is spent. */
    static boolean take(Context c,boolean catalogue){
        synchronized(LOCK){ApiFootball.Budget b=budget(c);if(!b.allows(catalogue))return false;prefs(c).edit().putString(BUDGET,b.spend().write()).commit();return true;}
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
        long now=System.currentTimeMillis();Fixtures.Team team=team(c);
        JSONObject cache=load(c,"fixtures.json");List<Fixtures.Fixture> cached=read(cache);
        Fixtures.FixtureSource source=source(c,team);
        if(source==null)return new ArrayList<>();
        if(!source.id().equals(cache.optString("source")))cached=new ArrayList<>();
        boolean due=source.id().equals("api-football")?ApiFootball.fixturesDue(cache.optLong("fetchedAt"),cached,now,HouseholdSettings.zone(c))
            :now-cache.optLong("fetchedAt")>=ApiFootball.QUARTER_MS||now<cache.optLong("fetchedAt");
        if(!due)return cached;
        try{
            List<Fixtures.Fixture> fresh=source.fetch(Instant.ofEpochMilli(now));
            JSONArray all=new JSONArray();for(Fixtures.Fixture f:fresh)all.put(f.json());
            store(c,"fixtures.json",new JSONObject().put("source",source.id()).put("fetchedAt",now).put("fixtures",all));
            JSONObject track;try{track=new JSONObject(prefs(c).getString(TRACK,"{}"));}catch(Exception e){track=new JSONObject();}
            prefs(c).edit().putString(TRACK,Fixtures.track(track,fresh).toString()).apply();
            return fresh;
        }catch(ApiFootball.Refused r){if(r.quota())exhausted(c);return cached;}
        catch(Exception e){return cached;}
    }
    static List<Fixtures.Fixture> read(JSONObject cache){
        List<Fixtures.Fixture> out=new ArrayList<>();JSONArray a=cache.optJSONArray("fixtures");
        if(a!=null)for(int i=0;i<a.length();i++){Fixtures.Fixture f=Fixtures.Fixture.read(a.optJSONObject(i));if(f!=null)out.add(f);}
        return out;
    }
    static JSONObject track(Context c){try{return new JSONObject(prefs(c).getString(TRACK,"{}"));}catch(Exception e){return new JSONObject();}}
    /** The household's source, or null when none is set up. */
    static Fixtures.FixtureSource source(Context c,Fixtures.Team team){
        if(team!=null&&team.id.startsWith("api:")){String key=key(c);if(!key.isEmpty())return api(c,key,team.id.substring(4));}
        String calendar;JSONObject ha;
        try{calendar=new Vault(c).read("household").optString("calendar");ha=new Vault(c).read("ha");}catch(Exception e){return null;}
        if(!calendar.matches("calendar\\.[a-z0-9_]+"))return null;
        return new Fixtures.HaCalendar(calendar,HouseholdSettings.zone(c),entity->LocalApi.get(ha,"/api/states/"+LocalApi.segment(entity)).getJSONObject("attributes"));
    }
    private static Fixtures.FixtureSource api(Context c,String key,String teamId){
        return new Fixtures.FixtureSource(){
            @Override public String id(){return "api-football";}
            @Override public List<Fixtures.Fixture> fetch(Instant now)throws Exception{
                if(!teamId.matches("[0-9]+"))return new ArrayList<>();
                if(!take(c,false))throw new IOException("Today's API-Football requests are used up");
                return ApiFootball.fixtures(ApiFootball.get(key,"/fixtures?team="+teamId+"&next="+ApiFootball.NEXT),now.toEpochMilli());
            }
        };
    }

    // --- the team list
    interface Progress{void update(JSONObject catalogue,String problem);}
    /** The saved team list (possibly partial). */
    static JSONObject catalogue(Context c){return load(c,"teams.json");}
    /**
     * Worker thread only: continues building the team list, a paced request at a time, until it is complete, today's
     * catalogue share is spent, the API refuses, or the thread is interrupted (the screen closed). A complete list is
     * kept for a week before it is read again.
     */
    static void build(Context c,Progress progress){
        String key=key(c);if(key.isEmpty()){progress.update(catalogue(c),"Add your API-Football key first.");return;}
        JSONObject current=catalogue(c);long now=System.currentTimeMillis();boolean refreshing=current.optLong("fetchedAt")>0;
        if(refreshing&&!ApiFootball.catalogueDue(current.optLong("fetchedAt"),now)){progress.update(current,null);return;}
        // A week-old list is rebuilt beside the old one, which stays in use until the new one is complete.
        String name=refreshing?"teams-next.json":"teams.json";JSONObject cat=load(c,name);
        while(!Thread.currentThread().isInterrupted()){
            String request=ApiFootball.next(cat);
            if(request==null){if(refreshing){store(c,"teams.json",cat);forget(c,"teams-next.json");}progress.update(cat,null);return;}
            JSONObject shown=refreshing?current:cat;
            if(!take(c,true)){progress.update(shown,"Today's share of API-Football requests is used. The list continues tomorrow.");return;}
            try{ApiFootball.apply(cat,request,ApiFootball.get(key,request),System.currentTimeMillis());store(c,name,cat);if(!refreshing)progress.update(cat,null);}
            catch(ApiFootball.Refused r){if(r.quota())exhausted(c);progress.update(shown,r.key()?"API-Football didn't accept the key. Check it in Settings > Smart Home.":"API-Football has no more requests for today. The list continues tomorrow.");return;}
            catch(Exception e){progress.update(shown,"API-Football couldn't be reached. Try again later.");return;}
            try{Thread.sleep(ApiFootball.PACE_MS);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
        }
    }
}
