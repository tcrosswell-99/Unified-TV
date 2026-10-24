package io.unifiedtv.preview;
import java.util.*;
/** Pure arbitration: Ring wins, duplicates are bounded, stale reminders are dropped. */
final class AlertPolicy {
    private final LinkedHashMap<String,Long> seen=new LinkedHashMap<>();
    private long ringUntil;
    boolean acceptRing(String id,long now){
        Long previous=seen.get(id);if(previous!=null&&now-previous<15000)return false;
        seen.put(id,now);while(seen.size()>128)seen.remove(seen.keySet().iterator().next());ringUntil=now+45000;return true;
    }
    boolean reminderAllowed(long fired,long now){return now>=ringUntil&&now>=fired&&now-fired<=30000;}
    void dismiss(){ringUntil=0;}
    static boolean rising(String entity,String door,String person,String oldState,String next){
        return !entity.isEmpty()&&(entity.equals(door)||entity.equals(person))&&"off".equals(oldState)&&"on".equals(next);
    }
}
