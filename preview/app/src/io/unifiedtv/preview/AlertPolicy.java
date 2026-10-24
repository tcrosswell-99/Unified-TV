package io.unifiedtv.preview;
import org.json.*;
import java.util.*;
/**
 * Pure arbitration (owner decision OD-07, 25 October 2026): smoke and carbon monoxide alarms come first and are equal, then
 * Ring, then detector warnings, then reminders. Ring never covers an active alarm; it waits until every alarm has cleared.
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
    boolean reminderAllowed(long fired,long now){return !emergency()&&now>=ringUntil&&fired-now<=SKEW&&now-fired<=RELEVANT;}
    boolean ringing(long now){return now<ringUntil;}
    /** Spec 4.10: a reminder that arrives during Ring is held (latest only), then shown if still relevant. */
    void defer(String text,long fired){if(text!=null&&!text.trim().isEmpty()){deferred=text;deferredFired=fired;}}
    String release(long now){String text=deferred;deferred="";return !text.isEmpty()&&reminderAllowed(deferredFired,now)?text:null;}
    void dismiss(){ringUntil=0;}
    static boolean rising(String entity,String door,String person,String oldState,String next){
        return !entity.isEmpty()&&(entity.equals(door)||entity.equals(person))&&"off".equals(oldState)&&"on".equals(next);
    }

    // Smoke and carbon monoxide detectors, from Home Assistant's own device classes; never guessed from an entity's name.
    static final String ALARM="alarm",NORMAL="normal",OFFLINE="offline",TEST="test",FAULT="fault";
    /** One detector's current reading. An alarm keeps its first time; "lost" marks an alarm whose detector stopped answering. */
    static final class Detector {
        final String entity,kind;String name,state;long since;boolean simulated,acknowledged,lost;
        Detector(String entity,String kind,String name,String state,long since,boolean simulated){this.entity=entity;this.kind=kind;this.name=name;this.state=state;this.since=since;this.simulated=simulated;}
        boolean alarm(){return ALARM.equals(state);}
        /** "SMOKE ALARM" or "CARBON MONOXIDE ALARM"; simulated ones always say TEST. */
        String title(){return (simulated?"TEST · ":"")+("smoke".equals(kind)?"SMOKE ALARM":"CARBON MONOXIDE ALARM");}
        String advice(){return "smoke".equals(kind)?"Get everyone out, stay out, and call the fire service."
            :"Get everyone outside into fresh air and call for help. Don't go back in until it's safe.";}
        String warning(){String what="smoke".equals(kind)?"Smoke detector":"Carbon monoxide detector";
            return (simulated?"TEST · ":"")+what+(TEST.equals(state)?" test":FAULT.equals(state)?" fault":" offline");}
    }
    private final LinkedHashMap<String,Detector> detectors=new LinkedHashMap<>();
    private boolean connected=true;private long queuedRing;
    static final int NONE=0,CHANGED=1,NEW_ALARM=2;
    static final long RING_STILL_RELEVANT=45000;

    static String alarmKind(String entity,String deviceClass){
        // binary_sensor only: a sensor.* with device class carbon_monoxide is a concentration in ppm, not an alarm.
        if(entity==null||!entity.startsWith("binary_sensor."))return null;
        return "smoke".equals(deviceClass)||"carbon_monoxide".equals(deviceClass)?deviceClass:null;
    }
    /** Home Assistant's state to ours. Test and fault exist only where an integration reports them; nothing is assumed. */
    static String reading(String state){
        if(state==null)return null;
        switch(state){case "on":return ALARM;case "off":return NORMAL;case "unavailable":case "unknown":return OFFLINE;
            case "test":return TEST;case "fault":return FAULT;default:return null;}
    }
    /**
     * Records one detector's state. NEW_ALARM when an alarm starts (the full screen comes back), CHANGED when anything shown
     * changes. Normal removes it: that is "no alarm from this detector", never "safe". An alarm whose detector goes quiet
     * stays an alarm, marked lost, because the detector may have failed in the fire.
     */
    int update(String entity,String kind,String name,String haState,long since,boolean simulated){
        String state=reading(haState);if(kind==null||state==null)return NONE;
        Detector d=detectors.get(entity);name=name==null?"":name;
        if(NORMAL.equals(state))return detectors.remove(entity)!=null?CHANGED:NONE;
        if(d!=null&&d.alarm()&&(OFFLINE.equals(state)||FAULT.equals(state))){if(d.lost)return NONE;d.lost=true;return CHANGED;}
        if(d!=null&&d.alarm()&&ALARM.equals(state)){boolean changed=d.lost||!d.name.equals(name);d.lost=false;d.name=name;return changed?CHANGED:NONE;}
        if(d!=null&&d.state.equals(state)&&d.name.equals(name))return NONE;
        detectors.put(entity,new Detector(entity,kind,name,state,since,simulated));
        return ALARM.equals(state)?NEW_ALARM:CHANGED;
    }
    /**
     * The full picture after (re)connecting: every smoke and CO detector with its state, as {entity, kind, name, state, since}.
     * Detectors no longer reported are dropped; simulated ones are kept. Acknowledgements survive for alarms still sounding.
     */
    int snapshot(List<String[]> all){
        connected=true;int result=NONE;Set<String> seen=new HashSet<>();
        for(String[] d:all){seen.add(d[0]);long since;try{since=Long.parseLong(d[4]);}catch(RuntimeException e){since=0;}result=Math.max(result,update(d[0],d[1],d[2],d[3],since,false));}
        for(Iterator<Detector> it=detectors.values().iterator();it.hasNext();){Detector d=it.next();if(!d.simulated&&!seen.contains(d.entity)){it.remove();result=Math.max(result,CHANGED);}}
        return result;
    }
    /** Losing Home Assistant never clears an alarm; it is shown as "status unknown" until the next full picture. */
    void connection(boolean up){connected=up;}
    boolean connected(){return connected;}
    boolean emergency(){for(Detector d:detectors.values())if(d.alarm())return true;return false;}
    /** The full screen is shown while any alarm is unacknowledged. */
    boolean screen(){for(Detector d:detectors.values())if(d.alarm()&&!d.acknowledged)return true;return false;}
    List<Detector> alarms(){List<Detector> out=new ArrayList<>();for(Detector d:detectors.values())if(d.alarm())out.add(d);out.sort((a,b)->Long.compare(a.since,b.since));return out;}
    List<Detector> warnings(){List<Detector> out=new ArrayList<>();for(Detector d:detectors.values())if(!d.alarm())out.add(d);return out;}
    /** Acknowledges one alarm only; others stay on screen. The detector itself is never touched. */
    boolean acknowledge(String entity){Detector d=detectors.get(entity);if(d==null||!d.alarm()||d.acknowledged)return false;d.acknowledged=true;return true;}
    /** Ends a test: removes simulated detectors only. */
    int endSimulation(){int n=0;for(Iterator<Detector> it=detectors.values().iterator();it.hasNext();)if(it.next().simulated){it.remove();n++;}return n>0?CHANGED:NONE;}
    /** A doorbell visit during an emergency: no camera, only the latest visit is remembered. */
    void queueRing(long now){queuedRing=now;}
    /** After every alarm has cleared: 1 to show the camera (visit still recent), -1 for a "rang during the alarm" note, 0 for nothing. */
    int ringAfterAlarm(long now){if(queuedRing==0||emergency())return 0;long at=queuedRing;queuedRing=0;return now-at<=RING_STILL_RELEVANT?1:-1;}
    long queuedRingAt(){return queuedRing;}

    /** Every smoke and CO detector in a successful get_states result, as {entity, kind, name, state, since}; null for anything else, so a refusal never clears an alarm. */
    static List<String[]> detectorsIn(String message,int id){
        try{JSONObject value=new JSONObject(message);if(value.optInt("id")!=id||!"result".equals(value.optString("type"))||!value.optBoolean("success"))return null;
            JSONArray states=value.optJSONArray("result");List<String[]> out=new ArrayList<>();if(states==null)return out;
            for(int i=0;i<states.length();i++){JSONObject s=states.optJSONObject(i);if(s==null)continue;JSONObject attr=s.optJSONObject("attributes");
                String entity=s.optString("entity_id"),kind=alarmKind(entity,attr==null?"":attr.optString("device_class"));
                if(kind!=null)out.add(new String[]{entity,kind,name(attr),s.optString("state"),String.valueOf(time(s.optString("last_changed")))});}
            return out;
        }catch(JSONException e){return null;}
    }
    static long time(String iso){try{return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();}catch(RuntimeException e){return 0;}}
    static String name(JSONObject attributes){String n=attributes==null?"":attributes.optString("friendly_name");return n.length()>60?n.substring(0,60):n;}
}
