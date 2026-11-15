package io.unifiedtv.preview;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** SET-081 backup and import, and SET-004 restore defaults: what may change, what never does. */
public class SettingsBackupTest {
    private static Map<String,Object> household(){
        Map<String,Object> m=new HashMap<>();
        m.put("pin","c2FsdA==:aGFzaA==");m.put("pinFailures",2);m.put("profile","child");m.put("room","sony");m.put("deviceId","1234");
        m.put(AlarmWatch.SHOWN,false);m.put(AlarmWatch.STOPPED,true);m.put("liveProtection","{\"ok\":true}");
        m.put("reduceMotion",true);m.put("dock","netflix,bbc,library");m.put(HouseholdSettings.RATINGS,"uk_us");m.put(DoorbellCamera.CLOSE,"60");
        m.put(QuietHours.ON,true);m.put(QuietHours.START,23*60);m.put(PlaybackPrefs.AUTOPLAY,true);m.put("reduceMotionX","stray");
        return m;
    }
    @Test public void anExportHoldsPreferencesButNoSecretOrSafetyState()throws Exception{
        String file=SettingsBackup.export(household(),"2026-10-25");
        JSONObject o=new JSONObject(file),s=o.getJSONObject("settings");
        assertEquals(SettingsBackup.FORMAT,o.getString("format"));assertEquals(SettingsSchema.VERSION,o.getInt("schema"));
        for(String never:new String[]{"pin","pinFailures","profile","room","deviceId",AlarmWatch.SHOWN,AlarmWatch.STOPPED,"liveProtection","reduceMotionX"})assertFalse(never+" never leaves the TV",s.has(never));
        assertFalse("no PIN hash anywhere in the file",file.contains("aGFzaA=="));
        assertTrue(s.getBoolean("reduceMotion"));assertEquals("netflix,bbc,library",s.getString("dock"));assertEquals(23*60,s.getInt(QuietHours.START));
    }
    @Test public void aFileComesBackCheckedAndShowsWhatWouldChange(){
        SettingsBackup.Incoming in=SettingsBackup.parse(SettingsBackup.export(household(),"2026-10-25"));
        assertTrue(in.usable());assertEquals(0,in.ignored);assertFalse(in.newer);
        Map<String,Object> now=new HashMap<>();now.put("reduceMotion",false);
        List<String> changes=SettingsBackup.changes(now,in);
        assertTrue(changes.toString(),changes.contains("Reduce motion: Off → On"));
        assertTrue(changes.toString(),changes.contains("Doorbell camera closes: "+DoorbellCamera.closeLabel("30")+" → "+DoorbellCamera.closeLabel("60")));
        Map<String,Object> same=new HashMap<>(SettingsBackup.apply(in));assertTrue("nothing to change when the TV already matches",SettingsBackup.changes(same,in).isEmpty());
    }
    @Test public void aHostileOrBrokenFileChangesNothingItShouldNot()throws Exception{
        assertFalse(SettingsBackup.parse("").usable());assertFalse(SettingsBackup.parse("not json").usable());
        assertFalse("another app's file",SettingsBackup.parse("{\"format\":\"other\",\"schema\":2,\"settings\":{\"reduceMotion\":true}}").usable());
        StringBuilder big=new StringBuilder("{\"format\":\"unified-tv-settings\",\"schema\":2,\"settings\":{\"dock\":\"");while(big.length()<SettingsBackup.MAX_CHARS)big.append("netflix,");
        assertFalse("too big",SettingsBackup.parse(big.append("\"}}").toString()).usable());
        JSONObject s=new JSONObject().put("pin","0000").put(AlarmWatch.SHOWN,false).put(AlarmWatch.STOPPED,true).put("liveProtection","yes").put("profile","adult")
            .put("reduceMotion","yes").put(DoorbellCamera.CLOSE,"forever").put("highContrast",true).put(QuietHours.END,6*60);
        SettingsBackup.Incoming in=SettingsBackup.parse(new JSONObject().put("format",SettingsBackup.FORMAT).put("schema",9).put("settings",s).toString());
        assertTrue(in.usable());assertTrue("from a newer version",in.newer);assertEquals(7,in.ignored);
        Map<String,Object> write=SettingsBackup.apply(in);
        assertEquals(new HashSet<>(Arrays.asList("highContrast",QuietHours.END)),write.keySet());
        assertEquals("a JSON number becomes the schema's integer",6*60,write.get(QuietHours.END));
        JSONObject nothing=new JSONObject().put("pin","0000");
        assertFalse("a file with only forbidden keys is refused",SettingsBackup.parse(new JSONObject().put("format",SettingsBackup.FORMAT).put("schema",2).put("settings",nothing).toString()).usable());
    }
    @Test public void aFullExportFitsThePhoneUpload(){
        Map<String,Object> m=new HashMap<>();
        for(SettingsSchema.Setting s:SettingsSchema.ALL)m.put(s.key,s.fallback);
        StringBuilder dock=new StringBuilder();for(int i=0;i<60;i++)dock.append(i==0?"":",").append("com.example.app").append(i);m.put("dock",dock.toString());m.put(KidsPolicy.APPS_PREF,dock.toString());
        String file=SettingsBackup.export(m,"2026-10-25");
        assertTrue(file.length()+" characters",file.length()<SettingsBackup.MAX_CHARS&&SettingsBackup.MAX_CHARS<PhoneSetupServer.BODY_LIMIT-1024);
        assertTrue(SettingsBackup.parse(file).usable());
    }
    @Test public void theInboxHoldsOnlyAUsableFile(){
        SettingsBackup.offer(SettingsBackup.parse("broken"));assertNull(SettingsBackup.waiting());
        SettingsBackup.Incoming ok=SettingsBackup.parse(SettingsBackup.export(household(),""));SettingsBackup.offer(ok);assertSame(ok,SettingsBackup.waiting());
        SettingsBackup.discard();assertNull(SettingsBackup.waiting());
    }

