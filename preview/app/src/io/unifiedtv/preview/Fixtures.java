package io.unifiedtv.preview;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.*;
import java.util.*;

/**
 * The matchday card's model, sport-agnostic (owner, 25 October 2026: "Your team" is a household setting, none by default).
 * Pure: sports, teams, fixtures, the FixtureSource adapters' contract, the Home Assistant calendar adapter, and the change
 * detection that notices a fixture's date or kick-off moving. Scores are never part of the model: no source's goals or
 * result fields are read at all.
 */
final class Fixtures {
    private Fixtures(){}

    /** A sport: its word for the start and how long a match lasts when the source gives no end. */
    enum Sport {
        FOOTBALL("Football","Kick-off",120),RUGBY("Rugby","Kick-off",100),CRICKET("Cricket","Starts",480),OTHER("Sport","Starts",120);
        final String label,startWord;final int minutes;
        Sport(String label,String startWord,int minutes){this.label=label;this.startWord=startWord;this.minutes=minutes;}
        static Sport of(String name){for(Sport s:values())if(s.name().equalsIgnoreCase(name))return s;return FOOTBALL;}
    }

    /**
     * The household's team. The badge is generated (Unified TV draws the short name on the two colours); no crest is
     * bundled or downloaded. Aliases are other names the household's calendar uses ("Rovers"), shown as the display name.
     */
    static final class Team {
        final String id,displayName,shortName;final int primary,secondary;final Sport sport;final List<String> aliases;
        Team(String id,String displayName,String shortName,int primary,int secondary,Sport sport,List<String> aliases){
            this.id=id==null?"":id;this.displayName=displayName==null?"":displayName.trim();
            this.shortName=badgeText(shortName,this.displayName);this.primary=primary;this.secondary=secondary;
            this.sport=sport==null?Sport.FOOTBALL:sport;this.aliases=aliases==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(aliases));
        }
        /** True when the text mentions this team by its name or an alias, as whole words. */
        boolean named(String text){
            if(text==null||displayName.isEmpty())return false;
            List<String> all=new ArrayList<>(aliases);all.add(displayName);
            for(String n:all)if(!n.trim().isEmpty()&&text.toLowerCase(Locale.ROOT).matches("(?s).*(?<![\\p{L}\\p{N}])"+java.util.regex.Pattern.quote(n.trim().toLowerCase(Locale.ROOT))+"(?![\\p{L}\\p{N}]).*"))return true;
            return false;
        }
        JSONObject json(){
            try{JSONObject o=new JSONObject().put("id",id).put("name",displayName).put("short",shortName).put("c1",primary).put("c2",secondary).put("sport",sport.name());
                o.put("aliases",new JSONArray(aliases));return o;}catch(Exception e){return new JSONObject();}
        }
        /** A saved team, or null for none (the default) or anything unreadable. */
        static Team read(String saved){
            if(saved==null||saved.trim().isEmpty())return null;
            try{JSONObject o=new JSONObject(saved);if(o.optString("name").trim().isEmpty())return null;
                List<String> aliases=new ArrayList<>();JSONArray a=o.optJSONArray("aliases");if(a!=null)for(int i=0;i<a.length();i++)if(!a.optString(i).trim().isEmpty())aliases.add(a.optString(i).trim());
                return new Team(o.optString("id"),o.optString("name"),o.optString("short"),o.optInt("c1",COLOURS[0]),o.optInt("c2",COLOURS[1]),Sport.of(o.optString("sport")),aliases);
            }catch(Exception e){return null;}
        }
    }
    /** Two to four capital letters for the generated badge: the source's short code, else the name's initials or start. */
    static String badgeText(String code,String name){
        String c=code==null?"":code.replaceAll("[^\\p{L}\\p{N}]","").toUpperCase(Locale.ROOT);
        if(c.length()>=2)return c.length()>4?c.substring(0,4):c;
        String n=name==null?"":name.trim();if(n.isEmpty())return "";
        String[] words=n.split("[\\s-]+");
        if(words.length>=2){StringBuilder b=new StringBuilder();for(String w:words)if(!w.isEmpty()&&b.length()<3)b.append(w.charAt(0));return b.toString().toUpperCase(Locale.ROOT);}
        String w=n.replaceAll("[^\\p{L}\\p{N}]","");return (w.length()>3?w.substring(0,3):w).toUpperCase(Locale.ROOT);
    }
    /** Badge colours offered in Settings, with their names. */
    static final int[] COLOURS={0xFF132257,0xFFFFFFFF,0xFFC8102E,0xFF034694,0xFF6CABDD,0xFFFDB913,0xFF00A650,0xFF111111,0xFF7A263A,0xFFF47A20};
    static final String[] COLOUR_NAMES={"Navy","White","Red","Blue","Sky blue","Yellow","Green","Black","Claret","Orange"};
    static String colourName(int c){for(int i=0;i<COLOURS.length;i++)if(COLOURS[i]==c)return COLOUR_NAMES[i];return "Custom";}
    /** Black or white lettering, whichever reads better on the colour (relative luminance). */
    static int ink(int colour){
        double r=lin(((colour>>16)&255)/255.0),g=lin(((colour>>8)&255)/255.0),b=lin((colour&255)/255.0);
        double l=.2126*r+.7152*g+.0722*b;return (1.05/(l+.05))>=((l+.05)/.05)?0xFFFFFFFF:0xFF111111;
    }
    private static double lin(double v){return v<=.03928?v/12.92:Math.pow((v+.055)/1.055,2.4);}

    enum Status {SCHEDULED,TBC,LIVE,FINISHED,POSTPONED,CANCELLED,UNKNOWN}

    /**
     * One fixture as a source reported it. "home"/"away" are the teams' names where the source has them; "summary" is a
     * calendar's own text otherwise. finish may be null (the sport's usual length applies).
     */
    static final class Fixture {
        final String id,competition,source,home,away,summary;final List<String> teamIds;final Instant kickoff,finish;final Status status;final long lastChanged;
        Fixture(String id,List<String> teamIds,String competition,Instant kickoff,Instant finish,Status status,String source,long lastChanged,String home,String away,String summary){
            this.id=id==null?"":id;this.teamIds=teamIds==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(teamIds));
            this.competition=competition==null?"":competition;this.kickoff=kickoff;this.finish=finish;this.status=status==null?Status.UNKNOWN:status;
            this.source=source==null?"":source;this.lastChanged=lastChanged;this.home=home==null?"":home;this.away=away==null?"":away;this.summary=summary==null?"":summary;
        }
        Instant end(Sport sport){return finish!=null?finish:kickoff.plusSeconds(60L*(sport==null?Sport.FOOTBALL:sport).minutes);}
        /** No card at all: not yet timed, postponed or called off. */
        boolean unsettled(){return status==Status.TBC||status==Status.POSTPONED||status==Status.CANCELLED||kickoff==null;}
        JSONObject json(){
            try{return new JSONObject().put("id",id).put("teams",new JSONArray(teamIds)).put("competition",competition).put("kickoff",kickoff==null?0:kickoff.getEpochSecond())
                .put("finish",finish==null?0:finish.getEpochSecond()).put("status",status.name()).put("source",source).put("changed",lastChanged).put("home",home).put("away",away).put("summary",summary);}
            catch(Exception e){return new JSONObject();}
        }
        static Fixture read(JSONObject o){
            if(o==null)return null;List<String> teams=new ArrayList<>();JSONArray t=o.optJSONArray("teams");if(t!=null)for(int i=0;i<t.length();i++)teams.add(t.optString(i));
            long k=o.optLong("kickoff"),f=o.optLong("finish");Status s;try{s=Status.valueOf(o.optString("status"));}catch(RuntimeException e){s=Status.UNKNOWN;}
            return new Fixture(o.optString("id"),teams,o.optString("competition"),k>0?Instant.ofEpochSecond(k):null,f>0?Instant.ofEpochSecond(f):null,s,o.optString("source"),o.optLong("changed"),o.optString("home"),o.optString("away"),o.optString("summary"));
        }
    }

    /** Where fixtures come from: the Home Assistant calendar now; an API (ApiFootball) or an ICS feed are further adapters. */
    interface FixtureSource {
        String id();
        /** The source's upcoming and current fixtures for the household's team; empty when there are none. */
        List<Fixture> fetch(Instant now)throws Exception;
    }

    /** The household's own Home Assistant calendar entity: its next event is the fixture. Its text may hold a score; the card never shows it. */
    static final class HaCalendar implements FixtureSource {
        interface Reader{JSONObject attributes(String entity)throws Exception;}
        private final String entity;private final Reader reader;private final ZoneId zone;
        /** zone: the household's clock, for calendar times written without an offset. */
        HaCalendar(String entity,ZoneId zone,Reader reader){this.entity=entity;this.zone=zone;this.reader=reader;}
        @Override public String id(){return "calendar";}
        @Override public List<Fixture> fetch(Instant now)throws Exception{
            List<Fixture> out=new ArrayList<>();if(entity==null||!entity.matches("calendar\\.[a-z0-9_]+"))return out;
            JSONObject a=reader.attributes(entity);Fixture f=fixture(entity,a.optString("message"),a.optString("start_time"),a.optString("end_time"),now.toEpochMilli(),zone);
            if(f!=null)out.add(f);return out;
        }
        /** One calendar event, or null when its times can't be read. Its id follows the text (scores removed), so a moved event keeps it. */
        static Fixture fixture(String entity,String summary,String start,String end,long now,ZoneId zone){
            try{
                Instant kick=parse(start,zone),finish=end==null||end.isEmpty()?null:parse(end,zone);
                String text=summary==null?"":summary;String lower=text.toLowerCase(Locale.ROOT);
                Status status=lower.matches("(?s).*\\b(postponed|cancelled|canceled|called off)\\b.*")?Status.POSTPONED:lower.matches("(?s).*\\b(tbc|tba|tbd)\\b.*")?Status.TBC:Status.SCHEDULED;
                String key=FixturePolicy.clean(text).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
                return new Fixture((entity==null?"calendar":entity)+":"+key,Collections.emptyList(),"",kick,finish,status,"calendar",now,"","",text);
            }catch(Exception e){return null;}
        }
        /** ISO with an offset, or a local time read on the household's clock. */
        static Instant parse(String s,ZoneId zone){
            try{return OffsetDateTime.parse(s.replace(' ','T')).toInstant();}
            catch(Exception e){return LocalDateTime.parse(s.replace(' ','T')).atZone(zone==null?ZoneOffset.UTC:zone).toInstant();}
        }
    }

    /** A fixture whose kick-off moved: from and to. A move to another day is "Moved to <day>", the same day "Kick-off moved to HH:mm". */
    static final class Move {
        final String id;final Instant from,to;
        Move(String id,Instant from,Instant to){this.id=id;this.from=from;this.to=to;}
        boolean otherDay(ZoneId zone){return !from.atZone(zone).toLocalDate().equals(to.atZone(zone).toLocalDate());}
    }
    /**
     * Change detection (pure): the stored kick-off of each fixture id against a fresh read. Only a fixture already known
     * with a settled time can move; a new fixture, or one that was "time to be confirmed", is simply recorded.
     */
    static List<Move> moves(Map<String,Long> before,List<Fixture> fresh){
        List<Move> out=new ArrayList<>();if(before==null||fresh==null)return out;
        for(Fixture f:fresh){if(f.kickoff==null||f.unsettled())continue;Long was=before.get(f.id);
            if(was!=null&&was>0&&was!=f.kickoff.getEpochSecond())out.add(new Move(f.id,Instant.ofEpochSecond(was),f.kickoff));}
        return out;
    }
    /**
     * The tracking record kept between reads (and restarts): each fixture's last settled kick-off ("seen") and the moves
     * still worth saying ("moves", id to {from, to}). A move stays while the fixture still has that kick-off; fixtures the
     * source no longer lists are forgotten. Returns the new record; the old one is not changed.
     */
    static JSONObject track(JSONObject record,List<Fixture> fresh){
        JSONObject out=new JSONObject();
        try{
            JSONObject seen=record==null?null:record.optJSONObject("seen"),oldMoves=record==null?null:record.optJSONObject("moves");
            Map<String,Long> before=new HashMap<>();if(seen!=null)for(Iterator<String> it=seen.keys();it.hasNext();){String k=it.next();before.put(k,seen.optLong(k));}
            JSONObject nextSeen=new JSONObject(),nextMoves=new JSONObject();
            for(Fixture f:fresh){
                if(f.kickoff!=null&&!f.unsettled())nextSeen.put(f.id,f.kickoff.getEpochSecond());
                else if(before.containsKey(f.id))nextSeen.put(f.id,before.get(f.id));   // an unsettled read never erases the last known time
                JSONObject m=oldMoves==null?null:oldMoves.optJSONObject(f.id);
                if(m!=null&&f.kickoff!=null&&m.optLong("to")==f.kickoff.getEpochSecond())nextMoves.put(f.id,m);
            }
            for(Move m:moves(before,fresh))nextMoves.put(m.id,new JSONObject().put("from",m.from.getEpochSecond()).put("to",m.to.getEpochSecond()));
            out.put("seen",nextSeen).put("moves",nextMoves);
        }catch(Exception ignored){}
        return out;
    }
    /** The recorded move for this fixture at its current kick-off, or null. */
    static Move moveOf(JSONObject record,Fixture f){
        JSONObject moves=record==null?null:record.optJSONObject("moves");JSONObject m=moves==null||f==null?null:moves.optJSONObject(f.id);
        if(m==null||f.kickoff==null||m.optLong("to")!=f.kickoff.getEpochSecond())return null;
        return new Move(f.id,Instant.ofEpochSecond(m.optLong("from")),f.kickoff);
    }
}
