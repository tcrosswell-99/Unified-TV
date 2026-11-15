package io.unifiedtv.preview;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * SET-055 event log: a short, local-only history of what this TV showed or noticed (alarms shown, doorbell presses,
 * person toasts and ones held back by quiet hours or Kids, connections made, lost or forgotten, settings restored), so a
 * household can answer "did the TV see the doorbell?". Minimal by design: a kind, a time and a few plain words. Never a
 * camera image, a picture, an address, a token, an entity id or a detector's name. Kept 7 days and at most 200 events,
 * oldest first out; never sent anywhere, excluded from backups and exports, and cleared from Privacy & Data. On unless
 * the household switches it off, which also clears it.
 */
final class EventLog {
    static final String PREFS="unified-tv-events",KEY="events",ON="eventLog";
    static final int MAX=200,WORDS_MAX=80;
    static final long KEEP_MS=7L*24*60*60*1000;
    static final String ALERT="alert",DOORBELL="doorbell",PERSON="person",CONNECTION="connection",SETTINGS="settings";
    private static final Set<String> KINDS=new HashSet<>(Arrays.asList(ALERT,DOORBELL,PERSON,CONNECTION,SETTINGS));

    static final class Event{final String kind,words;final long at;Event(String kind,String words,long at){this.kind=kind;this.words=words;this.at=at;}}

    // --- pure
    /** The log with one event added, old and surplus events dropped. An unknown kind or empty words adds nothing. */
    static List<Event> add(List<Event> log,String kind,String words,long now){
        List<Event> out=trim(log,now);
        if(!KINDS.contains(kind)||words==null||words.trim().isEmpty())return out;
        String w=words.replaceAll("\\s+"," ").trim();if(w.length()>WORDS_MAX)w=w.substring(0,WORDS_MAX);
        out.add(new Event(kind,w,now));
        while(out.size()>MAX)out.remove(0);
        return out;
    }
    /** Only the last 7 days (and nothing dated in the future, which a wrong clock could leave), at most MAX, oldest first. */
    static List<Event> trim(List<Event> log,long now){
        List<Event> out=new ArrayList<>();if(log!=null)for(Event e:log)if(e!=null&&now-e.at<=KEEP_MS&&e.at<=now+60_000)out.add(e);
        out.sort(Comparator.comparingLong(e->e.at));while(out.size()>MAX)out.remove(0);return out;
    }
    static String json(List<Event> log){
        JSONArray a=new JSONArray();
        for(Event e:log)try{a.put(new JSONObject().put("k",e.kind).put("w",e.words).put("t",e.at));}catch(Exception ignored){}
        return a.toString();
    }
    /** A damaged log reads as empty (and is replaced on the next event), never as a crash. */
    static List<Event> parse(String json){
        List<Event> out=new ArrayList<>();if(json==null||json.isEmpty())return out;
        try{JSONArray a=new JSONArray(json);for(int i=0;i<a.length()&&i<MAX*2;i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;
            String k=o.optString("k"),w=o.optString("w");long t=o.optLong("t");if(KINDS.contains(k)&&!w.isEmpty()&&t>0)out.add(new Event(k,w.length()>WORDS_MAX?w.substring(0,WORDS_MAX):w,t));}}
        catch(Exception ignored){return new ArrayList<>();}
        return out;
    }

    // --- Android
    private static SharedPreferences store(Context c){return c.getSharedPreferences(PREFS,0);}
    static boolean on(Context c){try{return c.getSharedPreferences(HouseholdSettings.PREFS,0).getBoolean(ON,true);}catch(RuntimeException e){return true;}}
    /** Adds an event when the log is on. Safe from any thread. */
    static synchronized void record(Context c,String kind,String words){
        if(c==null||!on(c))return;long now=System.currentTimeMillis();
        try{store(c).edit().putString(KEY,json(add(parse(store(c).getString(KEY,"")),kind,words,now))).apply();}catch(RuntimeException ignored){}
    }
    /** Most recent first, for Settings. */
    static synchronized List<Event> recent(Context c){
        List<Event> all;try{all=trim(parse(store(c).getString(KEY,"")),System.currentTimeMillis());}catch(RuntimeException e){all=new ArrayList<>();}
        Collections.reverse(all);return all;
    }
    static synchronized void clear(Context c){store(c).edit().clear().commit();}
}
