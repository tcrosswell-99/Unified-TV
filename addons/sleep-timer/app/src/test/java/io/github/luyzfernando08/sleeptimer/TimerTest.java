package io.github.luyzfernando08.sleeptimer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimerTest {
    private static final long END = 1_700_000_000_000L;

    @Test
    public void noTimerDoesNothing() {
        assertEquals(Timer.Due.NOTHING, Timer.due(Timer.ACTION_WARN, 0, END));
        assertEquals(Timer.Due.NOTHING, Timer.due(Timer.ACTION_SLEEP, 0, END));
    }

    @Test
    public void warningOnTimeShowsTheCountdown() {
        assertEquals(Timer.Due.WARN, Timer.due(Timer.ACTION_WARN, END, END - Timer.WARN_MS));
        assertEquals(Timer.Due.WARN, Timer.due(Timer.ACTION_WARN, END, END - 1));
    }

    @Test
    public void sleepOnTimeSleeps() {
        assertEquals(Timer.Due.SLEEP, Timer.due(Timer.ACTION_SLEEP, END, END + 2_000));
    }

    @Test
    public void lateSleepAlarmStillSleeps() {
        // Inexact alarms (no exact-alarm permission) can come many minutes after the end.
        assertEquals(Timer.Due.SLEEP, Timer.due(Timer.ACTION_SLEEP, END, END + 90 * 60_000L));
    }

    @Test
    public void warningAfterTheEndGoesStraightToSleep() {
        assertEquals(Timer.Due.SLEEP, Timer.due(Timer.ACTION_WARN, END, END));
        assertEquals(Timer.Due.SLEEP, Timer.due(Timer.ACTION_WARN, END, END + 10 * 60_000L));
    }

    @Test
    public void sleepAlarmBeforeAPushedBackEndWaits() {
        // "15 more minutes" moved the end; an old sleep alarm arriving now must not cut the extra time short.
        assertEquals(Timer.Due.NOTHING, Timer.due(Timer.ACTION_SLEEP, END, END - 60_000));
    }

    @Test
    public void staleEndIsIgnored() {
        assertEquals(Timer.Due.NOTHING, Timer.due(Timer.ACTION_SLEEP, END, END + Timer.STALE_MS + 1));
        assertEquals(Timer.Due.NOTHING, Timer.due(Timer.ACTION_WARN, END, END + Timer.STALE_MS + 1));
    }

    @Test
    public void otherActionsDoNothing() {
        assertEquals(Timer.Due.NOTHING, Timer.due(Timer.ACTION_TEST, END, END + 1));
        assertEquals(Timer.Due.NOTHING, Timer.due(null, END, END - 1));
    }
}
