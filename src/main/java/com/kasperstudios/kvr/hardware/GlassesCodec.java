package com.kasperstudios.kvr.hardware;

import java.util.EnumMap;
import java.util.Map;

/**
 * Stable wire/stack format of an assembled pair of glasses: {@code [chassisLevel, cpuLevel, gpuLevel,
 * ramLevel, storageLevel, batteryLevel]}.
 *
 * <p>Six small ints are all that is ever stored on the item, which is what makes NBT (1.20.x) and data
 * components (1.21.x) interchangeable - only the container differs per version.
 */
public final class GlassesCodec {

    public static final int SLOTS = 1 + ComponentType.values().length;

    private GlassesCodec() {
    }

    public static int[] toInts(GlassesBuild build) {
        int[] data = new int[SLOTS];
        data[0] = build.chassisTier().level();
        int index = 1;
        for (ComponentType type : ComponentType.values()) {
            data[index++] = build.tierOf(type).level();
        }
        return data;
    }

    /**
     * @throws IllegalArgumentException when the array is malformed or the stored build is invalid
     *                                  (e.g. a part of a higher tier than the chassis)
     */
    public static GlassesBuild fromInts(int[] data) {
        if (data == null || data.length != SLOTS) {
            throw new IllegalArgumentException("KVR glasses data must have " + SLOTS + " entries, got "
                    + (data == null ? "null" : data.length));
        }
        ComponentTier chassis = ComponentTier.byLevel(data[0]);
        Map<ComponentType, ComponentTier> parts = new EnumMap<>(ComponentType.class);
        int index = 1;
        for (ComponentType type : ComponentType.values()) {
            parts.put(type, ComponentTier.byLevel(data[index++]));
        }
        return new GlassesBuild(chassis, parts);
    }

    /** {@code true} when the stack carries a readable, valid build. */
    public static boolean isValid(int[] data) {
        try {
            fromInts(data);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
