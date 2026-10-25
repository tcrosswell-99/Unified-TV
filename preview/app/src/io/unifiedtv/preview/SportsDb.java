package io.unifiedtv.preview;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/**
 * Football fixtures from TheSportsDB, the default source (owner decision OD-22, 25 October 2026): no account, no key to
 * add and no phone step. API-Football stays optional (ApiFootball) for a household that adds its own key.
 *
 * TheSportsDB's free public JSON API, version 1: https://www.thesportsdb.com/api/v1/json/{key}/... with the free test
 * key TheSportsDB documents for small and non-commercial projects (FREE_KEY). The key is public, the same for everyone,
 * and identifies nobody; it is not a household secret. Only that one HTTPS host is contacted, and only the league names
 * below and team numbers are sent. Nothing about the household is.
 *   search_all_teams.php?l={league name}   {"teams":[{idTeam,strTeam,strTeamShort,strTeamAlternate,strSport,strLeague,
 *                                            idLeague,idAPIfootball,strColour1,strColour2,...}]} or {"teams":null}
 *   lookupteam.php?id={team}               {"teams":[{...one team...}]}
 *   eventsnext.php?id={team}               {"events":[{idEvent,strEvent,idHomeTeam,idAwayTeam,strHomeTeam,strAwayTeam,
 *                                            strLeague,strTimestamp,dateEvent,strTime,strStatus,strPostponed,...}]}
 *   eventslast.php?id={team}               the same event shape, under "results" (older replies) or "events"
 * UNVERIFIED: written from TheSportsDB's long-documented v1 endpoints and the field names used by open-source clients
 * (github.com/nkl-kst/the-sports-db, MIT), because thesportsdb.com could not be reached when this was written. The free
 * key ("123"; older clients use "3"), the exact league names, the free tier's limits (it may return fewer teams or events
 * than a paid key) and whether eventsnext answers on the free key must be confirmed on the TV's first live reads.
 * Scores, results, crests and pictures in the replies are never read.
 *
 * Polite use: one request at a time, PACE_MS apart, at most a few dozen a week for the team list; fixtures at most four
 * times a day, every 15 minutes only within three hours of a kick-off (fixturesDue).
 */
final class SportsDb {
    private SportsDb(){}
    static final String HOST="www.thesportsdb.com",FREE_KEY="123",BASE="https://"+HOST+"/api/v1/json/"+FREE_KEY+"/",SOURCE="sportsdb";
    static final String ATTRIBUTION="Football fixtures and team names: TheSportsDB (thesportsdb.com), a free, community-edited sports database.";
    static final long QUARTER_MS=900_000L,HOUR_MS=3_600_000L,SIX_HOURS_MS=6*HOUR_MS,NEAR_MS=3*HOUR_MS,DAY_MS=86_400_000L,WEEK_MS=7*DAY_MS;
    /** Pause between team-list requests: well under the free key's published per-minute limit. Tests set it to 0. */
    static volatile long paceMs=2_500;
    /** Most teams kept from one league and most fixtures from one reply: bounded whatever arrives. */
    static final int MAX_TEAMS=60,MAX_EVENTS=20,MAX_TEXT=80;
    static final String[] COUNTRIES={"England","Scotland","Wales","Northern Ireland"};

    /** A UK league as the picker shows it, with the names TheSportsDB may use for it, tried in order. */
    static final class League {
        final int id;final String country,name;final String[] names;
        League(int id,String country,String name,String... names){this.id=id;this.country=country;this.name=name;this.names=names;}
    }
    /** All UK leagues (owner, 25 October 2026): England's top five, Scotland's four, and the Welsh and Northern Irish top flights. */
    static final League[] LEAGUES={
        new League(1,"England","Premier League","English Premier League"),
        new League(2,"England","Championship","English League Championship"),
        new League(3,"England","League One","English League 1"),
        new League(4,"England","League Two","English League 2"),
        new League(5,"England","National League","English National League"),
        new League(6,"Scotland","Premiership","Scottish Premiership","Scottish Premier League"),
        new League(7,"Scotland","Championship","Scottish Championship"),
        new League(8,"Scotland","League One","Scottish League 1","Scottish League One"),
        new League(9,"Scotland","League Two","Scottish League 2","Scottish League Two"),
        new League(10,"Wales","Cymru Premier","Welsh Premier League","Cymru Premier"),
        new League(11,"Northern Ireland","NIFL Premiership","Northern Irish Premiership","NIFL Premiership"),
    };

    /** One GET to TheSportsDB: replaced in tests with fake replies, so no test touches the network. */
    interface Http{JSONObject get(String pathAndQuery)throws Exception;}
    static volatile Http http=SportsDb::fetch;

