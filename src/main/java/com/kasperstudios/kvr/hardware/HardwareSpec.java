package com.kasperstudios.kvr.hardware;

/**
 * Stats a single component contributes (SPEC 2.5, as confirmed by the Creator). A component only fills
 * the column of its own type; everything else stays 0.
 *
 * <p>CPU is expressed purely as the QuickJS tick rate: that is what "logic speed" and "apps respond
 * slowly" mean mechanically, without inventing separate boot-time numbers.
 */
public record HardwareSpec(
        int scriptTickHz,
        int fps,
        int maxWindows,
        long storageBytes,
        long batteryCapacityFe,
        int maxDischargeFePerTick
) {

    public static final HardwareSpec NONE = new HardwareSpec(0, 0, 0, 0, 0, 0);

    /** Tick period of the script runtime in milliseconds - a direct measure of app responsiveness. */
    public int scriptTickMillis() {
        return scriptTickHz <= 0 ? 0 : Math.round(1000.0F / scriptTickHz);
    }

    static HardwareSpec cpu(int scriptTickHz) {
        return new HardwareSpec(scriptTickHz, 0, 0, 0, 0, 0);
    }

    static HardwareSpec gpu(int fps) {
        return new HardwareSpec(0, fps, 0, 0, 0, 0);
    }

    static HardwareSpec ram(int maxWindows) {
        return new HardwareSpec(0, 0, maxWindows, 0, 0, 0);
    }

    static HardwareSpec storage(long storageBytes) {
        return new HardwareSpec(0, 0, 0, storageBytes, 0, 0);
    }

    static HardwareSpec battery(long capacityFe, int maxDischargeFePerTick) {
        return new HardwareSpec(0, 0, 0, 0, capacityFe, maxDischargeFePerTick);
    }
}
