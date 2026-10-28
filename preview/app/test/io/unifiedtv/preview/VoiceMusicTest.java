package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

/** OD-27 voice: "play …" becomes Amazon Music play by name, on this profile's Play on choice, Everywhere or a named Echo. */
public class VoiceMusicTest {
    private static VoiceCommand.Music m(String s) { return VoiceCommand.music(s); }

    @Test public void playAndSomethingIsMusic() {
        assertEquals("music", VoiceCommand.classify("Play Bohemian Rhapsody"));
        assertEquals("Bohemian Rhapsody", m("Play Bohemian Rhapsody.").query);
        assertEquals("", m("play jazz").place);
        assertEquals(AmazonMusic.Focus.ANY, m("play jazz").focus);
        assertNull("just “play” is not a request", m("play"));
        assertNull(m("find Casablanca"));
    }
    @Test public void onAmazonMusicIsTheSameRequest() {
        VoiceCommand.Music x = m("play Adele on Amazon Music");
        assertEquals("Adele", x.query); assertEquals("", x.place);
    }
    @Test public void whereItPlays() {
        assertEquals(Echoes.EVERYWHERE, m("play jazz everywhere").place);
        assertEquals("jazz", m("play jazz everywhere").query);
        assertEquals(Echoes.EVERYWHERE, m("play jazz on Amazon Music everywhere").place);
        VoiceCommand.Music k = m("play the Beatles on the kitchen echo");
        assertEquals("the Beatles", k.query); assertEquals("the kitchen echo", k.place);
        assertEquals(Echoes.TV, m("play Abba on this TV").place);
        assertEquals(Echoes.TV, m("play Abba here").place);
    }
    @Test public void whatKindOfThing() {
        assertEquals(AmazonMusic.Focus.ALBUM, m("play the album Rumours").focus); assertEquals("Rumours", m("play the album Rumours").query);
        assertEquals(AmazonMusic.Focus.PLAYLIST, m("play my playlist Friday").focus);
        assertEquals(AmazonMusic.Focus.PLAYLIST, m("play Friday Mix playlist").focus); assertEquals("Friday Mix", m("play Friday Mix playlist").query);
        assertEquals(AmazonMusic.Focus.ARTIST, m("play songs by Queen").focus); assertEquals("Queen", m("play songs by Queen").query);
        assertEquals(AmazonMusic.Focus.SONG, m("play the song Yesterday").focus);
    }
    @Test public void titlesWithOnKeepTheWholePhraseUntilAnEchoIsFound() {
        VoiceCommand.Music carry = m("play carry on wayward son");
        assertTrue("“wayward son” is only a possible Echo name", carry.maybeEcho());
        assertEquals("carry on wayward son", carry.whole);
        VoiceCommand.Music shine = m("Play Shine On You Crazy Diamond.");
        assertTrue(shine.maybeEcho()); assertEquals("Shine On You Crazy Diamond", shine.whole);
        assertEquals("the album, kept whole", AmazonMusic.Focus.ALBUM, m("play the album shine on").wholeFocus);
        assertFalse("everywhere and this TV are places for sure", m("play jazz everywhere").maybeEcho() || m("play jazz on the TV").maybeEcho());
        assertEquals("no “on”: the whole phrase", "carry", m("play carry").whole);
    }
    @Test public void otherAppsAndLiveTvAreNeverMusic() {
        assertNotEquals("music", VoiceCommand.classify("play Stranger Things on Netflix"));
        assertNull(m("play Bluey on BBC iPlayer"));
        assertNotEquals("music", VoiceCommand.classify("play live TV"));
        assertNull(m("play live tv everywhere"));
    }
}
