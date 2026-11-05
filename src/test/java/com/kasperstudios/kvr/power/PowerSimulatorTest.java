package com.kasperstudios.kvr.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowerSimulatorTest {

    private static final PowerBuild SAFE = new PowerBuild(1, 1, 1, 1, 1);
    private static final PowerBuild BOMB = new PowerBuild(5, 5, 5, 5, 1);

    private static PowerData charged(PowerBuild b, PowerState state, double charge) {
        PowerData d = new PowerData();
        d.state = state;
        d.charge = charge;
        d.lastStep = 0;
        return d;
    }

    @Test
    void onDrainsDrawTimesElapsedTicks() {
        PowerData d = charged(SAFE, PowerState.ON, 1000);
        assertEquals(PowerSimulator.Event.NONE, PowerSimulator.step(SAFE, d, 20));
        assertEquals(1000 - 9 * 20, d.charge(), 1e-9);
    }

    @Test
    void hudOffDrainsThirtyPercent() {
        PowerData d = charged(SAFE, PowerState.HUD_OFF, 1000);
        PowerSimulator.step(SAFE, d, 20);
        assertEquals(1000 - 9 * 0.3 * 20, d.charge(), 1e-9);
    }

    @Test
    void offDrawsNothing() {
        PowerData d = charged(SAFE, PowerState.OFF, 500);
        PowerSimulator.step(SAFE, d, 2000);
        assertEquals(500, d.charge(), 1e-9);
    }

    @Test
    void emptyBatteryShutsDown() {
        PowerData d = charged(SAFE, PowerState.ON, 50);
        assertEquals(PowerSimulator.Event.SHUTDOWN_EMPTY, PowerSimulator.step(SAFE, d, 20));
        assertEquals(PowerState.OFF, d.state());
    }

    @Test
    void coldBootTakesCpuTimeAndCannotStartEmpty() {
        PowerData d = charged(SAFE, PowerState.OFF, 1000);
        PowerSimulator.setState(SAFE, d, PowerState.ON, 0);
        assertTrue(d.booting(99));
        assertFalse(d.active(99));
        assertEquals(PowerSimulator.Event.BOOTED, PowerSimulator.step(SAFE, d, 100));
        assertTrue(d.active(100));

        PowerData empty = charged(SAFE, PowerState.OFF, 0);
        PowerSimulator.setState(SAFE, empty, PowerState.ON, 0);
        assertEquals(PowerState.OFF, empty.state());
    }

    @Test
    void overloadDetonatesAfterExactlyThirtySeconds() {
        PowerData d = charged(BOMB, PowerState.ON, 1000);
        assertEquals(PowerSimulator.Event.OVERLOAD_STARTED, PowerSimulator.step(BOMB, d, 20));
        assertEquals(20 + 600, d.overloadDeadline);
        assertEquals(PowerSimulator.Event.NONE, PowerSimulator.step(BOMB, d, 619));
        assertEquals(PowerSimulator.Event.DETONATE, PowerSimulator.step(BOMB, d, 620));
    }

    @Test
    void emptyCellDoesNotStopTheRunaway() {
        PowerData d = charged(BOMB, PowerState.ON, 1000);
        PowerSimulator.step(BOMB, d, 20);
        PowerSimulator.step(BOMB, d, 200);
        assertEquals(0, d.charge(), 1e-9);
        assertTrue(d.overloading());
        assertEquals(PowerState.ON, d.state());
    }

    @Test
    void powerOffSavesButCapacityLossStays() {
        PowerData d = charged(BOMB, PowerState.ON, 1000);
        PowerSimulator.step(BOMB, d, 20);
        PowerSimulator.step(BOMB, d, 320); // halfway
        assertEquals(PowerSimulator.Event.SAVED, PowerSimulator.setState(BOMB, d, PowerState.OFF, 320));
        assertFalse(d.overloading());
        assertEquals(0.5, d.wear(), 1e-9);
        assertEquals(PowerSimulator.Event.NONE, PowerSimulator.step(BOMB, d, 2000));
    }

    @Test
    void hudOffDoesNotDefuse() {
        PowerData d = charged(BOMB, PowerState.ON, 1000);
        PowerSimulator.step(BOMB, d, 20);
        PowerSimulator.setState(BOMB, d, PowerState.HUD_OFF, 40);
        assertTrue(d.overloading());
    }
}
