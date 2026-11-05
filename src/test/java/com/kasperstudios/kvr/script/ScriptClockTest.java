package com.kasperstudios.kvr.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScriptClockTest {

    private static final long MS = 1_000_000L;

    @Test
    void cpuTierSetsTheRate() {
        assertEquals(10, ScriptClock.hz(1));
        assertEquals(200, ScriptClock.hz(5));
        assertEquals(100 * MS, new ScriptClock(1).intervalNanos());
        assertEquals(5 * MS, new ScriptClock(5).intervalNanos());
        assertThrows(IllegalArgumentException.class, () -> ScriptClock.hz(6));
    }

    @Test
    void runsOneTickPerInterval() {
        ScriptClock c = new ScriptClock(1); // 100 ms
        assertEquals(1, c.ticksDue(0));
        assertEquals(0, c.ticksDue(50 * MS));
        assertEquals(1, c.ticksDue(100 * MS));
        assertEquals(2, c.ticksDue(300 * MS));
    }

    @Test
    void lagSpikeIsCappedAndBacklogDropped() {
        ScriptClock c = new ScriptClock(5); // 5 ms
        c.ticksDue(0);
        assertEquals(ScriptClock.MAX_CATCH_UP, c.ticksDue(1000 * MS));
        assertEquals(0, c.ticksDue(1001 * MS));
        assertEquals(1, c.ticksDue(1005 * MS));
    }
}
