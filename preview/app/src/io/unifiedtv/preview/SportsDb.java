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
 * TheSportsDB's free public JSON API, version 1: https://www.thesportsdb.com/api/v1/json/{key}/... with the free key
 * TheSportsDB documents for small and non-commercial projects (FREE_KEY). The key is public, the same for everyone, and
 * identifies nobody; it is not a household secret. Only that one HTTPS host is contacted. What is sent: league names and
 * numbers, team numbers, a club's name (the household's chosen club, to find its number) and, only when an adult presses
 * "Look up on TheSportsDB" in the picker, the name typed there. Nothing else about the household.
 *   search_all_teams.php?l={league name}   {"teams":[{idTeam,strTeam,strTeamShort,strTeamAlternate,strSport,strLeague,
 *                                            idLeague,idAPIfootball,strColour1,strColour2,...}]} or {"teams":null}
 *   searchteams.php?t={club name}          the same team shape
 *   lookupteam.php?id={team}               {"teams":[{...one team...}]}
 *   eventsnext.php?id={team}               {"events":[{idEvent,strEvent,idHomeTeam,idAwayTeam,strHomeTeam,strAwayTeam,
 *                                            strLeague,intRound,strSeason,strTimestamp,dateEvent,strTime,strStatus,...}]}
 *   eventslast.php?id={team}               the same event shape, under "results" (older replies) or "events"
 *   eventsnextleague.php?id={league}       the league's next events, same shape
 *   eventsround.php?id={league}&r={round}&s={season}   one round of the league, same shape
 *   eventsseason.php?id={league}&s={season}            the season's events, same shape
 *
 * The free key's limits (owner's TCL, build 93, and TheSportsDB's documentation as quoted by client libraries measured
 * on 5 October 2026; thesportsdb.com itself could not be reached from the development machine, so none of this was read
 * from it directly): key "123" ("3" was permanently disabled in 2026); about 30 requests a minute; search_all_teams 10
 * teams a league (seen on the TCL); searchteams 1 team, exact name only; lookupteam 1; eventsnext and eventslast 1 event,
 * home matches only; eventsnextleague 1; eventsseason 5 (documented 15); eventsround, undocumented, the whole round.
 * So the team list is completed from a bundled list (res/raw/football_teams.json: the top three English divisions) and
 * from one round of each league, and a team's fixtures come from its own next match, its league's next two rounds, and
 * the season's schedule as a last resort (read). UNVERIFIED on the TV: every limit above, the league numbers in LEAGUES,
 * intRound/strSeason in eventsnextleague's reply, and whether eventsround keeps answering the free key.
 * Scores, results, crests and pictures in the replies are never read.
 *
 * Polite use: one request at a time, PACE_MS apart, a few dozen a week for the team list; fixtures at most four times a
 * day, every 15 minutes only within three hours of a kick-off (fixturesDue), with each league's rounds kept between reads.
 */
final class SportsDb {
    private SportsDb(){}
    static final String HOST="www.thesportsdb.com",FREE_KEY="123",BASE="https://"+HOST+"/api/v1/json/"+FREE_KEY+"/",SOURCE="sportsdb";
    static final String ATTRIBUTION="Football fixtures and team names: TheSportsDB (thesportsdb.com), a free, community-edited sports database.";
    static final long QUARTER_MS=900_000L,HOUR_MS=3_600_000L,SIX_HOURS_MS=6*HOUR_MS,NEAR_MS=3*HOUR_MS,DAY_MS=86_400_000L,WEEK_MS=7*DAY_MS;
    /** Pause between team-list requests: well under the free key's 30 a minute. Tests set it to 0. */
    static volatile long paceMs=2_500;
    /** Most teams kept from one league, most fixtures from one reply, most a league keeps, most names tried for a club. */
    static final int MAX_TEAMS=60,MAX_EVENTS=20,MAX_LEAGUE_EVENTS=60,MAX_TEXT=80,MAX_NAMES=4;
    /** Rounds of a league read for fixtures: the next one and the one after. */
    static final int ROUNDS=2;
    static final String[] COUNTRIES={"England","Scotland","Wales","Northern Ireland"};

