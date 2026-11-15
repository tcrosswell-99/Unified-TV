package io.unifiedtv.preview;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** The pure rules behind SET-025, -052, -055, -070, -073, -033, -031 and -021. */
public class SettingsFoundationsTest {
    // SET-052
    @Test public void quietHoursRunOvernightAndNeverByAccident(){
        assertTrue(QuietHours.inside(22*60,7*60,23*60));assertTrue(QuietHours.inside(22*60,7*60,3*60));assertFalse(QuietHours.inside(22*60,7*60,7*60));assertFalse(QuietHours.inside(22*60,7*60,12*60));
        assertTrue(QuietHours.inside(13*60,14*60,13*60+30));assertFalse(QuietHours.inside(13*60,14*60,14*60));
        assertFalse("equal times: no quiet time",QuietHours.inside(600,600,600));assertFalse("nonsense never quietens",QuietHours.inside(-5,9000,100));
        assertTrue("off: always allowed",QuietHours.personAllowed(false,22*60,7*60,23*60));assertFalse(QuietHours.personAllowed(true,22*60,7*60,23*60));
        assertEquals("07:30",QuietHours.clock(7*60+30));assertEquals(48,QuietHours.choices().length);
    }
    // SET-055
    @Test public void theEventLogIsMinimalBoundedAndLocal(){
        long now=10L*24*60*60*1000;List<EventLog.Event> log=new ArrayList<>();
        log=EventLog.add(log,EventLog.DOORBELL,"Doorbell pressed: camera shown",now-8L*24*60*60*1000);
        log=EventLog.add(log,EventLog.PERSON,"Person at the door: shown",now);
        assertEquals("older than 7 days goes",1,log.size());
        assertEquals("an unknown kind adds nothing",1,EventLog.add(log,"camera","frame.jpg",now).size());
        assertEquals("empty words add nothing",1,EventLog.add(log,EventLog.ALERT," ",now).size());
        for(int i=0;i<250;i++)log=EventLog.add(log,EventLog.CONNECTION,"Home Assistant connected "+i,now+i);
        assertEquals(EventLog.MAX,log.size());assertEquals("oldest out first","Home Assistant connected 249",log.get(log.size()-1).words);
        String w=EventLog.add(new ArrayList<>(),EventLog.ALERT,new String(new char[300]).replace('\0','x'),now).get(0).words;assertEquals(EventLog.WORDS_MAX,w.length());
        List<EventLog.Event> back=EventLog.parse(EventLog.json(log));assertEquals(log.size(),back.size());assertEquals(log.get(0).words,back.get(0).words);
        assertTrue("a damaged log reads as empty",EventLog.parse("{broken").isEmpty());
        assertTrue("nothing dated in the future",EventLog.trim(Collections.singletonList(new EventLog.Event(EventLog.ALERT,"x",now+3_600_000)),now).isEmpty());
    }
    // SET-070
    @Test public void connectivityTellsNotSetUpFromCantReach(){
        assertEquals("Media server: not set up",Connectivity.line(Connectivity.media(false,"",0)));
        assertEquals("Media server (Synology): can't reach it",Connectivity.line(Connectivity.media(true,"Synology",DlnaClient.Failure.UNREACHABLE)));
        assertEquals("Media server (Synology): working",Connectivity.line(Connectivity.media(true,"Synology",0)));
        assertEquals("Home Assistant: not set up",Connectivity.line(Connectivity.homeAssistant(null)));
        assertEquals("Home Assistant: not set up",Connectivity.line(Connectivity.homeAssistant(HaStatus.NOT_CONFIGURED)));
        assertEquals("Home Assistant: can't reach it",Connectivity.line(Connectivity.homeAssistant(HaStatus.OFFLINE)));
        assertEquals("Home Assistant: needs signing in again",Connectivity.line(Connectivity.homeAssistant(HaStatus.AUTH_REQUIRED)));
        assertEquals("Internet: connected",Connectivity.line(Connectivity.internet(true,true)));
        Connectivity.Row offline=Connectivity.internet(true,false);
        assertEquals("Internet: can't reach it",Connectivity.line(offline));assertTrue("the NAS may still work",offline.detail.contains("home network may still work"));
        assertTrue(Connectivity.detail(offline,"today 14:02").endsWith("Last worked today 14:02."));
        assertTrue(Connectivity.detail(offline,null).endsWith("hasn't worked on this TV yet."));
        assertFalse("not set up never claims a failure",Connectivity.detail(Connectivity.media(false,"",0),null).contains("worked"));
        assertEquals("Home network: can't reach it",Connectivity.line(Connectivity.home(false)));
    }
    // SET-073 and SET-025
    @Test public void accessibilityScalesTextAndLengthensTimes(){
        assertEquals(1f,Accessibility.scale("nonsense"),0);assertEquals(1.2f,Accessibility.scale(Accessibility.LARGE),0);assertEquals(1.4f,Accessibility.scale(Accessibility.LARGEST),0);
        assertEquals(10_000,Accessibility.time(5_000,true));assertEquals(5_000,Accessibility.time(5_000,false));assertEquals("Back only stays Back only",0,Accessibility.time(0,true));
        assertEquals("Standard",Accessibility.label(null));assertEquals("Largest",Accessibility.label(Accessibility.LARGEST));
    }
    @Test public void riskyChangesArePreviewedAndCountDown(){
        assertTrue(PreviewRevert.risky(Accessibility.TEXT_SIZE,"normal","largest"));assertFalse(PreviewRevert.risky(Accessibility.TEXT_SIZE,"large","large"));
        assertTrue("high contrast off",PreviewRevert.risky("highContrast",true,false));assertFalse("high contrast on is safe",PreviewRevert.risky("highContrast",false,true));
        assertTrue(PreviewRevert.risky("opaque",true,false));assertFalse(PreviewRevert.risky("reduceMotion",false,true));
        PreviewRevert p=new PreviewRevert(Accessibility.TEXT_SIZE,"normal","largest",1000);
        assertEquals(15,p.secondsLeft(1000));assertEquals(1,p.secondsLeft(15_500));assertFalse(p.due(15_999));assertTrue(p.due(16_000));
        assertEquals("Text size is now Largest. Keep it? It goes back to Standard in 15 seconds.",p.words(1000));
    }
    // SET-033
    @Test public void playbackPreferencesDecideWhereToStartAndWhatIsNext(){
        assertEquals(-1,PlaybackPrefs.start(PlaybackPrefs.ASK,60_000));assertEquals(60_000,PlaybackPrefs.start(PlaybackPrefs.ALWAYS,60_000));
        assertEquals(0,PlaybackPrefs.start(PlaybackPrefs.START,60_000));assertEquals("nothing saved: from the start",0,PlaybackPrefs.start(PlaybackPrefs.ASK,0));
        assertEquals(PlaybackPrefs.ASK,PlaybackPrefs.resume("garbage"));
        assertEquals(.0533f,PlaybackPrefs.subtitleFraction((String)null),0);assertTrue(PlaybackPrefs.subtitleFraction(Accessibility.LARGEST)>PlaybackPrefs.subtitleFraction(Accessibility.LARGE));
        List<LibraryCatalogue.Title> eps=new ArrayList<>();
        for(int i=1;i<=25;i++){LibraryCatalogue.Title t=new LibraryCatalogue.Title();t.kind=LibraryCatalogue.Kind.EPISODE;t.season=1;t.episode=i;t.url=i==3?"":"http|80|/e"+i;t.id="e"+i;eps.add(t);}
        List<LibraryCatalogue.Title> next=PlaybackPrefs.following(eps,eps.get(0));
        assertEquals("e2",next.get(0).id);assertEquals("a title with no stream is skipped","e4",next.get(1).id);assertEquals(PlaybackPrefs.QUEUE,next.size());
        assertTrue("the last episode has nothing next",PlaybackPrefs.following(eps,eps.get(24)).isEmpty());
        assertTrue(PlaybackPrefs.following(eps,new LibraryCatalogue.Title()).isEmpty());
    }
    // SET-031
    @Test public void aLocalNameIsShortAndPlain(){
        assertEquals("Loft NAS",MediaSources.name(" Loft NAS ","DS920+"));assertEquals("DS920+",MediaSources.name("","DS920+"));assertEquals("Media server",MediaSources.name(null,""));
        assertTrue(MediaSources.validName(""));assertFalse(MediaSources.validName("<script>"));assertFalse(MediaSources.validName("a\nb"));assertFalse(MediaSources.validName(new String(new char[41]).replace('\0','a')));
        assertNull(MediaSources.problem(0));assertEquals(DlnaClient.Failure.plain(DlnaClient.Failure.REFUSED),MediaSources.problem(DlnaClient.Failure.REFUSED));
    }
    // SET-021 / SET-BUILD-06
    @Test public void missingAppsOnTheDockAreFoundAndOfferedReplacements(){
        Map<String,String> apps=new HashMap<>();apps.put("com.netflix.ninja","Netflix");apps.put("com.spotify.tv.android","Spotify");apps.put("org.example.iplayer","BBC iPlayer");apps.put("com.plexapp.android","Plex");
        List<String> dock=Arrays.asList("netflix","disney","bbc","library","xbox","live","com.gone.app","com.plexapp.android");
        assertEquals(Arrays.asList("disney","live","com.gone.app"),DockRepair.missing(dock,apps,false));
        assertEquals("Live TV is there when a Live TV app is recognised",Arrays.asList("disney","com.gone.app"),DockRepair.missing(dock,apps,true));
        assertEquals("Disney+",DockRepair.name("disney",apps));assertEquals("com.gone.app",DockRepair.name("com.gone.app",apps));
        List<String> c=DockRepair.candidates("tcl",dock,apps);
        assertEquals("only apps not already on Home, by name",Arrays.asList("org.example.iplayer","com.spotify.tv.android"),c);
        assertEquals("replacing keeps the place","netflix,com.spotify.tv.android,bbc",DockPolicy.replace("tcl",Arrays.asList("netflix","disney","bbc"),"disney","com.spotify.tv.android"));
    }
    @Test public void privacyFiguresAreInPlainWords(){
        assertEquals("Nothing",PrivacyData.size(0));assertEquals("Under 1 MB",PrivacyData.size(4096));assertEquals("12 MB",PrivacyData.size(12L*1024*1024+5));assertEquals("1.5 GB",PrivacyData.size(1536L*1024*1024));
        assertEquals("none",PrivacyData.count(0,"title","titles"));assertEquals("1 title",PrivacyData.count(1,"title","titles"));assertEquals("3 titles",PrivacyData.count(3,"title","titles"));
    }
}
