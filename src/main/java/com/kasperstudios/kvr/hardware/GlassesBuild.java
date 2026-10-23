package com.kasperstudios.kvr.hardware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A finished pair of glasses: a chassis plus exactly one component of each type, all of them fitting.
 *
 * <p>Instances are immutable snapshots, which is what makes the disassembly contract trivial: what the
 * player assembled is exactly what comes back out.
 */
public final class GlassesBuild {

    private final ComponentTier chassisTier;
    private final Map<ComponentType, ComponentTier> parts;

    /**
     * @throws IllegalArgumentException when a component is missing or does not fit the chassis
     */
    public GlassesBuild(ComponentTier chassisTier, Map<ComponentType, ComponentTier> parts) {
        if (chassisTier == null) {
            throw new IllegalArgumentException("chassisTier must not be null");
        }
        Map<ComponentType, ComponentTier> copy = new EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) {
            ComponentTier tier = parts.get(type);
            if (tier == null) {
                throw new IllegalArgumentException("KVR glasses need a " + type + " component");
            }
            ChassisRules.requireInstallable(chassisTier, tier);
            copy.put(type, tier);
        }
        this.chassisTier = chassisTier;
        this.parts = Collections.unmodifiableMap(copy);
    }

    public ComponentTier chassisTier() {
        return chassisTier;
    }

    public ComponentTier tierOf(ComponentType type) {
        return parts.get(type);
    }

    /** Stable, chassis-independent part order used by the assembly screen and by disassembly. */
    public List<Map.Entry<ComponentType, ComponentTier>> parts() {
        List<Map.Entry<ComponentType, ComponentTier>> ordered = new ArrayList<>(parts.size());
        for (ComponentType type : ComponentType.values()) {
            ordered.add(Map.entry(type, parts.get(type)));
        }
        return Collections.unmodifiableList(ordered);
    }

    public int fps() {
        return spec(ComponentType.GPU).fps();
    }

    public int maxWindows() {
        return spec(ComponentType.RAM).maxWindows();
    }

    public long storageBytes() {
        return spec(ComponentType.STORAGE).storageBytes();
    }

    public long batteryCapacityFe() {
        return spec(ComponentType.BATTERY).batteryCapacityFe();
    }

    public int maxDischargeFePerTick() {
        return spec(ComponentType.BATTERY).maxDischargeFePerTick();
    }

    public int scriptTickHz() {
        return spec(ComponentType.CPU).scriptTickHz();
    }

    public double bootSeconds() {
        return spec(ComponentType.CPU).bootSeconds();
    }

    private HardwareSpec spec(ComponentType type) {
        return HardwareTable.of(type, parts.get(type));
    }
}
