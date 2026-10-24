package io.unifiedtv.preview;
import java.util.*;
/** Pure arbitration: Ring wins, duplicates are bounded, a reminder during Ring waits briefly, stale reminders are dropped. */
final class AlertPolicy {
    private final LinkedHashMap<String,Long> seen=new LinkedHashMap<>();
    private long ringUntil;private String deferred="";private long deferredFired;
    /** HA and TV clocks are independently set: allow the same small lead the event filter accepts. */
    static final long SKEW=5000,RELEVANT=30000;
    boolean acceptRing(String id,long now){
        Long previous=seen.get(id);if(previous!=null&&now-previous<15000)return false;
        seen.put(id,now);while(seen.size()>128)seen.remove(seen.keySet().iterator().next());ringUntil=now+45000;return true;
    }
    boolean reminderAllowed(long fired,long now){return now>=ringUntil&&fired-now<=SKEW&&now-fired<=RELEVANT;}
    boolean ringing(long now){return now<ringUntil;}
    /** Spec 4.10: a reminder that arrives during Ring is held (latest only), then shown if still relevant. */
    void defer(String text,long fired){if(text!=null&&!text.trim().isEmpty()){deferred=text;deferredFired=fired;}}
    String release(long now){String text=deferred;deferred="";return !text.isEmpty()&&reminderAllowed(deferredFired,now)?text:null;}
    void dismiss(){ringUntil=0;}
    static boolean rising(String entity,String door,String person,String oldState,String next){
        return !entity.isEmpty()&&(entity.equals(door)||entity.equals(person))&&"off".equals(oldState)&&"on".equals(next);
    }
}
