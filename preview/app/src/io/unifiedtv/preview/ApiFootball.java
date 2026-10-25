package io.unifiedtv.preview;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/**
 * Football fixtures from API-Football (owner, 25 October 2026), one FixtureSource behind Fixtures.FixtureSource; the
 * Home Assistant calendar stays as the alternative. The household's own key (Settings > Smart Home > Add a service) is
 * kept in the Vault, sent only in the x-apisports-key header to the one fixed origin, and never logged or shown again.
 *
 * API v3 (documentation: https://www.api-football.com/documentation-v3, base https://v3.football.api-sports.io). The
 * shapes below were checked against real responses recorded by open-source clients (github.com/syurchen93/
 * api-football-client, test/response/*.json, and github.com/0ffsidecompass/api-football-go-client, MIT), because the
 * official pages could not be reached when this was written; confirm them against the owner's first live replies. Every reply is {"get","parameters","errors","results","paging",
 * "response":[...]}; "errors" is [] when fine and an object such as {"requests": "..."} or {"token": "..."} otherwise,
 * even with HTTP 200.
 *   GET /leagues?country=England&current=true             response[]: league{id,name,type}, country{name,code},
 *        seasons[]{year,start,end,current}. Country names: England, Scotland, Wales, Northern-Ireland.
 *   GET /teams?league=39&season=2026                       response[]: team{id,name,code,country,national,logo}, venue{...}
 *   GET /fixtures?team=47&next=10                          response[]: fixture{id,date,timestamp,timezone,status{long,short,
 *        elapsed},venue}, league{id,name,country,season,round}, teams{home{id,name},away{id,name}}, goals, score.
 * Logos, goals and score are never read: the badge is generated and no result is ever shown.
 *
 * Request budget (owner): at most 100 a day, counted per UTC day and kept across restarts. The team list refreshes at
 * most weekly (and spends at most CATALOGUE_SHARE of a day, so fixtures always have room); fixtures refresh daily, hourly
 * on a matchday and every 15 minutes within three hours of kick-off.
 */
final class ApiFootball {
    private ApiFootball(){}
    static final String BASE="https://v3.football.api-sports.io",HEADER="x-apisports-key",VAULT="api-football";
    static final String[] COUNTRIES={"England","Scotland","Wales","Northern-Ireland"};
    static final int DAILY_LIMIT=100,CATALOGUE_SHARE=50,NEXT=10;
    static final long WEEK_MS=7*86_400_000L,DAY_MS=86_400_000L,HOUR_MS=3_600_000L,QUARTER_MS=900_000L,NEAR_MS=3*HOUR_MS;
    /** Pause between catalogue requests: stays under a per-minute limit as low as ten a minute. */
    static final long PACE_MS=6_500;

    /** A refusal the API reported, by its error key only ("requests", "token", ...); never the key or the message. */
    static final class Refused extends IOException {final String reason;Refused(String reason){super("API-Football refused the request: "+reason);this.reason=reason;}
        boolean quota(){return reason.contains("requests")||reason.contains("rateLimit")||reason.equals("429");}
        boolean key(){return reason.contains("token")||reason.contains("key")||reason.equals("401")||reason.equals("403");}}
    /** Throws Refused when the body carries errors; otherwise the response array (possibly empty). */
    static JSONArray response(JSONObject body)throws Refused{
        if(body==null)throw new Refused("empty");
        Object errors=body.opt("errors");
        if(errors instanceof JSONObject&&((JSONObject)errors).length()>0){List<String> keys=new ArrayList<>();for(Iterator<String> it=((JSONObject)errors).keys();it.hasNext();)keys.add(it.next());Collections.sort(keys);throw new Refused(String.join(",",keys));}
        if(errors instanceof JSONArray&&((JSONArray)errors).length()>0)throw new Refused("errors");
        JSONArray r=body.optJSONArray("response");return r==null?new JSONArray():r;
    }