    // --- requests
    /** Only these shapes are ever sent: a league name from LEAGUES, or a team number. */
    static boolean allowed(String pathAndQuery){
        return pathAndQuery!=null&&pathAndQuery.matches("search_all_teams\\.php\\?l=[A-Za-z0-9%_.-]{1,80}|(lookupteam|eventsnext|eventslast)\\.php\\?id=[0-9]{1,10}");
    }
    static String teamsOf(String leagueName){
        try{return "search_all_teams.php?l="+URLEncoder.encode(leagueName,"UTF-8").replace("+","%20");}catch(UnsupportedEncodingException e){throw new IllegalStateException(e);}
    }
    static String upcoming(String team){return "eventsnext.php?id="+team;}
    static String recent(String team){return "eventslast.php?id="+team;}

    // --- parsing (defensive: anything missing or odd is skipped, never guessed)
    private static String text(JSONObject o,String k){
        if(o==null||o.isNull(k))return "";String s=o.optString(k,"").replaceAll("[\\p{Cntrl}]"," ").trim();
        return s.length()>MAX_TEXT?s.substring(0,MAX_TEXT).trim():s;
    }
    private static long number(JSONObject o,String k){String s=text(o,k);return s.matches("[0-9]{1,10}")?Long.parseLong(s):0;}
    /** "#132257" or "132257" to an opaque colour; 0 when it isn't one. */
    static int colour(String s){
        String h=s==null?"":s.trim().replaceFirst("^#","");
        return h.matches("[0-9A-Fa-f]{6}")?0xFF000000|Integer.parseInt(h,16):0;
    }
    /** One team from a list. */
    static final class TeamEntry {
        final long id,api;final String name,code,also;final int c1,c2;
        TeamEntry(long id,String name,String code,String also,long api,int c1,int c2){this.id=id;this.name=name;this.code=code;this.also=also;this.api=api;this.c1=c1;this.c2=c2;}
    }
    /** The football clubs in a search_all_teams or lookupteam reply, by name; at most MAX_TEAMS. */
    static List<TeamEntry> teams(JSONObject body){
        List<TeamEntry> out=new ArrayList<>();JSONArray a=body==null?null:body.optJSONArray("teams");Set<Long> seen=new HashSet<>();
        if(a!=null)for(int i=0;i<a.length()&&out.size()<MAX_TEAMS;i++){JSONObject t=a.optJSONObject(i);if(t==null)continue;
            long id=number(t,"idTeam");String name=text(t,"strTeam"),sport=text(t,"strSport");
            if(id<=0||name.isEmpty()||(!sport.isEmpty()&&!sport.equalsIgnoreCase("Soccer"))||!seen.add(id))continue;
            String code=text(t,"strTeamShort");if(!code.matches("[A-Za-z0-9]{2,5}"))code="";
            String also=text(t,"strTeamAlternate");
            out.add(new TeamEntry(id,name,code,also,number(t,"idAPIfootball"),colour(text(t,"strColour1")),colour(text(t,"strColour2"))));}
        out.sort(Comparator.comparing(t->t.name.toLowerCase(Locale.ROOT)));return out;
    }

