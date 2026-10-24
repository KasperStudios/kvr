package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam: the partial build - the chassis-as-container state. It must accept one module per type,
 * reject nothing silently, and always return exactly what was inserted (the disassembly contract).
 */
class PartialBuildTest {

    @Test
    void emptyPartialBuildReportsNoTiers() {
        Map<ComponentType, ComponentTier> parts = new EnumMap<>(ComponentType.class);
        AssemblyRules.Result result = AssemblyRules.evaluate(ComponentTier.TIER_1_BOMZH, parts);
        assertFalse(result.ok());
        assertTrue(result.problems().contains("cpu"));
        assertTrue(result.problems().contains("gpu"));
    }

    @Test
    void insertingEachTypeOnceCompletesTheBuild() {
        Map<ComponentType, ComponentTier> parts = new EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) {
            parts.put(type, ComponentTier.TIER_1_BOMZH);
        }
        AssemblyRules.Result result = AssemblyRules.evaluate(ComponentTier.TIER_1_BOMZH, parts);
        assertTrue(result.ok(), result.problems().toString());
    }

    @Test
    void aModuleAboveTheChassisTierKeepsTheBuildInvalid() {
        Map<ComponentType, ComponentTier> parts = new EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) {
            parts.put(type, ComponentTier.TIER_1_BOMZH);
        }
        parts.put(ComponentType.CPU, ComponentTier.TIER_3_SREDNYAK);
        AssemblyRules.Result result = AssemblyRules.evaluate(ComponentTier.TIER_1_BOMZH, parts);
        assertFalse(result.ok());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("does not fit")));
    }
}
