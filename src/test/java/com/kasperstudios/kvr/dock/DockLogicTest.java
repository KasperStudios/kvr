package com.kasperstudios.kvr.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DockLogicTest {

    @Test
    void redstoneGivesOneSolarFourInDaylight() {
        assertEquals(0, DockLogic.generation(false, false, true), 1e-9);
        assertEquals(1, DockLogic.generation(true, false, false), 1e-9);
        assertEquals(4, DockLogic.generation(false, true, true), 1e-9);
        assertEquals(0, DockLogic.generation(false, true, false), 1e-9);
        assertEquals(5, DockLogic.generation(true, true, true), 1e-9);
    }

    @Test
    void bufferIsCappedAtFourThousand() {
        assertEquals(4000, DockLogic.fill(3999, 4), 1e-9);
        assertEquals(10, DockLogic.fill(5, 5), 1e-9);
    }

    @Test
    void offersFiftyPerTickButNotMoreThanStored() {
        assertEquals(1000, DockLogic.offer(4000, 20), 1e-9);
        assertEquals(300, DockLogic.offer(300, 20), 1e-9);
    }

    @Test
    void cablesFillOnlyFreeRoom() {
        assertEquals(500, DockLogic.accept(0, 500));
        assertEquals(100, DockLogic.accept(3900, 500));
        assertEquals(0, DockLogic.accept(4000, 500));
    }

    @Test
    void sunnyNeedsDaySkyAndNoRain() {
        assertTrue(DockLogic.sunny(6000, false, true));
        assertFalse(DockLogic.sunny(18000, false, true));
        assertFalse(DockLogic.sunny(6000, true, true));
        assertFalse(DockLogic.sunny(6000, false, false));
        assertTrue(DockLogic.sunny(24000L * 3 + 100, false, true));
    }
}