    /**
     * A UK league as the picker shows it, with the names TheSportsDB may use for it, tried in order, and TheSportsDB's
     * number for it when known in advance (0 otherwise; the number in a team-list reply wins).
     */
    static final class League {
        final int id;final long sdb;final String country,name;final String[] names;
        League(int id,long sdb,String country,String name,String... names){this.id=id;this.sdb=sdb;this.country=country;this.name=name;this.names=names;}
    }
    /**
     * All UK leagues (owner, 25 October 2026): England's top five, Scotland's four, and the Welsh and Northern Irish top
     * flights. League numbers are TheSportsDB's long-standing ones for these leagues (UNVERIFIED on the TV).
     */
    static final League[] LEAGUES={
        new League(1,4328,"England","Premier League","English Premier League"),
        new League(2,4329,"England","Championship","English League Championship"),
        new League(3,4396,"England","League One","English League 1"),
        new League(4,4397,"England","League Two","English League 2"),
        new League(5,0,"England","National League","English National League"),
        new League(6,4330,"Scotland","Premiership","Scottish Premiership","Scottish Premier League"),
        new League(7,0,"Scotland","Championship","Scottish Championship"),
        new League(8,0,"Scotland","League One","Scottish League 1","Scottish League One"),
        new League(9,0,"Scotland","League Two","Scottish League 2","Scottish League Two"),
        new League(10,0,"Wales","Cymru Premier","Welsh Premier League","Cymru Premier"),
        new League(11,0,"Northern Ireland","NIFL Premiership","Northern Irish Premiership","NIFL Premiership"),
    };
    static League league(int id){for(League l:LEAGUES)if(l.id==id)return l;return null;}

    /** One GET to TheSportsDB: replaced in tests with fake replies, so no test touches the network. */
    interface Http{JSONObject get(String pathAndQuery)throws Exception;}
    static volatile Http http=SportsDb::fetch;
    /** TheSportsDB answered, but not with a page (a 404, a 429): carries only the HTTP code. */
    static final class Answered extends IOException{
        final int code;Answered(int code){super("TheSportsDB answered "+code);this.code=code;}
    }

    // --- requests
    /** Only these shapes are ever sent: a league name or number, a team number, a round and season, or a club's name. */
    static boolean allowed(String pathAndQuery){
        return pathAndQuery!=null&&pathAndQuery.matches("(search_all_teams\\.php\\?l|searchteams\\.php\\?t)=[A-Za-z0-9%_.-]{1,120}"
            +"|(lookupteam|eventsnext|eventslast|eventsnextleague)\\.php\\?id=[0-9]{1,10}"
            +"|eventsround\\.php\\?id=[0-9]{1,10}&r=[0-9]{1,3}&s=[0-9]{4}(-[0-9]{4})?"
            +"|eventsseason\\.php\\?id=[0-9]{1,10}&s=[0-9]{4}(-[0-9]{4})?");
    }
    private static String encode(String s){
        try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(UnsupportedEncodingException e){throw new IllegalStateException(e);}
    }
    static String teamsOf(String leagueName){return "search_all_teams.php?l="+encode(leagueName);}
    static String upcoming(String team){return "eventsnext.php?id="+team;}
    static String recent(String team){return "eventslast.php?id="+team;}
    static String lookup(String team){return "lookupteam.php?id="+team;}
    static String nextInLeague(long league){return "eventsnextleague.php?id="+league;}
    static String round(long league,int round,String season){return "eventsround.php?id="+league+"&r="+round+"&s="+season;}
    static String season(long league,String season){return "eventsseason.php?id="+league+"&s="+season;}
    /**
     * A club's name as sent to searchteams, or null when nothing usable is left: letters (accents kept), digits, spaces,
     * apostrophes, full stops, hyphens and ampersands; at most 40 characters.
     */
    static String searchName(String name){
        String n=name==null?"":name.replaceAll("[^\\p{L}\\p{N} '.&-]"," ").replaceAll("\\s+"," ").trim();
        if(n.length()>40)n=n.substring(0,40).trim();
        return n.replaceAll("[\\s'.&-]","").isEmpty()?null:n;
    }
    static String searchTeams(String name){String n=searchName(name);return n==null?null:"searchteams.php?t="+encode(n);}
    /** TheSportsDB's football season around a date: "2026-2027" from July, else "2025-2026". */
    static String seasonOf(long now){
        LocalDate d=Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate();int y=d.getMonthValue()>=7?d.getYear():d.getYear()-1;
        return y+"-"+(y+1);
    }

