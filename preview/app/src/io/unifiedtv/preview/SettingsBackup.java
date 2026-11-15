package io.unifiedtv.preview;

import org.json.JSONObject;
import java.util.*;

/**
 * SET-081 backup, export and import of the household's preferences, dock and safe profile settings, through phone setup
 * (OD-17): the phone downloads a small JSON file and can send one back; nothing is typed with the remote.
 *
 * <p>Only settings in SettingsSchema marked for export go into a file, and only with values that schema accepts. Never
 * in a file: tokens, keys, passwords, the Vault's records (connections, media servers, photo folders), the adult PIN or
 * its hash, camera images, photos, addresses, watch history, the event log, the profile in use, this TV's room, the
 * alarm switches or anything about Live TV. After a restore, connections are set up again on the phone.
 *
 * <p>Import is checked before anything changes: the file must say it is a Unified TV settings file, every value must pass
 * the schema (anything else is left out and counted), and the TV shows what would change; only an adult (the PIN) applies
 * it. A file from a newer version is read for the settings this version knows. Pure, apart from the in-memory inbox.
 */
final class SettingsBackup {
    private SettingsBackup(){}
    static final String FORMAT="unified-tv-settings",FILE="unified-tv-settings.json",TYPE="application/json; charset=utf-8";
    /** A backup is a few kilobytes; anything bigger is not one of ours (the phone page's upload limit is above this). */
    static final int MAX_CHARS=12_000;

    /** The file's text: {format, schema, created, settings{…}}. */
    static String export(Map<String,?> prefs,String created){
        try{
            JSONObject settings=new JSONObject();
            for(SettingsSchema.Setting s:SettingsSchema.ALL){if(!s.export||!SettingsSchema.managed(s.key))continue;Object v=prefs.get(s.key);if(s.accepts(v))settings.put(s.key,v);}
            return new JSONObject().put("format",FORMAT).put("schema",SettingsSchema.VERSION).put("created",created==null?"":created).put("settings",settings).toString(2);
        }catch(Exception e){throw new IllegalStateException(e);}
    }

    /** A checked file: the settings it may change, how many entries were left out, and a plain problem when unusable. */
    static final class Incoming {
        final Map<String,Object> values=new LinkedHashMap<>();int ignored;boolean newer;String problem;
        boolean usable(){return problem==null;}
    }
    static Incoming parse(String text){
        Incoming in=new Incoming();
        if(text==null||text.trim().isEmpty()){in.problem="That file is empty. Choose the file the TV saved.";return in;}
        if(text.length()>MAX_CHARS){in.problem="That file is too big to be a Unified TV settings file.";return in;}
        JSONObject o;
        try{o=new JSONObject(text.trim());}catch(Exception e){in.problem="That file isn't a Unified TV settings file.";return in;}
        if(!FORMAT.equals(o.optString("format"))){in.problem="That file isn't a Unified TV settings file.";return in;}
        int schema=o.optInt("schema",0);if(schema<1){in.problem="That file isn't a Unified TV settings file.";return in;}
        in.newer=schema>SettingsSchema.VERSION;
        JSONObject settings=o.optJSONObject("settings");if(settings==null){in.problem="That file has no settings in it.";return in;}
        for(Iterator<String> it=settings.keys();it.hasNext();){
            String key=it.next();SettingsSchema.Setting s=SettingsSchema.of(key);Object v=value(settings.opt(key),s);
            if(s!=null&&s.export&&SettingsSchema.managed(key)&&s.accepts(v))in.values.put(key,v);else in.ignored++;
        }
        if(in.values.isEmpty())in.problem="That file has no settings this TV can use.";
        return in;
    }
    /** JSON numbers and booleans as the schema's own types (org.json may read a number as Integer, Long or Double). */
    private static Object value(Object raw,SettingsSchema.Setting s){
        if(s==null||raw==null||raw==JSONObject.NULL)return null;
        if(s.type==SettingsSchema.Type.INT&&raw instanceof Number){double d=((Number)raw).doubleValue();return d==Math.rint(d)&&Math.abs(d)<=Integer.MAX_VALUE?(Object)(int)d:null;}
        return raw;
    }
    /** "Name: now → after", for each setting the file would change (a missing value reads as its default). */
    static List<String> changes(Map<String,?> now,Incoming in){
        List<String> out=new ArrayList<>();
        for(Map.Entry<String,Object> e:in.values.entrySet()){SettingsSchema.Setting s=SettingsSchema.of(e.getKey());Object before=now.get(e.getKey());
            if(!s.accepts(before))before=s.fallback;if(Objects.equals(before,e.getValue()))continue;
            out.add(s.name+": "+s.say(before)+" → "+s.say(e.getValue()));}
        return out;
    }
    /** What to write to apply a checked file: its values for managed keys only. */
    static Map<String,Object> apply(Incoming in){
        Map<String,Object> out=new LinkedHashMap<>();
        if(in==null||!in.usable())return out;
        for(Map.Entry<String,Object> e:in.values.entrySet())if(SettingsSchema.managed(e.getKey()))out.put(e.getKey(),e.getValue());
        return out;
    }

    /** The last file the phone sent, waiting for an adult on the TV; memory only, never saved. */
    private static volatile Incoming inbox;
    static void offer(Incoming in){inbox=in!=null&&in.usable()?in:null;}
    static Incoming waiting(){return inbox;}
    static void discard(){inbox=null;}
}
