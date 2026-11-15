package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackPolicyTest {
    @Test public void onePressSeeksTenSecondsAndHoldingGetsFasterButThrottled(){
        assertEquals(10_000,PlaybackPolicy.seekStep(0));
        assertEquals(0,PlaybackPolicy.seekStep(1));assertEquals(0,PlaybackPolicy.seekStep(3));
        assertEquals(10_000,PlaybackPolicy.seekStep(4));
        assertEquals(30_000,PlaybackPolicy.seekStep(40));assertEquals(60_000,PlaybackPolicy.seekStep(100));
        long moved=0;for(int r=0;r<20;r++)moved+=PlaybackPolicy.seekStep(r);
        assertEquals("one second of holding moves less than a minute",50_000,moved);
    }
    @Test public void seekStaysInsideTheFilm(){
        assertEquals(0,PlaybackPolicy.seekTarget(4_000,-10_000,600_000));
        assertEquals(599_000,PlaybackPolicy.seekTarget(595_000,10_000,600_000));
        assertEquals(70_000,PlaybackPolicy.seekTarget(60_000,10_000,600_000));
        assertEquals("unknown length (Media3 TIME_UNSET) leaves the end open",130_000,PlaybackPolicy.seekTarget(120_000,10_000,Long.MIN_VALUE+1));
    }
    @Test public void resumeOnlyFromAMeaningfulPlace(){
        assertEquals(0,PlaybackPolicy.resumePoint(20_000,6_000_000));
        assertEquals(1_800_000,PlaybackPolicy.resumePoint(1_800_000,6_000_000));
        assertEquals("watched to the credits",0,PlaybackPolicy.resumePoint(5_950_000,6_000_000));
        assertEquals("unknown length still resumes",1_800_000,PlaybackPolicy.resumePoint(1_800_000,0));
        assertTrue(PlaybackPolicy.finished(290_000,300_000));assertFalse(PlaybackPolicy.finished(200_000,300_000));
    }
    @Test public void stallLimitEndsAnyEndlessSpinner(){
        assertFalse(PlaybackPolicy.stalled(0,100_000));
        assertFalse(PlaybackPolicy.stalled(10_000,39_999));
        assertTrue(PlaybackPolicy.stalled(10_000,40_000));
    }
    @Test public void clockLabels(){
        assertEquals("0:05",PlaybackPolicy.clock(5_400));assertEquals("42:10",PlaybackPolicy.clock(2_530_000));assertEquals("1:02:03",PlaybackPolicy.clock(3_723_000));
    }
    @Test public void media3ErrorCodesBecomePlainReasons(){
        assertEquals(PlaybackPolicy.Kind.NETWORK,PlaybackPolicy.kind(2001));assertEquals(PlaybackPolicy.Kind.NETWORK,PlaybackPolicy.kind(2002));assertEquals(PlaybackPolicy.Kind.NETWORK,PlaybackPolicy.kind(1003));
        assertEquals(PlaybackPolicy.Kind.REFUSED,PlaybackPolicy.kind(2004));assertEquals(PlaybackPolicy.Kind.MISSING,PlaybackPolicy.kind(2005));
        assertEquals(PlaybackPolicy.Kind.FORMAT,PlaybackPolicy.kind(3003));assertEquals(PlaybackPolicy.Kind.FORMAT,PlaybackPolicy.kind(3001));
        assertEquals(PlaybackPolicy.Kind.PICTURE,PlaybackPolicy.kind(4005));assertEquals(PlaybackPolicy.Kind.PICTURE,PlaybackPolicy.kind(4004));
        assertEquals(PlaybackPolicy.Kind.SOUND,PlaybackPolicy.kind(5001));assertEquals(PlaybackPolicy.Kind.PROTECTED,PlaybackPolicy.kind(6001));
        assertEquals(PlaybackPolicy.Kind.OTHER,PlaybackPolicy.kind(1000));
        assertTrue(PlaybackPolicy.retryable(PlaybackPolicy.Kind.NETWORK));assertTrue(PlaybackPolicy.retryable(PlaybackPolicy.Kind.STALLED));
        assertFalse("a format the TV can't play won't change on retry",PlaybackPolicy.retryable(PlaybackPolicy.Kind.PICTURE));
    }
    @Test public void messagesArePlainLanguage(){
        for(PlaybackPolicy.Kind k:PlaybackPolicy.Kind.values()){String m=PlaybackPolicy.message(k);
            assertFalse(m.isEmpty());
            for(String jargon:new String[]{"codec","HTTP","ERROR_","Exception","DLNA","UPnP","://","Media3","decoder"})assertFalse(k+": "+m,m.toLowerCase().contains(jargon.toLowerCase()));
            assertFalse("no false success: "+m,m.toLowerCase().contains("playing now"));}
    }
    static PlaybackDiagnostics.Track track(int type,boolean supported){return new PlaybackDiagnostics.Track(type,"x/y","","",-1,-1,-1,-1,-1,supported,supported);}
    @Test public void blackScreenOrSilenceIsNotSuccess(){
        int V=PlaybackDiagnostics.VIDEO,A=PlaybackDiagnostics.AUDIO,T=PlaybackDiagnostics.TEXT;
        assertNull(PlaybackPolicy.unplayable(java.util.Arrays.asList(track(V,true),track(A,true),track(T,false))));
        assertNull("one playable soundtrack is enough",PlaybackPolicy.unplayable(java.util.Arrays.asList(track(V,true),track(A,false),track(A,true))));
        assertEquals(PlaybackPolicy.Kind.PICTURE,PlaybackPolicy.unplayable(java.util.Arrays.asList(track(V,false),track(A,true))));
        assertEquals(PlaybackPolicy.Kind.SOUND,PlaybackPolicy.unplayable(java.util.Arrays.asList(track(V,true),track(A,false))));
        assertNull("nothing known yet",PlaybackPolicy.unplayable(java.util.Collections.emptyList()));
    }
}
