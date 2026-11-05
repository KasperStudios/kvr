package com.kasperstudios.kvr.power;

/**
 * The hardware that matters for power, tiers 1..5. Built from the existing glasses codec by the
 * reader installed in {@link GlassesPower#installBuildReader} — this class does not know how the
 * assembly is stored.
 */
public record PowerBuild(int cpu, int gpu, int ram, int storage, int battery) {

    public PowerBuild {
        PowerTable.index(cpu);
        PowerTable.index(gpu);
        PowerTable.index(ram);
        PowerTable.index(storage);
        PowerTable.index(battery);
    }

    /** Current drawn by the modules in this state, FE/t. */
    public double draw(PowerState state) {
        double on = PowerTable.STANDBY
            + PowerTable.partDraw(cpu) + PowerTable.partDraw(gpu) + PowerTable.partDraw(ram) + PowerTable.partDraw(storage);
        return switch (state) {
            case ON -> on;
            case HUD_OFF -> on * PowerTable.HUD_OFF_FACTOR;
            case OFF -> 0;
        };
    }

    public int maxDischarge() {
        return PowerTable.batteryMaxDischarge(battery);
    }

    public int capacity() {
        return PowerTable.batteryCapacity(battery);
    }

    /** SPEC 2.8: the hardware pulls more than the cell can safely give. */
    public boolean overloads(PowerState state) {
        return draw(state) > maxDischarge();
    }

    public int coldBootTicks() {
        return PowerTable.coldBootTicks(cpu);
    }
}
