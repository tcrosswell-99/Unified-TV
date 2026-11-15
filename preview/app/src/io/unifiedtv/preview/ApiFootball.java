package io.unifiedtv.preview;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/**
 * Football fixtures from API-Football: OPTIONAL "more detail" (owner decision OD-22, 25 October 2026). The default source
 * is TheSportsDB (SportsDb), which needs no account; API-Football is used only when the household adds its own key from a
 * phone (PhoneService, Settings > Smart Home > Football) and its team has an API-Football number (TheSportsDB's team
 * record names it, or the team was picked from API-Football's list on an earlier install). When API-Football refuses or
 * can't be reached, fixtures come from TheSportsDB instead (FixtureFeed). The key is kept in the Vault, sent only in the
 * x-apisports-key header to the one fixed origin, and never logged or shown again.
 *
 * API v3 (documentation: https://www.api-football.com/documentation-v3, base https://v3.football.api-sports.io). The
 * shapes below were checked against real responses recorded by open-source clients (github.com/syurchen93/
 * api-football-client, test/response/*.json, and github.com/0ffsidecompass/api-football-go-client, MIT), because the
 * official pages could not be reached when this was written; confirm them against the owner's first live replies. Every reply is {"get","parameters","errors","results","paging",
 * "response":[...]}; "errors" is [] when fine and an object such as {"requests": "..."} or {"token": "..."} otherwise,
 * even with HTTP 200.
 *   GET /fixtures?team=47&next=10                          response[]: fixture{id,date,timestamp,timezone,status{long,short,
 *        elapsed},venue}, league{id,name,country,season,round}, teams{home{id,name},away{id,name}}, goals, score.
 * Logos, goals and score are never read: the badge is generated and no result is ever shown.
 *
 * Request budget (owner): at most 100 a day, counted per UTC day and kept across restarts. Fixtures refresh daily, hourly
 * on a matchday and every 15 minutes within three hours of kick-off. The team list comes from TheSportsDB, so API-Football
 * is never asked for leagues or teams.
 */
final class ApiFootball {
    private ApiFootball(){}
    static final String BASE="https://v3.football.api-sports.io",HEADER="x-apisports-key",VAULT="api-football",SOURCE="api-football";
    static final int DAILY_LIMIT=100,NEXT=10;
    static final long DAY_MS=86_400_000L,HOUR_MS=3_600_000L,QUARTER_MS=900_000L,NEAR_MS=3*HOUR_MS;

