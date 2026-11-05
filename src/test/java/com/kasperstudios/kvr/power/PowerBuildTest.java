package com.kasperstudios.kvr.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowerBuildTest {

    private static PowerBuild all(int tier) {
        return new PowerBuild(tier, tier, tier, tier, tier);
    }

    @Test
    void drawIsStandbyPlusFourModules() {
        assertEquals(1 + 4 * 2, all(1).draw(PowerState.ON), 1e-9);
        assertEquals(1 + 4 * 90, all(5).draw(PowerState.ON), 1e-9);
    }

    @Test
    void hudOffCutsSeventyPercentAndOffIsZero() {
        assertEquals(9 * 0.3, all(1).draw(PowerState.HUD_OFF), 1e-9);
        assertEquals(0, all(5).draw(PowerState.OFF), 1e-9);
    }

    @Test
    void matchingTiersNeverOverload() {
        for (int t = 1; t <= 5; t++) assertFalse(all(t).overloads(PowerState.ON), "tier " + t);
    }

    @Test
    void topPartsOnWeakCellOverload() {
        assertTrue(new PowerBuild(5, 5, 5, 5, 1).overloads(PowerState.ON));
        assertTrue(new PowerBuild(4, 4, 4, 4, 3).overloads(PowerState.ON));
    }

    @Test
    void coldBootDependsOnCpu() {
        assertEquals(100, new PowerBuild(1, 1, 1, 1, 1).coldBootTicks());
        assertEquals(20, new PowerBuild(5, 1, 1, 1, 1).coldBootTicks());
    }

    @Test
    void rejectsTierOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new PowerBuild(0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PowerBuild(1, 1, 1, 1, 6));
    }
}