    // --- parsing (defensive: anything missing or odd is skipped, never guessed)
    private static String text(JSONObject o,String k){
        if(o==null||o.isNull(k))return "";String s=o.optString(k,"").replaceAll("[\\p{Cntrl}]"," ").trim();
        return s.length()>MAX_TEXT?s.substring(0,MAX_TEXT).trim():s;
    }
    private static long number(JSONObject o,String k){String s=text(o,k);return s.matches("[0-9]{1,10}")?Long.parseLong(s):0;}
    static boolean digits(String s){return s!=null&&s.matches("[0-9]{1,10}")&&Long.parseLong(s)>0;}
    /** "#132257" or "132257" to an opaque colour; 0 when it isn't one. */
    static int colour(String s){
        String h=s==null?"":s.trim().replaceFirst("^#","");
        return h.matches("[0-9A-Fa-f]{6}")?0xFF000000|Integer.parseInt(h,16):0;
    }
    /** One team from a list: its league as TheSportsDB names and numbers it (0 when not given). */
    static final class TeamEntry {
        final long id,api,leagueId;final String name,code,also,league;final int c1,c2;
        TeamEntry(long id,String name,String code,String also,long api,int c1,int c2){this(id,name,code,also,api,c1,c2,0,"");}
        TeamEntry(long id,String name,String code,String also,long api,int c1,int c2,long leagueId,String league){
            this.id=id;this.name=name;this.code=code;this.also=also;this.api=api;this.c1=c1;this.c2=c2;this.leagueId=leagueId;this.league=league==null?"":league;
        }
    }
    /** The football clubs in a search_all_teams, searchteams or lookupteam reply, by name; at most MAX_TEAMS. */
    static List<TeamEntry> teams(JSONObject body){
        List<TeamEntry> out=new ArrayList<>();JSONArray a=body==null?null:body.optJSONArray("teams");Set<Long> seen=new HashSet<>();
        if(a!=null)for(int i=0;i<a.length()&&out.size()<MAX_TEAMS;i++){JSONObject t=a.optJSONObject(i);if(t==null)continue;
            long id=number(t,"idTeam");String name=text(t,"strTeam"),sport=text(t,"strSport");
            if(id<=0||name.isEmpty()||(!sport.isEmpty()&&!sport.equalsIgnoreCase("Soccer"))||!seen.add(id))continue;
            String code=text(t,"strTeamShort");if(!code.matches("[A-Za-z0-9]{2,5}"))code="";
            String also=text(t,"strTeamAlternate");
            out.add(new TeamEntry(id,name,code,also,number(t,"idAPIfootball"),colour(text(t,"strColour1")),colour(text(t,"strColour2")),number(t,"idLeague"),text(t,"strLeague")));}
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
    private static JSONArray events(JSONObject body){
        if(body==null)return null;JSONArray a=body.optJSONArray("events");return a!=null?a:body.optJSONArray("results");
    }
    /** The fixtures in an events reply ("events" or "results"), at most MAX_EVENTS. Scores are never read. */
    static List<Fixtures.Fixture> fixtures(JSONObject body,long now){
        List<Fixtures.Fixture> out=new ArrayList<>();JSONArray a=events(body);if(a==null)return out;
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
    /** The round and season the first event of a reply belongs to: {round (0 when not given), season ("" when not given)}. */
    static String[] pointer(JSONObject body){
        JSONArray a=events(body);JSONObject e=a==null?null:a.optJSONObject(0);
        long r=number(e,"intRound");String s=text(e,"strSeason");
        return new String[]{r>0&&r<1000?String.valueOf(r):"0",s.matches("[0-9]{4}(-[0-9]{4})?")?s:""};
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
    /** Worker thread only: a numbered team's own fixtures, two requests (on the free key: its next and last home match). */
    static List<Fixtures.Fixture> read(String team,long now)throws Exception{
        if(!digits(team))return new ArrayList<>();
        List<Fixtures.Fixture> next=fixtures(http.get(upcoming(team)),now),last;
        try{last=fixtures(http.get(recent(team)),now);}catch(Exception e){last=Collections.emptyList();}   // recent ones only help during a match
        return merge(next,last,now);
    }

    // --- a team's fixtures on the free key: its own matches, its league's rounds, the season as a last resort
    /** What one read found, and what it learnt about the team on the way (its number; its league, "0" when it has none). */
    static final class Found {
        final List<Fixtures.Fixture> fixtures;final String team,league;
        Found(List<Fixtures.Fixture> fixtures,String team,String league){this.fixtures=fixtures;this.team=team;this.league=league;}
    }
    /**
     * Worker thread only. team: TheSportsDB's number, or null for a club known only by name (the bundled list); league:
     * its league's number, "0" when it is known to have none here, or null when not known yet; names: the club's names
     * (display name first, then other names), used to find its number and to recognise it in a league's rounds; leagues:
     * the per-league record kept between reads (changed in place); maxAge: how long a league's rounds are kept.
     *
     * The fallback chain on the free key: (1) the team's own next and last match (home matches only on the free key);
     * (2) its league's next event, which names the current round and season, then that round and the next (each league
     * read once per maxAge, whichever team asks); (3) when no round is named, the season's schedule (the free key gives
     * only its first few events). Everything found for the team is merged by event; a club with no number is recognised
     * in its league's rounds by name, else looked up by name (searchteams, exact names on the free key, then its other
     * names). Throws only when TheSportsDB answered none of it.
     */
    static Found read(String team,String league,List<String> names,JSONObject leagues,long maxAge,long now)throws Exception{
        String t=digits(team)?team:null,l=league==null||league.isEmpty()?null:league;
        Exception failed=null;boolean answered=false;Map<String,Fixtures.Fixture> found=new LinkedHashMap<>();
        if(t!=null&&l==null){   // a team picked before leagues were kept: one look at its own record
            try{List<TeamEntry> e=teams(http.get(lookup(t)));answered=true;l=!e.isEmpty()&&e.get(0).leagueId>0?String.valueOf(e.get(0).leagueId):"0";}catch(Exception e){failed=e;}
        }
        List<Fixtures.Fixture> schedule=null;
        if(digits(l))try{schedule=league(Long.parseLong(l),leagues,maxAge,now);answered=true;}catch(Exception e){failed=e;}
        if(t==null&&schedule!=null)t=byName(schedule,names);
        if(t==null&&names!=null&&!names.isEmpty())try{
            TeamEntry e=resolve(names,digits(l)?Long.parseLong(l):0);answered=true;
            if(e!=null){t=String.valueOf(e.id);
                if(!digits(l)&&e.leagueId>0){l=String.valueOf(e.leagueId);try{schedule=league(e.leagueId,leagues,maxAge,now);}catch(Exception x){failed=x;}}}
        }catch(Exception e){failed=e;}
        if(t!=null){
            try{for(Fixtures.Fixture f:fixtures(http.get(upcoming(t)),now))found.put(f.id,f);answered=true;}catch(Exception e){failed=e;}
            try{for(Fixtures.Fixture f:fixtures(http.get(recent(t)),now))found.putIfAbsent(f.id,f);}catch(Exception ignored){}   // recent ones only help during a match
        }
        if(schedule!=null)for(Fixtures.Fixture f:schedule)if(involves(f,t,names))found.putIfAbsent(f.id,f);
        if(!answered&&failed!=null)throw failed;
        List<Fixtures.Fixture> out=new ArrayList<>();
        for(Fixtures.Fixture f:found.values())if(f.kickoff==null||now-f.kickoff.toEpochMilli()<=DAY_MS)out.add(f);
        out.sort(Comparator.comparing((Fixtures.Fixture f)->f.kickoff==null?Long.MAX_VALUE:f.kickoff.toEpochMilli()));
        return new Found(out.size()>MAX_EVENTS?new ArrayList<>(out.subList(0,MAX_EVENTS)):out,t,l);
    }
    /** True when the fixture is the team's: by its number, or for a club with no number, by one of its names. */
    static boolean involves(Fixtures.Fixture f,String team,List<String> names){
        if(team!=null)return f.teamIds.contains("sdb:"+team);
        return same(f.home,names)||same(f.away,names);
    }
    private static boolean same(String name,List<String> names){
        String k=TeamList.key(name);if(k.isEmpty()||names==null)return false;
        for(String n:names)if(k.equals(TeamList.key(n)))return true;
        return false;
    }
    /** The club's number from a league's fixtures, recognised by one of its names; null when it isn't there. */
    static String byName(List<Fixtures.Fixture> schedule,List<String> names){
        for(Fixtures.Fixture f:schedule)if(f.teamIds.size()==2){
            if(same(f.home,names))return f.teamIds.get(0).substring(4);
            if(same(f.away,names))return f.teamIds.get(1).substring(4);
        }
        return null;
    }
    /**
     * A club's number by name (searchteams): each name in turn, at most MAX_NAMES requests. A reply counts when its club
     * has the name asked for (or lists it as an other name), or is the only club and in the expected league.
     */
    static TeamEntry resolve(List<String> names,long league)throws Exception{
        int asked=0;Set<String> tried=new HashSet<>();
        // Each spelling is asked as written ("&" and "and" are different names to the free key's exact search), once.
        for(String n:names){String r=searchTeams(n);if(r==null||!tried.add(r))continue;if(++asked>MAX_NAMES)break;
            List<TeamEntry> got=teams(http.get(r));
            for(TeamEntry e:got)if(same(e.name,names)||alsoNamed(e.also,names))return e;
            if(got.size()==1&&league>0&&got.get(0).leagueId==league)return got.get(0);
        }
        return null;
    }
    private static boolean alsoNamed(String also,List<String> names){
        if(also==null||also.isEmpty())return false;
        for(String a:also.split("[,;/]"))if(same(a,names))return true;
        return false;
    }
    /**
     * One league's fixtures for the next two rounds, read at most once per maxAge (kept in leagues under the league's
     * number: {at, season, round, events}). The league's next event names the round and season; with no round named,
     * the season's schedule stands in. A round TheSportsDB refuses is skipped; an unreachable host is thrown.
     */
    static List<Fixtures.Fixture> league(long id,JSONObject leagues,long maxAge,long now)throws Exception{
        String key=String.valueOf(id);JSONObject kept=leagues==null?null:leagues.optJSONObject(key);
        if(kept!=null){long at=kept.optLong("at");if(at>0&&now>=at&&now-at<maxAge)return stored(kept);}
        JSONObject next=http.get(nextInLeague(id));
        Map<String,Fixtures.Fixture> out=new LinkedHashMap<>();for(Fixtures.Fixture f:fixtures(next,now))out.put(f.id,f);
        String[] p=pointer(next);int round=Integer.parseInt(p[0]);String season=p[1].isEmpty()?seasonOf(now):p[1];
        if(round>0)for(int r=round;r<round+ROUNDS;r++){
            try{for(Fixtures.Fixture f:fixtures(http.get(round(id,r,season)),now))out.putIfAbsent(f.id,f);}catch(Answered refused){if(refused.code==429)throw refused;}   // too many requests: try again later, keep nothing half-read
        }
        if(round<=0||out.size()<=1)try{for(Fixtures.Fixture f:fixtures(http.get(season(id,season)),now))out.putIfAbsent(f.id,f);}catch(Answered refused){/* nothing more on the free key */}
        List<Fixtures.Fixture> all=new ArrayList<>(out.values());if(all.size()>MAX_LEAGUE_EVENTS)all=new ArrayList<>(all.subList(0,MAX_LEAGUE_EVENTS));
        if(leagues!=null){JSONArray a=new JSONArray();for(Fixtures.Fixture f:all)a.put(f.json());
            leagues.put(key,new JSONObject().put("at",now).put("season",season).put("round",round).put("events",a));}
        return all;
    }
    private static List<Fixtures.Fixture> stored(JSONObject kept){
        List<Fixtures.Fixture> out=new ArrayList<>();JSONArray a=kept.optJSONArray("events");
        if(a!=null)for(int i=0;i<a.length();i++){Fixtures.Fixture f=Fixtures.Fixture.read(a.optJSONObject(i));if(f!=null)out.add(f);}
        return out;
    }

    // --- schedule
    /** How long fixtures are kept: 15 minutes within three hours either side of a kick-off, otherwise six hours. */
    static long interval(List<Fixtures.Fixture> cached,long now){
        if(cached!=null)for(Fixtures.Fixture f:cached)if(f.kickoff!=null&&Math.abs(f.kickoff.toEpochMilli()-now)<=NEAR_MS)return QUARTER_MS;
        return SIX_HOURS_MS;
    }
    static boolean fixturesDue(long fetchedAt,List<Fixtures.Fixture> cached,long now){return fetchedAt<=0||now<fetchedAt||now-fetchedAt>=interval(cached,now);}
    static boolean catalogueDue(long fetchedAt,long now){return fetchedAt<=0||now<fetchedAt||now-fetchedAt>=WEEK_MS;}

    // --- the team list, one request at a time so a closed screen never loses progress
    /**
     * {"started": ms, "fetchedAt": ms when complete, "leagues": [{id, name, country, sdb (TheSportsDB's league number),
     * tried (names tried so far), "teams": [{id, name, code, also, api, c1, c2}] once read (10 at most on the free key),
     * round and season (from the league's next event; round 0 when none), "more": [{id, name}] (every club in that round)}]}.
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
    private static long sdbOf(JSONObject x,League l){long n=x.optLong("sdb");return n>0?n:l.sdb;}
    /** A league's next request, or null when it is complete: its team list, then its next event, then that round. */
    private static String step(JSONObject x,League l){
        if(!x.has("teams")){int t=Math.min(x.optInt("tried"),l.names.length-1);return teamsOf(l.names[Math.max(0,t)]);}
        long sdb=sdbOf(x,l);if(sdb<=0)return null;
        if(!x.has("round"))return nextInLeague(sdb);
        int r=x.optInt("round");String s=x.optString("season");
        if(r>0&&!x.has("more")&&s.matches("[0-9]{4}(-[0-9]{4})?"))return round(sdb,r,s);
        return null;
    }
    /** The request still to make, or null when every league is read. */
    static String next(JSONObject catalogue){
        try{for(League l:LEAGUES){String s=step(entry(catalogue,l),l);if(s!=null)return s;}}catch(Exception ignored){}
        return null;
    }
    /**
     * Records one reply against the request next() gave. An empty team list tries the league's next name, if it has one;
     * a refused optional step (next event, round) is recorded as an empty reply so the list still completes.
     */
    static void apply(JSONObject catalogue,String request,JSONObject body,long now)throws Exception{
        if(!catalogue.has("started"))catalogue.put("started",now);
        for(League l:LEAGUES){JSONObject x=entry(catalogue,l);String step=step(x,l);if(step==null||!step.equals(request))continue;
            if(!x.has("teams")){
                int t=Math.max(0,Math.min(x.optInt("tried"),l.names.length-1));
                List<TeamEntry> found=teams(body);
                if(found.isEmpty()&&t+1<l.names.length){x.put("tried",t+1);break;}
                JSONArray teams=new JSONArray();long league=0;JSONArray raw=body==null?null:body.optJSONArray("teams");
                if(raw!=null&&raw.length()>0)league=number(raw.optJSONObject(0),"idLeague");
                for(TeamEntry e:found){JSONObject o=new JSONObject().put("id",e.id).put("name",e.name).put("code",e.code);
                    if(!e.also.isEmpty())o.put("also",e.also);if(e.api>0)o.put("api",e.api);if(e.c1!=0)o.put("c1",e.c1);if(e.c2!=0)o.put("c2",e.c2);teams.put(o);}
                x.put("teams",teams).put("tried",t+1);if(league>0)x.put("sdb",league);
            }else if(!x.has("round")){
                String[] p=pointer(body);x.put("round",Integer.parseInt(p[0])).put("season",p[1].isEmpty()?seasonOf(now):p[1]);
            }else{
                JSONArray more=new JSONArray();Set<Long> seen=new HashSet<>();JSONArray a=events(body);
                if(a!=null)for(int i=0;i<a.length()&&more.length()<MAX_TEAMS;i++){JSONObject e=a.optJSONObject(i);if(e==null)continue;
                    String sport=text(e,"strSport");if(!sport.isEmpty()&&!sport.equalsIgnoreCase("Soccer"))continue;
                    for(String side:new String[]{"Home","Away"}){long id=number(e,"id"+side+"Team");String name=text(e,"str"+side+"Team");
                        if(id>0&&!name.isEmpty()&&seen.add(id))more.put(new JSONObject().put("id",id).put("name",name));}}
                x.put("more",more);
            }
            break;
        }
        if(next(catalogue)==null)catalogue.put("fetchedAt",now);
    }
    /** True for the team-list steps that may be refused without stopping the list: a league's next event and round. */
    static boolean optional(String request){return request!=null&&(request.startsWith("eventsnextleague.php")||request.startsWith("eventsround.php"));}
    /** {leagues complete, all leagues}. */
    static int[] progress(JSONObject catalogue){
        int read=0;
        try{if(catalogue!=null)for(League l:LEAGUES){JSONArray a=catalogue.optJSONArray("leagues");if(a==null)break;
            for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&x.optInt("id")==l.id&&x.has("teams")&&step(x,l)==null)read++;}}}catch(RuntimeException ignored){}
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
            if(code<200||code>=300)throw new Answered(code);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>LIMIT)throw new IOException("Response too large");out.write(b,0,n);}
                String body=new String(out.toByteArray(),StandardCharsets.UTF_8).trim();
                // An empty body or "null" is how some replies say "nothing"; so is a plain-text message (an error
                // string for a request it doesn't support): treated as no rows.
                return body.isEmpty()||body.equals("null")||!body.startsWith("{")?new JSONObject():new JSONObject(body);
            }
        }finally{c.disconnect();}
    }
}
