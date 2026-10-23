package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Seam: the influence matrix from SPEC 2.5 - what a given component tier actually means at runtime. */
class HardwareTableTest {

    @Test
    void cpuControlsScriptTickRateAndResponsiveness() {
        assertEquals(10, HardwareTable.of(ComponentType.CPU, ComponentTier.TIER_1_BOMZH).scriptTickHz());
        assertEquals(100, HardwareTable.of(ComponentType.CPU, ComponentTier.TIER_1_BOMZH).scriptTickMillis());
        assertEquals(200, HardwareTable.of(ComponentType.CPU, ComponentTier.TIER_5_FRONTIER).scriptTickHz());
        assertEquals(5, HardwareTable.of(ComponentType.CPU, ComponentTier.TIER_5_FRONTIER).scriptTickMillis());
    }

    @Test
    void gpuControlsFrameRate() {
        assertEquals(15, HardwareTable.of(ComponentType.GPU, ComponentTier.TIER_1_BOMZH).fps());
        assertEquals(30, HardwareTable.of(ComponentType.GPU, ComponentTier.TIER_2_BEDNYAK).fps());
        assertEquals(60, HardwareTable.of(ComponentType.GPU, ComponentTier.TIER_3_SREDNYAK).fps());
        assertEquals(90, HardwareTable.of(ComponentType.GPU, ComponentTier.TIER_4_NORMAL).fps());
        assertEquals(144, HardwareTable.of(ComponentType.GPU, ComponentTier.TIER_5_FRONTIER).fps());
    }

    @Test
    void ramControlsSimultaneousAppWindows() {
        assertEquals(1, HardwareTable.of(ComponentType.RAM, ComponentTier.TIER_1_BOMZH).maxWindows());
        assertEquals(2, HardwareTable.of(ComponentType.RAM, ComponentTier.TIER_2_BEDNYAK).maxWindows());
        assertEquals(4, HardwareTable.of(ComponentType.RAM, ComponentTier.TIER_3_SREDNYAK).maxWindows());
        assertEquals(8, HardwareTable.of(ComponentType.RAM, ComponentTier.TIER_4_NORMAL).maxWindows());
        assertEquals(16, HardwareTable.of(ComponentType.RAM, ComponentTier.TIER_5_FRONTIER).maxWindows());
    }

    @Test
    void storageControlsVirtualDiskSize() {
        assertEquals(256L * 1024, HardwareTable.of(ComponentType.STORAGE, ComponentTier.TIER_1_BOMZH).storageBytes());
        assertEquals(1024L * 1024, HardwareTable.of(ComponentType.STORAGE, ComponentTier.TIER_2_BEDNYAK).storageBytes());
        assertEquals(8L * 1024 * 1024, HardwareTable.of(ComponentType.STORAGE, ComponentTier.TIER_3_SREDNYAK).storageBytes());
        assertEquals(64L * 1024 * 1024, HardwareTable.of(ComponentType.STORAGE, ComponentTier.TIER_4_NORMAL).storageBytes());
        assertEquals(512L * 1024 * 1024, HardwareTable.of(ComponentType.STORAGE, ComponentTier.TIER_5_FRONTIER).storageBytes());
    }

    @Test
    void batteryControlsCapacityAndSafeDischarge() {
        assertEquals(1_000, HardwareTable.of(ComponentType.BATTERY, ComponentTier.TIER_1_BOMZH).batteryCapacityFe());
        assertEquals(10, HardwareTable.of(ComponentType.BATTERY, ComponentTier.TIER_1_BOMZH).maxDischargeFePerTick());
        assertEquals(500_000, HardwareTable.of(ComponentType.BATTERY, ComponentTier.TIER_5_FRONTIER).batteryCapacityFe());
        assertEquals(500, HardwareTable.of(ComponentType.BATTERY, ComponentTier.TIER_5_FRONTIER).maxDischargeFePerTick());
    }

    @Test
    void aComponentOnlyFillsItsOwnColumn() {
        HardwareSpec ram = HardwareTable.of(ComponentType.RAM, ComponentTier.TIER_3_SREDNYAK);
        assertEquals(4, ram.maxWindows());
        assertEquals(0, ram.fps());
        assertEquals(0, ram.scriptTickHz());
        assertEquals(0, ram.storageBytes());
        assertEquals(0, ram.batteryCapacityFe());

        HardwareSpec cpu = HardwareTable.of(ComponentType.CPU, ComponentTier.TIER_3_SREDNYAK);
        assertEquals(50, cpu.scriptTickHz());
        assertEquals(0, cpu.maxWindows());
        assertEquals(0, cpu.fps());
    }
}
