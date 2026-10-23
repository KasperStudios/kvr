package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam: the upgrade price list. ADR-003 2 (confirmed by the Creator) fixes it at
 * 4 / 16 / 64 / 256 UpOre with the tier upgrader template as the pattern.
 */
class TierUpgradeTest {

    @Test
    void followsTheGeometricProgression() {
        assertEquals(4, TierUpgrade.of(ComponentType.CPU, ComponentTier.TIER_1_BOMZH).oreCost());
        assertEquals(16, TierUpgrade.of(ComponentType.CPU, ComponentTier.TIER_2_BEDNYAK).oreCost());
        assertEquals(64, TierUpgrade.of(ComponentType.CPU, ComponentTier.TIER_3_SREDNYAK).oreCost());
        assertEquals(256, TierUpgrade.of(ComponentType.CPU, ComponentTier.TIER_4_NORMAL).oreCost());
    }

    @Test
    void topTierHasNoUpgrade() {
        assertThrows(IllegalArgumentException.class,
                () -> TierUpgrade.of(ComponentType.GPU, ComponentTier.TIER_5_FRONTIER));
    }

    @Test
    void ladderOfEachTypeHasFourSteps() {
        for (ComponentType type : ComponentType.values()) {
            List<TierUpgrade> steps = TierUpgrade.ladder(type);
            assertEquals(4, steps.size(), type + " ladder");
            assertEquals(ComponentTier.TIER_2_BEDNYAK, steps.get(0).to());
            assertEquals(ComponentTier.TIER_5_FRONTIER, steps.get(3).to());
        }
    }

    @Test
    void twoHundredFiftySixOreIsFourCompressedBlocks() {
        assertEquals(4, UpgradeRules.compressedBlocksFor(256));
        assertEquals(1, UpgradeRules.compressedBlocksFor(64));
        assertEquals(1, UpgradeRules.compressedBlocksFor(4));
        assertEquals(64, CompressedOre.ORE_PER_BLOCK);
        assertEquals(256, CompressedOre.oreFor(4));
        assertEquals(4, CompressedOre.blocksFor(256));
    }
}
