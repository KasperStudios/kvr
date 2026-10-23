package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam: the chassis bottleneck (ADR-003 3.1). A chassis can never host a component above its own tier,
 * which is what forces players to upgrade the shell before the hardware.
 */
class ChassisRulesTest {

    @Test
    void acceptsEqualOrLowerTierParts() {
        assertTrue(ChassisRules.canInstall(ComponentTier.TIER_3_SREDNYAK, ComponentTier.TIER_3_SREDNYAK));
        assertTrue(ChassisRules.canInstall(ComponentTier.TIER_3_SREDNYAK, ComponentTier.TIER_1_BOMZH));
        assertTrue(ChassisRules.canInstall(ComponentTier.TIER_5_FRONTIER, ComponentTier.TIER_4_NORMAL));
    }

    @Test
    void rejectsPartsAboveTheChassis() {
        assertFalse(ChassisRules.canInstall(ComponentTier.TIER_1_BOMZH, ComponentTier.TIER_2_BEDNYAK));
        assertFalse(ChassisRules.canInstall(ComponentTier.TIER_4_NORMAL, ComponentTier.TIER_5_FRONTIER));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ChassisRules.requireInstallable(ComponentTier.TIER_2_BEDNYAK, ComponentTier.TIER_4_NORMAL));
        assertTrue(ex.getMessage().contains("TIER_2_BEDNYAK"), ex.getMessage());
        assertTrue(ex.getMessage().contains("TIER_4_NORMAL"), ex.getMessage());
    }

    @Test
    void equalTierInstallIsSilent() {
        assertDoesNotThrow(() -> ChassisRules.requireInstallable(ComponentTier.TIER_4_NORMAL, ComponentTier.TIER_4_NORMAL));
    }
}
