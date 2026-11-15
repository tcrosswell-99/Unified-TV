package io.unifiedtv.preview;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Plain words and choices for setup: Home Assistant's one status line, this TV's rooms, and the team search. */
public class SetupWordsTest {
    @Test public void homeAssistantStatusIsOnePlainLineAndNeverConnectedByGuess() {
        assertEquals("Home Assistant: connected", HaStatus.line(true, HaStatus.AUTHORISED, null));
        assertEquals("Home Assistant: checking…", HaStatus.line(true, null, null));
        assertEquals("Home Assistant: needs you to sign in again", HaStatus.line(true, HaStatus.AUTH_REQUIRED, null));
        assertEquals("Home Assistant: can't be reached right now", HaStatus.line(true, HaStatus.OFFLINE, true));
        assertEquals("Home Assistant: found on your network — not set up", HaStatus.line(false, null, true));
        assertEquals("Home Assistant: not found", HaStatus.line(false, null, false));
        assertEquals("Home Assistant: looking on your home network…", HaStatus.line(false, null, null));
        assertEquals("a saved record that can't be used counts as not set up", "Home Assistant: found on your network — not set up", HaStatus.line(true, HaStatus.NOT_CONFIGURED, true));
        for (String s : new String[]{HaStatus.NOT_CONFIGURED, HaStatus.REACHABLE, HaStatus.AUTH_REQUIRED, HaStatus.OFFLINE})
            assertNotEquals(s, "Home Assistant: connected", HaStatus.line(true, s, true));
        for (Boolean f : new Boolean[]{null, true, false}) for (String c : new String[]{null, HaStatus.AUTHORISED}) {
            String line = HaStatus.line(f != null && f, c, f).toLowerCase(Locale.ROOT);
            for (String jargon : new String[]{"entity", "token", "http", "binary", "script", "authoris", "lan"}) assertFalse(line + " says " + jargon, line.contains(jargon));
        }
    }

    @Test public void onlyThisTvsRoomsAndItemsAreOffered() {
        assertEquals("tcl", HouseholdPage.room("", "TCL"));
        assertEquals("sony", HouseholdPage.room("", "Sony"));
        assertEquals("a Frame saved by an older version is never used on Android", "tcl", HouseholdPage.room("frame", "TCL"));
        assertEquals("sony", HouseholdPage.room("sony", "TCL"));
        for (String[] r : HaEntities.ANDROID_ROOMS) assertNotEquals("frame", r[0]);
        for (String key : new String[]{"tvplus", "art", "ringEnter", "ringExit"}) for (String room : new String[]{"tcl", "sony", "frame"}) assertFalse(key, HouseholdPage.visible(key, room));
        assertTrue(HouseholdPage.visible("xbox", "sony")); assertFalse(HouseholdPage.visible("xbox", "tcl"));
        for (String[] item : HouseholdPage.ITEMS) {
            String words = item[1].toLowerCase(Locale.ROOT);
            for (String jargon : new String[]{"entity", "binary", "sensor", "script", "hdmi", "owner"}) assertFalse(item[1], words.contains(jargon));
        }
    }

    private static JSONObject catalogue() throws Exception {
        return new JSONObject("{\"leagues\":[{\"id\":39,\"name\":\"Premier League\",\"country\":\"England\",\"teams\":[{\"id\":47,\"name\":\"Tottenham\",\"code\":\"TOT\"},{\"id\":42,\"name\":\"Arsenal\",\"code\":\"ARS\"},{\"id\":33,\"name\":\"Manchester United\",\"code\":\"MUN\"},{\"id\":50,\"name\":\"Manchester City\",\"code\":\"MCI\"}]},"
            + "{\"id\":179,\"name\":\"Premiership\",\"country\":\"Scotland\",\"teams\":[{\"id\":254,\"name\":\"Heart Of Midlothian\",\"code\":\"HEA\"},{\"id\":247,\"name\":\"Celtic\",\"code\":\"CEL\"}]},"
            + "{\"id\":40,\"name\":\"Championship\",\"country\":\"England\"},"
            + "{\"id\":408,\"name\":\"Premiership\",\"country\":\"Northern-Ireland\",\"teams\":[{\"id\":1,\"name\":\"Linfield\",\"code\":null}]}]}");
    }

    @Test public void teamSearchFindsByFirstLettersWordsAndCode() throws Exception {
        JSONObject cat = catalogue();
        assertEquals("leagues not read yet are left out", 3, TeamList.leagues(cat).size());
        assertEquals(Collections.singletonList("Tottenham"), names(TeamList.search(cat, "tot", 30)));
        assertEquals(Arrays.asList("Manchester City", "Manchester United"), names(TeamList.search(cat, "man", 30)));
        assertEquals("a later word", Collections.singletonList("Manchester United"), names(TeamList.search(cat, "united", 30)));
        assertEquals("the short code", Collections.singletonList("Heart Of Midlothian"), names(TeamList.search(cat, "HEA", 30)));
        assertEquals("letters inside a name come after", "Celtic", TeamList.search(cat, "elt", 30).get(0).name);
        assertEquals("the league is carried", "Premiership", TeamList.search(cat, "linf", 30).get(0).league);
        assertTrue(TeamList.search(cat, "  ", 30).isEmpty()); assertTrue(TeamList.search(cat, "zzz", 30).isEmpty());
        assertEquals(1, TeamList.search(cat, "m", 1).size());
        assertEquals("atletico madrid", TeamList.fold("Atlético  Madrid!"));
    }
    private static List<String> names(List<TeamList.Entry> e) { List<String> out = new ArrayList<>(); for (TeamList.Entry x : e) out.add(x.name); return out; }
}