    // --- leagues and teams
    static final class League {final int id,season;final String name,country;League(int id,String name,String country,int season){this.id=id;this.name=name;this.country=country;this.season=season;}}
    /** The country's leagues (type "League", not cups: cup sides play in a league too) with a current season. */
    static List<League> leagues(JSONObject body)throws Refused{
        List<League> out=new ArrayList<>();JSONArray r=response(body);
        for(int i=0;i<r.length();i++){JSONObject e=r.optJSONObject(i);if(e==null)continue;JSONObject l=e.optJSONObject("league"),c=e.optJSONObject("country");JSONArray seasons=e.optJSONArray("seasons");
            if(l==null||l.optInt("id")<=0||!"league".equalsIgnoreCase(l.optString("type","league"))||seasons==null)continue;
            int season=0;for(int s=0;s<seasons.length();s++){JSONObject x=seasons.optJSONObject(s);if(x!=null&&x.optBoolean("current"))season=x.optInt("year");}
            if(season>0)out.add(new League(l.optInt("id"),l.optString("name").trim(),c==null?"":c.optString("name"),season));}
        return out;
    }
    static final class TeamEntry {final int id;final String name,code;TeamEntry(int id,String name,String code){this.id=id;this.name=name;this.code=code;}}
    /** Club teams in a league's season (national sides left out), by display name. */
    static List<TeamEntry> teams(JSONObject body)throws Refused{
        List<TeamEntry> out=new ArrayList<>();JSONArray r=response(body);
        for(int i=0;i<r.length();i++){JSONObject e=r.optJSONObject(i);JSONObject t=e==null?null:e.optJSONObject("team");
            if(t==null||t.optInt("id")<=0||t.optString("name").trim().isEmpty()||t.optBoolean("national"))continue;
            out.add(new TeamEntry(t.optInt("id"),t.optString("name").trim(),t.isNull("code")?"":t.optString("code").trim()));}
        out.sort(Comparator.comparing(t->t.name.toLowerCase(Locale.ROOT)));return out;
    }

    // --- fixtures
    /** API-Football's short status to the card's: NS scheduled; TBD time to be defined; PST, CANC, ABD, AWD, WO off; live and break codes live; FT, AET, PEN finished. */
    static Fixtures.Status status(String s){
        switch(s==null?"":s){
            case "NS":return Fixtures.Status.SCHEDULED;
            case "TBD":return Fixtures.Status.TBC;
            case "PST":return Fixtures.Status.POSTPONED;
            case "CANC":case "ABD":case "AWD":case "WO":return Fixtures.Status.CANCELLED;
            case "1H":case "HT":case "2H":case "ET":case "BT":case "P":case "SUSP":case "INT":case "LIVE":return Fixtures.Status.LIVE;
            case "FT":case "AET":case "PEN":return Fixtures.Status.FINISHED;
            default:return Fixtures.Status.UNKNOWN;
        }
    }
    /** The fixtures in a /fixtures reply. Goals and score are deliberately not read. */
    static List<Fixtures.Fixture> fixtures(JSONObject body,long now)throws Refused{
        List<Fixtures.Fixture> out=new ArrayList<>();JSONArray r=response(body);
        for(int i=0;i<r.length();i++){JSONObject e=r.optJSONObject(i);if(e==null)continue;
            JSONObject f=e.optJSONObject("fixture"),l=e.optJSONObject("league"),t=e.optJSONObject("teams");if(f==null||f.optLong("id")<=0||t==null)continue;
            JSONObject home=t.optJSONObject("home"),away=t.optJSONObject("away");if(home==null||away==null)continue;
            long ts=f.optLong("timestamp");Instant kick=ts>0?Instant.ofEpochSecond(ts):null;
            if(kick==null)try{kick=OffsetDateTime.parse(f.optString("date")).toInstant();}catch(RuntimeException ignored){}
            JSONObject st=f.optJSONObject("status");
            out.add(new Fixtures.Fixture("api:"+f.optLong("id"),Arrays.asList("api:"+home.optInt("id"),"api:"+away.optInt("id")),l==null?"":l.optString("name"),kick,null,
                status(st==null?"":st.optString("short")),"api-football",now,home.optString("name"),away.optString("name"),""));
        }
        return out;
    }

    // --- request budget
    /** Requests spent on one UTC day. Immutable: spend() and on() return a new one. */
    static final class Budget {
        final long day;final int used;
        Budget(long day,int used){this.day=day;this.used=Math.max(0,used);}
        static long utcDay(long now){return Math.floorDiv(now,DAY_MS);}
        static Budget read(String saved,long now){
            try{String[] p=saved.split(":");Budget b=new Budget(Long.parseLong(p[0]),Integer.parseInt(p[1]));return b.on(now);}catch(RuntimeException e){return new Budget(utcDay(now),0);}
        }
        String write(){return day+":"+used;}
        /** A new UTC day starts at zero. */
        Budget on(long now){long d=utcDay(now);return d==day?this:new Budget(d,0);}
        /** Room for one more: fixtures may use the whole day; the team list at most CATALOGUE_SHARE of it. */
        boolean allows(boolean catalogue){return used<(catalogue?CATALOGUE_SHARE:DAILY_LIMIT);}
        Budget spend(){return new Budget(day,used+1);}
        /** The API said the day's quota is gone: nothing more today. */
        Budget exhausted(){return new Budget(day,DAILY_LIMIT);}
        int left(){return Math.max(0,DAILY_LIMIT-used);}
    }

