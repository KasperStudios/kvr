package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Seam: a finished pair of glasses - validated parts plus the aggregate stats and the disassembly contract. */
class GlassesBuildTest {

    static Map<ComponentType, ComponentTier> parts(ComponentTier tier) {
        Map<ComponentType, ComponentTier> parts = new EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) {
            parts.put(type, tier);
        }
        return parts;
    }

    @Test
    void requiresAllFiveComponents() {
        Map<ComponentType, ComponentTier> missingBattery = parts(ComponentTier.TIER_2_BEDNYAK);
        missingBattery.remove(ComponentType.BATTERY);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new GlassesBuild(ComponentTier.TIER_3_SREDNYAK, missingBattery));
        assertTrue(ex.getMessage().contains("BATTERY"), ex.getMessage());
    }

    @Test
    void rejectsPartsAboveTheChassis() {
        assertThrows(IllegalArgumentException.class,
                () -> new GlassesBuild(ComponentTier.TIER_2_BEDNYAK, parts(ComponentTier.TIER_4_NORMAL)));
    }

    @Test
    void aggregatesTheStatsOfTheInstalledParts() {
        GlassesBuild build = new GlassesBuild(ComponentTier.TIER_4_NORMAL, parts(ComponentTier.TIER_3_SREDNYAK));

        assertEquals(60, build.fps());
        assertEquals(4, build.maxWindows());
        assertEquals(8L * 1024 * 1024, build.storageBytes());
        assertEquals(25_000, build.batteryCapacityFe());
        assertEquals(60, build.maxDischargeFePerTick());
        assertEquals(50, build.scriptTickHz());
        assertEquals(20, build.scriptTickMillis());
        assertEquals(ComponentTier.TIER_4_NORMAL, build.chassisTier());
    }

    @Test
    void disassemblyReturnsEveryPartUnchanged() {
        Map<ComponentType, ComponentTier> installed = parts(ComponentTier.TIER_5_FRONTIER);
        installed.put(ComponentType.RAM, ComponentTier.TIER_2_BEDNYAK);
        GlassesBuild build = new GlassesBuild(ComponentTier.TIER_5_FRONTIER, installed);

        List<Map.Entry<ComponentType, ComponentTier>> parts = build.parts();

        assertEquals(5, parts.size());
        assertEquals(ComponentTier.TIER_2_BEDNYAK, build.tierOf(ComponentType.RAM));
        assertEquals(ComponentTier.TIER_5_FRONTIER, build.tierOf(ComponentType.GPU));
        assertEquals(ComponentType.CPU, parts.get(0).getKey(), "part order must be stable for the UI");
    }

    @Test
    void partsCannotBeMutatedFromOutside() {
        Map<ComponentType, ComponentTier> mutable = parts(ComponentTier.TIER_1_BOMZH);
        GlassesBuild build = new GlassesBuild(ComponentTier.TIER_3_SREDNYAK, mutable);

        mutable.put(ComponentType.CPU, ComponentTier.TIER_3_SREDNYAK); // caller keeps a live map, the build must not

        assertEquals(ComponentTier.TIER_1_BOMZH, build.tierOf(ComponentType.CPU));
        assertThrows(UnsupportedOperationException.class, () -> build.parts().clear());
    }
}
