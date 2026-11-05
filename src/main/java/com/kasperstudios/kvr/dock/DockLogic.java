package com.kasperstudios.kvr.dock;

import com.kasperstudios.kvr.power.PowerTable;

/** Pure dock-plate energy rules (SPEC 2.6 + Creator numbers), unit-tested in {@code DockLogicTest}. */
public final class DockLogic {

    private DockLogic() {}

    /** FE generated this tick: redstone signal (low efficiency) + solar module in daylight under open sky. */
    public static double generation(boolean redstone, boolean solarInstalled, boolean sunny) {
        double fe = 0;
        if (redstone) fe += PowerTable.REDSTONE_GENERATION;
        if (solarInstalled && sunny) fe += PowerTable.SOLAR_GENERATION;
        return fe;
    }

    /** Buffer after adding {@code fe}, capped at the built-in 4000 FE power bank. */
    public static double fill(double buffer, double fe) {
        return Math.max(0, Math.min(PowerTable.DOCK_BUFFER, buffer + fe));
    }

    /** Energy offered to the glasses for a step of {@code ticks}: 50 FE/t, never more than the buffer holds. */
    public static double offer(double buffer, int ticks) {
        return Math.max(0, Math.min(buffer, (double) PowerTable.DOCK_TO_GLASSES * ticks));
    }

    /** External FE input (cables): how much of {@code max} fits into the buffer. */
    public static int accept(double buffer, int max) {
        return (int) Math.max(0, Math.min(max, Math.floor(PowerTable.DOCK_BUFFER - buffer)));
    }

    /** Daylight approximation that stays stable across versions: day half of the cycle, no rain. */
    public static boolean sunny(long dayTime, boolean raining, boolean seesSky) {
        return seesSky && !raining && Math.floorMod(dayTime, 24000L) < 12000L;
    }
}