    /**
     * TheSportsDB's status words (or API-style codes) to the card's. Nothing at all means not started; a postponed flag
     * wins; anything unknown stays UNKNOWN (the card still shows the kick-off).
     */
    static Fixtures.Status status(String s,String postponed){
        if("yes".equalsIgnoreCase(postponed==null?"":postponed.trim()))return Fixtures.Status.POSTPONED;
        String v=s==null?"":s.trim().toLowerCase(Locale.ROOT);
        switch(v){
            case "":case "ns":case "not started":case "scheduled":return Fixtures.Status.SCHEDULED;
            case "tbd":case "tbc":case "time to be defined":return Fixtures.Status.TBC;
            case "pst":case "postponed":return Fixtures.Status.POSTPONED;
            case "canc":case "cancelled":case "canceled":case "abd":case "abandoned":case "awd":case "awarded":case "wo":case "walkover":return Fixtures.Status.CANCELLED;
            case "1h":case "ht":case "2h":case "et":case "bt":case "p":case "susp":case "int":case "live":case "in progress":
            case "first half":case "half time":case "halftime":case "second half":case "extra time":case "penalties":return Fixtures.Status.LIVE;
            case "ft":case "aet":case "pen":case "match finished":case "finished":case "after extra time":case "after penalties":return Fixtures.Status.FINISHED;
            default:return Fixtures.Status.UNKNOWN;
        }
    }
    /**
     * The kick-off: strTimestamp (UTC when it has no offset), else dateEvent with strTime (UTC). Null when there is no
     * date. A time of exactly midnight UTC is how undecided kick-offs often appear; UK football never starts then, so it
     * reads as "time to be confirmed" (the caller marks it TBC).
     */
    static Instant kickoff(JSONObject e){
        String ts=text(e,"strTimestamp");
        if(!ts.isEmpty()){
            try{return OffsetDateTime.parse(ts.replace(' ','T')).toInstant();}catch(RuntimeException ignored){}
            try{return LocalDateTime.parse(ts.replace(' ','T')).toInstant(ZoneOffset.UTC);}catch(RuntimeException ignored){}
        }
        String date=text(e,"dateEvent"),time=text(e,"strTime").replaceFirst("([+-]\\d\\d:?\\d\\d|Z)$","");
        try{LocalDate d=LocalDate.parse(date);
            if(time.matches("\\d\\d:\\d\\d(:\\d\\d)?"))return LocalDateTime.of(d,LocalTime.parse(time)).toInstant(ZoneOffset.UTC);
            return d.atStartOfDay().toInstant(ZoneOffset.UTC);}catch(RuntimeException ignored){return null;}
    }
    static boolean undecided(Instant kick){return kick!=null&&kick.atZone(ZoneOffset.UTC).toLocalTime().equals(LocalTime.MIDNIGHT);}
    /** The fixtures in an eventsnext or eventslast reply ("events" or "results"), at most MAX_EVENTS. Scores are never read. */
    static List<Fixtures.Fixture> fixtures(JSONObject body,long now){
        List<Fixtures.Fixture> out=new ArrayList<>();if(body==null)return out;
        JSONArray a=body.optJSONArray("events");if(a==null)a=body.optJSONArray("results");if(a==null)return out;
        Set<Long> seen=new HashSet<>();
        for(int i=0;i<a.length()&&out.size()<MAX_EVENTS;i++){JSONObject e=a.optJSONObject(i);if(e==null)continue;
            long id=number(e,"idEvent"),home=number(e,"idHomeTeam"),away=number(e,"idAwayTeam");
            String sport=text(e,"strSport");if(id<=0||home<=0||away<=0||!seen.add(id)||(!sport.isEmpty()&&!sport.equalsIgnoreCase("Soccer")))continue;
            Instant kick=kickoff(e);Fixtures.Status st=status(text(e,"strStatus"),text(e,"strPostponed"));
            if(st==Fixtures.Status.SCHEDULED&&undecided(kick))st=Fixtures.Status.TBC;
            out.add(new Fixtures.Fixture("sdb:"+id,Arrays.asList("sdb:"+home,"sdb:"+away),text(e,"strLeague"),kick,null,st,SOURCE,now,text(e,"strHomeTeam"),text(e,"strAwayTeam"),""));
        }
        return out;
    }
    /**
     * Upcoming fixtures and the recent ones (eventslast), merged: a match that has started may already have left the
     * "next" list, and the card still needs it until it ends. Only recent fixtures from the last day are kept.
     */
    static List<Fixtures.Fixture> merge(List<Fixtures.Fixture> next,List<Fixtures.Fixture> last,long now){
        Map<String,Fixtures.Fixture> out=new LinkedHashMap<>();
        if(next!=null)for(Fixtures.Fixture f:next)out.put(f.id,f);
        if(last!=null)for(Fixtures.Fixture f:last)if(f.kickoff!=null&&now-f.kickoff.toEpochMilli()<=DAY_MS&&!out.containsKey(f.id))out.put(f.id,f);
        return new ArrayList<>(out.values());
    }
    /** Worker thread only: the team's fixtures, two requests. */
    static List<Fixtures.Fixture> read(String team,long now)throws Exception{
        if(team==null||!team.matches("[0-9]{1,10}"))return new ArrayList<>();
        List<Fixtures.Fixture> next=fixtures(http.get(upcoming(team)),now),last;
        try{last=fixtures(http.get(recent(team)),now);}catch(Exception e){last=Collections.emptyList();}   // recent ones only help during a match
        return merge(next,last,now);
    }

    // --- schedule
    /** How long fixtures are kept: 15 minutes within three hours either side of a kick-off, otherwise six hours. */
    static long interval(List<Fixtures.Fixture> cached,long now){
        if(cached!=null)for(Fixtures.Fixture f:cached)if(f.kickoff!=null&&Math.abs(f.kickoff.toEpochMilli()-now)<=NEAR_MS)return QUARTER_MS;
        return SIX_HOURS_MS;
    }
    static boolean fixturesDue(long fetchedAt,List<Fixtures.Fixture> cached,long now){return fetchedAt<=0||now<fetchedAt||now-fetchedAt>=interval(cached,now);}
    static boolean catalogueDue(long fetchedAt,long now){return fetchedAt<=0||now<fetchedAt||now-fetchedAt>=WEEK_MS;}

