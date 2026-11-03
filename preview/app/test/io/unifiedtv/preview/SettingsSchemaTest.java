package io.unifiedtv.preview;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** SET-006/SET-015: one versioned schema; migration keeps configuration; corrupt values fall back; safety keys untouched. */
public class SettingsSchemaTest {
    private static Map<String,Object> after(Map<String,Object> before,Map<String,Object> changes){
        Map<String,Object> m=new HashMap<>(before);for(Map.Entry<String,Object> e:changes.entrySet()){if(e.getValue()==null)m.remove(e.getKey());else m.put(e.getKey(),e.getValue());}return m;
    }
    @Test public void everyDefaultIsAcceptedAndNoSafetyKeyIsInTheSchema(){
        Set<String> keys=new HashSet<>();
        for(SettingsSchema.Setting s:SettingsSchema.ALL){
            assertTrue(s.key+" is listed once",keys.add(s.key));
            assertTrue(s.key+"'s default passes its own check",s.accepts(s.fallback));
            assertFalse(s.key+" must never be managed by resets or imports",SettingsSchema.NEVER.contains(s.key));
            assertFalse(s.name.isEmpty());assertNotNull(s.say(s.fallback));
        }
        for(String never:new String[]{"pin",AlarmWatch.SHOWN,AlarmWatch.STOPPED,"liveProtection","profile","room"})assertFalse(never,SettingsSchema.managed(never));
    }
    @Test public void anEarlierInstallMigratesInOneStepWithoutLosingItsSettings(){
        Map<String,Object> v0=new HashMap<>();v0.put("focus",2);v0.put("reduceMotion",true);v0.put("liveProtection","{}");v0.put("pin","salt:hash");v0.put(AlarmWatch.SHOWN,false);
        Map<String,Object> c=SettingsSchema.migrate(v0,"tcl","GB");
        assertEquals(SettingsSchema.VERSION,c.get(SettingsSchema.KEY));
        assertEquals("the earlier six are written down as its dock","smartutube,netflix,disney,bbc,live,music",c.get("dock"));
        assertTrue("Live TV attestation removed",c.containsKey("liveProtection")&&c.get("liveProtection")==null);
        Map<String,Object> v2=after(v0,c);
        assertEquals(true,v2.get("reduceMotion"));assertEquals("salt:hash",v2.get("pin"));assertEquals("alarms stay switched off exactly as the adult left them",false,v2.get(AlarmWatch.SHOWN));
        assertTrue("runs once",SettingsSchema.migrate(v2,"tcl","GB").isEmpty());
    }
    @Test public void versionOneMovesToTwoAndRepairsCorruptValues(){
        Map<String,Object> v1=new HashMap<>();v1.put(SettingsSchema.KEY,1);v1.put("dock","netflix,bbc");v1.put("reduceMotion","yes");v1.put(DoorbellCamera.CLOSE,"forever");
        v1.put("wallpaperIdle",7);v1.put(QuietHours.START,5000);v1.put("someFutureKey","kept");v1.put(AlarmWatch.STOPPED,true);v1.put("highContrast",true);
        Map<String,Object> c=SettingsSchema.migrate(v1,"tcl","GB");
        assertEquals(2,c.get(SettingsSchema.KEY));
        for(String bad:new String[]{"reduceMotion",DoorbellCamera.CLOSE,"wallpaperIdle",QuietHours.START})assertTrue(bad+" falls back to its default",c.containsKey(bad)&&c.get(bad)==null);
        for(String kept:new String[]{"dock","someFutureKey",AlarmWatch.STOPPED,"highContrast"})assertFalse(kept+" is untouched",c.containsKey(kept));
    }
    @Test public void repairOnlyJudgesKnownKeys(){
        Map<String,Object> m=new HashMap<>();m.put("textSize","enormous");m.put("unknown",42);m.put(AlarmWatch.SHOWN,"garbage");m.put("autoplayNext",false);
        Map<String,Object> r=SettingsSchema.repair(m);
        assertEquals(Collections.singleton("textSize"),r.keySet());
    }
}
