package com.kasperstudios.kvr.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PowerDataTest {

    @Test
    void saveLoadRoundTrip() {
        PowerData d = new PowerData();
        d.charge = 1234.5;
        d.wear = 0.25;
        d.state = PowerState.HUD_OFF;
        d.bootUntil = 77;
        d.overloadDeadline = 900;
        d.lastStep = 880;
        PowerData r = PowerData.load(d.save());
        assertEquals(1234.5, r.charge, 1e-9);
        assertEquals(0.25, r.wear, 1e-9);
        assertEquals(PowerState.HUD_OFF, r.state);
        assertEquals(77, r.bootUntil);
        assertEquals(900, r.overloadDeadline);
        assertEquals(880, r.lastStep);
    }

    @Test
    void freshGlassesAreOffAndEmpty() {
        PowerData r = PowerData.load(new net.minecraft.nbt.CompoundTag());
        assertEquals(PowerState.OFF, r.state);
        assertEquals(0, r.charge, 1e-9);
        assertEquals(PowerData.NONE, r.overloadDeadline);
    }

    @Test
    void wearShrinksCapacityAndPercent() {
        PowerBuild b = new PowerBuild(1, 1, 1, 1, 1);
        PowerData d = new PowerData();
        d.wear = 0.5;
        d.charge = 250;
        assertEquals(500, d.capacity(b), 1e-9);
        assertEquals(50, d.percent(b));
    }

    @Test
    void temperatureRisesToDetonationPoint() {
        PowerData d = new PowerData();
        d.overloadDeadline = 600;
        assertEquals(20, d.temperature(0), 1e-9);
        assertEquals(140, d.temperature(600), 1e-9);
    }
}
