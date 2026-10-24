package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class RaceWeekendTest {

    // The 2026 United States GP, times as Jolpica gives them (UTC).
    private static final RaceWeekend.Race AUSTIN = new RaceWeekend.Race(18, "United States Grand Prix", "Austin",
            Arrays.asList(
                    // Out of order on purpose: the calendar is sorted by start time.
                    new RaceWeekend.Session("race", Instant.parse("2026-10-25T20:00:00Z")),
                    new RaceWeekend.Session("FP1", Instant.parse("2026-10-23T17:30:00Z")),
                    new RaceWeekend.Session("FP2", Instant.parse("2026-10-23T21:00:00Z")),
                    new RaceWeekend.Session("FP3", Instant.parse("2026-10-24T17:30:00Z")),
                    new RaceWeekend.Session("Qualifying", Instant.parse("2026-10-24T21:00:00Z"))));
    private static final List<RaceWeekend.Race> CALENDAR = Collections.singletonList(AUSTIN);

    @Test
    public void nothingBeforeTheWeekendOpens() {
        assertNull(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-23T05:29:00Z")));
    }

    @Test
    public void opensTwelveHoursBeforeFirstPractice() {
        RaceWeekend.State s = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-23T05:30:00Z"));
        assertSame(AUSTIN, s.race);
        assertEquals("FP1", s.next.name);
        assertEquals(Duration.ofHours(12), s.untilNext);
        assertEquals(RaceWeekend.Status.NEXT, s.statuses.get(0));
        assertEquals(RaceWeekend.Status.LATER, s.statuses.get(4));
    }

    @Test
    public void aSessionOnTrackIsLiveAndTheNextOneIsCountedDown() {
        RaceWeekend.State s = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-24T21:30:00Z"));
        assertEquals("QUALIFYING", s.live.name);
        assertTrue(s.next.isRace());
        assertEquals(Arrays.asList(RaceWeekend.Status.DONE, RaceWeekend.Status.DONE, RaceWeekend.Status.DONE,
                RaceWeekend.Status.LIVE, RaceWeekend.Status.NEXT), s.statuses);
    }

    @Test
    public void theRaceIsLiveForTwoHoursThenFinished() {
        RaceWeekend.State live = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T21:59:00Z"));
        assertTrue(live.live.isRace());
        assertNull(live.next);
        RaceWeekend.State done = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T22:00:00Z"));
        assertTrue(done.finished());
    }

    @Test
    public void closesFourHoursAfterTheRaceStarts() {
        assertTrue(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T23:59:00Z")).finished());
        assertNull(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-26T00:00:00Z")));
    }

    @Test
    public void aSessionStartIsReadInUtc() {
        assertEquals(Instant.parse("2026-10-23T17:30:00Z"), RaceWeekend.sessionStart("2026-10-23T17:30:00Z"));
        assertEquals(RaceWeekend.EARLIEST, RaceWeekend.sessionStart("2000-01-01T00:00:00Z"));
        assertEquals(Instant.parse("2100-12-31T23:59:59Z"), RaceWeekend.sessionStart("2100-12-31T23:59:59Z"));
    }

    @Test
    public void aStartTheClockCannotShowIsLeftOut() {
        // Instant.parse takes these, but formatting them for the panel throws on every resume.
        assertNull(RaceWeekend.sessionStart("+999999999-12-31T23:59:59Z"));
        assertNull(RaceWeekend.sessionStart("-999999999-01-01T00:00:00Z"));
        assertNull(RaceWeekend.sessionStart("1999-12-31T23:59:59Z"));
        assertNull(RaceWeekend.sessionStart("2101-01-01T00:00:00Z"));
        assertNull(RaceWeekend.sessionStart("2026-10-23 17:30"));
        assertNull(RaceWeekend.sessionStart(""));
    }

}
