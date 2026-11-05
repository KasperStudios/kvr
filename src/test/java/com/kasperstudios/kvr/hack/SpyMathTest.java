package com.kasperstudios.kvr.hack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SpyMathTest {

    private static final Vec3 EYE = Vec3.ZERO;
    private static final Vec3 LOOK = new Vec3(0, 0, 1);

    @Test
    void straightAheadScoresHighest() {
        assertEquals(1.0, SpyMath.score(EYE, LOOK, new Vec3(0, 0, 10), 32, 8), 1e-9);
    }

    @Test
    void outsideConeOrRangeIsRejected() {
        assertEquals(-2, SpyMath.score(EYE, LOOK, new Vec3(5, 0, 10), 32, 8), 1e-9); // ~26.6 degrees
        assertEquals(-2, SpyMath.score(EYE, LOOK, new Vec3(0, 0, 40), 32, 8), 1e-9);
        assertEquals(-2, SpyMath.score(EYE, LOOK, new Vec3(0, 0, -10), 32, 8), 1e-9);
    }

    @Test
    void slightlyOffCentreStillCounts() {
        double s = SpyMath.score(EYE, LOOK, new Vec3(1, 0, 10), 32, 8); // ~5.7 degrees
        assertTrue(s > Math.cos(Math.toRadians(8)) && s < 1);
    }
}
