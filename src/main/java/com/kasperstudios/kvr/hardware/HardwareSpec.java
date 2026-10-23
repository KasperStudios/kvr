package com.kasperstudios.kvr.hardware;

/**
 * Stats a single component contributes (SPEC 2.5). A component only fills the column of its own type;
 * everything else stays 0.
 */
public record HardwareSpec(
        double bootSeconds,
        int scriptTickHz,
        int fps,
        int maxWindows,
        long storageBytes,
        long batteryCapacityFe,
        int maxDischargeFePerTick
) {

    public static final HardwareSpec NONE = new HardwareSpec(0, 0, 0, 0, 0, 0, 0);

    static HardwareSpec cpu(double bootSeconds, int scriptTickHz) {
        return new HardwareSpec(bootSeconds, scriptTickHz, 0, 0, 0, 0, 0);
    }

    static HardwareSpec gpu(int fps) {
        return new HardwareSpec(0, 0, fps, 0, 0, 0, 0);
    }

    static HardwareSpec ram(int maxWindows) {
        return new HardwareSpec(0, 0, 0, maxWindows, 0, 0, 0);
    }

    static HardwareSpec storage(long storageBytes) {
        return new HardwareSpec(0, 0, 0, 0, storageBytes, 0, 0);
    }

    static HardwareSpec battery(long capacityFe, int maxDischargeFePerTick) {
        return new HardwareSpec(0, 0, 0, 0, 0, capacityFe, maxDischargeFePerTick);
    }
}
