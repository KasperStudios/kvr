package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Seam: whether the smithing upgrade can run right now, with an honest report of what is missing. */
class UpgradeRulesTest {

    private static final TierUpgrade CPU_3_TO_4 = TierUpgrade.of(ComponentType.CPU, ComponentTier.TIER_3_SREDNYAK);

    @Test
    void allowsTheUpgradeWhenEverythingIsPresent() {
        assertTrue(UpgradeRules.canUpgrade(CPU_3_TO_4,
                new UpgradeRules.Requirements(true, true, true), 64));
        assertDoesNotThrow(() -> UpgradeRules.requireUpgradeable(CPU_3_TO_4,
                new UpgradeRules.Requirements(true, true, true), 64));
    }

    @Test
    void reportsEveryMissingPiece() {
        List<String> missing = UpgradeRules.missing(CPU_3_TO_4,
                new UpgradeRules.Requirements(false, false, false), 10);

        assertEquals(4, missing.size(), missing.toString());
        assertTrue(missing.contains("cpu_tier3"), missing.toString());
        assertTrue(missing.contains("tier_upgrader_template"), missing.toString());
        assertTrue(missing.contains("54 x up_ore"), missing.toString());
        assertTrue(missing.contains("chassis of tier 4 or higher"), missing.toString());
        assertFalse(UpgradeRules.canUpgrade(CPU_3_TO_4, new UpgradeRules.Requirements(false, false, false), 10));
    }

    @Test
    void chassisOfTheTargetTierIsRequired() {
        // a tier-3 shell cannot hold tier-4 parts, so upgrading the part implies upgrading the shell
        assertFalse(UpgradeRules.canUpgrade(CPU_3_TO_4, new UpgradeRules.Requirements(true, true, false), 64));
    }

    @Test
    void exactOreIsEnoughButOneShortIsNot() {
        UpgradeRules.Requirements ready = new UpgradeRules.Requirements(true, true, true);

        assertTrue(UpgradeRules.canUpgrade(CPU_3_TO_4, ready, 64));
        assertFalse(UpgradeRules.canUpgrade(CPU_3_TO_4, ready, 63));
    }

    @Test
    void requireUpgradeableNamesTheUpgrade() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> UpgradeRules.requireUpgradeable(CPU_3_TO_4, new UpgradeRules.Requirements(true, false, true), 64));

        assertTrue(ex.getMessage().contains("cpu"), ex.getMessage());
        assertTrue(ex.getMessage().contains("TIER_3_SREDNYAK"), ex.getMessage());
        assertTrue(ex.getMessage().contains("tier_upgrader_template"), ex.getMessage());
    }
}
