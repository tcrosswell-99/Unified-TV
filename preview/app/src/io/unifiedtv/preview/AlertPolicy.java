package io.unifiedtv.preview;
import org.json.*;
import java.util.*;
/**
 * Pure arbitration. A smoke or carbon monoxide alarm comes first (owner decision, 24 October 2026), then Ring, then reminders.
 * Duplicates are bounded, a reminder during Ring waits briefly, stale reminders are dropped.
 */
final class AlertPolicy {
    private final LinkedHashMap<String,Long> seen=new LinkedHashMap<>();
    private long ringUntil;private String deferred="";private long deferredFired;
    /** HA and TV clocks are independently set: allow the same small lead the event filter accepts. */
    static final long SKEW=5000,RELEVANT=30000;
    boolean acceptRing(String id,long now){
        Long previous=seen.get(id);if(previous!=null&&now-previous<15000)return false;
        seen.put(id,now);while(seen.size()>128)seen.remove(seen.keySet().iterator().next());ringUntil=now+45000;return true;
    }
    boolean reminderAllowed(long fired,long now){return sounding.isEmpty()&&now>=ringUntil&&fired-now<=SKEW&&now-fired<=RELEVANT;}
    boolean ringing(long now){return now<ringUntil;}
    /** Spec 4.10: a reminder that arrives during Ring is held (latest only), then shown if still relevant. */
    void defer(String text,long fired){if(text!=null&&!text.trim().isEmpty()){deferred=text;deferredFired=fired;}}
    String release(long now){String text=deferred;deferred="";return !text.isEmpty()&&reminderAllowed(deferredFired,now)?text:null;}
    void dismiss(){ringUntil=0;}
    static boolean rising(String entity,String door,String person,String oldState,String next){
        return !entity.isEmpty()&&(entity.equals(door)||entity.equals(person))&&"off".equals(oldState)&&"on".equals(next);
    }

    // Smoke and carbon monoxide alarms, from Home Assistant's own device classes; never guessed from an entity's name.
    private final LinkedHashMap<String,String[]> sounding=new LinkedHashMap<>();private boolean acknowledged;
    static String alarmKind(String entity,String deviceClass){
        if(entity==null||!entity.startsWith("binary_sensor."))return null;
        return "smoke".equals(deviceClass)||"carbon_monoxide".equals(deviceClass)?deviceClass:null;
    }
    /**
     * Records one alarm sensor's state; true when the alarm screen should appear because a sensor that wasn't sounding now is.
     * Only "off" clears it: a sensor that goes unavailable mid-alarm may have failed in the fire.
     */
    boolean alarm(String entity,String kind,String name,String state){
        if(kind==null)return false;
        if("on".equals(state)){boolean fresh=!sounding.containsKey(entity);sounding.put(entity,new String[]{kind,name==null?"":name});if(fresh)acknowledged=false;return fresh;}
        if("off".equals(state)){sounding.remove(entity);if(sounding.isEmpty())acknowledged=false;}
        return false;
    }
    /** A full picture after (re)connecting: exactly these sensors are sounding now. True when one of them is new. */
    boolean alarmSnapshot(List<String[]> on){
        Set<String> now=new HashSet<>();boolean fresh=false;
        for(String[] a:on){now.add(a[0]);fresh|=alarm(a[0],a[1],a[2],"on");}
        sounding.keySet().retainAll(now);if(sounding.isEmpty())acknowledged=false;
        return fresh;
    }
    boolean alarming(){return !sounding.isEmpty();}
    /** "OK, I've seen it": the screen stays away until another sensor sounds or this one clears and sounds again. */
    void acknowledge(){acknowledged=true;}
    boolean alarmShown(){return !sounding.isEmpty()&&!acknowledged;}
    String alarmTitle(){
        boolean smoke=false,co=false;for(String[] a:sounding.values()){smoke|=a[0].equals("smoke");co|=a[0].equals("carbon_monoxide");}
        return smoke&&co?"Smoke and carbon monoxide alarms":smoke?"Smoke alarm":"Carbon monoxide alarm";
    }
    String alarmAdvice(){
        for(String[] a:sounding.values())if(a[0].equals("smoke"))return "Get everyone out, stay out, and call the fire service.";
        return "Get everyone outside into fresh air and call for help. Don't go back in until it's safe.";
    }
    /** Where, in the household's own names for its sensors; up to three. */
    String alarmPlaces(){
        List<String> names=new ArrayList<>();for(String[] a:sounding.values())if(!a[1].trim().isEmpty()&&names.size()<3&&!names.contains(a[1].trim()))names.add(a[1].trim());
        return String.join(", ",names);
    }
    /** The sounding alarms in a successful get_states result, as {entity, kind, name}; null for anything else, so a refusal never clears an alarm. */
    static List<String[]> soundingIn(String message,int id){
        try{JSONObject value=new JSONObject(message);if(value.optInt("id")!=id||!"result".equals(value.optString("type"))||!value.optBoolean("success"))return null;
            JSONArray states=value.optJSONArray("result");List<String[]> out=new ArrayList<>();if(states==null)return out;
            for(int i=0;i<states.length();i++){JSONObject s=states.optJSONObject(i);if(s==null)continue;JSONObject attr=s.optJSONObject("attributes");
                String entity=s.optString("entity_id"),kind=alarmKind(entity,attr==null?"":attr.optString("device_class"));
                if(kind!=null&&"on".equals(s.optString("state")))out.add(new String[]{entity,kind,name(attr)});}
            return out;
        }catch(JSONException e){return null;}
    }
    static String name(JSONObject attributes){String n=attributes==null?"":attributes.optString("friendly_name");return n.length()>60?n.substring(0,60):n;}
}
