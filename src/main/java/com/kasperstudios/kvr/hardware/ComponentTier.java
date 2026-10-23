package com.kasperstudios.kvr.hardware;

import java.util.Optional;

/**
 * The five hardware tiers (ADR-003 2). The ladder is the single source of truth for upgrade costs and
 * for the chassis compatibility rule.
 */
public enum ComponentTier {
    TIER_1_BOMZH(1, "Бомжатский"),
    TIER_2_BEDNYAK(2, "Бедняжественный"),
    TIER_3_SREDNYAK(3, "Среднячковский"),
    TIER_4_NORMAL(4, "Нормальный"),
    TIER_5_FRONTIER(5, "Фронтирный");

    private final int level;
    private final String displayName;

    ComponentTier(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int level() {
        return level;
    }

    public String displayName() {
        return displayName;
    }

    public Optional<ComponentTier> next() {
        for (ComponentTier tier : values()) {
            if (tier.level == level + 1) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    /**
     * UpOre cost of upgrading <em>this</em> tier to the next one: geometric progression
     * 4 -> 16 -> 64 -> 256 (SPEC 2.2).
     *
     * @throws IllegalStateException when called on {@link #TIER_5_FRONTIER}, which has no upgrade
     */
    public int upgradeOreCost() {
        if (next().isEmpty()) {
            throw new IllegalStateException(name() + " is the top tier and cannot be upgraded");
        }
        return (int) Math.round(4 * Math.pow(4, level - 1));
    }

    public static ComponentTier byLevel(int level) {
        for (ComponentTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        throw new IllegalArgumentException("no KVR component tier with level " + level);
    }
}
