package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
public class NowPlayingTest {
    private static NowPlaying track(int state, long position, long updated, float speed, long duration) {
        return new NowPlaying("com.amazon.music.tv", "Song", "Artist", "Album", duration, state, position, updated, speed, 0);
    }
    @Test public void clockIsMinutesAndSecondsOrHours() {
        assertEquals("0:00", NowPlaying.clock(0));
        assertEquals("3:07", NowPlaying.clock(187_999));
        assertEquals("59:59", NowPlaying.clock(3_599_000));
        assertEquals("1:02:03", NowPlaying.clock(3_723_000));
        assertEquals("", NowPlaying.clock(-1));
    }
    @Test public void positionMovesOnWithElapsedTimeAndSpeedWhilePlaying() {
        assertEquals(15_000, track(NowPlaying.STATE_PLAYING, 10_000, 1_000, 1f, 200_000).position(6_000));
        assertEquals(20_000, track(NowPlaying.STATE_PLAYING, 10_000, 1_000, 2f, 200_000).position(6_000));
        assertEquals(10_000, track(NowPlaying.STATE_PAUSED, 10_000, 1_000, 1f, 200_000).position(60_000));
        assertEquals(10_000, track(NowPlaying.STATE_BUFFERING, 10_000, 1_000, 1f, 200_000).position(60_000));
    }
    @Test public void positionIsClampedAndUnknownStaysUnknown() {
        assertEquals(200_000, track(NowPlaying.STATE_PLAYING, 190_000, 1_000, 1f, 200_000).position(900_000));
        assertEquals(0, track(NowPlaying.STATE_REWINDING, 1_000, 1_000, -4f, 200_000).position(9_000));
        assertEquals(-1, track(NowPlaying.STATE_PLAYING, -1, 1_000, 1f, 200_000).position(9_000));
        assertEquals(1f, track(NowPlaying.STATE_PLAYING, 0, 0, Float.NaN, 0).speed, 0f);
    }
    @Test public void fractionIsBounded() {
        assertEquals(0f, NowPlaying.fraction(5_000, 0), 0f);
        assertEquals(0f, NowPlaying.fraction(-1, 10_000), 0f);
        assertEquals(.5f, NowPlaying.fraction(5_000, 10_000), 1e-6f);
        assertEquals(1f, NowPlaying.fraction(12_000, 10_000), 0f);
    }
    @Test public void stringsAreOneBoundedLine() {
        assertEquals("", NowPlaying.bounded(null, 10));
        assertEquals("A B C", NowPlaying.bounded("  A\n\tB\u0000  C  ", 10));
        assertEquals("abcdefghi…", NowPlaying.bounded("abcdefghijklmnop", 10));
        assertEquals("abcdefghij", NowPlaying.bounded("abcdefghij", 10));
        String longTitle = new String(new char[500]).replace('\0', 'x');
        assertEquals(NowPlaying.TITLE_MAX, new NowPlaying("p", longTitle, "", "", 0, 3, 0, 0, 1f, 0).title.length());
    }
    @Test public void amazonPlayingWinsThenAnyPlayingThenAmazonPaused() {
        String self = "io.unifiedtv.preview";
        assertEquals(1, NowPlaying.pick(new String[]{"org.other", "com.amazon.mp3"}, new int[]{3, 3}, self));
        assertEquals(0, NowPlaying.pick(new String[]{"org.other", "com.amazon.mp3"}, new int[]{3, 2}, self));
        assertEquals(1, NowPlaying.pick(new String[]{"org.other", "com.amazon.music.tv"}, new int[]{2, 2}, self));
        assertEquals(-1, NowPlaying.pick(new String[]{"org.other"}, new int[]{2}, self));
        assertEquals(-1, NowPlaying.pick(new String[]{"com.amazon.mp3"}, new int[]{1}, self));
        assertEquals(-1, NowPlaying.pick(new String[]{self}, new int[]{3}, self));
        assertEquals(-1, NowPlaying.pick(new String[0], new int[0], self));
    }
    @Test public void actionsAreOfferedOnlyWhenDeclaredOrUndeclared() {
        NowPlaying none = track(3, 0, 0, 1f, 0);
        assertTrue(none.offers(NowPlaying.ACTION_SKIP_TO_NEXT));
        NowPlaying some = new NowPlaying("p", "t", "", "", 0, 3, 0, 0, 1f, NowPlaying.ACTION_PLAY | NowPlaying.ACTION_PAUSE | NowPlaying.ACTION_SKIP_TO_NEXT);
        assertTrue(some.offers(NowPlaying.ACTION_SKIP_TO_NEXT));
        assertFalse(some.offers(NowPlaying.ACTION_SKIP_TO_PREVIOUS));
    }
    @Test public void shuffleOnlyWhenTheAppOffersIt() {
        assertEquals(1, NowPlaying.shuffleAction(new String[]{"thumbs_up", "com.amazon.SHUFFLE"}, new String[]{"Like", "Mix"}));
        assertEquals(0, NowPlaying.shuffleAction(new String[]{"a1"}, new String[]{"Shuffle on"}));
        assertEquals(-1, NowPlaying.shuffleAction(new String[]{"repeat", null}, new String[]{"Repeat"}));
    }
    @Test public void newTrackHasANewKeySoOldArtIsDropped() {
        NowPlaying a = new NowPlaying("p", "One", "X", "Y", 0, 3, 0, 0, 1f, 0), b = new NowPlaying("p", "Two", "X", "Y", 0, 3, 0, 0, 1f, 0);
        assertNotEquals(a.trackKey(), b.trackKey());
        assertEquals(a.trackKey(), new NowPlaying("p", "One", "X", "Y", 9, 2, 5, 5, 1f, 0).trackKey());
    }
    @Test public void playingStatesAndAdbLine() {
        assertTrue(NowPlaying.playing(NowPlaying.STATE_PLAYING));
        assertTrue(NowPlaying.playing(NowPlaying.STATE_BUFFERING));
        assertFalse(NowPlaying.playing(NowPlaying.STATE_PAUSED));
        assertFalse(NowPlaying.alive(NowPlaying.STATE_ERROR));
        assertEquals("adb shell cmd notification allow_listener io.unifiedtv.preview/io.unifiedtv.preview.MediaWatch",
                NowPlaying.adbLine("io.unifiedtv.preview/io.unifiedtv.preview.MediaWatch"));
    }
}