    // --- the team list, one league per request so a closed screen never loses progress
    /**
     * {"started": ms, "fetchedAt": ms when complete, "leagues": [{id, name, country, sdb (TheSportsDB's league number),
     * tried (names tried so far), "teams": [{id, name, code, also, api, c1, c2}] once read}]}.
     */
    private static JSONObject entry(JSONObject catalogue,League l)throws Exception{
        JSONArray a=catalogue.optJSONArray("leagues");if(a==null){a=new JSONArray();catalogue.put("leagues",a);}
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&x.optInt("id")==l.id)return x;}
        JSONObject x=new JSONObject().put("id",l.id).put("name",l.name).put("country",l.country).put("tried",0);
        // Kept in LEAGUES order whatever order they were added in.
        JSONArray sorted=new JSONArray();boolean placed=false;
        for(int i=0;i<a.length();i++){JSONObject y=a.optJSONObject(i);if(y==null)continue;if(!placed&&y.optInt("id")>l.id){sorted.put(x);placed=true;}sorted.put(y);}
        if(!placed)sorted.put(x);catalogue.put("leagues",sorted);return x;
    }
    /** The request still to make, or null when every league is read. */
    static String next(JSONObject catalogue){
        try{for(League l:LEAGUES){JSONObject x=entry(catalogue,l);if(!x.has("teams")){int t=Math.min(x.optInt("tried"),l.names.length-1);return teamsOf(l.names[Math.max(0,t)]);}}}catch(Exception ignored){}
        return null;
    }
    /** Records one reply against the request next() gave. An empty reply tries the league's next name, if it has one. */
    static void apply(JSONObject catalogue,String request,JSONObject body,long now)throws Exception{
        if(!catalogue.has("started"))catalogue.put("started",now);
        for(League l:LEAGUES){JSONObject x=entry(catalogue,l);if(x.has("teams"))continue;
            int t=Math.max(0,Math.min(x.optInt("tried"),l.names.length-1));if(!teamsOf(l.names[t]).equals(request))continue;
            List<TeamEntry> found=teams(body);
            if(found.isEmpty()&&t+1<l.names.length){x.put("tried",t+1);break;}
            JSONArray teams=new JSONArray();long league=0;JSONArray raw=body==null?null:body.optJSONArray("teams");
            if(raw!=null&&raw.length()>0)league=number(raw.optJSONObject(0),"idLeague");
            for(TeamEntry e:found){JSONObject o=new JSONObject().put("id",e.id).put("name",e.name).put("code",e.code);
                if(!e.also.isEmpty())o.put("also",e.also);if(e.api>0)o.put("api",e.api);if(e.c1!=0)o.put("c1",e.c1);if(e.c2!=0)o.put("c2",e.c2);teams.put(o);}
            x.put("teams",teams).put("tried",t+1);if(league>0)x.put("sdb",league);
            break;
        }
        if(next(catalogue)==null)catalogue.put("fetchedAt",now);
    }
    /** {leagues read, all leagues}. */
    static int[] progress(JSONObject catalogue){
        int read=0;JSONArray a=catalogue==null?null:catalogue.optJSONArray("leagues");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&x.has("teams"))read++;}
        return new int[]{read,LEAGUES.length};
    }

    // --- network (as ApiFootball: fixed origin, timeouts, no redirects, bounded size, nothing logged)
    static final int LIMIT=4*1024*1024;
    /** One GET to TheSportsDB. Errors carry only an HTTP code. */
    static JSONObject fetch(String pathAndQuery)throws Exception{
        if(!allowed(pathAndQuery))throw new IllegalArgumentException("unexpected request");
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+pathAndQuery).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestMethod("GET");c.setUseCaches(false);
        try{
            int code=c.getResponseCode();
            if(code<200||code>=300)throw new IOException("TheSportsDB answered "+code);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>LIMIT)throw new IOException("Response too large");out.write(b,0,n);}
                String body=new String(out.toByteArray(),StandardCharsets.UTF_8).trim();
                // An empty body or "null" is how some replies say "nothing": treated as no rows.
                return body.isEmpty()||body.equals("null")?new JSONObject():new JSONObject(body);
            }
        }finally{c.disconnect();}
    }
}
