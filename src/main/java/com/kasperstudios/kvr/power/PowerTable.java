package com.kasperstudios.kvr.power;

/**
 * Every number of Block 3 in one place. Battery capacity / discharge come from SPEC 2.5; the rest
 * are Creator decisions (2026-11, BLOCK3_PLAN.md). If {@code HardwareTable} already exposes the
 * battery columns, delegate to it instead of keeping a second copy (DevNotes reuse rule).
 */
public final class PowerTable {

    public static final int TIERS = 5;

    /** Per-module draw, FE/t, tiers 1..5 (CPU, GPU, RAM, Storage — the battery itself draws nothing). */
    private static final int[] PART_DRAW = {2, 5, 12, 30, 90};
    /** Background OS consumption while on, FE/t. */
    public static final double STANDBY = 1.0;
    /** HUD-off keeps 30% of the consumption (−70%, SPEC 2.7). */
    public static final double HUD_OFF_FACTOR = 0.3;

    private static final int[] BATTERY_CAPACITY = {1_000, 5_000, 25_000, 100_000, 500_000};
    private static final int[] BATTERY_MAX_DISCHARGE = {10, 25, 60, 150, 500};

    /** Cold boot after Power Off, by CPU tier: T1 = 5 s … T5 = 1 s. */
    private static final int[] COLD_BOOT_SECONDS = {5, 4, 3, 2, 1};

    /** Overload → detonation, strictly 30 s: time to realise, take the glasses off or hit Power Off. */
    public static final int OVERLOAD_TICKS = 30 * 20;
    public static final double AMBIENT_CELSIUS = 20;
    public static final double DETONATION_CELSIUS = 140;

    public static final int DOCK_BUFFER = 4_000;
    public static final int DOCK_TO_GLASSES = 50;
    public static final int REDSTONE_GENERATION = 1;
    public static final int SOLAR_GENERATION = 4;

    /** Charged creeper; block damage follows the mobGriefing gamerule. */
    public static final float GROUND_EXPLOSION_POWER = 6.0f;
    public static final double PART_BURN_CHANCE = 0.5;
    /** Held in hands / inventory: left on half a heart. */
    public static final float HELD_HEALTH_LEFT = 1.0f;
    public static final int CONTUSION_TICKS = 10 * 20;
    public static final int DARKNESS_TICKS = 5 * 20;
    public static final int SIREN_PERIOD_TICKS = 5 * 20;

    /** Power is simulated in steps of this many ticks; writing the stack every tick spams inventory sync. */
    public static final int STEP_TICKS = 20;

    private PowerTable() {}

    static int index(int tier) {
        if (tier < 1 || tier > TIERS) throw new IllegalArgumentException("tier must be 1.." + TIERS + ", got " + tier);
        return tier - 1;
    }

    public static int partDraw(int tier) {
        return PART_DRAW[index(tier)];
    }

    public static int batteryCapacity(int tier) {
        return BATTERY_CAPACITY[index(tier)];
    }

    public static int batteryMaxDischarge(int tier) {
        return BATTERY_MAX_DISCHARGE[index(tier)];
    }

    public static int coldBootTicks(int cpuTier) {
        return COLD_BOOT_SECONDS[index(cpuTier)] * 20;
    }
}
