package com.kasperstudios.kvr.hardware;

import java.util.EnumMap;
import java.util.Map;

/** The influence matrix from SPEC 2.5, as data. */
public final class HardwareTable {

    private static final Map<ComponentType, Map<ComponentTier, HardwareSpec>> TABLE = new EnumMap<>(ComponentType.class);

    static {
        spec(ComponentType.CPU, HardwareSpec.cpu(20, 10), HardwareSpec.cpu(10, 20), HardwareSpec.cpu(4, 50),
                HardwareSpec.cpu(1, 100), HardwareSpec.cpu(0.1, 200));
        spec(ComponentType.GPU, HardwareSpec.gpu(15), HardwareSpec.gpu(30), HardwareSpec.gpu(60),
                HardwareSpec.gpu(90), HardwareSpec.gpu(144));
        spec(ComponentType.RAM, HardwareSpec.ram(1), HardwareSpec.ram(2), HardwareSpec.ram(4),
                HardwareSpec.ram(8), HardwareSpec.ram(16));
        spec(ComponentType.STORAGE,
                HardwareSpec.storage(256L * 1024),
                HardwareSpec.storage(1024L * 1024),
                HardwareSpec.storage(8L * 1024 * 1024),
                HardwareSpec.storage(64L * 1024 * 1024),
                HardwareSpec.storage(512L * 1024 * 1024));
        spec(ComponentType.BATTERY,
                HardwareSpec.battery(1_000, 10),
                HardwareSpec.battery(5_000, 25),
                HardwareSpec.battery(25_000, 60),
                HardwareSpec.battery(100_000, 150),
                HardwareSpec.battery(500_000, 500));
    }

    private HardwareTable() {
    }

    private static void spec(ComponentType type, HardwareSpec t1, HardwareSpec t2, HardwareSpec t3,
                             HardwareSpec t4, HardwareSpec t5) {
        Map<ComponentTier, HardwareSpec> row = new EnumMap<>(ComponentTier.class);
        row.put(ComponentTier.TIER_1_BOMZH, t1);
        row.put(ComponentTier.TIER_2_BEDNYAK, t2);
        row.put(ComponentTier.TIER_3_SREDNYAK, t3);
        row.put(ComponentTier.TIER_4_NORMAL, t4);
        row.put(ComponentTier.TIER_5_FRONTIER, t5);
        TABLE.put(type, row);
    }

    public static HardwareSpec of(ComponentType type, ComponentTier tier) {
        HardwareSpec spec = TABLE.get(type).get(tier);
        return spec == null ? HardwareSpec.NONE : spec;
    }
}
