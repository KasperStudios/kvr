package com.kasperstudios.kvr.hardware;

/**
 * Whether an upgrade can be performed right now, and what exactly is missing when it cannot.
 *
 * <p>Costs: the part of the previous tier, one tier upgrader template and 4/16/64/256 UpOre
 * (ADR-003 2, confirmed by the Creator). 256 UpOre is exactly four compressed UpOre blocks of 64.
 */
public final class UpgradeRules {

    private UpgradeRules() {
    }

    public record Requirements(boolean hasBasePart, boolean hasTemplate, boolean hasChassisRoom) {
    }

    /** Everything the player still needs, empty when the upgrade can run. */
    public static java.util.List<String> missing(TierUpgrade upgrade, Requirements requirements, int availableOre) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (!requirements.hasBasePart()) {
            missing.add(upgrade.type().id() + "_tier" + upgrade.from().level());
        }
        if (!requirements.hasTemplate()) {
            missing.add("tier_upgrader_template");
        }
        int shortfall = upgrade.oreCost() - availableOre;
        if (shortfall > 0) {
            missing.add(shortfall + " x up_ore");
        }
        if (!requirements.hasChassisRoom()) {
            missing.add("chassis of tier " + upgrade.to().level() + " or higher");
        }
        return java.util.List.copyOf(missing);
    }

    public static boolean canUpgrade(TierUpgrade upgrade, Requirements requirements, int availableOre) {
        return missing(upgrade, requirements, availableOre).isEmpty();
    }

    /**
     * @throws IllegalStateException listing every missing ingredient
     */
    public static void requireUpgradeable(TierUpgrade upgrade, Requirements requirements, int availableOre) {
        java.util.List<String> missing = missing(upgrade, requirements, availableOre);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("cannot upgrade " + upgrade.type().id() + " from "
                    + upgrade.from() + " to " + upgrade.to() + ", missing: " + String.join(", ", missing));
        }
    }

    /** Compressed UpOre blocks needed to carry a cost, e.g. 256 ore = 4 blocks. */
    public static int compressedBlocksFor(int oreCost) {
        return (oreCost + CompressedOre.ORE_PER_BLOCK - 1) / CompressedOre.ORE_PER_BLOCK;
    }
}
