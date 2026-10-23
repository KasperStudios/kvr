package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam: the tier ladder. Every craft, every stat and every upgrade recipe is derived from it, so the
 * ladder itself is pinned down here.
 */
class ComponentTierTest {

    @Test
    void hasFiveTiersInOrder() {
        assertEquals(5, ComponentTier.values().length);
        assertEquals(1, ComponentTier.TIER_1_BOMZH.level());
        assertEquals(5, ComponentTier.TIER_5_FRONTIER.level());
    }

    @Test
    void upOreCostFollowsGeometricProgression() {
        // SPEC 2.2: Tier 1->2: 4, 2->3: 16, 3->4: 64, 4->5: 256
        assertEquals(4, ComponentTier.TIER_1_BOMZH.upgradeOreCost());
        assertEquals(16, ComponentTier.TIER_2_BEDNYAK.upgradeOreCost());
        assertEquals(64, ComponentTier.TIER_3_SREDNYAK.upgradeOreCost());
        assertEquals(256, ComponentTier.TIER_4_NORMAL.upgradeOreCost());
    }

    @Test
    void frontierIsTerminal() {
        assertTrue(ComponentTier.TIER_5_FRONTIER.next().isEmpty());
        assertThrows(IllegalStateException.class, ComponentTier.TIER_5_FRONTIER::upgradeOreCost);
        assertEquals(ComponentTier.TIER_2_BEDNYAK, ComponentTier.TIER_1_BOMZH.next().orElseThrow());
    }

    @Test
    void tiersAreAddressableByLevel() {
        for (ComponentTier tier : ComponentTier.values()) {
            assertEquals(tier, ComponentTier.byLevel(tier.level()));
        }
        assertThrows(IllegalArgumentException.class, () -> ComponentTier.byLevel(0));
        assertThrows(IllegalArgumentException.class, () -> ComponentTier.byLevel(6));
    }

    @Test
    void everyTierHasARussianName() {
        for (ComponentTier tier : ComponentTier.values()) {
            assertFalse(tier.displayName().isBlank(), tier + " has no display name");
        }
    }
}