    // --- schedule
    /**
     * How long fixtures may be kept before the next read: 15 minutes within three hours either side of a kick-off, one
     * hour on a matchday (household clock), otherwise a day.
     */
    static long interval(List<Fixtures.Fixture> cached,long now,ZoneId zone){
        long result=DAY_MS;LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if(cached!=null)for(Fixtures.Fixture f:cached){if(f.kickoff==null)continue;long k=f.kickoff.toEpochMilli();
            if(Math.abs(k-now)<=NEAR_MS)return QUARTER_MS;
            if(f.kickoff.atZone(zone).toLocalDate().equals(today))result=Math.min(result,HOUR_MS);}
        return result;
    }
    static boolean fixturesDue(long fetchedAt,List<Fixtures.Fixture> cached,long now,ZoneId zone){return fetchedAt<=0||now<fetchedAt||now-fetchedAt>=interval(cached,now,zone);}
    static boolean catalogueDue(long fetchedAt,long now){return fetchedAt<=0||now<fetchedAt||now-fetchedAt>=WEEK_MS;}

    // --- the team catalogue, built in steps so a day's budget (or a closed screen) never loses progress
    /**
     * {"started": ms, "fetchedAt": ms when complete, "countries": [names done], "leagues": [{id, name, country, season,
     * "teams": [{id, name, code}] once read}]}. next() is the request still to make, or null when complete.
     */
    static String next(JSONObject catalogue){
        JSONArray done=catalogue.optJSONArray("countries");Set<String> have=new HashSet<>();if(done!=null)for(int i=0;i<done.length();i++)have.add(done.optString(i));
        for(String c:COUNTRIES)if(!have.contains(c))return "/leagues?country="+c+"&current=true";
        JSONArray l=catalogue.optJSONArray("leagues");
        if(l!=null)for(int i=0;i<l.length();i++){JSONObject x=l.optJSONObject(i);if(x!=null&&!x.has("teams"))return "/teams?league="+x.optInt("id")+"&season="+x.optInt("season");}
        return null;
    }
    /** Records one reply against the request next() gave. */
    static void apply(JSONObject catalogue,String request,JSONObject body,long now)throws Exception{
        if(!catalogue.has("started"))catalogue.put("started",now);
        if(request.startsWith("/leagues?country=")){
            String country=request.substring("/leagues?country=".length(),request.indexOf('&'));
            JSONArray leagues=catalogue.optJSONArray("leagues");if(leagues==null){leagues=new JSONArray();catalogue.put("leagues",leagues);}
            for(League l:leagues(body))leagues.put(new JSONObject().put("id",l.id).put("name",l.name).put("country",l.country.isEmpty()?country:l.country).put("season",l.season));
            JSONArray done=catalogue.optJSONArray("countries");if(done==null){done=new JSONArray();catalogue.put("countries",done);}done.put(country);
        }else if(request.startsWith("/teams?league=")){
            int id=Integer.parseInt(request.substring("/teams?league=".length(),request.indexOf('&')));
            JSONArray l=catalogue.optJSONArray("leagues");JSONArray teams=new JSONArray();
            for(TeamEntry t:teams(body))teams.put(new JSONObject().put("id",t.id).put("name",t.name).put("code",t.code));
            if(l!=null)for(int i=0;i<l.length();i++){JSONObject x=l.optJSONObject(i);if(x!=null&&x.optInt("id")==id&&!x.has("teams"))x.put("teams",teams);}
        }
        if(next(catalogue)==null)catalogue.put("fetchedAt",now);
    }
    /** Progress in words: "12 of 31 leagues read". */
    static int[] progress(JSONObject catalogue){
        JSONArray l=catalogue.optJSONArray("leagues");int read=0,all=l==null?0:l.length();
        if(l!=null)for(int i=0;i<l.length();i++){JSONObject x=l.optJSONObject(i);if(x!=null&&x.has("teams"))read++;}
        return new int[]{read,all};
    }

    // --- network (the app's plain HttpURLConnection, as LocalApi: fixed origin, timeouts, no redirects, bounded size)
    static final int LIMIT=2*1024*1024;
    /** One GET to the fixed origin. Errors carry an HTTP code or the API's error key, never the key or the body. */
    static JSONObject get(String key,String pathAndQuery)throws Exception{
        if(key==null||key.trim().isEmpty())throw new Refused("token");
        if(!pathAndQuery.matches("/(leagues|teams|fixtures)\\?[A-Za-z0-9=&_.-]+"))throw new IllegalArgumentException("unexpected request");
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+pathAndQuery).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestMethod("GET");c.setRequestProperty(HEADER,key.trim());c.setUseCaches(false);
        try{
            int code=c.getResponseCode();if(code==429||code==401||code==403)throw new Refused(String.valueOf(code));
            if(code<200||code>=300)throw new IOException("API-Football answered "+code);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>LIMIT)throw new IOException("Response too large");out.write(b,0,n);}
                return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{c.disconnect();}
    }
}
