package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Seam: what actually gets written onto the glasses item, and what comes back out of it. */
class GlassesCodecTest {

    private static GlassesBuild build(ComponentTier chassis, ComponentTier parts) {
        Map<ComponentType, ComponentTier> map = new EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) {
            map.put(type, parts);
        }
        return new GlassesBuild(chassis, map);
    }

    @Test
    void roundTripsEveryTier() {
        GlassesBuild original = build(ComponentTier.TIER_5_FRONTIER, ComponentTier.TIER_5_FRONTIER);

        GlassesBuild restored = GlassesCodec.fromInts(GlassesCodec.toInts(original));

        assertEquals(GlassesBuildTest.parts(ComponentTier.TIER_5_FRONTIER), toMap(restored));
        assertEquals(ComponentTier.TIER_5_FRONTIER, restored.chassisTier());
        assertEquals(original.fps(), restored.fps());
        assertEquals(original.batteryCapacityFe(), restored.batteryCapacityFe());
    }

    @Test
    void keepsMixedTierBuilds() {
        Map<ComponentType, ComponentTier> mixed = GlassesBuildTest.parts(ComponentTier.TIER_4_NORMAL);
        mixed.put(ComponentType.RAM, ComponentTier.TIER_1_BOMZH);
        GlassesBuild original = new GlassesBuild(ComponentTier.TIER_4_NORMAL, mixed);

        GlassesBuild restored = GlassesCodec.fromInts(GlassesCodec.toInts(original));

        assertEquals(ComponentTier.TIER_1_BOMZH, restored.tierOf(ComponentType.RAM));
        assertEquals(ComponentTier.TIER_4_NORMAL, restored.tierOf(ComponentType.CPU));
        assertEquals(1, restored.maxWindows(), "RAM tier 1 gives exactly one window");
    }

    @Test
    void rejectsMalformedData() {
        assertFalse(GlassesCodec.isValid(null));
        assertFalse(GlassesCodec.isValid(new int[2]));
        assertFalse(GlassesCodec.isValid(new int[]{9, 1, 1, 1, 1, 1}), "unknown tier level");
        assertFalse(GlassesCodec.isValid(new int[]{1, 5, 5, 5, 5, 5}), "parts above chassis");
        assertThrows(IllegalArgumentException.class, () -> GlassesCodec.fromInts(new int[3]));
    }

    static Map<ComponentType, ComponentTier> toMap(GlassesBuild build) {
        Map<ComponentType, ComponentTier> map = new EnumMap<>(ComponentType.class);
        for (Map.Entry<ComponentType, ComponentTier> entry : build.parts()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
