package com.kasperstudios.kvr.hardware;

import java.util.ArrayList;
import java.util.List;

/**
 * One upgrade step of one module: {@code cpu_tier2 -> cpu_tier3} for the price of
 * {@code TIER_2_BEDNYAK.upgradeOreCost()} UpOre plus the tier upgrader template.
 *
 * <p>The price is a domain rule, not a recipe: a vanilla smithing/crafting slot consumes one item per
 * slot, so 16 or 256 UpOre can never be a vanilla recipe. The upgrade station consumes from the
 * player's inventory using {@link #oreCost()}.
 */
public record TierUpgrade(ComponentType type, ComponentTier from) {

    public TierUpgrade {
        if (from.next().isEmpty()) {
            throw new IllegalArgumentException(type + " is already at the top tier");
        }
    }

    public ComponentTier to() {
        return from.next().orElseThrow();
    }

    public int oreCost() {
        return from.upgradeOreCost();
    }

    /** Every upgrade step of one module, in ladder order. */
    public static List<TierUpgrade> ladder(ComponentType type) {
        List<TierUpgrade> steps = new ArrayList<>();
        for (ComponentTier tier : ComponentTier.values()) {
            if (tier.next().isPresent()) {
                steps.add(new TierUpgrade(type, tier));
            }
        }
        return List.copyOf(steps);
    }

    public static TierUpgrade of(ComponentType type, ComponentTier from) {
        return new TierUpgrade(type, from);
    }
}