    // --- SET-004
    @Test public void aSectionRestoresOnlyItsOwnSettingsAndSaysWhatIsKept(){
        Map<String,Object> now=household();now.put("highContrast",true);
        SettingsReset.Plan p=SettingsReset.section(SettingsSchema.APPEARANCE,now);
        assertTrue(p.keys.contains("reduceMotion"));assertTrue(p.keys.contains(Accessibility.TEXT_SIZE));assertFalse("the dock is Apps & Shortcuts'",p.keys.contains("dock"));
        assertEquals(Arrays.asList("Reduce motion","High contrast"),p.changes);
        assertTrue(p.message(),p.message().startsWith("Goes back to its default: Reduce motion, High contrast."));
        assertTrue(p.message(),p.message().contains("smoke and carbon monoxide alarms")&&p.message().contains("Live TV protection"));
        SettingsReset.Plan n=SettingsReset.section(SettingsSchema.NOTIFICATIONS,now);
        assertTrue(n.keys.contains(QuietHours.ON));for(String k:n.keys)assertTrue(k,SettingsSchema.managed(k));
        assertFalse("alarm switches are never a section's default",n.keys.contains(AlarmWatch.SHOWN)||n.keys.contains(AlarmWatch.STOPPED));
        assertFalse("the team is chosen, not reset",SettingsReset.section(SettingsSchema.SMART_HOME,now).keys.contains(FixtureFeed.TEAM));
        assertFalse(SettingsReset.offered(SettingsSchema.SMART_HOME));assertTrue(SettingsReset.offered(SettingsSchema.APPS));
    }
    @Test public void theTopLevelResetKeepsConnectionsUnlessChosen(){
        SettingsReset.Plan kept=SettingsReset.all(household(),false),gone=SettingsReset.all(household(),true);
        for(SettingsReset.Plan p:new SettingsReset.Plan[]{kept,gone})for(String never:SettingsSchema.NEVER)assertFalse(never,p.keys.contains(never));
        assertTrue(kept.message(),kept.message().contains("Kept: connections and keys"));
        assertTrue(gone.message(),gone.message().contains("connections and keys (Home Assistant"));
        for(String[] c:SettingsReset.CONNECTIONS)assertNotEquals("the household record holds the alarm switch","household",c[0]);
    }
}
