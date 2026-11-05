package com.kasperstudios.kvr.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PartBurnTest {

    private static final List<String> PARTS = List.of("chassis", "cpu", "gpu", "ram", "storage", "battery");

    @Test
    void chassisAndBatteryNeverSurvive() {
        assertEquals(List.of("cpu", "gpu", "ram", "storage"), PartBurn.survivors(PARTS, () -> 0.99));
    }

    @Test
    void everythingBurnsOnLowRolls() {
        assertEquals(List.of(), PartBurn.survivors(PARTS, () -> 0.1));
    }

    @Test
    void eachModuleRollsSeparately() {
        double[] rolls = {0.9, 0.1, 0.5, 0.49};
        int[] i = {0};
        assertEquals(List.of("cpu", "ram"), PartBurn.survivors(PARTS, () -> rolls[i[0]++]));
    }
}
