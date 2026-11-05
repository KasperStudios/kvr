package com.kasperstudios.kvr.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WatchdogTest {

    private static final long MS = 1_000_000L;

    @Test
    void budgetIsQuarterOfTheTick() {
        assertEquals(25 * MS, new Watchdog(new ScriptClock(1)).budgetNanos());
        assertEquals(1_250_000L, new Watchdog(new ScriptClock(5)).budgetNanos());
    }

    @Test
    void threeSlowTicksInARowMeanNotResponding() {
        Watchdog w = new Watchdog(new ScriptClock(1));
        assertEquals(Watchdog.Verdict.SLOW, w.record(30 * MS));
        assertEquals(Watchdog.Verdict.SLOW, w.record(30 * MS));
        assertEquals(Watchdog.Verdict.NOT_RESPONDING, w.record(30 * MS));
    }

    @Test
    void aGoodTickResetsTheStrikes() {
        Watchdog w = new Watchdog(new ScriptClock(1));
        w.record(30 * MS);
        w.record(30 * MS);
        assertEquals(Watchdog.Verdict.OK, w.record(10 * MS));
        assertEquals(Watchdog.Verdict.SLOW, w.record(30 * MS));
    }

    @Test
    void runawayTickIsKilledAtFourTimesTheBudget() {
        Watchdog w = new Watchdog(new ScriptClock(1));
        assertEquals(100 * MS, w.killAfterNanos());
        assertEquals(Watchdog.Verdict.KILL, w.record(101 * MS));
    }
}
