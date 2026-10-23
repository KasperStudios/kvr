package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Seam: what the assembly menu must accept, reject, and never silently lose. */
class AssemblyRulesTest {

    private static Map<ComponentType, ComponentTier> parts(ComponentTier tier) {
        Map<ComponentType, ComponentTier> map = new EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) {
            map.put(type, tier);
        }
        return map;
    }

    @Test
    void assemblesMatchingParts() {
        AssemblyRules.Result result = AssemblyRules.evaluate(ComponentTier.TIER_3_SREDNYAK, parts(ComponentTier.TIER_2_BEDNYAK));

        assertTrue(result.ok(), result.problems().toString());
        assertEquals(ComponentTier.TIER_3_SREDNYAK, result.build().chassisTier());
        assertEquals(ComponentTier.TIER_2_BEDNYAK, result.build().tierOf(ComponentType.RAM));
        assertEquals(30, result.build().fps());
    }

    @Test
    void acceptsPartsOfTheSameTierAsTheChassis() {
        assertTrue(AssemblyRules.evaluate(ComponentTier.TIER_4_NORMAL, parts(ComponentTier.TIER_4_NORMAL)).ok());
    }

    @Test
    void rejectsEverythingWhenTheChassisSlotIsEmpty() {
        AssemblyRules.Result result = AssemblyRules.evaluate(null, parts(ComponentTier.TIER_1_BOMZH));

        assertFalse(result.ok());
        assertEquals(List.of("chassis"), result.problems());
    }

    @Test
    void listsEveryEmptySlot() {
        Map<ComponentType, ComponentTier> partial = parts(ComponentTier.TIER_1_BOMZH);
        partial.remove(ComponentType.GPU);
        partial.remove(ComponentType.BATTERY);

        AssemblyRules.Result result = AssemblyRules.evaluate(ComponentTier.TIER_2_BEDNYAK, partial);

        assertFalse(result.ok());
        assertEquals(List.of("gpu", "battery"), result.problems());
    }

    @Test
    void namesTheOffendingModuleWhenItDoesNotFit() {
        Map<ComponentType, ComponentTier> tooFast = parts(ComponentTier.TIER_1_BOMZH);
        tooFast.put(ComponentType.GPU, ComponentTier.TIER_5_FRONTIER);

        AssemblyRules.Result result = AssemblyRules.evaluate(ComponentTier.TIER_2_BEDNYAK, tooFast);

        assertFalse(result.ok());
        assertEquals(1, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).startsWith("gpu tier 5"), result.problems().toString());
    }

    @Test
    void nullPartsAreReportedNotThrown() {
        AssemblyRules.Result result = AssemblyRules.evaluate(ComponentTier.TIER_1_BOMZH, null);

        assertFalse(result.ok());
        assertEquals(5, result.problems().size());
    }
}
