package io.unifiedtv.preview;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Where each film was left, on this TV only (spec 27.2): never shown as Continue Watching and never sent to a server.
 * Keys are one-way hashes of the stream address, so no titles, folders or addresses are stored. Bounded: oldest entries go first.
 *
 * One store per profile (owner, 25 October 2026): Adults and Kids never see each other's places. Places saved before
 * profiles had their own were all an adult's (Kids could not play NAS titles then), so they move to Adults once.
 * Other apps' watch progress is not here and can't be: Android TV's Watch Next rows belong to the system launcher and
 * are not readable by a third-party Home, and no supported API offers them. Nothing to build.
 */
final class ResumeStore {
    static final int MAX=60;
    static final long EXPIRY_MS=180L*24*60*60*1000;
    private final JSONObject entries;

    ResumeStore(String json){
        JSONObject parsed;
        try{parsed=json==null||json.isEmpty()?new JSONObject():new JSONObject(json);}catch(Exception e){parsed=new JSONObject();}
        entries=parsed;
    }
    static String key(String stableId){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(("unified-tv-resume\n"+stableId).getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(int i=0;i<16;i++)out.append(String.format(Locale.ROOT,"%02x",hash[i]&0xff));return out.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    /** Saved position in milliseconds, or 0 when there is none or it has expired. */
    long position(String key,long now){
        JSONObject e=entries.optJSONObject(key);
        if(e==null||now-e.optLong("at")>EXPIRY_MS)return 0;
        return Math.max(0,e.optLong("pos"));
    }
    /** Records a position, or clears it when the film was watched to the end or barely started. */
    void record(String key,long position,long duration,long now){
        if(PlaybackPolicy.resumePoint(position,duration)==0){entries.remove(key);return;}
        try{entries.put(key,new JSONObject().put("pos",position).put("dur",Math.max(0,duration)).put("at",now));}catch(Exception ignored){return;}
        trim();
    }
    void remove(String key){entries.remove(key);}
    int size(){return entries.length();}
    private void trim(){
        while(entries.length()>MAX){
            String oldest=null;long at=Long.MAX_VALUE;
            for(Iterator<String> it=entries.keys();it.hasNext();){String k=it.next();long t=entries.optJSONObject(k)==null?0:entries.optJSONObject(k).optLong("at");if(t<at){at=t;oldest=k;}}
            if(oldest==null)return;entries.remove(oldest);
        }
    }
    String json(){return entries.toString();}

    static final String LEGACY="places";
    static String prefsKey(String profile){return Profiles.CHILD.equals(Profiles.stored(profile))?"places-kids":"places-adults";}
    /** Legacy places joined to the Adults store; the newer of two places for one title wins. Bounded like any store. */
    static String merge(String legacy,String adults){
        ResumeStore into=new ResumeStore(adults),from=new ResumeStore(legacy);
        for(Iterator<String> it=from.entries.keys();it.hasNext();){String k=it.next();JSONObject e=from.entries.optJSONObject(k);if(e==null)continue;
            JSONObject mine=into.entries.optJSONObject(k);if(mine==null||mine.optLong("at")<e.optLong("at"))try{into.entries.put(k,e);}catch(Exception ignored){}}
        into.trim();return into.json();
    }
    /** The current profile's places, after the one-time move of legacy places to Adults. */
    static ResumeStore load(android.content.Context c){
        android.content.SharedPreferences p=c.getSharedPreferences(PlayerActivity.RESUME_PREFS,0);
        if(p.contains(LEGACY))p.edit().putString(prefsKey(Profiles.ADULT),merge(p.getString(LEGACY,""),p.getString(prefsKey(Profiles.ADULT),""))).remove(LEGACY).commit();
        return new ResumeStore(p.getString(prefsKey(Profiles.current(c.getSharedPreferences("unified-tv-preview",0))),""));
    }
    static void save(android.content.Context c,ResumeStore store){
        c.getSharedPreferences(PlayerActivity.RESUME_PREFS,0).edit().putString(prefsKey(Profiles.current(c.getSharedPreferences("unified-tv-preview",0))),store.json()).apply();
    }
}
