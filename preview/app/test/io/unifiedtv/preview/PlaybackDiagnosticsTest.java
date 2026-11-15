package io.unifiedtv.preview;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PlaybackDiagnosticsTest {
    @Test public void recordsFirstFrameTimeAndFormatsWithoutPrivateDetails()throws Exception{
        PlaybackDiagnostics d=PlaybackDiagnostics.begin("nas",1_000);
        assertSame(d,PlaybackDiagnostics.last);assertEquals(-1,d.firstFrameMs());
        d.prepared(1_200,90_000);d.firstFrame(2_700);d.firstFrame(9_000);d.seek();d.rebuffer();
        d.tracks("video/x-matroska",Arrays.asList(
            new PlaybackDiagnostics.Track(PlaybackDiagnostics.VIDEO,"video/hevc","hvc1.2.4.L150","",3840,2160,23.976f,-1,-1,true,true),
            new PlaybackDiagnostics.Track(PlaybackDiagnostics.AUDIO,"audio/eac3",null,"en",-1,-1,-1,6,48000,true,true),
            new PlaybackDiagnostics.Track(PlaybackDiagnostics.AUDIO,"audio/true-hd",null,"fr",-1,-1,-1,8,48000,false,false),
            new PlaybackDiagnostics.Track(PlaybackDiagnostics.TEXT,"application/x-subrip","http://192.168.50.20/subs/Secret.Film.srt","en",-1,-1,-1,-1,-1,true,false)));
        JSONObject j=d.json();
        assertEquals(1_700,j.getLong("firstFrameMs"));assertEquals(1_500,j.getLong("prepareToFirstFrameMs"));assertEquals(90_000,j.getLong("resumedFromMs"));
        assertEquals(2,j.getInt("audioTracks"));assertEquals(1,j.getInt("subtitleTracks"));assertEquals("video/x-matroska",j.getString("container"));
        assertEquals(1,j.getInt("seeks"));assertEquals(1,j.getInt("rebuffers"));
        assertEquals(3840,j.getJSONArray("tracks").getJSONObject(0).getInt("width"));
        assertFalse(j.getJSONArray("tracks").getJSONObject(2).getBoolean("supported"));
        assertFalse("no addresses or file names",j.toString().contains("192.168")||j.toString().contains("Secret"));
        d.failed("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED",PlaybackPolicy.Kind.PICTURE);
        assertEquals("PICTURE",d.json().getString("errorKind"));
    }
    @Test public void retriesAreCountedNotReset(){
        PlaybackDiagnostics d=PlaybackDiagnostics.begin("media-account",0);d.prepared(10,0);d.prepared(5_000,60_000);d.firstFrame(6_000);
        assertEquals(1,d.json().optInt("retries"));assertEquals(6_000,d.firstFrameMs());
    }
    @Test public void soundAndSubtitleLabelsArePlain(){
        assertEquals("English · 5.1",PlaybackDiagnostics.soundLabel("en",6,0,Locale.UK));
        assertEquals("French · stereo",PlaybackDiagnostics.soundLabel("fr",2,1,Locale.UK));
        assertEquals("Sound 2",PlaybackDiagnostics.soundLabel(null,-1,1,Locale.UK));
        assertEquals("Sound 1",PlaybackDiagnostics.soundLabel("und",-1,0,Locale.UK));
        assertEquals("German",PlaybackDiagnostics.subtitleLabel("de",0,Locale.UK));
        assertEquals("Subtitles 3",PlaybackDiagnostics.subtitleLabel("",2,Locale.UK));
    }
}