    /** A refusal the API reported, by its error key only ("requests", "token", ...); never the key or the message. */
    static final class Refused extends IOException {final String reason;Refused(String reason){super("API-Football refused the request: "+reason);this.reason=reason;}
        boolean quota(){return reason.contains("requests")||reason.contains("rateLimit")||reason.equals("429");}
        boolean key(){return reason.contains("token")||reason.contains("key")||reason.equals("401")||reason.equals("403");}
        /** The API's words named a proxy or VPN (only this marker is kept, never its message). */
        boolean proxy(){return reason.contains(PROXY);}
        /**
         * Refused because of where the request came from, not the key (owner, 25 October 2026: API-Football's dashboard
         * says "It seems that you are using a Proxy or a VPN"): proxy or VPN wording, or an "access" error or HTTP 403
         * while this TV's own traffic goes through a VPN.
         */
        boolean vpn(boolean vpnActive){return proxy()||(vpnActive&&(reason.contains("access")||reason.equals("403")));}}
    static final String PROXY="proxy";
    static final String VPN_WORDS="API-Football refused the request because NordVPN is on. Fixtures update when NordVPN is off, or allow API-Football in NordVPN's split tunnelling if it offers it.";
    /** Whether an error's wording points at a proxy or VPN; the wording itself is never kept or shown. */
    static boolean proxyWords(String text){String t=text==null?"":text.toLowerCase(Locale.ROOT);return t.contains("proxy")||t.contains("vpn");}
    /** Throws Refused when the body carries errors; otherwise the response array (possibly empty). */
    static JSONArray response(JSONObject body)throws Refused{
        if(body==null)throw new Refused("empty");
        Object errors=body.opt("errors");
        if(errors instanceof JSONObject&&((JSONObject)errors).length()>0){List<String> keys=new ArrayList<>();boolean proxy=false;
            for(Iterator<String> it=((JSONObject)errors).keys();it.hasNext();){String k=it.next();keys.add(k);proxy|=proxyWords(k)||proxyWords(((JSONObject)errors).optString(k));}
            Collections.sort(keys);if(proxy)keys.add(PROXY);throw new Refused(String.join(",",keys));}
        if(errors instanceof JSONArray&&((JSONArray)errors).length()>0)throw new Refused(proxyWords(errors.toString())?"errors,"+PROXY:"errors");
        JSONArray r=body.optJSONArray("response");return r==null?new JSONArray():r;
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
                status(st==null?"":st.optString("short")),SOURCE,now,home.optString("name"),away.optString("name"),""));
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
        /** Room for one more today. */
        boolean allows(){return used<DAILY_LIMIT;}
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

    // --- checking a key (phone setup)
    /** The account endpoint: API-Football documents that it doesn't count against the daily quota. */
    static final String STATUS="/status";
    /**
     * A /status reply: {"errors":[],"response":{"account":{...},"subscription":{"plan","end","active"},"requests":
     * {"current","limit_day"}}}. Returns {requests used today, daily limit}; Refused for errors or an inactive account.
     * Account names and email addresses in the reply are never read.
     */
    static int[] account(JSONObject body)throws Refused{
        response(body);
        JSONObject r=body.optJSONObject("response");if(r==null)throw new Refused("empty");
        JSONObject sub=r.optJSONObject("subscription");if(sub!=null&&sub.has("active")&&!sub.optBoolean("active"))throw new Refused("inactive");
        JSONObject req=r.optJSONObject("requests");
        return new int[]{req==null?0:Math.max(0,req.optInt("current")),req==null?DAILY_LIMIT:req.optInt("limit_day",DAILY_LIMIT)};
    }
    /** What a failed check means to the household: a bad key, a busy service, or no connection. */
    static PhoneFlows.Verdict verdict(Exception e){return verdict(e,false);}
    /** {@code vpnActive}: this TV's own traffic goes through a VPN now (VpnGate.routed). */
    static PhoneFlows.Verdict verdict(Exception e,boolean vpnActive){
        if(e==null)return PhoneFlows.Verdict.OK;
        if(e instanceof Refused&&((Refused)e).vpn(vpnActive))return PhoneFlows.Verdict.VPN;
        if(e instanceof Refused){Refused r=(Refused)e;return r.quota()?PhoneFlows.Verdict.BUSY:PhoneFlows.Verdict.REFUSED;}
        return PhoneFlows.Verdict.OFFLINE;
    }

    /** The first 4 KB of an error body, only to look for proxy or VPN wording; never kept or shown. */
    static String start(InputStream in){
        if(in==null)return "";
        try(InputStream i=in){byte[] b=new byte[4096];int total=0,n;while(total<b.length&&(n=i.read(b,total,b.length-total))!=-1)total+=n;return new String(b,0,total,StandardCharsets.UTF_8);}catch(IOException e){return "";}
    }
    /** One GET with the household's key: replaced in tests with fake replies, so no test touches the network. */
    interface Http{JSONObject get(String key,String pathAndQuery)throws Exception;}
    static volatile Http http=ApiFootball::get;
    // --- network (the app's plain HttpURLConnection, as LocalApi: fixed origin, timeouts, no redirects, bounded size)
    static final int LIMIT=2*1024*1024;
    /** One GET to the fixed origin. Errors carry an HTTP code or the API's error key, never the key or the body. */
    static JSONObject get(String key,String pathAndQuery)throws Exception{
        if(key==null||key.trim().isEmpty())throw new Refused("token");
        if(!pathAndQuery.equals(STATUS)&&!pathAndQuery.matches("/fixtures\\?team=[0-9]{1,10}&next=[0-9]{1,2}"))throw new IllegalArgumentException("unexpected request");
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+pathAndQuery).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestMethod("GET");c.setRequestProperty(HEADER,key.trim());c.setUseCaches(false);
        try{
            int code=c.getResponseCode();
            if(code==429||code==401||code==403)throw new Refused(code==403&&proxyWords(start(c.getErrorStream()))?code+","+PROXY:String.valueOf(code));
            if(code<200||code>=300)throw new IOException("API-Football answered "+code);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>LIMIT)throw new IOException("Response too large");out.write(b,0,n);}
                return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{c.disconnect();}
    }
}
